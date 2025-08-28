package com.betterdairy.autodense.validation;

import org.json.JSONObject;
import org.json.JSONArray;
import com.betterdairy.autodense.validation.InputValidator.ValidationException;

/**
 * Common validation patterns extracted from InputValidator for reusability.
 * 
 * This utility class provides standardized validation patterns that can be
 * reused across different tool classes, reducing code duplication and
 * ensuring consistent validation behavior.
 * 
 * Usage patterns:
 * - ValidationPatterns.validateImageTool(params)
 * - ValidationPatterns.validateExportTool(params) 
 * - ValidationPatterns.validateDetectionTool(params)
 */
public final class ValidationPatterns {
    
    private ValidationPatterns() {} // Utility class
    
    /**
     * Standard validation for image processing tools
     */
    public static JSONObject validateImageTool(JSONObject params, String toolName) {
        return new InputValidator.ToolValidator(params, toolName)
            .requireString("image_handle")
            .optionalInt("width", 1, 10000, -1)
            .optionalInt("height", 1, 10000, -1)
            .getArgs();
    }
    
    /**
     * Standard validation for colony detection tools
     */
    public static JSONObject validateColonyDetectionTool(JSONObject params, String toolName) {
        return new InputValidator.ToolValidator(params, toolName)
            .requireString("image_handle")
            .optionalDouble("min_size", 1.0, 10000.0, 5.0)
            .optionalDouble("max_size", 10.0, 100000.0, 1000.0)
            .optionalDouble("threshold", 0.0, 1.0, 0.5)
            .getArgs();
    }
    
    /**
     * Standard validation for gel analysis tools
     */
    public static JSONObject validateGelAnalysisTool(JSONObject params, String toolName) {
        return new InputValidator.ToolValidator(params, toolName)
            .requireString("image_handle")
            .optionalInt("expected_lanes", 1, 50, 8)
            .optionalDouble("lane_width", 0.01, 1.0, 0.8)
            .optionalDouble("sensitivity", 0.1, 2.0, 1.0)
            .getArgs();
    }
    
    /**
     * Standard validation for export tools
     */
    public static JSONObject validateExportTool(JSONObject params, String toolName) {
        return new InputValidator.ToolValidator(params, toolName)
            .requireString("image_handle")
            .requireEnum("format", "csv", "xlsx", "png", "jpg", "pdf", "json")
            .optionalInt("quality", 1, 100, 95)
            .getArgs();
    }
    
    /**
     * Standard validation for band detection tools
     */
    public static JSONObject validateBandDetectionTool(JSONObject params, String toolName) {
        return new InputValidator.ToolValidator(params, toolName)
            .requireString("image_handle")
            .requireString("analysis_handle")
            .optionalDouble("sensitivity", 0.1, 2.0, 1.0)
            .optionalInt("min_band_area", 1, 100000, 50)
            .optionalDouble("noise_threshold", 0.0, 1.0, 0.1)
            .getArgs();
    }
    
    /**
     * Validate and sanitize common tool parameters
     */
    public static class CommonValidations {
        
        /**
         * Validate image handle with proper format checking
         */
        public static String validateAndSanitizeImageHandle(JSONObject params, String paramName) {
            if (!params.has(paramName)) {
                throw new ValidationException("Missing required parameter: " + paramName);
            }
            
            String handle = params.getString(paramName);
            return InputValidator.validateImageHandle(handle);
        }
        
        /**
         * Validate file path with security checks
         */
        public static String validateAndSanitizeFilePath(JSONObject params, String paramName, 
                                                        boolean allowRead, boolean allowWrite) {
            if (!params.has(paramName)) {
                throw new ValidationException("Missing required parameter: " + paramName);
            }
            
            String path = params.getString(paramName);
            return InputValidator.validateFilePath(path, allowRead, allowWrite);
        }
        
        /**
         * Validate numeric parameter within range
         */
        public static double validateNumericRange(JSONObject params, String paramName, 
                                                double min, double max, double defaultValue) {
            if (!params.has(paramName)) {
                return defaultValue;
            }
            
            double value = params.getDouble(paramName);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new ValidationException("Parameter '" + paramName + "' must be a valid number");
            }
            
            if (value < min || value > max) {
                throw new ValidationException("Parameter '" + paramName + "' must be between " + 
                    min + " and " + max + ", got: " + value);
            }
            
            return value;
        }
        
        /**
         * Validate array parameter with size constraints
         */
        public static JSONArray validateArrayParameter(JSONObject params, String paramName, 
                                                     int minSize, int maxSize, boolean required) {
            if (!params.has(paramName)) {
                if (required) {
                    throw new ValidationException("Missing required parameter: " + paramName);
                }
                return new JSONArray();
            }
            
            if (!(params.get(paramName) instanceof JSONArray)) {
                throw new ValidationException("Parameter '" + paramName + "' must be an array");
            }
            
            JSONArray array = params.getJSONArray(paramName);
            if (array.length() < minSize) {
                throw new ValidationException("Parameter '" + paramName + "' must have at least " + 
                    minSize + " elements");
            }
            
            if (array.length() > maxSize) {
                throw new ValidationException("Parameter '" + paramName + "' cannot have more than " + 
                    maxSize + " elements");
            }
            
            return array;
        }
    }
    
    /**
     * Pre-configured validation schemas for common tool types
     */
    public static class StandardSchemas {
        
        public static final String[] IMAGE_FORMATS = {"png", "jpg", "jpeg", "tiff", "bmp"};
        public static final String[] EXPORT_FORMATS = {"csv", "xlsx", "png", "jpg", "pdf", "json"};
        public static final String[] STAIN_TYPES = {"x-gal", "neutral-red", "none"};
        public static final String[] BACKGROUND_METHODS = {"rolling_ball", "polynomial", "median"};
        
        /**
         * Complete validation for image analysis workflow
         */
        public static JSONObject validateImageAnalysisWorkflow(JSONObject params, String toolName) {
            InputValidator.ToolValidator validator = new InputValidator.ToolValidator(params, toolName)
                .requireString("image_handle");
            
            // Optional workflow parameters
            if (params.has("expected_lanes")) {
                validator.optionalInt("expected_lanes", 1, 50, 8);
            }
            
            if (params.has("sensitivity")) {
                validator.optionalDouble("sensitivity", 0.1, 2.0, 1.0);
            }
            
            if (params.has("background_method")) {
                validator.requireEnum("background_method", BACKGROUND_METHODS);
            }
            
            if (params.has("export_format")) {
                validator.requireEnum("export_format", EXPORT_FORMATS);
            }
            
            return validator.getArgs();
        }
        
        /**
         * Complete validation for colony analysis workflow
         */
        public static JSONObject validateColonyAnalysisWorkflow(JSONObject params, String toolName) {
            InputValidator.ToolValidator validator = new InputValidator.ToolValidator(params, toolName)
                .requireString("image_handle");
            
            // Colony-specific parameters
            if (params.has("stain")) {
                validator.requireEnum("stain", STAIN_TYPES);
            }
            
            if (params.has("min_size")) {
                validator.optionalDouble("min_size", 1.0, 10000.0, 5.0);
            }
            
            if (params.has("max_size")) {
                validator.optionalDouble("max_size", 10.0, 100000.0, 1000.0);
            }
            
            if (params.has("plate_layout")) {
                validator.optionalInt("plate_layout", 1, 384, 1);
            }
            
            return validator.getArgs();
        }
    }
}
