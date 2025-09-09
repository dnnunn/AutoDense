#!/usr/bin/env python3
"""
autodense_codemod.py — migrate away from ImageJ macros and Java-era assets.

Usage:
  python autodense_codemod.py --repo /path/to/repo --mode report
  python autodense_codemod.py --repo /path/to/repo --mode apply [--git]

What it does:
- Scans for .ijm files and produces best-effort Python translations using
  autodense.legacy.mask_ops (you must have added mask_ops.py).
- Detects Java/ImageJ artifacts (pom.xml, Fiji.app bundles, CLASSPATH scripts).
- In 'apply' mode:
  * Writes translated Python files under autodense/legacy/translated/
  * Moves .ijm files to autodense/legacy/legacy_macros/
  * Writes MIGRATION_REPORT.md summarizing actions
  * Optionally creates a git branch and commits the changes.

Limitations:
- Macro translation is heuristic. Review the generated Python before merging.
"""

from __future__ import annotations
import argparse, re, shutil, subprocess, sys
from pathlib import Path
from typing import List, Dict

JAVA_TOKENS = [
    "ImageJ", "Fiji", "net.imagej", "scijava", "scifio", "loci.formats",
    "IJ.", ".ijm", "Bio-Formats"
]

MACRO_CMD_RE = re.compile(r'run\(\s*"([^"]+)"\s*(?:,\s*"([^"]*)")?\s*\)\s*;?', re.I)
SET_OPT_RE = re.compile(r'setOption\(\s*"([^"]+)"\s*,\s*(true|false)\s*\)\s*;?', re.I)
SET_THR_RE = re.compile(r'setThreshold\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)\s*;?', re.I)

TRANSLATE_MAP = {
    "8-bit": 'img = to_8bit(img)',
    "Convert to Mask": 'mask, thr = otsu_mask(img)',
    "Make Binary": 'mask, thr = otsu_mask(img)',
    "Fill Holes": 'mask = fill_holes(mask)',
    "Erode": 'mask = erode_mask(mask, radius=1)',
    "Dilate": 'mask = dilate_mask(mask, radius=1)',
    "Open": 'mask = open_mask(mask, radius=1)',
    "Close": 'mask = close_mask(mask, radius=1)',
}

def parse_macro_line(line: str) -> List[str]:
    out = []
    m = MACRO_CMD_RE.search(line)
    if m:
        cmd, args = m.group(1), (m.group(2) or "")
        cmd = cmd.strip()
        if cmd.lower().startswith("gaussian blur"):
            # Extract sigma or radius
            sigma = None
            for token in args.split(","):
                token = token.strip()
                if "=" in token:
                    k,v = token.split("=",1)
                    if k.strip().lower() in ("sigma","radius"):
                        try:
                            sigma = float(v.strip())
                        except ValueError:
                            pass
            if sigma is None:
                sigma = 1.0
            out.append(f"from skimage import filters")
            out.append(f"img = filters.gaussian(img, sigma={sigma}, preserve_range=True)")
        elif cmd in TRANSLATE_MAP:
            out.append(TRANSLATE_MAP[cmd])
        elif cmd.lower().startswith("analyze particles"):
            out.append("# NOTE: Analyze Particles → label_mask + regionprops_df; adapt filters as needed")
            out.append("lab = label_mask(mask)")
            out.append("tbl = regionprops_df(lab, None, props=('label','area','bbox','centroid'))")
        else:
            out.append(f"# TODO: Unhandled macro command: {cmd} ({args})")
        return out

    m = SET_THR_RE.search(line)
    if m:
        lo, hi = m.group(1), m.group(2)
        out.append(f"mask = (img >= {lo}) & (img <= {hi})  # threshold range from macro")
        return out

    m = SET_OPT_RE.search(line)
    if m:
        opt, val = m.group(1), m.group(2)
        out.append(f"# setOption('{opt}', {val})  # ignored")
        return out

    return out

