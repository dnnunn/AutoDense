"""Unit tests for prerequisites_panel.py component.

This test suite provides comprehensive coverage of the prerequisites panel
functionality including all prerequisite states and navigation behavior.
"""

import pytest
from unittest.mock import patch, Mock
from components.prerequisites_panel import render_prerequisites_panel


class TestPrerequisitesPanelBasicFunctionality:
    """Test basic functionality and return value validation."""

    @pytest.mark.unit
    def test_no_prerequisites_returns_false(self, mock_streamlit, prerequisite_states):
        """Test that function returns False when no prerequisites are met."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            result = render_prerequisites_panel()
            assert result is False

    @pytest.mark.unit
    def test_image_only_returns_false(self, mock_streamlit, prerequisite_states):
        """Test that function returns False when only image is uploaded."""
        with patch('streamlit.session_state', prerequisite_states['image_only']):
            result = render_prerequisites_panel()
            assert result is False

    @pytest.mark.unit
    def test_calibration_only_returns_false(self, mock_streamlit, prerequisite_states):
        """Test that function returns False when only calibration is done."""
        with patch('streamlit.session_state', prerequisite_states['calibration_only']):
            result = render_prerequisites_panel()
            assert result is False

    @pytest.mark.unit
    def test_both_prerequisites_returns_true(self, mock_streamlit, prerequisite_states):
        """Test that function returns True when both prerequisites are met."""
        with patch('streamlit.session_state', prerequisite_states['both_prerequisites']):
            result = render_prerequisites_panel()
            assert result is True


class TestPrerequisitesPanelUIRendering:
    """Test UI rendering behavior for different prerequisite states."""

    @pytest.mark.unit
    def test_renders_prerequisite_header(self, mock_streamlit, prerequisite_states):
        """Test that prerequisite header is always rendered."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            render_prerequisites_panel()
            mock_streamlit['markdown'].assert_any_call("### 📋 Prerequisites Status")

    @pytest.mark.unit
    def test_renders_columns_for_status(self, mock_streamlit, prerequisite_states):
        """Test that columns are created for status display."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            render_prerequisites_panel()
            mock_streamlit['columns'].assert_called_once_with(2)

    @pytest.mark.unit
    def test_no_image_shows_error_message(self, mock_streamlit, prerequisite_states):
        """Test error message is shown when image is missing."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            render_prerequisites_panel()
            mock_streamlit['error'].assert_any_call("❌ **Image Missing**")
            mock_streamlit['caption'].assert_any_call("👈 Upload gel image in **Lane Calibration** tab")

    @pytest.mark.unit
    def test_image_present_shows_success_message(self, mock_streamlit, prerequisite_states):
        """Test success message is shown when image is present."""
        with patch('streamlit.session_state', prerequisite_states['image_only']):
            render_prerequisites_panel()
            mock_streamlit['success'].assert_any_call("✅ **Image Ready:** test.jpg")
            mock_streamlit['caption'].assert_any_call("📐 Dimensions: 1024×768px")

    @pytest.mark.unit
    def test_no_calibration_shows_error_message(self, mock_streamlit, prerequisite_states):
        """Test error message is shown when calibration is missing."""
        with patch('streamlit.session_state', prerequisite_states['image_only']):
            render_prerequisites_panel()
            mock_streamlit['error'].assert_any_call("❌ **Calibration Missing**")
            mock_streamlit['caption'].assert_any_call("👈 Complete lane setup in **Lane Calibration** tab")

    @pytest.mark.unit
    def test_calibration_present_shows_success_message(self, mock_streamlit, prerequisite_states):
        """Test success message is shown when calibration is present."""
        with patch('streamlit.session_state', prerequisite_states['calibration_only']):
            render_prerequisites_panel()
            mock_streamlit['success'].assert_any_call("✅ **Lanes Calibrated:** 2 lanes configured")
            mock_streamlit['caption'].assert_any_call("🎯 Ready for SDS_PAGE analysis")


