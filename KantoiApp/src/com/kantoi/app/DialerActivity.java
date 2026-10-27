package com.kantoi.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pendail Telefon Pintar KANTOI (Smart Dialer)
 * Membolehkan pengguna mendail sebarang nombor telefon dengan semakan
 * scammer masa nyata (0ms) dan butang pintasan Panggil / SMS.
 */
public class DialerActivity extends Activity {
    public static final String EXTRA_INITIAL_NUMBER = "extra_initial_number";
    private static final int REQUEST_CALL_PERM = 3001;

    private EditText mEtNumber;
    private TextView mTvThreatBadge;
    private KantoiThreatEngine mEngine;
    private Vibrator mVibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEngine = KantoiThreatEngine.getInstance(this);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF0F172A);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0F172A); // Slate 900
        scrollView.setClipToPadding(false);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(75)); // 75dp bottom padding for floating dock!

        // 1. Top Header Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnBack = new Button(this);
        btnBack.setText("← UTAMA");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnBack.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(0xFF334155);
        backBg.setCornerRadius(dpToPx(6));
        btnBack.setBackground(backBg);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("  📞 PENDAIL PINTAR");
        tvTitle.setTextColor(0xFFF8FAFC);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(tvTitle);

        View topSpacer1 = new View(this);
        topSpacer1.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
        topBar.addView(topSpacer1);

        Button btnContacts = new Button(this);
        btnContacts.setText("📖 BUKU TEL");
        btnContacts.setTextColor(Color.WHITE);
        btnContacts.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnContacts.setTypeface(Typeface.DEFAULT_BOLD);
        btnContacts.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        GradientDrawable conBg = new GradientDrawable();
        conBg.setColor(0xFF0284C7);
        conBg.setCornerRadius(dpToPx(6));
        btnContacts.setBackground(conBg);
        btnContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DialerActivity.this, ContactsActivity.class));
            }
        });
        topBar.addView(btnContacts);

        root.addView(topBar);

        // Upper Spacer: Menurunkan sedikit paparan input nombor dari bar tajuk supaya seimbang
        View topSpacer = new View(this);
        LinearLayout.LayoutParams topSpacerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.35f);
        topSpacer.setLayoutParams(topSpacerLp);
        root.addView(topSpacer);

        // 2. Number Display Section with Backspace
        LinearLayout displayCard = new LinearLayout(this);
        displayCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1E293B);
        cardBg.setCornerRadius(dpToPx(16));
        cardBg.setStroke(dpToPx(1), 0xFF334155);
        displayCard.setBackground(cardBg);
        displayCard.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14));
        displayCard.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        mEtNumber = new EditText(this);
        mEtNumber.setHint("Ketik nombor telefon...");
        mEtNumber.setHintTextColor(0xFF64748B);
        mEtNumber.setTextColor(Color.WHITE);
        mEtNumber.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        mEtNumber.setTypeface(Typeface.DEFAULT_BOLD);
        mEtNumber.setBackground(null);
        mEtNumber.setGravity(Gravity.CENTER);
        mEtNumber.setShowSoftInputOnFocus(false); // Guna pad kekunci binaan dalam
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mEtNumber.setLayoutParams(etLp);
        inputRow.addView(mEtNumber);

        // Butang Padam (Backspace)
        Button btnBackspace = new Button(this);
        btnBackspace.setText("⌫");
        btnBackspace.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        btnBackspace.setTextColor(0xFFCBD5E1);
        GradientDrawable bsBg = new GradientDrawable();
        bsBg.setColor(0xFF334155);
        bsBg.setCornerRadius(dpToPx(8));
        btnBackspace.setBackground(bsBg);
        LinearLayout.LayoutParams bsLp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(44));
        btnBackspace.setLayoutParams(bsLp);
        btnBackspace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticFeedback();
                int length = mEtNumber.getText().length();
                if (length > 0) {
                    mEtNumber.getText().delete(length - 1, length);
                }
            }
        });
        btnBackspace.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                hapticFeedback();
                mEtNumber.setText("");
                return true;
            }
        });
        inputRow.addView(btnBackspace);
        displayCard.addView(inputRow);

        // Threat live preview badge below input
        mTvThreatBadge = new TextView(this);
        mTvThreatBadge.setText("💡 Ketik nombor untuk semakan perlindungan scammer 0ms");
        mTvThreatBadge.setTextColor(0xFF94A3B8);
        mTvThreatBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvThreatBadge.setTypeface(Typeface.DEFAULT_BOLD);
        mTvThreatBadge.setGravity(Gravity.CENTER);
        setMarginTop(mTvThreatBadge, 8);
        displayCard.addView(mTvThreatBadge);

        root.addView(displayCard);

        mEtNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                evaluateNumberThreat(s.toString().trim());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Middle Spacer: Menurunkan butang dail ke bawah ke zon capaian ibu jari (Thumb Zone)
        View midSpacer = new View(this);
        LinearLayout.LayoutParams midSpacerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.65f);
        midSpacer.setLayoutParams(midSpacerLp);
        root.addView(midSpacer);

        // 3. Dialpad Grid (1-9, *, 0, #)
        LinearLayout keypadLayout = new LinearLayout(this);
        keypadLayout.setOrientation(LinearLayout.VERTICAL);
        keypadLayout.setGravity(Gravity.CENTER);
        keypadLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        keypadLayout.addView(createKeypadRow(
            new KeypadKey("1", ""),
            new KeypadKey("2", "ABC"),
            new KeypadKey("3", "DEF")
        ));
        keypadLayout.addView(createKeypadRow(
            new KeypadKey("4", "GHI"),
            new KeypadKey("5", "JKL"),
            new KeypadKey("6", "MNO")
        ));
        keypadLayout.addView(createKeypadRow(
            new KeypadKey("7", "PQRS"),
            new KeypadKey("8", "TUV"),
            new KeypadKey("9", "WXYZ")
        ));
        keypadLayout.addView(createKeypadRow(
            new KeypadKey("*", ""),
            new KeypadKey("0", "+"),
            new KeypadKey("#", "")
        ));

        root.addView(keypadLayout);

        // 4. Action Row (Call, SMS, Profile)
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER);
        setMarginTop(actionsRow, 22);

        // SMS Shortcut Button
        Button btnSms = createRoundButton("💬", 0xFF0284C7, dpToPx(56));
        btnSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String num = mEtNumber.getText().toString().trim();
                Intent smsIntent = new Intent(DialerActivity.this, SmsActivity.class);
                if (!num.isEmpty()) {
                    smsIntent.putExtra(SmsActivity.EXTRA_RECIPIENT, num);
                }
                startActivity(smsIntent);
            }
        });
        actionsRow.addView(btnSms);

        // Spacer
        actionsRow.addView(createHorizontalSpacer(dpToPx(28)));

        // Main Call Button (Green)
        Button btnCall = createRoundButton("📞", 0xFF16A34A, dpToPx(76));
        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initiateCall();
            }
        });
        actionsRow.addView(btnCall);

        // Spacer
        actionsRow.addView(createHorizontalSpacer(dpToPx(28)));

        // Profile / Risk Dossier Button
        Button btnInfo = createRoundButton("👤", 0xFF475569, dpToPx(56));
        btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String num = mEtNumber.getText().toString().trim();
                if (num.isEmpty()) {
                    Toast.makeText(DialerActivity.this, "Sila masukkan nombor telefon.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent pIt = new Intent(DialerActivity.this, ContactProfileActivity.class);
                pIt.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, num);
                startActivity(pIt);
            }
        });
        actionsRow.addView(btnInfo);

        root.addView(actionsRow);

        // Footer info
        TextView tvFoot = new TextView(this);
        tvFoot.setText("🛡️ KANTOI Smart Dialer • Panggilan Dilindungi");
        tvFoot.setTextColor(0xFF64748B);
        tvFoot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvFoot.setGravity(Gravity.CENTER);
        setMarginTop(tvFoot, 18);
        root.addView(tvFoot);

        scrollView.addView(root);
        mainLayout.addView(scrollView);

        // Persistent Floating Bottom Bar
        View bottomBar = KantoiBottomBar.buildBottomNavBar(this, KantoiBottomBar.TAB_KEYPAD);
        mainLayout.addView(bottomBar);

        setContentView(mainLayout);

        // Check if initial number was passed
        Intent it = getIntent();
        if (it != null) {
            String init = it.getStringExtra(EXTRA_INITIAL_NUMBER);
            if (init != null && !init.isEmpty()) {
                mEtNumber.setText(init);
                mEtNumber.setSelection(init.length());
            } else if (it.getData() != null && "tel".equalsIgnoreCase(it.getData().getScheme())) {
                String tel = it.getData().getSchemeSpecificPart();
                if (tel != null && !tel.isEmpty()) {
                    mEtNumber.setText(tel);
                    mEtNumber.setSelection(tel.length());
                }
            }
        }
    }

    private void evaluateNumberThreat(String raw) {
        if (raw.length() < 3) {
            mTvThreatBadge.setText("💡 Ketik nombor untuk semakan perlindungan scammer 0ms");
            mTvThreatBadge.setTextColor(0xFF94A3B8);
            return;
        }

        String local = KantoiThreatEngine.normalizeToLocal(raw);
        if (mEngine.isSafeContactFast(local)) {
            String name = mEngine.getContactDisplayNameFast(local);
            mTvThreatBadge.setText("✅ KENALAN SELAMAT: " + (name != null ? name : local));
            mTvThreatBadge.setTextColor(0xFF34D399); // Emerald
            return;
        }

        KantoiThreatEngine.ScammerThreat threat = mEngine.checkThreat(local);
        if (threat != null) {
            mTvThreatBadge.setText("🚨 AMARAN SCAMMER: " + threat.suspectName + " (" + threat.riskTag + ")");
            mTvThreatBadge.setTextColor(0xFFEF4444); // Crimson
        } else {
            mTvThreatBadge.setText("ℹ️ Nombor Asing (Tiada rekod penipuan dalam pangkalan data)");
            mTvThreatBadge.setTextColor(0xFF38BDF8); // Sky blue
        }
    }

    private void initiateCall() {
        String num = mEtNumber.getText().toString().trim();
        if (num.isEmpty()) {
            Toast.makeText(this, "Sila masukkan nombor untuk mendail.", Toast.LENGTH_SHORT).show();
            return;
        }

        hapticFeedback();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num)));
                try {
                    startActivity(callIntent);
                    return;
                } catch (Throwable ignored) {}
            } else {
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PERM);
                return;
            }
        }

        // Fallback to ACTION_DIAL
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num)));
        startActivity(dialIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALL_PERM) {
            initiateCall();
        }
    }

    private static class KeypadKey {
        String digit;
        String letters;
        KeypadKey(String d, String l) { this.digit = d; this.letters = l; }
    }

    private LinearLayout createKeypadRow(KeypadKey k1, KeypadKey k2, KeypadKey k3) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        setMarginTop(row, 18); // Jarakkan baris butang dial secara menegak

        row.addView(createKeypadButton(k1));
        row.addView(createHorizontalSpacer(dpToPx(30))); // Jarakkan butang dial secara mendatar (30dp)
        row.addView(createKeypadButton(k2));
        row.addView(createHorizontalSpacer(dpToPx(30))); // Jarakkan butang dial secara mendatar (30dp)
        row.addView(createKeypadButton(k3));

        return row;
    }

    private View createKeypadButton(final KeypadKey key) {
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.VERTICAL);
        btnLayout.setGravity(Gravity.CENTER);

        int size = dpToPx(72);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        btnLayout.setLayoutParams(lp);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.OVAL);
        normalBg.setColor(0xFF131B2E);
        normalBg.setStroke(dpToPx(1), 0xFF1E293B);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList rippleColor = ColorStateList.valueOf(0x3338BDF8); // Soft cyan ripple
            btnLayout.setBackground(new RippleDrawable(rippleColor, normalBg, null));
        } else {
            btnLayout.setBackground(normalBg);
        }

        TextView tvDigit = new TextView(this);
        tvDigit.setText(key.digit);
        tvDigit.setTextColor(Color.WHITE);
        tvDigit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tvDigit.setTypeface(Typeface.DEFAULT_BOLD);
        tvDigit.setGravity(Gravity.CENTER);
        tvDigit.setIncludeFontPadding(false);
        btnLayout.addView(tvDigit);

        if (!key.letters.isEmpty()) {
            TextView tvSub = new TextView(this);
            tvSub.setText(key.letters);
            tvSub.setTextColor(0xFF94A3B8);
            tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tvSub.setTypeface(Typeface.DEFAULT_BOLD);
            tvSub.setIncludeFontPadding(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tvSub.setLetterSpacing(0.08f);
            }
            tvSub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subLp.topMargin = -dpToPx(3);
            tvSub.setLayoutParams(subLp);
            btnLayout.addView(tvSub);
        }

        btnLayout.setClickable(true);
        btnLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticFeedback();
                mEtNumber.append(key.digit);
            }
        });

        if ("0".equals(key.digit)) {
            btnLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    hapticFeedback();
                    mEtNumber.append("+");
                    return true;
                }
            });
        }

        return btnLayout;
    }

    private Button createRoundButton(String text, int bgColor, int sizePx) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        btn.setTextColor(Color.WHITE);
        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.OVAL);
        normalBg.setColor(bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList rippleColor = ColorStateList.valueOf(0x44FFFFFF);
            btn.setBackground(new RippleDrawable(rippleColor, normalBg, null));
        } else {
            btn.setBackground(normalBg);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        btn.setLayoutParams(lp);
        return btn;
    }

    private View createHorizontalSpacer(int widthPx) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, 1);
        v.setLayoutParams(lp);
        return v;
    }

    private void hapticFeedback() {
        try {
            if (mVibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mVibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    mVibrator.vibrate(30);
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

    @Override
    protected void onResume() {
        super.onResume();
        KantoiThreatEngine.ensureFloatingPill(this);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
