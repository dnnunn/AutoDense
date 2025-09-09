# Issues Report: September 01, 2025 - Truth Preservation Analysis

> **Doc Meta**
> - **Purpose:** Critical issues identified during comprehensive detection analysis and external audit integration
> - **Scope:** Truth inflation problems, AI orchestration failures, detection algorithm issues, and architectural gaps
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-01

## 🚨 CRITICAL ISSUES

### Issue #1: Truth Inflation - Epistemological Violation
**Severity:** CRITICAL  
**Impact:** Complete undermining of AI optimization capability

**Description:**
Raw detection measurements are being artificially inflated to match prior expectations, violating the fundamental principle that "truth > prior."

**Evidence:**
- **SDS-PAGE**: Raw detection finds 7 peaks → logs show "robust detection found 7 peaks" → JSON reports "expected=10 found=10"
- **EtBr**: Raw detection finds 11 peaks → honest reporting of 11 vs expected 20
- **Inconsistent Application**: Truth inflation applied to some analysis types but not others

**Root Cause:**
Biased adjustment logic in `LaneDetector.java:270-290` that forces detected counts to match `expectedLanes` parameter.

**Impact Analysis:**
- **AI Optimization Impossible**: Cannot optimize parameters when measurements are dishonest
- **Quality Metrics Corrupted**: Detection confidence scores (0.95) contradict actual results
- **User Trust Erosion**: System reports false positives that don't match visual inspection

**Investigation Required:**
- [ ] Map all locations where count inflation occurs
- [ ] Identify which analysis types apply biased adjustment vs honest reporting
- [ ] Design dual reporting system (raw + reconciled) with clear explanations

**Priority:** Phase 1 of remediation plan - must fix before any parameter optimization

---

### Issue #2: AI Orchestration Bypass - Core Value Proposition Failure
**Severity:** CRITICAL  
**Impact:** AutoDense's main feature (AI optimization) is completely non-functional

**Description:**
The Java CLI completely bypasses the Python orchestrator, meaning Gemini AI is never invoked in the optimization process despite being AutoDense's core value proposition.

**Evidence:**
- **Log Analysis**: No `GEMINI: enabled/disabled` markers in any test runs
- **Execution Path**: `AutotuneAnalysisCLI.java` → `detection_results.json` → exit (no AI involvement)
- **Fast Results**: "Extremely rapid" results confirm single-pass analysis with no optimization
- **Missing Artifacts**: No `attempt_###/` directories or iterative optimization logs

**Root Cause:**
Direct invocation of Java CLI bypasses Python orchestration layer that contains AI integration.

**Current Architecture Gap:**
```
Expected: User → Python Orchestrator → Gemini AI → Parameter Proposals → Java Execution → Feedback Loop
Actual:   User → Java CLI → Direct Analysis → Results → Exit (No AI)
```

**Impact Analysis:**
- **Feature Not Working**: Core differentiation of AutoDense is non-functional
- **No Parameter Optimization**: System cannot learn or improve detection parameters
- **Manual Tuning Required**: Users must manually adjust parameters with no AI assistance

**Investigation Required:**
- [ ] Map correct execution path through Python orchestrator
- [ ] Identify why build scripts route to Java CLI instead of Python orchestrator
- [ ] Design clear logging to indicate when AI is enabled vs disabled
- [ ] Implement proper artifact persistence for multi-attempt optimization

**Priority:** Phase 3 of remediation plan - architectural change required

---

### Issue #3: Universal Band Detection Failure
**Severity:** HIGH  
**Impact:** Complete loss of band quantification capability across all analysis types

**Description:**
Band detection reports 0 bands across SDS-PAGE, EtBr, and Colony analysis, despite successful lane detection and visible bands in source images.

**Evidence:**
- **SDS-PAGE**: `bands_total: 0`, `coverage_bands: 0` despite 7 lanes detected
- **EtBr**: `bands_total: 0`, `coverage_bands: 0` despite 11 lanes detected  
- **Colony**: Different detection method but same pattern of feature detection failure
- **Baseline Issues**: `baseline_post_med: 0` suggests over-aggressive baseline removal

**Root Cause Analysis:**
- **Over-Aggressive Baseline Removal**: Baseline processing flattens band signals along with noise
- **Parameter Coupling**: Band detection parameters coupled with lane detection instead of independent
- **Threshold Issues**: Band detection thresholds likely too restrictive after baseline processing

**Technical Evidence:**
```json
"baseline_pre_med": 0.8205,
"baseline_post_med": 0.0,
"baseline_post_max": 0.179
```
This indicates baseline removal eliminated most signal, killing bands but preserving lane structure.

**Investigation Required:**
- [ ] Implement independent baseline parameters for bands (smaller windows: 2-8px vs 3-12px)
- [ ] Test band detection with disabled baseline removal to confirm hypothesis
- [ ] Separate band prominence thresholds from lane detection parameters
- [ ] Implement per-lane vertical profiling for band detection

**Priority:** Phase 2 of remediation plan - separate band detection architecture

---

### Issue #4: Configuration Schema Conflicts
**Severity:** HIGH  
**Impact:** Parameter optimization failures due to routing conflicts

**Description:**
Multiple overlapping configuration sections cause parameter routing failures, with good parameters being lost due to schema inconsistencies.

**Evidence:**
- **SDS Config**: `prominence_frac: 0.06` (detect section) vs `prominence: 0.30` (detection section)
- **Section Proliferation**: `detect`, `detection`, `sds`, `etbr` sections with conflicting parameters
- **Parameter Loss**: Tools reading from different sections get different parameter values

