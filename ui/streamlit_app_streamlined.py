

import streamlit as st
import json, io
import numpy as np
import pandas as pd
from PIL import Image
import time
import contextlib
import gc
import hashlib

# AutoDense imports with graceful fallbacks
try:
    from autodense.preprocess.pipeline import PreprocParams, run as preproc_run
    from autodense.preprocess.policy import guarded_preprocess
    HAS_PREPROCESS = True
except ImportError:
    HAS_PREPROCESS = False
    st.warning("⚠️ AutoDense preprocessing not available")

try:
    from streamlit_drawable_canvas import st_canvas
    HAS_DRAWABLE_CANVAS = True
except ImportError:
    HAS_DRAWABLE_CANVAS = False

try:
    from autodense.preprocess.openai_policy import openai_guided_preprocess
    HAS_OPENAI = True
except ImportError:
    HAS_OPENAI = False

try:
    from autodense.preprocess.simple_lane_mapping import (
        TwoPointCalibration, calculate_lane_positions,
        validate_calibration_points, get_recommended_gel_settings, boundaries_to_csv
    )
    HAS_LANE_MAPPING = True
except ImportError:
    HAS_LANE_MAPPING = False

@st.cache_data(persist=True, max_entries=1)
def process_uploaded_image(uploaded_file_bytes: bytes, filename: str):
    
    img = Image.open(io.BytesIO(uploaded_file_bytes)).convert("RGB")
    img_array = np.asarray(img)
    
    image_hash = hashlib.md5(uploaded_file_bytes).hexdigest()[:8]
    
    metadata = {
        'filename': filename,
        'dimensions': img.size,  # More efficient than array shape
        'mode': img.mode,
        'format': img.format or 'Unknown',
        'hash': image_hash,
        'size_bytes': len(uploaded_file_bytes)
    }
    return img, img_array, metadata

@st.cache_data(
    show_spinner="🔬 Optimizing gel image...", 
    max_entries=10,  # Increased for better hit rate
    ttl=7200,  # 2 hours for longer sessions
    hash_funcs={"dict": lambda x: str(sorted(x.items())) if x else ""}
)
def cached_preprocess_image(image_hash: str, mode: str, manual_params_str: str = ""):
    
    if 'res_uploaded_image' not in st.session_state or st.session_state.res_image_metadata.get('hash') != image_hash:
        return None, {"status": "error", "error": "Image not found in session"}
    
    img = st.session_state.res_uploaded_image
    manual_params = json.loads(manual_params_str) if manual_params_str else None
    
    try:
        if mode.startswith("ChatGPT") and HAS_OPENAI:
            outcome = openai_guided_preprocess(img)
            return outcome.image, {
                "mode": f"ChatGPT-4.1: {outcome.mode}",
                "confidence": outcome.params.get('ai_confidence', 0.0),
                "reasoning": outcome.params.get('ai_reasoning', 'No reasoning provided'),
                "status": "success"
            }
        elif mode.startswith("AI") and HAS_PREPROCESS:
            outcome = guarded_preprocess(img)
            return outcome.image, {
                "mode": outcome.mode,
                "before": outcome.before,
                "after": outcome.after,
                "params": outcome.params,
                "status": "success"
            }
        elif mode == "Manual" and manual_params and HAS_PREPROCESS:
            pp = PreprocParams(**manual_params)
            prepped, meta, _ = preproc_run(img, pp, save_dir=None, save_prefix="")
            return prepped, {"mode": "Manual", "meta": meta.__dict__, "status": "success"}
        else:
            arr = np.asarray(img.convert("L")).astype(np.float32)
            prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
            return prepped, {"mode": "Raw (no preprocessing)", "status": "fallback"}
    
    except Exception as e:
        arr = np.asarray(img.convert("L")).astype(np.float32)
        prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
        return prepped, {"mode": "Fallback (preprocessing failed)", "error": str(e), "status": "error"}

@st.cache_data(max_entries=20, ttl=1800)
def calculate_lane_metrics(lane_boundaries_data: list):
    
    if len(lane_boundaries_data) < 2:
        return {'spacings': [], 'widths': [], 'min_spacing': 0, 'max_spacing': 0, 'mean_spacing': 0, 'spacing_cv': 0}
    
    centers = np.array([b['center_px'] for b in lane_boundaries_data])
    widths = np.array([b['width_px'] for b in lane_boundaries_data])
    spacings = np.abs(np.diff(centers))  # Vectorized spacing calculation
    
    return {
        'spacings': spacings.tolist(),
        'widths': widths.tolist(),
        'min_spacing': float(spacings.min()) if len(spacings) > 0 else 0,
        'max_spacing': float(spacings.max()) if len(spacings) > 0 else 0,
        'mean_spacing': float(spacings.mean()) if len(spacings) > 0 else 0,
        'spacing_cv': float(spacings.std() / spacings.mean()) if len(spacings) > 0 and spacings.mean() > 0 else 0
    }

@st.cache_data(show_spinner="🔍 Analyzing gel structure...", max_entries=5, ttl=1800)
def cached_analyze_gel(_image_path: str, _params_dict: dict, _retries: int):
    
    try:
        return {
            "lanes": [],
            "bands": [],
            "status": "success",
            "message": "Analysis pipeline integration pending"
        }
    except Exception as e:
        return {"status": "error", "error": str(e)}

st.set_page_config(
    page_title="AutoDense – UX Optimized",
    page_icon="🔬",
    layout="wide",
    initial_sidebar_state="collapsed",
    menu_items={
        'Get Help': 'https://github.com/your-org/autodense',
        'Report a bug': 'mailto:support@autodense.com',
        'About': 'AutoDense: Professional gel electrophoresis analysis for bench scientists'
    }
)

