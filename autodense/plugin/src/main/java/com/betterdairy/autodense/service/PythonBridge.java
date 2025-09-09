package com.betterdairy.autodense.service;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Base64;

/**
 * Java bridge to the Python AutoDense analysis engine.
 * 
 * Provides clean integration between Java UI components and the Python
 * computer vision engine via ProcessBuilder-based JSON communication.
 * 
 * Key Features:
 * - Lane/band detection via Python vision pipeline
 * - MW calibration and QC overlay generation
 * - Robust error handling with timeout protection
 * - Base64 overlay image transfer
 * - Structured JSON request/response protocol
 */
public class PythonBridge {
    
    private static final Logger logger = Logger.getLogger(PythonBridge.class.getName());
    
    private final Path pythonPackagePath;
    private final int timeoutSeconds;
    private final String pythonExecutable;
    
    /**
     * Result container for Python analysis responses
     */
    public static class AnalysisResult {
        private final boolean success;
        private final JSONObject data;
        private final String error;
        private final BufferedImage overlayImage;
        
        public AnalysisResult(boolean success, JSONObject data, String error, BufferedImage overlayImage) {
            this.success = success;
            this.data = data;
            this.error = error;
            this.overlayImage = overlayImage;
        }
        
        public boolean isSuccess() { return success; }
        public JSONObject getData() { return data; }
        public String getError() { return error; }
        public BufferedImage getOverlayImage() { return overlayImage; }
        
        public JSONArray getLanes() { 
            return data != null && data.has("lanes") ? data.getJSONArray("lanes") : new JSONArray(); 
        }
        public JSONArray getBands() { 
            return data != null && data.has("bands") ? data.getJSONArray("bands") : new JSONArray(); 
        }
        public JSONObject getFinalParams() { 
            return data != null && data.has("final_params") ? data.getJSONObject("final_params") : new JSONObject(); 
        }
        public JSONObject getObservations() { 
            return data != null && data.has("observations") ? data.getJSONObject("observations") : new JSONObject(); 
        }
    }
    
    /**
     * Initialize Python bridge.
     * 
     * @param pythonPackagePath Path to the AutoDense Python package directory
     * @param timeoutSeconds Timeout for Python operations (default: 30s)
     */
    public PythonBridge(Path pythonPackagePath, int timeoutSeconds) {
        this.pythonPackagePath = pythonPackagePath;
        this.timeoutSeconds = timeoutSeconds;
        this.pythonExecutable = discoverPythonExecutable();
    }
    
    /**
     * Convenience constructor with default 30s timeout
     */
    public PythonBridge(Path pythonPackagePath) {
        this(pythonPackagePath, 30);
    }
    
    /**
     * Discover available Python executable
     */
    private String discoverPythonExecutable() {
        String[] candidates = {"python3", "python", "/opt/anaconda3/envs/bio_env/bin/python"};
        
        for (String candidate : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                
                if (finished && process.exitValue() == 0) {
                    logger.info("Found Python executable: " + candidate);
                    return candidate;
                }
            } catch (Exception e) {
                // Try next candidate
            }
        }
        
