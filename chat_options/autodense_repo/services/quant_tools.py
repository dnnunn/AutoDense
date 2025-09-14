
from __future__ import annotations
from dataclasses import dataclass
from typing import List, Dict, Any, Optional, Tuple
import math

def _pick_intensity_key(band: Dict[str, Any]) -> Optional[str]:
    # Heuristic: pick the first plausible intensity-like key
    for k in ["intensity", "integrated_intensity", "sum_intensity", "volume", "area_intensity", "raw_volume"]:
        if isinstance(band.get(k), (int, float)):
            return k
    # Fallback: sometimes "area" is the only numeric; not ideal for quant but better than nothing
    if isinstance(band.get("area"), (int, float)):
        return "area"
    # Try any numeric value
    for k, v in band.items():
        if isinstance(v, (int, float)) and not ("index" in k or "x" in k and "y" not in k):
            return k
    return None

def _extract_bands(results: Dict[str, Any]) -> List[Dict[str, Any]]:
    bands = results.get("bands") or []
    # normalize indexes
    out = []
    for i, b in enumerate(bands):
        d = dict(b)
        d.setdefault("band_index", d.get("band_index", i))
        out.append(d)
    return out

def _by_lane(bands: List[Dict[str, Any]]) -> Dict[int, List[Dict[str, Any]]]:
    lanes: Dict[int, List[Dict[str, Any]]] = {}
    for b in bands:
        li = int(b.get("lane_index", -1))
        lanes.setdefault(li, []).append(b)
    # sort by vertical position if present
    for li in lanes:
        lanes[li].sort(key=lambda x: float(x.get("centroid_y", x.get("y", x.get("top", 0)))))
    return lanes

# ---------- Standard curve & quantification ----------

def _fit_linear(xy: List[Tuple[float, float]]) -> Tuple[float, float]:
    # y = a*x + b
    n = len(xy)
    sx = sum(x for x, y in xy)
    sy = sum(y for x, y in xy)
    sxx = sum(x*x for x, y in xy)
    sxy = sum(x*y for x, y in xy)
    denom = n * sxx - sx * sx
    if abs(denom) < 1e-12:
        raise ValueError("Degenerate points for linear fit")
    a = (n * sxy - sx * sy) / denom
    b = (sy * sxx - sx * sxy) / denom
    return a, b

def _fit_loglog(xy: List[Tuple[float, float]]) -> Tuple[float, float]:
    # log(y) = A*log(x) + B -> y = exp(B) * x^A
    logxy = [(math.log(max(x, 1e-12)), math.log(max(y, 1e-12))) for x, y in xy]
    A, B = _fit_linear(logxy)
    return A, B  # interpret accordingly

def quantify_against_standard(
    results: Dict[str, Any],
    standard_lane: Optional[int] = None,
    standard_points: Optional[List[Tuple[float, float]]] = None,  # [(known_amount, measured_intensity)]
    fit: str = "linear",  # or "loglog"
    target_lane: Optional[int] = None,
    target_band_index: Optional[int] = None,
) -> Dict[str, Any]:
    """
    Returns {"model": {...}, "targets": [...]} with per-band amounts.
    If standard_points not provided, extracts from standard_lane by ascending band order
    and requires 'known_amounts' in results["standards"] or via args.
    """
    bands = _extract_bands(results)
    bylane = _by_lane(bands)

    # Build standard points
    xy: List[Tuple[float, float]] = []
    model = {"fit": fit}
    if standard_points:
        xy = list(standard_points)
    else:
        if standard_lane is None:
            raise ValueError("Either standard_points or standard_lane is required")
        std_bands = bylane.get(standard_lane, [])
        knowns = None
        # results["standards"] may hold mapping: lane_index -> list of known amounts (same order as bands)
        if isinstance(results.get("standards"), dict):
            lane_map = results["standards"].get(str(standard_lane)) or results["standards"].get(standard_lane)
            if isinstance(lane_map, list):
                knowns = lane_map
        if not std_bands or not knowns or len(knowns) != len(std_bands):
            raise ValueError("Standard lane extraction failed or known amounts missing")
        for k, b in zip(knowns, std_bands):
            ikey = _pick_intensity_key(b)
            if not ikey:
                raise ValueError("No intensity-like field found in standard bands")
            xy.append((float(k), float(b.get(ikey, 0.0))))

    if fit == "linear":
        a, b = _fit_linear([(x, y) for x, y in xy])
        model.update({"type": "linear", "a": a, "b": b, "form": "intensity = a*amount + b"})
        def inv(y):  # amount = (y - b)/a
            if abs(a) < 1e-12:
                raise ValueError("Invalid linear model (a≈0)")
            return (y - b) / a
    elif fit == "loglog":
        A, B = _fit_loglog([(x, y) for x, y in xy])
        model.update({"type": "loglog", "A": A, "B": B, "form": "log(intensity)=A*log(amount)+B"})
        def inv(y):
            # amount = exp((log(y)-B)/A)
            if abs(A) < 1e-12:
                raise ValueError("Invalid loglog model (A≈0)")
            return math.exp((math.log(max(y, 1e-12)) - B) / A)
    else:
        raise ValueError("Unsupported fit type")

    # Quantify target(s)
    targets: List[Dict[str, Any]] = []
    target_lanes = [target_lane] if target_lane is not None else sorted(bylane.keys())
    for li in target_lanes:
        for b in bylane.get(li, []):
            if target_band_index is not None and int(b.get("band_index", -1)) != int(target_band_index):
                continue
            ikey = _pick_intensity_key(b)
            if not ikey:
                continue
            y = float(b.get(ikey, 0.0))
            amt = inv(y)
            out = {"lane_index": li, "band_index": int(b.get("band_index", -1)), "intensity_key": ikey, "intensity": y, "amount_est": amt}
            # propagate size if present
            if "mw_kda" in b: out["mw_kda"] = b["mw_kda"]
            if "bp" in b: out["bp"] = b["bp"]
            targets.append(out)

    return {"model": model, "standards_xy": xy, "targets": targets}

