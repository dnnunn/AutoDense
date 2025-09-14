# autodense/recipes/ops.py
from typing import Dict, Any, List, Optional
import numpy as np
import pandas as pd
from dataclasses import dataclass

@dataclass
class OperationResult:
    """Result of a recipe operation"""
    success: bool
    data: Any
    message: str = ""
    warnings: List[str] = None

    def __post_init__(self):
        if self.warnings is None:
            self.warnings = []

class SafeOps:
    """Safe, typed operations for custom analytical workflows"""
    
    @staticmethod
    def normalize_to_control(values: List[float], control_idx: int = 0) -> OperationResult:
        """Normalize values to a control (typically first lane)"""
        try:
            if not values or control_idx >= len(values):
                return OperationResult(False, None, "Invalid control index")
            
            control_val = values[control_idx]
            if control_val <= 0:
                return OperationResult(False, None, "Control value must be positive")
            
            normalized = [v / control_val for v in values]
            return OperationResult(True, normalized, f"Normalized to control (lane {control_idx})")
        except Exception as e:
            return OperationResult(False, None, f"Normalization failed: {str(e)}")

    @staticmethod
    def fold_change(treated: List[float], control: List[float]) -> OperationResult:
        """Calculate fold changes between treated and control groups"""
        try:
            if len(treated) != len(control):
                return OperationResult(False, None, "Treated and control groups must have same length")
            
            fold_changes = []
            warnings = []
            
            for i, (t, c) in enumerate(zip(treated, control)):
                if c <= 0:
                    warnings.append(f"Control value {i} is non-positive, skipping")
                    fold_changes.append(np.nan)
                else:
                    fold_changes.append(t / c)
            
            return OperationResult(True, fold_changes, "Fold changes calculated", warnings)
        except Exception as e:
            return OperationResult(False, None, f"Fold change calculation failed: {str(e)}")

    @staticmethod
    def outlier_detection(values: List[float], method: str = "iqr", threshold: float = 1.5) -> OperationResult:
        """Detect outliers using IQR or Z-score methods"""
        try:
            arr = np.array(values)
            arr_clean = arr[~np.isnan(arr)]
            
            if len(arr_clean) < 3:
                return OperationResult(False, None, "Need at least 3 non-NaN values for outlier detection")
            
            if method == "iqr":
                q1, q3 = np.percentile(arr_clean, [25, 75])
                iqr = q3 - q1
                lower_bound = q1 - threshold * iqr
                upper_bound = q3 + threshold * iqr
                outliers = [(i, v) for i, v in enumerate(values) if v < lower_bound or v > upper_bound]
            elif method == "zscore":
                mean_val = np.mean(arr_clean)
                std_val = np.std(arr_clean)
                outliers = [(i, v) for i, v in enumerate(values) if abs((v - mean_val) / std_val) > threshold]
            else:
                return OperationResult(False, None, f"Unknown outlier method: {method}")
            
            return OperationResult(True, outliers, f"Found {len(outliers)} outliers using {method}")
        except Exception as e:
            return OperationResult(False, None, f"Outlier detection failed: {str(e)}")

    @staticmethod
    def log_transform(values: List[float], base: float = 2.0) -> OperationResult:
        """Apply logarithmic transformation"""
        try:
            transformed = []
            warnings = []
            
            for i, v in enumerate(values):
                if v <= 0:
                    warnings.append(f"Value {i} is non-positive, setting to NaN")
                    transformed.append(np.nan)
                else:
                    transformed.append(np.log(v) / np.log(base))
            
            return OperationResult(True, transformed, f"Log{base} transformation applied", warnings)
        except Exception as e:
            return OperationResult(False, None, f"Log transformation failed: {str(e)}")

    @staticmethod
    def statistical_summary(values: List[float]) -> OperationResult:
        """Generate statistical summary"""
        try:
            arr = np.array(values)
            arr_clean = arr[~np.isnan(arr)]
            
            if len(arr_clean) == 0:
                return OperationResult(False, None, "No valid values for statistics")
            
            summary = {
                "count": len(arr_clean),
                "mean": np.mean(arr_clean),
                "median": np.median(arr_clean),
                "std": np.std(arr_clean, ddof=1) if len(arr_clean) > 1 else 0,
                "min": np.min(arr_clean),
                "max": np.max(arr_clean),
                "cv_percent": (np.std(arr_clean, ddof=1) / np.mean(arr_clean)) * 100 if len(arr_clean) > 1 and np.mean(arr_clean) != 0 else 0
            }
            
            return OperationResult(True, summary, "Statistical summary generated")
        except Exception as e:
            return OperationResult(False, None, f"Statistical summary failed: {str(e)}")

    @staticmethod
    def quality_filter(values: List[float], min_val: Optional[float] = None, 
                      max_val: Optional[float] = None, max_cv: Optional[float] = None) -> OperationResult:
        """Filter values based on quality criteria"""
        try:
            filtered_indices = []
            warnings = []
            
            for i, v in enumerate(values):
                if np.isnan(v):
                    warnings.append(f"Value {i} is NaN, filtering out")
                    continue
                
                if min_val is not None and v < min_val:
                    warnings.append(f"Value {i} below minimum ({v} < {min_val})")
                    continue
                    
                if max_val is not None and v > max_val:
                    warnings.append(f"Value {i} above maximum ({v} > {max_val})")
                    continue
                
                filtered_indices.append(i)
            
            filtered_values = [values[i] for i in filtered_indices]
            
            # Check CV if requested
            if max_cv is not None and len(filtered_values) > 1:
                cv = (np.std(filtered_values, ddof=1) / np.mean(filtered_values)) * 100
                if cv > max_cv:
                    warnings.append(f"CV too high: {cv:.1f}% > {max_cv}%")
            
            # Return filtered values as primary result for pipeline compatibility
            # Additional metadata available in warnings/message
            message = f"Quality filter kept {len(filtered_values)}/{len(values)} values (filtered {len(values) - len(filtered_values)} values)"
            warnings.append(f"Kept indices: {filtered_indices}")
            
            return OperationResult(True, filtered_values, message, warnings)
        except Exception as e:
            return OperationResult(False, None, f"Quality filtering failed: {str(e)}")