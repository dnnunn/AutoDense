"""Data manipulation utilities for AutoDense.

This module contains standalone data processing functions with no Streamlit dependencies.
These functions are extracted from the main application for better organization and testability.
"""

import io
import json
import re
import numpy as np
from typing import Dict, Any, List


def obj_to_dict(x) -> Dict[str, Any]:
    """Convert any object to a JSON-serializable dictionary."""
    if x is None:
        return {}
    if isinstance(x, dict):
        return x
    out = {}
    for k in dir(x):
        if k.startswith("_"):
            continue
        try:
            v = getattr(x, k)
        except Exception:
            continue
        if callable(v):
            continue
        try:
            json.dumps(v, default=str)
            out[k] = v
        except Exception:
            out[k] = str(v)
    return out


def extract_json(text: str) -> Dict[str, Any]:
    """Extract JSON object from text using regex."""
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        raise ValueError("No JSON object found in model output")
    return json.loads(m.group(0))


def calculate_lane_metrics(lane_boundaries_data: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Efficient vectorized lane metrics calculation - replaces O(n) loops."""
    if len(lane_boundaries_data) == 0:
        return {
            'spacings': [], 'widths': [], 'min_spacing': 0, 'max_spacing': 0,
            'mean_spacing': 0, 'spacing_cv': 0
        }

    # Convert to numpy arrays for vectorized operations
    centers = np.array([b['center_px'] for b in lane_boundaries_data])
    widths = np.array([b['width_px'] for b in lane_boundaries_data])

    # Calculate spacings only if we have more than one lane
    if len(lane_boundaries_data) < 2:
        return {
            'spacings': [], 'widths': widths.tolist(), 'min_spacing': 0, 'max_spacing': 0,
            'mean_spacing': 0, 'spacing_cv': 0
        }

    spacings = np.abs(np.diff(centers))  # Vectorized spacing calculation

    return {
        'spacings': spacings.tolist(),
        'widths': widths.tolist(),
        'min_spacing': float(spacings.min()) if len(spacings) > 0 else 0,
        'max_spacing': float(spacings.max()) if len(spacings) > 0 else 0,
        'mean_spacing': float(spacings.mean()) if len(spacings) > 0 else 0,
        'spacing_cv': float(spacings.std() / spacings.mean()) if len(spacings) > 0 and spacings.mean() > 0 else 0
    }


def convert_to_csv(rows: List[Dict[str, Any]], columns: List[str]) -> str:
    """Convert data rows and columns to CSV format."""
    s = io.StringIO()
    s.write(",".join(columns) + "\n")
    for r in rows:
        values = []
        for c in columns:
            value = r.get(c, "")
            # Convert None to empty string, everything else to string
            values.append("" if value is None else str(value))
        s.write(",".join(values) + "\n")
    return s.getvalue()