# AutoDense Build Guide - Fixed Organizational Chaos

> **Doc Meta**
> - **Purpose:** Simple, reliable build/test/run procedures that replace organizational chaos
> - **Scope:** Complete build system usage, from environment setup to production deployment
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-30

## 🚨 CRITICAL: Optimization Integration Warning

**⚠️ FOR OPTIMIZATION/AUTOTUNE INTEGRATION: ALWAYS USE --no-exit FLAG ⚠️**
```
🚨 CRITICAL: When calling AutotuneAnalysisCLI from Python bridge or optimization loops:
✅ CORRECT: java ... AutotuneAnalysisCLI --task sds --input ... --output ... --no-exit
❌ WRONG:   java ... AutotuneAnalysisCLI --task sds --input ... --output ... 

WITHOUT --no-exit: Java process terminates and breaks Python-Java bridge
WITH --no-exit:    Java process stays alive for optimization loop communication

ALL PYTHON BRIDGE CALLS MUST INCLUDE --no-exit OR OPTIMIZER QUIETLY DIES!
```

---

## 🎯 Quick Start (One Command Solution)

```bash
# Complete build, test, and validation in one command:
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
./build.sh all
```

This single command replaces the previous chaos of scattered procedures, mixed classpath files, and environment confusion.

## 🔧 Build System Fixes

### Problems Solved

