# Parameter Translation System for AutoDense

> **Doc Meta**
> - **Purpose:** Documents the parameter translation layer that safely maps UI parameters to backend-compatible parameters
> - **Scope:** Covers translation mappings, validation rules, user feedback, and integration details
> - **Owner:** @claude-code
> - **Last-verified:** 2025-09-13

## Overview

The Parameter Translation System solves the critical issue where UI parameters (`gel_type`, `conf_threshold`, `mw_lane`) were incompatible with the backend `Params` dataclass, causing `TypeError` exceptions. This system provides safe parameter mapping without modifying the existing backend code.

## Problem Solved

**Original Error Scenario:**
```python
# UI passes these parameters:
ui_params = {
    "gel_type": "sds_page",        # ❌ Backend expects "modality"
    "conf_threshold": 0.30,        # ❌ Backend expects "ladder_min_score"
    "mw_lane": 1                   # ❌ Backend ignores this completely
}

# This caused TypeError when constructing ADParams(**ui_params)
```

**Solution:**
The translation layer safely converts UI parameters to backend-compatible format with clear user feedback.

## Translation Mappings

### 1. Gel Type → Modality
| UI Parameter | Backend Parameter | Notes |
|-------------|------------------|--------|
| `gel_type: "sds_page"` | `modality: "sds"` | Protein gel analysis |
| `gel_type: "etbr_agarose"` | `modality: "dna"` | DNA gel analysis |
| `gel_type: <unknown>` | `modality: "sds"` | Default fallback with warning |

### 2. Confidence Threshold → Ladder Min Score
| UI `conf_threshold` | Backend `ladder_min_score` | Interpretation |
|--------------------|---------------------------|----------------|
| ≥ 0.8 | 0.45 | Very strict detection |
| ≥ 0.6 | 0.40 | Conservative detection |
| ≥ 0.4 | 0.35 | Standard detection |
| ≥ 0.2 | 0.30 | Permissive detection |
| < 0.2 | 0.25 | Very permissive detection |

### 3. MW Lane Handling
- **UI Parameter:** `mw_lane: <integer>`
- **Backend Handling:** Parameter is **ignored** with explanation
- **Rationale:** Backend uses automatic ladder detection instead of manual lane specification

### 4. Pass-Through Parameters
Known backend parameters are passed through unchanged:
- `min_lanes`, `max_lanes`, `comb`
- `num_ladders`, `ladder_min_bands`
- `bg_radius`, `invert`

## Core Functions

### `translate_ui_to_backend_params(ui_params)`
**Purpose:** Core translation logic
**Returns:** `(backend_params_dict, translation_log)`

```python
ui_params = {"gel_type": "sds_page", "conf_threshold": 0.6, "mw_lane": 1}
backend_params, log = translate_ui_to_backend_params(ui_params)

# Result:
# backend_params = {"modality": "sds", "ladder_min_score": 0.40}
# log = [
#     "✅ gel_type 'sds_page' → modality 'sds'",
#     "✅ conf_threshold 0.60 → ladder_min_score 0.40",
#     "ℹ️ mw_lane 1 → ignored (backend uses automatic ladder detection)"
# ]
```

### `validate_and_explain_params(ui_params)`
**Purpose:** Validation with user-friendly explanations
**Returns:** `(is_valid, backend_params, explanations, errors)`

```python
is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

if is_valid:
    # Use backend_params safely
    # Show explanations to user
else:
    # Display errors to user
```

### `create_safe_ad_params(ui_params)`
**Purpose:** Safe ADParams construction with error handling
**Returns:** `(ad_params_or_none, explanation_text, error_text)`

## User Feedback System

### Success Case
```
✅ Parameters validated successfully

**Parameter Translation:**
✅ gel_type 'sds_page' → modality 'sds'
✅ conf_threshold 0.65 → ladder_min_score 0.40
ℹ️ mw_lane 2 → ignored (backend uses automatic ladder detection)
✅ min_lanes 6 → passed through

**Final Backend Parameters:**
• modality: sds
• ladder_min_score: 0.4
• min_lanes: 6
```

### Error Case
```
❌ Parameter validation failed:
• conf_threshold must be between 0.0 and 1.0, got -0.5
• mw_lane must be a positive integer, got 0
```

## Integration Points

### Streamlit App Integration

**Before (Problematic):**
```python
# This could cause TypeError
ad_params = ADParams(**kwargs)
```

**After (Safe):**
```python
# Use translation layer
ad_params, explanation, error = create_safe_ad_params(kwargs)
if error:
    out["errors"].append(f"Parameter translation failed: {error}")
if explanation:
    out["parameter_translation"] = explanation
```

### UI Parameter Collection
```python
# Collect parameters in internal format for translation
params = {
    "gel_type": st.session_state.params_gel_type,  # "sds_page" or "etbr_agarose"
    "conf_threshold": float(st.session_state.params_conf_threshold),
    "mw_lane": int(st.session_state.params_mw_lane),
}

# Show translation preview to user
with st.expander("🔧 Parameter Translation Details", expanded=False):
    is_valid, backend_params, explanations, errors = validate_and_explain_params(params)
    # Display feedback to user
```

## Validation Rules

### Required Validations
1. **conf_threshold:** Must be in range [0.0, 1.0]
2. **mw_lane:** Must be positive integer if provided
3. **gel_type:** Must be 'sds_page' or 'etbr_agarose' if provided

### Error Handling
- **Invalid parameters:** Clear error messages with specific issues
- **Unknown parameters:** Warnings but not errors
- **Missing parameters:** Graceful defaults where possible

## Testing

### Test Coverage
- ✅ All translation mappings
- ✅ Boundary condition handling
- ✅ Validation error cases
- ✅ Original TypeError prevention
- ✅ User feedback generation

### Running Tests
```bash
# Comprehensive test suite
python3 test_comprehensive_translation.py

# Simple standalone test
python3 parameter_translation_standalone.py
```

## Deployment Checklist

### ✅ Implementation Complete
- [x] Core translation functions implemented
- [x] Validation and error handling
- [x] User feedback system
- [x] Streamlit app integration
- [x] Comprehensive test suite

### ✅ Success Criteria Met
- [x] No TypeError when UI passes gel_type, conf_threshold, mw_lane
- [x] Parameters intelligently translated to backend equivalents
- [x] Users receive clear feedback about parameter handling
- [x] All existing functionality continues to work
- [x] Comprehensive test coverage

## Backward Compatibility

The translation system is **fully backward compatible**:
- Existing workflows continue to work unchanged
- Backend `Params` dataclass is not modified
- Additional safety layer with no breaking changes
- Clear upgrade path for parameter handling

## Future Enhancements

1. **Dynamic Mapping:** Load translation rules from configuration
2. **Advanced Validation:** Context-aware parameter validation
3. **User Learning:** Remember user preferences for parameter mappings
4. **Performance Optimization:** Cache translation results for repeated calls

## Troubleshooting

### Common Issues

**Issue:** Translation explanations not showing in UI
**Solution:** Check that `validate_and_explain_params()` is called and explanations are displayed

**Issue:** Backend still receiving unknown parameters
**Solution:** Verify `create_safe_ad_params()` is used instead of direct `ADParams()` construction

**Issue:** Validation too strict for edge cases
**Solution:** Review validation rules in `validate_and_explain_params()` and adjust ranges

## Support

For issues with the parameter translation system:
1. Run the test suite to verify functionality
2. Check the translation logs for parameter handling details
3. Review the user feedback messages for validation errors
4. Consult this documentation for expected behavior