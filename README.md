# 🛡️ KANTOI: Sistem Pertahanan Siber, Pengenal Pemanggil & Anti-Trojan Android

<p align="center">
  <a href="https://github.com/kand4/kantoi/releases/latest"><img src="https://img.shields.io/badge/📥_Muat_Turun_APK-v1.13.10_Release-success?style=for-the-badge&logo=android&logoColor=white" height="36"/></a>
  &nbsp;&nbsp;
  <a href="https://t.me/crowdcaller"><img src="https://img.shields.io/badge/💬_Saluran_Telegram-@crowdcaller-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" height="36"/></a>
  &nbsp;&nbsp;
  <a href="https://t.me/crowdcallerbot"><img src="https://img.shields.io/badge/🤖_Bot_Telegram-@crowdcallerbot-blue?style=for-the-badge&logo=telegram&logoColor=white" height="36"/></a>
</p>

<p align="center">
  <a href="https://github.com/kand4/kantoi/releases"><img src="https://img.shields.io/badge/Versi-v1.13.10-emerald?style=flat-square&logo=android" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-8.0_hingga_12L_(API_26--32)-sky?style=flat-square&logo=android" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/Lesen-GPL--3.0-blue?style=flat-square" /></a>
</p>

**KANTOI** ialah aplikasi pertahanan siber mudah alih berprestasi tinggi (*zero-lag architecture*) yang direka khas untuk melindungi rakyat Malaysia daripada ancaman panggilan penipuan (*scammer*), skim akaun keldai (*mule accounts*), pancingan data SMS (*phishing*), dan trojan pencuri OTP/TAC perbankan (*banking malware* seperti MaliBot & Godfather).

---

## 🚀 Ciri-Ciri Utama (Key Features)

### 1. 📞 Pengenal Pemanggil & Penapis Spam Masa Nyata (Real-Time Caller ID)
- Beroperasi sebagai **Aplikasi Telefon Lalai (Default Dialer / InCallService)** yang sah mengikut garis panduan sistem Android AOSP.
- Mengesan dan menyekat panggilan masuk daripada sindiket Macau Scam, Ah Long, panggilan robotik (*robocall*), dan ejen pengutip hutang haram.
- Antaramuka panggilan masuk, panggilan aktif, dan skrin tamat panggilan (*after-call*) berkadar segar semula **60 / 120 FPS tanpa sebarang sela masa (Zero-Lag)**.

### 2. 🏛️ Pangkalan Data Risikan Jenayah Kewangan PDRM (CCID SemakMule)
- Dilengkapi lebih **45,000+ rekod ancaman luar talian** yang diselaraskan dengan pangkalan data Jabatan Siasatan Jenayah Komersial (JSJK) PDRM.
- Pengguna boleh membuat carian nombor telefon dan nombor akaun bank secara setempat tanpa sambungan internet (0ms kependaman).

### 3. ⚡ Enjin Heuristik Trojan Perbankan Tempatan (0ms Offline, Zero-Data)
- Mengesan formula kebenaran berniat jahat APK scam yang popular di Malaysia (contoh: kad kahwin digital palsu, saman JPJ palsu, pakej pembersihan rumah):
  > 🚨 **Formula Heuristik Trojan:**  
  > `BIND_ACCESSIBILITY_SERVICE` + (`RECEIVE_SMS` atau `READ_SMS`) ➔ **CRITICAL BANKING TROJAN**
- Berfungsi sepenuhnya di dalam peranti secara luar talian tanpa menghantar maklumat pengguna ke mana-mana pelayan luar.

### 4. 🌐 Integrasi VirusTotal API v3 (70+ Enjin Antivirus Global) & Hibrid BYOK
- **Hash-Only Lookup:** Menjana hash SHA-256 fail APK secara tempatan dan menyemak reputasi kebersihan terhadap 70+ makmal antivirus dunia (Kaspersky, Avast, Bitdefender, Google dll.) tanpa memuat naik fail mentah.
- **Kunci Komuniti Bawaan (Multi-Layer Obfuscated):** Pengguna baharu boleh terus menikmati semakan tanpa sebarang tetapan manual.
- **Model Hibrid BYOK (Bring-Your-Own-Key):** Pengguna boleh memasukkan Kunci API peribadi sendiri di tab Tetapan Pertahanan untuk kuota eksklusif 500 semakan/hari.

### 5. 📥 Penjaga Muat Turun Latar Belakang (Real-Time Download Sentry)
- Menggunakan `FileObserver` natif Linux pada folder `/storage/emulated/0/Download/`.
- Memantau muat turun fail `.apk` baru dari WhatsApp, Telegram, Facebook, Threads, atau Chrome, dan memaparkan amaran bahaya keutamaan tinggi di bar notifikasi sebelum mangsa sempat memasang aplikasi tersebut.

