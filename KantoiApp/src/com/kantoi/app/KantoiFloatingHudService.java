package com.kantoi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;

/**
 * IDEA 1: 1-Tap Floating Threat HUD (Quick Scanner)
 * Gelembung terapung yang sentiasa sedia di skrin (atas WhatsApp, Bank, Telegram, FB).
 * Pengguna hanya perlu ketik 1 kali untuk membuka kad dossier risikan bersepadu
 * tanpa perlu keluar dari aplikasi semasa.
 */
public class KantoiFloatingHudService extends Service {
    private static final String TAG = "KantoiFloatingHud";
    private static final String CHANNEL_ID = "kantoi_floating_hud_channel";
    private static final int NOTIF_ID = 9015;

    public static volatile boolean sIsRunning = false;

    private WindowManager mWindowManager;
    private FrameLayout mRootContainer;
    private WindowManager.LayoutParams mParams;

    // View keadaan
    private LinearLayout mBubbleView;
    private LinearLayout mCardView;
    private boolean mIsExpanded = false;

    // Komponen kad
    private EditText mEtTarget;
    private LinearLayout mResultCard;
    private TextView mTvResultHeader;
    private TextView mTvResultBody;
    private Button mBtnShareWa;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        sIsRunning = true;
        Log.i(TAG, "KantoiFloatingHudService created.");
        startForegroundNotification();
        initFloatingView();
    }

    private void startForegroundNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "Kantoi Gelembung Terapung (Floating HUD)",
                    NotificationManager.IMPORTANCE_LOW
                );
                chan.setDescription("Perkhidmatan Gelembung Pemeriksa Skrin Pantas KANTOI");
                nm.createNotificationChannel(chan);
            }

            Intent openIntent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

            nb.setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle("🛡️ Gelembung KANTOI Aktif")
              .setContentText("Ketik gelembung terapung di skrin untuk imbasan pantas 1-ketikan.")
              .setContentIntent(pi)
              .setOngoing(true);

            startForeground(NOTIF_ID, nb.build());
        } catch (Throwable t) {
            Log.e(TAG, "Error starting foreground notification: " + t.getMessage(), t);
        }
    }

    private void initFloatingView() {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mWindowManager == null) return;

        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        mParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 20;
        mParams.y = 250;

        mRootContainer = new FrameLayout(this);

        // 1. Bina Bubble (Gelembung Kuncup)
        buildBubbleView();
        mRootContainer.addView(mBubbleView);

        // 2. Bina Card (Kad Kembang)
        buildCardView();
        mCardView.setVisibility(View.GONE);
        mRootContainer.addView(mCardView);

        try {
            mWindowManager.addView(mRootContainer, mParams);
        } catch (Throwable t) {
            Log.e(TAG, "Gagal menambah floating view ke WindowManager: " + t.getMessage(), t);
        }
    }

    private void buildBubbleView() {
        mBubbleView = new LinearLayout(this);
        mBubbleView.setOrientation(LinearLayout.HORIZONTAL);
        mBubbleView.setGravity(Gravity.CENTER);
        int padH = dpToPx(12);
        int padV = dpToPx(8);
        mBubbleView.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE0F172A); // Slate dark semi-transparent
        bg.setCornerRadius(dpToPx(24));
        bg.setStroke(dpToPx(2), 0xFF059669); // Emerald border
        mBubbleView.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setText("🛡️ KANTOI");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        mBubbleView.addView(tv);

        // Touch & Drag listener
        mBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            mWindowManager.updateViewLayout(mRootContainer, mParams);
                        } catch (Throwable ignored) {}
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);
                        if (duration < 250 && diffX < 15 && diffY < 15) {
                            // 1-Tap Trigger!
                            expandCard();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void buildCardView() {
        mCardView = new LinearLayout(this);
        mCardView.setOrientation(LinearLayout.VERTICAL);
        int cardWidth = dpToPx(320);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        mCardView.setLayoutParams(clp);
        mCardView.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF0F172A); // Dark Slate 900
        cardBg.setCornerRadius(dpToPx(16));
        cardBg.setStroke(dpToPx(2), 0xFF38BDF8); // Sky blue neon accent
        mCardView.setBackground(cardBg);

        // Bar Tajuk & Butang Tutup
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🛡️ KANTOI CYBER HUD");
        tvTitle.setTextColor(0xFF38BDF8);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        titleBar.addView(tvTitle, tLp);

        TextView tvClose = new TextView(this);
        tvClose.setText(" ✕ ");
        tvClose.setTextColor(0xFF94A3B8);
        tvClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvClose.setTypeface(Typeface.DEFAULT_BOLD);
        tvClose.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        tvClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseCard();
            }
        });
        titleBar.addView(tvClose);
        mCardView.addView(titleBar);

        // Subtitle
        TextView tvSub = new TextView(this);
        tvSub.setText("Imbasan PDRM CCID + Pangkalan KANTOI 1-Ketikan");
        tvSub.setTextColor(0xFF94A3B8);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        setMarginTop(tvSub, 2);
        mCardView.addView(tvSub);

        // Input Sasaran
        mEtTarget = new EditText(this);
        mEtTarget.setHint("Nombor Telefon / Akaun Bank...");
        mEtTarget.setHintTextColor(0xFF64748B);
        mEtTarget.setTextColor(Color.WHITE);
        mEtTarget.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mEtTarget.setInputType(InputType.TYPE_CLASS_PHONE);
        mEtTarget.setSingleLine(true);
        mEtTarget.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));

        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF1E293B);
        etBg.setCornerRadius(dpToPx(8));
        etBg.setStroke(dpToPx(1), 0xFF475569);
        mEtTarget.setBackground(etBg);
        setMarginTop(mEtTarget, 8);
        mCardView.addView(mEtTarget);

        // Butang Semak 1-Ketikan
        Button btnScan = new Button(this);
        btnScan.setText("⚡ SEMAK RISIKAN SEKARANG");
        btnScan.setTextColor(Color.WHITE);
        btnScan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnScan.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF0284C7); // Sky blue button
        btnBg.setCornerRadius(dpToPx(8));
        btnScan.setBackground(btnBg);
        setMarginTop(btnScan, 8);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performHudScan();
            }
        });
        mCardView.addView(btnScan);

        // Bekas Hasil Imbasan (Result Card)
        mResultCard = new LinearLayout(this);
        mResultCard.setOrientation(LinearLayout.VERTICAL);
        mResultCard.setVisibility(View.GONE);
        mResultCard.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        setMarginTop(mResultCard, 8);

        mTvResultHeader = new TextView(this);
        mTvResultHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mTvResultHeader.setTypeface(Typeface.DEFAULT_BOLD);
        mResultCard.addView(mTvResultHeader);

        mTvResultBody = new TextView(this);
        mTvResultBody.setTextColor(0xFFCBD5E1);
        mTvResultBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        setMarginTop(mTvResultBody, 4);
        mResultCard.addView(mTvResultBody);

        mBtnShareWa = new Button(this);
        mBtnShareWa.setText("📤 KONGSI KE WHATSAPP");
        mBtnShareWa.setTextColor(Color.WHITE);
        mBtnShareWa.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mBtnShareWa.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable waBg = new GradientDrawable();
        waBg.setColor(0xFF059669); // WhatsApp green
        waBg.setCornerRadius(dpToPx(8));
        mBtnShareWa.setBackground(waBg);
        setMarginTop(mBtnShareWa, 6);
        mResultCard.addView(mBtnShareWa);

        mCardView.addView(mResultCard);
    }

    private void expandCard() {
        mIsExpanded = true;
        mBubbleView.setVisibility(View.GONE);
        mCardView.setVisibility(View.VISIBLE);

        // Benarkan input sentuhan ke EditText
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        try {
            mWindowManager.updateViewLayout(mRootContainer, mParams);
        } catch (Throwable ignored) {}

        // Semak clipboard secara automatik untuk mengisi sasaran jika ada nombor disalin
        autoCheckClipboard();
    }

    private void collapseCard() {
        mIsExpanded = false;
        mCardView.setVisibility(View.GONE);
        mBubbleView.setVisibility(View.VISIBLE);

        // Kunci semula sentuhan luar
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        try {
            mWindowManager.updateViewLayout(mRootContainer, mParams);
        } catch (Throwable ignored) {}
    }

    private void autoCheckClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence cs = clip.getItemAt(0).getText();
                    if (cs != null) {
                        String s = cs.toString().trim();
                        String digits = s.replaceAll("[^0-9]", "");
                        if (digits.length() >= 8 && digits.length() <= 18) {
                            mEtTarget.setText(digits);
                            // Terus laksanakan imbasan jika dikesan nombor sah dari clipboard!
                            performHudScan();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void performHudScan() {
        final String raw = mEtTarget.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Sila masukkan nombor sasaran.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String cleanDigits = raw.replaceAll("[^0-9]", "");
        if (cleanDigits.length() < 8) {
            Toast.makeText(this, "Sila masukkan sekurang-kurangnya 8 digit.", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean isPhone = cleanDigits.startsWith("01") || cleanDigits.startsWith("601") || cleanDigits.startsWith("1");
        final String local = isPhone ? KantoiThreatEngine.normalizeToLocal(cleanDigits) : cleanDigits;

        mResultCard.setVisibility(View.VISIBLE);
        GradientDrawable loadingBg = new GradientDrawable();
        loadingBg.setColor(0xFF1E293B);
        loadingBg.setCornerRadius(dpToPx(8));
        loadingBg.setStroke(dpToPx(1), 0xFF64748B);
        mResultCard.setBackground(loadingBg);
        mTvResultHeader.setText("⏳ Menyemak PDRM CCID & Pangkalan Data KANTOI...");
        mTvResultHeader.setTextColor(0xFF38BDF8);
        mTvResultBody.setText("Kategori: " + (isPhone ? "Telefon" : "Akaun Bank") + " [" + local + "]");
        mBtnShareWa.setVisibility(View.GONE);

        // 1. Semakan Offline SQLite KANTOI serta-merta
        final KantoiThreatEngine engine = KantoiThreatEngine.getInstance(this);
        final KantoiThreatEngine.ScammerThreat threat = isPhone ? engine.checkThreat(local) : engine.checkBankThreat(cleanDigits);

        // 2. Semakan Siap-Sedia PDRM CCID secara langsung di latar belakang
        final String cat = isPhone ? "telefon" : "bank";
        new Thread(new Runnable() {
            @Override
            public void run() {
                final KantoiThreatEngine.SemakMuleData smData = KantoiThreatEngine.querySemakMuleNetworkDirect(cat, local);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        renderDossierResult(local, isPhone, threat, smData);
                    }
                });
            }
        }).start();
    }

    private void renderDossierResult(final String target, boolean isPhone, KantoiThreatEngine.ScammerThreat threat, KantoiThreatEngine.SemakMuleData smData) {
        int reports = (smData != null && smData.reportCount > 0) ? smData.reportCount : 0;
        int searches = (smData != null && smData.searchCount > 0) ? smData.searchCount : 0;
        boolean hasThreat = (threat != null) || (reports > 0);

        GradientDrawable resBg = new GradientDrawable();
        resBg.setCornerRadius(dpToPx(8));

        if (hasThreat) {
            resBg.setColor(0xFF7F1D1D); // Deep Crimson
            resBg.setStroke(dpToPx(1), 0xFFEF4444);
            mResultCard.setBackground(resBg);

            mTvResultHeader.setText("🚨 ANCAMAN SCAMMER DIKESAN!");
            mTvResultHeader.setTextColor(0xFFFECACA);

            StringBuilder sb = new StringBuilder();
            sb.append("• Sasaran: ").append(target).append(" (").append(isPhone ? "Telefon" : "Bank").append(")\n");
            if (reports > 0) {
                sb.append("• 🏛️ PDRM CCID: 🚨 ").append(reports).append(" Laporan Polis! (Dicari ").append(searches).append(" kali)\n");
            } else {
                sb.append("• 🏛️ PDRM CCID: 0 Laporan (Dicari ").append(searches).append(" kali)\n");
            }

            if (threat != null) {
                sb.append("• 🛡️ Pangkalan KANTOI: ").append(threat.riskTag).append("\n");
                if (threat.suspectName != null && !threat.suspectName.isEmpty()) {
                    sb.append("• 👤 Suspek: ").append(threat.suspectName).append("\n");
                }
                if (threat.bankAccounts != null && !threat.bankAccounts.equals("N/A")) {
                    sb.append("• 🏦 Bank Terlibat: ").append(threat.bankAccounts).append("\n");
                }
            }
            sb.append("⚠️ JANGAN PINDAHKAN WANG!");
            mTvResultBody.setText(sb.toString());
        } else {
            resBg.setColor(0xFF064E3B); // Deep Emerald
            resBg.setStroke(dpToPx(1), 0xFF10B981);
            mResultCard.setBackground(resBg);

            mTvResultHeader.setText("✅ REKOD BERSIH (TIADA LAPORAN)");
            mTvResultHeader.setTextColor(0xFFA7F3D0);

            StringBuilder sb = new StringBuilder();
            sb.append("• Sasaran: ").append(target).append("\n");
            sb.append("• 🏛️ PDRM CCID: 0 Laporan Polis (Dicari ").append(searches).append(" kali)\n");
            sb.append("• 🛡️ Pangkalan KANTOI: Rekod Bersih (6,100+ Senarai)\n");
            sb.append("ℹ️ Tiada laporan jenayah rasmi dikesan setakat ini.");
            mTvResultBody.setText(sb.toString());
        }

        // Konfigurasi Butang Perkongsian WhatsApp 1-Ketikan
        mBtnShareWa.setVisibility(View.VISIBLE);
        mBtnShareWa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareThreatToWhatsApp(target);
            }
        });
    }

    private void shareThreatToWhatsApp(String target) {
        try {
            String msg = "⚠️ Semakan Keselamatan Siber KANTOI:\nSila berhati-hati dengan nombor/akaun " + target + ". Semak status scammer secara percuma di Telegram: @crowdcallerbot atau guna aplikasi KANTOI!";
            Intent sendIt = new Intent(Intent.ACTION_SEND);
            sendIt.setType("text/plain");
            sendIt.putExtra(Intent.EXTRA_TEXT, msg);
            sendIt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sendIt.setPackage("com.whatsapp");
            try {
                startActivity(sendIt);
            } catch (Throwable notFound) {
                // Jika WhatsApp tidak dipasang, buka general chooser
                sendIt.setPackage(null);
                startActivity(Intent.createChooser(sendIt, "Kongsi Amaran KANTOI").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal melancarkan WhatsApp: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sIsRunning = false;
        try {
            if (mWindowManager != null && mRootContainer != null) {
                mWindowManager.removeView(mRootContainer);
            }
        } catch (Throwable ignored) {}
        Log.i(TAG, "KantoiFloatingHudService destroyed.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void setMarginTop(View v, int dp) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).topMargin = dpToPx(dp);
            v.setLayoutParams(lp);
        }
    }
}
