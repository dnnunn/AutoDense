# Project Structure: August 29, 2025 - Evening

> **Doc Meta**
> - **Purpose:** Current project structure snapshot following detection pipeline breakthrough session
> - **Scope:** Complete directory tree with key file locations and organization
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-29

## Project Overview

**Total Structure**: 54 directories, 196 files
**Root Path**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`

## Complete Directory Tree

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
├── api-config.properties
├── Architecture.md
├── archives
├── AUDIT_SCRIPTS_README.md
├── audit.txt
├── audits
│   ├── test_optimization
│   │   ├── optimization_validation_report.md
│   │   ├── optimization_validation_results.json
│   │   ├── optimization_validation.py
│   │   └── test1
│   └── test_run
│       ├── autotune_run_1756392425
│       ├── autotune_run_1756392483
│       └── optimization
├── autodense
│   ├── build
│   ├── nl
│   │   └── target
│   ├── packaging
│   │   ├── resources
│   │   └── scripts
│   ├── plugin
│   │   ├── checkstyle.xml
│   │   ├── META-INF
│   │   ├── pom.xml
│   │   ├── src
│   │   ├── target
│   │   ├── tmp
│   │   └── TODO.md
│   ├── pom.xml
│   ├── README.md
│   ├── samples
│   │   ├── Gel1.jpg
│   │   ├── Gel2.jpg
│   │   ├── IMG_2937.jpg
│   │   ├── IMG_2938.jpg
│   │   ├── IMG_2939.jpg
│   │   ├── IMG_2940.jpg
│   │   ├── IMG_9715-2.jpg
│   │   ├── IMG_9715.jpg
│   │   ├── IMG_9716-2.jpg
│   │   └── IMG_9716.jpg
│   ├── screenshots
│   │   └── Screenshot 2025-08-22 at 12.29.37.png
│   └── TestHandlePersistence.java
├── autodense_autotune
│   ├── __init__.py
│   ├── __pycache__
│   │   ├── __init__.cpython-313.pyc
│   │   ├── cli.cpython-313.pyc
│   │   ├── config_manager.cpython-313.pyc
│   │   ├── helper.cpython-313.pyc
│   │   ├── java_bridge.cpython-313.pyc
│   │   ├── patcher.cpython-313.pyc
│   │   ├── persistence.cpython-313.pyc
│   │   ├── runner.cpython-313.pyc
│   │   ├── runners.cpython-313.pyc
│   │   └── schemas.cpython-313.pyc
│   ├── adapters.py
│   ├── cli.py
│   ├── config_manager.py
│   ├── helper.py
│   ├── java_bridge.py
│   ├── patcher.py
│   ├── persistence.py
│   ├── runner.py
│   ├── runners.py
│   └── schemas.py
├── autodense_codebase_2025-08-29_150326.tar.gz
├── autodense_debug_rescue_toolkit_2025-08-28.tar.gz
├── autodense_preflight_priors_2025-08-28.tar.gz
├── challenge_packs
│   ├── colony_count_v1
│   │   ├── __pycache__
│   │   ├── metrics.py
│   │   └── spec.yaml
│   ├── etbr_v1
│   │   ├── __pycache__
│   │   ├── metrics.py
│   │   └── spec.yaml
│   └── sds_page_v1
│       ├── __pycache__
│       ├── metrics.py
│       └── spec.yaml
├── ChatGPT
│   ├── Cleanup.md
│   └── What to fix (surgical and minimal).md
├── CLAUDE.md
├── COMPILE_PATHS.md
├── configs
│   ├── colony_backup.yaml
│   ├── colony.yaml
│   ├── colony.yaml.bak
│   ├── etbr_backup.yaml
│   ├── etbr.yaml
│   └── sds.yaml
├── cspell.json
├── docs
│   ├── API_REFERENCE.md
│   ├── ARCHITECTURAL_DEBT.md
│   ├── BandAssist.md
│   ├── Bundle and Ship.md
│   ├── CANONICAL_TOOLS_SUMMARY.md
│   ├── CHANGELOG.md
│   ├── CLAUDEKIT_REFERENCE.md
│   ├── COLONY_CLASSIFIER_IMPLEMENTATION.md
│   ├── COLONY_EXPORT_SYSTEM.md
│   ├── COLONY_IDENTIFICATION_ASSIST.md
│   ├── COLONY_NORMALIZATION.md
│   ├── COLONY_WORKFLOW_COLOR_FIX.md
│   ├── COMBINED_AUDIT_ACTION_PLAN.md
│   ├── CONTRIBUTING.md
│   ├── CORE_DETECTOR_IMPLEMENTATION.md
│   ├── DEBUGGING_CHECKLIST.md
│   ├── DEMO_SYSTEM.md
│   ├── DEPENDENCY_STRATEGY.md
│   ├── DEPRECATED_TEMPLATE.md
│   ├── DOCUMENT_CATALOG.md
│   ├── DOCUMENTATION.md
│   ├── EXPORT_SYSTEM.md
│   ├── FIJI_ANALYSIS_INTEGRATION.md
│   ├── FRONTEND_ENHANCEMENTS.md
│   ├── FUNCTIONAL_ARCHITECTURE_INTEGRATION.md
│   ├── HANDLE_PERSISTENCE_STRATEGY.md
│   ├── IMPLEMENTATION_PLAN.md
│   ├── IMPLEMENTATION.md
│   ├── Instructions.md
│   ├── INTERNAL_AUDIT_COLONY_COUNTING.md
│   ├── MIGRATION_NOTES.md
│   ├── MIGRATION.md
│   ├── More Fiji imports.md
│   ├── New plan.md
│   ├── NEW_DOC_TEMPLATE.md
│   ├── PERFORMANCE_OPTIMIZATIONS.md
│   ├── perspective_robustness_analysis.md
│   ├── PLATE_ANALYSIS_DEFAULTS.md
│   ├── Preflight.md
│   ├── PROJECT_STATUS.md
│   ├── ProteinQuantificationWorkflowIdea.md
│   ├── README_AUTOTUNE.md
│   ├── README.md
│   ├── RELEASE_NOTES.md
│   ├── robust_plate_registration_summary.md
│   ├── STATUSLOG.md
│   ├── STREAMLINED_COLONY_ANALYSIS_SYSTEM.md
│   ├── SuccessLog.md
│   ├── test_sharpness_smear.md
│   ├── TOOL_VALIDATION_SYSTEM.md
│   ├── VISUAL_MARKUP_STRATEGY.md
│   ├── WorkflowPresets.md
│   └── xgal_improvements_summary.md
├── docs_inventory.csv
├── ImageHarverster
│   └── image_harvester.py
├── Issues
│   ├── Issues_August_27_2025_Evening.md
│   ├── Issues_August_27_2025.md
│   ├── Issues_August_28_2025_CLI_Architecture.md
│   ├── Issues_August_28_2025_CLI.md
│   ├── Issues_August_28_2025.md
│   └── Issues_August_29_2025_Detection_Pipeline_Fix.md      [NEW TODAY]
├── Makefile
├── Makefile 2
├── Makefile 3
├── markdownlint.config.json
├── NextSteps
│   ├── ERROR_HANDLING_REFACTORING_GUIDE.md
│   ├── INPUT_VALIDATION_SECURITY_GUIDE.md
│   ├── NextSteps_August_27_2025_Evening.md
│   ├── NextSteps_August_27_2025.md
│   ├── NextSteps_August_28_2025_CLI_Architecture.md
│   ├── NextSteps_August_28_2025_CLI.md
│   ├── NextSteps_August_28_2025.md
│   └── NextSteps_August_29_2025_Detection_Pipeline_Fix.md   [NEW TODAY]
├── output
│   ├── colony_test
│   ├── detect_only_test
│   │   ├── lane_profile.csv
│   │   └── lane_profile.png
│   ├── detect_only_test2
│   │   ├── lane_profile.csv
│   │   └── lane_profile.png
│   ├── direct_test
│   ├── direct_test2
│   ├── etbr_test
│   └── sds_test
│       ├── overlay.png
│       ├── run_report.json
│       ├── stage0_input.png
│       └── stage1_norm.png
├── prompts
│   ├── helper.system.md
│   ├── orchestrator.system.md
│   └── README.md
├── pytest.ini
├── samples
│   ├── colony_plate.jpg
│   ├── etbr_gel.jpg
│   ├── sds_gel.jpg
│   ├── synthetic_colony_plate.jpg
│   ├── synthetic_colony_plate2.png
│   ├── synthetic_etbr_gel.jpg
│   ├── synthetic_etbr_gel2.png
│   ├── synthetic_sds_gel.jpg
│   └── synthetic_sds_gel2.png
├── scripts
│   ├── build_classpath.sh
│   ├── check_config_drift.py
│   ├── deprecate_doc.sh
│   ├── discover_imagej.py
│   ├── docs_inventory.py
│   ├── docs_links.py
│   ├── docs_meta_check.py
│   ├── docs_similar.py
│   ├── docs_tombstone_check.py
│   ├── env_check.py
│   ├── golden_sds.ijm
│   ├── golden_xgal.ijm
│   ├── install-githooks.sh
│   ├── md_check.sh
│   ├── package_codebase.py
│   ├── run_preset_agarose.json
│   ├── run_preset_sds_autodetect.json
│   ├── run_preset_sds.json
│   ├── scan_hardcoded_params.py
│   ├── test_env.sh
│   └── verify_golden.py
├── server
│   └── registry.json
├── SessionSummaries
│   ├── Session_summary_August_20_2025.md
│   ├── Session_summary_August_25_2025.md
│   ├── Session_summary_August_27_2025.md
│   ├── Session_summary_August_28_2025_CLI_Architecture.md
│   ├── Session_summary_August_28_2025_CLI_Integration.md
│   ├── Session_summary_August_28_2025.md
│   └── Session_summary_August_29_2025_Detection_Pipeline_Fix.md   [NEW TODAY]
├── StandardsTest.class
├── StructureDocs
│   ├── Structure_August_28_2025_Evening.md
│   ├── Structure_August_28_2025.md
│   └── Structure_August_29_2025_Evening.md                        [NEW TODAY]
├── systematic_preprocessing_audit.sh
├── test_canonical_helpers.class
├── test_canonical_helpers.java
├── tests
│   ├── __init__.py
│   ├── __pycache__
│   │   ├── __init__.cpython-313-pytest-8.4.1.pyc
│   │   ├── test_helper_gate.cpython-313-pytest-8.4.1.pyc
│   │   ├── test_metrics_colony.cpython-313-pytest-8.4.1.pyc
│   │   └── test_schemas.cpython-313-pytest-8.4.1.pyc
│   ├── test_helper_gate.py
│   ├── test_metrics_colony.py
│   └── test_schemas.py
└── wire_smart_wrapper.py
```

