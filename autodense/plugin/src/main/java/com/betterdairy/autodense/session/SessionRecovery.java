package com.betterdairy.autodense.session;

import org.json.JSONObject;
import java.util.List;
import java.time.Instant;

/**
 * Handle recovery and exception management for Gemini interactions.
 * Provides mechanisms to recover from handle loss, memory issues, and session state problems.
 */
public class SessionRecovery {
    
    private final SessionStore store;
    
    public SessionRecovery(SessionStore store) {
        this.store = store;
    }
    
    /**
     * Handle the case where Gemini forgets or provides invalid handles
     */
    public static class HandleException extends Exception {
        private final String invalidHandle;
        private final String expectedType;
        
        public HandleException(String invalidHandle, String expectedType, String message) {
            super(message);
            this.invalidHandle = invalidHandle;
            this.expectedType = expectedType;
        }
        
        public String getInvalidHandle() { return invalidHandle; }
        public String getExpectedType() { return expectedType; }
    }
    
    /**
     * Attempt to recover from missing image handle
     */
    public JSONObject recoverImageHandle(String invalidHandle) {
        JSONObject recovery = new JSONObject();
        
        try {
            // Strategy 1: Use current image if available
            if (store.hasCurrentImage()) {
                SessionStore.ImageRecord current = store.getCurrentImage();
                recovery.put("status", "recovered");
                recovery.put("method", "current_image");
                recovery.put("recovered_handle", current.handle);
                recovery.put("message", String.format(
                    "Invalid handle '%s' - using current image: %s", 
                    invalidHandle, current.handle));
                return recovery;
            }
            
            // Strategy 2: Use most recent image
            String mostRecentHandle = store.getMostRecentImageHandle();
            if (mostRecentHandle != null) {
                recovery.put("status", "recovered");
                recovery.put("method", "most_recent");
                recovery.put("recovered_handle", mostRecentHandle);
                recovery.put("message", String.format(
                    "Invalid handle '%s' - using most recent image: %s", 
                    invalidHandle, mostRecentHandle));
                return recovery;
            }
            
            // Strategy 3: Prompt user to specify
            recovery.put("status", "requires_user_input");
            recovery.put("method", "user_selection");
            recovery.put("available_images", store.listAvailableImages());
            recovery.put("message", String.format(
                "Invalid handle '%s' - please specify which image to use", 
                invalidHandle));
            
        } catch (Exception e) {
            recovery.put("status", "failed");
            recovery.put("error", e.getMessage());
            recovery.put("message", "No images available for recovery");
        }
        
        return recovery;
    }
    
    /**
     * Generate memory refresh prompt for Gemini
     */
    public String generateMemoryRefresh() {
        StringBuilder refresh = new StringBuilder();
        refresh.append("SESSION STATE REFRESH:\\n\\n");
        
        // Current session summary
        refresh.append("CURRENT SESSION:\\n");
        refresh.append("• Session ID: ").append(store.getSessionId()).append("\\n");
        refresh.append("• Active Images: ").append(store.getImageCount()).append("\\n");
        refresh.append("• Total Overlays: ").append(store.getOverlayCount()).append("\\n");
        refresh.append("• Analysis Results: ").append(store.getAnalysisCount()).append("\\n\\n");
        
        // Available handles
        List<String> imageHandles = store.listAvailableImages();
        if (!imageHandles.isEmpty()) {
            refresh.append("AVAILABLE IMAGE HANDLES:\\n");
            for (String handle : imageHandles) {
                SessionStore.ImageRecord img = store.getImage(handle);
                refresh.append("• ").append(handle)
                       .append(" - ").append(img.image.getTitle())
                       .append(" (").append(img.image.getWidth())
                       .append("x").append(img.image.getHeight()).append(")\\n");
            }
            refresh.append("\\n");
        }
        
        // Current image context
        if (store.hasCurrentImage()) {
            SessionStore.ImageRecord current = store.getCurrentImage();
            refresh.append("CURRENT IMAGE CONTEXT:\\n");
            refresh.append("• Handle: ").append(current.handle).append("\\n");
            refresh.append("• Title: ").append(current.image.getTitle()).append("\\n");
            refresh.append("• Size: ").append(current.image.getWidth())
                   .append("x").append(current.image.getHeight()).append("\\n");
            refresh.append("• Has Overlay: ").append(current.currentOverlay != null).append("\\n\\n");
        }
        
        // Recent analysis history
        List<String> recentAnalysis = store.getRecentAnalysisTypes();
        if (!recentAnalysis.isEmpty()) {
            refresh.append("RECENT ANALYSIS HISTORY:\\n");
            for (String analysis : recentAnalysis) {
                refresh.append("• ").append(analysis).append("\\n");
            }
            refresh.append("\\n");
        }
        
        refresh.append("IMPORTANT: Always use these exact handles in your tool calls. ");
        refresh.append("If you need to reference an image, use one of the handles listed above.");
        
        return refresh.toString();
    }
    
