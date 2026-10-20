import sqlite3
import os

db_path = os.path.join("KantoiApp", "assets", "kantoi_threats.db")
output_path = "KANTOI_Threat_Intelligence_Citations.txt"

conn = sqlite3.connect(db_path)
cursor = conn.cursor()
cursor.execute("""
    SELECT phone_norm, suspect_name, risk_tag, bank_accounts, ic_number, address, source, evidence_links 
    FROM threat_numbers 
    ORDER BY suspect_name ASC, phone_norm ASC
""")
rows = cursor.fetchall()

with open(output_path, "w", encoding="utf-8") as f:
    f.write("=" * 80 + "\n")
    f.write("      KANTOI ANTI-SCAM THREAT INTELLIGENCE DATABASE - LAPORAN LENGKAP & SITASI\n")
    f.write(f"      Jumlah Rekod   : {len(rows):,} Entri Keselamatan Komuniti\n")
    f.write("      Pangkalan Data : kantoi_threats.db (Version 8)\n")
    f.write("      Saluran Telegram : @crowdcaller\n")
    f.write("=" * 80 + "\n\n")

    for idx, r in enumerate(rows, 1):
        phone, suspect, tag, bank, ic, addr, source, evidence = r
        f.write(f"[{idx:,}] 🚨 NOMBOR TELEFON : {phone}\n")
        f.write(f"      👤 NAMA SUSPEK    : {suspect or 'N/A'}\n")
        f.write(f"      ⚠️ KLASIFIKASI    : {tag or 'N/A'}\n")
        if bank and bank != "N/A" and bank.strip():
            f.write(f"      🏦 AKAUN BANK     : {bank}\n")
        if ic and ic.strip():
            f.write(f"      🪪 NO. K/P         : {ic}\n")
        if addr and addr != "N/A" and addr.strip():
            f.write(f"      📍 ALAMAT         : {addr}\n")
        f.write(f"      📚 SUMBER SITASI  : {source or 'Pangkalan Data KANTOI'}\n")
        if evidence and evidence != "N/A" and evidence.strip():
            f.write(f"      🔗 PAUTAN BUKTI   : {evidence}\n")
        f.write("-" * 80 + "\n")

print(f"Export selesai: {output_path} ({len(rows)} rekod, saiz: {os.path.getsize(output_path):,} bytes)")
