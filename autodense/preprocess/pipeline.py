# autodense/preprocess/pipeline.py
from __future__ import annotations
from dataclasses import dataclass, asdict
from typing import Optional, Dict, Any, Tuple, Sequence, Union
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps
from skimage import exposure, filters, morphology, transform, feature
try:
    from scipy import ndimage as ndi
except Exception:
    ndi = None
try:
    from skimage.restoration import rolling_ball
    HAS_ROLLING = True
except Exception:
    HAS_ROLLING = False

@dataclass
class PreprocParams:
    modality: str = "sds"              # "sds" | "dna"
    comb_hint: Optional[int] = None    # 10,12,15,20 or None
    target_channel: str = "auto"       # "auto"|"R"|"G"|"B"
    polarity: str = "auto"             # "auto"|"bright"|"dark"
    bg_method: str = "auto"            # "auto"|"rolling_ball"|"tophat_white"|"tophat_black"
    bg_radius_px: Union[str,int] = "auto"
    denoise: str = "auto"              # "auto"|"none"|"median"|"gaussian"
    clahe: bool = True
    deskew: bool = True
    rectify: bool = False              # placeholder for perspective rectify

@dataclass
class PreprocMeta:
    channel: str
    polarity: str
    bg_method: str
    bg_radius_px: int
    denoise: str
    clahe: bool
    deskew_deg: float
    rectify: bool

def _exif_orient(img: Image.Image) -> Image.Image:
    try: return ImageOps.exif_transpose(img)
    except Exception: return img

def _choose_channel(arr: np.ndarray, target: str):
    if arr.ndim == 2:
        return arr.astype(np.float32), "gray"
    if target in ("R","G","B"):
        idx = {"R":0,"G":1,"B":2}[target]
        return arr[..., idx].astype(np.float32), target
    # auto: pick channel with best Otsu separability
    best_c, best_thr = 0, -1
    for c in range(min(arr.shape[2],3)):
        try: thr = filters.threshold_otsu(arr[..., c])
        except Exception: thr = -1
        if thr > best_thr: best_thr, best_c = thr, c
    return arr[..., best_c].astype(np.float32), {0:"R",1:"G",2:"B"}.get(best_c, f"C{best_c}")

def _normalize01(img: np.ndarray) -> np.ndarray:
    p1, p99 = np.percentile(img, [1, 99])
    if p99 <= p1: return np.zeros_like(img, dtype=np.float32)
    return np.clip((img - p1) / (p99 - p1), 0, 1).astype(np.float32)

def _detect_polarity(g: np.ndarray) -> str:
    p5, p50, p95 = np.percentile(g, [5, 50, 95])
    return "bright" if (p95 - p50) > (p50 - p5) else "dark"

def _bg_radius_auto(modality: str, comb_hint: Optional[int]) -> int:
    if comb_hint in (10,12,15,20): return 15 if modality=="dna" else 17
    return 17

def _background_correct(g: np.ndarray, polarity: str, method: str, radius: int):
    if method == "auto":
        method = "rolling_ball" if HAS_ROLLING else ("tophat_white" if polarity=="bright" else "tophat_black")
    if method == "rolling_ball" and HAS_ROLLING:
        bg = rolling_ball(g, radius=radius)
        return (g - bg if polarity == "bright" else g + bg), "rolling_ball"
    se = morphology.disk(radius)
    if method == "tophat_white" or (method=="auto" and polarity=="bright"):
        return morphology.white_tophat(g, selem=se), "tophat_white"
    if method == "tophat_black" or (method=="auto" and polarity=="dark"):
        return morphology.black_tophat(g, selem=se), "tophat_black"
    return (morphology.white_tophat(g, selem=se) if polarity=="bright" else morphology.black_tophat(g, selem=se),
            "tophat_white" if polarity=="bright" else "tophat_black")

def _denoise(g: np.ndarray, mode: str):
    if mode == "auto": mode = "gaussian"
    if mode == "median":
        if ndi is not None: return ndi.median_filter(g, size=3), "median"
        return filters.gaussian(g, sigma=0.6, preserve_range=True), "gaussian"
    if mode == "gaussian": return filters.gaussian(g, sigma=0.6, preserve_range=True), "gaussian"
    return g, "none"

