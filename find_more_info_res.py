import zipfile, sys

sys.stdout.reconfigure(encoding='utf-8')

with zipfile.ZipFile('_base_backup/truecaller_original_base.apk', 'r') as z:
    for name in z.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            data = z.read(name)
            for phrase in [b'More info available', b'more info available', b'who viewed', b'Who viewed', b'Call History', b'call history']:
                pos = data.find(phrase)
                if pos != -1:
                    print(f'{name} contains "{phrase.decode()}" at {pos}')
