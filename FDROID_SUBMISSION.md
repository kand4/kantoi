# Panduan & Templat Permohonan Kemasukan F-Droid / IzzyOnDroid untuk KANTOI

Panduan ini menyediakan templat siap guna untuk memohon kemasukan aplikasi **KANTOI** ke dalam repositori **IzzyOnDroid (F-Droid Staging)** dan **F-Droid Rasmi**.

---

## 🚀 Laluan 1: F-Droid RFP di GitLab (Menggunakan Akaun GitLab Anda)

F-Droid rasmi menyediakan penjejak isu **RFP (Request For Packaging)** di GitLab untuk memasukkan aplikasi baharu:

1. **Buka Borang RFP di GitLab:**
   👉 Pautan: `https://gitlab.com/fdroid/rfp/-/issues/new`
2. **Tajuk Isu (*Title*):**
   `RFP: com.kantoi.app (KANTOI - Anti-Scam Call Blocker)`
3. **Kandungan Isu (*Description*):**
   Salin teks templat di bawah dan hantar.

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
KANTOI is a privacy-first, zero-cost, offline scam call screening and cyber threat defense application designed specifically for Malaysia and Southeast Asia. Fastlane metadata structure with 512x512 app icon and multi-language descriptions (en-US & ms-MY) is already included in the root directory.
```

---

## 📦 Laluan 2: IzzyOnDroid di Codeberg (Staging Repo)

*Nota: IzzyOnDroid telah memindahkan penjejak isu mereka dari GitLab ke **Codeberg**.*
* **Pautan Isu Codeberg:** `https://codeberg.org/IzzyOnDroid/repo/issues`
* Gunakan templat yang sama jika anda memohon di Codeberg IzzyOnDroid.

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
