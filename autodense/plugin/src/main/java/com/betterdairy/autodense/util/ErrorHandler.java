package com.betterdairy.autodense.util;

import org.json.JSONObject;
import org.json.JSONArray;
import com.betterdairy.autodense.session.SessionLogger;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.betterdairy.autodense.session.SessionRecovery;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standardized error handling for AutoDense tools.
 * 
 * Addresses audit findings:
 * - Replaces generic Exception catching with specific error types
 * - Adds comprehensive logging with stack traces  
 * - Implements structured error responses
 * - Provides error recovery guidance
 * 
 * Usage patterns:
 * 
 * // Instead of: catch (Exception e) { return recovery.createRecoveryResponse("tool", e); }
 * // Use:
 * } catch (IOException e) {
 *     return ErrorHandler.handleFileError("tool_name", e, logger, recovery);
 * } catch (IllegalArgumentException e) {
 *     return ErrorHandler.handleValidationError("tool_name", e, logger, recovery);
 * } catch (RuntimeException e) {
 *     return ErrorHandler.handleUnexpectedError("tool_name", e, logger, recovery);
 * }
 */
public final class ErrorHandler {
    
    // Error metrics for monitoring
    private static final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    private ErrorHandler() {} // Utility class
    
    /**
     * Handle file I/O related errors (IOException, FileNotFoundException, etc.)
     */
    public static JSONObject handleFileError(String toolName, Exception e, 
                                           SessionLogger logger, SessionRecovery recovery) {
        logError(toolName, "FILE_ERROR", e, logger);
        return createFileErrorResponse(toolName, e, recovery);
    }
    
    /**
     * Handle file I/O related errors (IOException, FileNotFoundException, etc.)
     */
    public static JSONObject handleFileError(String toolName, Exception e, 
                                           Logger logger, SessionRecovery recovery) {
        logError(toolName, "FILE_ERROR", e, logger);
        return createFileErrorResponse(toolName, e, recovery);
    }
    
    private static JSONObject createFileErrorResponse(String toolName, Exception e, SessionRecovery recovery) {
        JSONObject response = createBaseErrorResponse(toolName, "FILE_ERROR", e);
        
        // Add specific recovery guidance for file errors
        JSONObject recoveryGuidance = new JSONObject();
        recoveryGuidance.put("check_file_exists", true);
        recoveryGuidance.put("verify_permissions", true);
        recoveryGuidance.put("check_disk_space", true);
        
        if (e.getMessage() != null) {
            if (e.getMessage().contains("Permission denied")) {
                recoveryGuidance.put("suggestion", "Check file permissions or try running with elevated privileges");
            } else if (e.getMessage().contains("No space left")) {
                recoveryGuidance.put("suggestion", "Free up disk space and try again");
            } else if (e.getMessage().contains("not found")) {
                recoveryGuidance.put("suggestion", "Verify the file path is correct and the file exists");
            }
        }
        
        response.put("recovery_actions", recoveryGuidance);
        
        return response;
    }
    
    /**
     * Handle parameter validation errors (IllegalArgumentException, etc.)
     */
    public static JSONObject handleValidationError(String toolName, Exception e, 
                                                 SessionLogger logger, SessionRecovery recovery) {
        logError(toolName, "VALIDATION_ERROR", e, logger);
        return createValidationErrorResponse(toolName, e, recovery);
    }
    
    /**
     * Handle parameter validation errors (IllegalArgumentException, etc.)
     */
    public static JSONObject handleValidationError(String toolName, Exception e, 
                                                 Logger logger, SessionRecovery recovery) {
        logError(toolName, "VALIDATION_ERROR", e, logger);
        return createValidationErrorResponse(toolName, e, recovery);
    }
    
    private static JSONObject createValidationErrorResponse(String toolName, Exception e, SessionRecovery recovery) {
        
        JSONObject response = createBaseErrorResponse(toolName, "VALIDATION_ERROR", e);
        
        // Add specific recovery guidance for validation errors
        JSONObject recoveryGuidance = new JSONObject();
        recoveryGuidance.put("check_parameters", true);
        recoveryGuidance.put("verify_input_format", true);
        
        if (e.getMessage() != null) {
            String message = e.getMessage().toLowerCase();
            if (message.contains("handle")) {
                recoveryGuidance.put("suggestion", "Verify the image handle is valid and the image exists in session");
                recoveryGuidance.put("check_session_store", true);
            } else if (message.contains("null") || message.contains("empty")) {
                recoveryGuidance.put("suggestion", "Ensure all required parameters are provided and non-empty");
            } else if (message.contains("range") || message.contains("bounds")) {
                recoveryGuidance.put("suggestion", "Check that numeric parameters are within valid ranges");
            }
        }
        
        response.put("recovery_actions", recoveryGuidance);
        
        return response;
    }
    
