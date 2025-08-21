package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.analysis.AssistModels.AssistBand;
import ij.IJ;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstration of the BandAssist feature for AutoDense.
 * Shows how to use user-assisted band identification.
 */
public class BandAssistDemo {
    
    public static void main(String[] args) {
        System.out.println("=== BandAssist Demo ===");
        
        // Create a test gel image
        ImagePlus testGel = createTestGelImage();
        testGel.show(); // Display for user interaction
        
        // Create mock lanes (in real usage, these come from lane detection)
        List<Lane> testLanes = createTestLanes();
        System.out.println("✓ Created " + testLanes.size() + " test lanes");
        
        // Test the helper classes
        testProfileAnalysis(testGel, testLanes.get(0));
        
        // Create and test BandAssist tool
        AssistBandTool assistTool = new AssistBandTool(testLanes);
        
        // Configure for better detection of demo bands
        assistTool.setMinProminence(0.01f); // Lower threshold for demo
        assistTool.setMinSnr(1.0); // Lower SNR requirement
        assistTool.setSearchWindow(30); // Larger search window
        
        // Enable assist mode
        assistTool.enable(testGel);
        System.out.println("✓ BandAssist enabled - you can now click on bands in the image");
        System.out.println("ℹ️ Click on any band to see it identified across all lanes");
        System.out.println("ℹ️ Close the image window or press Ctrl+C to exit");
        
        // Keep the program running to allow user interaction
        try {
            // Wait for user to close the image window
            while (testGel.getWindow() != null && testGel.getWindow().isVisible()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assistTool.disable();
        System.out.println("✓ BandAssist demo complete");
    }
    
    /**
     * Create a test gel image with simulated bands
     */
    private static ImagePlus createTestGelImage() {
        System.out.println("Creating test gel image with simulated bands...");
        
        int width = 600;
        int height = 400;
        ImagePlus imp = IJ.createImage("BandAssist Demo Gel", "8-bit", width, height, 1);
        
        // First, add a low background
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                imp.getProcessor().putPixel(x, y, 50 + (int)(Math.random() * 20)); // 50-70 background
            }
        }
        
        // Add simulated lanes and bands with higher contrast
        for (int lane = 0; lane < 4; lane++) {
            int laneX = 100 + lane * 120;
            int laneWidth = 80;
            
            // Add several prominent bands per lane
            int[] bandYPositions = {60, 140, 200, 260, 320};
            for (int bandY : bandYPositions) {
                // Create band with some variation between lanes
                int actualY = bandY + (int)(Math.random() * 6 - 3); // ±3px variation
                int bandHeight = 12 + (int)(Math.random() * 4); // 12-16px height
                
                for (int x = laneX; x < laneX + laneWidth; x++) {
                    for (int y = actualY; y < actualY + bandHeight; y++) {
                        if (x >= 0 && x < width && y >= 0 && y < height) {
                            // High intensity bands with good contrast
                            int intensity = 220 + (int)(Math.random() * 35); // 220-255
                            imp.getProcessor().putPixel(x, y, intensity);
                        }
                    }
                }
            }
        }
        
        imp.updateAndDraw();
        return imp;
    }
    
    /**
     * Create test lane definitions
     */
    private static List<Lane> createTestLanes() {
        List<Lane> lanes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int xStart = 80 + i * 120;
            int xEnd = xStart + 100;
            lanes.add(new Lane(i + 1, xStart, xEnd));
        }
        return lanes;
    }
    
    /**
     * Test the profile analysis components
     */
    private static void testProfileAnalysis(ImagePlus imp, Lane testLane) {
        System.out.println("\nTesting profile analysis components:");
        
        // Test vertical profile
        float[] profile = Profiles.verticalSum(imp, testLane.xStart(), testLane.xEnd());
        System.out.println("✓ Profile generated: " + profile.length + " points");
        
        // Test peak detection
        int peak = Peaks.argmax(profile, 40, 60);
        boolean isProminent = Peaks.isProminent(profile, peak, 0.05f);
        System.out.println("✓ Peak detection: peak at y=" + peak + ", prominent=" + isProminent);
        
        // Test band quantification
        if (isProminent) {
            AssistBand testBand = Quant.integrateBand(imp, testLane, peak - 4, peak + 4);
            System.out.println("✓ Band quantification: area=" + String.format("%.0f", testBand.areaCorr) + 
                             ", SNR=" + String.format("%.1f", testBand.snr));
        }
        
        System.out.println();
    }
}