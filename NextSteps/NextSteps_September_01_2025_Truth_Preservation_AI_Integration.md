# Next Steps: September 01, 2025 - Truth Preservation & AI Integration

> **Doc Meta**
> - **Purpose:** Comprehensive remediation plan for AutoDense detection issues based on external audit findings
> - **Scope:** Truth inflation fixes, AI orchestration implementation, configuration unification, and detection algorithm corrections
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-01

## 🚨 CRITICAL FINDINGS FROM EXTERNAL AUDIT

### **Root Cause Analysis**

**1. Epistemological Heresy: Truth Inflation Over Raw Measurements**
- **SDS**: Raw detection finds 7 peaks → inflated to report "expected=10 found=10"
- **EtBr**: Raw detection finds 11 peaks → expected 20 (at least honest about shortfall)
- **Core Issue**: "Priors are for choosing among candidate explanations, not overwriting the measurement"

**2. AI Orchestration Disconnect**
- **Current Reality**: Java CLI bypasses Python orchestrator entirely
- **Evidence**: No `GEMINI: enabled/disabled` markers in logs, no iterative proposals
- **Path Taken**: Direct `AutotuneAnalysisCLI.java` → `detection_results.json` → exit (no AI involvement)

**3. Configuration Schema Warfare**
- **SDS Config**: `prominence_frac: 0.06` (detect) vs `prominence: 0.30` (detection) - fighting parameters
- **Parameter Routing**: Multiple overlapping sections causing conflicts
- **Impact**: Good parameters go missing due to schema inconsistencies

---

## 🎯 4-PHASE REMEDIATION PLAN

### **PHASE 1: Truth Preservation & Data Integrity** [CRITICAL - WEEK 1]

**Mission**: Stop lying about measurements, preserve raw detection truth

#### **1.1 Remove Biased Count Inflation Logic**
- **Target File**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
- **Target Lines**: 270-290 (biased adjustment logic)
- **Action**: Remove logic that forces `peaksOld.size()` to match `expectedLanes`
- **Preserve**: `logger.info("robust detection found %d peaks", peaksOld.size())`
- **Result**: Always report actual detected peaks as ground truth

#### **1.2 Implement Dual Reporting System**
- **Target Files**: 
  - `AutotuneAnalysisCLI.java` (result JSON construction)
  - `GelAnalysisTools.java` (detection result structures)
- **New Fields**:
  ```json
  {
    "lanes_raw": 7,           // Actual detected peaks
    "lanes_reconciled": 10,   // Best candidate after scoring
    "bands_raw": 0,           // Actual detected bands  
    "bands_reconciled": 0,    // Best candidate after scoring
    "reconciliation_explanation": "Geometry strong, coverage moderate → applied soft prior adjustment"
  }
  ```

#### **1.3 Implement Scoring Function for Reconciliation**
- **Replace**: Hard count overrides and forced matches
- **With**: Best candidate selection via weighted scoring:
  ```
  score = w1*coverage_total + w2*stability + w3*geometry_score + w4*prior_match - w5*violations
  ```
- **Principle**: Select best candidate among safe parameter tweaks, keep raw counts sacred

#### **Success Criteria Phase 1**:
- ✅ Raw counts always match log messages (`robust detection found X peaks`)
- ✅ No count inflation - report actual measurements
- ✅ Clear explanation when reconciled counts differ from raw

---

### **PHASE 2: Configuration Unification & Band Independence** [HIGH - WEEK 2]

**Mission**: Single schema, proper band detection isolation

#### **2.1 Unified Configuration Schema**
- **Target Files**: All config files (`sds.yaml`, `etbr.yaml`, `colony.yaml`)
- **New Structure**:
  ```yaml
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
        clamp_min_px: 3
        clamp_max_px: 12
        quantile: 0.10
    bands:
      min_peak_distance_px: 15
      prominence_frac: 0.08
      baseline:
        method: "percentile"  
        window_frac: 0.015     # Smaller window for bands
        clamp_min_px: 2        # Tighter clamps
        clamp_max_px: 8
        quantile: 0.08
        
  segmentation:  # For colonies
    threshold_radius: 15
    min_colony_size: 5
    max_colony_size: 1000
    
  priors:
    expected_lanes: 12
    expected_band_range: [5, 50]
  ```

