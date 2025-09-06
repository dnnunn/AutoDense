#!/usr/bin/env python
from __future__ import annotations
import argparse, json, shutil, sys, os
from pathlib import Path
import yaml
import subprocess

def run(cmd: list[str]) -> tuple[int,str,str]:
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, err = p.communicate()
    return p.returncode, out, err

def read_json(p: Path) -> dict:
    return json.loads(p.read_text(encoding="utf-8"))

def write_yaml(obj: dict, p: Path):
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        yaml.safe_dump(obj, f, sort_keys=False)

def load_yaml(p: Path) -> dict:
    if not p.exists(): return {}
    return yaml.safe_load(p.read_text(encoding="utf-8")) or {}

def tweak_for_rescue(cfg: dict) -> dict:
    out = json.loads(json.dumps(cfg))
    # toggle polarity
    inv = out.get("pre",{}).get("invert_polarity","auto")
    if isinstance(inv, bool):
        out.setdefault("pre",{})["invert_polarity"] = (not inv)
    elif isinstance(inv, str):
        if inv.lower() == "true":  out.setdefault("pre",{})["invert_polarity"] = "false"
        elif inv.lower() == "false": out.setdefault("pre",{})["invert_polarity"] = "true"
        else: out.setdefault("pre",{})["invert_polarity"] = "true"
    # reduce prominence and bump smoothing
    det = out.setdefault("detect",{})
    det["prominence_frac"] = float(max(0.01, det.get("prominence_frac", 0.08) * 0.33))
    det["gaussian_sigma"] = float(max(1.0, det.get("gaussian_sigma", 2.0) * 1.6))
    # colony knobs
    post = out.setdefault("post",{})
    post["merge_distance_px"] = int(max(3, int(post.get("merge_distance_px", 5)) + 2))
    seg = out.setdefault("segmentation",{})
    seg["min_area"] = int(max(10, int(seg.get("min_area", 30)) - 10))
    return out

def zeroish(metrics: dict, task: str) -> bool:
    if task == "colony_count":
        return int(metrics.get("colony_count", 0)) == 0
    lanes = int(metrics.get("lane_count", 0))
    bands = int(metrics.get("band_count", 0))
    return lanes == 0 or bands == 0

def pick_better(task: str, m0: dict, m1: dict) -> int:
    # Return 0 or 1 (which metrics set is "better")
    if task == "colony_count":
        c0, c1 = int(m0.get("colony_count",0)), int(m1.get("colony_count",0))
        return 1 if c1 > c0 else 0
    # For gels: prefer whichever has more lanes, then more bands
    lanes0, lanes1 = int(m0.get("lane_count",0)), int(m1.get("lane_count",0))
    if lanes1 != lanes0:
        return 1 if lanes1 > lanes0 else 0
    b0, b1 = int(m0.get("band_count",0)), int(m1.get("band_count",0))
    return 1 if b1 > b0 else 0

def main():
    ap = argparse.ArgumentParser(description="Preflight -> run via java_bridge -> optional rescue retry if zero features.")
    ap.add_argument("--task", required=True, choices=["etbr_agarose","sds_page","colony_count"])
    ap.add_argument("--input", required=True)
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--base-config", required=True, help="Base YAML config for the task")
    ap.add_argument("--priors-json", default=None)
    ap.add_argument("--java-main", default=os.environ.get("AUTODENSE_MAIN_CLASS"))
    ap.add_argument("--pom-dir", default=os.environ.get("POM_DIR"))
    args = ap.parse_args()

    outdir = Path(args.outdir)
    outdir.mkdir(parents=True, exist_ok=True)

    # 1) Preflight into adjusted config
    adj_cfg = outdir / "preflight.yaml"
    cmd_pf = [
        sys.executable, "-m", "autodense_autotune.preflight.apply_preflight",
        "--task", args.task, "--input", args.input,
        "--base-config", args.base_config, "--out-config", adj_cfg.as_posix()
    ]
    if args.priors_json:
        cmd_pf += ["--priors-json", args.priors_json]
    code, out, err = run(cmd_pf)
    if code != 0 or not adj_cfg.exists():
        print(err, file=sys.stderr)
        sys.exit(code if code != 0 else 1)

    # 2) First pass via java_bridge
    p0 = outdir / "pass0"
    p0.mkdir(parents=True, exist_ok=True)
    cmd0 = [
        sys.executable, "-m", "autodense_autotune.java_bridge",
        "--task", args.task, "--input", args.input, "--outdir", p0.as_posix(),
        "--config", adj_cfg.as_posix()
    ]
    if args.java_main: cmd0 += ["--java-main", args.java_main]
    if args.pom_dir:   cmd0 += ["--pom-dir", args.pom_dir]
    code0, out0, err0 = run(cmd0)
    rep0 = p0 / "run_report.json"
    if not rep0.exists():
        print(err0, file=sys.stderr)
        sys.exit(code0 if code0 != 0 else 1)
    m0 = read_json(rep0).get("metrics", {})

    # 3) Rescue if zeroish
    used_rescue = False
    rep_final = rep0
    src_dir = p0
    if zeroish(m0, args.task):
        used_rescue = True
        rescue_cfg = tweak_for_rescue(yaml.safe_load(adj_cfg.read_text(encoding="utf-8")))
        adj_rescue = outdir / "preflight_rescue.yaml"
        write_yaml(rescue_cfg, adj_rescue)
        p1 = outdir / "rescue"
        p1.mkdir(parents=True, exist_ok=True)
        cmd1 = [
            sys.executable, "-m", "autodense_autotune.java_bridge",
            "--task", args.task, "--input", args.input, "--outdir", p1.as_posix(),
            "--config", adj_rescue.as_posix()
        ]
        if args.java_main: cmd1 += ["--java-main", args.java_main]
        if args.pom_dir:   cmd1 += ["--pom-dir", args.pom_dir]
        code1, out1, err1 = run(cmd1)
        rep1 = p1 / "run_report.json"
        if rep1.exists():
            m1 = read_json(rep1).get("metrics", {})
            winner = pick_better(args.task, m0, m1)
            rep_final = rep1 if winner == 1 else rep0
            src_dir = p1 if winner == 1 else p0

    # 4) Copy the winning artifacts to outdir root for downstream tools
    final_json = outdir / "run_report.json"
    shutil.copy2(rep_final, final_json)
    # Mirror overlay if present
    rr = read_json(rep_final)
    diag = rr.get("diagnostics_png")
    if diag:
        diag_p = Path(diag)
        if not diag_p.is_absolute():
            diag_p = src_dir / diag_p
        if diag_p.exists():
            # copy overlay to root and patch JSON path to relative filename
            dst = outdir / diag_p.name
            shutil.copy2(diag_p, dst)
            rr["diagnostics_png"] = dst.name
            final_json.write_text(json.dumps(rr, indent=2), encoding="utf-8")
    # Mark meta flags
    rr.setdefault("meta", {})["rescue_used"] = used_rescue
    final_json.write_text(json.dumps(rr, indent=2), encoding="utf-8")
    print(final_json.as_posix())

if __name__ == "__main__":
    main()
