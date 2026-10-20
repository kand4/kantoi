import urllib.request
import re

req = urllib.request.Request(
    'https://semakmule.rmp.gov.my/static/js/main.188e200d.js',
    headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
)

with urllib.request.urlopen(req, timeout=15) as resp:
    js = resp.read().decode('utf-8', errors='ignore')

# Search for calls to sa(
for m in re.finditer(r'sa\(', js):
    snippet = js[max(0, m.start()-150):min(len(js), m.end()+250)]
    print('Snippet around sa(:')
    print(snippet)
    print('---')
