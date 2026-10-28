# -*- coding: utf-8 -*-
"""
CrowdCaller & KANTOI Anti-Scam Telegram Bot
Versi Pintar: NLP Free-Form Extraction ("Main Tulis Saja") + Admin Approval Gate (Mod A)

Fungsi:
1. Semakan Pantas: Taip nombor telefon atau akaun bank untuk semak pangkalan data KANTOI.
2. Laporan Bebas ("Main Tulis Saja"): Pengguna boleh taip cerita panjang/bahasa harian, bot automatik ekstrak & reformat.
3. Laporan Berstruktur: Boleh juga guna /lapor langkah demi langkah.
4. Kad Kelulusan Admin: Admin terima laporan kemas dengan butang [Luluskan & Siar] / [Tolak].
5. Autopenyiaran ke saluran @crowdcaller dengan tag OTA #KANTOI_UPDATE & penyegerakan terus ke SQLite.
"""

import os
import re
import sys
import io
import time
import json
import sqlite3
import requests
import html
import base64
import subprocess
import tempfile
import hashlib
import zipfile
import datetime
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
import kantoi_faq_vault

try:
    from PIL import Image
    from pyzbar.pyzbar import decode as decode_barcode
    HAS_QR_SUPPORT = True
except Exception:
    HAS_QR_SUPPORT = False

# Pastikan output konsol Windows menyokong UTF-8 / Emoji
if sys.stdout and hasattr(sys.stdout, 'buffer'):
    try:
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace', line_buffering=True)
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace', line_buffering=True)
    except Exception:
        pass

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

try:
    from local_secrets import BOT_TOKEN, GEMINI_API_KEY
except ImportError:
    BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "YOUR_TELEGRAM_BOT_TOKEN_HERE")
    GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
except Exception:
    GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

try:
    from local_secrets import ADMIN_PASSCODE
except ImportError:
    ADMIN_PASSCODE = os.environ.get("ADMIN_PASSCODE", "kantoi2026")
except Exception:
    ADMIN_PASSCODE = os.environ.get("ADMIN_PASSCODE", "kantoi2026")

CHANNEL_ID = "@kantoiradar"

DB_PATH = os.path.join(BASE_DIR, "kantoi_threats.db")
if not os.path.exists(DB_PATH):
    DB_PATH = os.path.join(BASE_DIR, "KantoiApp", "assets", "kantoi_threats.db")
BACKUP_DB_PATH = os.path.join(BASE_DIR, "lowyat_threats.db")

PDRM_SEMAKMULE_API = "https://semakmule.rmp.gov.my/api/mule/get_search_data.php"
PDRM_API_KEY = "j3j389#nklala2"
MALAYSIA_SCAM_NEWS_RSS = "https://news.google.com/rss/search?q=scam+OR+penipuan+Malaysia&hl=ms&gl=MY&ceid=MY:ms"
GOOGLE_CSE_CX = "53a0041f2f24f4e3b"

OFFICIAL_DOMAINS = {
    'maybank': ['maybank2u.com.my', 'maybank.com', 'maybank.com.my'],
    'cimb': ['cimbclicks.com.my', 'cimb.com', 'cimb.com.my'],
    'publicbank': ['pbebank.com', 'publicbank.com.my'],
    'rhb': ['rhbgroup.com', 'rhbtradesmart.com'],
    'bankislam': ['bankislam.com', 'bankislam.biz'],
    'hongleong': ['hlb.com.my', 'hongleongconnect.my'],
    'ambank': ['ambank.com.my', 'ambankspot.com'],
    'affin': ['affinonline.com', 'affinbank.com.my'],
    'bsn': ['mybsn.com.my', 'bsn.com.my'],
    'tng': ['touchngo.com.my', 'tngdigital.com.my'],
    'touchngo': ['touchngo.com.my', 'tngdigital.com.my'],
    'tngdigital': ['touchngo.com.my', 'tngdigital.com.my'],
    'moneypacket': ['touchngo.com.my', 'tngdigital.com.my'],
    'moneypocket': ['touchngo.com.my', 'tngdigital.com.my'],
    'duitnow': ['duitnow.my', 'paynet.my'],
    'gxbank': ['gxbank.my'],
    'bigpay': ['bigpayme.com']
}

TNG_GENUINE_DOMAINS = [
    'touchngo.com.my',
    'tngdigital.com.my'
]

TNG_PHISHING_KEYWORDS = [
    'tng', 'touchngo', 'touch-n-go', 'tngdigital', 'tngewallet', 'tngwallet',
    'moneypacket', 'moneypocket', 'angpow-tng', 'angpau-tng', 'angpowtng',
    'duitraya-tng', 'tng-claim', 'tng-subsidi', 'tng-bantuan', 'tng-bonus',
    'tng-login', 'tng-reward', 'tng-angpau', 'tng-angpow', 'pocket-tng', 'packet-tng'
]

TNG_MONEY_PACKET_SAFETY_NOTE = (
    "💡 <b>PANDUAN KESELAMATAN TOUCH 'N GO MONEY PACKET:</b>\n"
    "• <b>Money Packet yang SEBENAR:</b>\n"
    "  Apabila anda mengimbas kod QR atau menekan pautan rasmi, ia akan <b>terus membuka aplikasi Touch 'n Go eWallet</b> anda dan wang akan <b>TERUS MASUK</b> ke dalam baki eWallet serta-merta tanpa meminta sebarang maklumat peribadi!\n"
    "• <b>Tanda-Tanda Phishing / SCAM:</b>\n"
    "  Jika pautan membuka laman web pelayar (browser) dan meminta anda <b>memasukkan Nombor Telefon, Kata Laluan, Kod PIN 6-digit eWallet, atau SMS OTP/TAC</b>, itu adalah <b>100% PERANGKAP PHISHING!</b>\n"
    "• TNG Digital <b>TIDAK PERNAH</b> meminta nombor PIN atau OTP melalui laman web untuk menebus Money Packet."
)

SUSPICIOUS_TLDS = {
    '.top', '.xyz', '.vip', '.icu', '.tk', '.ml', '.ga', '.cf', '.gq', 
    '.buzz', '.work', '.click', '.rest', '.fit', '.online', '.site', 
    '.cc', '.space', '.pw', '.club', '.link', '.live', '.monster', '.cfd'
}

GOV_KEYWORDS = [
    'str', 'bantuan', 'sumbangan', 'tunai', 'ebpn', 'mykasih', 'ekasih', 
    'madani', 'kwsp', 'saman', 'jpj', 'mysejahtera', 'padu', 'pakej-5g'
]

ADMIN_CONFIG_PATH = os.path.join(BASE_DIR, "admin_config.json")
REPORTS_DATA_PATH = os.path.join(BASE_DIR, "pending_reports.json")
USER_KEYS_PATH = os.path.join(BASE_DIR, "user_api_keys.json")

# Senarai Model Google Gemini yang disokong secara automatik (Fallback Terpantas)
AVAILABLE_GEMINI_MODELS = [
    "gemini-3.8-flash",            # Pilihan Utama 2026: Ultra-Pantas & Multimodal Vision
    "gemini-3.8-flash-lite",       # Sandaran Pantas: Kependaman Sangat Rendah & Kuota Luas
    "gemini-3.5-flash",            # Generasi 3.5: Kestabilan Tinggi & Vision
    "gemini-3.5-flash-lite",       # Generasi 3.5 Flash-Lite: Jimat Kuota & Bebas Sekatan
    "gemini-2.5-flash",            # Generasi 2.5: Pantas & Vision
    "gemini-2.0-flash",            # Sandaran Serasi ke Belakang
    "gemini-1.5-flash"             # Sandaran Warisan (Legacy Fallback)
]

user_sessions = {}
unconfirmed_reports = {}

# ==============================================================================
# PENGURUSAN ADMIN & LAPORAN
# ==============================================================================
def load_json_file(path, default):
    if not os.path.exists(path):
        return default
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default

def save_json_file(path, data):
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[ERR] Gagal menyimpan {path}: {e}")

LAST_GEMINI_DIAGNOSTIC = {
    "status": "NOT_TESTED",
    "error": "",
    "model": "",
    "timestamp": 0
}

