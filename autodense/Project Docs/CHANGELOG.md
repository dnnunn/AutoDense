# AutoDense Changelog

All notable changes to the AutoDense gel densitometry project are documented in this file.

## [0.1.0-SNAPSHOT] - 2025-08-20

### Project Initialization
- **2025-08-20**: Created AutoDense project structure (plugin, nl, packaging) based on Instructions.md
- Added scripts for llama.cpp universal build, packaging, and signing
- Added NL intent schema (draft-07) and documentation scaffolding
- Added plugin skeletons: `BandDetector`, `Calibrator`, `Normalizer`, `Deltas`, `ActionExecutor`, `GelContext` in `autodense/plugin/src/main/java/`
- Added `standards.json` to `autodense/plugin/src/main/resources/` with common protein ladders
- Added NL utilities: `NLClient`, `PortFinder`, `Waiter`, `JsonSchemas`, `Prompts`, `NLContext` in `autodense/nl/src/main/java/`

### Build System & Dependencies
- Configured SciJava Maven repository in root `autodense/pom.xml`
- Fixed plugin `pom.xml` to import ImageJ BOM and depend on `net.imagej:ij` and `org.scijava:scijava-common`
- Verified project compiles with `mvn -DskipTests install`

### Core Functionality Implementation
- Implemented auto-open of input image in `OpenAnalyzeCommand.run()` using `IJ.openImage`
- Implemented minimal lane detection (projection, smoothing, peak find, bounds) in `analysis/LaneDetector.findLanes(ImagePlus)`
- Wired mock NL plan execution in `OpenAnalyzeCommand.run()` invoking `ActionExecutor` with a `GelContext` instance

### Lane Detection Improvements

#### 12:41 BST - Enhanced Lane Detection Robustness
- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
- Crop out white margins to estimate gel region automatically
- Use inverted vertical projection with smoothing
- Apply peak selection with prominence and larger minimum spacing
- Expand peaks to lane bounds with padding
- **Reason**: Previous lanes were misaligned across bright background and margins

#### 12:52 BST - Improved Band Background Estimation
- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/BandDetector.java`
- Add median background from expanded side (or top/bottom) regions similar to `_BandPeakQuantification.ijm`
- Tighten band threshold slightly; integrate background into band area integral
- **Reason**: Improve robustness of band quantification under uneven illumination

#### 13:52 BST - Added Expected Lane Count Guidance
- **File**: `OpenAnalyzeCommand`
- New parameter `expectedLaneCount` (0 = auto)
- **File**: `LaneDetector`
- New overload `findLanes(ImagePlus,int)` and adaptive retry loop that relaxes thresholds/spacing toward the target count
- **Reason**: Allow user to steer detection on challenging gels; improves recall without hardcoding

#### 13:56 BST - Enforced Uniform Lane Width
- **File**: `LaneDetector`
- Constant `LANE_WIDTH_TOL_FRAC = 0.25` and `regularizeLaneWidths()` post-processing
- Clamps each lane width to within ±25% of the median, respecting neighbors and gel bounds
- **Reason**: Lanes are expected to be mostly equal; prevents overly narrow/wide lanes from threshold quirks

#### 14:00 BST - Added Constant-Spacing Lane Mode
- **File**: `OpenAnalyzeCommand`
- Parameter `constantLaneSpacing`
- **File**: `LaneDetector`
- Overload `findLanes(ImagePlus,int,boolean)`
- When enabled and count > 0, evenly spaces lane ROIs between detected gel edges and sets ROI width to 70% of spacing
- **Reason**: Gels of this type have fixed spacing; deterministically marks lanes and avoids under-detection

#### 14:11 BST - Improved Gel Bounds and Preprocessing
- **File**: `LaneDetector`
- Detect longest contiguous run of active columns to set gel `xLeft..xRight`, ignore short blebs
- Light contrast stretch and blur
- Constant-spacing ROI width reduced to 55% of spacing
- **Reason**: Avoid left-shift from spurious left blebs, improve contrast for weak/strong bands, and reduce lane overlap

#### 14:26 BST - Added User Controls for Lane Layout and Post-Detection Optimization
- **File**: `OpenAnalyzeCommand`
- New parameters: `laneWidthFraction`, `gridOffsetFraction`, `preprocessForDetection`, `postLowPct`, `postHighPct`, `postSmoothing` (none/light/medium)
- Band detection now runs on an optimized duplicate after lanes are fixed
- **File**: `LaneDetector`  
- New overload `findLanes(imp, expectedCount, constantSpacing, laneWidthFraction, gridOffsetFraction, preprocessForDetection)`
- Constant-spacing centers include optional grid offset
- **Reason**: Allow manual correction of slight ROI offset and control image optimization strength without affecting lane placement

### Interactive Features
- Added interactive band detection optimization with live preview overlay
- Real-time parameter adjustment with visual feedback
- Enhanced user interface for fine-tuning detection parameters

#### 2025-08-21 - Restored Arrow (Spinner) Controls for Lane Layout
- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/plugin/OpenAnalyzeCommand.java`
- Re-enabled SciJava command parameters with `style="spinner"` for:
  - `laneWidthFraction` (0.2–0.9)
  - `gridOffsetFraction` (-0.25–0.25)
- Keeps expected lane count/constant spacing as parameters and clarifies descriptions.
- Reason: Up/down arrows were nonfunctional with previous slider approach; spinners restore precise nudge control.

### AI Integration & Natural Language Processing - 2025-08-20 Evening