**Examples of Conflicts:**
```yaml
detect:
  prominence_frac: 0.06      # Modern parameter
detection:  
  prominence: 0.30           # Legacy parameter - 5x higher!
sds:
  prominence: 0.3            # Another legacy parameter
```

**Impact Analysis:**
- **Optimization Interference**: AI cannot optimize parameters if different tools read different values
- **Debugging Difficulty**: Hard to determine which parameters are actually being used
- **Development Friction**: Developers must update multiple sections for single parameter change

**Investigation Required:**
- [ ] Audit all config files for section conflicts
- [ ] Map which tools read from which configuration sections  
- [ ] Design unified schema: `detect.lanes`, `detect.bands`, `segmentation`, `priors`
- [ ] Create migration plan for existing configurations

**Priority:** Phase 2 of remediation plan - configuration unification

---

## 🔍 MEDIUM PRIORITY ISSUES

### Issue #5: Colony Detection Pipeline Failure
**Severity:** MEDIUM  
**Impact:** Complete colony analysis non-functional

**Description:**
Colony detection reports 0 colonies detected, suggesting binary mask contract violation or color physics issues.

**Root Cause Hypotheses:**
- **Binary Mask Contract**: Not producing true 8-bit {0,255} binary mask for particle analysis
- **Color Physics Loss**: Grayscale conversion eliminates X-gal blue/white distinction
- **Threshold Issues**: Over-restrictive size or threshold parameters

**Investigation Required:**
- [ ] Verify binary mask output format (8-bit boolean vs grayscale)
- [ ] Test color preservation through processing pipeline
- [ ] Add telemetry: `foreground_fraction`, `median_blob_area`, `mask_type`

### Issue #6: Stability and Robustness Gaps  
**Severity:** MEDIUM
**Impact:** Parameter optimization may be unreliable

**Description:**
No stability testing under parameter micro-jitters, making parameter optimization potentially brittle.

**Missing Capabilities:**
- Stability scoring under ±10% threshold variations
- Robustness testing under ±1-2px distance variations  
- Parameter sensitivity analysis

**Investigation Required:**
- [ ] Implement micro-jitter testing framework
- [ ] Add stability scoring to reconciliation function
- [ ] Design parameter sensitivity reporting

---

## 🔧 LOW PRIORITY ISSUES

### Issue #7: Package Size Optimization Needed
**Severity:** LOW (RESOLVED THIS SESSION)  
**Status:** ✅ **FIXED**

**Description:**
Codebase packaging included 3.3GB of unnecessary files, making distribution and analysis inefficient.

**Resolution:**
- Enhanced `package_codebase.py` with better exclusion patterns
- Achieved 90% size reduction (3.3GB → 30MB)
- Excluded large dependency JARs, generated outputs, duplicate samples

### Issue #8: Code Quality Violations
**Severity:** LOW (RESOLVED THIS SESSION)
**Status:** ✅ **FIXED** 

**Description:**
150+ Checkstyle violations and Python linting issues affecting code maintainability.

**Resolution:**
- Applied linting expert to fix Java and Python violations
- Achieved 100% Checkstyle pass, SpotBugs clean, Python linting clean
- Preserved detection functionality while improving code quality

---

## 📊 ISSUE PRIORITY MATRIX

### **CRITICAL (Fix Immediately)**
1. **Truth Inflation** - Blocks all optimization
2. **AI Orchestration Bypass** - Core feature non-functional

### **HIGH (Fix Next Sprint)**  
3. **Universal Band Detection Failure** - Major capability loss
4. **Configuration Schema Conflicts** - Prevents parameter optimization

### **MEDIUM (Fix When Resources Available)**
5. **Colony Detection Pipeline Failure** - One analysis type affected
6. **Stability and Robustness Gaps** - Optimization reliability concerns

### **LOW (Monitor/Maintenance)**
7. **Package Size** - ✅ FIXED
8. **Code Quality** - ✅ FIXED

---

## 🔄 INVESTIGATION DEPENDENCIES

### **Phase 1 Prerequisites (Truth Preservation)**
- All other fixes depend on honest measurements
- Must complete before any parameter optimization work

### **Phase 2 Prerequisites (Configuration & Band Detection)**
- Requires Phase 1 completion for accurate testing
- Band detection fixes need truth preservation to validate improvements

### **Phase 3 Prerequisites (AI Integration)**
- Requires Phases 1-2 for clean data and unified configuration
- AI optimization needs honest measurements and consistent parameters

### **Phase 4 Prerequisites (Full Validation)**
- Requires all previous phases for end-to-end testing
- Colony fixes can proceed in parallel with other phases

---

## 📚 REFERENCE INFORMATION

### **Key Evidence Files**
- **Test Results**: `output/test_sds/run_report.json` - Shows truth inflation evidence
- **Configuration Examples**: `configs/sds.yaml` - Shows schema conflicts
- **Detection Code**: `LaneDetector.java:270-290` - Contains biased adjustment logic

### **Success Criteria for Resolution**
- **Truth Preservation**: Raw counts match log messages, no inflation
- **AI Integration**: `GEMINI: enabled` in logs, multiple optimization attempts
- **Band Detection**: Non-zero band detection across analysis types
- **Configuration**: Single schema, no parameter routing conflicts

### **External Audit Insights**
- **"Truth > Prior"**: Fundamental principle for measurement integrity
- **Observable Telemetry**: AI needs geometry, coverage, baseline health (not pixels)
- **Bounded Optimization**: Parameter changes within safety constraints only

This issues report provides a systematic categorization of all problems identified during the comprehensive detection analysis, with clear priorities and investigation paths for resolution.