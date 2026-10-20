
with open(r'C:\Users\fisaa\.gemini\antigravity\brain\4722c81a-3024-452d-ba19-045cf9141738\.system_generated\steps\5177\content.md', 'r', encoding='utf-8') as f:
    t = f.read()
import re
# print all text between <body and </body>
body = re.findall(r'<body.*?</body>', t, re.DOTALL)
if body:
    print('Body len:', len(body[0]))
    # strip tags
    clean = re.sub(r'<[^>]+>', ' ', body[0])
    clean = re.sub(r'\s+', ' ', clean)
    print('Clean text:', clean[:2000])
