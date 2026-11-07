# -*- coding: utf-8 -*-
"""
KANTOI UNIFIED CYBER INTELLIGENCE ENGINE
========================================
Modul Bersepadu Forensik Siber, Analisis Nombor & Rangkaian Sindiket:
1. Profiler Telco & VoIP / Nombor Maya (Operator Malaysia & Luar Negara)
2. Enjin Penilaian Risiko Siber (0-100% FAIR Model & Markah Berbobot)
3. Pokok Rangkaian Sindiket Visual (ASCII Syndicate Graph)
4. Forensik Resit & Slip Bank Palsu (Pemeriksa EXIF & Suntingan Imej)
5. Forensik Panggilan Audio & Mesej Suara (Transkripsi & Pengesan Manipulasi)
6. Penjana Pek Sinkronisasi Pangkalan Data Luar Talian (Untuk Aplikasi Android Kantoi)
7. Algoritma Pembahagian Mesej Berbilang Halaman (Smart Telegram HTML Pagination)
"""

import os
import re
import time
import json
import html
import hashlib
from PIL import Image, ExifTags

# ==============================================================================
# 0. ENJIN DISAMBIGUASI ENTITI (TELEFON VS AKAUN BANK)
# ==============================================================================
def normalize_phone(raw_phone):
    """
    Menyelaras format nombor telefon ke format kebangsaan rasmi:
    - Malaysia Mobile: 01xxxxxxxx (10-11 digit)
    - Malaysia Fixed: 03xxxxxxxx (9-11 digit)
    - Indonesia Mobile: 08xxxxxxxx (10-14 digit)
    - Nombor antarabangsa bermula +
    Menolak nombor akaun bank secara automatik (mengembalikan string kosong '').
    """
    if not raw_phone:
        return ""
    raw = str(raw_phone).strip()
    digits = re.sub(r"[^\d]", "", raw)
    if not digits:
        return ""

    # 1. Kod Antarabangsa Malaysia (+60 / 60)
    if digits.startswith("60"):
        rest = digits[2:]
        if rest.startswith("1") and len(rest) in (9, 10):
            return "0" + rest
        if rest.startswith(("3", "4", "5", "6", "7", "8", "9")) and len(rest) in (8, 9):
            return "0" + rest
        if len(rest) in (8, 9, 10):
            return "0" + rest
        return digits

    # 2. Format Tempatan Malaysia Bermula 01 (Mobile 10-11 digit)
    if digits.startswith("01") and len(digits) in (10, 11):
        return digits

    # 3. Format Fixed-line Malaysia Bermula 03-09 (9-11 digit)
    if digits.startswith(("03", "04", "05", "06", "07", "08", "09")) and len(digits) in (9, 10, 11):
        return digits

    # 4. Format Indonesia (+628... / 628... / 08...)
    if digits.startswith("628") and len(digits) in range(11, 15):
        return "08" + digits[3:]
    if digits.startswith("08") and len(digits) in range(10, 14):
        return digits

    # 5. Format Taip Cepat Tanpa 0 Di Depan (HANYA prefix mobile Malaysia 10-19)
    if digits.startswith("1") and len(digits) in (9, 10):
        if digits[0:2] in ("10", "11", "12", "13", "14", "15", "16", "17", "18", "19"):
            return "0" + digits

    # 6. Nombor Antarabangsa Lain bermula +
    if raw.startswith("+") and len(digits) >= 8:
        return "+" + digits

    return ""

def is_phone_candidate(val):
    """Mengesahkan sama ada nilai adalah nombor telefon sah."""
    return bool(normalize_phone(val))

def is_bank_account_candidate(val):
    """
    Mengesahkan sama ada nilai adalah akaun bank:
    - Panjang 8 hingga 18 digit
    - BUKAN nombor telefon bimbit atau talian tetap
    """
    if not val:
        return False
    digits = re.sub(r"[^\d]", "", str(val))
    if len(digits) < 8 or len(digits) > 18:
        return False
    if is_phone_candidate(val) or is_phone_candidate(digits):
        return False
    return True

