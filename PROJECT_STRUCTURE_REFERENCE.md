# AutoDense Project Structure Reference

> **Doc Meta**
> - **Purpose:** Comprehensive reference for all build, compile, test, and configuration operations - single source of truth for paths, environments, and procedures
> - **Scope:** Complete project structure, environment setup, build system, testing infrastructure, and troubleshooting guide
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-31

**CRITICAL:** Consult this document BEFORE any build/compile/test operation to prevent path errors and environment issues.

---

## 🎯 Quick Reference Commands

```bash
# Build everything (from project root)
./build.sh build

# Test specific pipelines
./build.sh test-sds       # SDS-PAGE analysis
./build.sh test-etbr      # EtBr gel analysis  
./build.sh test-colony    # Colony counting
./build.sh test-detect    # Detect-only (known working)
./build.sh test-all       # All tests
```

---

## 📁 Complete Directory Structure

**Project Root:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/
├── build.sh                           # 🔧 UNIFIED BUILD SCRIPT (PRIMARY TOOL)
├── api-config.properties              # Gemini API key configuration
├── .venv/                             # Python 3.13 virtual environment
│   ├── bin/activate                   # Auto-activated by build.sh
│   ├── pyvenv.cfg                     # Python 3.13.4 config
│   └── lib/python3.13/site-packages/  # numpy, yaml, scipy, etc.
│
├── autodense/                         # Java Maven project
│   └── plugin/                        # 🎯 MAVEN WORKING DIRECTORY
│       ├── pom.xml                    # Maven configuration (Java 17)
│       ├── src/main/java/             # Java source code
│       │   ├── com/betterdairy/autodense/cli/
│       │   │   └── AutotuneAnalysisCLI.java  # Main CLI entry point
│       │   ├── com/betterdairy/autodense/util/
│       │   │   └── ImagePreprocessor.java    # Known HeadlessException at line 197
│       │   └── [other packages]/
│       └── target/                    # 🎯 COMPILATION OUTPUT
│           ├── classes/               # Compiled Java .class files
│           ├── autodense-plugin-0.1.0-SNAPSHOT.jar  # 📦 PRIMARY JAR
│           ├── runtime-classpath.txt  # 🔧 CLI execution classpath
│           └── classpath.txt          # 🔧 Makefile compatibility classpath
│
├── autodense_autotune/               # 🚀 PYTHON OPTIMIZATION ENGINE
│   ├── __init__.py                   # Python package init
│   ├── cli.py                        # Python CLI interface (6.3KB)
│   ├── config_manager.py             # Configuration management (8.1KB)
│   ├── java_bridge.py                # Python-Java bridge (45.9KB)
│   ├── helper_critic.py              # Second-opinion validation (14.8KB)
│   ├── parameter_validation.py       # Parameter validation (13.5KB)
│   ├── workflow_validator.py         # Workflow validation (16.0KB)
│   ├── rescue_tools.py               # Error recovery tools (11.0KB)
│   ├── persistence.py                # State persistence (17.4KB)
│   ├── runner.py                     # Optimization runner (8.1KB)
│   ├── patcher.py                    # Parameter patching (4.5KB)
│   └── [other modules]
│
├── challenge_packs/                  # 🎯 TASK-SPECIFIC OPTIMIZATION
│   ├── sds_page_v1/
│   │   └── spec.yaml                 # SDS optimization config
│   ├── colony_count_v1/
│   │   └── spec.yaml                 # Colony optimization config
│   └── etbr_v1/
│       └── spec.yaml                 # EtBr optimization config
│
├── configs/                          # 🔧 YAML CONFIGURATION FILES
│   ├── sds.yaml                      # SDS-PAGE analysis config (1.9KB)
│   ├── colony.yaml                   # Colony counting config (2.0KB)
│   ├── etbr.yaml                     # EtBr gel analysis config (2.5KB)
│   └── [backup files]                # .bak, _backup.yaml files
│
├── samples/                          # 🖼️ TEST IMAGES (12.3MB total)
│   ├── sds_gel.jpg                   # Primary SDS test (1.6MB, 12 lanes)
│   ├── colony_plate.jpg              # Colony test (3.6MB)
│   ├── etbr_gel.jpg                  # EtBr test (372KB)
│   ├── synthetic_sds_gel.jpg         # Generated SDS (15.5KB)
│   ├── synthetic_sds_gel2.png        # Generated SDS v2 (20.3KB)
│   ├── synthetic_colony_plate.jpg    # Generated colony (28.7KB)
│   ├── synthetic_colony_plate2.png   # Generated colony v2 (26.9KB)
│   ├── synthetic_etbr_gel.jpg        # Generated EtBr (25.8KB)
│   └── synthetic_etbr_gel2.png       # Generated EtBr v2 (758KB)
│
├── output/                           # 📊 ANALYSIS RESULTS
│   ├── test_sds_validation/          # SDS test outputs
│   ├── test_etbr_validation/         # EtBr test outputs
│   ├── test_colony_validation/       # Colony test outputs
│   └── test_colony_validation2/      # Additional colony outputs
│
├── tests/                            # 🧪 PYTHON TEST SUITE
│   ├── __init__.py                   # Test package init
│   ├── test_helper_gate.py           # Helper validation tests
│   ├── test_metrics_colony.py        # Colony metrics tests
│   └── test_schemas.py               # Schema validation tests
│
├── prompts/                          # 🤖 GEMINI SYSTEM PROMPTS
│   ├── orchestrator.system.md        # Main optimization prompt (3.8KB)
│   ├── helper.system.md              # Critic validation prompt (1.8KB)
│   └── README.md                     # Prompt documentation
│
├── SessionSummaries/                 # 📝 SESSION DOCUMENTATION
├── NextSteps/                        # 📋 TASK PLANNING
├── Issues/                           # 🚨 BUG TRACKING
├── StructureDocs/                    # 📐 STRUCTURE SNAPSHOTS
├── ImageHarverster/                  # 📸 IMAGE GENERATION TOOLS
└── audits/                           # 🔍 VALIDATION AUDITS
```

---

## 🛠️ Environment Setup

### Python Environment
- **Version:** Python 3.13.4 (via Homebrew)
- **Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/`
- **Executable:** `/opt/homebrew/Cellar/python@3.13/3.13.4/Frameworks/Python.framework/Versions/3.13/bin/python3.13`
- **Activation:** Automatic via `build.sh` (sources `.venv/bin/activate`)
- **Packages:** numpy, scipy, scikit-image, matplotlib, yaml, etc.

