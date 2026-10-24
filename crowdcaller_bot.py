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
CHANNEL_ID = "@crowdcaller"
ADMIN_PASSCODE = "kantoi2026"

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
    'gxbank': ['gxbank.my'],
    'bigpay': ['bigpayme.com']
}

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
        ("config.json", os.path.join(BASE_DIR, "config.json")),
        ("settings.json", os.path.join(BASE_DIR, "settings.json")),
        ("secrets.json", os.path.join(BASE_DIR, "secrets.json")),
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
    
    # 1. Ekstrak Nombor Telefon
    phone_match = re.search(r'(?:\+?60\s?1[0-9][\s\-\.]?\d{3,4}[\s\-\.]?\d{4}|01[0-9][\s\-\.]?\d{3,4}[\s\-\.]?\d{4})', clean_text)
    if not phone_match:
        # Cuba cari rentetan nombor 9-11 digit yang bermula dengan 01 atau 601
        digits_match = re.search(r'\b(?:601|01)\d{7,9}\b', re.sub(r'[\s\-]', '', clean_text))
        if digits_match:
            phone_norm = normalize_phone(digits_match.group(0))
        else:
            return None
    else:
        phone_norm = normalize_phone(phone_match.group(0))
        
    if not phone_norm or len(phone_norm) < 9:
        return None

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
    url_m = re.search(r'(https?://[^\s<>"]+|www\.[^\s<>"]+)', raw_data, re.IGNORECASE)
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
        u = urllib.parse.urlparse(url_str)
        domain = u.netloc.lower()
        bank_name = 'PAUTAN_WEB'
        if 'tngdigital' in domain: bank_name = 'TNG DIGITAL'
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

def analyze_image_with_gemini(image_path, api_key=None):
    """Menghantar imej ke Google Gemini Vision API dengan sokongan perlindungan keselamatan dan kebal SSL Windows 7"""
    global LAST_GEMINI_DIAGNOSTIC
    if not api_key:
        api_key = get_gemini_api_key()
    api_key = (api_key or "").strip(" \t\r\n\'\"")
    if not api_key or api_key == "YOUR_GEMINI_API_KEY_HERE":
        LAST_GEMINI_DIAGNOSTIC = {
            "status": "NOT_CONFIGURED",
            "error": "Kunci API Gemini belum ditetapkan",
            "model": "",
            "timestamp": time.time()
        }
        return None
        
    mime_type = get_image_mime_type(image_path)
    try:
        with open(image_path, "rb") as f:
            b64_data = base64.b64encode(f.read()).decode("utf-8")
    except Exception as e:
        print(f"[GEMINI FILE READ ERR] {e}")
        return None

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

    models_to_try = [
        "gemini-2.0-flash",       # TERPANTAS & PALING PINTAR UNTUK VISION (Google Gemini 2.0)
        "gemini-1.5-flash",       # Sandaran stabil pantas (Google Gemini 1.5)
        "gemini-1.5-flash-8b",    # Sandaran latensi ultra-rendah
        "gemini-2.0-flash-exp",   # Model eksperimental 2.0
        "gemini-1.5-pro"          # Sandaran penalaran mendalam
    ]

    for model_name in models_to_try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
        headers = {
            "x-goog-api-key": api_key,
            "Content-Type": "application/json"
        }
        resp = None
        try:
            resp = requests.post(url, headers=headers, json=payload, timeout=25)
        except Exception as net_or_ssl_err:
            # Kebal SSL untuk Windows 7 SP1 & Python 3.8 embeddable
            print(f"[GEMINI SSL/NET WARN on {model_name}] {net_or_ssl_err}. Mencuba mod kebal SSL (verify=False)...")
            try:
                import urllib3
                urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
                resp = requests.post(url, headers=headers, json=payload, timeout=25, verify=False)
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
            if resp.status_code in [404, 400]:
                continue
            else:
                break

    return None

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

def extract_hybrid_image_intel(image_path, gemini_api_key=None):
    """
    Menyelaraskan gabungan QR DuitNow + AI Vision (Gemini) + OCR Tempatan
    """
    # 1. Imbas Kod QR DuitNow / EMVCo / URL / Teks
    qr_data = decode_duitnow_qr(image_path)
    
    # 2. Imbas teks melalui AI Vision (jika ada kunci) atau Windows Native OCR
    active_key = (gemini_api_key or "").strip() or get_gemini_api_key()
    gemini_data = None
    if active_key:
        gemini_data = analyze_image_with_gemini(image_path, active_key)
        
    ocr_text = ""
    if not gemini_data:
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
        "gemini_error": LAST_GEMINI_DIAGNOSTIC.get("error", "")
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
    norm_phone = normalize_phone(query_text)
    clean_digits = re.sub(r"[^\d]", "", query_text)
    
    if not os.path.exists(DB_PATH):
        return None, "DATABASE_MISSING"

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    match = None

    if norm_phone:
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

