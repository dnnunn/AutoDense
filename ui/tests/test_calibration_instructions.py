"""Unit tests for calibration_instructions.py component.

This test suite provides comprehensive coverage of the calibration instructions
component including rendering behavior and content validation.
"""

import pytest
from unittest.mock import patch, Mock
from components.calibration_instructions import render_calibration_instructions


class TestCalibrationInstructionsBasicFunctionality:
    """Test basic functionality and rendering behavior."""

    @pytest.mark.unit
    def test_renders_without_error_collapsed(self, mock_streamlit):
        """Test that component renders without error when collapsed by default."""
        render_calibration_instructions()
        # Should complete without raising exceptions
        mock_streamlit['expander'].assert_called_once()

    @pytest.mark.unit
    def test_renders_without_error_expanded(self, mock_streamlit):
        """Test that component renders without error when expanded."""
        render_calibration_instructions(expanded=True)
        mock_streamlit['expander'].assert_called_once()

    @pytest.mark.unit
    def test_returns_none(self, mock_streamlit):
        """Test that function returns None (no return value)."""
        result = render_calibration_instructions()
        assert result is None

    @pytest.mark.unit
    def test_function_signature_compatibility(self, mock_streamlit):
        """Test that function signature works with different argument combinations."""
        # Test with no arguments
        render_calibration_instructions()
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=False)

        # Reset mock
        mock_streamlit['expander'].reset_mock()

        # Test with expanded=True
        render_calibration_instructions(expanded=True)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=True)

        # Reset mock
        mock_streamlit['expander'].reset_mock()

        # Test with expanded=False explicitly
        render_calibration_instructions(expanded=False)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=False)


class TestCalibrationInstructionsUIStructure:
    """Test UI structure and layout."""

    @pytest.mark.unit
    def test_creates_expander_with_correct_title(self, mock_streamlit):
        """Test that expander is created with correct title."""
        render_calibration_instructions()
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=False)

    @pytest.mark.unit
    def test_creates_expander_expanded_when_requested(self, mock_streamlit):
        """Test that expander is created with expanded=True when requested."""
        render_calibration_instructions(expanded=True)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=True)

    @pytest.mark.unit
    def test_creates_two_column_layout(self, mock_streamlit):
        """Test that two-column layout is created inside expander."""
        render_calibration_instructions()
        # Should create columns with ratio [2, 1]
        mock_streamlit['columns'].assert_called_with([2, 1])

    @pytest.mark.unit
    def test_uses_expander_context_manager(self, mock_streamlit):
        """Test that expander is used as context manager."""
        render_calibration_instructions()

        # Verify expander context manager was entered and exited
        expander_mock = mock_streamlit['expander'].return_value
        expander_mock.__enter__.assert_called_once()
        expander_mock.__exit__.assert_called_once()

    @pytest.mark.unit
    def test_uses_column_context_managers(self, mock_streamlit):
        """Test that both columns are used as context managers."""
        render_calibration_instructions()

        # Verify column context managers were used
        col1_mock = mock_streamlit['col1']
        col2_mock = mock_streamlit['col2']

        col1_mock.__enter__.assert_called_once()
        col1_mock.__exit__.assert_called_once()
        col2_mock.__enter__.assert_called_once()
        col2_mock.__exit__.assert_called_once()


