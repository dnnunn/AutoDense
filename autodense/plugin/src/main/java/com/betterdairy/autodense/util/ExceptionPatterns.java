package com.betterdairy.autodense.util;

import org.json.JSONObject;
import com.betterdairy.autodense.session.SessionRecovery;
import java.util.logging.Logger;
import java.io.IOException;

/**
 * Standardized exception handling patterns for AutoDense tools.
 * Converts generic Exception catches to specific types with appropriate error handlers.
 * 
 * Usage:
 * Replace: } catch (Exception e) { return ErrorHandler.handleUnexpectedError(...); }
 * With: } catch (IOException e) { return ExceptionPatterns.handleIO(toolName, e, logger, recovery); }
 *       } catch (IllegalArgumentException e) { return ExceptionPatterns.handleValidation(...); }
 *       } catch (Exception e) { return ExceptionPatterns.handleUnexpected(...); }
 */
public final class ExceptionPatterns {
    
    private ExceptionPatterns() {} // Utility class
    
    /**
     * Standard exception handling pattern for tool methods
     * Use this to wrap tool method implementation for consistent error handling
     */
    public static JSONObject handleToolExecution(String toolName, ToolOperation operation, 
                                               Logger logger, SessionRecovery recovery) {
        try {
            return operation.execute();
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError(toolName, e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError(toolName, e, logger, recovery);
        } catch (NullPointerException e) {
            return ErrorHandler.handleImageProcessingError(toolName, e, logger, recovery);
        } catch (IOException e) {
            return ErrorHandler.handleFileError(toolName, e, logger, recovery);
        } catch (OutOfMemoryError e) {
            return ErrorHandler.handleImageProcessingError(toolName, 
                new RuntimeException("Image too large to process: " + e.getMessage(), e), logger, recovery);
        } catch (ArrayIndexOutOfBoundsException e) {
            return ErrorHandler.handleImageProcessingError(toolName, e, logger, recovery);
        } catch (java.util.ConcurrentModificationException e) {
            return ErrorHandler.handleSessionError(toolName, e, logger, recovery);
        } catch (RuntimeException e) {
            return ErrorHandler.handleUnexpectedError(toolName, e, logger, recovery);
        } catch (Exception e) {
            // Last resort - wrap in RuntimeException for proper handling
            return ErrorHandler.handleUnexpectedError(toolName, new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Functional interface for tool operations
     */
    @FunctionalInterface
    public interface ToolOperation {
        JSONObject execute() throws Exception;
    }
    
    /**
     * Handle image processing specific errors
     */
    public static JSONObject handleImageProcessing(String toolName, Exception e, 
                                                 Logger logger, SessionRecovery recovery) {
        if (e instanceof NullPointerException) {
            return ErrorHandler.handleImageProcessingError(toolName, e, logger, recovery);
        } else if (e instanceof ArrayIndexOutOfBoundsException) {
            return ErrorHandler.handleImageProcessingError(toolName, e, logger, recovery);
        } else {
            return ErrorHandler.handleUnexpectedError(toolName, new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Handle file operation specific errors
     */
    public static JSONObject handleFileOperation(String toolName, Exception e,
                                               Logger logger, SessionRecovery recovery) {
        if (e instanceof IOException) {
            return ErrorHandler.handleFileError(toolName, e, logger, recovery);
        } else if (e instanceof SecurityException) {
            return ErrorHandler.handleValidationError(toolName, e, logger, recovery);
        } else {
            return ErrorHandler.handleUnexpectedError(toolName, new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Handle session/storage specific errors
     */
    public static JSONObject handleSessionOperation(String toolName, Exception e,
                                                  Logger logger, SessionRecovery recovery) {
        if (e instanceof IllegalStateException) {
            return ErrorHandler.handleSessionError(toolName, e, logger, recovery);
        } else if (e instanceof java.util.ConcurrentModificationException) {
            return ErrorHandler.handleSessionError(toolName, e, logger, recovery);
        } else {
            return ErrorHandler.handleUnexpectedError(toolName, new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Handle parameter validation specific errors  
     */
    public static JSONObject handleParameterValidation(String toolName, Exception e,
                                                      Logger logger, SessionRecovery recovery) {
        if (e instanceof IllegalArgumentException) {
            return ErrorHandler.handleValidationError(toolName, e, logger, recovery);
        } else if (e instanceof NumberFormatException) {
            return ErrorHandler.handleValidationError(toolName, 
                new IllegalArgumentException("Invalid numeric parameter: " + e.getMessage(), e), logger, recovery);
        } else {
            return ErrorHandler.handleValidationError(toolName, 
                new IllegalArgumentException("Parameter validation failed", e), logger, recovery);
        }
    }
}