# ---------- Ratios, lane comparisons, mobility ----------

def band_ratio(results: Dict[str, Any], ref: Tuple[int,int], target: Tuple[int,int]) -> Dict[str, Any]:
    """
    ref=(lane_index, band_index), target=(lane_index, band_index)
    Returns ratio target/ref based on chosen intensity key.
    """
    bands = _extract_bands(results)
    bykey = {(int(b.get("lane_index", -1)), int(b.get("band_index", -1))): b for b in bands}
    b_ref = bykey.get((int(ref[0]), int(ref[1])))
    b_tar = bykey.get((int(target[0]), int(target[1])))
    if not b_ref or not b_tar:
        raise ValueError("Reference or target band not found")
    k_ref = _pick_intensity_key(b_ref)
    k_tar = _pick_intensity_key(b_tar)
    if not k_ref or not k_tar:
        raise ValueError("No intensity field found on one of the bands")
    r = float(b_tar.get(k_tar, 0.0)) / max(float(b_ref.get(k_ref, 0.0)), 1e-12)
    return {"ratio": r, "target_intensity": b_tar.get(k_tar), "ref_intensity": b_ref.get(k_ref), "intensity_keys": (k_tar, k_ref)}

def lane_compare(results: Dict[str, Any], lanes: Optional[List[int]] = None) -> Dict[str, Any]:
    """
    Summarize per-lane total intensity and band counts.
    """
    bands = _extract_bands(results)
    bylane = _by_lane(bands)
    if lanes is None:
        lanes = sorted(bylane.keys())
    out = []
    for li in lanes:
        s = 0.0
        cnt = 0
        for b in bylane.get(li, []):
            k = _pick_intensity_key(b)
            if not k: 
                continue
            s += float(b.get(k, 0.0))
            cnt += 1
        out.append({"lane_index": li, "band_count": cnt, "total_intensity": s})
    return {"lanes": out}

def mobility_shift(results: Dict[str, Any], lane_a: int, band_a: int, lane_b: int, band_b: int) -> Dict[str, Any]:
    """
    Compute relative mobility shift between two bands using centroid_y and image height if available.
    Mobility = centroid_y / image_height (unitless), lower is slower (approx).
    """
    bands = _extract_bands(results)
    img_h = (results.get("image") or {}).get("height")
    lookup = {(int(b.get("lane_index", -1)), int(b.get("band_index", -1))): b for b in bands}
    A = lookup.get((int(lane_a), int(band_a)))
    B = lookup.get((int(lane_b), int(band_b)))
    if not A or not B:
        raise ValueError("Bands not found")
    ya = float(A.get("centroid_y", A.get("y", 0.0)))
    yb = float(B.get("centroid_y", B.get("y", 0.0)))
    if img_h:
        ma = ya / float(img_h)
        mb = yb / float(img_h)
    else:
        # Fallback: relative to max y across bands
        maxy = max([float(b.get("centroid_y", b.get("y", 0.0))) for b in bands] + [1.0])
        ma = ya / maxy
        mb = yb / maxy
    return {"mobility_a": ma, "mobility_b": mb, "delta": mb - ma}

def copy_number_estimate(results: Dict[str, Any], lane_ref: int, lane_target: int) -> Dict[str, Any]:
    """
    Rough DNA copy-number estimate from EtBr gel: proportional to intensity / length.
    Sums bands per lane. Assumes equal staining and imaging conditions.
    Returns ratio target/ref.
    """
    bands = _extract_bands(results)
    bylane = _by_lane(bands)
    def lane_score(li: int) -> float:
        s = 0.0
        for b in bylane.get(li, []):
            k = _pick_intensity_key(b)
            if not k: 
                continue
            length = float(b.get("bp", 0.0))
            val = float(b.get(k, 0.0))
            if length > 0:
                s += val / length
            else:
                s += val
        return s
    ref_s = lane_score(int(lane_ref))
    tar_s = lane_score(int(lane_target))
    ratio = tar_s / max(ref_s, 1e-12)
    return {"ratio": ratio, "ref_score": ref_s, "target_score": tar_s}
