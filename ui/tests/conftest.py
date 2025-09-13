"""Shared pytest fixtures for AutoDense UI component testing.

This module provides comprehensive fixtures for mocking Streamlit dependencies
and creating test data for component testing.
"""

import pytest
from unittest.mock import Mock, MagicMock, patch
import numpy as np
from PIL import Image
import io
from typing import Dict, Any, Optional, Callable


class HybridSessionStateMock:
    """Mock that supports both dict-like and object-like access for session state.

    This class bridges the gap between dict-like access ('key' in state) and
    object-like access (state.key) that Streamlit session state supports.
    """

    def __init__(self, initial_data=None):
        # Use object.__setattr__ to avoid recursion with our custom __setattr__
        object.__setattr__(self, '_data', initial_data or {})

    # Dict-like interface support
    def __contains__(self, key):
        """Support 'key' in state syntax."""
        return key in self._data

    def get(self, key, default=None):
        """Support state.get('key', default) syntax."""
        return self._data.get(key, default)

    def __getitem__(self, key):
        """Support state['key'] syntax."""
        return self._data[key]

    def __setitem__(self, key, value):
        """Support state['key'] = value syntax."""
        self._data[key] = value

    # Object-like interface support
    def __getattr__(self, key):
        """Support state.key syntax."""
        if key.startswith('_'):  # Internal attributes
            raise AttributeError(f"'{type(self).__name__}' object has no attribute '{key}'")
        return self._data.get(key)

    def __setattr__(self, key, value):
        """Support state.key = value syntax."""
        if key.startswith('_'):  # Internal attributes like _data
            object.__setattr__(self, key, value)
        else:
            self._data[key] = value

    def __delattr__(self, key):
        """Support del state.key syntax."""
        if key.startswith('_'):
            object.__delattr__(self, key)
        else:
            del self._data[key]

    # Additional dict-like methods that might be needed
    def keys(self):
        """Return keys like a dict."""
        return self._data.keys()

    def values(self):
        """Return values like a dict."""
        return self._data.values()

    def items(self):
        """Return items like a dict."""
        return self._data.items()

    def update(self, other):
        """Update like a dict."""
        self._data.update(other)

    def clear(self):
        """Clear like a dict."""
        self._data.clear()


@pytest.fixture
def mock_session_state():
    """Mock Streamlit session state for component testing.

    Returns a hybrid mock object that behaves like st.session_state with
    both dict-like ('key' in state) and object-like (state.key) access patterns.
    """
    # Initialize common AutoDense session state keys with defaults
    initial_data = {
        'res_uploaded_image': None,
        'res_uploaded_array': None,
        'res_image_metadata': None,
        'res_image_bytes': None,
        'res_lane_boundaries': None,
        'params_gel_type': "sds_page",
        'ui_last_action': None,
        'ui_error_count': 0
    }

    return HybridSessionStateMock(initial_data)


@pytest.fixture
def mock_streamlit():
    """Mock all Streamlit UI functions for isolated component testing.

    Returns a mock object with all necessary Streamlit functions
    that components use for rendering and interaction.
    """
    with patch('streamlit.markdown') as mock_markdown, \
         patch('streamlit.columns') as mock_columns, \
         patch('streamlit.success') as mock_success, \
         patch('streamlit.error') as mock_error, \
         patch('streamlit.info') as mock_info, \
         patch('streamlit.warning') as mock_warning, \
         patch('streamlit.caption') as mock_caption, \
         patch('streamlit.button') as mock_button, \
         patch('streamlit.expander') as mock_expander, \
         patch('streamlit.file_uploader') as mock_file_uploader, \
         patch('streamlit.metric') as mock_metric, \
         patch('streamlit.write') as mock_write, \
         patch('streamlit.toast') as mock_toast:

        # Configure column mock to return context managers
        col1_mock = MagicMock()
        col2_mock = MagicMock()
        col3_mock = MagicMock()
        col1_mock.__enter__ = Mock(return_value=col1_mock)
        col1_mock.__exit__ = Mock(return_value=None)
        col2_mock.__enter__ = Mock(return_value=col2_mock)
        col2_mock.__exit__ = Mock(return_value=None)
        col3_mock.__enter__ = Mock(return_value=col3_mock)
        col3_mock.__exit__ = Mock(return_value=None)

        # Make columns return appropriate number based on call
        def columns_side_effect(*args, **kwargs):
            if args and isinstance(args[0], list) and len(args[0]) == 2:
                return [col1_mock, col2_mock]  # For [2, 1] calls
            elif args and args[0] == 2:
                return [col1_mock, col2_mock]  # For st.columns(2) calls
            elif args and args[0] == 3:
                return [col1_mock, col2_mock, col3_mock]  # For st.columns(3) calls
            else:
                return [col1_mock, col2_mock]  # Default

        mock_columns.side_effect = columns_side_effect

        # Configure expander mock to return context manager
        expander_mock = MagicMock()
        expander_mock.__enter__ = Mock(return_value=expander_mock)
        expander_mock.__exit__ = Mock(return_value=None)
        mock_expander.return_value = expander_mock

        # Configure button to return False by default (not clicked)
        mock_button.return_value = False

        # Configure file_uploader to return None by default (no file)
        mock_file_uploader.return_value = None

        yield {
            'markdown': mock_markdown,
            'columns': mock_columns,
            'success': mock_success,
            'error': mock_error,
            'info': mock_info,
            'warning': mock_warning,
            'caption': mock_caption,
            'button': mock_button,
            'expander': mock_expander,
            'file_uploader': mock_file_uploader,
            'metric': mock_metric,
            'write': mock_write,
            'toast': mock_toast,
            'col1': col1_mock,
            'col2': col2_mock,
            'col3': col3_mock,
            'expander_context': expander_mock
        }


