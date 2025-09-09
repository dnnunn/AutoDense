# scripts/infer_one.py
import argparse, csv
from pathlib import Path
from PIL import Image
from autodense.orchestrator.pipeline import Params, run
from autodense.vision.analyzer import draw_overlay
from autodense.vision.reporting import write_report

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--modality", choices=["sds","dna"], default="sds")
    ap.add_argument("--min-lanes", type=int, default=6)
    ap.add_argument("--max-lanes", type=int, default=16)
    ap.add_argument("--comb", type=int, default=None)
    ap.add_argument("--retries", type=int, default=1)
    args = ap.parse_args()

    p = Params(modality=args.modality, min_lanes=args.min_lanes, max_lanes=args.max_lanes, comb=args.comb)
    res, p_final, obs = run(args.image, p, retries=args.retries)
    args.out.mkdir(parents=True, exist_ok=True)
    im = Image.open(args.image).convert("RGB")
    draw_overlay(im, res, args.out / f"{args.image.stem}_overlay.png")

    with open(args.out / "lanes.csv", "w", newline="") as f:
        w = csv.writer(f); w.writerow(["image","lane","type","x0","x1","y0","y1","band_count"])
        for ln in res.lanes:
            w.writerow([args.image.name, ln.index, ln.type, ln.x0, ln.x1, ln.y0, ln.y1, len(ln.bands)])
    with open(args.out / "bands.csv", "w", newline="") as f:
        w = csv.writer(f); w.writerow(["image","lane","type","band","y0","y1","intensity","confidence"])
        for ln in res.lanes:
            for b in ln.bands:
                w.writerow([args.image.name, ln.index, ln.type, b.index, b.y0, b.y1, b.intensity, b.confidence])

    write_report(args.image, res, obs, p_final.__dict__, args.out / "report.json")
    print("[ok] wrote overlay, CSV, report.json to", args.out)

if __name__ == "__main__":
    main()
