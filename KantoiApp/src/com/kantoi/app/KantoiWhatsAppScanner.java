package com.kantoi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pengimbas Senyap Kenalan WhatsApp (WhatsApp Silent Threat Scanner)
 * Mengimbas semua nombor yang wujud / disegerakkan oleh WhatsApp ke dalam Contacts Provider Android
 * disebalik tabir tanpa mengganggu pengguna dan memadankannya dengan pangkalan data scammer KANTOI.
 */
public class KantoiWhatsAppScanner {
    private static final String TAG = "KantoiWhatsAppScanner";
    private static final String PREFS_NAME = "kantoi_whatsapp_prefs";

    public static final String KEY_TOTAL_SCANNED = "wa_total_scanned";
    public static final String KEY_THREATS_COUNT = "wa_threats_count";
    public static final String KEY_LAST_SCAN_TIME = "wa_last_scan_time";
    public static final String KEY_IS_DETECTED = "wa_is_detected";

    public static class WhatsAppThreatMatch {
        public String contactName;
        public String phoneNumber;
        public String suspectName;
        public String riskTag;
        public String details;
    }

    public static class WhatsAppScanResult {
        public int totalWhatsAppContacts = 0;
        public int scammerMatchesCount = 0;
        public boolean isWhatsAppDetected = false;
        public long scanTimestamp = 0;
        public List<WhatsAppThreatMatch> threatsFound = new ArrayList<WhatsAppThreatMatch>();
    }

    /**
     * Jalankan imbasan senyap ke atas semua rekod WhatsApp di dalam Contacts Provider Android
     */
    public static WhatsAppScanResult scanWhatsAppContacts(Context context) {
        WhatsAppScanResult result = new WhatsAppScanResult();
        if (context == null) return result;

        result.scanTimestamp = System.currentTimeMillis();

        if (context.checkCallingOrSelfPermission(android.Manifest.permission.READ_CONTACTS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Kebenaran READ_CONTACTS belum diberikan.");
            return result;
        }

        KantoiThreatEngine engine = KantoiThreatEngine.getInstance(context);
        ContentResolver cr = context.getContentResolver();
        Set<String> processedNumbers = new HashSet<String>();

        try {
            // Imbas Data ContactsContract dengan MIME type WhatsApp (Personal & Business)
            Uri dataUri = ContactsContract.Data.CONTENT_URI;
            String[] projection = new String[] {
                ContactsContract.Data._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.DATA1, // e.g. "60123456789@s.whatsapp.net" or formatted number
                ContactsContract.Data.DATA3, // e.g. "Message +60 12-345 6789"
                ContactsContract.Data.MIMETYPE
            };

            String selection = ContactsContract.Data.MIMETYPE + " LIKE ? OR " 
                             + ContactsContract.Data.MIMETYPE + " LIKE ?";
            String[] selectionArgs = new String[] {
                "%whatsapp%",
                "%whatsapp.w4b%"
            };

            Cursor cursor = cr.query(dataUri, projection, selection, selectionArgs, null);
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                int data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1);
                int data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3);

                while (cursor.moveToNext()) {
                    result.isWhatsAppDetected = true;
                    String displayName = nameIdx >= 0 ? cursor.getString(nameIdx) : "Kenalan WhatsApp";
                    String data1 = data1Idx >= 0 ? cursor.getString(data1Idx) : null;
                    String data3 = data3Idx >= 0 ? cursor.getString(data3Idx) : null;

                    String rawCandidate = data1;
                    if (rawCandidate == null || rawCandidate.trim().isEmpty()) {
                        rawCandidate = data3;
                    }

                    if (rawCandidate != null && !rawCandidate.trim().isEmpty()) {
                        // Asingkan nombor sebelum tanda '@' jika ada format JID
                        if (rawCandidate.contains("@")) {
                            rawCandidate = rawCandidate.substring(0, rawCandidate.indexOf('@'));
                        }

                        // Bersihkan digit nombor telefon
                        String cleanDigits = rawCandidate.replaceAll("[^0-9+]", "");
                        if (cleanDigits.length() >= 7) {
                            String norm = KantoiThreatEngine.normalizeToLocal(cleanDigits);
                            if (processedNumbers.add(norm)) {
                                result.totalWhatsAppContacts++;

                                // Semak secara masa nyata dengan pangkalan data scammer KANTOI
                                KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(norm);
                                if (threat != null) {
                                    result.scammerMatchesCount++;
                                    WhatsAppThreatMatch match = new WhatsAppThreatMatch();
                                    match.contactName = (displayName != null && !displayName.trim().isEmpty()) ? displayName : "Tanpa Nama";
                                    match.phoneNumber = norm;
                                    match.suspectName = threat.suspectName;
                                    match.riskTag = threat.riskTag;
                                    StringBuilder detSb = new StringBuilder();
                                    if (threat.bankAccounts != null && !threat.bankAccounts.isEmpty()) {
                                        detSb.append("Bank: ").append(threat.bankAccounts);
                                    }
                                    if (threat.source != null && !threat.source.isEmpty()) {
                                        if (detSb.length() > 0) detSb.append(" • ");
                                        detSb.append("Sumber: ").append(threat.source);
                                    }
                                    match.details = detSb.toString();
                                    result.threatsFound.add(match);
                                }
                            }
                        }
                    }
                }
                cursor.close();
            }