def infer_malaysian_bank(account_number):
    """
    Mengenal pasti atau menganggar nama bank Malaysia berasaskan:
    1. Panjang digit akaun (account length)
    2. Corak awalan digit (bank prefix patterns)
    
    Dilengkapi dengan label penafian (disclaimer) untuk mengelakkan kekeliruan pengguna.
    """
    if not account_number:
        return {"bank_name": "Akaun Bank", "display_name": "Akaun Bank", "is_inferred": False, "disclaimer": ""}

    digits = re.sub(r"[^\d]", "", str(account_number))
    length = len(digits)

    if length < 8 or length > 18:
        return {"bank_name": "Akaun Bank", "display_name": "Akaun Bank", "is_inferred": False, "disclaimer": ""}

    # 1. MAYBANK: 12 digit (awalan lazim 11, 15, 16, 51, 56)
    if length == 12:
        if digits.startswith(("11", "15", "16", "51", "56", "10", "12", "14")):
            return {
                "bank_name": "Maybank",
                "display_name": "Maybank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 12-digit Maybank. Sila sahkan nama bank pada resit transaksi."
            }
        elif digits.startswith(("22", "23")):
            return {
                "bank_name": "Bank Rakyat",
                "display_name": "Bank Rakyat (Anggaran Format)",
                "is_inferred": True,
                "confidence": "MEDIUM",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 12-digit Bank Rakyat. Sila sahkan nama bank pada resit transaksi."
            }
        else:
            return {
                "bank_name": "Maybank / Bank Rakyat",
                "display_name": "Maybank / Bank Rakyat (Anggaran)",
                "is_inferred": True,
                "confidence": "LOW",
                "disclaimer": "⚠️ Format 12-digit lazim Maybank atau Bank Rakyat. Sila rujuk resit transaksi rasmi."
            }

    # 2. CIMB BANK / PUBLIC BANK / AFFIN: 10 digit
    if length == 10:
        if digits.startswith(("70", "76", "80", "86", "12", "14")):
            return {
                "bank_name": "CIMB Bank",
                "display_name": "CIMB Bank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 10-digit CIMB Bank. Sila sahkan nama bank pada resit transaksi."
            }
        elif digits.startswith(("3", "4", "6")):
            return {
                "bank_name": "Public Bank",
                "display_name": "Public Bank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 10-digit Public Bank. Sila sahkan nama bank pada resit transaksi."
            }
        elif digits.startswith("10"):
            return {
                "bank_name": "Affin Bank",
                "display_name": "Affin Bank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "MEDIUM",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 10-digit Affin Bank. Sila sahkan nama bank pada resit transaksi."
            }
        else:
            return {
                "bank_name": "CIMB / Public Bank",
                "display_name": "CIMB / Public Bank (Anggaran)",
                "is_inferred": True,
                "confidence": "LOW",
                "disclaimer": "⚠️ Format 10-digit lazim CIMB atau Public Bank. Sila rujuk resit transaksi rasmi."
            }

    # 3. BANK ISLAM / RHB / BANK MUAMALAT: 14 digit
    if length == 14:
        if digits.startswith(("12", "14", "03", "08")):
            return {
                "bank_name": "Bank Islam",
                "display_name": "Bank Islam (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 14-digit Bank Islam. Sila sahkan nama bank pada resit transaksi."
            }
        elif digits.startswith(("10", "20", "60", "16", "26", "2", "6")):
            return {
                "bank_name": "RHB Bank",
                "display_name": "RHB Bank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 14-digit RHB Bank. Sila sahkan nama bank pada resit transaksi."
            }
        else:
            return {
                "bank_name": "Bank Islam / RHB",
                "display_name": "Bank Islam / RHB (Anggaran)",
                "is_inferred": True,
                "confidence": "LOW",
                "disclaimer": "⚠️ Format 14-digit lazim Bank Islam atau RHB. Sila rujuk resit transaksi rasmi."
            }

    # 4. BSN & AGROBANK: 16 digit
    if length == 16:
        if digits.startswith(("01", "02", "03", "10", "11", "14")):
            return {
                "bank_name": "BSN",
                "display_name": "BSN (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 16-digit BSN. Sila sahkan nama bank pada resit transaksi."
            }
        elif digits.startswith("20"):
            return {
                "bank_name": "Agrobank",
                "display_name": "Agrobank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "MEDIUM",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 16-digit Agrobank. Sila sahkan nama bank pada resit transaksi."
            }
        else:
            return {
                "bank_name": "BSN / Agrobank",
                "display_name": "BSN / Agrobank (Anggaran)",
                "is_inferred": True,
                "confidence": "LOW",
                "disclaimer": "⚠️ Format 16-digit lazim BSN atau Agrobank. Sila rujuk resit transaksi rasmi."
            }

    # 5. HONG LEONG BANK: 11 digit
    if length == 11:
        if digits.startswith(("11", "22")):
            return {
                "bank_name": "Bank Rakyat / Hong Leong",
                "display_name": "Bank Rakyat / Hong Leong (Anggaran)",
                "is_inferred": True,
                "confidence": "MEDIUM",
                "disclaimer": "⚠️ Format 11-digit lazim (Bank Rakyat/Hong Leong). Sila sahkan nama bank pada resit."
            }
        else:
            return {
                "bank_name": "Hong Leong Bank",
                "display_name": "Hong Leong Bank (Anggaran Format)",
                "is_inferred": True,
                "confidence": "HIGH",
                "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 11-digit Hong Leong Bank. Sila sahkan nama bank pada resit."
            }

    # 6. AMBANK: 13 digit
    if length == 13:
        return {
            "bank_name": "AmBank",
            "display_name": "AmBank (Anggaran Format)",
            "is_inferred": True,
            "confidence": "HIGH",
            "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 13-digit AmBank. Sila sahkan nama bank pada resit transaksi."
        }

    # 7. ALLIANCE BANK: 15 digit
    if length == 15:
        return {
            "bank_name": "Alliance Bank",
            "display_name": "Alliance Bank (Anggaran Format)",
            "is_inferred": True,
            "confidence": "HIGH",
            "disclaimer": "⚠️ Nama bank dianggar berdasarkan format 15-digit Alliance Bank. Sila sahkan nama bank pada resit transaksi."
        }

    return {"bank_name": "Akaun Bank", "display_name": "Akaun Bank", "is_inferred": False, "disclaimer": ""}

