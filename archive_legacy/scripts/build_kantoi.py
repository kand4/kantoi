import os
import sys
import shutil
import zipfile
import subprocess
import re

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "KantoiApp"))
BASE_DIR = os.path.abspath(os.path.dirname(__file__))

USER_HOME = os.path.expanduser("~")
AAPT2 = os.path.join(USER_HOME, r"AppData\Local\Android\Sdk\build-tools\32.0.0\aapt2.exe")
ANDROID_JAR = os.path.join(USER_HOME, r"AppData\Local\Android\Sdk\platforms\android-32\android.jar")
JAVAC = os.path.join(USER_HOME, r"Downloads\android\jdk17\jdk-17.0.19+10\bin\javac.exe")
JAVA = os.path.join(USER_HOME, r"Downloads\android\jdk17\jdk-17.0.19+10\bin\java.exe")
D8_JAR = os.path.join(USER_HOME, r"AppData\Local\Android\Sdk\build-tools\32.0.0\lib\d8.jar")
UBER_SIGNER = os.path.join(USER_HOME, r"Downloads\android\moviebox\uber-apk-signer.jar")

# Read SemVer dynamically from AndroidManifest.xml
manifest_xml_path = os.path.join(PROJECT_ROOT, "AndroidManifest.xml")
VERSION_NAME = "1.4.0"
if os.path.exists(manifest_xml_path):
    with open(manifest_xml_path, "r", encoding="utf-8") as mf:
        v_match = re.search(r'android:versionName="([^"]+)"', mf.read())
        if v_match:
            VERSION_NAME = v_match.group(1)

print("==================================================")
print(f"       KANTOI APP STANDALONE APK BUILDER         ")
print(f"       SemVer Release Version: v{VERSION_NAME}   ")
print("==================================================")

BUILD_DIR = os.path.join(PROJECT_ROOT, "build")
GEN_DIR = os.path.join(BUILD_DIR, "gen")
CLASSES_DIR = os.path.join(BUILD_DIR, "classes")
DEX_DIR = os.path.join(BUILD_DIR, "dex")

RES_ZIP = os.path.join(BUILD_DIR, "resources.zip")
BASE_APK = os.path.join(BUILD_DIR, "base.apk")
UNSIGNED_APK = os.path.join(BUILD_DIR, "Kantoi_unsigned.apk")
FINAL_RELEASE_APK = os.path.join(BASE_DIR, f"Kantoi_v{VERSION_NAME}_Release_signed.apk")

# 1. Clean and prepare build dirs
print("\n[1/6] Preparing build directories...")
os.makedirs(BUILD_DIR, exist_ok=True)
os.makedirs(GEN_DIR, exist_ok=True)
if os.path.exists(CLASSES_DIR):
    shutil.rmtree(CLASSES_DIR)
os.makedirs(CLASSES_DIR, exist_ok=True)
if os.path.exists(DEX_DIR):
    shutil.rmtree(DEX_DIR)
os.makedirs(DEX_DIR, exist_ok=True)

# 2. AAPT2 Compile
print("\n[2/6] Compiling resources with AAPT2...")
res_dir = os.path.join(PROJECT_ROOT, "res")
cmd_aapt2_compile = [AAPT2, "compile", "--dir", res_dir, "-o", RES_ZIP]
res = subprocess.run(cmd_aapt2_compile, capture_output=True, text=True)
if res.returncode != 0:
    print("ERROR in AAPT2 compile:")
    print(res.stderr)
    sys.exit(1)
print(f"Compiled resources -> {RES_ZIP} ({os.path.getsize(RES_ZIP)} bytes)")

# 3. AAPT2 Link
print("\n[3/6] Linking APK and generating R.java with AAPT2...")
manifest_xml = os.path.join(PROJECT_ROOT, "AndroidManifest.xml")
cmd_aapt2_link = [
    AAPT2, "link",
    "-I", ANDROID_JAR,
    RES_ZIP,
    "--manifest", manifest_xml,
    "-o", BASE_APK,
    "--java", GEN_DIR,
    "--auto-add-overlay"
]
res = subprocess.run(cmd_aapt2_link, capture_output=True, text=True)
if res.returncode != 0:
    print("ERROR in AAPT2 link:")
    print(res.stderr)
    sys.exit(1)
print(f"Linked base APK -> {BASE_APK} ({os.path.getsize(BASE_APK)} bytes)")

# 4. Compile Java sources
print("\n[4/6] Compiling Java source files with javac...")
java_srcs = []
for root, _, files in os.walk(os.path.join(PROJECT_ROOT, "src")):
    for f in files:
        if f.endswith(".java"):
            java_srcs.append(os.path.join(root, f))
