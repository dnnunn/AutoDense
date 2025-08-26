# AutoDense Workflow Presets Documentation

> **Doc Meta**
> - **Purpose:** Comprehensive guide for workflow presets and gel analysis configurations
> - **Scope:** All presets, parameters, best practices, and laboratory workflow integration
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

This document describes all available workflow presets in AutoDense, their parameters, expected outputs, and usage examples.

## Overview

AutoDense includes 6 built-in workflow presets designed for common laboratory analysis tasks:

1. **Molecular Weight Determination** - Universal MW analysis for protein or DNA gels with standard curve calibration
2. **Protein Quantification with Standards** - Quantitative analysis using calibration curves
3. **Compare Lanes** - Statistical comparison of densitometry profiles between lanes
4. **Semi-Quantitative PCR** - EtBr gel analysis with dual-lane normalization for PCR quantification
5. **X-gal Blue/White Colony Screening** - Bacterial transformation analysis
6. **Colony Growth Analysis** - Time-series colony growth analysis with plate matching and morphology tracking

Each preset contains pre-configured parameters and natural language examples to streamline analysis workflows.

---

## Gel Analysis Workflows

### 1. Molecular Weight Determination

**Type:** Gel Analysis  
**Description:** Universal MW analysis for protein or DNA gels with standard curve calibration and range estimation

#### Parameters
```json
{
  "auto_detect_gel_type": true,
  "gel_type_detection_method": "brightness_analysis",
  "mw_standards_required": true,
  "mw_marker_lane": "prompt_user",
  "mw_standards_input": "prompt_user",
  "create_mw_calibration_curve": true,
  "mw_curve_fitting": "polynomial",
  "mw_curve_degree": 2,
  "calculate_mw_uncertainty": true,
  "uncertainty_method": "curve_confidence",
  "band_assist_available": true,
  "band_assist_mode": "chat_toggle",
  "export_mw_csv": true,
  "export_annotated_png": true,
  "export_dialog": true,
  "annotate_bands_with_mw": true,
  "workflow_analysis_type": "comparative",
  "gel_format_selectable": true,
  "default_gel_format": "Auto-detect based on gel type"
}
```

#### Workflow Steps
1. **Image Loading and Format Detection** - Load gel image and auto-detect gel format
2. **Gel Type Analysis** - Determine if protein (bright bands on dark) or DNA (dark bands on bright)
3. **Lane Detection** - Find lanes using appropriate format parameters  
4. **MW Standards Identification** - Prompt user to specify which lane contains standards
5. **MW Standards Input** - User provides known molecular weights for marker bands
6. **Calibration Curve Creation** - Fit polynomial curve (MW vs migration distance)
7. **Band Detection** - Find all bands across gel with BandAssist option available
8. **MW Assignment** - Calculate molecular weights with uncertainty ranges for all bands
9. **Results Annotation** - Mark bands with MW labels on image
10. **Export Dialog** - Present save options for CSV and annotated PNG files

#### Expected Outputs
- **Gel Type Detection** - Automatic identification of protein vs DNA gel
- **MW Calibration Curve** - 2nd-degree polynomial fit with R² and curve equation
- **MW Assignments** - Molecular weights with ±uncertainty ranges for all detected bands
- **Annotated Image** - Gel image with MW labels overlaid on bands
- **BandAssist Integration** - Optional user-guided band selection across lanes
- **Export Files** - CSV with MW data and annotated PNG image:
  - **CSV**: Lane, band position, MW (kDa/bp), uncertainty range, confidence
  - **PNG**: Original gel with MW labels, calibration info, and lane markers
- **Quality Metrics** - Curve fit quality, extrapolation flags, confidence levels

#### Natural Language Examples
- "Determine molecular weights using standards in lane 1"
- "Create MW calibration curve and assign weights to unknowns"
- "This protein gel has ladder in first lane - calculate MWs"
- "DNA gel with 1kb ladder - determine fragment sizes"
- "Enable BandAssist for band selection across lanes"
- "Export results with MW assignments and uncertainty ranges"
- "Show calibration curve and mark bands with molecular weights"
- "Lane 3 has protein ladder: 250, 150, 100, 75, 50, 37, 25, 20, 15, 10 kDa"

#### Gel Type Auto-Detection
- **Protein Gels**: Bright bands on dark background → NuPAGE format preferred
- **DNA Gels**: Dark bands on bright background → Bio-Rad agarose format preferred
- **Brightness Analysis**: Automatically determines gel type from image intensity patterns
- **Format Selection**: Suggests appropriate gel format based on detected type

#### BandAssist Integration
- **Chat Toggle**: "Enable BandAssist" → Allows clicking on bands to find matches across lanes
- **Cross-Lane Selection**: Click band in one lane → Find corresponding bands in other lanes
- **MW Propagation**: Selected bands receive MW assignments from calibration curve
- **Visual Feedback**: Color-coded confidence indicators (green/orange/red)

#### Analysis Width Usage
- **Uses 66% lane width** for all MW determination to ensure consistent measurements
- **Avoids edge artifacts** that could affect migration distance calculations
- **Consistent region** across all lanes for accurate Rf calculations
- **Rationale**: "66% width provides consistent migration measurements for MW calibration"

#### Export Dialog System
- **Interactive Save**: User chooses location and filenames for exports
- **Format Options**: CSV for data analysis, PNG for presentations/reports
- **Filename Suggestions**: Auto-generated based on gel type and analysis date
- **Export Confirmation**: Displays saved file locations and contents

#### Best Use Cases
- **Universal MW Analysis** - Works for both protein and DNA gels
- **Research Documentation** - Comprehensive MW assignments with uncertainty
- **Method Validation** - Curve quality metrics and confidence indicators
- **Publication Figures** - Annotated images with professional MW labels
- **Teaching/Training** - Clear visualization of MW determination principles
- **Quality Control** - Automated detection with user verification via BandAssist

---

### 2. Protein Quantification with Standards

