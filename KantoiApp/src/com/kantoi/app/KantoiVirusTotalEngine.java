package com.kantoi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Enjin Pengimbas Keselamatan Dwi-Lapisan KANTOI:
 * 1. Enjin Heuristik Tempatan (Offline 0ms, Zero-Data) - Analisis Kebenaran Trojan Perbankan (Accessibility + SMS)
 * 2. Integrasi API VirusTotal v3 (70+ Enjin Antivirus Global & Pengesanan Phishing URL)
 */
public class KantoiVirusTotalEngine {
    private static final String TAG = "KantoiVT";
    private static final String PREFS_NAME = "kantoi_virustotal_prefs";
    private static final String KEY_API_KEY = "vt_api_key";

    public static class ApkScanReport {
        public File file;
        public String fileName = "";
        public long fileSize = 0;
        public String sha256 = "";
        public String packageName = "";
        public String versionName = "";
        public boolean hasAccessibilityService = false;
        public boolean hasSmsPermissions = false;
        public boolean hasInstallerPermission = false;
        public boolean hasOverlayPermission = false;
        public boolean isBankingTrojanSignature = false;
        public int riskScore = 0; // 0=Clean, 1-2=Warning, 3+=Dangerous
        public String riskTitle = "✅ BERSIH";
        public String riskDescription = "";
        public List<String> flaggedPermissions = new ArrayList<String>();

        // VirusTotal Results
        public boolean vtQueried = false;
        public boolean vtFound = false;
        public int vtMalicious = 0;
        public int vtSuspicious = 0;
        public int vtHarmless = 0;
        public int vtUndetected = 0;
        public String vtSuggestedThreat = "";
    }

    public interface VtReportCallback {
        void onResult(ApkScanReport report);
    }

    public interface VtUrlCallback {
        void onResult(String targetUrl, boolean found, int malicious, int suspicious, int harmless, String errorMsg);
    }

    // Multi-Layer Bitwise Obfuscated Security Seed & Vector Mask
    // Melindungi kunci lalai daripada carian teks statik / decompiler (strings/hex search)
    private static final byte[] SEC_SEED_TABLE = new byte[] {
        (byte) 0x63, (byte) 0xa3, (byte) 0x0c, (byte) 0x10, (byte) 0xa0, (byte) 0x1f, (byte) 0xef, (byte) 0x73,
        (byte) 0x6c, (byte) 0xab, (byte) 0x5d, (byte) 0x43, (byte) 0xf5, (byte) 0x1d, (byte) 0xeb, (byte) 0x23,
        (byte) 0x3d, (byte) 0xfb, (byte) 0x0d, (byte) 0x12, (byte) 0xa2, (byte) 0x1f, (byte) 0xb9, (byte) 0x22,
        (byte) 0x39, (byte) 0xfb, (byte) 0x5b, (byte) 0x46, (byte) 0xf2, (byte) 0x1d, (byte) 0xef, (byte) 0x22,
        (byte) 0x3d, (byte) 0xf8, (byte) 0x08, (byte) 0x44, (byte) 0xa5, (byte) 0x4d, (byte) 0xbc, (byte) 0x25,
        (byte) 0x3e, (byte) 0xac, (byte) 0x07, (byte) 0x46, (byte) 0xf6, (byte) 0x1e, (byte) 0xb5, (byte) 0x77,
        (byte) 0x3a, (byte) 0xf8, (byte) 0x06, (byte) 0x17, (byte) 0xf2, (byte) 0x1d, (byte) 0xee, (byte) 0x2f,
        (byte) 0x62, (byte) 0xff, (byte) 0x0d, (byte) 0x10, (byte) 0xf0, (byte) 0x17, (byte) 0xbb, (byte) 0x22
    };

    private static final byte[] SEC_VECTOR_MASK = new byte[] {
        (byte) 0x5b, (byte) 0x9a, (byte) 0x3f, (byte) 0x71, (byte) 0xc4, (byte) 0x2e, (byte) 0x8d, (byte) 0x16
    };

    private static String resolveDefaultSecurityToken() {
        try {
            byte[] out = new byte[SEC_SEED_TABLE.length];
            for (int i = 0; i < SEC_SEED_TABLE.length; i++) {
                out[i] = (byte) (SEC_SEED_TABLE[i] ^ SEC_VECTOR_MASK[i % SEC_VECTOR_MASK.length]);
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String getCustomApiKey(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_API_KEY, "").trim();
    }

    public static String getApiKey(Context context) {
        String custom = getCustomApiKey(context);
        if (!custom.isEmpty()) {
            return custom; // Keutamaan kepada kunci peribadi yang dimasukkan pengguna (BYOK)
        }
        return resolveDefaultSecurityToken(); // Fallback kepada kunci komuniti yang di-obfuscate
    }

    public static void setApiKey(Context context, String key) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_KEY, key != null ? key.trim() : "").apply();
    }

