package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Streamlined colony analysis tools using robust ImageJ-based detection
 * and efficient mutable colony objects. Integrates all components for
 * production-ready colony analysis workflows.
 */
public final class StreamlinedColonyTools {
    
    /**
     * Tool executor following the case structure pattern
     */
    public static JSONObject execute(String toolName, JSONObject args, SessionStore store) {
        return switch (toolName) {
            case "detect_plate" -> detectPlate(args, store);
            case "set_scale_from_plate" -> setScaleFromPlate(args, store);
            case "detect_colonies" -> detectColonies(args, store);
            case "classify_colonies" -> classifyColonies(args, store);
            case "bin_colonies" -> binColonies(args, store);
            case "export_detailed_results" -> exportDetailedResults(args, store);
            case "apply_visual_overlay" -> applyVisualOverlay(args, store);
            default -> error(toolName, "Unknown tool", "tool_name");
        };
    }
    
    /**
     * Detect plate using robust ParticleAnalyzer approach
     */
    private static JSONObject detectPlate(JSONObject args, SessionStore store) {
        try {
            double dishMM = args.optDouble("dish_diameter_mm", 90.0);
            double rimMM = args.optDouble("rim_exclusion_mm", 5.0);
            boolean deskew = args.optBoolean("deskew", false);
            
            SessionStore.ImageRecord rec = store.getImage(args.getString("image_handle"));
            if (rec == null) {
                return error("detect_plate", "Image not found", "image_handle");
            }
            
            // Use comprehensive detection parameters
            ColonyAnalysisParams.DetectionParams detectionParams = 
                ColonyAnalysisParams.DetectionParams.phonePhoto();
            detectionParams.rimExclusionMM = rimMM;
            
            RobustPlateDetector.PlateAnalysisResult result = 
                RobustPlateDetector.analyzeplate(rec.image, dishMM, detectionParams);
            
            if (!result.isValidDetection()) {
                return error("detect_plate", "Poor detection quality: " + result.getQualityReport(), "detection");
            }
            
            // Store plate data in session
            String plateHandle = store.putPlate(args.getString("image_handle"), 
                result.outerRoi(), result.pxPerMM(), dishMM);
            store.setPlateInnerRoi(plateHandle, result.innerRoi());
            
            return ok("detect_plate", new JSONObject()
                .put("plate_handle", plateHandle)
                .put("pixels_per_mm", result.pxPerMM())
                .put("diameter_px", result.diameterPx())
                .put("roundness", result.roundness())
                .put("quality_report", result.getQualityReport()));
                
        } catch (Exception e) {
            return error("detect_plate", e.getMessage(), "processing");
        }
    }
    
    /**
     * Set scale from plate (alias for detect_plate)
     */
    private static JSONObject setScaleFromPlate(JSONObject args, SessionStore store) {
        args.put("rim_exclusion_mm", args.optDouble("rim_exclusion_mm", 5.0));
        return detectPlate(args, store);
    }
    
