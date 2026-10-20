# -*- coding: utf-8 -*-
"""
Penyiaran Log Perubahan v1.13.8 dan Muat Naik Fail APK ke Saluran Telegram @crowdcaller
"""

import os
import sys
import io
import html
import requests

# Set console encoding for Windows
if sys.stdout and hasattr(sys.stdout, 'buffer'):
    try:
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass

import re

try:
    from local_secrets import BOT_TOKEN
except ImportError:
    BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "YOUR_TELEGRAM_BOT_TOKEN_HERE")
CHANNEL_ID = "@crowdcaller"
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Baca versi SemVer secara dinamik daripada AndroidManifest.xml
manifest_xml_path = os.path.join(BASE_DIR, "KantoiApp", "AndroidManifest.xml")
VERSION_NAME = "1.13.9"
VERSION_CODE = "11309"
if os.path.exists(manifest_xml_path):
    with open(manifest_xml_path, "r", encoding="utf-8") as mf:
        content = mf.read()
        v_match = re.search(r'android:versionName="([^"]+)"', content)
        if v_match:
            VERSION_NAME = v_match.group(1)
        c_match = re.search(r'android:versionCode="([^"]+)"', content)
        if c_match:
            VERSION_CODE = c_match.group(1)

APK_PATH = os.path.join(BASE_DIR, f"Kantoi_v{VERSION_NAME}_Release_signed.apk")

def send_changelog_message():
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    
    msg_html = (
        f"📋 <b>KEMASKINI APLIKASI KANTOI: VERSI {VERSION_NAME} DILEPASKAN!</b>\n\n"
        f"Pelepasan <b>Kantoi v{VERSION_NAME}</b> membawakan: <b>Kunci Komuniti VirusTotal Terbina (Multi-Layer Obfuscated)</b> dan <b>Model Hibrid BYOK (Bring-Your-Own-Key Priority)</b> untuk manfaat automatik kepada semua pengguna:\n\n"
        f"📜 <b>RINGKASAN LOG PERUBAHAN (CHANGELOG v{VERSION_NAME}):</b>\n\n"
        "🔑 <b>1. Kunci Komuniti VirusTotal Bawaan (Zero-Config Community Key):</b>\n"
        "• Pengguna baharu kini tidak perlu lagi mendaftar atau menyalin kunci API VirusTotal secara manual.\n"
        "• KANTOI kini dilengkapi Kunci Komuniti pra-pasang yang sedia digunakan serta-merta sebaik sahaja aplikasi dipasang untuk imbasan fail APK dan pautan web scammer.\n\n"
        "🛡️ <b>2. Perlindungan Penyamaran Multi-Lapisan (Bitwise XOR Masking & Seed Splitting):</b>\n"
        "• Kunci API dilindungi menggunakan penyamaran bitwise (Bitwise XOR) dan pemecahan vektor bait dalam memori.\n"
        "• Menghalang carian rentetan teks biasa (<code>strings</code> / hex search) oleh alat dekompilasi asas (Jadx / Apktool).\n\n"
        "🔄 <b>3. Model Hibrid BYOK (Bring-Your-Own-Key Priority):</b>\n"
        "• Pengguna yang mempunyai akaun VirusTotal sendiri tetap boleh memasukkan Kunci API peribadi di tab Tetapan Pertahanan untuk menikmati kuota eksklusif 500 semakan/hari.\n"
        "• Sistem mengutamakan kunci peribadi jika ada, dan beralih semula ke Kunci Komuniti secara pintar jika dikosongkan.\n\n"
        "⚡ <b>4. Semua Ciri Pertahanan Siber Kekal Aktif 100%:</b>\n"
        "• Enjin Heuristik Trojan Perbankan Tempatan (0ms, 100% Offline, Zero-Data).\n"
        "• Penjaga Muat Turun Latar Belakang (Real-Time Download Sentry) mengesan APK baharu di <code>/Download</code> sebelum dipasang.\n"
        "• Integrasi 'Kongsi ke KANTOI' (Universal Android System Share Target).\n"
        "• Kapsul Terapung Sisi (Hab Keselamatan Pantas SemakMule PDRM, Imbas APK, dan Imbas Pautan).\n\n"
        "━━━━━━━━━━━━━━━━━━━━\n"
        "⚠️ <b>PANDUAN PENTING: KEBENARAN SISTEM (PERMISSIONS SETUP)</b>\n"
        "Untuk memastikan sistem pertahanan KANTOI dapat bertindak secara automatik dan lancar 24/7 melindungi telefon anda, sila pastikan anda <b>BENARKAN (ALLOW) SEMUA KEBENARAN</b> berikut selepas memasang:\n\n"
        "1️⃣ 📞 <b>Telefon & Log Panggilan (Phone & Call Log):</b> Mengenal pasti ID pemanggil dan menyaring panggilan scammer secara langsung.\n"
        "2️⃣ 💬 <b>SMS & Mesej:</b> Mengimbas pautan phishing, APK berniat jahat, dan mesej palsu TAC/OTP perbankan.\n"
        "3️⃣ 📖 <b>Kenalan (Contacts):</b> Membolehkan sistem mengecam kenalan sah anda dan melindunginya melalui Senarai Putih.\n"
        "4️⃣ 📁 <b>Storan & Pengurusan Fail (Storage Access):</b> Membolehkan Penjaga Muat Turun (Download Sentry) mengimbas fail APK baharu serta-merta.\n"
        "5️⃣ 🪟 <b>Papar Di Atas Aplikasi Lain (Display Over Other Apps):</b> Wajib dibenarkan agar paparan amaran radar panggilan masuk dapat muncul di skrin.\n"
        "6️⃣ 🛡️ <b>Tetapkan Sebagai Aplikasi Pemanggil & Spam Lalai (Default Caller ID & Spam App):</b> Memberikan kuasa kepada KANTOI untuk menyekat panggilan scammer serta-merta.\n"
        "7️⃣ 🔋 <b>Pengoptimuman Bateri (Battery Optimization):</b> Pilih <i>'Tidak Dihadkan' (Unrestricted)</i> supaya sistem Android tidak mematikan perkhidmatan amaran di latar belakang.\n\n"
        "📲 <i>Fail APK rasmi dimuat naik di bawah untuk kemaskini segera.</i>"
    )
    
    payload = {
        "chat_id": CHANNEL_ID,
        "text": msg_html,
        "parse_mode": "HTML",
        "disable_web_page_preview": True
    }
    
    print(f"[1/2] Sedang menyiarkan pengumuman Changelog v{VERSION_NAME} berserta panduan kebenaran ke saluran @crowdcaller...")
    resp = requests.post(url, json=payload, timeout=30)
    data = resp.json()
    if data.get("ok"):
        print("[SUCCESS] Pengumuman changelog berjaya disiarkan! Message ID:", data["result"]["message_id"])
        return data["result"]["message_id"]
    else:
        print("[ERROR] Gagal menyiarkan changelog:", data)
        return None

