# Issues Report: August 30, 2025 - Lane Detection & Environment

> **Doc Meta**
> - **Purpose:** Issues discovered during environment documentation and lane detection diagnosis session
> - **Scope:** Critical algorithm bugs, environment setup problems, and infrastructure issues
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-30

## 🚨 CRITICAL ISSUES

### Issue #1: Lane Detection Algorithm Severely Under-Performs
**Severity:** CRITICAL  
**Impact:** Core functionality failure - missing 58% of lanes  

**Description:**
Lane detection consistently finds only 5 peaks when test gel contains 12 actual lanes.

**Evidence:**
```
[LANE_PROJ] robust detection found 5 peaks
[PATH] detection:done count=8
[DETECT_ONLY] LaneDetector found 8 lanes
```
- Raw detection: 5 peaks found
- Biased adjustment: Increased to 8 lanes  
- Actual count: 12 lanes
- **Success rate: 42% (5/12 raw), 67% (8/12 final)**

**Location:** `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java:findLanesInternal()`

**Root Cause Analysis:**
1. **Possible profile generation issues** - Horizontal projection may not capture lane structure properly
2. **Over-restrictive parameters** - minDist=144, minProm=0.060 may be filtering valid peaks
3. **Baseline removal effectiveness** - May still be removing signal despite auditor's fix
4. **ROI boundary issues** - ROI 50,100,800,400 may not encompass all lanes

**Test Case:**
- Image: `output/sds_test/stage1_norm.png` (preprocessed SDS gel)
- Expected: 12 lanes
- Actual: 5 peaks detected, 8 lanes reported
- Consistent across expectedLanes values: 0, 12, 15

**Investigation Required:**
- [ ] Analyze horizontal projection profile visually
- [ ] Test different minDist/minProm parameters  
- [ ] Verify ROI encompasses all lane regions
- [ ] Check baseline removal effectiveness on test image

---

### Issue #2: Biased Detection Algorithm
**Severity:** HIGH  
**Impact:** False confidence in detection accuracy

**Description:**
Lane detection algorithm artificially adjusts results to match expected count instead of pure signal-based detection.

**Code Location:**
```java
int target = expectedCount > 0 ? expectedCount : 8; // heuristic default
List<Lane> best = lanes; int bestDiff = Math.abs(target - lanes.size());
for (int step = 0; step < 6 && bestDiff > 0; step++) {
    // ... adjust thresholds to minimize Math.abs(target - ls.size())
    int diff = Math.abs(target - ls.size());
    if (diff < bestDiff) { best = ls; bestDiff = diff; }
}
```

**Problem:** Algorithm iteratively adjusts detection parameters to minimize difference from expected count, creating bias.

**Evidence:** Regardless of expectedLanes setting (0, 12, 15), final result is consistently 8 lanes.

**Impact:**
- Masks underlying detection failures
- Creates false sense of accuracy
- Makes debugging more difficult
- Provides inconsistent results based on expectations rather than data

**Fix Required:** Remove target-matching logic and implement pure signal-based detection.

---

### Issue #3: Python Environment Configuration Confusion  
**Severity:** MEDIUM (RESOLVED for CLI mode)  
**Impact:** Session time waste, development velocity reduction

**Description:**
Java ConfigManagerBridge calls `python3` globally instead of using project's Python virtual environment, causing module import failures.

**Error:**
```
ModuleNotFoundError: No module named 'yaml'
```

**Root Cause:** 
- Packages exist in `.venv/bin/python` environment
- Java hardcodes `python3` command
- Global Python installation lacks required packages

**Temporary Fix Applied:**
Modified `AutotuneAnalysisCLI.java` line 1283:
```java
// OLD: JSONObject jsonConfig = loadConfigFile(configPath, "detect_only_test");
// NEW: JSONObject jsonConfig = loadConfigFileLegacy(configPath);
```

**Proper Fix Needed:**
- Update `ConfigManagerBridge.java` to use `.venv/bin/python` instead of `python3`
- Or ensure Python bridge activates virtual environment before execution

**Status:** CLI mode works with legacy config, but full pipeline still affected.

---

## 🔧 INFRASTRUCTURE ISSUES

### Issue #4: Session Environment Setup Time Waste
**Severity:** MEDIUM (RESOLVED)  
**Impact:** 20+ minutes per session rediscovering basic facts

