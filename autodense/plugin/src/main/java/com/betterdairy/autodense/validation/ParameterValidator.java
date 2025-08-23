package com.betterdairy.autodense.validation;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.ArrayList;

/**
 * SINGLE-POINT PARAMETER CLAMPING for all AutoDense tools.
 * 
 * ARCHITECTURAL GUARDRAIL: All parameter validation and clamping happens here.
 * No Math.max/Math.min/clamping operations anywhere else in the codebase.
 * 
 * BENEFITS:
 * - Consistent parameter ranges across all tools
 * - Single place to update validation logic
 * - Comprehensive logging of parameter adjustments
 * - Type-safe parameter extraction with defaults
 */
public final class ParameterValidator {
    
    private final List<String> warnings = new ArrayList<>();
    
    public ParameterValidator() {}
    
    /**
     * Get validation warnings generated during parameter processing
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }
    
    /**
     * Clear accumulated warnings
     */
    public void clearWarnings() {
        warnings.clear();
    }
    
    // =============================================================================
    // GEL ANALYSIS PARAMETER VALIDATION
    // =============================================================================
    
    /**
     * Validate and clamp lane detection parameters
     */
    public static class LaneParams {
        public final int expectedLanes;
        public final double gridOffset;
        public final double laneWidth;
        public final double sensitivity;
        public final boolean constantSpacing;
        
        private LaneParams(int expectedLanes, double gridOffset, double laneWidth, 
                          double sensitivity, boolean constantSpacing) {
            this.expectedLanes = expectedLanes;
            this.gridOffset = gridOffset;
            this.laneWidth = laneWidth;
            this.sensitivity = sensitivity;
            this.constantSpacing = constantSpacing;
        }
    }
    
    public LaneParams validateLaneParams(JSONObject args, int imageWidth) {
        // Expected lanes: 2-20 (practical gel limits)
        int expectedLanes = args.optInt("expected_lanes", estimateDefaultLanes(imageWidth));
        int clampedLanes = clampInt(expectedLanes, 2, 20, "expected_lanes");
        
        // Grid offset: -0.5 to +0.5 (relative lane position adjustment)
        double gridOffset = args.optDouble("grid_offset", 0.0);
        double clampedOffset = clampDouble(gridOffset, -0.5, 0.5, "grid_offset");
        
        // Lane width: 0.1 to 2.0 (fraction of estimated lane width)
        double laneWidth = args.optDouble("lane_width", 0.55);
        double clampedWidth = clampDouble(laneWidth, 0.1, 2.0, "lane_width");
        
        // Sensitivity: 0.1 to 1.0 (peak detection threshold)
        double sensitivity = args.optDouble("sensitivity", 0.7);
        double clampedSensitivity = clampDouble(sensitivity, 0.1, 1.0, "sensitivity");
        
        boolean constantSpacing = args.optBoolean("constant_spacing", true);
        
        return new LaneParams(clampedLanes, clampedOffset, clampedWidth, 
                             clampedSensitivity, constantSpacing);
    }
    
    /**
     * Validate and clamp band detection parameters
     */
    public static class BandParams {
        public final double sensitivity;
        public final int minPeakDistance;
        public final double backgroundPercentile;
        public final String backgroundMethod;
        
        private BandParams(double sensitivity, int minPeakDistance, 
                          double backgroundPercentile, String backgroundMethod) {
            this.sensitivity = sensitivity;
            this.minPeakDistance = minPeakDistance;
            this.backgroundPercentile = backgroundPercentile;
            this.backgroundMethod = backgroundMethod;
        }
    }
    
    public BandParams validateBandParams(JSONObject args, int imageHeight) {
        // Sensitivity: 0.1 to 1.0 (lower = more sensitive)
        double sensitivity = args.optDouble("sensitivity", 0.7);
        double clampedSensitivity = clampDouble(sensitivity, 0.1, 1.0, "sensitivity");
        
        // Min peak distance: 2-50 pixels (prevents duplicate detection)
        int minPeakDistance = args.optInt("min_peak_distance", Math.max(2, imageHeight / 100));
        int clampedDistance = clampInt(minPeakDistance, 2, 50, "min_peak_distance");
        
        // Background percentile: 5-95% (for baseline subtraction)
        double backgroundPercentile = args.optDouble("background_percentile", 10.0);
        double clampedPercentile = clampDouble(backgroundPercentile, 5.0, 95.0, "background_percentile");
        
        // Background method: validated against allowed values
        String backgroundMethod = args.optString("background_method", "median");
        String validatedMethod = validateStringChoice(backgroundMethod, 
            List.of("none", "median", "gaussian", "rolling"), "background_method");
        
        return new BandParams(clampedSensitivity, clampedDistance, 
                             clampedPercentile, validatedMethod);
    }
    
    // =============================================================================
    // COLONY ANALYSIS PARAMETER VALIDATION  
    // =============================================================================
    
    /**
     * Validate and clamp colony detection parameters
     */
    public static class ColonyParams {
        public final int minColonySize;
        public final int maxColonySize;
        public final double sensitivity;
        public final String edgeDetection;
        public final List<String> colorGroups;
        
