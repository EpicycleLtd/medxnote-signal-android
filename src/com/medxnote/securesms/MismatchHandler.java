package com.medxnote.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.GroupDatabase;
import com.medxnote.securesms.database.IdentityDatabase;
import com.medxnote.securesms.database.MmsAddressDatabase;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.MmsSmsDatabase;
import com.medxnote.securesms.database.PushDatabase;
import com.medxnote.securesms.database.SmsDatabase;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.groups.GroupManager;
import com.medxnote.securesms.jobs.PushDecryptJob;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.service.KeyCachingService;
import com.medxnote.securesms.sms.MessageSender;
import com.medxnote.securesms.util.Base64;
import com.medxnote.securesms.util.BitmapUtil;
import com.medxnote.securesms.util.GroupUtil;
import com.medxnote.securesms.util.TextSecurePreferences;

public class MismatchHandler {

  private static final String TAG = MismatchHandler.class.getSimpleName();
  private Context context;
  private MasterSecret masterSecret;


  public MismatchHandler(Context context) {
    this.context = context;
    this.masterSecret = KeyCachingService.getMasterSecret(context);
  }

  private void notifyClient(MessageRecord messageRecord, String number){
    if(TextSecurePreferences.isAutoAcceptMessageEnabled(context)) {
      // This hack because of group's Anonymous recipient
      Recipient recipient = RecipientFactory.getRecipientsFromString(context, number, false)
              .getPrimaryRecipient();

      SmsDatabase db = DatabaseFactory.getSmsDatabase(context);
      db.insertKeysChanged(messageRecord.getThreadId(), recipient,
              messageRecord.getTimestamp());
    }
  }

  public void acceptMismatch(Recipients recipients) {
    acceptMismatch(recipients, recipients.getPrimaryRecipient().getNumber());
  }

  public void acceptMismatch(final Recipients recipients, final String number) {

    new AsyncTask<Void, Void, Void>() {

      @Override
      protected Void doInBackground(Void... params) {
        MessageRecord messageRecord;

        MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
        IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);

        long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
        Cursor cursor = mmsSmsDatabase.getIdentityConflictMessagesForThread(threadId);
        MmsSmsDatabase.Reader mmsSmsReader = mmsSmsDatabase.readerFor(cursor, masterSecret);

        while ((messageRecord = mmsSmsReader.getNext()) != null) {
          IdentityKeyMismatch identityKeyMismatch = messageRecord.getIdentityKeyMismatches().get(0);

          identityDatabase.saveIdentity(identityKeyMismatch.getRecipientId(),
                  identityKeyMismatch.getIdentityKey());

          processMessageRecord(messageRecord, identityKeyMismatch);
          processPendingMessageRecords(messageRecord.getThreadId(), identityKeyMismatch);
          notifyClient(messageRecord, number);
        }

        Recipient recipient = recipients.getPrimaryRecipient();
        if (recipient != null) {
          try {
            byte[] groupId = GroupUtil.getDecodedId(recipient.getNumber());
            GroupDatabase.GroupRecord groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(groupId);
            if (groupRecord != null) {
              Set<Recipient> recipientSet = GroupUtil.getMembers(context, groupRecord.getMembers());
              if (recipientSet != null) {
                try {
                  Recipient admin = RecipientFactory.getRecipientsFromString(context, groupRecord.getAdmin(), false)
                          .getPrimaryRecipient();

                  GroupManager.updateGroup(context, masterSecret, groupId, recipientSet, new HashSet<Recipient>(),
                          BitmapUtil.fromByteArray(groupRecord.getAvatar()), groupRecord.getTitle(), admin);
                } catch (InvalidNumberException e1) {
                  Log.d(TAG, "Invalid number: " + e1.getMessage());
                }
              }
            }
          } catch (IOException e) {
            Log.d(TAG, "Recipient isn't a group!");
          }
        }
        return null;
      }

      private void processMessageRecord(MessageRecord messageRecord, IdentityKeyMismatch identityKeyMismatch) {
        if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord, identityKeyMismatch);
        else                            processIncomingMessageRecord(messageRecord, identityKeyMismatch);
      }

      private void processPendingMessageRecords(long threadId, IdentityKeyMismatch identityKeyMismatch) {
        MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
        Cursor cursor = mmsSmsDatabase.getIdentityConflictMessagesForThread(threadId);
        MmsSmsDatabase.Reader reader = mmsSmsDatabase.readerFor(cursor, masterSecret);
        MessageRecord         record;

        try {
          while ((record = reader.getNext()) != null) {
            for (IdentityKeyMismatch recordMismatch : record.getIdentityKeyMismatches()) {
              if (identityKeyMismatch.equals(recordMismatch)) {
                processMessageRecord(record, identityKeyMismatch);
              }
            }
          }
        } finally {
          if (reader != null)
            reader.close();
        }
      }

      private void processOutgoingMessageRecord(MessageRecord messageRecord, IdentityKeyMismatch identityKeyMismatch) {
        SmsDatabase smsDatabase        = DatabaseFactory.getSmsDatabase(context);
        MmsDatabase mmsDatabase        = DatabaseFactory.getMmsDatabase(context);
        MmsAddressDatabase mmsAddressDatabase = DatabaseFactory.getMmsAddressDatabase(context);

        if (messageRecord.isMms()) {
          mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                  identityKeyMismatch.getRecipientId(),
                  identityKeyMismatch.getIdentityKey());

          Recipients recipients = mmsAddressDatabase.getRecipientsForId(messageRecord.getId());

          if (recipients.isGroupRecipient()){
            MessageSender.resendGroupMessage(context, masterSecret, messageRecord, identityKeyMismatch.getRecipientId());
          } else {
            MessageSender.resend(context, masterSecret, messageRecord);
          }
        } else {
          smsDatabase.removeMismatchedIdentity(
                  messageRecord.getId(),
                  identityKeyMismatch.getRecipientId(),
                  identityKeyMismatch.getIdentityKey()
          );
          MessageSender.resend(context, masterSecret, messageRecord);
        }
      }

      private void processIncomingMessageRecord(MessageRecord messageRecord, IdentityKeyMismatch identityKeyMismatch) {
        try {
          PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
          SmsDatabase  smsDatabase  = DatabaseFactory.getSmsDatabase(context);

          smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                  identityKeyMismatch.getRecipientId(),
                  identityKeyMismatch.getIdentityKey());

          SignalServiceEnvelope envelope = new SignalServiceEnvelope(
                  SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
                  messageRecord.getIndividualRecipient().getNumber(),
                  messageRecord.getRecipientDeviceId(), "",
                  messageRecord.getDateSent(),
                  Base64.decode(messageRecord.getBody().getBody()),
                  null
          );

          long pushId = pushDatabase.insert(envelope);

          ApplicationContext.getInstance(context)
                  .getJobManager()
                  .add(
                          new PushDecryptJob(
                                  context, pushId, messageRecord.getId(),
                                  messageRecord.getIndividualRecipient().getNumber()
                          )
                  );
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }.execute();
  }
}
