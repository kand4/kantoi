import urllib.request
import re

url = "https://whatsapp.checkleaked.cc/_nuxt/entry-C_x2dAup-c.js"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
with urllib.request.urlopen(req) as resp:
    content = resp.read().decode("utf-8", errors="ignore")

idx = 0
while True:
    pos = content.find("/api/search", idx)
    if pos == -1:
        break
    start = max(0, pos - 200)
    end = min(len(content), pos + 300)
    print("--- SNIPPET ---")
    print(content[start:end])
    idx = pos + 1
