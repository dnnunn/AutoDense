# AutoDense

AI-powered gel densitometry application built as a Mac-native Fiji/ImageJ2 app with Google Gemini
integration for natural language control.

## Quick Start

**Prerequisites:** Java 17+, Maven 3.8+, Gemini API key

**Build:**

```bash
cd autodense/
mvn -q -DskipTests=true -f pom.xml clean install
```

**Run with AI control:**

```bash
mvn -q -f plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.plugin.EnhancedImageJLauncher \
  -Dexec.classpathScope=runtime \
  -DGEMINI_API_KEY=your_api_key_here
```

## Architecture

AutoDense uses a **handle-based architecture** where:

- **Gemini** acts as the planner, emitting tool calls in natural language
- **ImageJ/Fiji** executes all image analysis operations
- **SessionStore** maintains state via handles (no pixels passed to LLM)

### Core Components

- **plugin/**: ImageJ2 plugin with analysis engine and UI
  - `SessionStore`: Handle-based state management
  - `GelAnalysisTools`: Tool implementations for Gemini
  - `GeminiOrchestrator`: Coordinates AI planning with ImageJ execution
  - Analysis classes: Lane/band detection, quantification, calibration

- **nl/**: *(Removed - local LLM support discontinued)*

- **packaging/**: Standalone app bundling
  - Build scripts for universal binaries
  - App bundle resources and signing

## Usage

1. **Open AutoDense**: Run with Gemini API key
2. **Load gel image**: Drag & drop or use natural language
3. **Analyze with AI**: "Detect 12 lanes, find bands, quantify intensities"
4. **Export results**: CSV, JSON, or annotated images

### Example AI Commands

- "Open gel.tif and detect lanes"
- "I see 8 lanes, find all protein bands"
- "Quantify band intensities using median background"
- "Export results as CSV and annotated PNG"
- "The lanes need adjustment, shift them 2 pixels right"

## Key Features

- **AI-Guided Analysis**: Natural language control via Gemini Vision
- **Full Fiji Integration**: Complete ImageJ2/Fiji functionality available
- **Handle-Based Architecture**: Persistent state across AI commands
- **Expert Quantification**: Fiji-compatible band measurement algorithms
- **Flexible Export**: CSV, JSON, PNG with overlays
- **Mac-Native**: Standalone app bundle with code signing

## Documentation

- **CLAUDE.md**: Complete architecture and development guide
- **Project Docs/**: Implementation plans and migration notes

## Requirements

- macOS 10.15+ (for packaged app)
- Java 17+ (for development)
- Google Gemini API access (for AI features)
- Maven 3.8+ (for building)

## License

Proprietary - Better Dairy Inc.
