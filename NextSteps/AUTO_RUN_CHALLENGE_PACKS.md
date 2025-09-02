# Auto-Run Challenge Packs: Immediate Analysis on Drop

> **Doc Meta**
> - **Purpose:** Basic challenge packs for zero-friction auto-analysis when images are dropped
> - **Scope:** gel_basic_v1 and colony_basic_v1 specs with fail-gate triggers for optimization
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 🎯 Purpose

These auto-run challenge packs execute immediately when images are dropped into AutoDense, providing instant analysis results with overlays. They implement the "Drop" part of the "Drop, See, Ask" philosophy using our established Phase I-III foundation.

**Key Features**:
- **Zero user input required**
- **Safe default parameters** from our validated configurations
- **Automatic fail-gate detection** to trigger Phase IV optimization
- **Immediate overlay generation** for visual feedback
- **Comprehensive telemetry** for downstream workflows

---

## 📦 Auto-Run Pack 1: Basic Gel Analysis

### **File: `challenge_packs/gel_basic_v1/spec.yaml`**

```yaml
# Basic Gel Analysis - Auto-run on image drop
meta:
  name: "gel_basic_v1"
  description: "Immediate gel analysis with safe defaults - no user input required"
  version: "1.0"
  trigger: "auto_on_drop"
  last_verified: "2025-09-02"

# Auto-detect assay type
assay_detection:
  method: "auto"                    # SDS-PAGE vs EtBr heuristic
  polarity_check: true              # Dark bands vs bright bands
  channel_analysis: true            # RGB vs grayscale patterns

# Phase III: Unified detection with parameter independence
detect:
  lanes:
    min_peak_distance_frac: 0.035   # Safe default spacing
    prominence_frac: 0.06           # Balanced sensitivity  
  baseline:
    method: "percentile"
    window_frac: 0.02               # Conservative 2% window
    quantile: 0.10                  # 10th percentile baseline
    clamp_min_px: 3
    clamp_max_px: 12

# Phase III: Completely independent band parameters  
bands:
  baseline:
    method: "percentile"            # Independent from lane baseline
    window_frac: 0.015              # 1.5% window for bands
    quantile: 0.08                  # 8th percentile for sensitivity
    clamp_min_px: 2                 # Tighter bounds for bands
    clamp_max_px: 8
  min_peak_distance_px: 14          # Configurable parameter (Phase III fix)
  prominence_frac: 0.02             # Lower for gentle detection

# Ladder detection with top-K candidate selection
ladder:
  method: "top_k_candidates"
  candidates_to_try: 3              # Try 3 best lane candidates
  min_r2_threshold: 0.85            # Minimum R² to accept ladder
  standard_markers: [10,15,20,25,30,37,50,75,100,150,250] # kDa

# Phase IV: Auto-trigger optimization on fail-gates
optimization:
  auto_trigger_conditions:
    lanes_raw: 0                    # No lanes detected
    bands_raw: 0                    # No bands detected  
    baseline_post_med: "<0.01"      # Baseline too aggressive
    coverage_total: "<0.2"          # Poor coverage
    ladder_r2: "<0.7"               # Poor MW calibration
  
  mode: "telemetry_first"           # Try telemetry optimization first
  fallback_to_vision: true         # Use Phase IV vision-assist if needed
  max_attempts: 3                   # Bounded optimization attempts

# Immediate outputs (no user input required)
outputs:
  overlay: "overlay.png"            # Always generated for UI
  summary: "run_report.json"        # Phase II telemetry
  quick_stats: "quick_summary.json" # UI-friendly summary
  
# UI integration
ui_integration:
  status_badges: ["lanes", "bands", "ladder", "coverage"]
  quick_metrics: ["lanes_found", "bands_total", "ladder_r2", "coverage_total"]
  fail_indicators: ["optimization_suggested", "vision_assist_recommended"]
```

### **Quick Summary JSON Format**
```json
{
  "analysis_type": "gel",
  "assay_detected": "sds_page",
  "status": "success",
  "quick_metrics": {
    "lanes_found": 7,
    "bands_total": 24,
    "ladder_r2": 0.93,
    "coverage_total": 0.39
  },
  "ui_display": {
    "primary_text": "7 lanes, 24 bands detected",
    "secondary_text": "Ladder fit: R² = 0.93",
    "status_badge": "success",
    "optimization_suggested": false
  },
  "fail_gates": {
    "triggered": false,
    "reasons": []
  }
}
```

---

## 📦 Auto-Run Pack 2: Basic Colony Analysis

