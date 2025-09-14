# autodense/preprocess/policy.py
"""
Guarded preprocessing policy.
- Inspect the RAW image to compute diagnostics (no transforms).
- Propose at most one minimal transform (deskew, destripe, background, CLAHE) IF metrics warrant it.
- Accept a transform only if objective metrics improve by configured margins.
- Return the chosen image, metrics before/after, and a provenance record.

This is deliberately independent of the pipeline's internals so it can run even if
you swap implementations. It uses scikit-image + numpy only.
"""

from __future__ import annotations
from dataclasses import dataclass, asdict
from typing import Dict, Any, Optional, Tuple, List, Literal
import numpy as np
from PIL import Image

from skimage import color, exposure, filters, morphology, transform, feature

try:
    from scipy.ndimage import gaussian_filter, gaussian_filter1d
    HAS_SCIPY = True
except Exception:
    HAS_SCIPY = False
    def gaussian_filter(a, sigma, **kw):  # type: ignore
        from skimage.filters import gaussian as _g
        return _g(a, sigma=sigma, preserve_range=True)
    def gaussian_filter1d(a, sigma, **kw):  # poor man's 1d via 2d gaussian
        from skimage.filters import gaussian as _g
        return _g(a, sigma=sigma, preserve_range=True)

@dataclass
class PolicyThresholds:
    snr_gain: float = 0.20        # +20% SNR
    sep_gain: float = 0.15        # +15% lane separability
    skew_min_deg: float = 0.5     # run deskew if |skew| > 0.5°
    stripe_ratio_min: float = 1.25 # run destripe if col/row noise ratio > 1.25
    illum_amp_min: float = 0.10   # run background if amplitude > 0.10 (on [0,1])

@dataclass
class PolicyOutcome:
    mode: Literal["none","deskew","destripe","background","clahe"]
    params: Dict[str, Any]
    before: Dict[str, float]
    after: Dict[str, float]
    image: np.ndarray             # float image in [0,1]

def _to_gray_float(img: np.ndarray | Image.Image) -> np.ndarray:
    if isinstance(img, Image.Image):
        arr = np.asarray(img.convert("RGB"))
        g = color.rgb2gray(arr).astype(np.float32)
    else:
        arr = np.asarray(img)
        if arr.ndim == 2:
            g = arr.astype(np.float32)
            if g.max() > 1.5: g = g/255.0
        elif arr.ndim == 3:
            g = color.rgb2gray(arr).astype(np.float32)
        else:
            raise ValueError("Unsupported image shape")
    # normalize robustly
    p1, p99 = np.percentile(g, [1,99])
    if p99 <= p1: return np.zeros_like(g, dtype=np.float32)
    return np.clip((g - p1)/(p99-p1), 0, 1).astype(np.float32)

def estimate_skew_deg(g: np.ndarray) -> float:
    edges = feature.canny(g, sigma=2.0)
    h, theta, _ = transform.hough_line(edges)
    if theta.size == 0: return 0.0
    ang = theta[np.argmax(h.max(axis=0))]
    return float(np.rad2deg(ang) - 90.0)

def destripe_colmedian(g: np.ndarray) -> np.ndarray:
    col_med = np.median(g, axis=0)
    return np.clip(g - col_med + np.median(col_med), 0, 1).astype(np.float32)

def illumination_amplitude(g: np.ndarray) -> float:
    # large-scale background via big gaussian; amplitude = max-min
    sigma = max(g.shape)/8.0
    bg = gaussian_filter(g, sigma=sigma/6.0)  # soften a lot
    return float(np.clip(bg.max() - bg.min(), 0, 1))

