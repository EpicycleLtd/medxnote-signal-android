package com.medxnote.securesms.preferences;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;

import com.medxnote.securesms.R;
import com.medxnote.securesms.crypto.MasterSecret;

/**
 * Created by jnovkovic on 5/19/17.
 */

public class AboutPreferenceFragment extends PreferenceFragment {

    private static final String TAG = AboutPreferenceFragment.class.getSimpleName();

    private static final String VERSION_PREF = "pref_version";

    private MasterSecret masterSecret;

    @Override
    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        masterSecret = getArguments().getParcelable("master_secret");
        addPreferencesFromResource(R.xml.preferences_about);

        Preference version = this.findPreference(VERSION_PREF);
        version.setSummary(getVersion(getActivity()));

    }

    private @NonNull
    String getVersion(@Nullable Context context) {
        try {
            if (context == null) return "";
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e);
            return context.getString(R.string.app_name);
        }
    }
}
