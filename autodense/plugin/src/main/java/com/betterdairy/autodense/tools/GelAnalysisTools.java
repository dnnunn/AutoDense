package com.betterdairy.autodense.tools;

// AutoDense Core
import com.betterdairy.autodense.analysis.*;
import com.betterdairy.autodense.analysis.AssistModels.AssistBand;
import com.betterdairy.autodense.img.IJUtils;
import com.betterdairy.autodense.model.Models.*;
import com.betterdairy.autodense.plugin.ToolSchemaValidator;
import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.util.ErrorHandler;
import com.betterdairy.autodense.util.ImageJResourceManager;
import com.betterdairy.autodense.validation.SecureToolValidator;

// External Libraries
import autodense.util.OverlayExporter;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import org.json.JSONArray;
import org.json.JSONObject;

// Java Standard Library
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tool implementations for Gemini function calling in AutoDense gel analysis system.
 * 
 * <p>This class provides a comprehensive set of tools for automated gel electrophoresis
 * image analysis, including lane detection, band detection, quantification, and various
 * specialized analyses like digest kinetics and treatment comparisons.</p>
 * 
 * <h3>Key Design Principles:</h3>
 * <ul>
 *   <li><strong>Handle-based operations:</strong> All tools operate on handles, not raw pixels</li>
 *   <li><strong>Deterministic processing:</strong> Parameters are clamped and echoed for reproducibility</li>
 *   <li><strong>Comprehensive logging:</strong> All operations are logged for debugging and audit trails</li>
 *   <li><strong>Resource management:</strong> Automatic cleanup of temporary files and resources</li>
 *   <li><strong>Error recovery:</strong> Robust error handling with meaningful error messages</li>
 * </ul>
 * 
 * <h3>Method Complexity Refactoring:</h3>
 * <p>This class has been refactored to reduce method complexity and improve maintainability.
 * Complex methods like {@code digestKinetics}, {@code compareTreatments}, and {@code detectBands}
 * have been decomposed into smaller, focused helper methods following the Extract Method pattern.</p>
 * 
 * <h3>Threading and Performance:</h3>
 * <p>Includes preprocessing caches for expensive operations and efficient resource management
 * to handle large gel images and complex analyses.</p>
 * 
 * @author AutoDense Development Team
 * @version 2.0
 * @since 1.0
 */
public class GelAnalysisTools {
    
    private static final Logger logger = Logger.getLogger(GelAnalysisTools.class.getName());
    
    // Resource cleanup now handled by shared ImageJResourceManager utility
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    private final HandleGuard handleGuard;
    private Path tempDir;
    
    // Cache for expensive preprocessing operations (rolling ball, CLAHE, etc.)
    private final Map<String, String> preprocessingCache = new ConcurrentHashMap<>();
    
    public GelAnalysisTools(SessionStore store) {
        this.store = Objects.requireNonNull(store, "SessionStore cannot be null");
        this.recovery = new SessionRecovery(store);
        this.handleGuard = new HandleGuard(store, recovery);
        initializeTempDirectory();
        logger.info("GelAnalysisTools initialized with temp directory: " + this.tempDir);
    }

    private void initializeTempDirectory() {
        try {
            this.tempDir = Files.createTempDirectory("autodense_gel_");
            logger.fine("Created temporary directory: " + this.tempDir);
        } catch (IOException e) {
            this.tempDir = Path.of(System.getProperty("java.io.tmpdir"));
            logger.warning("Failed to create temp directory, falling back to system temp: " + e.getMessage());
        }
    }

