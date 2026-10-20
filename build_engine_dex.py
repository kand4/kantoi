import os
import shutil
import subprocess

android_jar = r'C:\Users\fisaa\AppData\Local\Android\Sdk\platforms\android-32\android.jar'
javac_exe = r'C:\Users\fisaa\Downloads\android\jdk17\jdk-17.0.19+10\bin\javac.exe'
java_exe = r'C:\Users\fisaa\Downloads\android\jdk17\jdk-17.0.19+10\bin\java.exe'
d8_jar = r'C:\Users\fisaa\AppData\Local\Android\Sdk\build-tools\32.0.0\lib\d8.jar'

threat_dir = os.path.join('src_patch', 'com', 'truecaller', 'threat')

# 1. Clean previous .class files
for f in os.listdir(threat_dir):
    if f.endswith('.class'):
        os.remove(os.path.join(threat_dir, f))

# 2. Compile Java sources
java_files = [os.path.join(threat_dir, f) for f in os.listdir(threat_dir) if f.endswith('.java')]
print("Compiling Java files:", java_files)

cmd_javac = [javac_exe, '-cp', android_jar, '-encoding', 'UTF-8', '-source', '1.8', '-target', '1.8'] + java_files
res = subprocess.run(cmd_javac, capture_output=True, text=True)
print("Javac returncode:", res.returncode)
if res.stdout:
    print("Javac stdout:", res.stdout)
if res.stderr:
    print("Javac stderr:", res.stderr)

if res.returncode != 0:
    print("ERROR: Javac compilation failed!")
    exit(1)

# 3. Collect class files
class_files = [os.path.join(threat_dir, f) for f in os.listdir(threat_dir) if f.endswith('.class')]
print("Compiled classes count:", len(class_files))

os.makedirs('classes10_build', exist_ok=True)
if os.path.exists('classes10_build/classes.dex'):
    os.remove('classes10_build/classes.dex')

cmd_d8 = [java_exe, '-cp', d8_jar, 'com.android.tools.r8.D8', '--output', 'classes10_build', '--lib', android_jar] + class_files
res_d8 = subprocess.run(cmd_d8, capture_output=True, text=True)
print("D8 returncode:", res_d8.returncode)
if res_d8.stdout:
    print("D8 stdout:", res_d8.stdout)
if res_d8.stderr:
    print("D8 stderr:", res_d8.stderr)

if os.path.exists('classes10_build/classes.dex'):
    print("D8 success! Output size:", os.path.getsize('classes10_build/classes.dex'))
else:
    print("ERROR: classes.dex was not generated!")
    exit(1)