### **File: `challenge_packs/colony_basic_v1/spec.yaml`**

```yaml
# Basic Colony Analysis - Auto-run on image drop
meta:
  name: "colony_basic_v1"
  description: "Immediate colony analysis with Lab colorspace - no user input required"
  version: "1.0"
  trigger: "auto_on_drop"
  last_verified: "2025-09-02"

# Phase III: Optimized colony detection
detect:
  min_peak_distance_frac: 0.05     # Larger spacing for colonies
  prominence_frac: 0.08             # Balanced sensitivity
  threshold_radius: 15              # Phansalkar radius
  min_colony_size: 5.0              # Minimum area filter

# Phase III: Binary segmentation with watershed
segmentation:
  threshold:
    method: "Phansalkar"            # Good for uneven illumination
    radius: 20                      # Adaptive window
  min_area_px: 25                   # Filter small artifacts
  watershed_enabled: true           # Separate touching colonies
  hole_filling: true                # Clean up masks

# Phase III: Lab colorspace classification (CRITICAL FIX)
color_classification:
  colorspace: "Lab"                 # Phase III fix - was RGB
  method: "rule_based"              # Simple thresholding
  blue_threshold_b: -4.0            # Lab b* threshold for blue
  generate_histogram: true          # Phase II telemetry
  confidence_scoring: true          # Classification confidence
  
# Automatic size binning
size_analysis:
  bin_edges_px: [20, 50, 100, 200, 400] # Size categories
  generate_distribution: true       # Size histogram
  outlier_detection: true           # Flag unusually large/small

# Phase IV: Auto-trigger optimization on fail-gates
optimization:
  auto_trigger_conditions:
    mask_binary_check: false        # Non-binary mask
    components_raw: ">50"           # Too many raw detections
    final_count: "<<components_raw" # Major filter chain loss
    classifier_applied: false       # Color classification failed
    blue_white_sum: "!=final_count" # Classification accounting error
    
  mode: "telemetry_first"           # Try parameter optimization first
  fallback_to_vision: true         # Use vision-assist for challenging plates
  max_attempts: 3                   # Bounded attempts

# Immediate outputs
outputs:
  overlay: "overlay.png"            # Color-coded colony overlay
  summary: "run_report.json"        # Phase II filter chain telemetry
  quick_stats: "quick_summary.json" # UI-friendly metrics
  
# UI integration
ui_integration:
  status_badges: ["colonies", "blue_white", "size_dist", "quality"]
  quick_metrics: ["colony_count", "blue_count", "white_count", "median_size"]
  fail_indicators: ["optimization_suggested", "vision_assist_recommended"]
  color_coding:
    blue_colonies: "#0066cc"        # UI color for blue colonies
    white_colonies: "#f0f0f0"       # UI color for white colonies
    ambiguous: "#ffaa00"            # UI color for ambiguous
```

### **Colony Quick Summary JSON Format**
```json
{
  "analysis_type": "colony",
  "status": "success", 
  "quick_metrics": {
    "colony_count": 15,
    "blue_count": 0,
    "white_count": 15,
    "median_size_px": 45,
    "size_distribution": [2, 8, 4, 1, 0]
  },
  "ui_display": {
    "primary_text": "15 colonies detected",
    "secondary_text": "0 blue, 15 white",
    "status_badge": "success",
    "optimization_suggested": false
  },
  "fail_gates": {
    "triggered": false,
    "reasons": []
  },
  "filter_chain_summary": {
    "components_raw": 25,
    "after_min_area": 15,
    "after_watershed": 15,
    "final_count": 15,
    "major_loss_stage": null
  }
}
```

---

## 🚀 Auto-Run Integration Flow

### **File Drop → Auto Analysis**

1. **Image Detection**: UI detects gel vs colony plate (aspect ratio, content heuristics)
2. **Pack Selection**: 
   - Rectangular/landscape → `gel_basic_v1`
   - Square/circular → `colony_basic_v1`
3. **Immediate Execution**: Run selected pack with safe defaults
4. **Overlay Generation**: Create visual overlay within 10 seconds
5. **Fail-Gate Check**: Detect if optimization should be suggested

### **Integration with Existing Phases**