def insert_approved_threat(phone_norm, suspect_name, category, bank_accounts, source):
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
                phone_norm,
                bank_accounts or "N/A",
                "N/A",
                "Laporan Disahkan Komuniti",
                "Telegram @crowdcaller",
                category or "COMMUNITY_VERIFIED_SCAMMER",
                source or "Sumbangan Komuniti CrowdCaller (Disahkan Admin)"
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
def inspect_url_phishing(url):
    """
    Menganalisis pautan web untuk mengesan pancingan data (Phishing) perbankan & bantuan palsu
    """
    final_url = url
    try:
        parsed_init = urllib.parse.urlparse(url)
        host_init = (parsed_init.hostname or "").lower()
        shorteners = ['bit.ly', 'tinyurl.com', 't.co', 'is.gd', 'cutt.ly', 'rb.gy', 'wa.me', 't.me']
        if any(host_init == s or host_init.endswith('.' + s) for s in shorteners):
            r = requests.head(url, allow_redirects=True, timeout=5, headers={"User-Agent": "Mozilla/5.0"})
            final_url = r.url
    except Exception:
        pass

    parsed = urllib.parse.urlparse(final_url)
    hostname = (parsed.hostname or '').lower()
    path = parsed.path.lower()
    
    reasons = []
    verdict = "BERSIH / TIADA INDIKATOR PHISHING"
    severity = "INFO"
    target_brand = ""

    # Semak penyamaran jenama bank rasmi
    for brand, legits in OFFICIAL_DOMAINS.items():
        if brand in hostname:
            if not any(hostname == leg or hostname.endswith('.' + leg) for leg in legits):
                verdict = f"BAHAYA - LAMAN PERBANKAN PALSU (PHISHING {brand.upper()})!"
                severity = "CRITICAL"
                target_brand = brand.upper()
                reasons.append(f"Menyamar sebagai perbankan rasmi {brand.upper()} menggunakan domain luar: `{hostname}`")
                break

    # Semak umpan bantuan kerajaan / subsidi
    if severity != "CRITICAL":
        for kw in GOV_KEYWORDS:
            if kw in hostname or kw in path:
                if not (hostname.endswith('.gov.my') or hostname.endswith('.my')):
                    verdict = "RISIKO TINGGI - PANCINGAN BANTUAN KERAJAAN / APLIKASI PALSU"
                    severity = "HIGH"
                    target_brand = f"Bantuan Kerajaan ({kw.upper()})"
                    reasons.append(f"Menggunakan kata kunci bantuan (`{kw}`) tanpa pengesahan domain rasmi `.gov.my`")
                    break

    # Semak akhiran domain berisiko tinggi
    for tld in SUSPICIOUS_TLDS:
        if hostname.endswith(tld):
            reasons.append(f"Menggunakan akhiran domain risiko tinggi sindiket (`{tld}`)")
            if severity == "INFO":
                severity = "MEDIUM"
                verdict = "MENCURIGAKAN - DOMAIN RISIKO TINGGI"

    # Semak muat turun APK terus
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
        "reasons": reasons
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
    1. Google Custom Search JSON API (jika ada kunci API Google)
    2. Pangkalan data komuniti tempatan KANTOI (Lowyat, CariGold, KenaScam)
    """
    clean_target = re.sub(r'[^\d]', '', str(query_target))
    if not clean_target or len(clean_target) < 8:
        clean_target = str(query_target).strip()
        
    snippets = []
    google_key = get_gemini_api_key()
    
    # 1. Cuba Google Custom Search JSON API jika ada kunci Google
    if google_key:
        try:
            api_url = f"https://www.googleapis.com/customsearch/v1?key={google_key}&cx={GOOGLE_CSE_CX}&q={urllib.parse.quote(clean_target)}&num={max_results}"
            r = requests.get(api_url, timeout=5)
            if r.status_code == 200:
                data = r.json()
                for item in data.get("items", [])[:max_results]:
                    title = item.get("title", "").strip()
                    snippet = item.get("snippet", "").strip().replace("\n", " ")
                    link = item.get("link", "")
                    domain = urllib.parse.urlparse(link).netloc.replace("www.", "")
                    snippets.append({
                        "source": domain.upper(),
                        "title": title[:55],
                        "snippet": snippet[:95],
                        "link": link
                    })
        except Exception:
            pass
            
    # 2. Semak rekod forum dalam Pangkalan Data Komuniti KANTOI (Lowyat/CariGold/KenaScam)
    if not snippets:
        try:
            conn = sqlite3.connect(DB_PATH)
            cur = conn.cursor()
            cur.execute("""
                SELECT suspect_name, source, evidence_links, risk_tag 
                FROM threat_numbers 
                WHERE phone_norm = ? OR bank_accounts LIKE ?
                LIMIT ?
            """, (clean_target, f"%{clean_target}%", max_results))
            rows = cur.fetchall()
            conn.close()
            for r in rows:
                s_name, src, ev_links, r_tag = r
                if ev_links and ev_links != "N/A":
                    for lk in ev_links.split(" | ")[:max_results]:
                        domain = urllib.parse.urlparse(lk).netloc.replace("www.", "") or src
                        snippets.append({
                            "source": domain.upper(),
                            "title": f"Aduan: {s_name}" if s_name else f"Kes {src}",
                            "snippet": f"Aduan dilaporkan di {src} ({r_tag})",
                            "link": lk
                        })
                elif src:
                    snippets.append({
                        "source": src.upper(),
                        "title": f"Aduan: {s_name or clean_target}",
                        "snippet": f"Rekod pertikaian dalam pangkalan komuniti KANTOI ({src})",
                        "link": ""
                    })
        except Exception:
            pass

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

def get_threat_check_keyboard(query_input, norm_phone=None, has_threat=False):
    target = norm_phone if norm_phone else query_input
    cse_url = generate_google_cse_url(target)
    kb = [
        [
            {"text": "🌐 Semak Media Sosial (Google CSE)", "url": cse_url}
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

def format_welcome_message(is_admin=False):
    admin_tag = "\n⭐ *Status Anda:* Pentadbir (Admin KANTOI)" if is_admin else ""
    return (
        "🤖 *SELAMAT DATANG KE BOT KANTOI ANTI-SCAM!* 🇲🇾\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "Pertahanan siber komuniti anda terhadap sindiket scammer, keldai akaun, dan pancingan data.\n\n"
        "💡 *PILIHAN PANTAS:* Tekan mana-mana butang di bawah untuk membuka panduan terperinci bagi setiap fungsi bot:\n\n"
        "• 🔍 *Semakan No/Bank:* Semak nombor & kesan sindiket\n"
        "• 🏛️ *PDRM CCID:* Semakan aduan polis rasmi secara live\n"
        "• 🌐 *Media Sosial (CSE):* Carian aduan di Lowyat, CariGold & FB\n"
        "• 📸 *QR DuitNow & Resit:* Ekstrak data terus dari gambar\n"
        "• 🔗 *Phishing URL:* Kesan laman bank & STR palsu\n"
        "• 📦 *Malware APK:* Sandbox trojan pencuri SMS TAC\n"
        "• 📢 *Lapor Scammer:* Hantar aduan teks atau gambar\n"
        "• 📰 *Buletin Berita:* Laporan kes jenayah siber terkini\n"
        "• 🛡️ *Penjaga Group:* Lindungi group Telegram anda\n"
        f"{admin_tag}\n"
        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        "ℹ️ _Ketik butang menu di bawah untuk panduan cara guna:_"
    )

# ==============================================================================
# MENU BANTUAN & PANDUAN INTERAKTIF
# ==============================================================================
MAIN_REPLY_KEYBOARD = {
    "keyboard": [
        [{"text": "🔍 Semak No / Akaun"}, {"text": "🏛️ Semakan PDRM"}],
        [{"text": "🌐 Carian Media Sosial"}, {"text": "📰 Buletin Berita Scam"}],
        [{"text": "📢 Lapor Scammer"}, {"text": "ℹ️ Menu Bantuan & Panduan"}]
    ],
    "resize_keyboard": True,
    "is_persistent": True
}

def get_help_menu_keyboard():
    return {
        "inline_keyboard": [
            [
                {"text": "🔍 Semakan No / Bank", "callback_data": "help_check"},
                {"text": "🏛️ Semakan PDRM CCID", "callback_data": "help_pdrm"}
            ],
            [
                {"text": "🌐 Carian Medsos (Google CSE)", "callback_data": "help_cse"},
                {"text": "📸 Imbas QR & Resit", "callback_data": "help_vision"}
            ],
            [
                {"text": "🔗 Semak Pautan (URL)", "callback_data": "help_url"},
                {"text": "📦 Sandbox APK Malware", "callback_data": "help_apk"}
            ],
            [
                {"text": "📢 Cara Lapor Scammer", "callback_data": "help_report"},
                {"text": "📰 Buletin Berita Scam", "callback_data": "help_bulletin"}
            ],
            [
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
    
    if topic == "help_check":
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
            "Enjin Google CSE (Custom Search Engine ID: `53a0041f2f24f4e3b`) telah diprogramkan khusus untuk menyaring rekod penipuan dan pertikaian transaksi di platform utama Malaysia:\n\n"
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
                [{"text": "🌐 Uji Carian Google CSE", "url": generate_google_cse_url("0179597645")}],
                [{"text": "« Kembali ke Menu Utama", "callback_data": "help_menu"}]
            ]
        }
        return text, cse_kb

    elif topic == "help_vision":
        text = (
            "📸 *PANDUAN IMBASAN FOTO & KOD QR DUITNOW*\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "Malas menaip? Anda boleh hantar gambar terus kepada bot ini!\n\n"
            "💡 *Apa Yang Boleh Dihantar:*\n"
            "1. *Kod QR DuitNow:*\n"
            "   Bot membaca matrik QR binary secara matematik (0.001 saat) untuk membongkar Nama Pemegang Akaun, Bank, dan No Akaun sebenar di sebalik QR tersebut!\n\n"
            "2. *Tangkap Layar (Screenshot) WhatsApp / Telegram:*\n"
            "   Enjin OCR mengekstrak nombor telefon suspek, akaun bank, dan cerita scammer secara automatik.\n\n"
            "3. *Slip / Resit Transaksi Bank:*\n"
            "   Mengekstrak maklumat akaun keldai untuk semakan segera."
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
            "• Bot juga menyiarkan buletin automatik setiap pagi di saluran rasmi @crowdcaller."
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
        {"command": "help", "description": "Pusat Bantuan & Panduan Interaktif"},
        {"command": "semak", "description": "Semak no telefon atau akaun bank"},
        {"command": "pdrm", "description": "Semakan terus portal rasmi PDRM CCID"},
        {"command": "cse", "description": "Carian media sosial & forum (Google CSE)"},
        {"command": "url", "description": "Semak pautan phishing & bank palsu"},
        {"command": "buletin", "description": "Berita kes penipuan terkini di Malaysia"},
        {"command": "lapor", "description": "Borang aduan scammer langkah demi langkah"},
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

    admin_card = (
        f"🚨 <b>LAPORAN SCAMMER BAHARU DITERIMA (ID: <code>{rep_id}</code>)</b>\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"👤 <b>Pengirim:</b> @{safe_user} (ID: <code>{safe_uid}</code>)\n\n"
        f"📱 <b>Nombor Scammer:</b> <code>{safe_phone}</code>\n"
        f"🏷️ <b>Taktik / Suspek:</b> {safe_suspect}\n"
        f"🏦 <b>Akaun Bank:</b> {safe_bank}\n"
        f"📝 <b>Keterangan Kes:</b> {safe_ev}\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"Sila semak maklumat di atas sebelum meluluskan siaran ke @crowdcaller:"
    )

    markup = {
        "inline_keyboard": [
            [
                {"text": "✅ LULUSKAN & SIAR", "callback_data": f"app_{rep_id}"},
                {"text": "❌ TOLAK", "callback_data": f"rej_{rep_id}"}
            ]
        ]
    }

    photo_id = rep_data.get("photo_file_id")
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

        # 1. Pos ke saluran @crowdcaller dengan format HTML yang selamat dari ralat aksara khas
        safe_suspect = html.escape(str(suspect_name or 'N/A'))
        safe_bank = html.escape(str(bank_account or 'Tiada'))
        safe_rep_id = html.escape(str(rep_id))

        channel_post = (
            "🚨 <b>AMARAN SCAMMER DISAHKAN (SUMBANGAN KOMUNITI)</b>\n"
            "==================================================\n"
            f"🚨 <b>NOMBOR TELEFON</b> : <code>{phone_norm}</code>\n"
            f"👤 <b>NAMA / TAKTIK</b>  : {safe_suspect}\n"
            "⚠️ <b>KLASIFIKASI</b>    : LAPORAN_KOMUNITI_DISAHKAN\n"
        )
        if safe_bank and safe_bank != "Tiada":
            channel_post += f"🏦 <b>AKAUN BANK</b>     : {safe_bank}\n"
            
        channel_post += (
            f"📚 <b>SUMBER SITASI</b>  : Sumbangan Disahkan Admin (Ref: <code>{safe_rep_id}</code>)\n"
            "--------------------------------------------------\n"
            "ℹ️ <i>Laporan disahkan oleh admin komuniti @crowdcaller.</i>\n"
            "📲 <i>Buka Tab Pertahanan KANTOI untuk perlindungan masa nyata.</i>\n"
            "💬 <i>Bantu komuniti: Lapor sebarang nombor/akaun bank scammer (teks/gambar) ke @crowdcallerbot.</i>\n"
            "🔗 <i>Semak kod sumber projek di: https://github.com/kand4/kantoi</i>\n\n"
            "#KANTOI_UPDATE\n"
            f"{phone_norm} | {safe_suspect} | LAPORAN_KOMUNITI_DISAHKAN | {safe_bank or 'Tiada'}"
        )

        photo_id = rep.get("photo_file_id")
        if photo_id:
            api_call("sendPhoto", {
                "chat_id": CHANNEL_ID,
                "photo": photo_id,
                "caption": channel_post,
                "parse_mode": "HTML"
            })
        else:
            api_call("sendMessage", {
                "chat_id": CHANNEL_ID,
                "text": channel_post,
                "parse_mode": "HTML",
                "disable_web_page_preview": True
            })

        # 2. Masukkan ke dalam SQLite KANTOI
        insert_approved_threat(
            phone_norm=phone_norm,
            suspect_name=suspect_name,
            category="LAPORAN_KOMUNITI_DISAHKAN",
            bank_accounts=bank_account,
            source=f"Laporan Komuniti CrowdCaller (Ref: {rep_id})"
        )

        # 3. Notifikasi kepada pengirim laporan
        if user_chat_id:
            user_msg = (
                f"🎉 <b>TERIMA KASIH! LAPORAN ANDA TELAH DILULUSKAN!</b>\n\n"
                f"Nombor: <code>{phone_norm}</code>\n"
                f"Status: <b>Telah disahkan oleh Admin KANTOI</b>\n\n"
                f"📢 Maklumat ini telah disiarkan ke saluran @crowdcaller dan kini dimasukkan ke dalam enjin risikan KANTOI untuk melindungi orang ramai."
            )
            api_call("sendMessage", {
                "chat_id": user_chat_id,
                "text": user_msg,
                "parse_mode": "HTML"
            })

        edit_message(admin_chat_id, message_id, f"✅ *DILULUSKAN & DISIARKAN KE @CROWDCALLER*\nRef ID: `{rep_id}`\nOleh Admin: @{admin_username}")
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

        # Pos ke saluran @crowdcaller
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
                "text": f"🎉 <b>TAHNIAH! SEMUA {inserted_count} NOMBOR SCAMMER TELAH DILULUSKAN!</b>\n\nMaklumat telah dimasukkan ke pangkalan data KANTOI dan disiarkan ke saluran @crowdcaller.",
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

                            edit_message(
                                cb_chat_id, 
                                cb_msg_id, 
                                f"✅ *LAPORAN ANDA TELAH DIHANTAR KE BILIK ADMIN KANTOI!*\n"
                                f"Ref ID: `{rep_id}`\n\n"
                                f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @crowdcaller. Terima kasih atas sumbangan anda!"
                            )
                            forward_report_to_admins(rep_id, rep_data)
                            answer_callback(cb_id, "Laporan berjaya dihantar ke Admin!", alert=False)
                        else:
                            answer_callback(cb_id, "Sesi laporan telah tamat tempoh.", alert=True)
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
                                f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @crowdcaller. Terima kasih atas sumbangan anda!"
                            )
                            forward_batch_report_to_admins(batch_id, rep_data)
                            answer_callback(cb_id, "Laporan pukal berjaya dihantar ke Admin!", alert=False)
                        else:
                            answer_callback(cb_id, "Sesi laporan telah tamat tempoh.", alert=True)
                        continue

                    # 1d. Pusat Bantuan & Panduan Interaktif Pengguna
                    if cb_data == "help_menu":
                        is_admin = cb_chat_id in get_admin_ids()
                        edit_message(cb_chat_id, cb_msg_id, format_welcome_message(is_admin), get_help_menu_keyboard())
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
                            cse_url = generate_google_cse_url(target)
                            reply = (
                                "🌐 <b>CARIAN PERTIKAIAN MEDIA SOSIAL (GOOGLE CSE)</b>\n"
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                f"🔍 <b>Sasaran Carian:</b> <code>{html.escape(target)}</code>\n"
                                "📚 <b>Sumber Liputan:</b> Forum Lowyat, CariGold, Facebook, Twitter/X, Telegram, TikTok & Blogspot.\n"
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                                "Ketik butang di bawah untuk membuka carian pertikaian komuniti terus di Telegram:"
                            )
                            markup = {
                                "inline_keyboard": [
                                    [{"text": "🌐 Buka Google CSE KANTOI", "url": cse_url}],
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
                def process_image_intel_message(t_chat_id, t_username, t_file_id, t_caption, is_doc=False, doc_filename=""):
                    # 1. Semak jika pengguna dalam wizard /lapor bertingkat langkah 4
                    session = user_sessions.get(t_chat_id)
                    if session and session.get("step") == "STEP_EVIDENCE":
                        evidence_text = t_caption if t_caption and t_caption.lower() != "tiada" else "Bukti bergambar dilampirkan."
                        rep_id = f"REP-{int(time.time()) % 100000:05d}"
                        rep_data = {
                            "report_id": rep_id,
                            "user_id": t_chat_id,
                            "username": t_username,
                            "phone_norm": session["data"]["phone_norm"],
                            "suspect_name": session["data"]["suspect_name"],
                            "bank_account": session["data"]["bank_account"],
                            "evidence_text": evidence_text,
                            "photo_file_id": t_file_id,
                            "status": "PENDING",
                            "created_at": int(time.time())
                        }
                        save_pending_report(rep_id, rep_data)
                        del user_sessions[t_chat_id]
                        summary = (
                            f"📋 *RINGKASAN LAPORAN ANDA (Ref: `{rep_id}`)*\n"
                            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            f"📱 Nombor: `{rep_data['phone_norm']}`\n"
                            f"👤 Suspek: {rep_data['suspect_name']}\n"
                            f"🏦 Akaun Bank: {rep_data['bank_account']}\n"
                            f"📸 Gambar Bukti: Dilampirkan\n"
                            f"━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
                            f"✅ *Laporan anda telah berjaya dihantar ke Bilik Admin KANTOI!*\n"
                            f"Laporan akan disemak dan disahkan sebelum disiarkan ke saluran @crowdcaller. Terima kasih!"
                        )
                        send_message(t_chat_id, summary)
                        forward_report_to_admins(rep_id, rep_data)
                        return

                    # 2. Semak jika pengguna melampirkan foto pada draf yang sedia ada
                    found_key = None
                    for k, v in reversed(list(unconfirmed_reports.items())):
                        if v.get("user_id") == t_chat_id and not v.get("photo_file_id"):
                            found_key = k
                            break
                    if not found_key:
                        for k, v in reversed(list(get_pending_reports().items())):
                            if v.get("user_id") == t_chat_id and not v.get("photo_file_id") and v.get("status") in ["DRAFT", "PENDING"]:
                                found_key = k
                                break
                    if found_key:
                        if found_key in unconfirmed_reports:
                            unconfirmed_reports[found_key]["photo_file_id"] = t_file_id
                        reports = get_pending_reports()
                        if found_key in reports:
                            reports[found_key]["photo_file_id"] = t_file_id
                            save_json_file(REPORTS_DATA_PATH, reports)
                        send_message(t_chat_id, "📸 *Gambar bukti berjaya dilampirkan pada draf laporan anda!*")
                        return

                    # 3. ENJIN HIBRID: Analisis Imej Baharu (Robust QR Decoder + AI Vision / Native OCR)
                    api_call("sendChatAction", {"chat_id": t_chat_id, "action": "typing"})
                    tmp_img = download_telegram_photo(t_file_id)
                    if not tmp_img:
                        send_message(t_chat_id, "❌ Gagal memuat turun gambar dari pelayan Telegram. Sila cuba lagi.")
                        return

                    try:
                        intel = extract_hybrid_image_intel(tmp_img, GEMINI_API_KEY)
                        if intel.get("has_intel"):
                            qr_data = intel.get("qr_data")
                            target_phone = intel.get("phone_norm") or ""
                            target_acc = intel.get("account_number") or ""
                            target_name = intel.get("suspect_name") or "Tidak Dinyatakan"
                            target_bank = intel.get("bank_name") or "N/A"
                            target_url = qr_data.get("url") if qr_data else ""

                            # A. Jika kod QR mengandungi pautan URL / Pembayaran Digital / Malware Dropper
                            if target_url:
                                res_url = inspect_url_phishing(target_url)
                                final_target = res_url.get("final_url", target_url)
                                
                                # Semak jika pautan mengarah kepada muat turun fail APK (Malware Dropper)
                                is_apk_target = (
                                    final_target.lower().endswith('.apk') or 
                                    '.apk?' in final_target.lower() or 
                                    '/apk/' in final_target.lower() or
                                    'download' in final_target.lower()
                                )

                                tmp_apk = None
                                apk_rep = None
                                if is_apk_target:
                                    api_call("sendChatAction", {"chat_id": t_chat_id, "action": "upload_document"})
                                    tmp_apk = download_url_to_temp_apk(final_target)
                                    if tmp_apk:
                                        try:
                                            fn = os.path.basename(urllib.parse.urlparse(final_target).path) or "aplikasi_kod_qr.apk"
                                            apk_rep = inspect_apk_malware(tmp_apk, fn)
                                        finally:
                                            if os.path.exists(tmp_apk):
                                                try:
                                                    os.unlink(tmp_apk)
                                                except Exception:
                                                    pass

                                if apk_rep and "error" not in apk_rep:
                                    # Laporan Sandbox Quishing-to-Malware Lengkap
                                    badge = "🚨" if apk_rep["severity"] == "CRITICAL" else ("⚠️" if apk_rep["severity"] in ["HIGH", "MEDIUM"] else "✅")
                                    lines = [
                                        f"{badge} <b>PERISAI KANTOI: ANCAMAN QUISHING & MALWARE APK DIKESAN DARI KOD QR!</b>",
                                        "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                        f"🔗 <b>Pautan QR Dikesan:</b> <code>{html.escape(target_url)}</code>",
                                        f"🌐 <b>Pelayan Muat Turun:</b> <code>{html.escape(res_url['domain'])}</code>",
                                        f"📁 <b>Fail APK:</b> <code>{html.escape(apk_rep['filename'])}</code> ({apk_rep['size_mb']} MB)",
                                        f"🔑 <b>Hash SHA256:</b> <code>{apk_rep['sha256'][:24]}...</code>",
                                        "",
                                        f"🚨 <b>KEPUTUSAN SANDBOX KANTOI:</b>",
                                        f"<b>{html.escape(apk_rep['verdict'])}</b>",
                                        ""
                                    ]
                                    if apk_rep["danger_perms"]:
                                        lines.append("⚠️ <b>Kebenaran Berbahaya Dikesan:</b>")
                                        for dp, desc in apk_rep["danger_perms"]:
                                            lines.append(f"   • <code>{html.escape(dp)}</code>: {html.escape(desc)}")
                                        lines.append("")
                                    if apk_rep["dex_tokens"]:
                                        lines.append(f"🤖 <b>Bot C2 Exfiltration:</b> Dikesan <code>{len(apk_rep['dex_tokens'])}</code> token Bot Telegram scammer dalam kod!")
                                    if apk_rep["has_banking_strings"]:
                                        lines.append("🏦 <b>Sasaran Perbankan:</b> Rentetan nama bank tempatan (Maybank/CIMB/TNG) ditemui dalam kod!")
                                    lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                    if apk_rep["severity"] == "CRITICAL":
                                        lines.append("🚫 <b>AMARAN KRITIKAL:</b> JANGAN PASANG fail ini! Ia mengandungi kod trojan pencuri SMS TAC/OTP untuk menceroboh akaun bank anda.")
                                    else:
                                        lines.append("ℹ️ <i>Dianalisis secara automatik oleh KANTOI Static Android Sandbox tanpa menjalankan kod.</i>")

                                    api_call("sendMessage", {
                                        "chat_id": t_chat_id,
                                        "text": "\n".join(lines),
                                        "parse_mode": "HTML"
                                    })
                                    return

                                # Laporan Phishing / Pautan Biasa (Quishing)
                                is_danger = res_url["severity"] in ["CRITICAL", "HIGH"]
                                badge = "🚨" if is_danger else ("⚠️" if res_url["severity"] == "MEDIUM" else "✅")
                                
                                title_text = "PERISAI KANTOI: KOD QR PHISHING (QUISHING) DIKESAN!" if is_danger else "HASIL IMBASAN KOD QR: PAUTAN (URL)"
                                lines = [
                                    f"{badge} <b>{title_text}</b>",
                                    "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                    f"🔗 <b>Pautan Terkandung:</b> <code>{html.escape(res_url['url'])}</code>",
                                    f"🌐 <b>Domain Pendaratan:</b> <code>{html.escape(res_url['domain'])}</code>",
                                    f"🛡️ <b>Keputusan:</b> <b>{html.escape(res_url['verdict'])}</b>",
                                    ""
                                ]
                                if res_url["reasons"]:
                                    lines.append("⚠️ <b>Faktor Penilaian Risiko:</b>")
                                    for rsn in res_url["reasons"]:
                                        lines.append(f"   • {html.escape(rsn)}")
                                    lines.append("")
                                lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                if is_danger:
                                    lines.append("🚫 <b>AMARAN:</b> Ini adalah perangkap pancingan data (Quishing) untuk mencuri kata laluan atau TAC bank anda. Jangan buka pautan ini!")
                                else:
                                    lines.append("ℹ️ <i>Pautan ini tidak menunjukkan sebarang ciri phishing atau penyamaran bank rasmi yang diketahui.</i>")

                                api_call("sendMessage", {
                                    "chat_id": t_chat_id,
                                    "text": "\n".join(lines),
                                    "parse_mode": "HTML"
                                })
                                return

                            # B. Semakan Sasaran Nombor / Akaun
                            check_target = target_acc if (target_acc and target_acc != "N/A") else target_phone
                            clean_digits = re.sub(r'[^\d]', '', check_target)
                            check_cat = "bank" if (target_acc and target_acc != "N/A" and len(clean_digits) > 8) else "telefon"

                            rec_kantoi = None
                            if target_phone and target_phone != "N/A":
                                rec_kantoi, _ = query_threat(target_phone)
                            if not rec_kantoi and target_acc and target_acc != "N/A":
                                rec_kantoi, _ = query_threat(target_acc)

                            # Semakan Siap-Sedia PDRM CCID SemakMule Secara Langsung
                            pdrm_info = query_pdrm_semakmule_live(check_cat, check_target) if check_target else {"success": False}
                            pdrm_reports = pdrm_info.get("police_reports", 0) if pdrm_info.get("success") else 0
                            pdrm_searches = pdrm_info.get("search_count", 0) if pdrm_info.get("success") else 0

                            is_threat = (rec_kantoi is not None) or (pdrm_reports > 0)
                            source_badge = "📱 Kod QR DuitNow" if intel["qr_detected"] else ("🤖 AI Vision" if intel["source_type"] == "AI_VISION" else "🔍 OCR KANTOI")
                            is_phone_only = bool(target_phone and (not target_acc or target_acc == "N/A"))

                            # Jana Draf Laporan Sementara
                            tmp_id = f"tmp_{int(time.time()*1000) % 1000000}"
                            combined_evidence = intel["evidence_text"]
                            if t_caption:
                                combined_evidence = f"{t_caption}\n\n[Bukti Imej]: {combined_evidence}"

                            if intel.get("email"):
                                combined_evidence = f"{combined_evidence}\n[Emel]: {intel['email']}"
                            rep_data = {
                                "user_id": t_chat_id,
                                "username": t_username,
                                "phone_norm": target_phone or target_acc or "N/A",
                                "suspect_name": f"{intel['tactic']} ({target_name})" if target_name != "Tidak Dinyatakan" else intel['tactic'],
                                "bank_account": intel["bank_accounts"],
                                "evidence_text": combined_evidence,
                                "photo_file_id": t_file_id,
                                "status": "PENDING"
                            }
                            unconfirmed_reports[tmp_id] = rep_data
                            save_pending_report(tmp_id, rep_data)

                            if is_threat:
                                is_phone_only = bool(target_phone and (not target_acc or target_acc == "N/A"))
                                target_label = "Nombor Telefon" if is_phone_only else "No. Akaun / Sasaran"
                                target_icon = "📱" if is_phone_only else "💳"

                                # KAD MERAH: ANCAMAN SCAMMER DIKESAN
                                warn_lines = [
                                    f"🚨 <b>PERISAI KANTOI: ANCAMAN SCAMMER DIKESAN DARI {source_badge.upper()}!</b>",
                                    "━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                ]
                                if is_phone_only:
                                    warn_lines.append(f"📱 <b>Nombor Telefon:</b> <code>{html.escape(target_phone)}</code>")
                                    if target_name and target_name != "Tidak Dinyatakan":
                                        warn_lines.append(f"👤 <b>Nama Suspek / Panggilan:</b> {html.escape(target_name)}")
                                else:
                                    warn_lines.append(f"👤 <b>Nama Pemunya:</b> {html.escape(target_name)}")
                                    if target_bank != "N/A":
                                        warn_lines.append(f"🏦 <b>Institusi / Bank:</b> {html.escape(target_bank)}")
                                    warn_lines.append(f"💳 <b>No. Akaun Bank:</b> <code>{html.escape(check_target)}</code>")

                                if intel.get("email"):
                                    warn_lines.append(f"📧 <b>Emel Suspek:</b> <code>{html.escape(intel['email'])}</code>")
                                if intel.get("tactic"):
                                    warn_lines.append(f"🏷️ <b>Modus / Taktik:</b> {html.escape(intel['tactic'])}")

                                warn_lines.extend([
                                    "",
                                    f"📑 <b>Laporan Polis Rasmi (PDRM):</b> <b>{pdrm_reports} Laporan</b>",
                                    f"👥 <b>Jumlah Carian Awam PDRM:</b> <b>{pdrm_searches} Kali</b>",
                                ])
                                if rec_kantoi:
                                    warn_lines.append(f"🏷️ <b>Klasifikasi KANTOI:</b> {html.escape(rec_kantoi[2] or 'AMARAN')}")
                                    if rec_kantoi[1]:
                                        warn_lines.append(f"👤 <b>Rekod Suspek:</b> {html.escape(rec_kantoi[1])}")
                                    if rec_kantoi[4]:
                                        warn_lines.append(f"📝 <b>Modus Operandi:</b> {html.escape(rec_kantoi[4])}")

                                cse_snippets = fetch_google_cse_snippets(check_target)
                                if cse_snippets:
                                    warn_lines.append("")
                                    warn_lines.append("🌐 <b>Hasil Media Sosial & Forum (Google CSE):</b>")
                                    for cs in cse_snippets:
                                        warn_lines.append(f"• <b>[{html.escape(cs['source'])}]</b> {html.escape(cs['title'])}")
                                        if cs.get('snippet'):
                                            warn_lines.append(f"  <i>{html.escape(cs['snippet'])}</i>")
                                warn_lines.append("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                warn_lines.append("🚫 <b>AMARAN:</b> Akaun ini mempunyai rekod jenayah penipuan. JANGAN PINDAHKAN SEBARANG WANG!")

                                is_admin_sender = t_chat_id in get_admin_ids()
                                cse_btn = {"text": "🌐 Semak Media Sosial (Google CSE)", "url": generate_google_cse_url(check_target or target_name)}
                                if is_admin_sender:
                                    markup = {
                                        "inline_keyboard": [
                                            [cse_btn],
                                            [
                                                {"text": "⚡ Luluskan & Siar Terus", "callback_data": f"cfrm_direct_{tmp_id}"},
                                                {"text": "❌ Batal", "callback_data": f"cncl_{tmp_id}"}
                                            ]
                                        ]
                                    }
                                else:
                                    markup = {
                                        "inline_keyboard": [
                                            [cse_btn],
                                            [
                                                {"text": "📢 Tambah Aduan Saya", "callback_data": f"repqr_{tmp_id}"},
                                                {"text": "❌ Batal", "callback_data": f"cncl_{tmp_id}"}
                                            ]
                                        ]
                                    }

                                api_call("sendMessage", {
                                    "chat_id": t_chat_id,
                                    "text": "\n".join(warn_lines),
                                    "parse_mode": "HTML",
                                    "reply_markup": markup
                                })
                            else:
                                # KAD HIJAU: BERSIH / TIADA REKOD ANCAMAN
                                clean_lines = [
                                    f"✅ <b>HASIL IMBASAN {source_badge.upper()}</b>",
                                    "━━━━━━━━━━━━━━━━━━━━━━━━━━"
                                ]
                                if is_phone_only:
                                    clean_lines.append(f"📱 <b>Nombor Telefon:</b> <code>{html.escape(target_phone)}</code>")
                                    if target_name and target_name != "Tidak Dinyatakan":
                                        clean_lines.append(f"👤 <b>Nama / Profil:</b> {html.escape(target_name)}")
                                else:
                                    clean_lines.append(f"👤 <b>Nama Pemunya:</b> {html.escape(target_name)}")
                                    if target_bank != "N/A":
                                        clean_lines.append(f"🏦 <b>Institusi / Bank:</b> {html.escape(target_bank)}")
                                    clean_lines.append(f"💳 <b>No. Akaun Bank:</b> <code>{html.escape(check_target or 'N/A')}</code>")

                                if intel.get("email"):
                                    clean_lines.append(f"📧 <b>Emel Dikesan:</b> <code>{html.escape(intel['email'])}</code>")
                                if intel.get("tactic"):
                                    clean_lines.append(f"📝 <b>Taktik / Ringkasan:</b> <i>{html.escape(intel['tactic'])}</i>")

                                clean_lines.extend([
                                    "",
                                    f"🏛️ <b>PDRM SemakMule CCID:</b> <b>Tiada Laporan Polis (0 Rekod)</b>",
                                    f"👥 <b>Jumlah Carian Awam PDRM:</b> {pdrm_searches} Kali",
                                    f"🛡️ <b>Pangkalan Data KANTOI:</b> <b>Rekod Bersih (6,100+ Senarai)</b>",
                                ])

                                cse_snippets = fetch_google_cse_snippets(check_target)
                                if cse_snippets:
                                    clean_lines.append("")
                                    clean_lines.append("🌐 <b>Hasil Media Sosial & Forum (Google CSE):</b>")
                                    for cs in cse_snippets:
                                        clean_lines.append(f"• <b>[{html.escape(cs['source'])}]</b> {html.escape(cs['title'])}")
                                        if cs.get('snippet'):
                                            clean_lines.append(f"  <i>{html.escape(cs['snippet'])}</i>")
                                else:
                                    clean_lines.append("🌐 <b>Media Sosial (Google CSE):</b> <i>Tiada aduan dikesan di Lowyat/FB.</i>")

                                clean_lines.extend([
                                    "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                    f"ℹ️ <i>Buat masa ini tiada rekod aduan rasmi difailkan terhadap {'nombor' if is_phone_only else 'akaun'} ini.</i>\n",
                                    "💡 <i>Sekiranya anda merupakan mangsa penipuan individu ini, sila tekan butang di bawah untuk menghantar aduan:</i>"
                                ])
                                cse_btn_clean = {"text": "🌐 Semak Media Sosial (Google CSE)", "url": generate_google_cse_url(check_target or target_name)}
                                markup = {
                                    "inline_keyboard": [
                                        [cse_btn_clean],
                                        [
                                            {"text": "📢 Lapor Sebagai Scammer", "callback_data": f"repqr_{tmp_id}"},
                                            {"text": "ℹ️ Menu Utama", "callback_data": "help_menu"}
                                        ]
                                    ]
                                }
                                api_call("sendMessage", {
                                    "chat_id": t_chat_id,
                                    "text": "\n".join(clean_lines),
                                    "parse_mode": "HTML",
                                    "reply_markup": markup
                                })
                        else:
                            # Tiada maklumat dapat dikesan daripada imej
                            active_k, src_file, _ = get_gemini_api_key_details()
                            g_status = intel.get("gemini_status", "")
                            g_err = intel.get("gemini_error", "")

                            if not active_k:
                                ai_diag = "⚠️ <b>AI Vision Belum Ditetapkan:</b> Kunci Gemini API belum dimasukkan di pelayan.\n👉 <i>Taip <code>/gemini &lt;API_KEY&gt;</code> untuk aktifkan analisis tangkap layar WhatsApp secara percuma!</i>"
                            elif g_err:
                                ai_diag = f"⚠️ <b>Status AI Gemini ({html.escape(g_status)}):</b>\n<code>{html.escape(g_err[:180])}</code>\n👉 <i>Sila semak semula kunci anda melalui arahan <code>/gemini</code>.</i>"
                            else:
                                ai_diag = "🔍 <b>Pengecaman Teks:</b> Tiada nombor telefon (+60 / 01x), akaun bank, atau nama suspek yang jelas ditemui dalam gambar."

                            fb_lines = [
                                "📸 <b>Gambar Diterima, Namun Tiada Maklumat Ancaman Berjaya Dibaca.</b>",
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                ai_diag,
                                "",
                                "💡 <b>Tip Semakan Pantas KANTOI:</b>",
                                "• <b>Tangkapan Layar WhatsApp / Telegram:</b> Pastikan teks atau nombor telefon suspek tidak terlalu beresolusi rendah.",
                                "• <b>Semakan Manual Terus:</b> Anda boleh terus taip atau tampal (paste) nombor telefon atau akaun suspek ke dalam chat ini untuk semakan serta-merta tanpa perlu hantar gambar!",
                                "• <b>Kod QR:</b> Jika kod QR, pastikan petak tidak kabur dan bucu QR tidak terpotong."
                            ]
                            send_message(t_chat_id, "\n".join(fb_lines), parse_mode="HTML")
                    finally:
                        if os.path.exists(tmp_img):
                            try:
                                os.unlink(tmp_img)
                            except Exception:
                                pass

                # Kendalikan Foto yang dihantar
                if photos:
                    process_image_intel_message(chat_id, username, photos[-1]["file_id"], text)
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
                        process_image_intel_message(chat_id, username, doc["file_id"], text, is_doc=True, doc_filename=doc.get("file_name", ""))
                        continue

                    if doc_name.endswith(".apk") or "package-archive" in doc_mime:
                        api_call("sendChatAction", {"chat_id": chat_id, "action": "upload_document"})
                        tmp_apk = download_telegram_photo(doc["file_id"])
                        if tmp_apk:
                            try:
                                rep = inspect_apk_malware(tmp_apk, doc.get("file_name", "aplikasi.apk"))
                                if "error" in rep:
                                    send_message(chat_id, f"❌ Ralat menganalisis APK: {rep['error']}")
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

                                    send_message(chat_id, "\n".join(lines))
                            finally:
                                if os.path.exists(tmp_apk):
                                    try: os.unlink(tmp_apk)
                                    except: pass
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

                # ------------------------------------------------------------------
                # MOD CHAT PERIBADI: SEMAKAN MANUAL /SEMAK & /CHECK
                # ------------------------------------------------------------------
                if text.startswith(("/semak", "/check", "/kantoi")):
                    parts = text.split(maxsplit=1)
                    if len(parts) > 1 and parts[1].strip():
                        target = parts[1].strip()
                        rec, norm = query_threat(target)
                        if rec:
                            reply = format_threat_response(rec, target)
                            markup = get_threat_check_keyboard(target, norm, has_threat=True)
                        else:
                            reply = format_safe_response(target, norm)
                            markup = get_threat_check_keyboard(target, norm, has_threat=False)
                        send_message(chat_id, reply, reply_markup=markup)
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
                        cse_url = generate_google_cse_url(target)
                        reply = (
                            "🌐 *CARIAN PERTIKAIAN MEDIA SOSIAL (GOOGLE CSE)*\n"
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            f"🔍 *Sasaran Carian:* `{target}`\n"
                            "📚 *Sumber Liputan:* Forum Lowyat, CariGold, Facebook, Twitter/X, Telegram, TikTok & Blogspot.\n"
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                            "Enjin Carian Google CSE KANTOI membolehkan anda mencari sebarang aduan mangsa, resit transaksi meragukan, atau perbincangan forum yang membabitkan nombor atau akaun ini.\n\n"
                            "👇 *Ketik butang di bawah untuk membuka carian terus di Telegram:*"
                        )
                        markup = {
                            "inline_keyboard": [
                                [{"text": "🌐 BUKA GOOGLE CSE KANTOI 🌐", "url": cse_url}],
                                [
                                    {"text": "🏛️ Portal PDRM CCID", "url": "https://semakmule.rmp.gov.my"},
                                    {"text": "📰 Buletin Berita Scam", "callback_data": "read_bulletin_now"}
                                ]
                            ]
                        }
                        send_message(chat_id, reply, reply_markup=markup)
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

                # Arahan Konfigurasi & Status Gemini API: /gemini, /key, /ai, /ai_status
                if text.startswith(("/gemini", "/key", "/ai_status", "/ai")):
                    parts = text.split(maxsplit=2)
                    candidate_key = ""
                    if len(parts) > 1:
                        if len(parts) > 2 and parts[1] == ADMIN_PASSCODE:
                            candidate_key = parts[2].strip()
                        else:
                            candidate_key = parts[1].strip()

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
                        test_models = ["gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-flash-8b"]
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
                    active_model_info = "gemini-2.0-flash (Ultra-Fast Vision)"
                    if current_k:
                        test_models_diag = ["gemini-2.0-flash", "gemini-1.5-flash"]
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

                # Start / Bantuan / Menu Interaktif
                if text in ["/start", "/help", "start", "help", "bantuan", "/menu", "menu", "panduan"]:
                    user_sessions.pop(chat_id, None)
                    is_admin = chat_id in get_admin_ids()
                    # Hantar menu pantas papan kekunci (Reply Keyboard) dan menu inline interaktif
                    send_message(chat_id, format_welcome_message(is_admin), reply_markup=get_help_menu_keyboard())
                    continue

                # Butang Pantas Papan Kekunci Bawah (Quick Keyboard Buttons)
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
                    send_message(chat_id, format_welcome_message(is_admin), reply_markup=get_help_menu_keyboard())
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
                        preview_msg += "⭐ <b>Mod Admin:</b> Anda boleh meluluskan dan menyiarkan senarai ini terus ke @crowdcaller dengan satu klik:"
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
                            f"Laporan akan disemak dan disahkan oleh admin sebelum disiarkan ke saluran @crowdcaller. Terima kasih!"
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
                # E. SEMAKAN PANGKALAN DATA BIASA (TIADA CIRI LAPORAN)
                # ------------------------------------------------------------------
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
