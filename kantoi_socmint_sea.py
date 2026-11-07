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
    Menyemak satu platform sama ada username/nombor wujud.
    Mengembalikan dict hasil atau None jika tidak dijumpai / ralat.
    """
    name = platform["name"]
    method = platform.get("method", "status")
    url_template = platform["url"]

    if method == "skip":
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
                    "maaf, halaman ini tidak tersedia"
                ]
                if not any(sig in body_lower for sig in not_found_signals):
                    found = True
                    # Cuba ekstrak tajuk halaman sebagai nama paparan
                    title_match = re.search(r'<title[^>]*>([^<]+)</title>', r.text[:3000], re.IGNORECASE)
                    if title_match:
                        raw_title = html.unescape(title_match.group(1)).strip()
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


def derive_username_candidates(raw_query: str) -> List[str]:
    """
    Menghasilkan senarai calon username daripada input mentah:
    - Nombor telefon: 01127244506 → ["01127244506", "1127244506", "60112724"]
    - Username: @scammer123 → ["scammer123"]
    - Nama: Ahmad Bin Ali → ["ahmadali", "ahmad.ali", "ahmad_ali"]
    """
    candidates = []
    clean = str(raw_query).strip()

    if not clean:
        return candidates

    # 1. Input adalah nombor (telefon / akaun bank)
    digits_only = re.sub(r'[^\d]', '', clean)
    if len(digits_only) >= 8:
        candidates.append(digits_only)
        # Jika bermula dengan 60 (MY), tambah tanpa kod negara
        if digits_only.startswith("60") and len(digits_only) >= 11:
            candidates.append("0" + digits_only[2:])
        # Jika bermula dengan 62 (ID), tambah tanpa kod negara
        elif digits_only.startswith("62") and len(digits_only) >= 11:
            candidates.append("0" + digits_only[2:])
        elif digits_only.startswith("0"):
            candidates.append(digits_only[1:])  # Tanpa leading 0

    # 2. Input adalah username (@handle)
    if clean.startswith("@"):
        candidates.append(clean[1:])
    elif not digits_only or len(digits_only) < 8:
        # Anggap sebagai username
        candidates.append(clean.lower().replace(" ", ""))
        if " " in clean:
            parts = clean.lower().split()
            candidates.append(".".join(parts))
            candidates.append("_".join(parts))

    # Buang duplikat dan kosong
    seen = set()
    unique = []
    for c in candidates:
        c = c.strip()
        if c and c not in seen:
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

    if not hits:
        return (
            f"🌐 <b>SOCMINT SEA (Risikan Media Sosial Serantau):</b>\n"
            f"• Sasaran: <code>@{html.escape(username)}</code>\n"
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
        f"• Sasaran: <code>@{html.escape(username)}</code>",
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

    # 1. Kaedah Utama: Cuba library DDGS jika tersedia
    try:
        from duckduckgo_search import DDGS
        with DDGS(timeout=5) as ddgs:
            raw_hits = list(ddgs.text(dork_query, max_results=max_results))
            for h in raw_hits:
                link = h.get("href", "")
                if link and link not in seen_links:
                    seen_links.add(link)
                    # Tentukan sumber platform
                    platform_tag = "MESSAGING"
                    if "chat.whatsapp.com" in link:
                        platform_tag = "WhatsApp Group"
                    elif "wa.me" in link:
                        platform_tag = "WhatsApp Chat"
                    elif "t.me" in link:
                        platform_tag = "Telegram"

                    results.append({
                        "title": h.get("title", ""),
                        "snippet": h.get("body", ""),
                        "link": link,
                        "source": platform_tag
                    })
            if results:
                return results[:max_results]
    except Exception:
        pass

    # 2. Kaedah Sandaran (Fallback): DuckDuckGo HTML Endpoint (Tahan Lasak & Tanpa Kebergantungan Luar)
    try:
        from bs4 import BeautifulSoup
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "ms-MY,ms;q=0.9,en-US;q=0.8,en;q=0.7"
        }
        r = requests.get(
            "https://html.duckduckgo.com/html/",
            params={"q": dork_query},
            headers=headers,
            timeout=5,
            verify=False
        )
        if r.status_code == 200:
            soup = BeautifulSoup(r.text, "html.parser")
            for div in soup.find_all("div", class_="result")[:max_results]:
                t_elem = div.find("a", class_="result__a")
                s_elem = div.find("a", class_="result__snippet")
                if t_elem:
                    raw_href = t_elem.get("href", "")
                    # Buang redirect wrapper DuckDuckGo jika ada
                    actual_link = raw_href
                    if "uddg=" in raw_href:
                        m = re.search(r'uddg=([^&]+)', raw_href)
                        if m:
                            actual_link = urllib.parse.unquote(m.group(1))

                    if actual_link and actual_link not in seen_links:
                        seen_links.add(actual_link)
                        platform_tag = "MESSAGING"
                        if "chat.whatsapp.com" in actual_link:
                            platform_tag = "WhatsApp Group"
                        elif "wa.me" in actual_link:
                            platform_tag = "WhatsApp Chat"
                        elif "t.me" in actual_link:
                            platform_tag = "Telegram"

                        results.append({
                            "title": t_elem.get_text(strip=True),
                            "snippet": s_elem.get_text(strip=True) if s_elem else "",
                            "link": actual_link,
                            "source": platform_tag
                        })
    except Exception:
        pass

    return results[:max_results]