    public static boolean hasApiKey(Context context) {
        return !getApiKey(context).isEmpty();
    }

    public static boolean hasCustomApiKey(Context context) {
        return !getCustomApiKey(context).isEmpty();
    }

    public static String computeSha256(File file) {
        if (file == null || !file.exists()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                md.update(buffer, 0, n);
            }
            fis.close();
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.e(TAG, "computeSha256 error: " + t.getMessage());
            return null;
        }
    }

    public static File copyUriToTempFile(Context context, Uri uri) {
        return copyUriToTempFile(context, uri, null);
    }

    public static File copyUriToTempFile(Context context, Uri uri, String fallbackName) {
        if (context == null || uri == null) return null;
        try {
            File cacheDir = new File(context.getCacheDir(), "apk_scan");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            String name = (fallbackName != null && !fallbackName.isEmpty()) ? fallbackName : ("scan_" + System.currentTimeMillis() + ".apk");
            File dest = new File(cacheDir, name);
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                fos.write(buf, 0, r);
            }
            fos.flush();
            fos.close();
            is.close();
            return dest;
        } catch (Throwable t) {
            Log.e(TAG, "copyUriToTempFile error: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Analisis Heuristik Tempatan (0ms, Luar Talian & Percuma)
     * Mengesan cap jari Trojan Perbankan (contoh: MaliBot, Godfather, Poslaju/Kenduri APK palsu)
     */
    public static ApkScanReport analyzeApkFile(Context context, File apkFile) {
        ApkScanReport report = new ApkScanReport();
        if (apkFile == null || !apkFile.exists()) return report;
        report.file = apkFile;
        report.fileName = apkFile.getName();
        report.fileSize = apkFile.length();
        report.sha256 = computeSha256(apkFile);

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
            if (pi != null) {
                report.packageName = (pi.packageName != null) ? pi.packageName : "Tidak diketahui";
                report.versionName = (pi.versionName != null) ? pi.versionName : "1.0";

                if (pi.requestedPermissions != null) {
                    for (String perm : pi.requestedPermissions) {
                        if (perm == null) continue;
                        String pLower = perm.toLowerCase();
                        if (pLower.contains("bind_accessibility_service") || pLower.contains("accessibility")) {
                            report.hasAccessibilityService = true;
                            report.flaggedPermissions.add("🚨 BIND_ACCESSIBILITY_SERVICE (Kawalan Penuh Skrin & Sentuhan Senyap)");
                        } else if (pLower.contains("receive_sms")) {
                            report.hasSmsPermissions = true;
                            report.flaggedPermissions.add("🚨 RECEIVE_SMS (Pintasan Mesej SMS / TAC Bank)");
                        } else if (pLower.contains("read_sms")) {
                            report.hasSmsPermissions = true;
                            report.flaggedPermissions.add("⚠️ READ_SMS (Membaca Mesej Peribadi Masuk)");
                        } else if (pLower.contains("request_install_packages")) {
                            report.hasInstallerPermission = true;
                            report.flaggedPermissions.add("⚠️ REQUEST_INSTALL_PACKAGES (Memasang Fail Tambahan)");
                        } else if (pLower.contains("system_alert_window")) {
                            report.hasOverlayPermission = true;
                            report.flaggedPermissions.add("⚠️ SYSTEM_ALERT_WINDOW (Tetingkap Terapung / Cloaking)");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getPackageArchiveInfo error: " + t.getMessage());
        }

        // Penilaian Risiko Heuristik
        if (report.hasAccessibilityService && report.hasSmsPermissions) {
            report.isBankingTrojanSignature = true;
            report.riskScore = 10;
            report.riskTitle = "🚨 BAHAYA MAKSIMA: TROJAN KELDAI PERBANKAN";
            report.riskDescription = "Fail ini dikesan meminta KAWALAN SKRIN (Accessibility) serentak dengan PEMBACAAN SMS! Ini adalah corak mutlak perisian hasad pencuri simpanan bank di Malaysia. JANGAN PASANG!";
        } else if (report.hasAccessibilityService) {
            report.riskScore = 6;
            report.riskTitle = "⚠️ AMARAN TINGGI: KAWALAN SKRIN SENYAP";
            report.riskDescription = "Fail ini meminta kebenaran Accessibility Service yang membolehkannya mengawal skrin dan membaca kata laluan secara senyap.";
        } else if (report.hasSmsPermissions) {
            report.riskScore = 5;
            report.riskTitle = "⚠️ AMARAN TINGGI: PINTASAN SMS / OTP";
            report.riskDescription = "Fail ini meminta kebenaran membaca mesej SMS peribadi dan berisiko memintas kod OTP perbankan anda.";
        } else if (report.hasInstallerPermission || report.hasOverlayPermission) {
            report.riskScore = 2;
            report.riskTitle = "⚠️ PERLU PERHATIAN: KEBENARAN KHAS";
            report.riskDescription = "Fail ini meminta kebenaran memasang aplikasi lain atau memaparkan lapisan skrin di atas aplikasi lain.";
        } else {
            report.riskScore = 0;
            report.riskTitle = "✅ BERSIH DARI CORAK TROJAN PERBANKAN";
            report.riskDescription = "Tiada kebenaran berbahaya membaca SMS atau Accessibility Service dikesan pada fail APK ini.";
        }

        return report;
    }

    /**
     * Semakan Hash Fail secara Asynchronous ke VirusTotal API v3
     */
    public static void queryVirusTotalAsync(final Context context, final ApkScanReport report, final VtReportCallback callback) {
        final String apiKey = getApiKey(context);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (apiKey.isEmpty() || report.sha256 == null || report.sha256.isEmpty()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(report);
                        }
                    });
                    return;
                }

                HttpURLConnection conn = null;
                try {
                    URL url = new URL("https://www.virustotal.com/api/v3/files/" + report.sha256);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-apikey", apiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            JSONObject attr = data.optJSONObject("attributes");
                            if (attr != null) {
                                report.vtQueried = true;
                                report.vtFound = true;
                                JSONObject stats = attr.optJSONObject("last_analysis_stats");
                                if (stats != null) {
                                    report.vtMalicious = stats.optInt("malicious", 0);
                                    report.vtSuspicious = stats.optInt("suspicious", 0);
                                    report.vtHarmless = stats.optInt("harmless", 0);
                                    report.vtUndetected = stats.optInt("undetected", 0);
                                }
                                JSONObject threatClass = attr.optJSONObject("popular_threat_classification");
                                if (threatClass != null) {
                                    report.vtSuggestedThreat = threatClass.optString("suggested_threat_label", "");
                                }

                                if (report.vtMalicious > 0) {
                                    report.riskScore += (report.vtMalicious * 2);
                                    report.riskTitle = "🚨 VIRUSTOTAL: " + report.vtMalicious + " ENJIN MENGESAN SEBAGAI MALWARE!";
                                    if (!report.vtSuggestedThreat.isEmpty()) {
                                        report.riskDescription = "Klasifikasi Makmal: " + report.vtSuggestedThreat + "\n" + report.riskDescription;
                                    }
                                }
                            }
                        }
                    } else if (code == 404) {
                        report.vtQueried = true;
                        report.vtFound = false; // Belum pernah diimbas di VT
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "VirusTotal query error: " + t.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(report);
                    }
                });
            }
        }).start();
    }

    /**
     * Semakan Reputasi URL Pancingan Data (Phishing) di VirusTotal API v3
     */
    public static void queryVirusTotalUrlAsync(final Context context, final String targetUrl, final VtUrlCallback callback) {
        final String apiKey = getApiKey(context);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (apiKey.isEmpty()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(targetUrl, false, 0, 0, 0, "Kunci API VirusTotal belum ditetapkan.");
                        }
                    });
                    return;
                }

                HttpURLConnection conn = null;
                try {
                    // ID URL VirusTotal adalah Base64 URL-safe tanpa padding '='
                    String urlId = Base64.encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP).trim();
                    URL url = new URL("https://www.virustotal.com/api/v3/urls/" + urlId);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("x-apikey", apiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    final int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            JSONObject attr = data.optJSONObject("attributes");
                            if (attr != null) {
                                JSONObject stats = attr.optJSONObject("last_analysis_stats");
                                final int mal = (stats != null) ? stats.optInt("malicious", 0) : 0;
                                final int susp = (stats != null) ? stats.optInt("suspicious", 0) : 0;
                                final int harm = (stats != null) ? stats.optInt("harmless", 0) : 0;
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onResult(targetUrl, true, mal, susp, harm, null);
                                    }
                                });
                                return;
                            }
                        }
                    } else if (code == 404) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(targetUrl, false, 0, 0, 0, "Tiada rekod sedia ada di VirusTotal bagi pautan ini.");
                            }
                        });
                        return;
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(targetUrl, false, 0, 0, 0, "Respons API VirusTotal: Kod " + code);
                            }
                        });
                        return;
                    }
                } catch (Throwable t) {
                    final String err = t.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(targetUrl, false, 0, 0, 0, "Ralat sambungan: " + err);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    // ==========================================
    // DIALOG & LAPORAN VISUAL SEKURITI (UI)
    // ==========================================

    public static void showApkScanReportDialog(final Activity activity, final ApkScanReport report) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder b = new AlertDialog.Builder(activity);

        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(activity, 16);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(0xFF0A0A0F); // Void Black
        rootBg.setCornerRadius(dpToPx(activity, 16));
        rootBg.setStroke(dpToPx(activity, 2), 0xFF00D4FF);
        root.setBackground(rootBg);

        // Top Header
        LinearLayout topHdr = new LinearLayout(activity);
        topHdr.setOrientation(LinearLayout.VERTICAL);
        topHdr.setGravity(Gravity.CENTER);
        topHdr.setPadding(0, 0, 0, dpToPx(activity, 10));

        TextView tvHdr = new TextView(activity);
        tvHdr.setText("🛡️ LAPORAN IMBASAN APK KANTOI");
        tvHdr.setTextColor(Color.WHITE);
        tvHdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvHdr.setTypeface(Typeface.DEFAULT_BOLD);
        topHdr.addView(tvHdr);
        root.addView(topHdr);

        // 1. Kad Status Risiko Utama
        LinearLayout verdictCard = new LinearLayout(activity);
        verdictCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable vBg = new GradientDrawable();
        vBg.setCornerRadius(dpToPx(activity, 12));
        if (report.riskScore >= 3 || report.isBankingTrojanSignature || report.vtMalicious > 0) {
            vBg.setColor(0xFF450A0A); // Deep Crimson Red
            vBg.setStroke(dpToPx(activity, 2), 0xFFEF4444);
        } else if (report.riskScore > 0) {
            vBg.setColor(0xFF422006); // Dark Amber
            vBg.setStroke(dpToPx(activity, 2), 0xFFF59E0B);
        } else {
            vBg.setColor(0xFF064E3B); // Dark Emerald Green
            vBg.setStroke(dpToPx(activity, 2), 0xFF10B981);
        }
        verdictCard.setBackground(vBg);
        verdictCard.setPadding(dpToPx(activity, 14), dpToPx(activity, 12), dpToPx(activity, 14), dpToPx(activity, 12));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(report.riskTitle);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        verdictCard.addView(tvTitle);

        TextView tvDesc = new TextView(activity);
        tvDesc.setText(report.riskDescription);
        tvDesc.setTextColor(0xFFE2E8F0);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDesc.setPadding(0, dpToPx(activity, 6), 0, 0);
        verdictCard.addView(tvDesc);

        root.addView(verdictCard);

        // 2. Maklumat Fail APK
        LinearLayout infoCard = new LinearLayout(activity);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable iBg = new GradientDrawable();
        iBg.setColor(0xFF141422);
        iBg.setCornerRadius(dpToPx(activity, 10));
        infoCard.setBackground(iBg);
        infoCard.setPadding(dpToPx(activity, 14), dpToPx(activity, 10), dpToPx(activity, 14), dpToPx(activity, 10));
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iLp.topMargin = dpToPx(activity, 10);
        infoCard.setLayoutParams(iLp);

        TextView tvFileName = new TextView(activity);
        tvFileName.setText("📁 Fail: " + report.fileName + " (" + String.format("%.2f", report.fileSize / (1024.0 * 1024.0)) + " MB)");
        tvFileName.setTextColor(Color.WHITE);
        tvFileName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvFileName.setTypeface(Typeface.DEFAULT_BOLD);
        infoCard.addView(tvFileName);

        TextView tvPackage = new TextView(activity);
        tvPackage.setText("📦 Pakej: " + report.packageName + " (v" + report.versionName + ")");
        tvPackage.setTextColor(0xFF94A3B8);
        tvPackage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvPackage.setPadding(0, dpToPx(activity, 4), 0, 0);
        infoCard.addView(tvPackage);

        TextView tvHash = new TextView(activity);
        String shortHash = (report.sha256 != null && report.sha256.length() > 16) 
            ? (report.sha256.substring(0, 16) + "...") : (report.sha256 != null ? report.sha256 : "N/A");
        tvHash.setText("🔑 SHA-256: " + shortHash + " (Ketik untuk Salin)");
        tvHash.setTextColor(0xFF38BDF8);
        tvHash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvHash.setTypeface(Typeface.DEFAULT_BOLD);
        tvHash.setPadding(0, dpToPx(activity, 6), 0, 0);
        tvHash.setClickable(true);
        tvHash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null && report.sha256 != null) {
                    cb.setPrimaryClip(ClipData.newPlainText("SHA256", report.sha256));
                    Toast.makeText(activity, "Hash SHA-256 disalin ke papan keratan!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        infoCard.addView(tvHash);

        root.addView(infoCard);

        // 3. Senarai Kebenaran Berbahaya yang Dikesan (Heuristik)
        if (!report.flaggedPermissions.isEmpty()) {
            TextView lblPerm = new TextView(activity);
            lblPerm.setText("⚠️ KEBENARAN BERISIKO TINGGI DIKESAN:");
            lblPerm.setTextColor(0xFFFCA5A5);
            lblPerm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lblPerm.setTypeface(Typeface.DEFAULT_BOLD);
            lblPerm.setPadding(0, dpToPx(activity, 12), 0, dpToPx(activity, 4));
            root.addView(lblPerm);

            for (String p : report.flaggedPermissions) {
                TextView tvP = new TextView(activity);
                tvP.setText("• " + p);
                tvP.setTextColor(0xFFFECACA);
                tvP.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvP.setPadding(dpToPx(activity, 4), dpToPx(activity, 2), 0, dpToPx(activity, 2));
                root.addView(tvP);
            }
        }

        // 4. Status Pengesanan VirusTotal
        LinearLayout vtCard = new LinearLayout(activity);
        vtCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable vtBg = new GradientDrawable();
        vtBg.setColor(0xFF0A0A0F);
        vtBg.setCornerRadius(dpToPx(activity, 10));
        vtBg.setStroke(dpToPx(activity, 1), 0xFF2A2A3A);
        vtCard.setBackground(vtBg);
        vtCard.setPadding(dpToPx(activity, 14), dpToPx(activity, 10), dpToPx(activity, 14), dpToPx(activity, 10));
        LinearLayout.LayoutParams vtLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vtLp.topMargin = dpToPx(activity, 12);
        vtCard.setLayoutParams(vtLp);

        TextView tvVtHead = new TextView(activity);
        tvVtHead.setText("🌐 STATUS ENJIN VIRUSTOTAL GLOBAL:");
        tvVtHead.setTextColor(0xFF38BDF8);
        tvVtHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVtHead.setTypeface(Typeface.DEFAULT_BOLD);
        vtCard.addView(tvVtHead);

        TextView tvVtStats = new TextView(activity);
        if (report.vtQueried) {
            if (report.vtFound) {
                if (report.vtMalicious > 0) {
                    tvVtStats.setText("🚨 Dikesan Positif Malware: " + report.vtMalicious + " enjin sekuriti dunia!");
                    tvVtStats.setTextColor(0xFFEF4444);
                } else {
                    tvVtStats.setText("✅ Bersih: 0 enjin mengesannya sebagai malware (Selamat).");
                    tvVtStats.setTextColor(0xFF34D399);
                }
            } else {
                tvVtStats.setText("ℹ️ Rekod Belum Wujud: Fail ini adalah binaan baharu dan belum pernah diimbas di VirusTotal.");
                tvVtStats.setTextColor(0xFFFBBF24);
            }
        } else {
            if (!hasApiKey(activity)) {
                tvVtStats.setText("ℹ️ Kunci API belum dimasukkan. Anda boleh menyemak terus laporan awam secara percuma melalui butang web di bawah.");
            } else {
                tvVtStats.setText("ℹ️ Sedang memproses semakan VirusTotal...");
            }
            tvVtStats.setTextColor(0xFF94A3B8);
        }
        tvVtStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvVtStats.setPadding(0, dpToPx(activity, 4), 0, 0);
        vtCard.addView(tvVtStats);

        root.addView(vtCard);

        // Butang Tindakan
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = dpToPx(activity, 14);
        btnRow.setLayoutParams(brLp);

        // Butang Buka VirusTotal Web (Percuma & Sentiasa Berfungsi)
        Button btnWeb = new Button(activity);
        btnWeb.setText("🌐 BUKA LAPORAN VIRUSTOTAL WEB");
        btnWeb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnWeb.setTextColor(Color.WHITE);
        btnWeb.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable webBg = new GradientDrawable();
        webBg.setColor(0xFF0284C7);
        webBg.setCornerRadius(dpToPx(activity, 8));
        btnWeb.setBackground(webBg);
        btnWeb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String vtUrl = "https://www.virustotal.com/gui/file/" + report.sha256;
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(vtUrl));
                    activity.startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(activity, "Gagal membuka pelayar: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnRow.addView(btnWeb);

        // Jika fail wujud di storan, sediakan butang PADAM SEGERA jika berbahaya
        if (report.file != null && report.file.exists()) {
            Button btnDelete = new Button(activity);
            btnDelete.setText("🗑️ PADAMKAN FAIL APK INI DARI STORAN");
            btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnDelete.setTextColor(Color.WHITE);
            btnDelete.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable delBg = new GradientDrawable();
            delBg.setColor(0xFFDC2626);
            delBg.setCornerRadius(dpToPx(activity, 8));
            btnDelete.setBackground(delBg);
            LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlLp.topMargin = dpToPx(activity, 8);
            btnDelete.setLayoutParams(dlLp);
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(activity)
                        .setTitle("🗑️ Sahkan Pemadaman Fail")
                        .setMessage("Adakah anda pasti mahu memadamkan fail [" + report.fileName + "] secara kekal?")
                        .setPositiveButton("YA, PADAMKAN", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    boolean del = report.file.delete();
                                    if (del) {
                                        Toast.makeText(activity, "✅ Fail APK berbahaya berjaya dipadamkan!", Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(activity, "Gagal memadam fail (Kebenaran storan diperlukan).", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Throwable t) {
                                    Toast.makeText(activity, "Ralat: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("BATAL", null)
                        .show();
                }
            });
            btnRow.addView(btnDelete);
        }

        root.addView(btnRow);

        sv.addView(root);
        b.setView(sv);

        final AlertDialog dialog = b.create();

        Button btnClose = new Button(activity);
        btnClose.setText("✕ TUTUP LAPORAN");
        btnClose.setTextColor(0xFF94A3B8);
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(0xFF141422);
        cBg.setCornerRadius(dpToPx(activity, 8));
        cBg.setStroke(dpToPx(activity, 1), 0xFF2A2A3A);
        btnClose.setBackground(cBg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 42));
        clp.topMargin = dpToPx(activity, 10);
        btnClose.setLayoutParams(clp);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        root.addView(btnClose);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.75f);
        }
        dialog.show();
    }

    public static void showUrlScanDialog(final Activity activity) {
        showUrlScanDialog(activity, "");
    }

    public static void showUrlScanDialog(final Activity activity, String initialUrl) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder b = new AlertDialog.Builder(activity);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(activity, 18), dpToPx(activity, 20), dpToPx(activity, 18), dpToPx(activity, 20));

        GradientDrawable lBg = new GradientDrawable();
        lBg.setColor(0xFF0A0A0F); // Void Black
        lBg.setCornerRadius(dpToPx(activity, 16));
        lBg.setStroke(dpToPx(activity, 2), 0xFF00D4FF);
        layout.setBackground(lBg);

        // Header
        TextView tvTitle = new TextView(activity);
        tvTitle.setText("🔗 PENGIMBAS PAUTAN VIRUSTOTAL");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        layout.addView(tvTitle);

        TextView tvHint = new TextView(activity);
        tvHint.setText("Tampal pautan mencurigakan (cth: daripada WhatsApp, SMS, atau FB) untuk analisis perisian hasad & pancingan data:");
        tvHint.setTextColor(0xFF94A3B8);
        tvHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvHint.setLineSpacing(dpToPx(activity, 2), 1.0f);
        tvHint.setPadding(0, dpToPx(activity, 6), 0, dpToPx(activity, 10));
        layout.addView(tvHint);

        final EditText etUrl = new EditText(activity);
        etUrl.setHint("https://contoh-laman-scam.xyz");
        etUrl.setTextColor(Color.WHITE);
        etUrl.setHintTextColor(0xFF64748B);
        etUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (initialUrl != null && !initialUrl.trim().isEmpty()) {
            etUrl.setText(initialUrl.trim());
        }
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF141422);
        etBg.setCornerRadius(dpToPx(activity, 8));
        etBg.setStroke(dpToPx(activity, 1), 0xFF2A2A3A);
        etUrl.setBackground(etBg);
        etUrl.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = dpToPx(activity, 4);
        etLp.bottomMargin = dpToPx(activity, 12);
        etUrl.setLayoutParams(etLp);
        layout.addView(etUrl);

        b.setView(layout);
        final AlertDialog dialog = b.create();

        // Submit Button
        Button btnScan = new Button(activity);
        btnScan.setText("🔍 IMBAS PAUTAN SEKARANG");
        btnScan.setTextColor(Color.BLACK);
        btnScan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnScan.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable scBg = new GradientDrawable();
        scBg.setColor(0xFF00D4FF);
        scBg.setCornerRadius(dpToPx(activity, 8));
        btnScan.setBackground(scBg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 44));
        btnScan.setLayoutParams(sLp);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String target = etUrl.getText().toString().trim();
                if (target.isEmpty()) {
                    Toast.makeText(activity, "Sila masukkan pautan URL terlebih dahulu.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                    Toast.makeText(activity, "Pautan mestilah bermula dengan http:// atau https://", Toast.LENGTH_SHORT).show();
                    return;
                }

                dialog.dismiss();
                Toast.makeText(activity, "Sedang menyemak reputasi pautan di VirusTotal...", Toast.LENGTH_SHORT).show();
                queryVirusTotalUrlAsync(activity, target, new VtUrlCallback() {
                    @Override
                    public void onResult(String url, boolean found, int mal, int susp, int harm, String err) {
                        if (activity.isFinishing()) return;

                        AlertDialog.Builder resB = new AlertDialog.Builder(activity);
                        LinearLayout resRoot = new LinearLayout(activity);
                        resRoot.setOrientation(LinearLayout.VERTICAL);
                        resRoot.setPadding(dpToPx(activity, 18), dpToPx(activity, 20), dpToPx(activity, 18), dpToPx(activity, 20));

                        GradientDrawable rBg = new GradientDrawable();
                        rBg.setColor(0xFF0A0A0F);
                        rBg.setCornerRadius(dpToPx(activity, 16));
                        rBg.setStroke(dpToPx(activity, 2), mal > 0 ? 0xFFFF3366 : 0xFF00FF88);
                        resRoot.setBackground(rBg);

                        TextView tvResTitle = new TextView(activity);
                        tvResTitle.setText("🌐 KEPUTUSAN SEMAKAN PAUTAN");
                        tvResTitle.setTextColor(Color.WHITE);
                        tvResTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                        tvResTitle.setTypeface(Typeface.DEFAULT_BOLD);
                        resRoot.addView(tvResTitle);

                        TextView tvUrlText = new TextView(activity);
                        tvUrlText.setText("Pautan: " + url);
                        tvUrlText.setTextColor(0xFF94A3B8);
                        tvUrlText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        tvUrlText.setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 8));
                        resRoot.addView(tvUrlText);

                        LinearLayout resCard = new LinearLayout(activity);
                        resCard.setOrientation(LinearLayout.VERTICAL);
                        resCard.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
                        GradientDrawable rcBg = new GradientDrawable();
                        rcBg.setColor(mal > 0 ? 0xFF450A0A : 0xFF141422);
                        rcBg.setCornerRadius(dpToPx(activity, 10));
                        rcBg.setStroke(dpToPx(activity, 1), mal > 0 ? 0xFFFF3366 : 0xFF00FF88);
                        resCard.setBackground(rcBg);

                        TextView tvVerdict = new TextView(activity);
                        if (err != null) {
                            tvVerdict.setText("Status: " + err + "\nAnda boleh membuat semakan manual percuma di portal VirusTotal Web.");
                            tvVerdict.setTextColor(0xFFCBD5E1);
                        } else if (found) {
                            if (mal > 0) {
                                tvVerdict.setText("🚨 AMARAN BAHAYA: Pautan ini disahkan MALICIOUS/PHISHING oleh " + mal + " enjin keselamatan!\n\nJANGAN BUKA PAUTAN INI.");
                                tvVerdict.setTextColor(0xFFFECDD3);
                            } else {
                                tvVerdict.setText("✅ BERSIH: Tiada enjin keselamatan yang mengesan pautan ini sebagai berbahaya (0 Malicious).");
                                tvVerdict.setTextColor(0xFF00FF88);
                            }
                        } else {
                            tvVerdict.setText("ℹ️ Pautan ini belum mempunyai rekod sedia ada di pangkalan data VirusTotal.");
                            tvVerdict.setTextColor(0xFFCBD5E1);
                        }
                        tvVerdict.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        tvVerdict.setLineSpacing(dpToPx(activity, 2), 1.0f);
                        resCard.addView(tvVerdict);
                        resRoot.addView(resCard);

                        resB.setView(resRoot);
                        final AlertDialog resDialog = resB.create();

                        Button btnCloseRes = new Button(activity);
                        btnCloseRes.setText("✕ TUTUP");
                        btnCloseRes.setTextColor(0xFF94A3B8);
                        btnCloseRes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        btnCloseRes.setTypeface(Typeface.DEFAULT_BOLD);
                        GradientDrawable crBg = new GradientDrawable();
                        crBg.setColor(0xFF141422);
                        crBg.setCornerRadius(dpToPx(activity, 8));
                        crBg.setStroke(dpToPx(activity, 1), 0xFF2A2A3A);
                        btnCloseRes.setBackground(crBg);
                        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 42));
                        crLp.topMargin = dpToPx(activity, 10);
                        btnCloseRes.setLayoutParams(crLp);
                        btnCloseRes.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                resDialog.dismiss();
                            }
                        });
                        resRoot.addView(btnCloseRes);

                        if (resDialog.getWindow() != null) {
                            resDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                            resDialog.getWindow().setDimAmount(0.75f);
                        }
                        resDialog.show();
                    }
                });
            }
        });
        layout.addView(btnScan);

        // Cancel Button
        Button btnCancel = new Button(activity);
        btnCancel.setText("✕ BATAL");
        btnCancel.setTextColor(0xFF94A3B8);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnCancel.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(0xFF141422);
        cBg.setCornerRadius(dpToPx(activity, 8));
        cBg.setStroke(dpToPx(activity, 1), 0xFF2A2A3A);
        btnCancel.setBackground(cBg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 42));
        cLp.topMargin = dpToPx(activity, 8);
        btnCancel.setLayoutParams(cLp);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        layout.addView(btnCancel);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.75f);
        }
        dialog.show();
    }

    public static void showApiKeySettingsDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle("🔑 TETAPAN KUNCI API VIRUSTOTAL");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(activity, 16);
        layout.setPadding(pad, pad, pad, pad);

        final boolean hasCustom = hasCustomApiKey(activity);
        TextView tvInfo = new TextView(activity);
        if (hasCustom) {
            tvInfo.setText("✅ Menggunakan Kunci API Peribadi Anda.\n\nKosongkan kotak di bawah dan tekan 'SIMPAN' jika anda ingin kembali menggunakan Kunci Komuniti Bawaan KANTOI.");
        } else {
            tvInfo.setText("✅ Kunci Komuniti KANTOI (Obfuscated) AKTIF secara automatik untuk imbasan pantas.\n\nAnda juga boleh memasukkan Kunci API peribadi sendiri di bawah untuk mendapatkan kuota 500 semakan/hari tersendiri:");
        }
        tvInfo.setTextColor(0xFF94A3B8);
        tvInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        layout.addView(tvInfo);

        final EditText etKey = new EditText(activity);
        etKey.setHint("Tampal 64-aksara Kunci API Peribadi di sini");
        etKey.setTextColor(Color.WHITE);
        etKey.setHintTextColor(0xFF64748B);
        etKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        etKey.setText(getCustomApiKey(activity));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(activity, 10);
        etKey.setLayoutParams(lp);
        layout.addView(etKey);

        Button btnGet = new Button(activity);
        btnGet.setText("🌐 DAFTAR PERCUMA DI VIRUSTOTAL.COM");
        btnGet.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnGet.setTextColor(Color.WHITE);
        btnGet.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable gBg = new GradientDrawable();
        gBg.setColor(0xFF0284C7);
        gBg.setCornerRadius(dpToPx(activity, 6));
        btnGet.setBackground(gBg);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = dpToPx(activity, 10);
        btnGet.setLayoutParams(gLp);
        btnGet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/join-us"));
                    activity.startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(activity, "Ralat: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        layout.addView(btnGet);

        b.setView(layout);

        b.setPositiveButton("SIMPAN KUNCI", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                String key = etKey.getText().toString().trim();
                setApiKey(activity, key);
                if (key.isEmpty()) {
                    Toast.makeText(activity, "Kunci peribadi dikosongkan. Kembali menggunakan Kunci Komuniti Bawaan KANTOI.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, "✅ Kunci API peribadi VirusTotal berjaya disimpan!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        b.setNegativeButton("BATAL", null);
        b.show();
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
