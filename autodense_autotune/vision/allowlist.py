"""
Allowlist bounds validation system for Phase IV vision-assist mode.

Ensures all AI parameter suggestions remain within safe, tested bounds
while providing clear validation feedback for proposed changes.
"""

import logging
from typing import Dict, Any, List, Tuple, Optional, Union
import copy

logger = logging.getLogger(__name__)

class AllowlistValidator:
    """Validates parameter proposals against predefined safe bounds."""
    
    def __init__(self, allowlist_config: Dict[str, Any]):
        """
        Initialize validator with allowlist configuration.
        
        Args:
            allowlist_config: Nested dict with parameter bounds for each analysis type
        """
        self.allowlist = allowlist_config
        self.validation_history = []
        
    def validate_proposal(self, analysis_type: str, proposal: Dict[str, Any]) -> Dict[str, Any]:
        """
        Validate a parameter change proposal against allowlist bounds.
        
        Args:
            analysis_type: Type of analysis ('sds', 'etbr', 'colony')
            proposal: Dict of parameter changes to validate
            
        Returns:
            Validation result with approved changes, violations, and metadata
        """
        result = {
            'valid': True,
            'approved_changes': {},
            'violations': [],
            'warnings': [],
            'bounds_applied': {},
            'analysis_type': analysis_type,
            'safety_level': 'unknown'
        }
        
        # Get allowlist for this analysis type
        type_allowlist = self.allowlist.get(analysis_type, {})
        if not type_allowlist:
            result['valid'] = False
            result['violations'].append(f"No allowlist defined for analysis type: {analysis_type}")
            return result
        
        # Validate each proposed change
        for param_path, proposed_value in proposal.items():
            validation = self._validate_single_parameter(
                param_path, proposed_value, type_allowlist
            )
            
            if validation['valid']:
                result['approved_changes'][param_path] = validation['approved_value']
                if 'bounds_applied' in validation:
                    result['bounds_applied'][param_path] = validation['bounds_applied']
            else:
                result['valid'] = False
                result['violations'].extend(validation['violations'])
                
            if validation.get('warnings'):
                result['warnings'].extend(validation['warnings'])
        
        # Determine overall safety level
        result['safety_level'] = self._assess_safety_level(result)
        
        # Log validation result
        self._log_validation(result)
        
        return result
    
    def _validate_single_parameter(self, param_path: str, value: Any, allowlist: Dict[str, Any]) -> Dict[str, Any]:
        """
        Validate a single parameter against its allowlist constraints.
        
        Args:
            param_path: Dot-separated parameter path (e.g., 'lanes.prominence_frac')
            value: Proposed parameter value
            allowlist: Allowlist configuration for current analysis type
            
        Returns:
            Validation result for this parameter
        """
        result = {
            'valid': True,
            'approved_value': value,
            'violations': [],
            'warnings': []
        }
        
        # Navigate to parameter bounds in allowlist
        bounds = self._get_parameter_bounds(param_path, allowlist)
        
        if bounds is None:
            result['valid'] = False
            result['violations'].append(f"Parameter not in allowlist: {param_path}")
            return result
        
        # Validate based on bounds type
        if isinstance(bounds, list) and len(bounds) == 2:
            # Numeric range [min, max]
            validated = self._validate_numeric_range(value, bounds, param_path)
            result.update(validated)
            
        elif isinstance(bounds, list) and len(bounds) > 2:
            # Allowed values list
            validated = self._validate_allowed_values(value, bounds, param_path)
            result.update(validated)
            
        elif isinstance(bounds, dict):
            # Complex validation rules
            validated = self._validate_complex_rules(value, bounds, param_path)
            result.update(validated)
            
        else:
            result['valid'] = False
            result['violations'].append(f"Invalid bounds format for {param_path}: {bounds}")
        
        return result
    
    def _get_parameter_bounds(self, param_path: str, allowlist: Dict[str, Any]) -> Optional[Any]:
        """
        Navigate nested allowlist to find bounds for a parameter path.
        
        Args:
            param_path: Dot-separated path like 'lanes.prominence_frac'
            allowlist: Allowlist configuration dict
            
        Returns:
            Bounds configuration or None if not found
        """
        parts = param_path.split('.')
        current = allowlist
        
        for part in parts:
            if isinstance(current, dict) and part in current:
                current = current[part]
            else:
                return None
                
        return current
    
    def _validate_numeric_range(self, value: Union[int, float], bounds: List[float], param_path: str) -> Dict[str, Any]:
        """
        Validate numeric value against [min, max] bounds.
        
        Args:
            value: Proposed numeric value
            bounds: [min_value, max_value] list
            param_path: Parameter path for error messages
            
        Returns:
            Validation result with clamping if needed
        """
        result = {
            'valid': True,
            'approved_value': value,
            'violations': [],
            'warnings': []
        }
        
        min_val, max_val = bounds
        
        # Type check
        if not isinstance(value, (int, float)):
            result['valid'] = False
            result['violations'].append(f"{param_path}: Expected numeric value, got {type(value)}")
            return result
        
        # Range validation with clamping
        if value < min_val:
            result['approved_value'] = min_val
            result['bounds_applied'] = {'clamped_from': value, 'clamped_to': min_val, 'reason': 'below_minimum'}
            result['warnings'].append(f"{param_path}: Clamped {value} to minimum {min_val}")
            
        elif value > max_val:
            result['approved_value'] = max_val
            result['bounds_applied'] = {'clamped_from': value, 'clamped_to': max_val, 'reason': 'above_maximum'}
            result['warnings'].append(f"{param_path}: Clamped {value} to maximum {max_val}")
        
        return result
    
    def _validate_allowed_values(self, value: Any, allowed_values: List[Any], param_path: str) -> Dict[str, Any]:
        """
        Validate value against list of allowed values.
        
        Args:
            value: Proposed value
            allowed_values: List of acceptable values
            param_path: Parameter path for error messages
            
        Returns:
            Validation result
        """
        result = {
            'valid': True,
            'approved_value': value,
            'violations': [],
            'warnings': []
        }
        
        if value not in allowed_values:
            result['valid'] = False
            result['violations'].append(
                f"{param_path}: Value '{value}' not in allowed list {allowed_values}"
            )
        
        return result
    
    def _validate_complex_rules(self, value: Any, rules: Dict[str, Any], param_path: str) -> Dict[str, Any]:
        """
        Validate value against complex rules (dependencies, conditionals, etc.).
        
        Args:
            value: Proposed value
            rules: Complex validation rules dict
            param_path: Parameter path for error messages
            
        Returns:
            Validation result
        """
        result = {
            'valid': True,
            'approved_value': value,
            'violations': [],
            'warnings': []
        }
        
        # For now, implement basic rule types
        # This can be extended for more sophisticated constraints
        
        if 'range' in rules:
            range_result = self._validate_numeric_range(value, rules['range'], param_path)
            result.update(range_result)
            
        if 'depends_on' in rules:
            # Placeholder for parameter dependencies
            result['warnings'].append(f"{param_path}: Parameter has dependencies (not fully validated)")
            
        return result
    
    def _assess_safety_level(self, validation_result: Dict[str, Any]) -> str:
        """
        Assess overall safety level of the validation result.
        
        Args:
            validation_result: Full validation result
            
        Returns:
            Safety level string ('safe', 'caution', 'unsafe')
        """
        if not validation_result['valid']:
            return 'unsafe'
        
        if validation_result['violations']:
            return 'unsafe'
            
        if validation_result['bounds_applied']:
            return 'caution'  # Values were clamped
            
        if validation_result['warnings']:
            return 'caution'
            
        return 'safe'
    
    def _log_validation(self, result: Dict[str, Any]) -> None:
        """
        Log validation result for auditing and debugging.
        
        Args:
            result: Validation result to log
        """
        self.validation_history.append({
            'result': copy.deepcopy(result),
            'timestamp': None  # Would add timestamp in production
        })
        
        if result['valid']:
            logger.info(f"✅ Validation passed ({result['safety_level']}): {result['analysis_type']}")
            if result['bounds_applied']:
                logger.info(f"   Bounds applied: {result['bounds_applied']}")
        else:
            logger.warning(f"❌ Validation failed: {result['violations']}")
    
    def get_bounds_summary(self, analysis_type: str) -> Dict[str, Any]:
        """
        Get summary of all parameter bounds for an analysis type.
        
        Args:
            analysis_type: Analysis type to get bounds for
            
        Returns:
            Summary of parameter bounds and constraints
        """
        if analysis_type not in self.allowlist:
            return {'error': f'No allowlist for analysis type: {analysis_type}'}
        
        return {
            'analysis_type': analysis_type,
            'parameter_bounds': self.allowlist[analysis_type],
            'total_parameters': self._count_parameters(self.allowlist[analysis_type])
        }
    
    def _count_parameters(self, bounds_dict: Dict[str, Any], prefix: str = "") -> int:
        """Recursively count parameters in bounds dictionary."""
        count = 0
        for key, value in bounds_dict.items():
            if isinstance(value, dict):
                count += self._count_parameters(value, f"{prefix}{key}.")
            else:
                count += 1
        return count

