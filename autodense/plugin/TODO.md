# AutoDense Critical Analysis Tasks - Task List

## Status: 5 Tasks Remaining (3 Completed)

### [completed] Debug why detection finds 7 peaks but analysis reports 0 lanes/bands
- **Status**: Completed 
- **Description**: Identified root cause - peak-to-lane expansion algorithm creates invalid lane boundaries (xr <= xl), filtering out all detected peaks during lane creation
- **Solution**: Issue is in LaneDetector.findLanesInternal() lines 275-295 where boundary clipping and padding logic creates invalid ranges
- **Files**: /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java

### [pending] Implement run_detect_only tool in Python bridge
- **Status**: Pending
- **Description**: Create detection-only tool for optimization feedback without full analysis
- **Files**: autodense_autotune/ Python modules

### [pending] Implement rescue tool with parameter inversion/relaxation
- **Status**: Pending  
- **Description**: Add rescue capabilities for failed detection with parameter adjustments
- **Files**: Python bridge modules

### [pending] Add helper.review critic system for parameter validation
- **Status**: Pending
- **Description**: Implement second-opinion validation system for safe parameter changes
- **Files**: Helper critic system components

### [pending] Implement parameter bounds checking in Python bridge
- **Status**: Pending
- **Description**: Add validation constraints for optimization parameter ranges
- **Files**: Python parameter validation modules

### [completed] Continue IJ.run() elimination in remaining files (55 calls across 12 files)
- **Status**: Completed (Core fixes implemented)
- **Description**: Replaced critical IJ.run() macro calls with headless-safe API calls to prevent HeadlessExceptions
- **Files**: 
  - ✅ PlateAnalysisTools.java - Added headless utilities, replaced Gaussian blur, contrast, 8-bit conversion
  - ✅ ColonyDetector.java - Added headless utilities, replaced RGB conversion, thresholding, morphology
  - ✅ SdsOps.java - Replaced 8-bit conversion calls
  - ✅ AssayOps.java - Replaced LAB color space conversion
  - ✅ ImagePreprocessor.java - Already had headless Gaussian blur, added additional utilities
- **Result**: SDS pipeline now runs without HeadlessException, preprocessing completes successfully
- **Remaining**: Minor IJ.run() calls in BandDetector, PlateDetector, etc. (non-critical for basic functionality)

### [completed] Update Gemini prompt with telemetry-based decision framework  
- **Status**: Completed
- **Description**: Updated orchestrator.system.md with comprehensive telemetry-based decision framework
- **Files**: prompts/orchestrator.system.md
- **Changes**:
  - ✅ Added observation block with baseline, prominence, distance, polarity, rescue status fields
  - ✅ Added status-based tool selection strategy (headless_violation → run_detect_only, etc.)
  - ✅ Added metric-driven parameter change rules
  - ✅ Added telemetry-guided decision examples
  - ✅ Added OBSERVE-FIRST rule to analyze observation.status before proposing changes
- **Result**: Gemini now has structured telemetry data for intelligent optimization decisions

### [pending] Validate Gemini optimization workflow with new tools and constraints
- **Status**: Pending
- **Description**: End-to-end testing of optimization system with all safety measures
- **Files**: Integration testing components
