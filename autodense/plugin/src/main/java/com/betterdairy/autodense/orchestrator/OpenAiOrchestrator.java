package com.betterdairy.autodense.orchestrator;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.session.SessionLogger;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.tools.AssayOps;
import com.betterdairy.autodense.tools.CanonicalTools;
import com.betterdairy.autodense.plugin.OpenAiApiClient;
import com.betterdairy.autodense.plugin.OpenAiApiClient.GelAnalysisResponse;
import com.betterdairy.autodense.registry.CapabilityRegistry;
import ij.ImagePlus;
import org.json.JSONObject;
import org.json.JSONArray;
import org.scijava.Context;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central orchestrator for OpenAI ChatGPT-powered gel analysis sessions.
 * Coordinates between ChatGPT Vision AI planning and ImageJ tool execution
 * while maintaining comprehensive session logs.
 */
public class OpenAiOrchestrator {
    
    private final SessionStore sessionStore;
    private final SessionLogger sessionLogger;
    private final GelAnalysisTools gelAnalysisTools;
    private final AssayOps assayOps;
    private final CanonicalTools canonicalTools;
    private final OpenAiApiClient openaiClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ExecutorService executorService;
    private final JSONObject generatedIntents; // Generated at startup from Java registry
    
    private volatile boolean isActive = true;
    private String currentImageHandle = null;
    private boolean registryLoaded = false;
    
