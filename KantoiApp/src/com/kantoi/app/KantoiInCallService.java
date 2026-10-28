package com.kantoi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;

/**
 * KantoiInCallService: Servis rasmi android.telecom.InCallService
 * Membolehkan KANTOI berfungsi sebagai Aplikasi Telefon Lalai (Default Phone / Dialer App)
 * setaraf Truecaller / Google Phone untuk mengendalikan panggilan:
 * - Sebelum masuk (Screening)
 * - Sedang masuk (In-Call Overlay / Lock screen fullScreenIntent)
 * - Selepas masuk (After-Call Summary Dialog)
 */
public class KantoiInCallService extends InCallService {
    private static final String TAG = "KantoiInCallService";
    public static final String CALL_CHANNEL_ID = "kantoi_active_call_channel";
    public static final int CALL_NOTIFICATION_ID = 8801;

    private static volatile Call sCurrentCall = null;
    private static volatile KantoiInCallService sInstance = null;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        createCallNotificationChannel();
        Log.i(TAG, "KantoiInCallService created.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        sCurrentCall = null;
        Log.i(TAG, "KantoiInCallService destroyed.");
    }

    public static Call getCurrentCall() {
        return sCurrentCall;
    }

    public static KantoiInCallService getInstance() {
        return sInstance;
    }

    /**
     * Jawab panggilan aktif secara terus dari GUI IncomingCallActivity
     */
    public static boolean answerCall() {
        try {
            if (sCurrentCall != null) {
                sCurrentCall.answer(VideoProfile.STATE_AUDIO_ONLY);
                Log.i(TAG, "Panggilan berjaya dijawab melalui InCallService.");
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Ralat answerCall: " + t.getMessage(), t);
        }
        return false;
    }

    /**
     * Tolak panggilan atau tamatkan panggilan secara terus dari GUI
     */
    public static boolean rejectCall() {
        try {
            if (sCurrentCall != null) {
                if (sCurrentCall.getState() == Call.STATE_RINGING) {
                    sCurrentCall.reject(false, null);
                    Log.i(TAG, "Panggilan berjaya ditolak (reject) melalui InCallService.");
                } else {
                    sCurrentCall.disconnect();
                    Log.i(TAG, "Panggilan berjaya diputuskan (disconnect) melalui InCallService.");
                }
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Ralat rejectCall: " + t.getMessage(), t);
        }
        return false;
    }

    public static boolean disconnectCall() {
        return rejectCall();
    }

    public static boolean setSpeaker(boolean on) {
        if (sInstance != null) {
            try {
                sInstance.setAudioRoute(on ? android.telecom.CallAudioState.ROUTE_SPEAKER : android.telecom.CallAudioState.ROUTE_EARPIECE);
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "Error setting speaker: " + t.getMessage());
            }
        }
        return false;
    }

    public static boolean setMute(boolean mute) {
        if (sInstance != null) {
            try {
                sInstance.setMuted(mute);
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "Error setting mute: " + t.getMessage());
            }
        }
        return false;
    }

    public static void playDtmf(char digit) {
        if (sCurrentCall != null) {
            try {
                sCurrentCall.playDtmfTone(digit);
                sCurrentCall.stopDtmfTone();
            } catch (Throwable ignored) {}
        }
    }

