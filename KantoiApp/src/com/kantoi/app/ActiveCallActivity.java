package com.kantoi.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * GUI Panggilan Sedang Berlangsung (Active In-Call Screen)
 * Menyediakan paparan masa panggilan berjalan secara langsung,
 * kawalan mikrofon (Bisu / Mute), pembesar suara (Speaker), pad DTMF,
 * serta butang TAMATKAN PANGGILAN AKTIF yang tidak mengelirukan dengan skrin berdering.
 */
public class ActiveCallActivity extends Activity {
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";
    public static final String EXTRA_LAUNCH_COUNTER_STRIKE = "extra_launch_counter_strike";
    public static final String ACTION_CALL_ENDED = "com.kantoi.app.ACTION_CALL_ENDED";

    private String mPhoneNumber = "";
    private boolean mLaunchCounterStrike = false;
    private KantoiThreatEngine mEngine;
    private KantoiThreatEngine.ScammerThreat mThreat;
    private boolean mIsSafeContact = false;
    private String mContactName = null;

    private boolean mIsMuted = false;
    private boolean mIsSpeakerOn = false;
    private boolean mIsKeypadVisible = false;

    private Button mBtnMute;
    private Button mBtnSpeaker;
    private LinearLayout mDtmfKeypadContainer;
    private Chronometer mChronometer;

    private TextView mTvCsTactic;
    private TextView mTvCsSpeech;
    private Button mBtnCsToggleTactic;
    private Button mBtnCsStop;

    private final BroadcastReceiver mCallEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupWindowFlags();

        mEngine = KantoiThreatEngine.getInstance(this);

        Intent intent = getIntent();
        if (intent != null) {
            mPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
            mLaunchCounterStrike = intent.getBooleanExtra(EXTRA_LAUNCH_COUNTER_STRIKE, false);
        }
        if (mPhoneNumber == null || mPhoneNumber.trim().isEmpty()) {
            mPhoneNumber = "0112345678";
        }

        final String cleanLocal = KantoiThreatEngine.normalizeToLocal(mPhoneNumber);
        mThreat = mEngine.checkThreat(cleanLocal);
        mIsSafeContact = mEngine.isSafeContact(cleanLocal);
        mContactName = mEngine.getContactDisplayName(cleanLocal);

        registerReceiver(mCallEndReceiver, new IntentFilter(ACTION_CALL_ENDED));

