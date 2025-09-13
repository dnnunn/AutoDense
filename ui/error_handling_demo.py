"""Demonstration of AutoDense Unified Error Handling System.

This script demonstrates how to use the unified error handling system
across AutoDense UI components for consistent error reporting and user feedback.
"""

import streamlit as st
from utils.error_handling import (
    handle_ui_errors,
    safe_execute,
    with_error_boundary,
    show_error_summary,
    get_ui_error_count,
    clear_ui_error_log
)


def demo_error_handling():
    """Demonstrate various error handling patterns."""

    st.title("🛡️ AutoDense Error Handling Demo")

    st.markdown("""
    This page demonstrates the unified error handling system implemented across
    AutoDense UI components. The system provides:

    - **Consistent error reporting** with toast notifications
    - **Detailed error information** for debugging
    - **Error tracking and analytics**
    - **Graceful component degradation** when errors occur
    - **User-friendly error messages** with actionable feedback
    """)

    # Error summary section
    st.markdown("## 📊 Current Session Error Status")
    error_count = get_ui_error_count()

    if error_count == 0:
        st.success(f"✅ No errors recorded in this session")
    else:
        st.warning(f"⚠️ {error_count} errors recorded in this session")

    col1, col2 = st.columns(2)
    with col1:
        if st.button("🔄 Show Error Summary", help="Display detailed error log"):
            show_error_summary()

    with col2:
        if st.button("🧹 Clear Error Log", help="Reset error counter and log"):
            clear_ui_error_log()
            st.rerun()

    # Demonstration of different error handling patterns
    st.markdown("## 🧪 Error Handling Pattern Demos")

    # Pattern 1: Function decorator
    st.markdown("### 1. Function Decorator Pattern")
    st.code("""
    @handle_ui_errors("Demo Operation", show_details=True)
    def demo_function():
        # Function implementation that might fail
        pass
    """)

    @handle_ui_errors("Demo Function", show_details=True, return_value="Error occurred")
    def demo_function_success():
        return "Function executed successfully!"

    @handle_ui_errors("Demo Function", show_details=True, return_value="Error occurred")
    def demo_function_failure():
        raise ValueError("This is a demonstration error")

    col1, col2 = st.columns(2)
    with col1:
        if st.button("🟢 Test Successful Function", help="Test decorator with working function"):
            result = demo_function_success()
            st.info(f"Result: {result}")

    with col2:
        if st.button("🔴 Test Failing Function", help="Test decorator with failing function"):
            result = demo_function_failure()
            st.info(f"Result: {result}")

    # Pattern 2: Safe execution
    st.markdown("### 2. Safe Execution Pattern")
    st.code("""
    result = safe_execute(
        "Operation Name",
        lambda: risky_operation(),
        default_return="safe_default"
    )
    """)

    col1, col2 = st.columns(2)
    with col1:
        if st.button("🟢 Safe Execute (Success)", help="Test safe execution with working function"):
            result = safe_execute(
                "Safe Demo Operation",
                lambda: "Safe execution successful!",
                default_return="Operation failed safely"
            )
            st.info(f"Result: {result}")

    with col2:
        if st.button("🔴 Safe Execute (Failure)", help="Test safe execution with failing function"):
            result = safe_execute(
                "Safe Demo Operation",
                lambda: 1/0,  # This will fail
                default_return="Operation failed safely"
            )
            st.info(f"Result: {result}")

    # Pattern 3: Error boundary
    st.markdown("### 3. Error Boundary Pattern")
    st.code("""
    @with_error_boundary("Component Name")
    def render_component():
        # Component rendering code
        pass
    """)

    @with_error_boundary("Demo Component")
    def demo_component_success():
        st.success("✅ Component rendered successfully!")
        return "Component OK"

    @with_error_boundary("Demo Component")
    def demo_component_failure():
        raise RuntimeError("Component rendering failed")

    col1, col2 = st.columns(2)
    with col1:
        if st.button("🟢 Test Working Component", help="Test error boundary with working component"):
            demo_component_success()

    with col2:
        if st.button("🔴 Test Failing Component", help="Test error boundary with failing component"):
            demo_component_failure()

    # Best practices section
    st.markdown("## 📋 Best Practices")
    with st.expander("Error Handling Best Practices", expanded=False):
        st.markdown("""
        ### When to use each pattern:

        **Function Decorator (`@handle_ui_errors`)**
        - Use for individual functions that process user input
        - Good for file upload, form validation, data processing
        - Provides detailed error feedback to users

        **Safe Execute (`safe_execute`)**
        - Use for one-off operations where you need inline error handling
        - Good for checking session state, loading configurations
        - Allows custom default return values

        **Error Boundary (`@with_error_boundary`)**
        - Use for entire component render functions
        - Prevents single component failures from breaking the entire UI
        - Provides consistent fallback UI for component errors

        ### Error Handling Guidelines:

        1. **Always provide user feedback** - Use toast notifications for immediate feedback
        2. **Include actionable information** - Tell users what they can do to fix the issue
        3. **Log for debugging** - All errors are automatically logged for analysis
        4. **Fail gracefully** - Provide sensible defaults when operations fail
        5. **Test error paths** - Include error scenarios in your testing
        """)


if __name__ == "__main__":
    demo_error_handling()