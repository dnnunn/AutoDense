import sys
import os
from pathlib import Path

# Comprehensive environment initialization
def initialize_autodense_environment():
    """Initialize all required environment variables and paths for AutoDense."""

    # 1. Set up Python path for autodense imports
    project_root = Path(__file__).parent.parent
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

    # 2. Load environment variables from .env file
    env_file = project_root / ".env"
    if env_file.exists():
        with open(env_file, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    if key and value:
                        os.environ[key] = value

    # 3. Load API key from api_properties.config as fallback
    api_config = project_root / "api_properties.config"
    if api_config.exists() and not os.environ.get('OPENAI_API_KEY'):
        with open(api_config, 'r') as f:
            for line in f:
                line = line.strip()
                if line.startswith('OPENAI_API_KEY='):
                    key, value = line.split('=', 1)
                    if value:
                        os.environ['OPENAI_API_KEY'] = value
                        break

    # 4. Set required environment variables if not already set
    defaults = {
        'STREAMLIT_BROWSER_GATHER_USAGE_STATS': 'false',
        'PYTHONPATH': str(project_root),
        'LOG_LEVEL': 'INFO',
        'DEBUG': 'false'
    }

    for key, value in defaults.items():
        if key not in os.environ:
            os.environ[key] = value

    # 5. Verify critical dependencies are available
    try:
        import streamlit
        import numpy
        import PIL
        dependencies_ok = True
    except ImportError as e:
        dependencies_ok = False
        print(f"WARNING: Missing dependency: {e}")

    return {
        'project_root': str(project_root),
        'python_path_set': str(project_root) in sys.path,
        'env_file_loaded': env_file.exists(),
        'api_config_loaded': api_config.exists(),
        'openai_key_available': bool(os.environ.get('OPENAI_API_KEY')),
        'dependencies_ok': dependencies_ok
    }

# Initialize environment before any other imports
_env_status = initialize_autodense_environment()

import io
import json, tempfile
from collections import defaultdict
from typing import Dict, Any, Optional, Tuple, List, Union, Callable
from PIL import Image, ImageDraw, ImageOps

# Import extracted utility functions
from utils.image_processing import (
    standardize_image_size, to_png_bytes, img_to_data_url,
    process_uploaded_image, overlay_fallback
)
from utils.data_helpers import obj_to_dict, extract_json, calculate_lane_metrics, convert_to_csv
from utils.preprocessing import (
    filter_supported_preproc_params,
    _guarded_preprocess_with_timeout, HAS_PREPROCESS,
    sanitize_openai_error, summarize_openai_error
)
from utils.parameter_management import (
    translate_ui_to_backend_params, validate_and_explain_params,
    create_safe_ad_params
)
from components.image_upload import render_image_upload
from components.prerequisites_panel import render_prerequisites_panel
from components.calibration_instructions import render_calibration_instructions
from autodense_types import ImageMetadata, ImageTuple, ParameterDict, AnalysisResult
try:
    _futures
except NameError:
    import concurrent.futures as _futures

# Accessibility: Screen reader announcement function
def announce_to_screen_reader(message: str, priority: str = "polite") -> None:
    """Announce status updates to screen readers via live regions."""
    st.markdown(f"""
    <div aria-live="{priority}" aria-atomic="true" class="sr-only" role="status">
        {message}
    </div>
    """, unsafe_allow_html=True)


# Enhanced loading state management
def show_loading_context(operation_name: str, details: str = "", estimated_time: str = ""):
    """Display enhanced loading state with context and progress information."""
    loading_message = f"🔄 {operation_name}"
    if estimated_time:
        loading_message += f" (up to {estimated_time})"

    if details:
        st.info(f"🔄 {details}")

    announce_to_screen_reader(f"{operation_name} in progress", "polite")
    return st.spinner(loading_message)

def show_operation_success(operation_name: str, details: str = ""):
    """Display success state with consistent formatting."""
    success_message = f"✅ {operation_name} completed successfully"
    if details:
        success_message += f": {details}"

    st.success(success_message)
    announce_to_screen_reader(f"{operation_name} completed", "assertive")

def show_operation_error(operation_name: str, error_message: str, context: str = ""):
    """Display error state with helpful context and next steps."""
    error_display = f"⚠️ {operation_name} failed: {error_message}"
    if context:
        error_display += f"\n\n**Context:** {context}"

    st.error(error_display)
    announce_to_screen_reader(f"{operation_name} failed: {error_message}", "assertive")

# Safe timeout helpers (no Image annotations to avoid NameError at import-time)
# REMOVED: Duplicate function - see the main _openai_preprocess_with_timeout function below








def cached_preprocess_image(image_hash: str, mode: str, manual_params_str: str = "") -> Tuple[Any, Dict[str, Any]]:
    """Improved caching with better key strategy - no longer processes image bytes repeatedly"""
    # Get image from session state (already processed once)
    if 'res_uploaded_image' not in st.session_state or st.session_state.res_image_metadata.get('hash') != image_hash:
        return None, {"status": "error", "error": "Image not found in session"}

    img: Image.Image = st.session_state.res_uploaded_image
    manual_params: Optional[Dict[str, Any]] = json.loads(manual_params_str) if manual_params_str else None

    try:
        normalized_mode = (mode or "").lower()

        if normalized_mode.startswith("auto") and HAS_PREPROCESS:
            with st.spinner(f"🔬 Applying AutoDense heuristic preprocessing..."):
                st.info("🔄 Optimizing image contrast and background...")
                img2, meta = _guarded_preprocess_with_timeout(img, timeout_sec=60)
                meta.setdefault("mode", "Auto (heuristic)")
                announce_to_screen_reader("Auto preprocessing completed successfully", "assertive")
            return img2, meta

        elif normalized_mode.startswith("manual") and manual_params and HAS_PREPROCESS:
            with st.spinner("⚙️ Applying manual preprocessing parameters..."):
                st.info(f"🔄 Processing with {len(manual_params)} custom parameters...")
                # Filter parameters to only include those supported by PreprocParams
                manual_params = filter_supported_preproc_params(manual_params)
                # Parameter aliasing for backward compatibility
                alias = {"bg_radius": "bg_radius_px"}
                manual_params = { (alias.get(k, k)): v for k, v in manual_params.items() }
                pp = PreprocParams(**manual_params)
                prepped, meta, _ = preproc_run(img, pp, save_dir=None, save_prefix="")
                announce_to_screen_reader("Manual preprocessing completed successfully", "assertive")
            return prepped, {"mode": "Manual", "meta": getattr(meta, "__dict__", {}), "status": "success"}

        else:
            # Raw fallback (no preprocessing) - Fast operation, minimal loading state
            with st.spinner("📸 Preparing raw image data..."):
                arr = np.asarray(img.convert("L")).astype(np.float32)
                prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
            mode_label = "Raw (no preprocessing)" if normalized_mode.startswith("off") else f"Fallback ({mode})"
            status = "success" if normalized_mode.startswith("off") else "fallback"
            return prepped, {"mode": mode_label, "status": status}

    except Exception as e:
        # Return raw image on any preprocessing failure with error annotation
        friendly_error = sanitize_openai_error(e)
        st.error(f"⚠️ Preprocessing failed: {friendly_error}")
        announce_to_screen_reader(f"Preprocessing failed: {friendly_error}", "assertive")
        with st.spinner("🔧 Falling back to raw image processing..."):
            arr = np.asarray(img.convert("L")).astype(np.float32)
            prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
        return prepped, {"mode": "Fallback (preprocessing failed)", "error": friendly_error, "status": "error"}
def _apply_footer_hide() -> None:
    """Hide sticky footer while a run is active."""
    try:
        if st.session_state.get("_analysis_running"):
            st.markdown("<style>#ad-sticky-footer{display:none!important}</style>", unsafe_allow_html=True)
    except Exception:
        pass
_apply_footer_hide()
# ui/streamlit_app_ux_optimized.py

import streamlit as st
import json, io
import numpy as np
import pandas as pd
import concurrent.futures as _futures
import time
import contextlib
import gc
import hashlib
import streamlit.components.v1 as components

# Optional HEIC/HEIF support via pillow-heif
_HEIC_OK = False
try:
    from pillow_heif import register_heif_opener  # type: ignore
    register_heif_opener()
    _HEIC_OK = True
except Exception:
    _HEIC_OK = False



# AutoDense imports with graceful fallbacks
try:
    from autodense.preprocess.pipeline import PreprocParams, run as preproc_run
    from autodense.preprocess.policy import guarded_preprocess
    HAS_PREPROCESS = True
except ImportError:
    HAS_PREPROCESS = False
    st.warning("⚠️ AutoDense preprocessing not available")

# AutoDense analysis pipeline (lanes/bands) with robust import
HAS_AD_PIPELINE = False
ADParams = None
ad_pipeline_run = None
draw_ad_overlay = None
try:
    from scripts.autodense.orchestrator.pipeline import Params as ADParams, run as ad_pipeline_run
    from scripts.autodense.vision.analyzer import draw_overlay as draw_ad_overlay
    HAS_AD_PIPELINE = True
except Exception:
    try:
        from autodense.orchestrator.pipeline import Params as ADParams, run as ad_pipeline_run
        from autodense.vision.analyzer import draw_overlay as draw_ad_overlay
        HAS_AD_PIPELINE = True
    except Exception:
        HAS_AD_PIPELINE = False




# Optional advanced features
try:
    from streamlit_drawable_canvas import st_canvas
    HAS_DRAWABLE_CANVAS = True
except ImportError:
    HAS_DRAWABLE_CANVAS = False

try:
    from streamlit_image_coordinates import streamlit_image_coordinates
    HAS_IMAGE_COORDINATES = True
except ImportError:
    HAS_IMAGE_COORDINATES = False

try:
    from autodense.preprocess.simple_lane_mapping import (
        TwoPointCalibration, calculate_lane_positions,
        validate_calibration_points, get_recommended_gel_settings, boundaries_to_csv,
        SimpleLaneBoundary
    )
    HAS_LANE_MAPPING = True
except ImportError:
    HAS_LANE_MAPPING = False

# OPTIMIZATION 1: Single image processing pipeline with better caching
@st.cache_data(persist=True, max_entries=1)

# OPTIMIZATION 1: Improved preprocessing cache with better key strategy
@st.cache_data(
    show_spinner="🔬 Optimizing gel image...", 
    max_entries=10,  # Increased for better hit rate
    ttl=7200,  # 2 hours for longer sessions
    hash_funcs={"dict": lambda x: str(sorted(x.items())) if x else ""}
)




@st.cache_data(show_spinner="🔍 Analyzing gel structure...", max_entries=5, ttl=1800)
def cached_analyze_gel(_image_path: str, _params_dict: Dict[str, Any], _retries: int) -> Dict[str, Any]:
    """Cache gel analysis operations with 30-minute TTL"""
    try:
        # This would integrate with the full AutoDense pipeline
        # For now, return mock data to demonstrate UX patterns
        return {
            "lanes": [],
            "bands": [],
            "status": "success",
            "message": "Analysis pipeline integration pending"
        }
    except Exception as e:
        return {"status": "error", "error": str(e)}



# ---- Band Assist helpers ----

def get_current_lane_calibration():
    """Extract current lane calibration from session state.

    Returns:
        Dict containing lane calibration data including boundaries,
        calibration points, and status.
    """
    return {
        'lane_boundaries': st.session_state.get('res_lane_boundaries'),
        'calibration_lane1': st.session_state.get('calibration_lane1'),
        'calibration_lane2': st.session_state.get('calibration_lane2'),
        'has_manual_calibration': bool(st.session_state.get('res_lane_boundaries'))
    }



def _try_run_ad_band_assist(
    pil_img: Image.Image,
    params: Dict[str, Any],
    lane_boundaries: Optional[List] = None
) -> Optional[Dict[str, Any]]:
    """Run AutoDense lanes/bands pipeline; resilient to signature changes across versions.

    Args:
        pil_img: PIL Image to analyze
        params: Analysis parameters
        lane_boundaries: Optional manual lane boundaries from calibration

    Returns dict: {lanes, bands, overlay, errors}
    """
    out: Dict[str, Any] = {"lanes": [], "bands": [], "overlay": None, "errors": []}
    if not HAS_AD_PIPELINE:
        out["errors"].append("AutoDense analysis pipeline is not available in this environment.")
        return out
    try:
        base_image = pil_img.convert("RGB")

        # Save PIL Image to temporary file for backend processing
        with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tmp:
            base_image.save(tmp.name, format='JPEG', quality=95)
            base = Path(tmp.name)

        kwargs = dict(params or {})

        # Add manual lane boundaries if provided
        if lane_boundaries is not None:
            # Debug: print the type and content of lane_boundaries
            print(f"DEBUG: lane_boundaries type: {type(lane_boundaries)}")
            if lane_boundaries and len(lane_boundaries) > 0:
                print(f"DEBUG: first boundary type: {type(lane_boundaries[0])}")
                print(f"DEBUG: first boundary: {lane_boundaries[0]}")
            kwargs['manual_lane_boundaries'] = lane_boundaries

        # Use translation layer for safe parameter handling
        ad_params = None
        translation_explanation = ""
        if ADParams is not None:
            ad_params, translation_explanation, translation_error = create_safe_ad_params(kwargs)
            if translation_error:
                out["errors"].append(f"Parameter translation failed: {translation_error}")
            if translation_explanation:
                # Store explanation for debugging/logging
                out["parameter_translation"] = translation_explanation

        # Try only valid call patterns using translated parameters
        attempts = []
        if ad_params is not None:
            attempts.append(lambda: ad_pipeline_run(image=base, p=ad_params))        # run(image=..., p=...)
            attempts.append(lambda: ad_pipeline_run(p=ad_params, image=base))       # run(p=..., image=...)
        else:
            # If ADParams creation failed, we can't proceed since run() requires a Params object
            out["errors"].append("Cannot call backend analysis: ADParams not available and parameter translation failed")

        res = None
        params_used = None
        observations = None
        errors = []

        # Only try to run if we have valid attempts
        if attempts:
            for attempt in attempts:
                try:
                    # Backend returns tuple: (analysis_result, params, observations)
                    pipeline_result = attempt()
                    if isinstance(pipeline_result, tuple) and len(pipeline_result) == 3:
                        res, params_used, observations = pipeline_result
                    else:
                        # Fallback for backward compatibility
                        res = pipeline_result
                    break
                except Exception as e:
                    errors.append(str(e))

        if res is None:
            if errors:
                out["errors"].append(errors[-1])
            else:
                # This handles the case where ad_params is None and no attempts were made
                out["errors"].append("Band detection failed: no valid call patterns available")
            return out

        # Store additional pipeline information for debugging
        if observations is not None:
            out["observations"] = obj_to_dict(observations)
        if params_used is not None:
            out["params_used"] = obj_to_dict(params_used)

        # Normalize analysis result to simple dicts
        try:
            rdict = obj_to_dict(res)
        except Exception as e:
            out["errors"].append(f"Failed to process analysis result: {str(e)}")
            return out
        # Extract lanes with robust error handling
        lanes_src = rdict.get("lanes") or rdict.get("detected_lanes") or []
        lanes = []
        try:
            for idx, ln in enumerate(lanes_src):
                if ln is None:
                    continue
                try:
                    d = obj_to_dict(ln)
                    d.setdefault("lane_index", idx)
                    lanes.append(d)
                except Exception as lane_error:
                    out["errors"].append(f"Failed to process lane {idx}: {lane_error}")
        except Exception as lanes_error:
            out["errors"].append(f"Failed to process lanes: {lanes_error}")
            lanes = []  # Ensure lanes is always a list

        # Extract bands with robust error handling
        bands = []
        try:
            for li, ln in enumerate(lanes_src):
                if ln is None:
                    continue
                try:
                    d = obj_to_dict(ln)
                    bands_list = d.get("bands") or d.get("detected_bands") or []
                    for bi, b in enumerate(bands_list):
                        if b is None:
                            continue
                        try:
                            bd = obj_to_dict(b)
                            bd.setdefault("lane_index", d.get("lane_index", li))
                            bd.setdefault("band_index", bi)
                            bands.append(bd)
                        except Exception as band_error:
                            out["errors"].append(f"Failed to process band {bi} in lane {li}: {band_error}")
                except Exception as lane_bands_error:
                    out["errors"].append(f"Failed to process bands for lane {li}: {lane_bands_error}")
        except Exception as bands_error:
            out["errors"].append(f"Failed to process bands: {bands_error}")
            bands = []  # Ensure bands is always a list

        # Overlay (official drawer if available, else fallback)
        overlay_bytes = None
        try:
            if draw_ad_overlay and res:
                # Backend draw_overlay saves to file and returns None
                # Create a temporary path for the overlay image
                with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as tmp_overlay:
                    tmp_overlay_path = Path(tmp_overlay.name)

                # Call draw_overlay - it saves to file and returns None
                draw_ad_overlay(base_image.copy(), res, tmp_overlay_path)  # type: ignore

                # Read the saved overlay image
                try:
                    if tmp_overlay_path.exists():
                        over = Image.open(tmp_overlay_path)
                        overlay_bytes = to_png_bytes(over)
                        over.close()  # Close file handle
                except Exception as read_error:
                    out["errors"].append(f"Failed to read overlay file: {read_error}")

                # Clean up temporary overlay path
                try:
                    tmp_overlay_path.unlink(missing_ok=True)
                except Exception:
                    pass  # Ignore cleanup errors

            if overlay_bytes is None:
                fallback = overlay_fallback(base_image, lanes, bands)
                overlay_bytes = to_png_bytes(fallback)
        except Exception as e:
            out["errors"].append(f"Overlay error: {e}")
            # Ensure fallback overlay is created even on error
            try:
                fallback = overlay_fallback(base_image, lanes, bands)
                overlay_bytes = to_png_bytes(fallback)
            except Exception:
                pass  # If even fallback fails, overlay_bytes stays None

        out.update({"lanes": lanes, "bands": bands, "overlay": overlay_bytes})

        # Clean up temporary file
        try:
            base.unlink(missing_ok=True)
        except Exception:
            pass  # Ignore cleanup errors

        return out
    except Exception as e:
        out["errors"].append(str(e))
        # Clean up temporary file in case of error
        try:
            if 'base' in locals() and isinstance(base, Path):
                base.unlink(missing_ok=True)
        except Exception:
            pass  # Ignore cleanup errors
        return out


def _band_assist_get_image_dims() -> Tuple[int, int]:
    """Return dimensions for the uploaded image, defaulting to metadata if absent."""
    img = st.session_state.get('res_uploaded_image')
    if img is not None:
        try:
            width, height = img.size  # type: ignore[attr-defined]
            return int(width), int(height)
        except Exception:
            pass
    metadata = st.session_state.get('res_image_metadata') or {}
    dims = metadata.get('dimensions') or metadata.get('original_dimensions') or (0, 0)
    if isinstance(dims, (list, tuple)) and len(dims) >= 2:
        try:
            return int(dims[0]), int(dims[1])
        except Exception:
            return (0, 0)
    return (0, 0)


def _band_assist_lane_for_x(
    lanes: List[Dict[str, Any]],
    x: float
) -> Tuple[Optional[Any], Optional[Dict[str, Any]]]:
    """Locate the lane dictionary that spans the provided x coordinate."""
    for lane in lanes:
        x0 = lane.get('x0', lane.get('left_px'))
        x1 = lane.get('x1', lane.get('right_px'))
        if x0 is None or x1 is None:
            continue
        try:
            left = float(x0)
            right = float(x1)
        except (TypeError, ValueError):
            continue
        if left <= x <= right:
            raw_idx = lane.get('lane_index', lane.get('lane_number', lane.get('index')))
            try:
                lane_idx = int(raw_idx)
            except (TypeError, ValueError):
                lane_idx = raw_idx
            return lane_idx, lane
    return None, None


def _band_assist_band_midpoint(band: Dict[str, Any]) -> float:
    """Return the vertical midpoint for a band dictionary."""
    y0 = band.get('y0', band.get('top', 0.0))
    y1 = band.get('y1', band.get('bottom', y0))
    try:
        return (float(y0) + float(y1)) / 2.0
    except (TypeError, ValueError):
        try:
            return float(y0)
        except (TypeError, ValueError):
            return 0.0


def _band_assist_reindex_bands(bands: List[Dict[str, Any]]) -> None:
    """Ensure band_index increments sequentially for each lane."""
    grouped: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for band in bands:
        lane_key = band.get('lane_index')
        grouped[str(lane_key)].append(band)
    for lane_bands in grouped.values():
        lane_bands.sort(key=_band_assist_band_midpoint)
        for order, band in enumerate(lane_bands):
            band['band_index'] = order


def _band_assist_refresh_overlay() -> None:
    """Rebuild the Band Assist overlay from current lane/band state."""
    base_image = st.session_state.get('res_uploaded_image')
    if base_image is None:
        return
    lanes = st.session_state.get('res_ba_lanes_rows') or []
    bands = st.session_state.get('res_ba_bands_rows') or []
    try:
        fallback_img = overlay_fallback(base_image.convert("RGB"), lanes, bands)
        st.session_state.res_ba_overlay_png = to_png_bytes(fallback_img)
    except Exception:
        pass


def _lane_boundary_to_overlay_dict(boundary: Any, image_height: int) -> Dict[str, int]:
    """Normalize lane boundary objects to overlay-friendly dictionaries."""
    if isinstance(boundary, dict):
        return {
            "x0": int(boundary.get("x0", boundary.get("left_px", 0))),
            "x1": int(boundary.get("x1", boundary.get("right_px", 0))),
            "y0": int(boundary.get("y0", 0)),
            "y1": int(boundary.get("y1", image_height)),
            "lane_index": int(boundary.get("lane_index", boundary.get("lane", 0))),
        }

    left = getattr(boundary, "left_px", None)
    right = getattr(boundary, "right_px", None)
    center = getattr(boundary, "center_px", None)
    width = getattr(boundary, "width_px", None)
    lane_index = getattr(boundary, "lane_index", getattr(boundary, "lane", 0))

    if left is None and center is not None and width is not None:
        left = center - width / 2.0
    if right is None and center is not None and width is not None:
        right = center + width / 2.0

    return {
        "x0": int(left or 0),
        "x1": int(right or 0),
        "y0": 0,
        "y1": image_height,
        "lane_index": int(lane_index or 0),
    }


def _band_assist_add_manual_band_from_click(x: float, y: float, band_span: int) -> Tuple[bool, str]:
    """Append a manual band centered on the click location."""
    lanes = st.session_state.get('res_ba_lanes_rows') or []
    if not lanes:
        return False, "No lane detections available yet."
    lane_idx, lane_data = _band_assist_lane_for_x(lanes, x)
    if lane_data is None:
        return False, "Click landed outside of the calibrated lanes."

    _, image_height = _band_assist_get_image_dims()
    if image_height <= 0:
        return False, "Image dimensions unavailable; upload a fresh image first."

    try:
        lane_left = float(lane_data.get('x0', lane_data.get('left_px', x)))
        lane_right = float(lane_data.get('x1', lane_data.get('right_px', x)))
    except (TypeError, ValueError):
        lane_left, lane_right = x - 5, x + 5

    half_span = max(2, int(band_span // 2))
    y0 = max(0, int(y) - half_span)
    y1 = min(image_height, int(y) + half_span)

    bands = list(st.session_state.get('res_ba_bands_rows') or [])
    tolerance = max(half_span, 8)
    lane_key = str(lane_idx)
    for band in bands:
        if str(band.get('lane_index')) != lane_key:
            continue
        if abs(_band_assist_band_midpoint(band) - y) <= tolerance:
            return False, "A band already exists near that location."

    new_band = {
        'lane_index': lane_idx,
        'band_index': -1,
        'x0': lane_left,
        'x1': lane_right,
        'y0': y0,
        'y1': y1,
        'intensity': 1.0,
        'confidence': float(st.session_state.get('params_conf_threshold', 0.5)),
        'source': 'manual'
    }

    bands.append(new_band)
    _band_assist_reindex_bands(bands)
    st.session_state.res_ba_bands_rows = bands
    st.session_state.band_assist_manual_run = True

    lane_label = lane_idx if lane_idx is not None else "?"
    return True, f"Added band to lane {lane_label}."


def _band_assist_remove_band_from_click(x: float, y: float, tolerance: int) -> Tuple[bool, str]:
    """Remove the closest band within tolerance of the click location."""
    bands = list(st.session_state.get('res_ba_bands_rows') or [])
    if not bands:
        return False, "No bands available to remove."

    lanes = st.session_state.get('res_ba_lanes_rows') or []
    lane_idx, _ = _band_assist_lane_for_x(lanes, x)
    if lane_idx is None:
        return False, "Click landed outside of the calibrated lanes."

    lane_key = str(lane_idx)
    best_idx = None
    best_dist = float('inf')

    for idx, band in enumerate(bands):
        if str(band.get('lane_index')) != lane_key:
            continue
        dist = abs(_band_assist_band_midpoint(band) - y)
        if dist < best_dist:
            best_dist = dist
            best_idx = idx

    if best_idx is None or best_dist > tolerance:
        return False, "No band detected near that location."

    removed = bands.pop(best_idx)
    _band_assist_reindex_bands(bands)
    st.session_state.res_ba_bands_rows = bands
    st.session_state.band_assist_manual_run = True

    lane_label = lane_idx if lane_idx is not None else "?"
    removed_idx = removed.get('band_index', '?')
    return True, f"Removed band {removed_idx} from lane {lane_label}."

# Page configuration
# Page configuration with enhanced metadata
# Load Better Dairy icon for page favicon
try:
    from PIL import Image as PILImage
    better_dairy_icon = PILImage.open("betterdairyicon.png")
except:
    better_dairy_icon = "🔬"  # Fallback to microscope if icon not found

st.set_page_config(
    page_title="AutoDense – UX Optimized",
    page_icon=better_dairy_icon,
    layout="wide",
    initial_sidebar_state="collapsed",
    menu_items={
        'Get Help': 'https://github.com/your-org/autodense',
        'Report a bug': 'mailto:support@autodense.com',
        'About': 'AutoDense: Professional gel electrophoresis analysis for bench scientists'
    }
)

# Display environment initialization status
def render_environment_status() -> None:
    if st.checkbox("🔧 Show Environment Status", value=False, help="View automatic environment configuration"):
        with st.expander("Environment Configuration Details", expanded=True):
            col1, col2, col3 = st.columns(3)

            with col1:
                st.metric("Project Root", "✅ Detected" if _env_status['python_path_set'] else "❌ Missing")
                st.metric("Dependencies", "✅ Available" if _env_status['dependencies_ok'] else "❌ Missing")

            with col2:
                st.metric("Environment File", "✅ Loaded" if _env_status['env_file_loaded'] else "❌ Missing")
                st.metric("API Config", "✅ Found" if _env_status['api_config_loaded'] else "❌ Missing")

            with col3:
                st.metric("OpenAI API Key", "✅ Configured" if _env_status['openai_key_available'] else "❌ Missing")
                st.metric("AutoDense Imports", "✅ Working" if HAS_PREPROCESS else "❌ Failed")

            if not all([_env_status['dependencies_ok'], _env_status['openai_key_available']]):
                st.warning("⚠️ Some components may not function properly. Check configuration files.")
            else:
                st.success("🚀 All environment components properly configured!")

# Better Dairy logo in header
header_col1, header_col2 = st.columns([8, 2])
with header_col1:
    st.title("AutoDense – UX Optimized Interface")
    st.markdown("*Professional gel electrophoresis analysis for bench scientists*")

with header_col2:
    render_environment_status()

# Accessibility: Skip links for keyboard navigation
st.markdown("""
<a href="#main-content" class="skip-link" tabindex="1">Skip to main content</a>
<a href="#navigation" class="skip-link" tabindex="2">Skip to navigation</a>
""", unsafe_allow_html=True)

# Enhanced CSS with accessibility and professional styling
st.markdown("""
<style>
/* Skip links for keyboard navigation */
.skip-link {
    position: absolute;
    top: -9999px;
    left: -9999px;
    background: #000;
    color: #fff;
    padding: 8px 12px;
    text-decoration: none;
    z-index: 9999;
    font-size: 16px;
    border-radius: 4px;
    transition: top 0.2s ease;
}

.skip-link:focus {
    top: 6px;
    left: 6px;
    outline: 3px solid #007bff;
    outline-offset: 2px;
}

/* Screen reader only content */
.sr-only {
    position: absolute !important;
    width: 1px !important;
    height: 1px !important;
    padding: 0 !important;
    margin: -1px !important;
    overflow: hidden !important;
    clip: rect(0,0,0,0) !important;
    white-space: nowrap !important;
    border: 0 !important;
}
/* Main container optimizations */
.main > div {
    padding-top: 0;
    margin-top: 0;
    max-width: 1400px;
}

/* Remove default Streamlit spacing */
.block-container {
    padding-top: 0 !important;
    margin-top: 0 !important;
}

/* Remove header spacing */
header[data-testid="stHeader"] {
    height: 0 !important;
    min-height: 0 !important;
}

/* Compact the toolbar */
.stApp > div:first-child {
    margin-top: 0 !important;
}

/* Accessibility improvements */
button:focus, .stSelectbox:focus, .stSlider:focus, .stNumberInput:focus {
    outline: 3px solid #007bff !important;
    outline-offset: 2px;
    box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.25);
}

/* High contrast mode support */
@media (prefers-contrast: high) {
    .workflow-step {
        border-width: 4px;
    }
    .status-indicator {
        border: 2px solid #000;
    }
}


/* Status indicators with better visibility */
.status-indicator {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    margin-right: 8px;
    border: 1px solid rgba(0,0,0,0.2);
}
/* Accessible status indicators with proper contrast ratios (WCAG AA compliant) */
.status-ready {
    background-color: #155724; /* Dark green - 7.07:1 contrast */
    color: white;
    border: 2px solid #155724;
}
.status-warning {
    background-color: #856404; /* Dark goldenrod - 6.26:1 contrast */
    color: white;
    border: 2px solid #856404;
}
.status-error {
    background-color: #721c24; /* Dark red - 6.48:1 contrast */
    color: white;
    border: 2px solid #721c24;
}
.status-info {
    background-color: #0c5460; /* Dark teal - 6.93:1 contrast */
    color: white;
    border: 2px solid #0c5460;
}

/* Enhanced workflow steps */
.workflow-step {
    border-left: 4px solid #e9ecef;
    padding: 12px 16px;
    margin: 8px 0;
    border-radius: 0 6px 6px 0;
    transition: all 0.3s ease;
    background: rgba(248, 249, 250, 0.5);
}
.workflow-step.active {
    border-left-color: #007bff;
    background: rgba(0, 123, 255, 0.08);
    transform: translateX(2px);
}
.workflow-step.complete {
    border-left-color: #28a745;
    background: rgba(40, 167, 69, 0.08);
}
.workflow-step.error {
    border-left-color: #dc3545;
    background: rgba(220, 53, 69, 0.08);
}

/* Enhanced buttons */
.stButton > button {
    transition: all 0.2s ease;
    border-radius: 6px;
    font-weight: 500;
}
.stButton > button:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
.stButton > button[data-baseweb="button"][kind="primary"] {
    background: linear-gradient(135deg, #007bff 0%, #0056b3 100%);
    border: none;
}

/* Error and info styling */
.error-details {
    background: #f8f9fa;
    border: 1px solid #dee2e6;
    border-left: 4px solid #dc3545;
    padding: 1rem;
    border-radius: 0 4px 4px 0;
    margin: 0.5rem 0;
}

.info-panel {
    background: #e7f3ff;
    border: 1px solid #b6d7ff;
    border-left: 4px solid #007bff;
    padding: 1rem;
    border-radius: 0 4px 4px 0;
    margin: 0.5rem 0;
}

.compact-card {
    background: #f6f9ff;
    border: 1px solid #d7e3ff;
    border-radius: 6px;
    padding: 0.6rem 0.9rem;
    margin-bottom: 0.6rem;
}

.compact-card .card-title {
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: #5c6c80;
    margin-bottom: 0.2rem;
}

.compact-card .card-value {
    font-weight: 600;
    font-size: 0.95rem;
    margin-bottom: 0.1rem;
}

.compact-card .card-subtext {
    font-size: 0.8rem;
    color: #6c7a90;
    margin: 0;
}

.hint-text {
    font-size: 0.8rem;
    color: #5c6c80;
    margin-top: 0.2rem;
}

.inline-pill {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.2rem 0.55rem;
    border-radius: 999px;
    background: rgba(0, 123, 255, 0.08);
    color: #0056b3;
    font-size: 0.8rem;
    font-weight: 600;
}

.status-panel {
    border-radius: 6px;
    padding: 0.75rem 1rem;
    margin: 0.5rem 0;
    border: 1px solid transparent;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-size: 0.95rem;
}

.status-panel.success {
    background: #e6f4ea;
    border-color: #a8d5b8;
    color: #1e6c3c;
}

.status-panel.error {
    background: #fdecea;
    border-color: #f5c2c7;
    color: #842029;
}

.status-panel .status-title {
    font-weight: 600;
}

.status-panel .status-subtext {
    opacity: 0.85;
}

/* Toast notification positioning */
.stToast {
    z-index: 1001;
}

/* Responsive design improvements */
@media (max-width: 768px) {
    .main > div {
        padding: 0.5rem;
    }
}

/* Loading animations */
@keyframes pulse {
    0% { opacity: 1; }
    50% { opacity: 0.5; }
    100% { opacity: 1; }
}
.loading-indicator {
    animation: pulse 1.5s ease-in-out infinite;
}

/* AGGRESSIVE WHITESPACE REDUCTION */

/* Main layout - drastically reduce padding */
.main .block-container {
    padding-top: 0.5rem !important;
    padding-bottom: 0.5rem !important;
    padding-left: 1rem !important;
    padding-right: 1rem !important;
    max-width: 1200px;
}

/* Remove excessive spacing from all Streamlit elements */
.element-container {
    margin-bottom: 0.25rem !important;
    margin-top: 0 !important;
}

/* Compact section spacing */
.stMarkdown {
    margin-bottom: 0.5rem !important;
}

/* Reduce header spacing */
h1, h2, h3, h4, h5, h6 {
    margin-top: 0.5rem !important;
    margin-bottom: 0.5rem !important;
    padding-top: 0 !important;
    padding-bottom: 0 !important;
}

/* Compact form elements */
.stSelectbox > div {
    margin-bottom: 0.5rem !important;
}

.stNumberInput > div {
    margin-bottom: 0.5rem !important;
}

.stButton > button {
    margin-top: 0.5rem !important;
    margin-bottom: 0.5rem !important;
}

/* Compact columns */
.stColumns {
    gap: 0.5rem !important;
}

/* Compact alerts and messages */
.stAlert {
    margin-top: 0.5rem !important;
    margin-bottom: 0.5rem !important;
    padding: 0.75rem !important;
}

/* Workflow progress indicator - compact */
.workflow-progress {
    position: sticky;
    top: 0;
    z-index: 1000;
    background: white;
    border-bottom: 1px solid #f0f2f6;
    padding: 0.25rem 0;
    margin: 0.5rem 0;
}

.progress-steps {
    display: flex;
    justify-content: space-between;
    align-items: center;
    max-width: 600px;
    margin: 0 auto;
    gap: 0.5rem;
}

.progress-step {
    display: flex;
    align-items: center;
    font-size: 0.85rem;
    color: #666;
    padding: 0.25rem 0.5rem;
    white-space: nowrap;
}

.progress-step.active {
    color: #ff4b4b;
    font-weight: 600;
    background: #fff5f5;
    border-radius: 4px;
}

.progress-step.completed {
    color: #00cc44;
    font-weight: 500;
}

/* Sticky calibration controls - compact */
.calibration-controls {
    position: sticky;
    top: 45px;
    z-index: 999;
    background: white;
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    padding: 0.75rem;
    margin: 0.5rem 0;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

/* Workflow section headers - compact */
.workflow-section {
    margin: 0 0 0.5rem 0;
    padding: 0.15rem 0;
}

/* Compact expanders */
.streamlit-expander {
    margin: 0.5rem 0 !important;
}

.streamlit-expander > div > div {
    padding: 0.5rem !important;
}

/* Compact file uploader */
.stFileUploader {
    margin: 0.5rem 0 !important;
}

.stFileUploader > div {
    padding: 1rem !important;
}

/* Results prioritization - compact */
.results-primary {
    order: 1;
    margin-bottom: 1rem;
}

.results-secondary {
    order: 2;
    margin-top: 0.5rem;
}

/* Auto-scroll behaviour */
.scroll-target {
    scroll-margin-top: 60px;
}

/* Inline step tracker */
.step-tracker {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    margin: 0.25rem 0 0.5rem 0;
}

.step-tracker .step-pill {
    flex: 1 1 0;
    text-align: center;
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.9rem;
    color: #6c757d;
    background: #f4f5f7;
    border: 1px solid transparent;
}

.step-tracker .step-pill.completed {
    color: #17813d;
    font-weight: 600;
    background: rgba(23, 129, 61, 0.12);
    border-color: rgba(23, 129, 61, 0.2);
}

.step-tracker .step-pill.active {
    color: #ff4b4b;
    font-weight: 600;
    background: rgba(255, 75, 75, 0.12);
    border-color: rgba(255, 75, 75, 0.3);
}

.calibration-tip {
    background: rgba(255, 75, 75, 0.06);
    border-left: 3px solid rgba(255, 75, 75, 0.4);
    padding: 8px 12px;
    margin-bottom: 0.5rem;
    border-radius: 6px;
    color: #4c4c4c;
    transition: opacity 0.25s ease;
}

.calibration-tip .calibration-icon {
    margin-right: 8px;
}

.calibration-tip.step2 {
    background: rgba(36, 155, 64, 0.1);
    border-left-color: rgba(36, 155, 64, 0.4);
    color: #1f5131;
}

.calibration-tip.step-done {
    background: rgba(223, 240, 216, 0.5);
    border-left-color: rgba(36, 155, 64, 0.2);
}

/* Sticky footer navigation */
#ad-sticky-footer {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 9999;
    padding: 10px 16px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(6px);
    border-top: 1px solid #e0e0e0;
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
    display: flex;
    justify-content: space-between;
    align-items: center;
}

#ad-sticky-footer .btn {
    background: #f8f9fa;
    border: 1px solid #dee2e6;
    color: #495057;
    padding: 8px 16px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.2s ease;
    text-decoration: none;
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

#ad-sticky-footer .btn:hover {
    background: #e9ecef;
    border-color: #adb5bd;
    transform: translateY(-1px);
}

#ad-sticky-footer .btn.primary {
    background: #0d6efd;
    border-color: #0d6efd;
    color: #fff;
}

#ad-sticky-footer .btn.primary:hover {
    background: #0b5ed7;
    border-color: #0a58ca;
}

#ad-sticky-footer .btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none !important;
}

#ad-sticky-footer .hint {
    color: #6c757d;
    font-size: 13px;
    text-align: center;
    flex: 1;
    margin: 0 16px;
}

@media (max-width: 768px) {
    #ad-sticky-footer {
        display: none;
    }
}

/* Navigation frame - compact */
.stIframe {
    margin: 0.5rem 0 !important;
}

/* Compact spacing for specific components */
.stRadio > div {
    gap: 0.25rem !important;
}

.stCheckbox {
    margin: 0.25rem 0 !important;
}

/* Reduce gaps in status displays */
.stSuccess, .stError, .stInfo, .stWarning {
    margin: 0.25rem 0 !important;
    padding: 0.5rem !important;
}
</style>

""", unsafe_allow_html=True)

# Session state initialization with consistent patterns
# --- Helper to smoothly scroll to workflow sections ---
def scroll_to_section(section_id: str) -> None:
    """Scroll the parent document to the element with the provided id."""
    st.markdown(f"""
    <script>
    const target = document.getElementById('{section_id}');
    if (target) {{
        target.scrollIntoView({{
            behavior: 'smooth',
            block: 'start'
        }});
    }}
    </script>
    """, unsafe_allow_html=True)


WORKFLOW_TABS = [
    "📸 Upload",
    "🎯 Calibration",
    "🔬 Analysis",
    "📊 Results",
    "💬 Companion",
]


def goto_tab(label_prefix: str) -> None:
    """Update the selected workflow tab to mimic legacy navigation helpers."""
    normalized = label_prefix.strip()
    for label in WORKFLOW_TABS:
        if label.startswith(normalized) or normalized.startswith(label.split()[0]):
            st.session_state.ui_workflow_tab = label
            break

def init_session_state() -> None:
    """Initialize session state following ui_* params_* res_* convention"""
    defaults: Dict[str, Any] = {
        # UI State
        'ui_workflow_tab': WORKFLOW_TABS[0],
        'ui_canvas_key': 0,
        'ui_analysis_count': 0,
        'ui_error_count': 0,
        'ui_last_action': None,
        
        # Parameters
        'params_gel_type': 'sds_page',
        'params_n_lanes': 12,
        'params_preprocessing_mode': 'Auto (heuristic)',
        'params_conf_threshold': 0.30,
        'params_mw_lane': 1,
        'params_ladder_type': 'auto',
        
        # Results
        'res_uploaded_image': None,
        'res_uploaded_array': None,
        'res_image_metadata': None,
        'res_calibration_points': [],
        'res_lane_boundaries': None,
        'res_preprocessing_outcome': None,
        'res_analysis_data': None,
        'res_export_data': None,

        # Band Assist
        'res_ba_overlay_png': None,
        'res_ba_lanes_rows': [],
        'res_ba_bands_rows': [],
        'res_ba_errors': [],

        # Control
        'analysis_cancelled': False,

        # Scroll flags
        '_scrolled_to_calibrate': False,
        '_scrolled_to_analyze': False,
        '_scrolled_to_results': False,
        'band_assist_manual_run': False,
        'lane_calibration_locked': False,
    }
    
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value

init_session_state()


def render_workflow_tracker() -> None:
    """Display progress pills for the major workflow stages."""
    upload_done = bool(st.session_state.get('res_uploaded_image'))
    calibration_done = bool(st.session_state.get('res_lane_boundaries'))
    analysis_done = bool(st.session_state.get('res_analysis_data'))

    step_defs = [
        ("1. Upload Image", upload_done, not upload_done),
        ("2. Calibrate Lanes", calibration_done, upload_done and not calibration_done),
        ("3. Analyze", analysis_done, calibration_done and not analysis_done),
        (
            "4. Results",
            analysis_done,
            analysis_done and not bool(st.session_state.get('res_export_data')),
        ),
    ]

    tracker_html = ["<div class=\"step-tracker\">"]
    for label, completed, active in step_defs:
        cls = "step-pill"
        if completed:
            cls += " completed"
        elif active:
            cls += " active"
        tracker_html.append(f'<div class="{cls}">{label}</div>')
    tracker_html.append('</div>')
    st.markdown("".join(tracker_html), unsafe_allow_html=True)

# Enhanced error handler decorator
def handle_errors(operation_name: str) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
    """Decorator for consistent error handling with user feedback"""
    def decorator(func: Callable[..., Any]) -> Callable[..., Any]:
        def wrapper(*args: Any, **kwargs: Any) -> Any:
            try:
                return func(*args, **kwargs)
            except Exception as e:
                st.session_state.ui_error_count += 1
                st.toast(f"{operation_name} failed", icon="❌")
                
                with st.expander(f"🔍 {operation_name} Error Details", expanded=True):
                    st.markdown(f"""
                    <div class="error-details">
                    <strong>Operation:</strong> {operation_name}<br>
                    <strong>Error:</strong> {str(e)}<br>
                    <strong>Error #{st.session_state.ui_error_count}</strong>
                    </div>
                    """, unsafe_allow_html=True)
                    
                    # Contextual troubleshooting based on operation
                    if "image" in operation_name.lower():
                        st.markdown("""
                        **🔧 Troubleshooting Steps:**
                        - Verify image format (PNG, JPG, TIFF)
                        - Check file isn't corrupted
                        - Try smaller image size (<10MB)
                        - Ensure sufficient system memory
                        """)
                    elif "calibration" in operation_name.lower():
                        st.markdown("""
                        **🔧 Troubleshooting Steps:**
                        - Ensure two different calibration points
                        - Verify points are within image bounds
                        - Choose points farther apart
                        - Check lane numbers are valid
                        """)
                    elif "analysis" in operation_name.lower():
                        st.markdown("""
                        **🔧 Troubleshooting Steps:**
                        - Complete image upload and calibration first
                        - Try different preprocessing mode
                        - Check available system resources
                        - Verify all parameters are valid
                        """)
                    
                    # Debug information toggle
                    if st.checkbox("Show technical details", key=f"debug_{operation_name}_{st.session_state.ui_error_count}"):
                        st.code(f"Exception: {type(e).__name__}: {str(e)}")
                        import traceback
                        st.code(traceback.format_exc())
                
                return None
        return wrapper
    return decorator



# Accessibility: Add main content landmark
st.markdown('<main id="main-content" role="main">', unsafe_allow_html=True)

def render_step_upload_and_calibration(section: str = "both") -> None:
    """Render Step 1 (upload) and Step 2 (lane calibration) content."""
    show_upload = section in ("both", "upload")
    show_calibration = section in ("both", "calibration")

    if show_upload:
        st.markdown("### 📸 Step 1: Upload & Configure Image")
        render_image_upload(key_prefix="main", show_metadata=True)

        if not st.session_state.res_uploaded_image:
            with st.expander("💡 Image Upload Guidelines", expanded=False):
                col1, col2 = st.columns(2)

                with col1:
                    st.markdown("""
                    **Optimal image characteristics:**
                    - **Format:** PNG or TIFF preferred, JPG acceptable
                    - **Resolution:** 1000-4000 pixels wide
                    - **Orientation:** Upright (ladder on left)
                    - **Lighting:** Even illumination without glare
                    """)

                with col2:
                    st.markdown("""
                    **Tips:**
                    - Crop away unused borders before uploading
                    - Avoid compression artifacts (set scanner to high quality)
                    - If image is dark, try manual preprocessing later
                    - Include the molecular weight ladder in the frame
                    """)
        else:
            st.success("✅ Image uploaded. Proceed to lane calibration when ready.")
            st.caption("Switch to the Lane Calibration tab to define boundaries and verify alignment.")

    if not show_calibration:
        return

    st.markdown("### 🎯 Step 2: Lane Calibration")

    if not st.session_state.res_uploaded_image:
        st.info("Upload a gel image in Step 1 before calibrating lanes.")
        return

    if st.session_state.get('lane_calibration_locked', False):
        st.info("🔒 Gel configuration & calibration are hidden because an analysis has already run.")
        if st.button("Edit gel configuration & calibration", key="unlock_gel_setup"):
            st.session_state.lane_calibration_locked = False
            st.rerun()
        return


    with st.expander("🧭 Gel configuration & lane calibration", expanded=True):
        # Gel configuration section
        st.markdown("### 🧪 Gel Configuration")

        col1, col2 = st.columns(2)

        with col1:
            gel_type = st.selectbox(
                "Gel Type:",
                ["sds_page", "etbr_agarose"],
                index=0 if st.session_state.params_gel_type == "sds_page" else 1,
                help="SDS-PAGE for protein separation, EtBr agarose for DNA separation",
                key="gel_type_select"
            )
            st.session_state.params_gel_type = gel_type

        with col2:
            default_lanes = 12 if gel_type == "sds_page" else 20
            if st.session_state.params_n_lanes not in (12, 20):
                default_lanes = st.session_state.params_n_lanes
            n_lanes = st.number_input(
                "Number of lanes:",
                min_value=2, max_value=30,
                value=default_lanes,
                step=1,
                help="Total number of sample lanes including molecular weight standard"
            )
            st.session_state.params_n_lanes = n_lanes

        calibration_expanded = (
            not st.session_state.get('lane_calibration_locked', False)
            and not st.session_state.get('res_lane_boundaries')
        )

        with st.expander("🎯 Step 2: Lane Calibration", expanded=calibration_expanded):
            st.markdown('<div class="scroll-target" id="section-calibrate">', unsafe_allow_html=True)

            if st.session_state.get('res_lane_boundaries'):
                st.markdown('<div class="calibration-controls">', unsafe_allow_html=True)
                st.markdown("**⚙️ Calibration Active** - Lane boundaries are set and ready for analysis")
                if st.button("♻️ Recalibrate Lanes", key="recalibrate_lanes", help="Modify the current lane calibration"):
                    st.session_state.res_lane_boundaries = None
                    st.session_state.res_calibration_points = []
                    st.session_state.ui_canvas_key += 1
                    st.session_state._scrolled_to_analyze = False
                    st.session_state._scrolled_to_results = False
                    st.session_state.lane_calibration_locked = False
                    st.rerun()
                st.markdown('</div>', unsafe_allow_html=True)

            render_calibration_instructions(expanded=False)

            @handle_errors("Lane Calibration")
            def perform_calibration():
                points = st.session_state.res_calibration_points

                if len(points) < 2:
                    return None

                lane1 = st.session_state.get('calibration_lane1', 1)
                lane2 = st.session_state.get('calibration_lane2', min(n_lanes, 12))

                if lane1 == lane2:
                    st.error("❌ Lane numbers must be different")
                    return None

                (x1, _), (x2, _) = points[:2]

                if HAS_LANE_MAPPING:
                    is_valid, validation_msg = validate_calibration_points(x1, lane1, x2, lane2, n_lanes, w)

                    if not is_valid:
                        st.error(f"❌ {validation_msg}")
                        return None

                    calibration = TwoPointCalibration(
                        point1_x=x1, point1_lane=lane1,
                        point2_x=x2, point2_lane=lane2,
                        total_lanes=n_lanes
                    )

                    boundaries = calculate_lane_positions(calibration)
                    st.session_state.res_lane_boundaries = boundaries
                    st.session_state.ui_last_action = "calibration"
                    st.session_state._scrolled_to_analyze = False
                    st.session_state._scrolled_to_results = False

                    return calibration, boundaries

                return None

            if st.session_state.res_uploaded_image and st.session_state.res_uploaded_array is not None:
                img = st.session_state.res_uploaded_image
                img_array = st.session_state.res_uploaded_array
                h, w, _ = img_array.shape
            else:
                st.error("❌ Image data not available. Please upload an image first.")
                st.stop()

        if HAS_IMAGE_COORDINATES:
            st.markdown("#### 🖱️ Interactive Calibration")

            controls_col1, controls_col2, controls_col3 = st.columns([1, 1, 1])

            with controls_col1:
                clear_btn = st.button(
                    "🔄 Clear Points",
                    help="Remove all calibration points and start over",
                    use_container_width=True
                )
                if clear_btn:
                    st.session_state.res_calibration_points = []
                    st.session_state.ui_canvas_key += 1
                    st.session_state.ui_last_action = "clear_calibration"
                    st.session_state.lane_calibration_locked = False
                    st.rerun()

            with controls_col2:
                undo_btn = st.button(
                    "↩️ Undo Last",
                    help="Remove the most recently placed point",
                    use_container_width=True,
                    disabled=len(st.session_state.res_calibration_points) == 0
                )
                if undo_btn and st.session_state.res_calibration_points:
                    st.session_state.res_calibration_points = st.session_state.res_calibration_points[:-1]
                    st.session_state.ui_canvas_key += 1
                    st.session_state.ui_last_action = "undo_calibration"
                    st.rerun()

            with controls_col3:
                st.markdown("**Calibration Mode**")
                mode_sel = st.radio(
                    "Calibration mode",
                    ["Standard", "Advanced"],
                    help="Standard: guided two-point calibration. Advanced: custom lane placement.",
                    label_visibility="collapsed",
                    horizontal=True,
                    key="calibration_mode_select"
                )
                st.session_state.calibration_mode = mode_sel

            tips_col1, tips_col2 = st.columns(2)
            with tips_col1:
                st.info("👆 **Tip:** Place the first point in the MW ladder lane and the second in the far-right reference lane.")
            with tips_col2:
                st.info("🧪 **Need to recalibrate later?** Use the Recalibrate button after analysis to reopen this section.")

            st.markdown("##### Calibration Canvas")
            canvas_col1, canvas_col2 = st.columns([4, 1])

            draw_img = img.copy()
            draw = ImageDraw.Draw(draw_img)
            for idx, (px, py) in enumerate(st.session_state.res_calibration_points):
                color = "#FF3366" if idx == 0 else "#33C1FF"
                r = 6
                draw.ellipse((px - r, py - r, px + r, py + r), fill=color)
                draw.text((px + 10, py), f"P{idx+1}", fill=color)

            display_width = min(w, 800)
            display_height = int(display_width * h / w)

            with canvas_col1:
                coord_result = streamlit_image_coordinates(
                    draw_img,
                    width=display_width,
                    height=display_height,
                    cursor="crosshair",
                    key=f"calibration_coords_{st.session_state.ui_canvas_key}"
                )

            with canvas_col2:
                st.markdown("**Calibration Points**")
                if st.session_state.res_calibration_points:
                    for i, (px, py) in enumerate(st.session_state.res_calibration_points, 1):
                        st.markdown(f"P{i}: ({px}, {py})")
                else:
                    st.caption("No points selected yet.")

            if coord_result is not None and coord_result.get('x') is not None:
                with st.spinner("🎯 Processing calibration point..."):
                    scale_x = w / display_width
                    scale_y = h / display_height

                    x = int(coord_result['x'] * scale_x)
                    y = int(coord_result['y'] * scale_y)

                    current_points = st.session_state.res_calibration_points[:]

                    is_duplicate = any(abs(x - existing_x) < 5 and abs(y - existing_y) < 5 for existing_x, existing_y in current_points)

                    if len(current_points) < 2 and not is_duplicate:
                        current_points.append((x, y))
                        st.session_state.res_calibration_points = current_points
                        announce_to_screen_reader(f"Calibration point {len(current_points)} added at position {x}, {y}", "assertive")
                        st.rerun()
                    elif len(current_points) >= 2:
                        st.info("✋ Already have 2 points. Use Clear or Undo to reset.")

        else:
            st.warning("⚠️ Interactive canvas not available. Using manual coordinate entry mode.")

            with st.expander("📍 Manual Coordinate Entry", expanded=True):
                st.info("Enter pixel coordinates by examining your image. You can use image viewing software to find precise coordinates.")

                col1, col2 = st.columns(2)

                with col1:
                    st.markdown("**🎯 Point 1 - MW Standard Lane**")
                    pt1_x = st.number_input(
                        "X coordinate (pixels from left edge)",
                        min_value=0, max_value=w, value=max(w//6, 50),
                        step=1, key="manual_pt1_x",
                        help=f"Range: 0-{w} pixels"
                    )
                    pt1_y = st.number_input(
                        "Y coordinate (pixels from top edge)",
                        min_value=0, max_value=h, value=h//3,
                        step=1, key="manual_pt1_y",
                        help=f"Range: 0-{h} pixels"
                    )

                    if pt1_x and pt1_y:
                        rel_x = pt1_x / w * 100
                        rel_y = pt1_y / h * 100
                        st.caption(f"📍 Point 1: {rel_x:.1f}% from left, {rel_y:.1f}% from top")

                with col2:
                    st.markdown("**🎯 Point 2 - Reference Lane**")
                    pt2_x = st.number_input(
                        "X coordinate (pixels from left edge)",
                        min_value=0, max_value=w, value=min(5*w//6, w-50),
                        step=1, key="manual_pt2_x",
                        help=f"Range: 0-{w} pixels"
                    )
                    pt2_y = st.number_input(
                        "Y coordinate (pixels from top edge)",
                        min_value=0, max_value=h, value=h//3,
                        step=1, key="manual_pt2_y",
                        help=f"Range: 0-{h} pixels"
                    )

                    if pt2_x and pt2_y:
                        rel_x = pt2_x / w * 100
                        rel_y = pt2_y / h * 100
                        st.caption(f"📍 Point 2: {rel_x:.1f}% from left, {rel_y:.1f}% from top")

                if pt1_x and pt1_y and pt2_x and pt2_y:
                    distance = ((pt2_x - pt1_x)**2 + (pt2_y - pt1_y)**2)**0.5
                    if distance < w * 0.2:
                        st.warning("⚠️ Points are quite close together. Consider spacing them farther apart for better accuracy.")
                    else:
                        st.success(f"✅ Point separation: {distance:.1f} pixels (good spacing)")

            if st.button("✅ Apply Manual Calibration Points", use_container_width=True):
                st.session_state.res_calibration_points = [(int(pt1_x), int(pt1_y)), (int(pt2_x), int(pt2_y))]
                st.session_state.ui_canvas_key += 1
                st.session_state.ui_last_action = "manual_calibration"
                st.toast("Manual calibration points applied", icon="✅")
                st.rerun()

        # Update lane boundaries automatically when two points are present
        if len(st.session_state.res_calibration_points) == 2:
            perform_calibration()

        # Use calibrated boundaries to generate preview overlay
        if st.session_state.res_lane_boundaries:
            st.markdown("### ✅ Calibration Preview")
            if HAS_LANE_MAPPING:
                try:
                    from autodense.preprocess.simple_lane_mapping import boundaries_to_dataframe
                    st.dataframe(boundaries_to_dataframe(st.session_state.res_lane_boundaries), use_container_width=True)
                except Exception as exc:
                    st.warning(f"Could not render lane table: {exc}")

            if st.session_state.res_uploaded_image and HAS_AD_PIPELINE:
                img_height = st.session_state.res_uploaded_image.height
                lane_boxes = [
                    _lane_boundary_to_overlay_dict(boundary, img_height)
                    for boundary in (st.session_state.res_lane_boundaries or [])
                ]
                preview_img = overlay_fallback(
                    st.session_state.res_uploaded_image,
                    lane_boxes,
                    []
                )
                st.image(preview_img, caption="Lane calibration preview", use_container_width=True)

        else:
            st.warning("⚠️ Calibration not complete. Please place two calibration points.")


def render_step_analysis() -> None:
    if not st.session_state.get('res_lane_boundaries'):
        return

    expanded = not bool(st.session_state.get('res_analysis_data'))
    with st.expander("🔬 Step 3: Analysis & Processing", expanded=expanded):
        st.markdown('<div class="workflow-section scroll-target" id="section-analyze">', unsafe_allow_html=True)
        _render_analysis_inner()
        st.markdown('</div>', unsafe_allow_html=True)


def render_step_results() -> None:
    results = st.session_state.get('res_analysis_data')
    if not results:
        return

    st.markdown('<div class="workflow-section scroll-target" id="section-results">', unsafe_allow_html=True)
    st.markdown("### 📊 Step 4: Results Overview")

    st.markdown('<div class="results-primary">', unsafe_allow_html=True)
    overlay_png = st.session_state.get('res_ba_overlay_png')
    if overlay_png:
        st.image(overlay_png, caption="Analysis overlay", use_container_width=True)
    else:
        st.caption("Run analysis and Band Assist to view the annotated gel overlay.")
    st.markdown('</div>', unsafe_allow_html=True)

    st.markdown("### 🧪 Band Assist Post-Processing")

    feedback = st.session_state.pop('band_assist_feedback', None)
    feedback_announce = st.session_state.pop('band_assist_feedback_announce', None)
    if feedback:
        level, text = feedback
        if level == 'success':
            st.success(text)
        elif level == 'warning':
            st.warning(text)
        else:
            st.info(text)
    if feedback_announce:
        announce_to_screen_reader(feedback_announce, "assertive")

    has_overlay = bool(st.session_state.get('res_ba_overlay_png'))
    has_lanes = bool(st.session_state.get('res_ba_lanes_rows'))
    has_bands = bool(st.session_state.get('res_ba_bands_rows'))

    with st.expander("1️⃣ Run or refresh Band Assist", expanded=False):
        st.caption("Re-run the deterministic pipeline with your current calibration before manual tweaking.")
        ctrl_run, ctrl_help = st.columns([1, 1])
        with ctrl_run:
            run_ba = st.button("🚀 Run Band Assist", use_container_width=True)
        with ctrl_help:
            st.caption("Uses the uploaded image and lane calibration captured in earlier steps.")

        if run_ba:
            if not st.session_state.res_uploaded_image:
                st.error("Upload an image first.")
            else:
                params = {
                    "gel_type": st.session_state.params_gel_type,
                    "conf_threshold": float(st.session_state.params_conf_threshold),
                    "mw_lane": int(st.session_state.params_mw_lane),
                }

                with st.expander("🔧 Parameter translation", expanded=False):
                    is_valid, backend_params, explanations, errors = validate_and_explain_params(params)
                    if errors:
                        st.error("Parameter validation failed:")
                        for error in errors:
                            st.error(f"• {error}")
                    else:
                        st.success("✅ Parameters validated successfully")
                        for explanation in explanations:
                            st.text(explanation)

                lane_calibration = get_current_lane_calibration()
                boundaries = lane_calibration['lane_boundaries']
                res = _try_run_ad_band_assist(
                    st.session_state.res_uploaded_image,
                    params,
                    lane_boundaries=boundaries
                )
                st.session_state.res_ba_errors = res.get("errors", [])
                st.session_state.res_ba_lanes_rows = res.get("lanes", [])
                st.session_state.res_ba_bands_rows = res.get("bands", [])
                st.session_state.res_ba_overlay_png = res.get("overlay")
                if res.get("errors"):
                    st.session_state.band_assist_manual_run = False
                    st.warning(" ; ".join(res["errors"]))
                else:
                    st.session_state.band_assist_manual_run = True
                    st.success("Band Assist completed")

    with st.expander("2️⃣ Manual review & editing", expanded=not st.session_state.get('band_assist_finalized', False)):
        if has_overlay or has_lanes:
            overlay_col, lanes_col = st.columns([3, 2])
            with overlay_col:
                if has_overlay:
                    st.image(
                        st.session_state.res_ba_overlay_png,
                        caption="Editable overlay",
                        use_container_width=True,
                    )
                else:
                    st.caption("Run Band Assist to generate the overlay before editing.")
            with lanes_col:
                st.markdown("**Lane summary**")
                lane_cols = ["lane_index", "x0", "y0", "x1", "y1", "lane_type", "band_count"]
                lanes_tbl = [
                    {k: row.get(k, "") for k in lane_cols}
                    for row in (st.session_state.res_ba_lanes_rows or [])
                ]
                if lanes_tbl:
                    st.dataframe(lanes_tbl, use_container_width=True, hide_index=True)
                else:
                    st.caption("No lanes detected yet.")

        if has_overlay:
            st.session_state.setdefault('band_assist_default_span', 18)
            st.session_state.setdefault('band_assist_last_click', None)
            if HAS_IMAGE_COORDINATES:
                with st.expander("🎯 Interactive band editor", expanded=False):
                    st.caption("Click on the overlay to add or remove bands. Adjust the span to match your gel.")
                    ctrl_col, hint_col = st.columns([2, 1])
                    with ctrl_col:
                        click_action = st.radio(
                            "Click action",
                            ["Add band", "Remove band"],
                            horizontal=True,
                            key="band_assist_click_action",
                        )
                        band_span = st.slider(
                            "Band height (px)",
                            min_value=6,
                            max_value=60,
                            value=int(st.session_state.get('band_assist_default_span', 18)),
                            step=2,
                            help="Sets the vertical window used when adding or removing bands.",
                            key="band_assist_band_span",
                        )
                    with hint_col:
                        st.caption("Add captures a new band centered on your click. Remove targets the nearest band in that lane.")

                    st.session_state.band_assist_default_span = band_span
                    removal_tolerance = max(10, int(band_span // 2) + 6)
                    width, height = _band_assist_get_image_dims()
                    click_key = f"band_assist_click_{st.session_state.get('_ba_editor_version', 0)}"
                    overlay_bytes = st.session_state.res_ba_overlay_png
                    if isinstance(overlay_bytes, bytes):
                        overlay_img = Image.open(io.BytesIO(overlay_bytes))
                    else:
                        overlay_img = overlay_bytes

                    click = streamlit_image_coordinates(
                        overlay_img,
                        width=width or None,
                        height=height or None,
                        key=click_key,
                    )

                    if click and click.get('x') is not None and click.get('y') is not None:
                        click_point = (int(click['x']), int(click['y']), click_action)
                        if st.session_state.get('band_assist_last_click') != click_point:
                            if click_action == "Add band":
                                success, message = _band_assist_add_manual_band_from_click(
                                    click_point[0], click_point[1], band_span
                                )
                            else:
                                success, message = _band_assist_remove_band_from_click(
                                    click_point[0], click_point[1], removal_tolerance
                                )

                            if success:
                                st.session_state['band_assist_last_click'] = click_point
                                st.session_state['_ba_editor_version'] = st.session_state.get('_ba_editor_version', 0) + 1
                                _band_assist_refresh_overlay()
                                st.session_state['band_assist_feedback'] = ('success', message)
                                st.session_state['band_assist_feedback_announce'] = message
                                st.rerun()
                            else:
                                st.session_state['band_assist_last_click'] = click_point
                                st.session_state['band_assist_feedback'] = ('warning', message)
                                st.rerun()
                    else:
                        st.session_state['band_assist_last_click'] = None
            else:
                with st.expander("🎯 Interactive band editor", expanded=False):
                    st.info("Install `streamlit-image-coordinates` to enable click-based editing.")

        if has_bands:
            st.session_state.setdefault('_ba_editor_version', 0)
            st.caption("Band table reflects click edits. Tooltips with per-band metrics will land in a follow-up.")
            band_cols = ["lane_index", "band_index", "x0", "x1", "y0", "y1", "intensity", "confidence"]
            base_rows = [
                {k: row.get(k, "") for k in band_cols}
                for row in st.session_state.res_ba_bands_rows
            ]
            editor_key = f"ba_bands_editor_{st.session_state['_ba_editor_version']}"
            edited = st.data_editor(
                base_rows,
                key=editor_key,
                num_rows="dynamic",
                use_container_width=True,
                hide_index=True,
            )
            if st.button("Apply edits and refresh overlay", key="apply_ba_edits"):
                with st.spinner("Updating band overlay..."):
                    if isinstance(edited, pd.DataFrame):
                        edited_records = edited.to_dict(orient='records')
                    elif isinstance(edited, list):
                        edited_records = edited
                    elif isinstance(edited, dict) and edited.get('data'):
                        edited_records = edited['data']
                    elif edited:
                        edited_records = list(edited)
                    else:
                        edited_records = []

                    st.session_state.res_ba_bands_rows = edited_records
                    _band_assist_reindex_bands(st.session_state.res_ba_bands_rows)
                    _band_assist_refresh_overlay()
                    st.session_state['_ba_editor_version'] += 1
                    st.success("✅ Band edits applied")
                    announce_to_screen_reader("Band edits applied successfully", "assertive")

        finalize_cols = st.columns([1, 1])
        with finalize_cols[0]:
            if st.button("✅ Accept band set", key="band_assist_finalize"):
                st.session_state.band_assist_finalized = True
                st.success("Band detections locked for final quantification.")
                announce_to_screen_reader("Band detections finalized", "assertive")
        with finalize_cols[1]:
            if st.session_state.get('band_assist_finalized'):
                if st.button("↩️ Reopen editing", key="band_assist_reopen"):
                    st.session_state.band_assist_finalized = False
                    st.info("Band Assist editing re-enabled.")

    if st.session_state.get('band_assist_finalized'):
        st.success("✅ Band Assist finalized — downstream quantification will use accepted bands.")
    else:
        st.info("Finalize Band Assist to proceed with confident quantification and labeling.")

    with st.expander("🧾 Lane labeling (coming soon)", expanded=False):
        st.info(
            "Lane annotation tools will allow manual entry or CSV import once bands are accepted. "
            "For now, note the desired labels so they can be added when the feature lands."
        )

    st.markdown('<div class="results-secondary">', unsafe_allow_html=True)

    st.success(f"""
    ✅ **Analysis complete** — ID: {results['analysis_id']}  
    📅 Completed: {results['timestamp'][:19]}  |  🔄 Run #{results['analysis_count']}
    """)

    params = results.get('parameters', {})
    lane_boundaries = results.get('lane_boundaries') or []
    detected_bands = st.session_state.get('res_ba_bands_rows') or []
    preproc = results.get('preprocessing', {})

    summary_cols = st.columns(3)
    with summary_cols[0]:
        st.metric("Total lanes", params.get('n_lanes', len(lane_boundaries)))
        st.metric("MW lane", f"Lane {params.get('mw_lane', '—')}")
    with summary_cols[1]:
        st.metric("Bands listed", len(detected_bands))
        st.metric("Confidence", f"{params.get('conf_threshold', 0):.2f}")
    with summary_cols[2]:
        st.metric("Gel type", params.get('gel_type', '—').replace('_', ' ').title())
        st.metric("Modality", params.get('modality', '—').upper())

    with st.expander("🔬 Preprocessing details", expanded=False):
        mode = preproc.get('mode', 'Unknown')
        status = preproc.get('status', 'unknown')
        if status == 'success':
            st.success(f"Method: {mode}")
        elif status == 'error':
            st.error(f"Method failed: {mode}")
            if preproc.get('error'):
                st.caption(preproc['error'])
        else:
            st.info(f"Method: {mode}")

        before_metrics = preproc.get('before') or {}
        after_metrics = preproc.get('after') or {}
        if before_metrics and after_metrics:
            delta_cols = st.columns(2)
            with delta_cols[0]:
                st.metric("SNR", f"{after_metrics.get('snr', 0):.2f}", f"{after_metrics.get('snr',0) - before_metrics.get('snr',0):+.2f}")
            with delta_cols[1]:
                st.metric("Lane separation", f"{after_metrics.get('sep', 0):.2f}", f"{after_metrics.get('sep',0) - before_metrics.get('sep',0):+.2f}")
        if preproc.get('note'):
            st.caption(preproc['note'])
        with st.expander("Raw preprocessing metadata", expanded=False):
            st.json(preproc)

    with st.expander("🎯 Lane & band details", expanded=False):
        if lane_boundaries and HAS_LANE_MAPPING:
            try:
                from autodense.preprocess.simple_lane_mapping import boundaries_to_dataframe
                st.dataframe(boundaries_to_dataframe(lane_boundaries), use_container_width=True)
            except Exception as exc:
                st.warning(f"Could not render lane table: {exc}")
        else:
            st.caption("Lane boundary table not available.")

        if detected_bands:
            st.metric("Bands recorded", len(detected_bands))
            st.caption("Modify bands from the Band Assist panel above.")

    with st.expander("⚙️ Analysis parameters", expanded=False):
        config_cols = st.columns(2)
        with config_cols[0]:
            st.markdown("**Gel configuration**")
            st.write(f"• Type: {params.get('gel_type', '—')}")
            st.write(f"• Modality: {params.get('modality', '—').upper()}")
            st.write(f"• Total lanes: {params.get('n_lanes', '—')}")
            st.write(f"• MW lane: {params.get('mw_lane', '—')}")
        with config_cols[1]:
            st.markdown("**Detection settings**")
            st.write(f"• Confidence threshold: {params.get('conf_threshold', 0):.2f}")
            st.write(f"• Ladder type: {params.get('ladder_type', '—')}")
            st.write(f"• Preprocessing mode: {params.get('preprocessing_mode', '—')}")
            if params.get('manual_params'):
                st.caption("Manual preprocessing parameters were applied.")
        if results.get('warnings'):
            st.warning("Warnings: " + "; ".join(results['warnings']))

    st.markdown("### 💾 Save & Continue")
    st.button("💾 Save analysis snapshot", disabled=True, help="Persistence workspace coming soon")
    st.caption("Use this checkpoint to persist results before deeper analysis or reporting.")

    with st.expander("📥 Export & download", expanded=False):
        lanes = st.session_state.get("res_ba_lanes_rows") or []
        bands = st.session_state.get("res_ba_bands_rows") or []
        overlay = st.session_state.get("res_ba_overlay_png")

        lane_cols = ["lane_index", "x0", "y0", "x1", "y1", "lane_type", "band_count"]
        band_cols = ["lane_index", "band_index", "x0", "x1", "y0", "y1", "intensity", "confidence"]
        lane_data = convert_to_csv(lanes, lane_cols) if lanes else ""
        band_data = convert_to_csv(bands, band_cols) if bands else ""

        export_cols = st.columns(3)
        with export_cols[0]:
            st.markdown("**Lanes/Bands CSV**")
            st.download_button("⬇️ lanes.csv", data=lane_data, file_name="lanes.csv", mime="text/csv", use_container_width=True, disabled=not lanes)
            st.download_button("⬇️ bands.csv", data=band_data, file_name="bands.csv", mime="text/csv", use_container_width=True, disabled=not bands)
        with export_cols[1]:
            st.markdown("**Analysis snapshot**")
            analysis_json = json.dumps(results, indent=2, default=str)
            st.download_button("⚙️ analysis.json", data=analysis_json, file_name=f"autodense_analysis_{results['analysis_id']}.json", mime="application/json", use_container_width=True)
            st.button("📄 summary.pdf", disabled=True, help="Coming soon", use_container_width=True)
        with export_cols[2]:
            st.markdown("**Visuals**")
            st.download_button("🖼️ overlay.png", data=overlay or b"", file_name="overlay.png", mime="image/png", use_container_width=True, disabled=overlay is None)
            st.button("📊 charts.zip", disabled=True, help="Coming soon", use_container_width=True)

    st.markdown('</div>', unsafe_allow_html=True)
    st.markdown('</div>', unsafe_allow_html=True)

    # 5. Analysis History and Management
    with st.expander("🗂️ Analysis History & Management", expanded=False):
        st.markdown(f"**Current Analysis:** {results['analysis_id']}")
        st.markdown(f"**Total Analyses This Session:** {st.session_state.ui_analysis_count}")
        
        # Session analysis history (if multiple analyses)
        if st.session_state.ui_analysis_count > 1:
            st.info(f"This is analysis #{st.session_state.ui_analysis_count} in the current session.")
            
            # Option to compare with previous analyses
            st.button(
                "📈 **Compare with Previous**",
                disabled=True,
                help="Analysis comparison feature (coming soon)",
                use_container_width=True
            )
        
        # Clear results option
        col1, col2 = st.columns(2)
        
        with col1:
            if st.button("🗑️ **Clear Results**", help="Remove current analysis results"):
                with st.spinner("🗑️ Clearing analysis results..."):
                    st.info("🔄 Removing all analysis data and cached results...")
                    st.session_state.res_analysis_data = None
                    st.session_state.res_export_data = None
                    st.session_state.lane_calibration_locked = False
                    announce_to_screen_reader("Analysis results cleared successfully", "assertive")
                st.rerun()
        
        with col2:
            st.button(
                "💾 **Save Session**",
                disabled=True,
                help="Save analysis session for later (coming soon)"
            )
    
    # 6. Development and Debug Information
    if st.checkbox("🔍 **Developer Debug Information**", help="Show technical details for troubleshooting"):
        with st.expander("Debug Information", expanded=False):
            
            st.markdown("**Session State Summary:**")
            debug_state = {
                k: str(v)[:200] + "..." if len(str(v)) > 200 else v 
                for k, v in st.session_state.to_dict().items()
                if not k.startswith('_')
            }
            st.json(debug_state)
            
            st.markdown("**Results Object Structure:**")
            result_structure = {
                "keys": list(results.keys()),
                "preprocessing_keys": list(results.get('preprocessing', {}).keys()),
                "parameters_keys": list(results.get('parameters', {}).keys()),
                "lane_boundaries_count": len(results.get('lane_boundaries', [])),
                "total_size_estimate": len(str(results))
            }
            st.json(result_structure)
            
            st.markdown("**System Information:**")
            system_info = {
                "has_preprocess": HAS_PREPROCESS,
                "has_drawable_canvas": HAS_DRAWABLE_CANVAS,
                "has_lane_mapping": HAS_LANE_MAPPING,
                "session_analysis_count": st.session_state.ui_analysis_count,
                "session_error_count": st.session_state.ui_error_count
            }
            st.json(system_info)


def render_chat_companion_tab() -> None:
    st.markdown("### 💬 Analysis Companion")

    if not st.session_state.get('res_analysis_data'):
        st.info("Run an analysis to populate the companion workspace with quantified results.")
        return

    st.info(
        "A guided chat workspace will arrive soon. It will let you interrogate band metrics, "
        "launch predefined analysis playbooks, and draft reports from natural language prompts."
    )

def render_global_sticky_footer() -> None:
    """Fixed footer with Back / Next buttons anchored to workflow sections."""
    import streamlit.components.v1 as components

    components.html("""
    <script>
    (function() {
        const parentWin = window.parent;
        if (!parentWin || !parentWin.document) {
            return;
        }
        const doc = parentWin.document;

        if (!doc.getElementById('ad-sticky-footer')) {
            const footer = doc.createElement('div');
            footer.id = 'ad-sticky-footer';
            footer.setAttribute('role', 'navigation');
            footer.setAttribute('aria-label', 'Workflow navigation');
            footer.innerHTML = `
                <button id="ad-back" class="btn" type="button">⬅️ Back</button>
                <div class="hint">Use Back/Next to move through Upload → Calibrate → Analyze → Results</div>
                <button id="ad-next" class="btn primary" type="button">Next ➡️</button>
            `;
            doc.body.appendChild(footer);
        }

        const sections = ['section-upload','section-calibrate','section-analyze','section-results'];

        function scrollToSection(idx) {
            if (idx < 0 || idx >= sections.length) return;
            const el = doc.getElementById(sections[idx]);
            if (!el) return;
            parentWin.scrollTo({
                top: el.getBoundingClientRect().top + parentWin.scrollY - 60,
                behavior: 'smooth'
            });
        }

        function nearestSectionIdx() {
            let bestIdx = 0;
            let minDelta = Number.POSITIVE_INFINITY;
            sections.forEach((id, idx) => {
                const el = doc.getElementById(id);
                if (!el) return;
                const rect = el.getBoundingClientRect();
                const center = rect.top + rect.height / 2;
                const delta = Math.abs(center);
                if (delta < minDelta) {
                    minDelta = delta;
                    bestIdx = idx;
                }
            });
            return bestIdx;
        }

        function configureButtons() {
            const backBtn = doc.getElementById('ad-back');
            const nextBtn = doc.getElementById('ad-next');
            if (!backBtn || !nextBtn) {
                return;
            }
            const current = nearestSectionIdx();
            backBtn.disabled = current <= 0;
            nextBtn.disabled = current >= sections.length - 1;
            backBtn.onclick = () => scrollToSection(current - 1);
            nextBtn.onclick = () => scrollToSection(current + 1);
        }

        if (!parentWin.__adStickyFooterListenersAttached) {
            parentWin.addEventListener('scroll', () => {
                parentWin.requestAnimationFrame(configureButtons);
            }, { passive: true });
            parentWin.addEventListener('resize', () => {
                parentWin.requestAnimationFrame(configureButtons);
            });
            parentWin.__adStickyFooterListenersAttached = true;
        }

        configureButtons();

        if (document && document.body) {
            document.body.innerHTML = '';
        }
    })();
    </script>
    """, height=0)

# Workflow navigation tabs
render_workflow_tracker()

current_tab = st.radio(
    "Workflow",
    WORKFLOW_TABS,
    index=WORKFLOW_TABS.index(st.session_state.ui_workflow_tab)
    if st.session_state.ui_workflow_tab in WORKFLOW_TABS else 0,
    horizontal=True,
    key="ui_workflow_tab"
)

if current_tab == "📸 Upload":
    st.markdown('<div id="section-upload"></div>', unsafe_allow_html=True)
    render_step_upload_and_calibration("upload")
elif current_tab == "🎯 Calibration":
    st.markdown('<div id="section-calibrate"></div>', unsafe_allow_html=True)
    render_step_upload_and_calibration("calibration")
elif current_tab == "🔬 Analysis":
    st.markdown('<div id="section-analyze"></div>', unsafe_allow_html=True)
    render_step_analysis()
elif current_tab == "📊 Results":
    st.markdown('<div id="section-results"></div>', unsafe_allow_html=True)
    render_step_results()
else:
    render_chat_companion_tab()

# Accessibility: Close main content landmark
st.markdown('</main>', unsafe_allow_html=True)

# Render sticky footer navigation
render_global_sticky_footer()

def main() -> None:
    """Entry point for the UX-optimized AutoDense interface"""
    import subprocess
    import sys
    
    # Launch with optimized settings
    subprocess.run([
        sys.executable, "-m", "streamlit", "run", __file__,
        "--server.address", "0.0.0.0",
        "--server.port", "8501",
        "--browser.gatherUsageStats", "false",
        "--theme.base", "light"
    ])

if __name__ == "__main__":
    main()
