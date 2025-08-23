package com.betterdairy.autodense.orchestrator;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionLogger;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.tools.PlateAnalysisTools;
import com.betterdairy.autodense.tools.ColonyAnalysisTools;
import com.betterdairy.autodense.tools.CanonicalTools;
import com.betterdairy.autodense.plugin.GeminiApiClient;
import com.betterdairy.autodense.plugin.GeminiApiClient.GelAnalysisResponse;
import com.betterdairy.autodense.registry.CapabilityRegistry;
import ij.ImagePlus;
import org.json.JSONObject;
import org.json.JSONArray;
import org.scijava.Context;

import java.time.Instant;
import java.util.List;
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
    private final GelAnalysisTools gelAnalysisTools;
    private final PlateAnalysisTools plateAnalysisTools;
    private final CanonicalTools canonicalTools;
    private final GeminiApiClient geminiClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ExecutorService executorService;
    private final JSONObject generatedIntents; // Generated at startup from Java registry
    
    private volatile boolean isActive = true;
    private String currentImageHandle = null;
    
    public GeminiOrchestrator(String apiKey) {
        // Initialize core components
        this.sessionStore = new SessionStore();
        this.sessionLogger = new SessionLogger(sessionStore.getSessionId());
        this.gelAnalysisTools = new GelAnalysisTools(sessionStore);
        this.plateAnalysisTools = new PlateAnalysisTools(sessionStore);
        this.canonicalTools = new CanonicalTools(gelAnalysisTools, null, plateAnalysisTools, sessionStore);
        this.geminiClient = new GeminiApiClient(apiKey);
        this.capabilityRegistry = new CapabilityRegistry(new Context());
        
        // Generate intents JSON from Java registry (single source of truth)
        this.generatedIntents = capabilityRegistry.exportGelAnalysisIntents();
        
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
            .put("analysis_tools_count", getAvailableToolsCount())
            .put("intents_generated", generatedIntents != null)
            .put("intents_count", generatedIntents != null ? 
                generatedIntents.optJSONObject("intents", new JSONObject()).length() : 0);
            
        sessionLogger.logSessionEvent("orchestrator_init", 
            "Gemini orchestrator initialized with generated intents from Java registry", initData);
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
            // ARCHITECTURAL GUARDRAIL: Handle injection ONLY happens here in GeminiOrchestrator
            // All other components must receive explicit handles - no magic injection elsewhere
            if (!parameters.has("image_handle") || parameters.isNull("image_handle")) {
                String injectedHandle = injectLastActiveImageHandle(toolName);
                if (injectedHandle != null) {
                    parameters.put("image_handle", injectedHandle);
                } else {
                    throw new IllegalArgumentException("image_handle required and no active image in session");
                }
            }
            
            // Auto-inject overlay_handle if tool typically works with overlays
            if (!parameters.has("overlay_handle") && toolNeedsOverlayHandle(toolName)) {
                String injectedOverlayHandle = injectLastActiveOverlayHandle(toolName);
                if (injectedOverlayHandle != null) {
                    parameters.put("overlay_handle", injectedOverlayHandle);
                }
            }
            
            // Execute the tool - CANONICAL FIRST, then backward compatibility aliases
            result = switch (toolName) {
                // =============================================================================
                // CANONICAL TOOLS (Primary interface for Gemini)
                // =============================================================================
                case "analyze_gel" -> canonicalTools.analyze_gel(parameters);
                case "adjust_gel" -> canonicalTools.adjust_gel(parameters);
                case "export_gel" -> canonicalTools.export_gel(parameters);
                case "analyze_plate" -> canonicalTools.analyze_plate(parameters);
                case "adjust_plate" -> canonicalTools.adjust_plate(parameters);
                case "export_plate" -> canonicalTools.export_plate(parameters);
                case "preprocess_image" -> canonicalTools.preprocess_image(parameters);
                case "clear_session" -> canonicalTools.clear_session(parameters);
                
                // =============================================================================
                // BACKWARD COMPATIBILITY ALIASES (Deprecated - Log warnings)
                // =============================================================================
                case "open_image" -> { 
                    sessionLogger.warn("deprecated_tool", "open_image deprecated, use analyze_gel with image_path");
                    yield gelAnalysisTools.openImage(parameters);
                }
                case "preprocess" -> { 
                    sessionLogger.warn("deprecated_tool", "preprocess deprecated, use preprocess_image");
                    yield gelAnalysisTools.preprocess(parameters);
                }
                case "detect_lanes" -> { 
                    sessionLogger.warn("deprecated_tool", "detect_lanes deprecated, use analyze_gel");
                    yield gelAnalysisTools.detectLanes(parameters);
                }
                case "detect_bands" -> { 
                    sessionLogger.warn("deprecated_tool", "detect_bands deprecated, use analyze_gel");
                    yield gelAnalysisTools.detectBands(parameters);
                }
                case "adjust_lanes" -> { 
                    sessionLogger.warn("deprecated_tool", "adjust_lanes deprecated, use adjust_gel");
                    yield gelAnalysisTools.adjustLanes(parameters);
                }
                case "quantify_bands" -> { 
                    sessionLogger.warn("deprecated_tool", "quantify_bands deprecated, use analyze_gel");
                    yield gelAnalysisTools.quantifyBands(parameters);
                }
                case "render_overlay_png" -> { 
                    sessionLogger.warn("deprecated_tool", "render_overlay_png deprecated, use export_gel");
                    yield gelAnalysisTools.renderOverlayPng(parameters);
                }
                case "export_results" -> { 
                    sessionLogger.warn("deprecated_tool", "export_results deprecated, use export_gel");
                    yield gelAnalysisTools.exportResults(parameters);
                }
                case "calibrate_molecular_weight" -> { 
                    sessionLogger.warn("deprecated_tool", "calibrate_molecular_weight deprecated, use adjust_gel");
                    yield gelAnalysisTools.calibrateMolecularWeight(parameters);
                }
                case "enable_band_assist" -> { 
                    sessionLogger.warn("deprecated_tool", "enable_band_assist deprecated, use adjust_gel");
                    yield gelAnalysisTools.enableBandAssist(parameters);
                }
                case "disable_band_assist" -> { 
                    sessionLogger.warn("deprecated_tool", "disable_band_assist deprecated, use adjust_gel");
                    yield gelAnalysisTools.disableBandAssist(parameters);
                }
                case "configure_band_assist" -> { 
                    sessionLogger.warn("deprecated_tool", "configure_band_assist deprecated, use adjust_gel");
                    yield gelAnalysisTools.configureBandAssist(parameters);
                }
                case "normalize_intensities" -> { 
                    sessionLogger.warn("deprecated_tool", "normalize_intensities deprecated, use adjust_gel");
                    yield gelAnalysisTools.normalizeIntensities(parameters);
                }
                
                // Colony analysis aliases
                case "detect_plate" -> { 
                    sessionLogger.warn("deprecated_tool", "detect_plate deprecated, use analyze_plate");
                    yield ColonyAnalysisTools.detectPlate(parameters, sessionStore);
                }
                case "count_colonies" -> { 
                    sessionLogger.warn("deprecated_tool", "count_colonies deprecated, use analyze_plate");
                    yield ColonyAnalysisTools.countColonies(parameters, sessionStore);
                }
                case "classify_colonies" -> { 
                    sessionLogger.warn("deprecated_tool", "classify_colonies deprecated, use analyze_plate");
                    yield ColonyAnalysisTools.classifyColonies(parameters, sessionStore);
                }
                case "bin_colonies" -> { 
                    sessionLogger.warn("deprecated_tool", "bin_colonies deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.binColonies(parameters, sessionStore);
                }
                case "normalize_colonies" -> { 
                    sessionLogger.warn("deprecated_tool", "normalize_colonies deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.normalizeColonies(parameters, sessionStore);
                }
                case "export_colonies" -> { 
                    sessionLogger.warn("deprecated_tool", "export_colonies deprecated, use export_plate");
                    yield ColonyAnalysisTools.exportColonies(parameters, sessionStore);
                }
                case "enable_colony_assist" -> { 
                    sessionLogger.warn("deprecated_tool", "enable_colony_assist deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.enableColonyAssist(parameters, sessionStore);
                }
                case "disable_colony_assist" -> { 
                    sessionLogger.warn("deprecated_tool", "disable_colony_assist deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.disableColonyAssist(parameters, sessionStore);
                }
                case "colony_assist_click" -> { 
                    sessionLogger.warn("deprecated_tool", "colony_assist_click deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.colonyAssistClick(parameters, sessionStore);
                }
                case "propagate_colony_class" -> { 
                    sessionLogger.warn("deprecated_tool", "propagate_colony_class deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.propagateColonyClass(parameters, sessionStore);
                }
                case "relabel_colony" -> { 
                    sessionLogger.warn("deprecated_tool", "relabel_colony deprecated, use adjust_plate");
                    yield ColonyAnalysisTools.relabelColony(parameters, sessionStore);
                }
                
                // Legacy plate analysis tools - mostly redundant  
                case "detect_colonies" -> { 
                    sessionLogger.warn("deprecated_tool", "detect_colonies deprecated, use analyze_plate");
                    yield plateAnalysisTools.detectColonies(parameters);
                }
                case "count_colonies_by_color" -> { 
                    sessionLogger.warn("deprecated_tool", "count_colonies_by_color deprecated, use analyze_plate");
                    yield plateAnalysisTools.countColoniesByColor(parameters);
                }
                case "measure_colony_sizes" -> { 
                    sessionLogger.warn("deprecated_tool", "measure_colony_sizes deprecated, use analyze_plate");
                    yield plateAnalysisTools.measureColonySizes(parameters);
                }
                case "check_contamination" -> { 
                    sessionLogger.warn("deprecated_tool", "check_contamination deprecated, use analyze_plate");
                    yield plateAnalysisTools.checkContamination(parameters);
                }
                case "create_labeled_reference" -> { 
                    sessionLogger.warn("deprecated_tool", "create_labeled_reference deprecated, use export_plate");
                    yield plateAnalysisTools.createLabeledReference(parameters);
                }
                case "export_for_notebook" -> { 
                    sessionLogger.warn("deprecated_tool", "export_for_notebook deprecated, use export_plate");
                    yield plateAnalysisTools.exportForNotebook(parameters);
                }
                case "export_for_presentation" -> { 
                    sessionLogger.warn("deprecated_tool", "export_for_presentation deprecated, use export_plate");
                    yield plateAnalysisTools.exportForPresentation(parameters);
                }
                case "export_colony_analysis" -> { 
                    sessionLogger.warn("deprecated_tool", "export_colony_analysis deprecated, use export_plate");
                    yield plateAnalysisTools.exportColonyAnalysis(parameters);
                }
                
                default -> new JSONObject()
                    .put("error", true)
                    .put("message", "Unknown tool: " + toolName + ". Use canonical actions: analyze_gel, adjust_gel, export_gel, analyze_plate, adjust_plate, export_plate, preprocess_image, clear_session");
            };
            // Record last active image handle if returned by tool
            if (result != null && result.has("image_handle")) {
                String handle = result.optString("image_handle", null);
                if (handle != null) {
                    sessionStore.setLastActiveImageHandle(handle);
                    this.currentImageHandle = handle;
                }
            }
            
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
            
            // Create structured prompt (business logic now in orchestrator)
            String systemPrompt = createSystemPrompt();
            String analysisPrompt = createAnalysisPrompt(userCommand);
            String fullPrompt = systemPrompt + "\n\n" + analysisPrompt;
            
            // Call pure HTTP client
            JSONObject rawResponse = geminiClient.sendRequest(fullPrompt, image);
            
            // Parse response (business logic now in orchestrator)
            GelAnalysisResponse response = parseGeminiResponse(rawResponse, userCommand);
            
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
        
        // Before dispatch: ensure image_handle is present or inject last active
        if (!toolParameters.has("image_handle") || toolParameters.isNull("image_handle")) {
            String last = (currentImageHandle != null) ? currentImageHandle : sessionStore.getMostRecentImageHandle();
            if (last != null && sessionStore.hasImage(last)) {
                toolParameters.put("image_handle", last);
                sessionLogger.logSessionEvent(
                    "tool_call_warning",
                    "Injected missing image_handle: " + last,
                    new JSONObject().put("injected_image_handle", last).put("tool", geminiResponse.action)
                );
            } else {
                throw new IllegalArgumentException("image_handle required and no active image in session");
            }
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
    
    // Business logic methods (moved from GeminiApiClient)
    
    private String createSystemPrompt() {
        // Get dynamic capability summary
        String capabilities = capabilityRegistry.generateCapabilitySummary();
        
        return String.format("""
            You are an expert laboratory image analysis AI assistant. You analyze:
            1) Gel electrophoresis (SDS-PAGE, DNA gels) - lanes, bands, molecular weights
            2) Agar plates with microbial colonies (yeast, bacteria) - colony counting, color analysis
            
            %s
            
            FIRST: Identify the image type by examining visual characteristics:
            - Gel: Dark background, vertical lanes, horizontal bands, ladder patterns
            - Agar plate: Circular plate, scattered colonies, various colors, growth patterns
            
            For GEL ANALYSIS, respond with this JSON format:
            {
                "image_type": "gel",
                "intent": "lane_detection|band_detection|quantification|calibration|adjustment|normalization",
                "action": "detect_lanes|detect_bands|quantify_bands|calibrate_molecular_weight|adjust_lanes|normalize_intensities",
                "parameters": {
                    "lane_count": 12,
                    "grid_offset": -0.05,
                    "lane_width": 0.55,
                    "sensitivity": 0.7,
                    "background_method": "median",
                    "ladder_type": "protein",
                    "ladder_lane": 1,
                    "normalization_method": "total_lane"
                },
                "analysis": "Description of lanes, bands, and gel quality",
                "confidence": 0.9
            }
            
            For AGAR PLATE ANALYSIS, respond with this JSON format:
            {
                "image_type": "agar_plate",
                "intent": "colony_detection|colony_counting|color_analysis|size_analysis|contamination_check",
                "action": "detect_colonies|count_colonies_by_color|measure_colony_sizes|check_contamination",
                "parameters": {
                    "min_colony_size": 5,
                    "max_colony_size": 200,
                    "color_groups": ["white", "pink", "red", "blue", "green", "yellow"],
                    "sensitivity": 0.8,
                    "edge_detection": "adaptive"
                },
                "analysis": "Description of colony count, colors, sizes, and distribution",
                "confidence": 0.9
            }
            
            Always examine the image carefully and determine the correct analysis type.
            For gels: count lanes and bands. For plates: count colonies by color and size.
        """, capabilities);
    }
    
    private String createAnalysisPrompt(String userCommand) {
        return String.format("""
            You are analyzing a laboratory image that has been provided with this request.
            The image is already loaded in the system and you have full access to analyze it.
            
            User Command: "%s"
            
            ANALYSIS WORKFLOW:
            1) IDENTIFY IMAGE TYPE: Look at the image and determine if it's a gel or agar plate
            2) EXAMINE DETAILS: 
               - For gels: Count lanes, identify bands, assess gel quality
               - For agar plates: Count colonies, identify colors, assess sizes and distribution
            3) EXTRACT PARAMETERS: Parse user command for specific requirements
            4) RETURN JSON: Use the appropriate format for the detected image type
            
            IMPORTANT: 
            - You ARE analyzing the actual laboratory image (not a simulation)
            - The image is provided with every request for your vision analysis
            - Return a JSON response that will trigger the appropriate ImageJ analysis
            - Include "image_type" field so the system knows which analysis pipeline to use
            
            COMMON COMMANDS:
            Gel: "Detect 12 lanes", "Find protein bands", "Quantify lane 3"
            Agar: "Count all colonies", "Count pink colonies only", "Measure colony sizes"
            
            Analyze the provided image and respond with the appropriate JSON format.
        """, userCommand);
    }
    
    private GelAnalysisResponse parseGeminiResponse(JSONObject rawResponse, String originalCommand) throws Exception {
        JSONArray candidates = rawResponse.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new RuntimeException("No response candidates from Gemini");
        }
        
        JSONObject candidate = candidates.getJSONObject(0);
        JSONObject content = candidate.optJSONObject("content");
        if (content == null) {
            throw new RuntimeException("No content in Gemini response");
        }
        
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            throw new RuntimeException("No parts in Gemini response");
        }
        
        String responseText = parts.getJSONObject(0).optString("text", "");
        if (responseText.isEmpty()) {
            throw new RuntimeException("Empty response text from Gemini");
        }
        
        // Clean and parse JSON response
        responseText = responseText.trim();
        if (responseText.startsWith("```json")) {
            responseText = responseText.substring(7);
        }
        if (responseText.endsWith("```")) {
            responseText = responseText.substring(0, responseText.length() - 3);
        }
        responseText = responseText.trim();
        
        try {
            JSONObject parsedResponse = new JSONObject(responseText);
            
            GelAnalysisResponse response = new GelAnalysisResponse();
            response.imageType = parsedResponse.optString("image_type", "gel");
            response.intent = parsedResponse.optString("intent", "unknown");
            response.action = parsedResponse.optString("action", "detect_lanes");
            response.analysis = parsedResponse.optString("analysis", "Analysis completed");
            response.confidence = parsedResponse.optDouble("confidence", 0.8);
            response.originalCommand = originalCommand;
            
            // Convert parameters JSONObject to Map
            JSONObject params = parsedResponse.optJSONObject("parameters");
            if (params != null) {
                for (String key : params.keySet()) {
                    response.parameters.put(key, params.get(key));
                }
            }
            
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini JSON response: " + responseText, e);
        }
    }
    
    /**
     * Get the generated intents JSON (single source of truth from Java registry)
     * Replaces static gel_analysis_intents.json file
     */
    public JSONObject getGeneratedIntents() {
        return generatedIntents;
    }
    
    /**
     * Export the generated intents to a file for debugging/inspection
     */
    public void exportGeneratedIntentsToFile(String filePath) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(filePath), 
                generatedIntents.toString(2).getBytes());
            System.out.println("Generated intents exported to: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to export generated intents: " + e.getMessage());
        }
    }
    
    /**
     * ARCHITECTURAL GUARDRAIL: Handle injection methods - only used in GeminiOrchestrator
     * These methods should NEVER be called from tool implementations - tools must receive explicit handles
     */
    
    private String injectLastActiveImageHandle(String toolName) {
        String handle = (currentImageHandle != null) ? currentImageHandle : sessionStore.getMostRecentImageHandle();
        if (handle != null && sessionStore.hasImage(handle)) {
            sessionLogger.logSessionEvent(
                "handle_injection",
                "Auto-injected image_handle for user convenience (orchestrator-only operation)",
                new JSONObject()
                    .put("injected_image_handle", handle)
                    .put("tool", toolName)
                    .put("architectural_note", "Tools outside orchestrator must provide explicit handles")
            );
            return handle;
        }
        return null;
    }
    
    private String injectLastActiveOverlayHandle(String toolName) {
        // Get most recent overlay for current image
        String currentImage = (currentImageHandle != null) ? currentImageHandle : sessionStore.getMostRecentImageHandle();
        if (currentImage != null) {
            List<String> overlays = sessionStore.getOverlaysForImage(currentImage);
            if (!overlays.isEmpty()) {
                String overlayHandle = overlays.get(overlays.size() - 1); // Most recent overlay
                sessionLogger.logSessionEvent(
                    "handle_injection",
                    "Auto-injected overlay_handle for user convenience (orchestrator-only operation)",
                    new JSONObject()
                        .put("injected_overlay_handle", overlayHandle)
                        .put("image_handle", currentImage)
                        .put("tool", toolName)
                        .put("architectural_note", "Tools outside orchestrator must provide explicit handles")
                );
                return overlayHandle;
            }
        }
        return null;
    }
    
    private boolean toolNeedsOverlayHandle(String toolName) {
        // Tools that typically work with existing overlays and benefit from auto-injection
        return switch (toolName) {
            case "adjust_gel", "export_gel", "adjust_plate", "export_plate" -> true;
            case "render_overlay_png", "adjust_lanes" -> true; // Legacy deprecated tools
            default -> false;
        };
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