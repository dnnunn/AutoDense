"""Tests for the unified error handling system."""

import pytest
import streamlit as st
from unittest.mock import Mock, patch
import sys
import os

# Add the ui directory to the path so we can import our modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from utils.error_handling import (
    handle_ui_errors,
    safe_execute,
    with_error_boundary,
    get_ui_error_count,
    get_ui_error_log,
    clear_ui_error_log,
    UIErrorHandler
)


class TestUIErrorHandler:
    """Test the UIErrorHandler class."""

    def test_error_handler_initialization(self, mock_session_state):
        """Test that error handler initializes session state correctly."""
        with patch('streamlit.session_state', mock_session_state):
            handler = UIErrorHandler()
            handler._initialize_error_tracking()

            assert st.session_state.get('ui_error_count') == 0
            assert st.session_state.get('ui_error_log') == []

    def test_increment_error_count(self, mock_session_state):
        """Test error count increment functionality."""
        with patch('streamlit.session_state', mock_session_state):
            handler = UIErrorHandler()

            count1 = handler._increment_error_count()
            assert count1 == 1

            count2 = handler._increment_error_count()
            assert count2 == 2

            assert st.session_state.get('ui_error_count') == 2

    @patch('utils.error_handling.logger')
    def test_log_error(self, mock_logger, mock_session_state):
        """Test error logging functionality."""
        with patch('streamlit.session_state', mock_session_state):
            handler = UIErrorHandler()
            handler._initialize_error_tracking()  # Make sure error log is initialized
            test_error = ValueError("Test error message")

            handler._log_error("Test Operation", test_error, 1)

            error_log = st.session_state.get('ui_error_log', [])
            assert len(error_log) == 1

            log_entry = error_log[0]
            assert log_entry['operation'] == "Test Operation"
            assert log_entry['error_type'] == "ValueError"
            assert log_entry['error_message'] == "Test error message"
            assert log_entry['error_count'] == 1
            assert 'traceback' in log_entry

            # Check that logger was called
            mock_logger.error.assert_called_once()


class TestErrorDecorators:
    """Test error handling decorators."""

    @patch('streamlit.toast')
    @patch('streamlit.expander')
    def test_handle_ui_errors_success(self, mock_expander, mock_toast, mock_session_state):
        """Test that successful functions work normally with decorator."""
        with patch('streamlit.session_state', mock_session_state):
            @handle_ui_errors("Test Operation")
            def successful_function(x, y):
                return x + y

            result = successful_function(2, 3)
            assert result == 5

            # No error handling should be triggered
            mock_toast.assert_not_called()
            mock_expander.assert_not_called()

    @patch('streamlit.toast')
    @patch('streamlit.expander')
    def test_handle_ui_errors_failure(self, mock_expander, mock_toast, mock_session_state):
        """Test that errors are handled correctly by decorator."""
        with patch('streamlit.session_state', mock_session_state):
            @handle_ui_errors("Test Operation", return_value="error_return")
            def failing_function():
                raise ValueError("Test error")

            result = failing_function()
            assert result == "error_return"

            # Error handling should be triggered
            mock_toast.assert_called_once_with("Test Operation failed", icon="❌")
            assert st.session_state.get('ui_error_count', 0) > 0

    def test_safe_execute_success(self, mock_session_state):
        """Test safe_execute with successful function."""
        with patch('streamlit.session_state', mock_session_state):
            result = safe_execute(
                "Test Operation",
                lambda: 2 + 3,
                default_return=0,
                show_error=False
            )
            assert result == 5

    @patch('streamlit.toast')
    def test_safe_execute_failure(self, mock_toast, mock_session_state):
        """Test safe_execute with failing function."""
        with patch('streamlit.session_state', mock_session_state):
            result = safe_execute(
                "Test Operation",
                lambda: 1 / 0,
                default_return="error_occurred",
                show_error=True
            )
            assert result == "error_occurred"
            assert st.session_state.get('ui_error_count', 0) > 0


class TestErrorHandlingUtilities:
    """Test utility functions for error handling."""

    def test_get_error_count_empty(self, mock_session_state):
        """Test getting error count when no errors occurred."""
        with patch('streamlit.session_state', mock_session_state):
            mock_session_state['ui_error_count'] = 0
            mock_session_state['ui_error_log'] = []

            count = get_ui_error_count()
            assert count == 0

    def test_get_error_count_with_errors(self, mock_session_state):
        """Test getting error count when errors occurred."""
        with patch('streamlit.session_state', mock_session_state):
            mock_session_state['ui_error_count'] = 5
            mock_session_state['ui_error_log'] = []

            count = get_ui_error_count()
            assert count == 5

    def test_get_error_log(self, mock_session_state):
        """Test getting error log."""
        with patch('streamlit.session_state', mock_session_state):
            test_errors = ['error1', 'error2']
            mock_session_state['ui_error_count'] = 2
            mock_session_state['ui_error_log'] = test_errors

            error_log = get_ui_error_log()
            assert len(error_log) == 2
            assert error_log == test_errors

    def test_clear_error_log(self, mock_session_state):
        """Test clearing error log."""
        with patch('streamlit.session_state', mock_session_state):
            mock_session_state['ui_error_count'] = 10
            mock_session_state['ui_error_log'] = ['many', 'errors']

            clear_ui_error_log()

            assert st.session_state.get('ui_error_count') == 0
            assert st.session_state.get('ui_error_log') == []


class TestErrorBoundary:
    """Test error boundary decorator."""

    @patch('streamlit.error')
    @patch('streamlit.expander')
    @patch('streamlit.markdown')
    @patch('streamlit.info')
    @patch('streamlit.code')
    def test_error_boundary_with_failure(self, mock_code, mock_info, mock_markdown, mock_expander, mock_error, mock_session_state):
        """Test error boundary handles component failures."""
        # Setup expander mock to be a context manager
        expander_context = Mock()
        expander_context.__enter__ = Mock(return_value=expander_context)
        expander_context.__exit__ = Mock(return_value=None)
        mock_expander.return_value = expander_context

        with patch('streamlit.session_state', mock_session_state):
            @with_error_boundary("Test Component")
            def failing_component():
                raise RuntimeError("Component failed to render")

            result = failing_component()
            assert result is None

            # Check that error UI was displayed
            mock_error.assert_called_once_with("❌ Test Component failed to render")
            assert st.session_state.get('ui_error_count', 0) > 0

    @patch('streamlit.error')
    def test_error_boundary_with_success(self, mock_error, mock_session_state):
        """Test error boundary allows successful components to work."""
        with patch('streamlit.session_state', mock_session_state):
            @with_error_boundary("Test Component")
            def working_component():
                return "Component rendered successfully"

            result = working_component()
            assert result == "Component rendered successfully"

            # No error handling should be triggered
            mock_error.assert_not_called()


if __name__ == "__main__":
    pytest.main([__file__])