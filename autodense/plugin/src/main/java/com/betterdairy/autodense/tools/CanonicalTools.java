package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionRecovery;
import com.betterdairy.autodense.util.ErrorHandler;
import org.json.JSONObject;

import java.util.logging.Logger;

/**
 * CANONICAL TOOL SURFACE for Gemini AI interaction.
 * 
 * Provides a streamlined, consistent set of 8 core actions that cover
 * all laboratory image analysis workflows without confusion or duplication.
 * 
 * DESIGN PRINCIPLES:
 * - Complete workflows over granular steps
 * - Consistent naming (verb_noun pattern)
 * - No functional overlap
 * - Clear separation: gel vs plate vs cross-cutting
 * 
 * CANONICAL ACTIONS:
 * 1. analyze_gel - Complete gel analysis workflow
 * 2. adjust_gel - Fine-tune gel analysis
 * 3. export_gel - Export gel results
 * 4. analyze_plate - Complete plate analysis workflow  
 * 5. adjust_plate - Fine-tune plate analysis
 * 6. export_plate - Export plate results
 * 7. preprocess_image - Image enhancement
 * 8. clear_session - Reset analysis state
 */
public final class CanonicalTools {
    
    private static final Logger logger = Logger.getLogger(CanonicalTools.class.getName());
    
    private final GelAnalysisTools gelTools;
    private final AssayOps assayOps;
    private final SessionStore sessionStore;
    private final SessionRecovery recovery;
    
    public CanonicalTools(GelAnalysisTools gelTools, AssayOps assayOps, SessionStore sessionStore) {
        this.gelTools = gelTools;
        this.assayOps = assayOps;
        this.sessionStore = sessionStore;
        this.recovery = new SessionRecovery(sessionStore);
    }
    
    // =============================================================================
    // GEL ANALYSIS CANONICAL ACTIONS
    // =============================================================================
    