    /**
     * Handle ImageJ/ImagePlus related errors
     */
    public static JSONObject handleImageProcessingError(String toolName, Exception e, 
                                                       SessionLogger logger, SessionRecovery recovery) {
        logError(toolName, "IMAGE_PROCESSING_ERROR", e, logger);
        return createImageProcessingErrorResponse(toolName, e, recovery);
    }
    
    /**
     * Handle ImageJ/ImagePlus related errors
     */
    public static JSONObject handleImageProcessingError(String toolName, Exception e, 
                                                       Logger logger, SessionRecovery recovery) {
        logError(toolName, "IMAGE_PROCESSING_ERROR", e, logger);
        return createImageProcessingErrorResponse(toolName, e, recovery);
    }
    
    private static JSONObject createImageProcessingErrorResponse(String toolName, Exception e, SessionRecovery recovery) {
        
        JSONObject response = createBaseErrorResponse(toolName, "IMAGE_PROCESSING_ERROR", e);
        
        // Add specific recovery guidance for image processing errors
        JSONObject recoveryGuidance = new JSONObject();
        recoveryGuidance.put("check_image_valid", true);
        recoveryGuidance.put("verify_image_type", true);
        
        if (e.getMessage() != null) {
            String message = e.getMessage().toLowerCase();
            if (message.contains("null") && message.contains("image")) {
                recoveryGuidance.put("suggestion", "The image handle may be invalid or the image was closed");
            } else if (message.contains("dimensions") || message.contains("size")) {
                recoveryGuidance.put("suggestion", "Check image dimensions and ensure it's large enough for processing");
            } else if (message.contains("type") || message.contains("bit")) {
                recoveryGuidance.put("suggestion", "Convert image to supported type (8-bit, 16-bit, or 32-bit)");
            }
        }
        
        response.put("recovery_actions", recoveryGuidance);
        
        return response;
    }
    
    /**
     * Handle session storage errors
     */
    public static JSONObject handleSessionError(String toolName, Exception e, 
                                              SessionLogger logger, SessionRecovery recovery) {
        logError(toolName, "SESSION_ERROR", e, logger);
        return createSessionErrorResponse(toolName, e, recovery);
    }
    
    /**
     * Handle session storage errors
     */
    public static JSONObject handleSessionError(String toolName, Exception e, 
                                              Logger logger, SessionRecovery recovery) {
        logError(toolName, "SESSION_ERROR", e, logger);
        return createSessionErrorResponse(toolName, e, recovery);
    }
    
    private static JSONObject createSessionErrorResponse(String toolName, Exception e, SessionRecovery recovery) {
        
        JSONObject response = createBaseErrorResponse(toolName, "SESSION_ERROR", e);
        
        // Add specific recovery guidance for session errors
        JSONObject recoveryGuidance = new JSONObject();
        recoveryGuidance.put("check_session_state", true);
        recoveryGuidance.put("verify_handles", true);
        
        if (e.getMessage() != null) {
            String message = e.getMessage().toLowerCase();
            if (message.contains("concurrent") || message.contains("thread")) {
                recoveryGuidance.put("suggestion", "Concurrent access detected - try again or use session locking");
            } else if (message.contains("not found") || message.contains("missing")) {
                recoveryGuidance.put("suggestion", "The requested data may have been removed from session");
            }
        }
        
        response.put("recovery_actions", recoveryGuidance);
        
        return response;
    }
    
    /**
     * Handle unexpected runtime errors that weren't caught by specific handlers
     * This should be used as a last resort when specific error types aren't known
     */
    public static JSONObject handleUnexpectedError(String toolName, Exception e, 
                                                  SessionLogger logger, SessionRecovery recovery) {
        logError(toolName, "UNEXPECTED_ERROR", e, logger);
        return createUnexpectedErrorResponse(toolName, e, recovery);
    }
    
    /**
     * Handle unexpected runtime errors that weren't caught by specific handlers
     * This should be used as a last resort when specific error types aren't known
     */
    public static JSONObject handleUnexpectedError(String toolName, Exception e, 
                                                  Logger logger, SessionRecovery recovery) {
        logError(toolName, "UNEXPECTED_ERROR", e, logger);
        return createUnexpectedErrorResponse(toolName, e, recovery);
    }
    
