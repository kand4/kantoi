# -*- coding: utf-8 -*-
"""
Sync KenaScam to KANTOI Threat Database
Menggunakan rangkaian Tor (SOCKS5 127.0.0.1:9150) untuk perlindungan privasi dan IP penuh.
Menyedut data kes scammer dari kenascam.com, mengekstrak nombor telefon & akaun bank,
dan mengimportnya ke dalam pangkalan data SQLite KANTOI (kantoi_threats.db).
"""

import os
import re
import sys
import time
import json
import sqlite3
import subprocess
import html
import shutil
import io

if sys.stdout and hasattr(sys.stdout, 'buffer'):
    try:
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace', line_buffering=True)
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace', line_buffering=True)
    except Exception:
        pass

TOR_PROXY = "127.0.0.1:9150"
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "kantoi_threats.db")
ASSETS_DB_PATH = os.path.join(BASE_DIR, "KantoiApp", "assets", "kantoi_threats.db")
SERVER_DB_PATH = os.path.join(BASE_DIR, "Kantoi_Win7_Server", "kantoi_threats.db")

VAULT_FILE = os.path.join(BASE_DIR, "kenascam_vault.json")
CHECKPOINT_FILE = os.path.join(BASE_DIR, "kenascam_checkpoint.json")

def check_tor_connection():
    """Mengesahkan sambungan Tor aktif dan memaparkan IP Exit"""
    print("[*] Memeriksa sambungan ke rangkaian Tor (127.0.0.1:9150)...")
    cmd = [
        "curl.exe", "--socks5-hostname", TOR_PROXY,
        "-s", "--connect-timeout", "10",
        "https://check.torproject.org/api/ip"
    ]
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15)
        if res.returncode == 0 and res.stdout:
            data = json.loads(res.stdout)
            if data.get("IsTor"):
                print(f"[+] Sambungan Tor BERJAYA! IP Tor Exit anda: {data.get('IP')}")
                return True
            else:
                print(f"[!] Amaran: Sambungan berjaya tetapi bukan IP Tor: {res.stdout}")
                return True
        else:
            print(f"[-] Gagal berhubung dengan Tor: {res.stderr}")
            return False
    except Exception as e:
        print(f"[-] Ralat memeriksa Tor: {e}")
        return False

def fetch_page_via_tor(page, per_page=50):
    """Memuat turun satu muka surat JSON pos daripada KenaScam melalui Tor"""
    url = f"https://kenascam.com/wp-json/wp/v2/posts?per_page={per_page}&page={page}"
    temp_json = os.path.join(BASE_DIR, f"temp_page_{page}.json")
    cmd = [
        "curl.exe", "--socks5-hostname", TOR_PROXY,
        "-s", "--connect-timeout", "25", "--max-time", "60",
        "-A", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "-o", temp_json,
        "-w", "%{http_code}",
        url
    ]
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=75)
        http_code = res.stdout.strip()
        if http_code == "200" and os.path.exists(temp_json):
            with open(temp_json, "r", encoding="utf-8") as f:
                data = json.load(f)
            try: os.unlink(temp_json)
            except: pass
            return data
        else:
            print(f"[!] Muka surat {page} mengembalikan HTTP code: {http_code}")
            if os.path.exists(temp_json):
                try: os.unlink(temp_json)
                except: pass
            return None
    except Exception as e:
        print(f"[-] Ralat muat turun muka surat {page}: {e}")
        if os.path.exists(temp_json):
            try: os.unlink(temp_json)
            except: pass
        return None