### Java Environment
- **Version:** Java 17 (Maven compiler source/target)
- **Maven Directory:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
- **Main JAR:** `autodense-plugin-0.1.0-SNAPSHOT.jar`
- **ImageJ Version:** 2.14.0
- **Critical:** Always use `-Djava.awt.headless=true` for CLI operations

### Environment Variables
- **GEMINI_API_KEY:** Required for Gemini integration (starts with "AIza...")
- **Alternative:** Set in `api-config.properties` file

---

## 🔨 Build System

### Primary Build Tool: `./build.sh`
**Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/build.sh`

**Key Features:**
- Auto-detects project root via `$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)`
- Activates Python venv automatically
- Builds Java components in correct Maven directory
- Generates both classpath files simultaneously
- Provides unified test commands

**Build Process:**
1. Activates Python venv at `$PROJECT_ROOT/.venv/`
2. Changes to Maven directory: `$PROJECT_ROOT/autodense/plugin/`
3. Runs Maven clean compile
4. Generates classpath files:
   - `target/runtime-classpath.txt` (for CLI)
   - `target/classpath.txt` (for Makefile compatibility)
5. Creates JAR if missing: `target/autodense-plugin-0.1.0-SNAPSHOT.jar`

### Maven Configuration
**Working Directory:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
**POM Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/pom.xml`

