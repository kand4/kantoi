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
    from local_secrets import BOT_TOKEN
except ImportError:
    BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "YOUR_TELEGRAM_BOT_TOKEN_HERE")
CHANNEL_ID = "@crowdcaller"
ADMIN_PASSCODE = "kantoi2026"

DB_PATH = os.path.join(BASE_DIR, "kantoi_threats.db")
if not os.path.exists(DB_PATH):
    DB_PATH = os.path.join(BASE_DIR, "KantoiApp", "assets", "kantoi_threats.db")
BACKUP_DB_PATH = os.path.join(BASE_DIR, "lowyat_threats.db")

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

    lines.append("--------------------------------------------------")
    lines.append("ℹ️ _Disemak daripada KANTOI Threat Engine (3,652+ Rekod)._")
    lines.append("📲 _Gunakan aplikasi KANTOI untuk perlindungan panggilan & SMS masa nyata._")
    return "\n".join(lines)

def format_safe_response(query_input, norm_phone):
    display_num = norm_phone if norm_phone else query_input
    lines = [
        "==================================================",
        "✅ *TIADA REKOD SCAMMER DITEMUI*",
        "==================================================",
        f"Nombor Disemak: `{display_num}`",
        "Status: *BERSIH* (Tiada rekod penipuan dalam pangkalan data KANTOI)",
        "",
        "⚠️ *PERINGATAN PENTING:*",
        "• Walaupun tiada rekod, sentiasa berwaspada!",
        "• Jangan sesekali berkongsi kata laluan, TAC/OTP, atau memindahkan wang ke akaun peribadi yang tidak dikenali.",
        "• Anda boleh melaporkan nombor ini dengan menaip cerita/bukti kejadian jika mengesyaki penipuan.",
        "--------------------------------------------------",
        "ℹ️ _Pangkalan Data KANTOI merangkumi rekod PDRM SemakMule CCID & Komuniti._"
    ]
    return "\n".join(lines)

def format_welcome_message(is_admin=False):
    admin_tag = "\n⭐ *Status Anda:* Pentadbir (Admin KANTOI)" if is_admin else ""
    return (
        "👋 *Selamat datang ke Bot Semakan & Laporan Scammer KANTOI!*\n\n"
        "🔍 *1. Semak Nombor:* Taip nombor telefon (contoh: `0179597645`) atau akaun bank untuk menyemak rekod.\n\n"
        "📢 *2. Lapor Scammer (Teks, Senarai Panjang & Bergambar):*\n"
        "Anda dialu-alukan melaporkan sebarang cubaan penipuan:\n"
        "• *Teks Bebas ('Main Tulis Saja'):* Ceritakan pengalaman anda secara santai.\n"
        "• *Senarai Pukal:* Tampal senarai nombor penipu serentak.\n"
        "• *Tangkap Layar (Bergambar):* Hantar gambar tangkap layar (screenshot) resit bank atau perbualan chat scammer terus ke sini!\n\n"
        "🤖 Enjin pintar KANTOI akan menyusun laporan anda secara automatik untuk disahkan oleh Pentadbir sebelum disiarkan ke saluran @crowdcaller demi melindungi jutaan rakyat Malaysia.\n"
        f"{admin_tag}\n\n"
        "🛡️ Saluran Rasmi: @crowdcaller\n"
        "🌐 Repositori Kod: https://github.com/kand4/kantoi"
    )

# ==============================================================================
# TELEGRAM API CALLS
# ==============================================================================
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

def send_message(chat_id, text, reply_markup=None):
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "Markdown",
        "disable_web_page_preview": True
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    return api_call("sendMessage", payload)

def edit_message(chat_id, message_id, text, reply_markup=None):
    payload = {
        "chat_id": chat_id,
        "message_id": message_id,
        "text": text,
        "parse_mode": "Markdown",
        "disable_web_page_preview": True
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    return api_call("editMessageText", payload)

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
    print(f"[BOT] Pangkalan data: {DB_PATH}")
    print(f"[BOT] Saluran siaran: {CHANNEL_ID}")
    
    admins = get_admin_ids()
    print(f"[BOT] Admin berdaftar semasa ({len(admins)}): {list(admins)}")
    
    last_update_id = 0
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
                            }
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
                user = message.get("from", {})
                username = user.get("username") or user.get("first_name", "Komuniti")
                text = (message.get("text") or message.get("caption") or "").strip()
                photos = message.get("photo")

                if not text and not photos:
                    continue

                # Jika pengguna hantar foto tanpa teks dan ada laporan/batch draf yang belum ada foto
                if photos and not text:
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
                        if found_key in unconfirmed_reports:
                            unconfirmed_reports[found_key]["photo_file_id"] = photos[-1]["file_id"]
                        reports = get_pending_reports()
                        if found_key in reports:
                            reports[found_key]["photo_file_id"] = photos[-1]["file_id"]
                            save_json_file(REPORTS_DATA_PATH, reports)
                        send_message(chat_id, "📸 *Gambar bukti berjaya dilampirkan pada draf laporan anda!*")
                    else:
                        send_message(chat_id, "📸 *Gambar diterima.* Sila sertakan teks nombor telefon atau penerangan scammer.")
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

                # Start / Bantuan
                if text in ["/start", "/help", "start", "help", "bantuan"]:
                    user_sessions.pop(chat_id, None)
                    is_admin = chat_id in get_admin_ids()
                    send_message(chat_id, format_welcome_message(is_admin))
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
                else:
                    reply = format_safe_response(text, norm_phone)
                    print(f"[CLEAN] Tiada rekod untuk: {text}")

                send_message(chat_id, reply)

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
