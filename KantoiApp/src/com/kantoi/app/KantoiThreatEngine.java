package com.kantoi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;
import android.widget.EditText;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import android.provider.ContactsContract;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

public class KantoiThreatEngine {
    private static final String TAG = "KantoiThreatEngine";
    private static final String DB_NAME = "kantoi_threats.db";
    private static final int CURRENT_DB_VERSION = 9; // Version 9: 3,729 Threats (+67 Kes Pelaburan Palsu & Akaun Keldai Telegram)
    private static final int SCAMMER_CALL_NOTIFICATION_ID = 999111;
    private static final String SCAMMER_CALL_CHANNEL_ID = "scammer_incoming_call_channel";
    private static final String SCAMMER_SMS_CHANNEL_ID = "kantoi_sms_alerts";
    private static final int SCAMMER_SMS_NOTIFICATION_ID = 9002;
    private static final String OTA_SYNC_CHANNEL_ID = "kantoi_ota_sync_channel";
    private static final int OTA_SYNC_NOTIFICATION_ID = 9010;
    private static final String PREF_OTA_NAME = "kantoi_ota_prefs";
    private static final String PREF_KEY_LAST_SYNC = "last_ota_sync_time";
    private static final long MIN_AUTO_SYNC_INTERVAL_MS = 2 * 60 * 60 * 1000L; // 2 jam minimum antara semakan automatik

    private static volatile KantoiThreatEngine sInstance;
    private static volatile ScammerThreat sLastThreatOnScreen = null;
    private final Set<String> mNativeContactsWhitelist = Collections.synchronizedSet(new HashSet<String>());
    private final Map<String, String> mContactNameCache = new ConcurrentHashMap<String, String>();
    private final Set<String> mCheckedNonContacts = Collections.synchronizedSet(new HashSet<String>());
    private volatile boolean mContactsScanned = false;
    private static volatile WeakReference<Activity> sLastForegroundActivity = null;

    // Incoming Call Floating Alert Banner state
    private static volatile View sFloatingAlertView = null;
    private static volatile WindowManager sWindowManager = null;
    private static volatile boolean sCallReceiverRegistered = false;
    private static volatile String sCurrentRingingNumber = null;
    private static volatile String sCurrentViewingPhone = null;

    public static class SemakMuleData {
        public int searchCount = -1;
        public int reportCount = -1;
        public boolean loading = false;
    }

    private static final Map<String, SemakMuleData> sSemakMuleMap = new ConcurrentHashMap<String, SemakMuleData>();

    private final Context mContext;
    private SQLiteDatabase mDb;
    private final Map<String, ScammerThreat> mThreatMap = new HashMap<String, ScammerThreat>();
    private final List<ScammerThreat> mAllThreats = new ArrayList<ScammerThreat>();
    private final Map<String, SafeContactItem> mCustomSafeContacts = new ConcurrentHashMap<String, SafeContactItem>();
    private boolean mInitialized = false;

    public static class SafeContactItem {
        public String phoneNorm;
        public String contactName;
        public String category;
        public String notes;
        public long createdAt;
    }

    public static class ScammerThreat {
        public String phoneNorm;
        public int profileId;
        public String suspectName;
        public String allPhones;
        public String bankAccounts;
        public String icNumber;
        public String address;
        public String evidenceLinks;
        public String riskTag;
        public String source;
    }

