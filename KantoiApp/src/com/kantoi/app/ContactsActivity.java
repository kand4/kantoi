package com.kantoi.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Vibrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Buku Telefon Pintar KANTOI (Tactical Phonebook UI)
 * Berprestasi Tinggi & Pengecaman Nama Pemilik Sebenar:
 * 1. Pengecaman Nama Sebenar (True Human Name Resolution): Menyelesaikan nama manusia daripada
 *    ContactsContract.Contacts agregat & StructuredName, bukan nombor WhatsApp/mentah '+60...'.
 * 2. Susunan Hierarki Jelas: Nama Pemilik di atas (Teks Tebal Putih), Nombor Telefon di bawah (Kelabu Lembut).
 * 3. Avatar Huruf Bersih: Huruf abjad pertama nama pemilik (e.g. 'A', 'M', 'Z') atau '👤', tiada simbol '+'.
 * 4. Paparan Serta-Merta (0ms Latency) dengan cache statik proses dan tatalan lancar 60/120 FPS (ListView + ViewHolder).
 */
public class ContactsActivity extends Activity {
    private static final int PERMISSION_REQ_CONTACTS = 4001;

    public static class ContactModel {
        public long id;
        public String name;
        public String number;
        public String cleanNumber;
        public String typeLabel;
        public boolean hasRealName;
        public boolean isSafe;
        public boolean isScammer;
        public String scammerTag;
        public long lastCallDate = 0;
        public int callFrequency = 0;

        // Pre-computed cache fields to guarantee 0ms filtering and 60/120fps scrolling
        public String cleanDigits;
        public String lowerName;
        public String formattedNumber;
        public String initial;
        public int avatarColor;

        public ContactModel(long id, String name, String number, String typeLabel, boolean hasRealName) {
            this.id = id;
            this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : "Kenalan Tanpa Nama";
            this.number = (number != null) ? number.trim() : "";
            this.cleanNumber = KantoiThreatEngine.normalizeToLocal(this.number);
            this.typeLabel = typeLabel;
            this.hasRealName = hasRealName;
            updatePrecomputed();
        }

        public void updatePrecomputed() {
            this.cleanDigits = this.number != null ? this.number.replaceAll("[^0-9]", "") : "";
            this.lowerName = this.name != null ? this.name.toLowerCase(Locale.ROOT) : "";
            this.formattedNumber = KantoiThreatEngine.formatPhoneNumber(this.number);
            this.initial = getAvatarInitial(this.name, this.hasRealName);
            this.avatarColor = getAvatarColor(this.hasRealName ? this.name : this.number);
        }
    }

    // Global in-memory static cache to eliminate repeated scanning & provide 0ms instant display
    private static final List<ContactModel> sCachedContacts = new ArrayList<>();
    private static long sLastCacheTime = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 min TTL
    private static volatile boolean sIsLoading = false;

    private KantoiThreatEngine mThreatEngine;
    private final List<ContactModel> mAllContacts = new ArrayList<>();
    private final List<ContactModel> mFilteredContacts = new ArrayList<>();

    private ListView mListView;
    private ContactsAdapter mAdapter;
    private TextView mTvCountBadge;
    private TextView mTvEmptyState;
    private EditText mEtSearch;

    private int mCurrentFilterMode = 0; // 0 = Nama A-Z, 1 = Panggilan Terkini, 2 = Kerap Dihubungi, 3 = Amaran Scammer
    private Button mBtnFilterName;
    private Button mBtnFilterRecent;
    private Button mBtnFilterFreq;
    private Button mBtnFilterThreat;
    // Multi-Selection & Contact Deletion Mode
    private boolean mContactSelectionMode = false;
    private final Set<Long> mSelectedContactIds = new HashSet<>();

