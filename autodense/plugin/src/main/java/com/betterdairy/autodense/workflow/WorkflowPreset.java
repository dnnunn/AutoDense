package com.betterdairy.autodense.workflow;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;

/**
 * Workflow preset for reusable gel/plate analysis configurations.
 * 
 * Enables users to save their common lab protocols as presets that can be
 * quickly applied to new images with natural language commands.
 * 
 * Examples:
 * - "12-lane SDS-PAGE with MW marker in lane 1"
 * - "15-lane protein gel, high sensitivity"
 * - "X-gal blue/white screening plate"
 */
public class WorkflowPreset {
    
    private String name;
    private String description;
    private WorkflowType type;
    private JSONObject parameters;
    private List<String> naturalLanguageExamples;
    private Instant created;
    private Instant lastUsed;
    private int useCount;
    
    public enum WorkflowType {
        GEL_ANALYSIS("Gel Analysis"),
        PROTEIN_QUANTIFICATION("Protein Quantification"),
        COMPARATIVE_ANALYSIS("Comparative Analysis"),
        PCR_ANALYSIS("PCR Analysis"),
        PLATE_ANALYSIS("Plate Analysis"),
        COLONY_COUNTING("Colony Counting"),
        CUSTOM("Custom Workflow");
        
        private final String displayName;
        
        WorkflowType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public WorkflowPreset(String name, String description, WorkflowType type) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.parameters = new JSONObject();
        this.naturalLanguageExamples = new ArrayList<>();
        this.created = Instant.now();
        this.lastUsed = null;
        this.useCount = 0;
    }
    
    // Factory methods for common presets
    public static WorkflowPreset createMolecularWeightDetermination() {
        WorkflowPreset preset = new WorkflowPreset(
            "Molecular Weight Determination",
            "MW analysis for protein or DNA gels with standard curve calibration and range estimation",
            WorkflowType.GEL_ANALYSIS
        );
        
        // Set default gel parameters (format will be determined dynamically)
        NuPAGEFormat defaultFormat = NuPAGEFormat.createMini12Well();
        JSONObject formatParams = defaultFormat.toWorkflowParameters();
        JSONObject detectionParams = defaultFormat.getLaneDetectionParameters();
        JSONObject analysisParams = defaultFormat.getAnalysisParameters("comparative");  // 66% width for MW determination
        
        // Copy format parameters
        for (String key : formatParams.keySet()) {
            preset.parameters.put(key, formatParams.get(key));
        }
        for (String key : detectionParams.keySet()) {
            preset.parameters.put(key, detectionParams.get(key));
        }
        for (String key : analysisParams.keySet()) {
            preset.parameters.put(key, analysisParams.get(key));
        }
        
        // MW determination specific parameters
        preset.parameters
            .put("auto_detect_gel_type", true)  // Detect protein vs DNA automatically
            .put("gel_type_detection_method", "brightness_analysis")  // Bright bands on dark vs dark on bright
            .put("mw_standards_required", true)  // Must have MW standards
            .put("mw_marker_lane", "prompt_user")  // Ask user which lane has standards
            .put("mw_standards_input", "prompt_user")  // Ask for standard molecular weights
            .put("create_mw_calibration_curve", true)
            .put("mw_curve_fitting", "polynomial")  // Polynomial fit for MW vs migration distance
            .put("mw_curve_degree", 2)  // 2nd degree polynomial (log-linear relationship)
            .put("calculate_mw_uncertainty", true)  // Calculate +/- range for unknowns
            .put("uncertainty_method", "curve_confidence")  // Base uncertainty on curve fit quality
            .put("band_assist_available", true)  // Enable BandAssist functionality
            .put("band_assist_mode", "chat_toggle")  // Enable via chat command
            .put("export_mw_csv", true)
            .put("export_annotated_png", true)
            .put("export_dialog", true)  // Show save dialog for exports
            .put("annotate_bands_with_mw", true)  // Mark bands with MW on image
            .put("workflow_analysis_type", "comparative")  // Use 66% lane width
            .put("gel_format_selectable", true)
            .put("default_gel_format", "Auto-detect based on gel type");
        
        // Natural language examples for MW determination
        preset.naturalLanguageExamples.add("Determine molecular weights using standards in lane 1");
        preset.naturalLanguageExamples.add("Create MW calibration curve and assign weights to unknowns");
        preset.naturalLanguageExamples.add("This protein gel has ladder in first lane - calculate MWs");
        preset.naturalLanguageExamples.add("DNA gel with 1kb ladder - determine fragment sizes");
        preset.naturalLanguageExamples.add("Enable BandAssist for band selection across lanes");
        preset.naturalLanguageExamples.add("Export results with MW assignments and uncertainty ranges");
        preset.naturalLanguageExamples.add("Show calibration curve and mark bands with molecular weights");
        preset.naturalLanguageExamples.add("Lane 3 has protein ladder: 250, 150, 100, 75, 50, 37, 25, 20, 15, 10 kDa");
        
        return preset;
    }
    
