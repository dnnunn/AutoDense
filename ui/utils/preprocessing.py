"""AutoDense preprocessing utilities extracted from main application.

This module contains preprocessing logic with no Streamlit dependencies.
These functions are extracted from the main application for better organization and testability.
"""

import sys
from pathlib import Path
# Add parent directory to Python path to enable autodense imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

import json
import base64
import concurrent.futures as _futures
import re
from typing import Dict, Any, Tuple, Optional, Union
from PIL import Image, ImageOps

# Import Phase 1 utilities
from .image_processing import img_to_data_url
from .data_helpers import extract_json

# Initialize preprocessing capabilities
HAS_PREPROCESS = False
HAS_OPENAI = False

_API_KEY_PATTERN = re.compile(r"sk-[A-Za-z0-9_\-\*]{8,}")


def _mask_openai_keys(text: str) -> str:
    """Mask API key-like substrings to avoid leaking secrets."""
    def _replacer(match: re.Match) -> str:
        token = match.group(0)
        if len(token) <= 8:
            return "sk-***"
        return f"{token[:8]}…{token[-4:]}"

    return _API_KEY_PATTERN.sub(_replacer, text)


def sanitize_openai_error(error: Union[str, Exception]) -> str:
    """Sanitize raw OpenAI error text by masking sensitive substrings."""
    return _mask_openai_keys(str(error))


def summarize_openai_error(error: Union[str, Exception]) -> str:
    """Provide a user-friendly OpenAI error description without leaking secrets."""
    raw = str(error)
    sanitized = sanitize_openai_error(raw)
    lowered = raw.lower()

    if "incorrect api key" in lowered or "invalid_api_key" in lowered:
        return "Invalid OpenAI API key. Set a valid `OPENAI_API_KEY`."
    if "rate limit" in lowered:
        return "OpenAI API rate limit exceeded. Try again soon."
    if "timeout" in lowered:
        return "OpenAI API request timed out. Please retry."
    if "organization" in lowered and "does not exist" in lowered:
        return "OpenAI organization not found. Check `OPENAI_ORG`."

    return sanitized

def _initialize_capabilities():
    """Initialize preprocessing capabilities on module load."""
    global HAS_PREPROCESS, HAS_OPENAI

    try:
        from autodense.preprocess.pipeline import PreprocParams, run as preproc_run
        from autodense.preprocess.policy import guarded_preprocess
        HAS_PREPROCESS = True
    except ImportError:
        HAS_PREPROCESS = False

    try:
        from openai import OpenAI
        HAS_OPENAI = True
    except ImportError:
        HAS_OPENAI = False

    return HAS_PREPROCESS, HAS_OPENAI

# Initialize capabilities on module load
HAS_PREPROCESS, HAS_OPENAI = _initialize_capabilities()


def _load_prompt(name: str, fallback: str) -> str:
    """Load prompt file from multiple candidate locations."""
    candidates = [
        Path(__file__).with_name(name),
        Path(__file__).parent / "prompts" / name,
        Path.cwd() / name,
        Path.cwd() / "prompts" / name,
    ]
    for p in candidates:
        try:
            if p.exists():
                return p.read_text(encoding="utf-8")
        except Exception:
            pass
    return fallback


def filter_supported_preproc_params(ui_params):
    """Filter UI parameters to only include those supported by PreprocParams backend."""
    try:
        from autodense.preprocess.pipeline import PreprocParams
        supported = set(PreprocParams.__annotations__.keys())
        filtered = {k: v for k, v in ui_params.items() if k in supported}
        unsupported = {k: v for k, v in ui_params.items() if k not in supported}

        if unsupported:
            print(f"INFO: Filtered out unsupported preprocessing parameters: {list(unsupported.keys())}")

        return filtered
    except ImportError:
        # If import fails, return params as-is and let downstream handle it
        print("WARNING: Could not import PreprocParams for parameter filtering")
        return ui_params


