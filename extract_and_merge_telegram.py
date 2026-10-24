import json
import os
import re
import sqlite3
import asyncio
import time
import sys
from PIL import Image
import winocr

sys.stdout.reconfigure(encoding='utf-8')

BASE_DIR = os.path.join(os.path.expanduser("~"), r"Downloads\Telegram Desktop\ChatExport_2026-10-18")
JSON_PATH = os.path.join(BASE_DIR, "result.json")
DB_PATH = "lowyat_threats.db"

# Channel admin / known trusted midman numbers to exclude from being flagged
TRUSTED_WHITELIST = {
    '01139538679', # Midman Hakimie
    '01624196700', '0162419670', # Midman Bani Ojan
    '01127461392', # Midman Noah
    '01171148895', # Midman Major
    '0132283831',  # Midman Amon
    '01139740407', # Midman Nurudin
    '01153360552', # Midman Dinie
    '0132167781',  # Midman Hazx
    '0139740463',  # Midman Onchie
    '0199655623',  # Midman Fitrry
    '01156218904', # Midman Jp
    '0174015827',  # Midman Aiman
}

PHONE_REGEX = re.compile(r'(?:\+?60|0)[1-9]\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{3,5}')
WA_LINK_REGEX = re.compile(r'wa(?:sap)?\.(?:me|my)/(?:60|0)?([1-9]\d{7,10})')
IC_REGEX = re.compile(r'\b\d{6}-\d{2}-\d{4}\b')

BANK_PATTERNS = [
    (re.compile(r'maybank|malayan banking', re.I), 'Maybank'),
    (re.compile(r'cimb', re.I), 'CIMB Bank'),
    (re.compile(r'bank islam', re.I), 'Bank Islam'),
    (re.compile(r'bsn|bank simpanan nasional', re.I), 'BSN'),
    (re.compile(r'rhb', re.I), 'RHB Bank'),
    (re.compile(r'public bank', re.I), 'Public Bank'),
    (re.compile(r'hong leong', re.I), 'Hong Leong Bank'),
    (re.compile(r'ambank', re.I), 'AmBank'),
    (re.compile(r'agrobank|bank pertanian', re.I), 'Agrobank'),
    (re.compile(r'bank muamalat', re.I), 'Bank Muamalat'),
    (re.compile(r'affin bank', re.I), 'Affin Bank'),
    (re.compile(r'touch\s*[\'n]?\s*go|tng\s*ewallet', re.I), 'TnG eWallet')
]

ACCOUNT_PATTERNS = [
    re.compile(r'(?:to\s+account\s+number|account\s+number|no\.?\s*akaun|acc\s*no\.?|a/c\s*no\.?)\s*[:.]?\s*(\d{8,18})', re.I),
    re.compile(r'(?:account|akaun|acc)\s*[:.]?\s*(\d{8,18})', re.I)
]

RECIPIENT_PATTERNS = [
    re.compile(r'(?:recipient\s+name|nama\s+penerima|beneficiary\s+name|to\s+name)\s*[:.]?\s*([A-Za-z@/\s]{3,40})', re.I),
    re.compile(r'(?:recipient|penerima)\s*[:.]?\s*([A-Za-z@/\s]{3,40})', re.I)
]

def clean_phone(raw):
    if not raw: return None
    d = re.sub(r'\D', '', raw)
    if d.startswith('60'):
        d = '0' + d[2:]
    elif d.startswith('1'):
        d = '0' + d
    if d.startswith('01') and 9 <= len(d) <= 11:
        if d in TRUSTED_WHITELIST:
            return None
        return d
    return None

