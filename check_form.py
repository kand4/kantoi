
import sys, re
sys.stdout.reconfigure(encoding='utf-8')
with open(r'C:\Users\fisaa\.gemini\antigravity\brain\4722c81a-3024-452d-ba19-045cf9141738\.system_generated\steps\5177\content.md', 'r', encoding='utf-8') as f:
    t = f.read()

# find form tags, input names, action, etc.
forms = re.findall(r'<form.*?</form>', t, re.DOTALL)
print('Forms found:', len(forms))
for f in forms:
    print(f[:500])

# find buttons or inputs
inputs = re.findall(r'<input[^>]+>', t)
for inp in inputs[:10]:
    print('Input:', inp)
