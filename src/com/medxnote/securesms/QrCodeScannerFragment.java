package com.medxnote.securesms;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import com.medxnote.securesms.components.camera.CameraView;
import com.medxnote.securesms.components.camera.CameraView.PreviewCallback;
import com.medxnote.securesms.components.camera.CameraView.PreviewFrame;
import com.medxnote.securesms.util.Util;
import com.medxnote.securesms.util.ViewUtil;

/**
 * @author skal1ozz on 13.12.16.
 */

public class QrCodeScannerFragment extends Fragment implements PreviewCallback {

    private static final String TAG = QrCodeScannerActivity.class.getSimpleName();

    private final MultiFormatReader reader = new MultiFormatReader();

    private CameraView scannerView;
    private PreviewFrame previewFrame;
    private ScanningThread scanningThread;
    private ScanListener scanListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup view = ViewUtil.inflate(inflater, container, R.layout.qr_code_scan_fragment);
        this.scannerView  = ViewUtil.findById(view, R.id.scanner);

        this.scannerView.onResume();
        this.scannerView.setPreviewCallback(this);

        scannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scannerView.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Log.w(TAG, "Autofocus Done", new Exception());
                    }
                });
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        this.scannerView.onResume();
        this.scannerView.setPreviewCallback(this);
        this.previewFrame   = null;
        this.scanningThread = new ScanningThread();
        this.scanningThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        this.scannerView.onPause();
        this.scanningThread.stopScanning();
    }

    @Override
    public void onPreviewFrame(@NonNull CameraView.PreviewFrame frame) {
        Context context = getActivity();

        try {
            if (context != null) {
                synchronized (this) {
                    this.previewFrame = frame;
                    this.notify();
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, e);
        }
    }

    public void setScanListener(ScanListener scanListener) {
        this.scanListener = scanListener;
    }

    private class ScanningThread extends Thread {

        private boolean scanning = true;

        @Override
        public void run() {
            while (true) {
                PreviewFrame ourFrame;

                synchronized (QrCodeScannerFragment.this) {
                    while (scanning && previewFrame == null) {
                        Util.wait(QrCodeScannerFragment.this, 0);
                    }

                    if (!scanning) return;
                    else           ourFrame = previewFrame;

                    previewFrame = null;
                }

                String data = getData(ourFrame.getData(), ourFrame.getWidth(), ourFrame.getHeight(), ourFrame.getOrientation());

                if (data != null && scanListener != null) {
                    scanListener.onDataFound(data);
                    return;
                }
            }
        }

        public void stopScanning() {
            synchronized (QrCodeScannerFragment.this) {
                scanning = false;
                QrCodeScannerFragment.this.notify();
            }
        }

        private @Nullable String getData(byte[] data, int width, int height, int orientation) {
            try {
                if (orientation == Configuration.ORIENTATION_PORTRAIT || orientation == 90) {
                    byte[] rotatedData = new byte[data.length];

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            rotatedData[x * height + height - y - 1] = data[x + y * width];
                        }
                    }

                    int tmp = width;
                    width  = height;
                    height = tmp;
                    data   = rotatedData;
                }

                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height,
                        0, 0, width, height,
                        false);

                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                Result result = reader.decode(bitmap);

                if (result != null) return result.getText();

            } catch (NullPointerException e) {
                Log.w(TAG, e);
            } catch (NotFoundException e) {
                // Thanks ZXing...
            }

            return null;
        }
    }

    interface ScanListener {
        void onDataFound(String data);
    }

}