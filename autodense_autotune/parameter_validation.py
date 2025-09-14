"""
Parameter bounds checking and validation for optimization system.

Provides comprehensive parameter validation, bounds enforcement, and safety checks
for the AutoDense optimization system to prevent unsafe parameter combinations.
"""

import logging
import json
import yaml
from typing import Dict, List, Optional, Tuple, Any, Union
from pathlib import Path
import copy

from .helper_critic import ParameterBounds, HelperCritic, ValidationResult

logger = logging.getLogger(__name__)

class ParameterValidator:
    """Comprehensive parameter validation system"""
    
    def __init__(self, analysis_type: str = "sds_page"):
        self.analysis_type = analysis_type
        self.critic = HelperCritic(analysis_type)
        self.bounds = ParameterBounds.get_bounds(analysis_type)
        
        # Parameter evolution tracking
        self.parameter_history = []
        self.validation_cache = {}
    
    def validate_and_enforce_bounds(self, config: Dict[str, Any], 
                                  strict_mode: bool = True) -> Tuple[Dict[str, Any], ValidationResult]:
        """
        Validate parameters and enforce bounds
        
        Args:
            config: Configuration to validate
            strict_mode: If True, reject configs that violate bounds; if False, auto-correct
            
        Returns:
            (corrected_config, validation_result)
        """
        # Deep copy to avoid modifying original
        validated_config = copy.deepcopy(config)
        
        # Track all validation issues
        all_warnings = []
        all_errors = []
        corrections_made = {}
        
        # Validate each parameter section
        for section_name, section_bounds in self.bounds.items():
            if section_name not in validated_config:
                continue
            
            section_config = validated_config[section_name]
            
            for param_name, (min_val, max_val) in section_bounds.items():
                if param_name not in section_config:
                    continue
                
                param_value = section_config[param_name]
                param_path = f"{section_name}.{param_name}"
                
                # Validate type
                if not isinstance(param_value, (int, float)):
                    all_errors.append(f"{param_path} must be numeric, got {type(param_value)}")
                    continue
                
                # Check bounds
                if param_value < min_val:
                    if strict_mode:
                        all_errors.append(f"{param_path} = {param_value} is below minimum {min_val}")
                    else:
                        all_warnings.append(f"{param_path} corrected from {param_value} to {min_val}")
                        section_config[param_name] = min_val
                        corrections_made[param_path] = (param_value, min_val)
                        
                elif param_value > max_val:
                    if strict_mode:
                        all_errors.append(f"{param_path} = {param_value} is above maximum {max_val}")
                    else:
                        all_warnings.append(f"{param_path} corrected from {param_value} to {max_val}")
                        section_config[param_name] = max_val
                        corrections_made[param_path] = (param_value, max_val)
        
        # Additional validation using critic system
        dummy_original = self._create_default_config()
        critic_result = self.critic.validate_parameter_change(dummy_original, validated_config)
        
        all_warnings.extend(critic_result.warnings)
        all_errors.extend(critic_result.errors)
        
        # Create final validation result
        is_valid = len(all_errors) == 0
        result = ValidationResult(is_valid, all_warnings, all_errors, corrections_made)
        
        logger.info(f"Parameter validation: valid={is_valid}, warnings={len(all_warnings)}, errors={len(all_errors)}")
        
        return validated_config, result
    
    def check_parameter_evolution(self, config_sequence: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Analyze parameter evolution over multiple optimization steps
        
        Args:
            config_sequence: Sequence of configurations from optimization history
            
        Returns:
            Analysis of parameter evolution trends and potential issues
        """
        if len(config_sequence) < 2:
            return {"status": "insufficient_data", "sequence_length": len(config_sequence)}
        
        evolution_analysis = {
            "sequence_length": len(config_sequence),
            "parameter_trends": {},
            "stability_issues": [],
            "convergence_indicators": {},
            "recommendations": []
        }
        
        # Track parameter changes over time
        for section_name, section_bounds in self.bounds.items():
            for param_name, (min_val, max_val) in section_bounds.items():
                param_path = f"{section_name}.{param_name}"
                param_values = []
                
                # Extract parameter values across sequence
                for config in config_sequence:
                    if section_name in config and param_name in config[section_name]:
                        param_values.append(config[section_name][param_name])
                    else:
                        param_values.append(None)
                
                if len([v for v in param_values if v is not None]) < 2:
                    continue
                
                # Analyze trend for this parameter
                trend_analysis = self._analyze_parameter_trend(param_path, param_values, min_val, max_val)
                evolution_analysis["parameter_trends"][param_path] = trend_analysis
                
                # Check for instability
                if trend_analysis.get("is_oscillating", False):
                    evolution_analysis["stability_issues"].append(f"{param_path} is oscillating")
                
                if trend_analysis.get("hits_bounds", False):
                    evolution_analysis["stability_issues"].append(f"{param_path} frequently hits bounds")
        
        # Generate recommendations
        if evolution_analysis["stability_issues"]:
            evolution_analysis["recommendations"].append("Consider reducing optimization step size")
            evolution_analysis["recommendations"].append("Review parameter bounds for overly restrictive limits")
        
        return evolution_analysis
    
    def _analyze_parameter_trend(self, param_path: str, values: List[Optional[float]], 
                               min_val: float, max_val: float) -> Dict[str, Any]:
        """Analyze trend for a single parameter"""
        # Filter out None values
        valid_values = [v for v in values if v is not None]
        
        if len(valid_values) < 2:
            return {"status": "insufficient_data"}
        
        # Calculate basic statistics
        value_range = max(valid_values) - min(valid_values)
        bound_range = max_val - min_val
        relative_range = value_range / bound_range if bound_range > 0 else 0
        
        # Detect oscillation
        direction_changes = 0
        for i in range(2, len(valid_values)):
            prev_trend = valid_values[i-1] - valid_values[i-2]
            curr_trend = valid_values[i] - valid_values[i-1]
            if prev_trend * curr_trend < 0:  # Sign change indicates oscillation
                direction_changes += 1
        
        is_oscillating = direction_changes > len(valid_values) // 3
        
        # Check boundary hitting
        boundary_hits = sum(1 for v in valid_values if v <= min_val * 1.01 or v >= max_val * 0.99)
        hits_bounds = boundary_hits > len(valid_values) // 4
        
        # Check convergence
        if len(valid_values) >= 3:
            recent_range = max(valid_values[-3:]) - min(valid_values[-3:])
            is_converging = recent_range < value_range * 0.2
        else:
            is_converging = False
        
        return {
            "value_count": len(valid_values),
            "value_range": value_range,
            "relative_range": relative_range,
            "is_oscillating": is_oscillating,
            "direction_changes": direction_changes,
            "hits_bounds": hits_bounds,
            "boundary_hits": boundary_hits,
            "is_converging": is_converging,
            "latest_value": valid_values[-1] if valid_values else None
        }
    
    def _create_default_config(self) -> Dict[str, Any]:
        """Create a sensible default configuration for comparison"""
        if self.analysis_type == "sds_page":
            return {
                "pre": {
                    "gaussian_sigma": 2.0,
                    "clip_percentile_low": 0.5,
                    "clip_percentile_high": 99.5,
                },
                "detect": {
                    "sensitivity": 0.5,
                    "expected_lanes": 10,
                    "constant_spacing": True,
                }
            }
        elif self.analysis_type == "colony_count":
            return {
                "pre": {
                    "gaussian_sigma": 1.5,
                    "clip_percentile_low": 1.0,
                    "clip_percentile_high": 99.0,
                },
                "detect": {
                    "sensitivity": 0.3,
                    "min_colony_size": 50,
                    "max_colony_size": 1000,
                }
            }
        elif self.analysis_type == "etbr_agarose":
            return {
                "pre": {
                    "gaussian_sigma": 2.5,
                    "clip_percentile_low": 1.0,
                    "clip_percentile_high": 98.5,
                },
                "detect": {
                    "sensitivity": 0.4,
                    "expected_lanes": 12,
                    "constant_spacing": True,
                }
            }
        else:
            return {"pre": {}, "detect": {}}
    
    def load_and_validate_config(self, config_path: Union[str, Path], 
                               strict_mode: bool = False) -> Tuple[Dict[str, Any], ValidationResult]:
        """
        Load configuration from file and validate
        
        Args:
            config_path: Path to configuration file (JSON or YAML)
            strict_mode: If True, reject invalid configs; if False, auto-correct
            
        Returns:
            (validated_config, validation_result)
        """
        config_path = Path(config_path)
        
        try:
            with open(config_path, 'r') as f:
                if config_path.suffix.lower() == '.json':
                    config = json.load(f)
                else:
                    # Assume YAML
                    config = yaml.safe_load(f)
        except Exception as e:
            error_msg = f"Failed to load config from {config_path}: {e}"
            logger.error(error_msg)
            return {}, ValidationResult(False, [], [error_msg])
        
        return self.validate_and_enforce_bounds(config, strict_mode)
    
    def save_validated_config(self, config: Dict[str, Any], output_path: Union[str, Path], 
                            validation_metadata: bool = True) -> None:
        """
        Save validated configuration to file
        
        Args:
            config: Configuration to save
            output_path: Output file path
            validation_metadata: If True, include validation metadata in output
        """
        output_path = Path(output_path)
        
        # Add validation metadata if requested
        output_config = copy.deepcopy(config)
        if validation_metadata:
            output_config["_validation"] = {
                "analysis_type": self.analysis_type,
                "validated": True,
                "bounds_checked": True,
                "validation_timestamp": str(pd.Timestamp.now()) if 'pd' in globals() else "unknown"
            }
        
        # Save based on file extension
        try:
            with open(output_path, 'w') as f:
                if output_path.suffix.lower() == '.json':
                    json.dump(output_config, f, indent=2)
                else:
                    # Assume YAML
                    yaml.dump(output_config, f, default_flow_style=False, indent=2)
            
            logger.info(f"Validated configuration saved to {output_path}")
        except Exception as e:
            logger.error(f"Failed to save config to {output_path}: {e}")
            raise

# Convenience functions
def validate_config_file(config_path: Union[str, Path], analysis_type: str = "sds_page",
                        strict_mode: bool = False) -> Tuple[Dict[str, Any], ValidationResult]:
    """Convenience function to validate a configuration file"""
    validator = ParameterValidator(analysis_type)
    return validator.load_and_validate_config(config_path, strict_mode)

def enforce_parameter_bounds(config: Dict[str, Any], analysis_type: str = "sds_page"
                           ) -> Tuple[Dict[str, Any], ValidationResult]:
    """Convenience function to enforce parameter bounds on a configuration"""
    validator = ParameterValidator(analysis_type)
    return validator.validate_and_enforce_bounds(config, strict_mode=False)
