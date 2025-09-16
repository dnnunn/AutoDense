# autodense/security/scientific_validator.py
"""
Scientific validation layer for AI-generated preprocessing decisions.
CRITICAL: Prevents AI hallucinations from corrupting scientific results.
"""

import logging
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
import numpy as np

logger = logging.getLogger(__name__)

@dataclass
class ValidationRule:
    """A single scientific validation rule"""
    name: str
    description: str
    severity: str  # "error", "warning", "info"
    
@dataclass 
class ValidationResult:
    """Result of scientific validation"""
    is_valid: bool
    confidence_adjustment: float  # Factor to adjust AI confidence (0.0-1.0)
    warnings: List[str]
    errors: List[str]
    scientific_reasoning: str


class ScientificValidator:
    """
    Validates AI preprocessing decisions against established scientific principles.
    
    Purpose: Prevent AI hallucinations from corrupting gel electrophoresis analysis.
    
    Validation Categories:
    1. Physical plausibility (e.g., rotation limits, parameter bounds)
    2. Scientific appropriateness (e.g., don't enhance already clear gels)
    3. Consistency checking (e.g., AI confidence vs. measured metrics)
    4. Domain knowledge validation (e.g., gel electrophoresis best practices)
    """
    
    def __init__(self):
        self.validation_rules = self._initialize_validation_rules()
    
    def _initialize_validation_rules(self) -> Dict[str, ValidationRule]:
        """Initialize scientific validation rules"""
        return {
            # Physical plausibility rules
            "deskew_angle_bounds": ValidationRule(
                "Deskew Angle Bounds",
                "Deskew angles should be reasonable for gel electrophoresis (typically < 10°)",
                "error"
            ),
            "minimal_skew_threshold": ValidationRule(
                "Minimal Skew Threshold", 
                "Don't deskew if skew is minimal (< 0.5°) - may introduce artifacts",
                "warning"
            ),
            
            # Scientific appropriateness rules  
            "background_illumination_check": ValidationRule(
                "Background Illumination Check",
                "Don't apply background correction if illumination is already uniform",
                "warning"
            ),
            "contrast_enhancement_necessity": ValidationRule(
                "Contrast Enhancement Necessity",
                "Don't enhance contrast if SNR is already high (> 4.0)",
                "warning" 
            ),
            "destripe_noise_threshold": ValidationRule(
                "Destripe Noise Threshold",
                "Only destripe if column noise is significantly higher than row noise",
                "warning"
            ),
            
            # Consistency checking rules
            "ai_confidence_metric_consistency": ValidationRule(
                "AI Confidence vs Metrics Consistency",
                "AI confidence should align with objective image quality metrics",
                "warning"
            ),
            "preprocessing_necessity_check": ValidationRule(
                "Preprocessing Necessity Check",
                "High-quality images may not need preprocessing - verify necessity",
                "info"
            )
        }
    
    def validate_preprocessing_decision(self, 
                                      ai_recommendation: str,
                                      ai_confidence: float,
                                      ai_reasoning: str,
                                      image_metrics: Dict[str, float],
                                      image_shape: Optional[Tuple[int, int]] = None) -> ValidationResult:
        """
        Validate AI preprocessing decision against scientific principles.
        
        Args:
            ai_recommendation: AI's recommended preprocessing method
            ai_confidence: AI's confidence score (0.0-1.0)
            ai_reasoning: AI's reasoning for the decision
            image_metrics: Objective image metrics (SNR, skew, etc.)
            image_shape: Image dimensions if available
            
        Returns:
            ValidationResult with validation outcome and adjustments
        """
        warnings = []
        errors = []
        confidence_adjustment = 1.0  # Default: no adjustment
        
        # Validate based on recommendation type
        if ai_recommendation == "deskew":
            result = self._validate_deskew_decision(ai_confidence, image_metrics)
            warnings.extend(result["warnings"])
            errors.extend(result["errors"])
            confidence_adjustment *= result["confidence_factor"]
            
        elif ai_recommendation == "background":
            result = self._validate_background_decision(ai_confidence, image_metrics)
            warnings.extend(result["warnings"])
            errors.extend(result["errors"])
            confidence_adjustment *= result["confidence_factor"]
            
        elif ai_recommendation == "clahe":
            result = self._validate_clahe_decision(ai_confidence, image_metrics)
            warnings.extend(result["warnings"])
            errors.extend(result["errors"])
            confidence_adjustment *= result["confidence_factor"]
            
        elif ai_recommendation == "destripe":
            result = self._validate_destripe_decision(ai_confidence, image_metrics)
            warnings.extend(result["warnings"])  
            errors.extend(result["errors"])
            confidence_adjustment *= result["confidence_factor"]
            
        elif ai_recommendation == "none":
            result = self._validate_none_decision(ai_confidence, image_metrics)
            warnings.extend(result["warnings"])
            errors.extend(result["errors"])
            confidence_adjustment *= result["confidence_factor"]
        
        # Cross-validation: Check AI confidence against objective metrics
        consistency_result = self._validate_confidence_consistency(
            ai_recommendation, ai_confidence, image_metrics
        )
        warnings.extend(consistency_result["warnings"])
        
        # Generate scientific reasoning for the validation
        scientific_reasoning = self._generate_scientific_reasoning(
            ai_recommendation, ai_reasoning, image_metrics, warnings, errors
        )
        
        # Determine overall validity
        is_valid = len(errors) == 0
        
        return ValidationResult(
            is_valid=is_valid,
            confidence_adjustment=max(0.1, min(1.0, confidence_adjustment)),  # Clamp to [0.1, 1.0]
            warnings=warnings,
            errors=errors,
            scientific_reasoning=scientific_reasoning
        )
    
    def _validate_deskew_decision(self, ai_confidence: float, metrics: Dict[str, float]) -> Dict:
        """Validate deskew preprocessing decision"""
        warnings = []
        errors = []
        confidence_factor = 1.0
        
        skew_angle = abs(metrics.get('skew', 0.0))
        
        # Critical error: Extreme skew angles are physically implausible
        if skew_angle > 15.0:
            errors.append(f"Extreme skew angle ({skew_angle:.1f}°) detected - likely measurement error")
            confidence_factor *= 0.1
        
        # Warning: Minimal skew may not need correction
        if skew_angle < 0.5:
            warnings.append(f"Minimal skew ({skew_angle:.2f}°) - correction may introduce artifacts")
            confidence_factor *= 0.7
        
        # Warning: High confidence for borderline skew
        if 0.5 <= skew_angle <= 1.0 and ai_confidence > 0.8:
            warnings.append("High AI confidence for borderline skew - verify necessity")
            confidence_factor *= 0.8
        
        return {
            "warnings": warnings,
            "errors": errors,
            "confidence_factor": confidence_factor
        }
    
    def _validate_background_decision(self, ai_confidence: float, metrics: Dict[str, float]) -> Dict:
        """Validate background correction decision"""
        warnings = []
        errors = []
        confidence_factor = 1.0
        
        illum_amp = metrics.get('illum_amp', 0.0)
        
        # Warning: Background correction on uniform illumination
        if illum_amp < 0.05:
            warnings.append(f"Background correction suggested but illumination appears uniform (amp={illum_amp:.3f})")
            confidence_factor *= 0.6
        
        # Warning: High confidence for borderline illumination
        if 0.05 <= illum_amp <= 0.08 and ai_confidence > 0.8:
            warnings.append("High AI confidence for borderline illumination non-uniformity")
            confidence_factor *= 0.8
        
        return {
            "warnings": warnings,
            "errors": errors, 
            "confidence_factor": confidence_factor
        }
    
    def _validate_clahe_decision(self, ai_confidence: float, metrics: Dict[str, float]) -> Dict:
        """Validate contrast enhancement decision"""
        warnings = []
        errors = []
        confidence_factor = 1.0
        
        snr = metrics.get('snr', 0.0)
        
        # Warning: Enhancing already high-contrast images
        if snr > 4.0:
            warnings.append(f"Contrast enhancement suggested but SNR is already high ({snr:.2f})")
            confidence_factor *= 0.6
        
        # Info: Appropriate for low SNR
        if snr < 2.0:
            # This is appropriate - no penalty
            pass
        
        return {
            "warnings": warnings,
            "errors": errors,
            "confidence_factor": confidence_factor
        }
    
    def _validate_destripe_decision(self, ai_confidence: float, metrics: Dict[str, float]) -> Dict:
        """Validate destripe decision"""
        warnings = []
        errors = []
        confidence_factor = 1.0
        
        stripe_ratio = metrics.get('stripe_ratio', 1.0)
        
        # Warning: Destriping when stripe ratio is not significant
        if stripe_ratio < 1.25:
            warnings.append(f"Destripe suggested but stripe ratio is not significant ({stripe_ratio:.2f})")
            confidence_factor *= 0.7
        
        return {
            "warnings": warnings,
            "errors": errors,
            "confidence_factor": confidence_factor
        }
    
    def _validate_none_decision(self, ai_confidence: float, metrics: Dict[str, float]) -> Dict:
        """Validate decision to apply no preprocessing"""
        warnings = []
        errors = []
        confidence_factor = 1.0
        
        skew = abs(metrics.get('skew', 0.0))
        snr = metrics.get('snr', 0.0)
        illum_amp = metrics.get('illum_amp', 0.0)
        
        # Warning: Clear issues present but AI says "none"
        clear_issues = []
        if skew > 2.0:
            clear_issues.append(f"significant skew ({skew:.1f}°)")
        if snr < 1.5:
            clear_issues.append(f"low SNR ({snr:.2f})")
        if illum_amp > 0.15:
            clear_issues.append(f"uneven illumination (amp={illum_amp:.2f})")
        
        if clear_issues:
            warnings.append(f"AI suggests no preprocessing but metrics indicate: {', '.join(clear_issues)}")
            confidence_factor *= 0.5
        
        return {
            "warnings": warnings,
            "errors": errors,
            "confidence_factor": confidence_factor
        }
    
    def _validate_confidence_consistency(self, recommendation: str, ai_confidence: float, 
                                       metrics: Dict[str, float]) -> Dict:
        """Validate that AI confidence aligns with objective metrics"""
        warnings = []
        
        # Calculate "objective confidence" based on how clear-cut the metrics are
        objective_confidence = self._calculate_objective_confidence(recommendation, metrics)
        
        # Flag large discrepancies
        confidence_diff = abs(ai_confidence - objective_confidence)
        if confidence_diff > 0.4:
            if ai_confidence > objective_confidence:
                warnings.append(f"AI confidence ({ai_confidence:.2f}) seems high given objective metrics (estimated {objective_confidence:.2f})")
            else:
                warnings.append(f"AI confidence ({ai_confidence:.2f}) seems low given clear objective metrics (estimated {objective_confidence:.2f})")
        
        return {"warnings": warnings}
    
    def _calculate_objective_confidence(self, recommendation: str, metrics: Dict[str, float]) -> float:
        """Calculate objective confidence based on metrics"""
        if recommendation == "deskew":
            skew = abs(metrics.get('skew', 0.0))
            if skew > 3.0:
                return 0.9
            elif skew > 1.5:
                return 0.7
            elif skew > 0.5:
                return 0.5
            else:
                return 0.2
        
        elif recommendation == "background":
            illum_amp = metrics.get('illum_amp', 0.0)
            if illum_amp > 0.2:
                return 0.9
            elif illum_amp > 0.1:
                return 0.7
            elif illum_amp > 0.05:
                return 0.5
            else:
                return 0.2
        
        elif recommendation == "clahe":
            snr = metrics.get('snr', 0.0)
            if snr < 1.0:
                return 0.9
            elif snr < 2.0:
                return 0.7
            elif snr < 3.0:
                return 0.5
            else:
                return 0.2
        
        # Default moderate confidence
        return 0.6
    
    def _generate_scientific_reasoning(self, ai_recommendation: str, ai_reasoning: str,
                                     metrics: Dict[str, float], warnings: List[str], 
                                     errors: List[str]) -> str:
        """Generate scientific reasoning for the validation decision"""
        reasoning_parts = [f"AI Decision: {ai_recommendation}"]
        
        # Add objective metrics
        skew = abs(metrics.get('skew', 0.0))
        snr = metrics.get('snr', 0.0)
        illum_amp = metrics.get('illum_amp', 0.0)
        stripe_ratio = metrics.get('stripe_ratio', 1.0)
        
        reasoning_parts.append(f"Objective Metrics: skew={skew:.2f}°, SNR={snr:.2f}, illumination_amp={illum_amp:.3f}, stripe_ratio={stripe_ratio:.2f}")
        
        # Add scientific assessment
        if errors:
            reasoning_parts.append(f"SCIENTIFIC CONCERNS: {'; '.join(errors)}")
        
        if warnings:
            reasoning_parts.append(f"Scientific Notes: {'; '.join(warnings[:2])}")  # Limit to 2 warnings
        
        # Add validation outcome
        if not errors:
            reasoning_parts.append("✅ Scientifically validated")
        else:
            reasoning_parts.append("❌ Failed scientific validation")
        
        return " | ".join(reasoning_parts)