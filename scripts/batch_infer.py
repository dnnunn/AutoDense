# scripts/batch_infer.py
import argparse, csv
from pathlib import Path
from autodense.orchestrator.pipeline import Params, run
from PIL import Image
from autodense.vision.analyzer import draw_overlay
from autodense.vision.reporting import write_report
from autodense.export.coco import export_coco
from autodense.export.yolo import export_yolo_txt

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--modality", choices=["sds","dna"], default="sds")
    ap.add_argument("--min-lanes", type=int, default=6)
    ap.add_argument("--max-lanes", type=int, default=16)
    ap.add_argument("--comb", type=int, default=None)
    ap.add_argument("--retries", type=int, default=1)
    ap.add_argument("--export-coco", type=Path, default=None)
    ap.add_argument("--export-yolo", type=Path, default=None)
    args = ap.parse_args()

    exts = {".png",".jpg",".jpeg",".tif",".tiff"}
    imgs = [p for p in args.dir.rglob("*") if p.suffix.lower() in exts]
    args.out.mkdir(parents=True, exist_ok=True)

    lanes_all = open(args.out/"lanes.csv","w",newline=""); wl = csv.writer(lanes_all); wl.writerow(["image","lane","type","x0","x1","y0","y1","band_count"])
    bands_all = open(args.out/"bands.csv","w",newline=""); wb = csv.writer(bands_all); wb.writerow(["image","lane","type","band","y0","y1","intensity","confidence"])

    p = Params(modality=args.modality, min_lanes=args.min_lanes, max_lanes=args.max_lanes, comb=args.comb)

    for img in imgs:
        res, p_final, obs = run(img, p, retries=args.retries)
        im = Image.open(img).convert("RGB")
        out_dir = args.out / img.stem
        out_dir.mkdir(parents=True, exist_ok=True)
        draw_overlay(im, res, out_dir / f"{img.stem}_overlay.png")
        for ln in res.lanes:
            wl.writerow([img.name, ln.index, ln.type, ln.x0, ln.x1, ln.y0, ln.y1, len(ln.bands)])
            for b in ln.bands:
                wb.writerow([img.name, ln.index, ln.type, b.index, b.y0, b.y1, b.intensity, b.confidence])
        write_report(img, res, obs, p_final.__dict__, out_dir / "report.json")
        if args.export_coco:
            export_coco(res, img.name, args.export_coco / f"{img.stem}.json")
        if args.export_yolo:
            export_yolo_txt(res, args.export_yolo)
        print("[ok]", img)

    lanes_all.close(); bands_all.close()
    print("[done] wrote batch outputs to", args.out)

if __name__ == "__main__":
    main()
