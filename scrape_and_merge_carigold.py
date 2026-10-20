import urllib.request
from bs4 import BeautifulSoup
import re
import json
import time
import os
import sqlite3
import openpyxl
from concurrent.futures import ThreadPoolExecutor, as_completed

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'ms,en-US;q=0.9,en;q=0.8'
}

def clean_phone(p):
    digits = re.sub(r'[^0-9]', '', p)
    if digits.startswith('60'):
        digits = '0' + digits[2:]
    if 9 <= len(digits) <= 11:
        if any(digits.startswith(prefix) for prefix in ['01', '03', '04', '05', '06', '07', '08', '09']):
            return digits
    return None

def extract_thread_data(thread_meta):
    url = thread_meta['url']
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
    except Exception as e:
        return None

    soup = BeautifulSoup(html, 'html.parser')
    title_el = soup.select_one('h1.p-title-value')
    title = title_el.get_text(strip=True) if title_el else thread_meta.get('title', '')

    messages = soup.select('.message-body .bbWrapper')
    if not messages:
        return None

    # Take first 5 posts in the thread
    full_text = " ".join([m.get_text(separator=' ', strip=True) for m in messages[:5]])
    combined = title + " " + full_text

    # Extract phones
    raw_phones = re.findall(r'(?:\+?60|0)[1-9][0-9\-\s]{6,12}[0-9]', combined)
    cleaned_phones = set()
    for p in raw_phones:
        cp = clean_phone(p)
        if cp:
            cleaned_phones.add(cp)

    # Extract bank accounts
    bank_patterns = [
        r'(?:maybank|mbb|cimb|bsn|rhb|public bank|pbb|bank islam|ambank|hong leong|hlb|bank rakyat|agrobank)[\s\:\-]+(\d{10,16})',
        r'(\d{10,16})[\s\:\-]+(?:maybank|mbb|cimb|bsn|rhb|public bank|pbb|bank islam|ambank|hong leong|hlb|bank rakyat|agrobank)',
        r'(?:no[\.\s]+akaun|acc(?:ount)?[\.\s]+no|no[\.\s]+acc)[\s\:\-]+(\d{10,16})'
    ]
    banks = set()
    for bp in bank_patterns:
        for m in re.finditer(bp, combined, re.IGNORECASE):
            banks.add(m.group(0).strip())

    # Extract IC numbers
    ics = set(re.findall(r'\b\d{6}[-\s]\d{2}[-\s]\d{4}\b', combined))

    # Detect scheme name or suspect name
    suspect_name = title
    for prefix in ["Scam Alert :", "SCAM ALERT :", "SUSPICIOUS :", "[Update]", "Penipuan:", "AWAS :", "Awas :"]:
        suspect_name = suspect_name.replace(prefix, "").strip()

    return {
        'title': title,
        'suspect_name': suspect_name,
        'url': url,
        'author': thread_meta.get('author', ''),
        'date': thread_meta.get('date', ''),
        'phones': sorted(list(cleaned_phones)),
        'banks': sorted(list(banks)),
        'ics': sorted(list(ics)),
        'summary': full_text[:250].replace('\n', ' ')
    }

def run_scraper():
    with open('carigold_threads_list.json', 'r', encoding='utf-8') as f:
        threads = json.load(f)

    print(f"Total threads available in CariGold list: {len(threads)}")

    # Prioritize threads with keywords or numbers
    keywords = ['scam', 'penipu', 'tipu', 'awas', 'hati-hati', 'perangkap', 'mule', 'akaun', 'fraud', '01', '03', 'polis', 'laporan', 'suspek']
    priority_threads = []
    other_threads = []
    for t in threads:
        title_lower = t['title'].lower()
        if any(k in title_lower for k in keywords) or re.search(r'\d{8,}', t['title']):
            priority_threads.append(t)
        else:
            other_threads.append(t)

    target_threads = priority_threads + other_threads
    print(f"Prioritized target threads to scrape: {len(target_threads)}")

    results = []
    processed = 0
    start_time = time.time()

    with ThreadPoolExecutor(max_workers=6) as executor:
        future_to_thread = {executor.submit(extract_thread_data, t): t for t in target_threads}
        for future in as_completed(future_to_thread):
            t = future_to_thread[future]
            processed += 1
            if processed % 25 == 0 or processed == len(target_threads):
                elapsed = time.time() - start_time
                print(f"Scraped {processed}/{len(target_threads)} threads ({elapsed:.1f}s) - Threats found with phones: {len(results)}")
            try:
                res = future.result()
                if res and (len(res['phones']) > 0 or len(res['banks']) > 0 or len(res['ics']) > 0):
                    results.append(res)
            except Exception as e:
                pass

    print(f"\n--- Scraping Complete ---")
    print(f"Total actionable threats extracted with phones/banks/ICs: {len(results)}")

    # Format into carigold_scammers_db.json
    carigold_db = []
    for idx, r in enumerate(results, 1):
        phones = r['phones']
        primary = ("+6" + phones[0]) if phones else "N/A"
        all_p = ", ".join([("+6" + p) for p in phones]) if phones else "N/A"
        banks_str = "; ".join(r['banks']) if r['banks'] else "N/A"
        ic_str = ", ".join(r['ics']) if r['ics'] else "N/A"

        carigold_db.append({
            "id": idx,
            "name": r['suspect_name'],
            "primary_phone": primary,
            "all_phones": all_p,
            "bank_accounts": banks_str,
            "ic_number": ic_str,
            "address": "CariGold Forum: " + r['title'][:80],
            "forum_handles": r['author'],
            "evidence_links": r['url'],
            "risk_tag": "COMMUNITY_FLAGGED_CARIGOLD",
            "source": f"CariGold Forum - Suspicious Business/Scheme Activities ({r['url']})"
        })

    with open('carigold_scammers_db.json', 'w', encoding='utf-8') as f:
        json.dump(carigold_db, f, indent=2, ensure_ascii=False)
    print(f"Saved {len(carigold_db)} threats to carigold_scammers_db.json")

    return carigold_db

if __name__ == '__main__':
    run_scraper()
