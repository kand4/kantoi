package com.kantoi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.provider.CallLog;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ID_ROLE_SCREENING = 1001;
    private static final int REQUEST_ID_ROLE_DIALER = 1002;
    private static final int REQUEST_ID_OVERLAY = 1003;
    private static final int REQUEST_ID_PERMISSIONS = 2001;
    private static final int REQUEST_ID_CALL_LOG_PERM = 2002;
    private static final int REQUEST_CODE_PICK_APK = 4001;

    private KantoiThreatEngine mEngine;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Navigation state: 0 = Calls/Recents, 1 = Shield/Settings
    private int mCurrentTab = 0;
    private FrameLayout mTabContentFrame;
    private ScrollView mCallsScrollView;
    private ScrollView mShieldScrollView;

    private Button mNavBtnCalls;
    private Button mNavBtnContacts;
    private Button mNavBtnKeypad;
    private Button mNavBtnSms;
    private Button mNavBtnShield;

    // Call Logs container
    private LinearLayout mCallLogsListContainer;
    private TextView mTvCallLogStatus;

    // Shield UI elements
    private TextView mTvDefaultDialerStatus;
    private Button mBtnSetDefaultDialer;
    private TextView mTvOverlayStatus;
    private Button mBtnRequestOverlay;
    private TextView mTvScreeningStatus;
    private Button mBtnEnableScreening;
    private TextView mTvHeroCount;
    private Button mBtnAutoBlockToggle;
    private Button mBtnSeniorModeToggle;
    private Button mBtnClipboardToggle;
    private TextView mTvContactAuditStatus;
    private Button mBtnAuditContacts;
    private TextView mTvSmsShieldStatus;
    private Button mBtnSmsPermission;

    // WhatsApp Silent Threat Radar
    private TextView mTvWhatsAppStatus;
    private Button mBtnScanWhatsApp;
    private Button mBtnViewWhatsAppThreats;
    private KantoiWhatsAppScanner.WhatsAppScanResult mLastWhatsAppResult;

    // Call Log Selection & Delete State
    private boolean mCallLogSelectionMode = false;
    private final Set<Long> mSelectedCallLogIds = new HashSet<Long>();
    private LinearLayout mCallLogNormalHeaderRow;
    private LinearLayout mCallLogSelectionHeaderRow;
    private TextView mTvCallLogSelectedCount;
    private Button mBtnCallLogDeleteSelected;
    private Button mBtnCallLogSelectAll;
    private List<CallLogItem> mCurrentLoadedCallLogs = new ArrayList<CallLogItem>();

    // Search elements
    private EditText mEtSearchPhone;
    private LinearLayout mSearchResultCard;
    private TextView mTvSearchResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEngine = KantoiThreatEngine.getInstance(this);
        mEngine.preloadDeviceContactsAsync();

        final SharedPreferences prefs = getSharedPreferences("kantoi_settings", Context.MODE_PRIVATE);

        // Start Clipboard Mule Sniffer service if enabled
        if (prefs.getBoolean("pref_clipboard_sniffer", true)) {
            try {
                startService(new Intent(this, KantoiClipboardService.class));
            } catch (Throwable ignored) {}
        }

        // Root container (Vertical Layout: Header + TabContentFrame + BottomNavBar)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F172A); // Deep Slate 900

        // 1. App Bar Header (Brand Identity + Changelog Button)
        LinearLayout topHeaderRow = new LinearLayout(this);
        topHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        topHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        topHeaderRow.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(10));
        topHeaderRow.setBackgroundColor(0xFF0B0F19);

        LinearLayout brandBar = new LinearLayout(this);
        brandBar.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        brandBar.setLayoutParams(brandLp);

        TextView tvLogo = new TextView(this);
        tvLogo.setText("🛡️ KANTOI");
        tvLogo.setTextColor(0xFFF8FAFC);
        tvLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvLogo.setTypeface(Typeface.DEFAULT_BOLD);
        brandBar.addView(tvLogo);

        TextView tvTagline = new TextView(this);
        tvTagline.setText("Scammer Call, Terus Kantoi");
        tvTagline.setTextColor(0xFF38BDF8); // Sky blue
        tvTagline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvTagline.setTypeface(Typeface.DEFAULT_BOLD);
        brandBar.addView(tvTagline);
        topHeaderRow.addView(brandBar);

        Button btnChangelog = new Button(this);
        btnChangelog.setText("📜 CHANGELOG v" + getAppVersionName());
        btnChangelog.setTextColor(Color.WHITE);
        btnChangelog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnChangelog.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable chBg = new GradientDrawable();
        chBg.setColor(0xFF0284C7);
        chBg.setCornerRadius(dpToPx(6));
        btnChangelog.setBackground(chBg);
        btnChangelog.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        btnChangelog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ChangelogActivity.class));
            }
        });
        topHeaderRow.addView(btnChangelog);

        root.addView(topHeaderRow);

        // 2. Tab Content Frame (Fills middle screen)
        mTabContentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mTabContentFrame.setLayoutParams(frameLp);

        // Build both tab views
        mCallsScrollView = buildCallsView(prefs);
        mShieldScrollView = buildShieldView(prefs);

        mTabContentFrame.addView(mCallsScrollView);
        mTabContentFrame.addView(mShieldScrollView);
        root.addView(mTabContentFrame);

        // 3. Fixed Bottom Navigation Bar (World-Class Phone App Dock)
        View bottomNavBar = buildBottomNavBar();
        root.addView(bottomNavBar);

        setContentView(root);

        // Handle incoming dial intent
        Intent it = getIntent();
        if (it != null && (Intent.ACTION_DIAL.equals(it.getAction()) || Intent.ACTION_VIEW.equals(it.getAction()))) {
            Uri data = it.getData();
            if (data != null && "tel".equalsIgnoreCase(data.getScheme())) {
                String number = data.getSchemeSpecificPart();
                if (number != null && !number.isEmpty()) {
                    Intent dIt = new Intent(this, DialerActivity.class);
                    dIt.putExtra(DialerActivity.EXTRA_INITIAL_NUMBER, number);
                    startActivity(dIt);
                }
            }
        }

        // Set default tab to Calls / Recents
        selectTab(0);
        updateCallSystemStatusUI();
        updateContactAndSmsStatusUI();
        KantoiBadgeManager.updateBadge(this);
        runSilentWhatsAppScan();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            if (intent.hasExtra("select_tab")) {
                int tab = intent.getIntExtra("select_tab", 0);
                selectTab(tab);
            } else if (intent.getBooleanExtra("extra_open_defense_tab", false)) {
                selectTab(1);
                intent.removeExtra("extra_open_defense_tab");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent() != null && getIntent().getBooleanExtra("extra_open_defense_tab", false)) {
            selectTab(1);
            getIntent().removeExtra("extra_open_defense_tab");
        }
        mEngine.preloadDeviceContactsAsync();
        mEngine.checkAndAutoSyncTelegramAsync(this, false);
        updateCallSystemStatusUI();
        updateContactAndSmsStatusUI();
        updateWhatsAppCardStatus();
        KantoiBadgeManager.updateBadge(this);
        KantoiThreatEngine.ensureFloatingPill(this);
        if (mCurrentTab == 0) {
            loadCallLogsAsync();
        }
    }

    public void selectTab(int tabIndex) {
        mCurrentTab = tabIndex;
        if (tabIndex == 0) {
            // Calls View
            mCallsScrollView.setVisibility(View.VISIBLE);
            mShieldScrollView.setVisibility(View.GONE);
            updateNavButtonState(mNavBtnCalls, true);
            updateNavButtonState(mNavBtnShield, false);
            KantoiBadgeManager.updateBadge(this);
            loadCallLogsAsync();
        } else if (tabIndex == 1) {
            // Shield & Settings View
            mCallsScrollView.setVisibility(View.GONE);
            mShieldScrollView.setVisibility(View.VISIBLE);
            updateNavButtonState(mNavBtnCalls, false);
            updateNavButtonState(mNavBtnShield, true);
            updateCallSystemStatusUI();
        }
    }

    // ==================== TAB 0: CALLS & RECENTS VIEW ====================
    private ScrollView buildCallsView(final SharedPreferences prefs) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0F172A);
        scrollView.setSmoothScrollingEnabled(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setClipToPadding(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(75)); // 75dp bottom padding for dock!

        // Sleek Unified Search & Scammer Lookup Bar
        LinearLayout searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.VERTICAL);
        setMarginTop(searchContainer, 6);

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sbBg = new GradientDrawable();
        sbBg.setColor(0xFF1E293B);
        sbBg.setStroke(dpToPx(1), 0xFF334155);
        sbBg.setCornerRadius(dpToPx(10));
        searchBox.setBackground(sbBg);
        searchBox.setPadding(dpToPx(12), dpToPx(4), dpToPx(8), dpToPx(4));

        TextView tvSearchIcon = new TextView(this);
        tvSearchIcon.setText("🔍");
        tvSearchIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvSearchIcon.setPadding(0, 0, dpToPx(8), 0);
        searchBox.addView(tvSearchIcon);

        mEtSearchPhone = new EditText(this);
        mEtSearchPhone.setHint("Cari nama, nombor atau semak scammer...");
        mEtSearchPhone.setHintTextColor(0xFF64748B);
        mEtSearchPhone.setTextColor(Color.WHITE);
        mEtSearchPhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mEtSearchPhone.setInputType(InputType.TYPE_CLASS_TEXT);
        mEtSearchPhone.setBackgroundColor(Color.TRANSPARENT);
        mEtSearchPhone.setPadding(0, dpToPx(8), dpToPx(8), dpToPx(8));
        mEtSearchPhone.setSingleLine(true);
        mEtSearchPhone.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        mEtSearchPhone.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                performSearch();
                return true;
            }
        });
        mEtSearchPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCallLogs(s != null ? s.toString() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mEtSearchPhone.setLayoutParams(etLp);
        searchBox.addView(mEtSearchPhone);

        Button btnSemak = new Button(this);
        btnSemak.setText("SEMAK");
        btnSemak.setTextColor(Color.WHITE);
        btnSemak.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnSemak.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable semakBg = new GradientDrawable();
        semakBg.setColor(0xFF0284C7);
        semakBg.setCornerRadius(dpToPx(6));
        btnSemak.setBackground(semakBg);
        btnSemak.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        btnSemak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });
        searchBox.addView(btnSemak);

        searchContainer.addView(searchBox);

        mSearchResultCard = new LinearLayout(this);
        mSearchResultCard.setOrientation(LinearLayout.VERTICAL);
        mSearchResultCard.setVisibility(View.GONE);
        setMarginTop(mSearchResultCard, 8);
        searchContainer.addView(mSearchResultCard);

        layout.addView(searchContainer);

        // Call Logs Section Headers (Normal & Selection Modes)
        mCallLogNormalHeaderRow = new LinearLayout(this);
        mCallLogNormalHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        mCallLogNormalHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        setMarginTop(mCallLogNormalHeaderRow, 16);

        TextView tvLogHeader = new TextView(this);
        tvLogHeader.setText("📋 SEJARAH PANGGILAN");
        tvLogHeader.setTextColor(0xFF94A3B8);
        tvLogHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvLogHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams logHlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvLogHeader.setLayoutParams(logHlp);
        mCallLogNormalHeaderRow.addView(tvLogHeader);

        Button btnSelectLog = new Button(this);
        btnSelectLog.setText("🗑️ PILIH");
        btnSelectLog.setTextColor(0xFFE2E8F0);
        btnSelectLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnSelectLog.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable selBg = new GradientDrawable();
        selBg.setColor(0xFF334155);
        selBg.setCornerRadius(dpToPx(6));
        btnSelectLog.setBackground(selBg);
        btnSelectLog.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnSelectLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterCallLogSelectionMode(null);
            }
        });
        mCallLogNormalHeaderRow.addView(btnSelectLog);

        Button btnRefreshLog = new Button(this);
        btnRefreshLog.setText("🔄 SEGERAK");
        btnRefreshLog.setTextColor(0xFF38BDF8);
        btnRefreshLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnRefreshLog.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable rfBg = new GradientDrawable();
        rfBg.setColor(0xFF1E293B);
        rfBg.setCornerRadius(dpToPx(6));
        btnRefreshLog.setBackground(rfBg);
        btnRefreshLog.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rfLp.leftMargin = dpToPx(6);
        btnRefreshLog.setLayoutParams(rfLp);
        btnRefreshLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadCallLogsAsync();
            }
        });
        mCallLogNormalHeaderRow.addView(btnRefreshLog);
        layout.addView(mCallLogNormalHeaderRow);

        // Selection Toolbar Header Row (shown only in selection mode)
        mCallLogSelectionHeaderRow = new LinearLayout(this);
        mCallLogSelectionHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        mCallLogSelectionHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        mCallLogSelectionHeaderRow.setVisibility(View.GONE);
        setMarginTop(mCallLogSelectionHeaderRow, 16);

        mBtnCallLogSelectAll = new Button(this);
        mBtnCallLogSelectAll.setText("✓ SEMUA");
        mBtnCallLogSelectAll.setTextColor(0xFF34D399); // Emerald green
        mBtnCallLogSelectAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnCallLogSelectAll.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable saBg = new GradientDrawable();
        saBg.setColor(0xFF1E293B);
        saBg.setCornerRadius(dpToPx(6));
        mBtnCallLogSelectAll.setBackground(saBg);
        mBtnCallLogSelectAll.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        mBtnCallLogSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCallLogSelectAll();
            }
        });
        mCallLogSelectionHeaderRow.addView(mBtnCallLogSelectAll);

        mTvCallLogSelectedCount = new TextView(this);
        mTvCallLogSelectedCount.setText("0 Dipilih");
        mTvCallLogSelectedCount.setTextColor(0xFFF8FAFC);
        mTvCallLogSelectedCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTvCallLogSelectedCount.setTypeface(Typeface.DEFAULT_BOLD);
        mTvCallLogSelectedCount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mTvCallLogSelectedCount.setLayoutParams(cntLp);
        mCallLogSelectionHeaderRow.addView(mTvCallLogSelectedCount);

        mBtnCallLogDeleteSelected = new Button(this);
        mBtnCallLogDeleteSelected.setText("🗑️ PADAM");
        mBtnCallLogDeleteSelected.setTextColor(Color.WHITE);
        mBtnCallLogDeleteSelected.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnCallLogDeleteSelected.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(0xFFEF4444); // Red
        delBg.setCornerRadius(dpToPx(6));
        mBtnCallLogDeleteSelected.setBackground(delBg);
        mBtnCallLogDeleteSelected.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        mBtnCallLogDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteSelectedCallLogs();
            }
        });
        mCallLogSelectionHeaderRow.addView(mBtnCallLogDeleteSelected);

        Button btnCancelSelect = new Button(this);
        btnCancelSelect.setText("✕ BATAL");
        btnCancelSelect.setTextColor(0xFF94A3B8);
        btnCancelSelect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCancelSelect.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable canBg = new GradientDrawable();
        canBg.setColor(0xFF1E293B);
        canBg.setCornerRadius(dpToPx(6));
        btnCancelSelect.setBackground(canBg);
        btnCancelSelect.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        canLp.leftMargin = dpToPx(6);
        btnCancelSelect.setLayoutParams(canLp);
        btnCancelSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitCallLogSelectionMode();
            }
        });
        mCallLogSelectionHeaderRow.addView(btnCancelSelect);

        layout.addView(mCallLogSelectionHeaderRow);

        // Call Logs Container
        mCallLogsListContainer = new LinearLayout(this);
        mCallLogsListContainer.setOrientation(LinearLayout.VERTICAL);
        setMarginTop(mCallLogsListContainer, 8);

        mTvCallLogStatus = new TextView(this);
        mTvCallLogStatus.setText("⏳ Sedang memuatkan sejarah panggilan...");
        mTvCallLogStatus.setTextColor(0xFF64748B);
        mTvCallLogStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mCallLogsListContainer.addView(mTvCallLogStatus);

        layout.addView(mCallLogsListContainer);

        scrollView.addView(layout);
        return scrollView;
    }

    private void loadCallLogsAsync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                mCallLogsListContainer.removeAllViews();

                LinearLayout permCard = createCard(0xFF1E293B, 0xFFF59E0B, 10);
                permCard.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

                TextView tv = new TextView(this);
                tv.setText("⚠️ Kebenaran Sejarah Panggilan (Call Log) belum diberikan.\nBenarkan akses untuk memaparkan senarai panggilan & semakan scammer:");
                tv.setTextColor(0xFFFEF3C7);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                permCard.addView(tv);

                Button btnPerm = createButton("🔑 BENARKAN AKSES SEJARAH PANGGILAN", 0xFFD97706, Color.WHITE);
                setMarginTop(btnPerm, 8);
                btnPerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, REQUEST_ID_CALL_LOG_PERM);
                    }
                });
                permCard.addView(btnPerm);
                mCallLogsListContainer.addView(permCard);
                return;
            }
        }

        if (mCurrentLoadedCallLogs.isEmpty()) {
            mCallLogsListContainer.removeAllViews();
            TextView tvLoading = new TextView(this);
            tvLoading.setText("⏳ Sedang memuatkan sejarah panggilan...");
            tvLoading.setTextColor(0xFF64748B);
            tvLoading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            mCallLogsListContainer.addView(tvLoading);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<CallLogItem> logs = queryCallLogs();
                final int unreadMissed = KantoiBadgeManager.getUnreadMissedCallsCount(MainActivity.this);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        boolean isSame = false;
                        if (!mCurrentLoadedCallLogs.isEmpty() && mCurrentLoadedCallLogs.size() == logs.size()) {
                            if (mCurrentLoadedCallLogs.get(0).id == logs.get(0).id && mCurrentLoadedCallLogs.get(0).date == logs.get(0).date) {
                                isSame = true;
                            }
                        }
                        mCurrentLoadedCallLogs = logs;
                        if (!isSame || mCallLogsListContainer.getChildCount() <= 1) {
                            renderLoadedCallLogs(unreadMissed);
                        }
                    }
                });
            }
        }).start();
    }

    private static final int INITIAL_CALL_LOG_BATCH = 15;
    private static final int CHUNK_CALL_LOG_BATCH = 15;
    private int mRenderedCallLogsCount = 0;
    private int mLastUnreadMissedCount = 0;

    private void renderLoadedCallLogs() {
        renderLoadedCallLogs(mLastUnreadMissedCount);
    }

    private void renderLoadedCallLogs(final int unreadMissed) {
        mLastUnreadMissedCount = unreadMissed;
        mCallLogsListContainer.removeAllViews();
        mRenderedCallLogsCount = 0;
        if (mCurrentLoadedCallLogs.isEmpty()) {
            TextView emptyTv = new TextView(MainActivity.this);
            emptyTv.setText("📭 Tiada rekod panggilan ditemui.");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            emptyTv.setGravity(Gravity.CENTER);
            setMarginTop(emptyTv, 20);
            mCallLogsListContainer.addView(emptyTv);
            return;
        }

        if (unreadMissed > 0) {
            LinearLayout missedBanner = createCard(0xFF450A0A, 0xFFDC2626, 8);
            missedBanner.setOrientation(LinearLayout.HORIZONTAL);
            missedBanner.setGravity(Gravity.CENTER_VERTICAL);
            missedBanner.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            setMarginTop(missedBanner, 8);

            TextView tvMissed = new TextView(MainActivity.this);
            tvMissed.setText("🔔 " + unreadMissed + " Panggilan Terlepas");
            tvMissed.setTextColor(0xFFFECACA);
            tvMissed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvMissed.setTypeface(Typeface.DEFAULT_BOLD);
            tvMissed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            missedBanner.addView(tvMissed);

            Button btnClear = createButton("KOSONGKAN LENCANA", 0xFFDC2626, Color.WHITE);
            btnClear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnClear.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    KantoiBadgeManager.markMissedCallsAsRead(MainActivity.this);
                    loadCallLogsAsync();
                }
            });
            missedBanner.addView(btnClear);
            mCallLogsListContainer.addView(missedBanner);
        }

        final int total = mCurrentLoadedCallLogs.size();
        final int initialLimit = Math.min(INITIAL_CALL_LOG_BATCH, total);
        for (int i = 0; i < initialLimit; i++) {
            CallLogItem item = mCurrentLoadedCallLogs.get(i);
            View itemView = createCallLogItemView(item);
            setMarginTop(itemView, 8);
            mCallLogsListContainer.addView(itemView);
        }
        mRenderedCallLogsCount = initialLimit;

        if (mRenderedCallLogsCount < total) {
            postNextCallLogsBatch(total);
        }

        KantoiBadgeManager.updateBadge(MainActivity.this);
    }

    private void postNextCallLogsBatch(final int total) {
        mCallLogsListContainer.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                int start = mRenderedCallLogsCount;
                if (start >= total || start >= mCurrentLoadedCallLogs.size()) return;
                int end = Math.min(start + CHUNK_CALL_LOG_BATCH, Math.min(total, mCurrentLoadedCallLogs.size()));
                for (int i = start; i < end; i++) {
                    CallLogItem item = mCurrentLoadedCallLogs.get(i);
                    View itemView = createCallLogItemView(item);
                    setMarginTop(itemView, 8);
                    mCallLogsListContainer.addView(itemView);
                }
                mRenderedCallLogsCount = end;
                if (mRenderedCallLogsCount < total && mRenderedCallLogsCount < mCurrentLoadedCallLogs.size()) {
                    postNextCallLogsBatch(total);
                }
            }
        });
    }

    private void filterCallLogs(String query) {
        if (mCurrentLoadedCallLogs == null) return;
        if (query == null || query.trim().isEmpty()) {
            renderLoadedCallLogs();
            return;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        String digits = query.replaceAll("[^0-9]", "");
        List<CallLogItem> filtered = new ArrayList<CallLogItem>();
        for (CallLogItem item : mCurrentLoadedCallLogs) {
            boolean matchName = (item.name != null && item.name.toLowerCase(Locale.ROOT).contains(q));
            boolean matchNum = (!digits.isEmpty() && item.number.replaceAll("[^0-9]", "").contains(digits));
            if (matchName || matchNum) {
                filtered.add(item);
            }
        }
        renderFilteredCallLogs(filtered);
    }

    private void renderFilteredCallLogs(List<CallLogItem> items) {
        mCallLogsListContainer.removeAllViews();
        if (items.isEmpty()) {
            TextView emptyTv = new TextView(MainActivity.this);
            emptyTv.setText("🔍 Tiada rekod panggilan sepadan dengan carian.");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            emptyTv.setGravity(Gravity.CENTER);
            setMarginTop(emptyTv, 20);
            mCallLogsListContainer.addView(emptyTv);
            return;
        }
        for (CallLogItem item : items) {
            View itemView = createCallLogItemView(item);
            setMarginTop(itemView, 8);
            mCallLogsListContainer.addView(itemView);
        }
    }

    private List<CallLogItem> queryCallLogs() {
        List<CallLogItem> list = new ArrayList<>();
        try {
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                },
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT 50"
            );

            if (cursor != null) {
                int colId = cursor.getColumnIndex(CallLog.Calls._ID);
                int colNum = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int colName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int colType = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int colDate = cursor.getColumnIndex(CallLog.Calls.DATE);
                int colDur = cursor.getColumnIndex(CallLog.Calls.DURATION);

                while (cursor.moveToNext()) {
                    CallLogItem item = new CallLogItem();
                    item.id = colId != -1 ? cursor.getLong(colId) : -1;
                    item.number = colNum != -1 ? cursor.getString(colNum) : "";
                    item.name = colName != -1 ? cursor.getString(colName) : null;
                    item.type = colType != -1 ? cursor.getInt(colType) : 1;
                    item.date = colDate != -1 ? cursor.getLong(colDate) : 0;
                    item.duration = colDur != -1 ? cursor.getLong(colDur) : 0;

                    String local = KantoiThreatEngine.normalizeToLocal(item.number);
                    String digits = item.number.replaceAll("[^0-9]", "");
                    if (item.name != null && !item.name.trim().isEmpty() && !item.name.equalsIgnoreCase(item.number) && !item.name.replaceAll("[^0-9]", "").equals(digits) && !item.name.replaceAll("[^0-9]", "").equals(local)) {
                        item.isSafeContact = true;
                        mEngine.cacheContactName(local, item.name);
                    } else {
                        // Selesaikan nama pemilik nombor daripada buku telefon peranti
                        String resolved = mEngine.getContactDisplayNameFast(item.number);
                        if (resolved == null) resolved = mEngine.getContactDisplayNameFast(local);
                        if (resolved != null && !resolved.trim().isEmpty()) {
                            item.name = resolved;
                            item.isSafeContact = true;
                        } else {
                            item.isSafeContact = mEngine.isSafeContactFast(local);
                            if (item.isSafeContact) {
                                item.name = mEngine.getContactDisplayNameFast(local);
                            }
                        }
                    }

                    KantoiThreatEngine.ScammerThreat threat = mEngine.checkThreat(local);
                    if (threat != null) {
                        item.isScammer = true;
                        item.scammerDetails = threat.suspectName + " (" + threat.riskTag + ")";
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

    private View createCallLogItemView(final CallLogItem item) {
        final boolean isSelected = mCallLogSelectionMode && mSelectedCallLogIds.contains(item.id);

        int bgColor = item.isScammer ? 0xFF450A0A : 0xFF1E293B;
        int strokeColor = item.isScammer ? 0xFFDC2626 : 0xFF334155;

        if (mCallLogSelectionMode && isSelected) {
            bgColor = 0xFF1E3A8A; // Deep blue highlight when selected
            strokeColor = 0xFF38BDF8; // Bright sky stroke
        }

        // Direct horizontal layout without redundant nested contentRow
        LinearLayout row = createCard(bgColor, strokeColor, 10, LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        // Selection Checkbox (Visible in selection mode)
        if (mCallLogSelectionMode) {
            TextView tvCheckbox = new TextView(this);
            tvCheckbox.setText(isSelected ? "☑️" : "⬜");
            tvCheckbox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tvCheckbox.setPadding(0, 0, dpToPx(10), 0);
            row.addView(tvCheckbox);
        }

        // Call Type Icon indicator
        TextView tvIcon = new TextView(this);
        if (item.isScammer) {
            tvIcon.setText("🚨");
        } else if (item.type == CallLog.Calls.MISSED_TYPE) {
            tvIcon.setText("↙");
            tvIcon.setTextColor(0xFFEF4444); // Red missed
        } else if (item.type == CallLog.Calls.OUTGOING_TYPE) {
            tvIcon.setText("↗");
            tvIcon.setTextColor(0xFF38BDF8); // Blue outgoing
        } else {
            tvIcon.setText("↙");
            tvIcon.setTextColor(0xFF34D399); // Green incoming
        }
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvIcon.setTypeface(Typeface.DEFAULT_BOLD);
        tvIcon.setPadding(0, 0, dpToPx(12), 0);
        row.addView(tvIcon);

        // Details Column
        LinearLayout detailsCol = new LinearLayout(this);
        detailsCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        detailsCol.setLayoutParams(dLp);

        TextView tvTitle = new TextView(this);
        String titleText = (item.name != null && !item.name.isEmpty()) ? item.name : KantoiThreatEngine.formatPhoneNumber(item.number);
        tvTitle.setText(titleText);
        tvTitle.setTextColor(item.isScammer ? 0xFFFCA5A5 : Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detailsCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (item.name != null && !item.name.isEmpty()) {
            sb.append(KantoiThreatEngine.formatPhoneNumber(item.number)).append(" • ");
        }
        sb.append(formatCallDate(item.date));
        if (item.duration > 0) {
            sb.append(" (").append(formatCallDuration(item.duration)).append(")");
        }
        tvSub.setText(sb.toString());
        tvSub.setTextColor(0xFF94A3B8);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(tvSub, 2);
        detailsCol.addView(tvSub);

        if (item.isScammer) {
            TextView tvScamBadge = new TextView(this);
            tvScamBadge.setText("🚨 SCAMMER DISAHKAN PDRM: " + item.scammerDetails);
            tvScamBadge.setTextColor(0xFFEF4444);
            tvScamBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvScamBadge.setTypeface(Typeface.DEFAULT_BOLD);
            setMarginTop(tvScamBadge, 2);
            detailsCol.addView(tvScamBadge);
        }

        row.addView(detailsCol);

        // Quick Action Buttons on right (WhatsApp & Call) (Only when not in selection mode)
        if (!mCallLogSelectionMode) {
            Button btnWhatsApp = new Button(this);
            btnWhatsApp.setText("💬");
            btnWhatsApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            btnWhatsApp.setTextColor(Color.WHITE);
            GradientDrawable waBg = new GradientDrawable();
            waBg.setShape(GradientDrawable.OVAL);
            waBg.setColor(0xFF059669); // WhatsApp Emerald Green
            btnWhatsApp.setBackground(waBg);
            LinearLayout.LayoutParams waLp = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            waLp.setMarginEnd(dpToPx(8));
            btnWhatsApp.setLayoutParams(waLp);
            btnWhatsApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openWhatsAppChat(item.number);
                }
            });
            row.addView(btnWhatsApp);

            Button btnCall = new Button(this);
            btnCall.setText("📞");
            btnCall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            btnCall.setTextColor(Color.WHITE);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setShape(GradientDrawable.OVAL);
            cBg.setColor(0xFF0284C7);
            btnCall.setBackground(cBg);
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            cbLp.setMarginEnd(dpToPx(4));
            btnCall.setLayoutParams(cbLp);
            btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent callIt = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(item.number)));
                    startActivity(callIt);
                }
            });
            row.addView(btnCall);
        }

        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallLogSelectionMode) {
                    toggleCallLogItemSelection(item.id);
                } else {
                    showCallLogActionDialog(item);
                }
            }
        });

        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mCallLogSelectionMode) {
                    enterCallLogSelectionMode(item.id);
                    return true;
                }
                return false;
            }
        });

        return row;
    }

    private void enterCallLogSelectionMode(Long preselectedId) {
        mCallLogSelectionMode = true;
        mSelectedCallLogIds.clear();
        if (preselectedId != null && preselectedId > 0) {
            mSelectedCallLogIds.add(preselectedId);
        }
        if (mCallLogNormalHeaderRow != null) mCallLogNormalHeaderRow.setVisibility(View.GONE);
        if (mCallLogSelectionHeaderRow != null) mCallLogSelectionHeaderRow.setVisibility(View.VISIBLE);
        updateCallLogSelectionUI();
        renderLoadedCallLogs();
    }

    private void exitCallLogSelectionMode() {
        mCallLogSelectionMode = false;
        mSelectedCallLogIds.clear();
        if (mCallLogNormalHeaderRow != null) mCallLogNormalHeaderRow.setVisibility(View.VISIBLE);
        if (mCallLogSelectionHeaderRow != null) mCallLogSelectionHeaderRow.setVisibility(View.GONE);
        renderLoadedCallLogs();
    }

    private void toggleCallLogItemSelection(long id) {
        if (mSelectedCallLogIds.contains(id)) {
            mSelectedCallLogIds.remove(id);
        } else {
            mSelectedCallLogIds.add(id);
        }
        updateCallLogSelectionUI();
        renderLoadedCallLogs();
    }

    private void toggleCallLogSelectAll() {
        if (!mCurrentLoadedCallLogs.isEmpty() && mSelectedCallLogIds.size() == mCurrentLoadedCallLogs.size()) {
            mSelectedCallLogIds.clear();
        } else {
            for (CallLogItem item : mCurrentLoadedCallLogs) {
                if (item.id > 0) {
                    mSelectedCallLogIds.add(item.id);
                }
            }
        }
        updateCallLogSelectionUI();
        renderLoadedCallLogs();
    }

    private void updateCallLogSelectionUI() {
        int count = mSelectedCallLogIds.size();
        if (mTvCallLogSelectedCount != null) {
            mTvCallLogSelectedCount.setText(count + " Dipilih");
        }
        if (mBtnCallLogDeleteSelected != null) {
            mBtnCallLogDeleteSelected.setEnabled(count > 0);
            mBtnCallLogDeleteSelected.setAlpha(count > 0 ? 1.0f : 0.4f);
        }
        if (mBtnCallLogSelectAll != null) {
            boolean allSelected = !mCurrentLoadedCallLogs.isEmpty() && (mSelectedCallLogIds.size() == mCurrentLoadedCallLogs.size());
            mBtnCallLogSelectAll.setText(allSelected ? "☐ NYAH" : "✓ SEMUA");
        }
    }

    private void deleteSingleCallLog(final CallLogItem item) {
        new AlertDialog.Builder(this)
            .setTitle("🗑️ Padam Rekod Panggilan?")
            .setMessage("Adakah anda pasti mahu memadam rekod panggilan ini (" + 
                (item.name != null ? item.name : KantoiThreatEngine.formatPhoneNumber(item.number)) + 
                ") daripada peranti anda?")
            .setPositiveButton("PADAM", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        int rows = getContentResolver().delete(
                            CallLog.Calls.CONTENT_URI,
                            CallLog.Calls._ID + " = ?",
                            new String[]{ String.valueOf(item.id) }
                        );
                        Toast.makeText(MainActivity.this, "Rekod panggilan telah dipadam.", Toast.LENGTH_SHORT).show();
                        loadCallLogsAsync();
                    } catch (Throwable t) {
                        Toast.makeText(MainActivity.this, "Gagal memadam: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void confirmDeleteSelectedCallLogs() {
        final int count = mSelectedCallLogIds.size();
        if (count == 0) return;

        new AlertDialog.Builder(this)
            .setTitle("🗑️ Padam " + count + " Rekod Panggilan?")
            .setMessage("Adakah anda pasti mahu memadam " + count + " rekod panggilan yang dipilih daripada peranti anda?")
            .setPositiveButton("PADAM (" + count + ")", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final Set<Long> toDelete = new HashSet<Long>(mSelectedCallLogIds);
                    exitCallLogSelectionMode();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int deletedCount = 0;
                            for (Long id : toDelete) {
                                try {
                                    int rows = getContentResolver().delete(
                                        CallLog.Calls.CONTENT_URI,
                                        CallLog.Calls._ID + " = ?",
                                        new String[]{ String.valueOf(id) }
                                    );
                                    if (rows > 0) deletedCount++;
                                } catch (Throwable ignored) {}
                            }
                            final int finalCount = deletedCount;
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "Berjaya memadam " + finalCount + " rekod panggilan.", Toast.LENGTH_SHORT).show();
                                    loadCallLogsAsync();
                                }
                            });
                        }
                    }).start();
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void showCallLogActionDialog(final CallLogItem item) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        String title = item.name != null ? item.name : KantoiThreatEngine.formatPhoneNumber(item.number);
        b.setTitle(title);

        StringBuilder sb = new StringBuilder();
        sb.append("Nombor: ").append(KantoiThreatEngine.formatPhoneNumber(item.number)).append("\n");
        sb.append("Masa: ").append(formatCallDate(item.date)).append("\n");
        if (item.duration > 0) {
            sb.append("Tempoh: ").append(formatCallDuration(item.duration)).append("\n");
        }
        if (item.isScammer) {
            sb.append("\n🚨 STATUS: SCAMMER DISAHKAN PDRM\n• ").append(item.scammerDetails).append("\n");
        } else if (item.isSafeContact) {
            sb.append("\n✅ STATUS: Kenalan Buku Telefon Selamat\n");
        } else {
            sb.append("\nℹ️ STATUS: Nombor Asing (Tiada rekod penipuan)\n");
        }
        b.setMessage(sb.toString());

        CharSequence[] options = new CharSequence[]{
            "📞 Panggil (" + KantoiThreatEngine.formatPhoneNumber(item.number) + ")",
            "💬 Chat WhatsApp Terus",
            "✉️ Hantar Mesej SMS",
            "👤 Lihat Profil / Simpan Kenalan",
            "🗑️ Padam Rekod Panggilan Ini",
            "☑️ Masuk Mod Pilih & Padam Banyak"
        };

        b.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: {
                        Intent callIt = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(item.number)));
                        startActivity(callIt);
                        break;
                    }
                    case 1: {
                        openWhatsAppChat(item.number);
                        break;
                    }
                    case 2: {
                        Intent smsIt = new Intent(MainActivity.this, SmsActivity.class);
                        smsIt.putExtra(SmsActivity.EXTRA_RECIPIENT, item.number);
                        startActivity(smsIt);
                        break;
                    }
                    case 3: {
                        Intent pIt = new Intent(MainActivity.this, ContactProfileActivity.class);
                        pIt.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, item.number);
                        startActivity(pIt);
                        break;
                    }
                    case 4: {
                        deleteSingleCallLog(item);
                        break;
                    }
                    case 5: {
                        enterCallLogSelectionMode(item.id);
                        break;
                    }
                }
            }
        });

        b.setNegativeButton("TUTUP", null);
        b.show();
    }

    private void openWhatsAppChat(String phone) {
        if (phone == null || phone.trim().isEmpty()) return;
        String cleanDigits = phone.replaceAll("[^0-9]", "");
        if (cleanDigits.startsWith("0")) {
            cleanDigits = "60" + cleanDigits.substring(1);
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanDigits));
            startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("whatsapp://send?phone=" + cleanDigits));
                startActivity(intent);
            } catch (Throwable t2) {
                Toast.makeText(this, "Aplikasi WhatsApp tidak dipasang pada peranti ini", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String formatCallDate(long timestamp) {
        if (timestamp <= 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff < 60 * 1000) {
            return "Baru sahaja";
        } else if (diff < 60 * 60 * 1000) {
            return (diff / (60 * 1000)) + " min lalu";
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
        if (diff < 24 * 60 * 60 * 1000) {
            return "Hari ini, " + timeFormat.format(new Date(timestamp));
        } else if (diff < 48 * 60 * 60 * 1000) {
            return "Semalam, " + timeFormat.format(new Date(timestamp));
        } else {
            return dateFormat.format(new Date(timestamp));
        }
    }

    private String formatCallDuration(long sec) {
        if (sec <= 0) return "";
        long m = sec / 60;
        long s = sec % 60;
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }

    // ==================== TAB 1: SHIELD & SETTINGS VIEW ====================
    private ScrollView buildShieldView(final SharedPreferences prefs) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0F172A);
        scrollView.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(75)); // 75dp bottom padding for dock!

        // 1. Hero Protection Status Card (Clickable to view DB stats)
        LinearLayout heroCard = createCard(0xFF1E293B, 0xFF0284C7, 12);
        heroCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        TextView tvHeroTitle = new TextView(this);
        tvHeroTitle.setText("🛡️ PERTAHANAN AKTIF & MENJAGA ANDA");
        tvHeroTitle.setTextColor(0xFF34D399); // Emerald bright
        tvHeroTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvHeroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroCard.addView(tvHeroTitle);

        int count = mEngine.getThreatCount();
        mTvHeroCount = new TextView(this);
        mTvHeroCount.setText(String.format("%,d", count) + " Rekod Scammer Disahkan");
        mTvHeroCount.setTextColor(Color.WHITE);
        mTvHeroCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mTvHeroCount.setTypeface(Typeface.DEFAULT_BOLD);
        mTvHeroCount.setPadding(0, dpToPx(4), 0, dpToPx(2));
        heroCard.addView(mTvHeroCount);

        TextView tvHeroSub = new TextView(this);
        tvHeroSub.setText("Lowyat • CariGold • Telegram OCR • SemakMule CCID\n(Tekan sini untuk melihat pecahan sumber)");
        tvHeroSub.setTextColor(0xFF94A3B8);
        tvHeroSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        heroCard.addView(tvHeroSub);

        heroCard.setClickable(true);
        heroCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiThreatEngine.showDatabaseInfoDialog(MainActivity.this);
            }
        });
        root.addView(heroCard);

        // 2. Telegram OTA Threat Sync Card (@crowdcaller)
        LinearLayout syncCard = createCard(0xFF1E293B, 0xFF0284C7, 12);
        syncCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(syncCard, 10);

        TextView tvSyncTitle = new TextView(this);
        tvSyncTitle.setText("🔄 KEMASKINI PANGKALAN DATA (TELEGRAM SYNC)");
        tvSyncTitle.setTextColor(0xFF38BDF8);
        tvSyncTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSyncTitle.setTypeface(Typeface.DEFAULT_BOLD);
        syncCard.addView(tvSyncTitle);

        TextView tvSyncDesc = new TextView(this);
        tvSyncDesc.setText("Segerakkan senarai scammer terkini terus daripada saluran Telegram rasmi @crowdcaller (Percuma seumur hidup).");
        tvSyncDesc.setTextColor(0xFFE2E8F0);
        tvSyncDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSyncDesc.setPadding(0, dpToPx(4), 0, dpToPx(8));
        syncCard.addView(tvSyncDesc);

        final Button btnSync = createButton("🚀 KEMASKINI DARI @CROWDCALLER SEKARANG", 0xFF0284C7, Color.WHITE);
        btnSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSync.setEnabled(false);
                btnSync.setText("⏳ Menyambung ke @crowdcaller...");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final KantoiThreatEngine.TelegramSyncResult res = mEngine.syncFromTelegram("crowdcaller");
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                btnSync.setEnabled(true);
                                btnSync.setText("🚀 KEMASKINI DARI @CROWDCALLER SEKARANG");
                                if (res.success && mTvHeroCount != null) {
                                    mTvHeroCount.setText(String.format("%,d", res.totalCount) + " Rekod Scammer Disahkan");
                                }
                                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                                b.setTitle(res.success ? "✅ Kemaskini Telegram" : "⚠️ Status Kemaskini");
                                b.setMessage(res.statusMessage);
                                b.setPositiveButton("BAIK", null);
                                b.show();
                            }
                        });
                    }
                }).start();
            }
        });
        syncCard.addView(btnSync);
        root.addView(syncCard);

        // 2b. Kad Anjuran Lapor Scammer Komuniti (@crowdcallerbot)
        LinearLayout reportBotCard = createCard(0xFF1E293B, 0xFF059669, 12);
        reportBotCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(reportBotCard, 10);

        TextView tvBotTitle = new TextView(this);
        tvBotTitle.setText("📢 LAPOR SCAMMER KE KOMUNITI (@crowdcallerbot)");
        tvBotTitle.setTextColor(0xFF34D399);
        tvBotTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBotTitle.setTypeface(Typeface.DEFAULT_BOLD);
        reportBotCard.addView(tvBotTitle);

        TextView tvBotDesc = new TextView(this);
        tvBotDesc.setText("Dihubungi nombor mencurigakan atau menemui akaun bank keldai? Laporkan nombor scammer atau hantar tangkap layar (screenshot) resit/chat kepada @crowdcallerbot di Telegram secara teks atau bergambar untuk disahkan oleh pentadbir demi melindungi jutaan pengguna KANTOI.");
        tvBotDesc.setTextColor(0xFFE2E8F0);
        tvBotDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBotDesc.setPadding(0, dpToPx(4), 0, dpToPx(8));
        reportBotCard.addView(tvBotDesc);

        final Button btnLaunchBot = createButton("💬 HANTAR LAPORAN KE @CROWDCALLERBOT", 0xFF059669, Color.WHITE);
        btnLaunchBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/crowdcallerbot"));
                    startActivity(i);
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "Sila buka Telegram dan cari @crowdcallerbot", Toast.LENGTH_SHORT).show();
                }
            }
        });
        reportBotCard.addView(btnLaunchBot);
        root.addView(reportBotCard);

        // 2c. Kad Gelembung Terapung 1-Ketikan (Idea 1: Floating Threat HUD)
        LinearLayout hudCard = createCard(0xFF1E293B, 0xFF0284C7, 12);
        hudCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(hudCard, 10);

        TextView tvHudTitle = new TextView(this);
        tvHudTitle.setText("🛡️ GELEMBUNG TERAPUNG 1-KETIKAN (FLOATING THREAT HUD)");
        tvHudTitle.setTextColor(0xFF38BDF8);
        tvHudTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvHudTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hudCard.addView(tvHudTitle);

        TextView tvHudDesc = new TextView(this);
        tvHudDesc.setText("Pemeriksa pantas KANTOI terapung di atas WhatsApp, Telegram, FB, dan Aplikasi Bank. Ketik 1 kali untuk membuka kad risikan pantas tanpa perlu bertukar aplikasi!");
        tvHudDesc.setTextColor(0xFFE2E8F0);
        tvHudDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvHudDesc.setPadding(0, dpToPx(4), 0, dpToPx(8));
        hudCard.addView(tvHudDesc);

        final TextView tvHudStatus = new TextView(this);
        boolean isHudActive = KantoiFloatingHudService.sIsRunning;
        tvHudStatus.setText("Status Gelembung: " + (isHudActive ? "✅ AKTIF" : "⏹️ DIMATIKAN"));
        tvHudStatus.setTextColor(isHudActive ? 0xFF34D399 : 0xFF94A3B8);
        tvHudStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvHudStatus.setTypeface(Typeface.DEFAULT_BOLD);
        hudCard.addView(tvHudStatus);

        final Button btnToggleHud = createButton(isHudActive ? "⏹️ HENTIKAN GELEMBUNG HUD" : "🚀 AKTIFKAN GELEMBUNG TERAPUNG", isHudActive ? 0xFF475569 : 0xFF0284C7, Color.WHITE);
        setMarginTop(btnToggleHud, 6);
        btnToggleHud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Sila berikan kebenaran 'Paparkan di atas aplikasi lain'", Toast.LENGTH_LONG).show();
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(permIntent);
                    return;
                }

                if (KantoiFloatingHudService.sIsRunning) {
                    stopService(new Intent(MainActivity.this, KantoiFloatingHudService.class));
                    tvHudStatus.setText("Status Gelembung: ⏹️ DIMATIKAN");
                    tvHudStatus.setTextColor(0xFF94A3B8);
                    btnToggleHud.setText("🚀 AKTIFKAN GELEMBUNG TERAPUNG");
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xFF0284C7);
                    bg.setCornerRadius(dpToPx(8));
                    btnToggleHud.setBackground(bg);
                    Toast.makeText(MainActivity.this, "Gelembung KANTOI dihentikan.", Toast.LENGTH_SHORT).show();
                } else {
                    startService(new Intent(MainActivity.this, KantoiFloatingHudService.class));
                    tvHudStatus.setText("Status Gelembung: ✅ AKTIF");
                    tvHudStatus.setTextColor(0xFF34D399);
                    btnToggleHud.setText("⏹️ HENTIKAN GELEMBUNG HUD");
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xFF475569);
                    bg.setCornerRadius(dpToPx(8));
                    btnToggleHud.setBackground(bg);
                    Toast.makeText(MainActivity.this, "Gelembung KANTOI kini terapung di skrin anda!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        hudCard.addView(btnToggleHud);
        root.addView(hudCard);

        // 3. Android Default Phone App (InCallService) & Screening Manager Card
        LinearLayout callMgrCard = createCard(0xFF1E293B, 0xFF0284C7, 12);
        callMgrCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(callMgrCard, 12);

        TextView tvCallMgrTitle = new TextView(this);
        tvCallMgrTitle.setText("👑 PENGURUS APLIKASI TELEFON LALAI (DEFAULT PHONE APP)");
        tvCallMgrTitle.setTextColor(0xFF38BDF8);
        tvCallMgrTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvCallMgrTitle.setTypeface(Typeface.DEFAULT_BOLD);
        callMgrCard.addView(tvCallMgrTitle);

        TextView tvCallMgrDesc = new TextView(this);
        tvCallMgrDesc.setText("Untuk memaparkan antaramuka skrin panggilan masuk (In-Call), skrin tamatkan panggilan, dan popup selepas panggilan secara automatik:");
        tvCallMgrDesc.setTextColor(0xFFCBD5E1);
        tvCallMgrDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvCallMgrDesc.setPadding(0, dpToPx(2), 0, dpToPx(6));
        callMgrCard.addView(tvCallMgrDesc);

        // Status 1: Default Dialer Role
        mTvDefaultDialerStatus = new TextView(this);
        mTvDefaultDialerStatus.setText("Status Telefon Lalai: Memeriksa...");
        mTvDefaultDialerStatus.setTextColor(Color.WHITE);
        mTvDefaultDialerStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvDefaultDialerStatus.setTypeface(Typeface.DEFAULT_BOLD);
        callMgrCard.addView(mTvDefaultDialerStatus);

        mBtnSetDefaultDialer = createButton("👑 TETAPKAN KANTOI SEBAGAI APP TELEFON LALAI", 0xFF0284C7, Color.WHITE);
        setMarginTop(mBtnSetDefaultDialer, 6);
        mBtnSetDefaultDialer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestDefaultDialerRole();
            }
        });
        callMgrCard.addView(mBtnSetDefaultDialer);

        // Status 2: Overlay Permission
        mTvOverlayStatus = new TextView(this);
        mTvOverlayStatus.setText("Status Paparan Atas Skrin: Memeriksa...");
        mTvOverlayStatus.setTextColor(Color.WHITE);
        mTvOverlayStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvOverlayStatus.setTypeface(Typeface.DEFAULT_BOLD);
        setMarginTop(mTvOverlayStatus, 10);
        callMgrCard.addView(mTvOverlayStatus);

        mBtnRequestOverlay = createButton("🪟 AKTIFKAN PAPARAN ATAS SKRIN (OVERLAY)", 0xFF475569, Color.WHITE);
        setMarginTop(mBtnRequestOverlay, 6);
        mBtnRequestOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        callMgrCard.addView(mBtnRequestOverlay);

        // Status 3: Call Screening Service
        mTvScreeningStatus = new TextView(this);
        mTvScreeningStatus.setText("Status Penyaring Panggilan: Memeriksa...");
        mTvScreeningStatus.setTextColor(Color.WHITE);
        mTvScreeningStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvScreeningStatus.setTypeface(Typeface.DEFAULT_BOLD);
        setMarginTop(mTvScreeningStatus, 10);
        callMgrCard.addView(mTvScreeningStatus);

        mBtnEnableScreening = createButton("🛡️ TETAPKAN PENYARING PANGGILAN (CALL SCREENING)", 0xFF334155, Color.WHITE);
        setMarginTop(mBtnEnableScreening, 6);
        mBtnEnableScreening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestCallScreeningRole();
            }
        });
        callMgrCard.addView(mBtnEnableScreening);

        // Toggle Auto-Block vs Alert Only
        boolean autoBlock = prefs.getBoolean("pref_auto_block", true);
        mBtnAutoBlockToggle = createButton(
            autoBlock ? "🛑 MOD: AUTO-SEKAT PANGGILAN SCAMMER" : "⚠️ MOD: DERING & AMARAN MERAH SAHAJA",
            autoBlock ? 0xFFDC2626 : 0xFFD97706,
            Color.WHITE
        );
        setMarginTop(mBtnAutoBlockToggle, 10);
        mBtnAutoBlockToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = prefs.getBoolean("pref_auto_block", true);
                boolean next = !cur;
                prefs.edit().putBoolean("pref_auto_block", next).apply();
                mBtnAutoBlockToggle.setText(next ? "🛑 MOD: AUTO-SEKAT PANGGILAN SCAMMER" : "⚠️ MOD: DERING & AMARAN MERAH SAHAJA");
                updateButtonBg(mBtnAutoBlockToggle, next ? 0xFFDC2626 : 0xFFD97706);
                Toast.makeText(MainActivity.this, next ? "Panggilan scammer akan ditolak secara automatik." : "Telefon akan berdering dengan paparan amaran merah.", Toast.LENGTH_SHORT).show();
            }
        });
        callMgrCard.addView(mBtnAutoBlockToggle);
        root.addView(callMgrCard);

        // 4. Mod Perlindungan Warga Emas (Senior Citizen Mode)
        LinearLayout seniorCard = createCard(0xFF1E293B, 0xFF334155, 12);
        seniorCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(seniorCard, 12);

        TextView tvSeniorTitle = new TextView(this);
        tvSeniorTitle.setText("👴 MOD PERLINDUNGAN WARGA EMAS (STRICT)");
        tvSeniorTitle.setTextColor(0xFFF43F5E);
        tvSeniorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSeniorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        seniorCard.addView(tvSeniorTitle);

        TextView tvSeniorDesc = new TextView(this);
        tvSeniorDesc.setText("Sekat semua nombor tidak dikenali / nombor asing secara automatik. Hanya kenalan dalam buku telefon dibenarkan berdering untuk melindungi ibu bapa.");
        tvSeniorDesc.setTextColor(0xFFE2E8F0);
        tvSeniorDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSeniorDesc.setPadding(0, dpToPx(4), 0, dpToPx(8));
        seniorCard.addView(tvSeniorDesc);

        boolean seniorMode = prefs.getBoolean("pref_senior_mode", false);
        mBtnSeniorModeToggle = createButton(
            seniorMode ? "🛑 MOD WARGA EMAS: AKTIF (SEKAT NOMBOR ASING)" : "⚪ MOD WARGA EMAS: TIDAK AKTIF (STANDARD)",
            seniorMode ? 0xFFBE123C : 0xFF475569,
            Color.WHITE
        );
        mBtnSeniorModeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = prefs.getBoolean("pref_senior_mode", false);
                boolean next = !cur;
                prefs.edit().putBoolean("pref_senior_mode", next).apply();
                mBtnSeniorModeToggle.setText(next ? "🛑 MOD WARGA EMAS: AKTIF (SEKAT NOMBOR ASING)" : "⚪ MOD WARGA EMAS: TIDAK AKTIF (STANDARD)");
                updateButtonBg(mBtnSeniorModeToggle, next ? 0xFFBE123C : 0xFF475569);
                Toast.makeText(MainActivity.this, next ? "Mod Warga Emas Aktif: Hanya kenalan buku telefon dibenarkan berdering." : "Mod Warga Emas Dinyahaktifkan.", Toast.LENGTH_LONG).show();
            }
        });
        seniorCard.addView(mBtnSeniorModeToggle);
        root.addView(seniorCard);

        // 5. Pengesan Akaun Keldai Papan Keratan (Clipboard Mule Sniffer)
        LinearLayout clipCard = createCard(0xFF1E293B, 0xFF334155, 12);
        clipCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(clipCard, 12);

        TextView tvClipTitle = new TextView(this);
        tvClipTitle.setText("📋 PENGESAN PINTAR PAPAN KERATAN (CLIPBOARD)");
        tvClipTitle.setTextColor(0xFFA855F7);
        tvClipTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvClipTitle.setTypeface(Typeface.DEFAULT_BOLD);
        clipCard.addView(tvClipTitle);

        TextView tvClipDesc = new TextView(this);
        tvClipDesc.setText("Mengesan nombor akaun bank atau telefon yang disalin di WhatsApp/FB/Telegram. Memberi amaran merah jika akaun keldai dikesan sebelum anda transfer duit.");
        tvClipDesc.setTextColor(0xFFE2E8F0);
        tvClipDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvClipDesc.setPadding(0, dpToPx(4), 0, dpToPx(8));
        clipCard.addView(tvClipDesc);

        boolean clipSniffer = prefs.getBoolean("pref_clipboard_sniffer", true);
        mBtnClipboardToggle = createButton(
            clipSniffer ? "✅ PENGESAN CLIPBOARD: AKTIF" : "⚪ PENGESAN CLIPBOARD: DIMATIKAN",
            clipSniffer ? 0xFF7E22CE : 0xFF475569,
            Color.WHITE
        );
        mBtnClipboardToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = prefs.getBoolean("pref_clipboard_sniffer", true);
                boolean next = !cur;
                prefs.edit().putBoolean("pref_clipboard_sniffer", next).apply();
                mBtnClipboardToggle.setText(next ? "✅ PENGESAN CLIPBOARD: AKTIF" : "⚪ PENGESAN CLIPBOARD: DIMATIKAN");
                updateButtonBg(mBtnClipboardToggle, next ? 0xFF7E22CE : 0xFF475569);
                if (next) {
                    try { startService(new Intent(MainActivity.this, KantoiClipboardService.class)); } catch (Throwable ignored) {}
                }
                Toast.makeText(MainActivity.this, next ? "Pengesan akaun keldai diaktifkan." : "Pengesan papan keratan dimatikan.", Toast.LENGTH_SHORT).show();
            }
        });
        clipCard.addView(mBtnClipboardToggle);
        root.addView(clipCard);

        // 6. Contact Whitelist & Threat Audit Card
        LinearLayout contactsCard = createCard(0xFF1E293B, 0xFF334155, 12);
        contactsCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(contactsCard, 12);

        TextView tvCtTitle = new TextView(this);
        tvCtTitle.setText("📱 AUDIT BUKU TELEFON (SAFE WHITELIST)");
        tvCtTitle.setTextColor(0xFF38BDF8);
        tvCtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvCtTitle.setTypeface(Typeface.DEFAULT_BOLD);
        contactsCard.addView(tvCtTitle);

        mTvContactAuditStatus = new TextView(this);
        mTvContactAuditStatus.setText("Imbas buku telefon anda untuk mengesan scammer tersimpan & bina senarai putih (Safe Whitelist).");
        mTvContactAuditStatus.setTextColor(0xFFE2E8F0);
        mTvContactAuditStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvContactAuditStatus.setPadding(0, dpToPx(4), 0, dpToPx(8));
        contactsCard.addView(mTvContactAuditStatus);

        mBtnAuditContacts = createButton("🛡️ IMBAS BUKU TELEFON SEKARANG", 0xFF059669, Color.WHITE);
        mBtnAuditContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndStartContactAudit();
            }
        });
        contactsCard.addView(mBtnAuditContacts);
        root.addView(contactsCard);

        // 6b. Radar Ancaman WhatsApp (Disebalik Tabir)
        LinearLayout waCard = createCard(0xFF1E293B, 0xFF25D366, 12);
        waCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(waCard, 12);

        TextView tvWaTitle = new TextView(this);
        tvWaTitle.setText("🟢 RADAR ANCAMAN WHATSAPP (DISEBALIK TABIR)");
        tvWaTitle.setTextColor(0xFF25D366);
        tvWaTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvWaTitle.setTypeface(Typeface.DEFAULT_BOLD);
        waCard.addView(tvWaTitle);

        mTvWhatsAppStatus = new TextView(this);
        mTvWhatsAppStatus.setTextColor(0xFFE2E8F0);
        mTvWhatsAppStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvWhatsAppStatus.setPadding(0, dpToPx(4), 0, dpToPx(8));
        waCard.addView(mTvWhatsAppStatus);

        mBtnScanWhatsApp = createButton("🔍 IMBAS KENALAN WHATSAPP SEKARANG", 0xFF059669, Color.WHITE);
        mBtnScanWhatsApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runManualWhatsAppScan();
            }
        });
        waCard.addView(mBtnScanWhatsApp);

        mBtnViewWhatsAppThreats = createButton("🚨 LIHAT REKOD SCAMMER WHATSAPP", 0xFFDC2626, Color.WHITE);
        setMarginTop(mBtnViewWhatsAppThreats, 8);
        mBtnViewWhatsAppThreats.setVisibility(View.GONE);
        mBtnViewWhatsAppThreats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLastWhatsAppResult != null) {
                    KantoiWhatsAppScanner.showWhatsAppThreatsDialog(MainActivity.this, mLastWhatsAppResult);
                }
            }
        });
        waCard.addView(mBtnViewWhatsAppThreats);

        root.addView(waCard);
        updateWhatsAppCardStatus();

        // 7. SMS Scam & Phishing Shield Card
        LinearLayout smsCard = createCard(0xFF1E293B, 0xFF334155, 12);
        smsCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(smsCard, 12);

        TextView tvSmsTitle = new TextView(this);
        tvSmsTitle.setText("💬 PERISAI SMS SCAM & PHISHING (AKTIF)");
        tvSmsTitle.setTextColor(0xFFF59E0B);
        tvSmsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSmsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        smsCard.addView(tvSmsTitle);

        mTvSmsShieldStatus = new TextView(this);
        mTvSmsShieldStatus.setText("Melindungi anda daripada SMS pancingan data (Phishing), pautan muat turun APK trojan, dan penyamaran bank/LHDN.");
        mTvSmsShieldStatus.setTextColor(0xFFE2E8F0);
        mTvSmsShieldStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTvSmsShieldStatus.setPadding(0, dpToPx(4), 0, dpToPx(8));
        smsCard.addView(mTvSmsShieldStatus);

        mBtnSmsPermission = createButton("🔐 STATUS PERISAI SMS", 0xFF0284C7, Color.WHITE);
        mBtnSmsPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SmsActivity.class));
            }
        });
        smsCard.addView(mBtnSmsPermission);
        root.addView(smsCard);

        // 8. Direct Community Tools
        LinearLayout toolsCard = createCard(0xFF1E293B, 0xFF334155, 12);
        toolsCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(toolsCard, 12);

        TextView tvToolsTitle = new TextView(this);
        tvToolsTitle.setText("⚡ TINDAKAN PANTAS & RISIKAN");
        tvToolsTitle.setTextColor(0xFF94A3B8);
        tvToolsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvToolsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        toolsCard.addView(tvToolsTitle);

        Button btnRegisterFriend = createButton("👥 DAFTAR RAKAN / KENALAN SELAMAT", 0xFF059669, Color.WHITE);
        setMarginTop(btnRegisterFriend, 8);
        btnRegisterFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = mEtSearchPhone != null ? mEtSearchPhone.getText().toString() : "";
                mEngine.showRegisterFriendDialog(MainActivity.this, input);
            }
        });
        toolsCard.addView(btnRegisterFriend);

        Button btnRegisterScammer = createButton("🚨 LAPOR SCAMMER KE KOMUNITI KANTOI", 0xFFDC2626, Color.WHITE);
        setMarginTop(btnRegisterScammer, 8);
        btnRegisterScammer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = mEtSearchPhone != null ? mEtSearchPhone.getText().toString() : "";
                mEngine.showRegisterScammerDialog(MainActivity.this, input);
            }
        });
        toolsCard.addView(btnRegisterScammer);

        Button btnCse = createButton("🌐 CARIAN PERTIKAIAN MEDIA SOSIAL (GOOGLE CSE)", 0xFF1D4ED8, Color.WHITE);
        setMarginTop(btnCse, 8);
        btnCse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = mEtSearchPhone != null ? mEtSearchPhone.getText().toString() : "";
                KantoiThreatEngine.openGoogleCseSearch(MainActivity.this, input);
            }
        });
        toolsCard.addView(btnCse);

        Button btnWaIntel = createButton("📸 SIASAT WHATSAPP & KEBOCORAN (CHECKLEAKED OSINT)", 0xFF059669, Color.WHITE);
        setMarginTop(btnWaIntel, 8);
        btnWaIntel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = mEtSearchPhone != null ? mEtSearchPhone.getText().toString() : "";
                KantoiThreatEngine.showWhatsAppIntelDialog(MainActivity.this, input);
            }
        });
        toolsCard.addView(btnWaIntel);
        root.addView(toolsCard);

        // 9. VirusTotal & APK Heuristic Engine Card
        LinearLayout vtCard = createCard(0xFF0F172A, 0xFF0284C7, 12);
        vtCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(vtCard, 12);

        TextView tvVtTitle = new TextView(this);
        tvVtTitle.setText("🛡️ PENGIMBAS APK & PAUTAN (VIRUSTOTAL + HEURISTIK)");
        tvVtTitle.setTextColor(0xFF38BDF8);
        tvVtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVtTitle.setTypeface(Typeface.DEFAULT_BOLD);
        vtCard.addView(tvVtTitle);

        TextView tvVtDesc = new TextView(this);
        tvVtDesc.setText("Lindungi telefon daripada pancingan data (phishing) dan APK trojan perbankan (MaliBot, Godfather). Mengimbas kebenaran trojan berisiko secara luar talian (0ms) serta risikan global 70+ enjin antivirus.");
        tvVtDesc.setTextColor(0xFFCBD5E1);
        tvVtDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVtDesc.setPadding(0, dpToPx(4), 0, dpToPx(10));
        vtCard.addView(tvVtDesc);

        Button btnPickApk = createButton("📁 IMBAS FAIL APK DARI STORAN", 0xFF0284C7, Color.WHITE);
        btnPickApk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApkPicker();
            }
        });
        vtCard.addView(btnPickApk);

        Button btnScanUrl = createButton("🔗 IMBAS PAUTAN / URL SUSPEK", 0xFF059669, Color.WHITE);
        setMarginTop(btnScanUrl, 8);
        btnScanUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiVirusTotalEngine.showUrlScanDialog(MainActivity.this);
            }
        });
        vtCard.addView(btnScanUrl);

        Button btnVtApiKey = createButton("🔑 TETAPKAN API KEY VIRUSTOTAL", 0xFF475569, Color.WHITE);
        setMarginTop(btnVtApiKey, 8);
        btnVtApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KantoiVirusTotalEngine.showApiKeySettingsDialog(MainActivity.this);
            }
        });
        vtCard.addView(btnVtApiKey);
        root.addView(vtCard);

        // 10. GUI Previews (Now includes Active Call Preview!)
        LinearLayout guiPreviewCard = createCard(0xFF1E293B, 0xFF0284C7, 12);
        guiPreviewCard.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(guiPreviewCard, 12);

        TextView tvGuiTitle = new TextView(this);
        tvGuiTitle.setText("📱 PRATONTON ANTARAMUKA PANGGILAN KANTOI");
        tvGuiTitle.setTextColor(0xFF38BDF8);
        tvGuiTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvGuiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        guiPreviewCard.addView(tvGuiTitle);

        // Preview In-Call
        Button btnPreviewInCall = createButton("📞 PRATONTON SKRIN PANGGILAN MASUK (INCOMING)", 0xFFBE123C, Color.WHITE);
        setMarginTop(btnPreviewInCall, 8);
        btnPreviewInCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent inCall = new Intent(MainActivity.this, IncomingCallActivity.class);
                inCall.putExtra(IncomingCallActivity.EXTRA_PHONE_NUMBER, "0112345678");
                inCall.putExtra(IncomingCallActivity.EXTRA_IS_TEST_DEMO, true);
                startActivity(inCall);
            }
        });
        guiPreviewCard.addView(btnPreviewInCall);

        // Preview Active Call (Ongoing with End Call button)
        Button btnPreviewActiveCall = createButton("⚡ PRATONTON PANGGILAN BERLANGSUNG (ACTIVE CALL)", 0xFFD97706, Color.WHITE);
        setMarginTop(btnPreviewActiveCall, 8);
        btnPreviewActiveCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent actCall = new Intent(MainActivity.this, ActiveCallActivity.class);
                actCall.putExtra(ActiveCallActivity.EXTRA_PHONE_NUMBER, "0112345678");
                startActivity(actCall);
            }
        });
        guiPreviewCard.addView(btnPreviewActiveCall);

        // Preview After-Call
        Button btnPreviewAfterCall = createButton("📋 PRATONTON SKRIN SELEPAS PANGGILAN (AFTER-CALL)", 0xFF0284C7, Color.WHITE);
        setMarginTop(btnPreviewAfterCall, 8);
        btnPreviewAfterCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent afterCall = new Intent(MainActivity.this, AfterCallActivity.class);
                afterCall.putExtra(AfterCallActivity.EXTRA_PHONE_NUMBER, "0112345678");
                startActivity(afterCall);
            }
        });
        guiPreviewCard.addView(btnPreviewAfterCall);

        // Preview Contact Profile
        Button btnPreviewProfile = createButton("👤 PRATONTON PROFIL KENALAN & RISIKAN", 0xFF059669, Color.WHITE);
        setMarginTop(btnPreviewProfile, 8);
        btnPreviewProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pIt = new Intent(MainActivity.this, ContactProfileActivity.class);
                pIt.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, "0112345678");
                startActivity(pIt);
            }
        });
        guiPreviewCard.addView(btnPreviewProfile);
        root.addView(guiPreviewCard);

        // Footer
        TextView tvFooter = new TextView(this);
        tvFooter.setText("Kantoi v" + getAppVersionName() + " • 100% Bebas Iklan • Dilindungi Komuniti Malaysia");
        tvFooter.setTextColor(0xFF64748B);
        tvFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvFooter.setGravity(Gravity.CENTER);
        setMarginTop(tvFooter, 24);
        root.addView(tvFooter);

        Button btnGithub = createButton("🌐 REPOSITORI GITHUB (KAND4/KANTOI)", 0xFF0F172A, 0xFF38BDF8);
        setMarginTop(btnGithub, 8);
        btnGithub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kand4/kantoi"));
                    startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "Ralat: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnGithub);

        scrollView.addView(root);
        return scrollView;
    }

    // ==================== FIXED BOTTOM NAVIGATION BAR ====================
    private View buildBottomNavBar() {
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackgroundColor(0xFF0B0F19); // Stealth Dark
        navBar.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(8));

        // 1. Calls Tab Button
        mNavBtnCalls = createNavButton("📞", "Panggilan");
        mNavBtnCalls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(0);
            }
        });
        navBar.addView(mNavBtnCalls);

        // 2. Contacts / Phonebook Shortcut
        mNavBtnContacts = createNavButton("📖", "Kenalan");
        mNavBtnContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ContactsActivity.class));
            }
        });
        navBar.addView(mNavBtnContacts);

        // 3. Keypad Shortcut
        mNavBtnKeypad = createNavButton("🔢", "Pad Dail");
        mNavBtnKeypad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DialerActivity.class));
            }
        });
        navBar.addView(mNavBtnKeypad);

        // 3. SMS Shortcut
        mNavBtnSms = createNavButton("💬", "Mesej");
        mNavBtnSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SmsActivity.class));
            }
        });
        navBar.addView(mNavBtnSms);

        // 4. Shield & Settings Tab Button
        mNavBtnShield = createNavButton("🛡️", "Pertahanan");
        mNavBtnShield.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(1);
            }
        });
        navBar.addView(mNavBtnShield);

        return navBar;
    }

    private Button createNavButton(String icon, String label) {
        Button btn = new Button(this);
        btn.setText(icon + "\n" + label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTextColor(0xFF94A3B8);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(52), 1.0f);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void updateNavButtonState(Button btn, boolean selected) {
        if (btn == null) return;
        GradientDrawable bg = new GradientDrawable();
        if (selected) {
            bg.setColor(0xFF1E293B);
            bg.setCornerRadius(dpToPx(8));
            bg.setStroke(dpToPx(1), 0xFF0284C7);
            btn.setBackground(bg);
            btn.setTextColor(0xFF38BDF8); // Sky blue
        } else {
            btn.setBackground(null);
            btn.setTextColor(0xFF94A3B8);
        }
    }

    // ==================== SEARCH & AUDIT LOGIC ====================
    private void performSearch() {
        String raw = mEtSearchPhone.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Sila masukkan nombor telefon.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String local = KantoiThreatEngine.normalizeToLocal(raw);
        final KantoiThreatEngine.ScammerThreat threat = mEngine.checkThreat(local);

        mSearchResultCard.removeAllViews();
        mSearchResultCard.setVisibility(View.VISIBLE);

        if (threat != null) {
            GradientDrawable redBg = new GradientDrawable();
            redBg.setColor(0xFF7F1D1D); // Deep Crimson
            redBg.setCornerRadius(dpToPx(8));
            redBg.setStroke(dpToPx(1), 0xFFEF4444);
            mSearchResultCard.setBackground(redBg);
            mSearchResultCard.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            TextView tv = new TextView(this);
            tv.setText("🚨 KANTOI! NOMBOR SCAMMER DISAHKAN\n• Suspek: " + threat.suspectName + "\n• Kategori: " + threat.riskTag + "\n• Bank: " + threat.bankAccounts);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            mSearchResultCard.addView(tv);

            Button btnProfile = createButton("📖 BUKA PROFIL LENGKAP & RISIKAN", 0xFF0284C7, Color.WHITE);
            setMarginTop(btnProfile, 8);
            btnProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent(MainActivity.this, ContactProfileActivity.class);
                    it.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, local);
                    startActivity(it);
                }
            });
            mSearchResultCard.addView(btnProfile);

            Button btnDossier = createButton("🚨 DIALOG ANCAMAN KANTOI", 0xFFDC2626, Color.WHITE);
            setMarginTop(btnDossier, 6);
            btnDossier.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showThreatDialog(MainActivity.this, threat);
                }
            });
            mSearchResultCard.addView(btnDossier);
        } else {
            GradientDrawable yellowBg = new GradientDrawable();
            yellowBg.setColor(0xFF451A03); // Deep amber
            yellowBg.setCornerRadius(dpToPx(8));
            yellowBg.setStroke(dpToPx(1), 0xFFF59E0B);
            mSearchResultCard.setBackground(yellowBg);
            mSearchResultCard.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            TextView tv = new TextView(this);
            tv.setText("ℹ️ Nombor [" + local + "] tiada dalam pangkalan data offline.\nAnda boleh membuat semakan segera di portal SemakMule JSJK PDRM:");
            tv.setTextColor(0xFFFEF3C7);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            mSearchResultCard.addView(tv);

            // 1. SemakMule PDRM Action Button (Diletakkan di posisi paling atas)
            Button btnSemakMule = createButton("🏛️ SEMAK DI SEMAKMULE PDRM (1-KLIK)", 0xFF0284C7, Color.WHITE);
            setMarginTop(btnSemakMule, 8);
            btnSemakMule.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent bIt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://semakmule.rmp.gov.my/"));
                        startActivity(bIt);
                    } catch (Throwable ignored) {}
                }
            });
            mSearchResultCard.addView(btnSemakMule);

            Button btnProfile = createButton("👤 BUKA PROFIL KESELAMATAN NOMBOR", 0xFF334155, Color.WHITE);
            setMarginTop(btnProfile, 6);
            btnProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent(MainActivity.this, ContactProfileActivity.class);
                    it.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, local);
                    startActivity(it);
                }
            });
            mSearchResultCard.addView(btnProfile);

            Button btnSaveFriend = createButton("👥 SIMPAN SEBAGAI RAKAN SELAMAT", 0xFF059669, Color.WHITE);
            setMarginTop(btnSaveFriend, 6);
            btnSaveFriend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterFriendDialog(MainActivity.this, local);
                }
            });
            mSearchResultCard.addView(btnSaveFriend);

            Button btnCheckSemak = createButton("🚨 LAPOR SCAMMER KE KOMUNITI KANTOI", 0xFFDC2626, Color.WHITE);
            setMarginTop(btnCheckSemak, 6);
            btnCheckSemak.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mEngine.showRegisterScammerDialog(MainActivity.this, local);
                }
            });
            mSearchResultCard.addView(btnCheckSemak);

            Button btnCheckCse = createButton("🌐 SEMAK DI GOOGLE CSE", 0xFF1D4ED8, Color.WHITE);
            setMarginTop(btnCheckCse, 6);
            btnCheckCse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    KantoiThreatEngine.openGoogleCseSearch(MainActivity.this, local);
                }
            });
            mSearchResultCard.addView(btnCheckCse);

            Button btnCheckWa = createButton("📸 SIASAT GAMBAR DP WHATSAPP (CHECKLEAKED)", 0xFF059669, Color.WHITE);
            setMarginTop(btnCheckWa, 6);
            btnCheckWa.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    KantoiThreatEngine.showWhatsAppIntelDialog(MainActivity.this, local);
                }
            });
            mSearchResultCard.addView(btnCheckWa);
        }
    }

    private void updateCallSystemStatusUI() {
        if (mTvDefaultDialerStatus == null) return;

        // 1. Default Phone App (ROLE_DIALER / InCallService)
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        String defaultDialer = telecomManager != null ? telecomManager.getDefaultDialerPackage() : null;
        boolean isDefaultDialer = getPackageName().equals(defaultDialer);
        if (isDefaultDialer) {
            mTvDefaultDialerStatus.setText("✅ AKTIF: KANTOI adalah Aplikasi Telefon Lalai");
            mTvDefaultDialerStatus.setTextColor(0xFF34D399); // Emerald
            mBtnSetDefaultDialer.setText("✅ APLIKASI TELEFON LALAI SUDAH AKTIF");
            updateButtonBg(mBtnSetDefaultDialer, 0xFF059669);
        } else {
            mTvDefaultDialerStatus.setText("⚠️ BELUM AKTIF: KANTOI bukan aplikasi telefon lalai (" + (defaultDialer != null ? defaultDialer : "Tiada") + ")");
            mTvDefaultDialerStatus.setTextColor(0xFFFBBF24); // Amber
            mBtnSetDefaultDialer.setText("👑 TETAPKAN KANTOI SEBAGAI APP TELEFON LALAI");
            updateButtonBg(mBtnSetDefaultDialer, 0xFF0284C7);
        }

        // 2. Overlay Permission (Appear on Top / Lockscreen alert)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                mTvOverlayStatus.setText("✅ AKTIF: Kebenaran paparan atas skrin (Overlay) aktif");
                mTvOverlayStatus.setTextColor(0xFF34D399);
                mBtnRequestOverlay.setText("✅ PAPARAN ATAS SKRIN DIBENARKAN");
                updateButtonBg(mBtnRequestOverlay, 0xFF059669);
            } else {
                mTvOverlayStatus.setText("⚠️ BELUM AKTIF: Perlu dibenarkan untuk paparan skrin masuk/tamat");
                mTvOverlayStatus.setTextColor(0xFFFBBF24);
                mBtnRequestOverlay.setText("🪟 AKTIFKAN PAPARAN ATAS SKRIN (OVERLAY)");
                updateButtonBg(mBtnRequestOverlay, 0xFF475569);
            }
        } else {
            mTvOverlayStatus.setText("✅ AKTIF (Dibenarkan secara automatik)");
            mTvOverlayStatus.setTextColor(0xFF34D399);
        }

        // 3. Call Screening Role
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                mTvScreeningStatus.setText("✅ AKTIF: Penyaring panggilan (Call Screening) aktif");
                mTvScreeningStatus.setTextColor(0xFF34D399);
                mBtnEnableScreening.setText("✅ PENYARING PANGGILAN AKTIF");
                updateButtonBg(mBtnEnableScreening, 0xFF059669);
            } else {
                mTvScreeningStatus.setText("⚠️ BELUM AKTIF: Sila aktifkan penyaring panggilan");
                mTvScreeningStatus.setTextColor(0xFFFBBF24);
                mBtnEnableScreening.setText("🛡️ TETAPKAN PENYARING PANGGILAN (CALL SCREENING)");
                updateButtonBg(mBtnEnableScreening, 0xFF334155);
            }
        } else {
            mTvScreeningStatus.setText("✅ AKTIF (Mod Latar Belakang Telefon)");
            mTvScreeningStatus.setTextColor(0xFF34D399);
        }
    }

    private void requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    startActivityForResult(intent, REQUEST_ID_ROLE_DIALER);
                    return;
                } else {
                    Toast.makeText(this, "KANTOI sudah menjadi Aplikasi Telefon Lalai!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivityForResult(intent, REQUEST_ID_ROLE_DIALER);
            } catch (Throwable t) {
                Toast.makeText(this, "Sila tetapkan KANTOI di Settings -> Default Apps -> Phone App", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_ID_OVERLAY);
            } else {
                Toast.makeText(this, "Kebenaran paparan atas skrin telah aktif!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null) {
                if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                        startActivityForResult(intent, REQUEST_ID_ROLE_SCREENING);
                    } else {
                        Toast.makeText(this, "Kantoi sudah menjadi penyaring panggilan aktif!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } else {
            Toast.makeText(this, "Android versi anda menggunakan perkhidmatan latar belakang automatik.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkAndStartContactAudit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_ID_PERMISSIONS);
                return;
            }
        }
        startContactAuditAsync();
    }

    private void startContactAuditAsync() {
        mTvContactAuditStatus.setText("⏳ Sedang mengimbas buku telefon anda...");
        mTvContactAuditStatus.setTextColor(0xFFFBBF24); // Amber
        mBtnAuditContacts.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final KantoiThreatEngine.ContactAuditResult result = mEngine.auditContacts();
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBtnAuditContacts.setEnabled(true);
                        if (result.threatsFound.isEmpty()) {
                            mTvContactAuditStatus.setText("✅ " + result.totalScanned + " Kenalan Selamat (Senarai Putih Aktif). Tiada scammer ditemui.");
                            mTvContactAuditStatus.setTextColor(0xFF34D399); // Emerald
                        } else {
                            mTvContactAuditStatus.setText("🚨 AMARAN: " + result.threatsFound.size() + " Scammer Ditemui daripada " + result.totalScanned + " kenalan!");
                            mTvContactAuditStatus.setTextColor(0xFFEF4444); // Crimson
                        }
                        KantoiThreatEngine.showContactAuditDialog(MainActivity.this, result);
                    }
                });
            }
        }).start();
    }

    private void updateContactAndSmsStatusUI() {
        if (mTvContactAuditStatus == null) return;

        // Contacts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                mTvContactAuditStatus.setText("⚠️ Kebenaran kenalan belum diberikan. Sila tekan butang di bawah untuk aktifkan Senarai Putih.");
                mTvContactAuditStatus.setTextColor(0xFFFBBF24);
                mBtnAuditContacts.setText("🔑 BENARKAN AKSES & IMBAS BUKU TELEFON");
            } else {
                int count = mEngine.getWhitelistedContactsCount();
                if (count > 0) {
                    mTvContactAuditStatus.setText("✅ " + count + " Kenalan Selamat dalam Senarai Putih (Buku telefon dilindungi).");
                    mTvContactAuditStatus.setTextColor(0xFF34D399);
                }
                mBtnAuditContacts.setText("🛡️ IMBAS SEMULA BUKU TELEFON");
            }
        }

        // SMS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                mTvSmsShieldStatus.setText("⚠️ Kebenaran SMS belum diberikan. Sila aktifkan untuk mengesan SMS phishing & pautan APK bahaya.");
                mTvSmsShieldStatus.setTextColor(0xFFFBBF24);
            } else {
                String recent = KantoiThreatEngine.getRecentSmsThreatSummary(this);
                if (recent != null) {
                    mTvSmsShieldStatus.setText("🚨 " + recent);
                    mTvSmsShieldStatus.setTextColor(0xFFEF4444);
                } else {
                    mTvSmsShieldStatus.setText("✅ Perisai SMS aktif. Pautan phishing, APK trojan, dan penyamaran bank disaring secara masa nyata.");
                    mTvSmsShieldStatus.setTextColor(0xFF34D399);
                }
            }
        }
    }

    private void updateWhatsAppCardStatus() {
        if (mTvWhatsAppStatus == null) return;
        mLastWhatsAppResult = KantoiWhatsAppScanner.getCachedResult(this);
        if (mLastWhatsAppResult.scanTimestamp == 0) {
            mTvWhatsAppStatus.setText("Imbas senyap semua kenalan WhatsApp anda untuk mengesan nombor scammer & akaun keldai tersimpan.");
            mTvWhatsAppStatus.setTextColor(0xFFE2E8F0);
            if (mBtnViewWhatsAppThreats != null) mBtnViewWhatsAppThreats.setVisibility(View.GONE);
        } else if (mLastWhatsAppResult.scammerMatchesCount > 0) {
            mTvWhatsAppStatus.setText("🚨 AMARAN: " + mLastWhatsAppResult.scammerMatchesCount + " nombor scammer dikesan daripada " + mLastWhatsAppResult.totalWhatsAppContacts + " kenalan WhatsApp yang diimbas!");
            mTvWhatsAppStatus.setTextColor(0xFFF87171);
            if (mBtnViewWhatsAppThreats != null) mBtnViewWhatsAppThreats.setVisibility(View.VISIBLE);
        } else {
            mTvWhatsAppStatus.setText("✅ BERSIH: Semua " + mLastWhatsAppResult.totalWhatsAppContacts + " kenalan WhatsApp anda selamat (Tiada rekod scammer).");
            mTvWhatsAppStatus.setTextColor(0xFF34D399);
            if (mBtnViewWhatsAppThreats != null) mBtnViewWhatsAppThreats.setVisibility(View.GONE);
        }
    }

    private void runManualWhatsAppScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_ID_PERMISSIONS);
                return;
            }
        }

        if (mBtnScanWhatsApp != null) {
            mBtnScanWhatsApp.setEnabled(false);
            mBtnScanWhatsApp.setText("⏳ Mengimbas kenalan WhatsApp...");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final KantoiWhatsAppScanner.WhatsAppScanResult res = KantoiWhatsAppScanner.scanWhatsAppContacts(MainActivity.this);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mLastWhatsAppResult = res;
                        if (mBtnScanWhatsApp != null) {
                            mBtnScanWhatsApp.setEnabled(true);
                            mBtnScanWhatsApp.setText("🔍 IMBAS KENALAN WHATSAPP SEKARANG");
                        }
                        updateWhatsAppCardStatus();
                        KantoiWhatsAppScanner.showWhatsAppThreatsDialog(MainActivity.this, res);
                    }
                });
            }
        }).start();
    }

    private void runSilentWhatsAppScan() {
        if (checkCallingOrSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final KantoiWhatsAppScanner.WhatsAppScanResult res = KantoiWhatsAppScanner.scanWhatsAppContacts(MainActivity.this);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mLastWhatsAppResult = res;
                            updateWhatsAppCardStatus();
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ID_PERMISSIONS) {
            updateContactAndSmsStatusUI();
            boolean contactsGranted = (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED);
            if (contactsGranted) {
                if (mEngine.getWhitelistedContactsCount() == 0) {
                    startContactAuditAsync();
                }
                runSilentWhatsAppScan();
            }
        } else if (requestCode == REQUEST_ID_CALL_LOG_PERM) {
            loadCallLogsAsync();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        updateCallSystemStatusUI();
        if (requestCode == REQUEST_ID_ROLE_DIALER) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "🎉 Tahniah! KANTOI kini Aplikasi Telefon Lalai anda.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_ID_ROLE_SCREENING) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "🛡️ Kantoi kini aktif menyaring panggilan scammer.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_ID_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "🪟 Paparan atas skrin diaktifkan!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_PICK_APK) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                final Uri apkUri = data.getData();
                final ProgressDialog progress = new ProgressDialog(this);
                progress.setMessage("Sedang menganalisis heuristik fail APK...");
                progress.setCancelable(false);
                progress.show();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final File tempApk = KantoiVirusTotalEngine.copyUriToTempFile(MainActivity.this, apkUri);
                        if (tempApk == null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progress.dismiss();
                                    Toast.makeText(MainActivity.this, "Gagal membaca fail APK yang dipilih.", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }

                        final KantoiVirusTotalEngine.ApkScanReport report = KantoiVirusTotalEngine.analyzeApkFile(MainActivity.this, tempApk);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progress.setMessage("Memeriksa 70+ enjin antivirus VirusTotal...");
                            }
                        });

                        KantoiVirusTotalEngine.queryVirusTotalAsync(MainActivity.this, report, new KantoiVirusTotalEngine.VtReportCallback() {
                            @Override
                            public void onResult(final KantoiVirusTotalEngine.ApkScanReport finalReport) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progress.dismiss();
                                        KantoiVirusTotalEngine.showApkScanReportDialog(MainActivity.this, finalReport);
                                    }
                                });
                            }
                        });
                    }
                }).start();
            }
        }
    }

    public void launchApkPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            String[] mimeTypes = {"application/vnd.android.package-archive", "application/octet-stream"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Pilih fail APK untuk diimbas"), REQUEST_CODE_PICK_APK);
        } catch (Throwable t) {
            Toast.makeText(this, "Ralat membuka pengurus fail: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static class CallLogItem {
        public long id;
        public String number;
        public String name;
        public int type;
        public long date;
        public long duration;
        public boolean isScammer;
        public String scammerDetails;
        public boolean isSafeContact;
    }

    private LinearLayout createCard(int bgColor, int strokeColor, int cornerDp) {
        return createCard(bgColor, strokeColor, cornerDp, LinearLayout.VERTICAL);
    }

    private LinearLayout createCard(int bgColor, int strokeColor, int cornerDp, int orientation) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(orientation);
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
        updateButtonBg(btn, bgColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44));
        btn.setLayoutParams(lp);
        return btn;
    }

    private void updateButtonBg(Button btn, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dpToPx(8));
        btn.setBackground(bg);
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

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.11.2";
        }
    }
}
