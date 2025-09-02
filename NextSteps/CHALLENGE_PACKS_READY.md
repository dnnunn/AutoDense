# Phase VII Challenge Packs: Production Lab Workflows

> **Doc Meta**
> - **Purpose:** Ready-to-implement challenge pack specifications for complete lab workflows
> - **Scope:** Six production workflows with QC gates, statistical validation, and reporting
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 🎯 Overview

These challenge packs provide complete analytical workflows that integrate detection, quantification, statistics, and reporting. Each pack includes QC gates that use our comprehensive telemetry system to ensure reliable results.

---

## 📦 Challenge Pack 1: Protein Quantification

### **File: `challenge_packs/protein_quant_v1/spec.yaml`**

```yaml
# Protein Quantification with Standard Curves
meta:
  name: "protein_quant_v1"
  description: "Protein quantification with LOQ/LLOQ calculation and %CV reporting"
  version: "1.0"
  last_verified: "2025-09-02"

task: "sds_page"

# Standard unified config
detect:
  lanes:
    min_peak_distance_frac: 0.035
    prominence_frac: 0.06
  baseline:
    method: "percentile"
    window_frac: 0.02
    quantile: 0.10
  
bands:
  baseline:
    method: "percentile" 
    window_frac: 0.015
    quantile: 0.08
  min_peak_distance_px: 14
  prominence_frac: 0.02

# Vision-assist integration
vision:
  mode: "assist"
  allowlist:
    bands:
      baseline.window_frac: [0.008, 0.025]
      min_peak_distance_px: [10, 18]

# Protein quantification specific
protein_quant:
  curve_types: ["linear", "log_linear", "4pl"]
  standards_required_min: 3
  replicate_cv_max: 0.15
  extrapolation_warning_factor: 1.2
  
  # Sample sheet format
  sample_sheet: "sample_sheet.csv"  # Required columns: lane, type, concentration, replicate
  
  # Quality gates
  qc_gates:
    curve_r2_min: 0.98
    standards_min: 3
    cv_max: 0.15
    ladder_r2_min: 0.95

# Outputs
outputs:
  standard_curve: "standard_curve.png"
  quantification_table: "protein_concentrations.csv"
  qc_report: "qc_metrics.json"
  lot_report: "lot_summary.pdf"

# Expected sample sheet format:
# lane,type,concentration,replicate,sample_id
# 1,standard,0.0,1,blank
# 2,standard,0.5,1,std_0.5
# 3,standard,1.0,1,std_1.0
# 4,unknown,NA,1,sample_A1
```

### **Natural Language Trigger**: 
*"Quantify protein with log-linear, report LOQ/LLOQ and %CV"*

---

## 📦 Challenge Pack 2: Lane Comparison with Statistics

### **File: `challenge_packs/lane_compare_v1/spec.yaml`**

```yaml
# Lane Comparison with MW Binning and Statistical Testing
meta:
  name: "lane_compare_v1" 
  description: "MW-binned lane comparison with Holm-Bonferroni correction"
  version: "1.0"
  last_verified: "2025-09-02"

task: "sds_page"

# Standard detection config
detect:
  lanes:
    min_peak_distance_frac: 0.035
    prominence_frac: 0.06
  baseline:
    method: "percentile"
    window_frac: 0.02
    quantile: 0.10

bands:
  baseline:
    method: "percentile"
    window_frac: 0.015  
    quantile: 0.08
  min_peak_distance_px: 14

# Lane comparison specific
lane_compare:
  mw_bins: [10, 15, 20, 25, 30, 37, 50, 75, 100, 150, 250]  # kDa
  statistical_test: "t_test"  # t_test | mann_whitney | anova
  multiple_correction: "holm_bonferroni"
  alpha: 0.05
  min_samples_per_condition: 2
  
  sample_sheet: "sample_sheet.csv"  # Required: lane, condition, replicate
  
  # Quality gates
  qc_gates:
    ladder_r2_min: 0.95
    min_samples_per_bin: 2
    bands_per_lane_min: 3

outputs:
  comparison_table: "lane_comparison.csv"
  volcano_plot: "volcano_plot.png"
  statistical_summary: "stats_summary.json"
  mw_profiles: "mw_profiles.png"

# Expected sample sheet:
# lane,condition,replicate,sample_id  
# 1,control,1,ctrl_1
# 2,control,2,ctrl_2
# 3,treatment,1,treat_1
# 4,treatment,2,treat_2
```

### **Natural Language Trigger**:
*"Compare treatment vs control at 5 kDa bins; Holm-Bonferroni 0.05"*

---

## 📦 Challenge Pack 3: Semi-Quantitative PCR

### **File: `challenge_packs/semi_qpcr_v1/spec.yaml`**