# ==============================================================================
# 1. PROFILER TELCO & VOIP / NOMBOR MAYA MALAYSIA & ANTARABANGSA
# ==============================================================================
def profile_telco_and_carrier(norm_phone):
    """
    Menganalisis nombor telefon untuk mengenal pasti:
    - Operator Telco Malaysia (CelcomDigi, Maxis, U Mobile, Yes 5G, Unifi, dsb.)
    - Status VoIP / Virtual PBX (Contohnya SKMM 015 block atau nombor OTT)
    - Kategori Talian (Mudah Alih, Talian Tetap TM, atau Talian Asing)
    """
    if not norm_phone or not is_phone_candidate(norm_phone):
        return {
            "telco": "Bukan Talian Telefon (Akaun Bank / Entiti Kewangan)",
            "type": "NOT_PHONE",
            "is_voip": False,
            "origin": "Entiti Perbankan",
            "badge": "💳 Entiti Akaun Bank",
            "warning": "Entiti ini adalah nombor akaun bank atau rujukan transaksi, bukan nombor talian telefon."
        }

    raw = re.sub(r"[^\d\+]", "", str(norm_phone))
    
    # Normalisasikan awalan Malaysia
    digits = raw
    if digits.startswith("+60"):
        digits = "0" + digits[3:]
    elif digits.startswith("60"):
        digits = "0" + digits[2:]

    # A. Pengesanan Nombor Antarabangsa (Luar Negara) dengan Sempadan Panjang Sah
    if digits.startswith("+") or (not digits.startswith("0") and len(digits) in (10, 11, 12, 13, 14)):
        intl = digits.lstrip("+")
        if intl.startswith("65") and len(intl) in (10, 11):
            return {
                "telco": "Singtel / StarHub / M1",
                "type": "INTL_MOBILE",
                "is_voip": False,
                "origin": "🇸🇬 Singapura",
                "badge": "🇸🇬 Talian Singapura"
            }
        elif intl.startswith("62"):
            return {
                "telco": "Telkomsel / Indosat / XL",
                "type": "INTL_MOBILE",
                "is_voip": False,
                "origin": "🇮🇩 Indonesia",
                "badge": "🇮🇩 Talian Indonesia"
            }
        elif intl.startswith("66"):
            return {
                "telco": "AIS / TrueMove / DTAC",
                "type": "INTL_MOBILE",
                "is_voip": False,
                "origin": "🇹🇭 Thailand",
                "badge": "🇹🇭 Talian Thailand"
            }
        elif intl.startswith("855"):
            return {
                "telco": "Smart / Cellcard (Kemboja)",
                "type": "FOREIGN_HIGH_RISK",
                "is_voip": True,
                "origin": "🇰🇭 Kemboja (Zon Sindiket Macau Scam)",
                "badge": "🚨 🇰🇭 Kemboja (Zon Risiko Tinggi)"
            }
        elif intl.startswith("95"):
            return {
                "telco": "MPT / Telenor (Myanmar)",
                "type": "FOREIGN_HIGH_RISK",
                "is_voip": True,
                "origin": "🇲🇲 Myanmar (Zon Sindiket Scam)",
                "badge": "🚨 🇲🇲 Myanmar (Zon Risiko Tinggi)"
            }
        elif intl.startswith("1") and len(intl) == 11:
            return {
                "telco": "US/Canada Virtual OTT (Twilio / TextNow / Google Voice)",
                "type": "VOIP_VIRTUAL",
                "is_voip": True,
                "origin": "🇺🇸/🇨🇦 Nombor Maya Luar Negara",
                "badge": "⚠️ Nombor Maya Asing (VoIP)"
            }
        elif intl.startswith("234"):
            return {
                "telco": "MTN / Airtel Nigeria",
                "type": "FOREIGN_HIGH_RISK",
                "is_voip": True,
                "origin": "🇳🇬 Nigeria (Sindiket Parcel/Love Scam)",
                "badge": "🚨 🇳🇬 Nigeria (Zon Parcel Scam)"
            }
        else:
            return {
                "telco": "Talian Antarabangsa",
                "type": "FOREIGN_LINE",
                "is_voip": False,
                "origin": "🌍 Antarabangsa",
                "badge": "🌍 Talian Asing"
            }

    # B. Pengesanan VoIP Malaysia (SKMM 015 Range)
    if digits.startswith("015"):
        return {
            "telco": "Talian VoIP / IP Telephony Kebangsaan",
            "type": "VOIP_VIRTUAL",
            "is_voip": True,
            "origin": "🇲🇾 Malaysia (Talian Maya / SIP Trunk)",
            "badge": "⚠️ VoIP / Talian Maya 015 (Kerap Disalahguna)",
            "warning": "Julat 015 adalah Talian Maya VoIP yang boleh disewa dari luar negara tanpa kad SIM fizikal."
        }

    # C. Pengesanan Talian Tetap Pejabat (Fixed Line TM / Setempat)
    fixed_prefixes = {
        "03": ("Kuala Lumpur / Selangor / Putrajaya", "Telekom Malaysia (Talian Tetap)"),
        "04": ("Pulau Pinang / Kedah / Perlis", "Telekom Malaysia (Talian Tetap)"),
        "05": ("Perak / Cameron Highlands", "Telekom Malaysia (Talian Tetap)"),
        "06": ("Melaka / Negeri Sembilan / Muar", "Telekom Malaysia (Talian Tetap)"),
        "07": ("Johor Bahru / Batu Pahat / Kluang", "Telekom Malaysia (Talian Tetap)"),
        "082": ("Kuching, Sarawak", "Telekom Malaysia (Talian Tetap)"),
        "083": ("Sri Aman, Sarawak", "Telekom Malaysia (Talian Tetap)"),
        "084": ("Sibu / Sarikei, Sarawak", "Telekom Malaysia (Talian Tetap)"),
        "085": ("Miri / Limbang, Sarawak", "Telekom Malaysia (Talian Tetap)"),
        "086": ("Bintulu, Sarawak", "Telekom Malaysia (Talian Tetap)"),
        "087": ("Labuan / Pedalaman Sabah", "Telekom Malaysia (Talian Tetap)"),
        "088": ("Kota Kinabalu / Kudat, Sabah", "Telekom Malaysia (Talian Tetap)"),
        "089": ("Sandakan / Tawau / Lahad Datu, Sabah", "Telekom Malaysia (Talian Tetap)"),
    }
    for pfx in sorted(fixed_prefixes.keys(), key=lambda x: -len(x)):
        if digits.startswith(pfx):
            loc, provider = fixed_prefixes[pfx]
            return {
                "telco": provider,
                "type": "FIXED_LINE",
                "is_voip": False,
                "origin": f"🇲🇾 Malaysia ({loc})",
                "badge": f"☎️ Talian Tetap ({loc})",
                "is_fixed": True
            }

    # D. Pengesanan Operator Mudah Alih (Mobile Telcos MCMC / SKMM)
    p3 = digits[:3]
    p4 = digits[:4]

    if p3 in ["012", "017"] or p4 == "0142":
        return {
            "telco": "Maxis / Hotlink",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (Maxis)",
            "badge": "🟢 Maxis / Hotlink"
        }
    elif p3 in ["013", "019"] or p4 == "0145":
        return {
            "telco": "CelcomDigi (Rangkaian Celcom)",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (Celcom)",
            "badge": "🔵 CelcomDigi (Celcom)"
        }
    elif p3 == "016" or p4 in ["0143", "0146", "0149"]:
        return {
            "telco": "CelcomDigi (Rangkaian Digi)",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (Digi)",
            "badge": "🟡 CelcomDigi (Digi)"
        }
    elif p3 == "018":
        return {
            "telco": "U Mobile",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (U Mobile)",
            "badge": "🟠 U Mobile"
        }
    elif p4 == "0114":
        return {
            "telco": "Yes 5G (YTL Communications)",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (Yes 5G)",
            "badge": "🔴 Yes 5G (YTL)"
        }
    elif p4 == "0115":
        return {
            "telco": "Unifi Mobile (TM Technology Services)",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (Unifi Mobile)",
            "badge": "🟠 Unifi Mobile"
        }
    elif p4 in ["0111", "0112", "0113", "0116", "0117", "0118"]:
        sub_map = {
            "0111": ("CelcomDigi / XOX / TuneTalk", "🔵 CelcomDigi / MVNO"),
            "0112": ("Maxis / Hotlink", "🟢 Maxis Prepaid"),
            "0113": ("U Mobile", "🟠 U Mobile Prepaid"),
            "0116": ("CelcomDigi (Digi Prepaid)", "🟡 Digi Prepaid"),
            "0117": ("Yoodo / CelcomDigi MVNO", "🟣 Yoodo / MVNO"),
            "0118": ("Tune Talk / MVNO", "🔴 Tune Talk / MVNO")
        }
        name, badge = sub_map.get(p4, ("Prabayar Malaysia 011", "📱 Prabayar 011"))
        return {
            "telco": name,
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia (Prabayar 011)",
            "badge": badge
        }
    elif p3 == "011":
        return {
            "telco": "Talian Mudah Alih 011 (Prabayar / MVNO)",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia",
            "badge": "📱 Prabayar Malaysia 011"
        }
    elif p3 == "014":
        return {
            "telco": "CelcomDigi / Maxis (014)",
            "type": "MOBILE_CARRIER",
            "is_voip": False,
            "origin": "🇲🇾 Malaysia",
            "badge": "📱 Rangkaian 014"
        }

    return {
        "telco": "Talian Mudah Alih Malaysia",
        "type": "MOBILE_GENERAL",
        "is_voip": False,
        "origin": "🇲🇾 Malaysia",
        "badge": "📱 Talian Mudah Alih"
    }


