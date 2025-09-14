# autodense/preprocess/enhanced_orchestrator.py
"""
Enhanced orchestrator that supports custom lane boundaries from user configuration.
Extends the existing pipeline with lane boundary override capabilities.
"""

from dataclasses import dataclass
from typing import Optional, Dict, Any, List, Tuple
from pathlib import Path
import sys
import importlib.util

# Import the existing orchestrator components
sys.path.append(str(Path(__file__).parent.parent.parent / "scripts"))
from autodense.orchestrator.pipeline import Params as BaseParams, observe, small_rule_coach
from autodense.vision.analyzer import analyze_image, Lane, Band, AnalysisResult
from autodense.metrics.gel_metrics import acceptance_default

@dataclass 
class EnhancedParams(BaseParams):
    """Extended parameters with custom lane boundary support"""
    custom_lanes: Optional[List[Tuple[float, float, float, float]]] = None  # (x0, y0, x1, y1) boundaries
    use_custom_lanes: bool = False
    force_lane_count: Optional[int] = None  # Override detected lane count

def analyze_with_custom_lanes(
    image: Path, 
    gel_type: str,
    custom_lanes: List[Tuple[float, float, float, float]] = None,
    **kwargs
) -> AnalysisResult:
    """
    Analyze image with custom lane boundaries if provided.
    
    Args:
        image: Path to preprocessed image
        gel_type: "protein" or "dna"
        custom_lanes: List of (x0, y0, x1, y1) lane boundaries
        **kwargs: Other parameters for analyze_image
    
    Returns:
        AnalysisResult with lanes using custom boundaries if provided
    """
    if custom_lanes:
        # Use custom lane boundaries instead of auto-detection
        from PIL import Image as PILImage
        img = PILImage.open(image)
        img_width, img_height = img.size
        
        # Convert custom boundaries to Lane objects with basic band detection
        custom_lane_objects = []
        for i, (x0, y0, x1, y1) in enumerate(custom_lanes):
            # Ensure boundaries are within image
            x0 = max(0, min(img_width, int(x0)))
            x1 = max(0, min(img_width, int(x1)))
            y0 = max(0, min(img_height, int(y0)))
            y1 = max(0, min(img_height, int(y1)))
            
            # Create lane object - bands will be detected separately
            lane = Lane(
                index=i+1,
                x0=x0, x1=x1, y0=y0, y1=y1,
                type="sample" if i > 0 else "marker",  # Assume first lane is marker
                bands=[]
            )
            custom_lane_objects.append(lane)
        
        # For now, create a basic result - this would need full band detection integration
        result = AnalysisResult(
            lanes=custom_lane_objects,
            ladder_lanes=[1] if custom_lane_objects else [],
            mw_fit=None,
            image_size=(img_width, img_height)
        )
        
        # TODO: Integrate proper band detection within custom lane boundaries
        # This would require modifying the analyzer to work with predefined lanes
        
        return result
    else:
        # Use standard analysis
        return analyze_image(image, gel_type=gel_type, **kwargs)

def enhanced_run(image: Path, p: EnhancedParams, retries: int = 1, use_llm: bool = False):
    """
    Enhanced pipeline run with custom lane boundary support.
    """
    gel_type = "protein" if p.modality.lower() == "sds" else "dna"
    
    if p.use_custom_lanes and p.custom_lanes:
        # Use custom lane boundaries
        res = analyze_with_custom_lanes(
            image, 
            gel_type=gel_type,
            custom_lanes=p.custom_lanes,
            invert_mode=p.invert,
            bg_radius=p.bg_radius
        )
    else:
        # Use standard analysis
        res = analyze_image(
            image, 
            gel_type=gel_type, 
            invert_mode=p.invert,
            min_lanes=p.min_lanes, 
            max_lanes=p.max_lanes, 
            comb=p.comb,
            num_ladders=p.num_ladders, 
            ladder_min_bands=p.ladder_min_bands,
            ladder_min_score=p.ladder_min_score, 
            bg_radius=p.bg_radius
        )
    
    obs = observe(res)
    acc = acceptance_default(p.modality, res)

    attempt = 0
    # Skip retries if using custom lanes (user has already configured)
    if not p.use_custom_lanes:
        while attempt < retries and not acc.passed:
            proposal = small_rule_coach(obs, p, p.modality)
            if not proposal:
                # Create fallback proposal
                proposal = EnhancedParams(**vars(p))
                proposal.min_lanes = max(2, p.min_lanes - 1)
                proposal.max_lanes = p.max_lanes + 1
                proposal.ladder_min_score = max(0.25, p.ladder_min_score - 0.05)
            p = proposal
            
            res = analyze_image(
                image, 
                gel_type=gel_type, 
                invert_mode=p.invert,
                min_lanes=p.min_lanes, 
                max_lanes=p.max_lanes, 
                comb=p.comb,
                num_ladders=p.num_ladders, 
                ladder_min_bands=p.ladder_min_bands,
                ladder_min_score=p.ladder_min_score, 
                bg_radius=p.bg_radius
            )
            obs = observe(res)
            acc = acceptance_default(p.modality, res)
            attempt += 1

    obs.update({"acceptance": acc.details, "accepted": acc.passed})
    return res, p, obs

def create_enhanced_params_from_lane_config(
    lane_config: Dict[str, Any],
    base_params: BaseParams
) -> EnhancedParams:
    """
    Create EnhancedParams from lane configuration and base parameters.
    """
    boundaries = lane_config.get('boundaries', [])
    custom_lanes = []
    
    # Convert boundaries to (x0, y0, x1, y1) format
    # For now, use full image height - this should be updated with actual gel ROI
    for boundary in boundaries:
        custom_lanes.append((
            boundary.left_inset_px,
            0,  # y0 - should be actual gel top
            boundary.right_inset_px, 
            1000  # y1 - should be actual gel bottom
        ))
    
    enhanced_params = EnhancedParams(
        modality=base_params.modality,
        min_lanes=base_params.min_lanes,
        max_lanes=base_params.max_lanes,
        comb=base_params.comb,
        num_ladders=base_params.num_ladders,
        ladder_min_bands=base_params.ladder_min_bands,
        ladder_min_score=base_params.ladder_min_score,
        bg_radius=base_params.bg_radius,
        invert=base_params.invert,
        custom_lanes=custom_lanes,
        use_custom_lanes=len(custom_lanes) > 0,
        force_lane_count=len(boundaries) if boundaries else None
    )
    
    return enhanced_params