# AutoDense Development Reference Index

> **Doc Meta**
> - **Purpose:** Complete reference index for all AutoDense development phases, components, and implementations
> - **Scope:** Navigation guide to all documentation, code, and specifications
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 📚 Master Documentation Map

### **🎯 Primary Planning Documents**
| Document | Purpose | Status | Key Content |
|----------|---------|--------|-------------|
| **MASTER_DEVELOPMENT_PLAN.md** | Complete 7-phase roadmap | ✅ COMPLETE | Executive summary, phase overview, timeline |
| **DEFINITIVE_BUILD_REFERENCE.md** | Build/compile/test procedures | ✅ UPDATED | Paths, commands, --no-exit warnings |
| **CLAUDE.md** | Session primer & architecture | ✅ CURRENT | Project state, capabilities, known issues |

### **📋 Phase-Specific Implementation Guides**
| Phase | Document | Status | Ready for Implementation |
|-------|----------|--------|-------------------------|
| **I-III** | *Completed in main system* | ✅ DEPLOYED | Truth preservation, telemetry, parameter independence |
| **IV** | `INTEGRATED_PHASE_IV_VISION_ASSIST.md` | 📋 SPECIFIED | Vision-assist hybrid mode with bounded optimization |
| **V** | `PHASE_V_IMPLEMENTATION_READY.md` | 🔧 READY | Natural language interface, feedback schema, intent parser |
| **VI** | *Extracted from Appendix VI* | 📋 SPECIFIED | Interactive assist tools (BandAssist, ColonyAssist) |
| **VII** | `CHALLENGE_PACKS_READY.md` | 🔧 READY | 6 production workflows with QC gates |

### **🎨 UI & Experience Design**
| Document | Purpose | Status | Key Innovation |
|----------|---------|--------|----------------|
| **AutoDense_Minimal_UI_Spec.md** | Original "Drop, See, Ask" philosophy | 📋 REFERENCE | Zero-friction single-screen design |
| **UI_OVERLAY_INTEGRATION.md** | UI philosophy integrated with all phases | 🔧 READY | Auto-run specs, time-series system, command bar integration |

### **📖 Original Appendices (Reference)**
| Appendix | Focus | Integration Status |
|----------|-------|-------------------|
| **Appendix V** | Vision-Assist Mode | ✅ Integrated into Phase IV plan |
| **Appendix VI** | User-Triggered Gemini | ✅ Integrated into Phase V implementation |
| **Appendix VII** | Final Lab Workflows | ✅ Integrated into challenge packs |

---

## 🏗️ System Architecture Reference

### **Current System State (Phases I-III Deployed)**
```
AutoDense Production System
├── Java Pipeline (Deterministic Core)
│   ├── AutotuneAnalysisCLI.java ✅ --no-exit integrated
│   ├── GelAnalysisTools.java ✅ Band/lane independence fixed  
│   ├── ColonyAnalysisTools.java ✅ Lab colorspace, filter chain
│   ├── BandDetector.java ✅ Configurable min_distance_px
│   └── BaselineUtils.java ✅ Separate baseline processing
├── Python Orchestrator
│   ├── java_bridge.py ✅ --no-exit warnings added
│   ├── config_manager.py ✅ Unified schema support
│   └── autotune system ✅ Telemetry integration
├── Configuration System
│   ├── configs/sds.yaml ✅ Band/lane separation
│   ├── configs/etbr.yaml ✅ Optimized parameters  
│   └── configs/colony.yaml ✅ Lab colorspace
└── Telemetry System ✅ COMPLETE
    ├── Flight recorder metrics
    ├── Coverage numerator/denominator
    ├── Filter chain ledger
    └── Parameter usage tracking
```

