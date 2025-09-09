# Project Structure: September 01, 2025 - Truth Preservation Analysis

> **Doc Meta**
> - **Purpose:** Project structure snapshot following comprehensive detection analysis and remediation planning
> - **Scope:** Complete directory tree and file organization after truth inflation investigation and AI orchestration analysis
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-01

## 📊 Structure Overview

**Status**: Post-comprehensive analysis with linting fixes and packaging optimization applied
**Key Changes**: Enhanced packaging script, comprehensive documentation added, code quality improvements
**Focus**: Detection pipeline analysis and architectural issue identification

## 🗂️ Complete Project Structure

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/
├── build.sh                                    # 🔨 UNIFIED BUILD SCRIPT
├── api-config.properties                       # Gemini API configuration
├── .venv/                                      # Python 3.13 virtual environment
├── .gitignore
├── CLAUDE.md                                   # Project context for AI assistants
├── Architecture.md                             # Technical architecture reference
├── DEFINITIVE_BUILD_REFERENCE.md              # Single source build documentation
├── PROJECT_STRUCTURE_REFERENCE.md             # Comprehensive structure guide
├── BUILD_GUIDE.md                              # Build procedures guide
│
├── autodense/                                  # 🎯 MAIN JAVA/MAVEN PROJECT
│   ├── pom.xml                                 # Root Maven configuration
│   ├── plugin/                                 # Maven plugin module
│   │   ├── pom.xml                             # Plugin-specific Maven config
│   │   ├── src/main/java/                      # Java source code
│   │   │   ├── com/betterdairy/autodense/
│   │   │   │   ├── cli/
│   │   │   │   │   └── AutotuneAnalysisCLI.java    # 🎯 MAIN CLI ENTRY POINT
│   │   │   │   ├── analysis/
│   │   │   │   │   ├── LaneDetector.java           # 🚨 TRUTH INFLATION ISSUE HERE
│   │   │   │   │   ├── ColonyDetector.java         # Colony detection algorithms  
│   │   │   │   │   └── UnifiedColonyDetector.java  # Modern colony detection
│   │   │   │   ├── tools/
│   │   │   │   │   ├── GelAnalysisTools.java       # Gel analysis operations
│   │   │   │   │   ├── AssayOps.java               # Assay operations
│   │   │   │   │   └── PlateAnalysisTools.java     # Plate analysis tools
│   │   │   │   ├── util/
│   │   │   │   │   └── ImagePreprocessor.java      # Image preprocessing utilities
│   │   │   │   └── [other packages]
│   │   │   └── autodense/sds/
│   │   │       └── SdsOps.java                     # 📍 SDS-PAGE detection (linting fixed)
│   │   └── target/                                 # Maven build output
│   │       ├── classes/                            # Compiled Java classes
│   │       ├── autodense-plugin-0.1.0-SNAPSHOT.jar
│   │       ├── runtime-classpath.txt               # CLI execution classpath
│   │       └── classpath.txt                       # Makefile compatibility
│   ├── packaging/                                  # Distribution packaging (311MB excluded from packages)
│   └── samples/                                    # Large sample images (19MB excluded from packages)
│
├── autodense_autotune/                           # 🚀 PYTHON OPTIMIZATION ENGINE  
│   ├── __init__.py
│   ├── cli.py                                    # Python CLI interface
│   ├── java_bridge.py                            # Python-Java bridge (45.9KB)
│   ├── config_manager.py                         # Configuration management
│   ├── helper_critic.py                          # Second-opinion validation
│   ├── parameter_validation.py                   # Parameter validation
│   ├── workflow_validator.py                     # Workflow validation  
│   ├── rescue_tools.py                           # Error recovery tools
│   ├── persistence.py                            # State persistence
│   ├── runner.py                                 # Optimization runner
│   ├── patcher.py                                # Parameter patching
│   └── [other modules]
│
├── challenge_packs/                              # 🎯 TASK-SPECIFIC OPTIMIZATION
│   ├── sds_page_v1/
│   │   └── spec.yaml                             # SDS optimization configuration
│   ├── colony_count_v1/
│   │   └── spec.yaml                             # Colony optimization configuration
│   └── etbr_v1/
│       └── spec.yaml                             # EtBr optimization configuration
│
├── configs/                                      # 🔧 YAML CONFIGURATIONS
│   ├── sds.yaml                                  # 🚨 SCHEMA CONFLICTS IDENTIFIED
│   ├── colony.yaml                               # Colony counting config
│   ├── etbr.yaml                                 # EtBr gel analysis config
│   └── sds_sensitive.yaml                        # 12-lane optimized config
│
├── samples/                                      # 🖼️ TEST IMAGES (Essential samples kept)
│   ├── sds_gel.jpg                               # Primary SDS test (1.6MB, 12 lanes)
│   ├── colony_plate.jpg                          # Colony test (3.6MB)
│   ├── etbr_gel.jpg                              # EtBr test (372KB) 
│   ├── synthetic_sds_gel2.png                    # Generated SDS test
│   ├── synthetic_colony_plate2.png               # Generated colony test
│   ├── synthetic_etbr_gel.jpg                    # Generated EtBr test
│   └── synthetic_etbr_gel2.png                   # Generated EtBr test 2
│
├── output/                                       # 📊 ANALYSIS RESULTS (49MB excluded from packages)
│   ├── test_sds/                                 # SDS test outputs
│   │   ├── run_report.json                       # 🔍 TRUTH INFLATION EVIDENCE HERE
│   │   ├── overlay.png                           # Visual detection results (9MB)
│   │   ├── stage0_input.png                      # Preprocessing stage (11MB)
│   │   └── stage1_norm.png                       # Normalization stage (6MB)
│   ├── test_etbr/                                # EtBr test outputs  
│   └── test_colony/                              # Colony test outputs
│
├── scripts/                                      # 🛠️ UTILITY SCRIPTS
│   ├── package_codebase.py                       # 📦 ENHANCED THIS SESSION (847 words)
│   └── [other scripts]
│
├── prompts/                                      # 🤖 GEMINI SYSTEM PROMPTS
│   ├── orchestrator.system.md                   # Main optimization prompt (3.8KB)
│   ├── helper.system.md                          # Critic validation prompt (1.8KB)
│   └── README.md                                 # Prompt documentation
│
├── docs/                                         # 📚 PROJECT DOCUMENTATION
│   ├── DOCUMENT_CATALOG.md                       # 📋 UPDATED THIS SESSION (86 files)
│   ├── CLAUDEKIT_REFERENCE.md                    # ClaudeKit tools reference
│   ├── API_REFERENCE.md                          # Technical API documentation
│   ├── WorkflowPresets.md                        # Workflow documentation
│   ├── CANONICAL_TOOLS_SUMMARY.md                # Core tool definitions
│   └── [extensive documentation library]
│
├── SessionSummaries/                             # 📝 SESSION DOCUMENTATION
│   ├── Session_summary_August_30_2025_Configuration_Pipeline_Fix.md
│   ├── Session_summary_September_01_2025_Truth_Preservation_Analysis.md  # 📍 NEW THIS SESSION
│   └── [previous session summaries]
│
├── NextSteps/                                    # 📋 TASK PLANNING
│   ├── NextSteps_August_30_2025_Configuration_Pipeline_Fix.md
│   ├── NextSteps_September_01_2025_Truth_Preservation_AI_Integration.md  # 📍 NEW THIS SESSION (1638 words)
│   └── [previous next steps]
│
├── Issues/                                       # 🚨 BUG TRACKING
│   ├── Issues_August_30_2025_Configuration_Pipeline_Fix.md
│   ├── Issues_September_01_2025_Truth_Preservation_Analysis.md           # 📍 NEW THIS SESSION
│   └── [previous issues]
│
├── StructureDocs/                                # 📐 STRUCTURE SNAPSHOTS
│   ├── Structure_August_30_2025_Configuration_Pipeline_Fix.md
│   ├── Structure_September_01_2025_Truth_Preservation_Analysis.md        # 📍 THIS DOCUMENT
│   ├── Absolute_Paths.md                         # Memorized path reference
│   └── [previous structure docs]
│
├── tests/                                        # 🧪 PYTHON TEST SUITE
│   ├── test_helper_gate.py                       # Helper validation tests
│   ├── test_metrics_colony.py                    # Colony metrics tests
│   └── test_schemas.py                           # Schema validation tests
│
└── audits/                                       # 🔍 EXTERNAL AUDITS
    └── External Audit -08312025.md               # External audit findings
