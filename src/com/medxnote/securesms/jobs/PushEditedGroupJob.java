package com.medxnote.securesms.jobs;


import android.content.Context;

import com.medxnote.securesms.MismatchHandler;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.documents.NetworkFailure;
import com.medxnote.securesms.dependencies.InjectableType;
import com.medxnote.securesms.dependencies.TextSecureCommunicationModule;
import com.medxnote.securesms.mms.OutgoingGroupMediaMessage;
import com.medxnote.securesms.mms.OutgoingMediaMessage;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.RecipientFormattingException;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.transport.UndeliverableMessageException;
import com.medxnote.securesms.util.GroupUtil;
import com.medxnote.securesms.util.TextSecurePreferences;

import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;


public class PushEditedGroupJob extends PushSendJob implements InjectableType {

    private long messageId;
    private long filterRecipientId;
    @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

    public PushEditedGroupJob(Context context, long messageId, String destination, long filterRecipientId) {
        super(context, constructParameters(context, destination));

        this.messageId = messageId;
        this.filterRecipientId = filterRecipientId;
    }

    @Override
    protected void onSend(MasterSecret masterSecret) throws Exception {
        MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
        OutgoingMediaMessage message = database.getOutgoingMessage(masterSecret, messageId);
        try {
            deliver(masterSecret, message, filterRecipientId);
            markAttachmentsUploaded(messageId, message.getAttachments());
        } catch (InvalidNumberException e) {
            database.markAsSentFailed(messageId);
            notifyMediaMessageDeliveryFailed(context, messageId);
        } catch (EncapsulatedExceptions e) {
            List<NetworkFailure> failures = new LinkedList<>();

            for (NetworkFailureException nfe : e.getNetworkExceptions()) {
                Recipient recipient = RecipientFactory.getRecipientsFromString(context, nfe.getE164number(), false).getPrimaryRecipient();
                failures.add(new NetworkFailure(recipient.getRecipientId()));
                DatabaseFactory.getReceiptDatabase(context).deleteReceipts(nfe.getE164number(), message.getSentTimeMillis());
            }

            for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
                Recipient recipient = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false).getPrimaryRecipient();
                database.addMismatchedIdentity(messageId, recipient.getRecipientId(), uie.getIdentityKey());
                DatabaseFactory.getReceiptDatabase(context).deleteReceipts(uie.getE164Number(), message.getSentTimeMillis());

                if(TextSecurePreferences.isAutoacceptKeysEnabled(context)) {
                    new MismatchHandler(context).acceptMismatch(message.getRecipients(), uie.getE164Number());
                }
            }

            if(!e.getNetworkExceptions().isEmpty() || (!e.getUntrustedIdentityExceptions().isEmpty() &&
                    !TextSecurePreferences.isAutoacceptKeysEnabled(context))) {
                database.addFailures(messageId, failures);
                database.markAsPush(messageId);
            }

            if(!e.getNetworkExceptions().isEmpty() || (!e.getUntrustedIdentityExceptions().isEmpty() &&
                                    !TextSecurePreferences.isAutoacceptKeysEnabled(context))) {
                database.markAsSecure(messageId);
                database.markAsSent(messageId);
                markAttachmentsUploaded(messageId, message.getAttachments());
            } else {
                database.markAsSentFailed(messageId);
                notifyMediaMessageDeliveryFailed(context, messageId);
            }
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return exception instanceof IOException;
    }

    @Override
    public void onAdded() {
        DatabaseFactory.getMmsDatabase(context).markAsEdit(messageId);
    }

    @Override
    public void onCanceled() {
        DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    }

    private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message, long filterRecipientId)
            throws IOException, RecipientFormattingException, InvalidNumberException,
            EncapsulatedExceptions, UndeliverableMessageException {
        SignalServiceMessageSender messageSender = messageSenderFactory.create();
        byte[] groupId = GroupUtil.getDecodedId(message.getRecipients().getPrimaryRecipient().getNumber());
        Recipients recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
        List<SignalServiceAttachment> attachments = getAttachmentsFor(masterSecret, message.getAttachments());
        List<SignalServiceAddress> addresses;

        if (filterRecipientId >= 0) addresses = getPushAddresses(filterRecipientId);
        else                        addresses = getPushAddresses(recipients);

        if (message.isGroup()) {
            OutgoingGroupMediaMessage groupMessage = (OutgoingGroupMediaMessage) message;
            SignalServiceProtos.GroupContext groupContext = groupMessage.getGroupContext();
            SignalServiceAttachment avatar = attachments.isEmpty() ? null : attachments.get(0);
            SignalServiceGroup.Type type = groupMessage.isGroupQuit() ? SignalServiceGroup.Type.QUIT : SignalServiceGroup.Type.UPDATE;
            SignalServiceGroup group = new SignalServiceGroup(type, groupId, groupContext.getName(), groupContext.getMembersList(), avatar);
            SignalServiceDataMessage groupDataMessage = new SignalServiceDataMessage(message.getSentTimeMillis(), group, null, null);

            messageSender.sendMessage(addresses, groupDataMessage);
        } else {
            SignalServiceGroup group = new SignalServiceGroup(groupId);
            SignalServiceDataMessage groupMessage = new SignalServiceDataMessage(message.getSentTimeMillis(), group, attachments, message.getBody());

            messageSender.sendMessage(addresses, groupMessage);
        }
    }
    private List<SignalServiceAddress> getPushAddresses(Recipients recipients) throws InvalidNumberException {
        List<SignalServiceAddress> addresses = new LinkedList<>();

        for (Recipient recipient : recipients.getRecipientsList()) {
            addresses.add(getPushAddress(recipient.getNumber()));
        }

        return addresses;
    }

    private List<SignalServiceAddress> getPushAddresses(long filterRecipientId) throws InvalidNumberException {
        List<SignalServiceAddress> addresses = new LinkedList<>();
        addresses.add(getPushAddress(RecipientFactory.getRecipientForId(context, filterRecipientId, false).getNumber()));
        return addresses;
    }
}