**Type:** Protein Quantification  
**Description:** SDS-PAGE gel with protein standards for quantitative analysis using calibration curves

#### Parameters
```json
{
  "expected_lanes": 12,
  "lane_width": 0.55,
  "constant_spacing": true,
  "standard_lanes": [1, 2],
  "enable_calibration": true,
  "calibration_method": "linear",
  "auto_linear_range": true,
  "robust_fit": true,
  "extrapolate": true,
  "confidence_intervals": true,
  "sensitivity": 0.8,
  "background_method": "local",
  "integration_method": "trapezoid",
  "export_excel": true,
  "min_snr": 3.0,
  "min_points_fit": 5
}
```

#### Workflow Steps
1. **Image Loading and Lane Detection** - Standard gel setup
2. **Standards Processing** - Analyze known protein amounts in lanes 1-2
3. **Calibration Curve Generation** - Create linear relationship between band area and protein amount
4. **Auto Linear Range Finding** - Identify best R² window for calibration
5. **Robust Fitting** - Apply Huber regression to exclude outliers
6. **Unknown Sample Quantification** - Apply calibration to remaining lanes
7. **Quality Control** - Flag out-of-range extrapolations and low-confidence results
8. **Confidence Interval Calculation** - Statistical uncertainty estimation
9. **Excel Export** - Comprehensive results with standards table and calibration curve

#### Expected Outputs
- **Calibration Model** - Linear regression parameters (slope, intercept, R²)
- **Standards Analysis** - Known amounts vs measured areas for validation
- **Quantified Results** - Protein amounts (µg) for all unknown samples
- **Quality Flags** - Out-of-range warnings and confidence indicators
- **Excel Workbook** - Three sheets:
  - *Standards*: Band areas, known amounts, residuals
  - *Calibration*: Curve parameters and scatter plot
  - *Results*: Sample quantification with confidence intervals
- **Statistical Report** - R² values, linear range, excluded outliers

#### Natural Language Examples
- "Quantify protein bands using standards in lanes 1-2"
- "Create calibration curve from known amounts: lane 1 has 2,4,8 µg, lane 2 has 2,4,8 µg"
- "Apply standard curve to unknown samples and flag out-of-range values"
- "Export results to Excel with standards table, curve, and quantified amounts"
- "Show confidence intervals for protein concentrations"
- "Use robust fitting to exclude outlier bands from calibration"
- "Find best linear range automatically for highest R-squared"

#### Analysis Width Usage
- **Uses 100% lane width** (full well) for all quantification to capture complete signal
- **Maximizes signal capture** for accurate concentration measurements
- **Prevents underestimation** of protein amounts due to partial band sampling
- **Rationale**: "Full width captures complete band signal for accurate quantification"

#### Best Use Cases
- Absolute protein quantification
- Concentration measurements
- Dose-response studies
- Quality control with known standards
- Regulatory compliance requiring quantitative data

---

## Comparative Analysis Workflows

### 3. Compare Lanes

**Type:** Comparative Analysis  
**Description:** Statistical comparison of densitometry profiles between gel lanes with significance testing

#### Parameters
```json
{
  "expected_lanes": 12,
  "lane_width": 0.55,
  "constant_spacing": true,
  "mw_marker_lane": 1,
  "enable_mw_calibration": true,
  "generate_profiles": true,
  "statistical_comparison": true,
  "significance_test": "t_test",
  "significance_threshold": 0.05,
  "multiple_comparison_correction": "bonferroni",
  "reference_lane": "prompt_user",
  "comparison_method": "pairwise",
  "peak_detection_method": "local_maxima",
  "peak_matching_tolerance": 0.02,
  "overlay_significant_changes": true,
  "green_for_increase": true,
  "red_for_decrease": true,
  "sensitivity": 0.7,
  "background_method": "local",
  "profile_smoothing": true,
  "export_profiles": true,
  "export_statistics": true
}
```

#### Workflow Steps
1. **Image Loading and Lane Detection** - Load gel and identify all lanes
2. **MW Calibration Setup** - Use designated marker lane (usually lane 1) for molecular weight standards
3. **Densitometry Profile Generation** - Create intensity profiles for each lane
4. **Profile Smoothing** - Apply smoothing algorithms for noise reduction
5. **Peak Detection** - Identify significant peaks in all lane profiles using local maxima
6. **Peak Matching** - Match corresponding peaks across lanes within Rf tolerance (2%)
7. **Reference Lane Selection** - Prompt user to specify which lane to use as reference
8. **Statistical Comparison** - Perform t-tests between reference lane and all other lanes
9. **Multiple Comparison Correction** - Apply Bonferroni correction for multiple testing
10. **Significance Filtering** - Identify peaks with p < 0.05 (or user-specified threshold)
11. **MW Assignment** - Calculate molecular weights for significant changes using calibration
12. **Overlay Generation** - Create visual overlay with color-coded significance markers
13. **Results Export** - Generate statistical reports and profile data

#### Expected Outputs
- **Lane Profiles** - Densitometry traces for each lane with peak annotations
- **Statistical Results** - P-values, fold changes, and significance flags for each peak
- **MW Assignments** - Molecular weights for all significantly changed bands
- **Comparative Overlay** - Visual representation with color-coded changes:
  - **Green bands**: Significantly increased signal (p < 0.05)
  - **Red bands**: Significantly decreased signal (p < 0.05)
  - **Unmarked bands**: No significant change detected
- **Profile Export Files** - CSV/JSON data containing:
  - Raw intensity profiles for each lane
  - Peak positions and intensities
  - Statistical test results
  - Molecular weight assignments
- **Statistical Report** - Comprehensive analysis including:
  - Summary of significant changes by lane
  - Multiple comparison correction results
  - Effect sizes and confidence intervals

