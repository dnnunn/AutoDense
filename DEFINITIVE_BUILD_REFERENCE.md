# DEFINITIVE BUILD & STRUCTURE REFERENCE

> **Doc Meta**
> - **Purpose:** Single source of truth for ALL AutoDense build, compile, test, and configuration operations
> - **Scope:** Complete directory structure, environments, classpaths, samples, configs, testing, startup procedures
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-08-31

## 🚨 CRITICAL: READ THIS BEFORE ANY BUILD/COMPILE/TEST OPERATION

This document consolidates ALL essential information to prevent path/environment assumptions and build errors.

---

## 📁 ABSOLUTE PATHS (MEMORIZE THESE)

### Core Directories
- **Root**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`
- **Python venv**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/`
- **Maven dir**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`
- **Python autotune**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense_autotune/`

### Build Artifacts
- **JAR file**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
- **CLI classpath**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/runtime-classpath.txt`
- **Makefile classpath**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/classpath.txt`

### Configuration & Samples
- **Configs**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/`
  - `sds.yaml` (NOT sds_page_basic.yaml)
  - `colony.yaml`
  - `etbr.yaml`
- **Samples**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/`
  - `sds_gel.jpg`
  - `colony_plate.jpg` 
  - `etbr_gel.jpg`
  - `synthetic_*.jpg/png`

### Fiji & ImageJ Libraries
- **Fiji Installation**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/packaging/resources/Fiji.app`
- **ImageJ JAR Libraries**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/packaging/resources/AutoDense.app/Contents/Resources/java/lib/`
  - Contains 162 JAR files including SCIFIO, ImageIO, and other ImageJ libraries
  - Includes: `scifio-0.45.0.jar`, `scifio-jai-imageio-1.1.1.jar`, `imageio-*.jar`

---

## 🔨 BUILD SYSTEM (MANDATORY PROCESS)

### Single Build Command (USE THIS)
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
./build.sh build
```

### What build.sh Does
- ✅ Activates Python venv automatically
- ✅ Maven builds from correct directory (`autodense/plugin/`)
- ✅ Generates BOTH classpath files (`classpath.txt` + `runtime-classpath.txt`)
- ✅ Places JAR in expected location
- ✅ Handles all environment setup

### Manual Build (IF build.sh FAILS)
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin
mvn clean compile
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
```

---

## 🧪 TESTING COMMANDS (USE EXACT PATHS)

### Unified Test Commands
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
./build.sh test-sds      # Uses samples/sds_gel.jpg + configs/sds.yaml
./build.sh test-etbr     # Uses samples/etbr_gel.jpg + configs/etbr.yaml  
./build.sh test-colony   # Uses samples/colony_plate.jpg + configs/colony.yaml
./build.sh test-detect   # Detection-only mode (faster)
```

### Python Bridge Testing
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense_autotune
python3 -c "from java_bridge import run_sds_page; run_sds_page('/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/sds_gel.jpg', '/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml', '/tmp/test_output')"
```

---

## 🐍 PYTHON ENVIRONMENT SETUP

### Python Version & Location
- **Version**: Python 3.13
- **Venv**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/`
- **Activation**: `source .venv/bin/activate` (done by build.sh)

### Required Python Packages
- `pyyaml` - YAML configuration parsing
- `numpy` - Numerical operations
- `scipy` - Scientific computing
- `scikit-image` - Image processing
- `matplotlib` - Plotting (for optimization)

### Python Command (ALWAYS use python3)
```bash
# ❌ WRONG: python
# ✅ CORRECT: python3
python3 script.py
```

---

## ☕ JAVA ENVIRONMENT

### Java Version
- **Required**: Java 17+
- **Current**: Java 17.0.15

### Main Class
- **CLI**: `com.betterdairy.autodense.cli.AutotuneAnalysisCLI`

### Critical Dependencies
- **org.json.JSONObject** - Must be in classpath
- **org.yaml.snakeyaml.Yaml** - Must be in classpath
- **ImageJ/Fiji JARs** - Must be in classpath

### Classpath Generation (REQUIRED BEFORE TESTING)
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

---

## 📋 CONFIGURATION FILES

### Available Configs (EXACT FILENAMES)
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml`
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/colony.yaml`  
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/etbr.yaml`
- `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds_sensitive.yaml` (12-lane optimized)

### 🚨 CRITICAL Configuration Discovery
**Java Code Uses LEGACY Section**: The AutotuneAnalysisCLI.java reads the `"detection:"` section, NOT `"detect:"` or `"bands:"`
- Lane parameters: `expected_lanes`, `sensitivity`, `constant_spacing`  
- Band parameters: `background_subtraction`, `peak_detection_method`
- **Result**: Achieved 12/12 lane detection after fixing configuration section

### ❌ COMMON MISTAKES TO AVOID
- **DON'T USE**: `sds_page_basic.yaml` (doesn't exist)
- **DON'T ASSUME**: Config files in different locations
- **DON'T HARDCODE**: Relative paths in tests

---

## 🖼️ SAMPLE FILES (EXACT PATHS)

