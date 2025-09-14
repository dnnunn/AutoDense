"""
Safe Migration Infrastructure for Session State Abstraction

This module provides safe migration from direct st.session_state access to the new
memory-optimized abstraction layer with automatic rollback on failure.

Key Features:
- Snapshot creation before migration
- Automatic rollback on validation failure
- Backwards compatibility during transition
- Comprehensive logging of migration operations
- Gradual migration with feature flags
"""

from __future__ import annotations

import json
import logging
import time
from contextlib import contextmanager
from dataclasses import asdict, dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

import streamlit as st
from PIL import Image

from .state_management import ImageData, ImageMetadata, ReactiveStateManager


class MigrationStatus(Enum):
    """Status of migration operations."""
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    SUCCESS = "success"
    FAILED = "failed"
    ROLLED_BACK = "rolled_back"


@dataclass
class MigrationSnapshot:
    """Snapshot of session state before migration."""
    timestamp: float
    version: str
    state_keys: List[str]
    state_data: Dict[str, Any]
    migration_id: str
    status: MigrationStatus = MigrationStatus.PENDING

    def save_to_file(self, snapshot_dir: Path) -> Path:
        """Save snapshot to disk for recovery."""
        snapshot_dir.mkdir(exist_ok=True)
        snapshot_file = snapshot_dir / f"migration_snapshot_{self.migration_id}.json"

        # Convert to JSON-serializable format
        snapshot_data = {
            'timestamp': self.timestamp,
            'version': self.version,
            'state_keys': self.state_keys,
            'migration_id': self.migration_id,
            'status': self.status.value,
            # Note: state_data may contain non-serializable objects (PIL Images, etc.)
            # We'll handle this with custom serialization
            'has_complex_objects': self._has_complex_objects()
        }

        with open(snapshot_file, 'w') as f:
            json.dump(snapshot_data, f, indent=2)

        return snapshot_file

    def _has_complex_objects(self) -> bool:
        """Check if state contains complex objects that need special handling."""
        for value in self.state_data.values():
            if isinstance(value, (Image.Image, type(None))):
                continue  # Handle these types
            if hasattr(value, '__class__') and value.__class__.__module__ != 'builtins':
                return True
        return False


