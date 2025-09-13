#!/usr/bin/env python3
"""
Comprehensive test suite for AutoDense parameter translation system.

This test suite validates:
1. Translation accuracy from UI parameters to backend parameters
2. Boundary condition handling
3. User feedback validation
4. Regression prevention for original error scenarios
5. Type safety and error handling
"""

import pytest
import sys
from pathlib import Path

# Add the project root to Python path for imports
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

# Mock the streamlit import for testing
class MockStreamlit:
    def __init__(self):
        pass

sys.modules['streamlit'] = MockStreamlit()

# Import the translation functions
from ui.streamlit_autodense_app import (
    translate_ui_to_backend_params,
    validate_and_explain_params,
    create_safe_ad_params
)

class TestParameterTranslation:
    """Test core parameter translation functionality."""

    def test_gel_type_translation_sds(self):
        """Test gel_type 'sds_page' translates to modality 'sds'."""
        ui_params = {"gel_type": "sds_page"}
        backend_params, log = translate_ui_to_backend_params(ui_params)

        assert backend_params["modality"] == "sds"
        assert any("gel_type 'sds_page' → modality 'sds'" in entry for entry in log)

    def test_gel_type_translation_dna(self):
        """Test gel_type 'etbr_agarose' translates to modality 'dna'."""
        ui_params = {"gel_type": "etbr_agarose"}
        backend_params, log = translate_ui_to_backend_params(ui_params)

        assert backend_params["modality"] == "dna"
        assert any("gel_type 'etbr_agarose' → modality 'dna'" in entry for entry in log)

    def test_gel_type_unknown_defaults_to_sds(self):
        """Test unknown gel_type defaults to 'sds' with warning."""
        ui_params = {"gel_type": "unknown_type"}
        backend_params, log = translate_ui_to_backend_params(ui_params)

        assert backend_params["modality"] == "sds"
        assert any("Unknown gel_type 'unknown_type'" in entry for entry in log)

    def test_conf_threshold_mapping(self):
        """Test confidence threshold mapping to ladder_min_score."""
        test_cases = [
            (0.9, 0.45),   # High confidence
            (0.7, 0.40),   # Medium-high
            (0.5, 0.35),   # Medium
            (0.3, 0.30),   # Medium-low
            (0.1, 0.25),   # Low confidence
        ]

        for conf_threshold, expected_ladder_score in test_cases:
            ui_params = {"conf_threshold": conf_threshold}
            backend_params, log = translate_ui_to_backend_params(ui_params)

            assert backend_params["ladder_min_score"] == expected_ladder_score
            assert any(f"conf_threshold {conf_threshold:.2f} → ladder_min_score {expected_ladder_score:.2f}"
                      in entry for entry in log)

    def test_mw_lane_ignored(self):
        """Test mw_lane parameter is ignored with explanation."""
        ui_params = {"mw_lane": 3}
        backend_params, log = translate_ui_to_backend_params(ui_params)

        assert "mw_lane" not in backend_params
        assert any("mw_lane 3 → ignored" in entry for entry in log)

    def test_known_backend_params_passthrough(self):
        """Test known backend parameters pass through unchanged."""
        ui_params = {
            "min_lanes": 8,
            "max_lanes": 12,
            "comb": 10,
            "bg_radius": 25,
            "invert": "auto"
        }
        backend_params, log = translate_ui_to_backend_params(ui_params)

        for key, value in ui_params.items():
            assert backend_params[key] == value
            assert any(f"{key} {value} → passed through" in entry for entry in log)

    def test_unknown_params_ignored(self):
        """Test unknown parameters are ignored with warning."""
        ui_params = {"unknown_param": "some_value"}
        backend_params, log = translate_ui_to_backend_params(ui_params)

        assert "unknown_param" not in backend_params
        assert any("Unknown parameter 'unknown_param' → ignored" in entry for entry in log)

    def test_comprehensive_translation(self):
        """Test complete parameter set translation."""
        ui_params = {
            "gel_type": "sds_page",
            "conf_threshold": 0.6,
            "mw_lane": 1,
            "min_lanes": 6,
            "max_lanes": 16,
            "bg_radius": 30,
            "unknown_param": "ignored"
        }

        backend_params, log = translate_ui_to_backend_params(ui_params)

        expected_backend = {
            "modality": "sds",
            "ladder_min_score": 0.40,
            "min_lanes": 6,
            "max_lanes": 16,
            "bg_radius": 30
        }

        assert backend_params == expected_backend
        assert len(log) == 6  # 5 valid translations + 1 warning

