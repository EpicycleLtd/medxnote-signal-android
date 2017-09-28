package com.medxnote.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.ApplicationContext;
import com.medxnote.securesms.database.MessagingDatabase.SyncMessageId;
import com.medxnote.securesms.database.NotInDirectoryException;
import com.medxnote.securesms.database.TextSecureDirectory;
import com.medxnote.securesms.dependencies.TextSecureCommunicationModule;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.service.KeyCachingService;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.inject.Inject;

public abstract class PushReceivedJob extends ContextJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  protected PushReceivedJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  public void handle(SignalServiceEnvelope envelope, boolean sendExplicitReceipt) {
    if (!isActiveNumber(context, envelope.getSource())) {
      TextSecureDirectory directory           = TextSecureDirectory.getInstance(context);
      ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
      contactTokenDetails.setNumber(envelope.getSource());

      directory.setNumber(contactTokenDetails, true);

      Recipients recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
      ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context, KeyCachingService.getMasterSecret(context), recipients));
    }

    if (envelope.isReceipt()) {
      handleReceipt(envelope);
    } else if (envelope.isRead()){
      handleRead(envelope);
    } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage()) {
      handleMessage(envelope, sendExplicitReceipt);
    } else {
      Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, boolean sendExplicitReceipt) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    if (!recipients.isBlocked()) {
      long messageId = DatabaseFactory.getPushDatabase(context).insert(envelope);
      jobManager.add(new PushDecryptJob(context, messageId, envelope.getSource()));
    } else {
      Log.w(TAG, "*** Received blocked push message, ignoring...");
    }

    if (sendExplicitReceipt) {
      jobManager.add(new DeliveryReceiptJob(context, envelope.getSource(),
                                            envelope.getTimestamp(),
                                            envelope.getRelay()));
    }
  }

  private void handleReceipt(SignalServiceEnvelope envelope) {
    Log.w(TAG, String.format("Received receipt: (XXXXX, %d)", envelope.getTimestamp()));
    Log.w(TAG, "XXXXX " + envelope.getSource());
    SyncMessageId syncMessageId = new SyncMessageId(
      envelope.getSource(),
      envelope.getTimestamp(),
      envelope.getDeliveryTimestamp()
    );
    setDateGroupReceived(syncMessageId);
    boolean isGroupReceipt = DatabaseFactory.getReceiptDatabase(context).isGroupReceipt(syncMessageId);
    int count = DatabaseFactory.getReceiptDatabase(context).getCountUnreceivedMessage(syncMessageId);
    //if(!isGroupReceipt || count < 1) {
      markReceipt(syncMessageId);
    //}
  }

  private void setDateGroupReceived(SyncMessageId syncMessageId){
    DatabaseFactory.getReceiptDatabase(context).setDateReceived(syncMessageId);
  }

  private void markReceipt(SyncMessageId syncMessageId){
    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(syncMessageId);
    DatabaseFactory.getMmsSmsDatabase(context).setReceived(syncMessageId);
  }

  private void handleRead(SignalServiceEnvelope envelope) {
    Log.w(TAG, String.format("Received read: (XXXXX, %d)", envelope.getTimestamp()));
    SyncMessageId syncMessageId = new SyncMessageId(
        envelope.getSource(),
        envelope.getTimestamp(),
        envelope.getDeliveryTimestamp()
    );
    boolean isGroupReceipt = DatabaseFactory.getReceiptDatabase(context).isGroupReceipt(syncMessageId);
    setDateGroupRead(syncMessageId);
    int count = DatabaseFactory.getReceiptDatabase(context).getCountUnreadMessage(syncMessageId);
    //if(!isGroupReceipt || count < 1) {
      markRead(syncMessageId);
    //}
  }

  private void setDateGroupRead(SyncMessageId syncMessageId){
    DatabaseFactory.getReceiptDatabase(context).setDateRead(syncMessageId);
  }

  private void markRead(SyncMessageId syncMessageId){
    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(syncMessageId);
    DatabaseFactory.getMmsSmsDatabase(context).setAsRead(syncMessageId);
  }

  private boolean isActiveNumber(Context context, String e164number) {
    boolean isActiveNumber;

    try {
      isActiveNumber = TextSecureDirectory.getInstance(context).isSecureTextSupported(e164number);
    } catch (NotInDirectoryException e) {
      isActiveNumber = false;
    }

    return isActiveNumber;
  }


}
