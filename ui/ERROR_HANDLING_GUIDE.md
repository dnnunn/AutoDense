# AutoDense Unified Error Handling System

> **Doc Meta**
> - **Purpose:** Comprehensive guide for using the unified error handling system across AutoDense UI components
> - **Scope:** Error handling patterns, best practices, and implementation guidelines for AutoDense UI development
> - **Owner:** @claude-code
> - **Last-verified:** 2025-01-13

## Overview

The AutoDense unified error handling system provides consistent error reporting, user feedback, and debugging capabilities across all UI components. This system replaces ad-hoc error handling with a standardized approach that ensures:

- **Consistent user experience** with toast notifications and detailed error information
- **Comprehensive error logging** for debugging and analytics
- **Graceful degradation** when components fail
- **Developer-friendly debugging** with detailed tracebacks and error context

## Architecture

### Core Components

1. **`UIErrorHandler`** - Central error handling class that manages error counting, logging, and user feedback
2. **Decorators** - Function decorators for different error handling patterns
3. **Utility Functions** - Helper functions for safe execution and error reporting
4. **Session State Integration** - Error tracking persisted in Streamlit session state

### Error Handling Patterns

The system provides three main patterns for error handling:

#### 1. Function Decorator Pattern

Use `@handle_ui_errors` for individual functions that might fail:

```python
from utils.error_handling import handle_ui_errors

@handle_ui_errors("File Upload", show_details=True, return_value=None)
def upload_file(uploaded_file):
    # File processing logic that might fail
    if not uploaded_file:
        raise ValueError("No file provided")
    return process_file(uploaded_file)
```

**When to use:**
- File upload and processing functions
- Form validation and submission
- Data transformation operations
- Any function that processes user input

#### 2. Safe Execution Pattern

Use `safe_execute` for inline error handling without decorators:

```python
from utils.error_handling import safe_execute

# Check session state safely
has_image = safe_execute(
    "Image Status Check",
    lambda: st.session_state.uploaded_image is not None,
    default_return=False,
    show_error=False
)

# Load configuration with fallback
config = safe_execute(
    "Configuration Load",
    lambda: load_user_config(),
    default_return=default_config,
    show_error=True
)
```

**When to use:**
- Session state access that might fail
- Configuration loading with defaults
- Quick validation checks
- One-off operations where decorators are overkill

#### 3. Error Boundary Pattern

Use `@with_error_boundary` for component render functions:

```python
from utils.error_handling import with_error_boundary

@with_error_boundary("Image Upload Component")
def render_image_upload():
    # Component rendering logic
    st.file_uploader("Upload image...")
    # If this fails, show error boundary UI instead of crashing
```

**When to use:**
- Main component render functions
- Complex UI components with multiple dependencies
- Components that might fail due to session state issues
- Any component where failure should not break the entire page

## Implementation Guide

### Setting Up Error Handling in New Components

1. **Import the error handling utilities:**
```python
from utils.error_handling import handle_ui_errors, safe_execute, with_error_boundary
```

2. **Wrap your main render function with error boundary:**
```python
@with_error_boundary("Your Component Name")
def render_your_component():
    # Component implementation
    pass
```

3. **Use decorators for functions that might fail:**
```python
@handle_ui_errors("Data Processing", show_details=True)
def process_data(data):
    # Processing logic
    pass
```

4. **Use safe execution for session state access:**
```python
def render_component():
    # Safe session state access
    user_data = safe_execute(
        "User Data Access",
        lambda: st.session_state.user_data,
        default_return={},
        show_error=False
    )
```

### Error Handling Parameters

#### `@handle_ui_errors` Parameters

- **`operation_name`** (required): Name shown in error messages
- **`show_details`** (default: `True`): Whether to show detailed error information
- **`show_traceback`** (default: `False`): Whether to show Python traceback
- **`return_value`** (default: `None`): Value to return when error occurs

#### `safe_execute` Parameters

- **`operation_name`** (required): Name for error reporting
- **`func`** (required): Function to execute safely
- **`default_return`** (default: `None`): Value to return on error
- **`show_error`** (default: `True`): Whether to show error to user

#### `@with_error_boundary` Parameters

- **`component_name`** (required): Component name for error reporting
- **`show_traceback`** (default: `False`): Whether to show traceback in error UI

### Error Logging and Analytics

All errors are automatically logged with:

- Operation name and error details
- Error type and message
- Full Python traceback
- Error count for the session
- Timestamp information