### Available Test Images
```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/
├── sds_gel.jpg                 # Main SDS-PAGE test (12 lanes)
├── colony_plate.jpg            # Colony counting test
├── etbr_gel.jpg               # EtBr gel test
├── synthetic_sds_gel2.png      # Generated SDS test
├── synthetic_colony_plate2.png # Generated colony test  
├── synthetic_etbr_gel.jpg      # Generated EtBr test
└── synthetic_etbr_gel2.png     # Generated EtBr test 2
```

### Sample Selection Guidelines
- **SDS testing**: Use `sds_gel.jpg` (known 12-lane gel)
- **Colony testing**: Use `colony_plate.jpg` or `synthetic_colony_plate2.png`
- **EtBr testing**: Use `etbr_gel.jpg`

---

## 🔧 COMMON TROUBLESHOOTING

### ClassNotFoundException Issues
**Problem**: `java.lang.NoClassDefFoundError: org/json/JSONObject`
**Solution**: 
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

### Config File Not Found
**Problem**: `Config file not found: /path/to/config`
**Solution**: Use EXACT paths from this document:
```bash
# ❌ WRONG
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds_page_basic.yaml
# ✅ CORRECT  
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml
```

### Image Loading Failures
**Problem**: `Failed to load image: /path/to/image`
**Solution**: Use EXACT sample paths from this document
```bash
# ❌ WRONG
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/challenge_packs/sds_page_v1/sample_data/gel_001.tiff
# ✅ CORRECT
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/sds_gel.jpg
```

### HeadlessException Issues
**Problem**: `java.awt.HeadlessException`
**Solution**: Use headless-safe alternatives to `IJ.run()` calls
- **Status**: Fixed in main preprocessing pipeline
- **Remaining**: 61 calls across 13 files still need replacement

### ImageJ Patcher Warning (EXPECTED - NOT AN ERROR)
**Message**: `ImageJ patcher not found - some ImageJ features may not work with Java 17+`
**Explanation**: This warning is **EXPECTED and HARMLESS**
- AutoDense uses **SCIFIO** (ImageJ2 modern I/O) instead of ImageJ1
- SCIFIO has built-in Java 17+ compatibility and headless operation
- No patcher needed - this is actually BETTER architecture than ImageJ1+patcher
- AutoDense has 10+ ImageJ/image processing JARs (scifio, imageio-*, common-image)
**Action**: IGNORE this warning - it's informational only

---

## ⚡ QUICK REFERENCE COMMANDS

### Before Any Operation
```bash
# 1. Navigate to root
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense

# 2. Build everything  
./build.sh build

# 3. Test basic functionality
./build.sh test-sds
```

### Rich Telemetry Testing
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense_autotune
python3 -c "
from java_bridge import run_sds_page
import tempfile, json, os
outdir = tempfile.mkdtemp()
run_sds_page('/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/sds_gel.jpg',
             '/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml', 
             outdir)
with open(os.path.join(outdir, 'run_report.json')) as f:
    report = json.load(f)
    print('Telemetry data:', list(report.get('observation', {}).keys()))
"
```

---

## 📁 COMPLETE DIRECTORY STRUCTURE

```
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/
├── build.sh                        # 🔨 UNIFIED BUILD SCRIPT - USE THIS
├── .venv/                          # Python virtual environment
│   ├── bin/activate                # Auto-activated by build.sh  
│   └── lib/python3.13/             # Python packages
├── autodense/
│   └── plugin/                     # 🎯 MAVEN PROJECT ROOT
│       ├── pom.xml                 
│       ├── src/main/java/          # Java source
│       │   └── com/betterdairy/autodense/cli/
│       │       └── AutotuneAnalysisCLI.java  # Main CLI class
│       └── target/                 # 🔧 BUILD OUTPUT
│           ├── classes/            # Compiled classes
│           ├── autodense-plugin-0.1.0-SNAPSHOT.jar
│           ├── classpath.txt       # 📋 REQUIRED FOR JAVA BRIDGE
│           └── runtime-classpath.txt
├── autodense_autotune/             # 🐍 PYTHON OPTIMIZATION ENGINE
│   ├── java_bridge.py              # Python-Java bridge
│   ├── config_manager.py           
│   └── [optimization modules]
├── challenge_packs/                # Task optimization configs
├── configs/                        # 📋 YAML CONFIGS - USE EXACT NAMES
│   ├── sds.yaml                    # ✅ SDS-PAGE config
│   ├── colony.yaml                 # ✅ Colony config  
│   └── etbr.yaml                   # ✅ EtBr config
├── samples/                        # 🖼️ TEST IMAGES - USE EXACT PATHS
│   ├── sds_gel.jpg                 # Main SDS test
│   ├── colony_plate.jpg            # Main colony test
│   └── etbr_gel.jpg                # Main EtBr test
└── output/                         # Analysis results
```

---

## ✅ VALIDATION CHECKLIST

Before claiming "build works" or "test passes", verify:

- [ ] JAR exists: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
- [ ] Classpath exists: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/classpath.txt`  
- [ ] Config loads: Using exact filename `sds.yaml` not `sds_page_basic.yaml`
- [ ] Sample loads: Using exact path `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/sds_gel.jpg`
- [ ] No ClassNotFoundException in logs
- [ ] No "file not found" errors in logs
- [ ] run_report.json generated with observation data

---

**🔥 GOLDEN RULE: ALWAYS CONSULT THIS DOCUMENT BEFORE MAKING PATH/ENVIRONMENT ASSUMPTIONS**