    public static WorkflowPreset createXgalColonyScreen() {
        WorkflowPreset preset = new WorkflowPreset(
            "X-gal Blue/White Colony Screening",
            "Bacterial transformation plates with X-gal blue/white selection",
            WorkflowType.COLONY_COUNTING
        );
        
        preset.parameters
            .put("classify_colonies", true)
            .put("classification_type", "xgal")
            .put("color_groups", new JSONArray(List.of("blue", "white", "mixed")))
            .put("min_colony_size_mm", 0.2)
            .put("max_colony_size_mm", 8.0)
            .put("sensitivity", 0.8)
            .put("use_three_pass_filtering", true)
            .put("blue_enhancement_factor", 1.5)
            .put("rg_suppression_factor", 0.7)
            .put("blue_threshold", 120)
            .put("white_balance_threshold", 30)
            .put("apply_median_filter", true)
            .put("median_filter_radius", 2)
            .put("format_normalization", "preserve_color_channels")
            .put("support_heic_images", true);
        
        preset.naturalLanguageExamples.add("Count blue and white colonies separately");
        preset.naturalLanguageExamples.add("Classify colonies by X-gal reaction");
        preset.naturalLanguageExamples.add("Show me transformation efficiency");
        preset.naturalLanguageExamples.add("Export colony data with positions and colors");
        preset.naturalLanguageExamples.add("Analyze iPhone HEIC image of transformation plate");
        preset.naturalLanguageExamples.add("Use enhanced blue filtering for faint colonies");
        preset.naturalLanguageExamples.add("Apply three-pass color filtering for better discrimination");
        
        return preset;
    }
    
    public static WorkflowPreset createGrowthQuantification() {
        WorkflowPreset preset = new WorkflowPreset(
            "Colony Growth Analysis",
            "Time-series colony growth analysis with plate matching and morphology tracking",
            WorkflowType.COLONY_COUNTING
        );
        
        preset.parameters
            .put("measure_sizes", true)
            .put("size_grouping", true)
            .put("size_bins", new JSONArray(List.of(0.5, 1.0, 2.0, 4.0)))  // Size bins in mm
            .put("min_colony_size_mm", 0.1)
            .put("max_colony_size_mm", 15.0)
            .put("statistical_analysis", true)
            .put("time_series_analysis", true)
            .put("plate_alignment_method", "feature_matching")  // "feature_matching" or "orientation_marks"
            .put("track_morphology", true)
            .put("measure_circularity", true)
            .put("measure_texture", true)
            .put("xgal_blueness_analysis", false)  // User configurable
            .put("growth_rate_calculation", true)
            .put("max_time_points", 20)
            .put("alignment_tolerance_px", 5)
            .put("colony_matching_threshold", 0.8);
        
        preset.naturalLanguageExamples.add("Track colony growth over multiple time points");
        preset.naturalLanguageExamples.add("Align plates using orientation marks");
        preset.naturalLanguageExamples.add("Measure colony size and morphology changes");
        preset.naturalLanguageExamples.add("Analyze X-gal blueness development over time");
        preset.naturalLanguageExamples.add("Calculate growth rates for each colony");
        preset.naturalLanguageExamples.add("Match colonies across different imaging sessions");
        preset.naturalLanguageExamples.add("Correct for plate rotation and skewing");
        preset.naturalLanguageExamples.add("Export time-series growth data");
        
        return preset;
    }
    