class TestPrerequisitesPanelNavigationLogic:
    """Test navigation button behavior and callback functionality."""

    @pytest.mark.unit
    def test_navigation_button_shown_when_prerequisites_missing(self, mock_streamlit, prerequisite_states, mock_goto_tab):
        """Test navigation button is shown when prerequisites are missing."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            render_prerequisites_panel(goto_tab=mock_goto_tab)
            mock_streamlit['button'].assert_called()

    @pytest.mark.unit
    def test_no_navigation_button_when_prerequisites_met(self, mock_streamlit, prerequisite_states, mock_goto_tab):
        """Test navigation button is not shown when prerequisites are met."""
        with patch('streamlit.session_state', prerequisite_states['both_prerequisites']):
            render_prerequisites_panel(goto_tab=mock_goto_tab)
            # Button should not be called when prerequisites are met
            mock_streamlit['button'].assert_not_called()

    @pytest.mark.unit
    def test_navigation_callback_not_called_without_goto_tab(self, mock_streamlit, prerequisite_states):
        """Test that navigation works properly when goto_tab is None."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            # This should not raise an exception
            result = render_prerequisites_panel(goto_tab=None)
            assert result is False

    @pytest.mark.unit
    def test_analysis_locked_message_shown_when_missing_prerequisites(self, mock_streamlit, prerequisite_states):
        """Test that analysis locked message is shown when prerequisites are missing."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            render_prerequisites_panel()
            mock_streamlit['info'].assert_any_call("🔒 **Analysis locked until prerequisites are completed.**")

    @pytest.mark.unit
    def test_analysis_capabilities_expander_shown_when_missing_prerequisites(self, mock_streamlit, prerequisite_states):
        """Test that analysis capabilities expander is shown when prerequisites are missing."""
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            render_prerequisites_panel()
            mock_streamlit['expander'].assert_called_with("🔮 Analysis Capabilities (Available After Prerequisites)", expanded=True)


class TestPrerequisitesPanelEdgeCases:
    """Test edge cases and error conditions."""

    @pytest.mark.unit
    def test_handles_missing_image_metadata_gracefully(self, mock_streamlit, prerequisite_states):
        """Test that missing image metadata is handled gracefully."""
        # Create new state with image but no metadata
        mock_state = Mock()
        mock_state.res_uploaded_image = Mock()  # Has image
        mock_state.res_lane_boundaries = None   # No calibration
        mock_state.res_image_metadata = None    # No metadata
        mock_state.params_gel_type = 'sds_page'

        with patch('streamlit.session_state', mock_state):
            result = render_prerequisites_panel()
            # Should still show image present but with defaults
            mock_streamlit['success'].assert_any_call("✅ **Image Ready:** Unknown")
            mock_streamlit['caption'].assert_any_call("📐 Dimensions: 0×0px")
            assert result is False  # Still missing calibration

    @pytest.mark.unit
    def test_handles_empty_image_metadata_gracefully(self, mock_streamlit, prerequisite_states):
        """Test that empty image metadata dictionary is handled gracefully."""
        mock_state = Mock()
        mock_state.res_uploaded_image = Mock()  # Has image
        mock_state.res_lane_boundaries = None   # No calibration
        mock_state.res_image_metadata = {}      # Empty metadata
        mock_state.params_gel_type = 'sds_page'

        with patch('streamlit.session_state', mock_state):
            result = render_prerequisites_panel()
            mock_streamlit['success'].assert_any_call("✅ **Image Ready:** Unknown")
            mock_streamlit['caption'].assert_any_call("📐 Dimensions: 0×0px")
            assert result is False

    @pytest.mark.unit
    def test_handles_empty_lane_boundaries_list(self, mock_streamlit, prerequisite_states):
        """Test that empty lane boundaries list is handled as calibrated (0 lanes)."""
        mock_state = Mock()
        mock_state.res_uploaded_image = Mock()   # Has image
        mock_state.res_lane_boundaries = []      # Empty list
        mock_state.res_image_metadata = {'filename': 'test.jpg', 'dimensions': (100, 100)}
        mock_state.params_gel_type = 'sds_page'

        with patch('streamlit.session_state', mock_state):
            result = render_prerequisites_panel()
            # Empty list is not None, so it shows as calibrated with 0 lanes
            mock_streamlit['success'].assert_any_call("✅ **Lanes Calibrated:** 0 lanes configured")
            assert result is True  # Both image and 'calibration' are present

    @pytest.mark.unit
    def test_handles_different_gel_types(self, mock_streamlit, prerequisite_states):
        """Test that different gel types are displayed correctly."""
        mock_state = Mock()
        mock_state.res_uploaded_image = None     # No image
        mock_state.res_lane_boundaries = [{'x0': 100, 'x1': 200}]  # Has calibration
        mock_state.res_image_metadata = None
        mock_state.params_gel_type = 'native_page'

        with patch('streamlit.session_state', mock_state):
            render_prerequisites_panel()
            mock_streamlit['caption'].assert_any_call("🎯 Ready for NATIVE_PAGE analysis")


class TestPrerequisitesPanelIntegration:
    """Integration-style tests for component behavior."""

    @pytest.mark.integration
    def test_complete_workflow_no_to_both_prerequisites(self, mock_streamlit, prerequisite_states, mock_goto_tab):
        """Test complete workflow from no prerequisites to both met."""
        # Start with no prerequisites
        with patch('streamlit.session_state', prerequisite_states['no_prerequisites']):
            result1 = render_prerequisites_panel(goto_tab=mock_goto_tab)
            assert result1 is False
            mock_streamlit['error'].assert_any_call("❌ **Image Missing**")
            mock_streamlit['error'].assert_any_call("❌ **Calibration Missing**")

        # Reset mock call counts
        for mock_obj in mock_streamlit.values():
            if hasattr(mock_obj, 'reset_mock'):
                mock_obj.reset_mock()

        # Move to both prerequisites met
        with patch('streamlit.session_state', prerequisite_states['both_prerequisites']):
            result2 = render_prerequisites_panel(goto_tab=mock_goto_tab)
            assert result2 is True
            mock_streamlit['success'].assert_any_call("✅ **Image Ready:** test.jpg")
            mock_streamlit['success'].assert_any_call("✅ **Lanes Calibrated:** 2 lanes configured")

    @pytest.mark.integration
    def test_function_signature_compatibility(self, mock_streamlit):
        """Test that function signature is backward compatible."""
        # Test with no arguments
        mock_state = Mock()
        mock_state.res_uploaded_image = None
        mock_state.res_lane_boundaries = None
        mock_state.res_image_metadata = None
        mock_state.params_gel_type = 'sds_page'

        with patch('streamlit.session_state', mock_state):
            result = render_prerequisites_panel()
            assert isinstance(result, bool)

        # Test with goto_tab argument
        with patch('streamlit.session_state', mock_state):
            result = render_prerequisites_panel(goto_tab=Mock())
            assert isinstance(result, bool)

    @pytest.mark.unit
    def test_return_type_validation(self, mock_streamlit, prerequisite_states):
        """Test that function always returns a boolean."""
        for state_name, state in prerequisite_states.items():
            with patch('streamlit.session_state', state):
                result = render_prerequisites_panel()
                assert isinstance(result, bool), f"Expected bool for state {state_name}, got {type(result)}"


class TestPrerequisitesPanelPerformance:
    """Test performance characteristics of the component."""

    @pytest.mark.unit
    def test_minimal_streamlit_calls_when_prerequisites_met(self, mock_streamlit, prerequisite_states):
        """Test that minimal Streamlit calls are made when prerequisites are met."""
        with patch('streamlit.session_state', prerequisite_states['both_prerequisites']):
            render_prerequisites_panel()

            # Should have basic structure calls
            assert mock_streamlit['markdown'].call_count >= 1  # Header
            assert mock_streamlit['columns'].call_count == 1   # Column layout
            assert mock_streamlit['success'].call_count == 2   # Two success messages

            # Should NOT have error/info/button calls when prerequisites are met
            mock_streamlit['error'].assert_not_called()
            mock_streamlit['info'].assert_not_called()
            mock_streamlit['button'].assert_not_called()
            mock_streamlit['expander'].assert_not_called()

    @pytest.mark.unit
    def test_no_expensive_operations(self, mock_streamlit, prerequisite_states):
        """Test that no expensive operations are performed during rendering."""
        with patch('streamlit.session_state', prerequisite_states['both_prerequisites']):
            # This should complete quickly - just checking it doesn't hang
            result = render_prerequisites_panel()
            assert result is True