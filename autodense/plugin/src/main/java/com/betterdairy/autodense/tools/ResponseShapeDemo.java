package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionStore;
import org.json.JSONObject;

/**
 * Demonstration of the new standardized response shapes.
 * Shows how Gemini will receive consistent success/failure responses.
 */
public class ResponseShapeDemo {
    
    public static void main(String[] args) {
        System.out.println("=== AutoDense Response Shape Demo ===\n");
        
        SessionStore store = new SessionStore();
        GelAnalysisTools tools = new GelAnalysisTools(store);
        
        // Test 1: Missing required field
        System.out.println("1. Testing missing_required_field error:");
        JSONObject missingParam = new JSONObject();
        // Not setting image_handle - should trigger handle discipline
        JSONObject result1 = tools.enableBandAssist(missingParam);
        System.out.println(result1.toString(2));
        System.out.println();
        
        // Test 2: Invalid parameter validation (would need to setup preprocessing)
        System.out.println("2. Example of invalid_param error (from subtract_background):");
        JSONObject invalidParam = new JSONObject()
            .put("ok", false)
            .put("error", new JSONObject()
                .put("code", "invalid_param")
                .put("message", "radius_px must be between 10 and 400")
                .put("param", "radius_px"));
        System.out.println(invalidParam.toString(2));
        System.out.println();
        
        // Test 3: Success response format
        System.out.println("3. Example of success response:");
        JSONObject successResponse = new JSONObject()
            .put("ok", true)
            .put("tool", "detect_bands")
            .put("data", new JSONObject()
                .put("bands_detected", 67)
                .put("lanes_processed", 12)
                .put("analysis_handle", "analysis_abc123"))
            .put("warnings", new org.json.JSONArray()
                .put("Lane 3 has low contrast bands"))
            .put("audit", new JSONObject()
                .put("timestamp", "2025-08-22T10:30:45.123Z")
                .put("session_id", "session_demo")
                .put("execution_time_ms", 1250));
        System.out.println(successResponse.toString(2));
        System.out.println();
        
        System.out.println("=== Error Taxonomy for Gemini Self-Correction ===");
        System.out.println("missing_required_field - Add the required parameter");
        System.out.println("invalid_param - Fix parameter value/range");
        System.out.println("image_not_found - Use correct image_handle");
        System.out.println("overlay_not_found - Use correct overlay_handle");
        System.out.println("image_state_conflict - Complete prerequisite step");
        System.out.println("ij_runtime_error - ImageJ processing failed");
        System.out.println();
        
        System.out.println("✅ Response shapes are now consistent and machine-readable!");
    }
}