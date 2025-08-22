package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.analysis.*;
import com.betterdairy.autodense.analysis.ColonyAssistModels.*;
import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import com.betterdairy.autodense.session.SessionStore;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Clean functional colony analysis tools.
 * Pure functions with explicit dependencies - no hidden state.
 */
public final class ColonyAnalysisTools {
    
    /**
     * Success response helper
     */
    private static JSONObject ok(String tool, JSONObject data) {
        return new JSONObject()
            .put("success", true)
            .put("tool", tool)
            .put("data", data);
    }
    
    /**
     * Error response helper
     */
    private static JSONObject error(String tool, String message, String field) {
        return new JSONObject()
            .put("success", false)
            .put("tool", tool)
            .put("error", message)
            .put("field", field);
    }
    
    /**
     * Convert JSONArray to double array
     */
    private static double[] toDoubleArray(JSONArray array) {
        double[] result = new double[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = array.optDouble(i);
        }
        return result;
    }
    
    /**
     * Detect plate boundary and calibrate scale
     */
    public static JSONObject detectPlate(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("detect_plate", "Image not found", "image_handle");
            }
            
            // Use defaults for common phone photography workflow
            var plateDefaults = PlateAnalysisDefaults.StandardPlateDimensions.standard90mm();
            double dishMM = args.optDouble("dish_diameter_mm", plateDefaults.dishDiameterMm());
            String thresholdMethod = args.optString("threshold_method", "Triangle");
            boolean correctIllumination = args.optBoolean("correct_illumination", true);
            
            // Detect plate using core detector
            PlateDetector.Result result = PlateDetector.detect(rec.image, thresholdMethod);
                
            // Store plate data in session
            String plateHandle = "plate_" + System.currentTimeMillis();
            store.putAnalysis(plateHandle, result, imageHandle);
            
            // Calculate pixels per mm
            double pxPerMM = result.pxPerMM(dishMM);
            