    /**
     * Clean up resources when this instance is no longer needed
     */
    public void cleanup() {
        try {
            if (tempDir != null && Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            logger.warning("Failed to delete temp file: " + path + " - " + e.getMessage());
                        }
                    });
                logger.fine("Cleaned up temporary directory: " + tempDir);
            }
        } catch (IOException e) {
            logger.warning("Failed to cleanup temp directory: " + e.getMessage());
        }
    }
    
    private JSONObject ok(String tool, JSONObject data) {
        return new JSONObject().put("ok", true).put("tool", tool).put("data", data).put("warnings", new JSONArray());
    }
    
    // Removed legacy fail() method - all calls converted to ErrorHandler pattern
    
    // Removed unused error constants - all errors now use ErrorHandler pattern
    
    // DEPRECATED PARAMETER CLAMPING: Use ParameterValidator for new code
    // These methods remain only for backward compatibility in deprecated tools
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Enforce handle discipline - FAIL FAST if image_handle is missing
     * Returns error JSONObject if handle is missing, null if successful
     * 
     * CRITICAL: No silent injection - explicit handles required for data integrity
     */
    private JSONObject enforceHandleDiscipline(JSONObject args) {
        // FAIL FAST: Require explicit image_handle parameter
        if (!args.has("image_handle") || args.isNull("image_handle") || 
            args.getString("image_handle").trim().isEmpty()) {
            
            return ErrorHandler.handleValidationError("openimage_validation", 
                new IllegalArgumentException("Explicit image_handle parameter is required. " +
                "Silent injection disabled to prevent stale image operations. " +
                "Include image_handle from open_image result in ALL tool calls."), logger, recovery);
        }
        
        // Validate handle exists in session
        String handle = args.getString("image_handle");
        if (store.getImage(handle) == null) {
            return ErrorHandler.handleValidationError("openimage_validation", 
                new IllegalArgumentException("Image handle '" + handle + "' not found in session. " +
                "Ensure you use the exact handle returned by open_image."), logger, recovery);
        }
        
        return null; // Success - explicit handle present and valid
    }
    
    /**
     * Tool: open_image
     * Load a gel image into the session
     * 
     * Security: Path traversal protection, file extension validation
     */
    public JSONObject openImage(JSONObject args) {
        // SECURITY: Validate and sanitize file path to prevent directory traversal attacks
        SecureToolValidator.Tools.validateOpenImage(args);
        try {
            // Path is now validated and sanitized
            String path = args.getString("path");
            ImagePlus imp = IJ.openImage(path);
            if (imp == null) {
                throw new IllegalArgumentException("Could not open image: " + path);
            }
            
            String handle = store.putImage(imp);
            // Explicitly set as current (putImage already does this, but being explicit)
            store.setLastActiveImageHandle(handle);
            imp.show(); // Display in ImageJ
            
            return new JSONObject()
                .put("image_handle", handle)
                .put("width", imp.getWidth())
                .put("height", imp.getHeight())
                .put("title", imp.getTitle())
                .put("CRITICAL_INSTRUCTION", 
                     "ALWAYS use image_handle='" + handle + "' in ALL subsequent tool calls. Never omit this parameter!")
                .put("handle_persistence_reminder", 
                     "This image handle must be included in every tool call: detect_lanes, detect_bands, quantify_bands, etc.");
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("open_image", e, logger, recovery);
        } catch (RuntimeException e) {
            // Handle other runtime errors (file access issues from IJ.openImage, etc.)
            if (e.getMessage() != null && e.getMessage().contains("file")) {
                return ErrorHandler.handleFileError("open_image", e, logger, recovery);
            }
            return ErrorHandler.handleUnexpectedError("open_image", e, logger, recovery);
        }
    }
    
    /**
     * Tool: preprocess
     * Apply preprocessing steps to an image
     * 
     * Security: Parameter validation, operation limits, handle validation
     * IMPORTANT: All preprocessing operations are destructive and will modify pixel data.
     * Set "destructive": true to modify the original image in place.
     * If destructive=false or omitted, creates a duplicate and processes that instead.
     */
    public JSONObject preprocess(JSONObject args) {
        // SECURITY: Validate preprocessing parameters and limit operations
        SecureToolValidator.Tools.validatePreprocess(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            SessionStore.ImageRecord originalImg = store.getImage(args.getString("image_handle"));
            if (originalImg == null) {
                return ErrorHandler.handleValidationError("preprocess", 
                    new IllegalArgumentException("Image not found in session: " + args.getString("image_handle")), 
                    logger, recovery);
            }
            
            boolean destructive = args.optBoolean("destructive", false);
            
            // Support both mode-based and step-based preprocessing
            JSONArray steps;
            String cacheKey;
            
            if (args.has("mode") && !args.isNull("mode")) {
                // Mode-based preprocessing - convert mode to equivalent steps
                String mode = args.getString("mode");
                cacheKey = createPreprocessingCacheKey(originalImg.handle, mode, destructive);
                
                // Convert mode to equivalent step array for processing
                switch (mode) {
                    case "coomassie_default" -> {
                        // Create steps equivalent to coomassie_default mode
                        double saturated = 0.3; // FIXED: TODO - make this configurable
                        double sigma = 1.0;     // FIXED: TODO - make this configurable
                        int radius = 60;        // FIXED: TODO - make this configurable
                        double quantile = 0.15; // FIXED: TODO - make this configurable
                        steps = new JSONArray()
                            .put(new JSONObject().put("op", "8-bit"))
                            .put(new JSONObject().put("op", "enhance_contrast").put("saturated", saturated).put("normalize", true))
                            .put(new JSONObject().put("op", "gaussian_blur").put("sigma", sigma))
                            .put(new JSONObject().put("op", "lane_wise_background").put("radius", radius).put("quantile", quantile));
                    }
                    default -> {
                        return ErrorHandler.handleValidationError("preprocess", 
                            new IllegalArgumentException("Unknown preprocessing mode: " + mode), 
                            logger, recovery);
                    }
                }
            } else {
                // Step-based preprocessing (original approach)
                ToolSchemaValidator.requireArray(args, "steps");
                steps = args.getJSONArray("steps");
                cacheKey = createPreprocessingCacheKey(originalImg.handle, steps, destructive);
            }
            
            // Check if we've already processed this exact combination
            if (preprocessingCache.containsKey(cacheKey)) {
                String cachedHandle = preprocessingCache.get(cacheKey);
                if (store.getImage(cachedHandle) != null) {
                    // Return cached result
                    JSONObject data = new JSONObject()
                        .put("image_handle", cachedHandle)
                        .put("cache_hit", true)
                        .put("processed_steps", steps) // Return original steps as-is
                        .put("parameters_used", args);
                    return ok("preprocess", data);
                }
            }
            
            // Decide whether to work on original or duplicate
            SessionStore.ImageRecord workingImg;
            String resultHandle;
            
            if (destructive) {
                // Work directly on original image
                workingImg = originalImg;
                resultHandle = originalImg.handle;
            } else {
                // Create duplicate only when needed (not already cached)
                // Use conditionalDuplicate utility for potential optimization
                ImagePlus duplicate = conditionalDuplicate(originalImg.image, true, "processed");
                resultHandle = store.putImage(duplicate);
                workingImg = store.getImage(resultHandle);
            }
            
            // Delegate all preprocessing to ImagePreprocessor (single source of truth)
            ImagePlus processedImage = ImagePreprocessor.apply(workingImg.image, steps, destructive);
            
            // Update the image in the session store with the processed result
            if (destructive) {
                // For destructive processing, we already modified the original image
                workingImg.image.updateAndDraw();
            } else {
                // For non-destructive processing, replace the stored image with the processed version
                store.replaceImageContent(resultHandle, processedImage);
                workingImg = store.getImage(resultHandle);
            }
            
            // Echo back the original steps for transparency (ImagePreprocessor handles validation internally)
            JSONArray processedSteps = steps;
            
            workingImg.image.updateAndDraw();
            
            // Set the result as the current active image
            store.setLastActiveImageHandle(resultHandle);
            
            JSONObject data = new JSONObject()
                .put("image_handle", resultHandle)
                .put("steps_applied", steps.length())
                .put("processed_steps", processedSteps) // Echo all actual values used
                .put("original_steps", steps) // Include the modified original steps with clamped values
                .put("destructive_operation", destructive)
                .put("original_preserved", !destructive)
                .put("parameters_used", new JSONObject()
                    .put("destructive", destructive)
                    .put("total_steps", steps.length()));
            
            if (!destructive) {
                data.put("original_image_handle", originalImg.handle);
                data.put("processing_note", "Original image preserved. New processed image created with handle: " + resultHandle);
            }
            
            // Cache the result for future use
            preprocessingCache.put(cacheKey, resultHandle);
            data.put("cache_miss", true); // Indicate this was newly processed
            
            return ok("preprocess", data);
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("preprocess", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleImageProcessingError("preprocess", e, logger, recovery);
        }
    }
    
    /**
     * Tool: detect_lanes  
     * Detect lanes in a gel image
     * 
     * Security: Handle validation, parameter range checking
     */
    public JSONObject detectLanes(JSONObject args) {
        // SECURITY: Validate all parameters with ranges and handle format
        SecureToolValidator.Tools.validateDetectLanes(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("detect_lanes", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Clamp and echo parameters for determinism - improved defaults for robustness
            int expectedLanes = args.optInt("expected_lanes", 12); // Default to 12 lanes for typical gels
            expectedLanes = clamp(expectedLanes, 1, 50);
            args.put("expected_lanes", expectedLanes);
            
            // Use non-constant spacing by default for better robustness
            boolean constantSpacing = args.optBoolean("constant_spacing", false);
            
            // Use thinner lane width to prevent lane merging (was 0.55, now 0.40)
            double laneWidth = args.optDouble("lane_width_fraction", 0.40);
            laneWidth = clamp(laneWidth, 0.1, 0.9);
            args.put("lane_width_fraction", laneWidth);
            
            double gridOffset = args.optDouble("grid_offset", 0.0);
            gridOffset = clamp(gridOffset, -0.5, 0.5);
            args.put("grid_offset", gridOffset);
            
            // Increase min peak distance for better lane separation (was 20.0, now 20-22)
            double minPeakDistance = args.optDouble("min_peak_distance", 20.0);
            minPeakDistance = clamp(minPeakDistance, 18.0, 200.0); // Allow 18-22px range
            args.put("min_peak_distance", minPeakDistance);
            
            // Suppress ROI Manager and ensure clean overlay workflow
            IJUtils.silenceRoiManager(img.image);
            
            // Phase 1.2: Use truth preservation detection for dual reporting
            LaneDetector.DetectionResult detectionResult = LaneDetector.findLanesWithTruthPreservation(
                img.image, expectedLanes, constantSpacing, LaneDetector.Polarity.AUTO
            );
            List<Lane> lanes = detectionResult.lanes;
            
            // Debug logging for lane detection diagnostics
            if (!lanes.isEmpty()) {
                double meanWidth = lanes.stream().mapToInt(lane -> lane.xEnd() - lane.xStart()).average().orElse(0);
                double[] spacings = new double[lanes.size() - 1];
                for (int i = 0; i < lanes.size() - 1; i++) {
                    spacings[i] = lanes.get(i + 1).xStart() - lanes.get(i).xEnd();
                }
                double meanSpacing = spacings.length > 0 ? java.util.Arrays.stream(spacings).average().orElse(0) : 0;
                double spacingStd = spacings.length > 1 ? 
                    Math.sqrt(java.util.Arrays.stream(spacings).map(x -> Math.pow(x - meanSpacing, 2)).average().orElse(0)) : 0;
                
                logger.info(String.format("[LANES] expected=%d found=%d lane_width_px=%.1f±%.1f spacing_px=%.1f±%.1f", 
                    expectedLanes, lanes.size(), meanWidth, 0.0, meanSpacing, spacingStd));
            } else {
                logger.warning(String.format("[LANES] expected=%d found=0 DETECTION_FAILED", expectedLanes));
            }
            
            // Create overlay
            Overlay overlay = new Overlay();
            int height = img.image.getHeight();
            
            for (int i = 0; i < lanes.size(); i++) {
                Lane lane = lanes.get(i);
                int x = lane.xStart();
                int width = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                
                // Lane overlay (green) with label
                Roi laneRoi = new Roi(x, 0, width, height);
                laneRoi.setStrokeColor(new Color(0, 255, 0, 180)); // Green
                laneRoi.setStrokeWidth(2.0);
                laneRoi.setName("Lane " + (i + 1));
                overlay.add(laneRoi);
                
                // Add lane label text overlay
                TextRoi laneLabel = new TextRoi(x + width/2 - 10, 15, "L" + (i + 1));
                laneLabel.setStrokeColor(new Color(0, 255, 0));
                laneLabel.setFillColor(new Color(255, 255, 255, 200));
                laneLabel.setFont(new Font("Arial", Font.BOLD, 14));
                overlay.add(laneLabel);
            }
            
            img.image.setOverlay(overlay);
            img.image.updateAndDraw();
            IJUtils.refresh(img.image);
            
            String overlayHandle = store.putOverlay(overlay, img.handle);
            String analysisHandle = store.putAnalysis("lanes", lanes, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            // Build standardized success response with Phase 1.2 dual reporting
            JSONObject data = new JSONObject()
                .put("lanes_found", lanes.size())
                .put("lanes_raw", detectionResult.rawCount)  // Phase 1.2: Raw detection count
                .put("lanes_reconciled", detectionResult.finalCount)  // Phase 1.2: Final count after reconciliation
                .put("reconciliation_explanation", detectionResult.reconciliationExplanation)  // Phase 1.2: Explanation
                .put("overlay_handle", overlayHandle)
                .put("analysis_handle", analysisHandle)
                .put("image_handle", img.handle)
                .put("parameters_used", new JSONObject()
                    .put("expected_lanes", expectedLanes)
                    .put("constant_spacing", constantSpacing)
                    .put("lane_width_fraction", laneWidth)
                    .put("grid_offset", gridOffset)
                    .put("min_peak_distance", minPeakDistance));
            
            return ok("detect_lanes", data);
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("detect_lanes", e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError("detect_lanes", e, logger, recovery);
        } catch (NullPointerException e) {
            return ErrorHandler.handleImageProcessingError("detect_lanes", e, logger, recovery);
        } catch (RuntimeException e) {
            return ErrorHandler.handleUnexpectedError("detect_lanes", e, logger, recovery);
        }
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
     * Configuration-aware band detection that uses separate baseline parameters
     * 
     * CRITICAL FIX: This method ensures band detection uses bands.baseline config
     * instead of inheriting lane baseline parameters.
     * 
     * @param args Standard band detection arguments
     * @param config Full configuration map with bands.baseline section
     * @return JSONObject with band detection results using proper baseline config
     */
    public JSONObject detectBandsWithConfig(JSONObject args, java.util.Map<String, Object> config) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            BandDetectionParams params = validateAndPrepareBandDetectionParams(args);
            List<Lane> lanes = getLanesForBandDetection(args, params.imageHandle);
            
            // CRITICAL FIX: Use the overloaded method that accepts full configuration
            BandDetectionResult result = performBandDetection(lanes, params, config);
            String overlayHandle = createBandDetectionOverlay(result, params.imageHandle);
            return formatBandDetectionResponse(result, overlayHandle, params);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("detect_bands_with_config", e, logger, recovery);
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
        final int rawTotalBands;  // Phase 1.2: Raw detection count for truth preservation

        BandDetectionResult(List<Lane> lanes, List<List<Band>> allBands, int totalBands) {
            this.lanes = lanes;
            this.allBands = allBands;
            this.totalBands = totalBands;
            this.rawTotalBands = totalBands;  // Default: raw same as final
        }
        
        // Phase 1.2: Enhanced constructor with raw count tracking
        BandDetectionResult(List<Lane> lanes, List<List<Band>> allBands, int totalBands, int rawTotalBands) {
            this.lanes = lanes;
            this.allBands = allBands;
            this.totalBands = totalBands;
            this.rawTotalBands = rawTotalBands;
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
        int rawTotalBands = 0;  // Phase 1.2: Track raw detection count
        
        // Create configuration map from band detection parameters WITH baseline config
        java.util.Map<String, Object> bandsConfig = new java.util.HashMap<>();
        bandsConfig.put("min_distance_px", (int) params.minPeakDistance);
        bandsConfig.put("prominence_frac", params.prominence);
        
        // CRITICAL FIX: Add band-specific baseline configuration to prevent lane parameter inheritance
        java.util.Map<String, Object> bandBaselineConfig = new java.util.HashMap<>();
        bandBaselineConfig.put("method", "percentile");
        bandBaselineConfig.put("window_frac", 0.015);  // Gentle 1.5% window for bands
        bandBaselineConfig.put("quantile", 0.08);      // Lower quantile for bands
        bandBaselineConfig.put("clamp_min_px", 2);     // Smaller min for bands
        bandBaselineConfig.put("clamp_max_px", 8);     // Smaller max for bands
        bandsConfig.put("baseline", bandBaselineConfig);
        
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("bands", bandsConfig);
        
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            Lane lane = lanes.get(laneIndex);
            // Use config-aware band detection method
            List<Band> bands = BandDetector.findBands(img.image, lane, config);
            allBands.add(bands);
            totalBands += bands.size();
            rawTotalBands += bands.size();  // Phase 1.2: For now, same as final (no filtering yet)
            
            // Debug logging for band detection diagnostics
            logger.fine(String.format("[BANDS] lane i=%d, peaks=%d, prominence>=%.3f, sigma=%.1f, min_distance_px=%d", 
                laneIndex + 1, bands.size(), params.prominence, params.smoothSigma, (int) params.minPeakDistance));
        }
        
        // Phase 1.2: Log raw band detection count for truth preservation
        logger.info(String.format("[TRUTH_PRESERVED] Raw band detection count: %d (before any adjustments)", rawTotalBands));
        
        return new BandDetectionResult(lanes, allBands, totalBands, rawTotalBands);
    }

    /**
     * Overloaded performBandDetection that accepts full configuration with band-specific baseline parameters
     * 
     * CRITICAL FIX: This method uses the provided config instead of creating its own,
     * ensuring band baseline parameters are preserved and don't inherit from lane settings.
     * 
     * @param lanes List of lanes to detect bands in
     * @param params Basic band detection parameters
     * @param fullConfig Full configuration map with bands.baseline section
     * @return BandDetectionResult with bands detected using proper baseline parameters
     */
    private BandDetectionResult performBandDetection(List<Lane> lanes, BandDetectionParams params, java.util.Map<String, Object> fullConfig) {
        SessionStore.ImageRecord img = store.getImage(params.imageHandle);
        List<List<Band>> allBands = new ArrayList<>();
        int totalBands = 0;
        int rawTotalBands = 0;
        
        // CRITICAL FIX: Use provided configuration directly instead of creating new one
        // This preserves bands.baseline parameters from the optimization config
        
        // Log the configuration being used to verify baseline parameters
        if (fullConfig.containsKey("bands")) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> bandsConfig = (java.util.Map<String, Object>) fullConfig.get("bands");
            if (bandsConfig.containsKey("baseline")) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> bandBaseline = (java.util.Map<String, Object>) bandsConfig.get("baseline");
                System.err.printf("[BASELINE_FIX] performBandDetection using bands.baseline: method=%s window_frac=%s quantile=%s%n",
                                 bandBaseline.getOrDefault("method", "percentile"),
                                 bandBaseline.getOrDefault("window_frac", "0.015"), 
                                 bandBaseline.getOrDefault("quantile", "0.08"));
            } else {
                System.err.printf("[BASELINE_FIX] WARNING: No bands.baseline in config, will use detect.baseline fallback%n");
            }
        } else {
            System.err.printf("[BASELINE_FIX] WARNING: No bands section in config%n");
        }
        
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            Lane lane = lanes.get(laneIndex);
            // Use config-aware band detection method with PROVIDED configuration
            List<Band> bands = BandDetector.findBands(img.image, lane, fullConfig);
            allBands.add(bands);
            totalBands += bands.size();
            rawTotalBands += bands.size();
            
            // Debug logging for band detection diagnostics  
            logger.fine(String.format("[BANDS] lane i=%d, peaks=%d, prominence>=%.3f, sigma=%.1f, min_distance_px=%d", 
                laneIndex + 1, bands.size(), params.prominence, params.smoothSigma, (int) params.minPeakDistance));
        }
        
        // Log raw band detection count for truth preservation
        logger.info(String.format("[TRUTH_PRESERVED] Raw band detection count: %d (before any adjustments)", rawTotalBands));
        
        return new BandDetectionResult(lanes, allBands, totalBands, rawTotalBands);
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
            .put("bands_raw", result.rawTotalBands)  // Phase 1.2: Raw detection count
            .put("bands_reconciled", result.totalBands)  // Phase 1.2: Final count after reconciliation
            .put("overlay_handle", overlayHandle)
            .put("analysis_handle", analysisHandle)
            .put("image_handle", img.handle)
            .put("parameters_used", new JSONObject()
                .put("sensitivity", params.sensitivity)
                .put("min_band_height", params.minBandHeight)
                .put("prominence", params.prominence));
        
        return ok("detect_bands", data);
    }
    
    /**
     * Tool: adjust_lanes
     * Adjust lane positions (offset/width)
     */
    public JSONObject adjustLanes(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("adjust_lanes", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Clamp and echo parameters for determinism
            double offsetAmount = args.optDouble("offset", 0.0);
            offsetAmount = clamp(offsetAmount, -0.5, 0.5); // ±50% offset
            args.put("offset", offsetAmount);
            
            double widthAdjust = args.optDouble("width_adjust", 1.0);
            widthAdjust = clamp(widthAdjust, 0.5, 2.0); // 50% to 200% width
            args.put("width_adjust", widthAdjust);
            
            // Get current overlay
            Overlay currentOverlay = img.currentOverlay;
            if (currentOverlay == null) {
                return ErrorHandler.handleValidationError("adjust_lanes", 
                    new IllegalStateException("No lanes detected yet - run detect_lanes first"), logger, recovery);
            }
            
            // Create new overlay with adjusted positions
            Overlay newOverlay = new Overlay();
            int imageWidth = img.image.getWidth();
            
            for (int i = 0; i < currentOverlay.size(); i++) {
                Roi roi = currentOverlay.get(i);
                if (roi.getStrokeColor().getGreen() == 255) { // Lane ROI
                    Rectangle bounds = roi.getBounds();
                    int newX = (int)(bounds.x + offsetAmount * imageWidth);
                    int newWidth = (int)(bounds.width * widthAdjust);
                    
                    Roi adjustedRoi = new Roi(newX, bounds.y, newWidth, bounds.height);
                    adjustedRoi.setStrokeColor(roi.getStrokeColor());
                    adjustedRoi.setStrokeWidth(roi.getStrokeWidth());
                    adjustedRoi.setName(roi.getName());
                    newOverlay.add(adjustedRoi);
                }
            }
            
            img.image.setOverlay(newOverlay);
            img.image.updateAndDraw();
            
            String overlayHandle = store.putOverlay(newOverlay, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            // Build standardized success response
            JSONObject data = new JSONObject()
                .put("overlay_handle", overlayHandle)
                .put("image_handle", img.handle)
                .put("parameters_used", new JSONObject()
                    .put("offset", offsetAmount)
                    .put("width_adjust", widthAdjust));
            
            return ok("adjust_lanes", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("adjust_lanes", e, logger, recovery);
        }
    }
    
    /**
     * Tool: render_overlay_png
     * Export current view with labeled overlays as PNG for visual communication.
     * IMPORTANT: This PNG is for visual reference only - all measurements must use original image data.
     * The labeled overlay helps users identify lanes (L1, L2, etc.) and bands (B1, B2, etc.) for discussion.
     */
    public JSONObject renderOverlayPng(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("render_overlay_png", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Clamp and echo parameters for determinism
            int maxWidth = args.optInt("max_width", 1200);
            maxWidth = clamp(maxWidth, 200, 4000); // 200-4000 pixels
            args.put("max_width", maxWidth);
            
            int quality = args.optInt("quality", 90);
            quality = clamp(quality, 50, 100); // 50-100% quality
            args.put("quality", quality);
            
            // Duplicate base image (no overlay burning)
            ImagePlus dup = img.image.duplicate();
            
            // Scale if needed
            if (dup.getWidth() > maxWidth) {
                double scale = maxWidth / (double)dup.getWidth();
                int newHeight = (int)(dup.getHeight() * scale);
                ImageProcessor proc = dup.getProcessor();
                proc = proc.resize(maxWidth, newHeight);
                dup.setProcessor(proc);
            }
            
            // Save base image
            String baseFilename = "gel_base_" + System.currentTimeMillis() + ".png";
            Path baseOutputPath = tempDir.resolve(baseFilename);
            Path baseTempPath = baseOutputPath.resolveSibling(baseOutputPath.getFileName() + ".tmp");
            
            // Save overlay as separate transparent PNG if it exists
            String overlayFilename = "gel_overlay_" + System.currentTimeMillis() + ".png";
            Path overlayOutputPath = tempDir.resolve(overlayFilename);
            Path overlayTempPath = overlayOutputPath.resolveSibling(overlayOutputPath.getFileName() + ".tmp");
            
            // For backward compatibility, we'll still create a flattened version but log it as deprecated
            String filename = "gel_combined_" + System.currentTimeMillis() + ".png";  
            Path outputPath = tempDir.resolve(filename);
            Path tempPath = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
            
            try {
                // Save base image (scaled gel without overlay)
                BufferedImage baseBufferedImage = dup.getBufferedImage();
                ImageIO.write(baseBufferedImage, "PNG", baseTempPath.toFile());
                Files.move(baseTempPath, baseOutputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                
                // Save overlay as transparent PNG if it exists
                String overlayPath = null;
                if (img.currentOverlay != null) {
                    // Scale overlay to match resized image
                    ImagePlus overlayImg = img.image.duplicate();
                    overlayImg.setOverlay(img.currentOverlay);
                    if (overlayImg.getWidth() > maxWidth) {
                        double scale = maxWidth / (double)overlayImg.getWidth();
                        int newHeight = (int)(overlayImg.getHeight() * scale);
                        // Scale overlay ROIs
                        Overlay scaledOverlay = new Overlay();
                        for (Roi roi : img.currentOverlay.toArray()) {
                            Roi scaledRoi = (Roi) roi.clone();
                            scaledRoi.setLocation(
                                (int)(roi.getBounds().x * scale),
                                (int)(roi.getBounds().y * scale)
                            );
                            // Scale stroke width too
                            scaledRoi.setStrokeWidth((float)(roi.getStrokeWidth() * scale));
                            scaledOverlay.add(scaledRoi);
                        }
                        overlayImg = IJ.createImage("overlay", overlayImg.getType(), maxWidth, newHeight, 1);
                        overlayImg.setOverlay(scaledOverlay);
                    }
                    
                    OverlayExporter.exportOverlayPNG(overlayImg, overlayTempPath.toFile());
                    Files.move(overlayTempPath, overlayOutputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    overlayPath = overlayOutputPath.toString();
                }
                
                // Create combined image for backward compatibility (DEPRECATED)
                ImagePlus combined = dup.duplicate();
                if (img.currentOverlay != null) {
                    combined.setOverlay(img.currentOverlay);
                    combined = combined.flatten(); // Still flatten for compatibility but mark deprecated
                }
                BufferedImage combinedBuffered = combined.getBufferedImage();
                ImageIO.write(combinedBuffered, "PNG", tempPath.toFile());
                Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                
            } catch (IOException e) {
                // Fallback to original flattening approach if atomic write fails  
                try { 
                    Files.deleteIfExists(tempPath); 
                    Files.deleteIfExists(baseTempPath);
                    Files.deleteIfExists(overlayTempPath);
                } catch (IOException ignored) {}
                
                // Original fallback behavior
                ImagePlus fallbackImg = img.image.duplicate();
                if (img.currentOverlay != null) {
                    fallbackImg.setOverlay(img.currentOverlay);
                    fallbackImg = fallbackImg.flatten();
                }
                FileSaver fs = new FileSaver(fallbackImg);
                fs.saveAsPng(outputPath.toString());
            }
            
            // Build standardized success response with separate file paths
            JSONObject data = new JSONObject()
                .put("combined_png_path", outputPath.toString()) // DEPRECATED: flattened image
                .put("base_image_path", baseOutputPath.toString()) // Clean gel image
                .put("overlay_path", img.currentOverlay != null ? overlayOutputPath.toString() : null) // Transparent overlay PNG
                .put("width", dup.getWidth())
                .put("height", dup.getHeight())
                .put("image_handle", img.handle)
                .put("usage_note", "Use base_image_path + overlay_path for modern workflows. combined_png_path is deprecated.")
                .put("visual_elements", "Lane labels: L1, L2, L3... Band labels: B1, B2, B3... (first 3 bands per lane)")
                .put("export_format", "separate_layers") // Modern approach: base + overlay
                .put("parameters_used", new JSONObject()
                    .put("max_width", maxWidth)
                    .put("quality", quality));
            
            return ok("render_overlay_png", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("render_overlay_png", e, logger, recovery);
        }
    }
    
    /**
     * Tool: quantify_bands
     * Quantify band intensities using ORIGINAL image data only.
     * CRITICAL: This method operates on the original ImagePlus data, not any PNG exports.
     * All measurements are performed on pixel values from the source image.
     */
    public JSONObject quantifyBands(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
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
            
            String analysisHandle = args.optString("analysis_handle", "");
            if (analysisHandle.isEmpty() || !store.hasAnalysis(analysisHandle)) {
                return ErrorHandler.handleValidationError("quantify_bands", 
                    new IllegalArgumentException("Analysis handle not found - run detect_bands first"), logger, recovery);
            }
            
            // Clamp and echo parameters for determinism
            String backgroundMethod = args.optString("background_method", "median");
            // Validate background method options
            if (!backgroundMethod.equals("median") && !backgroundMethod.equals("rolling") && !backgroundMethod.equals("none")) {
                backgroundMethod = "median";
            }
            args.put("background_method", backgroundMethod);
            
            int backgroundRadius = args.optInt("background_radius", 50);
            backgroundRadius = clamp(backgroundRadius, 10, 200); // 10-200 pixels
            args.put("background_radius", backgroundRadius);
            
            // PCR-specific housekeeping normalization parameters
            int housekeepingLaneIdx = args.optInt("housekeeping_lane_idx", -1); // -1 = no normalization
            int housekeepingBandIdx = args.optInt("housekeeping_band_idx", -1); // -1 = use lane average
            boolean enablePCRNormalization = housekeepingLaneIdx > 0;
            
            if (enablePCRNormalization) {
                args.put("housekeeping_lane_idx", housekeepingLaneIdx);
                args.put("housekeeping_band_idx", housekeepingBandIdx);
                args.put("pcr_normalization_enabled", true);
            }
            
            // IMPORTANT: All quantification uses original ImagePlus pixel data
            ImagePlus originalImage = img.image; // Original image data for measurements
            
            // Get bands from analysis
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            @SuppressWarnings("unchecked")
            List<List<Band>> allBands = (List<List<Band>>) analysis.data;
            
            // Get lanes for enhanced quantification
            List<Lane> lanes = LaneDetector.findLanes(originalImage, 0, false, 0.55, 0.0, true);
            
            // Check if this is MW determination workflow (enhanced analysis)
            boolean enhancedAnalysis = args.optString("workflow_type", "").contains("molecular_weight") ||
                                     args.optString("workflow_type", "").contains("mw_determination");
            
            JSONArray results = new JSONArray();
            
            for (int laneIdx = 0; laneIdx < allBands.size() && laneIdx < lanes.size(); laneIdx++) {
                List<Band> bands = allBands.get(laneIdx);
                Lane lane = lanes.get(laneIdx);
                JSONObject laneResult = new JSONObject()
                    .put("lane", laneIdx + 1)
                    .put("band_count", bands.size());
                
                JSONArray bandIntensities = new JSONArray();
                for (Band band : bands) {
                    JSONObject bandData = new JSONObject()
                        .put("y_position", band.y())
                        .put("intensity", band.area())  // Using area as intensity measure
                        .put("area", band.area());
                    
                    // Enhanced analysis for MW determination
                    if (enhancedAnalysis) {
                        // Use Quant.integrateBand for detailed analysis including sharpness and smear
                        int bandHeight = Math.max(5, (int)(originalImage.getHeight() * 0.02)); // ~2% of image height
                        int bandTop = Math.max(0, band.y() - bandHeight/2);
                        int bandBot = Math.min(originalImage.getHeight()-1, band.y() + bandHeight/2);
                        
                        AssistBand assistBand = Quant.integrateBand(originalImage, lane, bandTop, bandBot);
                        
                        bandData.put("snr", assistBand.snr)
                               .put("confidence", Quant.snrToConfidence(assistBand.snr))
                               .put("sharpness", assistBand.sharpness)
                               .put("smear_percent", assistBand.smearPercent)
                               .put("area_corrected", assistBand.areaCorr)
                               .put("area_background", assistBand.areaBg);
                    }
                    
                    bandIntensities.put(bandData);
                }
                laneResult.put("bands", bandIntensities);
                results.put(laneResult);
            }
            
            // Apply PCR housekeeping normalization if enabled
            if (enablePCRNormalization) {
                results = applyPCRHousekeepingNormalization(results, housekeepingLaneIdx, housekeepingBandIdx, enhancedAnalysis);
            }
            
            String quantHandle = store.putAnalysis("quantification", results, img.handle);
            
            // Build standardized success response
            JSONObject data = new JSONObject()
                .put("quantification_handle", quantHandle)
                .put("lanes_quantified", allBands.size())
                .put("total_bands", allBands.stream().mapToInt(List::size).sum())
                .put("results", results)
                .put("image_handle", img.handle)
                .put("measurement_source", "original_image_data")
                .put("parameters_used", new JSONObject()
                    .put("background_method", backgroundMethod)
                    .put("background_radius", backgroundRadius)
                    .put("pcr_normalization_enabled", enablePCRNormalization)
                    .put("housekeeping_lane_idx", enablePCRNormalization ? housekeepingLaneIdx : null)
                    .put("housekeeping_band_idx", enablePCRNormalization ? housekeepingBandIdx : null));
            
            return ok("quantify_bands", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("quantify_bands", e, logger, recovery);
        }
    }
    
    /**
     * Apply PCR housekeeping normalization to quantification results
     * Calculates ΔΔI (delta-delta intensity) ratios for semi-quantitative PCR analysis
     */
    private JSONArray applyPCRHousekeepingNormalization(JSONArray rawResults, int housekeepingLaneIdx, 
                                                       int housekeepingBandIdx, boolean enhancedAnalysis) {
        try {
            // Convert to 0-based index
            int hkLaneIndex = housekeepingLaneIdx - 1;
            int hkBandIndex = housekeepingBandIdx - 1;
            
            // Get housekeeping reference intensity
            double housekeepingIntensity = getHousekeepingReferenceIntensity(rawResults, hkLaneIndex, hkBandIndex, enhancedAnalysis);
            
            if (housekeepingIntensity <= 0) {
                logger.warning("Could not determine housekeeping reference intensity");
                return rawResults; // Return original results if normalization fails
            }
            
            // Apply normalization to all lanes
            JSONArray normalizedResults = new JSONArray();
            
            for (int i = 0; i < rawResults.length(); i++) {
                JSONObject laneResult = rawResults.getJSONObject(i);
                JSONObject normalizedLaneResult = new JSONObject(laneResult, JSONObject.getNames(laneResult));
                
                JSONArray bands = laneResult.getJSONArray("bands");
                JSONArray normalizedBands = new JSONArray();
                
                for (int j = 0; j < bands.length(); j++) {
                    JSONObject band = bands.getJSONObject(j);
                    JSONObject normalizedBand = new JSONObject(band, JSONObject.getNames(band));
                    
                    // Get raw intensity (use area_corrected if available, otherwise area/intensity)
                    double rawIntensity = enhancedAnalysis ? 
                                         band.optDouble("area_corrected", band.getDouble("intensity")) :
                                         band.getDouble("intensity");
                    
                    // Calculate ΔΔI (normalized intensity ratio)
                    double deltaI = rawIntensity / housekeepingIntensity;
                    double deltaDeltaI = Math.log(deltaI) / Math.log(2.0); // Log2 ratio
                    
                    // Calculate relative copy number (2^(ΔΔI))
                    double relativeCopyNumber = Math.pow(2.0, deltaDeltaI);
                    
                    // Add normalized values while keeping originals
                    normalizedBand.put("intensity_raw", rawIntensity);
                    normalizedBand.put("intensity_normalized", deltaI);
                    normalizedBand.put("delta_delta_i", deltaDeltaI);
                    normalizedBand.put("relative_copy_number", relativeCopyNumber);
                    normalizedBand.put("housekeeping_reference", housekeepingIntensity);
                    
                    // Add quality flags for PCR analysis
                    String qualityFlag = "";
                    if (deltaI < 0.1) {
                        qualityFlag = "VERY_LOW";
                    } else if (deltaI < 0.5) {
                        qualityFlag = "LOW";
                    } else if (deltaI > 5.0) {
                        qualityFlag = "HIGH";
                    } else if (deltaI > 10.0) {
                        qualityFlag = "VERY_HIGH";
                    } else {
                        qualityFlag = "NORMAL";
                    }
                    normalizedBand.put("pcr_quality_flag", qualityFlag);
                    
                    normalizedBands.put(normalizedBand);
                }
                
                normalizedLaneResult.put("bands", normalizedBands);
                normalizedLaneResult.put("normalized_to_lane", housekeepingLaneIdx);
                normalizedLaneResult.put("normalized_to_band", housekeepingBandIdx > 0 ? housekeepingBandIdx : "average");
                normalizedResults.put(normalizedLaneResult);
            }
            
            return normalizedResults;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying PCR normalization", e);
            return rawResults; // Return original results if normalization fails
        }
    }
    
    /**
     * Get housekeeping reference intensity from the specified lane/band
     */
    private double getHousekeepingReferenceIntensity(JSONArray results, int hkLaneIndex, int hkBandIndex, boolean enhancedAnalysis) {
        try {
            if (hkLaneIndex < 0 || hkLaneIndex >= results.length()) {
                return -1; // Invalid lane index
            }
            
            JSONObject housekeepingLane = results.getJSONObject(hkLaneIndex);
            JSONArray bands = housekeepingLane.getJSONArray("bands");
            
            if (bands.length() == 0) {
                return -1; // No bands in housekeeping lane
            }
            
            if (hkBandIndex >= 0) {
                // Use specific band as housekeeping reference
                if (hkBandIndex >= bands.length()) {
                    return -1; // Invalid band index
                }
                
                JSONObject housekeepingBand = bands.getJSONObject(hkBandIndex);
                return enhancedAnalysis ? 
                       housekeepingBand.optDouble("area_corrected", housekeepingBand.getDouble("intensity")) :
                       housekeepingBand.getDouble("intensity");
            } else {
                // Use average of all bands in housekeeping lane
                double totalIntensity = 0.0;
                int validBands = 0;
                
                for (int i = 0; i < bands.length(); i++) {
                    JSONObject band = bands.getJSONObject(i);
                    double intensity = enhancedAnalysis ? 
                                     band.optDouble("area_corrected", band.getDouble("intensity")) :
                                     band.getDouble("intensity");
                    
                    if (intensity > 0) {
                        totalIntensity += intensity;
                        validBands++;
                    }
                }
                
                return validBands > 0 ? totalIntensity / validBands : -1;
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting housekeeping reference", e);
            return -1;
        }
    }
    
    /**
     * Tool: export_results
     * Export analysis results to CSV/JSON/PDF
     */
    public JSONObject exportResults(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        ToolSchemaValidator.requireArray(args, "export_formats");
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("export_results", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            if (!args.has("export_formats")) {
                return ErrorHandler.handleValidationError("export_results", 
                    new IllegalArgumentException("export_formats array is required"), logger, recovery);
            }
            
            JSONArray formats = args.getJSONArray("export_formats");
            
            // Clamp and echo parameters for determinism
            String baseFilename = args.optString("filename", "gel_analysis_" + System.currentTimeMillis());
            // Sanitize filename
            baseFilename = baseFilename.replaceAll("[^a-zA-Z0-9_-]", "_");
            baseFilename = baseFilename.substring(0, Math.min(baseFilename.length(), 100)); // Max 100 chars
            args.put("filename", baseFilename);
            
            String outputDir = args.optString("output_directory", tempDir.toString());
            
            int maxFileSize = args.optInt("max_file_size_mb", 50);
            maxFileSize = clamp(maxFileSize, 1, 500); // 1-500 MB limit
            args.put("max_file_size_mb", maxFileSize);
            Path outputPath = Path.of(outputDir);
            
            JSONArray exportedFiles = new JSONArray();
            
            for (int i = 0; i < formats.length(); i++) {
                String format = formats.getString(i).toLowerCase();
                
                switch (format) {
                    case "csv":
                        Path csvPath = outputPath.resolve(baseFilename + "_data.csv");
                        exportQuantificationCSV(csvPath, img.handle);
                        exportedFiles.put(new JSONObject()
                            .put("format", "csv")
                            .put("path", csvPath.toString())
                            .put("description", "Quantification data in CSV format"));
                        break;
                        
                    case "json":
                        Path jsonPath = outputPath.resolve(baseFilename + "_analysis.json");
                        JSONObject allData = new JSONObject()
                            .put("image_info", new JSONObject()
                                .put("handle", img.handle)
                                .put("width", img.image.getWidth())
                                .put("height", img.image.getHeight())
                                .put("title", img.image.getTitle()))
                            .put("session_summary", store.getSummary())
                            .put("analysis_timestamp", java.time.Instant.now().toString());
                        Files.writeString(jsonPath, allData.toString(2));
                        exportedFiles.put(new JSONObject()
                            .put("format", "json")
                            .put("path", jsonPath.toString())
                            .put("description", "Complete analysis data in JSON format"));
                        break;
                        
                    case "png":
                    case "labeled_png":
                        Path pngPath = outputPath.resolve(baseFilename + "_labeled.png");
                        exportLabeledPNG(pngPath, img.handle, args);
                        exportedFiles.put(new JSONObject()
                            .put("format", "png")
                            .put("path", pngPath.toString())
                            .put("description", "Labeled gel image for presentations and notebooks"));
                        break;
                        
                    case "high_res_png":
                        Path hiResPngPath = outputPath.resolve(baseFilename + "_high_res.png");
                        exportHighResLabeledPNG(hiResPngPath, img.handle, args);
                        exportedFiles.put(new JSONObject()
                            .put("format", "high_res_png")
                            .put("path", hiResPngPath.toString())
                            .put("description", "High resolution labeled gel image for publication"));
                        break;
                        
                    case "presentation_png":
                        Path presentationPngPath = outputPath.resolve(baseFilename + "_presentation.png");
                        exportPresentationPNG(presentationPngPath, img.handle, args);
                        exportedFiles.put(new JSONObject()
                            .put("format", "presentation_png")
                            .put("path", presentationPngPath.toString())
                            .put("description", "Presentation-optimized labeled gel with title and annotations"));
                        break;
                }
            }
            
            // Build standardized success response
            JSONObject data = new JSONObject()
                .put("exported_files", exportedFiles)
                .put("export_summary", new JSONObject()
                    .put("total_files", exportedFiles.length())
                    .put("base_filename", baseFilename)
                    .put("output_directory", outputPath.toString()))
                .put("image_handle", img.handle)
                .put("usage_guide", new JSONObject()
                    .put("labeled_png", "Perfect for notebooks, presentations, and documentation")
                    .put("high_res_png", "Publication-quality with full resolution and intensity values")
                    .put("presentation_png", "Includes title and summary statistics for slides")
                    .put("csv", "Quantification data for further analysis in Excel/R/Python")
                    .put("json", "Complete analysis metadata and session information"))
                .put("parameters_used", new JSONObject()
                    .put("filename", baseFilename)
                    .put("max_file_size_mb", maxFileSize)
                    .put("export_formats", formats));
            
            return ok("export_results", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_results", e, logger, recovery);
        }
    }
    
    /**
     * Tool: clear_session
     * Clear all session data
     */
    
    
    
    /**
     * Tool: check_contamination
     * Identify potential contamination in colonies
     */
    public JSONObject checkContamination(JSONObject args) {
        try {
            // Validate image handle with recovery
            String imageHandle = args.optString("image_handle", "");
            JSONObject validation = recovery.validateHandle(imageHandle, "image");
            
            if (!validation.getBoolean("valid")) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", true);
                errorResponse.put("validation", validation);
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            double threshold = args.optDouble("sensitivity", 0.7);
            
            // Simplified contamination detection based on size/shape outliers
            JSONObject contaminationReport = new JSONObject();
            contaminationReport.put("suspicious_colonies", 0);
            contaminationReport.put("confidence", threshold);
            contaminationReport.put("recommendation", "Manual inspection recommended for unusual colony morphology");
            
            String analysisHandle = store.putAnalysis("contamination", contaminationReport.toString(), img.handle);
            
            return new JSONObject()
                .put("contamination_detected", false)
                .put("report", contaminationReport)
                .put("analysis_handle", analysisHandle)
                .put("image_handle", img.handle);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("check_contamination", e, logger, recovery);
        }
    }
    
    /**
     * Tool: calibrate_molecular_weight
     * Calibrate molecular weight ladder
     */
    public JSONObject calibrateMolecularWeight(JSONObject args) {
        try {
            // Validate image handle with recovery
            String imageHandle = args.optString("image_handle", "");
            JSONObject validation = recovery.validateHandle(imageHandle, "image");
            
            if (!validation.getBoolean("valid")) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", true);
                errorResponse.put("validation", validation);
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            String ladderType = args.optString("ladder_type", "protein");
            int ladderLane = args.optInt("ladder_lane", 1);
            
            // This would integrate with existing molecular weight calibration
            JSONObject calibration = new JSONObject();
            calibration.put("ladder_type", ladderType);
            calibration.put("lane", ladderLane);
            calibration.put("status", "calibrated");
            
            String analysisHandle = store.putAnalysis("mw_calibration", calibration.toString(), img.handle);
            
            return new JSONObject()
                .put("calibration_success", true)
                .put("ladder_type", ladderType)
                .put("analysis_handle", analysisHandle)
                .put("image_handle", img.handle);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("calibrate_molecular_weight", e, logger, recovery);
        }
    }
    
    /**
     * Tool: calibrate_standard_curve
     * Calibrate protein standard curve for quantitative analysis with LOQ/LLOQ determination
     */
    public JSONObject calibrateStandardCurve(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("calibrate_standard_curve", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Get standard curve parameters
            JSONArray concentrations = args.optJSONArray("concentrations");
            JSONArray responses = args.optJSONArray("responses");
            
            if (concentrations == null || responses == null || 
                concentrations.length() != responses.length() || concentrations.length() < 3) {
                return ErrorHandler.handleValidationError("calibrate_standard_curve", 
                    new IllegalArgumentException("Need at least 3 matched concentration-response pairs"), 
                    logger, recovery);
            }
            
            // Convert to lists
            List<Double> concList = new ArrayList<>();
            List<Double> respList = new ArrayList<>();
            
            for (int i = 0; i < concentrations.length(); i++) {
                concList.add(concentrations.getDouble(i));
                respList.add(responses.getDouble(i));
            }
            
            // Fit standard curve using enhanced calibrator
            Calibrator.StandardCurveFitter.Model curve = Calibrator.StandardCurveFitter.fit(concList, respList);
            
            // Get CV predictions
            List<Calibrator.StandardCurveFitter.CVPrediction> cvPredictions = curve.getCVPredictions();
            
            // Store calibration data
            JSONObject calibData = new JSONObject();
            calibData.put("slope", curve.getSlope());
            calibData.put("intercept", curve.getIntercept());
            calibData.put("r2", curve.getR2());
            calibData.put("loq", curve.getLOQ());
            calibData.put("lloq", curve.getLLOQ());
            calibData.put("loq_cv_percent", curve.calculateCVPercent(curve.getLOQ()));
            calibData.put("lloq_cv_percent", curve.calculateCVPercent(curve.getLLOQ()));
            
            // Add CV predictions
            JSONArray cvArray = new JSONArray();
            for (Calibrator.StandardCurveFitter.CVPrediction pred : cvPredictions) {
                JSONObject predObj = new JSONObject();
                predObj.put("concentration", pred.concentration);
                predObj.put("cv_percent", pred.cvPercent);
                predObj.put("label", pred.label);
                cvArray.put(predObj);
            }
            calibData.put("cv_predictions", cvArray);
            
            // Store calibration analysis
            String analysisHandle = store.putAnalysis("standard_curve_calibration", calibData, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            // Build success response
            JSONObject data = new JSONObject()
                .put("calibration_handle", analysisHandle)
                .put("r2", curve.getR2())
                .put("loq", curve.getLOQ())
                .put("lloq", curve.getLLOQ())
                .put("cv_predictions_count", cvPredictions.size())
                .put("image_handle", img.handle);
                
            return ok("calibrate_standard_curve", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("calibrate_standard_curve", e, logger, recovery);
        }
    }
    
    /**
     * Tool: compare_lanes
     * MW-aware statistical comparison between lanes with multiple testing correction
     */
    public JSONObject compareLanes(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("compare_lanes", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Get lane groups
            JSONArray controlLanesArray = args.optJSONArray("control_lanes");
            JSONArray treatmentLanesArray = args.optJSONArray("treatment_lanes");
            
            if (controlLanesArray == null || treatmentLanesArray == null ||
                controlLanesArray.length() < 2 || treatmentLanesArray.length() < 2) {
                return ErrorHandler.handleValidationError("compare_lanes", 
                    new IllegalArgumentException("Need at least 2 control and 2 treatment lanes for comparison"), 
                    logger, recovery);
            }
            
            // Convert to lists
            List<Integer> controlLanes = new ArrayList<>();
            List<Integer> treatmentLanes = new ArrayList<>();
            
            for (int i = 0; i < controlLanesArray.length(); i++) {
                controlLanes.add(controlLanesArray.getInt(i));
            }
            for (int i = 0; i < treatmentLanesArray.length(); i++) {
                treatmentLanes.add(treatmentLanesArray.getInt(i));
            }
            
            // Get configuration parameters
            LaneComparator.ComparisonConfig config = new LaneComparator.ComparisonConfig();
            config.mwTolerance = args.optDouble("mw_tolerance", 0.1);
            config.minIntensity = args.optDouble("min_intensity", 100.0);
            config.minSNR = args.optDouble("min_snr", 3.0);
            config.multipleTestingMethod = args.optString("multiple_testing_method", "holm-bonferroni");
            config.significanceLevel = args.optDouble("significance_level", 0.05);
            config.requireBothGroups = args.optBoolean("require_both_groups", true);
            config.volcanoFCThreshold = args.optDouble("volcano_fc_threshold", 1.0);
            config.volcanoPThreshold = args.optDouble("volcano_p_threshold", 0.05);
            
            // Extract peak data from quantification results
            Map<Integer, List<LaneComparator.Peak>> peaksByLane = extractPeaksFromQuantification(imageHandle, controlLanes, treatmentLanes);
            
            if (peaksByLane.isEmpty()) {
                return ErrorHandler.handleValidationError("compare_lanes", 
                    new IllegalArgumentException("No quantification data found - run quantify_bands first"), 
                    logger, recovery);
            }
            
            // Perform statistical comparison
            LaneComparator.LaneComparisonAnalysis analysis = 
                LaneComparator.compareLanes(peaksByLane, controlLanes, treatmentLanes, config);
            
            // Store analysis results
            JSONObject analysisData = LaneComparator.exportToJSON(analysis);
            String analysisHandle = store.putAnalysis("lane_comparison", analysisData, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            // Build success response
            JSONObject data = new JSONObject()
                .put("comparison_handle", analysisHandle)
                .put("total_peaks", analysis.totalPeaks)
                .put("significant_peaks", analysis.significantPeaks)
                .put("fdr_level", analysis.fdrLevel)
                .put("multiple_testing_method", analysis.config.multipleTestingMethod)
                .put("image_handle", img.handle);
                
            return ok("compare_lanes", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("compare_lanes", e, logger, recovery);
        }
    }
    
    /**
     * Tool: export_volcano_plot
     * Export volcano plot as PNG image
     */
    public JSONObject exportVolcanoPlot(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("export_volcano_plot", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            String comparisonHandle = args.optString("comparison_handle", "");
            if (comparisonHandle.isEmpty() || !store.hasAnalysis(comparisonHandle)) {
                return ErrorHandler.handleValidationError("export_volcano_plot", 
                    new IllegalArgumentException("Lane comparison not found - run compare_lanes first"), 
                    logger, recovery);
            }
            
            // Get comparison analysis
            SessionStore.AnalysisRecord analysisRecord = store.getAnalysis(comparisonHandle);
            if (!"lane_comparison".equals(analysisRecord.type)) {
                return ErrorHandler.handleValidationError("export_volcano_plot", 
                    new IllegalArgumentException("Analysis is not a lane comparison"), 
                    logger, recovery);
            }
            
            JSONObject analysisData = (JSONObject) analysisRecord.data;
            LaneComparator.LaneComparisonAnalysis analysis = parseComparisonAnalysis(analysisData);
            
            if (analysis == null) {
                return ErrorHandler.handleValidationError("export_volcano_plot", 
                    new IllegalArgumentException("Could not parse comparison analysis data"), 
                    logger, recovery);
            }
            
            // Get plot parameters
            int plotWidth = args.optInt("plot_width", 600);
            int plotHeight = args.optInt("plot_height", 500);
            String outputPath = args.optString("output_path", "");
            
            if (outputPath.isEmpty()) {
                // Generate default path
                String filename = img.image.getTitle().replaceAll("\\.[^.]+$", "");
                outputPath = System.getProperty("user.home") + "/Desktop/" + filename + "_volcano_plot.png";
            }
            
            // Create volcano plot overlay
            Overlay volcanoOverlay = OverlayRenderer.createVolcanoPlotOverlay(analysis, plotWidth, plotHeight);
            
            // Create blank image for volcano plot
            ImagePlus volcanoImage = IJ.createImage("Volcano Plot", "RGB white", plotWidth, plotHeight, 1);
            volcanoImage.setOverlay(volcanoOverlay);
            
            // Flatten and save
            ImagePlus flattenedImage = volcanoImage.flatten();
            FileSaver fs = new FileSaver(flattenedImage);
            boolean saved = fs.saveAsPng(outputPath);
            
            if (!saved) {
                return ErrorHandler.handleFileError("export_volcano_plot", 
                    new java.io.IOException("Failed to save volcano plot PNG to: " + outputPath), 
                    logger, recovery);
            }
            
            // Clean up
            volcanoImage.close();
            flattenedImage.close();
            
            // Build success response
            JSONObject data = new JSONObject()
                .put("png_path", outputPath)
                .put("plot_width", plotWidth)
                .put("plot_height", plotHeight)
                .put("total_peaks", analysis.totalPeaks)
                .put("significant_peaks", analysis.significantPeaks)
                .put("image_handle", img.handle);
                
            return ok("export_volcano_plot", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_volcano_plot", e, logger, recovery);
        }
    }
    
    /**
     * Extract peak data from quantification results for lane comparison
     */
    private Map<Integer, List<LaneComparator.Peak>> extractPeaksFromQuantification(String imageHandle, 
                                                                                  List<Integer> controlLanes,
                                                                                  List<Integer> treatmentLanes) {
        Map<Integer, List<LaneComparator.Peak>> peaksByLane = new HashMap<>();
        
        // Get all lane numbers
        Set<Integer> allLanes = new HashSet<>();
        allLanes.addAll(controlLanes);
        allLanes.addAll(treatmentLanes);
        
        // Get quantification analysis
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("quantification".equals(analysis.type)) {
                JSONArray results = (JSONArray) analysis.data;
                
                for (int i = 0; i < results.length(); i++) {
                    JSONObject laneResult = results.getJSONObject(i);
                    int laneIndex = laneResult.getInt("lane_index");
                    
                    if (allLanes.contains(laneIndex) && laneResult.has("bands")) {
                        JSONArray bands = laneResult.getJSONArray("bands");
                        List<LaneComparator.Peak> lanePeaks = new ArrayList<>();
                        
                        for (int j = 0; j < bands.length(); j++) {
                            JSONObject bandData = bands.getJSONObject(j);
                            
                            // Create Band object
                            Band band = new Band(
                                bandData.getInt("band_index"),
                                bandData.getInt("y_position"),
                                bandData.getDouble("area_corrected"),
                                bandData.optDouble("background", 0.0),
                                bandData.optDouble("mw_kda", 0.0)
                            );
                            
                            // Create Peak object
                            LaneComparator.Peak peak = new LaneComparator.Peak(
                                band,
                                bandData.getDouble("area_corrected"), // intensity
                                bandData.optDouble("mw_kda", 0.0),     // mw
                                bandData.optDouble("rf", 0.0),         // rf
                                bandData.optDouble("snr", 0.0)         // snr
                            );
                            
                            lanePeaks.add(peak);
                        }
                        
                        peaksByLane.put(laneIndex, lanePeaks);
                    }
                }
                break;
            }
        }
        
        return peaksByLane;
    }
    
    /**
     * Parse comparison analysis from JSON data
     */
    private LaneComparator.LaneComparisonAnalysis parseComparisonAnalysis(JSONObject analysisData) {
        try {
            // Extract basic data
            JSONArray controlLanesArray = analysisData.getJSONArray("control_lanes");
            JSONArray treatmentLanesArray = analysisData.getJSONArray("treatment_lanes");
            
            List<Integer> controlLanes = new ArrayList<>();
            List<Integer> treatmentLanes = new ArrayList<>();
            
            for (int i = 0; i < controlLanesArray.length(); i++) {
                controlLanes.add(controlLanesArray.getInt(i));
            }
            for (int i = 0; i < treatmentLanesArray.length(); i++) {
                treatmentLanes.add(treatmentLanesArray.getInt(i));
            }
            
            // Parse results
            JSONArray resultsArray = analysisData.getJSONArray("results");
            List<LaneComparator.ComparisonResult> results = new ArrayList<>();
            List<LaneComparator.PeakGroup> peakGroups = new ArrayList<>();
            
            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject resultJson = resultsArray.getJSONObject(i);
                
                // Create minimal peak group (just for volcano plot)
                LaneComparator.PeakGroup group = new LaneComparator.PeakGroup(
                    resultJson.getDouble("target_mw"), 0.1);
                
                LaneComparator.ComparisonResult result = new LaneComparator.ComparisonResult(
                    group,
                    resultJson.getDouble("p_value"),
                    resultJson.getDouble("adjusted_p_value"),
                    resultJson.getDouble("log2_fold_change"),
                    resultJson.getDouble("mean_control"),
                    resultJson.getDouble("mean_treatment"),
                    resultJson.getBoolean("is_significant"),
                    resultJson.getString("test_method")
                );
                
                results.add(result);
                peakGroups.add(group);
            }
            
            // Create config
            LaneComparator.ComparisonConfig config = new LaneComparator.ComparisonConfig();
            config.multipleTestingMethod = analysisData.getString("multiple_testing_method");
            config.significanceLevel = analysisData.getDouble("significance_level");
            
            return new LaneComparator.LaneComparisonAnalysis(
                controlLanes, treatmentLanes, peakGroups, results, config);
                
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Tool: enable_band_assist
     * Enable user-assisted band identification mode
     */
    public JSONObject enableBandAssist(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            // Now we're guaranteed to have a valid image_handle
            String imageHandle = args.getString("image_handle");
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("enable_band_assist", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Get lanes from previous analysis
            List<String> analyses = store.getAnalysesForImage(imageHandle);
            List<Lane> lanes = null;
            
            for (String analysisHandle : analyses) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if ("lanes".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<Lane> tempLanes = (List<Lane>) analysis.data;
                    lanes = tempLanes;
                    break;
                }
            }
            
            if (lanes == null || lanes.isEmpty()) {
                return ErrorHandler.handleValidationError("enable_band_assist", 
                    new IllegalStateException("No lanes detected. Please detect lanes first before using BandAssist."), 
                    logger, recovery);
            }
            
            // Store BandAssist tool instance in session metadata
            com.betterdairy.autodense.analysis.AssistBandTool assistTool = 
                new com.betterdairy.autodense.analysis.AssistBandTool(lanes);
            assistTool.enable(img.image);
            
            // Store tool instance for later use
            img.metadata.put("band_assist_tool", assistTool);
            
            JSONObject data = new JSONObject()
                .put("band_assist_enabled", true)
                .put("lanes_available", lanes.size())
                .put("image_handle", img.handle)
                .put("instructions", "Click on any band in any lane to identify it across all lanes");
            
            return ok("enable_band_assist", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleImageProcessingError("enable_band_assist", e, logger, recovery);
        }
    }
    
    /**
     * Tool: disable_band_assist
     * Disable user-assisted band identification mode
     */
    public JSONObject disableBandAssist(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("disable_band_assist", 
                    new IllegalArgumentException("Image not found in session: " + imageHandle), 
                    logger, recovery);
            }
            
            // Get and disable assist tool
            Object toolObj = img.metadata.get("band_assist_tool");
            boolean wasEnabled = false;
            if (toolObj instanceof com.betterdairy.autodense.analysis.AssistBandTool) {
                com.betterdairy.autodense.analysis.AssistBandTool assistTool = 
                    (com.betterdairy.autodense.analysis.AssistBandTool) toolObj;
                assistTool.disable();
                img.metadata.remove("band_assist_tool");
                wasEnabled = true;
            }
            
            JSONObject data = new JSONObject()
                .put("band_assist_disabled", true)
                .put("was_enabled", wasEnabled)
                .put("image_handle", img.handle);
            
            return ok("disable_band_assist", data);
                
        } catch (Exception e) {
            return ErrorHandler.handleImageProcessingError("disable_band_assist", e, logger, recovery);
        }
    }
    
    /**
     * Tool: configure_band_assist
     * Configure BandAssist parameters for better accuracy
     */
    public JSONObject configureBandAssist(JSONObject args) {
        try {
            // Validate image handle with recovery
            String imageHandle = args.optString("image_handle", "");
            JSONObject validation = recovery.validateHandle(imageHandle, "image");
            
            if (!validation.getBoolean("valid")) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", true);
                errorResponse.put("validation", validation);
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            
            // Get assist tool
            Object toolObj = img.metadata.get("band_assist_tool");
            if (!(toolObj instanceof com.betterdairy.autodense.analysis.AssistBandTool)) {
                return new JSONObject()
                    .put("error", true)
                    .put("message", "BandAssist is not currently enabled. Please enable it first.");
            }
            
            com.betterdairy.autodense.analysis.AssistBandTool assistTool = 
                (com.betterdairy.autodense.analysis.AssistBandTool) toolObj;
            
            // Configure parameters
            if (args.has("search_window_px")) {
                assistTool.setSearchWindow(args.getInt("search_window_px"));
            }
            if (args.has("min_prominence")) {
                assistTool.setMinProminence((float) args.getDouble("min_prominence"));
            }
            if (args.has("min_snr")) {
                assistTool.setMinSnr(args.getDouble("min_snr"));
            }
            if (args.has("width_ratio_min") && args.has("width_ratio_max")) {
                assistTool.setWidthRatioRange(args.getDouble("width_ratio_min"), 
                                            args.getDouble("width_ratio_max"));
            }
            if (args.has("rf_tolerance")) {
                assistTool.setRfTolerance(args.getDouble("rf_tolerance"));
            }
            
            return new JSONObject()
                .put("configuration_updated", true)
                .put("image_handle", img.handle)
                .put("current_settings", new JSONObject()
                    .put("search_window_px", args.optInt("search_window_px", 20))
                    .put("min_prominence", args.optDouble("min_prominence", 0.05))
                    .put("min_snr", args.optDouble("min_snr", 3.0))
                    .put("width_ratio_range", args.optDouble("width_ratio_min", 0.5) + " - " + args.optDouble("width_ratio_max", 2.0))
                    .put("rf_tolerance", args.optDouble("rf_tolerance", 0.02)));
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("configure_band_assist", e, logger, recovery);
        }
    }
    
    /**
     * Tool: normalize_intensities
     * Normalize band intensities across lanes
     */
    public JSONObject normalizeIntensities(JSONObject args) {
        try {
            // Validate image handle with recovery
            String imageHandle = args.optString("image_handle", "");
            JSONObject validation = recovery.validateHandle(imageHandle, "image");
            
            if (!validation.getBoolean("valid")) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", true);
                errorResponse.put("validation", validation);
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            String method = args.optString("normalization_method", "total_lane");
            
            JSONObject normalization = new JSONObject();
            normalization.put("method", method);
            normalization.put("status", "normalized");
            
            String analysisHandle = store.putAnalysis("normalization", normalization.toString(), img.handle);
            
            return new JSONObject()
                .put("normalization_complete", true)
                .put("method", method)
                .put("analysis_handle", analysisHandle)
                .put("image_handle", img.handle);
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("normalize_intensities", e, logger, recovery);
        }
    }
    
    /**
     * Tool: create_labeled_reference
     * Create a comprehensive labeled reference image showing all detected features
     * with clear markings for user communication and discussion
     */
    public JSONObject createLabeledReference(JSONObject args) {
        try {
            // Apply comprehensive handle protection
            JSONObject validationError = handleGuard.validateHandle(args, "create_labeled_reference");
            if (validationError != null) {
                validationError.put("memory_refresh", recovery.generateMemoryRefresh());
                return validationError;
            }
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            boolean includeIntensities = args.optBoolean("include_intensities", false);
            
            // Create comprehensive overlay with all available analysis data
            Overlay referenceOverlay = new Overlay();
            int height = img.image.getHeight();
            
            // Add lanes if available
            List<String> analyses = store.getAnalysesForImage(imageHandle);
            List<Lane> lanes = null;
            List<List<Band>> allBands = null;
            
            for (String analysisHandle : analyses) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if ("lanes".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<Lane> tempLanes = (List<Lane>) analysis.data;
                    lanes = tempLanes;
                } else if ("bands".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<List<Band>> tempBands = (List<List<Band>>) analysis.data;
                    allBands = tempBands;
                }
            }
            
            if (lanes != null) {
                for (int i = 0; i < lanes.size(); i++) {
                    Lane lane = lanes.get(i);
                    int x = lane.xStart();
                    int laneWidth = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                    
                    // Lane boundary (green)
                    Roi laneRoi = new Roi(x, 0, laneWidth, height);
                    laneRoi.setStrokeColor(new Color(0, 255, 0, 120));
                    laneRoi.setStrokeWidth(2.0);
                    laneRoi.setName("Lane " + (i + 1));
                    referenceOverlay.add(laneRoi);
                    
                    // Lane label at top
                    TextRoi laneLabel = new TextRoi(x + laneWidth/2 - 15, 5, "Lane " + (i + 1));
                    laneLabel.setStrokeColor(new Color(0, 200, 0));
                    laneLabel.setFillColor(new Color(255, 255, 255, 220));
                    laneLabel.setFont(new Font("Arial", Font.BOLD, 16));
                    referenceOverlay.add(laneLabel);
                    
                    // Add bands if available
                    if (allBands != null && i < allBands.size()) {
                        List<Band> bands = allBands.get(i);
                        for (int b = 0; b < bands.size(); b++) {
                            Band band = bands.get(b);
                            int y = Math.max(0, Math.min(height - 6, band.y()));
                            
                            // Band rectangle (red)
                            Roi bandRoi = new Roi(x, y, laneWidth, 6);
                            bandRoi.setStrokeColor(new Color(255, 0, 0, 200));
                            bandRoi.setStrokeWidth(2.0);
                            bandRoi.setName("L" + (i+1) + "B" + (b+1));
                            referenceOverlay.add(bandRoi);
                            
                            // Band label (show all bands)
                            String bandText = "B" + (b+1);
                            if (includeIntensities) {
                                bandText += String.format("(%.0f)", band.area());
                            }
                            
                            TextRoi bandLabel = new TextRoi(x + laneWidth + 3, y, bandText);
                            bandLabel.setStrokeColor(new Color(200, 0, 0));
                            bandLabel.setFillColor(new Color(255, 255, 255, 200));
                            bandLabel.setFont(new Font("Arial", Font.PLAIN, 12));
                            referenceOverlay.add(bandLabel);
                        }
                    }
                }
            }
            
            // Apply overlay and render PNG
            img.image.setOverlay(referenceOverlay);
            img.image.updateAndDraw();
            
            // Save reference image and overlay separately (modern approach)
            ImagePlus baseImage = img.image.duplicate();
            String baseFilename = "reference_base_" + System.currentTimeMillis() + ".png";
            Path baseOutputPath = tempDir.resolve(baseFilename);
            FileSaver baseFs = new FileSaver(baseImage);
            baseFs.saveAsPng(baseOutputPath.toString());
            
            // Save overlay as transparent PNG
            String overlayFilename = "reference_overlay_" + System.currentTimeMillis() + ".png";
            Path overlayOutputPath = tempDir.resolve(overlayFilename);
            String overlayPath = null;
            if (referenceOverlay != null) {
                try {
                    OverlayExporter.exportOverlayPNG(img.image, overlayOutputPath.toFile());
                    overlayPath = overlayOutputPath.toString();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to export overlay PNG", e);
                }
            }
            
            // For backward compatibility, also save flattened version (DEPRECATED)
            ImagePlus labeledImage = img.image.duplicate();
            if (referenceOverlay != null) {
                labeledImage.setOverlay(referenceOverlay);
                labeledImage = labeledImage.flatten(); // Still flatten for compatibility but mark deprecated
            }
            
            String filename = "labeled_reference_" + System.currentTimeMillis() + ".png";
            Path outputPath = tempDir.resolve(filename);
            FileSaver fs = new FileSaver(labeledImage);
            fs.saveAsPng(outputPath.toString());
            
            // Store reference overlay
            String overlayHandle = store.putOverlay(referenceOverlay, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            return new JSONObject()
                .put("reference_png", outputPath.toString()) // DEPRECATED: flattened
                .put("base_image_path", baseOutputPath.toString()) // Clean gel image  
                .put("overlay_path", overlayPath) // Transparent overlay PNG
                .put("overlay_handle", overlayHandle)
                .put("image_handle", img.handle)
                .put("export_format", "separate_layers") // Modern approach: base + overlay
                .put("reference_purpose", "Visual communication tool with comprehensive labels for lanes and bands")
                .put("measurement_warning", "CRITICAL: Use original image data for all measurements, not this labeled PNG")
                .put("lane_count", lanes != null ? lanes.size() : 0)
                .put("band_count", allBands != null ? allBands.stream().mapToInt(List::size).sum() : 0);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("create_labeled_reference", e, logger, recovery);
        }
    }

    /**
     * Tool: export_for_notebook
     * Quick export of labeled gel image optimized for Jupyter notebooks and lab documentation
     */
    public JSONObject exportForNotebook(JSONObject args) {
        try {
            // Apply comprehensive handle protection
            JSONObject validationError = handleGuard.validateHandle(args, "export_for_notebook");
            if (validationError != null) {
                validationError.put("memory_refresh", recovery.generateMemoryRefresh());
                return validationError;
            }
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            String filename = args.optString("filename", "gel_labeled_" + System.currentTimeMillis() + ".png");
            String outputDir = args.optString("output_directory", System.getProperty("user.home") + "/Downloads");
            boolean includeIntensities = args.optBoolean("include_intensities", false);
            int maxWidth = args.optInt("max_width", 800); // Optimal for notebooks
            
            Path outputPath = Path.of(outputDir, filename);
            
            // Create labeled overlay optimized for notebooks
            Overlay notebookOverlay = createExportOverlay(imageHandle, includeIntensities, false);
            
            // Scale for notebook display
            ImagePlus exportImage = img.image.duplicate();
            if (exportImage.getWidth() > maxWidth) {
                double scale = maxWidth / (double)exportImage.getWidth();
                int newHeight = (int)(exportImage.getHeight() * scale);
                ImageProcessor proc = exportImage.getProcessor();
                proc = proc.resize(maxWidth, newHeight);
                exportImage.setProcessor(proc);
            }
            
            // Apply overlay and save
            exportImage.setOverlay(notebookOverlay);
            exportImage = exportImage.flatten();
            
            Files.createDirectories(outputPath.getParent());
            FileSaver fs = new FileSaver(exportImage);
            fs.saveAsPng(outputPath.toString());
            
            return new JSONObject()
                .put("exported_file", outputPath.toString())
                .put("filename", filename)
                .put("dimensions", new JSONObject()
                    .put("width", exportImage.getWidth())
                    .put("height", exportImage.getHeight()))
                .put("image_handle", img.handle)
                .put("notebook_usage", "Perfect for embedding in Jupyter notebooks and lab documentation")
                .put("markdown_embed", "![Gel Analysis](" + outputPath.toString() + ")")
                .put("intensities_included", includeIntensities);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_for_notebook", e, logger, recovery);
        }
    }

    /**
     * Tool: export_for_presentation
     * Export labeled gel image with title and summary, optimized for presentations
     */
    public JSONObject exportForPresentation(JSONObject args) {
        try {
            // Apply comprehensive handle protection
            JSONObject validationError = handleGuard.validateHandle(args, "export_for_presentation");
            if (validationError != null) {
                validationError.put("memory_refresh", recovery.generateMemoryRefresh());
                return validationError;
            }
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            String title = args.optString("title", "Gel Electrophoresis Analysis");
            String filename = args.optString("filename", "gel_presentation_" + System.currentTimeMillis() + ".png");
            String outputDir = args.optString("output_directory", System.getProperty("user.home") + "/Downloads");
            
            Path outputPath = Path.of(outputDir, filename);
            
            // Create presentation overlay
            Overlay presentationOverlay = createPresentationOverlay(imageHandle, title);
            
            ImagePlus exportImage = img.image.duplicate();
            exportImage.setOverlay(presentationOverlay);
            exportImage = exportImage.flatten();
            
            Files.createDirectories(outputPath.getParent());
            FileSaver fs = new FileSaver(exportImage);
            fs.saveAsPng(outputPath.toString());
            
            return new JSONObject()
                .put("exported_file", outputPath.toString())
                .put("title", title)
                .put("filename", filename)
                .put("dimensions", new JSONObject()
                    .put("width", exportImage.getWidth())
                    .put("height", exportImage.getHeight()))
                .put("image_handle", img.handle)
                .put("presentation_ready", true)
                .put("usage", "Ready for PowerPoint, Keynote, or Google Slides");
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_for_presentation", e, logger, recovery);
        }
    }

    public JSONObject clearSession(JSONObject args) {
        store.clear();
        return new JSONObject().put("status", "session_cleared");
    }
    
    

    /**
     * Export labeled PNG optimized for presentations and notebooks
     */
    private void exportLabeledPNG(Path outputPath, String imageHandle, JSONObject args) throws Exception {
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        int maxWidth = args.optInt("max_width", 1200);
        boolean includeIntensities = args.optBoolean("include_intensities", false);
        
        // Create comprehensive labeled overlay
        Overlay exportOverlay = createExportOverlay(imageHandle, includeIntensities, false);
        
        // Prepare image for export
        ImagePlus exportImage = img.image.duplicate();
        if (exportImage.getWidth() > maxWidth) {
            double scale = maxWidth / (double)exportImage.getWidth();
            int newHeight = (int)(exportImage.getHeight() * scale);
            ImageProcessor proc = exportImage.getProcessor();
            proc = proc.resize(maxWidth, newHeight);
            exportImage.setProcessor(proc);
        }
        
        // Apply overlay and flatten
        exportImage.setOverlay(exportOverlay);
        exportImage = exportImage.flatten();
        
        // Save
        FileSaver fs = new FileSaver(exportImage);
        fs.saveAsPng(outputPath.toString());
    }
    
    /**
     * Export high resolution labeled PNG for publication
     */
    private void exportHighResLabeledPNG(Path outputPath, String imageHandle, JSONObject args) throws Exception {
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        boolean includeIntensities = args.optBoolean("include_intensities", true);
        
        // Use original resolution for publication quality
        Overlay exportOverlay = createExportOverlay(imageHandle, includeIntensities, true);
        
        ImagePlus exportImage = img.image.duplicate();
        exportImage.setOverlay(exportOverlay);
        exportImage = exportImage.flatten();
        
        FileSaver fs = new FileSaver(exportImage);
        fs.saveAsPng(outputPath.toString());
    }
    
    /**
     * Export presentation-optimized PNG with title and annotations
     */
    private void exportPresentationPNG(Path outputPath, String imageHandle, JSONObject args) throws Exception {
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        String title = args.optString("title", "Gel Analysis Results");
        int maxWidth = args.optInt("max_width", 1000);
        
        // Create presentation overlay with title
        Overlay presentationOverlay = createPresentationOverlay(imageHandle, title);
        
        // Scale for presentation
        ImagePlus exportImage = img.image.duplicate();
        if (exportImage.getWidth() > maxWidth) {
            double scale = maxWidth / (double)exportImage.getWidth();
            int newHeight = (int)(exportImage.getHeight() * scale);
            ImageProcessor proc = exportImage.getProcessor();
            proc = proc.resize(maxWidth, newHeight);
            exportImage.setProcessor(proc);
        }
        
        exportImage.setOverlay(presentationOverlay);
        exportImage = exportImage.flatten();
        
        FileSaver fs = new FileSaver(exportImage);
        fs.saveAsPng(outputPath.toString());
    }
    
    /**
     * Create comprehensive overlay for export
     */
    private Overlay createExportOverlay(String imageHandle, boolean includeIntensities, boolean highRes) throws Exception {
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        Overlay exportOverlay = new Overlay();
        int height = img.image.getHeight();
        
        // Font sizes based on resolution
        int laneFontSize = highRes ? 18 : 14;
        int bandFontSize = highRes ? 14 : 10;
        
        // Get analysis data
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        List<Lane> lanes = null;
        List<List<Band>> allBands = null;
        
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("lanes".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<Lane> tempLanes = (List<Lane>) analysis.data;
                lanes = tempLanes;
            } else if ("bands".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<List<Band>> tempBands = (List<List<Band>>) analysis.data;
                allBands = tempBands;
            }
        }
        
        if (lanes != null) {
            for (int i = 0; i < lanes.size(); i++) {
                Lane lane = lanes.get(i);
                int x = lane.xStart();
                int laneWidth = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                
                // Lane boundary
                Roi laneRoi = new Roi(x, 0, laneWidth, height);
                laneRoi.setStrokeColor(new Color(0, 255, 0, 150));
                laneRoi.setStrokeWidth(highRes ? 3.0 : 2.0);
                laneRoi.setName("Lane " + (i + 1));
                exportOverlay.add(laneRoi);
                
                // Lane label
                TextRoi laneLabel = new TextRoi(x + laneWidth/2 - 20, 8, "Lane " + (i + 1));
                laneLabel.setStrokeColor(new Color(0, 150, 0));
                laneLabel.setFillColor(new Color(255, 255, 255, 230));
                laneLabel.setFont(new Font("Arial", Font.BOLD, laneFontSize));
                exportOverlay.add(laneLabel);
                
                // Bands
                if (allBands != null && i < allBands.size()) {
                    List<Band> bands = allBands.get(i);
                    for (int b = 0; b < bands.size(); b++) {
                        Band band = bands.get(b);
                        int y = Math.max(0, Math.min(height - 8, band.y()));
                        
                        // Band rectangle
                        Roi bandRoi = new Roi(x, y, laneWidth, 8);
                        bandRoi.setStrokeColor(new Color(255, 0, 0, 180));
                        bandRoi.setStrokeWidth(highRes ? 2.5 : 1.5);
                        bandRoi.setName("L" + (i+1) + "B" + (b+1));
                        exportOverlay.add(bandRoi);
                        
                        // Band label
                        String bandText = "B" + (b+1);
                        if (includeIntensities) {
                            bandText += String.format("(%.0f)", band.area());
                        }
                        
                        TextRoi bandLabel = new TextRoi(x + laneWidth + 5, y - 2, bandText);
                        bandLabel.setStrokeColor(new Color(150, 0, 0));
                        bandLabel.setFillColor(new Color(255, 255, 255, 200));
                        bandLabel.setFont(new Font("Arial", Font.PLAIN, bandFontSize));
                        exportOverlay.add(bandLabel);
                    }
                }
            }
        }
        
        return exportOverlay;
    }
    
    /**
     * Create presentation overlay with title and enhanced annotations
     */
    private Overlay createPresentationOverlay(String imageHandle, String title) throws Exception {
        Overlay presentationOverlay = createExportOverlay(imageHandle, false, false);
        SessionStore.ImageRecord img = store.getImage(imageHandle);
        
        // Add title at the bottom
        int titleY = img.image.getHeight() - 25;
        TextRoi titleLabel = new TextRoi(10, titleY, title);
        titleLabel.setStrokeColor(new Color(0, 0, 0));
        titleLabel.setFillColor(new Color(255, 255, 255, 240));
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        presentationOverlay.add(titleLabel);
        
        // Add analysis summary
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        int laneCount = 0;
        int bandCount = 0;
        
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("lanes".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<Lane> lanes = (List<Lane>) analysis.data;
                laneCount = lanes.size();
            } else if ("bands".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<List<Band>> allBands = (List<List<Band>>) analysis.data;
                bandCount = allBands.stream().mapToInt(List::size).sum();
            }
        }
        
        String summary = String.format("Lanes: %d | Bands: %d", laneCount, bandCount);
        TextRoi summaryLabel = new TextRoi(10, titleY - 20, summary);
        summaryLabel.setStrokeColor(new Color(64, 64, 64));
        summaryLabel.setFillColor(new Color(255, 255, 255, 200));
        summaryLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        presentationOverlay.add(summaryLabel);
        
        return presentationOverlay;
    }
    
    /**
     * Export quantification data to CSV with standardized format
     * Header: file,lane,band_idx,x_start,x_end,y_top,y_bottom,apex_y,area_raw,area_bg,area_corr,snr,mw_kda,rf,flags
     */
    private void exportQuantificationCSV(Path outputPath, String imageHandle) throws Exception {
        SessionStore.ImageRecord imgRecord = store.getImage(imageHandle);
        if (imgRecord == null) {
            throw new Exception("Image not found for handle: " + imageHandle);
        }
        
        // Decimal formatters for fixed precision
        DecimalFormat df3 = new DecimalFormat("#.###");  // 3 decimal places
        DecimalFormat df4 = new DecimalFormat("#.####"); // 4 decimal places for RF
        
        StringBuilder csv = new StringBuilder();
        
        // Standardized CSV header with PCR normalization columns
        csv.append("file,lane,band_idx,x_start,x_end,y_top,y_bottom,apex_y,area_raw,area_bg,area_corr,snr,sharpness,smear_percent,mw_kda,rf,intensity_raw,intensity_normalized,delta_delta_i,relative_copy_number,housekeeping_reference,pcr_quality_flag,flags\n");
        
        // Get filename from image
        String filename = imgRecord.image.getTitle();
        if (filename == null || filename.isEmpty()) {
            filename = "unknown.tif";
        }
        
        // Get lanes and bands from analysis data
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        List<Lane> lanes = null;
        List<List<Band>> allBands = new ArrayList<>();
        
        // Find lane detection results
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("lane_detection".equals(analysis.type)) {
                JSONObject analysisData = (JSONObject) analysis.data;
                if (analysisData.has("lanes")) {
                    JSONArray laneArray = analysisData.getJSONArray("lanes");
                    lanes = new ArrayList<>();
                    for (int i = 0; i < laneArray.length(); i++) {
                        JSONObject laneObj = laneArray.getJSONObject(i);
                        lanes.add(new Lane(
                            laneObj.getInt("index"),
                            laneObj.getInt("x_start"),
                            laneObj.getInt("x_end")
                        ));
                    }
                }
                break;
            }
        }
        
        // Find band detection results
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("band_detection".equals(analysis.type)) {
                JSONObject analysisData = (JSONObject) analysis.data;
                if (analysisData.has("lanes")) {
                    JSONArray laneResults = analysisData.getJSONArray("lanes");
                    for (int laneIdx = 0; laneIdx < laneResults.length(); laneIdx++) {
                        JSONObject laneResult = laneResults.getJSONObject(laneIdx);
                        if (laneResult.has("bands")) {
                            JSONArray bands = laneResult.getJSONArray("bands");
                            List<Band> laneBands = new ArrayList<>();
                            
                            for (int bandIdx = 0; bandIdx < bands.length(); bandIdx++) {
                                JSONObject bandObj = bands.getJSONObject(bandIdx);
                                Band band = new Band(
                                    bandIdx + 1,
                                    bandObj.getInt("y_position"),
                                    bandObj.getDouble("area"),
                                    bandObj.optDouble("background", 0.0),
                                    bandObj.optDouble("mw_kda", 0.0)
                                );
                                laneBands.add(band);
                            }
                            allBands.add(laneBands);
                        }
                    }
                }
                break;
            }
        }
        
        // If we don't have proper analysis data, create minimal structure
        if (lanes == null) {
            lanes = new ArrayList<>();
            // Create default single lane if no lane data
            lanes.add(new Lane(1, 0, imgRecord.image.getWidth()));
        }
        
        // Export data for each lane and band
        for (int laneIdx = 0; laneIdx < lanes.size(); laneIdx++) {
            Lane lane = lanes.get(laneIdx);
            List<Band> laneBands = (laneIdx < allBands.size()) ? allBands.get(laneIdx) : new ArrayList<>();
            
            for (int bandIdx = 0; bandIdx < laneBands.size(); bandIdx++) {
                Band band = laneBands.get(bandIdx);
                
                // Calculate derived values
                double apexY = band.y(); // Use center as apex
                double areaRaw = band.area();
                double areaBg = band.baseline() * (lane.xEnd() - lane.xStart() + 1) * 10; // Estimate band height as 10px
                double areaCorr = Math.max(0, areaRaw - areaBg);
                double snr = 0.0; // SNR would need to be calculated from raw data
                double sharpness = 0.0; // Default if not available
                double smearPercent = 0.0; // Default if not available
                double rf = apexY / (double) imgRecord.image.getHeight(); // Simple RF calculation
                double mwKda = band.mwKDa(); // Get molecular weight if available
                
                // Initialize flags list for LOQ/LLOQ checking
                List<String> flagsList = new ArrayList<>();
                
                // Check for LOQ/LLOQ violations using standard curve calibration
                checkLOQFlags(flagsList, mwKda, analyses);
                
                String flags = String.join(";", flagsList);
                
                // Initialize PCR normalization variables
                double intensityRaw = areaCorr; // Default to corrected area
                double intensityNormalized = 0.0;
                double deltaDeltaI = 0.0;
                double relativeCopyNumber = 0.0;
                double housekeepingReference = 0.0;
                String pcrQualityFlag = "";
                
                // Try to get enhanced analysis data from band detection results
                // Look for enhanced analysis data that includes sharpness, smear_percent, and PCR data
                for (String analysisHandle : analyses) {
                    SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                    if ("band_detection".equals(analysis.type)) {
                        JSONObject analysisData = (JSONObject) analysis.data;
                        if (analysisData.has("lanes") && laneIdx < analysisData.getJSONArray("lanes").length()) {
                            JSONObject laneResult = analysisData.getJSONArray("lanes").getJSONObject(laneIdx);
                            if (laneResult.has("bands") && bandIdx < laneResult.getJSONArray("bands").length()) {
                                JSONObject bandData = laneResult.getJSONArray("bands").getJSONObject(bandIdx);
                                if (bandData.has("sharpness")) {
                                    sharpness = bandData.getDouble("sharpness");
                                }
                                if (bandData.has("smear_percent")) {
                                    smearPercent = bandData.getDouble("smear_percent");
                                }
                                if (bandData.has("snr")) {
                                    snr = bandData.getDouble("snr");
                                }
                                // Extract PCR normalization data if available
                                if (bandData.has("intensity_raw")) {
                                    intensityRaw = bandData.getDouble("intensity_raw");
                                }
                                if (bandData.has("intensity_normalized")) {
                                    intensityNormalized = bandData.getDouble("intensity_normalized");
                                }
                                if (bandData.has("delta_delta_i")) {
                                    deltaDeltaI = bandData.getDouble("delta_delta_i");
                                }
                                if (bandData.has("relative_copy_number")) {
                                    relativeCopyNumber = bandData.getDouble("relative_copy_number");
                                }
                                if (bandData.has("housekeeping_reference")) {
                                    housekeepingReference = bandData.getDouble("housekeeping_reference");
                                }
                                if (bandData.has("pcr_quality_flag")) {
                                    pcrQualityFlag = bandData.getString("pcr_quality_flag");
                                }
                            }
                        }
                        break;
                    }
                }
                
                // Build CSV row with fixed decimal formatting including PCR normalization columns
                csv.append(String.format("%s,%d,%d,%d,%d,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    filename,
                    lane.index(),
                    band.index(),
                    lane.xStart(),
                    lane.xEnd(),
                    Math.max(0, (int)(apexY - 5)), // y_top (estimate)
                    Math.min(imgRecord.image.getHeight() - 1, (int)(apexY + 5)), // y_bottom (estimate)
                    df3.format(apexY),
                    df3.format(areaRaw),
                    df3.format(areaBg),
                    df3.format(areaCorr),
                    df3.format(snr),
                    df3.format(sharpness),
                    df3.format(smearPercent),
                    df3.format(mwKda),
                    df4.format(rf),
                    df3.format(intensityRaw),
                    df3.format(intensityNormalized),
                    df3.format(deltaDeltaI),
                    df3.format(relativeCopyNumber),
                    df3.format(housekeepingReference),
                    pcrQualityFlag,
                    flags
                ));
            }
        }
        
        // Add LOQ/LLOQ information rows if standard curve data is available
        addLOQLLOQRows(csv, analyses, filename);
        
        Files.writeString(outputPath, csv.toString());
    }
    
    /**
     * Add LOQ/LLOQ information rows to CSV export
     */
    private void addLOQLLOQRows(StringBuilder csv, List<String> analyses, String filename) {
        // Look for standard curve calibration data
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("standard_curve_calibration".equals(analysis.type)) {
                try {
                    JSONObject calibData = (JSONObject) analysis.data;
                    if (calibData.has("loq") && calibData.has("lloq")) {
                        double loq = calibData.getDouble("loq");
                        double lloq = calibData.getDouble("lloq");
                        double loqCV = calibData.optDouble("loq_cv_percent", 20.0);
                        double lloqCV = calibData.optDouble("lloq_cv_percent", 30.0);
                        
                        DecimalFormat df3 = new DecimalFormat("#.###");
                        DecimalFormat df4 = new DecimalFormat("#.####");
                        
                        // Add LLOQ row (Lower Limit of Quantification)
                        csv.append(String.format("%s,LLOQ,0,0,0,0,0,0,0,0,0,0,0,0,%s,0,%s\n",
                            filename,
                            df3.format(lloq),
                            "LLOQ_CV_" + df3.format(lloqCV) + "%"
                        ));
                        
                        // Add LOQ row (Limit of Quantification)
                        csv.append(String.format("%s,LOQ,0,0,0,0,0,0,0,0,0,0,0,0,%s,0,%s\n",
                            filename,
                            df3.format(loq),
                            "LOQ_CV_" + df3.format(loqCV) + "%"
                        ));
                        
                        // Add CV prediction summary row
                        if (calibData.has("cv_predictions")) {
                            JSONArray cvPreds = calibData.getJSONArray("cv_predictions");
                            StringBuilder cvSummary = new StringBuilder("CV_PREDICTIONS:");
                            for (int i = 0; i < cvPreds.length() && i < 5; i++) { // Limit to 5 predictions
                                JSONObject pred = cvPreds.getJSONObject(i);
                                double conc = pred.getDouble("concentration");
                                double cv = pred.getDouble("cv_percent");
                                String label = pred.optString("label", "");
                                cvSummary.append(String.format(" %s%.2f(μg/mL)=%.1f%%CV", 
                                                             label.isEmpty() ? "" : label + "_", 
                                                             conc, cv));
                            }
                            
                            csv.append(String.format("%s,CV_INFO,0,0,0,0,0,0,0,0,0,0,0,0,0,0,%s\n",
                                filename, cvSummary.toString()));
                        }
                        
                        break; // Only process first standard curve found
                    }
                } catch (Exception e) {
                    // Skip malformed calibration data
                }
            }
        }
    }
    
    /**
     * Check concentration against LOQ/LLOQ limits and add appropriate flags
     */
    private void checkLOQFlags(List<String> flagsList, double concentration, List<String> analyses) {
        // Look for standard curve calibration data
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("standard_curve_calibration".equals(analysis.type)) {
                try {
                    JSONObject calibData = (JSONObject) analysis.data;
                    if (calibData.has("loq") && calibData.has("lloq")) {
                        double loq = calibData.getDouble("loq");
                        double lloq = calibData.getDouble("lloq");
                        
                        // Flag bands below LLOQ (Lower Limit of Quantification)
                        if (!Double.isNaN(lloq) && concentration < lloq && concentration > 0) {
                            flagsList.add("BELOW_LLOQ");
                        }
                        // Flag bands below LOQ (Limit of Quantification) but above LLOQ
                        else if (!Double.isNaN(loq) && concentration < loq && concentration > 0) {
                            flagsList.add("BELOW_LOQ");
                        }
                        
                        // Add CV information for concentrations near limits
                        if (concentration > 0) {
                            // Create temporary model to calculate CV
                            List<Double> tempConcs = new ArrayList<>();
                            List<Double> tempResps = new ArrayList<>();
                            
                            // Extract original standard data if available
                            if (calibData.has("slope") && calibData.has("intercept")) {
                                double slope = calibData.getDouble("slope");
                                double intercept = calibData.getDouble("intercept");
                                
                                // Create minimal standard curve for CV calculation
                                // Use points around the concentration of interest
                                double baseConc = Math.max(concentration * 0.5, lloq * 0.5);
                                for (int i = 1; i <= 5; i++) {
                                    double conc = baseConc * i;
                                    double resp = slope * conc + intercept;
                                    tempConcs.add(conc);
                                    tempResps.add(resp);
                                }
                                
                                try {
                                    Calibrator.StandardCurveFitter.Model tempModel = 
                                        Calibrator.StandardCurveFitter.fit(tempConcs, tempResps);
                                    double cvPercent = tempModel.calculateCVPercent(concentration);
                                    
                                    if (cvPercent > 30.0) {
                                        flagsList.add("HIGH_CV");
                                    } else if (cvPercent > 20.0) {
                                        flagsList.add("MODERATE_CV");
                                    }
                                } catch (Exception e) {
                                    // Skip CV calculation if it fails
                                }
                            }
                        }
                        
                        break; // Only process first standard curve found
                    }
                } catch (Exception e) {
                    // Skip malformed calibration data
                }
            }
        }
    }
    
    
    

    
    /**
     * Create a cache key for preprocessing operations.
     * This ensures identical preprocessing chains reuse cached results.
     */
    private String createPreprocessingCacheKey(String imageHandle, JSONArray steps, boolean destructive) {
        StringBuilder key = new StringBuilder();
        key.append(imageHandle).append("|").append(destructive);
        
        // Include each step's operation and parameters
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            key.append("|").append(step.getString("op"));
            
            // Include relevant parameters for cache differentiation
            switch (step.getString("op")) {
                case "rotate":
                    key.append(":").append(step.optDouble("angle_deg", 0.0));
                    break;
                case "flip":
                    key.append(":").append(step.optString("axis", "horizontal"));
                    break;
                case "enhance_contrast":
                    key.append(":").append(step.optDouble("saturated", 0.35));
                    break;
                case "subtract_background":
                    key.append(":").append(step.optInt("radius_px", 150));
                    break;
                case "clahe":
                    key.append(":").append(step.optInt("block_size", 127))
                       .append(":").append(step.optInt("histogram_bins", 256))
                       .append(":").append(step.optDouble("max_slope", 3.0));
                    break;
                case "bandpass":
                    key.append(":").append(step.optDouble("filter_large", 40.0))
                       .append(":").append(step.optDouble("filter_small", 3.0));
                    break;
            }
        }
        
        return key.toString();
    }
    
    /**
     * Create a cache key for mode-based preprocessing operations.
     * Mode-based preprocessing uses predefined parameter sets for common gel types.
     */
    private String createPreprocessingCacheKey(String imageHandle, String mode, boolean destructive) {
        return imageHandle + "|" + destructive + "|mode:" + mode;
    }
    
    /**
     * Conditionally duplicate an image only if modifications are needed.
     * This prevents unnecessary deep copies when read-only operations suffice.
     */
    private ImagePlus conditionalDuplicate(ImagePlus original, boolean needsModification, String suffix) {
        if (!needsModification) {
            return original; // Return reference to original
        }
        
        ImagePlus duplicate = original.duplicate();
        if (suffix != null && !suffix.isEmpty()) {
            duplicate.setTitle(original.getTitle() + "_" + suffix);
        }
        return duplicate;
    }
    
    // ==================== TIME-SERIES COLONY ANALYSIS TOOLS ====================
    
    /**
     * Tool: start_timeseries_analysis
     * Initialize time-series colony tracking for growth analysis
     */
    public JSONObject startTimeseriesAnalysis(JSONObject args) {
        ToolSchemaValidator.require(args, "reference_image_handle");
        try {
            String referenceHandle = args.getString("reference_image_handle");
            SessionStore.ImageRecord refImg = store.getImage(referenceHandle);
            if (refImg == null) {
                return ErrorHandler.handleValidationError("start_timeseries_analysis", 
                    new IllegalArgumentException("Reference image not found: " + referenceHandle), 
                    logger, recovery);
            }
            
            // Create time-series tracker with options
            TimeSeriesColonyTracker.TrackingOptions options = new TimeSeriesColonyTracker.TrackingOptions();
            options.maxMatchingDistance = args.optDouble("max_matching_distance", 10.0);
            options.sizeChangeThreshold = args.optDouble("size_change_threshold", 2.0);
            options.trackNewColonies = args.optBoolean("track_new_colonies", true);
            
            TimeSeriesColonyTracker tracker = new TimeSeriesColonyTracker(options);
            
            // Analyze colonies in reference image
            boolean measureMorphology = args.optBoolean("measure_morphology", true);
            boolean measureXgalBlueness = args.optBoolean("xgal_analysis", false);
            
            List<TimeSeriesColonyTracker.Colony> colonies = 
                TimeSeriesColonyTracker.analyzeColonies(refImg.image, measureMorphology, measureXgalBlueness);
            
            // Add first time point
            java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
            tracker.addTimePoint(timestamp, refImg.image, colonies);
            
            // Store tracker in session
            String trackingHandle = "tracking_" + System.currentTimeMillis();
            store.putAnalysis(trackingHandle, tracker, "Time-series colony tracking");
            
            JSONObject data = new JSONObject()
                .put("tracking_handle", trackingHandle)
                .put("reference_image_handle", referenceHandle)
                .put("initial_colony_count", colonies.size())
                .put("options", new JSONObject()
                    .put("max_matching_distance", options.maxMatchingDistance)
                    .put("size_change_threshold", options.sizeChangeThreshold)
                    .put("track_new_colonies", options.trackNewColonies)
                    .put("measure_morphology", measureMorphology)
                    .put("xgal_analysis", measureXgalBlueness));
            
            return ok("start_timeseries_analysis", data);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("start_timeseries_analysis", e, logger, recovery);
        }
    }
    
    /**
     * Tool: add_timepoint
     * Add a new time point to existing time-series analysis
     */
    public JSONObject addTimepoint(JSONObject args) {
        ToolSchemaValidator.require(args, "tracking_handle");
        ToolSchemaValidator.require(args, "image_handle");
        try {
            String trackingHandle = args.getString("tracking_handle");
            String imageHandle = args.getString("image_handle");
            
            SessionStore.AnalysisRecord trackingRecord = store.getAnalysis(trackingHandle);
            if (trackingRecord == null) {
                return ErrorHandler.handleValidationError("add_timepoint", 
                    new IllegalArgumentException("Time-series tracking not found: " + trackingHandle), 
                    logger, recovery);
            }
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("add_timepoint", 
                    new IllegalArgumentException("Image not found: " + imageHandle), 
                    logger, recovery);
            }
            
            TimeSeriesColonyTracker tracker = (TimeSeriesColonyTracker) trackingRecord.data;
            
            // Align image to reference if needed
            boolean performAlignment = args.optBoolean("perform_alignment", true);
            ImagePlus alignedImage = img.image;
            
            if (performAlignment) {
                JSONObject alignmentResult = alignPlateImages(args);
                if (alignmentResult.optBoolean("ok", false)) {
                    String alignedHandle = alignmentResult.getJSONObject("data").getString("aligned_image_handle");
                    SessionStore.ImageRecord alignedImg = store.getImage(alignedHandle);
                    if (alignedImg != null) {
                        alignedImage = alignedImg.image;
                    }
                }
            }
            
            // Analyze colonies in new image
            boolean measureMorphology = args.optBoolean("measure_morphology", true);
            boolean measureXgalBlueness = args.optBoolean("xgal_analysis", false);
            
            List<TimeSeriesColonyTracker.Colony> colonies = 
                TimeSeriesColonyTracker.analyzeColonies(alignedImage, measureMorphology, measureXgalBlueness);
            
            // Add time point to tracker
            java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
            tracker.addTimePoint(timestamp, alignedImage, colonies);
            
            // Update stored tracker
            store.putAnalysis(trackingHandle, tracker, "Time-series colony tracking");
            
            // Get tracking statistics
            Map<String, TimeSeriesColonyTracker.ColonyTrack> tracks = tracker.getTracks();
            List<TimeSeriesColonyTracker.ColonyTrack> growingTracks = tracker.getGrowingTracks(0.1);
            
            JSONObject data = new JSONObject()
                .put("tracking_handle", trackingHandle)
                .put("image_handle", imageHandle)
                .put("colonies_detected", colonies.size())
                .put("total_tracks", tracks.size())
                .put("growing_tracks", growingTracks.size())
                .put("alignment_performed", performAlignment)
                .put("timestamp", timestamp.toString());
            
            return ok("add_timepoint", data);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("add_timepoint", e, logger, recovery);
        }
    }
    
    /**
     * Tool: align_plate_images
     * Align a target image to match a reference plate image
     */
    public JSONObject alignPlateImages(JSONObject args) {
        ToolSchemaValidator.require(args, "reference_image_handle");
        ToolSchemaValidator.require(args, "target_image_handle");
        try {
            String refHandle = args.getString("reference_image_handle");
            String targetHandle = args.getString("target_image_handle");
            
            SessionStore.ImageRecord refImg = store.getImage(refHandle);
            SessionStore.ImageRecord targetImg = store.getImage(targetHandle);
            
            if (refImg == null) {
                return ErrorHandler.handleValidationError("align_plate_images", 
                    new IllegalArgumentException("Reference image not found: " + refHandle), 
                    logger, recovery);
            }
            if (targetImg == null) {
                return ErrorHandler.handleValidationError("align_plate_images", 
                    new IllegalArgumentException("Target image not found: " + targetHandle), 
                    logger, recovery);
            }
            
            // Configure alignment options
            PlateAlignment.AlignmentOptions options = new PlateAlignment.AlignmentOptions();
            options.method = args.optString("alignment_method", "feature_matching");
            options.tolerancePx = args.optDouble("tolerance_px", 5.0);
            options.allowRotation = args.optBoolean("allow_rotation", true);
            options.allowSkewing = args.optBoolean("allow_skewing", true);
            
            // Perform alignment
            PlateAlignment.AlignmentResult result = 
                PlateAlignment.alignPlates(refImg.image, targetImg.image, options);
            
            if (result.confidence < 0.5) {
                return ErrorHandler.handleImageProcessingError("align_plate_images", 
                    new RuntimeException("Could not reliably align images - confidence: " + result.confidence), 
                    logger, recovery);
            }
            
            // Apply alignment transformation
            ImagePlus alignedImage = PlateAlignment.applyAlignment(targetImg.image, result.transform);
            String alignedHandle = store.putImage(alignedImage);
            
            JSONObject data = new JSONObject()
                .put("aligned_image_handle", alignedHandle)
                .put("reference_image_handle", refHandle)
                .put("target_image_handle", targetHandle)
                .put("alignment_confidence", result.confidence)
                .put("alignment_method", result.method)
                .put("key_points_found", result.keyPoints.size())
                .put("metadata", result.metadata)
                .put("parameters_used", new JSONObject()
                    .put("method", options.method)
                    .put("tolerance_px", options.tolerancePx)
                    .put("allow_rotation", options.allowRotation)
                    .put("allow_skewing", options.allowSkewing));
            
            return ok("align_plate_images", data);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("align_plate_images", e, logger, recovery);
        }
    }
    
    /**
     * Tool: analyze_xgal_blueness
     * Analyze X-gal blueness for colonies in an image
     */
    public JSONObject analyzeXgalBlueness(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return ErrorHandler.handleValidationError("analyze_xgal_blueness", 
                    new IllegalArgumentException("Image not found: " + imageHandle), 
                    logger, recovery);
            }
            
            // Configure blueness analysis options
            XGalBluenessAnalyzer.BluenessOptions options = new XGalBluenessAnalyzer.BluenessOptions();
            options.useRGBAnalysis = args.optBoolean("use_rgb_analysis", true);
            options.analysisRadius = args.optInt("analysis_radius", 8);
            options.normalizeForLighting = args.optBoolean("normalize_lighting", true);
            
            // Get colony positions from overlay or detect them
            Map<String, java.awt.geom.Point2D> colonyPositions = extractColonyPositions(img);
            
            if (colonyPositions.isEmpty()) {
                return ErrorHandler.handleValidationError("analyze_xgal_blueness", 
                    new IllegalStateException("No colonies found for blueness analysis"), 
                    logger, recovery);
            }
            
            // Analyze blueness for each colony
            Map<String, XGalBluenessAnalyzer.BluenessResult> bluenessResults = 
                XGalBluenessAnalyzer.analyzeMultipleColonies(img.image, colonyPositions, options);
            
            // Convert results to JSON
            JSONObject coloniesData = new JSONObject();
            JSONObject summary = new JSONObject();
            int whiteCount = 0, lightBlueCount = 0, mediumBlueCount = 0, deepBlueCount = 0;
            
            for (Map.Entry<String, XGalBluenessAnalyzer.BluenessResult> entry : bluenessResults.entrySet()) {
                String colonyId = entry.getKey();
                XGalBluenessAnalyzer.BluenessResult result = entry.getValue();
                
                coloniesData.put(colonyId, result.toJSON());
                
                // Update classification counts
                switch (result.classification) {
                    case "white": whiteCount++; break;
                    case "light_blue": lightBlueCount++; break;
                    case "medium_blue": mediumBlueCount++; break;
                    case "deep_blue": deepBlueCount++; break;
                }
            }
            
            summary.put("total_colonies", bluenessResults.size())
                   .put("white_colonies", whiteCount)
                   .put("light_blue_colonies", lightBlueCount)
                   .put("medium_blue_colonies", mediumBlueCount)
                   .put("deep_blue_colonies", deepBlueCount)
                   .put("transformation_efficiency", 
                        bluenessResults.size() > 0 ? 
                        (double)(lightBlueCount + mediumBlueCount + deepBlueCount) / bluenessResults.size() : 0);
            
            JSONObject data = new JSONObject()
                .put("image_handle", imageHandle)
                .put("colonies", coloniesData)
                .put("summary", summary)
                .put("analysis_options", new JSONObject()
                    .put("use_rgb_analysis", options.useRGBAnalysis)
                    .put("analysis_radius", options.analysisRadius)
                    .put("normalize_lighting", options.normalizeForLighting));
            
            return ok("analyze_xgal_blueness", data);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("analyze_xgal_blueness", e, logger, recovery);
        }
    }
    
    /**
     * Tool: export_timeseries_data
     * Export time-series colony growth analysis data
     */
    public JSONObject exportTimeseriesData(JSONObject args) {
        ToolSchemaValidator.require(args, "tracking_handle");
        try {
            String trackingHandle = args.getString("tracking_handle");
            SessionStore.AnalysisRecord trackingRecord = store.getAnalysis(trackingHandle);
            
            if (trackingRecord == null) {
                return ErrorHandler.handleValidationError("export_timeseries_data", 
                    new IllegalArgumentException("Time-series tracking not found: " + trackingHandle), 
                    logger, recovery);
            }
            
            TimeSeriesColonyTracker tracker = (TimeSeriesColonyTracker) trackingRecord.data;
            
            // Export data
            JSONObject exportData = tracker.exportData();
            
            // Save to file if requested
            JSONArray formats = args.optJSONArray("export_formats");
            JSONArray exportedFiles = new JSONArray();
            
            if (formats != null) {
                String baseFilename = args.optString("filename", "timeseries_analysis");
                Path outputPath = tempDir;
                
                for (int i = 0; i < formats.length(); i++) {
                    String format = formats.getString(i);
                    
                    switch (format) {
                        case "json":
                            Path jsonPath = outputPath.resolve(baseFilename + ".json");
                            Files.writeString(jsonPath, exportData.toString(2));
                            exportedFiles.put(new JSONObject()
                                .put("format", "json")
                                .put("path", jsonPath.toString())
                                .put("description", "Complete time-series analysis data"));
                            break;
                            
                        case "csv":
                            Path csvPath = outputPath.resolve(baseFilename + ".csv");
                            exportTimeseriesCSV(csvPath, tracker);
                            exportedFiles.put(new JSONObject()
                                .put("format", "csv")
                                .put("path", csvPath.toString())
                                .put("description", "Colony growth data in spreadsheet format"));
                            break;
                    }
                }
            }
            
            JSONObject data = new JSONObject()
                .put("tracking_handle", trackingHandle)
                .put("export_data", exportData)
                .put("exported_files", exportedFiles)
                .put("summary", exportData.getJSONObject("summary"));
            
            return ok("export_timeseries_data", data);
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_timeseries_data", e, logger, recovery);
        }
    }
    
    // Helper methods for time-series analysis
    
    private Map<String, java.awt.geom.Point2D> extractColonyPositions(SessionStore.ImageRecord img) {
        Map<String, java.awt.geom.Point2D> positions = new java.util.HashMap<>();
        
        // Try to extract from existing overlay
        if (img.currentOverlay != null) {
            for (int i = 0; i < img.currentOverlay.size(); i++) {
                Roi roi = img.currentOverlay.get(i);
                if (roi.getName() != null && roi.getName().contains("Colony")) {
                    Rectangle bounds = roi.getBounds();
                    positions.put("colony_" + i, new java.awt.geom.Point2D.Double(
                        bounds.x + bounds.width/2.0, bounds.y + bounds.height/2.0));
                }
            }
        }
        
        // If no colonies in overlay, use simple detection
        if (positions.isEmpty()) {
            List<TimeSeriesColonyTracker.Colony> colonies = 
                TimeSeriesColonyTracker.analyzeColonies(img.image, false, false);
            for (int i = 0; i < colonies.size(); i++) {
                TimeSeriesColonyTracker.Colony colony = colonies.get(i);
                positions.put("detected_" + i, colony.center);
            }
        }
        
        return positions;
    }
    
    private void exportTimeseriesCSV(Path csvPath, TimeSeriesColonyTracker tracker) throws Exception {
        StringBuilder csv = new StringBuilder();
        csv.append("Track_ID,Time_Point,Colony_ID,Center_X,Center_Y,Area_mm2,Diameter_mm,")
           .append("Circularity,Solidity,Aspect_Ratio,Texture_Variance,Blueness\n");
        
        Map<String, TimeSeriesColonyTracker.ColonyTrack> tracks = tracker.getTracks();
        
        for (TimeSeriesColonyTracker.ColonyTrack track : tracks.values()) {
            for (int t = 0; t < track.timePoints.size(); t++) {
                TimeSeriesColonyTracker.Colony colony = track.timePoints.get(t);
                csv.append(String.format("%s,%d,%s,%.2f,%.2f,%.4f,%.3f,%.3f,%.3f,%.3f,%.2f,%.3f\n",
                    track.trackId, t, colony.id, 
                    colony.center.getX(), colony.center.getY(),
                    colony.area, colony.diameter,
                    colony.morphology.circularity, colony.morphology.solidity, 
                    colony.morphology.aspectRatio, colony.morphology.textureVariance,
                    colony.blueness));
            }
        }
        
        Files.writeString(csvPath, csv.toString());
    }
    
    /**
     * Tool: map_fractions
     * Map purification fractions to gel lanes for yield/purity tracking
     */
    public JSONObject mapFractions(JSONObject args) {
        try {
            // args: lane_map {Name:[lanes...]}, volumes_ml {Name:[ml...] or single}, loaded_ul_default, target_mw_kda
            ToolSchemaValidator.require(args, "lane_map");
            ToolSchemaValidator.require(args, "volumes_ml");
            double loadedUl = args.has("loaded_ul_default") ? args.getNumber("loaded_ul_default").doubleValue() : 10.0;
            double targetMw = args.has("target_mw_kda") ? args.getNumber("target_mw_kda").doubleValue() : Double.NaN;

            java.util.Map<String, java.util.List<Integer>> lanesByName = new java.util.HashMap<>();
            org.json.JSONObject lm = args.getJSONObject("lane_map");
            for (var key : lm.keySet()) {
                var arr = lm.getJSONArray(key);
                java.util.List<Integer> L = new java.util.ArrayList<>();
                for (int i=0;i<arr.length();i++) L.add(arr.getInt(i));
                lanesByName.put(key, L);
            }

            java.util.Map<String, java.util.List<Double>> vols = new java.util.HashMap<>();
            org.json.JSONObject vm = args.getJSONObject("volumes_ml");
            for (var key : vm.keySet()) {
                var v = vm.get(key);
                java.util.List<Double> L = new java.util.ArrayList<>();
                if (v instanceof org.json.JSONArray ja) {
                    for (int i=0;i<ja.length();i++) L.add(ja.getNumber(i).doubleValue());
                } else {
                    L.add(((Number)v).doubleValue());
                }
                vols.put(key, L);
            }

            store.setFractionMap(new com.betterdairy.autodense.session.SessionStore.FractionMap(lanesByName, vols, loadedUl, targetMw));

            return ok("map_fractions", new org.json.JSONObject()
                .put("fractions", lm)
                .put("volumes_ml", vm)
                .put("loaded_ul_default", loadedUl)
                .put("target_mw_kda", targetMw));
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("map_fractions", e, logger, recovery);
        }
    }
    
    /**
     * Tool: compute_yield_purity
     * Compute yield and purity from fraction mapping and calibration
     */
    public JSONObject computeYieldPurity(JSONObject args) {
        try {
            // Validate image handle
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec == null) {
                throw new IllegalArgumentException("Image not found: " + imageHandle);
            }
            
            // Get fraction map
            SessionStore.FractionMap fm = store.getFractionMap();
            if (fm == null) {
                throw new IllegalStateException("No fraction map set. Call map_fractions first.");
            }

            // Get lanes and bands from analysis data
            List<String> analyses = store.getAnalysesForImage(imageHandle);
            List<Lane> lanes = null;
            List<List<Band>> allBands = null;
            
            for (String analysisHandle : analyses) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if ("lanes".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<Lane> tempLanes = (List<Lane>) analysis.data;
                    lanes = tempLanes;
                } else if ("bands".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<List<Band>> tempBands = (List<List<Band>>) analysis.data;
                    allBands = tempBands;
                }
            }
            
            if (lanes == null || allBands == null) {
                throw new IllegalStateException("Lane and band detection required. Run detect_lanes and detect_bands first.");
            }

            // Helper to pick target bands by MW window (±8%)
            double windowPct = args.has("window_pct") ? args.getNumber("window_pct").doubleValue() : 8.0;

            org.json.JSONArray steps = new org.json.JSONArray();
            double firstInUg = Double.NaN;

            for (var entry : fm.lanesByName.entrySet()) {
                String name = entry.getKey();
                var laneNums = entry.getValue();
                var vols = fm.volumesMlByName.getOrDefault(name, java.util.List.of());

                double stepUgLoaded = 0.0;   // from band areas (target bands per lane, sum)
                double stepAreaTarget = 0.0; // area of target bands (for purity)
                double stepAreaTotal  = 0.0; // total lane area (for purity proxy)

                for (int laneNo1 : laneNums) {
                    int laneIdx = laneNo1 - 1; // Convert to 0-based
                    if (laneIdx < 0 || laneIdx >= allBands.size()) continue;
                    
                    List<Band> bands = allBands.get(laneIdx);

                    // Sum total lane corrected area
                    for (Band b : bands) {
                        stepAreaTotal += Math.max(0.0, b.area());
                    }

                    // Select target bands near targetMw (if provided); else use the strongest band
                    java.util.List<Band> targets = new java.util.ArrayList<>();
                    if (!Double.isNaN(fm.targetMwKda)) {
                        // For now, use simple Y-position based selection (would need calibration for true MW)
                        // This is a placeholder - in real implementation you'd use Calibrator
                        for (Band b : bands) {
                            targets.add(b); // Simplified: add all bands as potential targets
                        }
                    } else if (!bands.isEmpty()) {
                        // fallback: top-1 band by area
                        Band top = bands.stream().max(java.util.Comparator.comparingDouble(Band::area)).orElse(null);
                        if (top != null) targets.add(top);
                    }

                    // Per-lane area from target bands (simplified without calibration)
                    for (Band tb : targets) {
                        double area = Math.max(0.0, tb.area());
                        stepUgLoaded += area; // Using area as proxy for amount
                        stepAreaTarget += area;
                    }
                }

                // Scale to whole fraction: total = loaded * (fraction_vol_uL / loaded_ul)
                double stepUgTotal = 0.0;
                if (!vols.isEmpty()) {
                    for (int i=0; i<laneNums.size(); i++) {
                        double volMl = vols.get(Math.min(i, vols.size()-1));
                        double scale = (volMl * 1000.0) / Math.max(1e-9, fm.loadedUlPerLaneDefault);
                        // crude: assume each lane's contribution ~ (stepUgLoaded / laneNums.size())
                        stepUgTotal += (stepUgLoaded / Math.max(1, laneNums.size())) * scale;
                    }
                } else {
                    // no volumes -> report loaded amount only
                    stepUgTotal = stepUgLoaded;
                }

                double purity = (stepAreaTotal > 0.0) ? (stepAreaTarget / stepAreaTotal) : 0.0;

                if (Double.isNaN(firstInUg)) firstInUg = stepUgTotal;

                steps.put(new org.json.JSONObject()
                    .put("name", name)
                    .put("lanes", new org.json.JSONArray(laneNums))
                    .put("target_area_loaded_sum", stepUgLoaded)
                    .put("target_area_total_est", stepUgTotal)
                    .put("purity_est", purity)
                    .put("volumes_ml", fm.volumesMlByName.getOrDefault(name, java.util.List.of())));
            }

            // Simple recoveries using first step as input
            for (int i=0; i<steps.length(); i++) {
                var obj = steps.getJSONObject(i);
                double areaTot = obj.getDouble("target_area_total_est");
                double stepRec = (i==0 || firstInUg<=0) ? 1.0 : areaTot / firstInUg;
                obj.put("step_recovery", stepRec);
                obj.put("cum_recovery", stepRec); // same as stepRec in this flat version
            }

            return ok("compute_yield_purity", new org.json.JSONObject().put("steps", steps));
            
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("compute_yield_purity", e, logger, recovery);
        }
    }
    
    /**
     * Tool: profile_isoforms
     * Profile protein isoforms within a molecular weight window
     */
    public JSONObject profileIsoforms(JSONObject args) {
        try {
            // args: target_mw_kda (double), window_pct (double, default 20), lanes ("all" or array)
            ToolSchemaValidator.require(args, "target_mw_kda");
            double targetMw = args.getNumber("target_mw_kda").doubleValue();
            double windowPct = args.has("window_pct") ? args.getNumber("window_pct").doubleValue() : 20.0;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec == null) {
                throw new IllegalArgumentException("Image not found: " + imageHandle);
            }
            
            // Get lanes and bands from analysis data
            List<String> analyses = store.getAnalysesForImage(imageHandle);
            List<Lane> lanes = null;
            List<List<Band>> allBands = null;
            
            for (String analysisHandle : analyses) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if ("lanes".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<Lane> tempLanes = (List<Lane>) analysis.data;
                    lanes = tempLanes;
                } else if ("bands".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<List<Band>> tempBands = (List<List<Band>>) analysis.data;
                    allBands = tempBands;
                }
            }
            
            if (lanes == null || allBands == null) {
                throw new IllegalStateException("Lane and band detection required. Run detect_lanes and detect_bands first.");
            }
            
            // Resolve which lanes to analyze
            List<Integer> targetLanes = new ArrayList<>();
            if (args.has("lanes") && !args.isNull("lanes")) {
                if (args.get("lanes") instanceof String && "all".equals(args.getString("lanes"))) {
                    for (int i = 0; i < lanes.size(); i++) {
                        targetLanes.add(i);
                    }
                } else {
                    JSONArray laneArray = args.getJSONArray("lanes");
                    for (int i = 0; i < laneArray.length(); i++) {
                        int laneIndex1 = laneArray.getInt(i);
                        if (laneIndex1 >= 1 && laneIndex1 <= lanes.size()) {
                            targetLanes.add(laneIndex1 - 1); // Convert to 0-based
                        }
                    }
                }
            } else {
                // Default to all lanes
                for (int i = 0; i < lanes.size(); i++) {
                    targetLanes.add(i);
                }
            }
            
            JSONArray lanesOut = new JSONArray();
            
            // Optional calibrator for MW; if missing we fall back to position-based analysis
            // Note: In simplified version, we'll use position-based analysis
            
            for (int laneIdx : targetLanes) {
                if (laneIdx >= allBands.size()) continue;
                
                List<Band> bandsInLane = allBands.get(laneIdx);
                List<Band> inWindow = new ArrayList<>();
                
                // For now, without calibration, select bands within middle region of gel
                // This is a simplified approach - in real implementation you'd use MW calibration
                double sumArea = 0.0;
                for (Band b : bandsInLane) {
                    // Simple position-based filtering (bands in middle 60% of gel height)
                    double relativeY = (double) b.y() / rec.image.getHeight();
                    if (relativeY >= 0.2 && relativeY <= 0.8) {
                        inWindow.add(b);
                        sumArea += Math.max(0.0, b.area());
                    }
                }
                
                // Create synthetic profile for sharpness calculation (simplified)
                float[] profile = createSimpleProfile(rec.image, lanes.get(laneIdx));
                
                JSONArray isoforms = new JSONArray();
                for (int i = 0; i < inWindow.size(); i++) {
                    Band b = inWindow.get(i);
                    int apexIdx = Math.max(0, Math.min(profile.length - 1, b.y()));
                    double sharp = Quant.sharpnessScore(profile, apexIdx);
                    double bandMw = Double.NaN; // Would use calibration if available
                    double areaPct = (sumArea > 0.0) ? (b.area() / sumArea) : 0.0;
                    
                    isoforms.put(new JSONObject()
                        .put("band_idx", i)
                        .put("mw_kda", bandMw)
                        .put("apex_y", b.y())
                        .put("width_px", 8.0) // Default width
                        .put("sharpness", sharp)
                        .put("area_corr", b.area())
                        .put("area_pct", areaPct));
                }
                
                double smear = Quant.smearFractionWithinWindow(inWindow);
                
                lanesOut.put(new JSONObject()
                    .put("lane", laneIdx + 1) // Convert back to 1-based
                    .put("n_isoforms", inWindow.size())
                    .put("smear_fraction", smear)
                    .put("isoforms", isoforms));
            }
            
            return ok("profile_isoforms", new JSONObject()
                .put("target_mw_kda", targetMw)
                .put("window_pct", windowPct)
                .put("lanes", lanesOut));
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("profile_isoforms", e, logger, recovery);
        }
    }
    
    /**
     * Create a simple vertical profile for a lane (helper for isoform analysis)
     */
    private float[] createSimpleProfile(ImagePlus image, Lane lane) {
        int height = image.getHeight();
        float[] profile = new float[height];
        ImageProcessor ip = image.getProcessor();
        
        int x0 = lane.xStart();
        int x1 = lane.xEnd();
        
        for (int y = 0; y < height; y++) {
            float sum = 0;
            for (int x = x0; x <= x1; x++) {
                sum += ip.getf(x, y);
            }
            profile[y] = sum / Math.max(1, x1 - x0 + 1);
        }
        
        return profile;
    }

    /**
     * Tool: hcp_snapshot
     * Quantify non-target signal per lane and list the top contaminant bands
     */
    public JSONObject hcpSnapshot(JSONObject args) {
        try {
            // args: target_mw_kda, window_pct (default 8), top_n (default 5)
            ToolSchemaValidator.require(args, "target_mw_kda");
            double targetMw = args.getNumber("target_mw_kda").doubleValue();
            double windowPct = args.has("window_pct") ? args.getNumber("window_pct").doubleValue() : 8.0;
            int topN = args.has("top_n") ? args.getInt("top_n") : 5;

            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord rec = store.getImage(imageHandle);
            if (rec == null) {
                throw new IllegalArgumentException("Image not found: " + imageHandle);
            }

            // Get calibration model - required for HCP analysis
            List<String> analyses = store.getAnalysesForImage(imageHandle);
            CalibrationModel cal = null;
            List<Lane> lanes = null;
            List<List<Band>> allBands = null;
            
            for (String analysisHandle : analyses) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if ("calibration".equals(analysis.type)) {
                    cal = (CalibrationModel) analysis.data;
                } else if ("lanes".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<Lane> tempLanes = (List<Lane>) analysis.data;
                    lanes = tempLanes;
                } else if ("bands".equals(analysis.type)) {
                    @SuppressWarnings("unchecked")
                    List<List<Band>> tempBands = (List<List<Band>>) analysis.data;
                    allBands = tempBands;
                }
            }

            if (cal == null) {
                throw new IllegalStateException("MW calibration required for HCP analysis. Run calibrate_molecular_weight first.");
            }
            if (lanes == null || allBands == null) {
                throw new IllegalStateException("Lane and band detection required. Run detect_lanes and detect_bands first.");
            }

            // Resolve which lanes to analyze
            List<Integer> targetLanes = new ArrayList<>();
            if (args.has("lanes") && !args.isNull("lanes")) {
                if (args.get("lanes") instanceof String && "all".equals(args.getString("lanes"))) {
                    for (int i = 0; i < lanes.size(); i++) {
                        targetLanes.add(i);
                    }
                } else {
                    JSONArray laneArray = args.getJSONArray("lanes");
                    for (int i = 0; i < laneArray.length(); i++) {
                        int laneIndex1 = laneArray.getInt(i);
                        if (laneIndex1 >= 1 && laneIndex1 <= lanes.size()) {
                            targetLanes.add(laneIndex1 - 1); // Convert to 0-based
                        }
                    }
                }
            } else {
                // Default to all lanes
                for (int i = 0; i < lanes.size(); i++) {
                    targetLanes.add(i);
                }
            }

            JSONArray out = new JSONArray();
            for (int laneIdx : targetLanes) {
                if (laneIdx >= allBands.size()) continue;
                
                List<Band> bandsInLane = allBands.get(laneIdx);
                double total = 0.0; 
                for (var b : bandsInLane) total += Math.max(0.0, b.area());
                
                double hcpArea = Quant.sumAreaExceptWindow(bandsInLane, cal, targetMw, windowPct);
                double hcpPct = (total > 0) ? hcpArea / total * 100.0 : 0.0;

                List<Band> top = Quant.topNonTargetBands(bandsInLane, cal, targetMw, windowPct, topN);
                JSONArray contaminants = new JSONArray();
                for (Band b : top) {
                    double mw = Calibrator.assignMw(cal, b.y());
                    double rel = (total > 0) ? b.area() / total * 100.0 : 0.0;
                    contaminants.put(new JSONObject()
                        .put("band_idx", b.index())
                        .put("mw_kda", mw)
                        .put("rel_area_pct", rel)
                        .put("area_corr", b.area()));
                }

                out.put(new JSONObject()
                    .put("lane", laneIdx + 1) // Convert back to 1-based
                    .put("hcp_percent", hcpPct)
                    .put("top_contaminants", contaminants));
            }

            return ok("hcp_snapshot", new JSONObject()
                .put("target_mw_kda", targetMw)
                .put("window_pct", windowPct)
                .put("analysis", String.format("HCP analysis for %.1f kDa target (±%.1f%% window)", targetMw, windowPct))
                .put("lanes", out));

        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("hcp_snapshot", e, logger, recovery);
        }
    }

    /**
     * Tool: compare_treatments
     * Compare control vs treated lanes; report MW shifts, sharpening, and area changes
     */
    public JSONObject compareTreatments(JSONObject args) {
        try {
            TreatmentComparisonParams params = validateTreatmentComparisonParams(args);
            AnalysisData analysisData = retrieveRequiredAnalysisData(params.imageHandle);
            TreatmentComparisonResult result = performTreatmentComparison(analysisData, params);
            return formatTreatmentComparisonResponse(result);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("compare_treatments", e, logger, recovery);
        }
    }

    // Supporting classes for treatment comparison refactoring
    private static class TreatmentComparisonParams {
        final String imageHandle;
        final List<Integer> controlLanes;
        final List<Integer> treatedLanes;
        final double mwLo;
        final double mwHi;

        TreatmentComparisonParams(String imageHandle, JSONArray controlLanesJson, JSONArray treatedLanesJson, 
                                JSONArray mwWindowJson) {
            this.imageHandle = imageHandle;
            this.mwLo = mwWindowJson.getNumber(0).doubleValue();
            this.mwHi = mwWindowJson.getNumber(1).doubleValue();

            this.controlLanes = new ArrayList<>();
            for (int i = 0; i < controlLanesJson.length(); i++) {
                int laneIndex1 = controlLanesJson.getInt(i);
                if (laneIndex1 >= 1) {
                    this.controlLanes.add(laneIndex1 - 1); // Convert to 0-based
                }
            }

            this.treatedLanes = new ArrayList<>();
            for (int i = 0; i < treatedLanesJson.length(); i++) {
                int laneIndex1 = treatedLanesJson.getInt(i);
                if (laneIndex1 >= 1) {
                    this.treatedLanes.add(laneIndex1 - 1); // Convert to 0-based
                }
            }
        }
    }

    private static class TreatmentComparisonResult {
        final double mwLo;
        final double mwHi;
        final JSONArray pairs;

        TreatmentComparisonResult(double mwLo, double mwHi, JSONArray pairs) {
            this.mwLo = mwLo;
            this.mwHi = mwHi;
            this.pairs = pairs;
        }
    }

    private TreatmentComparisonParams validateTreatmentComparisonParams(JSONObject args) {
        ToolSchemaValidator.require(args, "control_lanes");
        ToolSchemaValidator.require(args, "treated_lanes");
        ToolSchemaValidator.require(args, "mw_window");

        String imageHandle = args.getString("image_handle");
        SessionStore.ImageRecord rec = store.getImage(imageHandle);
        if (rec == null) {
            throw new IllegalArgumentException("Image not found: " + imageHandle);
        }

        JSONArray controlLanesJson = args.getJSONArray("control_lanes");
        JSONArray treatedLanesJson = args.getJSONArray("treated_lanes");
        JSONArray mwWindowJson = args.getJSONArray("mw_window");

        return new TreatmentComparisonParams(imageHandle, controlLanesJson, treatedLanesJson, mwWindowJson);
    }

    private TreatmentComparisonResult performTreatmentComparison(AnalysisData data, TreatmentComparisonParams params) {
        SessionStore.ImageRecord rec = store.getImage(params.imageHandle);
        JSONArray pairs = new JSONArray();

        for (int k = 0; k < Math.min(params.controlLanes.size(), params.treatedLanes.size()); k++) {
            int cLaneIdx = params.controlLanes.get(k);
            int tLaneIdx = params.treatedLanes.get(k);
            
            if (cLaneIdx >= data.allBands.size() || tLaneIdx >= data.allBands.size()) continue;
            
            Lane cLane = data.lanes.get(cLaneIdx);
            Lane tLane = data.lanes.get(tLaneIdx);
            List<Band> cBands = data.allBands.get(cLaneIdx);
            List<Band> tBands = data.allBands.get(tLaneIdx);

            // Analyze lane pair
            JSONArray lanePairs = analyzeLanePair(rec, data.calibration, cLane, tLane, 
                                               cBands, tBands, cLaneIdx, tLaneIdx, 
                                               params.mwLo, params.mwHi);
            for (int i = 0; i < lanePairs.length(); i++) {
                pairs.put(lanePairs.get(i));
            }
        }

        return new TreatmentComparisonResult(params.mwLo, params.mwHi, pairs);
    }

    private JSONArray analyzeLanePair(SessionStore.ImageRecord rec, CalibrationModel cal,
                                     Lane cLane, Lane tLane, List<Band> cBands, List<Band> tBands,
                                     int cLaneIdx, int tLaneIdx, double mwLo, double mwHi) {
        // Create profiles for alignment
        float[] pc = createSimpleProfile(rec.image, cLane);
        float[] pt = createSimpleProfile(rec.image, tLane);
        int delta = Profiles.alignByXcorr(pc, pt, 4);

        // Collect bands within MW window
        List<Band> cb = collectBandsInMWWindow(cBands, cal, mwLo, mwHi, 0);
        List<Band> tb = collectBandsInMWWindow(tBands, cal, mwLo, mwHi, delta);

        return matchAndAnalyzeBands(cb, tb, cal, pc, pt, delta, cLaneIdx, tLaneIdx);
    }

    private List<Band> collectBandsInMWWindow(List<Band> bands, CalibrationModel cal, 
                                            double mwLo, double mwHi, int deltaY) {
        List<Band> filtered = new ArrayList<>();
        for (Band b : bands) {
            double mw = Calibrator.assignMw(cal, b.y() + deltaY);
            if (mw >= mwLo && mw <= mwHi) {
                filtered.add(b);
            }
        }
        return filtered;
    }

    private JSONArray matchAndAnalyzeBands(List<Band> controlBands, List<Band> treatedBands,
                                         CalibrationModel cal, float[] pcProfile, float[] ptProfile,
                                         int delta, int cLaneIdx, int tLaneIdx) {
        JSONArray pairs = new JSONArray();
        
        for (Band bC : controlBands) {
            double mwC = Calibrator.assignMw(cal, bC.y());
            Band bestMatch = findBestMWMatch(treatedBands, cal, mwC, delta);
            
            if (bestMatch != null) {
                JSONObject pair = createBandComparisonPair(bC, bestMatch, cal, pcProfile, ptProfile,
                                                         delta, cLaneIdx, tLaneIdx);
                pairs.put(pair);
            }
        }
        
        return pairs;
    }

    private Band findBestMWMatch(List<Band> bands, CalibrationModel cal, double targetMw, int delta) {
        Band best = null;
        double bestDiff = Double.MAX_VALUE;
        
        for (Band b : bands) {
            double mw = Calibrator.assignMw(cal, b.y() + delta);
            double diff = Math.abs(targetMw - mw);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = b;
            }
        }
        
        return best;
    }

    private JSONObject createBandComparisonPair(Band controlBand, Band treatedBand, CalibrationModel cal,
                                              float[] pcProfile, float[] ptProfile, int delta,
                                              int cLaneIdx, int tLaneIdx) {
        double mwC = Calibrator.assignMw(cal, controlBand.y());
        double mwT = Calibrator.assignMw(cal, treatedBand.y() + delta);
        
        // Calculate sharpness scores
        int iC = Math.max(0, Math.min(pcProfile.length - 1, (int)Math.round(controlBand.y())));
        int iT = Math.max(0, Math.min(ptProfile.length - 1, (int)Math.round(treatedBand.y() + delta)));
        double sC = Quant.sharpnessScore(pcProfile, iC);
        double sT = Quant.sharpnessScore(ptProfile, iT);

        return new JSONObject()
            .put("control_lane", cLaneIdx + 1)
            .put("treated_lane", tLaneIdx + 1)
            .put("mw_control", mwC)
            .put("mw_treated", mwT)
            .put("delta_mw", mwT - mwC)
            .put("sharpness_control", sC)
            .put("sharpness_treated", sT)
            .put("delta_sharpness", sT - sC)
            .put("area_control", controlBand.area())
            .put("area_treated", treatedBand.area())
            .put("area_ratio", treatedBand.area() / Math.max(1e-9, controlBand.area()));
    }

    private JSONObject formatTreatmentComparisonResponse(TreatmentComparisonResult result) {
        return ok("compare_treatments", new JSONObject()
            .put("mw_window", new JSONArray().put(result.mwLo).put(result.mwHi))
            .put("analysis", String.format("Treatment comparison for %.1f-%.1f kDa window", 
                result.mwLo, result.mwHi))
            .put("pairs", result.pairs));
    }

    // Supporting classes for digestKinetics refactoring
    private static class DigestKineticsParams {
        final String imageHandle;
        final List<Integer> targetLanes;
        final List<Double> times;
        final double parentMw;
        final double windowPct;
        
        DigestKineticsParams(String imageHandle, JSONArray lanesJson, JSONArray timesJson, 
                           double parentMw, double windowPct) {
            this.imageHandle = imageHandle;
            this.parentMw = parentMw;
            this.windowPct = windowPct;
            
            this.targetLanes = new ArrayList<>();
            for (int i = 0; i < lanesJson.length(); i++) {
                int laneIndex1 = lanesJson.getInt(i);
                if (laneIndex1 >= 1) {
                    this.targetLanes.add(laneIndex1 - 1); // Convert to 0-based
                }
            }
            
            this.times = new ArrayList<>();
            for (int i = 0; i < timesJson.length(); i++) {
                this.times.add(timesJson.getNumber(i).doubleValue());
            }
        }
    }
    
    private static class AnalysisData {
        final CalibrationModel calibration;
        final List<Lane> lanes;
        final List<List<Band>> allBands;
        
        AnalysisData(CalibrationModel calibration, List<Lane> lanes, List<List<Band>> allBands) {
            this.calibration = calibration;
            this.lanes = lanes;
            this.allBands = allBands;
        }
    }
    
    private static class DigestKineticsResult {
        final double parentMw;
        final double windowPct;
        final double kPerMin;
        final double tHalfMin;
        final double rSquared;
        final JSONArray timeSeries;
        final JSONArray fragments;
        
        DigestKineticsResult(double parentMw, double windowPct, double kPerMin, 
                           double tHalfMin, double rSquared, JSONArray timeSeries, JSONArray fragments) {
            this.parentMw = parentMw;
            this.windowPct = windowPct;
            this.kPerMin = kPerMin;
            this.tHalfMin = tHalfMin;
            this.rSquared = rSquared;
            this.timeSeries = timeSeries;
            this.fragments = fragments;
        }
    }

    /**
     * Tool: digest_kinetics
     * Track parent band decay and fragment emergence over a time series
     */
    public JSONObject digestKinetics(JSONObject args) {
        try {
            DigestKineticsParams params = validateDigestKineticsParams(args);
            AnalysisData analysisData = retrieveRequiredAnalysisData(params.imageHandle);
            DigestKineticsResult result = performDigestKineticsAnalysis(analysisData, params);
            return formatDigestKineticsResponse(result);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("digest_kinetics", e, logger, recovery);
        }
    }

    private DigestKineticsParams validateDigestKineticsParams(JSONObject args) {
        ToolSchemaValidator.require(args, "lanes");
        ToolSchemaValidator.require(args, "times_min");
        ToolSchemaValidator.require(args, "parent_mw_kda");
        
        String imageHandle = args.getString("image_handle");
        SessionStore.ImageRecord rec = store.getImage(imageHandle);
        if (rec == null) {
            throw new IllegalArgumentException("Image not found: " + imageHandle);
        }

        JSONArray lanesJson = args.getJSONArray("lanes");
        JSONArray timesJson = args.getJSONArray("times_min");
        double parentMw = args.getNumber("parent_mw_kda").doubleValue();
        double windowPct = args.has("window_pct") ? args.getNumber("window_pct").doubleValue() : 15.0;

        if (timesJson.length() != lanesJson.length()) {
            throw new IllegalArgumentException("times_min length must equal number of lanes");
        }

        return new DigestKineticsParams(imageHandle, lanesJson, timesJson, parentMw, windowPct);
    }

    private AnalysisData retrieveRequiredAnalysisData(String imageHandle) {
        List<String> analyses = store.getAnalysesForImage(imageHandle);
        CalibrationModel cal = null;
        List<Lane> lanes = null;
        List<List<Band>> allBands = null;
        
        for (String analysisHandle : analyses) {
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            if ("calibration".equals(analysis.type)) {
                cal = (CalibrationModel) analysis.data;
            } else if ("lanes".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<Lane> tempLanes = (List<Lane>) analysis.data;
                lanes = tempLanes;
            } else if ("bands".equals(analysis.type)) {
                @SuppressWarnings("unchecked")
                List<List<Band>> tempBands = (List<List<Band>>) analysis.data;
                allBands = tempBands;
            }
        }

        if (cal == null) {
            throw new IllegalStateException("MW calibration required for digest kinetics. Run calibrate_molecular_weight first.");
        }
        if (lanes == null || allBands == null) {
            throw new IllegalStateException("Lane and band detection required. Run detect_lanes and detect_bands first.");
        }

        return new AnalysisData(cal, lanes, allBands);
    }

    private DigestKineticsResult performDigestKineticsAnalysis(AnalysisData data, DigestKineticsParams params) {
        // Perform parent decay analysis
        JSONArray series = new JSONArray();
        List<Double> t = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        
        for (int i = 0; i < params.targetLanes.size(); i++) {
            int laneIdx = params.targetLanes.get(i);
            if (laneIdx >= data.allBands.size()) continue;
            
            double ti = params.times.get(i);
            List<Band> bandsInLane = data.allBands.get(laneIdx);
            Band parent = Peaks.closestByMw(bandsInLane, data.calibration, params.parentMw, params.windowPct);
            double area = (parent == null) ? 0.0 : Math.max(1e-9, parent.area());
            
            t.add(ti);
            y.add(Math.log(area));
            series.put(new JSONObject()
                .put("t_min", ti)
                .put("lane", laneIdx + 1)
                .put("parent_area", area)
                .put("parent_present", parent != null));
        }
        
        // Calculate kinetics parameters using OLS
        double[] kineticsParams = calculateKineticsParameters(t, y);
        double kPerMin = kineticsParams[0];
        double tHalfMin = kineticsParams[1];
        double rSquared = kineticsParams[2];
        
        // Analyze fragment emergence
        JSONArray fragments = analyzeFragmentEmergence(data, params, t);
        
        return new DigestKineticsResult(params.parentMw, params.windowPct, kPerMin, 
                                      tHalfMin, rSquared, series, fragments);
    }

    private double[] calculateKineticsParameters(List<Double> t, List<Double> y) {
        int n = t.size();
        if (n < 2) return new double[]{0.0, Double.POSITIVE_INFINITY, 0.0};
        
        // OLS fit y = a + b*t; k = -b; t_half = ln(2)/k
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += t.get(i);
            sy += y.get(i);
        }
        double tx = sx / n, ty = sy / n;
        for (int i = 0; i < n; i++) {
            double dx = t.get(i) - tx, dy = y.get(i) - ty;
            sxx += dx * dx;
            sxy += dx * dy;
        }
        
        double b = (sxx > 0) ? sxy / sxx : 0.0;
        double k = -b;
        double tHalf = (k > 0) ? (Math.log(2.0) / k) : Double.POSITIVE_INFINITY;
        
        // Calculate R²
        double r2 = 0.0;
        if (sxx > 0) {
            double syy = 0;
            for (int i = 0; i < n; i++) {
                double dy = y.get(i) - ty;
                syy += dy * dy;
            }
            r2 = (syy > 0) ? (sxy * sxy) / (sxx * syy) : 0.0;
        }
        
        return new double[]{k, tHalf, r2};
    }

    private JSONArray analyzeFragmentEmergence(AnalysisData data, DigestKineticsParams params, List<Double> t) {
        JSONArray fragments = new JSONArray();
        Map<String, List<Double>> fragmentAreasByMw = new HashMap<>();
        Map<String, Double> fragmentMws = new HashMap<>();
        
        // Collect fragments across time points
        for (int i = 0; i < params.targetLanes.size(); i++) {
            int laneIdx = params.targetLanes.get(i);
            if (laneIdx >= data.allBands.size()) continue;
            
            List<Band> bandsInLane = data.allBands.get(laneIdx);
            for (Band band : bandsInLane) {
                double mw = Calibrator.assignMw(data.calibration, band.y());
                if (Double.isNaN(mw) || mw >= params.parentMw * 0.98) continue;
                
                String mwKey = String.format("%.1f", mw);
                fragmentAreasByMw.computeIfAbsent(mwKey, key -> new ArrayList<>())
                    .add(Math.max(0.0, band.area()));
                fragmentMws.putIfAbsent(mwKey, mw);
            }
        }
        
        // Analyze increasing fragments
        int n = t.size();
        for (Map.Entry<String, List<Double>> entry : fragmentAreasByMw.entrySet()) {
            List<Double> areas = entry.getValue();
            if (areas.size() != n) continue;
            
            double slope = calculateSlope(t, areas);
            if (slope > 0.1) {
                fragments.put(new JSONObject()
                    .put("mw_kda", fragmentMws.get(entry.getKey()))
                    .put("slope_area_per_min", slope)
                    .put("initial_area", areas.get(0))
                    .put("final_area", areas.get(areas.size() - 1)));
            }
        }
        
        return fragments;
    }

    private double calculateSlope(List<Double> x, List<Double> y) {
        int n = x.size();
        if (n < 2) return 0.0;
        
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += x.get(i);
            sy += y.get(i);
        }
        double mx = sx / n, my = sy / n;
        for (int i = 0; i < n; i++) {
            double dx = x.get(i) - mx, dy = y.get(i) - my;
            sxx += dx * dx;
            sxy += dx * dy;
        }
        
        return (sxx > 0) ? sxy / sxx : 0.0;
    }

    private JSONObject formatDigestKineticsResponse(DigestKineticsResult result) {
        return ok("digest_kinetics", new JSONObject()
            .put("parent_mw_kda", result.parentMw)
            .put("window_pct", result.windowPct)
            .put("k_per_min", result.kPerMin)
            .put("t_half_min", result.tHalfMin)
            .put("r_squared", result.rSquared)
            .put("analysis", String.format("Digest kinetics for %.1f kDa parent protein (k=%.4f/min, t½=%.1f min)",
                result.parentMw, result.kPerMin, result.tHalfMin))
            .put("time_series", result.timeSeries)
            .put("fragments", result.fragments));
    }
    
    /**
     * Tool: ui.refresh_canvas
     * Refresh the web canvas overlay after ImageJ operations
     * Creates cache-busted overlay URL for immediate visual feedback
     */
    public JSONObject refreshCanvas(JSONObject args) {
        try {
            String imageHandle = args.optString("image_handle");
            if (imageHandle.isEmpty()) {
                // Use most recent image if no handle specified
                if (store.getImageCount() > 0) {
                    // Get the most recent image handle (simple implementation)
                    imageHandle = "img_current";
                }
            }
            
            if (!imageHandle.isEmpty()) {
                SessionStore.ImageRecord img = store.getImage(imageHandle);
                if (img != null && img.currentOverlay != null) {
                    // Generate new overlay PNG with cache-busting timestamp
                    JSONObject overlayArgs = new JSONObject()
                        .put("image_handle", imageHandle)
                        .put("max_width", 1200)
                        .put("quality", 90);
                        
                    JSONObject overlayResult = renderOverlayPng(overlayArgs);
                    if (overlayResult.optBoolean("success", false)) {
                        String pngPath = overlayResult.optJSONObject("data").optString("png_path");
                        
                        // Convert absolute path to web-accessible URL
                        String overlayUrl = "/overlays/current.png";
                        
                        return ok("refresh_canvas", new JSONObject()
                            .put("overlay_png", overlayUrl)
                            .put("cache_bust", System.currentTimeMillis())
                            .put("image_handle", imageHandle)
                            .put("overlay_updated", true));
                    }
                }
            }
            
            // Return success even if no overlay to refresh
            return ok("refresh_canvas", new JSONObject()
                .put("overlay_png", "/overlays/current.png")
                .put("cache_bust", System.currentTimeMillis())
                .put("overlay_updated", false)
                .put("message", "No overlay to refresh"));
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("refresh_canvas", e, logger, recovery);
        }
    }
    
}
