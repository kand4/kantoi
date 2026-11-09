import urllib.request
import re

req = urllib.request.Request(
    'https://semakmule.rmp.gov.my/static/js/main.188e200d.js',
    headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
)

with urllib.request.urlopen(req, timeout=15) as resp:
    js = resp.read().decode('utf-8', errors='ignore')

print('JS length:', len(js))

# Find references to apiEndPoint or apiEndPointMule
endpoints = set(re.findall(r'apiEndPoint(?:Mule)?\s*\+\s*["\']([^"\']+)["\']', js))
print('Endpoints found with apiEndPoint:', endpoints)

# Look for phone/bank query patterns
for m in re.finditer(r'apiEndPoint[a-zA-Z0-9_\s\+\'\"\/]+', js):
    snippet = js[max(0, m.start()-50):min(len(js), m.end()+150)]
    print('Snippet:', snippet)
    print('---')
