from __future__ import annotations
import os, json
from pathlib import Path
from typing import Dict, Any, Optional, Tuple
import numpy as np
try:
    from PIL import Image
    PIL_OK = True
except Exception:
    PIL_OK = False
try:
    import yaml
except Exception as e:
    raise RuntimeError("pyyaml is required for preflight") from e

def load_image_gray(path: str) -> np.ndarray:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(path)
    arr = None
    if PIL_OK:
        img = Image.open(p)
        if img.mode in ("I;16", "I;16B", "I;16L"):
            arr = (np.array(img, dtype=np.float32) / 65535.0)
        else:
            arr = np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0
            r, g, b = arr[...,0], arr[...,1], arr[...,2]
            arr = 0.2126*r + 0.7152*g + 0.0722*b
    else:
        raise RuntimeError("Pillow not available; install pillow to enable preflight loaders")
    return np.clip(arr, 0.0, 1.0)

def compute_image_stats(img: np.ndarray) -> Dict[str, float]:
    p1, p99 = np.percentile(img, [1, 99])
    mean = float(img.mean())
    std = float(img.std())
    dy = np.abs(np.diff(img, axis=0)).mean()
    dx = np.abs(np.diff(img, axis=1)).mean()
    return {"p1": float(p1), "p99": float(p99), "mean": mean, "std": std,
            "grad_vert": float(dy), "grad_horz": float(dx)}

def _choose_polarity(stats: Dict[str,float]) -> str:
    dynamic = stats["p99"] - stats["p1"]
    if stats["mean"] < 0.45 and dynamic > 0.05:
        return "true"
    if stats["mean"] > 0.65 and dynamic > 0.05:
        return "false"
    return "auto"

def _scale_params_from_size(img: np.ndarray) -> Tuple[float,int]:
    H, W = img.shape[:2]
    sigma = max(1.0, W / 300.0)
    min_d = max(6, int(W / 110.0))
    return float(sigma), int(min_d)

def merge_with_priors(defaults: Dict[str,Any], priors: Optional[Dict[str,Any]]) -> Dict[str,Any]:
    if not priors: return defaults
    def deep_merge(a,b):
        if isinstance(a, dict) and isinstance(b, dict):
            out = dict(a)
            for k,v in b.items():
                out[k] = deep_merge(a.get(k), v) if k in a else v
            return out
        return b if b is not None else a
    return deep_merge(priors, defaults)

def save_yaml(d: Dict[str,Any], path: str):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(d, f, sort_keys=False)

def base_defaults_for_img(img: np.ndarray) -> Dict[str,Any]:
    st = compute_image_stats(img)
    inv = _choose_polarity(st)
    sigma, min_d = _scale_params_from_size(img)
    prom = 0.08 if (st["p99"] - st["p1"]) > 0.2 else 0.04
    return {
        "pre": {
            "invert_polarity": inv,
            "clip_percentiles": {"low": 1.0, "high": 99.0},
            "clahe": {"enabled": True, "clip_limit": 2.0, "tile_grid": [8,8]}
        },
        "detect": {
            "gaussian_sigma": sigma,
            "min_peak_distance_px": min_d,
            "prominence_frac": prom
        }
    }
