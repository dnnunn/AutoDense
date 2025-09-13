"""Image Upload Component for AutoDense.

This component handles file upload, validation, and image standardization
for scientific gel electrophoresis analysis.
"""

import hashlib
import io
from typing import Optional, Tuple, Dict, Any, Callable, Union
from functools import wraps

import numpy as np
import streamlit as st
from PIL import Image
from streamlit.runtime.uploaded_file_manager import UploadedFile

from utils.image_processing import standardize_image_size
from autodense_types import ImageMetadata, ImageTuple, ImageDimensions, FileBytes, FileHash


def handle_errors(operation_name: str) -> Callable[[Callable[..., Any]], Callable[..., Optional[Any]]]:
    """Decorator for consistent error handling with user feedback."""
    def decorator(func: Callable[..., Any]) -> Callable[..., Optional[Any]]:
        @wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> Optional[Any]:
            try:
                return func(*args, **kwargs)
            except Exception as e:
                if 'ui_error_count' not in st.session_state:
                    st.session_state.ui_error_count = 0
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
                return None
        return wrapper
    return decorator


def render_image_upload(
    key_prefix: str = "main",
    max_file_size_mb: int = 50,
    show_metadata: bool = True
) -> Optional[ImageTuple]:
    """
    Render image upload component with validation and standardization.

    Args:
        key_prefix: Unique prefix for session state keys to avoid conflicts
        max_file_size_mb: Maximum file size in MB (default: 50MB)
        show_metadata: Whether to show expanded metadata section

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

    @handle_errors("Image Upload")
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

        # Update session state
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
        result = handle_file_upload(uploaded_file)
        if result:
            img: Image.Image
            img_array: np.ndarray
            metadata: ImageMetadata
            img, img_array, metadata = result
            h, w, _ = img_array.shape

            st.toast(f"Image loaded: {metadata['filename']}", icon="✅")

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