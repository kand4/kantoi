import urllib.request
import re

url = "https://whatsapp.checkleaked.cc/_nuxt/entry-C_x2dAup-c.js"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req) as resp:
    code = resp.read().decode("utf-8", errors="ignore")

for m in re.finditer(r'/api/search', code):
    p = m.start()
    print("--- MATCH ---")
    print(code[max(0, p - 200):min(len(code), p + 400)])