Access error information programmatically:

```python
from utils.error_handling import get_ui_error_count, get_ui_error_log, clear_ui_error_log

# Get current error count
error_count = get_ui_error_count()

# Get detailed error log
error_log = get_ui_error_log()

# Clear errors (useful for testing)
clear_ui_error_log()
```

### Displaying Error Summary

Use the built-in error summary component:

```python
from utils.error_handling import show_error_summary

# Show error summary in your UI
show_error_summary()
```

## Migration Guide

### Migrating from Old Error Handling

If you have existing components with custom error handling:

1. **Replace custom error decorators** with `@handle_ui_errors`
2. **Remove manual toast/expander error display code** - it's handled automatically
3. **Update imports** to use the unified system
4. **Test error paths** to ensure consistent behavior

### Example Migration

**Before:**
```python
def handle_errors(operation_name: str):
    def decorator(func):
        def wrapper(*args, **kwargs):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                st.toast(f"{operation_name} failed", icon="❌")
                st.error(f"Error: {str(e)}")
                return None
        return wrapper
    return decorator

@handle_errors("File Upload")
def upload_file(file):
    # Implementation
    pass
```

**After:**
```python
from utils.error_handling import handle_ui_errors

@handle_ui_errors("File Upload", show_details=True)
def upload_file(file):
    # Implementation (unchanged)
    pass
```

## Best Practices

### Error Message Guidelines

1. **Use descriptive operation names** - "Image Upload" vs "Process"
2. **Be specific about what failed** - Include context in error messages
3. **Provide actionable feedback** - Tell users what they can do to fix issues
4. **Maintain consistent tone** - Professional but helpful

### Performance Considerations

1. **Use `show_error=False`** for frequent operations (like session state checks)
2. **Avoid deep nesting** of error-handled functions
3. **Consider caching** for expensive operations with error handling
4. **Test error paths** to ensure they don't create performance bottlenecks

### Testing Error Handling

1. **Test both success and failure paths**
2. **Verify error messages are helpful**
3. **Check that UI degrades gracefully**
4. **Ensure error logs are populated correctly**

Example test:
```python
def test_component_error_handling():
    # Test successful operation
    result = render_component()
    assert result is not None

    # Test error path
    with patch('session_state', side_effect=Exception("Test error")):
        result = render_component()
        # Should handle error gracefully
        assert get_ui_error_count() > 0
```

### Common Anti-Patterns

❌ **Don't:**
- Catch exceptions and fail silently without logging
- Show generic error messages without context
- Use error handling for control flow
- Ignore error return values

✅ **Do:**
- Log all errors for debugging
- Provide specific, actionable error messages
- Return sensible defaults when operations fail
- Test error paths as thoroughly as success paths

## Troubleshooting

### Common Issues

1. **Import errors** - Ensure `utils.error_handling` is in your Python path
2. **Session state conflicts** - Clear error log if testing multiple scenarios
3. **Streamlit rerun issues** - Use `st.rerun()` after clearing errors
4. **Missing error details** - Check that `show_details=True` is set

### Debugging Tips

1. **Check error count** - Use `get_ui_error_count()` to verify errors are being tracked
2. **Review error log** - Use `get_ui_error_log()` to see detailed error information
3. **Test with traceback** - Set `show_traceback=True` during development
4. **Use error demo** - Run `error_handling_demo.py` to test the system

### Support

For issues with the error handling system:

1. Check this documentation for usage patterns
2. Review the demo script for examples
3. Look at existing component implementations
4. Check the test files for expected behavior

## API Reference

### Function Decorators

#### `@handle_ui_errors(operation_name, show_details=True, show_traceback=False, return_value=None)`
Decorator for consistent UI error handling.

#### `@with_error_boundary(component_name, show_traceback=False)`
Higher-order component wrapper with error boundary functionality.

### Utility Functions

#### `safe_execute(operation_name, func, default_return=None, show_error=True)`
Safely execute a function with error handling.

#### `get_ui_error_count() -> int`
Get the current UI error count.

#### `get_ui_error_log() -> list`
Get the complete UI error log.

#### `clear_ui_error_log()`
Clear error log and reset counter.

#### `show_error_summary()`
Display error summary component in Streamlit UI.

### Legacy Compatibility

#### `handle_errors(operation_name)`
Legacy compatibility function for old error handling decorator pattern.

---

*This documentation is maintained as part of the AutoDense project. Last updated: January 13, 2025*