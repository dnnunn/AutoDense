# Implementation Plan: August 29, 2025 - Auditor's Safe Baseline System

> **Doc Meta**
> - **Purpose:** Concrete implementation plan based on auditor's complete safe baseline subsystem solution
> - **Scope:** Java baseline replacement, config updates, challenge pack modifications, and testing infrastructure
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-29

## 🚀 Auditor's Complete Solution Overview

The auditor has provided a **production-ready safe baseline subsystem** that directly addresses the aggressive baseline removal issue I identified. This replaces our destructive 32-pixel morphological opening with:

- **Safe parameter bounds**: 3-15 pixel windows (vs destructive 32px)
- **Multiple baseline methods**: NONE, PERCENTILE, MORPH with gentle algorithms
- **ROI-aware sizing**: Parameters resolve against actual ROI width
- **Comprehensive logging**: Before/after statistics to prevent silent signal destruction
- **Config-driven approach**: YAML-based parameter management
- **Optimized algorithms**: O(n) moving min/max with deques

---

## 🔧 Implementation Tasks (Priority Order)

### A. Java: Baseline Subsystem Implementation

#### 1. Add BaselineParams Container Class
**Location**: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/BaselineParams.java`

**Complete implementation provided by auditor:**
```java
public final class BaselineParams {
    public enum Method { NONE, PERCENTILE, MORPH } // morph = opening (min→max)

    public final Method method;
    public final double windowFrac;      // fraction of ROI width, e.g. 0.02
    public final int    windowPx;        // explicit px; overrides frac if >0
    public final int    clampMinPx;      // 3
    public final int    clampMaxPx;      // 15
    public final double quantile;        // for percentile, e.g. 0.10
    
    // ... (complete implementation provided)
}
```

**Key features:**
- **Hard clamps**: 3-15 pixel bounds prevent destructive windows
- **ROI-aware**: Window sizing as fraction of actual ROI width  
- **Config loading**: Direct YAML integration with `fromConfig()` method

#### 2. Replace Moving Min/Max with Optimized Algorithms
**Location**: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`

**Auditor provides O(n) deque-based implementations:**
```java
// Fast O(n) moving min/max with deque instead of naive O(n²)
static double[] movingMin(double[] a, int w) {
    // Complete optimized implementation provided
}

static double[] runningLowEnvelope(double[] a, int w, double q) {
    // Gentle baseline via opening (erode→dilate)
}

static double[] subtractBaseline(double[] a, BaselineParams bp, String tag, PrintStream log) {
    // Safe baseline subtraction with comprehensive logging
}
```

#### 3. Update LaneDetector Integration
**Replace this destructive block** (around lines 1376-1382):
```java
// OLD: DESTRUCTIVE 32-pixel baseline removal
int minDistPx = Math.max(6, (int) Math.round(w * 0.04));  // 32px!
double[] base = movingMin(movingMax(profX, minDistPx), minDistPx);
for (int i = 0; i < profX.length; i++) {
    profX[i] = Math.max(0, profX[i] - base[i]);  // Destroys signal
}
```

**With auditor's safe approach:**
```java
// NEW: Safe, parameterized baseline removal
BaselineParams bp = BaselineParams.fromConfig(config, w);
double[] profForPeaks = subtractBaseline(profX, bp, "lanes", System.err);

// Continue with existing smoothing and detection
double sigma = Math.min(3.0, Math.max(1.0, w/300.0));
profForPeaks = gaussian1D(profForPeaks, sigma);
```

---

### B. Config: Safe Defaults and Parameter Structure

#### 1. Update SDS Configuration
**File**: `configs/sds.yaml`

**Add baseline configuration block:**
```yaml
detect:
  # Existing detection parameters...
  min_peak_distance_frac: 0.04     # ROI-relative spacing
  prominence_frac: 0.06             # Peak prominence threshold
  gaussian_sigma_max: 3.0           # Smoothing limit

  # NEW: Safe baseline removal configuration
  baseline:
    method: "percentile"            # "none" | "percentile" | "morph"
    window_frac: 0.02               # 2% of ROI width (gentle)
    window_px: 0                    # 0 = use fraction; else explicit px
    clamp_min_px: 3                 # Hard minimum bound
    clamp_max_px: 15                # Hard maximum bound (was 32!)
    quantile: 0.10                  # For percentile method
```

#### 2. Mirror for EtBr and Colony Configs
**Files**: `configs/etbr.yaml`, `configs/colony.yaml`

**Colony special case** - default to no baseline:
```yaml
detect:
  baseline:
    method: "none"                  # Colonies are fragile, default off
    # ... other params for when baseline is enabled
```

---

### C. Challenge Packs: Safe Parameter Search Grids

#### 1. Update SDS Challenge Pack
**File**: `challenge_packs/sds_page_v1/spec.yaml`

