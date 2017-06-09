package com.medxnote.securesms.jobs;

import android.content.Context;
import android.util.Log;

import com.medxnote.securesms.ApplicationContext;
import com.medxnote.securesms.MismatchHandler;
import com.medxnote.securesms.attachments.Attachment;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.dependencies.InjectableType;
import com.medxnote.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;
import com.medxnote.securesms.mms.MediaConstraints;
import com.medxnote.securesms.mms.OutgoingMediaMessage;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.transport.InsecureFallbackApprovalException;
import com.medxnote.securesms.transport.RetryLaterException;
import com.medxnote.securesms.transport.UndeliverableMessageException;
import com.medxnote.securesms.util.TextSecurePreferences;

import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;


public class PushEditedMediaSendJob extends PushSendJob implements InjectableType {

    private static final String TAG = PushEditedMediaSendJob.class.getSimpleName();

    @Inject transient TextSecureMessageSenderFactory messageSenderFactory;
    private long messageId;

    public PushEditedMediaSendJob(Context context, long messageId, String destination) {
        super(context, constructParameters(context, destination));

        this.messageId = messageId;
    }

    @Override
    protected void onSend(MasterSecret masterSecret) throws Exception {

        MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
        OutgoingMediaMessage message = database.getOutgoingMessage(masterSecret, messageId);

        try {
            deliver(masterSecret, message);
        } catch (InsecureFallbackApprovalException ifae) {
            Log.w(TAG, ifae);
            database.markAsPendingInsecureSmsFallback(messageId);
            notifyMediaMessageDeliveryFailed(context, messageId);
            ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context));
        } catch (UntrustedIdentityException uie) {
            Log.w(TAG, uie);
            Recipients recipients = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
            long recipientId = recipients.getPrimaryRecipient().getRecipientId();

            database.addMismatchedIdentity(messageId, recipientId, uie.getIdentityKey());
            if(TextSecurePreferences.isAutoacceptKeysEnabled(context)) {
                new MismatchHandler(context).acceptMismatch(recipients);
            } else {
                database.markAsSentFailed(messageId);
                database.markAsPush(messageId);
            }
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return exception instanceof RequirementNotMetException
                || exception instanceof RetryLaterException;
    }

    @Override
    public void onAdded() {
        DatabaseFactory.getMmsDatabase(context).markAsEdit(messageId);
    }

    @Override
    public void onCanceled() {
        DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    }

    private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message)
            throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
            UndeliverableMessageException
    {
        if (message.getRecipients() == null                       ||
                message.getRecipients().getPrimaryRecipient() == null ||
                message.getRecipients().getPrimaryRecipient().getNumber() == null)
        {
            throw new UndeliverableMessageException("No destination address.");
        }

        SignalServiceMessageSender messageSender = messageSenderFactory.create();

        try {
            SignalServiceAddress address = getPushAddress(message.getRecipients().getPrimaryRecipient().getNumber());
            List<Attachment> scaledAttachments = scaleAttachments(masterSecret, MediaConstraints.PUSH_CONSTRAINTS, message.getAttachments());
            List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);
            SignalServiceDataMessage mediaMessage = SignalServiceDataMessage.newBuilder()
                    .withBody(message.getBody())
                    .withAttachments(attachmentStreams)
                    .withTimestamp(message.getSentTimeMillis())
                    .build();

            messageSender.sendMessage(address, mediaMessage);
        } catch (InvalidNumberException | UnregisteredUserException e) {
            Log.w(TAG, e);
            throw new InsecureFallbackApprovalException(e);
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
            throw new UndeliverableMessageException(e);
        } catch (IOException e) {
            Log.w(TAG, e);
            throw new RetryLaterException(e);
        }
    }

}
