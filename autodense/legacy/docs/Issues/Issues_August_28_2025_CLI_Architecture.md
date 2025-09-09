# Issues - August 28, 2025 - CLI Architecture Fix

> **Doc Meta**
> - **Purpose:** Issues identified during YAML-based preprocessing parameter architecture implementation
> - **Scope:** Technical debt, known bugs, and architectural concerns discovered during session
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-28

## 🐛 Unresolved Issues from Previous Sessions

### CRITICAL: Preprocessing Causes Image Rotation/Skewing
**Status:** Architecture fixed, debugging ready
**Context:** User reported: *"stage2_bgremoved.png is completely skewed"* and *"even without the deskewing the same thing happened"*

**Symptoms:**
- Massive rotation/skewing of images during preprocessing
- Grey bands with white halos in stage2_bgremoved.png
- Loss of all visible bands in output images

**Investigation Status:** 
- ✅ Architecture now supports precise YAML parameter control
- ❌ Root cause still unknown (rotation occurs even with deskew disabled)
- 🔄 Ready for systematic debugging with four scenarios

### CRITICAL: Detection Algorithms Return 0 Despite Visible Features
**Status:** Unresolved, linked to preprocessing issues
**Context:** ImageJ lane/band detection returning 0 results on images that clearly contain detectable features

**Symptoms:**
- Lane detection: 0 lanes found
- Band detection: 0 bands found  
- Despite visible lanes/bands in original synthetic images

**Investigation Status:**
- Root cause likely preprocessing destroying image features
- May be polarity/scale mismatch as suggested in "15-minute triage guide"
- Debugging blocked pending preprocessing issue resolution

## 🔧 Technical Debt Identified

### Medium: TODO Comment in AutotuneAnalysisCLI.java
**Location:** Line 721 - `TODO: Implement real touching detection`
**Context:** Placeholder comment for colony touching fraction calculation
```java
metrics.put("touching_fraction", 0.1); // TODO: Implement real touching detection
```

**Impact:** Colony analysis metrics incomplete, affects optimization feedback
**Recommendation:** Implement overlap detection algorithm for accurate touching fraction

### Low: Hardcoded Default Values in Metrics
**Location:** Multiple locations in AutotuneAnalysisCLI.java
**Context:** Default metric values when real analysis data unavailable
```java
metrics.put("ladder_r2", 0.95); // Default reasonable value
metrics.put("background_snr", 4.2); // Default reasonable value
```

**Impact:** Optimization system receives placeholder data instead of real metrics
**Recommendation:** Ensure analysis tools return actual measured values

### Low: Inconsistent Hash Generation
**Location:** `generateFileHash()` method
**Context:** Uses different hash methods in different parts of codebase
```java
String inputHash = Integer.toHexString(Paths.get(input).hashCode()); // Simple hash
// vs
String inputHash = generateFileHash(inputPath); // SHA-256 hash
```

**Impact:** Input tracking inconsistent across analysis runs
**Recommendation:** Standardize on SHA-256 for all input hashing

## 🏗️ Architectural Concerns

### Medium: Preprocessing Parameter Validation
**Context:** YAML parameters now loaded but validation incomplete
**Risk:** Invalid parameter values could cause processing failures or artifacts
**Current State:** Basic bounds checking in some areas, missing in others

**Example:**
```java
double rescueSensitivity = Math.max(0.1, originalProminence * 0.33); // Has bounds
config.gaussianSigma = preConfig.optDouble("gaussian_sigma", 1.0); // No bounds
```

**Recommendation:** Add comprehensive parameter validation with user-friendly error messages

### Low: Debug File Naming Consistency  
**Context:** Different analysis methods use different debug file names
- EtBr: `etbr_overlay.png`
- SDS: `sds_overlay.png` 
- Colony: `colony_overlay.png`

**Inconsistency:** Not aligned with preprocessing debug files (`stage0_input.png`, etc.)
**Recommendation:** Standardize debug file naming convention

## 🚨 Potential Security/Safety Issues

### Low: YAML Loading Security
**Context:** Using SnakeYAML to parse configuration files
**Risk:** YAML parsing can be vulnerable to injection attacks if input not trusted
**Current Mitigation:** Using SafeConstructor in YAML parser
**Status:** Acceptable for local configuration files

## 📝 Documentation Gaps

### Medium: YAML Parameter Documentation
**Context:** New YAML structure lacks comprehensive documentation
**Missing:** 
- Parameter descriptions and valid ranges
- Examples for different analysis scenarios
- Migration guide from hardcoded parameters

**Impact:** Users may configure invalid parameters causing processing failures
**Recommendation:** Add inline YAML comments and parameter reference documentation

### Low: Debug Output Interpretation Guide
**Context:** Debug overlays now generated but no guidance on interpretation
**Missing:** Guide for analyzing stage0/stage1/stage2 debug outputs
**Impact:** Harder to diagnose preprocessing issues
**Recommendation:** Create debug output analysis guide

## 🔄 Process Issues

### Low: Session Context Loss Between Debugging Sessions
**Context:** Debugging requires multiple test runs across sessions
**Risk:** Parameter state and test results lost between sessions
**Recommendation:** Implement test result persistence and parameter tracking

---

## Next Session Action Items

1. **CRITICAL:** Run four debugging scenarios to identify rotation/skewing root cause
2. **HIGH:** Implement parameter validation for YAML configuration
3. **MEDIUM:** Add TODO item resolution for colony touching detection
4. **LOW:** Standardize debug file naming and create interpretation guide

---

*Issues logged for systematic resolution - architecture foundation stable for debugging.*