class TestCalibrationInstructionsContent:
    """Test content rendering and validation."""

    @pytest.mark.unit
    def test_renders_step_by_step_instructions(self, mock_streamlit):
        """Test that step-by-step instructions are rendered in first column."""
        render_calibration_instructions()

        # Check that markdown was called with step-by-step content
        markdown_calls = [call[0][0] for call in mock_streamlit['markdown'].call_args_list]

        # Should contain the main instructions
        step_by_step_found = any("Step-by-step calibration:" in call for call in markdown_calls)
        assert step_by_step_found, "Step-by-step instructions should be rendered"

    @pytest.mark.unit
    def test_renders_common_mistakes_section(self, mock_streamlit):
        """Test that common mistakes section is rendered in second column."""
        render_calibration_instructions()

        # Check that markdown was called with common mistakes content
        markdown_calls = [call[0][0] for call in mock_streamlit['markdown'].call_args_list]

        # Should contain the common mistakes section
        mistakes_found = any("Common mistakes to avoid:" in call for call in markdown_calls)
        assert mistakes_found, "Common mistakes section should be rendered"

    @pytest.mark.unit
    def test_contains_essential_calibration_steps(self, mock_streamlit):
        """Test that essential calibration steps are included in content."""
        render_calibration_instructions()

        # Get all markdown content
        markdown_calls = [call[0][0] for call in mock_streamlit['markdown'].call_args_list]
        all_content = " ".join(markdown_calls)

        # Check for key calibration concepts
        essential_terms = [
            "reference lanes",
            "calibration point",
            "band centers",
            "lane boundaries",
            "accuracy"
        ]

        for term in essential_terms:
            assert term.lower() in all_content.lower(), f"Essential term '{term}' should be in instructions"

    @pytest.mark.unit
    def test_contains_best_practice_guidance(self, mock_streamlit):
        """Test that best practice guidance is included."""
        render_calibration_instructions()

        # Get all markdown content
        markdown_calls = [call[0][0] for call in mock_streamlit['markdown'].call_args_list]
        all_content = " ".join(markdown_calls)

        # Check for best practices
        best_practices = [
            "sharp and well-separated",
            "avoid bands at the very top",
            "span a good portion",
            "precisely on the band centers"
        ]

        for practice in best_practices:
            assert practice.lower() in all_content.lower(), f"Best practice '{practice}' should be in guidance"

    @pytest.mark.unit
    def test_contains_common_mistake_warnings(self, mock_streamlit):
        """Test that common mistake warnings are included."""
        render_calibration_instructions()

        # Get all markdown content
        markdown_calls = [call[0][0] for call in mock_streamlit['markdown'].call_args_list]
        all_content = " ".join(markdown_calls)

        # Check for common mistakes
        common_mistakes = [
            "clicking on band edges",
            "distorted or unclear bands",
            "too close together",
            "same lane",
            "gel artifacts or bubbles"
        ]

        for mistake in common_mistakes:
            assert mistake.lower() in all_content.lower(), f"Common mistake '{mistake}' should be mentioned"


class TestCalibrationInstructionsNoSideEffects:
    """Test that component has no unwanted side effects."""

    @pytest.mark.unit
    def test_no_session_state_modifications(self, mock_streamlit):
        """Test that component does not modify session state."""
        with patch('streamlit.session_state') as mock_state:
            initial_calls = len(mock_state.method_calls) if hasattr(mock_state, 'method_calls') else 0

            render_calibration_instructions()

            # Should not have modified session state
            final_calls = len(mock_state.method_calls) if hasattr(mock_state, 'method_calls') else 0
            assert final_calls == initial_calls, "Component should not modify session state"

    @pytest.mark.unit
    def test_no_error_or_warning_messages(self, mock_streamlit):
        """Test that component does not generate error or warning messages."""
        render_calibration_instructions()

        # Should not call error, warning, or toast functions
        mock_streamlit['error'].assert_not_called()
        mock_streamlit['warning'].assert_not_called()
        if 'toast' in mock_streamlit:
            mock_streamlit['toast'].assert_not_called()

    @pytest.mark.unit
    def test_no_user_interaction_elements(self, mock_streamlit):
        """Test that component does not create interactive elements."""
        render_calibration_instructions()

        # Should not create buttons, inputs, or other interactive elements
        mock_streamlit['button'].assert_not_called()
        if 'text_input' in mock_streamlit:
            mock_streamlit['text_input'].assert_not_called()
        if 'selectbox' in mock_streamlit:
            mock_streamlit['selectbox'].assert_not_called()