#### Natural Language Examples
- "Compare all lanes to lane 3 and show significant differences"
- "Generate densitometry profiles and compare lanes statistically"
- "Mark increased bands in green and decreased bands in red"
- "Show me which protein bands changed significantly between treatments"
- "Compare lanes with p < 0.05 significance and MW assignments"
- "Use lane 1 as MW standard and compare lanes 2-12 to lane 5"
- "Apply multiple comparison correction and export statistical results"
- "Generate comparative overlay showing only significant changes"

#### Analysis Width Usage
- **Uses 66% lane width** for all quantification to ensure fair comparisons
- **Avoids edge artifacts** that could skew statistical comparisons between lanes
- **Consistent analysis region** across all lanes eliminates systematic biases
- **Rationale**: "66% width avoids edge artifacts for fair comparison"

#### Best Use Cases
- **Treatment vs Control Studies** - Compare protein expression before/after treatment
- **Time Course Experiments** - Track protein changes over multiple time points
- **Dose Response Analysis** - Compare effects of different concentrations
- **Knockout/Overexpression Studies** - Identify proteins affected by genetic modifications
- **Drug Screening** - Compare protein profiles across different compounds
- **Quality Control** - Verify consistency between replicate samples
- **Pathway Analysis** - Identify co-regulated protein groups

#### Statistical Methodology
- **T-test Comparison** - Paired or unpaired t-tests for peak intensity differences
- **Bonferroni Correction** - Conservative adjustment for multiple comparisons
- **Peak Matching** - Rf-based alignment with 2% tolerance for gel-to-gel variation
- **Effect Size Calculation** - Fold change and Cohen's d for biological significance
- **Confidence Intervals** - 95% CI for all significant changes

#### User Interaction Flow
1. **Lane Count Specification** - "Use 15 lanes" or "This is a 12-lane gel"
2. **MW Marker Designation** - "Lane 1 contains molecular weight standards"
3. **Reference Lane Selection** - "Compare all lanes to lane 4 (control)"
4. **Significance Threshold** - "Use p < 0.01 for stricter significance" (optional)
5. **Results Review** - Interactive overlay showing all significant changes
6. **Export Options** - Choose data formats and visualization preferences

---

## PCR Analysis Workflows

### 4. Semi-Quantitative PCR

**Type:** PCR Analysis  
**Description:** EtBr gel analysis with dual-lane normalization for semi-quantitative PCR results

#### Parameters
```json
{
  "gel_type": "etbr",
  "dual_lane_layout": true,
  "expected_sample_pairs": 12,
  "lane_pairing": "vertical",
  "experimental_row": "upper",
  "control_row": "lower",
  "normalization_method": "control_ratio",
  "mw_marker_present": false,
  "mw_marker_lane": "prompt_if_present",
  "blank_detection": true,
  "blank_threshold": 0.1,
  "percentile_highlighting": true,
  "highlight_percentile": 90,
  "sensitivity": 0.6,
  "background_method": "median",
  "band_integration": "total_intensity",
  "export_csv": true,
  "sample_naming": "prompt_user",
  "include_raw_values": true,
  "quality_flags": true
}
```

#### Workflow Steps
1. **EtBr Gel Image Loading** - Import ethidium bromide gel image
2. **Dual-Lane Layout Detection** - Identify upper experimental and lower control lane pairs
3. **Sample Pair Validation** - Confirm expected number of sample pairs (default 12)
4. **MW Marker Detection** - Check for and optionally process molecular weight standards
5. **Band Detection and Integration** - Find PCR product bands in both experimental and control lanes
6. **Blank Lane Identification** - Detect lanes with signal below threshold (mark as "not detectable")
7. **Control Normalization** - Calculate ratio of experimental signal to corresponding control signal
8. **Quality Assessment** - Flag low-quality measurements and problematic ratios
9. **Percentile Analysis** - Identify values in 90th percentile for highlighting
10. **Sample Naming** - Prompt user for custom sample names or use lane numbers
11. **Results Compilation** - Generate normalized ratios with quality flags
12. **CSV Export** - Export results with sample names, ratios, and quality indicators

#### Expected Outputs
- **Normalized Ratios** - Experimental/Control ratio for each sample pair
- **Quality Flags** - Indicators for low signal, poor normalization, or technical issues
- **Percentile Highlighting** - Visual and data marking of top 10% values
- **Blank Detection** - "Not detectable" designation for samples below threshold
- **Sample Identification** - Lane numbers or user-provided sample names
- **CSV Export File** - Comprehensive results table containing:
  - Sample ID/Lane number
  - Experimental lane signal (raw intensity)
  - Control lane signal (raw intensity)  
  - Normalized ratio (Experimental/Control)
  - Quality flags
  - Percentile ranking
  - "Not detectable" markers for blank lanes
- **Visual Overlay** - Lane pairs marked with normalization results
- **Statistics Summary** - Overall distribution metrics and quality assessment

#### Natural Language Examples
- "Analyze this EtBr gel with experimental lanes on top, control lanes below"
- "Quantify PCR products using control normalization and highlight top 10% values"
- "Calculate ratios of experimental to control PCR signals"
- "Mark blank lanes as 'not detectable' and export results to CSV"
- "Use 12 sample pairs with upper experimental and lower control lanes"
- "Highlight values in 90th percentile and provide sample names"
- "Normalize PCR band intensities against corresponding control reactions"
- "Export semi-quantitative PCR results with quality flags"

#### Analysis Width Usage  
- **Uses 100% lane width** (full well) for PCR band quantification
- **Captures complete PCR product signal** for accurate ratio calculations
- **Essential for normalization** - both experimental and control signals need full capture
- **Rationale**: "Full width captures complete band signal for accurate quantification"

#### Best Use Cases
- **Gene Expression Analysis** - Semi-quantitative RT-PCR with housekeeping gene controls
- **Viral Load Estimation** - PCR quantification with internal control reactions
- **Transgene Copy Number** - Comparing transgenic samples to control genes
- **Mutation Detection** - Allele-specific PCR with reference controls
- **Drug Effect Studies** - Comparing treated vs untreated samples with controls
- **Quality Control** - Validating PCR reactions against known standards
- **Screening Assays** - High-throughput sample analysis with normalization

