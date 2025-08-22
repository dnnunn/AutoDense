package com.betterdairy.autodense.capabilities;

import java.util.*;

/**
 * Comprehensive capability registry for both gel densitometry and colony analysis.
 * Provides natural language descriptions and tool mappings for AI orchestration.
 */
public final class AnalysisCapabilityRegistry {
    
    /**
     * Capability definition with natural language support
     */
    public static record Capability(
        String id,
        String toolName,
        String category,
        String shortDescription,
        String detailedDescription,
        List<String> naturalLanguagePatterns,
        List<String> requiredInputs,
        List<String> optionalInputs,
        List<String> outputs,
        Map<String, Object> defaultParameters,
        List<String> prerequisites,
        String example
    ) {}
    
    /**
     * Get all registered capabilities
     */
    public static List<Capability> getAllCapabilities() {
        List<Capability> capabilities = new ArrayList<>();
        capabilities.addAll(getGelAnalysisCapabilities());
        capabilities.addAll(getColonyAnalysisCapabilities());
        return capabilities;
    }
    
    /**
     * Gel densitometry analysis capabilities
     */
    public static List<Capability> getGelAnalysisCapabilities() {
        return List.of(
            // Image Loading and Preprocessing
            new Capability(
                "open_gel_image",
                "open_image",
                "gel_preprocessing",
                "Load gel electrophoresis image",
                "Opens and loads gel electrophoresis images from file system, supporting common formats (TIFF, JPEG, PNG). Automatically detects image properties and prepares for analysis.",
                List.of(
                    "open gel image", "load gel", "import gel file", "open gel.tif",
                    "load the gel image", "import gel electrophoresis image"
                ),
                List.of("file_path"),
                List.of("auto_rotate", "auto_contrast"),
                List.of("image_handle", "image_width", "image_height", "bit_depth"),
                Map.of("auto_rotate", false, "auto_contrast", true),
                List.of(),
                "open_image({file_path: '/path/to/gel.tif'})"
            ),
            
            new Capability(
                "preprocess_gel",
                "preprocess",
                "gel_preprocessing", 
                "Enhance gel image quality",
                "Applies various image enhancements including rotation, contrast adjustment, background subtraction, and filtering to optimize gel images for analysis.",
                List.of(
                    "enhance gel image", "preprocess gel", "improve gel quality", "adjust contrast",
                    "rotate gel", "enhance image", "apply filters", "background subtraction"
                ),
                List.of("image_handle"),
                List.of("rotate_angle", "flip_horizontal", "flip_vertical", "contrast_factor", "background_subtract", "gaussian_blur"),
                List.of("processed_image_handle", "preprocessing_applied"),
                Map.of("contrast_factor", 1.2, "background_subtract", true, "gaussian_blur", 1.0),
                List.of("open_gel_image"),
                "preprocess({image_handle: 'img_123', rotate_angle: 90, contrast_factor: 1.5})"
            ),
            
            // Lane Detection
            new Capability(
                "detect_gel_lanes",
                "detect_lanes",
                "gel_structure",
                "Automatically detect gel lanes",
                "Identifies individual lanes in gel electrophoresis images using vertical projection analysis. Can detect standard rectangular lanes or custom lane shapes.",
                List.of(
                    "detect lanes", "find lanes", "identify gel lanes", "locate lanes",
                    "detect gel structure", "find gel lanes", "identify lane boundaries"
                ),
                List.of("image_handle"),
                List.of("expected_lanes", "lane_width_px", "manual_positions", "detection_method"),
                List.of("overlay_handle", "lanes_found", "lane_positions", "lane_widths"),
                Map.of("expected_lanes", 12, "detection_method", "projection"),
                List.of("open_gel_image"),
                "detect_lanes({image_handle: 'img_123', expected_lanes: 8})"
            ),
            
            new Capability(
                "adjust_gel_lanes",
                "adjust_lanes",
                "gel_structure",
                "Fine-tune lane positions",
                "Manually adjust lane boundaries and positions for optimal band detection. Allows precise control over lane placement when automatic detection needs refinement.",
                List.of(
                    "adjust lanes", "modify lane positions", "fine-tune lanes", "correct lane placement",
                    "edit lane boundaries", "move lanes", "resize lanes"
                ),
                List.of("image_handle", "lane_adjustments"),
                List.of("overlay_handle"),
                List.of("updated_overlay_handle", "lanes_adjusted"),
                Map.of(),
                List.of("detect_gel_lanes"),
                "adjust_lanes({image_handle: 'img_123', lane_adjustments: [{lane: 1, x_offset: 5}]})"
            ),
            
            // Band Detection and Analysis
            new Capability(
                "detect_gel_bands",
                "detect_bands",
                "gel_analysis",
                "Detect protein/DNA bands in lanes",
                "Automatically identifies bands within gel lanes using 1D profile analysis. Detects peaks corresponding to protein or DNA bands with configurable sensitivity.",
                List.of(
                    "detect bands", "find bands", "identify bands", "locate protein bands",
                    "find DNA bands", "detect gel bands", "band detection", "peak detection"
                ),
                List.of("image_handle"),
                List.of("lane_handle", "sensitivity", "min_band_height", "background_method"),
                List.of("bands_detected", "band_positions", "band_intensities"),
                Map.of("sensitivity", 0.1, "min_band_height", 10.0, "background_method", "rolling_ball"),
                List.of("detect_gel_lanes"),
                "detect_bands({image_handle: 'img_123', sensitivity: 0.15})"
            ),
            
            new Capability(
                "quantify_gel_bands",
                "quantify_bands",
                "gel_analysis",
                "Measure band intensities and molecular weights",
                "Quantifies band intensities using various methods (peak area, peak height, volumetric) and estimates molecular weights using calibration curves from ladder lanes.",
                List.of(
                    "quantify bands", "measure band intensity", "calculate molecular weight",
                    "analyze band intensity", "measure bands", "band quantification", "intensity analysis"
                ),
                List.of("image_handle"),
                List.of("quantification_method", "background_method", "calibration_lane", "molecular_weight_standard"),
                List.of("band_measurements", "molecular_weights", "relative_intensities"),
                Map.of("quantification_method", "peak_area", "background_method", "local_minimum"),
                List.of("detect_gel_bands"),
                "quantify_bands({image_handle: 'img_123', quantification_method: 'peak_area'})"
            ),
            
            // Band Assist Feature
            new Capability(
                "enable_band_assist",
                "enable_band_assist",
                "gel_interaction",
                "Enable user-assisted band identification",
                "Activates interactive mode where users can click on bands to automatically find corresponding bands across all lanes based on relative mobility (Rf) values.",
                List.of(
                    "enable band assist", "turn on band assist", "activate band selection",
                    "enable clicking mode", "start band assist", "interactive band selection"
                ),
                List.of("image_handle"),
                List.of("search_window_px", "min_prominence", "rf_tolerance"),
                List.of("band_assist_enabled", "lanes_available"),
                Map.of("search_window_px", 20, "min_prominence", 0.05, "rf_tolerance", 0.02),
                List.of("detect_gel_lanes"),
                "enable_band_assist({image_handle: 'img_123'})"
            ),
            
            // Export and Visualization
            new Capability(
                "export_gel_results",
                "export_results",
                "gel_export",
                "Export gel analysis results",
                "Exports gel analysis data in various formats (CSV, JSON, PNG) including band measurements, molecular weights, and lane profiles.",
                List.of(
                    "export results", "save gel data", "export to CSV", "save analysis",
                    "export gel results", "save measurements", "export data"
                ),
                List.of("image_handle"),
                List.of("formats", "include_overlay", "filename"),
                List.of("exported_files", "export_summary"),
                Map.of("formats", List.of("csv", "png"), "include_overlay", true),
                List.of("quantify_gel_bands"),
                "export_results({image_handle: 'img_123', formats: ['csv', 'json']})"
            )
        );
    }
    
