package com.medxnote.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.IdentityDatabase;
import com.medxnote.securesms.database.MmsAddressDatabase;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.MmsSmsDatabase;
import com.medxnote.securesms.database.PushDatabase;
import com.medxnote.securesms.database.SmsDatabase;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.jobs.PushDecryptJob;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.service.KeyCachingService;
import com.medxnote.securesms.sms.MessageSender;
import com.medxnote.securesms.util.Base64;
import com.medxnote.securesms.util.TextSecurePreferences;

public class MismatchHandler {

  String TAG = MismatchHandler.class.getCanonicalName();
  Context context;


  public MismatchHandler(Context context){
    this.context = context;
  }

  private void notifyClient(MessageRecord messageRecord, String number){
    if(TextSecurePreferences.isAutoacceptMessageEnabled(context)) {
      // This hack because of group's Anonymous recipient
      Recipient recipient = RecipientFactory.getRecipientsFromString(
              context,
              number,
              false
      ).getPrimaryRecipient();
      SmsDatabase db = DatabaseFactory.getSmsDatabase(context);
      db.insertKeysChanged(
              messageRecord.getThreadId(),
              recipient,
              messageRecord.getTimestamp()
      );
    }
  }

  private MessageRecord getMessageRecord(Recipients recipients) {
    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    Cursor cursor = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);

    MmsSmsDatabase db = DatabaseFactory.getMmsSmsDatabase(context);
    MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
    MessageRecord messageRecord = null;
    if (cursor != null && cursor.moveToFirst()) {
      messageRecord = db.readerFor(cursor, masterSecret).getCurrent();
    }
    return messageRecord;
  }

  public void acceptMismatch(Recipients recipients){
    acceptMismatch(
            recipients,
            recipients.getPrimaryRecipient().getNumber()
    );
  }

  public void acceptMismatch(Recipients recipients, final String number){
    final MessageRecord messageRecord = getMessageRecord(recipients);
    new AsyncTask<Void, Void, Void>() {
      MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
      IdentityKeyMismatch mismatch;

      @Override
      protected Void doInBackground(Void... params) {
        if (messageRecord != null && !messageRecord.getIdentityKeyMismatches().isEmpty()) {
          mismatch = messageRecord.getIdentityKeyMismatches().get(0);
          IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
          identityDatabase.saveIdentity(
                  mismatch.getRecipientId(),
                  mismatch.getIdentityKey()
          );

          processMessageRecord(messageRecord);
          processPendingMessageRecords(messageRecord.getThreadId());
          notifyClient(messageRecord, number);

          return null;
        }
        Log.e(TAG, "mismatches is empty", new Exception());
        return null;
      }

      private void processMessageRecord(MessageRecord messageRecord) {
        if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord);
        else                            processIncomingMessageRecord(messageRecord);
      }

      private void processPendingMessageRecords(long threadId) {
        MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
        Cursor cursor = mmsSmsDatabase.getIdentityConflictMessagesForThread(threadId);
        MmsSmsDatabase.Reader reader = mmsSmsDatabase.readerFor(cursor, masterSecret);
        MessageRecord         record;

        try {
          while ((record = reader.getNext()) != null) {
            for (IdentityKeyMismatch recordMismatch : record.getIdentityKeyMismatches()) {
              if (mismatch.equals(recordMismatch)) {
                processMessageRecord(record);
              }
            }
          }
        } finally {
            if (reader != null)
            reader.close();
        }
      }

      private void processOutgoingMessageRecord(MessageRecord messageRecord) {
        SmsDatabase smsDatabase        = DatabaseFactory.getSmsDatabase(context);
        MmsDatabase mmsDatabase        = DatabaseFactory.getMmsDatabase(context);
        MmsAddressDatabase mmsAddressDatabase = DatabaseFactory.getMmsAddressDatabase(context);

        if (messageRecord.isMms()) {
          mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                  mismatch.getRecipientId(),
                  mismatch.getIdentityKey()
          );

          Recipients recipients = mmsAddressDatabase.getRecipientsForId(messageRecord.getId());

          if (recipients.isGroupRecipient()){
            MessageSender.resendGroupMessage(context, masterSecret, messageRecord, mismatch.getRecipientId());
          } else {
            MessageSender.resend(context, masterSecret, messageRecord);
          }
        } else {
          smsDatabase.removeMismatchedIdentity(
                  messageRecord.getId(),
                  mismatch.getRecipientId(),
                  mismatch.getIdentityKey()
          );
          MessageSender.resend(context, masterSecret, messageRecord);
        }
      }

      private void processIncomingMessageRecord(MessageRecord messageRecord) {
        try {
          PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
          SmsDatabase  smsDatabase  = DatabaseFactory.getSmsDatabase(context);

          smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
          mismatch.getRecipientId(),
          mismatch.getIdentityKey());

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