#### Dual-Lane Layout System
- **Upper Lanes (Experimental)** - PCR reactions containing target sequences to be quantified
- **Lower Lanes (Control)** - Corresponding control PCR reactions for normalization
- **Vertical Pairing** - Lane 1 upper paired with Lane 1 lower, Lane 2 upper with Lane 2 lower, etc.
- **Flexible Sample Count** - Supports variable number of sample pairs (user configurable)
- **Blank Handling** - Automatic detection of empty wells in either experimental or control positions

#### Normalization Methodology
- **Ratio Calculation** - Experimental signal ÷ Control signal for each sample pair
- **Background Subtraction** - Median background correction for EtBr fluorescence
- **Quality Thresholds** - Minimum signal requirements for reliable quantification
- **Outlier Detection** - Statistical identification of problematic samples
- **Percentile Ranking** - Relative quantification across all samples in the gel

#### User Interaction Flow
1. **Gel Type Confirmation** - "This is an EtBr gel with dual-lane layout"
2. **Sample Count Specification** - "I have 12 sample pairs" or system auto-detects
3. **MW Marker Check** - "Lane X contains molecular weight markers" (if present)
4. **Sample Naming** - Option to provide custom sample names or use lane numbers
5. **Results Review** - Interactive display of normalized ratios and quality flags
6. **Export Configuration** - CSV format with user-specified filename and location

#### Quality Control Features
- **Signal Threshold Validation** - Ensures both experimental and control signals are above background
- **Ratio Range Checking** - Flags unusually high or low normalization ratios
- **Blank Lane Detection** - Automatic identification of failed PCR reactions
- **Technical Replicate Assessment** - Identifies samples that may need repeat analysis
- **Statistical Outlier Flagging** - Highlights samples that deviate significantly from the population

---

## Colony Analysis Workflows

### 5. X-gal Blue/White Colony Screening

**Type:** Colony Counting  
**Description:** Bacterial transformation plates with X-gal blue/white selection

#### Parameters
```json
{
  "classify_colonies": true,
  "classification_type": "xgal",
  "color_groups": ["blue", "white", "mixed"],
  "min_colony_size_mm": 0.2,
  "max_colony_size_mm": 8.0,
  "sensitivity": 0.8,
  "use_three_pass_filtering": true,
  "blue_enhancement_factor": 1.5,
  "rg_suppression_factor": 0.7,
  "blue_threshold": 120,
  "white_balance_threshold": 30,
  "apply_median_filter": true,
  "median_filter_radius": 2,
  "format_normalization": "preserve_color_channels",
  "support_heic_images": true
}
```

#### Workflow Steps
1. **Image Loading and Format Normalization** - Import and normalize iPhone HEIC, JPEG, or PNG images
2. **Three-Pass Color Filtering** - Apply enhanced blue colony detection:
   - **Pass 1**: Blue Channel Enhancement (1.5x amplification)
   - **Pass 2**: Red/Green Suppression (0.7x reduction)  
   - **Pass 3**: Contrast Optimization for blue/white discrimination
3. **Colony Detection** - Identify colonies using color-aware segmentation
4. **Color Classification** - Distinguish blue, white, and mixed colonies using RGB analysis
5. **Size Validation** - Filter colonies by size constraints (0.2-8.0mm)
6. **Median Filtering** - Reduce noise while preserving colony boundaries
7. **Statistical Analysis** - Calculate transformation efficiency and ratios
8. **Visualization** - Color-coded overlay with classification confidence
9. **Results Export** - Comprehensive colony data with positions and color metrics

#### Expected Outputs
- **Enhanced Colony Classification** - Blue, white, and mixed colony counts with confidence scores
- **Three-Pass Filtered Images** - 
  - Original image
  - Blue-enhanced image showing improved discrimination
  - Classification mask with color-coded regions
- **Color Channel Analysis** - Separate R, G, B channel images for validation
- **Transformation Efficiency** - Percentage calculations with blue/white ratios
- **Colony Statistics** - Per-colony data including:
  - Color ratios (R:G:B proportions)
  - Blue discrimination scores
  - Size measurements in mm
  - Position coordinates
- **Export Files** - Enhanced CSV with:
  - Standard colony data (count, position, size)
  - Color metrics (RGB values, discrimination scores)
  - Quality flags and confidence levels
  - Three-pass filter results

#### Natural Language Examples
- "Count blue and white colonies separately"
- "Classify colonies by X-gal reaction"
- "Show me transformation efficiency"
- "Export colony data with positions and colors"
- "Analyze iPhone HEIC image of transformation plate"
- "Use enhanced blue filtering for faint colonies"
- "Apply three-pass color filtering for better discrimination"

#### Best Use Cases
- Cloning efficiency assessment
- Transformation optimization
- Blue/white screening protocols
- Insert validation experiments

---

### 6. Colony Growth Analysis

**Type:** Colony Counting  
**Description:** Time-series colony growth analysis with plate matching and morphology tracking

#### Parameters
```json
{
  "measure_sizes": true,
  "size_grouping": true,
  "size_bins": [0.5, 1.0, 2.0, 4.0],
  "min_colony_size_mm": 0.1,
  "max_colony_size_mm": 15.0,
  "statistical_analysis": true,
  "time_series_analysis": true,
  "plate_alignment_method": "feature_matching",
  "track_morphology": true,
  "measure_circularity": true,
  "measure_texture": true,
  "xgal_blueness_analysis": false,
  "growth_rate_calculation": true,
  "max_time_points": 20,
  "alignment_tolerance_px": 5,
  "colony_matching_threshold": 0.8
}
```

#### Workflow Steps
1. **Reference Image Setup** - Load first time point as reference
2. **Initial Colony Detection** - Identify and measure all colonies (size, morphology, position)
3. **Subsequent Time Points** - Load additional time point images
4. **Plate Alignment** - Automatically align plates using feature matching or orientation marks
5. **Colony Tracking** - Match colonies across time points based on position and characteristics
6. **Growth Analysis** - Calculate growth rates, morphology changes, and X-gal development
7. **Statistical Analysis** - Analyze growth patterns, track statistics, and confidence metrics
8. **Results Export** - Comprehensive time-series data with growth curves and statistics

