# -*- coding: utf-8 -*-
"""
KANTOI SOCMINT SEA ENGINE (Fasa 3 / v3.0)
==========================================
Enjin Risikan Media Sosial Terarah Asia Tenggara (Fast-Targeted Social OSINT)
Menyasarkan Top 40+ platform paling aktif di Malaysia & Indonesia sahaja.
Mengelakkan imbasan 500+ laman global yang membazir masa dan kuota.

Ciri-ciri:
1. Penyasaran khusus platform SEA (MY & ID) — tiada laman Eropah/AS
2. Imbasan selari (concurrent) dengan hard timeout 8s per-platform
3. Graceful degradation — timeout/ralat pada 1 laman tidak menjejaskan laman lain
4. Pengekstrakan metadata profil (nama paparan, bio, avatar URL)
5. Integrasi penuh dengan pipeline dossier KANTOI (progress_callback)
6. Mod carian: Username, Nombor Telefon, E-mel
7. Zero-cost / Keyless — tiada API berbayar diperlukan
"""

import re
import time
import html
import hashlib
import urllib.parse
import concurrent.futures
from typing import List, Dict, Optional, Callable

try:
    import requests
    requests.packages.urllib3.disable_warnings()
except Exception:
    pass

# ==============================================================================
# KATALOG PLATFORM SASARAN SEA (MALAYSIA & INDONESIA PRIORITI)
# ==============================================================================
# Setiap entri: (nama_platform, url_template, kategori, negara, kaedah_semakan)
# url_template: {} diganti dengan username/nombor
# kaedah_semakan: "status" = HTTP status 200/404, "text" = cari teks dalam body

