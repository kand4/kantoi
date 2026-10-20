import json
import sqlite3
import re
import os
import openpyxl

def normalize_digits(p):
    d = re.sub(r'[^0-9]', '', p.strip())
    if d.startswith('60'):
        d = '0' + d[2:]
    return d

def merge_databases():
    # 1. Load Lowyat database
    with open('lowyat_scammers_db.json', 'r', encoding='utf-8') as f:
        lowyat_db = json.load(f)
    print(f"Lowyat base records: {len(lowyat_db)}")

    # 2. Load CariGold database
    if not os.path.exists('carigold_scammers_db.json'):
        print("Error: carigold_scammers_db.json not found!")
        return

    with open('carigold_scammers_db.json', 'r', encoding='utf-8') as f:
        carigold_db = json.load(f)
    print(f"CariGold extracted records: {len(carigold_db)}")

    # 3. Track existing phones in Lowyat to avoid duplicates
    existing_phones = set()
    for item in lowyat_db:
        combined = item.get('all_phones', '') + ' ' + item.get('primary_phone', '')
        for p in re.findall(r'(?:\+?60|0)[1-9]\d{7,10}', combined):
            norm = normalize_digits(p)
            if norm:
                existing_phones.add(norm)

    print(f"Existing known phone numbers in Lowyat: {len(existing_phones)}")

    # 4. Merge CariGold into combined list
    combined_db = list(lowyat_db)
    current_id = max([r['id'] for r in lowyat_db]) if lowyat_db else 0
    added_carigold = 0

    for cg in carigold_db:
        phones_str = cg.get('all_phones', '') + ' ' + cg.get('primary_phone', '')
        cg_phones = set()
        for p in re.findall(r'(?:\+?60|0)[1-9]\d{7,10}', phones_str):
            norm = normalize_digits(p)
            if norm and len(norm) >= 9:
                cg_phones.add(norm)

        # Check if already present
        new_phones = [p for p in cg_phones if p not in existing_phones]
        if new_phones or (not cg_phones and cg.get('name')):
            current_id += 1
            cg['id'] = current_id
            combined_db.append(cg)
            for p in new_phones:
                existing_phones.add(p)
            added_carigold += 1

    print(f"Added {added_carigold} new CariGold records to combined database.")
    print(f"Total merged records: {len(combined_db)}")

    # Save merged JSON
    with open('lowyat_scammers_db.json', 'w', encoding='utf-8') as f:
        json.dump(combined_db, f, indent=2, ensure_ascii=False)
    print("Saved combined records to lowyat_scammers_db.json")

    # Also save standalone CariGold JSON backup
    with open('carigold_only_scammers_db.json', 'w', encoding='utf-8') as f:
        json.dump(carigold_db, f, indent=2, ensure_ascii=False)

    # 5. Update Excel spreadsheet lowyat_scammers_db.xlsx
    try:
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.title = "Threat Database"
        headers = ["id", "name", "primary_phone", "all_phones", "bank_accounts", "ic_number", "address", "forum_handles", "evidence_links", "risk_tag", "source"]
        ws.append(headers)
        for r in combined_db:
            row = [r.get(h, '') for h in headers]
            ws.append(row)
        wb.save('lowyat_scammers_db.xlsx')
        print(f"Saved {len(combined_db)} rows to lowyat_scammers_db.xlsx")
    except Exception as e:
        print(f"Error saving xlsx: {e}")

    # 6. Rebuild SQLite database lowyat_threats.db
    db_file = 'lowyat_threats.db'
    conn = sqlite3.connect(db_file)
    cur = conn.cursor()
    cur.execute("DROP TABLE IF EXISTS threat_numbers;")
    cur.execute("""
    CREATE TABLE threat_numbers (
        phone_norm TEXT PRIMARY KEY,
        profile_id INTEGER,
        suspect_name TEXT,
        all_phones TEXT,
        bank_accounts TEXT,
        ic_number TEXT,
        address TEXT,
        evidence_links TEXT,
        risk_tag TEXT,
        source TEXT
    );
    """)

    indexed_count = 0
    for profile in combined_db:
        p_id = profile["id"]
        s_name = profile["name"]
        all_p = profile.get("all_phones", "")
        banks = profile.get("bank_accounts", "")
        ic = profile.get("ic_number", "")
        addr = profile.get("address", "")
        evid = profile.get("evidence_links", "")
        tag = profile.get("risk_tag", "HIGH_RISK_SCAMMER")
        src = profile.get("source", "Threat Intelligence")

        # Find all phone variations in profile
        all_text = all_p + " " + profile.get("primary_phone", "")
        phones_found = set(re.findall(r'(?:\+?60|0)[1-9]\d{7,10}', all_text))

        for raw_phone in phones_found:
            norm = normalize_digits(raw_phone)
            if norm and len(norm) >= 9:
                try:
                    cur.execute("""
                    INSERT OR REPLACE INTO threat_numbers 
                    (phone_norm, profile_id, suspect_name, all_phones, bank_accounts, ic_number, address, evidence_links, risk_tag, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                    """, (norm, p_id, s_name, all_p, banks, ic, addr, evid, tag, src))
                    indexed_count += 1
                except Exception as e:
                    pass

    conn.commit()
    print(f"Rebuilt SQLite database 'lowyat_threats.db' successfully!")
    print(f"Total threat phone numbers indexed: {indexed_count}")
    conn.close()

if __name__ == '__main__':
    merge_databases()
