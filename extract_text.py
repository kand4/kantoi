
import sys
sys.stdout.reconfigure(encoding='utf-8')
with open(r'C:\Users\fisaa\.gemini\antigravity\brain\4722c81a-3024-452d-ba19-045cf9141738\.system_generated\steps\5177\content.md', 'r', encoding='utf-8') as f:
    t = f.read()
import re
t = re.sub(r'<style.*?</style>', '', t, flags=re.DOTALL)
t = re.sub(r'<script.*?</script>', '', t, flags=re.DOTALL)
clean = re.sub(r'<[^>]+>', ' ', t)
clean = re.sub(r'\s+', ' ', clean)
print('Visible text:')
print(clean[:3000])