        private ColonyParams(int minColonySize, int maxColonySize, double sensitivity,
                           String edgeDetection, List<String> colorGroups) {
            this.minColonySize = minColonySize;
            this.maxColonySize = maxColonySize;
            this.sensitivity = sensitivity;
            this.edgeDetection = edgeDetection;
            this.colorGroups = colorGroups;
        }
    }
    
    public ColonyParams validateColonyParams(JSONObject args, double pixelsPerMM) {
        // Colony size in pixels (based on typical colony sizes in mm)
        double minSizeMM = args.optDouble("min_colony_size_mm", 0.1);
        double maxSizeMM = args.optDouble("max_colony_size_mm", 10.0);
        
        // Clamp sizes to reasonable biological limits
        double clampedMinMM = clampDouble(minSizeMM, 0.05, 2.0, "min_colony_size_mm");
        double clampedMaxMM = clampDouble(maxSizeMM, 1.0, 25.0, "max_colony_size_mm");
        
        // Convert to pixels
        int minColonySize = (int) Math.max(3, clampedMinMM * pixelsPerMM);
        int maxColonySize = (int) Math.min(1000, clampedMaxMM * pixelsPerMM);
        
        // Sensitivity: 0.1 to 1.0
        double sensitivity = args.optDouble("sensitivity", 0.8);
        double clampedSensitivity = clampDouble(sensitivity, 0.1, 1.0, "sensitivity");
        
        // Edge detection method
        String edgeDetection = args.optString("edge_detection", "adaptive");
        String validatedEdge = validateStringChoice(edgeDetection,
            List.of("none", "sobel", "canny", "adaptive"), "edge_detection");
        
        // Color groups for classification
        JSONArray colorArray = args.optJSONArray("color_groups");
        List<String> colorGroups = new ArrayList<>();
        if (colorArray != null) {
            for (int i = 0; i < colorArray.length(); i++) {
                colorGroups.add(colorArray.getString(i));
            }
        } else {
            colorGroups = List.of("white", "pink", "red", "blue", "green", "yellow");
        }
        
        return new ColonyParams(minColonySize, maxColonySize, clampedSensitivity,
                               validatedEdge, colorGroups);
    }
    
    // =============================================================================
    // IMAGE PREPROCESSING PARAMETER VALIDATION
    // =============================================================================
    
    /**
     * Validate and clamp image preprocessing parameters
     */
    public static class PreprocessParams {
        public final double rotation;
        public final double contrast;
        public final double brightness;
        public final int gaussianRadius;
        public final boolean flipHorizontal;
        public final boolean flipVertical;
        
        private PreprocessParams(double rotation, double contrast, double brightness,
                               int gaussianRadius, boolean flipHorizontal, boolean flipVertical) {
            this.rotation = rotation;
            this.contrast = contrast;
            this.brightness = brightness;
            this.gaussianRadius = gaussianRadius;
            this.flipHorizontal = flipHorizontal;
            this.flipVertical = flipVertical;
        }
    }
    
    public PreprocessParams validatePreprocessParams(JSONObject args) {
        // Rotation: -45 to +45 degrees (practical limits for gel correction)
        double rotation = args.optDouble("rotation", 0.0);
        double clampedRotation = clampDouble(rotation, -45.0, 45.0, "rotation");
        
        // Contrast: 0.1 to 3.0 (multiplicative factor)
        double contrast = args.optDouble("contrast", 1.0);
        double clampedContrast = clampDouble(contrast, 0.1, 3.0, "contrast");
        
        // Brightness: -100 to +100 (additive offset)
        double brightness = args.optDouble("brightness", 0.0);
        double clampedBrightness = clampDouble(brightness, -100.0, 100.0, "brightness");
        
        // Gaussian radius: 0 to 10 pixels (0 = no smoothing)
        int gaussianRadius = args.optInt("gaussian_radius", 0);
        int clampedRadius = clampInt(gaussianRadius, 0, 10, "gaussian_radius");
        
        boolean flipHorizontal = args.optBoolean("flip_horizontal", false);
        boolean flipVertical = args.optBoolean("flip_vertical", false);
        
        return new PreprocessParams(clampedRotation, clampedContrast, clampedBrightness,
                                   clampedRadius, flipHorizontal, flipVertical);
    }
    
    // =============================================================================
    // UTILITY METHODS
    // =============================================================================
    
    private int clampInt(int value, int min, int max, String paramName) {
        if (value < min || value > max) {
            warnings.add(String.format("Parameter '%s' clamped from %d to %d (range: %d-%d)", 
                paramName, value, Math.max(min, Math.min(max, value)), min, max));
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    private double clampDouble(double value, double min, double max, String paramName) {
        if (value < min || value > max) {
            warnings.add(String.format("Parameter '%s' clamped from %.2f to %.2f (range: %.2f-%.2f)", 
                paramName, value, Math.max(min, Math.min(max, value)), min, max));
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    private String validateStringChoice(String value, List<String> allowed, String paramName) {
        if (!allowed.contains(value)) {
            String defaultChoice = allowed.get(0);
            warnings.add(String.format("Parameter '%s' invalid value '%s', using '%s' (allowed: %s)", 
                paramName, value, defaultChoice, String.join(", ", allowed)));
            return defaultChoice;
        }
        return value;
    }
    
    private int estimateDefaultLanes(int imageWidth) {
        // Estimate lane count based on image width (typical gel lane width ~60px)
        return Math.max(2, Math.min(20, imageWidth / 60));
    }
}