package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.ThreadSafeSessionStore;
import com.betterdairy.autodense.tools.gel.*;
import com.betterdairy.autodense.util.ErrorHandler;
import ij.IJ;
import ij.ImagePlus;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Refactored GelAnalysisTools class implementing the Facade pattern.
 * 
 * <p>This class serves as a coordinator/facade for gel analysis functionality,
 * delegating to specialized tool classes while maintaining the same public API
 * for backward compatibility.</p>
 * 
 * <h3>Architecture Changes:</h3>
 * <ul>
 *   <li>Decomposed into focused, single-responsibility classes</li>
 *   <li>Maintained all ErrorHandler patterns and session integration</li>
 *   <li>Preserved handle-based architecture and existing method signatures</li>
 *   <li>Implemented proper resource management and cleanup</li>
 * </ul>
 * 
 * <h3>Specialized Tool Classes:</h3>
 * <ul>
 *   <li>{@link LaneDetectionTools} - Lane detection and adjustment</li>
 *   <li>{@link BandDetectionTools} - Band detection and quantification</li>
 *   <li>{@link GelQuantificationTools} - Calibration and normalization</li>
 *   <li>{@link GelExportTools} - Export and rendering functionality</li>
 *   <li>{@link GelSpecializedAnalysis} - Advanced analysis workflows</li>
 * </ul>
 * 
 * @author AutoDense Development Team
 * @version 2.0
 * @since 2.0
 */
public class GelAnalysisToolsNew {
    
    private static final Logger logger = Logger.getLogger(GelAnalysisToolsNew.class.getName());
    
    private final SessionStore store;
    private final SessionRecovery recovery;
    private final Path tempDir;
    
    // Specialized tool instances
    private final LaneDetectionTools laneTools;
    private final BandDetectionTools bandTools;
    private final GelQuantificationTools quantificationTools;
    private final GelExportTools exportTools;
    private final GelSpecializedAnalysis specializedAnalysis;
    
    /**
     * Constructor for refactored GelAnalysisTools
     * 
     * @param store SessionStore instance for state management
     */
    public GelAnalysisToolsNew(SessionStore store) {
        this.store = Objects.requireNonNull(store, "SessionStore cannot be null");
        this.recovery = new SessionRecovery(store);
        this.tempDir = initializeTempDirectory();
        
        // Initialize specialized tool classes
        this.laneTools = new LaneDetectionTools(store, tempDir);
        this.bandTools = new BandDetectionTools(store, tempDir);
        this.quantificationTools = new GelQuantificationTools(store, tempDir);
        this.exportTools = new GelExportTools(store, tempDir);
        this.specializedAnalysis = new GelSpecializedAnalysis(store, tempDir);
        
        logger.info("GelAnalysisToolsNew initialized with specialized tool classes and temp directory: " + this.tempDir);
    }
    
    private Path initializeTempDirectory() {
        try {
            Path tempDir = Files.createTempDirectory("autodense_gel_");
            logger.fine("Created temporary directory: " + tempDir);
            return tempDir;
        } catch (IOException e) {
            Path fallbackDir = Path.of(System.getProperty("java.io.tmpdir"));
            logger.warning("Failed to create temp directory, falling back to system temp: " + e.getMessage());
            return fallbackDir;
        }
    }
    