    /**
     * Colony analysis capabilities
     */
    public static List<Capability> getColonyAnalysisCapabilities() {
        return List.of(
            // Plate Detection
            new Capability(
                "detect_agar_plate",
                "detect_plate",
                "colony_preprocessing",
                "Detect and calibrate agar plate",
                "Automatically detects agar plate boundaries using robust particle analysis, establishes pixel-to-millimeter calibration, and applies rim exclusion for accurate colony analysis.",
                List.of(
                    "detect plate", "find plate", "calibrate plate", "set plate scale",
                    "detect agar plate", "identify plate boundaries", "plate detection"
                ),
                List.of("image_handle"),
                List.of("dish_diameter_mm", "rim_exclusion_mm", "deskew"),
                List.of("plate_handle", "pixels_per_mm", "diameter_px", "roundness", "quality_report"),
                Map.of("dish_diameter_mm", 90.0, "rim_exclusion_mm", 5.0, "deskew", false),
                List.of(),
                "detect_plate({image_handle: 'img_123', dish_diameter_mm: 90})"
            ),
            
            // Colony Detection
            new Capability(
                "detect_colonies",
                "detect_colonies", 
                "colony_detection",
                "Detect bacterial colonies on agar plates",
                "Identifies individual bacterial colonies using ImageJ ParticleAnalyzer with configurable size limits, circularity thresholds, and watershed splitting for touching colonies.",
                List.of(
                    "detect colonies", "find colonies", "identify colonies", "count colonies",
                    "locate bacteria", "colony detection", "bacterial counting", "find bacteria"
                ),
                List.of("image_handle"),
                List.of("min_diameter_mm", "max_diameter_mm", "min_circularity", "split_touching", "robust_detection"),
                List.of("colony_count", "detection_quality", "quality_report", "parameters_used"),
                Map.of("min_diameter_mm", 0.2, "max_diameter_mm", 5.0, "min_circularity", 0.5, "split_touching", true),
                List.of("detect_agar_plate"),
                "detect_colonies({image_handle: 'img_123', min_diameter_mm: 0.3})"
            ),
            
            // X-gal Classification
            new Capability(
                "classify_xgal_colonies",
                "classify_colonies",
                "colony_classification",
                "Classify colonies by X-gal phenotype",
                "Analyzes colony colors in Lab color space to classify X-gal positive (blue) vs negative (white) colonies with semi-quantitative grading (light/medium/dark blue) and auto-calibration.",
                List.of(
                    "classify colonies", "X-gal classification", "identify blue colonies", "color analysis",
                    "classify by color", "blue white screening", "X-gal phenotyping", "colony classification"
                ),
                List.of("image_handle"),
                List.of("color_params", "auto_calibrate", "flatten_background", "use_local_background"),
                List.of("classification_summary", "total_colonies", "auto_calibrated", "thresholds_used"),
                Map.of("auto_calibrate", true, "flatten_background", true, "use_local_background", true),
                List.of("detect_colonies"),
                "classify_colonies({image_handle: 'img_123', auto_calibrate: true})"
            ),
            
            // Size Binning
            new Capability(
                "bin_colonies_by_size",
                "bin_colonies",
                "colony_analysis",
                "Group colonies by size categories",
                "Bins colonies into size categories (tiny/small/medium/large) based on equivalent diameter in millimeters, with user-configurable size edges and combined color+size labels.",
                List.of(
                    "bin colonies", "group by size", "size binning", "categorize colonies",
                    "size classification", "group colonies by size", "size categories"
                ),
                List.of("image_handle"),
                List.of("size_edges_mm", "size_preset"),
                List.of("bin_summary", "size_edges_mm", "total_colonies"),
                Map.of("size_preset", "standard", "size_edges_mm", List.of(0.2, 1.0, 2.0)),
                List.of("classify_xgal_colonies"),
                "bin_colonies({image_handle: 'img_123', size_preset: 'microcolonies'})"
            ),
            
            // Visual Overlay
            new Capability(
                "apply_colony_overlay",
                "apply_visual_overlay",
                "colony_visualization",
                "Apply color-coded visual overlay",
                "Creates intuitive visual overlay with color-coded dots: deep blue for dark X-gal, pale blue for light X-gal, orange for negative, gray for uncertain. Dot size proportional to colony diameter.",
                List.of(
                    "show overlay", "apply overlay", "visualize results", "color code colonies",
                    "show colony colors", "display classification", "visual overlay"
                ),
                List.of("image_handle"),
                List.of("show_legend"),
                List.of("overlay_applied", "colony_count", "show_legend"),
                Map.of("show_legend", true),
                List.of("classify_xgal_colonies"),
                "apply_visual_overlay({image_handle: 'img_123', show_legend: true})"
            ),
            
            // Export
            new Capability(
                "export_colony_results",
                "export_detailed_results",
                "colony_export",
                "Export comprehensive colony analysis data",
                "Exports detailed colony analysis results including all measurements: position, size, Lab color values, classification, and confidence scores in CSV format with standardized columns.",
                List.of(
                    "export colony data", "save colony results", "export to CSV", "save analysis",
                    "export detailed results", "save colony measurements", "download results"
                ),
                List.of("image_handle"),
                List.of("format", "filename"),
                List.of("exported_file", "format", "colony_count", "columns"),
                Map.of("format", "csv", "filename", "colony_analysis_results.csv"),
                List.of("bin_colonies_by_size"),
                "export_detailed_results({image_handle: 'img_123', format: 'csv'})"
            )
        );
    }
    
