import urllib.request
import re

req = urllib.request.Request(
    'https://semakmule.rmp.gov.my/static/js/main.188e200d.js',
    headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
)

with urllib.request.urlopen(req, timeout=15) as resp:
    js = resp.read().decode('utf-8', errors='ignore')

# find searchData call
pos = js.find('searchData')
while pos != -1:
    snippet = js[max(0, pos-100):min(len(js), pos+300)]
    print('Snippet around searchData:')
    print(snippet)
    print('---')
    pos = js.find('searchData', pos + 1)