```yaml
# Semi-Quantitative PCR with ΔΔI Normalization
meta:
  name: "semi_qpcr_v1"
  description: "Semi-quantitative PCR analysis with ΔΔI normalization"
  version: "1.0"
  last_verified: "2025-09-02"

task: "etbr"

# EtBr-optimized detection
detect:
  lanes:
    min_peak_distance_frac: 0.028
    prominence_frac: 0.045
  baseline:
    method: "percentile" 
    window_frac: 0.015
    quantile: 0.08

bands:
  baseline:
    method: "percentile"
    window_frac: 0.01
    quantile: 0.12
  min_peak_distance_px: 12

# PCR-specific analysis
semi_qpcr:
  housekeeping_bands: "auto"  # auto-detect or specify lane indices
  calibrator_sample: 1        # reference sample for ΔΔI
  confidence_interval: 0.95
  replicate_min: 2
  
  sample_sheet: "sample_sheet.csv"  # lane, target, condition, replicate
  
  # Quality gates
  qc_gates:
    target_snr_min: 3.0
    housekeeping_snr_min: 3.0
    saturation_check: true
    replicate_cv_max: 0.3

outputs:
  deltadelta_table: "deltadelta_results.csv"
  fold_changes: "fold_change_plot.png"
  qc_metrics: "qc_summary.json"
  gel_overlay: "annotated_gel.png"

# Expected sample sheet:
# lane,target,condition,replicate,sample_id
# 1,actin,control,1,ctrl_1
# 2,gene_x,control,1,ctrl_1  
# 3,actin,treatment,1,treat_1
# 4,gene_x,treatment,1,treat_1
```

### **Natural Language Trigger**:
*"ΔΔI with WT_1 calibrator; 95% CI"*

---

## 📦 Challenge Pack 4: BandAssist Interactive

### **File: `challenge_packs/band_assist_v1/spec.yaml`**

```yaml
# Interactive Band Propagation with Confidence Scoring
meta:
  name: "band_assist_v1"
  description: "User-guided band propagation with confidence scoring"  
  version: "1.0"
  last_verified: "2025-09-02"

task: "sds_page"

# Standard detection with relaxed band settings for user guidance
detect:
  lanes:
    min_peak_distance_frac: 0.035
    prominence_frac: 0.06
    
bands:
  baseline:
    method: "percentile"
    window_frac: 0.015
    quantile: 0.08
  min_peak_distance_px: 12
  prominence_frac: 0.015  # Lower for user-guided detection

# BandAssist specific
band_assist:
  confidence_threshold: 0.8
  propagation_method: "rf_based"  # Rf-based or pixel-based
  ladder_lane_auto: true
  interactive_mode: true
  
  # Quality gates
  qc_gates:
    confidence_min: 0.8
    crossover_check: true
    monotonic_order: true
    bands_per_lane_min: 2

# Interactive workflow
workflow:
  steps:
    - "detect_lanes"
    - "user_click_bands"      # Interactive step
    - "propagate_across_lanes"
    - "calculate_confidence"
    - "validate_ordering"

outputs:
  band_table: "bands_with_confidence.csv"
  confidence_overlay: "confidence_overlay.png"
  propagation_report: "propagation_summary.json"
  interactive_session: "user_interactions.json"
```

### **Natural Language Trigger**:
*"Propagate bands from ladder; min confidence 0.8"*

---

## 📦 Challenge Pack 5: Colony Time-Series Tracking

### **File: `challenge_packs/colony_timeseries_v1/spec.yaml`**

```yaml
# Multi-Day Colony Tracking with Growth Analysis
meta:
  name: "colony_timeseries_v1"
  description: "Time-series colony tracking with growth curves and X-gal analysis"
  version: "1.0" 
  last_verified: "2025-09-02"

task: "colonies"

# Colony detection optimized for time-series
detect:
  min_peak_distance_frac: 0.05
  prominence_frac: 0.08
  baseline:
    method: "none"
    
segmentation:
  threshold:
    method: "Phansalkar"
    radius: 20
  min_area_px: 30

# Time-series specific
colony_timeseries:
  registration_method: "ecc"    # Plate alignment between timepoints
  tracking_method: "centroid"  # centroid | optical_flow
  growth_model: "exponential"  # exponential | logistic
  timepoint_hours: [0, 24, 48, 72]  # From sample sheet
  
  sample_sheet: "timepoints.csv"  # timepoint, hours, image_path
  
  # Quality gates  
  qc_gates:
    registration_rms_max: 5.0   # pixels
    track_continuity_min: 0.8   # fraction
    growth_r2_min: 0.85

# X-gal classification
color_classification:
  colorspace: "Lab"
  blue_threshold_b: -4.0
  temporal_consistency: true   # Track color changes over time

outputs:
  growth_curves: "growth_curves.png"
  tracking_overlay: "tracking_overlay.png" 
  colony_trajectories: "trajectories.csv"
  xgal_timeseries: "xgal_classification.csv"

# Expected sample sheet:
# timepoint,hours,image_path,plate_id
# 1,0,day0_plate1.jpg,plate1
# 2,24,day1_plate1.jpg,plate1
# 3,48,day2_plate1.jpg,plate1
```