    /**
     * Detect colonies using robust ParticleAnalyzer
     */
    private static JSONObject detectColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec == null) {
                return error("detect_colonies", "Image not found", "image_handle");
            }
            
            // Get plate data for analysis region
            Roi analysisRoi = store.getPlateInnerRoi(imageHandle);
            if (analysisRoi == null) {
                return error("detect_colonies", "No plate detected. Run detect_plate first.", "plate");
            }
            
            // Build detection parameters
            ColonyAnalysisParams.DetectionParams detectionParams = 
                createDetectionParams(args);
            
            // Robust colony detection
            List<MutableColony> colonies;
            if (args.optBoolean("robust_detection", true)) {
                colonies = RobustColonyDetector.detectColoniesRobust(
                    rec.image, analysisRoi, detectionParams);
            } else {
                colonies = RobustColonyDetector.detectColonies(
                    rec.image, analysisRoi, detectionParams);
            }
            
            // Assess detection quality
            RobustColonyDetector.DetectionQuality quality = 
                RobustColonyDetector.assessDetectionQuality(colonies, detectionParams);
            
            if (!quality.acceptable()) {
                return error("detect_colonies", "Poor detection: " + quality.report(), "detection");
            }
            
            // Store colonies in session
            store.putColonies(imageHandle, colonies);
            
            // Apply overlay for visualization
            ColonyBinner.applyVisualOverlay(rec.image, colonies, 
                rec.image.getCalibration().pixelWidth != 1.0 ? 
                    1.0 / rec.image.getCalibration().pixelWidth : 20.0, true);
            
            return ok("detect_colonies", new JSONObject()
                .put("colony_count", colonies.size())
                .put("detection_quality", quality.score())
                .put("quality_report", quality.report())
                .put("parameters_used", detectionParamsToJson(detectionParams)));
                
        } catch (Exception e) {
            return error("detect_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Classify colonies using streamlined classifier
     */
    private static JSONObject classifyColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            List<MutableColony> colonies = store.getColonies(imageHandle);
            Roi plateRoi = store.getPlateRoi(imageHandle);
            
            if (rec == null || colonies == null || colonies.isEmpty()) {
                return error("classify_colonies", "Image or colonies not found", "data");
            }
            
            // Build comprehensive analysis configuration
            ColonyAnalysisParams.AnalysisConfig config = createAnalysisConfig(args);
            
            // Classify using streamlined classifier
            StreamlinedColonyClassifier.classifyLab(rec.image, colonies, plateRoi, config);
            
            // Update stored colonies
            store.putColonies(imageHandle, colonies);
            
            // Generate classification summary
            var summary = generateClassificationSummary(colonies);
            
            return ok("classify_colonies", new JSONObject()
                .put("classification_summary", summary)
                .put("total_colonies", colonies.size())
                .put("auto_calibrated", config.colorParams.autoCalibrate)
                .put("thresholds_used", colorParamsToJson(config.colorParams)));
                
        } catch (Exception e) {
            return error("classify_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Apply size binning to classified colonies
     */
    private static JSONObject binColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            List<MutableColony> colonies = store.getColonies(imageHandle);
            
            if (colonies == null || colonies.isEmpty()) {
                return error("bin_colonies", "No colonies found", "colonies");
            }
            
            // Get calibration
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            double pxPerMM = rec.image.getCalibration().pixelWidth != 1.0 ? 
                1.0 / rec.image.getCalibration().pixelWidth : 20.0;
            
            // Get size parameters
            double[] sizeEdges = getSizeEdgesFromArgs(args);
            
            // Apply binning
            ColonyBinner.applyBins(colonies, pxPerMM, sizeEdges);
            
            // Update stored colonies
            store.putColonies(imageHandle, colonies);
            
            // Generate bin summary
            var binSummary = ColonyBinner.getBinSummary(colonies);
            
            // Update visualization
            ColonyBinner.applyVisualOverlay(rec.image, colonies, pxPerMM, true);
            
            return ok("bin_colonies", new JSONObject()
                .put("bin_summary", new JSONObject(binSummary))
                .put("size_edges_mm", new JSONArray(sizeEdges))
                .put("total_colonies", colonies.size()));
                
        } catch (Exception e) {
            return error("bin_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Export detailed results with comprehensive measurements
     */
    private static JSONObject exportDetailedResults(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            List<MutableColony> colonies = store.getColonies(imageHandle);
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (colonies == null || colonies.isEmpty()) {
                return error("export_detailed_results", "No colonies found", "colonies");
            }
            
            // Get calibration
            double pxPerMM = rec.image.getCalibration().pixelWidth != 1.0 ? 
                1.0 / rec.image.getCalibration().pixelWidth : 20.0;
            
            // Export to CSV
            String csvData = ColonyBinner.exportToCSV(colonies, pxPerMM);
            
            // Write to file
            String format = args.optString("format", "csv");
            String filename = args.optString("filename", "colony_analysis_results." + format);
            String outputPath = System.getProperty("user.home") + "/" + filename;
            
            try (java.io.FileWriter writer = new java.io.FileWriter(outputPath)) {
                writer.write(csvData);
            }
            
            return ok("export_detailed_results", new JSONObject()
                .put("exported_file", outputPath)
                .put("format", format)
                .put("colony_count", colonies.size())
                .put("columns", "colony_id,x_mm,y_mm,eq_diam_mm,L,a,b,L_bg,a_bg,b_bg,b_delta,dE_bg,snr_L,xgal_binary,xgal_grade,size_bin,label,confidence"));
                
        } catch (Exception e) {
            return error("export_detailed_results", e.getMessage(), "processing");
        }
    }
    
    /**
     * Apply visual overlay with classification colors
     */
    private static JSONObject applyVisualOverlay(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            List<MutableColony> colonies = store.getColonies(imageHandle);
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (colonies == null || rec == null) {
                return error("apply_visual_overlay", "Image or colonies not found", "data");
            }
            
            double pxPerMM = rec.image.getCalibration().pixelWidth != 1.0 ? 
                1.0 / rec.image.getCalibration().pixelWidth : 20.0;
            boolean showLegend = args.optBoolean("show_legend", true);
            
            ColonyBinner.applyVisualOverlay(rec.image, colonies, pxPerMM, showLegend);
            
            return ok("apply_visual_overlay", new JSONObject()
                .put("overlay_applied", true)
                .put("colony_count", colonies.size())
                .put("show_legend", showLegend));
                
        } catch (Exception e) {
            return error("apply_visual_overlay", e.getMessage(), "processing");
        }
    }
    
    // Helper methods for parameter conversion
    
    private static ColonyAnalysisParams.DetectionParams createDetectionParams(JSONObject args) {
        ColonyAnalysisParams.DetectionParams params = ColonyAnalysisParams.DetectionParams.phonePhoto();
        
        params.minDiameterMM = args.optDouble("min_diameter_mm", params.minDiameterMM);
        params.maxDiameterMM = args.optDouble("max_diameter_mm", params.maxDiameterMM);
        params.minCircularity = args.optDouble("min_circularity", params.minCircularity);
        params.splitTouchingColonies = args.optBoolean("split_touching", params.splitTouchingColonies);
        params.rimExclusionMM = args.optDouble("rim_exclusion_mm", params.rimExclusionMM);
        
        return params;
    }
    
    private static ColonyAnalysisParams.AnalysisConfig createAnalysisConfig(JSONObject args) {
        ColonyAnalysisParams.AnalysisConfig config = ColonyAnalysisParams.AnalysisConfig.standardPhoneWorkflow();
        
        // Color parameters
        if (args.has("color_params")) {
            JSONObject colorArgs = args.getJSONObject("color_params");
            config.colorParams.bDeltaPos = colorArgs.optDouble("b_delta_pos", config.colorParams.bDeltaPos);
            config.colorParams.bDeltaMed = colorArgs.optDouble("b_delta_med", config.colorParams.bDeltaMed);
            config.colorParams.bDeltaDark = colorArgs.optDouble("b_delta_dark", config.colorParams.bDeltaDark);
            config.colorParams.autoCalibrate = colorArgs.optBoolean("auto_calibrate", config.colorParams.autoCalibrate);
        }
        
        // Preprocessing parameters
        config.preprocessingParams.flattenBackground = args.optBoolean("flatten_background", true);
        config.preprocessingParams.useLocalBackground = args.optBoolean("use_local_background", true);
        
        return config;
    }
    
    private static double[] getSizeEdgesFromArgs(JSONObject args) {
        if (args.has("size_edges_mm")) {
            JSONArray edgesArray = args.getJSONArray("size_edges_mm");
            double[] edges = new double[edgesArray.length()];
            for (int i = 0; i < edgesArray.length(); i++) {
                edges[i] = edgesArray.getDouble(i);
            }
            return edges;
        }
        
        // Use preset if specified
        String preset = args.optString("size_preset", "standard");
        return switch (preset) {
            case "microcolonies" -> new double[]{0.1, 0.5, 1.0};
            case "large" -> new double[]{0.5, 1.5, 3.0};
            default -> new double[]{0.2, 1.0, 2.0}; // standard
        };
    }
    
    private static JSONObject generateClassificationSummary(List<MutableColony> colonies) {
        JSONObject summary = new JSONObject();
        int pos = 0, neg = 0, uncertain = 0;
        int dark = 0, medium = 0, light = 0;
        
        for (MutableColony colony : colonies) {
            switch (colony.xgalBinary) {
                case "pos" -> {
                    pos++;
                    switch (colony.xgalGrade) {
                        case "dark" -> dark++;
                        case "medium" -> medium++;
                        case "light" -> light++;
                    }
                }
                case "neg" -> neg++;
                case "uncertain" -> uncertain++;
            }
        }
        
        summary.put("total_positive", pos);
        summary.put("total_negative", neg);
        summary.put("total_uncertain", uncertain);
        summary.put("dark_blue", dark);
        summary.put("medium_blue", medium);
        summary.put("light_blue", light);
        
        return summary;
    }
    
    private static JSONObject detectionParamsToJson(ColonyAnalysisParams.DetectionParams params) {
        return new JSONObject()
            .put("min_diameter_mm", params.minDiameterMM)
            .put("max_diameter_mm", params.maxDiameterMM)
            .put("min_circularity", params.minCircularity)
            .put("split_touching", params.splitTouchingColonies)
            .put("rim_exclusion_mm", params.rimExclusionMM);
    }
    
    private static JSONObject colorParamsToJson(ColonyAnalysisParams.ColorParams params) {
        return new JSONObject()
            .put("b_delta_pos", params.bDeltaPos)
            .put("b_delta_med", params.bDeltaMed)
            .put("b_delta_dark", params.bDeltaDark)
            .put("min_DE", params.minDE)
            .put("min_SNR_L", params.minSNRL)
            .put("auto_calibrate", params.autoCalibrate);
    }
    
    // Utility methods for responses
    
    private static JSONObject ok(String tool, JSONObject result) {
        return new JSONObject()
            .put("status", "success")
            .put("tool", tool)
            .put("result", result);
    }
    
    private static JSONObject error(String tool, String message, String field) {
        return new JSONObject()
            .put("status", "error")
            .put("tool", tool)
            .put("error", message)
            .put("field", field);
    }
    
    // Session store interface (placeholder for actual implementation)
    public interface SessionStore {
        record ImageRecord(ImagePlus image) {}
        
        ImageRecord getImage(String handle);
        List<MutableColony> getColonies(String handle);
        void putColonies(String handle, List<MutableColony> colonies);
        String putPlate(String imageHandle, Roi plateRoi, double pxPerMM, double dishMM);
        void setPlateInnerRoi(String plateHandle, Roi innerRoi);
        Roi getPlateRoi(String handle);
        Roi getPlateInnerRoi(String handle);
    }
}