def get_gemini_api_key_details():
    """
    Mengesan kunci Gemini API secara fleksibel daripada pelbagai fail konfigurasi:
    - admin_config.json (semua variasi nama kunci)
    - config.json, settings.json, secrets.json
    - local_secrets.py
    - .env / os.environ
    Mengembalikan (kunci_bersih, nama_fail, nama_kunci)
    """
    key_names = [
        "gemini_api_key", "GEMINI_API_KEY", "api_key", "apiKey", "API_KEY", 
        "gemini", "GEMINI", "gemini_key", "GEMINI_KEY", "google_api_key", "GOOGLE_API_KEY", 
        "key", "KEY", "token"
    ]
    
    # 1. Semak fail-fail konfigurasi JSON
    json_candidates = [
        ("admin_config.json", os.path.join(BASE_DIR, "admin_config.json")),
        ("secret.json", os.path.join(BASE_DIR, "secret.json")),
        ("secrets.json", os.path.join(BASE_DIR, "secrets.json")),
        ("config.json", os.path.join(BASE_DIR, "config.json")),
        ("settings.json", os.path.join(BASE_DIR, "settings.json")),
    ]
    for fn_label, fpath in json_candidates:
        if os.path.exists(fpath):
            try:
                data = load_json_file(fpath, {})
                if isinstance(data, dict):
                    for kn in key_names:
                        val = data.get(kn)
                        if val and isinstance(val, str):
                            clean = val.strip(" \t\r\n\'\"")
                            if clean and clean != "YOUR_GEMINI_API_KEY_HERE" and len(clean) >= 15:
                                return clean, fn_label, kn
            except Exception:
                pass

    # 2. Semak local_secrets.py
    try:
        import local_secrets
        for kn in key_names:
            val = getattr(local_secrets, kn, None)
            if val and isinstance(val, str):
                clean = val.strip(" \t\r\n\'\"")
                if clean and clean != "YOUR_GEMINI_API_KEY_HERE" and len(clean) >= 15:
                    return clean, "local_secrets.py", kn
    except Exception:
        pass

    # 3. Semak fail .env
    env_file = os.path.join(BASE_DIR, ".env")
    if os.path.exists(env_file):
        try:
            with open(env_file, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    k_part, v_part = line.split("=", 1)
                    k_part = k_part.strip()
                    v_part = v_part.strip(" \t\r\n\'\"")
                    if k_part in key_names and v_part and v_part != "YOUR_GEMINI_API_KEY_HERE" and len(v_part) >= 15:
                        return v_part, ".env", k_part
        except Exception:
            pass

    # 4. Semak Pembolehubah Persekitaran (os.environ)
    for kn in key_names:
        val = os.environ.get(kn, "").strip(" \t\r\n\'\"")
        if val and val != "YOUR_GEMINI_API_KEY_HERE" and len(val) >= 15:
            return val, "os.environ", kn

    return "", "Tiada", "Tiada"

def get_gemini_api_key():
    """Mendapatkan Gemini API Key yang disahkan aktif"""
    k, _, _ = get_gemini_api_key_details()
    return k

def set_gemini_api_key(new_key):
    """Menyimpan Gemini API Key ke dalam admin_config.json dan local_secrets.py serentak"""
    clean_key = new_key.strip(" \t\r\n\'\"")
    # 1. Simpan ke admin_config.json
    cfg = load_json_file(ADMIN_CONFIG_PATH, {})
    cfg["gemini_api_key"] = clean_key
    save_json_file(ADMIN_CONFIG_PATH, cfg)
    
    # 2. Kemas kini local_secrets.py
    ls_path = os.path.join(BASE_DIR, "local_secrets.py")
    if os.path.exists(ls_path):
        try:
            with open(ls_path, "r", encoding="utf-8") as f:
                content = f.read()
            if "GEMINI_API_KEY" in content:
                content = re.sub(r'GEMINI_API_KEY\s*=\s*["\x27].*?["\x27]', f'GEMINI_API_KEY = "{clean_key}"', content)
            else:
                content += f'\nGEMINI_API_KEY = "{clean_key}"\n'
            with open(ls_path, "w", encoding="utf-8") as f:
                f.write(content)
        except Exception:
            pass
            
    os.environ["GEMINI_API_KEY"] = clean_key
    global GEMINI_API_KEY
    GEMINI_API_KEY = clean_key

# ==============================================================================
# SISTEM BYOK (BRING YOUR OWN KEY) UNTUK PENGGUNA AWAM
# ==============================================================================
def get_user_gemini_key(user_id):
    """Mendapatkan kunci Gemini peribadi (BYOK) untuk pengguna Telegram tertentu"""
    if not user_id:
        return ""
    all_keys = load_json_file(USER_KEYS_PATH, {})
    u_info = all_keys.get(str(user_id))
    if isinstance(u_info, dict):
        k = u_info.get("api_key", "").strip(" \t\r\n\'\"")
        if k and len(k) >= 15:
            return k
    elif isinstance(u_info, str):
        k = u_info.strip(" \t\r\n\'\"")
        if k and len(k) >= 15:
            return k
    return ""

def set_user_gemini_key(user_id, api_key, model="auto"):
    """Menyimpan kunci Gemini peribadi (BYOK) ke dalam user_api_keys.json"""
    clean_k = api_key.strip(" \t\r\n\'\"")
    all_keys = load_json_file(USER_KEYS_PATH, {})
    all_keys[str(user_id)] = {
        "api_key": clean_k,
        "registered_at": int(time.time()),
        "model": model
    }
    save_json_file(USER_KEYS_PATH, all_keys)
    return True

def remove_user_gemini_key(user_id):
    """Memadamkan kunci Gemini peribadi (BYOK) pengguna"""
    all_keys = load_json_file(USER_KEYS_PATH, {})
    if str(user_id) in all_keys:
        del all_keys[str(user_id)]
        save_json_file(USER_KEYS_PATH, all_keys)
        return True
    return False

def get_effective_gemini_key(user_id=None):
    """
    Mengambil kunci Gemini yang berkesan mengikut keutamaan:
    1. Kunci peribadi pengguna (BYOK) jika didaftarkan
    2. Kunci pelayan KANTOI (admin_config.json / secret.json / local_secrets.py)
    Mengembalikan (kunci_bersih, label_sumber, jenis_kunci)
    """
    if user_id:
        u_key = get_user_gemini_key(user_id)
        if u_key:
            return u_key, "BYOK (Kunci Peribadi Pengguna)", "BYOK"

    server_key, src_fn, kname = get_gemini_api_key_details()
    if server_key:
        return server_key, f"Pelayan KANTOI ({src_fn})", "SERVER"

    return "", "Tiada Kunci Dikesan", "NONE"

def test_gemini_api_key(api_key):
    """
    Menguji kesahihan kunci Google Gemini API secara langsung dengan permintaan ringan (PONG).
    Mengembalikan (is_valid: bool, working_model_or_error: str)
    """
    clean_key = (api_key or "").strip(" \t\r\n\'\"")
    if not clean_key or len(clean_key) < 15:
        return False, "Format kunci tidak sah atau terlalu pendek."

    last_err = ""
    for tm in AVAILABLE_GEMINI_MODELS:
        test_url = f"https://generativelanguage.googleapis.com/v1beta/models/{tm}:generateContent?key={clean_key}"
        headers = {
            "x-goog-api-key": clean_key,
            "Content-Type": "application/json"
        }
        test_body = {"contents": [{"parts": [{"text": "PONG"}]}]}
        r = None
        try:
            r = requests.post(test_url, headers=headers, json=test_body, timeout=10)
        except Exception as net_err:
            try:
                import urllib3
                urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
                r = requests.post(test_url, headers=headers, json=test_body, timeout=10, verify=False)
            except Exception as e_retry:
                last_err = f"Ralat sambungan: {str(e_retry)[:80]}"
                continue

        if r and r.status_code == 200:
            return True, tm
        elif r and r.status_code in [404, 400]:
            last_err = f"Model {tm} tidak disokong (HTTP {r.status_code})"
            continue
        elif r and r.status_code in [401, 403]:
            return False, f"Google menolak kunci (HTTP {r.status_code} - API Key Invalid)."
        elif r:
            last_err = f"HTTP {r.status_code}: {r.text[:80]}"

    return False, last_err or "Pelayan Google tidak dapat dihubungi."

def get_admin_ids():
    data = load_json_file(ADMIN_CONFIG_PATH, {"admins": []})
    return set(data.get("admins", []))

def add_admin(chat_id):
    data = load_json_file(ADMIN_CONFIG_PATH, {"admins": []})
    admins = set(data.get("admins", []))
    admins.add(chat_id)
    data["admins"] = list(admins)
    save_json_file(ADMIN_CONFIG_PATH, data)
    return True

def get_pending_reports():
    return load_json_file(REPORTS_DATA_PATH, {})

def save_pending_report(rep_id, rep_data):
    reports = get_pending_reports()
    reports[rep_id] = rep_data
    save_json_file(REPORTS_DATA_PATH, reports)

# ==============================================================================
# ENJIN PENGEKSTRAK PINTAR NLP ("MAIN TULIS SAJA")
# ==============================================================================
BANKS_LIST = [
    'Maybank', 'MBB', 'Malayan Banking', 'CIMB', 'RHB', 'Public Bank', 'PBB', 
    'Hong Leong', 'HLB', 'AmBank', 'Bank Islam', 'BIMB', 'Bank Rakyat', 'BSN', 
    'Affin Bank', 'Affin', 'Alliance Bank', 'Alliance', 'Agrobank', 'TNG', 'Touch n Go', 'BigPay'
]

SCAM_CATEGORIES = [
    ('Macau Scam / Penyamaran Penguatkuasa', ['polis', 'mahkamah', 'bukit aman', 'pdrm', 'waran', 'tangkap', 'dsp', 'asp', 'inspektor', 'lhdn', 'cukai', 'tax', 'saman', 'jpj', 'sprm']),
    ('Penipuan Pelaburan Palsu / Kripto', ['pelaburan', 'invest', 'crypto', 'bitcoin', 'forex', 'trading', 'saham', 'dividen', 'pulangan']),
    ('Penipuan Kerja Sambilan / Task E-Dagang', ['part time', 'kerja sambilan', 'task', 'review hotel', 'tiktok', 'shopee', 'lazada', 'komisen', 'job']),
    ('Penipuan Pinjaman Wang / Ah Long', ['pinjaman', 'loan', 'lulus segera', 'wang berlesen', 'processing fee', 'deposit', 'ah long', 'pinjam']),
    ('Penipuan Jual Beli / Barang Tak Sampai', ['beli barang', 'tak dapat barang', 'marketplace', 'carousell', 'seller scam', 'facebook scam', 'order']),
    ('Penipuan Parcel / Kurier Tertahan', ['parcel', 'kurier', 'poslaju', 'j&t', 'kastam', 'sangkut']),
    ('Penipuan Pautan Phishing / Bantuan Palsu', ['bantuan', 'str', 'e-madani', 'kwsp', 'link palsu', 'phishing', 'apk'])
]

REPORT_TRIGGER_KEYWORDS = [
    'scam', 'scammer', 'penipu', 'tipu', 'keldai', 'transfer', 'duit', 'bayar', 
    'rugi', 'saman', 'mahkamah', 'polis', 'akaun', 'call', 'mangsa', 'ugut', 
    'palsu', 'wasap', 'whatsapp', 'telegram'
]

def normalize_phone(raw_phone):
    digits = re.sub(r"[^\d]", "", raw_phone)
    if not digits:
        return ""
    if digits.startswith("60"):
        digits = "0" + digits[2:]
    elif not digits.startswith("0") and (len(digits) == 9 or len(digits) == 10):
        digits = "0" + digits
    return digits

def extract_freeform_report(text):
    """
    Mengekstrak data berstruktur daripada teks bebas orang ramai:
    - Nombor Telefon Scammer
    - Akaun Bank & Nama Pemilik
    - Kategori / Modus Operandi
    - Keterangan / Kronologi
    """
    clean_text = text.strip()
    
    # 1. Ekstrak Semua Nombor Telefon
    all_found_phones = []
    raw_found = re.findall(r'(?:\+?60\s?1[0-9][\s\-\.]?\d{3,4}[\s\-\.]?\d{4}|01[0-9][\s\-\.]?\d{3,4}[\s\-\.]?\d{4})', clean_text)
    for rf in raw_found:
        np = normalize_phone(rf)
        if np and len(np) >= 9 and np not in all_found_phones:
            all_found_phones.append(np)
            
    if not all_found_phones:
        digits_matches = re.findall(r'\b(?:601|01)\d{7,9}\b', re.sub(r'[\s\-]', '', clean_text))
        for dm in digits_matches:
            np = normalize_phone(dm)
            if np and len(np) >= 9 and np not in all_found_phones:
                all_found_phones.append(np)
                
    if not all_found_phones:
        return None
        
    phone_norm = all_found_phones[0]

    # 2. Ekstrak Akaun Bank
    bank_info = "Tiada"
    bank_pattern = r'(' + '|'.join(BANKS_LIST) + r')\s*(?:bank)?\s*[:\-\s]?\s*(\d{8,16})'
    b_match = re.search(bank_pattern, clean_text, re.IGNORECASE)
    
    extracted_name = ""
    # Cari nama pemilik jika dinyatakan (contoh: 'nama mohd ali' atau 'a/n siti')
    name_match = re.search(r'(?:nama|a/n|holder|atas nama)\s+([a-zA-Z\s@/]{3,30})(?:\s+sebelum|\s+binti|\s+bin|\s+kerana|\.|\,|$|\n)', clean_text, re.IGNORECASE)
    if name_match:
        extracted_name = name_match.group(1).strip().upper()

    if b_match:
        bank_name = b_match.group(1).upper()
        acc_no = b_match.group(2)
        if extracted_name:
            bank_info = f"{bank_name} : {acc_no} ({extracted_name})"
        else:
            bank_info = f"{bank_name} : {acc_no}"
    else:
        # Jika tiada nama bank tapi ada nombor akaun panjang berdekatan perkataan 'akaun' / 'acc'
        acc_match = re.search(r'(?:akaun|acc|no\s*akaun)\s*[:\-\s]?\s*(\d{8,16})', clean_text, re.IGNORECASE)
        if acc_match:
            bank_info = f"Akaun: {acc_match.group(1)}"
            if extracted_name:
                bank_info += f" ({extracted_name})"

    # 3. Klasifikasi Kategori / Modus Operandi
    matched_tactic = "Penipuan Komuniti (Pelbagai)"
    for cat_name, kw_list in SCAM_CATEGORIES:
        if any(kw in clean_text.lower() for kw in kw_list):
            matched_tactic = cat_name
            break

    # 4. Nama Suspek
    suspect_title = matched_tactic
    if extracted_name:
        suspect_title = f"{matched_tactic} ({extracted_name})"

    return {
        "phone_norm": phone_norm,
        "all_phones": all_found_phones,
        "suspect_name": suspect_title,
        "bank_account": bank_info,
        "evidence_text": clean_text
    }

def is_likely_report(text):
    """Menentukan sama ada teks pengguna adalah laporan bebas atau sekadar semakan carian nombor"""
    clean = text.lower().strip()
    
    # Jika bermula dengan /lapor atau perkataan lapor
    if clean.startswith("/lapor") or clean.startswith("lapor"):
        return True
        
    # Jika teks mengandungi kata kunci aduan scam
    has_trigger_word = any(kw in clean for kw in REPORT_TRIGGER_KEYWORDS)
    
    # Jika mengandungi nombor telefon DAN ayat keterangan (> 25 aksara)
    has_phone = bool(re.search(r'(?:\+?60\s?1[0-9]|01[0-9])', clean))
    if has_phone and has_trigger_word and len(clean) >= 20:
        return True
        
    return False

def extract_batch_reports(text):
    """
    Mengekstrak senarai banyak scammer jika pengguna paste senarai berbaris / berangka.
    Contoh:
    1. 0177064436 - scammer pr
    2. 0167443103 - scammer ip
    """
    if not text:
        return []
    lines = [line.strip() for line in text.split('\n') if line.strip()]
    extracted_items = []
    for line in lines:
        phones = re.findall(r'(?:\+?60\s?1[0-9][\s\-\.]?\d{7,8}|01[0-9][\s\-\.]?\d{7,8}|\b01[0-9]\d{7,8}\b|\b601[0-9]\d{7,8}\b)', line)
        if phones:
            norm_phones = []
            for p in phones:
                np = normalize_phone(p)
                if len(np) in (10, 11) and np not in norm_phones:
                    norm_phones.append(np)
            if norm_phones:
                desc = re.sub(r'^\d+[\.\)]\s*', '', line)
                for p in phones:
                    desc = desc.replace(p, '')
                desc = re.sub(r'[\-\/\:\,]', ' ', desc).strip()
                extracted_items.append({
                    'phones': norm_phones,
                    'desc': desc or 'Scammer dilaporkan pengguna'
                })
        elif extracted_items and not line.lower().startswith(('list', 'amaran', 'alert', 'scammer', 'peringatan')):
            clean_sub = re.sub(r'[\-\/\:\,]', ' ', line).strip()
            if clean_sub:
                extracted_items[-1]['desc'] += f" ({clean_sub})"
    return extracted_items

# ==============================================================================
# ENJIN HIBRID: QR DECODER (DUITNOW/EMVCO) + AI VISION (GEMINI) + LOCAL OCR
# ==============================================================================

KNOWN_DUITNOW_BINS = {
    '501851': 'MAYBANK',
    '501852': 'MAYBANK ISLAMIC',
    '501853': 'CIMB ISLAMIC',
    '501854': 'CIMB',
    '501855': 'PUBLIC BANK',
    '501856': 'PUBLIC ISLAMIC BANK',
    '501857': 'RHB BANK',
    '501858': 'HONG LEONG BANK',
    '501859': 'AMBANK',
    '501860': 'UOB MALAYSIA',
    '501861': 'BANK ISLAM',
    '501862': 'BANK RAKYAT',
    '501863': 'BSN',
    '501864': 'BANK MUAMALAT',
    '501865': 'AGROBANK',
    '501866': 'TNG DIGITAL',
    '501867': 'GXBANK',
    '501868': 'BOOST',
    '501869': 'SHOPEEPAY',
    '501870': 'GRABPAY',
    '501871': 'AEON BANK',
    '501872': 'MBSB BANK',
    '501873': 'STANDARD CHARTERED',
    '501874': 'HSBC',
    '501875': 'OCBC',
    '501876': 'ALLIANCE BANK',
    '501877': 'AFFIN BANK',
    '501878': 'AL RAJHI BANK',
    '501879': 'BIGPAY'
}

def parse_emvco_tlv(payload):
    """Mengekstrak rentetan data EMVCo TLV (Tag-Length-Value) PayNet DuitNow"""
    idx = 0
    tlvs = {}
    while idx < len(payload):
        tag = payload[idx:idx+2]
        if len(tag) < 2:
            break
        length_str = payload[idx+2:idx+4]
        if len(length_str) < 2 or not length_str.isdigit():
            break
        length = int(length_str)
        val = payload[idx+4:idx+4+length]
        tlvs[tag] = val
        idx += 4 + length
    return tlvs

def robust_decode_qr(image_path):
    """
    Enjin Dekoder QR Multi-Pass Pintar.
    Mampu membaca QR kod daripada sebarang format gambar, mod gelap (inverted),
    warna kontras rendah (DuitNow Pink/Merah), gambar resolusi tinggi kamera fon,
    serta tangkap layar (screenshot).
    """
    if not HAS_QR_SUPPORT:
        return []
    try:
        from PIL import ImageOps, ImageEnhance, ImageFilter
        raw_img = Image.open(image_path)
    except Exception as e:
        print(f"[QR READ ERR] Gagal membuka fail imej: {e}")
        return []

    try:
        # Transpose orientasi EXIF kamera telefon pintar
        raw_img = ImageOps.exif_transpose(raw_img)
    except Exception:
        pass

    # Pastikan imej dalam mod RGB atau L
    if raw_img.mode not in ('RGB', 'L'):
        try:
            base_img = raw_img.convert('RGB')
        except Exception:
            base_img = raw_img
    else:
        base_img = raw_img

    w, h = base_img.size
    gray = base_img.convert('L')

    # Strategi Pelbagai Fasa (Multi-Pass Passes)
    passes = [
        ('raw', base_img),
        ('gray', gray),
        ('inv', ImageOps.invert(gray)),  # Sokong QR Mod Gelap (Dark Mode)
        ('contrast', ImageEnhance.Contrast(gray).enhance(2.3)),
        ('sharp', gray.filter(ImageFilter.SHARPEN)),
        ('th_128', gray.point(lambda p: 255 if p > 128 else 0)),
        ('th_90', gray.point(lambda p: 255 if p > 90 else 0)),
        ('th_160', gray.point(lambda p: 255 if p > 160 else 0)),
    ]

    # Skala resolusi untuk imej kamera telefon bersaiz gergasi
    if max(w, h) > 1600:
        ratio = 1200.0 / max(w, h)
        down = base_img.resize((int(w * ratio), int(h * ratio)), Image.LANCZOS)
        down_gray = down.convert('L')
        passes.append(('down_raw', down))
        passes.append(('down_gray', down_gray))
        passes.append(('down_inv', ImageOps.invert(down_gray)))
    elif max(w, h) < 600:
        up = base_img.resize((w * 2, h * 2), Image.LANCZOS)
        passes.append(('up_raw', up))
        passes.append(('up_gray', up.convert('L')))

    # Keratan tengah (Crop Center) untuk tangkap layar
    if w > 300 and h > 300:
        crop_c = base_img.crop((int(w * 0.1), int(h * 0.1), int(w * 0.9), int(h * 0.9)))
        passes.append(('crop_c', crop_c))
        passes.append(('crop_c_gray', crop_c.convert('L')))

    for name, p_img in passes:
        try:
            barcodes = decode_barcode(p_img)
            if barcodes:
                return barcodes
        except Exception:
            continue

    return []

def parse_qr_payload(raw_data):
    """
    Menganalisis kandungan teks / data binari daripada kod QR secara pintar.
    Mengekstrak DuitNow EMVCo Kebangsaan, pautan web phishing/quishing,
    muat turun fail APK malware, dan teks nombor akaun.
    """
    raw_data = raw_data.strip()
    
    # 1. Kod QR DuitNow / EMVCo Kebangsaan PayNet
    if raw_data.startswith(('000201', '000202', '000203')) or 'A0000006150001' in raw_data or (raw_data.startswith('0002') and len(raw_data) > 30):
        tlv = parse_emvco_tlv(raw_data)
        name = tlv.get('59', '').strip()
        city = tlv.get('60', '').strip()
        amount = tlv.get('54', '').strip()
        
        acc_no = ''
        bank_name = 'DUITNOW'
        proxy_type = ''
        proxy_phone = ''
        
        # Semak semua tag akaun merchant daripada Tag 26 hingga 51
        for t_num in range(26, 52):
            tag_key = f'{t_num:02d}'
            if tag_key in tlv:
                sub_tlv = parse_emvco_tlv(tlv[tag_key])
                sub_01 = sub_tlv.get('01', '')
                sub_02 = sub_tlv.get('02', '')
                
                if sub_01 in KNOWN_DUITNOW_BINS:
                    bank_name = KNOWN_DUITNOW_BINS[sub_01]
                    
                if sub_02:
                    acc_no = sub_02
                    if sub_01 == '01':
                        proxy_type = 'TELEFON'
                        proxy_phone = sub_02
                    elif sub_01 == '02':
                        proxy_type = 'NRIC'
                    break
                elif sub_01 and sub_01.isdigit() and len(sub_01) >= 8 and not acc_no:
                    acc_no = sub_01
                    
        return {
            'qr_detected': True,
            'qr_type': 'DUITNOW_EMVCO',
            'name': name or 'Tidak Dinyatakan',
            'bank': bank_name,
            'account_number': acc_no,
            'proxy_type': proxy_type,
            'proxy_phone': proxy_phone,
            'amount': amount,
            'raw_payload': raw_data
        }
        
    # 2. Pengesanan Pautan Web / Phishing / Quishing / APK Dropper daripada Kod QR
    url_m = re.search(r'(https?://[^\s<>"]+|www\.[^\s<>"]+|tngd://[^\s<>"]+)', raw_data, re.IGNORECASE)
    if not url_m:
        dom_m = re.search(r'\b([a-zA-Z0-9-]+\.(?:top|xyz|vip|icu|cc|site|club|app|info|online|live|shop|cfd|buzz|gov\.my|com\.my|com|net|org)(?:/[^\s<>"]*)?)\b', raw_data, re.IGNORECASE)
        if dom_m:
            url_str = 'https://' + dom_m.group(1).rstrip('.,;:)')
        else:
            url_str = None
    else:
        url_str = url_m.group(0).rstrip('.,;:)')
        if url_str.lower().startswith('www.'):
            url_str = 'https://' + url_str

    if url_str:
        u_inspect = inspect_url_phishing(url_str)
        domain = u_inspect.get("domain") or ""
        bank_name = 'PAUTAN_WEB'
        if u_inspect.get("is_genuine_tng"):
            bank_name = 'TNG DIGITAL (RASMI)'
        elif u_inspect.get("is_tng"):
            bank_name = 'TNG PALSU (PHISHING)'
        elif 'maybank' in domain: bank_name = 'MAYBANK'
        elif 'cimb' in domain: bank_name = 'CIMB'
        elif 'duitnow' in domain: bank_name = 'DUITNOW'
        
        phone_match = re.search(r'(?:60|0)1[0-9]{8,9}', url_str)
        phone = phone_match.group(0) if phone_match else ''
        
        return {
            'qr_detected': True,
            'qr_type': 'URL',
            'name': f'Pautan ({domain})',
            'bank': bank_name,
            'account_number': '',
            'proxy_phone': phone,
            'url': url_str,
            'url_inspect': u_inspect,
            'raw_payload': raw_data
        }
        
    # 3. Kod QR Berbentuk Teks / Nombor Hubungi
    phone_match = re.search(r'(?:\+?60|0)[\s\-]?[1-9][0-9][\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,5}', raw_data)
    acc_match = re.search(r'\b[0-9]{8,18}\b', raw_data)
    return {
        'qr_detected': True,
        'qr_type': 'TEKS',
        'name': 'Kod QR Teks',
        'bank': 'N/A',
        'account_number': acc_match.group(0) if acc_match else '',
        'proxy_phone': phone_match.group(0) if phone_match else '',
        'raw_payload': raw_data
    }

def decode_duitnow_qr(image_path):
    """Membaca petak kod QR secara multi-pass dan mengekstrak maklumat perbankan"""
    barcodes = robust_decode_qr(image_path)
    for b in barcodes:
        try:
            raw_data = b.data.decode('utf-8', errors='replace')
            res = parse_qr_payload(raw_data)
            if res and res.get('qr_detected'):
                return res
        except Exception as e:
            print(f"[QR PAYLOAD ERR] {e}")
            continue
    return None

def get_image_mime_type(image_path):
    """Mengesahkan format imej secara dinamik daripada header fail (PNG, JPEG, WEBP, dsb)"""
    try:
        with open(image_path, "rb") as f:
            header = f.read(16)
        if header.startswith(b'\x89PNG\r\n\x1a\n'):
            return "image/png"
        elif header.startswith(b'\xff\xd8\xff'):
            return "image/jpeg"
        elif header.startswith(b'RIFF') and b'WEBP' in header:
            return "image/webp"
        elif header.startswith(b'GIF8'):
            return "image/gif"
    except Exception:
        pass
    return "image/jpeg"

def optimize_image_for_gemini(image_path, max_dim=1280, quality=85):
    """
    Mengoptimumkan saiz fail imej sebelum dihantar ke Gemini API.
    - Mengurangkan saiz fail drastik (dari 3-5MB ke 100-200KB)
    - Mengelakkan sekatan gateway Google Cloud / Cloud Armor (HTTP 403 / 413)
    - Memastikan teks, nombor telefon dan kod QR kekal tajam & boleh dibaca
    """
    if not image_path or not os.path.exists(image_path):
        return image_path, "image/jpeg", False
    try:
        from PIL import Image
        with Image.open(image_path) as img:
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")
            w, h = img.size
            if max(w, h) > max_dim:
                scale = max_dim / float(max(w, h))
                new_w = int(w * scale)
                new_h = int(h * scale)
                resample_filter = getattr(Image, "Resampling", None)
                filter_algo = resample_filter.LANCZOS if resample_filter else Image.LANCZOS
                img = img.resize((new_w, new_h), filter_algo)
            tmp_opt = tempfile.NamedTemporaryFile(delete=False, suffix=".jpg")
            img.save(tmp_opt.name, format="JPEG", quality=quality, optimize=True)
            tmp_opt.close()
            return tmp_opt.name, "image/jpeg", True
    except Exception as opt_err:
        print(f"[IMG OPTIMIZE WARN] {opt_err}. Menggunakan fail asal.")
        return image_path, get_image_mime_type(image_path), False

def handle_gemini_retry_delay(resp, attempt_idx=0, next_model_name=None):
    """
    Mengira masa jeda pintar (smart backoff delay) sekiranya Google API memulangkan
    status ralat seperti HTTP 423 (Locked), 429 (Rate Limit), atau 503 (Overload).
    Memeriksa header 'Retry-After' atau menggunakan progressive backoff yang selamat.
    """
    if resp is None:
        return

    status = getattr(resp, "status_code", 0)
    retry_header = None
    if hasattr(resp, "headers") and resp.headers:
        retry_header = resp.headers.get("Retry-After") or resp.headers.get("retry-after")

    if retry_header:
        try:
            wait_sec = min(max(float(retry_header), 1.0), 5.0)
            target_str = f" ke model {next_model_name}" if next_model_name else ""
            print(f"[GEMINI RETRY-AFTER] Header pelayan meminta jeda {wait_sec:.1f}s sebelum bertukar{target_str}...")
            time.sleep(wait_sec)
            return
        except Exception:
            pass

    if status == 423:
        # HTTP 423 Locked: Kunci konkurensi/gateway mutex memerlukan jeda 2.0s - 3.5s untuk dilepaskan
        wait_sec = min(2.0 + (attempt_idx * 0.5), 4.0)
        target_str = f" ke model {next_model_name}" if next_model_name else ""
        print(f"[GEMINI 423 LOCKED] Kunci sumber dikesan! Jeda selamat {wait_sec:.1f}s untuk pelepasan kunci gerbang sebelum bertukar{target_str}...")
        time.sleep(wait_sec)
    elif status == 429:
        # HTTP 429 Rate Limit / Quota Exceeded: Jeda progresif 1.8s - 4.5s
        wait_sec = min(1.8 + (attempt_idx * 0.6), 4.5)
        target_str = f" ke model {next_model_name}" if next_model_name else ""
        print(f"[GEMINI 429 RATE LIMIT] Had kuota dicapai! Jeda {wait_sec:.1f}s sebelum beralih{target_str}...")
        time.sleep(wait_sec)
    elif status in [500, 502, 503, 504]:
        # Server Overload / Gateway Timeout: Jeda 1.2s untuk kestabilan kluster Google
        time.sleep(1.2)
    elif status in [400, 403, 404]:
        # Model tidak wujud / kebenaran ditolak: Terus beralih ke model seterusnya (0.2s pantas)
        time.sleep(0.2)
    else:
        time.sleep(0.5)

def analyze_image_with_gemini(image_path, api_key=None, user_id=None):
    """Menghantar imej ke Google Gemini Vision API dengan sokongan perlindungan keselamatan dan kebal SSL Windows 7"""
    global LAST_GEMINI_DIAGNOSTIC
    if not api_key:
        api_key, _, _ = get_effective_gemini_key(user_id)
    api_key = (api_key or "").strip(" \t\r\n\'\"")
    if not api_key or api_key == "YOUR_GEMINI_API_KEY_HERE":
        LAST_GEMINI_DIAGNOSTIC = {
            "status": "NOT_CONFIGURED",
            "error": "Kunci API Gemini belum ditetapkan",
            "model": "",
            "timestamp": time.time()
        }
        return None
        
    opt_img_path, mime_type, was_optimized = optimize_image_for_gemini(image_path)
    try:
        with open(opt_img_path, "rb") as f:
            b64_data = base64.b64encode(f.read()).decode("utf-8")
    except Exception as e:
        print(f"[GEMINI FILE READ ERR] {e}")
        if was_optimized and os.path.exists(opt_img_path):
            try: os.remove(opt_img_path)
            except: pass
        return None
    finally:
        if was_optimized and os.path.exists(opt_img_path):
            try: os.remove(opt_img_path)
            except: pass

    prompt = (
        "Anda adalah penganalisis keselamatan siber risikan penipuan KANTOI (Malaysia).\n"
        "TUGAS UTAMA: Analisis gambar ini secara pintar dan teliti (screenshot WhatsApp/Telegram, resit bank, profil suspek, foto telefon lain).\n\n"
        "PANDUAN MENGIKUT ARKETIP IMEJ:\n"
        "1. SKRIN PANGGILAN WHATSAPP (Call Screen / Ringing / Calling / Incoming Call):\n"
        "   - Nombor terpapar besar di bahagian atas (cth: +60 14-627 2791) bersama status 'Ringing...', butang tamat panggilan, dan avatar suspek.\n"
        "   - Ekstrak nombor telefon ke 'phone_numbers'.\n"
        "   - scam_tactic: 'Panggilan Suara Scammer (WhatsApp Call)'.\n\n"
        "2. PROFIL / KAD PERNIAGAAN WHATSAPP (WhatsApp Business Profile / Bio):\n"
        "   - Mengandungi nombor utama, nama profil/perkhidmatan (cth: 'OFFICIAL MIDMAN MAZE'), emel (cth: 'midmanmaze@gmail.com'), dan nombor tambahan dalam teks bio (cth: 0145057539 -Paidpromote).\n"
        "   - Ekstrak SEMUA nombor unik ke 'phone_numbers'.\n"
        "   - Ekstrak nama organisasi/servis ke 'suspect_name'.\n"
        "   - Ekstrak emel jika ada ke 'email'.\n"
        "   - scam_tactic: 'Khidmat Orang Tengah Palsu (Fake Midman / Escrow)'.\n\n"
        "3. SKRIN MAKLUMAT KENALAN WHATSAPP (Contact Info Screen dengan simbol ~):\n"
        "   - Nombor telefon terpapar besar di atas (cth: +60 14-627 2791) dan di bawahnya nama push yang didaftarkan suspek (cth: ~ Prem Kumar).\n"
        "   - Ekstrak nama tersebut (buang simbol ~) ke 'suspect_name' (cth: 'Prem Kumar').\n"
        "   - Ekstrak nombor telefon ke 'phone_numbers'.\n"
        "   - scam_tactic: 'Profil / Kenalan WhatsApp Suspek'.\n\n"
        "4. FOTO FIZIKAL SKRIN TELEFON LAIN / QUISHING TNG MONEY PACKET:\n"
        "   - Foto fizikal telefon lain yang ditangkap kamera (ada pantulan lampu/sudut condong).\n"
        "   - Jika ada mesej Telegram/WhatsApp (cth pengirim 'Wafa KJ Dietetik' dan teks 'Saya nak bagi sikit rezeki...'):\n"
        "   - Ekstrak nama pengirim ke 'suspect_name'.\n"
        "   - scam_tactic: 'Penipuan Sedekah Palsu / Quishing TNG Money Packet'.\n\n"
        "5. RESIT PEMINDAHAN BANK / DUITNOW QR:\n"
        "   - Ekstrak nombor akaun, nama bank, nama penerima, dan jumlah.\n\n"
        "6. PENGESANAN KOD QR & PAUTAN TNG MONEY PACKET (SEBENAR VS PHISHING):\n"
        "   - Perhatikan sebarang pautan/QR 'Money Packet', 'Money Pocket', 'Sedekah', 'Duit Raya', atau 'Bantuan TNG':\n"
        "   - Pautan SEBENAR HANYA menggunakan domain rasmi: *.tngdigital.com.my atau *.touchngo.com.my (atau tngd://), di mana wang terus masuk ke eWallet tanpa meminta maklumat peribadi.\n"
        "   - Pautan PALSU (PHISHING) menggunakan domain selain itu (cth tng-claim.com, tapak percuma) dan meminta nombor telefon, PIN 6-digit atau OTP.\n"
        "   - Jika dikesan penipuan ini: scam_tactic = 'Penipuan Pancingan Data (Phishing) TNG Money Packet Palsu', dan sertakan peringatan bahawa Money Packet sebenar tidak meminta maklumat peribadi sebaliknya terus masuk ke akaun eWallet.\n\n"
        "Formatkan maklumat dalam JSON sah dengan kunci berikut:\n"
        "{\n"
        '  "phone_numbers": ["senarai nombor telefon unik cth 011xxxxxxxx, 014xxxxxxxx"],\n'
        '  "suspect_name": "nama scammer, nama profil WhatsApp tanpa ~, nama servis (cth OFFICIAL MIDMAN MAZE), atau pengirim Telegram",\n'
        '  "email": "alamat emel jika ada atau null",\n'
        '  "bank_name": "nama bank jika ada atau null",\n'
        '  "account_number": "nombor akaun bank keldai jika ada atau null",\n'
        '  "scam_tactic": "kategori penipuan terperinci",\n'
        '  "evidence_text": "ringkasan ringkas bukti dalam bahasa Melayu",\n'
        '  "full_text": "semua teks dan nombor penting dalam imej"\n'
        "}\n"
        "Berikan JSON SAHAJA tanpa sebarang teks tambahan."
    )
    
    payload = {
        "contents": [{
            "parts": [
                {"text": prompt},
                {"inline_data": {"mime_type": mime_type, "data": b64_data}}
            ]
        }],
        "safetySettings": [
            {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
        ],
        "generationConfig": {
            "temperature": 0.1,
            "response_mime_type": "application/json"
        }
    }

    models_to_try = AVAILABLE_GEMINI_MODELS

    for idx, model_name in enumerate(models_to_try):
        next_model = models_to_try[idx + 1] if idx + 1 < len(models_to_try) else None
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
        headers = {
            "x-goog-api-key": api_key,
            "Content-Type": "application/json"
        }
        resp = None
        try:
            resp = requests.post(url, headers=headers, json=payload, timeout=45)
        except Exception as net_or_ssl_err:
            # Kebal SSL untuk Windows 7 SP1 & Python 3.8 embeddable
            print(f"[GEMINI SSL/NET WARN on {model_name}] {net_or_ssl_err}. Mencuba mod kebal SSL (verify=False)...")
            try:
                import urllib3
                urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
                resp = requests.post(url, headers=headers, json=payload, timeout=45, verify=False)
            except Exception as e_retry:
                print(f"[GEMINI RETRY ERR] {e_retry}")
                LAST_GEMINI_DIAGNOSTIC = {
                    "status": "CONN_ERROR",
                    "error": str(e_retry)[:120],
                    "model": model_name,
                    "timestamp": time.time()
                }
                continue

        if not resp:
            continue

        if resp.status_code == 200:
            try:
                data = resp.json()
                candidates = data.get("candidates", [])
                if not candidates:
                    block_r = data.get("promptFeedback", {}).get("blockReason", "Unknown")
                    print(f"[GEMINI PROMPT BLOCKED] {block_r}")
                    LAST_GEMINI_DIAGNOSTIC = {
                        "status": "BLOCKED",
                        "error": f"Disekat oleh Google ({block_r})",
                        "model": model_name,
                        "timestamp": time.time()
                    }
                    continue

                cand = candidates[0]
                finish_r = cand.get("finishReason", "")
                parts = cand.get("content", {}).get("parts", [])
                if not parts:
                    print(f"[GEMINI EMPTY PARTS] finishReason={finish_r}")
                    LAST_GEMINI_DIAGNOSTIC = {
                        "status": "SAFETY_BLOCKED" if finish_r == "SAFETY" else "EMPTY_RESPONSE",
                        "error": f"Tiada teks respons (finishReason: {finish_r})",
                        "model": model_name,
                        "timestamp": time.time()
                    }
                    continue

                raw_text = parts[0].get("text", "").strip()
                clean_json = re.sub(r'^```(?:json)?\s*', '', raw_text, flags=re.IGNORECASE)
                clean_json = re.sub(r'\s*```$', '', clean_json.strip())

                m = re.search(r'\{.*\}', clean_json, re.DOTALL)
                parsed = json.loads(m.group(0)) if m else json.loads(clean_json)

                LAST_GEMINI_DIAGNOSTIC = {
                    "status": "OK",
                    "error": "",
                    "model": model_name,
                    "timestamp": time.time()
                }
                return parsed
            except Exception as parse_e:
                print(f"[GEMINI JSON PARSE ERR] {parse_e}")
                LAST_GEMINI_DIAGNOSTIC = {
                    "status": "PARSE_ERROR",
                    "error": str(parse_e)[:120],
                    "model": model_name,
                    "timestamp": time.time()
                }
                continue
        else:
            err_msg = resp.text[:220].replace('\n', ' ')
            print(f"[GEMINI HTTP {resp.status_code} ({model_name})] {err_msg}")
            LAST_GEMINI_DIAGNOSTIC = {
                "status": f"HTTP_{resp.status_code}",
                "error": err_msg,
                "model": model_name,
                "timestamp": time.time()
            }
            # Tangani sebarang ralat (423 Locked, 429 Rate Limit, 403, 500/503 dll):
            # Gunakan jeda pintar berperingkat (backoff) untuk mengelakkan kegagalan berulang
            handle_gemini_retry_delay(resp, idx, next_model)
            continue

    return None

def analyze_multi_images_with_gemini(image_paths, api_key=None, captions=None, user_id=None):
    """
    Menghantar beberapa imej (album/screenshot berbilang) ke Google Gemini Vision API
    untuk dianalisis secara serentak sebagai SATU KES PENIPUAN / SCAMMER YANG SAMA.
    """
    global LAST_GEMINI_DIAGNOSTIC
    if not api_key:
        api_key, _, _ = get_effective_gemini_key(user_id)
    api_key = (api_key or "").strip(" \t\r\n'\"")
    if not api_key or api_key == "YOUR_GEMINI_API_KEY_HERE":
        LAST_GEMINI_DIAGNOSTIC = {
            "status": "NOT_CONFIGURED",
            "error": "Kunci API Gemini belum ditetapkan",
            "model": "",
            "timestamp": time.time()
        }
        return None

    if not image_paths:
        return None

    parts = []
    
    # Bina Prompt Siasatan Rentas Imej & Dispute Forensik (Cross-Examination & Chronology Intelligence)
    prompt = (
        "Anda adalah penganalisis forensik keselamatan siber risikan penipuan KANTOI (Malaysia).\n"
        "TUGAS KRITIKAL: Pengguna telah memuat naik beberapa tangkapan layar (screenshots) yang merupakan BUKTI KEPADA SATU KES PENIPUAN / SCAMMER YANG SAMA.\n"
        "Bukti ini mungkin mengandungi gabungan: paparan kronologi/dispute mangsa, skrin perbualan WhatsApp/Telegram dengan scammer, resit bayaran, profil suspek, dan Kod QR DuitNow.\n"
        "Anda WAJIB menghubungkaitkan (cross-correlate) kesemua gambar ini secara serentak untuk membina SATU profil kes dan laporan komprehensif bersepadu.\n\n"
        "PANDUAN MENGANALISIS BUKTI BERGAMBAR:\n"
        "1. PEMISAHAN ENTITI MANGSA VS SUSPEK (AMAT KRITIKAL):\n"
        "   - Dalam paparan kronologi, nota aduan, atau dispute bertulis mangsa:\n"
        "     * BEZAKAN DENGAN JELAS antara PENGADU/MANGSA (pihak yang ditipu/rugi wang) dan SUSPEK/SCAMMER (penipu).\n"
        "     * JANGAN SESEKALI memasukkan nama atau nombor telefon mangsa ke dalam 'phone_numbers' atau 'suspect_names'!\n"
        "     * Hanya ekstrak identiti SUSPEK, nombor telefon suspek, dan akaun bank keldai suspek.\n"
        "   - Dalam tangkapan layar perbualan (WhatsApp/Telegram/SMS):\n"
        "     * Mesej di sebelah kiri (diterima) atau nama/nombor pada header tajuk chat adalah SUSPEK.\n"
        "     * Mesej di sebelah kanan (bubble hijau/keluar) adalah MANGSA.\n\n"
        "2. SILANG PERIKSA KRONOLOGI, CHAT & KOD QR (Cross-Entity Linkage):\n"
        "   - Hubungkan kronologi dispute mangsa (tarikh, tawaran barangan/perkhidmatan, janji palsu) dengan teks perbualan scammer.\n"
        "   - Jika terdapat Kod QR DuitNow atau resit transaksi, padankan nama penerima/pemegang akaun pada QR/resit dengan perbualan scammer.\n"
        "   - Ekstrak SEMUA nombor telefon suspek yang muncul dalam mana-mana gambar (WhatsApp, SMS, Telegram, bio profil).\n"
        "   - Ekstrak SEMUA akaun bank keldai yang terlibat (nama bank, nombor akaun, nama pemilik akaun).\n\n"
        "3. RUMUSAN KES & ANGGARAN KERUGIAN (Case Synthesis Narrative):\n"
        "   - Bina satu ringkasan jalan cerita penipuan yang padat dan jelas menghubungkan kronologi kejadian: Bagaimana mangsa mula berhubung, taktik penipuan yang digunakan, arahan pembayaran, jumlah kerugian wang (jika dinyatakan cth: RM300), dan tindakan suspek selepas wang dipindahkan (cth: menyekat nombor mangsa).\n"
        "   - Kenalpasti modus operandi / taktik scammer utama.\n\n"
        "4. PENGESANAN KOD QR & PAUTAN TNG MONEY PACKET / BANTUAN:\n"
        "   - Kenalpasti sebarang kod QR atau tangkapan layar pautan mendakwa 'Money Packet', 'Money Pocket', 'Angpow TNG', atau 'Bantuan E-Wallet'.\n"
        "   - Kaedah mengesan Money Packet SEBENAR: Hanya menggunakan domain *.tngdigital.com.my atau *.touchngo.com.my (atau tngd://), dan wang TERUS MASUK ke eWallet tanpa meminta maklumat peribadi.\n"
        "   - Pautan PALSU (PHISHING) menggunakan domain selain itu dan memancing mangsa memasukkan nombor telefon, PIN 6-digit eWallet, atau SMS OTP/TAC.\n"
        "   - Jika terdapat penipuan ini: scam_tactic = 'Penipuan Pancingan Data (Phishing) TNG Money Packet Palsu' dan perincikan dalam case_summary.\n\n"
        "Formatkan hasil analisis HANYA dalam JSON SAH dengan struktur berikut:\n"
        "{\n"
        '  "has_intel": true,\n'
        '  "case_summary": "Ringkasan komprehensif jalan cerita kes penipuan menghubungkaitkan kronologi dan kesemua gambar bukti",\n'
        '  "scam_tactic": "Kategori / Modus operandi penipuan (contoh: Penipuan Jual Beli / Khidmat Orang Tengah Palsu / Scam Pelaburan)",\n'
        '  "suspect_names": ["Senarai nama atau alias suspek yang ditemui"],\n'
        '  "primary_suspect_name": "Nama suspek atau nama servis utama",\n'
        '  "phone_numbers": ["Senarai semua nombor telefon unik suspek cth 011xxxxxxxx, 014xxxxxxxx"],\n'
        '  "primary_phone": "Nombor telefon utama suspek",\n'
        '  "bank_accounts": [\n'
        '    {"bank": "Nama Bank", "account_number": "1234567890", "holder_name": "Nama Pemilik Akaun"}\n'
        '  ],\n'
        '  "victim_loss": "Jumlah kerugian mangsa jika dinyatakan (contoh: RM 350.00) atau null",\n'
        '  "emails": ["Alamat emel jika ada"],\n'
        '  "evidence_details": "Poin-poin penting fakta kes daripada perbualan, kronologi atau transaksi"\n'
        "}\n"
        "PENTING: Jangan sertakan teks di luar JSON. Berikan JSON SAHAJA."
    )
    
    parts.append({"text": prompt})
    
    if captions:
        cap_clean = [c.strip() for c in captions if c and c.strip()]
        if cap_clean:
            parts.append({"text": "Catatan / Keterangan Pengguna berkaitan bukti: " + " | ".join(cap_clean)})

    valid_images_count = 0
    temp_opt_files = []
    for idx, img_p in enumerate(image_paths[:10]):
        try:
            opt_path, m_type, was_opt = optimize_image_for_gemini(img_p)
            if was_opt:
                temp_opt_files.append(opt_path)
            with open(opt_path, "rb") as f:
                b64_d = base64.b64encode(f.read()).decode("utf-8")
            parts.append({
                "inline_data": {
                    "mime_type": m_type,
                    "data": b64_d
                }
            })
            valid_images_count += 1
        except Exception as read_err:
            print(f"[MULTI-IMAGE READ ERR ({idx})] {read_err}")

    # Bersihkan fail JPEG sementara serta-merta sebaik sahaja selesai ditukar kepada Base64
    for t_f in temp_opt_files:
        if os.path.exists(t_f):
            try: os.remove(t_f)
            except Exception: pass
    temp_opt_files.clear()

    if valid_images_count == 0:
        return None

    payload = {
        "contents": [{"parts": parts}],
        "safetySettings": [
            {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
        ],
        "generationConfig": {
            "temperature": 0.1,
            "response_mime_type": "application/json"
        }
    }

    models_to_try = AVAILABLE_GEMINI_MODELS

    for idx, model_name in enumerate(models_to_try):
        next_model = models_to_try[idx + 1] if idx + 1 < len(models_to_try) else None
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
        headers = {
            "x-goog-api-key": api_key,
            "Content-Type": "application/json"
        }
        resp = None
        try:
            resp = requests.post(url, headers=headers, json=payload, timeout=45)
        except Exception as net_or_ssl_err:
            print(f"[GEMINI MULTI-IMG SSL/NET WARN on {model_name}] {net_or_ssl_err}. Mencuba kebal SSL...")
            try:
                import urllib3
                urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
                resp = requests.post(url, headers=headers, json=payload, timeout=45, verify=False)
            except Exception as e_retry:
                print(f"[GEMINI MULTI-IMG RETRY ERR] {e_retry}")
                LAST_GEMINI_DIAGNOSTIC = {
                    "status": "CONN_ERROR",
                    "error": str(e_retry)[:120],
                    "model": model_name,
                    "timestamp": time.time()
                }
                continue

        if not resp:
            continue

        if resp.status_code == 200:
            try:
                data = resp.json()
                candidates = data.get("candidates", [])
                if not candidates:
                    block_r = data.get("promptFeedback", {}).get("blockReason", "Unknown")
                    LAST_GEMINI_DIAGNOSTIC = {
                        "status": "BLOCKED",
                        "error": f"Disekat oleh Google ({block_r})",
                        "model": model_name,
                        "timestamp": time.time()
                    }
                    continue

                cand = candidates[0]
                finish_r = cand.get("finishReason", "")
                res_parts = cand.get("content", {}).get("parts", [])
                if not res_parts:
                    LAST_GEMINI_DIAGNOSTIC = {
                        "status": "SAFETY_BLOCKED" if finish_r == "SAFETY" else "EMPTY_RESPONSE",
                        "error": f"Tiada teks respons (finishReason: {finish_r})",
                        "model": model_name,
                        "timestamp": time.time()
                    }
                    continue

                raw_text = res_parts[0].get("text", "").strip()
                clean_json = re.sub(r'^```(?:json)?\s*', '', raw_text, flags=re.IGNORECASE)
                clean_json = re.sub(r'\s*```$', '', clean_json.strip())

                m = re.search(r'\{.*\}', clean_json, re.DOTALL)
                parsed = json.loads(m.group(0)) if m else json.loads(clean_json)

                LAST_GEMINI_DIAGNOSTIC = {
                    "status": "OK",
                    "error": "",
                    "model": model_name,
                    "timestamp": time.time()
                }
                return parsed
            except Exception as parse_e:
                print(f"[GEMINI MULTI-IMG JSON PARSE ERR] {parse_e}")
                LAST_GEMINI_DIAGNOSTIC = {
                    "status": "PARSE_ERROR",
                    "error": str(parse_e)[:120],
                    "model": model_name,
                    "timestamp": time.time()
                }
                continue
        else:
            err_msg = resp.text[:220].replace('\n', ' ')
            print(f"[GEMINI MULTI-IMG HTTP {resp.status_code} ({model_name})] {err_msg}")
            LAST_GEMINI_DIAGNOSTIC = {
                "status": f"HTTP_{resp.status_code}",
                "error": err_msg,
                "model": model_name,
                "timestamp": time.time()
            }
            # Tangani sebarang ralat (423 Locked, 429 Rate Limit, 403, 500/503 dll):
            # Gunakan jeda pintar berperingkat (backoff) untuk mengelakkan kegagalan berulang
            handle_gemini_retry_delay(resp, idx, next_model)
            continue

    return None

def synthesize_multi_images_offline(image_paths, captions=None):
    """
    Enjin sintesis tempatan (offline fallback) untuk menggabungkan intel daripada beberapa imej:
    - Imbas kod QR DuitNow pada setiap gambar
    - Imbas teks OCR tempatan (Windows 10/11 WinRT OCR / Tesseract / regex) pada setiap gambar
    - Ekstrak dan cantumkan nombor telefon unik, akaun bank, dan petunjuk taktik
    """
    all_phones = []
    all_accounts = []
    qr_list = []
    combined_texts = []
    
    if captions:
        for c in captions:
            if c and c.strip():
                combined_texts.append(c.strip())

    for idx, pth in enumerate(image_paths):
        # 1. Semak DuitNow QR
        qr_d = decode_duitnow_qr(pth)
        if qr_d:
            qr_list.append(qr_d)
            if qr_d.get("proxy_phone"):
                np = normalize_phone(qr_d["proxy_phone"])
                if np and np not in all_phones:
                    all_phones.append(np)
            if qr_d.get("account_number"):
                c_acc = re.sub(r'[^\d]', '', str(qr_d["account_number"]))
                if len(c_acc) >= 8 and c_acc not in all_accounts:
                    all_accounts.append(c_acc)

        # 2. Imbas OCR tempatan
        ocr_res = local_image_ocr(pth)
        if ocr_res:
            combined_texts.append(ocr_res)
            # Ekstrak nombor telefon
            for p in re.findall(r'(?:\+?60|0)[\s\-]?[1-9][0-9][\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,5}', ocr_res):
                np = normalize_phone(p)
                if np and np not in all_phones:
                    all_phones.append(np)
            # Ekstrak akaun bank
            for a in re.findall(r'\b[0-9]{8,18}\b', ocr_res):
                cd = re.sub(r'[^\d]', '', a)
                if len(cd) >= 8 and cd not in all_accounts and cd not in all_phones:
                    all_accounts.append(cd)

    full_corpus = "\n".join(combined_texts)
    
    # Pengelasan taktik daripada teks terkumpul
    tactic = "Semakan Kes Penipuan (Multi-Bukti)"
    if re.search(r'\b(?:Manage storage|Media visibility|Voice\s+Video|Kept messages)\b', full_corpus, re.IGNORECASE):
        tactic = "Profil / Kenalan WhatsApp Suspek"
    elif re.search(r'\b(?:transfer|successful|duitnow|pindahan|resit|receipt|maybank2u|cimb clicks)\b', full_corpus, re.IGNORECASE):
        tactic = "Bukti Pemindahan Wang (Resit Transaksi)"
    elif re.search(r'\b(?:pelaburan|profit|dividen|keuntungan|deposit|broker)\b', full_corpus, re.IGNORECASE):
        tactic = "Skim Pelaburan Palsu"
    elif re.search(r'\b(?:midman|escrow|official|trusted)\b', full_corpus, re.IGNORECASE):
        tactic = "Khidmat Orang Tengah Palsu (Fake Midman)"

    return {
        "has_intel": bool(all_phones or all_accounts or qr_list),
        "case_summary": f"Gabungan risikan daripada {len(image_paths)} tangkapan layar bukti kes.",
        "scam_tactic": tactic,
        "suspect_names": [],
        "primary_suspect_name": "Tidak Dinyatakan",
        "phone_numbers": all_phones,
        "primary_phone": all_phones[0] if all_phones else "",
        "bank_accounts": [{"bank": "Akaun Bank", "account_number": a, "holder_name": "N/A"} for a in all_accounts],
        "emails": re.findall(r'[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+', full_corpus),
        "qr_list": qr_list,
        "evidence_details": full_corpus[:600]
    }

def extract_multi_image_case_intel(image_paths, captions=None, user_id=None, progress_callback=None):
    """
    Enjin Bersepadu Analisis Multi-Bukti:
    1. Imbas kod QR DuitNow pada setiap gambar terlebih dahulu (100% tepat EMVCo)
    2. Hubungi Gemini Vision dengan semua imej untuk cross-examination
    3. Jika Gemini gagal/tiada API key, jatuh semula ke sintesis OCR tempatan
    4. Cantumkan semua hasil ke dalam format seragam untuk Dossier & Laporan
    """
    if not image_paths:
        return {"has_intel": False, "image_count": 0}

    # 1. Semak DuitNow QR pada setiap gambar
    all_qr_data = []
    for pth in image_paths:
        q = decode_duitnow_qr(pth)
        if q:
            all_qr_data.append(q)

    # 2. Dapatkan API Key Gemini
    active_k, key_src_label, key_type = get_effective_gemini_key(user_id)
    
    gemini_data = None
    if active_k and active_k != "YOUR_GEMINI_API_KEY_HERE":
        if progress_callback:
            progress_callback(40, f"🧠 Menyelaras {len(image_paths)} bukti serentak dengan AI Vision...", "Gemini Cross-Analysis")
        gemini_data = analyze_multi_images_with_gemini(image_paths, api_key=active_k, captions=captions, user_id=user_id)

    # 3. Jika Gemini tidak berjaya atau tiada key, gunakan sintesis tempatan
    if not gemini_data:
        if progress_callback:
            progress_callback(50, f"🔍 Mengimbas {len(image_paths)} bukti secara tempatan (Local OCR & Matrix)...", "Local Synthesis")
        offline_data = synthesize_multi_images_offline(image_paths, captions=captions)
        source_type = "OFFLINE_MULTI_OCR"
        source_badge = f"🔍 OCR Tempatan KANTOI ({len(image_paths)} Bukti)"
        final_summary = offline_data.get("case_summary", "")
        final_tactic = offline_data.get("scam_tactic", "Semakan Keselamatan Siber")
        final_name = offline_data.get("primary_suspect_name", "Tidak Dinyatakan")
        final_phones = list(offline_data.get("phone_numbers") or [])
        final_accounts_raw = offline_data.get("bank_accounts") or []
        final_emails = list(offline_data.get("emails") or [])
        final_ev_text = offline_data.get("evidence_details", "")
        final_victim_loss = None
        loss_m = re.search(r'\b(?:RM|MYR)\s*(\d+(?:[,.]\d{2})?)\b', final_ev_text, re.IGNORECASE)
        if loss_m:
            final_victim_loss = f"RM {loss_m.group(1)}"

    else:
        source_type = "AI_VISION_MULTI"
        source_badge = f"🧠 AI Gemini Vision ({len(image_paths)} Bukti Bersilang)"
        final_summary = gemini_data.get("case_summary", "")
        final_tactic = gemini_data.get("scam_tactic", "Semakan Keselamatan Siber")
        final_name = gemini_data.get("primary_suspect_name") or (gemini_data.get("suspect_names")[0] if gemini_data.get("suspect_names") else "Tidak Dinyatakan")
        final_victim_loss = gemini_data.get("victim_loss")
        final_phones = []
        for p in (gemini_data.get("phone_numbers") or []):
            np = normalize_phone(p)
            if np and np not in final_phones:
                final_phones.append(np)
        if gemini_data.get("primary_phone"):
            np = normalize_phone(gemini_data["primary_phone"])
            if np and np not in final_phones:
                final_phones.insert(0, np)
        final_accounts_raw = gemini_data.get("bank_accounts") or []
        final_emails = list(gemini_data.get("emails") or [])
        final_ev_text = gemini_data.get("evidence_details", "")

    # 4. Integrasikan maklumat kod QR jika ada
    cand_accounts = []
    formatted_bank_entries = []
    primary_bank = "N/A"
    
    for b_item in final_accounts_raw:
        if isinstance(b_item, dict):
            acc_no = re.sub(r'[^\d]', '', str(b_item.get("account_number") or ""))
            b_name = b_item.get("bank") or "Bank"
            h_name = b_item.get("holder_name") or ""
            if len(acc_no) >= 8 and acc_no not in cand_accounts:
                cand_accounts.append(acc_no)
                desc = f"{b_name} : {acc_no}"
                if h_name and h_name != "N/A":
                    desc += f" ({h_name})"
                formatted_bank_entries.append(desc)
                if primary_bank == "N/A":
                    primary_bank = b_name
        elif isinstance(b_item, str):
            acc_no = re.sub(r'[^\d]', '', b_item)
            if len(acc_no) >= 8 and acc_no not in cand_accounts:
                cand_accounts.append(acc_no)
                formatted_bank_entries.append(f"Akaun: {acc_no}")

    for qr in all_qr_data:
        if qr.get("proxy_phone"):
            np = normalize_phone(qr["proxy_phone"])
            if np and np not in final_phones:
                final_phones.insert(0, np)
        if qr.get("account_number"):
            acc_no = re.sub(r'[^\d]', '', str(qr["account_number"]))
            if len(acc_no) >= 8 and acc_no not in cand_accounts:
                cand_accounts.append(acc_no)
                qr_bank = qr.get("bank") or "DuitNow"
                qr_name = qr.get("name") or ""
                desc = f"{qr_bank} : {acc_no}"
                if qr_name:
                    desc += f" ({qr_name})"
                formatted_bank_entries.append(desc)
                if primary_bank == "N/A":
                    primary_bank = qr_bank
        if qr.get("name") and final_name in ["Tidak Dinyatakan", "N/A", ""]:
            final_name = qr["name"]

    has_intel = bool(final_phones or cand_accounts or all_qr_data)
    primary_phone = final_phones[0] if final_phones else (cand_accounts[0] if cand_accounts else "")
    primary_acc = cand_accounts[0] if cand_accounts else "N/A"
    bank_formatted_str = " | ".join(formatted_bank_entries) if formatted_bank_entries else "Tiada"

    primary_qr = all_qr_data[0] if all_qr_data else None

    return {
        "has_intel": has_intel,
        "image_count": len(image_paths),
        "source_type": source_type,
        "source_badge": source_badge,
        "case_summary": final_summary,
        "victim_loss": final_victim_loss,
        "tactic": final_tactic,
        "suspect_name": final_name,
        "phone_norm": primary_phone,
        "all_phones": final_phones,
        "bank_name": primary_bank,
        "account_number": primary_acc,
        "all_accounts": cand_accounts,
        "bank_accounts": bank_formatted_str,
        "emails": final_emails,
        "evidence_text": final_ev_text,
        "qr_detected": bool(all_qr_data),
        "qr_data": primary_qr,
        "all_qr_data": all_qr_data,
        "gemini_status": LAST_GEMINI_DIAGNOSTIC.get("status", "NOT_CONFIGURED"),
        "gemini_error": LAST_GEMINI_DIAGNOSTIC.get("error", ""),
        "key_source": key_src_label,
        "is_byok": bool(user_id and get_user_gemini_key(user_id))
    }

def local_image_ocr(image_path):
    """
    Enjin OCR tempatan hibrid berbilang lapisan:
    1. Windows 10/11 Media OCR (WinRT - pantas dan rasmi)
    2. Tesseract OCR (jika ada dipasang pada sistem atau folder python38)
    3. Imbasan rentetan bait & metadata (0 kebergantungan)
    """
    abs_path = os.path.abspath(image_path).replace('/', '\\').replace("'", "''")
    
    # Lapisan 1: Windows 10/11 WinRT OCR
    ps_cmd = (
        "Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue\n"
        "$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n"
        "if ($asTaskGeneric) {\n"
        "    function AwaitOp($op, $t) { return $asTaskGeneric.MakeGenericMethod($t).Invoke($null, @($op)).GetAwaiter().GetResult() }\n"
        "    try {\n"
        "        [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null\n"
        "        [Windows.Storage.FileAccessMode, Windows.Storage, ContentType = WindowsRuntime] | Out-Null\n"
        "        [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime] | Out-Null\n"
        "        [Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation, ContentType = WindowsRuntime] | Out-Null\n"
        "        $file = AwaitOp ([Windows.Storage.StorageFile]::GetFileFromPathAsync('" + abs_path + "')) ([Windows.Storage.StorageFile])\n"
        "        $stream = AwaitOp ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])\n"
        "        $decoder = AwaitOp ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])\n"
        "        $bitmap = AwaitOp ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])\n"
        "        $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()\n"
        "        if ($engine) {\n"
        "            $res = AwaitOp ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])\n"
        "            $res.Text\n"
        "        }\n"
        "    } catch {}\n"
        "}"
    )
    try:
        res = subprocess.run(['powershell', '-NoProfile', '-Command', ps_cmd], capture_output=True, text=True, timeout=12)
        txt = (res.stdout or '').strip()
        if txt:
            return txt
    except Exception:
        pass

    # Lapisan 2: Tesseract OCR (Windows 7 / External)
    tess_candidates = [
        os.path.join(BASE_DIR, "python38", "tesseract", "tesseract.exe"),
        r"C:\Program Files\Tesseract-OCR\tesseract.exe",
        r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
        "tesseract"
    ]
    for t_bin in tess_candidates:
        if os.path.exists(t_bin) or t_bin == "tesseract":
            try:
                res_t = subprocess.run([t_bin, abs_path, "stdout", "-l", "eng+msa", "--psm", "6"], capture_output=True, text=True, timeout=12)
                txt_t = (res_t.stdout or '').strip()
                if txt_t and len(txt_t) > 5:
                    return txt_t
            except Exception:
                pass

    # Lapisan 3: Imbasan Bait Rentetan Mentah / Metadata PNG
    try:
        with open(image_path, "rb") as f:
            raw_b = f.read(1024 * 512)
        found_strings = re.findall(rb'[\x20-\x7E]{4,}', raw_b)
        extracted_text = " ".join([s.decode('utf-8', errors='ignore') for s in found_strings])
        return extracted_text
    except Exception:
        pass

    return ''

def windows_native_ocr(image_path):
    """Pengekalan keserasian fungsi lama"""
    return local_image_ocr(image_path)

def extract_hybrid_image_intel(image_path, gemini_api_key=None, user_id=None, progress_callback=None):
    """
    Menyelaraskan gabungan QR DuitNow + AI Vision (Gemini) + OCR Tempatan (dengan sokongan BYOK)
    """
    if progress_callback:
        progress_callback(35, "🔍 Mengimbas kod QR DuitNow & mentafsir EMVCo...", "OpenCV / PyZbar Binary Matrix")

    # 1. Imbas Kod QR DuitNow / EMVCo / URL / Teks
    qr_data = decode_duitnow_qr(image_path)
    
    # 2. Imbas teks melalui AI Vision (jika ada kunci) atau Windows Native OCR
    active_key = (gemini_api_key or "").strip()
    key_src_label = ""
    if not active_key:
        active_key, key_src_label, _ = get_effective_gemini_key(user_id)
    else:
        key_src_label = "Kunci Dinyatakan"

    gemini_data = None
    if active_key:
        if progress_callback:
            progress_callback(60, "🧠 Pengecaman Teks & Nombor Telefon...", "Enjin AI Vision KANTOI")
        gemini_data = analyze_image_with_gemini(image_path, active_key, user_id=user_id)
        
    ocr_text = ""
    if not gemini_data:
        if progress_callback:
            progress_callback(65, "🔍 Menjalankan Enjin OCR Tempatan Windows...", "OCR Cadangan Luar Talian")
        ocr_text = windows_native_ocr(image_path)
        
    # Ekstrak data OCR / Vision
    vision_phones = []
    vision_name = ""
    vision_email = ""
    vision_bank = ""
    vision_acc = ""
    vision_tactic = "Penipuan Siber / Akaun Keldai"
    vision_evidence = "Bukti imej disahkan melalui Enjin Risikan KANTOI."
    source_type = "LOCAL_OCR"

    if gemini_data:
        source_type = "AI_VISION"
        raw_phones = gemini_data.get("phone_numbers", [])
        if isinstance(raw_phones, str):
            raw_phones = [raw_phones]
        elif not isinstance(raw_phones, list):
            raw_phones = []
            
        for p in raw_phones:
            np = normalize_phone(str(p))
            if np and np not in vision_phones:
                vision_phones.append(np)

        vision_name = (gemini_data.get("suspect_name") or "").strip()
        vision_email = (gemini_data.get("email") or "").strip()
        vision_bank = (gemini_data.get("bank_name") or "").strip()
        vision_acc = (gemini_data.get("account_number") or "").strip()
        vision_tactic = gemini_data.get("scam_tactic") or vision_tactic
        vision_evidence = gemini_data.get("evidence_text") or vision_evidence
        
        # Imbas nombor telefon tambahan daripada teks penuh / bukti / nama suspek jika terlepas
        all_text_search = f"{vision_name} {vision_evidence} {gemini_data.get('full_text', '')}"
        extra_phones = re.findall(r'(?:(?:\+?60|0)[\s\-]?[1-9][0-9][\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,5})', all_text_search)
        for ep in extra_phones:
            np = normalize_phone(ep)
            if np and np not in vision_phones:
                vision_phones.append(np)
                
        # Jika nama suspek sebenarnya adalah nombor telefon, bersihkan
        if vision_name and normalize_phone(vision_name) in vision_phones:
            vision_name = "Suspek (Nombor Telefon)"
    elif ocr_text:
        # Ekstrak nombor telefon
        for p in re.findall(r'(?:\+?60|0)[\s\-]?[1-9][0-9][\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,5}', ocr_text):
            np = normalize_phone(p)
            if np and np not in vision_phones:
                vision_phones.append(np)
                
        # Ekstrak akaun bank
        for am in re.findall(r'(?:ACC|AKAUN|ACCOUNT|AKAI-JN|NO ACC|NOMBOR AKAUN)\s*[:.\-]?[\s]*([0-9\s]{8,18})', ocr_text, re.IGNORECASE):
            cam = re.sub(r'\s+', '', am)
            if len(cam) >= 8 and cam.isdigit() and not vision_acc:
                vision_acc = cam
        for bk in ['MAYBANK', 'CIMB', 'BANK ISLAM', 'PUBLIC BANK', 'RHB', 'AMBANK', 'HONG LEONG', 'BSN', 'AFFIN', 'GXBANK', 'BIGPAY', 'TNG DIGITAL']:
            if re.search(r'\b' + re.escape(bk) + r'\b', ocr_text, re.IGNORECASE):
                vision_bank = bk
                break

        # 1. Kesan WhatsApp Push Name (cth: ~ Prem Kumar)
        push_m = re.search(r'~\s*([A-Za-z\s\'.]{3,35})', ocr_text)
        if push_m:
            vision_name = push_m.group(1).strip()

        # 2. Kesan Nama Rasmi / Profil
        if not vision_name:
            nm = re.search(r'(?:NAMA|NAME)\s*[:.\-]?[\s]*([A-Za-z\s@/]+?)(?:\s+(?:BANK|ACC|ACCOUNT|NOMBOR|AKAUN|Online)|\n|$)', ocr_text, re.IGNORECASE)
            if nm:
                vision_name = nm.group(1).strip()
            
        # 3. Kesan Nama Perniagaan / Organisasi (cth: OFFICIAL MIDMAN MAZE)
        if not vision_name:
            biz_m = re.search(r'\b(OFFICIAL\s+[A-Za-z\s]{3,25}|[A-Za-z0-9\s]{3,25}\s+ENTERPRISE|[A-Za-z0-9\s]{3,25}\s+SDN BHD)\b', ocr_text, re.IGNORECASE)
            if biz_m:
                vision_name = biz_m.group(1).strip()

        # 4. Kesan Nama Suspek Tepat Selepas Nombor Telefon (cth: +60 14-627 2791 Prem Kumar Voice Video...)
        if not vision_name and vision_phones:
            ui_boundary = r'(?:Voice|Video|Search|Call|Audio|Media|Links|Docs|Ringing|Calling|Manage|Storage|Notifications|Visibility|Messages|Open|Edit)'
            phone_after_m = re.search(r'(?:\+?60|0)[\s\-]?[1-9][0-9][\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,5}\s*(?:~)?\s*([A-Za-z\s\'.]{2,35}?)(?=\s+' + ui_boundary + r'|\n|$)', ocr_text)
            if phone_after_m:
                cand = phone_after_m.group(1).strip()
                if cand and len(cand) > 2 and cand.lower() not in ['voice', 'video', 'search', 'ringing', 'calling', 'ringing .']:
                    vision_name = cand

        # 5. Kesan Nama Pengirim Chat Bar Atas
        if not vision_name:
            sender_m = re.search(r'(?:^|\n)\s*([A-Za-z0-9\s]{3,30}?)(?=\s+(?:last seen|online|typing|Open now|Saya nak bag)|\n|$)', ocr_text, re.IGNORECASE)
            if sender_m:
                cand = sender_m.group(1).strip()
                if cand and len(cand) > 2 and not any(w in cand.lower() for w in ['scammer', 'edit', 'open']):
                    vision_name = cand

        # 6. Ekstrak Emel
        email_matches = re.findall(r'[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+', ocr_text)
        if email_matches:
            vision_email = email_matches[0]

        # 7. Pengelasan Taktik Penipuan Tempatan
        if re.search(r'\b(?:MIDMAN|MIDDLEMAN|REKBER)\b', ocr_text, re.IGNORECASE):
            vision_tactic = "Khidmat Orang Tengah Palsu (Fake Midman / Escrow)"
        elif re.search(r'\b(?:MONEY PACKET|DUIT RAYA|REZEKI|ANGPOW)\b', ocr_text, re.IGNORECASE):
            vision_tactic = "Penipuan Touch 'n Go Money Packet / Sedekah Palsu"
        elif re.search(r'\b(?:Ringing|Calling|Panggilan)\b', ocr_text, re.IGNORECASE) and vision_phones:
            vision_tactic = "Panggilan Suara Scammer (WhatsApp Call)"

        vision_evidence = ocr_text[:250].replace('\n', ' ')

    # 3. Gabungkan Penemuan QR + Vision/OCR (Utamakan nama manusia sebenar jika ada)
    qr_human_name = qr_data.get('name') if qr_data and qr_data.get('name') != 'Tidak Dinyatakan' and not qr_data.get('name', '').startswith('Pautan (') else None
    final_name = vision_name or qr_human_name or (qr_data.get('name') if qr_data else None) or "Tidak Dinyatakan"
    final_bank = (qr_data.get('bank') if qr_data and qr_data.get('bank') != 'N/A' else None) or vision_bank or "N/A"
    final_acc = (qr_data.get('account_number') if qr_data and qr_data.get('account_number') else None) or vision_acc or "N/A"
    
    if qr_data:
        source_type = "QR_DUITNOW" if not gemini_data else "HYBRID_QR_VISION"
        if qr_data.get('proxy_phone'):
            np = normalize_phone(qr_data['proxy_phone'])
            if np and np not in vision_phones:
                vision_phones.insert(0, np)

    has_intel = bool(vision_phones or (final_acc and final_acc != "N/A") or qr_data)
    primary_phone = vision_phones[0] if vision_phones else (final_acc if final_acc != "N/A" else "")

    bank_formatted = "N/A"
    if final_bank != "N/A" and final_acc != "N/A":
        bank_formatted = f"{final_bank} : {final_acc}"
    elif final_acc != "N/A":
        bank_formatted = f"Akaun: {final_acc}"

    # Pengelasan Taktik Profil WhatsApp jika dikesan tanda kenalan/profil
    if not gemini_data and ocr_text:
        if re.search(r'\b(?:Manage storage|Media visibility|Voice\s+Video|Kept messages)\b', ocr_text, re.IGNORECASE):
            vision_tactic = "Profil / Kenalan WhatsApp Suspek"

    return {
        "has_intel": has_intel,
        "qr_detected": bool(qr_data),
        "qr_data": qr_data,
        "source_type": source_type,
        "phone_norm": primary_phone,
        "all_phones": vision_phones,
        "email": vision_email,
        "suspect_name": final_name,
        "bank_name": final_bank,
        "account_number": final_acc,
        "bank_accounts": bank_formatted,
        "tactic": vision_tactic,
        "evidence_text": vision_evidence,
        "gemini_status": LAST_GEMINI_DIAGNOSTIC.get("status", "NOT_CONFIGURED"),
        "gemini_error": LAST_GEMINI_DIAGNOSTIC.get("error", ""),
        "key_source": key_src_label,
        "is_byok": bool(user_id and get_user_gemini_key(user_id))
    }

def download_telegram_photo(file_id):
    """Memuat turun fail gambar daripada pelayan Telegram ke fail sementara"""
    res = api_call("getFile", {"file_id": file_id})
    if res and res.get("ok"):
        file_path = res["result"].get("file_path")
        if file_path:
            download_url = f"https://api.telegram.org/file/bot{BOT_TOKEN}/{file_path}"
            try:
                r = requests.get(download_url, timeout=30)
                if r.status_code == 200:
                    ext = os.path.splitext(file_path)[1] or ".jpg"
                    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=ext)
                    tmp.write(r.content)
                    tmp.close()
                    return tmp.name
            except Exception as e:
                print(f"[DOWNLOAD ERR] Gagal muat turun gambar: {e}")
    return None

# ==============================================================================
# OPERASI PANGKALAN DATA (SQLITE)
# ==============================================================================
def query_threat(query_text):
    if not query_text or not str(query_text).strip():
        return None, ""
    clean_raw = str(query_text).strip()
    if clean_raw.lower() in ["none", "unknown", "n/a", "tidak dinyatakan", "tiada", "0"]:
        return None, ""

    norm_phone = normalize_phone(clean_raw)
    clean_digits = re.sub(r"[^\d]", "", clean_raw)
    
    if not os.path.exists(DB_PATH):
        return None, "DATABASE_MISSING"

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    match = None

    if norm_phone and len(norm_phone) >= 8:
        cur.execute("SELECT phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones FROM threat_numbers WHERE phone_norm = ? LIMIT 1", (norm_phone,))
        match = cur.fetchone()
        if not match:
            cur.execute("SELECT phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones FROM threat_numbers WHERE all_phones LIKE ? LIMIT 1", (f"%{norm_phone}%",))
            match = cur.fetchone()

    if not match and len(clean_digits) >= 8:
        cur.execute("SELECT phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones FROM threat_numbers WHERE bank_accounts LIKE ? LIMIT 1", (f"%{clean_digits}%",))
        match = cur.fetchone()

    conn.close()
    return match, norm_phone

def insert_approved_threat(phone_norm, suspect_name, category, bank_accounts, source, all_phones=None):
    dbs = [DB_PATH, BACKUP_DB_PATH]
    for db in dbs:
        if not os.path.exists(db):
            continue
        try:
            con = sqlite3.connect(db)
            cur = con.cursor()
            cur.execute("""
                INSERT OR REPLACE INTO threat_numbers 
                (phone_norm, profile_id, suspect_name, all_phones, bank_accounts, ic_number, address, evidence_links, risk_tag, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                phone_norm,
                9999,
                suspect_name,
                all_phones or phone_norm,
                bank_accounts or "N/A",
                "N/A",
                "Laporan Disahkan Komuniti",
                "Telegram @kantoiradar",
                category or "COMMUNITY_VERIFIED_SCAMMER",
                source or "Sumbangan Komuniti KANTOI (Disahkan Admin)"
            ))
            con.commit()
            con.close()
            print(f"[SQLITE] Berjaya simpan {phone_norm} ke {db}")
        except Exception as e:
            print(f"[SQLITE ERR] Gagal simpan ke {db}: {e}")

# ==============================================================================
# MOD PENGENALPASTIAN SINDIKET & CLUSTER (IDEA 1)
# ==============================================================================
def find_syndicate_cluster(db_path, query_input, norm_phone=None, suspect_name=None, bank_accounts=None):
    """
    Mengesan rangkaian sindiket (cluster) melalui pertindihan akaun bank keldai,
    nama suspek yang sama, atau nombor telefon bersiri.
    """
    if not os.path.exists(db_path):
        return {"is_syndicate": False, "cluster_count": 0, "matched_items": []}
        
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    
    target_accounts = []
    if bank_accounts and bank_accounts != "N/A":
        target_accounts = [m for m in re.findall(r'\b\d{8,18}\b', bank_accounts)]
        
    clean_name = ""
    if suspect_name and suspect_name not in ["N/A", "Tidak Dinyatakan", "Scammer", "Penyewa Akaun", "Komuniti"] and len(suspect_name.strip()) >= 4:
        clean_name = suspect_name.strip().split("|")[0].strip()
        if len(clean_name) < 4:
            clean_name = ""

    matched_rows = []
    seen_phones = set()
    if norm_phone:
        seen_phones.add(norm_phone)
    if query_input:
        seen_phones.add(query_input)

    # 1. Carian melalui Akaun Bank yang sama (Mule Account Sharing)
    for acc in target_accounts:
        cur.execute("SELECT phone_norm, suspect_name, risk_tag, bank_accounts, all_phones FROM threat_numbers WHERE bank_accounts LIKE ? LIMIT 10", (f"%{acc}%",))
        for r in cur.fetchall():
            if r[0] not in seen_phones:
                seen_phones.add(r[0])
                matched_rows.append(("Akaun Bank Sama", acc, r))

    # 2. Carian melalui Nama Suspek yang sama
    if clean_name:
        cur.execute("SELECT phone_norm, suspect_name, risk_tag, bank_accounts, all_phones FROM threat_numbers WHERE suspect_name LIKE ? LIMIT 10", (f"%{clean_name}%",))
        for r in cur.fetchall():
            if r[0] not in seen_phones:
                seen_phones.add(r[0])
                matched_rows.append(("Nama Suspek Sama", clean_name, r))

    conn.close()
    return {
        "is_syndicate": len(matched_rows) > 0,
        "cluster_count": len(matched_rows),
        "matched_items": matched_rows
    }

def format_syndicate_badge(cluster_info):
    """Menjana lencana risikan sindiket dalam balasan amaran"""
    if not cluster_info or not cluster_info.get("is_syndicate"):
        return ""
        
    count = cluster_info["cluster_count"]
    lines = [
        "",
        "🕸️ *RANGKAIAN SINDIKET DIKESAN (Cluster Intelligence)*",
        f"⚠️ _Nombor ini berkait rapat dengan *{count}* rekod scammer lain dalam pangkalan data!_",
        "👥 *Rangkaian Terhubung:*"
    ]
    for reason, key, r in cluster_info["matched_items"][:4]:
        p_num = r[0]
        s_name = r[1] or "Suspek"
        lines.append(f"   • `{p_num}` ({s_name[:20]}) — _{reason}: {key}_")
    if count > 4:
        lines.append(f"   • _...dan {count - 4} nombor proxy lain._")
    lines.append("🚨 *Petunjuk:* Operasi sindiket terancang yang berkongsi akaun keldai / profil.")
    return "\n".join(lines)


# ==============================================================================
# SEMAKAN SIAP-SEDIA SEMAKMULE PDRM CCID SECARA LANGSUNG (IDEA 4)
# ==============================================================================
def query_pdrm_semakmule_live(category, value):
    """
    Menyemak rekod aduan rasmi secara langsung di portal PDRM CCID SemakMule
    """
    clean_val = re.sub(r'[^\d]', '', str(value))
    if not clean_val or len(clean_val) < 6:
        return {"success": False, "police_reports": 0, "search_count": 0, "has_record": False}
        
    cat_param = "bank" if category == "bank" else "telefon"
    payload = {
        "data": {
            "category": cat_param,
            "telNo": clean_val if cat_param == "telefon" else "",
            "bankAccount": clean_val if cat_param == "bank" else "",
            "companyName": ""
        }
    }
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)",
        "apikey": PDRM_API_KEY,
        "Content-Type": "application/json"
    }
    try:
        r = requests.post(PDRM_SEMAKMULE_API, json=payload, headers=headers, verify=False, timeout=6)
        if r.status_code == 200:
            data = r.json()
            table_data = data.get("table_data", [])
            police_reports = 0
            if table_data and len(table_data) > 0 and len(table_data[0]) > 1:
                try:
                    police_reports = int(table_data[0][1])
                except (ValueError, TypeError):
                    police_reports = 1
            search_count = data.get("count", 0)
            return {
                "success": True,
                "category": cat_param,
                "value": clean_val,
                "police_reports": police_reports,
                "search_count": search_count,
                "has_record": police_reports > 0
            }
    except Exception as e:
        print(f"[PDRM LIVE ERR] {e}")
    return {"success": False, "police_reports": 0, "search_count": 0, "has_record": False}


# ==============================================================================
# SEMAKAN PAUTAN PHISHING & SANDBOX MALWARE APK (IDEA 3)
# ==============================================================================
def is_safe_external_url(target_url):
    """
    Perlindungan Anti-SSRF: Memastikan pautan adalah HTTP/HTTPS awam dan
    bukan alamat persendirian/dalaman (localhost, RFC 1918, link-local metadata).
    """
    try:
        import ipaddress
        p = urllib.parse.urlparse(target_url)
        if p.scheme not in ('http', 'https'):
            return False
        hostname = (p.hostname or '').strip().lower()
        if not hostname or hostname in ('localhost', '127.0.0.1', '::1', '0.0.0.0'):
            return False
        try:
            ip = ipaddress.ip_address(hostname)
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
                return False
        except ValueError:
            pass
        return True
    except Exception:
        return False

def inspect_url_phishing(url):
    """
    Menganalisis pautan web untuk mengesan pancingan data (Phishing) perbankan & bantuan palsu
    dengan kepintaran khusus membezakan pautan Touch 'n Go / Money Packet yang sebenar vs phishing,
    serta pertahanan Anti-SSRF terhadap pautan dalaman.
    """
    final_url = url
    url_l = (url or "").strip().lower()

    # 1. Semak jika Deep Link rasmi aplikasi Touch 'n Go (tngd://)
    if url_l.startswith("tngd://"):
        return {
            "url": url,
            "final_url": url,
            "domain": "tngd:// (Aplikasi Rasmi)",
            "verdict": "✅ SAH - DEEP LINK RASMI APLIKASI TOUCH 'N GO EWALLET",
            "severity": "SAFE",
            "target_brand": "TOUCH 'N GO DIGITAL",
            "reasons": [
                "Skim pautan terus (Deep Link) rasmi aplikasi Touch 'n Go eWallet.",
                "Pautan membuka aplikasi secara langsung tanpa melalui pelayar web."
            ],
            "is_tng": True,
            "is_genuine_tng": True,
            "safety_note": TNG_MONEY_PACKET_SAFETY_NOTE
        }

    # Nyah-pemendek URL dengan perlindungan Anti-SSRF
    try:
        parsed_init = urllib.parse.urlparse(url if "://" in url else "https://" + url)
        host_init = (parsed_init.hostname or "").lower()
        shorteners = ['bit.ly', 'tinyurl.com', 't.co', 'is.gd', 'cutt.ly', 'rb.gy', 'wa.me', 't.me']
        if is_safe_external_url(url) and any(host_init == s or host_init.endswith('.' + s) for s in shorteners):
            # Anti-SSRF: allow_redirects dimatikan bagi memeriksa sasaran penghalaan
            r = requests.head(url, allow_redirects=False, timeout=5, headers={"User-Agent": "Mozilla/5.0"})
            if r.status_code in (301, 302, 303, 307, 308) and "Location" in r.headers:
                target_loc = r.headers["Location"]
                if is_safe_external_url(target_loc):
                    final_url = target_loc
    except Exception:
        pass

    parsed = urllib.parse.urlparse(final_url if "://" in final_url else "https://" + final_url)
    hostname = (parsed.hostname or '').lower()
    path = parsed.path.lower()
    query = parsed.query.lower()
    combined_target = f"{hostname}{path}?{query}"
    
    reasons = []
    verdict = "BERSIH / TIADA INDIKATOR PHISHING"
    severity = "INFO"
    target_brand = ""
    is_tng = False
    is_genuine_tng = False
    safety_note = None

    # 2. PENGESANAN PINTAR TOUCH 'N GO / TNG DIGITAL (SEBENAR VS PHISHING)
    # A. Semak jika domain rasmi Touch 'n Go / TNG Digital Sdn Bhd
    for leg in TNG_GENUINE_DOMAINS:
        if hostname == leg or hostname.endswith('.' + leg):
            is_genuine_tng = True
            is_tng = True
            break

    if is_genuine_tng:
        verdict = "✅ SAH - PAUTAN RASMI TOUCH 'N GO DIGITAL SDN BHD"
        severity = "SAFE"
        target_brand = "TOUCH 'N GO DIGITAL"
        reasons.append("Domain rasmi disahkan milik TNG Digital Sdn Bhd.")
        reasons.append("Disahkan selamat daripada ancaman pancingan data (phishing).")
        safety_note = TNG_MONEY_PACKET_SAFETY_NOTE
        return {
            "url": url,
            "final_url": final_url,
            "domain": hostname,
            "verdict": verdict,
            "severity": severity,
            "target_brand": target_brand,
            "reasons": reasons,
            "is_tng": True,
            "is_genuine_tng": True,
            "safety_note": safety_note
        }

    # B. Semak jika pautan luar cuba menyamar sebagai Touch 'n Go / Money Packet (PHISHING TNG)
    tng_indicators = [k for k in TNG_PHISHING_KEYWORDS if k in combined_target]
    if tng_indicators and not is_genuine_tng:
        is_tng = True
        verdict = "🚨 BAHAYA - LAMAN PANCINGAN DATA (PHISHING) TNG MONEY PACKET PALSU!"
        severity = "CRITICAL"
        target_brand = "TOUCH 'N GO EWALLET (MONEY PACKET SCAM)"
        reasons.append(f"Domain luar `{hostname}` menyamar sebagai Touch 'n Go / Money Packet menggunakan indikator: {', '.join(tng_indicators[:3])}")
        reasons.append("TNG Digital Sdn Bhd HANYA menggunakan domain rasmi `*.tngdigital.com.my` atau `*.touchngo.com.my`.")
        reasons.append("AMARAN: Scammer berniat memancing Nombor Telefon, Kata Laluan, PIN 6-digit dan OTP eWallet mangsa!")
        safety_note = TNG_MONEY_PACKET_SAFETY_NOTE
        return {
            "url": url,
            "final_url": final_url,
            "domain": hostname,
            "verdict": verdict,
            "severity": severity,
            "target_brand": target_brand,
            "reasons": reasons,
            "is_tng": True,
            "is_genuine_tng": False,
            "safety_note": safety_note
        }

    # 3. Semak penyamaran jenama bank rasmi
    for brand, legits in OFFICIAL_DOMAINS.items():
        if brand in hostname:
            if not any(hostname == leg or hostname.endswith('.' + leg) for leg in legits):
                verdict = f"BAHAYA - LAMAN PERBANKAN PALSU (PHISHING {brand.upper()})!"
                severity = "CRITICAL"
                target_brand = brand.upper()
                reasons.append(f"Menyamar sebagai perbankan rasmi {brand.upper()} menggunakan domain luar: `{hostname}`")
                break

    # 4. Semak umpan bantuan kerajaan / subsidi
    if severity != "CRITICAL":
        for kw in GOV_KEYWORDS:
            if kw in hostname or kw in path:
                if not (hostname.endswith('.gov.my') or hostname.endswith('.my')):
                    verdict = "RISIKO TINGGI - PANCINGAN BANTUAN KERAJAAN / APLIKASI PALSU"
                    severity = "HIGH"
                    target_brand = f"Bantuan Kerajaan ({kw.upper()})"
                    reasons.append(f"Menggunakan kata kunci bantuan (`{kw}`) tanpa pengesahan domain rasmi `.gov.my`")
                    break

    # 5. Semak akhiran domain berisiko tinggi
    for tld in SUSPICIOUS_TLDS:
        if hostname.endswith(tld):
            reasons.append(f"Menggunakan akhiran domain risiko tinggi sindiket (`{tld}`)")
            if severity == "INFO":
                severity = "MEDIUM"
                verdict = "MENCURIGAKAN - DOMAIN RISIKO TINGGI"

    # 6. Semak muat turun APK terus
    if path.endswith('.apk'):
        reasons.append("Pautan memuat turun fail aplikasi Android (`.apk`) secara terus dari web tidak sah!")
        verdict = "AMARAN - PAUTAN DROPPER MALWARE APK"
        severity = "CRITICAL"

    return {
        "url": url,
        "final_url": final_url,
        "domain": hostname,
        "verdict": verdict,
        "severity": severity,
        "target_brand": target_brand,
        "reasons": reasons,
        "is_tng": is_tng,
        "is_genuine_tng": is_genuine_tng,
        "safety_note": safety_note
    }

def download_url_to_temp_apk(url, max_size_mb=25):
    """
    Memuat turun fail APK daripada pautan web secara selamat ke dalam
    sandbox sementara untuk analisis kebenaran trojan perbankan & C2 bot.
    """
    try:
        headers = {"User-Agent": "Mozilla/5.0 (Linux; Android 13; SM-S908B)"}
        r = requests.get(url, stream=True, timeout=12, headers=headers, verify=False)
        if r.status_code == 200:
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".apk")
            downloaded = 0
            for chunk in r.iter_content(chunk_size=65536):
                if chunk:
                    tmp.write(chunk)
                    downloaded += len(chunk)
                    if downloaded > max_size_mb * 1024 * 1024:
                        break
            tmp.close()
            if zipfile.is_zipfile(tmp.name):
                return tmp.name
            else:
                if os.path.exists(tmp.name):
                    try:
                        os.unlink(tmp.name)
                    except Exception:
                        pass
    except Exception as e:
        print(f"[QUISHING APK DL ERR] {e}")
    return None

def inspect_apk_malware(apk_path, original_filename="aplikasi.apk"):
    """
    Sandbox Statik KANTOI: Menganalisis fail APK untuk kebenaran trojan perbankan,
    pencurian SMS OTP, dan rentetan bot C2 tanpa perlu memasang aplikasi.
    """
    if not os.path.exists(apk_path):
        return {"error": "Fail tidak ditemui."}

    file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
    with open(apk_path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()

    perms = set()
    dex_tokens = []
    has_banking_strings = False
    
    try:
        with zipfile.ZipFile(apk_path, "r") as zf:
            namelist = zf.namelist()
            manifest_raw = b""
            if "AndroidManifest.xml" in namelist:
                manifest_raw = zf.read("AndroidManifest.xml")
                
                # Ekstrak permissions daripada string pool binary AXML (UTF-8 dan UTF-16LE)
                for m in re.finditer(rb'permission\.([A-Za-z0-9_]+)', manifest_raw):
                    perms.add(m.group(1).decode("utf-8", errors="ignore"))
                for m in re.finditer(rb'p\x00e\x00r\x00m\x00i\x00s\x00s\x00i\x00o\x00n\x00\.\x00([A-Za-z0-9_\x00]+)', manifest_raw):
                    raw_str = m.group(1).replace(b"\x00", b"").decode("utf-8", errors="ignore")
                    p_name = re.match(r'^[A-Za-z0-9_]+', raw_str)
                    if p_name:
                        perms.add(p_name.group(0))

            # Imbas rentetan DEX untuk token Bot Telegram atau sasaran bank
            for name in namelist:
                if name.endswith(".dex"):
                    dex_data = zf.read(name)
                    found_tokens = re.findall(rb'bot[0-9]{8,10}:[A-Za-z0-9_-]{35}', dex_data)
                    if found_tokens:
                        dex_tokens.extend([t.decode("utf-8", errors="ignore") for t in found_tokens])
                    if any(b in dex_data for b in [b'com.maybank2u', b'com.cimb', b'touchngo', b'my.com.tngdigital']):
                        has_banking_strings = True
    except Exception as e:
        return {"error": f"Gagal membaca arkib APK: {e}"}

    danger_perms = []
    high_risk_list = {
        "RECEIVE_SMS": "Membaca mesej SMS masuk secara langsung (Pintasan TAC/OTP)",
        "READ_SMS": "Membaca peti masuk SMS peribadi (Kecurian TAC/OTP)",
        "SEND_SMS": "Menghantar mesej SMS tanpa pengetahuan mangsa",
        "BIND_ACCESSIBILITY_SERVICE": "Kawalan Skrin Penuh (Accessibility Service / Keylogger)",
        "SYSTEM_ALERT_WINDOW": "Paparan Bertindih Skrin (Overlay palsu login bank)",
        "REQUEST_INSTALL_PACKAGES": "Memasang aplikasi luar secara senyap (Dropper)",
        "RECORD_AUDIO": "Intipan Mikrofon / Rakaman Suara",
        "READ_CONTACTS": "Mengekstrak senarai kenalan buku telefon"
    }
    
    for p, desc in high_risk_list.items():
        if p in perms:
            danger_perms.append((p, desc))

    # Tentukan Tahap Ancaman
    verdict = "APLIKASI BIASA / BERSIH"
    severity = "INFO"

    if (any(p in perms for p in ["RECEIVE_SMS", "READ_SMS"]) and 
        any(p in perms for p in ["BIND_ACCESSIBILITY_SERVICE", "SYSTEM_ALERT_WINDOW"])):
        verdict = "🚨 BAHAYA KRITIKAL: DISAHKAN TROJAN PERBANKAN (BANKING MALWARE)!"
        severity = "CRITICAL"
    elif any(p in perms for p in ["RECEIVE_SMS", "READ_SMS", "BIND_ACCESSIBILITY_SERVICE"]):
        verdict = "⚠️ RISIKO TINGGI: DISYAKI PENCURI SMS TAC / SPYWARE"
        severity = "HIGH"
    elif danger_perms:
        verdict = "⚠️ AMARAN: APLIKASI MEMINTA KEBENARAN BERISIKO TINGGI"
        severity = "MEDIUM"

    return {
        "filename": original_filename,
        "size_mb": round(file_size_mb, 2),
        "sha256": sha256,
        "danger_perms": danger_perms,
        "all_permissions": sorted(list(perms)),
        "dex_tokens": list(set(dex_tokens)),
        "has_banking_strings": has_banking_strings,
        "verdict": verdict,
        "severity": severity
    }


# ==============================================================================
# BULETIN RADAR ANCAMAN HARIAN DARI SUMBER BERITA UTAMA (IDEA 6)
# ==============================================================================
def fetch_malaysia_scam_news(limit=5):
    """
    Mengambil suapan berita kes penipuan & jenayah siber Malaysia terkini
    daripada sumber berita utama (Berita Harian, Astro Awani, Harian Metro, dsb)
    """
    req = urllib.request.Request(MALAYSIA_SCAM_NEWS_RSS, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            root = ET.fromstring(resp.read())
        items = root.findall("./channel/item")
        articles = []
        for it in items[:limit]:
            title = it.find("title").text if it.find("title") is not None else "Kes Penipuan Terkini"
            link = it.find("link").text if it.find("link") is not None else ""
            pub_date = it.find("pubDate").text if it.find("pubDate") is not None else ""
            source_el = it.find("source")
            source = source_el.text if source_el is not None else "Media Utama"
            
            clean_title = re.sub(r'\s*-\s*[^-]+$', '', title).strip()
            articles.append({
                "title": clean_title,
                "source": source,
                "link": link,
                "date": pub_date[:16]
            })
        return articles
    except Exception as e:
        print(f"[NEWS ERR] {e}")
        return []

def format_scam_radar_bulletin(limit=5):
    """Menjana teks buletin berita jenayah siber harian untuk tatapan pengguna"""
    news = fetch_malaysia_scam_news(limit)
    today_str = datetime.datetime.now().strftime("%d %B %Y")
    
    lines = [
        "📰 *BULETIN RADAR ANCAMAN SIBER & SCAM MALAYSIA*",
        f"📅 *Tarikh:* {today_str}",
        "📡 *Sumber Liputan:* _Berita Harian, Astro Awani, Harian Metro, Bernama, TV3_",
        "━━━━━━━━━━━━━━━━━━━━━━━━━━"
    ]
    
    if not news:
        lines.append("⚠️ _Tiada berita baharu dapat dimuat turun buat masa ini._")
    else:
        for idx, a in enumerate(news, 1):
            lines.append(f"*{idx}. [{a['source']}]* {a['title']}")
            if a.get('link'):
                lines.append(f"   🔗 [Baca Penuh di Sini]({a['link']})\n")
                
    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
    lines.append("💡 *Peringatan KANTOI:* _Jangan sesekali klik pautan tidak sah atau melayan panggilan individu mencurigakan. Laporkan nombor scammer terus kepada @crowdcallerbot!_")
    return "\n".join(lines)


def fetch_google_cse_snippets(query_target, max_results=2):
    """
    Menyemak hasil carian media sosial & forum secara automatik:
    1. Google Custom Search JSON API (jika ada kunci API Google rasmi AIzaSy...)
    2. Pangkalan data komuniti tempatan KANTOI (Lowyat, CariGold, KenaScam)
    """
    if not query_target or not str(query_target).strip():
        return []

    raw_str = str(query_target).strip()
    if raw_str.lower() in ["none", "unknown", "n/a", "tidak dinyatakan", "tiada", "0"]:
        return []

    clean_target = re.sub(r'[^\d]', '', raw_str)
    if not clean_target or len(clean_target) < 8:
        clean_target = raw_str

    if len(clean_target) < 3:
        return []

    norm = normalize_phone(clean_target)
    search_phone = norm if norm else clean_target
        
    snippets = []
    seen_links = set()
    google_key = get_gemini_api_key()
    
    # 1. Cuba Google Custom Search JSON API jika ada kunci Google Cloud (AIzaSy...)
    if google_key and google_key.startswith("AIzaSy"):
        try:
            api_url = f"https://www.googleapis.com/customsearch/v1?key={google_key}&cx={GOOGLE_CSE_CX}&q={urllib.parse.quote(clean_target)}&num={max_results}"
            r = requests.get(api_url, timeout=4)
            if r.status_code == 200:
                data = r.json()
                for item in data.get("items", [])[:max_results]:
                    title = item.get("title", "").strip()
                    snippet = item.get("snippet", "").strip().replace("\n", " ")
                    link = item.get("link", "")
                    domain = urllib.parse.urlparse(link).netloc.replace("www.", "")
                    if link:
                        seen_links.add(link)
                    snippets.append({
                        "source": domain.upper(),
                        "title": title[:65],
                        "snippet": snippet[:120],
                        "link": link
                    })
        except Exception:
            pass
            
    # 2. Semak rekod forum dalam Pangkalan Data Komuniti KANTOI (Lowyat/CariGold/KenaScam)
    # ELAKKAN WILDCARD (LIKE %%) DENGAN MEMASTIKAN SYARAT KUKUH
    if not snippets or len(snippets) < max_results:
        try:
            where_clauses = []
            params = []

            if search_phone and len(search_phone) >= 3:
                where_clauses.append("phone_norm = ?")
                params.append(search_phone)
                where_clauses.append("all_phones LIKE ?")
                params.append(f"%{search_phone}%")

            if clean_target and len(clean_target) >= 4 and clean_target != search_phone:
                where_clauses.append("bank_accounts LIKE ?")
                params.append(f"%{clean_target}%")
                where_clauses.append("suspect_name LIKE ?")
                params.append(f"%{clean_target}%")

            if where_clauses:
                conn = sqlite3.connect(DB_PATH)
                cur = conn.cursor()
                sql = f"""
                    SELECT suspect_name, source, evidence_links, risk_tag 
                    FROM threat_numbers 
                    WHERE {' OR '.join(where_clauses)}
                    LIMIT ?
                """
                params.append(max_results * 2)
                cur.execute(sql, tuple(params))
                rows = cur.fetchall()
                conn.close()

                for r in rows:
                    s_name, src, ev_links, r_tag = r
                    if ev_links and ev_links != "N/A":
                        for lk in ev_links.split(" | "):
                            lk = lk.strip()
                            if not lk or lk in seen_links:
                                continue
                            seen_links.add(lk)
                            domain = urllib.parse.urlparse(lk).netloc.replace("www.", "") or src
                            snippets.append({
                                "source": domain.upper(),
                                "title": f"Aduan: {s_name}" if s_name else f"Kes {src}",
                                "snippet": f"Aduan dilaporkan di {src} ({r_tag})",
                                "link": lk
                            })
                            if len(snippets) >= max_results:
                                break
                    elif src:
                        key_tag = f"{src}:{s_name}"
                        if key_tag not in seen_links:
                            seen_links.add(key_tag)
                            snippets.append({
                                "source": src.upper(),
                                "title": f"Aduan: {s_name or clean_target}",
                                "snippet": f"Rekod pertikaian dalam pangkalan komuniti KANTOI ({src})",
                                "link": ""
                            })
                    if len(snippets) >= max_results:
                        break
        except Exception as e:
            print(f"[CSE LOCAL DB ERR] {e}")

    return snippets[:max_results]


def generate_google_cse_url(query):
    """
    Menjana URL Carian Khas Google CSE mengikut konfigurasi KANTOI Android.
    Engine ID (cx): 53a0041f2f24f4e3b
    Format: https://cse.google.com/cse?cx=53a0041f2f24f4e3b&q={clean_query}#gsc.tab=0&gsc.q={clean_query}
    """
    clean_q = (str(query) if query else "").strip()
    digits = re.sub(r'[^\d]', '', clean_q)
    if digits.startswith("60") and len(digits) >= 10:
        target = "0" + digits[2:]
    elif digits and len(digits) >= 8:
        target = digits
    else:
        target = clean_q
    encoded = urllib.parse.quote(target)
    return f"https://cse.google.com/cse?cx={GOOGLE_CSE_CX}&q={encoded}#gsc.tab=0&gsc.q={encoded}"

def format_social_media_intel_report(target_q):
    """
    Menjana laporan risikan media sosial & forum KANTOI terus di dalam Telegram chat
    tanpa memerlukan pengguna membuka tetingkap pelayar web luaran.
    """
    if not target_q or not str(target_q).strip() or str(target_q).strip().lower() in ["none", "unknown", "n/a", "tidak dinyatakan", "tiada", "0"]:
        return (
            "⚠️ <b>Maklumat Sasaran Tidak Sah:</b>\n\n"
            "Sila masukkan nombor telefon atau nombor akaun bank yang ingin disemak.\n"
            "Contoh carian:\n"
            "• <code>/cse 01139444255</code>\n"
            "• <code>/cse 112028138548</code>"
        )
    clean_display = str(target_q).strip()
    norm = normalize_phone(clean_display)
    search_key = norm if norm else clean_display
    snippets = fetch_google_cse_snippets(search_key, max_results=5)
    rec, _ = query_threat(search_key)
    
    # Semak juga PDRM secara langsung
    clean_digits = re.sub(r'[^\d]', '', clean_display)
    is_bank = len(clean_digits) >= 11 or not clean_display.startswith(("01", "+601", "601"))
    cat = "bank" if is_bank else "telefon"
    pdrm_info = query_pdrm_semakmule_live(cat, clean_display)
    
    lines = [
        "🌐 <b>HASIL RISIKAN MEDIA SOSIAL & FORUM KANTOI</b>",
        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
        f"🔍 <b>Sasaran Carian:</b> <code>{html.escape(clean_display)}</code>"
    ]
    
    if rec:
        phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones = rec
        lines.append(f"👤 <b>Nama Suspek:</b> {html.escape(suspect_name or 'Tidak Dinyatakan')}")
        if bank_accounts and bank_accounts != 'N/A':
            lines.append(f"💳 <b>Akaun Terlibat:</b> <code>{html.escape(bank_accounts[:65])}</code>")
        lines.append("🚨 <b>Status Komuniti:</b> <b>TERCATAT DALAM REKOD PERTIKAIAN / BLACKLIST</b>")
    elif snippets:
        lines.append("⚠️ <b>Status Komuniti:</b> <b>ADUAN AKTIF DIKESAN DI FORUM TEMPATAN</b>")
    else:
        lines.append("✅ <b>Status Komuniti:</b> <b>TIADA ADUAN DIKESAN</b>")
        
    if pdrm_info.get("police_reports", 0) > 0:
        lines.append(f"🏛️ <b>PDRM SemakMule:</b> 🚨 <b>{pdrm_info['police_reports']} Laporan Polis Rasmi!</b> (Dicari {pdrm_info['search_count']} kali)")
    elif pdrm_info.get("success"):
        lines.append(f"🏛️ <b>PDRM SemakMule:</b> 0 Laporan Polis (Dicari {pdrm_info['search_count']} kali)")
        
    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
    
    if snippets:
        lines.append("📚 <b>Aduan Forum & Media Sosial Dikesan:</b>")
        for i, cs in enumerate(snippets, 1):
            s_source = html.escape(cs.get("source", "FORUM"))
            s_title = html.escape(cs.get("title", "Aduan Komuniti"))
            s_snip = html.escape(cs.get("snippet", ""))
            s_link = cs.get("link", "")
            
            lines.append(f"<b>{i}. [{s_source}]</b> {s_title}")
            if s_snip:
                lines.append(f"   <i>{s_snip}</i>")
            if s_link and s_link.startswith(("http://", "https://")):
                lines.append(f'   🔗 <a href="{html.escape(s_link)}">Buka Thread Perbincangan</a>')
            lines.append("")
        lines.append("⚠️ <i>Akaun/nombor ini pernah dibincangkan oleh komuniti awam. Elakkan sebarang pemindahan wang atau transaksi tanpa perlindungan selamat.</i>")
    else:
        lines.extend([
            "📚 <b>Liputan Risikan Enjin KANTOI:</b>",
            "• Forum Lowyat.NET (Dispute Resolution Corner)",
            "• CariGold Forum (Scam Alert)",
            "• Pangkalan Data KenaScam Malaysia",
            "• Perbincangan Terbuka Facebook & Twitter/X",
            "",
            "ℹ️ <i>Tiada aduan jenayah, resit transaksi mencurigakan, atau pertikaian terbuka ditemui di forum komuniti untuk sasaran ini setakat ini.</i>"
        ])
        
    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
    lines.append("💡 <i>Ketik butang di bawah untuk membuka paparan carian web penuh (Google CSE) di pelayar jika perlu:</i>")
    return "\n".join(lines)

def get_threat_check_keyboard(query_input, norm_phone=None, has_threat=False):
    target = norm_phone if norm_phone else query_input
    safe_cb = re.sub(r'[^a-zA-Z0-9_\-\.]', '', str(target))[:45]
    if not safe_cb:
        safe_cb = "unknown"
    kb = [
        [
            {"text": "🌐 Semak Media Sosial (Google CSE)", "callback_data": f"cselive_{safe_cb}"}
        ],
        [
            {"text": "🏛️ Portal PDRM SemakMule", "url": "https://semakmule.rmp.gov.my"},
            {"text": "📰 Buletin Scam", "callback_data": "read_bulletin_now"}
        ]
    ]
    return {"inline_keyboard": kb}

# ==============================================================================
# FORMATTER MESEJ
# ==============================================================================

# ==============================================================================
# ENJIN DOSSIER RISIKAN SIBER BERSEPADU (1-PAPARAN / 1-CLICK UNIFIED DOSSIER)
# ==============================================================================
def build_unified_cyber_dossier(
    phones=None,
    accounts=None,
    qr_data=None,
    suspect_name="Tidak Dinyatakan",
    bank_name="N/A",
    email="",
    tactic="Semakan Keselamatan Siber",
    source_badge="🤖 AI Vision & Matrix OCR",
    tmp_id=None,
    is_admin=False,
    raw_query="",
    evidence_summary=None,
    image_count=1,
    victim_loss=None
):
    """
    Menjana Kad Risikan Siber Bersepadu (1-Paparan) merangkumi:
    - PDRM CCID SemakMule (Telefon & Bank Serentak)
    - Pangkalan Data Ancaman KANTOI (6,100+ Rekod)
    - Pengesanan Rangkaian Sindiket (Cluster Intelligence)
    - Pengecaman Kod QR DuitNow (EMVCo Binary Decoder)
    - Carian Media Sosial & Forum Google CSE (Lowyat, CariGold, FB, KenaScam)
    Semua dalam 1 paparan mesej tanpa perlu menekan 2-3 butang berasingan!
    """
    # 1. Bersihkan & kumpulkan semua nombor telefon unik
    all_phones = []
    if phones:
        if isinstance(phones, str): phones = [phones]
        for p in phones:
            np = normalize_phone(p)
            if np and np not in all_phones:
                all_phones.append(np)
                
    if qr_data and qr_data.get("proxy_phone"):
        np = normalize_phone(qr_data["proxy_phone"])
        if np and np not in all_phones:
            all_phones.append(np)
            
    if raw_query:
        for p in re.findall(r'(?:\+?60|0)[\s\-]?[1-9][0-9][\s\-]?[0-9]{3,4}[\s\-]?[0-9]{3,5}', raw_query):
            np = normalize_phone(p)
            if np and np not in all_phones:
                all_phones.append(np)

    # 2. Bersihkan & kumpulkan semua akaun bank unik
    all_accounts = []
    if accounts:
        if isinstance(accounts, str): accounts = [accounts]
        for acc in accounts:
            c_acc = re.sub(r'[^\d]', '', str(acc))
            if len(c_acc) >= 8 and c_acc not in all_accounts:
                all_accounts.append(c_acc)
                
    if qr_data and qr_data.get("account_number"):
        c_acc = re.sub(r'[^\d]', '', str(qr_data["account_number"]))
        if len(c_acc) >= 8 and c_acc not in all_accounts:
            all_accounts.append(c_acc)

    if raw_query:
        for acc in re.findall(r'\b[0-9]{8,18}\b', raw_query):
            c_acc = re.sub(r'[^\d]', '', acc)
            if len(c_acc) >= 8 and c_acc not in all_accounts and c_acc not in all_phones:
                all_accounts.append(c_acc)

    # Jika langsung tiada nombor atau akaun dikesan daripada query teks
    if not all_phones and not all_accounts and raw_query:
        np = normalize_phone(raw_query)
        if np: all_phones.append(np)
        else:
            cd = re.sub(r'[^\d]', '', raw_query)
            if len(cd) >= 8: all_accounts.append(cd)

    # 3. Kumpul Risikan bagi setiap Entiti
    is_threat = False
    phone_intel_list = []
    acc_intel_list = []
    combined_cse_snippets = []
    seen_cse_links = set()

    # Risikan Telefon
    for p in all_phones:
        rec_kantoi, _ = query_threat(p)
        pdrm_info = query_pdrm_semakmule_live("telefon", p)
        cluster = find_syndicate_cluster(DB_PATH, p, norm_phone=p)
        cse = fetch_google_cse_snippets(p, max_results=2)
        
        has_p_threat = (rec_kantoi is not None) or (pdrm_info.get("police_reports", 0) > 0)
        if has_p_threat:
            is_threat = True
            
        for cs in cse:
            if cs.get("link") and cs["link"] not in seen_cse_links:
                seen_cse_links.add(cs["link"])
                combined_cse_snippets.append(cs)
            elif not cs.get("link") and cs not in combined_cse_snippets:
                combined_cse_snippets.append(cs)
                
        phone_intel_list.append({
            "phone": p,
            "rec_kantoi": rec_kantoi,
            "pdrm": pdrm_info,
            "cluster": cluster,
            "has_threat": has_p_threat
        })

    # Risikan Akaun Bank
    for acc in all_accounts:
        rec_kantoi, _ = query_threat(acc)
        pdrm_info = query_pdrm_semakmule_live("bank", acc)
        cluster = find_syndicate_cluster(DB_PATH, acc, bank_accounts=acc)
        cse = fetch_google_cse_snippets(acc, max_results=2)
        
        has_acc_threat = (rec_kantoi is not None) or (pdrm_info.get("police_reports", 0) > 0)
        if has_acc_threat:
            is_threat = True
            
        for cs in cse:
            if cs.get("link") and cs["link"] not in seen_cse_links:
                seen_cse_links.add(cs["link"])
                combined_cse_snippets.append(cs)
            elif not cs.get("link") and cs not in combined_cse_snippets:
                combined_cse_snippets.append(cs)
                
        acc_intel_list.append({
            "account": acc,
            "bank": bank_name if bank_name != "N/A" else "Akaun Bank",
            "rec_kantoi": rec_kantoi,
            "pdrm": pdrm_info,
            "cluster": cluster,
            "has_threat": has_acc_threat
        })

    # Risikan Kod QR (DuitNow EMVCo vs Pautan URL / Quishing)
    has_qr = bool(qr_data and qr_data.get("qr_detected"))
    qr_danger = False
    qr_url_res = None
    if has_qr and qr_data.get("url"):
        qr_url_res = inspect_url_phishing(qr_data["url"])
        if qr_url_res.get("severity") in ["CRITICAL", "HIGH"]:
            is_threat = True
            qr_danger = True

    # 4. Bina Kad Dossier HTML Bersepadu
    status_header = (
        "🚨 <b>STATUS: ANCAMAN SCAMMER DIKESAN!</b>" 
        if is_threat else 
        "✅ <b>STATUS: REKOD BERSIH (TIADA ADUAN RASMI)</b>"
    )

    dossier_title = (
        f"🛡️ <b>DOSSIER KES BERSEPADU KANTOI ({image_count} BUKTI BERGAMBAR)</b>"
        if image_count > 1 else
        "🛡️ <b>DOSSIER RISIKAN SIBER KANTOI (1-PAPARAN)</b>"
    )

    lines = [
        dossier_title,
        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
        f"📊 {status_header}",
        ""
    ]

    # Seksyen 1: Profil & Identiti Suspek
    clean_sname = suspect_name if suspect_name not in ["N/A", "Tidak Dinyatakan", ""] else ""
    if clean_sname or tactic != "Semakan Keselamatan Siber" or email or evidence_summary or image_count > 1:
        lines.append("👤 <b>PROFIL & SINTESIS BUKTI KES:</b>")
        if clean_sname:
            lines.append(f"• <b>Nama / Profil:</b> {html.escape(clean_sname)}")
        if tactic:
            lines.append(f"• <b>Taktik / Modus:</b> <i>{html.escape(tactic)}</i>")
        if victim_loss:
            lines.append(f"• <b>Kerugian Dilaporkan:</b> <code>{html.escape(str(victim_loss))}</code>")
        if email:
            lines.append(f"• <b>Emel Suspek:</b> <code>{html.escape(email)}</code>")
        if image_count > 1:
            lines.append(f"• <b>Bukti Dihubungkait:</b> <code>{image_count} Tangkapan Layar (Satu Kes)</code>")
        if evidence_summary:
            lines.append(f"• <b>Sintesis Kes:</b> <i>{html.escape(evidence_summary)}</i>")
        lines.append(f"• <b>Enjin Pengesanan:</b> {source_badge}")
        lines.append("")

    # Seksyen 2: Nombor Telefon
    if phone_intel_list:
        lines.append("📱 <b>NOMBOR TELEFON SASARAN:</b>")
        for pi in phone_intel_list:
            p_str = pi["phone"]
            pdrm = pi["pdrm"]
            rec = pi["rec_kantoi"]
            cl = pi["cluster"]

            lines.append(f"• <b>Nombor:</b> <code>{p_str}</code>")
            # PDRM
            if pdrm.get("success"):
                reps = pdrm.get("police_reports", 0)
                searches = pdrm.get("search_count", 0)
                if reps > 0:
                    lines.append(f"  🏛️ <b>PDRM SemakMule:</b> 🚨 <b>{reps} Laporan Polis Rasmi!</b> (Dicari {searches} kali)")
                else:
                    lines.append(f"  🏛️ <b>PDRM SemakMule:</b> 0 Laporan Polis (Dicari {searches} kali)")
            else:
                lines.append("  🏛️ <b>PDRM SemakMule:</b> 0 Laporan Polis")

            # KANTOI DB
            if rec:
                r_tag = rec[2] or "HIGH_RISK"
                s_rec_name = rec[1] or "Scammer Disahkan"
                lines.append(f"  🛡️ <b>Pangkalan KANTOI:</b> 🚨 <b>{html.escape(r_tag)}</b> ({html.escape(s_rec_name)})")
            else:
                lines.append("  🛡️ <b>Pangkalan KANTOI:</b> <b>Rekod Bersih (6,100+ Senarai)</b>")

            # Sindiket
            if cl.get("is_syndicate"):
                c_cnt = cl.get("cluster_count", 0)
                lines.append(f"  🕸️ <b>Rangkaian Sindiket:</b> ⚠️ <i>Berkait dengan {c_cnt} rekod scammer lain!</i>")
            lines.append("")

    # Seksyen 3: Akaun Bank
    if acc_intel_list:
        lines.append("💳 <b>AKAUN BANK SASARAN:</b>")
        for ai in acc_intel_list:
            acc_str = ai["account"]
            b_name = ai["bank"]
            pdrm = ai["pdrm"]
            rec = ai["rec_kantoi"]
            cl = ai["cluster"]

            lines.append(f"• <b>No. Akaun:</b> <code>{acc_str}</code> ({html.escape(b_name)})")
            if pdrm.get("success"):
                reps = pdrm.get("police_reports", 0)
                searches = pdrm.get("search_count", 0)
                if reps > 0:
                    lines.append(f"  🏛️ <b>PDRM SemakMule:</b> 🚨 <b>{reps} Laporan Polis Rasmi!</b> (Dicari {searches} kali)")
                else:
                    lines.append(f"  🏛️ <b>PDRM SemakMule:</b> 0 Laporan Polis (Dicari {searches} kali)")
            else:
                lines.append("  🏛️ <b>PDRM SemakMule:</b> 0 Laporan Polis")

            if rec:
                r_tag = rec[2] or "HIGH_RISK_MULE"
                lines.append(f"  🛡️ <b>Pangkalan KANTOI:</b> 🚨 <b>{html.escape(r_tag)}</b>")
            else:
                lines.append("  🛡️ <b>Pangkalan KANTOI:</b> <b>Rekod Bersih (Akaun Keldai Tidak Dikesan)</b>")

            if cl.get("is_syndicate"):
                c_cnt = cl.get("cluster_count", 0)
                lines.append(f"  🕸️ <b>Rangkaian Sindiket:</b> ⚠️ <i>Akaun ini berkongsi sindiket dengan {c_cnt} mangsa/kes!</i>")
            lines.append("")

    # Seksyen 4: Kod QR (DuitNow EMVCo vs Pautan URL / Quishing)
    if has_qr:
        if qr_data.get("qr_type") == "URL" or qr_data.get("url"):
            q_url = qr_data.get("url", "")
            q_dom = qr_url_res.get("domain", "") if qr_url_res else ""
            q_verdict = qr_url_res.get("verdict", "N/A") if qr_url_res else "N/A"
            q_sev = qr_url_res.get("severity", "INFO") if qr_url_res else "INFO"
            
            lines.append("📷 <b>KOD QR PAUTAN WEB (QUISHING / URL):</b>")
            lines.append(f"• <b>Pautan:</b> <code>{html.escape(q_url)}</code>")
            if q_dom:
                lines.append(f"• <b>Domain:</b> <code>{html.escape(q_dom)}</code>")
            
            badge_icon = "🚨" if q_sev in ["CRITICAL", "HIGH"] else ("⚠️" if q_sev == "MEDIUM" else "✅")
            lines.append(f"• <b>Status Pautan:</b> {badge_icon} <b>{html.escape(q_verdict)}</b>")
            
            if qr_url_res and qr_url_res.get("reasons"):
                for rsn in qr_url_res["reasons"]:
                    lines.append(f"  └ <i>{html.escape(rsn)}</i>")
                    
            if qr_url_res and qr_url_res.get("safety_note"):
                lines.append("")
                lines.append(qr_url_res["safety_note"])
            lines.append("")
        else:
            lines.append("📷 <b>KOD QR DUITNOW (EMVCo BINARY):</b>")
            qr_holder = qr_data.get("name") or "Tidak Dinyatakan"
            qr_bank_d = qr_data.get("bank") or "N/A"
            qr_acc_d = qr_data.get("account_number") or qr_data.get("proxy_phone") or "N/A"
            lines.append(f"• <b>Nama Pemegang:</b> {html.escape(qr_holder)}")
            if qr_bank_d != "N/A":
                lines.append(f"• <b>Institusi / Bank:</b> {html.escape(qr_bank_d)}")
            if qr_acc_d != "N/A":
                lines.append(f"• <b>Akaun Tersembunyi:</b> <code>{html.escape(qr_acc_d)}</code>")
            if qr_danger:
                lines.append("• <b>Status Pautan:</b> 🚫 <b>QUISHING PHISHING / MALWARE DETECTED!</b>")
            else:
                lines.append("• <b>Status Matriks:</b> ✅ EMVCo Standard Kebangsaan PayNet Sah")
            lines.append("")

    # Seksyen 5: Carian Media Sosial & Forum Tempatan (Google CSE)
    lines.append("🌐 <b>HASIL SEMAKAN MEDIA SOSIAL & FORUM (Google CSE):</b>")
    if combined_cse_snippets:
        for cs in combined_cse_snippets[:3]:
            s_source = html.escape(cs.get("source", "FORUM"))
            s_title = html.escape(cs.get("title", "Aduan Komuniti"))
            s_snip = html.escape(cs.get("snippet", ""))
            s_link = cs.get("link", "")
            lines.append(f"• <b>[{s_source}]</b> {s_title}")
            if s_snip:
                lines.append(f"  <i>{s_snip}</i>")
            if s_link and s_link.startswith(("http://", "https://")):
                lines.append(f'  🔗 <a href="{html.escape(s_link)}">Buka Thread Perbincangan</a>')
    else:
        lines.append("• <i>Tiada sebarang aduan pertikaian dikesan di Lowyat, CariGold, atau FB.</i>")

    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
    if is_threat:
        lines.append("🚫 <b>AMARAN:</b> Sasaran mempunyai rekod penipuan yang disahkan. <b>JANGAN PINDAHKAN SEBARANG WANG!</b>")
    else:
        lines.append("ℹ️ <i>Setakat ini tiada rekod penipuan rasmi dikesan. Sentiasa berwaspada ketika membuat sebarang transaksi kewangan.</i>")

    # Bina Butang 1-Ketikan (1-Click Action Buttons)
    share_target = (all_phones[0] if all_phones else (all_accounts[0] if all_accounts else "sasaran"))
    share_text = urllib.parse.quote(
        f"⚠️ Semakan KANTOI: Sila berhati-hati dengan nombor/akaun {share_target}. Semak status scammer secara pantas di Telegram: @crowdcallerbot"
    )
    wa_share_url = f"https://api.whatsapp.com/send?text={share_text}"

    inline_kb = []
    # 1. Butang Lapor / Tindakan
    if tmp_id:
        if is_admin:
            inline_kb.append([
                {"text": "⚡ Luluskan & Siar ke Saluran", "callback_data": f"cfrm_direct_{tmp_id}"},
                {"text": "❌ Batal", "callback_data": f"cncl_{tmp_id}"}
            ])
        else:
            inline_kb.append([
                {"text": "🚀 Sahkan & Hantar Laporan ke Admin", "callback_data": f"cfrm_{tmp_id}"},
                {"text": "📝 Tambah Cerita Kes", "callback_data": f"repqr_{tmp_id}"}
            ])
            inline_kb.append([
                {"text": "🏛️ Salur Tindakan ke Agensi Penguatkuasa", "callback_data": f"enf_{tmp_id}"}
            ])

    # 2. Butang Kongsi Amaran 1-Ketikan
    inline_kb.append([
        {"text": "📤 Kongsi Amaran ke WhatsApp", "url": wa_share_url},
        {"text": "ℹ️ Menu Utama", "callback_data": "help_menu"}
    ])

    return "\n".join(lines), {"inline_keyboard": inline_kb}, is_threat

# ==============================================================================
# MODUL PUSAT TINDAKAN AGENSI PENGUATKUASAAN MALAYSIA (NSRC, SKMM, PDRM, CYBER999, BNM)
# ==============================================================================
def build_enforcement_email_draft(rep_data):
    phones_str = ", ".join(rep_data.get("all_phones") or [rep_data.get("phone_norm") or "Tidak Dinyatakan"])
    suspect = rep_data.get("suspect_name") or "Tidak Dinyatakan"
    bank = rep_data.get("bank_account") or "Tidak Dinyatakan"
    loss = rep_data.get("victim_loss") or "Tidak Dinyatakan"
    ev = rep_data.get("evidence_text") or "Bukti perbualan dan transaksi dilampirkan."

    draft = (
        "Kepada: Pegawai Bertugas SKMM, Cyber999 & BNM TELELINK\n\n"
        "PER: ADUAN RASMI KES PENIPUAN SIBER (MODUS OPERANDI SCAMMER)\n\n"
        "Tuan/Puan,\n\n"
        "Saya ingin membuat aduan rasmi mengenai aktiviti penipuan jenayah siber yang telah berlaku ke atas saya. Berikut adalah butiran lengkap kes untuk siasatan dan tindakan pihak tuan:\n\n"
        f"1. BUTIRAN SUSPEK / SCAMMER:\n"
        f"   • Nombor Telefon: {phones_str}\n"
        f"   • Nama / Profil Suspek: {suspect}\n"
        f"   • Saluran Digunakan: WhatsApp / Media Sosial\n\n"
        f"2. BUTIRAN AKAUN BANK KELDAI (TRANSAKSI):\n"
        f"   • Maklumat Akaun: {bank}\n"
        f"   • Jumlah Kerugian Terlibat: {loss}\n\n"
        f"3. KRONOLOGI & RINGKASAN KEJADIAN:\n"
        f"   {ev[:600]}\n\n"
        "4. TINDAKAN DIMOHON:\n"
        "   • SKMM: Menyekat nombor telefon suspek dan pautan/akaun yang digunakan.\n"
        "   • Cyber999: Merekodkan insiden dan menyiasat sebarang laman phishing/malware.\n"
        "   • BNM: Menyenaraihitam dan membekukan akaun keldai terbabit.\n\n"
        "Bukti tangkapan layar perbualan, kod QR DuitNow, dan resit pemindahan telah disimpan dan sedia dilampirkan.\n\n"
        "Sekian, terima kasih.\n\n"
        "Pengadu,\n"
        "[Nama Penuh Anda]\n"
        "[No. Kad Pengenalan / IC]\n"
        "[No. Telefon Anda]"
    )
    return draft

def build_police_report_draft(rep_data):
    phones_str = ", ".join(rep_data.get("all_phones") or [rep_data.get("phone_norm") or "Tidak Dinyatakan"])
    suspect = rep_data.get("suspect_name") or "Tidak Dinyatakan"
    bank = rep_data.get("bank_account") or "Tidak Dinyatakan"
    loss = rep_data.get("victim_loss") or "RM..."
    ev = rep_data.get("evidence_text") or "Butiran kejadian dilampirkan."

    draft = (
        "REPOT POLIS: MAKLUMAT PERTAMA (SEKSYEN 107 KANUN TATACARA JENAYAH)\n\n"
        "Pada [Tarikh Kejadian], lebih kurang jam [Masa], semasa saya berada di [Lokasi Anda], saya telah berurusan dengan satu individu tidak dikenali melalui aplikasi WhatsApp/media sosial.\n\n"
        f"Individu tersebut menggunakan nombor telefon {phones_str} dan memperkenalkan diri sebagai '{suspect}'.\n\n"
        f"Suspek telah memperdayakan saya berkaitan tawaran/urusan yang dijalankan, dan mengarahkan saya membuat bayaran ke akaun bank {bank}.\n\n"
        f"Saya telah membuat pemindahan wang berjumlah {loss} melalui transaksi perbankan dalam talian/DuitNow ke akaun tersebut.\n\n"
        f"Ringkasan kejadian: {ev[:600]}\n\n"
        "Sejurus bayaran dibuat, suspek telah menyekat nombor telefon saya dan gagal dihubungi sehingga kini. Saya berasa tertipu dan mengalami kerugian wang ringgit.\n\n"
        "Tujuan saya membuat laporan polis ini adalah untuk rujukan pihak polis menjalankan siasatan di bawah Seksyen 420 Kanun Keseksaan serta untuk tindakan pihak Bank dan pihak berwajib membekukan akaun keldai tersebut."
    )
    return draft

def build_enforcement_menu(rep_id_or_tmp_id, rep_data):
    safe_phone = html.escape(str(rep_data.get("phone_norm") or "N/A"))
    safe_suspect = html.escape(str(rep_data.get("suspect_name") or "N/A"))
    safe_bank = html.escape(str(rep_data.get("bank_account") or "N/A"))
    safe_loss = html.escape(str(rep_data.get("victim_loss") or "Tidak dinyatakan"))



    text = (
        "🏛️ <b>PUSAT TINDAKAN AGENSI PENGUATKUASAAN MALAYSIA</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"📱 <b>Nombor Suspek:</b> <code>{safe_phone}</code>\n"
        f"👤 <b>Nama / Profil:</b> {safe_suspect}\n"
        f"🏦 <b>Akaun Bank:</b> {safe_bank}\n"
        f"💰 <b>Kerugian Dilaporkan:</b> <code>{safe_loss}</code>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
        "🚨 <b>1. TINDAKAN KECEMASAN (KURANG 24 JAM):</b>\n"
        "Jika anda baru sahaja memindahkan wang dalam tempoh 24 jam lalu, SEGERA hubungi:\n"
        "📞 <b>Talian Kecemasan NSRC: 997</b> <i>(Waktu operasi: 8:00 AM – 8:00 PM)</i>\n"
        "📞 <b>CCID Scam Response Centre: 03-2610 1559 / 03-2610 1599</b>\n"
        "<i>Pihak NSRC & pegawai CCID bersama bank akan memintas & membekukan akaun keldai sebelum wang dikeluarkan scammer!</i>\n\n"
        "📧 <b>2. ADUAN RASMI SKMM & CYBER999:</b>\n"
        "Salurkan laporan untuk menyekat nombor telefon, pautan phishing, dan akaun WhatsApp scammer.\n\n"
        "👮 <b>3. LAPORAN BALAI POLIS PDRM (SEKSYEN 107 CPC):</b>\n"
        "Wajib untuk pembukaan Kertas Siasatan Jenayah (Seksyen 420 Kanun Keseksaan) & tuntutan bayaran balik bank.\n\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "<i>Pilih saluran tindakan yang anda perlukan di bawah:</i>"
    )

    markup = {
        "inline_keyboard": [
            [
                {"text": "📧 Deraf Emel Rasmi (SKMM/Cyber999)", "callback_data": f"enf_em_{rep_id_or_tmp_id}"},
                {"text": "👮 Deraf Laporan Polis (Balai)", "callback_data": f"enf_pol_{rep_id_or_tmp_id}"}
            ],
            [
                {"text": "🌐 Semak Portal SemakMule PDRM", "url": "https://semakmule.rmp.gov.my"},
                {"text": "🌐 Portal e-Aduan SKMM", "url": "https://aduan.mcmc.gov.my"}
            ],
            [
                {"text": "📢 Facebook Cyber Crime Alert PDRM", "url": "https://www.facebook.com/CyberCrimeAlertRMP"},
                {"text": "« Menu Utama", "callback_data": "help_menu"}
            ]
        ]
    }
    return text, markup

def build_enforcement_email_view(rep_id_or_tmp_id, rep_data):
    draft_raw = build_enforcement_email_draft(rep_data)
    safe_draft = html.escape(draft_raw)
    safe_phone = html.escape(str(rep_data.get("phone_norm") or "N/A"))
    safe_bank = html.escape(str(rep_data.get("bank_account") or "N/A"))

    text = (
        "📧 <b>DERAF EMEL ADUAN RASMI KE SKMM, CYBER999 & BNM</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "Salin teks deraf aduan di bawah dan hantarkan ke emel rasmi agensi penguatkuasaan:\n\n"
        "📮 <b>Penerima Emel:</b>\n"
        "<code>aduanskmm@mcmc.gov.my, cyber999@cybersecurity.my, bnmtelelink@bnm.gov.my</code>\n\n"
        f"📌 <b>Subjek Emel:</b>\n"
        f"<code>[ADUAN RASMI PENIPUAN SIBER] - Kes No: {safe_phone} / Akaun: {safe_bank}</code>\n\n"
        "📋 <b>Teks Aduan Lengkap (Ketik Kotak untuk Salin):</b>\n"
        f"<pre><code>{safe_draft}</code></pre>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "💡 <i>Tip: Lampirkan bersama fail tangkapan layar (screenshot chat & resit) di dalam emel anda sebagai bukti sokongan.</i>"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "🌐 Buka Portal e-Aduan SKMM", "url": "https://aduan.mcmc.gov.my"}],
            [{"text": "« Kembali ke Pusat Agensi", "callback_data": f"enf_{rep_id_or_tmp_id}"}]
        ]
    }
    return text, markup

def build_enforcement_police_view(rep_id_or_tmp_id, rep_data):
    draft_raw = build_police_report_draft(rep_data)
    safe_draft = html.escape(draft_raw)

    text = (
        "👮 <b>DERAF FORMAT LAPORAN POLIS (BALAI POLIS PDRM)</b>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "Gunakan format teks ini sebagai rujukan kenyataan pengadu semasa hadir di kaunter Balai Polis atau serahkan terus kepada Pegawai Penyiasat (IO):\n\n"
        "📋 <b>Kenyataan Laporan Seksyen 107 CPC (Ketik Kotak untuk Salin):</b>\n"
        f"<pre><code>{safe_draft}</code></pre>\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "💡 <b>Dokumen Penting yang Wajib Dibawa ke Balai Polis:</b>\n"
        "1. Salinan Kad Pengenalan (MyKad) anda.\n"
        "2. Salinan Penyata Bank rasmi atau Resit Transaksi Pemindahan Wang.\n"
        "3. Salinan Cetakan (Printout) Tangkap Layar Perbualan WhatsApp / Media Sosial.\n"
        "4. No rujukan aduan NSRC (jika anda telah hubungi 997 sebelum ini).\n"
        "5. Hubungi <b>CCID Scam Response Centre</b> di <code>03-2610 1559</code> / <code>03-2610 1599</code> jika perlukan panduan lanjut pegawai penyiasat Bukit Aman."
    )
    markup = {
        "inline_keyboard": [
            [
                {"text": "🌐 Semak Portal SemakMule PDRM", "url": "https://semakmule.rmp.gov.my"},
                {"text": "📢 Facebook Cyber Crime Alert PDRM", "url": "https://www.facebook.com/CyberCrimeAlertRMP"}
            ],
            [{"text": "« Kembali ke Pusat Agensi", "callback_data": f"enf_{rep_id_or_tmp_id}"}]
        ]
    }
    return text, markup

def format_threat_response(record, query_input):
    phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones = record
    lines = [
        "==================================================",
        "🚨 *AMARAN: REKOD SCAMMER DITEMUI DALAM DATABASE!*",
        "==================================================",
        f"🚨 *NOMBOR TELEFON* : `{phone_norm}`",
        f"👤 *NAMA SUSPEK*    : {suspect_name or 'Tidak Dinyatakan'}",
        f"⚠️ *KLASIFIKASI*    : {risk_tag or 'AMARAN_KESELAMATAN'}"
    ]
    if bank_accounts and bank_accounts.strip() and bank_accounts != "N/A":
        lines.append(f"🏦 *AKAUN BANK*     : {bank_accounts}")
    if address and address.strip() and address != "N/A":
        lines.append(f"📍 *ALAMAT*         : {address}")
    if source and source.strip():
        lines.append(f"📚 *SUMBER SITASI*  : {source}")
    if evidence_links and evidence_links.strip() and evidence_links != "N/A":
        clean_links = " | ".join(evidence_links.split(" | ")[:3])
        lines.append(f"🔗 *PAUTAN BUKTI*   : {clean_links}")

    # Semakan Live PDRM CCID SemakMule (Idea 4)
    pdrm_info = query_pdrm_semakmule_live("telefon", phone_norm)
    if not pdrm_info.get("has_record") and bank_accounts and bank_accounts != "N/A":
        accs = re.findall(r'\b\d{8,18}\b', bank_accounts)
        if accs:
            pdrm_info = query_pdrm_semakmule_live("bank", accs[0])
            
    lines.append("--------------------------------------------------")
    if pdrm_info.get("success"):
        if pdrm_info.get("police_reports", 0) > 0:
            lines.append(f"🏛️ *PDRM SEMAKMULE CCID:* 🚨 *{pdrm_info['police_reports']} Laporan Polis Rasmi!* (Dicari {pdrm_info['search_count']} kali)")
        else:
            lines.append(f"🏛️ *PDRM SEMAKMULE CCID:* _Tiada laporan polis rasmi (Dicari {pdrm_info['search_count']} kali di portal CCID)_")

    # Mod Pengecam Sindiket (Idea 1)
    cluster = find_syndicate_cluster(DB_PATH, query_input, phone_norm, suspect_name, bank_accounts)
    if cluster.get("is_syndicate"):
        lines.append(format_syndicate_badge(cluster))

    lines.append("--------------------------------------------------")
    cse_snippets = fetch_google_cse_snippets(phone_norm or query_input)
    if cse_snippets:
        lines.append("🌐 *HASIL SEMAKAN MEDIA SOSIAL & FORUM (Google CSE):*")
        for cs in cse_snippets:
            lines.append(f"• *[{cs['source']}]* {cs['title']}")
            if cs.get('snippet'):
                lines.append(f"  _{cs['snippet']}_")
    else:
        lines.append("🌐 *Semakan Media Sosial & Forum (Google CSE):*")
        lines.append("• _Ketik butang di bawah untuk carian lanjut di Lowyat, CariGold, FB & TikTok._")
    lines.append("--------------------------------------------------")
    lines.append("ℹ️ _Disemak daripada KANTOI Threat Engine (4,700+ Rekod)._")
    lines.append("📲 _Gunakan aplikasi KANTOI untuk perlindungan panggilan & SMS masa nyata._")
    return "\n".join(lines)

def format_safe_response(query_input, norm_phone):
    display_num = norm_phone if norm_phone else query_input
    
    # Semakan Live PDRM CCID SemakMule walaupun tiada dalam DB KANTOI (Idea 4)
    is_bank = not norm_phone and len(re.sub(r'[^\d]', '', query_input)) >= 8
    cat = "bank" if is_bank else "telefon"
    pdrm_info = query_pdrm_semakmule_live(cat, display_num)
    
    if pdrm_info.get("has_record") and pdrm_info.get("police_reports", 0) > 0:
        lines = [
            "==================================================",
            "🚨 *AMARAN KRITIKAL: REKOD PDRM CCID DITEMUI!*",
            "==================================================",
            f"Nombor Disemak: `{display_num}`",
            f"🏛️ *Laporan Polis Rasmi (CCID):* 🚨 *{pdrm_info['police_reports']} Laporan Didaftarkan!*",
            f"🔍 *Rekod Carian Portal PDRM:* Dicari sebanyak *{pdrm_info['search_count']}* kali.",
            "",
            "⚠️ *PERHATIAN:*",
            "Walaupun nombor ini belum dimasukkan ke pangkalan data komuniti KANTOI, *PDRM SemakMule mengesahkan terdapat aduan jenayah komersil terhadap akaun/nombor ini!*",
            "🚫 *JANGAN PINDAHKAN WANG ATAU BERURUSAN DENGAN PIHAK INI!*",
            "--------------------------------------------------",
            "💡 _Anda boleh menaip laporan mengenai nombor ini untuk dikemaskini ke aplikasi KANTOI._"
        ]
        return "\n".join(lines)

    lines = [
        "==================================================",
        "✅ *TIADA REKOD SCAMMER DITEMUI*",
        "==================================================",
        f"Nombor Disemak: `{display_num}`",
        "Status: *BERSIH* (Tiada rekod penipuan dalam pangkalan data KANTOI)",
        ""
    ]
    if pdrm_info.get("success"):
        lines.append(f"🏛️ *PDRM SemakMule CCID:* 0 Laporan Polis (Dicari {pdrm_info['search_count']} kali)")
        lines.append("")

    lines.extend([
        "⚠️ *PERINGATAN PENTING:*",
        "• Walaupun tiada rekod, sentiasa berwaspada!",
        "• Jangan sesekali berkongsi kata laluan, TAC/OTP, atau memindahkan wang ke akaun peribadi yang tidak dikenali.",
        "• Anda boleh melaporkan nombor ini dengan menaip cerita/bukti kejadian jika mengesyaki penipuan.",
        "--------------------------------------------------"
    ])
    cse_snippets = fetch_google_cse_snippets(display_num)
    if cse_snippets:
        lines.append("🌐 *HASIL SEMAKAN MEDIA SOSIAL & FORUM (Google CSE):*")
        for cs in cse_snippets:
            lines.append(f"• *[{cs['source']}]* {cs['title']}")
            if cs.get('snippet'):
                lines.append(f"  _{cs['snippet']}_")
    else:
        lines.append("🌐 *SEMAKAN MEDIA SOSIAL & FORUM (Google CSE):*")
        lines.append("• _Tiada rekod aduan dikesan di Lowyat, CariGold atau Facebook._")
        lines.append("• _Ketik butang Google CSE di bawah untuk carian lanjut._")
    lines.append("--------------------------------------------------")
    lines.append("ℹ️ _Pangkalan Data KANTOI merangkumi rekod PDRM SemakMule CCID & Komuniti (6,100+ Rekod)._")
    return "\n".join(lines)

def format_welcome_message(is_admin=False, user_id=None):
    admin_tag = "\n⭐ *Status Anda:* Pentadbir (Admin KANTOI)" if is_admin else ""
    
    # Status Kunci AI Pengguna (BYOK vs Percubaan Pelayan)
    user_k = get_user_gemini_key(user_id) if user_id else None
    if user_k:
        key_badge = "🟢 *Status AI Vision Anda:* Kunci Peribadi Aktif (VIP / Kuota Penuh)"
        key_note = "⚡ _Anda menikmati analisis AI Vision pantas tanpa sebarang kekangan kuota._"
    else:
        key_badge = "🟡 *Status AI Vision Anda:* Mod Percubaan Komuniti (Kuota Pelayan KANTOI)"
        key_note = (
            "👉 _Untuk kelajuan maksimum & tiada had perkongsian, daftarkan Kunci API Google percuma anda sendiri dengan arahan: `/mykey`._"
        )

    return (
        "🤖 *SELAMAT DATANG KE BOT KANTOI ANTI-SCAM!* 🇲🇾\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "*Pertahanan Siber Komuniti Malaysia Menentang Sindiket Scammer & Keldai Akaun.*\n\n"
        "👋 *Hai! Jika anda pertama kali di sini, bot ini adalah pelindung siber peribadi anda. Anda boleh terus buat semakan pantas:*\n\n"
        "⚡ *3 CARA PANTAS MENGGUNAKAN BOT INI:*\n"
        "1️⃣ *Taip Terus Nombor / Akaun Bank:*\n"
        "   Tampal sebarang nombor telefon (cth: `0179597645`) atau akaun bank keldai (cth: `112028138548`). Bot akan semak serentak dalam pangkalan KANTOI & portal rasmi PDRM SemakMule!\n\n"
        "2️⃣ *Hantar Gambar / Screenshot (Multi-Bukti):*\n"
        "   Hantar tangkapan layar WhatsApp, resit transaksi, kod QR DuitNow, atau profil suspek. Hantar 1 hingga 5 keping sekaligus — AI Vision KANTOI akan menyatukan kesemuanya menjadi satu laporan kes lengkap!\n\n"
        "3️⃣ *Imbas Pautan Phishing & Fail APK:*\n"
        "   Hantar link web mencurigakan (`/url <pautan>`) atau fail dokumen `.apk` untuk dianalisis keselamatan trojan pencuri TAC dalam Sandbox.\n\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "🔑 *PENTING: PENGALAMAN PENGGUNA TERBAIK (BYOK)*\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"{key_badge}\n"
        f"{key_note}\n\n"
        "🎁 *Mod Percubaan Percuma (Sedia Ada):*\n"
        "Pihak KANTOI menyediakan kunci pelayan percuma secara terus (hardcoded) supaya anda boleh mencuba dan menguji bot ini serta-merta tanpa sebarang bayaran atau pendaftaran awal!\n\n"
        "🚀 *Ingin Kelajuan Maksimum & Pengalaman VIP?*\n"
        "Kuota percubaan pelayan dikongsi oleh ribuan pengguna awam. Untuk menikmati *respon AI Vision sepantas kilat*, kuota peribadi tanpa had, dan mengelakkan sebarang kesesakan kuota, *anda sangat digalakkan menggunakan Kunci API Google Gemini anda sendiri (100% Percuma!)*:\n\n"
        "👉 *Daftar kunci peribadi anda (1 minit sahaja):*\n"
        "1. Layari: https://aistudio.google.com/app/apikey (log masuk Google & klik _Create API Key_)\n"
        "2. Taip arahan berikut ke dalam bot ini:\n"
        "   `/mykey <KUNCI_API_ANDA>`\n"
        f"{admin_tag}\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "ℹ️ _Ketik mana-mana butang menu di bawah untuk panduan terperinci setiap fungsi:_"
    )

# ==============================================================================
# MENU BANTUAN & PANDUAN INTERAKTIF
# ==============================================================================
MAIN_REPLY_KEYBOARD = {
    "keyboard": [
        [{"text": "🔍 Semak No / Akaun"}, {"text": "🏛️ Semakan PDRM"}],
        [{"text": "📚 30 FAQ Mangsa Scam"}, {"text": "📢 Lapor Scammer"}],
        [{"text": "🌐 Carian Media Sosial"}, {"text": "📸 Imbas Foto / QR"}],
        [{"text": "🔑 Kunci API (BYOK)"}, {"text": "📰 Buletin Berita Scam"}],
        [{"text": "ℹ️ Menu Bantuan & Panduan"}]
    ],
    "resize_keyboard": True,
    "is_persistent": True
}

def get_help_menu_keyboard():
    return {
        "inline_keyboard": [
            [
                {"text": "📚 30 FAQ WAJIB MANGSA SCAM (PANDUAN HAK & TINDAKAN)", "callback_data": "faq_hub"}
            ],
            [
                {"text": "🔑 Kunci API Sendiri (BYOK)", "callback_data": "help_mykey"},
                {"text": "🔍 Semakan No / Bank", "callback_data": "help_check"}
            ],
            [
                {"text": "🏛️ Semakan PDRM CCID", "callback_data": "help_pdrm"},
                {"text": "📸 Imbas Foto & QR", "callback_data": "help_vision"}
            ],
            [
                {"text": "🌐 Carian Medsos (Google CSE)", "callback_data": "help_cse"},
                {"text": "🔗 Semak Pautan (URL)", "callback_data": "help_url"}
            ],
            [
                {"text": "📦 Sandbox APK Malware", "callback_data": "help_apk"},
                {"text": "📢 Cara Lapor Scammer", "callback_data": "help_report"}
            ],
            [
                {"text": "📰 Buletin Berita Scam", "callback_data": "help_bulletin"},
                {"text": "🛡️ Penjaga Group", "callback_data": "help_group"}
            ]
        ]
    }

def get_help_detail(topic):
    back_button = {
        "inline_keyboard": [
            [{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]
        ]
    }
    
    if topic == "help_mykey":
        text = (
            "🔑 *PANDUAN KUNCI API PERIBADI (BYOK - BRING YOUR OWN KEY)*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "*Mengapa Anda Perlu Menggunakan Kunci API Sendiri?*\n\n"
            "⚡ *1. Kelajuan Sepantas Kilat & Kuota VIP:*\n"
            "Google AI Studio memberikan Kunci API Gemini *100% PERCUMA seumur hidup* dengan had ribuan semakan sehari bagi setiap akaun. Menggunakan kunci sendiri memastikan anda sentiasa mendapat analisis AI Vision segera tanpa berkongsi had kuota dengan orang ramai.\n\n"
            "🎁 *2. Mod Percubaan Percuma Sedia Ada:*\n"
            "Pihak KANTOI tetap menyediakan kunci pelayan percuma secara terus (hardcoded) supaya pengguna baharu boleh mencuba bot bila-bila masa tanpa sebarang pendaftaran awal. Anda tidak diwajibkan mendaftar kunci jika hanya ingin mencuba beberapa kali.\n\n"
            "🔒 *3. Privasi Dijamin:*\n"
            "Kunci API anda disimpan secara disulitkan dan hanya digunakan khusus untuk menjawab pertanyaan akaun Telegram anda sahaja.\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "📋 *LANGKAH DAPATKAN KUNCI PERCUMA (1 MINIT):*\n"
            "1️⃣ Buka pelayar web telefon/komputer anda dan pergi ke:\n"
            "   https://aistudio.google.com/app/apikey\n"
            "2️⃣ Log masuk dengan Akaun Google (Gmail) anda.\n"
            "3️⃣ Klik butang biru *'Create API key'*.\n"
            "4️⃣ Salin kunci yang dihasilkan (bermula `AIzaSy...` atau `AQ...`).\n"
            "5️⃣ Kembali ke chat ini dan hantar:\n"
            "   `/mykey <KUNCI_API_ANDA>`\n"
            "   _(Contoh: `/mykey AIzaSyC...`)_\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "ℹ️ *Arahan Tambahan:*\n"
            "• Taip `/mykey` untuk menyemak status kunci semasa anda.\n"
            "• Taip `/mykey padam` jika ingin memadam kunci dan kembali ke kuota awam."
        )
        mykey_kb = {
            "inline_keyboard": [
                [{"text": "🌐 Dapatkan Kunci Percuma (Google AI Studio)", "url": "https://aistudio.google.com/app/apikey"}],
                [{"text": "\xab Kembali ke Menu Utama", "callback_data": "help_menu"}]
            ]
        }
        return text, mykey_kb

    elif topic == "help_check":
        text = (
            "🔍 *PANDUAN SEMAKAN NO TELEFON & AKAUN BANK*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Anda boleh menyemak sebarang nombor telefon atau akaun bank yang meragukan.\n\n"
            "💡 *Cara Penggunaan:*\n"
            "Cukup sekadar taip nombor telefon atau nombor akaun bank terus ke dalam chat bot ini!\n\n"
            "📝 *Contoh Carian:*\n"
            "• `0179597645`\n"
            "• `+601139444255`\n"
            "• `112028138548` (Akaun Maybank)\n"
            "• `7056210969` (Akaun CIMB)\n\n"
            "🛡️ *Enjin KANTOI akan automatik:*\n"
            "1. Menyemak pangkalan data komuniti KANTOI (4,700+ rekod).\n"
            "2. Mengesan rangkaian sindiket (Cluster Matching).\n"
            "3. Menyemak portal rasmi PDRM SemakMule CCID secara langsung!"
        )
        return text, back_button

    elif topic == "help_pdrm":
        text = (
            "🏛️ *PANDUAN SEMAKAN SIAP-SEDIA PDRM CCID*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Bot KANTOI dihubungkan terus dengan pangkalan data Jabatan Siasatan Jenayah Komersil (JSJK) Polis Diraja Malaysia.\n\n"
            "💡 *Cara Penggunaan:*\n"
            "Gunakan arahan `/pdrm` atau `/semakmule` diikuti nombor sasaran.\n\n"
            "📝 *Contoh Arahan:*\n"
            "• `/pdrm 01121938112`\n"
            "• `/pdrm 512802774281`\n"
            "• `/semakmule 162900219348`\n\n"
            "📊 *Maklumat yang Diberikan:*\n"
            "• Jumlah Laporan Polis Rasmi yang telah difailkan mangsa.\n"
            "• Jumlah carian awam yang pernah dibuat di portal PDRM."
        )
        return text, back_button

    elif topic == "help_cse":
        text = (
            "🌐 *PANDUAN CARIAN PERTIKAIAN MEDIA SOSIAL (GOOGLE CSE)*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Ciri berkuasa yang diadaptasi terus daripada enjin carian khas aplikasi KANTOI!\n\n"
            "Enjin Carian Khusus KANTOI (Penyaring Komuniti & Forum Tempatan) telah diprogramkan khusus untuk menyaring rekod penipuan dan pertikaian transaksi di platform utama Malaysia:\n\n"
            "• 🔴 *Forum Lowyat* (Aduan Kopitiam & Trade Zone Dispute)\n"
            "• 🟡 *Forum CariGold* (Pendedahan Scammer & Skim Cepat Kaya)\n"
            "• 🔵 *Facebook & Kumpulan Komuniti Anti-Scam*\n"
            "• ⚫ *Twitter / X & Thread Pendedahan Scammer*\n"
            "• 📱 *TikTok & Laporan Transaksi Bank Meragukan*\n"
            "• 📝 *Blog-blog & Portal Jenayah Siber Tempatan*\n\n"
            "💡 *Cara Penggunaan:*\n"
            "Gunakan arahan `/cse` atau `/social` diikuti nombor atau nama sasaran:\n"
            "• `/cse 0179597645`\n"
            "• `/cse 112028138548`\n"
            "• `/social scammer pakej umrah`\n\n"
            "Setiap hasil carian biasa atau imbasan QR juga akan automatik disertakan butang terus ke Google CSE!"
        )
        cse_kb = {
            "inline_keyboard": [
                [{"text": "🌐 Uji Carian Google CSE", "callback_data": "cselive_0179597645"}],
                [{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]
            ]
        }
        return text, cse_kb

    elif topic == "help_vision":
        text = (
            "📸 *PANDUAN IMBASAN FOTO & MULTI-BUKTI KANTOI*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Malas menaip? Anda boleh hantar gambar terus kepada bot ini!\n\n"
            "🧠 *Sokongan Berbilang Tangkap Layar (Multi-Screenshot Kes):*\n"
            "Anda boleh memilih dan menghantar *1 hingga 5 keping gambar sekali gus* (sebagai album atau berturutan):\n"
            "• Tangkap layar perbualan WhatsApp / Telegram\n"
            "• Nota kronologi aduan & kerugian wang (dispute)\n"
            "• Resit transaksi perbankan / DuitNow\n"
            "• Kod QR DuitNow pembayaran\n\n"
            "Enjin AI Vision KANTOI akan menghubungkaitkan kesemua gambar tersebut dan merumuskannya dalam *1 Dossier Kes Bersepadu*!\n\n"
            "🔍 *Kod QR DuitNow (Binary EMVCo Decoder):*\n"
            "Bot membaca matrik QR binary secara matematik (0.001 saat) untuk membongkar Nama Pemegang Akaun, Bank, dan No Akaun sebenar di sebalik QR tersebut!\n\n"
            "🔑 *Gunakan Kuota AI Sendiri (BYOK):*\n"
            "Dapatkan kunci percuma di https://aistudio.google.com/app/apikey dan taip `/mykey <API_KEY>` untuk kelajuan maksimum tanpa had!"
        )
        return text, back_button

    elif topic == "help_url":
        text = (
            "🔗 *PANDUAN SEMAKAN PAUTAN PHISHING (URL)*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Menerima SMS atau mesej yang menyuruh anda menekan pautan bank atau bantuan kerajaan? Hantar ke sini sebelum klik!\n\n"
            "💡 *Cara Penggunaan:*\n"
            "Taip `/url <pautan>` ATAU hantar saja pautan penuh ke dalam chat.\n\n"
            "📝 *Contoh Arahan:*\n"
            "• `/url https://maybank2u-login.top/my`\n"
            "• `/url http://bantuan-str-fasa1.vip`\n\n"
            "🛡️ *Apa Yang Dikesan:*\n"
            "• Portal perbankan palsu (Maybank, CIMB, RHB, TNG, dll).\n"
            "• Pancingan data bantuan kerajaan (STR, MyKasih, PADU).\n"
            "• Domain risiko tinggi (`.top`, `.xyz`, `.vip`, `.icu`).\n"
            "• Pautan pendek (`bit.ly`, `tinyurl`) yang cuba menyembunyikan alamat sebenar."
        )
        return text, back_button

    elif topic == "help_apk":
        text = (
            "📦 *PANDUAN SANDBOX MALWARE APK*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Scammer sering menghantar fail `.apk` (cth: 'Bantuan_STR.apk', 'Wedding_Card.apk', 'Cleaning_Service.apk') untuk mencuri wang perbankan anda.\n\n"
            "💡 *Cara Penggunaan:*\n"
            "Hantarkan fail dokumen `.apk` tersebut terus ke dalam chat bot ini!\n\n"
            "🛡️ *Enjin KANTOI Sandbox akan:*\n"
            "• Menganalisis kebenaran berisiko (Pencuri SMS TAC/OTP, Accessibility Keylogger, Overlay Login Palsu).\n"
            "• Mengesan pautan pelayan Bot C2 scammer.\n"
            "• Memberikan gred keselamatan (SELAMAT / RISIKO / BAHAYA KRITIKAL) tanpa perlu memasang fail tersebut pada telefon anda!"
        )
        return text, back_button

    elif topic == "help_report":
        text = (
            "📢 *PANDUAN CARA MELAPORKAN SCAMMER*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Setiap laporan anda membantu melindungi jutaan rakyat Malaysia daripada ditipu.\n\n"
            "💡 *Pilihan 1: 'Main Tulis Saja' (Paling Mudah!)*\n"
            "Taip cerita anda dalam bahasa harian biasa mengandungi nombor scammer & akaun bank. Bot akan menyusun draf secara automatik!\n"
            "Contoh: _'01139444255 scammer menyamar pegawai polis bukit aman suruh transfer rm500 ke cimb 7056210969'_\n\n"
            "💡 *Pilihan 2: Borang Berstruktur*\n"
            "Taip `/lapor` untuk mengisi borang 4-langkah secara teratur.\n\n"
            "💡 *Pilihan 3: Laporan Pukal (Banyak Nombor)*\n"
            "Tampal senarai nombor sekali gus untuk pengesahan pukal!"
        )
        return text, back_button

    elif topic == "help_bulletin":
        text = (
            "📰 *PANDUAN BULETIN BERITA SCAM HARIAN*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Ketahui modus operandi dan kes tangkapan penipuan terkini di Malaysia.\n\n"
            "💡 *Cara Penggunaan:*\n"
            "• Taip `/buletin` atau `/berita` untuk membaca 5 berita terkini daripada sumber berita utama (Astro Awani, Berita Harian, Harian Metro, TV3, Bernama).\n"
            "• Bot juga menyiarkan buletin automatik setiap pagi di saluran rasmi @kantoiradar."
        )
        bulletin_kb = {
            "inline_keyboard": [
                [{"text": "📰 Baca Buletin Sekarang", "callback_data": "read_bulletin_now"}],
                [{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]
            ]
        }
        return text, bulletin_kb

    elif topic == "help_group":
        text = (
            "🛡️ *PANDUAN PENJAGA KUMPULAN (GROUP SENTINEL)*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Lindungi group Telegram komuniti, keluarga, atau persatuan anda daripada spambot & scammer!\n\n"
            "💡 *Cara Memasang:*\n"
            "1. Masukkan `@crowdcallerbot` ke dalam Telegram Group anda.\n"
            "2. Lantik bot sebagai *Admin* dengan kebenaran 'Delete Messages'.\n\n"
            "🛡️ *Tindakan Automatik Bot:*\n"
            "• Memadam secara senyap sebarang mesej yang mengandungi nombor scammer atau akaun keldai.\n"
            "• Memadam pautan phishing dan fail APK trojan yang dihantar ahli.\n"
            "• Mengeluarkan kad amaran merah untuk mengingatkan semua ahli kumpulan!"
        )
        return text, back_button

    return format_welcome_message(), get_help_menu_keyboard()

def register_bot_commands():
    """Mendaftar senarai perintah rasmi pada butang Menu Telegram"""
    commands = [
        {"command": "start", "description": "Mula & buka Menu Utama"},
        {"command": "faq", "description": "30 Soalan Lazim & Panduan Hak Mangsa Scam"},
        {"command": "help", "description": "Pusat Bantuan & Panduan Interaktif"},
        {"command": "mykey", "description": "Daftar / Semak kunci AI Gemini (BYOK)"},
        {"command": "agensi", "description": "Salur aduan rasmi NSRC 997, SKMM, PDRM"},
        {"command": "semak", "description": "Semak no telefon atau akaun bank"},
        {"command": "pdrm", "description": "Semakan terus portal rasmi PDRM CCID"},
        {"command": "cse", "description": "Carian media sosial & forum (Google CSE)"},
        {"command": "url", "description": "Semak pautan phishing & bank palsu"},
        {"command": "buletin", "description": "Berita kes penipuan terkini di Malaysia"},
        {"command": "lapor", "description": "Borang aduan scammer langkah demi langkah"},
        {"command": "panduan", "description": "Panduan langkah demi langkah"},
        {"command": "batal", "description": "Batal proses semasa"}
    ]
    try:
        res = api_call("setMyCommands", {"commands": commands})
        if res.get("ok"):
            print("[BOT] Perintah bot rasmi (setMyCommands) berjaya didaftarkan ke Telegram.")
    except Exception as e:
        print(f"[BOT] Gagal daftar setMyCommands: {e}")

def api_call(method, payload=None, files=None):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/{method}"
    try:
        if files:
            resp = requests.post(url, data=payload, files=files, timeout=30)
        else:
            resp = requests.post(url, json=payload, timeout=25)
        res = resp.json()
        if not res.get("ok"):
            print(f"[API FAIL] {method}: {res.get('description')}")
        return res
    except Exception as e:
        print(f"[API ERR] {method}: {e}")
        return {"ok": False, "description": str(e)}

def send_message(chat_id, text, reply_markup=None, parse_mode="Markdown", disable_web_page_preview=True):
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": parse_mode,
        "disable_web_page_preview": disable_web_page_preview
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    res = api_call("sendMessage", payload)
    if not res.get("ok") and "parse" in str(res.get("description", "")).lower():
        clean_text = re.sub(r'<[^>]+>', '', text).replace('*', '').replace('_', '').replace('`', '')
        plain_payload = {
            "chat_id": chat_id,
            "text": clean_text,
            "disable_web_page_preview": disable_web_page_preview
        }
        if reply_markup:
            plain_payload["reply_markup"] = reply_markup
        return api_call("sendMessage", plain_payload)
    return res

def edit_message(chat_id, message_id, text, reply_markup=None, parse_mode="Markdown", disable_web_page_preview=True):
    payload = {
        "chat_id": chat_id,
        "message_id": message_id,
        "text": text,
        "parse_mode": parse_mode,
        "disable_web_page_preview": disable_web_page_preview
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    res = api_call("editMessageText", payload)
    if not res.get("ok") and "parse" in str(res.get("description", "")).lower():
        clean_text = re.sub(r'<[^>]+>', '', text).replace('*', '').replace('_', '').replace('`', '')
        plain_payload = {
            "chat_id": chat_id,
            "message_id": message_id,
            "text": clean_text,
            "disable_web_page_preview": disable_web_page_preview
        }
        if reply_markup:
            plain_payload["reply_markup"] = reply_markup
        return api_call("editMessageText", plain_payload)
    return res

def answer_callback(callback_query_id, text, alert=False):
    return api_call("answerCallbackQuery", {
        "callback_query_id": callback_query_id,
        "text": text,
        "show_alert": alert
    })

def render_progress_bar(percent, stage_title, detail=""):
    """
    Menjana reka bentuk indikator kemajuan (HUD) canggih 0% -> 100% untuk Telegram.
    Menggunakan palet status beranimasi (Kuning -> Biru -> Ungu -> Hijau) dan bar kemajuan tepat.
    """
    total_blocks = 10
    filled = int(round((percent / 100.0) * total_blocks))
    filled = max(0, min(total_blocks, filled))
    bar = "█" * filled + "░" * (total_blocks - filled)
    
    if percent < 30:
        pulse = "🟡"
    elif percent < 75:
        pulse = "🔵"
    elif percent < 100:
        pulse = "🟣"
    else:
        pulse = "🟢"

    lines = [
        "🛡️ <b>KANTOI THREAT SENTINEL — ENJIN AKTIF</b>",
        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
        f"{pulse} <b>Kemajuan:</b> <code>[{bar}]</code> <b>{percent}%</b>",
        f"⚙️ <b>Fasa:</b> {stage_title}",
    ]
    if detail:
        lines.append(f"🔬 <b>Modul:</b> <code>{html.escape(detail)}</code>")
    lines.extend([
        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
        "⏳ <i>Pemprosesan multi-enjin aktif di sebalik tabir...</i>"
    ])
    return "\n".join(lines)


class ProgressHUD:
    """
    Pengurus Indikator Kemajuan Dinamik (0% -> 100%) untuk Telegram Bot.
    Memberi maklum balas masa-nyata kepada pengguna bahawa bot sedang aktif bekerja
    dan menghalang anggapan bahawa bot telah terhenti/hang.
    """
    def __init__(self, chat_id, reply_to_msg_id=None):
        self.chat_id = chat_id
        self.reply_to_msg_id = reply_to_msg_id
        self.msg_id = None
        self.last_update_time = 0
        self.last_percent = -1
        self.last_text = ""

    def start(self, initial_percent=10, stage="Memulakan analisis...", detail=""):
        text = render_progress_bar(initial_percent, stage, detail)
        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "HTML"
        }
        if self.reply_to_msg_id:
            payload["reply_to_message_id"] = self.reply_to_msg_id
        res = api_call("sendMessage", payload)
        if res.get("ok"):
            self.msg_id = res.get("result", {}).get("message_id")
            self.last_update_time = time.time()
            self.last_percent = initial_percent
            self.last_text = text
        return self.msg_id

    def update(self, percent, stage, detail=""):
        if not self.msg_id:
            return
        now = time.time()
        # Selang masa minimum 0.35s antara suntingan Telegram untuk visual lancar dan patuh rate-limit
        elapsed = now - self.last_update_time
        if elapsed < 0.35 and percent != 100:
            time.sleep(0.35 - elapsed)
        text = render_progress_bar(percent, stage, detail)
        if text == self.last_text:
            return
        res = edit_message(self.chat_id, self.msg_id, text, parse_mode="HTML")
        if res.get("ok"):
            self.last_update_time = time.time()
            self.last_percent = percent
            self.last_text = text

    def complete_with_message(self, final_text, reply_markup=None, parse_mode="HTML"):
        if self.msg_id:
            res = edit_message(self.chat_id, self.msg_id, final_text, reply_markup=reply_markup, parse_mode=parse_mode)
            if res.get("ok"):
                return res
        # Fallback sekiranya edit gagal
        return send_message(self.chat_id, final_text, reply_markup=reply_markup, parse_mode=parse_mode)

# ==============================================================================
# ALIRAN MOD A: FORWARD KE ADMIN & KELULUSAN
# ==============================================================================
import html

def forward_report_to_admins(rep_id, rep_data):
    admin_ids = get_admin_ids()
    if not admin_ids:
        print(f"[WARN] Tiada admin berdaftar untuk menerima laporan {rep_id}!")
        return

    safe_user = html.escape(str(rep_data.get('username') or 'N/A'))
    safe_uid = html.escape(str(rep_data.get('user_id') or 'N/A'))
    safe_phone = html.escape(str(rep_data.get('phone_norm') or 'N/A'))
    safe_suspect = html.escape(str(rep_data.get('suspect_name') or 'N/A'))
    safe_bank = html.escape(str(rep_data.get('bank_account') or 'Tiada'))
    safe_ev = html.escape(str(rep_data.get('evidence_text') or 'Tiada'))

    photo_ids = rep_data.get("photo_file_ids") or ([rep_data["photo_file_id"]] if rep_data.get("photo_file_id") else [])
    count_str = f" ({len(photo_ids)} Bukti Bergambar)" if len(photo_ids) > 1 else ""

    admin_card = (
        f"🚨 <b>LAPORAN SCAMMER BAHARU DITERIMA (ID: <code>{rep_id}</code>){count_str}</b>\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"👤 <b>Pengirim:</b> @{safe_user} (ID: <code>{safe_uid}</code>)\n\n"
        f"📱 <b>Nombor Scammer:</b> <code>{safe_phone}</code>\n"
        f"🏷️ <b>Taktik / Suspek:</b> {safe_suspect}\n"
        f"🏦 <b>Akaun Bank:</b> {safe_bank}\n"
        + (f"💰 <b>Kerugian Mangsa:</b> <code>{html.escape(str(rep_data.get('victim_loss')))}</code>\n" if rep_data.get('victim_loss') else "")
        + f"📝 <b>Keterangan Kes:</b> {safe_ev[:900]}\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"Sila semak maklumat di atas sebelum meluluskan siaran ke @kantoiradar:"
    )

    markup = {
        "inline_keyboard": [
            [
                {"text": "✅ LULUSKAN & SIAR", "callback_data": f"app_{rep_id}"},
                {"text": "❌ TOLAK", "callback_data": f"rej_{rep_id}"}
            ]
        ]
    }

    for adm_id in admin_ids:
        if len(photo_ids) > 1:
            # Hantar album gambar bukti
            media = []
            for idx, pid in enumerate(photo_ids[:10]):
                m_obj = {"type": "photo", "media": pid}
                if idx == 0:
                    m_obj["caption"] = admin_card[:1024]
                    m_obj["parse_mode"] = "HTML"
                media.append(m_obj)
            res = api_call("sendMediaGroup", {"chat_id": adm_id, "media": media})
            # Hantar kad kelulusan di bawah album
            api_call("sendMessage", {
                "chat_id": adm_id,
                "text": f"Pengesahan Laporan <code>{rep_id}</code> oleh @{safe_user}:",
                "parse_mode": "HTML",
                "reply_markup": markup
            })
        elif len(photo_ids) == 1:
            api_call("sendPhoto", {
                "chat_id": adm_id,
                "photo": photo_ids[0],
                "caption": admin_card,
                "parse_mode": "HTML",
                "reply_markup": markup
            })
        else:
            api_call("sendMessage", {
                "chat_id": adm_id,
                "text": admin_card,
                "parse_mode": "HTML",
                "reply_markup": markup,
                "disable_web_page_preview": True
            })

def format_channel_approved_post(rep, admin_username):
    """
    Membina teks laporan penuh, komprehensif dan terperinci untuk saluran rasmi @kantoiradar
    tanpa diringkaskan atau memotong bukti dan kronologi awal kes.
    """
    rep_id = rep.get("report_id") or "N/A"
    phone_norm = rep.get("phone_norm") or "N/A"
    all_phones = rep.get("all_phones") or []
    if phone_norm not in all_phones and phone_norm != "N/A":
        all_phones = [phone_norm] + [p for p in all_phones if p != phone_norm]
    
    suspect_name = rep.get("suspect_name") or "Tidak Dinyatakan"
    bank_account = rep.get("bank_account") or "Tiada"
    victim_loss = rep.get("victim_loss")
    evidence_text = rep.get("evidence_text") or ""
    reviewed_by = admin_username or rep.get("reviewed_by") or "Pentadbir KANTOI"
    qr_data = rep.get("qr_data")
    tactic = rep.get("tactic") or "Laporan Komuniti Disahkan"
    
    lines = [
        "🚨 <b>AMARAN RASMI KANTOI RADAR: LAPORAN SCAMMER DISAHKAN</b>",
        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
        f"🛡️ <b>STATUS:</b> 🚨 <b>SAH SEBAGAI ANCAMAN PENIPUAN (DILULUSKAN MOD)</b>",
        f"🆔 <b>Rujukan Kes:</b> <code>{html.escape(str(rep_id))}</code>",
        f"👤 <b>Disahkan Oleh:</b> @{html.escape(str(reviewed_by))} (Pentadbir Saluran)",
        ""
    ]
    
    # Seksyen 1: Profil & Taktik Suspek
    lines.append("👤 <b>PROFIL & TAKTIK SUSPEK:</b>")
    lines.append(f"• <b>Taktik / Kategori:</b> <i>{html.escape(str(tactic))}</i>")
    lines.append(f"• <b>Nama / Samaran:</b> {html.escape(str(suspect_name))}")
    if victim_loss:
        lines.append(f"• <b>Kerugian Dilaporkan:</b> <code>{html.escape(str(victim_loss))}</code>")
    if rep.get("email"):
        lines.append(f"• <b>Emel Suspek:</b> <code>{html.escape(str(rep['email']))}</code>")
    lines.append("")
    
    # Seksyen 2: Nombor Telefon Sasaran
    lines.append("📱 <b>NOMBOR TELEFON SASARAN:</b>")
    lines.append(f"• <b>Nombor Utama:</b> <code>{html.escape(str(phone_norm))}</code>")
    if len(all_phones) > 1:
        other_phones = [p for p in all_phones if p != phone_norm]
        if other_phones:
            lines.append(f"• <b>Nombor Rangkaian Terkait:</b> " + ", ".join(f"<code>{html.escape(str(p))}</code>" for p in other_phones))
    lines.append("")
    
    # Seksyen 3: Akaun Bank Sasaran
    if bank_account and bank_account != "Tiada" and bank_account != "N/A":
        lines.append("💳 <b>AKAUN BANK / DUITNOW SASARAN:</b>")
        lines.append(f"• <b>Butiran Akaun:</b> {html.escape(str(bank_account))}")
        lines.append("")
        
    # Seksyen 4: Maklumat Kod QR / Pautan Quishing (jika ada)
    if qr_data:
        if qr_data.get("url"):
            lines.append("📷 <b>KOD QR / PAUTAN WEB (QUISHING):</b>")
            lines.append(f"• <b>Pautan:</b> <code>{html.escape(str(qr_data['url']))}</code>")
            u_res = inspect_url_phishing(qr_data["url"])
            if u_res.get("domain"):
                lines.append(f"• <b>Domain:</b> <code>{html.escape(str(u_res['domain']))}</code>")
            lines.append(f"• <b>Keputusan:</b> <b>{html.escape(str(u_res.get('verdict', 'Mencurigakan')))}</b>")
            if u_res.get("safety_note"):
                lines.append("")
                lines.append(u_res["safety_note"])
            lines.append("")
        elif qr_data.get("account_number") or qr_data.get("proxy_phone"):
            lines.append("📷 <b>KOD QR DUITNOW (EMVCo BINARY):</b>")
            if qr_data.get("name"):
                lines.append(f"• <b>Nama Penerima:</b> {html.escape(str(qr_data['name']))}")
            if qr_data.get("bank"):
                lines.append(f"• <b>Bank / Institusi:</b> {html.escape(str(qr_data['bank']))}")
            lines.append("")

    # Seksyen 5: Kronologi & Keterangan Kes Lengkap (Tanpa Dipotong!)
    if evidence_text and evidence_text.strip():
        lines.append("📝 <b>KRONOLOGI & BUKTI KES LENGKAP:</b>")
        lines.append(html.escape(evidence_text.strip()))
        lines.append("")

    # Seksyen 6: Nasihat Keselamatan & Footer
    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
    lines.append("⚠️ <b>PERINGATAN AWAM:</b>")
    lines.append("JANGAN buat sebarang bayaran atau berikan maklumat peribadi (OTP / PIN). Maklumat ini telah dimasukkan ke pangkalan data KANTOI bagi menyekat panggilan & SMS secara automatik.")
    lines.append("")
    lines.append("📲 <i>Buka Tab Pertahanan Aplikasi KANTOI untuk perlindungan masa nyata.</i>")
    lines.append("💬 <i>Bantu komuniti: Hantar bukti scammer terus ke Bot Risikan: @crowdcallerbot</i>")
    lines.append("📢 <i>Saluran Rasmi: @kantoiradar | Kod Sumber: https://github.com/kand4/kantoi</i>\n")
    lines.append("#KANTOI_UPDATE #PDRM #SEMAKMULE #SCAMALERT")
    lines.append(f"{html.escape(str(phone_norm))} | {html.escape(str(suspect_name))} | LAPORAN_KOMUNITI_DISAHKAN | {html.escape(str(bank_account or 'Tiada'))}")
    
    return "\n".join(lines)

def handle_admin_decision(action, rep_id, admin_chat_id, admin_username, message_id):
    reports = get_pending_reports()
    rep = reports.get(rep_id)
    if not rep:
        return "Ralat: Rekod laporan tidak ditemui atau telah diproses."

    if rep.get("status") in ["APPROVED", "REJECTED"]:
        return f"Laporan ini sudah diproses sebelum ini ({rep.get('status')})."

    user_chat_id = rep.get("user_id")
    phone_norm = rep.get("phone_norm")
    suspect_name = rep.get("suspect_name")
    bank_account = rep.get("bank_account")
    evidence_text = rep.get("evidence_text")

    if action == "approve":
        rep["status"] = "APPROVED"
        rep["reviewed_by"] = admin_username
        rep["reviewed_at"] = int(time.time())
        save_pending_report(rep_id, rep)

        # 1. Pos ke saluran @kantoiradar dengan format terperinci dan lengkap (tidak diringkaskan)
        channel_post = format_channel_approved_post(rep, admin_username)
        photo_id = rep.get("photo_file_id")

        if photo_id:
            # Semak had panjang kapsyen foto Telegram (maksimum 1024 aksara)
            if len(channel_post) <= 1024:
                api_call("sendPhoto", {
                    "chat_id": CHANNEL_ID,
                    "photo": photo_id,
                    "caption": channel_post,
                    "parse_mode": "HTML"
                })
            else:
                # Jika laporan terperinci melebihi 1024 aksara:
                # Hantar foto dengan tajuk ringkas bukti, disusuli mesej teks laporan penuh tanpa potongan!
                short_caption = (
                    f"🚨 <b>AMARAN RASMI KANTOI RADAR (Ref: <code>{html.escape(str(rep_id))}</code>)</b>\n"
                    f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                    f"📱 <b>Sasaran:</b> <code>{html.escape(str(phone_norm))}</code>\n"
                    f"👤 <b>Modus:</b> {html.escape(str(suspect_name)[:80])}\n"
                    f"📸 <i>Bukti tangkapan layar dilampirkan. Sila rujuk laporan dan keterangan kes penuh di bawah:</i>"
                )
                api_call("sendPhoto", {
                    "chat_id": CHANNEL_ID,
                    "photo": photo_id,
                    "caption": short_caption,
                    "parse_mode": "HTML"
                })
                # Hantar teks penuh (sehingga 4096 aksara)
                if len(channel_post) <= 4096:
                    api_call("sendMessage", {
                        "chat_id": CHANNEL_ID,
                        "text": channel_post,
                        "parse_mode": "HTML",
                        "disable_web_page_preview": True
                    })
                else:
                    for chunk_idx in range(0, len(channel_post), 4000):
                        api_call("sendMessage", {
                            "chat_id": CHANNEL_ID,
                            "text": channel_post[chunk_idx:chunk_idx+4000],
                            "parse_mode": "HTML",
                            "disable_web_page_preview": True
                        })
        else:
            if len(channel_post) <= 4096:
                api_call("sendMessage", {
                    "chat_id": CHANNEL_ID,
                    "text": channel_post,
                    "parse_mode": "HTML",
                    "disable_web_page_preview": True
                })
            else:
                for chunk_idx in range(0, len(channel_post), 4000):
                    api_call("sendMessage", {
                        "chat_id": CHANNEL_ID,
                        "text": channel_post[chunk_idx:chunk_idx+4000],
                        "parse_mode": "HTML",
                        "disable_web_page_preview": True
                    })

        # 2. Masukkan ke dalam SQLite KANTOI (simpan semua nombor telefon kes)
        all_phones_list = rep.get("all_phones") or [phone_norm]
        all_phones_str = ", ".join(all_phones_list)
        insert_approved_threat(
            phone_norm=phone_norm,
            suspect_name=suspect_name,
            category="LAPORAN_KOMUNITI_DISAHKAN",
            bank_accounts=bank_account,
            source=f"Risikan Komuniti KANTOI (Ref: {rep_id})",
            all_phones=all_phones_str
        )

        # 3. Notifikasi kepada pengirim laporan
        if user_chat_id:
            user_msg = (
                f"🎉 <b>TERIMA KASIH! LAPORAN ANDA TELAH DILULUSKAN!</b>\n\n"
                f"Nombor: <code>{phone_norm}</code>\n"
                f"Status: <b>Telah disahkan oleh Admin KANTOI</b>\n\n"
                f"📢 Maklumat ini telah disiarkan ke saluran @kantoiradar dan kini dimasukkan ke dalam enjin risikan KANTOI untuk melindungi orang ramai."
            )
            api_call("sendMessage", {
                "chat_id": user_chat_id,
                "text": user_msg,
                "parse_mode": "HTML"
            })

        edit_message(admin_chat_id, message_id, f"✅ *DILULUSKAN & DISIARKAN KE @KANTOIRADAR*\nRef ID: `{rep_id}`\nOleh Admin: @{admin_username}")
        return "Berjaya! Laporan diluluskan, disimpan ke database dan disiarkan ke saluran."

    elif action == "reject":
        rep["status"] = "REJECTED"
        rep["reviewed_by"] = admin_username
        rep["reviewed_at"] = int(time.time())
        save_pending_report(rep_id, rep)

        if user_chat_id:
            user_msg = (
                f"⚠️ *STATUS LAPORAN SCAMMER (Ref: `{rep_id}`)*\n\n"
                f"Nombor: `{phone_norm}`\n"
                f"Status: *Tidak dapat diluluskan*\n\n"
                f"Laporan anda tidak dapat disahkan oleh Admin kerana maklumat atau bukti yang dikemukakan tidak mencukupi. Terima kasih atas keprihatinan anda."
            )
            send_message(user_chat_id, user_msg)

        edit_message(admin_chat_id, message_id, f"❌ *LAPORAN DITOLAK*\nRef ID: `{rep_id}`\nOleh Admin: @{admin_username}")
        return "Laporan telah ditolak."

def forward_batch_report_to_admins(batch_id, batch_data):
    admin_ids = get_admin_ids()
    if not admin_ids:
        print(f"[WARN] Tiada admin berdaftar untuk menerima batch {batch_id}!")
        return

    safe_user = html.escape(str(batch_data.get('username') or 'N/A'))
    items = batch_data.get("items", [])
    total_nums = sum(len(it['phones']) for it in items)

    summary_lines = []
    for idx, it in enumerate(items, 1):
        p_str = ", ".join(f"<code>{p}</code>" for p in it['phones'])
        summary_lines.append(f"{idx}. {p_str} — {html.escape(it['desc'])}")

    admin_card = (
        f"🚨 <b>SENARAI BATCH SCAMMER BAHARU DITERIMA (ID: <code>{batch_id}</code>)</b>\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"👤 <b>Pengirim:</b> @{safe_user}\n"
        f"📊 <b>Jumlah:</b> {len(items)} entri ({total_nums} nombor telefon)\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        + "\n".join(summary_lines[:10]) +
        ("\n<i>...dan seterusnya</i>" if len(summary_lines) > 10 else "") +
        "\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "Sila semak maklumat di atas sebelum meluluskan kemasukan pukal ke pangkalan data KANTOI:"
    )

    markup = {
        "inline_keyboard": [
            [
                {"text": f"✅ LULUSKAN SEMUA ({total_nums} NOMBOR)", "callback_data": f"appb_{batch_id}"},
                {"text": "❌ TOLAK", "callback_data": f"rejb_{batch_id}"}
            ]
        ]
    }

    photo_id = batch_data.get("photo_file_id")
    for adm_id in admin_ids:
        if photo_id:
            api_call("sendPhoto", {
                "chat_id": adm_id,
                "photo": photo_id,
                "caption": admin_card,
                "parse_mode": "HTML",
                "reply_markup": markup
            })
        else:
            api_call("sendMessage", {
                "chat_id": adm_id,
                "text": admin_card,
                "parse_mode": "HTML",
                "reply_markup": markup,
                "disable_web_page_preview": True
            })

def handle_admin_batch_decision(action, batch_id, admin_chat_id, admin_username, message_id):
    reports = get_pending_reports()
    batch = reports.get(batch_id)
    if not batch:
        return "Ralat: Rekod batch tidak ditemui atau telah diproses."

    if batch.get("status") in ["APPROVED", "REJECTED"]:
        return f"Batch ini sudah diproses sebelum ini ({batch.get('status')})."

    user_chat_id = batch.get("user_id")
    items = batch.get("items", [])

    if action == "approve":
        batch["status"] = "APPROVED"
        batch["reviewed_by"] = admin_username
        batch["reviewed_at"] = int(time.time())
        save_pending_report(batch_id, batch)

        inserted_count = 0
        channel_threat_tags = []

        for it in items:
            desc = it.get("desc", "Laporan Komuniti")
            for p in it.get("phones", []):
                insert_approved_threat(
                    phone_norm=p,
                    suspect_name=desc,
                    category="LAPORAN_KOMUNITI_DISAHKAN",
                    bank_accounts="Tiada",
                    source=f"Batch Komuniti (Ref: {batch_id})"
                )
                inserted_count += 1
                channel_threat_tags.append(f"{p} | {desc} | LAPORAN_KOMUNITI_DISAHKAN | Tiada")

        # Pos ke saluran @kantoiradar
        post_text = (
            f"🚨 <b>KEMASKINI PUKAL SCAMMER BAHARU ({inserted_count} NOMBOR DILULUSKAN)</b>\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"Laporan pukal komuniti disahkan oleh Admin @{admin_username} (Ref: <code>{batch_id}</code>):\n\n"
        )
        for idx, it in enumerate(items, 1):
            p_str = ", ".join(f"<code>{p}</code>" for p in it['phones'])
            post_text += f"{idx}. {p_str} — {html.escape(it['desc'])}\n"

        post_text += (
            f"\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"📲 <i>Semua {inserted_count} nombor kini aktif dilindungi dalam KANTOI.</i>\n"
            f"💬 <i>Bantu komuniti: Lapor sebarang nombor/akaun bank scammer (teks/gambar) ke @crowdcallerbot.</i>\n"
            f"🔗 <i>Semak kod sumber projek di: https://github.com/kand4/kantoi</i>\n\n"
            f"#KANTOI_UPDATE\n"
            + "\n".join(channel_threat_tags)
        )

        api_call("sendMessage", {
            "chat_id": CHANNEL_ID,
            "text": post_text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True
        })

        if user_chat_id:
            api_call("sendMessage", {
                "chat_id": user_chat_id,
                "text": f"🎉 <b>TAHNIAH! SEMUA {inserted_count} NOMBOR SCAMMER TELAH DILULUSKAN!</b>\n\nMaklumat telah dimasukkan ke pangkalan data KANTOI dan disiarkan ke saluran @kantoiradar.",
                "parse_mode": "HTML"
            })

        edit_message(admin_chat_id, message_id, f"✅ *SEMUA {inserted_count} NOMBOR DILULUSKAN & DISIARKAN*\nRef ID: `{batch_id}`\nOleh Admin: @{admin_username}")
        return f"Berjaya meluluskan {inserted_count} nombor!"

    elif action == "reject":
        batch["status"] = "REJECTED"
        batch["reviewed_by"] = admin_username
        batch["reviewed_at"] = int(time.time())
        save_pending_report(batch_id, batch)

        if user_chat_id:
            send_message(user_chat_id, f"⚠️ *Status Batch (Ref: `{batch_id}`):* Laporan batch anda tidak dapat diluluskan oleh Admin.")

        edit_message(admin_chat_id, message_id, f"❌ *BATCH DITOLAK*\nRef ID: `{batch_id}`\nOleh Admin: @{admin_username}")
        return "Batch telah ditolak."

# ==============================================================================
# PENGURUS PENIMBAL KES MULTI-IMEJ (MULTI-SCREENSHOT DEBOUNCE AGGREGATOR)
# ==============================================================================
import threading

PHOTO_CASE_BUFFERS = {}  # chat_id -> list of dicts
PHOTO_CASE_TIMERS = {}   # chat_id -> threading.Timer
PHOTO_CASE_LOCK = threading.Lock()

def queue_image_for_case_aggregation(chat_id, username, file_id, caption, msg_id=None, is_doc=False, doc_filename=""):
    """
    Memasukkan imej ke dalam penimbal debounce (2.0 saat).
    Jika pengguna menghantar beberapa screenshot serentak (album atau berturutan),
    semua gambar dikumpulkan ke dalam satu batch kes dan dianalisis bersama!
    """
    with PHOTO_CASE_LOCK:
        if chat_id not in PHOTO_CASE_BUFFERS:
            PHOTO_CASE_BUFFERS[chat_id] = []
        PHOTO_CASE_BUFFERS[chat_id].append({
            "file_id": file_id,
            "caption": caption or "",
            "msg_id": msg_id,
            "is_doc": is_doc,
            "doc_filename": doc_filename,
            "time": time.time()
        })
        
        # Batalkan timer lama jika ada
        old_timer = PHOTO_CASE_TIMERS.get(chat_id)
        if old_timer:
            try:
                old_timer.cancel()
            except Exception:
                pass
                
        # Mulakan timer debounce baharu (2.0s)
        new_timer = threading.Timer(2.0, execute_multi_photo_case, args=[chat_id, username])
        PHOTO_CASE_TIMERS[chat_id] = new_timer
        new_timer.daemon = True
        new_timer.start()

    # Beri maklum balas pantas 'typing' kepada pengguna di Telegram
    api_call("sendChatAction", {"chat_id": chat_id, "action": "typing"})

def execute_multi_photo_case(chat_id, username):
    with PHOTO_CASE_LOCK:
        items = PHOTO_CASE_BUFFERS.pop(chat_id, [])
        PHOTO_CASE_TIMERS.pop(chat_id, None)

    if not items:
        return

    first_msg_id = items[0].get("msg_id")

    # 1. Semak jika pengguna dalam wizard /lapor bertingkat langkah 4 (STEP_EVIDENCE)
    session = user_sessions.get(chat_id)
    if session and session.get("step") == "STEP_EVIDENCE":
        all_captions = [it["caption"] for it in items if it.get("caption") and it["caption"].lower() != "tiada"]
        evidence_text = " | ".join(all_captions) if all_captions else f"Bukti bergambar ({len(items)} tangkapan layar) dilampirkan."
        rep_id = f"REP-{int(time.time()) % 100000:05d}"
        rep_data = {
            "report_id": rep_id,
            "user_id": chat_id,
            "username": username,
            "phone_norm": session["data"]["phone_norm"],
            "suspect_name": session["data"]["suspect_name"],
            "bank_account": session["data"]["bank_account"],
            "evidence_text": evidence_text,
            "photo_file_id": items[0]["file_id"],
            "photo_file_ids": [it["file_id"] for it in items],
            "status": "PENDING",
            "created_at": int(time.time())
        }
        save_pending_report(rep_id, rep_data)
        del user_sessions[chat_id]
        summary = (
            f"📋 *RINGKASAN LAPORAN ANDA (Ref: `{rep_id}`)*\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"📱 Nombor: `{rep_data['phone_norm']}`\n"
            f"👤 Suspek: {rep_data['suspect_name']}\n"
            f"🏦 Akaun Bank: {rep_data['bank_account']}\n"
            f"📸 Gambar Bukti: {len(items)} tangkapan layar dilampirkan\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
            f"✅ *Laporan anda telah berjaya dihantar ke Bilik Admin KANTOI!*\n"
            f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @kantoiradar. Terima kasih!"
        )
        send_message(chat_id, summary)
        forward_report_to_admins(rep_id, rep_data)
        return

    # 2. Semak jika pengguna melampirkan foto pada draf yang sedia ada
    found_key = None
    for k, v in reversed(list(unconfirmed_reports.items())):
        if v.get("user_id") == chat_id and not v.get("photo_file_id"):
            found_key = k
            break
    if not found_key:
        for k, v in reversed(list(get_pending_reports().items())):
            if v.get("user_id") == chat_id and not v.get("photo_file_id") and v.get("status") in ["DRAFT", "PENDING"]:
                found_key = k
                break
    if found_key:
        p_ids = [it["file_id"] for it in items]
        if found_key in unconfirmed_reports:
            unconfirmed_reports[found_key]["photo_file_id"] = p_ids[0]
            unconfirmed_reports[found_key]["photo_file_ids"] = p_ids
        reports = get_pending_reports()
        if found_key in reports:
            reports[found_key]["photo_file_id"] = p_ids[0]
            reports[found_key]["photo_file_ids"] = p_ids
            save_json_file(REPORTS_DATA_PATH, reports)
        send_message(chat_id, f"📸 *{len(items)} keping gambar bukti berjaya dilampirkan pada draf laporan anda!*")
        return

    # 3. ENJIN MULTI-IMEJ: Analisis Kes Bersepadu
    hud = ProgressHUD(chat_id, reply_to_msg_id=first_msg_id)
    if len(items) > 1:
        hud.start(10, f"📥 Menerima {len(items)} tangkapan layar kes scammer...", f"{len(items)} Bukti Kes")
    else:
        hud.start(10, "📥 Memuat turun imej dari pelayan Telegram...", "Telegram Bot CDN")

    temp_paths = []
    try:
        for idx, it in enumerate(items):
            p = download_telegram_photo(it["file_id"])
            if p:
                temp_paths.append(p)

        if not temp_paths:
            hud.complete_with_message("❌ Gagal memuat turun gambar dari pelayan Telegram. Sila cuba lagi.")
            return

        captions = [it["caption"] for it in items if it.get("caption")]

        # Semak jika imej tunggal mengandungi pautan URL terus (Anti-Quishing)
        if len(temp_paths) == 1:
            single_qr = decode_duitnow_qr(temp_paths[0])
            if single_qr and single_qr.get("url"):
                res_url = inspect_url_phishing(single_qr["url"])
                is_danger = res_url["severity"] in ["CRITICAL", "HIGH"]
                is_safe_tng = res_url.get("is_genuine_tng", False)
                badge = "🚨" if is_danger else ("✅" if is_safe_tng else ("⚠️" if res_url["severity"] == "MEDIUM" else "ℹ️"))
                
                if is_safe_tng:
                    title_text = "PERISAI KANTOI: PAUTAN TOUCH 'N GO EWALLET SAH DISAHKAN"
                elif is_danger:
                    title_text = "PERISAI KANTOI: KOD QR PHISHING (QUISHING) DIKESAN!"
                else:
                    title_text = "HASIL IMBASAN KOD QR: PAUTAN (URL)"

                lines = [
                    f"{badge} <b>{title_text}</b>",
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                    f"🔗 <b>Pautan Terkandung:</b> <code>{html.escape(res_url['url'])}</code>",
                    f"🌐 <b>Domain Pendaratan:</b> <code>{html.escape(res_url['domain'])}</code>",
                    f"🛡️ <b>Keputusan:</b> <b>{html.escape(res_url['verdict'])}</b>",
                    ""
                ]
                if res_url.get("reasons"):
                    lines.append("⚠️ <b>Faktor Penilaian Risiko:</b>")
                    for rsn in res_url["reasons"]:
                        lines.append(f"   • {html.escape(rsn)}")
                    lines.append("")

                if res_url.get("safety_note"):
                    lines.append(res_url["safety_note"])
                    lines.append("")

                lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                if is_danger:
                    lines.append("🚫 <b>AMARAN:</b> Ini adalah perangkap pancingan data (Quishing) untuk mencuri kata laluan atau TAC/PIN akaun anda. Jangan buka pautan ini!")
                elif not is_safe_tng:
                    lines.append("ℹ️ <i>Pautan ini tidak menunjukkan sebarang ciri phishing atau penyamaran bank rasmi yang diketahui.</i>")

                # Butang tindakan: Jika pautan bahaya atau mencurigakan, sediakan draf laporan untuk dihantar ke Admin
                inline_kb = []
                if is_danger:
                    tmp_id = f"tmp_{int(time.time()*1000) % 1000000}"
                    ev_text = (
                        f"[Kod QR Quishing Dikesan]\n"
                        f"Pautan: {res_url['url']}\n"
                        f"Domain: {res_url['domain']}\n"
                        f"Keputusan: {res_url['verdict']}\n"
                        f"Faktor Risiko:\n" + "\n".join(f"- {r}" for r in res_url['reasons'])
                    )
                    rep_data = {
                        "user_id": chat_id,
                        "username": username,
                        "phone_norm": single_qr.get("proxy_phone") or res_url["domain"],
                        "all_phones": [single_qr["proxy_phone"]] if single_qr.get("proxy_phone") else [],
                        "suspect_name": res_url.get("target_brand") or "Quishing Phishing",
                        "bank_account": f"Pautan Phishing ({res_url['domain']})",
                        "all_accounts": [],
                        "evidence_text": ev_text,
                        "victim_loss": None,
                        "photo_file_id": items[0]["file_id"] if items else None,
                        "photo_file_ids": [items[0]["file_id"]] if items else [],
                        "qr_data": single_qr,
                        "tactic": f"Quishing / Phishing ({res_url.get('target_brand', 'Pautan Palsu')})",
                        "case_summary": f"Kod QR Quishing dikesan: {res_url['url']}",
                        "status": "PENDING"
                    }
                    unconfirmed_reports[tmp_id] = rep_data
                    save_pending_report(tmp_id, rep_data)

                    is_admin_sender = chat_id in get_admin_ids()
                    if is_admin_sender:
                        inline_kb.append([
                            {"text": "⚡ Luluskan & Siar ke Saluran", "callback_data": f"cfrm_direct_{tmp_id}"},
                            {"text": "❌ Batal", "callback_data": f"cncl_{tmp_id}"}
                        ])
                    else:
                        inline_kb.append([
                            {"text": "🚀 Sahkan & Hantar Laporan ke Admin", "callback_data": f"cfrm_{tmp_id}"},
                            {"text": "📝 Tambah Cerita Kes", "callback_data": f"repqr_{tmp_id}"}
                        ])
                        inline_kb.append([
                            {"text": "🏛️ Salur Tindakan ke Agensi Penguatkuasa", "callback_data": f"enf_{tmp_id}"}
                        ])

                share_target = res_url["domain"]
                share_text = urllib.parse.quote(
                    f"⚠️ Semakan Keselamatan KANTOI: Pautan {share_target} dikesan. Semak status keselamatan pautan/scammer di Telegram: @crowdcallerbot"
                )
                wa_share_url = f"https://api.whatsapp.com/send?text={share_text}"
                inline_kb.append([
                    {"text": "📤 Kongsi Amaran ke WhatsApp", "url": wa_share_url},
                    {"text": "ℹ️ Menu Utama", "callback_data": "help_menu"}
                ])

                hud.update(100, "✨ Analisis Pautan Selesai!", "Anti-Quishing")
                hud.complete_with_message("\n".join(lines), reply_markup={"inline_keyboard": inline_kb}, parse_mode="HTML")
                return

        # Jalankan sintesis kes multi-imej
        intel = extract_multi_image_case_intel(
            temp_paths, 
            captions=captions, 
            user_id=chat_id, 
            progress_callback=lambda pct, title, sub: hud.update(pct, title, sub)
        )

        if intel.get("has_intel"):
            hud.update(75, "🏛️ Menyelaras risikan PDRM CCID & Pangkalan KANTOI...", "Korelasi Multi-Entiti")
            cand_phones = list(intel.get("all_phones") or [])
            cand_accounts = list(intel.get("all_accounts") or [])
            target_name = intel.get("suspect_name", "Tidak Dinyatakan")
            target_bank = intel.get("bank_name", "N/A")
            primary_target = cand_phones[0] if cand_phones else (cand_accounts[0] if cand_accounts else "N/A")

            # Draf Laporan Sementara
            tmp_id = f"tmp_{int(time.time()*1000) % 1000000}"
            combined_evidence = intel.get("evidence_text", "")
            if intel.get("case_summary"):
                combined_evidence = f"[Ringkasan Kes]: {intel['case_summary']}\n\n{combined_evidence}"
            if captions:
                combined_evidence = f"[Kapsyen Pengguna]: {' | '.join(captions)}\n\n{combined_evidence}"

            p_ids = [it["file_id"] for it in items]
            rep_data = {
                "user_id": chat_id,
                "username": username,
                "phone_norm": primary_target,
                "all_phones": cand_phones,
                "suspect_name": f"{intel.get('tactic', 'Aduan')} ({target_name})" if target_name != "Tidak Dinyatakan" else intel.get('tactic', 'Aduan'),
                "bank_account": intel.get("bank_accounts") or (cand_accounts[0] if cand_accounts else "N/A"),
                "all_accounts": cand_accounts,
                "evidence_text": combined_evidence,
                "victim_loss": intel.get("victim_loss"),
                "photo_file_id": p_ids[0] if p_ids else None,
                "photo_file_ids": p_ids,
                "qr_data": intel.get("qr_data"),
                "email": (intel.get("emails") or [""])[0] if intel.get("emails") else "",
                "tactic": intel.get("tactic", "Semakan Keselamatan Siber"),
                "case_summary": intel.get("case_summary", ""),
                "status": "PENDING"
            }
            unconfirmed_reports[tmp_id] = rep_data
            save_pending_report(tmp_id, rep_data)

            is_admin_sender = chat_id in get_admin_ids()

            hud.update(90, "🌐 Mengimbas forum Lowyat, CariGold & FB (Google CSE)...", "Enjin Komuniti KANTOI")

            dossier_html, dossier_kb, is_threat = build_unified_cyber_dossier(
                phones=cand_phones,
                accounts=cand_accounts,
                qr_data=intel.get("qr_data"),
                suspect_name=target_name,
                bank_name=target_bank,
                email=(intel.get("emails") or [""])[0] if intel.get("emails") else "",
                tactic=intel.get("tactic", "Semakan Keselamatan Siber"),
                source_badge=intel.get("source_badge", "🧠 AI Vision (Multi-Bukti)"),
                tmp_id=tmp_id,
                is_admin=is_admin_sender,
                evidence_summary=intel.get("case_summary"),
                image_count=len(temp_paths),
                victim_loss=intel.get("victim_loss")
            )

            hud.update(100, "✨ Analisis Selesai! Menyusun Dossier Bersepadu...", "Dossier 1-Paparan Sedia")
            hud.complete_with_message(dossier_html, reply_markup=dossier_kb, parse_mode="HTML")
        else:
            # Tiada entiti dikesan
            active_k, src_label, k_type = get_effective_gemini_key(chat_id)
            g_status = intel.get("gemini_status", "")
            g_err = intel.get("gemini_error", "")

            if not active_k:
                ai_diag = (
                    "⚠️ <b>AI Vision Belum Ditetapkan:</b>\n"
                    "Kunci Gemini API belum didaftarkan untuk akaun anda atau pelayan.\n"
                    "👉 <i>Daftar kunci percuma anda sendiri (BYOK) dengan arahan: <code>/mykey &lt;API_KEY&gt;</code>!</i>"
                )
            elif g_err:
                ai_diag = (
                    f"⚠️ <b>Status AI Gemini ({html.escape(g_status)}):</b>\n"
                    f"<code>{html.escape(g_err[:180])}</code>\n"
                    f"👉 <i>Kunci digunakan: {html.escape(src_label)}. Semak semula dengan <code>/mykey</code> atau <code>/gemini</code>.</i>"
                )
            else:
                ai_diag = f"🔍 <b>Pengecaman Teks:</b> Tiada nombor telefon (+60 / 01x), akaun bank, atau nama suspek yang jelas ditemui dalam {len(temp_paths)} gambar tersebut."

            fb_lines = [
                f"📸 <b>{len(temp_paths)} Gambar Diterima, Namun Tiada Maklumat Ancaman Berjaya Dibaca.</b>",
                "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                ai_diag,
                "",
                "💡 <b>Tip Semakan Pantas KANTOI:</b>",
                "• <b>Tangkapan Layar WhatsApp / Telegram:</b> Pastikan teks atau nombor telefon suspek tidak terlalu beresolusi rendah.",
                "• <b>Semakan Manual Terus:</b> Anda boleh terus taip atau tampal (paste) nombor telefon atau akaun suspek ke dalam chat ini untuk semakan serta-merta tanpa perlu hantar gambar!",
                "• <b>Kod QR:</b> Jika kod QR, pastikan petak tidak kabur dan bucu QR tidak terpotong."
            ]
            hud.update(100, "✨ Analisis Selesai! Menyusun laporan semakan...", "Tip & Panduan")
            hud.complete_with_message("\n".join(fb_lines), parse_mode="HTML")
    finally:
        for tp in temp_paths:
            if os.path.exists(tp):
                try:
                    os.unlink(tp)
                except Exception:
                    pass

# ==============================================================================
# GELUNG UTAMA BOT (LONG POLLING)
# ==============================================================================
def run_bot():
    print("[BOT] Memulakan CrowdCaller & KANTOI Bot (Smart NLP Free-Form + Mod A)...")
    register_bot_commands()
    print(f"[BOT] Pangkalan data: {DB_PATH}")
    print(f"[BOT] Saluran siaran: {CHANNEL_ID}")
    
    admins = get_admin_ids()
    print(f"[BOT] Admin berdaftar semasa ({len(admins)}): {list(admins)}")
    
    last_update_id = 0
    last_radar_date = ""
    processed_msg_ids = set()
    get_updates_url = f"https://api.telegram.org/bot{BOT_TOKEN}/getUpdates"

    if not BOT_TOKEN or BOT_TOKEN == "YOUR_TELEGRAM_BOT_TOKEN_HERE":
        print("[RALAT KRITIKAL] TELEGRAM_BOT_TOKEN tidak sah atau tiada dalam local_secrets.py!")
        return

    print("[BOT] Bot sedia menerima pertanyaan & laporan bebas (Main Tulis Saja)!")

    while True:
        try:
            params = {"offset": last_update_id + 1, "timeout": 25}
            resp = requests.get(get_updates_url, params=params, timeout=30)
            data = resp.json()

            if not data.get("ok"):
                print(f"[RALAT TELEGRAM] getUpdates gagal: {data.get('description')} (Kod: {data.get('error_code')})")
                time.sleep(3)
                continue

            # Auto-Scheduler: Buletin Radar Ancaman Harian (Idea 6)
            today_iso = datetime.date.today().isoformat()
            if datetime.datetime.now().hour >= 9 and last_radar_date != today_iso:
                last_radar_date = today_iso
                try:
                    bulletin_text = format_scam_radar_bulletin(5)
                    api_call("sendMessage", {
                        "chat_id": CHANNEL_ID,
                        "text": bulletin_text,
                        "parse_mode": "Markdown",
                        "disable_web_page_preview": True
                    })
                    print(f"[RADAR] Buletin harian disiarkan secara automatik ke {CHANNEL_ID} pada {today_iso}")
                except Exception as e:
                    print(f"[RADAR AUTO ERR] {e}")

            for update in data.get("result", []):
                last_update_id = update["update_id"]

                # ------------------------------------------------------------------
                # A. KENDALIKAN CALLBACK QUERY
                # ------------------------------------------------------------------
                callback = update.get("callback_query")
                if callback:
                    cb_id = callback["id"]
                    cb_data = callback.get("data", "")
                    cb_user = callback["from"]
                    cb_chat_id = callback["message"]["chat"]["id"]
                    cb_msg_id = callback["message"]["message_id"]
                    username = cb_user.get("username") or str(cb_user.get("id"))

                    # 1a. Pengesahan Terus oleh Admin (Satu Klik Luluskan)
                    if cb_data.startswith("cfrm_direct_"):
                        admin_ids = get_admin_ids()
                        if cb_chat_id not in admin_ids:
                            answer_callback(cb_id, "Akses Ditolak: Anda bukan admin.", alert=True)
                            continue
                        tmp_id = cb_data.replace("cfrm_direct_", "")
                        rep_data = unconfirmed_reports.pop(tmp_id, None) or get_pending_reports().get(tmp_id)
                        if rep_data:
                            rep_id = f"REP-{int(time.time()) % 100000:05d}"
                            rep_data["report_id"] = rep_id
                            rep_data["created_at"] = int(time.time())
                            save_pending_report(rep_id, rep_data)
                            msg = handle_admin_decision("approve", rep_id, cb_chat_id, username, cb_msg_id)
                            answer_callback(cb_id, msg, alert=True)
                        else:
                            answer_callback(cb_id, "Sesi laporan telah tamat tempoh.", alert=True)
                        continue

                    if cb_data.startswith("cfrmb_direct_"):
                        admin_ids = get_admin_ids()
                        if cb_chat_id not in admin_ids:
                            answer_callback(cb_id, "Akses Ditolak: Anda bukan admin.", alert=True)
                            continue
                        batch_id = cb_data.replace("cfrmb_direct_", "")
                        rep_data = unconfirmed_reports.pop(batch_id, None) or get_pending_reports().get(batch_id)
                        if rep_data:
                            save_pending_report(batch_id, rep_data)
                            msg = handle_admin_batch_decision("approve", batch_id, cb_chat_id, username, cb_msg_id)
                            answer_callback(cb_id, msg, alert=True)
                        else:
                            answer_callback(cb_id, "Sesi batch telah tamat tempoh.", alert=True)
                        continue

                    # 1b. Pengesahan Laporan Bebas oleh Pengguna Sendiri
                    if cb_data.startswith("cfrm_"):
                        tmp_id = cb_data.replace("cfrm_", "")
                        rep_data = unconfirmed_reports.pop(tmp_id, None) or get_pending_reports().get(tmp_id)
                        if rep_data:
                            rep_id = f"REP-{int(time.time()) % 100000:05d}"
                            rep_data["report_id"] = rep_id
                            rep_data["created_at"] = int(time.time())
                            save_pending_report(rep_id, rep_data)

                            confirm_post_text = (
                                f"✅ *LAPORAN ANDA TELAH DIHANTAR KE BILIK ADMIN KANTOI!*\n"
                                f"Ref ID: `{rep_id}`\n\n"
                                f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @kantoiradar. Terima kasih atas sumbangan anda!\n\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🏛️ *TINDAKAN SUSULAN AGENSI PENGUATKUASAAN*\n"
                                f"Adakah anda ingin kami sediakan dokumen aduan rasmi untuk dilaporkan terus kepada agensi penguatkuasaan Malaysia (NSRC 997, SKMM, Cyber999, BNM & PDRM CCID)?"
                            )
                            confirm_markup = {
                                "inline_keyboard": [
                                    [{"text": "🏛️ Salur Laporan ke Agensi Penguatkuasa", "callback_data": f"enf_{rep_id}"}],
                                    [{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]
                                ]
                            }
                            edit_message(cb_chat_id, cb_msg_id, confirm_post_text, reply_markup=confirm_markup)
                            forward_report_to_admins(rep_id, rep_data)
                            answer_callback(cb_id, "Laporan berjaya dihantar ke Admin!", alert=False)
                        else:
                            answer_callback(cb_id, "Sesi laporan telah tamat tempoh.", alert=True)
                        continue

                    # Pengendali Pusat Tindakan Agensi Penguatkuasaan
                    if cb_data.startswith("enf_em_"):
                        t_id = cb_data.replace("enf_em_", "")
                        rep = unconfirmed_reports.get(t_id) or get_pending_reports().get(t_id) or {}
                        view_text, view_kb = build_enforcement_email_view(t_id, rep)
                        edit_message(cb_chat_id, cb_msg_id, view_text, reply_markup=view_kb, parse_mode="HTML")
                        answer_callback(cb_id, "Deraf Emel SKMM & Cyber999", alert=False)
                        continue

                    if cb_data.startswith("enf_pol_"):
                        t_id = cb_data.replace("enf_pol_", "")
                        rep = unconfirmed_reports.get(t_id) or get_pending_reports().get(t_id) or {}
                        view_text, view_kb = build_enforcement_police_view(t_id, rep)
                        edit_message(cb_chat_id, cb_msg_id, view_text, reply_markup=view_kb, parse_mode="HTML")
                        answer_callback(cb_id, "Deraf Laporan Balai Polis", alert=False)
                        continue

                    if cb_data.startswith("enf_"):
                        t_id = cb_data.replace("enf_", "")
                        rep = unconfirmed_reports.get(t_id) or get_pending_reports().get(t_id) or {}
                        menu_text, menu_kb = build_enforcement_menu(t_id, rep)
                        edit_message(cb_chat_id, cb_msg_id, menu_text, reply_markup=menu_kb, parse_mode="HTML")
                        answer_callback(cb_id, "Pusat Agensi Penguatkuasaan", alert=False)
                        continue

                    elif cb_data.startswith("cncl_"):
                        tmp_id = cb_data.replace("cncl_", "")
                        unconfirmed_reports.pop(tmp_id, None)
                        reports = get_pending_reports()
                        if tmp_id in reports:
                            reports.pop(tmp_id, None)
                            save_json_file(REPORTS_DATA_PATH, reports)
                        edit_message(cb_chat_id, cb_msg_id, "❌ Laporan telah dibatalkan.")
                        answer_callback(cb_id, "Dibatalkan.", alert=False)
                        continue

                    # 1c. Pengesahan Laporan Batch oleh Pengguna Sendiri
                    if cb_data.startswith("cfrmb_"):
                        batch_id = cb_data.replace("cfrmb_", "")
                        rep_data = unconfirmed_reports.pop(batch_id, None) or get_pending_reports().get(batch_id)
                        if rep_data:
                            save_pending_report(batch_id, rep_data)
                            edit_message(
                                cb_chat_id, 
                                cb_msg_id, 
                                f"✅ *LAPORAN PUKAL ({len(rep_data.get('items', []))} ENTRI) TELAH DIHANTAR KE BILIK ADMIN KANTOI!*\n"
                                f"Ref ID: `{batch_id}`\n\n"
                                f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @kantoiradar. Terima kasih atas sumbangan anda!"
                            )
                            forward_batch_report_to_admins(batch_id, rep_data)
                            answer_callback(cb_id, "Laporan pukal berjaya dihantar ke Admin!", alert=False)
                        else:
                            answer_callback(cb_id, "Sesi laporan telah tamat tempoh.", alert=True)
                        continue

                    # 1e. Hab 30 FAQ Mangsa Scam (Hak & Prosedur)
                    if cb_data == "faq_hub":
                        f_text, f_kb = kantoi_faq_vault.build_faq_hub_view()
                        edit_message(cb_chat_id, cb_msg_id, f_text, reply_markup=f_kb, parse_mode="HTML")
                        answer_callback(cb_id, "Hab 30 FAQ Mangsa Scam", alert=False)
                        continue

                    if cb_data.startswith("faq_cat_"):
                        c_id = cb_data.replace("faq_cat_", "")
                        f_text, f_kb = kantoi_faq_vault.build_faq_category_view(c_id)
                        edit_message(cb_chat_id, cb_msg_id, f_text, reply_markup=f_kb, parse_mode="HTML")
                        answer_callback(cb_id, "Kategori FAQ", alert=False)
                        continue

                    if cb_data.startswith("faq_q_"):
                        q_id = cb_data.replace("faq_q_", "")
                        f_text, f_kb = kantoi_faq_vault.build_faq_answer_view(q_id)
                        edit_message(cb_chat_id, cb_msg_id, f_text, reply_markup=f_kb, parse_mode="HTML")
                        answer_callback(cb_id, "Jawapan FAQ", alert=False)
                        continue

                    # 1d. Pusat Bantuan & Panduan Interaktif Pengguna
                    if cb_data == "help_menu":
                        is_admin = cb_chat_id in get_admin_ids()
                        edit_message(cb_chat_id, cb_msg_id, format_welcome_message(is_admin, user_id=cb_chat_id), get_help_menu_keyboard())
                        answer_callback(cb_id, "Menu Utama", alert=False)
                        continue

                    if cb_data.startswith("help_"):
                        text_guide, markup = get_help_detail(cb_data)
                        edit_message(cb_chat_id, cb_msg_id, text_guide, markup)
                        answer_callback(cb_id, "Panduan dimuatkan", alert=False)
                        continue

                    if cb_data.startswith("repqr_"):
                        tmp_id = cb_data.replace("repqr_", "")
                        rep_data = unconfirmed_reports.get(tmp_id) or get_pending_reports().get(tmp_id)
                        if rep_data:
                            user_sessions[cb_chat_id] = {
                                "step": "STEP_EVIDENCE_FOR_DRAFT",
                                "tmp_id": tmp_id
                            }
                            prompt_txt = (
                                f"📢 *SILA TULIS KETERANGAN KES PENIPUAN*\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"Sasaran: `{rep_data['phone_norm']}` ({rep_data['suspect_name']})\n\n"
                                f"Sila taipkan ringkasan cerita kejadian penipuan / taktik yang digunakan terus ke dalam chat ini:\n"
                                f"_(Contoh: 'Saya bayar deposit RM200 untuk beli telefon tapi barang tak sampai dan dia sekat saya')_\n\n"
                                f"_(Taip /batal untuk membatalkan)_"
                            )
                            send_message(cb_chat_id, prompt_txt)
                            answer_callback(cb_id, "Sila masukkan keterangan kes", alert=False)
                        else:
                            answer_callback(cb_id, "Sesi imbasan telah tamat tempoh.", alert=True)
                        continue

                    if cb_data == "read_bulletin_now":
                        b_text = format_scam_radar_bulletin(5)
                        back_markup = {"inline_keyboard": [[{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]]}
                        edit_message(cb_chat_id, cb_msg_id, b_text, back_markup)
                        answer_callback(cb_id, "Buletin terkini", alert=False)
                        continue

                    if cb_data.startswith("cselive_"):
                        target_q = cb_data.replace("cselive_", "").strip()
                        answer_callback(cb_id, "🔍 Sedang menyemak media sosial & forum KANTOI...", alert=False)
                        if not target_q or target_q == "unknown":
                            send_message(cb_chat_id, "⚠️ Maklumat nombor telefon atau akaun bank tidak dapat dikenal pasti. Sila buat semakan dengan menaip: `/cse <nombor>`", parse_mode="Markdown")
                            continue
                        hud = ProgressHUD(cb_chat_id)
                        hud.start(25, "🔍 Menyemak Pangkalan Data Komuniti KANTOI...", f"Sasaran: {target_q}")
                        hud.update(55, "📚 Mengimbas arkib forum Lowyat.NET, CariGold & KenaScam...", "6,100+ Rekod Forum")
                        hud.update(85, "🏛️ Menyemak silang portal PDRM CCID SemakMule...", "PDRM Live Check")
                        report_html = format_social_media_intel_report(target_q)
                        cse_url = generate_google_cse_url(target_q)
                        markup = {
                            "inline_keyboard": [
                                [{"text": "🔗 Buka Paparan Web Penuh (Google CSE)", "url": cse_url}],
                                [{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]
                            ]
                        }
                        hud.update(100, "✨ Analisis Selesai! Menyusun kad risikan forum...", "Laporan Komuniti")
                        hud.complete_with_message(report_html, reply_markup=markup, parse_mode="HTML")
                        continue

                    # 2. Tindakan oleh Admin
                    admin_ids = get_admin_ids()
                    if cb_chat_id not in admin_ids:
                        answer_callback(cb_id, "Akses Ditolak: Anda bukan admin berdaftar KANTOI.", alert=True)
                        continue

                    if cb_data.startswith("app_"):
                        rep_id = cb_data.replace("app_", "")
                        msg = handle_admin_decision("approve", rep_id, cb_chat_id, username, cb_msg_id)
                        answer_callback(cb_id, msg, alert=True)
                    elif cb_data.startswith("rej_"):
                        rep_id = cb_data.replace("rej_", "")
                        msg = handle_admin_decision("reject", rep_id, cb_chat_id, username, cb_msg_id)
                        answer_callback(cb_id, msg, alert=True)
                    elif cb_data.startswith("appb_"):
                        batch_id = cb_data.replace("appb_", "")
                        msg = handle_admin_batch_decision("approve", batch_id, cb_chat_id, username, cb_msg_id)
                        answer_callback(cb_id, msg, alert=True)
                    elif cb_data.startswith("rejb_"):
                        batch_id = cb_data.replace("rejb_", "")
                        msg = handle_admin_batch_decision("reject", batch_id, cb_chat_id, username, cb_msg_id)
                        answer_callback(cb_id, msg, alert=True)
                    continue

                # ------------------------------------------------------------------
                # B. KENDALIKAN INLINE QUERY (@crowdcallerbot <nombor>)
                # ------------------------------------------------------------------
                inline_query = update.get("inline_query")
                if inline_query:
                    iq_id = inline_query["id"]
                    q = inline_query.get("query", "").strip()
                    if q:
                        rec, norm = query_threat(q)
                        if rec:
                            title = f"🚨 SCAMMER: {rec[0]} ({rec[1]})"
                            desc = f"{rec[2]} | Bank: {rec[3] or 'N/A'}"
                            text_body = format_threat_response(rec, q)
                        else:
                            title = f"✅ BERSIH: {q}"
                            desc = "Tiada rekod penipuan dalam pangkalan data KANTOI."
                            text_body = format_safe_response(q, norm)

                        results = [{
                            "type": "article",
                            "id": "1",
                            "title": title,
                            "description": desc,
                            "input_message_content": {
                                "message_text": text_body,
                                "parse_mode": "Markdown",
                                "disable_web_page_preview": True
                            },
                            "reply_markup": get_threat_check_keyboard(q, norm, has_threat=bool(rec))
                        }]
                        try:
                            requests.post(f"https://api.telegram.org/bot{BOT_TOKEN}/answerInlineQuery",
                                          json={"inline_query_id": iq_id, "results": results, "cache_time": 5},
                                          timeout=5)
                        except Exception:
                            pass
                    continue

                # ------------------------------------------------------------------
                # C. KENDALIKAN MESEJ BIASA
                # ------------------------------------------------------------------
                message = update.get("message") or update.get("edited_message")
                if not message:
                    continue

                msg_id = message.get("message_id")
                # Jangan proses semula mesej yang sama melainkan kemaskini baru
                if msg_id in processed_msg_ids and update.get("edited_message"):
                    continue
                if msg_id:
                    processed_msg_ids.add(msg_id)
                    if len(processed_msg_ids) > 2000:
                        processed_msg_ids.clear()

                chat_id = message["chat"]["id"]
                chat_type = message.get("chat", {}).get("type", "private")
                is_group = chat_type in ["group", "supergroup"]
                user = message.get("from", {})
                username = user.get("username") or user.get("first_name", "Komuniti")
                text = (message.get("text") or message.get("caption") or "").strip()
                photos = message.get("photo")
                doc = message.get("document")

                if not text and not photos and not doc:
                    continue

                # ------------------------------------------------------------------
                # MOD PENJAGA KUMPULAN (GROUP SENTINEL SHIELD)
                # ------------------------------------------------------------------
                if is_group:
                    # 1. Semakan Manual di dalam Group (/semak, /check, /kantoi, /pdrm, /semakmule, /buletin)
                    if text.startswith(("/semak", "/check", "/kantoi")):
                        parts = text.split(maxsplit=1)
                        query = parts[1].strip() if len(parts) > 1 else ""
                        if query:
                            rec, norm = query_threat(query)
                            if rec:
                                reply = format_threat_response(rec, query)
                                markup = get_threat_check_keyboard(query, norm, has_threat=True)
                            else:
                                reply = format_safe_response(query, norm)
                                markup = get_threat_check_keyboard(query, norm, has_threat=False)
                            api_call("sendMessage", {
                                "chat_id": chat_id,
                                "text": reply,
                                "parse_mode": "Markdown",
                                "reply_to_message_id": msg_id,
                                "reply_markup": markup
                            })
                        continue

                    if text.startswith(("/cse", "/social")):
                        parts = text.split(maxsplit=1)
                        target = parts[1].strip() if len(parts) > 1 else ""
                        if target:
                            api_call("sendChatAction", {"chat_id": chat_id, "action": "typing"})
                            reply = format_social_media_intel_report(target)
                            cse_url = generate_google_cse_url(target)
                            markup = {
                                "inline_keyboard": [
                                    [{"text": "🔗 Buka Paparan Web Penuh (Google CSE)", "url": cse_url}],
                                    [{"text": "🏛️ Portal PDRM CCID", "url": "https://semakmule.rmp.gov.my"}]
                                ]
                            }
                            api_call("sendMessage", {
                                "chat_id": chat_id,
                                "text": reply,
                                "parse_mode": "HTML",
                                "reply_to_message_id": msg_id,
                                "reply_markup": markup
                            })
                        continue

                    if text.startswith(("/pdrm", "/semakmule")):
                        parts = text.split(maxsplit=1)
                        if len(parts) > 1 and parts[1].strip():
                            qt = parts[1].strip()
                            clean_digits = re.sub(r'[^\d]', '', qt)
                            cat = "bank" if len(clean_digits) > 11 or not qt.startswith(("01", "+601", "601")) else "telefon"
                            pdrm = query_pdrm_semakmule_live(cat, qt)
                            if pdrm.get("success"):
                                reps = pdrm["police_reports"]
                                searches = pdrm["search_count"]
                                badge = "🚨 <b>REKOD JENAYAH DIKESAN!</b>" if reps > 0 else "✅ <b>TIADA REKOD ADUAN PDRM</b>"
                                rep_msg = (
                                    f"🏛️ <b>STATUS SIAP-SEDIA PDRM CCID SEMAKMULE</b>\n"
                                    f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                    f"🔍 <b>Sasaran:</b> <code>{html.escape(qt)}</code> ({cat.upper()})\n"
                                    f"📊 <b>Keputusan:</b> {badge}\n"
                                    f"📑 <b>Laporan Polis Rasmi:</b> <b>{reps} Laporan</b>\n"
                                    f"👥 <b>Jumlah Carian Awam:</b> <b>{searches} Kali</b>\n"
                                    f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                    f"ℹ️ <i>Data diambil secara langsung dari portal rasmi CCID SemakMule PDRM.</i>"
                                )
                            else:
                                rep_msg = "⚠️ Gagal berhubung dengan pelayan PDRM SemakMule buat masa ini."
                            api_call("sendMessage", {
                                "chat_id": chat_id,
                                "text": rep_msg,
                                "parse_mode": "HTML",
                                "reply_to_message_id": msg_id
                            })
                        continue

                    if text in ["/buletin", "/berita", "buletin", "berita", "/radar"]:
                        bulletin = format_scam_radar_bulletin(3)
                        api_call("sendMessage", {
                            "chat_id": chat_id,
                            "text": bulletin,
                            "parse_mode": "Markdown",
                            "disable_web_page_preview": True,
                            "reply_to_message_id": msg_id
                        })
                        continue

                    # 2. Imbasan Senyap Keselamatan Kumpulan (Zero-Scam Group Guard)
                    # a. Imbas pautan phishing
                    if text:
                        urls = re.findall(r'https?://[^\s<>"]+', text)
                        phishing_hit = None
                        for u in urls:
                            u_res = inspect_url_phishing(u)
                            if u_res["severity"] in ["CRITICAL", "HIGH"]:
                                phishing_hit = u_res
                                break
                        if phishing_hit:
                            api_call("deleteMessage", {"chat_id": chat_id, "message_id": msg_id})
                            warning_msg = (
                                f"🚨 <b>PERISAI KANTOI: PAUTAN PHISHING DIPADAM!</b>\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"⚠️ Mesej daripada <b>@{html.escape(username)}</b> telah dipadamkan secara automatik kerana mengandungi pautan pancingan data berbahaya:\n\n"
                                f"🌐 <b>Domain:</b> <code>{html.escape(phishing_hit['domain'])}</code>\n"
                                f"🏷️ <b>Keputusan:</b> {html.escape(phishing_hit['verdict'])}\n\n"
                                f"⚠️ <i>Semua ahli kumpulan diingatkan agar TIDAK menekan pautan atau memasukkan maklumat perbankan!</i>\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🛡️ <i>KANTOI Group Sentinel Shield</i>"
                            )
                            api_call("sendMessage", {"chat_id": chat_id, "text": warning_msg, "parse_mode": "HTML"})
                            print(f"[GROUP SENTINEL] Pautan phishing dipadamkan dalam kumpulan {chat_id} daripada @{username}: {phishing_hit['domain']}")
                            continue

                    # b. Imbas fail APK berniat jahat
                    if doc:
                        doc_name = (doc.get("file_name") or "").lower()
                        doc_mime = (doc.get("mime_type") or "").lower()
                        if doc_name.endswith(".apk") or "package-archive" in doc_mime:
                            tmp_apk = download_telegram_photo(doc["file_id"])
                            if tmp_apk:
                                try:
                                    apk_res = inspect_apk_malware(tmp_apk, doc.get("file_name", "aplikasi.apk"))
                                    if apk_res.get("severity") in ["CRITICAL", "HIGH"]:
                                        api_call("deleteMessage", {"chat_id": chat_id, "message_id": msg_id})
                                        warning_msg = (
                                            f"🚨 <b>PERISAI KANTOI: FAIL APK MALWARE DIPADAM!</b>\n"
                                            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                            f"⚠️ Fail <code>{html.escape(apk_res['filename'])}</code> daripada <b>@{html.escape(username)}</b> telah dipadamkan secara automatik!\n\n"
                                            f"📦 <b>Klasifikasi:</b> {html.escape(apk_res['verdict'])}\n"
                                            f"🔑 <b>Hash:</b> <code>{apk_res['sha256'][:20]}...</code>\n\n"
                                            f"⚠️ <i>Fail ini mengandungi kod trojan perbankan / pencuri SMS TAC/OTP. JANGAN PASANG!</i>\n"
                                            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                            f"🛡️ <i>KANTOI Group Sentinel Shield</i>"
                                        )
                                        api_call("sendMessage", {"chat_id": chat_id, "text": warning_msg, "parse_mode": "HTML"})
                                        print(f"[GROUP SENTINEL] APK malware dipadamkan dalam kumpulan {chat_id} daripada @{username}: {apk_res['filename']}")
                                        continue
                                finally:
                                    if os.path.exists(tmp_apk):
                                        try: os.unlink(tmp_apk)
                                        except: pass

                    # c. Imbas nombor/akaun scammer dalam teks
                    threat_found = None
                    if text:
                        rec, _ = query_threat(text)
                        if rec:
                            threat_found = rec

                    # d. Imbas kod QR atau imej dalam kumpulan (Quishing & Scammer Shield)
                    if not threat_found and (photos or (doc and (doc.get("mime_type", "").startswith("image/") or (doc.get("file_name", "")).lower().endswith((".png", ".jpg", ".jpeg", ".webp"))))):
                        target_file_id = photos[-1]["file_id"] if photos else doc["file_id"]
                        tmp_img = download_telegram_photo(target_file_id)
                        if tmp_img:
                            try:
                                intel = extract_hybrid_image_intel(tmp_img, GEMINI_API_KEY)
                                if intel.get("has_intel"):
                                    # 1. Semak jika kod QR mengandungi pautan Phishing atau APK Dropper
                                    qr_info = intel.get("qr_data")
                                    if qr_info and qr_info.get("url"):
                                        u_res = inspect_url_phishing(qr_info["url"])
                                        if u_res["severity"] in ["CRITICAL", "HIGH"]:
                                            threat_found = ("QUISHING_URL", qr_info["url"], u_res["verdict"], u_res["domain"])

                                    # 2. Semak rekod scammer dalam pangkalan data komuniti KANTOI
                                    if not threat_found:
                                        for p in intel.get("all_phones", []):
                                            rec, _ = query_threat(p)
                                            if rec:
                                                threat_found = rec
                                                break
                                    if not threat_found and intel.get("account_number") and intel.get("account_number") != "N/A":
                                        rec, _ = query_threat(intel["account_number"])
                                        if rec:
                                            threat_found = rec
                            finally:
                                if os.path.exists(tmp_img):
                                    try:
                                        os.unlink(tmp_img)
                                    except Exception:
                                        pass

                    # Jika ancaman scammer dikesan dalam kumpulan:
                    if threat_found:
                        api_call("deleteMessage", {"chat_id": chat_id, "message_id": msg_id})
                        if isinstance(threat_found, tuple) and threat_found[0] == "QUISHING_URL":
                            warning_msg = (
                                f"🚨 <b>PERISAI KANTOI: KOD QR HASAD (QUISHING / MALWARE) DIPADAMKAN!</b>\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"⚠️ Mesej / Imej daripada <b>@{html.escape(username)}</b> telah dipadamkan secara automatik kerana mengandungi Kod QR berpautkan laman pancingan data (Phishing) / fail Trojan APK:\n\n"
                                f"🔗 <b>Pautan Dikesan:</b> <code>{html.escape(threat_found[1])}</code>\n"
                                f"🌐 <b>Domain:</b> <code>{html.escape(threat_found[3])}</code>\n"
                                f"🏷️ <b>Klasifikasi:</b> {html.escape(threat_found[2])}\n\n"
                                f"⚠️ <i>Semua ahli kumpulan diingatkan agar TIDAK mengimbas sebarang kod QR mencurigakan yang dihantar ke dalam kumpulan!</i>\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🛡️ <i>KANTOI Group Sentinel Shield</i>"
                            )
                            print(f"[GROUP SENTINEL] Quishing QR dipadamkan dalam kumpulan {chat_id} daripada @{username}: {threat_found[1]}")
                        else:
                            warning_msg = (
                                f"🚨 <b>PERISAI KANTOI: ANCAMAN SCAMMER DIKESAN DALAM KUMPULAN!</b>\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"⚠️ Mesej / Imej daripada <b>@{html.escape(username)}</b> telah dipadamkan secara automatik kerana mengandungi rekod SCAMMER yang disahkan:\n\n"
                                f"📱 <b>Nombor / Akaun:</b> <code>{threat_found[0]}</code>\n"
                                f"👤 <b>Suspek:</b> {html.escape(threat_found[1] or 'N/A')}\n"
                                f"🏷️ <b>Klasifikasi:</b> {html.escape(threat_found[2] or 'AMARAN')}\n"
                                f"🏦 <b>Maklumat Bank:</b> {html.escape(threat_found[3] or 'N/A')}\n\n"
                                f"⚠️ <i>Semua ahli kumpulan diingatkan agar TIDAK memindahkan sebarang wang atau berurusan dengan pihak ini!</i>\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🛡️ <i>KANTOI Group Sentinel Shield</i>"
                            )
                            print(f"[GROUP SENTINEL] Ancaman dipadamkan dalam kumpulan {chat_id} daripada @{username}: {threat_found[0]}")
                        api_call("sendMessage", {"chat_id": chat_id, "text": warning_msg, "parse_mode": "HTML"})
                        continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: ENJIN IMBASAN IMEJ & KOD QR HIBRID
                # ------------------------------------------------------------------
                def process_image_intel_message(t_chat_id, t_username, t_file_id, t_caption, is_doc=False, doc_filename="", reply_to_msg_id=None):
                    queue_image_for_case_aggregation(t_chat_id, t_username, t_file_id, t_caption, msg_id=reply_to_msg_id, is_doc=is_doc, doc_filename=doc_filename)

                # Kendalikan Foto yang dihantar (Multi-Image Buffer Aggregator)
                if photos:
                    queue_image_for_case_aggregation(chat_id, username, photos[-1]["file_id"], text, msg_id=msg_id)
                    continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: KENDALIKAN DOKUMEN / FAIL APK (SANDBOX MALWARE)
                # ------------------------------------------------------------------
                if doc:
                    doc_name = (doc.get("file_name") or "").lower()
                    doc_mime = (doc.get("mime_type") or "").lower()
                    is_img_doc = (
                        doc_mime.startswith("image/") or 
                        doc_name.endswith((".png", ".jpg", ".jpeg", ".webp", ".bmp"))
                    )

                    # Jika pengguna menghantar gambar sebagai dokumen / fail tanpa mampatan
                    if is_img_doc:
                        queue_image_for_case_aggregation(chat_id, username, doc["file_id"], text, msg_id=msg_id, is_doc=True, doc_filename=doc.get("file_name", ""))
                        continue

                    if doc_name.endswith(".apk") or "package-archive" in doc_mime:
                        hud = ProgressHUD(chat_id, reply_to_msg_id=msg_id)
                        hud.start(15, "📥 Memuat turun fail APK ke persekitaran Sandbox KANTOI...", f"Fail: {doc.get('file_name', 'aplikasi.apk')}")
                        api_call("sendChatAction", {"chat_id": chat_id, "action": "upload_document"})
                        tmp_apk = download_telegram_photo(doc["file_id"])
                        if tmp_apk:
                            try:
                                hud.update(45, "📦 Membongkar Arkib Android & membaca AndroidManifest.xml...", "AXML Decoder")
                                hud.update(75, "🔍 Mengimbas dex bytecode, kebenaran berbahaya & token C2...", "Static Bytecode Scanner")
                                rep = inspect_apk_malware(tmp_apk, doc.get("file_name", "aplikasi.apk"))
                                hud.update(100, "✨ Analisis Sandbox Selesai! Menyusun laporan ancaman...", "Sandbox Verdict")
                                if "error" in rep:
                                    hud.complete_with_message(f"❌ Ralat menganalisis APK: {rep['error']}")
                                else:
                                    badge = "🚨" if rep["severity"] == "CRITICAL" else ("⚠️" if rep["severity"] in ["HIGH", "MEDIUM"] else "✅")
                                    lines = [
                                        f"{badge} *HASIL ANALISIS SANDBOX MALWARE APK (KANTOI)*",
                                        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                        f"📁 *Nama Fail:* `{rep['filename']}` ({rep['size_mb']} MB)",
                                        f"🔑 *SHA256:* `{rep['sha256'][:24]}...`",
                                        f"🚨 *KEPUTUSAN:* *{rep['verdict']}*",
                                        ""
                                    ]
                                    if rep["danger_perms"]:
                                        lines.append("⚠️ *Kebenaran Berbahaya Dikesan:*")
                                        for dp, desc in rep["danger_perms"]:
                                            lines.append(f"   • `{dp}`: {desc}")
                                        lines.append("")
                                    if rep["dex_tokens"]:
                                        lines.append(f"🤖 *Bot Exfiltration (C2) Dikesan:* `{len(rep['dex_tokens'])}` Token Bot Telegram")
                                    if rep["has_banking_strings"]:
                                        lines.append("🏦 *Sasaran:* Rentetan aplikasi bank tempatan (Maybank/CIMB/TNG) ditemui dalam kod!")
                                    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    if rep["severity"] == "CRITICAL":
                                        lines.append("🚫 *NASIHAT KESELAMATAN:* JANGAN PASANG fail ini! Ia mengandungi kod trojan pencuri SMS TAC/OTP perbankan anda.")
                                    else:
                                        lines.append("ℹ️ _Dianalisis melalui KANTOI Static Android Sandbox tanpa menjalankan kod._")

                                    hud.complete_with_message("\n".join(lines), parse_mode="Markdown")
                            finally:
                                if os.path.exists(tmp_apk):
                                    try: os.unlink(tmp_apk)
                                    except: pass
                        else:
                            hud.complete_with_message("❌ Gagal memuat turun fail APK dari pelayan Telegram.")
                        continue
                    else:
                        send_message(chat_id, "📄 *Dokumen Diterima.* Bot kini menyokong imbasan kod QR / gambar dan fail Android (`.apk`). Sila muat naik fail APK atau gambar kod QR untuk semakan.")
                        continue

                # MOD CHAT PERIBADI: PERINTAH BULETIN & RADAR ANCAMAN (IDEA 6)
                # ------------------------------------------------------------------
                if text in ["/buletin", "/berita", "buletin", "berita", "/radar"]:
                    bulletin = format_scam_radar_bulletin(5)
                    send_message(chat_id, bulletin)
                    continue

                if text.startswith("/siar_radar"):
                    if chat_id not in get_admin_ids():
                        send_message(chat_id, "❌ Akses Ditolak: Perintah ini hanya untuk Admin KANTOI.")
                        continue
                    bulletin = format_scam_radar_bulletin(5)
                    api_call("sendMessage", {
                        "chat_id": CHANNEL_ID,
                        "text": bulletin,
                        "parse_mode": "Markdown",
                        "disable_web_page_preview": True
                    })
                    send_message(chat_id, f"✅ *Buletin Radar Ancaman berjaya disiarkan ke saluran {CHANNEL_ID}!*")
                    continue

                if text.startswith(("/siar_roadmap", "/roadmap")):
                    if text.startswith("/siar_roadmap") and chat_id not in get_admin_ids():
                        send_message(chat_id, "❌ Akses Ditolak: Perintah ini hanya untuk Admin KANTOI.")
                        continue
                    roadmap_msg = (
                        "🗺️ <b>PENGUMUMAN RASMI: PELAN HALA TUJU KANTOI (ROADMAP 2026/2027)</b>\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
                        "Terima kasih kepada ribuan ahli komuniti yang sentiasa bersama KANTOI dalam memerangi sindiket penipuan siber dan akaun keldai di Malaysia.\n\n"
                        "Bagi memastikan projek KANTOI terus berkembang aktif dan memberi impak perlindungan maksimum, berikut adalah <b>Pelan Hala Tuju Pembangunan Rasmi</b> yang telah dijadualkan secara berperingkat:\n\n"
                        "🚀 <b>FASA 1: BULAN INI (KINI DILANCARKAN!)</b>\n"
                        "• 🛡️ <b>1-Tap Floating Threat HUD (Gelembung Terapung):</b>\n"
                        "  Pemeriksa pantas KANTOI kini boleh terapung di atas WhatsApp, Telegram, FB, dan Aplikasi Perbankan. Ketik 1 kali untuk membuka kad risikan tanpa perlu menutup chat anda!\n"
                        "• 🤖 <b>Dossier Risikan Bersepadu 1-Paparan (@crowdcallerbot):</b>\n"
                        "  Hantar tangkapan layar (screenshot) yang mengandungi Nombor Telefon, Akaun Bank, dan Kod QR DuitNow sekali gus — bot akan menyemak kesemuanya serentak dalam 1 mesej kemas!\n\n"
                        "⏳ <b>FASA 2: BULAN DEPAN (NOVEMBER 2026)</b>\n"
                        "• 📋 <b>Auto-Clipboard Mule Sentinel:</b>\n"
                        "  Pengesanan automatik di latar belakang sebaik sahaja anda 'Copy' nombor akaun bank atau telefon di WhatsApp sebelum membuat sebarang pemindahan wang.\n\n"
                        "⏳ <b>FASA 3: BULAN DISEMBER 2026</b>\n"
                        "• 📱 <b>Native In-App Dossier View:</b>\n"
                        "  Paparan terus data siasatan SemakMule CCID PDRM dan forum Lowyat secara natif di dalam aplikasi KANTOI tanpa perlu membuka pelayar web luaran.\n\n"
                        "⏳ <b>FASA 4: AWAL TAHUN 2027</b>\n"
                        "• 📞 <b>WhatsApp Call & Contact Risk Sentinel:</b>\n"
                        "  Perlindungan masa nyata daripada panggilan suara WhatsApp scammer dan audit keselamatan buku kenalan dalam 1 ketikan.\n\n"
                        "💡 <b>FASA MASA DEPAN (DALAM PENYELIDIKAN):</b>\n"
                        "• 📸 <i>Pengecam Resit Bank Palsu (AI Slip Forgery Detector)</i>\n"
                        "• 👨‍👩‍👧 <i>Mod Perlindungan Keluarga & Warga Emas (Family Shield)</i>\n\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        "📲 <b>Kekal dilindungi & sebarkan benteng ini:</b>\n"
                        "• Telegram Bot: @crowdcallerbot\n"
                        "• Saluran Rasmi: https://t.me/kantoiradar\n"
                        "• Kod Sumber GitHub: https://github.com/kand4/kantoi\n\n"
                        "🛡️ <i>Bersama Komuniti KANTOI — Sekali Kena, Selamanya Kantoi!</i>"
                    )
                    if text.startswith("/siar_roadmap"):
                        api_call("sendMessage", {
                            "chat_id": CHANNEL_ID,
                            "text": roadmap_msg,
                            "parse_mode": "HTML",
                            "disable_web_page_preview": True
                        })
                        send_message(chat_id, f"✅ <b>Pelan Hala Tuju (Roadmap) berjaya disiarkan ke saluran {CHANNEL_ID}!</b>", parse_mode="HTML")
                    else:
                        send_message(chat_id, roadmap_msg, parse_mode="HTML")
                    continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: SEMAKAN MANUAL /SEMAK & /CHECK (1-CLICK DOSSIER)
                # ------------------------------------------------------------------
                if text.startswith(("/semak", "/check", "/kantoi")):
                    parts = text.split(maxsplit=1)
                    if len(parts) > 1 and parts[1].strip():
                        target = parts[1].strip()
                        hud = ProgressHUD(chat_id, reply_to_msg_id=msg_id)
                        hud.start(25, "🔍 Menyelaras risikan PDRM CCID & Pangkalan Data KANTOI...", f"Sasaran: {target}")
                        hud.update(70, "🌐 Mengimbas forum Lowyat, CariGold & FB (Google CSE)...", "Deep Intel")
                        dossier_html, dossier_kb, is_threat = build_unified_cyber_dossier(
                            raw_query=target,
                            source_badge="💬 Carian Teks Terus",
                            is_admin=(chat_id in get_admin_ids())
                        )
                        hud.update(100, "✨ Analisis Selesai! Menyusun Kad Risikan...", "Sedia")
                        hud.complete_with_message(dossier_html, reply_markup=dossier_kb, parse_mode="HTML")
                        continue
                    else:
                        send_message(chat_id, "ℹ️ Format semakan: `/semak <nombor_telefon_atau_akaun_bank>`\nContoh: `/semak 0179597645`")
                        continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: CARIAN MEDIA SOSIAL GOOGLE CSE (KANTOI ENGINE)
                # ------------------------------------------------------------------
                if text.startswith(("/cse", "/social")):
                    parts = text.split(maxsplit=1)
                    if len(parts) > 1 and parts[1].strip():
                        target = parts[1].strip()
                        api_call("sendChatAction", {"chat_id": chat_id, "action": "typing"})
                        reply = format_social_media_intel_report(target)
                        cse_url = generate_google_cse_url(target)
                        markup = {
                            "inline_keyboard": [
                                [{"text": "🔗 Buka Paparan Web Penuh (Google CSE)", "url": cse_url}],
                                [
                                    {"text": "🏛️ Portal PDRM CCID", "url": "https://semakmule.rmp.gov.my"},
                                    {"text": "📰 Buletin Berita Scam", "callback_data": "read_bulletin_now"}
                                ]
                            ]
                        }
                        send_message(chat_id, reply, reply_markup=markup, parse_mode="HTML")
                        continue
                    else:
                        send_message(
                            chat_id,
                            "ℹ️ *Format Carian Media Sosial:*\n`/cse <nombor_telefon_atau_akaun_bank>`\n\nContoh:\n• `/cse 0179597645`\n• `/cse 112028138548`\n• `/social scammer pakej umrah`"
                        )
                        continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: SEMAKAN LANGSUNG PDRM CCID (IDEA 4)
                # ------------------------------------------------------------------
                if text.startswith(("/pdrm", "/semakmule")):
                    parts = text.split(maxsplit=1)
                    if len(parts) > 1 and parts[1].strip():
                        qt = parts[1].strip()
                        clean_digits = re.sub(r'[^\d]', '', qt)
                        cat = "bank" if len(clean_digits) > 11 or not qt.startswith(("01", "+601", "601")) else "telefon"
                        pdrm = query_pdrm_semakmule_live(cat, qt)
                        if pdrm.get("success"):
                            reps = pdrm["police_reports"]
                            searches = pdrm["search_count"]
                            badge = "🚨 *REKOD JENAYAH DIKESAN!*" if reps > 0 else "✅ *TIADA REKOD ADUAN PDRM*"
                            rep_msg = (
                                f"🏛️ *STATUS SIAP-SEDIA PDRM CCID SEMAKMULE*\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🔍 *Sasaran:* `{qt}` ({cat.upper()})\n"
                                f"📊 *Keputusan:* {badge}\n"
                                f"📑 *Laporan Polis Rasmi:* *{reps} Laporan*\n"
                                f"👥 *Jumlah Carian Awam:* *{searches} Kali*\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"ℹ️ _Data diambil secara langsung dari portal rasmi CCID SemakMule PDRM._"
                            )
                        else:
                            rep_msg = "⚠️ Gagal berhubung dengan pelayan PDRM SemakMule buat masa ini. Sila cuba sebentar lagi."
                        send_message(chat_id, rep_msg)
                        continue
                    else:
                        send_message(chat_id, "ℹ️ Format carian PDRM: `/pdrm <nombor_telefon_atau_akaun_bank>`")
                        continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: SEMAKAN PAUTAN PHISHING (IDEA 3)
                # ------------------------------------------------------------------
                if text.startswith("/url") or (re.search(r'https?://[^\s<>"]+', text) and not is_likely_report(text)):
                    urls = re.findall(r'https?://[^\s<>"]+', text)
                    if urls:
                        for u in urls[:3]:
                            res_url = inspect_url_phishing(u)
                            badge = "🚨" if res_url["severity"] in ["CRITICAL", "HIGH"] else ("⚠️" if res_url["severity"] == "MEDIUM" else "✅")
                            lines = [
                                f"{badge} *HASIL SEMAKAN KESELAMATAN PAUTAN (URL)*",
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                f"🔗 *Pautan:* `{res_url['url']}`",
                                f"🌐 *Domain Pendaratan:* `{res_url['domain']}`",
                                f"🛡️ *Keputusan:* *{res_url['verdict']}*",
                                ""
                            ]
                            if res_url["reasons"]:
                                lines.append("⚠️ *Faktor Penilaian Risiko:*")
                                for rsn in res_url["reasons"]:
                                    lines.append(f"   • {rsn}")
                                lines.append("")
                            lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            if res_url["severity"] in ["CRITICAL", "HIGH"]:
                                lines.append("🚫 *AMARAN:* Jangan masukkan kata laluan atau TAC bank anda di pautan ini!")
                            else:
                                lines.append("ℹ️ _Sentiasa pastikan domain rasmi bank berakhir dengan domain yang disahkan._")

                            send_message(chat_id, "\n".join(lines))
                        continue

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: BYOK (BRING YOUR OWN KEY) UNTUK PENGGUNA AWAM
                # ------------------------------------------------------------------
                if text.startswith(("/mykey", "/kuncisaya", "/byok")):
                    parts = text.split(maxsplit=1)
                    subcmd = parts[1].strip() if len(parts) > 1 else ""

                    if subcmd.lower() in ["padam", "reset", "buang", "hapus", "delete", "remove"]:
                        if remove_user_gemini_key(chat_id):
                            send_message(
                                chat_id,
                                "✅ <b>Kunci API Peribadi (BYOK) Berjaya Dipadam!</b>\n\n"
                                "Akaun anda kini kembali menggunakan kuota pelayan KANTOI.\n"
                                "Jika anda ingin mendaftarkan kunci baharu semula pada bila-bila masa, taip:\n"
                                "<code>/mykey &lt;KUNCI_API_ANDA&gt;</code>",
                                parse_mode="HTML"
                            )
                        else:
                            send_message(chat_id, "ℹ️ Anda belum mendaftarkan sebarang kunci peribadi (BYOK). Anda kini menggunakan kuota pelayan KANTOI.")
                        continue

                    if subcmd:
                        candidate_k = subcmd.strip(" \t\r\n\'\"")
                        is_valid_pattern = bool(candidate_k.startswith(("AQ.", "AQ", "AIza")) or len(candidate_k) >= 15)
                        if not is_valid_pattern:
                            send_message(
                                chat_id,
                                "⚠️ <b>Format Kunci API Tidak Sah:</b>\n"
                                f"Teks <code>{html.escape(candidate_k[:20])}</code> bukan format kunci Google Gemini yang sah.\n\n"
                                "💡 <b>Format Rasmi Google AI Studio:</b>\n"
                                "• <b>Format Terkini:</b> Bermula dengan <code>AQ....</code> (~53 aksara)\n"
                                "• <b>Format Standard:</b> Bermula dengan <code>AIzaSy...</code> (39 aksara)\n\n"
                                "Dapatkan kunci percuma di https://aistudio.google.com/ dan taip:\n"
                                "<code>/mykey &lt;KUNCI_API_PENUH&gt;</code>",
                                parse_mode="HTML"
                            )
                            continue

                        api_call("sendChatAction", {"chat_id": chat_id, "action": "typing"})
                        is_ok, res_model_or_err = test_gemini_api_key(candidate_k)
                        if is_ok:
                            set_user_gemini_key(chat_id, candidate_k, model=res_model_or_err)
                            masked = f"{candidate_k[:8]}...{candidate_k[-4:]}"
                            send_message(
                                chat_id,
                                "🎉 <b>TAHNIAH! KUNCI GEMINI PERIBADI ANDA BERJAYA DIAKTIFKAN (BYOK)!</b>\n"
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🔑 <b>Kunci Disimpan:</b> <code>{masked}</code>\n"
                                f"⚡ <b>Model Beroperasi:</b> <code>{res_model_or_err}</code> (Google AI Vision)\n"
                                f"👤 <b>Status Kuota:</b> 100% Kuota Peribadi Anda (Percuma & Berasingan)\n\n"
                                "🛡️ <b>Kelebihan Menggunakan BYOK:</b>\n"
                                "1. Imbasan tangkap layar WhatsApp & profil suspek sentiasa pantas tanpa had perkongsian awam.\n"
                                "2. Kunci disimpan selamat dan hanya digunakan khusus untuk pertanyaan akaun Telegram anda.\n"
                                "3. Anda boleh memadamkan kunci bila-bila masa dengan arahan <code>/mykey padam</code>.\n\n"
                                "📸 <i>Sila hantarkan tangkap layar (screenshot) WhatsApp, panggilan scammer, atau resit bank untuk mula menguji!</i>",
                                parse_mode="HTML"
                            )
                        else:
                            send_message(
                                chat_id,
                                f"❌ <b>Pengesahan Kunci Gagal:</b>\n"
                                f"Google tidak menerima kunci yang anda berikan.\n"
                                f"<b>Sebab:</b> <code>{html.escape(str(res_model_or_err))}</code>\n\n"
                                "💡 <i>Pastikan kunci disalin penuh daripada <a href=\"https://aistudio.google.com/\">Google AI Studio</a>.</i>",
                                parse_mode="HTML"
                            )
                        continue

                    # Jika pengguna menaip /mykey sahaja tanpa hujah: Paparkan Status BYOK
                    user_k = get_user_gemini_key(chat_id)
                    server_k, server_src, _ = get_gemini_api_key_details()

                    if user_k:
                        masked_u = f"{user_k[:8]}...{user_k[-4:]}"
                        send_message(
                            chat_id,
                            "🔑 <b>STATUS KUNCI PERIBADI ANDA (BYOK)</b>\n"
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            "✅ <b>Status:</b> 🟢 Aktif & Digunakan\n"
                            f"🔑 <b>Kunci Anda:</b> <code>{masked_u}</code>\n"
                            "⚡ <b>Enjin:</b> Google Gemini AI Vision (Kuota Peribadi)\n\n"
                            "💡 Semua imbasan gambar, resit dan tangkap layar anda menggunakan kuota peribadi ini.\n\n"
                            "🗑️ <b>Padam Kunci:</b> Taip <code>/mykey padam</code> untuk memadam kunci anda dan kembali ke kuota pelayan.",
                            parse_mode="HTML"
                        )
                    else:
                        server_status = f"🟢 Tersedia ({server_src})" if server_k else "⚪ Belum Ditetapkan"
                        send_message(
                            chat_id,
                            "🔑 <b>DAFTAR KUNCI GEMINI PERIBADI (BYOK - BRING YOUR OWN KEY)</b>\n"
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            "Status Anda: ⚪ <b>Menggunakan Kuota Pelayan KANTOI</b>\n"
                            f"Status Pelayan: {server_status}\n\n"
                            "💡 <b>Mengapa Galakkan BYOK?</b>\n"
                            "Google AI Studio menyediakan Kunci API Gemini <b>100% PERCUMA</b> dengan had ribuan permintaan setiap hari. Mendaftarkan kunci anda sendiri memastikan bot tidak terganggu jika kuota awam pelayan telah penuh.\n\n"
                            "👉 <b>Cara Mendapatkan Kunci Percuma (1 Minit):</b>\n"
                            "1. Buka laman: https://aistudio.google.com/\n"
                            "2. Log masuk akaun Google & klik butang <b>'Get API key'</b>\n"
                            "3. Salin kunci (bermula <code>AQ...</code> atau <code>AIzaSy...</code>)\n"
                            "4. Hantar ke sini dengan arahan:\n"
                            "   <code>/mykey &lt;KUNCI_API_ANDA&gt;</code>\n\n"
                            "<i>(Privasi: Kunci anda hanya digunakan untuk semakan mesej akaun anda sendiri)</i>",
                            parse_mode="HTML"
                        )
                    continue

                # Arahan Konfigurasi & Status Gemini API: /gemini, /key, /ai, /ai_status
                if text.startswith(("/gemini", "/key", "/ai_status", "/ai")):
                    parts = text.split(maxsplit=2)
                    candidate_key = ""
                    has_admin_passcode = False
                    if len(parts) > 1:
                        if len(parts) > 2 and parts[1] == ADMIN_PASSCODE:
                            candidate_key = parts[2].strip()
                            has_admin_passcode = True
                        else:
                            candidate_key = parts[1].strip()

                    # KESELAMATAN (SEC-02): Sekat akses jika bukan Admin dan tiada Passcode sah
                    is_admin_user = chat_id in get_admin_ids()
                    if not is_admin_user and not has_admin_passcode:
                        denied_msg = (
                            "\u26d4 <b>Akses Ditolak (Pentadbir Sahaja):</b>\n"
                            "Arahan <code>/gemini</code> dikhaskan untuk penetapan Kunci Pelayan KANTOI oleh Pentadbir.\n\n"
                            "\ud83d\udc49 <b>Untuk Pengguna Komuniti:</b>\n"
                            "Sila gunakan arahan <code>/mykey &lt;KUNCI_API&gt;</code> untuk mendaftar Kunci Percuma Peribadi anda (BYOK) secara berasingan tanpa had kuota awam!"
                        )
                        send_message(chat_id, denied_msg, parse_mode="HTML")
                        continue

                    # Pengesahan format: Terima kunci format baharu (AQ...) atau lama (AIza...) atau panjang >= 15
                    is_valid_key_pattern = bool(
                        candidate_key and (
                            candidate_key.startswith(("AQ.", "AQ", "AIza")) or len(candidate_key) >= 15
                        )
                    )

                    # Jika pengguna menaip teks yang terlalu pendek atau perkataan rawak (cth /gemini APIKEY)
                    if candidate_key and not is_valid_key_pattern:
                        send_message(
                            chat_id,
                            "⚠️ <b>Format Kunci API Tidak Sah:</b>\n"
                            f"Teks yang anda masukkan (<code>{html.escape(candidate_key[:25])}</code>) bukan format kunci Google Gemini yang sah.\n\n"
                            "💡 <b>Format Kunci Rasmi Google AI Studio:</b>\n"
                            "• <b>Format Terkini (Auth Key):</b> Bermula dengan <code>AQ....</code> (~53 aksara)\n"
                            "• <b>Format Standard (API Key):</b> Bermula dengan <code>AIzaSy...</code> (39 aksara)\n\n"
                            "Sila salin kunci penuh daripada https://aistudio.google.com/ dan taip:\n"
                            "<code>/gemini &lt;KUNCI_API_PENUH&gt;</code>",
                            parse_mode="HTML"
                        )
                        continue

                    # Jika pengguna membekalkan Kunci API baharu
                    if candidate_key and is_valid_key_pattern:
                        api_call("sendChatAction", {"chat_id": chat_id, "action": "typing"})
                        # Uji kunci secara langsung ke Google Gemini API (Model Terpantas: gemini-2.0-flash)
                        test_models = AVAILABLE_GEMINI_MODELS
                        t_resp = None
                        success_model = ""
                        for tm in test_models:
                            test_url = f"https://generativelanguage.googleapis.com/v1beta/models/{tm}:generateContent?key={candidate_key}"
                            test_headers = {
                                "x-goog-api-key": candidate_key,
                                "Content-Type": "application/json"
                            }
                            test_body = {"contents": [{"parts": [{"text": "PONG"}]}]}
                            try:
                                t_resp = requests.post(test_url, headers=test_headers, json=test_body, timeout=12)
                            except Exception:
                                try:
                                    import urllib3
                                    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
                                    t_resp = requests.post(test_url, headers=test_headers, json=test_body, timeout=12, verify=False)
                                except Exception:
                                    continue
                            if t_resp and t_resp.status_code == 200:
                                success_model = tm
                                break

                        if t_resp and t_resp.status_code == 200:
                            set_gemini_api_key(candidate_key)
                            add_admin(chat_id)
                            key_type_label = "Google Auth Key (Format Terkini AQ)" if candidate_key.startswith("AQ") else "Google API Key"
                            send_message(
                                chat_id,
                                "🎉 <b>BERJAYA! KUNCI GOOGLE GEMINI VISION DISAHKAN AKTIF!</b>\n"
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🔑 <b>Kunci Disimpan:</b> <code>{candidate_key[:8]}...{candidate_key[-4:]}</code>\n"
                                f"🏷️ <b>Jenis Kunci:</b> {key_type_label}\n"
                                f"⚡ <b>Model Vision Terpantas:</b> <code>{success_model}</code> (Ultra-Fast Vision)\n"
                                "📁 <b>Fail Konfigurasi:</b> Disimpan ke <code>admin_config.json</code> & <code>local_secrets.py</code>\n"
                                "👑 <b>Akses Admin:</b> ID Telegram anda didaftarkan sebagai Admin KANTOI.\n\n"
                                "🤖 <i>Kini bot anda 100% berkuasa tinggi! Hantarkan sebarang tangkapan skrin WhatsApp, kenalan (~ Prem Kumar), resit bank, atau panggilan masuk untuk diekstrak nombornya secara automatik!</i>",
                                parse_mode="HTML"
                            )
                        elif t_resp:
                            err_txt = t_resp.text[:220].replace('\n', ' ')
                            send_message(
                                chat_id,
                                f"⚠️ <b>Kunci API Ditolak oleh Google (HTTP {t_resp.status_code}):</b>\n"
                                f"<code>{html.escape(err_txt)}</code>\n\n"
                                "💡 <b>Sila pastikan:</b>\n"
                                "1. Anda telah menyalin keseluruhan kunci tanpa tertinggal sebarang aksara.\n"
                                "2. Kunci telah diaktifkan di https://aistudio.google.com/\n"
                                "3. Taip semula: <code>/gemini &lt;KUNCI_API_ANDA&gt;</code>",
                                parse_mode="HTML"
                            )
                        else:
                            send_message(chat_id, "❌ <b>Ralat Rangkaian:</b> Gagal menghubungi pelayan Google. Sila pastikan komputer anda bersambung ke internet.", parse_mode="HTML")
                        continue

                    # Jika tanpa parameter: Paparkan Dashboard Diagnosis Penuh
                    current_k, src_fn, kname = get_gemini_api_key_details()
                    status_k = f"<code>{current_k[:8]}...{current_k[-4:]}</code>" if current_k else "<i>Belum Ditetapkan</i>"
                    
                    # Semak status ujian sambungan jika ada kunci
                    google_status = "⚪ Belum Ditetapkan"
                    active_model_info = "gemini-3.8-flash / gemini-3.5-flash-lite (Enjin Vision Pantas)"
                    if current_k:
                        test_models_diag = AVAILABLE_GEMINI_MODELS
                        r_chk = None
                        working_m = ""
                        for tm in test_models_diag:
                            test_url = f"https://generativelanguage.googleapis.com/v1beta/models/{tm}:generateContent?key={current_k}"
                            test_headers = {
                                "x-goog-api-key": current_k,
                                "Content-Type": "application/json"
                            }
                            try:
                                r_chk = requests.post(test_url, headers=test_headers, json={"contents": [{"parts": [{"text": "PING"}]}]}, timeout=8)
                            except Exception:
                                try:
                                    import urllib3
                                    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
                                    r_chk = requests.post(test_url, headers=test_headers, json={"contents": [{"parts": [{"text": "PING"}]}]}, timeout=8, verify=False)
                                except Exception:
                                    pass
                            if r_chk and r_chk.status_code == 200:
                                working_m = tm
                                break

                        if r_chk and r_chk.status_code == 200:
                            google_status = f"🟢 <b>Aktif & Sah (HTTP 200 OK - Model: {working_m})</b>"
                            active_model_info = f"{working_m} (Aktif)"
                        elif r_chk:
                            google_status = f"🔴 <b>Ralat Google (HTTP {r_chk.status_code})</b>"
                        else:
                            google_status = "🟡 <b>Mod Luar Talian / Ralat Rangkaian</b>"

                    ocr_status = "🟢 Windows 10/11 WinRT Native OCR" if os.name == 'nt' else "⚪ Enjin Asas"
                    diag_lines = [
                        "🤖 <b>DASHBOARD STATUS AI VISION & OCR KANTOI</b>",
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                        f"🔑 <b>Status Kunci API:</b> {status_k}",
                        f"📂 <b>Sumber Dikesan:</b> <code>{src_fn}</code> (kunci: <code>{kname}</code>)",
                        f"🌐 <b>Sambungan Google AI:</b> {google_status}",
                        f"⚡ <b>Model Vision Utama:</b> <code>{active_model_info}</code>",
                        f"🖥️ <b>Enjin OCR Tempatan:</b> {ocr_status}",
                        "",
                        "💡 <b>Format Kunci yang Disokong:</b>",
                        "• <b>Kunci Baharu Google AI Studio:</b> <code>AQ....</code> (~53 aksara)",
                        "• <b>Kunci Standard (Legacy):</b> <code>AIzaSy...</code> (39 aksara)",
                        "",
                        "💡 <b>Cara Menetapkan / Mengemas Kini Kunci:</b>",
                        "Taip arahan berikut dalam chat ini:",
                        "<code>/gemini &lt;API_KEY_ANDA&gt;</code>",
                        "",
                        "<i>Kunci API percuma boleh didapati di https://aistudio.google.com/</i>"
                    ]
                    send_message(chat_id, "\n".join(diag_lines), parse_mode="HTML")
                    continue

                # Arahan Pendaftaran Admin: /admin <passcode>
                if text.startswith("/admin"):
                    parts = text.split()
                    if len(parts) > 1 and parts[1] == ADMIN_PASSCODE:
                        add_admin(chat_id)
                        send_message(chat_id, "🎉 *TAHNIAH! ANDA KINI PENTADBIR (ADMIN) KANTOI.*\n\nSemua laporan scammer baharu daripada orang ramai akan terus dihantar ke sini untuk semakan dan kelulusan satu-klik anda.")
                        print(f"[ADMIN] Admin didaftarkan: {chat_id} (@{username})")
                        continue
                    elif chat_id in get_admin_ids():
                        send_message(chat_id, "⭐ Anda sudah berdaftar sebagai Admin KANTOI.")
                        continue
                    else:
                        send_message(chat_id, "❌ Kod laluan salah. Format: `/admin <passcode>`")
                        continue

                # Batal proses
                if text in ["/batal", "batal", "/cancel"]:
                    user_sessions.pop(chat_id, None)
                    send_message(chat_id, "❌ Proses dibatalkan.")
                    continue

                # Arahan Panduan Agensi Penguatkuasaan
                if text.startswith(("/agensi", "/nsrc", "/skmm", "/penguatkuasa", "/polis")):
                    user_sessions.pop(chat_id, None)
                    sample_rep = {
                        "phone_norm": "01xxxxxxxx",
                        "suspect_name": "Scammer / Suspek",
                        "bank_account": "Akaun Bank Terbabit",
                        "victim_loss": "RM...",
                        "evidence_text": "Sila masukkan butiran kes atau gunakan semakan imej KANTOI."
                    }
                    menu_text, menu_kb = build_enforcement_menu("general", sample_rep)
                    send_message(chat_id, menu_text, reply_markup=menu_kb, parse_mode="HTML")
                    continue

                # Start / Bantuan / Menu Interaktif & Deep Link KANTOI
                if text.startswith(("/start", "start", "/help", "help", "/menu", "menu", "/panduan", "panduan", "bantuan")):
                    parts = text.split(maxsplit=1)
                    start_arg = parts[1].strip() if len(parts) > 1 else ""
                    user_sessions.pop(chat_id, None)

                    # a. Deep-Link Semakan daripada Aplikasi KANTOI (?start=semak_01xxxxxxxx)
                    if start_arg.startswith("semak_"):
                        target_num = start_arg.replace("semak_", "").strip()
                        if target_num:
                            api_call("sendChatAction", {"chat_id": chat_id, "action": "typing"})
                            norm_t = normalize_phone(target_num) or target_num
                            is_adm = chat_id in get_admin_ids()
                            dossier_html, dossier_kb, _ = build_unified_cyber_dossier(
                                phones=[norm_t],
                                suspect_name="Sasaran Aplikasi KANTOI",
                                tactic="Semakan Terus Aplikasi KANTOI",
                                source_badge="📲 Aplikasi KANTOI Android (Deep Link)",
                                is_admin=is_adm,
                                raw_query=target_num
                            )
                            send_message(chat_id, dossier_html, reply_markup=dossier_kb, parse_mode="HTML")
                            continue

                    # b. Deep-Link Laporan daripada Aplikasi KANTOI (?start=lapor_01xxxxxxxx)
                    if start_arg.startswith("lapor_"):
                        target_num = start_arg.replace("lapor_", "").strip()
                        norm_t = normalize_phone(target_num) or target_num
                        user_sessions[chat_id] = {
                            "step": "STEP_SUSPECT",
                            "user_id": chat_id,
                            "username": username,
                            "data": {
                                "phone_norm": norm_t,
                                "suspect_name": "",
                                "bank_account": ""
                            }
                        }
                        send_message(
                            chat_id,
                            f"✅ Nombor diterima dari Aplikasi KANTOI: `{norm_t}`\n\n"
                            "👤 *NAMA SUSPEK / TAKTIK PENIPUAN (Langkah 2/4)*\n"
                            "Sila nyatakan *Nama Suspek* atau *Taktik Scammer*:\n"
                            "_(Contoh: Menyamar Pegawai JPJ / Penipuan Kerja Sambilan / Pelaburan Palsu)_",
                            reply_markup=MAIN_REPLY_KEYBOARD
                        )
                        continue

                    # c. Paparan Menu Utama / Alu-aluan Standard KANTOI
                    is_admin = chat_id in get_admin_ids()
                    send_message(chat_id, format_welcome_message(is_admin, user_id=chat_id), reply_markup=get_help_menu_keyboard())
                    continue

                # Butang Pantas Papan Kekunci Bawah (Quick Keyboard Buttons)
                if text in ["📚 30 FAQ Mangsa Scam", "30 FAQ Mangsa Scam", "/faq", "faq", "/panduan_mangsa", "panduan mangsa", "soalan", "/soalan"]:
                    user_sessions.pop(chat_id, None)
                    f_text, f_kb = kantoi_faq_vault.build_faq_hub_view()
                    send_message(chat_id, f_text, reply_markup=f_kb, parse_mode="HTML")
                    continue

                if text == "🔍 Semak No / Akaun":
                    send_message(
                        chat_id, 
                        "🔍 *SEMAKAN NOMBOR TELEFON ATAU AKAUN BANK*\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        "Sila taip *nombor telefon* (contoh: `0179597645`) atau *nombor akaun bank* (contoh: `112028138548`) yang ingin disemak terus ke sini:",
                        reply_markup=MAIN_REPLY_KEYBOARD
                    )
                    continue

                if text == "🏛️ Semakan PDRM":
                    send_message(
                        chat_id, 
                        "🏛️ *SEMAKAN TERUS PORTAL PDRM CCID*\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        "Sila taip nombor sasaran dengan arahan `/pdrm <nombor>`\n"
                        "Contoh: `/pdrm 01121938112` atau `/pdrm 512802774281`",
                        reply_markup=MAIN_REPLY_KEYBOARD
                    )
                    continue

                if text == "🌐 Carian Media Sosial":
                    send_message(
                        chat_id,
                        "🌐 *CARIAN PERTIKAIAN MEDIA SOSIAL (GOOGLE CSE)*\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        "Sila taip nombor telefon atau akaun bank dengan arahan `/cse <nombor>`\n"
                        "Contoh: `/cse 0179597645` atau `/cse 112028138548`\n\n"
                        "💡 _Enjin Google CSE KANTOI menyaring perbincangan di Lowyat, CariGold, Facebook, Twitter/X dan TikTok secara automatik._",
                        reply_markup=MAIN_REPLY_KEYBOARD
                    )
                    continue

                if text == "📰 Buletin Berita Scam":
                    bulletin = format_scam_radar_bulletin(5)
                    send_message(chat_id, bulletin, reply_markup=MAIN_REPLY_KEYBOARD)
                    continue

                if text == "📢 Lapor Scammer":
                    user_sessions[chat_id] = {
                        "step": "STEP_PHONE",
                        "user_id": chat_id,
                        "username": username,
                        "data": {}
                    }
                    send_message(
                        chat_id,
                        "📢 *BORANG LAPORAN SCAMMER (Langkah 1/4)*\n\n"
                        "Sila masukkan *Nombor Telefon Scammer* yang ingin dilaporkan:\n"
                        "_(Contoh: `01139444255` atau `+60123456789`)_\n\n"
                        "💡 _Tip: Anda juga boleh 'main tulis saja' satu cerita penuh terus ke sini tanpa isi borang!_\n"
                        "_(Taip /batal untuk membatalkan)_",
                        reply_markup=MAIN_REPLY_KEYBOARD
                    )
                    continue

                if text == "ℹ️ Menu Bantuan & Panduan":
                    user_sessions.pop(chat_id, None)
                    is_admin = chat_id in get_admin_ids()
                    send_message(chat_id, format_welcome_message(is_admin, user_id=chat_id), reply_markup=get_help_menu_keyboard())
                    continue

                if text == "🔑 Kunci API (BYOK)":
                    user_sessions.pop(chat_id, None)
                    text_guide, markup = get_help_detail("help_mykey")
                    send_message(chat_id, text_guide, reply_markup=markup)
                    continue

                if text == "📸 Imbas Foto / QR":
                    user_sessions.pop(chat_id, None)
                    text_guide, markup = get_help_detail("help_vision")
                    send_message(chat_id, text_guide, reply_markup=markup)
                    continue

                # Mula Wizard /lapor berstruktur (jika pengguna mahu langkah demi langkah)
                if text == "/lapor" or text.lower() == "lapor":
                    user_sessions[chat_id] = {
                        "step": "STEP_PHONE",
                        "user_id": chat_id,
                        "username": username,
                        "data": {}
                    }
                    send_message(
                        chat_id,
                        "📢 *BORANG LAPORAN SCAMMER BAHARU (Langkah 1/4)*\n\n"
                        "Sila masukkan *Nombor Telefon Scammer* yang ingin dilaporkan:\n"
                        "_(Contoh: `01139444255` atau `+60123456789`)_\n\n"
                        "💡 _Tip: Anda juga boleh 'main tulis saja' satu cerita penuh terus ke sini tanpa isi borang!_\n"
                        "_(Taip /batal untuk membatalkan)_"
                    )
                    continue

                # Semak jika teks mengandungi senarai banyak nombor (Batch Report)
                batch_items = extract_batch_reports(text)
                if len(batch_items) >= 2 or (len(batch_items) == 1 and len(batch_items[0]['phones']) >= 2):
                    user_sessions.pop(chat_id, None)
                    batch_id = f"BAT-{int(time.time()) % 100000:05d}"
                    photo_file_id = photos[-1]["file_id"] if photos else None
                    
                    total_nums = sum(len(it['phones']) for it in batch_items)
                    rep_data = {
                        "batch_id": batch_id,
                        "user_id": chat_id,
                        "username": username,
                        "items": batch_items,
                        "photo_file_id": photo_file_id,
                        "status": "PENDING",
                        "created_at": int(time.time())
                    }
                    unconfirmed_reports[batch_id] = rep_data
                    save_pending_report(batch_id, rep_data)

                    summary_preview = []
                    for idx, it in enumerate(batch_items[:6], 1):
                        p_str = ", ".join(f"<code>{p}</code>" for p in it['phones'])
                        clean_desc = html.escape(it['desc'][:45])
                        summary_preview.append(f"{idx}. {p_str} — <i>{clean_desc}</i>")

                    preview_msg = (
                        f"🤖 <b>SAYA MENGESAN SENARAI {len(batch_items)} ENTRI ({total_nums} NOMBOR SCAMMER)!</b>\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        + "\n".join(summary_preview) +
                        ("\n<i>...dan lain-lain lagi</i>" if len(batch_items) > 6 else "") +
                        "\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                    )

                    is_admin_sender = chat_id in get_admin_ids()
                    if is_admin_sender:
                        preview_msg += "⭐ <b>Mod Admin:</b> Anda boleh meluluskan dan menyiarkan senarai ini terus ke @kantoiradar dengan satu klik:"
                        markup = {
                            "inline_keyboard": [
                                [
                                    {"text": f"⚡ LULUSKAN & SIAR ({total_nums} NOMBOR)", "callback_data": f"cfrmb_direct_{batch_id}"},
                                    {"text": "❌ BATAL", "callback_data": f"cncl_{batch_id}"}
                                ]
                            ]
                        }
                    else:
                        preview_msg += "Adakah anda ingin menghantar senarai pukal ini kepada Admin KANTOI untuk semakan dan kelulusan?"
                        markup = {
                            "inline_keyboard": [
                                [
                                    {"text": f"🚀 YA, HANTAR SEMUA ({total_nums} NOMBOR)", "callback_data": f"cfrmb_{batch_id}"},
                                    {"text": "❌ BATAL", "callback_data": f"cncl_{batch_id}"}
                                ]
                            ]
                        }
                    api_call("sendMessage", {
                        "chat_id": chat_id,
                        "text": preview_msg,
                        "parse_mode": "HTML",
                        "reply_markup": markup,
                        "disable_web_page_preview": True
                    })
                    continue

                # Semak jika pengguna sedang dalam sesi wizard /lapor
                session = user_sessions.get(chat_id)
                if session:
                    step = session.get("step")

                    if step == "STEP_PHONE":
                        norm = normalize_phone(text)
                        if not norm or len(norm) < 9:
                            send_message(chat_id, "⚠️ Format nombor tidak sah. Sila masukkan nombor telefon Malaysia yang sah (contoh: `01139444255`):")
                            continue
                        session["data"]["phone_norm"] = norm
                        session["step"] = "STEP_SUSPECT"
                        send_message(
                            chat_id,
                            f"✅ Nombor diterima: `{norm}`\n\n"
                            "👤 *NAMA SUSPEK / TAKTIK PENIPUAN (Langkah 2/4)*\n"
                            "Sila nyatakan *Nama Suspek* atau *Taktik Scammer*:\n"
                            "_(Contoh: Menyamar Pegawai JPJ / Penipuan Kerja Sambilan / Pelaburan Palsu)_"
                        )
                        continue

                    elif step == "STEP_SUSPECT":
                        session["data"]["suspect_name"] = text
                        session["step"] = "STEP_BANK"
                        send_message(
                            chat_id,
                            "🏦 *AKAUN BANK KELDAI (Langkah 3/4)*\n"
                            "Sila masukkan *Nama Bank & Nombor Akaun* yang diminta oleh suspek:\n"
                            "_(Contoh: Maybank 112028138548)_\n\n"
                            "_(Jika tiada atau tidak pasti, taip 'Tiada')_"
                        )
                        continue

                    elif step == "STEP_EVIDENCE_FOR_DRAFT":
                        tmp_id = session.get("tmp_id")
                        rep_data = unconfirmed_reports.get(tmp_id) or get_pending_reports().get(tmp_id)
                        if rep_data:
                            if text and text.lower() != "tiada":
                                rep_data["evidence_text"] = f"[Cerita Mangsa]: {text}\n\n{rep_data.get('evidence_text', '')}"
                            rep_id = f"REP-{int(time.time()) % 100000:05d}"
                            rep_data["report_id"] = rep_id
                            rep_data["created_at"] = int(time.time())
                            save_pending_report(rep_id, rep_data)
                            forward_report_to_admins(rep_id, rep_data)
                            del user_sessions[chat_id]
                            summary = (
                                f"📋 *RINGKASAN LAPORAN ANDA (Ref: `{rep_id}`)*\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"📱 Nombor: `{rep_data['phone_norm']}`\n"
                                f"👤 Suspek: {rep_data['suspect_name']}\n"
                                f"🏦 Akaun Bank: {rep_data['bank_account']}\n"
                                f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
                                f"✅ *Laporan anda telah berjaya dihantar ke Bilik Admin KANTOI!*\n"
                                f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @kantoiradar. Terima kasih!"
                            )
                            send_message(chat_id, summary)
                            continue

                    elif step == "STEP_BANK":
                        session["data"]["bank_account"] = text if text.lower() != "tiada" else "Tiada"
                        session["step"] = "STEP_EVIDENCE"
                        send_message(
                            chat_id,
                            "📸 *BUKTI / TANGKAP LAYAR (Langkah 4/4)*\n\n"
                            "Sila *hantar gambar screenshot* chat/resit transaksi ATAU taip pautan bukti ringkas.\n\n"
                            "_(Jika tiada gambar, taip 'Tiada' untuk teruskan penghantaran)_"
                        )
                        continue

                    elif step == "STEP_EVIDENCE":
                        photo_file_id = photos[-1]["file_id"] if photos else None
                        evidence_text = text if text.lower() != "tiada" else "Tiada bukti tambahan disertakan."

                        rep_id = f"REP-{int(time.time()) % 100000:05d}"
                        rep_data = {
                            "report_id": rep_id,
                            "user_id": chat_id,
                            "username": username,
                            "phone_norm": session["data"]["phone_norm"],
                            "suspect_name": session["data"]["suspect_name"],
                            "bank_account": session["data"]["bank_account"],
                            "evidence_text": evidence_text,
                            "photo_file_id": photo_file_id,
                            "status": "PENDING",
                            "created_at": int(time.time())
                        }

                        save_pending_report(rep_id, rep_data)
                        del user_sessions[chat_id]

                        summary = (
                            f"📋 *RINGKASAN LAPORAN ANDA (Ref: `{rep_id}`)*\n"
                            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            f"📱 Nombor: `{rep_data['phone_norm']}`\n"
                            f"👤 Suspek: {rep_data['suspect_name']}\n"
                            f"🏦 Akaun Bank: {rep_data['bank_account']}\n"
                            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
                            f"✅ *Laporan anda telah berjaya dihantar ke Bilik Admin KANTOI!*\n"
                            f"Laporan akan disemak dan disahkan oleh admin sebelum disiarkan ke saluran @kantoiradar. Terima kasih!"
                        )
                        send_message(chat_id, summary)
                        forward_report_to_admins(rep_id, rep_data)
                        print(f"[WIZARD] Laporan {rep_id} dihantar oleh @{username}.")
                        continue

                # ------------------------------------------------------------------
                # D. PENGEKSTRAKAN PINTAR NLP: "MAIN TULIS SAJA"
                # ------------------------------------------------------------------
                if is_likely_report(text):
                    extracted = extract_freeform_report(text)
                    if extracted and extracted.get("phone_norm"):
                        tmp_id = f"tmp_{int(time.time()*1000) % 1000000}"
                        photo_file_id = photos[-1]["file_id"] if photos else None
                        
                        rep_data = {
                            "user_id": chat_id,
                            "username": username,
                            "phone_norm": extracted["phone_norm"],
                            "all_phones": extracted.get("all_phones") or [extracted["phone_norm"]],
                            "suspect_name": extracted["suspect_name"],
                            "bank_account": extracted["bank_account"],
                            "evidence_text": extracted["evidence_text"],
                            "photo_file_id": photo_file_id,
                            "status": "PENDING"
                        }
                        unconfirmed_reports[tmp_id] = rep_data
                        save_pending_report(tmp_id, rep_data)

                        preview_msg = (
                            "🤖 *SAYA TELAH MENYUSUN LAPORAN ANDA SECARA AUTOMATIK:*\n"
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            f"📱 *Nombor Scammer:* `{extracted['phone_norm']}`\n"
                            f"🏷️ *Taktik / Suspek:* {extracted['suspect_name']}\n"
                            f"🏦 *Akaun Bank:* {extracted['bank_account']}\n"
                            f"📝 *Keterangan Kes:* _{extracted['evidence_text'][:200]}..._\n"
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            "Adakah maklumat di atas tepat untuk dihantar kepada Admin KANTOI?"
                        )

                        is_admin_sender = chat_id in get_admin_ids()
                        if is_admin_sender:
                            markup = {
                                "inline_keyboard": [
                                    [
                                        {"text": "⚡ LULUSKAN & SIAR TERUS", "callback_data": f"cfrm_direct_{tmp_id}"},
                                        {"text": "❌ BATAL", "callback_data": f"cncl_{tmp_id}"}
                                    ]
                                ]
                            }
                        else:
                            markup = {
                                "inline_keyboard": [
                                    [
                                        {"text": "🚀 YA, HANTAR KEPADA ADMIN", "callback_data": f"cfrm_{tmp_id}"},
                                        {"text": "❌ BATAL", "callback_data": f"cncl_{tmp_id}"}
                                    ]
                                ]
                            }

                        send_message(chat_id, preview_msg, reply_markup=markup)
                        print(f"[FREEFORM NLP] Pengesanan pintar berjaya untuk @{username}: {extracted['phone_norm']}")
                        continue

                # ------------------------------------------------------------------
                # E. SEMAKAN PANGKALAN DATA BIASA (DOSSIER RISIKAN 1-PAPARAN)
                # ------------------------------------------------------------------
                clean_num_check = re.sub(r'[^\d]', '', text)
                if len(clean_num_check) >= 8:
                    hud = ProgressHUD(chat_id, reply_to_msg_id=msg_id)
                    hud.start(25, "🔍 Menyelaras risikan PDRM CCID & Pangkalan Data KANTOI...", f"Sasaran: {text[:25]}")
                    hud.update(70, "🌐 Mengimbas forum Lowyat, CariGold & FB (Google CSE)...", "Deep Intel")
                    dossier_html, dossier_kb, is_threat = build_unified_cyber_dossier(
                        raw_query=text,
                        source_badge="💬 Carian Teks Terus",
                        is_admin=(chat_id in get_admin_ids())
                    )
                    hud.update(100, "✨ Analisis Selesai! Menyusun Kad Risikan...", "Sedia")
                    hud.complete_with_message(dossier_html, reply_markup=dossier_kb, parse_mode="HTML")
                else:
                    record, norm_phone = query_threat(text)
                    if record:
                        reply = format_threat_response(record, text)
                        print(f"[HIT] Rekod scammer dijumpai untuk: {text} -> {record[1]}")
                        send_message(chat_id, reply, reply_markup=get_threat_check_keyboard(text, norm_phone, has_threat=True))
                    else:
                        reply = format_safe_response(text, norm_phone)
                        print(f"[CLEAN] Tiada rekod untuk: {text}")
                        send_message(chat_id, reply, reply_markup=get_threat_check_keyboard(text, norm_phone, has_threat=False))

        except requests.exceptions.RequestException:
            time.sleep(3)
        except Exception as e:
            print(f"[ERR] Ralat: {e}")
            time.sleep(2)

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "test":
        test_no = sys.argv[2] if len(sys.argv) > 2 else "0179597645"
        rec, norm = query_threat(test_no)
        if rec:
            print(format_threat_response(rec, test_no))
        else:
            print(format_safe_response(test_no, norm))
    elif len(sys.argv) > 1 and sys.argv[1] == "nlp_test":
        sample = sys.argv[2] if len(sys.argv) > 2 else "011-39444255 scammer call kata ada saman mahkamah kl, suruh transfer rm500 ke cimb 7012345678 nama mohd ali"
        print(extract_freeform_report(sample))
    else:
        run_bot()