#### **2.2 Separate Band Detection Implementation**
- **Target Files**: `BandDetectionTools.java`, `GelAnalysisTools.java`
- **Independent Baseline**: Smaller window (2-8px) vs lanes (3-12px)
- **Per-Lane Profiles**: Vertical analysis within detected lane boundaries
- **Tiered Thresholds**: Different prominence parameters for bands vs lanes
- **Ladder Anchoring**: Use ladder lane for expected vertical spacing

#### **2.3 Configuration Migration & Validation**
- **Action**: Update all config files to new unified schema
- **Remove**: Legacy `detection`, `sds`, `etbr` sections causing conflicts
- **Update Code**: Ensure parameter routing uses only new unified paths
- **Test**: Verify no "parameter not found" errors after migration

#### **Success Criteria Phase 2**:
- ✅ Single configuration schema across all analysis types
- ✅ Independent band detection with smaller baseline windows
- ✅ No parameter routing conflicts between sections

---

### **PHASE 3: AI Orchestration & Rich Telemetry** [HIGH - WEEK 3-4]

**Mission**: Actually engage Gemini in optimization loop

#### **3.1 Expand Observable Telemetry**
- **Target Files**: `AutotuneAnalysisCLI.java`, telemetry generation code
- **Add Geometry Observables**:
  - `lane_parallelism_score`: How parallel are detected lanes?
  - `lane_spacing_cv`: Coefficient of variation in lane spacing
  - `lane_width_mean/std`: Statistics on lane width consistency
- **Add Coverage Observables**:  
  - `coverage_lanes[]`: Per-lane coverage percentages
  - `coverage_bands[]`: Per-band coverage percentages
  - `coverage_total`: Overall feature coverage
- **Add Baseline Health**:
  - `baseline_pre_med`: Median before baseline removal
  - `baseline_post_med`: Median after baseline removal  
  - `baseline_post_max`: Maximum after baseline removal
- **Add Stability Metrics**:
  - Counts under ±10% threshold variations
  - Counts under ±1-2px min-distance variations
  - `count_stability_score`: Overall robustness metric

#### **3.2 Wire Gemini AI into Optimization Loop**
- **Current Issue**: `AutotuneAnalysisCLI.java` bypasses Python orchestrator
- **Solution**: Route execution through `python -m autodense_autotune.cli run --mode ai`
- **Add Clear Logging**:
  - `GEMINI: enabled` when API key found and mode=ai
  - `GEMINI: disabled (grid only)` when no AI involvement
  - `GEMINI/PROPOSE:` markers for proposal attempts
  - `GEMINI/CRITIQUE:` markers for validation steps
- **Persistence Strategy**:
  - Per-attempt: `attempt_001/run_report.json`, `attempt_002/run_report.json`
  - Final chosen: `final_report.json` with optimization metadata
  - Include `optimization_mode`, `attempt_count`, parameter deltas tried

#### **3.3 AI Proposal System Implementation**
- **Target Files**: `autodense_autotune/` Python modules
- **Input**: Rich observables (geometry, coverage, baseline health) - NOT pixels
- **Process**: 
  1. Gemini analyzes current metrics and identifies bottlenecks
  2. Proposes bounded parameter adjustments within safe ranges
  3. Helper critic validates proposals against physics constraints
  4. Execute parameter test → measure results → repeat
- **Safety Gates**: 
  - Enforce parameter bounds before applying any AI patches
  - Require improvement in coverage, stability, or geometry
  - Block proposals that violate ladder physics or cause violations

#### **Success Criteria Phase 3**:
- ✅ `GEMINI: enabled` appears in logs for AI-driven runs  
- ✅ Multiple optimization attempts visible in logs and artifacts
- ✅ Rich telemetry enables Gemini to reason about detection quality
- ✅ AI proposes sensible parameter adjustments based on observables

---

### **PHASE 4: Colony Detection & Full Pipeline Validation** [MEDIUM - WEEK 5]

**Mission**: Fix colony analysis, validate entire architecture

#### **4.1 Colony Binary Contract & Color Physics**
- **Target Files**: Colony detection modules, `ColonyDetector.java`
- **Issues Identified**: `colony_count=0` suggests binary mask problems
- **Binary Contract**: Guarantee 8-bit {0,255} binary mask before particle analysis
- **Color Preservation**: Maintain B/R ratio, Lab b* for X-gal blue/white detection
- **ROI Restriction**: Limit normalization to plate ROI, not entire photo
- **Add Reporting**: 
  - `foreground_fraction`: Percentage of pixels in foreground
  - `median_blob_area`: Typical colony size
  - `mask_type`: Verification that mask is truly binary

