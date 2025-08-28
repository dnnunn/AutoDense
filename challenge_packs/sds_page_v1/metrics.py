"""SDS-PAGE metrics utilities.

Dependencies: numpy, scipy
"""
from __future__ import annotations
import numpy as np
from typing import Dict, Sequence, Optional
from scipy.signal import find_peaks

def band_stability_jitter(lane_profiles: Sequence[np.ndarray], rel_jitter: float = 0.05) -> float:
    """Estimate band-call stability under small threshold jitter.

    For each lane profile, threshold at T, then at T*(1±rel_jitter), count peaks
    and measure how often peak count and positions remain consistent.
    Returns fraction in [0,1] (higher = more stable).
    """
    if not lane_profiles:
        return 0.0
    stabilities = []
    for prof in lane_profiles:
        prof = (prof - prof.min()) / (prof.ptp() + 1e-9)
        T = 0.5
        def peaks_at(t):
            thr = prof > t
            # use peaks on smoothed profile
            from scipy.ndimage import gaussian_filter1d
            sm = gaussian_filter1d(prof.astype(float), 1.0)
            pk, _ = find_peaks(sm, prominence=0.1)
            return pk
        p0 = peaks_at(T)
        p1 = peaks_at(T*(1.0+rel_jitter))
        p2 = peaks_at(T*(1.0-rel_jitter))
        # simple stability: Jaccard of peak index sets within ±1 px tolerance
        def jacc(a,b):
            if a.size==0 and b.size==0: return 1.0
            def tol_match(x,y,eps=1):
                used=set()
                m=0
                for i in x:
                    for j in y:
                        if j in used: continue
                        if abs(int(i)-int(j))<=eps:
                            used.add(j); m+=1; break
                return m, len(x)+len(y)-m
            m, u = tol_match(a,b)
            return m/max(u,1)
        s = 0.5*jacc(p0,p1) + 0.5*jacc(p0,p2)
        stabilities.append(s)
    return float(np.mean(stabilities))

def compute_metrics(
    lane_profiles: Sequence[np.ndarray],
    ladder_migration_px: Optional[Sequence[float]] = None,
    ladder_kDa: Optional[Sequence[float]] = None
) -> Dict[str, float]:
    out = {
        "band_stability_jitter": band_stability_jitter(lane_profiles)
    }
    if ladder_migration_px is not None and ladder_kDa is not None:
        # linear fit on log(kDa) vs migration distance
        x = np.asarray(ladder_migration_px, dtype=float)
        y = np.log10(np.asarray(ladder_kDa, dtype=float))
        if x.size >= 3 and x.size == y.size:
            from scipy.stats import linregress
            r2 = linregress(x, y).rvalue**2
            out["ladder_r2"] = float(r2)
    return out
