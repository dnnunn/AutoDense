# Project Structure: August 30, 2025 - Environment Fix Session

> **Doc Meta**
> - **Purpose:** Project structure with CRITICAL environment procedures to prevent session time waste
> - **Scope:** Complete directory tree with mandatory working procedures that MUST be followed
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-30

## 🚨 CRITICAL: Essential Working Procedures (ORGANIZATIONAL CHAOS FIXED!)

**NEW UNIFIED SYSTEM - No more wasting 20+ minutes per session!**

### One Command Solution (REPLACES ALL MANUAL PROCEDURES)
```bash
# Complete build, test, and validation:
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
./build.sh all
```

### Individual Commands (when needed)
```bash
# Check environment:
./build.sh env-check

# Build and test:
./build.sh compile test-cli

# Clean rebuild:
./build.sh clean all

# Show all options:
./build.sh help
```

**PROBLEMS SOLVED:**
✅ **JAR Location**: Always at `autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`  
✅ **Classpath Files**: Both `classpath.txt` and `runtime-classpath.txt` generated automatically  
✅ **Directory Confusion**: Script changes to correct directories automatically  
✅ **Python Environment**: Integrated venv activation checking  
✅ **Error Messages**: Clear guidance when something goes wrong  

**SEE**: `/BUILD_GUIDE.md` for complete documentation

### Absolute Paths (MEMORIZE)
- **Root**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`
- **Python venv**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/`
- **Maven dir**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
- **JAR file**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
- **CLI classpath**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/runtime-classpath.txt`
- **Makefile classpath**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/classpath.txt`
- **Configs**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/`
- **Samples**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/`

### Two Ways to Run Complete Analysis

**Option 1: Direct Java CLI (detect-only mode)**
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
# Needs runtime-classpath.txt:
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt -q
java -cp "autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar:$(cat autodense/plugin/target/runtime-classpath.txt)" com.betterdairy.autodense.cli.AutotuneAnalysisCLI --detect-only [args]
```

**Option 2: Makefile with Python Bridge (full analysis)**
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
# Needs classpath.txt:
make build-java  # Creates target/classpath.txt
make tune-sds-ij INPUT=samples/sds_gel.jpg
make tune-etbr-ij INPUT=samples/etbr_gel.jpg
```

### Common Mistakes to NEVER Make Again
❌ Run `mvn` from root (wrong directory)  
❌ Assume packages globally installed (use .venv)  
❌ Run Java from plugin directory (wrong location)  
❌ Try installing packages with mamba/pip (they exist in .venv)  
❌ Mix up classpath.txt vs runtime-classpath.txt (different tools expect different names)

---

## Project Overview

**Total Structure**: 54 directories, 196 files  
**Root Path**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`

## Current Working State (August 30, 2025)

### Recent Changes
1. **Baseline removal auditor fix implemented** - Safe parameter grids in challenge packs
2. **Config integration completed** - detect-only CLI mode with legacy config loading
3. **Gating and honest reporting** - Metadata creation for transparent results
4. **Environment documentation** - This critical procedures section added

### Files Modified Today
- `autodense/plugin/src/main/java/com/betterdairy/autodense/cli/AutotuneAnalysisCLI.java` - Fixed to use `loadConfigFileLegacy()` instead of Python validation
- Updated challenge pack configurations with safe baseline parameters
- Enhanced structure documentation with mandatory procedures

## Directory Structure 

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
├── .venv/                          # Python virtual environment (CRITICAL)
│   ├── bin/activate                # Must source this first
│   └── lib/python3.13/             # Contains yaml, numpy, etc.
├── autodense/
│   └── plugin/                     # Maven must run from HERE
│       ├── pom.xml                 # Maven project file  
│       ├── src/main/java/          # Java source code
│       └── target/                 # Compiled classes and JARs
├── autodense_autotune/             # Python optimization engine
│   ├── __pycache__/                # Proves modules work (from Aug 28-29)
│   ├── config_manager.py           # Config management
│   └── *.py                        # Optimization modules
├── challenge_packs/                # Task-specific optimization
│   ├── sds_page_v1/spec.yaml       # Updated with safe params
│   ├── colony_count_v1/spec.yaml   # Updated with safe params
│   └── etbr_v1/spec.yaml           # Updated with safe params
├── configs/                        # YAML configuration files
│   ├── sds.yaml                    # SDS-PAGE analysis config
│   ├── colony.yaml                 # Colony counting config
│   └── etbr.yaml                   # EtBr gel config
├── samples/                        # Test images (9 files)
│   ├── sds_gel.jpg                 # Main test image
│   ├── colony_plate.jpg            
│   ├── etbr_gel.jpg
│   └── synthetic_*.jpg/png         # Generated test images
├── output/                         # Analysis results
├── target/                         # Java compilation output (from root)
│   ├── autodense-plugin-0.1.0-SNAPSHOT.jar
│   └── runtime-classpath.txt       # Critical for Java execution
├── scripts/
│   └── test_env.sh                 # Environment activation script
├── docs/                           # Documentation
└── StructureDocs/                  # Session structure docs
    └── Structure_August_30_2025_Environment_Fix.md  # This file
```

## Key Working Components

### Python Environment (.venv/)
- **Status**: Fully configured with all scientific packages
- **Evidence**: `autodense_autotune/__pycache__/` shows successful imports (Aug 28-29)
- **Required**: Must `source .venv/bin/activate` before any Python operations
- **Contains**: yaml 6.0.2, numpy, scipy, scikit-image, matplotlib, pandas, etc.

### Java Compilation (autodense/plugin/)
- **Maven Directory**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
- **Compilation**: `mvn compile -q` (from plugin directory)
- **Output**: Classes in `target/classes/`, JARs in root `target/`
- **Classpath**: Generated via `mvn dependency:build-classpath`

### Configuration System
- **Configs Location**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/`
- **Updated Today**: All three configs have safe baseline parameters
- **Loading**: AutotuneAnalysisCLI now uses `loadConfigFileLegacy()` for detect-only mode

### Testing Infrastructure
- **Sample Images**: 9 files in `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/`
- **Test Command**: Java CLI with exact classpath and parameters (see CRITICAL section)
- **Expected Output**: Lane detection with honest reporting metadata

## Next Session Checklist

✅ **Before ANY work, verify environment:**
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
python -c "import yaml; print('Environment OK')"
```

✅ **If compiling Java:**
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin
mvn compile -q
```

✅ **If running Java CLI:**
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
java -cp "autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar:$(cat autodense/plugin/target/runtime-classpath.txt)" com.betterdairy.autodense.cli.AutotuneAnalysisCLI [args]
```

## Current Tasks Status

**Completed:**
- ✅ Safe baseline parameter grids in all challenge packs
- ✅ Config integration for detect-only CLI mode  
- ✅ Gating and honest reporting metadata implementation
- ✅ Environment procedures documentation

**Completed This Session:**
- ✅ Fixed detect-only CLI with legacy config loading (bypasses Python environment issues)
- ✅ Diagnosed fundamental lane detection algorithm flaws (finds 5/12 lanes)
- ✅ Created comprehensive session documentation (4 new documents)
- ✅ Updated document catalog with proper categorization

**Critical Priority for Next Session:**
- 🚨 Fix core lane detection algorithm (finds only 5/12 lanes)
- 🚨 Remove biased target matching from detection logic

**Lower Priority:**
- ⏳ Add separate bands.baseline config for band detection  
- ⏳ Test complete implementation once detection works

This structure document serves as the definitive reference to prevent environment setup time waste in future sessions.