def _guarded_preprocess_with_timeout(pil_img: Image.Image, timeout_sec: int = 60):
    """Run guarded_preprocess with a timeout; returns (image, meta) or raises on failure/timeout."""
    if not HAS_PREPROCESS:
        raise RuntimeError("Preprocessing module not available")

    def _task():
        from autodense.preprocess.policy import guarded_preprocess
        outcome = guarded_preprocess(pil_img)
        raw_mode = getattr(outcome, "mode", "auto") or "auto"
        mode_lower = raw_mode.lower() if isinstance(raw_mode, str) else "auto"

        if mode_lower in {"auto", "heuristic"}:
            friendly_mode = "Auto (heuristic)"
        elif mode_lower == "none":
            friendly_mode = "Auto (heuristic) — no changes needed"
        else:
            friendly_mode = f"Auto (heuristic) — {raw_mode}" if raw_mode else "Auto (heuristic)"

        meta_note = None
        if mode_lower == "none":
            meta_note = "Heuristic preprocessing determined no adjustments were necessary."

        return outcome.image, {
            "mode": friendly_mode,
            "raw_mode": raw_mode,
            "before": outcome.before,
            "after": outcome.after,
            "params": outcome.params,
            "status": "success",
            **({"note": meta_note} if meta_note else {}),
        }

    with _futures.ThreadPoolExecutor(max_workers=1) as ex:
        fut = ex.submit(_task)
        try:
            return fut.result(timeout=timeout_sec)
        except _futures.TimeoutError:
            raise TimeoutError(f"Guarded preprocessing timed out after {timeout_sec}s")


def _openai_preprocess_with_timeout(pil_img: Image.Image, timeout_sec: int = 75):
    """Run openai_guided_preprocess with a timeout; returns (image, metadata_dict) or raises on failure/timeout."""
    if not HAS_OPENAI:
        raise RuntimeError("OpenAI is not configured")

    def _task():
        # Image is already standardized at upload time, no need to resize again
        outcome = openai_guided_preprocess(pil_img)
        mode_label = getattr(outcome, "mode", "ChatGPT-4.1")
        meta: Dict[str, Any] = {"mode": mode_label, "status": "success"}

        extra_meta = getattr(outcome, "meta", {})
        if isinstance(extra_meta, dict):
            confidence = extra_meta.get("confidence")
            if confidence is not None:
                meta["confidence"] = confidence
            elif extra_meta.get("ai_confidence") is not None:
                meta["confidence"] = extra_meta["ai_confidence"]

            reasoning = extra_meta.get("reasoning")
            if reasoning:
                meta["reasoning"] = reasoning
            elif extra_meta.get("ai_reasoning"):
                meta["reasoning"] = extra_meta["ai_reasoning"]

            if extra_meta.get("error") and "error" not in meta:
                meta["error"] = extra_meta["error"]

            if extra_meta.get("status"):
                meta["status"] = extra_meta["status"]

            for key, value in extra_meta.items():
                if key in {"confidence", "ai_confidence", "reasoning", "ai_reasoning", "error", "status"}:
                    continue
                if value is not None and key not in meta:
                    meta[key] = value

        error_info = getattr(outcome, "error_info", None)
        if error_info and "error" not in meta:
            meta["error"] = error_info
        if error_info and meta.get("status") == "success":
            meta["status"] = "fallback"

        params = getattr(outcome, "params", None)
        if isinstance(params, dict):
            meta.update(params)
        return outcome.image, meta

    with _futures.ThreadPoolExecutor(max_workers=1) as ex:
        fut = ex.submit(_task)
        try:
            return fut.result(timeout=timeout_sec)
        except _futures.TimeoutError:
            raise TimeoutError(f"OpenAI preprocessing timed out after {timeout_sec}s")


