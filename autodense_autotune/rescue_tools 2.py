"""
Rescue tools for failed detection with parameter inversion/relaxation.

Provides automated parameter adjustment strategies when initial detection fails,
including polarity inversion, sensitivity adjustment, and preprocessing tweaks.
"""

import logging
import json
from typing import Dict, List, Optional, Tuple, Any
from pathlib import Path
import copy

from .java_bridge import run_detect_only_sds_page, JavaBridgeError

logger = logging.getLogger(__name__)

class RescueStrategy:
    """Base class for parameter rescue strategies"""
    
    def __init__(self, name: str, description: str):
        self.name = name
        self.description = description
    
    def adjust_config(self, original_config: Dict[str, Any]) -> Dict[str, Any]:
        """Apply rescue adjustments to config. Override in subclasses."""
        return copy.deepcopy(original_config)

class PolarityInversionRescue(RescueStrategy):
    """Invert image polarity when detection fails"""
    
    def __init__(self):
        super().__init__("polarity_inversion", "Invert image polarity (dark->bright bands)")
    
    def adjust_config(self, original_config: Dict[str, Any]) -> Dict[str, Any]:
        config = copy.deepcopy(original_config)
        
        # Adjust preprocessing to force polarity inversion
        if "pre" not in config:
            config["pre"] = {}
        
        config["pre"]["force_invert"] = True
        
        # Adjust detection sensitivity for inverted polarity
        if "detect" in config:
            # Lower sensitivity for inverted images (often noisier)
            if "sensitivity" in config["detect"]:
                config["detect"]["sensitivity"] = max(0.1, config["detect"]["sensitivity"] * 0.7)
        
        return config

class SensitivityRelaxationRescue(RescueStrategy):
    """Relax detection sensitivity when detection fails"""
    
    def __init__(self, sensitivity_factor: float = 0.33):
        super().__init__("sensitivity_relaxation", f"Reduce sensitivity by {sensitivity_factor}x")
        self.sensitivity_factor = sensitivity_factor
    
    def adjust_config(self, original_config: Dict[str, Any]) -> Dict[str, Any]:
        config = copy.deepcopy(original_config)
        
        if "detect" not in config:
            config["detect"] = {}
        
        # Relax sensitivity (lower prominence threshold)
        if "sensitivity" in config["detect"]:
            original_sensitivity = config["detect"]["sensitivity"]
            config["detect"]["sensitivity"] = max(0.1, original_sensitivity * self.sensitivity_factor)
        else:
            config["detect"]["sensitivity"] = 0.15  # Conservative default
        
        # Disable constant spacing for more flexible detection
        config["detect"]["constant_spacing"] = False
        
        return config

class PreprocessingEnhancementRescue(RescueStrategy):
    """Enhance preprocessing when detection fails"""
    
    def __init__(self):
        super().__init__("preprocessing_enhancement", "Enhance contrast and noise reduction")
    
    def adjust_config(self, original_config: Dict[str, Any]) -> Dict[str, Any]:
        config = copy.deepcopy(original_config)
        
        if "pre" not in config:
            config["pre"] = {}
        
        # Enhance Gaussian smoothing
        original_sigma = config["pre"].get("gaussian_sigma", 2.0)
        config["pre"]["gaussian_sigma"] = original_sigma * 1.6
        
        # Adjust clipping percentiles for better contrast
        config["pre"]["clip_percentile_low"] = 1.0
        config["pre"]["clip_percentile_high"] = 99.5
        
        return config

