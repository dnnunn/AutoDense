package com.betterdairy.autodense.util;

import org.json.JSONObject;
import org.json.JSONArray;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;
import java.util.logging.Logger;

/**
 * Common validation patterns extracted from tool classes.
 * Reduces code duplication and provides consistent error handling.
 */
public final class CommonValidation {
    
    private CommonValidation() {} // Utility class
    
    /**
     * Standard image handle validation pattern
     * Returns error JSONObject if validation fails, null if successful
     */
    public static JSONObject validateImageHandle(JSONObject args, String toolName,
                                               SessionStore store, Logger logger, SessionRecovery recovery) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord record = store.getImage(imageHandle);
            
            if (record == null) {
                return ErrorHandler.handleValidationError(toolName,
                    new IllegalArgumentException("Image handle not found: " + imageHandle),
                    logger, recovery);
            }
            
            return null; // Success
        } catch (org.json.JSONException e) {
            return ErrorHandler.handleValidationError(toolName,
                new IllegalArgumentException("Missing required parameter: image_handle"),
                logger, recovery);
        }
    }
    
    /**
     * Retrieve validated image record
     * Returns the record if valid, throws exception if not
     */
    public static SessionStore.ImageRecord getValidatedImage(JSONObject args, String toolName,
                                                           SessionStore store) {
        String imageHandle = args.getString("image_handle");
        SessionStore.ImageRecord record = store.getImage(imageHandle);
        
        if (record == null) {
            throw new IllegalArgumentException("Image handle not found: " + imageHandle);
        }
        
        return record;
    }
    
    /**
     * Standard parameter extraction with defaults and validation
     */
    public static class ParameterExtractor {
        private final JSONObject args;
        private final String toolName;
        
        public ParameterExtractor(JSONObject args, String toolName) {
            this.args = args;
            this.toolName = toolName;
        }
        
        public String getRequiredString(String key) {
            if (!args.has(key)) {
                throw new IllegalArgumentException("Missing required parameter: " + key);
            }
            String value = args.getString(key);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Parameter '" + key + "' cannot be empty");
            }
            return value.trim();
        }
        
        public String getOptionalString(String key, String defaultValue) {
            return args.optString(key, defaultValue);
        }
        
        public double getRequiredDouble(String key, double min, double max) {
            if (!args.has(key)) {
                throw new IllegalArgumentException("Missing required parameter: " + key);
            }
            double value = args.getDouble(key);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be a valid number");
            }
            if (value < min || value > max) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be between " + min + " and " + max);
            }
            return value;
        }
        
        public double getOptionalDouble(String key, double defaultValue, double min, double max) {
            if (!args.has(key)) {
                return defaultValue;
            }
            double value = args.getDouble(key);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be a valid number");
            }
            if (value < min || value > max) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be between " + min + " and " + max);
            }
            return value;
        }
        
        public int getRequiredInt(String key, int min, int max) {
            if (!args.has(key)) {
                throw new IllegalArgumentException("Missing required parameter: " + key);
            }
            int value = args.getInt(key);
            if (value < min || value > max) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be between " + min + " and " + max);
            }
            return value;
        }
        
        public int getOptionalInt(String key, int defaultValue, int min, int max) {
            if (!args.has(key)) {
                return defaultValue;
            }
            int value = args.getInt(key);
            if (value < min || value > max) {
                throw new IllegalArgumentException("Parameter '" + key + "' must be between " + min + " and " + max);
            }
            return value;
        }
        
        public boolean getOptionalBoolean(String key, boolean defaultValue) {
            return args.optBoolean(key, defaultValue);
        }
        
        public JSONArray getOptionalArray(String key) {
            return args.optJSONArray(key);
        }
        
        public double[] getOptionalDoubleArray(String key, double[] defaultValues) {
            JSONArray array = args.optJSONArray(key);
            if (array == null) {
                return defaultValues;
            }
            
            double[] result = new double[array.length()];
            for (int i = 0; i < array.length(); i++) {
                result[i] = array.optDouble(i);
                if (Double.isNaN(result[i]) || Double.isInfinite(result[i])) {
                    throw new IllegalArgumentException("Invalid numeric value in array parameter '" + key + "' at index " + i);
                }
            }
            return result;
        }
    }
    
    /**
     * Common success response pattern
     */
    public static JSONObject createSuccessResponse(String toolName, JSONObject data) {
        return new JSONObject()
            .put("success", true)
            .put("tool", toolName)
            .put("data", data)
            .put("timestamp", System.currentTimeMillis());
    }
    
    /**
     * Colony detection parameter validation pattern
     */
    public static void validateColonyDetectionParams(JSONObject args) {
        ParameterExtractor extractor = new ParameterExtractor(args, "colony_detection");
        
        // Validate size parameters
        double minSize = extractor.getOptionalDouble("min_size", 5.0, 0.1, 1000.0);
        double maxSize = extractor.getOptionalDouble("max_size", 1000.0, 1.0, 100000.0);
        
        if (minSize >= maxSize) {
            throw new IllegalArgumentException("min_size must be less than max_size");
        }
        
        // Validate circularity
        extractor.getOptionalDouble("min_circularity", 0.3, 0.0, 1.0);
        
        // Validate solidity
        extractor.getOptionalDouble("min_solidity", 0.5, 0.0, 1.0);
    }
    
    /**
     * Gel analysis parameter validation pattern
     */
    public static void validateGelAnalysisParams(JSONObject args) {
        ParameterExtractor extractor = new ParameterExtractor(args, "gel_analysis");
        
        // Validate expected lanes if provided
        if (args.has("expected_lanes")) {
            extractor.getRequiredInt("expected_lanes", 1, 50);
        }
        
        // Validate sensitivity if provided
        if (args.has("sensitivity")) {
            extractor.getRequiredDouble("sensitivity", 0.1, 10.0);
        }
        
        // Validate background method if provided
        if (args.has("background_method")) {
            String method = extractor.getRequiredString("background_method");
            if (!isValidBackgroundMethod(method)) {
                throw new IllegalArgumentException("Invalid background_method: " + method + 
                    ". Valid options: rolling_ball, median, none");
            }
        }
    }
    
    private static boolean isValidBackgroundMethod(String method) {
        return "rolling_ball".equals(method) || "median".equals(method) || "none".equals(method);
    }
    
    /**
     * File path parameter validation pattern
     */
    public static String validateFilePath(JSONObject args, String paramName) {
        ParameterExtractor extractor = new ParameterExtractor(args, "file_operation");
        String path = extractor.getRequiredString(paramName);
        
        // Use existing InputValidator for security
        try {
            return com.betterdairy.autodense.validation.InputValidator.validateFilePath(path, true, false);
        } catch (com.betterdairy.autodense.validation.InputValidator.ValidationException e) {
            throw new IllegalArgumentException("Invalid file path: " + e.getMessage(), e);
        }
    }
}
