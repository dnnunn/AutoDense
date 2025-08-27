package com.betterdairy.autodense.validation;

import org.json.JSONObject;
import org.json.JSONArray;
import com.betterdairy.autodense.validation.InputValidator.ValidationException;

/**
 * Enhanced secure tool validation that replaces ToolSchemaValidator.
 * 
 * Provides backward compatibility while adding comprehensive security validation.
 * All new tools should use InputValidator.ToolValidator directly for better APIs.
 * 
 * Security improvements over original ToolSchemaValidator:
 * - Path traversal protection
 * - Handle format validation  
 * - Parameter type enforcement
 * - Range validation
 * - Input sanitization
 */
public final class SecureToolValidator {
    
    private SecureToolValidator() {} // Utility class
    
    /**
     * Validate required parameter exists (backward compatibility)
     */
    public static void require(JSONObject args, String key) {
        if (!args.has(key) || args.isNull(key)) {
            throw new ValidationException("Missing required parameter: " + key);
        }
    }
    
    /**
     * Validate required image handle with security checks
     */
    public static void requireImageHandle(JSONObject args) {
        require(args, "image_handle");
        String handle = args.getString("image_handle");
        String validatedHandle = InputValidator.validateImageHandle(handle);
        args.put("image_handle", validatedHandle); // Store sanitized version
    }
    
    /**
     * Validate required overlay handle with security checks
     */
    public static void requireOverlayHandle(JSONObject args) {
        require(args, "overlay_handle");
        String handle = args.getString("overlay_handle");
        String validatedHandle = InputValidator.validateOverlayHandle(handle);
        args.put("overlay_handle", validatedHandle);
    }
    
    /**
     * Validate required analysis handle with security checks
     */
    public static void requireAnalysisHandle(JSONObject args) {
        require(args, "analysis_handle");
        String handle = args.getString("analysis_handle");
        String validatedHandle = InputValidator.validateAnalysisHandle(handle);
        args.put("analysis_handle", validatedHandle);
    }
    
    /**
     * Validate required file path with security checks
     */
    public static void requireSecureFilePath(JSONObject args, String key, boolean allowRead, boolean allowWrite) {
        require(args, key);
        String path = args.getString(key);
        String validatedPath = InputValidator.validateFilePath(path, allowRead, allowWrite);
        args.put(key, validatedPath); // Store sanitized version
    }
    
    /**
     * Validate required export path with security checks
     */
    public static void requireSecureExportPath(JSONObject args, String key) {
        require(args, key);
        String path = args.getString(key);
        String validatedPath = InputValidator.validateExportPath(path);
        args.put(key, validatedPath); // Store sanitized version
    }
    
    /**
     * Validate required array parameter with size limits
     */
    public static void requireArray(JSONObject args, String key) {
        requireArray(args, key, 1, 10000); // Default size limits
    }
    
    /**
     * Validate required array parameter with size limits
     */
    public static void requireArray(JSONObject args, String key, int minSize, int maxSize) {
        if (!args.has(key) || args.isNull(key)) {
            throw new ValidationException("Missing required parameter: " + key);
        }
        
        if (!(args.get(key) instanceof JSONArray)) {
            throw new ValidationException("Parameter '" + key + "' must be an array");
        }
        
        JSONArray array = args.getJSONArray(key);
        if (array.length() < minSize) {
            throw new ValidationException("Parameter '" + key + "' must have at least " + minSize + " elements");
        }
        
        if (array.length() > maxSize) {
            throw new ValidationException("Parameter '" + key + "' cannot have more than " + maxSize + " elements");
        }
    }
    
    /**
     * Validate required integer parameter with range
     */
    public static void requireInt(JSONObject args, String key, int min, int max) {
        require(args, key);
        int value = args.getInt(key);
        if (value < min || value > max) {
            throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
        }
    }
    