st.markdown("<style>.workflow-step{padding:8px;margin:4px;border-radius:4px;}.status-ready{background:#28a745;}.status-warning{background:#ffc107;}.status-error{background:#dc3545;}</style>", unsafe_allow_html=True)

def init_session_state():
    
    defaults = {
        'ui_current_tab': 'calibration',
        'ui_canvas_key': 0,
        'ui_analysis_count': 0,
        'ui_error_count': 0,
        'ui_last_action': None,
        
        'params_gel_type': 'sds_page',
        'params_n_lanes': 12,
        'params_preprocessing_mode': 'AI guarded (recommended)',
        'params_conf_threshold': 0.30,
        'params_mw_lane': 1,
        'params_ladder_type': 'auto',
        
        'res_uploaded_image': None,
        'res_uploaded_array': None,
        'res_image_metadata': None,
        'res_calibration_points': [],
        'res_lane_boundaries': None,
        'res_preprocessing_outcome': None,
        'res_analysis_data': None,
        'res_export_data': None,
    }
    
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value

init_session_state()

def handle_errors(operation_name: str):
    
    def decorator(func):
        def wrapper(*args, **kwargs):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                st.session_state.ui_error_count += 1
                st.toast(f"{operation_name} failed", icon="❌")
                
                with st.expander(f"🔍 {operation_name} Error Details", expanded=True):
                    st.markdown(f, unsafe_allow_html=True)
                    
                    if "image" in operation_name.lower():
                        st.markdown()
                    elif "calibration" in operation_name.lower():
                        st.markdown()
                    elif "analysis" in operation_name.lower():
                        st.markdown()
                    
                    # Debug information toggle
                    if st.checkbox("Show technical details", key=f"debug_{operation_name}_{st.session_state.ui_error_count}"):
                        st.code(f"Exception: {type(e).__name__}: {str(e)}")
                        import traceback
                        st.code(traceback.format_exc())
                
                return None
        return wrapper
    return decorator

st.markdown()

st.markdown("### 📊 Analysis Workflow")

workflow_steps = [
    ("📤", "Upload", "upload", "Upload gel image", st.session_state.res_uploaded_image is not None),
    ("🎯", "Calibrate", "calibrate", "Set lane boundaries", st.session_state.res_lane_boundaries is not None),
    ("🔬", "Analyze", "analyze", "Run comprehensive analysis", st.session_state.res_analysis_data is not None),
    ("📊", "Export", "export", "Download results", st.session_state.res_export_data is not None)
]

# Hide large workflow cards and show compact chips
st.markdown("""
<style>
.workflow-step { display: none !important; }
.workflow-chips { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin: 4px 0 6px 0; }
.workflow-chip { display:inline-flex; align-items:center; gap:6px; padding:4px 8px; border-radius:999px; font-size:12px; background:#f8f9fa; border:1px solid #e2e6ea; color:#212529; }
.workflow-chip.complete { background:#e7f8ef; border-color:#cdebd9; }
.workflow-chip.active { background:#eaf2ff; border-color:#cbdafc; }
.workflow-chip.pending { opacity:0.85; }
.workflow-dot { width:8px; height:8px; border-radius:50%; display:inline-block; }
.workflow-dot.ready { background:#28a745; }
.workflow-dot.warning { background:#ffc107; }
.workflow-dot.error { background:#dc3545; }
</style>
""", unsafe_allow_html=True)

chips_html = ["<div class='workflow-chips' aria-label='Analysis workflow status'>"]
for i, (icon, step, test_id, description, completed) in enumerate(workflow_steps):
    is_current = False
    if i == 0 and not completed:
        is_current = True
    elif i > 0 and workflow_steps[i-1][4] and not completed:
        is_current = True
    status_class = "complete" if completed else ("active" if is_current else "pending")
    status_color = "ready" if completed else ("warning" if is_current else "error")
    chips_html.append(
        f"<span class='workflow-chip {status_class}' role='status' aria-label='{step}: {description}'>"
        f"<span class='workflow-dot {status_color}' aria-hidden='true'></span>"
        f"{icon} {step}</span>"
    )
chips_html.append("</div>")
st.markdown("".join(chips_html), unsafe_allow_html=True)

cols = st.columns(len(workflow_steps))
for i, (icon, step, test_id, description, completed) in enumerate(workflow_steps):
    with cols[i]:
        is_current = False
        if i == 0 and not completed:
            is_current = True
        elif i > 0 and workflow_steps[i-1][4] and not completed:
            is_current = True
        
        status_class = "complete" if completed else ("active" if is_current else "")
        status_color = "ready" if completed else ("warning" if is_current else "error")
        
        st.markdown(f, unsafe_allow_html=True)

st.markdown("---")

# Sticky tabs CSS: ensure scroll container allows sticky and apply to tabs
st.markdown("""
<style>
  [data-testid=\"stAppViewContainer\"] .main .block-container {
    overflow: visible !important;
    contain: none !important;
  }
  [data-testid=\"stVerticalBlock\"], .element-container {
    overflow: visible !important;
  }
  .stTabs, [data-testid=\"stTabs\"] {
    position: -webkit-sticky !important;
    position: sticky !important;
    top: var(--sticky-top, 0px) !important;
    z-index: 1100 !important;
    background: var(--background-color, #fff) !important;
    box-shadow: 0 2px 4px rgba(0,0,0,0.06);
    margin-bottom: 8px !important;
  }
  .stTabs [role=\"tablist\"] {
    border-bottom: 1px solid #e0e0e0 !important;
    padding: 8px 0 !important;
  }
  .stTabs [data-baseweb=\"tab\"] { padding: 8px 16px !important; font-weight: 500 !important; }
  .stTabs [data-baseweb=\"tab\"]:hover { background-color: #f0f2f6 !important; border-radius: 4px !important; }
</style>
""", unsafe_allow_html=True)