**Key Dependencies:**
- ImageJ 2.14.0 (net.imagej:imagej, net.imagej:ij)
- SciJava Common (org.scijava:scijava-common)
- JSON processing (org.json:json, com.fasterxml.jackson)
- YAML parsing (org.yaml:snakeyaml)
- TwelveMonkeys ImageIO (enhanced format support)
- Apache POI (Excel export)
- JUnit 5 (testing)

**Critical Build Features:**
- Shade plugin relocates dependencies to avoid Fiji conflicts
- Preserves META-INF/services for ImageIO ServiceLoader
- Main class: `com.betterdairy.autodense.plugin.EnhancedImageJLauncher`
- Checkstyle and SpotBugs static analysis enabled
- PMD disabled (complexity violations identified)

### Generated Files (Post-Build)
```
$PROJECT_ROOT/autodense/plugin/target/
├── classes/                          # Compiled Java classes
├── autodense-plugin-0.1.0-SNAPSHOT.jar  # Primary JAR (shaded)
├── runtime-classpath.txt             # CLI execution classpath
├── classpath.txt                     # Makefile compatibility
└── [Maven standard directories]
```

---

## 🧪 Testing Infrastructure

### Test Commands (via build.sh)
```bash
# From project root:
./build.sh test-sds      # Test SDS-PAGE pipeline
./build.sh test-etbr     # Test EtBr gel pipeline
./build.sh test-colony   # Test colony counting pipeline
./build.sh test-detect   # Test detect-only mode (KNOWN WORKING)
./build.sh test-all      # Run all pipeline tests
```

### Test Data Locations
**Sample Images:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/`
- `sds_gel.jpg` (1.6MB) - Primary SDS test image with 12 lanes
- `colony_plate.jpg` (3.6MB) - Colony counting test image
- `etbr_gel.jpg` (372KB) - EtBr gel analysis test image
- `synthetic_*.jpg/png` - Generated test images for reproducible testing

**Test Results:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/output/`
- `test_sds_validation/` - SDS pipeline outputs
- `test_etbr_validation/` - EtBr pipeline outputs
- `test_colony_validation/` - Colony pipeline outputs

**Python Tests:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/tests/`
- `test_helper_gate.py` - Helper validation tests
- `test_metrics_colony.py` - Colony metrics tests  
- `test_schemas.py` - Schema validation tests

### CLI Test Command Template
```bash
java -Djava.awt.headless=true \
     -cp "$PLUGIN_DIR/target/classes:$(cat $PLUGIN_DIR/target/runtime-classpath.txt)" \
     com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
     [PIPELINE_TYPE] [INPUT_IMAGE] [CONFIG_FILE] [OUTPUT_DIR]
```

**Pipeline Types:**
- `sds_page` - SDS-PAGE densitometry
- `etbr_agarose` - EtBr gel analysis
- `colony_count` - Colony counting
- `--detect-only` - Detection without full analysis

---

## ⚙️ Configuration Files

### Primary Configurations
**Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/`

- **`sds.yaml`** (1.9KB) - SDS-PAGE analysis parameters
- **`colony.yaml`** (2.0KB) - Colony counting parameters
- **`etbr.yaml`** (2.5KB) - EtBr gel analysis parameters

**Backup Files:** `*_backup.yaml`, `*.yaml.bak` (for recovery)

