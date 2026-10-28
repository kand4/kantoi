package com.kantoi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * KantoiCounterStrikeEngine:
 * Enjin Tindak Balas Hibrid & Rawak terhadap Scammer & Panggilan Spoofed:
 * - Taktik A: PDRM Psychological Trap (Siren & Amaran Rasmi Siber PDRM)
 * - Taktik B: AI Voice Honeypot (Persona Atuk Pekak / Mak Cik Blur pembakar masa & kos VoIP)
 * - Taktik C: Silent Forensic Telemetry Relay (Ekstrak & Simpan Log Bukti Digital Masa Nyata)
 * - Mod Ujian Simulasi (Sandbox): Ujian selamat menggunakan nombor rakan
 */
public class KantoiCounterStrikeEngine {
    private static final String TAG = "KantoiCounterStrike";
    private static final String PREF_NAME = "kantoi_counter_strike_prefs";

    public static final String KEY_ENABLED = "cs_enabled";
    public static final String KEY_MODE = "cs_mode";
    public static final String KEY_PERSONA = "cs_persona";
    public static final String KEY_AUTO_ANSWER = "cs_auto_answer";
    public static final String KEY_TEST_NUMBER = "cs_test_number";
    public static final String KEY_TELEMETRY_LOGS = "cs_telemetry_logs";

    public static final int MODE_HYBRID_RANDOM = 0; // Hibrid Pintar & Rawak (Cadangan)
    public static final int MODE_TACTIC_A_PDRM = 1;  // Taktik A: Amaran Siber PDRM & Siren
    public static final int MODE_TACTIC_B_HONEYPOT = 2; // Taktik B: AI Voice Honeypot
    public static final int MODE_TACTIC_C_SILENT = 3;   // Taktik C: Senyap & Rekod Forensik

    public static final int PERSONA_ATUK_PEKAK = 0;
    public static final int PERSONA_MAK_CIK_BLUR = 1;
    public static final int PERSONA_ABANG_RUNCIT = 2;

    private static volatile KantoiCounterStrikeEngine sInstance;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Random mRandom = new Random();

    private TextToSpeech mTts;
    private boolean mTtsReady = false;
    private boolean mIsSpeaking = false;
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
        initTts();
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