class RescueRunner:
    """Manages rescue attempts with multiple strategies"""
    
    def __init__(self):
        self.strategies = [
            PolarityInversionRescue(),
            SensitivityRelaxationRescue(0.33),
            PreprocessingEnhancementRescue(),
            # Combined rescue: polarity + sensitivity
            self._create_combined_rescue()
        ]
    
    def _create_combined_rescue(self) -> RescueStrategy:
        """Create combined polarity inversion + sensitivity relaxation rescue"""
        class CombinedRescue(RescueStrategy):
            def __init__(self):
                super().__init__("combined_rescue", "Polarity inversion + sensitivity relaxation")
            
            def adjust_config(self, original_config: Dict[str, Any]) -> Dict[str, Any]:
                # Apply polarity inversion first
                polarity_rescue = PolarityInversionRescue()
                config = polarity_rescue.adjust_config(original_config)
                
                # Then apply sensitivity relaxation
                sensitivity_rescue = SensitivityRelaxationRescue(0.5)
                config = sensitivity_rescue.adjust_config(config)
                
                return config
        
        return CombinedRescue()
    
    def attempt_rescue(self, input_path: str, original_config_path: str, output_dir: str, 
                      roi: str = None, max_attempts: int = None) -> Optional[Dict[str, Any]]:
        """
        Attempt rescue with multiple strategies until one succeeds
        
        Args:
            input_path: Path to preprocessed image
            original_config_path: Path to original config file
            output_dir: Output directory for rescue attempts
            roi: Optional ROI specification
            max_attempts: Maximum rescue attempts (default: all strategies)
            
        Returns:
            Detection results if rescue succeeds, None if all attempts fail
        """
        # Load original config
        with open(original_config_path, 'r') as f:
            if original_config_path.endswith('.json'):
                original_config = json.load(f)
            else:
                # Assume YAML
                import yaml
                original_config = yaml.safe_load(f)
        
        max_attempts = max_attempts or len(self.strategies)
        
        for i, strategy in enumerate(self.strategies[:max_attempts]):
            try:
                logger.info(f"Attempting rescue strategy {i+1}/{max_attempts}: {strategy.name}")
                
                # Apply rescue strategy to config
                rescue_config = strategy.adjust_config(original_config)
                
                # Create temporary config file for rescue attempt
                rescue_config_path = Path(output_dir) / f"rescue_config_{i+1}_{strategy.name}.json"
                with open(rescue_config_path, 'w') as f:
                    json.dump(rescue_config, f, indent=2)
                
                # Create rescue output directory
                rescue_output_dir = Path(output_dir) / f"rescue_attempt_{i+1}_{strategy.name}"
                rescue_output_dir.mkdir(parents=True, exist_ok=True)
                
                # Attempt detection with rescue parameters
                result = run_detect_only_sds_page(
                    input_path=input_path,
                    config_path=str(rescue_config_path),
                    output_dir=str(rescue_output_dir),
                    roi=roi
                )
                
                # Check if rescue was successful
                if self._is_rescue_successful(result):
                    logger.info(f"Rescue successful with strategy: {strategy.name}")
                    result['rescue_strategy'] = strategy.name
                    result['rescue_config'] = rescue_config
                    return result
                else:
                    logger.info(f"Rescue attempt {i+1} failed: {strategy.name}")
                    
            except Exception as e:
                logger.warning(f"Rescue attempt {i+1} failed with error: {e}")
                continue
        
        logger.error(f"All {max_attempts} rescue attempts failed")
        return None
    
    def _is_rescue_successful(self, result: Dict[str, Any]) -> bool:
        """Check if rescue attempt was successful based on detection results"""
        # Check for basic success indicators
        if not result.get("detection_completed", False):
            return False
        
        # Check for actual detections in metrics
        if "metrics" in result:
            lane_count = result["metrics"].get("lane_count", 0)
            band_count = result["metrics"].get("band_count", 0)
            
            # Success if we detected any lanes or bands
            return lane_count > 0 or band_count > 0
        
        # Fallback: check stderr/stdout for success indicators
        stderr = result.get("stderr", "")
        stdout = result.get("stdout", "")
        
        # Look for detection success patterns in output
        success_patterns = [
            "robust detection found",
            "lanes found",
            "bands found",
            "detection:done count=",
        ]
        
        combined_output = stderr + stdout
        return any(pattern in combined_output for pattern in success_patterns)

def run_rescue_analysis(input_path: str, config_path: str, output_dir: str, 
                       roi: str = None, max_attempts: int = 3) -> Dict[str, Any]:
    """
    Convenience function to run detection with automatic rescue fallback
    
    Args:
        input_path: Path to preprocessed image
        config_path: Path to configuration file
        output_dir: Output directory
        roi: Optional ROI specification
        max_attempts: Maximum rescue attempts
        
    Returns:
        Detection results (either from initial attempt or successful rescue)
    """
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Try initial detection first
    try:
        logger.info("Attempting initial detection...")
        result = run_detect_only_sds_page(input_path, config_path, output_dir, roi)
        
        # Check if initial detection was successful
        rescue_runner = RescueRunner()
        if rescue_runner._is_rescue_successful(result):
            logger.info("Initial detection successful")
            result['rescue_used'] = False
            return result
        else:
            logger.info("Initial detection failed, attempting rescue...")
    except Exception as e:
        logger.warning(f"Initial detection failed with error: {e}")
    
    # Attempt rescue
    rescue_runner = RescueRunner()
    rescue_result = rescue_runner.attempt_rescue(
        input_path=input_path,
        original_config_path=config_path,
        output_dir=output_dir,
        roi=roi,
        max_attempts=max_attempts
    )
    
    if rescue_result:
        rescue_result['rescue_used'] = True
        return rescue_result
    else:
        # Return failure result
        return {
            'detection_completed': False,
            'rescue_used': True,
            'rescue_attempts': max_attempts,
            'error': 'All detection and rescue attempts failed'
        }
