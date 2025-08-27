package com.betterdairy.autodense.analysis;

import org.json.JSONObject;

/**
 * Centralized parameter unit conversion and standardization utility.
 * 
 * Design principles:
 * 1. All parameters are converted to pixels at tool entry points
 * 2. Millimeter parameters are always converted using current plate calibration
 * 3. Clear parameter naming conventions with unit suffixes
 * 4. Validation and clamping to reasonable ranges
 * 5. Support for both legacy and new parameter formats
 */
public final class ParameterConverter {
    
    private ParameterConverter() {} // Utility class
    
    /**
     * Standardized colony detection parameters in pixels
     */
    public static record ColonyDetectionParams(
        int minDiameterPx,          // Minimum colony diameter in pixels
        int maxDiameterPx,          // Maximum colony diameter in pixels
        double minCircularity,      // Minimum circularity (0.0-1.0)
        double minSolidity,         // Minimum solidity (0.0-1.0)
        boolean splitTouching,      // Apply watershed separation
        boolean removeRimArtifacts, // Exclude edge particles
        double blueThreshold,       // CIELAB b* threshold for detection
        boolean useAdaptiveThreshold // Enable local threshold adaptation
    ) {
        
        /**
         * Create validated parameters with automatic unit conversion
         */
        public static ColonyDetectionParams fromArgs(JSONObject args, double pixelsPerMM) {
            // Handle both legacy and new parameter formats
            int minDiamPx = extractMinDiameter(args, pixelsPerMM);
            int maxDiamPx = extractMaxDiameter(args, pixelsPerMM);
            
            // Shape constraints with validation
            double minCirc = clamp(args.optDouble("min_circularity", 0.3), 0.0, 1.0);
            double minSol = clamp(args.optDouble("min_solidity", 0.5), 0.0, 1.0);
            
            // Processing options
            boolean split = args.optBoolean("split_touching", true);
            boolean removeRim = args.optBoolean("remove_rim_artifacts", true);
            
            // Color detection parameters
            double blueThresh = clamp(args.optDouble("blue_threshold", -6.0), -20.0, 5.0);
            boolean adaptive = args.optBoolean("use_adaptive_threshold", true);
            
            return new ColonyDetectionParams(
                minDiamPx, maxDiamPx, minCirc, minSol,
                split, removeRim, blueThresh, adaptive
            );
        }
        
        /**
         * Extract minimum diameter with unit conversion and fallback logic
         */
        private static int extractMinDiameter(JSONObject args, double pixelsPerMM) {
            // Priority 1: Explicit pixel parameter
            if (args.has("min_diameter_px") || args.has("min_diam_px")) {
                int pxValue = args.optInt("min_diameter_px", args.optInt("min_diam_px", 0));
                if (pxValue > 0) {
                    return clampDiameterPx(pxValue);
                }
            }
            
            // Priority 2: Millimeter parameter with conversion
            if (args.has("min_diameter_mm") || args.has("min_diam_mm")) {
                double mmValue = args.optDouble("min_diameter_mm", args.optDouble("min_diam_mm", 0.0));
                if (mmValue > 0.0) {
                    return clampDiameterPx((int) Math.round(mmValue * pixelsPerMM));
                }
            }
            
            // Priority 3: Use calibrated defaults based on resolution
            var defaults = PlateAnalysisDefaults.CalibrationHelper.recommendColonySizes(pixelsPerMM);
            return defaults.minDiameterPx();
        }
        
        /**
         * Extract maximum diameter with unit conversion and fallback logic
         */
        private static int extractMaxDiameter(JSONObject args, double pixelsPerMM) {
            // Priority 1: Explicit pixel parameter
            if (args.has("max_diameter_px") || args.has("max_diam_px")) {
                int pxValue = args.optInt("max_diameter_px", args.optInt("max_diam_px", 0));
                if (pxValue > 0) {
                    return clampDiameterPx(pxValue);
                }
            }
            
            // Priority 2: Millimeter parameter with conversion
            if (args.has("max_diameter_mm") || args.has("max_diam_mm")) {
                double mmValue = args.optDouble("max_diameter_mm", args.optDouble("max_diam_mm", 0.0));
                if (mmValue > 0.0) {
                    return clampDiameterPx((int) Math.round(mmValue * pixelsPerMM));
                }
            }
            
            // Priority 3: Use calibrated defaults based on resolution
            var defaults = PlateAnalysisDefaults.CalibrationHelper.recommendColonySizes(pixelsPerMM);
            return defaults.maxDiameterPx();
        }
        
        /**
         * Validate and clamp diameter values to reasonable pixel ranges
         */
        private static int clampDiameterPx(int diameterPx) {
            return clamp(diameterPx, 2, 500); // Reasonable range: 2-500 pixels
        }
    }
    
