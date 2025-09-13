"""Tests for UI data helper utilities.

These tests validate the extracted data manipulation functions to ensure
scientific data integrity during refactoring.
"""

import pytest
import json
import numpy as np
from ui.utils.data_helpers import (
    obj_to_dict, extract_json, calculate_lane_metrics, convert_to_csv
)


class TestObjToDict:
    """Test object to dictionary conversion."""

    def test_dict_passthrough(self):
        """Test that dictionaries pass through unchanged."""
        input_dict = {"lane": 1, "intensity": 255.5}
        result = obj_to_dict(input_dict)
        assert result == input_dict

    def test_none_input(self):
        """Test None input returns empty dict."""
        result = obj_to_dict(None)
        assert result == {}

    def test_simple_object(self):
        """Test conversion of simple object with attributes."""
        class TestObj:
            def __init__(self):
                self.lane = 1
                self.intensity = 255.5
                self._private = "hidden"

            def method(self):
                return "not included"

        obj = TestObj()
        result = obj_to_dict(obj)

        assert result["lane"] == 1
        assert result["intensity"] == 255.5
        assert "_private" not in result
        assert "method" not in result


class TestExtractJson:
    """Test JSON extraction from text."""

    def test_valid_json_extraction(self):
        """Test extraction of valid JSON from text."""
        text = 'Some text {"key": "value", "number": 42} more text'
        result = extract_json(text)
        assert result == {"key": "value", "number": 42}

    def test_nested_json(self):
        """Test extraction of nested JSON structures."""
        text = 'Data: {"outer": {"inner": [1, 2, 3]}, "simple": true}'
        result = extract_json(text)
        expected = {"outer": {"inner": [1, 2, 3]}, "simple": True}
        assert result == expected

    def test_no_json_found(self):
        """Test error when no JSON is found."""
        text = "This is just plain text with no JSON"
        with pytest.raises(ValueError, match="No JSON object found"):
            extract_json(text)

    def test_invalid_json(self):
        """Test error with malformed JSON."""
        text = 'Bad JSON: {"key": value}'  # Missing quotes around value
        with pytest.raises(json.JSONDecodeError):
            extract_json(text)


class TestCalculateLaneMetrics:
    """Test lane metrics calculation for gel analysis."""

    def test_empty_input(self):
        """Test handling of empty lane data."""
        result = calculate_lane_metrics([])
        expected = {
            'spacings': [], 'widths': [], 'min_spacing': 0, 'max_spacing': 0,
            'mean_spacing': 0, 'spacing_cv': 0
        }
        assert result == expected

    def test_single_lane(self):
        """Test handling of single lane (no spacings possible)."""
        lanes = [{"center_px": 100, "width_px": 20}]
        result = calculate_lane_metrics(lanes)

        assert result['spacings'] == []
        assert result['widths'] == [20]
        assert result['min_spacing'] == 0
        assert result['max_spacing'] == 0
        assert result['mean_spacing'] == 0
        assert result['spacing_cv'] == 0

    def test_multiple_lanes(self):
        """Test normal case with multiple lanes."""
        lanes = [
            {"center_px": 100, "width_px": 20},
            {"center_px": 150, "width_px": 22},
            {"center_px": 200, "width_px": 18}
        ]
        result = calculate_lane_metrics(lanes)

        # Check spacings: |150-100| = 50, |200-150| = 50
        assert result['spacings'] == [50, 50]
        assert result['widths'] == [20, 22, 18]
        assert result['min_spacing'] == 50
        assert result['max_spacing'] == 50
        assert result['mean_spacing'] == 50
        assert result['spacing_cv'] == 0.0  # No variation

    def test_irregular_spacing(self):
        """Test lanes with irregular spacing."""
        lanes = [
            {"center_px": 100, "width_px": 20},
            {"center_px": 140, "width_px": 20},  # spacing = 40
            {"center_px": 200, "width_px": 20}   # spacing = 60
        ]
        result = calculate_lane_metrics(lanes)

        assert result['spacings'] == [40, 60]
        assert result['min_spacing'] == 40
        assert result['max_spacing'] == 60
        assert result['mean_spacing'] == 50
        # CV should be > 0 due to variation
        assert result['spacing_cv'] > 0

    def test_numpy_conversion(self):
        """Test that numpy arrays are properly converted to lists."""
        lanes = [
            {"center_px": 100, "width_px": 20},
            {"center_px": 150, "width_px": 25}
        ]
        result = calculate_lane_metrics(lanes)

        # Ensure results are Python lists, not numpy arrays
        assert isinstance(result['spacings'], list)
        assert isinstance(result['widths'], list)
        assert isinstance(result['min_spacing'], float)


