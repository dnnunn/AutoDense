"""Image Upload Component for AutoDense.

This component handles file upload, validation, and image standardization
for scientific gel electrophoresis analysis.
"""

import hashlib
import io
from typing import Optional, Tuple, Dict, Any, Callable, Union

import numpy as np
import streamlit as st
from PIL import Image
from streamlit.runtime.uploaded_file_manager import UploadedFile

from utils.image_processing import standardize_image_size
from utils.error_handling import handle_ui_errors
from utils.state_management import get_state_manager
from autodense_types import ImageMetadata, ImageTuple, ImageDimensions, FileBytes, FileHash




def render_image_upload(
    key_prefix: str = "main",
    max_file_size_mb: int = 50,
    show_metadata: bool = True,
    use_new_state_management: bool = False
) -> Optional[ImageTuple]:
    """
    Render image upload component with validation and standardization.

    Args:
        key_prefix: Unique prefix for session state keys to avoid conflicts
        max_file_size_mb: Maximum file size in MB (default: 50MB)
        show_metadata: Whether to show expanded metadata section
        use_new_state_management: Enable memory-optimized state management (experimental)

    Returns:
        Tuple of (PIL Image, numpy array, metadata dict) if successful upload,
        None otherwise

    Side Effects:
        Updates session state with standardized image data:
        - res_uploaded_image: PIL Image object
        - res_uploaded_array: numpy array
        - res_image_metadata: metadata dictionary
        - res_image_bytes: raw file bytes
        - ui_last_action: "upload"
    """

    @handle_ui_errors("Image Upload", show_details=True, return_value=None)
    def handle_file_upload(uploaded_file: Optional[UploadedFile]) -> Optional[ImageTuple]:
        if not uploaded_file:
            return None

        # Validate file size
        max_size_bytes: int = max_file_size_mb * 1024 * 1024
        if uploaded_file.size > max_size_bytes:
            st.error(f"❌ File too large (>{max_file_size_mb}MB). Please use a smaller image.")
            return None

        # Load and validate image
        file_bytes: FileBytes = uploaded_file.getvalue()
        image_hash: FileHash = hashlib.md5(file_bytes).hexdigest()[:8]

        # Check if this is the same image already loaded (avoid duplicate processing)
        if use_new_state_management:
            state_manager = get_state_manager()
            current_metadata = st.session_state.get('autodense_image_metadata')
            if (current_metadata and current_metadata.hash == image_hash):
                return None  # Same image, don't reprocess
        else:
            if ('res_uploaded_image' in st.session_state and
                st.session_state.res_image_metadata and
                st.session_state.res_image_metadata.get('hash') == image_hash):
                return None  # Same image, don't reprocess

        img_original: Image.Image = Image.open(io.BytesIO(file_bytes)).convert("RGB")

        # Apply upload-time image standardization
        original_size: ImageDimensions = img_original.size
        img: Image.Image = standardize_image_size(img_original)
        standardized_size: ImageDimensions = img.size
        img_array: np.ndarray = np.array(img)
        h: int
        w: int
        h, w, _ = img_array.shape

        # Store metadata including standardization info
        metadata: ImageMetadata = {
            'filename': uploaded_file.name,
            'size_bytes': uploaded_file.size,
            'original_dimensions': original_size,
            'standardized_dimensions': standardized_size,
            'dimensions': (w, h),  # Keep for backward compatibility
            'format': img_original.format or 'Unknown',
            'mode': img.mode,
            'hash': image_hash,
            'standardized': original_size != standardized_size
        }

        # Update session state - dual mode support
        if use_new_state_management:
            # Use new memory-optimized state manager
            state_manager = get_state_manager()
            image_data = state_manager.set_current_image(img, file_bytes, uploaded_file.name)

            # Show memory optimization info
            memory_stats = state_manager.get_memory_stats()
            st.info(f"🧠 Memory-optimized storage: {memory_stats['session_state_mb']:.1f}MB session state")
        else:
            # Use legacy direct session state storage
            st.session_state.res_uploaded_image = img  # Store the PIL image, not file object
            st.session_state.res_uploaded_array = img_array  # Store the numpy array
            st.session_state.res_image_metadata = metadata
            st.session_state.res_image_bytes = file_bytes

        st.session_state.ui_last_action = "upload"

        # Quality assessment with standardization info
        if metadata['standardized']:
            st.info(f"📏 Image standardized: {original_size[0]}×{original_size[1]}px → {w}×{h}px")
            st.success(f"✅ Standardized for optimal processing and ChatGPT compatibility")
        else:
            st.success(f"✅ Image size already optimal: {w}×{h}px")

        if w < 500 or h < 300:
            st.warning(f"⚠️ Small image ({w}×{h}px) may produce less accurate results.")
        elif original_size[0] > 4000 or original_size[1] > 4000:
            st.success(f"🚀 Large image optimized: {original_size[0]}×{original_size[1]}px → {w}×{h}px")

        return img, img_array, metadata

    # Render the component UI
    st.markdown('<div data-testid="file-upload-section" role="region" aria-labelledby="upload-heading">', unsafe_allow_html=True)
    st.markdown('<h3 id="upload-heading">📤 Image Upload</h3>', unsafe_allow_html=True)

    uploaded_file: Optional[UploadedFile] = st.file_uploader(
        "Select gel image",
        type=["jpg", "jpeg", "png", "tiff", "tif", "heic", "heif"],
        help="Supported: PNG, JPG, TIFF, HEIC/HEIF (install pillow-heif). Optimal size: 1000–4000px width, <50MB",
        label_visibility="collapsed",
        key=f"{key_prefix}_file_uploader"
    )

    result: Optional[ImageTuple] = None
    if uploaded_file:
        with st.spinner("📤 Processing uploaded image..."):
            st.info(f"🔄 Loading and validating image: {uploaded_file.name}")
            result = handle_file_upload(uploaded_file)

        if result:
            img: Image.Image
            img_array: np.ndarray
            metadata: ImageMetadata
            img, img_array, metadata = result
            h, w, _ = img_array.shape

            st.success(f"✅ Image successfully loaded: {metadata['filename']}")
            st.toast(f"Image loaded: {metadata['filename']}", icon="✅")

            # Announce to screen readers for accessibility
            try:
                from ..streamlit_autodense_app import announce_to_screen_reader
                announce_to_screen_reader(f"Image {metadata['filename']} loaded successfully", "assertive")
            except ImportError:
                pass  # Fallback if announce function not available

            # Display image metadata if requested
            if show_metadata:
                with st.expander("📋 Image Information", expanded=False):
                    col1, col2, col3 = st.columns(3)
                    with col1:
                        st.metric("Dimensions", f"{w}×{h}px")
                    with col2:
                        st.metric("File Size", f"{metadata['size_bytes'] / 1024:.1f} KB")
                    with col3:
                        st.metric("Format", metadata['format'])

                    # Show standardization info if applied
                    if metadata['standardized']:
                        st.markdown("**📏 Image Standardization Applied**")
                        col1, col2 = st.columns(2)
                        with col1:
                            st.write(f"Original: {metadata['original_dimensions'][0]}×{metadata['original_dimensions'][1]}px")
                        with col2:
                            st.write(f"Standardized: {metadata['standardized_dimensions'][0]}×{metadata['standardized_dimensions'][1]}px")

    st.markdown('</div>', unsafe_allow_html=True)

    return result