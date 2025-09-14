from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, List, Literal, Callable
import math

Severity = Literal["info", "warn", "error"]
Modality = Literal["sds", "etbr", "colony"]

@dataclass
class Action:
    label: str
    op: Literal["rerun_step", "preview_overlay_diff", "open_docs"]
    step_id: str | None = None
    params_delta: Dict[str, Any] | None = None
    docs_id: str | None = None

@dataclass
class Hint:
    id: str
    severity: Severity
    confidence: float
    applies_to: Modality
    symptoms: List[str]
    why_it_matters: str
    suggested_changes: Dict[str, Any] = field(default_factory=dict)
    actions: List[Action] = field(default_factory=list)
    docs: str | None = None
    dismissible_for_session: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "severity": self.severity,
            "confidence": round(float(self.confidence), 3),
            "applies_to": self.applies_to,
            "symptoms": self.symptoms,
            "why_it_matters": self.why_it_matters,
            "suggested_changes": self.suggested_changes,
            "actions": [a.__dict__ for a in self.actions],
            "docs": self.docs,
            "dismissible_for_session": self.dismissible_for_session,
        }

def _sigmoid(x: float) -> float:
    try:
        return 1.0 / (1.0 + math.exp(-x))
    except OverflowError:
        return 0.0 if x < 0 else 1.0

def _safe_get(d: Dict[str, Any], path: str, default: Any = 0):
    cur = d
    for key in path.split("."):
        if not isinstance(cur, dict) or key not in cur:
            return default
        cur = cur[key]
    return cur

# -------------------------------
# SDS-PAGE rules
# -------------------------------

def rule_sds_saturation(metrics: Dict[str, Any], params: Dict[str, Any]):
    sat = float(_safe_get(metrics, "img.sat_pct", 0.0))
    flat_peaks = float(_safe_get(metrics, "sds.flat_peak_score", 0.0))
    if sat <= 0.015 and flat_peaks <= 0.6:
        return None
    new_prom = max(0.0, float(_safe_get(metrics, "sds.min_prom", 0.02)) * 0.8)
    return Hint(
        id="HINT_SDS_SATURATION",
        severity="warn",
        confidence=_sigmoid(3 * sat + flat_peaks),
        applies_to="sds",
        symptoms=["peak_clipped_high" if sat > 0.015 else "flat_peaks"],
        why_it_matters="Clipped band cores break intensity proportionality and inflate wide bands.",
        suggested_changes={"exposure_comp": -0.3, "min_band_prominence": new_prom},
        actions=[
            Action(
                label="Apply & re-run lane quant",
                op="rerun_step",
                step_id="sds.quantify_lanes",
                params_delta={"exposure_comp": -0.3, "min_band_prominence": new_prom},
            ),
            Action(label="Show before/after overlay", op="preview_overlay_diff", step_id="sds.quantify_lanes"),
        ],
        docs="tip_sds_saturation",
    )

def rule_sds_lanewise_bg(metrics: Dict[str, Any], params: Dict[str, Any]):
    lanewise = bool(_safe_get(metrics, "sds.lanewise_bg", False))
    baseline_cv = float(_safe_get(metrics, "sds.lane_baseline_cv", 0.0))
    lane_w = float(_safe_get(metrics, "sds.lane_width_px", 30.0))
    if lanewise or baseline_cv <= 0.12:
        return None
    rb = int(max(10, round(lane_w * 1.5)))
    return Hint(
        id="HINT_SDS_BG",
        severity="info",
        confidence=0.72,
        applies_to="sds",
        symptoms=["uneven_lane_background"],
        why_it_matters="Uneven background biases between-lane comparisons.",
        suggested_changes={"lanewise_background": True, "rolling_ball_radius": rb},
        actions=[
            Action(
                label="Enable lane-wise BG & rerun",
                op="rerun_step",
                step_id="sds.quantify_lanes",
                params_delta={"lanewise_background": True, "rolling_ball_radius": rb},
            )
        ],
        docs="tip_sds_rolling_ball",
    )

def rule_sds_ladder_fit(metrics: Dict[str, Any], params: Dict[str, Any]):
    r2 = float(_safe_get(metrics, "sds.ladder_r2", 1.0))
    monotonic = bool(_safe_get(metrics, "sds.residuals_monotonic", False))
    if r2 >= 0.985 and not monotonic:
        return None
    return Hint(
        id="HINT_SDS_LADDER",
        severity="error" if r2 < 0.97 else "warn",
        confidence=0.8 if r2 < 0.985 else 0.6,
        applies_to="sds",
        symptoms=["ladder_fit_poor"] + (["residuals_monotonic"] if monotonic else []),
        why_it_matters="Unreliable size mapping misplaces bands; size/quantity calls become suspect.",
        suggested_changes={"refit_ladder": True, "allow_manual_anchors": True},
        actions=[
            Action(
                label="Re-detect ladder",
                op="rerun_step",
                step_id="sds.fit_ladder",
                params_delta={"detect_color_hint": _safe_get(metrics, "sds.ladder_color_hint", None)},
            ),
            Action(label="Show residuals", op="preview_overlay_diff", step_id="sds.fit_ladder"),
        ],
        docs="tip_sds_ladder_refit",
    )

