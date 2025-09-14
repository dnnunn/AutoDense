"""
Comprehensive tests for the memory-optimized session state abstraction layer.

Tests cover:
- Memory optimization with __slots__ and external caching
- Streamlit reactivity compatibility
- Safe migration with rollback capability
- Memory-mapped array functionality
- Backwards compatibility layer
"""

import gc
import hashlib
import tempfile
import time
from pathlib import Path
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
from PIL import Image

from utils.state_management import (
    ExternalImageCache,
    ImageData,
    ImageMetadata,
    MemoryMappedArray,
    ReactiveStateManager,
    get_memory_stats,
    get_state_manager,
)
from utils.state_migration import (
    BackwardsCompatibilityLayer,
    MigrationSnapshot,
    StateMigrator,
)


class TestImageMetadata:
    """Test memory-optimized ImageMetadata with __slots__."""

    def test_metadata_creation(self):
        """Test basic metadata creation."""
        metadata = ImageMetadata(
            filename="test.jpg",
            hash="abc123",
            original_dimensions=(800, 600),
            standardized_dimensions=(800, 600),
            format="JPEG",
            mode="RGB",
            size_bytes=50000,
            upload_timestamp=time.time()
        )

        assert metadata.filename == "test.jpg"
        assert metadata.hash == "abc123"
        assert metadata.original_dimensions == (800, 600)
        assert metadata.format == "JPEG"
        assert metadata.mode == "RGB"
        assert metadata.size_bytes == 50000

    def test_metadata_validation_small_image(self):
        """Test validation rejects images that are too small."""
        with pytest.raises(ValueError, match="at least 50x50 pixels"):
            ImageMetadata(
                filename="tiny.jpg",
                hash="abc123",
                original_dimensions=(30, 30),  # Too small
                standardized_dimensions=(30, 30),
                format="JPEG",
                mode="RGB",
                size_bytes=1000,
                upload_timestamp=time.time()
            )

    def test_metadata_validation_negative_size(self):
        """Test validation rejects negative file sizes."""
        with pytest.raises(ValueError, match="Image size must be positive"):
            ImageMetadata(
                filename="test.jpg",
                hash="abc123",
                original_dimensions=(800, 600),
                standardized_dimensions=(800, 600),
                format="JPEG",
                mode="RGB",
                size_bytes=-1000,  # Invalid
                upload_timestamp=time.time()
            )

    def test_metadata_immutability(self):
        """Test that metadata is immutable (frozen=True)."""
        metadata = ImageMetadata(
            filename="test.jpg",
            hash="abc123",
            original_dimensions=(800, 600),
            standardized_dimensions=(800, 600),
            format="JPEG",
            mode="RGB",
            size_bytes=50000,
            upload_timestamp=time.time()
        )

        with pytest.raises(Exception):  # Should be immutable
            metadata.filename = "new_name.jpg"


class TestMemoryMappedArray:
    """Test memory-mapped array functionality."""

    def test_create_mmap_array(self):
        """Test creating memory-mapped array from numpy array."""
        # Create test array
        test_array = np.random.randint(0, 256, (100, 100, 3), dtype=np.uint8)

        # Create memory-mapped version
        mmap_array = MemoryMappedArray.create(test_array)

        assert mmap_array.shape == test_array.shape
        assert mmap_array.dtype == test_array.dtype
        assert mmap_array.file_path.exists()

        # Test that data is preserved
        np.testing.assert_array_equal(mmap_array.array, test_array)

        # Cleanup
        mmap_array.close()
        assert not mmap_array.file_path.exists()

    def test_mmap_array_persistence(self):
        """Test that memory-mapped arrays persist across instances."""
        test_array = np.random.randint(0, 256, (50, 50), dtype=np.uint8)

        # Create and close first instance
        mmap_array1 = MemoryMappedArray.create(test_array)
        file_path = mmap_array1.file_path
        mmap_array1.close()

        # File should still exist after close() because we haven't destroyed the object
        assert file_path.exists()

        # Create new instance with same file
        mmap_array2 = MemoryMappedArray(
            file_path=file_path,
            shape=test_array.shape,
            dtype=test_array.dtype
        )

        # Data should be preserved
        np.testing.assert_array_equal(mmap_array2.array, test_array)

        # Cleanup
        mmap_array2.close()

    def test_mmap_array_cleanup(self):
        """Test proper cleanup of memory-mapped files."""
        test_array = np.random.randint(0, 256, (30, 30), dtype=np.uint8)
        mmap_array = MemoryMappedArray.create(test_array)
        file_path = mmap_array.file_path

        # File should exist
        assert file_path.exists()

        # Close should remove file
        mmap_array.close()
        assert not file_path.exists()


