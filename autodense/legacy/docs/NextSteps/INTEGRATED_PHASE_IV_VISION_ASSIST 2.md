# Integrated Phase IV: Vision-Assist Hybrid Optimization

> **Doc Meta**
> - **Purpose:** Integration plan for Vision-Assist mode into AutoDense phased approach
> - **Scope:** Complete Phase IV implementation building on Phases I-III foundation
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 🎯 Phase IV: Vision-Assist Hybrid Mode

**Building on our successful Phases I-III foundation:**
- ✅ **Phase I**: Truth preservation system with dual reporting (raw vs reconciled)
- ✅ **Phase II**: Comprehensive telemetry system (flight recorder diagnostics)
- ✅ **Phase III**: Parameter optimization with configuration independence
- 🚀 **Phase IV**: Vision-Assist hybrid mode with limited pixel privileges

### **Core Principle: Gemini Advises, AutoDense Measures**

The Vision-Assist mode maintains our deterministic measurement pipeline while adding intelligent visual parameter optimization for edge cases.

---

## 🔴 CRITICAL FIXES COMPLETED (Auditor Requirements)

### ✅ **1. Band/Lane Parameter Independence**
- **Fixed**: Band baselines now use `win=2px` (band config) vs `win=15px` (lane config)
- **Verification**: Logs show `[BASELINE_FIX] SUCCESS: Using bands.baseline parameters`
- **Result**: 24 bands detected vs 0-2 previously with proper baseline separation

### ✅ **2. --no-exit Flag Requirements**
- **Fixed**: Added bright red box warnings to DEFINITIVE_BUILD_REFERENCE.md
- **Fixed**: Updated java_bridge.py to automatically include --no-exit in ALL CLI calls
- **Added**: Header warnings in java_bridge.py about optimization loop death
- **Result**: Python-Java bridge now optimizer-safe

---

## 📋 Vision-Assist Architecture Overview

### **Unified Configuration Schema Extension**

Add to existing YAML configs under same top-level as `detect` and `bands`:

```yaml
vision:
  mode: "off"             # off | assist | qc
  max_side_px: 1024       # downscale for crops
  include_stage: "stage1_norm"   # stage0_input | stage1_norm | overlay
  crop: "auto"            # auto | bbox:[x0,y0,w,h]
  scrub:
    strip_exif: true
    blur_text: true       # blur rim annotations
    grayscale_gels: true  # gels only

  # BOUNDED parameter ranges Gemini may suggest
  allowlist:
    lanes:
      min_peak_distance_frac: [0.02, 0.06]
      prominence_frac:        [0.02, 0.12]
    bands:
      baseline.window_frac:   [0.005, 0.03]  # Independent from lanes
      baseline.quantile:      [0.05, 0.20]   # Independent from lanes
      min_peak_distance_px:   [6, 18]        # Independent from lanes
    colonies:
      threshold.method:       ["Otsu", "Phansalkar"]
      threshold.radius:       [15, 35]
      min_area_px:            [20, 400]
      colorspace:             ["Lab"]
      blue_cutoff_b:          [-8.0, -3.0]
```

---

## 🔄 Phase IV Orchestrator Flow

### **1. Baseline Run (Existing Phases I-III)**
- Execute full analysis with comprehensive telemetry
- **CRITICAL**: Ensure Java CLI called with `--no-exit` flag
- Generate flight recorder metrics for all pipelines

### **2. Vision-Assist Gate Check**
Trigger assist mode if ANY condition met:
- **Detection failures**: `lanes_raw == 0` OR `bands_raw == 0`
- **Baseline issues**: `baseline_post_med ≈ 0` OR `profile_zero_frac_after > 0.2`
- **Coverage problems**: `coverage_total < 0.2` OR `count_stability_score < 0.7`
- **Colony issues**: `final_count << components_raw` OR classifier not applied

### **3. Vision-Assist Processing (If Triggered)**
1. **Generate scrubbed crop**:
   - Auto-detect ROI or use configured bbox
   - Downscale to max_side_px=1024
   - Strip EXIF, blur text annotations
   - Grayscale for gels (preserve color for colonies)

2. **Build advisor request**:
   ```json
   {
     "task": "sds_page | etbr | colonies",
     "metrics": { "...": "comprehensive telemetry from Phase II" },
     "observation": { "...": "flight recorder diagnostics" },
     "crop_path": "sandbox:/runs/.../assist_crop.png",
     "config_subset": { "detect": {...}, "bands": {...} },
     "bounds": { "...": "from vision.allowlist" },
     "ask": ["roi","polarity","ladder_lane","param_tweaks"]
   }
   ```

3. **Validate & apply response**:
   - Check patch against allowlist bounds
   - Apply parameter changes
   - Re-run analysis with `--no-exit`
   - Accept ONLY if: (coverage ↑ OR stability ↑) AND no physics violations