for root, _, files in os.walk(GEN_DIR):
    for f in files:
        if f.endswith(".java"):
            java_srcs.append(os.path.join(root, f))

print(f"Found {len(java_srcs)} Java source files.")
cmd_javac = [
    JAVAC,
    "-cp", ANDROID_JAR,
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-d", CLASSES_DIR
] + java_srcs

res = subprocess.run(cmd_javac, capture_output=True, text=True)
if res.returncode != 0:
    print("ERROR in javac compilation:")
    print(res.stderr)
    sys.exit(1)

# Count compiled classes
compiled_classes = []
for root, _, files in os.walk(CLASSES_DIR):
    for f in files:
        if f.endswith(".class"):
            compiled_classes.append(os.path.join(root, f))
print(f"Compiled {len(compiled_classes)} class files successfully.")

# 5. Run D8 to generate classes.dex
print("\n[5/6] Converting bytecode to DEX using D8...")
d8_args_file = os.path.join(BUILD_DIR, "d8_classes.txt")
with open(d8_args_file, "w", encoding="utf-8") as af:
    for cls_path in compiled_classes:
        af.write(f"{cls_path}\n")

cmd_d8 = [
    JAVA,
    "-cp", D8_JAR,
    "com.android.tools.r8.D8",
    "--output", DEX_DIR,
    "--lib", ANDROID_JAR,
    "--min-api", "26",
    f"@{d8_args_file}"
]

res = subprocess.run(cmd_d8, capture_output=True, text=True)
if res.returncode != 0:
    print("ERROR in D8:")
    print(res.stderr)
    sys.exit(1)

dex_file = os.path.join(DEX_DIR, "classes.dex")
if not os.path.exists(dex_file):
    print("ERROR: classes.dex was not generated!")
    sys.exit(1)
print(f"DEX generated -> {dex_file} ({os.path.getsize(dex_file)} bytes)")

# 6. Package APK with assets and classes.dex
print("\n[6/6] Packaging APK and signing...")
if os.path.exists(UNSIGNED_APK):
    os.remove(UNSIGNED_APK)

asset_db = os.path.join(PROJECT_ROOT, "assets", "kantoi_threats.db")
print(f"Attaching asset: {asset_db} ({os.path.getsize(asset_db)} bytes)")

with zipfile.ZipFile(BASE_APK, 'r') as zin:
    with zipfile.ZipFile(UNSIGNED_APK, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            zout.writestr(item, data)
        
        # Add DEX
        print("  + Adding classes.dex")
        zout.write(dex_file, "classes.dex")
        
        # Add Database Asset (stored without compression or deflated)
        print("  + Adding assets/kantoi_threats.db")
        zout.write(asset_db, "assets/kantoi_threats.db")

print(f"Unsigned APK created -> {UNSIGNED_APK} ({os.path.getsize(UNSIGNED_APK)} bytes)")

# Sign with uber-apk-signer
print("Signing APK with uber-apk-signer...")
cmd_sign = [
    JAVA,
    "-jar", UBER_SIGNER,
    "-a", UNSIGNED_APK,
    "--out", BASE_DIR,
    "--allowResign"
]
res = subprocess.run(cmd_sign, capture_output=True, text=True)
print(res.stdout)
if res.stderr:
    print(res.stderr)

# Check signed output file in BASE_DIR
signed_candidates = [
    f for f in os.listdir(BASE_DIR)
    if f.startswith("Kantoi_unsigned") and f.endswith(".apk") and "aligned" in f
]

if signed_candidates:
    src_signed = os.path.join(BASE_DIR, signed_candidates[0])
    if os.path.exists(FINAL_RELEASE_APK):
        os.remove(FINAL_RELEASE_APK)
    shutil.move(src_signed, FINAL_RELEASE_APK)
    # clean up any .idsig if present
    idsig = src_signed + ".idsig"
    if os.path.exists(idsig):
        shutil.move(idsig, FINAL_RELEASE_APK + ".idsig")
    print(f"\n>>> SUCCESS! Final release APK created at:\n    {FINAL_RELEASE_APK}")
    print(f"    Size: {os.path.getsize(FINAL_RELEASE_APK):,} bytes")
else:
    print("Checking if direct signed file exists...")
    if os.path.exists(FINAL_RELEASE_APK):
        print(f"\n>>> SUCCESS! Final release APK: {FINAL_RELEASE_APK}")
    else:
        print("ERROR: Signed APK not found in directory!")
        sys.exit(1)

print("\nDone!")
