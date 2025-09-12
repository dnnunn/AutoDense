# ui/streamlit_app_minimal.py - Focused version for debugging file upload
import streamlit as st
import json, io
import numpy as np
import pandas as pd
from PIL import Image
import time
import contextlib

# Optional advanced features
try:
    from streamlit_drawable_canvas import st_canvas
    HAS_DRAWABLE_CANVAS = True
except ImportError:
    HAS_DRAWABLE_CANVAS = False

# Page configuration
st.set_page_config(
    page_title="AutoDense – Minimal",
    page_icon="🔬",
    layout="wide",
    initial_sidebar_state="collapsed"
)

# Session state initialization
def init_session_state():
    defaults = {
        'ui_error_count': 0,
        'ui_canvas_key': 0,
        'res_uploaded_image': None,
        'res_uploaded_array': None,
        'res_image_metadata': None,
        'res_calibration_points': [],
    }
    
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value

init_session_state()

# Custom CSS and JavaScript to fix browse button
st.markdown("""
<style>
/* Fix for file uploader browse button */
.stFileUploader > div > div > button {
    pointer-events: auto !important;
    cursor: pointer !important;
    z-index: 1000 !important;
}

.stFileUploader > div > div {
    position: relative !important;
}

/* Ensure file input is properly positioned */
.stFileUploader input[type="file"] {
    position: absolute !important;
    opacity: 0 !important;
    width: 100% !important;
    height: 100% !important;
    cursor: pointer !important;
    z-index: 999 !important;
}
</style>

<script>
// Force refresh file uploader functionality
setTimeout(function() {
    const fileUploaders = document.querySelectorAll('.stFileUploader input[type="file"]');
    fileUploaders.forEach(function(uploader) {
        uploader.style.pointerEvents = 'auto';
        uploader.style.cursor = 'pointer';
    });
}, 1000);
</script>
""", unsafe_allow_html=True)

# Main interface
st.title("🔬 AutoDense – File Upload Test")

# File upload section
st.markdown("### 📤 Image Upload")

uploaded_file = st.file_uploader(
    "Select gel image",
    type=["jpg", "jpeg", "png", "tiff", "tif"],
    help="Supported formats: PNG, JPG, TIFF. Click 'Browse files' or drag & drop.",
    key="main_file_uploader",
    accept_multiple_files=False
)

if uploaded_file:
    st.success("✅ File uploaded successfully!")
    
    # Load and validate image
    img = Image.open(uploaded_file)
    img_array = np.array(img.convert("RGB"))
    h, w, _ = img_array.shape
    
    # Store in session state
    st.session_state.res_uploaded_image = img
    st.session_state.res_uploaded_array = img_array
    st.session_state.res_image_metadata = {
        'filename': uploaded_file.name,
        'size_bytes': uploaded_file.size,
        'dimensions': (w, h),
        'format': img.format or 'Unknown'
    }
    
    # Display image info
    st.write(f"**Filename:** {uploaded_file.name}")
    st.write(f"**Dimensions:** {w} × {h} pixels")
    st.write(f"**File size:** {uploaded_file.size / 1024:.1f} KB")
    
    # Show image
    st.image(img, caption="Uploaded Image", use_container_width=True)
    
    # Show canvas if available
    if HAS_DRAWABLE_CANVAS:
        st.markdown("### 🎯 Interactive Canvas Available")
        canvas_result = st_canvas(
            fill_color="rgba(255, 0, 0, 0.4)",
            stroke_width=3,
            stroke_color="#ff0000",
            background_image=img,
            drawing_mode="point",
            height=min(h, 600),
            width=min(w, 800),
            key="test_canvas"
        )
    else:
        st.warning("⚠️ Interactive canvas not available")

else:
    st.info("👆 Please upload a gel image to test the functionality")

# Debug info
st.markdown("### 🔍 Debug Information")
st.write("**Session State:**")
debug_info = {
    'uploaded_image': st.session_state.res_uploaded_image is not None,
    'uploaded_array': st.session_state.res_uploaded_array is not None,
    'image_metadata': st.session_state.res_image_metadata,
    'has_drawable_canvas': HAS_DRAWABLE_CANVAS
}
st.json(debug_info)