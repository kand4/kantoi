package com.kantoi.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ChangelogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0F172A); // Deep Slate 900

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 1. Header with Back Button
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dpToPx(4), 0, dpToPx(16));

        Button btnBack = new Button(this);
        btnBack.setText("← KEMBALI");
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
        tvTitle.setText("  📜 LOG PERUBAHAN (CHANGELOG)");
        tvTitle.setTextColor(0xFFF8FAFC);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(tvTitle);

        root.addView(topBar);

        // Subtitle Info
        TextView tvSub = new TextView(this);
        tvSub.setText("Pantau rekod penambahbaikan ciri keselamatan, fungsi sistem, dan pangkalan data ancaman KANTOI.");
        tvSub.setTextColor(0xFF94A3B8);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSub.setPadding(0, 0, 0, dpToPx(14));
        root.addView(tvSub);

        // ==================== VERSION 1.13.9 ====================
        LinearLayout cardV1139 = createCard(0xFF1E293B, 0xFF10B981, 12);
        cardV1139.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1139Header = createVersionHeader("VERSI 1.13.9 (TERKINI)", "20 OKTOBER 2026", 0xFF10B981);
        cardV1139.addView(v1139Header);

        TextView tvV1139Content = new TextView(this);
        tvV1139Content.setText(
            "🔑 Kunci Komuniti VirusTotal Terbina (Multi-Layer Obfuscated) & Model Hibrid BYOK:\n\n" +
            "✨ 1. Kunci Komuniti Bawaan Siap Pasang (Zero-Config Community Key):\n" +
            "   • Pengguna baharu tidak perlu mendaftar atau menyalin kunci API secara manual untuk menikmati imbasan VirusTotal.\n" +
            "   • KANTOI kini dilengkapi Kunci Komuniti pra-pasang yang sedia digunakan serta-merta sebaik sahaja aplikasi dipasang.\n\n" +
            "🛡️ 2. Perlindungan Penyamaran Multi-Lapisan (Bitwise XOR Masking & Seed Splitting):\n" +
            "   • Kunci API dilindungi menggunakan penyamaran bitwise (Bitwise XOR) dan pemecahan vektor bait dalam memori.\n" +
            "   • Menghalang carian rentetan teks biasa (strings/hex) oleh alat dekompilasi asas (Jadx / Apktool).\n\n" +
            "🔄 3. Model Hibrid BYOK (Bring-Your-Own-Key Priority):\n" +
            "   • Sekiranya pengguna memasukkan Kunci API peribadi sendiri di bahagian Tetapan Pertahanan, sistem akan secara automatik mengutamakan kunci peribadi pengguna untuk mendapatkan kuota 500 semakan/hari tersendiri.\n" +
            "   • Sekiranya dikosongkan, sistem beralih semula secara pintar ke Kunci Komuniti Bawaan."
        );
        tvV1139Content.setTextColor(0xFFE2E8F0);
        tvV1139Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1139Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1139Content, 10);
        cardV1139.addView(tvV1139Content);

        setMarginTop(cardV1139, 10);
        root.addView(cardV1139);

        // ==================== VERSION 1.13.8 ====================
        LinearLayout cardV1138 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1138.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1138Header = createVersionHeader("VERSI 1.13.8", "20 OKTOBER 2026", 0xFF94A3B8);
        cardV1138.addView(v1138Header);

        TextView tvV1138Content = new TextView(this);
        tvV1138Content.setText(
            "🛡️ Integrasi Pengimbas VirusTotal API v3, Enjin Heuristik APK Trojan Tempatan & Perisai Muat Turun (Download Sentry):\n\n" +
            "✨ 1. Enjin Heuristik Trojan Perbankan Tempatan (0ms, 100% Offline):\n" +
            "   • Menganalisis kebenaran berisiko tinggi secara luar talian serta-merta tanpa menggunakan data internet.\n" +
            "   • Mengesan formula trojan perbankan popular di Malaysia (MaliBot, Godfather dsb.) yang menggabungkan BIND_ACCESSIBILITY_SERVICE dengan kebenaran pintas SMS (RECEIVE_SMS / READ_SMS) untuk mencuri kod OTP/TAC perbankan.\n\n" +
            "🌐 2. Integrasi VirusTotal API v3 (70+ Enjin Antivirus Global):\n" +
            "   • Mengira hash SHA-256 fail APK secara tempatan dan menyemak reputasi kebersihan terhadap 70+ pengeluar antivirus antarabangsa (Kaspersky, Avast, Bitdefender, Google dsb.).\n" +
            "   • Menyokong semakan URL/Pautan suspek dengan analisis skor keselamatan pancingan data (phishing).\n" +
            "   • Pengguna boleh menetapkan API Key peribadi sendiri secara selamat dalam tetapan KANTOI.\n\n" +
            "📥 3. Penjaga Muat Turun Latar Belakang (Real-Time Download Sentry):\n" +
            "   • Mengawasi direktori muat turun sistem Android (/Download). Sebaik sahaja fail .apk dimuat turun daripada WhatsApp, Telegram, FB, atau pelayar web, amaran keselamatan berisiko tinggi terus dipaparkan sebelum mangsa memasangnya.\n\n" +
            "📲 4. Integrasi 'Kongsi ke KANTOI' (Universal Android System Share Target):\n" +
            "   • Pengguna kini boleh menekan 'Share / Kongsi' daripada sebarang aplikasi mesej sosial atau pengurus fail terus ke KANTOI untuk semakan keselamatan pantas.\n\n" +
            "🏛️ 5. Hab Pantas pada Kapsul Terapung (Enhanced Retractable Pill):\n" +
            "   • Butang terapung sisi kini memuatkan pintasan pantas ke Pengimbas Fail APK dan URL VirusTotal di samping SemakMule CCID PDRM."
        );
        tvV1138Content.setTextColor(0xFFE2E8F0);
        tvV1138Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1138Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1138Content, 10);
        cardV1138.addView(tvV1138Content);

        setMarginTop(cardV1138, 10);
        root.addView(cardV1138);

        // ==================== VERSION 1.13.7 ====================
        LinearLayout cardV1137 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1137.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1137Header = createVersionHeader("VERSI 1.13.7", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1137.addView(v1137Header);

        TextView tvV1137Content = new TextView(this);
        tvV1137Content.setText(
            "🏛️ Pemulihan & Penyahgandingan Senibina Butang Terapung Sisi SemakMule PDRM (Retractable Floating Pill):\n\n" +
            "✨ 1. Pemulihan Penuh Butang Kapsul Terapung Sisi (Retractable Slide-In/Out Pill):\n" +
            "   • Memulihkan butang terapung sisi '🏛️ PDRM' yang boleh disorok pada semua tab aplikasi utama (Panggilan, Kenalan, Pad Dail, Mesej).\n" +
            "   • Memudahkan pengguna membuat semakan 1-klik terus ke portal SemakMule JSJK PDRM pada bila-bila masa dari mana-mana skrin.\n\n" +
            "⚡ 2. Penyahgandingan Senibina Bersih (Zero-Lag Clean Architecture Decoupling):\n" +
            "   • Mengasingkan fungsi butang terapung daripada pengimbas OnPreDrawListener lama, menjadikannya komponen bebas O(1) yang dipasang serta-merta (<0.1ms) semasa kitaran hayat aktiviti.\n" +
            "   • Menjamin 100% kepantasan paparan 60/120 FPS kekal terpelihara tanpa sebarang sela masa (zero-lag).\n\n" +
            "👆 3. Ciri Ergonomik Boleh Seret & Auto-Sorok (Auto-Collapse & Drag):\n" +
            "   • Kapsul berlabuh kemas di sisi kanan skrin (115dp dari bawah, tidak menghalang navigasi).\n" +
            "   • Mengembang dengan animasi gelongsor (slide-out) apabila disentuh, menutup semula secara automatik atau melalui butang ✕, serta boleh dilaras ketinggiannya dengan seretan jari."
        );
        tvV1137Content.setTextColor(0xFFE2E8F0);
        tvV1137Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1137Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1137Content, 10);
        cardV1137.addView(tvV1137Content);

        setMarginTop(cardV1137, 10);
        root.addView(cardV1137);

        // ==================== VERSION 1.13.6 ====================
        LinearLayout cardV1136 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1136.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1136Header = createVersionHeader("VERSI 1.13.6", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1136.addView(v1136Header);

        TextView tvV1136Content = new TextView(this);
        tvV1136Content.setText(
            "📐 Penalaan Ergonomik Pad Dail & Simetri Visual Skrin Panggilan Aktif (Active In-Call UI/UX):\n\n" +
            "🔢 1. Butang Pad Dail DTMF Diperbesarkan (Ergonomic Large Touch Target):\n" +
            "   • Saiz setiap butang dail dinaikkan ke 80dp x 56dp (melebihi piawaian WCAG 48dp) dengan fon tebal bersaiz 22sp, direka khas untuk kemudahan pengguna yang mempunyai jari besar.\n" +
            "   • Menetapkan jarak pernafasan seimbang (12dp mendatar & 10dp menegak) antara setiap butang dan baris nombor supaya butang tidak terlalu rapat dan menghalang kesilapan dailan.\n" +
            "   • Dilengkapi maklum balas getaran haptik (keyboard tap) pada setiap sentuhan butang.\n\n" +
            "⏱️ 2. Penjajaran Tengah Teks Tempoh Panggilan Langsung:\n" +
            "   • Memperbaiki susunan teks 'Tempoh Panggilan Langsung' di bawah kronometer supaya berpusat tepat di tengah (center aligned) secara seimbang dan simetri.\n\n" +
            "🚨 3. Lencana Kategori Ancaman Profesional (Centered Security Pill Badge):\n" +
            "   • Menyelaraskan teks kategori ancaman (cth: '🚨 Kategori: Macau Scam PDRM') dengan lencana keselamatan (badge pill) berkontras tinggi dan berada tepat di tengah di bawah nombor pemanggil."
        );
        tvV1136Content.setTextColor(0xFFE2E8F0);
        tvV1136Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1136Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1136Content, 10);
        cardV1136.addView(tvV1136Content);

        setMarginTop(cardV1136, 10);
        root.addView(cardV1136);

        // ==================== VERSION 1.13.5 ====================
        LinearLayout cardV1135 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1135.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1135Header = createVersionHeader("VERSI 1.13.5", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1135.addView(v1135Header);

        TextView tvV1135Content = new TextView(this);
        tvV1135Content.setText(
            "⚡ Pengoptimuman Prestasi Penuh & Penghapusan Sela Masa (Zero-Lag 60/120 FPS UI Engine):\n\n" +
            "🚀 1. Pemansuhan 'OnPreDrawListener' Pada Aktiviti Natif:\n" +
            "   • Menyingkirkan pemeriksaan dekorasi berulang pada setiap bingkai lukisan skrin (60-120x sesaat) untuk semua tab KANTOI (Panggilan, Kenalan, Pad Dail, Mesej).\n" +
            "   • Menghapuskan 100% bebanan CPU, kekejangan animasi (jank), dan penurunan bingkai skrin semasa menatal senarai.\n\n" +
            "⚡ 2. Carian & Penapisan Kenalan 0ms (Precomputed In-Memory Index):\n" +
            "   • Medan carian nama dan nombor telefon dipra-kira semasa pemuatan awal. Menghapuskan kompilasi Regex dalam gelung carian.\n" +
            "   • Menggunakan semula objek Drawable latar belakang kad & avatar bagi menghapuskan 'Garbage Collection pause' semasa tatalan pantas.\n\n" +
            "📱 3. Penukaran Tab Segera Tanpa Kelipan (Smart Non-Blocking Tab Restore):\n" +
            "   • Menghapuskan paparan loading berulang semasa bertukar antara tab Panggilan dan Mesej jika data sedia ada tidak berubah.\n" +
            "   • Memindahkan pertanyaan ContentResolver bagi semakan panggilan terlepas dan pemprosesan nombor tidak dikenali ke bebenang latar belakang sepenuhnya."
        );
        tvV1135Content.setTextColor(0xFFE2E8F0);
        tvV1135Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1135Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1135Content, 10);
        cardV1135.addView(tvV1135Content);

        setMarginTop(cardV1135, 10);
        root.addView(cardV1135);

        // ==================== VERSION 1.13.4 ====================
        LinearLayout cardV1134 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1134.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1134Header = createVersionHeader("VERSI 1.13.4", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1134.addView(v1134Header);

        TextView tvV1134Content = new TextView(this);
        tvV1134Content.setText(
            "📋 Mod Pilihan & Padam Kenalan (Tekan Lama) & Penalaan Antaramuka Panggilan Masuk:\n\n" +
            "🗑️ 1. Mod Pilihan & Padam Kenalan Melalui Tekan Lama (Long-Press Multi-Delete):\n" +
            "   • Seperti di bahagian Mesej dan Log Panggilan, pengguna kini boleh menekan lama (long press) mana-mana kad kenalan untuk mengaktifkan mod pilihan berbilang.\n" +
            "   • Dilengkapi bar tindakan atas [✓ SEMUA / ☐ NYAH], pembilang pilihan, dan butang [🗑️ PADAM] dengan dialog pengesahan keselamatan.\n\n" +
            "⚖️ 2. Penjajaran Teks Butang TOLAK dan JAWAB 100% Center & Simetri:\n" +
            "   • Struktur lajur tindakan distandardkan dengan kelebaran seimbang (120dp), memusatkan teks label 'TOLAK' / 'Abaikan' dan 'JAWAB' / 'Terima' tepat di bawah bulatan butang tanpa herot atau pemotongan teks.\n\n" +
            "🚫 3. Pembuangan Teks [SCAMMER] Berkembar Sebelah Nombor Telefon:\n" +
            "   • Teks lewah [SCAMMER] di sebelah nombor telefon dibuang sepenuhnya, mengekalkan paparan nombor telefon yang bersih dan kemas di bawah nama suspek."
        );
        tvV1134Content.setTextColor(0xFFE2E8F0);
        tvV1134Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1134Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1134Content, 10);
        cardV1134.addView(tvV1134Content);

        setMarginTop(cardV1134, 10);
        root.addView(cardV1134);

        // ==================== VERSION 1.13.3 ====================
        LinearLayout cardV1133 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1133.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1133Header = createVersionHeader("VERSI 1.13.3", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1133.addView(v1133Header);

        TextView tvV1133Content = new TextView(this);
        tvV1133Content.setText(
            "💬 Penalaan Ergonomik Ikon WhatsApp & Penyelesaian Konflik Bar Tatalan (Scroll Collision Fix):\n\n" +
            "👈 1. Pemindahan Ikon WhatsApp (💬) ke Sebelah Kiri:\n" +
            "   • Butang sembang pantas WhatsApp dipindahkan ke sebelah kiri butang panggilan telefon (📞) pada setiap kad kenalan.\n" +
            "   • Kedudukan baharu ini memberikan jarak selamat melebihi 48dp daripada bucu kanan skrin, menjauhkan ikon daripada laluan sentuhan bar tatalan.\n\n" +
            "🛡️ 2. Pembasmian Konflik Bar Tatalan (Fast-Scroll Interception Fix):\n" +
            "   • Mod 'FastScroll' sistem yang mencuri fokus sentuhan (mengakibatkan senarai melorot ke bawah apabila ditekan) telah dinyahaktifkan.\n" +
            "   • Tatalan kini dikendalikan oleh enjin penatalan lancar (smooth inertial scroll) tanpa sebarang pintasan sentuhan yang mengganggu ketikan pengguna.\n\n" +
            "🎯 3. Sasaran Sentuhan Mesra Jari Besar (Enhanced Touch Hit-Target):\n" +
            "   • Saiz butang tindakan dinaikkan kepada 40x40dp dengan zon penampan selesa dan maklum balas riak (ripple feedback) untuk ketikan pantas yang amat jitu."
        );
        tvV1133Content.setTextColor(0xFFE2E8F0);
        tvV1133Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1133Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1133Content, 10);
        cardV1133.addView(tvV1133Content);

        setMarginTop(cardV1133, 10);
        root.addView(cardV1133);

        // ==================== VERSION 1.13.2 ====================
        LinearLayout cardV1132 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1132.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1132Header = createVersionHeader("VERSI 1.13.2", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1132.addView(v1132Header);

        TextView tvV1132Content = new TextView(this);
        tvV1132Content.setText(
            "📐 Penalaan Posisi Dok PDRM (+20% Ketinggian) & Sokongan Seretan Bebas (Vertical Dragging):\n\n" +
            "⬆️ 1. Peningkatan Ketinggian Posisi Asal Sebanyak 20%+:\n" +
            "   • Kapsul terapung PDRM dinaikkan kedudukannya daripada 88dp kepada 115dp dari bahagian bawah skrin.\n" +
            "   • Memberikan zon penampan lebih luas dari dok navigasi bawah, meletakkannya tepat pada zon capaian ibu jari yang paling ergonomik.\n\n" +
            "🖐️ 2. Sokongan Seretan Vertikal Bebas (Interactive Vertical Dragging):\n" +
            "   • Pengguna kini boleh menyentuh dan menyeret kapsul PDRM ke atas atau ke bawah di sepanjang sisi kanan skrin mengikut keselesaan saiz skrin telefon masing-masing.\n" +
            "   • Algoritma pengesanan sentuhan pintar memastikan ketikan biasa (tap) tetap membuka fungsi Slide-In / Slide-Out tanpa sebarang konflik dengan seretan."
        );
        tvV1132Content.setTextColor(0xFFE2E8F0);
        tvV1132Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1132Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1132Content, 10);
        cardV1132.addView(tvV1132Content);

        setMarginTop(cardV1132, 10);
        root.addView(cardV1132);

        // ==================== VERSION 1.13.1 ====================
        LinearLayout cardV1131 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1131.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1131Header = createVersionHeader("VERSI 1.13.1", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1131.addView(v1131Header);

        TextView tvV1131Content = new TextView(this);
        tvV1131Content.setText(
            "🛡️ Dok Sisi Interaktif SemakMule PDRM (Slide-In / Slide-Out Edge Dock) & Pembebasan Bar Bawah:\n\n" +
            "✨ 1. Inovasi Kapsul Tepi Slide-In / Slide-Out (Interactive Edge Dock):\n" +
            "   • Butang SemakMule diubah suai menjadi kapsul tepi minimalis yang anggun di sebelah kanan skrin [🏛️ PDRM].\n" +
            "   • Mod Asal: Sekadar ikon perisai kecil berlabuh di tepi kanan tanpa menghalang sebarang teks atau senarai nama.\n" +
            "   • Mod Kembang (Slide Out): Mengetik kapsul meluncurkan bar penuh [🏛️ SEMAKMULE PDRM | ✕] dengan animasi lancar 60fps.\n" +
            "   • Pemasa Auto-Tutup: Meluncur masuk kembali (Slide In / Collapse) secara automatik selepas 4.5 saat atau apabila butang '✕' ditekan.\n\n" +
            "🚀 2. Pembebasan Total Bar Navigasi Bawah (KantoiBottomBar Freedom):\n" +
            "   • Menghapuskan sama sekali masalah butang SemakMule terapung bertindih di atas tab menu [Kenalan, Pad Dail, Mesej].\n" +
            "   • Kapsul tepi kini ditempatkan pada paras ketinggian 88dp di sebelah kanan (di atas zon dok navigasi bawah) memastikan 5 tab utama kekal 100% bebas halangan.\n\n" +
            "🚫 3. Pengecualian Pintar pada Dialog & Skrin Panggilan (Context Isolation):\n" +
            "   • Menghentikan penampalan butang terapung pada AfterCallActivity, IncomingCallActivity, dan ActiveCallActivity bagi mengelakkan butang berkembar atau tindihan visual.\n\n" +
            "🌐 4. Hab Semakan Pantas Dwi-Pilihan (Quick Action Modal):\n" +
            "   • Mengetik bar kembang memaparkan dialog pilihan: Buka terus portal rasmi PDRM (semakmule.rmp.gov.my) atau semak dalam pangkalan data ancaman KANTOI."
        );
        tvV1131Content.setTextColor(0xFFE2E8F0);
        tvV1131Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1131Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1131Content, 10);
        cardV1131.addView(tvV1131Content);

        setMarginTop(cardV1131, 10);
        root.addView(cardV1131);

        // ==================== VERSION 1.13.0 ====================
        LinearLayout cardV1130 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1130.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1130Header = createVersionHeader("VERSI 1.13.0", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1130.addView(v1130Header);

        TextView tvV1130Content = new TextView(this);
        tvV1130Content.setText(
            "🔔 Revolusi GUI Panggilan Masuk (Incoming Call UI Overhaul) & Perbezaan Mutlak Panggilan Aktif:\n\n" +
            "📱 1. Pengecaman Status Berdering Tanpa Ragu (Ringing State Clarity):\n" +
            "   • Banner status atas baharu dengan animasi denyutan berulang: '🔔 PANGGILAN MASUK • SEDANG BERDERING' beserta panduan tindakan.\n" +
            "   • Menghapuskan kekeliruan dengan skrin perbualan aktif — pengguna kini tahu serta-merta bahawa telefon sedang berdering dan panggilan belum disambungkan.\n\n" +
            "🌊 2. Efek Gelombang Radar Denyutan Avatar (Pulsing Radar Waves):\n" +
            "   • Avatar pemanggil kini dilengkapi lingkaran gelombang denyutan berkembar (pulsing ripple) animasi 60fps yang aktif semasa telefon berdering.\n" +
            "   • Skrin panggilan aktif kini menggunakan bingkai tenang tanpa denyutan gelombang radar bagi membezakan kedua-dua mod secara visual.\n\n" +
            "🔘 3. Butang Tindakan Berbeza & Tidak Mengelirukan (Jawab vs Tolak vs Tolak & Sekat):\n" +
            "   • Menghapuskan ikon pangkah (✕) yang mengelirukan pada skrin panggilan masuk.\n" +
            "   • Butang Jawab: Ikon 📞 hijau emerald berukuran 76dp dengan teks jelas 'JAWAB' dan 'Angkat & Berbual'.\n" +
            "   • Butang Tolak: Ikon 📵 merah crimson berukuran 76dp dengan teks jelas 'TOLAK' dan 'Abaikan Panggilan'.\n" +
            "   • Butang Khas Scammer: '🚫 TOLAK & SEKAT SCAMMER SERTA-MERTA' untuk memutuskan panggilan dan menyekat nombor terus ke pangkalan data peranti dalam 1-klik.\n\n" +
            "⚡ 4. Reka Bentuk Eksklusif Panggilan Aktif (ActiveCallActivity Refinement):\n" +
            "   • Header status hijau terang '🟢 PANGGILAN AKTIF • SEDANG BERBUAL'.\n" +
            "   • Kad pemasa digital langsung (⏱️ Chronometer) memaparkan tempoh perbualan saat demi saat.\n" +
            "   • Butang tamat panggilan kini berbentuk bar penuh merah terang: '📵 TAMATKAN PANGGILAN AKTIF' tanpa sebarang butang jawab yang mengelirukan.\n\n" +
            "🎛️ 5. Penukar Mod Demo Pantas (Interactive Demo Switcher):\n" +
            "   • Skrin ujian pratonon kini dilengkapi penukar mod segera [🚨 Scammer PDRM | ✓ Kenalan | 👤 Nombor Asing] untuk semakan GUI secara langsung."
        );
        tvV1130Content.setTextColor(0xFFE2E8F0);
        tvV1130Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1130Content.setLineSpacing(dpToPx(4), 1.15f);
        setMarginTop(tvV1130Content, 10);
        cardV1130.addView(tvV1130Content);

        setMarginTop(cardV1130, 10);
        root.addView(cardV1130);

        // ==================== VERSION 1.12.0 ====================
        LinearLayout cardV1120 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1120.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1120Header = createVersionHeader("VERSI 1.12.0", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1120.addView(v1120Header);

        TextView tvV1120Content = new TextView(this);
        tvV1120Content.setText(
            "🧭 Navigasi Bawah Terapung, 4 Penapis Buku Telefon, WhatsApp 1-Klik, & Reposisi SemakMule PDRM:\n\n" +
            "🔤 1. Empat Penapis Pintar Buku Telefon (Tactical Filtering):\n" +
            "   • 🔤 Nama (A - Z): Susunan abjad nama pemilik kenalan sebenar dengan pengesahan nama manusia.\n" +
            "   • ⏱️ Panggilan Terkini: Menyusun kenalan mengikut tarikh panggilan paling terkini beserta label masa relatif.\n" +
            "   • 🔥 Kerap Dihubungi: Menyusun kenalan berdasarkan kekerapan dihubungi beserta indikator jumlah panggilan.\n" +
            "   • 🚨 Amaran Scammer: Menapis hanya kenalan yang dikesan mempunyai rekod ancaman scammer atau akaun keldai.\n\n" +
            "👤 2. Avatar Akronim Boleh Ditekan & Pembuangan Ikon Info:\n" +
            "   • Ikon 'ℹ️' dipadamkan sepenuhnya bagi reka bentuk kad kenalan yang kemas dan profesional.\n" +
            "   • Mengetik huruf akronim avatar (cth. huruf 'A' bagi Alan) kini membuka terus halaman Profil Keselamatan Nombor.\n\n" +
            "💬 3. Pintasan WhatsApp 1-Klik dalam Buku Telefon & Log Panggilan:\n" +
            "   • Ikon gelembung dialog (💬) dalam Buku Telefon kini membuka sembang WhatsApp terus ke nombor tersebut.\n" +
            "   • Menambah butang hijau WhatsApp (💬) pada setiap baris Log Panggilan (Call History) untuk menghantar WhatsApp segera tanpa perlu menyimpan nombor terlebih dahulu.\n\n" +
            "🧭 4. Dok Navigasi Bawah Terapung Persisten (KantoiBottomBar):\n" +
            "   • Menu navigasi 5-tab [📞 Panggilan | 📖 Kenalan | 🔢 Pad Dail | 💬 Mesej | 🛡️ Pertahanan] kini kekal terapung di semua skrin utama.\n" +
            "   • Dilengkapi zon penampan (bottom padding 75dp & clipToPadding=false) memastikan kandungan paling bawah tidak sesekali terhalang.\n\n" +
            "🏛️ 5. Reposisi Strategik Butang SemakMule CCID PDRM:\n" +
            "   • Butang SemakMule PDRM dinaikkan ke bahagian atas halaman Profil dan Hasil Carian bagi capaian segera.\n" +
            "   • Prestasi ultra pantas (0ms Latency) berasaskan indeks memori setempat tanpa lagging."
        );
        tvV1120Content.setTextColor(0xFFE2E8F0);
        tvV1120Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1120Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1120Content, 8);
        cardV1120.addView(tvV1120Content);

        root.addView(cardV1120);
        setMarginTop(cardV1120, 10);

        // ==================== VERSION 1.11.9 ====================
        LinearLayout cardV1119 = createCard(0xFF1E293B, 0xFF64748B, 12);
        cardV1119.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        LinearLayout v1119Header = createVersionHeader("VERSI 1.11.9", "19 OKTOBER 2026", 0xFF94A3B8);
        cardV1119.addView(v1119Header);

        TextView tvV1119Content = new TextView(this);
        tvV1119Content.setText(
            "🚨 Sistem Laporan Scammer Komuniti KANTOI (Zero-Server Crowdsourcing) & Penstrukturan Kemas Butang Tindakan:\n\n" +
            "🚨 1. Laporan Scammer Terus ke Komuniti KANTOI (Tanpa Akaun Telegram):\n" +
            "   • Pengguna biasa kini boleh melaporkan sebarang nombor scammer terus dari aplikasi KANTOI tanpa perlu mempunyai akaun Telegram atau kemahiran teknikal.\n" +
            "   • Sistem menggunakan protokol HTTPS POST terus ke Telegram Bot API rasmi (@crowdcaller), menghantar dossier lengkap (nombor, kategori, suspek, akaun bank keldai, dan modus operandi) terus kepada Pentadbir Komuniti dalam masa nyata (real-time).\n" +
            "   • Integrasi automatik SemakMule CCID PDRM: Rekod carian dan laporan rasmi polis dilampirkan bersama laporan secara langsung.\n" +
            "   • Perlindungan serta-merta pada peranti: Nombor yang dilaporkan disekat serta-merta secara offline pada telefon anda dan dikategorikan secara automatik.\n" +
            "   • Jaminan Privasi: 100% anonim, tanpa memerlukan kebenaran data peribadi atau pengenalan diri pelapor.\n\n" +
            "🏷️ 2. Pemilih Kategori Interaktif Berasaskan Chip/Pill (High-Craft UI):\n" +
            "   • Antaramuka dialog laporan serba baharu dengan reka bentuk Slate-900 mewah, sempadan Crimson Red, dan cip kategori interaktif.\n" +
            "   • Pilihan kategori pantas: Macau Scam/Polis, Pinjaman Wang/Ah Long, Pelaburan Palsu/Kripto, Parcel Kastam/Kurier, Task E-Dagang/Shopee, Cabutan Bertuah/Hadiah, Phishing SMS/TAC, dan Gangguan/Ugutan.\n\n" +
            "🛡️ 3. Penstrukturan Kemas Butang Tindakan (Bebas Kekeliruan & Tiada Butang Berkembar):\n" +
            "   • Menghapuskan kekeliruan butang dalam paparan Profil Kenalan: Butang kini dipaparkan secara dinamik mengikut status nombor (Scammer disahkan, Rakan Selamat, atau Nombor Asing).\n" +
            "   • Jika nombor telah disenaraihitamkan, butang 'KEMASKINI / LAPOR SCAMMER' dan 'PADAM DARI REKOD SCAMMER' diasingkan dengan jelas bersama pengesahan keselamatan.\n" +
            "   • Butang di halaman Carian dan Menu Utama diselaraskan kepada '🚨 LAPOR SCAMMER KE KOMUNITI KANTOI'."
        );
        tvV1119Content.setTextColor(0xFFE2E8F0);
        tvV1119Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1119Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1119Content, 8);
        cardV1119.addView(tvV1119Content);

        root.addView(cardV1119);

        // ==================== VERSION 1.11.8 ====================
        LinearLayout cardV1118 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1118.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1118, 14);

        LinearLayout v1118Header = createVersionHeader("VERSI 1.11.8", "19 OKTOBER 2026", 0xFF38BDF8);
        cardV1118.addView(v1118Header);

        TextView tvV1118Content = new TextView(this);
        tvV1118Content.setText(
            "👤 Pengecaman Nama Sebenar Pemilik Kenalan & Penstrukturan Kemas UI Buku Telefon:\n\n" +
            "👤 1. Pengecaman Penuh Nama Sebenar Pemilik Kenalan (True Human Name Resolution):\n" +
            "   • Menyelesaikan isu di mana nombor telefon mentah (cth. '+60 10-279 8275') dipaparkan sebagai nama kenalan akibat data 'raw sync' WhatsApp/SIM yang menyekat nama asal.\n" +
            "   • Melaksanakan pengekstrakan nama agregat rasmi secara terus daripada ContactsContract.Contacts.CONTENT_URI dan ContactsContract.Data (StructuredName: GIVEN_NAME + FAMILY_NAME).\n" +
            "   • Algoritma isRealContactName pintar menapis dan menolak sebarang rentetan yang hanya mengandungi nombor, simbol '+', atau ketiadaan huruf manusia.\n" +
            "   • Smart Deduplication: Memastikan jika terdapat beberapa entri bagi nombor yang sama, entri yang mempunyai nama manusia sebenar akan sentiasa diutamakan.\n\n" +
            "🎨 2. Penstrukturan Kemas Hierarki Kad Kenalan (Pro UI Layout):\n" +
            "   • Nama Pemilik Kenalan dipaparkan dengan jelas di bahagian atas dalam fon tebal bersaiz 15sp (Slate-50 Bold).\n" +
            "   • Nombor Telefon dan label talian (cth. '+60 10-279 8275 • Mudah Alih') disusun kemas di baris bawah dalam warna kelabu lembut (Slate-400).\n" +
            "   • Avatar huruf bulat kini sentiasa menggunakan abjad pertama nama manusia (cth. 'A', 'M', 'Z') atau ikon '👤', menghapuskan sepenuhnya lambang '+' yang janggal.\n" +
            "   • Senarai disusun mengikut susunan abjad (A-Z) dengan kenalan bernama diutamakan di barisan atas."
        );
        tvV1118Content.setTextColor(0xFFE2E8F0);
        tvV1118Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1118Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1118Content, 8);
        cardV1118.addView(tvV1118Content);

        root.addView(cardV1118);

        // ==================== VERSION 1.11.7 ====================
        LinearLayout cardV1117 = createCard(0xFF1E293B, 0xFF38BDF8, 12);
        cardV1117.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1117, 14);

        LinearLayout v1117Header = createVersionHeader("VERSI 1.11.7", "19 OKTOBER 2026", 0xFF38BDF8);
        cardV1117.addView(v1117Header);

        TextView tvV1117Content = new TextView(this);
        tvV1117Content.setText(
            "⚡ Pengoptimuman Prestasi Ekstrim Buku Telefon & Tatalan Lancar 60/120 FPS:\n\n" +
            "⚡ 1. Paparan Serta-Merta Buku Telefon (Instant 0ms Load via In-Memory Static Cache):\n" +
            "   • Menghapuskan masalah imbasan berulang ('Sedang mengimbas kenalan...') setiap kali bertukar tab atau membuka semula buku telefon.\n" +
            "   • Melaksanakan cache statik proses (sCachedContacts) yang mengekalkan senarai kenalan secara kekal semasa aplikasi berjalan.\n" +
            "   • Membuka skrin Buku Telefon kini memaparkan kenalan serta-merta dalam masa 0 milisaat tanpa sebarang lag atau sela masa.\n" +
            "   • Pra-muat pintar di latar belakang (Background Preload) bermula secara automatik sebaik sahaja aplikasi dibuka.\n" +
            "   • Dilengkapi butang penyegar pantas '🔄' di bar tajuk untuk kemaskini manual jika pengguna menambah kenalan baharu di luar aplikasi.\n\n" +
            "🚀 2. Tatalan Lancar 60/120 FPS (High-Performance Recycled ListView & ViewHolder):\n" +
            "   • Menggantikan ScrollView statik lama kepada senibina Android ListView dengan kitaran semula paparan (View Recycling & BaseAdapter).\n" +
            "   • Penggunaan memori diminimumkan secara drastik—hanya ~12 kad paparan aktif diwujudkan serentak berbanding ribuan kad sebelum ini.\n" +
            "   • Tatalan (scrolling) senarai kenalan kini sangat responsif dan lancar seperti mentega (butter-smooth) tanpa kekakuan atau penurunan kadar bingkai.\n" +
            "   • Mengaktifkan ciri 'Fast Scroll' yang membolehkan pengguna meleret abjad senarai kenalan dengan pantas.\n\n" +
            "🛡️ 3. Penghapusan Pembekuan Thread & Pertanyaan N+1 IPC Disk:\n" +
            "   • Mengoptimumkan pembacaan ContentResolver kepada Single-Pass Query; tiada lagi pertanyaan pangkalan data berganda.\n" +
            "   • Penilaian keselamatan kenalan (Safe Contact & Scammer Threat) dilakukan terus melalui carian indeks memori O(1) tanpa menyekat UI thread."
        );
        tvV1117Content.setTextColor(0xFFE2E8F0);
        tvV1117Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1117Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1117Content, 8);
        cardV1117.addView(tvV1117Content);

        root.addView(cardV1117);

        // ==================== VERSION 1.11.6 ====================
        LinearLayout cardV1116 = createCard(0xFF1E293B, 0xFF10B981, 12);
        cardV1116.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1116, 14);

        LinearLayout v1116Header = createVersionHeader("VERSI 1.11.6", "19 OKTOBER 2026", 0xFF10B981);
        cardV1116.addView(v1116Header);

        TextView tvV1116Content = new TextView(this);
        tvV1116Content.setText(
            "👤 Paparan Nama Pemilik Nombor Peranti & Pemodenan UI Halaman Utama:\n\n" +
            "👤 1. Paparan Lengkap Nama Pemilik Nombor Peranti (True Contact Name Resolution):\n" +
            "   • Menyelesaikan isu nombor kenalan tersimpan dipaparkan tanpa nama pada buku telefon, sejarah panggilan, SMS, dan pendail.\n" +
            "   • Mengintegrasikan pra-muat kenalan latar belakang (preloadDeviceContactsAsync) yang mengindeks semua kenalan telefon (Google, SIM, WhatsApp, peranti) ke dalam memori pantas O(1).\n" +
            "   • Enjin semakan multi-lapisan (Multi-Tier Resolution): Menyokong semakan indeks pantas akhiran nombor (Suffix Index), Android PhoneLookup, dan nama berstruktur ContactsContract.Data (GIVEN_NAME + FAMILY_NAME).\n" +
            "   • Kad buku telefon dan sejarah panggilan kini menonjolkan nama sebenar pemilik nombor dalam teks tebal bersama avatar huruf bulat berpadanan.\n\n" +
            "💎 2. Pembersihan & Pemodenan UI Halaman Utama (Sleek Minimalist Front Page):\n" +
            "   • Membuang kad menu bertindih dengan butang 'BUKA' dan butang komunikasi yang memakan ruang skrin secara berlebihan.\n" +
            "   • Menggantikannya dengan Bar Carian Bersatu Pintar (Unified Sleek Search Bar) berprofil rendah dan moden.\n" +
            "   • Menyokong penapisan masa nyata log panggilan (Real-time Instant Search) apabila menaip nama/nombor, serta butang pintas 'SEMAK' untuk risikan scammer serta-merta.\n" +
            "   • Sejarah panggilan kini terpapar terus di hadapan mata pengguna sebaik sahaja membuka aplikasi tanpa halangan visual."
        );
        tvV1116Content.setTextColor(0xFFE2E8F0);
        tvV1116Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1116Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1116Content, 8);
        cardV1116.addView(tvV1116Content);

        root.addView(cardV1116);

        // ==================== VERSION 1.11.5 ====================
        LinearLayout cardV1115 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV1115.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1115, 14);

        LinearLayout v1115Header = createVersionHeader("VERSI 1.11.5", "19 OKTOBER 2026", 0xFF38BDF8);
        cardV1115.addView(v1115Header);

        TextView tvV1115Content = new TextView(this);
        tvV1115Content.setText(
            "📖 UI Buku Telefon Baharu, Pembaikan Lencana Panggilan Terlepas & Integrasi Komuniti:\n\n" +
            "📖 1. Antara Muka Penuh Buku Telefon Peranti (ContactsActivity & Threat Integration):\n" +
            "   • Membina skrin buku telefon baharu yang memuatkan senarai kenalan secara tak segerak (asynchronous background loading) tanpa sebarang lag (0-stutter).\n" +
            "   • Dilengkapi semakan risikan keselamatan masa nyata dengan KantoiThreatEngine untuk mengesan nombor scammer yang mungkin tersimpan dalam kenalan.\n" +
            "   • Carian pantas (Instant Search) mengikut nama dan nombor telefon serta cip penapis pantas: 'Semua', 'Rakan Sah', dan 'Amaran Scammer'.\n" +
            "   • Butang tindakan segera: Panggil Terus, Hantar SMS, dan Semak Profil Risikan Scammer (Dossier).\n\n" +
            "📍 2. Tiga Titik Akses Pantas Buku Telefon (Universal Easy Access):\n" +
            "   • Halaman Utama: Kad pantas '📖 BUKU TELEFON & KENALAN PERANTI' betul-betul di bawah dok komunikasi utama.\n" +
            "   • Bar Navigasi Bawah: Butang kekal '[📖 Kenalan]' di bar menu navigasi bawah bersebelahan Panggilan, Pad Dail, Mesej, dan Pertahanan.\n" +
            "   • Pad Dail Pintar: Butang capaian segera '[📖 BUKU TEL]' di bar tindakan atas pad pendail.\n\n" +
            "🔴 3. Pembaikan Menyeluruh Lencana Ikon Panggilan Terlepas (Launcher Badge Unread Missed Calls):\n" +
            "   • Mengintegrasikan sokongan multi-pengeluar OEM dalam KantoiBadgeManager (Samsung One UI, Xiaomi HyperOS/MIUI, Huawei EMUI, Oppo ColorOS, Vivo OriginOS, Sony, HTC & pelancar pihak ketiga).\n" +
            "   • Menggunakan saluran notifikasi senyap berkeutamaan sistem (Silent Badge Channel) dengan ikon siluet pematuhan Android (ic_stat_call) agar lencana tidak ditolak oleh OS.\n" +
            "   • Menghapuskan pembersihan lencana pramatang; lencana kini mengekalkan bilangan panggilan terlepas sebenar sehingga pengguna sengaja memadamkannya atau menyemak log.\n" +
            "   • Ditambah sepanduk makluman panggilan terlepas di bahagian atas sejarah panggilan berserta butang 'KOSONGKAN LENCANA'.\n\n" +
            "🤖 4. Integrasi Laporan Komuniti Telegram & Pembersihan Pangkalan Data:\n" +
            "   • Menyokong laporan komuniti melalui bot pintar @crowdcallerbot dengan pengekstrakan teks bebas (Free-form NLP) dan kelulusan Pentadbir (Mod A).\n" +
            "   • Penyiaran automatik ke saluran @crowdcaller dengan tag OTA #KANTOI_UPDATE dan sinkronisasi terus ke pangkalan data SQLite.\n" +
            "   • Pembersihan rekod ujian eksperimen bagi memastikan pangkalan data 100% tepat dan berwibawa."
        );
        tvV1115Content.setTextColor(0xFFE2E8F0);
        tvV1115Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1115Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1115Content, 8);
        cardV1115.addView(tvV1115Content);

        root.addView(cardV1115);

        // ==================== VERSION 1.11.3 ====================
        LinearLayout cardV1113 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV1113.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1113, 14);

        LinearLayout v1113Header = createVersionHeader("VERSI 1.11.3", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV1113.addView(v1113Header);

        TextView tvV1113Content = new TextView(this);
        tvV1113Content.setText(
            "⚡ Pengoptimuman Prestasi & Kelancaran Tatalan Sejarah Panggilan & SMS (Smooth 60/120FPS):\n\n" +
            "🚀 1. Pemaparan Berperingkat Pantas (Progressive Chunked Lazy Batch Rendering):\n" +
            "   • Memecahkan beban pameran serentak 50-60 kad kepada paparan serta-merta 15 item teratas (0ms kependaman), diikuti lampiran rekod seterusnya merentasi kitaran bingkai secara lancar.\n" +
            "   • Menghapuskan sepenuhnya isu pembekuan skrin (UI thread freeze & frame drops) apabila menukar tab atau membuka peti masuk mesej.\n\n" +
            "🧠 2. Cache Memori Kenalan Berkelajuan Tinggi (O(1) In-Memory Contact Cache):\n" +
            "   • Menghapuskan kesesakan N+1 carian ContentResolver ContactsProvider dengan struktur data memori pantas 'mContactNameCache' dan 'mCheckedNonContacts'.\n" +
            "   • Memanfaatkan rekod CACHED_NAME terus daripada sistem Android dan mengecualikan penghantar SMS abjad/pendek (cth: MAYBANK, CIMB, GOV, OTP) daripada kueri disk yang membebankan.\n\n" +
            "📐 3. Perataan Hierarki Susun Atur (Layout Flattening & Double-Measure Elimination):\n" +
            "   • Membuang lapisan kontena berulang pada kad log panggilan dan mengoptimumkan pengukuran teks snippet SMS dengan 'TextUtils.TruncateAt.END'.\n\n" +
            "🏎️ 4. Asynchronous Badge I/O & Pemecutan GPU (Hardware Acceleration):\n" +
            "   • Memindahkan kemaskini pangkalan data lencana notifikasi dan rekod panggilan terlepas ke bebenang latar belakang (background thread).\n" +
            "   • Mengaktifkan pemecutan perkakasan GPU ('hardwareAccelerated=true') dan 'setSmoothScrollingEnabled(true)' untuk pengalaman tatalan licin dan lancar."
        );
        tvV1113Content.setTextColor(0xFFE2E8F0);
        tvV1113Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1113Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1113Content, 8);
        cardV1113.addView(tvV1113Content);

        root.addView(cardV1113);

        // ==================== VERSION 1.11.2 ====================
        LinearLayout cardV1112 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV1112.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1112, 14);

        LinearLayout v1112Header = createVersionHeader("VERSI 1.11.2", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV1112.addView(v1112Header);

        TextView tvV1112Content = new TextView(this);
        tvV1112Content.setText(
            "🛠️ Pembaikan Pepijat Dialog Rakan Selamat & Penyelarasan Versi Dinamik:\n\n" +
            "👥 1. Pembaikan Skrin Hitam Dialog Daftar Rakan Selamat (Dialog Layout Attachment Fix):\n" +
            "   • Menyelesaikan isu pepijat kritikal di mana butang 'Daftar sebagai rakan selamat' memaparkan dialog hitam kosong tanpa teks atau input.\n" +
            "   • Memperbaiki hierarki paparan dengan menyambung root layout terus ke dalam ScrollView dan mengkonfigurasi tetingkap berpenjuru bulat 16dp (lebar 94%, tinggi 88%).\n" +
            "   • Semua borang pendaftaran rakan (Nama, Nombor, Cip Kategori Sahabat/Keluarga/Kerja, Catatan, Butang Simpan & Padam) kini muncul dengan reka bentuk kemas dan jelas.\n\n" +
            "📜 2. Penyelarasan Nombor Versi Bina Dinamik (Dynamic Build Version Sync):\n" +
            "   • Memperbaiki label '📜 CHANGELOG' dan teks kaki utama yang sebelum ini tersekat pada teks lama (v1.7.0).\n" +
            "   • Mengintegrasikan fungsi pembacaan metadata pakej dinamik 'getAppVersionName()' supaya nombor versi di laman utama sentiasa tepat dan selari dengan binaan APK sebenar."
        );
        tvV1112Content.setTextColor(0xFFE2E8F0);
        tvV1112Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1112Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1112Content, 8);
        cardV1112.addView(tvV1112Content);

        root.addView(cardV1112);

        // ==================== VERSION 1.11.1 ====================
        LinearLayout cardV1111 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV1111.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1111, 14);

        LinearLayout v1111Header = createVersionHeader("VERSI 1.11.1", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV1111.addView(v1111Header);

        TextView tvV1111Content = new TextView(this);
        tvV1111Content.setText(
            "🔧 Pembaikan Pepijat Paparan Web Risikan WhatsApp (CheckLeaked Fix & Fallback):\n\n" +
            "🌐 1. Pilihan Terus Buka di Chrome / Pelayar Sistem (Direct Browser Primary Option):\n" +
            "   • Menu WhatsApp Hub kini menyediakan pilihan utama '🌐 BUKA DI CHROME / BROWSER' untuk memintas sekatan Cloudflare Turnstile bot protection yang menghalang paparan WebView biasa.\n" +
            "   • Nombor antarabangsa (+601...) disalin automatik serta-merta ke clipboard apabila ditekan.\n\n" +
            "📐 2. Pembaikan Ukuran Siling Dialog (Dialog Sizing & Weight Fix):\n" +
            "   • Menukar daripada AlertDialog kepada Full Frame Dialog berdimensi tetap (MATCH_PARENT) bagi menyelesaikan isu WebView terlipat (0px height / skrin kosong).\n\n" +
            "🔒 3. Pengendali Keselamatan SSL & Pengabaian Ralat Sijil (SSL Handler Proceed):\n" +
            "   • Menambah kaedah 'onReceivedSslError' (handler.proceed()) bagi mengelakkan sambungan dibatalkan secara senyap akibat isu sijil perantara Cloudflare pada Android.\n\n" +
            "🛡️ 4. Pengesanan Ralat Automatik & Butang Pemulihan (Fallback Error Banner):\n" +
            "   • Jika berlaku sebarang ralat rangkaian atau sekatan Cloudflare, panel amaran mesra pengguna bersama butang pantas '🌐 BUKA TERUS DI PELAYAR CHROME' akan dipaparkan secara automatik."
        );
        tvV1111Content.setTextColor(0xFFE2E8F0);
        tvV1111Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1111Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1111Content, 8);
        cardV1111.addView(tvV1111Content);

        root.addView(cardV1111);

        // ==================== VERSION 1.11.0 ====================
        LinearLayout cardV1110 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV1110.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1110, 14);

        LinearLayout v1110Header = createVersionHeader("VERSI 1.11.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV1110.addView(v1110Header);

        TextView tvV1110Content = new TextView(this);
        tvV1110Content.setText(
            "⚡ Hab Risikan Siber WhatsApp (CheckLeaked OSINT) & Penyatuan GUI Sifar Butang Kembar:\n\n" +
            "📸 1. Enjin Risikan Siber Profil & DP WhatsApp (CheckLeaked CC Integration):\n" +
            "   • Integrasi penuh enjin risikan https://whatsapp.checkleaked.cc/ terus di dalam KANTOI.\n" +
            "   • Membolehkan pengguna menyiasat gambar profil (DP) resolusi tinggi, status bio, dan rekod kebocoran data pangkalan data tanpa perlu menambah nombor ke buku telefon.\n" +
            "   • Format antarabangsa (+60...) diselaraskan secara automatik dan disalin terus ke papan keratan (clipboard) dengan pemberitahuan HUD.\n\n" +
            "🎯 2. Seni Bina GUI Taktikal Sifar Butang Berkembar (Zero Duplicate Buttons):\n" +
            "   • Menghapuskan kekeliruan butang berganda antara sembang terus WhatsApp dan carian risikan OSINT.\n" +
            "   • Naik taraf butang ke-5 di Ringkasan Selepas Panggilan (After-Call) kepada '💬 WHATSAPP HUB' yang membuka lembaran tindakan taktikal dwifungsi: [Mod Risikan OSINT] vs [Mod Sembang Terus wa.me].\n" +
            "   • Susun atur grid 2-kolum x 3-baris kekal seimbang, simetri, dan profesional tanpa sebarang lambakan butang.\n\n" +
            "🛡️ 3. Matriks 4 Tiang Risikan Siber & OSINT (Contact Profile Upgrade):\n" +
            "   • Skrin Profil Kenalan kini dilengkapi panel matriks risikan lengkap:\n" +
            "     - Tiang 1: 🏛️ PDRM SemakMule CCID (Statistik carian & rekod laporan polis)\n" +
            "     - Tiang 2: 📸 WhatsApp DP & Data Leaks (CheckLeaked OSINT)\n" +
            "     - Tiang 3: 🌐 Carian Forum & Media Sosial (Google CSE Cyber Intel)\n" +
            "     - Tiang 4: 👥 Pangkalan Data Ancaman Komuniti KANTOI (3,654 rekod luar talian)\n\n" +
            "💻 4. Paparan HUD WebView Taktikal Dalam Aplikasi (In-App Tactical HUD):\n" +
            "   • Dialog WebView bertema gelap (Tactical Dark Slate) dengan bar indikator proses muat turun, kawalan muat semula (reload), butang salin semula nombor, dan sandaran pelayar luar."
        );
        tvV1110Content.setTextColor(0xFFE2E8F0);
        tvV1110Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1110Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1110Content, 8);
        cardV1110.addView(tvV1110Content);

        root.addView(cardV1110);

        // ==================== VERSION 1.10.0 ====================
        LinearLayout cardV1100 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV1100.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV1100, 14);

        LinearLayout v1100Header = createVersionHeader("VERSI 1.10.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV1100.addView(v1100Header);

        TextView tvV1100Content = new TextView(this);
        tvV1100Content.setText(
            "👥 Pengasingan Pendaftaran Rakan Selamat (Whitelist) & Senarai Scammer (Blacklist):\n\n" +
            "🛡️ 1. Pangkalan Data Rakan Selamat KANTOI (Dedicated Whitelist DB):\n" +
            "   • Struktur jadual khas 'custom_safe_contacts' diwujudkan untuk menyimpan rekod kenalan selamat secara setempat tanpa bergantung kepada buku telefon Android sahaja.\n" +
            "   • Pengkategorian pintar: Sahabat, Keluarga, Kerja / Rakan Sekerja, Perniagaan / Pelanggan, Rider / Kurier, dan Lain-lain berserta catatan peribadi.\n\n" +
            "🔒 2. Perlindungan Saling Eksklusif (Mutual Exclusivity Engine):\n" +
            "   • Mendaftarkan nombor sebagai Rakan Selamat akan MEMADAM dan membersihkan nombor daripada rekod scammer secara automatik.\n" +
            "   • Panggilan daripada rakan selamat dijamin 100% bebas daripada amaran scammer, siren kecemasan, atau ikon tengkorak merah.\n" +
            "   • Notis amaran telus sekiranya nombor yang ingin didaftarkan pernah mempunyai rekod terdahulu.\n\n" +
            "🎨 3. Antara Muka Pengurusan Profil Berasingan & Kontras Tinggi (Distinct UI Actions):\n" +
            "   • Pada Skrin Profil Kenalan, tindakan pendaftaran diasingkan kepada 2 butang bebas:\n" +
            "     - [👥 DAFTAR / KEMASKINI SEBAGAI RAKAN SELAMAT] (Hijau Zamrud #059669)\n" +
            "     - [🚨 DAFTAR / LAPOR SEBAGAI SCAMMER] (Merah Menyala #DC2626)\n" +
            "     - [👤 EKSPORT KE BUKU TELEFON ANDROID] (Kelabu Gelap #1E293B)\n" +
            "   • Paparan Lencana Hero tersuai: Menunjukkan lencana hijau '👥 RAKAN KANTOI: [KATEGORI] • DISAHKAN SELAMAT'.\n\n" +
            "⚡ 4. Integrasi Dialog Selepas Panggilan & Skrin Carian Utama:\n" +
            "   • Menu Selepas Panggilan (After-Call Dialog) kini membolehkan pengguna memilih untuk menyimpan nombor sebagai Rakan Selamat KANTOI atau mengeksport ke buku telefon Android.\n" +
            "   • Tab Alat & Pertahanan menyediakan butang pantas pendaftaran rakan selamat dan scammer secara berasingan."
        );
        tvV1100Content.setTextColor(0xFFE2E8F0);
        tvV1100Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV1100Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV1100Content, 8);
        cardV1100.addView(tvV1100Content);

        root.addView(cardV1100);

        // ==================== VERSION 1.9.1 ====================
        LinearLayout cardV191 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV191.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV191, 14);

        LinearLayout v191Header = createVersionHeader("VERSI 1.9.1", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV191.addView(v191Header);

        TextView tvV191Content = new TextView(this);
        tvV191Content.setText(
            "📐 Penambahbaikan Ergonomik & Pengemasan Butang Dialog Selepas Panggilan (After-Call UI Polish):\n\n" +
            "✨ 1. Penjarakan Grid Butang Lebih Selesa & Berjarak (14dp Uniform Spacing):\n" +
            "   • Jarak mendatar dan menegak antara 6 butang tindakan pantas diperluaskan dari 12dp ke 14dp, menghapuskan masalah butang rapat atau bertindih secara visual.\n\n" +
            "🎯 2. Dimensi Butang Bertaraf Dunia (World-Class Touch Target):\n" +
            "   • Ketinggian butang diselaraskan ke 48dp (standard Material Design touch target) dengan bucu bulat moden 12dp.\n" +
            "   • Garisan sempadan aksen berdefinisi tinggi (1dp stroke) bagi setiap kategori tindakan untuk pemisahan visual yang jelas dan kemas.\n\n" +
            "🔤 3. Tipografi Bersih & Ruang Nafas (Breathing Room):\n" +
            "   • Saiz fon teks diselaraskan ke 12sp bold dengan padding dalaman seimbang, mengelakkan teks daripada menghimpit bucu butang pada pelbagai saiz skrin telefon.\n\n" +
            "🖼️ 4. Bingkai Terapung Kemas (Floating Inset Padding):\n" +
            "   • Kad ringkasan kini mempunyai inset terapung 14dp daripada bingkai skrin telefon, memberikan paparan modal yang elegan dan profesional."
        );
        tvV191Content.setTextColor(0xFFE2E8F0);
        tvV191Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV191Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV191Content, 8);
        cardV191.addView(tvV191Content);

        root.addView(cardV191);

        // ==================== VERSION 1.9.0 ====================
        LinearLayout cardV19 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV19.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV19, 14);

        LinearLayout v19Header = createVersionHeader("VERSI 1.9.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV19.addView(v19Header);

        TextView tvV19Content = new TextView(this);
        tvV19Content.setText(
            "🗑️ Pengurusan & Pemadaman Fleksibel Sejarah Panggilan dan Mesej SMS:\n\n" +
            "📞 1. Pemadaman Sejarah Panggilan (Call Logs Deletion):\n" +
            "   • Butang [🗑️ PILIH] untuk mengaktifkan mod pemilihan berbilang (Multi-Select Batch Mode).\n" +
            "   • Butang [✓ SEMUA / NYAH] untuk memilih atau menyahpilih semua rekod panggilan serentak.\n" +
            "   • Padam rekod terpilih dengan satu sentuhan berserta dialog pengesahan keselamatan.\n" +
            "   • Padam rekod panggilan tunggal secara terus melalui menu tindakan pop-up panggilan.\n\n" +
            "💬 2. Pemadaman Peti Masuk Mesej SMS (SMS Messages Deletion):\n" +
            "   • Mod pemilihan pintar untuk memilih dan memadam mesej SMS biasa mahupun SMS phishing.\n" +
            "   • Ciri [✓ SEMUA] dan kiraan bilangan mesej dipilih secara langsung di bar alat atas.\n" +
            "   • Sistem perlindungan dwilapis: Pemadaman penyedia sistem rasmi dan sandaran penapis peribadi KANTOI (Zero-Lag Local Filter) yang menjamin mesej lenyap serta-merta.\n" +
            "   • Padam mesej SMS tunggal dengan selamat melalui dialog butiran SMS."
        );
        tvV19Content.setTextColor(0xFFE2E8F0);
        tvV19Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV19Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV19Content, 8);
        cardV19.addView(tvV19Content);

        root.addView(cardV19);

        // ==================== VERSION 1.8.0 ====================
        LinearLayout cardV18 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV18.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV18, 14);

        LinearLayout v18Header = createVersionHeader("VERSI 1.8.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV18.addView(v18Header);

        TextView tvV18Content = new TextView(this);
        tvV18Content.setText(
            "🛡️ Lencana Notifikasi Ikon Launcher & Radar Ancaman WhatsApp (Disebalik Tabir):\n\n" +
            "🔴 1. Lencana Notifikasi Kecil pada Ikon Pelancar (Launcher App Icon Badging):\n" +
            "   • Paparan bilangan panggilan terlepas (Missed Calls Badge) terus pada ikon aplikasi KANTOI di skrin utama telefon.\n" +
            "   • Menyokong standard rasmi Android 8.0+ (NotificationChannel setShowBadge) dan pelancar pengeluar (Samsung One UI, Xiaomi HyperOS, Sony Xperia, Nova Launcher).\n" +
            "   • Lencana dibersihkan secara automatik sebaik sahaja pengguna membuka senarai panggilan KANTOI.\n\n" +
            "🟢 2. Imbasan Senyap Kenalan WhatsApp (WhatsApp Threat Radar):\n" +
            "   • Mengimbas semua nombor yang disegerakkan oleh WhatsApp ke dalam Android Contacts Provider secara senyap disebalik tabir.\n" +
            "   • Memadankan setiap nombor WhatsApp dengan 3,654+ rekod pangkalan data scammer PDRM, SemakMule, dan kes penipuan tempatan.\n" +
            "   • Memaparkan jumlah kenalan yang disahkan bersih serta memberi amaran segera di tab Pertahanan sekiranya terdapat nombor scammer tersimpan dalam WhatsApp.\n" +
            "   • Dialog senarai ancaman terperinci dengan nama WhatsApp, nombor telefon, kategori risiko, dan butang pintasan Profil Risiko."
        );
        tvV18Content.setTextColor(0xFFE2E8F0);
        tvV18Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV18Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV18Content, 8);
        cardV18.addView(tvV18Content);

        root.addView(cardV18);

        // ==================== VERSION 1.7.1 ====================
        LinearLayout cardV171 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV171.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV171, 14);

        LinearLayout v171Header = createVersionHeader("VERSI 1.7.1", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV171.addView(v171Header);

        TextView tvV171Content = new TextView(this);
        tvV171Content.setText(
            "📐 Pelarasan Ergonomik Pendail Pintar & Maklum Balas Sentuhan (Smart Dialer UI Refinements):\n\n" +
            "👇 1. Penurunan Pad Dail ke Zon Capaian Ibu Jari (Lower Thumb Zone):\n" +
            "   • Pad kekunci dail diturunkan ke bahagian bawah skrin menggunakan pembahagi fleksibel (Adaptive Weight Spacers).\n" +
            "   • Tidak lagi terlalu tinggi ke siling skrin, memudahkan pengguna menaip nombor dengan sebelah tangan.\n\n" +
            "↔️ 2. Penjarakan Butang Dail Luas & Selesa (Spacious Grid Layout):\n" +
            "   • Jarak menegak antara baris butang dinaikkan daripada 10dp kepada 18dp (+80%).\n" +
            "   • Jarak mendatar antara setiap butang dinaikkan daripada 20dp kepada 30dp (+50%) untuk mengelakkan salah tekan digit bersebelahan.\n\n" +
            "✨ 3. Paparan Input Nombor & Lencana Scammer Seimbang:\n" +
            "   • Kad paparan nombor direka bentuk semula dengan bucu bulat 16dp dan saiz teks 28sp bold yang jelas.\n" +
            "   • Ruang atas (top spacing) diseimbangkan supaya paparan tidak melekat pada bar tajuk.\n\n" +
            "🌊 4. Maklum Balas Visual Sentuhan (Tactile Ripple Feedback):\n" +
            "   • Setiap butang pad kekunci dan butang panggilan kini dilengkapi animasi gelombang Ripple (RippleDrawable) serta-merta apabila ditekan."
        );
        tvV171Content.setTextColor(0xFFE2E8F0);
        tvV171Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV171Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV171Content, 8);
        cardV171.addView(tvV171Content);

        root.addView(cardV171);

        // ==================== VERSION 1.7.0 ====================
        LinearLayout cardV17 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV17.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV17, 14);

        LinearLayout v17Header = createVersionHeader("VERSI 1.7.0", "18 OKTOBER 2026", 0xFF10B981);
        cardV17.addView(v17Header);

        TextView tvV17Content = new TextView(this);
        tvV17Content.setText(
            "🌟 Naik Taraf Pengalaman Pengguna (UX) Bertaraf Dunia & Sejarah Panggilan:\n\n" +
            "📞 1. Sejarah Log Panggilan Pintar (Call Logs History):\n" +
            "   • Senarai panggilan terkini (Masuk ↙, Keluar ↗, Terlepas ↙) dengan durasi masa dan tarikh terperinci.\n" +
            "   • Pengecaman automatik status ancaman scammer pada setiap rekod dengan lencana amaran segera.\n" +
            "   • Bar carian pintar (Search) untuk menapis nama atau nombor serta butang pintas profil/panggilan.\n\n" +
            "🔴 2. Skrin Panggilan Berlangsung (Active In-Call Screen):\n" +
            "   • Antaramuka panggilan penuh dengan butang merah bulat besar 'Tamatkan Panggilan' (76dp) yang jelas dan mudah dicapai.\n" +
            "   • Kawalan audio bersepadu: Butang Bisu (Mute), Pembesar Suara (Speaker), Pad DTMF, dan Pintasan SMS pantas.\n" +
            "   • Pemasa langsung durasi panggilan (Live Chronometer) dan paparan avatar pemanggil.\n\n" +
            "🧭 3. Navigasi Bawah 4-Tab Standard Industri (Bottom Navigation Bar):\n" +
            "   • Susun atur mesra pengguna: Panggilan (Calls), Pad Dail (Keypad), Mesej (SMS), dan Pertahanan (Shield).\n" +
            "   • Menghapuskan kekusutan menu bertingkat demi kemudahan navigasi pengguna biasa.\n\n" +
            "📐 4. Dialog Ringkasan Selepas Panggilan Lebih Luas & Jelas:\n" +
            "   • Saiz butang diperbesarkan kepada 52dp dengan teks tebal 13sp dan sudut bulat moden.\n" +
            "   • Jarak antara menu (spacing & margin) dinaikkan kepada 12dp untuk mengelakkan salah tekan."
        );
        tvV17Content.setTextColor(0xFFE2E8F0);
        tvV17Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV17Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV17Content, 8);
        cardV17.addView(tvV17Content);

        root.addView(cardV17);

        // ==================== VERSION 1.6.0 ====================
        LinearLayout cardV16 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV16.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV16, 14);

        LinearLayout v16Header = createVersionHeader("VERSI 1.6.0", "18 OKTOBER 2026", 0xFF0284C7);
        cardV16.addView(v16Header);

        TextView tvV16Content = new TextView(this);
        tvV16Content.setText(
            "🚀 Naik Taraf Komunikasi Penuh & Pembaikan GUI Profesional:\n\n" +
            "📞 1. Pendail Pintar Binaan Dalam (Smart Dialer):\n" +
            "   • Pad kekunci dail 0-9, *, # dengan respons getaran haptik dan butang padam pantas.\n" +
            "   • Pengesanan scammer masa nyata (0ms) semasa menaip digit nombor dengan lencana status segera.\n" +
            "   • Butang panggilan pantas (Call) dan integrasi terus ke sistem telefon lalai Android.\n\n" +
            "💬 2. Peti Masuk & Pengarang SMS (SMS Reader & Composer):\n" +
            "   • Membaca peti masuk SMS peranti dengan penapisan automatik pancingan data (Phishing), pautan trojan APK, dan domain risiko tinggi.\n" +
            "   • Mengarang dan menghantar mesej SMS selamat terus dalam aplikasi berserta pengira aksara.\n" +
            "   • Pengesanan ancaman masa nyata pada nombor penerima dan analisis keselamatan teks.\n\n" +
            "✨ 3. Pembaikan Menyeluruh GUI Panggilan Masuk (Anti-Bertindih):\n" +
            "   • Menghapuskan sepenuhnya menu WindowManager terapung yang bertindih di atas skrin panggilan.\n" +
            "   • Reka bentuk baharu yang kemas, moden, luas, dan bebas gangguan teks panjang.\n" +
            "   • Dua butang bulat ikonik yang besar: Tolak (Merah 72dp) dan Jawab (Hijau 72dp) untuk operasi pantas.\n\n" +
            "⚡ 4. Dok Pintas Komunikasi di Laman Utama:\n" +
            "   • Akses 1-klik pantas ke Pendail Pintar dan Mesej SMS terus dari bahagian atas skrin utama."
        );
        tvV16Content.setTextColor(0xFFE2E8F0);
        tvV16Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV16Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV16Content, 8);
        cardV16.addView(tvV16Content);

        root.addView(cardV16);

        // ==================== VERSION 1.5.0 ====================
        LinearLayout cardV15 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV15.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV15, 14);

        LinearLayout v15Header = createVersionHeader("VERSI 1.5.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV15.addView(v15Header);

        TextView tvV15Content = new TextView(this);
        tvV15Content.setText(
            "🚀 Integrasi Penuh Aplikasi Telefon Lalai (Default Phone App Engine):\n\n" +
            "👑 1. Sokongan Penuh InCallService (Android Telecom Subsystem):\n" +
            "   • KANTOI kini menyokong peranan Default Dialer (ROLE_DIALER) sepenuhnya mengikut piawaian rasmi Android Telecom.\n" +
            "   • Menangani panggilan masuk dan tamat panggilan secara automatik terus ke sistem OS tanpa bergantung kepada aplikasi luar.\n\n" +
            "🔔 2. Paparan Skrin Kunci & Notifikasi Keutamaan Tinggi:\n" +
            "   • Integrasi USE_FULL_SCREEN_INTENT dan Heads-Up Notification (NotificationChannel IMPORTANCE_HIGH).\n" +
            "   • Memastikan skrin panggilan masuk (In-Call UI) tetap menyala dan terpapar walaupun telefon terkunci atau skrin dimatikan.\n\n" +
            "🛡️ 3. Pengendalian 3 Fasa Panggilan Lengkap:\n" +
            "   • Fasa 1 (Akan Masuk): Penapisan pantas CallScreeningService sebelum telefon berdering.\n" +
            "   • Fasa 2 (Sedang Masuk): Antaramuka InCallService & amaran ancaman scammer secara visual.\n" +
            "   • Fasa 3 (Selepas Masuk): Dialog ringkasan tamat panggilan AfterCallActivity dengan 6 butang tindakan segera.\n\n" +
            "⚙️ 4. Pengurus Sistem Panggilan Berpusat (Call System Manager):\n" +
            "   • Pengesanan status dan pengaktifan 1-klik untuk 3 tonggak: Default Phone App, Paparan Atas Skrin (Overlay), dan Call Screening."
        );
        tvV15Content.setTextColor(0xFFE2E8F0);
        tvV15Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV15Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV15Content, 8);
        cardV15.addView(tvV15Content);

        root.addView(cardV15);

        // ==================== VERSION 1.4.0 ====================
        LinearLayout cardV14 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV14.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV14, 14);

        LinearLayout v14Header = createVersionHeader("VERSI 1.4.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV14.addView(v14Header);

        TextView tvV14Content = new TextView(this);
        tvV14Content.setText(
            "✨ Ciri-Ciri Baharu & Penambahbaikan GUI Panggilan:\n\n" +
            "📞 1. GUI Semasa Menerima Panggilan (In-Call Screen):\n" +
            "   • Skrin Caller ID penuh bercirikan keselamatan moden (Stealth Dark #0B0F19).\n" +
            "   • Lencana ancaman dinamik (Merah untuk scammer disahkan, Hijau untuk kenalan selamat, Biru untuk nombor asing).\n" +
            "   • Paparan butiran suspek, akaun bank keldai, statistik SemakMule CCID PDRM, serta butang: Tolak & Sekat, Jawab, dan Buka Dossier.\n\n" +
            "📋 2. GUI Selepas Menerima Panggilan (After-Call Summary Dialog):\n" +
            "   • Kad dialog terapung automatik sejurus panggilan ditamatkan.\n" +
            "   • 6 tindakan pantas sepantas kilat: Buka Profil Lengkap, Sekat/Lapor Scammer, SemakMule CCID, Google CSE, Mesej WhatsApp, dan Simpan Kenalan.\n\n" +
            "👤 3. GUI Memeriksa Profil Kenalan (Contact Detail & Threat Profile):\n" +
            "   • Skrin profil lengkap dengan avatar status cincin warna dan salin nombor 1-klik.\n" +
            "   • Bar komunikasi pantas: Panggil, WhatsApp terus (wa.me), SMS, dan Google CSE.\n" +
            "   • Dossier risikan penuh (Nama suspek, kategori jenayah, akaun bank keldai, IC, alamat, pautan bukti) & statistik SemakMule CCID secara langsung.\n\n" +
            "📱 4. Mod Pratonton GUI di Laman Utama:\n" +
            "   • Membolehkan pengguna menguji dan melihat sendiri ketiga-tiga antaramuka GUI baharu dengan 1-klik tanpa perlu menunggu panggilan sebenar."
        );
        tvV14Content.setTextColor(0xFFCBD5E1);
        tvV14Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV14Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV14Content, 8);
        cardV14.addView(tvV14Content);

        root.addView(cardV14);

        // ==================== VERSION 1.3.0 ====================
        LinearLayout cardV13 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV13.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV13, 14);

        LinearLayout v13Header = createVersionHeader("VERSI 1.3.0", "18 OKTOBER 2026", 0xFF38BDF8);
        cardV13.addView(v13Header);

        TextView tvV13Content = new TextView(this);
        tvV13Content.setText(
            "✨ Ciri-Ciri Baharu & Penambahbaikan:\n\n" +
            "🔄 1. Kemaskini Pangkalan Data Atas Talian (Telegram OTA Sync):\n" +
            "   • Segerakkan senarai scammer baharu terus dari saluran Telegram rasmi @crowdcaller dengan 1-klik.\n" +
            "   • 100% Bebas Kos Pelayan (Zero Webhost Cost) menggunakan infrastruktur Telegram CDN pantas.\n" +
            "   • Admin hanya perlu hantar teks nombor scammer di Telegram dan pengguna terus dapat data terkini.\n\n" +
            "⚡ 2. Penambahbaikan Prestasi Enjin SQLite:\n" +
            "   • Pemuatan pantas data dalam transaksi atomik untuk memastikan tiada gangguan semasa panggilan masuk."
        );
        tvV13Content.setTextColor(0xFFE2E8F0);
        tvV13Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV13Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV13Content, 8);
        cardV13.addView(tvV13Content);

        root.addView(cardV13);

        // ==================== VERSION 1.2.0 ====================
        LinearLayout cardV12 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV12.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV12, 14);

        LinearLayout v12Header = createVersionHeader("VERSI 1.2.0", "18 OKTOBER 2026", 0xFF6366F1);
        cardV12.addView(v12Header);

        TextView tvV12Content = new TextView(this);
        tvV12Content.setText(
            "✨ Ciri-Ciri Baharu & Penambahbaikan:\n\n" +
            "👴 1. Mod Lindungi Warga Emas (Strict Protection Mode):\n" +
            "   • Sekat automatik semua panggilan daripada nombor tidak dikenali / nombor asing.\n" +
            "   • Hanya nombor dalam buku telefon dibenarkan berdering untuk melindungi ibu bapa daripada Macau Scam & ugutan mahkamah.\n\n" +
            "📋 2. Pengesan Pintar Papan Keratan (Clipboard Mule Sniffer):\n" +
            "   • Mengesan nombor akaun bank (10-16 digit) dan nombor telefon yang disalin di WhatsApp/Facebook/Telegram.\n" +
            "   • Memberi amaran merah serta-merta jika akaun keldai dikesan, atau butang 1-klik SemakMule CCID.\n\n" +
            "📜 3. Halaman Log Perubahan Rasmi (Changelog):\n" +
            "   • Memudahkan pengguna menyemak setiap naik taraf fungsi dan keselamatan terus dalam aplikasi."
        );
        tvV12Content.setTextColor(0xFFE2E8F0);
        tvV12Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV12Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV12Content, 8);
        cardV12.addView(tvV12Content);

        root.addView(cardV12);

        // ==================== VERSION 1.1.0 ====================
        LinearLayout cardV11 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV11.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV11, 14);

        LinearLayout v11Header = createVersionHeader("VERSI 1.1.0", "18 OKTOBER 2026", 0xFF059669);
        cardV11.addView(v11Header);

        TextView tvV11Content = new TextView(this);
        tvV11Content.setText(
            "✨ Ciri-Ciri Baharu:\n\n" +
            "📱 1. Audit & Imbasan Buku Telefon (Safe Whitelist):\n" +
            "   • Mengimbas buku telefon pengguna secara 100% luar talian untuk mengesan scammer tersimpan.\n" +
            "   • Membina senarai putih (Safe Whitelist) automatik supaya panggilan keluarga tidak disekat.\n\n" +
            "💬 2. Perisai SMS Scam & Pautan Phishing:\n" +
            "   • Menapis SMS masuk secara masa nyata daripada cubaan pancingan data (Phishing).\n" +
            "   • Mengesan pautan muat turun APK trojan berbahaya (.apk, Mediafire) dan domain risiko tinggi (.xyz, .top, bit.ly).\n" +
            "   • Mengesan penyamaran LHDN, PDRM, JPJ, KWSP, dan bank-bank Malaysia."
        );
        tvV11Content.setTextColor(0xFFCBD5E1);
        tvV11Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvV11Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV11Content, 8);
        cardV11.addView(tvV11Content);

        root.addView(cardV11);

        // ==================== VERSION 1.0.0 ====================
        LinearLayout cardV10 = createCard(0xFF1E293B, 0xFF334155, 12);
        cardV10.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        setMarginTop(cardV10, 14);

        LinearLayout v10Header = createVersionHeader("VERSI 1.0.0 (PELANCARAN AWAL)", "18 OKTOBER 2026", 0xFF64748B);
        cardV10.addView(v10Header);

        TextView tvV10Content = new TextView(this);
        tvV10Content.setText(
            "🚀 Pelancaran Rasmi Aplikasi KANTOI (Standalone Anti-Scam):\n\n" +
            "🛡️ • Pangkalan data luar talian mengandungi 3,654 rekod scammer disahkan dan 428 akaun bank keldai.\n" +
            "🔒 • 100% Bebas Iklan, 100% Milik Tempatan, Tanpa Langganan Berbayar, Tanpa Sedutan Data Kenalan (Zero Cloud Upload).\n" +
            "📞 • Integrasi CallScreeningService rasmi Android 10+ (Mod Auto-Sekat / Dering Amaran).\n" +
            "🔍 • Semakan pantas nombor telefon offline 0ms.\n" +
            "🌐 • Carian Peribadi Google CSE Dalam Aplikasi (cx=53a0041f2f24f4e3b).\n" +
            "🏛️ • Integrasi Langsung Portal SemakMule CCID PDRM.\n" +
            "➕ • Modul Lapor & Daftar Scammer Baharu Komuniti."
        );
        tvV10Content.setTextColor(0xFF94A3B8);
        tvV10Content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvV10Content.setLineSpacing(dpToPx(2), 1.1f);
        setMarginTop(tvV10Content, 8);
        cardV10.addView(tvV10Content);

        root.addView(cardV10);

        // Footer
        TextView tvFooter = new TextView(this);
        tvFooter.setText("KANTOI • Komuniti Pertahanan Anti-Scam Rakyat Malaysia");
        tvFooter.setTextColor(0xFF475569);
        tvFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvFooter.setGravity(Gravity.CENTER);
        setMarginTop(tvFooter, 24);
        root.addView(tvFooter);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private LinearLayout createVersionHeader(String versionTitle, String date, int badgeColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(" " + versionTitle + " ");
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(badgeColor);
        bg.setCornerRadius(dpToPx(4));
        tvBadge.setBackground(bg);
        tvBadge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
        row.addView(tvBadge);

        TextView tvDate = new TextView(this);
        tvDate.setText("  " + date);
        tvDate.setTextColor(0xFF94A3B8);
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        row.addView(tvDate);

        return row;
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