def _clahe8(g: np.ndarray, enabled: bool) -> np.ndarray:
    if not enabled: return g
    u8 = (np.clip(g,0,1) * 255).astype(np.uint8)
    return exposure.equalize_adapthist(u8).astype(np.float32)

def _deskew(g: np.ndarray):
    edges = feature.canny(g, sigma=2.0)
    h, theta, _ = transform.hough_line(edges)
    if theta.size == 0: return g, 0.0
    ang = theta[np.argmax(h.max(axis=0))]
    rot_deg = np.rad2deg(ang) - 90.0
    if abs(rot_deg) < 0.3: return g, 0.0
    return transform.rotate(g, rot_deg, resize=False, preserve_range=True).astype(np.float32), float(rot_deg)

def run(image, params: Optional[PreprocParams]=None, save_dir: Optional[Path]=None, save_prefix: str=""):
    if params is None: params = PreprocParams()
    # Load
    if isinstance(image, (str, Path)): im = Image.open(str(image)).convert("RGB")
    elif isinstance(image, Image.Image): im = image.convert("RGB")
    elif isinstance(image, np.ndarray):
        if image.ndim == 2: im = Image.fromarray((np.clip(image,0,1)*255).astype(np.uint8))
        elif image.ndim == 3 and image.shape[2] in (3,4):
            im = Image.fromarray((np.clip(image[...,:3],0,1)*255).astype(np.uint8)) if image.dtype!=np.uint8 else Image.fromarray(image[...,:3])
        else: raise ValueError("Unsupported array shape for image")
    else: raise ValueError("Unsupported image type")

    im = _exif_orient(im)
    arr = np.asarray(im).astype(np.float32)
    stages: Dict[str, Any] = {"00_input": arr}

    # Channel + normalize
    gray, ch_label = _choose_channel(arr, params.target_channel); stages["10_gray"] = gray
    g = _normalize01(gray); stages["20_norm"] = g

    # Polarity + background
    pol = params.polarity if params.polarity in ("bright","dark") else _detect_polarity(g)
    radius = _bg_radius_auto(params.modality, params.comb_hint) if params.bg_radius_px == "auto" else int(params.bg_radius_px)
    g_bg, method_used = _background_correct(g, pol, params.bg_method, radius); stages["30_bg"] = g_bg

    # Denoise + CLAHE
    g_dn, dn_used = _denoise(g_bg, params.denoise); stages["40_denoise"] = g_dn
    g_eq = _clahe8(g_dn, params.clahe); stages["50_contrast"] = g_eq

    # Deskew
    g_sk, deg = _deskew(g_eq) if params.deskew else (g_eq, 0.0); stages["60_deskew"] = g_sk

    meta = PreprocMeta(channel=ch_label, polarity=pol, bg_method=method_used, bg_radius_px=int(radius),
                       denoise=dn_used, clahe=bool(params.clahe), deskew_deg=float(deg), rectify=bool(params.rectify))

    # Save stages + sidecar
    if save_dir is not None:
        save_dir = Path(save_dir); save_dir.mkdir(parents=True, exist_ok=True)
        from PIL import Image as _I, ImageOps as _Ops
        for key in sorted(stages.keys()):
            a = stages[key]
            a8 = (np.clip(a,0,1)*255).astype(np.uint8) if a.ndim==2 or a.dtype!=np.uint8 else a
            _I.fromarray(a8).save(save_dir / f"{save_prefix}{key}.png")
        import json as _json
        (save_dir / f"{save_prefix}preproc.json").write_text(_json.dumps(meta.__dict__, indent=2))

    return g_sk.astype(np.float32), meta, stages

def baseline_subtract_per_lane(img: np.ndarray, lanes: Sequence[tuple], win: int = 31):
    """Return lane_index -> baseline-corrected strip using per-row median baseline."""
    out = {}
    for i, (x0,y0,x1,y1) in enumerate(lanes):
        strip = img[int(y0):int(y1), int(x0):int(x1)]
        if strip.size == 0: continue
        base = np.median(strip, axis=1)
        if ndi is not None:
            try: base = ndi.median_filter(base, size=max(5, win//3))
            except Exception: pass
        corr = np.clip((strip.T - base).T, 0, 1).astype(np.float32)
        out[i] = corr
    return out

__all__ = ["PreprocParams", "PreprocMeta", "run", "baseline_subtract_per_lane"]
