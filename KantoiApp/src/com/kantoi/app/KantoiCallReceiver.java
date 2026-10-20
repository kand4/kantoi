package com.kantoi.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

public class KantoiCallReceiver extends BroadcastReceiver {
    private static final String TAG = "KantoiCallReceiver";

    private static volatile boolean sWasRingingOrOffhook = false;
    private static volatile String sLastCallNumber = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        try {
            String action = intent.getAction();
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                if (incomingNumber != null && !incomingNumber.trim().isEmpty()) {
                    sLastCallNumber = incomingNumber;
                }

                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    sWasRingingOrOffhook = true;
                    if (incomingNumber != null && !incomingNumber.trim().isEmpty()) {
                        // 1. Lancarkan Skrin Semasa Menerima Panggilan (In-Call Screen)
                        try {
                            Intent inCallIntent = new Intent(context, IncomingCallActivity.class);
                            inCallIntent.putExtra(IncomingCallActivity.EXTRA_PHONE_NUMBER, incomingNumber);
                            inCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            context.startActivity(inCallIntent);
                        } catch (Throwable t) {
                            Log.e(TAG, "Gagal melancarkan IncomingCallActivity: " + t.getMessage());
                        }

                        // 2. Semak ancaman untuk notifikasi sistem (jika skrin dimatikan)
                        KantoiThreatEngine engine = KantoiThreatEngine.getInstance(context);
                        if (!engine.isSafeContact(incomingNumber)) {
                            KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(incomingNumber);
                            if (threat != null) {
                                engine.showScammerCallNotification(context, incomingNumber, threat, false);
                            }
                        }
                    }
                } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    sWasRingingOrOffhook = true;
                } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    KantoiThreatEngine.removeFloatingAlertBanner();

                    // Kemaskini lencana bilangan panggilan terlepas pada ikon launcher
                    try {
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                KantoiBadgeManager.updateBadge(context);
                            }
                        }, 1200);
                    } catch (Throwable ignored) {}

                    if (sWasRingingOrOffhook && sLastCallNumber != null && !sLastCallNumber.trim().isEmpty()) {
                        String finishedNumber = sLastCallNumber;
                        sWasRingingOrOffhook = false;
                        sLastCallNumber = null;

                        // Lancarkan Skrin Selepas Menerima Panggilan (After-Call Summary Dialog)
                        try {
                            Intent afterCallIntent = new Intent(context, AfterCallActivity.class);
                            afterCallIntent.putExtra(AfterCallActivity.EXTRA_PHONE_NUMBER, finishedNumber);
                            afterCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(afterCallIntent);
                        } catch (Throwable t) {
                            Log.e(TAG, "Gagal melancarkan AfterCallActivity: " + t.getMessage());
                        }
                    } else {
                        sWasRingingOrOffhook = false;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in KantoiCallReceiver: " + t.getMessage(), t);
        }
    }
}