1. **JAR Location Chaos**: JARs now created predictably at `/autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
2. **Classpath Inconsistencies**: Both `classpath.txt` and `runtime-classpath.txt` generated automatically
3. **Directory Confusion**: Build script changes to correct directories automatically
4. **Python Environment Issues**: Integrated venv activation checking
5. **Manual Environment Setup**: Automated validation and clear error messages

### Architecture Overview

```
AutoDense Build System
├── ./build.sh                 # Unified build script (NEW)
│   ├── env-check              # Validate Python + Java environments
│   ├── compile                # Compile Java + generate both classpaths
│   ├── jar                    # Build shaded JAR package
│   ├── test-cli               # Test CLI functionality
│   ├── test-python            # Test Python bridge
│   └── all                    # Complete build + test sequence
│
├── Maven (autodense/plugin/)   # Java compilation
│   ├── target/classes/         # Compiled Java classes
│   ├── target/classpath.txt   # Dependencies only (for Makefile)
│   ├── target/runtime-classpath.txt # Classes + deps (for CLI)
│   └── target/*.jar            # Built JARs (logical location)
│
└── Python (.venv/)            # Python environment
    └── autodense_autotune/     # Optimization modules
```

## 📋 Available Commands

### Environment Management
```bash
./build.sh env-check           # Check Python venv + Java/Maven setup
./build.sh clean               # Clean all build artifacts
```

### Build Commands
```bash
./build.sh compile             # Compile Java + generate classpaths
./build.sh jar                 # Build JAR package
./build.sh package             # Build macOS app bundle
```

### Testing Commands
```bash
./build.sh test-cli            # Test Java CLI with sample image
./build.sh test-python         # Test Python bridge functionality
./build.sh all                 # Complete build + test workflow
```

### Help
```bash
./build.sh help               # Show usage and available commands
```

## 🚀 Usage Examples

### Development Workflow
```bash
# Start development session
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate

# Build and test after code changes
./build.sh compile test-cli

# Complete rebuild when needed
./build.sh clean all
```

### Testing Different Scenarios
```bash
# Test Java CLI directly
./build.sh test-cli

# Test Python optimization
./build.sh test-python

# Test macOS app bundle
./build.sh package
```

### Production Build
```bash
# Complete production build
source .venv/bin/activate
./build.sh all
./build.sh package
```

## 🔍 Understanding the Build Process

### 1. Environment Validation
The build script automatically validates:
- Python venv activation (checks for yaml package)
- Java/Maven availability and versions
- Required directories and file structure

### 2. Java Compilation
- Runs from correct directory (`autodense/plugin/`)
- Generates both classpath variants:
  - `target/classpath.txt` - Dependencies only (Makefile compatibility)
  - `target/runtime-classpath.txt` - Classes + dependencies (CLI usage)

### 3. JAR Packaging
- Uses Maven Shade plugin for dependency management
- Creates shaded JAR at predictable location
- Avoids Fiji version conflicts through relocation

### 4. Testing Integration
- CLI testing uses proper classpath construction
- Python bridge testing validates module imports
- Provides clear success/failure reporting

## 🎯 Quick Reference Commands

### Manual Java CLI (if needed)
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
java -cp "autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar:$(cat autodense/plugin/target/runtime-classpath.txt)" \
  com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
  --detect-only --preprocessed samples/sds_gel.jpg \
  --roi 50,100,800,400 --config-yaml configs/sds.yaml \
  --outdir output/test
```

### Manual Python (if needed)
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
python -m autodense_autotune.cli \
  --spec challenge_packs/sds_page_v1/spec.yaml \
  --input samples/sds_gel.jpg \
  --workdir audits/autotune_runs
```

### Makefile Integration (unchanged)
```bash
make tune-sds-ij INPUT=samples/sds_gel.jpg
make tune-etbr-ij INPUT=samples/etbr_gel.jpg
```

## 🔧 Comprehensive Troubleshooting

### Critical Path Issues

#### Config File Not Found
**Problem**: `Config file not found: /path/to/config`
**Solution**: Use EXACT paths and filenames:
```bash
# ❌ WRONG
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds_page_basic.yaml
# ✅ CORRECT  
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/configs/sds.yaml
```

#### Image Loading Failures
**Problem**: `Failed to load image: /path/to/image`
**Solution**: Use exact sample paths:
```bash
# ❌ WRONG
./challenge_packs/sds_page_v1/sample_data/gel_001.tiff
# ✅ CORRECT
/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/samples/sds_gel.jpg
```

#### ClassNotFoundException Issues
**Problem**: `java.lang.NoClassDefFoundError: org/json/JSONObject`
**Solution**: 
```bash
cd autodense/plugin
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

#### HeadlessException Issues
**Problem**: `java.awt.HeadlessException`
**Solution**: Use headless-safe alternatives to `IJ.run()` calls
- **Status**: Fixed in main preprocessing pipeline
- **Remaining**: Some calls across multiple files still need replacement

#### ImageJ Patcher Warning (EXPECTED - NOT AN ERROR)
**Message**: `ImageJ patcher not found - some ImageJ features may not work with Java 17+`
**Explanation**: This warning is **EXPECTED and HARMLESS**
- AutoDense uses **SCIFIO** (ImageJ2 modern I/O) instead of ImageJ1
- SCIFIO has built-in Java 17+ compatibility and headless operation
- No patcher needed - this is actually BETTER architecture than ImageJ1+patcher
- AutoDense has 10+ ImageJ/image processing JARs (scifio, imageio-*, common-image)
**Action**: IGNORE this warning - it's informational only

## ⚠️ Common Issues and Solutions

### "Python venv not activated"
```bash
# Solution:
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
./build.sh env-check
```

### "JAR not found at expected location"
```bash
# Solution:
./build.sh jar
# Or for complete rebuild:
./build.sh clean all
```

### "Runtime classpath not found"
```bash
# Solution:
./build.sh compile
```

### "CLI test failed"
```bash
# Check if environment is ready:
./build.sh env-check

# Rebuild everything:
./build.sh clean all
```

## 🔄 Migration from Old Procedures

### Old Way (Chaos)
```bash
# Multiple steps, different directories, manual environment management
cd autodense/plugin
mvn compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
cd ../..
source .venv/bin/activate
java -cp "confusing/classpath/construction" ...
```

### New Way (Unified)
```bash
# Single command that handles everything
source .venv/bin/activate && ./build.sh all
```

## 🚀 Benefits of New System

1. **One Command Solution**: `./build.sh all` replaces multiple manual steps
2. **Consistent JAR Location**: Always at `autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
3. **Dual Classpath Support**: Automatically generates both required variants
4. **Environment Validation**: Clear error messages for setup issues
5. **Directory Independence**: Works from any directory (changes to correct locations)
6. **Error Recovery**: Specific commands for fixing common issues
7. **Production Ready**: Includes packaging for distribution

## ✅ Validation Checklist

Before claiming "build works" or "test passes", verify:

- [ ] JAR exists: `autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
- [ ] Classpath exists: `autodense/plugin/target/classpath.txt`  
- [ ] Config loads: Using exact filename `sds.yaml` not `sds_page_basic.yaml`
- [ ] Sample loads: Using exact path `samples/sds_gel.jpg`
- [ ] No ClassNotFoundException in logs
- [ ] No "file not found" errors in logs
- [ ] run_report.json generated with observation data
- [ ] Python bridge passes --no-exit flag to Java CLI

## 📁 Complete Directory Structure

```
AutoDense/
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
│   ├── java_bridge.py              # Python-Java bridge (MUST use --no-exit)
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

## 🏁 Next Steps

After running `./build.sh all` successfully:

1. **Development**: Use `./build.sh compile test-cli` for rapid iteration
2. **Analysis**: Run analyses using the working CLI or Python bridge
3. **Distribution**: Use `./build.sh package` for macOS app bundle
4. **Documentation**: Update any project-specific procedures to use new build system

The organizational chaos is now solved - one script, predictable behavior, clear error messages.