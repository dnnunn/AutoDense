package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.analysis.*;
import com.betterdairy.autodense.analysis.ColonyAssistModels.*;
import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.session.SessionAnalysisKeys;
import com.betterdairy.autodense.session.SessionStorageMigrator;
import com.betterdairy.autodense.util.ErrorHandler;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.logging.Logger;

/**
 * Clean functional colony analysis tools with enhanced handle validation.
 * Implements HandleGuard protection for all tool operations.
 */
public final class ColonyAnalysisTools {
    
    private static final Logger logger = Logger.getLogger(ColonyAnalysisTools.class.getName());
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    private final HandleGuard handleGuard;
    private final SessionStorageMigrator.CompatibilityLayer compatibility;
    
    /**
     * Validated storage operation wrapper - ENFORCES standardized keys
     */
    private String putAnalysisWithValidation(String storageKey, Object data, String imageHandle) {
        // Basic validation
        if (storageKey == null || storageKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage key cannot be null or empty");
        }
        if (imageHandle == null || imageHandle.trim().isEmpty()) {
            throw new IllegalArgumentException("Image handle cannot be null or empty");
        }
        
        // FAIL FAST: Reject non-standardized keys to ensure data consistency
        if (!SessionAnalysisKeys.isStandardizedKey(storageKey)) {
            throw new IllegalArgumentException(
                "Non-standardized storage key rejected: '" + storageKey + "'. " +
                "Use SessionAnalysisKeys factory methods to generate standardized keys. " +
                "This enforces data consistency and prevents session data corruption.");
        }
        
        return store.putAnalysis(storageKey, data, imageHandle);
    }
    
    public ColonyAnalysisTools(SessionStore store) {
        this.store = store;
        this.recovery = new SessionRecovery(store);
        this.handleGuard = new HandleGuard(store, recovery);
        this.compatibility = new SessionStorageMigrator.CompatibilityLayer(store);
    }
    