## Key Directory Descriptions

### Core System Architecture
- **`autodense/`** - Main Java application with Maven modules
  - **`plugin/`** - Core AutoDense plugin with ImageJ/Fiji integration
  - **`plugin/src/`** - Java source code for analysis tools and CLI
  - **`plugin/target/`** - Compiled classes and generated classpath files

### AI Optimization System  
- **`autodense_autotune/`** - Python optimization engine for AI parameter tuning
- **`challenge_packs/`** - Task-specific optimization configurations
  - **`sds_page_v1/`**, **`colony_count_v1/`**, **`etbr_v1/`** - Analysis type specifications

### Configuration & Data
- **`configs/`** - YAML configuration files for different analysis types
- **`samples/`** - Test images for validation (9 files: 3 SDS, 3 EtBr, 3 colony)
- **`output/`** - Analysis results and preprocessed images
  - **`sds_test/`** - Contains `stage1_norm.png` used in breakthrough testing

### Documentation System
- **`docs/`** - Comprehensive technical documentation (40+ files)
- **`SessionSummaries/`** - Session-by-session development history
- **`NextSteps/`** - Priority task planning for upcoming sessions  
- **`Issues/`** - Bug tracking and issue documentation
- **`StructureDocs/`** - Project structure snapshots over time

### Development Infrastructure
- **`scripts/`** - Build, validation, and utility scripts
- **`prompts/`** - Gemini orchestrator and helper system prompts
- **`tests/`** - Python test suite for optimization system

