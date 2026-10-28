package com.kantoi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

public class KantoiScreeningService extends CallScreeningService {
    private static final String TAG = "KantoiScreening";

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails == null) return;
        try {
            Uri handle = callDetails.getHandle();
            String rawNumber = (handle != null) ? handle.getSchemeSpecificPart() : null;
            if (rawNumber == null || rawNumber.isEmpty()) {
                respondAllow(callDetails);
                return;
            }

            Log.i(TAG, "Screening incoming call: " + rawNumber);
            KantoiThreatEngine engine = KantoiThreatEngine.getInstance(this);

            if (engine.isSafeContact(rawNumber)) {
                Log.i(TAG, "Incoming call is verified native contact: " + rawNumber + ". Allowing safely.");
                respondAllow(callDetails);
                return;
            }

            SharedPreferences prefs = getSharedPreferences("kantoi_settings", Context.MODE_PRIVATE);
            boolean seniorMode = prefs.getBoolean("pref_senior_mode", false);
            boolean autoBlock = prefs.getBoolean("pref_auto_block", true);

            KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(rawNumber);

            // CIRI 3: MOD PERLINDUNGAN WARGA EMAS (STRICT WHITELIST)
            if (seniorMode) {
                respondBlock(callDetails);
                if (threat != null) {
                    engine.showScammerCallNotification(this, rawNumber, threat, true);
                    Log.w(TAG, "👴 Senior Mode: Disekat panggilan scammer disahkan: " + rawNumber);
                } else {
                    engine.showSeniorBlockedNotification(this, rawNumber);
                    Log.w(TAG, "👴 Senior Mode: Disekat panggilan nombor asing tidak dikenali: " + rawNumber);
                }
                return;
            }

            if (threat != null) {
                Log.w(TAG, "🚨 KANTOI! Panggilan scammer dikesan: " + rawNumber + " (" + threat.suspectName + ")");

                final KantoiCounterStrikeEngine csEngine = KantoiCounterStrikeEngine.getInstance(this);
                if (autoBlock || csEngine.isZeroRingBlockEnabled()) {
                    respondBlock(callDetails);
                    engine.showScammerCallNotification(this, rawNumber, threat, true);

                    final String fNum = rawNumber;
                    final String fSuspect = threat.suspectName;
                    if (csEngine.isSmsCounterStrikeEnabled()) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                csEngine.sendCounterStrikeSms(fNum, fSuspect);
                            }
                        }).start();
                    }

                    csEngine.recordTelemetryIncident(rawNumber, threat.suspectName, threat.riskTag,
                            csEngine.isSmsCounterStrikeEnabled() ? "SEKATAN_SCREENING_0_SAAT_SMS" : "SEKATAN_SCREENING_0_SAAT");
                    Log.i(TAG, "Call auto-blocked successfully for scammer: " + rawNumber);
                } else {
                    respondAllow(callDetails);
                }
            } else {
                respondAllow(callDetails);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onScreenCall: " + t.getMessage(), t);
            respondAllow(callDetails);
        }
    }

    private void respondAllow(Call.Details callDetails) {
        try {
            CallResponse.Builder response = new CallResponse.Builder();
            response.setDisallowCall(false);
            response.setRejectCall(false);
            response.setSkipCallLog(false);
            response.setSkipNotification(false);
            respondToCall(callDetails, response.build());
        } catch (Throwable ignored) {}
    }

    private void respondBlock(Call.Details callDetails) {
        try {
            CallResponse.Builder response = new CallResponse.Builder();
            response.setDisallowCall(true);
            response.setRejectCall(true);
            response.setSkipCallLog(false);
            response.setSkipNotification(false);
            respondToCall(callDetails, response.build());
        } catch (Throwable ignored) {}
    }
}
