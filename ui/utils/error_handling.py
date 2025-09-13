"""Unified Error Handling System for AutoDense UI Components.

This module provides standardized error handling decorators and utilities
for consistent error reporting, user feedback, and debugging across all
UI components.
"""

import logging
import traceback
from functools import wraps
from typing import Callable, Optional, Any, Dict, Union
import streamlit as st

from autodense_types import ErrorCallback

# Configure logger for UI error handling
logger = logging.getLogger(__name__)


class UIErrorHandler:
    """Centralized error handler for AutoDense UI components."""

    def __init__(self):
        self._error_count_key = 'ui_error_count'
        self._error_log_key = 'ui_error_log'

    def _initialize_error_tracking(self) -> None:
        """Initialize error tracking in session state if not present."""
        if self._error_count_key not in st.session_state:
            st.session_state[self._error_count_key] = 0
        if self._error_log_key not in st.session_state:
            st.session_state[self._error_log_key] = []

    def _increment_error_count(self) -> int:
        """Increment error counter and return new count."""
        self._initialize_error_tracking()
        st.session_state[self._error_count_key] += 1
        return st.session_state[self._error_count_key]

    def _log_error(self, operation_name: str, error: Exception, error_count: int) -> None:
        """Log error details for debugging and analytics."""
        error_entry = {
            'operation': operation_name,
            'error_type': type(error).__name__,
            'error_message': str(error),
            'error_count': error_count,
            'traceback': traceback.format_exc()
        }

        # Add to session state log
        st.session_state[self._error_log_key].append(error_entry)

        # Log to Python logger
        logger.error(
            f"UI Error #{error_count} in {operation_name}: {type(error).__name__}: {str(error)}",
            exc_info=True
        )

    def _show_user_feedback(self, operation_name: str, error: Exception, error_count: int,
                           show_details: bool = True, show_traceback: bool = False) -> None:
        """Display user-friendly error feedback with optional technical details."""
        # Show toast notification
        st.toast(f"{operation_name} failed", icon="❌")

        # Show detailed error information if requested
        if show_details:
            with st.expander(f"🔍 {operation_name} Error Details", expanded=True):
                st.markdown(f"""
                <div class="error-details">
                <strong>Operation:</strong> {operation_name}<br>
                <strong>Error Type:</strong> {type(error).__name__}<br>
                <strong>Error Message:</strong> {str(error)}<br>
                <strong>Error #{error_count}</strong>
                </div>
                """, unsafe_allow_html=True)

                if show_traceback:
                    st.code(traceback.format_exc(), language="python")

    def handle_error(self, operation_name: str, show_details: bool = True,
                    show_traceback: bool = False, return_value: Any = None) -> Callable:
        """
        Decorator for consistent error handling with user feedback.

        Args:
            operation_name: Name of the operation for error reporting
            show_details: Whether to show detailed error information
            show_traceback: Whether to show full Python traceback
            return_value: Value to return when an error occurs (default: None)

        Returns:
            Decorator function that wraps the target function with error handling
        """
        def decorator(func: Callable[..., Any]) -> Callable[..., Any]:
            @wraps(func)
            def wrapper(*args: Any, **kwargs: Any) -> Any:
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    error_count = self._increment_error_count()
                    self._log_error(operation_name, e, error_count)
                    self._show_user_feedback(operation_name, e, error_count, show_details, show_traceback)
                    return return_value
            return wrapper
        return decorator

    def get_error_count(self) -> int:
        """Get current error count from session state."""
        self._initialize_error_tracking()
        return st.session_state[self._error_count_key]

    def get_error_log(self) -> list:
        """Get error log from session state."""
        self._initialize_error_tracking()
        return st.session_state[self._error_log_key]

    def clear_error_log(self) -> None:
        """Clear error log and reset counter."""
        st.session_state[self._error_count_key] = 0
        st.session_state[self._error_log_key] = []
        logger.info("UI error log cleared")


# Global error handler instance
_error_handler = UIErrorHandler()


