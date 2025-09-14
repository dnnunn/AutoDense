"""
Enhanced Preprocessing Recipes for Vision-Assisted Optimization

Provides specialized preprocessing pipelines for different gel types and failure modes.
Designed to address specific detection shortfalls identified in AutoDense analysis.
"""

from typing import Dict, Any, List, Tuple, Optional
import numpy as np
from dataclasses import dataclass
import logging

logger = logging.getLogger(__name__)

@dataclass
class PreprocessingRecipe:
    """Complete preprocessing recipe specification."""
    recipe_id: str
    target_analysis: str  # 'etbr', 'sds', 'colony'
    failure_modes_addressed: List[str]
    transforms: List[Dict[str, Any]]
    validation_criteria: Dict[str, Any]
    token_reduction_target: float
    expected_improvements: Dict[str, str]

class VisionPreprocessingEngine:
    """Enhanced preprocessing engine targeting specific detection failures."""
    
    def __init__(self):
        self.recipes = self._initialize_recipes()
        self.calibration_results = {}
    
    def _initialize_recipes(self) -> Dict[str, PreprocessingRecipe]:
        """Initialize preprocessing recipes for different failure modes."""
        recipes = {}
        
        # EtBr Dark Fluorescence Recipe
        recipes['etbr_fluorescence_rescue'] = PreprocessingRecipe(
            recipe_id="etbr_fluorescence_rescue_v1.0",
            target_analysis="etbr",
            failure_modes_addressed=[
                "catastrophic_band_detection", 
                "dark_fluorescence_contrast",
                "inverted_polarity_detection",
                "baseline_calculation_failure"
            ],
            transforms=[
                {
                    "step": 1,
                    "operation": "invert_polarity_auto",
                    "method": "histogram_analysis", 
                    "rationale": "Detect and correct dark fluorescence images",
                    "parameters": {
                        "dark_threshold": 0.4,  # Mean intensity below 40% indicates dark image
                        "confidence_threshold": 0.8
                    }
                },
                {
                    "step": 2,
                    "operation": "contrast_enhancement",
                    "method": "clahe_optimized",
                    "rationale": "Enhance band visibility in fluorescence images",
                    "parameters": {
                        "clip_limit": 3.0,  # Higher clip for bright band enhancement
                        "tile_grid": [6, 6],  # Smaller tiles for localized enhancement
                        "preserve_edges": True
                    }
                },
                {
                    "step": 3,
                    "operation": "noise_reduction",
                    "method": "edge_preserving_gaussian",
                    "rationale": "Remove acquisition noise without band edge loss",
                    "parameters": {
                        "sigma": 0.8,
                        "preserve_sharp_edges": True,
                        "edge_threshold": 0.1
                    }
                },
                {
                    "step": 4,
                    "operation": "intensity_normalization", 
                    "method": "percentile_stretch_tight",
                    "rationale": "Optimize dynamic range for band detection",
                    "parameters": {
                        "percentiles": [0.5, 99.5],  # Tighter clipping
                        "target_range": [0, 255]
                    }
                }
            ],
            validation_criteria={
                "band_edge_preservation": ">=95%",
                "contrast_improvement": ">=2.0x", 
                "noise_reduction": "SNR improvement >=3dB",
                "processing_time": "<=3 seconds"
            },
            token_reduction_target=0.75,
            expected_improvements={
                "band_detection": "From 2 bands to 15-20+ bands",
                "lane_detection": "Maintain 11+ lanes", 
                "baseline_stability": "Improved fluorescence baseline calculation",
                "edge_preservation": "Sharp band boundaries maintained"
            }
        )
        
        # SDS Edge Lane Recipe
        recipes['sds_edge_lane_rescue'] = PreprocessingRecipe(
            recipe_id="sds_edge_lane_rescue_v1.0",
            target_analysis="sds",
            failure_modes_addressed=[
                "peripheral_lane_loss",
                "uneven_illumination",
                "edge_contrast_degradation",
                "lane_boundary_detection"
            ],
            transforms=[
                {
                    "step": 1,
                    "operation": "illumination_correction",
                    "method": "rolling_ball_background",
                    "rationale": "Correct uneven illumination affecting edge lanes",
                    "parameters": {
                        "radius": 15.0,
                        "preserve_edges": True,
                        "sliding_paraboloid": False
                    }
                },
                {
                    "step": 2,
                    "operation": "contrast_enhancement",
                    "method": "clahe_edge_optimized",
                    "rationale": "Enhance edge lane contrast without oversaturation",
                    "parameters": {
                        "clip_limit": 2.5,
                        "tile_grid": [4, 8],  # Horizontal emphasis for lanes
                        "edge_amplification": 1.2
                    }
                },
                {
                    "step": 3,
                    "operation": "edge_preservation",
                    "method": "gaussian_mild",
                    "rationale": "Slight smoothing without lane boundary loss",
                    "parameters": {
                        "sigma": 0.5,
                        "boundary_preservation": True
                    }
                },
                {
                    "step": 4,
                    "operation": "deskew_correction",
                    "method": "hough_line_detection",
                    "rationale": "Correct gel rotation for better lane alignment",
                    "parameters": {
                        "angle_tolerance": 1.0,
                        "confidence_threshold": 0.7,
                        "max_correction": 5.0  # degrees
                    }
                }
            ],
            validation_criteria={
                "lane_count_improvement": ">=10/12 lanes detected",
                "edge_lane_preservation": "Outer 2 lanes maintained",
                "band_detection_stability": "No regression in band count",
                "illumination_uniformity": "CV < 15%"
            },
            token_reduction_target=0.70,
            expected_improvements={
                "lane_detection": "From 7/12 to 10+/12 lanes",
                "edge_preservation": "Better peripheral lane boundaries",
                "alignment": "Improved lane straightness",
                "band_detection": "Stable or improved within lanes"
            }
        )
        
        # Colony Segmentation Recipe 
        recipes['colony_classification_rescue'] = PreprocessingRecipe(
            recipe_id="colony_classification_rescue_v1.0", 
            target_analysis="colony",
            failure_modes_addressed=[
                "over_aggressive_filtering",
                "small_colony_detection",
                "blue_white_discrimination", 
                "touching_colony_separation"
            ],
            transforms=[
                {
                    "step": 1,
                    "operation": "plate_normalization",
                    "method": "illumination_flattening",
                    "rationale": "Normalize plate illumination for consistent detection",
                    "parameters": {
                        "background_estimation": "gaussian_blur",
                        "sigma": 50.0,
                        "preserve_colonies": True
                    }
                },
                {
                    "step": 2,
                    "operation": "contrast_optimization", 
                    "method": "adaptive_histogram",
                    "rationale": "Enhance colony-background contrast",
                    "parameters": {
                        "clip_limit": 2.0,
                        "tile_grid": [8, 8],
                        "colony_size_adaptive": True
                    }
                },
                {
                    "step": 3,
                    "operation": "edge_enhancement",
                    "method": "unsharp_masking",
                    "rationale": "Sharpen colony boundaries for better segmentation",
                    "parameters": {
                        "radius": 1.0,
                        "amount": 0.5,
                        "threshold": 0.02
                    }
                },
                {
                    "step": 4,
                    "operation": "size_filtering_relaxed",
                    "method": "morphological_opening",
                    "rationale": "Less aggressive filtering to preserve small colonies",
                    "parameters": {
                        "min_area": 25,  # Reduced from aggressive filtering
                        "max_area": 10000,
                        "circularity_min": 0.4  # More tolerant
                    }
                }
            ],
            validation_criteria={
                "colony_recovery": ">=80% of raw detections classified",
                "small_colony_detection": ">=2mm diameter colonies detected", 
                "blue_white_accuracy": ">=90% classification accuracy",
                "false_positive_rate": "<5%"
            },
            token_reduction_target=0.70,
            expected_improvements={
                "classification_efficiency": "From 40% to 80% colony retention",
                "small_colony_detection": "Better sensitivity for small colonies",
                "boundary_detection": "Improved colony edge definition",
                "blue_white_discrimination": "Enhanced chromogenic analysis"
            }
        )
        
        return recipes
    
    def get_recipe_for_failure_mode(
        self, 
        analysis_type: str, 
        failure_indicators: Dict[str, Any]
    ) -> Optional[PreprocessingRecipe]:
        """
        Select optimal preprocessing recipe based on failure mode analysis.
        
        Args:
            analysis_type: Type of analysis ('etbr', 'sds', 'colony')
            failure_indicators: Metrics indicating specific failure modes
            
        Returns:
            Optimal preprocessing recipe or None
        """
        if analysis_type == 'etbr':
            # Check for catastrophic band detection failure
            bands_raw = failure_indicators.get('bands_raw', 0)
            lanes_raw = failure_indicators.get('lanes_raw', 0)
            
            if bands_raw <= 5 and lanes_raw >= 8:  # Lanes detected but very few bands
                logger.info(f"🎯 EtBr catastrophic band failure detected: {bands_raw} bands, {lanes_raw} lanes")
                return self.recipes['etbr_fluorescence_rescue']
        
        elif analysis_type == 'sds':
            # Check for peripheral lane loss
            lanes_raw = failure_indicators.get('lanes_raw', 0)
            expected_lanes = failure_indicators.get('expected_lanes', 12)
            
            if lanes_raw < 0.75 * expected_lanes:  # Missing >25% of lanes
                logger.info(f"🎯 SDS edge lane failure detected: {lanes_raw}/{expected_lanes} lanes")
                return self.recipes['sds_edge_lane_rescue']
        
        elif analysis_type == 'colony':
            # Check for over-aggressive filtering
            colonies_raw = failure_indicators.get('colonies_raw', 0)
            colonies_final = failure_indicators.get('colonies_classified', 0)
            
            if colonies_raw > 0 and colonies_final / colonies_raw < 0.6:  # >40% loss
                logger.info(f"🎯 Colony classification failure: {colonies_final}/{colonies_raw} retained")
                return self.recipes['colony_classification_rescue']
        
        return None
    
    def generate_optimized_parameters(
        self, 
        recipe: PreprocessingRecipe,
        current_config: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Generate optimized analysis parameters based on preprocessing recipe.
        
        Args:
            recipe: Preprocessing recipe to apply
            current_config: Current analysis configuration
            
        Returns:
            Dictionary of optimized parameters
        """
        optimized_params = {}
        
        if recipe.target_analysis == 'etbr':
            # EtBr fluorescence rescue parameters
            optimized_params.update({
                'bands.prominence_frac': 0.015,  # Ultra-sensitive
                'bands.min_distance_px': 8,     # Allow closer bands
                'bands.baseline.method': 'morph',  # Better for fluorescence
                'bands.baseline.quantile': 0.03,   # Low quantile for dark backgrounds
                'detect.prominence_frac': 0.035,   # More sensitive lane detection
                'workflow.sensitivity': 0.9,       # High sensitivity
                'pre.clahe.enabled': True,          # Enable contrast enhancement
                'pre.clahe.clip_limit': 3.0        # Aggressive enhancement
            })
        
        elif recipe.target_analysis == 'sds':
            # SDS edge lane rescue parameters
            optimized_params.update({
                'detect.prominence_frac': 0.045,         # Slightly more sensitive
                'workflow.sensitivity': 1.2,             # High sensitivity
                'workflow.expected_lanes': 12,           # Target full count
                'pre.background_removal_radius': 15.0,   # Enable background correction
                'pre.enable_deskew': True,               # Enable alignment correction
                'pre.clahe.enabled': True,               # Enable edge enhancement
                'pre.clahe.tile_grid': [4, 8],          # Horizontal emphasis
                'lanes.prominence_frac': 0.025           # Enhanced boundary detection
            })
        
        elif recipe.target_analysis == 'colony':
            # Colony classification rescue parameters
            optimized_params.update({
                'segmentation.min_area': 25,              # Less aggressive filtering
                'segmentation.circularity_min': 0.4,     # More tolerant shape
                'classification.sensitivity': 1.1,        # Higher sensitivity
                'preprocessing.unsharp_amount': 0.5,      # Edge enhancement
                'quality_gates.size_filter_aggressive': False  # Disable aggressive filtering
            })
        
        return optimized_params
    
    def validate_recipe_performance(
        self, 
        recipe: PreprocessingRecipe,
        before_metrics: Dict[str, Any],
        after_metrics: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Validate preprocessing recipe performance against expected improvements.
        
        Args:
            recipe: Applied preprocessing recipe
            before_metrics: Metrics before recipe application
            after_metrics: Metrics after recipe application
            
        Returns:
            Validation results with success indicators
        """
        validation_result = {
            'recipe_id': recipe.recipe_id,
            'target_analysis': recipe.target_analysis,
            'success': False,
            'improvements': {},
            'regressions': {},
            'validation_criteria_met': {}
        }
        
        # Check improvements by analysis type
        if recipe.target_analysis == 'etbr':
            bands_before = before_metrics.get('bands_raw', 0)
            bands_after = after_metrics.get('bands_raw', 0)
            improvement = bands_after - bands_before
            
            validation_result['improvements']['bands_detected'] = improvement
            validation_result['success'] = improvement >= 10  # Significant improvement
        
        elif recipe.target_analysis == 'sds':
            lanes_before = before_metrics.get('lanes_raw', 0)
            lanes_after = after_metrics.get('lanes_raw', 0)
            improvement = lanes_after - lanes_before
            
            validation_result['improvements']['lanes_detected'] = improvement
            validation_result['success'] = improvement >= 2  # At least 2 more lanes
        
        elif recipe.target_analysis == 'colony':
            raw_before = before_metrics.get('colonies_raw', 0)
            classified_before = before_metrics.get('colonies_classified', 0)
            raw_after = after_metrics.get('colonies_raw', 0)
            classified_after = after_metrics.get('colonies_classified', 0)
            
            retention_before = classified_before / raw_before if raw_before > 0 else 0
            retention_after = classified_after / raw_after if raw_after > 0 else 0
            
            validation_result['improvements']['retention_rate'] = retention_after - retention_before
            validation_result['success'] = retention_after >= 0.75  # 75%+ retention
        
        return validation_result

def create_calibration_image_set(analysis_type: str) -> List[Dict[str, Any]]:
    """
    Create calibration image set for testing preprocessing recipes.
    
    Args:
        analysis_type: Type of analysis to create calibration for
        
    Returns:
        List of calibration image specifications
    """
    calibration_sets = {
        'etbr': [
            {
                'name': 'etbr_clean_fluorescence',
                'description': 'High quality EtBr gel, clear bands, proper exposure',
                'expected_lanes': 20,
                'expected_bands': 25,
                'difficulty': 'easy',
                'failure_modes': ['none'],
                'ground_truth': {
                    'lane_positions': [],  # Would be filled with actual positions
                    'band_positions': [],
                    'molecular_weights': []
                }
            },
            {
                'name': 'etbr_dark_fluorescence_critical',
                'description': 'Dark fluorescence image - critical failure case',
                'expected_lanes': 18,
                'expected_bands': 20,
                'difficulty': 'critical',
                'failure_modes': ['catastrophic_band_detection', 'dark_fluorescence'],
                'ground_truth': {
                    'should_trigger_inversion': True,
                    'requires_aggressive_band_detection': True
                }
            },
            {
                'name': 'etbr_noisy_baseline',
                'description': 'Noisy background with baseline calculation issues',
                'expected_lanes': 16,
                'expected_bands': 15,
                'difficulty': 'hard',
                'failure_modes': ['baseline_calculation', 'background_noise']
            }
        ],
        'sds': [
            {
                'name': 'sds_complete_gel',
                'description': 'Full 12-lane SDS gel, good edge lane visibility',
                'expected_lanes': 12,
                'expected_bands': 30,
                'difficulty': 'easy',
                'failure_modes': ['none']
            },
            {
                'name': 'sds_edge_illumination_loss',
                'description': 'Uneven illumination causing edge lane loss',
                'expected_lanes': 12,
                'expected_bands': 28,
                'difficulty': 'hard',
                'failure_modes': ['peripheral_lane_loss', 'uneven_illumination']
            }
        ]
    }
    
    return calibration_sets.get(analysis_type, [])

# Export key functions for integration
__all__ = [
    'VisionPreprocessingEngine',
    'PreprocessingRecipe', 
    'create_calibration_image_set'
]