#### Expected Outputs
- **Colony Tracks** - Individual colony growth over time with position tracking
- **Growth Rates** - Area and diameter growth rates per hour for each colony
- **Morphology Analysis** - Circularity, solidity, aspect ratio, and texture changes
- **X-gal Progression** - Blueness development over time (if enabled)
- **Alignment Results** - Plate alignment confidence and transformation matrices
- **Statistical Summary** - Total tracks, growing colonies, average final sizes
- **Time-Series Data** - Complete CSV and JSON export with all measurements
- **Visual Overlays** - Growth tracking visualization with confidence indicators

#### Natural Language Examples
- "Track colony growth over multiple time points"
- "Align plates using orientation marks"
- "Measure colony size and morphology changes"
- "Analyze X-gal blueness development over time"
- "Calculate growth rates for each colony"
- "Match colonies across different imaging sessions"
- "Correct for plate rotation and skewing"
- "Export time-series growth data"

#### Time-Series Analysis Features
- **Automatic Plate Alignment** - Corrects for rotation, translation, and skewing between time points
- **Colony Matching Algorithm** - Tracks individual colonies across time with confidence scoring
- **Growth Rate Calculations** - Area and diameter growth per hour with statistical analysis
- **Morphology Tracking** - Changes in shape, texture, and circularity over time
- **X-gal Integration** - Optional blueness quantification for transformation studies
- **Quality Control** - Confidence metrics and alignment validation

#### Alignment Methods
1. **Feature Matching** - Uses colony positions and plate edges as alignment features
2. **Orientation Marks** - Detects user-placed marks (dots, crosses) for precise alignment
3. **Hybrid Approach** - Combines both methods for maximum robustness

#### Export Formats
**CSV Format:**
```csv
Track_ID,Time_Point,Colony_ID,Center_X,Center_Y,Area_mm2,Diameter_mm,Circularity,Blueness
track_1,0,colony_50_60,50.0,60.0,2.341,1.72,0.89,0.23
track_1,1,colony_52_61,52.0,61.0,3.127,1.99,0.87,0.35
```

**JSON Format:** Complete analysis data with tracking statistics and metadata

#### Best Use Cases
- **Longitudinal Growth Studies** - Track colony development over hours or days
- **Antibiotic Time-Kill Assays** - Monitor colony response to treatments
- **Transformation Efficiency Over Time** - X-gal development tracking
- **Growth Kinetics Analysis** - Detailed growth rate measurements
- **Morphology Studies** - Shape and texture changes during growth
- **Multi-timepoint Screening** - Large-scale time-series experiments
- **Quality Control** - Growth consistency across batches

---

## Using Workflow Presets

### Accessing Presets
1. **Demo Button** - Click the Demo button in the main UI to see workflow examples
2. **Workflows Button** - Access the workflow management interface
3. **Natural Language** - Type commands that match the natural language examples
4. **Direct Selection** - Choose presets from the workflow dropdown

### Workflow Management
- **Built-in Presets** - Cannot be deleted or modified, always available
- **Custom Presets** - Users can create and modify their own workflows
- **Usage Tracking** - System tracks which presets are used most frequently
- **Import/Export** - Share workflows between users or backup configurations

### Parameter Customization
Most workflow parameters can be adjusted through natural language:
- "Use 10 lanes instead of 12"
- "Set sensitivity to high for faint bands"  
- "Change background method to gaussian"
- "Enable molecular weight calibration"

### Best Practices
1. **Start with Built-in Presets** - Use the closest matching preset as a starting point
2. **Validate Parameters** - Check that lane counts and sizes match your gel format
3. **Test with Known Samples** - Verify workflows work with reference images
4. **Save Custom Workflows** - Create presets for your specific protocols
5. **Use Natural Language** - Describe what you want in plain English

---

## NuPAGE Gel Format System

AutoDense includes built-in support for standard Invitrogen NuPAGE gel formats with precise lane detection parameters.

### Supported NuPAGE Formats

#### Mini Gels (8cm × 8cm)
- **10-well**: ~8.0mm well width, 5.3mm lane width (66% of well)
- **12-well**: ~6.7mm well width, 4.4mm lane width (66% of well)  
- **15-well**: ~5.3mm well width, 3.5mm lane width (66% of well)
- **17-well**: ~4.7mm well width, 3.1mm lane width (66% of well)
- **12-well Tris-Acetate**: High MW protein gel variant

#### Midi Gels (8cm × 13cm)
- **12+2-well**: 14 total wells, ~5.7mm well width, 3.8mm lane width
- **20-well**: ~4.0mm well width, 2.6mm lane width
- **26-well**: ~3.1mm well width, 2.0mm lane width

### Lane Width System (Critical Distinction)

AutoDense uses **two different width concepts** depending on the analysis type:

#### 1. Lane Detection Width (66% - Always Used for Finding Lanes)
- **Purpose**: Identify where lanes are located in the gel image
- **Width**: 66% of well width to avoid edge artifacts during detection
- **Usage**: All workflows use this for initial lane identification
- **Calculation**: Well width × 0.66
- **Edge margin**: 17% on each side of well for clean detection

#### 2. Analysis Width (Varies by Workflow Type)
- **Purpose**: Define the area used for actual band quantification and analysis
- **Width**: Depends on analysis requirements:
  - **Comparative Analysis** (Compare Lanes, MW determination): 66% width
  - **Quantitative Analysis** (Protein Quantification, PCR): 100% width (full well)
  - **Standard Analysis**: 66% width (default)

#### Why This Matters

**Comparative Analysis (66% width):**
- Uses interior portion to avoid edge artifacts
- Ensures fair comparison between lanes
- Consistent region eliminates systematic differences
- Examples: Compare Lanes, molecular weight determination

