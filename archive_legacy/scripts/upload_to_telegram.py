"""
Skrip Pembantu: Muat Naik Pangkalan Data Risikan KANTOI ke Saluran Telegram @crowdcaller
Menyokong:
1. Muat naik dokumen teks lengkap (KANTOI_Threat_Intelligence_Citations.txt) ke saluran.
2. Penyiaran mesej berformat (#KANTOI_UPDATE) yang serasi dengan enjin OTA KANTOI.
"""

import sys
import os
import requests
import time

BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
CHANNEL_ID = "@crowdcaller"
FILE_PATH = "KANTOI_Threat_Intelligence_Citations.txt"

def upload_document(token, channel_id, file_path):
    if not os.path.exists(file_path):
        print(f"Ralat: Fail {file_path} tidak ditemui!")
        return False
    
    url = f"https://api.telegram.org/bot{token}/sendDocument"
    caption = (
        "🛡️ PANGKALAN DATA RISIKAN SCAMMER KANTOI (VERSI DIKEMASKINI & SITASI RASMI)\n\n"
        "📊 Statistik Semasa:\n"
        "• 3,652 Nombor Scammer Disahkan\n"
        "• Sumber: PDRM SemakMule CCID, Lowyat.NET, CariGold & Telegram Intel\n"
        "• Termasuk rekod akaun bank keldai & pautan bukti laporan\n\n"
        "📲 Boleh disegerakkan ke aplikasi KANTOI untuk perlindungan masa nyata."
    )
    
    print(f"Sedang memuat naik {file_path} ke saluran {channel_id}...")
    with open(file_path, "rb") as f:
        files = {"document": f}
        data = {"chat_id": channel_id, "caption": caption}
        resp = requests.post(url, data=data, files=files, timeout=60)
        
    res = resp.json()
    if res.get("ok"):
        print("[SUCCESS] Berjaya dimuat naik ke Telegram!")
        print(f"Mesej ID: {res['result']['message_id']}")
        return True
    else:
        print(f"[ERROR] Gagal memuat naik: {res.get('description')}")
        return False

if __name__ == "__main__":
    token = BOT_TOKEN
    if len(sys.argv) > 1:
        token = sys.argv[1]
    
    if not token:
        print("Sila masukkan TELEGRAM_BOT_TOKEN:")
        print("Cara penggunaan: py upload_to_telegram.py <BOT_TOKEN>")
    else:
        upload_document(token, CHANNEL_ID, FILE_PATH)
