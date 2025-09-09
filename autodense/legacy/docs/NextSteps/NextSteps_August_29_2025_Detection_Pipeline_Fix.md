# Next Steps: August 29, 2025 - Detection Pipeline Fix Implementation

> **Doc Meta**  
> - **Purpose:** Priority tasks and implementation roadmap following detection pipeline breakthrough
> - **Scope:** Immediate fixes, testing strategy, and AI optimization integration plan
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-29

## 🚨 Immediate Priority Tasks (Next Session)

### 1. Apply Detection Fix to Main Pipeline
**Priority**: Critical
**File**: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/LaneDetector.java`
**Location**: Around lines 1376-1382 (morphological opening section)

**Action Required**:
```java
// REPLACE aggressive 32-pixel baseline removal with gentler approach
// Current problem code (destroying signal):
int minDistPx = Math.max(6, (int) Math.round(w * 0.04));  // 32px too large
double[] base = movingMin(movingMax(profX, minDistPx), minDistPx);
for (int i = 0; i < profX.length; i++) {
    profX[i] = Math.max(0, profX[i] - base[i]);  // Flattens to zeros
}

// SOLUTION options:
// Option A: Reduce window size to 3-15 pixels max
// Option B: Skip baseline removal entirely for now  
// Option C: Implement adaptive window sizing based on lane spacing
```

**Validation**: Test with detect-only mode to confirm 15+ lanes still detected

### 2. Comprehensive Sample Testing
**Priority**: High
**Scope**: All 9 images in `/samples/` directory

**Testing Strategy**:
```bash
# Test each image type with fixed detector
samples=(
  "sds_gel.jpg" "synthetic_sds_gel.jpg" "synthetic_sds_gel2.png"
  "etbr_gel.jpg" "synthetic_etbr_gel.jpg" "synthetic_etbr_gel2.png"  
  "colony_plate.jpg" "synthetic_colony_plate.jpg" "synthetic_colony_plate2.png"
)

for img in "${samples[@]}"; do
  java [...] AutotuneAnalysisCLI --detect-only --preprocessed "samples/$img" [...]
done
```

**Expected Outcomes**:
- **SDS-PAGE**: 10-20 lanes typical
- **EtBr gels**: 5-15 lanes typical  
- **Colony plates**: 20-200+ colonies typical

### 3. Performance Baseline Establishment
**Priority**: High
**Purpose**: Create optimization target metrics

**Metrics to Capture**:
- Detection counts (lanes/bands/colonies)
- Processing time per image
- Memory usage patterns
- False positive estimates
- Confidence scores where available

**Implementation**: Enhance RunReport schema with performance data

## 🔧 Technical Implementation Tasks

### 4. Config Updates 
**Files**: `configs/sds.yaml`, `configs/colony.yaml`, `configs/etbr.yaml`
**Purpose**: Update parameters to work with fixed detector

**Parameters to Adjust**:
```yaml
# Reduce aggressive preprocessing that might interfere with detection
background_removal_radius: 5.0    # was 0.0 (disabled), try gentle removal
baseline_window_fraction: 0.01    # was 0.04 (too large), much smaller window
prominence_threshold: 0.02        # lenient initial setting
minimum_distance_fraction: 0.02   # appropriate spacing
```

### 5. Integration Testing
**Purpose**: Ensure fix works in full pipeline, not just detect-only mode
**Approach**: Test complete workflows (preprocessing → detection → analysis)

```bash
# Test full workflow with fixed detector
java [...] AutotuneAnalysisCLI sds_page samples/sds_gel.jpg output/full_test configs/sds.yaml
# Verify: stage1_norm.png generated, overlay shows detected lanes, run_report.json has >0 counts
```

## 🤖 AI Optimization Preparation

### 6. Parameter Grid Definition
**Priority**: Medium
**Purpose**: Define safe exploration bounds for optimization

**Critical Parameters** (based on today's findings):
```yaml
baseline_window_size: [3, 6, 9, 12, 15]      # NEVER exceed 20-25 pixels
prominence_threshold: [0.01, 0.02, 0.05, 0.10, 0.15]
minimum_distance: [0.015, 0.02, 0.03, 0.04, 0.05]  # fraction of width
gaussian_sigma: [1.0, 1.5, 2.0, 2.5, 3.0]
percentile_range: [[1,99], [2,98], [5,95]]    # p5/p95 clipping bounds
```

**Safety Constraints**:
- Baseline window MUST be < 0.025 * image_width (avoid signal destruction)
- Prominence threshold MUST be > 0.005 (avoid noise peaks)
- Always test with detect-only mode before full pipeline

### 7. Challenge Pack Updates
**Files**: `challenge_packs/sds_page_v1/spec.yaml`, `challenge_packs/colony_count_v1/spec.yaml`, `challenge_packs/etbr_v1/spec.yaml`

**Updates Needed**:
- Include working baseline parameters
- Define optimization objectives (precision vs recall balance)
- Add safety constraints based on detection fix learnings
- Update success criteria (>0 detections minimum)

## 🔄 Autotune Integration Strategy

### Phase 1: Baseline Metrics (Sessions 1-2)
1. **Performance measurement** across all sample types
2. **Ground truth establishment** (manual annotation of expected counts)
3. **Metric standardization** (consistent RunReport schema)

### Phase 2: Parameter Exploration (Sessions 3-4) 
1. **Grid search validation** within safe bounds
2. **Performance impact analysis** for each parameter
3. **Optimization objective definition** (detection quality vs processing time)

### Phase 3: AI Integration (Sessions 5+)
1. **Gemini optimizer connection** to working detector
2. **Feedback loop implementation** (Observe → Propose → Patch → Measure → Gate)
3. **Helper critic validation** to prevent regression

### Phase 4: Production Ready (Sessions 8+)
1. **Dual-mode operation** (legacy + optimization)
2. **User interface integration** 
3. **Performance monitoring** and quality gates

## ⚠️ Risk Mitigation

### Critical Dependencies
- **Main detector fix** must work before any optimization attempts
- **Sample testing** must validate fix universality across image types
- **Performance baseline** must be established before optimization begins

### Fallback Plans  
- **Regression detection**: Always compare against baseline performance
- **Safe parameter bounds**: Never exceed limits that broke detection originally
- **Rollback capability**: Maintain working detector version

### Quality Gates
- **Never break working detection**: Optimization must improve on 15-lane baseline
- **Preserve signal integrity**: Monitor profile statistics before/after processing
- **Performance monitoring**: Track detection quality trends over time

## 📋 Success Criteria

### Next Session Goals
- [ ] Main `LaneDetector.java` updated with baseline removal fix
- [ ] All 9 sample images test successfully (>0 detections each)  
- [ ] Full pipeline integration validated (not just detect-only mode)
- [ ] Performance baseline metrics captured for optimization

### Medium-term Goals (2-3 sessions)
- [ ] Parameter grid validated with safe bounds
- [ ] Challenge packs updated with working parameters
- [ ] Autotune system connected to fixed detector
- [ ] AI optimization feedback loop functional

This breakthrough session has established the foundation - now we execute systematic implementation to reach full AI optimization capability.