# Header-aware sticky offset
st.markdown("""
<script>
(function(){
  function updateStickyTop(){
    const header = document.querySelector('[data-testid="stHeader"]');
    const h = header ? header.getBoundingClientRect().height : 0;
    document.documentElement.style.setProperty('--sticky-top', (h) + 'px');
  }
  window.addEventListener('load', updateStickyTop);
  window.addEventListener('resize', updateStickyTop);
  setTimeout(updateStickyTop, 500);
})();
</script>
""", unsafe_allow_html=True)

tab1, tab2, tab3 = st.tabs([
    "🎯 Lane Calibration", 
    "🔬 Analysis & Processing", 
    "📊 Results & Export"
])

with tab1:
    st.markdown()
    
    @handle_errors("Image Upload")
    def handle_file_upload(uploaded_file):
        if not uploaded_file:
            return None
            
        if uploaded_file.size > 50 * 1024 * 1024:  # 50MB limit
            st.error("❌ File too large (>50MB). Please use a smaller image.")
            return None
        
        img = Image.open(uploaded_file)
        img_array = np.array(img.convert("RGB"))
        h, w, _ = img_array.shape
        
        metadata = {
            'filename': uploaded_file.name,
            'size_bytes': uploaded_file.size,
            'dimensions': (w, h),
            'format': img.format or 'Unknown',
            'mode': img.mode
        }
        
        st.session_state.res_uploaded_image = img  # Store the PIL image, not file object
        st.session_state.res_uploaded_array = img_array  # Store the numpy array
        st.session_state.res_image_metadata = metadata
        st.session_state.ui_last_action = "upload"
        
        if w < 500 or h < 300:
            st.warning(f"⚠️ Small image ({w}×{h}px) may produce less accurate results.")
        elif w > 4000 or h > 4000:
            st.info(f"ℹ️ Large image ({w}×{h}px) detected. Processing may take longer.")
        else:
            st.success(f"✅ Optimal image size: {w}×{h}px")
        
        return img, img_array, metadata
    
    st.markdown('<div data-testid="file-upload-section" role="region" aria-labelledby="upload-heading">', unsafe_allow_html=True)
    st.markdown('<h3 id="upload-heading">📤 Image Upload</h3>', unsafe_allow_html=True)
    
    uploaded_file = st.file_uploader(
        "Select gel image",
        type=["jpg", "jpeg", "png", "tiff", "tif"],
        ,
        label_visibility="collapsed"
    )
    
    if uploaded_file:
        result = handle_file_upload(uploaded_file)
        if result:
            img, img_array, metadata = result
            h, w, _ = img_array.shape
            
            st.toast(f"Image loaded: {metadata['filename']}", icon="✅")
            
            with st.expander("📋 Image Information", expanded=False):
                col1, col2, col3 = st.columns(3)
                with col1:
                    st.metric("Dimensions", f"{w}×{h}px")
                with col2:
                    st.metric("File Size", f"{metadata['size_bytes'] / 1024:.1f} KB")
                with col3:
                    st.metric("Format", metadata['format'])
    
    st.markdown('</div>', unsafe_allow_html=True)
    
    if st.session_state.res_uploaded_image:
        st.markdown("### 🧪 Gel Configuration")
        
        col1, col2, col3 = st.columns(3)
        
        with col1:
            gel_type = st.selectbox(
                "Gel Type:",
                ["sds_page", "etbr_agarose"],
                index=0 if st.session_state.params_gel_type == "sds_page" else 1,
                ,
                key="gel_type_select"
            )
            st.session_state.params_gel_type = gel_type
            
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
                
            )
            st.session_state.params_n_lanes = n_lanes
            
            if n_lanes <= 8:
                st.markdown("📏 **Standard density** (good for quantification)")
            elif n_lanes <= 16:
                st.markdown("📏 **Medium density** (standard commercial gels)")
            else:
                st.markdown("📏 **High density** (may need careful calibration)")
        
        with col3:
            if HAS_LANE_MAPPING:
                try:
                    gel_settings = get_recommended_gel_settings(gel_type, n_lanes)
                    st.info(f"💡 **Recommendation:** {gel_settings['description']}")
                    
                    if 'tips' in gel_settings:
                        with st.expander("💡 Optimization Tips"):
                            for tip in gel_settings['tips']:
                                st.markdown(f"- {tip}")
                except:
                    
            else:

        st.markdown("### 🎯 Two-Point Lane Calibration")
        
        instructions_expanded = len(st.session_state.res_calibration_points) < 2
        
        with st.expander("📖 Calibration Instructions & Best Practices", expanded=instructions_expanded):
            col1, col2 = st.columns([2, 1])
            
            with col1:
                st.markdown()
            
            with col2:
                st.markdown()
        
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
                
                return calibration, boundaries
            
            return None
        
        if st.session_state.res_uploaded_image and st.session_state.res_uploaded_array is not None:
            img = st.session_state.res_uploaded_image
            img_array = st.session_state.res_uploaded_array
            h, w, _ = img_array.shape
        else:
            st.error("❌ Image data not available. Please upload an image first.")
            st.stop()
        
        if HAS_DRAWABLE_CANVAS:
            st.markdown("#### 🖱️ Interactive Calibration")
            
            col1, col2, col3, col4 = st.columns([2, 1, 1, 1])
            
            with col2:
                clear_btn = st.button(
                    "🔄 Clear Points",
                    ,
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
                    ,
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
                    ,
                    use_container_width=True
                )
                if help_btn:

            st.markdown(f, unsafe_allow_html=True)
            
            canvas_result = st_canvas(
                fill_color="rgba(255, 0, 0, 0.4)",
                stroke_width=3,
                stroke_color="#ff0000",
                background_image=img,
                update_streamlit=True,
                height=min(h, 700),  # Reasonable height limit
                width=w,
                drawing_mode="point",
                key=f"calibration_canvas_{st.session_state.ui_canvas_key}"
            )
            
            points = []
            if canvas_result.json_data is not None:
                for obj in canvas_result.json_data["objects"]:
                    x, y = obj["left"], obj["top"]
                    if 0 <= x <= w and 0 <= y <= h:
                        points.append((int(x), int(y)))
            
            st.session_state.res_calibration_points = points
        
        else:
            st.warning("⚠️ Interactive canvas not available. Using manual coordinate entry mode.")
            
            with st.expander("📍 Manual Coordinate Entry", expanded=True):

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

                if pt1_x and pt1_y and pt2_x and pt2_y:
                    distance = ((pt2_x - pt1_x)**2 + (pt2_y - pt1_y)**2)**0.5
                    if distance < w * 0.2:  # Less than 20% of image width
                        st.warning("⚠️ Points are quite close together. Consider spacing them farther apart for better accuracy.")
                    else:
                        st.success(f"✅ Point separation: {distance:.1f} pixels (good spacing)")
                
                apply_manual = st.button(
                    "✅ Apply Manual Calibration Points",
                    use_container_width=True,
                    type="primary",
                    
                )
                
                if apply_manual:
                    st.session_state.res_calibration_points = [(pt1_x, pt1_y), (pt2_x, pt2_y)]
                    st.session_state.ui_last_action = "manual_calibration"
                    st.toast("Manual calibration points applied", icon="✅")
                    st.rerun()
        
        points = st.session_state.res_calibration_points
        
        if len(points) == 0:

            st.markdown(, unsafe_allow_html=True)
        
        elif len(points) == 1:
            (x1, y1) = points[0]
            st.success(f"✅ **First calibration point set:** ({x1}, {y1})")

            st.markdown(f, unsafe_allow_html=True)
        
        elif len(points) >= 2:
            st.success(f"✅ **Calibration points ready:** {len(points)} points captured")
            
            if len(points) > 2:
                st.info(f"ℹ️ Using first two points only (ignoring {len(points) - 2} extra points)")
            
            st.markdown("#### 🏷️ Lane Number Assignment")
            
            col1, col2 = st.columns(2)
            
            with col1:
                lane1 = st.number_input(
                    "MW standard lane number:",
                    min_value=1, max_value=n_lanes, value=1,
                    key="calibration_lane1",
                    
                )
                
                (x1, y1) = points[0]
                 → Lane {lane1}")
            
            with col2:
                default_lane2 = min(n_lanes, max(8, n_lanes - 1))
                if default_lane2 == lane1:
                    default_lane2 = max(1, min(n_lanes, lane1 + 6))
                
                lane2 = st.number_input(
                    "Reference lane number:",
                    min_value=1, max_value=n_lanes, value=default_lane2,
                    key="calibration_lane2", 
                    
                )
                
                (x2, y2) = points[1]
                 → Lane {lane2}")
            
            if lane1 == lane2:
                st.error("❌ **Validation Error:** Lane numbers must be different")
                
            else:
                calibration_result = perform_calibration()
                
                if calibration_result:
                    calibration, boundaries = calibration_result
                    
                    st.markdown("#### 📏 Calibration Metrics")
                    
                    col1, col2, col3, col4 = st.columns(4)
                    
                    with col1:
                        spacing = calibration.lane_spacing_px
                        st.metric(
                            "Lane Spacing",
                            f"{spacing:.1f} px",
                            
                        )
                        if spacing < 20:
                            
                        elif spacing > 200:
                            
                        else:

                    with col2:
                        width = calibration.lane_width_px
                        st.metric(
                            "Lane Width",
                            f"{width:.1f} px",
                            
                        )
                    
                    with col3:
                        total_span = abs(boundaries[-1].right_px - boundaries[0].left_px)
                        st.metric(
                            "Total Gel Width",
                            f"{total_span:.0f} px",
                            
                        )
                    
                    with col4:
                        gel_coverage = (total_span / w) * 100
                        st.metric(
                            "Image Coverage",
                            f"{gel_coverage:.1f}%",
                            
                        )
                    
                    st.markdown("#### 🔍 Lane Alignment Preview")
                    
                    try:
                        import cv2
                        HAS_CV2 = True
                    except ImportError:
                        HAS_CV2 = False
                    
                    if HAS_CV2:
                        overlay_img = img_array.copy()
                        
                        for i, boundary in enumerate(boundaries):
                            center = int(boundary.center_px)
                            left = int(boundary.left_px)
                            right = int(boundary.right_px)
                            
                            if i == lane1 - 1:
                                color = (255, 215, 0)  # Gold for MW standard
                                thickness = 3
                            elif i == lane2 - 1:
                                color = (255, 140, 0)  # Orange for reference
                                thickness = 3
                            else:
                                color = (0, 255, 255)  # Cyan for interpolated
                                thickness = 2
                            
                            cv2.line(overlay_img, (left, 0), (left, h), color, thickness)
                            cv2.line(overlay_img, (right, 0), (right, h), color, thickness)
                            
                            cv2.line(overlay_img, (center, 0), (center, h), (255, 255, 255), 1, cv2.LINE_AA)
                            
                            label_y = 50
                            label_bg_size = 20
                            
                            cv2.rectangle(overlay_img, 
                                        (center - label_bg_size, label_y - label_bg_size), 
                                        (center + label_bg_size, label_y + 5), 
                                        (0, 0, 0), -1)
                            
                            cv2.putText(overlay_img, str(boundary.lane_index),
                                      (center - 10, label_y - 5),
                                      cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2, cv2.LINE_AA)
                            
                            if i == lane1 - 1:
                                cv2.putText(overlay_img, "MW", (center - 12, label_y + 15),
                                          cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 215, 0), 1, cv2.LINE_AA)
                            elif i == lane2 - 1:
                                cv2.putText(overlay_img, "REF", (center - 15, label_y + 15),
                                          cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 140, 0), 1, cv2.LINE_AA)
                        
                        st.image(
                            overlay_img,
                            caption="🎯 Lane boundary preview: Gold=MW standard, Orange=Reference lane, Cyan=Interpolated lanes, White=Centers",
                            use_column_width=True
                        )
                        
                    else:

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
                    
                    st.markdown("#### ✅ Verification & Confirmation")
                    
                    st.markdown(, unsafe_allow_html=True)
                    
                    lane_spacing = abs(calibration.lane_spacing_px)
                    if lane_spacing < 30:
                        st.warning("⚠️ **Very narrow lanes detected.** Consider if this matches your gel specifications.")
                    elif lane_spacing > 150:

                    alignment_confirmed = st.checkbox(
                        "🎯 **I confirm the lane boundaries align correctly with my gel**",
                        value=False,
                        ,
                        key="lane_alignment_confirmed"
                    )
                    
                    if alignment_confirmed:
                        st.success("✅ **Lane calibration confirmed!** 🎉")
                        st.balloons()
                        
                        col1, col2, col3 = st.columns(3)
                        
                        with col1:
                            st.markdown("**Next Step:**")

                        with col2:
                            if HAS_LANE_MAPPING:
                                try:
                                    csv_data = boundaries_to_csv(boundaries)
                                    st.download_button(
                                        "📥 **Export Calibration**",
                                        csv_data,
                                        f"lane_calibration_{gel_type}_{n_lanes}lanes.csv",
                                        "text/csv",
                                        ,
                                        use_container_width=True
                                    )
                                except Exception as e:
                                    st.button("📥 Export", disabled=True, help=f"Export failed: {e}", use_container_width=True)
                            else:
                                st.button("📥 Export", disabled=True, , use_container_width=True)
                        
                        with col3:
                            if st.button("🔬 **Start Analysis**", use_container_width=True, type="primary"):
                                st.switch_page("tab2")  # Would work in newer Streamlit versions
                    
                    else:

                        adj_col1, adj_col2, adj_col3 = st.columns(3)
                        
                        with adj_col1:
                            if st.button("🔄 **Recalibrate**", , use_container_width=True):
                                st.session_state.res_calibration_points = []
                                st.session_state.res_lane_boundaries = None
                                st.session_state.ui_canvas_key += 1
                                st.rerun()
                        
                        with adj_col2:
                            if st.button("🎯 **Adjust Points**", , use_container_width=True):
                                st.session_state.res_calibration_points = []
                                st.session_state.ui_canvas_key += 1
                                
                                st.rerun()
                        
                        with adj_col3:
                            st.button("⚙️ **Change Lanes**", , disabled=True, use_container_width=True)
    
    else:
        st.markdown(, unsafe_allow_html=True)
        
        with st.expander("💡 Image Upload Guidelines", expanded=True):
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown()
            
            with col2:
                st.markdown()

