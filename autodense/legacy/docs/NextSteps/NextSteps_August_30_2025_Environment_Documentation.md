# Next Steps: August 30, 2025 - Lane Detection Algorithm Fix Required

> **Doc Meta**
> - **Purpose:** Priority tasks following environment documentation and lane detection algorithm diagnosis
> - **Scope:** Core algorithm fixes, detection accuracy improvements, and system validation
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-30

## 🚨 CRITICAL PRIORITY: Fix Lane Detection Algorithm

**Status:** Core detection algorithm only finds 5 peaks when 12 lanes exist (58% failure rate)

### Immediate Tasks

#### 1. **Debug Core Peak Detection Logic** [CRITICAL]
- **File:** `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
- **Issue:** `findLanesInternal()` reports "robust detection found 5 peaks" but should find 12
- **Investigation needed:**
  - Check baseline removal effectiveness on `stage1_norm.png`
  - Analyze peak detection parameters (minDist=144, minProm=0.060)
  - Verify projection calculation creates correct profile
  - Test different ROI coordinates and sizes

#### 2. **Remove Biased Target Matching** [HIGH]
- **Location:** Lines with `Math.abs(target - ls.size())`
- **Problem:** Algorithm adjusts thresholds to match expected count instead of pure detection
- **Fix:** Implement pure signal-based detection without bias toward expected count
- **Test:** Ensure results consistent regardless of expectedLanes parameter

#### 3. **Validate Detection on Known Images** [HIGH]  
- **Approach:** Test detection on gel images with manually counted lanes
- **Images:** Use `samples/sds_gel.jpg` (12 lanes), `samples/etbr_gel.jpg` 
- **Success criteria:** Detection count matches manual count ±1 lane
- **Tools:** Use detect-only CLI mode with `expectedLanes=0` for unbiased testing

## 📋 Secondary Priorities

#### 4. **Fix Python-Java Bridge** [MEDIUM]
- **Current status:** Unknown - blocked by core detection issues
- **Dependencies:** Complete lane detection fix first
- **Investigation:** Why `make tune-sds-ij` reports `"lane_count": 0` when CLI finds 8
- **Files:** `autodense_autotune/java_bridge.py`, configuration handoff

#### 5. **Add Separate Bands Baseline Config** [LOW]  
- **Requirement:** From auditor specifications
- **Purpose:** Different baseline parameters for vertical band detection within lanes
- **Files:** Update `configs/*.yaml` with `bands.baseline` section
- **Dependencies:** Lane detection must work first

#### 6. **Complete Implementation Testing** [LOW]
- **Scope:** Test across all 9 sample images once detection works
- **Images:** 3 SDS, 3 EtBr, 3 colony samples
- **Success:** Consistent lane/colony detection across image types

## 🛠️ Recommended Approach

### Session Start Procedure
1. **Follow documented environment setup** (see `StructureDocs/Structure_August_30_2025_Environment_Fix.md`)
2. **Activate Python venv:** `source .venv/bin/activate`
3. **Build from correct directory:** `cd autodense/plugin && mvn compile -q`
4. **Use documented CLI command** for testing

### Debugging Strategy
```bash
# Test current detection state
java -cp "autodense/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar:$(cat autodense/plugin/target/runtime-classpath.txt)" \
  com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
  --detect-only --preprocessed output/sds_test/stage1_norm.png \
  --roi 50,100,800,400 --config-yaml configs/sds.yaml \
  --outdir output/debug_detection

# Look for: "robust detection found X peaks" vs actual lane count
```

### Investigation Areas
1. **Profile generation:** Is horizontal projection creating clear lane peaks?
2. **Baseline effectiveness:** After baseline removal, are lane valleys distinct?
3. **Peak detection parameters:** Are minDist/minProm too restrictive?
4. **ROI boundaries:** Is the 50,100,800,400 ROI capturing all lanes?

## 📝 Documentation Updates Needed

#### Update Structure Documentation
- **When:** After lane detection algorithm fixed
- **Content:** Add successful detection testing procedures
- **Location:** `StructureDocs/` - update current or create new

#### Update Session Logs
- **Track:** Detection accuracy improvements  
- **Metrics:** Peak count vs actual lane count across test images
- **Format:** Consistent testing results for comparison

## 🎯 Success Criteria for Next Session

### Minimum Viable Success
- [ ] Lane detection finds 10+ peaks on `sds_gel.jpg` (currently finds 5)
- [ ] Results consistent across different expectedLanes values
- [ ] Detection works on multiple gel images

### Ideal Success  
- [ ] Lane detection accuracy: 12 lanes detected on 12-lane gel
- [ ] Pure signal-based detection (no bias toward expected count)
- [ ] Python-Java bridge produces same results as CLI mode
- [ ] Complete SDS and EtBr gel analysis working end-to-end

## ⚠️ Blockers and Dependencies

1. **Core Detection Fix Required First** - All other work depends on functional lane detection
2. **Test Image Analysis** - May need manual lane counting for validation
3. **Parameter Tuning** - Detection parameters may need adjustment for different gel types

## 📚 Reference Documents

- **Environment Setup:** `StructureDocs/Structure_August_30_2025_Environment_Fix.md`
- **Session Context:** `SessionSummaries/Session_summary_August_30_2025_Environment_Documentation.md`  
- **Algorithm Location:** `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
- **Test Images:** `samples/sds_gel.jpg`, `samples/etbr_gel.jpg`

The core lane detection algorithm fix is the critical path - all other functionality depends on accurate lane identification.