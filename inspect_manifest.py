import os
import subprocess

aapt = os.path.join(os.path.expanduser("~"), r"AppData\Local\Android\Sdk\build-tools\32.0.0\aapt2.exe")
cmd = [aapt, 'dump', 'xmltree', '_base_backup/truecaller_original_base.apk', '--file', 'AndroidManifest.xml']
res = subprocess.run(cmd, capture_output=True, text=True, errors='ignore')

lines = res.stdout.splitlines()
current_tag = None
current_name = None
actions = []

for line in lines:
    line_s = line.strip()
    if line_s.startswith('E: service') or line_s.startswith('E: receiver'):
        if current_name and ('call' in current_name.lower() or any('call' in a.lower() for a in actions)):
            print(f'[{current_tag}] {current_name}: {actions}')
        current_tag = line_s.split()[1]
        current_name = None
        actions = []
    elif line_s.startswith('A: http://schemas.android.com/apk/res/android:name'):
        val = line_s.split('="')[1].rstrip('")')
        if current_name is None:
            current_name = val
        elif 'action' in line.lower():
            actions.append(val)

if current_name and ('call' in current_name.lower() or any('call' in a.lower() for a in actions)):
    print(f'[{current_tag}] {current_name}: {actions}')
