import os
import zipfile

src_dir = "Kantoi_Win7_Server"
zip_name = "Kantoi_Win7_Server_Portable.zip"

print(f"Compressing {src_dir} into {zip_name}...")

exclude_exts = {".pyc", ".log"}
exclude_dirs = {"__pycache__"}

with zipfile.ZipFile(zip_name, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(src_dir):
        # Exclude pycache
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        for f in files:
            ext = os.path.splitext(f)[1].lower()
            if ext in exclude_exts:
                continue
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, start=os.path.dirname(src_dir))
            zf.write(full_path, rel_path)

size_mb = os.path.getsize(zip_name) / (1024 * 1024)
print(f"SUCCESS: {zip_name} created ({size_mb:.2f} MB)")