```

## 🔍 Key Structural Changes Since Last Session

### **New Documentation Added**
- **NextSteps/NextSteps_September_01_2025_Truth_Preservation_AI_Integration.md** - 4-phase remediation plan
- **SessionSummaries/Session_summary_September_01_2025_Truth_Preservation_Analysis.md** - Comprehensive analysis summary
- **Issues/Issues_September_01_2025_Truth_Preservation_Analysis.md** - Critical issues identified

### **Enhanced Files**  
- **scripts/package_codebase.py** - Optimized exclusions, 90% size reduction capability
- **docs/DOCUMENT_CATALOG.md** - Updated with new documents, now 86 total files

### **Analysis Focus Areas Identified**
- **autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java:270-290** - Truth inflation logic location
- **configs/*.yaml** - Configuration schema conflicts requiring unification
- **output/test_*/run_report.json** - Evidence of detection issues and AI bypass

## 📊 Size Analysis & Packaging Optimization

### **Large Directories (Excluded from Packages)**
- **autodense/packaging/** (322MB) - AutoDense.app + Fiji.app distributions
- **output/** (49MB) - Generated test images and analysis results  
- **autodense/samples/** (19MB) - Large original gel photos

### **Essential Directories (Included in Packages)**
- **autodense/plugin/src/** - Core Java source code for detection algorithms
- **autodense_autotune/** - Python optimization engine  
- **configs/** - YAML configuration files (with identified schema conflicts)
- **samples/** - Essential test images (smaller, synthetic samples)
- **docs/** - Complete documentation library

## 🎯 Critical File Locations for Remediation

### **Phase 1 Targets (Truth Preservation)**
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java:270-290`** - Biased adjustment logic
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java`** - Result JSON construction

### **Phase 2 Targets (Configuration Unification)**
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml`** - Schema conflicts (detect vs detection sections)
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/etbr.yaml`** - Parameter routing issues
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/colony.yaml`** - Different analysis type parameters

