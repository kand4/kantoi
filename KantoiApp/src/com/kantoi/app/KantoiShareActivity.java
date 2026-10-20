package com.kantoi.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Titik Integrasi Perkongsian Sistem ("Share to KANTOI"):
 * Membolehkan pengguna menekan "Share" pada sebarang teks, pautan, atau fail APK
 * di WhatsApp, Telegram, Facebook, atau Pelayar Web dan terus mengimbasnya dengan KANTOI.
 */
public class KantoiShareActivity extends Activity {
    private static final String TAG = "KantoiShare";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && !sharedText.trim().isEmpty()) {
                    handleSharedText(sharedText.trim());
                    return;
                }
            } else if (type.contains("vnd.android.package-archive") || type.contains("octet-stream") || type.contains("application/")) {
                Uri fileUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (fileUri != null) {
                    handleSharedApkUri(fileUri);
                    return;
                }
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    handleSharedText(data.toString());
                    return;
                } else if ("file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)) {
                    handleSharedApkUri(data);
                    return;
                }
            }
        }

        Toast.makeText(this, "Tiada kandungan teks, pautan atau fail APK yang sah diterima.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void handleSharedText(String text) {
        // 1. Semak sama ada teks mengandungi URL
        Pattern urlPattern = Pattern.compile("https?://[\\w\\-\\.]+(?::[0-9]+)?(?:/[^\\s]*)?", Pattern.CASE_INSENSITIVE);
        Matcher um = urlPattern.matcher(text);
        if (um.find()) {
            final String foundUrl = um.group();
            KantoiVirusTotalEngine.showUrlScanDialog(this, foundUrl);
            return;
        }

        // 2. Semak sama ada teks mengandungi nombor telefon scammer
        Pattern phonePattern = Pattern.compile("\\b(?:\\+?60|0)1[0-9]-?[0-9]{7,8}\\b");
        Matcher pm = phonePattern.matcher(text);
        if (pm.find()) {
            final String norm = KantoiThreatEngine.normalizeToLocal(pm.group());
            KantoiThreatEngine engine = KantoiThreatEngine.getInstance(this);
            KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(norm);
            if (threat != null) {
                engine.showThreatDialog(this, threat);
            } else {
                engine.showRegisterScammerDialog(this, norm);
            }
            return;
        }

        // 3. Fallback: Buka URL scan dialog dengan teks yang dikongsi
        KantoiVirusTotalEngine.showUrlScanDialog(this, text);
    }

    private void handleSharedApkUri(final Uri uri) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Sedang menganalisis fail APK dengan Enjin Heuristik & VirusTotal...");
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File tempApk = null;
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    tempApk = new File(uri.getPath());
                } else {
                    tempApk = KantoiVirusTotalEngine.copyUriToTempFile(KantoiShareActivity.this, uri, "shared_analysis.apk");
                }

                if (tempApk == null || !tempApk.exists()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.dismiss();
                            Toast.makeText(KantoiShareActivity.this, "Gagal membaca fail APK yang dikongsi.", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                    return;
                }

                final KantoiVirusTotalEngine.ApkScanReport report = KantoiVirusTotalEngine.analyzeApkFile(KantoiShareActivity.this, tempApk);
                KantoiVirusTotalEngine.queryVirusTotalAsync(KantoiShareActivity.this, report, new KantoiVirusTotalEngine.VtReportCallback() {
                    @Override
                    public void onResult(final KantoiVirusTotalEngine.ApkScanReport fullReport) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.dismiss();
                                KantoiVirusTotalEngine.showApkScanReportDialog(KantoiShareActivity.this, fullReport);
                            }
                        });
                    }
                });
            }
        }).start();
    }
}