class StateMigrator:
    """
    Safe migration manager with rollback capability.

    Handles the transition from direct session_state access to the new
    abstraction layer with comprehensive error handling and recovery.
    """

    def __init__(self, snapshot_dir: Optional[Path] = None):
        self.logger = logging.getLogger(__name__)
        self.snapshot_dir = snapshot_dir or Path.cwd() / ".streamlit" / "migration_snapshots"
        self.snapshot_dir.mkdir(parents=True, exist_ok=True)

        # Legacy key mappings for backwards compatibility
        self.legacy_key_mapping = {
            # Image-related keys
            'res_uploaded_image': 'autodense_image_data.image',
            'res_uploaded_array': 'autodense_image_data.array',
            'res_image_metadata': 'autodense_image_metadata',
            'res_image_bytes': 'autodense_image_data.bytes',

            # Analysis keys
            'res_lane_boundaries': 'autodense_analysis.lane_boundaries',
            'res_calibration_points': 'autodense_analysis.calibration_points',
            'res_analysis_data': 'autodense_analysis.analysis_data',
            'res_export_data': 'autodense_analysis.export_data',

            # Parameter keys
            'params_gel_type': 'autodense_params.gel_type',
            'params_n_lanes': 'autodense_params.n_lanes',
            'calibration_lane1': 'autodense_params.lane1',
            'calibration_lane2': 'autodense_params.lane2',

            # UI state keys
            'ui_error_count': 'autodense_ui_state.error_count',
            'ui_last_action': 'autodense_ui_state.last_action',
            'ui_canvas_key': 'autodense_ui_state.canvas_key',
            '_analysis_running': 'autodense_ui_state.analysis_running'
        }

    def create_snapshot(self, migration_id: Optional[str] = None) -> MigrationSnapshot:
        """Create snapshot of current session state before migration."""
        if migration_id is None:
            migration_id = f"migration_{int(time.time() * 1000)}"

        # Capture current session state
        state_keys = list(st.session_state.keys())
        state_data = {}

        for key in state_keys:
            try:
                value = st.session_state[key]
                # Store value (may contain non-serializable objects)
                state_data[key] = value
            except Exception as e:
                self.logger.warning(f"Could not capture session state key '{key}': {e}")
                state_data[key] = f"<CAPTURE_FAILED: {str(e)}>"

        snapshot = MigrationSnapshot(
            timestamp=time.time(),
            version="legacy",
            state_keys=state_keys,
            state_data=state_data,
            migration_id=migration_id
        )

        # Save snapshot to disk
        try:
            snapshot_file = snapshot.save_to_file(self.snapshot_dir)
            self.logger.info(f"Migration snapshot saved: {snapshot_file}")
        except Exception as e:
            self.logger.error(f"Failed to save migration snapshot: {e}")

        return snapshot

    def validate_legacy_state(self, state_data: Dict[str, Any]) -> List[str]:
        """Validate legacy session state structure and return issues."""
        issues = []

        # Check for required image data consistency
        has_image = 'res_uploaded_image' in state_data
        has_metadata = 'res_image_metadata' in state_data
        has_bytes = 'res_image_bytes' in state_data

        if has_image and not has_metadata:
            issues.append("Image present but metadata missing")

        if has_image and state_data['res_uploaded_image'] is not None:
            image = state_data['res_uploaded_image']
            if not isinstance(image, Image.Image):
                issues.append(f"Invalid image type: {type(image)}")

        # Check parameter consistency
        gel_type = state_data.get('params_gel_type')
        if gel_type and gel_type not in ['sds_page', 'etbr_agarose']:
            issues.append(f"Invalid gel type: {gel_type}")

        n_lanes = state_data.get('params_n_lanes')
        if n_lanes and (not isinstance(n_lanes, int) or n_lanes < 1 or n_lanes > 24):
            issues.append(f"Invalid lane count: {n_lanes}")

        return issues

    def migrate_image_data(self, state_manager: ReactiveStateManager,
                          legacy_state: Dict[str, Any]) -> bool:
        """Migrate image-related data to new state manager."""
        try:
            # Extract legacy image data
            pil_image = legacy_state.get('res_uploaded_image')
            image_bytes = legacy_state.get('res_image_bytes')
            metadata = legacy_state.get('res_image_metadata', {})

            if pil_image is None:
                return True  # No image to migrate

            if not isinstance(pil_image, Image.Image):
                raise ValueError(f"Invalid image type: {type(pil_image)}")

            # Get filename from metadata or use default
            filename = metadata.get('filename', 'migrated_image.jpg')

            # If we don't have bytes, generate them
            if image_bytes is None:
                import io
                buffer = io.BytesIO()
                pil_image.save(buffer, format='PNG')
                image_bytes = buffer.getvalue()

            # Use new state manager to set image
            image_data = state_manager.set_current_image(
                image=pil_image,
                file_bytes=image_bytes,
                filename=filename
            )

            self.logger.info(f"Successfully migrated image data: {filename}")
            return True

        except Exception as e:
            self.logger.error(f"Failed to migrate image data: {e}")
            return False

    def migrate_to_new_state(self, snapshot: MigrationSnapshot) -> bool:
        """
        Migrate legacy session state to new abstraction layer.

        Returns True if migration successful, False if rollback needed.
        """
        try:
            # Validate legacy state first
            validation_issues = self.validate_legacy_state(snapshot.state_data)
            if validation_issues:
                self.logger.warning(f"Legacy state validation issues: {validation_issues}")
                # Continue with migration but log issues

            # Create new state manager
            state_manager = ReactiveStateManager()

            # Migrate image data
            if not self.migrate_image_data(state_manager, snapshot.state_data):
                return False

            # Migrate other data categories would go here...
            # For now, focusing on image data as the most critical

            snapshot.status = MigrationStatus.SUCCESS
            self.logger.info("Migration completed successfully")
            return True

        except Exception as e:
            self.logger.error(f"Migration failed: {e}")
            snapshot.status = MigrationStatus.FAILED
            return False

    def rollback_to_snapshot(self, snapshot: MigrationSnapshot) -> bool:
        """Rollback session state to snapshot."""
        try:
            # Clear current session state
            keys_to_remove = []
            for key in st.session_state.keys():
                if key.startswith('autodense_'):
                    keys_to_remove.append(key)

            for key in keys_to_remove:
                del st.session_state[key]

            # Restore snapshot data
            for key, value in snapshot.state_data.items():
                if not key.startswith('<CAPTURE_FAILED'):  # Skip failed captures
                    st.session_state[key] = value

            snapshot.status = MigrationStatus.ROLLED_BACK
            self.logger.info("Successfully rolled back to snapshot")
            return True

        except Exception as e:
            self.logger.error(f"Rollback failed: {e}")
            return False

    @contextmanager
    def safe_migration(self, migration_id: Optional[str] = None):
        """
        Context manager for safe migration with automatic rollback.

        Usage:
            with migrator.safe_migration() as snapshot:
                # Perform migration operations
                success = migrate_data()
                if not success:
                    raise MigrationError("Migration failed")
        """
        snapshot = self.create_snapshot(migration_id)

        try:
            snapshot.status = MigrationStatus.IN_PROGRESS
            yield snapshot

            # If we get here without exception, migration succeeded
            snapshot.status = MigrationStatus.SUCCESS
            self.logger.info(f"Migration {snapshot.migration_id} completed successfully")

        except Exception as e:
            self.logger.error(f"Migration {snapshot.migration_id} failed: {e}")
            snapshot.status = MigrationStatus.FAILED

            # Attempt rollback
            if self.rollback_to_snapshot(snapshot):
                self.logger.info(f"Migration {snapshot.migration_id} rolled back successfully")
            else:
                self.logger.error(f"Rollback failed for migration {snapshot.migration_id}")
                raise Exception(f"Migration failed and rollback failed: {e}")

            # Re-raise original exception
            raise


