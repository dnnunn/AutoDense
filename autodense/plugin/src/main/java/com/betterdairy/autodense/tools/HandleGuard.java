package com.betterdairy.autodense.tools;

import org.json.JSONObject;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.util.ErrorHandler;
import com.betterdairy.autodense.session.SessionLogger;

/**
 * Utility class for handle validation with standardized error responses
 * NO AUTO-INJECTION: Fail-fast approach for data integrity
 */
public class HandleGuard {
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    private final SessionLogger logger;
    
    // Standardized error codes for API responses
    private static final String ERROR_MISSING_HANDLE = "MISSING_HANDLE";
    private static final String ERROR_INVALID_HANDLE = "INVALID_HANDLE";
    private static final String ERROR_HANDLE_NOT_FOUND = "HANDLE_NOT_FOUND";
    
    public HandleGuard(SessionStore store, SessionRecovery recovery) {
        this.store = store;
        this.recovery = recovery;
        this.logger = null; // Will be injected when available
    }
    
    public HandleGuard(SessionStore store, SessionRecovery recovery, SessionLogger logger) {
        this.store = store;
        this.recovery = recovery;
        this.logger = logger;
    }
    
    /**
     * Validate handle with standardized error responses - NO AUTO-INJECTION
     * Returns standardized error JSON or null if valid
     */
    public JSONObject validateHandle(JSONObject args, String toolName) {
        String imageHandle = args.optString("image_handle", "");
        
        // FAIL FAST: Missing handle
        if (imageHandle.isEmpty()) {
            logToSession(toolName, "Missing image_handle parameter", "VALIDATION_ERROR");
            return createStandardizedError(ERROR_MISSING_HANDLE, 
                "Missing required parameter: image_handle", "image_handle");
        }
        
        // FAIL FAST: Invalid handle format
        if (!isValidHandleFormat(imageHandle)) {
            logToSession(toolName, "Invalid handle format: " + imageHandle, "VALIDATION_ERROR");
            return createStandardizedError(ERROR_INVALID_HANDLE,
                "Invalid handle format. Expected: img_XXXXXX", "image_handle");
        }
        
        // FAIL FAST: Handle not found in session
        if (store.getImage(imageHandle) == null) {
            logToSession(toolName, "Handle not found in session: " + imageHandle, "VALIDATION_ERROR");
            return createStandardizedError(ERROR_HANDLE_NOT_FOUND,
                "Image handle not found in session", "image_handle");
        }
        
        // Success - handle is valid
        logToSession(toolName, "Handle validation successful: " + imageHandle, "VALIDATION_SUCCESS");
        return null;
    }
    
    /**
     * Create standardized error response - NO INTERNAL GUIDANCE
     */
    private JSONObject createStandardizedError(String errorCode, String message, String parameter) {
        JSONObject error = new JSONObject();
        error.put("ok", false);
        error.put("error_code", errorCode);
        error.put("error_message", message);
        error.put("error_parameter", parameter);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
    
    /**
     * Validate handle format (img_XXXXXX pattern)
     */
    private boolean isValidHandleFormat(String handle) {
        return handle != null && handle.matches("^img_[a-zA-Z0-9]{6}$");
    }
    
    /**
     * Log to session if logger available, otherwise system err
     */
    private void logToSession(String toolName, String message, String level) {
        String logMessage = String.format("[%s] %s: %s", level, toolName, message);
        // Always use system logging for now (SessionLogger integration can be added later)
        // TODO: Integrate with proper SessionLogger when available
        java.util.logging.Logger.getLogger(HandleGuard.class.getName()).info(logMessage);
    }
    
    /**
     * Simplified validation result - error JSONObject or null if valid
     * NO MORE COMPLEX RESULT CONTAINERS - keep it simple and standardized
     */
    @Deprecated
    public static class HandleValidationResult {
        public final String imageHandle;
        public final JSONObject validation;
        
        @Deprecated
        public HandleValidationResult(String imageHandle, JSONObject validation) {
            this.imageHandle = imageHandle;
            this.validation = validation;
        }
        
        @Deprecated
        public boolean isValid() {
            return validation == null || validation.optBoolean("ok", false);
        }
        
        @Deprecated
        public JSONObject createErrorResponse() {
            return validation; // Validation is already standardized error or null
        }
    }
    
    /**
     * Protect tool calls from invalid handles - temporary bridge method
     * @deprecated Use validateHandle instead for consistent error handling
     */
    @Deprecated
    public JSONObject protectToolCall(JSONObject args, String toolName) {
        return validateHandle(args, toolName);
    }
    
    /**
     * Add persistence guidance - no-op for now
     * @deprecated This method is deprecated and does nothing
     */
    @Deprecated
    public JSONObject addPersistenceGuidance(JSONObject response, String guidance) {
        // No-op - persistence guidance not implemented
        return response;
    }
}