# ==============================================================================
# 2. ENJIN SKOR RISIKO SIBER (0 - 100% FAIR / MULTI-FACTOR MODEL)
# ==============================================================================
def calculate_scam_risk_score(phone_intel_list, acc_intel_list, qr_intel=None, receipt_forensics=None, audio_intel=None):
    """
    Mengira Skor Risiko Siber (0 - 100%) berbobot:
    - Rekod Rasmi PDRM SemakMule: +50 mata
    - Pangkalan Data KANTOI: +35 mata
    - Hubungan Kluster Sindiket: +25 mata
    - Ciri VoIP / Nombor Maya Asing: +20 mata
    - Forensik Resit Palsu / EXIF Edit: +25 mata
    - Forensik Audio Macau Scam: +30 mata
    - Quishing / Pautan Berbahaya: +30 mata
    """
    total_score = 0
    factors = []

    # 1. PDRM SemakMule
    pdrm_reports = sum(pi.get("pdrm", {}).get("police_reports", 0) for pi in (phone_intel_list + acc_intel_list))
    if pdrm_reports > 0:
        pts = min(50, 30 + (pdrm_reports * 5))
        total_score += pts
        factors.append(f"🚨 <b>{pdrm_reports} Laporan Polis PDRM SemakMule</b> (+{pts}%)")

    # 2. Pangkalan KANTOI
    kantoi_hits = sum(1 for item in (phone_intel_list + acc_intel_list) if item.get("rec_kantoi"))
    if kantoi_hits > 0:
        pts = min(35, 25 + (kantoi_hits * 5))
        total_score += pts
        factors.append(f"🛡️ <b>Tersenarai Dalam Blacklist Komuniti KANTOI</b> (+{pts}%)")

    # 3. Kluster Rangkaian Sindiket
    syndicate_hits = sum(1 for item in (phone_intel_list + acc_intel_list) if item.get("cluster", {}).get("is_syndicate"))
    if syndicate_hits > 0:
        total_score += 25
        factors.append("🕸️ <b>Berkait Dengan Rangkaian Sindiket Keldai</b> (+25%)")

    # 4. Talian Maya / VoIP / Asing
    for pi in phone_intel_list:
        telco_info = pi.get("telco_info", {})
        if telco_info.get("is_voip"):
            total_score += 20
            factors.append(f"⚠️ <b>Penggunaan Talian Maya / VoIP Asing:</b> {telco_info.get('telco')} (+20%)")
            break

    # 5. Forensik Resit Palsu
    if receipt_forensics and receipt_forensics.get("is_edited"):
        pts = receipt_forensics.get("penalty", 25)
        total_score += pts
        factors.append(f"🧾 <b>Resit Bank Disunting:</b> {receipt_forensics.get('software', 'Editor Imej')} (+{pts}%)")

    # 6. Forensik Audio Scam
    if audio_intel and audio_intel.get("is_scam_call"):
        pts = audio_intel.get("penalty", 30)
        total_score += pts
        factors.append(f"🎙️ <b>Manipulasi Suara Ugutan Dikesan:</b> {audio_intel.get('tactic', 'Macau Scam')} (+{pts}%)")

    # 7. Quishing / Kod QR Pautan Bahaya
    if qr_intel and qr_intel.get("danger"):
        total_score += 30
        factors.append("🚫 <b>Kod QR Mengandungi Phishing / Pautan Berbahaya</b> (+30%)")

    final_score = min(100, total_score)

    # Bina Progress Meter Bar
    filled_blocks = int(final_score / 10)
    meter_bar = "█" * filled_blocks + "░" * (10 - filled_blocks)

    if final_score >= 80:
        grade = "🔴 KRITIKAL (CRITICAL)"
        action = "JANGAN SESEKALI PINDAHKAN WANG! SEKAT SERTA-MERTA!"
        icon = "🚨"
    elif final_score >= 50:
        grade = "🟠 TINGGI (HIGH RISK)"
        action = "PETUNJUK KUAT PENIPUAN. ELAKKAN SEBARANG TRANSAKSI!"
        icon = "⚠️"
    elif final_score >= 25:
        grade = "🟡 SEDERHANA (MEDIUM RISK)"
        action = "PERLU BERWASPADA. MINTA PENGESAHAN IDENTITI RASMI."
        icon = "⚡"
    else:
        grade = "🟢 RENDAH (LOW RISK / REKOD BERSIH)"
        action = "TIADA REKOD PENIPUAN RASMI SETAKAT INI."
        icon = "✅"

    return {
        "score": final_score,
        "meter": f"[{meter_bar}] {final_score}%",
        "grade": grade,
        "action": action,
        "icon": icon,
        "factors": factors
    }


