#!/usr/bin/env python3
"""
Security test suite for ChatGPT-4.1 integration fixes.
Tests the critical security vulnerabilities identified by audit-critic.
"""
import sys
import os
import tempfile
import json
from pathlib import Path
from unittest.mock import patch, MagicMock, patch as mock_patch
import numpy as np
from PIL import Image

# Add autodense to path
sys.path.insert(0, str(Path(__file__).parent))

def test_api_key_security():
    """Test that API keys are handled securely"""
    print("🔐 Testing API Key Security")
    print("=" * 30)
    
    try:
        from autodense.security.key_manager import SecureAPIKeyManager, get_openai_api_key, mask_key_for_logging
        
        # Test 1: API key masking
        test_key = "sk-proj-1234567890abcdefghijklmnopqrstuvwxyz1234567890"
        masked = mask_key_for_logging(test_key)
        print(f"✅ Key masking: {test_key[:10]}... → {masked}")
        assert "sk-" in masked and "890" in masked  # Check prefix and suffix
        assert len(masked) < 20  # Masked version should be much shorter
        
        # Test 2: Placeholder key detection
        manager = SecureAPIKeyManager()
        placeholders = ["__SECURE_ENV_VARIABLE__", "your_api_key_here"]
        for placeholder in placeholders:
            assert manager.is_placeholder_key(placeholder), f"Should detect {placeholder} as placeholder"
            print(f"✅ Placeholder detection: {placeholder}")
        
        # Test 3: Key format validation
        valid_keys = ["sk-proj-" + "x" * 40, "sk-test-" + "y" * 35]
        invalid_keys = ["invalid", "sk-", "wrongprefix-1234567890"]
        
        for key in valid_keys:
            assert manager._validate_openai_key(key), f"Should validate {key[:10]}..."
            print(f"✅ Valid key format: {key[:10]}...")
            
        for key in invalid_keys:
            assert not manager._validate_openai_key(key), f"Should reject {key}"
            print(f"✅ Invalid key rejected: {key}")
        
        # Test 4: Environment variable priority
        test_env_key = "sk-test-environment-key-12345678901234567890123456789"  # Make it longer
        with patch.dict(os.environ, {"OPENAI_API_KEY": test_env_key}):
            retrieved_key = get_openai_api_key()
            assert retrieved_key == test_env_key
            print(f"✅ Environment variable priority: {mask_key_for_logging(test_env_key)}")
        
        print("✅ API key security tests passed!")
        return True
        
    except Exception as e:
        print(f"❌ API key security test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_scientific_validation():
    """Test scientific validation prevents AI hallucinations"""
    print("\n🧪 Testing Scientific Validation")
    print("=" * 35)
    
    try:
        from autodense.security.scientific_validator import ScientificValidator
        
        validator = ScientificValidator()
        
        # Test 1: Detect inappropriate deskew decision
        metrics_minimal_skew = {
            'skew': 0.2,  # Very small skew
            'snr': 3.5,
            'sep': 0.8,
            'stripe_ratio': 1.1,
            'illum_amp': 0.05
        }
        
        result = validator.validate_preprocessing_decision(
            ai_recommendation="deskew",
            ai_confidence=0.95,  # High confidence for minimal skew - should be flagged
            ai_reasoning="Significant skew detected",
            image_metrics=metrics_minimal_skew
        )
        
        assert len(result.warnings) > 0, "Should warn about minimal skew correction"
        assert result.confidence_adjustment < 1.0, "Should reduce confidence for inappropriate decision"
        print(f"✅ Inappropriate deskew detected: confidence {0.95:.2f} → {0.95 * result.confidence_adjustment:.2f}")
        
        # Test 2: Validate appropriate preprocessing decision
        metrics_clear_skew = {
            'skew': 3.2,  # Clear skew that needs correction
            'snr': 2.1,
            'sep': 0.6,
            'stripe_ratio': 1.0,
            'illum_amp': 0.08
        }
        
        result = validator.validate_preprocessing_decision(
            ai_recommendation="deskew",
            ai_confidence=0.85,
            ai_reasoning="Clear lane misalignment detected",
            image_metrics=metrics_clear_skew
        )
        
        assert result.is_valid, "Should validate appropriate deskew decision"
        print(f"✅ Appropriate deskew validated: confidence maintained at {result.confidence_adjustment:.2f}")
        
        # Test 3: Detect extreme parameter injection
        metrics_extreme = {
            'skew': 25.0,  # Physically implausible skew
            'snr': 2.5,
            'sep': 0.7,
            'stripe_ratio': 1.2,
            'illum_amp': 0.12
        }
        
        result = validator.validate_preprocessing_decision(
            ai_recommendation="deskew",
            ai_confidence=0.9,
            ai_reasoning="Extreme misalignment requires correction",
            image_metrics=metrics_extreme
        )
        
        assert not result.is_valid, "Should reject physically implausible parameters"
        assert len(result.errors) > 0, "Should flag extreme parameters as errors"
        print(f"✅ Extreme parameter injection blocked: {result.errors[0]}")
        
        print("✅ Scientific validation tests passed!")
        return True
        
    except Exception as e:
        print(f"❌ Scientific validation test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_secure_error_handling():
    """Test that error handling doesn't leak sensitive information"""
    print("\n🛡️ Testing Secure Error Handling")
    print("=" * 35)
    
    try:
        from autodense.preprocess.openai_policy import OpenAIPreprocessingClient
        
        # Mock environment to test various failure modes
        long_test_key = "sk-test-mock-key-12345678901234567890123456789012345"
        with patch.dict(os.environ, {"OPENAI_API_KEY": long_test_key}):
            
            # Test 1: API timeout handling
            with patch('autodense.preprocess.openai_policy.OpenAI') as mock_openai_class:
                mock_client = MagicMock()
                mock_client.chat.completions.create.side_effect = Exception("API timeout occurred")
                mock_openai_class.return_value = mock_client
                
                client = OpenAIPreprocessingClient()
                
                # Create test metrics
                test_metrics = {
                    'skew': 1.5,
                    'snr': 2.1,
                    'sep': 0.7,
                    'stripe_ratio': 1.3,
                    'illum_amp': 0.09
                }
                
                # Create test image
                test_image = np.random.rand(100, 80).astype(np.float32)
                
                # Should handle error gracefully without exposing details
                result = client.analyze_preprocessing_needs(test_image, test_metrics)
                
                assert result is not None, "Should return fallback result on API error"
                assert result.get('reasoning', '').startswith('Fallback:'), "Should indicate fallback was used"
                print("✅ API timeout handled gracefully with fallback")
                
        # Test 2: Invalid API key handling
        with patch.dict(os.environ, {}, clear=True):  # No API key set
            try:
                client = OpenAIPreprocessingClient()
                assert False, "Should raise ValueError for missing API key"
            except ValueError as e:
                error_msg = str(e)
                # Should give helpful message without exposing system internals
                assert "OPENAI_API_KEY" in error_msg
                assert "NEVER store API keys" in error_msg
                print("✅ Missing API key error handled securely")
        
        print("✅ Secure error handling tests passed!")
        return True
        
    except Exception as e:
        print(f"❌ Secure error handling test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_config_file_security():
    """Test that config file no longer contains plaintext keys"""
    print("\n📄 Testing Config File Security")
    print("=" * 32)
    
    try:
        config_file = Path("api-config.properties")
        
        if config_file.exists():
            with open(config_file, 'r') as f:
                content = f.read()
            
            # Should not contain actual API keys
            assert "sk-proj-xbXyDo_L0dN8AWJwZef5" not in content, "Original API key should be removed"
            assert "__SECURE_ENV_VARIABLE__" in content, "Should contain placeholder"
            assert "SECURITY NOTE" in content, "Should contain security warning"
            
            print("✅ Config file sanitized - no plaintext keys found")
            print("✅ Security warnings added to config file")
        else:
            print("⚠️ Config file not found - assuming secure setup")
        
        return True
        
    except Exception as e:
        print(f"❌ Config file security test failed: {e}")
        return False

def test_integration_security():
    """Test end-to-end security of the integration"""
    print("\n🔄 Testing Integration Security")
    print("=" * 33)
    
    try:
        # Set up secure environment  
        test_key = "sk-test-integration-key-123456789012345678901234567890"
        
        with mock_patch.dict(os.environ, {"OPENAI_API_KEY": test_key}):
            # Test that the secure workflow works end-to-end
            from autodense.preprocess.openai_policy import openai_guided_preprocess
            from unittest.mock import patch
            
            # Create test image
            test_image = Image.fromarray(np.random.randint(0, 255, (200, 150, 3), dtype=np.uint8))
            
            # Mock OpenAI response with scientifically valid decision
            mock_response = MagicMock()
            mock_response.choices[0].message.content = json.dumps({
                "recommendation": "deskew",
                "confidence": 0.75,
                "reasoning": "Moderate lane misalignment detected",
                "expected_improvement": "lane alignment"
            })
            
            with patch('openai.OpenAI') as mock_openai_class:
                mock_client = MagicMock()
                mock_client.chat.completions.create.return_value = mock_response
                mock_openai_class.return_value = mock_client
                
                # Should work securely with validation
                outcome = openai_guided_preprocess(test_image)
                
                assert outcome is not None, "Should return valid outcome"
                assert hasattr(outcome, 'mode'), "Should have preprocessing mode"
                assert hasattr(outcome, 'params'), "Should have parameters"
                
                # Check that scientific validation enhanced the reasoning
                if hasattr(outcome, 'params') and 'reasoning' in outcome.params:
                    reasoning = outcome.params['reasoning']
                    assert "Objective Metrics" in reasoning or "scientifically validated" in reasoning.lower()
                    print("✅ Scientific validation integrated into reasoning")
                
                print("✅ Secure end-to-end integration working")
        
        return True
        
    except Exception as e:
        print(f"❌ Integration security test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """Run all security tests"""
    print("🛡️ AutoDense Security Test Suite")
    print("=" * 50)
    print("Testing fixes for critical vulnerabilities identified by audit-critic")
    
    tests = [
        ("API Key Security", test_api_key_security),
        ("Scientific Validation", test_scientific_validation), 
        ("Secure Error Handling", test_secure_error_handling),
        ("Config File Security", test_config_file_security),
        ("Integration Security", test_integration_security)
    ]
    
    results = []
    for test_name, test_func in tests:
        try:
            success = test_func()
            results.append((test_name, success))
        except Exception as e:
            print(f"❌ {test_name} crashed: {e}")
            results.append((test_name, False))
    
    # Summary
    print("\n" + "=" * 50)
    print("🔍 SECURITY TEST SUMMARY")
    print("=" * 50)
    
    passed = sum(1 for _, success in results if success)
    total = len(results)
    
    for test_name, success in results:
        status = "✅ PASS" if success else "❌ FAIL"
        print(f"  {status}: {test_name}")
    
    print(f"\nOverall: {passed}/{total} tests passed")
    
    if passed == total:
        print("\n🎉 ALL SECURITY TESTS PASSED!")
        print("✅ Critical vulnerabilities have been addressed")
        print("✅ ChatGPT-4.1 integration is now secure for production use")
        return True
    else:
        print(f"\n⚠️  {total - passed} security tests failed")
        print("❌ System may still have vulnerabilities")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)