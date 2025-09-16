# autodense/vision/mw_calibration.py
from dataclasses import dataclass
from typing import List, Optional, Dict, Tuple
import numpy as np

COMMON_LADDERS = {
    "broad":   [250, 150, 100, 75, 50, 37, 25, 20, 15, 10],
    "pageruler": [180, 130, 100, 70, 55, 40, 35, 25, 15, 10],
    "presto":  [200, 140, 100, 80, 60, 50, 40, 30, 25, 20, 15, 10]
}

@dataclass
class MWFit:
    ladder_name: str
    a: float
    b: float
    r2: float

def fit_semilog(distances_px: List[float], kda: List[float]) -> Optional[MWFit]:
    if len(distances_px) < 5 or len(kda) < 5:
        return None
    d = np.array(distances_px, dtype=np.float32)
    d = (d - d.min()) / (d.max() - d.min() + 1e-6)
    y = np.log10(np.array(kda[:len(d)], dtype=np.float32) + 1e-6)
    A = np.vstack([d, np.ones_like(d)]).T
    a, b = np.linalg.lstsq(A, y, rcond=None)[0]
    yhat = a*d + b
    r2 = float(1 - np.sum((y - yhat)**2) / (np.sum((y - y.mean())**2) + 1e-9))
    return MWFit(ladder_name="custom", a=float(a), b=float(b), r2=r2)

def try_common_ladders(distances_px: List[float]) -> Optional[MWFit]:
    best = None
    for name, kda in COMMON_LADDERS.items():
        f = fit_semilog(distances_px, kda)
        if f is None: 
            continue
        f.ladder_name = name
        if (best is None) or (f.r2 > best.r2):
            best = f
    return best

def estimate_band_kda(dist_px: float, fit: MWFit) -> float:
    # log10(kDa) = a * d_norm + b
    # Need the same normalization used at fit-time; caller should pass normalized distance
    # Here we assume dist_px is already normalized 0..1 distance from well origin.
    log10_kda = fit.a * dist_px + fit.b
    return float(10 ** log10_kda)