### **4. Persistence & Logging**
- Per attempt: `attempt_###/run_report.json`, `attempt_###/advice.json`
- Final: `final_report.json` with before/after deltas
- Log markers: `VISION_ASSIST: enabled`, `ADVICE_APPLIED/REJECTED`

---

## 🏗️ Implementation Plan

### **Day 1: Core Infrastructure**
1. **Add vision config schema** to existing YAML files
2. **Implement core modules**:
   - `autodense_autotune/vision/cropper.py` - ROI, downscale, scrubbing
   - `autodense_autotune/vision/allowlist.py` - bounds validation
   - `autodense_autotune/vision/gating.py` - trigger logic
   - `autodense_autotune/vision/logging.py` - markers

3. **Mock advisor testing**:
   - Create mock advisor that returns safe parameter tweaks
   - Validate end-to-end flow without real Gemini calls
   - Test gate triggers and acceptance logic

### **Day 2: Real Integration**
1. **Implement real advisor**:
   - `autodense_autotune/vision/advisor.py` - Gemini API calls
   - Request building and response parsing
   - Confidence scoring and error handling

2. **Integration testing**:
   - **EtBr**: Trigger on baseline zeroing → suggest gentler band baseline
   - **SDS**: Trigger on 0 bands → suggest smaller min_peak_distance_px
   - **Colony**: Trigger on classification failure → suggest Lab colorspace

3. **Validation with real scenarios**:
   - Test acceptance rules (coverage/stability improvement)
   - Verify physics violation detection (ladder R², geometry)
   - Confirm parameter independence maintained

---

## 🔍 Vision-Assist Integration Points

### **Existing Telemetry (Phase II) → Advisor Input**
- **Lane metrics**: `profile_zero_frac_after`, lane geometry, spacing
- **Band metrics**: per-lane zero fractions, prominence used, window effective
- **Coverage**: numerator/denominator breakdown for debugging
- **Colony metrics**: filter chain ledger, Lab-b histogram, classification status

### **Advisor Response → Parameter Patches**
- **Bands**: Completely independent from lanes (window_frac, quantile, min_distance_px)
- **Lanes**: Independent lane detection parameters
- **Colonies**: Color classification and segmentation parameters
- **All patches**: Validated against allowlist bounds before application

### **Acceptance Criteria (Physics-Based)**
- **Coverage improvement**: `coverage_total_after > coverage_total_before`
- **Stability preservation**: `stability_after ≥ stability_before`
- **No degradation**: Ladder R² not worse, lane geometry maintained
- **Parameter bounds**: All suggestions within allowlist ranges

---

## 📊 Expected Outcomes

### **Vision-Assist Success Cases**
1. **EtBr under-detection**: Gentler band baseline recovers weak bands
2. **SDS over-aggressive**: Smaller min_distance_px finds closely spaced bands  
3. **Colony misclassification**: Lab colorspace + proper thresholds fix blue/white
4. **ROI issues**: Crop suggestions improve detection region focus

### **Fallback Behavior**
- **Vision disabled**: Falls back to Phase III telemetry-only optimization
- **API failures**: Graceful degradation with comprehensive logging
- **Parameter rejection**: Original results preserved if suggestions don't improve metrics

---

## 🎯 Integration Success Metrics

### **Technical Validation**
- ✅ All Java CLI calls include `--no-exit` flag
- ✅ Band parameters completely independent from lane parameters
- ✅ Vision-assist only triggered on actual failure conditions
- ✅ Parameter suggestions within bounded allowlist ranges
- ✅ Acceptance rules prevent regression in coverage/stability

### **Performance Targets**
- **EtBr**: 14-20 lanes detected (vs current 11), 5+ bands (vs current 2)
- **SDS**: Maintain 24+ bands with optimized parameters
- **Colony**: Proper blue/white classification (vs current all-white)
- **Overall**: >90% of vision-assist suggestions accepted based on metrics improvement

---

## 🔒 Safety & Compliance

### **Data Privacy**
- Only scrubbed crops sent to vision model (EXIF stripped, text blurred)
- No raw images permanently stored
- Only advice JSON + config fingerprints persisted

### **Deterministic Guarantees**  
- **Measurements**: Always produced by deterministic AutoDense pipeline
- **Reproducibility**: All parameter changes logged with config fingerprints
- **Fallback**: System works without vision-assist (telemetry-only mode)

### **Parameter Safety**
- **Bounded ranges**: All suggestions constrained by allowlist
- **Physics validation**: Sanity checks prevent impossible parameter combinations
- **Rejection logic**: Poor suggestions rejected based on metrics degradation

---

**Phase IV maintains our core principle: Gemini provides intelligent optimization suggestions, but AutoDense always produces the final measurements through its deterministic pipeline.**