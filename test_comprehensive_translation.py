#!/usr/bin/env python3
"""
Comprehensive test for parameter translation functionality.
"""

import sys
from parameter_translation_standalone import (
    translate_ui_to_backend_params,
    validate_and_explain_params,
    create_safe_ad_params
)

def test_all_scenarios():
    """Test comprehensive scenarios."""
    print("🧪 Comprehensive Parameter Translation Test Suite")
    print("=" * 60)

    test_cases = [
        {
            "name": "Original TypeError scenario",
            "input": {"gel_type": "sds_page", "conf_threshold": 0.30, "mw_lane": 1},
            "expected_modality": "sds",
            "expected_ladder_score": 0.30,
            "should_ignore_mw_lane": True
        },
        {
            "name": "DNA gel with high confidence",
            "input": {"gel_type": "etbr_agarose", "conf_threshold": 0.85, "mw_lane": 2},
            "expected_modality": "dna",
            "expected_ladder_score": 0.45,
            "should_ignore_mw_lane": True
        },
        {
            "name": "Low confidence SDS gel",
            "input": {"gel_type": "sds_page", "conf_threshold": 0.15, "mw_lane": 3},
            "expected_modality": "sds",
            "expected_ladder_score": 0.25,
            "should_ignore_mw_lane": True
        },
        {
            "name": "With additional backend parameters",
            "input": {
                "gel_type": "sds_page",
                "conf_threshold": 0.5,
                "mw_lane": 1,
                "min_lanes": 8,
                "max_lanes": 12,
                "bg_radius": 25
            },
            "expected_modality": "sds",
            "expected_ladder_score": 0.35,
            "should_ignore_mw_lane": True
        }
    ]

    passed = 0
    failed = 0

    for case in test_cases:
        print(f"\n🔍 Testing: {case['name']}")
        print(f"   Input: {case['input']}")

        try:
            # Test validation
            is_valid, backend_params, explanations, errors = validate_and_explain_params(case['input'])

            if not is_valid:
                print(f"   ❌ Validation failed: {errors}")
                failed += 1
                continue

            # Check expected translations
            if backend_params.get('modality') != case['expected_modality']:
                print(f"   ❌ Wrong modality: expected {case['expected_modality']}, got {backend_params.get('modality')}")
                failed += 1
                continue

            if backend_params.get('ladder_min_score') != case['expected_ladder_score']:
                print(f"   ❌ Wrong ladder_min_score: expected {case['expected_ladder_score']}, got {backend_params.get('ladder_min_score')}")
                failed += 1
                continue

            if case['should_ignore_mw_lane'] and 'mw_lane' in backend_params:
                print("   ❌ mw_lane should have been ignored")
                failed += 1
                continue

            print(f"   ✅ Backend params: {backend_params}")
            print(f"   ✅ Explanations provided: {len(explanations)} lines")
            passed += 1

        except Exception as e:
            print(f"   ❌ Exception: {e}")
            failed += 1

    # Test error cases
    print(f"\n🔍 Testing error cases...")
    error_cases = [
        {"conf_threshold": -0.1},  # Out of range
        {"conf_threshold": 1.5},   # Out of range
        {"mw_lane": 0},            # Invalid lane
        {"mw_lane": -1},           # Invalid lane
        {"gel_type": "invalid"},   # Unknown gel type
    ]

    for error_case in error_cases:
        print(f"   Testing invalid: {error_case}")
        is_valid, _, _, errors = validate_and_explain_params(error_case)

        if is_valid:
            print(f"   ❌ Should have failed validation")
            failed += 1
        else:
            print(f"   ✅ Correctly rejected: {errors[0]}")
            passed += 1

    # Summary
    print(f"\n📊 Test Results: {passed} passed, {failed} failed")

    if failed == 0:
        print("\n🎉 All tests passed! Parameter translation system is robust and ready for deployment.")
        return True
    else:
        print(f"\n⚠️ {failed} tests failed. Review implementation.")
        return False

def demonstrate_user_feedback():
    """Demonstrate the user feedback system."""
    print("\n" + "=" * 60)
    print("📋 User Feedback Demonstration")
    print("=" * 60)

    ui_params = {
        "gel_type": "sds_page",
        "conf_threshold": 0.65,
        "mw_lane": 2,
        "min_lanes": 6,
        "unknown_param": "should_be_ignored"
    }

    print(f"User input parameters: {ui_params}")

    is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

    print("\n📋 User would see this feedback:")
    print("-" * 40)
    for explanation in explanations:
        print(explanation)

    print(f"\n🔧 Backend would receive: {backend_params}")

if __name__ == "__main__":
    success = test_all_scenarios()
    demonstrate_user_feedback()

    if success:
        print("\n✅ Parameter translation system is ready for integration!")
        sys.exit(0)
    else:
        print("\n❌ Fix issues before deployment.")
        sys.exit(1)