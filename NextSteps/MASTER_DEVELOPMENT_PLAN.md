# AutoDense Complete Development Plan: Phases I-VII Integration

> **Doc Meta**
> - **Purpose:** Master development plan integrating all phases and appendices for AutoDense AI optimization system
> - **Scope:** Complete roadmap from truth preservation through final lab workflows
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 🎯 Executive Summary

This master plan integrates seven phases of AutoDense development, transitioning from rigid tool orchestration to an intelligent hybrid system where **Gemini advises and AutoDense measures**. Each phase builds systematically on the previous, maintaining deterministic pipeline integrity while adding increasingly sophisticated AI optimization capabilities.

---

## 🏗️ Phase Overview

| Phase | Status | Purpose | Key Deliverables |
|-------|--------|---------|------------------|
| **I** | ✅ **COMPLETE** | Truth Preservation | Dual reporting (raw vs reconciled), no measurement overrides |
| **II** | ✅ **COMPLETE** | Flight Recorder Telemetry | Comprehensive metrics without pixels for AI optimization |
| **IIa** | 🎯 **NEW** | Real-Time Quality Coach | Live analysis guidance with one-click parameter fixes |
| **III** | ✅ **COMPLETE** | Parameter Independence | Separated band/lane configs, configurable detection parameters |
| **IV** | 🚧 **PLANNED** | Vision-Assist Hybrid | Limited pixel access for parameter optimization on failures |
| **V** | 🚧 **PLANNED** | User-Triggered Control | Natural language interface, feedback-driven optimization |
| **VI** | 🚧 **PLANNED** | Interactive Assist Tools | BandAssist, ColonyAssist for user-guided analysis |
| **VII** | 🚧 **PLANNED** | Production Workflows | Complete lab workflows with QC gates and reporting |

---

## ✅ Completed Foundations (Phases I-III)

### **Phase I: Truth Preservation System**
**Principle**: AI never overwrites measurements, only suggests parameter adjustments

**Completed Features**:
- ✅ Dual reporting: `*_raw` (detector output) vs `*_reconciled` (candidate selection)  
- ✅ No measurement overrides by AI suggestions
- ✅ Complete audit trail of parameter changes
- ✅ Deterministic pipeline outputs preserved

**Validation**: All current test runs show proper raw/reconciled separation

### **Phase II: Comprehensive Telemetry System** 
**Principle**: Provide AI with decisive metrics without pixel access

**Completed Features**:
- ✅ Lane metrics: `profile_zero_frac_after`, baseline health, geometry
- ✅ Band metrics: per-lane zero fractions, prominence used, min_distance_px_used
- ✅ Coverage metrics: numerator/denominator breakdown for debugging
- ✅ Colony metrics: filter chain ledger (200→15 tracking), Lab-b histograms
- ✅ Stability metrics: count/classification jitter detection

**Validation**: Current runs show comprehensive telemetry (EtBr: 11 lanes/2 bands, SDS: 7 lanes/24 bands, Colony: 15 with Lab classification)

### **Phase IIa: Real-Time Quality Coach** ⭐ *NEW*
**Principle**: Provide immediate, actionable guidance during analysis to prevent measurement errors

**Integration Features**:
- ✅ **Coach Rules Engine**: Stateless rule evaluation for SDS-PAGE, EtBr, and Colony analysis
- ✅ **Real-time Hints**: Confidence-scored suggestions (error/warn/info) based on telemetry
- ✅ **One-click Fixes**: Reversible parameter adjustments with immediate re-analysis
- ✅ **Measurement Integrity**: Focus on quantification accuracy over workflow convenience
- ✅ **Clone-based Safety**: Never modify original user data during optimization

**Implementation Status**:
- ✅ `autodense/coach_rules.py` - Rule engine with SDS/EtBr/Colony quality detection
- ✅ `ui/CoachBanner.tsx` - React component with sticky banner and action buttons
- ✅ `backend/app.py` - FastAPI endpoints for `/api/coach/hints` and `/api/coach/rerun`
- ✅ Coach integration guide with step-by-step workflow instructions

**Coach Quality Rules**:
- **SDS-PAGE**: Saturation detection (>1.5% pixels clipped), lane-wise background assessment, ladder fit validation (R² < 0.985)
- **EtBr Gels**: Smear vs discrete band detection, exposure optimization, mode switching to mass-only
- **Colony Plates**: Illumination correction, touching colony separation, adaptive thresholding