@pytest.fixture
def sample_image_metadata():
    """Sample image metadata for testing image upload component."""
    return {
        'filename': 'test_gel.jpg',
        'size_bytes': 1024 * 100,  # 100KB
        'original_dimensions': (2000, 1500),
        'standardized_dimensions': (1024, 768),
        'dimensions': (1024, 768),  # For backward compatibility
        'format': 'JPEG',
        'mode': 'RGB',
        'hash': 'abcd1234',
        'standardized': True
    }


@pytest.fixture
def sample_image():
    """Create a sample PIL Image for testing."""
    # Create a simple 100x100 RGB image with some pattern
    image = Image.new('RGB', (100, 100), color='white')
    # Add some simple pattern for testing
    pixels = image.load()
    for i in range(100):
        for j in range(100):
            if (i + j) % 20 < 10:
                pixels[i, j] = (100, 100, 100)  # Gray stripes
    return image


@pytest.fixture
def sample_image_array(sample_image):
    """Create a sample numpy array from PIL Image for testing."""
    return np.array(sample_image)


@pytest.fixture
def sample_uploaded_file():
    """Mock uploaded file object for testing file upload."""
    mock_file = Mock()
    mock_file.name = 'test_gel.jpg'
    mock_file.size = 1024 * 100  # 100KB

    # Create some sample image bytes
    img = Image.new('RGB', (100, 100), color='red')
    buf = io.BytesIO()
    img.save(buf, format='JPEG')
    file_bytes = buf.getvalue()

    mock_file.getvalue.return_value = file_bytes
    return mock_file


@pytest.fixture
def mock_goto_tab():
    """Mock navigation function for testing."""
    return Mock()


@pytest.fixture
def prerequisite_states():
    """Various prerequisite states for testing prerequisites panel."""

    def create_mock_state(state_dict):
        return HybridSessionStateMock(state_dict)

    return {
        'no_prerequisites': create_mock_state({
            'res_uploaded_image': None,
            'res_lane_boundaries': None,
            'res_image_metadata': None,
            'params_gel_type': 'sds_page'
        }),
        'image_only': create_mock_state({
            'res_uploaded_image': Mock(),  # Mock PIL Image
            'res_lane_boundaries': None,
            'res_image_metadata': {
                'filename': 'test.jpg',
                'dimensions': (1024, 768)
            },
            'params_gel_type': 'sds_page'
        }),
        'calibration_only': create_mock_state({
            'res_uploaded_image': None,
            'res_lane_boundaries': [{'x0': 100, 'x1': 200}, {'x0': 300, 'x1': 400}],
            'res_image_metadata': None,
            'params_gel_type': 'sds_page'
        }),
        'both_prerequisites': create_mock_state({
            'res_uploaded_image': Mock(),  # Mock PIL Image
            'res_lane_boundaries': [{'x0': 100, 'x1': 200}, {'x0': 300, 'x1': 400}],
            'res_image_metadata': {
                'filename': 'test.jpg',
                'dimensions': (1024, 768)
            },
            'params_gel_type': 'sds_page'
        })
    }


@pytest.fixture
def mock_standardize_image():
    """Mock the standardize_image_size function for testing."""
    with patch('components.image_upload.standardize_image_size') as mock:
        # By default, return the input image unchanged
        mock.side_effect = lambda img: img
        yield mock


@pytest.fixture
def mock_handle_errors():
    """Mock the handle_errors decorator for testing."""
    def mock_decorator(operation_name: str):
        def decorator(func):
            # Return the function unchanged for testing
            return func
        return decorator

    with patch('components.image_upload.handle_errors', side_effect=mock_decorator):
        yield


@pytest.fixture(autouse=True)
def reset_session_state():
    """Reset any global state between tests."""
    # This fixture runs automatically before each test
    # Add any cleanup code here if needed
    yield
    # Cleanup after test if needed


@pytest.fixture
def large_file_mock():
    """Mock for testing large file upload scenarios."""
    mock_file = Mock()
    mock_file.name = 'large_image.jpg'
    mock_file.size = 60 * 1024 * 1024  # 60MB - exceeds default 50MB limit
    mock_file.getvalue.return_value = b'x' * (60 * 1024 * 1024)
    return mock_file


@pytest.fixture
def invalid_image_mock():
    """Mock for testing invalid image file scenarios."""
    mock_file = Mock()
    mock_file.name = 'not_an_image.txt'
    mock_file.size = 1000
    mock_file.getvalue.return_value = b'This is not image data'
    return mock_file


@pytest.fixture
def session_state_with_existing_image(sample_image, sample_image_metadata):
    """Session state with an existing image loaded."""
    initial_data = {
        'res_uploaded_image': sample_image,
        'res_uploaded_array': np.array(sample_image),
        'res_image_metadata': sample_image_metadata,
        'res_image_bytes': b'existing_image_bytes',
        'res_lane_boundaries': None,
        'params_gel_type': "sds_page",
        'ui_last_action': None,
        'ui_error_count': 0
    }
    return HybridSessionStateMock(initial_data)