package com.betterdairy.autodense.analysis;

import ij.IJ;
import ij.ImagePlus;

/**
 * Demonstration of the core PlateDetector implementation:
 * Threshold → biggest particle → ROI → ellipse fit → deskew if needed
 */
public class CoreDetectorDemo {
    
    public static void main(String[] args) {
        demonstrateCoreDetector();
    }
    
    /**
     * Show the core detector pipeline in action
     */
    public static void demonstrateCoreDetector() {
        System.out.println("=== Core PlateDetector Demo ===");
        
        // Create sample images to test different scenarios
        testCircularPlate();
        testEllipticalPlate();
        testMultipleThresholdMethods();
    }
    
    /**
     * Test with circular plate (no deskewing needed)
     */
    private static void testCircularPlate() {
        System.out.println("\n1. Testing circular plate detection...");
        
        // Create a circular plate image
        ImagePlus circularPlate = IJ.createImage("Circular Plate", "8-bit", 400, 400, 1);
        
        // Add a circular region (simulated plate)
        IJ.run(circularPlate, "Add...", "value=100");
        IJ.makeOval(50, 50, 300, 300);
        IJ.run(circularPlate, "Add...", "value=100");
        IJ.run(circularPlate, "Select None", "");
        
        // Detect using core algorithm
        PlateDetector.Result result = PlateDetector.detect(circularPlate);
        
        System.out.printf("   Center: (%.1f, %.1f)%n", result.centerX(), result.centerY());
        System.out.printf("   Radius: %.1f px%n", result.radiusPx());
        System.out.printf("   Axis ratio: %.3f%n", result.axisRatio());
        System.out.printf("   Was deskewed: %s%n", result.wasDeskewed());
        System.out.printf("   Scale: %.2f px/mm (90mm dish)%n", result.pxPerMM(90.0));
        
        circularPlate.close();
    }
    
    /**
     * Test with elliptical plate (should trigger deskewing)
     */
    private static void testEllipticalPlate() {
        System.out.println("\n2. Testing elliptical plate detection...");
        
        // Create an elliptical plate image (simulating perspective distortion)
        ImagePlus ellipticalPlate = IJ.createImage("Elliptical Plate", "8-bit", 400, 400, 1);
        
        // Add background
        IJ.run(ellipticalPlate, "Add...", "value=50");
        
        // Add an elliptical region (simulated distorted plate)
        IJ.makeOval(60, 80, 280, 240); // Elliptical selection
        IJ.run(ellipticalPlate, "Add...", "value=150");
        IJ.run(ellipticalPlate, "Select None", "");
        
        // Detect using core algorithm
        PlateDetector.Result result = PlateDetector.detect(ellipticalPlate);
        
        System.out.printf("   Center: (%.1f, %.1f)%n", result.centerX(), result.centerY());
        System.out.printf("   Major axis: %.1f px%n", result.majorAxis());
        System.out.printf("   Minor axis: %.1f px%n", result.minorAxis());
        System.out.printf("   Axis ratio: %.3f%n", result.axisRatio());
        System.out.printf("   Angle: %.1f degrees%n", result.angle());
        System.out.printf("   Was deskewed: %s%n", result.wasDeskewed());
        
        if (result.wasDeskewed()) {
            System.out.println("   ✓ Deskewing was applied to correct elliptical distortion");
            System.out.println("   → Transform matrix available for colony coordinate correction");
        }
        
        ellipticalPlate.close();
    }
    
    /**
     * Test different threshold methods
     */
    private static void testMultipleThresholdMethods() {
        System.out.println("\n3. Testing different threshold methods...");
        
        // Create a plate with varying illumination
        ImagePlus varyingPlate = IJ.createImage("Varying Illumination", "8-bit", 300, 300, 1);
        
        // Add gradient background
        IJ.run(varyingPlate, "Add...", "value=30");
        IJ.makeRectangle(0, 0, 150, 300);
        IJ.run(varyingPlate, "Add...", "value=20");
        IJ.run(varyingPlate, "Select None", "");
        
        // Add plate
        IJ.makeOval(25, 25, 250, 250);
        IJ.run(varyingPlate, "Add...", "value=120");
        IJ.run(varyingPlate, "Select None", "");
        
        String[] methods = {"Triangle", "Otsu", "Mean"};
        
        for (String method : methods) {
            try {
                PlateDetector.Result result = PlateDetector.detect(varyingPlate, method);
                System.out.printf("   %s: Center=(%.1f,%.1f), Ratio=%.3f, Deskewed=%s%n", 
                    method, result.centerX(), result.centerY(), 
                    result.axisRatio(), result.wasDeskewed());
            } catch (Exception e) {
                System.out.printf("   %s: FAILED (%s)%n", method, e.getMessage());
            }
        }
        
        varyingPlate.close();
    }
    
    /**
     * Demonstrate the key benefits of the core detector approach
     */
    public static void showCoreDetectorBenefits() {
        System.out.println("\n=== Core Detector Benefits ===");
        
        System.out.println("✓ SIMPLE PIPELINE:");
        System.out.println("  1. Threshold → biggest particle");  
        System.out.println("  2. Fit ellipse to particle");
        System.out.println("  3. Check axis ratio < 0.95");
        System.out.println("  4. Deskew if needed → update ROI");
        
        System.out.println("\n✓ HANDLES REAL-WORLD ISSUES:");
        System.out.println("  • Perspective distortion (camera angle)");
        System.out.println("  • Varying illumination (multiple threshold methods)");
        System.out.println("  • Automatic scale calibration (px/mm)");
        
        System.out.println("\n✓ PURE FUNCTIONAL DESIGN:");
        System.out.println("  • Input: ImagePlus + threshold method");
        System.out.println("  • Output: Complete Result record with all parameters");
        System.out.println("  • No side effects or hidden state");
        
        System.out.println("\n✓ READY FOR INTEGRATION:");
        System.out.println("  • Works with existing ColonyAnalysisTools");
        System.out.println("  • Provides deskew transform for colony coordinate correction");
        System.out.println("  • Extensible for additional detection methods");
    }
}