def upload_apk():
    if not os.path.exists(APK_PATH):
        print(f"[ERROR] Fail APK tidak wujud: {APK_PATH}")
        return False
        
    apk_size = os.path.getsize(APK_PATH)
    apk_size_kb = apk_size / 1024.0
    print(f"[2/2] Sedang memuat naik fail APK ({apk_size_kb:.1f} KB)...")
    
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument"
    caption = (
        f"📦 <b>KANTOI Anti-Scam Caller ID & Threat Defense (v{VERSION_NAME} Release)</b>\n\n"
        f"📌 <b>Binaan:</b> <code>{os.path.basename(APK_PATH)}</code>\n"
        f"🔢 <b>Version Code:</b> <code>{VERSION_CODE}</code> (SemVer v{VERSION_NAME})\n"
        f"🛡️ <b>Ciri Baharu:</b> Kunci Komuniti VirusTotal Terbina (Multi-Layer Obfuscated), Model Hibrid BYOK, Enjin Heuristik Trojan Tempatan (0ms Offline), Penjaga Muat Turun (Download Sentry), dan 'Kongsi ke KANTOI'.\n"
        f"🔑 <b>Status Tandatangan:</b> Android APK Signature Scheme v2 & v3 (Disahkan & Zipalign)\n\n"
        f"⚠️ <b>PERINGATAN SELEPAS PASANG:</b> Sila <b>BENARKAN (ALLOW) SEMUA KEBENARAN</b> (Telefon, SMS, Kenalan, Storan, Papar di Atas Aplikasi Lain & Tiada Sekatan Bateri) di tetapan telefon anda agar perisai KANTOI berfungsi 100%!"
    )
    
    with open(APK_PATH, "rb") as f:
        files = {
            "document": (os.path.basename(APK_PATH), f, "application/vnd.android.package-archive")
        }
        data = {
            "chat_id": CHANNEL_ID,
            "caption": caption,
            "parse_mode": "HTML"
        }
        resp = requests.post(url, data=data, files=files, timeout=120)
        
    res = resp.json()
    if res.get("ok"):
        print("[SUCCESS] Fail APK berjaya dimuat naik ke Telegram @crowdcaller!")
        print("Document Message ID:", res["result"]["message_id"])
        return True
    else:
        print("[ERROR] Gagal memuat naik APK:", res)
        return False

if __name__ == "__main__":
    msg_id = send_changelog_message()
    if msg_id:
        upload_apk()
