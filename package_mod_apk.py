import zipfile
import os
import shutil
import subprocess

base_apk = os.path.join("_base_backup", "truecaller_original_base.apk") if os.path.exists(os.path.join("_base_backup", "truecaller_original_base.apk")) else "truecaller.apk"
unsigned_apk = "Truecaller_v26.38.50_LowyatMod_unsigned.apk"
signed_apk_name = "Truecaller_v26.38.50_LowyatThreatIntelligence_MOD_signed.apk"

print("Step 1: Repackaging APK from clean base:", base_apk)

with zipfile.ZipFile(base_apk, 'r') as zin:
    with zipfile.ZipFile(unsigned_apk, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            # Skip signature files so uber-apk-signer can sign fresh
            if item.filename.startswith("META-INF/") and (item.filename.endswith(".SF") or item.filename.endswith(".RSA") or item.filename.endswith(".MF")):
                continue
            
            # Replace classes5.dex with our patched version
            if item.filename == "classes5.dex":
                print("Replacing classes5.dex with patched version...")
                zout.write("test_classes5_rebuilt.dex", "classes5.dex")
            elif item.filename == "classes6.dex":
                print("Replacing classes6.dex with patched Compose version...")
                zout.write("test_classes6_rebuilt.dex", "classes6.dex")
            else:
                data = zin.read(item.filename)
                zout.writestr(item, data)
        
        # Add classes10.dex
        print("Adding classes10.dex...")
        zout.write("classes10_build/classes.dex", "classes10.dex")
        
        # Add classes11.dex
        print("Adding classes11.dex...")
        zout.write("test_classes11_rebuilt.dex", "classes11.dex")
        
        # Add assets/lowyat_threats.db
        print("Adding assets/lowyat_threats.db...")
        zout.write("lowyat_threats.db", "assets/lowyat_threats.db")

print("Unsigned APK created successfully. Size:", os.path.getsize(unsigned_apk))

print("Step 2: Signing APK with uber-apk-signer...")
uber_signer = os.path.join(os.path.expanduser("~"), r"Downloads\android\moviebox\uber-apk-signer.jar")
java_exe = os.path.join(os.path.expanduser("~"), r"Downloads\android\jdk17\jdk-17.0.19+10\bin\java.exe")

sign_cmd = [
    java_exe,
    "-jar", uber_signer,
    "-a", unsigned_apk,
    "--allowResign",
    "--overwrite"
]

res = subprocess.run(sign_cmd, capture_output=True, text=True)
print("Signer output:", res.stdout)
if res.stderr:
    print("Signer stderr:", res.stderr)

if os.path.exists(unsigned_apk):
    shutil.copy2(unsigned_apk, signed_apk_name)
    print("Final release APK copied to:", signed_apk_name)
    print("Final APK Size:", os.path.getsize(signed_apk_name))
else:
    print("ERROR: Signed APK not found!")
