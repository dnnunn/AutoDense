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