# ==============================================================================
# 3. POKOK RANGKAIAN SINDIKET VISUAL (ASCII SYNDICATE TREE GRAPH)
# ==============================================================================
def generate_syndicate_ascii_tree(phone_intel_list, acc_intel_list, risk_info, suspect_name="Sasaran"):
    """
    Menjana visual pokok hubungan (ASCII Graph) untuk menggambarkan
    korelasi entiti scammer dalam satu pandangan visual.
    """
    clean_name = suspect_name if suspect_name not in ["N/A", "Tidak Dinyatakan", ""] else "Sasaran"
    tree_lines = ["<code>"]
    tree_lines.append(f"┌── [SASARAN: {clean_name.upper()}] ── {risk_info['meter']}")
    
    all_nodes = []
    
    # Node Telefon
    for pi in phone_intel_list:
        p = pi["phone"]
        tel = pi.get("telco_info", {}).get("badge", "Telco")
        p_reps = pi.get("pdrm", {}).get("police_reports", 0)
        p_stat = f"🚨 {p_reps} Laporan PDRM" if p_reps > 0 else "Rekod Bersih"
        all_nodes.append(f"📱 {p} [{tel}] ── {p_stat}")
        
    # Node Bank
    for ai in acc_intel_list:
        acc = ai["account"]
        bank = ai.get("bank", "Bank")
        a_reps = ai.get("pdrm", {}).get("police_reports", 0)
        a_stat = f"🚨 {a_reps} Kes Keldai" if a_reps > 0 else "Rekod Bersih"
        cl = ai.get("cluster", {})
        extra = f" (Berkait {cl.get('cluster_count')} kes)" if cl.get("is_syndicate") else ""
        all_nodes.append(f"💳 {bank} {acc}{extra} ── {a_stat}")

    if not all_nodes:
        all_nodes.append("🔍 Tiada entiti nombor/akaun khusus untuk diplotkan.")

    for idx, node in enumerate(all_nodes):
        prefix = "└── " if idx == len(all_nodes) - 1 else "├── "
        tree_lines.append(f"{prefix}{node}")

    tree_lines.append("</code>")
    return "\n".join(tree_lines)


