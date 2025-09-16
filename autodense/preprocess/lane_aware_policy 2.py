# autodense/preprocess/lane_aware_policy.py
"""
Lane-aware preprocessing policy that considers user-defined lane boundaries
when making preprocessing decisions. Extends the base policy with lane-specific analysis.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple
import numpy as np
from PIL import Image

from .policy import (
    PolicyThresholds, PolicyOutcome, _to_gray_float, evaluate,
    guarded_preprocess as base_guarded_preprocess
)
from .simple_lane_mapping import SimpleLaneBoundary

@dataclass
class LaneAwarePolicyThresholds(PolicyThresholds):
    """Extended thresholds with lane-specific criteria"""
    lane_uniformity_min: float = 0.15  # Minimum uniformity across lanes
    mw_lane_snr_min: float = 3.0      # Minimum SNR for MW standard lane
    sample_lane_snr_min: float = 2.0   # Minimum SNR for sample lanes

@dataclass
class LaneSpecificMetrics:
    """Metrics calculated for individual lanes"""
    lane_index: int
    snr: float
    uniformity: float  # Variance within the lane
    band_sharpness: float  # Measure of band definition
    background_level: float

@dataclass
class LaneAwarePolicyOutcome(PolicyOutcome):
    """Extended outcome with lane-specific analysis"""
    lane_metrics: List[LaneSpecificMetrics]
    problematic_lanes: List[int]
    mw_lane_quality: float

def extract_lane_region(image: np.ndarray, boundary: SimpleLaneBoundary) -> np.ndarray:
    """Extract the image region corresponding to a lane"""
    h, w = image.shape[:2]
    
    # Ensure boundaries are within image
    left = max(0, min(w-1, int(boundary.left_px)))
    right = max(left+1, min(w, int(boundary.right_px)))
    
    # Return the lane strip (full height for now)
    return image[:, left:right]

def calculate_lane_metrics(lane_region: np.ndarray, lane_index: int) -> LaneSpecificMetrics:
    """Calculate quality metrics for an individual lane"""
    if lane_region.size == 0:
        return LaneSpecificMetrics(lane_index, 0.0, 0.0, 0.0, 0.0)
    
    # SNR calculation (similar to global but lane-specific)
    med = np.median(lane_region)
    low = lane_region[lane_region <= med]
    bg_std = float(low.std() + 1e-6)
    p95 = float(np.percentile(lane_region, 95))
    snr = (p95 - med) / bg_std
    
    # Uniformity (inverse of coefficient of variation)
    lane_profile = np.mean(lane_region, axis=0)  # Average down columns
    uniformity = 1.0 - (np.std(lane_profile) / (np.mean(lane_profile) + 1e-6))
    uniformity = max(0.0, uniformity)
    
    # Band sharpness (gradient-based measure)
    vertical_profile = np.mean(lane_region, axis=1)  # Average across width
    gradients = np.abs(np.diff(vertical_profile))
    band_sharpness = float(np.percentile(gradients, 90))  # 90th percentile of gradients
    
    # Background level
    background_level = float(np.percentile(lane_region, 10))  # 10th percentile
    
    return LaneSpecificMetrics(
        lane_index=lane_index,
        snr=snr,
        uniformity=uniformity,
        band_sharpness=band_sharpness,
        background_level=background_level
    )

def analyze_lanes(image: np.ndarray, boundaries: List[SimpleLaneBoundary]) -> List[LaneSpecificMetrics]:
    """Analyze quality metrics for all lanes"""
    lane_metrics = []
    
    for boundary in boundaries:
        lane_region = extract_lane_region(image, boundary)
        metrics = calculate_lane_metrics(lane_region, boundary.lane_index)
        lane_metrics.append(metrics)
    
    return lane_metrics

def identify_problematic_lanes(
    lane_metrics: List[LaneSpecificMetrics],
    thresholds: LaneAwarePolicyThresholds,
    mw_lane_index: int = 1
) -> Tuple[List[int], float]:
    """Identify lanes with quality issues and assess MW lane quality"""
    problematic_lanes = []
    mw_lane_quality = 0.0
    
    for metrics in lane_metrics:
        # Check MW lane quality
        if metrics.lane_index == mw_lane_index:
            mw_lane_quality = metrics.snr
            if metrics.snr < thresholds.mw_lane_snr_min:
                problematic_lanes.append(metrics.lane_index)
        else:
            # Check sample lane quality
            if metrics.snr < thresholds.sample_lane_snr_min:
                problematic_lanes.append(metrics.lane_index)
        
        # Check uniformity for all lanes
        if metrics.uniformity < thresholds.lane_uniformity_min:
            if metrics.lane_index not in problematic_lanes:
                problematic_lanes.append(metrics.lane_index)
    
    return problematic_lanes, mw_lane_quality

def lane_aware_guarded_preprocess(
    image: np.ndarray | Image.Image,
    lane_boundaries: Optional[List[SimpleLaneBoundary]] = None,
    mw_lane_index: int = 1,
    thresholds: Optional[LaneAwarePolicyThresholds] = None,
    max_candidates: int = 3
) -> LaneAwarePolicyOutcome:
    """
    Enhanced preprocessing that considers user-defined lane boundaries.
    
    Args:
        image: Input gel image
        lane_boundaries: List of lane boundaries from user calibration
        mw_lane_index: Index of MW standard lane (1-based)
        thresholds: Policy thresholds for lane-aware decisions
        max_candidates: Maximum preprocessing candidates to test
    
    Returns:
        LaneAwarePolicyOutcome with lane-specific analysis
    """
    if thresholds is None:
        thresholds = LaneAwarePolicyThresholds()
    
    # Convert to grayscale float
    g0 = _to_gray_float(image)
    
    # If no lane boundaries provided, fall back to base policy
    if not lane_boundaries:
        base_outcome = base_guarded_preprocess(image, thresholds, max_candidates)
        return LaneAwarePolicyOutcome(
            mode=base_outcome.mode,
            params=base_outcome.params,
            before=base_outcome.before,
            after=base_outcome.after,
            image=base_outcome.image,
            lane_metrics=[],
            problematic_lanes=[],
            mw_lane_quality=0.0
        )
    
    # Analyze lanes in original image
    initial_lane_metrics = analyze_lanes(g0, lane_boundaries)
    problematic_lanes, mw_lane_quality = identify_problematic_lanes(
        initial_lane_metrics, thresholds, mw_lane_index
    )
    
    # Get base preprocessing outcome
    base_outcome = base_guarded_preprocess(image, thresholds, max_candidates)
    
    # If we have problematic lanes and base preprocessing helped, verify it improves lanes
    if problematic_lanes and base_outcome.mode != "none":
        # Analyze lanes after preprocessing
        post_lane_metrics = analyze_lanes(base_outcome.image, lane_boundaries)
        post_problematic, post_mw_quality = identify_problematic_lanes(
            post_lane_metrics, thresholds, mw_lane_index
        )
        
        # Check if lane-specific quality improved
        lane_improvement = len(problematic_lanes) - len(post_problematic)
        mw_improvement = post_mw_quality - mw_lane_quality
        
        # Use preprocessing if it improves lane quality
        if lane_improvement > 0 or (mw_improvement > 0.5 and len(post_problematic) <= len(problematic_lanes)):
            return LaneAwarePolicyOutcome(
                mode=base_outcome.mode,
                params=base_outcome.params,
                before=base_outcome.before,
                after=base_outcome.after,
                image=base_outcome.image,
                lane_metrics=post_lane_metrics,
                problematic_lanes=post_problematic,
                mw_lane_quality=post_mw_quality
            )
    
    # Return original image if preprocessing doesn't improve lane quality
    m0 = evaluate(g0)
    return LaneAwarePolicyOutcome(
        mode="none",
        params={},
        before=m0,
        after=m0,
        image=g0,
        lane_metrics=initial_lane_metrics,
        problematic_lanes=problematic_lanes,
        mw_lane_quality=mw_lane_quality
    )

def generate_lane_quality_report(outcome: LaneAwarePolicyOutcome) -> Dict[str, any]:
    """Generate a detailed report on lane quality and preprocessing decisions"""
    report = {
        "preprocessing_applied": outcome.mode,
        "total_lanes": len(outcome.lane_metrics),
        "problematic_lanes": outcome.problematic_lanes,
        "mw_lane_quality": outcome.mw_lane_quality,
        "lane_details": []
    }
    
    for metrics in outcome.lane_metrics:
        lane_status = "problematic" if metrics.lane_index in outcome.problematic_lanes else "good"
        report["lane_details"].append({
            "lane": metrics.lane_index,
            "status": lane_status,
            "snr": round(metrics.snr, 2),
            "uniformity": round(metrics.uniformity, 3),
            "sharpness": round(metrics.band_sharpness, 3)
        })
    
    # Overall assessment
    good_lanes = len(outcome.lane_metrics) - len(outcome.problematic_lanes)
    report["overall_quality"] = "excellent" if good_lanes > 0.9 * len(outcome.lane_metrics) else \
                               "good" if good_lanes > 0.7 * len(outcome.lane_metrics) else \
                               "needs_attention"
    
    return report