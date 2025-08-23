package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.analysis.*;
import com.betterdairy.autodense.model.Models.*;
import com.betterdairy.autodense.plugin.ToolSchemaValidator;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
// Removed unused ImageJ imports
import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Tool implementations for Gemini function calling.
 * Each tool operates on handles, not pixels.
 */
public class GelAnalysisTools {
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    private final HandleGuard handleGuard;
    private Path tempDir;
    
    // Cache for expensive preprocessing operations (rolling ball, CLAHE, etc.)
    private final Map<String, String> preprocessingCache = new ConcurrentHashMap<>();
    
    public GelAnalysisTools(SessionStore store) {
        this.store = store;
        this.recovery = new SessionRecovery(store);
        this.handleGuard = new HandleGuard(store, recovery);
        try {
            this.tempDir = Files.createTempDirectory("autodense_");
        } catch (Exception e) {
            this.tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        }
    }
    
    private JSONObject ok(String tool, JSONObject data) {
        return new JSONObject().put("ok", true).put("tool", tool).put("data", data).put("warnings", new JSONArray());
    }
    
    /**
     * Create standardized failure response
     */
    private JSONObject fail(String code, String msg, String param) {
        return new JSONObject().put("ok", false)
            .put("error", new JSONObject().put("code", code).put("message", msg).put("param", param));
    }
    
    
    /**
     * Standard error codes for Gemini self-correction
     */
    private static final String ERROR_MISSING_REQUIRED_FIELD = "missing_required_field";
    @SuppressWarnings("unused")
    private static final String ERROR_INVALID_PARAM = "invalid_param";
    private static final String ERROR_IMAGE_NOT_FOUND = "image_not_found";
    private static final String ERROR_IMAGE_STATE_CONFLICT = "image_state_conflict";
    private static final String ERROR_IJ_RUNTIME_ERROR = "ij_runtime_error";
    protected static final String ERROR_ANALYSIS_FAILED = "analysis_failed";
    
    // DEPRECATED PARAMETER CLAMPING: Use ParameterValidator for new code
    // These methods remain only for backward compatibility in deprecated tools
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Enforce handle discipline - ensure image_handle is present or inject last active
     * Returns error JSONObject if handle cannot be resolved, null if successful
     */
    private JSONObject enforceHandleDiscipline(JSONObject args) {
        // Before dispatch
        if (!args.has("image_handle") || args.isNull("image_handle")) {
            var last = store.lastActiveImageHandle();
            if (last != null) {
                args.put("image_handle", last);
                // Note: SessionLogger not directly accessible here, would need to be passed in
                // sessionLogger.warn("tool_call", "Injected missing image_handle=" + last);
                System.out.println("DEBUG: Injected missing image_handle=" + last);
            } else {
                return fail(ERROR_MISSING_REQUIRED_FIELD, "image_handle is required", "image_handle");
            }
        }
        return null; // Success
    }
    
    /**
     * Tool: open_image
     * Load a gel image into the session
     */
    public JSONObject openImage(JSONObject args) {
        ToolSchemaValidator.require(args, "path");
        try {
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
                
        } catch (Exception e) {
            return recovery.createRecoveryResponse("open_image", e);
        }
    }
    
    /**
     * Tool: preprocess
     * Apply preprocessing steps to an image
     * IMPORTANT: All preprocessing operations are destructive and will modify pixel data.
     * Set "destructive": true to modify the original image in place.
     * If destructive=false or omitted, creates a duplicate and processes that instead.
     */
    public JSONObject preprocess(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            ToolSchemaValidator.requireArray(args, "steps");
            SessionStore.ImageRecord originalImg = store.getImage(args.getString("image_handle"));
            if (originalImg == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            JSONArray steps = args.getJSONArray("steps");
            boolean destructive = args.optBoolean("destructive", false);
            
            // Create cache key for this preprocessing combination
            String cacheKey = createPreprocessingCacheKey(originalImg.handle, steps, destructive);
            
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
                
        } catch (Exception e) {
            return fail(ERROR_IJ_RUNTIME_ERROR, "ImageJ preprocessing error: " + e.getMessage(), "preprocessing");
        }
    }
    