        View uiView = buildLayout(cleanLocal);
        setContentView(uiView);
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
        try {
            KantoiCounterStrikeEngine.getInstance(this).stopTts();
        } catch (Throwable ignored) {}
    }

    private View buildLayout(final String cleanLocal) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF070B14); // Deep Night Black

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dpToPx(20), dpToPx(38), dpToPx(20), dpToPx(32));

        // 1. ACTIVE CALL STATUS PILL (JELAS: PANGGILAN AKTIF SEDANG BERBUAL)
        LinearLayout topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.VERTICAL);
        topHeader.setGravity(Gravity.CENTER);

        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dpToPx(20));
        pillBg.setColor(0xFF064E3B); // Deep Emerald Green
        pillBg.setStroke(dpToPx(1), 0xFF10B981);
        statusPill.setBackground(pillBg);

        TextView tvDot = new TextView(this);
        tvDot.setText("🟢");
        tvDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDot.setPadding(0, 0, dpToPx(6), 0);
        statusPill.addView(tvDot);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("PANGGILAN AKTIF • SEDANG BERBUAL");
        tvStatus.setTextColor(0xFFA7F3D0);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.addView(tvStatus);
        topHeader.addView(statusPill);

        TextView tvSubHint = new TextView(this);
        tvSubHint.setText("Panggilan telah bersambung • Mikrofon aktif");
        tvSubHint.setTextColor(0xFF94A3B8);
        tvSubHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSubHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subHintLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subHintLp.gravity = Gravity.CENTER_HORIZONTAL;
        subHintLp.topMargin = dpToPx(6);
        tvSubHint.setLayoutParams(subHintLp);
        topHeader.addView(tvSubHint);

        root.addView(topHeader);

        // 2. LIVE DURATION CARD (DIGITAL CHRONOMETER)
        LinearLayout timerCard = new LinearLayout(this);
        timerCard.setOrientation(LinearLayout.VERTICAL);
        timerCard.setGravity(Gravity.CENTER);
        timerCard.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12));
        LinearLayout.LayoutParams timerCardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timerCardLp.topMargin = dpToPx(12);
        timerCard.setLayoutParams(timerCardLp);

        GradientDrawable timerBg = new GradientDrawable();
        timerBg.setColor(0xFF141422);
        timerBg.setCornerRadius(dpToPx(14));
        timerBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        timerCard.setBackground(timerBg);

        mChronometer = new Chronometer(this);
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.setTextColor(Color.WHITE);
        mChronometer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        mChronometer.setTypeface(Typeface.DEFAULT_BOLD);
        mChronometer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chronoLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chronoLp.gravity = Gravity.CENTER_HORIZONTAL;
        mChronometer.setLayoutParams(chronoLp);
        mChronometer.start();
        timerCard.addView(mChronometer);

        TextView tvTimerLabel = new TextView(this);
        tvTimerLabel.setText("Tempoh Panggilan Langsung");
        tvTimerLabel.setTextColor(0xFF38BDF8);
        tvTimerLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTimerLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvTimerLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timerLabelLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timerLabelLp.gravity = Gravity.CENTER_HORIZONTAL;
        timerLabelLp.topMargin = dpToPx(4);
        tvTimerLabel.setLayoutParams(timerLabelLp);
        timerCard.addView(tvTimerLabel);

        root.addView(timerCard);

        // Spacer Top
        View spTop = new View(this);
        LinearLayout.LayoutParams spTopLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(16));
        spTop.setLayoutParams(spTopLp);
        root.addView(spTop);

        // 3. CALLER AVATAR (CALM STEADY FRAME - NO RINGING RIPPLE)
        LinearLayout avatarFrame = new LinearLayout(this);
        avatarFrame.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dpToPx(104), dpToPx(104));
        avLp.gravity = Gravity.CENTER_HORIZONTAL;
        avatarFrame.setLayoutParams(avLp);

        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);

        TextView tvAvatarIcon = new TextView(this);
        tvAvatarIcon.setGravity(Gravity.CENTER);

        if (mThreat != null) {
            avBg.setColor(0xFF450A0A);
            avBg.setStroke(dpToPx(3), 0xFFDC2626);
            tvAvatarIcon.setText("🚨");
            tvAvatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        } else if (mIsSafeContact) {
            avBg.setColor(0xFF064E3B);
            avBg.setStroke(dpToPx(3), 0xFF059669);
            String initial = (mContactName != null && !mContactName.isEmpty()) ? mContactName.substring(0, 1).toUpperCase() : "✓";
            tvAvatarIcon.setText(initial);
            tvAvatarIcon.setTextColor(0xFF34D399);
            tvAvatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
            tvAvatarIcon.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            avBg.setColor(0xFF141422);
            avBg.setStroke(dpToPx(3), 0xFF0284C7);
            tvAvatarIcon.setText("👤");
            tvAvatarIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        }
        avatarFrame.setBackground(avBg);
        avatarFrame.addView(tvAvatarIcon);
        root.addView(avatarFrame);

        // 4. CALLER NAME & FORMATTED NUMBER
        TextView tvCallerName = new TextView(this);
        if (mThreat != null) {
            String name = (mThreat.suspectName != null && !mThreat.suspectName.isEmpty()) ? mThreat.suspectName : "SCAMMER DISAHKAN PDRM";
            tvCallerName.setText(name);
            tvCallerName.setTextColor(0xFFF87171);
        } else if (mIsSafeContact && mContactName != null) {
            tvCallerName.setText(mContactName);
            tvCallerName.setTextColor(Color.WHITE);
        } else {
            tvCallerName.setText("Nombor Tidak Dikenali");
            tvCallerName.setTextColor(Color.WHITE);
        }
        tvCallerName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvCallerName.setTypeface(Typeface.DEFAULT_BOLD);
        tvCallerName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.gravity = Gravity.CENTER_HORIZONTAL;
        nameLp.topMargin = dpToPx(12);
        tvCallerName.setLayoutParams(nameLp);
        root.addView(tvCallerName);

        TextView tvFormattedNum = new TextView(this);
        tvFormattedNum.setText(KantoiThreatEngine.formatPhoneNumber(mPhoneNumber));
        tvFormattedNum.setTextColor(0xFFCBD5E1);
        tvFormattedNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvFormattedNum.setTypeface(Typeface.DEFAULT_BOLD);
        tvFormattedNum.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numLp.gravity = Gravity.CENTER_HORIZONTAL;
        numLp.topMargin = dpToPx(4);
        tvFormattedNum.setLayoutParams(numLp);
        root.addView(tvFormattedNum);

        // Threat Pill if scammer (Centered & Professionally Badged)
        if (mThreat != null) {
            LinearLayout threatBadge = new LinearLayout(this);
            threatBadge.setOrientation(LinearLayout.HORIZONTAL);
            threatBadge.setGravity(Gravity.CENTER);
            threatBadge.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

            GradientDrawable threatBg = new GradientDrawable();
            threatBg.setCornerRadius(dpToPx(16));
            threatBg.setColor(0xFF450A0A); // Deep Crimson Red
            threatBg.setStroke(dpToPx(1), 0xFFEF4444); // Red-500
            threatBadge.setBackground(threatBg);

            TextView tvThreatPill = new TextView(this);
            tvThreatPill.setText("🚨 Kategori: " + (mThreat.riskTag != null ? mThreat.riskTag : "Scam/Mule"));
            tvThreatPill.setTextColor(0xFFFECACA);
            tvThreatPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvThreatPill.setTypeface(Typeface.DEFAULT_BOLD);
            tvThreatPill.setGravity(Gravity.CENTER);
            threatBadge.addView(tvThreatPill);

            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillLp.gravity = Gravity.CENTER_HORIZONTAL;
            pillLp.topMargin = dpToPx(10);
            threatBadge.setLayoutParams(pillLp);

            root.addView(threatBadge);
        }

        // Counter-Strike Command Panel if threat or launched
        if (mThreat != null || mLaunchCounterStrike) {
            View csPanel = buildCounterStrikeLivePanel(cleanLocal);
            root.addView(csPanel);
        }

        // Spacer Middle
        View spMid = new View(this);
        LinearLayout.LayoutParams spMidLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(20));
        spMid.setLayoutParams(spMidLp);
        root.addView(spMid);

        // 5. IN-CALL CONTROLS TOOLBAR (MUTE, SPEAKER, DTMF KEYPAD)
        LinearLayout controlsCard = new LinearLayout(this);
        controlsCard.setOrientation(LinearLayout.VERTICAL);
        controlsCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        GradientDrawable ctrlCardBg = new GradientDrawable();
        ctrlCardBg.setColor(0xFF141422);
        ctrlCardBg.setCornerRadius(dpToPx(16));
        ctrlCardBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        controlsCard.setBackground(ctrlCardBg);

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);

        // Mute Toggle Button
        mBtnMute = createCircleControlButton("🔇", "Bisu", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsMuted = !mIsMuted;
                KantoiInCallService.setMute(mIsMuted);
                updateControlHighlight(mBtnMute, mIsMuted);
                Toast.makeText(ActiveCallActivity.this, mIsMuted ? "Mikrofon dibisukan." : "Mikrofon diaktifkan semula.", Toast.LENGTH_SHORT).show();
            }
        });
        controlsRow.addView(mBtnMute);

        // Spacer
        controlsRow.addView(createSpacer(dpToPx(28)));

        // Speaker Toggle Button
        mBtnSpeaker = createCircleControlButton("🔊", "Speaker", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSpeakerOn = !mIsSpeakerOn;
                KantoiInCallService.setSpeaker(mIsSpeakerOn);
                updateControlHighlight(mBtnSpeaker, mIsSpeakerOn);
                Toast.makeText(ActiveCallActivity.this, mIsSpeakerOn ? "Pembesar suara dihidupkan." : "Pembesar suara dimatikan.", Toast.LENGTH_SHORT).show();
            }
        });
        controlsRow.addView(mBtnSpeaker);

        // Spacer
        controlsRow.addView(createSpacer(dpToPx(28)));

        // DTMF Keypad Button
        Button btnKeypad = createCircleControlButton("🔢", "Pad Dail", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsKeypadVisible = !mIsKeypadVisible;
                mDtmfKeypadContainer.setVisibility(mIsKeypadVisible ? View.VISIBLE : View.GONE);
            }
        });
        controlsRow.addView(btnKeypad);

        controlsCard.addView(controlsRow);

        // Expandable In-Call DTMF Keypad (for automated banking/IVR menus)
        mDtmfKeypadContainer = buildDtmfKeypad();
        mDtmfKeypadContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams kpLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kpLp.topMargin = dpToPx(14);
        mDtmfKeypadContainer.setLayoutParams(kpLp);
        controlsCard.addView(mDtmfKeypadContainer);

        root.addView(controlsCard);

        // Spacer Bottom
        View spBot = new View(this);
        LinearLayout.LayoutParams spBotLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(24));
        spBot.setLayoutParams(spBotLp);
        root.addView(spBot);

        // 6. PROMINENT WIDE "TAMATKAN PANGGILAN AKTIF" (END CALL) ACTION
        Button btnEndCall = new Button(this);
        btnEndCall.setText("📵 TAMATKAN PANGGILAN AKTIF");
        btnEndCall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnEndCall.setTextColor(Color.WHITE);
        btnEndCall.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable endBg = new GradientDrawable();
        endBg.setCornerRadius(dpToPx(28));
        endBg.setColor(0xFFDC2626); // Crimson Red
        endBg.setStroke(dpToPx(2), 0xFFEF4444);
        btnEndCall.setBackground(endBg);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56));
        btnEndCall.setLayoutParams(endLp);
        btnEndCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleEndCall(cleanLocal);
            }
        });
        root.addView(btnEndCall);

        TextView tvEndHint = new TextView(this);
        tvEndHint.setText("Tekan butang di atas untuk putuskan sambungan perbualan");
        tvEndHint.setTextColor(0xFF94A3B8);
        tvEndHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvEndHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams endHintLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endHintLp.gravity = Gravity.CENTER_HORIZONTAL;
        endHintLp.topMargin = dpToPx(8);
        tvEndHint.setLayoutParams(endHintLp);
        root.addView(tvEndHint);

        scrollView.addView(root);
        return scrollView;
    }

    private void handleEndCall(String localPhone) {
        KantoiInCallService.disconnectCall();
        Toast.makeText(this, "Panggilan ditamatkan.", Toast.LENGTH_SHORT).show();

        // Launch AfterCallActivity
        Intent afterIntent = new Intent(this, AfterCallActivity.class);
        afterIntent.putExtra(AfterCallActivity.EXTRA_PHONE_NUMBER, localPhone);
        startActivity(afterIntent);
        finish();
    }

    private Button createCircleControlButton(String icon, String label, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(icon + "\n" + label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF141422);
        bg.setStroke(dpToPx(1), 0xFF334155);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(68), dpToPx(68));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void updateControlHighlight(Button btn, boolean active) {
        GradientDrawable bg = (GradientDrawable) btn.getBackground();
        if (bg != null) {
            bg.setColor(active ? 0xFF00D4FF : 0xFF141422);
            bg.setStroke(dpToPx(2), active ? 0xFF38BDF8 : 0xFF334155);
        }
    }

    private View createSpacer(int widthPx) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, 1);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout buildDtmfKeypad() {
        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        keypad.setGravity(Gravity.CENTER);
        GradientDrawable padBg = new GradientDrawable();
        padBg.setColor(0xFF070B14);
        padBg.setCornerRadius(dpToPx(14));
        padBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        keypad.setBackground(padBg);
        keypad.setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14));

        char[][] rows = {
            {'1', '2', '3'},
            {'4', '5', '6'},
            {'7', '8', '9'},
            {'*', '0', '#'}
        };

        for (int i = 0; i < rows.length; i++) {
            char[] row = rows[i];
            LinearLayout rLayout = new LinearLayout(this);
            rLayout.setOrientation(LinearLayout.HORIZONTAL);
            rLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rLp.topMargin = dpToPx(10); // Clear vertical gap between rows
            }
            rLayout.setLayoutParams(rLp);

            for (final char d : row) {
                Button btn = new Button(this);
                btn.setText(String.valueOf(d));
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                btn.setTextColor(Color.WHITE);
                btn.setTypeface(Typeface.DEFAULT_BOLD);
                btn.setGravity(Gravity.CENTER);

                GradientDrawable bBg = new GradientDrawable();
                bBg.setColor(0xFF141422); // Slate-800
                bBg.setCornerRadius(dpToPx(12));
                bBg.setStroke(dpToPx(1), 0xFF334155); // Slate-700
                btn.setBackground(bBg);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(80), dpToPx(56));
                lp.setMargins(dpToPx(6), 0, dpToPx(6), 0); // 12dp horizontal gap between buttons
                btn.setLayoutParams(lp);

                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        } catch (Exception ignored) {}
                        KantoiInCallService.playDtmf(d);
                    }
                });
                rLayout.addView(btn);
            }
            keypad.addView(rLayout);
        }

        return keypad;
    }

    private void setMarginTop(View v, int dp) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).topMargin = dpToPx(dp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpToPx(dp);
            v.setLayoutParams(lp);
        }
    }

    private View buildCounterStrikeLivePanel(final String cleanLocal) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(card, 12);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141422);
        bg.setCornerRadius(dpToPx(14));
        bg.setStroke(dpToPx(2), 0xFF00D4FF);
        card.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("⚡ KANTOI COUNTER-STRIKE COMMAND CENTER");
        tvTitle.setTextColor(0xFF00D4FF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvTitle);

        mTvCsTactic = new TextView(this);
        mTvCsTactic.setText("Memulakan enjin tindak balas siber...");
        mTvCsTactic.setTextColor(0xFF00FF88);
        mTvCsTactic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mTvCsTactic.setTypeface(Typeface.DEFAULT_BOLD);
        setMarginTop(mTvCsTactic, 4);
        card.addView(mTvCsTactic);

        // Speech bubble
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        setMarginTop(bubble, 8);
        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(0xFF0A0A0F);
        bBg.setCornerRadius(dpToPx(10));
        bBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        bubble.setBackground(bBg);

        mTvCsSpeech = new TextView(this);
        mTvCsSpeech.setText("🎙️ Menyambung talian suara ke scammer...");
        mTvCsSpeech.setTextColor(0xFFCBD5E1);
        mTvCsSpeech.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bubble.addView(mTvCsSpeech);
        card.addView(bubble);

        // Controls Row
        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        setMarginTop(ctrlRow, 10);

        mBtnCsToggleTactic = new Button(this);
        mBtnCsToggleTactic.setText("🎲 TUKAR TAKTIK");
        mBtnCsToggleTactic.setTextColor(0xFF00D4FF);
        mBtnCsToggleTactic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnCsToggleTactic.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable togBg = new GradientDrawable();
        togBg.setColor(0xFF141422);
        togBg.setCornerRadius(dpToPx(8));
        togBg.setStroke(dpToPx(1), 0xFF00D4FF);
        mBtnCsToggleTactic.setBackground(togBg);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
        tLp.rightMargin = dpToPx(6);
        mBtnCsToggleTactic.setLayoutParams(tLp);
        mBtnCsToggleTactic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiCounterStrikeEngine eng = KantoiCounterStrikeEngine.getInstance(ActiveCallActivity.this);
                int currentMode = eng.getMode();
                int nextMode;
                if (currentMode == KantoiCounterStrikeEngine.MODE_TACTIC_A_PDRM) {
                    nextMode = KantoiCounterStrikeEngine.MODE_TACTIC_B_ACOUSTIC;
                } else if (currentMode == KantoiCounterStrikeEngine.MODE_TACTIC_B_ACOUSTIC) {
                    nextMode = KantoiCounterStrikeEngine.MODE_TACTIC_C_SILENT;
                } else {
                    nextMode = KantoiCounterStrikeEngine.MODE_TACTIC_A_PDRM;
                }
                eng.setMode(nextMode);
                eng.stopTts();
                startCounterStrikeExecution(cleanLocal);
            }
        });
        ctrlRow.addView(mBtnCsToggleTactic);

        mBtnCsStop = new Button(this);
        mBtnCsStop.setText("⏹️ HENTIKAN SUARA");
        mBtnCsStop.setTextColor(0xFFFF3366);
        mBtnCsStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnCsStop.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(0xFF141422);
        stopBg.setCornerRadius(dpToPx(8));
        stopBg.setStroke(dpToPx(1), 0xFFFF3366);
        mBtnCsStop.setBackground(stopBg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
        mBtnCsStop.setLayoutParams(sLp);
        mBtnCsStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiCounterStrikeEngine.getInstance(ActiveCallActivity.this).stopTts();
                if (mTvCsSpeech != null) {
                    mTvCsSpeech.setText("⏹️ Suara dihentikan. Mikrofon kini terus ke pemanggil.");
                }
            }
        });
        ctrlRow.addView(mBtnCsStop);

        card.addView(ctrlRow);

        // Auto trigger execution
        startCounterStrikeExecution(cleanLocal);

        return card;
    }

    private void startCounterStrikeExecution(String cleanLocal) {
        mIsSpeakerOn = true;
        updateControlHighlight(mBtnSpeaker, true);
        KantoiInCallService.setSpeaker(true);
        KantoiCounterStrikeEngine engine = KantoiCounterStrikeEngine.getInstance(this);
        engine.executeCounterStrike(cleanLocal, mThreat, new KantoiCounterStrikeEngine.CounterStrikeListener() {
            @Override
            public void onTacticStarted(final int tactic, final String tacticName) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvCsTactic != null) {
                            mTvCsTactic.setText(tacticName);
                        }
                    }
                });
            }

            @Override
            public void onSpeechSpoken(final String text, final int sequenceIndex, final int totalSequences) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvCsSpeech != null) {
                            mTvCsSpeech.setText("💬 [" + sequenceIndex + "/" + totalSequences + "] \"" + text + "\"");
                        }
                    }
                });
            }

            @Override
            public void onTacticFinished() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvCsSpeech != null) {
                            mTvCsSpeech.setText("✅ Tindak balas selesai. Scammer telah dilayan & bukti direkod.");
                        }
                    }
                });
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
