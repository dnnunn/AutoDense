# autodense/preprocess/geometric_adjustments.py
"""
Geometric adjustment tools for gel images: inset, deskew, and desmile corrections.
Provides user-controlled visual feedback for perfect lane alignment.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Tuple, List, Optional
import numpy as np
import cv2
from PIL import Image
from scipy.ndimage import rotate, map_coordinates

@dataclass
class GeometricParams:
    """Parameters for geometric adjustments"""
    inset_left: float = 0.0      # Pixels to inset from left
    inset_right: float = 0.0     # Pixels to inset from right  
    deskew_angle: float = 0.0    # Rotation angle in degrees
    desmile_factor: float = 0.0  # Smile correction factor (0 = no correction, 1 = full correction)

def apply_lane_inset(boundaries: List, inset_left: float, inset_right: float):
    """Apply asymmetric inset to lane boundaries"""
    adjusted_boundaries = []
    
    for boundary in boundaries:
        # Apply inset - reduce from left and right independently
        new_left = boundary.left_px + inset_left
        new_right = boundary.right_px - inset_right
        new_width = new_right - new_left
        
        # Ensure minimum width
        if new_width < 5.0:
            center = boundary.center_px
            new_left = center - 2.5
            new_right = center + 2.5
            new_width = 5.0
        
        # Create adjusted boundary
        from autodense.preprocess.simple_lane_mapping import SimpleLaneBoundary
        adjusted_boundaries.append(SimpleLaneBoundary(
            lane_index=boundary.lane_index,
            center_px=boundary.center_px,  # Center stays the same
            left_px=new_left,
            right_px=new_right,
            width_px=new_width
        ))
    
    return adjusted_boundaries

def apply_deskew_rotation(image: np.ndarray, angle_degrees: float) -> Tuple[np.ndarray, np.ndarray]:
    """
    Apply rotation to deskew gel image.
    
    Args:
        image: Input gel image array
        angle_degrees: Rotation angle in degrees (positive = clockwise)
    
    Returns:
        (rotated_image, transformation_matrix)
    """
    if abs(angle_degrees) < 0.1:
        return image, np.eye(3)
    
    # Get image dimensions
    if len(image.shape) == 3:
        h, w, c = image.shape
    else:
        h, w = image.shape
        c = 1
    
    # Calculate rotation matrix around image center
    center = (w // 2, h // 2)
    rotation_matrix = cv2.getRotationMatrix2D(center, -angle_degrees, 1.0)  # Negative for intuitive direction
    
    # Calculate new image dimensions to avoid clipping
    cos_theta = abs(rotation_matrix[0, 0])
    sin_theta = abs(rotation_matrix[0, 1])
    new_w = int(h * sin_theta + w * cos_theta)
    new_h = int(h * cos_theta + w * sin_theta)
    
    # Adjust rotation matrix to account for new image center
    rotation_matrix[0, 2] += (new_w - w) / 2
    rotation_matrix[1, 2] += (new_h - h) / 2
    
    # Apply rotation
    if len(image.shape) == 3:
        rotated = cv2.warpAffine(image, rotation_matrix, (new_w, new_h), 
                                flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, 
                                borderValue=(0, 0, 0))
    else:
        rotated = cv2.warpAffine(image, rotation_matrix, (new_w, new_h), 
                                flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, 
                                borderValue=0)
    
    # Create full 3x3 transformation matrix
    transform_matrix = np.eye(3)
    transform_matrix[:2, :] = rotation_matrix
    
    return rotated, transform_matrix

def apply_desmile_correction(image: np.ndarray, desmile_factor: float) -> Tuple[np.ndarray, np.ndarray]:
    """
    Apply desmile correction to straighten curved gel lanes.
    
    Args:
        image: Input gel image array
        desmile_factor: Correction strength (0.0 = no correction, 1.0 = full correction)
    
    Returns:
        (corrected_image, coordinate_mapping)
    """
    if abs(desmile_factor) < 0.01:
        return image, np.eye(3)
    
    if len(image.shape) == 3:
        h, w, c = image.shape
    else:
        h, w = image.shape
        c = 1
    
    # Create coordinate grids
    y_indices, x_indices = np.mgrid[0:h, 0:w].astype(np.float32)
    
    # Apply barrel/pincushion distortion correction
    # Normalize coordinates to [-1, 1] range
    x_norm = (x_indices - w/2) / (w/2)
    y_norm = (y_indices - h/2) / (h/2)
    
    # Calculate radial distance from center
    r_squared = x_norm**2 + y_norm**2
    
    # Apply smile correction (quadratic distortion)
    # Positive factor corrects "smile" (barrel distortion)
    # Negative factor corrects "frown" (pincushion distortion)
    distortion_factor = 1 + desmile_factor * r_squared
    
    # Apply distortion to coordinates
    x_corrected = x_norm * distortion_factor * (w/2) + w/2
    y_corrected = y_norm * distortion_factor * (h/2) + h/2
    
    # Ensure coordinates stay within image bounds
    x_corrected = np.clip(x_corrected, 0, w-1)
    y_corrected = np.clip(y_corrected, 0, h-1)
    
    # Apply correction using interpolation
    if len(image.shape) == 3:
        corrected = np.zeros_like(image)
        for channel in range(c):
            corrected[:, :, channel] = map_coordinates(
                image[:, :, channel], 
                [y_corrected, x_corrected], 
                order=1, 
                mode='constant', 
                cval=0
            )
    else:
        corrected = map_coordinates(
            image, 
            [y_corrected, x_corrected], 
            order=1, 
            mode='constant', 
            cval=0
        )
    
    # Create transformation matrix (approximation for reference)
    transform_matrix = np.eye(3)
    # This is a simplified representation - the actual transformation is non-linear
    transform_matrix[0, 0] = 1 + desmile_factor * 0.1  # Approximation
    transform_matrix[1, 1] = 1 + desmile_factor * 0.1
    
    return corrected.astype(image.dtype), transform_matrix

def apply_all_geometric_adjustments(
    image: np.ndarray, 
    boundaries: List,
    params: GeometricParams
) -> Tuple[np.ndarray, List, dict]:
    """
    Apply all geometric adjustments in the correct order.
    
    Args:
        image: Input gel image
        boundaries: Lane boundaries
        params: Geometric adjustment parameters
    
    Returns:
        (adjusted_image, adjusted_boundaries, transformation_info)
    """
    current_image = image.copy()
    current_boundaries = boundaries.copy()
    transformations = {}
    
    # Step 1: Apply deskew rotation
    if abs(params.deskew_angle) > 0.1:
        current_image, rotation_matrix = apply_deskew_rotation(current_image, params.deskew_angle)
        transformations['rotation'] = rotation_matrix
        
        # Transform boundary coordinates
        # This is simplified - in practice, you'd need to transform the boundary coordinates
        # through the rotation matrix as well
    
    # Step 2: Apply desmile correction
    if abs(params.desmile_factor) > 0.01:
        current_image, desmile_matrix = apply_desmile_correction(current_image, params.desmile_factor)
        transformations['desmile'] = desmile_matrix
    
    # Step 3: Apply lane insets (this doesn't transform the image, just the boundaries)
    if abs(params.inset_left) > 0.1 or abs(params.inset_right) > 0.1:
        current_boundaries = apply_lane_inset(current_boundaries, params.inset_left, params.inset_right)
        transformations['inset'] = {'left': params.inset_left, 'right': params.inset_right}
    
    return current_image, current_boundaries, transformations

def create_adjustment_overlay(
    image: np.ndarray, 
    boundaries: List,
    params: GeometricParams,
    show_grid: bool = False
) -> np.ndarray:
    """
    Create visual overlay showing the effects of geometric adjustments.
    
    Args:
        image: Adjusted gel image
        boundaries: Adjusted lane boundaries  
        params: Current adjustment parameters
        show_grid: Whether to show alignment grid
    
    Returns:
        Image array with overlay
    """
    overlay = image.copy()
    h, w = overlay.shape[:2]
    
    # Draw lane boundaries
    for boundary in boundaries:
        center = int(boundary.center_px)
        left = int(boundary.left_px)
        right = int(boundary.right_px)
        
        # Ensure coordinates are valid
        if left < 0 or right >= w or center < 0 or center >= w:
            continue
            
        # Draw boundaries with different colors based on adjustments
        boundary_color = (0, 255, 0)  # Green for normal
        if abs(params.inset_left) > 0.1 or abs(params.inset_right) > 0.1:
            boundary_color = (0, 255, 255)  # Cyan for inset
        
        cv2.line(overlay, (left, 0), (left, h), boundary_color, 2)
        cv2.line(overlay, (right, 0), (right, h), boundary_color, 2)
        cv2.line(overlay, (center, 0), (center, h), (255, 0, 0), 1)  # Red center
        
        # Lane numbers
        cv2.putText(overlay, str(boundary.lane_index), (center-10, 30), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
    
    # Optional alignment grid
    if show_grid:
        grid_color = (128, 128, 128)
        # Horizontal grid lines
        for y in range(0, h, h//10):
            cv2.line(overlay, (0, y), (w, y), grid_color, 1)
        
        # Vertical reference lines
        for x in range(0, w, w//20):
            cv2.line(overlay, (x, 0), (x, h), grid_color, 1)
    
    # Draw adjustment indicators
    if abs(params.deskew_angle) > 0.1:
        # Draw rotation indicator
        cv2.putText(overlay, f"Deskew: {params.deskew_angle:.1f}°", 
                   (10, h-60), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 0), 2)
    
    if abs(params.desmile_factor) > 0.01:
        # Draw desmile indicator
        cv2.putText(overlay, f"Desmile: {params.desmile_factor:.2f}", 
                   (10, h-40), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 0, 255), 2)
    
    if abs(params.inset_left) > 0.1 or abs(params.inset_right) > 0.1:
        # Draw inset indicator
        cv2.putText(overlay, f"Inset: L{params.inset_left:.0f} R{params.inset_right:.0f}", 
                   (10, h-20), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
    
    return overlay

def estimate_optimal_desmile(image: np.ndarray, boundaries: List) -> float:
    """
    Estimate optimal desmile correction by analyzing lane straightness.
    
    Args:
        image: Gel image array
        boundaries: Lane boundaries
    
    Returns:
        Suggested desmile correction factor
    """
    if not boundaries or len(image.shape) != 3:
        return 0.0
    
    # Convert to grayscale for analysis
    gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
    h, w = gray.shape
    
    # Analyze curvature in sample lanes (skip first/last which might be markers)
    curvature_scores = []
    
    for boundary in boundaries[1:-1]:  # Skip first and last lanes
        center = int(boundary.center_px)
        if center < 10 or center > w-10:
            continue
            
        # Extract vertical profile at lane center
        lane_profile = gray[:, max(0, center-5):min(w, center+5)].mean(axis=1)
        
        # Smooth the profile
        from scipy.ndimage import uniform_filter1d
        smoothed = uniform_filter1d(lane_profile, size=5)
        
        # Calculate second derivative (curvature)
        if len(smoothed) > 4:
            second_deriv = np.diff(smoothed, 2)
            curvature = np.mean(np.abs(second_deriv))
            curvature_scores.append(curvature)
    
    if not curvature_scores:
        return 0.0
    
    # Convert curvature to suggested desmile factor
    avg_curvature = np.mean(curvature_scores)
    
    # Empirical mapping (may need tuning based on real gel data)
    if avg_curvature > 10:
        return min(0.3, avg_curvature / 100.0)  # Positive for "smile"
    else:
        return 0.0