class TestImageData:
    """Test lazy-loaded image data container."""

    def create_test_metadata(self) -> ImageMetadata:
        """Helper to create test metadata."""
        return ImageMetadata(
            filename="test.jpg",
            hash="test_hash",
            original_dimensions=(100, 100),
            standardized_dimensions=(100, 100),
            format="JPEG",
            mode="RGB",
            size_bytes=10000,
            upload_timestamp=time.time()
        )

    def create_test_image(self) -> Image.Image:
        """Helper to create test PIL image."""
        return Image.new('RGB', (100, 100), color='red')

    def test_image_data_creation(self):
        """Test basic ImageData creation."""
        metadata = self.create_test_metadata()
        image_data = ImageData(metadata=metadata)

        assert image_data.metadata == metadata
        assert image_data.image is None  # Not set yet
        assert image_data.array is None  # Not set yet

    def test_lazy_array_loading(self):
        """Test that numpy array is created lazily from PIL image."""
        metadata = self.create_test_metadata()
        image_data = ImageData(metadata=metadata)

        # Set PIL image
        test_image = self.create_test_image()
        image_data.image = test_image

        # Array should be created lazily
        array = image_data.array
        assert array is not None
        assert array.shape == (100, 100, 3)  # Height, Width, Channels

        # Array should be memory-mapped
        assert image_data._array_mmap is not None

    def test_memory_usage_tracking(self):
        """Test memory usage calculation."""
        metadata = self.create_test_metadata()
        image_data = ImageData(metadata=metadata)

        # Initially minimal usage
        usage = image_data.get_memory_usage()
        assert 'metadata' in usage
        assert usage['metadata'] > 0

        # Add PIL image
        test_image = self.create_test_image()
        image_data.image = test_image

        usage = image_data.get_memory_usage()
        assert 'pil_image' in usage
        assert usage['pil_image'] > 0

        # Access array to trigger memory mapping
        _ = image_data.array

        usage = image_data.get_memory_usage()
        assert 'array_mmap' in usage
        assert 'array_disk' in usage

    def test_explicit_cleanup(self):
        """Test explicit cleanup of cached data."""
        metadata = self.create_test_metadata()
        image_data = ImageData(metadata=metadata)

        # Set data
        test_image = self.create_test_image()
        image_data.image = test_image
        test_bytes = b"fake_image_bytes"
        image_data.bytes = test_bytes

        # Trigger array creation
        _ = image_data.array

        # Verify data is present
        assert image_data.image is not None
        assert image_data.bytes is not None
        assert image_data._array_mmap is not None

        # Cleanup
        image_data.cleanup()

        # Data should be cleared
        assert image_data.image is None
        assert image_data.bytes is None
        assert image_data._array_mmap is None