def openai_guided_preprocess(pil_img):
    """
    Calls ChatGPT-4.1 with the preprocessing.system.md prompt.
    Returns an object with .image (PIL), .params (dict), .mode (str).
    """
    if not HAS_OPENAI:
        raise RuntimeError("OpenAI is not configured")

    from openai import OpenAI

    client = OpenAI()

    preprocessing_system = _load_prompt(
        "preprocessing.system.md",
        fallback="You are a scientific image preprocessing expert. Analyze the gel electrophoresis image and suggest optimal preprocessing parameters in JSON format."
    )

    try:
        resp = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": preprocessing_system},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "Please analyze this gel image and recommend preprocessing parameters. Return ONLY valid JSON with no additional text."},
                        {"type": "image_url", "image_url": {"url": img_to_data_url(pil_img)}}
                    ]
                }
            ],
            max_tokens=500,  # Increased for more complete responses
            temperature=0.1
        )
        text = resp.choices[0].message.content

        # Enhanced JSON extraction with better error handling
        obj = extract_json(text)

        # Check if we got a fallback response due to parsing error
        if obj.get("error"):
            print(f"AI JSON parsing warning: {obj['error']}")
            # Continue with fallback parameters

    except Exception as e:
        friendly_error = summarize_openai_error(e)
        print(f"OpenAI API error: {friendly_error}")
        # Provide fallback response for any API errors
        fallback_reason = f"Fallback due to OpenAI error: {friendly_error}"
        obj = {
            "error": f"API error: {friendly_error}",
            "mode": "OpenAI error",
            "ops": [],
            "params": {
                "polarity": "bands_dark",
                "prominence_frac": 0.06,
                "min_peak_distance_px": 12,
                "baseline": {"method": "percentile", "window_px": 24, "quantile": 0.12},
                "mw_lane": 0,
                "lane_count_expected": 8
            },
            "status": "fallback",
            "ai_confidence": 0.0,
            "confidence": 0.0,
            "ai_reasoning": fallback_reason,
            "reasoning": fallback_reason
        }

    # Normalize optional fields for downstream consumers
    ai_reasoning = obj.get("ai_reasoning") or obj.get("reasoning")
    if ai_reasoning and "reasoning" not in obj:
        obj["reasoning"] = ai_reasoning
    if ai_reasoning and "ai_reasoning" not in obj:
        obj["ai_reasoning"] = ai_reasoning

    confidence = obj.get("confidence") or obj.get("ai_confidence")
    if confidence is not None and "confidence" not in obj:
        obj["confidence"] = confidence
    if confidence is not None and "ai_confidence" not in obj:
        obj["ai_confidence"] = confidence

    # Apply minimal safe ops locally; leave heavy steps to your pipeline
    img2 = pil_img.copy()
    try:
        ops = obj.get("ops") or []
        for step in ops:
            if not isinstance(step, dict):
                continue
            # Handle both single key-value and multiple key operations
            for k, v in step.items():
                if k == "resize":
                    if isinstance(v, list) and len(v) == 2:
                        w, h = v
                        img2 = img2.resize((w, h), Image.Resampling.LANCZOS)
                elif k == "auto_contrast" and v:
                    img2 = ImageOps.autocontrast(img2)
                elif k == "equalize" and v:
                    img2 = ImageOps.equalize(img2)
                elif k == "invert" and v:
                    img2 = ImageOps.invert(img2)
                # Add more operations as needed
    except Exception as e:
        print(f"Warning: Error applying preprocessing ops: {e}")
        # Continue with original image if preprocessing fails

    # Mock object to match expected interface
    class PreprocessResult:
        def __init__(self, image, mode, params, error_info=None, meta=None):
            self.image = image
            self.mode = mode
            self.params = params
            self.error_info = error_info
            self.meta = meta or {}

    # Determine mode and metadata based on response
    ai_reasoning = obj.get("ai_reasoning") or obj.get("reasoning") or "analysis complete"
    base_label = obj.get("mode")
    if obj.get("error"):
        mode = "ChatGPT-4.1 (fallback)"
    else:
        mode = "ChatGPT-4.1"
    if base_label:
        mode = f"{mode}: {base_label}"

    extra_meta: Dict[str, Any] = {}
    if ai_reasoning:
        extra_meta["ai_reasoning"] = ai_reasoning
        extra_meta.setdefault("reasoning", ai_reasoning)
    if obj.get("ai_confidence") is not None:
        extra_meta["ai_confidence"] = obj["ai_confidence"]
        extra_meta.setdefault("confidence", obj["ai_confidence"])
    if obj.get("confidence") is not None:
        extra_meta["confidence"] = obj["confidence"]
    if obj.get("status"):
        extra_meta["status"] = obj["status"]
    if obj.get("error"):
        extra_meta["error"] = obj["error"]

    return PreprocessResult(
        img2,
        mode,
        obj.get("params", {}),
        obj.get("error"),
        extra_meta
    )
