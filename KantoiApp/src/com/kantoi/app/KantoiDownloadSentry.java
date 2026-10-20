package com.kantoi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

/**
 * Pengawal Folder Muat Turun Automatik (Download Sentry):
 * Mengesan kehadiran sebarang fail .apk baharu yang dimuat turun dari WhatsApp, Telegram,
 * atau Pelayar Web, dan menganalisisnya secara serta-merta sebelum pengguna sempat memasangnya.
 */
public class KantoiDownloadSentry {
    private static final String TAG = "KantoiSentry";
    private static final String CHANNEL_ID = "kantoi_apk_sentry_channel";
    private static final int NOTIF_ID = 9008;

    private static FileObserver sObserver = null;
    private static boolean sIsWatching = false;

    public static synchronized void startWatching(final Context context) {
        if (sIsWatching || context == null) return;
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir == null || !downloadDir.exists()) {
                downloadDir = new File("/storage/emulated/0/Download");
            }
            if (!downloadDir.exists()) {
                // Cuba cipta atau keluar jika tiada laluan
                downloadDir.mkdirs();
            }

            final String downloadPath = downloadDir.getAbsolutePath();
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            sObserver = new FileObserver(downloadPath, FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                @Override
                public void onEvent(int event, final String path) {
                    if (path == null) return;
                    if (path.toLowerCase().endsWith(".apk")) {
                        Log.w(TAG, "Fail APK baharu dikesan dalam folder muat turun: " + path);
                        // Berikan sela masa 1.2s untuk memastikan fail selesai dimuat turun sepenuhnya
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                handleApkDetected(context, new File(downloadPath, path));
                            }
                        }, 1200);
                    }
                }
            };
            sObserver.startWatching();
            sIsWatching = true;
            Log.i(TAG, "KantoiDownloadSentry kini aktif memantau: " + downloadPath);
        } catch (Throwable t) {
            Log.e(TAG, "startWatching error: " + t.getMessage());
        }
    }

    private static void handleApkDetected(final Context context, final File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) return;
        try {
            final KantoiVirusTotalEngine.ApkScanReport report = KantoiVirusTotalEngine.analyzeApkFile(context, apkFile);
            if (report.riskScore >= 2 || report.isBankingTrojanSignature) {
                // Semak juga di VirusTotal jika ada kunci API
                KantoiVirusTotalEngine.queryVirusTotalAsync(context, report, new KantoiVirusTotalEngine.VtReportCallback() {
                    @Override
                    public void onResult(KantoiVirusTotalEngine.ApkScanReport fullReport) {
                        showThreatNotification(context, fullReport);
                    }
                });
            }
        } catch (Throwable t) {
            Log.e(TAG, "handleApkDetected error: " + t.getMessage());
        }
    }

    private static void showThreatNotification(Context context, KantoiVirusTotalEngine.ApkScanReport report) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Kantoi Pengimbas APK Muat Turun",
                    NotificationManager.IMPORTANCE_HIGH
                );
                ch.setDescription("Amaran masa nyata apabila fail APK berniat jahat / Trojan dimuat turun.");
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }

            Intent it = new Intent(context, KantoiShareActivity.class);
            it.setDataAndType(Uri.fromFile(report.file), "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pi = PendingIntent.getActivity(
                context,
                0,
                it,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.ic_dialog_alert)
              .setContentTitle("🚨 KANTOI: FAIL APK BERBAHAYA DIKESAN!")
              .setContentText("Fail [" + report.fileName + "] disyaki Trojan Perbankan! Ketik untuk memadamkannya.")
              .setAutoCancel(true)
              .setContentIntent(pi);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                nb.setPriority(Notification.PRIORITY_MAX);
            }

            nm.notify(NOTIF_ID, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showThreatNotification error: " + t.getMessage());
        }
    }
}
