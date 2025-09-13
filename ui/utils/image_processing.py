"""Pure image processing utilities for AutoDense.

This module contains standalone image processing functions with no Streamlit dependencies.
These functions are extracted from the main application for better organization and testability.
"""

import io
import base64
import hashlib
import numpy as np
from PIL import Image, ImageDraw
from typing import Tuple, Dict, Any


def standardize_image_size(pil_img: Image.Image, max_pixels: int = 1024*768) -> Image.Image:
    """Standardize image size to prevent OpenAI API timeouts."""
    width, height = pil_img.size
    total_pixels = width * height

    if total_pixels <= max_pixels:
        return pil_img

    # Calculate scaling factor to reduce to max_pixels
    scale_factor = (max_pixels / total_pixels) ** 0.5
    new_width = int(width * scale_factor)
    new_height = int(height * scale_factor)

    return pil_img.resize((new_width, new_height), Image.Resampling.LANCZOS)


def to_png_bytes(pil_img: Image.Image) -> bytes:
    """Convert PIL Image to PNG bytes."""
    buf = io.BytesIO()
    pil_img.save(buf, format="PNG")
    return buf.getvalue()


def img_to_data_url(pil_img: Image.Image, max_dimension: int = 1920) -> str:
    """Convert PIL image to data URL.

    Note: Images are now standardized at upload time to prevent ChatGPT API timeouts,
    so this function no longer needs to resize. However, max_dimension parameter is
    kept for backward compatibility.
    """
    # Images are already standardized at upload time, so no resizing needed
    # Convert directly to base64
    buf = io.BytesIO()
    pil_img.save(buf, format="PNG")
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")

    return f"data:image/png;base64,{b64}"


def process_uploaded_image(uploaded_file_bytes: bytes, filename: str) -> Tuple[Image.Image, np.ndarray, Dict[str, Any]]:
    """Single source of truth for image processing - avoids redundant conversions."""
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


def overlay_fallback(base: Image.Image, lanes, bands) -> Image.Image:
    """Create fallback overlay when OpenCV is not available."""
    im = base.convert("RGBA").copy()
    dr = ImageDraw.Draw(im, "RGBA")
    for ln in (lanes or []):
        x0, y0 = int(ln.get("x0", 0)), int(ln.get("y0", 0))
        x1, y1 = int(ln.get("x1", im.width)), int(ln.get("y1", im.height))
        dr.rectangle([(x0, y0), (x1, y1)], outline=(0, 255, 255, 128), width=2)
    for bd in (bands or []):
        x0, y0 = int(bd.get("x0", 0)), int(bd.get("y0", 0))
        x1, y1 = int(bd.get("x1", 50)), int(bd.get("y1", 20))
        dr.rectangle([(x0, y0), (x1, y1)], outline=(255, 255, 0, 200), width=1)
    return im.convert("RGB")