### **Natural Language Trigger**:
*"Track colonies across days; ECC align; X-gal classification"*

---

## 📦 Challenge Pack 6: ColonyAssist Interactive QA

### **File: `challenge_packs/colony_assist_v1/spec.yaml`**

```yaml
# Interactive Colony Classification and QA
meta:
  name: "colony_assist_v1"
  description: "Interactive colony classification correction and mask refinement"
  version: "1.0"
  last_verified: "2025-09-02"

task: "colonies"

# Initial automated detection
detect:
  min_peak_distance_frac: 0.05
  prominence_frac: 0.08
  threshold_radius: 15
  min_colony_size: 5.0

segmentation:
  threshold:
    method: "Phansalkar"
    radius: 20
  min_area_px: 25

# Color classification
color_classification:
  colorspace: "Lab" 
  method: "rule_based"  # rule_based | kmeans | interactive
  blue_threshold_b: -4.0
  interactive_override: true

# ColonyAssist specific  
colony_assist:
  interactive_mode: true
  mask_editing: true
  batch_reclassify: true
  confidence_display: true
  
  # Quality gates
  qc_gates:
    mask_binary: true
    filter_chain_consistent: true
    classification_margins_reasonable: true

# Interactive workflow
workflow:
  steps:
    - "detect_colonies"
    - "initial_classification"  
    - "user_review_session"     # Interactive
    - "mask_refinement"         # Interactive
    - "batch_reclassification"  # Interactive
    - "final_validation"

outputs:
  corrected_classifications: "final_colonies.csv"
  mask_overlay: "corrected_mask.png"
  classification_confidence: "confidence_scores.csv"
  user_corrections_log: "corrections.json"
  before_after_comparison: "qa_comparison.png"
```

### **Natural Language Trigger**:
*"Open colony assist on this plate; fix mask and reclassify"*

---

## 🚀 Usage Instructions

### **Running Challenge Packs**
```bash
# Basic usage
python -m autodense_autotune.cli run \
  --spec challenge_packs/protein_quant_v1/spec.yaml \
  --input ./gels/standard_curve_run \
  --workdir ./runs/protein_quant_20250902

# With natural language trigger
python -m autodense_autotune.cli run \
  --nl "Quantify protein with log-linear, report LOQ/LLOQ and %CV" \
  --input ./gels/standards.jpg \
  --workdir ./runs/protein_quant_auto
```

### **Directory Structure**
```
challenge_packs/
├── protein_quant_v1/
│   ├── spec.yaml
│   └── sample_sheet_template.csv
├── lane_compare_v1/
│   ├── spec.yaml  
│   └── sample_sheet_template.csv
├── semi_qpcr_v1/
│   ├── spec.yaml
│   └── sample_sheet_template.csv
├── band_assist_v1/
│   └── spec.yaml
├── colony_timeseries_v1/
│   ├── spec.yaml
│   └── timepoints_template.csv  
└── colony_assist_v1/
    └── spec.yaml

runs/
├── protein_quant_20250902/
│   ├── attempt_001/
│   ├── attempt_002/
│   └── final_report.json
└── lane_compare_20250902/
    ├── final_report.json
    └── statistical_summary.json
```

---

## 🛡️ Quality Gates Summary

Each workflow includes specific QC gates that must pass:

| Workflow | Critical QC Gates |
|----------|-------------------|
| **Protein Quantification** | Curve R² ≥ 0.98, ≥3 standards, CV ≤ 15% |
| **Lane Comparison** | Ladder R² ≥ 0.95, ≥2 samples/condition |  
| **Semi-qPCR** | Target & HK SNR ≥ 3.0, no saturation |
| **BandAssist** | Confidence ≥ 0.8, no crossovers |
| **Colony Time-Series** | Registration RMS ≤ 5px, continuity ≥ 80% |
| **ColonyAssist** | Binary mask, consistent filter chain |

---

## 📊 Integration with Existing System

These challenge packs integrate seamlessly with our existing phases:
- **Phase I-III**: Use established telemetry and parameter independence
- **Phase IV**: Trigger vision-assist on QC gate failures
- **Phase V**: Accept natural language workflow requests
- **Phase VI**: Enable interactive refinement for BandAssist and ColonyAssist

Each workflow maintains our core principle: **Gemini advises, AutoDense measures, users guide, science decides.**