from __future__ import annotations
import json, argparse
from pathlib import Path

def main():
    ap = argparse.ArgumentParser(description="Merge per-task priors into a single JSON file.")
    ap.add_argument("--etbr", default=None)
    ap.add_argument("--sds", default=None)
    ap.add_argument("--colony", default=None)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    priors = {}
    if args.etbr and Path(args.etbr).exists():
        priors["etbr_agarose"] = json.loads(Path(args.etbr).read_text(encoding="utf-8"))
    if args.sds and Path(args.sds).exists():
        priors["sds_page"] = json.loads(Path(args.sds).read_text(encoding="utf-8"))
    if args.colony and Path(args.colony).exists():
        priors["colony_count"] = json.loads(Path(args.colony).read_text(encoding="utf-8"))
    Path(args.out).write_text(json.dumps(priors, indent=2), encoding="utf-8")
    print(args.out)

if __name__ == "__main__":
    main()
