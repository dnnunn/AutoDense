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
    """Extract JSON object from text, handling markdown code blocks and various formatting."""
    if not text or not text.strip():
        return {"error": "Empty response", "mode": "fallback", "params": {}}

    # Strategy 1: Try to extract from markdown code blocks (```json ... ```)
    code_block_patterns = [
        r"```(?:json)?\s*(\{[\s\S]*?\})\s*```",  # Standard markdown
        r"```(?:json)?\s*(\{[\s\S]*?\})",        # Missing closing ```
        r"(\{[\s\S]*?\})\s*```"                  # Missing opening ```
    ]

    for pattern in code_block_patterns:
        code_block_match = re.search(pattern, text)
        if code_block_match:
            json_text = code_block_match.group(1).strip()
            try:
                return json.loads(json_text)
            except json.JSONDecodeError:
                continue

    # Strategy 2: Find the largest, most complete JSON object
    # Look for balanced braces starting with {
    json_candidates = []
    start_pos = 0

    while True:
        start = text.find("{", start_pos)
        if start == -1:
            break

        # Find matching closing brace
        brace_count = 0
        end = start
        for i in range(start, len(text)):
            if text[i] == '{':
                brace_count += 1
            elif text[i] == '}':
                brace_count -= 1
                if brace_count == 0:
                    end = i
                    break

        if brace_count == 0:  # Found complete JSON candidate
            candidate = text[start:end+1]
            try:
                parsed = json.loads(candidate)
                json_candidates.append((len(candidate), parsed))
            except json.JSONDecodeError:
                pass

        start_pos = start + 1

    # Return the largest valid JSON object found
    if json_candidates:
        json_candidates.sort(key=lambda x: x[0], reverse=True)
        return json_candidates[0][1]

    # Strategy 3: Try to extract any { ... } pattern (single line or multiline)
    json_patterns = [
        r"\{[^{}]*\}",                          # Simple single-level object
        r"\{[\s\S]*?\}",                        # Any content between braces
    ]

    for pattern in json_patterns:
        matches = re.findall(pattern, text)
        for match in matches:
            try:
                return json.loads(match)
            except json.JSONDecodeError:
                continue

    # Strategy 4: Fallback - return a safe default structure for AI preprocessing
    print(f"WARNING: Could not extract JSON from AI response: {text[:200]}...")
    return {
        "error": "No valid JSON found in response",
        "mode": "fallback",
        "ops": [],
        "params": {
            "polarity": "bands_dark",
            "prominence_frac": 0.06,
            "min_peak_distance_px": 12,
            "baseline": {"method": "percentile", "window_px": 24, "quantile": 0.12},
            "mw_lane": 0,
            "lane_count_expected": 8
        },
        "ai_confidence": 0.0,
        "ai_reasoning": "Fallback due to parsing error"
    }


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