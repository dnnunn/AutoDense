# PATCH to autodense/scripts/cli_batch.py to add --preproc-mode guarded/manual/off
from __future__ import annotations
import argparse, json
from pathlib import Path
from PIL import Image
import numpy as np

from autodense.preprocess.pipeline import PreprocParams, run as preproc_run
from autodense.preprocess.policy import guarded_preprocess, PolicyThresholds
from autodense.orchestrator.pipeline import Params, run

def main():
    ap = argparse.ArgumentParser(description="AutoDense batch: preprocess → analyze → sidecars")
    ap.add_argument("--dir", required=True, help="Directory of input images")
    ap.add_argument("--modality", choices=["sds","dna"], default="sds")
    ap.add_argument("--comb", type=int, default=None)
    ap.add_argument("--min-lanes", type=int, default=6)
    ap.add_argument("--max-lanes", type=int, default=20)
    ap.add_argument("--bg-method", default="auto")
    ap.add_argument("--bg-radius", default="auto")
    ap.add_argument("--polarity", default="auto")
    ap.add_argument("--clahe", action="store_true")
    ap.add_argument("--no-clahe", dest="clahe", action="store_false")
    ap.add_argument("--deskew", action="store_true")
    ap.add_argument("--preproc-mode", choices=["ai_guarded","manual","off"], default="ai_guarded",
                    help="ai_guarded (recommended): metrics-gated single-step; manual: PreprocParams; off: raw image")
    ap.add_argument("--out", default="out")
    ap.set_defaults(clahe=True, deskew=True)

    args = ap.parse_args()
    in_dir = Path(args.dir); out_dir = Path(args.out); out_dir.mkdir(parents=True, exist_ok=True)

    pp = PreprocParams(modality=args.modality, comb_hint=args.comb, polarity=args.polarity,
                       bg_method=args.bg_method, bg_radius_px=args.bg_radius, clahe=args.clahe, deskew=args.deskew)
    det_params = Params(modality=args.modality, min_lanes=args.min_lanes, max_lanes=args.max_lanes, comb=args.comb)

    manifest = {"modality": args.modality, "preproc_mode": args.preproc_mode, "images": []}

    for p in sorted(in_dir.glob("*")):
        if p.suffix.lower() not in (".png",".jpg",".jpeg",".tif",".tiff"):
            continue
        pre_dir = out_dir / p.stem / "preproc"
        pre_dir.mkdir(parents=True, exist_ok=True)

        img = Image.open(p).convert("RGB")

        if args.preproc_mode == "ai_guarded":
            outcome = guarded_preprocess(img)
            prepped = outcome.image
            pre_meta = {
                "policy_mode": outcome.mode,
                "policy_params": outcome.params,
                "metrics_before": outcome.before,
                "metrics_after": outcome.after
            }
            # save a couple of stage images for visibility
            Image.fromarray((np.clip((prepped*255),0,255)).astype("uint8")).save(pre_dir/"prepped.png")
            (pre_dir/"policy.json").write_text(json.dumps(pre_meta, indent=2))
        elif args.preproc_mode == "manual":
            prepped, meta, _ = preproc_run(img, pp, save_dir=pre_dir, save_prefix="")
            pre_meta = {"pipeline": meta.__dict__}
            Image.fromarray((np.clip((prepped*255),0,255)).astype("uint8")).save(pre_dir/"prepped.png")
        else:  # off
            prepped = np.asarray(img.convert("L")).astype(np.float32)
            prepped = (prepped - prepped.min())/(prepped.max()-prepped.min()+1e-6)
            Image.fromarray((np.clip((prepped*255),0,255)).astype("uint8")).save(pre_dir/"prepped.png")
            pre_meta = {"pipeline": "off"}

        tmp = pre_dir / "prepped.png"
        res, p_final, obs = run(tmp, det_params, retries=1)

        manifest["images"].append({
            "file": str(p),
            "preproc": pre_meta,
            "metrics": obs,
            "params": p_final.__dict__,
            "accepted": bool(obs.get("accepted")) if isinstance(obs, dict) else None
        })

    (out_dir/"manifest.json").write_text(json.dumps(manifest, indent=2))
    print(f"[done] wrote {out_dir/'manifest.json'}")

if __name__ == "__main__":
    main()
