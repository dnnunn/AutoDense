"""AutoDense preprocessing utilities extracted from main application.

This module contains preprocessing logic with no Streamlit dependencies.
These functions are extracted from the main application for better organization and testability.
"""

import json
import base64
import concurrent.futures as _futures
from pathlib import Path
from typing import Dict, Any, Tuple, Optional
from PIL import Image, ImageOps

# Import Phase 1 utilities
from .image_processing import img_to_data_url
from .data_helpers import extract_json

# Initialize preprocessing capabilities
HAS_PREPROCESS = False
HAS_OPENAI = False

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
        return outcome.image, {
            "mode": outcome.mode,
            "before": outcome.before,
            "after": outcome.after,
            "params": outcome.params,
            "status": "success",
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
        meta = {"mode": f"ChatGPT-4.1: {getattr(outcome, 'mode', 'unknown')}", "status": "success"}
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
        fallback="You are a scientific image preprocessing expert. Analyze the gel electrophoresis image and suggest optimal preprocessing parameters."
    )

    resp = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": preprocessing_system},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "Please analyze this gel image and recommend preprocessing parameters."},
                    {"type": "image_url", "image_url": {"url": img_to_data_url(pil_img)}}
                ]
            }
        ],
        max_tokens=300,
        temperature=0.1
    )
    text = resp.choices[0].message.content
    obj = extract_json(text)

    # Apply minimal safe ops locally; leave heavy steps to your pipeline
    img2 = pil_img.copy()
    try:
        ops = obj.get("ops") or []
        for step in ops:
            (k, v), = step.items()
            if k == "resize":
                w, h = v
                img2 = img2.resize((w, h), Image.Resampling.LANCZOS)
            elif k == "auto_contrast":
                img2 = ImageOps.autocontrast(img2)
            elif k == "equalize":
                img2 = ImageOps.equalize(img2)
    except Exception:
        pass

    # Mock object to match expected interface
    class PreprocessResult:
        def __init__(self, image, mode, params):
            self.image = image
            self.mode = mode
            self.params = params

    return PreprocessResult(img2, obj.get("mode", "chatgpt"), obj.get("params", {}))