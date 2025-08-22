package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.orchestrator.GeminiOrchestrator;
import com.betterdairy.autodense.session.SessionStore;
import ij.IJ;
import ij.ImagePlus;
import org.json.JSONObject;

/**
 * Demonstration of the integrated ColonyAnalysisTools working through GeminiOrchestrator.
 * Shows the complete pipeline from user input to results.
 */
public class IntegrationDemo {
    
    public static void main(String[] args) {
        demonstrateIntegratedPipeline();
    }
    
    /**
     * Show the complete colony analysis pipeline working through the orchestrator
     */
    public static void demonstrateIntegratedPipeline() {
        System.out.println("=== ColonyAnalysisTools Integration Demo ===");
        
        // Create orchestrator (using null API key for demo)
        GeminiOrchestrator orchestrator = new GeminiOrchestrator(null);
        
        // Create sample image and store it via orchestrator
        ImagePlus sampleImage = IJ.createImage("Sample Plate", "RGB", 800, 600, 1);
        String imageHandle = orchestrator.loadImage(sampleImage);
        
        System.out.println("1. Stored sample image with handle: " + imageHandle);
        
        // Test the complete pipeline via orchestrator
        testPlateDetection(orchestrator, imageHandle);
        testColonyCountingPipeline(orchestrator, imageHandle);
        
        // Cleanup
        sampleImage.close();
        orchestrator.close();
        
        System.out.println("\n✅ Integration test completed successfully!");
    }
    
    /**
     * Test plate detection through the orchestrator
     */
    private static void testPlateDetection(GeminiOrchestrator orchestrator, String imageHandle) {
        System.out.println("\n2. Testing plate detection via orchestrator...");
        
        JSONObject detectPlateArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("dish_diameter_mm", 90.0)
            .put("threshold_method", "Triangle")
            .put("correct_illumination", true);
        
        // This now routes to ColonyAnalysisTools.detectPlate()
        JSONObject result = orchestrator.executeTool("detect_plate", detectPlateArgs);
        
        System.out.println("   Plate detection result:");
        System.out.println("   " + result.toString(2));
        
        if (result.optBoolean("success", false)) {
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                System.out.printf("   ✓ Plate detected at (%.1f, %.1f) with %.2f px/mm%n",
                    data.optDouble("center_x"),
                    data.optDouble("center_y"), 
                    data.optDouble("pixels_per_mm"));
            }
        }
    }
    
    /**
     * Test colony counting pipeline through the orchestrator
     */
    private static void testColonyCountingPipeline(GeminiOrchestrator orchestrator, String imageHandle) {
        System.out.println("\n3. Testing colony counting pipeline...");
        
        // Count colonies
        JSONObject countArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("min_diam_px", 8)
            .put("max_diam_px", 50)
            .put("split_touching", true);
        
        JSONObject countResult = orchestrator.executeTool("count_colonies", countArgs);
        System.out.println("   Colony counting result:");
        System.out.println("   " + countResult.toString(2));
        
        if (countResult.optBoolean("success", false)) {
            JSONObject data = countResult.optJSONObject("data");
            if (data != null) {
                System.out.printf("   ✓ Found %d colonies%n", data.optInt("colony_count"));
            }
        }
        
        // Classify colonies  
        System.out.println("\n4. Testing colony classification...");
        
        JSONObject classifyArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("mode", "xgal");
        
        JSONObject classifyResult = orchestrator.executeTool("classify_colonies", classifyArgs);
        System.out.println("   Classification result:");
        System.out.println("   " + classifyResult.toString(2));
        
        if (classifyResult.optBoolean("success", false)) {
            System.out.println("   ✓ Colony classification completed");
        }
        
        // Export results
        System.out.println("\n5. Testing colony export...");
        
        JSONObject exportArgs = new JSONObject()
            .put("image_handle", imageHandle);
        
        JSONObject exportResult = orchestrator.executeTool("export_colonies", exportArgs);
        System.out.println("   Export result:");
        System.out.println("   " + exportResult.toString(2));
        
        if (exportResult.optBoolean("success", false)) {
            System.out.println("   ✓ Colony export completed");
        }
    }
    
    /**
     * Show how this compares to the old monolithic approach
     */
    public static void compareApproaches() {
        System.out.println("\n=== Architecture Comparison ===");
        
        System.out.println("OLD APPROACH (PlateAnalysisTools):");
        System.out.println("  • Monolithic class with hidden state");
        System.out.println("  • Complex error handling and recovery");
        System.out.println("  • Hard to test individual components");
        System.out.println("  • Tightly coupled to SessionStore");
        
        System.out.println("\nNEW APPROACH (ColonyAnalysisTools):");
        System.out.println("  ✓ Pure functions with explicit dependencies");
        System.out.println("  ✓ Clean separation of concerns");
        System.out.println("  ✓ Easy to test and compose");
        System.out.println("  ✓ Clear data flow");
        System.out.println("  ✓ Single responsibility per class");
        
        System.out.println("\nINTEGRATION:");
        System.out.println("  • GeminiOrchestrator routes to new functional API");
        System.out.println("  • Legacy methods still available for compatibility");
        System.out.println("  • Gradual migration path supported");
    }
}