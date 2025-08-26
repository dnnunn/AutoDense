package com.betterdairy.autodense.orchestrator;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionLogger;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.tools.AssayOps;
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
    private final AssayOps assayOps;
    private final CanonicalTools canonicalTools;
    private final GeminiApiClient geminiClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ExecutorService executorService;
    private final JSONObject generatedIntents; // Generated at startup from Java registry
    
    private volatile boolean isActive = true;
    private String currentImageHandle = null;
    private boolean registryLoaded = false;
    
    public GeminiOrchestrator(String apiKey) {
        // Validate API key before initializing components
        if (!isValidApiKey(apiKey)) {
            throw new IllegalArgumentException(
                "❌ Invalid Gemini API key provided. Please:\n" +
                "1. Get a valid API key from https://makersuite.google.com/app/apikey\n" +
                "2. Set it via: export GEMINI_API_KEY=your_actual_key\n" +
                "3. Or create api-config.properties with: GEMINI_API_KEY=your_actual_key\n" +
                "4. Restart the application\n" +
                "Current key: '" + (apiKey != null ? apiKey.substring(0, Math.min(apiKey.length(), 10)) + "..." : "null") + "'"
            );
        }
        
        // Initialize core components
        this.sessionStore = new SessionStore();
        this.sessionLogger = new SessionLogger(sessionStore.getSessionId());
        this.gelAnalysisTools = new GelAnalysisTools(sessionStore);
        this.assayOps = new AssayOps(sessionStore);
        this.canonicalTools = new CanonicalTools(gelAnalysisTools, assayOps, sessionStore);
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
                // SPECIAL RESPONSE TYPES (Not actual tools)
                // =============================================================================
                case "clarification_needed" -> new JSONObject()
                    .put("success", true)
                    .put("message", parameters.optString("question", "Clarification needed"))
                    .put("response_type", "clarification");
                case "plan_executed" -> new JSONObject()
                    .put("success", true)
                    .put("message", "Plan executed successfully")
                    .put("response_type", "plan_result")
                    .put("artifacts", parameters.optString("artifacts", "{}"));
                case "registry.list_tools" -> new JSONObject()
                    .put("success", true)
                    .put("message", "Registry loaded")
                    .put("response_type", "registry")
                    .put("tools", getAvailableToolsList());
                
                // =============================================================================
                // UI OPERATIONS (Web Interface Integration)
                // =============================================================================
                case "ui.refresh_canvas" -> gelAnalysisTools.refreshCanvas(parameters);
                
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
                case "map_fractions" -> gelAnalysisTools.mapFractions(parameters);
                case "compute_yield_purity" -> gelAnalysisTools.computeYieldPurity(parameters);
                case "profile_isoforms" -> gelAnalysisTools.profileIsoforms(parameters);
                case "hcp_snapshot" -> gelAnalysisTools.hcpSnapshot(parameters);
                case "compare_treatments" -> gelAnalysisTools.compareTreatments(parameters);
                case "digest_kinetics" -> gelAnalysisTools.digestKinetics(parameters);
                case "normalize_intensities" -> { 
                    sessionLogger.warn("deprecated_tool", "normalize_intensities deprecated, use adjust_gel");
                    yield gelAnalysisTools.normalizeIntensities(parameters);
                }
                
                // =============================================================================
                // MISSING GEL ANALYSIS TOOLS (Now properly wired)
                // =============================================================================
                case "calibrate_standard_curve" -> gelAnalysisTools.calibrateStandardCurve(parameters);
                case "compare_lanes" -> gelAnalysisTools.compareLanes(parameters);
                case "export_volcano_plot" -> gelAnalysisTools.exportVolcanoPlot(parameters);
                
                // =============================================================================
                // ASSAY OPERATIONS (Consolidated Colony/Plate Analysis)
                // =============================================================================
                // Core assay operations with parameterized routing
                case "detect_colonies" -> assayOps.detectColonies(parameters);
                case "count_colonies_by_color" -> assayOps.detectColonies(parameters.put("stain", "x-gal"));
                case "count_colonies" -> assayOps.detectColonies(parameters);
                case "measure_colony_sizes" -> assayOps.measureColonies(parameters);
                case "classify_colonies" -> assayOps.measureColonies(parameters);
                case "export_colonies" -> assayOps.export(parameters);
                case "export_colony_analysis" -> assayOps.export(parameters);
                
                // Legacy plate/colony tool compatibility routing through AssayOps
                case "detect_plate" -> assayOps.detectColonies(parameters);
                case "bin_colonies" -> assayOps.measureColonies(parameters);
                case "normalize_colonies" -> assayOps.measureColonies(parameters);
                case "check_contamination" -> assayOps.measureColonies(parameters);
                case "create_labeled_reference" -> assayOps.annotate(parameters);
                case "export_for_notebook" -> assayOps.export(parameters);
                case "export_for_presentation" -> assayOps.export(parameters);
                case "export_detailed_features" -> assayOps.export(parameters);
                
                // Colony assist features routed through AssayOps annotation system
                case "enable_colony_assist" -> assayOps.annotate(parameters.put("mode", "assist"));
                case "disable_colony_assist" -> assayOps.annotate(parameters.put("mode", "normal"));
                case "colony_assist_click" -> assayOps.annotate(parameters.put("action", "click"));
                case "propagate_colony_class" -> assayOps.annotate(parameters.put("action", "propagate"));
                case "relabel_colony" -> assayOps.annotate(parameters.put("action", "relabel"));
                
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
            
            // AUTO-GENERATE VISUAL FEEDBACK: After detection tools, automatically render PNG
            if (success && shouldAutoGenerateVisualFeedback(toolName)) {
                try {
                    JSONObject pngArgs = new JSONObject();
                    if (parameters.has("image_handle")) {
                        pngArgs.put("image_handle", parameters.getString("image_handle"));
                    }
                    
                    JSONObject pngResult = gelAnalysisTools.renderOverlayPng(pngArgs);
                    if (pngResult.optBoolean("success", false)) {
                        // Add visual feedback info to original result
                        result.put("visual_feedback_generated", true);
                        if (pngResult.has("exported_files")) {
                            result.put("visual_feedback_files", pngResult.getJSONArray("exported_files"));
                        }
                        
                        sessionLogger.logSessionEvent("auto_visual_feedback", 
                            "Automatically generated PNG overlay after " + toolName,
                            new JSONObject()
                                .put("original_tool", toolName)
                                .put("png_files", pngResult.optJSONArray("exported_files")));
                    }
                } catch (Exception e) {
                    // Don't fail the original tool if PNG generation fails
                    sessionLogger.logError("auto_visual_feedback", e, 
                        new JSONObject().put("original_tool", toolName));
                }
            }
            
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
            
            // Create contract-based prompt using new system
            String systemPrompt = createSystemPrompt();
            
            // CRITICAL: Check if we need to enforce registry handshake
            String finalPrompt = systemPrompt;
            if (!registryLoaded && !isPresetWorkflow(userCommand)) {
                finalPrompt += "\n\nREJECT: Session lacks registry. You MUST call registry.list_tools first before any planning.";
            } else {
                finalPrompt += "\n\nUser command: " + userCommand;
            }
            
            // Call pure HTTP client with contract-based prompt
            JSONObject rawResponse = geminiClient.sendRequest(finalPrompt, image);
            
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
        
        // Special handling for response types that aren't real tools
        if ("clarification_needed".equals(geminiResponse.action)) {
            toolParameters.put("question", geminiResponse.analysis);
            return executeTool(geminiResponse.action, toolParameters);
        }
        if ("plan_executed".equals(geminiResponse.action)) {
            // Extract artifacts from parameters and pass them to the handler
            String artifacts = toolParameters.optString("artifacts", "{}");
            toolParameters.put("artifacts", artifacts);
            return executeTool(geminiResponse.action, toolParameters);
        }
        if ("registry.list_tools".equals(geminiResponse.action)) {
            // Registry loading doesn't need image handles
            return executeTool(geminiResponse.action, toolParameters);
        }
        
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
            .put("map_fractions")
            .put("compute_yield_purity")
            .put("profile_isoforms")
            .put("hcp_snapshot")
            .put("compare_treatments")
            .put("digest_kinetics")
            .put("normalize_intensities");
    }
    
    private long estimateImageSize(ImagePlus image) {
        // Rough estimate: width * height * channels * bytes_per_pixel
        return (long) image.getWidth() * image.getHeight() * image.getNChannels() * 
               (image.getBytesPerPixel() > 0 ? image.getBytesPerPixel() : 4);
    }
    
    // Business logic methods (moved from GeminiApiClient)
    
    private String createSystemPrompt() {
        // Get current session context
        String imageContext = "";
        if (currentImageHandle != null && sessionStore.hasImage(currentImageHandle)) {
            imageContext = "\n\n📸 CURRENT SESSION STATE:\n" +
                "- Image loaded: YES (handle: " + currentImageHandle + ")\n" +
                "- Image ready for analysis - you can proceed with workflows\n" +
                "- NO need to ask for image paths - use loaded image directly\n";
        } else {
            imageContext = "\n\n📸 CURRENT SESSION STATE:\n" +
                "- Image loaded: NO\n" +
                "- User must provide an image before analysis\n";
        }
        
        return """
            You are an orchestration engine. You NEVER analyze images yourself and you NEVER describe what you "see".
            """ + imageContext + """
            
            CRITICAL: You must choose between TWO execution modes - PRESET vs AD-HOC:
            
            ✅ MODE 1: PRESET WORKFLOWS (Preferred - Slim, Reliable, No Prompting)
            Triggers: "blue colonies", "x-gal", "gel lanes", "sds page", "densitometry"
            Response: Single tool call: workflows.preset with name="PRESET_NAME"
            Available:
            - SDS_PAGE_DENSITOMETRY: For gel analysis with lanes/bands
            - COLONY_BLUE_SCORING: For X-gal blue/white colony analysis
            
            ⚠️ MODE 2: AD-HOC PLANNING (Fallback - Custom workflows only)
            Triggers: Complex/unusual requests not covered by presets
            Requirements:
            1. MANDATORY first call: registry.list_tools (loads tool registry)
            2. Build custom plan using ONLY loaded registry tools
            3. Execute plan step by step with full error handling
            
            🚨 REGISTRY ENFORCEMENT:
            - Session without registry + non-preset request = IMMEDIATE REJECT
            - Force: "You must call registry.list_tools first before any planning"
            - This eliminates all "Gemini didn't know tools" bugs
            
            DECISION TREE:
            User Request → Is Preset? → YES: workflows.preset 
                       → NO: Registry Loaded? → YES: Ad-hoc plan
                                              → NO: REJECT + force registry.list_tools

            Rules:
            - No vision. Do not infer from image pixels; call ImageJ tools to measure, segment, annotate, or export.
            - Only emit JSON tool calls using the schema below.
            - Each step must specify: tool_id, inputs, rationale (one short sentence), and on_fail (retry/backoff or alternate tool).
            - You MUST call `registry.list_tools` at the start of every session and cache the result.
            - You are an ImageJ/Fiji expert: prefer ImageJ tools for image operations; rely on preset workflows when applicable.

            Output format: ALWAYS JSON with one of:
            { "action": "list_tools" }
            { "action": "run", "plan": [ { "tool_id": "...", "inputs": {...}, "rationale": "...", "on_fail": {...} }, ... ] }
            { "action": "ask", "question": "..." }

            Tool-call JSON Contract:
            {
              "action": "run",
              "plan": [
                {
                  "tool_id": "imagej.open_image",
                  "inputs": { "path": "sandbox:/inputs/plate.jpg" },
                  "rationale": "Load the plate image",
                  "on_fail": { "retry": 1, "next_tool": "imagej.open_image_with_bioformats" }
                },
                {
                  "tool_id": "imagej.assay.detect_colonies",
                  "inputs": { "stain": "x-gal", "min_size": 5.0, "max_size": 1000.0, "plate_layout": 96 },
                  "rationale": "Detect and classify X-gal blue/white colonies",
                  "on_fail": { "retry": 1, "next_tool": "imagej.assay.detect_colonies" }
                },
                {
                  "tool_id": "imagej.overlay.annotate",
                  "inputs": {
                    "labels": "colony_id,blue_index,size_bin",
                    "stroke_px": 2
                  },
                  "rationale": "Draw ROIs and labels to overlay only (non-destructive)",
                  "on_fail": { "retry": 0 }
                },
                {
                  "tool_id": "ui.refresh_canvas",
                  "inputs": { "refresh_reason": "post_annotation" },
                  "rationale": "Force a visual update after changes",
                  "on_fail": { "retry": 0 }
                },
                {
                  "tool_id": "export.results",
                  "inputs": { "tables": ["colonies.csv"], "images": ["overlay.png"] },
                  "rationale": "Save outputs for the user",
                  "on_fail": { "retry": 0 }
                }
              ]
            }

            Policy: Auto‑execute by default. Only switch to { "action": "ask" } if the user's request cannot be satisfied without one missing parameter (e.g., no image path, or ambiguous assay type).

            Capability Registry:
            {
              "tools": [
                {
                  "id": "registry.list_tools",
                  "desc": "List available tools",
                  "inputs_schema": {},
                  "returns": { "tools": "[]" }
                },
                {
                  "id": "imagej.open_image",
                  "desc": "Open an image using ImageJ",
                  "inputs_schema": { "path": "string" },
                  "returns": { "image_id": "string" }
                },
                {
                  "id": "imagej.overlay.annotate",
                  "desc": "Draw ROIs/labels on ImageJ Overlay (non-destructive).",
                  "inputs_schema": {
                    "labels": "string (comma-separated)",
                    "stroke_px": "number"
                  },
                  "returns": { "overlay_png": "path" }
                },
                {
                  "id": "imagej.assay.detect_colonies",
                  "desc": "Colony detection with stain/layout options; outputs colony set + metrics",
                  "inputs_schema": {
                    "stain": "enum: x-gal|neutral-red|none",
                    "plate_layout": "enum: 96|384",
                    "min_size": "number",
                    "max_size": "number"
                  },
                  "returns": { "colonies_detected": "number", "analysis_handle": "string" }
                },
                {
                  "id": "imagej.assay.measure_colonies",
                  "desc": "Measure colony properties; outputs intensity, circularity, eccentricity",
                  "inputs_schema": {
                    "image_handle": "string",
                    "analysis_handle": "string"
                  },
                  "returns": { "measurements": "array", "colonies_measured": "number" }
                },
                {
                  "id": "imagej.assay.annotate",
                  "desc": "Add colony labels with id, blue_index, size_bin; uses Overlay+ROI",
                  "inputs_schema": {
                    "image_handle": "string",
                    "labels": "array"
                  },
                  "returns": { "annotations_added": "number" }
                },
                {
                  "id": "imagej.assay.export",
                  "desc": "Export colony CSV + overlay PNG",
                  "inputs_schema": {
                    "image_handle": "string",
                    "analysis_handle": "string",
                    "include_csv": "boolean",
                    "include_overlay": "boolean"
                  },
                  "returns": { "exported_files": "array" }
                },
                {
                  "id": "imagej.assay.run_macro",
                  "desc": "Run baseline ImageJ macro for quick-test comparison and validation",
                  "inputs_schema": {
                    "image_handle": "string",
                    "macro": "enum: xgal_baseline"
                  },
                  "returns": { "colonies_detected": "number", "measurements": "array", "method": "string" }
                },
                {
                  "id": "imagej.sds.quantify_lanes",
                  "desc": "Detect lanes/bands and compute densitometry",
                  "inputs_schema": {
                    "lane_count": "number?",
                    "band_smoothing_px": "number?"
                  },
                  "returns": { "table": "bands.csv", "overlay_png": "path" }
                },
                {
                  "id": "workflows.preset",
                  "desc": "Run a named preset workflow",
                  "inputs_schema": { "name": "string", "params": "object" },
                  "returns": { "artifacts": "object" }
                },
                {
                  "id": "ui.refresh_canvas",
                  "desc": "Force UI to repaint layers; returns latest overlay url",
                  "inputs_schema": { "refresh_reason": "string" },
                  "returns": { "overlay_png": "path" }
                },
                {
                  "id": "export.results",
                  "desc": "Persist CSVs and images for download",
                  "inputs_schema": { "tables": "string[]", "images": "string[]" },
                  "returns": { "paths": "string[]" }
                }
              ],
              "workflows": [
                {
                  "name": "SDS_PAGE_DENSITOMETRY",
                  "steps": ["imagej.open_image","imagej.sds.quantify_lanes","imagej.overlay.annotate","export.results"]
                },
                {
                  "name": "COLONY_BLUE_SCORING",
                  "steps": ["imagej.open_image","imagej.assay.detect_colonies","imagej.assay.annotate","imagej.assay.export"]
                }
              ]
            }
            
            CRITICAL ANNOTATION REQUIREMENT:
            🚨 When annotating, NEVER draw into pixel data. ALWAYS operate on Overlay + ROI Manager.
            
            Example pattern for all annotation operations:
            Roi roi = new OvalRoi(x, y, w, h);
            roi.setStrokeColor(Color.getHSBColor(0.58f, 1f, 1f)); // blue
            roi.setStrokeWidth(2);
            roi.setName(String.format("c%03d  BI=%.2f  size=%s", id, blueIndex, sizeBin));
            Overlay ov = imp.getOverlay();
            if (ov == null) ov = new Overlay();
            ov.add(roi);
            imp.setOverlay(ov);
            
            This preserves the original image data while providing visual feedback.
        """;
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
        
        // Strip JSON comments (//...) that Gemini sometimes includes
        responseText = responseText.replaceAll("//[^\\r\\n]*", "");
        responseText = responseText.replaceAll(",\\s*\\n", ",\n");
        responseText = responseText.trim();
        
        try {
            JSONObject parsedResponse = new JSONObject(responseText);
            
            // Handle new orchestration engine format
            if (parsedResponse.has("action")) {
                String action = parsedResponse.getString("action");
                
                if ("list_tools".equals(action)) {
                    // Return registry response
                    GelAnalysisResponse response = new GelAnalysisResponse();
                    response.action = "registry.list_tools";
                    response.analysis = "Loading capability registry";
                    response.originalCommand = originalCommand;
                    return response;
                } else if ("run".equals(action) && parsedResponse.has("plan")) {
                    // Execute plan with finite-state executor
                    JSONArray plan = parsedResponse.getJSONArray("plan");
                    
                    // ENHANCED DEBUG: Show full Gemini plan before execution
                    System.out.println("DEBUG: ===== GEMINI GENERATED PLAN =====");
                    System.out.println("DEBUG: Original command: " + originalCommand);
                    System.out.println("DEBUG: Full plan JSON: " + plan.toString(2));
                    System.out.println("DEBUG: Plan steps count: " + plan.length());
                    
                    JSONObject artifacts = executePlan(plan);
                    
                    GelAnalysisResponse response = new GelAnalysisResponse();
                    response.action = "plan_executed";
                    response.analysis = "Plan executed successfully: " + plan.length() + " steps";
                    response.originalCommand = originalCommand;
                    response.parameters.put("artifacts", artifacts.toString());
                    return response;
                } else if ("ask".equals(action)) {
                    GelAnalysisResponse response = new GelAnalysisResponse();
                    response.action = "clarification_needed";
                    response.analysis = parsedResponse.optString("question", "Need clarification");
                    response.originalCommand = originalCommand;
                    return response;
                }
            }
            
            // Fallback to legacy format
            GelAnalysisResponse response = new GelAnalysisResponse();
            response.imageType = parsedResponse.optString("image_type", "gel");
            response.intent = parsedResponse.optString("intent", "unknown");
            response.action = parsedResponse.optString("action", "detect_lanes");
            response.analysis = parsedResponse.optString("analysis", "Analysis completed");
            response.confidence = parsedResponse.optDouble("confidence", 0.8);
            response.originalCommand = originalCommand;
            
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
     * Robust finite-state executor for orchestration plans
     */
    private JSONObject executePlan(JSONArray plan) {
        JSONObject artifacts = new JSONObject();
        
        for (int i = 0; i < plan.length(); i++) {
            JSONObject step = plan.getJSONObject(i);
            String toolId = step.getString("tool_id");
            JSONObject inputs = step.optJSONObject("inputs", new JSONObject());
            
            // ENHANCED DEBUG LOGGING
            System.out.println("DEBUG: ===== EXECUTING PLAN STEP " + (i+1) + "/" + plan.length() + " =====");
            System.out.println("DEBUG: Tool ID: " + toolId);
            System.out.println("DEBUG: Full inputs JSON: " + inputs.toString(2));
            System.out.println("DEBUG: Rationale: " + step.optString("rationale", ""));
            
            sessionLogger.logSessionEvent("tool_call", 
                "Executing step " + (i+1) + "/" + plan.length(),
                new JSONObject()
                    .put("tool_id", toolId)
                    .put("inputs", inputs)
                    .put("rationale", step.optString("rationale", "")));
            
            try {
                long toolStartTime = System.currentTimeMillis();
                JSONObject out = runTool(toolId, inputs, artifacts);
                long toolDuration = System.currentTimeMillis() - toolStartTime;
                artifacts.put(toolId, out);
                
                // LOG FOR ACTION LOG PANEL: ✅ Success with outputs
                JSONObject actionLogEntry = new JSONObject()
                    .put("tool_id", toolId)
                    .put("status", "success")
                    .put("duration_ms", toolDuration)
                    .put("inputs", inputs)
                    .put("outputs", extractOutputSummary(out))
                    .put("timestamp", System.currentTimeMillis());
                
                // GUARANTEED VISUAL FEEDBACK: Auto-refresh after any ImageJ write operation
                if (isImageJWriteOperation(toolId)) {
                    JSONObject refreshResult = runTool("ui.refresh_canvas", 
                        new JSONObject()
                            .put("refresh_reason", toolId)
                            .put("cache_bust", System.currentTimeMillis()), 
                        artifacts);
                    
                    // Add overlay info to action log entry
                    String overlayUrl = refreshResult.optString("overlay_png", "");
                    actionLogEntry.put("overlay_url", overlayUrl);
                    actionLogEntry.put("visual_feedback", true);
                    
                    // Log the refresh for debugging
                    sessionLogger.logSessionEvent("auto_refresh", 
                        "Auto-refreshed UI after " + toolId, 
                        new JSONObject()
                            .put("trigger_tool", toolId)
                            .put("overlay_url", overlayUrl));
                }
                
                // Store action log entry for UI panel
                sessionLogger.logSessionEvent("action_log", 
                    "Tool executed successfully", actionLogEntry);
                
            } catch (Exception e) {
                long toolDuration = System.currentTimeMillis() - System.currentTimeMillis(); // Will be updated
                
                sessionLogger.logError("tool_error", e, 
                    new JSONObject()
                        .put("tool_id", toolId)
                        .put("step", i+1));
                
                JSONObject onFail = step.optJSONObject("on_fail");
                if (onFail != null) {
                    int retry = onFail.optInt("retry", 0);
                    String nextTool = onFail.optString("next_tool", null);
                    
                    // LOG FOR ACTION LOG PANEL: ⚠️ Failure with retry/fallback attempt
                    JSONObject actionLogEntry = new JSONObject()
                        .put("tool_id", toolId)
                        .put("status", "failed_with_recovery")
                        .put("duration_ms", toolDuration)
                        .put("error", e.getMessage())
                        .put("retry_count", retry)
                        .put("fallback_tool", nextTool)
                        .put("timestamp", System.currentTimeMillis());
                    
                    if (retry > 0) {
                        try {
                            long retryStartTime = System.currentTimeMillis();
                            JSONObject out = runTool(toolId, inputs, artifacts);
                            long retryDuration = System.currentTimeMillis() - retryStartTime;
                            
                            artifacts.put(toolId, out);
                            
                            // Update action log: Retry succeeded
                            actionLogEntry.put("status", "recovered_after_retry");
                            actionLogEntry.put("retry_duration_ms", retryDuration);
                            actionLogEntry.put("outputs", extractOutputSummary(out));
                            
                        } catch (Exception retryError) {
                            if (nextTool != null) {
                                try {
                                    long fallbackStartTime = System.currentTimeMillis();
                                    JSONObject out = runTool(nextTool, inputs, artifacts);
                                    long fallbackDuration = System.currentTimeMillis() - fallbackStartTime;
                                    
                                    artifacts.put(nextTool, out);
                                    
                                    // Update action log: Fallback succeeded
                                    actionLogEntry.put("status", "recovered_with_fallback");
                                    actionLogEntry.put("fallback_duration_ms", fallbackDuration);
                                    actionLogEntry.put("outputs", extractOutputSummary(out));
                                } catch (Exception fallbackError) {
                                    actionLogEntry.put("status", "failed_permanently");
                                    throw new RuntimeException("Both primary and fallback tools failed", fallbackError);
                                }
                            } else {
                                actionLogEntry.put("status", "failed_permanently");
                                throw new RuntimeException("Tool failed with no fallback available", retryError);
                            }
                        }
                    } else {
                        if (nextTool != null) {
                            try {
                                long fallbackStartTime = System.currentTimeMillis();
                                JSONObject out = runTool(nextTool, inputs, artifacts);
                                long fallbackDuration = System.currentTimeMillis() - fallbackStartTime;
                                
                                artifacts.put(nextTool, out);
                                
                                // Update action log: Fallback succeeded  
                                actionLogEntry.put("status", "recovered_with_fallback");
                                actionLogEntry.put("fallback_duration_ms", fallbackDuration);
                                actionLogEntry.put("outputs", extractOutputSummary(out));
                            } catch (Exception fallbackError) {
                                actionLogEntry.put("status", "failed_permanently");
                                throw new RuntimeException("Both primary and fallback tools failed", fallbackError);
                            }
                        } else {
                            actionLogEntry.put("status", "failed_permanently");
                            throw new RuntimeException("Tool failed with no fallback available", e);
                        }
                    }
                    
                    // Store action log entry for UI panel
                    sessionLogger.logSessionEvent("action_log", 
                        "Tool failed but recovered", actionLogEntry);
                    
                } else {
                    // LOG FOR ACTION LOG PANEL: ⚠️ Permanent failure
                    JSONObject actionLogEntry = new JSONObject()
                        .put("tool_id", toolId)
                        .put("status", "failed_permanently")
                        .put("duration_ms", toolDuration)
                        .put("error", e.getMessage())
                        .put("timestamp", System.currentTimeMillis());
                    
                    sessionLogger.logSessionEvent("action_log", 
                        "Tool failed permanently", actionLogEntry);
                        
                    throw new RuntimeException("Tool execution failed permanently", e);
                }
            }
        }
        
        return artifacts;
    }
    
    /**
     * Tool execution dispatcher
     */
    private JSONObject runTool(String toolId, JSONObject inputs, JSONObject artifacts) throws Exception {
        switch (toolId) {
            case "registry.list_tools":
                registryLoaded = true; // Mark registry as loaded after successful call
                return createRegistryResponse();
            case "imagej.open_image":
                return executeImageJTool("open_image", inputs);
            case "imagej.sds.quantify_lanes":
                return executeImageJTool("detect_lanes", inputs);
            case "imagej.assay.detect_colonies":
                return assayOps.detectColonies(inputs);
            case "imagej.assay.measure_colonies":
                return assayOps.measureColonies(inputs);
            case "imagej.assay.annotate":
                return assayOps.annotate(inputs);
            case "imagej.assay.export":
                return assayOps.export(inputs);
            case "imagej.assay.run_macro":
                return assayOps.runMacro(inputs);
            case "imagej.overlay.annotate":
                return executeOverlayAnnotate(inputs);
            case "ui.refresh_canvas":
                return executeRefreshCanvas(inputs);
            case "export.results":
                return executeExportResults(inputs);
            case "workflows.preset":
                return executePresetWorkflow(inputs);
            default:
                throw new RuntimeException("Unknown tool: " + toolId);
        }
    }
    
    private JSONObject createRegistryResponse() {
        return new JSONObject()
            .put("tools", new JSONArray()
                .put(new JSONObject().put("id", "registry.list_tools").put("desc", "List available tools"))
                .put(new JSONObject().put("id", "imagej.open_image").put("desc", "Open an image using ImageJ"))
                .put(new JSONObject().put("id", "imagej.sds.quantify_lanes").put("desc", "Detect lanes/bands and compute densitometry"))
                .put(new JSONObject().put("id", "imagej.assay.detect_colonies").put("desc", "Detect colonies with stain/layout options"))
                .put(new JSONObject().put("id", "imagej.assay.measure_colonies").put("desc", "Measure colony intensity, circularity, eccentricity"))
                .put(new JSONObject().put("id", "imagej.assay.annotate").put("desc", "Add colony labels with id, blue_index, size_bin"))
                .put(new JSONObject().put("id", "imagej.assay.export").put("desc", "Export colony CSV + overlay PNG"))
                .put(new JSONObject().put("id", "imagej.assay.run_macro").put("desc", "Run baseline ImageJ macro for quick-test comparison"))
                .put(new JSONObject().put("id", "ui.refresh_canvas").put("desc", "Force UI to repaint layers"))
                .put(new JSONObject().put("id", "workflows.preset").put("desc", "Run named preset workflow")));
    }
    
    private JSONObject executeImageJTool(String legacyAction, JSONObject inputs) throws Exception {
        // Map new tool IDs to legacy tool execution
        JSONObject parameters = new JSONObject();
        
        if ("detect_lanes".equals(legacyAction)) {
            if (inputs.has("lane_count")) {
                parameters.put("expected_lanes", inputs.getInt("lane_count"));
            }
        } else if ("count_colonies_by_color".equals(legacyAction)) {
            if (inputs.has("blue_threshold")) {
                parameters.put("color_groups", new JSONArray().put("blue").put("white"));
            }
        }
        
        return executeTool(legacyAction, parameters);
    }
    
    private JSONObject executeOverlayAnnotate(JSONObject inputs) throws Exception {
        // CRITICAL: All annotation must use Overlay + ROI Manager approach
        String imageHandle = inputs.optString("image_handle", currentImageHandle);
        if (imageHandle == null) {
            imageHandle = sessionStore.getMostRecentImageHandle();
        }
        
        SessionStore.ImageRecord img = sessionStore.getImage(imageHandle);
        if (img == null) {
            throw new RuntimeException("Image not found: " + imageHandle);
        }
        
        // Example annotation pattern (following your specification)
        JSONArray annotations = inputs.optJSONArray("annotations");
        if (annotations != null) {
            ij.gui.Overlay ov = img.image.getOverlay();
            if (ov == null) {
                ov = new ij.gui.Overlay();
            }
            
            for (int i = 0; i < annotations.length(); i++) {
                JSONObject ann = annotations.getJSONObject(i);
                String type = ann.optString("type", "oval");
                int x = ann.optInt("x", 0);
                int y = ann.optInt("y", 0);
                int w = ann.optInt("width", 20);
                int h = ann.optInt("height", 20);
                String label = ann.optString("label", "");
                String color = ann.optString("color", "cyan");
                
                // Create ROI following the exact pattern you specified
                ij.gui.Roi roi;
                switch (type) {
                    case "oval":
                        roi = new ij.gui.OvalRoi(x, y, w, h);
                        break;
                    case "rectangle":
                        roi = new ij.gui.Roi(x, y, w, h);
                        break;
                    case "line":
                        roi = new ij.gui.Line(x, y, x + w, y + h);
                        break;
                    default:
                        roi = new ij.gui.OvalRoi(x, y, w, h);
                }
                
                // Apply annotation styling (never modify pixel data)
                switch (color.toLowerCase()) {
                    case "blue":
                        roi.setStrokeColor(java.awt.Color.getHSBColor(0.58f, 1f, 1f));
                        break;
                    case "red":
                        roi.setStrokeColor(java.awt.Color.RED);
                        break;
                    case "yellow":
                        roi.setStrokeColor(java.awt.Color.YELLOW);
                        break;
                    default:
                        roi.setStrokeColor(java.awt.Color.CYAN);
                }
                roi.setStrokeWidth(2);
                if (!label.isEmpty()) {
                    roi.setName(label);
                }
                
                // Add to Overlay (never to pixel data)
                ov.add(roi);
            }
            
            // Apply overlay to image
            img.image.setOverlay(ov);
        }
        
        // Generate transparent overlay PNG using new exporter
        return exportOverlayPNG();
    }
    
    private JSONObject executeRefreshCanvas(JSONObject inputs) throws Exception {
        // Force overlay refresh with cache-busting - return latest overlay URL
        long timestamp = inputs.optLong("cache_bust", System.currentTimeMillis());
        String refreshReason = inputs.optString("refresh_reason", "manual");
        
        // Generate fresh overlay PNG with cache-busting timestamp
        String overlayUrl = "/overlays/current.png?t=" + timestamp;
        
        // Export fresh overlay if image is available
        String imageHandle = inputs.optString("image_handle", currentImageHandle);
        if (imageHandle == null) {
            imageHandle = sessionStore.getMostRecentImageHandle();
        }
        
        String actualOverlayPath = null;
        if (imageHandle != null) {
            try {
                JSONObject overlayResult = exportOverlayPNG();
                actualOverlayPath = overlayResult.optString("overlay_png");
                
                // Update URL to point to actual file with cache-busting
                if (actualOverlayPath != null) {
                    overlayUrl = actualOverlayPath + "?t=" + timestamp;
                }
            } catch (Exception e) {
                System.err.println("Failed to export overlay for refresh: " + e.getMessage());
            }
        }
        
        return new JSONObject()
            .put("success", true)
            .put("overlay_png", overlayUrl)
            .put("refresh_reason", refreshReason)
            .put("timestamp", timestamp)
            .put("cache_busted", true);
    }
    
    private JSONObject executeExportResults(JSONObject inputs) throws Exception {
        JSONArray tables = inputs.optJSONArray("tables");
        JSONArray images = inputs.optJSONArray("images");
        JSONArray paths = new JSONArray();
        
        if (tables != null) {
            for (int i = 0; i < tables.length(); i++) {
                paths.put("/exports/" + tables.getString(i));
            }
        }
        
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                paths.put("/exports/" + images.getString(i));
            }
        }
        
        return new JSONObject().put("paths", paths);
    }
    
    private JSONObject executePresetWorkflow(JSONObject inputs) throws Exception {
        String workflowName = inputs.getString("name");
        JSONObject params = inputs.optJSONObject("params", new JSONObject());
        
        // Preset workflows: thin arrays of tool IDs, no bespoke code paths
        sessionLogger.logSessionEvent("preset_workflow", 
            "Executing preset workflow: " + workflowName, 
            new JSONObject().put("workflow", workflowName));
        
        // Get preset tool steps as thin arrays
        JSONArray steps = getPresetSteps(workflowName, params);
        
        // Guard: Skip open_image if we already have an image loaded
        if (hasCurrentImage()) {
            steps = removeOpenImageStep(steps);
            sessionLogger.logSessionEvent("open_image_skipped", 
                "Skipped open_image step - using current image handle: " + currentImageHandle,
                new JSONObject().put("current_image_handle", currentImageHandle));
        }
        
        // Execute as standard plan using same executor
        return executePlan(steps);
    }
    
    private JSONArray getPresetSteps(String workflowName, JSONObject params) {
        return switch (workflowName) {
            case "SDS_PAGE_DENSITOMETRY" -> {
                // Extract lane parameters from params for proper routing  
                int expectedLanes = params != null ? params.optInt("lane_count", 12) : 12;
                int markerLane = params != null ? params.optInt("marker_lane", 1) : 1;
                
                // Create corrected parameter objects
                JSONObject openImageParams = new JSONObject();
                if (params != null && params.has("path")) {
                    openImageParams.put("path", params.getString("path"));
                }
                
                JSONObject laneParams = new JSONObject()
                    .put("expected_lanes", expectedLanes)
                    .put("marker_lane", markerLane)
                    .put("constant_spacing", false)
                    .put("lane_width_fraction", 0.40)
                    .put("min_peak_distance", 20);
                    
                JSONObject bandParams = new JSONObject()
                    .put("method", "peak")
                    .put("smooth_sigma", 2.0)
                    .put("min_prominence", 0.06)
                    .put("min_peak_distance", 10);
                    
                JSONObject preprocessParams = new JSONObject()
                    .put("mode", "coomassie_default");
                
                yield new JSONArray()
                    .put(new JSONObject().put("tool_id", "imagej.open_image").put("inputs", openImageParams))
                    .put(new JSONObject().put("tool_id", "preprocess").put("inputs", preprocessParams))
                    .put(new JSONObject().put("tool_id", "detect_lanes").put("inputs", laneParams))
                    .put(new JSONObject().put("tool_id", "detect_bands").put("inputs", bandParams))
                    .put(new JSONObject().put("tool_id", "render_overlay_png").put("inputs", new JSONObject()
                        .put("lane_color", "blue")
                        .put("band_color", "lime")
                        .put("thickness", 2)))
                    .put(new JSONObject().put("tool_id", "export_results").put("inputs", new JSONObject()));
            }
                
            case "COLONY_BLUE_SCORING" -> {
                JSONObject colonyParams = new JSONObject(params.toString());
                if (!colonyParams.has("stain")) {
                    colonyParams.put("stain", "x-gal"); // Default for blue scoring
                }
                yield new JSONArray()
                    .put(new JSONObject().put("tool_id", "imagej.open_image").put("inputs", params))
                    .put(new JSONObject().put("tool_id", "imagej.assay.detect_colonies").put("inputs", colonyParams))
                    .put(new JSONObject().put("tool_id", "imagej.assay.annotate").put("inputs", new JSONObject()))
                    .put(new JSONObject().put("tool_id", "imagej.assay.export").put("inputs", new JSONObject()));
            }
            
            default -> throw new RuntimeException("Unknown preset workflow: " + workflowName + 
                ". Available presets: SDS_PAGE_DENSITOMETRY, COLONY_BLUE_SCORING");
        };
    }
    
    /**
     * Export transparent overlay PNG (non-destructive)
     */
    private JSONObject exportOverlayPNG() throws Exception {
        String imageHandle = currentImageHandle;
        if (imageHandle == null) {
            imageHandle = sessionStore.getMostRecentImageHandle();
        }
        
        if (imageHandle == null) {
            throw new RuntimeException("No image available for overlay export");
        }
        
        SessionStore.ImageRecord img = sessionStore.getImage(imageHandle);
        if (img == null) {
            throw new RuntimeException("Image not found: " + imageHandle);
        }
        
        // Use OverlayExporter to create transparent PNG
        java.io.File overlayFile = exportOverlayPNGFile(img.image);
        
        return new JSONObject()
            .put("overlay_png", overlayFile.getAbsolutePath())
            .put("width", img.image.getWidth())
            .put("height", img.image.getHeight())
            .put("transparent", true);
    }
    
    private java.io.File exportOverlayPNGFile(ij.ImagePlus imp) throws Exception {
        int w = imp.getWidth(), h = imp.getHeight();
        java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = png.createGraphics();
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        
        // CRITICAL: Only export from Overlay + ROI Manager, never from pixel data
        ij.gui.Overlay ov = imp.getOverlay();
        if (ov != null) {
            for (ij.gui.Roi r : ov.toArray()) {
                // Ensure ROI has proper annotation styling
                if (r.getStrokeColor() == null) {
                    r.setStrokeColor(java.awt.Color.CYAN); // default annotation color
                }
                if (r.getStrokeWidth() == 0) {
                    r.setStrokeWidth(2); // default annotation width
                }
                r.drawOverlay(g); // respects stroke/fill/labels from Overlay
            }
        }
        
        // Also check ROI Manager for additional annotations
        ij.plugin.frame.RoiManager roiManager = ij.plugin.frame.RoiManager.getRoiManager();
        if (roiManager != null) {
            ij.gui.Roi[] rois = roiManager.getRoisAsArray();
            for (ij.gui.Roi r : rois) {
                if (r.getStrokeColor() == null) {
                    r.setStrokeColor(java.awt.Color.YELLOW); // ROI Manager default
                }
                if (r.getStrokeWidth() == 0) {
                    r.setStrokeWidth(2);
                }
                r.drawOverlay(g);
            }
        }
        
        g.dispose();
        
        java.io.File out = new java.io.File(System.getProperty("java.io.tmpdir"), 
            "overlay_" + System.currentTimeMillis() + ".png");
        javax.imageio.ImageIO.write(png, "PNG", out);
        
        return out;
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
    
    private boolean shouldAutoGenerateVisualFeedback(String toolName) {
        // Tools that create visual annotations that users should see
        return switch (toolName) {
            case "detect_lanes", "detect_bands", "adjust_lanes" -> true;
            case "detect_plate", "count_colonies_by_color", "classify_colonies" -> true;
            case "calibrate_standard_curve", "compare_lanes" -> true;
            // Don't auto-generate for tools that already include PNG generation
            case "analyze_gel", "export_gel", "analyze_plate", "export_plate" -> false;
            case "render_overlay_png", "export_results" -> false; // Already PNG tools
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
    
    /**
     * Detect if user command matches a known preset workflow
     */
    private boolean isPresetWorkflow(String userCommand) {
        String cmd = userCommand.toLowerCase().trim();
        
        // Colony Blue Scoring preset patterns
        if (cmd.contains("blue") && (cmd.contains("colonies") || cmd.contains("colony"))) {
            return true;
        }
        if (cmd.contains("x-gal") || cmd.contains("xgal")) {
            return true;
        }
        if (cmd.matches(".*analyz.*coloni.*") && cmd.contains("blue")) {
            return true;
        }
        
        // SDS-PAGE Densitometry preset patterns  
        if (cmd.contains("gel") && (cmd.contains("lane") || cmd.contains("band"))) {
            return true;
        }
        if (cmd.contains("sds") || cmd.contains("page")) {
            return true;
        }
        if (cmd.contains("densitometry") || cmd.contains("quantif")) {
            return true;
        }
        
        // Explicit preset names
        if (cmd.contains("colony_blue_scoring") || cmd.contains("sds_page_densitometry")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Detect if tool performs ImageJ write operations requiring visual feedback
     */
    private boolean isImageJWriteOperation(String toolId) {
        return switch (toolId) {
            // Detection operations that add overlays
            case "imagej.open_image",
                 "imagej.sds.quantify_lanes",
                 "imagej.assay.detect_colonies",
                 "imagej.assay.measure_colonies",
                 "imagej.assay.annotate",
                 "imagej.assay.run_macro" -> true;
            
            // Workflow presets that perform detection/annotation
            case "workflows.preset" -> true;
            
            // Canvas refresh is not a write operation (avoid recursion)
            case "ui.refresh_canvas" -> false;
            
            // Registry and export operations don't modify overlays
            case "registry.list_tools",
                 "imagej.assay.export",
                 "export.results" -> false;
            
            default -> toolId.startsWith("imagej.");
        };
    }
    
    /**
     * Extract meaningful output summary for Action Log Panel
     */
    private JSONObject extractOutputSummary(JSONObject toolOutput) {
        JSONObject summary = new JSONObject();
        
        // Common output fields to highlight in Action Log
        String[] importantFields = {
            "colonies_detected", "lanes_found", "bands_total",
            "measurements", "exported_files", "overlay_png",
            "colonies_measured", "annotations_added", "success"
        };
        
        for (String field : importantFields) {
            if (toolOutput.has(field)) {
                Object value = toolOutput.get(field);
                
                // Format file arrays nicely
                if (field.equals("exported_files") && value instanceof JSONArray) {
                    JSONArray files = (JSONArray) value;
                    summary.put(field, formatFileList(files));
                }
                // Format overlay URLs nicely  
                else if (field.equals("overlay_png") && value instanceof String) {
                    String path = (String) value;
                    summary.put(field, extractFilename(path));
                }
                // Keep other values as-is
                else {
                    summary.put(field, value);
                }
            }
        }
        
        return summary;
    }
    
    private String formatFileList(JSONArray files) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.length(); i++) {
            if (i > 0) sb.append(", ");
            String file = files.optString(i, "");
            sb.append(extractFilename(file));
        }
        return sb.toString();
    }
    
    private String extractFilename(String path) {
        if (path == null || path.isEmpty()) return "";
        
        // Extract just the filename from full path
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
    
    /**
     * Get action log entries for right-hand UI panel display
     */
    public JSONArray getActionLogEntries() {
        JSONArray actionLog = new JSONArray();
        
        try {
            // Get recent session events of type "action_log"
            JSONObject sessionStats = sessionLogger.getSessionStatistics();
            JSONArray allEvents = sessionStats.optJSONArray("recent_events");
            
            if (allEvents != null) {
                for (int i = 0; i < allEvents.length(); i++) {
                    JSONObject event = allEvents.getJSONObject(i);
                    if ("action_log".equals(event.optString("event_type"))) {
                        JSONObject data = event.optJSONObject("data");
                        if (data != null) {
                            actionLog.put(data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to retrieve action log: " + e.getMessage());
        }
        
        return actionLog;
    }
    
    /**
     * Get latest overlay thumbnail URL for Action Log Panel
     */
    public String getLatestOverlayThumbnail() {
        try {
            String imageHandle = currentImageHandle;
            if (imageHandle == null) {
                imageHandle = sessionStore.getMostRecentImageHandle();
            }
            
            if (imageHandle != null) {
                JSONObject overlayResult = exportOverlayPNG();
                return overlayResult.optString("overlay_png", "") + "?t=" + System.currentTimeMillis();
            }
        } catch (Exception e) {
            System.err.println("Failed to get overlay thumbnail: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Check if we currently have an image loaded that can be used for analysis
     */
    private boolean hasCurrentImage() {
        return (currentImageHandle != null && sessionStore.hasImage(currentImageHandle)) ||
               sessionStore.getMostRecentImageHandle() != null;
    }
    
    /**
     * Remove any open_image steps from a plan when an image is already loaded
     */
    private JSONArray removeOpenImageStep(JSONArray steps) {
        JSONArray filteredSteps = new JSONArray();
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String toolId = step.optString("tool_id", "");
            // Skip any open_image related steps
            if (!toolId.equals("imagej.open_image") && !toolId.equals("open_image")) {
                filteredSteps.put(step);
            }
        }
        return filteredSteps;
    }
    
    /**
     * Validates if the provided API key is valid (not null, empty, or placeholder)
     */
    private static boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        // Check for common placeholder values
        String key = apiKey.trim().toLowerCase();
        return !key.equals("placeholder") && 
               !key.equals("your_api_key_here") && 
               !key.equals("your_key") && 
               !key.equals("test") && 
               !key.equals("demo") && 
               key.length() > 10; // Real keys are typically much longer
    }
}