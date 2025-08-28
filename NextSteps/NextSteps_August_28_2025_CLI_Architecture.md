# Next Steps - August 28, 2025 - CLI Architecture Fix

> **Doc Meta**
> - **Purpose:** Next steps for systematic debugging tests with YAML-controlled preprocessing parameters
> - **Scope:** Priority tasks for resolving preprocessing rotation/detection issues 
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-28

## 🎯 Immediate Priority Tasks

### 1. **Run Four Systematic Debugging Scenarios** (CRITICAL)
**Context:** Architecture now supports precise YAML parameter control. Ready to debug rotation/detection issues.

**Command to run:** `make tune-colony-ij INPUT=plates/plate.jpg` (with synthetic EtBr gel)

**Test scenarios to run sequentially:**

#### Scenario 1: Full Preprocessing (Baseline)
```yaml
# configs/etbr.yaml
pre:
  invert_polarity: auto
  normalize_intensity: true
  gaussian_sigma: 1.0
  background_removal_radius: 30.0
  enable_deskew: true
  enable_debug_logging: true
```

#### Scenario 2: Deskew Disabled Only
```yaml
pre:
  invert_polarity: auto
  normalize_intensity: true
  gaussian_sigma: 1.0
  background_removal_radius: 30.0
  enable_deskew: false  # <-- Only change
  enable_debug_logging: true
```

#### Scenario 3: All Preprocessing Disabled
```yaml
pre:
  invert_polarity: false
  normalize_intensity: false
  gaussian_sigma: 0.0
  background_removal_radius: 0.0
  enable_deskew: false
  enable_debug_logging: true
```

#### Scenario 4: Minimal Preprocessing Only
```yaml
pre:
  invert_polarity: auto
  normalize_intensity: true
  gaussian_sigma: 0.0      # No smoothing
  background_removal_radius: 0.0  # No background removal
  enable_deskew: false     # No deskew
  enable_debug_logging: true
```

### 2. **Analyze Debug Outputs** (HIGH)
For each scenario, examine generated files:
- `stage0_input.png` - Original image
- `stage1_norm.png` - After normalization
- `stage2_bgremoved.png` - After background removal
- `etbr_overlay.png` - Final detection overlay

**Look for:**
- Image rotation/skewing artifacts
- Loss of band visibility
- Gray bands with white halos
- Detection counts (lanes/bands found)

### 3. **Root Cause Analysis** (HIGH)
Based on debug outputs, identify:
- **Which preprocessing step** causes rotation/skewing
- **Why algorithms return 0 detections** despite visible features
- **Parameter value ranges** that cause artifacts
- **Polarity detection accuracy** in different scenarios

## 🔧 Technical Investigation Tasks

### 4. **Validate ImagePreprocessor.Config.fromYaml()** (MEDIUM)
Ensure YAML parameters are correctly parsed and applied:
- Log actual parameter values being used
- Verify default fallbacks work correctly
- Test edge cases (missing parameters, invalid values)

### 5. **Debug Polarity Detection Logic** (MEDIUM)
The preprocessing guide emphasizes polarity issues cause "99% of failures":
- Verify `auto` polarity detection heuristics
- Test forced `true`/`false` polarity settings
- Log polarity decisions for each image

### 6. **Investigate Rescue Fallback System** (LOW)
Current rescue logic in runEtbr() method:
- Verify rescue triggers correctly when detections = 0
- Test rescue parameter adjustments (prominence × 0.33, σ × 1.6)
- Ensure rescue uses different preprocessing than initial attempt

## 📋 Documentation Tasks

### 7. **Update Issues Log** (LOW)
Document specific rotation/detection issues discovered during testing

### 8. **Create Debugging Runbook** (LOW)
Standardize the four-scenario testing procedure for future debugging

## 🚨 Blockers & Dependencies

### Known Blockers
- **User availability:** Testing requires running `make tune-colony-ij` command
- **Output analysis:** Need manual inspection of debug overlay images

### Prerequisites Complete
- ✅ YAML architecture implemented
- ✅ All three analysis methods updated
- ✅ Debug logging enabled
- ✅ Synthetic test images available

## 🎯 Success Criteria

### Session Success
- [ ] All four scenarios run successfully without crashes
- [ ] Debug overlays generated for each scenario
- [ ] Root cause of rotation/skewing identified
- [ ] At least one scenario produces non-zero detections

### Architecture Validation
- [ ] YAML parameter changes affect preprocessing behavior
- [ ] Debug logging provides useful diagnostic information
- [ ] Preprocessing pipeline works consistently across analysis types

### Next Milestone
**Goal:** Working preprocessing pipeline that doesn't destroy image features
**Measure:** Detection algorithms find lanes/bands in preprocessed images

---

*Ready for systematic debugging - architecture foundation complete.*