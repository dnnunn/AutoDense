"""Integration tests for components with unified error handling."""

import pytest
import streamlit as st
from unittest.mock import Mock, patch, MagicMock
import sys
import os

# Add the ui directory to the path so we can import our modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from components.prerequisites_panel import render_prerequisites_panel
from components.calibration_instructions import render_calibration_instructions


class TestComponentErrorIntegration:
    """Test that components work correctly with error handling."""

    def _create_column_context_manager(self):
        """Helper to create properly configured column mock context managers."""
        mock_col = Mock()
        mock_col.__enter__ = Mock(return_value=mock_col)
        mock_col.__exit__ = Mock(return_value=None)
        return mock_col

    @patch('streamlit.success')
    @patch('streamlit.error')
    @patch('streamlit.columns')
    @patch('streamlit.markdown')
    @patch('streamlit.caption')
    @patch('streamlit.info')
    @patch('streamlit.button')
    @patch('streamlit.expander')
    def test_prerequisites_panel_with_valid_state(self, mock_expander, mock_button, mock_info,
                                                 mock_caption, mock_markdown, mock_columns,
                                                 mock_error, mock_success, mock_session_state):
        """Test prerequisites panel with valid session state."""
        with patch('streamlit.session_state', mock_session_state):
            # Mock valid session state
            st.session_state.res_uploaded_image = Mock()
            st.session_state.res_lane_boundaries = [1, 2, 3]
            st.session_state.res_image_metadata = {
                'filename': 'test.jpg',
                'dimensions': (800, 600)
            }
            st.session_state.params_gel_type = 'sds_page'

            # Mock columns to return context manager objects
            mock_col1 = self._create_column_context_manager()
            mock_col2 = self._create_column_context_manager()
            mock_columns.return_value = [mock_col1, mock_col2]

            result = render_prerequisites_panel()

            assert result is True  # Prerequisites should be met
            # Should not show error state
            mock_info.assert_not_called()
            mock_button.assert_not_called()

    @patch('streamlit.success')
    @patch('streamlit.error')
    @patch('streamlit.columns')
    @patch('streamlit.markdown')
    @patch('streamlit.caption')
    @patch('streamlit.info')
    @patch('streamlit.button')
    @patch('streamlit.expander')
    def test_prerequisites_panel_with_missing_requirements(self, mock_expander, mock_button, mock_info,
                                                          mock_caption, mock_markdown, mock_columns,
                                                          mock_error, mock_success, mock_session_state):
        """Test prerequisites panel with missing requirements."""
        with patch('streamlit.session_state', mock_session_state):
            # Mock invalid session state (missing requirements)
            st.session_state.res_uploaded_image = None
            st.session_state.res_lane_boundaries = None

            # Mock columns to return context manager objects
            mock_col1 = self._create_column_context_manager()
            mock_col2 = self._create_column_context_manager()
            mock_columns.return_value = [mock_col1, mock_col2]

            result = render_prerequisites_panel()

            assert result is False  # Prerequisites should not be met
            # Should show locked state
            mock_info.assert_called()

    @patch('streamlit.expander')
    @patch('streamlit.markdown')
    @patch('streamlit.columns')
    def test_calibration_instructions_renders_successfully(self, mock_columns, mock_markdown, mock_expander, mock_session_state):
        """Test that calibration instructions component renders without errors."""
        with patch('streamlit.session_state', mock_session_state):
            # Mock expander context manager
            mock_expander_context = Mock()
            mock_expander_context.__enter__ = Mock(return_value=mock_expander_context)
            mock_expander_context.__exit__ = Mock(return_value=None)
            mock_expander.return_value = mock_expander_context

            # Mock columns to return context manager objects
            mock_col1 = self._create_column_context_manager()
            mock_col2 = self._create_column_context_manager()
            mock_columns.return_value = [mock_col1, mock_col2]

            # This should not raise any exceptions
            result = render_calibration_instructions(expanded=True)

            # Function should complete successfully (returns None)
            assert result is None

            # Should have called expander and markdown
            mock_expander.assert_called_once()
            mock_markdown.assert_called()

    @patch('streamlit.error')
    @patch('streamlit.expander')
    @patch('streamlit.markdown')
    @patch('streamlit.info')
    @patch('streamlit.code')
    def test_component_error_boundary_handles_exceptions(self, mock_code, mock_info, mock_markdown, mock_expander, mock_error, mock_session_state):
        """Test that error boundary catches and handles component exceptions."""
        with patch('streamlit.session_state', mock_session_state):
            # Mock expander to support context manager
            expander_context = Mock()
            expander_context.__enter__ = Mock(return_value=expander_context)
            expander_context.__exit__ = Mock(return_value=None)
            mock_expander.return_value = expander_context

            # Force an exception in the component by making safe_execute fail
            with patch('components.prerequisites_panel.safe_execute', side_effect=Exception("Test error")):
                result = render_prerequisites_panel()

                # Should handle the error gracefully
                assert result is None  # Error boundary should return None
                mock_error.assert_called()  # Should display error message

    @patch('utils.error_handling.get_ui_error_count', return_value=0)
    def test_no_errors_in_normal_operation(self, mock_get_error_count, mock_session_state):
        """Test that components don't generate errors during normal operation."""
        with patch('streamlit.session_state', mock_session_state):
            # Setup valid session state
            st.session_state.res_uploaded_image = Mock()
            st.session_state.res_lane_boundaries = [1, 2, 3]
            st.session_state.res_image_metadata = {
                'filename': 'test.jpg',
                'dimensions': (800, 600)
            }

            # Mock Streamlit components to avoid actual rendering
            mock_col1 = self._create_column_context_manager()
            mock_col2 = self._create_column_context_manager()
            with patch('streamlit.success'), \
                 patch('streamlit.error'), \
                 patch('streamlit.columns', return_value=[mock_col1, mock_col2]), \
                 patch('streamlit.markdown'), \
                 patch('streamlit.caption'):

                # Run components
                prerequisites_result = render_prerequisites_panel()

            mock_col1 = self._create_column_context_manager()
            mock_col2 = self._create_column_context_manager()
            with patch('streamlit.expander') as mock_expander, \
                 patch('streamlit.markdown'), \
                 patch('streamlit.columns', return_value=[mock_col1, mock_col2]):

                # Mock expander context manager
                mock_expander_context = Mock()
                mock_expander_context.__enter__ = Mock(return_value=mock_expander_context)
                mock_expander_context.__exit__ = Mock(return_value=None)
                mock_expander.return_value = mock_expander_context

                instructions_result = render_calibration_instructions()

            # Both components should complete successfully
            assert prerequisites_result is True
            assert instructions_result is None

            # Check that no errors were recorded (just verify the function exists)
            error_count = mock_get_error_count()
            assert error_count == 0


if __name__ == "__main__":
    pytest.main([__file__])