### **Planned Extensions (Phases IV-VII)**
```
Extended AI Optimization System
├── Phase IV: Vision-Assist
│   ├── autodense_autotune/vision/cropper.py
│   ├── autodense_autotune/vision/advisor.py
│   ├── autodense_autotune/vision/allowlist.py
│   └── autodense_autotune/vision/gating.py
├── Phase V: Natural Language
│   ├── autodense_autotune/nlp/intent_parser.py
│   ├── autodense_autotune/nlp/domain_lexicon.yaml
│   └── Feedback schema (user_feedback.yaml)
├── Phase VI: Interactive Tools
│   ├── BandAssist enhancement
│   └── ColonyAssist (new)
└── Phase VII: Production Workflows
    ├── challenge_packs/protein_quant_v1/
    ├── challenge_packs/lane_compare_v1/
    ├── challenge_packs/semi_qpcr_v1/
    ├── challenge_packs/band_assist_v1/
    ├── challenge_packs/colony_timeseries_v1/
    └── challenge_packs/colony_assist_v1/
```

---

## 🔧 Implementation Status & Results

### **✅ Completed & Validated (Phases I-III)**

#### **Critical Fixes Applied**
1. **Band/Lane Parameter Independence** ✅ FIXED
   - **Issue**: Bands inherited lane baseline parameters (win=15px)
   - **Fix**: Separate configuration processing (win=2px for bands)
   - **Result**: 24 bands detected vs 0-2 previously
   - **Validation**: `[BASELINE_FIX] SUCCESS: Using bands.baseline parameters`

2. **--no-exit Flag Integration** ✅ FIXED
   - **Issue**: Java process termination broke optimization loops
   - **Fix**: Automatic --no-exit in java_bridge.py + prominent warnings
   - **Result**: Optimizer-safe Python-Java communication

3. **Comprehensive Telemetry** ✅ DEPLOYED
   - **Lane metrics**: profile_zero_frac_after, geometry, spacing
   - **Band metrics**: per-lane zero fractions, prominence_used, min_distance_px_used
   - **Colony metrics**: filter chain ledger, Lab-b histograms, classification status
   - **Coverage metrics**: numerator/denominator breakdown

#### **Current Test Results**
| Pipeline | Lanes | Bands | Coverage | Key Improvements |
|----------|-------|-------|----------|------------------|
| **EtBr** | 11 | 2 (was 0) | 46.3% | Relaxed parameters, gentle baseline |
| **SDS-PAGE** | 7 | 24 (was 0-2) | 39.2% | Configurable min_distance_px=14 |
| **Colony** | 15 | N/A | 63% | Lab colorspace, filter chain tracking |

---

## 📋 Implementation Priority Queue

### **Immediate Implementation (Phase IV - 2 days)**
1. **Day 1: Core Vision Infrastructure**
   - `autodense_autotune/vision/cropper.py` - ROI detection, image scrubbing
   - `autodense_autotune/vision/allowlist.py` - parameter bounds validation  
   - `autodense_autotune/vision/gating.py` - failure trigger logic
   - Mock advisor for end-to-end testing

2. **Day 2: Real Vision Integration**
   - `autodense_autotune/vision/advisor.py` - Gemini API integration
   - Physics validation (coverage/stability improvement)
   - Test scenarios: EtBr baseline fix, SDS min_distance optimization

### **Near-term Implementation (Phase V - 3 days)**
1. **Day 1: NL Parser Foundation**
   - Domain lexicon system (`domain_lexicon.yaml`)
   - Intent parser (`intent_parser.py`)
   - Feedback ingestion (`user_feedback.yaml`)

2. **Day 2: Parameter Patch System** 
   - Patch validation against allowlist
   - Bias profile application (recall/precision)
   - Quantifier handling ("slightly", "more", "aggressive")

3. **Day 3: Integration & Testing**
   - Wire to existing orchestrator
   - Report field additions
   - Test suite (20-30 lab phrases)

### **UI Overlay Integration (1-4 weeks parallel)**
**Week 1**: Auto-run basic challenge packs (gel_basic_v1, colony_basic_v1)
**Week 2**: Series builder for colony time-series with manifest system  
**Week 3**: Command bar integration with Phase V NL parser
**Week 4**: Bounded controls, shortcuts, export system

### **Production Workflows (Phase VII - 5 days)**
Ready-to-implement challenge packs with complete specifications:
- **Protein Quantification**: Standard curves, LOQ/LLOQ, %CV
- **Lane Comparison**: MW binning, Holm-Bonferroni correction
- **Semi-qPCR**: ΔΔI normalization, confidence intervals
- **Interactive Tools**: BandAssist, ColonyAssist with user guidance

