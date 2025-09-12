# autodense/preprocess/openai_policy.py
"""
OpenAI ChatGPT-4.1 integration for AI-guided preprocessing decisions.
Extends the policy.py module with intelligent LLM-based decision making.
"""

from __future__ import annotations
import base64
import io
import os
from typing import Dict, Any, Optional, Literal
import numpy as np
from PIL import Image
from dataclasses import dataclass

try:
    from openai import OpenAI
    HAS_OPENAI = True
except ImportError:
    HAS_OPENAI = False
    OpenAI = None

from .policy import PolicyOutcome, PolicyThresholds, evaluate, _to_gray_float
from ..security.key_manager import get_openai_api_key, mask_key_for_logging
from ..security.scientific_validator import ScientificValidator

@dataclass 
class OpenAIConfig:
    model: str = "gpt-4.1"
    max_tokens: int = 500
    temperature: float = 0.1  # Low temperature for consistent technical decisions
    timeout: float = 30.0

class OpenAIPreprocessingClient:
    """ChatGPT-4.1 client for intelligent preprocessing decisions"""
    
    def __init__(self, config: OpenAIConfig | None = None):
        if not HAS_OPENAI:
            raise ImportError("openai package not installed. Run: pip install openai")
        
        self.config = config or OpenAIConfig()
        
        # SECURITY: Load API key using secure key manager
        api_key = get_openai_api_key()
        if not api_key:
            raise ValueError(
                "OpenAI API key not found. "
                "Set OPENAI_API_KEY environment variable. "
                "NEVER store API keys in config files or code."
            )
        
        # SECURITY: Log masked key for debugging (never log actual key)
        import logging
        logger = logging.getLogger(__name__)
        logger.info(f"✅ OpenAI client initialized with key: {mask_key_for_logging(api_key)}")
        
        self.client = OpenAI(api_key=api_key, timeout=self.config.timeout)
        
        # Initialize scientific validator
        self.validator = ScientificValidator()
    
    # SECURITY: _load_api_key method removed - using secure key manager instead
    
    def _image_to_base64(self, image: np.ndarray) -> str:
        """Convert numpy image to base64 string for API"""
        # Convert to PIL Image
        if image.dtype == np.float32 or image.dtype == np.float64:
            # Convert float [0,1] to uint8 [0,255]
            img_uint8 = (np.clip(image, 0, 1) * 255).astype(np.uint8)
        else:
            img_uint8 = image
        
        # Handle grayscale vs RGB
        if img_uint8.ndim == 2:
            pil_img = Image.fromarray(img_uint8, mode='L')
        else:
            pil_img = Image.fromarray(img_uint8, mode='RGB')
        
        # Convert to base64
        buffer = io.BytesIO()
        pil_img.save(buffer, format='PNG')
        img_base64 = base64.b64encode(buffer.getvalue()).decode('utf-8')
        
        return f"data:image/png;base64,{img_base64}"
    
    def analyze_preprocessing_needs(self, image: np.ndarray | Image.Image, 
                                  metrics: Dict[str, float]) -> Dict[str, Any]:
        """Use ChatGPT-4.1 to analyze image and recommend preprocessing"""
        
        # Convert to grayscale float for analysis
        g = _to_gray_float(image)
        img_base64 = self._image_to_base64(g)
        
        # Construct prompt with image metrics
        prompt = f"""You are an expert in scientific gel electrophoresis image analysis. Analyze this SDS-PAGE gel image and recommend preprocessing steps.

Current image metrics:
- Signal-to-Noise Ratio: {metrics['snr']:.3f}
- Lane Separability: {metrics['sep']:.3f}
- Skew Angle: {metrics['skew']:.2f}°
- Stripe Ratio: {metrics['stripe_ratio']:.3f}
- Illumination Amplitude: {metrics['illum_amp']:.3f}

Available preprocessing options:
1. "deskew" - Rotate image to correct lane alignment (use if |skew| > 0.5°)
2. "destripe" - Remove column-wise noise artifacts (use if stripe_ratio > 1.25)
3. "background" - Correct uneven illumination (use if illum_amp > 0.10)
4. "clahe" - Enhance contrast adaptively (use if SNR < 2.0)
5. "none" - No preprocessing needed

Consider:
- Lane clarity and alignment
- Background uniformity
- Band visibility and contrast
- Noise patterns

IMPORTANT: Choose exactly ONE preprocessing method, not multiple.

Respond with ONLY a JSON object in this exact format:
{{
    "recommendation": "deskew|destripe|background|clahe|none",
    "confidence": 0.0-1.0,
    "reasoning": "brief technical explanation",
    "expected_improvement": "metric that should improve"
}}"""

        try:
            response = self.client.chat.completions.create(
                model=self.config.model,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {
                                "type": "image_url", 
                                "image_url": {"url": img_base64}
                            }
                        ]
                    }
                ],
                max_tokens=self.config.max_tokens,
                temperature=self.config.temperature
            )
            
            # Parse response
            content = response.choices[0].message.content
            
            # Clean up JSON if needed (remove markdown formatting)
            if content.startswith("```json"):
                content = content.split("```json")[1].split("```")[0].strip()
            elif content.startswith("```"):
                content = content.split("```")[1].split("```")[0].strip()
            
            import json
            result = json.loads(content)
            
            # Validate response format
            required_keys = {"recommendation", "confidence", "reasoning", "expected_improvement"}
            if not all(key in result for key in required_keys):
                raise ValueError(f"Invalid response format, missing keys: {required_keys - set(result.keys())}")
            
            # Validate and parse recommendation value
            valid_recommendations = {"deskew", "destripe", "background", "clahe", "none"}
            
            # Handle cases where ChatGPT returns multiple recommendations
            recommendation = result["recommendation"].lower().strip()
            
            # If multiple recommendations, take the first valid one
            if "," in recommendation:
                recommendations = [r.strip() for r in recommendation.split(",")]
                for rec in recommendations:
                    if rec in valid_recommendations:
                        result["recommendation"] = rec
                        result["reasoning"] = f"{result.get('reasoning', '')} (Selected: {rec} from multiple options)"
                        break
                else:
                    raise ValueError(f"No valid recommendation found in: {recommendation}")
            elif recommendation not in valid_recommendations:
                # Try to extract a valid recommendation from the string
                for valid_rec in valid_recommendations:
                    if valid_rec in recommendation:
                        result["recommendation"] = valid_rec
                        result["reasoning"] = f"{result.get('reasoning', '')} (Extracted: {valid_rec})"
                        break
                else:
                    raise ValueError(f"Invalid recommendation: {recommendation}")
            
            # SECURITY: Apply scientific validation to AI decision
            validation_result = self.validator.validate_preprocessing_decision(
                ai_recommendation=result["recommendation"],
                ai_confidence=result["confidence"], 
                ai_reasoning=result["reasoning"],
                image_metrics=metrics,
                image_shape=g.shape
            )
            
            # Apply scientific adjustments
            if not validation_result.is_valid:
                # CRITICAL: AI decision failed scientific validation
                import logging
                logger = logging.getLogger(__name__)
                logger.error(f"🚨 AI decision failed scientific validation: {validation_result.errors}")
                logger.info("🔄 Falling back to algorithmic decision")
                return self._fallback_analysis(metrics)
            
            # Adjust confidence based on scientific validation
            original_confidence = result["confidence"]
            result["confidence"] = original_confidence * validation_result.confidence_adjustment
            
            # Enhance reasoning with scientific validation
            result["reasoning"] = validation_result.scientific_reasoning
            result["original_ai_confidence"] = original_confidence
            result["scientific_warnings"] = validation_result.warnings
            
            # Log validation results for transparency
            import logging
            logger = logging.getLogger(__name__)
            if validation_result.warnings:
                logger.warning(f"⚠️ Scientific validation warnings: {validation_result.warnings[:2]}")
            logger.info(f"✅ AI decision scientifically validated (confidence: {original_confidence:.2f} → {result['confidence']:.2f})")
            
            return result
            
        except Exception as e:
            # SECURITY: Never log actual error messages that might contain sensitive data
            import logging
            logger = logging.getLogger(__name__)
            logger.error(f"OpenAI API error: {type(e).__name__}")
            logger.info("🔄 Falling back to algorithmic decision")
            # Fallback to algorithmic decision
            return self._fallback_analysis(metrics)
    
    def _fallback_analysis(self, metrics: Dict[str, float]) -> Dict[str, Any]:
        """Fallback to algorithmic decision if OpenAI fails"""
        thresholds = PolicyThresholds()
        
        if abs(metrics["skew"]) > thresholds.skew_min_deg:
            return {
                "recommendation": "deskew", 
                "confidence": 0.8,
                "reasoning": "Fallback: Large skew angle detected",
                "expected_improvement": "lane alignment"
            }
        elif metrics["stripe_ratio"] > thresholds.stripe_ratio_min:
            return {
                "recommendation": "destripe",
                "confidence": 0.8, 
                "reasoning": "Fallback: Column noise detected",
                "expected_improvement": "noise reduction"
            }
        elif metrics["illum_amp"] > thresholds.illum_amp_min:
            return {
                "recommendation": "background",
                "confidence": 0.8,
                "reasoning": "Fallback: Uneven illumination detected", 
                "expected_improvement": "background uniformity"
            }
        elif metrics["snr"] < 2.0:
            return {
                "recommendation": "clahe",
                "confidence": 0.8,
                "reasoning": "Fallback: Low signal-to-noise ratio",
                "expected_improvement": "contrast enhancement"
            }
        else:
            return {
                "recommendation": "none",
                "confidence": 0.9,
                "reasoning": "Fallback: No preprocessing needed",
                "expected_improvement": "none"
            }

