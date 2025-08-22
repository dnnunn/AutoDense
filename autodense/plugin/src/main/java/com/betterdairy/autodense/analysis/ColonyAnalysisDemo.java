package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;

import java.util.List;

/**
 * Demonstration of the complete functional colony analysis workflow:
 * PlateDetector → ColonyDetector → ColonyClassifier → Binner → ColonyOverlay
 * 
 * Shows how pure functional components work together without hidden state.
 */
public class ColonyAnalysisDemo {
    
    public static void main(String[] args) {
        demonstrateCompleteWorkflow();
    }
    
    /**
     * Show complete colony analysis workflow with functional architecture
     */
    public static void demonstrateCompleteWorkflow() {
        System.out.println("=== Colony Analysis Functional Workflow Demo ===");
        System.out.println("Architecture: Pure functions with explicit dependencies\n");
        
        // Create test colony plate image
        ImagePlus testImage = createTestPlateImage();
        
        // Step 1: Detect plate boundary
        System.out.println("1. PLATE DETECTION");
        PlateDetector.Result plate = PlateDetector.detect(testImage, "Triangle");
        System.out.printf("   Plate detected: center=(%.1f, %.1f), radius=%.1f px%n", 
                         plate.centerX(), plate.centerY(), plate.radiusPx());
        System.out.printf("   Ellipse: axes=(%.1f, %.1f), ratio=%.3f, deskewed=%s%n",
                         plate.majorAxis(), plate.minorAxis(), plate.axisRatio(), plate.wasDeskewed());
        
        double pxPerMM = plate.pxPerMM(90.0); // 90mm dish
        System.out.printf("   Calibration: %.2f pixels/mm%n%n", pxPerMM);
        
        // Step 2: Detect colonies
        System.out.println("2. COLONY DETECTION");
        List<Colony> colonies = ColonyDetector.detect(testImage, plate.plateRoi(), 8, 50, true);
        System.out.printf("   Detected %d colonies%n", colonies.size());
        System.out.printf("   Parameters: min=8px, max=50px, split_touching=true%n%n");
        
        // Step 3: Classify colonies using Lab color analysis
        System.out.println("3. COLONY CLASSIFICATION (X-gal Rules)");
        ColonyClassifier.classifyLab(testImage, colonies, plate.plateRoi(), "xgal");
        
        var classSummary = ColonyClassifier.summary(colonies);
        System.out.println("   Classification results:");
        classSummary.forEach((label, count) -> 
            System.out.printf("     %s: %d colonies%n", label, count));
        
        System.out.println("   Rules applied:");
        System.out.println("     xgal_pos: b_delta < -6 AND dE76 > 8 AND SNR_L > 2.5");
        System.out.println("     uncertain: borderline thresholds OR very small");
        System.out.println("     xgal_neg: default classification");
        System.out.println();
        
        // Step 4: Size binning
        System.out.println("4. SIZE BINNING");
        double[] sizeBins = {1.0, 2.5}; // small, medium, large
        List<Colony> binnedColonies = Binner.applyAndReturn(colonies, pxPerMM, sizeBins);
        
        var binSummary = Binner.summary(binnedColonies);
        System.out.println("   Size binning results:");
        binSummary.forEach((bin, count) -> 
            System.out.printf("     %s: %d colonies%n", bin, count));
        
        System.out.printf("   Bin edges: <%.1fmm=small, %.1f-%.1fmm=medium, >%.1fmm=large%n%n",
                         sizeBins[0], sizeBins[0], sizeBins[1], sizeBins[1]);
        
        // Step 5: Cross-plate normalization
        System.out.println("5. CROSS-PLATE NORMALIZATION");
        ColonyNormalizer.NormalizationResult normResult = ColonyNormalizer.normalize(
            testImage, binnedColonies, plate.plateRoi(), pxPerMM, true);
        
        System.out.printf("   Plate stats: radius=%.1fmm, area=%.1fcm², density=%.2f colonies/cm²%n",
                         normResult.plateStats().plateRadiusMm(),
                         normResult.plateStats().plateAreaCm2(),
                         normResult.plateStats().colonyDensityPerCm2());
        
        System.out.printf("   Background Lab: L*=%.1f, a*=%.1f, b*=%.1f%n",
                         normResult.plateStats().backgroundLab().L(),
                         normResult.plateStats().backgroundLab().a(),
                         normResult.plateStats().backgroundLab().b());
        
        var quadrants = normResult.plateStats().quadrantCounts();
        System.out.printf("   Quadrants: Q0=%d, Q1=%d, Q2=%d, Q3=%d%n%n",
                         quadrants.get(0), quadrants.get(1), quadrants.get(2), quadrants.get(3));
        
        // Step 6: Create visualization overlay
        System.out.println("6. OVERLAY RENDERING");
        Overlay detectionOverlay = ColonyOverlay.renderDetection(colonies);
        Overlay classificationOverlay = ColonyOverlay.render(binnedColonies, pxPerMM, true, true);
        
        System.out.printf("   Detection overlay: %d yellow circles at colony boundaries%n", detectionOverlay.size());
        System.out.printf("   Classification overlay: %d colored centroids with size labels%n", classificationOverlay.size());
        System.out.println("   Colors: blue=xgal_pos, orange=xgal_neg, gray=uncertain");
        System.out.println("   Stroke width scaled by pixels/mm for resolution independence");
        System.out.println();
        
        // Show functional architecture benefits
        showArchitectureBenefits(binnedColonies, pxPerMM);
        
        // Cleanup
        testImage.close();
    }
    
