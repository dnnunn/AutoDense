# Issues: September 01, 2025 - Band Detection and Configuration Conflicts

> **Doc Meta**
> - **Purpose:** Issues discovered during Phase 1 truth preservation implementation requiring investigation and resolution
> - **Scope:** Band detection failures, configuration conflicts, and integration considerations
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-01

## 🚨 CRITICAL ISSUES

### **Issue #1: Universal Band Detection Failure**

**Severity**: HIGH  
**Status**: DISCOVERED - Needs Investigation  
**Impact**: All gel analysis pipelines report 0 bands detected

#### **Symptoms**
- **SDS-PAGE**: 7 lanes detected, 0 bands detected
- **EtBr**: 11 lanes detected, 0 bands detected
- **Consistent Pattern**: Lane detection working, band detection consistently failing

#### **Evidence**
```
[RESULT_DEBUG] bandResult: {
  "bands_total": 0,
  "lanes_analyzed": 7,
  "bands_raw": 0,
  "bands_reconciled": 0
}
```

#### **Potential Root Causes**
1. **Algorithm Issues**: Band detection algorithm may have fundamental problems
2. **Parameter Conflicts**: Band detection parameters overridden by lane parameters
3. **Configuration Routing**: Band-specific config not reaching detection algorithms
4. **Threshold Problems**: Band detection thresholds too restrictive for test images
5. **Test Image Issues**: Current test images may not contain detectable bands

#### **Investigation Required**
- **Isolate band detection**: Test with known band-containing gel images
- **Parameter debugging**: Trace band detection parameter routing
- **Algorithm validation**: Verify band detection algorithm correctness
- **Threshold analysis**: Test various band detection sensitivity settings

#### **Files to Investigate**
- `GelAnalysisTools.java` - `performBandDetection()` method
- `AutotuneAnalysisCLI.java` - band detection parameter parsing
- Configuration files - band vs lane parameter routing
- Test gel images - validate they actually contain detectable bands

---

### **Issue #2: Configuration Schema Warfare**

**Severity**: HIGH  
**Status**: IDENTIFIED - Needs Resolution  
**Impact**: Parameter conflicts causing suboptimal detection performance

#### **Symptoms**
- **SDS Config Conflicts**: `prominence_frac: 0.06` (detect) vs `prominence: 0.30` (sds section)
- **Multiple Parameter Sources**: `detect`, `sds`, `etbr`, `colony` sections with overlapping responsibilities
- **Parameter Routing Confusion**: Unclear which parameters take precedence

#### **Evidence from Configuration Debugging**
```
[CONFIG_DEBUG] detect section: {"prominence_frac":0.06,...}
[CONFIG_DEBUG] sds section: {"prominence":0.3,...}
```

#### **Impact Analysis**
- **Good parameters may be overridden** by conflicting sections
- **Detection algorithms receive inconsistent parameters**
- **Difficult to optimize** when parameter sources are unclear

#### **Resolution Strategy**
1. **Design unified schema** with clear parameter hierarchy
2. **Migrate existing configs** to unified structure
3. **Update parameter routing** in Java code
4. **Maintain backward compatibility** during transition

---

## ⚠️ MEDIUM PRIORITY ISSUES

### **Issue #3: CSV File Generation Inconsistency**

**Severity**: MEDIUM  
**Status**: OBSERVED - Needs Clarification  
**Impact**: CSV artifacts not generated when detection count is 0

#### **Symptoms**
- **Colony Report**: Lists CSV artifacts (`colonies.csv`, `size_blue_bins.csv`) but files not present
- **SDS/EtBr Reports**: No CSV artifacts section when 0 bands detected

#### **Expected vs Actual**
```json
// Colony report claims:
"artifacts": {
  "colonies_csv": "colonies.csv",
  "size_blue_bins.csv": "size_blue_bins.csv"
}
// But files not present in output directory
```

#### **Investigation Needed**
- **Determine intended behavior**: Should CSV files be generated with 0 detections?
- **Fix generation logic**: Either generate empty CSVs or remove from artifacts list
- **Standardize across pipelines**: Consistent CSV generation behavior

---

### **Issue #4: Process Hanging Root Cause Unknown**

**Severity**: MEDIUM  
**Status**: WORKAROUND IMPLEMENTED  
**Impact**: CLI processes hang without System.exit(0), blocking AI integration

#### **Current Workaround**
- **System.exit(0)** forces termination for CLI usage
- **--no-exit flag** skips System.exit() for AI integration

#### **Root Cause Still Unknown**
- **ImageJ threads**: Likely ImageJ background threads not terminating properly
- **Context disposal**: ImageJ context disposal may be insufficient
- **Service cleanup**: Other services may have lingering threads

#### **Future Investigation**
- **Thread dump analysis**: Identify which threads prevent clean shutdown
- **ImageJ lifecycle**: Proper ImageJ context and service shutdown
- **Service registry**: Ensure all services properly disposed

#### **Risk Assessment**
- **Low risk for current usage**: Workaround functional for both CLI and AI integration
- **Technical debt**: Should be resolved for cleaner architecture
- **Integration impact**: May cause issues in complex optimization scenarios

---

## 📋 MINOR ISSUES

### **Issue #5: Log Message Inconsistencies**

**Severity**: LOW  
**Status**: OBSERVED  
**Impact**: Minor logging inconsistencies affecting audit trail clarity

#### **Symptoms**
- Some truth preservation messages use different formats
- Reconciliation explanations vary in detail level
- Debug vs info log level inconsistencies

#### **Resolution**
- **Standardize logging formats** across all detection types
- **Consistent message templates** for truth preservation events
- **Unified log levels** for similar event types

---

## 🔍 INVESTIGATION RECOMMENDATIONS

### **Priority 1: Band Detection Analysis**
1. **Create test with known band images**: Use gel images known to contain visible bands
2. **Parameter sensitivity analysis**: Test band detection with various sensitivity settings
3. **Algorithm step-through**: Debug band detection algorithm execution
4. **Comparison with working systems**: Compare with known-good band detection implementations

### **Priority 2: Configuration Unification**
1. **Map current parameter flow**: Document how parameters flow from YAML to algorithms
2. **Design unified schema**: Create comprehensive parameter schema
3. **Plan migration strategy**: Safe transition approach with backward compatibility
4. **Implement and test**: Unified configuration with comprehensive testing

### **Priority 3: Integration Testing**
1. **Python bridge testing**: Test --no-exit flag with actual Python subprocess calls
2. **Optimization simulation**: Simulate iterative parameter optimization workflows
3. **Performance impact**: Measure impact of dual reporting on system performance

---

## 📊 RISK ASSESSMENT

### **High Risk Issues**
- **Band Detection Failure**: May indicate fundamental algorithm problems
- **Configuration Conflicts**: Could prevent effective AI optimization

### **Medium Risk Issues**
- **CSV Generation**: Affects data export workflows
- **Process Hanging**: Technical debt that may cause future integration issues

### **Low Risk Issues**
- **Logging Inconsistencies**: Minor quality issues

### **Mitigation Strategies**
- **Systematic investigation**: Use specialized agents for complex debugging
- **Incremental fixes**: Address issues one at a time with full testing
- **Fallback plans**: Maintain working system while investigating issues
- **Documentation**: Record all investigation findings for future reference

---

**These issues represent the technical debt and challenges discovered during Phase 1 implementation. The high-priority items (band detection and configuration conflicts) should be addressed in Phase 2, while lower-priority items can be tackled as time permits.**