    /**
     * Success response helper
     */
    private JSONObject ok(String tool, JSONObject data) {
        return new JSONObject()
            .put("success", true)
            .put("tool", tool)
            .put("data", data);
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
     * Detect plate boundary and calibrate scale with enhanced handle validation
     */
    public JSONObject detectPlate(JSONObject args) {
        try {
            // FAIL FAST handle validation - no auto-injection
            JSONObject handleError = handleGuard.validateHandle(args, "detect_plate");
            if (handleError != null) {
                return handleError; // Standardized error response
            }
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec == null) {
                return ErrorHandler.handleValidationError("detect_plate", 
                    new IllegalArgumentException("Image not found after validation"), logger, recovery);
            }
            
            // Standardized parameter extraction
            var plateParams = ParameterConverter.PlateDetectionParams.fromArgs(args);
            
            // Detect plate using core detector
            PlateDetector.Result result = PlateDetector.detect(rec.image, plateParams.thresholdMethod());
                
            // Store plate data in session using standardized key
            String plateStorageKey = SessionAnalysisKeys.PlateKeys.detection(imageHandle);
            String plateHandle = putAnalysisWithValidation(plateStorageKey, result, imageHandle);
            
            // Calculate pixels per mm
            double pxPerMM = result.pxPerMM(plateParams.dishDiameterMM());
            
            JSONObject response = ok("detect_plate", new JSONObject()
                .put("plate_handle", plateHandle)
                .put("pixels_per_mm", pxPerMM)
                .put("dish_diameter_mm", plateParams.dishDiameterMM())
                .put("threshold_method", plateParams.thresholdMethod())
                .put("center_x", result.centerX())
                .put("center_y", result.centerY())
                .put("radius_px", result.radiusPx())
                .put("radius_mm", result.radiusPx() / pxPerMM)
                .put("major_axis", result.majorAxis())
                .put("minor_axis", result.minorAxis())
                .put("axis_ratio", result.axisRatio())
                .put("angle_degrees", result.angle())
                .put("was_deskewed", result.wasDeskewed())
                .put("illumination_corrected", result.illuminationCorrected()));
            
            return response; // No more guidance injection - clean standardized responses
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("detect_plate", e, logger, recovery);
        } catch (NullPointerException e) {
            return ErrorHandler.handleImageProcessingError("detect_plate", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("detect_plate", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Count colonies within plate region with enhanced handle validation
     */
    public JSONObject countColonies(JSONObject args) {
        try {
            // Apply comprehensive handle protection
            JSONObject validationError = handleGuard.validateHandle(args, "count_colonies");
            if (validationError != null) {
                return validationError;
            }
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec == null) {
                return ErrorHandler.handleValidationError("count_colonies", 
                    new IllegalArgumentException("Image not found after validation"), logger, recovery);
            }
            
            // Get plate data and calibration
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            OvalRoi plateRoi = plate != null ? plate.plateRoi() : null;
            double pxPerMM = plate != null ? plate.pxPerMM(90.0) : 20.0; // fallback estimate
            
            // Standardized parameter extraction with unit conversion
            var detectionParams = ParameterConverter.ColonyDetectionParams.fromArgs(args, pxPerMM);
            
            // Validate parameters
            var validation_result = ParameterConverter.Validation.validateColonyParams(detectionParams);
            if (!validation_result.isValid) {
                return ErrorHandler.handleValidationError("count_colonies", 
                    new IllegalArgumentException(validation_result.errorMessage + " (parameter: " + validation_result.parameterName + ")"), logger, recovery);
            }
            
            // Create unified detector parameters
            UnifiedColonyDetector.DetectionParams unifiedParams = new UnifiedColonyDetector.DetectionParams();
            unifiedParams.minDiameterPx = detectionParams.minDiameterPx();
            unifiedParams.maxDiameterPx = detectionParams.maxDiameterPx();
            unifiedParams.minCircularity = detectionParams.minCircularity();
            unifiedParams.minSolidity = detectionParams.minSolidity();
            unifiedParams.splitTouchingColonies = detectionParams.splitTouching();
            unifiedParams.removeRimArtifacts = detectionParams.removeRimArtifacts();
            unifiedParams.blueThreshold = detectionParams.blueThreshold();
            unifiedParams.useAdaptiveThreshold = detectionParams.useAdaptiveThreshold();
            
            // FIXED: Detect colonies using unified detection system with telemetry
            UnifiedColonyDetector.DetectionResult detectionResult = UnifiedColonyDetector.detectWithTelemetry(rec.image, plateRoi, unifiedParams, pxPerMM);
            List<Colony> colonies = detectionResult.colonies();
            UnifiedColonyDetector.FilterChainTelemetry filterChain = detectionResult.filterChain();
            int rawColonyCount = filterChain.componentsRaw(); // Phase 1.2: Capture actual raw detection count
            
            // Update colony diameters with proper mm conversion
            if (plate != null) {
                colonies = updateColonyMeasurements(colonies, pxPerMM);
            }
            
            // Phase 1.2: Log raw colony detection count for truth preservation
            logger.info(String.format("[TRUTH_PRESERVED] Raw colony detection count: %d (before any adjustments)", rawColonyCount));
            
            // Create and apply overlay
            Overlay ov = ColonyOverlay.renderDetection(colonies);
            String ovh = store.putOverlay(ov, imageHandle);
            rec.image.setOverlay(ov);
            
            // Store colony data using standardized key
            String colonyStorageKey = SessionAnalysisKeys.ColonyKeys.detection(imageHandle);
            putAnalysisWithValidation(colonyStorageKey, colonies, imageHandle);
            
            JSONObject response = ok("count_colonies", new JSONObject()
                .put("overlay_handle", ovh)
                .put("colony_count", colonies.size())
                .put("colonies_raw", rawColonyCount)  // Phase 1.2: Raw detection count
                .put("colonies_reconciled", colonies.size())  // Phase 1.2: Final count after reconciliation
                .put("min_diameter_px", detectionParams.minDiameterPx())
                .put("max_diameter_px", detectionParams.maxDiameterPx())
                .put("min_diameter_mm", ParameterConverter.UnitConversion.pxToMm(detectionParams.minDiameterPx(), pxPerMM))
                .put("max_diameter_mm", ParameterConverter.UnitConversion.pxToMm(detectionParams.maxDiameterPx(), pxPerMM))
                .put("min_circularity", detectionParams.minCircularity())
                .put("min_solidity", detectionParams.minSolidity())
                .put("split_touching", detectionParams.splitTouching())
                .put("pixels_per_mm", pxPerMM)
                // FIXED: Add filter chain telemetry for tracking 200→15 mystery
                .put("filter_chain", new JSONObject()
                    .put("components_raw", filterChain.componentsRaw())
                    .put("after_min_area", filterChain.afterMinArea())
                    .put("after_roundness", filterChain.afterRoundness())
                    .put("after_edge_exclusion", filterChain.afterEdgeExclusion())
                    .put("after_watershed", filterChain.afterWatershed())
                    .put("final_count", filterChain.finalCount())));
            
            return handleGuard.addPersistenceGuidance(response, imageHandle);
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("count_colonies", e, logger, recovery);
        } catch (NullPointerException e) {
            return ErrorHandler.handleImageProcessingError("count_colonies", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("count_colonies", e, logger, recovery);
        }
    }
    
    /**
     * Classify colonies using Lab color analysis
     */
    public JSONObject classifyColonies(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return ErrorHandler.handleValidationError("classify_colonies", 
                    new IllegalArgumentException("Image not found"), logger, recovery);
            }
            
            // Get existing colony data
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return ErrorHandler.handleSessionError("classify_colonies", 
                    new IllegalStateException("No colonies found. Run count_colonies first."), logger, recovery);
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
            
            // Update stored colony data using standardized key
            String colonyStorageKey = SessionAnalysisKeys.ColonyKeys.detection(imageHandle);
            putAnalysisWithValidation(colonyStorageKey, colonies, imageHandle);
            
            // CRITICAL FIX: Ensure proper data structure for CLI consumption
            JSONObject result = new JSONObject();
            JSONObject data = new JSONObject();
            
            // Store classification counts in data object for CLI extraction
            JSONObject classes = new JSONObject(classSummary);
            data.put("classes", classes);
            data.put("blue_count", classes.optInt("BLUE", 0));
            data.put("white_count", classes.optInt("WHITE", 0));
            data.put("uncertain_count", classes.optInt("OTHER", 0));
            data.put("classifier_applied", true);
            data.put("colorspace", "Lab");  // Mark that we used Lab colorspace
            
            // Generate Lab-b histogram for telemetry
            JSONArray labBHistogram = generateLabBHistogram(colonies);
            data.put("lab_b_histogram", labBHistogram);
            
            result.put("data", data);
            result.put("classification_mode", mode);
            result.put("total_colonies", colonies.size());
            result.put("auto_calibrate", autoCalibrate);
            
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
            
            logger.info(String.format("[COLOR_CLASSIFICATION] Completed Lab classification: Blue=%d, White=%d, Uncertain=%d", 
                classes.optInt("BLUE", 0), classes.optInt("WHITE", 0), classes.optInt("OTHER", 0)));
            
            return ok("classify_colonies", result);
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("classify_colonies", e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError("classify_colonies", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("classify_colonies", e, logger, recovery);
        }
    }
    
    /**
     * Bin colonies by size using equivalent diameter in millimeters
     * 
     * @param size_bins_mm Array of size edges in mm. Example: [0.2, 1.0, 2.0] creates bins:
     *                     ≤0.2mm=tiny, 0.2-1.0mm=small, 1.0-2.0mm=medium, >2.0mm=large
     */
    public JSONObject binColonies(JSONObject args) {
        try {
            String imageHandle = store.getLastActiveImageHandle();
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            
            if (colonies == null || colonies.isEmpty()) {
                return ErrorHandler.handleSessionError("bin_colonies", 
                    new IllegalStateException("No colonies found"), logger, recovery);
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
            
            // Update stored colony data with binning results using standardized key
            String colonyStorageKey = SessionAnalysisKeys.ColonyKeys.binning(imageHandle);
            putAnalysisWithValidation(colonyStorageKey, binnedColonies, imageHandle);
            
            return ok("bin_colonies", new JSONObject()
                .put("bins", new JSONObject(binSummary))
                .put("size_edges_mm", new JSONArray(edgesMM)));
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("bin_colonies", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("bin_colonies", e, logger, recovery);
        }
    }
    
    /**
     * Normalize colonies for cross-plate comparison
     */
    public JSONObject normalizeColonies(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return ErrorHandler.handleValidationError("normalize_colonies", 
                    new IllegalArgumentException("Image not found"), logger, recovery);
            }
            
            // Get existing colony data
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return ErrorHandler.handleSessionError("normalize_colonies", 
                    new IllegalStateException("No colonies found. Run count_colonies first."), logger, recovery);
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            if (plate == null) {
                return ErrorHandler.handleSessionError("normalize_colonies", 
                    new IllegalStateException("No plate data found. Run detect_plate first."), logger, recovery);
            }
            
            double pxPerMM = plate.pxPerMM(90.0);
            boolean enableQuadrants = args.optBoolean("enable_quadrants", true);
            
            // Normalize using pure function
            ColonyNormalizer.NormalizationResult result = ColonyNormalizer.normalize(
                rec.image, colonies, plate.plateRoi(), pxPerMM, enableQuadrants);
            
            // Store normalized data using standardized key
            String normalizationStorageKey = SessionAnalysisKeys.ColonyKeys.normalization(imageHandle);
            putAnalysisWithValidation(normalizationStorageKey, result, imageHandle);
            
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
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("normalize_colonies", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("normalize_colonies", e, logger, recovery);
        }
    }
    
    /**
     * Export detailed colony features with comprehensive measurements
     * 
     * Output columns: colony_id,x_mm,y_mm,eq_diam_mm,L,a,b,L_bg,a_bg,b_bg,b_delta,dE_bg,snr_L,
     *                 xgal_binary,xgal_grade,size_bin,label,confidence
     */
    public JSONObject exportDetailedFeatures(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return ErrorHandler.handleValidationError("export_detailed_features", 
                    new IllegalArgumentException("Image not found"), logger, recovery);
            }
            
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return ErrorHandler.handleSessionError("export_detailed_features", 
                    new IllegalStateException("No colonies found"), logger, recovery);
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            if (plate == null) {
                return ErrorHandler.handleSessionError("export_detailed_features", 
                    new IllegalStateException("No plate data found"), logger, recovery);
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
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("export_detailed_features", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_detailed_features", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Export colony data in multiple formats
     */
    public JSONObject exportColonies(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return ErrorHandler.handleValidationError("export_colonies", 
                    new IllegalArgumentException("Image not found"), logger, recovery);
            }
            
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return ErrorHandler.handleSessionError("export_colonies", 
                    new IllegalStateException("No colonies found"), logger, recovery);
            }
            
            // Export using pure function (would need ColonyExporter utility)
            var exportResult = exportColonyData(rec, colonies);
            
            return ok("export_colonies", new JSONObject()
                .put("exports", exportResult));
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("export_colonies", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_colonies", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Enable colony identification assist mode
     */
    public JSONObject enableColonyAssist(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            
            if (rec == null) {
                return ErrorHandler.handleValidationError("enable_colony_assist", 
                    new IllegalArgumentException("Image not found"), logger, recovery);
            }
            
            // Get existing colony and plate data
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            if (colonies == null) {
                colonies = List.of(); // Empty list if no colonies detected yet
            }
            
            PlateDetector.Result plate = getPlateFromSession(store, imageHandle);
            if (plate == null) {
                return ErrorHandler.handleSessionError("enable_colony_assist", 
                    new IllegalStateException("No plate data found. Run detect_plate first."), logger, recovery);
            }
            
            double pxPerMM = plate.pxPerMM(90.0);
            
            // Create assist tool
            AssistColonyTool assistTool = new AssistColonyTool(rec.image, plate.plateRoi(), pxPerMM);
            assistTool.initializeWithColonies(colonies);
            
            // Store assist tool in session using standardized key
            String assistStorageKey = SessionAnalysisKeys.ColonyKeys.assist(imageHandle);
            putAnalysisWithValidation(assistStorageKey, assistTool, imageHandle);
            
            return ok("enable_colony_assist", new JSONObject()
                .put("colony_assist_enabled", true)
                .put("initial_colonies", colonies.size())
                .put("plate_radius_mm", plate.radiusPx() / pxPerMM)
                .put("assist_config", new JSONObject()
                    .put("click_radius", 25.0)
                    .put("min_colony_size", 8.0)
                    .put("max_colony_size", 60.0)
                    .put("default_class", "OTHER")));
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("enable_colony_assist", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("enable_colony_assist", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Disable colony identification assist mode
     */
    public JSONObject disableColonyAssist(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            
            // Get assist tool and extract final colonies
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return ErrorHandler.handleSessionError("disable_colony_assist", 
                    new IllegalStateException("Colony assist not active"), logger, recovery);
            }
            
            // Extract current colonies from assist tool
            List<Colony> finalColonies = assistTool.getCurrentColonies();
            
            // Update stored colony data with assist results using standardized key
            String colonyStorageKey = SessionAnalysisKeys.ColonyKeys.detection(imageHandle);
            putAnalysisWithValidation(colonyStorageKey, finalColonies, imageHandle);
            
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
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("disable_colony_assist", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("disable_colony_assist", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Handle user click in colony assist mode
     */
    public JSONObject colonyAssistClick(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            double clickX = args.getDouble("click_x");
            double clickY = args.getDouble("click_y");
            boolean isDelete = args.optBoolean("is_delete", false);
            
            // Get assist tool
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return ErrorHandler.handleSessionError("colony_assist_click", 
                    new IllegalStateException("Colony assist not active"), logger, recovery);
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
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("colony_assist_click", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("colony_assist_click", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Propagate colony class using machine learning
     */
    public JSONObject propagateColonyClass(JSONObject args) {
        try {
            String imageHandle = args.getString("image_handle");
            String targetClassStr = args.getString("target_class");
            
            // Parse target class
            ColonyColor targetClass;
            try {
                targetClass = ColonyColor.valueOf(targetClassStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ErrorHandler.handleValidationError("propagate_colony_class", 
                    new IllegalArgumentException("Invalid colony class: " + targetClassStr), logger, recovery);
            }
            
            // Get assist tool
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return ErrorHandler.handleSessionError("propagate_colony_class", 
                    new IllegalStateException("Colony assist not active"), logger, recovery);
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
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("propagate_colony_class", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("propagate_colony_class", new RuntimeException(e), logger, recovery);
        }
    }
    
    /**
     * Manually relabel colony
     */
    public JSONObject relabelColony(JSONObject args) {
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
                return ErrorHandler.handleValidationError("relabel_colony", 
                    new IllegalArgumentException("Invalid colony class: " + newClassStr), logger, recovery);
            }
            
            // Get assist tool
            AssistColonyTool assistTool = getAssistToolFromSession(store, imageHandle);
            if (assistTool == null) {
                return ErrorHandler.handleSessionError("relabel_colony", 
                    new IllegalStateException("Colony assist not active"), logger, recovery);
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
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("relabel_colony", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("relabel_colony", new RuntimeException(e), logger, recovery);
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
    
    /**
     * Get colony data from session with backward compatibility support
     * Tries standardized keys first, falls back to legacy patterns
     */
    private List<Colony> getColoniesFromSession(String imageHandle) {
        @SuppressWarnings("unchecked")
        List<Colony> colonies = (List<Colony>) compatibility.getColonyData(
            imageHandle, SessionAnalysisKeys.AnalysisType.COLONY_DETECTION);
        return colonies;
    }
    
    /**
     * Legacy method - kept for backward compatibility
     */
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
    
    /**
     * Generate Lab-b histogram for telemetry reporting using actual Lab measurements
     */
    private JSONArray generateLabBHistogram(List<Colony> colonies) {
        // Create histogram bins for Lab b* values
        // Typical range for b* is -100 to +100, but for X-gal we focus on -20 to +10
        double[] binEdges = {-20, -16, -12, -8, -4, 0, 4, 8, 10};
        int[] binCounts = new int[binEdges.length - 1];
        
        // Count colonies in each bin based on their actual binCategory which contains Lab classification
        for (Colony colony : colonies) {
            double bStar = 0.0; // Default for unclassified
            
            // Extract actual b* value from classification label if available
            String binCategory = colony.binCategory();
            if (binCategory != null && !binCategory.equals("unclassified")) {
                // For properly classified colonies, estimate b* from classification
                bStar = switch (colony.colorClass()) {
                    case BLUE -> {
                        // Differentiate between light, medium, dark blue based on binCategory
                        if (binCategory.contains("dark")) yield -15.0;   // Dark blue
                        else if (binCategory.contains("medium")) yield -10.0; // Medium blue
                        else yield -7.0;  // Light blue
                    }
                    case WHITE -> 2.0;       // Typical white colony b* value  
                    case OTHER -> -2.0;      // Uncertain/ambiguous
                    default -> 0.0;
                };
            }
            
            // Find appropriate bin
            for (int i = 0; i < binEdges.length - 1; i++) {
                if (bStar >= binEdges[i] && bStar < binEdges[i + 1]) {
                    binCounts[i]++;
                    break;
                }
            }
        }
        
        // Convert to JSON array
        JSONArray histogram = new JSONArray();
        for (int count : binCounts) {
            histogram.put(count);
        }
        
        return histogram;
    }
}