#### Complete LLM Integration Setup
- **Model**: Downloaded Gemma 3 4B instruction-tuned GGUF model (2.3GB) from Hugging Face
- **Vision**: Downloaded mmproj vision component (812MB) for multimodal gel image analysis
- **Server**: Integrated llama.cpp server with ARM64 Metal GPU acceleration for M3 Max
- **Dependencies**: Added org.json, jackson-databind, json-schema-validator to nl module

#### Natural Language Architecture
- **File**: `autodense/nl/src/main/java/com/betterdairy/autodense/nl/LlamaServer.java`
  - Complete server lifecycle management (startup, health checks, shutdown)
  - Auto-detection of app bundle vs development mode paths
  - Vision support with --mmproj parameter integration
  - Process management with proper error handling and timeouts
- **File**: `autodense/nl/src/main/java/com/betterdairy/autodense/nl/NLClient.java`
  - Multimodal chat completion API integration
  - Base64 image encoding for vision analysis
  - JSON schema validation for reliable command parsing
  - OpenAI-compatible chat format with system prompts
- **File**: `autodense/nl/src/main/java/com/betterdairy/autodense/nl/PortFinder.java`
  - Enhanced port discovery with fallback mechanisms

#### Build System Updates
- **File**: `autodense/packaging/scripts/build_llama_universal.sh`
  - Updated from deprecated Makefile to modern CMake build system
  - ARM64-optimized build configuration for Apple Silicon
  - Fixed cross-compilation issues for macOS universal binaries
- **Dependencies**: Added CMake installation and dependency management
- **Models**: Integrated model files into packaging/resources/models/ structure

#### Testing & Validation
- **Performance**: Achieved 619 tokens/sec prompt processing, 75 tokens/sec generation
- **Response Time**: ~650ms total for natural language to structured JSON conversion
- **Accuracy**: Successfully converts "detect protein bands in lanes 1-4" to valid JSON:
  ```json
  {
    "intent": "multi_action",
    "actions": [{"action": "detect_bands", "lanes": ["1", "2", "3", "4"]}]
  }
  ```
- **Schema Compliance**: Full validation against intent.schema.json specification
- **Offline Operation**: Complete local inference with no internet dependency

#### System Integration
- **Chat Template**: Gemma 3 format with proper system/user/assistant roles
- **Vision Pipeline**: Ready for gel image analysis with <start_of_image> tokens
- **Context Management**: Gel analysis state integration with NLContext
- **Error Handling**: Comprehensive error handling with detailed logging
- **Resource Management**: Proper server startup/shutdown lifecycle

### 2025-08-22 - Synthetic Gel Pipeline Test and JUnit Setup (06:22 BST)

- **File**: `autodense/plugin/src/test/java/com/betterdairy/autodense/analysis/SyntheticGelTest.java`
  - Added a synthetic SDS mini-gel generator and smoke test for pipeline
  - Verifies lane detection (~8 lanes), band detection (~3 per lane), and positive band area
  - Uses `LaneDetector.findLanes(imp)` and `BandDetector.findBands(imp, lane)` APIs, and `Models.Band`
- **Build**: `autodense/plugin/pom.xml`
  - Added JUnit 5 dependencies (`junit-jupiter-api`, `junit-jupiter-engine`) and configured `maven-surefire-plugin` 3.1.2
  - Ensures tests under `src/test/java` compile and run with JUnit Jupiter
  - Reason: Establish a reproducible test for core analysis functions using a deterministic synthetic gel

### 2025-08-22 - Tool Schema Validator Utility (06:26 BST)

- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/plugin/ToolSchemaValidator.java`
  - Added JSON helper for tool argument validation and normalization
  - Methods: `requireImageHandle(JSONObject)`, `requireArray(JSONObject,String)`, `clamp(JSONObject,String,double,double)`
  - Reason: Centralize schema checks and numeric clamping for NL tool inputs

### 2025-08-22 - Integrated ToolSchemaValidator in Tools (06:27 BST)

- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/tools/GelAnalysisTools.java`
  - Added validations at top of handlers: `preprocess`, `detectLanes`, `detectBands`, `adjustLanes`, `renderOverlayPng`, `quantifyBands`, `exportResults`, `detectColonies`, `countColoniesByColor`
  - `preprocess`: Clamps `radius_px` to [10, 400] before running "Subtract Background..."
  - Also require arrays for `steps`, `export_formats`, and `color_groups` where applicable
  - Reason: Strengthen input validation and prevent out-of-range parameters reaching ImageJ

### 2025-08-22 - Orchestrator image_handle auto-injection (06:30 BST)

- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/orchestrator/GeminiOrchestrator.java`
  - Before dispatch, injects `image_handle` into tool parameters if missing by using `currentImageHandle` or `SessionStore.getMostRecentImageHandle()`
  - Logs a session event `tool_call_warning` when injection occurs
  - Throws `IllegalArgumentException` if no active image is available
  - Reason: Ensure robust tool execution even when Gemini omits `image_handle` in parameters

### 2025-08-22 - Track last active image handle on tool results (06:31 BST)

- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/session/SessionStore.java`
  - Added `setLastActiveImageHandle(String)` to update the current image when a tool returns a handle
- **File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/orchestrator/GeminiOrchestrator.java`
  - After tool execution in `executeTool(...)`, if `result.image_handle` is present, updates `SessionStore` and `currentImageHandle`
  - Reason: Keep orchestrator session state in sync with latest tool-produced image handle