    private static JSONObject createUnexpectedErrorResponse(String toolName, Exception e, SessionRecovery recovery) {
        
        JSONObject response = createBaseErrorResponse(toolName, "UNEXPECTED_ERROR", e);
        
        // Add generic recovery guidance
        JSONObject recoveryGuidance = new JSONObject();
        recoveryGuidance.put("suggestion", "An unexpected error occurred - check logs for details");
        recoveryGuidance.put("retry_recommended", true);
        recoveryGuidance.put("report_issue", true);
        
        response.put("recovery_actions", recoveryGuidance);
        
        return response;
    }
    
    /**
     * Create the base error response structure
     */
    private static JSONObject createBaseErrorResponse(String toolName, String errorType, Exception e) {
        JSONObject response = new JSONObject();
        response.put("ok", false);
        response.put("tool", toolName);
        response.put("error_type", errorType);
        response.put("error_class", e.getClass().getSimpleName());
        response.put("message", e.getMessage() != null ? e.getMessage() : "No error message available");
        response.put("timestamp", java.time.Instant.now().toString());
        
        // Include stack trace for debugging (first few frames)
        StackTraceElement[] stack = e.getStackTrace();
        if (stack != null && stack.length > 0) {
            JSONArray stackTrace = new JSONArray();
            // Only include first 5 stack frames to avoid overwhelming the response
            for (int i = 0; i < Math.min(5, stack.length); i++) {
                stackTrace.put(stack[i].toString());
            }
            response.put("stack_trace", stackTrace);
        }
        
        return response;
    }
    
    /**
     * Log error with comprehensive details - SessionLogger version
     */
    private static void logError(String toolName, String errorType, Exception e, SessionLogger logger) {
        // Increment error counter for monitoring
        incrementErrorCounter(toolName, errorType);
        
        if (logger != null) {
            JSONObject errorDetails = createErrorDetails(toolName, errorType, e);
            logger.logError("Tool Error: " + toolName, e, errorDetails);
        }
        logToSystemErr(toolName, errorType, e);
    }
    
    /**
     * Log error with comprehensive details - java.util.logging.Logger version
     */
    private static void logError(String toolName, String errorType, Exception e, Logger logger) {
        // Increment error counter for monitoring
        incrementErrorCounter(toolName, errorType);
        
        if (logger != null) {
            JSONObject errorDetails = createErrorDetails(toolName, errorType, e);
            String logMessage = String.format("Tool Error: %s (%s): %s\nDetails: %s", 
                toolName, errorType, e.getMessage(), errorDetails.toString(2));
            logger.log(Level.SEVERE, logMessage, e);
        }
        logToSystemErr(toolName, errorType, e);
    }
    
    private static void incrementErrorCounter(String toolName, String errorType) {
        String key = toolName + "_" + errorType;
        errorCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private static JSONObject createErrorDetails(String toolName, String errorType, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String fullStackTrace = sw.toString();
        
        return new JSONObject()
            .put("tool", toolName)
            .put("error_type", errorType)
            .put("error_class", e.getClass().getName())
            .put("message", e.getMessage())
            .put("stack_trace", fullStackTrace)
            .put("thread", Thread.currentThread().getName())
            .put("timestamp", java.time.Instant.now().toString());
    }
    
    private static void logToSystemErr(String toolName, String errorType, Exception e) {
        System.err.println("[ERROR] " + toolName + " (" + errorType + "): " + e.getMessage());
        if (e.getCause() != null) {
            System.err.println("  Caused by: " + e.getCause().getMessage());
        }
    }
    
    /**
     * Get error statistics for monitoring
     */
    public static JSONObject getErrorStats() {
        JSONObject stats = new JSONObject();
        for (var entry : errorCounts.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().get());
        }
        return stats;
    }
    
    /**
     * Reset error statistics (useful for testing)
     */
    public static void resetErrorStats() {
        errorCounts.clear();
    }
    
    /**
     * Common exception types that tools should catch specifically:
     */
    public static class ToolExceptions {
        // File I/O errors
        public static final Class<java.io.IOException> IO_ERROR = java.io.IOException.class;
        public static final Class<java.io.FileNotFoundException> FILE_NOT_FOUND = java.io.FileNotFoundException.class;
        
        // Validation errors  
        public static final Class<IllegalArgumentException> INVALID_ARGUMENT = IllegalArgumentException.class;
        public static final Class<IllegalStateException> INVALID_STATE = IllegalStateException.class;
        
        // ImageJ errors
        public static final Class<NullPointerException> NULL_POINTER = NullPointerException.class;
        public static final Class<ArrayIndexOutOfBoundsException> ARRAY_BOUNDS = ArrayIndexOutOfBoundsException.class;
        
        // Session errors
        public static final Class<java.util.ConcurrentModificationException> CONCURRENT_MODIFICATION = 
            java.util.ConcurrentModificationException.class;
    }
}