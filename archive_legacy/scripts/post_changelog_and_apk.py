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
        f"Pelepasan rasmi <b>Kantoi v{VERSION_NAME}</b> membawakan: <b>Pangkalan Data Risikan Baharu (DB v9: 3,729 Rekod)</b>, <b>Penyegerakan Automatik Senyap (OTA Background Sync)</b>, dan <b>Sistem Laporan Komuniti Terus ke @crowdcallerbot</b>:\n\n"
        f"📜 <b>RINGKASAN LOG PERUBAHAN (CHANGELOG v{VERSION_NAME}):</b>\n\n"
        "🛡️ <b>1. Pangkalan Data Risikan Dipertingkatkan (DB v9: 3,729 Rekod):</b>\n"
        "• Pertambahan 67 profil ancaman baharu membabitkan sindiket <b>Pelaburan Palsu & Akaun Keldai</b> (diekstrak melalui OCR daripada bukti transaksi & profil WhatsApp scammer).\n"
        "• Liputan meluas akaun keldai: CIMB, Maybank, Bank Islam, Public Bank, BSN, GXBank, BigPay & DuitNow.\n\n"
        "🔄 <b>2. Penyegerakan Automatik Senyap OTA (OTA Background Sync):</b>\n"
        "• Enjin KANTOI kini menyegerakkan rekod scammer baharu secara berkala (setiap 3 jam & setiap kali aplikasi dibuka) daripada saluran @crowdcaller secara automatik.\n"
        "• Notifikasi sistem pop-up akan memaklumkan pengguna sejurus rekod baharu disegerakkan tanpa perlu memasang semula APK!\n\n"
        "🤖 <b>3. Hab Pelaporan Komuniti Pintar (@crowdcallerbot):</b>\n"
        "• Laporkan nombor scammer atau akaun bank mencurigakan terus ke bot @crowdcallerbot.\n"
        "• Boleh taip santai ('main tulis saja'), hantar senarai panjang nombor, atau lampirkan gambar tangkap layar resit perbankan untuk semakan admin.\n\n"
        "🔑 <b>4. Semua Ciri Pertahanan Siber KANTOI Kekal Aktif 100%:</b>\n"
        "• Kunci Komuniti VirusTotal Terbina & Model Hibrid BYOK.\n"
        "• Enjin Heuristik Trojan Perbankan Tempatan (0ms Offline, Zero-Data).\n"
        "• Penjaga Muat Turun (Download Sentry) & 'Kongsi ke KANTOI'.\n"
        "• Kapsul Terapung Sisi SemakMule CCID PDRM.\n\n"
        "━━━━━━━━━━━━━━━━━━━━\n"
        "⚠️ <b>PANDUAN KEBENARAN SISTEM (WAJIB BENARKAN / ALLOW):</b>\n"
        "Agar perisai KANTOI aktif 24/7, sila benarkan semua kebenaran ini di telefon anda:\n"
        "1️⃣ 📞 <b>Telefon & Log Panggilan:</b> Mengesan ID pemanggil & sekat scammer.\n"
        "2️⃣ 💬 <b>SMS & Mesej:</b> Imbas pautan phishing & mesej palsu TAC/OTP.\n"
        "3️⃣ 📖 <b>Kenalan:</b> Perlindungan Senarai Putih kenalan sah.\n"
        "4️⃣ 📁 <b>Storan & Pengurusan Fail:</b> Download Sentry mengimbas APK baharu.\n"
        "5️⃣ 🪟 <b>Papar Di Atas Aplikasi Lain:</b> Paparan radar amaran panggilan masuk.\n"
        "6️⃣ 🛡️ <b>Aplikasi Pemanggil & Spam Lalai:</b> Kuasa menyekat panggilan scammer serta-merta.\n"
        "7️⃣ 🔋 <b>Pengoptimuman Bateri:</b> Pilih <i>'Tidak Dihadkan' (Unrestricted)</i> supaya sistem tidak dimatikan Android.\n\n"
        "━━━━━━━━━━━━━━━━━━━━\n"
        "🔍 <b>100% SUMBER TERBUKA & AUDIT BEBAS:</b>\n"
        "KANTOI bebas iklan, bebas penjejak, dan 100% telus di bawah lesen <b>GNU GPL-3.0</b>. Semak kod sumber di GitHub:\n"
        "👉 <b>https://github.com/kand4/kantoi</b>\n\n"
        "📲 <i>Fail APK rasmi dimuat naik di bawah untuk muat turun & kemaskini segera.</i>"
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
        f"🛡️ <b>Pangkalan Data:</b> DB v9 (3,729 Rekod Scammer & Akaun Keldai)\n"
        f"🔄 <b>Ciri:</b> OTA Background Sync, @crowdcallerbot Reporting, VirusTotal Community Key, Heuristik Trojan Tempatan (0ms Offline).\n"
        f"🌐 <b>GitHub:</b> https://github.com/kand4/kantoi\n"
        f"🔑 <b>Tandatangan:</b> APK Signature Scheme v2 & v3 (Verified & Zipalign)\n\n"
        f"⚠️ <b>Peringatan:</b> Sila <b>BENARKAN SEMUA KEBENARAN</b> (Telefon, SMS, Kenalan, Storan, Papar Atas Aplikasi & Tiada Sekatan Bateri) di telefon anda!"
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