            // Simpan status terkini ke SharedPreferences untuk paparan pantas UI Pertahanan
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putInt(KEY_TOTAL_SCANNED, result.totalWhatsAppContacts)
                .putInt(KEY_THREATS_COUNT, result.scammerMatchesCount)
                .putLong(KEY_LAST_SCAN_TIME, result.scanTimestamp)
                .putBoolean(KEY_IS_DETECTED, result.isWhatsAppDetected)
                .apply();

            Log.i(TAG, "Imbasan WhatsApp siap: " + result.totalWhatsAppContacts + " kenalan diimbas, " + result.scammerMatchesCount + " ancaman ditemui.");

        } catch (Throwable t) {
            Log.e(TAG, "Ralat mengimbas kenalan WhatsApp: " + t.getMessage(), t);
        }

        return result;
    }

    /**
     * Dapatkan ringkasan imbasan tersimpan daripada SharedPreferences
     */
    public static WhatsAppScanResult getCachedResult(Context context) {
        WhatsAppScanResult res = new WhatsAppScanResult();
        if (context == null) return res;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        res.totalWhatsAppContacts = prefs.getInt(KEY_TOTAL_SCANNED, 0);
        res.scammerMatchesCount = prefs.getInt(KEY_THREATS_COUNT, 0);
        res.scanTimestamp = prefs.getLong(KEY_LAST_SCAN_TIME, 0);
        res.isWhatsAppDetected = prefs.getBoolean(KEY_IS_DETECTED, false);
        return res;
    }

    /**
     * Paparkan dialog senarai ancaman scammer yang dikesan dalam WhatsApp
     */
    public static void showWhatsAppThreatsDialog(final Activity activity, final WhatsAppScanResult result) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(result.scammerMatchesCount > 0 ? "🚨 AMARAN ANCAMAN WHATSAPP" : "✅ STATUS KESELAMATAN WHATSAPP");

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(activity, 16);
        content.setPadding(pad, pad, pad, pad);

        // Header Ringkasan
        TextView tvSummary = new TextView(activity);
        if (result.scammerMatchesCount > 0) {
            tvSummary.setText("⚠️ Perhatian! Terdapat " + result.scammerMatchesCount + " nombor dalam akaun WhatsApp anda yang sepadan dengan pangkalan data scammer PDRM / Mule KANTOI:");
            tvSummary.setTextColor(0xFFEF4444);
        } else {
            tvSummary.setText("✅ Berita Baik! Semua " + result.totalWhatsAppContacts + " kenalan yang diimbas dari WhatsApp anda bersih daripada rekod jenayah penipuan.");
            tvSummary.setTextColor(0xFF34D399);
        }
        tvSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSummary.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(tvSummary);

        // Senarai nombor scammer jika ada
        if (result.threatsFound != null && !result.threatsFound.isEmpty()) {
            for (final WhatsAppThreatMatch match : result.threatsFound) {
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable cBg = new GradientDrawable();
                cBg.setColor(0xFF1E293B);
                cBg.setCornerRadius(dpToPx(activity, 8));
                cBg.setStroke(dpToPx(activity, 1), 0xFFDC2626);
                card.setBackground(cBg);
                card.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cLp.topMargin = dpToPx(activity, 10);
                card.setLayoutParams(cLp);

                TextView tvName = new TextView(activity);
                tvName.setText("👤 Nama WhatsApp: " + match.contactName);
                tvName.setTextColor(Color.WHITE);
                tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvName.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(tvName);

                TextView tvNum = new TextView(activity);
                tvNum.setText("📞 Nombor: " + KantoiThreatEngine.formatPhoneNumber(match.phoneNumber));
                tvNum.setTextColor(0xFFFCA5A5);
                tvNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                card.addView(tvNum);

                TextView tvRisk = new TextView(activity);
                tvRisk.setText("🚨 Kategori: " + (match.riskTag != null ? match.riskTag : "Scam/Mule"));
                tvRisk.setTextColor(0xFFEF4444);
                tvRisk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvRisk.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(tvRisk);

                if (match.details != null && !match.details.isEmpty()) {
                    TextView tvDet = new TextView(activity);
                    tvDet.setText("📝 Maklumat: " + match.details);
                    tvDet.setTextColor(0xFF94A3B8);
                    tvDet.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    card.addView(tvDet);
                }

                // Butang Tindakan Pantas
                LinearLayout btnRow = new LinearLayout(activity);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.RIGHT);
                LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                brLp.topMargin = dpToPx(activity, 8);
                btnRow.setLayoutParams(brLp);

                Button btnProfile = new Button(activity);
                btnProfile.setText("PROFIL RISIKO");
                btnProfile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                btnProfile.setTextColor(Color.WHITE);
                GradientDrawable pBg = new GradientDrawable();
                pBg.setColor(0xFF0284C7);
                pBg.setCornerRadius(dpToPx(activity, 6));
                btnProfile.setBackground(pBg);
                btnProfile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent pIt = new Intent(activity, ContactProfileActivity.class);
                        pIt.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, match.phoneNumber);
                        activity.startActivity(pIt);
                    }
                });
                btnRow.addView(btnProfile);

                card.addView(btnRow);
                content.addView(card);
            }
        }

        scrollView.addView(content);
        builder.setView(scrollView);
        builder.setPositiveButton("TUTUP", null);
        builder.show();
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