    public static WorkflowPreset createProteinQuantification() {
        WorkflowPreset preset = new WorkflowPreset(
            "Protein Quantification with Standards",
            "SDS-PAGE gel with protein standards for quantitative analysis using calibration curves",
            WorkflowType.PROTEIN_QUANTIFICATION
        );
        
        // Set gel-specific parameters using NuPAGE format for quantitative analysis
        NuPAGEFormat nupageQuant = NuPAGEFormat.createMini12Well();
        JSONObject formatParamsQuant = nupageQuant.toWorkflowParameters();
        JSONObject detectionParamsQuant = nupageQuant.getLaneDetectionParameters();
        JSONObject analysisParamsQuant = nupageQuant.getAnalysisParameters("quantitative");
        
        // Copy NuPAGE format parameters
        for (String key : formatParamsQuant.keySet()) {
            preset.parameters.put(key, formatParamsQuant.get(key));
        }
        for (String key : detectionParamsQuant.keySet()) {
            preset.parameters.put(key, detectionParamsQuant.get(key));
        }
        for (String key : analysisParamsQuant.keySet()) {
            preset.parameters.put(key, analysisParamsQuant.get(key));
        }
        
        // Parameters for protein quantification workflow
        preset.parameters
            .put("standard_lanes", new JSONArray(List.of(1, 2)))  // Lanes with known amounts
            .put("enable_calibration", true)
            .put("calibration_method", "linear")
            .put("auto_linear_range", true)  // Find best R² window
            .put("robust_fit", true)  // Use Huber regression
            .put("extrapolate", true)  // Allow out-of-range estimates with flags
            .put("confidence_intervals", true)
            .put("integration_method", "trapezoid")
            .put("export_excel", true)
            .put("min_snr", 3.0)  // Minimum SNR for standards
            .put("min_points_fit", 5)  // Minimum points for linear window
            .put("workflow_analysis_type", "quantitative")  // Specify analysis type
            .put("gel_format_selectable", true)
            .put("default_gel_format", "NuPAGE Mini 12-well");
        
        // Natural language examples focused on quantification
        preset.naturalLanguageExamples.add("Quantify protein bands using standards in lanes 1-2");
        preset.naturalLanguageExamples.add("Create calibration curve from known amounts: lane 1 has 2,4,8 µg, lane 2 has 2,4,8 µg");
        preset.naturalLanguageExamples.add("Apply standard curve to unknown samples and flag out-of-range values");
        preset.naturalLanguageExamples.add("Export results to Excel with standards table, curve, and quantified amounts");
        preset.naturalLanguageExamples.add("Show confidence intervals for protein concentrations");
        preset.naturalLanguageExamples.add("Use robust fitting to exclude outlier bands from calibration");
        preset.naturalLanguageExamples.add("Find best linear range automatically for highest R-squared");
        
        return preset;
    }
    