**Description:**
Every session required rediscovering:
- Python virtual environment location and activation
- Correct Maven build directories  
- JAR file and classpath locations
- Difference between `classpath.txt` vs `runtime-classpath.txt`

**Solution Applied:**
Created comprehensive documentation in `StructureDocs/Structure_August_30_2025_Environment_Fix.md` with:
- Exact commands for environment setup
- Absolute file paths for all critical components
- Two different analysis modes (CLI vs Makefile)
- Common mistakes to avoid

**Status:** RESOLVED - Future sessions should reference this documentation.

---

### Issue #5: Classpath File Naming Inconsistency
**Severity:** LOW  
**Impact:** Build system confusion

**Description:**
Different tools expect different classpath file names:
- CLI testing expects: `target/runtime-classpath.txt`
- Makefile expects: `target/classpath.txt`
- Both contain same dependencies but different names cause build failures

**Location:** 
- Maven commands generate different output files
- Makefile `verify-build` target looks for `classpath.txt`

**Workaround:** Use appropriate Maven command for each use case:
```bash
# For CLI: mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
# For Makefile: mvn compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

**Status:** DOCUMENTED - Included in environment procedures.

---

## 📊 PERFORMANCE ISSUES

### Issue #6: Python-Java Bridge Handoff Failure
**Severity:** UNKNOWN (Blocked by Issue #1)  
**Impact:** Complete pipeline analysis fails

**Description:**
Full pipeline via `make tune-sds-ij` reports `"lane_count": 0` while CLI mode finds 8 lanes on same preprocessed image.

**Evidence:**
- Pipeline result: `run_report.json` shows `"lane_count": 0, "band_count": 0`
- CLI result: `8 lanes detected` on `stage1_norm.png`
- Same preprocessing, different detection results

**Status:** Investigation blocked until core lane detection algorithm fixed (Issue #1).

**Investigation Plan:**
1. Fix core detection algorithm first
2. Test if Python bridge calls same Java detection code
3. Verify parameter passing between Python and Java components
4. Check if preprocessing handoff works correctly

---

## 🎯 TESTING GAPS

### Issue #7: Lack of Ground Truth Validation
**Severity:** MEDIUM  
**Impact:** Cannot verify detection accuracy improvements

**Description:**
No systematic validation of lane detection accuracy against manually verified ground truth.

**Current State:**
- Assume `samples/sds_gel.jpg` has 12 lanes (user statement)
- No other images have verified lane counts
- Detection claims success but may be inaccurate

**Required:**
- Manual lane counting for all test images
- Establish ground truth dataset
- Systematic accuracy metrics (precision, recall, F1)
- Validation across different gel types

---

### Issue #8: No Regression Testing Framework
**Severity:** LOW  
**Impact:** Risk of breaking working functionality during fixes

**Description:**
Changes to detection algorithm lack automated testing to ensure improvements don't break existing functionality.

**Recommendation:**
- Establish baseline detection results for current algorithm
- Create automated test suite for detection accuracy
- Test across multiple image types and configurations
- Track detection performance metrics over time

---

## 📝 INVESTIGATION PRIORITIES

### High Priority
1. **Debug Issue #1** - Core detection algorithm failure (5/12 lanes)
2. **Fix Issue #2** - Remove biased target matching
3. **Investigate Issue #6** - Python-Java bridge handoff (after #1 fixed)

### Medium Priority  
4. **Address Issue #7** - Establish ground truth validation
5. **Resolve Issue #3** - Python environment configuration (proper fix)

### Low Priority
6. **Improve Issue #8** - Regression testing framework
7. **Clean up Issue #5** - Classpath naming standardization

## 📚 REFERENCE INFORMATION

**Test Environment:**
- Java: OpenJDK 17.0.15
- Python: 3.13.4 in `.venv/`  
- Maven: 3.9.11
- Test Image: `samples/sds_gel.jpg` → `output/sds_test/stage1_norm.png`
- ROI: x=50, y=100, w=800, h=400

**Key Files:**
- Main algorithm: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
- CLI interface: `autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java`  
- Python bridge: `autodense_autotune/java_bridge.py`
- Config files: `configs/sds.yaml`, `configs/etbr.yaml`, `configs/colony.yaml`

The core lane detection algorithm fix (Issue #1) is the critical path blocking all other functionality improvements.