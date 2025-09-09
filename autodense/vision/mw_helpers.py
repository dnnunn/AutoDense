# autodense/vision/mw_helpers.py
from typing import List, Optional, Dict
from .mw_calibration import try_ladder, estimate_value

def ladder_lane_indices(ladder_lanes_param: Optional[str]) -> List[int]:
    if not ladder_lanes_param: return []
    try:
        return [int(x.strip()) for x in ladder_lanes_param.split(",") if x.strip()]
    except Exception:
        return []

def band_center_norm(ln, H: int) -> List[float]:
    y0, y1 = ln.y0, ln.y1
    denom = max(1.0, float(y1 - y0))
    return [ ( (b.y0 + b.y1)/2.0 - y0 ) / denom for b in ln.bands ]

def compute_mw_or_bp(res, modality: str, ladder_indices: List[int], ladder_type: Optional[str]="auto") -> Dict:
    H = res.image_size[1]
    dists = []
    for ln in res.lanes:
        if ln.index in ladder_indices and ln.bands:
            dists.extend(band_center_norm(ln, H))
    if len(dists) < 5:
        return {"status":"insufficient_ladder_bands","fit":None}
    fit = try_ladder(dists, modality=modality, ladder_type=ladder_type or "auto")
    if not fit:
        return {"status":"no_fit","fit":None}
    # Assign units to bands
    if modality.lower()=="sds":
        for ln in res.lanes:
            for b in ln.bands:
                cy = (b.y0 + b.y1)/2.0
                dnorm = (cy - ln.y0) / max(1.0, (ln.y1 - ln.y0))
                b.mw_kda = estimate_value(dnorm, fit)
    else:
        for ln in res.lanes:
            for b in ln.bands:
                cy = (b.y0 + b.y1)/2.0
                dnorm = (cy - ln.y0) / max(1.0, (ln.y1 - ln.y0))
                setattr(b, "bp", estimate_value(dnorm, fit))
    return {"status":"ok","fit":{"ladder":fit.ladder_name,"a":fit.a,"b":fit.b,"r2":fit.r2,"units":fit.units}}