    /**
     * Find capabilities matching natural language input
     */
    public static List<Capability> findCapabilitiesByNaturalLanguage(String userInput) {
        String input = userInput.toLowerCase().trim();
        List<Capability> matches = new ArrayList<>();
        
        for (Capability capability : getAllCapabilities()) {
            for (String pattern : capability.naturalLanguagePatterns()) {
                if (input.contains(pattern.toLowerCase()) || 
                    calculateSimilarity(input, pattern.toLowerCase()) > 0.7) {
                    matches.add(capability);
                    break;
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Get capabilities by category
     */
    public static List<Capability> getCapabilitiesByCategory(String category) {
        return getAllCapabilities().stream()
            .filter(cap -> cap.category().equals(category))
            .toList();
    }
    
    /**
     * Get suggested workflow for analysis type
     */
    public static List<String> getSuggestedWorkflow(String analysisType) {
        return switch (analysisType.toLowerCase()) {
            case "gel", "gel_analysis", "gel_densitometry" -> List.of(
                "open_gel_image",
                "preprocess_gel", 
                "detect_gel_lanes",
                "detect_gel_bands",
                "quantify_gel_bands",
                "export_gel_results"
            );
            
            case "colony", "colony_analysis", "xgal", "bacteria" -> List.of(
                "detect_agar_plate",
                "detect_colonies",
                "classify_xgal_colonies", 
                "bin_colonies_by_size",
                "apply_colony_overlay",
                "export_colony_results"
            );
            
            default -> List.of();
        };
    }
    
    /**
     * Get capability by tool name
     */
    public static Optional<Capability> getCapabilityByToolName(String toolName) {
        return getAllCapabilities().stream()
            .filter(cap -> cap.toolName().equals(toolName))
            .findFirst();
    }
    
    /**
     * Generate natural language description for workflow
     */
    public static String describeWorkflow(List<String> capabilityIds) {
        StringBuilder description = new StringBuilder();
        description.append("Analysis workflow:\n");
        
        for (int i = 0; i < capabilityIds.size(); i++) {
            String id = capabilityIds.get(i);
            Optional<Capability> cap = getAllCapabilities().stream()
                .filter(c -> c.id().equals(id))
                .findFirst();
                
            if (cap.isPresent()) {
                description.append(String.format("%d. %s - %s\n", 
                    i + 1, cap.get().shortDescription(), cap.get().detailedDescription()));
            }
        }
        
        return description.toString();
    }
    
    /**
     * Simple similarity calculation for fuzzy matching
     */
    private static double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        return (maxLen - editDistance(s1, s2)) / (double) maxLen;
    }
    
    private static int editDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Generate help text for all capabilities
     */
    public static String generateHelpText() {
        StringBuilder help = new StringBuilder();
        help.append("=== AutoDense Analysis Capabilities ===\n\n");
        
        help.append("🧬 GEL DENSITOMETRY:\n");
        for (Capability cap : getCapabilitiesByCategory("gel_preprocessing")) {
            help.append(String.format("• %s: %s\n", cap.shortDescription(), cap.detailedDescription()));
        }
        for (Capability cap : getCapabilitiesByCategory("gel_structure")) {
            help.append(String.format("• %s: %s\n", cap.shortDescription(), cap.detailedDescription()));
        }
        for (Capability cap : getCapabilitiesByCategory("gel_analysis")) {
            help.append(String.format("• %s: %s\n", cap.shortDescription(), cap.detailedDescription()));
        }
        
        help.append("\n🦠 COLONY ANALYSIS:\n");
        for (Capability cap : getCapabilitiesByCategory("colony_preprocessing")) {
            help.append(String.format("• %s: %s\n", cap.shortDescription(), cap.detailedDescription()));
        }
        for (Capability cap : getCapabilitiesByCategory("colony_detection")) {
            help.append(String.format("• %s: %s\n", cap.shortDescription(), cap.detailedDescription()));
        }
        for (Capability cap : getCapabilitiesByCategory("colony_classification")) {
            help.append(String.format("• %s: %s\n", cap.shortDescription(), cap.detailedDescription()));
        }
        
        help.append("\nExample commands:\n");
        help.append("• \"Open gel image and detect 8 lanes\"\n");
        help.append("• \"Detect colonies and classify by X-gal\"\n");
        help.append("• \"Export colony results to CSV\"\n");
        
        return help.toString();
    }
}