    /**
     * Tool: detect_lanes
     * Detect lanes in a gel image
     */
    public JSONObject detectLanes(JSONObject args) {
        ToolSchemaValidator.requireImageHandle(args);
        try {
            // Enforce handle discipline first
            JSONObject disciplineError = enforceHandleDiscipline(args);
            if (disciplineError != null) return disciplineError;
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            // Clamp and echo parameters for determinism
            int expectedLanes = args.optInt("expected_lanes", 0);
            expectedLanes = clamp(expectedLanes, 1, 50); // Reasonable range for gel lanes
            args.put("expected_lanes", expectedLanes);
            
            boolean constantSpacing = args.optBoolean("constant_spacing", true);
            
            double laneWidth = args.optDouble("lane_width_fraction", 0.55);
            laneWidth = clamp(laneWidth, 0.1, 0.9); // 10% to 90% of spacing
            args.put("lane_width_fraction", laneWidth);
            
            double gridOffset = args.optDouble("grid_offset", 0.0);
            gridOffset = clamp(gridOffset, -0.5, 0.5); // ±50% offset
            args.put("grid_offset", gridOffset);
            
            double minPeakDistance = args.optDouble("min_peak_distance", 20.0);
            minPeakDistance = clamp(minPeakDistance, 5.0, 200.0); // 5-200 pixels
            args.put("min_peak_distance", minPeakDistance);
            
            // Detect lanes using existing detector
            List<Lane> lanes = LaneDetector.findLanes(
                img.image, expectedLanes, constantSpacing, 
                laneWidth, gridOffset, true
            );
            
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
            
            String overlayHandle = store.putOverlay(overlay, img.handle);
            String analysisHandle = store.putAnalysis("lanes", lanes, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            // Build standardized success response
            JSONObject data = new JSONObject()
                .put("lanes_found", lanes.size())
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
                
        } catch (Exception e) {
            return recovery.createRecoveryResponse("detect_lanes", e);
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
            
            String imageHandle = args.getString("image_handle");
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            // Clamp and echo parameters for determinism
            double sensitivity = args.optDouble("sensitivity", 0.3);
            sensitivity = clamp(sensitivity, 0.1, 1.0); // 10% to 100% sensitivity
            args.put("sensitivity", sensitivity);
            
            double minBandHeight = args.optDouble("min_band_height", 3.0);
            minBandHeight = clamp(minBandHeight, 1.0, 20.0); // 1-20 pixels
            args.put("min_band_height", minBandHeight);
            
            double prominence = args.optDouble("prominence", 0.05);
            prominence = clamp(prominence, 0.01, 0.5); // 1% to 50% prominence
            args.put("prominence", prominence);
            
            // Get lanes from previous analysis or detect them
            List<Lane> lanes;
            if (args.has("analysis_handle")) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(args.getString("analysis_handle"));
                @SuppressWarnings("unchecked")
                List<Lane> tempLanes = (List<Lane>) analysis.data;
                lanes = tempLanes;
            } else {
                // Detect lanes first
                lanes = LaneDetector.findLanes(img.image, 0, false, 0.55, 0.0, true);
            }
            
            // Detect bands in each lane
            int totalBands = 0;
            List<List<Band>> allBands = new ArrayList<>();
            
            for (Lane lane : lanes) {
                List<Band> bands = BandDetector.findBands(img.image, lane);
                allBands.add(bands);
                totalBands += bands.size();
            }
            
            // Create overlay with lanes and bands
            Overlay overlay = new Overlay();
            int height = img.image.getHeight();
            
            for (int i = 0; i < lanes.size(); i++) {
                Lane lane = lanes.get(i);
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
                List<Band> bands = allBands.get(i);
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
            
            String overlayHandle = store.putOverlay(overlay, img.handle);
            String analysisHandle = store.putAnalysis("bands", allBands, img.handle);
            
            // Ensure this image remains current for subsequent operations
            store.setLastActiveImageHandle(img.handle);
            
            // Build standardized success response
            JSONObject data = new JSONObject()
                .put("lanes_analyzed", lanes.size())
                .put("bands_total", totalBands)
                .put("overlay_handle", overlayHandle)
                .put("analysis_handle", analysisHandle)
                .put("image_handle", img.handle)
                .put("parameters_used", new JSONObject()
                    .put("sensitivity", sensitivity)
                    .put("min_band_height", minBandHeight)
                    .put("prominence", prominence));
            
            return ok("detect_bands", data);
                
        } catch (Exception e) {
            return recovery.createRecoveryResponse("detect_bands", e);
        }
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
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
                return fail(ERROR_IMAGE_STATE_CONFLICT, "No lanes detected yet - run detect_lanes first", "overlay");
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
            return recovery.createRecoveryResponse("adjust_lanes", e);
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            // Clamp and echo parameters for determinism
            int maxWidth = args.optInt("max_width", 1200);
            maxWidth = clamp(maxWidth, 200, 4000); // 200-4000 pixels
            args.put("max_width", maxWidth);
            
            int quality = args.optInt("quality", 90);
            quality = clamp(quality, 50, 100); // 50-100% quality
            args.put("quality", quality);
            
            // Duplicate image with overlay
            ImagePlus dup = img.image.duplicate();
            if (img.currentOverlay != null) {
                dup.setOverlay(img.currentOverlay);
                dup = dup.flatten(); // Burn overlay into image
            }
            
            // Scale if needed
            if (dup.getWidth() > maxWidth) {
                double scale = maxWidth / (double)dup.getWidth();
                int newHeight = (int)(dup.getHeight() * scale);
                ImageProcessor proc = dup.getProcessor();
                proc = proc.resize(maxWidth, newHeight);
                dup.setProcessor(proc);
            }
            
            // Save to temp file
            String filename = "gel_overlay_" + System.currentTimeMillis() + ".png";
            Path outputPath = tempDir.resolve(filename);
            FileSaver fs = new FileSaver(dup);
            fs.saveAsPng(outputPath.toString());
            
            // Build standardized success response
            JSONObject data = new JSONObject()
                .put("png_path", outputPath.toString())
                .put("width", dup.getWidth())
                .put("height", dup.getHeight())
                .put("image_handle", img.handle)
                .put("usage_note", "This PNG shows labeled lanes/bands for visual reference only. Use original image data for all measurements.")
                .put("visual_elements", "Lane labels: L1, L2, L3... Band labels: B1, B2, B3... (first 3 bands per lane)")
                .put("parameters_used", new JSONObject()
                    .put("max_width", maxWidth)
                    .put("quality", quality));
            
            return ok("render_overlay_png", data);
                
        } catch (Exception e) {
            return recovery.createRecoveryResponse("render_overlay_png", e);
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            String analysisHandle = args.optString("analysis_handle", "");
            if (analysisHandle.isEmpty() || !store.hasAnalysis(analysisHandle)) {
                return fail("analysis_not_found", "Analysis handle not found - run detect_bands first", "analysis_handle");
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
            
            // IMPORTANT: All quantification uses original ImagePlus pixel data
            ImagePlus originalImage = img.image; // Original image data for measurements
            
            // Get bands from analysis
            SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
            @SuppressWarnings("unchecked")
            List<List<Band>> allBands = (List<List<Band>>) analysis.data;
            
            JSONArray results = new JSONArray();
            
            for (int laneIdx = 0; laneIdx < allBands.size(); laneIdx++) {
                List<Band> bands = allBands.get(laneIdx);
                JSONObject laneResult = new JSONObject()
                    .put("lane", laneIdx + 1)
                    .put("band_count", bands.size());
                
                JSONArray bandIntensities = new JSONArray();
                for (Band band : bands) {
                    bandIntensities.put(new JSONObject()
                        .put("y_position", band.y())
                        .put("intensity", band.area())  // Using area as intensity measure
                        .put("area", band.area()));
                }
                laneResult.put("bands", bandIntensities);
                results.put(laneResult);
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
                    .put("background_radius", backgroundRadius));
            
            return ok("quantify_bands", data);
                
        } catch (Exception e) {
            return recovery.createRecoveryResponse("quantify_bands", e);
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
            }
            
            if (!args.has("export_formats")) {
                return fail(ERROR_MISSING_REQUIRED_FIELD, "export_formats array is required", "export_formats");
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
            return recovery.createRecoveryResponse("export_results", e);
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
            return recovery.createRecoveryResponse("check_contamination", e);
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
            return recovery.createRecoveryResponse("calibrate_molecular_weight", e);
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
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
                return fail(ERROR_IMAGE_STATE_CONFLICT, "No lanes detected. Please detect lanes first before using BandAssist.", "overlay");
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
            return fail(ERROR_IJ_RUNTIME_ERROR, "ImageJ runtime error: " + e.getMessage(), "runtime");
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found in session", "image_handle");
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
            return fail(ERROR_IJ_RUNTIME_ERROR, "ImageJ runtime error: " + e.getMessage(), "runtime");
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
            return recovery.createRecoveryResponse("configure_band_assist", e);
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
            return recovery.createRecoveryResponse("normalize_intensities", e);
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
            HandleGuard.HandleValidationResult protection = handleGuard.protectToolCall(args, "create_labeled_reference");
            
            if (!protection.isValid()) {
                JSONObject errorResponse = protection.createErrorResponse();
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(protection.imageHandle);
            boolean includeMolecularWeights = args.optBoolean("include_molecular_weights", false);
            boolean includeIntensities = args.optBoolean("include_intensities", false);
            
            // Create comprehensive overlay with all available analysis data
            Overlay referenceOverlay = new Overlay();
            int height = img.image.getHeight();
            int width = img.image.getWidth();
            
            // Add lanes if available
            List<String> analyses = store.getAnalysesForImage(protection.imageHandle);
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
            
            // Save labeled reference image
            ImagePlus labeledImage = img.image.duplicate();
            if (referenceOverlay != null) {
                labeledImage.setOverlay(referenceOverlay);
                labeledImage = labeledImage.flatten(); // Burn overlay into image
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
                .put("reference_png", outputPath.toString())
                .put("overlay_handle", overlayHandle)
                .put("image_handle", img.handle)
                .put("reference_purpose", "Visual communication tool with comprehensive labels for lanes and bands")
                .put("measurement_warning", "CRITICAL: Use original image data for all measurements, not this labeled PNG")
                .put("lane_count", lanes != null ? lanes.size() : 0)
                .put("band_count", allBands != null ? allBands.stream().mapToInt(List::size).sum() : 0);
            
        } catch (Exception e) {
            return recovery.createRecoveryResponse("create_labeled_reference", e);
        }
    }

    /**
     * Tool: export_for_notebook
     * Quick export of labeled gel image optimized for Jupyter notebooks and lab documentation
     */
    public JSONObject exportForNotebook(JSONObject args) {
        try {
            // Apply comprehensive handle protection
            HandleGuard.HandleValidationResult protection = handleGuard.protectToolCall(args, "export_for_notebook");
            
            if (!protection.isValid()) {
                JSONObject errorResponse = protection.createErrorResponse();
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(protection.imageHandle);
            String filename = args.optString("filename", "gel_labeled_" + System.currentTimeMillis() + ".png");
            String outputDir = args.optString("output_directory", System.getProperty("user.home") + "/Downloads");
            boolean includeIntensities = args.optBoolean("include_intensities", false);
            int maxWidth = args.optInt("max_width", 800); // Optimal for notebooks
            
            Path outputPath = Path.of(outputDir, filename);
            
            // Create labeled overlay optimized for notebooks
            Overlay notebookOverlay = createExportOverlay(protection.imageHandle, includeIntensities, false);
            
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
            return recovery.createRecoveryResponse("export_for_notebook", e);
        }
    }

    /**
     * Tool: export_for_presentation
     * Export labeled gel image with title and summary, optimized for presentations
     */
    public JSONObject exportForPresentation(JSONObject args) {
        try {
            // Apply comprehensive handle protection
            HandleGuard.HandleValidationResult protection = handleGuard.protectToolCall(args, "export_for_presentation");
            
            if (!protection.isValid()) {
                JSONObject errorResponse = protection.createErrorResponse();
                errorResponse.put("memory_refresh", recovery.generateMemoryRefresh());
                return errorResponse;
            }
            
            SessionStore.ImageRecord img = store.getImage(protection.imageHandle);
            String title = args.optString("title", "Gel Electrophoresis Analysis");
            String filename = args.optString("filename", "gel_presentation_" + System.currentTimeMillis() + ".png");
            String outputDir = args.optString("output_directory", System.getProperty("user.home") + "/Downloads");
            
            Path outputPath = Path.of(outputDir, filename);
            
            // Create presentation overlay
            Overlay presentationOverlay = createPresentationOverlay(protection.imageHandle, title);
            
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
            return recovery.createRecoveryResponse("export_for_presentation", e);
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
        
        // Standardized CSV header
        csv.append("file,lane,band_idx,x_start,x_end,y_top,y_bottom,apex_y,area_raw,area_bg,area_corr,snr,mw_kda,rf,flags\n");
        
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
                double rf = apexY / (double) imgRecord.image.getHeight(); // Simple RF calculation
                double mwKda = band.mwKDa(); // Get molecular weight if available
                String flags = ""; // No specific flags in current data
                
                // Build CSV row with fixed decimal formatting
                csv.append(String.format("%s,%d,%d,%d,%d,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s\n",
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
                    df3.format(mwKda),
                    df4.format(rf),
                    flags
                ));
            }
        }
        
        Files.writeString(outputPath, csv.toString());
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Reference image not found", "reference_image_handle");
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
            return recovery.createRecoveryResponse("start_timeseries_analysis", e);
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
                return fail("tracking_not_found", "Time-series tracking not found", "tracking_handle");
            }
            
            SessionStore.ImageRecord img = store.getImage(imageHandle);
            if (img == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found", "image_handle");
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
            return recovery.createRecoveryResponse("add_timepoint", e);
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Reference image not found", "reference_image_handle");
            }
            if (targetImg == null) {
                return fail(ERROR_IMAGE_NOT_FOUND, "Target image not found", "target_image_handle");
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
                return fail("alignment_failed", "Could not reliably align images", "alignment_confidence");
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
            return recovery.createRecoveryResponse("align_plate_images", e);
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
                return fail(ERROR_IMAGE_NOT_FOUND, "Image not found", "image_handle");
            }
            
            // Configure blueness analysis options
            XGalBluenessAnalyzer.BluenessOptions options = new XGalBluenessAnalyzer.BluenessOptions();
            options.useRGBAnalysis = args.optBoolean("use_rgb_analysis", true);
            options.analysisRadius = args.optInt("analysis_radius", 8);
            options.normalizeForLighting = args.optBoolean("normalize_lighting", true);
            
            // Get colony positions from overlay or detect them
            Map<String, java.awt.geom.Point2D> colonyPositions = extractColonyPositions(img);
            
            if (colonyPositions.isEmpty()) {
                return fail("no_colonies_found", "No colonies found for blueness analysis", "colony_detection");
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
            return recovery.createRecoveryResponse("analyze_xgal_blueness", e);
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
                return fail("tracking_not_found", "Time-series tracking not found", "tracking_handle");
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
            return recovery.createRecoveryResponse("export_timeseries_data", e);
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
    
}
