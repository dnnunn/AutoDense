"""
Gating system for trigger logic in Phase IV vision-assist mode.

Determines when vision-assist optimization should be triggered based on
analysis results, failure conditions, and quality metrics.
"""

import logging
from typing import Dict, Any, List, Optional, Tuple
from enum import Enum
import math

logger = logging.getLogger(__name__)

class TriggerReason(Enum):
    """Reasons why vision-assist might be triggered."""
    DETECTION_FAILURE = "detection_failure"
    BASELINE_ISSUES = "baseline_issues"
    COVERAGE_PROBLEMS = "coverage_problems"
    STABILITY_ISSUES = "stability_issues"
    COLONY_ISSUES = "colony_issues"
    USER_REQUEST = "user_request"
    QUALITY_THRESHOLD = "quality_threshold"

class VisionGate:
    """Determines when to trigger vision-assist parameter optimization."""
    
    def __init__(self, gate_config: Dict[str, Any]):
        """
        Initialize gating system with trigger thresholds.
        
        Args:
            gate_config: Configuration with thresholds for different trigger conditions
        """
        self.config = gate_config
        self.trigger_history = []
        
    def should_trigger_vision_assist(self, run_report: Dict[str, Any], analysis_type: str) -> Dict[str, Any]:
        """
        Analyze run report to determine if vision-assist should be triggered.
        
        Args:
            run_report: Analysis results from Java pipeline
            analysis_type: Type of analysis ('sds_page', 'etbr_agarose', 'colonies_blue_white')
            
        Returns:
            Dict with trigger decision, reasons, confidence, and recommended focus areas
        """
        decision = {
            'should_trigger': False,
            'confidence': 0.0,
            'trigger_reasons': [],
            'focus_areas': [],
            'severity': 'none',
            'analysis_type': analysis_type,
            'quick_wins': [],
            'estimated_improvement': 0.0
        }
        
        # Extract key metrics from run report
        metrics = run_report.get('metrics', {})
        observation = run_report.get('observation', {})
        
        # Check each trigger condition
        triggers = self._check_all_triggers(metrics, observation, analysis_type)
        
        if triggers:
            decision['should_trigger'] = True
            decision['trigger_reasons'] = [t['reason'] for t in triggers]
            decision['confidence'] = self._calculate_trigger_confidence(triggers)
            decision['focus_areas'] = self._identify_focus_areas(triggers, analysis_type)
            decision['severity'] = self._assess_severity(triggers)
            decision['quick_wins'] = self._identify_quick_wins(triggers, analysis_type)
            decision['estimated_improvement'] = self._estimate_improvement(triggers)
        
        # Log decision
        self._log_trigger_decision(decision)
        
        return decision
    
    def _check_all_triggers(self, metrics: Dict[str, Any], observation: Dict[str, Any], analysis_type: str) -> List[Dict[str, Any]]:
        """
        Check all possible trigger conditions.
        
        Args:
            metrics: Analysis metrics
            observation: Analysis observations
            analysis_type: Type of analysis
            
        Returns:
            List of triggered conditions with details
        """
        triggers = []
        
        # Detection failure triggers
        detection_triggers = self._check_detection_failures(metrics, analysis_type)
        triggers.extend(detection_triggers)
        
        # Baseline issue triggers
        baseline_triggers = self._check_baseline_issues(observation)
        triggers.extend(baseline_triggers)
        
        # Coverage problem triggers
        coverage_triggers = self._check_coverage_problems(observation)
        triggers.extend(coverage_triggers)
        
        # Stability issue triggers
        stability_triggers = self._check_stability_issues(observation)
        triggers.extend(stability_triggers)
        
        # Analysis-specific triggers
        if analysis_type == 'colonies_blue_white':
            colony_triggers = self._check_colony_issues(metrics, observation)
            triggers.extend(colony_triggers)
        
        return triggers
    
    def _check_detection_failures(self, metrics: Dict[str, Any], analysis_type: str) -> List[Dict[str, Any]]:
        """Check for detection failures that warrant vision-assist."""
        triggers = []
        
        lanes_raw = metrics.get('lanes_raw', 0)
        bands_raw = metrics.get('bands_raw', 0)
        
        # Lane detection failure
        if lanes_raw == 0:
            triggers.append({
                'reason': TriggerReason.DETECTION_FAILURE,
                'details': 'No lanes detected',
                'severity': 'critical',
                'confidence': 0.95,
                'target_improvement': 'lane_detection'
            })
        
        # Band detection failure (gel analyses only)
        if analysis_type in ['sds_page', 'etbr_agarose'] and bands_raw == 0:
            triggers.append({
                'reason': TriggerReason.DETECTION_FAILURE,
                'details': 'No bands detected',
                'severity': 'high',
                'confidence': 0.85,
                'target_improvement': 'band_detection'
            })
        
        # Colony detection failure
        if analysis_type == 'colonies_blue_white':
            colony_count = metrics.get('colony_count', 0)
            if colony_count == 0:
                triggers.append({
                    'reason': TriggerReason.DETECTION_FAILURE,
                    'details': 'No colonies detected',
                    'severity': 'critical',
                    'confidence': 0.90,
                    'target_improvement': 'colony_detection'
                })
        
        return triggers
    
    def _check_baseline_issues(self, observation: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Check for baseline processing issues."""
        triggers = []
        
        # Check baseline statistics
        baseline_post_med = observation.get('baseline_post_med', None)
        profile_zero_frac = observation.get('profile_zero_frac_after', None)
        
        # Baseline not effective (median still high after removal)
        if baseline_post_med is not None and baseline_post_med > 0.1:
            triggers.append({
                'reason': TriggerReason.BASELINE_ISSUES,
                'details': f'Baseline removal ineffective (post_med={baseline_post_med:.3f})',
                'severity': 'medium',
                'confidence': 0.75,
                'target_improvement': 'baseline_parameters'
            })
        
        # Profile shows too many zeros after baseline removal
        if profile_zero_frac is not None and profile_zero_frac > 0.7:
            triggers.append({
                'reason': TriggerReason.BASELINE_ISSUES,
                'details': f'Excessive zero fraction after baseline ({profile_zero_frac:.3f})',
                'severity': 'medium',
                'confidence': 0.70,
                'target_improvement': 'baseline_parameters'
            })
        
        return triggers
    
    def _check_coverage_problems(self, observation: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Check for coverage and signal quality issues."""
        triggers = []
        
        coverage_total = observation.get('coverage_total', None)
        coverage_lanes = observation.get('coverage_lanes', None)
        
        # Low total coverage
        if coverage_total is not None and coverage_total < 0.2:
            triggers.append({
                'reason': TriggerReason.COVERAGE_PROBLEMS,
                'details': f'Low total coverage ({coverage_total:.3f})',
                'severity': 'high',
                'confidence': 0.80,
                'target_improvement': 'sensitivity_parameters'
            })
        
        # Low lane coverage
        if coverage_lanes is not None and coverage_lanes < 0.15:
            triggers.append({
                'reason': TriggerReason.COVERAGE_PROBLEMS,
                'details': f'Low lane coverage ({coverage_lanes:.3f})',
                'severity': 'medium',
                'confidence': 0.70,
                'target_improvement': 'lane_parameters'
            })
        
        return triggers
    
    def _check_stability_issues(self, observation: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Check for stability and consistency issues."""
        triggers = []
        
        count_stability = observation.get('count_stability_score', None)
        
        # Low stability score
        if count_stability is not None and count_stability < 0.7:
            triggers.append({
                'reason': TriggerReason.STABILITY_ISSUES,
                'details': f'Low count stability ({count_stability:.3f})',
                'severity': 'medium',
                'confidence': 0.65,
                'target_improvement': 'robustness_parameters'
            })
        
        return triggers
    
    def _check_colony_issues(self, metrics: Dict[str, Any], observation: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Check for colony-specific issues."""
        triggers = []
        
        colonies_raw = metrics.get('colonies_raw', 0)
        blue_count = metrics.get('blue_count', 0)
        white_count = metrics.get('white_count', 0)
        
        # Classification failure
        total_classified = blue_count + white_count
        if colonies_raw > 0 and total_classified < colonies_raw * 0.8:
            triggers.append({
                'reason': TriggerReason.COLONY_ISSUES,
                'details': f'Poor classification: {total_classified}/{colonies_raw} classified',
                'severity': 'medium',
                'confidence': 0.75,
                'target_improvement': 'color_classification'
            })
        
        # Check filter chain efficiency
        mask_info = observation.get('mask', {})
        components_raw = mask_info.get('components_raw', 0)
        final_count = mask_info.get('filter_chain', {}).get('final_count', 0)
        
        if components_raw > 0 and final_count < components_raw * 0.3:
            triggers.append({
                'reason': TriggerReason.COLONY_ISSUES,
                'details': f'Aggressive filtering: {final_count}/{components_raw} survived',
                'severity': 'low',
                'confidence': 0.60,
                'target_improvement': 'filtering_parameters'
            })
        
        return triggers
    
    def _calculate_trigger_confidence(self, triggers: List[Dict[str, Any]]) -> float:
        """Calculate overall confidence in trigger decision."""
        if not triggers:
            return 0.0
        
        # Weighted average of individual confidences
        total_weight = 0
        weighted_sum = 0
        
        for trigger in triggers:
            confidence = trigger.get('confidence', 0.5)
            weight = self._get_severity_weight(trigger.get('severity', 'low'))
            
            weighted_sum += confidence * weight
            total_weight += weight
        
        return weighted_sum / total_weight if total_weight > 0 else 0.0
    
    def _identify_focus_areas(self, triggers: List[Dict[str, Any]], analysis_type: str) -> List[str]:
        """Identify specific parameter areas to focus optimization on."""
        focus_areas = []
        
        target_improvements = [t.get('target_improvement') for t in triggers if t.get('target_improvement')]
        
        # Map target improvements to focus areas
        focus_mapping = {
            'lane_detection': ['lanes.prominence_frac', 'lanes.min_peak_distance_frac'],
            'band_detection': ['bands.prominence_frac', 'bands.min_peak_distance_px'],
            'baseline_parameters': ['bands.baseline.window_frac', 'bands.baseline.quantile'],
            'sensitivity_parameters': ['lanes.prominence_frac', 'bands.prominence_frac'],
            'colony_detection': ['threshold.method', 'threshold.radius'],
            'color_classification': ['colorspace']
        }
        
        for improvement in target_improvements:
            if improvement in focus_mapping:
                focus_areas.extend(focus_mapping[improvement])
        
        return list(set(focus_areas))  # Remove duplicates
    
    def _assess_severity(self, triggers: List[Dict[str, Any]]) -> str:
        """Assess overall severity of triggered conditions."""
        severities = [t.get('severity', 'low') for t in triggers]
        
        if 'critical' in severities:
            return 'critical'
        elif 'high' in severities:
            return 'high'
        elif 'medium' in severities:
            return 'medium'
        else:
            return 'low'
    
    def _identify_quick_wins(self, triggers: List[Dict[str, Any]], analysis_type: str) -> List[str]:
        """Identify quick optimization wins based on triggers."""
        quick_wins = []
        
        for trigger in triggers:
            reason = trigger.get('reason')
            
            if reason == TriggerReason.BASELINE_ISSUES:
                quick_wins.append("Adjust baseline window size for better background removal")
            elif reason == TriggerReason.COVERAGE_PROBLEMS:
                quick_wins.append("Reduce prominence threshold to increase sensitivity")
            elif reason == TriggerReason.STABILITY_ISSUES:
                quick_wins.append("Optimize min_distance parameter for more stable detection")
        
        return list(set(quick_wins))
    
    def _estimate_improvement(self, triggers: List[Dict[str, Any]]) -> float:
        """Estimate potential improvement from vision-assist optimization."""
        if not triggers:
            return 0.0
        
        # Simple heuristic based on severity and confidence
        improvement = 0.0
        
        for trigger in triggers:
            severity = trigger.get('severity', 'low')
            confidence = trigger.get('confidence', 0.5)
            
            severity_multiplier = {
                'critical': 0.5,
                'high': 0.3,
                'medium': 0.2,
                'low': 0.1
            }.get(severity, 0.1)
            
            improvement += severity_multiplier * confidence
        
        return min(improvement, 0.8)  # Cap at 80% estimated improvement
    
    def _get_severity_weight(self, severity: str) -> float:
        """Get weighting factor for severity level."""
        weights = {
            'critical': 4.0,
            'high': 3.0,
            'medium': 2.0,
            'low': 1.0
        }
        return weights.get(severity, 1.0)
    
    def _log_trigger_decision(self, decision: Dict[str, Any]) -> None:
        """Log trigger decision for debugging and auditing."""
        self.trigger_history.append(decision)
        
        if decision['should_trigger']:
            logger.info(f"🎯 Vision-assist triggered ({decision['severity']}): {decision['analysis_type']}")
            logger.info(f"   Reasons: {[r.value for r in decision['trigger_reasons']]}")
            logger.info(f"   Confidence: {decision['confidence']:.2f}")
            logger.info(f"   Focus areas: {decision['focus_areas']}")
        else:
            logger.debug(f"✅ No vision-assist trigger: {decision['analysis_type']}")

def create_default_gate_config() -> Dict[str, Any]:
    """Create default gating configuration."""
    return {
        'thresholds': {
            'coverage_min': 0.2,
            'stability_min': 0.7,
            'baseline_post_med_max': 0.1,
            'zero_frac_max': 0.7
        },
        'severity_weights': {
            'critical': 4.0,
            'high': 3.0,
            'medium': 2.0,
            'low': 1.0
        }
    }

def test_vision_gate():
    """Test the vision gate with sample run reports."""
    gate = VisionGate(create_default_gate_config())
    
    # Test with problematic SDS run
    problematic_sds = {
        'metrics': {'lanes_raw': 3, 'bands_raw': 0},  # Missing bands
        'observation': {
            'coverage_total': 0.15,  # Low coverage
            'baseline_post_med': 0.25  # Ineffective baseline
        }
    }
    
    decision = gate.should_trigger_vision_assist(problematic_sds, 'sds_page')
    print(f"🧪 Problematic SDS test: trigger={decision['should_trigger']}")
    print(f"   Reasons: {decision['trigger_reasons']}")
    print(f"   Confidence: {decision['confidence']:.2f}")
    
    # Test with good colony run
    good_colony = {
        'metrics': {'colonies_raw': 25, 'blue_count': 12, 'white_count': 13},
        'observation': {
            'coverage': {'colonies': 0.75},
            'mask': {'components_raw': 25, 'filter_chain': {'final_count': 25}}
        }
    }
    
    decision = gate.should_trigger_vision_assist(good_colony, 'colonies_blue_white')
    print(f"🧪 Good colony test: trigger={decision['should_trigger']}")

if __name__ == "__main__":
    test_vision_gate()