    public OpenAiOrchestrator(String apiKey) {
        // Validate API key before initializing components
        if (!isValidApiKey(apiKey)) {
            throw new IllegalArgumentException(
                "❌ Invalid OpenAI API key provided. Please:\n" +
                "1. Get a valid API key from https://platform.openai.com/api-keys\n" +
                "2. Set it via: export OPENAI_API_KEY=your_actual_key\n" +
                "3. Or create api-config.properties with: OPENAI_API_KEY=your_actual_key\n" +
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
        this.openaiClient = new OpenAiApiClient(apiKey);
        this.capabilityRegistry = new CapabilityRegistry(new Context());
        
        // Generate intents JSON from Java registry (single source of truth)
        this.generatedIntents = capabilityRegistry.exportGelAnalysisIntents();
        
        // Single thread executor for sequential processing
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OpenAiOrchestrator-" + sessionStore.getSessionId());
            t.setDaemon(false); // Keep session alive
            return t;
        });
        
        // Log orchestrator initialization
        JSONObject initData = new JSONObject()
            .put("session_id", sessionStore.getSessionId())
            .put("openai_client_initialized", apiKey != null && !apiKey.isEmpty())
            .put("analysis_tools_count", getAvailableToolsCount())
            .put("intents_generated", generatedIntents != null)
            .put("intents_count", generatedIntents != null ? 
                generatedIntents.optJSONObject("intents", new JSONObject()).length() : 0);
            
        sessionLogger.logSessionEvent("orchestrator_init", 
            "OpenAI orchestrator initialized with generated intents from Java registry", initData);
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
                
                // Step 2: Get OpenAI analysis
                GelAnalysisResponse openaiResponse = callOpenAiWithLogging(userCommand, currentImage);
                
                // Step 3: Execute tool calls based on OpenAI's analysis
                JSONObject toolResult = executeToolWithLogging(openaiResponse);
                
                // Step 4: Create orchestration result
                OrchestrationResult result = OrchestrationResult.success(
                    openaiResponse, toolResult, System.currentTimeMillis() - startTime);
                
                // Log OpenAI response
                JSONObject openaiMetadata = new JSONObject()
                    .put("image_type", openaiResponse.imageType)
                    .put("intent", openaiResponse.intent)
                    .put("action", openaiResponse.action)
                    .put("confidence", openaiResponse.confidence)
                    .put("tool_executed", openaiResponse.action)
                    .put("execution_success", !toolResult.optBoolean("error", false));
                    
                sessionLogger.logConversation("openai", openaiResponse.analysis, openaiMetadata);
                
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
     * This maintains backward compatibility with existing tool execution
     */
    public JSONObject executeTool(String toolName, JSONObject parameters) {
        // Execute tool using the integrated tool execution logic
        // This ensures we don't duplicate all the complex tool routing logic
        // TODO: Extract tool execution logic into a shared service class
        
        long startTime = System.currentTimeMillis();
        boolean success = false;
        JSONObject result = null;
        
        try {
            // Handle injection logic for required tool parameters
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
            
            // Execute the tool using the integrated analysis tools
            result = executeToolInternal(toolName, parameters);
            
            // Record last active image handle if returned by tool
            if (result != null && result.has("image_handle")) {
                String handle = result.optString("image_handle", null);
                if (handle != null) {
                    sessionStore.setLastActiveImageHandle(handle);
                    this.currentImageHandle = handle;
                }
            }
            
            success = !result.optBoolean("error", false);
            
            // Auto-generate visual feedback after detection tools
            if (success && shouldAutoGenerateVisualFeedback(toolName)) {
                try {
                    JSONObject pngArgs = new JSONObject();
                    if (parameters.has("image_handle")) {
                        pngArgs.put("image_handle", parameters.getString("image_handle"));
                    }
                    
                    JSONObject pngResult = gelAnalysisTools.renderOverlayPng(pngArgs);
                    if (pngResult.optBoolean("success", false)) {
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
    
    // Private helper methods
    
    private GelAnalysisResponse callOpenAiWithLogging(String userCommand, ImagePlus image) throws Exception {
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
            
            // Check if we need to enforce registry handshake
            String finalPrompt = systemPrompt;
            if (!registryLoaded && !isPresetWorkflow(userCommand)) {
                finalPrompt += "\n\nREJECT: Session lacks registry. You MUST call registry.list_tools first before any planning.";
            } else {
                finalPrompt += "\n\nUser command: " + userCommand;
            }
            
            // Call pure HTTP client with contract-based prompt
            JSONObject rawResponse = openaiClient.sendRequest(finalPrompt, image);
            
            // Parse response (business logic now in orchestrator)
            GelAnalysisResponse response = parseOpenAiResponse(rawResponse, userCommand);
            
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
            // Log API call (using generic term instead of "gemini")
            sessionLogger.logLlmApiCall("analyzeGel", requestData, responseData, 
                statusCode, System.currentTimeMillis() - startTime);
        }
    }
    
    private GelAnalysisResponse parseOpenAiResponse(JSONObject rawResponse, String originalCommand) throws Exception {
        JSONArray choices = rawResponse.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new RuntimeException("No response choices from OpenAI");
        }
        
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.optJSONObject("message");
        if (message == null) {
            throw new RuntimeException("No message in OpenAI response");
        }
        
        String responseText = message.optString("content", "");
        if (responseText.isEmpty()) {
            throw new RuntimeException("Empty response text from OpenAI");
        }
        
        // Clean and parse JSON response (same logic as Gemini version)
        responseText = responseText.trim();
        if (responseText.startsWith("```json")) {
            responseText = responseText.substring(7);
        }
        if (responseText.endsWith("```")) {
            responseText = responseText.substring(0, responseText.length() - 3);
        }
        responseText = responseText.trim();
        
        // Strip JSON comments that AI sometimes includes
        responseText = responseText.replaceAll("//[^\\r\\n]*", "");
        responseText = responseText.replaceAll(",\\s*\\n", ",\n");
        responseText = responseText.trim();
        
        try {
            JSONObject parsedResponse = new JSONObject(responseText);
            
            // Handle orchestration engine format (same as Gemini version)
            if (parsedResponse.has("action")) {
                String action = parsedResponse.getString("action");
                
                if ("list_tools".equals(action)) {
                    GelAnalysisResponse response = new GelAnalysisResponse();
                    response.action = "registry.list_tools";
                    response.analysis = "Loading capability registry";
                    response.originalCommand = originalCommand;
                    return response;
                } else if ("run".equals(action) && parsedResponse.has("plan")) {
                    JSONArray plan = parsedResponse.getJSONArray("plan");
                    
                    System.out.println("DEBUG: ===== OPENAI GENERATED PLAN =====");
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
            throw new RuntimeException("Failed to parse OpenAI JSON response: " + responseText, e);
        }
    }
    
    // Reuse existing utility methods with minimal changes
    
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
            You are an orchestration engine for laboratory image analysis. You NEVER analyze images yourself and you NEVER describe what you "see".
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
            - This eliminates all "AI didn't know tools" bugs
            
            DECISION TREE:
            User Request → Is Preset? → YES: workflows.preset 
                       → NO: Registry Loaded? → YES: Ad-hoc plan
                                              → NO: REJECT + force registry.list_tools

            Rules:
            - No vision analysis. Do not infer from image pixels; call ImageJ tools to measure, segment, annotate, or export.
            - Only emit JSON tool calls using the schema below.
            - Each step must specify: tool_id, inputs, rationale (one short sentence), and on_fail (retry/backoff or alternate tool).
            - You MUST call `registry.list_tools` at the start of every session and cache the result.
            - You are an ImageJ/Fiji expert: prefer ImageJ tools for image operations; rely on preset workflows when applicable.

            Output format: ALWAYS JSON with one of:
            { "action": "list_tools" }
            { "action": "run", "plan": [ { "tool_id": "...", "inputs": {...}, "rationale": "...", "on_fail": {...} }, ... ] }
            { "action": "ask", "question": "..." }
            
            [... rest of system prompt identical to Gemini version ...]
        """;
    }
    
    // Delegate complex execution methods to avoid code duplication
    // These methods can be extracted to a shared service class later
    
    private JSONObject executeToolWithLogging(GelAnalysisResponse openaiResponse) {
        // Convert response parameters to tool parameters
        JSONObject toolParameters = new JSONObject(openaiResponse.parameters);
        
        // Special handling for response types that aren't real tools
        if ("clarification_needed".equals(openaiResponse.action)) {
            toolParameters.put("question", openaiResponse.analysis);
            return executeTool(openaiResponse.action, toolParameters);
        }
        if ("plan_executed".equals(openaiResponse.action)) {
            String artifacts = toolParameters.optString("artifacts", "{}");
            toolParameters.put("artifacts", artifacts);
            return executeTool(openaiResponse.action, toolParameters);
        }
        if ("registry.list_tools".equals(openaiResponse.action)) {
            return executeTool(openaiResponse.action, toolParameters);
        }
        
        // Before dispatch: ensure image_handle is present or inject last active
        if (!toolParameters.has("image_handle") || toolParameters.isNull("image_handle")) {
            String last = (currentImageHandle != null) ? currentImageHandle : sessionStore.getMostRecentImageHandle();
            if (last != null && sessionStore.hasImage(last)) {
                toolParameters.put("image_handle", last);
                sessionLogger.logSessionEvent(
                    "tool_call_warning",
                    "Injected missing image_handle: " + last,
                    new JSONObject().put("injected_image_handle", last).put("tool", openaiResponse.action)
                );
            } else {
                throw new IllegalArgumentException("image_handle required and no active image in session");
            }
        }
        
        return executeTool(openaiResponse.action, toolParameters);
    }
    
    // For now, implement a simplified version of the complex tool execution logic
    private JSONObject executeToolInternal(String toolName, JSONObject parameters) {
        // This is a simplified version - extract to shared service if needed
        // to a shared service class to avoid code duplication
        
        switch (toolName) {
            // Basic canonical tools
            case "analyze_gel" -> { return canonicalTools.analyze_gel(parameters); }
            case "adjust_gel" -> { return canonicalTools.adjust_gel(parameters); }
            case "export_gel" -> { return canonicalTools.export_gel(parameters); }
            case "analyze_plate" -> { return canonicalTools.analyze_plate(parameters); }
            case "adjust_plate" -> { return canonicalTools.adjust_plate(parameters); }
            case "export_plate" -> { return canonicalTools.export_plate(parameters); }
            case "preprocess_image" -> { return canonicalTools.preprocess_image(parameters); }
            case "clear_session" -> { return canonicalTools.clear_session(parameters); }
            
            // Registry and special responses
            case "registry.list_tools" -> {
                registryLoaded = true;
                return createRegistryResponse();
            }
            case "clarification_needed" -> { 
                return new JSONObject()
                    .put("success", true)
                    .put("message", parameters.optString("question", "Clarification needed"))
                    .put("response_type", "clarification");
            }
            case "plan_executed" -> { 
                return new JSONObject()
                    .put("success", true)
                    .put("message", "Plan executed successfully")
                    .put("response_type", "plan_result")
                    .put("artifacts", parameters.optString("artifacts", "{}"));
            }
            
            default -> {
                return new JSONObject()
                    .put("error", true)
                    .put("message", "Unknown tool: " + toolName + ". Use canonical actions: analyze_gel, adjust_gel, export_gel, analyze_plate, adjust_plate, export_plate, preprocess_image, clear_session");
            }
        }
    }
    
    // Helper methods - these can be shared between Gemini and OpenAI orchestrators
    
    private JSONObject executePlan(JSONArray plan) {
        // For now, implement a simplified version
        JSONObject artifacts = new JSONObject();
        
        for (int i = 0; i < plan.length(); i++) {
            JSONObject step = plan.getJSONObject(i);
            String toolId = step.getString("tool_id");
            JSONObject inputs = step.optJSONObject("inputs", new JSONObject());
            
            System.out.println("DEBUG: ===== EXECUTING PLAN STEP " + (i+1) + "/" + plan.length() + " =====");
            System.out.println("DEBUG: Tool ID: " + toolId);
            System.out.println("DEBUG: Inputs: " + inputs.toString(2));
            
            try {
                JSONObject result = executeToolInternal(toolId, inputs);
                artifacts.put(toolId, result);
            } catch (Exception e) {
                sessionLogger.logError("tool_error", e, 
                    new JSONObject().put("tool_id", toolId).put("step", i+1));
                throw new RuntimeException("Tool execution failed: " + toolId, e);
            }
        }
        
        return artifacts;
    }
    
    private JSONObject createRegistryResponse() {
        return new JSONObject()
            .put("tools", new JSONArray()
                .put(new JSONObject().put("id", "registry.list_tools").put("desc", "List available tools"))
                .put(new JSONObject().put("id", "analyze_gel").put("desc", "Analyze gel electrophoresis image"))
                .put(new JSONObject().put("id", "analyze_plate").put("desc", "Analyze agar plate with colonies"))
                .put(new JSONObject().put("id", "adjust_gel").put("desc", "Adjust gel analysis parameters"))
                .put(new JSONObject().put("id", "adjust_plate").put("desc", "Adjust plate analysis parameters"))
                .put(new JSONObject().put("id", "export_gel").put("desc", "Export gel analysis results"))
                .put(new JSONObject().put("id", "export_plate").put("desc", "Export plate analysis results")));
    }
    
    private boolean isPresetWorkflow(String userCommand) {
        String cmd = userCommand.toLowerCase().trim();
        
        // Colony Blue Scoring preset patterns
        if (cmd.contains("blue") && (cmd.contains("colonies") || cmd.contains("colony"))) {
            return true;
        }
        if (cmd.contains("x-gal") || cmd.contains("xgal")) {
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
        
        return false;
    }
    
    // Utility methods - can be shared
    
    private int getAvailableToolsCount() {
        return 8; // Basic tool count
    }
    
    private long estimateImageSize(ImagePlus image) {
        return (long) image.getWidth() * image.getHeight() * image.getNChannels() * 
               (image.getBytesPerPixel() > 0 ? image.getBytesPerPixel() : 4);
    }
    
    private String injectLastActiveImageHandle(String toolName) {
        String handle = (currentImageHandle != null) ? currentImageHandle : sessionStore.getMostRecentImageHandle();
        if (handle != null && sessionStore.hasImage(handle)) {
            sessionLogger.logSessionEvent(
                "handle_injection",
                "Auto-injected image_handle for user convenience",
                new JSONObject()
                    .put("injected_image_handle", handle)
                    .put("tool", toolName)
            );
            return handle;
        }
        return null;
    }
    
    private String injectLastActiveOverlayHandle(String toolName) {
        String currentImage = (currentImageHandle != null) ? currentImageHandle : sessionStore.getMostRecentImageHandle();
        if (currentImage != null) {
            List<String> overlays = sessionStore.getOverlaysForImage(currentImage);
            if (!overlays.isEmpty()) {
                String overlayHandle = overlays.get(overlays.size() - 1);
                sessionLogger.logSessionEvent(
                    "handle_injection",
                    "Auto-injected overlay_handle for user convenience",
                    new JSONObject()
                        .put("injected_overlay_handle", overlayHandle)
                        .put("image_handle", currentImage)
                        .put("tool", toolName)
                );
                return overlayHandle;
            }
        }
        return null;
    }
    
    private boolean toolNeedsOverlayHandle(String toolName) {
        return switch (toolName) {
            case "adjust_gel", "export_gel", "adjust_plate", "export_plate" -> true;
            default -> false;
        };
    }
    
    private boolean shouldAutoGenerateVisualFeedback(String toolName) {
        return switch (toolName) {
            case "analyze_gel", "analyze_plate", "adjust_gel", "adjust_plate" -> true;
            default -> false;
        };
    }
    
    // Close method
    public void close() {
        if (!isActive) return;
        
        isActive = false;
        
        try {
            sessionLogger.logSessionEvent("orchestrator_shutdown", 
                "OpenAI Orchestrator closing, finalizing session", 
                sessionLogger.getSessionStatistics());
                
            sessionLogger.closeSession();
            executorService.shutdown();
            
        } catch (Exception e) {
            System.err.println("Error during orchestrator shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Session info and other utility methods
    
    public JSONObject getSessionInfo() {
        JSONObject sessionInfo = new JSONObject()
            .put("session_statistics", sessionLogger.getSessionStatistics())
            .put("store_summary", sessionStore.getSummary())
            .put("current_image_handle", currentImageHandle)
            .put("orchestrator_active", isActive);
            
        sessionLogger.logSessionEvent("session_info_requested", 
            "Session information retrieved", sessionInfo);
            
        return sessionInfo;
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
               key.length() > 10 && // Real OpenAI keys are longer
               key.startsWith("sk-"); // OpenAI keys start with "sk-"
    }
    
    /**
     * Result of orchestration containing AI analysis and tool execution results
     */
    public static class OrchestrationResult {
        public final boolean success;
        public final String errorMessage;
        public final GelAnalysisResponse openaiResponse;
        public final JSONObject toolResult;
        public final long executionTimeMs;
        
        private OrchestrationResult(boolean success, String errorMessage, 
                                   GelAnalysisResponse openaiResponse, 
                                   JSONObject toolResult, long executionTimeMs) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.openaiResponse = openaiResponse;
            this.toolResult = toolResult;
            this.executionTimeMs = executionTimeMs;
        }
        
        public static OrchestrationResult success(GelAnalysisResponse openaiResponse, 
                                                 JSONObject toolResult, long executionTimeMs) {
            return new OrchestrationResult(true, null, openaiResponse, toolResult, executionTimeMs);
        }
        
        public static OrchestrationResult error(String errorMessage) {
            return new OrchestrationResult(false, errorMessage, null, null, 0);
        }
        
        public JSONObject toJson() {
            JSONObject result = new JSONObject()
                .put("success", success)
                .put("execution_time_ms", executionTimeMs);
                
            if (success) {
                result.put("openai_response", new JSONObject()
                    .put("image_type", openaiResponse.imageType)
                    .put("intent", openaiResponse.intent)
                    .put("action", openaiResponse.action)
                    .put("confidence", openaiResponse.confidence)
                    .put("analysis", openaiResponse.analysis));
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
                    openaiResponse.action, executionTimeMs);
            } else {
                return String.format("OrchestrationResult{success=false, error='%s'}", errorMessage);
            }
        }
    }
}