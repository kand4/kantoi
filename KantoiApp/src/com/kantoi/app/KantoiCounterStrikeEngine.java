package com.kantoi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
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
 * Enjin Tindak Balas Hibrid & Pertahanan Siber terhadap Scammer & Panggilan Spoofed:
 * - Taktik A: PDRM Psychological Cyber Trap (Siren & Amaran Rasmi Siber PDRM Seksyen 420)
 * - Taktik B: Siren Frekuensi Tinggi Pemutus Talian (Acoustic Eardrum Disruption untuk Scammer ber-headset)
 * - Taktik C: Silent Forensic Telemetry Relay (Ekstrak & Simpan Log Bukti Digital Masa Nyata)
 * - Mod Ujian Simulasi (Sandbox): Ujian selamat menggunakan nombor rakan
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

    public static final int MODE_HYBRID_RANDOM = 0;   // Hibrid Pintar (Taktik A & B)
    public static final int MODE_TACTIC_A_PDRM = 1;    // Taktik A: Amaran Rasmi Siber PDRM & Siren (Disyorkan)
    public static final int MODE_TACTIC_B_ACOUSTIC = 2;// Taktik B: Siren Frekuensi Tinggi Pemutus Talian
    public static final int MODE_TACTIC_C_SILENT = 3;  // Taktik C: Senyap & Rekod Forensik

    // Backward-compatibility alias
    public static final int MODE_TACTIC_B_HONEYPOT = MODE_TACTIC_B_ACOUSTIC;

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
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                android.media.AudioAttributes aa = new android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build();
                                mTts.setAudioAttributes(aa);
                            }
                        } catch (Throwable ignored) {}

                        int res = mTts.setLanguage(new Locale("ms", "MY"));
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            res = mTts.setLanguage(new Locale("ms"));
                            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                res = mTts.setLanguage(new Locale("in", "ID"));
                                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    res = mTts.setLanguage(Locale.getDefault());
                                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                        mTts.setLanguage(Locale.US);
                                    }
                                }
                            }
                        }
                        mTts.setPitch(0.92f);      // Nada autoriti, tegas, dan dalam
                        mTts.setSpeechRate(0.85f); // Kelajuan teratur & jelas supaya perkataan tidak terpotong
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
        return mPrefs.getInt(KEY_MODE, MODE_TACTIC_A_PDRM);
    }

    public void setMode(int mode) {
        mPrefs.edit().putInt(KEY_MODE, mode).apply();
    }

    // Deprecated Persona methods maintained as stubs for backward-compatibility
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
        if (configuredMode == MODE_TACTIC_B_ACOUSTIC) return MODE_TACTIC_B_ACOUSTIC;
        if (configuredMode == MODE_TACTIC_C_SILENT) return MODE_TACTIC_C_SILENT;

        // MODE_HYBRID_RANDOM (80% Taktik A Amaran Rasmi, 20% Taktik B Pemutus Talian)
        if (threat != null) {
            if ("SPOOFED_INBOUND_HOTLINE".equals(threat.riskTag)) {
                return MODE_TACTIC_A_PDRM; // Hotline dispoof sentiasa dikenakan Taktik A
            }
            return (mRandom.nextInt(100) < 75) ? MODE_TACTIC_A_PDRM : MODE_TACTIC_B_ACOUSTIC;
        }
        return MODE_TACTIC_A_PDRM;
    }

    /**
     * KUNCI AUDIO UTAMA:
     * Menghidupkan pembesar suara telefon (Speakerphone) dan menetapkan kelantangan MAKSIMUM.
     * Ini membolehkan isyarat audio TTS & Siren memancar keluar dari corong pembesar suara
     * dan ditangkap secara terus oleh mikrofon peranti untuk disalurkan ke talian pemanggil (scammer).
     */
    public void prepareAudioForBroadcast() {
        try {
            // 1. Arahkan servis panggilan rasmi (InCallService) ke SPEAKER jika ada panggilan aktif
            KantoiInCallService.setSpeaker(true);

            // 2. Maksimumkan volum media (STREAM_MUSIC) dan panggilan supaya amaran lantang kedengaran
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0);

                int maxVoice = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0);

                am.setSpeakerphoneOn(true);
            }
        } catch (Throwable t) {
            Log.e(TAG, "prepareAudioForBroadcast error: " + t.getMessage());
        }
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
            if (listener != null) listener.onTacticFinished();
            return;
        }

        // Pastikan laluan audio bersedia ke speaker sebelum memainkan sebarang bunyi
        prepareAudioForBroadcast();

        if (tactic == MODE_TACTIC_A_PDRM) {
            executeTacticA_PdrmTrap(listener);
        } else if (tactic == MODE_TACTIC_B_ACOUSTIC) {
            executeTacticB_AcousticDisruption(listener);
        }
    }

    private void executeTacticA_PdrmTrap(final CounterStrikeListener listener) {
        // 1. Siren permulaan (DTMF Telco Burst + Loud Siren)
        playRadarToneSequence();

        final String warningScript =
                "PERHATIAN: Panggilan ini telah dipintas oleh sistem pertahanan siber KANTOI. " +
                "Nombor ini disahkan memalsukan identiti rasmi dan berada dalam senarai hitam penipuan siber. " +
                "Rakaman audio dan telemetri pemanggil kini sedang disalurkan terus kepada Bahagian Siasatan Jenayah Siber JSJK PDRM " +
                "di bawah Seksyen 420 Kanun Keseksaan. Talian anda kini sedang dijejak dan disekat.";

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
        }, 1200);
    }

    /**
     * Taktik B: Siren Frekuensi Tinggi Pemutus Talian.
     * Menggantikan watak Atuk/Nenek yang robotik dengan siri letusan nada frekuensi tinggi
     * (DTMF rangkaian + Dual-Tone Siren) yang menembusi fon kepala (headset) scammer,
     * memaksa mereka menanggalkan headset dan memutuskan panggilan dengan serta-merta.
     */
    private void executeTacticB_AcousticDisruption(final CounterStrikeListener listener) {
        prepareAudioForBroadcast();

        final String statusMsg = "⚡ MEMANJANGKAN SIREN FREKUENSI TINGGI (GANGGUAN FON KEPALA SCAMMER)...";
        if (listener != null) {
            listener.onSpeechSpoken(statusMsg, 1, 1);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                ToneGenerator toneGen = null;
                try {
                    toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    char[] dtmfSequence = new char[]{'1', '9', '#', '0', '*'};
                    for (int i = 0; i < 5; i++) {
                        // Letuskan nada DTMF terus ke suis telekomunikasi jika dalam talian
                        KantoiInCallService.playDtmfToneBurst(dtmfSequence[i % dtmfSequence.length], 200);
                        toneGen.startTone(ToneGenerator.TONE_DTMF_D, 220);
                        Thread.sleep(260);
                        toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 220);
                        Thread.sleep(260);
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (toneGen != null) {
                        try {
                            toneGen.release();
                        } catch (Throwable ignored) {}
                    }
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

    private void speakText(final String text, final int seq, final int total, final Runnable onDone) {
        prepareAudioForBroadcast();

        if (mTts == null) {
            initTts();
        }

        if (!mTtsReady) {
            Log.w(TAG, "TTS not ready yet, queuing speech retry...");
            mHandler.postDelayed(new Runnable() {
                private int retry = 0;
                @Override
                public void run() {
                    if (mTtsReady && mTts != null) {
                        doSpeak(text, onDone);
                    } else if (retry < 6) {
                        retry++;
                        mHandler.postDelayed(this, 350);
                    } else {
                        Log.e(TAG, "TTS timed out, playing fallback siren...");
                        playRadarToneSequence();
                        if (onDone != null) onDone.run();
                    }
                }
            }, 300);
            return;
        }

        doSpeak(text, onDone);
    }

    private void doSpeak(String text, final Runnable onDone) {
        prepareAudioForBroadcast();
        if (mTts != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    Bundle params = new Bundle();
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                    mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "cs_pdrm_" + System.currentTimeMillis());
                } else {
                    java.util.HashMap<String, String> params = new java.util.HashMap<String, String>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0");
                    mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
                }
            } catch (Throwable t) {
                Log.e(TAG, "TTS speak error: " + t.getMessage(), t);
            }
        }

        long duration = Math.max(3500L, (long) (text.length() * 80L));
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (onDone != null) onDone.run();
            }
        }, duration);
    }

    private void playRadarToneSequence() {
        prepareAudioForBroadcast();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ToneGenerator toneGen = null;
                try {
                    // Letuskan isyarat nada DTMF terus ke talian telco jika dalam panggilan
                    KantoiInCallService.playDtmfToneBurst('*', 250);

                    toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    for (int i = 0; i < 3; i++) {
                        toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 180);
                        Thread.sleep(280);
                    }
                    toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 450);
                    Thread.sleep(500);
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

    public void testAudioWarning(final Context context) {
        prepareAudioForBroadcast();
        playRadarToneSequence();

        final String testScript = "Perhatian: Ini adalah ujian sistem amaran siber KANTOI. Siren frekuensi dan sistem audio bersedia sepenuhnya.";
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                speakText(testScript, 1, 1, new Runnable() {
                    @Override
                    public void run() {
                        playRadarToneSequence();
                    }
                });
            }
        }, 1200);
    }

    public String getTacticName(int tactic) {
        switch (tactic) {
            case MODE_TACTIC_A_PDRM: return "🚨 Taktik A: Amaran Rasmi Siber PDRM";
            case MODE_TACTIC_B_ACOUSTIC: return "🔊 Taktik B: Siren Pemutus Talian";
            case MODE_TACTIC_C_SILENT: return "📡 Taktik C: Telemetri Senyap";
            default: return "🎲 Hibrid Pintar (Taktik A & B)";
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
