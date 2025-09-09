# scripts/acceptance_check.py
import argparse, json, csv
from pathlib import Path

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--reports-dir", type=Path, required=True, help="Directory containing per-image report.json files")
    ap.add_argument("--out-csv", type=Path, required=True)
    args = ap.parse_args()

    rows = []
    for rp in args.reports_dir.rglob("report.json"):
        rec = json.loads(rp.read_text())
        obs = rec.get("observer", {})
        rows.append({
            "image": rec.get("image"),
            "lanes": obs.get("lanes"),
            "markers": obs.get("markers"),
            "empty_frac": obs.get("empty_frac"),
            "lane_width_cv": obs.get("lane_width_cv"),
            "accepted": obs.get("accepted"),
        })
    args.out_csv.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out_csv, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()) if rows else ["image","lanes","markers","empty_frac","lane_width_cv","accepted"])
        w.writeheader(); w.writerows(rows)
    print("[ok] acceptance summary ->", args.out_csv)

if __name__ == "__main__":
    main()