class TestConvertToCsv:
    """Test CSV conversion functionality."""

    def test_basic_conversion(self):
        """Test basic CSV conversion."""
        rows = [
            {"lane": 1, "intensity": 255.5, "mw": 50.0},
            {"lane": 2, "intensity": 180.2, "mw": 37.0}
        ]
        columns = ["lane", "intensity", "mw"]

        result = convert_to_csv(rows, columns)
        lines = result.strip().split('\n')

        assert lines[0] == "lane,intensity,mw"
        assert lines[1] == "1,255.5,50.0"
        assert lines[2] == "2,180.2,37.0"

    def test_missing_values(self):
        """Test handling of missing values."""
        rows = [
            {"lane": 1, "intensity": 255.5},  # Missing 'mw'
            {"lane": 2, "mw": 37.0}           # Missing 'intensity'
        ]
        columns = ["lane", "intensity", "mw"]

        result = convert_to_csv(rows, columns)
        lines = result.strip().split('\n')

        assert "1,255.5," in lines[1]  # Empty string for missing mw
        assert "2,,37.0" in lines[2]   # Empty string for missing intensity

    def test_empty_rows(self):
        """Test handling of empty row list."""
        result = convert_to_csv([], ["lane", "intensity"])
        assert result.strip() == "lane,intensity"

    def test_scientific_notation_preservation(self):
        """Test that scientific notation is preserved in output."""
        rows = [{"measurement": 1.23e-6, "sample": "A1"}]
        columns = ["sample", "measurement"]

        result = convert_to_csv(rows, columns)
        # Should contain scientific notation representation
        assert ("1.23e-06" in result.lower() or
                "1.23e-6" in result or
                "0.00000123" in result)

    def test_boolean_conversion(self):
        """Test that boolean values are properly converted."""
        rows = [{"active": True, "validated": False}]
        columns = ["active", "validated"]

        result = convert_to_csv(rows, columns)
        lines = result.strip().split('\n')

        assert "True,False" in lines[1]

    def test_none_values(self):
        """Test that None values become empty strings."""
        rows = [{"value": None, "name": "test"}]
        columns = ["name", "value"]

        result = convert_to_csv(rows, columns)
        lines = result.strip().split('\n')

        assert lines[1] == "test,"  # None becomes empty string


# Integration test for typical scientific workflow
class TestScientificWorkflow:
    """Integration tests simulating typical scientific data processing."""

    def test_gel_analysis_workflow(self):
        """Test complete gel analysis data processing workflow."""
        # Simulate lane detection results
        lane_data = [
            {"center_px": 100, "width_px": 18},
            {"center_px": 150, "width_px": 20},
            {"center_px": 200, "width_px": 19}
        ]

        # Calculate metrics
        metrics = calculate_lane_metrics(lane_data)

        # Verify metrics are reasonable
        assert len(metrics['spacings']) == 2
        assert len(metrics['widths']) == 3
        assert metrics['mean_spacing'] > 0

        # Convert lane data to CSV
        csv_result = convert_to_csv(lane_data, ["center_px", "width_px"])

        # Verify CSV structure
        lines = csv_result.strip().split('\n')
        assert len(lines) == 4  # Header + 3 data rows
        assert lines[0] == "center_px,width_px"

    def test_band_quantification_workflow(self):
        """Test band quantification data export."""
        # Simulate band quantification results
        band_data = [
            {"lane": 1, "band_id": "B1", "intensity": 255.7, "mw_kda": 50.2, "rf": 0.25},
            {"lane": 1, "band_id": "B2", "intensity": 180.3, "mw_kda": 37.1, "rf": 0.45},
            {"lane": 2, "band_id": "B1", "intensity": 200.1, "mw_kda": 49.8, "rf": 0.26}
        ]

        columns = ["lane", "band_id", "intensity", "mw_kda", "rf"]
        csv_result = convert_to_csv(band_data, columns)

        # Verify scientific precision is maintained
        assert "255.7" in csv_result
        assert "0.25" in csv_result

        # Verify all bands are included
        lines = csv_result.strip().split('\n')
        assert len(lines) == 4  # Header + 3 bands


if __name__ == "__main__":
    pytest.main([__file__, "-v"])