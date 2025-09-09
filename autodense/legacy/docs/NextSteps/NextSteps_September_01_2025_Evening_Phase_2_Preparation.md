# Next Steps: September 01, 2025 Evening - Phase 2 Preparation

> **Doc Meta**
> - **Purpose:** Phase 2 preparation following successful Phase 1 truth preservation implementation
> - **Scope:** Configuration unification, band detection investigation, and Phase 2 implementation roadmap
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-01

## 🎯 IMMEDIATE PRIORITIES FOR NEXT SESSION

### **Phase 2: Configuration Unification & Band Independence** [HIGH PRIORITY]

Following the NextSteps_September_01_2025_Truth_Preservation_AI_Integration.md roadmap:

#### **2.1 Unified Configuration Schema** [CRITICAL - WEEK 2]
**Current Issue**: Configuration warfare between conflicting sections
- **SDS Config**: `prominence_frac: 0.06` (detect) vs `prominence: 0.30` (detection) 
- **Parameter Routing**: Multiple overlapping sections causing conflicts
- **Impact**: Good parameters missing due to schema inconsistencies

**Action Required**:
```yaml
# Target unified structure:
pre:
  invert_polarity: auto
  normalize_intensity: true
  gaussian_sigma: 0.0
  
detect:
  lanes:
    min_peak_distance_frac: 0.04
    prominence_frac: 0.06
    baseline: 
      method: "percentile"
      window_frac: 0.02
  bands:
    min_peak_distance_px: 15
    prominence_frac: 0.08
    baseline:
      method: "percentile"
      window_frac: 0.015
```

**Files to Modify**:
- `configs/sds.yaml`, `configs/etbr.yaml`, `configs/colony.yaml`
- `AutotuneAnalysisCLI.java` configuration parsing sections

#### **2.2 Band Detection Independence** [HIGH PRIORITY]
**Current Issue**: Band detection consistently returns 0 across all pipelines
- **SDS**: 7 lanes detected, 0 bands detected
- **EtBr**: 11 lanes detected, 0 bands detected
- **Root Cause**: Band detection parameters likely coupled to lane detection

**Investigation Required**:
1. **Isolate band detection parameters** from lane detection influence
2. **Test band detection independently** with known band-containing images  
3. **Validate band detection algorithm** correctness
4. **Implement separate parameter spaces** for lane vs band detection

**Files to Investigate**:
- `GelAnalysisTools.java` - band detection implementation
- `AutotuneAnalysisCLI.java` - band detection parameter routing
- Configuration files - band vs lane parameter conflicts

### **Phase 1 Completion Validation** [MEDIUM PRIORITY]

#### **Success Criteria Verification**
✅ **COMPLETE** - All Phase 1 success criteria met:
- Raw counts match log messages (`robust detection found X peaks`)
- No count inflation (report actual measurements) 
- Clear reconciliation explanations when counts differ

#### **Integration Testing Required** [NEXT SESSION START]
- Test `--no-exit` flag with Python bridge integration
- Validate dual reporting with optimization workflow simulation
- Confirm scoring function behavior under parameter variation

## 🔍 INVESTIGATION PRIORITIES

### **Band Detection Root Cause Analysis**

**Hypothesis**: Band detection failure may be caused by:
1. **Parameter Conflicts**: Lane detection parameters overriding band parameters
2. **Algorithm Issues**: Band detection algorithm not tuned for current preprocessing
3. **Configuration Routing**: Band-specific config not reaching detection algorithms
4. **Threshold Problems**: Band detection thresholds too restrictive

**Testing Approach**:
1. **Isolate band detection**: Test with known band-containing gel images
2. **Parameter sweep**: Test various band detection thresholds independently
3. **Algorithm validation**: Verify band detection algorithm correctness
4. **Configuration debugging**: Trace parameter routing from config to algorithm

### **Configuration Schema Analysis**

**Current Problem Areas**:
- Multiple conflicting parameter sources (`detect` vs `sds` vs `etbr` sections)
- Inconsistent parameter naming conventions
- Overlapping parameter responsibilities
- Missing parameter validation

**Analysis Needed**:
1. **Map parameter flow**: From YAML → Java parsing → algorithm execution
2. **Identify conflicts**: Parameters that override each other
3. **Design unified schema**: Single source of truth for all parameters
4. **Plan migration**: Safe transition from current to unified schema

## 📋 TECHNICAL DEBT & IMPROVEMENTS

### **Code Quality Enhancements**

**Phase 1 Success Principles to Maintain**:
- **Raw counts sacred**: Never overwrite measurements
- **Explicit failures over fallbacks**: Prefer diagnostic failures to artificial completion
- **Agent-assisted implementation**: Use specialized agents for complex modifications
- **Comprehensive testing**: Validate all success criteria before proceeding

### **Documentation Updates Required**

1. **Update Phase 2 progress** in NextSteps_September_01_2025_Truth_Preservation_AI_Integration.md
2. **Document band detection investigation** findings and solutions
3. **Create configuration migration guide** for unified schema transition
4. **Update CLAUDE.md** with Phase 2 implementation details

## 🎯 SUCCESS METRICS FOR NEXT SESSION

### **Phase 2.1 Configuration Unification**
- [ ] Unified YAML schema implemented across all three config files
- [ ] Parameter routing conflicts resolved
- [ ] All three pipelines working with unified configuration
- [ ] Backward compatibility maintained during transition

### **Phase 2.2 Band Detection Resolution** 
- [ ] Root cause of 0 band detection identified and fixed
- [ ] Band detection working independently from lane detection
- [ ] At least one pipeline showing successful band detection (>0 bands)
- [ ] Band detection parameters properly isolated and configurable

### **Integration Readiness**
- [ ] `--no-exit` flag tested with actual Python bridge calls
- [ ] Dual reporting system validated under parameter variations
- [ ] Scoring function tested with different configuration scenarios
- [ ] System ready for Phase 3 (AI optimization integration)

## 🚧 POTENTIAL BLOCKERS

### **Configuration Migration Risks**
- **Backward Compatibility**: Existing workflows may break during schema unification
- **Parameter Conflicts**: Some conflicts may require algorithm changes, not just config changes
- **Testing Coverage**: Need comprehensive testing across all three analysis types

### **Band Detection Complexity**
- **Algorithm Issues**: Band detection algorithm may need fundamental fixes
- **Image Quality**: Current test images may not contain detectable bands
- **Parameter Sensitivity**: Band detection may require very precise parameter tuning

## 🔗 DEPENDENCIES

### **Phase 1 Foundation** ✅ COMPLETE
- Truth preservation system operational
- Dual reporting implemented and tested
- Scoring function working correctly
- AI integration infrastructure in place

### **Phase 2 Prerequisites**
- Configuration schema analysis and design
- Band detection algorithm investigation
- Test image validation (ensure test gels actually contain bands)
- Parameter isolation strategy

---

**Next session should begin with Phase 2.1 (Configuration Unification) while simultaneously investigating the band detection issue (Phase 2.2). The strong foundation from Phase 1 enables confident progression to these next challenges.**