    /**
     * Validate required double parameter with range
     */
    public static void requireDouble(JSONObject args, String key, double min, double max) {
        require(args, key);
        double value = args.getDouble(key);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValidationException("Parameter '" + key + "' must be a valid number");
        }
        if (value < min || value > max) {
            throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
        }
    }
    
    /**
     * Validate required enum parameter
     */
    public static void requireEnum(JSONObject args, String key, String... allowedValues) {
        require(args, key);
        String value = args.getString(key);
        for (String allowed : allowedValues) {
            if (allowed.equals(value)) {
                return; // Valid
            }
        }
        throw new ValidationException("Parameter '" + key + "' must be one of: " + 
            java.util.Arrays.toString(allowedValues) + ", got: " + value);
    }
    
    /**
     * Enhanced clamp with validation (replaces original clamp method)
     */
    public static void clamp(JSONObject args, String key, double min, double max) {
        if (!args.has(key)) return;
        
        double value = args.getNumber(key).doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValidationException("Parameter '" + key + "' must be a valid number");
        }
        
        double clamped = Math.max(min, Math.min(max, value));
        args.put(key, clamped);
    }
    
    /**
     * Validate and set optional integer parameter with default
     */
    public static void optionalInt(JSONObject args, String key, int min, int max, int defaultValue) {
        if (!args.has(key) || args.isNull(key)) {
            args.put(key, defaultValue);
            return;
        }
        
        int value = args.getInt(key);
        if (value < min || value > max) {
            throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
        }
    }
    
    /**
     * Validate and set optional double parameter with default
     */
    public static void optionalDouble(JSONObject args, String key, double min, double max, double defaultValue) {
        if (!args.has(key) || args.isNull(key)) {
            args.put(key, defaultValue);
            return;
        }
        
        double value = args.getDouble(key);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValidationException("Parameter '" + key + "' must be a valid number");
        }
        
        if (value < min || value > max) {
            throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
        }
    }
    
    /**
     * Validate and set optional string parameter with default
     */
    public static void optionalString(JSONObject args, String key, String defaultValue) {
        if (!args.has(key) || args.isNull(key)) {
            args.put(key, defaultValue);
            return;
        }
        
        String value = args.getString(key);
        if (value.length() > 1000) { // Prevent extremely long strings
            throw new ValidationException("Parameter '" + key + "' exceeds maximum length of 1000 characters");
        }
    }
    
    /**
     * Complete validation for common tool patterns
     */
    public static class Tools {
        
        /**
         * Validate open_image tool parameters
         */
        public static void validateOpenImage(JSONObject args) {
            requireSecureFilePath(args, "path", true, false);
        }
        
        /**
         * Validate detect_lanes tool parameters
         */
        public static void validateDetectLanes(JSONObject args) {
            requireImageHandle(args);
            optionalInt(args, "expected_lanes", 1, 50, 8);
            optionalDouble(args, "lane_width", 0.01, 1.0, 0.8);
            optionalDouble(args, "grid_offset", 0.0, 1.0, 0.1);
            optionalInt(args, "min_peak_distance", 1, 1000, 20);
            optionalDouble(args, "sensitivity", 0.1, 2.0, 1.0);
        }
        
        /**
         * Validate detect_bands tool parameters
         */
        public static void validateDetectBands(JSONObject args) {
            requireImageHandle(args);
            requireAnalysisHandle(args);
            optionalDouble(args, "sensitivity", 0.1, 2.0, 1.0);
            optionalInt(args, "min_band_area", 1, 100000, 50);
            optionalDouble(args, "noise_threshold", 0.0, 1.0, 0.1);
        }
        
        /**
         * Validate export tool parameters
         */
        public static void validateExport(JSONObject args) {
            requireSecureExportPath(args, "path");
            if (args.has("format")) {
                requireEnum(args, "format", "csv", "xlsx", "png", "jpg", "pdf", "json");
            }
            optionalInt(args, "quality", 1, 100, 95);
        }
        
        /**
         * Validate preprocess tool parameters
         */
        public static void validatePreprocess(JSONObject args) {
            requireImageHandle(args);
            requireArray(args, "steps", 1, 20); // Limit preprocessing steps
            
            // Validate each preprocessing step
            JSONArray steps = args.getJSONArray("steps");
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                require(step, "op");
                String op = step.getString("op");
                
                // Validate operation-specific parameters
                validatePreprocessingStep(step, op);
            }
        }
        
        private static void validatePreprocessingStep(JSONObject step, String op) {
            switch (op) {
                case "rotate" -> requireDouble(step, "angle_deg", -180.0, 180.0);
                case "flip" -> requireEnum(step, "axis", "horizontal", "vertical");
                case "crop" -> {
                    requireInt(step, "x", 0, 10000);
                    requireInt(step, "y", 0, 10000);
                    requireInt(step, "width", 1, 10000);
                    requireInt(step, "height", 1, 10000);
                }
                case "clahe" -> {
                    optionalInt(step, "blocksize", 8, 512, 127);
                    optionalInt(step, "histogram", 16, 1024, 256);
                    optionalDouble(step, "maximum", 0.1, 10.0, 3.0);
                }
                case "bandpass" -> {
                    optionalDouble(step, "filter_large", 1.0, 1000.0, 40.0);
                    optionalDouble(step, "filter_small", 0.1, 100.0, 3.0);
                }
                case "background" -> {
                    optionalEnum(step, "method", "rolling_ball", "sliding_paraboloid", "median", "gaussian");
                    optionalInt(step, "radius_px", 1, 500, 50);
                }
                // Add more validation for other operations
                default -> throw new ValidationException("Unknown preprocessing operation: " + op);
            }
        }
        
        private static void optionalEnum(JSONObject step, String key, String... allowedValues) {
            if (step.has(key) && !step.isNull(key)) {
                requireEnum(step, key, allowedValues);
            }
        }
    }
}