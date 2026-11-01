package com.kantoi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * KantoiCounterStrikeEngine (v1.16.1):
 * Enjin Tindak Balas & Pertahanan Siber Bersih:
 * - Taktik 1 (MODE_TACTIC_B_ACOUSTIC): Letusan Nada DTMF Rangkaian (Call.playDtmfTone) yang sampai
 *   terus ke fon telinga scammer melalui modem telco tanpa sekatan pembatal gema (AEC).
 * - Taktik 2 (MODE_TACTIC_C_SILENT): Telemetri Forensik Senyap (100% rakaman cap jari digital & draf laporan rasmi).
 * - Mod Ujian Simulasi (Sandbox): Ujian pemintasan nombor rakan secara selamat.
 */
public class KantoiCounterStrikeEngine {
    private static final String TAG = "KantoiCounterStrike";
    private static final String PREF_NAME = "kantoi_counter_strike_prefs";

    public static final String KEY_ENABLED = "cs_enabled";
    public static final String KEY_MODE = "cs_mode";
    public static final String KEY_AUTO_ANSWER = "cs_auto_answer";
    public static final String KEY_TEST_NUMBER = "cs_test_number";
    public static final String KEY_TELEMETRY_LOGS = "cs_telemetry_logs";
    public static final String KEY_ZERO_RING_BLOCK = "cs_zero_ring_block";

    // Mod Taktikal Standard (v1.16.1)
    public static final int MODE_HYBRID_RANDOM = 0;    // Lalai: DTMF Acoustic Disruption
    public static final int MODE_TACTIC_A_PDRM = 1;     // Dialias ke Taktik B (DTMF) untuk keserasian tetapan lama
    public static final int MODE_TACTIC_B_ACOUSTIC = 2; // Taktik Utama: Letusan Nada DTMF Pemutus Talian
    public static final int MODE_TACTIC_C_SILENT = 3;   // Taktik Senyap: Telemetri Forensik & Rekod Senyap

    // Alias keserasian
    public static final int MODE_TACTIC_B_HONEYPOT = MODE_TACTIC_B_ACOUSTIC;

    private static volatile KantoiCounterStrikeEngine sInstance;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private CounterStrikeListener mActiveListener = null;

    public interface CounterStrikeListener {
        void onTacticStarted(int tactic, String tacticName);
        void onSpeechSpoken(String text, int sequenceIndex, int totalSequences);
        void onTacticFinished();
    }

    public static class TelemetryLogItem {
        public long timestamp;
        public String phone;
        public String suspectName;
        public String riskTag;
        public String tacticUsed;
        public int durationSeconds;
    }

    private KantoiCounterStrikeEngine(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static KantoiCounterStrikeEngine getInstance(Context context) {
        if (sInstance == null) {
            synchronized (KantoiCounterStrikeEngine.class) {
                if (sInstance == null) {
                    sInstance = new KantoiCounterStrikeEngine(context);
                }
            }
        }
        return sInstance;
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public int getMode() {
        int m = mPrefs.getInt(KEY_MODE, MODE_TACTIC_B_ACOUSTIC);
        if (m == MODE_TACTIC_C_SILENT) return MODE_TACTIC_C_SILENT;
        return MODE_TACTIC_B_ACOUSTIC;
    }

    public void setMode(int mode) {
        mPrefs.edit().putInt(KEY_MODE, mode).apply();
    }

    // Deprecated Persona methods (stubs keserasian)
    public int getPersona() { return 0; }
    public void setPersona(int persona) {}
    public String getPersonaName(int persona) { return "Mod Rasmi Siber"; }

    public boolean isZeroRingBlockEnabled() {
        return mPrefs.getBoolean(KEY_ZERO_RING_BLOCK, true);
    }

    public void setZeroRingBlockEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ZERO_RING_BLOCK, enabled).apply();
    }

    public boolean isAutoAnswerEnabled() {
        return mPrefs.getBoolean(KEY_AUTO_ANSWER, false);
    }

