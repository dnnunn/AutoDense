package com.betterdairy.autodense.workflow;

import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

/**
 * Defines NuPAGE gel formats with precise dimensions for lane detection.
 * 
 * Supports Invitrogen NuPAGE Mini and Midi gels with various well configurations.
 * Lane width is set to 66% of total well width to avoid edge effects.
 */
public class NuPAGEFormat {
    
    private final String name;
    private final String description;
    private final GelSize size;
    private final int wellCount;
    private final double totalWidthMm;
    private final double totalHeightMm;
    private final double wellWidthMm;
    private final double laneDetectionWidthMm;  // 66% of well width for lane finding
    private final double fullLaneWidthMm;       // 100% of well width for quantification
    private final String gelType;
    
    public enum GelSize {
        MINI("Mini", 80.0, 80.0),          // 8cm × 8cm
        MIDI("Midi", 80.0, 130.0);         // 8cm × 13cm
        
        private final String displayName;
        private final double widthMm;
        private final double heightMm;
        
        GelSize(String displayName, double widthMm, double heightMm) {
            this.displayName = displayName;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }
        
        public String getDisplayName() { return displayName; }
        public double getWidthMm() { return widthMm; }
        public double getHeightMm() { return heightMm; }
    }
    
    private NuPAGEFormat(String name, String description, GelSize size, int wellCount, String gelType) {
        this.name = name;
        this.description = description;
        this.size = size;
        this.wellCount = wellCount;
        this.totalWidthMm = size.getWidthMm();
        this.totalHeightMm = size.getHeightMm();
        this.wellWidthMm = totalWidthMm / wellCount;
        this.laneDetectionWidthMm = wellWidthMm * 0.66;  // 66% for lane detection (avoids edge effects)
        this.fullLaneWidthMm = wellWidthMm;               // 100% for quantitative analysis
        this.gelType = gelType;
    }
    
    // Factory methods for standard NuPAGE formats
    
    // Mini gel formats
    public static NuPAGEFormat createMini10Well() {
        return new NuPAGEFormat(
            "NuPAGE Mini 10-well",
            "Mini Bis-Tris/Bolt Plus gel, 10 wells, ~8mm well width",
            GelSize.MINI, 10, "bis_tris"
        );
    }
    
    public static NuPAGEFormat createMini12Well() {
        return new NuPAGEFormat(
            "NuPAGE Mini 12-well", 
            "Mini Bis-Tris/Bolt Plus gel, 12 wells, ~6.7mm well width",
            GelSize.MINI, 12, "bis_tris"
        );
    }
    
    public static NuPAGEFormat createMini15Well() {
        return new NuPAGEFormat(
            "NuPAGE Mini 15-well",
            "Mini Bis-Tris/Bolt Plus gel, 15 wells, ~5.3mm well width", 
            GelSize.MINI, 15, "bis_tris"
        );
    }
    
    public static NuPAGEFormat createMini17Well() {
        return new NuPAGEFormat(
            "NuPAGE Mini 17-well",
            "Mini Bis-Tris/Bolt Plus gel, 17 wells, ~4.7mm well width",
            GelSize.MINI, 17, "bis_tris"
        );
    }
    
    // Midi gel formats
    public static NuPAGEFormat createMidi12Plus2Well() {
        return new NuPAGEFormat(
            "NuPAGE Midi 12+2-well",
            "Midi gel, 12 regular + 2 extra wells, ~5.7mm well width",
            GelSize.MIDI, 14, "bis_tris"  // 12+2 = 14 total
        );
    }
    
    public static NuPAGEFormat createMidi20Well() {
        return new NuPAGEFormat(
            "NuPAGE Midi 20-well",
            "Midi gel, 20 wells, ~4mm well width",
            GelSize.MIDI, 20, "bis_tris"
        );
    }
    
    public static NuPAGEFormat createMidi26Well() {
        return new NuPAGEFormat(
            "NuPAGE Midi 26-well", 
            "Midi gel, 26 wells, ~3.1mm well width",
            GelSize.MIDI, 26, "bis_tris"
        );
    }
    
    // Tris-Acetate variants (for high MW proteins)
    public static NuPAGEFormat createMini12WellTrisAcetate() {
        return new NuPAGEFormat(
            "NuPAGE Mini 12-well Tris-Acetate",
            "Mini Tris-Acetate gel for high MW proteins, 12 wells",
            GelSize.MINI, 12, "tris_acetate"
        );
    }
    
