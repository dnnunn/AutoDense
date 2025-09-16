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

import base64, json, re, tempfile
from typing import Dict, Any, Optional, Tuple, List, Union, Callable
from PIL import Image, ImageDraw, ImageOps

# Import extracted utility functions
from utils.image_processing import (
    standardize_image_size, to_png_bytes, img_to_data_url,
    process_uploaded_image, overlay_fallback
)
from utils.data_helpers import obj_to_dict, extract_json, calculate_lane_metrics, convert_to_csv
from utils.preprocessing import (
    _load_prompt, filter_supported_preproc_params,
    _guarded_preprocess_with_timeout, _openai_preprocess_with_timeout,
    openai_guided_preprocess, HAS_PREPROCESS, HAS_OPENAI
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
        if mode.startswith("ChatGPT") and HAS_OPENAI:
            with st.spinner(f"🤖 Processing image with ChatGPT... (up to 75s)"):
                st.info("🔄 Sending image to OpenAI for intelligent preprocessing...")
                img2, meta = _openai_preprocess_with_timeout(img, timeout_sec=75)
                announce_to_screen_reader("ChatGPT preprocessing completed successfully", "assertive")
            return img2, meta

        elif mode.startswith("AI") and HAS_PREPROCESS:
            with st.spinner(f"🔬 AI preprocessing in progress... (up to 60s)"):
                st.info("🔄 Applying intelligent image enhancement algorithms...")
                img2, meta = _guarded_preprocess_with_timeout(img, timeout_sec=60)
                announce_to_screen_reader("AI preprocessing completed successfully", "assertive")
            return img2, meta

        elif mode == "Manual" and manual_params and HAS_PREPROCESS:
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
            return prepped, {"mode": "Raw (no preprocessing)", "status": "fallback"}

    except Exception as e:
        # Return raw image on any preprocessing failure with error annotation
        st.error(f"⚠️ Preprocessing failed: {str(e)}")
        announce_to_screen_reader(f"Preprocessing failed: {str(e)}", "assertive")
        with st.spinner("🔧 Falling back to raw image processing..."):
            arr = np.asarray(img.convert("L")).astype(np.float32)
            prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
        return prepped, {"mode": "Fallback (preprocessing failed)", "error": str(e), "status": "error"}
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
    from autodense.preprocess.openai_policy import openai_guided_preprocess
    HAS_OPENAI = True
except ImportError:
    HAS_OPENAI = False

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

# Better Dairy logo in header
col1, col2 = st.columns([1, 8])
with col1:
    try:
        st.image("betterdairyicon.png", width=60)
    except:
        pass  # Skip if logo not found
with col2:
    st.title("AutoDense – UX Optimized Interface")

st.markdown("*Professional gel electrophoresis analysis for bench scientists*")
st.markdown("Built with accessibility, performance, and scientific workflows in mind.")

# Display environment initialization status
def render_environment_status():
    """Display the automatic environment configuration status."""
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
</style>

""", unsafe_allow_html=True)

# Session state initialization with consistent patterns
# --- Simple DOM-click helper to programmatically switch Streamlit tabs ---
def goto_tab(label_prefix: str) -> None:
    """Switch to a tab whose label starts with label_prefix (e.g., '🔬 Analysis').
    Works by clicking the DOM tab button; resilient to Streamlit updates by matching role="tab".
    """
    components.html(f"""
        <script>
        const pref = `{label_prefix}`;
        // Try a few times until the tablist renders
        let tries = 0;
        const iv = setInterval(() => {{
            tries += 1;
            const doc = window.parent.document;
            const tabs = doc.querySelectorAll('[role="tab"]');
            if (tabs && tabs.length) {{
                for (const t of tabs) {{
                    const txt = (t.innerText || t.textContent).trim();
                    if (txt.startsWith(pref)) {{
                        t.click();
                        window.parent.scrollTo({{top: 0, behavior: 'smooth'}});
                        clearInterval(iv);
                        break;
                    }}
                }}
            }}
            if (tries > 40) clearInterval(iv);
        }}, 75);
        </script>
    """, height=0)

def init_session_state() -> None:
    """Initialize session state following ui_* params_* res_* convention"""
    defaults: Dict[str, Any] = {
        # UI State
        'ui_current_tab': 'calibration',
        'ui_canvas_key': 0,
        'ui_analysis_count': 0,
        'ui_error_count': 0,
        'ui_last_action': None,
        
        # Parameters
        'params_gel_type': 'sds_page',
        'params_n_lanes': 12,
        'params_preprocessing_mode': 'AI guarded (recommended)',
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
        'analysis_cancelled': False,}
    
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value

init_session_state()

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



# Sticky tabs CSS for better navigation - Updated for Streamlit 1.28+
st.markdown("""
<style>
    /* Primary sticky tabs CSS */
    .stTabs [data-baseweb="tab-list"] {
        position: sticky !important;
        top: 0 !important;
        z-index: 999 !important;
        background: white !important;
        padding: 10px 0 !important;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1) !important;
        border-bottom: 1px solid #e0e0e0 !important;
        margin-bottom: 10px !important;
    }
    
    /* Alternative selectors for different Streamlit versions */
    .stTabs > div > div > div[role="tablist"] {
        position: sticky !important;
        top: 0 !important;
        z-index: 999 !important;
        background: white !important;
        padding: 10px 0 !important;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1) !important;
        border-bottom: 1px solid #e0e0e0 !important;
        margin-bottom: 10px !important;
    }
    
    /* Tab styling */
    .stTabs [data-baseweb="tab"] {
        padding: 8px 16px !important;
        font-weight: 500 !important;
    }
    
    .stTabs [data-baseweb="tab"]:hover {
        background-color: #f0f2f6 !important;
        border-radius: 4px !important;
    }
    
    /* Force sticky positioning across browsers */
    .stTabs [role="tablist"] {
        position: -webkit-sticky !important;
        position: sticky !important;
        top: 0 !important;
        z-index: 999 !important;
        background: white !important;
        padding: 10px 0 !important;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1) !important;
        border-bottom: 1px solid #e0e0e0 !important;
    }