        logger.warning("No Python executable found, falling back to 'python'");
        return "python"; // Fallback
    }
    
    /**
     * Check if Python bridge is available and healthy
     * 
     * @return true if bridge is operational
     */
    public boolean isHealthy() {
        try {
            JSONObject response = sendSingleRequest("get_status", new JSONObject());
            return response != null && response.optBoolean("success", false);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Health check failed", e);
            return false;
        }
    }
    
    /**
     * Validate configuration parameters
     * 
     * @param config Configuration parameters to validate
     * @return Validation result with errors/warnings
     */
    public JSONObject validateConfig(JSONObject config) throws IOException, InterruptedException {
        JSONObject request = new JSONObject();
        request.put("config", config);
        
        return sendSingleRequest("validate_config", request);
    }
    
    /**
     * Perform gel/colony analysis on an image
     * 
     * @param imagePath Path to the image file
     * @param config Analysis configuration parameters  
     * @return Analysis results including lanes, bands, overlay, and metrics
     */
    public AnalysisResult analyzeImage(Path imagePath, JSONObject config) throws IOException, InterruptedException {
        JSONObject request = new JSONObject();
        request.put("image_path", imagePath.toString());
        request.put("config", config);
        
        JSONObject response = sendSingleRequest("analyze_gel", request);
        
        if (response == null) {
            return new AnalysisResult(false, null, "No response from Python bridge", null);
        }
        
        boolean success = response.optBoolean("success", false);
        String error = response.optString("error", null);
        
        // For successful responses, the result data is directly in the response
        // For failed responses, we still return the response structure
        JSONObject result = success ? response : null;
        
        // Decode base64 overlay image if present
        BufferedImage overlayImage = null;
        if (result != null && result.has("overlay_png_b64")) {
            try {
                String base64Data = result.getString("overlay_png_b64");
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                overlayImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                logger.info("Successfully decoded overlay image (" + imageBytes.length + " bytes)");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to decode overlay image", e);
            }
        }
        
        return new AnalysisResult(success, result, error, overlayImage);
    }
    
    
    /**
     * Send single request to Python bridge (starts process if needed)
     */
    private JSONObject sendSingleRequest(String method, JSONObject params) throws IOException, InterruptedException {
        // Use single-shot mode for simplicity and reliability
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "autodense.service.bridge");
        
        // Add parameters for specific methods based on Python bridge CLI
        if ("analyze_gel".equals(method) && params.has("image_path")) {
            pb.command().add("--analyze");
            pb.command().add(params.getString("image_path"));
            
            if (params.has("config")) {
                // Write config to temp file for complex parameters
                File tempConfig = File.createTempFile("autodense_config_", ".json");
                tempConfig.deleteOnExit();
                try (FileWriter fw = new FileWriter(tempConfig)) {
                    fw.write(params.getJSONObject("config").toString());
                }
                pb.command().add("--config");
                pb.command().add(tempConfig.getAbsolutePath());
            }
        } else if ("validate_config".equals(method) && params.has("config")) {
            // Write config to temp file
            File tempConfig = File.createTempFile("autodense_config_", ".json");
            tempConfig.deleteOnExit();
            try (FileWriter fw = new FileWriter(tempConfig)) {
                fw.write(params.getJSONObject("config").toString());
            }
            pb.command().add("--validate-config");
            pb.command().add(tempConfig.getAbsolutePath());
        } else if ("get_status".equals(method)) {
            pb.command().add("--status");
        }
        
        pb.directory(pythonPackagePath.toFile());
        
        Process process = pb.start();
        
        // Wait for completion with timeout
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Python bridge timeout after " + timeoutSeconds + " seconds");
        }
        
        // Read response from stdout
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // Log any errors from stderr
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                logger.info("Python stderr: " + line);
            }
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Python bridge failed with exit code: " + exitCode);
        }
        
        String responseText = output.toString().trim();
        if (responseText.isEmpty()) {
            return null;
        }
        
        try {
            JSONObject response = new JSONObject(responseText);
            
            // Handle the Python bridge response format (which includes success/result structure)
            if (response.has("success") && response.optBoolean("success", false)) {
                // For successful responses, return the nested result if available
                if (response.has("result")) {
                    JSONObject result = response.getJSONObject("result");
                    result.put("success", true); // Preserve success flag
                    return result;
                } else {
                    return response; // Return the response as-is for status checks
                }
            } else {
                // For failed responses, preserve the error information
                return response;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse JSON response: " + responseText, e);
            // Return a structured error response instead of null
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to parse Python bridge response: " + e.getMessage());
            errorResponse.put("raw_response", responseText);
            return errorResponse;
        }
    }
    
    /**
     * Clean shutdown of bridge resources.
     * Since we use single-shot processes, this is mainly for consistency.
     */
    public void shutdown() {
        // No persistent resources to clean up in single-shot mode
        logger.info("PythonBridge shutdown completed");
    }
    
    /**
     * Create default AutoDense Python bridge
     * 
     * @return PythonBridge instance configured for AutoDense
     */
    public static PythonBridge createDefault() {
        // Look for Python package in expected locations
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path[] searchPaths = {
            currentDir.resolve("../python"),                        // from plugin dir to python dir
            currentDir.getParent().resolve("python"),               // from autodense/plugin to autodense/python
            currentDir.resolve("autodense/python"),                 // direct relative path
            Paths.get("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/python"), // absolute path
            currentDir.resolve("scripts")                           // legacy scripts location
        };
        
        for (Path searchPath : searchPaths) {
            try {
                Path bridgeModule = searchPath.resolve("autodense/service/bridge.py");
                if (Files.exists(bridgeModule)) {
                    logger.info("Found Python bridge at: " + searchPath.toAbsolutePath());
                    return new PythonBridge(searchPath);
                }
            } catch (Exception e) {
                // Continue searching
            }
        }
        
        throw new RuntimeException("Could not locate AutoDense Python package. " +
                "Searched paths: " + java.util.Arrays.toString(searchPaths) + 
                ". Please ensure autodense/python is accessible.");
    }
}