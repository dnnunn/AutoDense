# Next Steps: August 30, 2025 - HeadlessException Fix Required

> **Doc Meta**
> - **Purpose:** Priority tasks following configuration handoff fix and build organization success
> - **Scope:** ImageJ headless mode resolution, full pipeline completion, and validation testing
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-30

## 🚨 CRITICAL PRIORITY: Fix HeadlessException in Preprocessing

**Status:** Full pipeline fails at `ImagePreprocessor.java:197` when `IJ.run("Gaussian Blur...")` triggers GUI menu initialization.

### Immediate Tasks

#### 1. **Replace ImageJ1 IJ.run() with Headless-Compatible Operations** [CRITICAL]
- **File:** `autodense/plugin/src/main/java/com/betterdairy/autodense/util/ImagePreprocessor.java`
- **Line:** 197 - `IJ.run(working, "Gaussian Blur...", "sigma=" + config.gaussianSigma)`
- **Solution Options:**
  1. Use ImageJ2 Ops framework for Gaussian blur (context-aware, headless compatible)
  2. Use native Java imaging operations with convolution kernels
  3. Temporarily disable Gaussian blur by setting `gaussian_sigma: 0.0` in configs
- **Success Criteria:** Full pipeline completes without HeadlessException

#### 2. **Validate ImageJ Headless Initialization** [HIGH]
- **Current Issue:** ImageJ1 `IJ.init()` still attempts to create GUI menus despite headless properties
- **Investigation:** Compare how detect-only mode initializes ImageJ vs full pipeline mode
- **Fix:** Ensure proper ImageJ1 headless initialization before any `IJ.run()` calls
- **Test:** Verify headless mode works across all ImageJ operations in preprocessing

#### 3. **Test All Three Pipeline Types** [HIGH]
- **Scope:** SDS-PAGE, EtBr, Colony analysis
- **Command:** Use `./build.sh test-all` once HeadlessException fixed
- **Validation:** All three analysis types complete without GUI errors
- **Success:** Generate actual lane/band/colony detection results

## 📋 Secondary Priorities

#### 4. **Lane Detection Algorithm Investigation** [MEDIUM] 
- **Current Status:** Detection finds 5 peaks when 12 lanes exist (58% failure rate)
- **Dependencies:** Complete HeadlessException fix first
- **Investigation:** Debug why core detection only finds 5/12 lanes
- **Files:** `LaneDetector.java:findLanesInternal()` - biased target matching logic

#### 5. **Python-Java Bridge Validation** [MEDIUM]
- **Test:** Compare `make tune-sds-ij` results with direct CLI results after HeadlessException fix
- **Current:** Pipeline via Makefile reports `"lane_count": 0` while CLI finds lanes
- **Goal:** Consistent results between Makefile and CLI execution modes

#### 6. **Configuration System Cleanup** [LOW]
- **Remove:** Debug logging added during configuration debugging
- **Standardize:** Single configuration loading method across all pipeline modes
- **Validate:** Ensure Python config validation properly matches Java parameter expectations

## 🛠️ Recommended Approach

### Session Start Procedure
1. **Use unified build system:** `./build.sh build`
2. **Test current state:** `./build.sh test-sds` to confirm HeadlessException
3. **Fix ImageJ operations:** Replace `IJ.run()` with headless-compatible alternatives
4. **Validate fix:** Ensure full pipeline completes successfully

### HeadlessException Fix Strategy
```bash
# Option 1: Disable Gaussian blur temporarily
# Edit configs/sds.yaml: gaussian_sigma: 0.0

# Option 2: Replace with ImageJ2 Ops
# Research ImageJ2 context-aware Gaussian blur operations
# Replace IJ.run() calls with Ops framework calls

# Option 3: Use Java native imaging
# Implement Gaussian convolution without ImageJ dependencies
```

### Validation Steps
```bash
# Test progression:
./build.sh test-detect   # Should work (baseline)
./build.sh test-sds      # Target: complete without HeadlessException  
./build.sh test-etbr     # Target: work after SDS fix
./build.sh test-colony   # Target: work after SDS fix
```

## 📊 Success Metrics for Next Session

### Minimum Viable Success
- [ ] Full SDS pipeline completes without HeadlessException
- [ ] Preprocessing generates stage0/stage1 images AND detection results
- [ ] Configuration debug logging shows real parameters (not empty objects)

### Ideal Success  
- [ ] All three pipeline types (SDS, EtBr, Colony) work end-to-end
- [ ] Lane detection accuracy improvement (>5 peaks detected)
- [ ] Python-Java bridge produces consistent results with CLI mode
- [ ] Build system documented and reproducible for future sessions

## ⚠️ Blockers and Dependencies

1. **HeadlessException Fix Required First** - All pipeline testing depends on this
2. **ImageJ1/ImageJ2 Compatibility** - May need deeper understanding of dual initialization
3. **Gaussian Blur Replacement** - Need headless-compatible image processing operation

## 📚 Reference Information

**Working Components:**
- ✅ Configuration handoff: `loadConfigFileLegacy()` works correctly
- ✅ Build system: `./build.sh` handles all directory and environment issues
- ✅ Preprocessing: Creates proper normalized images before failing

**Current Tools:**
- **Build:** `./build.sh build` (unified, foolproof)
- **Test:** `./build.sh test-sds|test-etbr|test-colony|test-detect|test-all`
- **Debug:** Configuration debug logging shows parameter values

**Error Location:** `autodense/plugin/src/main/java/com/betterdairy/autodense/util/ImagePreprocessor.java:197`
**Test Images:** `samples/sds_gel.jpg`, `samples/etbr_gel.jpg`, `samples/colony_plate.jpg`

The HeadlessException fix is the critical path - all other AutoDense functionality depends on completing the full preprocessing → detection pipeline successfully.