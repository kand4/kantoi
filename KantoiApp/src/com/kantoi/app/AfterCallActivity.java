package com.kantoi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * GUI 2: Skrin Selepas Menerima Panggilan (After-Call Summary Dialog)
 * Dipaparkan serta-merta selepas panggilan tamat.
 * Memberi ringkasan Caller ID, ancaman scammer, dan 6 tindakan pantas:
 * (Dossier, Sekat/Lapor, SemakMule CCID, Google CSE, WhatsApp, Simpan Kenalan).
 */
public class AfterCallActivity extends Activity {
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";

    private String mPhoneNumber = "";
    private KantoiThreatEngine mEngine;
    private KantoiThreatEngine.ScammerThreat mThreat;
    private boolean mIsSafeContact = false;
    private String mContactName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mEngine = KantoiThreatEngine.getInstance(this);

        Intent it = getIntent();
        if (it != null) {
            mPhoneNumber = it.getStringExtra(EXTRA_PHONE_NUMBER);
        }
        if (mPhoneNumber == null || mPhoneNumber.trim().isEmpty()) {
            mPhoneNumber = "0112345678";
        }

        final String cleanLocal = KantoiThreatEngine.normalizeToLocal(mPhoneNumber);
        mThreat = mEngine.checkThreat(cleanLocal);
        mIsSafeContact = mEngine.isSafeContact(cleanLocal);
        mContactName = mEngine.getContactDisplayName(cleanLocal);

        // Preload SemakMule CCID async
        KantoiThreatEngine.querySemakMuleAsync(this, cleanLocal);

        View dialogView = buildDialogLayout(cleanLocal);
        setContentView(dialogView);