#### **4.2 Stability Testing Under Micro-Jitters**
- **Implementation**: Test parameter robustness under small variations
- **Variations**: ±10% threshold changes, ±1-2px min-distance adjustments  
- **Scoring**: Include stability metrics in reconciliation scoring
- **Gating**: Require minimum stability threshold before accepting parameter changes
- **Report**: Min/max counts across variations, stability confidence score

#### **4.3 End-to-End Pipeline Validation**
- **Test All Three Analysis Types**: SDS-PAGE, EtBr, Colony with new architecture
- **Verification Checklist**:
  - Raw counts preserved and honest
  - AI actually invoked (clear log evidence) 
  - Parameters optimized through multiple iterations
  - Rich telemetry provides actionable insights
- **Success Targets**:
  - **SDS**: 12/12 lanes detected (raw), some bands detected (>0)
  - **EtBr**: Improved lane count (>11), functional band detection  
  - **Colony**: Non-zero colony detection with color classification

#### **Success Criteria Phase 4**:
- ✅ Colony analysis produces non-zero, reasonable results
- ✅ All three analysis types work with unified architecture
- ✅ Stability testing validates parameter robustness
- ✅ Full pipeline demonstrates AI-driven optimization

---

## 📋 EXECUTION STRATEGY

### **Dependencies & Sequencing**
1. **Phase 1 (Truth Preservation)**: Foundational - must complete first
2. **Phase 2 (Config Unification)**: Can run parallel to Phase 1
3. **Phase 3 (AI Integration)**: Requires Phases 1-2 complete  
4. **Phase 4 (Validation)**: Requires all previous phases

### **Critical Success Indicators**
- **✅ Week 1**: Raw counts match detection logs, no count inflation
- **✅ Week 2**: Single config schema, independent band detection working
- **✅ Week 3**: `GEMINI: enabled` logs, multiple optimization attempts visible
- **✅ Week 4**: AI proposals based on rich telemetry, parameter improvements
- **✅ Week 5**: All three analysis types producing reasonable results

### **Risk Mitigation**
- **Preserve Working Functionality**: Keep existing detection working during migration
- **Incremental Testing**: Validate each phase before proceeding
- **Rollback Capability**: Maintain current configs as backup during schema migration
- **Clear Logging**: Ensure all changes are traceable through enhanced telemetry

---

## 🎯 IMMEDIATE NEXT ACTIONS

### **This Week (Phase 1 Focus)**
1. **Analyze Current Count Inflation**: Examine `LaneDetector.java:270-290` for biased adjustment logic
2. **Design Dual Reporting**: Plan JSON schema for raw vs reconciled counts  
3. **Implement Scoring Function**: Design weighted scoring for candidate selection
4. **Test Truth Preservation**: Ensure raw counts always reflect actual detections

### **Key Files to Modify First**
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java`  
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml`

### **Success Validation**
Run current SDS test after Phase 1 changes:
```bash
./build.sh test-sds
```
**Expected**: Logs show "robust detection found 7 peaks", JSON reports `lanes_raw: 7`, with explanation of any reconciliation.

---

## 📚 REFERENCE MATERIALS

### **Key Audit Findings**
- **Truth > Prior**: Never inflate measurements to match expectations
- **AI Disconnection**: Current path bypasses Gemini orchestrator entirely  
- **Config Warfare**: Multiple conflicting sections cause parameter routing failures

### **Architecture Principles**
- **Raw measurements are sacred** - never overwrite, always preserve
- **Reconciliation via scoring** - choose best candidate, explain differences
- **Rich observables for AI** - geometry, coverage, stability (not pixels)
- **Single source of truth** - unified configuration schema
- **Bounded optimization** - AI proposals within safety constraints

### **Current Detection Status**
- **SDS**: 7 peaks detected (raw) vs 12 expected, 0 bands detected
- **EtBr**: 11 peaks detected (raw) vs 20 expected, 0 bands detected  
- **Colony**: 0 colonies detected (complete failure)

This remediation plan addresses the fundamental epistemological issue (truth vs priors), architectural disconnect (AI not invoked), and technical detection problems (config conflicts, band detection failure) in a systematic, testable approach.