**Success Metrics**:
- Hint acceptance rate >70% for high-confidence suggestions
- Measurable quality improvement (R² +0.01, band count +1, F1 score +0.1)
- User engagement: coach usage frequency across analysis types
- Error reduction: decreased analysis failures post-coaching

### **Phase III: Parameter Independence**
**Principle**: Complete separation of detection parameter domains

**Completed Features**:
- ✅ Band/lane parameter independence: bands use `win=2px` vs lanes `win=15px`
- ✅ Configurable `min_peak_distance_px` for band detection
- ✅ Separate baseline configurations: `detect.baseline` vs `bands.baseline`
- ✅ --no-exit flag integration for optimizer loops
- ✅ Configuration-aware detection across all pipelines

**Validation**: Logs confirm `[BASELINE_FIX] SUCCESS: Using bands.baseline parameters`

---

## 🚧 Development Phases IV-VII

### **Phase IV: Vision-Assist Hybrid Mode**
**Status**: Ready for implementation  
**Timeline**: 2 days  
**Principle**: Gemini provides visual parameter optimization with bounded, safety-validated suggestions

#### **Core Architecture**
```yaml
vision:
  mode: "off"             # off | assist | qc
  max_side_px: 1024       # downscale for privacy
  include_stage: "stage1_norm"
  crop: "auto"
  scrub:
    strip_exif: true
    blur_text: true
    grayscale_gels: true

  allowlist:              # BOUNDED parameter ranges
    lanes:
      min_peak_distance_frac: [0.02, 0.06]
      prominence_frac:        [0.02, 0.12]
    bands:                  # Completely independent from lanes
      baseline.window_frac:   [0.005, 0.03]
      baseline.quantile:      [0.05, 0.20]
      min_peak_distance_px:   [6, 18]
    colonies:
      threshold.method:       ["Otsu", "Phansalkar"]
      colorspace:             ["Lab"]
```

#### **Trigger Conditions**
- **Detection failures**: `lanes_raw == 0` OR `bands_raw == 0`
- **Baseline issues**: `baseline_post_med ≈ 0` OR `profile_zero_frac_after > 0.2`
- **Coverage problems**: `coverage_total < 0.2` OR `count_stability_score < 0.7`
- **Colony issues**: `final_count << components_raw` OR classifier not applied

#### **Coach Integration Points** 🎯
**Real-time Monitoring**: Coach evaluates metrics after each analysis step
- Monitor lane detection failures → suggest parameter relaxation
- Detect baseline issues → recommend lane-wise background correction
- Track band detection problems → propose prominence/distance adjustments
- Watch for saturation → trigger exposure compensation workflows

#### **Implementation Plan**
**Day 1**: Core modules + mock advisor + **coach monitoring integration**
- `autodense_autotune/vision/cropper.py` - ROI detection, scrubbing
- `autodense_autotune/vision/allowlist.py` - bounds validation  
- `autodense_autotune/vision/gating.py` - trigger logic
- Mock advisor for end-to-end testing

**Day 2**: Gemini integration + validation + **coach hint generation pipeline**
- `autodense_autotune/vision/advisor.py` - real API calls
- Physics validation (coverage/stability improvement)
- Test cases: EtBr baseline fix, SDS min_distance optimization, Colony Lab classification

### **Phase V: User-Triggered Natural Language Control**
**Status**: Specifications complete  
**Timeline**: 3 days  
**Principle**: Scientists guide analysis using natural language and explicit feedback

#### **Natural Language Interface**
**Supported Intents**:
- `analyze_gel` (sds_page | etbr)
- `analyze_colonies` 
- `optimize_params` (assist loop)
- `rerun_with_feedback` (user correction)
- `compare_runs`, `summarize_results`, `export_reports`

**Domain Lexicon**:
```yaml
synonyms:
  assay:
    sds_page: [sds, sds-page, coomassie, denaturing gel]
    etbr: [agarose, dna gel, ethidium, etbr]
    colonies: [plates, colony count, blue white]
  
bias_profiles:
  recall:    { lanes.prominence: -10%, bands.min_dist: -20% }
  precision: { lanes.prominence: +10%, bands.min_dist: +15% }
```

**Example Interactions**:
- *"Be a bit more sensitive on bands"* → `bands.prominence_frac: -10%`
- *"This should have ~20 lanes; favor recall but keep ladder fit ≥0.95"* → lane detection relaxation with R² constraint
- *"Count blue/white; lots of glare"* → Lab colorspace + Phansalkar thresholding

