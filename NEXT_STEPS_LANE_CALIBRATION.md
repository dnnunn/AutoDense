# Lane Calibration Fix - Implementation Plan

## Problem Summary
**CRITICAL ISSUE**: Frontend lane calibration and manual lane selection is being ignored/overwritten by all backend processing modes (AutoDense, ChatGPT, Manual).

### Root Cause Analysis
The issue occurs because:

1. **Available Data**: Frontend has comprehensive lane calibration in session state:
   - `st.session_state.get('calibration_lane1', 1)`
   - `st.session_state.get('calibration_lane2', min(n_lanes, 12))`
   - `st.session_state.res_lane_boundaries` (calculated boundaries from lines 1074, 1976, 2138, etc.)

2. **Missing Data Flow**: Backend calls at lines 2065 and 2273 only pass basic parameters without lane calibration information:
   - **Line 2065**: `_try_run_ad_band_assist(st.session_state.res_uploaded_image, params)`
   - **Line 2273**: `_try_run_ad_band_assist(img_for_pipeline, ba_params)`

3. **Impact**: User's manual calibration is completely ignored, causing backend to auto-detect lanes instead of using user-defined boundaries.

## Implementation Plan

### Phase 1: Modify Function Signature ⏳ PENDING
**File**: `ui/streamlit_autodense_app.py:326`

1. **Update `_try_run_ad_band_assist()` signature** to accept optional lane calibration:
   ```python
   def _try_run_ad_band_assist(
       pil_img: Image.Image,
       params: Dict[str, Any],
       lane_boundaries: Optional[List] = None
   ) -> Optional[Dict[str, Any]]:
   ```

2. **Add lane boundary processing** inside the function (lines 326-444) to include calibration data in backend parameters when available.

### Phase 2: Create Helper Function ⏳ PENDING
**File**: `ui/streamlit_autodense_app.py` (new function)

1. **Create `get_current_lane_calibration()`** helper function:
   ```python
   def get_current_lane_calibration():
       """Extract current lane calibration from session state."""
       return {
           'lane_boundaries': st.session_state.res_lane_boundaries,
           'calibration_lane1': st.session_state.get('calibration_lane1'),
           'calibration_lane2': st.session_state.get('calibration_lane2'),
           'has_manual_calibration': bool(st.session_state.res_lane_boundaries)
       }
   ```

### Phase 3: Update Function Calls ⏳ PENDING
**File**: `ui/streamlit_autodense_app.py`

1. **Line 2065** - Update first call site:
   ```python
   # OLD:
   res = _try_run_ad_band_assist(st.session_state.res_uploaded_image, params)

   # NEW:
   lane_calibration = get_current_lane_calibration()
   res = _try_run_ad_band_assist(
       st.session_state.res_uploaded_image,
       params,
       lane_boundaries=lane_calibration['lane_boundaries']
   )
   ```

2. **Line 2273** - Update second call site:
   ```python
   # OLD:
   res_ba = _try_run_ad_band_assist(img_for_pipeline, ba_params)

   # NEW:
   lane_calibration = get_current_lane_calibration()
   res_ba = _try_run_ad_band_assist(
       img_for_pipeline,
       ba_params,
       lane_boundaries=lane_calibration['lane_boundaries']
   )
   ```

### Phase 4: Backend Parameter Translation ⏳ PENDING
**File**: `ui/utils/parameter_management.py`

1. **Enhance `create_safe_ad_params()`** to include lane boundary information:
   ```python
   def create_safe_ad_params(ui_params, lane_boundaries=None):
       # Existing parameter translation...

       # Add lane calibration to backend parameters
       if lane_boundaries:
           # TODO: Determine correct backend format for lane constraints
           ad_params.lane_boundaries = lane_boundaries
   ```

### Phase 5: Backend Integration 🔍 INVESTIGATION REQUIRED
**Target**: `scripts/autodense/orchestrator/pipeline.py`

**CRITICAL RESEARCH NEEDED**:

1. **Investigate backend `run()` function** to understand:
   - How to pass lane boundary constraints to `run(image: Path, p: Params, retries: int=1, use_llm: bool=False)`
   - What format does the Params dataclass expect for lane information?
   - Does backend support overriding automatic lane detection with manual boundaries?

2. **Check Params dataclass structure**:
   - Look for existing lane boundary parameters
   - Understand how to inject manual calibration data
   - Determine if new fields need to be added

3. **Update parameter passing** in `_try_run_ad_band_assist()` to include lane constraints in backend calls.

### Phase 6: Testing Strategy ⏳ PENDING

**Test Cases**:
1. **Manual Calibration Mode**:
   - Upload gel image
   - Set manual lane calibration points (lines 1049-1630 UI)
   - Run AutoDense analysis → verify lane boundaries respected

2. **ChatGPT Mode**:
   - Same image with manual calibration
   - Run AI preprocessing → verify calibration preserved

3. **All Processing Modes**:
   - Manual detection
   - AutoDense detection
   - AI-assisted detection
   - Verify in each mode that user's lane calibration overrides automatic detection

### Phase 7: Validation & Error Handling ⏳ PENDING

1. **Add validation** for lane boundary compatibility with backend
2. **Add user feedback** when manual calibration is being applied
3. **Add fallback behavior** if lane boundaries cannot be applied
4. **Add logging** for calibration data flow debugging

## Current Status

✅ **COMPLETED**:
- Analyzed lane calibration data flow from frontend to backend
- Identified root cause: backend calls don't include lane calibration data
- Created comprehensive implementation plan

⏳ **PENDING**:
- Function signature modification
- Helper function creation
- Call site updates
- Backend parameter translation
- Backend integration research
- Testing implementation

## Key Files to Modify

### Primary Changes
1. **`ui/streamlit_autodense_app.py`**:
   - Lines 326-444: `_try_run_ad_band_assist()` function signature and implementation
   - Line 2065: First function call site (AutoDense mode)
   - Line 2273: Second function call site (ChatGPT mode)
   - New helper function for calibration extraction

2. **`ui/utils/parameter_management.py`**:
   - `create_safe_ad_params()` function enhancement
   - Add lane boundary parameter handling

### Investigation Required
3. **`scripts/autodense/orchestrator/pipeline.py`**:
   - Backend `run()` function parameter format
   - Params dataclass structure for lane constraints
   - How backend processes lane boundary overrides

## Success Criteria

- [ ] User's manual lane calibration is preserved across all processing modes
- [ ] Backend processing respects frontend lane boundaries instead of auto-detecting
- [ ] No regression in existing functionality for users without manual calibration
- [ ] Clear user feedback when manual calibration is being applied
- [ ] Comprehensive testing across all analysis modes

## Next Session Action Items

1. **START WITH**: Research backend parameter format for lane boundaries
2. **THEN**: Implement Phase 1 function signature changes
3. **FOLLOW**: Execute phases 2-3 for call site updates
4. **TEST**: Validate calibration preservation across all modes

## Session State Variables Reference

**Lane Calibration Data Available**:
- `st.session_state.get('calibration_lane1', 1)` - First calibration lane number
- `st.session_state.get('calibration_lane2', min(n_lanes, 12))` - Second calibration lane number
- `st.session_state.res_lane_boundaries` - Calculated lane boundary positions
- Lines with calibration logic: 1049-1630 (UI), 1074, 1976, 2138, 2396, 2471, 2573, 2653, 3072

**Critical Call Sites**:
- Line 2065: AutoDense processing mode
- Line 2273: ChatGPT/AI processing mode

---
*Generated: 2025-01-15*
*Priority: HIGH - User experience critical issue*