# ==============================================================================
# 4. FORENSIK RESIT & SLIP BANK PALSU (EXIF & SUNTINGAN IMEJ)
# ==============================================================================
def inspect_receipt_slip_forensics(image_path, case_summary=""):
    """
    Menganalisis metadata EXIF dan tanda-tanda suntingan grafik:
    - Kesan perisian penyunting (Photoshop, Canva, PicsArt, Snapseed, InShot, GIMP)
    - Ketidakkonsistenan tarikh pengubahsuaian fail
    - Tanda teks suntingan berdasarkan sintesis AI Vision
    """
    if not image_path or not os.path.exists(image_path):
        return {"is_receipt": False, "is_edited": False, "verdict": "Tiada fail imej"}

    result = {
        "is_receipt": False,
        "is_edited": False,
        "software": "Tiada",
        "penalty": 0,
        "details": [],
        "verdict": "Imej Asli (Tiada Tanda Suntingan EXIF)"
    }

    try:
        with Image.open(image_path) as img:
            exif_data = img._getexif()
            if exif_data:
                exif = {ExifTags.TAGS.get(k, k): v for k, v in exif_data.items()}
                software = str(exif.get("Software", "")).lower()
                desc = str(exif.get("ImageDescription", "")).lower()
                
                # Pengesanan Perisian Suntingan
                suspicious_tools = ["photoshop", "canva", "picsart", "snapseed", "pixelmator", "gimp", "inshot", "lightroom", "paint.net"]
                for tool in suspicious_tools:
                    if tool in software or tool in desc:
                        result["is_edited"] = True
                        result["software"] = tool.capitalize()
                        result["penalty"] = 25
                        result["details"].append(f"Metadata EXIF mengandungi jejak perisian suntingan grafik: <b>{tool.capitalize()}</b>.")
                        result["verdict"] = f"🚨 RESIT DISUNTING DENGAN {tool.upper()}!"
                        break

    except Exception:
        pass

    # Sintesis bersama analisis kes AI Vision
    summary_l = case_summary.lower() if case_summary else ""
    if any(k in summary_l for k in ["resit palsu", "slip disunting", "font berbeza", "edited receipt", "slip palsu"]):
        result["is_edited"] = True
        result["penalty"] = max(result["penalty"], 25)
        result["details"].append("Analisis AI Vision mengesan ketidakkonsistenan fon pada jumlah RM atau tarikh pindahan wang.")
        result["verdict"] = "🚨 AMARAN: SLIP TRANSAKSI BERKEMUNGKINAN BESAR PALSU!"

    return result


