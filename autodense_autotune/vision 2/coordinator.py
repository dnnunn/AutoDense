"""
Vision-assist coordinator for Phase IV implementation.

Orchestrates the complete vision-assist workflow including trigger detection,
image processing, parameter suggestion, and validation.
"""

import logging
from typing import Dict, Any, Optional, Tuple
import os
import tempfile

from .cropper import VisionCropper
from .allowlist import AllowlistValidator, create_default_allowlist
from .gating import VisionGate, create_default_gate_config
from .mock_advisor import MockVisionAdvisor

logger = logging.getLogger(__name__)

class VisionAssistCoordinator:
    """Coordinates the complete vision-assist optimization workflow."""
    
    def __init__(self, config: Dict[str, Any]):
        """
        Initialize vision-assist coordinator with configuration.
        
        Args:
            config: Complete vision-assist configuration
        """
        self.config = config
        self.vision_config = config.get('vision', {})
        
        # Initialize components
        self.cropper = VisionCropper(self.vision_config)
        self.allowlist_validator = AllowlistValidator(
            config.get('allowlist', create_default_allowlist())
        )
        self.gate = VisionGate(
            config.get('gate', create_default_gate_config())
        )
        
        # Initialize advisor (mock for now)
        self.advisor = MockVisionAdvisor(
            config.get('mock_advisor', {'deterministic': True})
        )
        
        self.session_history = []
    
    def process_analysis_result(
        self, 
        input_image_path: str,
        run_report: Dict[str, Any],
        analysis_type: str,
        force_trigger: bool = False
    ) -> Dict[str, Any]:
        """
        Process analysis result and potentially trigger vision-assist optimization.
        
        Args:
            input_image_path: Path to original input image
            run_report: Analysis results from Java pipeline
            analysis_type: Type of analysis performed
            force_trigger: Force vision-assist even if gate doesn't trigger
            
        Returns:
            Complete vision-assist session result
        """
        session_result = {
            'input_image_path': input_image_path,
            'analysis_type': analysis_type,
            'original_run_report': run_report,
            'vision_assist_triggered': False,
            'gate_decision': None,
            'optimization_result': None,
            'session_id': len(self.session_history),
            'success': False
        }
        
        try:
            # Step 1: Check if vision-assist should be triggered
            gate_decision = self.gate.should_trigger_vision_assist(run_report, analysis_type)
            session_result['gate_decision'] = gate_decision
            
            should_proceed = gate_decision['should_trigger'] or force_trigger
            
            if not should_proceed:
                session_result['success'] = True
                session_result['message'] = "No vision-assist optimization needed"
                logger.info(f"✅ Vision-assist not triggered for {analysis_type}")
                return session_result
            
            # Step 2: Process image for vision analysis
            logger.info(f"🎯 Vision-assist triggered for {analysis_type} (confidence={gate_decision['confidence']:.2f})")
            session_result['vision_assist_triggered'] = True
            
            # Find preprocessed image (prefer stage1_norm if available)
            processed_image_path = self._find_processed_image(input_image_path, run_report)
            
            image_data = self.cropper.process_for_vision(processed_image_path)
            if not image_data.get('ready_for_analysis'):
                session_result['error'] = f"Image processing failed: {image_data.get('error')}"
                return session_result
            
            # Step 3: Get parameter suggestions from advisor
            suggestions_result = self.advisor.analyze_and_suggest(
                base64_image=image_data['base64_image'],
                run_report=run_report,
                focus_areas=gate_decision.get('focus_areas', []),
                analysis_type=self._normalize_analysis_type(analysis_type)
            )
            
            # Step 4: Validate suggestions against allowlist
            validation_result = self.allowlist_validator.validate_proposal(
                analysis_type=self._normalize_analysis_type(analysis_type),
                proposal=suggestions_result['suggestions']
            )
            
            # Step 5: Combine results
            optimization_result = {
                'image_processing': image_data,
                'suggestions': suggestions_result,
                'validation': validation_result,
                'approved_changes': validation_result.get('approved_changes', {}),
                'safety_assessment': self._assess_overall_safety(suggestions_result, validation_result)
            }
            
            session_result['optimization_result'] = optimization_result
            session_result['success'] = validation_result.get('valid', False)
            
            if session_result['success']:
                logger.info(f"✅ Vision-assist optimization completed successfully")
                logger.info(f"   Approved changes: {len(validation_result['approved_changes'])}")
                logger.info(f"   Safety level: {validation_result['safety_level']}")
            else:
                logger.warning(f"⚠️ Vision-assist optimization completed with issues")
                logger.warning(f"   Violations: {validation_result.get('violations', [])}")
            
        except Exception as e:
            logger.error(f"❌ Vision-assist processing failed: {e}")
            session_result['error'] = str(e)
            session_result['success'] = False
        
        # Store session history
        self.session_history.append(session_result)
        
        return session_result
    
    def get_optimization_summary(self, session_result: Dict[str, Any]) -> Dict[str, Any]:
        """
        Get a concise summary of optimization recommendations.
        
        Args:
            session_result: Result from process_analysis_result
            
        Returns:
            Summary suitable for user display or automated application
        """
        if not session_result.get('vision_assist_triggered'):
            return {'message': 'No optimization needed', 'changes': {}}
        
        if not session_result.get('success'):
            return {
                'message': 'Optimization failed',
                'error': session_result.get('error', 'Unknown error'),
                'changes': {}
            }
        
        opt_result = session_result.get('optimization_result', {})
        suggestions = opt_result.get('suggestions', {})
        validation = opt_result.get('validation', {})
        
        return {
            'message': 'Optimization recommendations available',
            'confidence': suggestions.get('confidence', 0.0),
            'risk_level': suggestions.get('risk_assessment', 'unknown'),
            'safety_level': validation.get('safety_level', 'unknown'),
            'changes': validation.get('approved_changes', {}),
            'reasoning': suggestions.get('reasoning', []),
            'expected_improvements': suggestions.get('expected_improvements', {}),
            'warnings': validation.get('warnings', []),
            'bounds_applied': validation.get('bounds_applied', {})
        }
    
    def _find_processed_image(self, original_path: str, run_report: Dict[str, Any]) -> str:
        """
        Find the best preprocessed image for vision analysis.
        
        Args:
            original_path: Original input image path
            run_report: Analysis run report
            
        Returns:
            Path to best available processed image
        """
        # Try to find preprocessed images in output directory
        # This assumes the Java pipeline saves stage images
        
        base_dir = os.path.dirname(original_path)
        output_dir = run_report.get('output_dir')
        
        if output_dir and os.path.exists(output_dir):
            # Prefer stage1_norm (normalized image)
            stage1_path = os.path.join(output_dir, 'stage1_norm.png')
            if os.path.exists(stage1_path):
                return stage1_path
                
            # Fallback to stage0_input
            stage0_path = os.path.join(output_dir, 'stage0_input.png')
            if os.path.exists(stage0_path):
                return stage0_path
        
        # Fallback to original image
        return original_path
    
    def _normalize_analysis_type(self, analysis_type: str) -> str:
        """
        Normalize analysis type names for consistent processing.
        
        Args:
            analysis_type: Raw analysis type from Java pipeline
            
        Returns:
            Normalized analysis type for vision system
        """
        type_mapping = {
            'sds_page': 'sds',
            'etbr_agarose': 'etbr', 
            'colonies_blue_white': 'colony'
        }
        
        return type_mapping.get(analysis_type, analysis_type)
    
    def _assess_overall_safety(
        self, 
        suggestions_result: Dict[str, Any], 
        validation_result: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Assess overall safety of the optimization recommendations.
        
        Args:
            suggestions_result: Results from advisor
            validation_result: Results from allowlist validation
            
        Returns:
            Overall safety assessment
        """
        return {
            'advisor_risk': suggestions_result.get('risk_assessment', 'unknown'),
            'validation_safety': validation_result.get('safety_level', 'unknown'),
            'overall_recommendation': self._determine_recommendation(
                suggestions_result.get('risk_assessment', 'high'),
                validation_result.get('safety_level', 'unsafe')
            ),
            'requires_review': validation_result.get('safety_level') != 'safe'
        }
    
    def _determine_recommendation(self, advisor_risk: str, validation_safety: str) -> str:
        """Determine overall recommendation based on risk and safety levels."""
        if validation_safety == 'unsafe':
            return 'reject'
        elif validation_safety == 'safe' and advisor_risk in ['low', 'medium']:
            return 'approve'
        else:
            return 'review_required'

def create_default_vision_config() -> Dict[str, Any]:
    """Create default configuration for vision-assist system."""
    return {
        'vision': {
            'mode': 'assist',  # off | assist | qc
            'max_side_px': 1024,
            'include_stage': 'stage1_norm',
            'crop': 'auto',
            'scrub': {
                'strip_exif': True,
                'blur_text': True,
                'grayscale_gels': True
            }
        },
        'allowlist': create_default_allowlist(),
        'gate': create_default_gate_config(),
        'mock_advisor': {
            'deterministic': True
        }
    }

def test_vision_coordinator():
    """Test the vision coordinator with sample data."""
    config = create_default_vision_config()
    coordinator = VisionAssistCoordinator(config)
    
    # Test with problematic run that should trigger vision-assist
    problematic_run = {
        'metrics': {'lanes_raw': 0, 'bands_raw': 0},
        'observation': {
            'coverage_total': 0.1,
            'baseline_post_med': 0.25
        }
    }
    
    # Use a sample image (will fallback to original if processed not found)
    sample_image = 'samples/sds_gel.jpg'
    
    result = coordinator.process_analysis_result(
        input_image_path=sample_image,
        run_report=problematic_run,
        analysis_type='sds_page'
    )
    
    print(f"🧪 Vision coordinator test:")
    print(f"   Triggered: {result['vision_assist_triggered']}")
    print(f"   Success: {result['success']}")
    
    if result['success']:
        summary = coordinator.get_optimization_summary(result)
        print(f"   Changes: {len(summary['changes'])}")
        print(f"   Confidence: {summary['confidence']:.2f}")

if __name__ == "__main__":
    test_vision_coordinator()