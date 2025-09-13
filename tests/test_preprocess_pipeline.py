# tests/test_preprocess_pipeline.py
import json
import numpy as np
import pytest
from pathlib import Path
from skimage import transform, feature

from autodense.preprocess.pipeline import PreprocParams, run as preproc_run, baseline_subtract_per_lane
from .util_synth import make_synth_gel

def estimate_angle_deg(img: np.ndarray) -> float:
    """Rudimentary dominant-line angle estimate like the pipeline's deskew step."""
    edges = feature.canny(img, sigma=2.0)
    h, theta, _ = transform.hough_line(edges)
    if theta.size == 0 or h.size == 0:
        return 0.0
    acc = h.max(axis=0)
    ang = theta[np.argmax(acc)]
    return float(np.rad2deg(ang) - 90.0)

@pytest.mark.parametrize("bright", [True, False])
def test_polarity_detection(bright):
    gel, _ = make_synth_gel(bright=bright, skew_deg=0.0)
    img = (np.clip(gel,0,1)*255).astype(np.uint8)
    pp = PreprocParams(modality="dna" if bright else "sds", polarity="auto", clahe=True, deskew=False)
    out, meta, stages = preproc_run(img, params=pp, save_dir=None)
    assert meta.polarity in ("bright","dark")
    if bright:
        assert meta.polarity == "bright"
    else:
        assert meta.polarity == "dark"

def test_background_correction_improves_snr():
    gel, band_rows = make_synth_gel(bright=True, skew_deg=0.0)
    img = (np.clip(gel,0,1)*255).astype(np.uint8)
    pp = PreprocParams(modality="dna", polarity="auto", bg_method="auto", clahe=False, deskew=False)
    out, meta, stages = preproc_run(img, params=pp, save_dir=None)

    pre = stages["20_norm"]
    post = stages["30_bg"]

    # Build a rough band mask using known band rows
    H, W = pre.shape
    band_mask = np.zeros((H,), dtype=bool)
    for r in band_rows:
        band_mask[max(0,r-3):min(H,r+4)] = True
    bg_mask = ~band_mask
    # SNR proxy: difference of means / std of background
    def snr(a):
        band_mean = a[band_mask].mean()
        bg_mean = a[bg_mask].mean()
        bg_std = a[bg_mask].std() + 1e-6
        return (band_mean - bg_mean) / bg_std
    snr_pre = snr(pre)
    snr_post = snr(post)
    assert snr_post > snr_pre * 1.2  # expect at least 20% improvement

def test_deskew_reduces_angle(tmp_path: Path):
    gel, _ = make_synth_gel(bright=False, skew_deg=2.0)  # dark-on-bright, skewed 2°
    img = (np.clip(gel,0,1)*255).astype(np.uint8)
    pp = PreprocParams(modality="sds", polarity="auto", clahe=True, deskew=True)
    out, meta, stages = preproc_run(img, params=pp, save_dir=tmp_path, save_prefix="t_")

    ang_before = estimate_angle_deg(stages["50_contrast"])
    ang_after  = estimate_angle_deg(stages["60_deskew"])
    # Should move closer to 0 by at least 0.5°
    assert abs(ang_after) < max(0.1, abs(ang_before) - 0.5)
    # sidecar files created
    assert (tmp_path/"t_preproc.json").exists()
    assert any(p.name.startswith("t_") and p.suffix==".png" for p in tmp_path.iterdir())

def test_baseline_subtract_per_lane():
    gel, _ = make_synth_gel(bright=True, skew_deg=0.0, lanes=8)
    img = (np.clip(gel,0,1)*255).astype(np.uint8)
    pp = PreprocParams(modality="dna", polarity="auto", clahe=False, deskew=False)
    out, meta, stages = preproc_run(img, params=pp, save_dir=None)
    H, W = out.shape
    lanes = 8
    margin = int(W*0.08)
    xs = np.linspace(margin, W-margin, lanes).astype(int)
    lane_w = max(6, W//(lanes*8))
    boxes = []
    for i, xc in enumerate(xs):
        x0 = max(0, xc - lane_w//2); x1 = min(W, xc + lane_w//2)
        boxes.append((x0, 0, x1, H))
    corr = baseline_subtract_per_lane(out, boxes, win=31)

    # Row medians should have reduced variance after baseline correction
    original_var = np.var(np.median(out[:, boxes[0][0]:boxes[0][2]], axis=1))
    corrected_var = np.var(np.median(corr[0], axis=1))
    assert corrected_var < original_var * 0.7
