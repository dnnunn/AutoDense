# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AutoDense is a gel densitometry application built as a standalone Mac-native Fiji/ImageJ2 app with AI-powered control via Google Gemini. The system uses a **handle-based architecture** where Gemini acts as the planner and ImageJ/Fiji executes the actual image analysis operations.

## Architecture (NEW - Handle-Based System)

### Core Design Principle
- **Gemini = Planner**: Emits structured tool calls, never processes pixels
- **ImageJ = Executor**: Performs all image operations, maintains state
- **Handles = References**: Images and overlays referenced by handles, not pixels

### Key Components

1. **SessionStore** (`/plugin/src/main/java/com/betterdairy/autodense/session/SessionStore.java`)
   - Maintains all state (images, overlays, analysis results)
   - Issues handles (e.g., `img_abc123`, `ov_def456`)
   - Never passes pixels to LLM

2. **GelAnalysisTools** (`/plugin/src/main/java/com/betterdairy/autodense/tools/GelAnalysisTools.java`)
   - Tool implementations for Gemini function calling
   - Each tool operates on handles, not pixels
   - Available tools:
     - `open_image`: Load gel image → returns image_handle
     - `preprocess`: Apply enhancements (rotate, flip, contrast, background)
     - `detect_lanes`: Find lanes → returns overlay_handle
     - `detect_bands`: Find bands within lanes
     - `adjust_lanes`: Fine-tune lane positions
     - `render_overlay_png`: Export view for user
     - `quantify_bands`: Measure band intensities
     - `export_results`: Save CSV/JSON/PNG

3. **GeminiOrchestrator** (`/plugin/src/main/java/com/betterdairy/autodense/orchestrator/GeminiOrchestrator.java`)
   - Coordinates Gemini planning with ImageJ execution
   - Sends tool schemas to Gemini
   - Executes tool calls from Gemini
   - Returns results with handles

### Workflow Example
```
User: "Open gel.tif, detect 12 lanes, find bands, export CSV"
↓
Gemini: tool_call("open_image", {path: "gel.tif"})
← Returns: {image_handle: "img_abc123"}
↓
Gemini: tool_call("detect_lanes", {image_handle: "img_abc123", expected_lanes: 12})
← Returns: {overlay_handle: "ov_def456", lanes_found: 12}
↓
Gemini: tool_call("detect_bands", {image_handle: "img_abc123"})
← Returns: {bands_total: 67}
↓
Gemini: tool_call("export_results", {image_handle: "img_abc123", formats: ["csv"]})
← Returns: {exported_files: ["results.csv"]}
```

## Build System

Maven single-module project using Java 17:

### Development Build & Run Commands

**Build:**
```bash
mvn -q -DskipTests=true -f autodense/pom.xml clean install
```

**Run with Gemini API:**
```bash
mvn -q -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.plugin.EnhancedImageJLauncher \
  -Dexec.classpathScope=runtime \
  -DGEMINI_API_KEY=your_api_key_here
```

## Core Analysis Components

- **LaneDetector**: Finds gel lanes via vertical projection analysis
- **BandDetector**: Identifies protein bands within lanes using 1D profile analysis  
- **FijiBandDetector**: Enhanced band detection with Fiji algorithms
- **BandQuantification**: Quantifies band intensities with background subtraction
- **Calibrator**: Fits molecular weight calibration curves from ladder lanes
- **Normalizer**: Applies various normalization strategies
- **ImagePreprocessor**: Handles saturation, contrast, filtering
- **WorkflowManager**: Manages analysis pipelines

## Migration from Old Architecture

### Old System (Removed)
- **GelUI.java**: Monolithic UI with embedded NLP (deprecated)
- **NaturalLanguageProcessor**: Pattern matching system (removed)
- **nl module**: Local LLM via llama.cpp (removed)
- **GGUF models**: Local model files (removed)
- Direct pixel manipulation
- Context loss between commands

### Current System
- **GeminiOrchestrator**: Clean separation of concerns
- **Gemini Cloud API**: Advanced reasoning and vision
- **Handle-based state**: SessionStore maintains context
- **Embedded chat**: Direct conversation in UI
- **Tool-based execution**: Structured function calls

