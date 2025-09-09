# autodense/vision/mw_calibration.py
from dataclasses import dataclass
from typing import List, Optional, Dict
import numpy as np

# --- Vendor catalogs ---
# Protein ladders (kDa)
PROTEIN_LADDERS: Dict[str, List[float]] = {
    # Thermo Scientific™ PageRuler™ Prestained Protein Ladder, 10 to 180 kDa
    # Source: Thermo Fisher page for Cat. 26616/26617: 180,130,100,70,55,40,35,25,15,10 kDa
    "pageruler_10_180": [180, 130, 100, 70, 55, 40, 35, 25, 15, 10],
}

# DNA ladders (bp)
DNA_LADDERS: Dict[str, List[int]] = {
    # NEB 1 kb DNA Ladder (N3232)
    # Source: NEB product page "Bases" table
    # Bands (bp): 10002, 8001, 6001, 5001, 4001, 3001, 2000, 1500, 1000, 517, 500
    "neb_1kb": [10002, 8001, 6001, 5001, 4001, 3001, 2000, 1500, 1000, 517, 500],
}

@dataclass
class MWFit:
    ladder_name: str
    a: float
    b: float
    r2: float
    units: str  # 'kDa' or 'bp'

def _fit_semilog(distances_norm: List[float], values: List[float]) -> Optional[MWFit]:
    if len(distances_norm) < 5 or len(values) < 5:
        return None
    d = np.asarray(distances_norm, dtype=np.float32)
    v = np.asarray(values[:len(d)], dtype=np.float32)
    y = np.log10(v + 1e-6)
    A = np.vstack([d, np.ones_like(d)]).T
    a, b = np.linalg.lstsq(A, y, rcond=None)[0]
    yhat = a*d + b
    r2 = float(1 - np.sum((y - yhat)**2) / (np.sum((y - y.mean())**2) + 1e-9))
    return a, b, r2

def try_ladder(distances_norm: List[float], modality: str, ladder_type: str="auto") -> Optional[MWFit]:
    modality = modality.lower()
    catalogs = PROTEIN_LADDERS if modality=="sds" else DNA_LADDERS
    units = "kDa" if modality=="sds" else "bp"
    if ladder_type and ladder_type!="auto":
        vals = catalogs.get(ladder_type)
        if not vals: 
            return None
        out = _fit_semilog(distances_norm, vals)
        if not out: 
            return None
        a,b,r2 = out
        return MWFit(ladder_name=ladder_type, a=float(a), b=float(b), r2=float(r2), units=units)
    # auto: try all known for this modality
    best = None
    for name, vals in catalogs.items():
        out = _fit_semilog(distances_norm, vals)
        if not out: 
            continue
        a,b,r2 = out
        fit = MWFit(ladder_name=name, a=float(a), b=float(b), r2=float(r2), units=units)
        if (best is None) or (fit.r2 > best.r2):
            best = fit
    return best

def estimate_value(dist_norm: float, fit: MWFit) -> float:
    # log10(value) = a * d_norm + b
    return float(10 ** (fit.a * float(dist_norm) + fit.b))
