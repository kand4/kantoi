import json
import re

with open('carigold_threads_list.json', 'r', encoding='utf-8') as f:
    threads = json.load(f)

print('Total threads:', len(threads))

keywords = ['scam', 'penipu', 'tipu', 'awas', 'hati-hati', 'perangkap', 'mule', 'akaun', 'fraud', 'polis', 'laporan', 'suspek', 'dikehendaki', 'kerugian']
scam_threads = []
for t in threads:
    title_lower = t['title'].lower()
    has_kw = any(k in title_lower for k in keywords)
    has_phone = bool(re.search(r'\b01\d[- ]?\d{7,8}\b', t['title']))
    if has_kw or has_phone:
        scam_threads.append(t)

print(f'Threads matching scam keywords or phone pattern in title: {len(scam_threads)}')
for st in scam_threads[:20]:
    print(f"[{st['date']}] {st['title']} -> {st['url']}")