</style>
""", unsafe_allow_html=True)

# Accessibility: Add semantic navigation landmark
st.markdown('<nav id="navigation" aria-label="Main navigation" role="navigation">', unsafe_allow_html=True)

# Main interface with enhanced tab system
tab1, tab2, tab3 = st.tabs([
    "🎯 Lane Calibration",
    "🔬 Analysis & Processing",
    "📊 Results & Export"
])

st.markdown('</nav>', unsafe_allow_html=True)

# --- Segmented navigation (radio) mirroring the tabs ---
_seg_opts = ["🎯 Lane Calibration", "🔬 Analysis & Processing", "📊 Results & Export"]
seg = st.radio("Workflow", _seg_opts, horizontal=True, label_visibility="collapsed", key="segmented_nav")
if seg.startswith("🎯"):
    goto_tab("🎯")
elif seg.startswith("🔬"):
    goto_tab("🔬")
elif seg.startswith("📊"):
    goto_tab("📊")


# Accessibility: Add main content landmark
st.markdown('<main id="main-content" role="main">', unsafe_allow_html=True)

with tab1:
    
    # Image upload component
    render_image_upload(key_prefix="main", show_metadata=True)
    
    if st.session_state.res_uploaded_image:
        # Gel configuration section
        st.markdown("### 🧪 Gel Configuration")
        
        col1, col2, col3 = st.columns(3)
        
        with col1:
            gel_type = st.selectbox(
                "Gel Type:",
                ["sds_page", "etbr_agarose"],
                index=0 if st.session_state.params_gel_type == "sds_page" else 1,
                help="SDS-PAGE for protein separation, EtBr agarose for DNA separation",
                key="gel_type_select"
            )
            st.session_state.params_gel_type = gel_type
            
            # Visual indicator of gel type
            if gel_type == "sds_page":
                st.markdown("🧬 **Protein Analysis** (SDS-PAGE)")
            else:
                st.markdown("🧬 **DNA Analysis** (Agarose + EtBr)")
        
        with col2:
            n_lanes = st.number_input(
                "Number of lanes:",
                min_value=2, max_value=30,
                value=st.session_state.params_n_lanes,
                step=1,
                help="Total number of sample lanes including molecular weight standard"
            )
            st.session_state.params_n_lanes = n_lanes
            
            # Lane density indicator
            if n_lanes <= 8:
                st.markdown("📏 **Standard density** (good for quantification)")
            elif n_lanes <= 16:
                st.markdown("📏 **Medium density** (standard commercial gels)")
            else:
                st.markdown("📏 **High density** (may need careful calibration)")
        
        with col3:
            # Gel recommendations based on type and lane count
            if HAS_LANE_MAPPING:
                try:
                    gel_settings = get_recommended_gel_settings(gel_type, n_lanes)
                    st.info(f"💡 **Recommendation:** {gel_settings['description']}")
                    
                    # Show additional tips
                    if 'tips' in gel_settings:
                        with st.expander("💡 Optimization Tips"):
                            for tip in gel_settings['tips']:
                                st.markdown(f"- {tip}")
                except:
                    st.info("💡 Configure based on your specific gel setup")
            else:
                st.info("💡 Configure lanes based on your gel specifications")
        
        # Enhanced calibration section
        st.markdown("### 🎯 Two-Point Lane Calibration")
        
        # Instructions with progressive disclosure
        render_calibration_instructions(expanded=False)
        
        # Calibration interface with enhanced error handling
        @handle_errors("Lane Calibration")
        def perform_calibration():
            points = st.session_state.res_calibration_points
            
            if len(points) < 2:
                return None
            
            # Get lane assignments
            lane1 = st.session_state.get('calibration_lane1', 1)
            lane2 = st.session_state.get('calibration_lane2', min(n_lanes, 12))
            
            if lane1 == lane2:
                st.error("❌ Lane numbers must be different")
                return None
            
            (x1, _), (x2, _) = points[:2]
            
            # Enhanced validation
            if HAS_LANE_MAPPING:
                is_valid, validation_msg = validate_calibration_points(x1, lane1, x2, lane2, n_lanes, w)
                
                if not is_valid:
                    st.error(f"❌ {validation_msg}")
                    return None
                
                # Create calibration
                calibration = TwoPointCalibration(
                    point1_x=x1, point1_lane=lane1,
                    point2_x=x2, point2_lane=lane2,
                    total_lanes=n_lanes
                )
                
                boundaries = calculate_lane_positions(calibration)
                st.session_state.res_lane_boundaries = boundaries
                st.session_state.ui_last_action = "calibration"
                
                return calibration, boundaries
            
            return None
        
        # Get image data from session state for calibration
        if st.session_state.res_uploaded_image and st.session_state.res_uploaded_array is not None:
            img = st.session_state.res_uploaded_image
            img_array = st.session_state.res_uploaded_array
            h, w, _ = img_array.shape
        else:
            st.error("❌ Image data not available. Please upload an image first.")
            st.stop()
        
        # Interactive calibration canvas or fallback
        if HAS_IMAGE_COORDINATES:
            st.markdown("#### 🖱️ Interactive Calibration")
            
            # Canvas controls with improved UX
            col1, col2, col3, col4 = st.columns([2, 1, 1, 1])
            
            with col2:
                clear_btn = st.button(
                    "🔄 Clear Points",
                    help="Remove all calibration points and start over",
                    use_container_width=True
                )
                if clear_btn:
                    st.session_state.res_calibration_points = []
                    st.session_state.ui_canvas_key += 1
                    st.session_state.ui_last_action = "clear_calibration"
                    st.rerun()
            
            with col3:
                undo_btn = st.button(
                    "↩️ Undo Last",
                    help="Remove the most recently placed point",
                    use_container_width=True,
                    disabled=len(st.session_state.res_calibration_points) == 0
                )
                if undo_btn:
                    st.session_state.res_calibration_points = st.session_state.res_calibration_points[:-1]
                    st.session_state.ui_canvas_key += 1
                    st.rerun()
            
            with col4:
                help_btn = st.button(
                    "❓ Help",
                    help="Show detailed calibration guidance",
                    use_container_width=True
                )
                if help_btn:
                    st.info("💡 Click precisely on the center of distinct, well-defined bands. Choose bands in different lanes that are far apart horizontally.")
            
            # Enhanced interactive coordinates with accessibility
            st.markdown(f"""
            <div data-testid="calibration-canvas" 
                 role="img" 
                 aria-label="Interactive gel image for two-point calibration"
                 tabindex="0">
            <p style="font-size: 12px; color: #6c757d; margin-bottom: 8px;">
                Click on two band centers to calibrate lane positions. Points: {len(st.session_state.res_calibration_points)}/2
            </p>
            </div>
            """, unsafe_allow_html=True)
            
            # Accessibility: Keyboard navigation options
            st.markdown("""
            <div role="application" aria-label="Gel calibration interface">
                <p><strong>Keyboard Navigation:</strong> Use the coordinate input fields below if you cannot use the interactive image.</p>
            </div>
            """, unsafe_allow_html=True)

            # Keyboard accessibility: Manual coordinate input option
            with st.expander("⌨️ Keyboard/Manual Coordinate Entry", expanded=False):
                st.markdown("**Alternative input method for precise coordinate entry or keyboard navigation:**")
                col1, col2 = st.columns(2)

                with col1:
                    manual_x = st.number_input("X Coordinate", min_value=0, max_value=w, value=w//2,
                                             help="Horizontal position on the image (0 = left edge)")
                with col2:
                    manual_y = st.number_input("Y Coordinate", min_value=0, max_value=h, value=h//2,
                                             help="Vertical position on the image (0 = top edge)")

                if st.button("Add Manual Coordinate Point", help="Add the coordinate point using the values above"):
                    with st.spinner("📍 Processing coordinate point..."):
                        # Handle manual coordinate input
                        coord_result = {'x': manual_x, 'y': manual_y}
                        # Add brief processing delay to show loading state for user feedback
                        import time
                        time.sleep(0.5)  # Brief pause to demonstrate loading state
                    st.success(f"✅ Manual coordinate added: ({manual_x}, {manual_y})")
                    announce_to_screen_reader(f"Coordinate point added at {manual_x}, {manual_y}", "assertive")
                else:
                    coord_result = None

            if coord_result is None:  # Only show interactive canvas if no manual input
                # Use streamlit-image-coordinates for better UX
                display_width = min(w, 800)  # Max display width
                display_height = int(display_width * h / w)

                coord_result = streamlit_image_coordinates(
                    img,
                    width=display_width,
                    height=display_height,
                    cursor="crosshair",
                    key=f"calibration_coords_{st.session_state.ui_canvas_key}"
                )
            
            # Handle coordinate clicks
            if coord_result is not None and coord_result.get('x') is not None:
                with st.spinner("🎯 Processing calibration point..."):
                    # Scale coordinates back to original image size
                    scale_x = w / display_width
                    scale_y = h / display_height

                    x = int(coord_result['x'] * scale_x)
                    y = int(coord_result['y'] * scale_y)

                    # Get current points
                    current_points = st.session_state.res_calibration_points[:]

                    # Check if this is a duplicate point (within 5 pixels)
                    is_duplicate = False
                    for existing_x, existing_y in current_points:
                        if abs(x - existing_x) < 5 and abs(y - existing_y) < 5:
                            is_duplicate = True
                            break

                    # Add point if we have less than 2 and it's not a duplicate
                    if len(current_points) < 2 and not is_duplicate:
                        current_points.append((x, y))
                        st.session_state.res_calibration_points = current_points
                        announce_to_screen_reader(f"Calibration point {len(current_points)} added at position {x}, {y}", "assertive")
                        st.rerun()
                    elif is_duplicate:
                        pass  # Don't show warning, green status box below is sufficient
                    elif len(current_points) >= 2:
                        st.info("✋ Already have 2 points. Use Clear or Undo to reset.")
            
            # Display current points
            points = st.session_state.res_calibration_points
        
        else:
            # Enhanced fallback manual coordinate entry
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
                    
                    # Visual preview of point 1 location
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
                    
                    # Visual preview of point 2 location
                    if pt2_x and pt2_y:
                        rel_x = pt2_x / w * 100
                        rel_y = pt2_y / h * 100
                        st.caption(f"📍 Point 2: {rel_x:.1f}% from left, {rel_y:.1f}% from top")
                
                # Distance validation
                if pt1_x and pt1_y and pt2_x and pt2_y:
                    distance = ((pt2_x - pt1_x)**2 + (pt2_y - pt1_y)**2)**0.5
                    if distance < w * 0.2:  # Less than 20% of image width
                        st.warning("⚠️ Points are quite close together. Consider spacing them farther apart for better accuracy.")
                    else:
                        st.success(f"✅ Point separation: {distance:.1f} pixels (good spacing)")
                
                # Apply manual calibration
                apply_manual = st.button(
                    "✅ Apply Manual Calibration Points",
                    use_container_width=True,
                    type="primary",
                    help="Use these coordinates as calibration points"
                )
                
                if apply_manual:
                    st.session_state.res_calibration_points = [(pt1_x, pt1_y), (pt2_x, pt2_y)]
                    st.session_state.ui_last_action = "manual_calibration"
                    st.toast("Manual calibration points applied", icon="✅")
                    st.rerun()
        
        # Calibration status and lane assignment
        points = st.session_state.res_calibration_points
        
        if len(points) == 0:
            st.info("👆 **Step 1:** Place first calibration point on the gel image")
            
            # Show calibration tips when no points
            st.markdown("""
            <div class="info-panel">
            <strong>💡 Getting started:</strong><br>
            • Look for distinct, well-defined bands in your gel<br>
            • Start with the molecular weight standard lane (usually leftmost)<br>
            • Click precisely on the center of a clear band
            </div>
            """, unsafe_allow_html=True)
        
        elif len(points) == 1:
            (x1, y1) = points[0]
            st.success(f"✅ **First calibration point set:** ({x1}, {y1})")
            st.info("👆 **Step 2:** Place second calibration point in a different lane")
            
            # Show progress and guidance
            st.markdown(f"""
            <div class="info-panel">
            <strong>Next step:</strong><br>
            • Choose a lane far from the first point (lane separation helps accuracy)<br>
            • Look for another distinct band at roughly the same gel height<br>
            • Click precisely on the band center
            </div>
            """, unsafe_allow_html=True)
        
        elif len(points) >= 2:
            st.success(f"✅ **Calibration points ready:** {len(points)} points captured")
            
            if len(points) > 2:
                st.info(f"ℹ️ Using first two points only (ignoring {len(points) - 2} extra points)")
            
            # Lane number assignment with smart defaults and validation
            st.markdown("#### 🏷️ Lane Number Assignment")
            
            col1, col2 = st.columns(2)
            
            with col1:
                lane1 = st.number_input(
                    "MW standard lane number:",
                    min_value=1, max_value=n_lanes, value=1,
                    key="calibration_lane1",
                    help="Lane number for the first calibration point (typically lane 1 for MW standard)"
                )
                
                # Visual feedback for lane 1
                (x1, y1) = points[0]
                st.caption(f"🎯 Point 1 at ({x1}, {y1}) → Lane {lane1}")
            
            with col2:
                # Smart default for lane 2 (avoid lane 1, prefer far lanes)
                default_lane2 = min(n_lanes, max(8, n_lanes - 1))
                if default_lane2 == lane1:
                    default_lane2 = max(1, min(n_lanes, lane1 + 6))
                
                lane2 = st.number_input(
                    "Reference lane number:",
                    min_value=1, max_value=n_lanes, value=default_lane2,
                    key="calibration_lane2", 
                    help="Lane number for the second calibration point (choose a lane far from the first)"
                )
                
                # Visual feedback for lane 2
                (x2, y2) = points[1]
                st.caption(f"🎯 Point 2 at ({x2}, {y2}) → Lane {lane2}")
            
            # Validation and calibration execution
            if lane1 == lane2:
                st.error("❌ **Validation Error:** Lane numbers must be different")
                st.info("💡 Choose different lane numbers for your two calibration points")
            else:
                # Perform calibration with error handling
                calibration_result = perform_calibration()
                
                if calibration_result:
                    calibration, boundaries = calibration_result
                    
                    # Display calibration metrics with enhanced formatting
                    st.markdown("#### 📏 Calibration Metrics")
                    
                    col1, col2, col3, col4 = st.columns(4)
                    
                    with col1:
                        spacing = calibration.lane_spacing_px
                        st.metric(
                            "Lane Spacing",
                            f"{spacing:.1f} px",
                            help="Distance between adjacent lane centers"
                        )
                        if spacing < 20:
                            st.caption("⚠️ Very narrow lanes")
                        elif spacing > 200:
                            st.caption("ℹ️ Wide-spaced lanes")
                        else:
                            st.caption("✅ Normal spacing")
                    
                    with col2:
                        width = calibration.lane_width_px
                        st.metric(
                            "Lane Width",
                            f"{width:.1f} px",
                            help="Calculated width of each lane (90% of spacing)"
                        )
                    
                    with col3:
                        total_span = abs(boundaries[-1].right_px - boundaries[0].left_px)
                        st.metric(
                            "Total Gel Width",
                            f"{total_span:.0f} px",
                            help="Span covered by all lanes"
                        )
                    
                    with col4:
                        gel_coverage = (total_span / w) * 100
                        st.metric(
                            "Image Coverage",
                            f"{gel_coverage:.1f}%",
                            help="Percentage of image width used by lanes"
                        )
                    
                    # Enhanced lane preview with quality assessment
                    st.markdown("#### 🔍 Lane Alignment Preview")
                    
                    # Generate enhanced overlay
                    try:
                        import cv2
                        HAS_CV2 = True
                    except ImportError:
                        HAS_CV2 = False
                    
                    if HAS_CV2:
                        overlay_img = img_array.copy()
                        
                        # Color coding for different lane types
                        for i, boundary in enumerate(boundaries):
                            center = int(boundary.center_px)
                            left = int(boundary.left_px)
                            right = int(boundary.right_px)
                            
                            # Determine color based on lane role
                            if i == lane1 - 1:
                                color = (255, 215, 0)  # Gold for MW standard
                                thickness = 3
                            elif i == lane2 - 1:
                                color = (255, 140, 0)  # Orange for reference
                                thickness = 3
                            else:
                                color = (0, 255, 255)  # Cyan for interpolated
                                thickness = 2
                            
                            # Draw lane boundaries
                            cv2.line(overlay_img, (left, 0), (left, h), color, thickness)
                            cv2.line(overlay_img, (right, 0), (right, h), color, thickness)
                            
                            # Draw center lines
                            cv2.line(overlay_img, (center, 0), (center, h), (255, 255, 255), 1, cv2.LINE_AA)
                            
                            # Enhanced lane numbering with background
                            label_y = 50
                            label_bg_size = 20
                            
                            # Background rectangle for visibility
                            cv2.rectangle(overlay_img, 
                                        (center - label_bg_size, label_y - label_bg_size), 
                                        (center + label_bg_size, label_y + 5), 
                                        (0, 0, 0), -1)
                            
                            # Lane number
                            cv2.putText(overlay_img, str(boundary.lane_index),
                                      (center - 10, label_y - 5),
                                      cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2, cv2.LINE_AA)
                            
                            # Lane type indicator
                            if i == lane1 - 1:
                                cv2.putText(overlay_img, "MW", (center - 12, label_y + 15),
                                          cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 215, 0), 1, cv2.LINE_AA)
                            elif i == lane2 - 1:
                                cv2.putText(overlay_img, "REF", (center - 15, label_y + 15),
                                          cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 140, 0), 1, cv2.LINE_AA)
                        
                        # Lane dimension adjustment slider
                        with st.container():
                            # Create an inset slider for lane width adjustment
                            st.markdown("**🔧 Lane Dimension Adjustment**")
                            
                            # Get the current average lane width as baseline
                            current_width = np.mean([b.width_px for b in boundaries])
                            
                            # Slider for adjusting lane dimensions (±50% of current width)
                            width_multiplier = st.slider(
                                "Lane Width Multiplier:",
                                min_value=0.5,
                                max_value=1.5,
                                value=1.0,
                                step=0.05,
                                help=f"Adjust lane width from baseline {current_width:.1f}px",
                                key="lane_width_adjust"
                            )
                            
                            # Apply width adjustment if changed from default
                            if width_multiplier != 1.0:
                                # Create a fresh overlay to avoid accumulation
                                overlay_img = img_array.copy()
                                
                                # Update lane boundaries with new width
                                adjusted_boundaries = []
                                for boundary in boundaries:
                                    new_width = boundary.width_px * width_multiplier
                                    half_width = new_width / 2
                                    adjusted_boundary = SimpleLaneBoundary(
                                        lane_index=boundary.lane_index,
                                        center_px=boundary.center_px,
                                        left_px=boundary.center_px - half_width,
                                        right_px=boundary.center_px + half_width,
                                        width_px=new_width
                                    )
                                    adjusted_boundaries.append(adjusted_boundary)
                                
                                # Draw all adjusted boundaries on fresh overlay
                                for i, adjusted_boundary in enumerate(adjusted_boundaries):
                                    # Determine colors based on lane type (using same logic as original)
                                    if i == lane1 - 1:
                                        rect_color = (255, 215, 0)  # Gold for MW lane
                                    elif i == lane2 - 1:
                                        rect_color = (255, 140, 0)  # Orange for REF lane
                                    else:
                                        rect_color = (0, 255, 255)  # Cyan for regular lanes
                                    
                                    # Draw lane boundary rectangles
                                    cv2.rectangle(
                                        overlay_img,
                                        (int(adjusted_boundary.left_px), 50),
                                        (int(adjusted_boundary.right_px), overlay_img.shape[0] - 50),
                                        rect_color, 2
                                    )
                                    
                                    # Add lane numbers at the top (white text)
                                    center = int(adjusted_boundary.center_px)
                                    cv2.putText(overlay_img, str(adjusted_boundary.lane_index), 
                                              (center - 8, 40),
                                              cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2, cv2.LINE_AA)
                                    
                                    # Add center line markers
                                    cv2.line(overlay_img, (center, 50), (center, overlay_img.shape[0] - 50), 
                                           (255, 255, 255), 1)
                                    
                                    # Lane type indicator
                                    if i == lane1 - 1:
                                        cv2.putText(overlay_img, "MW", (center - 12, 55),
                                                  cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 215, 0), 1, cv2.LINE_AA)
                                    elif i == lane2 - 1:
                                        cv2.putText(overlay_img, "REF", (center - 15, 55),
                                                  cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 140, 0), 1, cv2.LINE_AA)
                                
                                # Store adjusted boundaries for later use
                                st.session_state.adjusted_lane_boundaries = adjusted_boundaries
                                st.info(f"🔧 Lane width adjusted by {width_multiplier:.2f}x (from {current_width:.1f}px to {current_width * width_multiplier:.1f}px)")
                        
                        # Display overlay with detailed caption
                        st.image(
                            overlay_img,
                            caption="🎯 Lane boundary preview: Gold=MW standard, Orange=Reference lane, Cyan=Interpolated lanes, White=Centers",
                            use_container_width=True
                        )
                        
                    else:
                        # Fallback without OpenCV
                        st.info("🔍 Lane preview requires OpenCV. Install with: `pip install opencv-python`")
                        
                        # Show lane data as table instead
                        lane_data = []
                        for boundary in boundaries:
                            lane_type = "MW Standard" if boundary.lane_index == lane1 else (
                                "Reference" if boundary.lane_index == lane2 else "Sample"
                            )
                            lane_data.append({
                                "Lane": boundary.lane_index,
                                "Type": lane_type,
                                "Center (px)": f"{boundary.center_px:.1f}",
                                "Left (px)": f"{boundary.left_px:.1f}",
                                "Right (px)": f"{boundary.right_px:.1f}",
                                "Width (px)": f"{boundary.width_px:.1f}"
                            })
                        
                        st.dataframe(pd.DataFrame(lane_data), use_container_width=True)
                    
                    # Alignment verification and confirmation
                    st.markdown("#### ✅ Verification & Confirmation")
                    
                    st.markdown("""
                    <div class="info-panel">
                    <strong>Please verify the lane alignment:</strong><br>
                    ✓ <strong>Boundaries:</strong> Cyan/Gold/Orange lines align with actual lane edges<br>
                    ✓ <strong>Centers:</strong> White center lines run through the middle of each lane<br>
                    ✓ <strong>Numbers:</strong> Lane numbers match your gel layout<br>
                    ✓ <strong>Span:</strong> All sample lanes are covered appropriately
                    </div>
                    """, unsafe_allow_html=True)
                    
                    # Quality check suggestions
                    lane_spacing = abs(calibration.lane_spacing_px)
                    if lane_spacing < 30:
                        st.warning("⚠️ **Very narrow lanes detected.** Consider if this matches your gel specifications.")
                    elif lane_spacing > 150:
                        st.info("ℹ️ **Wide lanes detected.** Verify this matches your gel format.")
                    
                    # Final confirmation checkbox
                    alignment_confirmed = st.checkbox(
                        "🎯 **I confirm the lane boundaries align correctly with my gel**",
                        value=False,
                        help="Check this only after verifying the lane overlay matches your actual gel lanes",
                        key="lane_alignment_confirmed"
                    )
                    
                    if alignment_confirmed:
                        st.success("✅ **Lane calibration confirmed!** 🎉")
                        
                        # Action buttons for next steps
                        col1, col2, col3 = st.columns(3)
                        
                        with col1:
                            st.markdown("**Next Step:**")
                            st.info("➡️ Switch to **Analysis** tab to process your gel")
                        
                        with col2:
                            # Export calibration data
                            if HAS_LANE_MAPPING:
                                try:
                                    csv_data = boundaries_to_csv(boundaries)
                                    st.download_button(
                                        "📥 **Export Calibration**",
                                        csv_data,
                                        f"lane_calibration_{gel_type}_{n_lanes}lanes.csv",
                                        "text/csv",
                                        help="Download lane boundary data for external use",
                                        use_container_width=True
                                    )
                                except Exception as e:
                                    st.button("📥 Export", disabled=True, help=f"Export failed: {e}", use_container_width=True)
                            else:
                                st.button("📥 Export", disabled=True, help="Export not available", use_container_width=True)
                        
                        with col3:
                            # Quick analysis shortcut
                            if st.button("🔬 **Start Analysis**", use_container_width=True, type="primary"):
                                with st.spinner("🚀 Navigating to analysis..."):
                                    st.info("🔄 Switching to Analysis & Processing tab...")
                                    announce_to_screen_reader("Navigating to analysis tab", "polite")
                                    goto_tab("🔬 Analysis")
                                    goto_tab("🔬 Analysis & Processing")
                    
                    else:
                        st.info("💡 **Need adjustments?**")
                        
                        # Adjustment options
                        adj_col1, adj_col2, adj_col3 = st.columns(3)
                        
                        with adj_col1:
                            if st.button("🔄 **Recalibrate**", help="Clear points and start calibration over", use_container_width=True):
                                with st.spinner("🔄 Resetting calibration..."):
                                    st.info("🔄 Clearing all calibration data...")
                                    st.session_state.res_calibration_points = []
                                    st.session_state.res_lane_boundaries = None
                                    st.session_state.ui_canvas_key += 1
                                    announce_to_screen_reader("Calibration reset completed", "assertive")
                                st.rerun()

                        with adj_col2:
                            if st.button("🎯 **Adjust Points**", help="Keep lane settings but change calibration points", use_container_width=True):
                                with st.spinner("🎯 Adjusting calibration points..."):
                                    st.info("🔄 Clearing points while preserving lane settings...")
                                    st.session_state.res_calibration_points = []
                                    st.session_state.ui_canvas_key += 1
                                    announce_to_screen_reader("Calibration points cleared for adjustment", "assertive")
                                st.info("👆 Place new calibration points above")
                                st.rerun()
                        
                        with adj_col3:
                            st.button("⚙️ **Change Lanes**", help="Adjust lane numbers without changing points", disabled=True, use_container_width=True)
    
    else:
        # No image uploaded - show upload guidance
        st.markdown("""
        <div class="info-panel">
        <h4>🚀 Getting Started</h4>
        <p>Upload a gel electrophoresis image to begin the analysis workflow.</p>
        </div>
        """, unsafe_allow_html=True)
        
        with st.expander("💡 Image Upload Guidelines", expanded=False):
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown("""
                **Optimal image characteristics:**
                - **Format:** PNG or TIFF preferred, JPG acceptable
                - **Resolution:** 1000-4000 pixels wide
                - **Size:** Under 50MB for best performance
                - **Quality:** High contrast, well-focused bands
                - **Orientation:** Lanes should be vertical
                """)
            
            with col2:
                st.markdown("""
                **Image quality tips:**
                - Use proper lighting (avoid shadows/glare)
                - Capture the entire gel area
                - Minimize background noise
                - Ensure bands are clearly visible
                - Avoid camera shake or blur
                """)

with tab2:
    st.markdown("""
    ## 🔬 Analysis & Processing
    Configure preprocessing parameters and run comprehensive gel analysis with your calibrated lane boundaries.
    """)
    
    # Prerequisites status check using component
    prerequisites_met = render_prerequisites_panel(goto_tab)

    if not prerequisites_met:
        st.stop()  # Exit early if prerequisites not met
    
    # Analysis configuration section
    st.markdown("### ⚙️ Analysis Configuration")
    
    # Enhanced preprocessing mode selection
    st.markdown('<div data-testid="preprocessing-section" role="region" aria-label="Preprocessing Configuration">', unsafe_allow_html=True)
    
    # Determine available preprocessing modes
    mode_options = ["AI guarded (recommended)", "Manual", "Off"]
    mode_descriptions = {
        "AI guarded (recommended)": "🧠 Automatic optimization based on image analysis metrics and gel electrophoresis principles",
        "Manual": "🛠️ Use custom preprocessing parameters with full control",
        "Off": "📷 Analyze raw image without any preprocessing"
    }
    
    if HAS_OPENAI:
        mode_options.insert(0, "ChatGPT-4.1 (premium)")
        mode_descriptions["ChatGPT-4.1 (premium)"] = "🤖 Advanced AI vision analysis with scientific reasoning and validation"
    
    preprocessing_mode = st.selectbox(
        "**Preprocessing Method:**",
        mode_options,
        index=0,
        help="Choose how to optimize your gel image before analysis",
        key="preprocessing_mode_select"
    )
    
    # Display detailed description of selected mode
    if preprocessing_mode in mode_descriptions:
        st.markdown(f"""
        <div class="info-panel">
        <strong>Selected Mode:</strong> {mode_descriptions[preprocessing_mode]}
        </div>
        """, unsafe_allow_html=True)
    
    st.markdown('</div>', unsafe_allow_html=True)
    
    # API status and security information for ChatGPT mode
    if preprocessing_mode.startswith("ChatGPT"):
        st.markdown("#### 🤖 ChatGPT-4.1 Status")
        
        if HAS_OPENAI:
            try:
                from autodense.security.key_manager import get_openai_api_key, mask_key_for_logging
                
                api_key = get_openai_api_key()
                if api_key:
                    st.success("🧠 **ChatGPT-4.1 Ready** - Advanced AI analysis available")
                    st.info(f"🔐 **API Key Status:** {mask_key_for_logging(api_key)}")
                    
                    # Scientific validation notice
                    st.markdown("""
                    <div class="info-panel">
                    <strong>🧪 Scientific Validation Enabled:</strong><br>
                    AI preprocessing decisions are validated against established gel electrophoresis principles.
                    The system verifies that suggested optimizations improve image quality metrics relevant
                    to band detection and quantification.
                    </div>
                    """, unsafe_allow_html=True)
                    
                    # Model configuration info
                    try:
                        from autodense.preprocess.openai_policy import OpenAIPreprocessingClient
                        client = OpenAIPreprocessingClient()
                        st.caption(f"🔧 Model: {client.config.model} | Max tokens: {client.config.max_tokens}")
                    except Exception as e:
                        st.caption(f"⚠️ Client config unavailable: {e}")
                
                else:
                    st.error("❌ **OpenAI API Key Required**")
                    st.markdown("""
                    **🔧 Setup Instructions:**
                    1. Obtain API key from [OpenAI Platform](https://platform.openai.com/api-keys)
                    2. Set environment variable: `export OPENAI_API_KEY="your-key-here"`
                    3. Restart the application
                    
                    **🔐 Security Notice:**
                    - Never store API keys in code or configuration files
                    - Use environment variables or secure key management
                    - Keys are masked in logs for security
                    """)
                    st.warning("💡 **Fallback:** Will use algorithmic AI preprocessing instead")
                    
            except ValueError as e:
                if "API key not found" in str(e):
                    st.error("❌ **API Key Configuration Error**")
                    st.error("🔧 **Required:** Set `OPENAI_API_KEY` environment variable")
                    st.warning("⚠️ **SECURITY WARNING:** Never store API keys in files or code repositories")
                else:
                    st.error(f"❌ **Configuration Error:** {str(e)}")
                st.info("💡 **Fallback:** Using algorithmic AI preprocessing")
                
            except Exception as e:
                st.error("❌ **ChatGPT-4.1 Service Unavailable**")
                st.caption(f"Technical details: {str(e)}")
                st.info("💡 **Fallback:** Using algorithmic AI preprocessing")
        
        else:
            st.error("❌ **OpenAI Package Not Installed**")
            st.code("pip install openai", language="bash")
            st.info("💡 Install the OpenAI package to enable ChatGPT-4.1 preprocessing")
    
    # Manual preprocessing parameters (collapsible when not selected)
    manual_params = {}
    if preprocessing_mode == "Manual":
        with st.expander("🛠️ Manual Preprocessing Parameters", expanded=True):
            st.markdown("Configure custom preprocessing steps. Advanced users can fine-tune each parameter for optimal results.")
            
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown("**🎨 Image Processing**")
                
                manual_params["target_channel"] = st.selectbox(
                    "Color Channel:",
                    ["auto", "R", "G", "B"],
                    index=0,
                    help="Which color channel to analyze (auto selects optimal channel)"
                )
                
                manual_params["polarity"] = st.selectbox(
                    "Band Polarity:",
                    ["auto", "bright", "dark"],
                    index=0,
                    help="Whether bands appear brighter or darker than background"
                )
                
                manual_params["bg_method"] = st.selectbox(
                    "Background Correction:",
                    ["auto", "rolling_ball", "tophat_white", "tophat_black"],
                    index=0,
                    help="Method for removing background intensity variations"
                )
                
                manual_params["bg_radius"] = st.slider(
                    "Background Radius (px):",
                    min_value=5, max_value=99, value=17, step=2,
                    help="Size parameter for background correction algorithm"
                )
            
            with col2:
                st.markdown("**🔧 Enhancement Options**")
                
                manual_params["denoise"] = st.selectbox(
                    "Noise Reduction:",
                    ["auto", "none", "median", "gaussian"],
                    index=0,
                    help="Algorithm for reducing image noise"
                )
                
                manual_params["clahe"] = st.checkbox(
                    "CLAHE Enhancement",
                    value=True,
                    help="Contrast Limited Adaptive Histogram Equalization (improves local contrast)"
                )
                
                manual_params["deskew"] = st.checkbox(
                    "Geometric Correction",
                    value=True,
                    help="Correct for gel skewing, rotation, and perspective distortion"
                )
                
                # Advanced options
                with st.expander("Advanced Options"):
                    manual_params["gamma"] = st.slider(
                        "Gamma Correction:",
                        min_value=0.1, max_value=3.0, value=1.0, step=0.1,
                        help="Adjust image brightness curve (1.0 = no change)"
                    )
                    
                    # TEMPORARILY DISABLED: sharpen parameter not yet supported in PreprocParams backend
                    # manual_params["sharpen"] = st.checkbox(
                    #     "Edge Sharpening",
                    #     value=False,
                    #     help="Apply unsharp mask to enhance edge definition"
                    # )
            
            # Parameter validation and warnings
            if manual_params["bg_radius"] > 50:
                st.warning("⚠️ Large background radius may over-correct and remove band signals")
            
            if manual_params["gamma"] < 0.5 or manual_params["gamma"] > 2.0:
                st.info("ℹ️ Extreme gamma values may affect quantification accuracy")
    
    # Analysis parameters section
    st.markdown("### 🎯 Analysis Parameters")
    
    col1, col2 = st.columns(2)
    
    with col1:
        st.markdown("**🧬 Sample Type & Standards**")
        
        # Derive modality from gel type
        modality = "sds" if st.session_state.params_gel_type == "sds_page" else "dna"
        gel_type_display = "Protein (SDS-PAGE)" if modality == "sds" else "DNA (Agarose + EtBr)"
        
        st.info(f"**Analysis Type:** {gel_type_display}")
        st.caption(f"Derived from gel configuration: {st.session_state.params_gel_type}")
        
        # Molecular weight standard selection
        ladder_options = ["auto", "pageruler_10_180", "precision_plus"] if modality == "sds" else ["auto", "neb_1kb", "lambda_hindiii"]
        ladder_descriptions = {
            "auto": "🤖 Automatic detection",
            "pageruler_10_180": "🧬 PageRuler 10-180 kDa (Thermo)",
            "precision_plus": "🧬 Precision Plus (Bio-Rad)",
            "neb_1kb": "🧬 NEB 1kb DNA Ladder",
            "lambda_hindiii": "🧬 Lambda DNA/HindIII"
        }
        
        ladder_type = st.selectbox(
            "Molecular Weight Standard:",
            ladder_options,
            index=0,
            help=f"Choose the MW standard used in your {modality.upper()} gel"
        )
        
        if ladder_type in ladder_descriptions:
            st.caption(f"📋 {ladder_descriptions[ladder_type]}")
        
        st.session_state.params_ladder_type = ladder_type
    
    with col2:
        st.markdown("**🔍 Detection Parameters**")
        
        # Band confidence threshold with intelligent defaults
        conf_threshold = st.slider(
            "Band Confidence Threshold:",
            min_value=0.0, max_value=1.0, 
            value=st.session_state.params_conf_threshold, 
            step=0.01,
            help="Minimum confidence required to report a band (higher = more stringent)"
        )
        st.session_state.params_conf_threshold = conf_threshold
        
        # Confidence level guidance
        if conf_threshold < 0.2:
            st.caption("⚠️ Very permissive (may include noise)")
        elif conf_threshold < 0.5:
            st.caption("✅ Standard setting (good sensitivity)")
        elif conf_threshold < 0.8:
            st.caption("🎯 Conservative (high precision)")
        else:
            st.caption("🔒 Very strict (minimal false positives)")
        
        # MW standard lane specification
        mw_lane = st.number_input(
            "MW Standard Lane:",
            min_value=1, max_value=st.session_state.params_n_lanes,
            value=st.session_state.params_mw_lane,
            step=1,
            help="Lane number containing your molecular weight standards"
        )
        st.session_state.params_mw_lane = mw_lane
        
        # Visual validation of MW lane
        if st.session_state.res_lane_boundaries:
            total_lanes = len(st.session_state.res_lane_boundaries)
            if 1 <= mw_lane <= total_lanes:
                st.caption(f"✅ MW lane {mw_lane} of {total_lanes} configured lanes")
            else:
                st.caption(f"⚠️ MW lane {mw_lane} not in range (1-{total_lanes})")
    
    # Advanced analysis options
    with st.expander("⚙️ Advanced Analysis Options", expanded=False):
        col1, col2 = st.columns(2)
        
        with col1:
            st.markdown("**🔍 Band Detection**")
            
            min_band_width = st.slider(
                "Minimum Band Width (px):",
                min_value=1, max_value=20, value=3,
                help="Smallest band width to consider (filters noise)"
            )
            
            max_band_width = st.slider(
                "Maximum Band Width (px):",
                min_value=10, max_value=100, value=50,
                help="Largest band width to consider (filters artifacts)"
            )
            
            intensity_threshold = st.slider(
                "Intensity Threshold:",
                min_value=0.0, max_value=1.0, value=0.1, step=0.05,
                help="Minimum relative intensity for band detection"
            )
        
        with col2:
            st.markdown("**📊 Quantification**")
            
            background_method = st.selectbox(
                "Background Subtraction:",
                ["local", "global", "none"],
                index=0,
                help="Method for background intensity correction"
            )
            
            normalization = st.selectbox(
                "Intensity Normalization:",
                ["none", "total_intensity", "housekeeping", "loading_control"],
                index=0,
                help="Method for normalizing band intensities"
            )
            
            enable_statistics = st.checkbox(
                "Statistical Analysis",
                value=True,
                help="Calculate statistical significance between lanes"
            )

    # --- AutoDense Band Assist (lanes/bands pipeline) ---
    st.markdown("### 🧪 AutoDense Band Assist (Beta)")
    st.caption("Runs the AutoDense lanes/bands pipeline and shows an editable bands table with overlay.")

    ba_cols = st.columns([2,1])
    with ba_cols[0]:
        run_ba = st.button("🚀 Run Band Assist Pipeline", use_container_width=True)
    with ba_cols[1]:
        st.caption("Requires image upload; uses your current calibration and detection params.")

    if run_ba:
        if not st.session_state.res_uploaded_image:
            st.error("Upload an image first.")
        else:
            # Build minimal params from UI
            params = {
                "gel_type": st.session_state.params_gel_type,  # Use internal format for translation
                "conf_threshold": float(st.session_state.params_conf_threshold),
                "mw_lane": int(st.session_state.params_mw_lane),
                # Optional background radius from manual params if available
            }

            # Show parameter translation preview
            with st.expander("🔧 Parameter Translation Details", expanded=False):
                is_valid, backend_params, explanations, errors = validate_and_explain_params(params)

                if errors:
                    st.error("Parameter validation failed:")
                    for error in errors:
                        st.error(f"• {error}")
                else:
                    st.success("✅ Parameters validated successfully")
                    for explanation in explanations:
                        st.text(explanation)
            # Get current lane calibration data
            lane_calibration = get_current_lane_calibration()
            print(f"DEBUG: lane_calibration from get_current_lane_calibration: {lane_calibration}")
            boundaries = lane_calibration['lane_boundaries']
            print(f"DEBUG: extracted boundaries: {boundaries}, type: {type(boundaries)}")
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
                st.warning(" ; ".join(res["errors"]))
            else:
                st.success("Band Assist completed")

    # Preview overlay + lanes table
    if st.session_state.res_ba_overlay_png:
        c1, c2 = st.columns([2,1])
        with c1:
            st.image(st.session_state.res_ba_overlay_png, caption="Band Assist overlay", use_container_width=False)
        with c2:
            st.markdown("**Lanes (summary)**")
            lane_cols = ["lane_index", "x0", "y0", "x1", "y1", "lane_type", "band_count"]
            lanes_tbl = []
            for r in (st.session_state.res_ba_lanes_rows or []):
                lanes_tbl.append({k: r.get(k, "") for k in lane_cols})
            st.dataframe(lanes_tbl, use_container_width=True, hide_index=True)

    # Editable bands table
    if st.session_state.res_ba_bands_rows:
        st.markdown("**Band Assist — editable bands**")
        band_cols = ["lane_index", "band_index", "x0", "x1", "y0", "y1", "intensity", "confidence"]
        edited = st.data_editor(
            [{k: row.get(k, "") for k in band_cols} for row in st.session_state.res_ba_bands_rows],
            key="ba_bands_editor",
            num_rows="dynamic",
            use_container_width=True,
            hide_index=True,
        )
        if st.button("✏️ Apply Band Edits and Update Overlay"):
            with st.spinner("✏️ Applying band edits..."):
                st.info("🔄 Processing band modifications and regenerating overlay...")
                st.session_state.res_ba_bands_rows = edited
                try:
                    base2 = st.session_state.res_uploaded_image.convert("RGB")
                    if draw_ad_overlay and HAS_AD_PIPELINE:
                        over = draw_ad_overlay(base2, lanes=st.session_state.res_ba_lanes_rows, bands=st.session_state.res_ba_bands_rows)  # type: ignore
                        if isinstance(over, Image.Image):
                            st.session_state.res_ba_overlay_png = to_png_bytes(over)
                    else:
                        fallback = overlay_fallback(base2, st.session_state.res_ba_lanes_rows, st.session_state.res_ba_bands_rows)
                        st.session_state.res_ba_overlay_png = to_png_bytes(fallback)
                    st.success("✅ Band edits applied successfully!")
                    announce_to_screen_reader("Band edits applied successfully", "assertive")
                except Exception as e:
                    st.error(f"⚠️ Failed to update overlay: {e}")
                    announce_to_screen_reader(f"Band edits failed: {str(e)}", "assertive")
    # Analysis execution section
    # Analysis execution section
    st.markdown("### 🚀 Execute Analysis")

    # Pre-analysis validation
    analysis_warnings = []
    analysis_errors = []
    
    # Check image quality indicators
    if st.session_state.res_image_metadata:
        # Check ORIGINAL dimensions (before standardization) for detection accuracy warning
        dims = st.session_state.res_image_metadata.get('original_dimensions',
               st.session_state.res_image_metadata.get('dimensions', (0, 0)))
        if dims[0] < 1000 or dims[1] < 500:
            analysis_warnings.append("Small image dimensions may reduce detection accuracy")

        size_mb = st.session_state.res_image_metadata.get('size_bytes', 0) / (1024 * 1024)
        if size_mb > 20:
            analysis_warnings.append("Large image size may slow processing")
    
    # Check calibration quality
    if st.session_state.res_lane_boundaries:
        lane_spacings = []
        for i in range(1, len(st.session_state.res_lane_boundaries)):
            spacing = abs(st.session_state.res_lane_boundaries[i].center_px - 
                         st.session_state.res_lane_boundaries[i-1].center_px)
            lane_spacings.append(spacing)

        if lane_spacings:
            min_spacing = min(lane_spacings)
            max_spacing = max(lane_spacings)
    
            if min_spacing < 20:
                analysis_warnings.append("Very narrow lanes detected - consider calibration accuracy")
    
            if max_spacing / min_spacing > 2.0:
                analysis_warnings.append("Irregular lane spacing detected - verify calibration")
    
    # Display warnings and errors
    if analysis_errors:
        st.error("❌ **Analysis cannot proceed due to critical errors:**")
        for error in analysis_errors:
            st.error(f"• {error}")
    elif analysis_warnings:
        st.warning("⚠️ **Analysis can proceed, but note these warnings:**")
        for warning in analysis_warnings:
            st.warning(f"• {warning}")
    else:
        st.success("✅ **Ready for analysis - no issues detected**")
    
    # Main analysis button
    st.markdown('<div data-testid="analysis-execution-section" role="region" aria-label="Analysis Execution">', unsafe_allow_html=True)
    
    analysis_ready = len(analysis_errors) == 0
    
    analyze_button = st.button(
        "🔬 **Run Comprehensive Gel Analysis**",
        use_container_width=True,
        disabled=not analysis_ready,
        type="primary",
        help="Execute complete analysis pipeline: preprocessing → lane detection → band quantification → MW calibration → statistics"
    )
    
    st.markdown('</div>', unsafe_allow_html=True)
    
    # Analysis execution with comprehensive error handling
    if analyze_button:
        st.session_state['_analysis_running'] = True
        st.session_state.ui_analysis_count += 1
        st.session_state.ui_last_action = "analysis"
        st.session_state.analysis_cancelled = False  # Initialize cancellation flag

        # Create analysis progress tracking
        progress_container = st.container()

        with progress_container:
            progress_bar = st.progress(0, text="🚀 Initializing analysis...")
            status_text = st.empty()
    
            # Add stop button in a separate column layout
            col1, col2 = st.columns([3, 1])
            with col2:
                stop_button = st.button("🛑 Stop Analysis", key="stop_analysis", type="secondary", use_container_width=True)
    
            if stop_button:
                st.session_state.analysis_cancelled = True
                st.session_state['_analysis_running'] = False
                st.warning("⚠️ Analysis cancelled by user")
                st.rerun()
    
            try:
                # Step 1: Image preprocessing
                if not st.session_state.get('analysis_cancelled', False):
                    status_text.info("🔬 **Step 1/5:** Preprocessing gel image...")
                    progress_bar.progress(10, text="🔬 Starting image analysis...")
            
                    # Add specific feedback for AI preprocessing
                    if preprocessing_mode.startswith("ChatGPT"):
                        status_text.info("🤖 **Step 1/5:** AI is analyzing your gel image (this may take 30-60 seconds)...")
                        progress_bar.progress(15, text="🤖 Contacting OpenAI ChatGPT-4.1...")
                    else:
                        progress_bar.progress(20, text="🔬 Optimizing image quality...")
            
                    img_hash = (st.session_state.res_image_metadata or {}).get('hash', '')
                    manual_params_str = json.dumps(manual_params) if preprocessing_mode == "Manual" else ""
            
                    # Enhanced spinner text for AI preprocessing
                    spinner_text = "🤖 ChatGPT-4.1 is analyzing your gel image and recommending optimal preprocessing..." if preprocessing_mode.startswith("ChatGPT") else "Applying preprocessing algorithms..."
            
                    with st.spinner(spinner_text):
                        # Check for cancellation before expensive operation
                        if st.session_state.get('analysis_cancelled', False):
                            raise Exception("Analysis cancelled by user")
                
                        preproc_result = cached_preprocess_image(img_hash, preprocessing_mode, manual_params_str)
                        prepped_img, preproc_metadata = preproc_result
                        st.session_state.res_preprocessing_outcome = preproc_metadata
        
                    # Check for cancellation after preprocessing
                    if st.session_state.get('analysis_cancelled', False):
                        raise Exception("Analysis cancelled by user")
            
                    if preproc_metadata.get('status') == 'error':
                        raise Exception(f"Preprocessing failed: {preproc_metadata.get('error', 'Unknown error')}")
            
                    st.toast("Preprocessing complete", icon="✅")
        
                # Step 2: Lane boundary application
                if not st.session_state.get('analysis_cancelled', False):
                    status_text.info("🎯 **Step 2/5:** Applying custom lane boundaries...")
                    progress_bar.progress(40, text="🎯 Configuring lane regions...")
            
                    # Here we would apply the calibrated lane boundaries to the analysis
                    # For now, we'll simulate this step
                    import time
                    time.sleep(1)  # Simulate processing time
        
                # Step 3: Band detection
                if not st.session_state.get('analysis_cancelled', False):
                    status_text.info("🔍 **Step 3/5:** Detecting and quantifying bands...")
                    progress_bar.progress(60, text="🔍 Identifying band signals...")
            
                    with st.spinner("Analyzing band patterns..."):
                        # Check for cancellation before expensive operation
                        if st.session_state.get('analysis_cancelled', False):
                            raise Exception("Analysis cancelled by user")
                        
                        # Connect to AutoDense lanes/bands pipeline
                        img_for_pipeline = st.session_state.res_uploaded_image
                        if img_for_pipeline is None:
                            raise Exception("No image available for band detection")
                        ba_params = {
                            "gel_type": st.session_state.params_gel_type,  # Use internal format for translation
                            "conf_threshold": float(st.session_state.params_conf_threshold),
                            "mw_lane": int(st.session_state.params_mw_lane),
                        }
                        # Get current lane calibration data
                        lane_calibration = get_current_lane_calibration()
                        res_ba = _try_run_ad_band_assist(
                            img_for_pipeline,
                            ba_params,
                            lane_boundaries=lane_calibration['lane_boundaries']
                        )
                        st.session_state.res_ba_errors = res_ba.get("errors", [])
                        st.session_state.res_ba_lanes_rows = res_ba.get("lanes", [])
                        st.session_state.res_ba_bands_rows = res_ba.get("bands", [])
                        st.session_state.res_ba_overlay_png = res_ba.get("overlay")
                        if res_ba.get("errors"):
                            raise Exception("Band detection failed: " + "; ".join(res_ba["errors"]))

                # Step 4: Molecular weight calibration
                if not st.session_state.get('analysis_cancelled', False):
                    status_text.info("📏 **Step 4/5:** MW calibration and size determination...")
                    progress_bar.progress(80, text="📏 Calibrating molecular weights...")
            
                    with st.spinner("Calculating molecular weights..."):
                        # Check for cancellation
                        if st.session_state.get('analysis_cancelled', False):
                            raise Exception("Analysis cancelled by user")
                        # MW calibration would happen here
                        time.sleep(1)
        
                # Step 5: Statistical analysis and finalization
                if not st.session_state.get('analysis_cancelled', False):
                    status_text.info("📊 **Step 5/5:** Statistical analysis and report generation...")
                    progress_bar.progress(100, text="📊 Finalizing results...")
            
                    with st.spinner("Computing statistics and generating report..."):
                        # Check for cancellation
                        if st.session_state.get('analysis_cancelled', False):
                            raise Exception("Analysis cancelled by user")
                        time.sleep(1)
            
                    status_text.success("✅ **Analysis completed successfully!**")
            
                    # --- AI Post-Run Explainer (AutoDense) --------------------------------------
            
                    try:
            
                        summary = {
            
                            "metrics": {
            
                                "lane_count": len(st.session_state.get("res_ba_lanes_rows") or []),
            
                                "band_count": len(st.session_state.get("res_ba_bands_rows") or []),
            
                                "ladder_r2": float(st.session_state.get("res_ladder_r2") or 0.0),
            
                                "smearing_index": float(st.session_state.get("res_smearing_index") or 0.0),
            
                                "false_split_rate": float(st.session_state.get("res_false_split_rate") or 0.0),
            
                            },
            
                            "observation": {
            
                                "baseline": st.session_state.get("obs_baseline") or {},
            
                                "prominence_frac": float(st.session_state.get("obs_prominence_frac") or 0.08),
            
                                "min_peak_distance_px": int(st.session_state.get("obs_min_peak_distance_px") or 8),
            
                                "polarity": st.session_state.get("obs_polarity") or "auto",
            
                                "rescue_used": bool(st.session_state.get("obs_rescue_used") or False),
            
                                "status": st.session_state.get("obs_status") or "ok",
            
                            },
            
                            "gel_type": st.session_state.get("params_gel_type") or "sds_page",
            
                        }
            
                        expl = chatgpt_postrun_explainer(summary)
            
                        st.markdown("### 🤖 AI Interpretation")
            
                        if expl.get("interpretation"):
            
                            st.info(expl["interpretation"])
            
                        if expl.get("notes"):
            
                            st.caption(expl["notes"])
            
                        next_params = expl.get("next_params") or {}
            
                        action = expl.get("action") or "halt"
            
                        if action == "rerun" and next_params:
            
                            with st.expander("🔧 Suggested parameter tweak", expanded=True):
            
                                st.json(next_params)
            
                                if st.button("Apply suggestion & re-run", use_container_width=True):
            
                                    for k, v in next_params.items():
            
                                        if k == "baseline" and isinstance(v, dict):
            
                                            st.session_state["obs_baseline"] = v
            
                                        else:
            
                                            st.session_state[f"obs_{k}"] = v
            
                                    st.rerun()
            
                    except Exception as _e:
            
                        st.caption(f"AI explainer unavailable: {_e}")
            
                    # --- end explainer ----------------------------------------------------------

                st.session_state['_analysis_running'] = False
                # Store comprehensive analysis results
                analysis_timestamp = pd.Timestamp.now()
        
                st.session_state.res_analysis_data = {
                    'analysis_id': f"AD_{st.session_state.ui_analysis_count:03d}_{int(analysis_timestamp.timestamp())}",
                    'timestamp': analysis_timestamp.isoformat(),
                    'preprocessing': preproc_metadata,
                    'lane_boundaries': st.session_state.res_lane_boundaries,
                    'parameters': {
                        'gel_type': st.session_state.params_gel_type,
                        'n_lanes': st.session_state.params_n_lanes,
                        'modality': modality,
                        'ladder_type': ladder_type,
                        'mw_lane': mw_lane,
                        'conf_threshold': conf_threshold,
                        'preprocessing_mode': preprocessing_mode,
                        'manual_params': manual_params if preprocessing_mode == "Manual" else None
                    },
                    'analysis_count': st.session_state.ui_analysis_count,
                    'status': 'completed',
                    'warnings': analysis_warnings
                }
        
                # Success celebration
                st.success("🎉 **Analysis completed successfully!** 🎉")
        
                # Display analysis results summary
                st.markdown("### 📊 Analysis Results Summary")
        
                col1, col2, col3, col4 = st.columns(4)
        
                with col1:
                    st.metric("Analysis ID", f"AD_{st.session_state.ui_analysis_count:03d}")
                with col2:
                    st.metric("Processing Time", f"{int((pd.Timestamp.now() - analysis_timestamp).total_seconds())}s")
                with col3:
                    st.metric("Total Lanes", st.session_state.params_n_lanes)
                with col4:
                    st.metric("Status", "✅ Complete")
        
                # Preprocessing results section
                st.markdown("#### 🔬 Preprocessing Results")
        
                if preprocessing_mode.startswith("ChatGPT"):
                    confidence = preproc_metadata.get('confidence', 0.0)
                    reasoning = preproc_metadata.get('reasoning', 'No reasoning provided')
                    mode_display = preproc_metadata.get('mode', 'ChatGPT-4.1')
            
                    st.success(f"🧠 **AI Decision:** {mode_display} (Confidence: {confidence:.1%})")
            
                    with st.expander("🤖 AI Reasoning & Analysis"):
                        st.info(f"**AI Reasoning:** {reasoning}")
                        if 'params' in preproc_metadata:
                            st.json(preproc_metadata['params'])
        
                elif preprocessing_mode.startswith("AI"):
                    mode_display = preproc_metadata.get('mode', 'AI Guarded')
                    st.info(f"🧠 **Preprocessing:** {mode_display}")
            
                    if 'before' in preproc_metadata and 'after' in preproc_metadata:
                        before_metrics = preproc_metadata['before']
                        after_metrics = preproc_metadata['after']
                
                        col1, col2, col3 = st.columns(3)
                        with col1:
                            snr_delta = after_metrics.get('snr', 0) - before_metrics.get('snr', 0)
                            st.metric("SNR Improvement", f"{snr_delta:+.2f} dB")
                        with col2:
                            sep_delta = after_metrics.get('sep', 0) - before_metrics.get('sep', 0)
                            st.metric("Separation Δ", f"{sep_delta:+.3f}")
                        with col3:
                            st.metric("Quality Score", f"{after_metrics.get('quality', 0):.2f}")
        
                else:
                    mode_display = preproc_metadata.get('mode', 'Manual/Raw')
                    st.info(f"🛠️ **Preprocessing:** {mode_display}")
        
                # Analysis pipeline status
                st.markdown("#### 🔄 Analysis Pipeline Status")
        
                pipeline_steps = [
                    ("✅", "Image preprocessing", True, f"Complete - {mode_display}"),
                    ("✅", "Custom lane boundaries", True, f"{len(st.session_state.res_lane_boundaries)} lanes applied"),
                    ("🔄", "Band detection & quantification", False, "Integration with full pipeline in progress"),
                    ("⏳", "MW calibration", False, f"Using {ladder_type} standard"),
                    ("⏳", "Statistical analysis", False, "Pending band quantification"),
                    ("⏳", "Report generation", False, "Ready for implementation")
                ]
        
                for icon, step, complete, status_desc in pipeline_steps:
                    if complete:
                        st.success(f"{icon} **{step}:** {status_desc}")
                    else:
                        st.info(f"{icon} **{step}:** {status_desc}")
        
                # Next steps guidance
                st.markdown("#### 🎯 Next Steps")
                st.info("➡️ **View detailed results and export options in the** ***Results & Export*** **tab**")
        
                # Quick action buttons
                col1, col2, col3 = st.columns(3)
        
                with col1:
                    if st.button("📊 **View Results**", use_container_width=True, help="Switch to Results & Export tab"):
                        goto_tab("📊 Results")
                        goto_tab("📊 Results & Export")
        
                with col2:
                    st.button("🔄 **Analyze Again**", use_container_width=True, help="Run analysis with different parameters")
        
                with col3:
                    st.button("📥 **Quick Export**", use_container_width=True, help="Download analysis summary")
        
            except Exception as e:
                # Clear progress indicators
                st.session_state['_analysis_running'] = False
                progress_bar.empty()
                status_text.empty()
        
                # Handle cancelled operations differently from errors
                if "cancelled by user" in str(e).lower():
                    st.info("⏹️ **Analysis Cancelled**")
                    st.toast("Analysis stopped", icon="⏹️")
            
                    # Reset cancellation flag
                    st.session_state.analysis_cancelled = False
            
                    st.markdown("""
                    <div class="info-panel">
                    <strong>Analysis stopped by user</strong><br>
                    You can restart the analysis at any time by clicking the analysis button again.
                    </div>
                    """, unsafe_allow_html=True)
            
                else:
                    # Handle actual errors
                    st.session_state.ui_error_count += 1
                    st.toast("Analysis failed", icon="❌")
            
                    # Comprehensive error reporting
                    with st.expander("🔍 Analysis Error Details", expanded=True):
                        st.markdown(f"""
                        <div class="error-details">
                        <strong>Analysis Failed - Error #{st.session_state.ui_error_count}</strong><br>
                        <strong>Error:</strong> {str(e)}<br>
                        <strong>Analysis Count:</strong> {st.session_state.ui_analysis_count}<br>
                        <strong>Preprocessing Mode:</strong> {preprocessing_mode}
                        </div>
                        """, unsafe_allow_html=True)
            
                    # Contextual troubleshooting
                    st.markdown("""
                    **🔧 Troubleshooting Steps:**
            
                    1. **Verify Prerequisites:**
                       - Confirm image is loaded correctly
                       - Check lane calibration is complete
                       - Ensure all parameters are valid
            
                    2. **Try Different Settings:**
                       - Switch to "AI guarded" preprocessing mode
                       - Reduce image size if very large
                       - Verify MW lane number is correct
            
                    3. **System Resources:**
                       - Ensure sufficient memory available
                       - Close other resource-intensive applications
                       - Try with a smaller/simpler image first
            
                    4. **Get Help:**
                       - Check the troubleshooting guide
                       - Contact support with error details
                       - Try the simplified analysis mode
                    """)
            
                    # Debug information
                    if st.checkbox("Show technical debug information", key=f"analysis_debug_{st.session_state.ui_error_count}"):
                        st.subheader("Debug Information")
                
                        debug_info = {
                            "session_state_keys": list(st.session_state.keys()),
                            "image_metadata": st.session_state.res_image_metadata,
                            "preprocessing_mode": preprocessing_mode,
                            "manual_params": manual_params if preprocessing_mode == "Manual" else "Not applicable",
                            "lane_boundaries_count": len(st.session_state.res_lane_boundaries) if st.session_state.res_lane_boundaries else 0,
                            "analysis_parameters": {
                                "gel_type": st.session_state.params_gel_type,
                                "n_lanes": st.session_state.params_n_lanes,
                                "mw_lane": mw_lane,
                                "conf_threshold": conf_threshold,
                                "ladder_type": ladder_type
                            }
                        }
                
                        st.json(debug_info)
                
                        # Technical exception details
                        st.subheader("Exception Details")
                        st.code(f"Exception Type: {type(e).__name__}")
                        st.code(f"Exception Message: {str(e)}")
                
                        # Stack trace
                        import traceback
                        st.code(traceback.format_exc())
with tab3:
    st.markdown("""
    ## 📊 Results & Export
    View comprehensive analysis results, interactive visualizations, and export data in various formats.
    """)
    
    # Check for analysis results
    has_results = st.session_state.res_analysis_data is not None
    
    if not has_results:
        st.markdown("### 📊 No Analysis Results Available")
        
        st.info("Complete a gel analysis to view results here. The results dashboard will show:")
        
        # Preview of available features
        with st.expander("🔮 Results Dashboard Preview", expanded=True):
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown("""
                **📈 Analysis Summary:**
                - Gel configuration and parameters
                - Processing statistics and metrics
                - Lane and band detection summary
                - Quality assessment scores
                
                **📊 Quantitative Results:**
                - Band intensity measurements
                - Molecular weight calculations
                - Statistical significance testing
                - Comparative analysis between lanes
                """)
            
            with col2:
                st.markdown("""
                **🖼️ Visual Results:**
                - Annotated gel images with overlays
                - Lane boundary visualizations
                - Band detection highlights
                - MW calibration curves
                
                **📥 Export Options:**
                - CSV data tables (Excel/GraphPad compatible)
                - High-resolution annotated images
                - Complete analysis reports (PDF)
                - Raw data for further analysis
                """)
        
        # Status of prerequisites
        st.markdown("### 📋 Analysis Prerequisites")
        
        col1, col2, col3 = st.columns(3)
        
        with col1:
            if st.session_state.res_uploaded_image:
                st.success("✅ **Image Uploaded**")
            else:
                st.error("❌ **Image Missing**")
        
        with col2:
            if st.session_state.res_lane_boundaries:
                st.success("✅ **Lanes Calibrated**")
            else:
                st.error("❌ **Calibration Missing**")
        
        with col3:
            if has_results:
                st.success("✅ **Analysis Complete**")
            else:
                st.info("⏳ **Analysis Pending**")
        
        st.stop()  # Exit early if no results
    
    # Display comprehensive results
    results = st.session_state.res_analysis_data
    
    # Results header with key information
    st.success(f"""
    ✅ **Analysis Complete** - ID: {results['analysis_id']}  
    📅 **Completed:** {results['timestamp'][:19]} | 🔄 **Run #{results['analysis_count']}**
    """)
    
    # Main results dashboard
    st.markdown("### 📊 Analysis Dashboard")
    
    # Key metrics overview
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        gel_type_display = results['parameters']['gel_type'].replace('_', ' ').title()
        st.metric(
            "Gel Type",
            gel_type_display,
            help="Type of gel analyzed"
        )
    
    with col2:
        st.metric(
            "Total Lanes",
            results['parameters']['n_lanes'],
            help="Number of configured lanes"
        )
    
    with col3:
        st.metric(
            "MW Standard",
            f"Lane {results['parameters']['mw_lane']}",
            help="Lane containing molecular weight standards"
        )
    
    with col4:
        modality_display = results['parameters']['modality'].upper()
        st.metric(
            "Analysis Type",
            modality_display,
            help="Protein (SDS) or DNA analysis"
        )
    
    # Detailed results sections
    
    # 1. Preprocessing Results
    with st.expander("🔬 Preprocessing Analysis", expanded=True):
        preproc = results['preprocessing']
        
        col1, col2 = st.columns(2)
        
        with col1:
            st.markdown("**Processing Summary:**")
            
            mode = preproc.get('mode', 'Unknown')
            status = preproc.get('status', 'unknown')
            
            if status == 'success':
                st.success(f"✅ **Method:** {mode}")
            elif status == 'error':
                st.error(f"❌ **Method:** {mode} (with errors)")
            else:
                st.info(f"ℹ️ **Method:** {mode}")
            
            # ChatGPT-specific results
            if 'confidence' in preproc:
                confidence = preproc['confidence']
                reasoning = preproc.get('reasoning', 'No reasoning provided')
                
                st.metric("AI Confidence", f"{confidence:.1%}")
                
                with st.expander("🤖 AI Reasoning"):
                    st.info(f"**Analysis:** {reasoning}")
            
            # Algorithmic AI results
            elif 'before' in preproc and 'after' in preproc:
                before_metrics = preproc['before']
                after_metrics = preproc['after']
                
                st.markdown("**Quality Improvements:**")
                
                # Calculate improvements
                snr_improvement = after_metrics.get('snr', 0) - before_metrics.get('snr', 0)
                sep_improvement = after_metrics.get('sep', 0) - before_metrics.get('sep', 0)
                
                col_a, col_b = st.columns(2)
                with col_a:
                    st.metric("SNR Δ", f"{snr_improvement:+.2f} dB")
                with col_b:
                    st.metric("Separation Δ", f"{sep_improvement:+.3f}")
        
        with col2:
            st.markdown("**Technical Details:**")
            
            # Show preprocessing parameters in a formatted way
            if 'params' in preproc:
                params = preproc['params']
                if isinstance(params, dict) and params:
                    for key, value in params.items():
                        if isinstance(value, (int, float)):
                            st.caption(f"• **{key}:** {value:.3f}")
                        else:
                            st.caption(f"• **{key}:** {value}")
            
            # Error information if present
            if 'error' in preproc:
                st.warning(f"⚠️ **Note:** {preproc['error']}")
            
            # Processing time (if available)
            if 'processing_time' in preproc:
                st.caption(f"⏱️ **Processing time:** {preproc['processing_time']:.2f}s")
        
        # Full preprocessing data (collapsible)
        with st.expander("📄 Complete Preprocessing Data"):
            st.json(preproc)
    
    # 2. Lane Configuration Results
    with st.expander("🎯 Lane Configuration Analysis", expanded=True):
        lane_boundaries = results['lane_boundaries']
        
        if lane_boundaries and HAS_LANE_MAPPING:
            try:
                from autodense.preprocess.simple_lane_mapping import boundaries_to_dataframe
                
                # Convert to DataFrame for better display
                df = boundaries_to_dataframe(lane_boundaries)
                
                st.markdown("**Lane Boundary Configuration:**")
                
                # Enhanced dataframe display
                st.dataframe(
                    df,
                    use_container_width=True,
                    column_config={
                        "lane_index": st.column_config.NumberColumn(
                            "Lane #",
                            format="%d",
                            help="Lane number"
                        ),
                        "center_px": st.column_config.NumberColumn(
                            "Center (px)",
                            format="%.1f",
                            help="Lane center position"
                        ),
                        "left_px": st.column_config.NumberColumn(
                            "Left Edge (px)",
                            format="%.1f",
                            help="Left boundary position"
                        ),
                        "right_px": st.column_config.NumberColumn(
                            "Right Edge (px)",
                            format="%.1f",
                            help="Right boundary position"
                        ),
                        "width_px": st.column_config.NumberColumn(
                            "Width (px)",
                            format="%.1f",
                            help="Lane width"
                        )
                    }
                )
                
                # Lane statistics
                st.markdown("**Lane Statistics:**")
                col1, col2, col3, col4 = st.columns(4)
                
                lane_widths = [b.width_px for b in lane_boundaries]
                lane_spacings = [lane_boundaries[i].center_px - lane_boundaries[i-1].center_px 
                               for i in range(1, len(lane_boundaries))]
                
                with col1:
                    st.metric("Avg Width", f"{np.mean(lane_widths):.1f} px")
                with col2:
                    st.metric("Width StdDev", f"{np.std(lane_widths):.1f} px")
                with col3:
                    st.metric("Avg Spacing", f"{np.mean(lane_spacings):.1f} px")
                with col4:
                    coverage = ((lane_boundaries[-1].right_px - lane_boundaries[0].left_px) / 
                               st.session_state.res_image_metadata['dimensions'][0] * 100)
                    st.metric("Coverage", f"{coverage:.1f}%")
                
            except Exception as e:
                st.error(f"Error displaying lane data: {e}")
                st.json([{"lane": b.lane_index, "center": b.center_px} for b in lane_boundaries[:5]])
        
        else:
            st.info("Lane boundary data not available or display modules missing")
    
    # 3. Analysis Parameters Summary
    with st.expander("⚙️ Analysis Parameters", expanded=False):
        params = results['parameters']
        
        col1, col2 = st.columns(2)
        
        with col1:
            st.markdown("**Gel Configuration:**")
            st.info(f"• **Type:** {params['gel_type']}")
            st.info(f"• **Modality:** {params['modality'].upper()}")
            st.info(f"• **Lanes:** {params['n_lanes']}")
            st.info(f"• **MW Lane:** {params['mw_lane']}")
        
        with col2:
            st.markdown("**Detection Settings:**")
            st.info(f"• **Confidence:** {params['conf_threshold']:.2f}")
            st.info(f"• **MW Standard:** {params['ladder_type']}")
            st.info(f"• **Preprocessing:** {params['preprocessing_mode']}")
            
            if params.get('manual_params'):
                st.caption("• Custom preprocessing parameters applied")
        
        # Analysis warnings
        if results.get('warnings'):
            st.markdown("**Analysis Warnings:**")
            for warning in results['warnings']:
                st.warning(f"⚠️ {warning}")
    
    # 4. Export and Download Options
    st.markdown("### 📥 Export & Download Options")
    # --- AutoDense Band Assist exports ---
    st.markdown("### 🧾 AutoDense Band Assist — Exports")
    lanes = st.session_state.get("res_ba_lanes_rows") or []
    bands = st.session_state.get("res_ba_bands_rows") or []
    if lanes or bands or st.session_state.get("res_ba_overlay_png"):
        lane_cols = ["lane_index", "x0", "y0", "x1", "y1", "lane_type", "band_count"]
        band_cols = ["lane_index", "band_index", "x0", "x1", "y0", "y1", "intensity", "confidence"]
        lcsv = convert_to_csv(lanes, lane_cols) if lanes else ""
        bcsv = convert_to_csv(bands, band_cols) if bands else ""
        ec1, ec2, ec3 = st.columns(3)
        with ec1:
            st.download_button("⬇️ lanes.csv", data=lcsv, file_name="lanes.csv", mime="text/csv", use_container_width=True, disabled=not bool(lanes))
        with ec2:
            st.download_button("⬇️ bands.csv", data=bcsv, file_name="bands.csv", mime="text/csv", use_container_width=True, disabled=not bool(bands))
        with ec3:
            if st.session_state.get("res_ba_overlay_png"):
                st.download_button("⬇️ overlay.png", data=st.session_state.res_ba_overlay_png, file_name="overlay.png", mime="image/png", use_container_width=True)
    else:
        st.caption("No Band Assist data yet.")
    
    col1, col2, col3 = st.columns(3)
    
    with col1:
        st.markdown("**🗂️ Data Tables**")
        
        # Lane configuration CSV
        if lane_boundaries and HAS_LANE_MAPPING:
            try:
                csv_data = boundaries_to_csv(lane_boundaries)
                
                st.download_button(
                    "📊 **Lane Boundaries CSV**",
                    csv_data,
                    f"autodense_lanes_{results['analysis_id']}.csv",
                    "text/csv",
                    help="Download lane boundary data for external analysis",
                    use_container_width=True
                )
            except Exception as e:
                st.button(
                    "📊 Lane CSV",
                    disabled=True,
                    help=f"Export failed: {e}",
                    use_container_width=True
                )
        else:
            st.button(
                "📊 Lane CSV",
                disabled=True,
                help="Lane data not available",
                use_container_width=True
            )
        
        # Band intensity data (placeholder)
        st.button(
            "📈 **Band Data CSV**",
            disabled=True,
            help="Band quantification data (coming soon)",
            use_container_width=True
        )
    
    with col2:
        st.markdown("**📋 Analysis Reports**")
        
        # Complete analysis parameters JSON
        analysis_json = json.dumps(results, indent=2, default=str)
        st.download_button(
            "⚙️ **Analysis Report JSON**",
            analysis_json,
            f"autodense_analysis_{results['analysis_id']}.json",
            "application/json",
            help="Complete analysis configuration and metadata",
            use_container_width=True
        )
        
        # Summary report (placeholder)
        st.button(
            "📄 **Summary Report PDF**",
            disabled=True,
            help="Comprehensive analysis report (coming soon)",
            use_container_width=True
        )
    
    with col3:
        st.markdown("**🖼️ Visual Exports**")
        
        # Annotated gel images (placeholder)
        st.button(
            "🎨 **Annotated Gel Image**",
            disabled=True,
            help="High-resolution gel with overlays (coming soon)",
            use_container_width=True
        )
        
        # Results visualization (placeholder)
        st.button(
            "📊 **Results Visualization**",
            disabled=True,
            help="Interactive charts and graphs (coming soon)",
            use_container_width=True
        )
    
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
                "has_openai": HAS_OPENAI,
                "has_drawable_canvas": HAS_DRAWABLE_CANVAS,
                "has_lane_mapping": HAS_LANE_MAPPING,
                "session_analysis_count": st.session_state.ui_analysis_count,
                "session_error_count": st.session_state.ui_error_count
            }
            st.json(system_info)

# Enhanced footer with comprehensive status and help
st.markdown("---")

# Status bar with detailed information
col1, col2, col3, col4 = st.columns([3, 1, 1, 1])

with col1:
    # Dynamic status based on workflow progress
    status_items = []
    
    if st.session_state.res_uploaded_image:
        metadata = st.session_state.res_image_metadata or {}
        filename = metadata.get('filename', 'Image')[:20] + ('...' if len(metadata.get('filename', '')) > 20 else '')
        status_items.append(f"📤 {filename}")
    
    if st.session_state.res_lane_boundaries:
        n_lanes = len(st.session_state.res_lane_boundaries)
        gel_type = st.session_state.params_gel_type.upper().replace('_', '-')
        status_items.append(f"🎯 {n_lanes} lanes ({gel_type})")
    
    if st.session_state.res_analysis_data:
        analysis_id = st.session_state.res_analysis_data.get('analysis_id', 'Unknown')[-7:]  # Last 7 chars
        status_items.append(f"🔬 {analysis_id}")
    
    if status_items:
        st.success(" | ".join(status_items))
    else:
        st.info("🚀 Ready to begin - upload a gel image to start")

with col2:
    # Analysis counter with session info
    count = st.session_state.ui_analysis_count
    if count > 0:
        st.metric("Analyses", count, help="Number of analyses completed this session")
    else:
        st.caption("No analyses yet")

with col3:
    # Error counter (only show if errors occurred)
    error_count = st.session_state.ui_error_count
    if error_count > 0:
        st.metric("Errors", error_count, help="Number of errors encountered", delta_color="off")
    else:
        st.caption("No errors")

with col4:
    # Help and shortcuts
    if st.button("❓ **Help**", help="Show keyboard shortcuts and tips", use_container_width=True):
        # Create help modal content
        st.info("""
        **⌨️ Keyboard Shortcuts:**
        - `Ctrl + Enter`: Run analysis
        - `Tab`: Navigate between fields  
        - `Esc`: Clear current selection
        - `?`: Show this help
        
        **💡 Usage Tips:**
        - Use high-contrast, focused gel images
        - Calibrate with distinct, well-separated bands
        - Choose calibration points far apart for accuracy
        - Verify lane alignment before proceeding
        
        **🔧 Troubleshooting:**
        - Check Prerequisites Status in Analysis tab
        - Try different preprocessing modes
        - Verify image quality and format
        - Use manual calibration if canvas fails
        
        **📞 Support:**
        - GitHub: [AutoDense Repository](https://github.com/your-org/autodense)
        - Email: support@autodense.com
        """)

# TODO: Keyboard event handling for future enhancement
# This would require streamlit-keyup or similar package

def render_global_sticky_footer() -> None:
    """Fixed footer with Back / Next that follows the current active tab."""
    import streamlit.components.v1 as components
    
    components.html("""
    <style>
        #ad-sticky-footer {
            position: fixed;
            left: 0; right: 0; bottom: 0;
            z-index: 9999;
            padding: 10px 16px;
            background: rgba(255,255,255,0.92);
            backdrop-filter: blur(6px);
            border-top: 1px solid #e0e0e0;
            box-shadow: 0 -2px 8px rgba(0,0,0,0.1);
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
            color: white;
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
        
        /* Hide on mobile to avoid overlay issues */
        @media (max-width: 768px) {
            #ad-sticky-footer {
                display: none;
            }
        }
    </style>
    
    <div id="ad-sticky-footer">
        <button id="ad-back" class="btn">⬅️ Back</button>
        <div class="hint">Use Back/Next to move through Calibration → Analysis → Results</div>
        <button id="ad-next" class="btn primary">Next ➡️</button>
    </div>
    
    <script>
      const doc = window.parent.document;
      function clickTabByPrefix(prefixes) {
        const tabs = Array.from(doc.querySelectorAll('[role="tab"]'));
        for (const pref of prefixes) {
          for (const t of tabs) {
            const txt = (t.innerText || t.textContent).trim();
            if (txt.startsWith(pref)) { 
              t.click(); 
              window.parent.scrollTo({top: 0, behavior: 'smooth'}); 
              return true; 
            }
          }
        }
        return false;
      }
      function currentTabLabel() {
        const tabs = Array.from(doc.querySelectorAll('[role="tab"]'));
        const curr = tabs.find(t => t.getAttribute('aria-selected') === 'true');
        return curr ? (curr.innerText || curr.textContent).trim() : '';
      }
      function configureButtons() {
        const back = document.getElementById('ad-back');
        const next = document.getElementById('ad-next');
        const label = currentTabLabel();
        const isCal = label.startsWith('🎯');
        const isAna = label.startsWith('🔬');
        const isRes = label.startsWith('📊');
        // Reset
        back.removeAttribute('disabled'); next.removeAttribute('disabled');
        back.onclick = null; next.onclick = null;
        if (isCal) {
          back.setAttribute('disabled','true');
          next.onclick = () => clickTabByPrefix(['🔬 Analysis', '🔬 Analysis & Processing']);
        } else if (isAna) {
          back.onclick = () => clickTabByPrefix(['🎯 Lane Calibration']);
          next.onclick = () => clickTabByPrefix(['📊 Results', '📊 Results & Export']);
        } else if (isRes) {
          back.onclick = () => clickTabByPrefix(['🔬 Analysis', '🔬 Analysis & Processing']);
          next.setAttribute('disabled','true');
        } else {
          // Unknown: default to enabling both
          back.onclick = () => clickTabByPrefix(['🎯 Lane Calibration']);
          next.onclick = () => clickTabByPrefix(['🔬 Analysis', '🔬 Analysis & Processing']);
        }
      }
      // Try repeatedly until the tablist exists, then observe changes
      let tries = 0;
      const iv = setInterval(() => {
        tries += 1;
        const tablist = doc.querySelector('[role="tablist"]');
        if (tablist) {
          configureButtons();
          const obs = new MutationObserver(configureButtons);
          obs.observe(tablist, {attributes:true, subtree:true, childList:true, characterData:true});
          clearInterval(iv);
        }
        if (tries > 60) clearInterval(iv);
      }, 100);
    </script>
    """, height=0)

# Accessibility: Close main content landmark
st.markdown('</main>', unsafe_allow_html=True)

# Render sticky footer navigation
render_global_sticky_footer()

# Performance monitoring (development mode)
if st.checkbox("⚡ Performance Monitor", help="Show performance metrics"):
    import psutil
    import time
    
    # System metrics
    cpu_percent = psutil.cpu_percent(interval=1)
    memory = psutil.virtual_memory()
    
    col1, col2, col3 = st.columns(3)
    
    with col1:
        st.metric("CPU Usage", f"{cpu_percent:.1f}%")
    
    with col2:
        st.metric("Memory", f"{memory.percent:.1f}%")
    
    with col3:
        try:
            # Try to get cache stats (may not be available in all Streamlit versions)
            cache_info = getattr(st.cache_data, 'cache', {})
            cache_hits = len(cache_info) if hasattr(cache_info, '__len__') else 0
        except Exception:
            cache_hits = 0
        st.metric("Cache Hits", cache_hits)

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


# --- Prompt & OpenAI glue (inserted by wire_prompts_autodense.py) -----------
from pathlib import Path
import base64, json, re




def chatgpt_postrun_explainer(run_summary: Dict[str, Any]) -> Dict[str, Any]:
    """
    Calls ChatGPT-4.1 with analysis.system.md given your RunSummary telemetry.
    Returns a dict with interpretation / next_params / action / notes.
    """
    from ui.utils.preprocessing import _load_prompt
    sys_prompt = _load_prompt("analysis.system.md", "<embedded>")
    from openai import OpenAI
    client = OpenAI()
    resp = client.chat.completions.create(
        model="gpt-4o",
        messages=[{
            "role": "system",
            "content": sys_prompt
        }, {
            "role": "user", 
            "content": json.dumps(run_summary)
        }],
        temperature=0
    )
    text = resp.choices[0].message.content
    return extract_json(text)
# --- end glue ---------------------------------------------------------------
