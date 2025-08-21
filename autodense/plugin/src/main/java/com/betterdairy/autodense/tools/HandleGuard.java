package com.betterdairy.autodense.tools;

import org.json.JSONObject;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;

/**
 * Utility class for preventing Gemini from losing image handles
 * Implements server-side guards and prompt guidance
 */
public class HandleGuard {
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    
    public HandleGuard(SessionStore store, SessionRecovery recovery) {
        this.store = store;
        this.recovery = recovery;
    }
    
    /**
     * Apply comprehensive handle protection for a tool call
     * 1. Auto-inject missing handles from session
     * 2. Validate handles with recovery
     * 3. Add guidance to prevent future handle loss
     */
    public HandleValidationResult protectToolCall(JSONObject args, String toolName) {
        // Step 1: Server-side handle injection guard
        String imageHandle = ensureImageHandle(args, toolName);
        
        // Step 2: Enhanced validation with guidance
        JSONObject validation = validateWithGuidance(imageHandle, "image", toolName);
        
        return new HandleValidationResult(imageHandle, validation);
    }
    
    /**
     * Auto-inject missing image_handle from current session
     */
    private String ensureImageHandle(JSONObject args, String toolName) {
        String imageHandle = args.optString("image_handle", "");
        
        // If no handle provided, try to inject current image handle
        if (imageHandle.isEmpty() && store.hasCurrentImage()) {
            imageHandle = store.getCurrentImage().handle;
            System.err.println("WARNING: " + toolName + " missing image_handle - auto-injected: " + imageHandle);
            args.put("image_handle", imageHandle); // Update args for consistency
        }
        
        return imageHandle;
    }
    
    /**
     * Enhanced validation with prompt guidance for Gemini
     */
    private JSONObject validateWithGuidance(String handle, String type, String toolName) {
        JSONObject validation = recovery.validateHandle(handle, type);
        
        if (!validation.getBoolean("valid")) {
            // Add guidance for Gemini to prevent future handle loss
            validation.put("prompt_guidance", 
                "CRITICAL: Always include the image_handle parameter in ALL subsequent tool calls. " +
                "Use image_handle='" + (store.hasCurrentImage() ? store.getCurrentImage().handle : "img_xxx") + 
                "' for all operations on this image. Never omit this parameter.");
        }
        
        return validation;
    }
    
    /**
     * Add handle persistence guidance to successful responses
     */
    public JSONObject addPersistenceGuidance(JSONObject response, String imageHandle) {
        response.put("handle_guidance", 
            "REMEMBER: Use image_handle='" + imageHandle + "' for all subsequent tool calls on this image");
        return response;
    }
    
    /**
     * Result container for handle validation
     */
    public static class HandleValidationResult {
        public final String imageHandle;
        public final JSONObject validation;
        
        public HandleValidationResult(String imageHandle, JSONObject validation) {
            this.imageHandle = imageHandle;
            this.validation = validation;
        }
        
        public boolean isValid() {
            return validation.getBoolean("valid");
        }
        
        public JSONObject createErrorResponse() {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", true);
            errorResponse.put("error_type", "invalid_handle");
            errorResponse.put("message", validation.getString("message"));
            errorResponse.put("validation", validation);
            return errorResponse;
        }
    }
}