**Quantitative Analysis (100% width):**
- Captures complete band signal for accurate measurements
- Critical for absolute quantification workflows
- Prevents underestimation of protein/DNA amounts
- Examples: Protein Quantification, Semi-quantitative PCR

#### Width Specifications by Format
```
Mini 10-well: 8.0mm well → 5.3mm detection / 8.0mm quantitative
Mini 12-well: 6.7mm well → 4.4mm detection / 6.7mm quantitative  
Mini 15-well: 5.3mm well → 3.5mm detection / 5.3mm quantitative
Mini 17-well: 4.7mm well → 3.1mm detection / 4.7mm quantitative
Midi 20-well: 4.0mm well → 2.6mm detection / 4.0mm quantitative
Midi 26-well: 3.1mm well → 2.0mm detection / 3.1mm quantitative
```

### Gel Format Selection
All workflow presets support gel format selection:

```json
{
  "gel_format_selectable": true,
  "default_gel_format": "NuPAGE Mini 12-well",
  "available_formats": [
    "NuPAGE Mini 10-well",
    "NuPAGE Mini 12-well", 
    "NuPAGE Mini 15-well",
    "NuPAGE Mini 17-well",
    "NuPAGE Mini 12-well Tris-Acetate",
    "NuPAGE Midi 12+2-well",
    "NuPAGE Midi 20-well",
    "NuPAGE Midi 26-well",
    "Custom Format"
  ]
}
```

### Detection Parameters by Format
Lane detection sensitivity automatically adjusts based on well density:

- **≤12 wells**: 70% sensitivity (standard for wider wells)
- **13-17 wells**: 80% sensitivity (higher for narrower wells)  
- **≥18 wells**: 85% sensitivity (maximum for very narrow wells)

### Custom Gel Formats
Users can define custom formats with:
- **Custom dimensions** (width × height in mm)
- **Custom well count** (any number of wells)
- **Custom gel type** (bis_tris, tris_acetate, tricine, etc.)
- **Automatic lane width calculation** (66% rule maintained)

### Natural Language Format Selection
Users can specify gel formats naturally:
- "Use NuPAGE Mini 15-well format"
- "This is a 17-well mini gel"
- "Switch to Midi 20-well format" 
- "Custom gel: 10cm wide, 12 wells"

### Bio-Rad Agarose Gel Formats

AutoDense includes support for Bio-Rad Sub-Cell GT and Mini-Sub GT agarose gel systems based on manufacturer specifications.

#### Mini-Sub GT Formats (7×10 cm tray, ~90mm usable width)
- **8-well**: ~11.3mm well width, 7.5mm detection / 11.3mm quantitative
- **10-well**: ~9.0mm well width, 5.9mm detection / 9.0mm quantitative
- **15-well**: ~6.0mm well width, 4.0mm detection / 6.0mm quantitative

#### Wide Mini-Sub GT Formats (15×7 cm tray, ~130mm usable width)  
- **15-well**: ~8.7mm well width, 5.7mm detection / 8.7mm quantitative
- **20-well**: ~6.5mm well width, 4.3mm detection / 6.5mm quantitative
- **25-well**: ~5.2mm well width, 3.4mm detection / 5.2mm quantitative

#### Specifications Based on Bio-Rad Manuals
- **Mini-Sub GT**: 7×10 cm tray → ~60×90 mm gel size
- **Wide Mini-Sub GT**: 15×7 cm tray → ~60×130 mm gel size
- **Usable widths**: 90mm (standard) / 130mm (wide) from manufacturer specs
- **Variable height**: Gel height varies by user casting preferences
- **Tolerance**: ±0.5mm variation due to comb spacing and tapering

#### Custom Measurements
For exact measurements of your specific combs, use:
```java
BioRadFormat.createCustom(
    "Custom Bio-Rad Format",
    "Your measured description",
    exactWidthMm,    // Your measured width
    exactHeightMm,   // Your measured height  
    exactWellCount   // Your measured well count
);
```

#### Agarose Gel Optimization
BioRad formats are pre-configured for agarose gel analysis:
- **Lower detection sensitivity** (0.6-0.8) optimized for EtBr/SYBR staining
- **Gaussian background method** for fluorescent gel backgrounds
- **Increased spacing tolerance** (20%) for agarose gel flexibility
- **Same width rules apply**: 66% for detection, varies by analysis type

#### Natural Language Support
- "Use Bio-Rad Mini-Sub GT 10-well format"
- "This is a Wide Mini-Sub GT 20-well agarose gel"
- "Switch to Bio-Rad agarose format"

## Missing Gel Format Handling

### What Happens When Users Don't Specify Gel Format?

AutoDense uses a **multi-layered fallback system** to handle cases where users don't provide gel format or lane count information:

#### Detection Strategy Hierarchy

**1. User-Specified Format (Highest Priority)**
- **Natural Language**: "Use NuPAGE Mini 12-well" → Direct format selection
- **Explicit Mentions**: "This is a 15-well gel" → Format suggestions provided
- **Gel Type**: "Bio-Rad agarose" → Appropriate format family selected
- **Confidence**: 90-95% (user explicitly stated preference)

**2. Automatic Lane Detection**
- **Generic Parameters**: Uses broad detection settings to find lanes
- **Lane Counting**: Attempts to detect actual number of lanes in image
- **Format Matching**: Matches detected count to most likely formats
- **Confidence**: 60-85% (depends on image quality and detection clarity)

**3. Intelligent Workflow-Based Guessing**
- **Protein Workflows** → Defaults to NuPAGE Mini 12-well (most common)
- **PCR Workflows** → Defaults to Bio-Rad 10-well agarose (PCR standard)
- **Context Clues**: Analyzes user language for hints about gel type
- **Confidence**: 60-70% (educated guess based on workflow patterns)

