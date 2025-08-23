package com.betterdairy.autodense.plugin;

import org.json.JSONObject;
import org.json.JSONArray;
import ij.ImagePlus;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.betterdairy.autodense.registry.CapabilityRegistry;
import org.scijava.Context;

/**
 * Google Gemini 1.5 Pro API client for gel analysis
 * Provides multimodal reasoning and structured responses
 */
public class GeminiApiClient {
    
    private final HttpClient httpClient;
    private final String apiKey;
    private final CapabilityRegistry capabilityRegistry;
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent";
    
    public GeminiApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.capabilityRegistry = new CapabilityRegistry(new Context());
    }
    
    public GeminiApiClient(String apiKey, Context context) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.capabilityRegistry = new CapabilityRegistry(context);
    }
    
    /**
     * Pure HTTP client method - send prompt and image to Gemini API
     * @param prompt The complete prompt text to send
     * @param gelImage The image to analyze (optional)
     * @return Raw JSON response from Gemini API
     */
    public JSONObject sendRequest(String prompt, ImagePlus gelImage) throws Exception {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        
        // Add text part
        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);
        
        // Add image part if provided
        if (gelImage != null) {
            String base64Image = encodeImageToBase64(gelImage);
            JSONObject imagePart = new JSONObject();
            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", base64Image);
            imagePart.put("inline_data", inlineData);
            parts.put(imagePart);
        }
        
        content.put("parts", parts);
        contents.put(content);
        requestBody.put("contents", contents);
        
        // Add generation config for structured output
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 1024);
        requestBody.put("generationConfig", generationConfig);
        
        // Make API request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE_URL + "?key=" + apiKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .timeout(Duration.ofSeconds(40))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }
        
        return new JSONObject(response.body());
    }
    
    /**
     * @deprecated Use sendRequest() instead. This method contains business logic that should be in GeminiOrchestrator.
     */
    @Deprecated
    public GelAnalysisResponse analyzeGel(String userCommand, ImagePlus gelImage) throws Exception {
        // For backward compatibility, delegate to the old logic
        String systemPrompt = createSystemPrompt();
        String analysisPrompt = createAnalysisPrompt(userCommand);
        String fullPrompt = systemPrompt + "\n\n" + analysisPrompt;
        
        JSONObject rawResponse = sendRequest(fullPrompt, gelImage);
        return parseResponse(rawResponse.toString(), userCommand);
    }
    
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
            
            Based on the image analysis and user command, return the structured JSON response.
            Remember: You have the image - analyze it and provide real observations!
        """, userCommand);
    }
    
    private GelAnalysisResponse parseResponse(String responseBody, String originalCommand) throws Exception {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray candidates = jsonResponse.getJSONArray("candidates");
            
            if (candidates.length() == 0) {
                throw new RuntimeException("No response candidates from Gemini API");
            }
            
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");
            
            if (parts.length() == 0) {
                throw new RuntimeException("No response parts from Gemini API");
            }
            
            String responseText = parts.getJSONObject(0).getString("text");
            
            // Extract JSON from response (handle markdown code blocks)
            String jsonStr = responseText;
            if (responseText.contains("```json")) {
                int start = responseText.indexOf("```json") + 7;
                int end = responseText.indexOf("```", start);
                jsonStr = responseText.substring(start, end).trim();
            } else if (responseText.contains("{")) {
                int start = responseText.indexOf("{");
                int end = responseText.lastIndexOf("}") + 1;
                jsonStr = responseText.substring(start, end);
            }
            
            JSONObject analysisJson = new JSONObject(jsonStr);
            
            GelAnalysisResponse response = new GelAnalysisResponse();
            response.imageType = analysisJson.optString("image_type", "gel"); // Default to gel for backwards compatibility
            response.intent = analysisJson.optString("intent", "unknown");
            response.action = analysisJson.optString("action", "unknown");
            response.analysis = analysisJson.optString("analysis", "");
            response.confidence = analysisJson.optDouble("confidence", 0.5);
            response.originalCommand = originalCommand;
            
            // Parse parameters
            if (analysisJson.has("parameters")) {
                JSONObject params = analysisJson.getJSONObject("parameters");
                for (String key : params.keySet()) {
                    Object value = params.get(key);
                    response.parameters.put(key, value);
                }
            }
            
            return response;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini API response: " + e.getMessage() + 
                                     "\nResponse: " + responseBody);
        }
    }
    
    private String encodeImageToBase64(ImagePlus imp) throws Exception {
        BufferedImage bufferedImage = imp.getBufferedImage();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    /**
     * Get the full capability registry JSON for debugging/inspection
     */
    public JSONObject getCapabilityRegistry() {
        return capabilityRegistry.generateCapabilityRegistry();
    }
    
    /**
     * Response structure from Gemini API analysis
     * Now supports both gel electrophoresis and agar plate analysis
     */
    public static class GelAnalysisResponse {
        public String imageType;     // "gel" or "agar_plate"
        public String intent;        // Type of analysis requested
        public String action;        // Action to execute in ImageJ
        public String analysis;      // Gemini's visual analysis description
        public double confidence;    // Confidence in the analysis (0.0-1.0)
        public String originalCommand; // Original user command
        public java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        
        @Override
        public String toString() {
            return String.format("LabAnalysisResponse{type='%s', intent='%s', action='%s', params=%s, confidence=%.2f}", 
                                imageType, intent, action, parameters, confidence);
        }
    }
}