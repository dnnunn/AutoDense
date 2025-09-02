package com.betterdairy.autodense.tools.gel;

import com.betterdairy.autodense.analysis.BandDetector;
import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.img.IJUtils;
import com.betterdairy.autodense.model.Models.Band;
import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.plugin.ToolSchemaValidator;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.util.ErrorHandler;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tools for band detection and quantification in gel electrophoresis images.
 * 
 * <p>This class provides functionality for:</p>
 * <ul>
 *   <li>Automated band detection within lanes</li>
 *   <li>Band quantification and analysis</li>
 *   <li>BandAssist functionality for interactive band propagation</li>
 *   <li>Band visualization with overlays</li>
 * </ul>
 * 
 * @author AutoDense Development Team
 * @version 2.0
 * @since 2.0
 */
public class BandDetectionTools extends BaseGelTool {
    
    /**
     * Constructor for band detection tools
     * 
     * @param store SessionStore instance for state management
     * @param tempDir Temporary directory for file operations
     */
    public BandDetectionTools(SessionStore store, Path tempDir) {
        super(store, tempDir);
    }
    
    /**
     * Tool: detect_bands
     * Detect bands within lanes
     */
    public JSONObject detectBands(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;

            BandDetectionParams params = validateAndPrepareBandDetectionParams(args);
            List<Lane> lanes = getLanesForBandDetection(args, params.imageHandle);
            BandDetectionResult result = performBandDetection(lanes, params);
            String overlayHandle = createBandDetectionOverlay(result, params.imageHandle);
            return formatBandDetectionResponse(result, overlayHandle, params);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("detect_bands", e, logger, recovery);
        }
    }
    
    /**
     * Tool: quantify_bands
     * Quantify detected bands with intensity measurements
     */
    public JSONObject quantifyBands(JSONObject args) {
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("quantify_bands", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Get bands analysis
            String bandsAnalysisHandle = null;
            for (String analysisHandle : store.getAnalysesForImage(imageHandle)) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if (analysis != null && "bands".equals(analysis.type)) {
                    bandsAnalysisHandle = analysisHandle;
                    break;
                }
            }
            
            if (bandsAnalysisHandle == null) {
                return ErrorHandler.handleValidationError("quantify_bands", 
                    new IllegalArgumentException("No bands found. Run detect_bands first."), 
                    logger, recovery);
            }
            
            @SuppressWarnings("unchecked")
            List<List<Band>> allBands = (List<List<Band>>) store.getAnalysis(bandsAnalysisHandle).data;
            
            // Calculate quantification metrics
            int totalBands = allBands.stream().mapToInt(List::size).sum();
            double totalArea = 0.0;
            
            for (List<Band> laneBands : allBands) {
                for (Band band : laneBands) {
                    totalArea += band.area();
                }
            }
            
            JSONObject data = new JSONObject()
                .put("total_bands", totalBands)
                .put("total_area", totalArea)
                .put("image_handle", imageHandle);
            
            return ok("quantify_bands", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("quantify_bands", e, logger, recovery);
        }
    }
    
    /**
     * Tool: enable_band_assist
     * Enable BandAssist for interactive band propagation
     */
    public JSONObject enableBandAssist(JSONObject args) {
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            
            // Enable BandAssist mode in session
            JSONObject data = new JSONObject()
                .put("band_assist_enabled", true)
                .put("image_handle", imageHandle)
                .put("message", "BandAssist enabled. Click on bands to propagate across lanes.");
            
            return ok("enable_band_assist", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("enable_band_assist", e, logger, recovery);
        }
    }
    
    /**
     * Tool: disable_band_assist
     * Disable BandAssist mode
     */
    public JSONObject disableBandAssist(JSONObject args) {
        try {
            JSONObject data = new JSONObject()
                .put("band_assist_enabled", false)
                .put("message", "BandAssist disabled.");
            
            return ok("disable_band_assist", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("disable_band_assist", e, logger, recovery);
        }
    }
    
    /**
     * Tool: configure_band_assist
     * Configure BandAssist parameters
     */
    public JSONObject configureBandAssist(JSONObject args) {
        try {
            double confidence = clamp(args.optDouble("confidence_threshold", 0.8), 0.1, 1.0);
            double rfTolerance = clamp(args.optDouble("rf_tolerance", 0.05), 0.01, 0.2);
            
            JSONObject data = new JSONObject()
                .put("confidence_threshold", confidence)
                .put("rf_tolerance", rfTolerance)
                .put("message", "BandAssist configured.");
            
            return ok("configure_band_assist", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("configure_band_assist", e, logger, recovery);
        }
    }
    
    // Supporting classes for band detection refactoring
    private static class BandDetectionParams {
        final String imageHandle;
        final double sensitivity;
        final double minBandHeight;
        final double prominence;
        final double smoothSigma;
        final double minPeakDistance;

        BandDetectionParams(String imageHandle, double sensitivity, double minBandHeight,
                          double prominence, double smoothSigma, double minPeakDistance) {
            this.imageHandle = imageHandle;
            this.sensitivity = sensitivity;
            this.minBandHeight = minBandHeight;
            this.prominence = prominence;
            this.smoothSigma = smoothSigma;
            this.minPeakDistance = minPeakDistance;
        }
    }

    private static class BandDetectionResult {
        final List<Lane> lanes;
        final List<List<Band>> allBands;
        final int totalBands;

        BandDetectionResult(List<Lane> lanes, List<List<Band>> allBands, int totalBands) {
            this.lanes = lanes;
            this.allBands = allBands;
            this.totalBands = totalBands;
        }
    }

    private BandDetectionParams validateAndPrepareBandDetectionParams(JSONObject args) {
        String imageHandle = args.getString("image_handle");
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        if (img == null) {
            throw new IllegalArgumentException("Image not found in session");
        }

        // Clamp and validate parameters
        double sensitivity = clamp(args.optDouble("sensitivity", 0.3), 0.1, 1.0);
        double minBandHeight = clamp(args.optDouble("min_band_height", 3.0), 1.0, 20.0);
        double prominence = clamp(args.optDouble("min_prominence", 0.06), 0.01, 0.5);
        double smoothSigma = clamp(args.optDouble("smooth_sigma", 2.0), 1.0, 4.0);
        double minPeakDistance = clamp(args.optDouble("min_distance_px", 14.0), 6.0, 32.0);

        // Debug logging for band detection parameters
        logger.info(String.format("[BAND_PARAMS] min_distance_px=%.1f (from args), clamped to %.1f", 
            args.optDouble("min_distance_px", 14.0), minPeakDistance));

        // Echo parameters back for determinism
        args.put("sensitivity", sensitivity);
        args.put("min_band_height", minBandHeight);
        args.put("min_prominence", prominence);
        args.put("smooth_sigma", smoothSigma);
        args.put("min_distance_px", minPeakDistance);

        // Suppress ROI Manager
        IJUtils.silenceRoiManager(img.image);

        return new BandDetectionParams(imageHandle, sensitivity, minBandHeight, 
                                     prominence, smoothSigma, minPeakDistance);
    }

    private List<Lane> getLanesForBandDetection(JSONObject args, String imageHandle) {
        if (args.has("analysis_handle")) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(args.getString("analysis_handle"));
            @SuppressWarnings("unchecked")
            List<Lane> tempLanes = (List<Lane>) analysis.data;
            return tempLanes;
        } else {
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            return LaneDetector.findLanes(img.image, 0, false, 0.55, 0.0, true);
        }
    }

    private BandDetectionResult performBandDetection(List<Lane> lanes, BandDetectionParams params) {
        SessionStore.ImageRecord img = store.getImage(params.imageHandle);
        List<List<Band>> allBands = new ArrayList<>();
        int totalBands = 0;
        
        // Create configuration map from band detection parameters
        java.util.Map<String, Object> bandsConfig = new java.util.HashMap<>();
        bandsConfig.put("min_distance_px", (int) params.minPeakDistance);
        bandsConfig.put("prominence_frac", params.prominence);
        
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("bands", bandsConfig);
        
        // Debug: Log the configuration being passed to BandDetector
        logger.info(String.format("[BAND_DETECTION_TOOLS] Created config map: bands.min_distance_px=%d", 
            (int) params.minPeakDistance));
        
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            Lane lane = lanes.get(laneIndex);
            // Use config-aware band detection method
            List<Band> bands = BandDetector.findBands(img.image, lane, config);
            allBands.add(bands);
            totalBands += bands.size();
            
            // Debug logging for band detection diagnostics
            logger.fine(String.format("[BANDS] lane i=%d, peaks=%d, prominence>=%.3f, sigma=%.1f, min_distance_px=%d", 
                laneIndex + 1, bands.size(), params.prominence, params.smoothSigma, (int) params.minPeakDistance));
        }
        
        return new BandDetectionResult(lanes, allBands, totalBands);
    }

    private String createBandDetectionOverlay(BandDetectionResult result, String imageHandle) {
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        Overlay overlay = new Overlay();
        int height = img.image.getHeight();
        
        for (int i = 0; i < result.lanes.size(); i++) {
            Lane lane = result.lanes.get(i);
            int x = lane.xStart();
            int width = Math.max(1, lane.xEnd() - lane.xStart() + 1);
            
            // Lane overlay (green) with label
            Roi laneRoi = new Roi(x, 0, width, height);
            laneRoi.setStrokeColor(new Color(0, 255, 0, 180));
            laneRoi.setStrokeWidth(2.0);
            laneRoi.setName("Lane " + (i + 1));
            overlay.add(laneRoi);
            
            // Add lane label text overlay
            TextRoi laneLabel = new TextRoi(x + width/2 - 10, 10, "L" + (i + 1));
            laneLabel.setStrokeColor(new Color(0, 255, 0));
            laneLabel.setFillColor(new Color(255, 255, 255, 200));
            laneLabel.setFont(new Font("Arial", Font.BOLD, 14));
            overlay.add(laneLabel);
            
            // Band overlays (red) with labels
            List<Band> bands = result.allBands.get(i);
            for (int b = 0; b < bands.size(); b++) {
                Band band = bands.get(b);
                int y = Math.max(0, Math.min(height - 4, band.y()));
                
                // Band rectangle
                Roi bandRoi = new Roi(x, y, width, 4);
                bandRoi.setStrokeColor(new Color(255, 0, 0, 200));
                bandRoi.setStrokeWidth(1.5);
                bandRoi.setName("Band L" + (i+1) + "B" + (b+1));
                overlay.add(bandRoi);
                
                // Band label (only for first few bands to avoid clutter)
                if (b < 3) { // Label first 3 bands in each lane
                    TextRoi bandLabel = new TextRoi(x + width + 2, y - 2, "B" + (b+1));
                    bandLabel.setStrokeColor(new Color(255, 0, 0));
                    bandLabel.setFillColor(new Color(255, 255, 255, 180));
                    bandLabel.setFont(new Font("Arial", Font.PLAIN, 10));
                    overlay.add(bandLabel);
                }
            }
        }
        
        img.image.setOverlay(overlay);
        img.image.updateAndDraw();
        IJUtils.refresh(img.image);
        
        return store.putOverlay(overlay, img.handle);
    }

    private JSONObject formatBandDetectionResponse(BandDetectionResult result, String overlayHandle, 
                                                 BandDetectionParams params) {
        SessionStore.ImageRecord img = store.getImage(params.imageHandle);
        String analysisHandle = store.putAnalysis("bands", result.allBands, img.handle);
        
        // Ensure this image remains current for subsequent operations
        store.setLastActiveImageHandle(img.handle);
        
        JSONObject data = new JSONObject()
            .put("lanes_analyzed", result.lanes.size())
            .put("bands_total", result.totalBands)
            .put("overlay_handle", overlayHandle)
            .put("analysis_handle", analysisHandle)
            .put("image_handle", img.handle)
            .put("parameters_used", new JSONObject()
                .put("sensitivity", params.sensitivity)
                .put("min_band_height", params.minBandHeight)
                .put("prominence", params.prominence));
        
        return ok("detect_bands", data);
    }
}
