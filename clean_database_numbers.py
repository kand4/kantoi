# -*- coding: utf-8 -*-
"""
Skrip untuk membuang nombor eksperimen daripada pangkalan data KANTOI:
1. 0139986583
2. 0193761223
"""

import sqlite3
import os
import re

TARGET_DBS = [
    os.path.join(os.path.dirname(__file__), "KantoiApp", "assets", "kantoi_threats.db"),
    os.path.join(os.path.dirname(__file__), "lowyat_threats.db")
]

NUMBERS_TO_DELETE = ["0139986583", "0193761223"]

for db_path in TARGET_DBS:
    if not os.path.exists(db_path):
        print(f"Skipping {db_path} (not found)")
        continue
        
    print(f"\n[DB] Memproses {db_path}...")
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    
    # 1. Semak jumlah rekod sebelum
    cur.execute("SELECT COUNT(*) FROM threat_numbers")
    count_before = cur.fetchone()[0]
    print(f"  Jumlah rekod asal: {count_before}")
    
    # 2. Padam baris utama
    for num in NUMBERS_TO_DELETE:
        cur.execute("SELECT phone_norm, suspect_name, risk_tag FROM threat_numbers WHERE phone_norm = ?", (num,))
        rows = cur.fetchall()
        for r in rows:
            print(f"  -> Memadam entri: {r[0]} | {r[1]} ({r[2]})")
        cur.execute("DELETE FROM threat_numbers WHERE phone_norm = ?", (num,))
        print(f"  Dipadam {cur.rowcount} baris untuk {num}.")
        
    # 3. Bersihkan sebutan nombor dalam all_phones (cth: profile 78)
    cur.execute("SELECT phone_norm, all_phones FROM threat_numbers WHERE all_phones LIKE '%0193761223%' OR all_phones LIKE '%0139986583%'")
    to_update = cur.fetchall()
    for p_norm, all_p in to_update:
        cleaned_list = []
        for p in all_p.split(","):
            p_clean = p.strip()
            digits = re.sub(r"[^\d]", "", p_clean)
            if not any(target in digits for target in NUMBERS_TO_DELETE):
                cleaned_list.append(p_clean)
        new_all_phones = ", ".join(cleaned_list)
        cur.execute("UPDATE threat_numbers SET all_phones = ? WHERE phone_norm = ?", (new_all_phones, p_norm))
        print(f"  -> Dikemaskini all_phones bagi {p_norm}: '{new_all_phones}'")
        
    con.commit()
    
    # 4. Semak jumlah rekod selepas
    cur.execute("SELECT COUNT(*) FROM threat_numbers")
    count_after = cur.fetchone()[0]
    print(f"  Jumlah rekod terkini: {count_after} (Pengurangan: {count_before - count_after})")
    
    # 5. VACUUM
    print("  Menjalankan VACUUM untuk mengoptimumkan saiz...")
    cur.execute("VACUUM")
    con.close()

print("\n[SUCCESS] Pembersihan pangkalan data selesai sepenuhnya!")