**Replace dangerous parameter exploration:**
```yaml
search:
  param_grids:
    # Safe baseline method exploration
    - { file: "configs/sds.yaml", path: "detect.baseline.method", values: ["percentile","morph","none"] }
    - { file: "configs/sds.yaml", path: "detect.baseline.window_frac", values: [0.01, 0.02, 0.03] }
    - { file: "configs/sds.yaml", path: "detect.baseline.quantile", values: [0.05, 0.10, 0.15] }
    
    # Safe detection parameter ranges
    - { file: "configs/sds.yaml", path: "detect.prominence_frac", values: [0.04, 0.06, 0.08] }
    - { file: "configs/sds.yaml", path: "detect.min_peak_distance_frac", values: [0.03, 0.04, 0.05] }
```

**Key improvement**: Window fractions 0.01-0.03 (8-24px for 800px width) vs old destructive 32px

#### 2. Update EtBr and Colony Challenge Packs
**Files**: `challenge_packs/etbr_v1/spec.yaml`, `challenge_packs/colony_count_v1/spec.yaml`

Mirror the same safe parameter exploration approach.

---

### D. Enhanced detect-only Mode

#### 1. Improve CLI Integration
**File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java`

**Add config loading to detect-only mode:**
```bash
java [...] AutotuneAnalysisCLI \
  --detect-only \
  --preprocessed path/to/stage1_norm.png \
  --roi x0,y0,w,h \
  --config-yaml configs/sds.yaml \  # NEW: Config integration
  --outdir output/detect_test
```

#### 2. Enhanced Output and Logging
**Output includes:**
- `lane_profile.png` with detected peaks marked
- JSON with baseline method, window size, and detection results
- Before/after baseline removal statistics
- Non-zero exit code if no lanes detected

---

### E. Comprehensive Testing Infrastructure

#### 1. Systematic Validation Script
**Create**: `scripts/test_safe_baselines.sh`

```bash
#!/bin/bash
# Test safe baseline system across all sample images
for img in samples/*.{png,jpg,tif}; do
  java -cp target/classes:$(cat target/classpath.txt) \
    com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
    --detect-only --preprocessed "$img" --roi 20,10,760,480 \
    --config-yaml configs/sds.yaml \
    --outdir runs/detect_only/$(basename "$img" .png)
done
```

#### 2. Expected Results Validation
**Success criteria per auditor:**
- `lane_profile.png` shows skyline with gray bars at peaks
- Logs include: `[BASELINE] method=percentile win=9 pre{med=0.59,max=1.00} post{...}`
- Detection count: `[PATH] detection:done count=15` (or appropriate for image)

---

### F. Quality Gates and Honest Reporting

#### 1. Detection Metadata Enhancement
**Add to detection result processing:**
```java
meta.put("detector_called", true);
meta.put("baseline", Map.of(
  "method", bp.method.toString().toLowerCase(),
  "window_px", bp.windowPx
));

if (peaks.isEmpty()) {
    metrics.put("lane_count", 0);
    metrics.put("band_count", 0);
    metrics.remove("ladder_linear_r2");  // No fake confidence
    meta.put("status", "no_lanes");
}
```

#### 2. Prevent Silent Failures
- **Before/after logging**: Never allow signal destruction without warning
- **Hard clamps**: 3-15 pixel bounds prevent destructive windows
- **Method validation**: Explicit baseline method reporting
- **Exit codes**: Detect-only mode fails loudly when detection fails

---

## 🎯 Implementation Session Plan

### Session 1: Core Java Implementation (3-4 hours)
1. **Create BaselineParams class** with auditor's complete implementation
2. **Replace destructive baseline removal** in LaneDetector.java
3. **Add optimized moving min/max algorithms** with deque implementation
4. **Test detect-only mode** with new baseline system

### Session 2: Configuration and Testing (2-3 hours)  
1. **Update all config files** with safe baseline parameters
2. **Test across all 9 sample images** to validate working detection
3. **Create systematic testing script** for continuous validation
4. **Update challenge packs** with safe parameter grids

### Session 3: AI Optimization Integration (2-3 hours)
1. **Connect autotune system** to safe parameter ranges
2. **Implement performance baseline** measurement
3. **Test optimization feedback loops** with working detector
4. **Validate helper critic system** prevents regression

---

## 🔐 Safety and Validation

### Critical Success Metrics
- **No more zeros**: Signal median/max never collapses to 0.000 after baseline removal  
- **Configurable detection**: Lane counts vary sensibly with parameter changes
- **Performance baseline**: Can optimize from working detector performance
- **Safe bounds**: Parameter exploration stays within 3-15 pixel windows

### Quality Gates
- **Before/after logging**: Every baseline operation reports signal statistics
- **Hard parameter clamps**: Impossible to set destructive window sizes
- **Honest reporting**: No fake confidence metrics when detection fails
- **Exit code validation**: Detect-only mode fails explicitly when broken

---

## 🚀 Why This Unlocks AI Optimization

**Signal integrity restored**: Detection varies with parameters in predictable ways
**Safe exploration bounds**: Optimizer can't accidentally destroy detection
**Working baseline established**: Can measure improvement vs regression  
**Comprehensive feedback**: All metrics (counts, confidence, timing) now meaningful

The auditor's solution provides **exactly what's needed** to transition from "broken detection" to "AI-optimized detection" with confidence and safety.

---

**Next session**: Implement BaselineParams class and replace destructive baseline removal with auditor's safe system. Expected result: 15+ lanes detected consistently across sample images with configurable, gentle baseline removal.