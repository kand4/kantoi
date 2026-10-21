package com.kantoi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class KantoiClipboardService extends Service {
    private static final String TAG = "KantoiClipService";
    private static final String CHANNEL_ID = "kantoi_clipboard_service_channel";
    private static final int NOTIF_ID = 9005;

    private ClipboardManager mClipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener mClipListener;
    private String mLastHandledText = "";

    // Pemasa berkala untuk auto-sync risikan scammer dari Telegram @crowdcaller
    private Handler mSyncHandler;
    private Runnable mSyncRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "KantoiClipboardService created.");
        try {
            mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (mClipboardManager != null) {
                mClipListener = new ClipboardManager.OnPrimaryClipChangedListener() {
                    @Override
                    public void onPrimaryClipChanged() {
                        handlePrimaryClip();
                    }
                };
                mClipboardManager.addPrimaryClipChangedListener(mClipListener);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error registering clip listener: " + t.getMessage(), t);
        }

        // Mulakan semakan auto-sync senyap Telegram OTA berkala
        try {
            mSyncHandler = new Handler(Looper.getMainLooper());
            mSyncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        KantoiThreatEngine.getInstance(KantoiClipboardService.this)
                            .checkAndAutoSyncTelegramAsync(KantoiClipboardService.this, false);
                    } catch (Throwable ignored) {}
                    if (mSyncHandler != null) {
                        mSyncHandler.postDelayed(this, 3 * 60 * 60 * 1000L); // Semak setiap 3 jam
                    }
                }
            };
            mSyncHandler.postDelayed(mSyncRunnable, 25 * 1000L); // 25 saat selepas perkhidmatan aktif
        } catch (Throwable t) {
            Log.e(TAG, "Error initializing OTA auto sync: " + t.getMessage(), t);
        }
    }

    private void handlePrimaryClip() {
        if (mClipboardManager == null) return;
        try {
            ClipData clip = mClipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence textSeq = clip.getItemAt(0).getText();
                if (textSeq != null) {
                    String text = textSeq.toString().trim();
                    if (!text.isEmpty() && !text.equals(mLastHandledText)) {
                        mLastHandledText = text;
                        Log.i(TAG, "Clipboard text detected: " + ((text.length() > 30) ? text.substring(0, 30) + "..." : text));
                        KantoiThreatEngine.getInstance(this).handleClipboardText(this, text);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error reading clip: " + t.getMessage(), t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mClipboardManager != null && mClipListener != null) {
                mClipboardManager.removePrimaryClipChangedListener(mClipListener);
            }
        } catch (Throwable ignored) {}

        try {
            if (mSyncHandler != null && mSyncRunnable != null) {
                mSyncHandler.removeCallbacks(mSyncRunnable);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
