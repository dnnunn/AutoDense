from __future__ import annotations
import argparse, json, os, sys, subprocess
from pathlib import Path
import tempfile

# Optional scikit-image dependencies for fallback mode
try:
    from skimage import io, filters, morphology, measure, exposure
    import numpy as np
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    SCIKIT_AVAILABLE = True
except Exception:
    SCIKIT_AVAILABLE = False
    io = filters = morphology = measure = exposure = None
    np = None
    plt = None

# Import metrics for fallback mode
try:
    from challenge_packs.sds_page_v1.metrics import compute_metrics as sds_metrics
    from challenge_packs.colony_count_v1.metrics import colony_metrics
    from challenge_packs.etbr_v1.metrics import compute_metrics as etbr_metrics
    METRICS_AVAILABLE = True
except Exception:
    METRICS_AVAILABLE = False

def sha12(path: str) -> str:
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1<<20), b""):
            h.update(b)
    return h.hexdigest()[:12]

# ---------- SDS-PAGE runner ----------
def run_sds_page(input_path: str, outdir: str) -> dict:
    out = Path(outdir); out.mkdir(parents=True, exist_ok=True)
    if io is None:
        raise RuntimeError("scikit-image is required for this runner")
    img = io.imread(input_path)
    if img.ndim == 3:
        # assume grayscale from RGB
        img = img[...,0]*0.299 + img[...,1]*0.587 + img[...,2]*0.114
    img = img.astype(float)
    # Normalize
    img = (img - img.min()) / (img.ptp() + 1e-9)
    # Coarse lane detection: vertical projection + peak finding
    vert_profile = img.sum(axis=0)
    if gaussian_filter1d:
        vp = gaussian_filter1d(vert_profile, 3)
    else:
        vp = vert_profile
    # Simple peak picking
    peaks = _simple_peaks(vp, min_prominence=0.05)
    # Build lane profiles by averaging columns around each lane center
    lane_profiles = []
    win = 5
    H, W = img.shape
    for x in peaks:
        x0 = max(0, x-win); x1 = min(W, x+win+1)
        lane_profiles.append(img[:, x0:x1].mean(axis=1))
    # Compute SDS metrics
    m = sds_metrics(lane_profiles=lane_profiles)
    # Simple overlay: mark lane centers
    overlay = _save_overlay_lanes(img, peaks, out/"sds_overlay.png")
    return {
        "task": "sds_page",
        "input_path": input_path,
        "input_hash": sha12(input_path),
        "metrics": m,
        "diagnostics_png": str(overlay),
        "meta": {"lane_centers": list(map(int, peaks))}
    }

# ---------- Colony counting runner ----------
def run_colony_count(input_path: str, outdir: str) -> dict:
    out = Path(outdir); out.mkdir(parents=True, exist_ok=True)
    if io is None:
        raise RuntimeError("scikit-image is required for this runner")
    img = io.imread(input_path)
    if img.ndim == 3:
        gray = img[...,0]*0.299 + img[...,1]*0.587 + img[...,2]*0.114
    else:
        gray = img.astype(float)
    gray = (gray - gray.min()) / (gray.ptp() + 1e-9)
    # Invert if light colonies on dark bg are detected
    if gray.mean() < 0.5:
        g = gray
    else:
        g = 1.0 - gray
    # Threshold (Otsu) and cleanup
    from skimage.filters import threshold_otsu
    T = threshold_otsu(g)
    mask = g > T
    from skimage.morphology import remove_small_objects, opening, disk
    mask = remove_small_objects(mask, 20)
    mask = opening(mask, disk(2))
    # Metrics
    metrics = colony_metrics(mask, neighbor_radius_px=5)
    # Overlay
    overlay = _save_overlay_mask(gray, mask, out/"colony_overlay.png")
    return {
        "task": "colony_count",
        "input_path": input_path,
        "input_hash": sha12(input_path),
        "metrics": metrics,
        "diagnostics_png": str(overlay),
        "meta": {}
    }