#### **Feedback Schema**
```yaml
user_feedback:
  reason: "under-counted bands"
  targets:
    lanes: 12
    bands_per_lane_min: 4
  priorities:
    bias: "recall"
    preserve_ladder_fit: 0.95
  bounds_overrides:
    bands.min_peak_distance_px: [8, 16]
```

#### **Coach Enhancement for Phase V** 🎯
**Natural Language Coach**: Extend coach to understand user intent and provide contextual guidance
- *"Be more sensitive"* → Coach suggests specific prominence/threshold adjustments
- *"Missing small bands"* → Coach proposes min_distance reduction with confidence scores
- *"Ladder looks off"* → Coach validates R² and suggests re-detection strategies

#### **Implementation Plan**
**Day 1**: NL parser + feedback ingestion + **coach natural language interpretation**
**Day 2**: Intent-to-patch conversion with lexicon
**Day 3**: Integration testing with real lab phrases

### **Phase VI: Interactive Assist Tools**
**Status**: Concept design  
**Timeline**: 4 days  
**Principle**: Human-in-the-loop refinement for complex cases

#### **BandAssist Enhancement**
- Interactive band propagation across lanes
- Confidence scoring per band
- User click → Rf propagation with validation
- Monotonic ordering enforcement

#### **ColonyAssist (New)**
- Interactive colony classification correction
- Mask refinement tools
- Blue/white classification override
- Batch reclassification with learning

#### **Coach Integration for Interactive Tools** 🎯
**Interactive Coach Guidance**: Real-time coaching during manual refinement
- **BandAssist**: Coach validates band propagation confidence, suggests refinements
- **ColonyAssist**: Coach monitors classification changes, warns of systematic bias
- **Quality Feedback Loop**: Coach learns from user interactions to improve suggestions

#### **Implementation Plan**
**Day 1-2**: BandAssist confidence scoring and validation + **integrated coach feedback**
**Day 3-4**: ColonyAssist interactive classification system

### **Phase VII: Production Lab Workflows**  
**Status**: Specifications complete  
**Timeline**: 5 days  
**Principle**: Complete analytical workflows with integrated QC and reporting

#### **Six Production Workflows**

1. **Protein Quantification** (`protein_quant_v1`)
   - Standard curves (linear/log-linear/4PL)
   - LOQ/LLOQ calculation, %CV reporting
   - QC Gate: curve R² ≥ 0.98, ≥3 valid standards

2. **Lane Comparison** (`lane_compare_v1`)  
   - MW-binned comparison with statistics
   - Holm-Bonferroni correction
   - QC Gate: ladder R² ≥ 0.95, ≥2 samples/condition

3. **Semi-quantitative PCR** (`semi_qpcr_v1`)
   - ΔΔI normalization with housekeeping
   - 95% confidence intervals
   - QC Gate: target & HK SNR ≥ threshold, no saturation

4. **BandAssist Interactive** (`band_assist_v1`)
   - User-guided band propagation
   - Confidence-based validation
   - QC Gate: per-band confidence ≥ 0.8, no crossovers

5. **Colony Time-Series** (`colony_timeseries_v1`)
   - Multi-day plate tracking
   - Growth curve fitting, X-gal classification
   - QC Gate: registration RMS ≤ threshold, ≥80% continuity

6. **ColonyAssist Interactive** (`colony_assist_v1`)
   - Interactive classification QA
   - Filter chain optimization
   - QC Gate: binary mask contract, reasonable margins

#### **Challenge Pack Structure**
```
challenge_packs/
├── protein_quant_v1/spec.yaml
├── lane_compare_v1/spec.yaml
├── semi_qpcr_v1/spec.yaml
├── band_assist_v1/spec.yaml
├── colony_timeseries_v1/spec.yaml
└── colony_assist_v1/spec.yaml
```

#### **Coach Production Integration** 🎯
**Workflow-Specific Coaching**: Specialized guidance for production analytical workflows
- **Protein Quantification**: Coach validates curve fitting, warns of standard outliers
- **Lane Comparison**: Coach ensures proper MW binning, statistical validity
- **Semi-qPCR**: Coach monitors housekeeping stability, target amplification quality
- **Time-Series**: Coach tracks registration quality, growth curve validity

**QC Gate Integration**: Coach hints become part of formal QC gate evaluation
- Error-level hints block workflow progression until resolved
- Warn-level hints require user acknowledgment or fixing
- Info-level hints provide optional optimization suggestions

