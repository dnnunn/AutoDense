# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Always open lint and fix linter errors as they arise. Do not let them accumulate

## 🚨 BRANCH SAFETY PROTOCOL 🚨
**BEFORE ANY GIT OPERATION:**
1. Run `git branch --show-current` to confirm current branch
2. **NEVER** switch branches during active development without explicit user request
3. **ALWAYS** stay on the designated feature branch until work is complete
4. If you accidentally switch branches, immediately return to the correct branch

## Project Overview

AutoDense is a comprehensive laboratory image analysis application built as a standalone Mac-native Fiji/ImageJ2 app with AI-powered control via Google Gemini. The system uses a **handle-based architecture** where Gemini acts as the planner and ImageJ/Fiji executes the actual image analysis operations.

**Key Features:**

- 🧬 **Gel Densitometry**: Protein quantification with standards, %CV calculation, LOQ/LLOQ determination
- 🦠 **Colony Analysis**: Counting, classification, time-series growth tracking
- 🔬 **Advanced Statistics**: MW-aware lane comparison, Holm-Bonferroni multiple testing correction
- 🧪 **PCR Analysis**: Semi-quantitative PCR with housekeeping normalization and ΔΔI calculation
- 🎵 **Voice Input**: Hold-to-record voice commands for hands-free operation
- 📄 **Document Upload**: Integration with CSV/Excel/TXT files containing experimental metadata
- 🎯 **BandAssist**: User-assisted band identification across gel lanes

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
     - `quantify_bands`: Measure band intensities with PCR normalization support
     - `calibrate_standard_curve`: **NEW** Protein quantification with %CV and LOQ/LLOQ
     - `compare_lanes`: **NEW** MW-aware statistical comparison with multiple testing
     - `export_volcano_plot`: **NEW** Generate volcano plot PNG for lane comparisons
     - `enable_band_assist`: Enable user-assisted band identification
     - `disable_band_assist`: Disable user-assisted mode
     - `configure_band_assist`: Configure BandAssist parameters
     - `start_timeseries_analysis`: **NEW** Initialize colony growth tracking
     - `add_timepoint`: **NEW** Add time point to growth analysis
     - `align_plate_images`: **NEW** Align plates for time-series
     - `analyze_xgal_blueness`: **NEW** X-gal colony classification
     - `export_timeseries_data`: **NEW** Export growth data
     - `export_results`: Save CSV/JSON/PNG with enhanced metadata
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

### Gel Analysis

- **LaneDetector**: Finds gel lanes via vertical projection analysis
- **BandDetector**: Identifies protein bands within lanes using 1D profile analysis
- **FijiBandDetector**: Enhanced band detection with Fiji algorithms
- **BandQuantification**: Quantifies band intensities with background subtraction
- **Calibrator**: Fits molecular weight calibration curves + **NEW StandardCurveFitter**
- **LaneComparator**: **NEW** MW-aware statistical comparison with multiple testing correction
- **Normalizer**: Applies various normalization strategies including **NEW PCR housekeeping**
- **ImagePreprocessor**: Handles saturation, contrast, filtering
- **WorkflowManager**: Manages analysis pipelines

### Colony Analysis

- **TimeSeriesColonyTracker**: **NEW** Individual colony tracking across time points
- **PlateAlignment**: **NEW** Feature-based and orientation mark image registration
- **XGalBluenessAnalyzer**: **NEW** X-gal colony classification and blueness quantification

### User Interface

- **GelUI**: Enhanced with voice input, document upload, and console management
- **AssistBandTool**: **NEW** User-assisted band identification system
- **OverlayRenderer**: Enhanced with volcano plot visualization

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

| Removed            | Current                     |
| ------------------ | --------------------------- |
| Local LLM server   | Gemini Cloud API only       |
| NL module          | Plugin module only          |
| Pattern matching   | Vision analysis + reasoning |
| Dialog-based chat  | Embedded chat interface     |
| llama.cpp binaries | No local inference          |

## API Configuration

### 🔑 Gemini API Key Setup (CRITICAL)

**AutoDense REQUIRES a valid Gemini API key to function.** The application will fail with clear error messages if an invalid key is provided.

#### Step 1: Get Your API Key
1. Visit: https://makersuite.google.com/app/apikey
2. Sign in with your Google account
3. Create a new API key
4. Copy the key (starts with "AIza...")

