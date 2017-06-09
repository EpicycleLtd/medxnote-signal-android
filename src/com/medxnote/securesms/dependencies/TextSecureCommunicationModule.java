package com.medxnote.securesms.dependencies;

import android.content.Context;

import com.medxnote.securesms.BuildConfig;
import com.medxnote.securesms.DeviceListFragment;
import com.medxnote.securesms.crypto.storage.SignalProtocolStoreImpl;
import com.medxnote.securesms.jobs.AttachmentDownloadJob;
import com.medxnote.securesms.jobs.CleanPreKeysJob;
import com.medxnote.securesms.jobs.CreateSignedPreKeyJob;
import com.medxnote.securesms.jobs.DeliveryReadJob;
import com.medxnote.securesms.jobs.DeliveryReceiptJob;
import com.medxnote.securesms.jobs.MultiDeviceContactUpdateJob;
import com.medxnote.securesms.jobs.MultiDeviceReadUpdateJob;
import com.medxnote.securesms.jobs.PushEditedGroupJob;
import com.medxnote.securesms.jobs.PushEditedMediaSendJob;
import com.medxnote.securesms.jobs.PushEditedTextJob;
import com.medxnote.securesms.jobs.PushMediaSendJob;
import com.medxnote.securesms.jobs.PushNotificationReceiveJob;
import com.medxnote.securesms.jobs.RefreshAttributesJob;
import com.medxnote.securesms.jobs.RefreshPreKeysJob;
import com.medxnote.securesms.push.SecurityEventListener;
import com.medxnote.securesms.push.TextSecurePushTrustStore;
import com.medxnote.securesms.service.MessageRetrievalService;
import com.medxnote.securesms.util.TextSecurePreferences;
import com.medxnote.securesms.jobs.GcmRefreshJob;
import com.medxnote.securesms.jobs.MultiDeviceGroupUpdateJob;
import com.medxnote.securesms.jobs.PushGroupSendJob;
import com.medxnote.securesms.jobs.PushTextSendJob;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReadJob.class,
                                     DeliveryReceiptJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushEditedTextJob.class,
                                     PushEditedMediaSendJob.class,
                                     PushEditedGroupJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class})
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides SignalServiceAccountManager provideTextSecureAccountManager() {
    return new SignalServiceAccountManager(BuildConfig.TEXTSECURE_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public SignalServiceMessageSender create() {
        return new SignalServiceMessageSender(BuildConfig.TEXTSECURE_URL,
                                              new TextSecurePushTrustStore(context),
                                              TextSecurePreferences.getLocalNumber(context),
                                              TextSecurePreferences.getPushServerPassword(context),
                                              new SignalProtocolStoreImpl(context),
                                              BuildConfig.USER_AGENT,
                                              Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
      }
    };
  }

  @Provides SignalServiceMessageReceiver provideTextSecureMessageReceiver() {
    return new SignalServiceMessageReceiver(BuildConfig.TEXTSECURE_URL,
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context),
                                         BuildConfig.USER_AGENT);
  }

  public interface TextSecureMessageSenderFactory {
    SignalServiceMessageSender create();
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

}