    /**
     * Validate handle before tool execution
     */
    public JSONObject validateHandle(String handle, String expectedType) {
        JSONObject validation = new JSONObject();
        
        if (handle == null || handle.trim().isEmpty()) {
            validation.put("valid", false);
            validation.put("error", "missing_handle");
            validation.put("message", "Handle is null or empty");
            validation.put("recovery", recoverFromMissingHandle(expectedType));
            return validation;
        }
        
        switch (expectedType) {
            case "image":
                if (!store.hasImage(handle)) {
                    validation.put("valid", false);
                    validation.put("error", "invalid_image_handle");
                    validation.put("message", "Image handle '" + handle + "' not found");
                    validation.put("recovery", recoverImageHandle(handle));
                    return validation;
                }
                break;
                
            case "overlay":
                if (!store.hasOverlay(handle)) {
                    validation.put("valid", false);
                    validation.put("error", "invalid_overlay_handle");
                    validation.put("message", "Overlay handle '" + handle + "' not found");
                    validation.put("recovery", recoverOverlayHandle(handle));
                    return validation;
                }
                break;
                
            case "analysis":
                if (!store.hasAnalysis(handle)) {
                    validation.put("valid", false);
                    validation.put("error", "invalid_analysis_handle");
                    validation.put("message", "Analysis handle '" + handle + "' not found");
                    validation.put("recovery", recoverAnalysisHandle(handle));
                    return validation;
                }
                break;
        }
        
        validation.put("valid", true);
        validation.put("message", "Handle is valid");
        return validation;
    }
    
    /**
     * Recovery strategies for different handle types
     */
    private JSONObject recoverFromMissingHandle(String expectedType) {
        JSONObject recovery = new JSONObject();
        
        switch (expectedType) {
            case "image":
                if (store.hasCurrentImage()) {
                    SessionStore.ImageRecord current = store.getCurrentImage();
                    recovery.put("suggested_handle", current.handle);
                    recovery.put("method", "use_current_image");
                } else if (store.getImageCount() > 0) {
                    recovery.put("suggested_handle", store.getMostRecentImageHandle());
                    recovery.put("method", "use_most_recent");
                } else {
                    recovery.put("method", "load_new_image");
                    recovery.put("message", "No images available - load an image first");
                }
                break;
                
            default:
                recovery.put("method", "load_image_first");
                recovery.put("message", "Load an image before proceeding");
        }
        
        return recovery;
    }
    
    private JSONObject recoverOverlayHandle(String invalidHandle) {
        JSONObject recovery = new JSONObject();
        
        // Try to find overlays for current image
        if (store.hasCurrentImage()) {
            SessionStore.ImageRecord current = store.getCurrentImage();
            List<String> overlays = store.getOverlaysForImage(current.handle);
            
            if (!overlays.isEmpty()) {
                recovery.put("status", "recovered");
                recovery.put("method", "current_image_overlay");
                recovery.put("recovered_handle", overlays.get(overlays.size() - 1)); // Most recent
                recovery.put("message", "Using most recent overlay for current image");
            } else {
                recovery.put("status", "no_overlays");
                recovery.put("message", "No overlays found - run detection first");
            }
        } else {
            recovery.put("status", "no_current_image");
            recovery.put("message", "No current image - load image and run analysis");
        }
        
        return recovery;
    }
    
    private JSONObject recoverAnalysisHandle(String invalidHandle) {
        JSONObject recovery = new JSONObject();
        
        if (store.hasCurrentImage()) {
            SessionStore.ImageRecord current = store.getCurrentImage();
            List<String> analyses = store.getAnalysesForImage(current.handle);
            
            if (!analyses.isEmpty()) {
                recovery.put("status", "recovered");
                recovery.put("method", "current_image_analysis");
                recovery.put("recovered_handle", analyses.get(analyses.size() - 1)); // Most recent
                recovery.put("available_analyses", analyses);
            } else {
                recovery.put("status", "no_analyses");
                recovery.put("message", "No analysis results found - run analysis first");
            }
        } else {
            recovery.put("status", "no_current_image");
            recovery.put("message", "No current image - load image first");
        }
        
        return recovery;
    }
    
    /**
     * Generate error response with recovery suggestions
     */
    public JSONObject createRecoveryResponse(String operation, Exception e) {
        JSONObject response = new JSONObject();
        response.put("error", true);
        response.put("operation", operation);
        response.put("error_type", e.getClass().getSimpleName());
        response.put("message", e.getMessage());
        response.put("timestamp", Instant.now().toString());
        
        // Add specific recovery suggestions based on error type
        if (e instanceof HandleException) {
            HandleException he = (HandleException) e;
            response.put("invalid_handle", he.getInvalidHandle());
            response.put("expected_type", he.getExpectedType());
            response.put("recovery", validateHandle(he.getInvalidHandle(), he.getExpectedType()));
        } else if (e instanceof IllegalStateException) {
            response.put("recovery", generateStateRecovery());
        } else {
            response.put("recovery", generateGenericRecovery());
        }
        
        // Always include memory refresh
        response.put("memory_refresh", generateMemoryRefresh());
        
        return response;
    }
    
    private JSONObject generateStateRecovery() {
        JSONObject recovery = new JSONObject();
        recovery.put("method", "check_session_state");
        recovery.put("available_images", store.listAvailableImages());
        recovery.put("message", "Check session state and available resources");
        return recovery;
    }
    
    private JSONObject generateGenericRecovery() {
        JSONObject recovery = new JSONObject();
        recovery.put("method", "restart_operation");
        recovery.put("message", "Try restarting the operation with valid handles");
        
        if (store.hasCurrentImage()) {
            recovery.put("current_image", store.getCurrentImage().handle);
        }
        
        return recovery;
    }
}