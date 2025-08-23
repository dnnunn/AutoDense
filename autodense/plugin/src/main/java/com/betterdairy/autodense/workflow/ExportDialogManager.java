package com.betterdairy.autodense.workflow;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages export dialogs and user preferences for workflow outputs.
 * 
 * Handles CSV, PNG, Excel exports with user dialog prompts and save location selection.
 * Integrates with workflow system to provide consistent export experience.
 */
public class ExportDialogManager {
    
    public enum ExportType {
        CSV("CSV", "Comma-separated values", ".csv"),
        PNG("PNG", "Annotated image with markups", ".png"),
        EXCEL("Excel", "Excel workbook with multiple sheets", ".xlsx"),
        JSON("JSON", "Structured data format", ".json");
        
        private final String displayName;
        private final String description;
        private final String extension;
        
        ExportType(String displayName, String description, String extension) {
            this.displayName = displayName;
            this.description = description;
            this.extension = extension;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getExtension() { return extension; }
    }
    
    public static class ExportRequest {
        public final ExportType type;
        public final String defaultFilename;
        public final String description;
        public final JSONObject data;
        public final boolean required;
        
        public ExportRequest(ExportType type, String defaultFilename, String description, 
                           JSONObject data, boolean required) {
            this.type = type;
            this.defaultFilename = defaultFilename;
            this.description = description;
            this.data = data;
            this.required = required;
        }
    }
    
    public static class ExportDialog {
        public final List<ExportRequest> availableExports;
        public final String workflowName;
        public final String promptMessage;
        public final boolean showDialog;
        
        public ExportDialog(String workflowName, List<ExportRequest> exports, 
                          String promptMessage, boolean showDialog) {
            this.workflowName = workflowName;
            this.availableExports = exports;
            this.promptMessage = promptMessage;
            this.showDialog = showDialog;
        }
    }
    
    /**
     * Create export dialog for Molecular Weight Determination workflow
     */
    public static ExportDialog createMWDeterminationExportDialog(JSONObject results) {
        List<ExportRequest> exports = new ArrayList<>();
        
        // CSV export with MW assignments
        exports.add(new ExportRequest(
            ExportType.CSV,
            "molecular_weights.csv", 
            "MW assignments with uncertainty ranges for all bands",
            results.optJSONObject("mw_assignments"),
            false
        ));
        
        // PNG export with annotated bands
        exports.add(new ExportRequest(
            ExportType.PNG,
            "gel_annotated_mw.png",
            "Gel image with MW labels and calibration curve overlay",
            results.optJSONObject("annotated_image"),
            false
        ));
        
        String promptMessage = 
            "Analysis complete! Would you like to export the results?\n\n" +
            "Available exports:\n" +
            "• CSV: MW assignments with uncertainty ranges\n" +
            "• PNG: Annotated gel image with MW labels\n\n" +
            "Say 'export CSV' or 'export PNG' or 'export both'";
        
        return new ExportDialog("Molecular Weight Determination", exports, promptMessage, true);
    }
    
    /**
     * Create export dialog for Protein Quantification workflow
     */
    public static ExportDialog createProteinQuantificationExportDialog(JSONObject results) {
        List<ExportRequest> exports = new ArrayList<>();
        
        exports.add(new ExportRequest(
            ExportType.EXCEL,
            "protein_quantification.xlsx",
            "Complete quantification results with standards, curve, and measurements",
            results,
            false
        ));
        
        exports.add(new ExportRequest(
            ExportType.CSV,
            "protein_results.csv",
            "Quantified protein amounts with confidence intervals",
            results.optJSONObject("quantification_results"),
            false
        ));
        
        String promptMessage = 
            "Quantification complete! Export options:\n\n" +
            "• Excel: Complete analysis with standards table and calibration curve\n" +
            "• CSV: Protein amounts with confidence intervals\n\n" +
            "Which would you like to export?";
        
        return new ExportDialog("Protein Quantification", exports, promptMessage, true);
    }
    
    /**
     * Create export dialog for Semi-Quantitative PCR workflow
     */
    public static ExportDialog createPCRExportDialog(JSONObject results) {
        List<ExportRequest> exports = new ArrayList<>();
        
        exports.add(new ExportRequest(
            ExportType.CSV,
            "pcr_quantification.csv",
            "PCR ratios with percentile rankings and quality flags",
            results,
            false
        ));
        
        String promptMessage = 
            "PCR analysis complete! Ready to export:\n\n" +
            "• CSV: Sample ratios, percentile rankings, and quality flags\n\n" +
            "Export results?";
        
        return new ExportDialog("Semi-Quantitative PCR", exports, promptMessage, true);
    }
    
