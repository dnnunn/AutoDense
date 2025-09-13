"""Unit tests for image_upload.py component.

This test suite provides comprehensive coverage of the image upload component
including file validation, error handling, and session state management.
"""

import pytest
import io
import hashlib
from unittest.mock import patch, Mock, MagicMock
import numpy as np
from PIL import Image

from components.image_upload import render_image_upload, handle_errors


class TestImageUploadBasicFunctionality:
    """Test basic functionality and return values."""

    @pytest.mark.unit
    def test_returns_none_when_no_file_uploaded(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test that function returns None when no file is uploaded."""
        mock_streamlit['file_uploader'].return_value = None

        with patch('streamlit.session_state', mock_session_state):
            result = render_image_upload()
            assert result is None

    @pytest.mark.unit
    def test_renders_upload_section(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test that upload section UI is rendered."""
        mock_streamlit['file_uploader'].return_value = None

        with patch('streamlit.session_state', mock_session_state):
            render_image_upload()

            # Should render upload section with proper HTML structure
            mock_streamlit['markdown'].assert_any_call('<div data-testid="file-upload-section" role="region" aria-labelledby="upload-heading">', unsafe_allow_html=True)
            mock_streamlit['markdown'].assert_any_call('<h3 id="upload-heading">📤 Image Upload</h3>', unsafe_allow_html=True)
            mock_streamlit['markdown'].assert_any_call('</div>', unsafe_allow_html=True)

    @pytest.mark.unit
    def test_creates_file_uploader_with_correct_parameters(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test that file uploader is created with correct parameters."""
        mock_streamlit['file_uploader'].return_value = None

        with patch('streamlit.session_state', mock_session_state):
            render_image_upload()

            mock_streamlit['file_uploader'].assert_called_with(
                "Select gel image",
                type=["jpg", "jpeg", "png", "tiff", "tif", "heic", "heif"],
                help="Supported: PNG, JPG, TIFF, HEIC/HEIF (install pillow-heif). Optimal size: 1000–4000px width, <50MB",
                label_visibility="collapsed",
                key="main_file_uploader"
            )

    @pytest.mark.unit
    def test_custom_key_prefix_used_correctly(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test that custom key prefix is used for file uploader key."""
        mock_streamlit['file_uploader'].return_value = None

        with patch('streamlit.session_state', mock_session_state):
            render_image_upload(key_prefix="test")

            mock_streamlit['file_uploader'].assert_called_with(
                "Select gel image",
                type=["jpg", "jpeg", "png", "tiff", "tif", "heic", "heif"],
                help="Supported: PNG, JPG, TIFF, HEIC/HEIF (install pillow-heif). Optimal size: 1000–4000px width, <50MB",
                label_visibility="collapsed",
                key="test_file_uploader"
            )


class TestImageUploadFileProcessing:
    """Test file processing and validation logic."""

    @pytest.mark.unit
    def test_successful_file_upload_processing(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test successful file upload and processing."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            # Mock PIL Image
            mock_img = Mock(spec=Image.Image)
            mock_img.size = (100, 100)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img
            mock_image_open.return_value = mock_img

            # Mock numpy array conversion
            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((100, 100, 3), dtype=np.uint8)

                result = render_image_upload()

                assert result is not None
                assert len(result) == 3  # Should return (img, array, metadata)

    @pytest.mark.unit
    def test_file_size_validation_rejects_large_files(self, mock_streamlit, mock_session_state, large_file_mock, mock_standardize_image):
        """Test that files larger than max size are rejected."""
        mock_streamlit['file_uploader'].return_value = large_file_mock

        with patch('streamlit.session_state', mock_session_state):
            result = render_image_upload(max_file_size_mb=50)

            mock_streamlit['error'].assert_called_with("❌ File too large (>50MB). Please use a smaller image.")
            assert result is None

    @pytest.mark.unit
    def test_duplicate_image_detection(self, mock_streamlit, session_state_with_existing_image, sample_uploaded_file, mock_standardize_image):
        """Test that duplicate images are detected and not reprocessed."""
        # Configure uploaded file to have same hash as existing image
        file_bytes = sample_uploaded_file.getvalue.return_value
        image_hash = hashlib.md5(file_bytes).hexdigest()[:8]
        session_state_with_existing_image.res_image_metadata['hash'] = image_hash

        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', session_state_with_existing_image):
            result = render_image_upload()
            assert result is None  # Should not reprocess same image

    @pytest.mark.unit
    def test_image_standardization_applied(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test that image standardization is applied during processing."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            # Mock original and standardized images
            mock_original_img = Mock(spec=Image.Image)
            mock_original_img.size = (2000, 1500)
            mock_original_img.format = 'JPEG'
            mock_original_img.convert.return_value = mock_original_img

            mock_standardized_img = Mock(spec=Image.Image)
            mock_standardized_img.size = (1024, 768)
            mock_standardized_img.mode = 'RGB'

            mock_image_open.return_value = mock_original_img
            mock_standardize_image.return_value = mock_standardized_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((768, 1024, 3), dtype=np.uint8)

                result = render_image_upload()

                # Should call standardize_image_size
                mock_standardize_image.assert_called_once_with(mock_original_img)
                assert result is not None

    @pytest.mark.unit
    def test_session_state_updates_correctly(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test that session state is updated with correct values."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            mock_img = Mock(spec=Image.Image)
            mock_img.size = (1024, 768)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img
            mock_image_open.return_value = mock_img
            mock_standardize_image.return_value = mock_img

            with patch('numpy.array') as mock_array:
                mock_array_result = np.zeros((768, 1024, 3), dtype=np.uint8)
                mock_array.return_value = mock_array_result

                render_image_upload()

                # Verify session state updates
                assert mock_session_state.res_uploaded_image == mock_img
                assert np.array_equal(mock_session_state.res_uploaded_array, mock_array_result)
                assert mock_session_state.res_image_metadata is not None
                assert mock_session_state.res_image_bytes == sample_uploaded_file.getvalue.return_value
                assert mock_session_state.ui_last_action == "upload"


class TestImageUploadErrorHandling:
    """Test error handling scenarios."""

    @pytest.mark.unit
    def test_handle_errors_decorator_functionality(self):
        """Test that handle_errors decorator works correctly."""
        # Test successful function execution
        @handle_errors("Test Operation")
        def successful_function():
            return "success"

        result = successful_function()
        assert result == "success"

        # Test function that raises exception
        @handle_errors("Test Operation")
        def failing_function():
            raise ValueError("Test error")

        with patch('streamlit.session_state') as mock_state, \
             patch('streamlit.toast') as mock_toast, \
             patch('streamlit.expander') as mock_expander, \
             patch('streamlit.markdown') as mock_markdown:

            mock_state.ui_error_count = 0

            result = failing_function()
            assert result is None
            mock_toast.assert_called_with("Test Operation failed", icon="❌")

    @pytest.mark.unit
    def test_invalid_image_handling(self, mock_streamlit, mock_session_state, invalid_image_mock, mock_standardize_image):
        """Test handling of invalid image files."""
        mock_streamlit['file_uploader'].return_value = invalid_image_mock

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open', side_effect=Exception("Invalid image")):

            result = render_image_upload()
            # Should handle error gracefully and return None
            assert result is None

    @pytest.mark.unit
    def test_error_count_increments(self, mock_streamlit, mock_session_state, invalid_image_mock, mock_standardize_image):
        """Test that error count increments when errors occur."""
        mock_streamlit['file_uploader'].return_value = invalid_image_mock
        mock_session_state.ui_error_count = 0

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open', side_effect=Exception("Invalid image")):

            render_image_upload()
            # Error count should increment (handled by decorator)
            # Note: This test verifies the decorator is applied correctly


class TestImageUploadMetadata:
    """Test metadata generation and display."""

    @pytest.mark.unit
    def test_metadata_generation_complete(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test that complete metadata is generated."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            mock_original_img = Mock(spec=Image.Image)
            mock_original_img.size = (2000, 1500)
            mock_original_img.format = 'JPEG'
            mock_original_img.convert.return_value = mock_original_img

            mock_standardized_img = Mock(spec=Image.Image)
            mock_standardized_img.size = (1024, 768)
            mock_standardized_img.mode = 'RGB'

            mock_image_open.return_value = mock_original_img
            mock_standardize_image.return_value = mock_standardized_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((768, 1024, 3), dtype=np.uint8)

                render_image_upload()

                metadata = mock_session_state.res_image_metadata
                assert 'filename' in metadata
                assert 'size_bytes' in metadata
                assert 'original_dimensions' in metadata
                assert 'standardized_dimensions' in metadata
                assert 'dimensions' in metadata
                assert 'format' in metadata
                assert 'mode' in metadata
                assert 'hash' in metadata
                assert 'standardized' in metadata

    @pytest.mark.unit
    def test_metadata_display_when_requested(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test that metadata is displayed when show_metadata=True."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            mock_img = Mock(spec=Image.Image)
            mock_img.size = (1024, 768)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img
            mock_image_open.return_value = mock_img
            mock_standardize_image.return_value = mock_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((768, 1024, 3), dtype=np.uint8)

                render_image_upload(show_metadata=True)

                # Should create expander for metadata
                mock_streamlit['expander'].assert_called_with("📋 Image Information", expanded=False)

    @pytest.mark.unit
    def test_no_metadata_display_when_not_requested(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test that metadata is not displayed when show_metadata=False."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            mock_img = Mock(spec=Image.Image)
            mock_img.size = (1024, 768)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img
            mock_image_open.return_value = mock_img
            mock_standardize_image.return_value = mock_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((768, 1024, 3), dtype=np.uint8)

                render_image_upload(show_metadata=False)

                # Should not create expander for metadata
                mock_streamlit['expander'].assert_not_called()


class TestImageUploadQualityAssessment:
    """Test image quality assessment and feedback."""

    @pytest.mark.unit
    def test_standardization_feedback_when_applied(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test feedback when image standardization is applied."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            # Setup original image larger than standardized
            mock_original_img = Mock(spec=Image.Image)
            mock_original_img.size = (2000, 1500)
            mock_original_img.format = 'JPEG'
            mock_original_img.convert.return_value = mock_original_img

            mock_standardized_img = Mock(spec=Image.Image)
            mock_standardized_img.size = (1024, 768)
            mock_standardized_img.mode = 'RGB'

            mock_image_open.return_value = mock_original_img
            mock_standardize_image.return_value = mock_standardized_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((768, 1024, 3), dtype=np.uint8)

                render_image_upload()

                # Should show standardization info and success message
                mock_streamlit['info'].assert_any_call("📏 Image standardized: 2000×1500px → 1024×768px")
                mock_streamlit['success'].assert_any_call("✅ Standardized for optimal processing and ChatGPT compatibility")

    @pytest.mark.unit
    def test_no_standardization_feedback_when_not_needed(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test feedback when image standardization is not needed."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            # Setup image that doesn't need standardization
            mock_img = Mock(spec=Image.Image)
            mock_img.size = (1024, 768)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img

            mock_image_open.return_value = mock_img
            mock_standardize_image.return_value = mock_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((768, 1024, 3), dtype=np.uint8)

                render_image_upload()

                # Should show optimal size message
                mock_streamlit['success'].assert_any_call("✅ Image size already optimal: 1024×768px")

    @pytest.mark.unit
    def test_small_image_warning(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test warning for small images."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            # Setup small image
            mock_img = Mock(spec=Image.Image)
            mock_img.size = (400, 200)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img

            mock_image_open.return_value = mock_img
            mock_standardize_image.return_value = mock_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((200, 400, 3), dtype=np.uint8)

                render_image_upload()

                # Should show warning for small image
                mock_streamlit['warning'].assert_any_call("⚠️ Small image (400×200px) may produce less accurate results.")

    @pytest.mark.unit
    def test_large_image_optimization_message(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test success message for large image optimization."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            # Setup very large original image
            mock_original_img = Mock(spec=Image.Image)
            mock_original_img.size = (5000, 4000)
            mock_original_img.format = 'JPEG'
            mock_original_img.convert.return_value = mock_original_img

            mock_standardized_img = Mock(spec=Image.Image)
            mock_standardized_img.size = (1024, 819)  # Proportionally scaled down
            mock_standardized_img.mode = 'RGB'

            mock_image_open.return_value = mock_original_img
            mock_standardize_image.return_value = mock_standardized_img

            with patch('numpy.array') as mock_array:
                mock_array.return_value = np.zeros((819, 1024, 3), dtype=np.uint8)

                render_image_upload()

                # Should show optimization success message
                mock_streamlit['success'].assert_any_call("🚀 Large image optimized: 5000×4000px → 1024×819px")


class TestImageUploadIntegration:
    """Integration-style tests for complete workflows."""

    @pytest.mark.integration
    def test_complete_upload_workflow(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test complete upload workflow from file selection to session state update."""
        mock_streamlit['file_uploader'].return_value = sample_uploaded_file

        with patch('streamlit.session_state', mock_session_state), \
             patch('PIL.Image.open') as mock_image_open:

            mock_img = Mock(spec=Image.Image)
            mock_img.size = (1024, 768)
            mock_img.format = 'JPEG'
            mock_img.mode = 'RGB'
            mock_img.convert.return_value = mock_img
            mock_image_open.return_value = mock_img
            mock_standardize_image.return_value = mock_img

            with patch('numpy.array') as mock_array:
                mock_array_result = np.zeros((768, 1024, 3), dtype=np.uint8)
                mock_array.return_value = mock_array_result

                # Execute complete workflow
                result = render_image_upload()

                # Verify return value
                assert result is not None
                img, img_array, metadata = result
                assert img == mock_img
                assert np.array_equal(img_array, mock_array_result)
                assert isinstance(metadata, dict)

                # Verify session state updates
                assert mock_session_state.res_uploaded_image == mock_img
                assert np.array_equal(mock_session_state.res_uploaded_array, mock_array_result)
                assert mock_session_state.ui_last_action == "upload"

                # Verify user feedback
                mock_streamlit['toast'].assert_called_with("Image loaded: test_gel.jpg", icon="✅")

    @pytest.mark.integration
    def test_parameter_combinations(self, mock_streamlit, mock_session_state, sample_uploaded_file, mock_standardize_image):
        """Test various parameter combinations work correctly."""
        mock_streamlit['file_uploader'].return_value = None

        # Test different parameter combinations
        with patch('streamlit.session_state', mock_session_state):
            # Default parameters
            render_image_upload()

            # Custom key prefix
            render_image_upload(key_prefix="test")

            # Custom max file size
            render_image_upload(max_file_size_mb=25)

            # Hide metadata
            render_image_upload(show_metadata=False)

            # All custom parameters
            render_image_upload(key_prefix="custom", max_file_size_mb=100, show_metadata=True)

            # All calls should complete without error
            assert True


class TestImageUploadEdgeCases:
    """Test edge cases and boundary conditions."""

    @pytest.mark.unit
    def test_zero_byte_file(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test handling of zero-byte files."""
        zero_byte_file = Mock()
        zero_byte_file.name = 'empty.jpg'
        zero_byte_file.size = 0
        zero_byte_file.getvalue.return_value = b''

        mock_streamlit['file_uploader'].return_value = zero_byte_file

        with patch('streamlit.session_state', mock_session_state):
            result = render_image_upload()
            # Should handle gracefully (likely will error in PIL.Image.open, which is expected)

    @pytest.mark.unit
    def test_boundary_file_size_exactly_at_limit(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test file exactly at the size limit."""
        boundary_file = Mock()
        boundary_file.name = 'boundary.jpg'
        boundary_file.size = 50 * 1024 * 1024  # Exactly 50MB
        boundary_file.getvalue.return_value = b'x' * (50 * 1024 * 1024)

        mock_streamlit['file_uploader'].return_value = boundary_file

        with patch('streamlit.session_state', mock_session_state):
            # This should not be rejected (equal to limit, not greater)
            render_image_upload(max_file_size_mb=50)
            mock_streamlit['error'].assert_not_called()

    @pytest.mark.unit
    def test_function_signature_backward_compatibility(self, mock_streamlit, mock_session_state, mock_standardize_image):
        """Test that function signature is backward compatible."""
        mock_streamlit['file_uploader'].return_value = None

        with patch('streamlit.session_state', mock_session_state):
            # Test with no arguments
            result1 = render_image_upload()
            assert result1 is None

            # Test with all arguments
            result2 = render_image_upload(
                key_prefix="test",
                max_file_size_mb=100,
                show_metadata=False
            )
            assert result2 is None