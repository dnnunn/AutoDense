#!/usr/bin/env python3
import os, re, csv, sys, hashlib
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
DOCS = [p for p in ROOT.rglob("*.md") if ".git" not in p.parts]

H1 = re.compile(r"^#\s+(.*)", re.M)
DATE = re.compile(r"(last[-_ ]?(updated|modified|verified)\s*[:=]\s*(\d{4}-\d{2}-\d{2}))", re.I)
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")

def sha(path): 
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()

out = ROOT / "docs_inventory.csv"
with out.open("w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["path","title","words","links","sha256","last_verified"])
    for p in sorted(DOCS):
        text = p.read_text(encoding="utf-8", errors="ignore")
        title = (H1.search(text).group(1).strip() if H1.search(text) else "")
        words = len(re.findall(r"\b\w+\b", text))
        links = ";".join(sorted(set(LINK.findall(text))))
        m = DATE.search(text)
        verified = (m.group(3) if m else "")
        w.writerow([str(p.relative_to(ROOT)), title, words, links, sha(p), verified])

print(f"Wrote {out}")