```python
# autodense_autotune/auto_run/dispatcher.py
class AutoRunDispatcher:
    def __init__(self):
        self.gel_pack = "challenge_packs/gel_basic_v1/spec.yaml"
        self.colony_pack = "challenge_packs/colony_basic_v1/spec.yaml"
    
    def detect_image_type(self, image_path: str) -> str:
        """Detect if image is gel or colony plate"""
        # Heuristics: aspect ratio, circular regions, channel analysis
        
    def dispatch_auto_run(self, image_path: str) -> AutoRunResult:
        """Execute appropriate auto-run pack"""
        image_type = self.detect_image_type(image_path)
        
        if image_type == "gel":
            return self.run_pack(self.gel_pack, image_path)
        elif image_type == "colony":
            return self.run_pack(self.colony_pack, image_path)
            
    def check_fail_gates(self, run_result: AutoRunResult) -> List[str]:
        """Phase IV integration: detect when optimization needed"""
        fail_reasons = []
        
        if run_result.lanes_raw == 0:
            fail_reasons.append("no_lanes_detected")
        if run_result.bands_raw == 0:
            fail_reasons.append("no_bands_detected")
        if run_result.baseline_post_med < 0.01:
            fail_reasons.append("baseline_too_aggressive")
            
        return fail_reasons
```

### **UI Integration Points**

```javascript
// UI event handling for auto-run
class AutoDenseUI {
    onImageDrop(imagePath) {
        // Show immediate loading state
        this.showLoadingOverlay("Analyzing image...");
        
        // Dispatch auto-run
        const result = autoRunDispatcher.dispatch(imagePath);
        
        // Update UI with results
        this.updateOverlay(result.overlay_path);
        this.updateSummaryPanel(result.quick_stats);
        this.updateRunsList(result.run_id, result.status);
        
        // Check if optimization suggested
        if (result.fail_gates.triggered) {
            this.showOptimizeButton("Improve results?");
        }
    }
    
    updateSummaryPanel(quickStats) {
        if (quickStats.analysis_type === "gel") {
            this.displayGelMetrics(quickStats);
        } else if (quickStats.analysis_type === "colony") {
            this.displayColonyMetrics(quickStats);
        }
    }
}
```

---

## 🛡️ Safety & Quality Gates

### **Parameter Safety**
All auto-run packs use **validated safe defaults** from our Phase I-III testing:
- **Gel parameters**: Proven with EtBr (11 lanes, 2 bands) and SDS (7 lanes, 24 bands)
- **Colony parameters**: Proven with Lab colorspace fix (15 colonies, proper blue/white)

### **Fail-Gate Triggers**
Auto-run packs detect common failure modes and suggest optimization:
- **Detection failures**: No lanes/bands/colonies found
- **Quality issues**: Poor coverage, unstable baselines, classification failures
- **Physics violations**: Impossible metrics (coverage_total=0 with lanes>0)

### **Bounded Optimization**
When fail-gates trigger, system uses Phase IV bounded optimization:
- **Allowlist enforcement**: All parameter suggestions within safe ranges
- **Physics validation**: Accept only if metrics improve
- **Fallback safety**: Always preserve auto-run results as baseline

---

## 📊 Success Criteria

### **Performance Targets**
- **Time to overlay**: <10 seconds for typical gel/colony images
- **Success rate**: >95% produce reasonable overlays without optimization
- **Fail-gate accuracy**: Correctly identify >90% of cases needing optimization

### **Integration Metrics**
- **Auto-detection accuracy**: >95% correct gel vs colony classification  
- **Phase integration**: Seamless handoff to Phase IV optimization when needed
- **UI responsiveness**: Immediate visual feedback, no blocking operations

---

## 🚀 Deployment Instructions

### **Installation**
```bash
# Copy auto-run packs to challenge pack directory
cp NextSteps/AUTO_RUN_CHALLENGE_PACKS.md challenge_packs/README_AUTO_RUN.md

# Create pack directories
mkdir -p challenge_packs/gel_basic_v1
mkdir -p challenge_packs/colony_basic_v1

# Deploy specs (extract from this document)
# gel_basic_v1/spec.yaml
# colony_basic_v1/spec.yaml
```

### **Testing**
```bash
# Test gel auto-run
python -m autodense_autotune.cli run \
  --spec challenge_packs/gel_basic_v1/spec.yaml \
  --input samples/sds_gel.jpg \
  --workdir runs/test_gel_auto

# Test colony auto-run  
python -m autodense_autotune.cli run \
  --spec challenge_packs/colony_basic_v1/spec.yaml \
  --input samples/colony_plate.jpg \
  --workdir runs/test_colony_auto

# Verify outputs
ls runs/test_gel_auto/overlay.png
ls runs/test_gel_auto/quick_summary.json
```

These auto-run challenge packs complete the foundation for the "Drop, See, Ask" UI overlay, providing immediate analysis results that integrate seamlessly with our sophisticated AI optimization system underneath.