async def extract_all():
    print(f"Loading chat export: {JSON_PATH}")
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        chat_data = json.load(f)

    messages = chat_data.get("messages", [])
    print(f"Total messages to inspect: {len(messages)}")

    # 1. Group messages by timestamp (albums)
    # Telegram albums share the exact same date_unixtime or within 3 seconds
    grouped_posts = []
    current_group = []
    last_time = 0

    for m in messages:
        t = int(m.get("date_unixtime", 0))
        if not current_group or abs(t - last_time) <= 3:
            current_group.append(m)
        else:
            grouped_posts.append(current_group)
            current_group = [m]
        last_time = t
    if current_group:
        grouped_posts.append(current_group)

    print(f"Grouped into {len(grouped_posts)} distinct incident posts / albums.")

    threat_records = {} # localPhone -> dict of details
    t0 = time.time()
    photos_ocred = 0

    for idx, group in enumerate(grouped_posts):
        combined_text = ""
        case_title = ""
        group_photos = []

        for m in group:
            raw_text = m.get("text", "")
            if isinstance(raw_text, str):
                combined_text += " " + raw_text
            elif isinstance(raw_text, list):
                for part in raw_text:
                    if isinstance(part, str): combined_text += " " + part
                    elif isinstance(part, dict): combined_text += " " + part.get("text", "")

            # Tagged entities
            for ent in m.get("text_entities", []):
                if ent.get("type") == "phone":
                    combined_text += " " + ent.get("text", "")
                elif ent.get("type") == "link":
                    combined_text += " " + ent.get("text", "")

            photo_rel = m.get("photo")
            if photo_rel:
                group_photos.append(photo_rel)

        # Detect Case Title, e.g. "Kes 123"
        kes_match = re.search(r'\b(Kes\s*\d+)\b', combined_text, re.I)
        if kes_match:
            case_title = kes_match.group(1).title()

        # Run OCR on all photos in this group
        group_ocr_text = ""
        for photo_rel in group_photos:
            photo_path = os.path.join(BASE_DIR, photo_rel)
            if os.path.exists(photo_path):
                try:
                    img = Image.open(photo_path)
                    ocr_res = await winocr.recognize_pil(img)
                    group_ocr_text += "\n" + ocr_res.text
                    photos_ocred += 1
                except Exception:
                    pass

        full_incident_text = combined_text + "\n" + group_ocr_text

        # Extract Phones
        incident_phones = set()
        for p in PHONE_REGEX.findall(full_incident_text):
            cp = clean_phone(p)
            if cp: incident_phones.add(cp)
        for w in WA_LINK_REGEX.findall(full_incident_text):
            cp = clean_phone(w)
            if cp: incident_phones.add(cp)

        # Extract ICs
        incident_ics = set(IC_REGEX.findall(full_incident_text))

        # Extract Banks
        incident_banks = []
        for b_regex, b_name in BANK_PATTERNS:
            if b_regex.search(full_incident_text):
                incident_banks.append(b_name)

        # Extract Bank Accounts
        incident_accounts = []
        for a_regex in ACCOUNT_PATTERNS:
            for acc in a_regex.findall(full_incident_text):
                clean_acc = re.sub(r'\D', '', acc)
                if 8 <= len(clean_acc) <= 18:
                    incident_accounts.append(clean_acc)

        # Extract Suspect / Recipient Names
        incident_recipients = []
        for r_regex in RECIPIENT_PATTERNS:
            for rec in r_regex.findall(full_incident_text):
                clean_rec = rec.strip().split('\n')[0].strip()
                if len(clean_rec) >= 3 and not any(k in clean_rec.lower() for k in ['transfer', 'duitnow', 'amount', 'success', 'ref', 'cimb', 'maybank', 'bank']):
                    incident_recipients.append(clean_rec.upper())

        # If we have phones, associate all details with each phone!
        if incident_phones:
            bank_str = ""
            if incident_banks and incident_accounts:
                bank_str = f"{incident_banks[0]} {incident_accounts[0]}"
            elif incident_accounts:
                bank_str = f"Akaun: {incident_accounts[0]}"
            elif incident_banks:
                bank_str = f"{incident_banks[0]}"

            suspect_name = incident_recipients[0] if incident_recipients else ""
            if not suspect_name:
                suspect_name = f"Suspek Scammer ({case_title})" if case_title else "Suspek Scammer (Telegram)"

            ic_str = list(incident_ics)[0] if incident_ics else "N/A"

            # Create clean short description for notes
            snippet = combined_text.strip()
            # remove excess whitespace
            snippet = re.sub(r'\s+', ' ', snippet)[:180]
            if not snippet:
                snippet = f"Laporan saluran Telegram Info Scammer Malaysia ({case_title})" if case_title else "Laporan Telegram Info Scammer Malaysia"

            for ph in incident_phones:
                if ph not in threat_records:
                    threat_records[ph] = {
                        'phone_norm': ph,
                        'suspect_name': suspect_name,
                        'bank_accounts': bank_str if bank_str else "N/A",
                        'ic_number': ic_str,
                        'address': "N/A",
                        'evidence_links': snippet,
                        'risk_tag': "TELEGRAM_SCAMMER_INTEL",
                        'source': f"Telegram Info Scammer MY ({case_title})" if case_title else "Telegram Info Scammer MY"
                    }
                else:
                    # Enrich existing record
                    rec = threat_records[ph]
                    if rec['bank_accounts'] == "N/A" and bank_str:
                        rec['bank_accounts'] = bank_str
                    if rec['ic_number'] == "N/A" and ic_str != "N/A":
                        rec['ic_number'] = ic_str
                    if "Suspek Scammer" in rec['suspect_name'] and incident_recipients:
                        rec['suspect_name'] = incident_recipients[0]

        if (idx + 1) % 100 == 0 or idx == len(grouped_posts) - 1:
            elapsed = time.time() - t0
            print(f"Processed {idx + 1}/{len(grouped_posts)} albums | OCR-ed {photos_ocred} photos | Extracted {len(threat_records)} unique scammer numbers ({elapsed:.1f}s)...")

    print(f"\n==========================================")
    print(f"EXTRACTION COMPLETE in {time.time() - t0:.1f}s!")
    print(f"Total Photos Processed via OCR: {photos_ocred}")
    print(f"Total Unique Scammer Numbers Extracted: {len(threat_records)}")
    print(f"==========================================")

    # 3. Merge into SQLite database
    print(f"\nMerging extracted intelligence into SQLite database: {DB_PATH}")
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM threat_numbers")
    count_before = cursor.fetchone()[0]
    cursor.execute("SELECT MAX(profile_id) FROM threat_numbers")
    max_prof = cursor.fetchone()[0] or 10000

    next_profile_id = max_prof + 1
    new_inserts = 0
    enriched_updates = 0

    for phone, item in threat_records.items():
        cursor.execute("SELECT profile_id, suspect_name, bank_accounts, ic_number, evidence_links FROM threat_numbers WHERE phone_norm = ?", (phone,))
        existing = cursor.fetchone()

        if existing:
            # Enrich existing record if it had missing bank / IC
            p_id, ex_name, ex_bank, ex_ic, ex_ev = existing
            up_name = ex_name
            up_bank = ex_bank
            up_ic = ex_ic

            changed = False
            if (ex_bank in (None, 'N/A', 'Sila semak di portal SemakMule')) and item['bank_accounts'] != 'N/A':
                up_bank = item['bank_accounts']
                changed = True
            if (ex_ic in (None, 'N/A')) and item['ic_number'] != 'N/A':
                up_ic = item['ic_number']
                changed = True
            if ('suspek' in ex_name.lower() or ex_name.strip() == '') and ('suspek' not in item['suspect_name'].lower()):
                up_name = item['suspect_name']
                changed = True

            if changed:
                cursor.execute("""
                    UPDATE threat_numbers
                    SET suspect_name = ?, bank_accounts = ?, ic_number = ?
                    WHERE phone_norm = ?
                """, (up_name, up_bank, up_ic, phone))
                enriched_updates += 1
        else:
            # Insert brand new scammer
            cursor.execute("""
                INSERT INTO threat_numbers (
                    phone_norm, profile_id, suspect_name, all_phones,
                    bank_accounts, ic_number, address, evidence_links,
                    risk_tag, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                phone, next_profile_id, item['suspect_name'], phone,
                item['bank_accounts'], item['ic_number'], item['address'],
                item['evidence_links'], item['risk_tag'], item['source']
            ))
            next_profile_id += 1
            new_inserts += 1

    conn.commit()

    cursor.execute("SELECT COUNT(*) FROM threat_numbers")
    count_after = cursor.fetchone()[0]
    conn.close()

    print(f"\n================ DATABASE MERGE SUMMARY ================")
    print(f"Total Threats Before: {count_before}")
    print(f"New Scammer Numbers Added: +{new_inserts}")
    print(f"Existing Scammers Enriched with Bank/IC: {enriched_updates}")
    print(f"Total Threats After: {count_after}")
    print(f"========================================================")

if __name__ == "__main__":
    asyncio.run(extract_all())