def openai_guided_preprocess(image: np.ndarray | Image.Image,
                            thresholds: PolicyThresholds | None = None,
                            openai_config: OpenAIConfig | None = None) -> PolicyOutcome:
    """
    AI-guided preprocessing using ChatGPT-4.1 for intelligent decisions.
    Falls back to algorithmic approach if OpenAI is unavailable.
    """
    if thresholds is None:
        thresholds = PolicyThresholds()
    
    g0 = _to_gray_float(image)
    m0 = evaluate(g0)
    
    try:
        # Use ChatGPT-4.1 for decision making
        client = OpenAIPreprocessingClient(openai_config)
        analysis = client.analyze_preprocessing_needs(g0, m0)
        
        recommendation = analysis["recommendation"]
        
        if recommendation == "none":
            return PolicyOutcome("none", {}, m0, m0, g0)
        
        # Execute the recommended preprocessing
        from .policy import deskew_step, destripe_colmedian, background_step, clahe_step, polarity
        
        if recommendation == "deskew":
            g1, deg = deskew_step(g0)
            params = {"deg": deg, "ai_confidence": analysis["confidence"], 
                     "ai_reasoning": analysis["reasoning"]}
        elif recommendation == "destripe":
            g1 = destripe_colmedian(g0)
            params = {"ai_confidence": analysis["confidence"],
                     "ai_reasoning": analysis["reasoning"]}
        elif recommendation == "background":
            pol = polarity(g0)
            g1 = background_step(g0, pol)
            params = {"polarity": pol, "ai_confidence": analysis["confidence"],
                     "ai_reasoning": analysis["reasoning"]}
        elif recommendation == "clahe":
            g1 = clahe_step(g0)
            params = {"ai_confidence": analysis["confidence"],
                     "ai_reasoning": analysis["reasoning"]}
        else:
            g1 = g0.copy()
            params = {"ai_confidence": analysis["confidence"],
                     "ai_reasoning": analysis["reasoning"]}
        
        m1 = evaluate(g1)
        return PolicyOutcome(recommendation, params, m0, m1, g1)
        
    except Exception as e:
        print(f"OpenAI preprocessing failed, falling back to algorithmic: {e}")
        # Fall back to original algorithmic approach
        from .policy import guarded_preprocess
        return guarded_preprocess(image, thresholds)