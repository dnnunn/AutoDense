"""Type definitions for AutoDense UI components.

This module contains common type aliases and utility types used throughout
the AutoDense user interface to ensure consistent type safety.
"""

from typing import Dict, Any, Tuple, Optional, List, Union, Callable, TypeAlias
import numpy as np
from PIL import Image

# Common session state data types
ImageMetadata: TypeAlias = Dict[str, Any]
LaneBoundaries: TypeAlias = List[Any]  # Replace with more specific type when structure is known
SessionStateData: TypeAlias = Dict[str, Any]

# Image processing types
ImageArray: TypeAlias = np.ndarray
ImageDimensions: TypeAlias = Tuple[int, int]
ImageTuple: TypeAlias = Tuple[Image.Image, ImageArray, ImageMetadata]

# Analysis result types
AnalysisResult: TypeAlias = Dict[str, Any]
BandData: TypeAlias = Dict[str, Any]
LaneData: TypeAlias = Dict[str, Any]

# UI interaction types
TabNavigationFunction: TypeAlias = Callable[[str], None]
ErrorCallback: TypeAlias = Callable[[str], None]

# Parameter types
ParameterDict: TypeAlias = Dict[str, Union[str, int, float, bool]]
ConfigDict: TypeAlias = Dict[str, Any]

# File handling types
FileBytes: TypeAlias = bytes
FileHash: TypeAlias = str

# Preprocessing types
PreprocessingMode: TypeAlias = Union[str, None]  # "AI", "ChatGPT", "Manual", "Raw", etc.
PreprocessingResult: TypeAlias = Tuple[Union[ImageArray, Image.Image], Dict[str, Any]]

# Analysis pipeline types
PipelineParams: TypeAlias = Dict[str, Any]
PipelineResult: TypeAlias = Dict[str, Any]

# Session management types
SessionKey: TypeAlias = str
SessionValue: TypeAlias = Any

# Type guards and validation types
ValidationResult: TypeAlias = Tuple[bool, Optional[str]]  # (is_valid, error_message)