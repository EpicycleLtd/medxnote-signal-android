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
import android.content.Context;
import android.util.Log;

import com.medxnote.securesms.crypto.IdentityKeyUtil;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.crypto.MasterSecretUtil;
import com.medxnote.securesms.util.TextSecurePreferences;
import com.medxnote.securesms.util.Util;
import com.medxnote.securesms.util.VersionTracker;
import com.medxnote.securesms.crypto.InvalidPassphraseException;

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

        if (BuildConfig.APPLICATION_ID.contains("uk") && passphrase.length() < 6) {
            Toast.makeText(this, R.string.PassphraseCreateActivity_minimum_length, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!passphrase.equals(passphraseRepeat)) {
            Toast.makeText(this, R.string.PassphraseCreateActivity_passphrases_dont_match_exclamation, Toast.LENGTH_SHORT).show();
            this.passphraseEdit.setText("");
            this.passphraseRepeatEdit.setText("");
            return;
        }

        String original = MasterSecretUtil.UNENCRYPTED_PASSPHRASE;
        new ChangePassphraseTask(this).execute(original, passphrase);
    }



    private class ChangePassphraseTask extends AsyncTask<String, Void, MasterSecret> {
        private final Context context;

        public ChangePassphraseTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            createLayout.setVisibility(View.GONE);
            progressLayout.setVisibility(View.VISIBLE);
        }

        @Override
        protected MasterSecret doInBackground(String... params) {
            try {
                MasterSecret masterSecret = MasterSecretUtil.changeMasterSecretPassphrase(context, params[0], params[1]);
                TextSecurePreferences.setPasswordDisabled(context, false);
                TextSecurePreferences.setPassphraseTimeoutEnabled(PassphraseCreateActivity.this);

                return masterSecret;

            } catch (InvalidPassphraseException e) {
                Log.w(PassphraseChangeActivity.class.getSimpleName(), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(MasterSecret masterSecret) {
            createLayout.setVisibility(View.VISIBLE);
            progressLayout.setVisibility(View.GONE);

            if (masterSecret != null) {
                setMasterSecret(masterSecret);
            }
        }
    }

  @Override
  protected void cleanup() {
    System.gc();
  }
}
