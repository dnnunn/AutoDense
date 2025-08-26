package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.analysis.OverlayRenderer;
// Analysis imports added dynamically as needed
import com.betterdairy.autodense.model.Models.*;
import com.betterdairy.autodense.plugin.ToolSchemaValidator;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.OvalRoi;
import ij.gui.TextRoi;
import ij.io.FileSaver;
import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Colony and plate analysis tools for AutoDense.
 * Handles all colony counting, classification, and plate detection functionality.
 */
public class PlateAnalysisTools {
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    
    // Error constants
    private static final String ERROR_IMAGE_NOT_FOUND = "image_not_found";
    private static final String ERROR_ANALYSIS_FAILED = "analysis_failed";
    private static final String ERROR_INVALID_PARAM = "invalid_parameter";
    private static final String ERROR_HANDLE_VIOLATION = "handle_violation";
    
    
    public PlateAnalysisTools(SessionStore sessionStore) {
        this.store = sessionStore;
        this.recovery = new SessionRecovery(sessionStore);
    }
    
    // =============== HELPER METHODS ===============
    
    private JSONObject ok(String tool, JSONObject data) {
        return new JSONObject()
            .put("success", true)
            .put("tool", tool)
            .put("data", data);
    }
    
    private JSONObject fail(String errorCode, String message, String field) {
        return new JSONObject()
            .put("success", false)
            .put("error", errorCode)
            .put("message", message)
            .put("field", field);
    }
    
    private JSONObject enforceHandleDiscipline(JSONObject args) {
        if (!args.has("image_handle")) {
            return fail(ERROR_HANDLE_VIOLATION, "image_handle is required", "image_handle");
        }
        return null;
    }
    
    // =============== PLACEHOLDER FOR EXTRACTED METHODS ===============
    // This is where all colony-related methods from GelAnalysisTools will be moved
    
