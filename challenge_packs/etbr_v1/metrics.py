"""EtBr agarose gel metrics utilities.

Dependencies: numpy, scipy, scikit-image (for peak finding and filtering).
"""
from __future__ import annotations
import numpy as np
from typing import Dict, Optional, Sequence, Tuple
from scipy.stats import linregress
from scipy.ndimage import gaussian_filter, sobel

def ladder_linear_r2(band_positions_px: Sequence[float], ladder_bp: Sequence[float]) -> float:
    """Compute R² of linear fit between migration distance and log(bp).
    band_positions_px: sequence of y (migration) distances for ladder bands (in pixels).
    ladder_bp: matching basepair sizes (e.g., [10000, 8000, 6000, ...]).
    Returns R² in [0,1].
    """
    band_positions_px = np.asarray(band_positions_px, dtype=float)
    ladder_bp = np.asarray(ladder_bp, dtype=float)
    assert band_positions_px.size == ladder_bp.size and band_positions_px.size >= 3, "Need >=3 matched bands"
    x = band_positions_px
    y = np.log10(ladder_bp)
    res = linregress(x, y)
    return float(res.rvalue**2)

def background_snr(image: np.ndarray, band_mask: np.ndarray) -> float:
    """Compute mean(signal)/std(background).
    band_mask: boolean mask of detected band pixels (True where band).
    Background is image where band_mask==False.
    """
    sig = image[band_mask]
    bg = image[~band_mask]
    if sig.size == 0 or bg.size == 0:
        return 0.0
    return float(np.mean(sig) / (np.std(bg)+1e-9))

def smearing_index(image: np.ndarray, band_mask: np.ndarray, lane_axis: int = 0) -> float:
    """A simple, interpretable smear metric in [0,1] (lower is better).

    We estimate smear as the normalized edge spread around bands:
    1) Blur to suppress noise.
    2) Compute gradient magnitude along the vertical axis (migration axis).
    3) In band neighborhoods, compute the ratio of low-frequency energy to gradient energy.
    4) Normalize to [0,1].
    """
    img = gaussian_filter(image.astype(float), 1.0)
    # vertical gradient (assuming lanes run vertically)
    gy = sobel(img, axis=0)
    # focus on band neighborhoods: dilate band_mask a little by convolution box
    from scipy.ndimage import uniform_filter
    band_neigh = uniform_filter(band_mask.astype(float), size=5) > 0.1
    grad_energy = np.mean(np.abs(gy)[band_neigh]) if np.any(band_neigh) else 0.0
    lowfreq_energy = np.mean(img[band_neigh]) if np.any(band_neigh) else 0.0
    if grad_energy <= 0:
        return 1.0
    raw = lowfreq_energy / (grad_energy + 1e-9)
    # squash to [0,1] via logistic; higher raw -> more smear
    val = 1.0 / (1.0 + np.exp(- (raw - 1.0)))
    return float(np.clip(val, 0.0, 1.0))

def compute_metrics(
    image: np.ndarray,
    band_mask: np.ndarray,
    ladder_band_positions_px: Optional[Sequence[float]] = None,
    ladder_bp: Optional[Sequence[float]] = None,
    lane_count: Optional[int] = None
) -> Dict[str, float]:
    out = {
        "background_snr": background_snr(image, band_mask),
        "smearing_index": smearing_index(image, band_mask),
    }
    if ladder_band_positions_px is not None and ladder_bp is not None:
        out["ladder_linear_r2"] = ladder_linear_r2(ladder_band_positions_px, ladder_bp)
    if lane_count is not None:
        out["lane_count"] = float(lane_count)
    return out
