# autodense/preprocess/lane_mapping.py
"""
Lane boundary mapping and anchor-based alignment for gel analysis.
Provides functions to generate lane boundaries based on gel type and user anchor points.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Dict, List, Tuple, Optional, Literal
import numpy as np
import pandas as pd

GelType = Literal["sds_page", "etbr_agarose"]

@dataclass
class LaneConfig:
    """Configuration for lane boundary generation"""
    gel_type: GelType
    total_lanes: int
    lane_width_px: float
    lane_spacing_px: float
    inset_px: float = 10.0  # Inward boundary adjustment
    first_lane_offset_px: float = 15.0  # Offset from gel edge to first lane center

@dataclass 
class LaneBoundary:
    """Individual lane boundary definition"""
    lane_index: int
    left_px: float
    right_px: float
    center_px: float
    left_inset_px: float
    right_inset_px: float

def get_default_lane_config(gel_type: GelType, total_lanes: int) -> LaneConfig:
    """Get default lane configuration based on gel type"""
    if gel_type == "sds_page":
        # Standard SDS-PAGE parameters (Protein gels)
        # Based on common Bio-Rad and Invitrogen gel formats
        if total_lanes <= 10:
            lane_width = 45.0
            lane_spacing = 50.0
            first_offset = 15.0
        elif total_lanes <= 12:
            lane_width = 40.0
            lane_spacing = 45.0
            first_offset = 12.0
        else:  # 15+ lanes (high-density gels)
            lane_width = 35.0
            lane_spacing = 40.0
            first_offset = 10.0
    else:  # etbr_agarose
        # EtBr agarose gel parameters (DNA/RNA gels)
        # Typically wider lanes for DNA samples
        if total_lanes <= 8:
            lane_width = 70.0  # Wide lanes for DNA
            lane_spacing = 80.0
            first_offset = 25.0
        elif total_lanes <= 12:
            lane_width = 55.0
            lane_spacing = 65.0
            first_offset = 20.0
        elif total_lanes <= 16:
            lane_width = 45.0
            lane_spacing = 55.0
            first_offset = 15.0
        else:  # 20+ lanes (high-density DNA gels)
            lane_width = 35.0
            lane_spacing = 45.0
            first_offset = 12.0
    
    return LaneConfig(
        gel_type=gel_type,
        total_lanes=total_lanes,
        lane_width_px=lane_width,
        lane_spacing_px=lane_spacing,
        first_lane_offset_px=first_offset
    )

def generate_lane_boundaries(config: LaneConfig, anchor_x: Optional[float] = None) -> List[LaneBoundary]:
    """Generate lane boundaries based on configuration and optional anchor point"""
    boundaries = []
    
    # Calculate lane centers
    start_x = config.first_lane_offset_px
    if anchor_x is not None:
        # Align first lane center to anchor point
        start_x = anchor_x
    
    for i in range(config.total_lanes):
        center_x = start_x + (i * config.lane_spacing_px)
        half_width = config.lane_width_px / 2.0
        
        left = center_x - half_width
        right = center_x + half_width
        left_inset = left + config.inset_px
        right_inset = right - config.inset_px
        
        boundaries.append(LaneBoundary(
            lane_index=i + 1,
            left_px=left,
            right_px=right,
            center_px=center_x,
            left_inset_px=left_inset,
            right_inset_px=right_inset
        ))
    
    return boundaries

def align_to_anchor(boundaries: List[LaneBoundary], anchor_x: float, reference_lane: int = 1) -> List[LaneBoundary]:
    """Align existing boundaries to anchor point at specified reference lane"""
    if not boundaries or reference_lane < 1 or reference_lane > len(boundaries):
        return boundaries
    
    # Calculate offset needed to align reference lane to anchor
    ref_boundary = boundaries[reference_lane - 1]
    offset = anchor_x - ref_boundary.center_px
    
    # Apply offset to all lanes
    aligned = []
    for boundary in boundaries:
        aligned.append(LaneBoundary(
            lane_index=boundary.lane_index,
            left_px=boundary.left_px + offset,
            right_px=boundary.right_px + offset,
            center_px=boundary.center_px + offset,
            left_inset_px=boundary.left_inset_px + offset,
            right_inset_px=boundary.right_inset_px + offset
        ))
    
    return aligned

def boundaries_to_csv(boundaries: List[LaneBoundary]) -> str:
    """Convert lane boundaries to CSV format for download"""
    data = []
    for boundary in boundaries:
        data.append({
            "lane": boundary.lane_index,
            "left_px": boundary.left_px,
            "right_px": boundary.right_px,
            "center_px": boundary.center_px,
            "left_inset_px": boundary.left_inset_px,
            "right_inset_px": boundary.right_inset_px
        })
    
    df = pd.DataFrame(data)
    return df.to_csv(index=False)

def boundaries_to_lanes_format(boundaries: List[LaneBoundary]) -> List[Tuple[float, float, float, float]]:
    """Convert boundaries to (x0, y0, x1, y1) format for lane detection"""
    # For now, use full image height - this should be updated with actual gel ROI
    return [(b.left_inset_px, 0, b.right_inset_px, 1000) for b in boundaries]

def estimate_gel_roi(image_width: int, image_height: int, boundaries: List[LaneBoundary]) -> Tuple[int, int, int, int]:
    """Estimate gel ROI based on lane boundaries"""
    if not boundaries:
        return 0, 0, image_width, image_height
    
    # Use lane boundaries to estimate gel area
    min_x = min(b.left_px for b in boundaries) - 20
    max_x = max(b.right_px for b in boundaries) + 20
    
    # Assume gel takes up middle 80% of image height
    margin_y = int(image_height * 0.1)
    
    return (
        max(0, int(min_x)),
        margin_y,
        min(image_width, int(max_x)),
        image_height - margin_y
    )