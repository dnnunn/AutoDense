package com.betterdairy.autodense.validation;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comprehensive input validation and security for AutoDense tools.
 * 
 * Addresses Phase 4.2 security issues:
 * - Weak validation allows malformed inputs
 * - Directory traversal vulnerabilities  
 * - Unsafe file path handling
 * - Missing parameter type validation
 * - No input sanitization
 * 
 * Security Features:
 * - Path traversal prevention
 * - File extension whitelisting
 * - Handle format validation
 * - Parameter type enforcement
 * - Range validation
 * - Input sanitization
 */
public final class InputValidator {
    
    // Security constraints
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
        ".tif", ".tiff", ".jpg", ".jpeg", ".png", ".bmp", ".gif"
    );
    
    private static final Set<String> ALLOWED_EXPORT_EXTENSIONS = Set.of(
        ".csv", ".xlsx", ".png", ".jpg", ".pdf", ".json"
    );
    
    // Handle format validation (updated to support UUID-based handles)
    private static final Pattern VALID_HANDLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern VALID_IMAGE_HANDLE_PATTERN = Pattern.compile("^img_[0-9a-f]+$");
    private static final Pattern VALID_OVERLAY_HANDLE_PATTERN = Pattern.compile("^ov_[0-9a-f]+$");
    private static final Pattern VALID_ANALYSIS_HANDLE_PATTERN = Pattern.compile("^analysis_[0-9a-f]+$");
    
    // Parameter constraints
    private static final int MAX_STRING_LENGTH = 1000;
    private static final int MAX_ARRAY_SIZE = 10000;
    private static final double MIN_PERCENTAGE = 0.0;
    private static final double MAX_PERCENTAGE = 100.0;
    
    private InputValidator() {} // Utility class
    
    /**
     * Comprehensive parameter validation for tool methods
     */
    public static class ToolValidator {
        private final JSONObject args;
        private final String toolName;
        
        public ToolValidator(JSONObject args, String toolName) {
            this.args = args != null ? args : new JSONObject();
            this.toolName = toolName;
        }
        
        /**
         * Validate required string parameter
         */
        public ToolValidator requireString(String key) {
            if (!args.has(key) || args.isNull(key)) {
                throw new ValidationException("Missing required parameter: " + key);
            }
            
            String value = args.getString(key);
            if (value.isEmpty()) {
                throw new ValidationException("Parameter '" + key + "' cannot be empty");
            }
            
            if (value.length() > MAX_STRING_LENGTH) {
                throw new ValidationException("Parameter '" + key + "' exceeds maximum length of " + MAX_STRING_LENGTH);
            }
            
            return this;
        }
        
        /**
         * Validate required integer parameter with range
         */
        public ToolValidator requireInt(String key, int min, int max) {
            if (!args.has(key) || args.isNull(key)) {
                throw new ValidationException("Missing required parameter: " + key);
            }
            
            int value = args.getInt(key);
            if (value < min || value > max) {
                throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
            }
            
            return this;
        }
        
        /**
         * Validate required double parameter with range
         */
        public ToolValidator requireDouble(String key, double min, double max) {
            if (!args.has(key) || args.isNull(key)) {
                throw new ValidationException("Missing required parameter: " + key);
            }
            
            double value = args.getDouble(key);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new ValidationException("Parameter '" + key + "' must be a valid number");
            }
            
            if (value < min || value > max) {
                throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
            }
            
            return this;
        }
        
        /**
         * Validate optional integer parameter with range and default
         */
        public ToolValidator optionalInt(String key, int min, int max, int defaultValue) {
            if (!args.has(key) || args.isNull(key)) {
                args.put(key, defaultValue);
                return this;
            }
            
            int value = args.getInt(key);
            if (value < min || value > max) {
                throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
            }
            
            return this;
        }
        
        /**
         * Validate optional double parameter with range and default
         */
        public ToolValidator optionalDouble(String key, double min, double max, double defaultValue) {
            if (!args.has(key) || args.isNull(key)) {
                args.put(key, defaultValue);
                return this;
            }
            
            double value = args.getDouble(key);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new ValidationException("Parameter '" + key + "' must be a valid number");
            }
            
            if (value < min || value > max) {
                throw new ValidationException("Parameter '" + key + "' must be between " + min + " and " + max + ", got: " + value);
            }
            
            return this;
        }
        
        /**
         * Validate enum parameter against allowed values
         */
        public ToolValidator requireEnum(String key, String... allowedValues) {
            if (!args.has(key) || args.isNull(key)) {
                throw new ValidationException("Missing required parameter: " + key);
            }
            
            String value = args.getString(key);
            if (!Arrays.asList(allowedValues).contains(value)) {
                throw new ValidationException("Parameter '" + key + "' must be one of: " + 
                    Arrays.toString(allowedValues) + ", got: " + value);
            }
            
            return this;
        }
        
        /**
         * Validate required array parameter
         */
        public ToolValidator requireArray(String key, int minSize, int maxSize) {
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
            
            return this;
        }
        
        /**
         * Get the validated arguments
         */
        public JSONObject getArgs() {
            return args;
        }
    }
    
    /**
     * Validate and sanitize file path for security
     */
    public static String validateFilePath(String path, boolean allowRead, boolean allowWrite) {
        if (path == null || path.trim().isEmpty()) {
            throw new ValidationException("File path cannot be null or empty");
        }
        
        try {
            // SECURITY FIX: Use canonical path resolution to prevent bypasses
            Path requestedPath = Paths.get(path.trim());
            Path canonicalPath = requestedPath.toRealPath(); // Resolves symlinks and normalizes
            
            // Define allowed base directories (allowlist approach)
            Path allowedBase = Paths.get(System.getProperty("user.home"), "Documents");
            Path homeBase = Paths.get(System.getProperty("user.home"));
            
            // SECURITY: Verify the canonical path is within allowed directories
            if (!canonicalPath.startsWith(allowedBase) && !canonicalPath.startsWith(homeBase)) {
                throw new ValidationException("Path outside allowed directory: " + path);
            }
            
            Path filePath = canonicalPath; // Use the secure canonical path
            
            // Validate file extension for images
            String extension = getFileExtension(canonicalPath.toString()).toLowerCase();
            if (allowRead && !ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                throw new ValidationException("File extension '" + extension + 
                    "' not allowed. Allowed: " + ALLOWED_IMAGE_EXTENSIONS);
            }
            
            // Enhanced permission checks for read operations
            if (allowRead && !Files.isReadable(canonicalPath)) {
                throw new ValidationException("File not readable: " + path);
            }
            
            if (allowWrite && Files.exists(canonicalPath) && !Files.isWritable(canonicalPath)) {
                throw new ValidationException("File not writable: " + path);
            }
            
            // Check parent directory exists for write operations
            if (allowWrite && filePath.getParent() != null && !Files.exists(filePath.getParent())) {
                throw new ValidationException("Parent directory does not exist: " + filePath.getParent());
            }
            
            return filePath.toString();
            
        } catch (IOException e) {
            throw new ValidationException("Invalid file path: " + path + " - " + e.getMessage());
        } catch (ValidationException e) {
            throw e; // Re-throw our validation exceptions
        } catch (Exception e) {
            throw new ValidationException("Invalid file path: " + path + " - " + e.getMessage());
        }
    }
    
    /**
     * Validate export file path with specific restrictions
     */
    public static String validateExportPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new ValidationException("Export path cannot be null or empty");
        }
        
        String sanitized = sanitizePath(path.trim());
        
        try {
            Path filePath = Paths.get(sanitized).normalize().toAbsolutePath();
            
            if (containsDirectoryTraversal(path)) {
                throw new ValidationException("Directory traversal detected in export path: " + path);
            }
            
            String extension = getFileExtension(sanitized).toLowerCase();
            if (!ALLOWED_EXPORT_EXTENSIONS.contains(extension)) {
                throw new ValidationException("Export extension '" + extension + 
                    "' not allowed. Allowed: " + ALLOWED_EXPORT_EXTENSIONS);
            }
            
            return filePath.toString();
            
        } catch (Exception e) {
            throw new ValidationException("Invalid export path: " + path + " - " + e.getMessage());
        }
    }
    
    /**
     * Validate image handle format
     */
    public static String validateImageHandle(String handle) {
        if (handle == null || handle.trim().isEmpty()) {
            throw new ValidationException("Image handle cannot be null or empty");
        }
        
        String trimmed = handle.trim();
        if (!VALID_IMAGE_HANDLE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Invalid image handle format. Expected 'img_[alphanumeric]', got: " + handle);
        }
        
        return trimmed;
    }
    
    /**
     * Validate overlay handle format
     */
    public static String validateOverlayHandle(String handle) {
        if (handle == null || handle.trim().isEmpty()) {
            throw new ValidationException("Overlay handle cannot be null or empty");
        }
        
        String trimmed = handle.trim();
        if (!VALID_OVERLAY_HANDLE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Invalid overlay handle format. Expected 'ov_[alphanumeric]', got: " + handle);
        }
        
        return trimmed;
    }
    
    /**
     * Validate analysis handle format
     */
    public static String validateAnalysisHandle(String handle) {
        if (handle == null || handle.trim().isEmpty()) {
            throw new ValidationException("Analysis handle cannot be null or empty");
        }
        
        String trimmed = handle.trim();
        if (!VALID_ANALYSIS_HANDLE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Invalid analysis handle format. Expected 'analysis_[alphanumeric]', got: " + handle);
        }
        
        return trimmed;
    }
    
    /**
     * Validate generic handle format (for backwards compatibility)
     */
    public static String validateHandle(String handle) {
        if (handle == null || handle.trim().isEmpty()) {
            throw new ValidationException("Handle cannot be null or empty");
        }
        
        String trimmed = handle.trim();
        if (!VALID_HANDLE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Invalid handle format. Only alphanumeric, underscore, and hyphen allowed, got: " + handle);
        }
        
        return trimmed;
    }
    
    /**
     * Validate percentage value
     */
    public static double validatePercentage(double value, String parameterName) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValidationException("Parameter '" + parameterName + "' must be a valid number");
        }
        
        if (value < MIN_PERCENTAGE || value > MAX_PERCENTAGE) {
            throw new ValidationException("Parameter '" + parameterName + "' must be between " + 
                MIN_PERCENTAGE + "% and " + MAX_PERCENTAGE + "%, got: " + value + "%");
        }
        
        return value;
    }
    
    /**
     * Sanitize path string to remove dangerous characters
     */
    private static String sanitizePath(String path) {
        // Remove null bytes and control characters
        String sanitized = path.replaceAll("[\u0000-\u001f\u007f-\u009f]", "");
        
        // Remove dangerous path sequences (but don't break legitimate paths)
        sanitized = sanitized.replaceAll("\\.{3,}", ".."); // Convert .... to ..
        
        return sanitized;
    }
    
    /**
     * Check for directory traversal attempts
     */
    private static boolean containsDirectoryTraversal(String path) {
        String normalized = path.toLowerCase().replace('\\', '/');
        
        // Check for various directory traversal patterns
        return normalized.contains("../") || 
               normalized.contains("..\\") ||
               normalized.contains("/.../") ||
               normalized.startsWith("../") ||
               normalized.startsWith("..\\") ||
               normalized.endsWith("/..") ||
               normalized.endsWith("\\..") ||
               normalized.equals("..") ||
               normalized.contains("//") ||  // Double slashes can bypass filters
               normalized.contains("\\/") ||
               normalized.contains("/\\");
    }
    
    /**
     * Extract file extension safely
     */
    private static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDot);
    }
    
    /**
     * Custom validation exception
     */
    public static class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Predefined parameter schemas for common tool patterns
     */
    public static class Schemas {
        
        /**
         * Image processing tool parameters
         */
        public static ToolValidator imageProcessingTool(JSONObject args, String toolName) {
            return new ToolValidator(args, toolName)
                .requireString("image_handle")
                .optionalInt("width", 1, 10000, -1)
                .optionalInt("height", 1, 10000, -1);
        }
        
        /**
         * Detection tool parameters  
         */
        public static ToolValidator detectionTool(JSONObject args, String toolName) {
            return new ToolValidator(args, toolName)
                .requireString("image_handle")
                .optionalDouble("threshold", 0.0, 1.0, 0.5)
                .optionalInt("min_size", 1, 1000000, 100)
                .optionalInt("max_size", 1, 1000000, 10000);
        }
        
        /**
         * Export tool parameters
         */
        public static ToolValidator exportTool(JSONObject args, String toolName) {
            return new ToolValidator(args, toolName)
                .requireString("path")
                .requireEnum("format", "csv", "xlsx", "png", "jpg", "pdf", "json")
                .optionalInt("quality", 1, 100, 95);
        }
        
        /**
         * Lane detection parameters
         */
        public static ToolValidator laneDetection(JSONObject args, String toolName) {
            return new ToolValidator(args, toolName)
                .requireString("image_handle")
                .optionalInt("expected_lanes", 1, 50, 8)
                .optionalDouble("lane_width", 0.01, 1.0, 0.8)
                .optionalDouble("grid_offset", 0.0, 1.0, 0.1)
                .optionalInt("min_peak_distance", 1, 1000, 20);
        }
        
        /**
         * Band detection parameters
         */
        public static ToolValidator bandDetection(JSONObject args, String toolName) {
            return new ToolValidator(args, toolName)
                .requireString("image_handle")
                .requireString("analysis_handle")
                .optionalDouble("sensitivity", 0.1, 2.0, 1.0)
                .optionalInt("min_band_area", 1, 100000, 50)
                .optionalDouble("noise_threshold", 0.0, 1.0, 0.1);
        }
    }
}