### 6. 📲 Sasaran Perkongsian Sistem ('Kongsi ke KANTOI' Share Target)
- Menyokong integrasi `ACTION_SEND` dan `ACTION_VIEW` sistem Android.
- Pengguna boleh menekan *'Share / Kongsi'* daripada mana-mana perbualan WhatsApp atau SMS terus kepada KANTOI untuk menganalisis pautan web atau fail APK yang mencurigakan.

### 7. 🏛️ Hab Keselamatan Kapsul Terapung (Retractable Floating Pill)
- Kapsul terapung sisi yang boleh disorok, dilaras ketinggiannya (*draggable*), dan auto-tutup.
- Membolehkan semakan pantas ke portal SemakMule CCID PDRM, Pengimbas APK, dan Pengimbas URL tanpa mengganggu aliran navigasi utama.

---

## 🏗️ Senibina Aplikasi (Architecture)

```
TrueCaller/
├── KantoiApp/                     # Kod Sumber Projek Utama Android
│   ├── AndroidManifest.xml        # Konfigurasi Kebenaran & Komponen Sistem
│   ├── assets/
│   │   └── kantoi_threats.db      # Pangkalan Data SQLite (>45k Rekod Ancaman)
│   ├── res/                       # Antaramuka XML, Ikon Vektor & Warna
│   └── src/com/kantoi/app/
│       ├── MainActivity.java           # Hab Utama (Panggilan, Kenalan, Pad Dail, Pertahanan)
│       ├── KantoiFloatingHudService.java # Gelembung Terapung Pantas (1-Tap Floating Threat HUD)
│       ├── KantoiThreatEngine.java     # Enjin Pemadanan Ancaman PDRM & Kapsul Terapung
│       ├── KantoiVirusTotalEngine.java # Enjin Heuristik Trojan & VirusTotal API v3
│       ├── KantoiDownloadSentry.java   # Penjaga Muat Turun Latar Belakang (/Download)
│       ├── KantoiShareActivity.java    # Sistem Kongsi Pintar dari WhatsApp/Telegram
│       ├── KantoiWhatsAppScanner.java  # Pengimbas Senyap Kenalan WhatsApp
│       ├── KantoiInCallService.java    # Pengendali Panggilan Masuk & Keluar Natif
│       ├── IncomingCallActivity.java   # Paparan Amaran Radar Panggilan Masuk
│       ├── ActiveCallActivity.java     # Skrin Panggilan Sedang Berlangsung
│       ├── AfterCallActivity.java      # Skrin Laporan Selepas Panggilan
│       ├── SmsActivity.java            # Perisai Saringan Mesej SMS Phishing
│       └── ChangelogActivity.java      # Rekod Log Perubahan Versi Aplikasi
├── build_kantoi.py                # Skrip Kompilasi & Tandatangan APK Bebas
├── post_changelog_and_apk.py      # Skrip Siaran Log Perubahan & Muat Naik Telegram
├── .gitignore                     # Penapis Fail Binari & Repositori Git
├── LICENSE                        # Lesen Perisian Bebas GNU GPL-3.0
└── README.md                      # Dokumentasi Rasmi Projek
```

---

## ⚙️ Panduan Kebenaran Sistem (Permissions Guide)

Untuk memastikan sistem pertahanan KANTOI dapat bertindak secara automatik dan lancar 24/7 melindungi telefon anda, sila benarkan tetapan berikut selepas pemasangan:

1. 📞 **Telefon & Log Panggilan (Phone & Call Log):** Mengenal pasti ID pemanggil dan menyaring panggilan scammer secara langsung.
2. 💬 **SMS & Mesej:** Mengimbas pautan phishing, APK berniat jahat, dan mesej palsu TAC/OTP perbankan.
3. 📖 **Kenalan (Contacts):** Membolehkan sistem mengecam kenalan sah anda dan melindunginya melalui Senarai Putih (*Whitelist*).
4. 📁 **Storan / Akses Fail (Storage Access):** Membolehkan Penjaga Muat Turun (*Download Sentry*) mengimbas fail APK baharu serta-merta.
5. 🪟 **Papar Di Atas Aplikasi Lain (Display Over Other Apps):** Wajib dibenarkan agar paparan amaran radar panggilan masuk dapat muncul di skrin.
6. 🛡️ **Tetapkan Sebagai Aplikasi Pemanggil & Spam Lalai:** Memberikan kuasa kepada KANTOI untuk menyekat panggilan scammer serta-merta.
7. 🔋 **Pengoptimuman Bateri (Battery Optimization):** Pilih *'Tidak Dihadkan' (Unrestricted)* supaya sistem Android tidak mematikan perkhidmatan amaran di latar belakang.