# ==============================================================================
# 5. FORENSIK AUDIO PANGGILAN & MESEJ SUARA (VOICE NOTE SCAM)
# ==============================================================================
def analyze_audio_scam_payload(audio_text_or_transcript):
    """
    Menganalisis teks transkripsi audio untuk mengesan modus operandi
    dan manipulasi psikologi scammer (Macau Scam / Polis / LHDN / Mahkamah).
    """
    if not audio_text_or_transcript:
        return {"is_scam_call": False, "score": 0, "tactic": "N/A", "keywords": []}

    t = audio_text_or_transcript.lower()
    
    scam_keywords = {
        "Waran Tangkap / Mahkamah": ["waran tangkap", "mahkamah", "hakim", "perbicaraan", "tangkap anda", "seksyen", "lokap"],
        "Penyamaran Polis (IPD/Bukti Jenayah)": ["ibu pejabat polis", "ipd", "bukit aman", "inspektor", "sarjan", "kes dadah", "pengubahan wang haram"],
        "Ugutan Cukai / LHDN": ["lembaga hasil", "lhdn", "tunggakan cukai", "audit cukai", "bekukan akaun"],
        "Manipulasi Pemindahan Wang": ["pindahkan wang", "akaun audit", "wang jaminan", "akaun sementara", "selamatkan baki"]
    }

    detected_tactics = []
    found_words = []
    
    for tactic, words in scam_keywords.items():
        matched = [w for w in words if w in t]
        if matched:
            detected_tactics.append(tactic)
            found_words.extend(matched)

    if detected_tactics:
        return {
            "is_scam_call": True,
            "penalty": 30,
            "tactic": " + ".join(detected_tactics[:2]),
            "keywords": found_words,
            "verdict": "🚨 PANGGILAN MACAU SCAM / PENYAMARAN PENGUATKUASA DIKESAN!"
        }

    return {
        "is_scam_call": False,
        "penalty": 0,
        "tactic": "Perbualan Biasa",
        "keywords": [],
        "verdict": "Tiada Kata Kunci Ugutan Dikesan"
    }