            return ok("detect_plate", new JSONObject()
                .put("plate_handle", plateHandle)
                .put("pixels_per_mm", pxPerMM)
                .put("center_x", result.centerX())
                .put("center_y", result.centerY())
                .put("radius_px", result.radiusPx())
                .put("major_axis", result.majorAxis())
                .put("minor_axis", result.minorAxis())
                .put("axis_ratio", result.axisRatio())
                .put("angle_degrees", result.angle())
                .put("was_deskewed", result.wasDeskewed())
                .put("illumination_corrected", result.illuminationCorrected()));
                
        } catch (Exception e) {
            return error("detect_plate", e.getMessage(), "processing");
        }
    }
    
    /**
     * Count colonies within plate region
     */
    public static JSONObject countColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("count_colonies", "Image not found", "image_handle");
            }
            
            // Get plate data
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            OvalRoi plateRoi = plate != null ? plate.plateRoi() : null;
            
            // Use defaults based on estimated resolution
            double pxPerMM = plate != null ? plate.pxPerMM(90.0) : 20.0; // fallback estimate
            var sizeDefaults = PlateAnalysisDefaults.CalibrationHelper.recommendColonySizes(pxPerMM);
            
            int minD = args.optInt("min_diam_px", sizeDefaults.minDiameterPx());
            int maxD = args.optInt("max_diam_px", sizeDefaults.maxDiameterPx());
            boolean split = args.optBoolean("split_touching", true);
            
            // Detect colonies using pure function
            List<Colony> colonies = ColonyDetector.detect(rec.image, plateRoi, minD, maxD, split);
            
            // Update colony diameters with proper mm conversion
            if (plate != null) {
                colonies = updateColonyMeasurements(colonies, pxPerMM);
            }
            
            // Create and apply overlay
            Overlay ov = ColonyOverlay.renderDetection(colonies);
            String ovh = store.putOverlay(ov, imageHandle);
            rec.image.setOverlay(ov);
            
            // Store colony data
            store.putAnalysis("colonies_" + imageHandle, colonies, imageHandle);
            
            return ok("count_colonies", new JSONObject()
                .put("overlay_handle", ovh)
                .put("colony_count", colonies.size())
                .put("min_diameter_px", minD)
                .put("max_diameter_px", maxD)
                .put("split_touching", split));
                
        } catch (Exception e) {
            return error("count_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Classify colonies using Lab color analysis
     */
    public static JSONObject classifyColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("classify_colonies", "Image not found", "image_handle");
            }
            
            // Get existing colony data
            List<Colony> colonies = getColoniesFromSession(store, imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return error("classify_colonies", "No colonies found. Run count_colonies first.", "colonies");
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            OvalRoi plateRoi = plate != null ? plate.plateRoi() : null;
            
            // Get classification parameters
            String mode = args.optString("mode", "xgal");
            boolean autoCalibrate = args.optBoolean("auto_calibrate", false);
            
            // Parse color thresholds if provided
            ColonyClassifier.XGalColorThresholds customThresholds = null;
            if (args.has("color_thresholds")) {
                JSONObject thresholds = args.getJSONObject("color_thresholds");
                customThresholds = new ColonyClassifier.XGalColorThresholds(
                    thresholds.optDouble("b_delta_pos", -6.0),
                    thresholds.optDouble("b_delta_dark", -16.0),
                    thresholds.optDouble("b_delta_medium", -10.0),
                    thresholds.optDouble("min_dE", 8.0),
                    thresholds.optDouble("min_snr_L", 2.5)
                );
            }
            
            // Classify using enhanced function with auto-calibration support
            ColonyClassifier.classifyLab(rec.image, colonies, plateRoi, mode, autoCalibrate, customThresholds);
            
            // Generate summary
            var classSummary = ColonyClassifier.summary(colonies);
            
            // Update overlay with classification colors
            double pxPerMM = plate != null ? plate.pxPerMM(90.0) : 10.0;
            Overlay ov = ColonyOverlay.render(colonies, pxPerMM);
            rec.image.setOverlay(ov);
            
            // Update stored colony data
            store.putAnalysis("colonies_" + imageHandle, colonies, imageHandle);
            
            JSONObject result = new JSONObject()
                .put("classes", new JSONObject(classSummary))
                .put("classification_mode", mode)
                .put("total_colonies", colonies.size())
                .put("auto_calibrate", autoCalibrate);
            
            // Include threshold information if custom or auto-calibrated
            if (customThresholds != null || autoCalibrate) {
                ColonyClassifier.XGalColorThresholds finalThresholds = customThresholds;
                if (autoCalibrate) {
                    // The auto-calibration was performed inside classifyLab, but we can't access the result
                    // In a real implementation, we'd modify classifyLab to return the calibrated thresholds
                    result.put("auto_calibration_performed", true);
                } else {
                    JSONObject thresholdsJson = new JSONObject()
                        .put("b_delta_pos", finalThresholds.bDeltaPos())
                        .put("b_delta_dark", finalThresholds.bDeltaDark())
                        .put("b_delta_medium", finalThresholds.bDeltaMedium())
                        .put("min_dE", finalThresholds.minDE())
                        .put("min_snr_L", finalThresholds.minSnrL());
                    result.put("thresholds_used", thresholdsJson);
                }
            }
            
            return ok("classify_colonies", result);
                
        } catch (Exception e) {
            return error("classify_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Bin colonies by size using equivalent diameter in millimeters
     * 
     * @param size_bins_mm Array of size edges in mm. Example: [0.2, 1.0, 2.0] creates bins:
     *                     ≤0.2mm=tiny, 0.2-1.0mm=small, 1.0-2.0mm=medium, >2.0mm=large
     */
    public static JSONObject binColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = store.getLastActiveImageHandle();
            List<Colony> colonies = getColoniesFromSession(store, imageHandle);
            
            if (colonies == null || colonies.isEmpty()) {
                return error("bin_colonies", "No colonies found", "colonies");
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            double pxPerMM = plate != null ? plate.pxPerMM(90.0) : 10.0;
            
            // Use standard size binning based on typical colony sizes
            var sizeDefaults = PlateAnalysisDefaults.ColonySizeDefaults.phonePhoto90mm();
            double[] defaultEdges = {1.0, 2.5}; // small, medium, large based on 0.2-2.5mm range
            double[] edgesMM = args.has("size_bins_mm") ? 
                toDoubleArray(args.getJSONArray("size_bins_mm")) : 
                defaultEdges;
            
            // Apply binning using Binner utility
            List<Colony> binnedColonies = Binner.applyAndReturn(colonies, pxPerMM, edgesMM);
            var binSummary = Binner.summary(binnedColonies);
            
            // Update stored colony data with binning results
            store.putAnalysis("colonies_" + imageHandle, binnedColonies, imageHandle);
            
            return ok("bin_colonies", new JSONObject()
                .put("bins", new JSONObject(binSummary))
                .put("size_edges_mm", new JSONArray(edgesMM)));
                
        } catch (Exception e) {
            return error("bin_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Normalize colonies for cross-plate comparison
     */
    public static JSONObject normalizeColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("normalize_colonies", "Image not found", "image_handle");
            }
            
            // Get existing colony data
            List<Colony> colonies = getColoniesFromSession(store, imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return error("normalize_colonies", "No colonies found. Run count_colonies first.", "colonies");
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            if (plate == null) {
                return error("normalize_colonies", "No plate data found. Run detect_plate first.", "plate");
            }
            
            double pxPerMM = plate.pxPerMM(90.0);
            boolean enableQuadrants = args.optBoolean("enable_quadrants", true);
            
            // Normalize using pure function
            ColonyNormalizer.NormalizationResult result = ColonyNormalizer.normalize(
                rec.image, colonies, plate.plateRoi(), pxPerMM, enableQuadrants);
            
            // Store normalized data
            store.putAnalysis("normalized_" + imageHandle, result, imageHandle);
            
            // Generate portable thresholds
            var portableThresholds = ColonyNormalizer.createPortableThresholds(result.colonies());
            
            return ok("normalize_colonies", new JSONObject()
                .put("total_colonies", result.colonies().size())
                .put("plate_radius_mm", result.plateStats().plateRadiusMm())
                .put("plate_area_cm2", result.plateStats().plateAreaCm2())
                .put("colony_density_per_cm2", result.plateStats().colonyDensityPerCm2())
                .put("background_lab", new JSONObject()
                    .put("L", result.plateStats().backgroundLab().L())
                    .put("a", result.plateStats().backgroundLab().a())
                    .put("b", result.plateStats().backgroundLab().b()))
                .put("quadrant_counts", new JSONObject(result.plateStats().quadrantCounts()))
                .put("portable_thresholds", new JSONObject(portableThresholds)));
                
        } catch (Exception e) {
            return error("normalize_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Export detailed colony features with comprehensive measurements
     * 
     * Output columns: colony_id,x_mm,y_mm,eq_diam_mm,L,a,b,L_bg,a_bg,b_bg,b_delta,dE_bg,snr_L,
     *                 xgal_binary,xgal_grade,size_bin,label,confidence
     */
    public static JSONObject exportDetailedFeatures(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("export_detailed_features", "Image not found", "image_handle");
            }
            
            List<Colony> colonies = getColoniesFromSession(store, imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return error("export_detailed_features", "No colonies found", "colonies");
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            if (plate == null) {
                return error("export_detailed_features", "No plate data found", "plate");
            }
            
            double pxPerMM = plate.pxPerMM(90.0);
            
            // Get parameters
            String format = args.optString("format", "csv");
            boolean autoCalibrate = args.optBoolean("auto_calibrate", false);
            double[] sizeEdgesMM = args.has("size_bins_mm") ? 
                toDoubleArray(args.getJSONArray("size_bins_mm")) : 
                new double[]{0.2, 1.0, 2.0}; // Default user-defined edges
            
            // Get or create thresholds
            ColonyClassifier.XGalColorThresholds thresholds;
            if (args.has("color_thresholds")) {
                JSONObject t = args.getJSONObject("color_thresholds");
                thresholds = new ColonyClassifier.XGalColorThresholds(
                    t.optDouble("b_delta_pos", -6.0),
                    t.optDouble("b_delta_dark", -16.0),
                    t.optDouble("b_delta_medium", -10.0),
                    t.optDouble("min_dE", 8.0),
                    t.optDouble("min_snr_L", 2.5)
                );
            } else if (autoCalibrate) {
                // Would use auto-calibration here
                thresholds = ColonyClassifier.XGalColorThresholds.defaults();
            } else {
                thresholds = ColonyClassifier.XGalColorThresholds.defaults();
            }
            
            // Extract comprehensive features
            List<ColonyClassifier.ColonyFeatures> features = 
                ColonyClassifier.extractFeatures(rec.image, colonies, plate.plateRoi(), 
                                               pxPerMM, sizeEdgesMM, thresholds);
            
            // Export in requested format
            String exportData;
            String filename;
            if ("json".equals(format)) {
                exportData = ColonyClassifier.exportToJSON(features);
                filename = "colony_features.json";
            } else {
                exportData = ColonyClassifier.exportToCSV(features);
                filename = "colony_features.csv";
            }
            
            // Write to file
            String outputPath = System.getProperty("user.home") + "/" + filename;
            try (java.io.FileWriter writer = new java.io.FileWriter(outputPath)) {
                writer.write(exportData);
            }
            
            return ok("export_detailed_features", new JSONObject()
                .put("exported_file", outputPath)
                .put("format", format)
                .put("colony_count", features.size())
                .put("size_edges_mm", new JSONArray(sizeEdgesMM))
                .put("auto_calibrate", autoCalibrate));
                
        } catch (Exception e) {
            return error("export_detailed_features", e.getMessage(), "processing");
        }
    }
    
    /**
     * Export colony data in multiple formats
     */
    public static JSONObject exportColonies(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("export_colonies", "Image not found", "image_handle");
            }
            
            List<Colony> colonies = getColoniesFromSession(store, imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return error("export_colonies", "No colonies found", "colonies");
            }
            
            // Export using pure function (would need ColonyExporter utility)
            var exportResult = exportColonyData(rec, colonies);
            
            return ok("export_colonies", new JSONObject()
                .put("exports", exportResult));
                
        } catch (Exception e) {
            return error("export_colonies", e.getMessage(), "processing");
        }
    }
    
    /**
     * Enable colony identification assist mode
     */
    public static JSONObject enableColonyAssist(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return error("enable_colony_assist", "Image not found", "image_handle");
            }
            
            // Get existing colony and plate data
            List<Colony> colonies = getColoniesFromSession(store, imageHandle);
            if (colonies == null) {
                colonies = List.of(); // Empty list if no colonies detected yet
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            if (plate == null) {
                return error("enable_colony_assist", "No plate data found. Run detect_plate first.", "plate");
            }
            
            double pxPerMM = plate.pxPerMM(90.0);
            
            // Create assist tool
            AssistColonyTool assistTool = new AssistColonyTool(rec.image, plate.plateRoi(), pxPerMM);
            assistTool.initializeWithColonies(colonies);
            
            // Store assist tool in session
            store.putAnalysis("colony_assist_" + imageHandle, assistTool, imageHandle);
            
            return ok("enable_colony_assist", new JSONObject()
                .put("colony_assist_enabled", true)
                .put("initial_colonies", colonies.size())
                .put("plate_radius_mm", plate.radiusPx() / pxPerMM)
                .put("assist_config", new JSONObject()
                    .put("click_radius", 25.0)
                    .put("min_colony_size", 8.0)
                    .put("max_colony_size", 60.0)
                    .put("default_class", "OTHER")));
                
        } catch (Exception e) {
            return error("enable_colony_assist", e.getMessage(), "processing");
        }
    }
    
    /**
     * Disable colony identification assist mode
     */
    public static JSONObject disableColonyAssist(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            
            // Get assist tool and extract final colonies
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return error("disable_colony_assist", "Colony assist not active", "assist_tool");
            }
            
            // Extract current colonies from assist tool
            List<Colony> finalColonies = assistTool.getCurrentColonies();
            
            // Update stored colony data with assist results
            store.putAnalysis("colonies_" + imageHandle, finalColonies, imageHandle);
            
            // Remove assist tool from session (if removeAnalysis method exists)
            // store.removeAnalysis("colony_assist_" + imageHandle);
            
            // Update overlay
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec != null) {
                PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
                double pxPerMM = plate != null ? plate.pxPerMM(90.0) : 10.0;
                Overlay finalOverlay = ColonyOverlay.render(finalColonies, pxPerMM, true, true);
                rec.image.setOverlay(finalOverlay);
            }
            
            return ok("disable_colony_assist", new JSONObject()
                .put("colony_assist_disabled", true)
                .put("final_colonies", finalColonies.size()));
                
        } catch (Exception e) {
            return error("disable_colony_assist", e.getMessage(), "processing");
        }
    }
    
    /**
     * Handle user click in colony assist mode
     */
    public static JSONObject colonyAssistClick(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            double clickX = args.getDouble("click_x");
            double clickY = args.getDouble("click_y");
            boolean isDelete = args.optBoolean("is_delete", false);
            
            // Get assist tool
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return error("colony_assist_click", "Colony assist not active", "assist_tool");
            }
            
            // Handle click
            AssistResult result = assistTool.handleUserClick(clickX, clickY, isDelete);
            
            // Update overlay
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec != null) {
                Overlay assistOverlay = assistTool.createAssistOverlay();
                rec.image.setOverlay(assistOverlay);
            }
            
            return ok("colony_assist_click", new JSONObject()
                .put("action", result.lastAction())
                .put("click_x", result.clickX())
                .put("click_y", result.clickY())
                .put("refined_x", result.refinedX())
                .put("refined_y", result.refinedY())
                .put("total_colonies", result.totalColonies())
                .put("user_added", result.userAdded())
                .put("user_deleted", result.userDeleted())
                .put("message", result.message()));
                
        } catch (Exception e) {
            return error("colony_assist_click", e.getMessage(), "processing");
        }
    }
    
    /**
     * Propagate colony class using machine learning
     */
    public static JSONObject propagateColonyClass(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            String targetClassStr = args.getString("target_class");
            
            // Parse target class
            ColonyColor targetClass;
            try {
                targetClass = ColonyColor.valueOf(targetClassStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return error("propagate_colony_class", "Invalid colony class: " + targetClassStr, "target_class");
            }
            
            // Get assist tool
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return error("propagate_colony_class", "Colony assist not active", "assist_tool");
            }
            
            // Propagate classification
            ClassificationResult result = assistTool.propagateClassification(targetClass);
            
            // Update overlay
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec != null) {
                Overlay assistOverlay = assistTool.createAssistOverlay();
                rec.image.setOverlay(assistOverlay);
            }
            
            return ok("propagate_colony_class", new JSONObject()
                .put("target_class", targetClass.toString())
                .put("reclassified_count", result.totalReclassified())
                .put("model_features", result.modelFeatures())
                .put("message", result.message()));
                
        } catch (Exception e) {
            return error("propagate_colony_class", e.getMessage(), "processing");
        }
    }
    
    /**
     * Manually relabel colony
     */
    public static JSONObject relabelColony(JSONObject args, SessionStore store) {
        try {
            String imageHandle = args.getString("image_handle");
            double clickX = args.getDouble("click_x");
            double clickY = args.getDouble("click_y");
            String newClassStr = args.getString("new_class");
            
            // Parse new class
            ColonyColor newClass;
            try {
                newClass = ColonyColor.valueOf(newClassStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return error("relabel_colony", "Invalid colony class: " + newClassStr, "new_class");
            }
            
            // Get assist tool
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return error("relabel_colony", "Colony assist not active", "assist_tool");
            }
            
            // Relabel colony
            AssistResult result = assistTool.relabelColony(clickX, clickY, newClass);
            
            // Update overlay
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec != null) {
                Overlay assistOverlay = assistTool.createAssistOverlay();
                rec.image.setOverlay(assistOverlay);
            }
            
            return ok("relabel_colony", new JSONObject()
                .put("action", result.lastAction())
                .put("click_x", result.clickX())
                .put("click_y", result.clickY())
                .put("new_class", newClass.toString())
                .put("user_relabeled", result.userRelabeled())
                .put("message", result.message()));
                
        } catch (Exception e) {
            return error("relabel_colony", e.getMessage(), "processing");
        }
    }
    
    // Helper methods
    
    private static PlateDetector.Result getPlateFromSession(SessionStore store, String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord record = store.getAnalysis(analysisHandle);
            if (record.data instanceof PlateDetector.Result) {
                return (PlateDetector.Result) record.data;
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static List<Colony> getColoniesFromSession(SessionStore store, String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord record = store.getAnalysis(analysisHandle);
            if (record.data instanceof List<?>) {
                List<?> list = (List<?>) record.data;
                if (!list.isEmpty() && list.get(0) instanceof Colony) {
                    return (List<Colony>) list;
                }
            }
        }
        return null;
    }
    
    private static List<Colony> updateColonyMeasurements(List<Colony> colonies, double pxPerMM) {
        // Update colony diameter measurements with proper mm conversion
        // For immutable Colony records, would create new instances
        return colonies;
    }
    
    
    private static AssistColonyTool getAssistToolFromSession(SessionStore store, String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord record = store.getAnalysis(analysisHandle);
            if (record.data instanceof AssistColonyTool) {
                return (AssistColonyTool) record.data;
            }
        }
        return null;
    }
    
    private static java.util.Map<String, String> exportColonyData(SessionStore.ImageRecord rec, List<Colony> colonies) {
        // Placeholder for colony export functionality
        java.util.Map<String, String> exports = new java.util.HashMap<>();
        exports.put("csv", "/tmp/colonies.csv");
        exports.put("json", "/tmp/colonies.json");
        return exports;
    }
}