# autodense/legacy/mask_ops.py
"""
Minimal, well-tested Python replacements for common ImageJ macro steps.
These are intentionally thin wrappers over scikit-image / SciPy so they are easy to swap or extend.

Functions map roughly to ImageJ semantics:
- run("8-bit")                 -> to_8bit
- run("Convert to Mask")       -> otsu_mask (with optional blur), or threshold_mask
- run("Fill Holes")            -> fill_holes
- Erode/Dilate/Open/Close      -> erode_mask / dilate_mask / open_mask / close_mask
- Analyze Particles            -> regionprops_df (after label_mask)
"""

from __future__ import annotations
from typing import Iterable, Optional, Sequence, Tuple, Dict, Any
import numpy as np

try:
    import scipy.ndimage as ndi
except Exception:  # optional
    ndi = None

from skimage import filters, morphology, measure, exposure, util

# ----------------------- Utilities -----------------------

def _percentiles(a: np.ndarray, lo: float, hi: float) -> Tuple[float, float]:
    a = np.asarray(a)
    a = a[np.isfinite(a)]
    if a.size == 0:
        return 0.0, 1.0
    return (np.percentile(a, lo), np.percentile(a, hi))

def _to_float01(img: np.ndarray) -> np.ndarray:
    # convert to float in [0,1] based on dtype range
    img = np.asarray(img)
    if np.issubdtype(img.dtype, np.floating):
        v = img.copy()
        lo, hi = np.nanmin(v), np.nanmax(v)
        if hi > lo:
            v = (v - lo) / (hi - lo)
        else:
            v = np.zeros_like(v, dtype=float)
        return v.astype(float, copy=False)
    if np.issubdtype(img.dtype, np.integer):
        info = np.iinfo(img.dtype)
        return (img.astype(np.float32) - info.min) / (info.max - info.min)
    # fallback
    v = img.astype(np.float32)
    lo, hi = np.nanmin(v), np.nanmax(v)
    return (v - lo) / (hi - lo + 1e-9)

# ----------------------- Public API -----------------------

def to_8bit(img: np.ndarray, clip_percentile: Tuple[float,float]=(0.5, 99.5), per_channel: bool=True) -> np.ndarray:
    """
    Convert any image to uint8 using robust percentile clipping.
    If per_channel=True and image has shape (H,W,C), scale each channel independently.
    """
    arr = np.asarray(img)
    if arr.ndim == 3 and per_channel and arr.shape[2] <= 4:
        out = np.empty_like(arr, dtype=np.uint8)
        for c in range(arr.shape[2]):
            lo, hi = _percentiles(arr[..., c], *clip_percentile)
            if hi <= lo: 
                out[..., c] = 0
            else:
                x = np.clip(arr[..., c], lo, hi)
                x = (x - lo) / (hi - lo)
                out[..., c] = np.round(x * 255).astype(np.uint8)
        return out
    else:
        lo, hi = _percentiles(arr, *clip_percentile)
        if hi <= lo:
            return np.zeros(arr.shape, dtype=np.uint8)
        x = np.clip(arr, lo, hi)
        x = (x - lo) / (hi - lo)
        return np.round(x * 255).astype(np.uint8)

def threshold_mask(img: np.ndarray, thr: float, greater_equal: bool=True) -> np.ndarray:
    """Binary mask at a fixed threshold in the image's native scale."""
    img = np.asarray(img)
    return (img >= thr) if greater_equal else (img > thr)

def otsu_mask(img: np.ndarray, blur_sigma: float=0.0, min_size: int=0, fill: bool=True) -> Tuple[np.ndarray, float]:
    """
    Compute a binary mask by global Otsu threshold. Returns (mask, threshold).
    - blur_sigma: optional Gaussian blur to stabilize the histogram.
    - min_size: remove small objects below this area (pixels).
    - fill: fill interior holes.
    """
    arr = _to_float01(img)
    if blur_sigma and blur_sigma > 0:
        # skimage filters.gaussian preserves range; no color channel assumed here.
        arr = filters.gaussian(arr, sigma=blur_sigma, preserve_range=True)
    thr = filters.threshold_otsu(arr)
    mask = arr >= thr
    if min_size and min_size > 0:
        mask = morphology.remove_small_objects(mask, min_size=min_size)
    if fill:
        if ndi is not None:
            mask = ndi.binary_fill_holes(mask)
        else:
            mask = morphology.remove_small_holes(mask, area_threshold=max(16, min_size or 0))
    return mask.astype(bool), float(thr)

def fill_holes(mask: np.ndarray, area_threshold: Optional[int]=None) -> np.ndarray:
    """
    Fill holes in a binary mask. If SciPy is present, uses binary_fill_holes.
    Otherwise uses remove_small_holes with a large area_threshold.
    """
    m = np.asarray(mask).astype(bool)
    if ndi is not None and area_threshold is None:
        return ndi.binary_fill_holes(m)
    area = 10_000 if area_threshold is None else int(area_threshold)
    return morphology.remove_small_holes(m, area_threshold=area)

def remove_small(mask: np.ndarray, min_size: int) -> np.ndarray:
    """Remove connected components smaller than min_size pixels."""
    return morphology.remove_small_objects(mask.astype(bool), min_size=int(min_size))

def keep_largest(mask: np.ndarray, n: int=1) -> np.ndarray:
    """Keep the n largest connected components."""
    lab = measure.label(mask.astype(bool), connectivity=2)
    if lab.max() == 0:
        return mask.astype(bool)
    areas = [(i, (lab==i).sum()) for i in range(1, lab.max()+1)]
    areas.sort(key=lambda t: t[1], reverse=True)
    keep = {i for i,_ in areas[:max(1,int(n))]}
    return np.isin(lab, list(keep))

def erode_mask(mask: np.ndarray, radius: int=1) -> np.ndarray:
    se = morphology.disk(int(radius))
    return morphology.erosion(mask.astype(bool), footprint=se)

def dilate_mask(mask: np.ndarray, radius: int=1) -> np.ndarray:
    se = morphology.disk(int(radius))
    return morphology.dilation(mask.astype(bool), footprint=se)

def open_mask(mask: np.ndarray, radius: int=1) -> np.ndarray:
    se = morphology.disk(int(radius))
    return morphology.opening(mask.astype(bool), footprint=se)

def close_mask(mask: np.ndarray, radius: int=1) -> np.ndarray:
    se = morphology.disk(int(radius))
    return morphology.closing(mask.astype(bool), footprint=se)

def label_mask(mask: np.ndarray, connectivity: int=2) -> np.ndarray:
    """Return a labeled image (0 = background)."""
    return measure.label(mask.astype(bool), connectivity=int(connectivity))

def regionprops_df(mask: np.ndarray, intensity: Optional[np.ndarray]=None,
                   props: Sequence[str]=("label","area","bbox","centroid","eccentricity","perimeter")):
    """
    Tabular properties for each connected component.
    If intensity is provided, you may request intensity props (e.g., 'mean_intensity').
    Returns a pandas.DataFrame.
    """
    import pandas as pd
    tbl = measure.regionprops_table(mask.astype(int), intensity_image=intensity, properties=list(props))
    return pd.DataFrame(tbl)

__all__ = [
    "to_8bit", "threshold_mask", "otsu_mask", "fill_holes", "remove_small", "keep_largest",
    "erode_mask", "dilate_mask", "open_mask", "close_mask", "label_mask", "regionprops_df"
]
