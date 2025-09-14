#!/usr/bin/env python3
import re, sys
from pathlib import Path
ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
all_md = {p.resolve() for p in ROOT.rglob("*.md") if ".git" not in p.parts}
referenced = set()

for p in all_md:
    text = p.read_text(encoding="utf-8", errors="ignore")
    for href in LINK.findall(text):
        if href.startswith("http"): continue
        tgt = (p.parent / href).resolve()
        if tgt.suffix == "": tgt = tgt.with_suffix(".md")
        referenced.add(tgt)

orphans = [p for p in all_md if p not in referenced and "README.md" not in p.name]
for p in sorted(orphans):
    print(p.relative_to(ROOT))