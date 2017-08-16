package com.medxnote.securesms;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.util.TextSecurePreferences;
import com.medxnote.securesms.crypto.IdentityKeyUtil;
import com.medxnote.securesms.crypto.MasterSecretUtil;
import com.medxnote.securesms.util.Util;
import com.medxnote.securesms.util.VersionTracker;

public class PassphraseInitialActivity extends PassphraseActivity {

    public PassphraseInitialActivity() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.initial_passphrase_activity);

        initializeResources();
    }

    private void initializeResources() {
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setCustomView(R.layout.centered_app_title);

        new SecretGenerator().execute(MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
    }

    private class SecretGenerator extends AsyncTask<String, Void, Void> {
        private MasterSecret masterSecret;

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(String... params) {
            String passphrase = params[0];
            masterSecret      = MasterSecretUtil.generateMasterSecret(PassphraseInitialActivity.this,
                    passphrase);

            MasterSecretUtil.generateAsymmetricMasterSecret(PassphraseInitialActivity.this, masterSecret);
            IdentityKeyUtil.generateIdentityKeys(PassphraseInitialActivity.this);
            VersionTracker.updateLastSeenVersion(PassphraseInitialActivity.this);
            TextSecurePreferences.setLastExperienceVersionCode(PassphraseInitialActivity.this, Util.getCurrentApkReleaseVersion(PassphraseInitialActivity.this));
            TextSecurePreferences.setPasswordDisabled(PassphraseInitialActivity.this, true);

            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            setMasterSecret(masterSecret);
        }
    }

    @Override
    protected void cleanup() {
        System.gc();
    }
}