class TestCalibrationInstructionsEdgeCases:
    """Test edge cases and boundary conditions."""

    @pytest.mark.unit
    def test_handles_none_expanded_parameter(self, mock_streamlit):
        """Test handling of None as expanded parameter."""
        # Should treat None as False (default behavior)
        render_calibration_instructions(expanded=None)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=None)

    @pytest.mark.unit
    def test_handles_non_boolean_expanded_parameter(self, mock_streamlit):
        """Test handling of non-boolean expanded parameter."""
        # Test with string
        render_calibration_instructions(expanded="true")
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded="true")

        mock_streamlit['expander'].reset_mock()

        # Test with integer
        render_calibration_instructions(expanded=1)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=1)

    @pytest.mark.unit
    def test_multiple_consecutive_calls(self, mock_streamlit):
        """Test that multiple consecutive calls work correctly."""
        # First call
        render_calibration_instructions(expanded=False)
        first_call_count = mock_streamlit['expander'].call_count

        # Second call
        render_calibration_instructions(expanded=True)
        second_call_count = mock_streamlit['expander'].call_count

        # Should have been called twice
        assert second_call_count == first_call_count + 1


class TestCalibrationInstructionsPerformance:
    """Test performance characteristics."""

    @pytest.mark.unit
    def test_minimal_function_calls(self, mock_streamlit):
        """Test that component makes minimal function calls."""
        render_calibration_instructions()

        # Should call expander once, columns once, markdown twice (for each column)
        assert mock_streamlit['expander'].call_count == 1
        assert mock_streamlit['columns'].call_count == 1
        assert mock_streamlit['markdown'].call_count == 2

    @pytest.mark.unit
    def test_no_expensive_operations(self, mock_streamlit):
        """Test that no expensive operations are performed."""
        # This should complete very quickly - just checking it doesn't hang
        render_calibration_instructions()
        # If we get here, the function completed without hanging

    @pytest.mark.unit
    def test_consistent_behavior_across_calls(self, mock_streamlit):
        """Test that behavior is consistent across multiple calls."""
        # First call
        render_calibration_instructions(expanded=False)
        first_call_args = mock_streamlit['expander'].call_args

        # Reset mock
        mock_streamlit['expander'].reset_mock()
        mock_streamlit['columns'].reset_mock()
        mock_streamlit['markdown'].reset_mock()

        # Second call with same parameters
        render_calibration_instructions(expanded=False)
        second_call_args = mock_streamlit['expander'].call_args

        # Should have identical call arguments
        assert first_call_args == second_call_args


class TestCalibrationInstructionsIntegration:
    """Integration-style tests for real-world usage scenarios."""

    @pytest.mark.integration
    def test_typical_workflow_collapsed_then_expanded(self, mock_streamlit):
        """Test typical workflow: first show collapsed, then expanded."""
        # First show collapsed (typical initial state)
        render_calibration_instructions(expanded=False)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=False)

        # Reset for second call
        mock_streamlit['expander'].reset_mock()

        # Then show expanded (user wants to read instructions)
        render_calibration_instructions(expanded=True)
        mock_streamlit['expander'].assert_called_with("📖 Calibration Instructions & Best Practices", expanded=True)

    @pytest.mark.integration
    def test_content_completeness_for_user_guidance(self, mock_streamlit):
        """Test that content is complete enough for user guidance."""
        render_calibration_instructions()

        # Get all rendered content
        markdown_calls = [call[0][0] for call in mock_streamlit['markdown'].call_args_list]
        all_content = " ".join(markdown_calls)

        # Should have enough content to guide users (reasonable length)
        assert len(all_content) > 500, "Content should be substantial enough to guide users"

        # Should have both instructional and cautionary content
        assert "step" in all_content.lower(), "Should contain step-by-step guidance"
        assert "avoid" in all_content.lower(), "Should contain things to avoid"
        assert "click" in all_content.lower(), "Should mention clicking actions"
        assert "band" in all_content.lower(), "Should mention bands (core concept)"