def handle_ui_errors(operation_name: str, show_details: bool = True,
                    show_traceback: bool = False, return_value: Any = None) -> Callable:
    """
    Decorator for consistent UI error handling across AutoDense components.

    Args:
        operation_name: Name of the operation for error reporting
        show_details: Whether to show detailed error information to user
        show_traceback: Whether to show full Python traceback (for debugging)
        return_value: Value to return when an error occurs (default: None)

    Returns:
        Decorator function that wraps the target function with error handling

    Example:
        @handle_ui_errors("Image Upload", show_details=True)
        def upload_image(file):
            # Function implementation
            pass
    """
    return _error_handler.handle_error(operation_name, show_details, show_traceback, return_value)


def get_ui_error_count() -> int:
    """Get the current UI error count."""
    return _error_handler.get_error_count()


def get_ui_error_log() -> list:
    """Get the complete UI error log."""
    return _error_handler.get_error_log()


def clear_ui_error_log() -> None:
    """Clear the UI error log and reset error counter."""
    _error_handler.clear_error_log()


def show_error_summary() -> None:
    """Display a summary of UI errors for debugging purposes."""
    error_count = get_ui_error_count()
    error_log = get_ui_error_log()

    if error_count == 0:
        st.success("✅ No UI errors recorded in this session")
        return

    st.warning(f"⚠️ {error_count} UI errors recorded in this session")

    with st.expander(f"🐛 Error Summary ({error_count} errors)", expanded=False):
        for i, error_entry in enumerate(error_log, 1):
            st.markdown(f"""
            **Error {i}:** {error_entry['operation']}
            - **Type:** {error_entry['error_type']}
            - **Message:** {error_entry['error_message']}
            """)

            with st.expander(f"Traceback for Error {i}", expanded=False):
                st.code(error_entry['traceback'], language="python")


def safe_execute(operation_name: str, func: Callable[[], Any],
                default_return: Any = None, show_error: bool = True) -> Any:
    """
    Safely execute a function with error handling outside of decorator context.

    Args:
        operation_name: Name of the operation for error reporting
        func: Function to execute safely
        default_return: Value to return if function fails
        show_error: Whether to show error details to user

    Returns:
        Function result on success, default_return on error

    Example:
        result = safe_execute("Load Configuration", lambda: load_config(), {})
    """
    try:
        return func()
    except Exception as e:
        error_count = _error_handler._increment_error_count()
        _error_handler._log_error(operation_name, e, error_count)
        if show_error:
            _error_handler._show_user_feedback(operation_name, e, error_count)
        return default_return


def with_error_boundary(component_name: str, show_traceback: bool = False) -> Callable:
    """
    Higher-order component wrapper that adds error boundary functionality.

    Args:
        component_name: Name of the component for error reporting
        show_traceback: Whether to show traceback in error details

    Returns:
        Decorator that wraps component functions with error boundaries

    Example:
        @with_error_boundary("Image Upload Component")
        def render_image_upload():
            # Component implementation
            pass
    """
    def decorator(render_func: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(render_func)
        def wrapper(*args: Any, **kwargs: Any) -> Any:
            try:
                return render_func(*args, **kwargs)
            except Exception as e:
                error_count = _error_handler._increment_error_count()
                _error_handler._log_error(f"{component_name} Render", e, error_count)

                # Show error boundary UI
                st.error(f"❌ {component_name} failed to render")

                with st.expander(f"🔍 {component_name} Error Details", expanded=True):
                    st.markdown(f"""
                    <div class="error-details">
                    <strong>Component:</strong> {component_name}<br>
                    <strong>Error Type:</strong> {type(e).__name__}<br>
                    <strong>Error Message:</strong> {str(e)}<br>
                    <strong>Error #{error_count}</strong>
                    </div>
                    """, unsafe_allow_html=True)

                    if show_traceback:
                        st.code(traceback.format_exc(), language="python")

                    st.info("💡 Try refreshing the page or check the Prerequisites tab")

                return None
        return wrapper
    return decorator


# Compatibility functions for backward compatibility with existing code
def handle_errors(operation_name: str) -> Callable:
    """
    Legacy compatibility function for the old handle_errors decorator.

    This function maintains backward compatibility with existing code
    while providing the new unified error handling functionality.

    Args:
        operation_name: Name of the operation for error reporting

    Returns:
        Decorator function compatible with the old interface
    """
    return handle_ui_errors(operation_name, show_details=True, show_traceback=False, return_value=None)