def translate_ijm_to_python(ijm_path: Path, out_py: Path):
    lines = ijm_path.read_text(errors="ignore").splitlines()
    body: List[str] = []
    body.append("# Auto-generated from ImageJ macro; review before use.\n")
    body.append("from __future__ import annotations")
    body.append("import numpy as np")
    body.append("from autodense.legacy.mask_ops import (to_8bit, otsu_mask, fill_holes, erode_mask, dilate_mask, open_mask, close_mask, label_mask, regionprops_df)\n")
    body.append("def run_macro(img: np.ndarray):")
    body.append("    # img: numpy array (H,W) or (H,W,C)")
    body.append("    mask = None; thr = None")
    for ln in lines:
        cmds = parse_macro_line(ln)
        for c in cmds:
            body.append("    " + c)
    body.append("    return {'img': img, 'mask': mask, 'threshold': thr}")
    out_py.parent.mkdir(parents=True, exist_ok=True)
    out_py.write_text("\n".join(body))

def scan_repo(repo: Path) -> Dict[str, List[Path]]:
    ijm, java, pom, fiji, classpath = [], [], [], [], []
    for p in repo.rglob("*"):
        if not p.is_file():
            continue
        name = p.name.lower()
        if p.suffix.lower() == ".ijm":
            ijm.append(p)
        if name in ("pom.xml","build.gradle","settings.gradle"):
            pom.append(p)
        if "fiji.app" in str(p):
            fiji.append(p)
        if name.endswith(".java"):
            java.append(p)
        if "classpath" in name or "build_classpath" in name:
            classpath.append(p)
    return {"ijm": ijm, "java": java, "pom": pom, "fiji": fiji, "classpath": classpath}

def git_branch_and_commit(repo: Path, branch: str, message: str):
    try:
        subprocess.run(["git","-C",str(repo),"checkout","-b",branch], check=True, capture_output=True)
        subprocess.run(["git","-C",str(repo),"add","-A"], check=True, capture_output=True)
        subprocess.run(["git","-C",str(repo),"commit","-m",message], check=True, capture_output=True)
    except Exception as e:
        print(f"[git] skipped or failed: {e}")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, help="Path to repository root")
    ap.add_argument("--mode", choices=["report","apply"], default="report")
    ap.add_argument("--git", action="store_true", help="Create a branch and commit changes in apply mode")
    args = ap.parse_args()

    repo = Path(args.repo).resolve()
    assert repo.exists(), f"Repo not found: {repo}"

    found = scan_repo(repo)
    print("== Scan results ==")
    for k,v in found.items():
        print(f"{k}: {len(v)}")

    report = ["# AutoDense ImageJ→Python migration report\n"]
    for k,v in found.items():
        report.append(f"## {k} ({len(v)})")
        for p in v:
            report.append(f"- {p.relative_to(repo)}")
        report.append("")

    # Save report
    rep_path = repo / "MIGRATION_REPORT.md"
    rep_path.write_text("\n".join(report))
    print(f"[report] wrote {rep_path}")

    if args.mode == "apply":
        # Translate ijm files
        out_dir = repo / "autodense" / "legacy" / "translated"
        legacy_dir = repo / "autodense" / "legacy" / "legacy_macros"
        out_dir.mkdir(parents=True, exist_ok=True)
        legacy_dir.mkdir(parents=True, exist_ok=True)
        for ijm in found["ijm"]:
            base = ijm.stem
            out_py = out_dir / f"{base}_py.py"
            translate_ijm_to_python(ijm, out_py)
            # move original ijm
            dst = legacy_dir / ijm.name
            ijm.replace(dst)
            print(f"[translate] {ijm} -> {out_py} and moved macro to {dst}")

        # Advise deletions (java/pom/fiji/classpath)
        deletions_txt = repo / "autodense" / "legacy" / "DELETION_CANDIDATES.txt"
        lines = ["# Files safe to delete for Python-only migration:\n"]
        for k in ("java","pom","fiji","classpath"):
            if found[k]:
                lines.append(f"## {k}")
                for p in found[k]:
                    lines.append(str(p.relative_to(repo)))
                lines.append("")
        deletions_txt.write_text("\n".join(lines))
        print(f"[apply] wrote deletion candidates: {deletions_txt}")

        if args.git:
            git_branch_and_commit(repo, "feat/remove-imagej-era", "chore(migration): translate macros, move .ijm to legacy/, add deletion candidates & MIGRATION_REPORT")

if __name__ == "__main__":
    main()