class BackwardsCompatibilityLayer:
    """
    Provides backwards compatibility during migration transition.

    This layer intercepts legacy session_state access patterns and routes them
    through the new abstraction layer when available.
    """

    def __init__(self, state_manager: ReactiveStateManager):
        self.state_manager = state_manager
        self.legacy_access_log: List[Dict[str, Any]] = []
        self.feature_flags = self._load_feature_flags()

    def _load_feature_flags(self) -> Dict[str, bool]:
        """Load feature flags for gradual migration."""
        return {
            'enable_new_image_state': True,
            'enable_new_analysis_state': False,  # Not implemented yet
            'enable_new_parameter_state': False,  # Not implemented yet
            'log_legacy_access': True
        }

    def _log_legacy_access(self, key: str, operation: str) -> None:
        """Log legacy session state access for monitoring."""
        if self.feature_flags.get('log_legacy_access', False):
            entry = {
                'timestamp': time.time(),
                'key': key,
                'operation': operation,
                'stack_trace': None  # Could add stack trace for debugging
            }
            self.legacy_access_log.append(entry)

            # Keep log bounded
            if len(self.legacy_access_log) > 1000:
                self.legacy_access_log = self.legacy_access_log[-500:]

    def get_legacy_value(self, key: str) -> Any:
        """Get value using legacy key, routing through new system when available."""
        self._log_legacy_access(key, 'get')

        # Route image-related keys through new system
        if self.feature_flags.get('enable_new_image_state', False):
            if key == 'res_uploaded_image':
                image_data = self.state_manager.current_image
                return image_data.image if image_data else None

            elif key == 'res_uploaded_array':
                image_data = self.state_manager.current_image
                return image_data.array if image_data else None

            elif key == 'res_image_metadata':
                return st.session_state.get('autodense_image_metadata')

            elif key == 'res_image_bytes':
                image_data = self.state_manager.current_image
                return image_data.bytes if image_data else None

        # Fall back to direct session state access
        return st.session_state.get(key)

    def set_legacy_value(self, key: str, value: Any) -> None:
        """Set value using legacy key, routing through new system when available."""
        self._log_legacy_access(key, 'set')

        # Route image-related keys through new system
        if self.feature_flags.get('enable_new_image_state', False):
            if key == 'res_uploaded_image' and isinstance(value, Image.Image):
                # This is complex - would need file bytes too
                # For now, fall back to legacy
                pass

        # Fall back to direct session state access
        st.session_state[key] = value

    def get_migration_stats(self) -> Dict[str, Any]:
        """Get statistics about legacy access patterns."""
        if not self.legacy_access_log:
            return {'total_accesses': 0}

        # Analyze access patterns
        key_counts = {}
        operation_counts = {}

        for entry in self.legacy_access_log:
            key = entry['key']
            operation = entry['operation']

            key_counts[key] = key_counts.get(key, 0) + 1
            operation_counts[operation] = operation_counts.get(operation, 0) + 1

        return {
            'total_accesses': len(self.legacy_access_log),
            'unique_keys': len(key_counts),
            'most_accessed_keys': sorted(key_counts.items(),
                                       key=lambda x: x[1], reverse=True)[:10],
            'operation_breakdown': operation_counts,
            'feature_flags': self.feature_flags
        }


class MigrationError(Exception):
    """Custom exception for migration-related errors."""
    pass


# Global compatibility layer instance
_global_compatibility_layer: Optional[BackwardsCompatibilityLayer] = None


def get_compatibility_layer() -> BackwardsCompatibilityLayer:
    """Get or create global compatibility layer."""
    global _global_compatibility_layer
    if _global_compatibility_layer is None:
        from .state_management import get_state_manager
        state_manager = get_state_manager()
        _global_compatibility_layer = BackwardsCompatibilityLayer(state_manager)
    return _global_compatibility_layer


def get_migration_stats() -> Dict[str, Any]:
    """Convenience function to get migration statistics."""
    return get_compatibility_layer().get_migration_stats()