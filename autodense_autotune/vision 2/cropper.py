"""
Vision system cropper module with ROI detection and privacy scrubbing.

Implements Phase IV vision-assist functionality with bounded parameter optimization
while maintaining privacy and safety constraints.
"""

import numpy as np
from PIL import Image, ImageFilter, ImageDraw
import io
import base64
from typing import Dict, Any, Tuple, Optional
import logging
import cv2

logger = logging.getLogger(__name__)

class VisionCropper:
    """Handles image cropping, ROI detection, and privacy scrubbing for vision-assist mode."""
    
    def __init__(self, config: Dict[str, Any]):
        """
        Initialize cropper with vision configuration.
        
        Args:
            config: Vision configuration with max_side_px, scrub settings, etc.
        """
        self.config = config
        self.max_side_px = config.get('max_side_px', 1024)
        self.scrub_config = config.get('scrub', {})
        
    def process_for_vision(self, image_path: str, stage: str = "stage1_norm") -> Dict[str, Any]:
        """
        Process image for vision-assist analysis with privacy protection.
        
        Args:
            image_path: Path to input image
            stage: Processing stage ('stage0_input', 'stage1_norm', etc.)
            
        Returns:
            Dict with base64_image, dimensions, and processing metadata
        """
        try:
            # Load image and convert to RGB to ensure compatibility
            with Image.open(image_path) as img:
                img = img.convert('RGB')  # Ensure RGB format for processing
                original_size = img.size
                
                # Auto-detect ROI if configured
                if self.config.get('crop', 'auto') == 'auto':
                    roi_box = self._detect_analysis_roi(img)
                else:
                    roi_box = None
                    
                # Crop to ROI if detected
                if roi_box:
                    img = img.crop(roi_box)
                    crop_info = {
                        'roi_applied': True,
                        'roi_box': roi_box,
                        'original_size': original_size
                    }
                else:
                    crop_info = {
                        'roi_applied': False,
                        'original_size': original_size
                    }
                
                # Downscale for privacy/efficiency
                img = self._downscale_image(img)
                
                # Apply privacy scrubbing
                img = self._apply_privacy_scrubbing(img)
                
                # Convert to base64
                buffer = io.BytesIO()
                img.save(buffer, format='PNG')
                base64_data = base64.b64encode(buffer.getvalue()).decode('utf-8')
                
                return {
                    'base64_image': base64_data,
                    'dimensions': img.size,
                    'crop_info': crop_info,
                    'stage': stage,
                    'privacy_applied': self._get_privacy_summary(),
                    'ready_for_analysis': True
                }
                
        except Exception as e:
            logger.error(f"Vision processing failed for {image_path}: {e}")
            return {
                'error': str(e),
                'ready_for_analysis': False
            }
    
    def _detect_analysis_roi(self, img: Image.Image) -> Optional[Tuple[int, int, int, int]]:
        """
        Auto-detect the region of interest for analysis.
        
        For gels: detect gel boundaries
        For colonies: detect plate boundaries
        For general: detect high-contrast regions
        
        Returns:
            (left, top, right, bottom) box or None if full image should be used
        """
        # Convert to numpy for analysis
        img_array = np.array(img.convert('L'))
        
        # Simple edge-based ROI detection
        # Find significant gradient regions that likely contain analysis targets
        grad_x = cv2.Sobel(img_array, cv2.CV_64F, 1, 0, ksize=3)
        grad_y = cv2.Sobel(img_array, cv2.CV_64F, 0, 1, ksize=3)
        gradient_magnitude = np.sqrt(grad_x**2 + grad_y**2)
        
        # Threshold to find significant edges
        edge_threshold = np.percentile(gradient_magnitude, 85)
        edge_mask = gradient_magnitude > edge_threshold
        
        # Find bounding box of edge regions
        edge_coords = np.where(edge_mask)
        if len(edge_coords[0]) == 0:
            return None  # No significant edges found
            
        min_y, max_y = edge_coords[0].min(), edge_coords[0].max()
        min_x, max_x = edge_coords[1].min(), edge_coords[1].max()
        
        # Add padding and ensure reasonable bounds
        height, width = img_array.shape
        padding = min(width, height) // 20  # 5% padding
        
        left = max(0, min_x - padding)
        top = max(0, min_y - padding)
        right = min(width, max_x + padding)
        bottom = min(height, max_y + padding)
        
        # Only return ROI if it's significantly smaller than full image
        roi_area = (right - left) * (bottom - top)
        full_area = width * height
        
        if roi_area < 0.8 * full_area:  # ROI saves at least 20% of pixels
            return (left, top, right, bottom)
        else:
            return None  # ROI not worth it
    
    def _downscale_image(self, img: Image.Image) -> Image.Image:
        """
        Downscale image to max_side_px while preserving aspect ratio.
        
        Args:
            img: Input PIL Image
            
        Returns:
            Downscaled PIL Image
        """
        width, height = img.size
        max_dim = max(width, height)
        
        if max_dim <= self.max_side_px:
            return img
            
        # Calculate new dimensions
        scale = self.max_side_px / max_dim
        new_width = int(width * scale)
        new_height = int(height * scale)
        
        return img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    
    def _apply_privacy_scrubbing(self, img: Image.Image) -> Image.Image:
        """
        Apply privacy scrubbing based on configuration.
        
        Args:
            img: Input PIL Image
            
        Returns:
            Privacy-scrubbed PIL Image
        """
        # Strip EXIF data (PIL automatically removes it on save)
        
        # Apply grayscale for gels if configured
        if self.scrub_config.get('grayscale_gels', True):
            # Convert to grayscale for gel analysis (preserves structure, removes color info)
            img = img.convert('L').convert('RGB')
            
        # Blur text regions if configured
        if self.scrub_config.get('blur_text', True):
            img = self._blur_text_regions(img)
            
        return img
    
    def _blur_text_regions(self, img: Image.Image) -> Image.Image:
        """
        Detect and blur potential text regions for privacy.
        
        Args:
            img: Input PIL Image
            
        Returns:
            Image with blurred text regions
        """
        # Simple text detection: look for high-contrast rectangular regions
        # that might be labels, timestamps, or annotations
        
        img_array = np.array(img.convert('L'))
        
        # Detect high-contrast edges that form rectangular patterns
        edges = cv2.Canny(img_array, 50, 150)
        
        # Find contours that might be text boxes
        contours, _ = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        # Create mask for blurring
        blur_mask = Image.new('L', img.size, 0)
        draw = ImageDraw.Draw(blur_mask)
        
        for contour in contours:
            # Get bounding rectangle
            x, y, w, h = cv2.boundingRect(contour)
            
            # Filter for text-like aspect ratios and sizes
            aspect_ratio = w / h if h > 0 else 0
            
            # Text characteristics: moderate aspect ratio, not too large or small
            if (0.3 < aspect_ratio < 8.0 and 
                10 < w < img.size[0] // 3 and 
                8 < h < img.size[1] // 5):
                
                # Add padding for blur region
                padding = 5
                blur_x = max(0, x - padding)
                blur_y = max(0, y - padding)
                blur_w = min(img.size[0] - blur_x, w + 2 * padding)
                blur_h = min(img.size[1] - blur_y, h + 2 * padding)
                
                draw.rectangle([blur_x, blur_y, blur_x + blur_w, blur_y + blur_h], fill=255)
        
        # Apply gaussian blur to detected regions
        if np.any(np.array(blur_mask)):
            blurred_img = img.filter(ImageFilter.GaussianBlur(radius=3))
            
            # Composite: use blurred version where mask is white
            # Convert single channel mask to RGB
            blur_mask_rgb = blur_mask.convert('RGB')
            img = Image.composite(blurred_img, img, blur_mask)
            
        return img
    
    def _get_privacy_summary(self) -> Dict[str, Any]:
        """
        Get summary of privacy measures applied.
        
        Returns:
            Dictionary describing privacy protections applied
        """
        return {
            'exif_stripped': True,  # Always true with PIL
            'downscaled_to_px': self.max_side_px,
            'grayscale_applied': self.scrub_config.get('grayscale_gels', True),
            'text_regions_blurred': self.scrub_config.get('blur_text', True),
            'max_pixel_count': self.max_side_px * self.max_side_px
        }

def test_cropper():
    """Test the vision cropper with sample data."""
    config = {
        'max_side_px': 1024,
        'crop': 'auto',
        'scrub': {
            'strip_exif': True,
            'blur_text': True,
            'grayscale_gels': True
        }
    }
    
    cropper = VisionCropper(config)
    
    # Test with SDS gel
    result = cropper.process_for_vision('samples/sds_gel.jpg', 'stage1_norm')
    
    if result.get('ready_for_analysis'):
        print(f"✅ Cropper test passed")
        print(f"   Dimensions: {result['dimensions']}")
        print(f"   ROI applied: {result['crop_info']['roi_applied']}")
        print(f"   Privacy: {result['privacy_applied']}")
    else:
        print(f"❌ Cropper test failed: {result.get('error')}")

if __name__ == "__main__":
    test_cropper()