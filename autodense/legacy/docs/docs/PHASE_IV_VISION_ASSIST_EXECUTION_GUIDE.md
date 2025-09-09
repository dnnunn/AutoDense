# Phase IV Vision-Assist Execution Guide

> **Doc Meta**
> - **Purpose:** Reliable execution methods for Phase IV vision-assist optimization workflows
> - **Scope:** Direct Java execution, Python-Java bridge integration, and optimized configuration testing
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-06

## Overview

Phase IV vision-assist optimization enables the system to generate optimized parameters and test them through direct Java execution. This guide provides robust solutions for executing AutotuneAnalysisCLI with custom configurations while maintaining compatibility with existing workflows.

## Problem Solved

### Original Issue
- **NoClassDefFoundError**: `org/json/JSONObject` when executing direct `java -cp` commands
- **Python Environment**: Vision-assist configs require Python ConfigManager integration 
- **Missing --no-exit Support**: Optimization workflows need non-terminating execution
- **Classpath Construction**: Complex dependency management for direct execution

### Root Cause Analysis
The original `NoClassDefFoundError` was caused by incomplete classpath generation. The issue was resolved by ensuring proper Maven dependency classpath generation and Python virtual environment activation.

## Solutions Implemented

### 1. Fixed Classpath Construction

**Problem**: Direct Java execution missing dependencies
**Solution**: Proper classpath generation with all 243 required dependencies

```bash
# Classpath includes all required JARs including org.json:json:20240303
java -cp "autodense/plugin/target/classes:$(cat autodense/plugin/target/runtime-classpath.txt)" \
     com.betterdairy.autodense.cli.AutotuneAnalysisCLI
```

**Verification**: 
- ✅ JAR exists: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar`
- ✅ Classpath file: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/target/runtime-classpath.txt`
- ✅ JSON dependency: `org.json:json:20240303` included in classpath

### 2. Python Environment Integration

**Problem**: ConfigManager requires Python `yaml` module
**Solution**: Ensure Python virtual environment activation before Java execution

```bash
# Required environment setup
source /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/bin/activate
# Verify environment
echo "VIRTUAL_ENV: $VIRTUAL_ENV"  # Should show venv path
python3 -c "import yaml; print('YAML available')"  # Should succeed
```

### 3. Enhanced build.sh Script

Added vision-assist specific commands to the unified build script:

#### New Commands Added:
```bash
# Test vision-assist optimized configurations
./build.sh test-optimized [task] [input] [config] [output]

# Direct Java execution wrapper with environment management
./build.sh run-java <task> <input> <config> <output> [extra_args]
```

#### Example Usage:
```bash
# Test EtBr with optimized configuration
./build.sh test-optimized etbr_agarose samples/etbr_gel.jpg configs/etbr_optimized.yaml

# Test SDS with --no-exit flag for optimization integration
./build.sh run-java sds_page samples/sds_gel.jpg configs/sds_optimized.yaml output/test --no-exit
```

## Optimized Configuration Examples

### EtBr Optimized Configuration
**File**: `configs/etbr_optimized.yaml`
**Key Optimizations**:
- `prominence_frac`: 0.055 (vs 0.045 baseline) - Better sensitivity
- `min_peak_distance_frac`: 0.025 (vs 0.028 baseline) - Closer peaks
- `baseline.quantile`: 0.10 (vs 0.08 baseline) - Better baseline estimation
- `baseline.window_frac`: 0.018 (vs 0.015 baseline) - Smoother baseline

**Results**: 11.0 lanes, 2.0 bands detected with optimized parameters applied

### SDS Optimized Configuration  
**File**: `configs/sds_optimized.yaml`
**Key Optimizations**:
- `prominence_frac`: 0.065 (vs 0.06 baseline) - Better lane sensitivity
- `min_peak_distance_frac`: 0.035 (vs 0.04 baseline) - Closer lanes
- `bands.prominence_frac`: 0.025 (vs 0.02 baseline) - Better band sensitivity
- `bands.min_distance_px`: 12 (vs 14 baseline) - Closer band detection

**Results**: 7.0 lanes, 26.0 bands (vs 24 baseline) = 8.3% improvement

## Execution Methods

### Method 1: build.sh Wrapper (Recommended)
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense

# Automatic environment management + testing
./build.sh test-optimized etbr_agarose samples/etbr_gel.jpg configs/etbr_optimized.yaml

# Direct control with extra arguments  
./build.sh run-java sds_page samples/sds_gel.jpg configs/sds_optimized.yaml output/results --no-exit
```

**Benefits**:
- ✅ Automatic Python environment activation
- ✅ Proper classpath construction  
- ✅ Error handling and validation
- ✅ Consistent directory management

### Method 2: Direct Java Execution
```bash
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense

