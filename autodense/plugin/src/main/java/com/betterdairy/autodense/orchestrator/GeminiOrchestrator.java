package com.betterdairy.autodense.orchestrator;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionLogger;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.plugin.GeminiApiClient;
import com.betterdairy.autodense.plugin.GeminiApiClient.GelAnalysisResponse;
import ij.ImagePlus;
import org.json.JSONObject;
import org.json.JSONArray;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central orchestrator for Gemini-powered gel analysis sessions.
 * Coordinates between Gemini AI planning and ImageJ tool execution
 * while maintaining comprehensive session logs.
 */
public class GeminiOrchestrator {
    
    private final SessionStore sessionStore;
    private final SessionLogger sessionLogger;
    private final GelAnalysisTools analysisTools;
    private final GeminiApiClient geminiClient;
    private final ExecutorService executorService;
    
    private volatile boolean isActive = true;
    private String currentImageHandle = null;
    
    public GeminiOrchestrator(String apiKey) {
        // Initialize core components
        this.sessionStore = new SessionStore();
        this.sessionLogger = new SessionLogger(sessionStore.getSessionId());
        this.analysisTools = new GelAnalysisTools(sessionStore);
        this.geminiClient = new GeminiApiClient(apiKey);
        
        // Single thread executor for sequential processing
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GeminiOrchestrator-" + sessionStore.getSessionId());
            t.setDaemon(false); // Keep session alive
            return t;
        });
        
        // Log orchestrator initialization
        JSONObject initData = new JSONObject()
            .put("session_id", sessionStore.getSessionId())
            .put("gemini_client_initialized", apiKey != null && !apiKey.isEmpty())
            .put("analysis_tools_count", getAvailableToolsCount());
            
        sessionLogger.logSessionEvent("orchestrator_init", 
            "Gemini orchestrator initialized with session logging", initData);
    }
    
    /**
     * Process user command with full conversation and tool execution logging
     */
    public CompletableFuture<OrchestrationResult> processCommand(String userCommand, ImagePlus currentImage) {
        if (!isActive) {
            return CompletableFuture.completedFuture(
                OrchestrationResult.error("Orchestrator is not active"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            // Log user message
            JSONObject userMetadata = new JSONObject()
                .put("has_image", currentImage != null)
                .put("command_length", userCommand.length())
                .put("current_image_handle", currentImageHandle);
                
            sessionLogger.logConversation("user", userCommand, userMetadata);
            
            try {
                // Step 1: Load image if provided
                if (currentImage != null && !sessionStore.hasCurrentImage()) {
                    String imageHandle = sessionStore.putImage(currentImage);
                    this.currentImageHandle = imageHandle;
                    
                    sessionLogger.logSessionEvent("image_loaded", 
                        "New image loaded into session", 
                        new JSONObject()
                            .put("image_handle", imageHandle)
                            .put("width", currentImage.getWidth())
                            .put("height", currentImage.getHeight())
                            .put("title", currentImage.getTitle()));
                }
                
                // Step 2: Get Gemini analysis
                GelAnalysisResponse geminiResponse = callGeminiWithLogging(userCommand, currentImage);
                
                // Step 3: Execute tool calls based on Gemini's analysis
                JSONObject toolResult = executeToolWithLogging(geminiResponse);
                
                // Step 4: Create orchestration result
                OrchestrationResult result = OrchestrationResult.success(
                    geminiResponse, toolResult, System.currentTimeMillis() - startTime);
                
                // Log Gemini response
                JSONObject geminiMetadata = new JSONObject()
                    .put("image_type", geminiResponse.imageType)
                    .put("intent", geminiResponse.intent)
                    .put("action", geminiResponse.action)
                    .put("confidence", geminiResponse.confidence)
                    .put("tool_executed", geminiResponse.action)
                    .put("execution_success", !toolResult.optBoolean("error", false));
                    
                sessionLogger.logConversation("gemini", geminiResponse.analysis, geminiMetadata);
                
                return result;
                
            } catch (Exception e) {
                // Log error with full context
                JSONObject errorContext = new JSONObject()
                    .put("user_command", userCommand)
                    .put("current_image_handle", currentImageHandle)
                    .put("session_statistics", sessionLogger.getSessionStatistics());
                    
                sessionLogger.logError("process_command", e, errorContext);
                
                return OrchestrationResult.error("Failed to process command: " + e.getMessage());
            }
            
        }, executorService);
    }
    
    /**
     * Load image and return handle with logging
     */
    public String loadImage(ImagePlus image) {
        try {
            String handle = sessionStore.putImage(image);
            this.currentImageHandle = handle;
            
            // Log successful image load
            JSONObject imageData = new JSONObject()
                .put("image_handle", handle)
                .put("width", image.getWidth())
                .put("height", image.getHeight())
                .put("title", image.getTitle())
                .put("type", image.getType())
                .put("file_size_bytes", estimateImageSize(image));
                
            sessionLogger.logSessionEvent("image_loaded", 
                "Image successfully loaded into session", imageData);
            
            return handle;
            
        } catch (Exception e) {
            sessionLogger.logError("load_image", e, 
                new JSONObject().put("image_title", image.getTitle()));
            throw e;
        }
    }
    
    /**
     * Execute tool call directly with logging
     */
    public JSONObject executeTool(String toolName, JSONObject parameters) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        JSONObject result = null;
        
        try {
            // Ensure image handle is available
            if (currentImageHandle != null && !parameters.has("image_handle")) {
                parameters.put("image_handle", currentImageHandle);
            }
            
            // Execute the tool
            result = switch (toolName) {
                case "open_image" -> analysisTools.openImage(parameters);
                case "preprocess" -> analysisTools.preprocess(parameters);
                case "detect_lanes" -> analysisTools.detectLanes(parameters);
                case "detect_bands" -> analysisTools.detectBands(parameters);
                case "adjust_lanes" -> analysisTools.adjustLanes(parameters);
                case "quantify_bands" -> analysisTools.quantifyBands(parameters);
                case "render_overlay_png" -> analysisTools.renderOverlayPng(parameters);
                case "export_results" -> analysisTools.exportResults(parameters);
                case "calibrate_molecular_weight" -> analysisTools.calibrateMolecularWeight(parameters);
                case "enable_band_assist" -> analysisTools.enableBandAssist(parameters);
                case "disable_band_assist" -> analysisTools.disableBandAssist(parameters);
                case "configure_band_assist" -> analysisTools.configureBandAssist(parameters);
                case "normalize_intensities" -> analysisTools.normalizeIntensities(parameters);
                case "detect_colonies" -> analysisTools.detectColonies(parameters);
                case "count_colonies_by_color" -> analysisTools.countColoniesByColor(parameters);
                case "measure_colony_sizes" -> analysisTools.measureColonySizes(parameters);
                case "check_contamination" -> analysisTools.checkContamination(parameters);
                case "create_labeled_reference" -> analysisTools.createLabeledReference(parameters);
                case "export_for_notebook" -> analysisTools.exportForNotebook(parameters);
                case "export_for_presentation" -> analysisTools.exportForPresentation(parameters);
                case "export_colony_analysis" -> analysisTools.exportColonyAnalysis(parameters);
                case "clear_session" -> analysisTools.clearSession(parameters);
                default -> new JSONObject().put("error", true).put("message", "Unknown tool: " + toolName);
            };
            
            success = !result.optBoolean("error", false);
            return result;
            
        } catch (Exception e) {
            success = false;
            result = new JSONObject()
                .put("error", true)
                .put("message", e.getMessage())
                .put("error_type", e.getClass().getSimpleName());
            return result;
            
        } finally {
            // Log tool execution
            sessionLogger.logToolCall(toolName, parameters, result, 
                System.currentTimeMillis() - startTime, success);
        }
    }
    
    /**
     * Get comprehensive session information
     */
    public JSONObject getSessionInfo() {
        JSONObject sessionInfo = new JSONObject()
            .put("session_statistics", sessionLogger.getSessionStatistics())
            .put("store_summary", sessionStore.getSummary())
            .put("current_image_handle", currentImageHandle)
            .put("available_tools", getAvailableToolsList())
            .put("orchestrator_active", isActive);
            
        sessionLogger.logSessionEvent("session_info_requested", 
            "Session information retrieved", sessionInfo);
            
        return sessionInfo;
    }
    
    /**
     * Export complete session log
     */
    public JSONObject exportSessionLog(String outputDirectory) {
        try {
            // Force flush all pending log entries
            sessionLogger.flush();
            
            JSONObject exportInfo = new JSONObject()
                .put("session_summary", sessionLogger.generateSessionSummary())
                .put("export_timestamp", Instant.now().toString())
                .put("output_directory", outputDirectory);
                
            sessionLogger.logSessionEvent("log_export", 
                "Session log exported", exportInfo);
                
            return exportInfo;
            
        } catch (Exception e) {
            sessionLogger.logError("export_session_log", e, 
                new JSONObject().put("output_directory", outputDirectory));
            
            return new JSONObject()
                .put("error", true)
                .put("message", "Failed to export session log: " + e.getMessage());
        }
    }
    
    /**
     * Close orchestrator and finalize session
     */
    public void close() {
        if (!isActive) return;
        
        isActive = false;
        
        try {
            sessionLogger.logSessionEvent("orchestrator_shutdown", 
                "Orchestrator closing, finalizing session", 
                sessionLogger.getSessionStatistics());
                
            // Close session logger (flushes all pending entries)
            sessionLogger.closeSession();
            
            // Shutdown executor
            executorService.shutdown();
            
        } catch (Exception e) {
            System.err.println("Error during orchestrator shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Private helper methods
    
    private GelAnalysisResponse callGeminiWithLogging(String userCommand, ImagePlus image) throws Exception {
        long startTime = System.currentTimeMillis();
        int statusCode = 200;
        JSONObject requestData = new JSONObject();
        JSONObject responseData = new JSONObject();
        
        try {
            // Prepare request data (without sensitive info)
            requestData.put("command", userCommand)
                      .put("has_image", image != null)
                      .put("image_dimensions", image != null ? 
                          new JSONObject().put("width", image.getWidth()).put("height", image.getHeight()) : null);
            
            // Call Gemini API
            GelAnalysisResponse response = geminiClient.analyzeGel(userCommand, image);
            
            // Prepare response data
            responseData.put("image_type", response.imageType)
                       .put("intent", response.intent)
                       .put("action", response.action)
                       .put("confidence", response.confidence)
                       .put("parameters", new JSONObject(response.parameters))
                       .put("analysis_length", response.analysis.length());
            
            return response;
            
        } catch (Exception e) {
            statusCode = 500;
            responseData.put("error", e.getMessage());
            throw e;
            
        } finally {
            // Log API call
            sessionLogger.logGeminiApiCall("analyzeGel", requestData, responseData, 
                statusCode, System.currentTimeMillis() - startTime);
        }
    }
    
    private JSONObject executeToolWithLogging(GelAnalysisResponse geminiResponse) {
        // Convert Gemini parameters to tool parameters
        JSONObject toolParameters = new JSONObject(geminiResponse.parameters);
        
        // Ensure image handle is included
        if (currentImageHandle != null) {
            toolParameters.put("image_handle", currentImageHandle);
        }
        
        return executeTool(geminiResponse.action, toolParameters);
    }
    
    private int getAvailableToolsCount() {
        return getAvailableToolsList().length();
    }
    
    private JSONArray getAvailableToolsList() {
        return new JSONArray()
            .put("open_image")
            .put("preprocess")
            .put("detect_lanes")
            .put("detect_bands")
            .put("adjust_lanes")
            .put("quantify_bands")
            .put("render_overlay_png")
            .put("export_results")
            .put("calibrate_molecular_weight")
            .put("enable_band_assist")
            .put("disable_band_assist")
            .put("configure_band_assist")
            .put("normalize_intensities")
            .put("detect_colonies")
            .put("count_colonies_by_color")
            .put("measure_colony_sizes")
            .put("check_contamination")
            .put("create_labeled_reference")
            .put("export_for_notebook")
            .put("export_for_presentation")
            .put("export_colony_analysis")
            .put("clear_session");
    }
    
    private long estimateImageSize(ImagePlus image) {
        // Rough estimate: width * height * channels * bytes_per_pixel
        return (long) image.getWidth() * image.getHeight() * image.getNChannels() * 
               (image.getBytesPerPixel() > 0 ? image.getBytesPerPixel() : 4);
    }
    
    /**
     * Result of orchestration containing Gemini analysis and tool execution results
     */
    public static class OrchestrationResult {
        public final boolean success;
        public final String errorMessage;
        public final GelAnalysisResponse geminiResponse;
        public final JSONObject toolResult;
        public final long executionTimeMs;
        
        private OrchestrationResult(boolean success, String errorMessage, 
                                   GelAnalysisResponse geminiResponse, 
                                   JSONObject toolResult, long executionTimeMs) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.geminiResponse = geminiResponse;
            this.toolResult = toolResult;
            this.executionTimeMs = executionTimeMs;
        }
        
        public static OrchestrationResult success(GelAnalysisResponse geminiResponse, 
                                                 JSONObject toolResult, long executionTimeMs) {
            return new OrchestrationResult(true, null, geminiResponse, toolResult, executionTimeMs);
        }
        
        public static OrchestrationResult error(String errorMessage) {
            return new OrchestrationResult(false, errorMessage, null, null, 0);
        }
        
        public JSONObject toJson() {
            JSONObject result = new JSONObject()
                .put("success", success)
                .put("execution_time_ms", executionTimeMs);
                
            if (success) {
                result.put("gemini_response", new JSONObject()
                    .put("image_type", geminiResponse.imageType)
                    .put("intent", geminiResponse.intent)
                    .put("action", geminiResponse.action)
                    .put("confidence", geminiResponse.confidence)
                    .put("analysis", geminiResponse.analysis));
                result.put("tool_result", toolResult);
            } else {
                result.put("error_message", errorMessage);
            }
            
            return result;
        }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("OrchestrationResult{success=true, action='%s', executionTime=%dms}", 
                    geminiResponse.action, executionTimeMs);
            } else {
                return String.format("OrchestrationResult{success=false, error='%s'}", errorMessage);
            }
        }
    }
}