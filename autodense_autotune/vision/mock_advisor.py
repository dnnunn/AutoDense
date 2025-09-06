"""
Mock advisor for Phase IV vision-assist end-to-end testing.

Provides deterministic parameter suggestions for testing the vision-assist
pipeline without requiring actual Gemini API calls.
"""

import logging
import random
from typing import Dict, Any, List, Optional
import time

logger = logging.getLogger(__name__)

class MockVisionAdvisor:
    """Mock advisor that simulates intelligent parameter optimization suggestions."""
    
    def __init__(self, config: Dict[str, Any]):
        """
        Initialize mock advisor with configuration.
        
        Args:
            config: Mock advisor configuration
        """
        self.config = config
        self.suggestion_history = []
        self.deterministic = config.get('deterministic', True)
        
        # Set random seed for deterministic testing
        if self.deterministic:
            random.seed(42)
    
    def analyze_and_suggest(
        self, 
        base64_image: str, 
        run_report: Dict[str, Any], 
        focus_areas: List[str],
        analysis_type: str
    ) -> Dict[str, Any]:
        """
        Analyze image and metrics to provide parameter optimization suggestions.
        
        Args:
            base64_image: Base64-encoded cropped image
            run_report: Analysis results from Java pipeline
            focus_areas: Parameter areas to focus on (from gating system)
            analysis_type: Type of analysis ('sds', 'etbr', 'colony')
            
        Returns:
            Dict with parameter suggestions, confidence, and reasoning
        """
        # Simulate analysis delay
        time.sleep(0.5)
        
        suggestion = {
            'suggestions': {},
            'confidence': 0.0,
            'reasoning': [],
            'expected_improvements': {},
            'risk_assessment': 'low',
            'analysis_type': analysis_type,
            'focus_areas_addressed': focus_areas,
            'image_insights': self._mock_image_analysis(base64_image),
            'advisor_type': 'mock'
        }
        
        # Generate suggestions based on focus areas and analysis type
        if focus_areas:
            suggestion['suggestions'] = self._generate_focused_suggestions(
                focus_areas, run_report, analysis_type
            )
        else:
            suggestion['suggestions'] = self._generate_general_suggestions(
                run_report, analysis_type
            )
        
        # Calculate confidence based on problem severity
        suggestion['confidence'] = self._calculate_confidence(run_report, focus_areas)
        
        # Generate reasoning for suggestions
        suggestion['reasoning'] = self._generate_reasoning(
            suggestion['suggestions'], run_report, focus_areas
        )
        
        # Estimate expected improvements
        suggestion['expected_improvements'] = self._estimate_improvements(
            suggestion['suggestions'], run_report
        )
        
        # Assess risk level
        suggestion['risk_assessment'] = self._assess_risk(suggestion['suggestions'])
        
        # Log suggestion
        self._log_suggestion(suggestion)
        
        return suggestion
    
    def _mock_image_analysis(self, base64_image: str) -> Dict[str, Any]:
        """
        Simulate visual analysis of the image.
        
        Args:
            base64_image: Base64-encoded image data
            
        Returns:
            Mock insights about image characteristics
        """
        # Simulate different image characteristics based on image size
        image_size = len(base64_image)
        
        # Mock insights based on "visual analysis"
        insights = {
            'contrast_quality': 'medium' if image_size > 50000 else 'low',
            'noise_level': 'low' if image_size > 100000 else 'medium',
            'background_uniformity': random.choice(['good', 'fair', 'poor']) if not self.deterministic else 'fair',
            'feature_clarity': 'sharp' if image_size > 75000 else 'soft',
            'saturation_detected': image_size < 30000,  # Small images might be saturated
            'edge_definition': 'strong' if image_size > 60000 else 'weak'
        }
        
        return insights
    
    def _generate_focused_suggestions(
        self, 
        focus_areas: List[str], 
        run_report: Dict[str, Any], 
        analysis_type: str
    ) -> Dict[str, Any]:
        """Generate parameter suggestions focused on specific problem areas."""
        suggestions = {}
        
        metrics = run_report.get('metrics', {})
        observation = run_report.get('observation', {})
        
        for focus_area in focus_areas:
            if 'prominence_frac' in focus_area:
                # Suggest prominence adjustments based on current detection
                current_lanes = metrics.get('lanes_raw', 0)
                if current_lanes == 0:
                    suggestions[focus_area] = 0.03  # More sensitive
                elif current_lanes < 5:
                    suggestions[focus_area] = 0.04  # Slightly more sensitive
                else:
                    suggestions[focus_area] = 0.08  # Less sensitive to reduce noise
                    
            elif 'min_peak_distance' in focus_area:
                # Suggest distance adjustments
                if 'px' in focus_area:
                    suggestions[focus_area] = 10  # Smaller distance for more bands
                else:
                    suggestions[focus_area] = 0.025  # Smaller fraction for more lanes
                    
            elif 'baseline' in focus_area:
                # Suggest baseline improvements
                baseline_post_med = observation.get('baseline_post_med', 0)
                if baseline_post_med > 0.1:
                    if 'window_frac' in focus_area:
                        suggestions['bands.baseline.window_frac'] = 0.025  # Larger window
                    elif 'quantile' in focus_area:
                        suggestions['bands.baseline.quantile'] = 0.05  # Lower quantile
                        
            elif 'threshold' in focus_area and analysis_type == 'colony':
                # Colony-specific suggestions
                if 'method' in focus_area:
                    suggestions[focus_area] = 'Phansalkar'  # Better for colonies
                elif 'radius' in focus_area:
                    suggestions[focus_area] = 18  # Slightly larger radius
                    
            elif 'colorspace' in focus_area:
                suggestions[focus_area] = 'Lab'  # Better for blue/white discrimination
        
        return suggestions
    
    def _generate_general_suggestions(self, run_report: Dict[str, Any], analysis_type: str) -> Dict[str, Any]:
        """Generate general parameter suggestions when no specific focus areas."""
        suggestions = {}
        
        metrics = run_report.get('metrics', {})
        observation = run_report.get('observation', {})
        
        # General improvements based on analysis type and results
        if analysis_type == 'sds':
            bands_raw = metrics.get('bands_raw', 0)
            if bands_raw < 10:
                suggestions['bands.prominence_frac'] = 0.015  # More sensitive
                suggestions['bands.min_peak_distance_px'] = 8   # Closer bands allowed
                
        elif analysis_type == 'etbr':
            lanes_raw = metrics.get('lanes_raw', 0)
            if lanes_raw < 15:
                suggestions['lanes.prominence_frac'] = 0.035   # More sensitive
                
        elif analysis_type == 'colony':
            colony_count = metrics.get('colony_count', 0)
            if colony_count < 20:
                suggestions['threshold.radius'] = 12  # Smaller threshold radius
                suggestions['min_colony_size'] = 4    # Allow smaller colonies
        
        return suggestions
    
    def _calculate_confidence(self, run_report: Dict[str, Any], focus_areas: List[str]) -> float:
        """Calculate confidence in suggestions based on problem clarity."""
        base_confidence = 0.7  # Base mock confidence
        
        # Higher confidence for more focus areas (clearer problems)
        focus_bonus = min(len(focus_areas) * 0.1, 0.2)
        
        # Lower confidence for completely failed analyses
        metrics = run_report.get('metrics', {})
        if (metrics.get('lanes_raw', 0) == 0 and 
            metrics.get('bands_raw', 0) == 0 and 
            metrics.get('colony_count', 0) == 0):
            base_confidence = 0.5  # Lower confidence for total failures
        
        return min(base_confidence + focus_bonus, 0.95)
    
    def _generate_reasoning(
        self, 
        suggestions: Dict[str, Any], 
        run_report: Dict[str, Any], 
        focus_areas: List[str]
    ) -> List[str]:
        """Generate human-readable reasoning for suggestions."""
        reasoning = []
        
        metrics = run_report.get('metrics', {})
        observation = run_report.get('observation', {})
        
        # Reasoning based on detected issues
        if metrics.get('lanes_raw', 0) == 0:
            reasoning.append("No lanes detected - reducing prominence threshold to increase sensitivity")
            
        if metrics.get('bands_raw', 0) == 0:
            reasoning.append("No bands detected - adjusting band detection parameters for better sensitivity")
            
        baseline_post_med = observation.get('baseline_post_med', 0)
        if baseline_post_med > 0.1:
            reasoning.append(f"Baseline removal ineffective (median={baseline_post_med:.3f}) - optimizing baseline parameters")
            
        coverage_total = observation.get('coverage_total', 1.0)
        if coverage_total < 0.3:
            reasoning.append(f"Low feature coverage ({coverage_total:.2f}) - increasing detection sensitivity")
            
        # Visual analysis reasoning
        reasoning.append("Visual analysis suggests optimizing parameters for current image characteristics")
        
        if not reasoning:
            reasoning.append("General parameter optimization based on current results")
        
        return reasoning
    
    def _estimate_improvements(self, suggestions: Dict[str, Any], run_report: Dict[str, Any]) -> Dict[str, Any]:
        """Estimate expected improvements from suggestions."""
        improvements = {}
        
        metrics = run_report.get('metrics', {})
        
        # Estimate improvements based on current metrics and suggestions
        current_lanes = metrics.get('lanes_raw', 0)
        current_bands = metrics.get('bands_raw', 0)
        
        if 'lanes.prominence_frac' in suggestions or 'lanes.min_peak_distance_frac' in suggestions:
            if current_lanes == 0:
                improvements['lanes_expected'] = 8  # Expect to find some lanes
            else:
                improvements['lanes_expected'] = min(current_lanes + 3, 20)  # Modest improvement
        
        if 'bands.prominence_frac' in suggestions or 'bands.min_peak_distance_px' in suggestions:
            if current_bands == 0:
                improvements['bands_expected'] = 12  # Expect to find some bands
            else:
                improvements['bands_expected'] = min(current_bands + 5, 50)  # Modest improvement
        
        # Quality improvements
        improvements['coverage_increase'] = 0.15  # Expected coverage increase
        improvements['stability_increase'] = 0.1   # Expected stability improvement
        
        return improvements
    
    def _assess_risk(self, suggestions: Dict[str, Any]) -> str:
        """Assess risk level of suggested parameter changes."""
        # Mock risk assessment based on magnitude of changes
        num_suggestions = len(suggestions)
        
        if num_suggestions == 0:
            return 'none'
        elif num_suggestions <= 2:
            return 'low'
        elif num_suggestions <= 4:
            return 'medium'
        else:
            return 'high'  # Many changes = higher risk
    
    def _log_suggestion(self, suggestion: Dict[str, Any]) -> None:
        """Log suggestion for debugging and testing."""
        self.suggestion_history.append(suggestion)
        
        logger.info(f"🤖 Mock advisor suggestion (confidence={suggestion['confidence']:.2f})")
        logger.info(f"   Suggestions: {suggestion['suggestions']}")
        logger.info(f"   Reasoning: {suggestion['reasoning'][0] if suggestion['reasoning'] else 'General optimization'}")

def test_mock_advisor():
    """Test the mock advisor with various scenarios."""
    config = {'deterministic': True}
    advisor = MockVisionAdvisor(config)
    
    # Test with problematic SDS run
    problematic_run = {
        'metrics': {'lanes_raw': 0, 'bands_raw': 0},
        'observation': {
            'coverage_total': 0.1,
            'baseline_post_med': 0.3
        }
    }
    
    focus_areas = ['lanes.prominence_frac', 'baseline.window_frac']
    
    suggestion = advisor.analyze_and_suggest(
        base64_image='mock_image_data_123',
        run_report=problematic_run,
        focus_areas=focus_areas,
        analysis_type='sds'
    )
    
    print(f"🧪 Mock advisor test:")
    print(f"   Suggestions: {suggestion['suggestions']}")
    print(f"   Confidence: {suggestion['confidence']:.2f}")
    print(f"   Risk: {suggestion['risk_assessment']}")
    print(f"   Expected improvements: {suggestion['expected_improvements']}")

if __name__ == "__main__":
    test_mock_advisor()