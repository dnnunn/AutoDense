package com.betterdairy.autodense.workflow;

import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

/**
 * Defines BioRad agarose gel formats with dimensions from Bio-Rad manuals.
 * 
 * Supports Bio-Rad Sub-Cell GT & Mini-Sub GT agarose gel systems.
 * Lane width is set to 66% of total well width for detection, 100% for quantification.
 * 
 * Based on Bio-Rad Sub-Cell GT & Mini-Sub GT specifications:
 * - Mini-Sub GT: 7×10 cm tray → ~90mm usable width
 * - Wide Mini-Sub GT: 15×7 cm tray → ~130mm usable width
 */
public class BioRadFormat {
    
    private final String name;
    private final String description;
    private final int wellCount;
    private final double totalWidthMm;
    private final double totalHeightMm;
    private final double wellWidthMm;
    private final double laneDetectionWidthMm;  // 66% of well width for lane finding
    private final double fullLaneWidthMm;       // 100% of well width for quantification
    private final String gelType;
    
    private BioRadFormat(String name, String description, double totalWidthMm, double totalHeightMm, 
                        int wellCount, String gelType) {
        this.name = name;
        this.description = description;
        this.wellCount = wellCount;
        this.totalWidthMm = totalWidthMm;
        this.totalHeightMm = totalHeightMm;
        this.wellWidthMm = totalWidthMm / wellCount;
        this.laneDetectionWidthMm = wellWidthMm * 0.66;  // 66% for lane detection (avoids edge effects)
        this.fullLaneWidthMm = wellWidthMm;               // 100% for quantitative analysis
        this.gelType = gelType;
    }
    
    // Bio-Rad Mini-Sub GT formats (7×10 cm tray, ~90mm usable width)
    
    public static BioRadFormat createMiniSubGT8Well() {
        return new BioRadFormat(
            "Bio-Rad Mini-Sub GT 8-well",
            "Mini-Sub GT agarose gel, 8 wells, ~11mm per well",
            90.0, 60.0, 8, "agarose"  // 90mm usable width from manual
        );
    }
    
    public static BioRadFormat createMiniSubGT10Well() {
        return new BioRadFormat(
            "Bio-Rad Mini-Sub GT 10-well", 
            "Mini-Sub GT agarose gel, 10 wells, ~9mm per well",
            90.0, 60.0, 10, "agarose"  // 90mm usable width from manual
        );
    }
    
    public static BioRadFormat createMiniSubGT15Well() {
        return new BioRadFormat(
            "Bio-Rad Mini-Sub GT 15-well",
            "Mini-Sub GT agarose gel, 15 wells, ~6mm per well",
            90.0, 60.0, 15, "agarose"  // 90mm usable width from manual
        );
    }
    
    // Bio-Rad Wide Mini-Sub GT formats (15×7 cm tray, ~130mm usable width)
    
    public static BioRadFormat createWideMiniSubGT15Well() {
        return new BioRadFormat(
            "Bio-Rad Wide Mini-Sub GT 15-well",
            "Wide Mini-Sub GT agarose gel, 15 wells, ~8.5mm per well",
            130.0, 60.0, 15, "agarose"  // 130mm usable width from manual
        );
    }
    
    public static BioRadFormat createWideMiniSubGT20Well() {
        return new BioRadFormat(
            "Bio-Rad Wide Mini-Sub GT 20-well",
            "Wide Mini-Sub GT agarose gel, 20 wells, ~6.5mm per well",
            130.0, 60.0, 20, "agarose"  // 130mm usable width from manual
        );
    }
    
    public static BioRadFormat createWideMiniSubGT25Well() {
        return new BioRadFormat(
            "Bio-Rad Wide Mini-Sub GT 25-well",
            "Wide Mini-Sub GT agarose gel, 25 wells, ~5.2mm per well",
            130.0, 60.0, 25, "agarose"  // 130mm usable width from manual
        );
    }
    
    /**
     * Create a custom BioRad format with measured dimensions
     * Use this method when you have exact comb measurements
     */
    public static BioRadFormat createCustom(String name, String description, 
                                           double widthMm, double heightMm, 
                                           int wellCount) {
        return new BioRadFormat(name, description, widthMm, heightMm, wellCount, "agarose");
    }
    
    /**
     * Get all standard Bio-Rad formats based on manual specifications
     */
    public static List<BioRadFormat> getAllStandardFormats() {
        List<BioRadFormat> formats = new ArrayList<>();
        
        // Mini-Sub GT formats (90mm usable width)
        formats.add(createMiniSubGT8Well());
        formats.add(createMiniSubGT10Well());
        formats.add(createMiniSubGT15Well());
        
        // Wide Mini-Sub GT formats (130mm usable width)
        formats.add(createWideMiniSubGT15Well());
        formats.add(createWideMiniSubGT20Well());
        formats.add(createWideMiniSubGT25Well());
        
        return formats;
    }
    
