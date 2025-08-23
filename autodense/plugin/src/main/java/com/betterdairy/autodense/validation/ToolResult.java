package com.betterdairy.autodense.validation;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;

/**
 * STANDARDIZED RESULT ENVELOPE for all AutoDense tools.
 * 
 * ARCHITECTURAL GUARDRAIL: Every tool returns this exact structure.
 * No ad-hoc JSON construction anywhere in tool implementations.
 * 
 * STRUCTURE:
 * {
 *   "ok": true/false,
 *   "tool": "analyze_gel", 
 *   "data": {...},
 *   "warnings": [...],
 *   "audit": {...}
 * }
 * 
 * BENEFITS:
 * - Consistent error handling across all tools
 * - Standardized success/failure detection  
 * - Comprehensive audit trail for every operation
 * - Type-safe result construction
 */
public final class ToolResult {
    
    private final boolean success;
    private final String toolName;
    private final JSONObject data;
    private final List<String> warnings;
    private final JSONObject audit;
    
    private ToolResult(boolean success, String toolName, JSONObject data, 
                      List<String> warnings, JSONObject audit) {
        this.success = success;
        this.toolName = toolName;
        this.data = data != null ? data : new JSONObject();
        this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        this.audit = audit != null ? audit : new JSONObject();
    }
    
    /**
     * Convert to standardized JSON envelope
     */
    public JSONObject toJSON() {
        JSONObject result = new JSONObject();
        result.put("ok", success);
        result.put("tool", toolName);
        result.put("data", data);
        result.put("warnings", new JSONArray(warnings));
        result.put("audit", audit);
        return result;
    }
    
    // =============================================================================
    // SUCCESS RESULT BUILDERS
    // =============================================================================
    
    /**
     * Create successful result with data
     */
    public static ToolResult success(String toolName, JSONObject data) {
        return new Builder(toolName)
            .success()
            .data(data)
            .build();
    }
    
    /**
     * Create successful result with data and warnings
     */
    public static ToolResult success(String toolName, JSONObject data, List<String> warnings) {
        return new Builder(toolName)
            .success()
            .data(data)
            .warnings(warnings)
            .build();
    }
    
    // =============================================================================
    // ERROR RESULT BUILDERS  
    // =============================================================================
    
    /**
     * Create error result with message
     */
    public static ToolResult error(String toolName, String errorCode, String message) {
        JSONObject errorData = new JSONObject()
            .put("error_code", errorCode)
            .put("error_message", message);
            
        return new Builder(toolName)
            .failure()
            .data(errorData)
            .build();
    }
    
    /**
     * Create error result with exception details
     */
    public static ToolResult error(String toolName, String errorCode, Exception exception) {
        JSONObject errorData = new JSONObject()
            .put("error_code", errorCode)
            .put("error_message", exception.getMessage())
            .put("exception_class", exception.getClass().getSimpleName());
            
        return new Builder(toolName)
            .failure()
            .data(errorData)
            .auditField("exception_stack", getStackTraceString(exception))
            .build();
    }
    
    /**
     * Create validation error result
     */
    public static ToolResult validationError(String toolName, List<String> validationErrors) {
        JSONObject errorData = new JSONObject()
            .put("error_code", "validation_failed")
            .put("error_message", "Parameter validation failed")
            .put("validation_errors", new JSONArray(validationErrors));
            
        return new Builder(toolName)
            .failure()
            .data(errorData)
            .build();
    }
    
    // =============================================================================
    // FLUENT BUILDER
    // =============================================================================
    
    public static class Builder {
        private final String toolName;
        private boolean success = true;
        private JSONObject data = new JSONObject();
        private List<String> warnings = new ArrayList<>();
        private JSONObject audit = new JSONObject();
        
        public Builder(String toolName) {
            this.toolName = toolName;
            
            // Always include basic audit info
            audit.put("timestamp", Instant.now().toString());
            audit.put("tool_name", toolName);
        }
        
        public Builder success() {
            this.success = true;
            return this;
        }
        
        public Builder failure() {
            this.success = false;
            return this;
        }
        
        public Builder data(JSONObject data) {
            this.data = data != null ? data : new JSONObject();
            return this;
        }
        
        public Builder dataField(String key, Object value) {
            this.data.put(key, value);
            return this;
        }
        
        public Builder warnings(List<String> warnings) {
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
            return this;
        }
        
        public Builder warning(String warning) {
            this.warnings.add(warning);
            return this;
        }
        
        public Builder audit(JSONObject audit) {
            // Merge with existing audit info, don't overwrite
            if (audit != null) {
                for (String key : audit.keySet()) {
                    this.audit.put(key, audit.get(key));
                }
            }
            return this;
        }
        
        public Builder auditField(String key, Object value) {
            this.audit.put(key, value);
            return this;
        }
        
        /**
         * Add execution timing audit info
         */
        public Builder timing(long startTime, long endTime) {
            audit.put("execution_time_ms", endTime - startTime);
            audit.put("start_time", Instant.ofEpochMilli(startTime).toString());
            audit.put("end_time", Instant.ofEpochMilli(endTime).toString());
            return this;
        }
        
        /**
         * Add parameter clamping audit info
         */
        public Builder parameterClamping(List<String> clampingWarnings) {
            if (clampingWarnings != null && !clampingWarnings.isEmpty()) {
                audit.put("parameters_clamped", true);
                audit.put("clamping_warnings", new JSONArray(clampingWarnings));
                
                // Also add to warnings
                warnings.addAll(clampingWarnings);
            }
            return this;
        }
        
        /**
         * Add handle audit info
         */
        public Builder handles(String inputHandle, String outputHandle) {
            if (inputHandle != null) {
                audit.put("input_handle", inputHandle);
            }
            if (outputHandle != null) {
                audit.put("output_handle", outputHandle);
            }
            return this;
        }
        
        public ToolResult build() {
            return new ToolResult(success, toolName, data, warnings, audit);
        }
    }
    
    // =============================================================================
    // CONVENIENCE ACCESSORS
    // =============================================================================
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isFailure() {
        return !success;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public JSONObject getData() {
        return data;
    }
    
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }
    
    public JSONObject getAudit() {
        return audit;
    }
    
    /**
     * Get error message if this is a failure result
     */
    public String getErrorMessage() {
        if (success) return null;
        return data.optString("error_message", "Unknown error");
    }
    
    /**
     * Get error code if this is a failure result
     */
    public String getErrorCode() {
        if (success) return null;
        return data.optString("error_code", "unknown_error");
    }
    
    // =============================================================================
    // UTILITY METHODS
    // =============================================================================
    
    private static String getStackTraceString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Legacy compatibility: convert to old-style JSONObject with "success" field
     * TODO: Remove this once all tools use the new envelope
     */
    public JSONObject toLegacyJSON() {
        JSONObject legacy = new JSONObject(data.toString());
        legacy.put("success", success);
        
        if (!warnings.isEmpty()) {
            legacy.put("warnings", new JSONArray(warnings));
        }
        
        return legacy;
    }
}