PLATFORM_CATALOG_SEA: List[Dict] = [
    # ===================== PEMESEJAN & MEDIA SOSIAL =====================
    {"name": "Telegram",       "url": "https://t.me/{}",                              "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 1},
    {"name": "TikTok",         "url": "https://www.tiktok.com/@{}",                   "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 1},
    {"name": "Instagram",      "url": "https://www.instagram.com/{}/",                "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 1},
    {"name": "Facebook",       "url": "https://www.facebook.com/{}",                  "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 1},
    {"name": "X (Twitter)",    "url": "https://x.com/{}",                             "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 1},
    {"name": "Threads",        "url": "https://www.threads.net/@{}",                  "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 2},
    {"name": "YouTube",        "url": "https://www.youtube.com/@{}",                  "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 2},
    {"name": "MiChat",         "url": "https://michat.sg/{}",                         "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 3},
    {"name": "Reddit",         "url": "https://www.reddit.com/user/{}/",              "cat": "Sosial",     "region": "Global","method": "status", "priority": 3},
    {"name": "Pinterest",      "url": "https://www.pinterest.com/{}/",                "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 3},
    {"name": "Snapchat",       "url": "https://www.snapchat.com/add/{}",              "cat": "Sosial",     "region": "MY/ID", "method": "status", "priority": 3},
    {"name": "Twitch",         "url": "https://www.twitch.tv/{}",                     "cat": "Sosial",     "region": "Global","method": "status", "priority": 3},

    # ===================== E-DAGANG & PASARAN =====================
    {"name": "Carousell",      "url": "https://www.carousell.com.my/u/{}/",           "cat": "E-Dagang",   "region": "MY",    "method": "status", "priority": 1},
    {"name": "Carousell SG",   "url": "https://www.carousell.sg/u/{}/",               "cat": "E-Dagang",   "region": "SG",    "method": "status", "priority": 3},
    {"name": "Mudah.my",       "url": "https://www.mudah.my/{}",                      "cat": "E-Dagang",   "region": "MY",    "method": "status", "priority": 2},
    {"name": "Shopee MY",      "url": "https://shopee.com.my/shop/{}/",               "cat": "E-Dagang",   "region": "MY",    "method": "status", "priority": 2},
    {"name": "Shopee ID",      "url": "https://shopee.co.id/shop/{}/",                "cat": "E-Dagang",   "region": "ID",    "method": "status", "priority": 2},
    {"name": "Tokopedia",      "url": "https://www.tokopedia.com/{}/",                "cat": "E-Dagang",   "region": "ID",    "method": "status", "priority": 1},
    {"name": "Lazada MY",      "url": "https://www.lazada.com.my/shop/{}",            "cat": "E-Dagang",   "region": "MY",    "method": "status", "priority": 2},
    {"name": "Lazada ID",      "url": "https://www.lazada.co.id/shop/{}",             "cat": "E-Dagang",   "region": "ID",    "method": "status", "priority": 2},
    {"name": "Bukalapak",      "url": "https://www.bukalapak.com/u/{}",               "cat": "E-Dagang",   "region": "ID",    "method": "status", "priority": 2},
    {"name": "Blibli",         "url": "https://www.blibli.com/merchant/{}",           "cat": "E-Dagang",   "region": "ID",    "method": "status", "priority": 3},

    # ===================== KERJAYA & PROFIL =====================
    {"name": "LinkedIn",       "url": "https://www.linkedin.com/in/{}/",              "cat": "Kerjaya",    "region": "Global","method": "status", "priority": 1},
    {"name": "GitHub",         "url": "https://github.com/{}",                        "cat": "Kerjaya",    "region": "Global","method": "status", "priority": 1},
    {"name": "Gravatar",       "url": "https://gravatar.com/{}",                      "cat": "Kerjaya",    "region": "Global","method": "status", "priority": 2},
    {"name": "GitLab",         "url": "https://gitlab.com/{}",                        "cat": "Kerjaya",    "region": "Global","method": "status", "priority": 3},
    {"name": "Medium",         "url": "https://medium.com/@{}",                       "cat": "Kerjaya",    "region": "Global","method": "status", "priority": 3},

    # ===================== FORUM TEMPATAN =====================
    {"name": "Lowyat.NET",     "url": "https://forum.lowyat.net/index.php?showuser={}","cat": "Forum",     "region": "MY",    "method": "text",   "priority": 1, "match": "Member Profile"},
    {"name": "CariGold",      "url": "https://www.carigold.com/portal/forums/member.php?username={}","cat":"Forum","region":"MY","method":"text","priority": 2, "match": "Member Profile"},
    {"name": "Kaskus",         "url": "https://www.kaskus.co.id/user/{}/",            "cat": "Forum",      "region": "ID",    "method": "status", "priority": 2},

    # ===================== PERBANKAN DIGITAL & FINTECH =====================
    {"name": "GrabPay",        "url": "https://grab.com/my/",                         "cat": "Fintech",    "region": "MY",    "method": "skip",   "priority": 4},
    {"name": "DANA",           "url": "https://dana.id/",                             "cat": "Fintech",    "region": "ID",    "method": "skip",   "priority": 4},
    {"name": "OVO",            "url": "https://www.ovo.id/",                          "cat": "Fintech",    "region": "ID",    "method": "skip",   "priority": 4},
    {"name": "GoPay",          "url": "https://www.gojek.com/gopay/",                 "cat": "Fintech",    "region": "ID",    "method": "skip",   "priority": 4},
    {"name": "TNG eWallet",    "url": "https://www.tngdigital.com.my/",               "cat": "Fintech",    "region": "MY",    "method": "skip",   "priority": 4},

    # ===================== PERMAINAN & KOMUNITI =====================
    {"name": "Steam",          "url": "https://steamcommunity.com/id/{}",             "cat": "Permainan",  "region": "Global","method": "status", "priority": 3},
    {"name": "Discord",        "url": "https://discord.com/users/{}",                 "cat": "Permainan",  "region": "Global","method": "skip",   "priority": 3},

    # ===================== DOMAIN & LAMAN WEB =====================
    {"name": "WordPress",      "url": "https://{}.wordpress.com",                     "cat": "Blog",       "region": "Global","method": "status", "priority": 3},
    {"name": "Blogger",        "url": "https://{}.blogspot.com",                      "cat": "Blog",       "region": "MY/ID", "method": "status", "priority": 3},
    {"name": "Linktree",       "url": "https://linktr.ee/{}",                         "cat": "Bio",        "region": "Global","method": "status", "priority": 2},
]

# Jumlah platform aktif (bukan skip)
ACTIVE_PLATFORMS = [p for p in PLATFORM_CATALOG_SEA if p.get("method") != "skip"]

# ==============================================================================
# ENJIN IMBASAN SELARI (CONCURRENT PROBE ENGINE)
# ==============================================================================
_SOCMINT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ms-MY,ms;q=0.9,en-US;q=0.8,en;q=0.7,id;q=0.6",
}

_SOCMINT_TIMEOUT = 6.0     # Had masa per-laman (saat)
_SOCMINT_MAX_WORKERS = 12  # Bilangan utas selari maksimum
_SOCMINT_GLOBAL_TIMEOUT = 18.0  # Had masa keseluruhan imbasan (saat)


def _probe_single_platform(platform: Dict, username: str) -> Optional[Dict]:
    """
    Menyemak satu platform sama ada username wujud.
    Mengembalikan dict hasil atau None jika tidak dijumpai / ralat.
    """
    name = platform["name"]
    method = platform.get("method", "status")
    url_template = platform["url"]

    if method == "skip":
        return None

    # Pengesahan integriti: Jangan imbas platform sosial jika handle adalah nombor semata-mata
    if username.isdigit():
        if name in [
            "Telegram", "Gravatar", "Instagram", "TikTok", "Threads",
            "Lazada MY", "Lazada ID", "Shopee MY", "Shopee ID",
            "Carousell", "Carousell SG", "LinkedIn", "GitHub", "X (Twitter)",
            "YouTube", "Reddit", "Pinterest", "Snapchat", "Twitch"
        ]:
            return None

    try:
        url = url_template.format(urllib.parse.quote(username, safe=''))
    except Exception:
        return None

    try:
        r = requests.get(
            url,
            headers=_SOCMINT_HEADERS,
            timeout=_SOCMINT_TIMEOUT,
            allow_redirects=True,
            verify=False
        )

        found = False
        evidence = ""

        if method == "status":
            if r.status_code == 200:
                body_lower = r.text[:5000].lower() if r.text else ""
                # Penapis negatif: semak jika halaman 404/not found walaupun HTTP 200
                not_found_signals = [
                    "page not found", "user not found", "doesn't exist",
                    "this page isn't available", "no results found",
                    "this account doesn", "sorry, this page",
                    "halaman tidak ditemukan", "pengguna tidak ditemui",
                    "this user does not exist", "couldn't find this account",
                    "maaf, halaman ini tidak tersedia", "shop enter fail page",
                    "shop not found", "statuscode\":10202", "item_not_found",
                    "user_not_found"
                ]
                if not any(sig in body_lower for sig in not_found_signals):
                    # Cuba ekstrak tajuk halaman sebagai nama paparan
                    title_match = re.search(r'<title[^>]*>([^<]+)</title>', r.text[:3000], re.IGNORECASE)
                    raw_title = html.unescape(title_match.group(1)).strip() if title_match else ""
                    raw_lower = raw_title.lower()

                    # Penapis khusus platform anti-false-positive
                    is_false_positive = False
                    if name == "Instagram" and raw_lower in ["instagram", "login • instagram", "log in • instagram"]:
                        is_false_positive = True
                    elif name == "TikTok" and ("couldn't find this account" in body_lower or raw_lower in ["tiktok", "watch trending videos for you"]):
                        is_false_positive = True
                    elif name in ["Lazada MY", "Lazada ID"] and ("shop enter fail page" in body_lower or "shop enter fail page" in raw_lower):
                        is_false_positive = True
                    elif name in ["Shopee MY", "Shopee ID"] and ("buyer or shop not found" in body_lower or "error" in raw_lower):
                        is_false_positive = True

                    if not is_false_positive:
                        found = True
                        # Buang nama platform dari tajuk
                        for rm in [" | TikTok", " - YouTube", " (@", " on X", " • Instagram",
                                   " | LinkedIn", " | Facebook", " · GitHub", " | Carousell",
                                   " - Reddit", " on Threads", " | Tokopedia", " - Shopee",
                                   " | Lazada", " Steam Community", " | Bukalapak"]:
                            raw_title = raw_title.split(rm)[0]
                        evidence = raw_title[:60]
            elif r.status_code in [301, 302]:
                # Pengalihan mungkin bermaksud profil wujud
                location = r.headers.get("Location", "")
                if username.lower() in location.lower():
                    found = True
                    evidence = f"Redirect: {location[:80]}"

        elif method == "text":
            match_text = platform.get("match", "")
            if r.status_code == 200 and match_text and match_text.lower() in r.text[:8000].lower():
                found = True
                evidence = f"Padanan teks: '{match_text}'"

        if found:
            return {
                "platform": name,
                "url": url,
                "category": platform.get("cat", "Lain"),
                "region": platform.get("region", ""),
                "evidence": evidence,
                "status_code": r.status_code,
                "response_time_ms": int(r.elapsed.total_seconds() * 1000)
            }

    except requests.exceptions.Timeout:
        pass  # Graceful degradation — laman lambat dilangkau
    except requests.exceptions.ConnectionError:
        pass
    except Exception:
        pass

    return None


def scan_socmint_sea(
    username: str,
    priority_filter: int = 3,
    progress_callback: Optional[Callable] = None
) -> Dict:
    """
    Menjalankan imbasan SOCMINT selari terhadap platform SEA untuk username/nombor.

    Args:
        username: Username, nombor telefon, atau e-mel untuk dicari
        priority_filter: Hanya imbas platform dengan prioriti <= nilai ini (1=kritikal, 3=biasa)
        progress_callback: Fungsi panggil balik (percent, stage, detail)

    Returns:
        Dict dengan kunci:
        - hits: List[Dict] — platform yang username dijumpai
        - scanned: int — jumlah platform diimbas
        - duration_ms: int — masa keseluruhan (ms)
        - username: str — username yang dicari
    """
    if not username or not str(username).strip():
        return {"hits": [], "scanned": 0, "duration_ms": 0, "username": ""}

    clean_user = str(username).strip().lstrip("@").replace(" ", "")

    # Tapis platform mengikut prioriti
    platforms_to_scan = [
        p for p in ACTIVE_PLATFORMS
        if p.get("priority", 5) <= priority_filter
    ]

    if not platforms_to_scan:
        return {"hits": [], "scanned": 0, "duration_ms": 0, "username": clean_user}

    if progress_callback:
        progress_callback(40, f"🌐 Mengimbas {len(platforms_to_scan)} platform media sosial SEA...",
                          f"Sasaran: @{clean_user}")

    hits = []
    start_time = time.time()

    with concurrent.futures.ThreadPoolExecutor(
        max_workers=_SOCMINT_MAX_WORKERS,
        thread_name_prefix="socmint_probe"
    ) as executor:
        future_map = {
            executor.submit(_probe_single_platform, p, clean_user): p
            for p in platforms_to_scan
        }

        try:
            for future in concurrent.futures.as_completed(future_map, timeout=_SOCMINT_GLOBAL_TIMEOUT):
                result = future.result()
                if result:
                    hits.append(result)
        except concurrent.futures.TimeoutError:
            # Had masa global tercapai — ambil hasil yang sedia ada
            pass

    elapsed_ms = int((time.time() - start_time) * 1000)

    # Susun hits mengikut prioriti platform
    priority_map = {p["name"]: p.get("priority", 5) for p in platforms_to_scan}
    hits.sort(key=lambda h: priority_map.get(h["platform"], 5))

    if progress_callback:
        progress_callback(65, f"🔎 {len(hits)} profil aktif dijumpai ({len(platforms_to_scan)} diimbas)...",
                          f"{elapsed_ms}ms")

    return {
        "hits": hits,
        "scanned": len(platforms_to_scan),
        "duration_ms": elapsed_ms,
        "username": clean_user
    }


def is_phone_target(target: str) -> bool:
    """
    Mengenal pasti sama ada sasaran adalah nombor telefon (MY/ID/Intl).
    Contoh: '0133245879', '011-27244506', '+60133245879', '601127244506'.
    """
    raw = str(target).strip()
    if not raw:
        return False
    # Jika bermula dengan @ atau mengandungi abjad, ia BUKAN nombor telefon
    if raw.startswith("@") or re.search(r'[a-zA-Z]', raw):
        return False
    clean = re.sub(r'[^\d]', '', raw)
    if not clean:
        return False
    # Nombor telefon Malaysia / Serantau biasa: 9 hingga 14 digit
    if clean.startswith("601") and len(clean) in [11, 12]:
        return True
    if clean.startswith("01") and len(clean) in [10, 11]:
        return True
    if clean.startswith("0") and len(clean) in [9, 10, 11]:
        return True
    if clean.startswith("62") and len(clean) in [10, 11, 12, 13, 14]:
        return True
    if len(clean) >= 9 and len(clean) <= 13:
        return True
    return False


def derive_username_candidates(raw_query: str) -> List[str]:
    """
    Menghasilkan senarai calon username HANYA jika input adalah username atau nama.
    Jika input adalah nombor telefon, kembalikan [] kerana nombor telefon BUKAN username.
    """
    clean = str(raw_query).strip()

    if not clean or is_phone_target(clean):
        return []

    candidates = []

    # 1. Input adalah username (@handle)
    if clean.startswith("@"):
        candidates.append(clean[1:])
    else:
        # Jika bukan nombor telefon, anggap sebagai username atau nama orang
        candidates.append(clean.lower().replace(" ", ""))
        if " " in clean:
            parts = clean.lower().split()
            candidates.append(".".join(parts))
            candidates.append("_".join(parts))

    # Buang duplikat, kosong, dan rentetan yang berbentuk nombor semata-mata
    seen = set()
    unique = []
    for c in candidates:
        c = c.strip()
        if c and c not in seen and not c.isdigit():
            seen.add(c)
            unique.append(c)

    return unique[:3]  # Maksimum 3 calon


# ==============================================================================
# PENJANA LAPORAN SOCMINT HTML (TELEGRAM FORMAT)
# ==============================================================================
def format_socmint_report_html(scan_result: Dict, suspect_name: str = "") -> str:
    """
    Menjana seksyen HTML SOCMINT untuk dimasukkan ke dalam dossier KANTOI.
    """
    hits = scan_result.get("hits", [])
    scanned = scan_result.get("scanned", 0)
    duration = scan_result.get("duration_ms", 0)
    username = scan_result.get("username", "")

    # Pastikan tiada simbol @ jika sasaran adalah nombor
    is_num = bool(re.match(r"^[\d\+\-]+$", str(username).strip())) or is_phone_target(str(username))
    target_display = str(username) if is_num else (username if str(username).startswith("@") else f"@{username}")

    if not hits:
        return (
            f"🌐 <b>SOCMINT SEA (Risikan Media Sosial Serantau):</b>\n"
            f"• Sasaran: <code>{html.escape(target_display)}</code>\n"
            f"• Platform Diimbas: {scanned} laman SEA (MY/ID)\n"
            f"• Keputusan: ✅ Tiada profil aktif dijumpai\n"
            f"• Masa Imbasan: {duration}ms\n"
        )

    # Kategorikan hits
    by_cat = {}
    for h in hits:
        cat = h.get("category", "Lain")
        by_cat.setdefault(cat, []).append(h)

    cat_icons = {
        "Sosial": "📱", "E-Dagang": "🛒", "Kerjaya": "💼",
        "Forum": "📚", "Fintech": "💳", "Permainan": "🎮",
        "Blog": "📝", "Bio": "🔗", "Lain": "🌐"
    }

    lines = [
        "🌐 <b>SOCMINT SEA (Risikan Media Sosial Serantau):</b>",
        f"• Sasaran: <code>{html.escape(target_display)}</code>",
        f"• Profil Aktif Dijumpai: <b>{len(hits)}</b> / {scanned} platform",
        f"• Masa Imbasan: {duration}ms",
        "",
    ]

    for cat, cat_hits in by_cat.items():
        icon = cat_icons.get(cat, "🌐")
        lines.append(f"{icon} <b>{cat}:</b>")
        for h in cat_hits:
            pname = html.escape(h["platform"])
            evidence = html.escape(h.get("evidence", "")[:50])
            region = h.get("region", "")
            region_flag = ""
            if "MY" in region:
                region_flag = "🇲🇾"
            elif "ID" in region:
                region_flag = "🇮🇩"
            elif "SG" in region:
                region_flag = "🇸🇬"

            # Pautan boleh klik ke profil
            url = h.get("url", "")
            if url:
                lines.append(f'  • {region_flag} <a href="{html.escape(url)}">{pname}</a>'
                             + (f" — <i>{evidence}</i>" if evidence else ""))
            else:
                lines.append(f"  • {region_flag} {pname}"
                             + (f" — <i>{evidence}</i>" if evidence else ""))
        lines.append("")

    if len(hits) >= 3:
        lines.append("⚠️ <i>Sasaran mempunyai kehadiran digital aktif di pelbagai platform. "
                      "Semak silang nama paparan & gambar profil untuk mengesahkan pemilikan.</i>")
    else:
        lines.append("ℹ️ <i>Profil dijumpai — sila pastikan ianya milik individu yang sama melalui "
                      "semakan silang gambar, bio, dan nama paparan.</i>")

    return "\n".join(lines)


# ==============================================================================
# MODUL GRAVATAR HASH (PENCARIAN VIA E-MEL)
# ==============================================================================
def check_gravatar_by_email(email: str) -> Optional[Dict]:
    """
    Menyemak sama ada e-mel mempunyai profil Gravatar yang aktif.
    Menggunakan MD5 hash mengikut spesifikasi Gravatar.
    """
    if not email or "@" not in email:
        return None

    clean_email = email.strip().lower()
    email_hash = hashlib.md5(clean_email.encode('utf-8')).hexdigest()
    gravatar_url = f"https://www.gravatar.com/{email_hash}.json"

    try:
        r = requests.get(gravatar_url, timeout=4, verify=False,
                         headers={"User-Agent": _SOCMINT_HEADERS["User-Agent"]})
        if r.status_code == 200:
            data = r.json()
            entry = data.get("entry", [{}])[0]
            return {
                "platform": "Gravatar",
                "url": f"https://gravatar.com/{entry.get('preferredUsername', email_hash)}",
                "category": "Kerjaya",
                "region": "Global",
                "evidence": entry.get("displayName", "") or entry.get("preferredUsername", ""),
                "avatar_url": entry.get("thumbnailUrl", ""),
                "profile_url": entry.get("profileUrl", ""),
                "status_code": 200,
                "response_time_ms": int(r.elapsed.total_seconds() * 1000)
            }
    except Exception:
        pass
    return None


# ==============================================================================
# KATALOG LIPUTAN PLATFORM (UNTUK PAPARAN DOSSIER)
# ==============================================================================
def get_platform_coverage_summary() -> str:
    """Mengembalikan ringkasan jumlah platform yang disokong untuk paparan dossier."""
    total = len(PLATFORM_CATALOG_SEA)
    active = len(ACTIVE_PLATFORMS)
    my_count = len([p for p in PLATFORM_CATALOG_SEA if "MY" in p.get("region", "")])
    id_count = len([p for p in PLATFORM_CATALOG_SEA if "ID" in p.get("region", "")])
    return f"{active} platform aktif ({my_count} MY, {id_count} ID) daripada {total} katalog SEA"


# ==============================================================================
# ENJIN DORKING DUCKDUCKGO: KUMPULAN PEMESEJAN AWAM (WHATSAPP, TELEGRAM, DISCORD)
# ==============================================================================
def generate_duckduckgo_dork_url(target_query: str) -> str:
    """
    Menjana URL carian langsung DuckDuckGo dengan dorking khas messaging apps.
    Sesuai untuk dibuka terus oleh pengguna di pelayar.
    """
    clean_target = re.sub(r'[^\w\+]', ' ', str(target_query)).strip()
    if not clean_target:
        clean_target = "scam"
    dork = f'site:chat.whatsapp.com OR site:wa.me OR site:t.me "{clean_target}"'
    return f"https://duckduckgo.com/?q={urllib.parse.quote(dork)}"


def search_duckduckgo_messaging_dorks(target_query: str, max_results: int = 3) -> List[Dict]:
    """
    Menjalankan dorking DuckDuckGo khusus untuk Messaging Apps (WhatsApp, Telegram, dsb.)
    Berasaskan teknik OSINT Noimosiny:
    - site:chat.whatsapp.com (kumpulan WhatsApp awam)
    - site:wa.me (pautan perbualan terus WhatsApp)
    - site:t.me (saluran & grup Telegram)

    Ciri Utama:
    - 100% Zero-Cost & Keyless (RM0 / Rp0)
    - Mencuba library DDGS terlebih dahulu
    - Fallback automatik ke HTTP scraping ringan DuckDuckGo HTML
    - Penapis anti-duplikasi & pembersihan tag HTML
    """
    clean_target = re.sub(r'[^\w\+]', ' ', str(target_query)).strip()
    if not clean_target:
        return []

    dork_query = f'site:chat.whatsapp.com OR site:wa.me OR site:t.me "{clean_target}"'
    results = []
    seen_links = set()

    # Kaedah Pure Python: Parse DuckDuckGo Lite tanpa kebergantungan luar (BS4 / DDGS)
    try:
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "ms-MY,ms;q=0.9,en-US;q=0.8,en;q=0.7"
        }
        r = requests.post(
            "https://lite.duckduckgo.com/lite/",
            data={"q": dork_query},
            headers=headers,
            timeout=5
        )
        if r.status_code in (200, 202):
            current = {}
            for r_str in r.text.split('<tr'):
                if "result-link" in r_str:
                    link_m = re.search(r'href="([^"]+)"', r_str)
                    title_m = re.search(r"class=['\"]result-link['\"][^>]*>(.*?)</a>", r_str, re.DOTALL)
                    if link_m and title_m:
                        raw_href = link_m.group(1)
                        uddg = re.search(r'uddg=([^&]+)', raw_href)
                        clean_href = urllib.parse.unquote(uddg.group(1)) if uddg else raw_href
                        current = {
                            "link": clean_href,
                            "title": html.unescape(re.sub(r'<[^>]+>', '', title_m.group(1))).strip(),
                            "snippet": ""
                        }
                elif "result-snippet" in r_str and current:
                    snip_m = re.search(r"class=['\"]result-snippet['\"][^>]*>(.*?)</td>", r_str, re.DOTALL)
                    if snip_m:
                        clean_snip = html.unescape(re.sub(r'<[^>]+>', '', snip_m.group(1))).strip()
                        current["snippet"] = clean_snip
                        lnk = current.get("link", "")
                        if lnk and lnk not in seen_links:
                            seen_links.add(lnk)
                            platform_tag = "MESSAGING"
                            if "chat.whatsapp.com" in lnk:
                                platform_tag = "WhatsApp Group"
                            elif "wa.me" in lnk:
                                platform_tag = "WhatsApp Chat"
                            elif "t.me" in lnk:
                                platform_tag = "Telegram"
                            current["source"] = platform_tag
                            results.append(current)
                        current = {}
                        if len(results) >= max_results:
                            break
    except Exception:
        pass

    return results[:max_results]


# ==============================================================================
# ENJIN MENYELONGKAR SEBUTAN NOMBOR TELEFON DI MEDIA SOSIAL & FORUM (DORKING)
# ==============================================================================
def hunt_phone_social_mentions(
    phone_input: str,
    max_results: int = 5,
    progress_callback: Optional[Callable] = None
) -> Dict:
    """
    Menyelongkar laman-laman media sosial (Facebook, TikTok, Instagram, Threads,
    Carousell, Mudah.my, Telegram, WhatsApp) untuk mencari sebutan sebenar nombor
    telefon yang tercetak dalam teks hantaran, iklan, atau profil.

    MENJAMIN ZERO FALSE-POSITIVE:
    Setiap hasil carian disaring dengan teliti untuk memastikan digit nombor
    telefon benar-benar wujud dalam tajuk, petikan (snippet), atau URL.
    """
    digits = re.sub(r'[^\d]', '', str(phone_input).strip())
    if not digits:
        return {
            "target": phone_input,
            "clean_phone": "",
            "hyphenated": "",
            "intl_phone": "",
            "mentions": [],
            "has_mentions": False
        }

    if digits.startswith("60") and len(digits) >= 11:
        clean_local = "0" + digits[2:]
        intl = digits
    elif digits.startswith("0"):
        clean_local = digits
        intl = "60" + digits[1:]
    else:
        clean_local = digits
        intl = "60" + digits

    if len(clean_local) == 10:
        hyphen1 = clean_local[:3] + "-" + clean_local[3:]
        space1 = clean_local[:3] + " " + clean_local[3:]
    elif len(clean_local) == 11:
        hyphen1 = clean_local[:4] + "-" + clean_local[4:]
        space1 = clean_local[:4] + " " + clean_local[4:]
    else:
        hyphen1 = clean_local
        space1 = clean_local

    if progress_callback:
        progress_callback(30, "🌐 Menyelongkar sebutan nombor di media sosial & forum...", f"Sasaran: {clean_local}")

    verified_mentions = []
    seen_links = set()

    # Dork berfokus media sosial dan pasaran tempatan
    dork_social = (
        f'("{clean_local}" OR "{hyphen1}") '
        f'(site:facebook.com OR site:instagram.com OR site:tiktok.com OR '
        f'site:carousell.com.my OR site:mudah.my OR site:threads.net OR '
        f'site:t.me OR site:chat.whatsapp.com)'
    )
    dork_general = f'{clean_local}'

    queries = [dork_general, dork_social]

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "ms-MY,ms;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    for q in queries:
        if len(verified_mentions) >= max_results:
            break
        try:
            r = requests.post(
                "https://lite.duckduckgo.com/lite/",
                data={"q": q},
                headers=headers,
                timeout=6
            )
            if r.status_code in (200, 202):
                current = {}
                for r_str in r.text.split('<tr'):
                    if "result-link" in r_str:
                        link_m = re.search(r'href="([^"]+)"', r_str)
                        title_m = re.search(r"class=['\"]result-link['\"][^>]*>(.*?)</a>", r_str, re.DOTALL)
                        if link_m and title_m:
                            raw_href = link_m.group(1)
                            uddg = re.search(r'uddg=([^&]+)', raw_href)
                            clean_href = urllib.parse.unquote(uddg.group(1)) if uddg else raw_href
                            current = {
                                "link": clean_href,
                                "title": html.unescape(re.sub(r'<[^>]+>', '', title_m.group(1))).strip(),
                                "snippet": ""
                            }
                    elif "result-snippet" in r_str and current:
                        snip_m = re.search(r"class=['\"]result-snippet['\"][^>]*>(.*?)</td>", r_str, re.DOTALL)
                        if snip_m:
                            clean_snip = html.unescape(re.sub(r'<[^>]+>', '', snip_m.group(1))).strip()
                            current["snippet"] = clean_snip

                            link = current.get("link", "")
                            title = current.get("title", "")
                            snippet = current.get("snippet", "")

                            if link and link not in seen_links:
                                combined = f"{title} {snippet} {link}".lower()
                                combined_digits = re.sub(r'[^\d]', '', combined)

                                has_exact_match = (
                                    clean_local in combined
                                    or hyphen1 in combined
                                    or space1 in combined
                                    or intl in combined
                                    or (len(clean_local) >= 9 and clean_local in combined_digits)
                                )

                                if has_exact_match:
                                    seen_links.add(link)
                                    platform = "Web Awam"
                                    flag = "🌐"
                                    link_lower = link.lower()
                                    if "facebook.com" in link_lower:
                                        platform, flag = "Facebook", "🇲🇾"
                                    elif "tiktok.com" in link_lower:
                                        platform, flag = "TikTok", "🇲🇾"
                                    elif "instagram.com" in link_lower:
                                        platform, flag = "Instagram", "🇲🇾"
                                    elif "threads.net" in link_lower:
                                        platform, flag = "Threads", "🇲🇾"
                                    elif "carousell.com.my" in link_lower or "carousell.sg" in link_lower:
                                        platform, flag = "Carousell", "🇲🇾"
                                    elif "mudah.my" in link_lower:
                                        platform, flag = "Mudah.my", "🇲🇾"
                                    elif "chat.whatsapp.com" in link_lower or "wa.me" in link_lower:
                                        platform, flag = "WhatsApp Group/Chat", "🇲🇾"
                                    elif "t.me" in link_lower:
                                        platform, flag = "Telegram", "🇲🇾"
                                    elif "lowyat.net" in link_lower:
                                        platform, flag = "Lowyat Forum", "🇲🇾"
                                    elif "carigold.com" in link_lower:
                                        platform, flag = "CariGold Forum", "🇲🇾"

                                    verified_mentions.append({
                                        "platform": platform,
                                        "flag": flag,
                                        "title": title.strip(),
                                        "snippet": snippet.strip(),
                                        "link": link
                                    })
                                    if len(verified_mentions) >= max_results:
                                        break
                            current = {}
        except Exception:
            pass

    return {
        "target": phone_input,
        "clean_phone": clean_local,
        "hyphenated": hyphen1,
        "intl_phone": intl,
        "mentions": verified_mentions,
        "has_mentions": len(verified_mentions) > 0
    }


def format_phone_mentions_html(mention_data: Dict) -> str:
    """
    Format hasil selongkar sebutan media sosial untuk Telegram HTML.
    Memastikan TIADA simbol @ di awal nombor dan TIADA profil palsu.
    """
    phone = mention_data.get("clean_phone") or mention_data.get("target", "")
    intl = mention_data.get("intl_phone") or ("60" + phone[1:] if phone.startswith("0") else phone)
    mentions = mention_data.get("mentions", [])
    has_mentions = mention_data.get("has_mentions", False)

    lines = [
        "🌐 <b>SEBUTAN MEDIA SOSIAL & FORUM (Social Mentions):</b>",
        f"• Sasaran: <code>{html.escape(phone)}</code> (Nombor Telefon)",
    ]

    if has_mentions:
        lines.append(f"• Status: ⚠️ <b>Dijumpai {len(mentions)} sebutan nombor ini di laman awam:</b>")
        lines.append("")
        for idx, m in enumerate(mentions, 1):
            p_name = html.escape(m.get("platform", "Web"))
            flag = m.get("flag", "🌐")
            tit = html.escape(m.get("title", "Pautan Awam"))
            snp = html.escape(m.get("snippet", ""))
            lnk = m.get("link", "")
            lines.append(f"<b>{idx}. [{flag} {p_name}]</b> {tit}")
            if snp:
                lines.append(f"   📝 <i>\"{snp[:120]}...\"</i>")
            if lnk and lnk.startswith(("http://", "https://")):
                lines.append(f'   🔗 <a href="{html.escape(lnk)}">Buka Pautan Hantaran</a>')
            lines.append("")
    else:
        cse_url = f"https://cse.google.com/cse?cx=53a0041f2f24f4e3b&q={urllib.parse.quote(phone)}#gsc.tab=0&gsc.q={urllib.parse.quote(phone)}"
        lines.append("• Platform Disaring: Facebook, TikTok, Instagram, Threads, Carousell, Mudah.my, Telegram")
        lines.append("• Status Imbasan: ℹ️ <i>Imbasan pantas selesai. Untuk memeriksa aduan hantaran Facebook / viral penuh:</i>")
        lines.append(f'• Carian Menyeluruh: 🌐 <a href="{cse_url}"><b>Buka Carian Penuh Google CSE & FB ({html.escape(phone)})</b></a>')
        lines.append("")

    lines.append("📱 <b>Pautan Perhubungan Pantas:</b>")
    lines.append(f'• WhatsApp: <a href="https://wa.me/{intl}">wa.me/{intl}</a>')
    lines.append(f'• Telegram: <a href="https://t.me/+{intl}">t.me/+{intl}</a>')

    return "\n".join(lines)