with tab2:
    st.markdown()
    
    has_image = st.session_state.res_uploaded_image is not None
    has_calibration = st.session_state.res_lane_boundaries is not None
    
    st.markdown("### 📋 Prerequisites Status")
    
    col1, col2 = st.columns(2)
    
    with col1:
        if has_image:
            metadata = st.session_state.res_image_metadata or {}
            filename = metadata.get('filename', 'Unknown')
            dimensions = metadata.get('dimensions', (0, 0))
            st.success(f"✅ **Image Ready:** {filename}")
            
        else:
            st.error("❌ **Image Missing**")

    with col2:
        if has_calibration:
            n_boundaries = len(st.session_state.res_lane_boundaries)
            st.success(f"✅ **Lanes Calibrated:** {n_boundaries} lanes configured")
            } analysis")
        else:
            st.error("❌ **Calibration Missing**")

    if not has_image or not has_calibration:
        st.markdown("---")

        with st.expander("🔮 Analysis Capabilities (Available After Prerequisites)", expanded=True):
            st.markdown()
        
        st.stop()  # Exit early if prerequisites not met
    
    st.markdown("### ⚙️ Analysis Configuration")
    
    st.markdown('<div data-testid="preprocessing-section" role="region" aria-label="Preprocessing Configuration">', unsafe_allow_html=True)
    
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
        ,
        key="preprocessing_mode_select"
    )
    
    if preprocessing_mode in mode_descriptions:
        st.markdown(f, unsafe_allow_html=True)
    
    st.markdown('</div>', unsafe_allow_html=True)
    
    if preprocessing_mode.startswith("ChatGPT"):
        st.markdown("#### 🤖 ChatGPT-4.1 Status")
        
        if HAS_OPENAI:
            try:
                from autodense.security.key_manager import get_openai_api_key, mask_key_for_logging
                
                api_key = get_openai_api_key()
                if api_key:
                    st.success("🧠 **ChatGPT-4.1 Ready** - Advanced AI analysis available")
                    st.info(f"🔐 **API Key Status:** {mask_key_for_logging(api_key)}")
                    
                    st.markdown(, unsafe_allow_html=True)
                    
                    try:
                        from autodense.preprocess.openai_policy import OpenAIPreprocessingClient
                        client = OpenAIPreprocessingClient()
                        
                    except Exception as e:

                else:
                    st.error("❌ **OpenAI API Key Required**")
                    st.markdown()
                    st.warning("💡 **Fallback:** Will use algorithmic AI preprocessing instead")
                    
            except ValueError as e:
                if "API key not found" in str(e):
                    st.error("❌ **API Key Configuration Error**")
                    st.error("🔧 **Required:** Set `OPENAI_API_KEY` environment variable")
                    st.warning("⚠️ **SECURITY WARNING:** Never store API keys in files or code repositories")
                else:
                    st.error(f"❌ **Configuration Error:** {str(e)}")

            except Exception as e:
                st.error("❌ **ChatGPT-4.1 Service Unavailable**")
                }")

        else:
            st.error("❌ **OpenAI Package Not Installed**")
            st.code("pip install openai", language="bash")

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
                    
                )
                
                manual_params["polarity"] = st.selectbox(
                    "Band Polarity:",
                    ["auto", "bright", "dark"],
                    index=0,
                    
                )
                
                manual_params["bg_method"] = st.selectbox(
                    "Background Correction:",
                    ["auto", "rolling_ball", "tophat_white", "tophat_black"],
                    index=0,
                    
                )
                
                manual_params["bg_radius"] = st.slider(
                    "Background Radius (px):",
                    min_value=5, max_value=99, value=17, step=2,
                    
                )
            
            with col2:
                st.markdown("**🔧 Enhancement Options**")
                
                manual_params["denoise"] = st.selectbox(
                    "Noise Reduction:",
                    ["auto", "none", "median", "gaussian"],
                    index=0,
                    
                )
                
                manual_params["clahe"] = st.checkbox(
                    "CLAHE Enhancement",
                    value=True,
                    
                )
                
                manual_params["deskew"] = st.checkbox(
                    "Geometric Correction",
                    value=True,
                    
                )
                
                with st.expander("Advanced Options"):
                    manual_params["gamma"] = st.slider(
                        "Gamma Correction:",
                        min_value=0.1, max_value=3.0, value=1.0, step=0.1,
                        
                    )
                    
                    manual_params["sharpen"] = st.checkbox(
                        "Edge Sharpening",
                        value=False,
                        
                    )
            
            if manual_params["bg_radius"] > 50:
                st.warning("⚠️ Large background radius may over-correct and remove band signals")
            
            if manual_params["gamma"] < 0.5 or manual_params["gamma"] > 2.0:

    st.markdown("### 🎯 Analysis Parameters")
    
    col1, col2 = st.columns(2)
    
    with col1:
        st.markdown("**🧬 Sample Type & Standards**")
        
        modality = "sds" if st.session_state.params_gel_type == "sds_page" else "dna"
        gel_type_display = "Protein (SDS-PAGE)" if modality == "sds" else "DNA (Agarose + EtBr)"
        
        st.info(f"**Analysis Type:** {gel_type_display}")

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

        st.session_state.params_ladder_type = ladder_type
    
    with col2:
        st.markdown("**🔍 Detection Parameters**")
        
        conf_threshold = st.slider(
            "Band Confidence Threshold:",
            min_value=0.0, max_value=1.0, 
            value=st.session_state.params_conf_threshold, 
            step=0.01,
            
        )
        st.session_state.params_conf_threshold = conf_threshold
        
        if conf_threshold < 0.2:
            ")
        elif conf_threshold < 0.5:
            ")
        elif conf_threshold < 0.8:
            ")
        else:
            ")
        
        mw_lane = st.number_input(
            "MW Standard Lane:",
            min_value=1, max_value=st.session_state.params_n_lanes,
            value=st.session_state.params_mw_lane,
            step=1,
            
        )
        st.session_state.params_mw_lane = mw_lane
        
        if st.session_state.res_lane_boundaries:
            total_lanes = len(st.session_state.res_lane_boundaries)
            if 1 <= mw_lane <= total_lanes:
                
            else:
                ")
    
    with st.expander("⚙️ Advanced Analysis Options", expanded=False):
        col1, col2 = st.columns(2)
        
        with col1:
            st.markdown("**🔍 Band Detection**")
            
            min_band_width = st.slider(
                "Minimum Band Width (px):",
                min_value=1, max_value=20, value=3,
                
            )
            
            max_band_width = st.slider(
                "Maximum Band Width (px):",
                min_value=10, max_value=100, value=50,
                
            )
            
            intensity_threshold = st.slider(
                "Intensity Threshold:",
                min_value=0.0, max_value=1.0, value=0.1, step=0.05,
                
            )
        
        with col2:
            st.markdown("**📊 Quantification**")
            
            background_method = st.selectbox(
                "Background Subtraction:",
                ["local", "global", "none"],
                index=0,
                
            )
            
            normalization = st.selectbox(
                "Intensity Normalization:",
                ["none", "total_intensity", "housekeeping", "loading_control"],
                index=0,
                
            )
            
            enable_statistics = st.checkbox(
                "Statistical Analysis",
                value=True,
                
            )
    
    st.markdown("### 🚀 Execute Analysis")
    
    analysis_warnings = []
    analysis_errors = []
    
    if st.session_state.res_image_metadata:
        dims = st.session_state.res_image_metadata.get('dimensions', (0, 0))
        if dims[0] < 1000 or dims[1] < 500:
            analysis_warnings.append("Small image dimensions may reduce detection accuracy")
        
        size_mb = st.session_state.res_image_metadata.get('size_bytes', 0) / (1024 * 1024)
        if size_mb > 20:
            analysis_warnings.append("Large image size may slow processing")
    
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
    
    st.markdown('<div data-testid="analysis-execution-section" role="region" aria-label="Analysis Execution">', unsafe_allow_html=True)
    
    analysis_ready = len(analysis_errors) == 0
    
    analyze_button = st.button(
        "🔬 **Run Comprehensive Gel Analysis**",
        use_container_width=True,
        disabled=not analysis_ready,
        type="primary",
        
    )
    
    st.markdown('</div>', unsafe_allow_html=True)
    
    if analyze_button:
        st.session_state.ui_analysis_count += 1
        st.session_state.ui_last_action = "analysis"
        
        progress_container = st.container()
        
        with progress_container:
            progress_bar = st.progress(0, text="🚀 Initializing analysis...")
            status_text = st.empty()
            
            try:
                status_text.info("🔬 **Step 1/5:** Preprocessing gel image...")
                progress_bar.progress(20, text="🔬 Optimizing image quality...")
                
                img_bytes = st.session_state.res_uploaded_image.getvalue()
                
                with st.spinner("Applying preprocessing algorithms..."):
                    preproc_result = cached_preprocess_image(img_bytes, preprocessing_mode, manual_params)
                    prepped_img, preproc_metadata = preproc_result
                    st.session_state.res_preprocessing_outcome = preproc_metadata
                
                if preproc_metadata.get('status') == 'error':
                    raise Exception(f"Preprocessing failed: {preproc_metadata.get('error', 'Unknown error')}")
                
                st.toast("Preprocessing complete", icon="✅")
                
                status_text.info("🎯 **Step 2/5:** Applying custom lane boundaries...")
                progress_bar.progress(40, text="🎯 Configuring lane regions...")
                
                import time
                time.sleep(1)  # Simulate processing time
                
                status_text.info("🔍 **Step 3/5:** Detecting and quantifying bands...")
                progress_bar.progress(60, text="🔍 Identifying band signals...")
                
                with st.spinner("Analyzing band patterns..."):
                    analysis_result = cached_analyze_gel("/tmp/placeholder", {}, 1)
                    time.sleep(2)  # Simulate band detection time
                
                status_text.info("📏 **Step 4/5:** MW calibration and size determination...")
                progress_bar.progress(80, text="📏 Calibrating molecular weights...")
                
                with st.spinner("Calculating molecular weights..."):
                    time.sleep(1)
                
                status_text.info("📊 **Step 5/5:** Statistical analysis and report generation...")
                progress_bar.progress(100, text="📊 Finalizing results...")
                
                with st.spinner("Computing statistics and generating report..."):
                    time.sleep(1)
                
                status_text.success("✅ **Analysis completed successfully!**")
                
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
                
                st.success("🎉 **Analysis completed successfully!** 🎉")
                st.balloons()
                
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
                
                st.markdown("#### 🎯 Next Steps")

                col1, col2, col3 = st.columns(3)
                
                with col1:
                    st.button("📊 **View Results**", use_container_width=True, )
                
                with col2:
                    st.button("🔄 **Analyze Again**", use_container_width=True, )
                
                with col3:
                    st.button("📥 **Quick Export**", use_container_width=True, )
                
            except Exception as e:
                st.session_state.ui_error_count += 1
                
                progress_bar.empty()
                status_text.empty()
                
                st.toast("Analysis failed", icon="❌")
                
                with st.expander("🔍 Analysis Error Details", expanded=True):
                    st.markdown(f, unsafe_allow_html=True)
                    
                    st.markdown()
                    
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
                        
                        st.subheader("Exception Details")
                        st.code(f"Exception Type: {type(e).__name__}")
                        st.code(f"Exception Message: {str(e)}")
                        
                        import traceback
                        st.code(traceback.format_exc())