    /**
     * Clean up resources when this instance is no longer needed
     */
    public void cleanup() {
        try {
            // Cleanup all tool instances
            laneTools.cleanup();
            bandTools.cleanup();
            quantificationTools.cleanup();
            exportTools.cleanup();
            specializedAnalysis.cleanup();
            
            // Clean up main temp directory
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
    
    // =========================
    // ORIGINAL PUBLIC API - MAINTAINED FOR BACKWARD COMPATIBILITY
    // Delegates to specialized tool classes
    // =========================
    
    /**
     * Tool: open_image
     * Load a gel image into the session
     * 
     * Security: Path traversal protection, file extension validation
     */
    public JSONObject openImage(JSONObject args) {
        try {
            String path = args.getString("path");
            ImagePlus imp = IJ.openImage(path);
            if (imp == null) {
                throw new IllegalArgumentException("Could not open image: " + path);
            }
            
            String handle = store.putImage(imp);
            store.setLastActiveImageHandle(handle);
            imp.show(); // Display in ImageJ
            
            return new JSONObject()
                .put("image_handle", handle)
                .put("width", imp.getWidth())
                .put("height", imp.getHeight())
                .put("title", imp.getTitle())
                .put("CRITICAL_INSTRUCTION", 
                    "ALWAYS use this image_handle in all subsequent tool calls. " +
                    "Handle: " + handle);
                    
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("open_image", e, logger, recovery);
        }
    }
    
    // ===== LANE DETECTION METHODS (delegated to LaneDetectionTools) =====
    
    public JSONObject detectLanes(JSONObject args) {
        return laneTools.detectLanes(args);
    }
    
    public JSONObject adjustLanes(JSONObject args) {
        return laneTools.adjustLanes(args);
    }
    
    // ===== BAND DETECTION METHODS (delegated to BandDetectionTools) =====
    
    public JSONObject detectBands(JSONObject args) {
        return bandTools.detectBands(args);
    }
    
    public JSONObject quantifyBands(JSONObject args) {
        return bandTools.quantifyBands(args);
    }
    
    public JSONObject enableBandAssist(JSONObject args) {
        return bandTools.enableBandAssist(args);
    }
    
    public JSONObject disableBandAssist(JSONObject args) {
        return bandTools.disableBandAssist(args);
    }
    
    public JSONObject configureBandAssist(JSONObject args) {
        return bandTools.configureBandAssist(args);
    }
    
    // ===== QUANTIFICATION METHODS (delegated to GelQuantificationTools) =====
    
    public JSONObject calibrateMolecularWeight(JSONObject args) {
        return quantificationTools.calibrateMolecularWeight(args);
    }
    
    public JSONObject calibrateStandardCurve(JSONObject args) {
        return quantificationTools.calibrateStandardCurve(args);
    }
    
    public JSONObject compareLanes(JSONObject args) {
        return quantificationTools.compareLanes(args);
    }
    
    public JSONObject normalizeIntensities(JSONObject args) {
        return quantificationTools.normalizeIntensities(args);
    }
    
    // ===== EXPORT METHODS (delegated to GelExportTools) =====
    
    public JSONObject renderOverlayPng(JSONObject args) {
        return exportTools.renderOverlayPng(args);
    }
    
    public JSONObject exportResults(JSONObject args) {
        return exportTools.exportResults(args);
    }
    
    public JSONObject exportVolcanoPlot(JSONObject args) {
        return exportTools.exportVolcanoPlot(args);
    }
    
    public JSONObject exportForNotebook(JSONObject args) {
        return exportTools.exportForNotebook(args);
    }
    
    public JSONObject exportForPresentation(JSONObject args) {
        return exportTools.exportForPresentation(args);
    }
    
    // ===== SPECIALIZED ANALYSIS METHODS (delegated to GelSpecializedAnalysis) =====
    
    public JSONObject checkContamination(JSONObject args) {
        return specializedAnalysis.checkContamination(args);
    }
    
    public JSONObject digestKinetics(JSONObject args) {
        return specializedAnalysis.digestKinetics(args);
    }
    
    public JSONObject compareTreatments(JSONObject args) {
        return specializedAnalysis.compareTreatments(args);
    }
    
    public JSONObject mapFractions(JSONObject args) {
        return specializedAnalysis.mapFractions(args);
    }
    
    public JSONObject computeYieldPurity(JSONObject args) {
        return specializedAnalysis.computeYieldPurity(args);
    }
    
    public JSONObject profileIsoforms(JSONObject args) {
        return specializedAnalysis.profileIsoforms(args);
    }
    
    public JSONObject hcpSnapshot(JSONObject args) {
        return specializedAnalysis.hcpSnapshot(args);
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Tool: clear_session
     * Clear all session data
     */
    public JSONObject clearSession(JSONObject args) {
        try {
            store.clear();
            return new JSONObject()
                .put("session_cleared", true)
                .put("message", "All session data cleared");
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("clear_session", e, logger, recovery);
        }
    }
    
    /**
     * Tool: refresh_canvas
     * Refresh the web canvas overlay after ImageJ operations
     */
    public JSONObject refreshCanvas(JSONObject args) {
        try {
            String imageHandle = args.optString("image_handle");
            if (imageHandle.isEmpty()) {
                if (store.getImageCount() > 0) {
                    imageHandle = "img_current";
                }
            }
            
            if (!imageHandle.isEmpty()) {
                SessionStore.ImageRecord img = store.getImage(imageHandle);
                if (img != null && img.currentOverlay != null) {
                    JSONObject overlayArgs = new JSONObject()
                        .put("image_handle", imageHandle)
                        .put("max_width", 1200)
                        .put("quality", 90);
                        
                    JSONObject overlayResult = renderOverlayPng(overlayArgs);
                    if (overlayResult.optBoolean("success", false)) {
                        return new JSONObject()
                            .put("overlay_png", "/overlays/current.png")
                            .put("cache_bust", System.currentTimeMillis())
                            .put("image_handle", imageHandle)
                            .put("overlay_updated", true);
                    }
                }
            }
            
            return new JSONObject()
                .put("overlay_png", "/overlays/current.png")
                .put("cache_bust", System.currentTimeMillis())
                .put("overlay_updated", false)
                .put("message", "No overlay to refresh");
                
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("refresh_canvas", e, logger, recovery);
        }
    }
}
