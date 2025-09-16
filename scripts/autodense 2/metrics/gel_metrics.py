# autodense/metrics/gel_metrics.py
from dataclasses import dataclass
from typing import Dict, Any
import statistics

@dataclass
class Acceptance:
    passed: bool
    details: Dict[str, Any]

def summarize(res) -> Dict[str, Any]:
    nlanes = len(res.lanes)
    band_counts = [len(ln.bands) for ln in res.lanes]
    widths = [max(1, ln.x1 - ln.x0) for ln in res.lanes]
    width_cv = (statistics.pstdev(widths) / (statistics.mean(widths)+1e-9)) if widths else 0.0
    markers = sum(1 for ln in res.lanes if ln.type == "marker")
    empty = sum(1 for ln in res.lanes if len(ln.bands)==0)
    med_bands = statistics.median(band_counts) if band_counts else 0
    return {
        "lanes": nlanes,
        "markers": markers,
        "empty_lanes": empty,
        "empty_frac": (empty / max(1,nlanes)),
        "lane_width_cv": width_cv,
        "median_bands_per_lane": med_bands,
        "total_bands": sum(band_counts),
    }

def acceptance_default(modality: str, res) -> 'Acceptance':
    s = summarize(res)
    if modality.lower() == "sds":
        rules = {
            "markers_ok": (s["markers"] in (1,2)),
            "width_cv_ok": (s["lane_width_cv"] <= 0.35),
            "empty_frac_ok": (s["empty_frac"] <= 0.40),
            "min_lanes_ok": (s["lanes"] >= 6),
        }
    else:
        rules = {
            "markers_ok": (s["markers"] in (1,2)),
            "width_cv_ok": (s["lane_width_cv"] <= 0.40),
            "empty_frac_ok": (s["empty_frac"] <= 0.50),
            "min_lanes_ok": (s["lanes"] >= 10),
        }
    passed = all(rules.values())
    rules.update(s)
    return Acceptance(passed=passed, details=rules)
