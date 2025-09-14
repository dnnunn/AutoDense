"""
Tool adapters that the chat agent can call.
These are thin wrappers that dispatch to real functions your app already has.
To connect them, set callbacks into st.session_state["analysis_toolkit_callbacks"].
"""
from __future__ import annotations

from typing import Any, Dict, Optional
import json
import os
import time

# Expected st.session_state["analysis_toolkit_callbacks"] signature:
# {
#    "summarize_results": Callable[[dict], str],
#    "export_report":     Callable[[dict, str], str],
#    "rerun_analysis":    Callable[[dict], dict],
#    "sweep_parameters":  Callable[[dict], dict],
# }
# All are optional. Fallbacks below will be used if missing.

def _cb(name: str):
    try:
        import streamlit as st
        return (st.session_state.get("analysis_toolkit_callbacks") or {}).get(name)
    except Exception:
        return None

def summarize_results(results: Dict[str, Any]) -> str:
    fn = _cb("summarize_results")
    if fn:
        return fn(results)
    # Fallback: naive summary
    keys = list(results.keys())
    return f"Results object with {len(keys)} top-level keys: {keys[:10]}{'...' if len(keys)>10 else ''}"

def export_report(results: Dict[str, Any], path: str = "autodense_report.xlsx") -> str:
    fn = _cb("export_report")
    if fn:
        return fn(results, path)
    # Fallback: write a tiny JSON dump to prove the path exists.
    try:
        import json, pathlib
        p = pathlib.Path(path)
        if not p.parent.exists():
            p.parent.mkdir(parents=True, exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            json.dump({"note": "Demo report (replace with real exporter)", "keys": list(results.keys())}, f, indent=2)
        return f"Report written to {p} (demo content)."
    except Exception as e:
        return f"Failed to export report: {e}"

def rerun_analysis(params: Dict[str, Any]) -> Dict[str, Any]:
    fn = _cb("rerun_analysis")
    if fn:
        return fn(params)
    # Fallback: pretend we ran something
    time.sleep(0.1)
    return {"status": "ok", "note": "Demo rerun completed", "params_used": params}

def sweep_parameters(grid: Dict[str, Any]) -> Dict[str, Any]:
    fn = _cb("sweep_parameters")
    if fn:
        return fn(grid)
    # Fallback: pretend we did a sweep
    return {"status": "ok", "sweep": grid, "best": {k: (v[0] if isinstance(v, (list, tuple)) else v) for k, v in grid.items()}}

# Parse a JSON command of the form:
# {"tool": "export_report", "args": {"path": "out.xlsx"}}
def dispatch_json_command(cmd_text: str, results: Dict[str, Any]) -> str:
    try:
        payload = json.loads(cmd_text)
        tool = payload.get("tool")
        args = payload.get("args", {}) or {}
    except Exception as e:
        return f"Could not parse JSON command: {e}\nRaw: {cmd_text}"

    if tool == "summarize_results":
        
        return summarize_results(results)
    if tool == "export_report":
        
        return export_report(results, **args)
    if tool == "rerun_analysis":
        out = rerun_analysis(args)
        return f"Rerun complete. Summary: {json.dumps(out)[:500]}"
    if tool == "sweep_parameters":
        out = sweep_parameters(args)
        return f"Sweep complete. Summary: {json.dumps(out)[:500]}"
    return f"Unknown tool: {tool}"


# ---------- Quantitative post-analysis tools ----------
from .quant_tools import (
    quantify_against_standard,
    band_ratio as _band_ratio,
    lane_compare as _lane_compare,
    mobility_shift as _mobility_shift,
    copy_number_estimate as _copy_number_estimate,
)

def quantify_against_standard_tool(results: Dict[str, Any], **kwargs) -> str:
    out = quantify_against_standard(results, **kwargs)
    return json.dumps(out, indent=2)

def band_ratio_tool(results: Dict[str, Any], **kwargs) -> str:
    ref = kwargs.get("ref")
    target = kwargs.get("target")
    out = _band_ratio(results, ref=tuple(ref), target=tuple(target))
    return json.dumps(out, indent=2)

def lane_compare_tool(results: Dict[str, Any], **kwargs) -> str:
    out = _lane_compare(results, lanes=kwargs.get("lanes"))
    return json.dumps(out, indent=2)

def mobility_shift_tool(results: Dict[str, Any], **kwargs) -> str:
    out = _mobility_shift(results, **kwargs)
    return json.dumps(out, indent=2)

def copy_number_estimate_tool(results: Dict[str, Any], **kwargs) -> str:
    out = _copy_number_estimate(results, **kwargs)
    return json.dumps(out, indent=2)
