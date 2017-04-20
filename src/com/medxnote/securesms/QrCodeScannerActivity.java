package com.medxnote.securesms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.util.Log;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.util.DynamicLanguage;
import com.medxnote.securesms.util.DynamicTheme;

/**
 * @author skal1ozz on 13.12.16.
 */
public class QrCodeScannerActivity extends PassphraseRequiredActionBarActivity implements QrCodeScannerFragment.ScanListener {

    private static final String TAG = QrCodeScannerActivity.class.getSimpleName();

    private final DynamicTheme dynamicTheme    = new DynamicTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    private QrCodeScannerFragment mQrCodeScannerFragment;

    @Override
    public void onPreCreate() {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret masterSecret) {

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.QrCodeScannerActivity__title);
        }

        this.mQrCodeScannerFragment = new QrCodeScannerFragment();
        this.mQrCodeScannerFragment.setScanListener(this);

        initFragment(android.R.id.content, mQrCodeScannerFragment, masterSecret);
    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);
    }

    @Override
    public void onDataFound(String data) {
        ((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);
        Intent intent = new Intent();
        intent.putExtra("data", data);
        setResult(RESULT_OK, intent);
        finish();
    }
}