def extract_threat_entities(post):
    """
    Mengekstrak entiti ancaman daripada pos:
    - Nombor Telefon
    - Akaun Bank & DuitNow
    - Nama Suspek / Mangsa
    - Pautan & Modus
    """
    post_id = post.get("id")
    title = html.unescape(post.get("title", {}).get("rendered", "")).strip()
    link = post.get("link", "")
    raw_content = post.get("content", {}).get("rendered", "")
    
    clean_text = re.sub(r'<[^>]+>', ' ', raw_content)
    clean_text = html.unescape(clean_text)
    clean_text = re.sub(r'\s+', ' ', clean_text).strip()
    
    combined = title + " \n " + clean_text
    
    # 1. Phone extraction
    raw_phones = re.findall(r'(?:(?:\+?60|0)1[0-46-9]-?\d{7,8}|0[3-9]-?\d{6,8})', combined)
    valid_phones = []
    for p in raw_phones:
        digits = re.sub(r'[^\d]', '', p)
        if digits.startswith("60"):
            digits = "0" + digits[2:]
        if len(digits) in [10, 11] and digits.startswith("01"):
            valid_phones.append(digits)
    valid_phones = sorted(list(set(valid_phones)))
    
    # 2. Bank extraction
    # Malaysian banks: 8 to 16 digits
    candidates = re.findall(r'\b\d{8,16}\b', combined)
    bank_accs = []
    for c in candidates:
        # Tapis tahun 2010-2029 dan nombor telefon
        if c not in valid_phones and not (c.startswith("201") or c.startswith("202")) and len(c) >= 9:
            bank_accs.append(c)
    bank_accs = sorted(list(set(bank_accs)))
    
    # 3. Bank names tagging if found
    bank_labels = []
    for b in bank_accs:
        # Cuba kesan nama bank berhampiran nombor akaun
        bank_match = re.search(r'([A-Za-z ]{3,15})\s*[:=-]?\s*' + re.escape(b), combined, re.IGNORECASE)
        b_name = bank_match.group(1).strip() if bank_match else ""
        if any(known in b_name.lower() for known in ['maybank', 'cimb', 'bsn', 'rhb', 'islam', 'public', 'hong', 'tng', 'duitnow']):
            bank_labels.append(f"{b_name.upper()} : {b}")
        else:
            bank_labels.append(b)
            
    bank_str = " | ".join(bank_labels) if bank_labels else "N/A"
    
    # 4. Suspect name
    suspect_name = ""
    name_m = re.search(r'(?:nama|name|atas nama|pemilik|owner)\s*[:=-]?\s*([A-Za-z@/\'\. ]{4,40})', combined, re.IGNORECASE)
    if name_m:
        suspect_name = name_m.group(1).strip()
    elif title:
        suspect_name = title[:50]
        
    return {
        "id": post_id,
        "title": title,
        "link": link,
        "phones": valid_phones,
        "bank_accounts": bank_str,
        "suspect_name": suspect_name,
        "description": clean_text[:250]
    }

def import_to_sqlite(entities_list):
    """Memasukkan atau mengemaskini rekod ancaman ke dalam kantoi_threats.db"""
    if not os.path.exists(DB_PATH):
        print(f"[-] Pangkalan data SQLite tidak ditemui di: {DB_PATH}")
        return 0
        
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    
    new_inserts = 0
    updated_records = 0
    
    for item in entities_list:
        phones = item["phones"]
        banks = item["bank_accounts"]
        suspect = item["suspect_name"]
        link = item["link"]
        source_cite = f"KenaScam.com (Kes #{item['id']})"
        risk_tag = "KENASCAM_VERIFIED"
        
        # Kes A: Mempunyai nombor telefon
        if phones:
            primary_phone = phones[0]
            all_phones_str = ", ".join(phones)
            
            # Semak jika nombor telefon telah wujud
            cur.execute("SELECT phone_norm, bank_accounts, evidence_links, suspect_name FROM threat_numbers WHERE phone_norm = ?", (primary_phone,))
            row = cur.fetchone()
            
            if row:
                # Rekod telah wujud, kemaskini maklumat bank dan bukti
                existing_banks = row[1] or ""
                existing_links = row[2] or ""
                
                merged_banks = existing_banks
                if banks != "N/A" and banks not in existing_banks:
                    merged_banks = f"{existing_banks} | {banks}".strip(" |")
                    
                merged_links = existing_links
                if link and link not in existing_links:
                    merged_links = f"{existing_links} | {link}".strip(" |")
                    
                cur.execute("""
                    UPDATE threat_numbers 
                    SET bank_accounts = ?, evidence_links = ?, all_phones = ?
                    WHERE phone_norm = ?
                """, (merged_banks, merged_links, all_phones_str, primary_phone))
                updated_records += 1
            else:
                # Masukkan rekod baharu
                cur.execute("""
                    INSERT INTO threat_numbers (phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (primary_phone, suspect, risk_tag, banks, "Malaysia", source_cite, link, all_phones_str))
                new_inserts += 1
                
        # Kes B: Tiada nombor telefon, tetapi mempunyai akaun bank
        elif banks and banks != "N/A":
            # Gunakan digit akaun bank pertama sebagai identifier
            first_bank_digits = re.search(r'\b\d{8,16}\b', banks)
            if first_bank_digits:
                bank_key = first_bank_digits.group(0)
                cur.execute("SELECT phone_norm FROM threat_numbers WHERE phone_norm = ?", (bank_key,))
                if not cur.fetchone():
                    cur.execute("""
                        INSERT INTO threat_numbers (phone_norm, suspect_name, risk_tag, bank_accounts, address, source, evidence_links, all_phones)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, (bank_key, suspect, risk_tag, banks, "Malaysia", source_cite, link, ""))
                    new_inserts += 1
                    
    conn.commit()
    conn.close()
    
    # Salin pangkalan data ke KantoiApp assets dan Server jika ada perubahan
    if new_inserts > 0 or updated_records > 0:
        try:
            if os.path.exists(os.path.dirname(ASSETS_DB_PATH)):
                shutil.copy2(DB_PATH, ASSETS_DB_PATH)
            if os.path.exists(os.path.dirname(SERVER_DB_PATH)):
                shutil.copy2(DB_PATH, SERVER_DB_PATH)
        except Exception as e:
            print(f"[!] Amaran salinan pangkalan data: {e}")
            
    return new_inserts, updated_records