    /**
     * Create export dialog for Compare Lanes workflow
     */
    public static ExportDialog createCompareLanesExportDialog(JSONObject results) {
        List<ExportRequest> exports = new ArrayList<>();
        
        exports.add(new ExportRequest(
            ExportType.CSV,
            "lane_comparison_statistics.csv",
            "Statistical comparison results with p-values and fold changes",
            results.optJSONObject("statistics"),
            false
        ));
        
        exports.add(new ExportRequest(
            ExportType.CSV,
            "densitometry_profiles.csv",
            "Raw densitometry profiles for each lane",
            results.optJSONObject("profiles"),
            false
        ));
        
        exports.add(new ExportRequest(
            ExportType.PNG,
            "significant_changes_overlay.png",
            "Gel overlay showing significant changes in green/red",
            results.optJSONObject("overlay_image"),
            false
        ));
        
        String promptMessage = 
            "Comparative analysis complete! Export options:\n\n" +
            "• Statistics CSV: P-values and fold changes\n" +
            "• Profiles CSV: Raw densitometry data\n" +
            "• PNG: Overlay showing significant changes\n\n" +
            "Which exports would you like?";
        
        return new ExportDialog("Compare Lanes", exports, promptMessage, true);
    }
    
    /**
     * Generate natural language prompt for export options
     */
    public static String generateExportPrompt(ExportDialog dialog) {
        return dialog.promptMessage;
    }
    
    /**
     * Parse user response to determine which exports to perform
     */
    public static List<ExportType> parseExportResponse(String userResponse, ExportDialog dialog) {
        List<ExportType> requestedExports = new ArrayList<>();
        String response = userResponse.toLowerCase();
        
        // Check for "all" or "both"
        if (response.contains("all") || response.contains("both")) {
            for (ExportRequest request : dialog.availableExports) {
                requestedExports.add(request.type);
            }
            return requestedExports;
        }
        
        // Check for specific export types
        for (ExportRequest request : dialog.availableExports) {
            String typeName = request.type.getDisplayName().toLowerCase();
            if (response.contains(typeName)) {
                requestedExports.add(request.type);
            }
        }
        
        // Default behavior if unclear
        if (requestedExports.isEmpty()) {
            if (response.contains("yes") || response.contains("export")) {
                // Default to most common export for the workflow
                if (!dialog.availableExports.isEmpty()) {
                    requestedExports.add(dialog.availableExports.get(0).type);
                }
            }
        }
        
        return requestedExports;
    }
    
    /**
     * Generate file save dialog parameters
     */
    public static JSONObject generateSaveDialogParams(ExportType exportType, String defaultFilename) {
        JSONObject params = new JSONObject();
        params.put("export_type", exportType.getDisplayName());
        params.put("file_extension", exportType.getExtension());
        params.put("default_filename", defaultFilename);
        params.put("description", exportType.getDescription());
        
        // Add file type filters
        JSONArray filters = new JSONArray();
        switch (exportType) {
            case CSV -> {
                filters.put(new JSONObject().put("description", "CSV Files").put("extensions", "csv"));
                filters.put(new JSONObject().put("description", "All Files").put("extensions", "*"));
            }
            case PNG -> {
                filters.put(new JSONObject().put("description", "PNG Images").put("extensions", "png"));
                filters.put(new JSONObject().put("description", "All Images").put("extensions", "png,jpg,tiff"));
            }
            case EXCEL -> {
                filters.put(new JSONObject().put("description", "Excel Files").put("extensions", "xlsx"));
                filters.put(new JSONObject().put("description", "All Files").put("extensions", "*"));
            }
            case JSON -> {
                filters.put(new JSONObject().put("description", "JSON Files").put("extensions", "json"));
                filters.put(new JSONObject().put("description", "All Files").put("extensions", "*"));
            }
        }
        params.put("file_filters", filters);
        
        return params;
    }
    
    /**
     * Check if workflow supports export dialogs
     */
    public static boolean workflowSupportsExportDialog(String workflowName) {
        return switch (workflowName) {
            case "Molecular Weight Determination",
                 "Protein Quantification with Standards", 
                 "Semi-Quantitative PCR",
                 "Compare Lanes" -> true;
            default -> false;
        };
    }
}