    /**
     * Convert to JSON for workflow parameters
     */
    public JSONObject toWorkflowParameters() {
        JSONObject params = new JSONObject();
        params.put("gel_format", name);
        params.put("gel_type", gelType);
        params.put("expected_lanes", wellCount);
        params.put("gel_width_mm", totalWidthMm);
        params.put("gel_height_mm", totalHeightMm);
        params.put("well_width_mm", wellWidthMm);
        params.put("lane_detection_width_mm", laneDetectionWidthMm);
        params.put("full_lane_width_mm", fullLaneWidthMm);
        params.put("lane_detection_fraction", 0.66);  // 66% for detection
        params.put("full_lane_fraction", 1.0);        // 100% for quantification
        params.put("constant_spacing", true);
        params.put("size_category", "agarose");
        return params;
    }
    
    /**
     * Get analysis width parameters for specific workflow types
     */
    public JSONObject getAnalysisParameters(String workflowType) {
        JSONObject params = new JSONObject();
        
        switch (workflowType) {
            case "comparative", "compare_lanes", "mw_determination" -> {
                // Use 66% width for comparative analysis to avoid edge artifacts
                params.put("analysis_width_mm", laneDetectionWidthMm);
                params.put("analysis_width_fraction", 0.66);
                params.put("analysis_rationale", "66% width avoids edge artifacts for fair comparison");
                params.put("use_full_lane", false);
            }
            case "quantitative", "semi_quantitative_pcr", "dna_quantification" -> {
                // Use full width for quantitative analysis to capture complete signal
                params.put("analysis_width_mm", fullLaneWidthMm);
                params.put("analysis_width_fraction", 1.0);
                params.put("analysis_rationale", "Full width captures complete band signal for accurate quantification");
                params.put("use_full_lane", true);
            }
            default -> {
                // Default to detection width for general analysis
                params.put("analysis_width_mm", laneDetectionWidthMm);
                params.put("analysis_width_fraction", 0.66);
                params.put("analysis_rationale", "66% width provides robust analysis for most cases");
                params.put("use_full_lane", false);
            }
        }
        
        return params;
    }
    
    /**
     * Get lane detection parameters optimized for agarose gels
     */
    public JSONObject getLaneDetectionParameters() {
        JSONObject params = new JSONObject();
        
        // Lane detection sensitivity for agarose gels (generally lower than SDS-PAGE)
        double sensitivity;
        if (wellCount <= 12) {
            sensitivity = 0.6;  // Lower sensitivity for EtBr/SYBR staining
        } else if (wellCount <= 15) {
            sensitivity = 0.7;  // Higher sensitivity for narrower wells
        } else {
            sensitivity = 0.8;  // Very high sensitivity for very narrow wells
        }
        
        params.put("sensitivity", sensitivity);
        params.put("min_lane_width_px", Math.max(8, laneDetectionWidthMm * 2)); // Estimate 2px/mm
        params.put("max_lane_width_px", laneDetectionWidthMm * 4); // Allow some variation
        params.put("lane_spacing_tolerance", 0.2); // 20% tolerance (more flexible than SDS-PAGE)
        
        // Background method optimized for fluorescent agarose gels
        params.put("background_method", "gaussian"); // Better for fluorescent backgrounds
        
        return params;
    }
    
    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getWellCount() { return wellCount; }
    public double getTotalWidthMm() { return totalWidthMm; }
    public double getTotalHeightMm() { return totalHeightMm; }
    public double getWellWidthMm() { return wellWidthMm; }
    public double getLaneDetectionWidthMm() { return laneDetectionWidthMm; }
    public double getFullLaneWidthMm() { return fullLaneWidthMm; }
    public String getGelType() { return gelType; }
    
    @Override
    public String toString() {
        return String.format("%s (%d wells, %.1fmm detection/%.1fmm full)", 
                           name, wellCount, laneDetectionWidthMm, fullLaneWidthMm);
    }
    
    /**
     * Create BioRad format from name (for dropdown selection)
     */
    public static BioRadFormat fromName(String formatName) {
        return switch (formatName) {
            case "Bio-Rad Mini-Sub GT 8-well" -> createMiniSubGT8Well();
            case "Bio-Rad Mini-Sub GT 10-well" -> createMiniSubGT10Well();
            case "Bio-Rad Mini-Sub GT 15-well" -> createMiniSubGT15Well();
            case "Bio-Rad Wide Mini-Sub GT 15-well" -> createWideMiniSubGT15Well();
            case "Bio-Rad Wide Mini-Sub GT 20-well" -> createWideMiniSubGT20Well();
            case "Bio-Rad Wide Mini-Sub GT 25-well" -> createWideMiniSubGT25Well();
            default -> createMiniSubGT10Well(); // Default fallback
        };
    }
    
    /**
     * Update format with exact measurements
     * Call this method when you have precise comb measurements
     */
    public static BioRadFormat updateWithMeasurements(String formatName, 
                                                     double exactWidthMm, 
                                                     double exactHeightMm,
                                                     int exactWellCount) {
        return new BioRadFormat(
            formatName.replace("PLACEHOLDER:", "MEASURED:"),
            formatName + " - Updated with exact measurements",
            exactWidthMm, exactHeightMm, exactWellCount, "agarose"
        );
    }
}