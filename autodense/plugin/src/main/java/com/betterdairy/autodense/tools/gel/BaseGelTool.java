package com.betterdairy.autodense.tools.gel;

import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.tools.HandleGuard;
import com.betterdairy.autodense.util.ErrorHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base class for all gel analysis tools providing shared infrastructure.
 * 
 * <p>This class encapsulates common patterns and dependencies used across
 * all gel analysis tool classes:</p>
 * <ul>
 *   <li>Session management (SessionStore, SessionRecovery)</li>
 *   <li>Handle validation and security</li>
 *   <li>Error handling patterns</li>
 *   <li>Resource management</li>
 *   <li>Parameter validation and clamping</li>
 * </ul>
 * 
 * @author AutoDense Development Team
 * @version 2.0
 * @since 2.0
 */
public abstract class BaseGelTool {
    
    protected final Logger logger;
    protected final SessionStore store;
    protected final SessionRecovery recovery;
    protected final HandleGuard handleGuard;
    protected final Path tempDir;
    
    // Cache for expensive preprocessing operations (rolling ball, CLAHE, etc.)
    protected final Map<String, String> preprocessingCache = new ConcurrentHashMap<>();
    
    /**
     * Constructor for gel analysis tools
     * 
     * @param store SessionStore instance for state management
     * @param tempDir Temporary directory for file operations
     */
    protected BaseGelTool(SessionStore store, Path tempDir) {
        this.store = Objects.requireNonNull(store, "SessionStore cannot be null");
        this.recovery = new SessionRecovery(store);
        this.handleGuard = new HandleGuard(store, recovery);
        this.tempDir = Objects.requireNonNull(tempDir, "Temp directory cannot be null");
        this.logger = Logger.getLogger(this.getClass().getName());
        
        // Log initialization
        logger.info(this.getClass().getSimpleName() + " initialized with temp directory: " + this.tempDir);
    }
    
    /**
     * Create a successful response JSON object
     * 
     * @param tool The tool name
     * @param data The response data
     * @return JSON response object
     */
    protected JSONObject ok(String tool, JSONObject data) {
        return new JSONObject()
            .put("ok", true)
            .put("tool", tool)
            .put("data", data)
            .put("warnings", new JSONArray());
    }
    
    /**
     * Enforce handle discipline - FAIL FAST if image_handle is missing
     * Returns error JSONObject if handle is missing, null if successful
     * 
     * CRITICAL: No silent injection - explicit handles required for data integrity
     */
    protected JSONObject enforceHandleDiscipline(JSONObject args) {
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
     * Clamp integer value to specified range
     * 
     * @param value Value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    protected static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp double value to specified range
     * 
     * @param value Value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped value
     */
    protected static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
}
