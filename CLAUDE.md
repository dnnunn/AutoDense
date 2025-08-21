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
     - `enable_band_assist`: **NEW** Enable user-assisted band identification
     - `disable_band_assist`: **NEW** Disable user-assisted mode
     - `configure_band_assist`: **NEW** Configure BandAssist parameters
     - `export_results`: Save CSV/JSON/PNG

3. **GeminiOrchestrator** (`/plugin/src/main/java/com/betterdairy/autodense/orchestrator/GeminiOrchestrator.java`)
   - Coordinates Gemini planning with ImageJ execution
   - Sends tool schemas to Gemini
   - Executes tool calls from Gemini
   - Returns results with handles
   - **NEW**: Integrated with comprehensive session logging

4. **SessionLogger** (`/plugin/src/main/java/com/betterdairy/autodense/session/SessionLogger.java`)
   - **NEW**: Comprehensive JSON logging system for troubleshooting and training
   - Logs all conversations between user and Gemini
   - Records all tool calls with parameters, results, and execution times
   - Tracks session events, errors, and API interactions
   - Automatic log rotation and cleanup
   - Privacy-aware sanitization (removes API keys, truncates large data)

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

## Session Logging System (NEW)

### Overview
AutoDense now includes comprehensive session logging for troubleshooting, training, and quality assurance:

### Log File Structure
```
~/.autodense/logs/
├── autodense_session_20250821_210913_abc123.jsonl    # Main log (JSONL format)
├── autodense_session_20250821_210913_abc123_summary.json    # Session summary
└── [automatic rotation and cleanup]
```

### What Gets Logged
1. **Conversations**: All user messages and Gemini responses
2. **Tool Calls**: Every function call with parameters, results, execution times
3. **Session Events**: Image loading, analysis completion, errors
4. **API Interactions**: Gemini API calls with response times and token usage
5. **System Info**: Java version, OS, timezone, session metadata

### Log Entry Types
```json
// Conversation entry
{"type": "conversation", "speaker": "user", "message": "Detect 12 lanes", "timestamp": "..."}

// Tool call entry  
{"type": "tool_call", "tool_name": "detect_lanes", "execution_time_ms": 850, "success": true, "..."}

// Session event
{"type": "session_event", "event_type": "image_loaded", "data": {"handle": "img_123"}, "..."}

// API call (with sanitized data)
{"type": "gemini_api", "endpoint": "analyzeGel", "status_code": 200, "response_time_ms": 1250, "..."}
```

### Privacy & Security
- **API Keys**: Automatically removed from logs
- **Large Data**: Base64 images truncated with size metadata
- **File Paths**: Converted to relative paths (~/.../file.jpg)
- **Sensitive Info**: Sanitized recursively from JSON structures

### Usage
```java
// Access via GeminiOrchestrator
GeminiOrchestrator orchestrator = new GeminiOrchestrator(apiKey);

// All operations are automatically logged
orchestrator.processCommand("Detect lanes", gelImage);

// Export logs for analysis
orchestrator.exportSessionLog("/path/to/export");
```

### Configuration
```bash
# Set custom log directory
-Dautodense.log.dir=/custom/log/path

# Log rotation (defaults)
# - Max 50MB per file
# - Keep 100 most recent files
```

### Testing
```bash
# Run logging demo
mvn -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.logging.SessionLoggingExample

# Run BandAssist demo
mvn -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.analysis.BandAssistDemo
```

## BandAssist Feature (NEW)

### Overview
BandAssist is a user-assisted band identification system that allows users to click on a band in one lane and automatically find the corresponding band in all other lanes.

### How It Works
1. **Enable BandAssist**: User or Gemini enables the assist mode
2. **User Click**: User clicks on any band in any lane 
3. **Seed Refinement**: System refines the clicked position to nearest peak
4. **Rf Calculation**: Computes relative mobility (Rf) of the seed band
5. **Propagation**: Searches other lanes for bands at the same Rf
6. **Quality Assessment**: Filters results by SNR, width similarity, and prominence
7. **Display Results**: Shows identified bands with confidence indicators

### Workflow Example
```
User: "Enable BandAssist for this gel"
↓
Gemini: tool_call("enable_band_assist", {image_handle: "img_abc123"})
← Returns: {band_assist_enabled: true, lanes_available: 12}
↓
User clicks on a band in lane 3
↓ 
System: Identifies corresponding bands in lanes 1,2,4,5,6,7,8,9,10,11,12
↓
Display: Green=high confidence, Orange=medium, Red=low confidence
```

### Technical Components

**Helper Classes:**
- `Profiles.java`: Intensity profile generation from gel lanes
- `Peaks.java`: Peak detection and sub-pixel refinement  
- `Quant.java`: Band quantification with background correction
- `OverlayRenderer.java`: Visualization of identified bands
- `AssistModels.java`: Extended band models with confidence metrics

**Core Classes:**
- `AssistBandTool.java`: Main user interaction and band propagation logic
- Integration with `GelAnalysisTools.java` for tool calls

### Usage Examples

**Via Tool Calls:**
```json
// Enable BandAssist
{"tool": "enable_band_assist", "image_handle": "img_123"}

// Configure sensitivity
{"tool": "configure_band_assist", "min_snr": 2.0, "search_window_px": 25}

// Disable when done  
{"tool": "disable_band_assist", "image_handle": "img_123"}
```

**Via Natural Language:**
- "Enable BandAssist so I can click on bands"
- "Make BandAssist more sensitive for faint bands"
- "Turn off the clicking mode"

### Configuration Parameters
- `search_window_px`: Window around click for peak refinement (default: 20)
- `min_prominence`: Minimum peak prominence (default: 0.05)
- `min_snr`: Minimum signal-to-noise ratio (default: 3.0)
- `width_ratio_range`: Acceptable band width variation (default: 0.5-2.0)
- `rf_tolerance`: Rf matching tolerance (default: 0.02)

### Quality Indicators
- **Green bands**: High confidence (>70%) - strong peaks, good SNR
- **Orange bands**: Medium confidence (40-70%) - acceptable quality
- **Red bands**: Low confidence (<40%) - weak signals, may need verification
- **Magenta outline**: Seed band (user-clicked, 100% confidence)

## Security Notes

- **API Keys**: Never commit keys to repository, use environment variables
- **Session Logs**: Automatically sanitized but review before sharing
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