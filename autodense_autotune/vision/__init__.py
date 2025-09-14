"""
Phase IV Vision-Assist System for AutoDense

Provides bounded visual parameter optimization with privacy protection,
safety validation, and intelligent trigger logic.
"""

from .cropper import VisionCropper
from .allowlist import AllowlistValidator, create_default_allowlist
from .gating import VisionGate, TriggerReason, create_default_gate_config
from .mock_advisor import MockVisionAdvisor

__version__ = "1.0.0"
__all__ = [
    "VisionCropper",
    "AllowlistValidator", 
    "create_default_allowlist",
    "VisionGate",
    "TriggerReason",
    "create_default_gate_config",
    "MockVisionAdvisor"
]