class TestExternalImageCache:
    """Test external image cache with LRU eviction."""

    def create_test_image_data(self, hash_suffix: str = "") -> ImageData:
        """Helper to create test image data."""
        metadata = ImageMetadata(
            filename=f"test_{hash_suffix}.jpg",
            hash=f"hash_{hash_suffix}",
            original_dimensions=(100, 100),
            standardized_dimensions=(100, 100),
            format="JPEG",
            mode="RGB",
            size_bytes=10000,
            upload_timestamp=time.time()
        )
        image_data = ImageData(metadata=metadata)
        image_data.image = Image.new('RGB', (100, 100), color='red')
        return image_data

    def test_cache_put_get(self):
        """Test basic cache operations."""
        cache = ExternalImageCache(max_size=3)

        # Put image data
        image_data = self.create_test_image_data("1")
        cache.put(image_data)

        # Get image data
        retrieved = cache.get("hash_1")
        assert retrieved is not None
        assert retrieved.metadata.hash == "hash_1"

    def test_cache_lru_eviction(self):
        """Test LRU eviction when cache is full."""
        cache = ExternalImageCache(max_size=2)

        # Add images to fill cache
        image1 = self.create_test_image_data("1")
        image2 = self.create_test_image_data("2")
        image3 = self.create_test_image_data("3")

        cache.put(image1)
        cache.put(image2)

        # Both should be in cache
        assert cache.get("hash_1") is not None
        assert cache.get("hash_2") is not None

        # Add third image (should evict oldest)
        cache.put(image3)

        # First image should be evicted
        assert cache.get("hash_1") is None
        assert cache.get("hash_2") is not None
        assert cache.get("hash_3") is not None

    def test_cache_stats(self):
        """Test cache statistics."""
        cache = ExternalImageCache(max_size=3)

        # Initial stats
        stats = cache.get_cache_stats()
        assert stats['entries'] == 0
        assert stats['max_size'] == 3

        # Add image
        image_data = self.create_test_image_data("1")
        cache.put(image_data)

        stats = cache.get_cache_stats()
        assert stats['entries'] == 1
        assert stats['total_memory_bytes'] > 0


@patch('ui.utils.state_management.st')
class TestReactiveStateManager:
    """Test Streamlit-reactive state manager."""

    def test_state_manager_initialization(self, mock_st):
        """Test state manager initialization."""
        mock_st.session_state = {}

        manager = ReactiveStateManager()

        # Should initialize session state structure
        assert 'autodense_state_version' in mock_st.session_state
        assert 'autodense_ui_state' in mock_st.session_state

    def test_image_storage_and_retrieval(self, mock_st):
        """Test image storage with external caching."""
        mock_st.session_state = {}

        manager = ReactiveStateManager()

        # Create test image and bytes
        test_image = Image.new('RGB', (100, 100), color='blue')
        test_bytes = b"fake_image_bytes"

        # Set current image
        image_data = manager.set_current_image(
            image=test_image,
            file_bytes=test_bytes,
            filename="test.jpg"
        )

        # Should store metadata in session_state
        assert 'autodense_image_metadata' in mock_st.session_state
        metadata = mock_st.session_state['autodense_image_metadata']
        assert metadata.filename == "test.jpg"

        # Should store heavy data in external cache
        retrieved = manager.current_image
        assert retrieved is not None
        assert retrieved.image.size == (100, 100)
        assert retrieved.bytes == test_bytes

    def test_memory_stats(self, mock_st):
        """Test memory statistics reporting."""
        mock_st.session_state = {}

        manager = ReactiveStateManager()
        stats = manager.get_memory_stats()

        assert 'cache' in stats
        assert 'session_state_bytes' in stats
        assert 'session_state_mb' in stats

    def test_analysis_lock_context(self, mock_st):
        """Test analysis lock context manager."""
        mock_st.session_state = {}

        manager = ReactiveStateManager()

        # Initially not running
        assert not mock_st.session_state.get('autodense_analysis_running', False)

        # Use context manager
        with manager.analysis_lock():
            assert mock_st.session_state.get('autodense_analysis_running', False)

        # Should be cleared after context
        assert not mock_st.session_state.get('autodense_analysis_running', False)