def lane_separability(g: np.ndarray) -> float:
    """Horizontal (x) projection peak-valley separability in [0,1] scale."""
    # Emphasize vertical structures
    radius = max(10, int(min(g.shape)*0.03))
    fp = morphology.disk(radius)
    # Treat bands as bright by inverting if necessary based on histogram tail
    p5, p50, p95 = np.percentile(g, [5,50,95])
    g2 = g if (p95 - p50) > (p50 - p5) else (1.0 - g)
    enh = morphology.white_tophat(g2, footprint=fp)
    xprof = enh.sum(axis=0)
    xprof = gaussian_filter1d(xprof, sigma=max(3, g.shape[1]//200))
    # Normalize
    if xprof.max() <= 1e-6: return 0.0
    x = (xprof - xprof.min())/(xprof.max()-xprof.min()+1e-6)
    # separability = mean top 10% - mean bottom 10%
    k = max(3, int(0.1*len(x)))
    top = np.sort(x)[-k:].mean()
    bot = np.sort(x)[:k].mean()
    return float(np.clip(top - bot, 0, 1))

def snr_proxy(g: np.ndarray) -> float:
    """Robust SNR-like score without band masks."""
    med = np.median(g)
    low = g[g <= med]
    bg_std = float(low.std() + 1e-6)
    p95 = float(np.percentile(g, 95))
    return float((p95 - med) / bg_std)

def stripe_ratio(g: np.ndarray) -> float:
    col_std = np.std(np.median(g, axis=0))
    row_std = np.std(np.median(g, axis=1))
    return float(col_std / max(row_std, 1e-6))

def polarity(g: np.ndarray) -> str:
    p5, p50, p95 = np.percentile(g, [5, 50, 95])
    return "bright" if (p95 - p50) > (p50 - p5) else "dark"

def background_step(g: np.ndarray, pol: str) -> np.ndarray:
    r = max(12, int(min(g.shape)*0.03))
    fp = morphology.disk(r)
    if pol == "bright":
        out = morphology.white_tophat(g, footprint=fp)
    else:
        out = morphology.black_tophat(g, footprint=fp)
        out = np.clip(g + out, 0, 1)
        return out.astype(np.float32)
    return out.astype(np.float32)

def clahe_step(g: np.ndarray) -> np.ndarray:
    u8 = (np.clip(g,0,1)*255).astype(np.uint8)
    return exposure.equalize_adapthist(u8).astype(np.float32)

def deskew_step(g: np.ndarray) -> Tuple[np.ndarray, float]:
    deg = estimate_skew_deg(g)
    if abs(deg) < 0.3:
        return g, 0.0
    g2 = transform.rotate(g, deg, resize=False, preserve_range=True).astype(np.float32)
    return g2, float(deg)

def evaluate(g: np.ndarray) -> Dict[str, float]:
    return dict(
        snr=snr_proxy(g),
        sep=lane_separability(g),
        skew=estimate_skew_deg(g),
        stripe_ratio=stripe_ratio(g),
        illum_amp=illumination_amplitude(g)
    )

def guarded_preprocess(image: np.ndarray | Image.Image,
                       thresholds: PolicyThresholds | None = None,
                       max_candidates: int = 3) -> PolicyOutcome:
    if thresholds is None: thresholds = PolicyThresholds()
    g0 = _to_gray_float(image)
    m0 = evaluate(g0)
    pol = polarity(g0)

    proposals: List[Tuple[str, Dict[str, Any]]] = []

    if abs(m0["skew"]) > thresholds.skew_min_deg:
        proposals.append(("deskew", {}))
    if m0["stripe_ratio"] > thresholds.stripe_ratio_min:
        proposals.append(("destripe", {}))
    if m0["illum_amp"] > thresholds.illum_amp_min:
        proposals.append(("background", {"polarity": pol}))
    if m0["snr"] < 2.0:
        proposals.append(("clahe", {}))

    # Evaluate candidates, accept first that passes gains
    for name, params in proposals[:max_candidates]:
        if name == "deskew":
            g1, deg = deskew_step(g0)
            cand_params = {"deg": deg}
        elif name == "destripe":
            g1 = destripe_colmedian(g0); cand_params = {}
        elif name == "background":
            g1 = background_step(g0, pol=params.get("polarity", pol)); cand_params = {"polarity": pol}
        elif name == "clahe":
            g1 = clahe_step(g0); cand_params = {}
        else:
            continue
        m1 = evaluate(g1)
        gain_snr = (m1["snr"] - m0["snr"]) / max(m0["snr"], 1e-6)
        gain_sep = (m1["sep"] - m0["sep"]) / max(m0["sep"], 1e-6)
        if gain_snr >= thresholds.snr_gain and gain_sep >= thresholds.sep_gain:
            return PolicyOutcome(name, cand_params, m0, m1, g1)

    # No accepted candidate → return raw
    return PolicyOutcome("none", {}, m0, m0, g0)
