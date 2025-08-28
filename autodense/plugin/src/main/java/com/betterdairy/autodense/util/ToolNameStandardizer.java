package com.betterdairy.autodense.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility for standardizing tool names across ErrorHandler calls.
 * 
 * Ensures consistent naming conventions and provides mapping from
 * method names to standardized tool names for error handling.
 */
public final class ToolNameStandardizer {
    
    // Standard tool name mappings
    private static final Map<String, String> TOOL_NAME_MAPPINGS = new HashMap<>();
    
    // Patterns for automatic tool name generation
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");
    
    static {
        initializeStandardMappings();
    }
    
    private ToolNameStandardizer() {} // Utility class
    
    /**
     * Initialize standard tool name mappings
     */
    private static void initializeStandardMappings() {
        // Gel Analysis Tools
        TOOL_NAME_MAPPINGS.put("openImage", "open_image");
        TOOL_NAME_MAPPINGS.put("detectLanes", "detect_lanes");
        TOOL_NAME_MAPPINGS.put("detectBands", "detect_bands");
        TOOL_NAME_MAPPINGS.put("quantifyBands", "quantify_bands");
        TOOL_NAME_MAPPINGS.put("adjustLanes", "adjust_lanes");
        TOOL_NAME_MAPPINGS.put("enableBandAssist", "enable_band_assist");
        TOOL_NAME_MAPPINGS.put("disableBandAssist", "disable_band_assist");
        TOOL_NAME_MAPPINGS.put("configureBandAssist", "configure_band_assist");
        TOOL_NAME_MAPPINGS.put("exportResults", "export_results");
        TOOL_NAME_MAPPINGS.put("renderOverlayPng", "render_overlay_png");
        TOOL_NAME_MAPPINGS.put("createLabeledReference", "create_labeled_reference");
        TOOL_NAME_MAPPINGS.put("clearSession", "clear_session");
        TOOL_NAME_MAPPINGS.put("refreshCanvas", "refresh_canvas");
        TOOL_NAME_MAPPINGS.put("preprocess", "preprocess_image");
        
        // Gel Quantification Tools
        TOOL_NAME_MAPPINGS.put("calibrateMolecularWeight", "calibrate_molecular_weight");
        TOOL_NAME_MAPPINGS.put("calibrateStandardCurve", "calibrate_standard_curve");
        TOOL_NAME_MAPPINGS.put("compareLanes", "compare_lanes");
        TOOL_NAME_MAPPINGS.put("normalizeIntensities", "normalize_intensities");
        TOOL_NAME_MAPPINGS.put("exportVolcanoPlot", "export_volcano_plot");
        TOOL_NAME_MAPPINGS.put("exportForNotebook", "export_for_notebook");
        TOOL_NAME_MAPPINGS.put("exportForPresentation", "export_for_presentation");
        
        // Colony Analysis Tools
        TOOL_NAME_MAPPINGS.put("detectPlate", "detect_plate");
        TOOL_NAME_MAPPINGS.put("detectColonies", "detect_colonies");
        TOOL_NAME_MAPPINGS.put("countColonies", "count_colonies");
        TOOL_NAME_MAPPINGS.put("classifyColonies", "classify_colonies");
        TOOL_NAME_MAPPINGS.put("measureColonies", "measure_colonies");
        TOOL_NAME_MAPPINGS.put("binColonies", "bin_colonies");
        TOOL_NAME_MAPPINGS.put("normalizeColonies", "normalize_colonies");
        TOOL_NAME_MAPPINGS.put("exportColonies", "export_colonies");
        TOOL_NAME_MAPPINGS.put("exportDetailedFeatures", "export_detailed_features");
        TOOL_NAME_MAPPINGS.put("enableColonyAssist", "enable_colony_assist");
        TOOL_NAME_MAPPINGS.put("disableColonyAssist", "disable_colony_assist");
        TOOL_NAME_MAPPINGS.put("annotate", "annotate_colonies");
        TOOL_NAME_MAPPINGS.put("runMacro", "run_macro");
        TOOL_NAME_MAPPINGS.put("export", "export_data");
        TOOL_NAME_MAPPINGS.put("compareWithBaseline", "compare_with_baseline");
        
        // Canonical Tools
        TOOL_NAME_MAPPINGS.put("analyze_gel", "analyze_gel");
        TOOL_NAME_MAPPINGS.put("adjust_gel", "adjust_gel");
        TOOL_NAME_MAPPINGS.put("export_gel", "export_gel");
        TOOL_NAME_MAPPINGS.put("analyze_plate", "analyze_plate");
        TOOL_NAME_MAPPINGS.put("adjust_plate", "adjust_plate");
        TOOL_NAME_MAPPINGS.put("export_plate", "export_plate");
        TOOL_NAME_MAPPINGS.put("preprocess_image", "preprocess_image");
        TOOL_NAME_MAPPINGS.put("clear_session", "clear_session");
        
        // Specialized Analysis Tools
        TOOL_NAME_MAPPINGS.put("digestKinetics", "digest_kinetics");
        TOOL_NAME_MAPPINGS.put("compareTreatments", "compare_treatments");
        TOOL_NAME_MAPPINGS.put("analyzeTimePoint", "analyze_timepoint");
        TOOL_NAME_MAPPINGS.put("trackColonies", "track_colonies");
        TOOL_NAME_MAPPINGS.put("alignPlates", "align_plates");
        TOOL_NAME_MAPPINGS.put("measureBlueness", "measure_blueness");
        TOOL_NAME_MAPPINGS.put("calculateGrowthRate", "calculate_growth_rate");
        
        // Generic mappings that should be avoided
        TOOL_NAME_MAPPINGS.put("assay_tool", "detect_colonies"); // Default for colony operations
        TOOL_NAME_MAPPINGS.put("gel_tool", "analyze_gel");       // Default for gel operations
    }
    
