import urllib.request
import urllib.parse
from bs4 import BeautifulSoup
import time
import json
import re

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'ms,en-US;q=0.9,en;q=0.8'
}

BASE_URL = "https://carigold.com/forum/forums/suspicious-business-scheme-activities.182/"

def fetch_page(url, retries=3):
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=12) as resp:
                return resp.read().decode('utf-8', errors='ignore')
        except Exception as e:
            print(f"Error fetching {url} (attempt {i+1}): {e}")
            time.sleep(1.5)
    return None

def scrape_thread_list(max_pages=31):
    all_threads = []
    print(f"--- Fetching thread list from CariGold (Up to {max_pages} pages) ---")
    
    for page in range(1, max_pages + 1):
        page_url = BASE_URL if page == 1 else f"{BASE_URL}page-{page}"
        print(f"Fetching page {page}/{max_pages}: {page_url}")
        html = fetch_page(page_url)
        if not html:
            print(f"Failed to fetch page {page}")
            continue
            
        soup = BeautifulSoup(html, 'html.parser')
        items = soup.select('div.structItem--thread')
        if not items:
            print(f"No thread items found on page {page}. Stopping.")
            break
            
        page_threads = 0
        for item in items:
            title_el = item.select_one('div.structItem-title a[data-tp-primary]')
            if not title_el:
                title_el = item.select_one('div.structItem-title a')
            if not title_el:
                continue
                
            title = title_el.get_text(strip=True)
            href = title_el.get('href', '')
            if href.startswith('/'):
                href = "https://carigold.com" + href
                
            time_el = item.select_one('time.structItem-startDate')
            author_el = item.select_one('.structItem-parts a.username')
            
            # Extract reply count / views if present
            replies_el = item.select_one('dd.pairs--justified')
            
            all_threads.append({
                'title': title,
                'url': href,
                'author': author_el.get_text(strip=True) if author_el else '',
                'date': time_el.get_text(strip=True) if time_el else '',
                'page': page
            })
            page_threads += 1
            
        print(f"  Found {page_threads} threads on page {page}. (Total accumulated: {len(all_threads)})")
        time.sleep(0.5)
        
    return all_threads

if __name__ == '__main__':
    threads = scrape_thread_list(max_pages=31)
    with open('carigold_threads_list.json', 'w', encoding='utf-8') as f:
        json.dump(threads, f, indent=2, ensure_ascii=False)
    print(f"\nSaved {len(threads)} thread metadata entries to carigold_threads_list.json")
