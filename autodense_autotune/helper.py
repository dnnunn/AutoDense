from .schemas import RunReport, PatchProposal, Critique, Spec
from typing import List, Dict
import yaml

def load_spec(spec_path: str) -> Spec:
    with open(spec_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    # Fill required keys with defaults if missing
    data.setdefault("qc", {})
    data.setdefault("acceptance", {"must_improve": [], "must_not_regress": []})
    data.setdefault("budget", {"max_attempts": 3})
    data.setdefault("runner", {})
    data.setdefault("search", {})
    return Spec(**data)

def review_patch(before: RunReport, proposed: PatchProposal, spec: Spec) -> Critique:
    reasons = []
    # Minimal gate: only allow edits on files inside repo (no absolute paths via helper here)
    if proposed.file.startswith(("/", "\\")) and not proposed.file.startswith("./"):
        reasons.append("Absolute file paths not allowed in patch proposals.")
    # Specific SDS-PAGE guards as examples
    if before.metrics.get("ladder_r2", 1.0) < spec.qc.get("ladder_r2_min", 0.0):
        reasons.append(f"Ladder fit below threshold ({before.metrics.get('ladder_r2'):.3f} < {spec.qc.get('ladder_r2_min')})")
    verdict = "reject" if reasons else "accept"
    return Critique(verdict=verdict, reasons=reasons, notes="Helper review applied.")

def gate_improvement(before: RunReport, after: RunReport, spec: Spec) -> (bool, Dict[str, float]):
    deltas = {}
    ok = True
    # Improvements
    for key in spec.acceptance.get("must_improve", []):
        b = before.metrics.get(key)
        a = after.metrics.get(key)
        if b is None or a is None:
            ok = False
            deltas[key] = float("nan")
        else:
            deltas[key] = a - b
            if a <= b:
                ok = False
    # Do not regress
    for key in spec.acceptance.get("must_not_regress", []):
        b = before.metrics.get(key)
        a = after.metrics.get(key)
        if b is None or a is None:
            continue
        if a > b:
            ok = False
            deltas[key] = a - b
    # Hard QC minimums
    if "ladder_r2_min" in spec.qc:
        if after.metrics.get("ladder_r2", 0.0) < spec.qc["ladder_r2_min"]:
            ok = False
    return ok, deltas