### Challenge Pack Configurations
**Location:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/challenge_packs/`

- **`sds_page_v1/spec.yaml`** - SDS optimization parameters
- **`colony_count_v1/spec.yaml`** - Colony optimization parameters
- **`etbr_v1/spec.yaml`** - EtBr optimization parameters

**Key Challenge Pack Features:**
- QC thresholds (ladder_r2_min: 0.995, band_stability_jitter_min: 0.70)
- Parameter grids for safe exploration
- Budget limits (max_attempts: 4)
- Acceptance criteria for optimization

### API Configuration
**File:** `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/api-config.properties`
**Alternative:** Set `GEMINI_API_KEY` environment variable
**Format:** API key must start with "AIza..."

---

## 🔄 Dual Architecture Overview

### Legacy Mode (Backwards Compatible)
- **GeminiOrchestrator:** Builds JSON tool plans from user commands
- **ImageJ Tools:** Execute image operations deterministically
- **SessionStore:** Persists state via handle system (img_123, ov_456)
- **SessionLogger:** Logs conversations and API interactions

### Optimization Mode (Primary - August 2025)
- **AutotuneRunner:** Python optimization loop with metric feedback
- **GeminiOrchestrator:** Acts as intelligent parameter optimizer
- **HelperCritic:** Second-opinion validation system
- **RunReports:** Performance metrics fed back to optimization
- **PatchProposals:** Machine-readable parameter changes
- **ChallengeSpecs:** Task-specific optimization configurations

---

## 🚀 Startup Procedures

### Standard Build Sequence
```bash
# 1. Navigate to project root
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense

# 2. Build everything (auto-activates Python venv)
./build.sh build

# 3. Verify build success
ls -la autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar
ls -la autodense/plugin/target/runtime-classpath.txt
```

### Dependency Verification
```bash
# Check Python environment
source .venv/bin/activate && python --version  # Should show 3.13.4

# Check Java environment  
java --version   # Should show Java 17

# Check Maven
cd autodense/plugin && mvn --version
```

### Quick Test Verification
```bash
# Test known working mode first
./build.sh test-detect

# If successful, test specific pipeline
./build.sh test-sds
```

---

## 🔧 Common Operations

### Building Components
```bash
# Complete build (recommended)
./build.sh build

# Manual Java build (if needed)
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin
mvn clean compile
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
mvn package -DskipTests
```

### Running Analysis Pipelines
```bash
# Using build.sh (recommended)
./build.sh test-sds
./build.sh test-etbr  
./build.sh test-colony

# Manual execution template
java -Djava.awt.headless=true \
     -cp "autodense/plugin/target/classes:$(cat autodense/plugin/target/runtime-classpath.txt)" \
     com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
     sds_page samples/sds_gel.jpg configs/sds.yaml output/manual_test
```

### Python Optimization Engine
```bash
# Activate environment first
source .venv/bin/activate

# Run optimization
python -m autodense_autotune.java_bridge sds_page \
       --input samples/sds_gel.jpg \
       --config configs/sds.yaml \
       --outdir output/optimization_test
```

### Configuration Validation
```bash
# Test configuration loading
./build.sh test-detect  # Known working baseline

# Check debug output for config validation
./build.sh test-sds 2>&1 | grep "CONFIG_DEBUG"
```

---

## 🚨 Known Issues & Troubleshooting

### Current Blockers (August 2025)
1. **HeadlessException at ImagePreprocessor.java:197**
   - Occurs during Gaussian blur operation
   - Affects: SDS, EtBr, Colony pipelines
   - Working: Detect-only mode
   - Fix needed: Replace `IJ.run("Gaussian Blur...")` with headless-compatible operation

### Historical Issues (Resolved)
- ✅ Configuration schema mismatch (Python-Java handoff)
- ✅ Build directory confusion
- ✅ Preset parameter mis-routing
- ✅ ROI Manager popup interference
- ✅ Environment setup automation

### Common Mistakes to Avoid

#### Path Errors
```bash
# ❌ WRONG: Relative paths or incorrect working directory
cd some/wrong/dir && mvn compile

# ✅ CORRECT: Use build.sh or absolute paths
./build.sh build
# OR
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin && mvn compile
```

#### Environment Errors
```bash
# ❌ WRONG: Forgetting to activate Python venv
python -m autodense_autotune.cli