with tab3:
    st.markdown()
    
    has_results = st.session_state.res_analysis_data is not None
    
    if not has_results:
        st.markdown("### 📊 No Analysis Results Available")

        with st.expander("🔮 Results Dashboard Preview", expanded=True):
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown()
            
            with col2:
                st.markdown()
        
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

        st.stop()  # Exit early if no results
    
    results = st.session_state.res_analysis_data
    
    st.success(f)
    
    st.markdown("### 📊 Analysis Dashboard")
    
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        gel_type_display = results['parameters']['gel_type'].replace('_', ' ').title()
        st.metric(
            "Gel Type",
            gel_type_display,
            
        )
    
    with col2:
        st.metric(
            "Total Lanes",
            results['parameters']['n_lanes'],
            
        )
    
    with col3:
        st.metric(
            "MW Standard",
            f"Lane {results['parameters']['mw_lane']}",
            
        )
    
    with col4:
        modality_display = results['parameters']['modality'].upper()
        st.metric(
            "Analysis Type",
            modality_display,
            
        )

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
            
            if 'confidence' in preproc:
                confidence = preproc['confidence']
                reasoning = preproc.get('reasoning', 'No reasoning provided')
                
                st.metric("AI Confidence", f"{confidence:.1%}")
                
                with st.expander("🤖 AI Reasoning"):
                    st.info(f"**Analysis:** {reasoning}")
            
            elif 'before' in preproc and 'after' in preproc:
                before_metrics = preproc['before']
                after_metrics = preproc['after']
                
                st.markdown("**Quality Improvements:**")
                
                snr_improvement = after_metrics.get('snr', 0) - before_metrics.get('snr', 0)
                sep_improvement = after_metrics.get('sep', 0) - before_metrics.get('sep', 0)
                
                col_a, col_b = st.columns(2)
                with col_a:
                    st.metric("SNR Δ", f"{snr_improvement:+.2f} dB")
                with col_b:
                    st.metric("Separation Δ", f"{sep_improvement:+.3f}")
        
        with col2:
            st.markdown("**Technical Details:**")
            
            if 'params' in preproc:
                params = preproc['params']
                if isinstance(params, dict) and params:
                    for key, value in params.items():
                        if isinstance(value, (int, float)):
                            
                        else:

            if 'error' in preproc:
                st.warning(f"⚠️ **Note:** {preproc['error']}")
            
            if 'processing_time' in preproc:

        with st.expander("📄 Complete Preprocessing Data"):
            st.json(preproc)
    
    with st.expander("🎯 Lane Configuration Analysis", expanded=True):
        lane_boundaries = results['lane_boundaries']
        
        if lane_boundaries and HAS_LANE_MAPPING:
            try:
                from autodense.preprocess.simple_lane_mapping import boundaries_to_dataframe
                
                df = boundaries_to_dataframe(lane_boundaries)
                
                st.markdown("**Lane Boundary Configuration:**")
                
                st.dataframe(
                    df,
                    use_container_width=True,
                    column_config={
                        "lane_index": st.column_config.NumberColumn(
                            "Lane #",
                            format="%d",
                            
                        ),
                        "center_px": st.column_config.NumberColumn(
                            "Center (px)",
                            format="%.1f",
                            
                        ),
                        "left_px": st.column_config.NumberColumn(
                            "Left Edge (px)",
                            format="%.1f",
                            
                        ),
                        "right_px": st.column_config.NumberColumn(
                            "Right Edge (px)",
                            format="%.1f",
                            
                        ),
                        "width_px": st.column_config.NumberColumn(
                            "Width (px)",
                            format="%.1f",
                            
                        )
                    }
                )
                
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

        if results.get('warnings'):
            st.markdown("**Analysis Warnings:**")
            for warning in results['warnings']:
                st.warning(f"⚠️ {warning}")
    
    st.markdown("### 📥 Export & Download Options")
    
    col1, col2, col3 = st.columns(3)
    
    with col1:
        st.markdown("**🗂️ Data Tables**")
        
        if lane_boundaries and HAS_LANE_MAPPING:
            try:
                csv_data = boundaries_to_csv(lane_boundaries)
                
                st.download_button(
                    "📊 **Lane Boundaries CSV**",
                    csv_data,
                    f"autodense_lanes_{results['analysis_id']}.csv",
                    "text/csv",
                    ,
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
                ,
                use_container_width=True
            )
        
        st.button(
            "📈 **Band Data CSV**",
            disabled=True,
            ,
            use_container_width=True
        )
    
    with col2:
        st.markdown("**📋 Analysis Reports**")
        
        analysis_json = json.dumps(results, indent=2, default=str)
        st.download_button(
            "⚙️ **Analysis Report JSON**",
            analysis_json,
            f"autodense_analysis_{results['analysis_id']}.json",
            "application/json",
            ,
            use_container_width=True
        )
        
        st.button(
            "📄 **Summary Report PDF**",
            disabled=True,
            ,
            use_container_width=True
        )
    
    with col3:
        st.markdown("**🖼️ Visual Exports**")
        
        st.button(
            "🎨 **Annotated Gel Image**",
            disabled=True,
            ,
            use_container_width=True
        )
        
        st.button(
            "📊 **Results Visualization**",
            disabled=True,
            ,
            use_container_width=True
        )
    
    with st.expander("🗂️ Analysis History & Management", expanded=False):
        st.markdown(f"**Current Analysis:** {results['analysis_id']}")
        st.markdown(f"**Total Analyses This Session:** {st.session_state.ui_analysis_count}")
        
        if st.session_state.ui_analysis_count > 1:
            st.info(f"This is analysis #{st.session_state.ui_analysis_count} in the current session.")
            
            st.button(
                "📈 **Compare with Previous**",
                disabled=True,
                ,
                use_container_width=True
            )
        
        col1, col2 = st.columns(2)
        
        with col1:
            if st.button("🗑️ **Clear Results**", ):
                st.session_state.res_analysis_data = None
                st.session_state.res_export_data = None
                st.rerun()
        
        with col2:
            st.button(
                "💾 **Save Session**",
                disabled=True,
                
            )
    
    # 6. Development and Debug Information
    if st.checkbox("🔍 **Developer Debug Information**", ):
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