    /**
     * Highlight the benefits of the functional architecture
     */
    private static void showArchitectureBenefits(List<Colony> colonies, double pxPerMM) {
        System.out.println("=== FUNCTIONAL ARCHITECTURE BENEFITS ===");
        
        System.out.println("✅ EXPLICIT DEPENDENCIES:");
        System.out.println("  • All functions take required inputs as parameters");
        System.out.println("  • No hidden state or global variables");
        System.out.println("  • Clear data flow: Image → Plate → Colonies → Classes → Bins → Normalize → Overlay");
        
        System.out.println();
        System.out.println("✅ PURE FUNCTIONS:");
        System.out.println("  • Same inputs always produce same outputs");
        System.out.println("  • No side effects - functions don't modify inputs");
        System.out.println("  • Easy to test, debug, and reason about");
        
        System.out.println();
        System.out.println("✅ COMPOSABLE WORKFLOW:");
        System.out.println("  • Each step can be run independently");
        System.out.println("  • Easy to substitute different algorithms");
        System.out.println("  • Pipeline can be modified without breaking other components");
        
        System.out.println();
        System.out.println("✅ REUSABLE COMPONENTS:");
        System.out.println("  • PlateDetector works with any circular plate image");
        System.out.println("  • ColonyClassifier supports both X-gal rules and k-means");
        System.out.println("  • Binner works with any size classification system");
        System.out.println("  • ColonyNormalizer provides cross-plate portable measurements");
        System.out.println("  • ColonyAssist enables user-guided corrections with ML propagation");
        System.out.println("  • ColonyOverlay adapts to different display requirements");
        
        System.out.println();
        System.out.println("✅ TYPE SAFETY:");
        System.out.println("  • Immutable Colony records prevent accidental modification");
        System.out.println("  • Strong typing catches errors at compile time");
        System.out.println("  • Result classes encapsulate related data");
        
        System.out.println();
        System.out.println("EXAMPLE PIPELINE FLEXIBILITY:");
        System.out.println("  // Standard X-gal workflow");
        System.out.println("  plate = PlateDetector.detect(image, 'Triangle')");
        System.out.println("  colonies = ColonyDetector.detect(image, plate.roi, 8, 50, true)");
        System.out.println("  ColonyClassifier.classifyLab(image, colonies, plate.roi, 'xgal')");
        System.out.println("  binned = Binner.applyAndReturn(colonies, pxPerMM, [1.0, 2.5])");
        System.out.println("  normalized = ColonyNormalizer.normalize(image, binned, plate.roi, pxPerMM, true)");
        System.out.println("  overlay = ColonyOverlay.render(binned, pxPerMM, true, true)");
        System.out.println();
        System.out.println("  // Interactive assisted workflow"); 
        System.out.println("  enableColonyAssist(imageHandle)");
        System.out.println("  userClick(245, 180) → add missed colony");
        System.out.println("  relabelColony(180, 220, 'BLUE') → training example");
        System.out.println("  propagateColonyClass('BLUE') → ML reclassification");
        System.out.println("  disableColonyAssist(imageHandle) → finalize results");
    }
    
    /**
     * Create a test colony plate image for demonstration
     */
    private static ImagePlus createTestPlateImage() {
        // Create 400x400 plate image
        ImagePlus image = IJ.createImage("Colony Plate", "RGB", 400, 400, 1);
        
        // Fill with gray background
        IJ.run(image, "Add...", "value=80");
        
        // Create circular plate boundary
        IJ.makeOval(50, 50, 300, 300);
        IJ.setForegroundColor(200, 200, 200);
        IJ.run(image, "Fill", "");
        IJ.run(image, "Select None", "");
        
        // Add some test colonies
        addTestColonies(image);
        
        return image;
    }
    
    /**
     * Add simulated colonies to test image
     */
    private static void addTestColonies(ImagePlus image) {
        // Blue colonies (X-gal positive)
        IJ.setForegroundColor(100, 150, 255);
        IJ.makeOval(120, 120, 25, 25);
        IJ.run(image, "Fill", "");
        IJ.makeOval(180, 160, 20, 20);
        IJ.run(image, "Fill", "");
        IJ.makeOval(250, 180, 30, 30);
        IJ.run(image, "Fill", "");
        
        // White/cream colonies (X-gal negative)
        IJ.setForegroundColor(240, 235, 220);
        IJ.makeOval(160, 120, 22, 22);
        IJ.run(image, "Fill", "");
        IJ.makeOval(220, 140, 18, 18);
        IJ.run(image, "Fill", "");
        IJ.makeOval(280, 220, 25, 25);
        IJ.run(image, "Fill", "");
        
        // Light blue colonies (uncertain)
        IJ.setForegroundColor(180, 190, 230);
        IJ.makeOval(140, 180, 15, 15);
        IJ.run(image, "Fill", "");
        IJ.makeOval(200, 240, 12, 12);
        IJ.run(image, "Fill", "");
        
        IJ.run(image, "Select None", "");
    }
}