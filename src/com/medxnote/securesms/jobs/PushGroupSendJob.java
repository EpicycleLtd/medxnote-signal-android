package com.medxnote.securesms.jobs;

import android.content.Context;
import android.util.Log;

import com.medxnote.securesms.MismatchHandler;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.GroupDatabase;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.NoSuchMessageException;
import com.medxnote.securesms.database.documents.NetworkFailure;
import com.medxnote.securesms.dependencies.InjectableType;
import com.medxnote.securesms.dependencies.TextSecureCommunicationModule;
import com.medxnote.securesms.jobs.requirements.MasterSecretRequirement;
import com.medxnote.securesms.mms.OutgoingGroupMediaMessage;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.transport.UndeliverableMessageException;
import com.medxnote.securesms.util.GroupUtil;
import com.medxnote.securesms.mms.OutgoingMediaMessage;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.RecipientFormattingException;
import com.medxnote.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;

public class PushGroupSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushGroupSendJob.class.getSimpleName();

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;
  private final long filterRecipientId;
  private byte[] groupId;
  private boolean isResend;

  public PushGroupSendJob(Context context, long messageId, String destination, long filterRecipientId, boolean isResend) {
    this(context, messageId, destination, filterRecipientId);
    this.isResend = isResend;
  }

  public PushGroupSendJob(Context context, long messageId, String destination, long filterRecipientId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId(destination)
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withRetryCount(5)
                                .create());

    this.messageId         = messageId;
    this.filterRecipientId = filterRecipientId;
    this.isResend = false;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context)
                   .markAsSending(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws MmsException, IOException, NoSuchMessageException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      // save receipts
      groupId = GroupUtil.getDecodedId(message.getRecipients().getPrimaryRecipient().getNumber());
      Recipients recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
      if (!isResend) {
        DatabaseFactory.getReceiptDatabase(context).createReceipts(recipients, message.getSentTimeMillis());
      }
      deliver(masterSecret, message, filterRecipientId);

      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId);
      markAttachmentsUploaded(messageId, message.getAttachments());
    } catch (InvalidNumberException | RecipientFormattingException | UndeliverableMessageException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (EncapsulatedExceptions e) {
      Log.w(TAG, e);
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

      if(
          !e.getNetworkExceptions().isEmpty() || (
            !e.getUntrustedIdentityExceptions().isEmpty() &&
            !TextSecurePreferences.isAutoacceptKeysEnabled(context)
          )
      ){
        database.addFailures(messageId, failures);
        database.markAsPush(messageId);
      }

      if(
          !e.getNetworkExceptions().isEmpty() || (
            !e.getUntrustedIdentityExceptions().isEmpty() &&
            !TextSecurePreferences.isAutoacceptKeysEnabled(context)
          )
      ){
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
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message, long filterRecipientId)
          throws IOException, RecipientFormattingException, InvalidNumberException,
          EncapsulatedExceptions, UndeliverableMessageException
  {
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
    SignalServiceMessageSender messageSender = messageSenderFactory.create();
    Recipients recipients = groupDatabase.getGroupMembers(groupId, false);
    List<SignalServiceAttachment> attachments   = getAttachmentsFor(masterSecret, message.getAttachments());
    List<SignalServiceAddress>    addresses;
    String groupAdmin = groupDatabase.getAdmin(groupId);

    if (filterRecipientId >= 0) addresses = getPushAddresses(filterRecipientId);
    else                        addresses = getPushAddresses(recipients);

    if (message.isGroup()) {
      OutgoingGroupMediaMessage groupMessage     = (OutgoingGroupMediaMessage) message;
      GroupContext              groupContext     = groupMessage.getGroupContext();
      List<String>              kickMembers      = groupContext.getKickList();
      if (!kickMembers.isEmpty()) {
        List<String> memberNumbers = recipients.toNumberStringList(false);
        memberNumbers.removeAll(kickMembers);
        groupDatabase.updateMembers(groupId, memberNumbers);
      }
      SignalServiceAttachment   avatar           = attachments.isEmpty() ? null : attachments.get(0);
      SignalServiceGroup.Type   type;
      if (groupMessage.isGroupQuit()) {
        type = SignalServiceGroup.Type.QUIT;
      } else if (groupMessage.isGroupUpdate()) {
        type = SignalServiceGroup.Type.UPDATE;
      } else if (groupMessage.isGroupKick()) {
        type = SignalServiceGroup.Type.KICK;
      } else {
        type = SignalServiceGroup.Type.UNKNOWN;
      }
      SignalServiceGroup        group            = new SignalServiceGroup(type, groupId, groupContext.getName(), groupContext.getMembersList(), groupContext.getKickList(), avatar, groupAdmin);
      SignalServiceDataMessage  groupDataMessage = new SignalServiceDataMessage(message.getSentTimeMillis(), group, null, null);

      messageSender.sendMessage(addresses, groupDataMessage);
    } else {
      SignalServiceGroup       group        = new SignalServiceGroup(groupId, groupAdmin);
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
