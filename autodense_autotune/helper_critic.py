"""
Helper critic system for parameter validation and second-opinion analysis.

Provides safety validation for parameter changes proposed by the optimization system,
acting as a second opinion to prevent unsafe or counterproductive modifications.
"""

import logging
import json
from typing import Dict, List, Optional, Tuple, Any
from pathlib import Path
import copy

logger = logging.getLogger(__name__)

class ParameterBounds:
    """Define safe parameter bounds for different analysis types"""
    
    # SDS-PAGE parameter bounds
    SDS_PAGE_BOUNDS = {
        "pre": {
            "gaussian_sigma": (0.5, 5.0),
            "clip_percentile_low": (0.0, 5.0),
            "clip_percentile_high": (95.0, 100.0),
        },
        "detect": {
            "sensitivity": (0.1, 1.0),
            "expected_lanes": (2, 30),
        },
        "detection": {  # legacy fallback
            "expected_lanes": (2, 30),
        }
    }
    
    # Colony analysis parameter bounds
    COLONY_BOUNDS = {
        "pre": {
            "gaussian_sigma": (0.5, 3.0),
            "clip_percentile_low": (0.0, 3.0),
            "clip_percentile_high": (97.0, 100.0),
        },
        "detect": {
            "sensitivity": (0.05, 0.8),
            "min_colony_size": (10, 500),
            "max_colony_size": (100, 5000),
        }
    }
    
    # EtBr agarose parameter bounds
    ETBR_BOUNDS = {
        "pre": {
            "gaussian_sigma": (0.5, 4.0),
            "clip_percentile_low": (0.0, 4.0), 
            "clip_percentile_high": (96.0, 100.0),
        },
        "detect": {
            "sensitivity": (0.1, 0.9),
            "expected_lanes": (4, 25),
        }
    }
    
    @classmethod
    def get_bounds(cls, analysis_type: str) -> Dict[str, Dict[str, Tuple[float, float]]]:
        """Get parameter bounds for analysis type"""
        bounds_map = {
            "sds_page": cls.SDS_PAGE_BOUNDS,
            "colony_count": cls.COLONY_BOUNDS,
            "etbr_agarose": cls.ETBR_BOUNDS,
        }
        return bounds_map.get(analysis_type, cls.SDS_PAGE_BOUNDS)

class ValidationResult:
    """Result of parameter validation"""
    
    def __init__(self, is_valid: bool, warnings: List[str] = None, errors: List[str] = None,
                 adjusted_params: Dict[str, Any] = None):
        self.is_valid = is_valid
        self.warnings = warnings or []
        self.errors = errors or []
        self.adjusted_params = adjusted_params or {}
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "is_valid": self.is_valid,
            "warnings": self.warnings,
            "errors": self.errors,
            "adjusted_params": self.adjusted_params
        }