# ✅ CORRECT: Use build.sh (auto-activates) or manual activation
./build.sh test-sds
# OR
source .venv/bin/activate && python -m autodense_autotune.cli
```

#### Classpath Errors
```bash
# ❌ WRONG: Using wrong classpath file or missing dependency build
java -cp "target/classes" com.betterdairy.autodense.cli.AutotuneAnalysisCLI

# ✅ CORRECT: Use runtime-classpath.txt after dependency:build-classpath
java -cp "target/classes:$(cat target/runtime-classpath.txt)" \
     com.betterdairy.autodense.cli.AutotuneAnalysisCLI
```

#### API Key Errors
```bash
# ❌ WRONG: Missing or invalid API key
export GEMINI_API_KEY="invalid_key"

# ✅ CORRECT: Valid Gemini API key
export GEMINI_API_KEY="AIza..."  # Must start with AIza
# OR set in api-config.properties
```

### Debugging Commands
```bash
# Check build artifacts
ls -la autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar
ls -la autodense/plugin/target/runtime-classpath.txt

# Verify Python environment
source .venv/bin/activate && python -c "import numpy, yaml; print('Python deps OK')"

# Test configuration loading
./build.sh test-detect 2>&1 | grep -E "CONFIG_DEBUG|ERROR|Exception"

# Check Java compilation
cd autodense/plugin && mvn compile -X
```

---

## 📊 File Formats & Data

### Supported Image Formats
- **JPEG:** Primary format for sample images
- **PNG:** Secondary format, synthetic images
- **TIFF:** Enhanced support via TwelveMonkeys ImageIO
- **BMP:** Additional format support
- **Note:** HEIC requires platform-specific libraries

### Configuration Format
- **Type:** YAML
- **Schema:** Validated via JSON Schema Validator
- **Structure:** Nested parameters for detect, preprocess, analyze sections
- **Backup:** Automatic .bak files for recovery

### Output Formats
- **Images:** PNG (stage outputs, overlays)
- **Data:** JSON (analysis results)
- **Reports:** Excel (via Apache POI)
- **Logs:** JSONL (session logging)

---

## 🎯 Critical Paths Reference

### Absolute Paths (MEMORIZE)
```bash
# Project Structure
PROJECT_ROOT="/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense"
PYTHON_VENV="$PROJECT_ROOT/.venv"
MAVEN_DIR="$PROJECT_ROOT/autodense/plugin"

# Build Artifacts
JAR_FILE="$MAVEN_DIR/target/autodense-plugin-0.1.0-SNAPSHOT.jar"
RUNTIME_CLASSPATH="$MAVEN_DIR/target/runtime-classpath.txt"
MAKEFILE_CLASSPATH="$MAVEN_DIR/target/classpath.txt"

# Data Directories
CONFIGS="$PROJECT_ROOT/configs"
SAMPLES="$PROJECT_ROOT/samples"
OUTPUT="$PROJECT_ROOT/output"
CHALLENGE_PACKS="$PROJECT_ROOT/challenge_packs"

# Python Engine
AUTOTUNE_DIR="$PROJECT_ROOT/autodense_autotune"
TESTS_DIR="$PROJECT_ROOT/tests"
```

### Command Templates
```bash
# Standard build command
./build.sh build

# CLI execution template
java -Djava.awt.headless=true \
     -cp "$MAVEN_DIR/target/classes:$(cat $MAVEN_DIR/target/runtime-classpath.txt)" \
     com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
     [PIPELINE] [INPUT] [CONFIG] [OUTPUT]

# Python optimization template  
source .venv/bin/activate
python -m autodense_autotune.java_bridge [PIPELINE] \
       --input [INPUT] --config [CONFIG] --outdir [OUTPUT]
