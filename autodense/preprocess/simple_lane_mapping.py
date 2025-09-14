# autodense/preprocess/simple_lane_mapping.py
"""
Simplified two-point lane calibration system for gel analysis.
User clicks two known band centers and specifies their lane numbers.
System automatically calculates all lane positions.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Dict, List, Tuple, Optional, Literal
import numpy as np
import pandas as pd

GelType = Literal["sds_page", "etbr_agarose"]

@dataclass
class TwoPointCalibration:
    """Two-point calibration data"""
    point1_x: float
    point1_lane: int
    point2_x: float  
    point2_lane: int
    total_lanes: int
    
    @property
    def lane_spacing_px(self) -> float:
        """Calculate lane spacing in pixels"""
        if self.point2_lane == self.point1_lane:
            return 1.0  # Avoid division by zero
        return (self.point2_x - self.point1_x) / (self.point2_lane - self.point1_lane)
    
    @property
    def lane_width_px(self) -> float:
        """Calculate lane width (90% of spacing for clear boundaries)"""
        return abs(self.lane_spacing_px) * 0.9

@dataclass 
class SimpleLaneBoundary:
    """Simplified lane boundary definition"""
    lane_index: int
    center_px: float
    left_px: float
    right_px: float
    width_px: float

def calculate_lane_positions(calibration: TwoPointCalibration) -> List[SimpleLaneBoundary]:
    """Calculate all lane positions based on two-point calibration"""
    boundaries = []
    
    # Calculate centers for all lanes
    for lane_num in range(1, calibration.total_lanes + 1):
        # Calculate center position for this lane
        offset_from_point1 = (lane_num - calibration.point1_lane) * calibration.lane_spacing_px
        center_x = calibration.point1_x + offset_from_point1
        
        # Calculate boundaries
        half_width = calibration.lane_width_px / 2
        left_x = center_x - half_width
        right_x = center_x + half_width
        
        boundaries.append(SimpleLaneBoundary(
            lane_index=lane_num,
            center_px=center_x,
            left_px=left_x,
            right_px=right_x,
            width_px=calibration.lane_width_px
        ))
    
    return boundaries

def add_inset_to_boundaries(boundaries: List[SimpleLaneBoundary], inset_px: float = 5.0) -> List[SimpleLaneBoundary]:
    """Add inset to lane boundaries for better centering"""
    inset_boundaries = []
    
    for boundary in boundaries:
        # Apply inset by reducing width from both sides
        new_left = boundary.left_px + inset_px
        new_right = boundary.right_px - inset_px
        new_width = new_right - new_left
        
        # Ensure width doesn't go negative
        if new_width < 5.0:  # Minimum lane width
            # Keep center, reduce to minimum width
            new_width = 5.0
            new_left = boundary.center_px - new_width/2
            new_right = boundary.center_px + new_width/2
        
        inset_boundaries.append(SimpleLaneBoundary(
            lane_index=boundary.lane_index,
            center_px=boundary.center_px,  # Center stays the same
            left_px=new_left,
            right_px=new_right,
            width_px=new_width
        ))
    
    return inset_boundaries

def boundaries_to_dataframe(boundaries: List[SimpleLaneBoundary]) -> pd.DataFrame:
    """Convert boundaries to pandas DataFrame for display/export"""
    data = []
    for boundary in boundaries:
        data.append({
            "lane": boundary.lane_index,
            "center_px": round(boundary.center_px, 1),
            "left_px": round(boundary.left_px, 1),
            "right_px": round(boundary.right_px, 1),
            "width_px": round(boundary.width_px, 1)
        })
    return pd.DataFrame(data)

def boundaries_to_csv(boundaries: List[SimpleLaneBoundary]) -> str:
    """Export boundaries to CSV format"""
    df = boundaries_to_dataframe(boundaries)
    return df.to_csv(index=False)

def boundaries_to_overlay_data(boundaries: List[SimpleLaneBoundary]) -> List[Dict[str, float]]:
    """Convert boundaries to format suitable for overlay drawing"""
    overlay_data = []
    for boundary in boundaries:
        overlay_data.append({
            "lane": boundary.lane_index,
            "center": boundary.center_px,
            "left": boundary.left_px,
            "right": boundary.right_px
        })
    return overlay_data

def validate_calibration_points(point1_x: float, point1_lane: int, 
                               point2_x: float, point2_lane: int,
                               total_lanes: int, image_width: int) -> Tuple[bool, str]:
    """Validate that calibration points are reasonable"""
    
    # Check lane numbers are within range
    if point1_lane < 1 or point1_lane > total_lanes:
        return False, f"Point 1 lane number ({point1_lane}) must be between 1 and {total_lanes}"
    
    if point2_lane < 1 or point2_lane > total_lanes:
        return False, f"Point 2 lane number ({point2_lane}) must be between 1 and {total_lanes}"
    
    # Check points are different lanes
    if point1_lane == point2_lane:
        return False, "The two calibration points must be in different lanes"
    
    # Check points are within image bounds
    if point1_x < 0 or point1_x > image_width:
        return False, f"Point 1 X coordinate ({point1_x}) is outside image bounds (0-{image_width})"
    
    if point2_x < 0 or point2_x > image_width:
        return False, f"Point 2 X coordinate ({point2_x}) is outside image bounds (0-{image_width})"
    
    # Check spacing is reasonable
    spacing = abs(point2_x - point1_x) / abs(point2_lane - point1_lane)
    if spacing < 10:
        return False, f"Lane spacing too small ({spacing:.1f}px). Points may be too close together."
    
    if spacing > 400:
        return False, f"Lane spacing too large ({spacing:.1f}px). Points may be incorrectly positioned."
    
    return True, "Calibration points are valid"

def get_recommended_gel_settings(gel_type: GelType, total_lanes: int) -> Dict[str, any]:
    """Get recommended settings based on gel type and lane count"""
    settings = {
        "recommended_inset": 5.0,
        "typical_spacing_range": (30, 250),
        "description": ""
    }
    
    if gel_type == "sds_page":
        settings["description"] = f"SDS-PAGE protein gel with {total_lanes} lanes"
        if total_lanes <= 10:
            settings["typical_spacing_range"] = (45, 150)
            settings["recommended_inset"] = 5.0
        elif total_lanes <= 15:
            settings["typical_spacing_range"] = (35, 120)
            settings["recommended_inset"] = 4.0
        else:
            settings["typical_spacing_range"] = (25, 80)
            settings["recommended_inset"] = 3.0
    else:  # etbr_agarose
        settings["description"] = f"EtBr agarose DNA gel with {total_lanes} lanes"
        if total_lanes <= 8:
            settings["typical_spacing_range"] = (60, 200)
            settings["recommended_inset"] = 8.0
        elif total_lanes <= 12:
            settings["typical_spacing_range"] = (45, 150)
            settings["recommended_inset"] = 6.0
        else:
            settings["typical_spacing_range"] = (30, 100)
            settings["recommended_inset"] = 4.0
    
    return settings