#### Step 2: Set the API Key
**Option A - Environment Variable (Recommended):**
```bash
export GEMINI_API_KEY=your_actual_key_here
mvn -f autodense/plugin/pom.xml exec:java -Dexec.mainClass=com.betterdairy.autodense.plugin.EnhancedImageJLauncher
```

**Option B - System Property:**
```bash
mvn -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.plugin.EnhancedImageJLauncher \
  -DGEMINI_API_KEY=your_actual_key_here
```

**Option C - Configuration File:**
Create `api-config.properties` in the root directory:
```properties
GEMINI_API_KEY=your_actual_key_here
```

#### Common API Key Issues

❌ **NEVER use these placeholder values:**
- `placeholder`
- `your_api_key_here` 
- `your_key`
- `test`
- `demo`

✅ **Valid keys look like:** `AIzaSyD...` (39 characters total)

#### Error Prevention
The application now validates API keys at startup and will:
- Show clear warnings in the ImageJ log for invalid keys
- Prevent GeminiOrchestrator initialization with placeholder keys  
- Display helpful setup instructions when keys are missing/invalid

#### Troubleshooting
If you see "API key not valid" errors:
1. Check your key is correctly set: `echo $GEMINI_API_KEY`
2. Verify it starts with "AIza" and is ~39 characters
3. Test it works at: https://makersuite.google.com/app/apikey
4. Restart AutoDense after setting the key

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

## Recent Major Enhancements

### Protein Quantification with Standards (NEW)

**StandardCurveFitter** provides comprehensive protein quantification:

- Coefficient of Variation (%CV) calculation for quality assessment
- LOQ (Limit of Quantification) and LLOQ determination at 20%/30% CV thresholds
- Residual Standard Error (RSE) for statistical validation
- Enhanced CSV export with quality flags and LOQ/LLOQ indicators

### MW-Aware Lane Comparison (NEW)

**LaneComparator** enables rigorous statistical analysis:

- Peak grouping by molecular weight bins to prevent spurious associations
- Holm-Bonferroni multiple testing correction (default, less conservative)
- Volcano plot visualization (log₂ fold change vs -log₁₀ p-value)
- Publication-quality PNG export with color-coded significance
- Comprehensive statistical reporting with FDR control

### Semi-Quantitative PCR Analysis (NEW)

**PCR Housekeeping Normalization** for relative quantification:

- `housekeeping_lane_idx` and `housekeeping_band_idx` parameters
- ΔΔI (delta-delta intensity) calculation: log₂(sample/housekeeping)
- Relative copy number estimation using 2^(ΔΔI)
- Quality flagging: VERY_LOW, LOW, NORMAL, HIGH, VERY_HIGH
- Enhanced CSV export with both raw and normalized values

### Frontend Enhancements (NEW)

**Modern user interface with AI integration:**

- 🎵 **Voice Input**: Hold-to-record voice commands with speech-to-text framework
- 📄 **Document Upload**: CSV/Excel/TXT integration for experimental metadata
- 🖥️ **Console Management**: Hidden by default with menu toggle
- 📋 **Enhanced Menus**: Comprehensive help system for new features
- 🤖 **Context Integration**: Uploaded documents enhance Gemini AI understanding

## Session Logging System

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

## Time-Series Colony Growth Analysis (NEW)

### Overview

AutoDense now supports comprehensive time-series analysis for colony growth studies, including automatic plate alignment, morphology tracking, and X-gal blueness quantification over multiple time points.

### Core Components

**PlateAlignment.java:**

- Feature-based and orientation mark-based image registration
- Handles rotation, skewing, and translation correction
- Supports both automated feature detection and manual orientation marks

**TimeSeriesColonyTracker.java:**

- Individual colony tracking across multiple time points
- Growth rate calculations and morphology change analysis
- Colony matching algorithms with configurable thresholds

**XGalBluenessAnalyzer.java:**

- Specialized X-gal blueness quantification using RGB/HSV analysis
- Multi-colony batch analysis with confidence scoring
- Time-series blueness comparison and trend analysis

### New Tool Functions

**start_timeseries_analysis:**

- Initialize colony tracking for growth analysis
- Configure tracking options and morphology measurements
- Set up reference image as first time point

**add_timepoint:**

- Add new time point with automatic plate alignment
- Track colony growth and morphology changes
- Update growth rate calculations

**align_plate_images:**