def main():
    target_count = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    per_page = 50
    total_pages = (target_count + per_page - 1) // per_page
    
    print("=" * 60)
    print("   PENYEDUT PINTAR KENASCAM.COM -> KANTOI THREAT ENGINE")
    print("   Mod Privasi Tor: 127.0.0.1:9150 (SOCKS5)")
    print(f"   Sasaran: {target_count} Kes Penipuan Terkini ({total_pages} Muka Surat)")
    print("=" * 60)
    
    if not check_tor_connection():
        print("[-] Ralat: Sila pastikan Tor Browser dibuka dan berjalan di komputer anda.")
        return
        
    vault = {}
    if os.path.exists(VAULT_FILE):
        try:
            with open(VAULT_FILE, "r", encoding="utf-8") as f:
                vault = json.load(f)
            print(f"[*] Menemui {len(vault)} rekod sedia ada dalam arkib tempatan ({VAULT_FILE}).")
        except:
            vault = {}
            
    checkpoint = {"last_page": 0}
    if os.path.exists(CHECKPOINT_FILE):
        try:
            with open(CHECKPOINT_FILE, "r", encoding="utf-8") as f:
                checkpoint = json.load(f)
            print(f"[*] Melanjutkan dari muka surat terakhir: {checkpoint.get('last_page', 0)}")
        except:
            checkpoint = {"last_page": 0}
            
    start_page = checkpoint.get("last_page", 0) + 1
    end_page = min(total_pages, 67) # Maksimum 67 muka surat di KenaScam
    
    if start_page > end_page:
        print("[+] Sasaran 1000 rekod telah pun selesai dicapai sebelum ini!")
        return
        
    total_new_db = 0
    total_updated_db = 0
    start_time = time.time()
    
    for page in range(start_page, end_page + 1):
        p_t0 = time.time()
        pct = round((page / end_page) * 100, 1)
        bar = '█' * int(pct // 5) + '░' * (20 - int(pct // 5))
        print(f"\n[{bar}] {pct}% | [PAGE {page}/{end_page}] Memuat turun 50 pos melalui Tor...", end=" ", flush=True)
        posts = fetch_page_via_tor(page, per_page=per_page)
        
        if not posts:
            print("[GAGAL] Mencuba semula dalam 5 saat...")
            time.sleep(5)
            posts = fetch_page_via_tor(page, per_page=per_page)
            if not posts:
                print(f"[!] Melepaskan muka surat {page} akibat ralat rangkaian Tor.")
                continue
                
        p_time = round(time.time() - p_t0, 1)
        print(f"[OK ({len(posts)} pos dlm {p_time}s)]")
        
        # Ekstrak entiti
        entities = []
        for p in posts:
            pid = str(p.get("id"))
            vault[pid] = {
                "id": p.get("id"),
                "date": p.get("date"),
                "title": p.get("title", {}).get("rendered"),
                "link": p.get("link"),
                "content": p.get("content", {}).get("rendered")
            }
            ent = extract_threat_entities(p)
            entities.append(ent)
            
        # Simpan arkib tempatan
        with open(VAULT_FILE, "w", encoding="utf-8") as f:
            json.dump(vault, f, ensure_ascii=False)
            
        # Import ke SQLite
        new_cnt, upd_cnt = import_to_sqlite(entities)
        total_new_db += new_cnt
        total_updated_db += upd_cnt
        
        # Kemaskini checkpoint
        checkpoint["last_page"] = page
        checkpoint["total_in_vault"] = len(vault)
        with open(CHECKPOINT_FILE, "w", encoding="utf-8") as f:
            json.dump(checkpoint, f, indent=2)
            
        # Dapatkan jumlah terkini dalam DB
        conn = sqlite3.connect(DB_PATH)
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM threat_numbers")
        current_db_total = cur.fetchone()[0]
        conn.close()
        
        print(f"    -> Rekod Baharu SQLite: +{new_cnt} | Dikemaskini: +{upd_cnt} | Jumlah DB Terkini: {current_db_total:,}")
        
        # Jeda rehat sopan 2.5 saat
        time.sleep(2.5)
        
    elapsed = round(time.time() - start_time, 1)
    print("\n" + "=" * 60)
    print(f"🎉 SELESAI! Sedutan 1,000 Data Berjaya Melalui Tor.")
    print(f"⏱️ Masa Diambil   : {elapsed} saat")
    print(f"📦 Rekod Arkib   : {len(vault)} kes disimpan dalam kenascam_vault.json")
    print(f"🆕 Rekod Baharu  : +{total_new_db} nombor/akaun disuntik ke SQLite")
    print(f"🔄 Dikemaskini   : +{total_updated_db} rekod diselaraskan")
    print("=" * 60)

if __name__ == "__main__":
    main()