def create_default_allowlist() -> Dict[str, Any]:
    """
    Create default allowlist configuration for Phase IV vision-assist mode.
    
    Returns:
        Default allowlist with safe parameter bounds for all analysis types
    """
    return {
        'sds': {
            'lanes': {
                'min_peak_distance_frac': [0.02, 0.06],
                'prominence_frac': [0.02, 0.12]
            },
            'bands': {
                'prominence_frac': [0.01, 0.08],
                'baseline': {
                    'window_frac': [0.005, 0.03],
                    'quantile': [0.05, 0.20]
                },
                'min_peak_distance_px': [6, 18]
            }
        },
        'etbr': {
            'lanes': {
                'min_peak_distance_frac': [0.02, 0.08],
                'prominence_frac': [0.03, 0.15]
            },
            'bands': {
                'prominence_frac': [0.02, 0.10],
                'baseline': {
                    'window_frac': [0.005, 0.025],
                    'quantile': [0.06, 0.25]
                },
                'min_peak_distance_px': [8, 20]
            }
        },
        'colony': {
            'threshold': {
                'method': ['Otsu', 'Phansalkar', 'Huang'],
                'radius': [10, 25]
            },
            'colorspace': ['Lab', 'HSV'],
            'min_colony_size': [3, 15],
            'max_colony_size': [500, 2000]
        }
    }

def test_allowlist_validator():
    """Test the allowlist validator with various proposals."""
    validator = AllowlistValidator(create_default_allowlist())
    
    # Test valid SDS proposal
    valid_proposal = {
        'lanes.prominence_frac': 0.08,
        'bands.min_peak_distance_px': 12
    }
    
    result = validator.validate_proposal('sds', valid_proposal)
    print(f"✅ Valid proposal test: {result['valid']}")
    
    # Test proposal requiring clamping
    clamp_proposal = {
        'lanes.prominence_frac': 0.25,  # Above max of 0.12
        'bands.baseline.quantile': 0.03  # Below min of 0.05
    }
    
    result = validator.validate_proposal('sds', clamp_proposal)
    print(f"⚠️  Clamp proposal test: {result['valid']}, safety: {result['safety_level']}")
    print(f"   Bounds applied: {result['bounds_applied']}")
    
    # Test invalid proposal
    invalid_proposal = {
        'nonexistent.parameter': 0.5
    }
    
    result = validator.validate_proposal('sds', invalid_proposal)
    print(f"❌ Invalid proposal test: {result['valid']}")

if __name__ == "__main__":
    test_allowlist_validator()