- Standalone plate alignment tool
- Supports feature matching or orientation mark methods
- Returns alignment confidence and transformation matrix

**analyze_xgal_blueness:**

- Quantify X-gal blueness for colonies
- Classify colonies as white/light_blue/medium_blue/deep_blue
- Calculate transformation efficiency

**export_timeseries_data:**

- Export growth data as JSON or CSV
- Include growth rates, morphology changes, blueness trends
- Generate comprehensive statistical summaries

### Workflow Integration

**Colony Growth Analysis Workflow:**

- Time-series analysis with plate matching and morphology tracking
- Supports up to 20 time points with automatic colony matching
- X-gal blueness analysis as user-configurable option
- Growth rate calculations and statistical analysis
- Comprehensive data export capabilities

### Usage Examples

**Natural Language:**

- "Track colony growth over multiple time points"
- "Align plates using orientation marks"
- "Measure X-gal blueness development over time"
- "Calculate growth rates for each colony"
- "Correct for plate rotation and skewing"
- "Export time-series growth data"

**Tool Call Sequence:**

```json
// Initialize tracking
{"tool": "start_timeseries_analysis", "reference_image_handle": "img_t0", "measure_morphology": true, "xgal_analysis": true}

// Add time points
{"tool": "add_timepoint", "tracking_handle": "tracking_123", "image_handle": "img_t1", "perform_alignment": true}
{"tool": "add_timepoint", "tracking_handle": "tracking_123", "image_handle": "img_t2", "perform_alignment": true}

// Export results
{"tool": "export_timeseries_data", "tracking_handle": "tracking_123", "export_formats": ["csv", "json"]}
```

### Configuration Parameters

**Tracking Options:**

- `max_matching_distance`: Maximum pixel distance for colony matching (default: 10.0)
- `size_change_threshold`: Maximum diameter ratio between time points (default: 2.0)
- `morphology_threshold`: Maximum morphology change tolerance (default: 0.3)
- `colony_matching_threshold`: Confidence threshold for colony matches (default: 0.8)

**Alignment Options:**

- `alignment_method`: "feature_matching" or "orientation_marks"
- `tolerance_px`: Alignment tolerance in pixels (default: 5.0)
- `allow_rotation`: Enable rotation correction (default: true)
- `allow_skewing`: Enable skew correction (default: true)

**X-gal Analysis Options:**

- `analysis_radius`: Radius for colony color analysis (default: 8)
- `use_rgb_analysis`: Use RGB color space analysis (default: true)
- `normalize_lighting`: Correct for lighting variations (default: true)

### Data Export

**CSV Format:**

```csv
Track_ID,Time_Point,Colony_ID,Center_X,Center_Y,Area_mm2,Diameter_mm,Circularity,Solidity,Aspect_Ratio,Texture_Variance,Blueness
track_1,0,colony_50_60,50.0,60.0,2.341,1.72,0.89,0.94,1.12,15.3,0.23
track_1,1,colony_52_61,52.0,61.0,3.127,1.99,0.87,0.92,1.15,18.7,0.35
```

**JSON Format:**

```json
{
  "tracks": [
    {
      "track_id": "track_1",
      "statistics": {
        "total_area_growth_mm2": 0.786,
        "total_diameter_growth_mm": 0.27,
        "max_blueness": 0.35,
        "time_points_count": 2
      },
      "time_points": [...]
    }
  ],
  "summary": {
    "total_tracks": 45,
    "growing_tracks": 38,
    "avg_final_area_mm2": 2.87
  }
}
```

### Performance Considerations

- **Memory Usage**: Stores full ImagePlus objects for each time point
- **Processing Time**: Alignment and tracking scale with colony count
- **Storage**: JSON exports can be large for long time series
- **Accuracy**: Alignment quality affects tracking accuracy

### Testing

```bash
# Run time-series analysis test
mvn -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.analysis.TimeSeriesTest
```

## 🔴 ARCHITECTURAL ENFORCEMENT DOCUMENT - CRITICAL REMINDERS 🔴

### MANDATORY FIRST ACTION

🚨 **ALWAYS READ CLAUDE.md BEFORE STARTING ANY WORK** 🚨

- This is not optional
- This is not a suggestion
- This is a REQUIREMENT that must be followed EVERY time
- Failure to read CLAUDE.md first is unacceptable

### CORE ARCHITECTURE - NEVER FORGET

