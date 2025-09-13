"""AutoDense UI Components.

This module contains extracted UI components for better organization and maintainability.
All components follow the established pattern of accepting parameters and using session state
for persistence while maintaining AutoDense's scientific workflow integrity.
"""

# Import only existing components
from .image_upload import render_image_upload

__all__ = [
    'render_image_upload'
]

# TODO: Add imports as components are created:
# from .prerequisites_status import render_prerequisites_status
# from .calibration_instructions import render_calibration_instructions