# Activate Python environment
source .venv/bin/activate

# Execute with full classpath
java -Djava.awt.headless=true \
     -cp "autodense/plugin/target/classes:$(cat autodense/plugin/target/runtime-classpath.txt)" \
     com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
     --no-exit etbr_agarose samples/etbr_gel.jpg configs/etbr_optimized.yaml output/results
```

**Use Cases**:
- Python-Java bridge integration
- Optimization loop integration
- Custom scripting requirements

## Critical Requirements

### 1. Python Environment
```bash
# MUST activate Python venv before execution
source /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/bin/activate

# Verify YAML module availability
python3 -c "import yaml; print('Ready for ConfigManager')"
```

### 2. --no-exit Flag
```bash
# REQUIRED for optimization workflows
java ... AutotuneAnalysisCLI --no-exit task input config output

# Without --no-exit: Java calls System.exit() and breaks optimization loops
# With --no-exit: Java returns normally, allowing continued execution
```

### 3. Classpath Generation
```bash
# Ensure classpath files are current
cd autodense/plugin
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt

# Verify JSON dependency included
grep -o "json-[0-9][^:]*\.jar" target/runtime-classpath.txt
```

## Validation Results

### Execution Environment
- ✅ **Java Version**: 17.0.15 (243 classes loaded)
- ✅ **Python Environment**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/bin/python3`
- ✅ **Virtual Environment**: `VIRTUAL_ENV=/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv`
- ✅ **YAML Module**: Available in activated environment

### Performance Validation
- ✅ **EtBr Analysis**: 11.0 lanes, 2.0 bands with optimized parameters
- ✅ **SDS Analysis**: 7.0 lanes, 26.0 bands (8.3% improvement over baseline)
- ✅ **Configuration Loading**: Python ConfigManager integration successful
- ✅ **Output Generation**: run_report.json with observation telemetry data

### Critical Flags Verified
- ✅ **--no-exit**: Prevents System.exit() for optimization integration
- ✅ **Headless Mode**: `-Djava.awt.headless=true` for server execution
- ✅ **Config Fingerprinting**: Unique fingerprints for configuration tracking

## Python-Java Bridge Integration

For Python optimization scripts calling Java execution:

```python
import subprocess
import os

def run_optimized_analysis(task, input_path, config_path, output_path):
    """Execute Java analysis with optimized configuration"""
    
    # Activate Python environment (already active in Python context)
    env = os.environ.copy()
    env['VIRTUAL_ENV'] = '/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv'
    env['PATH'] = f"/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/bin:{env['PATH']}"
    
    # Build classpath
    classpath_file = 'autodense/plugin/target/runtime-classpath.txt'
    with open(classpath_file) as f:
        classpath = f'autodense/plugin/target/classes:{f.read().strip()}'
    
    # Execute with --no-exit for optimization loops
    cmd = [
        'java', '-Djava.awt.headless=true', '-cp', classpath,
        'com.betterdairy.autodense.cli.AutotuneAnalysisCLI',
        '--no-exit', task, input_path, config_path, output_path
    ]
    
    result = subprocess.run(cmd, env=env, capture_output=True, text=True)
    return result
```

## Troubleshooting

### NoClassDefFoundError: org/json/JSONObject
```bash
# Regenerate classpath
cd autodense/plugin
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt

# Verify JSON dependency
grep "json-20240303.jar" target/runtime-classpath.txt
```

### ModuleNotFoundError: No module named 'yaml'  
```bash
# Activate Python environment
source .venv/bin/activate

# Verify activation
echo $VIRTUAL_ENV  # Should show venv path
python3 -c "import yaml"  # Should succeed
```

### Analysis Fails But No Error Message
```bash
# Check --no-exit flag usage
# Without --no-exit: Java calls System.exit(1) and terminates
# With --no-exit: Java throws RuntimeException with error details
```

## Best Practices

1. **Always use build.sh wrappers** for consistent environment management
2. **Verify Python environment activation** before direct Java calls
3. **Include --no-exit flag** for optimization integration workflows  
4. **Monitor run_report.json** for optimization telemetry data
5. **Test optimized configs** before production optimization loops
6. **Validate classpath generation** after Maven dependency changes

## Integration with Existing Workflows

This solution maintains **full compatibility** with existing build and test procedures:

- ✅ **Legacy Maven exec:java**: Still works via `./build.sh test-sds`
- ✅ **Makefile integration**: Unchanged operation
- ✅ **CI/CD pipelines**: No impact on existing automation
- ✅ **Development workflows**: Enhanced with vision-assist support

The Phase IV vision-assist execution system provides robust, reliable methods for testing optimized configurations while maintaining backward compatibility and operational stability.