class HelperCritic:
    """Parameter validation critic system"""
    
    def __init__(self, analysis_type: str = "sds_page"):
        self.analysis_type = analysis_type
        self.bounds = ParameterBounds.get_bounds(analysis_type)
        logger.info(f"Helper critic initialized for analysis type: {analysis_type}")
    
    def validate_parameter_change(self, original_config: Dict[str, Any], 
                                proposed_config: Dict[str, Any]) -> ValidationResult:
        """
        Validate a proposed parameter change against safety bounds and best practices
        
        Args:
            original_config: Original configuration
            proposed_config: Proposed new configuration
            
        Returns:
            ValidationResult with validation outcome and recommendations
        """
        warnings = []
        errors = []
        adjusted_params = {}
        
        # 1. Validate parameter bounds
        bounds_result = self._validate_bounds(proposed_config)
        warnings.extend(bounds_result.warnings)
        errors.extend(bounds_result.errors)
        adjusted_params.update(bounds_result.adjusted_params)
        
        # 2. Validate parameter relationships
        relationship_result = self._validate_relationships(proposed_config)
        warnings.extend(relationship_result.warnings)
        errors.extend(relationship_result.errors)
        
        # 3. Check for dangerous combinations
        danger_result = self._check_dangerous_combinations(original_config, proposed_config)
        warnings.extend(danger_result.warnings)
        errors.extend(danger_result.errors)
        
        # 4. Assess change magnitude
        magnitude_result = self._assess_change_magnitude(original_config, proposed_config)
        warnings.extend(magnitude_result.warnings)
        
        # Overall validation result
        is_valid = len(errors) == 0
        
        if not is_valid:
            logger.warning(f"Parameter validation failed: {errors}")
        elif warnings:
            logger.info(f"Parameter validation passed with warnings: {warnings}")
        else:
            logger.info("Parameter validation passed without issues")
        
        return ValidationResult(is_valid, warnings, errors, adjusted_params)
    
    def _validate_bounds(self, config: Dict[str, Any]) -> ValidationResult:
        """Validate parameters are within safe bounds"""
        warnings = []
        errors = []
        adjusted_params = {}
        
        for section_name, section_bounds in self.bounds.items():
            if section_name not in config:
                continue
                
            section_config = config[section_name]
            
            for param_name, (min_val, max_val) in section_bounds.items():
                if param_name not in section_config:
                    continue
                
                param_value = section_config[param_name]
                
                if param_value < min_val:
                    errors.append(f"{section_name}.{param_name} = {param_value} is below minimum {min_val}")
                    adjusted_params[f"{section_name}.{param_name}"] = min_val
                elif param_value > max_val:
                    errors.append(f"{section_name}.{param_name} = {param_value} is above maximum {max_val}")
                    adjusted_params[f"{section_name}.{param_name}"] = max_val
                    
        return ValidationResult(len(errors) == 0, warnings, errors, adjusted_params)
    
    def _validate_relationships(self, config: Dict[str, Any]) -> ValidationResult:
        """Validate parameter relationships and dependencies"""
        warnings = []
        errors = []
        
        # Check preprocessing relationships
        if "pre" in config:
            pre_config = config["pre"]
            
            # Clip percentiles should be properly ordered
            low_clip = pre_config.get("clip_percentile_low", 0)
            high_clip = pre_config.get("clip_percentile_high", 100)
            
            if low_clip >= high_clip:
                errors.append(f"clip_percentile_low ({low_clip}) must be less than clip_percentile_high ({high_clip})")
            
            if high_clip - low_clip < 50:
                warnings.append(f"Narrow clipping range ({high_clip - low_clip}%) may cause loss of dynamic range")
        
        # Check detection relationships
        if "detect" in config:
            detect_config = config["detect"]
            
            # Sensitivity vs expected features relationship
            sensitivity = detect_config.get("sensitivity", 0.5)
            expected_lanes = detect_config.get("expected_lanes")
            
            if expected_lanes and sensitivity < 0.2 and expected_lanes > 15:
                warnings.append(f"Low sensitivity ({sensitivity}) with many expected lanes ({expected_lanes}) may miss features")
            
            if expected_lanes and sensitivity > 0.8 and expected_lanes < 5:
                warnings.append(f"High sensitivity ({sensitivity}) with few expected lanes ({expected_lanes}) may create false positives")
        
        return ValidationResult(len(errors) == 0, warnings, errors)
    
    def _check_dangerous_combinations(self, original_config: Dict[str, Any], 
                                    proposed_config: Dict[str, Any]) -> ValidationResult:
        """Check for parameter combinations that are known to be problematic"""
        warnings = []
        errors = []
        
        # Check for extreme preprocessing + high sensitivity
        if "pre" in proposed_config and "detect" in proposed_config:
            gaussian_sigma = proposed_config["pre"].get("gaussian_sigma", 2.0)
            sensitivity = proposed_config["detect"].get("sensitivity", 0.5)
            
            if gaussian_sigma > 3.5 and sensitivity > 0.7:
                warnings.append("High gaussian smoothing + high sensitivity may over-detect noise as features")
        
        # Check for polarity inversion without sensitivity adjustment
        if "pre" in proposed_config:
            force_invert = proposed_config["pre"].get("force_invert", False)
            
            # If we're forcing inversion, check if sensitivity was adjusted
            original_invert = original_config.get("pre", {}).get("force_invert", False)
            
            if force_invert and not original_invert:
                # Polarity was inverted, check if sensitivity was adjusted
                orig_sensitivity = original_config.get("detect", {}).get("sensitivity", 0.5)
                new_sensitivity = proposed_config.get("detect", {}).get("sensitivity", orig_sensitivity)
                
                if abs(new_sensitivity - orig_sensitivity) < 0.1:
                    warnings.append("Polarity inversion without sensitivity adjustment may perform poorly")
        
        return ValidationResult(len(errors) == 0, warnings, errors)
    
    def _assess_change_magnitude(self, original_config: Dict[str, Any], 
                               proposed_config: Dict[str, Any]) -> ValidationResult:
        """Assess if parameter changes are too drastic"""
        warnings = []
        
        # Define change thresholds (as multipliers)
        LARGE_CHANGE_THRESHOLD = 2.0  # Changes > 2x original value
        EXTREME_CHANGE_THRESHOLD = 5.0  # Changes > 5x original value
        
        def check_parameter_change(section: str, param: str):
            if (section in original_config and param in original_config[section] and
                section in proposed_config and param in proposed_config[section]):
                
                orig_val = original_config[section][param]
                new_val = proposed_config[section][param]
                
                if orig_val != 0:  # Avoid division by zero
                    change_ratio = abs(new_val / orig_val)
                    
                    if change_ratio > EXTREME_CHANGE_THRESHOLD:
                        warnings.append(f"Extreme change in {section}.{param}: {orig_val} -> {new_val} ({change_ratio:.1f}x)")
                    elif change_ratio > LARGE_CHANGE_THRESHOLD:
                        warnings.append(f"Large change in {section}.{param}: {orig_val} -> {new_val} ({change_ratio:.1f}x)")
        
        # Check key parameters for large changes
        key_params = [
            ("pre", "gaussian_sigma"),
            ("detect", "sensitivity"),
            ("detect", "expected_lanes"),
        ]
        
        for section, param in key_params:
            check_parameter_change(section, param)
        
        return ValidationResult(True, warnings, [])
    
    def suggest_safe_adjustment(self, original_config: Dict[str, Any], 
                               target_improvement: str) -> Dict[str, Any]:
        """
        Suggest a safe parameter adjustment for a specific improvement goal
        
        Args:
            original_config: Current configuration
            target_improvement: Description of desired improvement
            
        Returns:
            Suggested configuration adjustments
        """
        suggested_config = copy.deepcopy(original_config)
        
        # Initialize sections if missing
        if "pre" not in suggested_config:
            suggested_config["pre"] = {}
        if "detect" not in suggested_config:
            suggested_config["detect"] = {}
        
        improvement_lower = target_improvement.lower()
        
        if "sensitivity" in improvement_lower or "detection" in improvement_lower:
            # Improve detection sensitivity
            current_sensitivity = suggested_config["detect"].get("sensitivity", 0.5)
            # Conservative 20% improvement
            new_sensitivity = min(0.9, current_sensitivity * 1.2)
            suggested_config["detect"]["sensitivity"] = new_sensitivity
            
        elif "noise" in improvement_lower or "smooth" in improvement_lower:
            # Improve noise reduction
            current_sigma = suggested_config["pre"].get("gaussian_sigma", 2.0)
            # Conservative 30% increase in smoothing
            new_sigma = min(4.0, current_sigma * 1.3)
            suggested_config["pre"]["gaussian_sigma"] = new_sigma
            
        elif "contrast" in improvement_lower:
            # Improve contrast
            suggested_config["pre"]["clip_percentile_low"] = 1.0
            suggested_config["pre"]["clip_percentile_high"] = 99.0
            
        elif "polarity" in improvement_lower or "invert" in improvement_lower:
            # Invert polarity
            suggested_config["pre"]["force_invert"] = True
            # Reduce sensitivity for inverted images
            current_sensitivity = suggested_config["detect"].get("sensitivity", 0.5)
            suggested_config["detect"]["sensitivity"] = max(0.1, current_sensitivity * 0.8)
        
        # Validate the suggestion
        validation_result = self.validate_parameter_change(original_config, suggested_config)
        
        if not validation_result.is_valid:
            logger.warning(f"Generated suggestion failed validation: {validation_result.errors}")
            # Apply bounds corrections
            for param_path, corrected_value in validation_result.adjusted_params.items():
                section, param = param_path.split(".")
                suggested_config[section][param] = corrected_value
        
        return suggested_config

def validate_config_change(analysis_type: str, original_config: Dict[str, Any], 
                         proposed_config: Dict[str, Any]) -> ValidationResult:
    """
    Convenience function to validate a configuration change
    
    Args:
        analysis_type: Type of analysis (sds_page, colony_count, etbr_agarose)
        original_config: Original configuration
        proposed_config: Proposed new configuration
        
    Returns:
        ValidationResult
    """
    critic = HelperCritic(analysis_type)
    return critic.validate_parameter_change(original_config, proposed_config)