```

---

## 🔄 Current Status (August 31, 2025)

### Working Components
- ✅ **Build System:** Unified `build.sh` handles all contexts
- ✅ **Configuration Loading:** Python-Java handoff validated
- ✅ **Preprocessing:** Generates normalized images correctly
- ✅ **Environment Setup:** Automated Python venv + Maven builds
- ✅ **Detect-Only Mode:** Baseline functionality confirmed

### Known Blockers
- 🚨 **HeadlessException:** At `ImagePreprocessor.java:197` during Gaussian blur
- 🚨 **Full Pipeline:** SDS/EtBr/Colony pipelines blocked by above issue

### Investigation Focus
1. Replace `IJ.run("Gaussian Blur...")` with headless-compatible operation
2. Validate full pipeline completion for all analysis types
3. Test optimization workflow persistence
4. Validate challenge pack specifications

---

## 🎪 Next Session Checklist

### Before Starting Any Work
1. **Verify Environment:**
   ```bash
   cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
   ./build.sh build
   ./build.sh test-detect  # Confirm baseline works
   ```

2. **Check Current Status:**
   ```bash
   git status  # Check for uncommitted changes
   ls -la autodense/plugin/target/  # Verify build artifacts
   ```

3. **Validate Configuration:**
   ```bash
   ls -la configs/sds.yaml configs/colony.yaml configs/etbr.yaml
   ```

### For Build Operations
- Always use `./build.sh build` (not manual Maven)
- Verify JAR and classpath files generated
- Test with known working mode first

### For Testing Operations  
- Start with `./build.sh test-detect` (known working)
- Use absolute paths for custom input/output
- Monitor for HeadlessException in logs

### For Configuration Changes
- Always backup existing configs before modification
- Test configuration loading with detect-only mode
- Validate YAML syntax before full pipeline tests

---

## 🔍 Troubleshooting Guide

### Build Failures
```bash
# Check Java version
java --version  # Must be Java 17

# Clean rebuild
cd autodense/plugin
mvn clean
cd ../.. && ./build.sh build

# Check for missing dependencies
mvn dependency:tree
```

### Runtime Failures
```bash
# Check classpath file exists
ls -la autodense/plugin/target/runtime-classpath.txt

# Verify JAR exists
ls -la autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar

# Test with minimal command
./build.sh test-detect
```

### Python Environment Issues
```bash
# Check venv activation
source .venv/bin/activate
which python  # Should show .venv path

# Check package installation
python -c "import numpy, yaml; print('Packages OK')"

# Recreate venv if needed
rm -rf .venv
python3.13 -m venv .venv
source .venv/bin/activate
pip install numpy scipy scikit-image matplotlib pyyaml
```

### Configuration Issues
```bash
# Test config loading
./build.sh test-detect 2>&1 | grep "CONFIG_DEBUG"

# Validate YAML syntax
python -c "import yaml; yaml.safe_load(open('configs/sds.yaml'))"

# Check for missing config sections
grep -E "detect:|preprocess:|analyze:" configs/sds.yaml
```

---

## 📈 Success Indicators

### Build Success
- ✅ `autodense-plugin-0.1.0-SNAPSHOT.jar` exists in `target/`
- ✅ `runtime-classpath.txt` and `classpath.txt` both generated
- ✅ No Maven compilation errors
- ✅ Python venv activates without errors

### Runtime Success
- ✅ `./build.sh test-detect` completes without HeadlessException
- ✅ Configuration debug logs show real parameter values
- ✅ Output directory contains `stage0_input.png` and `stage1_norm.png`
- ✅ No "ClassNotFoundException" or classpath errors

### Pipeline Success (Target State)
- 🎯 Full SDS/EtBr/Colony pipelines complete without HeadlessException
- 🎯 Optimization loops execute successfully
- 🎯 Challenge pack specifications validate against real data
- 🎯 Python-Java bridge operates reliably under optimization loads

---

**Last Updated:** August 31, 2025  
**Next Review:** Before any major build system changes