    public static synchronized KantoiThreatEngine getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KantoiThreatEngine(context.getApplicationContext());
        }
        return sInstance;
    }

    private KantoiThreatEngine(Context context) {
        mContext = context;
        initDatabase();
    }

    public static void init(final Context context) {
        if (context == null) return;
        try {
            Log.i(TAG, "=== Initializing KantoiThreatEngine (v7.0 Strict Matching & Isolated Threat Engine) ===");
            final KantoiThreatEngine engine = getInstance(context);

            // Register real-time incoming call interceptor & floating alert banner
            registerCallStateListener(context);

            if (context instanceof Application) {
                Application app = (Application) context;
                app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        handleActivityLifecycle(activity, engine);
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        handleActivityLifecycle(activity, engine);
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (!isPaywallOrPremiumActivity(activity.getClass().getName())) {
                            sLastForegroundActivity = new WeakReference<Activity>(activity);
                        }
                        handleActivityLifecycle(activity, engine);
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {}

                    @Override
                    public void onActivityStopped(Activity activity) {}

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        if (sLastForegroundActivity != null && sLastForegroundActivity.get() == activity) {
                            sLastForegroundActivity = null;
                            sLastThreatOnScreen = null;
                        }
                    }
                });
                Log.i(TAG, "ActivityLifecycleCallbacks registered successfully.");
            }
        } catch (Throwable t) {
            Log.e(TAG, "init failed: " + t.getMessage(), t);
        }
    }

    private static boolean isPaywallOrPremiumActivity(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("paywall") ||
               lower.contains("premium") ||
               lower.contains("chooseplan") ||
               lower.contains("choiceactivity") ||
               lower.contains("whoviewed") ||
               lower.contains("whosearched") ||
               lower.contains("promocode") ||
               lower.contains("giveaway") ||
               lower.contains("featureinnerscreen") ||
               lower.contains("cataloginnerscreen") ||
               lower.contains("testinterstitial");
    }

    private static void handleActivityLifecycle(final Activity activity, final KantoiThreatEngine engine) {
        if (activity == null || activity.isFinishing()) return;
        try {
            String name = activity.getClass().getName();
            if (isPaywallOrPremiumActivity(name)) {
                // Instantly dismiss Truecaller's paywall activity to eliminate ads
                Log.i(TAG, "Paywall Activity (" + name + ") detected! Finishing paywall immediately.");
                activity.finish();
                activity.overridePendingTransition(0, 0);

                // Show Threat Intelligence Dossier on the underlying parent activity without bouncing back
                final Activity parent = (sLastForegroundActivity != null) ? sLastForegroundActivity.get() : null;
                if (parent != null && !parent.isFinishing()) {
                    if (sLastThreatOnScreen != null) {
                        final ScammerThreat activeThreat = sLastThreatOnScreen;
                        parent.getWindow().getDecorView().post(new Runnable() {
                            @Override
                            public void run() {
                                if (!parent.isFinishing()) {
                                    engine.showThreatDialog(parent, activeThreat);
                                }
                            }
                        });
                    } else {
                        final String phone = (sCurrentViewingPhone != null) ? sCurrentViewingPhone : "";
                        parent.getWindow().getDecorView().post(new Runnable() {
                            @Override
                            public void run() {
                                if (!parent.isFinishing()) {
                                    engine.showRegisterScammerDialog(parent, phone);
                                }
                            }
                        });
                    }
                }
            } else {
                attachDecorator(activity, engine);
                try {
                    final View decor = activity.getWindow().getDecorView();
                    if (decor != null) {
                        decor.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!activity.isFinishing()) {
                                    ensureFloatingCheckerPill(activity, engine, null);
                                }
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.e(TAG, "handleActivityLifecycle error: " + t.getMessage(), t);
        }
    }

    private static void attachDecorator(final Activity activity, final KantoiThreatEngine engine) {
        if (activity == null || activity.isFinishing()) return;
        String name = activity.getClass().getName();
        if (name.startsWith("com.kantoi.app.")) {
            // Native Kantoi activities manage their own custom cards, tags, and theme.
            // Skipping external decorator entirely eliminates OnPreDraw listener churn and guarantees 60/120fps UI.
            return;
        }
        try {
            final View decorView = activity.getWindow().getDecorView();
            if (decorView.getTag(0x7F0A0999) != null) {
                return; // Already attached to this decorView! Avoid duplicate listeners
            }
            decorView.setTag(0x7F0A0999, Boolean.TRUE);

            final long[] lastDecorTime = new long[]{0};
            decorView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    try {
                        long now = System.currentTimeMillis();
                        if (now - lastDecorTime[0] > 600) { // Throttle: at most once every 600ms
                            lastDecorTime[0] = now;
                            decorateActivity(activity, engine);
                        }
                    } catch (Throwable ignored) {}
                    return true;
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "attachDecorator failed: " + t.getMessage());
        }
    }

    private static boolean isPremiumRequiredTrigger(String text) {
        if (text == null) return false;
        String lower = text.trim().toLowerCase();
        return lower.contains("upgrade to premium") ||
               lower.contains("upgrade to view") ||
               lower.contains("premium to view") ||
               lower.contains("naik taraf") ||
               lower.contains("premium required") ||
               lower.contains("unlock");
    }

    private static boolean isMoreInfoTrigger(String text) {
        if (text == null) return false;
        String lower = text.trim().toLowerCase();
        return lower.contains("more info") ||
               lower.contains("maklumat lanjut") ||
               lower.contains("info lanjut") ||
               lower.contains("maklumat tambahan") ||
               lower.contains("who viewed") ||
               lower.contains("siapa lihat") ||
               lower.contains("who searched") ||
               lower.contains("siapa cari");
    }

    public static void onDetailsViewExtras(final Activity activity, final Object extras) {
        if (activity == null) return;
        sLastForegroundActivity = new WeakReference<Activity>(activity);
        
        // CRITICAL FIX: Reset threat state immediately so previous contacts NEVER leak!
        sLastThreatOnScreen = null;

        try {
            String norm = null;
            String raw = null;
            if (extras != null) {
                try {
                    java.lang.reflect.Method getCd = extras.getClass().getMethod("getContactData");
                    Object cd = getCd.invoke(extras);
                    if (cd != null) {
                        try {
                            java.lang.reflect.Method getNorm = cd.getClass().getMethod("getNormalizedNumber");
                            norm = (String) getNorm.invoke(cd);
                        } catch (Throwable ignored) {}
                        try {
                            java.lang.reflect.Method getRaw = cd.getClass().getMethod("getRawNumber");
                            raw = (String) getRaw.invoke(cd);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            if (norm == null && activity.getIntent() != null) {
                Intent it = activity.getIntent();
                if (it.getData() != null) {
                    norm = it.getData().getSchemeSpecificPart();
                }
            }

            sCurrentViewingPhone = (norm != null ? norm : raw);
            final KantoiThreatEngine engine = getInstance(activity);
            ScammerThreat threat = null;
            if (norm != null) threat = engine.checkThreat(norm);
            if (threat == null && raw != null) threat = engine.checkThreat(raw);

            // ONLY activate scammer threat state if this specific contact's phone is confirmed in DB
            if (threat != null) {
                sLastThreatOnScreen = threat;
                Log.w(TAG, "onDetailsViewExtras: CONFIRMED SCAMMER THREAT DETECTED! phone=" + threat.phoneNorm + ", suspect=" + threat.suspectName);
                String targetPhone = normalizeToLocal(threat.phoneNorm);
                if (targetPhone != null && targetPhone.length() >= 8) {
                    querySemakMuleAsync(activity, targetPhone);
                }
            } else {
                sLastThreatOnScreen = null;
                Log.i(TAG, "onDetailsViewExtras: Innocent number (" + (norm != null ? norm : raw) + "). Maintaining standard UI.");
            }
        } catch (Throwable t) {
            Log.e(TAG, "onDetailsViewExtras error: " + t.getMessage(), t);
        }
    }

    public static String interceptContactName(String originalName, String normalizedNumber) {
        try {
            KantoiThreatEngine engine = sInstance;
            if (engine != null && normalizedNumber != null) {
                ScammerThreat threat = engine.checkThreat(normalizedNumber);
                if (threat != null) {
                    sLastThreatOnScreen = threat;
                    String base = (originalName != null && !originalName.trim().isEmpty()) ? originalName : (threat.suspectName != null ? threat.suspectName : "SCAMMER");
                    if (!base.contains("\u2620\uFE0F")) {
                        return "\u2620\uFE0F [SCAMMER] " + base;
                    }
                    return base;
                }
            }
        } catch (Throwable ignored) {}
        return originalName;
    }

    public static String interceptComposeString(int resId, String original) {
        try {
            if (resId == 0x7f140d24 || resId == 0x7f140c9f || (original != null && isMoreInfoTrigger(original))) {
                if (sLastThreatOnScreen != null) {
                    return "\u2620\uFE0F AWAS! SCAMMER DISAHKAN!";
                } else {
                    return "\uD83D\uDEE1\uFE0F SEMAK / DAFTAR SCAMMER PDRM";
                }
            }
            if (resId == 0x7f140d77 || resId == 0x7f140cf8 || (original != null && isPremiumRequiredTrigger(original))) {
                if (sLastThreatOnScreen != null) {
                    String local = normalizeToLocal(sLastThreatOnScreen.phoneNorm);
                    SemakMuleData sm = (local != null) ? sSemakMuleMap.get(local) : null;
                    if (sm != null && sm.searchCount > 0) {
                        return "\uD83D\uDEA8 SemakMule: " + sm.searchCount + " Carian CCID \u2022 Buka Info";
                    }
                    return "\uD83D\uDEA8 Rekod SemakMule CCID PDRM \u2022 Buka Info";
                } else {
                    int cnt = (sInstance != null) ? sInstance.getThreatCount() : 3729;
                    return "\uD83D\uDEE1\uFE0F " + String.format("%,d", cnt) + " Rekod Scammer • Semak & Lapor";
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "interceptComposeString error: " + t.getMessage());
        }
        return original;
    }

    public static Object interceptStringProvider(Object original) {
        try {
            Class<?> clazz = Class.forName("o450");
            java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(String.class);
            if (sLastThreatOnScreen != null) {
                return ctor.newInstance("\u2620\uFE0F AWAS! SCAMMER DISAHKAN!");
            } else {
                return ctor.newInstance("\uD83D\uDEE1\uFE0F SEMAK / DAFTAR SCAMMER PDRM");
            }
        } catch (Throwable t) {
            Log.e(TAG, "interceptStringProvider error: " + t.getMessage());
        }
        return original;
    }

    public static int interceptCrownIcon(int defaultIcon) {
        if (sLastThreatOnScreen != null) {
            return 0x7f08057d;
        }
        return defaultIcon;
    }

    public static boolean handleThreatItemClick(Object item) {
        try {
            final Activity act = (sLastForegroundActivity != null) ? sLastForegroundActivity.get() : null;
            if (act != null && !act.isFinishing()) {
                if (sLastThreatOnScreen != null) {
                    final ScammerThreat threat = sLastThreatOnScreen;
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getInstance(act).showThreatDialog(act, threat);
                        }
                    });
                    return true;
                } else {
                    final String phone = (sCurrentViewingPhone != null) ? sCurrentViewingPhone : "";
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getInstance(act).showRegisterScammerDialog(act, phone);
                        }
                    });
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "handleThreatItemClick error: " + t.getMessage(), t);
        }
        return false;
    }

    private static boolean isViewAllTrigger(String text) {
        if (text == null) return false;
        String lower = text.trim().toLowerCase();
        return lower.equals("view all") ||
               lower.equals("lihat semua") ||
               lower.startsWith("view all") ||
               lower.startsWith("lihat semua");
    }

    private static void collectTextViews(View root, List<TextView> out) {
        if (root == null || out == null) return;
        if (root instanceof TextView) {
            out.add((TextView) root);
        } else if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                collectTextViews(vg.getChildAt(i), out);
            }
        }
    }

    private static void trustAllHosts(HttpsURLConnection conn) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
            conn.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "trustAllHosts error: " + t.getMessage());
        }
    }

    public static SemakMuleData querySemakMuleNetworkDirect(String category, String queryTarget) {
        if (queryTarget == null || queryTarget.length() < 8) return null;
        String cleanTarget = queryTarget.replaceAll("[^0-9]", "");
        if (cleanTarget.length() < 8) return null;
        String cat = ("bank".equalsIgnoreCase(category)) ? "bank" : "telefon";
        try {
            URL url = new URL("https://semakmule.rmp.gov.my/api/mule/get_search_data.php");
            java.net.URLConnection rawConn = url.openConnection();
            if (rawConn instanceof HttpsURLConnection) {
                trustAllHosts((HttpsURLConnection) rawConn);
            }
            HttpURLConnection conn = (HttpURLConnection) rawConn;
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", "j3j389#nklala2");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Origin", "https://semakmule.rmp.gov.my");
            conn.setRequestProperty("Referer", "https://semakmule.rmp.gov.my/");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);

            String jsonPayload;
            if ("bank".equalsIgnoreCase(cat)) {
                jsonPayload = "{\"data\":{\"category\":\"bank\",\"bankAccount\":\"" + cleanTarget + "\",\"telNo\":\"\",\"companyName\":\"\",\"captcha\":\"\"}}";
            } else {
                jsonPayload = "{\"data\":{\"category\":\"telefon\",\"telNo\":\"" + cleanTarget + "\",\"bankAccount\":\"\",\"companyName\":\"\",\"captcha\":\"\"}}";
            }
            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            int respCode = conn.getResponseCode();
            if (respCode >= 200 && respCode < 400) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                int status = json.optInt("status", 0);
                if (status == 1) {
                    JSONArray tableData = json.optJSONArray("table_data");
                    int reports = 0;
                    if (tableData != null && tableData.length() > 0) {
                        try {
                            JSONArray firstRow = tableData.optJSONArray(0);
                            if (firstRow != null && firstRow.length() > 1) {
                                reports = firstRow.optInt(1, 0);
                            } else {
                                Object first = tableData.get(0);
                                if (first instanceof JSONArray) {
                                    reports = ((JSONArray) first).optInt(1, 0);
                                }
                            }
                        } catch (Throwable tRow) {
                            Log.e(TAG, "Gagal memproses baris table_data: " + tRow.getMessage());
                        }
                    }
                    int searchCount = json.optInt("count", 0);

                    SemakMuleData data = new SemakMuleData();
                    data.searchCount = searchCount;
                    data.reportCount = reports;
                    data.loading = false;
                    Log.i(TAG, "SemakMule direct response for [" + cleanTarget + "] (" + cat + "): count=" + searchCount + ", reports=" + reports);
                    return data;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "querySemakMuleNetworkDirect error for " + queryTarget + ": " + t.getMessage());
        }
        return null;
    }

    private static SemakMuleData querySemakMuleNetwork(String localPhone) {
        return querySemakMuleNetworkDirect("telefon", localPhone);
    }

    public static void querySemakMuleAsync(final Activity activity, final String localPhone) {
        if (localPhone == null || localPhone.length() < 8) return;
        synchronized (sSemakMuleMap) {
            SemakMuleData existing = sSemakMuleMap.get(localPhone);
            if (existing != null && existing.searchCount > 0) {
                return;
            }
            SemakMuleData initial = new SemakMuleData();
            initial.loading = true;
            sSemakMuleMap.put(localPhone, initial);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                SemakMuleData result = querySemakMuleNetwork(localPhone);
                if (result != null) {
                    sSemakMuleMap.put(localPhone, result);
                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    decorateActivity(activity, getInstance(activity));
                                } catch (Throwable ignored) {}
                            }
                        });
                    }
                } else {
                    // Do NOT mark as 0 count on error/timeout! Mark as -1 so UI falls back gracefully
                    SemakMuleData fallback = new SemakMuleData();
                    fallback.searchCount = -1;
                    fallback.reportCount = -1;
                    fallback.loading = false;
                    sSemakMuleMap.put(localPhone, fallback);
                }
            }
        }).start();
    }

    private static ScammerThreat createSyntheticThreat(String localPhone) {
        ScammerThreat t = new ScammerThreat();
        t.phoneNorm = (localPhone != null) ? localPhone : "";
        t.allPhones = t.phoneNorm;
        t.suspectName = "Nombor Pemanggil";
        t.riskTag = "SEMAKMULE_VERIFICATION";
        t.source = "SemakMule CCID PDRM";
        t.bankAccounts = "Sila semak di portal SemakMule";
        return t;
    }

    private static void transformMoreInfoCard(final TextView tv, final Activity activity, final KantoiThreatEngine engine, final ScammerThreat threat, final String localPhone) {
        if (tv == null || activity == null || activity.isFinishing()) return;

        try {
            ViewParent p1 = tv.getParent();
            if (!(p1 instanceof ViewGroup)) return;
            ViewGroup vg1 = (ViewGroup) p1;
            ViewGroup cardContainer = vg1;

            if (vg1.getParent() instanceof ViewGroup) {
                ViewGroup vg2 = (ViewGroup) vg1.getParent();
                String c2Name = vg2.getClass().getName().toLowerCase();
                if (c2Name.contains("card") || vg2.getChildCount() <= 3) {
                    cardContainer = vg2;
                }
            }

            List<TextView> cardTextViews = new ArrayList<TextView>();
            collectTextViews(cardContainer, cardTextViews);

            SemakMuleData sm = (localPhone != null) ? sSemakMuleMap.get(localPhone) : null;
            String smCountStr;
            if (sm != null && sm.searchCount > 0) {
                smCountStr = sm.searchCount + " Carian SemakMule";
            } else {
                smCountStr = "Semakan SemakMule PDRM";
            }

            int cardBgColor = 0xFFB71C1C; // Deep Crimson Red
            int cardStrokeColor = 0xFFFF5252; // Red stroke
            String titleText = "\u2620\uFE0F ANCAMAN SCAMMER DISAHKAN";
            String subText;

            if (threat != null) {
                subText = "Suspek: " + threat.suspectName + " \u2022 \uD83C\uDFDB\uFE0F " + smCountStr + " \u2022 Buka Info";
            } else if (sm != null && sm.reportCount > 0) {
                titleText = "\u2620\uFE0F ANCAMAN SCAMMER DISAHKAN (PDRM)";
                subText = "🚨 " + sm.reportCount + " Laporan Polis \u2022 " + sm.searchCount + " Carian \u2022 Buka Info";
            } else if (sm != null && sm.searchCount > 0) {
                cardBgColor = 0xFFD97706; // Amber
                cardStrokeColor = 0xFFFBBF24;
                titleText = "\u26A0\uFE0F REKOD SEMAKMULE DIJUMPAI";
                subText = "🔍 Telah dicari " + sm.searchCount + " kali di SemakMule \u2022 Buka Info";
            } else {
                titleText = "\uD83D\uDEE1\uFE0F SEMAK STATUS KESELAMATAN";
                subText = "🏛️ Semak akaun bank & rekod SemakMule CCID PDRM";
            }

            if (cardTextViews.size() >= 2) {
                TextView titleTv = null;
                TextView subTv = null;
                for (TextView cTv : cardTextViews) {
                    String s = (cTv.getText() != null) ? cTv.getText().toString().toLowerCase() : "";
                    if (s.contains("more info") || s.contains("maklumat lanjut") || s.contains("scammer") || s.contains("ancaman") || s.contains("keselamatan")) {
                        titleTv = cTv;
                    } else if (s.contains("premium") || s.contains("upgrade") || s.contains("view") || s.contains("semakmule") || s.contains("suspek")) {
                        subTv = cTv;
                    }
                }
                if (titleTv == null) titleTv = cardTextViews.get(0);
                if (subTv == null) subTv = cardTextViews.get(cardTextViews.size() - 1);
                if (titleTv == subTv && cardTextViews.size() > 1) subTv = cardTextViews.get(1);

                titleTv.setText(titleText);
                titleTv.setTextColor(Color.WHITE);
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                titleTv.setTag("MORE_INFO_TRANSFORMED");

                if (subTv != titleTv) {
                    subTv.setText(subText);
                    subTv.setTextColor(0xFFFFE082); // Warm Gold
                    subTv.setTypeface(Typeface.DEFAULT_BOLD);
                    subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    subTv.setTag("MORE_INFO_TRANSFORMED");
                }
            } else {
                tv.setText(titleText + "\n" + subText);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setTag("MORE_INFO_TRANSFORMED");
            }

            GradientDrawable redBg = new GradientDrawable();
            redBg.setColor(cardBgColor);
            redBg.setCornerRadius(dpToPx(activity, 12));
            redBg.setStroke(dpToPx(activity, 2), cardStrokeColor);
            cardContainer.setBackground(redBg);
            cardContainer.setPadding(dpToPx(activity, 14), dpToPx(activity, 12), dpToPx(activity, 14), dpToPx(activity, 12));

            final ScammerThreat finalThreat = (threat != null) ? threat : createSyntheticThreat(localPhone);
            View.OnClickListener openThreat = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    engine.showThreatDialog(activity, finalThreat);
                }
            };

            cardContainer.setClickable(true);
            cardContainer.setOnClickListener(openThreat);
            tv.setClickable(true);
            tv.setOnClickListener(openThreat);
            for (TextView cTv : cardTextViews) {
                cTv.setClickable(true);
                cTv.setOnClickListener(openThreat);
            }
        } catch (Throwable t) {
            Log.e(TAG, "transformMoreInfoCard error: " + t.getMessage(), t);
        }
    }

    private static void transformMoreInfoCardToChecker(final TextView tv, final Activity activity, final KantoiThreatEngine engine, final String localPhone) {
        if (tv == null || activity == null || activity.isFinishing()) return;

        try {
            ViewParent p1 = tv.getParent();
            if (!(p1 instanceof ViewGroup)) return;
            ViewGroup vg1 = (ViewGroup) p1;
            ViewGroup cardContainer = vg1;

            if (vg1.getParent() instanceof ViewGroup) {
                ViewGroup vg2 = (ViewGroup) vg1.getParent();
                String c2Name = vg2.getClass().getName().toLowerCase();
                if (c2Name.contains("card") || vg2.getChildCount() <= 3) {
                    cardContainer = vg2;
                }
            }

            List<TextView> cardTextViews = new ArrayList<TextView>();
            collectTextViews(cardContainer, cardTextViews);

            int cardBgColor = 0xFF0F172A; // Deep Slate 900
            int cardStrokeColor = 0xFF0284C7; // Sky Blue border
            String titleText = "\uD83D\uDEE1\uFE0F SEMAK / DAFTAR SCAMMER PDRM";
            String subText = "🛡️ " + String.format("%,d", engine.getThreatCount()) + " Rekod Scammer • Semak & Lapor";

            if (cardTextViews.size() >= 2) {
                TextView titleTv = null;
                TextView subTv = null;
                for (TextView cTv : cardTextViews) {
                    String s = (cTv.getText() != null) ? cTv.getText().toString().toLowerCase() : "";
                    if (s.contains("more info") || s.contains("maklumat lanjut") || s.contains("semak") || s.contains("daftar")) {
                        titleTv = cTv;
                    } else if (s.contains("premium") || s.contains("upgrade") || s.contains("view") || s.contains("semakmule")) {
                        subTv = cTv;
                    }
                }
                if (titleTv == null) titleTv = cardTextViews.get(0);
                if (subTv == null) subTv = cardTextViews.get(cardTextViews.size() - 1);
                if (titleTv == subTv && cardTextViews.size() > 1) subTv = cardTextViews.get(1);

                titleTv.setText(titleText);
                titleTv.setTextColor(Color.WHITE);
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                titleTv.setTag("MORE_INFO_CHECKER");

                if (subTv != titleTv) {
                    subTv.setText(subText);
                    subTv.setTextColor(0xFF93C5FD);
                    subTv.setTypeface(Typeface.DEFAULT_BOLD);
                    subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    subTv.setTag("MORE_INFO_CHECKER");
                }
            } else {
                tv.setText(titleText + "\n" + subText);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setTag("MORE_INFO_CHECKER");
            }

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBgColor);
            bg.setCornerRadius(dpToPx(activity, 12));
            bg.setStroke(dpToPx(activity, 2), cardStrokeColor);
            cardContainer.setBackground(bg);
            cardContainer.setPadding(dpToPx(activity, 14), dpToPx(activity, 12), dpToPx(activity, 14), dpToPx(activity, 12));

            View.OnClickListener openChecker = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    engine.showRegisterScammerDialog(activity, localPhone);
                }
            };

            cardContainer.setClickable(true);
            cardContainer.setOnClickListener(openChecker);
            tv.setClickable(true);
            tv.setOnClickListener(openChecker);
            for (TextView cTv : cardTextViews) {
                cTv.setClickable(true);
                cTv.setOnClickListener(openChecker);
            }
        } catch (Throwable t) {
            Log.e(TAG, "transformMoreInfoCardToChecker error: " + t.getMessage(), t);
        }
    }

    private static void decorateActivity(final Activity activity, final KantoiThreatEngine engine) {
        if (activity == null || activity.isFinishing()) return;
        String actName = activity.getClass().getName();
        if (actName.contains("IncomingCallActivity") || actName.contains("ActiveCallActivity")) {
            return;
        }
        View decorView = activity.getWindow().getDecorView();
        if (decorView == null) return;

        List<TextView> allTextViews = new ArrayList<TextView>();
        collectTextViews(decorView, allTextViews);

        // Pass 1: Extract on-screen phone number & identify if there is a threat strictly by phone
        String onScreenPhone = null;
        ScammerThreat detectedThreat = null;

        for (TextView tv : allTextViews) {
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0) {
                String str = cs.toString().trim();
                String d = str.replaceAll("[^0-9]", "");
                if (d.length() >= 8 && d.length() <= 13) {
                    if (onScreenPhone == null) {
                        onScreenPhone = normalizeToLocal(d);
                    }
                    ScammerThreat found = engine.checkThreat(d);
                    if (found != null) {
                        detectedThreat = found;
                    }
                }
            }
        }

        // Keep sLastThreatOnScreen in sync with the current active screen
        if (detectedThreat != null) {
            sLastThreatOnScreen = detectedThreat;
        } else if (onScreenPhone != null && sLastThreatOnScreen != null) {
            String lastPhone = normalizeToLocal(sLastThreatOnScreen.phoneNorm);
            if (lastPhone == null || !lastPhone.equals(onScreenPhone)) {
                sLastThreatOnScreen = null;
            }
        }

        final ScammerThreat activeThreat = sLastThreatOnScreen;
        final String targetPhone = (activeThreat != null) ? normalizeToLocal(activeThreat.phoneNorm) : (onScreenPhone != null ? onScreenPhone : normalizeToLocal(sCurrentViewingPhone));
        final SemakMuleData sm = (targetPhone != null) ? sSemakMuleMap.get(targetPhone) : null;

        // Pass 2: Decorate TextViews
        for (final TextView tv : allTextViews) {
            CharSequence cs = tv.getText();
            String text = (cs != null) ? cs.toString().trim() : "";
            CharSequence cd = tv.getContentDescription();
            String desc = (cd != null) ? cd.toString().trim() : "";

            if (activeThreat != null) {
                // 1. Phone number decoration & SemakMule search count badge
                String digits = text.replaceAll("[^0-9]", "");
                if (digits.length() >= 8 && digits.length() <= 13 && !text.contains("[SCAMMER]")) {
                    String localD = normalizeToLocal(digits);
                    if (localD.equals(targetPhone)) {
                        String smBadge = "";
                        if (sm != null && sm.searchCount > 0) {
                            if (sm.reportCount > 0) {
                                smBadge = " \uD83D\uDEA8 [" + sm.reportCount + " Laporan \u2022 " + sm.searchCount + " Carian]";
                            } else {
                                smBadge = " \uD83D\uDD0D [" + sm.searchCount + " Carian SemakMule]";
                            }
                        }

                        // Buangkan teks [SCAMMER] sebelah nombor telefon supaya tiada lewah/pertindihan
                        tv.setText(text + smBadge);
                        tv.setTextColor(0xFFE53935);
                        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);

                        tv.setClickable(true);
                        tv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                engine.showThreatDialog(activity, activeThreat);
                            }
                        });
                    }
                }

                // 1b. Suspect Name / Caller Name decoration
                if (activeThreat.suspectName != null && !text.isEmpty() && !text.contains("\u2620\uFE0F")) {
                    if (text.equalsIgnoreCase(activeThreat.suspectName)) {
                        tv.setText("\u2620\uFE0F [SCAMMER] " + text);
                        tv.setTextColor(0xFFFF1744); // Bright Neon Crimson
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                }

                // 2. Subtitle under phone number (e.g. "Malaysia", "Incoming call", "Panggilan Masuk")
                String lower = text.toLowerCase();
                if (lower.equals("malaysia") || lower.startsWith("malaysia") || lower.contains("incoming call") || lower.contains("panggilan masuk")) {
                    String smBadge = (sm != null && sm.searchCount > 0) ? (" \u2022 \uD83D\uDD0D " + sm.searchCount + " Carian CCID") : "";
                    tv.setText("\u2620\uFE0F AMARAN SCAMMER DISAHKAN \u2022 Malaysia" + smBadge);
                    tv.setTextColor(0xFFFF5252);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                }

                // 3. Transform "More info available, Upgrade to Premium to view" card
                if (isMoreInfoTrigger(text) || isMoreInfoTrigger(desc)) {
                    transformMoreInfoCard(tv, activity, engine, activeThreat, targetPhone);
                }

                // 4. Hijack "View all" in Call History
                if (isViewAllTrigger(text) || isViewAllTrigger(desc)) {
                    if (!"VIEW_ALL_HIJACKED".equals(tv.getTag())) {
                        tv.setTag("VIEW_ALL_HIJACKED");
                        tv.setText("\u2620\uFE0F BUKTI SCAMMER & REKOD PANGGILAN");
                        tv.setTextColor(0xFFEF5350);
                        tv.setTypeface(Typeface.DEFAULT_BOLD);

                        View.OnClickListener dossierClick = new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                engine.showThreatDialog(activity, activeThreat);
                            }
                        };

                        tv.setClickable(true);
                        tv.setOnClickListener(dossierClick);

                        ViewParent p = tv.getParent();
                        if (p instanceof ViewGroup) {
                            ViewGroup rowVg = (ViewGroup) p;
                            rowVg.setClickable(true);
                            rowVg.setOnClickListener(dossierClick);
                        }
                    }
                }
            } else {
                // Number is NOT confirmed scammer:
                // 1. Replace Truecaller's paid "Upgrade to Premium to view" ad with our community checker!
                if (isMoreInfoTrigger(text) || isMoreInfoTrigger(desc)) {
                    transformMoreInfoCardToChecker(tv, activity, engine, targetPhone);
                }

                // 2. Also decorate subtitle (e.g. "Malaysia") for EVERY contact (including saved phonebook contacts!)
                String lower = text.toLowerCase();
                if ((lower.equals("malaysia") || lower.startsWith("malaysia")) && !text.contains("SemakMule") && !text.contains("Rekod")) {
                    tv.setText("🇲🇾 Malaysia • \uD83D\uDEE1\uFE0F " + String.format("%,d", engine.getThreatCount()) + " Rekod Scammer");
                    tv.setTextColor(0xFF38BDF8); // Bright Sky Blue
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setClickable(true);
                    tv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            engine.showRegisterScammerDialog(activity, targetPhone);
                        }
                    });
                }
            }
        }

        if (activeThreat == null) {
            ensureFloatingCheckerPill(activity, engine, targetPhone);
        } else {
            removeFloatingCheckerPill(activity);
        }
    }

    public static void ensureFloatingPill(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            final KantoiThreatEngine engine = getInstance(activity);
            final View decor = activity.getWindow().getDecorView();
            if (decor != null) {
                decor.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!activity.isFinishing()) {
                            ensureFloatingCheckerPill(activity, engine, null);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void removeFloatingCheckerPill(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) decor;
            View existing = root.findViewWithTag("SEMAKMULE_FLOATING_PILL");
            if (existing != null) {
                root.removeView(existing);
            }
        } catch (Throwable ignored) {}
    }

    private static void ensureFloatingCheckerPill(final Activity activity, final KantoiThreatEngine engine, final String targetPhone) {
        if (activity == null || activity.isFinishing()) return;
        try {
            String actName = activity.getClass().getName().toLowerCase();
            // JANGAN sesekali paparkan pada skrin panggilan, dialog selepas panggilan, atau changelog
            if (actName.contains("aftercall") || actName.contains("incoming") || actName.contains("activecall") || actName.contains("changelog") || actName.contains("dialog")) {
                return;
            }

            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            final ViewGroup root = (ViewGroup) decor;

            View existing = root.findViewWithTag("SEMAKMULE_FLOATING_PILL");
            if (existing != null) {
                return;
            }

            boolean isNative = actName.startsWith("com.kantoi.app.");
            boolean isDetails = actName.contains("details") || actName.contains("contact") || actName.contains("singlecall") || actName.contains("viewcontact");
            if (!isNative && !isDetails && targetPhone == null) {
                return;
            }

            final FrameLayout dockContainer = new FrameLayout(activity);
            dockContainer.setTag("SEMAKMULE_FLOATING_PILL");

            // Posisi: Sebelah kanan skrin (RIGHT EDGE), ditinggikan 20%+ di atas paras bottom bar (115dp dari bawah)
            // Ini mengelakkan gangguan pada KantoiBottomBar dan meletakkannya pada paras capai ibu jari yang selesa
            final FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fl.gravity = Gravity.END | Gravity.BOTTOM;
            fl.bottomMargin = dpToPx(activity, 115);
            fl.rightMargin = 0;
            dockContainer.setLayoutParams(fl);

            // Container Kapsul Sisi Interaktif
            final LinearLayout pillBar = new LinearLayout(activity);
            pillBar.setOrientation(LinearLayout.HORIZONTAL);
            pillBar.setGravity(Gravity.CENTER_VERTICAL);
            pillBar.setPadding(dpToPx(activity, 10), dpToPx(activity, 6), dpToPx(activity, 8), dpToPx(activity, 6));

            // Bentuk Kapsul Berlabuh Tepi (Curved on Left, Flat on Right)
            final GradientDrawable dockBg = new GradientDrawable();
            dockBg.setColor(0xFF141422); // Cyber Card Void
            float r = dpToPx(activity, 20);
            dockBg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
            dockBg.setStroke(dpToPx(activity, 1), 0xFF00D4FF); // Neon Cyan
            pillBar.setBackground(dockBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                pillBar.setElevation(dpToPx(activity, 8));
            }

            // 1. Ikon PDRM Shield
            final TextView tvIcon = new TextView(activity);
            tvIcon.setText("⚡");
            tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            pillBar.addView(tvIcon);

            // 2. Label Ringkas (Dipaparkan dalam Mod Collapsed)
            final TextView tvShortLabel = new TextView(activity);
            tvShortLabel.setText("HUD");
            tvShortLabel.setTextColor(0xFF00D4FF);
            tvShortLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvShortLabel.setTypeface(Typeface.DEFAULT_BOLD);
            tvShortLabel.setPadding(dpToPx(activity, 4), 0, dpToPx(activity, 2), 0);
            pillBar.addView(tvShortLabel);

            // 3. Susun Atur Kembang (Expanded Content - Tersembunyi secara lalai, slide out apabila diketik)
            final LinearLayout expandedLayout = new LinearLayout(activity);
            expandedLayout.setOrientation(LinearLayout.HORIZONTAL);
            expandedLayout.setGravity(Gravity.CENTER_VERTICAL);
            expandedLayout.setVisibility(View.GONE);

            final TextView tvFullText = new TextView(activity);
            tvFullText.setText(" ⚡ TACTICAL HUD ");
            tvFullText.setTextColor(Color.WHITE);
            tvFullText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvFullText.setTypeface(Typeface.DEFAULT_BOLD);
            expandedLayout.addView(tvFullText);

            // Butang Tutup (✕) untuk meluncurkan kembali kapsul ke dalam
            final TextView btnCollapse = new TextView(activity);
            btnCollapse.setText("✕");
            btnCollapse.setTextColor(0xFF94A3B8);
            btnCollapse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            btnCollapse.setTypeface(Typeface.DEFAULT_BOLD);
            btnCollapse.setPadding(dpToPx(activity, 6), dpToPx(activity, 2), dpToPx(activity, 4), dpToPx(activity, 2));
            expandedLayout.addView(btnCollapse);

            pillBar.addView(expandedLayout);

            // Pengurusan Status Slide-In / Slide-Out & Pemasa Auto-Tutup
            final boolean[] isExpanded = new boolean[]{false};
            final Handler collapseHandler = new Handler(Looper.getMainLooper());
            final Runnable autoCollapseRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isExpanded[0]) {
                        animateCollapse(expandedLayout, tvShortLabel, dockBg, activity);
                        isExpanded[0] = false;
                    }
                }
            };

            // Klik pada Kapsul Tepi
            pillBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isExpanded[0]) {
                        // Slide Out (Kembang)
                        isExpanded[0] = true;
                        animateExpand(expandedLayout, tvShortLabel, dockBg, activity);
                        collapseHandler.removeCallbacks(autoCollapseRunnable);
                        collapseHandler.postDelayed(autoCollapseRunnable, 4500);
                    } else {
                        // Jika sudah kembang, klik membuka dialog semakan
                        openSemakMuleAction(activity, engine, targetPhone);
                    }
                }
            });

            // Sentuhan & Seretan Vertikal (Vertical Dragging) membolehkan pengguna mengubah ketinggian sesuka hati
            final float[] touchStartY = new float[1];
            final int[] touchStartMargin = new int[1];
            final boolean[] isDragging = new boolean[1];

            pillBar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartY[0] = event.getRawY();
                            touchStartMargin[0] = fl.bottomMargin;
                            isDragging[0] = false;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float dy = touchStartY[0] - event.getRawY();
                            if (Math.abs(dy) > dpToPx(activity, 8)) {
                                isDragging[0] = true;
                                int newMargin = (int) (touchStartMargin[0] + dy);
                                int minMargin = dpToPx(activity, 80);
                                int maxMargin = activity.getResources().getDisplayMetrics().heightPixels - dpToPx(activity, 160);
                                if (newMargin < minMargin) newMargin = minMargin;
                                if (newMargin > maxMargin) newMargin = maxMargin;
                                fl.bottomMargin = newMargin;
                                dockContainer.setLayoutParams(fl);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            if (!isDragging[0]) {
                                v.performClick();
                            }
                            break;
                    }
                    return true;
                }
            });

            // Klik pada butang ✕ untuk meluncur masuk kembali serta-merta
            btnCollapse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    collapseHandler.removeCallbacks(autoCollapseRunnable);
                    isExpanded[0] = false;
                    animateCollapse(expandedLayout, tvShortLabel, dockBg, activity);
                }
            });

            dockContainer.addView(pillBar);
            root.addView(dockContainer);
        } catch (Throwable t) {
            Log.e(TAG, "ensureFloatingCheckerPill error: " + t.getMessage());
        }
    }

    private static void animateExpand(final View expandedLayout, final View shortLabel, final GradientDrawable dockBg, final Context context) {
        shortLabel.setVisibility(View.GONE);
        expandedLayout.setVisibility(View.VISIBLE);
        dockBg.setColor(0xFF0284C7); // Warna Sky Blue aktif apabila kembang
        dockBg.setStroke(dpToPx(context, 1), 0xFF7DD3FC);

        TranslateAnimation slide = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.4f, Animation.RELATIVE_TO_SELF, 0.0f,
                                                          Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        slide.setDuration(220);
        slide.setInterpolator(new AccelerateDecelerateInterpolator());
        expandedLayout.startAnimation(slide);
    }

    private static void animateCollapse(final View expandedLayout, final View shortLabel, final GradientDrawable dockBg, final Context context) {
        expandedLayout.setVisibility(View.GONE);
        shortLabel.setVisibility(View.VISIBLE);
        dockBg.setColor(0xFF141422); // Cyber Card Void
        dockBg.setStroke(dpToPx(context, 1), 0xFF00D4FF);
    }

    private static void openSemakMuleAction(final Activity activity, final KantoiThreatEngine engine, final String targetPhone) {
        if (activity == null || activity.isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("🛡️ HAB KESELAMATAN & RISIKAN PANTAS");
        final String phoneTarget = (targetPhone != null && !targetPhone.isEmpty()) ? targetPhone : ((sCurrentViewingPhone != null) ? sCurrentViewingPhone : "");

        String[] options = new String[] {
            "🛡️ Buka Pusat Pertahanan KANTOI (Pangkalan Data & Enjin)",
            "🏛️ Buka Portal Rasmi SemakMule CCID PDRM",
            "🔍 Semak / Lapor Nombor dalam KANTOI",
            "🔗 Imbas Pautan / URL Suspek (VirusTotal)",
            "📁 Imbas Fail APK Trojan (Heuristik + VirusTotal)"
        };

        b.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).selectTab(1);
                        } else {
                            Intent it = new Intent(activity, MainActivity.class);
                            it.putExtra("select_tab", 1);
                            it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            activity.startActivity(it);
                        }
                        break;
                    case 1:
                        try {
                            Intent bIt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                            activity.startActivity(bIt);
                        } catch (Throwable t) {
                            Toast.makeText(activity, "Gagal membuka pelayar: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 2:
                        engine.showRegisterScammerDialog(activity, phoneTarget);
                        break;
                    case 3:
                        KantoiVirusTotalEngine.showUrlScanDialog(activity);
                        break;
                    case 4:
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).launchApkPicker();
                        } else {
                            try {
                                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                                intent.setType("*/*");
                                String[] mimeTypes = {"application/vnd.android.package-archive", "application/octet-stream"};
                                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                activity.startActivity(intent);
                            } catch (Throwable t) {
                                Toast.makeText(activity, "Buka tab Pertahanan di skrin utama untuk imbas fail APK.", Toast.LENGTH_SHORT).show();
                            }
                        }
                        break;
                }
            }
        });
        b.setNegativeButton("TUTUP", null);
        b.show();
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private synchronized void initDatabase() {
        if (mInitialized && mDb != null && mDb.isOpen()) return;

        try {
            File dbDir = new File(mContext.getApplicationInfo().dataDir, "databases");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }
            File dbFile = new File(dbDir, DB_NAME);

            SharedPreferences prefs = mContext.getSharedPreferences("kantoi_threat_engine_prefs", Context.MODE_PRIVATE);
            int installedVersion = prefs.getInt("db_version", 0);

            if (!dbFile.exists() || installedVersion < CURRENT_DB_VERSION) {
                // Preserve user-registered threats and custom safe contacts across database upgrades
                List<ContentValues> manualThreats = new ArrayList<ContentValues>();
                List<ContentValues> manualSafeContacts = new ArrayList<ContentValues>();
                if (dbFile.exists()) {
                    try {
                        SQLiteDatabase oldDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                        Cursor cur = oldDb.rawQuery("SELECT phone_norm, profile_id, suspect_name, all_phones, bank_accounts, ic_number, address, evidence_links, risk_tag, source FROM threat_numbers WHERE profile_id >= 9000 OR risk_tag = 'MANUAL_REPORT'", null);
                        if (cur != null && cur.moveToFirst()) {
                            do {
                                ContentValues cv = new ContentValues();
                                cv.put("phone_norm", cur.getString(0));
                                cv.put("profile_id", cur.getInt(1));
                                cv.put("suspect_name", cur.getString(2));
                                cv.put("all_phones", cur.getString(3));
                                cv.put("bank_accounts", cur.getString(4));
                                cv.put("ic_number", cur.getString(5));
                                cv.put("address", cur.getString(6));
                                cv.put("evidence_links", cur.getString(7));
                                cv.put("risk_tag", cur.getString(8));
                                cv.put("source", cur.getString(9));
                                manualThreats.add(cv);
                            } while (cur.moveToNext());
                            cur.close();
                        }

                        try {
                            Cursor curSafe = oldDb.rawQuery("SELECT phone_norm, contact_name, category, notes, created_at FROM custom_safe_contacts", null);
                            if (curSafe != null && curSafe.moveToFirst()) {
                                do {
                                    ContentValues scv = new ContentValues();
                                    scv.put("phone_norm", curSafe.getString(0));
                                    scv.put("contact_name", curSafe.getString(1));
                                    scv.put("category", curSafe.getString(2));
                                    scv.put("notes", curSafe.getString(3));
                                    scv.put("created_at", curSafe.getLong(4));
                                    manualSafeContacts.add(scv);
                                } while (curSafe.moveToNext());
                                curSafe.close();
                            }
                        } catch (Throwable ignored) {}

                        oldDb.close();
                    } catch (Throwable ignored) {}
                }

                Log.i(TAG, "Extracting " + DB_NAME + " (v" + CURRENT_DB_VERSION + ") from assets to " + dbFile.getAbsolutePath());
                InputStream is = mContext.getAssets().open(DB_NAME);
                FileOutputStream fos = new FileOutputStream(dbFile);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                fos.close();
                is.close();

                // Re-insert user-added manual threats and safe contacts into the newly extracted database
                try {
                    SQLiteDatabase newDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
                    if (!manualThreats.isEmpty()) {
                        for (ContentValues mCv : manualThreats) {
                            newDb.insertWithOnConflict("threat_numbers", null, mCv, SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        Log.i(TAG, "Successfully restored " + manualThreats.size() + " user-added threats after upgrading DB to v" + CURRENT_DB_VERSION);
                    }

                    newDb.execSQL("CREATE TABLE IF NOT EXISTS custom_safe_contacts (" +
                            "phone_norm TEXT PRIMARY KEY, " +
                            "contact_name TEXT, " +
                            "category TEXT, " +
                            "notes TEXT, " +
                            "created_at INTEGER);");

                    if (!manualSafeContacts.isEmpty()) {
                        for (ContentValues sCv : manualSafeContacts) {
                            newDb.insertWithOnConflict("custom_safe_contacts", null, sCv, SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        Log.i(TAG, "Successfully restored " + manualSafeContacts.size() + " custom safe contacts after upgrading DB to v" + CURRENT_DB_VERSION);
                    }
                    newDb.close();
                } catch (Throwable ignored) {}

                prefs.edit().putInt("db_version", CURRENT_DB_VERSION).apply();
                Log.i(TAG, "Database extracted successfully.");
            }

            mDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            mDb.execSQL("CREATE TABLE IF NOT EXISTS custom_safe_contacts (" +
                    "phone_norm TEXT PRIMARY KEY, " +
                    "contact_name TEXT, " +
                    "category TEXT, " +
                    "notes TEXT, " +
                    "created_at INTEGER);");
            loadMemoryCache();
            loadSafeContactsCache();
            mInitialized = true;
            Log.i(TAG, "KantoiThreatEngine ready with " + mThreatMap.size() + " indexed phone variants, " + mAllThreats.size() + " threats, and " + mCustomSafeContacts.size() + " safe contacts.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initDatabase: " + t.getMessage(), t);
        }
    }

    private void loadMemoryCache() {
        if (mDb == null || !mDb.isOpen()) return;
        mThreatMap.clear();
        mAllThreats.clear();

        Cursor c = null;
        try {
            c = mDb.rawQuery("SELECT phone_norm, profile_id, suspect_name, all_phones, bank_accounts, ic_number, address, evidence_links, risk_tag, source FROM threat_numbers", null);
            if (c != null && c.moveToFirst()) {
                Map<Integer, Boolean> seenProfiles = new HashMap<Integer, Boolean>();
                do {
                    ScammerThreat t = new ScammerThreat();
                    t.phoneNorm = c.getString(0);
                    t.profileId = c.getInt(1);
                    t.suspectName = c.getString(2);
                    t.allPhones = c.getString(3);
                    t.bankAccounts = c.getString(4);
                    t.icNumber = c.getString(5);
                    t.address = c.getString(6);
                    t.evidenceLinks = c.getString(7);
                    t.riskTag = c.getString(8);
                    t.source = c.getString(9);

                    if (t.phoneNorm != null && !t.phoneNorm.isEmpty()) {
                        String digits = t.phoneNorm.replaceAll("[^0-9]", "");
                        if (!digits.isEmpty()) {
                            mThreatMap.put(digits, t);
                            if (digits.startsWith("0")) {
                                String core = digits.substring(1);
                                mThreatMap.put(core, t);
                                mThreatMap.put("60" + core, t);
                            } else if (digits.startsWith("60")) {
                                String core = digits.substring(2);
                                mThreatMap.put(core, t);
                                mThreatMap.put("0" + core, t);
                            }
                        }
                    }

                    if (!seenProfiles.containsKey(t.profileId)) {
                        seenProfiles.put(t.profileId, Boolean.TRUE);
                        mAllThreats.add(t);
                    }
                } while (c.moveToNext());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in loadMemoryCache: " + t.getMessage());
        } finally {
            if (c != null) {
                try { c.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private void loadSafeContactsCache() {
        if (mDb == null || !mDb.isOpen()) return;
        mCustomSafeContacts.clear();
        Cursor c = null;
        try {
            c = mDb.rawQuery("SELECT phone_norm, contact_name, category, notes, created_at FROM custom_safe_contacts", null);
            if (c != null && c.moveToFirst()) {
                do {
                    SafeContactItem item = new SafeContactItem();
                    item.phoneNorm = c.getString(0);
                    item.contactName = c.getString(1);
                    item.category = c.getString(2);
                    item.notes = c.getString(3);
                    item.createdAt = c.getLong(4);

                    if (item.phoneNorm != null && !item.phoneNorm.isEmpty()) {
                        indexSafeContact(item.phoneNorm, item);
                    }
                } while (c.moveToNext());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in loadSafeContactsCache: " + t.getMessage());
        } finally {
            if (c != null) {
                try { c.close(); } catch (Throwable ignored) {}
            }
        }
    }

    public SafeContactItem getCustomSafeContact(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return null;
        String digits = rawNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 8) return null;
        if (mCustomSafeContacts.containsKey(digits)) {
            return mCustomSafeContacts.get(digits);
        }
        if (digits.startsWith("0")) {
            String core = digits.substring(1);
            if (mCustomSafeContacts.containsKey(core)) return mCustomSafeContacts.get(core);
            if (mCustomSafeContacts.containsKey("60" + core)) return mCustomSafeContacts.get("60" + core);
        } else if (digits.startsWith("60")) {
            String core = digits.substring(2);
            if (mCustomSafeContacts.containsKey(core)) return mCustomSafeContacts.get(core);
            if (mCustomSafeContacts.containsKey("0" + core)) return mCustomSafeContacts.get("0" + core);
        } else if (digits.startsWith("1")) {
            if (mCustomSafeContacts.containsKey("0" + digits)) return mCustomSafeContacts.get("0" + digits);
            if (mCustomSafeContacts.containsKey("60" + digits)) return mCustomSafeContacts.get("60" + digits);
        }
        return null;
    }

    public boolean isCustomSafeContact(String rawNumber) {
        return getCustomSafeContact(rawNumber) != null;
    }

    private synchronized void indexSafeContact(String localPhone, SafeContactItem item) {
        if (localPhone == null || localPhone.isEmpty()) return;
        String digits = localPhone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return;
        mCustomSafeContacts.put(digits, item);
        mNativeContactsWhitelist.add(digits);
        if (digits.startsWith("0")) {
            String core = digits.substring(1);
            mCustomSafeContacts.put(core, item);
            mCustomSafeContacts.put("60" + core, item);
            mNativeContactsWhitelist.add(core);
            mNativeContactsWhitelist.add("60" + core);
        } else if (digits.startsWith("60")) {
            String core = digits.substring(2);
            mCustomSafeContacts.put(core, item);
            mCustomSafeContacts.put("0" + core, item);
            mNativeContactsWhitelist.add(core);
            mNativeContactsWhitelist.add("0" + core);
        } else if (digits.startsWith("1")) {
            mCustomSafeContacts.put("0" + digits, item);
            mCustomSafeContacts.put("60" + digits, item);
            mNativeContactsWhitelist.add("0" + digits);
            mNativeContactsWhitelist.add("60" + digits);
        }
    }

    private synchronized void unindexSafeContact(String localPhone) {
        if (localPhone == null || localPhone.isEmpty()) return;
        String digits = localPhone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return;
        mCustomSafeContacts.remove(digits);
        mNativeContactsWhitelist.remove(digits);
        if (digits.startsWith("0")) {
            String core = digits.substring(1);
            mCustomSafeContacts.remove(core);
            mCustomSafeContacts.remove("60" + core);
            mNativeContactsWhitelist.remove(core);
            mNativeContactsWhitelist.remove("60" + core);
        } else if (digits.startsWith("60")) {
            String core = digits.substring(2);
            mCustomSafeContacts.remove(core);
            mCustomSafeContacts.remove("0" + core);
            mNativeContactsWhitelist.remove(core);
            mNativeContactsWhitelist.remove("0" + core);
        } else if (digits.startsWith("1")) {
            mCustomSafeContacts.remove("0" + digits);
            mCustomSafeContacts.remove("60" + digits);
            mNativeContactsWhitelist.remove("0" + digits);
            mNativeContactsWhitelist.remove("60" + digits);
        }
    }

    public synchronized boolean saveSafeContact(String phoneRaw, String contactName, String category, String notes) {
        if (phoneRaw == null || phoneRaw.trim().isEmpty()) return false;
        if (!mInitialized) initDatabase();
        if (mDb == null) return false;
        try {
            String digits = phoneRaw.replaceAll("[^0-9]", "");
            String localPhone = normalizeToLocal(digits);
            if (localPhone == null || localPhone.length() < 8) return false;

            if (mDb.isReadOnly()) {
                mDb.close();
                File dbDir = new File(mContext.getApplicationInfo().dataDir, "databases");
                File dbFile = new File(dbDir, DB_NAME);
                mDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            }

            // CRITICAL: Mutual Exclusivity - purge from threat numbers if previously saved as scammer!
            deleteThreatLocal(localPhone);

            ContentValues cv = new ContentValues();
            cv.put("phone_norm", localPhone);
            cv.put("contact_name", (contactName != null && !contactName.trim().isEmpty()) ? contactName.trim() : "Kenalan Selamat");
            cv.put("category", (category != null && !category.trim().isEmpty()) ? category.trim() : "Sahabat");
            cv.put("notes", (notes != null && !notes.trim().isEmpty()) ? notes.trim() : "");
            cv.put("created_at", System.currentTimeMillis());

            mDb.insertWithOnConflict("custom_safe_contacts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);

            SafeContactItem item = new SafeContactItem();
            item.phoneNorm = localPhone;
            item.contactName = cv.getAsString("contact_name");
            item.category = cv.getAsString("category");
            item.notes = cv.getAsString("notes");
            item.createdAt = cv.getAsLong("created_at");

            indexSafeContact(localPhone, item);
            Log.i(TAG, "Successfully saved safe contact: " + localPhone + " (" + item.contactName + " - " + item.category + ")");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "saveSafeContact error: " + t.getMessage(), t);
            return false;
        }
    }

    public synchronized boolean deleteSafeContact(String phoneRaw) {
        if (phoneRaw == null || phoneRaw.trim().isEmpty()) return false;
        if (!mInitialized) initDatabase();
        if (mDb == null) return false;
        try {
            String digits = phoneRaw.replaceAll("[^0-9]", "");
            String localPhone = normalizeToLocal(digits);
            if (localPhone == null || localPhone.length() < 8) return false;

            if (mDb.isReadOnly()) {
                mDb.close();
                File dbDir = new File(mContext.getApplicationInfo().dataDir, "databases");
                File dbFile = new File(dbDir, DB_NAME);
                mDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            }

            mDb.delete("custom_safe_contacts", "phone_norm = ?", new String[]{localPhone});
            unindexSafeContact(localPhone);
            Log.i(TAG, "Successfully deleted safe contact: " + localPhone);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "deleteSafeContact error: " + t.getMessage(), t);
            return false;
        }
    }

    public int getCustomSafeContactsCount() {
        return mCustomSafeContacts.size();
    }

    public ScammerThreat checkThreat(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        if (isCustomSafeContact(input)) return null;
        if (!mInitialized) initDatabase();
        if (isCustomSafeContact(input)) return null;

        String raw = input.trim();
        String digits = raw.replaceAll("[^0-9]", "");

        // Only valid phone number lengths (8 to 14 digits) are evaluated
        if (digits.length() >= 8 && digits.length() <= 14) {
            if (mThreatMap.containsKey(digits)) {
                return mThreatMap.get(digits);
            }
            if (digits.startsWith("0")) {
                String core = digits.substring(1);
                if (mThreatMap.containsKey(core)) return mThreatMap.get(core);
            } else if (digits.startsWith("60")) {
                String core = digits.substring(2);
                if (mThreatMap.containsKey(core)) return mThreatMap.get(core);
            } else if (digits.startsWith("1")) {
                if (mThreatMap.containsKey("0" + digits)) return mThreatMap.get("0" + digits);
            }
        }

        // Strictly return null for anything else — no fuzzy substring or name guessing!
        return null;
    }

    public static String normalizeToLocal(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("60")) {
            return "0" + digits.substring(2);
        } else if (digits.startsWith("0")) {
            return digits;
        } else if (digits.startsWith("1")) {
            return "0" + digits;
        }
        return digits;
    }

    public static String normalizeToIntl(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        if (digits.startsWith("60")) {
            return "+" + digits;
        } else if (digits.startsWith("0")) {
            return "+60" + digits.substring(1);
        } else if (digits.startsWith("1")) {
            return "+60" + digits;
        }
        return "+" + digits;
    }

    private static Activity activity_to_activity(Context c) {
        if (c instanceof Activity) return (Activity) c;
        if (sLastForegroundActivity != null) return sLastForegroundActivity.get();
        return null;
    }

    public synchronized void indexPhoneThreat(String localPhone, ScammerThreat t) {
        if (localPhone == null || localPhone.isEmpty()) return;
        String digits = localPhone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return;
        synchronized (mThreatMap) {
            mThreatMap.put(digits, t);
            if (digits.startsWith("0")) {
                String core = digits.substring(1);
                mThreatMap.put(core, t);
                mThreatMap.put("60" + core, t);
            } else if (digits.startsWith("60")) {
                String core = digits.substring(2);
                mThreatMap.put(core, t);
                mThreatMap.put("0" + core, t);
            } else if (digits.startsWith("1")) {
                mThreatMap.put("0" + digits, t);
                mThreatMap.put("60" + digits, t);
            }
        }
        synchronized (mAllThreats) {
            mAllThreats.add(t);
        }
    }

    public synchronized void unindexPhoneThreat(String localPhone) {
        if (localPhone == null || localPhone.isEmpty()) return;
        String digits = localPhone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return;
        synchronized (mThreatMap) {
            mThreatMap.remove(digits);
            if (digits.startsWith("0")) {
                String core = digits.substring(1);
                mThreatMap.remove(core);
                mThreatMap.remove("60" + core);
            } else if (digits.startsWith("60")) {
                String core = digits.substring(2);
                mThreatMap.remove(core);
                mThreatMap.remove("0" + core);
            } else if (digits.startsWith("1")) {
                mThreatMap.remove("0" + digits);
                mThreatMap.remove("60" + digits);
            }
        }
        synchronized (mAllThreats) {
            for (int i = mAllThreats.size() - 1; i >= 0; i--) {
                ScammerThreat item = mAllThreats.get(i);
                if (digits.equals(item.phoneNorm != null ? item.phoneNorm.replaceAll("[^0-9]", "") : "")) {
                    mAllThreats.remove(i);
                }
            }
        }
    }

    public synchronized boolean saveThreatLocal(String phoneRaw, String suspectName, String address, String bankAccounts, String icNumber, String notes, String category, int semakCount, int reportCount) {
        if (phoneRaw == null || phoneRaw.trim().isEmpty()) return false;
        if (!mInitialized) initDatabase();
        if (mDb == null) return false;
        try {
            String digits = phoneRaw.replaceAll("[^0-9]", "");
            String localPhone = normalizeToLocal(digits);
            if (localPhone == null || localPhone.length() < 8) return false;

            // CRITICAL: Mutual Exclusivity - purge from custom safe contacts!
            deleteSafeContact(localPhone);

            if (mDb.isReadOnly()) {
                mDb.close();
                File dbDir = new File(mContext.getApplicationInfo().dataDir, "databases");
                File dbFile = new File(dbDir, DB_NAME);
                mDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            }

            int newProfileId = 9000;
            Cursor cp = mDb.rawQuery("SELECT MAX(profile_id) FROM threat_numbers", null);
            if (cp != null) {
                if (cp.moveToFirst()) {
                    newProfileId = Math.max(9000, cp.getInt(0) + 1);
                }
                cp.close();
            }

            Cursor c = mDb.rawQuery("SELECT profile_id FROM threat_numbers WHERE phone_norm = ?", new String[]{localPhone});
            boolean exists = (c != null && c.moveToFirst());
            if (exists) {
                newProfileId = c.getInt(0);
            }
            if (c != null) c.close();

            ContentValues cv = new ContentValues();
            cv.put("phone_norm", localPhone);
            cv.put("profile_id", newProfileId);
            cv.put("suspect_name", (suspectName != null && !suspectName.trim().isEmpty()) ? suspectName.trim() : "Suspek Scammer");
            cv.put("all_phones", localPhone);
            cv.put("bank_accounts", (bankAccounts != null && !bankAccounts.trim().isEmpty()) ? bankAccounts.trim() : "Sila semak di portal SemakMule");
            cv.put("ic_number", (icNumber != null && !icNumber.trim().isEmpty()) ? icNumber.trim() : "N/A");
            cv.put("address", (address != null && !address.trim().isEmpty()) ? address.trim() : "N/A");
            cv.put("evidence_links", (notes != null && !notes.trim().isEmpty()) ? notes.trim() : "https://semakmule.rmp.gov.my/");
            cv.put("risk_tag", (category != null && !category.isEmpty()) ? category : "MANUAL_REPORT");
            cv.put("source", "Laporan Pengguna & SemakMule");

            if (exists) {
                mDb.update("threat_numbers", cv, "phone_norm = ?", new String[]{localPhone});
            } else {
                mDb.insertWithOnConflict("threat_numbers", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }

            ScammerThreat t = new ScammerThreat();
            t.phoneNorm = localPhone;
            t.profileId = newProfileId;
            t.suspectName = cv.getAsString("suspect_name");
            t.allPhones = cv.getAsString("all_phones");
            t.bankAccounts = cv.getAsString("bank_accounts");
            t.icNumber = cv.getAsString("ic_number");
            t.address = cv.getAsString("address");
            t.evidenceLinks = cv.getAsString("evidence_links");
            t.riskTag = cv.getAsString("risk_tag");
            t.source = cv.getAsString("source");

            indexPhoneThreat(localPhone, t);

            if (semakCount >= 0 || reportCount >= 0) {
                SemakMuleData sm = new SemakMuleData();
                sm.searchCount = semakCount;
                sm.reportCount = reportCount;
                sm.loading = false;
                sSemakMuleMap.put(localPhone, sm);
            }

            sLastThreatOnScreen = t;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "saveThreatLocal error: " + t.getMessage(), t);
            return false;
        }
    }

    public synchronized boolean saveThreatLocal(String phoneRaw, String suspectName, String notes, String category, int semakCount, int reportCount) {
        return saveThreatLocal(phoneRaw, suspectName, "N/A", "Sila semak di portal SemakMule", "N/A", notes, category, semakCount, reportCount);
    }

    public synchronized boolean deleteThreatLocal(String phoneRaw) {
        if (phoneRaw == null || phoneRaw.trim().isEmpty()) return false;
        if (!mInitialized) initDatabase();
        if (mDb == null) return false;
        try {
            String digits = phoneRaw.replaceAll("[^0-9]", "");
            String localPhone = normalizeToLocal(digits);
            if (localPhone == null || localPhone.length() < 8) return false;

            if (mDb.isReadOnly()) {
                mDb.close();
                File dbDir = new File(mContext.getApplicationInfo().dataDir, "databases");
                File dbFile = new File(dbDir, DB_NAME);
                mDb = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            }

            mDb.delete("threat_numbers", "phone_norm = ?", new String[]{localPhone});
            unindexPhoneThreat(localPhone);

            if (sLastThreatOnScreen != null && localPhone.equals(normalizeToLocal(sLastThreatOnScreen.phoneNorm))) {
                sLastThreatOnScreen = null;
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "deleteThreatLocal error: " + t.getMessage(), t);
            return false;
        }
    }

    public synchronized int getThreatCount() {
        if (!mInitialized) initDatabase();
        if (mDb != null && mDb.isOpen()) {
            try {
                Cursor c = mDb.rawQuery("SELECT COUNT(*) FROM threat_numbers", null);
                if (c != null) {
                    int count = 0;
                    if (c.moveToFirst()) {
                        count = c.getInt(0);
                    }
                    c.close();
                    if (count > 0) return count;
                }
            } catch (Throwable ignored) {}
        }
        if (mThreatMap != null && !mThreatMap.isEmpty()) {
            return mThreatMap.size();
        }
        return 3729;
    }

    public static void showDatabaseInfoDialog(final Context context) {
        if (context == null) return;
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            b.setTitle("\uD83D\uDEE1\uFE0F STATUS PANGKALAN DATA RISIKAN");

            KantoiThreatEngine engine = getInstance(context);
            int total = (engine != null) ? engine.getThreatCount() : 3729;
            String msg = "Jumlah Perlindungan Aktif: " + String.format("%,d", total) + " Rekod\n\n"
                    + "Pecahan Sumber Risikan Komuniti:\n"
                    + "• \uD83D\uDD34 Forum Lowyat: 1,500 kes scammer\n"
                    + "• \uD83D\uDFE0 Forum CariGold: 554 kes penipuan\n"
                    + "• \uD83D\uDD35 Risikan Telegram (OCR): 1,675 kes & 495 akaun bank keldai\n"
                    + "• \uD83D\uDFE2 JSJK CCID SemakMule: Carian Langsung & Portal Rasmi PDRM\n"
                    + "• \uD83C\uDF10 Google CSE: Enjin Carian Pertikaian Media Sosial\n\n"
                    + "\u2139\uFE0F Pangkalan data disimpan 100% pada peranti anda (offline) bagi memastikan perlindungan serta-merta semasa panggilan masuk tanpa memerlukan internet.";

            b.setMessage(msg);
            b.setPositiveButton("FAHAM", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                }
            });
            b.show();
        } catch (Throwable t) {
            Log.e(TAG, "showDatabaseInfoDialog error: " + t.getMessage(), t);
        }
    }

    public static void openGoogleCseSearch(final Context context, final String phone) {
        if (context == null) return;
        final Activity act = activity_to_activity(context);
        if (act == null || act.isFinishing()) {
            openGoogleCseInBrowser(context, phone);
            return;
        }
        showGoogleCseDialog(act, phone);
    }

    private static boolean handleCseUrlLoading(WebView view, String url, Activity activity) {
        if (url == null) return false;
        String lower = url.toLowerCase();

        // Internal Google CSE and Google infrastructure services: keep loading inside WebView!
        if (lower.contains("google.") || lower.contains("gstatic.") || lower.contains("googleapis.")
                || lower.contains("doubleclick.") || lower.contains("recaptcha.") || lower.contains("googletagmanager.")) {
            return false; // Let WebView load it natively!
        }

        // External websites (e.g. Lowyat, CariGold, Facebook, Twitter/X, TikTok, Blogs) clicked by user:
        try {
            Intent extIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            extIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(extIntent);
            Toast.makeText(activity, "\uD83C\uDF10 Membuka pautan artikel luar...", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void showGoogleCseDialog(final Activity activity, final String phone) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String digits = (phone != null) ? phone.replaceAll("[^0-9]", "") : "";
                    String target = digits;
                    if (target.startsWith("60")) {
                        target = "0" + target.substring(2);
                    }
                    if (target.isEmpty() && phone != null) {
                        target = phone.trim();
                    }
                    if (target.isEmpty()) {
                        Toast.makeText(activity, "Sila masukkan nombor telefon untuk semakan.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final String query = URLEncoder.encode(target, "UTF-8");
                    final String cseUrl = "https://cse.google.com/cse?cx=53a0041f2f24f4e3b&q=" + query + "#gsc.tab=0&gsc.q=" + query;

                    final Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                    LinearLayout root = new LinearLayout(activity);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setBackgroundColor(0xFF0F172A); // Dark Slate background
                    root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    // 1. In-App Header Bar (Tiada bar URL langsung)
                    LinearLayout headerBar = new LinearLayout(activity);
                    headerBar.setOrientation(LinearLayout.HORIZONTAL);
                    headerBar.setGravity(Gravity.CENTER_VERTICAL);
                    headerBar.setPadding(dpToPx(activity, 16), dpToPx(activity, 12), dpToPx(activity, 14), dpToPx(activity, 12));
                    headerBar.setBackgroundColor(0xFF1E293B);

                    LinearLayout titleBox = new LinearLayout(activity);
                    titleBox.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams tP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    titleBox.setLayoutParams(tP);

                    TextView tvTitle = new TextView(activity);
                    tvTitle.setText("\uD83C\uDF10 CARIAN PERTIKAIAN MEDIA SOSIAL");
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    titleBox.addView(tvTitle);

                    TextView tvSub = new TextView(activity);
                    tvSub.setText("Enjin Risikan Komuniti \u2022 [" + target + "]");
                    tvSub.setTextColor(0xFF38BDF8);
                    tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    titleBox.addView(tvSub);

                    headerBar.addView(titleBox);

                    Button btnClose = new Button(activity);
                    btnClose.setText("\u2715 TUTUP");
                    btnClose.setTextColor(Color.WHITE);
                    btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    btnClose.setTypeface(Typeface.DEFAULT_BOLD);
                    GradientDrawable cBg = new GradientDrawable();
                    cBg.setColor(0xFFDC2626); // Crimson Red
                    cBg.setCornerRadius(dpToPx(activity, 8));
                    btnClose.setBackground(cBg);
                    LinearLayout.LayoutParams cP = new LinearLayout.LayoutParams(dpToPx(activity, 80), dpToPx(activity, 36));
                    btnClose.setLayoutParams(cP);
                    headerBar.addView(btnClose);

                    root.addView(headerBar);

                    // 2. Garis Loading Indikator (Determinate 0-100%)
                    final ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                    progressBar.setMax(100);
                    progressBar.setProgress(5);
                    progressBar.setVisibility(View.VISIBLE);
                    LinearLayout.LayoutParams pbP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 4));
                    progressBar.setLayoutParams(pbP);
                    root.addView(progressBar);

                    // 3. Embedded In-App WebView (Headless / Iframe Container tanpa URL bar)
                    final WebView webView = new WebView(activity);
                    LinearLayout.LayoutParams wvP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
                    webView.setLayoutParams(wvP);
                    webView.setBackgroundColor(Color.WHITE);
                    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    WebSettings ws = webView.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(true);
                    ws.setDatabaseEnabled(true);
                    ws.setJavaScriptCanOpenWindowsAutomatically(true);
                    ws.setSupportZoom(true);
                    ws.setBuiltInZoomControls(true);
                    ws.setDisplayZoomControls(false);
                    ws.setUseWideViewPort(true);
                    ws.setLoadWithOverviewMode(true);
                    ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    ws.setCacheMode(WebSettings.LOAD_DEFAULT);
                    ws.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
                    }
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true);

                    webView.setWebChromeClient(new WebChromeClient() {
                        @Override
                        public void onProgressChanged(WebView view, int newProgress) {
                            if (newProgress >= 100) {
                                progressBar.setVisibility(View.GONE);
                            } else {
                                progressBar.setVisibility(View.VISIBLE);
                                progressBar.setProgress(newProgress);
                            }
                        }
                    });

                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            progressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                            if (request != null && request.getUrl() != null) {
                                return handleCseUrlLoading(view, request.getUrl().toString(), activity);
                            }
                            return false;
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                            return handleCseUrlLoading(view, url, activity);
                        }

                        @Override
                        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                            super.onReceivedError(view, errorCode, description, failingUrl);
                            Log.w(TAG, "WebView error: " + description + " for " + failingUrl);
                        }
                    });

                    root.addView(webView);

                    btnClose.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                webView.stopLoading();
                                webView.destroy();
                            } catch (Throwable ignored) {}
                            dialog.dismiss();
                        }
                    });

                    dialog.setContentView(root);
                    dialog.setCancelable(true);
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface d) {
                            try {
                                webView.stopLoading();
                                webView.destroy();
                            } catch (Throwable ignored) {}
                        }
                    });

                    dialog.show();

                    Window win = dialog.getWindow();
                    if (win != null) {
                        GradientDrawable dWinBg = new GradientDrawable();
                        dWinBg.setColor(0xFF0F172A);
                        dWinBg.setCornerRadius(dpToPx(activity, 14));
                        win.setBackgroundDrawable(dWinBg);

                        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                        lp.copyFrom(win.getAttributes());
                        lp.width = (int) (dm.widthPixels * 0.95);
                        lp.height = (int) (dm.heightPixels * 0.88);
                        lp.gravity = Gravity.CENTER;
                        win.setAttributes(lp);
                    }

                    webView.loadUrl(cseUrl);
                    Toast.makeText(activity, "\uD83C\uDF10 Memuatkan semakan media sosial untuk [" + target + "]...", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Log.e(TAG, "showGoogleCseDialog error: " + t.getMessage(), t);
                    openGoogleCseInBrowser(activity, phone);
                }
            }
        });
    }

    private static void openGoogleCseInBrowser(final Context context, final String phone) {
        if (context == null) return;
        try {
            String digits = (phone != null) ? phone.replaceAll("[^0-9]", "") : "";
            String target = digits;
            if (target.startsWith("60")) {
                target = "0" + target.substring(2);
            }
            if (target.isEmpty() && phone != null) {
                target = phone.trim();
            }
            String query = URLEncoder.encode(target, "UTF-8");
            String cseUrl = "https://cse.google.com/cse?cx=53a0041f2f24f4e3b&q=" + query + "#gsc.tab=0&gsc.q=" + query;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(cseUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static void setMarginTop(Context context, View v, int topDp) {
        if (v == null || context == null) return;
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) p).topMargin = dpToPx(context, topDp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpToPx(context, topDp);
            v.setLayoutParams(lp);
        }
    }

    public static void showWhatsAppHubDialog(final Context context, final String rawNumber) {
        if (context == null) return;
        final Activity activity = activity_to_activity(context);
        if (activity == null || activity.isFinishing()) return;

        final String cleanLocal = normalizeToLocal(rawNumber);
        final String intlNumber = normalizeToIntl(rawNumber);
        final String waDigits = intlNumber.replace("+", "");

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                    LinearLayout root = new LinearLayout(activity);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setPadding(dpToPx(activity, 20), dpToPx(activity, 18), dpToPx(activity, 20), dpToPx(activity, 20));
                    root.setBackgroundColor(0xFF0F172A); // Slate 900 Tactical

                    // 1. Header Row
                    LinearLayout headerRow = new LinearLayout(activity);
                    headerRow.setOrientation(LinearLayout.HORIZONTAL);
                    headerRow.setGravity(Gravity.CENTER_VERTICAL);

                    TextView tvIcon = new TextView(activity);
                    tvIcon.setText("💬");
                    tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    headerRow.addView(tvIcon);

                    LinearLayout titleCol = new LinearLayout(activity);
                    titleCol.setOrientation(LinearLayout.VERTICAL);
                    titleCol.setPadding(dpToPx(activity, 12), 0, 0, 0);
                    LinearLayout.LayoutParams tcP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    titleCol.setLayoutParams(tcP);

                    TextView tvTitle = new TextView(activity);
                    tvTitle.setText("HAB OPERASI WHATSAPP");
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    titleCol.addView(tvTitle);

                    TextView tvSub = new TextView(activity);
                    tvSub.setText(formatPhoneNumber(cleanLocal) + " (" + intlNumber + ")");
                    tvSub.setTextColor(0xFF38BDF8); // Cyber Cyan
                    tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    titleCol.addView(tvSub);

                    headerRow.addView(titleCol);

                    Button btnClose = new Button(activity);
                    btnClose.setText("✕");
                    btnClose.setTextColor(0xFF94A3B8);
                    btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    btnClose.setBackgroundColor(Color.TRANSPARENT);
                    btnClose.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                        }
                    });
                    headerRow.addView(btnClose);
                    root.addView(headerRow);

                    // Notice / Subtext
                    TextView tvDesc = new TextView(activity);
                    tvDesc.setText("Pilih mod operasi yang diinginkan. Anda boleh menyiasat gambar profil & kebocoran data secara sulit atau terus bersembang tanpa perlu simpan ke kenalan.");
                    tvDesc.setTextColor(0xFF94A3B8);
                    tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    setMarginTop(activity, tvDesc, 10);
                    root.addView(tvDesc);

                    // Card Option 1: 🌐 SIASAT DI BROWSER CHROME (DISYORKAN - BEBAS SEKATAN)
                    LinearLayout cardBrowser = new LinearLayout(activity);
                    cardBrowser.setOrientation(LinearLayout.VERTICAL);
                    cardBrowser.setPadding(dpToPx(activity, 14), dpToPx(activity, 14), dpToPx(activity, 14), dpToPx(activity, 14));
                    setMarginTop(activity, cardBrowser, 14);

                    GradientDrawable brBg = new GradientDrawable();
                    brBg.setColor(0xFF064E3B); // Deep Emerald
                    brBg.setCornerRadius(dpToPx(activity, 12));
                    brBg.setStroke(dpToPx(activity, 2), 0xFF10B981);
                    cardBrowser.setBackground(brBg);
                    cardBrowser.setClickable(true);

                    TextView tvBrTitle = new TextView(activity);
                    tvBrTitle.setText("🌐 1. SIASAT DI CHROME / BROWSER (DISYORKAN)");
                    tvBrTitle.setTextColor(0xFF34D399);
                    tvBrTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvBrTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    cardBrowser.addView(tvBrTitle);

                    TextView tvBrDesc = new TextView(activity);
                    tvBrDesc.setText("Buka CheckLeaked CC terus di aplikasi pelayar telefon anda. Bebas daripada sebarang sekatan bot Cloudflare & paparan penuh. Nombor disalin automatik.");
                    tvBrDesc.setTextColor(0xFFD1FAE5);
                    tvBrDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    setMarginTop(activity, tvBrDesc, 4);
                    cardBrowser.addView(tvBrDesc);

                    cardBrowser.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            openWhatsAppIntelInBrowser(activity, cleanLocal);
                        }
                    });
                    root.addView(cardBrowser);

                    // Card Option 2: 📱 PAPARAN DALAM APLIKASI KANTOI (WEBVIEW HUD)
                    LinearLayout cardOsint = new LinearLayout(activity);
                    cardOsint.setOrientation(LinearLayout.VERTICAL);
                    cardOsint.setPadding(dpToPx(activity, 14), dpToPx(activity, 14), dpToPx(activity, 14), dpToPx(activity, 14));
                    setMarginTop(activity, cardOsint, 10);

                    GradientDrawable osintBg = new GradientDrawable();
                    osintBg.setColor(0xFF1E293B); // Slate
                    osintBg.setCornerRadius(dpToPx(activity, 12));
                    osintBg.setStroke(dpToPx(activity, 1), 0xFF0284C7);
                    cardOsint.setBackground(osintBg);
                    cardOsint.setClickable(true);

                    TextView tvOsintTitle = new TextView(activity);
                    tvOsintTitle.setText("📱 2. SIASAT DALAM APLIKASI (WEBVIEW HUD)");
                    tvOsintTitle.setTextColor(0xFF38BDF8);
                    tvOsintTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvOsintTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    cardOsint.addView(tvOsintTitle);

                    TextView tvOsintDesc = new TextView(activity);
                    tvOsintDesc.setText("Paparan skrin mini terus dalam KANTOI tanpa keluar dari aplikasi.");
                    tvOsintDesc.setTextColor(0xFF94A3B8);
                    tvOsintDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    setMarginTop(activity, tvOsintDesc, 4);
                    cardOsint.addView(tvOsintDesc);

                    cardOsint.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            showWhatsAppIntelDialog(activity, cleanLocal);
                        }
                    });
                    root.addView(cardOsint);

                    // Card Option 3: 💬 BUKA SEMBANG WHATSAPP TERUS (WA.ME)
                    LinearLayout cardChat = new LinearLayout(activity);
                    cardChat.setOrientation(LinearLayout.VERTICAL);
                    cardChat.setPadding(dpToPx(activity, 14), dpToPx(activity, 14), dpToPx(activity, 14), dpToPx(activity, 14));
                    setMarginTop(activity, cardChat, 10);

                    GradientDrawable chatBg = new GradientDrawable();
                    chatBg.setColor(0xFF0F172A); // Dark Slate
                    chatBg.setCornerRadius(dpToPx(activity, 12));
                    chatBg.setStroke(dpToPx(activity, 1), 0xFF334155);
                    cardChat.setBackground(chatBg);
                    cardChat.setClickable(true);

                    TextView tvChatTitle = new TextView(activity);
                    tvChatTitle.setText("💬 3. BUKA SEMBANG TERUS (DIRECT CHAT)");
                    tvChatTitle.setTextColor(0xFFE2E8F0);
                    tvChatTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvChatTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    cardChat.addView(tvChatTitle);

                    TextView tvChatDesc = new TextView(activity);
                    tvChatDesc.setText("Buka perbualan terus di aplikasi WhatsApp tanpa perlu menyimpan nombor ini ke dalam Buku Telefon Android anda.");
                    tvChatDesc.setTextColor(0xFF94A3B8);
                    tvChatDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    setMarginTop(activity, tvChatDesc, 4);
                    cardChat.addView(tvChatDesc);

                    cardChat.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            try {
                                Intent waIt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waDigits));
                                waIt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(waIt);
                            } catch (Throwable t) {
                                Toast.makeText(activity, "Gagal membuka WhatsApp: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    root.addView(cardChat);

                    dialog.setContentView(root);
                    dialog.setCancelable(true);
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.show();

                    Window win = dialog.getWindow();
                    if (win != null) {
                        GradientDrawable dWinBg = new GradientDrawable();
                        dWinBg.setColor(0xFF0F172A);
                        dWinBg.setCornerRadius(dpToPx(activity, 16));
                        win.setBackgroundDrawable(dWinBg);

                        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                        lp.copyFrom(win.getAttributes());
                        lp.width = (int) (dm.widthPixels * 0.94);
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        lp.gravity = Gravity.CENTER;
                        win.setAttributes(lp);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "showWhatsAppHubDialog error: " + t.getMessage(), t);
                }
            }
        });
    }

    public static void showWhatsAppIntelDialog(final Context context, final String rawNumber) {
        if (context == null) return;
        final Activity activity = activity_to_activity(context);
        if (activity == null || activity.isFinishing()) return;

        final String cleanLocal = normalizeToLocal(rawNumber);
        final String intlNumber = normalizeToIntl(rawNumber);
        final String checkLeakedUrl = "https://whatsapp.checkleaked.cc/";

        // Auto-copy international number to clipboard
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("WhatsApp Number", intlNumber));
            }
        } catch (Throwable ignored) {}

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                    LinearLayout root = new LinearLayout(activity);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setBackgroundColor(0xFF0F172A); // Deep Slate 900 Tactical
                    root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    // 1. Tactical HUD Header Bar
                    LinearLayout headerBar = new LinearLayout(activity);
                    headerBar.setOrientation(LinearLayout.HORIZONTAL);
                    headerBar.setGravity(Gravity.CENTER_VERTICAL);
                    headerBar.setPadding(dpToPx(activity, 16), dpToPx(activity, 12), dpToPx(activity, 16), dpToPx(activity, 10));
                    headerBar.setBackgroundColor(0xFF1E293B);

                    LinearLayout titleBox = new LinearLayout(activity);
                    titleBox.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams tP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    titleBox.setLayoutParams(tP);

                    TextView tvTitle = new TextView(activity);
                    tvTitle.setText("📸 RISIKAN WHATSAPP & KEBOCORAN");
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    titleBox.addView(tvTitle);

                    TextView tvSub = new TextView(activity);
                    tvSub.setText("OSINT CheckLeaked • DP & Profil Suspek");
                    tvSub.setTextColor(0xFF38BDF8);
                    tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    titleBox.addView(tvSub);

                    headerBar.addView(titleBox);

                    Button btnClose = new Button(activity);
                    btnClose.setText("✕ TUTUP");
                    btnClose.setTextColor(Color.WHITE);
                    btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    btnClose.setTypeface(Typeface.DEFAULT_BOLD);
                    GradientDrawable cBg = new GradientDrawable();
                    cBg.setColor(0xFFDC2626);
                    cBg.setCornerRadius(dpToPx(activity, 6));
                    btnClose.setBackground(cBg);
                    LinearLayout.LayoutParams cP = new LinearLayout.LayoutParams(dpToPx(activity, 74), dpToPx(activity, 34));
                    btnClose.setLayoutParams(cP);
                    headerBar.addView(btnClose);

                    root.addView(headerBar);

                    // 2. Tactical Target Banner with Copy Badge
                    LinearLayout banner = new LinearLayout(activity);
                    banner.setOrientation(LinearLayout.HORIZONTAL);
                    banner.setGravity(Gravity.CENTER_VERTICAL);
                    banner.setPadding(dpToPx(activity, 14), dpToPx(activity, 8), dpToPx(activity, 14), dpToPx(activity, 8));
                    banner.setBackgroundColor(0xFF064E3B); // Emerald Banner

                    LinearLayout bannerInfo = new LinearLayout(activity);
                    bannerInfo.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams biP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    bannerInfo.setLayoutParams(biP);

                    TextView tvTargetNum = new TextView(activity);
                    tvTargetNum.setText("🎯 SASARAN: " + intlNumber + " (" + cleanLocal + ")");
                    tvTargetNum.setTextColor(0xFF34D399);
                    tvTargetNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    tvTargetNum.setTypeface(Typeface.DEFAULT_BOLD);
                    bannerInfo.addView(tvTargetNum);

                    TextView tvTargetNotice = new TextView(activity);
                    tvTargetNotice.setText("✓ Nombor disalin automatik. Jika skrin putih, tekan [🌐 BUKA CHROME].");
                    tvTargetNotice.setTextColor(0xFFD1FAE5);
                    tvTargetNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    bannerInfo.addView(tvTargetNotice);

                    banner.addView(bannerInfo);

                    Button btnRecopy = new Button(activity);
                    btnRecopy.setText("📋 SALIN");
                    btnRecopy.setTextColor(Color.WHITE);
                    btnRecopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    btnRecopy.setTypeface(Typeface.DEFAULT_BOLD);
                    GradientDrawable rcBg = new GradientDrawable();
                    rcBg.setColor(0xFF059669);
                    rcBg.setCornerRadius(dpToPx(activity, 4));
                    btnRecopy.setBackground(rcBg);
                    LinearLayout.LayoutParams rcP = new LinearLayout.LayoutParams(dpToPx(activity, 64), dpToPx(activity, 30));
                    btnRecopy.setLayoutParams(rcP);
                    btnRecopy.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(ClipData.newPlainText("WhatsApp Number", intlNumber));
                                    Toast.makeText(activity, "Nombor " + intlNumber + " disalin semula!", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    banner.addView(btnRecopy);

                    root.addView(banner);

                    // 3. Loading Indicator
                    final ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                    progressBar.setMax(100);
                    progressBar.setProgress(10);
                    progressBar.setVisibility(View.VISIBLE);
                    LinearLayout.LayoutParams pbP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 3));
                    progressBar.setLayoutParams(pbP);
                    root.addView(progressBar);

                    // Error Fallback Container (Hidden by default, shown if page errors or blocked)
                    final LinearLayout errorContainer = new LinearLayout(activity);
                    errorContainer.setOrientation(LinearLayout.VERTICAL);
                    errorContainer.setPadding(dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16), dpToPx(activity, 16));
                    errorContainer.setBackgroundColor(0xFF1E293B);
                    errorContainer.setVisibility(View.GONE);

                    TextView tvErrNotice = new TextView(activity);
                    tvErrNotice.setText("⚠️ Laman web CheckLeaked memerlukan pengesahan pelayar penuh (Cloudflare Security Check). Sila tekan butang di bawah untuk membuka terus di pelayar Chrome:");
                    tvErrNotice.setTextColor(0xFFFBBF24); // Amber
                    tvErrNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    errorContainer.addView(tvErrNotice);

                    Button btnOpenInChrome = new Button(activity);
                    btnOpenInChrome.setText("🌐 BUKA TERUS DI PELAYAR CHROME (AUTO-SALIN)");
                    btnOpenInChrome.setTextColor(Color.WHITE);
                    btnOpenInChrome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    btnOpenInChrome.setTypeface(Typeface.DEFAULT_BOLD);
                    GradientDrawable crBg = new GradientDrawable();
                    crBg.setColor(0xFF059669);
                    crBg.setCornerRadius(dpToPx(activity, 8));
                    btnOpenInChrome.setBackground(crBg);
                    LinearLayout.LayoutParams ocP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 44));
                    ocP.setMargins(0, dpToPx(activity, 10), 0, 0);
                    btnOpenInChrome.setLayoutParams(ocP);
                    btnOpenInChrome.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            openWhatsAppIntelInBrowser(activity, cleanLocal);
                        }
                    });
                    errorContainer.addView(btnOpenInChrome);
                    root.addView(errorContainer);

                    // 4. In-App Tactical WebView
                    final WebView webView = new WebView(activity);
                    LinearLayout.LayoutParams wvP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
                    webView.setLayoutParams(wvP);
                    webView.setBackgroundColor(Color.WHITE);
                    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    WebSettings ws = webView.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(true);
                    ws.setDatabaseEnabled(true);
                    ws.setAllowContentAccess(true);
                    ws.setAllowFileAccess(true);
                    ws.setJavaScriptCanOpenWindowsAutomatically(true);
                    ws.setSupportMultipleWindows(true);
                    ws.setSupportZoom(true);
                    ws.setBuiltInZoomControls(true);
                    ws.setDisplayZoomControls(false);
                    ws.setUseWideViewPort(true);
                    ws.setLoadWithOverviewMode(true);
                    ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    ws.setCacheMode(WebSettings.LOAD_DEFAULT);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
                    }
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true);

                    webView.setWebChromeClient(new WebChromeClient() {
                        @Override
                        public void onProgressChanged(WebView view, int newProgress) {
                            if (newProgress >= 100) {
                                progressBar.setVisibility(View.GONE);
                            } else {
                                progressBar.setVisibility(View.VISIBLE);
                                progressBar.setProgress(newProgress);
                            }
                        }
                    });

                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            progressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                            Log.w(TAG, "CheckLeaked SSL error ignored: " + error);
                            handler.proceed();
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                            if (request != null && request.getUrl() != null) {
                                String url = request.getUrl().toString();
                                if (!url.contains("checkleaked.cc")) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        activity.startActivity(intent);
                                        return true;
                                    } catch (Throwable ignored) {}
                                }
                            }
                            return false;
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                            if (url != null && !url.contains("checkleaked.cc")) {
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    activity.startActivity(intent);
                                    return true;
                                } catch (Throwable ignored) {}
                            }
                            return false;
                        }

                        @Override
                        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                            super.onReceivedError(view, errorCode, description, failingUrl);
                            Log.w(TAG, "CheckLeaked WebView error: " + description + " for " + failingUrl);
                            progressBar.setVisibility(View.GONE);
                            errorContainer.setVisibility(View.VISIBLE);
                        }
                    });

                    root.addView(webView);

                    // 5. Tactical Bottom Toolbar (Reload, External Browser Fallback, wa.me)
                    LinearLayout bottomBar = new LinearLayout(activity);
                    bottomBar.setOrientation(LinearLayout.HORIZONTAL);
                    bottomBar.setGravity(Gravity.CENTER_VERTICAL);
                    bottomBar.setPadding(dpToPx(activity, 12), dpToPx(activity, 8), dpToPx(activity, 12), dpToPx(activity, 8));
                    bottomBar.setBackgroundColor(0xFF1E293B);

                    Button btnReload = new Button(activity);
                    btnReload.setText("🔄 SEMULA");
                    btnReload.setTextColor(0xFFE2E8F0);
                    btnReload.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    GradientDrawable rBg = new GradientDrawable();
                    rBg.setColor(0xFF334155);
                    rBg.setCornerRadius(dpToPx(activity, 6));
                    btnReload.setBackground(rBg);
                    LinearLayout.LayoutParams rP = new LinearLayout.LayoutParams(0, dpToPx(activity, 34), 1.0f);
                    btnReload.setLayoutParams(rP);
                    btnReload.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            errorContainer.setVisibility(View.GONE);
                            webView.reload();
                        }
                    });
                    bottomBar.addView(btnReload);

                    Button btnBrowser = new Button(activity);
                    btnBrowser.setText("🌐 BUKA CHROME");
                    btnBrowser.setTextColor(0xFF38BDF8);
                    btnBrowser.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    btnBrowser.setBackground(rBg);
                    LinearLayout.LayoutParams bP = new LinearLayout.LayoutParams(0, dpToPx(activity, 34), 1.3f);
                    bP.setMargins(dpToPx(activity, 6), 0, 0, 0);
                    btnBrowser.setLayoutParams(bP);
                    btnBrowser.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            openWhatsAppIntelInBrowser(activity, cleanLocal);
                        }
                    });
                    bottomBar.addView(btnBrowser);

                    Button btnWaDirect = new Button(activity);
                    btnWaDirect.setText("💬 WA.ME");
                    btnWaDirect.setTextColor(0xFF34D399);
                    btnWaDirect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    btnWaDirect.setBackground(rBg);
                    LinearLayout.LayoutParams wP = new LinearLayout.LayoutParams(0, dpToPx(activity, 34), 1.0f);
                    wP.setMargins(dpToPx(activity, 6), 0, 0, 0);
                    btnWaDirect.setLayoutParams(wP);
                    btnWaDirect.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String waDigits = intlNumber.replace("+", "");
                            try {
                                Intent waIt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waDigits));
                                waIt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(waIt);
                            } catch (Throwable t) {
                                Toast.makeText(activity, "Gagal membuka WhatsApp.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    bottomBar.addView(btnWaDirect);

                    root.addView(bottomBar);

                    btnClose.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                webView.stopLoading();
                                webView.destroy();
                            } catch (Throwable ignored) {}
                            dialog.dismiss();
                        }
                    });

                    dialog.setContentView(root);
                    dialog.setCancelable(true);
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface d) {
                            try {
                                webView.stopLoading();
                                webView.destroy();
                            } catch (Throwable ignored) {}
                        }
                    });

                    dialog.show();

                    Window win = dialog.getWindow();
                    if (win != null) {
                        GradientDrawable dWinBg = new GradientDrawable();
                        dWinBg.setColor(0xFF0F172A);
                        dWinBg.setCornerRadius(dpToPx(activity, 14));
                        win.setBackgroundDrawable(dWinBg);

                        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                        lp.copyFrom(win.getAttributes());
                        lp.width = (int) (dm.widthPixels * 0.96);
                        lp.height = (int) (dm.heightPixels * 0.90);
                        lp.gravity = Gravity.CENTER;
                        win.setAttributes(lp);
                    }

                    webView.loadUrl(checkLeakedUrl);
                    Toast.makeText(activity, "📸 Nombor [" + intlNumber + "] telah disalin ke papan keratan!", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Log.e(TAG, "showWhatsAppIntelDialog error: " + t.getMessage(), t);
                    openWhatsAppIntelInBrowser(activity, rawNumber);
                }
            }
        });
    }

    public static void openWhatsAppIntelInBrowser(final Context context, final String phone) {
        if (context == null) return;
        try {
            String intl = normalizeToIntl(phone);
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && !intl.isEmpty()) {
                    cm.setPrimaryClip(ClipData.newPlainText("WhatsApp Number", intl));
                }
            } catch (Throwable ignored) {}
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.checkleaked.cc/"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, "📸 Membuka CheckLeaked di pelayar. Nombor [" + intl + "] telah disalin!", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
    }

    private static String escapeTelegramHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static boolean sendCommunityReportToTelegram(String phone, String suspect, String category, String bank, String notes, int semakCount, int reportCount) {
        try {
            String token = "8832870484:AAGk4_QhZCXngMelWj06U7aVwHXStGmjB9c";
            String adminChatId = "1543214442";

            StringBuilder sb = new StringBuilder();
            sb.append("🚨 <b>LAPORAN SCAMMER KOMUNITI KANTOI</b>\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📱 <b>Nombor Scammer:</b> <code>").append(escapeTelegramHtml(phone)).append("</code>\n");
            sb.append("🏷️ <b>Kategori:</b> ").append(escapeTelegramHtml(category)).append("\n");
            if (suspect != null && !suspect.isEmpty() && !suspect.equals("N/A") && !suspect.contains("Suspek Scammer")) {
                sb.append("👤 <b>Nama Suspek / Agensi:</b> ").append(escapeTelegramHtml(suspect)).append("\n");
            }
            if (bank != null && !bank.isEmpty() && !bank.equals("N/A") && !bank.contains("SemakMule")) {
                sb.append("🏦 <b>Bank Keldai:</b> <code>").append(escapeTelegramHtml(bank)).append("</code>\n");
            }
            if (notes != null && !notes.isEmpty() && !notes.contains("Laporan Komuniti")) {
                sb.append("📝 <b>Keterangan Kes:</b>\n<i>\"").append(escapeTelegramHtml(notes)).append("\"</i>\n");
            }
            if (reportCount > 0 || semakCount > 0) {
                sb.append("🔍 <b>Status SemakMule:</b> ").append(reportCount).append(" Laporan Polis • ").append(semakCount).append(" Carian\n");
            } else {
                sb.append("🔍 <b>Status SemakMule:</b> Tiada rekod rasmi dijumpai / Belum dilaporkan\n");
            }
            sb.append("⏱️ <b>Masa:</b> ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n");
            sb.append("🛡️ <b>ID Aplikasi:</b> KANTOI Mobile v1.11.9\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("<i>ℹ️ Dihantar automatik oleh pengguna KANTOI (Tanpa akaun Telegram).</i>");

            JSONObject payload = new JSONObject();
            payload.put("chat_id", adminChatId);
            payload.put("text", sb.toString());
            payload.put("parse_mode", "HTML");

            URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            trustAllHosts(conn);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "KantoiMobile/1.11.9");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int respCode = conn.getResponseCode();
            Log.i(TAG, "sendCommunityReportToTelegram response code: " + respCode);
            return respCode == 200;
        } catch (Throwable t) {
            Log.e(TAG, "sendCommunityReportToTelegram error: " + t.getMessage(), t);
            return false;
        }
    }

    public void showCommunitySuccessDialog(final Activity activity, final String phone, final String category) {
        if (activity == null || activity.isFinishing()) return;
        try {
            final Context context = activity;
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dpToPx(context, 20), dpToPx(context, 22), dpToPx(context, 20), dpToPx(context, 20));
            root.setBackgroundColor(0xFF0F172A);

            TextView tvIcon = new TextView(context);
            tvIcon.setText("🎉 🛡️");
            tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
            tvIcon.setGravity(Gravity.CENTER);
            root.addView(tvIcon);

            TextView tvTitle = new TextView(context);
            tvTitle.setText("TERIMA KASIH WIRA KOMUNITI!");
            tvTitle.setTextColor(0xFF22C55E); // Emerald Green
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setGravity(Gravity.CENTER);
            tvTitle.setPadding(0, dpToPx(context, 10), 0, 0);
            root.addView(tvTitle);

            TextView tvMsg = new TextView(context);
            tvMsg.setText("Laporan anda telah selamat dihantar terus kepada Pentadbir Komuniti KANTOI.\n\n"
                    + "🔒 Nombor [" + formatPhoneNumber(phone) + "] telah disekat serta-merta pada telefon anda.\n\n"
                    + "🏷️ Kategori: " + category + "\n\n"
                    + "Pentadbir kami akan mengesahkan laporan ini dan menyiarkannya ke saluran komuniti @kantoiradar untuk melindungi jutaan rakyat Malaysia.");
            tvMsg.setTextColor(0xFFE2E8F0);
            tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvMsg.setLineSpacing(dpToPx(context, 3), 1.0f);
            tvMsg.setGravity(Gravity.CENTER);
            tvMsg.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 18));
            root.addView(tvMsg);

            final AlertDialog sDialog = builder.create();

            Button btnDone = new Button(context);
            btnDone.setText("👍 SELESAI & TUTUP");
            btnDone.setTextColor(Color.WHITE);
            btnDone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btnDone.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable dBg = new GradientDrawable();
            dBg.setColor(0xFF059669);
            dBg.setCornerRadius(dpToPx(context, 8));
            btnDone.setBackground(dBg);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 48));
            btnDone.setLayoutParams(btnLp);
            btnDone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sDialog.dismiss();
                }
            });
            root.addView(btnDone);

            sDialog.setView(root);
            sDialog.show();

            Window win = sDialog.getWindow();
            if (win != null) {
                GradientDrawable winBg = new GradientDrawable();
                winBg.setColor(0xFF0F172A);
                winBg.setCornerRadius(dpToPx(context, 16));
                win.setBackgroundDrawable(winBg);
            }
        } catch (Throwable t) {
            Log.e(TAG, "showCommunitySuccessDialog error: " + t.getMessage(), t);
        }
    }

    private void updateCategoryChips(Context context, List<TextView> chipViews, String[] categories, String currentSelection) {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = chipViews.get(i);
            String cat = categories[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dpToPx(context, 20));
            if (cat.equalsIgnoreCase(currentSelection) || (currentSelection != null && currentSelection.contains(cat))) {
                bg.setColor(0xFFDC2626); // Red 600
                bg.setStroke(dpToPx(context, 2), 0xFFFCA5A5);
                chip.setTextColor(Color.WHITE);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                bg.setColor(0xFF1E293B); // Slate 800
                bg.setStroke(dpToPx(context, 1), 0xFF334155);
                chip.setTextColor(0xFFCBD5E1);
                chip.setTypeface(Typeface.DEFAULT);
            }
            chip.setBackground(bg);
        }
    }

    public void showCommunityReportDialog(final Activity activity, final String initialPhone) {
        showRegisterScammerDialog(activity, initialPhone);
    }

    public void showRegisterScammerDialog(final Activity activity, final String initialPhone) {
        if (activity == null || activity.isFinishing()) return;
        try {
            final Context context = activity;
            final String localTarget = (initialPhone != null) ? normalizeToLocal(initialPhone) : "";
            final ScammerThreat existing = (localTarget.length() >= 8) ? checkThreat(localTarget) : null;
            final boolean isExisting = (existing != null);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 20));
            root.setBackgroundColor(0xFF0F172A);

            // 1. Header Card (Luxurious Slate / Crimson Theme)
            LinearLayout headerCard = new LinearLayout(context);
            headerCard.setOrientation(LinearLayout.VERTICAL);
            headerCard.setPadding(dpToPx(context, 14), dpToPx(context, 14), dpToPx(context, 14), dpToPx(context, 14));
            GradientDrawable hBg = new GradientDrawable();
            hBg.setColor(isExisting ? 0xFF881337 : 0xFF991B1B); // Rich Dark Crimson
            hBg.setCornerRadius(dpToPx(context, 12));
            hBg.setStroke(dpToPx(context, 2), 0xFFE11D48);
            headerCard.setBackground(hBg);

            TextView tvTitle = new TextView(context);
            tvTitle.setText(isExisting ? "🚨 KEMASKINI LAPORAN SCAMMER KOMUNITI" : "🚨 LAPOR SCAMMER KE KOMUNITI KANTOI");
            tvTitle.setTextColor(Color.WHITE);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            headerCard.addView(tvTitle);

            TextView tvSubtitle = new TextView(context);
            tvSubtitle.setText("Bantu lindungi jutaan rakyat Malaysia. Laporan anda dihantar terus kepada Pentadbir Komuniti KANTOI & nombor ini disekat serta-merta pada telefon anda.");
            tvSubtitle.setTextColor(0xFFFECDD3);
            tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvSubtitle.setLineSpacing(dpToPx(context, 2), 1.0f);
            tvSubtitle.setPadding(0, dpToPx(context, 6), 0, 0);
            headerCard.addView(tvSubtitle);

            // Privacy & Zero-Telegram Guarantee Pill
            LinearLayout pillShield = new LinearLayout(context);
            pillShield.setOrientation(LinearLayout.HORIZONTAL);
            pillShield.setPadding(dpToPx(context, 10), dpToPx(context, 5), dpToPx(context, 10), dpToPx(context, 5));
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(0x40000000); // 25% black
            pillBg.setCornerRadius(dpToPx(context, 6));
            pillShield.setBackground(pillBg);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillLp.setMargins(0, dpToPx(context, 8), 0, 0);
            pillShield.setLayoutParams(pillLp);

            TextView tvPill = new TextView(context);
            tvPill.setText("🔒 100% Anonim • Tanpa Akaun Telegram • Enkripsi HTTPS");
            tvPill.setTextColor(0xFFFFFFFF);
            tvPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tvPill.setTypeface(Typeface.DEFAULT_BOLD);
            pillShield.addView(tvPill);
            headerCard.addView(pillShield);

            root.addView(headerCard);

            final SafeContactItem customSafe = (localTarget.length() >= 8) ? getCustomSafeContact(localTarget) : null;
            if (customSafe != null) {
                LinearLayout alertBox = new LinearLayout(context);
                alertBox.setOrientation(LinearLayout.VERTICAL);
                alertBox.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
                GradientDrawable abBg = new GradientDrawable();
                abBg.setColor(0xFF7F1D1D); // Dark Red
                abBg.setCornerRadius(dpToPx(context, 8));
                abBg.setStroke(dpToPx(context, 1), 0xFFEF4444);
                alertBox.setBackground(abBg);
                LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                abLp.setMargins(0, dpToPx(context, 10), 0, 0);
                alertBox.setLayoutParams(abLp);

                TextView tvAb = new TextView(context);
                tvAb.setText("⚠️ PERHATIAN:\nNombor ini kini didaftarkan sebagai RAKAN SELAMAT (" + customSafe.contactName + ").\n\nMelaporkannya sebagai Scammer akan MEMADAM status rakan selamat dan menyenaraihitamkannya!");
                tvAb.setTextColor(Color.WHITE);
                tvAb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvAb.setTypeface(Typeface.DEFAULT_BOLD);
                alertBox.addView(tvAb);
                root.addView(alertBox);
            }

            // 2. Input Nombor Telefon
            TextView lblPhone = new TextView(context);
            lblPhone.setText("📱 NOMBOR TELEFON SCAMMER:");
            lblPhone.setTextColor(0xFF94A3B8);
            lblPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lblPhone.setTypeface(Typeface.DEFAULT_BOLD);
            lblPhone.setPadding(0, dpToPx(context, 14), 0, dpToPx(context, 4));
            root.addView(lblPhone);

            final EditText etPhone = new EditText(context);
            etPhone.setText(localTarget);
            etPhone.setHint("Cth: 0112345678");
            etPhone.setHintTextColor(0xFF64748B);
            etPhone.setTextColor(Color.WHITE);
            etPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            etPhone.setTypeface(Typeface.DEFAULT_BOLD);
            etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
            etPhone.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(0xFF1E293B);
            inputBg.setCornerRadius(dpToPx(context, 8));
            inputBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etPhone.setBackground(inputBg);
            root.addView(etPhone);

            // 3. Category Selector Chips
            TextView lblCategory = new TextView(context);
            lblCategory.setText("🏷️ PILIH KATEGORI PENIPUAN (KLIK UNTUK PILIH):");
            lblCategory.setTextColor(0xFF94A3B8);
            lblCategory.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lblCategory.setTypeface(Typeface.DEFAULT_BOLD);
            lblCategory.setPadding(0, dpToPx(context, 14), 0, dpToPx(context, 6));
            root.addView(lblCategory);

            final String[] categories = new String[]{
                "Macau Scam / Polis / LHDN",
                "Pinjaman Wang / Ah Long",
                "Pelaburan Palsu / Kripto",
                "Parcel Kastam / Kurier",
                "Task E-Dagang / Shopee",
                "Cabutan Bertuah / Hadiah",
                "Phishing SMS / TAC Kod",
                "Ugutan / Gangguan / Lain-lain"
            };
            final String[] categoryIcons = new String[]{
                "👮", "💰", "📈", "📦", "🛒", "🎁", "🔑", "⚠️"
            };

            final String[] selectedCategory = new String[]{
                (isExisting && existing != null && existing.riskTag != null && !existing.riskTag.isEmpty() && !existing.riskTag.equals("MANUAL_REPORT"))
                    ? existing.riskTag : "Macau Scam / Polis / LHDN"
            };

            final List<TextView> chipViews = new ArrayList<>();
            LinearLayout catContainer = new LinearLayout(context);
            catContainer.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < categories.length; i += 2) {
                LinearLayout chipRow = new LinearLayout(context);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, dpToPx(context, 3), 0, dpToPx(context, 3));
                chipRow.setLayoutParams(rowLp);

                for (int j = 0; j < 2 && (i + j) < categories.length; j++) {
                    final int idx = i + j;
                    final String cat = categories[idx];
                    final String icon = categoryIcons[idx];

                    final TextView chip = new TextView(context);
                    chip.setText(icon + " " + cat);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    chip.setGravity(Gravity.CENTER);
                    chip.setPadding(dpToPx(context, 6), dpToPx(context, 9), dpToPx(context, 6), dpToPx(context, 9));

                    LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    if (j > 0) {
                        chipLp.setMargins(dpToPx(context, 6), 0, 0, 0);
                    }
                    chip.setLayoutParams(chipLp);
                    chip.setClickable(true);

                    chipViews.add(chip);

                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedCategory[0] = cat;
                            updateCategoryChips(context, chipViews, categories, selectedCategory[0]);
                        }
                    });

                    chipRow.addView(chip);
                }
                catContainer.addView(chipRow);
            }
            updateCategoryChips(context, chipViews, categories, selectedCategory[0]);
            root.addView(catContainer);

            // 4. Input Nama Suspek / Agensi (Pilihan)
            TextView lblSuspect = new TextView(context);
            lblSuspect.setText("👤 NAMA SUSPEK / AGENSI YANG DIDAFTAR (PILIHAN):");
            lblSuspect.setTextColor(0xFF94A3B8);
            lblSuspect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lblSuspect.setTypeface(Typeface.DEFAULT_BOLD);
            lblSuspect.setPadding(0, dpToPx(context, 14), 0, dpToPx(context, 4));
            root.addView(lblSuspect);

            final EditText etSuspect = new EditText(context);
            etSuspect.setText(isExisting && existing.suspectName != null && !existing.suspectName.equals("Suspek Scammer") ? existing.suspectName : "");
            etSuspect.setHint("Cth: Sarjan Wong / Pegawai LHDN / Bank Islam");
            etSuspect.setHintTextColor(0xFF64748B);
            etSuspect.setTextColor(Color.WHITE);
            etSuspect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            etSuspect.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable suspectBg = new GradientDrawable();
            suspectBg.setColor(0xFF1E293B);
            suspectBg.setCornerRadius(dpToPx(context, 8));
            suspectBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etSuspect.setBackground(suspectBg);
            root.addView(etSuspect);

            // 5. Input Akaun Bank Keldai (Pilihan)
            TextView lblBank = new TextView(context);
            lblBank.setText("🏦 AKAUN BANK KELDAI (JIKA ADA DIMINTA):");
            lblBank.setTextColor(0xFF94A3B8);
            lblBank.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lblBank.setTypeface(Typeface.DEFAULT_BOLD);
            lblBank.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 4));
            root.addView(lblBank);

            final EditText etBank = new EditText(context);
            String existingBank = (isExisting && existing.bankAccounts != null && !existing.bankAccounts.equals("N/A") && !existing.bankAccounts.contains("SemakMule")) ? existing.bankAccounts : "";
            etBank.setText(existingBank);
            etBank.setHint("Cth: Maybank 164287654321 / CIMB 7012345678");
            etBank.setHintTextColor(0xFF64748B);
            etBank.setTextColor(Color.WHITE);
            etBank.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            etBank.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable bankBg = new GradientDrawable();
            bankBg.setColor(0xFF1E293B);
            bankBg.setCornerRadius(dpToPx(context, 8));
            bankBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etBank.setBackground(bankBg);
            root.addView(etBank);

            // 6. Input Keterangan Kes & Modus Operandi
            TextView lblNotes = new TextView(context);
            lblNotes.setText("📝 KETERANGAN KES & MODUS OPERANDI (PENTING):");
            lblNotes.setTextColor(0xFF94A3B8);
            lblNotes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lblNotes.setTypeface(Typeface.DEFAULT_BOLD);
            lblNotes.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 2));
            root.addView(lblNotes);

            TextView lblNotesSub = new TextView(context);
            lblNotesSub.setText("Ceritakan serba sedikit bagaimana suspek cuba memperdaya anda.");
            lblNotesSub.setTextColor(0xFF64748B);
            lblNotesSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            lblNotesSub.setPadding(0, 0, 0, dpToPx(context, 4));
            root.addView(lblNotesSub);

            final EditText etNotes = new EditText(context);
            String existingNotes = (isExisting && existing.evidenceLinks != null && !existing.evidenceLinks.equals("N/A") && !existing.evidenceLinks.contains("semakmule")) ? existing.evidenceLinks : "";
            etNotes.setText(existingNotes);
            etNotes.setHint("Cth: Hubungi dakwa ada saman mahkamah kerana cukai tertunggak, paksa pindahkan simpanan ke akaun audit khas...");
            etNotes.setHintTextColor(0xFF64748B);
            etNotes.setTextColor(Color.WHITE);
            etNotes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            etNotes.setMinLines(3);
            etNotes.setMaxLines(5);
            etNotes.setGravity(Gravity.TOP | Gravity.START);
            etNotes.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable notesBg = new GradientDrawable();
            notesBg.setColor(0xFF1E293B);
            notesBg.setCornerRadius(dpToPx(context, 8));
            notesBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etNotes.setBackground(notesBg);
            root.addView(etNotes);

            // 7. Butang Semak SemakMule CCID PDRM
            Button btnCheckSemak = new Button(context);
            btnCheckSemak.setText("🔍 SEMAK REKOD DI SEMAKMULE PDRM CCID");
            btnCheckSemak.setTextColor(Color.WHITE);
            btnCheckSemak.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            btnCheckSemak.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable btnCheckBg = new GradientDrawable();
            btnCheckBg.setColor(0xFF0284C7);
            btnCheckBg.setCornerRadius(dpToPx(context, 8));
            btnCheckSemak.setBackground(btnCheckBg);
            LinearLayout.LayoutParams btnCheckParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44));
            btnCheckParams.setMargins(0, dpToPx(context, 12), 0, 0);
            btnCheckSemak.setLayoutParams(btnCheckParams);
            root.addView(btnCheckSemak);

            // Kotak Keputusan SemakMule
            final LinearLayout resultCard = new LinearLayout(context);
            resultCard.setOrientation(LinearLayout.VERTICAL);
            resultCard.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            final GradientDrawable resBg = new GradientDrawable();
            resBg.setColor(0xFF1E293B);
            resBg.setCornerRadius(dpToPx(context, 8));
            resBg.setStroke(dpToPx(context, 1), 0xFF334155);
            resultCard.setBackground(resBg);
            LinearLayout.LayoutParams resParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            resParams.setMargins(0, dpToPx(context, 8), 0, 0);
            resultCard.setLayoutParams(resParams);

            final TextView tvResult = new TextView(context);
            tvResult.setText("👉 Tekan butang biru di atas untuk menyemak rekod rasmi polis SemakMule.");
            tvResult.setTextColor(0xFF94A3B8);
            tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            resultCard.addView(tvResult);
            root.addView(resultCard);

            final int[] semakCounts = new int[]{-1, -1};

            if (localTarget.length() >= 8) {
                SemakMuleData cachedSm = sSemakMuleMap.get(localTarget);
                if (cachedSm != null && cachedSm.searchCount > 0) {
                    semakCounts[0] = cachedSm.searchCount;
                    semakCounts[1] = cachedSm.reportCount;
                    if (cachedSm.reportCount > 0) {
                        resBg.setColor(0xFF7F1D1D);
                        resBg.setStroke(dpToPx(context, 2), 0xFFEF4444);
                        tvResult.setText("🚨 DISAHKAN PDRM CCID!\n• " + cachedSm.reportCount + " Laporan Polis Rasmi Ditemui\n• Telah dicari " + cachedSm.searchCount + " kali di SemakMule.");
                        tvResult.setTextColor(0xFFFF5252);
                    } else {
                        resBg.setColor(0xFF78350F);
                        resBg.setStroke(dpToPx(context, 2), 0xFFF59E0B);
                        tvResult.setText("⚠️ NOMBOR MENCURIGAKAN!\n• Telah dicari " + cachedSm.searchCount + " kali oleh orang ramai di SemakMule.");
                        tvResult.setTextColor(0xFFFFD54F);
                    }
                }
            }

            btnCheckSemak.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String raw = etPhone.getText().toString();
                    final String localD = normalizeToLocal(raw);
                    if (localD.length() < 8) {
                        Toast.makeText(context, "⚠️ Sila masukkan nombor telefon yang sah.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tvResult.setText("⏳ Sedang menyemak rekod dengan pelayan SemakMule PDRM...");
                    tvResult.setTextColor(0xFF80D8FF);
                    resBg.setColor(0xFF1E293B);
                    resBg.setStroke(dpToPx(context, 1), 0xFF0284C7);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final SemakMuleData data = querySemakMuleNetwork(localD);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (data != null && data.searchCount > 0) {
                                        semakCounts[0] = data.searchCount;
                                        semakCounts[1] = data.reportCount;
                                        sSemakMuleMap.put(localD, data);
                                        if (data.reportCount > 0) {
                                            resBg.setColor(0xFF7F1D1D);
                                            resBg.setStroke(dpToPx(context, 2), 0xFFEF4444);
                                            tvResult.setText("🚨 DISAHKAN PDRM CCID!\n• " + data.reportCount + " Laporan Polis Rasmi Ditemui\n• Telah dicari " + data.searchCount + " kali di SemakMule.");
                                            tvResult.setTextColor(0xFFFF5252);
                                        } else {
                                            resBg.setColor(0xFF78350F);
                                            resBg.setStroke(dpToPx(context, 2), 0xFFF59E0B);
                                            tvResult.setText("⚠️ REKOD CARIAN SEMAKMULE:\n• Nombor ini telah dicari " + data.searchCount + " kali di SemakMule.");
                                            tvResult.setTextColor(0xFFFFD54F);
                                        }
                                    } else if (data != null) {
                                        resBg.setColor(0xFF14532D);
                                        resBg.setStroke(dpToPx(context, 2), 0xFF22C55E);
                                        tvResult.setText("✅ TIADA REKOD DI SEMAKMULE\n• Belum pernah dilaporkan di SemakMule PDRM. Anda boleh teruskan laporan.");
                                        tvResult.setTextColor(0xFF86EFAC);
                                    } else {
                                        resBg.setColor(0xFF334155);
                                        resBg.setStroke(dpToPx(context, 1), 0xFF64748B);
                                        tvResult.setText("⚠️ Sambungan ke SemakMule terhad. Laporan tetap boleh dihantar.");
                                        tvResult.setTextColor(0xFFCBD5E1);
                                    }
                                }
                            });
                        }
                    }).start();
                }
            });

            final AlertDialog dialog = builder.create();

            // 8. Butang Hantar Laporan Gergasi
            final Button btnSave = new Button(context);
            btnSave.setText(isExisting ? "🚀 KEMASKINI & HANTAR KE KOMUNITI" : "🚀 HANTAR LAPORAN KE KOMUNITI & SEKAT");
            btnSave.setTextColor(Color.WHITE);
            btnSave.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btnSave.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setColor(0xFFDC2626); // Crimson Red
            saveBg.setCornerRadius(dpToPx(context, 10));
            saveBg.setStroke(dpToPx(context, 2), 0xFFF87171);
            btnSave.setBackground(saveBg);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 52));
            saveParams.setMargins(0, dpToPx(context, 16), 0, 0);
            btnSave.setLayoutParams(saveParams);

            btnSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String raw = etPhone.getText().toString();
                    final String localD = normalizeToLocal(raw);
                    if (localD.length() < 8) {
                        Toast.makeText(context, "⚠️ Sila masukkan nombor telefon yang sah.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String suspect = etSuspect.getText().toString().trim();
                    if (suspect.isEmpty()) {
                        suspect = "Suspek Scammer (" + selectedCategory[0] + ")";
                    }
                    String bank = etBank.getText().toString().trim();
                    if (bank.isEmpty()) {
                        bank = "N/A";
                    }
                    String notes = etNotes.getText().toString().trim();
                    if (notes.isEmpty()) {
                        notes = "Laporan Komuniti KANTOI: " + selectedCategory[0];
                    }

                    // 1. Immediately save locally into kantoi_threats.db
                    boolean ok = saveThreatLocal(localD, suspect, "N/A", bank, "N/A", notes, selectedCategory[0], semakCounts[0], semakCounts[1]);

                    // 2. Disable button to prevent double-click
                    btnSave.setEnabled(false);
                    btnSave.setText("⏳ Sedang Menghantar Laporan ke Pentadbir...");

                    final String finalSuspect = suspect;
                    final String finalBank = bank;
                    final String finalNotes = notes;
                    final String finalCat = selectedCategory[0];

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sendCommunityReportToTelegram(localD, finalSuspect, finalCat, finalBank, finalNotes, semakCounts[0], semakCounts[1]);

                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        dialog.dismiss();
                                    } catch (Throwable ignored) {}

                                    showCommunitySuccessDialog(activity, localD, finalCat);

                                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Intent intent = activity.getIntent();
                                                if (intent != null) {
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                                    activity.finish();
                                                    activity.overridePendingTransition(0, 0);
                                                    activity.startActivity(intent);
                                                    activity.overridePendingTransition(0, 0);
                                                } else {
                                                    activity.recreate();
                                                }
                                            } catch (Throwable t) {
                                                try { activity.recreate(); } catch (Throwable ignored) {}
                                            }
                                        }
                                    }, 400);
                                }
                            });
                        }
                    }).start();
                }
            });
            root.addView(btnSave);

            // 9. Butang Padam (Jika nombor ini sudah ada dalam DB)
            if (isExisting) {
                Button btnDelete = new Button(context);
                btnDelete.setText("🗑️ PADAM DARI REKOD SCAMMER");
                btnDelete.setTextColor(Color.WHITE);
                btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                GradientDrawable delBg = new GradientDrawable();
                delBg.setColor(0xFF334155);
                delBg.setCornerRadius(dpToPx(context, 8));
                delBg.setStroke(dpToPx(context, 1), 0xFF475569);
                btnDelete.setBackground(delBg);
                LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44));
                delParams.setMargins(0, dpToPx(context, 8), 0, 0);
                btnDelete.setLayoutParams(delParams);

                btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String raw = etPhone.getText().toString();
                        final String localD = normalizeToLocal(raw);
                        boolean deleted = deleteThreatLocal(localD);
                        if (deleted) {
                            Toast.makeText(context, "🗑️ Rekod dipadam! Nombor [" + localD + "] kembali kepada status normal.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Intent intent = activity.getIntent();
                                        if (intent != null) {
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                            activity.finish();
                                            activity.overridePendingTransition(0, 0);
                                            activity.startActivity(intent);
                                            activity.overridePendingTransition(0, 0);
                                        } else {
                                            activity.recreate();
                                        }
                                    } catch (Throwable t) {
                                        try { activity.recreate(); } catch (Throwable ignored) {}
                                    }
                                }
                            });
                        }
                    }
                });
                root.addView(btnDelete);
            }

            // 9b. Anjuran Lapor ke Telegram Bot @crowdcallerbot (Teks & Bergambar)
            LinearLayout tgPrompt = new LinearLayout(context);
            tgPrompt.setOrientation(LinearLayout.VERTICAL);
            tgPrompt.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable tgBg = new GradientDrawable();
            tgBg.setColor(0xFF1E293B);
            tgBg.setCornerRadius(dpToPx(context, 8));
            tgBg.setStroke(dpToPx(context, 1), 0xFF0284C7);
            tgPrompt.setBackground(tgBg);
            LinearLayout.LayoutParams tgLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tgLp.setMargins(0, dpToPx(context, 10), 0, 0);
            tgPrompt.setLayoutParams(tgLp);

            TextView tvTgHint = new TextView(context);
            tvTgHint.setText("💡 Ada Bukti Gambar / Senarai Banyak?\nAnda juga boleh laporkan terus kepada bot rasmi Telegram @crowdcallerbot. Hantar sebarang nombor, akaun bank keldai, atau tangkap layar (screenshot) perbualan/resit scammer untuk diproses oleh pentadbir.");
            tvTgHint.setTextColor(0xFF38BDF8);
            tvTgHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvTgHint.setLineSpacing(dpToPx(context, 2), 1.0f);
            tgPrompt.addView(tvTgHint);

            Button btnOpenBot = new Button(context);
            btnOpenBot.setText("💬 BUKA BOT TELEGRAM @CROWDCALLERBOT");
            btnOpenBot.setTextColor(Color.WHITE);
            btnOpenBot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnOpenBot.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable obBg = new GradientDrawable();
            obBg.setColor(0xFF0284C7);
            obBg.setCornerRadius(dpToPx(context, 6));
            btnOpenBot.setBackground(obBg);
            LinearLayout.LayoutParams obParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 38));
            obParams.setMargins(0, dpToPx(context, 6), 0, 0);
            btnOpenBot.setLayoutParams(obParams);
            btnOpenBot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent tgIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/crowdcallerbot"));
                        context.startActivity(tgIntent);
                    } catch (Throwable ignored) {}
                }
            });
            tgPrompt.addView(btnOpenBot);
            root.addView(tgPrompt);

            // 10. Butang Batal
            Button btnCancel = new Button(context);
            btnCancel.setText("✕ BATAL");
            btnCancel.setTextColor(0xFF94A3B8);
            btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setColor(0xFF1E293B);
            cancelBg.setCornerRadius(dpToPx(context, 8));
            btnCancel.setBackground(cancelBg);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44));
            cancelParams.setMargins(0, dpToPx(context, 8), 0, 0);
            btnCancel.setLayoutParams(cancelParams);

            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            root.addView(btnCancel);

            scrollView.addView(root);
            dialog.setView(scrollView);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            Window win = dialog.getWindow();
            if (win != null) {
                GradientDrawable dWinBg = new GradientDrawable();
                dWinBg.setColor(0xFF0F172A);
                dWinBg.setCornerRadius(dpToPx(context, 16));
                win.setBackgroundDrawable(dWinBg);

                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(win.getAttributes());
                lp.width = (int) (dm.widthPixels * 0.94);
                lp.height = (int) (dm.heightPixels * 0.88);
                lp.gravity = Gravity.CENTER;
                win.setAttributes(lp);
            }
        } catch (Throwable t) {
            Log.e(TAG, "showRegisterScammerDialog error: " + t.getMessage(), t);
        }
    }

    public void showRegisterFriendDialog(final Activity activity, final String initialPhone) {
        if (activity == null || activity.isFinishing()) return;
        try {
            final Context context = activity;
            final String localTarget = (initialPhone != null) ? normalizeToLocal(initialPhone) : "";
            final SafeContactItem existingFriend = (localTarget.length() >= 8) ? getCustomSafeContact(localTarget) : null;
            final boolean isExisting = (existingFriend != null);
            final ScammerThreat existingThreat = (localTarget.length() >= 8) ? checkThreat(localTarget) : null;

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 20));
            root.setBackgroundColor(0xFF0F172A);

            // 1. Header Card
            LinearLayout headerCard = new LinearLayout(context);
            headerCard.setOrientation(LinearLayout.VERTICAL);
            headerCard.setPadding(dpToPx(context, 14), dpToPx(context, 12), dpToPx(context, 14), dpToPx(context, 12));
            GradientDrawable hBg = new GradientDrawable();
            hBg.setColor(0xFF047857); // Emerald 700
            hBg.setCornerRadius(dpToPx(context, 10));
            headerCard.setBackground(hBg);

            TextView tvTitle = new TextView(context);
            tvTitle.setText(isExisting ? "👥 KEMASKINI RAKAN / KENALAN SELAMAT" : "👥 DAFTAR RAKAN / KENALAN SELAMAT");
            tvTitle.setTextColor(Color.WHITE);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            headerCard.addView(tvTitle);

            TextView tvSubtitle = new TextView(context);
            tvSubtitle.setText("Daftarkan nombor ke Senarai Putih KANTOI. Panggilan dari nombor ini disahkan bersih dan selamat daripada amaran scammer.");
            tvSubtitle.setTextColor(0xFFD1FAE5);
            tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvSubtitle.setPadding(0, dpToPx(context, 4), 0, 0);
            headerCard.addView(tvSubtitle);
            root.addView(headerCard);

            // Warning if previously flagged as threat
            if (existingThreat != null) {
                LinearLayout alertBox = new LinearLayout(context);
                alertBox.setOrientation(LinearLayout.VERTICAL);
                alertBox.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
                GradientDrawable abBg = new GradientDrawable();
                abBg.setColor(0xFF7C2D12); // Deep Amber / Orange
                abBg.setCornerRadius(dpToPx(context, 8));
                abBg.setStroke(dpToPx(context, 1), 0xFFF97316);
                alertBox.setBackground(abBg);
                LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                abLp.setMargins(0, dpToPx(context, 10), 0, 0);
                alertBox.setLayoutParams(abLp);

                TextView tvAb = new TextView(context);
                String suspName = (existingThreat.suspectName != null) ? existingThreat.suspectName : "Suspek Scammer";
                tvAb.setText("⚠️ PERHATIAN:\nNombor ini pada masa ini disenaraikan sebagai SCAMMER (" + suspName + ").\n\nMenyimpannya sebagai Rakan Selamat akan MEMADAM rekod scammer ini secara automatik daripada telefon anda!");
                tvAb.setTextColor(Color.WHITE);
                tvAb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvAb.setTypeface(Typeface.DEFAULT_BOLD);
                alertBox.addView(tvAb);
                root.addView(alertBox);
            }

            // 2. Input Nombor Telefon
            TextView lblPhone = new TextView(context);
            lblPhone.setText("📱 NOMBOR TELEFON:");
            lblPhone.setTextColor(0xFF94A3B8);
            lblPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            lblPhone.setTypeface(Typeface.DEFAULT_BOLD);
            lblPhone.setPadding(0, dpToPx(context, 14), 0, dpToPx(context, 4));
            root.addView(lblPhone);

            final EditText etPhone = new EditText(context);
            etPhone.setText(localTarget);
            etPhone.setHint("Cth: 0112345678");
            etPhone.setHintTextColor(0xFF64748B);
            etPhone.setTextColor(Color.WHITE);
            etPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            etPhone.setTypeface(Typeface.DEFAULT_BOLD);
            etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
            etPhone.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(0xFF1E293B);
            inputBg.setCornerRadius(dpToPx(context, 8));
            inputBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etPhone.setBackground(inputBg);
            root.addView(etPhone);

            // 3. Input Nama Kenalan
            TextView lblName = new TextView(context);
            lblName.setText("👤 NAMA RAKAN / KENALAN:");
            lblName.setTextColor(0xFF94A3B8);
            lblName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            lblName.setTypeface(Typeface.DEFAULT_BOLD);
            lblName.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 4));
            root.addView(lblName);

            String initialName = "";
            if (existingFriend != null && existingFriend.contactName != null) {
                initialName = existingFriend.contactName;
            } else {
                String disp = getContactDisplayName(localTarget);
                if (disp != null) initialName = disp;
            }

            final EditText etName = new EditText(context);
            etName.setText(initialName);
            etName.setHint("Cth: Ahmad Razali / Siti Aishah");
            etName.setHintTextColor(0xFF64748B);
            etName.setTextColor(Color.WHITE);
            etName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            etName.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable nameBg = new GradientDrawable();
            nameBg.setColor(0xFF1E293B);
            nameBg.setCornerRadius(dpToPx(context, 8));
            nameBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etName.setBackground(nameBg);
            root.addView(etName);

            // 4. Kategori Kenalan Selector
            TextView lblCat = new TextView(context);
            lblCat.setText("🏷️ KATEGORI KENALAN:");
            lblCat.setTextColor(0xFF94A3B8);
            lblCat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            lblCat.setTypeface(Typeface.DEFAULT_BOLD);
            lblCat.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 4));
            root.addView(lblCat);

            final String[] categories = new String[]{"Sahabat", "Keluarga", "Kerja", "Perniagaan", "Rider / Kurier", "Lain-lain"};
            final String[] selectedCat = new String[]{(existingFriend != null && existingFriend.category != null) ? existingFriend.category : "Sahabat"};

            final LinearLayout catGrid1 = new LinearLayout(context);
            catGrid1.setOrientation(LinearLayout.HORIZONTAL);
            final LinearLayout catGrid2 = new LinearLayout(context);
            catGrid2.setOrientation(LinearLayout.HORIZONTAL);
            catGrid2.setPadding(0, dpToPx(context, 6), 0, 0);

            final List<Button> catButtons = new ArrayList<Button>();

            for (int i = 0; i < categories.length; i++) {
                final String cat = categories[i];
                final Button btnCat = new Button(context);
                btnCat.setText(cat);
                btnCat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(context, 38), 1.0f);
                lp.setMargins(dpToPx(context, 2), 0, dpToPx(context, 2), 0);
                btnCat.setLayoutParams(lp);

                catButtons.add(btnCat);

                btnCat.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectedCat[0] = cat;
                        updateCatButtonStyles(catButtons, cat, context);
                    }
                });

                if (i < 3) {
                    catGrid1.addView(btnCat);
                } else {
                    catGrid2.addView(btnCat);
                }
            }
            updateCatButtonStyles(catButtons, selectedCat[0], context);

            root.addView(catGrid1);
            root.addView(catGrid2);

            // 5. Input Nota Tambahan
            TextView lblNotes = new TextView(context);
            lblNotes.setText("📝 CATATAN TAMBAHAN (PILIHAN):");
            lblNotes.setTextColor(0xFF94A3B8);
            lblNotes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            lblNotes.setTypeface(Typeface.DEFAULT_BOLD);
            lblNotes.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 4));
            root.addView(lblNotes);

            final EditText etNotes = new EditText(context);
            etNotes.setText(existingFriend != null && existingFriend.notes != null ? existingFriend.notes : "");
            etNotes.setHint("Cth: Rakan sepejabat Bahagian IT");
            etNotes.setHintTextColor(0xFF64748B);
            etNotes.setTextColor(Color.WHITE);
            etNotes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            etNotes.setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10));
            GradientDrawable notesBg = new GradientDrawable();
            notesBg.setColor(0xFF1E293B);
            notesBg.setCornerRadius(dpToPx(context, 8));
            notesBg.setStroke(dpToPx(context, 1), 0xFF334155);
            etNotes.setBackground(notesBg);
            root.addView(etNotes);

            final AlertDialog dialog = builder.create();

            // 6. Butang Simpan Kenalan Selamat
            Button btnSave = new Button(context);
            btnSave.setText(isExisting ? "💾 KEMASKINI RAKAN SELAMAT" : "💾 SIMPAN SEBAGAI RAKAN SELAMAT");
            btnSave.setTextColor(Color.WHITE);
            btnSave.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            btnSave.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setColor(0xFF059669); // Emerald 600
            saveBg.setCornerRadius(dpToPx(context, 8));
            btnSave.setBackground(saveBg);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 50));
            saveParams.setMargins(0, dpToPx(context, 18), 0, 0);
            btnSave.setLayoutParams(saveParams);

            btnSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String raw = etPhone.getText().toString();
                    final String localD = normalizeToLocal(raw);
                    if (localD.length() < 8) {
                        Toast.makeText(context, "⚠️ Nombor telefon tidak sah.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "Rakan KANTOI";
                    }
                    String category = selectedCat[0];
                    String notes = etNotes.getText().toString().trim();

                    boolean ok = saveSafeContact(localD, name, category, notes);
                    if (ok) {
                        Toast.makeText(context, "✅ Berjaya Disimpan! [" + localD + "] kini didaftarkan sebagai Rakan Selamat (" + name + ").", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Intent intent = activity.getIntent();
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                        activity.finish();
                                        activity.overridePendingTransition(0, 0);
                                        activity.startActivity(intent);
                                        activity.overridePendingTransition(0, 0);
                                    } else {
                                        activity.recreate();
                                    }
                                } catch (Throwable t) {
                                    try { activity.recreate(); } catch (Throwable ignored) {}
                                }
                            }
                        });
                    } else {
                        Toast.makeText(context, "❌ Ralat semasa menyimpan rakan selamat.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            root.addView(btnSave);

            // 7. Butang Padam Dari Senarai Rakan (Jika wujud)
            if (isExisting) {
                Button btnDelete = new Button(context);
                btnDelete.setText("🗑️ PADAM DARI SENARAI RAKAN");
                btnDelete.setTextColor(Color.WHITE);
                btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                GradientDrawable delBg = new GradientDrawable();
                delBg.setColor(0xFF475569);
                delBg.setCornerRadius(dpToPx(context, 8));
                btnDelete.setBackground(delBg);
                LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44));
                delParams.setMargins(0, dpToPx(context, 8), 0, 0);
                btnDelete.setLayoutParams(delParams);

                btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String raw = etPhone.getText().toString();
                        final String localD = normalizeToLocal(raw);
                        boolean deleted = deleteSafeContact(localD);
                        if (deleted) {
                            Toast.makeText(context, "🗑️ Rekod dipadam! [" + localD + "] dikeluarkan dari senarai rakan selamat.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Intent intent = activity.getIntent();
                                        if (intent != null) {
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                            activity.finish();
                                            activity.overridePendingTransition(0, 0);
                                            activity.startActivity(intent);
                                            activity.overridePendingTransition(0, 0);
                                        } else {
                                            activity.recreate();
                                        }
                                    } catch (Throwable t) {
                                        try { activity.recreate(); } catch (Throwable ignored) {}
                                    }
                                }
                            });
                        }
                    }
                });
                root.addView(btnDelete);
            }

            // 8. Butang Batal
            Button btnCancel = new Button(context);
            btnCancel.setText("✕ BATAL");
            btnCancel.setTextColor(0xFF94A3B8);
            btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setColor(0xFF1E293B);
            cancelBg.setCornerRadius(dpToPx(context, 8));
            btnCancel.setBackground(cancelBg);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44));
            cancelParams.setMargins(0, dpToPx(context, 8), 0, 0);
            btnCancel.setLayoutParams(cancelParams);

            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            root.addView(btnCancel);

            scrollView.addView(root);
            dialog.setView(scrollView);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            Window win = dialog.getWindow();
            if (win != null) {
                GradientDrawable dWinBg = new GradientDrawable();
                dWinBg.setColor(0xFF0F172A);
                dWinBg.setCornerRadius(dpToPx(context, 16));
                win.setBackgroundDrawable(dWinBg);

                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(win.getAttributes());
                lp.width = (int) (dm.widthPixels * 0.94);
                lp.height = (int) (dm.heightPixels * 0.88);
                lp.gravity = Gravity.CENTER;
                win.setAttributes(lp);
            }
        } catch (Throwable t) {
            Log.e(TAG, "showRegisterFriendDialog error: " + t.getMessage(), t);
        }
    }

    private static void updateCatButtonStyles(List<Button> buttons, String selectedCat, Context context) {
        for (Button btn : buttons) {
            boolean isSelected = btn.getText().toString().equals(selectedCat);
            GradientDrawable gd = new GradientDrawable();
            if (isSelected) {
                gd.setColor(0xFF059669); // Emerald
                gd.setStroke(dpToPx(context, 1), 0xFF34D399);
                btn.setTextColor(Color.WHITE);
                btn.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                gd.setColor(0xFF1E293B);
                gd.setStroke(dpToPx(context, 1), 0xFF334155);
                btn.setTextColor(0xFF94A3B8);
                btn.setTypeface(Typeface.DEFAULT);
            }
            gd.setCornerRadius(dpToPx(context, 6));
            btn.setBackground(gd);
        }
    }

    /**
     * Directly replaces Truecaller's "More info available" card with our collapsible Amaran Scammer Disahkan card.
     * Removes Truecaller's paywall trigger and displays the intelligence card inline right under Call History.
     */
    private static void replaceMoreInfoWithThreatCard(final View triggerView, final Activity activity, final KantoiThreatEngine engine, final ScammerThreat threat) {
        if (triggerView == null || activity == null || activity.isFinishing() || threat == null) return;

        try {
            ViewParent p1 = triggerView.getParent();
            if (!(p1 instanceof ViewGroup)) return;
            ViewGroup vg1 = (ViewGroup) p1;
            ViewGroup targetCard = vg1;

            if (vg1.getParent() instanceof ViewGroup) {
                ViewGroup vg2 = (ViewGroup) vg1.getParent();
                String c2Name = vg2.getClass().getName().toLowerCase();
                if (c2Name.contains("card")) {
                    targetCard = vg2;
                }
            }

            if ("LOWYAT_CARD_PROCESSED".equals(targetCard.getTag()) || targetCard.findViewWithTag("LOWYAT_INLINE_THREAT_CARD") != null) {
                return;
            }
            targetCard.setTag("LOWYAT_CARD_PROCESSED");

            // 1. Remove Truecaller upsell views & listeners completely
            targetCard.removeAllViews();
            targetCard.setOnClickListener(null);
            targetCard.setOnTouchListener(null);
            targetCard.setClickable(false);

            // 2. Build our Collapsible Threat Card in place
            final LinearLayout card = new LinearLayout(activity);
            card.setTag("LOWYAT_INLINE_THREAT_CARD");
            card.setOrientation(LinearLayout.VERTICAL);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(0xFF140D10);
            cardBg.setCornerRadius(dpToPx(activity, 14));
            cardBg.setStroke(dpToPx(activity, 1), 0xFFE53935);
            card.setBackground(cardBg);
            card.setPadding(dpToPx(activity, 2), dpToPx(activity, 2), dpToPx(activity, 2), dpToPx(activity, 2));

            // Header Bar (Collapsed mode)
            final LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dpToPx(activity, 14), dpToPx(activity, 12), dpToPx(activity, 14), dpToPx(activity, 12));

            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(0xFFB71C1C); // Crimson
            headerBg.setCornerRadius(dpToPx(activity, 12));
            header.setBackground(headerBg);

            LinearLayout titleLayout = new LinearLayout(activity);
            titleLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            titleLayout.setLayoutParams(tParams);

            TextView tvHeader = new TextView(activity);
            tvHeader.setText("\u2620\uFE0F AMARAN SCAMMER DISAHKAN");
            tvHeader.setTextColor(Color.WHITE);
            tvHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
            titleLayout.addView(tvHeader);

            TextView tvSub = new TextView(activity);
            tvSub.setText("Suspek: " + threat.suspectName + " • \uD83D\uDEE1\uFE0F " + String.format("%,d", engine.getThreatCount()) + " Rekod Komuniti");
            tvSub.setTextColor(0xFFFFCDD2);
            tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tvSub.setSingleLine(true);
            titleLayout.addView(tvSub);

            header.addView(titleLayout);

            final TextView btnToggle = new TextView(activity);
            btnToggle.setText("\uD83D\uDD3D BUKA INFO");
            btnToggle.setTextColor(Color.WHITE);
            btnToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnToggle.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable toggleBg = new GradientDrawable();
            toggleBg.setColor(0xFF212121);
            toggleBg.setCornerRadius(dpToPx(activity, 12));
            btnToggle.setBackground(toggleBg);
            btnToggle.setPadding(dpToPx(activity, 10), dpToPx(activity, 6), dpToPx(activity, 10), dpToPx(activity, 6));
            header.addView(btnToggle);

            card.addView(header);

            // Expandable Section
            final LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 12));
            body.setVisibility(View.GONE);

            if (threat.bankAccounts != null && !threat.bankAccounts.isEmpty() && !threat.bankAccounts.equals("N/A")) {
                addInfoRow(activity, body, "\uD83C\uDFE6 Akaun Bank Keldai:", threat.bankAccounts, 0xFFFFE082, true);
            }

            final String localPhone = normalizeToLocal(threat.phoneNorm != null ? threat.phoneNorm : threat.allPhones);

            // SemakMule CCID Direct Check Button
            Button btnSemakMule = new Button(activity);
            btnSemakMule.setText("\uD83C\uDFDB\uFE0F SEMAK TERUS DI SEMAKMULE PDRM (AUTO-SALIN)");
            btnSemakMule.setTextColor(Color.WHITE);
            btnSemakMule.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnSemakMule.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable smBg = new GradientDrawable();
            smBg.setColor(0xFFD97706);
            smBg.setCornerRadius(dpToPx(activity, 8));
            btnSemakMule.setBackground(smBg);
            LinearLayout.LayoutParams smP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 40));
            smP.setMargins(0, dpToPx(activity, 4), 0, dpToPx(activity, 6));
            btnSemakMule.setLayoutParams(smP);
            btnSemakMule.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("SemakMule", localPhone);
                    cm.setPrimaryClip(clip);
                    Toast.makeText(activity, "\u2705 Nombor [" + localPhone + "] disalin! Membuka portal SemakMule...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                }
            });
            body.addView(btnSemakMule);

            // Google CSE Social Media Dispute Check Button
            Button btnGoogleCSE = new Button(activity);
            btnGoogleCSE.setText("\uD83C\uDF10 SEMAK MEDIA SOSIAL (GOOGLE CSE)");
            btnGoogleCSE.setTextColor(Color.WHITE);
            btnGoogleCSE.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnGoogleCSE.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable cseBg = new GradientDrawable();
            cseBg.setColor(0xFF1D4ED8); // Deep Blue
            cseBg.setCornerRadius(dpToPx(activity, 8));
            btnGoogleCSE.setBackground(cseBg);
            LinearLayout.LayoutParams cseP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 40));
            cseP.setMargins(0, 0, 0, dpToPx(activity, 6));
            btnGoogleCSE.setLayoutParams(cseP);
            btnGoogleCSE.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleCseSearch(activity, localPhone);
                }
            });
            body.addView(btnGoogleCSE);

            if (threat.address != null && !threat.address.isEmpty() && !threat.address.equals("N/A") && !threat.address.equals("-")) {
                addInfoRow(activity, body, "\uD83D\uDCCD Alamat:", threat.address, 0xFF80CBC4, false);
                Button btnMaps = new Button(activity);
                btnMaps.setText("\uD83E\uDDED BUKA DI GOOGLE MAPS");
                btnMaps.setTextColor(Color.WHITE);
                btnMaps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                btnMaps.setTypeface(Typeface.DEFAULT_BOLD);
                GradientDrawable mapsBg = new GradientDrawable();
                mapsBg.setColor(0xFF00897B);
                mapsBg.setCornerRadius(dpToPx(activity, 8));
                btnMaps.setBackground(mapsBg);
                LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 38));
                mP.setMargins(0, 0, 0, dpToPx(activity, 6));
                btnMaps.setLayoutParams(mP);
                btnMaps.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + URLEncoder.encode(threat.address, "UTF-8")));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                        } catch (Throwable ignored) {}
                    }
                });
                body.addView(btnMaps);
            }

            if (threat.evidenceLinks != null && threat.evidenceLinks.startsWith("http")) {
                final String link = threat.evidenceLinks.split("\\|")[0].trim();
                Button btnLowyat = new Button(activity);
                btnLowyat.setText("\uD83D\uDD17 BUKA LAPORAN KES (LOWYAT.NET)");
                btnLowyat.setTextColor(Color.WHITE);
                btnLowyat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                btnLowyat.setTypeface(Typeface.DEFAULT_BOLD);
                GradientDrawable lwBg = new GradientDrawable();
                lwBg.setColor(0xFF1E88E5);
                lwBg.setCornerRadius(dpToPx(activity, 8));
                btnLowyat.setBackground(lwBg);
                LinearLayout.LayoutParams lP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 38));
                lP.setMargins(0, 0, 0, dpToPx(activity, 6));
                btnLowyat.setLayoutParams(lP);
                btnLowyat.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                        } catch (Throwable ignored) {}
                    }
                });
                body.addView(btnLowyat);
            }

            LinearLayout bRow = new LinearLayout(activity);
            bRow.setOrientation(LinearLayout.HORIZONTAL);
            bRow.setPadding(0, dpToPx(activity, 4), 0, 0);

            Button btnCopy = new Button(activity);
            btnCopy.setText("\uD83D\uDCCB SALIN INFO");
            btnCopy.setTextColor(Color.WHITE);
            btnCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            GradientDrawable copyBg = new GradientDrawable();
            copyBg.setColor(0xFF37474F);
            copyBg.setCornerRadius(dpToPx(activity, 8));
            btnCopy.setBackground(copyBg);
            LinearLayout.LayoutParams cpP = new LinearLayout.LayoutParams(0, dpToPx(activity, 38), 1.0f);
            cpP.setMargins(0, 0, dpToPx(activity, 4), 0);
            btnCopy.setLayoutParams(cpP);
            btnCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String txt = "[AMARAN SCAMMER LOWYAT/SEMAKMULE]\nNama: " + threat.suspectName + "\nTelefon: " + (threat.allPhones != null ? threat.allPhones : threat.phoneNorm) + "\nBank: " + threat.bankAccounts;
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Scammer Info", txt));
                    Toast.makeText(activity, "\u2705 Info disalin ke papan klip!", Toast.LENGTH_SHORT).show();
                }
            });
            bRow.addView(btnCopy);

            Button btnDossier = new Button(activity);
            btnDossier.setText("\uD83D\uDCD6 DOSSIER PENUH");
            btnDossier.setTextColor(Color.WHITE);
            btnDossier.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            GradientDrawable dosBg = new GradientDrawable();
            dosBg.setColor(0xFFC62828);
            dosBg.setCornerRadius(dpToPx(activity, 8));
            btnDossier.setBackground(dosBg);
            LinearLayout.LayoutParams dsP = new LinearLayout.LayoutParams(0, dpToPx(activity, 38), 1.0f);
            dsP.setMargins(dpToPx(activity, 4), 0, 0, 0);
            btnDossier.setLayoutParams(dsP);
            btnDossier.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    engine.showThreatDialog(activity, threat);
                }
            });
            bRow.addView(btnDossier);

            body.addView(bRow);
            card.addView(body);

            // Toggle expand / collapse
            View.OnClickListener toggleListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isExpanded = (body.getVisibility() == View.VISIBLE);
                    body.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                    btnToggle.setText(isExpanded ? "\uD83D\uDD3D BUKA INFO" : "\uD83D\uDD3C KUNCUP");
                }
            };
            header.setClickable(true);
            header.setOnClickListener(toggleListener);
            btnToggle.setClickable(true);
            btnToggle.setOnClickListener(toggleListener);

            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            targetCard.addView(card, lp);
            Log.i(TAG, "Replaced 'More info available' card with inline collapsible threat card successfully!");
        } catch (Throwable t) {
            Log.e(TAG, "replaceMoreInfoWithThreatCard error: " + t.getMessage(), t);
        }
    }

    /**
     * Displays modern dark-themed Threat Intelligence Dossier with SemakMule CCID live verification.
     * This is the exact rock-solid AlertDialog that stays on screen without crashing or bouncing back.
     */
    public void showThreatDialog(final Context context, final ScammerThreat threat) {
        if (context == null || threat == null) return;
        if (context instanceof Activity && ((Activity) context).isFinishing()) return;

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);

            ScrollView scrollView = new ScrollView(context);
            scrollView.setBackgroundColor(0xFF121212);
            scrollView.setPadding(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16));

            LinearLayout rootLayout = new LinearLayout(context);
            rootLayout.setOrientation(LinearLayout.VERTICAL);

            // Banner Header
            LinearLayout headerBox = new LinearLayout(context);
            headerBox.setOrientation(LinearLayout.VERTICAL);
            headerBox.setPadding(dpToPx(context, 14), dpToPx(context, 12), dpToPx(context, 14), dpToPx(context, 12));
            GradientDrawable headerBg = new GradientDrawable();
            headerBg.setColor(0xFFB71C1C);
            headerBg.setCornerRadius(dpToPx(context, 10));
            headerBox.setBackground(headerBg);

            TextView tvHeader = new TextView(context);
            tvHeader.setText("\u2620\uFE0F ANCAMAN SCAMMER DISAHKAN");
            tvHeader.setTextColor(Color.WHITE);
            tvHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
            tvHeader.setGravity(Gravity.CENTER);
            headerBox.addView(tvHeader);

            TextView tvSubHeader = new TextView(context);
            tvSubHeader.setText("SENARAI HITAM LOWYAT & CCID PDRM");
            tvSubHeader.setTextColor(0xFFFFCDD2);
            tvSubHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvSubHeader.setGravity(Gravity.CENTER);
            tvSubHeader.setPadding(0, dpToPx(context, 2), 0, 0);
            headerBox.addView(tvSubHeader);

            TextView tvDbBadge = new TextView(context);
            tvDbBadge.setText("\uD83D\uDEE1\uFE0F Dilindungi oleh " + String.format("%,d", getThreatCount()) + " Rekod Scammer (Klik Info)");
            tvDbBadge.setTextColor(0xFF81D4FA);
            tvDbBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvDbBadge.setTypeface(Typeface.DEFAULT_BOLD);
            tvDbBadge.setGravity(Gravity.CENTER);
            tvDbBadge.setPadding(0, dpToPx(context, 4), 0, 0);
            tvDbBadge.setClickable(true);
            tvDbBadge.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDatabaseInfoDialog(context);
                }
            });
            headerBox.addView(tvDbBadge);

            rootLayout.addView(headerBox);

            // Suspect Identity Card
            LinearLayout cardSuspect = createCard(context, 0xFF1E1E1E, 0xFF37474F);
            addSectionHeader(context, cardSuspect, "\uD83D\uDC64 IDENTITI SUSPEK");
            addInfoRow(context, cardSuspect, "Nama Suspek:", threat.suspectName, Color.WHITE, true);
            if (threat.phoneNorm != null && !threat.phoneNorm.isEmpty()) {
                addInfoRow(context, cardSuspect, "Nombor Telefon:", threat.phoneNorm, 0xFF81D4FA, true);
            }
            if (threat.allPhones != null && !threat.allPhones.isEmpty() && !threat.allPhones.equals(threat.phoneNorm)) {
                addInfoRow(context, cardSuspect, "Semua Nombor Berkaitan:", threat.allPhones, 0xFFB0BEC5, false);
            }
            if (threat.icNumber != null && !threat.icNumber.isEmpty() && !threat.icNumber.equals("N/A")) {
                addInfoRow(context, cardSuspect, "No. KP (IC):", threat.icNumber, Color.WHITE, true);
            }
            rootLayout.addView(cardSuspect);

            // Bank Accounts Card
            if (threat.bankAccounts != null && !threat.bankAccounts.isEmpty() && !threat.bankAccounts.equals("N/A") && !threat.bankAccounts.equals("Sila semak di portal SemakMule")) {
                LinearLayout cardBank = createCard(context, 0xFF2A1B1B, 0xFFC62828);
                addSectionHeader(context, cardBank, "\uD83C\uDFE6 AKAUN BANK KELDAI (MULE)");
                addInfoRow(context, cardBank, "Nombor Akaun & Bank:", threat.bankAccounts, 0xFFFFCC80, true);
                rootLayout.addView(cardBank);
            }

            // SemakMule CCID Verification Card
            final String localPhone = normalizeToLocal(threat.phoneNorm != null ? threat.phoneNorm : threat.allPhones);

            LinearLayout cardSemakMule = createCard(context, 0xFF1A237E, 0xFF3949AB);
            addSectionHeader(context, cardSemakMule, "\uD83C\uDFDB\uFE0F PENGESAHAN RASMI CCID SEMAKMULE PDRM");

            final TextView tvLiveSemakStatus = new TextView(context);
            tvLiveSemakStatus.setTextColor(0xFF80D8FF);
            tvLiveSemakStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvLiveSemakStatus.setPadding(0, 0, 0, dpToPx(context, 8));

            SemakMuleData cachedSm = sSemakMuleMap.get(localPhone);
            if (cachedSm != null && cachedSm.searchCount > 0) {
                if (cachedSm.reportCount > 0) {
                    tvLiveSemakStatus.setText("\u26A0\uFE0F DISAHKAN PDRM CCID!\n\u2022 " + cachedSm.reportCount + " Laporan Polis Rasmi Ditemui\n\u2022 Telah dicari " + cachedSm.searchCount + " kali di portal SemakMule.");
                    tvLiveSemakStatus.setTextColor(0xFFFF5252);
                } else {
                    tvLiveSemakStatus.setText("\uD83D\uDD0D REKOD CARIAN SEMAKMULE:\n\u2022 Nombor ini telah dicari " + cachedSm.searchCount + " kali oleh orang ramai di SemakMule PDRM.");
                    tvLiveSemakStatus.setTextColor(0xFFFFD54F);
                }
            } else {
                tvLiveSemakStatus.setText("\u23F3 Menyemak pangkalan data JSJK CCID untuk [" + localPhone + "]...");
                querySemakMuleApiAsync(context, localPhone, tvLiveSemakStatus);
            }
            cardSemakMule.addView(tvLiveSemakStatus);

            Button btnSemakMulePortal = new Button(context);
            btnSemakMulePortal.setText("\uD83C\uDFDB\uFE0F BUKA SEMAKMULE & AUTO-SALIN (" + localPhone + ")");
            btnSemakMulePortal.setTextColor(Color.WHITE);
            btnSemakMulePortal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnSemakMulePortal.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable btnSmBg = new GradientDrawable();
            btnSmBg.setColor(0xFFF57C00);
            btnSmBg.setCornerRadius(dpToPx(context, 8));
            btnSemakMulePortal.setBackground(btnSmBg);
            LinearLayout.LayoutParams smParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 40));
            smParams.setMargins(0, dpToPx(context, 4), 0, 0);
            btnSemakMulePortal.setLayoutParams(smParams);

            btnSemakMulePortal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("SemakMule Number", localPhone);
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "\u2705 Nombor [" + localPhone + "] disalin! Membuka portal SemakMule...", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Throwable t) {
                        Log.e(TAG, "Error opening SemakMule: " + t.getMessage());
                    }
                }
            });
            cardSemakMule.addView(btnSemakMulePortal);

            // Google CSE Social Media Dispute Check Button
            Button btnCsePortal = new Button(context);
            btnCsePortal.setText("\uD83C\uDF10 SEMAK DISPUTE MEDIA SOSIAL (GOOGLE CSE)");
            btnCsePortal.setTextColor(Color.WHITE);
            btnCsePortal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnCsePortal.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable btnCseBg = new GradientDrawable();
            btnCseBg.setColor(0xFF1D4ED8); // Deep Blue
            btnCseBg.setCornerRadius(dpToPx(context, 8));
            btnCsePortal.setBackground(btnCseBg);
            LinearLayout.LayoutParams cseParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 40));
            cseParams.setMargins(0, dpToPx(context, 6), 0, 0);
            btnCsePortal.setLayoutParams(cseParams);
            btnCsePortal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleCseSearch(context, localPhone);
                }
            });
            cardSemakMule.addView(btnCsePortal);

            rootLayout.addView(cardSemakMule);

            // Evidence, Address, Notes & Google Maps Card
            boolean hasAddress = (threat.address != null && !threat.address.isEmpty() && !threat.address.equals("N/A") && !threat.address.equals("-") && !threat.address.equals("Didaftarkan oleh pengguna"));
            boolean hasEvidence = (threat.evidenceLinks != null && !threat.evidenceLinks.isEmpty() && !threat.evidenceLinks.equals("N/A") && !threat.evidenceLinks.equals("https://semakmule.rmp.gov.my/"));

            if (hasAddress || hasEvidence) {
                LinearLayout cardLinks = createCard(context, 0xFF1E1E1E, 0xFF37474F);
                addSectionHeader(context, cardLinks, "\uD83D\uDD17 SUMBER BUKTI, LOKASI & CATATAN");

                if (hasAddress) {
                    addInfoRow(context, cardLinks, "\uD83D\uDCCD Alamat / Lokasi Premis:", threat.address, 0xFF80CBC4, false);
                    Button btnMap = new Button(context);
                    btnMap.setText("\uD83E\uDDED BUKA DI GOOGLE MAPS");
                    btnMap.setTextColor(Color.WHITE);
                    btnMap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    btnMap.setTypeface(Typeface.DEFAULT_BOLD);
                    GradientDrawable mapBg = new GradientDrawable();
                    mapBg.setColor(0xFF00897B); // Emerald Teal
                    mapBg.setCornerRadius(dpToPx(context, 8));
                    btnMap.setBackground(mapBg);
                    LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity_to_context(context), 42));
                    mapParams.setMargins(0, 0, 0, dpToPx(context, 8));
                    btnMap.setLayoutParams(mapParams);
                    btnMap.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + URLEncoder.encode(threat.address, "UTF-8")));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            } catch (Throwable ignored) {}
                        }
                    });
                    cardLinks.addView(btnMap);
                }

                if (hasEvidence) {
                    if (threat.evidenceLinks.startsWith("http")) {
                        final String link = threat.evidenceLinks.split("\\|")[0].trim();
                        Button btnLowyat = new Button(context);
                        String linkTitle = link.contains("lowyat") ? "BUKA BUKTI KES (FORUM LOWYAT)" : (link.contains("carigold") ? "BUKA BUKTI KES (FORUM CARIGOLD)" : "BUKA BUKTI KES (PAUTAN LUAR)");
                        btnLowyat.setText("\uD83D\uDD17 " + linkTitle);
                        btnLowyat.setTextColor(Color.WHITE);
                        btnLowyat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        btnLowyat.setTypeface(Typeface.DEFAULT_BOLD);
                        GradientDrawable lwBg = new GradientDrawable();
                        lwBg.setColor(0xFF1E88E5);
                        lwBg.setCornerRadius(dpToPx(context, 8));
                        btnLowyat.setBackground(lwBg);
                        LinearLayout.LayoutParams lwParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 38));
                        btnLowyat.setLayoutParams(lwParams);
                        btnLowyat.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                } catch (Throwable ignored) {}
                            }
                        });
                        cardLinks.addView(btnLowyat);
                    } else {
                        addInfoRow(context, cardLinks, "\uD83D\uDCDD Catatan / Modus Operandi:", threat.evidenceLinks, 0xFFE0E0E0, false);
                    }
                }
                rootLayout.addView(cardLinks);
            }

            // Bottom Action Buttons: Copy and Close
            LinearLayout actionLayout = new LinearLayout(context);
            actionLayout.setOrientation(LinearLayout.HORIZONTAL);
            actionLayout.setPadding(0, dpToPx(context, 14), 0, 0);

            Button btnCopy = new Button(context);
            btnCopy.setText("\uD83D\uDCCB SALIN INFO");
            btnCopy.setTextColor(Color.WHITE);
            btnCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            GradientDrawable copyBg = new GradientDrawable();
            copyBg.setColor(0xFF0284C7);
            copyBg.setCornerRadius(dpToPx(context, 8));
            btnCopy.setBackground(copyBg);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dpToPx(context, 44), 1.0f);
            copyParams.setMargins(0, 0, dpToPx(context, 6), 0);
            btnCopy.setLayoutParams(copyParams);

            btnCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        StringBuilder sb = new StringBuilder();
                        sb.append("\u26A0\uFE0F [AMARAN ANCAMAN SCAMMER DISAHKAN]\n");
                        sb.append("Nama Suspek: ").append(threat.suspectName).append("\n");
                        sb.append("Nombor Telefon: ").append(threat.allPhones != null ? threat.allPhones : threat.phoneNorm).append("\n");
                        if (threat.bankAccounts != null && !threat.bankAccounts.equals("N/A") && !threat.bankAccounts.equals("Sila semak di portal SemakMule")) {
                            sb.append("Akaun Bank Keldai: ").append(threat.bankAccounts).append("\n");
                        }
                        if (threat.icNumber != null && !threat.icNumber.equals("N/A")) {
                            sb.append("No. Kad Pengenalan: ").append(threat.icNumber).append("\n");
                        }
                        if (threat.address != null && !threat.address.equals("N/A") && !threat.address.equals("Didaftarkan oleh pengguna")) {
                            sb.append("Alamat / Lokasi: ").append(threat.address).append("\n");
                        }
                        sb.append("SemakMule CCID: https://semakmule.rmp.gov.my/\n");
                        if (threat.evidenceLinks != null && !threat.evidenceLinks.equals("N/A") && !threat.evidenceLinks.equals("https://semakmule.rmp.gov.my/")) {
                            sb.append("Catatan / Bukti: ").append(threat.evidenceLinks).append("\n");
                        }

                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Scammer Threat Dossier", sb.toString());
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "\u2705 Maklumat lengkap scammer disalin! Anda boleh tampal di SemakMule atau WhatsApp.", Toast.LENGTH_LONG).show();
                    } catch (Throwable t) {
                        Log.e(TAG, "Copy error: " + t.getMessage());
                    }
                }
            });
            actionLayout.addView(btnCopy);

            final AlertDialog dialog = builder.create();

            Button btnEdit = new Button(context);
            btnEdit.setText("\u270F\uFE0F KEMASKINI");
            btnEdit.setTextColor(Color.WHITE);
            btnEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            GradientDrawable editBg = new GradientDrawable();
            editBg.setColor(0xFFF59E0B);
            editBg.setCornerRadius(dpToPx(context, 8));
            btnEdit.setBackground(editBg);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dpToPx(context, 44), 1.0f);
            editParams.setMargins(0, 0, dpToPx(context, 6), 0);
            btnEdit.setLayoutParams(editParams);

            btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showRegisterScammerDialog(activity_to_activity(context), threat.phoneNorm);
                }
            });
            actionLayout.addView(btnEdit);

            Button btnClose = new Button(context);
            btnClose.setText("\u2715 TUTUP");
            btnClose.setTextColor(Color.WHITE);
            btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setColor(0xFF263238);
            closeBg.setCornerRadius(dpToPx(context, 8));
            btnClose.setBackground(closeBg);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(0, dpToPx(context, 44), 1.0f);
            closeParams.setMargins(dpToPx(context, 6), 0, 0, 0);
            btnClose.setLayoutParams(closeParams);

            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            actionLayout.addView(btnClose);

            Button btnReportBot = new Button(context);
            btnReportBot.setText("🏛️ SIASAT & LAPOR KE AGENSI (BOT KANTOI)");
            btnReportBot.setTextColor(Color.WHITE);
            btnReportBot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnReportBot.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable repBg = new GradientDrawable();
            repBg.setColor(0xFF059669);
            repBg.setCornerRadius(dpToPx(context, 8));
            btnReportBot.setBackground(repBg);
            LinearLayout.LayoutParams repParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44));
            repParams.setMargins(0, dpToPx(context, 10), 0, dpToPx(context, 6));
            btnReportBot.setLayoutParams(repParams);

            btnReportBot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String p = (threat != null && threat.phoneNorm != null) ? threat.phoneNorm : "";
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/crowdcallerbot?start=semak_" + p));
                        context.startActivity(i);
                    } catch (Throwable t) {
                        Toast.makeText(context, "Sila buka Telegram dan cari Bot Risikan KANTOI (@crowdcallerbot)", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            rootLayout.addView(btnReportBot);

            rootLayout.addView(actionLayout);
            scrollView.addView(rootLayout);

            dialog.setView(scrollView);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
        } catch (Throwable t) {
            Log.e(TAG, "showThreatDialog error: " + t.getMessage(), t);
        }
    }

    private static Context activity_to_context(Context c) {
        return c;
    }

    private void querySemakMuleApiAsync(final Context context, final String localPhone, final TextView tvResult) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final SemakMuleData data = querySemakMuleNetwork(localPhone);
                if (data != null) {
                    sSemakMuleMap.put(localPhone, data);
                } else {
                    SemakMuleData fallback = new SemakMuleData();
                    fallback.searchCount = -1;
                    fallback.reportCount = -1;
                    fallback.loading = false;
                    sSemakMuleMap.put(localPhone, fallback);
                }
                tvResult.post(new Runnable() {
                    @Override
                    public void run() {
                        if (data != null && data.searchCount > 0) {
                            if (data.reportCount > 0) {
                                tvResult.setText("\u26A0\uFE0F DISAHKAN PDRM CCID!\n\u2022 " + data.reportCount + " Laporan Polis Rasmi Ditemui\n\u2022 Telah dicari " + data.searchCount + " kali di portal SemakMule.");
                                tvResult.setTextColor(0xFFFF5252);
                            } else {
                                tvResult.setText("\uD83D\uDD0D REKOD CARIAN SEMAKMULE:\n\u2022 Nombor ini telah dicari " + data.searchCount + " kali oleh orang ramai di SemakMule PDRM.");
                                tvResult.setTextColor(0xFFFFD54F);
                            }
                        } else {
                            tvResult.setText("\uD83C\uDFDB\uFE0F Pangkalan Data SemakMule CCID PDRM\nSedia untuk semakan terus status akaun bank & rekod polis.");
                            tvResult.setTextColor(0xFF80D8FF);
                        }
                    }
                });
            }
        }).start();
    }

    private LinearLayout createCard(Context context, int bgColor, int strokeColor) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(context, 14), dpToPx(context, 12), dpToPx(context, 14), dpToPx(context, 12));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dpToPx(context, 10));
        gd.setStroke(dpToPx(context, 1), strokeColor);
        card.setBackground(gd);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dpToPx(context, 10), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private void addSectionHeader(Context context, LinearLayout parent, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(0xFFB0BEC5);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, dpToPx(context, 6));
        parent.addView(tv);
    }

    public static void addInfoRow(Context context, LinearLayout parent, String label, String value, int valColor, boolean isBold) {
        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF90A4AE);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        parent.addView(tvLabel);

        TextView tvVal = new TextView(context);
        tvVal.setText(value);
        tvVal.setTextColor(valColor);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (isBold) {
            tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        }
        tvVal.setPadding(0, 0, 0, dpToPx(context, 6));
        parent.addView(tvVal);
    }

    // =========================================================================
    // REAL-TIME INCOMING CALL SCAMMER INTERCEPTION & FLOATING ALERT CARD
    // =========================================================================

    private static void registerCallStateListener(final Context context) {
        if (sCallReceiverRegistered) return;
        try {
            final Context appContext = context.getApplicationContext();
            final KantoiThreatEngine engine = getInstance(appContext);

            // 1. TelephonyManager PhoneStateListener
            final TelephonyManager tm = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                PhoneStateListener listener = new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String incomingNumber) {
                        super.onCallStateChanged(state, incomingNumber);
                        handleCallState(appContext, engine, state, incomingNumber);
                    }
                };
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
                Log.i(TAG, "PhoneStateListener registered for incoming call interception.");
            }

            // 2. BroadcastReceiver for ACTION_PHONE_STATE_CHANGED
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    String stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                    int state = TelephonyManager.CALL_STATE_IDLE;
                    if (TelephonyManager.EXTRA_STATE_RINGING.equals(stateStr)) {
                        state = TelephonyManager.CALL_STATE_RINGING;
                    } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(stateStr)) {
                        state = TelephonyManager.CALL_STATE_OFFHOOK;
                    }
                    handleCallState(ctx, engine, state, incomingNumber);
                }
            };

            appContext.registerReceiver(receiver, filter);
            sCallReceiverRegistered = true;
            Log.i(TAG, "Incoming call BroadcastReceiver registered successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "registerCallStateListener error: " + t.getMessage(), t);
        }
    }

    private static void handleCallState(final Context context, final KantoiThreatEngine engine, int state, String incomingNumber) {
        try {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                Log.i(TAG, "INCOMING CALL RINGING: number=" + incomingNumber);
                if (incomingNumber != null && !incomingNumber.trim().isEmpty()) {
                    processIncomingRinging(context, engine, incomingNumber);
                } else {
                    checkRecentIncomingCallAsync(context, engine);
                }
            } else if (state == TelephonyManager.CALL_STATE_IDLE || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                Log.i(TAG, "Call ended or answered. Dismissing floating alert banner.");
                dismissIncomingScammerAlert(context);
                sLastThreatOnScreen = null;
                sCurrentRingingNumber = null;
            }
        } catch (Throwable t) {
            Log.e(TAG, "handleCallState error: " + t.getMessage(), t);
        }
    }

    private static void processIncomingRinging(final Context context, final KantoiThreatEngine engine, String rawNumber) {
        final String localPhone = normalizeToLocal(rawNumber);
        sCurrentRingingNumber = localPhone;
        Log.i(TAG, "Processing incoming ringing call: " + rawNumber + " -> " + localPhone);

        final ScammerThreat threat = engine.checkThreat(localPhone);
        if (threat != null) {
            Log.w(TAG, "SCAMMER MATCHED ON INCOMING CALL: " + threat.suspectName + " (" + localPhone + ")");
            sLastThreatOnScreen = threat;
            postIncomingCallNotification(context, localPhone, threat);
        } else {
            Log.i(TAG, "Incoming caller is not in threat DB (" + localPhone + "). Normal incoming call.");
            sLastThreatOnScreen = null;
            dismissIncomingScammerAlert(context);
        }
    }

    private static void checkRecentIncomingCallAsync(final Context context, final KantoiThreatEngine engine) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(400);
                    if (context.checkCallingOrSelfPermission(android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Cursor cursor = context.getContentResolver().query(
                            CallLog.Calls.CONTENT_URI,
                            new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE},
                            null, null,
                            CallLog.Calls.DATE + " DESC LIMIT 1"
                        );
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                String num = cursor.getString(0);
                                long date = cursor.getLong(2);
                                if (System.currentTimeMillis() - date < 20000) {
                                    processIncomingRinging(context, engine, num);
                                }
                            }
                            cursor.close();
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "checkRecentIncomingCallAsync failed: " + t.getMessage());
                }
            }
        }).start();
    }

    private static synchronized void showIncomingScammerAlert(final Context context, final String phone, final ScammerThreat threat) {
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    triggerWarningVibration(context);
                    postIncomingCallNotification(context, phone, threat);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to post scammer alert notification: " + t.getMessage(), t);
                }
            }
        });
    }

    private static synchronized void dismissIncomingScammerAlert(final Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sFloatingAlertView != null && sWindowManager != null) {
                        sWindowManager.removeView(sFloatingAlertView);
                        Log.i(TAG, "Floating incoming scammer alert banner removed.");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error removing floating alert: " + t.getMessage());
                } finally {
                    sFloatingAlertView = null;
                    sWindowManager = null;
                    sCurrentRingingNumber = null;
                }

                try {
                    if (context != null) {
                        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) {
                            nm.cancel(SCAMMER_CALL_NOTIFICATION_ID);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private static View buildIncomingAlertCardView(final Context context, final String phone, final ScammerThreat threat) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(context, 14), dpToPx(context, 12), dpToPx(context, 14), dpToPx(context, 12));

        GradientDrawable gd = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFFB71C1C, 0xFF880E4F} // Crimson to Deep Wine Red
        );
        gd.setCornerRadius(dpToPx(context, 18));
        gd.setStroke(dpToPx(context, 2), 0xFFFF5252); // Bright Crimson Border
        root.setBackground(gd);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setElevation(dpToPx(context, 16));
        }

        // Row 1: Header (Icon, Title, Badge, Close)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvIcon = new TextView(context);
        tvIcon.setText("\u2620\uFE0F");
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvIcon.setPadding(0, 0, dpToPx(context, 8), 0);
        header.addView(tvIcon);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("SCAMMER DISAHKAN!");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        header.addView(tvTitle);

        TextView tvBadge = new TextView(context);
        tvBadge.setText("ANCAMAN TINGGI");
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xFFD32F2F);
        badgeBg.setCornerRadius(dpToPx(context, 10));
        badgeBg.setStroke(dpToPx(context, 1), 0xFFFF8A80);
        tvBadge.setBackground(badgeBg);
        tvBadge.setPadding(dpToPx(context, 6), dpToPx(context, 2), dpToPx(context, 6), dpToPx(context, 2));
        header.addView(tvBadge);

        TextView tvClose = new TextView(context);
        tvClose.setText(" \u2715 ");
        tvClose.setTextColor(0xFFFFCDD2);
        tvClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvClose.setTypeface(Typeface.DEFAULT_BOLD);
        tvClose.setPadding(dpToPx(context, 6), 0, 0, 0);
        tvClose.setClickable(true);
        tvClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissIncomingScammerAlert(context);
            }
        });
        header.addView(tvClose);

        root.addView(header);

        // Row 2: Suspect Info
        TextView tvSuspect = new TextView(context);
        tvSuspect.setId(1001);
        String suspectName = (threat != null && threat.suspectName != null && !threat.suspectName.isEmpty())
                ? threat.suspectName
                : "Nombor Pemanggil Bahaya";
        tvSuspect.setText("Suspek: " + suspectName + " \u2022 " + phone);
        tvSuspect.setTextColor(0xFFFFF59D); // Soft Yellow
        tvSuspect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSuspect.setTypeface(Typeface.DEFAULT_BOLD);
        tvSuspect.setPadding(0, dpToPx(context, 4), 0, 0);
        root.addView(tvSuspect);

        // Row 3: CCID Stats
        TextView tvStats = new TextView(context);
        tvStats.setId(1002);
        SemakMuleData sm = sSemakMuleMap.get(phone);
        String statsText;
        if (sm != null && sm.reportCount > 0) {
            statsText = "\uD83D\uDEA8 PDRM CCID: " + sm.reportCount + " Laporan Polis Rasmi (" + sm.searchCount + " Carian) \u2022 Lowyat Blacklist";
        } else if (sm != null && sm.searchCount > 0) {
            statsText = "\uD83D\uDD0D PDRM SemakMule: " + sm.searchCount + " Carian Rekod JSJK \u2022 Lowyat Blacklist";
        } else {
            statsText = "\uD83D\uDEA8 Pangkalan Data: Disahkan dalam Lowyat Threat DB & SemakMule CCID";
        }
        tvStats.setText(statsText);
        tvStats.setTextColor(0xFFFFCC80); // Warm Amber
        tvStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvStats.setTypeface(Typeface.DEFAULT_BOLD);
        tvStats.setPadding(0, dpToPx(context, 2), 0, dpToPx(context, 8));
        root.addView(tvStats);

        // Row 4: Buttons
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnDossier = new TextView(context);
        btnDossier.setText("\uD83D\uDD0D BUKTI & INFO LENGKAP");
        btnDossier.setTextColor(Color.WHITE);
        btnDossier.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnDossier.setTypeface(Typeface.DEFAULT_BOLD);
        btnDossier.setGravity(Gravity.CENTER);
        GradientDrawable btn1Bg = new GradientDrawable();
        btn1Bg.setColor(0xFFD32F2F);
        btn1Bg.setCornerRadius(dpToPx(context, 8));
        btn1Bg.setStroke(dpToPx(context, 1), 0xFFFF8A80);
        btnDossier.setBackground(btn1Bg);
        btnDossier.setPadding(dpToPx(context, 8), dpToPx(context, 6), dpToPx(context, 8), dpToPx(context, 6));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnDossier.setLayoutParams(lp1);
        btnDossier.setClickable(true);
        btnDossier.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    final Activity fg = (sLastForegroundActivity != null) ? sLastForegroundActivity.get() : null;
                    if (fg != null && !fg.isFinishing()) {
                        getInstance(context).showThreatDialog(fg, (threat != null) ? threat : createSyntheticThreat(phone));
                    } else {
                        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(launchIntent);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
        btnRow.addView(btnDossier);

        TextView btnSemak = new TextView(context);
        btnSemak.setText("\uD83C\uDFDB\uFE0F SEMAKMULE PDRM");
        btnSemak.setTextColor(Color.WHITE);
        btnSemak.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnSemak.setTypeface(Typeface.DEFAULT_BOLD);
        btnSemak.setGravity(Gravity.CENTER);
        GradientDrawable btn2Bg = new GradientDrawable();
        btn2Bg.setColor(0xFF1565C0);
        btn2Bg.setCornerRadius(dpToPx(context, 8));
        btn2Bg.setStroke(dpToPx(context, 1), 0xFF90CAF9);
        btnSemak.setBackground(btn2Bg);
        btnSemak.setPadding(dpToPx(context, 8), dpToPx(context, 6), dpToPx(context, 8), dpToPx(context, 6));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp2.setMargins(dpToPx(context, 8), 0, 0, 0);
        btnSemak.setLayoutParams(lp2);
        btnSemak.setClickable(true);
        btnSemak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browserIntent);
                } catch (Throwable ignored) {}
            }
        });
        btnRow.addView(btnSemak);

        root.addView(btnRow);
        return root;
    }

    private static void updateIncomingAlertCard(View alertView, Context context, String phone, ScammerThreat threat) {
        if (alertView == null) return;
        try {
            TextView tvSuspect = (TextView) alertView.findViewById(1001);
            TextView tvStats = (TextView) alertView.findViewById(1002);
            if (tvSuspect != null) {
                String suspectName = (threat != null && threat.suspectName != null && !threat.suspectName.isEmpty())
                        ? threat.suspectName
                        : "Nombor Pemanggil Bahaya";
                tvSuspect.setText("Suspek: " + suspectName + " \u2022 " + phone);
            }
            if (tvStats != null) {
                SemakMuleData sm = sSemakMuleMap.get(phone);
                if (sm != null && sm.reportCount > 0) {
                    tvStats.setText("\uD83D\uDEA8 PDRM CCID: " + sm.reportCount + " Laporan Polis Rasmi (" + sm.searchCount + " Carian) \u2022 Lowyat Blacklist");
                } else if (sm != null && sm.searchCount > 0) {
                    tvStats.setText("\uD83D\uDD0D PDRM SemakMule: " + sm.searchCount + " Carian Rekod JSJK \u2022 Lowyat Blacklist");
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void triggerWarningVibration(Context context) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200, 100, 400}, -1));
                } else {
                    v.vibrate(new long[]{0, 200, 100, 200, 100, 400}, -1);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void postIncomingCallNotification(Context context, String phone, ScammerThreat threat) {
        if (context == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    SCAMMER_CALL_CHANNEL_ID,
                    "Amaran Scammer Panggilan Masuk",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Amaran visual & haptik apabila nombor scammer disahkan membuat panggilan");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 150, 250, 150, 500});
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = PendingIntent.getActivity(
                context,
                0,
                openApp,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
            );

            String suspect = (threat != null && threat.suspectName != null) ? threat.suspectName : "Suspek Tidak Dikenali";
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nb = new Notification.Builder(context, SCAMMER_CALL_CHANNEL_ID);
            } else {
                nb = new Notification.Builder(context);
            }

            nb.setSmallIcon(android.R.drawable.stat_sys_warning)
              .setContentTitle("\u2620\uFE0F [SCAMMER] " + phone)
              .setContentText("\uD83D\uDEA8 AMARAN: Suspek " + suspect + " dikesan dalam pangkalan data Scammer CCID & Lowyat!")
              .setCategory(Notification.CATEGORY_CALL)
              .setPriority(Notification.PRIORITY_MAX)
              .setOngoing(true)
              .setAutoCancel(false)
              .setContentIntent(pi);

            nm.notify(SCAMMER_CALL_NOTIFICATION_ID, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "postIncomingCallNotification failed: " + t.getMessage());
        }
    }

    public void showFloatingAlertBanner(Context context, String phone, ScammerThreat threat) {
        showIncomingScammerAlert(context, phone, threat);
    }

    public static void removeFloatingAlertBanner() {
        dismissIncomingScammerAlert(null);
    }

    public void showScammerCallNotification(Context context, String phone, ScammerThreat threat, boolean isBlocked) {
        if (context == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    SCAMMER_CALL_CHANNEL_ID,
                    "Kantoi Amaran Scammer",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifikasi amaran dan sekatan panggilan scammer");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 150, 250, 150, 500});
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                pi = PendingIntent.getActivity(
                    context, 0, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            String suspect = (threat != null && threat.suspectName != null) ? threat.suspectName : "Suspek Scammer";
            String title = isBlocked ? "\uD83D\uDEA8 KANTOI! Panggilan Scammer Disekat" : "\uD83D\uDEA8 KANTOI! Panggilan Scammer Dikesan";
            String msg = phone + " (" + suspect + ") dikesan dalam pangkalan data komuniti!";

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, SCAMMER_CALL_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_sys_warning)
              .setContentTitle(title)
              .setContentText(msg)
              .setPriority(Notification.PRIORITY_MAX)
              .setAutoCancel(true);

            if (pi != null) {
                nb.setContentIntent(pi);
            }

            nm.notify(SCAMMER_CALL_NOTIFICATION_ID, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showScammerCallNotification error: " + t.getMessage(), t);
        }
    }

    private static ScammerThreat parseThreat(Cursor c) {
        if (c == null) return null;
        ScammerThreat t = new ScammerThreat();
        t.phoneNorm = c.getString(0);
        t.profileId = c.getInt(1);
        t.suspectName = c.getString(2);
        t.allPhones = c.getString(3);
        t.bankAccounts = c.getString(4);
        t.icNumber = c.getString(5);
        t.address = c.getString(6);
        t.evidenceLinks = c.getString(7);
        t.riskTag = c.getString(8);
        t.source = c.getString(9);
        return t;
    }

    // ==========================================
    // CIRI 1: CONTACTS WHITELIST & AUDIT ENGINE
    // ==========================================

    public static class ContactAuditResult {
        public int totalScanned = 0;
        public final List<ContactThreatMatch> threatsFound = new ArrayList<ContactThreatMatch>();
    }

    public static class ContactThreatMatch {
        public String contactName;
        public String contactNumber;
        public ScammerThreat threat;
    }

    public static boolean isRealContactName(String name, String rawNumber) {
        if (name == null) return false;
        String clean = name.trim();
        if (clean.isEmpty() || "Tanpa Nama".equalsIgnoreCase(clean) || "Unknown".equalsIgnoreCase(clean) || "Kenalan Tanpa Nama".equalsIgnoreCase(clean)) {
            return false;
        }

        // Must contain at least one letter character (a-z, A-Z, or any Unicode letter)
        boolean hasLetter = false;
        for (int i = 0; i < clean.length(); i++) {
            if (Character.isLetter(clean.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) {
            return false; // Numbers or symbols only are NOT valid contact names!
        }

        // Strip non-digits and check if it's identical to phone number digits
        String nameDigits = clean.replaceAll("[^0-9]", "");
        if (rawNumber != null) {
            String rawDigits = rawNumber.replaceAll("[^0-9]", "");
            if (!nameDigits.isEmpty() && !rawDigits.isEmpty()) {
                if (nameDigits.equals(rawDigits)) return false;
                if (nameDigits.length() >= 7 && (nameDigits.endsWith(rawDigits) || rawDigits.endsWith(nameDigits))) {
                    return false;
                }
            }
        }

        return true;
    }

    public void cacheContactName(String rawNumber, String name) {
        if (rawNumber == null || rawNumber.trim().isEmpty() || name == null || name.trim().isEmpty()) return;
        String cleanName = name.trim();
        if (!isRealContactName(cleanName, rawNumber)) {
            return;
        }

        String digits = rawNumber.replaceAll("[^0-9]", "");
        String norm = normalizeToLocal(rawNumber);
        if (norm.length() >= 7) {
            mContactNameCache.put(norm, cleanName);
            mNativeContactsWhitelist.add(norm);
            mCheckedNonContacts.remove(norm);
        }
        if (!digits.isEmpty()) {
            mContactNameCache.put(digits, cleanName);
            mNativeContactsWhitelist.add(digits);
            mCheckedNonContacts.remove(digits);
            if (digits.length() >= 8) {
                mContactNameCache.put(digits.substring(digits.length() - 8), cleanName);
            }
        }
        mContactNameCache.put(rawNumber.trim(), cleanName);
    }

    public synchronized void preloadDeviceContacts() {
        if (mContext == null) return;
        try {
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }

            // 1. Fetch Master Aggregated Contact Names
            java.util.Map<Long, String> masterNames = new java.util.HashMap<Long, String>();
            Cursor cContacts = null;
            try {
                Uri cUri = ContactsContract.Contacts.CONTENT_URI;
                String[] cProj = new String[]{
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.DISPLAY_NAME
                };
                cContacts = mContext.getContentResolver().query(cUri, cProj, ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0", null, null);
                if (cContacts != null) {
                    int idCol = cContacts.getColumnIndex(ContactsContract.Contacts._ID);
                    int pCol = cContacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                    int dCol = cContacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                    while (cContacts.moveToNext()) {
                        long cId = idCol >= 0 ? cContacts.getLong(idCol) : 0;
                        String cName = pCol >= 0 ? cContacts.getString(pCol) : null;
                        if ((cName == null || cName.trim().isEmpty()) && dCol >= 0) {
                            cName = cContacts.getString(dCol);
                        }
                        if (cId > 0 && isRealContactName(cName, null)) {
                            masterNames.put(cId, cName.trim());
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (cContacts != null) cContacts.close();
            }

            // 2. Fetch Phone Numbers and bind to real names
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            };
            Cursor cursor = mContext.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int colNum = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int colPName = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY);
                int colDName = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int colId = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                while (cursor.moveToNext()) {
                    String num = (colNum >= 0) ? cursor.getString(colNum) : null;
                    if (num == null || num.trim().isEmpty()) continue;
                    long contactId = (colId >= 0) ? cursor.getLong(colId) : 0;

                    String name = null;
                    if (contactId > 0 && masterNames.containsKey(contactId)) {
                        name = masterNames.get(contactId);
                    }
                    if (!isRealContactName(name, num)) {
                        if (colPName >= 0) name = cursor.getString(colPName);
                        if (!isRealContactName(name, num) && colDName >= 0) {
                            name = cursor.getString(colDName);
                        }
                    }
                    if (isRealContactName(name, num)) {
                        cacheContactName(num, name);
                    }
                }
                cursor.close();
            }
            mContactsScanned = true;
        } catch (Throwable ignored) {}
    }

    public void preloadDeviceContactsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                preloadDeviceContacts();
                try {
                    ContactsActivity.preloadContactsCache(mContext);
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    public boolean isSafeContact(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return false;
        String digits = rawNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return false;
        String norm = normalizeToLocal(rawNumber);
        if (isCustomSafeContact(norm)) return true;
        if (mNativeContactsWhitelist.contains(norm) || mNativeContactsWhitelist.contains(digits)) return true;
        if (mContactNameCache.containsKey(norm) || mContactNameCache.containsKey(digits)) return true;
        if (digits.length() >= 8 && mContactNameCache.containsKey(digits.substring(digits.length() - 8))) return true;
        if (mContactsScanned || mCheckedNonContacts.contains(norm) || mCheckedNonContacts.contains(digits)) {
            return false;
        }
        String name = getContactDisplayName(rawNumber);
        return (name != null && !name.trim().isEmpty());
    }

    public boolean isSafeContactFast(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return false;
        String digits = rawNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return false;
        String norm = normalizeToLocal(rawNumber);
        if (isCustomSafeContact(norm)) return true;
        if (mNativeContactsWhitelist.contains(norm) || mNativeContactsWhitelist.contains(digits)) return true;
        if (mContactNameCache.containsKey(norm) || mContactNameCache.containsKey(digits)) return true;
        if (digits.length() >= 8 && mContactNameCache.containsKey(digits.substring(digits.length() - 8))) return true;
        return false;
    }

    public String getContactDisplayNameFast(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return null;
        String digits = rawNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return null;
        String norm = normalizeToLocal(rawNumber);

        // 1. Check custom safe contact saved in SQLite
        SafeContactItem customItem = getCustomSafeContact(norm);
        if (customItem != null && customItem.contactName != null && !customItem.contactName.trim().isEmpty()) {
            return customItem.contactName;
        }

        // 2. Check in-memory fast cache
        String cached = mContactNameCache.get(norm);
        if (isRealContactName(cached, rawNumber)) return cached;
        cached = mContactNameCache.get(digits);
        if (isRealContactName(cached, rawNumber)) return cached;
        cached = mContactNameCache.get(rawNumber.trim());
        if (isRealContactName(cached, rawNumber)) return cached;
        if (digits.length() >= 8) {
            cached = mContactNameCache.get(digits.substring(digits.length() - 8));
            if (isRealContactName(cached, rawNumber)) return cached;
        }
        return null;
    }

    public String getContactDisplayName(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return null;
        String digits = rawNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return null;
        String norm = normalizeToLocal(rawNumber);

        // 1. Check custom safe contact saved in SQLite
        SafeContactItem customItem = getCustomSafeContact(norm);
        if (customItem != null && customItem.contactName != null && !customItem.contactName.trim().isEmpty()) {
            return customItem.contactName;
        }

        // 2. Check in-memory fast cache
        String cached = mContactNameCache.get(norm);
        if (isRealContactName(cached, rawNumber)) return cached;
        cached = mContactNameCache.get(digits);
        if (isRealContactName(cached, rawNumber)) return cached;
        cached = mContactNameCache.get(rawNumber.trim());
        if (isRealContactName(cached, rawNumber)) return cached;
        if (digits.length() >= 8) {
            cached = mContactNameCache.get(digits.substring(digits.length() - 8));
            if (isRealContactName(cached, rawNumber)) return cached;
        }

        if (mContactsScanned || mCheckedNonContacts.contains(norm) || mCheckedNonContacts.contains(digits)) {
            return null;
        }

        if (mContext == null) return null;

        // 3. Fallback: Query Android ContactsProvider
        try {
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return null;
            }

            // Query PhoneLookup with multiple format variants
            String[] testNums = new String[]{rawNumber, norm, "+60" + (norm.startsWith("0") ? norm.substring(1) : norm)};
            for (String tNum : testNums) {
                if (tNum == null || tNum.isEmpty()) continue;
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(tNum));
                Cursor cursor = mContext.getContentResolver().query(uri, new String[]{
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup._ID
                }, null, null, null);
                if (cursor != null) {
                    String foundName = null;
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                        if (col >= 0) foundName = cursor.getString(col);
                    }
                    cursor.close();
                    if (isRealContactName(foundName, rawNumber)) {
                        cacheContactName(norm, foundName);
                        return foundName.trim();
                    }
                }
            }

            // Fallback: Query CommonDataKinds.Phone with suffix match
            Uri phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String suffix = digits.length() >= 8 ? digits.substring(digits.length() - 8) : digits;
            Cursor pCursor = mContext.getContentResolver().query(
                phoneUri,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
                },
                ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
                new String[]{"%" + suffix},
                null
            );
            if (pCursor != null) {
                String foundName = null;
                if (pCursor.moveToFirst()) {
                    int c1 = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY);
                    int c2 = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    if (c1 >= 0) foundName = pCursor.getString(c1);
                    if ((foundName == null || foundName.trim().isEmpty()) && c2 >= 0) {
                        foundName = pCursor.getString(c2);
                    }
                }
                pCursor.close();
                if (isRealContactName(foundName, rawNumber)) {
                    cacheContactName(norm, foundName);
                    return foundName.trim();
                }
            }
        } catch (Throwable ignored) {}

        mCheckedNonContacts.add(norm);
        mCheckedNonContacts.add(digits);
        return null;
    }

    public static SemakMuleData getSemakMuleData(String localPhone) {
        if (localPhone == null) return null;
        return sSemakMuleMap.get(localPhone);
    }

    public static String formatPhoneNumber(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("60") && digits.length() >= 10) {
            String prefix = "+60 " + digits.substring(2, 4);
            if (digits.length() == 11) {
                return prefix + "-" + digits.substring(4, 7) + " " + digits.substring(7);
            } else if (digits.length() >= 12) {
                return prefix + "-" + digits.substring(4, 8) + " " + digits.substring(8);
            } else {
                return prefix + "-" + digits.substring(4);
            }
        } else if (digits.startsWith("0") && digits.length() >= 9) {
            String prefix = digits.substring(0, 3);
            if (digits.length() == 10) {
                return prefix + "-" + digits.substring(3, 6) + " " + digits.substring(6);
            } else if (digits.length() >= 11) {
                return prefix + "-" + digits.substring(3, 7) + " " + digits.substring(7);
            } else {
                return prefix + "-" + digits.substring(3);
            }
        }
        return raw;
    }

    public ContactAuditResult auditContacts() {
        ContactAuditResult result = new ContactAuditResult();
        if (mContext == null) return result;
        try {
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_CONTACTS permission not granted!");
                return result;
            }

            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            };

            Cursor cursor = mContext.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                Set<String> processed = new HashSet<String>();

                do {
                    String name = (nameCol >= 0) ? cursor.getString(nameCol) : "Tanpa Nama";
                    String num = (numCol >= 0) ? cursor.getString(numCol) : null;
                    if (num != null && !num.trim().isEmpty()) {
                        String norm = normalizeToLocal(num);
                        mNativeContactsWhitelist.add(norm);
                        if (name != null && !name.trim().isEmpty() && !name.equals("Tanpa Nama")) {
                            mContactNameCache.put(norm, name.trim());
                        }
                        mCheckedNonContacts.remove(norm);
                        if (processed.add(norm)) {
                            result.totalScanned++;
                            ScammerThreat threat = checkThreat(norm);
                            if (threat != null) {
                                ContactThreatMatch match = new ContactThreatMatch();
                                match.contactName = (name != null && !name.trim().isEmpty()) ? name : "Tanpa Nama";
                                match.contactNumber = norm;
                                match.threat = threat;
                                result.threatsFound.add(match);
                            }
                        }
                    }
                } while (cursor.moveToNext());
                cursor.close();
            }
            mContactsScanned = true;
        } catch (Throwable t) {
            Log.e(TAG, "auditContacts error: " + t.getMessage(), t);
        }
        return result;
    }

    public int getWhitelistedContactsCount() {
        return mNativeContactsWhitelist.size();
    }

    public static void showContactAuditDialog(final Activity activity, final ContactAuditResult result) {
        if (activity == null || activity.isFinishing()) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            boolean isClean = (result.threatsFound.isEmpty());

            builder.setTitle(isClean ? "\uD83D\uDEE1\uFE0F BUKU TELEFON ANDA BERSIH!" : "\uD83D\uDEA8 AMARAN: KENALAN SCAMMER DITEMUI!");

            StringBuilder sb = new StringBuilder();
            if (isClean) {
                sb.append("✅ Sebanyak ").append(result.totalScanned).append(" kenalan telah diimbas.\n\n");
                sb.append("Tiada sebarang nombor scammer atau akaun keldai ditemui dalam buku telefon anda.\n\n");
                sb.append("Semua kenalan ini kini dilindungi di bawah Senarai Putih (Safe Whitelist) KANTOI.");
            } else {
                sb.append("⚠️ KANTOI mengesan ").append(result.threatsFound.size())
                  .append(" nombor dalam senarai kenalan anda yang berpadanan dengan rekod scammer disahkan:\n\n");
                for (int i = 0; i < result.threatsFound.size(); i++) {
                    ContactThreatMatch m = result.threatsFound.get(i);
                    sb.append(i + 1).append(". ").append(m.contactName).append(" (").append(m.contactNumber).append(")\n");
                    sb.append("   • Suspek: ").append(m.threat.suspectName).append("\n");
                    sb.append("   • Kategori: ").append(m.threat.riskTag).append(" (").append(m.threat.source).append(")\n");
                    if (m.threat.bankAccounts != null && !m.threat.bankAccounts.isEmpty()) {
                        sb.append("   • Bank Keldai: ").append(m.threat.bankAccounts).append("\n");
                    }
                    sb.append("\n");
                }
                sb.append("Nasihat: Sila berhati-hati atau padamkan nombor di atas daripada senarai kenalan anda.");
            }

            builder.setMessage(sb.toString());
            builder.setPositiveButton("TUTUP", null);
            if (!isClean && !result.threatsFound.isEmpty()) {
                builder.setNeutralButton("BUKA DOSSIER SUSPEK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        KantoiThreatEngine.getInstance(activity).showThreatDialog(activity, result.threatsFound.get(0).threat);
                    }
                });
            }
            builder.show();
        } catch (Throwable t) {
            Log.e(TAG, "showContactAuditDialog error: " + t.getMessage(), t);
        }
    }

    // ==========================================
    // CIRI 2: SMS SCAM & PHISHING SHIELD ENGINE
    // ==========================================

    public static class SmsThreatResult {
        public boolean isThreat = false;
        public String threatType = ""; // "KNOWN_SCAMMER", "PHISHING_URL", "FAKE_GOV_OR_BANK", "MULE_BANK_ACCOUNT"
        public String riskSummary = "";
        public String suspectName = "";
        public String extractedUrl = null;
        public String matchedDetail = "";
    }

    public SmsThreatResult analyzeSms(String sender, String body) {
        SmsThreatResult res = new SmsThreatResult();
        if (body == null) body = "";
        String lowerBody = body.toLowerCase();
        String normSender = (sender != null) ? normalizeToLocal(sender) : "";

        // 1. Check if sender is a known scammer in SQLite
        if (!normSender.isEmpty()) {
            ScammerThreat threat = checkThreat(normSender);
            if (threat != null) {
                res.isThreat = true;
                res.threatType = "KNOWN_SCAMMER";
                res.suspectName = threat.suspectName;
                res.riskSummary = "Pengirim SMS [" + normSender + "] disahkan sebagai scammer!";
                res.matchedDetail = "Suspek: " + threat.suspectName + " (" + threat.riskTag + " - " + threat.source + ")";
                return res;
            }
        }

        // 2. Extract URLs and analyze for phishing / dangerous downloads
        Pattern urlPattern = Pattern.compile("(https?://[\\w\\.-]+(?:/[\\w\\.-]*)*)", Pattern.CASE_INSENSITIVE);
        Matcher m = urlPattern.matcher(body);
        String detectedUrl = null;
        if (m.find()) {
            detectedUrl = m.group(1);
            res.extractedUrl = detectedUrl;
        }

        boolean hasSuspiciousUrl = false;
        String urlReason = "";
        if (detectedUrl != null) {
            String uLower = detectedUrl.toLowerCase();
            if (uLower.endsWith(".apk") || uLower.contains(".apk?") || uLower.contains("mediafire.com") || uLower.contains("dropbox.com") || uLower.contains("drive.google.com/uc")) {
                hasSuspiciousUrl = true;
                urlReason = "Pautan muat turun APK berbahaya / Trojan dikesan!";
            } else if (uLower.matches(".*\\.(xyz|top|vip|icu|cfd|buzz|site|fun|click|online|shop|live|cc|rest|quest|pro|ru)(/.*)?$") ||
                       uLower.contains("bit.ly") || uLower.contains("tinyurl.com") || uLower.contains("is.gd") || uLower.contains("cutt.ly") || uLower.contains("rb.gy")) {
                hasSuspiciousUrl = true;
                urlReason = "Pautan pemendek URL / domain berisiko tinggi (Phishing): " + detectedUrl;
            }
        }

        // 3. Check for high-risk Malaysian impersonation keywords
        boolean hasGovOrBank = false;
        String matchedOrg = "";
        String[] orgs = new String[]{
            "lhdn", "hasil", "cukai", "saman", "pdrm", "jpj", "myjpj", "mahkamah", "waran",
            "str", "sumbangan tunai", "bantuan kerajaan", "kwsp", "tng", "touch n go", "ewallet",
            "maybank", "cimb", "public bank", "rhb", "bsn", "bank islam", "ambank",
            "tac", "otp", "kod pengesahan", "akaun digantung", "akaun disekat", "pinjaman peribadi"
        };
        for (String org : orgs) {
            if (lowerBody.contains(org)) {
                hasGovOrBank = true;
                matchedOrg = org.toUpperCase();
                break;
            }
        }

        if (hasSuspiciousUrl && hasGovOrBank) {
            res.isThreat = true;
            res.threatType = "PHISHING_URL";
            res.riskSummary = "SMS menyamar agensi [" + matchedOrg + "] dengan pautan mencurigakan!";
            res.matchedDetail = urlReason + "\nKandungan SMS: " + ((body.length() > 80) ? body.substring(0, 80) + "..." : body);
            return res;
        }

        if (hasSuspiciousUrl) {
            res.isThreat = true;
            res.threatType = "PHISHING_URL";
            res.riskSummary = "SMS mengandungi pautan luaran tidak disahkan!";
            res.matchedDetail = urlReason;
            return res;
        }

        // 4. Check for 10-16 digit mule bank account numbers mentioned in SMS body
        Pattern bankPattern = Pattern.compile("\\b\\d{10,16}\\b");
        Matcher bm = bankPattern.matcher(body);
        while (bm.find()) {
            String bankCandidate = bm.group();
            ScammerThreat bankThreat = checkBankThreat(bankCandidate);
            if (bankThreat != null) {
                res.isThreat = true;
                res.threatType = "MULE_BANK_ACCOUNT";
                res.suspectName = bankThreat.suspectName;
                res.riskSummary = "SMS mengandungi No. Akaun Bank Keldai: " + bankCandidate;
                res.matchedDetail = "Suspek Pemilik: " + bankThreat.suspectName + " (" + bankThreat.riskTag + " - " + bankThreat.source + ")";
                return res;
            }
        }

        return res;
    }

    public ScammerThreat checkBankThreat(String bankAcc) {
        if (bankAcc == null || bankAcc.trim().length() < 8 || mDb == null || !mDb.isOpen()) return null;
        try {
            Cursor cursor = mDb.rawQuery("SELECT phone_norm, profile_id, suspect_name, all_phones, bank_accounts, ic_number, address, evidence_links, risk_tag, source FROM threat_numbers WHERE bank_accounts LIKE ? LIMIT 1", new String[]{"%" + bankAcc.trim() + "%"});
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    ScammerThreat threat = parseThreat(cursor);
                    cursor.close();
                    return threat;
                }
                cursor.close();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public void analyzeAndHandleSms(Context context, String sender, String body) {
        if (context == null || body == null) return;
        try {
            SmsThreatResult result = analyzeSms(sender, body);
            if (result.isThreat) {
                Log.w(TAG, "\uD83D\uDEA8 KANTOI SMS THREAT DETECTED from " + sender + ": " + result.riskSummary);
                showSmsScamNotification(context, sender, body, result);
                saveSmsThreatLog(sender, body, result);
            }
        } catch (Throwable t) {
            Log.e(TAG, "analyzeAndHandleSms error: " + t.getMessage(), t);
        }
    }

    public void showSmsScamNotification(Context context, String sender, String body, SmsThreatResult threat) {
        if (context == null || threat == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    SCAMMER_SMS_CHANNEL_ID,
                    "Kantoi Amaran SMS Scam",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifikasi segera apabila SMS scam atau pautan phishing dikesan");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 300, 100, 300, 100, 500});
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                openApp.putExtra("extra_open_sms_log", true);
                pi = PendingIntent.getActivity(
                    context, 101, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            String title = "\uD83D\uDEA8 KANTOI! SMS SCAM / PHISHING DIKESAN";
            String sub = "Pengirim: " + sender + " • " + threat.riskSummary;

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, SCAMMER_SMS_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_sys_warning)
              .setContentTitle(title)
              .setContentText(sub)
              .setStyle(new Notification.BigTextStyle().bigText(
                  "🚨 " + threat.riskSummary + "\n" +
                  "Pengirim: " + sender + "\n" +
                  (threat.extractedUrl != null ? "Pautan: " + threat.extractedUrl + "\n" : "") +
                  "Butiran: " + threat.matchedDetail + "\n\n" +
                  "⚠️ JANGAN KLIK sebarang pautan atau dedahkan maklumat perbankan anda!"
              ))
              .setPriority(Notification.PRIORITY_MAX)
              .setAutoCancel(true);

            if (pi != null) {
                nb.setContentIntent(pi);
            }

            nm.notify(SCAMMER_SMS_NOTIFICATION_ID, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showSmsScamNotification error: " + t.getMessage(), t);
        }
    }

    private void saveSmsThreatLog(String sender, String body, SmsThreatResult threat) {
        if (mContext == null) return;
        try {
            SharedPreferences prefs = mContext.getSharedPreferences("kantoi_sms_threat_logs", Context.MODE_PRIVATE);
            int count = prefs.getInt("log_count", 0);
            count++;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("log_count", count);
            editor.putString("log_" + count + "_sender", sender);
            editor.putString("log_" + count + "_body", body);
            editor.putString("log_" + count + "_summary", threat.riskSummary);
            editor.putString("log_" + count + "_detail", threat.matchedDetail);
            editor.putLong("log_" + count + "_time", System.currentTimeMillis());
            editor.apply();
        } catch (Throwable ignored) {}
    }

    public static String getRecentSmsThreatSummary(Context context) {
        if (context == null) return null;
        try {
            SharedPreferences prefs = context.getSharedPreferences("kantoi_sms_threat_logs", Context.MODE_PRIVATE);
            int count = prefs.getInt("log_count", 0);
            if (count > 0) {
                String sender = prefs.getString("log_" + count + "_sender", "Tidak diketahui");
                String summary = prefs.getString("log_" + count + "_summary", "Ancaman dikesan");
                return "SMS Terkini: " + sender + " (" + summary + ")";
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ==========================================
    // CIRI 3: MOD WARGA EMAS (SENIOR NOTIFICATION)
    // ==========================================

    public void showSeniorBlockedNotification(Context context, String phone) {
        if (context == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    SCAMMER_CALL_CHANNEL_ID,
                    "Kantoi Amaran Scammer",
                    NotificationManager.IMPORTANCE_HIGH
                );
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                pi = PendingIntent.getActivity(
                    context, 102, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, SCAMMER_CALL_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_notify_missed_call)
              .setContentTitle("\uD83D\uDC74 Mod Warga Emas: Panggilan Asing Disekat")
              .setContentText("Panggilan daripada [" + phone + "] telah disekat secara senyap kerana tiada dalam senarai kenalan.")
              .setPriority(Notification.PRIORITY_HIGH)
              .setAutoCancel(true);

            if (pi != null) nb.setContentIntent(pi);

            nm.notify((int) System.currentTimeMillis(), nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showSeniorBlockedNotification error: " + t.getMessage(), t);
        }
    }

    // ==========================================
    // CIRI 4: PENGESAN PAPAN KERATAN (CLIPBOARD SNIFFER)
    // ==========================================

    private static final String CLIPBOARD_ALERT_CHANNEL_ID = "kantoi_clipboard_alerts";
    private static final int CLIPBOARD_ALERT_NOTIFICATION_ID = 9006;

    public void handleClipboardText(final Context context, final String text) {
        if (context == null || text == null || text.trim().isEmpty()) return;
        try {
            // 1. Check for 10-16 digit bank account candidate
            Pattern bankPattern = Pattern.compile("\\b\\d{10,16}\\b");
            Matcher bm = bankPattern.matcher(text);
            if (bm.find()) {
                final String bankCandidate = bm.group();
                final ScammerThreat threat = checkBankThreat(bankCandidate);
                if (threat != null) {
                    Log.w(TAG, "\uD83D\uDEA8 KANTOI Clipboard: Confirmed Mule Bank Account copied: " + bankCandidate);
                    showClipboardMuleAlert(context, bankCandidate, threat);
                    return;
                } else {
                    showClipboardBankHelper(context, bankCandidate);
                    return;
                }
            }

            // 2. Check for Malaysian phone number candidate
            Pattern phonePattern = Pattern.compile("\\b(?:\\+?60|0)1[0-9]-?[0-9]{7,8}\\b");
            Matcher pm = phonePattern.matcher(text);
            if (pm.find()) {
                final String phoneCandidate = pm.group();
                final String norm = normalizeToLocal(phoneCandidate);
                final ScammerThreat threat = checkThreat(norm);
                if (threat != null) {
                    Log.w(TAG, "\uD83D\uDEA8 KANTOI Clipboard: Confirmed Scammer Phone copied: " + norm);
                    showClipboardScammerAlert(context, norm, threat);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "handleClipboardText error: " + t.getMessage(), t);
        }
    }

    public void showClipboardMuleAlert(Context context, String bankAcc, ScammerThreat threat) {
        if (context == null || threat == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CLIPBOARD_ALERT_CHANNEL_ID,
                    "Kantoi Papan Keratan (Clipboard)",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Amaran apabila akaun bank keldai dikesan disalin di WhatsApp/FB");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 150, 250, 150, 500});
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                pi = PendingIntent.getActivity(
                    context, 103, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            String title = "\uD83D\uDEA8 KANTOI! AKAUN BANK KELDAI DISALIN";
            String msg = "Akaun [" + bankAcc + "] milik suspek: " + threat.suspectName + " (" + threat.riskTag + ")!";

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, CLIPBOARD_ALERT_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_sys_warning)
              .setContentTitle(title)
              .setContentText(msg)
              .setStyle(new Notification.BigTextStyle().bigText(
                  "🚨 AMARAN KANTOI: AKAUN BANK KELDAI DISALIN!\n\n" +
                  "No. Akaun: " + bankAcc + "\n" +
                  "Suspek: " + threat.suspectName + "\n" +
                  "Kategori: " + threat.riskTag + " (" + threat.source + ")\n\n" +
                  "⚠️ JANGAN PINDAHKAN SEBARANG WANG! Akaun ini telah disahkan terlibat dalam penipuan."
              ))
              .setPriority(Notification.PRIORITY_MAX)
              .setAutoCancel(true);

            if (pi != null) nb.setContentIntent(pi);

            nm.notify(CLIPBOARD_ALERT_NOTIFICATION_ID, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showClipboardMuleAlert error: " + t.getMessage(), t);
        }
    }

    public void showClipboardBankHelper(Context context, String bankAcc) {
        if (context == null || bankAcc == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CLIPBOARD_ALERT_CHANNEL_ID,
                    "Kantoi Papan Keratan (Clipboard)",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                openApp.putExtra("extra_check_bank", bankAcc);
                pi = PendingIntent.getActivity(
                    context, 104, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, CLIPBOARD_ALERT_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_notify_chat)
              .setContentTitle("🏛️ No. Akaun Bank Disalin: " + bankAcc)
              .setContentText("Tekan untuk membuat semakan rekod di SemakMule CCID PDRM (1-Klik).")
              .setPriority(Notification.PRIORITY_DEFAULT)
              .setAutoCancel(true);

            if (pi != null) nb.setContentIntent(pi);

            nm.notify(CLIPBOARD_ALERT_NOTIFICATION_ID + 1, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showClipboardBankHelper error: " + t.getMessage(), t);
        }
    }

    public void showClipboardScammerAlert(Context context, String phone, ScammerThreat threat) {
        if (context == null || threat == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CLIPBOARD_ALERT_CHANNEL_ID,
                    "Kantoi Papan Keratan (Clipboard)",
                    NotificationManager.IMPORTANCE_HIGH
                );
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                pi = PendingIntent.getActivity(
                    context, 105, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, CLIPBOARD_ALERT_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_sys_warning)
              .setContentTitle("\uD83D\uDEA8 KANTOI! NOMBOR SCAMMER DISALIN")
              .setContentText("Nombor [" + phone + "] adalah milik: " + threat.suspectName + " (" + threat.riskTag + ")!")
              .setPriority(Notification.PRIORITY_MAX)
              .setAutoCancel(true);

            if (pi != null) nb.setContentIntent(pi);

            nm.notify(CLIPBOARD_ALERT_NOTIFICATION_ID + 2, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "showClipboardScammerAlert error: " + t.getMessage(), t);
        }
    }

    // ==========================================
    // CIRI 5: KEMASKINI ATAS TALIAN TELEGRAM SYNC
    // ==========================================

    public static class TelegramSyncResult {
        public boolean success = false;
        public int addedCount = 0;
        public int totalCount = 0;
        public String statusMessage = "";
    }

    public TelegramSyncResult syncFromTelegram(String channelName) {
        TelegramSyncResult result = new TelegramSyncResult();
        if (channelName == null || channelName.trim().isEmpty()) {
            channelName = "kantoiradar";
        }
        channelName = channelName.replace("@", "").trim();
        String urlStr = "https://t.me/s/" + channelName;

        try {
            Log.i(TAG, "Fetching Telegram updates from: " + urlStr);
            URL url = new URL(urlStr);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

            int code = conn.getResponseCode();
            if (code != 200) {
                result.statusMessage = "Gagal menghubungi saluran Telegram @" + channelName + " (HTTP " + code + "). Sila semak sambungan internet anda.";
                return result;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line).append("\n");
            }
            reader.close();

            String pageContent = html.toString();
            Pattern msgPattern = Pattern.compile("<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
            Matcher matcher = msgPattern.matcher(pageContent);

            int inserted = 0;
            int newlyAdded = 0;
            if (mDb != null && mDb.isOpen()) {
                mDb.beginTransaction();
                try {
                    while (matcher.find()) {
                        String rawMsg = matcher.group(1);
                        if (rawMsg == null) continue;

                        String clean = rawMsg.replaceAll("(?i)<br\\s*/?>", "\n")
                                             .replaceAll("<[^>]+>", "")
                                             .replace("&amp;", "&")
                                             .replace("&quot;", "\"")
                                             .replace("&#39;", "'")
                                             .replace("&lt;", "<")
                                             .replace("&gt;", ">");

                        if (clean.contains("#KANTOI_UPDATE") || clean.contains("#UPDATE")) {
                            String[] lines = clean.split("\n");
                            for (String l : lines) {
                                l = l.trim();
                                if (l.isEmpty() || l.startsWith("#")) continue;

                                String[] parts = l.split("\\|");
                                if (parts.length >= 2) {
                                    String phone = parts[0].trim();
                                    String norm = normalizeToLocal(phone);
                                    if (norm.length() >= 8) {
                                        String name = parts[1].trim();
                                        String tag = (parts.length > 2) ? parts[2].trim() : "TELEGRAM_SYNC";
                                        String bank = (parts.length > 3) ? parts[3].trim() : "";

                                        boolean isNew = true;
                                        Cursor c = null;
                                        try {
                                            c = mDb.rawQuery("SELECT 1 FROM threat_numbers WHERE phone_norm = ? LIMIT 1", new String[]{norm});
                                            if (c != null && c.moveToFirst()) {
                                                isNew = false;
                                            }
                                        } catch (Throwable ignored) {
                                        } finally {
                                            if (c != null) {
                                                try { c.close(); } catch (Throwable ignored) {}
                                            }
                                        }

                                        ContentValues cv = new ContentValues();
                                        cv.put("phone_norm", norm);
                                        cv.put("profile_id", 9500 + inserted);
                                        cv.put("suspect_name", name);
                                        cv.put("all_phones", phone);
                                        cv.put("bank_accounts", bank);
                                        cv.put("ic_number", "");
                                        cv.put("address", "");
                                        cv.put("evidence_links", "https://t.me/" + channelName);
                                        cv.put("risk_tag", tag);
                                        cv.put("source", "Telegram @" + channelName);

                                        mDb.insertWithOnConflict("threat_numbers", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                                        inserted++;
                                        if (isNew) {
                                            newlyAdded++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    mDb.setTransactionSuccessful();
                } finally {
                    mDb.endTransaction();
                }
            }

            if (newlyAdded > 0 || inserted > 0) {
                loadMemoryCache();
            }

            result.success = true;
            result.addedCount = newlyAdded;
            result.totalCount = getThreatCount();
            if (newlyAdded > 0) {
                result.statusMessage = "✅ Berjaya! " + newlyAdded + " rekod scammer baharu telah disegerakkan daripada saluran @" + channelName + ".";
            } else {
                result.statusMessage = "✅ Pangkalan data anda telah terkini! Saluran @" + channelName + " telah disemak dan tiada rekod baharu ditemui (" + inserted + " rekod disahkan).";
            }
        } catch (Throwable t) {
            Log.e(TAG, "syncFromTelegram error: " + t.getMessage(), t);
            result.statusMessage = "Ralat sambungan: " + t.getMessage();
        }
        return result;
    }

    public void showDatabaseUpdatedNotification(Context context, int newRecordsCount, String channelName) {
        if (context == null || newRecordsCount <= 0) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    OTA_SYNC_CHANNEL_ID,
                    "Kantoi Kemaskini Pangkalan Data (OTA)",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Notifikasi apabila pangkalan data scammer baharu berjaya disegerakkan secara automatik");
                nm.createNotificationChannel(channel);
            }

            Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent pi = null;
            if (openApp != null) {
                openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                openApp.putExtra("extra_open_defense_tab", true);
                pi = PendingIntent.getActivity(
                    context, 106, openApp,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
                );
            }

            String title = "🛡️ Pangkalan Data KANTOI Dikemaskini!";
            String shortMsg = newRecordsCount + " rekod scammer baharu berjaya dilindungi secara automatik.";
            String bigMsg = "Sistem risikan KANTOI telah mengesan dan menyegerakkan " + newRecordsCount + 
                            " nombor penipu baharu dari saluran komuniti @" + (channelName != null ? channelName : "kantoiradar") + 
                            " ke peranti anda.\n\n" +
                            "💡 Bantu lindungi komuniti: Laporkan sebarang nombor telefon atau akaun bank scammer (secara teks atau tangkap layar gambar) ke @crowdcallerbot di Telegram.";

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(context, OTA_SYNC_CHANNEL_ID)
                    : new Notification.Builder(context);

            nb.setSmallIcon(android.R.drawable.stat_notify_sync)
              .setContentTitle(title)
              .setContentText(shortMsg)
              .setStyle(new Notification.BigTextStyle().bigText(bigMsg))
              .setPriority(Notification.PRIORITY_DEFAULT)
              .setAutoCancel(true);

            if (pi != null) nb.setContentIntent(pi);

            nm.notify(OTA_SYNC_NOTIFICATION_ID, nb.build());
            Log.i(TAG, "showDatabaseUpdatedNotification: " + newRecordsCount + " new threats notified.");
        } catch (Throwable t) {
            Log.e(TAG, "showDatabaseUpdatedNotification error: " + t.getMessage(), t);
        }
    }

    public void checkAndAutoSyncTelegramAsync(final Context context, final boolean forceCheck) {
        if (context == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = context.getSharedPreferences(PREF_OTA_NAME, Context.MODE_PRIVATE);
                    long lastSync = prefs.getLong(PREF_KEY_LAST_SYNC, 0L);
                    long now = System.currentTimeMillis();

                    if (!forceCheck && (now - lastSync < MIN_AUTO_SYNC_INTERVAL_MS)) {
                        Log.d(TAG, "Auto-sync ditangguhkan: Semakan terakhir dibuat " + ((now - lastSync) / 60000) + " minit lepas.");
                        return;
                    }

                    Log.i(TAG, "Menjalankan auto-sync latar belakang dari @kantoiradar...");
                    TelegramSyncResult res = syncFromTelegram("kantoiradar");

                    // Rekod masa semakan terkini
                    prefs.edit().putLong(PREF_KEY_LAST_SYNC, now).apply();

                    if (res != null && res.success && res.addedCount > 0) {
                        Log.i(TAG, "Auto-sync selesai: " + res.addedCount + " rekod baru dikesan. Memaparkan notifikasi...");
                        showDatabaseUpdatedNotification(context, res.addedCount, "kantoiradar");
                    } else {
                        Log.i(TAG, "Auto-sync selesai: Tiada rekod baharu.");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "checkAndAutoSyncTelegramAsync error: " + t.getMessage(), t);
                }
            }
        }).start();
    }
}



