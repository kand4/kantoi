import sqlite3
import json

conn = sqlite3.connect('lowyat_threats.db')
c = conn.cursor()
c.execute("SELECT sql FROM sqlite_master WHERE type='table'")
for r in c.fetchall():
    print("TABLE SQL:\n", r[0])

c.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = [r[0] for r in c.fetchall()]
print("Tables in db:", tables)

for t in tables:
    c.execute(f"SELECT COUNT(1) FROM {t}")
    print(f"Total rows in {t}: {c.fetchone()[0]}")
    c.execute(f"SELECT * FROM {t} LIMIT 2")
    cols = [d[0] for d in c.description]
    for r in c.fetchall():
        print(f"Sample from {t}:", dict(zip(cols, r)))

conn.close()
