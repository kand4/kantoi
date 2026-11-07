package com.kantoi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;
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

            SharedPreferences prefs = getSharedPreferences("kantoi_settings", Context.MODE_PRIVATE);
            boolean seniorMode = prefs.getBoolean("pref_senior_mode", false);
            boolean autoBlock = prefs.getBoolean("pref_auto_block", true);

            // CIRI 1 (INSPIRASI SPAMBLOCKER): PENGESANAN CALLER ID SPOOFING (STIR/SHAKEN TELCO ATTESTATION)
            boolean isCallerIdSpoofed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
                try {
                    int verificationStatus = callDetails.getCallerNumberVerificationStatus();
                    if (verificationStatus == Connection.VERIFICATION_STATUS_FAILED) {
                        isCallerIdSpoofed = true;
                        Log.w(TAG, "\uD83D\uDEA8 STIR/SHAKEN FAILED! Panggilan VoIP memalsukan Caller ID: " + rawNumber);
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "Semakan STIR/SHAKEN tidak disokong telco: " + t.getMessage());
                }
            }

            if (isCallerIdSpoofed) {
                final KantoiCounterStrikeEngine csEngine = KantoiCounterStrikeEngine.getInstance(this);
                csEngine.recordTelemetryIncident(rawNumber, "Penyamaran Caller ID (STIR/SHAKEN Gagal)", "VOIP_SPOOFING", "SEKATAN_STIR_SHAKEN_SPOOF");
                engine.showSpoofedCallNotification(this, rawNumber, autoBlock);

                if (autoBlock) {
                    respondBlock(callDetails);
                    Log.i(TAG, "Panggilan VoIP Spoofed berjaya disekat automatik: " + rawNumber);
                    return;
                }
            }

            if (engine.isSafeContact(rawNumber)) {
                Log.i(TAG, "Incoming call is verified native contact: " + rawNumber + ". Allowing safely.");
                respondAllow(callDetails);
                return;
            }

            // CIRI C (INSPIRASI PHONEINFOGA): RADAR KOD NEGARA BERISIKO TINGGI (OVERSEAS SCAM RADAR)
            KantoiCountryRadar.CountryRisk countryRisk = KantoiCountryRadar.checkCountryRisk(rawNumber);
            if (countryRisk != null && countryRisk.isHighRisk) {
                boolean blockOverseas = prefs.getBoolean("pref_block_overseas_scam", true);
                Log.w(TAG, "\uD83D\uDEA8 Panggilan luar negara berisiko dikesan: " + rawNumber + " (" + countryRisk.countryName + ")");

                final KantoiCounterStrikeEngine csEngine = KantoiCounterStrikeEngine.getInstance(this);
                csEngine.recordTelemetryIncident(rawNumber, "Scam Luar Negara (" + countryRisk.countryName + ")", "OVERSEAS_SCAM", "SEKATAN_RADAR_NEGARA");
                engine.showHighRiskCountryNotification(this, rawNumber, countryRisk, blockOverseas);

                if (blockOverseas) {
                    respondBlock(callDetails);
                    Log.i(TAG, "Panggilan antarabangsa berisiko berjaya disekat automatik: " + rawNumber);
                    return;
                }
            }

            KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(rawNumber);

            // CIRI 3: MOD PERLINDUNGAN WARGA EMAS (STRICT WHITELIST)
            if (seniorMode) {
                respondBlock(callDetails);
                if (threat != null) {
                    engine.showScammerCallNotification(this, rawNumber, threat, true);
                    Log.w(TAG, "Senior Mode: Disekat panggilan scammer disahkan: " + rawNumber);
                } else {
                    engine.showSeniorBlockedNotification(this, rawNumber);
                    Log.w(TAG, "Senior Mode: Disekat panggilan nombor asing tidak dikenali: " + rawNumber);
                }
                return;
            }

            if (threat != null) {
                Log.w(TAG, "KANTOI! Panggilan scammer dikesan: " + rawNumber + " (" + threat.suspectName + ")");

                final KantoiCounterStrikeEngine csEngine = KantoiCounterStrikeEngine.getInstance(this);
                if (autoBlock || csEngine.isZeroRingBlockEnabled()) {
                    respondBlock(callDetails);
                    engine.showScammerCallNotification(this, rawNumber, threat, true);

                    csEngine.recordTelemetryIncident(rawNumber, threat.suspectName, threat.riskTag, "SEKATAN_SCREENING_0_SAAT");
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