    public void setAutoAnswerEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_AUTO_ANSWER, enabled).apply();
    }

    public String getTestNumber() {
        return mPrefs.getString(KEY_TEST_NUMBER, null);
    }

    /**
     * Daftarkan nombor rakan untuk Ujian Simulasi (Sandbox).
     */
    public synchronized boolean registerTestNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) return false;
        String digits = rawPhone.replaceAll("[^0-9]", "");
        String local = KantoiThreatEngine.normalizeToLocal(digits);
        if (local.length() < 8) return false;

        KantoiThreatEngine engine = KantoiThreatEngine.getInstance(mContext);
        boolean saved = engine.saveThreatLocal(
                local,
                "🧪 SASARAN UJIAN SIMULASI (RAKAN)",
                "Ujian simulasi fungsi Counter-Strike KANTOI. Nombor ini dipintas khas untuk menguji sistem amaran & telemetri.",
                "TEST_SIMULATION",
                0,
                1
        );

        if (saved) {
            mPrefs.edit().putString(KEY_TEST_NUMBER, local).apply();
            Log.i(TAG, "Test simulation number registered: " + local);
            return true;
        }
        return false;
    }

    public synchronized boolean clearTestNumber() {
        String testNum = getTestNumber();
        if (testNum != null && !testNum.isEmpty()) {
            KantoiThreatEngine engine = KantoiThreatEngine.getInstance(mContext);
            engine.deleteThreatLocal(testNum);
            mPrefs.edit().remove(KEY_TEST_NUMBER).apply();
            Log.i(TAG, "Test simulation number cleared: " + testNum);
            return true;
        }
        return false;
    }

    public boolean isTestNumber(String rawPhone) {
        if (rawPhone == null) return false;
        String testNum = getTestNumber();
        if (testNum == null || testNum.isEmpty()) return false;
        String local = KantoiThreatEngine.normalizeToLocal(rawPhone);
        return local.equals(testNum);
    }

    /**
     * Tentukan taktik yang akan dilancarkan.
     */
    public int resolveTacticForThreat(KantoiThreatEngine.ScammerThreat threat) {
        int configuredMode = getMode();
        if (configuredMode == MODE_TACTIC_C_SILENT) {
            return MODE_TACTIC_C_SILENT;
        }
        return MODE_TACTIC_B_ACOUSTIC;
    }

    /**
     * Kaedah penyediaan audio (dikekalkan untuk keserasian panggilan).
     */
    public void prepareAudioForBroadcast() {
        // Diringkaskan: Tiada pengalihan paksa speakerphone yang mengganggu pengguna.
    }

    /**
     * Lancarkan protokol Counter-Strike secara langsung.
     */
    public void executeCounterStrike(final String phone, final KantoiThreatEngine.ScammerThreat threat, final CounterStrikeListener listener) {
        mActiveListener = listener;
        final int tactic = resolveTacticForThreat(threat);
        final String tacticName = getTacticName(tactic);

        Log.i(TAG, "⚡ MELANCARKAN COUNTER-STRIKE! Taktik: " + tacticName + " pada sasaran: " + phone);

        // Rekod Telemetri Forensik Senyap (Sentiasa Aktif 100%)
        recordTelemetryIncident(phone, (threat != null ? threat.suspectName : "Suspek Scammer"), (threat != null ? threat.riskTag : "THREAT"), tacticName);

        if (listener != null) {
            listener.onTacticStarted(tactic, tacticName);
        }

        if (tactic == MODE_TACTIC_C_SILENT) {
            if (listener != null) {
                listener.onSpeechSpoken("📡 Mod Senyap Aktif: Rakaman telemetri forensik direkodkan.", 1, 1);
                listener.onTacticFinished();
            }
            return;
        }

        // Taktik B: Letusan Nada DTMF Berterusan (Acoustic Disruption)
        executeTacticB_AcousticDisruption(listener);
    }

    /**
     * Taktik B: Letusan Nada DTMF Rangkaian Pemutus Talian.
     * Menggunakan API Telecom rasmi (Call.playDtmfTone) yang dihantar terus melalui
     * modem dan isyarat rangkaian telco ke fon telinga scammer, tanpa disekat oleh
     * pembatal gema peranti (AEC).
     */
    private void executeTacticB_AcousticDisruption(final CounterStrikeListener listener) {
        final String statusMsg = "⚡ MEMANJANGKAN NADA DTMF TELCO KE FON KEPALA SCAMMER...";
        if (listener != null) {
            listener.onSpeechSpoken(statusMsg, 1, 1);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Siri nada DTMF frekuensi tinggi yang menusuk pendengaran pemanggil
                    char[] dtmfSequence = new char[]{'9', '1', '#', '0', '*', '7', '4', '#'};
                    for (int i = 0; i < dtmfSequence.length; i++) {
                        KantoiInCallService.playDtmfToneBurst(dtmfSequence[i], 250);
                        Thread.sleep(350);
                    }
                } catch (Throwable ignored) {
                } finally {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onTacticFinished();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Ujian nada amaran setempat (simulasi DTMF melalui pembesar suara peranti untuk pengguna semak).
     */
    public void testAudioWarning(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ToneGenerator toneGen = null;
                try {
                    toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
                    int[] tones = {ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_P, ToneGenerator.TONE_DTMF_0};
                    for (int t : tones) {
                        toneGen.startTone(t, 220);
                        Thread.sleep(300);
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (toneGen != null) {
                        try { toneGen.release(); } catch (Throwable ignored) {}
                    }
                }
            }
        }).start();
    }

    public String getTacticName(int tactic) {
        switch (tactic) {
            case MODE_TACTIC_C_SILENT:
                return "📡 Taktik 2: Telemetri Forensik Senyap";
            case MODE_TACTIC_B_ACOUSTIC:
            case MODE_TACTIC_A_PDRM:
            default:
                return "🔊 Taktik 1: Letusan Nada DTMF Pemutus Talian";
        }
    }

    /**
     * TONGGAK C: Rekod kejadian ke pangkalan log forensik tempatan.
     */
    public synchronized void recordTelemetryIncident(String phone, String name, String riskTag, String tactic) {
        try {
            JSONArray arr;
            String rawLogs = mPrefs.getString(KEY_TELEMETRY_LOGS, "[]");
            try {
                arr = new JSONArray(rawLogs);
            } catch (Throwable ignored) {
                arr = new JSONArray();
            }

            JSONObject obj = new JSONObject();
            obj.put("timestamp", System.currentTimeMillis());
            obj.put("phone", phone);
            obj.put("name", name);
            obj.put("risk_tag", riskTag);
            obj.put("tactic", tactic);

            // Sisip di bahagian atas (paling terkini)
            JSONArray newArr = new JSONArray();
            newArr.put(obj);
            for (int i = 0; i < Math.min(arr.length(), 49); i++) {
                newArr.put(arr.get(i));
            }

            mPrefs.edit().putString(KEY_TELEMETRY_LOGS, newArr.toString()).apply();
            Log.i(TAG, "Telemetry incident recorded successfully for: " + phone);
        } catch (Throwable t) {
            Log.e(TAG, "Error recording telemetry: " + t.getMessage(), t);
        }
    }

    public List<TelemetryLogItem> getRecentTelemetryLogs() {
        List<TelemetryLogItem> list = new ArrayList<TelemetryLogItem>();
        try {
            String raw = mPrefs.getString(KEY_TELEMETRY_LOGS, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                TelemetryLogItem item = new TelemetryLogItem();
                item.timestamp = o.optLong("timestamp", System.currentTimeMillis());
                item.phone = o.optString("phone", "N/A");
                item.suspectName = o.optString("name", "N/A");
                item.riskTag = o.optString("risk_tag", "N/A");
                item.tacticUsed = o.optString("tactic", "N/A");
                list.add(item);
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public String generateTelegramReportText(TelemetryLogItem item) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timeStr = sdf.format(new Date(item.timestamp));
        return "🚨 *[LAPORAN FORENSIK KANTOI COUNTER-STRIKE]*\n\n" +
                "⏰ *Waktu Pintasan:* `" + timeStr + "`\n" +
                "📞 *Nombor Sasaran:* `" + item.phone + "`\n" +
                "👤 *Profil / Samaran:* " + item.suspectName + "\n" +
                "🏷️ *Kategori Ancaman:* `" + item.riskTag + "`\n" +
                "⚡ *Tindak Balas:* " + item.tacticUsed + "\n" +
                "🛡️ *Status:* Berjaya dipintas dan direkodkan oleh KANTOI Defense Matrix.\n\n" +
                "#KantoiDefense #ScamRadarMalaysia #PDRM #CCID";
    }

    public void stopTts() {
        // Safe stub
    }
}