# -------------------------------
# EtBr rules
# -------------------------------

def rule_etbr_smear(metrics: Dict[str, Any], params: Dict[str, Any]):
    smear = float(_safe_get(metrics, "etbr.smear_score", 0.0))
    peaks = int(_safe_get(metrics, "etbr.discrete_peaks", 0))
    if smear < 0.6 or peaks >= 2:
        return None
    return Hint(
        id="HINT_ETBR_SMEAR",
        severity="warn",
        confidence=_sigmoid(3 * (smear - 0.6)),
        applies_to="etbr",
        symptoms=["smear_high", "few_discrete_peaks"],
        why_it_matters="Fragment sizing on smears is unreliable; only total mass is defensible.",
        suggested_changes={"mode": "mass_only", "hide_size_column": True},
        actions=[
            Action(
                label="Switch to mass-only & rerun",
                op="rerun_step",
                step_id="etbr.quantify_lane_profiles",
                params_delta={"mode": "mass_only"},
            )
        ],
        docs="tip_etbr_smear",
    )

# -------------------------------
# Colony rules
# -------------------------------

def rule_colony_illum(metrics: Dict[str, Any], params: Dict[str, Any]):
    grad = float(_safe_get(metrics, "img.illum_gradient", 0.0))
    adaptive = bool(_safe_get(metrics, "colony.adaptive_threshold", False))
    if grad <= 0.15 or adaptive:
        return None
    return Hint(
        id="HINT_COLONY_ILLUM",
        severity="info",
        confidence=0.7,
        applies_to="colony",
        symptoms=["uneven_illumination"],
        why_it_matters="Global thresholds bias edge colonies; adaptive improves recall.",
        suggested_changes={"flatfield": True, "threshold": "adaptive"},
        actions=[
            Action(
                label="Flat-field + adaptive threshold",
                op="rerun_step",
                step_id="colony.segment",
                params_delta={"flatfield": True, "threshold": "adaptive"},
            ),
            Action(label="Preview mask diff", op="preview_overlay_diff", step_id="colony.segment"),
        ],
        docs="tip_colony_threshold",
    )

def rule_colony_touching(metrics: Dict[str, Any], params: Dict[str, Any]):
    touching = float(_safe_get(metrics, "colony.touching_rate", 0.0))
    watershed = bool(_safe_get(metrics, "colony.watershed", False))
    if touching < 0.2 or watershed:
        return None
    min_dist = int(max(3, round(float(_safe_get(metrics, "colony.avg_radius_px", 8.0)) * 0.8)))
    return Hint(
        id="HINT_COLONY_WATERSHED",
        severity="warn",
        confidence=_sigmoid(3 * (touching - 0.2)),
        applies_to="colony",
        symptoms=["touching_colonies"],
        why_it_matters="Merged colonies undercount and distort color normalization.",
        suggested_changes={"watershed": True, "min_distance_px": min_dist},
        actions=[
            Action(
                label="Enable watershed & rerun",
                op="rerun_step",
                step_id="colony.segment",
                params_delta={"watershed": True, "min_distance_px": min_dist},
            )
        ],
        docs="tip_colony_watershed",
    )

# Registry & evaluation
Rule = Callable[[Dict[str, Any], Dict[str, Any]], Hint | None]
REGISTRY: List[Rule] = [
    rule_sds_saturation,
    rule_sds_lanewise_bg,
    rule_sds_ladder_fit,
    rule_etbr_smear,
    rule_colony_illum,
    rule_colony_touching,
]

def evaluate_hints(metrics: Dict[str, Any], params: Dict[str, Any], *, max_hints: int = 2) -> List[Dict[str, Any]]:
    hints: List[Hint] = []
    for rule in REGISTRY:
        try:
            h = rule(metrics, params)
        except Exception:
            h = None
        if h is not None and (h.confidence >= 0.55 or h.severity == "error"):
            hints.append(h)
    order = {"error": 2, "warn": 1, "info": 0}
    hints.sort(key=lambda x: (order[x.severity], x.confidence), reverse=True)
    return [h.to_dict() for h in hints[:max_hints]]

if __name__ == "__main__":
    dummy_metrics = {
        "img": {"sat_pct": 0.02, "illum_gradient": 0.3},
        "sds": {"flat_peak_score": 0.7, "min_prom": 0.02, "lanewise_bg": False, "lane_baseline_cv": 0.2, "lane_width_px": 28, "ladder_r2": 0.972, "residuals_monotonic": True},
        "etbr": {"smear_score": 0.7, "discrete_peaks": 1},
        "colony": {"touching_rate": 0.35, "watershed": False, "avg_radius_px": 9},
    }
    print(evaluate_hints(dummy_metrics, params={}))