    public static WorkflowPreset createCompareLanes() {
        WorkflowPreset preset = new WorkflowPreset(
            "Compare Lanes",
            "Statistical comparison of densitometry profiles between gel lanes with significance testing",
            WorkflowType.COMPARATIVE_ANALYSIS
        );
        
        // Set gel-specific parameters using NuPAGE format for comparative analysis  
        NuPAGEFormat nupageComp = NuPAGEFormat.createMini12Well();
        JSONObject formatParamsComp = nupageComp.toWorkflowParameters();
        JSONObject detectionParamsComp = nupageComp.getLaneDetectionParameters();
        JSONObject analysisParamsComp = nupageComp.getAnalysisParameters("comparative");
        
        // Copy NuPAGE format parameters
        for (String key : formatParamsComp.keySet()) {
            preset.parameters.put(key, formatParamsComp.get(key));
        }
        for (String key : detectionParamsComp.keySet()) {
            preset.parameters.put(key, detectionParamsComp.get(key));
        }
        for (String key : analysisParamsComp.keySet()) {
            preset.parameters.put(key, analysisParamsComp.get(key));
        }
        
        // Parameters for comparative lane analysis
        preset.parameters
            .put("mw_marker_lane", 1)  // Usually first lane
            .put("enable_mw_calibration", true)
            .put("generate_profiles", true)  // Generate densitometry profiles
            .put("statistical_comparison", true)
            .put("significance_test", "t_test")  // t-test for peak differences
            .put("significance_threshold", 0.05)  // p < 0.05
            .put("multiple_comparison_correction", "bonferroni")  // Adjust for multiple tests
            .put("reference_lane", "prompt_user")  // Ask user which lane to compare against
            .put("comparison_method", "pairwise")  // Compare each lane to reference
            .put("peak_detection_method", "local_maxima")
            .put("peak_matching_tolerance", 0.02)  // 2% Rf tolerance for peak matching
            .put("overlay_significant_changes", true)
            .put("green_for_increase", true)  // Green = more signal
            .put("red_for_decrease", true)   // Red = less signal
            .put("profile_smoothing", true)  // Smooth profiles for comparison
            .put("export_profiles", true)    // Export profile data
            .put("export_statistics", true)  // Export statistical results
            .put("workflow_analysis_type", "comparative")  // Specify analysis type
            .put("gel_format_selectable", true)
            .put("default_gel_format", "NuPAGE Mini 12-well");
        
        // Natural language examples for comparative analysis
        preset.naturalLanguageExamples.add("Compare all lanes to lane 3 and show significant differences");
        preset.naturalLanguageExamples.add("Generate densitometry profiles and compare lanes statistically");
        preset.naturalLanguageExamples.add("Mark increased bands in green and decreased bands in red");
        preset.naturalLanguageExamples.add("Show me which protein bands changed significantly between treatments");
        preset.naturalLanguageExamples.add("Compare lanes with p < 0.05 significance and MW assignments");
        preset.naturalLanguageExamples.add("Use lane 1 as MW standard and compare lanes 2-12 to lane 5");
        preset.naturalLanguageExamples.add("Apply multiple comparison correction and export statistical results");
        preset.naturalLanguageExamples.add("Generate comparative overlay showing only significant changes");
        
        return preset;
    }
    
