import json
import sqlite3
import re
import openpyxl

# 1. Load lowyat_scammers_db.json
with open('lowyat_scammers_db.json', 'r', encoding='utf-8') as f:
    db = json.load(f)

print(f"Current records in lowyat_scammers_db.json: {len(db)}")

# 2. Check if new records already exist
existing_phones = set()
for r in db:
    for p in re.findall(r'\d{9,11}', r.get('all_phones', '') + ' ' + r.get('primary_phone', '')):
        existing_phones.add(p)

new_records = [
    {
        "id": 79,
        "name": "Paih Lee / Helmet Original Auction Impersonator Alert",
        "primary_phone": "+60148206582",
        "all_phones": "+60148206582, 0148206582",
        "bank_accounts": "N/A (Bidaan Facebook / Peringatan Scammer)",
        "ic_number": "N/A",
        "address": "Facebook Group: Auction helmet original sahaja",
        "forum_handles": "Paih Lee",
        "evidence_links": "https://www.facebook.com/groups/174308421464709/",
        "risk_tag": "COMMUNITY_FLAGGED_FACEBOOK",
        "source": "Facebook Crowdsource Scammer Alert via Bing Dorking"
    },
    {
        "id": 80,
        "name": "GSAM ID 202101043 / Auction Bidding Suspect",
        "primary_phone": "+60172586463",
        "all_phones": "+60172586463, 0172586463",
        "bank_accounts": "N/A (Bidaan Facebook / GSAM Auction Risk)",
        "ic_number": "N/A",
        "address": "Facebook Group: G.S AUCTION MSIA (GSAM)",
        "forum_handles": "GSAM ID 202101043",
        "evidence_links": "https://www.facebook.com/groups/840671266713788/posts/2302281733886060/",
        "risk_tag": "COMMUNITY_FLAGGED_FACEBOOK",
        "source": "Facebook Crowdsource Scammer Alert via Bing Dorking"
    }
]

added_count = 0
for nr in new_records:
    norm_phone = nr["primary_phone"].replace("+60", "0").replace("+", "")
    if norm_phone not in existing_phones:
        db.append(nr)
        existing_phones.add(norm_phone)
        added_count += 1
        print(f"Added new profile [{nr['id']}]: {nr['name']} ({nr['primary_phone']})")

with open('lowyat_scammers_db.json', 'w', encoding='utf-8') as f:
    json.dump(db, f, indent=2, ensure_ascii=False)

print(f"Updated lowyat_scammers_db.json saved. Total records: {len(db)}")

# 3. Update lowyat_scammers_db.xlsx
try:
    wb = openpyxl.load_workbook('lowyat_scammers_db.xlsx')
    sheet = wb.active
    
    # Get existing IDs in xlsx
    existing_xlsx_ids = set()
    for row in range(2, sheet.max_row + 1):
        val = sheet.cell(row, 1).value
        if val is not None:
            existing_xlsx_ids.add(val)
            
    headers = [sheet.cell(1, c).value for c in range(1, sheet.max_column + 1)]
    
    for nr in new_records:
        if nr['id'] not in existing_xlsx_ids:
            row_data = [nr.get(h, 'N/A') for h in headers]
            sheet.append(row_data)
            print(f"Appended to xlsx: {nr['name']}")
            
    wb.save('lowyat_scammers_db.xlsx')
    print("Updated lowyat_scammers_db.xlsx successfully.")
except Exception as e:
    print(f"Error updating xlsx: {e}")

# 4. Rebuild SQLite database lowyat_threats.db
db_file = 'lowyat_threats.db'
conn = sqlite3.connect(db_file)
cur = conn.cursor()

# Drop existing table and rebuild cleanly
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

def extract_variants(raw_phone_str):
    variants = set()
    if not raw_phone_str or raw_phone_str == 'N/A':
        return variants
    
    # Split by comma, semicolon, slash, or space
    parts = re.split(r'[,;/|\n]+', raw_phone_str)
    for part in parts:
        digits = re.sub(r'[^0-9]', '', part.strip())
        if len(digits) < 8:
            continue
        
        # Format 1: 01...
        if digits.startswith('60'):
            v_local = '0' + digits[2:]
            v_int = digits
        elif digits.startswith('0'):
            v_local = digits
            v_int = '6' + digits
        elif digits.startswith('1'):
            v_local = '0' + digits
            v_int = '60' + digits
        else:
            v_local = digits
            v_int = digits
            
        variants.add(v_local)
        variants.add(v_int)
    return variants

indexed_variants = 0
for record in db:
    p_id = record.get('id', 0)
    name = record.get('name', '')
    all_p = record.get('all_phones', '')
    primary_p = record.get('primary_phone', '')
    bank_acc = record.get('bank_accounts', '')
    ic = record.get('ic_number', '')
    addr = record.get('address', '')
    links = record.get('evidence_links', '')
    risk = record.get('risk_tag', '')
    src = record.get('source', '')
    
    # Collect all phone variants
    combined_phones = f"{primary_p}, {all_p}"
    variants = extract_variants(combined_phones)
    
    for v in variants:
        try:
            cur.execute("""
            INSERT OR REPLACE INTO threat_numbers (
                phone_norm, profile_id, suspect_name, all_phones, bank_accounts,
                ic_number, address, evidence_links, risk_tag, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (v, p_id, name, all_p, bank_acc, ic, addr, links, risk, src))
            indexed_variants += 1
        except Exception as e:
            print(f"Error inserting variant {v} for profile {p_id}: {e}")

conn.commit()

cur.execute("SELECT count(*) FROM threat_numbers;")
total_threat_rows = cur.fetchone()[0]
print(f"SQLite lowyat_threats.db rebuilt successfully!")
print(f"Total threat_numbers entries: {total_threat_rows} (Indexed variants across {len(db)} profiles)")

# Quick sanity check for the two new numbers and Solihin Amran
for test_p in ['0148206582', '60148206582', '0172586463', '60172586463', '0139986583']:
    cur.execute("SELECT phone_norm, suspect_name, risk_tag FROM threat_numbers WHERE phone_norm = ?", (test_p,))
    row = cur.fetchone()
    print(f"Lookup '{test_p}' -> {row}")

conn.close()