        // Set window dimensions and background dimming
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.65f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private View buildDialogLayout(final String cleanLocal) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setPadding(dpToPx(14), dpToPx(16), dpToPx(14), dpToPx(20));
        scrollView.setClipToPadding(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(18));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF0A0A0F); // Slate 900
        cardBg.setCornerRadius(dpToPx(16));
        cardBg.setStroke(dpToPx(2), mThreat != null ? 0xFFEF4444 : (mIsSafeContact ? 0xFF10B981 : 0xFF0284C7));
        card.setBackground(cardBg);

        // 1. Header with Title and Close Button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📋 RINGKASAN SELEPAS PANGGILAN");
        tvTitle.setTextColor(mThreat != null ? 0xFFFCA5A5 : (mIsSafeContact ? 0xFF6EE7B7 : 0xFF38BDF8));
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleLp);
        headerRow.addView(tvTitle);

        Button btnCloseX = new Button(this);
        btnCloseX.setText("✕");
        btnCloseX.setTextColor(0xFF94A3B8);
        btnCloseX.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnCloseX.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF141422); closeBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        closeBg.setCornerRadius(dpToPx(16));
        closeBg.setStroke(dpToPx(1), 0xFF334155);
        btnCloseX.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        btnCloseX.setLayoutParams(closeLp);
        btnCloseX.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        headerRow.addView(btnCloseX);
        card.addView(headerRow);

        // 2. Caller Identity Row (Avatar + Name + Number)
        LinearLayout callerRow = new LinearLayout(this);
        callerRow.setOrientation(LinearLayout.HORIZONTAL);
        callerRow.setGravity(Gravity.CENTER_VERTICAL);
        setMarginTop(callerRow, 12);

        // Mini Avatar
        LinearLayout avatar = new LinearLayout(this);
        avatar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dpToPx(52), dpToPx(52));
        avatar.setLayoutParams(avLp);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        TextView tvAvIcon = new TextView(this);
        tvAvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvAvIcon.setGravity(Gravity.CENTER);

        if (mThreat != null) {
            avBg.setColor(0xFF7F1D1D);
            avBg.setStroke(dpToPx(2), 0xFFEF4444);
            tvAvIcon.setText("🚨");
        } else if (mIsSafeContact) {
            avBg.setColor(0xFF064E3B);
            avBg.setStroke(dpToPx(2), 0xFF10B981);
            tvAvIcon.setText("✓");
            tvAvIcon.setTextColor(0xFF34D399);
            tvAvIcon.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            avBg.setColor(0xFF141422);
            avBg.setStroke(dpToPx(2), 0xFF0284C7);
            tvAvIcon.setText("👤");
        }
        avatar.setBackground(avBg);
        avatar.addView(tvAvIcon);
        callerRow.addView(avatar);

        // Caller Details Column
        LinearLayout detailsCol = new LinearLayout(this);
        detailsCol.setOrientation(LinearLayout.VERTICAL);
        detailsCol.setPadding(dpToPx(12), 0, 0, 0);

        TextView tvName = new TextView(this);
        if (mThreat != null) {
            String name = (mThreat.suspectName != null && !mThreat.suspectName.isEmpty()) ? mThreat.suspectName : "SCAMMER DISAHKAN";
            tvName.setText(name);
            tvName.setTextColor(0xFFF87171);
        } else if (mIsSafeContact && mContactName != null) {
            tvName.setText(mContactName);
            tvName.setTextColor(0xFF34D399);
        } else {
            tvName.setText("Nombor Tidak Dikenali");
            tvName.setTextColor(Color.WHITE);
        }
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        detailsCol.addView(tvName);

        TextView tvNum = new TextView(this);
        tvNum.setText(KantoiThreatEngine.formatPhoneNumber(mPhoneNumber));
        tvNum.setTextColor(0xFF94A3B8);
        tvNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detailsCol.addView(tvNum);

        callerRow.addView(detailsCol);
        card.addView(callerRow);

        // 3. Threat Assessment Box
        LinearLayout threatBox = new LinearLayout(this);
        threatBox.setOrientation(LinearLayout.VERTICAL);
        threatBox.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        setMarginTop(threatBox, 12);

        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setCornerRadius(dpToPx(10));

        TextView tvBoxText = new TextView(this);
        tvBoxText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBoxText.setTypeface(Typeface.DEFAULT_BOLD);
        tvBoxText.setLineSpacing(dpToPx(3), 1.15f);

        if (mThreat != null) {
            boxBg.setColor(0xFF450A0A);
            boxBg.setStroke(dpToPx(1), 0xFFEF4444);
            tvBoxText.setText("🚨 KANTOI: Panggilan ini sah daripada scammer!\nKategori: " + (mThreat.riskTag != null ? mThreat.riskTag : "Mule Bank") + "\nBank Keldai: " + (mThreat.bankAccounts != null ? mThreat.bankAccounts : "-"));
            tvBoxText.setTextColor(0xFFFEE2E2);
        } else if (mIsSafeContact) {
            boxBg.setColor(0xFF064E3B);
            boxBg.setStroke(dpToPx(1), 0xFF059669);
            tvBoxText.setText("✓ Panggilan selamat daripada kenalan buku telefon anda.");
            tvBoxText.setTextColor(0xFFD1FAE5);
        } else {
            boxBg.setColor(0xFF141422); boxBg.setStroke(dpToPx(1), 0xFF2A2A3A);
            boxBg.setStroke(dpToPx(1), 0xFF334155);
            tvBoxText.setText("ℹ️ Nombor tiada dalam pangkalan data. Jika ada transaksi mencurigakan, buat semakan di SemakMule atau Google CSE di bawah.");
            tvBoxText.setTextColor(0xFFE2E8F0);
        }
        threatBox.setBackground(boxBg);
        threatBox.addView(tvBoxText);
        card.addView(threatBox);

        // 4. Section Label: Tindakan Pantas (Quick Actions)
        TextView tvActionHeader = new TextView(this);
        tvActionHeader.setText("⚡ TINDAKAN PANTAS & RISIKAN");
        tvActionHeader.setTextColor(0xFF94A3B8);
        tvActionHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvActionHeader.setTypeface(Typeface.DEFAULT_BOLD);
        setMarginTop(tvActionHeader, 18);
        card.addView(tvActionHeader);

        // 5. 6 Quick Action Buttons in 2-Column Responsive Grid (Uniform 14dp Spacing, 48dp Height, High Contrast Outlines)
        LinearLayout gridRow1 = new LinearLayout(this);
        gridRow1.setOrientation(LinearLayout.HORIZONTAL);
        setMarginTop(gridRow1, 14);

        // Action 1: Buka Profil Lengkap
        Button btnProfile = createSmallButton("📖 PROFIL LENGKAP", 0xFF0369A1, 0xFF38BDF8, Color.WHITE);
        btnProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pIt = new Intent(AfterCallActivity.this, ContactProfileActivity.class);
                pIt.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, cleanLocal);
                startActivity(pIt);
                finish();
            }
        });
        gridRow1.addView(btnProfile);

        // Action 2: Sekat / Lapor Scammer
        Button btnBlock = createSmallButton("🚫 SEKAT / LAPOR", 0xFFDC2626, 0xFFF87171, Color.WHITE);
        setMarginLeft(btnBlock, 14);
        btnBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEngine.showRegisterScammerDialog(AfterCallActivity.this, cleanLocal);
            }
        });
        gridRow1.addView(btnBlock);
        card.addView(gridRow1);

        LinearLayout gridRow2 = new LinearLayout(this);
        gridRow2.setOrientation(LinearLayout.HORIZONTAL);
        setMarginTop(gridRow2, 14);

        // Action 3: SemakMule CCID
        Button btnSemakMule = createSmallButton("🏛️ SEMAKMULE PDRM", 0xFFD97706, 0xFFFBBF24, Color.WHITE);
        btnSemakMule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent bIt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                    startActivity(bIt);
                } catch (Throwable ignored) {}
            }
        });
        gridRow2.addView(btnSemakMule);

        // Action 4: Google CSE
        Button btnGoogleCse = createSmallButton("🌐 GOOGLE INTEL", 0xFF1D4ED8, 0xFF60A5FA, Color.WHITE);
        setMarginLeft(btnGoogleCse, 14);
        btnGoogleCse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.openGoogleCseSearch(AfterCallActivity.this, cleanLocal);
            }
        });
        gridRow2.addView(btnGoogleCse);
        card.addView(gridRow2);

        LinearLayout gridRow3 = new LinearLayout(this);
        gridRow3.setOrientation(LinearLayout.HORIZONTAL);
        setMarginTop(gridRow3, 14);

        // Action 5: WhatsApp Hub (Sembang Direct & Risikan OSINT DP CheckLeaked Tanpa Butang Berkembar)
        Button btnWhatsApp = createSmallButton("💬 WHATSAPP HUB", 0xFF059669, 0xFF34D399, Color.WHITE);
        btnWhatsApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.showWhatsAppHubDialog(AfterCallActivity.this, cleanLocal);
            }
        });
        gridRow3.addView(btnWhatsApp);

        // Action 6: Simpan Nombor ke Contacts
        Button btnSaveContact = createSmallButton("👤 SIMPAN KENALAN", 0xFF334155, 0xFF64748B, 0xFFF8FAFC);
        setMarginLeft(btnSaveContact, 14);
        btnSaveContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(AfterCallActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
                b.setTitle("👤 CARA SIMPAN KENALAN");
                b.setItems(new CharSequence[]{
                    "👥 Simpan Sebagai Rakan Selamat KANTOI (Senarai Putih)",
                    "📖 Eksport ke Buku Telefon Android"
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            mEngine.showRegisterFriendDialog(AfterCallActivity.this, cleanLocal);
                        } else {
                            try {
                                Intent insertIt = new Intent(Intent.ACTION_INSERT);
                                insertIt.setType(ContactsContract.Contacts.CONTENT_TYPE);
                                insertIt.putExtra(ContactsContract.Intents.Insert.PHONE, cleanLocal);
                                if (mThreat != null && mThreat.suspectName != null) {
                                    insertIt.putExtra(ContactsContract.Intents.Insert.NAME, "[SCAMMER] " + mThreat.suspectName);
                                }
                                startActivity(insertIt);
                            } catch (Throwable t) {
                                Toast.makeText(AfterCallActivity.this, "Gagal membuka kenalan: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
                b.setNegativeButton("BATAL", null);
                b.show();
            }
        });
        gridRow3.addView(btnSaveContact);
        card.addView(gridRow3);

        // 6. Footer Button (Selesai & Tutup)
        Button btnDone = new Button(this);
        btnDone.setText("✓ SELESAI / TUTUP");
        btnDone.setTextColor(0xFFE2E8F0);
        btnDone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnDone.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setColor(0xFF141422); doneBg.setStroke(dpToPx(1), 0xFF00D4FF);
        doneBg.setCornerRadius(dpToPx(12));
        doneBg.setStroke(dpToPx(1), 0xFF475569);
        btnDone.setBackground(doneBg);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48));
        doneLp.topMargin = dpToPx(22);
        btnDone.setLayoutParams(doneLp);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        card.addView(btnDone);

        scrollView.addView(card);
        return scrollView;
    }

    private Button createSmallButton(String text, int bgColor, int strokeColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), strokeColor);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(48), 1.0f);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void setMarginLeft(View v, int dp) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).leftMargin = dpToPx(dp);
        }
    }

    private void setMarginTop(View v, int dp) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).topMargin = dpToPx(dp);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