---

## 🛡️ Safety & Compliance Framework

### **Architectural Principles (All Phases)**
1. **Truth Preservation**: AI advises, pipeline measures (never overridden)
2. **Parameter Bounds**: All suggestions within allowlist ranges  
3. **Physics Validation**: Acceptance requires metric improvement
4. **Audit Trail**: Complete logging of parameters, patches, decisions
5. **Fallback Safety**: System works without AI (telemetry-only mode)

### **Quality Gates (Cross-Phase)**
- **Coverage**: Must improve or maintain
- **Stability**: Must not degrade
- **Physics**: Ladder R², geometry sanity preserved  
- **Bounds**: All parameters within allowlist ranges
- **Reproducibility**: Deterministic re-runs with same config

### **Data Privacy (Phase IV+)**
- **Vision crops**: EXIF stripped, text blurred, downscaled
- **Retention policy**: Only advice JSON + config fingerprints persisted
- **API safety**: No raw images to external services

---

## 🚀 Quick Implementation Commands

### **Current System Testing**
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
./build.sh test-etbr    # Test EtBr with optimized parameters
./build.sh test-sds     # Test SDS with configurable band detection  
./build.sh test-colony  # Test colony with Lab colorspace
```

### **Phase IV Kickoff (Ready to Run)**
```bash
# Create vision module directory
mkdir -p autodense_autotune/vision

# Copy ready specifications
cp NextSteps/INTEGRATED_PHASE_IV_VISION_ASSIST.md autodense_autotune/vision/README.md

# Start implementation
cd autodense_autotune/vision
# Implement cropper.py, advisor.py, allowlist.py, gating.py per specifications
```

### **Phase V NL Interface (Ready to Run)**
```bash
# Create NLP module directory  
mkdir -p autodense_autotune/nlp

# Copy ready implementation
cp NextSteps/PHASE_V_IMPLEMENTATION_READY.md autodense_autotune/nlp/README.md

# Implement domain lexicon and intent parser per specifications
```

### **Phase VII Challenge Packs (Ready to Deploy)**
```bash
# Create challenge packs from specifications
mkdir -p challenge_packs
cp NextSteps/CHALLENGE_PACKS_READY.md challenge_packs/README.md

# Each workflow spec is ready for immediate implementation
```

---

## 📞 Support & Troubleshooting

### **Critical Path Dependencies**
1. **Java 17+**: Required for all phases
2. **Maven build**: Must complete before any testing  
3. **Python venv**: Required for optimization system
4. **--no-exit flag**: CRITICAL for phases IV+ (optimizer loops)

### **Common Issues & Solutions**
| Issue | Solution | Reference |
|-------|----------|-----------|
| ClassNotFoundException | `mvn dependency:build-classpath` | DEFINITIVE_BUILD_REFERENCE.md |
| Band baseline inheritance | Use bands.baseline config | Fixed in Phase III |
| Optimizer loop death | Ensure --no-exit flag | java_bridge.py warnings |
| Config file not found | Use exact paths from reference | DEFINITIVE_BUILD_REFERENCE.md |

### **Validation Commands**  
```bash
# Verify band/lane independence
grep "BASELINE_FIX.*SUCCESS" output/*/run_report.json

# Verify --no-exit integration
grep "no-exit" autodense_autotune/java_bridge.py

# Verify comprehensive telemetry
jq '.observation | keys' output/*/run_report.json
```

---

**Master Principle**: Throughout all phases, **Gemini advises, AutoDense measures, users guide, science decides** — now with **Drop, See, Ask** user experience overlay.

## 🎨 Complete System Vision

The UI overlay integration completes the AutoDense system design:
- **Drop**: Zero-friction image ingestion with immediate auto-analysis
- **See**: Real-time overlays with comprehensive telemetry integration  
- **Ask**: Natural language workflows accessing full challenge pack library

This creates a system that is both **scientifically rigorous** (deterministic pipeline, comprehensive telemetry, bounded optimization) and **delightfully simple** (single screen, natural language, immediate results).

This reference index provides complete navigation for the AutoDense development ecosystem, with clear implementation paths and validation procedures for each phase.