from __future__ import annotations
import os, json, random, subprocess
from pathlib import Path
from typing import Dict, Any, List, Tuple
import argparse, yaml

def run_cmd(cmd: List[str], cwd: str=None, timeout: int=0) -> Tuple[int,str,str]:
    p = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        out, err = p.communicate(timeout=timeout or None)
    except subprocess.TimeoutExpired:
        p.kill()
        return 124, "", "timeout"
    return p.returncode, out, err

def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))

def write_yaml(obj: dict, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    import yaml
    with open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(obj, f, sort_keys=False)

def sample_from_space(space: dict) -> dict:
    return {k: random.choice(v) for k,v in space.items()}

def apply_patch_to_config(base_cfg: dict, patch: dict) -> dict:
    out = dict(base_cfg)
    for dotted, val in patch.items():
        cur = out
        parts = dotted.split(".")
        for p in parts[:-1]:
            cur = cur.setdefault(p, {})
        cur[parts[-1]] = val
    return out

def score_from_metrics(metrics: dict, must_improve: List[str], qc: dict) -> float:
    score = 0.0; count = 0
    for m in must_improve:
        if m in metrics:
            score += float(metrics[m]); count += 1
    if count: score /= count
    penalty = 0.0
    for k,v in qc.items():
        if k.endswith("_min"):
            name = k[:-4]
            if name in metrics and float(metrics[name]) < float(v): penalty += 1.0
        if k.endswith("_max"):
            name = k[:-4]
            if name in metrics and float(metrics[name]) > float(v): penalty += 1.0
    return score - penalty

def main():
    ap = argparse.ArgumentParser(description="Fit preprocessing priors by random search over a dataset using existing runners.")
    ap.add_argument("--task", required=True, choices=["etbr_agarose","sds_page","colony_count"])
    ap.add_argument("--images-dir", required=True)
    ap.add_argument("--spec", required=True)
    ap.add_argument("--base-config", required=True)
    ap.add_argument("--workdir", default="audits/fit_priors_runs")
    ap.add_argument("--trials-per-image", type=int, default=12)
    ap.add_argument("--max-images", type=int, default=100)
    ap.add_argument("--runner-cmd", default=None)
    args = ap.parse_args()

    spec = yaml.safe_load(Path(args.spec).read_text(encoding="utf-8"))
    runner = args.runner_cmd or spec.get("runner",{}).get("run_cmd")
    if not runner:
        raise SystemExit("No runner command found. Provide --runner-cmd or set runner.run_cmd in spec.")
    space = {}
    for g in spec.get("search",{}).get("param_grids",[]):
        space[g["path"]] = g["values"]
    must_improve = spec.get("acceptance",{}).get("must_improve",[])
    qc = spec.get("qc",{})

    imgs = [p for p in Path(args.images_dir).glob("*") if p.suffix.lower() in (".png",".tif",".tiff",".jpg",".jpeg")]
    imgs = imgs[:args.max_images]
    results: Dict[str, Dict[str, Any]] = {}

    for img in imgs:
        best = None
        base_cfg = yaml.safe_load(Path(args.base_config).read_text(encoding="utf-8"))
        for t in range(args.trials_per_image):
            patch = sample_from_space(space)
            cfg = apply_patch_to_config(base_cfg, patch)
            run_dir = Path(args.workdir) / args.task / img.stem / f"trial_{t:02d}"
            cfg_path = run_dir / "config.yaml"
            outdir = run_dir / "out"
            write_yaml(cfg, cfg_path); outdir.mkdir(parents=True, exist_ok=True)
            cmd = runner.format(input=img.as_posix(), outdir=outdir.as_posix())
            code, out, err = run_cmd(cmd.split())
            report_path = outdir / "run_report.json"
            if code != 0 or not report_path.exists():
                score = -1e9; metrics = {}
            else:
                rr = read_json(report_path)
                metrics = rr.get("metrics",{})
                score = score_from_metrics(metrics, must_improve, qc)
            if (best is None) or (score > best["score"]):
                best = {"score": score, "patch": patch, "metrics": metrics}
        results[img.name] = best

    priors = {}
    for key in space.keys():
        vals = [res["patch"][key] for res in results.values() if res and "patch" in res]
        if not vals: continue
        if isinstance(vals[0], (str, bool)):
            probs = {}
            for v in vals: probs[v] = probs.get(v, 0) + 1
            total = sum(probs.values())
            for k in probs: probs[k] /= total
            priors_set = {"type": "categorical", "probs": probs}
        else:
            probs = {}
            for v in vals: probs[v] = probs.get(v, 0.0) + 1.0
            total = sum(probs.values())
            for k in probs: probs[k] /= total
            priors_set = {"type": "discrete", "probs": probs}
        priors[key] = priors_set

    out = Path("meta/priors") / f"{args.task}_priors.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(priors, indent=2), encoding="utf-8")
    print(out.as_posix())

if __name__ == "__main__":
    main()