**4. User Prompting (Fallback)**
- **Interactive Confirmation**: "Using NuPAGE 12-well as default - correct?"
- **Alternative Options**: Lists common alternatives for the workflow
- **Guided Selection**: Provides natural language options to clarify
- **Confidence**: 50% (requires user confirmation to proceed)

#### Example Interaction Flows

**Scenario 1: User Provides Clear Information**
```
User: "Quantify proteins on this NuPAGE Mini 15-well gel"
System: ✓ Using NuPAGE Mini 15-well format (95% confidence)
```

**Scenario 2: Partial Information**
```
User: "Analyze protein bands on this gel"
System: Auto-detecting lanes... found 12 lanes
System: ✓ Suggested NuPAGE Mini 12-well format (80% confidence)
System: "Is this correct, or would you prefer 15-well or Bio-Rad?"
```

**Scenario 3: No Format Information**
```
User: "Find the lanes and quantify bands"
System: Using NuPAGE Mini 12-well as default for protein analysis
System: "Please confirm or say 'use 15-well' / 'use Bio-Rad agarose' if different"
User: "Actually this is agarose"
System: ✓ Switching to Bio-Rad Mini-Sub GT 10-well format
```

**Scenario 4: PCR Workflow**
```
User: "Semi-quantitative PCR analysis"  
System: Using Bio-Rad 10-well agarose as default for PCR
System: "Correct, or specify different well count?"
```

#### Automatic Lane Detection Parameters

When format is unknown, the system uses **generous detection settings**:

```json
{
  "sensitivity": 0.7,
  "min_lane_width_px": 15,
  "max_lane_width_px": 100,
  "lane_spacing_tolerance": 0.25,
  "expected_lanes_range": [6, 25],
  "background_method": "median"
}
```

#### Smart Format Suggestions

**Lane Count Matching**:
- **8 lanes** → Bio-Rad Mini-Sub GT 8-well (PCR) / NuPAGE Mini 10-well (protein)
- **12 lanes** → NuPAGE Mini 12-well (most common)
- **15 lanes** → NuPAGE Mini 15-well / Bio-Rad 15-well (context-dependent)
- **20+ lanes** → Wide formats (Midi/Wide Mini-Sub GT)

**Workflow Context**:
- **Protein workflows** → Prefer NuPAGE formats
- **PCR workflows** → Prefer Bio-Rad agarose formats
- **Comparative analysis** → Most common format for lab
- **Quantitative analysis** → Format optimized for signal capture

#### User Guidance Messages

The system provides helpful prompts when format is unclear:

```
"Please confirm: Using NuPAGE Mini 12-well as default for protein analysis.
Say 'use 15-well' or 'use Bio-Rad agarose' if different.

Alternatives available:
• NuPAGE Mini 15-well
• Bio-Rad Mini-Sub GT 10-well  
• Bio-Rad Mini-Sub GT 15-well

You can also say:
• 'Detect lanes automatically'
• 'Use custom gel format'
• 'This is a [number]-well gel'"
```

#### Confidence Levels and Actions

- **>90%**: Proceed automatically with selected format
- **80-90%**: Proceed but mention confidence level
- **60-80%**: Ask for confirmation before proceeding
- **<60%**: Require user clarification before analysis

This multi-layered approach ensures users can get analysis results even when they don't specify gel formats, while maintaining accuracy through intelligent defaults and user confirmation when needed.

---

## Enhanced Image Format Support

### TwelveMonkeys ImageIO Integration

AutoDense now includes comprehensive image format support via TwelveMonkeys ImageIO library, providing:

#### Supported Input Formats
- **JPEG/JPG** - Enhanced JPEG support with better metadata handling
- **TIFF/TIF** - Extended TIFF support including multi-page and high-bit-depth images
- **PNG** - Full PNG support with transparency
- **BMP** - Bitmap image support
- **GIF** - Basic GIF support (static images)
- **HEIC/HEIF** - iPhone format support (with conversion recommendation)

#### Format Normalization System

All input images are automatically normalized to standardized formats for consistent analysis:

**Normalization Options:**
```java
// For gel analysis (grayscale conversion)
NormalizationOptions.forGelAnalysis()
  - Target: 8-bit grayscale TIFF
  - Conversion: Luminance weighting (0.299*R + 0.587*G + 0.114*B)
  - Enhancement: Contrast adjustment (1.2x factor)

// For colony analysis (preserve color)  
NormalizationOptions.forColonyAnalysis()
  - Target: 8-bit color TIFF
  - Conversion: Preserve RGB channels
  - Enhancement: Contrast adjustment for better discrimination
```

**Automatic Processing:**
1. **Format Detection** - Identifies input format from file extension and content
2. **ImageIO Loading** - Uses appropriate reader with full metadata support
3. **ImageJ Conversion** - Converts to ImagePlus for analysis pipeline
4. **Normalization** - Applies format-specific processing
5. **TIFF Export** - Saves normalized version for consistent analysis

#### iPhone HEIC Support

**Current Status:** HEIC images can be processed with conversion recommendations:

```
For HEIC images: please convert to JPEG/PNG format first
```

**Recommended Workflow:**
1. **iOS Photos App**: Export HEIC as JPEG (highest quality)
2. **Online Converters**: Use HEIC-to-JPEG conversion tools
3. **ImageJ Processing**: Load converted JPEG into AutoDense

**Future Enhancement:** Native HEIC decoding will be added when stable cross-platform libraries become available.

#### Three-Pass Color Filtering

**Enhanced Blue Colony Detection:**

The colony analysis workflows now use sophisticated color channel processing:

**Pass 1: Blue Channel Enhancement**
- Amplifies blue signal by configurable factor (default 1.5x)
- Preserves blue colony details while maintaining dynamic range
- Handles faint blue colonies that might be missed by standard processing

**Pass 2: Red/Green Suppression**  
- Reduces red and green channels by configurable factor (default 0.7x)
- Improves blue/white discrimination by reducing background interference
- Maintains white colony detection while enhancing blue contrast