class TestParameterValidation:
    """Test parameter validation and user feedback."""

    def test_valid_parameters(self):
        """Test validation of valid parameters."""
        ui_params = {
            "gel_type": "sds_page",
            "conf_threshold": 0.5,
            "mw_lane": 2
        }

        is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

        assert is_valid
        assert len(errors) == 0
        assert len(explanations) > 0
        assert backend_params["modality"] == "sds"
        assert backend_params["ladder_min_score"] == 0.35

    def test_invalid_conf_threshold_range(self):
        """Test validation fails for out-of-range conf_threshold."""
        test_cases = [-0.1, 1.5, 2.0]

        for invalid_conf in test_cases:
            ui_params = {"conf_threshold": invalid_conf}
            is_valid, _, _, errors = validate_and_explain_params(ui_params)

            assert not is_valid
            assert len(errors) == 1
            assert "conf_threshold must be between 0.0 and 1.0" in errors[0]

    def test_invalid_mw_lane(self):
        """Test validation fails for invalid mw_lane."""
        test_cases = [0, -1, "not_an_int"]

        for invalid_mw in test_cases:
            ui_params = {"mw_lane": invalid_mw}
            is_valid, _, _, errors = validate_and_explain_params(ui_params)

            assert not is_valid
            assert len(errors) == 1
            assert "mw_lane must be a positive integer" in errors[0]

    def test_invalid_gel_type(self):
        """Test validation fails for invalid gel_type."""
        ui_params = {"gel_type": "invalid_type"}
        is_valid, _, _, errors = validate_and_explain_params(ui_params)

        assert not is_valid
        assert len(errors) == 1
        assert "gel_type must be 'sds_page' or 'etbr_agarose'" in errors[0]

    def test_multiple_errors(self):
        """Test handling of multiple validation errors."""
        ui_params = {
            "gel_type": "invalid",
            "conf_threshold": -0.5,
            "mw_lane": 0
        }

        is_valid, _, _, errors = validate_and_explain_params(ui_params)

        assert not is_valid
        assert len(errors) == 3

    def test_explanation_format(self):
        """Test explanation text format and completeness."""
        ui_params = {
            "gel_type": "sds_page",
            "conf_threshold": 0.7,
            "mw_lane": 3,
            "min_lanes": 8
        }

        is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

        assert is_valid

        # Check explanation structure
        explanation_text = "\n".join(explanations)
        assert "**Parameter Translation:**" in explanation_text
        assert "**Final Backend Parameters:**" in explanation_text

        # Check specific translations are explained
        assert "gel_type 'sds_page' → modality 'sds'" in explanation_text
        assert "conf_threshold 0.70 → ladder_min_score 0.40" in explanation_text
        assert "mw_lane 3 → ignored" in explanation_text
        assert "min_lanes 8 → passed through" in explanation_text

class TestRegressionPrevention:
    """Test against original error scenarios that motivated this translation layer."""

    def test_original_type_error_scenario(self):
        """Test the original TypeError scenario is prevented."""
        # This was the original error: TypeError when UI passed gel_type, conf_threshold, mw_lane
        ui_params = {
            "gel_type": "sds_page",
            "conf_threshold": 0.30,
            "mw_lane": 1
        }

        # Before the translation layer, this would cause TypeError
        # Now it should translate successfully
        is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

        assert is_valid
        assert len(errors) == 0
        assert backend_params["modality"] == "sds"
        assert backend_params["ladder_min_score"] == 0.30
        assert "mw_lane" not in backend_params  # Correctly ignored

    def test_parameter_mismatch_handling(self):
        """Test handling of parameter mismatches between UI and backend."""
        # Test various parameter combinations that might cause issues
        problematic_combinations = [
            {"gel_type": "etbr_agarose", "conf_threshold": 0.9},  # DNA with high confidence
            {"gel_type": "sds_page", "mw_lane": 999},             # Invalid lane number
            {"gel_type": "unknown", "conf_threshold": 0.01},      # Unknown type + very low conf
        ]

        for ui_params in problematic_combinations:
            # Should not raise exceptions
            try:
                is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)
                # Validation may fail, but it shouldn't crash
                assert isinstance(is_valid, bool)
                assert isinstance(backend_params, dict)
                assert isinstance(explanations, list)
                assert isinstance(errors, list)
            except Exception as e:
                pytest.fail(f"Parameter translation crashed with {ui_params}: {e}")

class TestBoundaryConditions:
    """Test edge cases and boundary conditions."""

    def test_empty_params(self):
        """Test handling of empty parameter dict."""
        ui_params = {}
        backend_params, log = translate_ui_to_backend_params(ui_params)

        assert backend_params == {}
        assert log == []

    def test_boundary_conf_threshold_values(self):
        """Test exact boundary values for conf_threshold mapping."""
        boundary_cases = [
            (0.0, 0.25),   # Minimum
            (0.2, 0.30),   # Boundary
            (0.4, 0.35),   # Boundary
            (0.6, 0.40),   # Boundary
            (0.8, 0.45),   # Boundary
            (1.0, 0.45),   # Maximum
        ]

        for conf_threshold, expected_score in boundary_cases:
            ui_params = {"conf_threshold": conf_threshold}
            backend_params, log = translate_ui_to_backend_params(ui_params)

            assert backend_params["ladder_min_score"] == expected_score

    def test_type_coercion(self):
        """Test type coercion in validation."""
        # String numbers should be handled
        ui_params = {
            "conf_threshold": "0.5",  # String instead of float
        }

        try:
            is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)
            # Should either handle gracefully or provide clear error
            if not is_valid:
                assert len(errors) > 0
        except ValueError:
            # Acceptable if clear error is raised
            pass

def run_comprehensive_test_suite():
    """Run the complete test suite and report results."""
    print("🧪 Running AutoDense Parameter Translation Test Suite")
    print("=" * 60)

    # Run pytest programmatically
    exit_code = pytest.main([
        __file__,
        "-v",
        "--tb=short",
        "-x"  # Stop on first failure for debugging
    ])

    if exit_code == 0:
        print("\n✅ All tests passed! Parameter translation system is working correctly.")
        print("\n🎯 Key validations completed:")
        print("  • UI parameters translate correctly to backend format")
        print("  • Invalid parameters are caught with clear error messages")
        print("  • Original TypeError scenarios are prevented")
        print("  • Boundary conditions are handled properly")
        print("  • User feedback provides clear parameter explanations")
    else:
        print("\n❌ Some tests failed. Review the output above for details.")

    return exit_code

if __name__ == "__main__":
    exit_code = run_comprehensive_test_suite()
    sys.exit(exit_code)