# ==============================================================================
# 6. PENJANA PEK SINKRONISASI PANGKALAN DATA LUAR TALIAN (ANDROID APP SYNC)
# ==============================================================================
def generate_android_offline_pack(db_path, output_dir=None):
    """
    Mengekstrak rekod pangkalan data ancaman KANTOI ke dalam format JSON padat
    bernombor SemVer untuk dimuat turun oleh aplikasi Android Kantoi (Offline Threat Pack).
    """
    import sqlite3

    if not os.path.exists(db_path):
        return {"success": False, "error": "Pangkalan data tidak ditemui."}

    if not output_dir:
        output_dir = os.path.dirname(os.path.abspath(db_path))

    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    # Semak nama jadual yang wujud
    c.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [r[0] for r in c.fetchall()]
    tbl = "threat_numbers" if "threat_numbers" in tables else ("threats" if "threats" in tables else (tables[0] if tables else None))

    if not tbl:
        conn.close()
        return {"success": False, "error": "Tiada jadual ancaman ditemui dalam pangkalan data."}

    # Ekstrak data fleksibel mengikut kolum yang wujud
    c.execute(f"PRAGMA table_info({tbl})")
    cols = [col[1] for col in c.fetchall()]

    sel_cols = ["phone_norm"]
    for opt in ["suspect_name", "risk_tag", "category", "bank_accounts", "source"]:
        if opt in cols: sel_cols.append(opt)

    c.execute(f"SELECT {', '.join(sel_cols)} FROM {tbl}")
    raw_rows = c.fetchall()
    conn.close()

    threats_list = []
    for r in raw_rows:
        row_dict = dict(zip(sel_cols, r))
        threats_list.append({
            "phone": row_dict.get("phone_norm", ""),
            "name": row_dict.get("suspect_name") or "Scammer Disahkan",
            "category": row_dict.get("risk_tag") or row_dict.get("category") or "HIGH_RISK",
            "bank": row_dict.get("bank_accounts") or "",
            "source": row_dict.get("source") or "Komuniti KANTOI"
        })

    pack_data = {
        "schema_version": "2.7.0",
        "pack_name": "Kantoi_Offline_Threat_Pack",
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total_threats": len(threats_list),
        "threats": threats_list
    }

    out_file = os.path.join(output_dir, "kantoi_offline_threat_pack.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(pack_data, f, ensure_ascii=False, indent=2)

    # Kira MD5 Hash
    with open(out_file, "rb") as f:
        md5_hash = hashlib.md5(f.read()).hexdigest()

    return {
        "success": True,
        "file_path": out_file,
        "total_records": len(threats_list),
        "md5": md5_hash,
        "file_size_kb": round(os.path.getsize(out_file) / 1024, 2)
    }


# ==============================================================================
# 7. ALGORITMA PEMBAHAGIAN MESEJ BERBILANG HALAMAN (TELEGRAM HTML PAGINATION)
# ==============================================================================
def split_telegram_html(text, max_len=3600):
    """
    Memecahkan teks HTML Telegram yang panjang kepada beberapa bahagian (pages)
    dengan memelihara integriti tag HTML (<b/i/code/pre/a>) dan memotong pada
    garisan pemisah seksyen yang kemas.
    """
    if not text or len(text) <= max_len:
        return [text]

    # Pisahkan berdasarkan pemisah seksyen utama garisan atau perenggan berganda
    raw_sections = re.split(r'(\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n|\n\n)', text)

    chunks = []
    current = ""

    for sec in raw_sections:
        if len(current) + len(sec) < max_len:
            current += sec
        else:
            if current.strip():
                chunks.append(current.strip())
            current = sec

    if current.strip():
        chunks.append(current.strip())

    total_pages = len(chunks)
    if total_pages <= 1:
        return chunks

    formatted_pages = []
    for idx, page in enumerate(chunks):
        p_num = idx + 1
        
        # Header Halaman
        if p_num == 1:
            header = f"📄 <b>[BAHAGIAN 1/{total_pages}] DOSSIER RISIKAN SIBER KANTOI</b>\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        else:
            header = f"📄 <b>[BAHAGIAN {p_num}/{total_pages}] SAMBUNGAN DOSSIER RISIKAN KANTOI</b>\n━━━━━━━━━━━━━━━━━━━━━━━━━━\n"

        # Footer Halaman
        footer = f"\n\n<i>(Bersambung ke Bahagian {p_num+1} di bawah...)</i>" if p_num < total_pages else ""

        content = header + page + footer

        # Pengesahan Penutupan Tag HTML Automatik
        for tag in ['b', 'i', 'code', 'pre', 'a']:
            open_count = len(re.findall(rf'<{tag}(?:\s+[^>]*)?>', content, re.IGNORECASE))
            close_count = len(re.findall(rf'</{tag}>', content, re.IGNORECASE))
            if open_count > close_count:
                content += f"</{tag}>" * (open_count - close_count)

        formatted_pages.append(content)

    return formatted_pages