The AutoDense system uses a **HANDLE-BASED ARCHITECTURE** where:

#### Gemini's ONLY Role = Tool Orchestrator/Planner

- ✅ **Gemini ONLY**: Emits structured tool calls in JSON format
- ✅ **Gemini ONLY**: Plans multi-step workflows
- ✅ **Gemini ONLY**: Parses user commands into tool parameters
- ❌ **Gemini NEVER**: Processes pixels directly
- ❌ **Gemini NEVER**: Performs image analysis itself
- ❌ **Gemini NEVER**: Acts as a vision AI describing images
- ❌ **Gemini NEVER**: Bypasses the tool system

#### ImageJ's ONLY Role = Executor

- ✅ **ImageJ ONLY**: Performs all actual image operations
- ✅ **ImageJ ONLY**: Maintains all image state
- ✅ **ImageJ ONLY**: Executes tool calls from Gemini
- ❌ **ImageJ NEVER**: Makes planning decisions

#### Handle-Based System = References Only

- ✅ **Handles**: Images referenced as `img_abc123`, overlays as `ov_def456`
- ✅ **SessionStore**: Maintains all state using handles
- ✅ **No Pixels**: Never pass raw image data to LLM
- ❌ **No Direct Access**: Tools never access images without handles

### PROPER WORKFLOW - ALWAYS FOLLOW

```
User Command → GeminiOrchestrator → Gemini API → Tool JSON → ImageJ Execution → Handle Results
```

### WRONG ARCHITECTURES - NEVER IMPLEMENT

- ❌ Direct Gemini vision analysis instead of tool orchestration
- ❌ GelUI calling GeminiApiClient directly (bypasses orchestrator)
- ❌ Passing pixels to Gemini for image processing
- ❌ Local LLM integration (removed from architecture)
- ❌ Pattern matching instead of structured tool calls

### KEY FILES TO UNDERSTAND

- **GeminiOrchestrator.java**: Central coordinator (lines 21-767)
- **CanonicalTools.java**: Streamlined tool surface (8 core actions)
- **SessionStore.java**: Handle-based state management
- **GelAnalysisTools.java**: Detailed ImageJ tool implementations

### IMPLEMENTATION RULES

1. **GelUI must use GeminiOrchestrator** - Never call GeminiApiClient directly
2. **All image references are handles** - No pixel data in API calls
3. **Sequential tool execution** - One tool call at a time
4. **SessionStore maintains state** - Not in LLM memory
5. **Canonical actions preferred** - Use 8 core actions when possible

### ERROR PATTERNS TO AVOID

- Using deprecated `analyzeGel()` method instead of `processCommand()`
- Creating vision-based Gemini responses instead of tool orchestration
- Bypassing handle system with direct image manipulation
- Forgetting to read this document before starting work

### SUCCESS CRITERIA

- ✅ User commands result in structured tool calls
- ✅ Gemini acts purely as planner, never as vision processor
- ✅ All image operations happen in ImageJ with handles
- ✅ SessionStore maintains complete analysis state
- ✅ Workflow: User → Orchestrator → Tool Calls → ImageJ → Results

**🔥 THIS ARCHITECTURE IS NON-NEGOTIABLE AND MUST BE FOLLOWED EXACTLY 🔥**

### 🚨 **CRITICAL: SYSTEM PROMPT ENFORCEMENT** 🚨

The GeminiOrchestrator system prompt MUST enforce the handle-based architecture:

**❌ NEVER allow Gemini to:**

- Analyze images directly ("I see 10 lanes")
- Count lanes, bands, or colonies itself
- Provide vision descriptions ("The gel appears to...")
- Act as an image analysis AI

**✅ ALWAYS force Gemini to:**

- Emit structured JSON tool calls only
- Extract parameters from user commands
- Let ImageJ tools do ALL image processing
- Act as a pure tool orchestrator/planner

**System Prompt Must Include:**

```
🔥 CRITICAL: You are a TOOL ORCHESTRATOR, NOT an image analysis AI.
❌ NEVER analyze images directly or describe what you see
✅ ALWAYS respond with structured JSON tool calls
Your job: translate user commands → tool calls
ImageJ's job: process pixels and return measurements
```

**If Gemini starts providing vision analysis instead of tool orchestration, the system prompt has been corrupted and must be fixed immediately.**

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
- 
- Always open lint and fix linter errors as they arise. Do not let them accumulate
