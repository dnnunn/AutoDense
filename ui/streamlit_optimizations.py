# ui/streamlit_optimizations.py
"""
Efficiency optimizations for AutoDense Streamlit UI.
Contains performance monitoring, caching, and memory management utilities.
"""

import streamlit as st
import time
import contextlib
import gc
import json
import hashlib
import numpy as np
from PIL import Image
import io

# OPTIMIZATION 1: Single image processing pipeline with better caching
@st.cache_data(persist=True, max_entries=1)
def process_uploaded_image(uploaded_file_bytes: bytes, filename: str):
    """Single source of truth for image processing - avoids redundant conversions"""
    img = Image.open(io.BytesIO(uploaded_file_bytes)).convert("RGB")
    img_array = np.asarray(img)
    
    # Generate hash for cache keys
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

# OPTIMIZATION 1: Improved preprocessing cache with better key strategy
@st.cache_data(
    show_spinner="🔬 Optimizing gel image...", 
    max_entries=10,  # Increased for better hit rate
    ttl=7200,  # 2 hours for longer sessions
    hash_funcs={"dict": lambda x: str(sorted(x.items())) if x else ""}
)
def cached_preprocess_image_optimized(image_hash: str, mode: str, manual_params_str: str = ""):
    """Improved caching with better key strategy - no longer processes image bytes repeatedly"""
    # Get image from session state (already processed once)
    if 'res_uploaded_image' not in st.session_state or st.session_state.res_image_metadata.get('hash') != image_hash:
        return None, {"status": "error", "error": "Image not found in session"}
    
    img = st.session_state.res_uploaded_image
    manual_params = json.loads(manual_params_str) if manual_params_str else None
    
    # Import here to avoid circular dependencies
    try:
        from autodense.preprocess.pipeline import PreprocParams, run as preproc_run
        from autodense.preprocess.policy import guarded_preprocess
        from autodense.preprocess.openai_policy import openai_guided_preprocess
        HAS_PREPROCESS = True
    except ImportError:
        HAS_PREPROCESS = False
    
    try:
        if mode.startswith("ChatGPT") and HAS_PREPROCESS:
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
            # Raw fallback
            arr = np.asarray(img.convert("L")).astype(np.float32)
            prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
            return prepped, {"mode": "Raw (no preprocessing)", "status": "fallback"}
    
    except Exception as e:
        # Return raw image on any preprocessing failure
        arr = np.asarray(img.convert("L")).astype(np.float32)
        prepped = (arr - arr.min())/(arr.max()-arr.min()+1e-6)
        return prepped, {"mode": "Fallback (preprocessing failed)", "error": str(e), "status": "error"}

# OPTIMIZATION 2: Vectorized lane metrics calculation
@st.cache_data(max_entries=20, ttl=1800)
def calculate_lane_metrics(lane_boundaries_data: list):
    """Efficient vectorized lane metrics calculation - replaces O(n) loops"""
    if len(lane_boundaries_data) < 2:
        return {'spacings': [], 'widths': [], 'min_spacing': 0, 'max_spacing': 0, 'mean_spacing': 0, 'spacing_cv': 0}
    
    # Convert to numpy arrays for vectorized operations
    centers = np.array([b['center_px'] if isinstance(b, dict) else b.center_px for b in lane_boundaries_data])
    widths = np.array([b['width_px'] if isinstance(b, dict) else b.width_px for b in lane_boundaries_data])
    spacings = np.abs(np.diff(centers))  # Vectorized spacing calculation
    
    return {
        'spacings': spacings.tolist(),
        'widths': widths.tolist(),
        'min_spacing': float(spacings.min()) if len(spacings) > 0 else 0,
        'max_spacing': float(spacings.max()) if len(spacings) > 0 else 0,
        'mean_spacing': float(spacings.mean()) if len(spacings) > 0 else 0,
        'spacing_cv': float(spacings.std() / spacings.mean()) if len(spacings) > 0 and spacings.mean() > 0 else 0
    }

# OPTIMIZATION 3: Progressive data loading for large results  
class ProgressiveLoader:
    """Load analysis data sections on-demand instead of all upfront"""
    def __init__(self, data_key: str):
        self.data_key = data_key
        self._cache = {}
    
    def get_section(self, section: str):
        if section not in self._cache:
            full_data = st.session_state.get(self.data_key, {})
            self._cache[section] = full_data.get(section, {})
        return self._cache[section]
    
    def clear_cache(self):
        self._cache.clear()

# OPTIMIZATION 4: Batch state updates to reduce st.rerun() calls
def batch_state_update(updates: dict, increment_keys: list = None):
    """Batch multiple state updates before rerun - reduces UI flicker"""
    for key, value in updates.items():
        st.session_state[key] = value
    
    for key in (increment_keys or []):
        st.session_state[key] = st.session_state.get(key, 0) + 1
    
    st.rerun()

# OPTIMIZATION 5: Memory management and performance monitoring
class PerformanceMonitor:
    """Monitor operation performance and manage memory efficiently"""
    def __init__(self):
        self.metrics = {}
    
    @contextlib.contextmanager
    def time_operation(self, operation_name: str):
        start = time.time()
        try:
            yield
        finally:
            duration = time.time() - start
            self.metrics[operation_name] = duration
            if duration > 2.0:  # Warn about slow operations
                st.toast(f"⏱️ {operation_name} took {duration:.1f}s", icon="⏱️")
    
    def clear_memory(self):
        """Force garbage collection to free memory"""
        gc.collect()
    
    def get_memory_usage(self):
        """Get basic memory metrics"""
        try:
            import psutil
            process = psutil.Process()
            return {
                'memory_mb': process.memory_info().rss / 1024 / 1024,
                'cpu_percent': process.cpu_percent()
            }
        except ImportError:
            return {'memory_mb': 0, 'cpu_percent': 0}

@st.cache_resource
def get_performance_monitor():
    return PerformanceMonitor()

# OPTIMIZATION 3: Efficient session state management
def get_analysis_state():
    """Single point of truth for analysis state - reduces session state lookups"""
    return {
        'uploaded_image': st.session_state.get('res_uploaded_image'),
        'image_metadata': st.session_state.get('res_image_metadata', {}),
        'lane_boundaries': st.session_state.get('res_lane_boundaries', []),
        'analysis_data': st.session_state.get('res_analysis_data'),
        'params': {
            'gel_type': st.session_state.get('params_gel_type', 'sds_page'),
            'n_lanes': st.session_state.get('params_n_lanes', 10),
            'conf_threshold': st.session_state.get('params_conf_threshold', 0.7),
            'mw_lane': st.session_state.get('params_mw_lane')
        }
    }

# OPTIMIZATION 5: Efficient error handling context manager
@contextlib.contextmanager
def error_context(operation_name: str):
    """Efficient error handling without creating UI components repeatedly"""
    try:
        yield
    except Exception as e:
        error_id = f"{operation_name}_{int(time.time())}"
        st.session_state[f'error_{error_id}'] = {
            'operation': operation_name,
            'error': str(e),
            'timestamp': time.time()
        }
        st.toast(f"{operation_name} failed", icon="❌")
        
        # Show error in expander
        with st.expander(f"🔍 {operation_name} Error Details", expanded=False):
            st.error(f"**{operation_name} failed:** {str(e)}")
            st.code(f"Error ID: {error_id}", language="text")