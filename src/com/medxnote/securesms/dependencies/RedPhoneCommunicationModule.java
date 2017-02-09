package com.medxnote.securesms.dependencies;

import android.content.Context;

import com.medxnote.redphone.signaling.RedPhoneAccountManager;
import com.medxnote.redphone.signaling.RedPhoneTrustStore;
import com.medxnote.securesms.BuildConfig;
import com.medxnote.securesms.jobs.RefreshAttributesJob;
import com.medxnote.securesms.util.TextSecurePreferences;
import com.medxnote.securesms.jobs.GcmRefreshJob;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {GcmRefreshJob.class,
                                     RefreshAttributesJob.class})
public class RedPhoneCommunicationModule {

  private final Context context;

  public RedPhoneCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides
  RedPhoneAccountManager provideRedPhoneAccountManager() {
    return new RedPhoneAccountManager(BuildConfig.REDPHONE_MASTER_URL,
                                      new RedPhoneTrustStore(context),
                                      TextSecurePreferences.getLocalNumber(context),
                                      TextSecurePreferences.getPushServerPassword(context));
  }

}