### **Phase 3 Targets (AI Integration)**
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense_autotune/`** - Python orchestration engine
- **`/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/prompts/orchestrator.system.md`** - Gemini optimization prompts

## 🛠️ Build System Status

### **Unified Build Infrastructure**
- **✅ build.sh** - Single build script handles all environment setup
- **✅ Maven Integration** - Java builds with proper classpath generation  
- **✅ Python Environment** - Automated .venv activation and package management
- **✅ Testing Commands** - `./build.sh test-sds|test-etbr|test-colony|test-detect`

### **Current Build State**
- **Java Compilation**: ✅ Working (improved with linting fixes)
- **Python Environment**: ✅ Working (3.13.4 in .venv)
- **AI Integration**: ❌ Bypassed (Java CLI instead of Python orchestrator)
- **Configuration Loading**: ⚠️ Working but conflicted (multiple schema sections)

## 📈 Project Health Metrics

### **Documentation Coverage**
- **Total Documents**: 86 files across 9 categories
- **Recent Updates**: 3 new documents this session
- **Documentation Quality**: All new documents have proper Doc Meta blocks

### **Code Quality Status**  
- **Java Checkstyle**: ✅ 100% Pass (fixed this session)
- **Java SpotBugs**: ✅ Clean (no critical bugs)
- **Python Linting**: ✅ Clean (fixed this session)
- **Build Stability**: ✅ All builds successful

### **Detection Pipeline Status**
- **SDS-PAGE**: 7 peaks detected (raw) → needs truth preservation fix
- **EtBr**: 11 peaks detected (raw) → parameter optimization needed  
- **Colony**: 0 colonies detected → pipeline failure requiring investigation
- **Band Detection**: 0 bands across all types → algorithmic issue requiring separate parameters

This structure snapshot provides a complete view of the AutoDense project following the comprehensive detection analysis, with clear identification of critical files requiring modification and the overall health of the build and documentation systems.