# ---------- EtBr agarose runner ----------
def run_etbr(input_path: str, outdir: str) -> dict:
    out = Path(outdir); out.mkdir(parents=True, exist_ok=True)
    if io is None:
        raise RuntimeError("scikit-image is required for this runner")
    img = io.imread(input_path)
    if img.ndim == 3:
        gray = img[...,0]*0.299 + img[...,1]*0.587 + img[...,2]*0.114
    else:
        gray = img.astype(float)
    gray = (gray - gray.min()) / (gray.ptp() + 1e-9)
    # Band mask via white top-hat to enhance bright bands
    from skimage.morphology import white_tophat, rectangle
    enh = white_tophat(gray, selem=rectangle(9,3))
    from skimage.filters import threshold_otsu
    T = threshold_otsu(enh)
    band_mask = enh > T
    # Lane count via vertical projection
    vp = gray.sum(axis=0)
    if gaussian_filter1d:
        vp = gaussian_filter1d(vp, 3)
    lanes = _simple_peaks(vp, min_prominence=0.05)
    metrics = etbr_metrics(image=gray, band_mask=band_mask, lane_count=len(lanes))
    overlay = _save_overlay_lanes(gray, lanes, out/"etbr_overlay.png", mask=band_mask)
    return {
        "task": "etbr_agarose",
        "input_path": input_path,
        "input_hash": sha12(input_path),
        "metrics": metrics,
        "diagnostics_png": str(overlay),
        "meta": {"lane_centers": list(map(int, lanes))}
    }

# ---------- Helpers ----------
def _simple_peaks(arr, min_prominence=0.05):
    arr = np.asarray(arr, dtype=float)
    # naive prominence: local maxima above baseline
    peaks = []
    baseline = np.percentile(arr, 10)
    for i in range(1, len(arr)-1):
        if arr[i] > arr[i-1] and arr[i] > arr[i+1] and (arr[i]-baseline) >= min_prominence*(arr.max()-baseline):
            peaks.append(i)
    # prune near-duplicates
    pruned = []
    for p in peaks:
        if not pruned or p - pruned[-1] > 8:
            pruned.append(p)
    return np.array(pruned, dtype=int)

def _save_overlay_lanes(gray_img, lane_centers, outpath, mask=None):
    H, W = gray_img.shape
    fig = plt.figure(figsize=(W/100, H/100), dpi=100)
    plt.imshow(gray_img, cmap="gray", interpolation="nearest")
    if mask is not None:
        # outline mask
        from skimage.segmentation import find_boundaries
        b = find_boundaries(mask, mode="outer")
        plt.contour(b, levels=[0.5], linewidths=0.8)
    for x in lane_centers:
        plt.axvline(int(x), linestyle="--", linewidth=0.8)
    plt.axis("off")
    fig.tight_layout(pad=0)
    outpath = Path(outpath)
    fig.savefig(outpath, bbox_inches="tight", pad_inches=0)
    plt.close(fig)
    return outpath.as_posix()

def _save_overlay_mask(gray_img, mask, outpath):
    H, W = gray_img.shape
    fig = plt.figure(figsize=(W/100, H/100), dpi=100)
    plt.imshow(gray_img, cmap="gray", interpolation="nearest")
    from skimage.segmentation import find_boundaries
    b = find_boundaries(mask, mode="outer")
    plt.contour(b, levels=[0.5], linewidths=0.8)
    plt.axis("off")
    fig.tight_layout(pad=0)
    outpath = Path(outpath)
    fig.savefig(outpath, bbox_inches="tight", pad_inches=0)
    plt.close(fig)
    return outpath.as_posix()

def main():
    ap = argparse.ArgumentParser(description="Sample runners that produce run_report.json for autotune.")
    ap.add_argument("task", choices=["sds_page","colony_count","etbr_agarose"])
    ap.add_argument("--input", required=True)
    ap.add_argument("--outdir", required=True)
    args = ap.parse_args()
    if args.task=="sds_page":
        report = run_sds_page(args.input, args.outdir)
    elif args.task=="colony_count":
        report = run_colony_count(args.input, args.outdir)
    else:
        report = run_etbr(args.input, args.outdir)
    Path(args.outdir).mkdir(parents=True, exist_ok=True)
    (Path(args.outdir)/"run_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))

if __name__ == "__main__":
    main()
