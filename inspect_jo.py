import urllib.request

req = urllib.request.Request(
    'https://semakmule.rmp.gov.my/static/js/main.188e200d.js',
    headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
)

with urllib.request.urlopen(req, timeout=15) as resp:
    js = resp.read().decode('utf-8', errors='ignore')

pos = js.find('Jo=function')
print(js[pos:pos+600])