---

## 🛠️ Cara Membina Daripada Kod Sumber (Building from Source)

Projek ini menyokong kompilasi berdiri sendiri (*standalone automated build pipeline*) menggunakan skrip Python yang memanggil alat binaan Android SDK secara terus:

### Keperluan Pembangunan:
- **Python 3.8+**
- **Java Development Kit (JDK 11 atau JDK 17)**
- **Android SDK Build-Tools (v32.0.0+)** dengan utiliti `aapt2` dan `d8`
- **uber-apk-signer** (untuk zipalign dan tandatangan APK v2/v3)

### Langkah Kompilasi:
1. Pastikan laluan Android SDK dan JDK telah dikonfigurasi dalam `build_kantoi.py`.
2. Jalankan perintah kompilasi:
   ```bash
   python build_kantoi.py
   ```
3. Fail APK yang telah siap ditandatangani akan dijana di direktori utama:
   ```
   Kantoi_v1.13.10_Release_signed.apk
   ```

---

## 🗺️ Pelan Hala Tuju Pembangunan (Release Roadmap 2026/2027)

Bagi memastikan KANTOI terus berkembang secara teratur dan aktif, ciri-ciri baharu dijadualkan mengikut fasa bulanan:

- 🚀 **Fasa 1: Bulan Ini (v1.13.10 — Kini Dilancarkan)**
  - 🛡️ **1-Tap Floating Threat HUD (Gelembung Terapung):** Akses semakan pantas scammer terapung di atas WhatsApp, Telegram, FB, dan Aplikasi Bank.
  - 🤖 **Multi-Entity Cyber Dossier (@crowdcallerbot):** Semakan automatik serentak untuk No Telefon + Akaun Bank + Kod QR DuitNow daripada 1 keping tangkapan layar.
- ⏳ **Fasa 2: Bulan Depan (November 2026)**
  - 📋 **Auto-Clipboard Mule Sentinel:** Pengesanan proaktif di latar belakang saat nombor akaun bank disalin (*copy*) sebelum transaksi kewangan dibuat.
- ⏳ **Fasa 3: Bulan Disember 2026**
  - 📱 **Native In-App Dossier View:** Paparan siasatan SemakMule CCID & forum Lowyat secara terus dalam aplikasi tanpa pelayar web luar.
- ⏳ **Fasa 4: Awal Tahun 2027**
  - 📞 **WhatsApp Call & Contact Risk Sentinel:** Perlindungan masa nyata panggilan WhatsApp scammer & audit keselamatan buku kenalan.
- 💡 **Fasa Penyelidikan (Future Pipeline):**
  - 📸 *AI Slip Forgery Detector (Pengecam Resit Bank Palsu)*
  - 👨‍👩‍👧 *Family Shield (Mod Perlindungan Keluarga & Warga Emas)*

---

## 🤝 Sumbangan & Komuniti (Contributing)

Sumbangan daripada pembangun perisian, penyelidik keselamatan siber (*cybersecurity researchers*), dan komuniti anti-scam Malaysia amat dialu-alukan!

1. **Fork** repositori ini.
2. Cipta *branch* baharu (`git checkout -b feature/penambahbaikan-baru`).
3. Lakukan *commit* perubahan anda (`git commit -m 'Menambah pengesanan scammer baharu'`).
4. Tolak ke *branch* anda (`git push origin feature/penambahbaikan-baru`).
5. Buka **Pull Request (PR)** untuk semakan.

---

## ⚖️ Lesen & Penafian Undang-Undang (License & Disclaimer)

### Lesen Perisian:
Projek ini dilesenkan di bawah terma **GNU General Public License v3.0 (GPL-3.0)**. Sila rujuk fail [LICENSE](LICENSE) untuk maklumat lanjut. Sebarang pengedaran semula atau kod derivatif wajib kekal sebagai perisian sumber terbuka dan percuma.

### Penafian:
Aplikasi ini dibangunkan sebagai inisiatif komuniti awam untuk membantu rakyat Malaysia daripada menjadi mangsa jenayah penipuan telekomunikasi dan siber. Maklumat pemadanan ancaman dirujuk daripada rekod terbuka awam termasuk portal SemakMule JSJK PDRM dan laporan komuniti. Pembangun tidak bertanggungjawab atas sebarang salah guna perisian ini.
