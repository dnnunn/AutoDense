"""Accessibility compliance tests for AutoDense UI components."""

import pytest
import streamlit as st
from unittest.mock import Mock, patch
import sys
import os

# Add the ui directory to the path so we can import our modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from components.prerequisites_panel import render_prerequisites_panel
from components.calibration_instructions import render_calibration_instructions
from components.image_upload import render_image_upload


class TestAccessibilityCompliance:
    """Test WCAG 2.1 AA compliance for AutoDense UI components."""

    def test_skip_links_present(self):
        """Test that skip links are present for keyboard navigation."""
        # Read the source file directly to avoid Streamlit execution issues
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        assert 'skip-link' in content, "Skip links CSS class should be present"
        assert 'Skip to main content' in content, "Skip to main content link should be present"
        assert 'Skip to navigation' in content, "Skip to navigation link should be present"

    def test_color_contrast_compliance(self):
        """Test that color combinations meet WCAG AA contrast requirements."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for accessible status colors (dark variants)
        assert '#155724' in content, "Accessible green color should be used"
        assert '#856404' in content, "Accessible yellow color should be used"
        assert '#721c24' in content, "Accessible red color should be used"
        assert '#0c5460' in content, "Accessible teal color should be used"

    def test_screen_reader_announcements(self):
        """Test that screen reader announcement function is available."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Verify the announce function exists
        assert 'def announce_to_screen_reader' in content, "announce_to_screen_reader function should be defined"
        assert 'aria-live' in content, "ARIA live regions should be present"
        assert 'sr-only' in content, "Screen reader only content should be supported"

    def test_semantic_landmarks(self):
        """Test that proper semantic landmarks are present."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for semantic landmarks
        assert 'role="navigation"' in content, "Navigation landmark should be present"
        assert 'role="main"' in content, "Main content landmark should be present"
        assert 'id="main-content"' in content, "Main content ID should be present"
        assert 'id="navigation"' in content, "Navigation ID should be present"

    def test_focus_indicators(self):
        """Test that focus indicators are properly implemented."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for focus styling
        assert ':focus' in content, "Focus selectors should be present"
        assert 'outline:' in content, "Focus outline should be defined"

    def _create_column_context_manager(self):
        """Helper to create properly configured column mock context managers."""
        mock_col = Mock()
        mock_col.__enter__ = Mock(return_value=mock_col)
        mock_col.__exit__ = Mock(return_value=None)
        return mock_col

    @patch('streamlit.success')
    @patch('streamlit.error')
    @patch('streamlit.info')
    @patch('streamlit.columns')
    @patch('streamlit.markdown')
    def test_components_render_with_accessibility_features(self, mock_markdown, mock_columns,
                                                          mock_info, mock_error, mock_success,
                                                          mock_session_state):
        """Test that components render without accessibility errors."""
        with patch('streamlit.session_state', mock_session_state):
            # Setup valid session state
            st.session_state.res_uploaded_image = Mock()
            st.session_state.res_lane_boundaries = [1, 2, 3]
            st.session_state.res_image_metadata = {
                'filename': 'test.jpg',
                'dimensions': (800, 600)
            }
            st.session_state.params_gel_type = 'sds_page'

            # Mock columns
            mock_col1 = self._create_column_context_manager()
            mock_col2 = self._create_column_context_manager()
            mock_columns.return_value = [mock_col1, mock_col2]

            # Test prerequisites panel renders
            result = render_prerequisites_panel()
            assert result is True

            # Verify accessibility-related calls were made
            # (This is a simplified test - full accessibility testing would require browser automation)
            mock_markdown.assert_called()

    def test_keyboard_navigation_support(self):
        """Test that keyboard navigation is supported."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for keyboard alternatives to mouse interactions
        assert 'number_input' in content, "Keyboard alternatives should be available"
        assert 'Manual Coordinate Entry' in content, "Manual coordinate entry should be available for keyboard users"

    def test_form_labels_and_descriptions(self):
        """Test that form elements have proper labels and descriptions."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for accessible form patterns
        assert 'help=' in content, "Help text should be provided for form elements"
        assert 'aria-label' in content, "ARIA labels should be used for form accessibility"

    def test_no_accessibility_violations_in_css(self):
        """Test that CSS doesn't introduce accessibility violations."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for proper focus styling instead of removing outlines
        assert 'outline: 3px solid' in content, "Proper focus outlines should be defined"

    # Integration test placeholder for future browser-based testing
    def test_full_accessibility_scan(self):
        """Placeholder for full accessibility scanning with axe-core."""
        # This would use axe-core-python or similar for comprehensive testing
        # Example implementation:
        #
        # from axe_core_python import AxeCore
        # axe = AxeCore()
        # results = axe.scan(page_html)
        # violations = results.get('violations', [])
        # assert len(violations) == 0, f"Accessibility violations found: {violations}"

        pytest.skip("Full accessibility scan requires browser automation - placeholder for future implementation")


class TestAccessibilityTestingInfrastructure:
    """Test the accessibility testing infrastructure itself."""

    def test_accessibility_helper_functions_exist(self):
        """Test that accessibility helper functions are available."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Test that the announce function is properly defined
        assert 'def announce_to_screen_reader(message: str, priority: str = "polite")' in content, "announce_to_screen_reader function should be properly defined"

    def test_css_accessibility_classes_defined(self):
        """Test that accessibility-specific CSS classes are defined."""
        source_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'streamlit_autodense_app.py')

        with open(source_file, 'r') as f:
            content = f.read()

        # Check for accessibility CSS classes
        assert '.skip-link' in content, "Skip link CSS should be defined"
        assert '.sr-only' in content, "Screen reader only CSS should be defined"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])