### Key Changes
| Removed | Current |
|---------|---------|
| Local LLM server | Gemini Cloud API only |
| NL module | Plugin module only |
| Pattern matching | Vision analysis + reasoning |
| Dialog-based chat | Embedded chat interface |
| llama.cpp binaries | No local inference |

## API Configuration

Set Gemini API key via:
- Environment variable: `export GEMINI_API_KEY=your_key`
- System property: `-DGEMINI_API_KEY=your_key`

## Testing Protocol

When user provides feedback:
1. Kill running processes: `pkill -f ImageJ`
2. Rebuild with changes
3. Run with API key
4. Test handle persistence across commands

## Key Files (Updated)

- **Orchestrator**: `/plugin/src/main/java/com/betterdairy/autodense/orchestrator/GeminiOrchestrator.java`
- **Session Store**: `/plugin/src/main/java/com/betterdairy/autodense/session/SessionStore.java`
- **Tools**: `/plugin/src/main/java/com/betterdairy/autodense/tools/GelAnalysisTools.java`
- **Models**: `/plugin/src/main/java/com/betterdairy/autodense/model/Models.java`
- **Analysis**: `/plugin/src/main/java/com/betterdairy/autodense/analysis/` (all detectors)
- **Main UI**: `/plugin/src/main/java/com/betterdairy/autodense/plugin/GelUI.java` (embedded chat)

## Design Principles

1. **Never pass pixels to LLM** - Use handles for all references
2. **Gemini plans, ImageJ executes** - Clear separation of concerns
3. **Tools are deterministic** - Same inputs → same outputs
4. **State persists in SessionStore** - Not in LLM memory
5. **One tool call at a time** - Sequential execution for clarity

## Troubleshooting

### Common Issues

**Gemini API Errors:**
- 500 errors: Check API key validity and billing status
- 429 errors: API quota exceeded, wait or upgrade plan
- Invalid requests: Verify tool call JSON format

**State Management:**
- "Image not found": Handle expired or incorrect, check SessionStore
- Overlay persistence: Use putOverlay/getOverlay with image handle
- Analysis data: Store results with putAnalysis, retrieve with getAnalysis

**ImageJ Integration:**
- Plugin not found: Ensure EnhancedImageJLauncher launches complete ImageJ
- UI focus issues: Use ImageJ dialogs instead of Swing when possible
- Memory leaks: Clear SessionStore periodically for long sessions

### Debug Logging

Enable debug output:
```bash
-Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

## Dependencies

- **Java 17+** (required)
- **ImageJ2/SciJava** framework
- **Gemini Pro Vision API** (cloud service)
- **JSON processing** (org.json)
- **Maven 3.8+** for builds

## Known Limitations

1. **ImageJ Dialog Spinners**: Numeric fields don't support traditional spinner arrows
2. **Single Session**: Current design handles one gel analysis at a time
3. **Cloud Dependency**: Requires internet for Gemini API calls
4. **Memory Usage**: Large images consume significant RAM in SessionStore
5. **Tool Call Ordering**: Sequential execution may be slower than parallel

## Performance Considerations

- **Image Caching**: SessionStore keeps full ImagePlus objects in memory
- **Overlay Rendering**: PNG exports scale down large images automatically
- **API Latency**: Gemini responses typically 2-5 seconds
- **Analysis Speed**: ImageJ operations are CPU-intensive, not GPU accelerated

## Security Notes

- **API Keys**: Never commit keys to repository, use environment variables
- **Temp Files**: GelAnalysisTools creates temporary PNG files for exports
- **Network**: Gemini API calls send base64-encoded image data to Google
- **Local Data**: All analysis results stored locally in SessionStore

## Future Enhancements

- Capability registry from SciJava introspection
- Auto-tune loops for parameter optimization  
- Workflow recording and replay
- Multi-image session support
- Batch processing pipelines
- Local caching of Gemini responses
- GPU-accelerated analysis kernels