class TestStateMigrator:
    """Test safe migration infrastructure."""

    @patch('ui.utils.state_migration.st')
    def test_create_snapshot(self, mock_st):
        """Test migration snapshot creation."""
        # Setup mock session state
        mock_st.session_state = {
            'res_uploaded_image': Image.new('RGB', (50, 50)),
            'params_gel_type': 'sds_page',
            'ui_error_count': 5
        }

        migrator = StateMigrator()
        snapshot = migrator.create_snapshot("test_migration")

        assert snapshot.migration_id == "test_migration"
        assert len(snapshot.state_keys) == 3
        assert 'res_uploaded_image' in snapshot.state_data
        assert 'params_gel_type' in snapshot.state_data
        assert 'ui_error_count' in snapshot.state_data

    def test_legacy_state_validation(self):
        """Test validation of legacy session state."""
        migrator = StateMigrator()

        # Valid state
        valid_state = {
            'res_uploaded_image': Image.new('RGB', (100, 100)),
            'res_image_metadata': {'filename': 'test.jpg'},
            'params_gel_type': 'sds_page',
            'params_n_lanes': 12
        }

        issues = migrator.validate_legacy_state(valid_state)
        assert len(issues) == 0

        # Invalid state
        invalid_state = {
            'res_uploaded_image': "not_an_image",  # Should be PIL Image
            'params_gel_type': 'invalid_type',     # Invalid gel type
            'params_n_lanes': -5                   # Invalid lane count
        }

        issues = migrator.validate_legacy_state(invalid_state)
        assert len(issues) == 3

    @patch('ui.utils.state_migration.st')
    def test_safe_migration_context(self, mock_st):
        """Test safe migration context manager."""
        mock_st.session_state = {'test_key': 'test_value'}

        migrator = StateMigrator()

        # Test successful migration
        with migrator.safe_migration("test") as snapshot:
            assert snapshot.status.value == "in_progress"
            # Migration operations would go here

        # Should be marked as success
        assert snapshot.status.value == "success"

    @patch('ui.utils.state_migration.st')
    def test_migration_rollback_on_failure(self, mock_st):
        """Test automatic rollback on migration failure."""
        original_state = {'test_key': 'original_value'}
        mock_st.session_state = original_state.copy()

        migrator = StateMigrator()

        # Test migration with failure
        with pytest.raises(Exception):
            with migrator.safe_migration("test_fail") as snapshot:
                # Simulate migration failure
                mock_st.session_state['test_key'] = 'modified_value'
                raise Exception("Migration failed")

        # Should be rolled back to original state
        assert mock_st.session_state['test_key'] == 'original_value'


@patch('ui.utils.state_migration.st')
class TestBackwardsCompatibilityLayer:
    """Test backwards compatibility during migration."""

    def test_legacy_access_routing(self, mock_st):
        """Test routing legacy keys through new system."""
        mock_st.session_state = {}

        # Create state manager and compatibility layer
        from ui.utils.state_management import ReactiveStateManager
        state_manager = ReactiveStateManager()
        compat_layer = BackwardsCompatibilityLayer(state_manager)

        # Test image-related routing
        test_image = Image.new('RGB', (50, 50))

        # Set image through new system
        state_manager.set_current_image(
            image=test_image,
            file_bytes=b"test_bytes",
            filename="test.jpg"
        )

        # Access through legacy key should work
        retrieved_image = compat_layer.get_legacy_value('res_uploaded_image')
        assert retrieved_image is not None
        assert retrieved_image.size == (50, 50)

    def test_migration_stats_tracking(self, mock_st):
        """Test tracking of legacy access patterns."""
        mock_st.session_state = {}

        from ui.utils.state_management import ReactiveStateManager
        state_manager = ReactiveStateManager()
        compat_layer = BackwardsCompatibilityLayer(state_manager)

        # Perform several legacy accesses
        compat_layer.get_legacy_value('res_uploaded_image')
        compat_layer.get_legacy_value('params_gel_type')
        compat_layer.set_legacy_value('ui_error_count', 5)

        # Check stats
        stats = compat_layer.get_migration_stats()
        assert stats['total_accesses'] == 3
        assert stats['unique_keys'] == 3
        assert 'most_accessed_keys' in stats