**Pass 3: Contrast Optimization**
- Applies adaptive contrast enhancement based on color ratios
- Strengthens discrimination between blue and non-blue regions
- Uses median filtering to reduce noise while preserving colony boundaries

**Configuration Parameters:**
```json
{
  "blue_enhancement_factor": 1.5,     // Blue amplification
  "rg_suppression_factor": 0.7,       // Red/Green reduction  
  "blue_threshold": 120,               // Blue classification threshold
  "white_balance_threshold": 30,       // White balance tolerance
  "apply_median_filter": true,         // Noise reduction
  "median_filter_radius": 2            // Filter strength
}
```

**Color Analysis Outputs:**
- **RGB Channel Images** - Individual R, G, B channel visualization
- **Blue-Enhanced Image** - Result of three-pass filtering
- **Classification Mask** - Color-coded colony identification
- **Color Metrics** - Quantitative RGB analysis for each colony
- **Discrimination Scores** - Blue vs non-blue confidence measures

#### Technical Implementation

**Service Registration:**
```java
// Automatic initialization during startup
ImageIOServiceRegistry.initialize();

// Verify format support
boolean heicSupported = ImageIOServiceRegistry.isHEICSupported();
String[] allFormats = ImageIOServiceRegistry.getSupportedReadFormats();
```

**Format Normalization API:**
```java
// Normalize any supported image to grayscale TIFF
NormalizationResult result = FormatNormalizer.normalizeToGrayscaleTIFF(
    "/path/to/image.heic", 
    NormalizationOptions.forGelAnalysis()
);

// Access normalized image
ImagePlus normalized = new ImagePlus(result.normalizedFilePath);
```

**Color Channel Processing:**
```java
// Analyze blue colonies with three-pass filtering
ChannelAnalysisResult analysis = ColorChannelProcessor.analyzeBlueColonies(
    colorImage,
    BlueColonyFilterOptions.defaultOptions()
);

// Access results
ImagePlus blueEnhanced = analysis.blueEnhanced;
double blueToWhiteRatio = analysis.blueToWhiteRatio;
int estimatedBlue = analysis.estimatedBlueColonies;
```

#### Quality Assurance

**Format Validation:**
- Input format verification before processing
- Metadata extraction and preservation
- Error handling for unsupported or corrupted files
- Fallback options for processing failures

**Processing Verification:**
- Bit-depth consistency checking
- Color space validation
- Dynamic range preservation
- Quality metrics for normalization success

This enhanced format support ensures that AutoDense can handle images from any modern camera or microscope system, with special optimization for iPhone users in laboratory settings.

### Modular App Bundle Architecture

AutoDense uses a modular approach for runtime dependencies rather than a single fat JAR:

**App Bundle Structure:**
```
AutoDense.app/Contents/Resources/java/
├── autodense-plugin-0.1.0-SNAPSHOT.jar    # Main application
└── lib/                                     # Runtime dependencies (170 JARs)
    ├── imageio-core-3.12.0.jar            # TwelveMonkeys core
    ├── imageio-jpeg-3.12.0.jar            # Enhanced JPEG support  
    ├── imageio-tiff-3.12.0.jar            # Extended TIFF support
    ├── imageio-bmp-3.12.0.jar             # BMP support
    ├── imageio-metadata-3.12.0.jar        # Metadata handling
    └── [165 other dependencies...]          # ImageJ, JSON, etc.
```

**Runtime JAR Injection:**
- `EnhancedImageJLauncher` automatically discovers and injects all JARs from `lib/` directory
- Works in both app bundle and development environments
- Provides detailed logging of injection process
- Graceful fallback for different classloader types

**Build Process:**
```bash
# Build and package modularly
./autodense/packaging/scripts/build_and_package.sh

# Verification
./AutoDense.app/Contents/Resources/java/verify_classpath.sh
```

**Benefits:**
- **Maintainable**: Easy to update individual dependencies
- **Debuggable**: Clear separation of AutoDense code from libraries  
- **Flexible**: Can swap out TwelveMonkeys versions independently
- **Efficient**: Only loads necessary JARs at runtime
- **Transparent**: Full visibility into dependency injection process

This architecture ensures reliable format support while maintaining clean separation between application logic and external libraries.

---

## Technical Integration

### Handle-Based Architecture
All workflows use AutoDense's handle-based system:
- **Images** referenced by handles (e.g., `img_abc123`)
- **Overlays** referenced by handles (e.g., `ov_def456`)  
- **Analysis Results** stored with unique identifiers
- **No pixel data** passed to AI - only structured results

### Canonical Tools Integration
Workflows map to the 8 core canonical tools:
1. `open_image` - Load gel/plate images
2. `preprocess` - Apply enhancements 
3. `detect_lanes` - Find gel lanes
4. `detect_bands` - Identify protein bands
5. `adjust_lanes` - Fine-tune positions
6. `quantify_bands` - Measure intensities
7. `render_overlay_png` - Export visualizations
8. `export_results` - Save data files

### Session Management
- **SessionStore** maintains all workflow state
- **SessionLogger** records all workflow steps for troubleshooting
- **SessionRecovery** handles errors and provides context refresh
- **Deterministic Results** - Same inputs always produce same outputs

---

## Troubleshooting

### Common Issues
- **Lane Detection Failures** - Try adjusting expected lane count or preprocessing
- **Missing Bands** - Increase sensitivity or check background method
- **Quantification Errors** - Verify background subtraction and integration methods
- **Export Problems** - Check file permissions and output directory

### Debug Information
All workflows generate detailed logs in `~/.autodense/logs/` including:
- Tool call parameters and results
- Execution times and success/failure status
- Error messages and recovery suggestions
- Complete conversation history with Gemini

### Getting Help
Use natural language to describe problems:
- "The lanes aren't detected correctly"
- "I need higher sensitivity for faint bands"
- "Export the results to Excel format"
- "Show me what went wrong with the analysis"

AutoDense will suggest parameter adjustments and provide recovery options based on the specific workflow and error conditions encountered.