/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.medxnote.securesms;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.medxnote.securesms.crypto.IdentityKeyUtil;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.crypto.MasterSecretUtil;
import com.medxnote.securesms.util.TextSecurePreferences;
import com.medxnote.securesms.util.Util;
import com.medxnote.securesms.util.VersionTracker;

/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseCreateActivity extends PassphraseActivity {

    private LinearLayout createLayout;
    private LinearLayout progressLayout;

    private EditText passphraseEdit;
    private EditText passphraseRepeatEdit;
    private Button okButton;

    public PassphraseCreateActivity() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.create_passphrase_activity);

    initializeResources();
  }

  private void initializeResources() {
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
    getSupportActionBar().setCustomView(R.layout.centered_app_title);

      this.createLayout         = (LinearLayout)findViewById(R.id.create_layout);
      this.progressLayout       = (LinearLayout)findViewById(R.id.progress_layout);
      this.passphraseEdit       = (EditText)    findViewById(R.id.passphrase_edit);
      this.passphraseRepeatEdit = (EditText)    findViewById(R.id.passphrase_edit_repeat);
      this.okButton             = (Button)      findViewById(R.id.ok_button);


      this.okButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
              verifyAndSavePassphrases();
          }
      });
  }

    private void verifyAndSavePassphrases() {
        if (Util.isEmpty(this.passphraseEdit) || Util.isEmpty(this.passphraseRepeatEdit)) {
            Toast.makeText(this, R.string.PassphraseCreateActivity_you_must_specify_a_password, Toast.LENGTH_SHORT).show();
            return;
        }

        String passphrase       = this.passphraseEdit.getText().toString();
        String passphraseRepeat = this.passphraseRepeatEdit.getText().toString();

        if (BuildConfig.APPLICATION_ID.contains("uk") && passphrase.length() < 4) {
            Toast.makeText(this, R.string.PassphraseCreateActivity_minimum_length, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!passphrase.equals(passphraseRepeat)) {
            Toast.makeText(this, R.string.PassphraseCreateActivity_passphrases_dont_match_exclamation, Toast.LENGTH_SHORT).show();
            this.passphraseEdit.setText("");
            this.passphraseRepeatEdit.setText("");
            return;
        }

        new SecretGenerator().execute(passphrase);
    }

  private class SecretGenerator extends AsyncTask<String, Void, Void> {
    private MasterSecret masterSecret;

    @Override
    protected void onPreExecute() {
        createLayout.setVisibility(View.GONE);
        progressLayout.setVisibility(View.VISIBLE);
    }

    @Override
    protected Void doInBackground(String... params) {
      String passphrase = params[0];
      masterSecret      = MasterSecretUtil.generateMasterSecret(PassphraseCreateActivity.this,
                                                                passphrase);

      MasterSecretUtil.generateAsymmetricMasterSecret(PassphraseCreateActivity.this, masterSecret);
      IdentityKeyUtil.generateIdentityKeys(PassphraseCreateActivity.this);
      VersionTracker.updateLastSeenVersion(PassphraseCreateActivity.this);
      TextSecurePreferences.setLastExperienceVersionCode(PassphraseCreateActivity.this, Util.getCurrentApkReleaseVersion(PassphraseCreateActivity.this));
      TextSecurePreferences.setPasswordDisabled(PassphraseCreateActivity.this, false);
        TextSecurePreferences.setPassphraseTimeoutEnabled(PassphraseCreateActivity.this);

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