    public static void playDtmfToneBurst(final char digit, final long durationMs) {
        if (sCurrentCall != null) {
            try {
                sCurrentCall.playDtmfTone(digit);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (sCurrentCall != null) sCurrentCall.stopDtmfTone();
                        } catch (Throwable ignored) {}
                    }
                }, durationMs);
            } catch (Throwable t) {
                Log.e(TAG, "Error playing DTMF burst: " + t.getMessage());
            }
        }
    }

    @Override
    public void onCallAdded(final Call call) {
        super.onCallAdded(call);
        if (call == null) return;
        sCurrentCall = call;

        final String rawNumber = extractPhoneNumber(call);
        Log.i(TAG, "=== PANGGILAN BARU DITERIMA (onCallAdded) ===");
        Log.i(TAG, "Nombor: " + rawNumber + ", State: " + call.getState());

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call c, int state) {
                super.onStateChanged(c, state);
                Log.i(TAG, "Call State Changed: " + state);

                if (state == Call.STATE_DISCONNECTED) {
                    dismissCallNotification();
                    try {
                        sendBroadcast(new Intent("com.kantoi.app.ACTION_CALL_ENDED"));
                    } catch (Throwable ignored) {}

                    // Panggilan ditamatkan -> Lancarkan GUI Selepas Panggilan (AfterCallActivity)
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            launchAfterCallActivity(rawNumber);
                        }
                    }, 250);
                    sCurrentCall = null;
                } else if (state == Call.STATE_ACTIVE) {
                    dismissCallNotification();
                    launchActiveCallActivity(rawNumber);
                }
            }
        });

        // Jika panggilan sedang berdering (INCOMING / RINGING)
        if (call.getState() == Call.STATE_RINGING) {
            handleIncomingCall(rawNumber);
        } else if (call.getState() == Call.STATE_DIALING || call.getState() == Call.STATE_CONNECTING || call.getState() == Call.STATE_ACTIVE) {
            launchActiveCallActivity(rawNumber);
        }
    }

    private void launchActiveCallActivity(String phoneNumber) {
        try {
            Intent activeIntent = new Intent(this, ActiveCallActivity.class);
            activeIntent.putExtra(ActiveCallActivity.EXTRA_PHONE_NUMBER, phoneNumber);
            activeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(activeIntent);
        } catch (Throwable t) {
            Log.e(TAG, "Gagal melancarkan ActiveCallActivity: " + t.getMessage());
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.i(TAG, "onCallRemoved");
        dismissCallNotification();
        try {
            sendBroadcast(new Intent("com.kantoi.app.ACTION_CALL_ENDED"));
        } catch (Throwable ignored) {}
        if (sCurrentCall == call) {
            sCurrentCall = null;
        }

        // Kemaskini lencana notifikasi pada ikon launcher (jika ada panggilan terlepas)
        try {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    KantoiBadgeManager.updateBadge(KantoiInCallService.this);
                }
            }, 1200);
        } catch (Throwable ignored) {}
    }

    private void handleIncomingCall(String rawNumber) {
        // 1. Tampilkan notifikasi panggilan masuk berkeutamaan tinggi (fullScreenIntent)
        // Ini memastikan skrin amaran KANTOI muncul serta-merta walaupun skrin terkunci!
        showIncomingCallNotification(rawNumber);

        // 2. Lancarkan aktiviti IncomingCallActivity secara terus
        try {
            Intent inCallIntent = new Intent(this, IncomingCallActivity.class);
            inCallIntent.putExtra(IncomingCallActivity.EXTRA_PHONE_NUMBER, rawNumber);
            inCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(inCallIntent);
        } catch (Throwable t) {
            Log.e(TAG, "Gagal melancarkan IncomingCallActivity terus: " + t.getMessage());
        }

        // Panggilan masuk dikendalikan sepenuhnya secara profesional oleh IncomingCallActivity
        // Tanpa paparan overlay WindowManager yang bertindih
    }

    private void launchAfterCallActivity(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return;
        try {
            Intent afterIntent = new Intent(this, AfterCallActivity.class);
            afterIntent.putExtra(AfterCallActivity.EXTRA_PHONE_NUMBER, phoneNumber);
            afterIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(afterIntent);
        } catch (Throwable t) {
            Log.e(TAG, "Gagal melancarkan AfterCallActivity: " + t.getMessage());
        }
    }

    private String extractPhoneNumber(Call call) {
        try {
            if (call != null && call.getDetails() != null && call.getDetails().getHandle() != null) {
                return call.getDetails().getHandle().getSchemeSpecificPart();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private void createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                    CALL_CHANNEL_ID,
                    "Panggilan Masuk KANTOI",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Paparan Caller ID dan amaran scammer semasa panggilan masuk");
                channel.enableVibration(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.setBypassDnd(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void showIncomingCallNotification(String phoneNumber) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            KantoiThreatEngine engine = KantoiThreatEngine.getInstance(this);
            final String cleanLocal = KantoiThreatEngine.normalizeToLocal(phoneNumber);
            KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(cleanLocal);
            boolean isSafe = engine.isSafeContact(cleanLocal);
            String contactName = engine.getContactDisplayName(cleanLocal);

            Intent fullScreenIntent = new Intent(this, IncomingCallActivity.class);
            fullScreenIntent.putExtra(IncomingCallActivity.EXTRA_PHONE_NUMBER, phoneNumber);
            fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 991, fullScreenIntent, flags);

            String title;
            String text;

            if (threat != null) {
                title = "🚨 KANTOI! SCAMMER DISAHKAN";
                text = (threat.suspectName != null ? threat.suspectName : "Suspek Penipuan") + " • " + KantoiThreatEngine.formatPhoneNumber(phoneNumber);
            } else if (isSafe && contactName != null) {
                title = "✓ Panggilan: " + contactName;
                text = KantoiThreatEngine.formatPhoneNumber(phoneNumber) + " (Kenalan Selamat)";
            } else {
                title = "📞 Panggilan Masuk";
                text = KantoiThreatEngine.formatPhoneNumber(phoneNumber) + " (Nombor Asing)";
            }

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CALL_CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
                builder.setPriority(Notification.PRIORITY_MAX);
            }

            builder.setSmallIcon(R.drawable.ic_launcher)
                   .setContentTitle(title)
                   .setContentText(text)
                   .setCategory(Notification.CATEGORY_CALL)
                   .setVisibility(Notification.VISIBILITY_PUBLIC)
                   .setContentIntent(fullScreenPendingIntent)
                   .setFullScreenIntent(fullScreenPendingIntent, true)
                   .setAutoCancel(true)
                   .setOngoing(true);

            if (threat != null) {
                builder.setColor(0xFFEF4444);
            } else if (isSafe) {
                builder.setColor(0xFF10B981);
            } else {
                builder.setColor(0xFF0284C7);
            }

            nm.notify(CALL_NOTIFICATION_ID, builder.build());
        } catch (Throwable t) {
            Log.e(TAG, "Ralat showIncomingCallNotification: " + t.getMessage(), t);
        }
    }

    private void dismissCallNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(CALL_NOTIFICATION_ID);
            }
        } catch (Throwable ignored) {}
    }
}