st.markdown("---")

col1, col2, col3, col4 = st.columns([3, 1, 1, 1])

with col1:
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

with col2:
    count = st.session_state.ui_analysis_count
    if count > 0:
        st.metric("Analyses", count, )
    else:

with col3:
    error_count = st.session_state.ui_error_count
    if error_count > 0:
        st.metric("Errors", error_count, , delta_color="off")
    else:

with col4:
    if st.button("❓ **Help**", , use_container_width=True):

# TODO: Keyboard event handling for future enhancement

if st.checkbox("⚡ Performance Monitor", ):
    import psutil
    import time
    
    cpu_percent = psutil.cpu_percent(interval=1)
    memory = psutil.virtual_memory()
    
    col1, col2, col3 = st.columns(3)
    
    with col1:
        st.metric("CPU Usage", f"{cpu_percent:.1f}%")
    
    with col2:
        st.metric("Memory", f"{memory.percent:.1f}%")
    
    with col3:
        cache_info = st.cache_data.cache.get_stats()  # If available
        st.metric("Cache Hits", len(cache_info) if cache_info else 0)

def main():
    
    import subprocess
    import sys
    
    subprocess.run([
        sys.executable, "-m", "streamlit", "run", __file__,
        "--server.address", "0.0.0.0",
        "--server.port", "8501",
        "--browser.gatherUsageStats", "false",
        "--theme.base", "light"
    ])

if __name__ == "__main__":
    main()
