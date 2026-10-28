package com.kantoi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * GUI 3: Skrin Memeriksa Profile Contacts (Contact Detail & Threat Profile)
 * Memaparkan dossier risikan lengkap bagi sebarang nombor telefon:
 * Nama suspek, akaun bank keldai, IC, status SemakMule CCID PDRM,
 * serta integrasi komunikasi pantas (Call, WhatsApp, SMS, Google Intel).
 */
public class ContactProfileActivity extends Activity {
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";

    private String mPhoneNumber = "";
    private KantoiThreatEngine mEngine;
    private KantoiThreatEngine.ScammerThreat mThreat;
    private KantoiThreatEngine.SafeContactItem mCustomSafeContact;
    private boolean mIsSafeContact = false;
    private String mContactName = null;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEngine = KantoiThreatEngine.getInstance(this);

        Intent it = getIntent();
        if (it != null) {
            mPhoneNumber = it.getStringExtra(EXTRA_PHONE_NUMBER);
        }
        if (mPhoneNumber == null || mPhoneNumber.trim().isEmpty()) {
            mPhoneNumber = "0112345678";
        }

        final String cleanLocal = KantoiThreatEngine.normalizeToLocal(mPhoneNumber);
        mCustomSafeContact = mEngine.getCustomSafeContact(cleanLocal);
        mThreat = mEngine.checkThreat(cleanLocal);
        mIsSafeContact = mEngine.isSafeContact(cleanLocal);
        mContactName = mEngine.getContactDisplayName(cleanLocal);

        // Preload SemakMule CCID
        KantoiThreatEngine.querySemakMuleAsync(this, cleanLocal);