    /**
     * Create a custom gel format with user-specified dimensions
     */
    public static NuPAGEFormat createCustom(String name, String description, 
                                           double widthMm, double heightMm, 
                                           int wellCount, String gelType) {
        // For custom formats, use a special constructor that doesn't rely on enum
        return new NuPAGEFormat(name, description, widthMm, heightMm, wellCount, gelType);
    }
    
    // Special constructor for custom formats
    private NuPAGEFormat(String name, String description, double widthMm, double heightMm, 
                        int wellCount, String gelType) {
        this.name = name;
        this.description = description;
        this.size = null;  // No enum for custom
        this.wellCount = wellCount;
        this.totalWidthMm = widthMm;
        this.totalHeightMm = heightMm;
        this.wellWidthMm = widthMm / wellCount;
        this.laneDetectionWidthMm = wellWidthMm * 0.66;  // 66% for lane detection (avoids edge effects)
        this.fullLaneWidthMm = wellWidthMm;               // 100% for quantitative analysis
        this.gelType = gelType;
    }
    
    /**
     * Get all standard NuPAGE formats
     */
    public static List<NuPAGEFormat> getAllStandardFormats() {
        List<NuPAGEFormat> formats = new ArrayList<>();
        
        // Mini formats
        formats.add(createMini10Well());
        formats.add(createMini12Well());
        formats.add(createMini15Well());
        formats.add(createMini17Well());
        formats.add(createMini12WellTrisAcetate());
        
        // Midi formats  
        formats.add(createMidi12Plus2Well());
        formats.add(createMidi20Well());
        formats.add(createMidi26Well());
        
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
        params.put("size_category", size != null ? size.getDisplayName().toLowerCase() : "custom");
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
            case "quantitative", "protein_quantification", "semi_quantitative_pcr" -> {
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
     * Get lane detection parameters optimized for this format
     */
    public JSONObject getLaneDetectionParameters() {
        JSONObject params = new JSONObject();
        
        // Lane detection sensitivity based on well count
        double sensitivity;
        if (wellCount <= 12) {
            sensitivity = 0.7;  // Standard sensitivity for wider wells
        } else if (wellCount <= 17) {
            sensitivity = 0.8;  // Higher sensitivity for narrower wells
        } else {
            sensitivity = 0.85; // Very high sensitivity for very narrow wells
        }
        
        params.put("sensitivity", sensitivity);
        params.put("min_lane_width_px", Math.max(10, laneDetectionWidthMm * 2)); // Estimate 2px/mm  
        params.put("max_lane_width_px", laneDetectionWidthMm * 4); // Allow some variation
        params.put("lane_spacing_tolerance", 0.15); // 15% tolerance for spacing variation
        
        // Background method based on gel type
        if ("tris_acetate".equals(gelType)) {
            params.put("background_method", "gaussian"); // Better for high MW gels
        } else {
            params.put("background_method", "median");   // Standard for Bis-Tris
        }
        
        return params;
    }
    
    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public GelSize getSize() { return size; }
    public int getWellCount() { return wellCount; }
    public double getTotalWidthMm() { return totalWidthMm; }
    public double getTotalHeightMm() { return totalHeightMm; }
    public double getWellWidthMm() { return wellWidthMm; }
    public double getLaneDetectionWidthMm() { return laneDetectionWidthMm; }
    public double getFullLaneWidthMm() { return fullLaneWidthMm; }
    public String getGelType() { return gelType; }
    
    @Override
    public String toString() {
        return String.format("%s (%d wells, %.1fmm detection/%.1fmm full)", name, wellCount, laneDetectionWidthMm, fullLaneWidthMm);
    }
    
    /**
     * Create NuPAGE format from name (for dropdown selection)
     */
    public static NuPAGEFormat fromName(String formatName) {
        return switch (formatName) {
            case "NuPAGE Mini 10-well" -> createMini10Well();
            case "NuPAGE Mini 12-well" -> createMini12Well();
            case "NuPAGE Mini 15-well" -> createMini15Well();
            case "NuPAGE Mini 17-well" -> createMini17Well();
            case "NuPAGE Mini 12-well Tris-Acetate" -> createMini12WellTrisAcetate();
            case "NuPAGE Midi 12+2-well" -> createMidi12Plus2Well();
            case "NuPAGE Midi 20-well" -> createMidi20Well();
            case "NuPAGE Midi 26-well" -> createMidi26Well();
            default -> createMini12Well(); // Default fallback
        };
    }
}