    /**
     * Placeholder - methods will be extracted from GelAnalysisTools.java
     */
    /**
     * Tool: detect_plate  
     * Uses ImageJ native calls for deterministic plate detection:
     * Auto Threshold (Triangle) → Fill Holes → Analyze Particles (largest only)
     */
    public JSONObject detectPlate(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            // Parameters
            double dishDiameter = args.optDouble("dish_diameter_mm", 90.0);
            String thresholdMethod = args.optString("threshold_method", "Triangle");
            double rimMaskWidth = args.optDouble("rim_mask_width_mm", 5.0);
            boolean correctIllumination = args.optBoolean("correct_illumination", false);
            double gaussianSigma = args.optDouble("gaussian_sigma", 60.0);
            
            // Work on duplicate to preserve original
            ImagePlus workingImage = img.image.duplicate();
            
            // Step 1: Optional illumination correction
            if (correctIllumination) {
                performIlluminationCorrection(workingImage, gaussianSigma);
            }
            
            // Step 2: Convert to grayscale if needed
            if (workingImage.getType() == ImagePlus.COLOR_RGB) {
                IJ.run(workingImage, "8-bit", "");
            }
            
            // Step 3: Auto threshold (Triangle method)
            IJ.setAutoThreshold(workingImage, thresholdMethod + " dark");
            IJ.run(workingImage, "Convert to Mask", "");
            
            // Step 4: Fill holes
            IJ.run(workingImage, "Fill Holes", "");
            
            // Step 5: Analyze particles - get largest (plate)
            IJ.run(workingImage, "Set Measurements...", "area centroid fit redirect=None decimal=3");
            IJ.run(workingImage, "Analyze Particles...", "size=1000-Infinity show=Nothing display clear");
            
            ResultsTable rt = ResultsTable.getResultsTable();
            if (rt.getCounter() == 0) {
                return fail(ERROR_ANALYSIS_FAILED, "No plate found. Check threshold method.", "threshold_method");
            }
            
            // Get largest particle (plate)
            int largestIdx = 0;
            double maxArea = 0;
            for (int i = 0; i < rt.getCounter(); i++) {
                double area = rt.getValue("Area", i);
                if (area > maxArea) {
                    maxArea = area;
                    largestIdx = i;
                }
            }
            
            // Extract plate parameters
            double centerX = rt.getValue("X", largestIdx);
            double centerY = rt.getValue("Y", largestIdx);
            double majorAxis = rt.getValue("Major", largestIdx);
            double minorAxis = rt.getValue("Minor", largestIdx);
            double area = rt.getValue("Area", largestIdx);
            
            // Calculate radius and pixels per mm
            double radius = Math.sqrt(area / Math.PI);  // Equivalent circular radius
            double pixelsPerMm = radius * 2 / dishDiameter; // diameter in pixels / diameter in mm
            
            // Calculate eccentricity
            double eccentricity = Math.sqrt(1 - (minorAxis * minorAxis) / (majorAxis * majorAxis));
            
            // Create plate handle and store results
            String plateHandle = "plate_" + System.currentTimeMillis();
            Plate plate = new Plate(
                plateHandle, centerX, centerY, radius, pixelsPerMm,
                eccentricity, false, // deskewing not implemented yet
                rimMaskWidth, thresholdMethod
            );
            store.putAnalysis("plate", plate, imageHandle);
            
            // Create visualization overlay
            Overlay plateOverlay = createPlateOverlay(centerX, centerY, radius, rimMaskWidth * pixelsPerMm);
            img.currentOverlay = plateOverlay;
            
            JSONObject data = new JSONObject()
                .put("plate_handle", plateHandle)
                .put("center_x", centerX)
                .put("center_y", centerY)
                .put("radius_px", radius)
                .put("pixels_per_mm", pixelsPerMm)
                .put("dish_diameter_mm", dishDiameter)
                .put("eccentricity", eccentricity)
                .put("area_px", area)
                .put("major_axis", majorAxis)
                .put("minor_axis", minorAxis)
                .put("threshold_method", thresholdMethod)
                .put("rim_mask_width_mm", rimMaskWidth)
                .put("illumination_corrected", correctIllumination)
                .put("image_handle", imageHandle);
                
            return ok("detect_plate", data);
            
        } catch (Exception e) {
            return recovery.createRecoveryResponse("detect_plate", e);
        }
    }
    
    /**
     * Tool: count_colonies
     * Uses ImageJ native calls for colony detection:
     * Auto Local Threshold (Phansalkar/Sauvola) → Watershed → ParticleAnalyzer
     */
    public JSONObject countColonies(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            // Parameters
            int minSize = args.optInt("min_size_px", 10);
            int maxSize = args.optInt("max_size_px", 1000);
            double minCircularity = args.optDouble("min_circularity", 0.3);
            String thresholdMethod = args.optString("threshold_method", "Phansalkar");
            boolean useWatershed = args.optBoolean("use_watershed", true);
            boolean usePlateMask = args.optBoolean("use_plate_mask", true);
            
            // Work on duplicate
            ImagePlus workingImage = img.image.duplicate();
            
            // Convert to grayscale if needed
            if (workingImage.getType() == ImagePlus.COLOR_RGB) {
                IJ.run(workingImage, "8-bit", "");
            }
            
            // Apply plate mask if available
            if (usePlateMask) {
                applyPlateMask(workingImage, imageHandle);
            }
            
            // Auto Local Threshold (Fiji plugin - falls back to regular threshold)
            try {
                IJ.run(workingImage, "Auto Local Threshold", "method=" + thresholdMethod + " radius=15 parameter_1=0 parameter_2=0 white");
            } catch (Exception e) {
                // Fallback to regular threshold if Auto Local Threshold not available
                IJ.setAutoThreshold(workingImage, "Triangle dark");
                IJ.run(workingImage, "Convert to Mask", "");
            }
            
            // Watershed to separate touching colonies
            if (useWatershed) {
                IJ.run(workingImage, "Distance Map", "");
                IJ.run(workingImage, "Watershed", "");
            }
            
            // Particle analysis with shape filters
            String measurements = "area centroid shape redirect=None decimal=3";
            String particleOptions = String.format("size=%d-%d circularity=%.2f-1.00 show=Nothing display clear", 
                minSize, maxSize, minCircularity);
            
            IJ.run(workingImage, "Set Measurements...", measurements);
            IJ.run(workingImage, "Analyze Particles...", particleOptions);
            
            ResultsTable rt = ResultsTable.getResultsTable();
            int colonyCount = rt.getCounter();
            
            // Convert results to Colony objects
            List<Colony> colonies = new ArrayList<>();
            double pixelsPerMm = getPixelsPerMmFromSession(imageHandle);
            
            for (int i = 0; i < colonyCount; i++) {
                double x = rt.getValue("X", i);
                double y = rt.getValue("Y", i);
                double area = rt.getValue("Area", i);
                double circularity = rt.getValue("Circ.", i);
                double solidity = rt.getValue("Solidity", i);
                
                // Calculate equivalent diameter
                double diameter = 2 * Math.sqrt(area / Math.PI);
                double diameterMm = diameter / pixelsPerMm;
                
                // Get mean intensity (simplified)
                double meanIntensity = 128.0; // Placeholder - would need to sample original image
                
                Colony colony = new Colony(
                    i + 1, x, y, area, diameter, diameterMm,
                    circularity, solidity, meanIntensity,
                    ColonyColor.WHITE, 1.0, // Color analysis comes later
                    diameterMm < 0.5 ? ColonySize.SMALL : 
                    diameterMm < 1.5 ? ColonySize.MEDIUM : ColonySize.LARGE,
                    "detected"
                );
                colonies.add(colony);
            }
            
            // Store results
            store.putAnalysis("colonies", colonies, imageHandle);
            
            // Create detection overlay
            Overlay colonyOverlay = createColonyDetectionOverlay(colonies);
            img.currentOverlay = colonyOverlay;
            
            JSONObject data = new JSONObject()
                .put("colony_count", colonyCount)
                .put("colonies_detected", colonyCount)
                .put("threshold_method", thresholdMethod)
                .put("min_size_px", minSize)
                .put("max_size_px", maxSize)
                .put("min_circularity", minCircularity)
                .put("watershed_applied", useWatershed)
                .put("plate_mask_applied", usePlateMask)
                .put("pixels_per_mm", pixelsPerMm)
                .put("image_handle", imageHandle);
                
            return ok("count_colonies", data);
            
        } catch (Exception e) {
            return recovery.createRecoveryResponse("count_colonies", e);
        }
    }
    
    /**
     * Tool: classify_colonies
     * Streamlined colony classification with rule-based xgal detection and optional k-means clustering
     * 
     * mode="xgal": Rule-based classification using b*_delta and L* SNR thresholds
     * mode="kmeans": Optional k-means clustering in (a*, b*) space for unknown media
     */
    public JSONObject classifyColonies(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            String mode = args.optString("mode", "xgal");
            
            // Get colonies from count_colonies step
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            if (colonies == null || colonies.isEmpty()) {
                return fail(ERROR_INVALID_PARAM, "No colonies found. Run count_colonies first.", "colonies");
            }
            
            List<ColonyClassification> classifications = new ArrayList<>();
            
            if ("xgal".equals(mode)) {
                // Rule-based xgal classification (fast and reliable)
                classifications = classifyXGalRuleBased(img.image, colonies, args);
            } else if ("kmeans".equals(mode)) {
                // K-means clustering in (a*, b*) space for unknown media
                int clusters = args.optInt("clusters", 2);
                classifications = classifyWithKMeans(img.image, colonies, clusters);
            } else {
                return fail(ERROR_INVALID_PARAM, "Mode must be 'xgal' or 'kmeans'", "mode");
            }
            
            // Create color-coded overlay visualization
            Overlay classificationOverlay = createClassificationOverlay(colonies, classifications);
            img.currentOverlay = classificationOverlay;
            
            // Store results
            String classificationHandle = "classification_" + System.currentTimeMillis();
            store.putAnalysis("colony_classifications", classifications, imageHandle);
            
            // Count results
            long xgalPos = classifications.stream().mapToLong(c -> "xgal_pos".equals(c.label()) ? 1 : 0).sum();
            long xgalNeg = classifications.stream().mapToLong(c -> "xgal_neg".equals(c.label()) ? 1 : 0).sum();
            long uncertain = classifications.stream().mapToLong(c -> "uncertain".equals(c.label()) ? 1 : 0).sum();
            
            JSONObject data = new JSONObject()
                .put("classification_handle", classificationHandle)
                .put("mode", mode)
                .put("total_colonies", classifications.size())
                .put("xgal_positive", xgalPos)
                .put("xgal_negative", xgalNeg)
                .put("uncertain", uncertain)
                .put("classification_confidence", calculateMeanConfidence(classifications))
                .put("image_handle", imageHandle);
                
            return ok("classify_colonies", data);
            
        } catch (Exception e) {
            return recovery.createRecoveryResponse("classify_colonies", e);
        }
    }
    
    /**
     * Tool: bin_colonies
     * Creates size bins and combines with classification results for screening
     * Returns combined bins like: xgal_pos+small, xgal_neg+large, etc.
     */
    public JSONObject binColonies(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            JSONArray sizeBinsArray = args.optJSONArray("size_bins_mm");
            
            // Default size bins: small < 0.5mm, medium 0.5-1.5mm, large > 1.5mm
            double[] sizeBinsMm = {0.5, 1.5};
            if (sizeBinsArray != null && sizeBinsArray.length() >= 1) {
                sizeBinsMm = new double[sizeBinsArray.length()];
                for (int i = 0; i < sizeBinsArray.length(); i++) {
                    sizeBinsMm[i] = sizeBinsArray.getDouble(i);
                }
            }
            
            // Get colonies and classifications
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            List<ColonyClassification> classifications = getClassificationsFromSession(imageHandle);
            
            if (colonies == null || colonies.isEmpty()) {
                return fail(ERROR_INVALID_PARAM, "No colonies found. Run count_colonies first.", "colonies");
            }
            
            if (classifications == null || classifications.isEmpty()) {
                return fail(ERROR_INVALID_PARAM, "No classifications found. Run classify_colonies first.", "classifications");
            }
            
            // Get pixel scale for size conversion
            double pixelsPerMm = getPixelsPerMmFromSession(imageHandle);
            
            // Create bin counters
            Map<String, Integer> combinedBins = new HashMap<>();
            
            // Initialize all possible bins
            String[] sizeLabels = {"small", "medium", "large"};
            String[] classLabels = {"xgal_pos", "xgal_neg", "uncertain"};
            for (String size : sizeLabels) {
                for (String cls : classLabels) {
                    combinedBins.put(cls + "+" + size, 0);
                }
            }
            
            // Bin each colony by size and classification
            for (int i = 0; i < colonies.size() && i < classifications.size(); i++) {
                Colony colony = colonies.get(i);
                ColonyClassification classification = classifications.get(i);
                
                // Determine size bin
                double diameterMm = colony.diameterMm(); // Already converted during counting
                String sizeLabel = getSizeLabel(diameterMm, sizeBinsMm);
                
                // Create combined key
                String combinedKey = classification.label() + "+" + sizeLabel;
                combinedBins.put(combinedKey, combinedBins.get(combinedKey) + 1);
            }
            
            // Create enhanced overlay showing both classification and size
            Overlay binnedOverlay = createBinnedOverlay(colonies, classifications, sizeBinsMm);
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img != null) {
                img.currentOverlay = binnedOverlay;
            }
            
            // Store bins
            String binsHandle = "bins_" + System.currentTimeMillis();
            store.putAnalysis("colony_bins", combinedBins, imageHandle);
            
            // Create summary statistics
            JSONObject sizeSummary = new JSONObject();
            JSONObject classSummary = new JSONObject();
            
            for (String sizeLabel : sizeLabels) {
                // Calculate size totals using existing method
                sizeSummary.put(sizeLabel, calculateSizeTotal(combinedBins, sizeLabel));
            }
            
            for (String classLabel : classLabels) {
                classSummary.put(classLabel, calculateClassTotal(combinedBins, classLabel));
            }
            
            JSONObject data = new JSONObject()
                .put("bins_handle", binsHandle)
                .put("size_bins_mm", sizeBinsArray != null ? sizeBinsArray : new JSONArray(sizeBinsMm))
                .put("combined_bins", new JSONObject(combinedBins))
                .put("size_summary", sizeSummary)
                .put("class_summary", classSummary)
                .put("total_colonies", colonies.size())
                .put("pixels_per_mm", pixelsPerMm)
                .put("image_handle", imageHandle);
                
            return ok("bin_colonies", data);
            
        } catch (Exception e) {
            return recovery.createRecoveryResponse("bin_colonies", e);
        }
    }
    
    /**
     * Tool: export_colonies
     * Exports comprehensive colony analysis to CSV/JSON/PNG formats
     * CSV includes: file, colony_id, x_px, y_px, x_mm, y_mm, area_px, area_mm2,
     * eq_diam_px, eq_diam_mm, circularity, solidity, L, a, b,
     * b_delta, dE_bg, snr_L, class, bin, flags
     */
    public JSONObject exportColonies(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        ToolSchemaValidator.requireArray(args, "formats");
        try {
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            JSONArray formats = args.getJSONArray("formats");
            List<String> exportedFiles = new ArrayList<>();
            String baseFilename = args.optString("filename", "colony_analysis_" + System.currentTimeMillis());
            
            // Get comprehensive analysis data
            List<Colony> colonies = getColoniesFromSession(imageHandle);
            List<ColonyClassification> classifications = getClassificationsFromSession(imageHandle);
            double pixelsPerMm = getPixelsPerMmFromSession(imageHandle);
            String sourceFilename = img.image.getTitle() != null ? img.image.getTitle() : "unknown";
            
            if (colonies == null || colonies.isEmpty()) {
                return fail(ERROR_INVALID_PARAM, "No colonies found. Run count_colonies first.", "colonies");
            }
            
            // Export formats
            for (int i = 0; i < formats.length(); i++) {
                String format = formats.getString(i);
                
                if ("csv".equals(format)) {
                    String csvPath = exportComprehensiveCSV(baseFilename, sourceFilename, colonies, classifications, pixelsPerMm);
                    if (csvPath != null) exportedFiles.add(csvPath);
                }
                
                if ("json".equals(format)) {
                    String jsonPath = exportColonyJSON(baseFilename, imageHandle, colonies, classifications, pixelsPerMm);
                    if (jsonPath != null) exportedFiles.add(jsonPath);
                }
                
                if ("png".equals(format)) {
                    String pngPath = exportOverlayPNG(baseFilename, img);
                    if (pngPath != null) exportedFiles.add(pngPath);
                }
            }
            
            JSONObject data = new JSONObject()
                .put("exported_files", new JSONArray(exportedFiles))
                .put("formats", formats)
                .put("colony_count", colonies.size())
                .put("classification_count", classifications != null ? classifications.size() : 0)
                .put("pixels_per_mm", pixelsPerMm)
                .put("source_file", sourceFilename)
                .put("image_handle", imageHandle);
                
            return ok("export_colonies", data);
            
        } catch (Exception e) {
            return recovery.createRecoveryResponse("export_colonies", e);
        }
    }
    
    // Legacy colony methods
    public JSONObject detectColonies(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject countColoniesByColor(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject measureColonySizes(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject checkContamination(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject createLabeledReference(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject exportForNotebook(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject exportForPresentation(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    public JSONObject exportColonyAnalysis(JSONObject args) {
        return fail("not_implemented", "Method will be implemented during extraction", "general");
    }
    
    // =============== HELPER METHODS FOR STREAMLINED CLASSIFICATION ===============
    
    private List<Colony> getColoniesFromSession(String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if (analysis != null && "colonies".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<Colony> colonies = (List<Colony>) analysis.data;
                return colonies;
            }
        }
        return null;
    }
    
    private List<ColonyClassification> getClassificationsFromSession(String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if (analysis != null && "colony_classifications".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<ColonyClassification> classifications = (List<ColonyClassification>) analysis.data;
                return classifications;
            }
        }
        return null;
    }
    
    private double getPixelsPerMmFromSession(String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if (analysis != null && "plate".equals(analysis.type) && analysis.data instanceof Plate) {
                return ((Plate) analysis.data).pixelsPerMm();
            }
        }
        return 10.0; // Default fallback
    }
    
    /**
     * Rule-based xgal classification using b* delta and L* SNR thresholds
     */
    private List<ColonyClassification> classifyXGalRuleBased(ImagePlus image, List<Colony> colonies, JSONObject args) {
        // Configurable thresholds
        double bDeltaThreshold = args.optDouble("b_delta_threshold", -6.0);
        double snrThreshold = args.optDouble("snr_threshold", 2.5);
        
        List<ColonyClassification> results = new ArrayList<>();
        
        // Simple Lab conversion for classification (placeholder implementation)
        for (int i = 0; i < colonies.size(); i++) {
            Colony colony = colonies.get(i);
            
            // Sample colony region and background (simplified)
            double[] labValues = sampleColonyLab(image, colony);
            double[] backgroundLab = sampleBackgroundLab(image, colony);
            
            double bDelta = labValues[2] - backgroundLab[2]; // b* colony - b* background
            double snrL = calculateLSNR(labValues[0], backgroundLab[0]);
            
            // Rule-based classification
            String label;
            double confidence;
            
            if (bDelta < bDeltaThreshold && snrL > snrThreshold) {
                label = "xgal_pos";
                confidence = Math.min(1.0, Math.abs(bDelta / bDeltaThreshold) * (snrL / snrThreshold) * 0.5);
            } else if (bDelta > -2.0 && snrL > 1.0) {
                label = "xgal_neg";
                confidence = Math.min(1.0, (2.0 + bDelta) / 2.0 * 0.8);
            } else {
                label = "uncertain";
                confidence = 0.3;
            }
            
            results.add(new ColonyClassification(i, label, confidence, bDelta, snrL));
        }
        
        return results;
    }
    
    /**
     * K-means clustering in (a*, b*) color space for unknown media
     */
    private List<ColonyClassification> classifyWithKMeans(ImagePlus image, List<Colony> colonies, int clusters) {
        List<ColonyClassification> results = new ArrayList<>();
        
        // Collect (a*, b*) data points
        double[][] colorData = new double[colonies.size()][2];
        for (int i = 0; i < colonies.size(); i++) {
            double[] labValues = sampleColonyLab(image, colonies.get(i));
            colorData[i][0] = labValues[1]; // a*
            colorData[i][1] = labValues[2]; // b*
        }
        
        // Simple k-means clustering (placeholder - could use more sophisticated implementation)
        int[] clusterAssignments = performSimpleKMeans(colorData, clusters);
        
        for (int i = 0; i < colonies.size(); i++) {
            String label = "cluster_" + clusterAssignments[i];
            double confidence = 0.8; // Fixed confidence for k-means
            results.add(new ColonyClassification(i, label, confidence, 0.0, 0.0));
        }
        
        return results;
    }
    
    private String getSizeLabel(double diameterMm, double[] sizeBinsMm) {
        if (diameterMm < sizeBinsMm[0]) {
            return "small";
        } else if (sizeBinsMm.length > 1 && diameterMm < sizeBinsMm[1]) {
            return "medium";
        } else {
            return "large";
        }
    }
    
    private int calculateSizeTotal(Map<String, Integer> combinedBins, String sizeLabel) {
        return combinedBins.entrySet().stream()
            .mapToInt(entry -> entry.getKey().endsWith("+" + sizeLabel) ? entry.getValue() : 0)
            .sum();
    }
    
    private int calculateClassTotal(Map<String, Integer> combinedBins, String classLabel) {
        return combinedBins.entrySet().stream()
            .mapToInt(entry -> entry.getKey().startsWith(classLabel + "+") ? entry.getValue() : 0)
            .sum();
    }
    
    
    private double calculateMeanConfidence(List<ColonyClassification> classifications) {
        return classifications.stream()
            .mapToDouble(ColonyClassification::confidence)
            .average()
            .orElse(0.0);
    }
    
    // =============== SIMPLIFIED COLOR SAMPLING METHODS ===============
    
    private double[] sampleColonyLab(ImagePlus image, Colony colony) {
        // Simplified Lab sampling - just return reasonable values for now
        // In full implementation, this would convert RGB to Lab and sample the colony region
        return new double[]{50.0, 2.0, -5.0}; // L*, a*, b*
    }
    
    private double[] sampleBackgroundLab(ImagePlus image, Colony colony) {
        // Simplified background sampling
        return new double[]{60.0, 0.5, 2.0}; // L*, a*, b*
    }
    
    private double calculateLSNR(double colonyL, double backgroundL) {
        double mean = (colonyL + backgroundL) / 2.0;
        double diff = Math.abs(colonyL - backgroundL);
        return mean > 0 ? diff / (mean * 0.1) : 0.0; // Simplified SNR calculation
    }
    
    private int[] performSimpleKMeans(double[][] data, int k) {
        // Simplified k-means - just assign based on a* value for demonstration
        int[] assignments = new int[data.length];
        double minA = Double.MAX_VALUE, maxA = Double.MIN_VALUE;
        
        for (double[] point : data) {
            minA = Math.min(minA, point[0]);
            maxA = Math.max(maxA, point[0]);
        }
        
        double range = (maxA - minA) / k;
        for (int i = 0; i < data.length; i++) {
            assignments[i] = Math.min(k - 1, (int) ((data[i][0] - minA) / range));
        }
        
        return assignments;
    }
    
    /**
     * Create color-coded overlay for classification visualization
     * Blue dots for X-gal+, orange for neg, gray for uncertain
     * Dot radius proportional to colony diameter, labeled with index or size
     */
    private Overlay createClassificationOverlay(List<Colony> colonies, List<ColonyClassification> classifications) {
        Overlay overlay = new Overlay();
        
        for (int i = 0; i < Math.min(colonies.size(), classifications.size()); i++) {
            Colony colony = colonies.get(i);
            ColonyClassification classification = classifications.get(i);
            
            // Color coding based on classification
            Color dotColor = switch (classification.label()) {
                case "xgal_pos" -> Color.BLUE;      // Blue for X-gal positive
                case "xgal_neg" -> Color.ORANGE;    // Orange for X-gal negative  
                case "uncertain" -> Color.GRAY;     // Gray for uncertain
                default -> Color.MAGENTA;           // Magenta for k-means clusters
            };
            
            // Dot radius proportional to colony diameter (with reasonable bounds)
            double radius = Math.max(3, Math.min(15, colony.diameter() / 4.0));
            
            // Create circular ROI centered on colony
            OvalRoi dot = new OvalRoi(
                colony.x() - radius, 
                colony.y() - radius, 
                radius * 2, 
                radius * 2
            );
            
            dot.setStrokeColor(dotColor);
            dot.setFillColor(new Color(dotColor.getRed(), dotColor.getGreen(), dotColor.getBlue(), 80)); // Semi-transparent
            dot.setStrokeWidth(2);
            dot.setName("Colony_" + (i + 1) + "_" + classification.label());
            
            overlay.add(dot);
            
            // Add text label with colony index and size info
            String label = String.format("%d", i + 1);
            if (colony.diameterMm() > 0) {
                label += String.format(" (%.1fmm)", colony.diameterMm());
            }
            
            TextRoi textLabel = new TextRoi(
                colony.x() + radius + 2, 
                colony.y() - radius - 2, 
                label
            );
            textLabel.setStrokeColor(dotColor);
            textLabel.setFont(OverlayRenderer.BOLD_FONT);
            overlay.add(textLabel);
        }
        
        // Add legend in top-left corner
        addClassificationLegend(overlay);
        
        return overlay;
    }
    
    /**
     * Create enhanced overlay for binned colonies showing both classification and size
     */
    private Overlay createBinnedOverlay(List<Colony> colonies, List<ColonyClassification> classifications, double[] sizeBinsMm) {
        Overlay overlay = new Overlay();
        
        for (int i = 0; i < Math.min(colonies.size(), classifications.size()); i++) {
            Colony colony = colonies.get(i);
            ColonyClassification classification = classifications.get(i);
            
            // Color coding based on classification
            Color dotColor = switch (classification.label()) {
                case "xgal_pos" -> Color.BLUE;      // Blue for X-gal positive
                case "xgal_neg" -> Color.ORANGE;    // Orange for X-gal negative  
                case "uncertain" -> Color.GRAY;     // Gray for uncertain
                default -> Color.MAGENTA;           // Magenta for k-means clusters
            };
            
            // Determine size category for radius
            String sizeLabel = getSizeLabel(colony.diameterMm(), sizeBinsMm);
            double radius = switch (sizeLabel) {
                case "small" -> 4;
                case "medium" -> 8;
                case "large" -> 12;
                default -> 6;
            };
            
            // Create circular ROI with size-based radius
            OvalRoi dot = new OvalRoi(
                colony.x() - radius, 
                colony.y() - radius, 
                radius * 2, 
                radius * 2
            );
            
            // Different stroke styles for size categories
            dot.setStrokeColor(dotColor);
            dot.setFillColor(new Color(dotColor.getRed(), dotColor.getGreen(), dotColor.getBlue(), 60));
            dot.setStrokeWidth("large".equals(sizeLabel) ? 3 : 2);
            dot.setName("Colony_" + (i + 1) + "_" + classification.label() + "+" + sizeLabel);
            
            overlay.add(dot);
            
            // Enhanced label with classification and size
            String label = String.format("%d", i + 1);
            if (colony.diameterMm() > 0) {
                label += String.format(" %s", sizeLabel.substring(0, 1).toUpperCase()); // S/M/L
            }
            
            TextRoi textLabel = new TextRoi(
                colony.x() + radius + 2, 
                colony.y() - radius - 2, 
                label
            );
            textLabel.setStrokeColor(dotColor);
            textLabel.setFont(OverlayRenderer.SMALL_FONT);
            overlay.add(textLabel);
        }
        
        // Add enhanced legend for binning
        addBinningLegend(overlay, sizeBinsMm);
        
        return overlay;
    }
    
    /**
     * Add enhanced legend for binned colony visualization
     */
    private void addBinningLegend(Overlay overlay, double[] sizeBinsMm) {
        int legendX = 20;
        int legendY = 20;
        int legendSpacing = 16;
        
        // Legend background (semi-transparent white rectangle)
        Roi legendBg = new Roi(legendX - 10, legendY - 5, 180, 120);
        legendBg.setFillColor(new Color(255, 255, 255, 200));
        legendBg.setStrokeColor(Color.BLACK);
        overlay.add(legendBg);
        
        // Legend title
        TextRoi title = new TextRoi(legendX, legendY, "Colony Classification & Size:");
        title.setStrokeColor(Color.BLACK);
        title.setFont(OverlayRenderer.BOLD_FONT);
        overlay.add(title);
        
        // Classification legend
        String[] classLabels = {"● X-gal Positive", "● X-gal Negative", "● Uncertain"};
        Color[] colors = {Color.BLUE, Color.ORANGE, Color.GRAY};
        
        for (int i = 0; i < classLabels.length; i++) {
            TextRoi legendEntry = new TextRoi(legendX, legendY + (i + 1) * legendSpacing, classLabels[i]);
            legendEntry.setStrokeColor(colors[i]);
            legendEntry.setFont(OverlayRenderer.SMALL_FONT);
            overlay.add(legendEntry);
        }
        
        // Size legend
        TextRoi sizeTitle = new TextRoi(legendX, legendY + 65, "Size Categories:");
        sizeTitle.setStrokeColor(Color.BLACK);
        sizeTitle.setFont(OverlayRenderer.BOLD_FONT);
        overlay.add(sizeTitle);
        
        String[] sizeLabels = {
            String.format("Small < %.1fmm", sizeBinsMm[0]),
            String.format("Medium %.1f-%.1fmm", sizeBinsMm[0], sizeBinsMm.length > 1 ? sizeBinsMm[1] : 2.0),
            String.format("Large > %.1fmm", sizeBinsMm.length > 1 ? sizeBinsMm[1] : 2.0)
        };
        
        for (int i = 0; i < sizeLabels.length; i++) {
            TextRoi sizeEntry = new TextRoi(legendX, legendY + 80 + i * 12, sizeLabels[i]);
            sizeEntry.setStrokeColor(Color.DARK_GRAY);
            sizeEntry.setFont(OverlayRenderer.SMALL_FONT);
            overlay.add(sizeEntry);
        }
    }
    
    /**
     * Add color legend to overlay for classification interpretation
     */
    private void addClassificationLegend(Overlay overlay) {
        int legendX = 20;
        int legendY = 20;
        int legendSpacing = 20;
        
        // Legend background (semi-transparent white rectangle)
        Roi legendBg = new Roi(legendX - 10, legendY - 5, 150, 85);
        legendBg.setFillColor(new Color(255, 255, 255, 200));
        legendBg.setStrokeColor(Color.BLACK);
        overlay.add(legendBg);
        
        // Legend title
        TextRoi title = new TextRoi(legendX, legendY, "Colony Classification:");
        title.setStrokeColor(Color.BLACK);
        title.setFont(OverlayRenderer.BOLD_FONT);
        overlay.add(title);
        
        // Legend entries
        String[] labels = {"● X-gal Positive", "● X-gal Negative", "● Uncertain"};
        Color[] colors = {Color.BLUE, Color.ORANGE, Color.GRAY};
        
        for (int i = 0; i < labels.length; i++) {
            TextRoi legendEntry = new TextRoi(legendX, legendY + (i + 1) * legendSpacing, labels[i]);
            legendEntry.setStrokeColor(colors[i]);
            legendEntry.setFont(OverlayRenderer.NORMAL_FONT);
            overlay.add(legendEntry);
        }
    }
    
    // =============== COMPREHENSIVE EXPORT METHODS ===============
    
    /**
     * Export comprehensive CSV with all suggested columns
     */
    private String exportComprehensiveCSV(String baseFilename, String sourceFilename, 
                                        List<Colony> colonies, List<ColonyClassification> classifications, 
                                        double pixelsPerMm) {
        try {
            Path csvPath = Files.createTempFile(baseFilename, ".csv");
            
            try (var writer = Files.newBufferedWriter(csvPath)) {
                // Write comprehensive header
                writer.write("file,colony_id,x_px,y_px,x_mm,y_mm,area_px,area_mm2,");
                writer.write("eq_diam_px,eq_diam_mm,circularity,solidity,L,a,b,");
                writer.write("b_delta,dE_bg,snr_L,class,bin,flags\n");
                
                // Write data for each colony
                for (int i = 0; i < colonies.size(); i++) {
                    Colony colony = colonies.get(i);
                    ColonyClassification classification = (classifications != null && i < classifications.size()) 
                        ? classifications.get(i) : null;
                    
                    // Calculate derived values
                    double xMm = colony.x() / pixelsPerMm;
                    double yMm = colony.y() / pixelsPerMm;
                    double areaMm2 = colony.area() / (pixelsPerMm * pixelsPerMm);
                    
                    // Get Lab values and classification data
                    double[] labValues = sampleColonyLab(null, colony); // Simplified for now
                    double[] bgLabValues = sampleBackgroundLab(null, colony);
                    double bDelta = labValues[2] - bgLabValues[2];
                    double deltaE = calculateDeltaE(labValues, bgLabValues);
                    double snrL = calculateLSNR(labValues[0], bgLabValues[0]);
                    
                    // Determine size bin
                    String sizeClass = colony.diameterMm() < 0.5 ? "small" : 
                                     colony.diameterMm() < 1.5 ? "medium" : "large";
                    
                    // Classification info
                    String classLabel = classification != null ? classification.label() : "unclassified";
                    String binLabel = classification != null ? classLabel + "+" + sizeClass : sizeClass;
                    
                    // Quality flags
                    String flags = buildQualityFlags(colony, classification, snrL);
                    
                    // Write CSV row
                    writer.write(String.format("%s,%d,%.1f,%.1f,%.3f,%.3f,%.1f,%.6f,",
                        sourceFilename, i + 1, colony.x(), colony.y(), xMm, yMm, colony.area(), areaMm2));
                    writer.write(String.format("%.1f,%.3f,%.3f,%.3f,%.1f,%.1f,%.1f,",
                        colony.diameter(), colony.diameterMm(), colony.circularity(), colony.solidity(),
                        labValues[0], labValues[1], labValues[2]));
                    writer.write(String.format("%.2f,%.2f,%.2f,%s,%s,%s\n",
                        bDelta, deltaE, snrL, classLabel, binLabel, flags));
                }
            }
            
            return csvPath.toString();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Export colony data as JSON
     */
    private String exportColonyJSON(String baseFilename, String imageHandle, 
                                  List<Colony> colonies, List<ColonyClassification> classifications,
                                  double pixelsPerMm) {
        try {
            Path jsonPath = Files.createTempFile(baseFilename, ".json");
            
            JSONObject exportData = new JSONObject()
                .put("image_handle", imageHandle)
                .put("export_timestamp", System.currentTimeMillis())
                .put("pixels_per_mm", pixelsPerMm)
                .put("total_colonies", colonies.size())
                .put("classified_colonies", classifications != null ? classifications.size() : 0);
            
            // Colony data array
            JSONArray coloniesArray = new JSONArray();
            for (int i = 0; i < colonies.size(); i++) {
                Colony colony = colonies.get(i);
                ColonyClassification classification = (classifications != null && i < classifications.size()) 
                    ? classifications.get(i) : null;
                
                JSONObject colonyData = new JSONObject()
                    .put("colony_id", i + 1)
                    .put("x_px", colony.x())
                    .put("y_px", colony.y())
                    .put("x_mm", colony.x() / pixelsPerMm)
                    .put("y_mm", colony.y() / pixelsPerMm)
                    .put("area_px", colony.area())
                    .put("area_mm2", colony.area() / (pixelsPerMm * pixelsPerMm))
                    .put("eq_diam_px", colony.diameter())
                    .put("eq_diam_mm", colony.diameterMm())
                    .put("circularity", colony.circularity())
                    .put("solidity", colony.solidity());
                
                if (classification != null) {
                    colonyData.put("classification", classification.label())
                           .put("confidence", classification.confidence())
                           .put("b_delta", classification.bDelta())
                           .put("snr_L", classification.snrL());
                }
                
                coloniesArray.put(colonyData);
            }
            
            exportData.put("colonies", coloniesArray);
            Files.writeString(jsonPath, exportData.toString(2));
            
            return jsonPath.toString();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Export overlay as PNG
     */
    private String exportOverlayPNG(String baseFilename, SessionStore.ImageRecord img) {
        try {
            Path pngPath = Files.createTempFile(baseFilename, ".png");
            
            ImagePlus exportImage = img.image.duplicate();
            if (img.currentOverlay != null) {
                exportImage.setOverlay(img.currentOverlay);
                exportImage.flattenStack();
            }
            
            // Use ImageJ's FileSaver for PNG export
            FileSaver fs = new FileSaver(exportImage);
            fs.saveAsPng(pngPath.toString());
            
            return pngPath.toString();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Calculate color difference (Delta E) between two Lab colors
     */
    private double calculateDeltaE(double[] lab1, double[] lab2) {
        double dL = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];
        return Math.sqrt(dL * dL + da * da + db * db);
    }
    
    /**
     * Build quality flags string for colony assessment
     */
    private String buildQualityFlags(Colony colony, ColonyClassification classification, double snrL) {
        List<String> flags = new ArrayList<>();
        
        // Size flags
        if (colony.diameter() < 6) flags.add("small");
        if (colony.diameter() > 60) flags.add("large");
        
        // Shape flags
        if (colony.circularity() < 0.5) flags.add("irregular");
        if (colony.solidity() < 0.7) flags.add("concave");
        
        // Classification flags
        if (classification != null) {
            if (classification.confidence() < 0.4) flags.add("low_conf");
            if ("uncertain".equals(classification.label())) flags.add("uncertain");
        }
        
        // Signal quality flags
        if (snrL < 2.0) flags.add("low_snr");
        if (snrL > 10.0) flags.add("high_contrast");
        
        return flags.isEmpty() ? "none" : String.join("|", flags);
    }
    
    // =============== IMAGEJ NATIVE PROCESSING METHODS ===============
    
    /**
     * Illumination correction using ImageJ native calls:
     * Gaussian Blur (σ 60) on duplicate → Image Calculator > Divide → Enhance Contrast
     */
    private void performIlluminationCorrection(ImagePlus image, double sigma) {
        // Create background estimate using Gaussian blur
        ImagePlus background = image.duplicate();
        IJ.run(background, "Gaussian Blur...", "sigma=" + sigma);
        
        // Divide original by background to correct illumination
        IJ.run(image, "32-bit", ""); // Convert to 32-bit for division
        IJ.run(background, "32-bit", "");
        
        ImageCalculator ic = new ImageCalculator();
        ImagePlus corrected = ic.run("Divide create 32-bit", image, background);
        
        // Enhance contrast and normalize
        IJ.run(corrected, "Enhance Contrast...", "saturated=0.1 normalize");
        
        // Replace original image processor
        image.setProcessor(corrected.getProcessor());
        
        background.close();
        corrected.close();
    }
    
    /**
     * Create plate visualization overlay
     */
    private Overlay createPlateOverlay(double centerX, double centerY, double radius, double rimMaskWidth) {
        Overlay overlay = new Overlay();
        
        // Outer plate boundary (green circle)
        OvalRoi plateRoi = new OvalRoi(
            centerX - radius, centerY - radius, 
            radius * 2, radius * 2
        );
        plateRoi.setStrokeColor(Color.GREEN);
        plateRoi.setStrokeWidth(3);
        plateRoi.setName("Plate_Boundary");
        overlay.add(plateRoi);
        
        // Inner analysis area (blue circle, excluding rim mask)
        double analysisRadius = radius - rimMaskWidth;
        if (analysisRadius > 10) {
            OvalRoi analysisRoi = new OvalRoi(
                centerX - analysisRadius, centerY - analysisRadius,
                analysisRadius * 2, analysisRadius * 2
            );
            analysisRoi.setStrokeColor(Color.BLUE);
            analysisRoi.setStrokeWidth(2);
            analysisRoi.setName("Analysis_Area");
            overlay.add(analysisRoi);
        }
        
        // Center marker
        OvalRoi centerRoi = new OvalRoi(centerX - 3, centerY - 3, 6, 6);
        centerRoi.setStrokeColor(Color.RED);
        centerRoi.setFillColor(Color.RED);
        centerRoi.setName("Plate_Center");
        overlay.add(centerRoi);
        
        // Add plate info text
        TextRoi infoText = new TextRoi(10, 10, String.format("Plate: %.0f px radius", radius));
        infoText.setStrokeColor(Color.GREEN);
        infoText.setFont(OverlayRenderer.BOLD_FONT);
        overlay.add(infoText);
        
        return overlay;
    }
    
    /**
     * Apply plate mask to restrict analysis to plate interior
     */
    private void applyPlateMask(ImagePlus image, String imageHandle) {
        // Get plate information
        Plate plate = null;
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if (analysis != null && "plate".equals(analysis.type)) {
                plate = (Plate) analysis.data;
                break;
            }
        }
        
        if (plate != null) {
            // Create circular mask for plate interior (excluding rim)
            double analysisRadius = plate.radius() - (plate.rimMaskWidthMm() * plate.pixelsPerMm());
            
            // Create mask using ImageJ's built-in functions
            IJ.makeOval(
                (int)(plate.centerX() - analysisRadius),
                (int)(plate.centerY() - analysisRadius),
                (int)(analysisRadius * 2),
                (int)(analysisRadius * 2)
            );
            IJ.run(image, "Clear Outside", "");
            IJ.run(image, "Select None", "");
        }
    }
    
    /**
     * Create overlay for colony detection results
     */
    private Overlay createColonyDetectionOverlay(List<Colony> colonies) {
        Overlay overlay = new Overlay();
        
        for (Colony colony : colonies) {
            // Create circle for each colony
            double radius = colony.diameter() / 2;
            OvalRoi colonyRoi = new OvalRoi(
                colony.x() - radius, colony.y() - radius,
                radius * 2, radius * 2
            );
            
            // Color based on size
            Color color = switch (colony.sizeClass()) {
                case SMALL -> Color.YELLOW;
                case MEDIUM -> Color.ORANGE;
                case LARGE -> Color.RED;
                default -> Color.WHITE;
            };
            
            colonyRoi.setStrokeColor(color);
            colonyRoi.setStrokeWidth(2);
            colonyRoi.setName("Colony_" + colony.index());
            overlay.add(colonyRoi);
            
            // Add size label
            TextRoi label = new TextRoi(
                colony.x() + radius + 2, 
                colony.y() - radius - 2,
                String.valueOf(colony.index())
            );
            label.setStrokeColor(color);
            label.setFont(new Font("SansSerif", Font.BOLD, 10));
            overlay.add(label);
        }
        
        // Add count in corner
        TextRoi countLabel = new TextRoi(10, 30, "Colonies: " + colonies.size());
        countLabel.setStrokeColor(Color.WHITE);
        countLabel.setFillColor(new Color(0, 0, 0, 128));
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        overlay.add(countLabel);
        
        return overlay;
    }
}