        View contentView = buildLayout(cleanLocal);
        setContentView(contentView);
    }

    private View buildLayout(final String cleanLocal) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0B0F19); // Stealth Dark Night
        scrollView.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(75));

        // 1. Navigation Top Bar
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setPadding(0, dpToPx(4), 0, dpToPx(12));

        Button btnBack = new Button(this);
        btnBack.setText("← KEMBALI");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnBack.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(0xFF141422);
        backBg.setCornerRadius(dpToPx(6));
        backBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        btnBack.setBackground(backBg);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        navBar.addView(btnBack);

        TextView tvNavTitle = new TextView(this);
        tvNavTitle.setText(" PROFIL KESELAMATAN NOMBOR");
        tvNavTitle.setTextColor(0xFFF8FAFC);
        tvNavTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvNavTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams navTitleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvNavTitle.setLayoutParams(navTitleLp);
        navBar.addView(tvNavTitle);

        Button btnShare = new Button(this);
        btnShare.setText("📢 KONGSI");
        btnShare.setTextColor(0xFF38BDF8);
        btnShare.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnShare.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable shareBg = new GradientDrawable();
        shareBg.setColor(0xFF0A0A0F);
        shareBg.setCornerRadius(dpToPx(6));
        shareBg.setStroke(dpToPx(1), 0xFF0284C7);
        btnShare.setBackground(shareBg);
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareProfileWarning(cleanLocal);
            }
        });
        navBar.addView(btnShare);
        root.addView(navBar);

        // 2. Main Profile Hero Card
        int heroBorderColor = 0xFF0284C7;
        if (mCustomSafeContact != null) {
            heroBorderColor = 0xFF10B981; // Emerald green
        } else if (mThreat != null) {
            heroBorderColor = 0xFFEF4444; // Crimson red
        } else if (mIsSafeContact) {
            heroBorderColor = 0xFF10B981;
        }

        LinearLayout heroCard = createCard(0xFF0A0A0F, heroBorderColor, 14);
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroCard.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        // Circular Avatar
        LinearLayout avatar = new LinearLayout(this);
        avatar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dpToPx(84), dpToPx(84));
        avatar.setLayoutParams(avLp);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        TextView tvAvIcon = new TextView(this);
        tvAvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        tvAvIcon.setGravity(Gravity.CENTER);

        if (mCustomSafeContact != null) {
            avBg.setColor(0xFF064E3B);
            avBg.setStroke(dpToPx(3), 0xFF10B981);
            tvAvIcon.setText("👥");
        } else if (mThreat != null) {
            avBg.setColor(0xFF7F1D1D);
            avBg.setStroke(dpToPx(3), 0xFFEF4444);
            tvAvIcon.setText("🚨");
        } else if (mIsSafeContact) {
            avBg.setColor(0xFF064E3B);
            avBg.setStroke(dpToPx(3), 0xFF10B981);
            tvAvIcon.setText("✓");
            tvAvIcon.setTextColor(0xFF34D399);
            tvAvIcon.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            avBg.setColor(0xFF141422);
            avBg.setStroke(dpToPx(3), 0xFF0284C7);
            tvAvIcon.setText("👤");
        }
        avatar.setBackground(avBg);
        avatar.addView(tvAvIcon);
        heroCard.addView(avatar);

        // Display Name
        TextView tvName = new TextView(this);
        if (mCustomSafeContact != null) {
            tvName.setText(mCustomSafeContact.contactName);
            tvName.setTextColor(0xFF34D399);
        } else if (mThreat != null) {
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
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setGravity(Gravity.CENTER);
        setMarginTop(tvName, 12);
        heroCard.addView(tvName);

        // Phone Number + Copy Button Row
        LinearLayout phoneRow = new LinearLayout(this);
        phoneRow.setOrientation(LinearLayout.HORIZONTAL);
        phoneRow.setGravity(Gravity.CENTER);
        setMarginTop(phoneRow, 4);

        TextView tvPhone = new TextView(this);
        tvPhone.setText(KantoiThreatEngine.formatPhoneNumber(mPhoneNumber));
        tvPhone.setTextColor(0xFF94A3B8);
        tvPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPhone.setTypeface(Typeface.DEFAULT_BOLD);
        phoneRow.addView(tvPhone);

        TextView tvCopy = new TextView(this);
        tvCopy.setText(" [📋 SALIN]");
        tvCopy.setTextColor(0xFF38BDF8);
        tvCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvCopy.setTypeface(Typeface.DEFAULT_BOLD);
        tvCopy.setClickable(true);
        tvCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Phone", cleanLocal));
                    Toast.makeText(ContactProfileActivity.this, "Nombor " + cleanLocal + " disalin!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        phoneRow.addView(tvCopy);
        heroCard.addView(phoneRow);

        // Threat Pill Badge
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        setMarginTop(pill, 10);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dpToPx(12));

        TextView tvPill = new TextView(this);
        tvPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvPill.setTypeface(Typeface.DEFAULT_BOLD);

        if (mCustomSafeContact != null) {
            pillBg.setColor(0xFF065F46);
            pillBg.setStroke(dpToPx(1), 0xFF34D399);
            String cat = (mCustomSafeContact.category != null) ? mCustomSafeContact.category.toUpperCase() : "RAKAN";
            tvPill.setText("👥 RAKAN KANTOI: " + cat + " • DISAHKAN SELAMAT");
            tvPill.setTextColor(Color.WHITE);
        } else if (mThreat != null) {
            pillBg.setColor(0xFF991B1B);
            pillBg.setStroke(dpToPx(1), 0xFFF87171);
            tvPill.setText("🚨 SCAMMER DISAHKAN PDRM & KOMUNITI");
            tvPill.setTextColor(Color.WHITE);
        } else if (mIsSafeContact) {
            pillBg.setColor(0xFF065F46);
            pillBg.setStroke(dpToPx(1), 0xFF34D399);
            tvPill.setText("✓ KENALAN BUKU TELEFON (DISAHKAN SELAMAT)");
            tvPill.setTextColor(Color.WHITE);
        } else {
            pillBg.setColor(0xFF141422);
            pillBg.setStroke(dpToPx(1), 0xFF38BDF8);
            tvPill.setText("ℹ️ NOMBOR ASING (TIADA REKOD SCAMMER)");
            tvPill.setTextColor(0xFF38BDF8);
        }
        pill.setBackground(pillBg);
        pill.addView(tvPill);
        heroCard.addView(pill);

        root.addView(heroCard);

        // 3. Quick Communication Action Row (Panggil, WhatsApp, SMS, Google Intel)
        LinearLayout commRow = new LinearLayout(this);
        commRow.setOrientation(LinearLayout.HORIZONTAL);
        setMarginTop(commRow, 12);

        // Call button
        Button btnCall = createCommButton("📞\nPANGGIL", 0xFF059669);
        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent cIt = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanLocal));
                    startActivity(cIt);
                } catch (Throwable t) {
                    Toast.makeText(ContactProfileActivity.this, "Gagal memanggil: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        commRow.addView(btnCall);

        // WhatsApp button (Tactical Hub: Chat Direct / OSINT DP)
        Button btnWa = createCommButton("💬\nWHATSAPP", 0xFF0284C7);
        setMarginLeft(btnWa, 6);
        btnWa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.showWhatsAppHubDialog(ContactProfileActivity.this, cleanLocal);
            }
        });
        commRow.addView(btnWa);

        // SMS button
        Button btnSms = createCommButton("✉️\nSMS", 0xFFD97706);
        setMarginLeft(btnSms, 6);
        btnSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent smsIt = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + cleanLocal));
                    startActivity(smsIt);
                } catch (Throwable t) {
                    Toast.makeText(ContactProfileActivity.this, "Gagal membuka SMS: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        commRow.addView(btnSms);

        // Google Intel button
        Button btnGoogle = createCommButton("🌐\nGOOGLE", 0xFF1D4ED8);
        setMarginLeft(btnGoogle, 6);
        btnGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.openGoogleCseSearch(ContactProfileActivity.this, cleanLocal);
            }
        });
        commRow.addView(btnGoogle);

        root.addView(commRow);

        // 4. PDRM SemakMule CCID Intelligence & Direct Check (Elevated Prominently to Top Position)
        LinearLayout smCardTop = createCard(0xFF0A0A0F, 0xFF0284C7, 12);
        smCardTop.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(smCardTop, 12);

        TextView tvSmTopTitle = new TextView(this);
        tvSmTopTitle.setText("🏛️ PDRM SEMAKMULE CCID (PORTAL RASMI)");
        tvSmTopTitle.setTextColor(0xFF38BDF8);
        tvSmTopTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSmTopTitle.setTypeface(Typeface.DEFAULT_BOLD);
        smCardTop.addView(tvSmTopTitle);

        final TextView tvSmTopStats = new TextView(this);
        KantoiThreatEngine.SemakMuleData smData = KantoiThreatEngine.getSemakMuleData(cleanLocal);
        if (smData != null && smData.searchCount > 0) {
            tvSmTopStats.setText("• Carian CCID: " + smData.searchCount + " carian direkodkan\n• Laporan Polis: " + (smData.reportCount > 0 ? smData.reportCount + " laporan sah" : "Tiada laporan rasmi"));
        } else {
            tvSmTopStats.setText("• Status SemakMule: Buat semakan rekod jenayah siber & akaun keldai terus di portal rasmi JSJK PDRM.");
        }
        tvSmTopStats.setTextColor(0xFFFCD34D);
        tvSmTopStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(tvSmTopStats, 4);
        smCardTop.addView(tvSmTopStats);

        Button btnOpenSemakMule = createActionButton("🏛️ SEMAK DI PORTAL RASMI SEMAKMULE PDRM", 0xFF0284C7, Color.WHITE);
        setMarginTop(btnOpenSemakMule, 8);
        btnOpenSemakMule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent bIt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                    startActivity(bIt);
                } catch (Throwable ignored) {}
            }
        });
        smCardTop.addView(btnOpenSemakMule);
        root.addView(smCardTop);

        // 5. Dossier Risikan & Rekod Ancaman (Full Threat Intelligence Table)
        LinearLayout dossierCard = createCard(0xFF0A0A0F, 0xFF2A2A3A, 12);
        dossierCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(dossierCard, 14);

        TextView tvDossierTitle = new TextView(this);
        tvDossierTitle.setText("📁 DOSSIER RISIKAN & REKOD ANCAMAN");
        tvDossierTitle.setTextColor(0xFF38BDF8);
        tvDossierTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDossierTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dossierCard.addView(tvDossierTitle);

        if (mThreat != null) {
            addDossierField(dossierCard, "Nama Penuh Suspek", mThreat.suspectName != null ? mThreat.suspectName : "Tidak Dinyatakan");
            addDossierField(dossierCard, "Kategori Jenayah / Modus", mThreat.riskTag != null ? mThreat.riskTag : "Scam/Mule");
            addDossierField(dossierCard, "Akaun Bank Keldai (Mule)", mThreat.bankAccounts != null ? mThreat.bankAccounts : "Tiada maklumat akaun");
            addDossierField(dossierCard, "No. Kad Pengenalan (IC)", mThreat.icNumber != null ? mThreat.icNumber : "Tiada dalam rekod");
            addDossierField(dossierCard, "Alamat / Lokasi Operasi", mThreat.address != null ? mThreat.address : "Malaysia");
            addDossierField(dossierCard, "Sumber Risikan", mThreat.source != null ? mThreat.source : "PDRM SemakMule");

            if (mThreat.evidenceLinks != null && !mThreat.evidenceLinks.isEmpty() && !mThreat.evidenceLinks.equals("N/A")) {
                addDossierLinkField(dossierCard, "Pautan Bukti & Forum", mThreat.evidenceLinks);
            }
        } else {
            TextView tvClean = new TextView(this);
            tvClean.setText("Tiada sebarang rekod ancaman scammer atau akaun keldai ditemui bagi nombor ini dalam 3,654 rekod pangkalan data luar talian KANTOI.");
            tvClean.setTextColor(0xFF94A3B8);
            tvClean.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            setMarginTop(tvClean, 8);
            dossierCard.addView(tvClean);
        }
        root.addView(dossierCard);

        // 6. Matriks Risikan Siber & OSINT (WhatsApp DP CheckLeaked, Google CSE)
        LinearLayout smCard = createCard(0xFF0A0A0F, 0xFF2A2A3A, 12);
        smCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(smCard, 12);

        TextView tvSmTitle = new TextView(this);
        tvSmTitle.setText("🛡️ MATRIKS RISIKAN SIBER & OSINT LANJUTAN");
        tvSmTitle.setTextColor(0xFF38BDF8);
        tvSmTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSmTitle.setTypeface(Typeface.DEFAULT_BOLD);
        smCard.addView(tvSmTitle);

        TextView tvSmDesc = new TextView(this);
        tvSmDesc.setText("Siasat profil WhatsApp dan jejak digital terbuka nombor ini:");
        tvSmDesc.setTextColor(0xFF94A3B8);
        tvSmDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(tvSmDesc, 4);
        smCard.addView(tvSmDesc);

        // Sub-Section A: WhatsApp DP & Breach Intel (CheckLeaked OSINT)
        TextView tvSmSubB = new TextView(this);
        tvSmSubB.setText("📸 1. RISIKAN GAMBAR DP & KEBOCORAN (CHECKLEAKED)");
        tvSmSubB.setTextColor(0xFF34D399);
        tvSmSubB.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSmSubB.setTypeface(Typeface.DEFAULT_BOLD);
        setMarginTop(tvSmSubB, 12);
        smCard.addView(tvSmSubB);

        TextView tvWaIntelInfo = new TextView(this);
        tvWaIntelInfo.setText("Siasat gambar profil (DP) WhatsApp, status info akaun, dan rekod pangkalan data terbocor secara sulit.");
        tvWaIntelInfo.setTextColor(0xFFD1FAE5);
        tvWaIntelInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(tvWaIntelInfo, 2);
        smCard.addView(tvWaIntelInfo);

        Button btnCheckLeaked = createActionButton("📸 SIASAT GAMBAR DP DI CHECKLEAKED", 0xFF059669, Color.WHITE);
        updateButtonStroke(btnCheckLeaked, 0xFF10B981);
        setMarginTop(btnCheckLeaked, 6);
        btnCheckLeaked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.showWhatsAppIntelDialog(ContactProfileActivity.this, cleanLocal);
            }
        });
        smCard.addView(btnCheckLeaked);

        // Sub-Section C: Google CSE Social Media Intel
        TextView tvSmSubC = new TextView(this);
        tvSmSubC.setText("🌐 3. CARIAN FORUM & MEDIA SOSIAL (GOOGLE CSE)");
        tvSmSubC.setTextColor(0xFF60A5FA);
        tvSmSubC.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSmSubC.setTypeface(Typeface.DEFAULT_BOLD);
        setMarginTop(tvSmSubC, 12);
        smCard.addView(tvSmSubC);

        Button btnGoogleCse = createActionButton("🌐 CARI DI GOOGLE THREAT INTEL", 0xFF1D4ED8, Color.WHITE);
        updateButtonStroke(btnGoogleCse, 0xFF3B82F6);
        setMarginTop(btnGoogleCse, 6);
        btnGoogleCse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.openGoogleCseSearch(ContactProfileActivity.this, cleanLocal);
            }
        });
        smCard.addView(btnGoogleCse);

        root.addView(smCard);

        // 6. Security Management Controls (Rakan Selamat, Scammer, Simpan Kenalan)
        LinearLayout actionsCard = createCard(0xFF0A0A0F, 0xFF2A2A3A, 12);
        actionsCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(actionsCard, 12);

        TextView tvSecTitle = new TextView(this);
        tvSecTitle.setText("🛡️ PENGURUSAN STATUS & KESELAMATAN NOMBOR");
        tvSecTitle.setTextColor(0xFF94A3B8);
        tvSecTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSecTitle.setTypeface(Typeface.DEFAULT_BOLD);
        actionsCard.addView(tvSecTitle);

        if (mThreat != null) {
            // Case 1: Nombor ini tersenarai sebagai Scammer
            Button btnUpdateScam = createActionButton("🚨 KEMASKINI / LAPOR SCAMMER KE KOMUNITI", 0xFFDC2626, Color.WHITE);
            updateButtonStroke(btnUpdateScam, 0xFFF87171);
            setMarginTop(btnUpdateScam, 10);
            btnUpdateScam.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterScammerDialog(ContactProfileActivity.this, cleanLocal);
                }
            });
            actionsCard.addView(btnUpdateScam);

            Button btnDeleteScam = createActionButton("🗑️ PADAM DARI REKOD SCAMMER", 0xFF2A2A3A, Color.WHITE);
            updateButtonStroke(btnDeleteScam, 0xFF64748B);
            setMarginTop(btnDeleteScam, 10);
            btnDeleteScam.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(ContactProfileActivity.this)
                        .setTitle("Padam Rekod Scammer?")
                        .setMessage("Adakah anda pasti ingin mengeluarkan nombor [" + KantoiThreatEngine.formatPhoneNumber(cleanLocal) + "] daripada senarai scammer?")
                        .setPositiveButton("Padam", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean ok = mEngine.deleteThreatLocal(cleanLocal);
                                if (ok) {
                                    Toast.makeText(ContactProfileActivity.this, "Rekod scammer berjaya dipadam.", Toast.LENGTH_SHORT).show();
                                    recreate();
                                }
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
                }
            });
            actionsCard.addView(btnDeleteScam);

            Button btnSwitchToSafe = createActionButton("👥 TUKAR KEPADA RAKAN SELAMAT", 0xFF059669, Color.WHITE);
            updateButtonStroke(btnSwitchToSafe, 0xFF34D399);
            setMarginTop(btnSwitchToSafe, 10);
            btnSwitchToSafe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterFriendDialog(ContactProfileActivity.this, cleanLocal);
                }
            });
            actionsCard.addView(btnSwitchToSafe);

        } else if (mCustomSafeContact != null) {
            // Case 2: Nombor ini didaftarkan sebagai Rakan Selamat
            Button btnUpdateSafe = createActionButton("👥 KEMASKINI PROFIL RAKAN SELAMAT", 0xFF059669, Color.WHITE);
            updateButtonStroke(btnUpdateSafe, 0xFF34D399);
            setMarginTop(btnUpdateSafe, 10);
            btnUpdateSafe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterFriendDialog(ContactProfileActivity.this, cleanLocal);
                }
            });
            actionsCard.addView(btnUpdateSafe);

            Button btnDeleteSafe = createActionButton("🗑️ PADAM DARI RAKAN SELAMAT", 0xFF2A2A3A, Color.WHITE);
            updateButtonStroke(btnDeleteSafe, 0xFF64748B);
            setMarginTop(btnDeleteSafe, 10);
            btnDeleteSafe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(ContactProfileActivity.this)
                        .setTitle("Padam Rakan Selamat?")
                        .setMessage("Adakah anda pasti ingin memadam nombor [" + KantoiThreatEngine.formatPhoneNumber(cleanLocal) + "] daripada senarai rakan selamat?")
                        .setPositiveButton("Padam", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean ok = mEngine.deleteSafeContact(cleanLocal);
                                if (ok) {
                                    Toast.makeText(ContactProfileActivity.this, "Rekod rakan selamat berjaya dipadam.", Toast.LENGTH_SHORT).show();
                                    recreate();
                                }
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
                }
            });
            actionsCard.addView(btnDeleteSafe);

            Button btnReportScam = createActionButton("🚨 LAPOR SEBAGAI SCAMMER KE KOMUNITI", 0xFFDC2626, Color.WHITE);
            updateButtonStroke(btnReportScam, 0xFFF87171);
            setMarginTop(btnReportScam, 10);
            btnReportScam.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterScammerDialog(ContactProfileActivity.this, cleanLocal);
                }
            });
            actionsCard.addView(btnReportScam);

        } else {
            // Case 3: Nombor Asing (Neutral Unknown Contact)
            Button btnReportScam = createActionButton("🚨 LAPOR SCAMMER KE KOMUNITI KANTOI", 0xFFDC2626, Color.WHITE);
            updateButtonStroke(btnReportScam, 0xFFF87171);
            setMarginTop(btnReportScam, 10);
            btnReportScam.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterScammerDialog(ContactProfileActivity.this, cleanLocal);
                }
            });
            actionsCard.addView(btnReportScam);

            Button btnRegisterFriend = createActionButton("👥 TANDA SEBAGAI RAKAN SELAMAT", 0xFF059669, Color.WHITE);
            updateButtonStroke(btnRegisterFriend, 0xFF34D399);
            setMarginTop(btnRegisterFriend, 10);
            btnRegisterFriend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterFriendDialog(ContactProfileActivity.this, cleanLocal);
                }
            });
            actionsCard.addView(btnRegisterFriend);
        }

        // Button 3: Simpan ke Contacts
        Button btnSave = createActionButton("👤 EKSPORT KE BUKU TELEFON ANDROID", 0xFF141422, Color.WHITE);
        updateButtonStroke(btnSave, 0xFF2A2A3A);
        setMarginTop(btnSave, 10);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent insIt = new Intent(Intent.ACTION_INSERT);
                    insIt.setType(ContactsContract.Contacts.CONTENT_TYPE);
                    insIt.putExtra(ContactsContract.Intents.Insert.PHONE, cleanLocal);
                    if (mThreat != null && mThreat.suspectName != null) {
                        insIt.putExtra(ContactsContract.Intents.Insert.NAME, "[SCAMMER] " + mThreat.suspectName);
                    } else if (mCustomSafeContact != null && mCustomSafeContact.contactName != null) {
                        insIt.putExtra(ContactsContract.Intents.Insert.NAME, mCustomSafeContact.contactName);
                    }
                    startActivity(insIt);
                } catch (Throwable t) {
                    Toast.makeText(ContactProfileActivity.this, "Gagal membuka aplikasi kenalan.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        actionsCard.addView(btnSave);

        // Button 4: Kongsi Amaran Scammer / Maklumat
        Button btnShareAlert = createActionButton("📢 KONGSI MAKLUMAT KESELAMATAN KEPADA KELUARGA", 0xFF0284C7, Color.WHITE);
        updateButtonStroke(btnShareAlert, 0xFF38BDF8);
        setMarginTop(btnShareAlert, 10);
        btnShareAlert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareProfileWarning(cleanLocal);
            }
        });
        actionsCard.addView(btnShareAlert);

        root.addView(actionsCard);

        scrollView.addView(root);
        return scrollView;
    }

    private void shareProfileWarning(String cleanLocal) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ [AMARAN KESELAMATAN KANTOI]\n\n");
        sb.append("Nombor: ").append(KantoiThreatEngine.formatPhoneNumber(cleanLocal)).append("\n");
        if (mThreat != null) {
            sb.append("🚨 STATUS: SCAMMER DISAHKAN!\n");
            sb.append("Suspek: ").append(mThreat.suspectName).append("\n");
            sb.append("Kategori: ").append(mThreat.riskTag).append("\n");
            sb.append("Bank Keldai: ").append(mThreat.bankAccounts).append("\n");
        } else {
            sb.append("Status: Nombor Asing (Tiada rekod penipuan offline)\n");
        }
        sb.append("\nPeriksa status akaun bank & telefon sebelum memindahkan wang dengan aplikasi KANTOI.");

        Intent shareIt = new Intent(Intent.ACTION_SEND);
        shareIt.setType("text/plain");
        shareIt.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIt, "Kongsi Amaran Melalui"));
    }

    private void addDossierField(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        setMarginTop(row, 8);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label.toUpperCase());
        tvLabel.setTextColor(0xFF64748B);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvLabel);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(0xFFE2E8F0);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setPadding(0, dpToPx(2), 0, 0);
        row.addView(tvVal);

        parent.addView(row);
    }

    private void addDossierLinkField(LinearLayout parent, String label, final String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        setMarginTop(row, 8);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label.toUpperCase());
        tvLabel.setTextColor(0xFF64748B);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvLabel);

        TextView tvVal = new TextView(this);
        tvVal.setText(url + " ↗");
        tvVal.setTextColor(0xFF38BDF8);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setClickable(true);
        tvVal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent bIt = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(bIt);
                } catch (Throwable ignored) {}
            }
        });
        row.addView(tvVal);

        parent.addView(row);
    }

    private Button createCommButton(String text, int bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dpToPx(8));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(50), 1.0f);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button createActionButton(String text, int bgColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dpToPx(8));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44));
        btn.setLayoutParams(lp);
        return btn;
    }

    private void updateButtonStroke(Button btn, int strokeColor) {
        GradientDrawable bg = (GradientDrawable) btn.getBackground();
        if (bg != null) {
            bg.setStroke(dpToPx(1), strokeColor);
        }
    }

    private LinearLayout createCard(int bgColor, int strokeColor, int cornerDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dpToPx(cornerDp));
        bg.setStroke(dpToPx(1), strokeColor);
        layout.setBackground(bg);
        return layout;
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
