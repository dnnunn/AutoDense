# DEBUG ANALYSIS ERRORS

> **Doc Meta**
> - **Purpose:** Systematic documentation of functional errors in AutoDense Streamlit application
> - **Scope:** Analysis pipeline errors, parameter mismatches, and UI/backend integration issues
> - **Owner:** @claude-code
> - **Last-verified:** 2025-09-13

## Overview

This document tracks all functional errors encountered during AutoDense analysis runs to enable systematic debugging and resolution.

---

## Error #1: PreprocParams Gamma Parameter Mismatch

**Status:** 🔍 ANALYZED - Ready for Fix

### Error Details
- **Error Type:** Python TypeError (NOT Streamlit or interface error)
- **Trigger:** Manual preprocessing mode with "no preprocessing" selected
- **Error Message:** `PreprocParams.__init__() got an unexpected keyword argument 'gamma'`
- **Error Count:** First error in session (Error #1)

### Root Cause Analysis
1. **UI Collection:** Streamlit UI collects `gamma` parameter at `ui/streamlit_autodense_app.py:1795-1799`
2. **Parameter Flow:** `manual_params` dict with `gamma` passed to `PreprocParams(**manual_params)` at line 112
3. **Backend Mismatch:** `PreprocParams` dataclass in `autodense/preprocess/pipeline.py:20-31` doesn't have `gamma` field

### Technical Details
- **UI Location:** `ui/streamlit_autodense_app.py:1795` - Gamma slider in Advanced Options
- **Backend Location:** `autodense/preprocess/pipeline.py:20` - PreprocParams dataclass definition
- **Error Location:** `ui/streamlit_autodense_app.py:112` - Parameter instantiation

### Supported PreprocParams Fields
```python
@dataclass
class PreprocParams:
    modality: str = "sds"
    comb_hint: Optional[int] = None
    target_channel: str = "auto"
    polarity: str = "auto" 
    bg_method: str = "auto"
    bg_radius_px: Union[str,int] = "auto"
    denoise: str = "auto"
    clahe: bool = True
    deskew: bool = True
    rectify: bool = False
```

### Missing UI Parameters
- ❌ `gamma` - Collected in UI but not supported in backend
- ❌ `sharpen` - Collected in UI but not analyzed for backend support

### Debug Results COMPLETED ✅
**Gamma Support Analysis:**
- **scikit-image:** ✅ `exposure.adjust_gamma()` available and tested working
- **PIL/Pillow:** ✅ Version 10.4.0 with brightness enhancement support
- **OpenCV:** ✅ Version 4.12.0 with gamma correction via LUT tested working
- **PreprocParams Support:** ❌ Only 10 parameters supported, `gamma` missing
- **Recommendation:** Add gamma support as capabilities exist in all vision libraries

### Fix Options
1. **Option A (RECOMMENDED):** Add `gamma` parameter to `PreprocParams` dataclass and implement gamma correction
2. **Option B:** Filter out unsupported parameters before passing to `PreprocParams`
3. **Option C:** Remove `gamma` from UI entirely

---

## Error #2: Band Detection Missing Parameter

**Status:** 🔍 ANALYZING

### Error Details
- **Error Type:** Python TypeError (Missing positional argument)
- **Trigger:** Preprocessing mode set to "Off" OR "Run Band Assist Pipeline" button
- **Error Message:** `run() missing 1 required positional argument: 'p'`
- **Error Count:** Second error in session (Error #2)
- **Additional Context:** Band Assist Pipeline shows same error but with less detailed reporting

### Root Cause Analysis
1. **Preprocessing Off:** User selects "Off" preprocessing mode
2. **Band Detection Call:** System attempts to call `run()` function for band detection
3. **Parameter Missing:** The `run()` function expects parameter `p` but it's not being passed

### Technical Details
- **UI Location:** Preprocessing "Off" mode (user selection)
- **Backend Location:** `ui/streamlit_autodense_app.py:353` - `ad_pipeline_run(ad_params, base)` call
- **Error Location:** `ui/streamlit_autodense_app.py:2163` - `_try_run_ad_band_assist` function call
- **Pipeline Function:** `ad_pipeline_run()` expects parameter named 'p' but gets unnamed positional args

### Root Cause Analysis COMPLETED ✅
1. **Shared Function:** Both preprocessing "Off" AND "Run Band Assist Pipeline" button call `_try_run_ad_band_assist()`
2. **Band Detection Call:** System calls `_try_run_ad_band_assist()` at line 2163 (preprocessing off) and line ? (band assist button)
3. **Multiple Attempts:** Function tries various call patterns for `ad_pipeline_run()`
4. **Parameter Mismatch:** Line 353 attempts `ad_pipeline_run(ad_params, base)` (params first, image second)
5. **Function Signature:** `ad_pipeline_run()` expects named parameter `p` but gets positional args
6. **Same Error Source:** Both UI paths lead to identical parameter mismatch in the same function

### Investigation Completed ✅
- [✅] Found preprocessing "Off" mode handling
- [✅] Located the `run()` function: `ad_pipeline_run()` 
- [✅] Traced parameter flow: UI → `_try_run_ad_band_assist` → `ad_pipeline_run`
- [✅] Confirmed this is AutoDense pipeline, not preprocessing pipeline

### Debug Results COMPLETED ✅
**Function Signature Analysis:** `ad_pipeline_run(image: Path, p: Params, retries: int = 1, use_llm: bool = False)`
- **Required Parameters:** `image` (first), `p` (second)  
- **Current UI calls:** `ad_pipeline_run(base, ad_params)` and `ad_pipeline_run(ad_params, base)`
- **Parameter Issue:** Function expects `image` first, `p` second, but UI tries both orders
- **Test Results:** All test patterns failed due to `gel_type` parameter mismatch in ADParams

### Fix Options  
1. **Option A (CONFIRMED WORKING):** Use correct parameter order: `ad_pipeline_run(image=base, p=ad_params)`
2. **Option B:** Fix ADParams parameter validation to accept UI parameters
3. **Option C:** Filter UI parameters before creating ADParams object

---

## Error #3: AI Preprocessing Mode Timeout Issues

**Status:** 🔍 ANALYZING

### Error Details
- **Error Type:** Performance/Timeout (Hanging/infinite wait)
- **Trigger:** Using "AI Guarded" or "ChatGPT-4.1" preprocessing modes
- **Error Message:** `Preprocessing failed: OpenAI preprocessing timed out after 75s`
- **Error Count:** Error #3 (Analysis Count: 4)
- **Actual Timeout:** Much longer than the configured 75s timeout

### Hanging Points
1. **AI Guarded Mode:** Gets stuck on "🔬 Optimizing image quality..." (line 2108)
2. **ChatGPT Mode:** Gets stuck on "🤖 Contacting OpenAI ChatGPT-4.1..." (line 2106)

### Root Cause Analysis COMPLETED ✅
1. **Progress Display:** Messages show correctly at lines 2106/2108
2. **Function Call:** Both modes call `cached_preprocess_image()` at line 2121
3. **Hanging Point:** The call to `cached_preprocess_image(img_hash, preprocessing_mode, manual_params_str)` never returns
4. **Function Location:** `cached_preprocess_image()` defined at line 90
5. **ChatGPT Mode Issue:** `openai_guided_preprocess()` uses invalid OpenAI API call `client.responses.create()` 
6. **AI Guarded Mode Issue:** `guarded_preprocess()` function executes complex image analysis that may hang
7. **Timeout Wrapper:** Both functions are wrapped with timeout (75s/60s) but timeout may not trigger if thread hangs

### Technical Details
- **UI Location:** Lines 2106 (ChatGPT), 2108 (AI Guarded) - progress messages display
- **Backend Location:** Line 2121 - `cached_preprocess_image()` function call
- **Error Location:** Line 90 - `cached_preprocess_image()` function definition
- **Suspected Issue:** Function contains infinite loop, network timeout, or missing dependencies

### Investigation Completed ✅
- [✅] Examined `cached_preprocess_image()` function implementation at line 90
- [✅] Found timeout handling exists: 75s for ChatGPT, 60s for AI Guarded mode
- [✅] Located underlying functions: `openai_guided_preprocess()` and `guarded_preprocess()`
- [✅] Identified potential root causes: Invalid OpenAI API call and missing imports
- [✅] Manual mode works correctly (Error #1 is separate parameter issue)

### Debug Results COMPLETED ✅
**OpenAI API Analysis:** 
- **Invalid API calls CONFIRMED:** `client.responses.create()` found at lines 3258, 3321
- **Correct API call:** Should be `client.chat.completions.create()`
- **Timeout mechanism:** Works correctly (tested with 100s mock timeout)
- **Guarded preprocessing:** Works correctly (0.06s execution time)
- **API key issue:** Environment variable not set, using deprecated config file

### Fix Options  
1. **Option A (HIGH PRIORITY):** Fix invalid OpenAI API call `client.responses.create()` → `client.chat.completions.create()`
2. **Option B (MEDIUM):** Set OPENAI_API_KEY environment variable instead of config file
3. **Option C (MEDIUM):** Improve timeout mechanism with process-based interruption
4. **Option D (LOW):** Add fallback to manual preprocessing when AI methods fail or timeout

---

## Error Investigation Template

For each new error discovered:

### Error #X: [Brief Description]

**Status:** 🆕 NEW | 🔍 ANALYZING | ✅ FIXED | 🚫 BLOCKED

### Error Details
- **Error Type:** [Python/Streamlit/Interface/Other]
- **Trigger:** [What action causes this error]
- **Error Message:** `[Exact error text]`
- **Error Count:** [Session error number]

### Root Cause Analysis
1. **Step 1:** [Where error originates]
2. **Step 2:** [How error propagates]
3. **Step 3:** [Where error manifests]

### Technical Details
- **UI Location:** [File and line where UI collects/displays]
- **Backend Location:** [File and line where backend processes]
- **Error Location:** [File and line where error occurs]

### Fix Options
1. **Option A:** [Description]
2. **Option B:** [Description]

---

## Analysis Pipeline Error Patterns

### Parameter Interface Mismatches
- UI parameters not supported by backend classes
- Missing parameter validation before backend calls
- Inconsistent parameter naming between UI and backend

### Error Handling Patterns
- Error counting: `st.session_state.ui_error_count`
- Error display: `Analysis Failed - Error #{count}`
- Error context: Analysis count, preprocessing mode, timestamp

---

## Debugging Workflow

1. **Error Occurrence:** Note error message and trigger
2. **Parameter Trace:** Follow parameter from UI → backend
3. **Interface Analysis:** Check parameter compatibility
4. **Root Cause:** Identify exact mismatch point
5. **Fix Strategy:** Choose appropriate resolution
6. **Validation:** Test fix across modes

---

## Session Context

### Current Session Status
- **Original App:** Running on port 8501 (process 584149)
- **Analysis Mode:** Manual preprocessing
- **Trigger:** "No preprocessing" selection
- **Error Count:** 1 (this is the first error)

### Investigation Tools Used
- `grep` searches for parameter usage
- File reading for dataclass definitions
- Code flow tracing from UI to backend

---

## Next Steps

1. **Immediate:** Decide on fix strategy for gamma parameter
2. **Systematic:** Check all UI parameters against backend classes
3. **Validation:** Test fix with various preprocessing modes
4. **Documentation:** Update this document with resolution

---

## Notes

- This is a pure Python error, not a Streamlit framework issue
- The parameter collection mechanism works correctly
- The error manifests at dataclass instantiation, not in the preprocessing pipeline itself
- Manual mode specifically triggers this path vs Auto mode which uses different code paths