    /**
     * Get standardized tool name for a given method or operation
     */
    public static String getStandardToolName(String methodName) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return "unknown_tool";
        }
        
        String trimmed = methodName.trim();
        
        // Check direct mapping first
        if (TOOL_NAME_MAPPINGS.containsKey(trimmed)) {
            return TOOL_NAME_MAPPINGS.get(trimmed);
        }
        
        // Convert camelCase to snake_case if not found
        String snakeCase = camelCaseToSnakeCase(trimmed);
        
        // Check if snake_case version exists in mappings
        if (TOOL_NAME_MAPPINGS.containsKey(snakeCase)) {
            return TOOL_NAME_MAPPINGS.get(snakeCase);
        }
        
        // Return snake_case version as fallback
        return snakeCase;
    }
    
    /**
     * Convert camelCase to snake_case
     */
    private static String camelCaseToSnakeCase(String camelCase) {
        return CAMEL_CASE_PATTERN.matcher(camelCase)
            .replaceAll("$1_$2")
            .toLowerCase();
    }
    
    /**
     * Validate that a tool name follows standard conventions
     */
    public static boolean isValidToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = toolName.trim();
        
        // Should be snake_case with only letters, numbers, and underscores
        return trimmed.matches("^[a-z][a-z0-9_]*[a-z0-9]$") || 
               trimmed.matches("^[a-z][a-z0-9_]*$");
    }
    
    /**
     * Get tool category from tool name
     */
    public static String getToolCategory(String toolName) {
        if (toolName == null) return "unknown";
        
        String standardName = getStandardToolName(toolName);
        
        // Gel analysis tools
        if (standardName.contains("lane") || standardName.contains("band") || 
            standardName.contains("gel") || standardName.contains("molecular_weight") ||
            standardName.contains("digest") || standardName.contains("treatment")) {
            return "gel_analysis";
        }
        
        // Colony analysis tools  
        if (standardName.contains("colony") || standardName.contains("plate") || 
            standardName.contains("count") || standardName.contains("classify") ||
            standardName.contains("blueness") || standardName.contains("growth")) {
            return "colony_analysis";
        }
        
        // Export tools
        if (standardName.contains("export") || standardName.contains("render") ||
            standardName.contains("create") && standardName.contains("reference")) {
            return "export";
        }
        
        // Preprocessing tools
        if (standardName.contains("preprocess") || standardName.contains("enhance") ||
            standardName.contains("filter")) {
            return "preprocessing";
        }
        
        // Session management
        if (standardName.contains("session") || standardName.contains("clear") ||
            standardName.contains("refresh")) {
            return "session_management";
        }
        
        return "general";
    }
    
    /**
     * Get all standard tool names by category
     */
    public static Map<String, java.util.List<String>> getToolNamesByCategory() {
        Map<String, java.util.List<String>> categorized = new HashMap<>();
        
        for (String toolName : TOOL_NAME_MAPPINGS.values()) {
            String category = getToolCategory(toolName);
            categorized.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(toolName);
        }
        
        return categorized;
    }
    
    /**
     * Suggest correct tool name for common misspellings or variations
     */
    public static String suggestToolName(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "unknown_tool";
        }
        
        String standardName = getStandardToolName(input);
        
        // Check for common incorrect patterns and suggest corrections
        if (input.equals("assay_tool")) {
            return "detect_colonies"; // Most common colony operation
        }
        
        if (input.equals("gel_tool")) {
            return "analyze_gel"; // Most common gel operation
        }
        
        if (input.contains("run_xgal_macro")) {
            return "run_macro";
        }
        
        if (input.contains("annotate_colonies")) {
            return "annotate_colonies";
        }
        
        if (input.contains("export_colonies")) {
            return "export_colonies";
        }
        
        return standardName;
    }
    
    /**
     * Create a mapping report for debugging tool name issues
     */
    public static String createMappingReport() {
        StringBuilder report = new StringBuilder();
        report.append("Tool Name Standardization Report\n");
        report.append("=====================================\n\n");
        
        Map<String, java.util.List<String>> byCategory = getToolNamesByCategory();
        
        for (Map.Entry<String, java.util.List<String>> entry : byCategory.entrySet()) {
            report.append("Category: ").append(entry.getKey().toUpperCase()).append("\n");
            report.append("-".repeat(entry.getKey().length() + 10)).append("\n");
            
            for (String toolName : entry.getValue()) {
                report.append("  • ").append(toolName).append("\n");
            }
            report.append("\n");
        }
        
        return report.toString();
    }
}