## Notable Changes Since Last Structure

### New Files Added Today
1. **`Issues/Issues_August_29_2025_Detection_Pipeline_Fix.md`** - Issue tracking from breakthrough session
2. **`NextSteps/NextSteps_August_29_2025_Detection_Pipeline_Fix.md`** - Implementation roadmap
3. **`SessionSummaries/Session_summary_August_29_2025_Detection_Pipeline_Fix.md`** - Session documentation  
4. **`StructureDocs/Structure_August_29_2025_Evening.md`** - This structure snapshot

### Key Modified Files
1. **`autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java`**
   - Added `min()` helper function (lines 1493-1497)
   - Fixed debug statement placement and commented out aggressive baseline removal
   - Implemented working detect-only mode that detects 15 lanes vs 0 previously

### Build Artifacts
- **`autodense/plugin/target/runtime-classpath.txt`** - Generated classpath with 243 JAR dependencies
- **`output/sds_test/stage1_norm.png`** - Preprocessed image used for breakthrough testing

## File Count Summary

- **Java source files**: Located in `autodense/plugin/src/`
- **Python optimization**: 10 files in `autodense_autotune/`
- **Configuration files**: 6 YAML files in `configs/`
- **Documentation**: 72+ markdown files across multiple directories
- **Test images**: 9 sample files, 10 additional files in `autodense/samples/`
- **Build outputs**: Compiled classes, JARs, and generated files in `target/` directories

