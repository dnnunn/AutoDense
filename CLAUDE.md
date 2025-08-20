# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AutoDense is a gel densitometry application built as a standalone Mac-native Fiji/ImageJ2 app with offline natural language control. The project consists of two main Java modules that package into a self-contained macOS application bundle.

## Build System

This is a Maven multi-module project using Java 17:

- **Root build**: `mvn clean package` from `/autodense/`
- **Plugin module**: `mvn clean package` from `/autodense/plugin/`  
- **NL module**: `mvn clean package` from `/autodense/nl/`
- **Build llama.cpp**: `./packaging/scripts/build_llama_universal.sh`
- **Package app**: `./packaging/scripts/package_app.sh`

## Architecture

### Core Modules

1. **plugin** (`autodense-plugin`): ImageJ2 plugin containing core analysis and UI
   - Entry point: `OpenAnalyzeCommand.java` - main ImageJ plugin command
   - Analysis package: Lane detection, band detection, calibration, normalization
   - UI: Swing-based gel analysis interface with drag-and-drop
   - Model: Data structures for lanes, bands, calibration results

2. **nl** (`autodense-nl`): Natural language processing client 
   - Spawns local llama.cpp server processes for offline LLM inference
   - JSON schema validation for natural language commands
   - Context management for gel analysis state

### Key Analysis Components

- **LaneDetector**: Finds gel lanes via vertical projection analysis
- **BandDetector**: Identifies protein bands within lanes using 1D profile analysis  
- **Calibrator**: Fits molecular weight calibration curves from ladder lanes
- **Normalizer**: Applies various normalization strategies (lane total, reference band)
- **Deltas**: Computes statistical comparisons between lanes

### Natural Language Control

The system uses a local LLM (via llama.cpp) to parse natural language commands into structured JSON actions:
- Schema: `/nl/src/main/resources/intent.schema.json`
- Supported actions: set_ladder, detect_bands, calibrate_mw, quantify_bands, normalize, lane_deltas, export
- Standards library: `/plugin/src/main/resources/standards.json`

### Packaging Structure

- **packaging/resources/**: Contains Fiji.app bundle template and app resources
- **packaging/scripts/**: Build scripts for llama.cpp binaries and app packaging
- **Models**: GGUF model files placed in `Contents/Resources/models/`
- **LLM binaries**: Universal llama-server binary in `Contents/Resources/bin/`

## Development Workflow

1. Develop Java code in plugin/ and nl/ modules
2. Test with `mvn clean package` from root or individual modules
3. Build llama.cpp universal binaries if needed
4. Package into standalone .app bundle for distribution
5. Sign and notarize for macOS distribution (see packaging scripts)

## Key Files

- Main entry: `/plugin/src/main/java/com/betterdairy/autodense/plugin/OpenAnalyzeCommand.java:26`
- Core models: `/plugin/src/main/java/com/betterdairy/autodense/model/Models.java`
- NL client: `/nl/src/main/java/com/betterdairy/autodense/nl/NLClient.java`
- Action executor: `/plugin/src/main/java/com/betterdairy/autodense/plugin/ActionExecutor.java`
- UI implementation: `/plugin/src/main/java/com/betterdairy/autodense/plugin/GelUI.java`