    // Cached drawables for 60/120fps scrolling
    private Drawable mCardNormalBg = null;
    private Drawable mCardSelectedBg = null;
    private final Map<Integer, Drawable> mAvatarBgCache = new HashMap<>();
    private LinearLayout mNormalHeaderBar;
    private LinearLayout mSelectionHeaderBar;
    private TextView mTvSelectedCount;
    private Button mBtnSelectAll;
    private Button mBtnDeleteSelected;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mThreatEngine = KantoiThreatEngine.getInstance(this);

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F172A); // Slate 900

        // 1. Top Header Bar
        root.addView(buildTopHeaderBar());

        // 2. Search Box
        root.addView(buildSearchBar());

        // 3. Filter Chips (Horizontal Scrollable)
        root.addView(buildFilterChipsBar());

        // 4. ListView with Recycled BaseAdapter
        FrameLayout listWrapper = new FrameLayout(this);
        listWrapper.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        ));

        mListView = new ListView(this);
        mListView.setDivider(null);
        mListView.setDividerHeight(dpToPx(8));
        mListView.setSelector(android.R.color.transparent);
        mListView.setFastScrollEnabled(false); // Nyahaktif fast-scroll agar tiada konflik pintasan sentuhan (touch interception) pada bucu kanan
        mListView.setVerticalScrollBarEnabled(true);
        mListView.setScrollbarFadingEnabled(true);
        mListView.setScrollingCacheEnabled(true);
        mListView.setClipToPadding(false);
        mListView.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(75)); // 75dp padding bottom for floating dock!

        // Empty state label
        mTvEmptyState = new TextView(this);
        mTvEmptyState.setTextColor(0xFF94A3B8); // Slate 400
        mTvEmptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mTvEmptyState.setGravity(Gravity.CENTER);
        mTvEmptyState.setPadding(dpToPx(24), dpToPx(48), dpToPx(24), dpToPx(48));
        mTvEmptyState.setText("Sedang memuatkan buku telefon...");

        listWrapper.addView(mListView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        listWrapper.addView(mTvEmptyState, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ));

        mListView.setEmptyView(mTvEmptyState);
        root.addView(listWrapper);

        mAdapter = new ContactsAdapter();
        mListView.setAdapter(mAdapter);

        // 5. Persistent Floating Bottom Navigation Bar
        View bottomBar = KantoiBottomBar.buildBottomNavBar(this, KantoiBottomBar.TAB_CONTACTS);
        root.addView(bottomBar);

        setContentView(root);

        checkAndLoadContacts(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        KantoiThreatEngine.ensureFloatingPill(this);
        if (mAllContacts.isEmpty()) {
            checkAndLoadContacts(false);
        }
    }

    // ==================== STATIC PRELOAD METHOD ====================

    public static void preloadContactsCache(final Context context) {
        if (context == null) return;
        if (sIsLoading) return;
        synchronized (sCachedContacts) {
            if (!sCachedContacts.isEmpty() && (System.currentTimeMillis() - sLastCacheTime < CACHE_TTL_MS)) {
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkCallingOrSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                loadContactsFromProvider(context.getApplicationContext());
            }
        }).start();
    }

    // ==================== UI BUILDERS ====================

    private View buildTopHeaderBar() {
        FrameLayout headerWrapper = new FrameLayout(this);
        headerWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Normal Header Bar
        mNormalHeaderBar = new LinearLayout(this);
        mNormalHeaderBar.setOrientation(LinearLayout.HORIZONTAL);
        mNormalHeaderBar.setGravity(Gravity.CENTER_VERTICAL);
        mNormalHeaderBar.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(12));
        mNormalHeaderBar.setBackgroundColor(0xFF1E293B); // Slate 800
        mNormalHeaderBar.setVisibility(mContactSelectionMode ? View.GONE : View.VISIBLE);

        // Back button
        Button btnBack = new Button(this);
        btnBack.setText("←");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        btnBack.setTypeface(Typeface.DEFAULT_BOLD);
        btnBack.setBackground(createRipplePill(0xFF334155, 0xFF475569, dpToPx(8)));
        LinearLayout.LayoutParams lpBack = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        lpBack.setMarginEnd(dpToPx(12));
        btnBack.setLayoutParams(lpBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mNormalHeaderBar.addView(btnBack);

        // Title and count
        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📖 Buku Telefon");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);

        mTvCountBadge = new TextView(this);
        mTvCountBadge.setText("Memuatkan kenalan...");
        mTvCountBadge.setTextColor(0xFF38BDF8); // Sky 400
        mTvCountBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleCol.addView(mTvCountBadge);

        mNormalHeaderBar.addView(titleCol);

        // Refresh Button (🔄)
        Button btnRefresh = new Button(this);
        btnRefresh.setText("🔄");
        btnRefresh.setTextColor(Color.WHITE);
        btnRefresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnRefresh.setBackground(createRipplePill(0xFF334155, 0xFF475569, dpToPx(20)));
        LinearLayout.LayoutParams lpRefresh = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
        lpRefresh.setMarginEnd(dpToPx(8));
        btnRefresh.setLayoutParams(lpRefresh);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ContactsActivity.this, "Menyegarkan buku telefon...", Toast.LENGTH_SHORT).show();
                checkAndLoadContacts(true);
            }
        });
        mNormalHeaderBar.addView(btnRefresh);

        // Add Contact (+) Button
        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Tambah");
        btnAdd.setTextColor(0xFF0F172A);
        btnAdd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnAdd.setTypeface(Typeface.DEFAULT_BOLD);
        btnAdd.setBackground(createRipplePill(0xFF38BDF8, 0xFF0284C7, dpToPx(20)));
        btnAdd.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                    startActivity(intent);
                } catch (Throwable t) {
                    Toast.makeText(ContactsActivity.this, "Gagal membuka pengurus kenalan sistem", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mNormalHeaderBar.addView(btnAdd);

        headerWrapper.addView(mNormalHeaderBar);

        // 2. Selection Mode Header Bar
        mSelectionHeaderBar = new LinearLayout(this);
        mSelectionHeaderBar.setOrientation(LinearLayout.HORIZONTAL);
        mSelectionHeaderBar.setGravity(Gravity.CENTER_VERTICAL);
        mSelectionHeaderBar.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        mSelectionHeaderBar.setBackgroundColor(0xFF1E293B);
        mSelectionHeaderBar.setVisibility(mContactSelectionMode ? View.VISIBLE : View.GONE);

        mBtnSelectAll = new Button(this);
        mBtnSelectAll.setText("✓ SEMUA");
        mBtnSelectAll.setTextColor(0xFF34D399); // Emerald 400
        mBtnSelectAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mBtnSelectAll.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnSelectAll.setBackground(createRipplePill(0xFF0F172A, 0xFF334155, dpToPx(8)));
        mBtnSelectAll.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        mBtnSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleContactSelectAll();
            }
        });
        mSelectionHeaderBar.addView(mBtnSelectAll);

        mTvSelectedCount = new TextView(this);
        mTvSelectedCount.setText("0 Dipilih");
        mTvSelectedCount.setTextColor(0xFFF8FAFC);
        mTvSelectedCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mTvSelectedCount.setTypeface(Typeface.DEFAULT_BOLD);
        mTvSelectedCount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        mTvSelectedCount.setLayoutParams(scLp);
        mSelectionHeaderBar.addView(mTvSelectedCount);

        mBtnDeleteSelected = new Button(this);
        mBtnDeleteSelected.setText("🗑️ PADAM");
        mBtnDeleteSelected.setTextColor(Color.WHITE);
        mBtnDeleteSelected.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mBtnDeleteSelected.setTypeface(Typeface.DEFAULT_BOLD);
        mBtnDeleteSelected.setBackground(createRipplePill(0xFFDC2626, 0xFFEF4444, dpToPx(8)));
        mBtnDeleteSelected.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        mBtnDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteSelectedContacts();
            }
        });
        mSelectionHeaderBar.addView(mBtnDeleteSelected);

        Button btnCancel = new Button(this);
        btnCancel.setText("✕ BATAL");
        btnCancel.setTextColor(0xFF94A3B8);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnCancel.setTypeface(Typeface.DEFAULT_BOLD);
        btnCancel.setBackground(createRipplePill(0xFF0F172A, 0xFF334155, dpToPx(8)));
        btnCancel.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        canLp.setMarginStart(dpToPx(8));
        btnCancel.setLayoutParams(canLp);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitContactSelectionMode();
            }
        });
        mSelectionHeaderBar.addView(btnCancel);

        headerWrapper.addView(mSelectionHeaderBar);

        return headerWrapper;
    }

    private View buildSearchBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(6));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, 1, dpToPx(10)));
        box.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(46)));

        TextView tvIcon = new TextView(this);
        tvIcon.setText("🔍");
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.addView(tvIcon);

        mEtSearch = new EditText(this);
        mEtSearch.setHint("Cari nama kenalan atau nombor telefon...");
        mEtSearch.setHintTextColor(0xFF64748B);
        mEtSearch.setTextColor(Color.WHITE);
        mEtSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mEtSearch.setBackground(null);
        mEtSearch.setSingleLine(true);
        LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lpEt.setMarginStart(dpToPx(8));
        mEtSearch.setLayoutParams(lpEt);

        mEtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilterAndSearch();
            }
        });
        box.addView(mEtSearch);

        // Clear button
        final TextView tvClear = new TextView(this);
        tvClear.setText("✕");
        tvClear.setTextColor(0xFF94A3B8);
        tvClear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvClear.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEtSearch.setText("");
            }
        });
        box.addView(tvClear);

        wrapper.addView(box);
        return wrapper;
    }

    private View buildFilterChipsBar() {
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(8));

        mBtnFilterName = createFilterChip("🔤 Nama (A-Z)", 0);
        mBtnFilterRecent = createFilterChip("⏱️ Panggilan Terkini", 1);
        mBtnFilterFreq = createFilterChip("🔥 Kerap Dihubungi", 2);
        mBtnFilterThreat = createFilterChip("🚨 Amaran Scammer", 3);

        bar.addView(mBtnFilterName);
        bar.addView(mBtnFilterRecent);
        bar.addView(mBtnFilterFreq);
        bar.addView(mBtnFilterThreat);

        hsv.addView(bar);
        updateFilterChipStyles();
        return hsv;
    }

    private Button createFilterChip(String label, final int filterIndex) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(36));
        lp.setMarginEnd(dpToPx(8));
        btn.setLayoutParams(lp);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentFilterMode = filterIndex;
                updateFilterChipStyles();
                applyFilterAndSearch();
            }
        });
        return btn;
    }

    private void updateFilterChipStyles() {
        if (mBtnFilterName == null) return;
        mBtnFilterName.setBackground(createPillDrawable(mCurrentFilterMode == 0 ? 0xFF0284C7 : 0xFF1E293B, mCurrentFilterMode == 0 ? 0xFF38BDF8 : 0xFF334155, 1, dpToPx(16)));
        mBtnFilterName.setTextColor(mCurrentFilterMode == 0 ? Color.WHITE : 0xFF94A3B8);

        mBtnFilterRecent.setBackground(createPillDrawable(mCurrentFilterMode == 1 ? 0xFF059669 : 0xFF1E293B, mCurrentFilterMode == 1 ? 0xFF34D399 : 0xFF334155, 1, dpToPx(16)));
        mBtnFilterRecent.setTextColor(mCurrentFilterMode == 1 ? Color.WHITE : 0xFF94A3B8);

        mBtnFilterFreq.setBackground(createPillDrawable(mCurrentFilterMode == 2 ? 0xFFD97706 : 0xFF1E293B, mCurrentFilterMode == 2 ? 0xFFFBBF24 : 0xFF334155, 1, dpToPx(16)));
        mBtnFilterFreq.setTextColor(mCurrentFilterMode == 2 ? Color.WHITE : 0xFF94A3B8);

        mBtnFilterThreat.setBackground(createPillDrawable(mCurrentFilterMode == 3 ? 0xFFDC2626 : 0xFF1E293B, mCurrentFilterMode == 3 ? 0xFFF87171 : 0xFF334155, 1, dpToPx(16)));
        mBtnFilterThreat.setTextColor(mCurrentFilterMode == 3 ? Color.WHITE : 0xFF94A3B8);
    }

    // ==================== ASYNC CONTACTS LOADER ====================

    private void checkAndLoadContacts(final boolean forceRefresh) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_REQ_CONTACTS);
                return;
            }
        }

        // Fast In-Memory Cache Check: INSTANT 0ms DISPLAY
        synchronized (sCachedContacts) {
            if (!forceRefresh && !sCachedContacts.isEmpty()) {
                mAllContacts.clear();
                mAllContacts.addAll(sCachedContacts);
                mTvCountBadge.setText(mAllContacts.size() + " Kenalan Disimpan");
                applyFilterAndSearch();

                // If cache is fresh, return immediately without scanning
                if (System.currentTimeMillis() - sLastCacheTime < CACHE_TTL_MS) {
                    return;
                }
                // If cache is stale, reload silently in background
                loadContactsAsync(true, false);
                return;
            }
        }

        loadContactsAsync(forceRefresh, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkAndLoadContacts(true);
            } else {
                mTvEmptyState.setText("Kebenaran membaca buku telefon tidak diberikan.\nSila benarkan kebenaran Kenalan untuk menggunakan fungsi ini.");
                mTvCountBadge.setText("Kebenaran Ditolak");
            }
        }
    }

    private void loadContactsAsync(final boolean forceRefresh, final boolean showLoading) {
        if (showLoading && mAllContacts.isEmpty()) {
            mTvEmptyState.setText("Sedang memuatkan buku telefon...");
            mTvEmptyState.setVisibility(View.VISIBLE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ContactModel> loaded = loadContactsFromProvider(getApplicationContext());

                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        mAllContacts.clear();
                        mAllContacts.addAll(loaded);
                        mTvCountBadge.setText(mAllContacts.size() + " Kenalan Disimpan");
                        applyFilterAndSearch();
                    }
                });
            }
        }).start();
    }

    /**
     * Highly optimized contacts query that resolves TRUE HUMAN NAMES.
     * Prevents raw phone numbers ('+60 10-...') from masquerading as contact names.
     */
    private static List<ContactModel> loadContactsFromProvider(Context context) {
        if (context == null) return new ArrayList<>();
        sIsLoading = true;
        Map<String, ContactModel> contactMap = new LinkedHashMap<>();
        KantoiThreatEngine engine = KantoiThreatEngine.getInstance(context);

        // 1. Pre-fetch Master Aggregated Contact Names from ContactsContract.Contacts
        Map<Long, String> masterContactNames = new HashMap<>();
        Cursor cContacts = null;
        try {
            Uri cUri = ContactsContract.Contacts.CONTENT_URI;
            String[] cProj = new String[]{
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.DISPLAY_NAME
            };
            cContacts = context.getContentResolver().query(cUri, cProj, ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0", null, null);
            if (cContacts != null) {
                int idIdx = cContacts.getColumnIndex(ContactsContract.Contacts._ID);
                int pIdx = cContacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                int dIdx = cContacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                while (cContacts.moveToNext()) {
                    long cId = (idIdx >= 0) ? cContacts.getLong(idIdx) : 0;
                    String cName = (pIdx >= 0) ? cContacts.getString(pIdx) : null;
                    if ((cName == null || cName.trim().isEmpty()) && dIdx >= 0) {
                        cName = cContacts.getString(dIdx);
                    }
                    if (cId > 0 && KantoiThreatEngine.isRealContactName(cName, null)) {
                        masterContactNames.put(cId, cName.trim());
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cContacts != null) cContacts.close();
        }

        // 2. Supplement with StructuredName (Given Name + Family Name)
        Cursor cStruct = null;
        try {
            Uri dataUri = ContactsContract.Data.CONTENT_URI;
            String[] sProj = new String[]{
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
            };
            String sSel = ContactsContract.Data.MIMETYPE + " = ?";
            String[] sArgs = new String[]{ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};
            cStruct = context.getContentResolver().query(dataUri, sProj, sSel, sArgs, null);
            if (cStruct != null) {
                int idIdx = cStruct.getColumnIndex(ContactsContract.Data.CONTACT_ID);
                int dIdx = cStruct.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
                int gIdx = cStruct.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
                int fIdx = cStruct.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
                while (cStruct.moveToNext()) {
                    long cId = (idIdx >= 0) ? cStruct.getLong(idIdx) : 0;
                    if (cId <= 0) continue;
                    if (masterContactNames.containsKey(cId) && KantoiThreatEngine.isRealContactName(masterContactNames.get(cId), null)) {
                        continue;
                    }
                    String dName = (dIdx >= 0) ? cStruct.getString(dIdx) : null;
                    if (KantoiThreatEngine.isRealContactName(dName, null)) {
                        masterContactNames.put(cId, dName.trim());
                        continue;
                    }
                    String given = (gIdx >= 0) ? cStruct.getString(gIdx) : null;
                    String fam = (fIdx >= 0) ? cStruct.getString(fIdx) : null;
                    StringBuilder sb = new StringBuilder();
                    if (given != null && !given.trim().isEmpty()) sb.append(given.trim());
                    if (fam != null && !fam.trim().isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(fam.trim());
                    }
                    String fullName = sb.toString().trim();
                    if (KantoiThreatEngine.isRealContactName(fullName, null)) {
                        masterContactNames.put(cId, fullName);
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cStruct != null) cStruct.close();
        }

        // 3. Query Phone Numbers & Resolve Real Names
        Cursor cursor = null;
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            };

            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                int pNameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY);
                int dNameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);

                do {
                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : 0;
                    String number = numIdx >= 0 ? cursor.getString(numIdx) : "";
                    if (TextUtils.isEmpty(number)) continue;

                    String clean = KantoiThreatEngine.normalizeToLocal(number);
                    if (TextUtils.isEmpty(clean)) continue;

                    // Resolve the true human name
                    String resolvedName = null;
                    if (id > 0 && masterContactNames.containsKey(id)) {
                        resolvedName = masterContactNames.get(id);
                    }
                    if (!KantoiThreatEngine.isRealContactName(resolvedName, number)) {
                        String pName = (pNameIdx >= 0) ? cursor.getString(pNameIdx) : null;
                        if (KantoiThreatEngine.isRealContactName(pName, number)) {
                            resolvedName = pName.trim();
                        } else {
                            String dName = (dNameIdx >= 0) ? cursor.getString(dNameIdx) : null;
                            if (KantoiThreatEngine.isRealContactName(dName, number)) {
                                resolvedName = dName.trim();
                            }
                        }
                    }
                    if (!KantoiThreatEngine.isRealContactName(resolvedName, number) && engine != null) {
                        String engName = engine.getContactDisplayName(number);
                        if (KantoiThreatEngine.isRealContactName(engName, number)) {
                            resolvedName = engName.trim();
                        } else {
                            engName = engine.getContactDisplayName(clean);
                            if (KantoiThreatEngine.isRealContactName(engName, number)) {
                                resolvedName = engName.trim();
                            }
                        }
                    }

                    boolean hasRealName = KantoiThreatEngine.isRealContactName(resolvedName, number);
                    if (!hasRealName) {
                        resolvedName = "Kenalan Tanpa Nama";
                    }

                    // Cache verified real names into threat engine
                    if (engine != null && hasRealName) {
                        engine.cacheContactName(number, resolvedName);
                        engine.cacheContactName(clean, resolvedName);
                    }

                    int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                    String typeLabel = "Mudah Alih";
                    if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) typeLabel = "Rumah";
                    else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) typeLabel = "Pejabat";

                    ContactModel model = new ContactModel(id, resolvedName, number, typeLabel, hasRealName);

                    // O(1) in-memory threat lookup
                    if (engine != null) {
                        KantoiThreatEngine.ScammerThreat threat = engine.checkThreat(clean);
                        if (threat != null) {
                            model.isScammer = true;
                            model.scammerTag = threat.riskTag;
                            model.isSafe = false;
                        } else {
                            model.isSafe = true; // Contacts saved on device are inherently safe unless flagged
                        }
                    }

                    // Smart Deduplication: Keep or upgrade to real name
                    ContactModel existing = contactMap.get(clean);
                    if (existing == null) {
                        contactMap.put(clean, model);
                    } else {
                        if (!existing.hasRealName && model.hasRealName) {
                            existing.name = model.name;
                            existing.hasRealName = true;
                            existing.id = model.id;
                            existing.updatePrecomputed();
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            sIsLoading = false;
        }

        // 4. Single-pass CallLog enrichment (last call date & call frequency)
        try {
            if (context.checkCallingOrSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                Cursor cLog = context.getContentResolver().query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    new String[]{android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE},
                    null, null,
                    android.provider.CallLog.Calls.DATE + " DESC LIMIT 1000"
                );
                if (cLog != null) {
                    int cNumIdx = cLog.getColumnIndex(android.provider.CallLog.Calls.NUMBER);
                    int cDateIdx = cLog.getColumnIndex(android.provider.CallLog.Calls.DATE);
                    while (cLog.moveToNext()) {
                        String num = (cNumIdx >= 0) ? cLog.getString(cNumIdx) : null;
                        long date = (cDateIdx >= 0) ? cLog.getLong(cDateIdx) : 0;
                        if (num != null && !num.isEmpty()) {
                            String cl = KantoiThreatEngine.normalizeToLocal(num);
                            ContactModel cm = contactMap.get(cl);
                            if (cm != null) {
                                if (cm.lastCallDate == 0) {
                                    cm.lastCallDate = date;
                                }
                                cm.callFrequency++;
                            }
                        }
                    }
                    cLog.close();
                }
            }
        } catch (Throwable ignored) {}

        List<ContactModel> list = new ArrayList<>(contactMap.values());

        // Sort: Named contacts first A-Z, unnamed contacts at the end
        Collections.sort(list, new Comparator<ContactModel>() {
            @Override
            public int compare(ContactModel o1, ContactModel o2) {
                if (o1.hasRealName && !o2.hasRealName) return -1;
                if (!o1.hasRealName && o2.hasRealName) return 1;
                return o1.name.compareToIgnoreCase(o2.name);
            }
        });

        synchronized (sCachedContacts) {
            sCachedContacts.clear();
            sCachedContacts.addAll(list);
            sLastCacheTime = System.currentTimeMillis();
        }

        return list;
    }

    // ==================== PENAPISAN & ADAPTER ====================

    private void applyFilterAndSearch() {
        String query = (mEtSearch != null) ? mEtSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        String cleanQueryDigits = query.replaceAll("[^0-9]", "");

        mFilteredContacts.clear();
        for (ContactModel c : mAllContacts) {
            // Filter mode 3: Amaran Scammer sahaja
            if (mCurrentFilterMode == 3 && !c.isScammer) {
                continue;
            }

            // Tapis mengikut teks carian (0ms precomputed match, zero regex compilation)
            if (!TextUtils.isEmpty(query)) {
                boolean matchName = c.lowerName != null && c.lowerName.contains(query);
                boolean matchPhone = (!cleanQueryDigits.isEmpty() && c.cleanDigits != null && c.cleanDigits.contains(cleanQueryDigits));
                if (!matchName && !matchPhone) {
                    continue;
                }
            }

            mFilteredContacts.add(c);
        }

        // Susun mengikut mod penapis yang dipilih:
        // Mod 0 (Nama A-Z): mAllContacts sudah sedia tersusun A-Z, jadi tak perlu re-sort pada setiap ketikan!
        if (mCurrentFilterMode == 1) {
            // 1: Panggilan Terkini (Tarikh panggilan terbaru di atas)
            Collections.sort(mFilteredContacts, new Comparator<ContactModel>() {
                @Override
                public int compare(ContactModel o1, ContactModel o2) {
                    if (o1.lastCallDate != o2.lastCallDate) {
                        return Long.compare(o2.lastCallDate, o1.lastCallDate);
                    }
                    return o1.name.compareToIgnoreCase(o2.name);
                }
            });
        } else if (mCurrentFilterMode == 2) {
            // 2: Kerap Dihubungi (Kekerapan panggilan tertinggi di atas)
            Collections.sort(mFilteredContacts, new Comparator<ContactModel>() {
                @Override
                public int compare(ContactModel o1, ContactModel o2) {
                    if (o1.callFrequency != o2.callFrequency) {
                        return Integer.compare(o2.callFrequency, o1.callFrequency);
                    }
                    return o1.name.compareToIgnoreCase(o2.name);
                }
            });
        } else if (mCurrentFilterMode == 3) {
            // 3: Amaran Scammer
            Collections.sort(mFilteredContacts, new Comparator<ContactModel>() {
                @Override
                public int compare(ContactModel o1, ContactModel o2) {
                    return o1.name.compareToIgnoreCase(o2.name);
                }
            });
        }

        if (mFilteredContacts.isEmpty()) {
            if (!mAllContacts.isEmpty()) {
                mTvEmptyState.setText(mCurrentFilterMode == 3 ? "Tiada kenalan dengan amaran scammer ditemui." : "Tiada kenalan sepadan dengan carian.");
            } else {
                mTvEmptyState.setText("Buku telefon peranti kosong.");
            }
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    // ==================== LISTVIEW ADAPTER + VIEWHOLDER ====================

    private static class ViewHolder {
        LinearLayout card;
        TextView tvCheckbox;
        TextView tvAvatar;
        TextView tvName;
        TextView tvNumber;
        TextView tvStatusTag;
        LinearLayout actionsRow;
        Button btnCall;
        Button btnWhatsApp;
    }

    private class ContactsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mFilteredContacts.size();
        }

        @Override
        public Object getItem(int position) {
            return mFilteredContacts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = createItemView();
                holder = (ViewHolder) convertView.getTag();
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final ContactModel contact = mFilteredContacts.get(position);
            bindViewHolder(holder, contact);
            return convertView;
        }
    }

    private View createItemView() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(createPillDrawable(0xFF1E293B, 0xFF334155, 1, dpToPx(12)));
        card.setPadding(dpToPx(12), dpToPx(12), dpToPx(14), dpToPx(12));

        // 0. Checkbox Indicator (Pilihan Berbilang & Padam)
        TextView tvCheckbox = new TextView(this);
        tvCheckbox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvCheckbox.setPadding(0, 0, dpToPx(8), 0);
        tvCheckbox.setVisibility(View.GONE);
        card.addView(tvCheckbox);

        // 1. Avatar Huruf (Boleh ditekan terus untuk membuka profil)
        TextView tvAvatar = new TextView(this);
        tvAvatar.setTextColor(Color.WHITE);
        tvAvatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvAvatar.setTypeface(Typeface.DEFAULT_BOLD);
        tvAvatar.setGravity(Gravity.CENTER);
        tvAvatar.setFocusable(true);
        tvAvatar.setClickable(true);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        lpAv.setMarginEnd(dpToPx(12));
        tvAvatar.setLayoutParams(lpAv);
        card.addView(tvAvatar);

        // 2. Info Column (Nama di atas, Nombor di bawah)
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvName = new TextView(this);
        tvName.setTextColor(0xFFF8FAFC); // Slate 50
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(tvName);

        TextView tvNumber = new TextView(this);
        tvNumber.setTextColor(0xFF94A3B8); // Slate 400
        tvNumber.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvNumber.setPadding(0, dpToPx(2), 0, 0);
        infoCol.addView(tvNumber);

        TextView tvStatusTag = new TextView(this);
        tvStatusTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvStatusTag.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusTag.setPadding(0, dpToPx(2), 0, 0);
        infoCol.addView(tvStatusTag);

        card.addView(infoCol);

        // 3. Action Buttons Row (WhatsApp Direct Chat & Panggilan)
        // Ikon dialog bubble [💬 WhatsApp] dialihkan ke sebelah KIRI [📞 Panggilan] dan diberi zon penampan selesa
        // supaya pengguna berjari besar tidak lagi tersentuh bar tatalan (scrollbar).
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        Button btnWhatsApp = new Button(this);
        btnWhatsApp.setText("💬");
        btnWhatsApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnWhatsApp.setTextColor(Color.WHITE);
        btnWhatsApp.setBackground(createRipplePill(0xFF059669, 0xFF047857, dpToPx(20))); // WhatsApp Emerald Green
        LinearLayout.LayoutParams lpWa = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
        lpWa.setMarginEnd(dpToPx(8)); // Jarak penampan antara WhatsApp dan Panggilan
        btnWhatsApp.setLayoutParams(lpWa);
        actionsRow.addView(btnWhatsApp);

        Button btnCall = new Button(this);
        btnCall.setText("📞");
        btnCall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnCall.setBackground(createRipplePill(0xFF065F46, 0xFF047857, dpToPx(20))); // Emerald dark
        LinearLayout.LayoutParams lpCall = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
        lpCall.setMarginEnd(dpToPx(4)); // Margin keselamatan daripada tepi kad
        btnCall.setLayoutParams(lpCall);
        actionsRow.addView(btnCall);

        card.addView(actionsRow);

        ViewHolder holder = new ViewHolder();
        holder.card = card;
        holder.tvCheckbox = tvCheckbox;
        holder.tvAvatar = tvAvatar;
        holder.tvName = tvName;
        holder.tvNumber = tvNumber;
        holder.tvStatusTag = tvStatusTag;
        holder.actionsRow = actionsRow;
        holder.btnCall = btnCall;
        holder.btnWhatsApp = btnWhatsApp;

        card.setTag(holder);
        return card;
    }

    private void bindViewHolder(final ViewHolder holder, final ContactModel contact) {
        final boolean isSelected = mContactSelectionMode && mSelectedContactIds.contains(contact.id);

        if (mCardNormalBg == null) {
            mCardNormalBg = createPillDrawable(0xFF1E293B, 0xFF334155, 1, dpToPx(12));
            mCardSelectedBg = createPillDrawable(0xFF1E3A8A, 0xFF38BDF8, 1, dpToPx(12));
        }

        if (mContactSelectionMode) {
            holder.tvCheckbox.setVisibility(View.VISIBLE);
            holder.tvCheckbox.setText(isSelected ? "☑️" : "⬜");
            holder.actionsRow.setVisibility(View.GONE);
            holder.card.setBackground(isSelected ? mCardSelectedBg : mCardNormalBg);
        } else {
            holder.tvCheckbox.setVisibility(View.GONE);
            holder.actionsRow.setVisibility(View.VISIBLE);
            holder.card.setBackground(mCardNormalBg);
        }

        // Paparkan Nama Pemilik di atas, dan Nombor Telefon di bawah
        if (contact.hasRealName) {
            holder.tvName.setText(contact.name);
        } else {
            holder.tvName.setText(contact.formattedNumber);
        }

        // Subtext disesuaikan mengikut mod penapis aktif
        if (mCurrentFilterMode == 1 && contact.lastCallDate > 0) {
            holder.tvNumber.setText(contact.formattedNumber + " • ⏱️ " + formatCallDate(contact.lastCallDate));
        } else if (mCurrentFilterMode == 2 && contact.callFrequency > 0) {
            holder.tvNumber.setText(contact.formattedNumber + " • 🔥 " + contact.callFrequency + " panggilan");
        } else {
            String label = contact.hasRealName ? contact.typeLabel : "Nombor Peranti";
            holder.tvNumber.setText(contact.formattedNumber + " • " + label);
        }

        // Avatar Initial & Background Caching
        holder.tvAvatar.setText(contact.initial);
        Drawable avBg = mAvatarBgCache.get(contact.avatarColor);
        if (avBg == null) {
            avBg = createRipplePill(contact.avatarColor, 0xFFFFFFFF, dpToPx(22));
            mAvatarBgCache.put(contact.avatarColor, avBg);
        }
        holder.tvAvatar.setBackground(avBg);

        // Tekan Avatar Huruf terus membuka profil keselamatan (atau pilih semasa mod pilihan)
        holder.tvAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContactSelectionMode) {
                    toggleContactSelection(contact.id);
                } else {
                    openContactProfile(contact.number);
                }
            }
        });

        if (contact.isScammer) {
            holder.tvStatusTag.setVisibility(View.VISIBLE);
            holder.tvStatusTag.setText("🚨 " + (contact.scammerTag != null ? contact.scammerTag : "AMARAN SCAMMER"));
            holder.tvStatusTag.setTextColor(0xFFEF4444); // Red 500
        } else if (contact.isSafe) {
            holder.tvStatusTag.setVisibility(View.VISIBLE);
            holder.tvStatusTag.setText("🟢 Kenalan Sah Terlindung");
            holder.tvStatusTag.setTextColor(0xFF10B981); // Emerald 500
        } else {
            holder.tvStatusTag.setVisibility(View.GONE);
        }

        holder.btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initiateCall(contact.number);
            }
        });

        holder.btnWhatsApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWhatsAppChat(contact.number);
            }
        });

        // Tekan lama pada kad untuk mengaktifkan mod pilihan padam (sama seperti bahagian Mesej)
        holder.card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mContactSelectionMode) {
                    try {
                        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        if (vib != null) vib.vibrate(25);
                    } catch (Throwable ignored) {}
                    enterContactSelectionMode(contact.id);
                    return true;
                }
                return false;
            }
        });

        holder.card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContactSelectionMode) {
                    toggleContactSelection(contact.id);
                } else {
                    openContactProfile(contact.number);
                }
            }
        });
    }

    // ==================== MULTI-SELECTION & DELETION METHODS ====================

    private void enterContactSelectionMode(Long preselectedId) {
        mContactSelectionMode = true;
        mSelectedContactIds.clear();
        if (preselectedId != null && preselectedId > 0) {
            mSelectedContactIds.add(preselectedId);
        }
        updateSelectionUI();
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    private void exitContactSelectionMode() {
        mContactSelectionMode = false;
        mSelectedContactIds.clear();
        updateSelectionUI();
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    private void toggleContactSelection(long id) {
        if (mSelectedContactIds.contains(id)) {
            mSelectedContactIds.remove(id);
        } else {
            mSelectedContactIds.add(id);
        }
        updateSelectionUI();
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    private void toggleContactSelectAll() {
        if (!mFilteredContacts.isEmpty() && mSelectedContactIds.size() == mFilteredContacts.size()) {
            mSelectedContactIds.clear();
        } else {
            for (ContactModel c : mFilteredContacts) {
                if (c.id > 0) {
                    mSelectedContactIds.add(c.id);
                }
            }
        }
        updateSelectionUI();
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        if (mNormalHeaderBar != null) mNormalHeaderBar.setVisibility(mContactSelectionMode ? View.GONE : View.VISIBLE);
        if (mSelectionHeaderBar != null) mSelectionHeaderBar.setVisibility(mContactSelectionMode ? View.VISIBLE : View.GONE);
        int count = mSelectedContactIds.size();
        if (mTvSelectedCount != null) {
            mTvSelectedCount.setText(count + " Dipilih");
        }
        if (mBtnDeleteSelected != null) {
            mBtnDeleteSelected.setEnabled(count > 0);
            mBtnDeleteSelected.setAlpha(count > 0 ? 1.0f : 0.4f);
        }
        if (mBtnSelectAll != null) {
            boolean allSelected = !mFilteredContacts.isEmpty() && (mSelectedContactIds.size() == mFilteredContacts.size());
            mBtnSelectAll.setText(allSelected ? "☐ NYAH" : "✓ SEMUA");
        }
    }

    private void confirmDeleteSelectedContacts() {
        final int count = mSelectedContactIds.size();
        if (count == 0) return;

        new AlertDialog.Builder(this)
            .setTitle("🗑️ Padam " + count + " Kenalan?")
            .setMessage("Adakah anda pasti mahu memadam " + count + " kenalan yang dipilih daripada buku telefon peranti anda?\n\nTindakan ini akan memadam rekod kenalan tersebut secara kekal.")
            .setPositiveButton("PADAM (" + count + ")", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteSelectedContactsAsync();
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void deleteSelectedContactsAsync() {
        final Set<Long> toDelete = new HashSet<>(mSelectedContactIds);
        exitContactSelectionMode();
        Toast.makeText(this, "Sedang memadam " + toDelete.size() + " kenalan...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                int successCount = 0;
                for (Long contactId : toDelete) {
                    if (contactId == null || contactId <= 0) continue;
                    try {
                        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactId));
                        int rows = getContentResolver().delete(uri, null, null);
                        if (rows <= 0) {
                            getContentResolver().delete(
                                ContactsContract.RawContacts.CONTENT_URI,
                                ContactsContract.RawContacts.CONTACT_ID + " = ?",
                                new String[]{String.valueOf(contactId)}
                            );
                        }
                        successCount++;
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                final int finalCount = successCount;
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (sCachedContacts) {
                            sCachedContacts.clear();
                            sLastCacheTime = 0;
                        }
                        Toast.makeText(ContactsActivity.this, finalCount + " kenalan berjaya dipadam.", Toast.LENGTH_SHORT).show();
                        checkAndLoadContacts(true);
                    }
                });
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (mContactSelectionMode) {
            exitContactSelectionMode();
            return;
        }
        super.onBackPressed();
    }

    // ==================== ACTIONS ====================

    private void initiateCall(String phone) {
        if (TextUtils.isEmpty(phone)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(phone)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
                dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(dial);
            } catch (Throwable ignored) {
                Toast.makeText(this, "Gagal memulakan panggilan", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openWhatsAppChat(String phone) {
        if (TextUtils.isEmpty(phone)) return;
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

    private String formatCallDate(long timeMs) {
        if (timeMs <= 0) return "Tiada panggilan";
        long diff = System.currentTimeMillis() - timeMs;
        if (diff < 60 * 1000) return "Terkini";
        if (diff < 60 * 60 * 1000) return (diff / (60 * 1000)) + "m lalu";
        if (diff < 24 * 60 * 60 * 1000) return (diff / (3600 * 1000)) + "j lalu";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());
        return sdf.format(new java.util.Date(timeMs));
    }

    private void openContactProfile(String phone) {
        if (TextUtils.isEmpty(phone)) return;
        try {
            Intent intent = new Intent(this, ContactProfileActivity.class);
            intent.putExtra(ContactProfileActivity.EXTRA_PHONE_NUMBER, phone);
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal membuka profil kenalan", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== HELPER DRAWABLES & DP ====================

    public static String getAvatarInitial(String name, boolean hasRealName) {
        if (!hasRealName || name == null) return "👤";
        String trimmed = name.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c)) {
                return String.valueOf(c).toUpperCase(Locale.ROOT);
            }
        }
        return "👤";
    }

    public static int getAvatarColor(String seed) {
        int[] palette = new int[]{
            0xFF2563EB, // Blue 600
            0xFF059669, // Emerald 600
            0xFF7C3AED, // Violet 600
            0xFFD97706, // Amber 600
            0xFFDB2777, // Pink 600
            0xFF0D9488, // Teal 600
            0xFFEA580C  // Orange 600
        };
        int hash = Math.abs(seed != null ? seed.hashCode() : 0);
        return palette[hash % palette.length];
    }

    private GradientDrawable createPillDrawable(int bgColor, int strokeColor, int strokeWidthDp, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        if (strokeWidthDp > 0) {
            gd.setStroke(dpToPx(strokeWidthDp), strokeColor);
        }
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    private RippleDrawable createRipplePill(int normalColor, int rippleColor, int radiusPx) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(normalColor);
        content.setCornerRadius(radiusPx);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);

        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}