## Critical File Locations for Next Session

### Immediate Attention Required
- **`autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`** - Apply baseline removal fix
- **`configs/sds.yaml`**, **`configs/colony.yaml`**, **`configs/etbr.yaml`** - Update parameters post-fix

### Testing Infrastructure  
- **`samples/`** directory - All 9 test images for validation
- **`scripts/build_classpath.sh`** - Classpath generation for Java execution
- **`output/`** directories - Analysis results and debugging output

### AI Optimization Integration
- **`challenge_packs/*/spec.yaml`** - Update with working baseline parameters
- **`autodense_autotune/`** - Python optimization engine integration points

## CRITICAL: Essential Working Procedures (MEMORIZE THIS)

**🚨 READ THIS FIRST - These are the exact procedures to prevent wasting time every session:**

### Python Environment Setup
```bash
# ALWAYS activate the venv first - packages exist here, not globally
source .venv/bin/activate

# Test that environment is working:
python -c "import yaml; print('yaml version:', yaml.__version__)"  # Should show: 6.0.2
```

### Java Compilation (Maven)
```bash
# MUST run from the plugin directory, NOT root:
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin
mvn compile -q
mvn package -DskipTests -q  # If you need the JAR
```

### Java Execution (CLI Testing)
```bash
# MUST run from root directory with correct classpath:
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
java -cp "target/autodense-plugin-0.1.0-SNAPSHOT.jar:$(cat target/runtime-classpath.txt)" \
  com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
  --detect-only \
  --preprocessed samples/sds_gel.jpg \
  --roi 50,100,800,400 \
  --config-yaml configs/sds.yaml \
  --outdir output/detect_config_test
```

### Directory Structure (ABSOLUTE PATHS)
- **Root**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`
- **Python venv**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/`
- **Maven plugin**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
- **Config files**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/`
- **Sample images**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/`
- **Java target**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/target/`

### Environment Test Commands
```bash
# Test Python environment (should work):
source .venv/bin/activate && python -c "import autodense_autotune.config_manager as cm; print('Python OK')"

# Test Java compilation (should work):
cd autodense/plugin && mvn compile -q

# Test environment setup script:
source scripts/test_env.sh
```

**❌ NEVER DO THESE (Common Mistakes):**
- Run `mvn` from root directory (wrong location)
- Assume packages are globally installed (use .venv)
- Run Java from plugin directory (wrong location)
- Try to install packages globally (they exist in .venv)

This structure represents a mature development project with comprehensive documentation, systematic organization, and established development workflows, now positioned for AI optimization integration following the detection pipeline breakthrough.