    /**
     * Standardized plate detection parameters
     */
    public static record PlateDetectionParams(
        double dishDiameterMM,      // Physical dish diameter in mm
        String thresholdMethod,     // ImageJ threshold method name
        boolean correctIllumination,// Apply illumination correction
        double rimExclusionMM       // Exclude rim artifacts in mm
    ) {
        
        public static PlateDetectionParams fromArgs(JSONObject args) {
            // Get dish diameter with fallbacks
            double dishMM = args.optDouble("dish_diameter_mm", 
                           args.optDouble("plate_diameter_mm", 90.0)); // Default to 90mm
            
            // Validate dish size (support common petri dish sizes)
            dishMM = clamp(dishMM, 35.0, 150.0); // 35mm to 150mm range
            
            String threshold = args.optString("threshold_method", "Triangle");
            boolean correctIllum = args.optBoolean("correct_illumination", false);
            double rimMM = clamp(args.optDouble("rim_exclusion_mm", 5.0), 0.0, 15.0);
            
            return new PlateDetectionParams(dishMM, threshold, correctIllum, rimMM);
        }
    }
    
    /**
     * Unit conversion utilities
     */
    public static class UnitConversion {
        
        /**
         * Convert millimeters to pixels using calibration
         */
        public static int mmToPx(double millimeters, double pixelsPerMM) {
            return (int) Math.round(millimeters * pixelsPerMM);
        }
        
        /**
         * Convert pixels to millimeters using calibration
         */
        public static double pxToMm(int pixels, double pixelsPerMM) {
            return pixels / pixelsPerMM;
        }
        
        /**
         * Convert diameter in mm to area in pixels
         */
        public static double diameterMmToAreaPx(double diameterMM, double pixelsPerMM) {
            double radiusPx = (diameterMM * pixelsPerMM) / 2.0;
            return Math.PI * radiusPx * radiusPx;
        }
        
        /**
         * Convert area in pixels to diameter in mm
         */
        public static double areaPxToDiameterMm(double areaPx, double pixelsPerMM) {
            double radiusPx = Math.sqrt(areaPx / Math.PI);
            return (2.0 * radiusPx) / pixelsPerMM;
        }
        
        /**
         * Estimate resolution from plate detection result
         */
        public static double estimateResolution(PlateDetector.Result plateResult, double knownDishDiameterMM) {
            return plateResult.pxPerMM(knownDishDiameterMM);
        }
    }
    
    /**
     * Parameter validation and error reporting
     */
    public static class Validation {
        
        public static class ValidationResult {
            public final boolean isValid;
            public final String errorMessage;
            public final String parameterName;
            
            public ValidationResult(boolean isValid, String errorMessage, String parameterName) {
                this.isValid = isValid;
                this.errorMessage = errorMessage;
                this.parameterName = parameterName;
            }
            
            public static ValidationResult valid() {
                return new ValidationResult(true, null, null);
            }
            
            public static ValidationResult invalid(String parameterName, String message) {
                return new ValidationResult(false, message, parameterName);
            }
        }
        
        /**
         * Validate colony detection parameters for reasonableness
         */
        public static ValidationResult validateColonyParams(ColonyDetectionParams params) {
            // Check diameter range
            if (params.minDiameterPx >= params.maxDiameterPx) {
                return ValidationResult.invalid("diameter_range", 
                    "Minimum diameter must be less than maximum diameter");
            }
            
            // Check if parameters are reasonable for biological colonies
            if (params.minDiameterPx < 2) {
                return ValidationResult.invalid("min_diameter_px", 
                    "Minimum diameter too small (< 2 pixels)");
            }
            
            if (params.maxDiameterPx > 500) {
                return ValidationResult.invalid("max_diameter_px", 
                    "Maximum diameter too large (> 500 pixels)");
            }
            
            // Check shape constraints
            if (params.minCircularity < 0.0 || params.minCircularity > 1.0) {
                return ValidationResult.invalid("min_circularity", 
                    "Circularity must be between 0.0 and 1.0");
            }
            
            if (params.minSolidity < 0.0 || params.minSolidity > 1.0) {
                return ValidationResult.invalid("min_solidity", 
                    "Solidity must be between 0.0 and 1.0");
            }
            
            return ValidationResult.valid();
        }
    }
    
    // Utility methods
    
    /**
     * Clamp integer value to range
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp double value to range
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}