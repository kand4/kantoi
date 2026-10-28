package com.kantoi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Peti Masuk & Pengarang Mesej SMS Pintar KANTOI (SMS Inbox & Composer)
 * Menyaring SMS pancingan data (Phishing), trojan APK, dan scammer bank,
 * serta membolehkan pengguna menaip dan menghantar SMS selamat.
 */
public class SmsActivity extends Activity {
    public static final String EXTRA_RECIPIENT = "extra_recipient";
    private static final int REQUEST_SMS_PERMS = 4001;
    private static final String PREF_DELETED_SMS = "kantoi_deleted_sms_prefs";
    private static final String KEY_DELETED_IDS = "deleted_sms_ids";

    private KantoiThreatEngine mEngine;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private boolean mShowingComposeTab = false;
    private LinearLayout mContentContainer;
    private Button mBtnTabInbox;
    private Button mBtnTabCompose;

    // Compose tab fields
    private EditText mEtRecipient;
    private EditText mEtMessage;
    private TextView mTvCharCount;
    private TextView mTvRecipientStatus;

    // SMS Selection & Delete State
    private boolean mSmsSelectionMode = false;
    private final Set<Long> mSelectedSmsIds = new HashSet<Long>();
    private List<SmsItem> mCurrentLoadedSmsList = new ArrayList<SmsItem>();
    private LinearLayout mSmsNormalHeaderRow;
    private LinearLayout mSmsSelectionHeaderRow;
    private TextView mTvSmsSelectedCount;
    private Button mBtnSmsDeleteSelected;
    private Button mBtnSmsSelectAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEngine = KantoiThreatEngine.getInstance(this);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF0A0A0F);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0A0A0F); // Slate 900
        scrollView.setSmoothScrollingEnabled(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setClipToPadding(false);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(75)); // 75dp bottom padding for dock!

        // 1. Top Bar with Back Button
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

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
        tvTitle.setText("  💬 PERISAI & MESEJ SMS");
        tvTitle.setTextColor(0xFFF8FAFC);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(tvTitle);

        root.addView(topBar);

        // 2. Tab Switcher (Inbox vs Compose)
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        setMarginTop(tabRow, 14);

        mBtnTabInbox = new Button(this);
        mBtnTabInbox.setText("📥 PETI MASUK SMS");
        mBtnTabInbox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mBtnTabInbox.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp1 = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        tLp1.rightMargin = dpToPx(4);
        mBtnTabInbox.setLayoutParams(tLp1);
        mBtnTabInbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(false);
            }
        });
        tabRow.addView(mBtnTabInbox);

        mBtnTabCompose = new Button(this);
        mBtnTabCompose.setText("✍️ TULIS SMS");
        mBtnTabCompose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mBtnTabCompose.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp2 = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        tLp2.leftMargin = dpToPx(4);
        mBtnTabCompose.setLayoutParams(tLp2);
        mBtnTabCompose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(true);
            }
        });
        tabRow.addView(mBtnTabCompose);

        root.addView(tabRow);

        // 3. Dynamic Content Container
        mContentContainer = new LinearLayout(this);
        mContentContainer.setOrientation(LinearLayout.VERTICAL);
        setMarginTop(mContentContainer, 14);
        root.addView(mContentContainer);

        scrollView.addView(root);
        mainLayout.addView(scrollView);

        // Persistent Floating Bottom Bar
        View bottomBar = KantoiBottomBar.buildBottomNavBar(this, KantoiBottomBar.TAB_SMS);
        mainLayout.addView(bottomBar);

        setContentView(mainLayout);

        // Check if pre-filled recipient was passed
        Intent it = getIntent();
        String initialRecipient = null;
        if (it != null) {
            initialRecipient = it.getStringExtra(EXTRA_RECIPIENT);
            if (initialRecipient == null && it.getData() != null && "smsto".equalsIgnoreCase(it.getData().getScheme())) {
                initialRecipient = it.getData().getSchemeSpecificPart();
            }
        }

        if (initialRecipient != null && !initialRecipient.isEmpty()) {
            switchTab(true);
            if (mEtRecipient != null) {
                mEtRecipient.setText(initialRecipient);
            }
        } else {
            switchTab(false);
        }
    }

    private void switchTab(boolean showCompose) {
        mShowingComposeTab = showCompose;
        updateTabButtonsUI();
        mContentContainer.removeAllViews();

        if (showCompose) {
            buildComposeView();
        } else {
            buildInboxView();
        }
    }

    private void updateTabButtonsUI() {
        if (!mShowingComposeTab) {
            // Inbox active
            updateButtonBg(mBtnTabInbox, 0xFF0284C7, Color.WHITE);
            updateButtonBg(mBtnTabCompose, 0xFF141422, 0xFF94A3B8);
        } else {
            // Compose active
            updateButtonBg(mBtnTabInbox, 0xFF141422, 0xFF94A3B8);
            updateButtonBg(mBtnTabCompose, 0xFF0284C7, Color.WHITE);
        }
    }

    // ==================== INBOX VIEW ====================
    private void buildInboxView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                LinearLayout permCard = createCard(0xFF141422, 0xFFF59E0B, 12);
                permCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

                TextView tv = new TextView(this);
                tv.setText("⚠️ KEBENARAN MEMBACA SMS DIPERLUKAN\n\nUntuk menyaring SMS pancingan data (Phishing) dan membaca peti masuk anda, sila benarkan akses SMS:");
                tv.setTextColor(0xFFFEF3C7);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                permCard.addView(tv);

                Button btnPerm = createButton("🔑 BENARKAN AKSES SMS", 0xFFD97706, Color.WHITE);
                setMarginTop(btnPerm, 10);
                btnPerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS}, REQUEST_SMS_PERMS);
                    }
                });
                permCard.addView(btnPerm);
                mContentContainer.addView(permCard);
                return;
            }
        }

        if (mCurrentLoadedSmsList.isEmpty()) {
            final TextView tvLoading = new TextView(this);
            tvLoading.setText("⏳ Sedang memuatkan dan menyaring mesej SMS...");
            tvLoading.setTextColor(0xFF38BDF8);
            tvLoading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            mContentContainer.addView(tvLoading);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<SmsItem> smsList = loadSmsInbox();
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        boolean isSame = false;
                        if (!mCurrentLoadedSmsList.isEmpty() && mCurrentLoadedSmsList.size() == smsList.size()) {
                            if (mCurrentLoadedSmsList.get(0).id == smsList.get(0).id) {
                                isSame = true;
                            }
                        }
                        mCurrentLoadedSmsList = smsList;
                        if (!isSame || mContentContainer.getChildCount() <= 1) {
                            renderLoadedSmsInbox();
                        }
                    }
                });
            }
        }).start();
    }

    private static final int INITIAL_SMS_BATCH = 15;
    private static final int CHUNK_SMS_BATCH = 15;
    private int mRenderedSmsCount = 0;

    private void renderLoadedSmsInbox() {
        if (mShowingComposeTab) return;
        mContentContainer.removeAllViews();
        mRenderedSmsCount = 0;

        if (mCurrentLoadedSmsList.isEmpty()) {
            TextView emptyTv = new TextView(SmsActivity.this);
            emptyTv.setText("📭 Peti masuk SMS kosong atau tiada mesej baharu.");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            emptyTv.setGravity(Gravity.CENTER);
            setMarginTop(emptyTv, 40);
            mContentContainer.addView(emptyTv);
            return;
        }

        // Summary & Actions Header (Normal Mode)
        int scamCount = 0;
        for (SmsItem s : mCurrentLoadedSmsList) {
            if (s.isPhishingOrScam) scamCount++;
        }

        mSmsNormalHeaderRow = new LinearLayout(this);
        mSmsNormalHeaderRow.setOrientation(LinearLayout.VERTICAL);
        mSmsNormalHeaderRow.setVisibility(mSmsSelectionMode ? View.GONE : View.VISIBLE);

        LinearLayout summaryCard = createCard(0xFF141422, scamCount > 0 ? 0xFFFF3366 : 0xFF00FF88, 10);
        summaryCard.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        LinearLayout cardHeader = new LinearLayout(this);
        cardHeader.setOrientation(LinearLayout.HORIZONTAL);
        cardHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvSum = new TextView(this);
        if (scamCount > 0) {
            tvSum.setText("🚨 " + scamCount + " Berisiko / Phishing (" + mCurrentLoadedSmsList.size() + " mesej)");
            tvSum.setTextColor(0xFFF87171);
        } else {
            tvSum.setText("✅ " + mCurrentLoadedSmsList.size() + " Mesej SMS Selamat Disaring");
            tvSum.setTextColor(0xFF34D399);
        }
        tvSum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSum.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvSum.setLayoutParams(sumLp);
        cardHeader.addView(tvSum);

        Button btnSelectSms = new Button(this);
        btnSelectSms.setText("🗑️ PILIH");
        btnSelectSms.setTextColor(0xFFE2E8F0);
        btnSelectSms.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnSelectSms.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(0xFF334155);
        sBg.setCornerRadius(dpToPx(6));
        btnSelectSms.setBackground(sBg);
        btnSelectSms.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnSelectSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterSmsSelectionMode(null);
            }
        });
        cardHeader.addView(btnSelectSms);

        Button btnRefreshSms = new Button(this);
        btnRefreshSms.setText("🔄");
        btnRefreshSms.setTextColor(0xFF38BDF8);
        btnRefreshSms.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnRefreshSms.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable rfBg = new GradientDrawable();
        rfBg.setColor(0xFF141422); rfBg.setStroke(dpToPx(1), 0xFF00D4FF);
        rfBg.setCornerRadius(dpToPx(6));
        btnRefreshSms.setBackground(rfBg);
        btnRefreshSms.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rfLp.leftMargin = dpToPx(6);
        btnRefreshSms.setLayoutParams(rfLp);
        btnRefreshSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildInboxView();
            }
        });
        cardHeader.addView(btnRefreshSms);

        summaryCard.addView(cardHeader);
        mSmsNormalHeaderRow.addView(summaryCard);
        mContentContainer.addView(mSmsNormalHeaderRow);

        // Selection Toolbar Header Row (Selection Mode)
        mSmsSelectionHeaderRow = new LinearLayout(this);
        mSmsSelectionHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        mSmsSelectionHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        mSmsSelectionHeaderRow.setVisibility(mSmsSelectionMode ? View.VISIBLE : View.GONE);
        mSmsSelectionHeaderRow.setBackgroundColor(0xFF141422);
        mSmsSelectionHeaderRow.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(0xFF141422);
        barBg.setCornerRadius(dpToPx(8));
        barBg.setStroke(dpToPx(1), 0xFF38BDF8);
        mSmsSelectionHeaderRow.setBackground(barBg);

        mBtnSmsSelectAll = new Button(this);
        mBtnSmsSelectAll.setText("✓ SEMUA");
        mBtnSmsSelectAll.setTextColor(0xFF34D399);
        mBtnSmsSelectAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnSmsSelectAll.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable saBg = new GradientDrawable();
        saBg.setColor(0xFF141422);
        saBg.setCornerRadius(dpToPx(6));
        mBtnSmsSelectAll.setBackground(saBg);
        mBtnSmsSelectAll.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        mBtnSmsSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSmsSelectAll();
            }
        });
        mSmsSelectionHeaderRow.addView(mBtnSmsSelectAll);

        mTvSmsSelectedCount = new TextView(this);
        mTvSmsSelectedCount.setText("0 Dipilih");
        mTvSmsSelectedCount.setTextColor(0xFFF8FAFC);
        mTvSmsSelectedCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvSmsSelectedCount.setTypeface(Typeface.DEFAULT_BOLD);
        mTvSmsSelectedCount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mTvSmsSelectedCount.setLayoutParams(scLp);
        mSmsSelectionHeaderRow.addView(mTvSmsSelectedCount);

        mBtnSmsDeleteSelected = new Button(this);
        mBtnSmsDeleteSelected.setText("🗑️ PADAM");
        mBtnSmsDeleteSelected.setTextColor(Color.WHITE);
        mBtnSmsDeleteSelected.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnSmsDeleteSelected.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(0xFFEF4444);
        delBg.setCornerRadius(dpToPx(6));
        mBtnSmsDeleteSelected.setBackground(delBg);
        mBtnSmsDeleteSelected.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        mBtnSmsDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteSelectedSms();
            }
        });
        mSmsSelectionHeaderRow.addView(mBtnSmsDeleteSelected);

        Button btnCancelSms = new Button(this);
        btnCancelSms.setText("✕ BATAL");
        btnCancelSms.setTextColor(0xFF94A3B8);
        btnCancelSms.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCancelSms.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable canBg = new GradientDrawable();
        canBg.setColor(0xFF141422);
        canBg.setCornerRadius(dpToPx(6));
        btnCancelSms.setBackground(canBg);
        btnCancelSms.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        canLp.leftMargin = dpToPx(6);
        btnCancelSms.setLayoutParams(canLp);
        btnCancelSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSmsSelectionMode();
            }
        });
        mSmsSelectionHeaderRow.addView(btnCancelSms);

        mContentContainer.addView(mSmsSelectionHeaderRow);

        // Progressive Initial Batch Render
        final int total = mCurrentLoadedSmsList.size();
        final int initialLimit = Math.min(INITIAL_SMS_BATCH, total);
        for (int i = 0; i < initialLimit; i++) {
            SmsItem item = mCurrentLoadedSmsList.get(i);
            View itemView = createSmsItemView(item);
            setMarginTop(itemView, 10);
            mContentContainer.addView(itemView);
        }
        mRenderedSmsCount = initialLimit;

        if (mRenderedSmsCount < total) {
            postNextSmsBatch(total);
        }

        updateSmsSelectionUI();
    }

    private void postNextSmsBatch(final int total) {
        mContentContainer.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || mShowingComposeTab) return;
                int start = mRenderedSmsCount;
                if (start >= total || start >= mCurrentLoadedSmsList.size()) return;
                int end = Math.min(start + CHUNK_SMS_BATCH, Math.min(total, mCurrentLoadedSmsList.size()));
                for (int i = start; i < end; i++) {
                    SmsItem item = mCurrentLoadedSmsList.get(i);
                    View itemView = createSmsItemView(item);
                    setMarginTop(itemView, 10);
                    mContentContainer.addView(itemView);
                }
                mRenderedSmsCount = end;
                if (mRenderedSmsCount < total && mRenderedSmsCount < mCurrentLoadedSmsList.size()) {
                    postNextSmsBatch(total);
                }
            }
        });
    }

    private Set<String> getDeletedSmsIdStrings() {
        android.content.SharedPreferences sp = getSharedPreferences(PREF_DELETED_SMS, Context.MODE_PRIVATE);
        return new HashSet<String>(sp.getStringSet(KEY_DELETED_IDS, new HashSet<String>()));
    }

    private void markSmsAsDeletedLocally(long id) {
        android.content.SharedPreferences sp = getSharedPreferences(PREF_DELETED_SMS, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<String>(sp.getStringSet(KEY_DELETED_IDS, new HashSet<String>()));
        set.add(String.valueOf(id));
        sp.edit().putStringSet(KEY_DELETED_IDS, set).apply();
    }

    private void markMultipleSmsAsDeletedLocally(Set<Long> ids) {
        android.content.SharedPreferences sp = getSharedPreferences(PREF_DELETED_SMS, Context.MODE_PRIVATE);
        Set<String> set = new HashSet<String>(sp.getStringSet(KEY_DELETED_IDS, new HashSet<String>()));
        for (Long id : ids) {
            set.add(String.valueOf(id));
        }
        sp.edit().putStringSet(KEY_DELETED_IDS, set).apply();
    }

    private List<SmsItem> loadSmsInbox() {
        List<SmsItem> list = new ArrayList<>();
        Set<String> deletedIds = getDeletedSmsIdStrings();
        try {
            Uri uri = Uri.parse("content://sms/inbox");
            Cursor cursor = getContentResolver().query(
                uri,
                new String[]{"_id", "address", "body", "date"},
                null,
                null,
                "date DESC LIMIT 60"
            );

            if (cursor != null) {
                int colId = cursor.getColumnIndex("_id");
                int colAddress = cursor.getColumnIndex("address");
                int colBody = cursor.getColumnIndex("body");
                int colDate = cursor.getColumnIndex("date");

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

                while (cursor.moveToNext()) {
                    long id = (colId != -1) ? cursor.getLong(colId) : -1;
                    if (id != -1 && deletedIds.contains(String.valueOf(id))) {
                        continue; // Skip messages deleted locally
                    }

                    String address = (colAddress != -1) ? cursor.getString(colAddress) : "";
                    String body = (colBody != -1) ? cursor.getString(colBody) : "";
                    long dateMillis = (colDate != -1) ? cursor.getLong(colDate) : 0;

                    SmsItem item = new SmsItem();
                    item.id = id;
                    item.address = address != null ? address : "Tidak Dikenali";
                    item.body = body != null ? body : "";
                    item.formattedDate = (dateMillis > 0) ? sdf.format(new Date(dateMillis)) : "";

                    // Threat Check & Alphanumeric Pruning
                    boolean isAlphaSender = false;
                    String cleanPhone = "";
                    if (item.address != null && !item.address.isEmpty()) {
                        String digitsOnly = item.address.replaceAll("[^0-9]", "");
                        if (item.address.matches(".*[a-zA-Z].*") || digitsOnly.length() < 7) {
                            isAlphaSender = true;
                        } else {
                            cleanPhone = KantoiThreatEngine.normalizeToLocal(item.address);
                        }
                    }

                    if (!isAlphaSender && !cleanPhone.isEmpty()) {
                        item.isContact = mEngine.isSafeContactFast(cleanPhone);
                        item.contactName = item.isContact ? mEngine.getContactDisplayNameFast(cleanPhone) : null;

                        KantoiThreatEngine.ScammerThreat threat = mEngine.checkThreat(cleanPhone);
                        if (threat != null) {
                            item.isPhishingOrScam = true;
                            item.threatDetails = threat.suspectName + " (" + threat.riskTag + ")";
                        }
                    } else {
                        item.isContact = false;
                        item.contactName = null;
                    }

                    boolean bodyPhishing = isBodyPhishing(item.body);
                    if (bodyPhishing) {
                        item.isPhishingOrScam = true;
                        if (item.threatDetails == null) {
                            item.threatDetails = "Pautan / Teks Pancingan Data (Phishing)";
                        }
                    }

                    list.add(item);
                }
                cursor.close();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return list;
    }

    private boolean isBodyPhishing(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        String[] keywords = {
            ".apk", "mediafire.com", ".xyz", ".top", "bit.ly/", "tinyurl.com/",
            "lhdn-my.com", "lhdn-cukai", "cimbclicks-verify", "maybank2u-update",
            "bantuan-kwsp", "mykasih-bantuan", "t.me/joinchat", "tekan link ini untuk claim",
            "akaun anda disekat", "masukkan kata laluan"
        };
        for (String k : keywords) {
            if (lower.contains(k)) return true;
        }
        return false;
    }

    private View createSmsItemView(final SmsItem item) {
        final boolean isSelected = mSmsSelectionMode && mSelectedSmsIds.contains(item.id);

        int bgColor = item.isPhishingOrScam ? 0xFF450A0A : 0xFF141422;
        int strokeColor = item.isPhishingOrScam ? 0xFFDC2626 : 0xFF334155;

        if (mSmsSelectionMode && isSelected) {
            bgColor = 0xFF1E3A8A; // Selected dark blue
            strokeColor = 0xFF38BDF8; // Selected bright sky blue
        }

        LinearLayout card = createCard(bgColor, strokeColor, 10);
        card.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        // Top Row: (Checkbox if selection mode) + Sender + Date
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        if (mSmsSelectionMode) {
            TextView tvCheckbox = new TextView(this);
            tvCheckbox.setText(isSelected ? "☑️" : "⬜");
            tvCheckbox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tvCheckbox.setPadding(0, 0, dpToPx(8), 0);
            topRow.addView(tvCheckbox);
        }

        TextView tvSender = new TextView(this);
        String senderTitle = item.contactName != null ? item.contactName : item.address;
        tvSender.setText(senderTitle);
        tvSender.setTextColor(item.isPhishingOrScam ? 0xFFFCA5A5 : Color.WHITE);
        tvSender.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSender.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvSender.setLayoutParams(sLp);
        topRow.addView(tvSender);

        TextView tvDate = new TextView(this);
        tvDate.setText(item.formattedDate);
        tvDate.setTextColor(0xFF94A3B8);
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        topRow.addView(tvDate);

        card.addView(topRow);

        if (item.isPhishingOrScam) {
            TextView tvThreat = new TextView(this);
            tvThreat.setText("🚨 " + item.threatDetails);
            tvThreat.setTextColor(0xFFEF4444);
            tvThreat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvThreat.setTypeface(Typeface.DEFAULT_BOLD);
            setMarginTop(tvThreat, 4);
            card.addView(tvThreat);
        }

        // Snippet with max length & ellipsize optimization
        TextView tvSnippet = new TextView(this);
        String snippetText = item.body;
        if (snippetText != null && snippetText.length() > 200) {
            snippetText = snippetText.substring(0, 200) + "...";
        }
        tvSnippet.setText(snippetText);
        tvSnippet.setTextColor(0xFFCBD5E1);
        tvSnippet.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSnippet.setMaxLines(3);
        tvSnippet.setEllipsize(TextUtils.TruncateAt.END);
        setMarginTop(tvSnippet, 6);
        card.addView(tvSnippet);

        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSmsSelectionMode) {
                    toggleSmsItemSelection(item.id);
                } else {
                    showSmsDetailDialog(item);
                }
            }
        });

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mSmsSelectionMode) {
                    enterSmsSelectionMode(item.id);
                    return true;
                }
                return false;
            }
        });

        return card;
    }

    private void enterSmsSelectionMode(Long preselectedId) {
        mSmsSelectionMode = true;
        mSelectedSmsIds.clear();
        if (preselectedId != null && preselectedId > 0) {
            mSelectedSmsIds.add(preselectedId);
        }
        renderLoadedSmsInbox();
    }

    private void exitSmsSelectionMode() {
        mSmsSelectionMode = false;
        mSelectedSmsIds.clear();
        renderLoadedSmsInbox();
    }

    private void toggleSmsItemSelection(long id) {
        if (mSelectedSmsIds.contains(id)) {
            mSelectedSmsIds.remove(id);
        } else {
            mSelectedSmsIds.add(id);
        }
        updateSmsSelectionUI();
        renderLoadedSmsInbox();
    }

    private void toggleSmsSelectAll() {
        if (!mCurrentLoadedSmsList.isEmpty() && mSelectedSmsIds.size() == mCurrentLoadedSmsList.size()) {
            mSelectedSmsIds.clear();
        } else {
            for (SmsItem item : mCurrentLoadedSmsList) {
                if (item.id > 0) {
                    mSelectedSmsIds.add(item.id);
                }
            }
        }
        updateSmsSelectionUI();
        renderLoadedSmsInbox();
    }

    private void updateSmsSelectionUI() {
        int count = mSelectedSmsIds.size();
        if (mTvSmsSelectedCount != null) {
            mTvSmsSelectedCount.setText(count + " Dipilih");
        }
        if (mBtnSmsDeleteSelected != null) {
            mBtnSmsDeleteSelected.setEnabled(count > 0);
            mBtnSmsDeleteSelected.setAlpha(count > 0 ? 1.0f : 0.4f);
        }
        if (mBtnSmsSelectAll != null) {
            boolean allSelected = !mCurrentLoadedSmsList.isEmpty() && (mSelectedSmsIds.size() == mCurrentLoadedSmsList.size());
            mBtnSmsSelectAll.setText(allSelected ? "☐ NYAH" : "✓ SEMUA");
        }
    }

    private void deleteSingleSms(final SmsItem item) {
        new AlertDialog.Builder(this)
            .setTitle("🗑️ Padam Mesej SMS?")
            .setMessage("Adakah anda pasti mahu memadam mesej ini daripada " + 
                (item.contactName != null ? item.contactName : item.address) + "?")
            .setPositiveButton("PADAM", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    markSmsAsDeletedLocally(item.id);
                    try {
                        getContentResolver().delete(
                            Uri.parse("content://sms/" + item.id),
                            null,
                            null
                        );
                    } catch (Throwable ignored) {}
                    Toast.makeText(SmsActivity.this, "Mesej SMS telah dipadam.", Toast.LENGTH_SHORT).show();
                    buildInboxView();
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void confirmDeleteSelectedSms() {
        final int count = mSelectedSmsIds.size();
        if (count == 0) return;

        new AlertDialog.Builder(this)
            .setTitle("🗑️ Padam " + count + " Mesej SMS?")
            .setMessage("Adakah anda pasti mahu memadam " + count + " mesej SMS yang dipilih?")
            .setPositiveButton("PADAM (" + count + ")", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final Set<Long> toDelete = new HashSet<Long>(mSelectedSmsIds);
                    markMultipleSmsAsDeletedLocally(toDelete);
                    exitSmsSelectionMode();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            for (Long id : toDelete) {
                                try {
                                    getContentResolver().delete(
                                        Uri.parse("content://sms/" + id),
                                        null,
                                        null
                                    );
                                } catch (Throwable ignored) {}
                            }
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SmsActivity.this, "Berjaya memadam " + count + " mesej SMS.", Toast.LENGTH_SHORT).show();
                                    buildInboxView();
                                }
                            });
                        }
                    }).start();
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void showSmsDetailDialog(final SmsItem item) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle((item.isPhishingOrScam ? "🚨 [AMARAN] " : "💬 ") + (item.contactName != null ? item.contactName : item.address));

        StringBuilder sb = new StringBuilder();
        if (item.isPhishingOrScam) {
            sb.append("⚠️ STATUS KESELAMATAN: ANCAMAN DIKESAN\n• ").append(item.threatDetails).append("\n\n");
        } else {
            sb.append("✅ STATUS KESELAMATAN: TIADA REKOD ANCAMAN\n\n");
        }
        sb.append("Tarikh: ").append(item.formattedDate).append("\n\n");
        sb.append("Kandungan SMS:\n").append(item.body);

        b.setMessage(sb.toString());

        CharSequence[] options = new CharSequence[]{
            "💬 Balas Mesej SMS Ini",
            "📞 Panggil (" + item.address + ")",
            "🗑️ Padam Mesej SMS Ini",
            "☑️ Masuk Mod Pilih & Padam Banyak"
        };

        b.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: {
                        switchTab(true);
                        if (mEtRecipient != null) {
                            mEtRecipient.setText(item.address);
                        }
                        break;
                    }
                    case 1: {
                        Intent dIt = new Intent(SmsActivity.this, DialerActivity.class);
                        dIt.putExtra(DialerActivity.EXTRA_INITIAL_NUMBER, item.address);
                        startActivity(dIt);
                        break;
                    }
                    case 2: {
                        deleteSingleSms(item);
                        break;
                    }
                    case 3: {
                        enterSmsSelectionMode(item.id);
                        break;
                    }
                }
            }
        });

        b.setNegativeButton("TUTUP", null);
        b.show();
    }

    // ==================== COMPOSE VIEW ====================
    private void buildComposeView() {
        LinearLayout composeCard = createCard(0xFF141422, 0xFF2A2A3A, 12);
        composeCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("✍️ TULIS MESEJ SMS BAHARU");
        tvTitle.setTextColor(0xFF38BDF8);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        composeCard.addView(tvTitle);

        // Recipient Input
        TextView tvRecLabel = new TextView(this);
        tvRecLabel.setText("Nombor Penerima:");
        tvRecLabel.setTextColor(0xFF94A3B8);
        tvRecLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setMarginTop(tvRecLabel, 10);
        composeCard.addView(tvRecLabel);

        mEtRecipient = new EditText(this);
        mEtRecipient.setHint("Cth: 0112345678");
        mEtRecipient.setHintTextColor(0xFF64748B);
        mEtRecipient.setTextColor(Color.WHITE);
        mEtRecipient.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mEtRecipient.setInputType(InputType.TYPE_CLASS_PHONE);
        mEtRecipient.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(0xFF0A0A0F); rBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        rBg.setCornerRadius(dpToPx(8));
        rBg.setStroke(dpToPx(1), 0xFF334155);
        mEtRecipient.setBackground(rBg);
        setMarginTop(mEtRecipient, 4);
        composeCard.addView(mEtRecipient);

        mTvRecipientStatus = new TextView(this);
        mTvRecipientStatus.setTextColor(0xFF34D399);
        mTvRecipientStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(mTvRecipientStatus, 4);
        composeCard.addView(mTvRecipientStatus);

        mEtRecipient.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkRecipientThreat(s.toString().trim());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Message Body Input
        TextView tvMsgLabel = new TextView(this);
        tvMsgLabel.setText("Mesej Teks:");
        tvMsgLabel.setTextColor(0xFF94A3B8);
        tvMsgLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setMarginTop(tvMsgLabel, 12);
        composeCard.addView(tvMsgLabel);

        mEtMessage = new EditText(this);
        mEtMessage.setHint("Tulis mesej anda di sini...");
        mEtMessage.setHintTextColor(0xFF64748B);
        mEtMessage.setTextColor(Color.WHITE);
        mEtMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mEtMessage.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        mEtMessage.setLines(4);
        mEtMessage.setGravity(Gravity.TOP);
        mEtMessage.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        GradientDrawable mBg = new GradientDrawable();
        mBg.setColor(0xFF0A0A0F); mBg.setStroke(dpToPx(1), 0xFF2A2A3A);
        mBg.setCornerRadius(dpToPx(8));
        mBg.setStroke(dpToPx(1), 0xFF334155);
        mEtMessage.setBackground(mBg);
        setMarginTop(mEtMessage, 4);
        composeCard.addView(mEtMessage);

        mTvCharCount = new TextView(this);
        mTvCharCount.setText("0 / 160 aksara (1 SMS)");
        mTvCharCount.setTextColor(0xFF64748B);
        mTvCharCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvCharCount.setGravity(Gravity.RIGHT);
        setMarginTop(mTvCharCount, 4);
        composeCard.addView(mTvCharCount);

        mEtMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s.length();
                int smsCount = (len / 160) + 1;
                mTvCharCount.setText(len + " / " + (smsCount * 160) + " aksara (" + smsCount + " SMS)");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Send Button
        Button btnSend = createButton("📤 HANTAR MESEJ SMS SEKARANG", 0xFF059669, Color.WHITE);
        setMarginTop(btnSend, 16);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSmsMessage();
            }
        });
        composeCard.addView(btnSend);

        mContentContainer.addView(composeCard);
    }

    private void checkRecipientThreat(String num) {
        if (num.length() < 3) {
            mTvRecipientStatus.setText("");
            return;
        }
        String clean = KantoiThreatEngine.normalizeToLocal(num);
        if (mEngine.isSafeContact(clean)) {
            String name = mEngine.getContactDisplayName(clean);
            mTvRecipientStatus.setText("✓ Penerima adalah Kenalan Selamat: " + (name != null ? name : clean));
            mTvRecipientStatus.setTextColor(0xFF34D399);
            return;
        }
        KantoiThreatEngine.ScammerThreat threat = mEngine.checkThreat(clean);
        if (threat != null) {
            mTvRecipientStatus.setText("🚨 AWAS! Penerima disahkan scammer: " + threat.suspectName + " (" + threat.riskTag + ")");
            mTvRecipientStatus.setTextColor(0xFFEF4444);
        } else {
            mTvRecipientStatus.setText("ℹ️ Nombor Asing (Tiada rekod scammer)");
            mTvRecipientStatus.setTextColor(0xFF38BDF8);
        }
    }

    private void sendSmsMessage() {
        final String recipient = mEtRecipient.getText().toString().trim();
        final String message = mEtMessage.getText().toString().trim();

        if (recipient.isEmpty()) {
            Toast.makeText(this, "Sila masukkan nombor penerima.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (message.isEmpty()) {
            Toast.makeText(this, "Sila masukkan kandungan mesej.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQUEST_SMS_PERMS);
                return;
            }
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null);

            Toast.makeText(this, "✅ Mesej SMS berjaya dihantar ke " + recipient, Toast.LENGTH_LONG).show();
            mEtMessage.setText("");
            // Return to inbox tab
            switchTab(false);
        } catch (Throwable t) {
            Toast.makeText(this, "Ralat menghantar SMS: " + t.getMessage(), Toast.LENGTH_LONG).show();
            // Fallback via Intent
            try {
                Intent sendIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(recipient)));
                sendIntent.putExtra("sms_body", message);
                startActivity(sendIntent);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMS) {
            if (mShowingComposeTab) {
                sendSmsMessage();
            } else {
                buildInboxView();
            }
        }
    }

    private static class SmsItem {
        long id;
        String address;
        String body;
        String formattedDate;
        boolean isContact;
        String contactName;
        boolean isPhishingOrScam;
        String threatDetails;
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

    private Button createButton(String text, int bgColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        updateButtonBg(btn, bgColor, textColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44));
        btn.setLayoutParams(lp);
        return btn;
    }

    private void updateButtonBg(Button btn, int bgColor, int textColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dpToPx(8));
        btn.setBackground(bg);
        btn.setTextColor(textColor);
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