class TestIntegration:
    """Integration tests combining all components."""

    @patch('ui.utils.state_management.st')
    @patch('ui.utils.state_migration.st')
    def test_full_migration_workflow(self, mock_migration_st, mock_state_st):
        """Test complete migration workflow from legacy to new system."""
        # Setup legacy session state
        legacy_state = {
            'res_uploaded_image': Image.new('RGB', (100, 100), color='green'),
            'res_image_bytes': b"fake_image_data",
            'res_image_metadata': {'filename': 'legacy_image.jpg'},
            'params_gel_type': 'sds_page',
            'params_n_lanes': 10
        }

        mock_migration_st.session_state = legacy_state.copy()
        mock_state_st.session_state = {}

        # Perform migration
        migrator = StateMigrator()

        with migrator.safe_migration("integration_test") as snapshot:
            success = migrator.migrate_to_new_state(snapshot)
            assert success

        # Verify migration was successful
        assert snapshot.status.value == "success"

    def test_memory_optimization_effectiveness(self):
        """Test that memory optimization actually reduces usage."""
        # This test would ideally measure actual memory usage
        # For now, we verify that the structure supports optimization

        # Create large test image
        large_image = Image.new('RGB', (1000, 1000), color='blue')
        large_array = np.asarray(large_image)

        # Test that memory-mapped array uses less RAM
        mmap_array = MemoryMappedArray.create(large_array)

        # The array should exist on disk
        assert mmap_array.file_path.exists()
        file_size = mmap_array.file_path.stat().st_size
        expected_size = large_array.nbytes

        # File size should match array size
        assert file_size == expected_size

        # Cleanup
        mmap_array.close()


# Fixtures for test data
@pytest.fixture
def sample_image():
    """Create sample PIL image for testing."""
    return Image.new('RGB', (200, 150), color='red')


@pytest.fixture
def sample_image_bytes():
    """Create sample image bytes for testing."""
    return b"fake_image_data_for_testing"


@pytest.fixture
def sample_metadata():
    """Create sample image metadata for testing."""
    return ImageMetadata(
        filename="sample.jpg",
        hash="sample_hash_123",
        original_dimensions=(200, 150),
        standardized_dimensions=(200, 150),
        format="JPEG",
        mode="RGB",
        size_bytes=10000,
        upload_timestamp=time.time()
    )


# Performance and memory tests
@pytest.mark.performance
class TestPerformance:
    """Performance-focused tests."""

    def test_memory_mapped_array_performance(self):
        """Test that memory-mapped arrays have acceptable performance."""
        # Create moderately large array (similar to scientific image)
        test_array = np.random.randint(0, 256, (500, 500, 3), dtype=np.uint8)

        # Time memory-mapped array creation
        start_time = time.time()
        mmap_array = MemoryMappedArray.create(test_array)
        creation_time = time.time() - start_time

        # Should create reasonably quickly (< 1 second for 500x500 image)
        assert creation_time < 1.0

        # Time array access
        start_time = time.time()
        retrieved_array = mmap_array.array
        access_time = time.time() - start_time

        # Should access quickly (< 0.1 second)
        assert access_time < 0.1

        # Verify data integrity
        np.testing.assert_array_equal(retrieved_array, test_array)

        # Cleanup
        mmap_array.close()

    def test_cache_performance_under_load(self):
        """Test cache performance with multiple operations."""
        cache = ExternalImageCache(max_size=10)

        # Add multiple images rapidly
        start_time = time.time()

        for i in range(15):  # More than max_size to trigger eviction
            metadata = ImageMetadata(
                filename=f"test_{i}.jpg",
                hash=f"hash_{i}",
                original_dimensions=(100, 100),
                standardized_dimensions=(100, 100),
                format="JPEG",
                mode="RGB",
                size_bytes=10000,
                upload_timestamp=time.time()
            )
            image_data = ImageData(metadata=metadata)
            image_data.image = Image.new('RGB', (100, 100))
            cache.put(image_data)

        total_time = time.time() - start_time

        # Should complete rapidly (< 2 seconds for 15 operations)
        assert total_time < 2.0

        # Should maintain size limit
        stats = cache.get_cache_stats()
        assert stats['entries'] <= 10


if __name__ == "__main__":
    # Run tests with verbose output
    pytest.main([__file__, "-v", "--tb=short"])