# Issues: August 29, 2025 - Detection Pipeline Fix Session

> **Doc Meta**
> - **Purpose:** Issues identified during detection pipeline breakthrough session
> - **Scope:** Bugs discovered, performance concerns, and system limitations found during debugging
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-29

## 🐛 Resolved Issues

### 1. Missing min() Helper Function
**Issue**: `AutotuneAnalysisCLI.java` was missing `min()` helper function causing compilation errors
**Location**: Lines referencing `min(profX)` in detect-only mode
**Resolution**: Added `min()` helper function at lines 1493-1497
**Status**: ✅ **RESOLVED**

### 2. Incorrect Debug Statement Placement  
**Issue**: Debug statement labeled "BEFORE baseline removal" was actually placed AFTER baseline removal
**Impact**: Misleading debugging information showing flattened signal as "before" measurement
**Location**: `AutotuneAnalysisCLI.java` around line 1382
**Resolution**: Moved debug statement to proper location before morphological opening
**Status**: ✅ **RESOLVED**

### 3. Aggressive Baseline Removal Destroying Signal
**Issue**: 32-pixel morphological opening window completely flattening lane profiles to zeros
**Impact**: **CRITICAL** - Complete detection failure across all workflows
**Root Cause**: `int minDistPx = Math.max(6, (int) Math.round(w * 0.04))` creating 32px window for 800px width
**Evidence**: Signal `med=0.595 max=1.000` → `med=0.000 max=0.000` after baseline removal
**Resolution**: Bypassed aggressive baseline removal in detect-only mode
**Status**: ✅ **RESOLVED** (in detect-only mode, pending main pipeline fix)

## 🚨 Active Issues Requiring Attention

### 1. Main Detector Pipeline Still Broken
**Issue**: Fix only applied to detect-only mode, main `LaneDetector.java` still has aggressive baseline removal
**Location**: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java` ~lines 1376-1382
**Impact**: Full workflows still return 0 detections
**Priority**: **CRITICAL** - Must fix in next session
**Approach**: Apply same baseline removal bypass/reduction as in detect-only mode

### 2. ImageJ/Fiji Discovery Issues
**Issue**: `scripts/discover_imagej.py` finds 0 valid installations despite ImageJ being available
**Impact**: Classpath building requires manual JAR location
**Symptom**: Script reports "❌ No valid installations found!" 
**Workaround**: Using Maven-generated classpath from `scripts/build_classpath.sh`
**Priority**: **MEDIUM** - Not blocking core functionality but affects automation

### 3. Unused Import Warnings
**Issue**: Multiple unused imports in `AutotuneAnalysisCLI.java`
**Locations**: 
- Line 12: `com.betterdairy.autodense.util.ConfigIO`
- Line 30: `org.yaml.snakeyaml.constructor.SafeConstructor`
**Impact**: Code cleanliness, potential confusion
**Priority**: **LOW** - Cleanup when convenient

## 🔍 Performance Concerns Identified

### 1. Maven Build Warnings
**Issue**: Persistent `sun.misc.Unsafe` warnings during compilation
**Source**: Guice dependency in Maven
**Impact**: Build noise, potential future compatibility issues
**Sample Warning**: `WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner`
**Priority**: **LOW** - Not affecting functionality

### 2. Classpath Complexity
**Issue**: Runtime classpath includes 243 JAR files  
**Impact**: Startup time, complexity, potential conflicts
**Evidence**: `[ENV] java.class.path.size=243`
**Priority**: **MEDIUM** - Monitor for performance impact

### 3. Unused Helper Functions
**Issue**: Several helper functions now unused after baseline removal bypass
**Locations**:
- `movingMax()` method (line 1540)
- `movingMin()` method (line 1552)  
- `writeErrorJson()` method (line 1468)
**Impact**: Code bloat, maintenance overhead
**Priority**: **LOW** - Cleanup during refactoring

## 🚧 System Limitations Discovered

### 1. Hardcoded Parameter Assumptions
**Issue**: Many parameters assume specific image characteristics
**Examples**:
- Baseline window as 4% of image width (too large for lanes)
- Fixed 32-pixel minimum distance regardless of actual lane spacing
- Hardcoded percentile ranges (p5/p95) may not suit all image types
**Impact**: Poor generalization across different gel/plate types
**Priority**: **MEDIUM** - Address during AI optimization phase

### 2. No Adaptive Parameter Selection  
**Issue**: Parameters don't adapt to image characteristics
**Examples**: Same baseline removal window for 400px vs 2000px images
**Impact**: Either over-processing narrow images or under-processing wide images
**Suggested Investigation**: Dynamic parameter scaling based on detected features
**Priority**: **MEDIUM** - Good candidate for AI optimization

### 3. Limited Error Recovery
**Issue**: Pipeline fails completely when parameters are too aggressive
**Example**: Baseline removal destroys signal with no fallback detection attempt
**Impact**: All-or-nothing failure mode rather than graceful degradation
**Suggested Approach**: Multi-tier detection with increasingly lenient parameters  
**Priority**: **MEDIUM** - Implement as part of robustness improvements

## 🔬 Edge Cases for Future Investigation

### 1. Signal Preservation vs Background Removal Balance
**Question**: How to remove genuine background noise without destroying lane signals?
**Investigation Needed**: 
- Spectral analysis of lane vs background characteristics
- Adaptive window sizing based on detected lane spacing
- Multi-scale background removal approaches

### 2. Parameter Interaction Effects
**Question**: How do preprocessing parameters interact to affect final detection quality?
**Investigation Needed**:
- Systematic parameter interaction analysis
- Identify parameter combinations that cause signal destruction
- Define safe parameter exploration bounds

### 3. Image Type Generalization
**Question**: Can single parameter set work across SDS-PAGE, EtBr, and colony images?
**Investigation Needed**:
- Comparative analysis of signal characteristics across image types
- Type-specific parameter optimization
- Automatic image type detection for parameter selection

## 📋 Recommended Investigation Approaches

### For Next Session
1. **Apply main detector fix** and validate with comprehensive testing
2. **Parameter sensitivity analysis** to understand safe operating ranges
3. **Multi-scale testing** across different image resolutions and types

### For Future Sessions  
1. **Implement adaptive parameter selection** based on image characteristics
2. **Add graceful degradation** with multi-tier detection fallback
3. **Systematic parameter interaction study** to optimize combinations

## 🎯 Success Metrics for Issue Resolution

### Critical Issues (Must Fix)
- [ ] Main `LaneDetector.java` updated with working baseline removal
- [ ] Full pipeline workflows return >0 detections consistently
- [ ] All 9 sample images process successfully

### Performance Issues (Should Fix)
- [ ] Build warnings reduced or suppressed appropriately  
- [ ] Unused code cleaned up during refactoring
- [ ] ImageJ discovery fixed or alternative approach implemented

### Enhancement Opportunities (Could Fix)
- [ ] Adaptive parameter selection implemented
- [ ] Multi-tier fallback detection system
- [ ] Parameter interaction effects characterized

This session successfully identified and resolved the critical detection failure, establishing a clear path forward for addressing remaining issues.