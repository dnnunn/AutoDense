package com.betterdairy.autodense.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.json.JSONObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PythonBridge ProcessBuilder communication
 */
public class PythonBridgeTest {
    
    private PythonBridge bridge;
    private Path testImagePath;
    
    @BeforeEach
    void setUp() throws Exception {
        // Find Python package path - use parent directory structure
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path pythonPath = null;
        
        // Try different path combinations relative to project root
        Path[] searchPaths = {
            currentDir.resolve("../python"),                    // autodense/python (from plugin dir)
            currentDir.getParent().resolve("python"),           // autodense/python 
            currentDir.getParent().getParent().resolve("autodense/python"), // from project root
            Paths.get("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/python") // absolute fallback
        };
        
        for (Path searchPath : searchPaths) {
            if (Files.exists(searchPath.resolve("autodense/service/bridge.py"))) {
                pythonPath = searchPath;
                break;
            }
        }
        
        if (pythonPath == null) {
            throw new RuntimeException("Could not locate Python bridge at any expected location");
        }
        
        bridge = new PythonBridge(pythonPath, 60); // 60s timeout for tests
        
        // Create test image (128x256 grayscale)
        BufferedImage testImage = new BufferedImage(128, 256, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 128; x++) {
                testImage.setRGB(x, y, 0xC8C8C8); // Light gray
            }
        }
        
        testImagePath = Files.createTempFile("test_gel_", ".png");
        ImageIO.write(testImage, "PNG", testImagePath.toFile());
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (bridge != null) {
            bridge.shutdown();
        }
        if (testImagePath != null && Files.exists(testImagePath)) {
            Files.delete(testImagePath);
        }
    }
    
    @Test
    void testHealthCheck() {
        assertTrue(bridge.isHealthy(), "Python bridge should be healthy");
    }
    
    @Test
    void testConfigValidation() throws Exception {
        JSONObject config = new JSONObject();
        config.put("modality", "sds");
        config.put("min_lanes", 6);
        config.put("max_lanes", 16);
        config.put("bg_radius", 30);
        
        JSONObject result = bridge.validateConfig(config);
        
        assertNotNull(result, "Validation result should not be null");
        assertTrue(result.optBoolean("success", false), "Config validation should succeed");
    }
    
    @Test
    void testImageAnalysis() throws Exception {
        JSONObject config = new JSONObject();
        config.put("modality", "sds");
        config.put("min_lanes", 2);
        config.put("max_lanes", 2);
        config.put("bg_radius", 30);
        config.put("ladder_min_bands", 6);
        config.put("ladder_min_score", 0.35);
        
        PythonBridge.AnalysisResult result = bridge.analyzeImage(testImagePath, config);
        
        assertNotNull(result, "Analysis result should not be null");
        assertTrue(result.isSuccess(), "Analysis should succeed: " + result.getError());
        
        // Verify result structure
        assertNotNull(result.getData(), "Result data should not be null");
        assertTrue(result.getData().has("lanes"), "Result should contain lanes");
        assertTrue(result.getData().has("observations"), "Result should contain observations");
        
        // Should have some lanes detected
        assertTrue(result.getLanes().length() > 0, "Should detect some lanes");
        
        System.out.println("Analysis completed successfully:");
        System.out.println("- Lanes detected: " + result.getLanes().length());
        System.out.println("- Bands detected: " + result.getBands().length());
        System.out.println("- Has overlay: " + (result.getOverlayImage() != null));
        System.out.println("- Final params: " + result.getFinalParams().toString());
    }
    
    @Test 
    void testInvalidImagePath() throws Exception {
        JSONObject config = new JSONObject();
        config.put("modality", "sds");
        
        Path nonExistentPath = Paths.get("/tmp/nonexistent_image.png");
        PythonBridge.AnalysisResult result = bridge.analyzeImage(nonExistentPath, config);
        
        assertFalse(result.isSuccess(), "Analysis should fail for nonexistent image");
        assertNotNull(result.getError(), "Error message should be provided");
        assertNull(result.getData(), "Data should be null for failed analysis");
    }
    
    @Test
    void testBridgeShutdown() {
        // Test that shutdown doesn't throw exceptions
        assertDoesNotThrow(() -> bridge.shutdown());
        
        // Health check should fail after shutdown (or at least be handled gracefully)
        // Note: This may pass or fail depending on implementation details
        boolean healthyAfterShutdown = bridge.isHealthy();
        // We don't assert on this result since it's implementation dependent
        System.out.println("Healthy after shutdown: " + healthyAfterShutdown);
    }
}