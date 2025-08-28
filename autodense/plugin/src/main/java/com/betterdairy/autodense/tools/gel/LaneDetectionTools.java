package com.betterdairy.autodense.tools.gel;

import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.img.IJUtils;
import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.util.ErrorHandler;
import com.betterdairy.autodense.validation.SecureToolValidator;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;

/**
 * Tools for lane detection and adjustment in gel electrophoresis images.
 * 
 * <p>This class provides functionality for:</p>
 * <ul>
 *   <li>Automated lane detection with configurable parameters</li>
 *   <li>Manual lane adjustment and refinement</li>
 *   <li>Lane visualization with overlays</li>
 *   <li>Lane spacing analysis and diagnostics</li>
 * </ul>
 * 
 * @author AutoDense Development Team
 * @version 2.0
 * @since 2.0
 */
public class LaneDetectionTools extends BaseGelTool {
    
    /**
     * Constructor for lane detection tools
     * 
     * @param store SessionStore instance for state management
     * @param tempDir Temporary directory for file operations
     */
    public LaneDetectionTools(SessionStore store, Path tempDir) {
        super(store, tempDir);
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
            
            // Detect lanes using existing detector
            List<Lane> lanes = LaneDetector.findLanes(
                img.image, expectedLanes, constantSpacing, 
                laneWidth, gridOffset, true
            );
            
            // Debug logging for lane detection diagnostics
            logLaneDetectionDiagnostics(lanes, expectedLanes);
            
            // Create overlay
            Overlay overlay = createLaneOverlay(lanes, img.image.getHeight());
            
            img.image.setOverlay(overlay);
            img.image.updateAndDraw();
            IJUtils.refresh(img.image);
            
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
     * Tool: adjust_lanes
     * Manually adjust detected lanes
     */
    public JSONObject adjustLanes(JSONObject args) {
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
            
            // Get existing lanes - find the analysis handle for lanes
            String lanesAnalysisHandle = null;
            // Search through analysis records to find lanes analysis for this image
            for (String analysisHandle : store.getAnalysesForImage(imageHandle)) {
                SessionStore.AnalysisRecord analysis = store.getAnalysis(analysisHandle);
                if (analysis != null && "lanes".equals(analysis.type)) {
                    lanesAnalysisHandle = analysisHandle;
                    break;
                }
            }
            
            if (lanesAnalysisHandle == null) {
                return ErrorHandler.handleValidationError("adjust_lanes", 
                    new IllegalArgumentException("No lanes found. Run detect_lanes first."), 
                    logger, recovery);
            }
            
            @SuppressWarnings("unchecked")
            List<Lane> lanes = (List<Lane>) store.getAnalysis(lanesAnalysisHandle).data;
            
            // Parse adjustment parameters
            int laneIndex = args.optInt("lane_index", -1);
            if (laneIndex < 0 || laneIndex >= lanes.size()) {
                return ErrorHandler.handleValidationError("adjust_lanes", 
                    new IllegalArgumentException("Invalid lane_index: " + laneIndex + ". Valid range: 0-" + (lanes.size()-1)), 
                    logger, recovery);
            }
            
            int newX = args.optInt("new_x", lanes.get(laneIndex).xStart());
            int newWidth = args.optInt("new_width", lanes.get(laneIndex).xEnd() - lanes.get(laneIndex).xStart());
            
            // Clamp parameters
            newX = clamp(newX, 0, img.image.getWidth() - 1);
            newWidth = clamp(newWidth, 1, img.image.getWidth() - newX);
            
            // Update lane
            Lane oldLane = lanes.get(laneIndex);
            Lane newLane = new Lane(oldLane.index(), newX, newX + newWidth - 1);
            lanes.set(laneIndex, newLane);
            
            // Create updated overlay
            Overlay overlay = createLaneOverlay(lanes, img.image.getHeight());
            
            img.image.setOverlay(overlay);
            img.image.updateAndDraw();
            IJUtils.refresh(img.image);
            
            String overlayHandle = store.putOverlay(overlay, img.handle);
            String analysisHandle = store.putAnalysis("lanes", lanes, img.handle);
            
            JSONObject data = new JSONObject()
                .put("lane_adjusted", laneIndex)
                .put("new_x", newX)
                .put("new_width", newWidth)
                .put("overlay_handle", overlayHandle)
                .put("analysis_handle", analysisHandle)
                .put("image_handle", img.handle);
            
            return ok("adjust_lanes", data);
                
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("adjust_lanes", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("adjust_lanes", e, logger, recovery);
        }
    }
    
    /**
     * Create lane overlay with visualization
     */
    private Overlay createLaneOverlay(List<Lane> lanes, int imageHeight) {
        Overlay overlay = new Overlay();
        
        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            int x = lane.xStart();
            int width = Math.max(1, lane.xEnd() - lane.xStart() + 1);
            
            // Lane overlay (green) with label
            Roi laneRoi = new Roi(x, 0, width, imageHeight);
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
        
        return overlay;
    }
    
    /**
     * Log lane detection diagnostics
     */
    private void logLaneDetectionDiagnostics(List<Lane> lanes, int expectedLanes) {
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
    }
}
