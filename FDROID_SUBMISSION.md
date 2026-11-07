# Panduan & Templat Permohonan Kemasukan F-Droid / IzzyOnDroid untuk KANTOI

Panduan ini menyediakan templat siap guna untuk memohon kemasukan aplikasi **KANTOI** ke dalam repositori **IzzyOnDroid (F-Droid Staging)** dan **F-Droid Rasmi**.

---

## 🚀 Laluan 1: IzzyOnDroid (Paling Cepat & Disyorkan)

IzzyOnDroid adalah repositori rasmi terbesar dalam ekosistem F-Droid yang mengambil APK yang telah siap ditandatangani terus dari GitHub Releases anda.

### Langkah-Langkah:
1. **Tolak Kod ke GitHub:**
   Pastikan commit terkini ditolak ke GitHub:
   ```bash
   git push origin main
   ```
2. **Cipta Tag Release di GitHub:**
   * Pergi ke: `https://github.com/kand4/kantoi/releases/new`
   * Masukkan Tag version: `v1.18.0`
   * Release title: `v1.18.0 - Kantoi Scam Shield` (atau `v1.18.0 - Kantoi Scam Shield & Smart Dialer`)
   * Lampirkan fail: `Kantoi_v1.18.0_Release_signed.apk`
   * Klik **Publish release**.

3. **Buka Permohonan (*Inclusion Request*) di IzzyOnDroid:**
   * Pergi ke: `https://gitlab.com/IzzyOnDroid/repo/-/issues/new?issue%5Bissue_type%5D=issue`
   * Pilih templat: **"Inclusion Request"**
   * Salin dan tampal data berikut:

```markdown
### Application details

- **Name:** KANTOI - Anti-Scam Call Blocker
- **Package Name:** com.kantoi.app
- **Summary:** Offline scam call screener, caller ID spoof detector & tactical dialer.
- **License:** GNU General Public License v3.0 (GPLv3)
- **Source code:** https://github.com/kand4/kantoi
- **Issue tracker:** https://github.com/kand4/kantoi/issues
- **Changelog:** https://github.com/kand4/kantoi/blob/main/KantoiApp/src/com/kantoi/app/ChangelogActivity.java
- **Releases:** https://github.com/kand4/kantoi/releases
- **Categories:** Security, Phone, Utility

### Anti-Features Check
- **Tracking:** NO (0 trackers, 0 analytics)
- **Ads:** NO (0 ads)
- **Non-Free Network Services:** NO (100% offline SQLite database)
- **Non-Free Dependencies:** NO (Pure Java/Android SDK)

### Additional Information
KANTOI is a privacy-first, offline scam call screening and cyber threat defense application designed specifically for Malaysia and Southeast Asia. Fastlane metadata structure with 512x512 app icon and multi-language descriptions (en-US & ms-MY) is already included in the root directory.
```

---

## 🌐 Laluan 2: F-Droid Rasmi (Official fdroiddata)

Untuk F-Droid utama (`f-droid.org`), pelayan F-Droid membina APK sendiri dari sumber kod:

1. **Fork Repositori:**
   Fork `https://gitlab.com/fdroid/fdroiddata` ke akaun GitLab anda.
2. **Cipta Fail Metadata YAML:**
   Cipta fail `metadata/com.kantoi.app.yml` dengan kandungan:
   ```yaml
   Categories:
     - Security
     - Phone
   License: GPL-3.0-or-later
   SourceCode: https://github.com/kand4/kantoi
   IssueTracker: https://github.com/kand4/kantoi/issues
   Changelog: https://github.com/kand4/kantoi/releases

   Summary: Offline scam call screener, caller ID spoof detector & tactical dialer
   Description: |-
       KANTOI is a privacy-first, zero-cost, offline scam call screening
       and cyber threat defense application designed specifically for
       Malaysia and Southeast Asia.

   RepoType: git
   Repo: https://github.com/kand4/kantoi.git

   Builds:
     - versionName: 1.18.0
       versionCode: 11800
       commit: v1.18.0
       subdir: KantoiApp
   ```
3. **Buka Merge Request (MR):**
   Hantar Merge Request ke repositori `fdroid/fdroiddata`. Bot F-Droid akan menguji kompilasi kod secara automatik.

---

## 📁 Struktur Metadata Fastlane yang Telah Disediakan
Struktur folder standard berikut telah siap dibina dalam projek KANTOI:
* `fastlane/metadata/android/en-US/title.txt`
* `fastlane/metadata/android/en-US/short_description.txt`
* `fastlane/metadata/android/en-US/full_description.txt`
* `fastlane/metadata/android/en-US/images/icon.png` (512x512 PNG)
* `fastlane/metadata/android/ms-MY/title.txt`
* `fastlane/metadata/android/ms-MY/short_description.txt`
* `fastlane/metadata/android/ms-MY/full_description.txt`
