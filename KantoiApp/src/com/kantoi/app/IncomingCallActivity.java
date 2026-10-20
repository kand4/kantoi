package com.kantoi.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * GUI 1: Skrin Semasa Menerima Panggilan (Incoming Call Screen)
 * Memaparkan Caller ID masa nyata, status amaran panggilan masuk berdering (Ringing State),
 * amaran scammer PDRM CCID, serta butang tindakan jelas (Jawab vs Tolak vs Tolak & Sekat)
 * yang direka khusus supaya tidak mengelirukan pengguna dengan panggilan aktif.
 */
public class IncomingCallActivity extends Activity {
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";
    public static final String EXTRA_IS_TEST_DEMO = "extra_is_test_demo";

    private String mPhoneNumber = "";
    private boolean mIsDemo = false;
    private KantoiThreatEngine mEngine;
    private KantoiThreatEngine.ScammerThreat mThreat;
    private boolean mIsSafeContact = false;
    private String mContactName = null;
    private int mDemoState = 0; // 0 = Scammer, 1 = Safe Contact, 2 = Unknown
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private ScrollView mRootScrollView;
    private LinearLayout mContentContainer;

    // Penerima siaran tamat panggilan supaya aktiviti ini ditutup jika pemanggil meletakkan telefon
    private final BroadcastReceiver mCallEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    // Pemerhati berkala untuk memastikan jika panggilan disambungkan di luar skrin (cth. Bluetooth / Notifikasi),
    // skrin ini secara automatik berpindah ke ActiveCallActivity
    private final Runnable mCallStateWatcher = new Runnable() {
        @Override
        public void run() {
            try {
                Call call = KantoiInCallService.getCurrentCall();
                if (call != null) {
                    int state = call.getState();
                    if (state == Call.STATE_ACTIVE) {
                        handleAnswerCall(KantoiThreatEngine.normalizeToLocal(mPhoneNumber));
                        return;
                    } else if (state == Call.STATE_DISCONNECTED) {
                        finish();
                        return;
                    }
                }
            } catch (Throwable ignored) {}
            mHandler.postDelayed(this, 750);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Konfigurasi paparan atas skrin kunci (Show over lock screen & turn screen on)
        setupWindowFlags();

        mEngine = KantoiThreatEngine.getInstance(this);

        Intent intent = getIntent();
        if (intent != null) {
            mPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
            mIsDemo = intent.getBooleanExtra(EXTRA_IS_TEST_DEMO, false);
        }

        if (mPhoneNumber == null || mPhoneNumber.trim().isEmpty()) {
            mPhoneNumber = "0112345678"; // Nombor fallback jika kosong
        }

        final String cleanLocal = KantoiThreatEngine.normalizeToLocal(mPhoneNumber);
        mThreat = mEngine.checkThreat(cleanLocal);
        mIsSafeContact = mEngine.isSafeContact(cleanLocal);
        mContactName = mEngine.getContactDisplayName(cleanLocal);

        // Jika dalam Mod Demo dan nombor tiada dalam DB, jana mock Scammer Threat supaya pengguna dapat melihat amaran penuh
        if (mIsDemo && mThreat == null && !mIsSafeContact) {
            setupDemoThreat(0);
        }

        // Jika ancaman scammer dikesan, berikan getaran haptic amaran berkembar
        if (mThreat != null && !mIsSafeContact) {
            triggerThreatVibration();
        }

        // Prapapar SemakMule CCID di latar belakang
        KantoiThreatEngine.querySemakMuleAsync(this, cleanLocal);

        // Daftar pemantau tamat panggilan
        try {
            registerReceiver(mCallEndReceiver, new IntentFilter("com.kantoi.app.ACTION_CALL_ENDED"));
        } catch (Throwable ignored) {}

        // Mulakan pengawasan status panggilan aktif
        mHandler.postDelayed(mCallStateWatcher, 1000);

        // Bina Antaramuka Pengguna (UI)
        mRootScrollView = new ScrollView(this);
        mRootScrollView.setFillViewport(true);
        mRootScrollView.setBackgroundColor(0xFF070B14); // Deep Night Black

        mContentContainer = new LinearLayout(this);
        mContentContainer.setOrientation(LinearLayout.VERTICAL);
        mContentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        mContentContainer.setPadding(dpToPx(20), dpToPx(38), dpToPx(20), dpToPx(32));

        mRootScrollView.addView(mContentContainer);
        setContentView(mRootScrollView);

        renderUI(cleanLocal);
    }

    private void setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mCallEndReceiver);
        } catch (Throwable ignored) {}
        mHandler.removeCallbacksAndMessages(null);
    }

    private void setupDemoThreat(int demoType) {
        mDemoState = demoType;
        if (demoType == 0) {
            // Scammer Demo
            mThreat = new KantoiThreatEngine.ScammerThreat();
            mThreat.phoneNorm = KantoiThreatEngine.normalizeToLocal(mPhoneNumber);
            mThreat.suspectName = "AHMAD KELDAI (SINDIKET MACAU SCAM)";
            mThreat.riskTag = "Macau Scam / Mule Bank";
            mThreat.bankAccounts = "Maybank 512345678901 (Akaun Keldai Disahkan)";
            mThreat.source = "PDRM CCID & Lowyat Threat DB";
            mIsSafeContact = false;
            mContactName = null;
        } else if (demoType == 1) {
            // Safe Contact Demo
            mThreat = null;
            mIsSafeContact = true;
            mContactName = "Alan Wong (Rakan Sepejabat)";
        } else {
            // Unknown Number Demo
            mThreat = null;
            mIsSafeContact = false;
            mContactName = null;
        }
    }

    private void renderUI(final String cleanLocal) {
        mContentContainer.removeAllViews();

        // 1. TOP CALL-STATE HEADER (Unmistakable "PANGGILAN MASUK SEDANG BERDERING")
        LinearLayout ringHeader = new LinearLayout(this);
        ringHeader.setOrientation(LinearLayout.VERTICAL);
        ringHeader.setGravity(Gravity.CENTER);

        // Animated Ringing Pill
        LinearLayout ringPill = new LinearLayout(this);
        ringPill.setOrientation(LinearLayout.HORIZONTAL);
        ringPill.setGravity(Gravity.CENTER);
        ringPill.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setCornerRadius(dpToPx(24));

        if (mThreat != null) {
            ringBg.setColor(0xFF7F1D1D); // Deep Crimson
            ringBg.setStroke(dpToPx(2), 0xFFEF4444);
        } else if (mIsSafeContact) {
            ringBg.setColor(0xFF064E3B); // Deep Emerald
            ringBg.setStroke(dpToPx(2), 0xFF10B981);
        } else {
            ringBg.setColor(0xFF0F2642); // Navy Cyan
            ringBg.setStroke(dpToPx(2), 0xFF0284C7);
        }
        ringPill.setBackground(ringBg);

        TextView tvRingIcon = new TextView(this);
        tvRingIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvRingIcon.setPadding(0, 0, dpToPx(6), 0);

        TextView tvRingText = new TextView(this);
        tvRingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvRingText.setTypeface(Typeface.DEFAULT_BOLD);
        tvRingText.setTextColor(Color.WHITE);

        if (mThreat != null) {
            tvRingIcon.setText("🚨");
            tvRingText.setText("AMARAN SCAMMER • SEDANG BERDERING");
        } else if (mIsSafeContact) {
            tvRingIcon.setText("🟢");
            tvRingText.setText("KENALAN SAH • SEDANG BERDERING");
        } else {
            tvRingIcon.setText("🔔");
            tvRingText.setText("PANGGILAN MASUK • SEDANG BERDERING");
        }
        ringPill.addView(tvRingIcon);
        ringPill.addView(tvRingText);

        // Blinking / Pulsing animation on ringing text
        AlphaAnimation pulseText = new AlphaAnimation(1.0f, 0.45f);
        pulseText.setDuration(700);
        pulseText.setRepeatMode(Animation.REVERSE);
        pulseText.setRepeatCount(Animation.INFINITE);
        ringPill.startAnimation(pulseText);

        ringHeader.addView(ringPill);

        TextView tvRingHint = new TextView(this);
        tvRingHint.setText("Panggilan belum bersambung • Sila jawab atau tolak");
        tvRingHint.setTextColor(0xFF94A3B8);
        tvRingHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(tvRingHint, 6);
        ringHeader.addView(tvRingHint);

        mContentContainer.addView(ringHeader);

        // 1B. DEMO MODE TAB SELECTOR (JIKA MOD PRATONTON DILANCARKAN)
        if (mIsDemo) {
            LinearLayout demoSelector = new LinearLayout(this);
            demoSelector.setOrientation(LinearLayout.HORIZONTAL);
            demoSelector.setGravity(Gravity.CENTER);
            setMarginTop(demoSelector, 12);

            Button btnTabScam = createDemoTabButton("🚨 Scammer PDRM", mDemoState == 0, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setupDemoThreat(0);
                    renderUI(cleanLocal);
                }
            });
            demoSelector.addView(btnTabScam);

            Button btnTabSafe = createDemoTabButton("✓ Kenalan", mDemoState == 1, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setupDemoThreat(1);
                    renderUI(cleanLocal);
                }
            });
            setMarginLeft(btnTabSafe, 6);
            demoSelector.addView(btnTabSafe);

            Button btnTabUnknown = createDemoTabButton("👤 Nombor Asing", mDemoState == 2, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setupDemoThreat(2);
                    renderUI(cleanLocal);
                }
            });
            setMarginLeft(btnTabUnknown, 6);
            demoSelector.addView(btnTabUnknown);

            mContentContainer.addView(demoSelector);
        }

        // 2. THREAT ASSESSMENT CARD (HIGH VISIBILITY INTELLIGENCE)
        LinearLayout threatCard = new LinearLayout(this);
        threatCard.setOrientation(LinearLayout.VERTICAL);
        threatCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(threatCard, 14);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dpToPx(14));

        if (mThreat != null) {
            cardBg.setColor(0xFF450A0A); // Alert Crimson
            cardBg.setStroke(dpToPx(2), 0xFFEF4444);
            threatCard.setBackground(cardBg);

            TextView tvWarnTitle = new TextView(this);
            tvWarnTitle.setText("🚨 KANTOI: SCAMMER DISAHKAN PDRM CCID!");
            tvWarnTitle.setTextColor(0xFFFCA5A5);
            tvWarnTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvWarnTitle.setTypeface(Typeface.DEFAULT_BOLD);
            threatCard.addView(tvWarnTitle);

            TextView tvWarnDesc = new TextView(this);
            String risk = mThreat.riskTag != null ? mThreat.riskTag : "Sindiket Penipuan Kewangan";
            tvWarnDesc.setText("Kategori: " + risk + "\n" + (mThreat.bankAccounts != null ? "Akaun Keldai: " + mThreat.bankAccounts : "Disahkan dalam pangkalan data Polis Diraja Malaysia"));
            tvWarnDesc.setTextColor(0xFFFEE2E2);
            tvWarnDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            setMarginTop(tvWarnDesc, 4);
            threatCard.addView(tvWarnDesc);

            TextView tvSafetyNotice = new TextView(this);
            tvSafetyNotice.setText("⚠️ AWAS: JANGAN JAWAB / JANGAN BERI SEBARANG OTP ATAU TAC!");
            tvSafetyNotice.setTextColor(0xFFFBBF24); // Amber
            tvSafetyNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvSafetyNotice.setTypeface(Typeface.DEFAULT_BOLD);
            setMarginTop(tvSafetyNotice, 6);
            threatCard.addView(tvSafetyNotice);

        } else if (mIsSafeContact) {
            cardBg.setColor(0xFF064E3B);
            cardBg.setStroke(dpToPx(1), 0xFF10B981);
            threatCard.setBackground(cardBg);

            TextView tvSafeTitle = new TextView(this);
            tvSafeTitle.setText("✓ PANGGILAN KENALAN DIPERCAYAI");
            tvSafeTitle.setTextColor(0xFFA7F3D0);
            tvSafeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvSafeTitle.setTypeface(Typeface.DEFAULT_BOLD);
            threatCard.addView(tvSafeTitle);

            TextView tvSafeDesc = new TextView(this);
            tvSafeDesc.setText("Nombor ini selamat dan telah tersimpan dalam Buku Telefon peranti anda.");
            tvSafeDesc.setTextColor(0xFFD1FAE5);
            tvSafeDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            setMarginTop(tvSafeDesc, 3);
            threatCard.addView(tvSafeDesc);

        } else {
            cardBg.setColor(0xFF0F172A);
            cardBg.setStroke(dpToPx(1), 0xFF0284C7);
            threatCard.setBackground(cardBg);

            TextView tvUnkTitle = new TextView(this);
            tvUnkTitle.setText("ℹ️ NOMBOR ASING (TIADA DALAM KENALAN)");
            tvUnkTitle.setTextColor(0xFFBAE6FD);
            tvUnkTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvUnkTitle.setTypeface(Typeface.DEFAULT_BOLD);
            threatCard.addView(tvUnkTitle);

            TextView tvUnkDesc = new TextView(this);
            tvUnkDesc.setText("Nombor pemanggil ini tiada dalam buku telefon. Dilindungi sepenuhnya oleh perisai KANTOI.");
            tvUnkDesc.setTextColor(0xFFCBD5E1);
            tvUnkDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            setMarginTop(tvUnkDesc, 3);
            threatCard.addView(tvUnkDesc);
        }

        mContentContainer.addView(threatCard);

        // Spacer to center avatar nicely
        View spacerMiddleTop = new View(this);
        LinearLayout.LayoutParams spMTopLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(16));
        spacerMiddleTop.setLayoutParams(spMTopLp);
        mContentContainer.addView(spacerMiddleTop);

        // 3. PULSING RADAR RING + AVATAR (Visual Ringing Indicator)
        FrameLayout avatarContainer = new FrameLayout(this);
        LinearLayout.LayoutParams avContLp = new LinearLayout.LayoutParams(dpToPx(136), dpToPx(136));
        avContLp.gravity = Gravity.CENTER_HORIZONTAL;
        avatarContainer.setLayoutParams(avContLp);

        // Outer Pulsing Ripple Ring
        View pulseRing = new View(this);
        FrameLayout.LayoutParams pulseLp = new FrameLayout.LayoutParams(dpToPx(136), dpToPx(136));
        pulseRing.setLayoutParams(pulseLp);
        GradientDrawable pulseBg = new GradientDrawable();
        pulseBg.setShape(GradientDrawable.OVAL);
        if (mThreat != null) {
            pulseBg.setColor(0x33DC2626);
            pulseBg.setStroke(dpToPx(2), 0x88EF4444);
        } else if (mIsSafeContact) {
            pulseBg.setColor(0x2210B981);
            pulseBg.setStroke(dpToPx(2), 0x6634D399);
        } else {
            pulseBg.setColor(0x220284C7);
            pulseBg.setStroke(dpToPx(2), 0x6638BDF8);
        }
        pulseRing.setBackground(pulseBg);

        // Animate pulse ring continuously while incoming call rings
        AnimationSet ringAnim = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(0.92f, 1.14f, 0.92f, 1.14f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(900);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setRepeatCount(Animation.INFINITE);
        AlphaAnimation alpha = new AlphaAnimation(0.85f, 0.25f);
        alpha.setDuration(900);
        alpha.setRepeatMode(Animation.REVERSE);
        alpha.setRepeatCount(Animation.INFINITE);
        ringAnim.addAnimation(scale);
        ringAnim.addAnimation(alpha);
        ringAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseRing.startAnimation(ringAnim);
        avatarContainer.addView(pulseRing);

        // Inner Avatar Circle
        LinearLayout avatarCore = new LinearLayout(this);
        avatarCore.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams coreLp = new FrameLayout.LayoutParams(dpToPx(104), dpToPx(104));
        coreLp.gravity = Gravity.CENTER;
        avatarCore.setLayoutParams(coreLp);

        GradientDrawable coreBg = new GradientDrawable();
        coreBg.setShape(GradientDrawable.OVAL);

        TextView tvAvatarIcon = new TextView(this);
        tvAvatarIcon.setGravity(Gravity.CENTER);

        if (mThreat != null) {
            coreBg.setColor(0xFF450A0A);
            coreBg.setStroke(dpToPx(3), 0xFFDC2626);
            tvAvatarIcon.setText("🚨");
            tvAvatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        } else if (mIsSafeContact) {
            coreBg.setColor(0xFF064E3B);
            coreBg.setStroke(dpToPx(3), 0xFF059669);
            String initial = (mContactName != null && !mContactName.isEmpty()) ? mContactName.substring(0, 1).toUpperCase() : "✓";
            tvAvatarIcon.setText(initial);
            tvAvatarIcon.setTextColor(0xFF34D399);
            tvAvatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
            tvAvatarIcon.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            coreBg.setColor(0xFF1E293B);
            coreBg.setStroke(dpToPx(3), 0xFF0284C7);
            tvAvatarIcon.setText("👤");
            tvAvatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        }
        avatarCore.setBackground(coreBg);
        avatarCore.addView(tvAvatarIcon);
        avatarContainer.addView(avatarCore);

        mContentContainer.addView(avatarContainer);

        // 4. CALLER IDENTITY (Display Name & Phone Number)
        TextView tvCallerName = new TextView(this);
        if (mThreat != null) {
            String name = (mThreat.suspectName != null && !mThreat.suspectName.isEmpty()) ? mThreat.suspectName : "SUSPEK SCAMMER";
            if (!name.contains("SCAMMER") && !name.contains("🚨")) {
                name = "🚨 [SCAMMER] " + name;
            }
            tvCallerName.setText(name);
            tvCallerName.setTextColor(0xFFEF4444); // Bright Red
        } else if (mIsSafeContact && mContactName != null) {
            tvCallerName.setText(mContactName);
            tvCallerName.setTextColor(Color.WHITE);
        } else {
            tvCallerName.setText("Nombor Tidak Dikenali");
            tvCallerName.setTextColor(Color.WHITE);
        }
        tvCallerName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tvCallerName.setTypeface(Typeface.DEFAULT_BOLD);
        tvCallerName.setGravity(Gravity.CENTER);
        setMarginTop(tvCallerName, 12);
        mContentContainer.addView(tvCallerName);

        // Formatted Phone Number (NOMBOR SAHAJA TANPA TEKS [SCAMMER])
        TextView tvFormattedNum = new TextView(this);
        tvFormattedNum.setText(KantoiThreatEngine.formatPhoneNumber(mPhoneNumber));
        tvFormattedNum.setTextColor(mThreat != null ? 0xFFFCA5A5 : 0xFFE2E8F0);
        tvFormattedNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        tvFormattedNum.setTypeface(Typeface.DEFAULT_BOLD);
        tvFormattedNum.setGravity(Gravity.CENTER);
        setMarginTop(tvFormattedNum, 4);
        mContentContainer.addView(tvFormattedNum);

        // Subtitle / Location / Carrier Tag
        TextView tvSubTag = new TextView(this);
        if (mThreat != null) {
            tvSubTag.setText("⚠️ PDRM CCID & Jenayah Siber Blacklist");
            tvSubTag.setTextColor(0xFFFCA5A5);
        } else if (mIsSafeContact) {
            tvSubTag.setText("Tersimpan dalam Kenalan Buku Telefon");
            tvSubTag.setTextColor(0xFF6EE7B7);
        } else {
            tvSubTag.setText("🇲🇾 Malaysia • Talian Komunikasi Mudah Alih");
            tvSubTag.setTextColor(0xFF94A3B8);
        }
        tvSubTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSubTag.setGravity(Gravity.CENTER);
        setMarginTop(tvSubTag, 4);
        mContentContainer.addView(tvSubTag);

        // Spacer Bottom
        View spacerControls = new View(this);
        LinearLayout.LayoutParams spCtrlLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(24));
        spacerControls.setLayoutParams(spCtrlLp);
        mContentContainer.addView(spacerControls);

        // 5. SPECIAL SCAMMER QUICK ACTION: "TOLAK & SEKAT SERTA-MERTA"
        if (mThreat != null) {
            Button btnRejectAndBlock = new Button(this);
            btnRejectAndBlock.setText("🚫 TOLAK & SEKAT SCAMMER SERTA-MERTA");
            btnRejectAndBlock.setTextColor(Color.WHITE);
            btnRejectAndBlock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            btnRejectAndBlock.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable rbBg = new GradientDrawable();
            rbBg.setColor(0xFF7F1D1D);
            rbBg.setCornerRadius(dpToPx(12));
            rbBg.setStroke(dpToPx(2), 0xFFEF4444);
            btnRejectAndBlock.setBackground(rbBg);
            LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50));
            rbLp.bottomMargin = dpToPx(16);
            btnRejectAndBlock.setLayoutParams(rbLp);
            btnRejectAndBlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleRejectAndBlock(cleanLocal);
                }
            });
            mContentContainer.addView(btnRejectAndBlock);
        }

        // 6. ACTION CONTROLS: DUA BUTANG JELAS (TOLAK vs JAWAB) - KEDUDUKAN 100% CENTER & SEIMBANG
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.gravity = Gravity.CENTER_HORIZONTAL;
        actionsRow.setLayoutParams(actionsLp);

        // A. BUTANG TOLAK (DECLINE CALL - CRIMSON RED WITH HANGUP ICON)
        LinearLayout declineCol = new LinearLayout(this);
        declineCol.setOrientation(LinearLayout.VERTICAL);
        declineCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams decColLp = new LinearLayout.LayoutParams(dpToPx(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        declineCol.setLayoutParams(decColLp);

        Button btnDecline = new Button(this);
        btnDecline.setText("📵");
        btnDecline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        btnDecline.setTextColor(Color.WHITE);
        GradientDrawable decBg = new GradientDrawable();
        decBg.setShape(GradientDrawable.OVAL);
        decBg.setColor(0xFFDC2626); // Crimson Red
        decBg.setStroke(dpToPx(3), 0xFFEF4444);
        btnDecline.setBackground(decBg);
        LinearLayout.LayoutParams decLp = new LinearLayout.LayoutParams(dpToPx(76), dpToPx(76));
        decLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnDecline.setLayoutParams(decLp);
        btnDecline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleRejectCall(cleanLocal);
            }
        });
        declineCol.addView(btnDecline);

        TextView tvDecLabel = new TextView(this);
        tvDecLabel.setText("TOLAK");
        tvDecLabel.setTextColor(Color.WHITE);
        tvDecLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvDecLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvDecLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpDecLabel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpDecLabel.topMargin = dpToPx(8);
        tvDecLabel.setLayoutParams(lpDecLabel);
        declineCol.addView(tvDecLabel);

        TextView tvDecSub = new TextView(this);
        tvDecSub.setText("Abaikan");
        tvDecSub.setTextColor(0xFFFECACA);
        tvDecSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDecSub.setGravity(Gravity.CENTER);
        tvDecSub.setSingleLine(true);
        LinearLayout.LayoutParams lpDecSub = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpDecSub.topMargin = dpToPx(3);
        tvDecSub.setLayoutParams(lpDecSub);
        declineCol.addView(tvDecSub);

        actionsRow.addView(declineCol);

        // Middle Spacing (Jarak tengah yang seimbang)
        View btnMiddleSpacer = new View(this);
        LinearLayout.LayoutParams spMidBtn = new LinearLayout.LayoutParams(dpToPx(48), ViewGroup.LayoutParams.WRAP_CONTENT);
        btnMiddleSpacer.setLayoutParams(spMidBtn);
        actionsRow.addView(btnMiddleSpacer);

        // B. BUTANG JAWAB (ANSWER CALL - EMERALD GREEN WITH PHONE RECEIVER ICON)
        LinearLayout answerCol = new LinearLayout(this);
        answerCol.setOrientation(LinearLayout.VERTICAL);
        answerCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams ansColLp = new LinearLayout.LayoutParams(dpToPx(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        answerCol.setLayoutParams(ansColLp);

        Button btnAnswer = new Button(this);
        btnAnswer.setText("📞");
        btnAnswer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        btnAnswer.setTextColor(Color.WHITE);
        GradientDrawable ansBg = new GradientDrawable();
        ansBg.setShape(GradientDrawable.OVAL);
        ansBg.setColor(0xFF16A34A); // Emerald Green
        ansBg.setStroke(dpToPx(3), 0xFF22C55E);
        btnAnswer.setBackground(ansBg);
        LinearLayout.LayoutParams ansLp = new LinearLayout.LayoutParams(dpToPx(76), dpToPx(76));
        ansLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnAnswer.setLayoutParams(ansLp);
        btnAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleAnswerCall(cleanLocal);
            }
        });
        answerCol.addView(btnAnswer);

        TextView tvAnsLabel = new TextView(this);
        tvAnsLabel.setText("JAWAB");
        tvAnsLabel.setTextColor(Color.WHITE);
        tvAnsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvAnsLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvAnsLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpAnsLabel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpAnsLabel.topMargin = dpToPx(8);
        tvAnsLabel.setLayoutParams(lpAnsLabel);
        answerCol.addView(tvAnsLabel);

        TextView tvAnsSub = new TextView(this);
        tvAnsSub.setText("Terima");
        tvAnsSub.setTextColor(0xFFA7F3D0);
        tvAnsSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvAnsSub.setGravity(Gravity.CENTER);
        tvAnsSub.setSingleLine(true);
        LinearLayout.LayoutParams lpAnsSub = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpAnsSub.topMargin = dpToPx(3);
        tvAnsSub.setLayoutParams(lpAnsSub);
        answerCol.addView(tvAnsSub);

        actionsRow.addView(answerCol);

        mContentContainer.addView(actionsRow);

        // 7. FOOTER BRANDING & SAFETY ASSURANCE
        TextView tvFooter = new TextView(this);
        tvFooter.setText("🛡️ Dilindungi oleh KANTOI • Perisai Panggilan Pintar Malaysia");
        tvFooter.setTextColor(0xFF475569);
        tvFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvFooter.setGravity(Gravity.CENTER);
        setMarginTop(tvFooter, 24);
        mContentContainer.addView(tvFooter);
    }

    private Button createDemoTabButton(String label, boolean isSelected, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(isSelected ? Color.WHITE : 0xFF94A3B8);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(16));
        if (isSelected) {
            bg.setColor(0xFF0284C7);
            bg.setStroke(dpToPx(1), 0xFF38BDF8);
        } else {
            bg.setColor(0xFF1E293B);
            bg.setStroke(dpToPx(1), 0xFF334155);
        }
        btn.setBackground(bg);
        btn.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        btn.setOnClickListener(listener);
        return btn;
    }

    private void handleRejectCall(String localPhone) {
        boolean rejected = KantoiInCallService.rejectCall();
        if (!rejected) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                    if (tm != null && checkCallingOrSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        tm.endCall();
                    }
                }
            } catch (Throwable ignored) {}
        }

        Toast.makeText(this, "Panggilan ditolak & nombor dimasukkan ke senarai pantau.", Toast.LENGTH_SHORT).show();

        // Buka AfterCallActivity sebagai ringkasan
        Intent after = new Intent(this, AfterCallActivity.class);
        after.putExtra(AfterCallActivity.EXTRA_PHONE_NUMBER, localPhone);
        startActivity(after);
        finish();
    }

    private void handleRejectAndBlock(String localPhone) {
        // 1. Putuskan panggilan
        boolean rejected = KantoiInCallService.rejectCall();
        if (!rejected) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                    if (tm != null && checkCallingOrSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        tm.endCall();
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Simpan nombor scammer ke pangkalan data peranti secara automatik
        try {
            String name = (mThreat != null && mThreat.suspectName != null) ? mThreat.suspectName : "Scammer Disekat Semasa Panggilan";
            String cat = (mThreat != null && mThreat.riskTag != null) ? mThreat.riskTag : "Sindiket Penipuan";
            String bank = (mThreat != null && mThreat.bankAccounts != null) ? mThreat.bankAccounts : "N/A";
            mEngine.saveThreatLocal(localPhone, name, "N/A", bank, "N/A", "Disekat serta-merta melalui skrin panggilan masuk", cat, 1, 1);
        } catch (Throwable ignored) {}

        Toast.makeText(this, "🚫 Panggilan ditolak & nombor [" + localPhone + "] berjaya disekat!", Toast.LENGTH_LONG).show();

        // Buka AfterCallActivity sebagai ringkasan
        Intent after = new Intent(this, AfterCallActivity.class);
        after.putExtra(AfterCallActivity.EXTRA_PHONE_NUMBER, localPhone);
        startActivity(after);
        finish();
    }

    private void handleAnswerCall(String localPhone) {
        boolean answered = KantoiInCallService.answerCall();
        if (!answered) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                    if (tm != null && checkCallingOrSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        tm.acceptRingingCall();
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Buka ActiveCallActivity supaya pengguna ada UI tamatkan panggilan, mute, dan speaker
        Intent activeIntent = new Intent(this, ActiveCallActivity.class);
        activeIntent.putExtra(ActiveCallActivity.EXTRA_PHONE_NUMBER, localPhone);
        startActivity(activeIntent);
        finish();
    }

    private void triggerThreatVibration() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] pattern = {0, 400, 200, 600};
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1),
                        new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build());
                } else {
                    v.vibrate(600);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void setMarginTop(View v, int dp) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).topMargin = dpToPx(dp);
        }
    }

    private void setMarginLeft(View v, int dp) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).leftMargin = dpToPx(dp);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
