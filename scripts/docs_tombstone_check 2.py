#!/usr/bin/env python3
import sys, re
from pathlib import Path
from datetime import datetime, timedelta
import os

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
MAX_AGE_DAYS = int(os.environ.get("TOMBSTONE_MAX_AGE_DAYS", "180"))

FRONT_MATTER = re.compile(r"^---\s*\n(.*?)\n---\s*", re.S)
KV = re.compile(r"^([A-Za-z0-9_-]+)\s*:\s*(.+?)\s*$")
DATE_FMT = "%Y-%m-%d"

def parse_front_matter(text:str):
    m = FRONT_MATTER.match(text)
    if not m: return {}
    block = m.group(1)
    meta = {}
    for line in block.splitlines():
        kvm = KV.match(line.strip())
        if kvm:
            meta[kvm.group(1).strip()] = kvm.group(2).strip()
    return meta

def is_md(p:Path):
    return p.suffix.lower() == ".md" and ".git" not in p.parts

def parse_date(s:str, key:str, path:Path, errs:list):
    try:
        return datetime.strptime(s, DATE_FMT).date()
    except Exception:
        errs.append(f"[{path}] invalid date for `{key}`: {s!r} (expected YYYY-MM-DD)")
        return None

def main():
    errors = []
    md_files = [p for p in ROOT.rglob("*.md") if is_md(p)]
    today = datetime.utcnow().date()
    cutoff = today - timedelta(days=MAX_AGE_DAYS)

    for p in sorted(md_files):
        text = p.read_text(encoding="utf-8", errors="ignore")
        meta = parse_front_matter(text)
        if not meta: continue
        if meta.get("status","").lower() != "deprecated":
            continue

        # Required keys
        req = ["deprecated_on","replaced_by","owner","last-verified"]
        for k in req:
            if k not in meta or not meta[k].strip():
                errors.append(f"[{p}] missing required front-matter key: `{k}`")

        # Dates
        dep = parse_date(meta.get("deprecated_on",""), "deprecated_on", p, errors) if "deprecated_on" in meta else None
        ver = parse_date(meta.get("last-verified",""), "last-verified", p, errors) if "last-verified" in meta else None

        if ver:
            if ver < cutoff:
                errors.append(f"[{p}] last-verified {ver} is older than {MAX_AGE_DAYS} days (cutoff {cutoff}); refresh required")

            if dep and ver < dep:
                errors.append(f"[{p}] last-verified {ver} is earlier than deprecated_on {dep} (should be same day or later)")

        # replaced_by resolution
        rb = meta.get("replaced_by","")
        if rb:
            # Allow leading slash; treat as repo-root absolute
            rb_clean = rb[1:] if rb.startswith("/") else rb
            target = (ROOT / rb_clean).resolve()
            if not target.exists():
                errors.append(f"[{p}] replaced_by points to missing file: {rb} (looked for: {target.relative_to(ROOT)})")
            elif target.is_dir():
                errors.append(f"[{p}] replaced_by path is a directory, expected a file: {rb}")

    if errors:
        print("Tombstone check failed:\n")
        for e in errors:
            print(" - " + e)
        sys.exit(1)
    else:
        print(f"Tombstone check passed ✅ (max age {MAX_AGE_DAYS} days)")

if __name__ == "__main__":
    main()