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
import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.List;

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

    // FASA 2 & 3: T9 & SPEED DIAL
    private LinearLayout mT9Container;
    private final List<KantoiT9SearchEngine.T9Contact> mCachedContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEngine = KantoiThreatEngine.getInstance(this);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        loadContactsForT9();

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF0A0A0F);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0A0A0F); // Slate 900
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
        btnBack.setTextColor(0xFF94A3B8);
        btnBack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnBack.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(0xFF141422);
        backBg.setStroke(dpToPx(1), 0xFF2A2A3A);
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
        tvTitle.setTextColor(0xFF00D4FF);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(tvTitle);

        View topSpacer1 = new View(this);
        topSpacer1.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
        topBar.addView(topSpacer1);

        Button btnContacts = new Button(this);
        btnContacts.setText("📖 BUKU TEL");
        btnContacts.setTextColor(0xFF00D4FF);
        btnContacts.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnContacts.setTypeface(Typeface.DEFAULT_BOLD);
        btnContacts.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        GradientDrawable conBg = new GradientDrawable();
        conBg.setColor(0xFF141422);
        conBg.setStroke(dpToPx(1), 0xFF00D4FF);
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
        cardBg.setColor(0xFF141422);
        cardBg.setStroke(dpToPx(1), 0xFF2A2A3A);
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
                String q = s.toString().trim();
                evaluateNumberThreat(q);
                updateT9Suggestions(q);
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
        Button btnSms = createRoundButton("💬", 0xFF141422, dpToPx(56));
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
        Button btnCall = createRoundButton("📞", 0xFF00D4FF, dpToPx(76));
        btnCall.setTextColor(Color.BLACK);
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
        Button btnInfo = createRoundButton("👤", 0xFF141422, dpToPx(56));
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
        final String num = mEtNumber.getText().toString().trim();
        if (num.isEmpty()) {
            Toast.makeText(this, "Sila masukkan nombor untuk mendail.", Toast.LENGTH_SHORT).show();
            return;
        }

        hapticFeedback();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PERM);
                return;
            }

            // FASA 2: SOKONGAN DWI-SIM (DUAL-SIM SELECTOR)
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    List<PhoneAccountHandle> accounts = tm.getCallCapablePhoneAccounts();
                    if (accounts != null && accounts.size() > 1) {
                        showDualSimSelector(num, accounts);
                        return;
                    }
                } catch (Throwable ignored) {}
            }

            performCallWithAccount(num, null);
            return;
        }

        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num)));
        startActivity(dialIntent);
    }

    private void showDualSimSelector(final String num, final List<PhoneAccountHandle> accounts) {
        String[] simOptions = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            simOptions[i] = "\uD83D\uDCF1 Panggil Menggunakan Kad SIM " + (i + 1);
        }

        new AlertDialog.Builder(this)
            .setTitle("PILIH KAD SIM UNTUK PANGGILAN")
            .setItems(simOptions, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    performCallWithAccount(num, accounts.get(which));
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void performCallWithAccount(String num, PhoneAccountHandle handle) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num)));
            if (handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                callIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
            }
            startActivity(callIntent);
        } catch (Throwable t) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num)));
            startActivity(dialIntent);
        }
    }

    // FASA 2: DAIL PANTAS (SPEED DIAL 1-9)
    private void handleSpeedDial(final int digit) {
        hapticFeedback();
        if (digit == 1) {
            new AlertDialog.Builder(this)
                .setTitle("\uD83D\uDEA8 DAIL PANTAS KECEMASAN (999)")
                .setMessage("Adakah anda mahu mendail talian kecemasan Polis / Ambulans 999 sekarang?")
                .setPositiveButton("HUBUNGI 999", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mEtNumber.setText("999");
                        initiateCall();
                    }
                })
                .setNegativeButton("BATAL", null)
                .show();
            return;
        }

        final SharedPreferences sp = getSharedPreferences("kantoi_speed_dial", Context.MODE_PRIVATE);
        final String savedNum = sp.getString("sd_num_" + digit, null);
        final String savedName = sp.getString("sd_name_" + digit, null);

        if (savedNum != null && !savedNum.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("\u26A1 DAIL PANTAS [KEKUNCI " + digit + "]")
                .setMessage("Hubungi " + (savedName != null ? savedName : "Kenalan") + "\nNombor: " + savedNum + "?")
                .setPositiveButton("PANGGIL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mEtNumber.setText(savedNum);
                        initiateCall();
                    }
                })
                .setNeutralButton("TETAP SEMULA", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showSetSpeedDialDialog(digit);
                    }
                })
                .setNegativeButton("BATAL", null)
                .show();
        } else {
            showSetSpeedDialDialog(digit);
        }
    }

    private void showSetSpeedDialDialog(final int digit) {
        final SharedPreferences sp = getSharedPreferences("kantoi_speed_dial", Context.MODE_PRIVATE);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14));

        final EditText etName = new EditText(this);
        etName.setHint("Nama Kenalan (cth: Ibu / Pejabat)");
        etName.setTextColor(Color.WHITE);
        form.addView(etName);

        final EditText etNum = new EditText(this);
        etNum.setHint("Nombor Telefon (cth: 0123456789)");
        etNum.setTextColor(Color.WHITE);
        String curNum = mEtNumber.getText().toString().trim();
        if (!curNum.isEmpty()) etNum.setText(curNum);
        form.addView(etNum);

        new AlertDialog.Builder(this)
            .setTitle("\u26A1 TETAPKAN DAIL PANTAS [KEKUNCI " + digit + "]")
            .setView(form)
            .setPositiveButton("SIMPAN", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String num = etNum.getText().toString().trim();
                    String name = etName.getText().toString().trim();
                    if (!num.isEmpty()) {
                        sp.edit().putString("sd_num_" + digit, num).putString("sd_name_" + digit, name).apply();
                        Toast.makeText(DialerActivity.this, "Dail Pantas kekunci " + digit + " disimpan.", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    // FASA 3: ENJIN CARIAN PINTAR T9
    private void loadContactsForT9() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<KantoiT9SearchEngine.T9Contact> list = new ArrayList<>();
                    Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        },
                        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                    );
                    if (cursor != null) {
                        try {
                            int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                            int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                            while (cursor.moveToNext()) {
                                String name = (nameIdx >= 0) ? cursor.getString(nameIdx) : "";
                                String num = (numIdx >= 0) ? cursor.getString(numIdx) : "";
                                if (num != null && !num.trim().isEmpty()) {
                                    list.add(new KantoiT9SearchEngine.T9Contact(name, num));
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mCachedContacts) {
                                mCachedContacts.clear();
                                mCachedContacts.addAll(list);
                            }
                        }
                    });
                } catch (Throwable t) {
                    Log.d("DialerActivity", "loadContactsForT9 error: " + t.getMessage());
                }
            }
        }).start();
    }

    private void updateT9Suggestions(String query) {
        if (mT9Container == null) return;
        mT9Container.removeAllViews();
        if (query == null || query.trim().isEmpty()) {
            mT9Container.setVisibility(View.GONE);
            return;
        }

        List<KantoiT9SearchEngine.T9Contact> matches;
        synchronized (mCachedContacts) {
            matches = KantoiT9SearchEngine.filter(mCachedContacts, query, 3);
        }

        if (matches == null || matches.isEmpty()) {
            mT9Container.setVisibility(View.GONE);
            return;
        }

        mT9Container.setVisibility(View.VISIBLE);
        for (final KantoiT9SearchEngine.T9Contact c : matches) {
            LinearLayout row = createT9SuggestionRow(c);
            mT9Container.addView(row);
        }
    }

    private LinearLayout createT9SuggestionRow(final KantoiT9SearchEngine.T9Contact c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        setMarginTop(row, 3);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(0xFF1E1E30);
        rowBg.setCornerRadius(dpToPx(8));
        row.setBackground(rowBg);

        TextView tvAvatar = new TextView(this);
        String initial = (c.name != null && !c.name.isEmpty()) ? String.valueOf(c.name.charAt(0)).toUpperCase() : "\uD83D\uDC64";
        tvAvatar.setText(initial);
        tvAvatar.setTextColor(Color.WHITE);
        tvAvatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvAvatar.setTypeface(Typeface.DEFAULT_BOLD);
        tvAvatar.setGravity(Gravity.CENTER);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFF0284C7);
        tvAvatar.setBackground(avBg);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28));
        tvAvatar.setLayoutParams(avLp);
        row.addView(tvAvatar);

        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        infoLayout.setPadding(dpToPx(10), 0, 0, 0);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        infoLayout.setLayoutParams(infoLp);

        TextView tvName = new TextView(this);
        tvName.setText(c.name);
        tvName.setTextColor(0xFF38BDF8);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        infoLayout.addView(tvName);

        TextView tvNum = new TextView(this);
        tvNum.setText(c.number);
        tvNum.setTextColor(0xFF94A3B8);
        tvNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        infoLayout.addView(tvNum);

        row.addView(infoLayout);

        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticFeedback();
                mEtNumber.setText(c.cleanNumber);
                mEtNumber.setSelection(mEtNumber.getText().length());
            }
        });

        return row;
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
        normalBg.setColor(0xFF141422);
        normalBg.setStroke(dpToPx(1), 0xFF2A2A3A);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList rippleColor = ColorStateList.valueOf(0x4400D4FF); // Soft cyan ripple
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
            tvSub.setTextColor(0xFF00D4FF);
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
        } else {
            try {
                final int digitVal = Integer.parseInt(key.digit);
                btnLayout.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        handleSpeedDial(digitVal);
                        return true;
                    }
                });
            } catch (NumberFormatException ignored) {}
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
        if (bgColor != 0xFF00D4FF) {
            normalBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        }

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