    public static WorkflowPreset createSemiQuantitativePCR() {
        WorkflowPreset preset = new WorkflowPreset(
            "Semi-Quantitative PCR",
            "EtBr gel analysis with dual-lane normalization for semi-quantitative PCR results",
            WorkflowType.PCR_ANALYSIS
        );
        
        // Set gel-specific parameters using NuPAGE format for quantitative PCR analysis
        // Note: PCR gels may be different but we'll use NuPAGE as default with custom option
        NuPAGEFormat nupagePCR = NuPAGEFormat.createMini12Well();
        JSONObject formatParamsPCR = nupagePCR.toWorkflowParameters();
        JSONObject detectionParamsPCR = nupagePCR.getLaneDetectionParameters();
        JSONObject analysisParamsPCR = nupagePCR.getAnalysisParameters("quantitative");
        
        // Copy NuPAGE format parameters (will be overridden for EtBr-specific settings)
        for (String key : formatParamsPCR.keySet()) {
            preset.parameters.put(key, formatParamsPCR.get(key));
        }
        for (String key : detectionParamsPCR.keySet()) {
            preset.parameters.put(key, detectionParamsPCR.get(key));
        }
        for (String key : analysisParamsPCR.keySet()) {
            preset.parameters.put(key, analysisParamsPCR.get(key));
        }
        
        // Parameters for semi-quantitative PCR analysis
        preset.parameters
            .put("gel_type", "etbr")  // Ethidium bromide gel
            .put("dual_lane_layout", true)  // Upper experimental, lower control
            .put("expected_sample_pairs", 12)  // Default 12 sample pairs (24 lanes total)
            .put("lane_pairing", "vertical")  // Upper/lower lane pairing
            .put("experimental_row", "upper")  // Experimental PCR in upper lanes
            .put("control_row", "lower")  // Control PCR in lower lanes
            .put("normalization_method", "control_ratio")  // Ratio of experimental/control
            .put("mw_marker_present", false)  // May or may not have MW markers
            .put("mw_marker_lane", "prompt_if_present")  // Ask user if MW markers are visible
            .put("blank_detection", true)  // Detect blank/empty lanes
            .put("blank_threshold", 0.1)  // Signal threshold for "not detectable"
            .put("percentile_highlighting", true)
            .put("highlight_percentile", 90)  // Highlight 90th percentile values
            .put("sensitivity", 0.6)  // Lower sensitivity for EtBr bands (override NuPAGE)
            .put("background_method", "median")  // Median background for fluorescent gels (override)
            .put("band_integration", "total_intensity")  // Sum all signal in band region
            .put("export_csv", true)
            .put("sample_naming", "prompt_user")  // Allow custom sample names
            .put("include_raw_values", true)  // Include raw intensities in export
            .put("quality_flags", true)  // Flag low-quality measurements
            .put("workflow_analysis_type", "quantitative")  // Specify analysis type
            .put("gel_format_selectable", true)
            .put("default_gel_format", "NuPAGE Mini 12-well");
        
        // Natural language examples for PCR analysis
        preset.naturalLanguageExamples.add("Analyze this EtBr gel with experimental lanes on top, control lanes below");
        preset.naturalLanguageExamples.add("Quantify PCR products using control normalization and highlight top 10% values");
        preset.naturalLanguageExamples.add("Calculate ratios of experimental to control PCR signals");
        preset.naturalLanguageExamples.add("Mark blank lanes as 'not detectable' and export results to CSV");
        preset.naturalLanguageExamples.add("Use 12 sample pairs with upper experimental and lower control lanes");
        preset.naturalLanguageExamples.add("Highlight values in 90th percentile and provide sample names");
        preset.naturalLanguageExamples.add("Normalize PCR band intensities against corresponding control reactions");
        preset.naturalLanguageExamples.add("Export semi-quantitative PCR results with quality flags");
        
        return preset;
    }
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public WorkflowType getType() { return type; }
    public void setType(WorkflowType type) { this.type = type; }
    
    public JSONObject getParameters() { return parameters; }
    public void setParameters(JSONObject parameters) { this.parameters = parameters; }
    
    public List<String> getNaturalLanguageExamples() { return new ArrayList<>(naturalLanguageExamples); }
    public void setNaturalLanguageExamples(List<String> examples) { 
        this.naturalLanguageExamples = new ArrayList<>(examples); 
    }
    
    public void addNaturalLanguageExample(String example) {
        this.naturalLanguageExamples.add(example);
    }
    
    public Instant getCreated() { return created; }
    public Instant getLastUsed() { return lastUsed; }
    public int getUseCount() { return useCount; }
    
    public void recordUse() {
        this.lastUsed = Instant.now();
        this.useCount++;
    }
    
    // Serialization for storage
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("description", description);
        json.put("type", type.name());
        json.put("parameters", parameters);
        json.put("naturalLanguageExamples", new JSONArray(naturalLanguageExamples));
        json.put("created", created.toString());
        if (lastUsed != null) {
            json.put("lastUsed", lastUsed.toString());
        }
        json.put("useCount", useCount);
        return json;
    }
    
    public static WorkflowPreset fromJSON(JSONObject json) {
        WorkflowPreset preset = new WorkflowPreset(
            json.getString("name"),
            json.getString("description"),
            WorkflowType.valueOf(json.getString("type"))
        );
        
        preset.parameters = json.getJSONObject("parameters");
        
        JSONArray examplesArray = json.getJSONArray("naturalLanguageExamples");
        for (int i = 0; i < examplesArray.length(); i++) {
            preset.naturalLanguageExamples.add(examplesArray.getString(i));
        }
        
        preset.created = Instant.parse(json.getString("created"));
        if (json.has("lastUsed") && !json.isNull("lastUsed")) {
            preset.lastUsed = Instant.parse(json.getString("lastUsed"));
        }
        preset.useCount = json.optInt("useCount", 0);
        
        return preset;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s", name, type.getDisplayName(), description);
    }
}