package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.tools.ColonyAnalysisTools;
import ij.IJ;
import ij.ImagePlus;
import org.json.JSONObject;

import java.util.List;

/**
 * Demonstration of the clean functional API vs the old monolithic approach.
 */
public class FunctionalApiDemo {
    
    public static void main(String[] args) {
        demonstrateNewFunctionalAPI();
    }
    
    /**
     * Show how the new functional API makes colony analysis simple and testable
     */
    public static void demonstrateNewFunctionalAPI() {
        System.out.println("=== New Functional API Demo ===");
        
        // Load a sample image (would be real data in practice)
        ImagePlus sampleImage = IJ.createImage("Sample Plate", "8-bit", 800, 600, 1);
        
        // 1. Pure function: Detect plate boundary
        System.out.println("1. Detecting plate boundary...");
        PlateDetector.Result plate = PlateDetector.detect(sampleImage);
        System.out.printf("   Plate center: (%.1f, %.1f), radius: %.1f px%n", 
                         plate.centerX(), plate.centerY(), plate.radiusPx());
        System.out.printf("   Scale: %.2f px/mm%n", plate.pxPerMM(90.0));
        
        // 2. Pure function: Detect colonies
        System.out.println("2. Detecting colonies...");
        List<Colony> colonies = ColonyDetector.detect(sampleImage, plate.plateRoi());
        System.out.printf("   Found %d colonies%n", colonies.size());
        
        // 3. Pure function: Classify colonies
        System.out.println("3. Classifying colonies...");
        ColonyClassifier.classifyLab(sampleImage, colonies, plate.plateRoi(), "xgal");
        var summary = ColonyClassifier.summary(colonies);
        System.out.printf("   Classification results: %s%n", summary);
        
        // 4. Pure function: Create overlay
        System.out.println("4. Creating visualization...");
        var overlay = ColonyOverlay.render(colonies, plate.pxPerMM(90.0));
        System.out.printf("   Created overlay with %d elements%n", overlay.size());
        
        System.out.println("\n=== Benefits of Functional Design ===");
        System.out.println("✓ Pure functions - no hidden state");
        System.out.println("✓ Easy to test - deterministic inputs/outputs");
        System.out.println("✓ Composable - functions can be chained");
        System.out.println("✓ Readable - clear data flow");
        System.out.println("✓ Maintainable - single responsibility");
        
        // Example of composability
        demonstrateComposition();
        
        sampleImage.close();
    }
    
    /**
     * Show how functional design enables easy composition
     */
    private static void demonstrateComposition() {
        System.out.println("\n=== Composition Example ===");
        
        // Create sample image
        ImagePlus image = IJ.createImage("Test", "8-bit", 400, 400, 1);
        
        // One-liner pipeline composition
        var pipeline = PlateDetector.detect(image)
            .plateRoi();
        
        var colonies = ColonyDetector.detect(image, pipeline);
        
        System.out.printf("Pipeline result: %d colonies detected%n", colonies.size());
        
        image.close();
    }
    
    /**
     * Example of how this integrates with the tools layer
     */
    public static void demonstrateToolsIntegration() {
        System.out.println("\n=== Tools Integration ===");
        
        SessionStore store = new SessionStore();
        
        // Tools layer provides clean JSON API while using functional core
        JSONObject detectArgs = new JSONObject()
            .put("image_handle", "test_image")
            .put("dish_diameter_mm", 90.0);
            
        JSONObject result = ColonyAnalysisTools.detectPlate(detectArgs, store);
        
        System.out.println("Tools layer result: " + result.toString(2));
    }
}