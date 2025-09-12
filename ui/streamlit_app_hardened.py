#!/usr/bin/env python3
"""
Hardened file upload implementation based on research findings
"""

import streamlit as st
import io
from PIL import Image
import numpy as np

# Page configuration
st.set_page_config(
    page_title="AutoDense – Hardened Upload",
    page_icon="🔬",
    layout="wide"
)

st.write(f"Streamlit version: {st.__version__}")  # Expect 1.49.1

# Initialize session state
def init_session_state():
    if "u_key" not in st.session_state:
        st.session_state.u_key = "u1"
    if 'res_uploaded_image' not in st.session_state:
        st.session_state.res_uploaded_image = None
    if 'res_uploaded_array' not in st.session_state:
        st.session_state.res_uploaded_array = None
    if 'res_image_metadata' not in st.session_state:
        st.session_state.res_image_metadata = None

init_session_state()

def reset_uploader():
    st.session_state.u_key = "u2" if st.session_state.u_key == "u1" else "u1"
    st.session_state.res_uploaded_image = None
    st.session_state.res_uploaded_array = None
    st.session_state.res_image_metadata = None
    st.rerun()

st.title("🔬 AutoDense – Hardened File Upload")
st.markdown("Testing the hardened file upload pattern to resolve browse button issues")

# Hardened file uploader pattern
uploaded = st.file_uploader(
    "Upload gel image(s)", 
    type=["png", "jpg", "jpeg", "tiff", "tif"],
    accept_multiple_files=False,  # Single file for gel analysis
    key=st.session_state.u_key,
    help="Click 'Browse files' or drag & drop. ASCII filenames recommended."
)

if uploaded:
    # Guard against None and process file
    if uploaded is not None:
        try:
            # Read and process image
            img = Image.open(uploaded)
            img_array = np.array(img.convert("RGB"))
            h, w, _ = img_array.shape
            
            # Store in session state
            st.session_state.res_uploaded_image = img
            st.session_state.res_uploaded_array = img_array
            st.session_state.res_image_metadata = {
                'filename': uploaded.name,
                'size_bytes': uploaded.size,
                'dimensions': (w, h),
                'format': img.format or 'Unknown'
            }
            
            st.success(f"✅ File uploaded: {uploaded.name}")
            st.write(f"**Filename:** {uploaded.name}")
            st.write(f"**Dimensions:** {w} × {h} pixels")
            st.write(f"**File size:** {uploaded.size / 1024:.1f} KB")
            
            # Display image
            st.image(img, caption=f"Uploaded: {uploaded.name}", use_container_width=True)
            
            # Clear button
            if st.button("🔄 Clear file", on_click=reset_uploader):
                pass
                
        except Exception as e:
            st.error(f"❌ Error processing file: {str(e)}")
else:
    st.info("👆 Please upload a gel image using the browse button or drag & drop")

# Debug information
st.markdown("### 🔍 Debug Information")
debug_info = {
    'streamlit_version': st.__version__,
    'uploader_key': st.session_state.u_key,
    'uploaded_file_present': uploaded is not None,
    'session_image_present': st.session_state.res_uploaded_image is not None,
    'metadata': st.session_state.res_image_metadata
}
st.json(debug_info)

# Configuration check
st.markdown("### ⚙️ Configuration Check")
st.write("**Base URL Path:** Check if running behind proxy")
st.write("**XSRF Protection:** Verify CORS/XSRF settings")
st.write("**WebSocket Support:** Required for file uploads")

if st.button("🧪 Test Reset Mechanism"):
    reset_uploader()