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
 * OpenAI ChatGPT Vision API client for gel analysis
 * Provides multimodal reasoning and structured responses using GPT-4V
 */
public class OpenAiApiClient {
    
    private final HttpClient httpClient;
    private final String apiKey;
    private final CapabilityRegistry capabilityRegistry;
    private static final String API_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL_NAME = "gpt-5"; // GPT-5 has enhanced vision capabilities
    
    public OpenAiApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.capabilityRegistry = new CapabilityRegistry(new Context());
    }
    
    public OpenAiApiClient(String apiKey, Context context) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.capabilityRegistry = new CapabilityRegistry(context);
    }
    
    /**
     * Pure HTTP client method - send prompt and image to OpenAI ChatGPT Vision API
     * @param prompt The complete prompt text to send
     * @param gelImage The image to analyze (optional)
     * @return Raw JSON response from OpenAI API
     */
    public JSONObject sendRequest(String prompt, ImagePlus gelImage) throws Exception {
        System.out.println("DEBUG: OpenAiApiClient.sendRequest() called");
        System.out.println("DEBUG: Prompt length: " + prompt.length() + " characters");
        System.out.println("DEBUG: Has image: " + (gelImage != null));
        if (gelImage != null) {
            System.out.println("DEBUG: Image dimensions: " + gelImage.getWidth() + "x" + gelImage.getHeight());
        }
        
        JSONObject requestBody = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        
        if (gelImage != null) {
            // For images, use content array with text and image_url parts
            JSONArray content = new JSONArray();
            
            // Add text part
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            content.put(textPart);
            
            // Add image part
            String base64Image = encodeImageToBase64(gelImage);
            JSONObject imagePart = new JSONObject();
            imagePart.put("type", "image_url");
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
            imagePart.put("image_url", imageUrl);
            content.put(imagePart);
            
            message.put("content", content);
        } else {
            // For text-only, use simple string content
            message.put("content", prompt);
        }
        
        messages.put(message);
        requestBody.put("messages", messages);
        requestBody.put("model", MODEL_NAME);
        
        // GPT-5 uses different parameters
        if (MODEL_NAME.contains("gpt-5")) {
            requestBody.put("max_completion_tokens", 1024);
            // GPT-5 only supports default temperature of 1.0
        } else {
            requestBody.put("max_tokens", 1024);
            requestBody.put("temperature", 0.1);
        }
        
        // Make API request with retry logic
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .timeout(Duration.ofSeconds(40))
            .build();
        
        HttpResponse<String> response = null;
        int maxRetries = 3;
        Exception lastException = null;
        
        // Debug: Log request details (without sensitive data)
        System.out.println("DEBUG: Making request to: " + API_BASE_URL);
        System.out.println("DEBUG: Request method: POST");
        System.out.println("DEBUG: Request timeout: 40 seconds");
        System.out.println("DEBUG: Content-Type: application/json");
        System.out.println("DEBUG: Model: " + MODEL_NAME);
        System.out.println("DEBUG: Request body size: " + requestBody.toString().length() + " characters");
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("DEBUG: Attempt " + attempt + "/" + maxRetries + " - Sending HTTP request...");
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("DEBUG: HTTP request successful! Status: " + response.statusCode());
                break; // Success, exit retry loop
            } catch (java.net.ConnectException e) {
                lastException = e;
                System.err.println("DEBUG: ConnectException details: " + e.getClass().getName() + ": " + e.getMessage());
                System.err.println("DEBUG: Full exception stack trace:");
                e.printStackTrace();
                if (attempt < maxRetries) {
                    System.err.println("Connection failed, retrying in " + (attempt * 2) + " seconds... (attempt " + attempt + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(attempt * 2000); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Request interrupted", ie);
                    }
                } else {
                    throw new RuntimeException("Failed to connect to OpenAI API after " + maxRetries + " attempts. Check your internet connection and API key.", lastException);
                }
            } catch (Exception e) {
                System.err.println("DEBUG: Unexpected exception: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Unexpected error during API request: " + e.getMessage(), e);
            }
        }
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }
        
        return new JSONObject(response.body());
    }
    
    /**
     * @deprecated Use sendRequest() instead. This method contains business logic that should be in orchestrator.
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
            JSONArray choices = jsonResponse.getJSONArray("choices");
            
            if (choices.length() == 0) {
                throw new RuntimeException("No response choices from OpenAI API");
            }
            
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String responseText = message.getString("content");
            
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
            throw new RuntimeException("Failed to parse OpenAI API response: " + e.getMessage() + 
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
     * Response structure from OpenAI API analysis
     * Supports both gel electrophoresis and agar plate analysis
     */
    public static class GelAnalysisResponse {
        public String imageType;     // "gel" or "agar_plate"
        public String intent;        // Type of analysis requested
        public String action;        // Action to execute in ImageJ
        public String analysis;      // AI's visual analysis description
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