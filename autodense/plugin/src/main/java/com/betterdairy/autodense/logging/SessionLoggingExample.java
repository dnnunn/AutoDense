package com.betterdairy.autodense.logging;

import com.betterdairy.autodense.orchestrator.GeminiOrchestrator;
import com.betterdairy.autodense.session.SessionLogger;
import ij.IJ;
import ij.ImagePlus;
import org.json.JSONObject;

/**
 * Example demonstrating session logging functionality for AutoDense.
 * This shows how all conversations, tool calls, and session events are captured.
 */
public class SessionLoggingExample {
    
    public static void main(String[] args) {
        System.out.println("=== AutoDense Session Logging Demo ===");
        
        // Check if Gemini API key is available
        String apiKey = System.getProperty("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY"));
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("⚠️  GEMINI_API_KEY not found - running basic logging demo without API calls");
            runBasicLoggingDemo();
        } else {
            System.out.println("✓ GEMINI_API_KEY found - running full orchestration demo");
            runFullOrchestrationDemo(apiKey);
        }
    }
    
    /**
     * Demonstrates basic session logging without Gemini API calls
     */
    private static void runBasicLoggingDemo() {
        // Create a session logger
        SessionLogger logger = new SessionLogger("demo_basic");
        
        try {
            System.out.println("\n1. Testing basic session logging...");
            
            // Log some conversation examples
            logger.logConversation("user", "Detect 12 lanes in this gel", 
                new JSONObject().put("demo_mode", true));
                
            logger.logConversation("gemini", "I can see this is a protein gel. I'll detect the lanes for you.", 
                new JSONObject()
                    .put("image_type", "gel")
                    .put("confidence", 0.95)
                    .put("intent", "lane_detection"));
            
            // Log some tool calls
            JSONObject toolRequest = new JSONObject()
                .put("image_handle", "img_demo123")
                .put("expected_lanes", 12)
                .put("constant_spacing", true);
                
            JSONObject toolResponse = new JSONObject()
                .put("lanes_found", 12)
                .put("overlay_handle", "ov_demo456")
                .put("success", true);
                
            logger.logToolCall("detect_lanes", toolRequest, toolResponse, 850, true);
            
            // Log a session event
            logger.logSessionEvent("demo_analysis_complete", 
                "Completed demo gel analysis workflow", 
                new JSONObject().put("lanes_detected", 12).put("demo_mode", true));
            
            // Get session statistics
            JSONObject stats = logger.getSessionStatistics();
            System.out.println("✓ Session Statistics: " + stats.toString(2));
            
            // Force flush logs
            logger.flush();
            System.out.println("✓ Logs flushed to file: " + getLogLocation());
            
            // Close session
            logger.closeSession();
            System.out.println("✓ Session closed and finalized");
            
        } catch (Exception e) {
            System.err.println("❌ Error in basic logging demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrates full orchestration with session logging
     */
    private static void runFullOrchestrationDemo(String apiKey) {
        GeminiOrchestrator orchestrator = new GeminiOrchestrator(apiKey);
        
        try {
            System.out.println("\n1. Testing full orchestration with logging...");
            
            // Create a test image
            ImagePlus testImage = createTestGelImage();
            
            // Load image (this will be logged)
            String imageHandle = orchestrator.loadImage(testImage);
            System.out.println("✓ Test image loaded with handle: " + imageHandle);
            
            // Execute some tools directly (these will be logged)
            JSONObject laneDetectionParams = new JSONObject()
                .put("expected_lanes", 10)
                .put("constant_spacing", true)
                .put("lane_width_fraction", 0.6);
                
            JSONObject laneResult = orchestrator.executeTool("detect_lanes", laneDetectionParams);
            System.out.println("✓ Lane detection result: " + laneResult.optBoolean("success", false));
            
            if (laneResult.optBoolean("success", false)) {
                JSONObject bandParams = new JSONObject()
                    .put("analysis_handle", laneResult.optString("analysis_handle"));
                    
                JSONObject bandResult = orchestrator.executeTool("detect_bands", bandParams);
                System.out.println("✓ Band detection result: " + bandResult.optBoolean("success", false));
            }
            
            // Test user command processing (full conversation + tool execution logging)
            orchestrator.processCommand("Create a labeled reference image for this gel", testImage)
                .thenAccept(result -> {
                    System.out.println("✓ Async command completed: " + result.success);
                });
                
            System.out.println("✓ Async command submitted - this would normally call Gemini API");
            
            // Get session info
            JSONObject sessionInfo = orchestrator.getSessionInfo();
            System.out.println("✓ Session Info: " + sessionInfo.getJSONObject("session_statistics").toString(2));
            
            // Export session log
            JSONObject exportResult = orchestrator.exportSessionLog(System.getProperty("user.home") + "/Downloads");
            System.out.println("✓ Session log export: " + exportResult.toString(2));
            
            // Close orchestrator
            orchestrator.close();
            System.out.println("✓ Orchestrator closed and session finalized");
            
        } catch (Exception e) {
            System.err.println("❌ Error in full orchestration demo: " + e.getMessage());
            e.printStackTrace();
            orchestrator.close();
        }
    }
    
    /**
     * Create a test gel image for demonstration
     */
    private static ImagePlus createTestGelImage() {
        System.out.println("Creating test gel image...");
        
        // Create a simple test image that simulates a gel
        int width = 400;
        int height = 300;
        ImagePlus imp = IJ.createImage("Test Gel", "8-bit", width, height, 1);
        
        // Add some simple pattern to simulate lanes and bands
        for (int x = 50; x < width - 50; x += 30) {
            for (int y = 30; y < height - 30; y += 40) {
                imp.getProcessor().putPixel(x, y, 255);
                imp.getProcessor().putPixel(x+1, y, 255);
                imp.getProcessor().putPixel(x, y+1, 255);
                imp.getProcessor().putPixel(x+1, y+1, 255);
            }
        }
        
        imp.updateAndDraw();
        return imp;
    }
    
    /**
     * Get the expected log file location
     */
    private static String getLogLocation() {
        String logDir = System.getProperty("autodense.log.dir", 
            System.getProperty("user.home") + "/.autodense/logs");
        return logDir;
    }
}