    /**
     * CANONICAL: Complete gel electrophoresis analysis workflow
     * Replaces: open_image + detect_lanes + detect_bands + quantify_bands
     */
    public JSONObject analyze_gel(JSONObject args) {
        try {
            JSONObject result = new JSONObject();
            
            // Step 1: Open image (if path provided) or use existing
            if (args.has("image_path")) {
                JSONObject openArgs = new JSONObject().put("path", args.getString("image_path"));
                JSONObject openResult = gelTools.openImage(openArgs);
                if (!openResult.optBoolean("success", false)) {
                    return openResult; // Return error
                }
                result.put("image_loaded", true);
                result.put("image_handle", openResult.optString("image_handle"));
            }
            
            // Step 2: Detect lanes
            JSONObject laneArgs = new JSONObject();
            if (args.has("image_handle")) laneArgs.put("image_handle", args.getString("image_handle"));
            if (args.has("expected_lanes")) laneArgs.put("expected_lanes", args.getInt("expected_lanes"));
            if (args.has("constant_spacing")) laneArgs.put("constant_spacing", args.getBoolean("constant_spacing"));
            
            JSONObject laneResult = gelTools.detectLanes(laneArgs);
            if (!laneResult.optBoolean("success", false)) {
                return laneResult; // Return error
            }
            result.put("lanes_detected", true);
            result.put("lanes_found", laneResult.optInt("lanes_found", 0));
            
            // Step 3: Detect bands
            JSONObject bandArgs = new JSONObject();
            if (args.has("image_handle")) bandArgs.put("image_handle", args.getString("image_handle"));
            if (args.has("sensitivity")) bandArgs.put("sensitivity", args.getDouble("sensitivity"));
            
            JSONObject bandResult = gelTools.detectBands(bandArgs);
            if (!bandResult.optBoolean("success", false)) {
                return bandResult; // Return error  
            }
            result.put("bands_detected", true);
            result.put("bands_total", bandResult.optInt("bands_total", 0));
            
            // Step 4: Quantify bands
            JSONObject quantArgs = new JSONObject();
            if (args.has("image_handle")) quantArgs.put("image_handle", args.getString("image_handle"));
            if (args.has("background_method")) quantArgs.put("background_method", args.getString("background_method"));
            
            JSONObject quantResult = gelTools.quantifyBands(quantArgs);
            result.put("bands_quantified", quantResult.optBoolean("success", false));
            if (quantResult.has("lanes_quantified")) {
                result.put("lanes_quantified", quantResult.getInt("lanes_quantified"));
            }
            
            // Step 5: Generate visual feedback PNG with annotations
            JSONObject pngArgs = new JSONObject();
            if (args.has("image_handle")) pngArgs.put("image_handle", args.getString("image_handle"));
            
            JSONObject pngResult = gelTools.renderOverlayPng(pngArgs);
            if (pngResult.optBoolean("success", false)) {
                result.put("visual_feedback_generated", true);
                if (pngResult.has("exported_files")) {
                    result.put("visual_feedback_files", pngResult.getJSONArray("exported_files"));
                }
            }
            
            result.put("success", true);
            result.put("workflow", "complete_gel_analysis");
            result.put("message", String.format("Analyzed gel: %d lanes, %d bands", 
                result.optInt("lanes_found", 0), result.optInt("bands_total", 0)));
                
            return result;
            
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("analyze_gel", e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError("analyze_gel", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("analyze_gel", e, logger, recovery);
        }
    }
    
    /**
     * CANONICAL: Fine-tune gel analysis parameters
     * Replaces: adjust_lanes + configure_band_assist + enable_band_assist
     */
    public JSONObject adjust_gel(JSONObject args) {
        try {
            JSONObject result = new JSONObject();
            
            // Lane adjustments
            if (args.has("lane_offset") || args.has("lane_width")) {
                JSONObject adjustResult = gelTools.adjustLanes(args);
                result.put("lanes_adjusted", adjustResult.optBoolean("success", false));
            }
            
            // Band assist configuration
            if (args.has("enable_band_assist")) {
                if (args.getBoolean("enable_band_assist")) {
                    JSONObject assistResult = gelTools.enableBandAssist(args);
                    result.put("band_assist_enabled", assistResult.optBoolean("success", false));
                } else {
                    JSONObject assistResult = gelTools.disableBandAssist(args);
                    result.put("band_assist_disabled", assistResult.optBoolean("success", false));
                }
            }
            
            // Band assist parameters
            if (args.has("assist_sensitivity") || args.has("assist_window")) {
                JSONObject configResult = gelTools.configureBandAssist(args);
                result.put("band_assist_configured", configResult.optBoolean("success", false));
            }
            
            result.put("success", true);
            result.put("workflow", "gel_adjustment");
            return result;
            
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("adjust_gel", e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError("adjust_gel", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("adjust_gel", e, logger, recovery);
        }
    }
    
    /**
     * CANONICAL: Export gel analysis results
     * Replaces: export_results + render_overlay_png + create_labeled_reference
     */
    public JSONObject export_gel(JSONObject args) {
        try {
            // Default export formats if none specified
            if (!args.has("formats")) {
                args.put("formats", new org.json.JSONArray().put("csv").put("png"));
            }
            
            return gelTools.exportResults(args);
            
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("export_gel", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_gel", e, logger, recovery);
        }
    }
    
    // =============================================================================
    // PLATE ANALYSIS CANONICAL ACTIONS
    // =============================================================================
    
    /**
     * CANONICAL: Complete plate/colony analysis workflow
     * Replaces: detect_plate + count_colonies + classify_colonies
     */
    public JSONObject analyze_plate(JSONObject args) {
        try {
            JSONObject result = new JSONObject();
            
            // Step 1: Detect plate boundaries
            JSONObject plateArgs = new JSONObject();
            if (args.has("image_handle")) plateArgs.put("image_handle", args.getString("image_handle"));
            if (args.has("dish_diameter_mm")) plateArgs.put("dish_diameter_mm", args.getDouble("dish_diameter_mm"));
            
            JSONObject plateResult = assayOps.detectColonies(plateArgs);
            if (!plateResult.optBoolean("success", false)) {
                return plateResult; // Return error
            }
            result.put("plate_detected", true);
            
            // Step 2: Count colonies by color
            JSONObject countArgs = new JSONObject();
            if (args.has("image_handle")) countArgs.put("image_handle", args.getString("image_handle"));
            if (args.has("color_groups")) countArgs.put("color_groups", args.getJSONArray("color_groups"));
            
            JSONObject countResult = assayOps.detectColonies(countArgs.put("stain", "x-gal"));
            if (!countResult.optBoolean("success", false)) {
                return countResult; // Return error  
            }
            result.put("colonies_counted", true);
            result.put("total_colonies", countResult.optInt("total_colonies", 0));
            
            // Step 3: Classify colonies
            JSONObject classifyArgs = new JSONObject();
            if (args.has("image_handle")) classifyArgs.put("image_handle", args.getString("image_handle"));
            
            JSONObject classifyResult = assayOps.measureColonies(classifyArgs);
            result.put("colonies_classified", classifyResult.optBoolean("success", false));
            
            result.put("success", true);
            result.put("workflow", "complete_plate_analysis");
            result.put("message", String.format("Analyzed plate: %d colonies detected and classified", 
                result.optInt("total_colonies", 0)));
                
            return result;
            
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("analyze_plate", e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError("analyze_plate", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("analyze_plate", e, logger, recovery);
        }
    }
    
    /**
     * CANONICAL: Fine-tune plate analysis
     * Replaces: relabel_colony + bin_colonies + propagate_colony_class
     */
    public JSONObject adjust_plate(JSONObject args) {
        try {
            JSONObject result = new JSONObject();
            
            // Colony binning adjustments
            if (args.has("size_bins") || args.has("color_thresholds")) {
                JSONObject binResult = assayOps.measureColonies(args);
                result.put("colonies_binned", binResult.optBoolean("success", false));
            }
            
            result.put("success", true);
            result.put("workflow", "plate_adjustment");
            return result;
            
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("adjust_plate", e, logger, recovery);
        } catch (IllegalStateException e) {
            return ErrorHandler.handleSessionError("adjust_plate", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("adjust_plate", e, logger, recovery);
        }
    }
    
    /**
     * CANONICAL: Export plate analysis results
     * Replaces: export_colonies + export_colony_analysis + export_for_notebook
     */
    public JSONObject export_plate(JSONObject args) {
        try {
            // Default export formats if none specified
            if (!args.has("formats")) {
                args.put("formats", new org.json.JSONArray().put("csv").put("png"));
            }
            
            return assayOps.export(args);
            
        } catch (IllegalArgumentException e) {
            return ErrorHandler.handleValidationError("export_plate", e, logger, recovery);
        } catch (Exception e) {
            return ErrorHandler.handleUnexpectedError("export_plate", e, logger, recovery);
        }
    }
    
    // =============================================================================
    // CROSS-CUTTING CANONICAL ACTIONS
    // =============================================================================
    
    /**
     * CANONICAL: Image preprocessing and enhancement
     * Replaces: preprocess method with clearer naming
     */
    public JSONObject preprocess_image(JSONObject args) {
        return gelTools.preprocess(args);
    }
    
    /**
     * CANONICAL: Clear analysis session
     * Same as existing clear_session
     */
    public JSONObject clear_session(JSONObject args) {
        return gelTools.clearSession(args);
    }
}