    private void initTts() {
        try {
            mTts = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && mTts != null) {
                        int res = mTts.setLanguage(new Locale("ms", "MY"));
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            res = mTts.setLanguage(new Locale("in", "ID"));
                            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                mTts.setLanguage(Locale.getDefault());
                            }
                        }
                        mTts.setPitch(1.0f);
                        mTts.setSpeechRate(0.95f);
                        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                mIsSpeaking = true;
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                mIsSpeaking = false;
                            }

                            @Override
                            public void onError(String utteranceId) {
                                mIsSpeaking = false;
                            }
                        });
                        mTtsReady = true;
                        Log.i(TAG, "TextToSpeech initialized successfully for Counter-Strike.");
                    } else {
                        Log.w(TAG, "TextToSpeech init failed with status: " + status);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Error initializing TextToSpeech: " + t.getMessage(), t);
        }
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public int getMode() {
        return mPrefs.getInt(KEY_MODE, MODE_HYBRID_RANDOM);
    }

    public void setMode(int mode) {
        mPrefs.edit().putInt(KEY_MODE, mode).apply();
    }

    public int getPersona() {
        return mPrefs.getInt(KEY_PERSONA, PERSONA_ATUK_PEKAK);
    }

    public void setPersona(int persona) {
        mPrefs.edit().putInt(KEY_PERSONA, persona).apply();
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
     * Nombor ini akan dimasukkan sebagai sasaran ancaman dengan tag TEST_SIMULATION
     * supaya KANTOI memintasnya walaupun nombor rakan tersimpan dalam buku kenalan telefon.
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
                "Ujian simulasi fungsi Counter-Strike KANTOI. Nombor ini dipintas khas untuk menguji Taktik A, B, dan C.",
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
     * Tentukan taktik mana yang akan dilancarkan berdasarkan tetapan dan jenis ancaman.
     */
    public int resolveTacticForThreat(KantoiThreatEngine.ScammerThreat threat) {
        int configuredMode = getMode();
        if (configuredMode == MODE_TACTIC_A_PDRM) return MODE_TACTIC_A_PDRM;
        if (configuredMode == MODE_TACTIC_B_HONEYPOT) return MODE_TACTIC_B_HONEYPOT;
        if (configuredMode == MODE_TACTIC_C_SILENT) return MODE_TACTIC_C_SILENT;

        // MODE_HYBRID_RANDOM (Logik Pintar & Dadu Digital)
        if (threat != null) {
            // Jika scammer menyamar Hotline Rasmi (Inbound-Only): 85% peluang Taktik A (PDRM Audio Trap)
            if ("SPOOFED_INBOUND_HOTLINE".equals(threat.riskTag)) {
                return (mRandom.nextInt(100) < 85) ? MODE_TACTIC_A_PDRM : MODE_TACTIC_B_HONEYPOT;
            }
            // Jika nombor ujian atau penipuan biasa: 50% A, 50% B
            return (mRandom.nextInt(100) < 50) ? MODE_TACTIC_A_PDRM : MODE_TACTIC_B_HONEYPOT;
        }
        return MODE_TACTIC_A_PDRM;
    }

    /**
     * Lancarkan protokol Counter-Strike secara langsung.
     */
    public void executeCounterStrike(final String phone, final KantoiThreatEngine.ScammerThreat threat, final CounterStrikeListener listener) {
        mActiveListener = listener;
        final int tactic = resolveTacticForThreat(threat);
        final String tacticName = getTacticName(tactic);

        Log.i(TAG, "⚡ MELANCARKAN COUNTER-STRIKE! Taktik: " + tacticName + " pada sasaran: " + phone);

        // 1. TONGGAK C: Rekod Telemetri Forensik Senyap (Sentiasa Aktif 100%)
        recordTelemetryIncident(phone, (threat != null ? threat.suspectName : "Suspek Scammer"), (threat != null ? threat.riskTag : "THREAT"), tacticName);

        if (listener != null) {
            listener.onTacticStarted(tactic, tacticName);
        }

        if (tactic == MODE_TACTIC_C_SILENT) {
            if (listener != null) listener.onTacticFinished();
            return;
        }

        if (tactic == MODE_TACTIC_A_PDRM) {
            executeTacticA_PdrmTrap(listener);
        } else if (tactic == MODE_TACTIC_B_HONEYPOT) {
            executeTacticB_Honeypot(listener);
        }
    }

    private void executeTacticA_PdrmTrap(final CounterStrikeListener listener) {
        // Mainkan siren / radar tone permulaan
        playRadarToneSequence();

        final String warningScript =
                "AMARAN KESELAMATAN KANTOI: Panggilan ini telah dipintas oleh sistem pertahanan siber KANTOI. " +
                "Nombor ini disahkan memalsukan identiti rasmi atau disenaraihitamkan. " +
                "Alamat isyarat panggilan dan audio anda kini sedang direkodkan untuk siasatan jenayah siber PDRM " +
                "di bawah Seksyen 420 Kanun Keseksaan. Talian anda kini sedang dijejak.";

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                speakText(warningScript, 1, 1, new Runnable() {
                    @Override
                    public void run() {
                        playRadarToneSequence();
                        if (listener != null) listener.onTacticFinished();
                    }
                });
                if (listener != null) {
                    listener.onSpeechSpoken(warningScript, 1, 1);
                }
            }
        }, 800);
    }

    private void executeTacticB_Honeypot(final CounterStrikeListener listener) {
        int persona = getPersona();
        final List<String> dialogs = getDialogsForPersona(persona);

        runDialogSequence(dialogs, 0, listener);
    }

    private void runDialogSequence(final List<String> dialogs, final int index, final CounterStrikeListener listener) {
        if (index >= dialogs.size()) {
            if (listener != null) listener.onTacticFinished();
            return;
        }

        final String line = dialogs.get(index);
        speakText(line, index + 1, dialogs.size(), new Runnable() {
            @Override
            public void run() {
                // Jeda masa (scammer mendengar atau bercakap) sebelum watak membalas semula
                long pauseDelay = 3500L + (mRandom.nextInt(2000));
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runDialogSequence(dialogs, index + 1, listener);
                    }
                }, pauseDelay);
            }
        });

        if (listener != null) {
            listener.onSpeechSpoken(line, index + 1, dialogs.size());
        }
    }

    private List<String> getDialogsForPersona(int persona) {
        List<String> list = new ArrayList<String>();
        if (persona == PERSONA_MAK_CIK_BLUR) {
            list.add("Helo? Ya siapa ni ya? LHDN ke? Mak cik tengah goreng pisang kat dapur ni ha...");
            list.add("Sekejap ya encik, api dapur tengah marak... encik kata akaun mak cik kena beku ke tadi? Kenapa beku, letak dalam peti ais ke?");
            list.add("Akaun yang mana satu ya encik? Buku akaun hijau ke buku akaun merah? Mak cik simpan bawah bantal bilik tidur...");
            list.add("Apa dia encik? Kena transfer duit jaminan? Boleh bayar guna baucar pasar malam tak? Cucu mak cik ada bagi baucar semalam...");
            list.add("Encik jangan marah-marah, mak cik dah tua... telinga mak cik ni kadang-kadang dengar kadang-kadang tak dengar...");
        } else if (persona == PERSONA_ABANG_RUNCIT) {
            list.add("Helo kawan! Ini kedai runcit Ah Chai... lu mau order beras ke minyak masak?");
            list.add("Aiyaa, apa saman polis trafik? Ini nombor motor delivery la bos, mana ada saman punya!");
            list.add("Lu cakap kuat sikit, lori ais baru sampai ini macam bising la kawan!");
            list.add("Mau transfer duit mana ada duit kawan! Hari-hari orang beli telur hutang, lu mau tolong kutip hutang ke?");
        } else {
            // PERSONA_ATUK_PEKAK (Default)
            list.add("Helo? Ya siapa tu? Maybank ke? Cucu atuk beli keropok lekor ke apa tadi?");
            list.add("Aduh sekejap ya cu, cakap kuat sikit... Atuk nak pergi cari cermin mata atas peti ais sekejap... tunggu ya cu...");
            list.add("Apa dia cu? Waran tangkap mahkamah? Siapa nak tangkap atuk ni? Atuk bayar zakat tiap-tiap tahun kat surau!");
            list.add("Nombor TAC apa cu? Sekejap atuk cari pen... eh mana pen atuk ni... tunggu sekejap ya cu jangan letak telefon...");
            list.add("Helo cu? Masih ada kat situ ke? Pen atuk tak ada dakwat la cu, atuk pergi cari pensel kejap ya...");
        }
        return list;
    }

    private void speakText(String text, int seq, int total, final Runnable onDone) {
        if (mTtsReady && mTts != null) {
            try {
                mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cs_utt_" + System.currentTimeMillis());
            } catch (Throwable t) {
                Log.e(TAG, "TTS speak error: " + t.getMessage());
            }
        }
        // Anggarkan masa bercakap berasaskan kepanjangan ayat (purata ~70ms per aksara)
        long duration = Math.max(2200L, text.length() * 65L);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (onDone != null) onDone.run();
            }
        }, duration);
    }

    private void playRadarToneSequence() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ToneGenerator toneGen = null;
                try {
                    toneGen = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90);
                    for (int i = 0; i < 3; i++) {
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                        Thread.sleep(200);
                    }
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 400);
                } catch (Throwable ignored) {
                } finally {
                    if (toneGen != null) {
                        try {
                            toneGen.release();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }).start();
    }

    public String getTacticName(int tactic) {
        switch (tactic) {
            case MODE_TACTIC_A_PDRM: return "🚨 Taktik A: Amaran Siber PDRM";
            case MODE_TACTIC_B_HONEYPOT: return "👴 Taktik B: AI Voice Honeypot";
            case MODE_TACTIC_C_SILENT: return "📡 Taktik C: Telemetri Senyap";
            default: return "🎲 Hibrid Pintar & Rawak";
        }
    }

    public String getPersonaName(int persona) {
        switch (persona) {
            case PERSONA_MAK_CIK_BLUR: return "Mak Cik Blur Goreng Pisang";
            case PERSONA_ABANG_RUNCIT: return "Abang Kedai Runcit Salah Nombor";
            default: return "Atuk Pekak Cari Cermin Mata";
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
        if (mTts != null) {
            try {
                mTts.stop();
            } catch (Throwable ignored) {}
        }
        mIsSpeaking = false;
    }
}
