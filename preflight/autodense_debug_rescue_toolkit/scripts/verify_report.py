#!/usr/bin/env python
import json, sys
from pathlib import Path

SCHEMA_KEYS = ["task","input_path","input_hash","metrics","diagnostics_png","meta"]

def main():
    p = Path(sys.argv[1]) if len(sys.argv)>1 else None
    if not p or not p.exists():
        print("Usage: verify_report.py <run_report.json>"); sys.exit(1)
    rr = json.loads(p.read_text(encoding="utf-8"))
    missing = [k for k in SCHEMA_KEYS if k not in rr]
    if missing:
        print("Missing keys:", ", ".join(missing)); sys.exit(2)
    if not isinstance(rr.get("metrics",{}), dict):
        print("metrics must be a dict"); sys.exit(3)
    print("OK:", p)

if __name__ == "__main__":
    main()
