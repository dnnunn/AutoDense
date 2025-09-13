"""AutoDense UI Components.

This module contains extracted UI components for better organization and maintainability.
All components follow the established pattern of accepting parameters and using session state
for persistence while maintaining AutoDense's scientific workflow integrity.
"""

# Import all available components
from .image_upload import render_image_upload
from .prerequisites_panel import render_prerequisites_panel
from .calibration_instructions import render_calibration_instructions

__all__ = [
    'render_image_upload',
    'render_prerequisites_panel',
    'render_calibration_instructions'
]