#### **Implementation Plan**
**Day 1**: Protein quantification + lane comparison workflows + **integrated coach QC gates**
**Day 2**: Semi-qPCR + BandAssist workflows  
**Day 3**: Colony time-series + ColonyAssist workflows
**Day 4**: Integration testing across all workflows
**Day 5**: Documentation and validation

---

## 🛡️ Cross-Phase Safety & Compliance

### **Architectural Principles (All Phases)**
1. **Truth Preservation**: AI advises, pipeline measures (never overridden)
2. **Parameter Bounds**: All suggestions within allowlist ranges
3. **Physics Validation**: Acceptance requires metric improvement
4. **Audit Trail**: Complete logging of parameters, patches, decisions
5. **Fallback Safety**: System works without AI (telemetry-only mode)

### **Critical Requirements Verification**
- ✅ **--no-exit flag**: Fixed in java_bridge.py with prominent warnings
- ✅ **Band/lane independence**: Verified with separate baseline processing  
- ✅ **Comprehensive telemetry**: Flight recorder metrics implemented
- ✅ **Real-time coach**: Quality guidance system with measurement integrity focus
- 🔄 **Vision privacy**: EXIF stripping, text blurring in Phase IV
- 🔄 **User control**: Natural language safety bounds in Phase V

### **Quality Gates (All Phases)**
- **Coverage**: Must improve or maintain
- **Stability**: Must not degrade  
- **Physics**: Ladder R², geometry sanity preserved
- **Bounds**: All parameters within allowlist ranges
- **Reproducibility**: Deterministic re-runs with same config
- **Coach Validation**: Error-level hints must be resolved before workflow completion

---

## 📊 Success Metrics by Phase

### **Phase IV Success Criteria**
- **EtBr**: 14-20 lanes detected (vs current 11), 5+ bands (vs current 2)
- **SDS**: Maintain 24+ bands with optimized min_distance_px
- **Colony**: Proper blue/white classification (vs current all-white)
- **Vision-assist**: >90% of suggestions accepted based on metric improvement

### **Phase V Success Criteria**  
- **NL Parser**: >95% accuracy on 30-phrase test suite
- **Feedback Integration**: User corrections result in metric improvements
- **Intent Recognition**: Correct parameter patches for common lab phrases

### **Phase VI Success Criteria**
- **BandAssist**: >80% confidence on propagated bands
- **ColonyAssist**: User corrections improve classification accuracy

### **Phase VII Success Criteria**
- **Workflow Integration**: All 6 workflows pass QC gates
- **Production Ready**: Complete reporting with statistical validation
- **Lab Adoption**: Real-world validation with lab protocols

---

## 🚀 Implementation Timeline

**Current Status**: Phases I-III complete with excellent results
- EtBr: 11 lanes, 2 bands (was 0)
- SDS: 7 lanes, 24 bands (was 0-2)  
- Colony: 15 colonies with Lab classification (was RGB/broken)

**Immediate (This week)**:
- **Days 1-2**: Phase IIa Coach integration into existing telemetry system
- **Days 3-5**: Coach frontend integration and testing with current workflows

**Near-term (Next 2 weeks)**:
- **Week 1**: Phase IV Vision-Assist implementation + Coach monitoring integration
- **Week 2**: Phase V Natural Language interface + Coach NL interpretation

**Medium-term (Following month)**:
- **Week 3**: Phase VI Interactive assist tools  
- **Week 4**: Phase VII Production workflows

**Long-term**: Integration with lab workflows and user training

---

## 📚 Reference Architecture 

### **Core System Components**
- **Java Pipeline**: Deterministic detection and measurement (unchanged)
- **Python Orchestrator**: AI optimization and parameter management
- **Configuration System**: Unified YAML schema with parameter independence
- **Telemetry System**: Comprehensive metrics without pixel dependency
- **Vision System**: Bounded visual optimization for edge cases
- **NL Interface**: Natural language to parameter patch conversion

### **Data Flow**
1. **Input**: Images + configuration + optional user feedback
2. **Analysis**: Deterministic Java pipeline execution  
3. **Telemetry**: Comprehensive metrics extraction
4. **Real-time Coaching**: Quality assessment and hint generation
5. **AI Optimization**: Bounded parameter suggestions (vision + NL + coach)
6. **Validation**: Physics and safety checks + coach error resolution
7. **Output**: Truth-preserved results + optimization reports + quality coaching

**Master principle maintained throughout**: **Gemini advises, AutoDense measures, users guide, science decides.**