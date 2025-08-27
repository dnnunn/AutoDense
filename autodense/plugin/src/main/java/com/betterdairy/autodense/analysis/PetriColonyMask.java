package com.betterdairy.autodense.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical helper for standardized Petri colony detection.
 * Pipeline: BI threshold → open → fill holes → ParticleAnalyzer with size/circularity filters.
 */
public final class PetriColonyMask {
    
    private PetriColonyMask() {} // Utility class
    
    /**
     * Standard colony detection parameters
     */
    public static class ColonyParams {
        public int minSize = 50;           // Minimum colony area in pixels
        public int maxSize = 5000;         // Maximum colony area in pixels
        public double minCircularity = 0.3; // Minimum circularity (0.0-1.0)
        public double maxCircularity = 1.0;  // Maximum circularity
        public double blueThreshold = -6.0;  // CIELAB b* threshold for blue detection
    }
    
    /**
     * Detect colonies using standardized pipeline.
     * 
     * @param imp Source image
     * @param params Colony detection parameters
     * @return List of detected colony regions
     */
    public static List<Rectangle> detectColonies(ImagePlus imp, ColonyParams params) {
        // Step 1: Create blue index mask
        ImagePlus blueIndexImage = createBlueIndexMask(imp, params.blueThreshold);
        
        // Step 2: Morphological opening to remove noise
        IJ.run(blueIndexImage, "Options...", "iterations=2 count=1 black do=Open");
        
        // Step 3: Fill holes in colonies
        IJ.run(blueIndexImage, "Fill Holes", "");
        
        // Step 4: Particle analysis with size and shape filters
        return analyzeParticles(blueIndexImage, params);
    }
    
    /**
     * Create binary mask based on CIELAB blue index threshold.
     */
    private static ImagePlus createBlueIndexMask(ImagePlus imp, double blueThreshold) {
        ImageProcessor ip = imp.getProcessor();
        ImageProcessor mask = ip.createProcessor(ip.getWidth(), ip.getHeight());
        
        // Convert to color processor if needed
        if (imp.getType() != ImagePlus.COLOR_RGB) {
            ip = ip.convertToColorProcessor();
        }
        
        // Apply blue index threshold
        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x < ip.getWidth(); x++) {
                int rgb = ip.getPixel(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                double bStar = BlueIndex.bStar(r, g, b);
                
                // Set pixel to white (255) if below blue threshold, black (0) otherwise
                int maskValue = (bStar < blueThreshold) ? 255 : 0;
                mask.putPixel(x, y, maskValue);
            }
        }
        
        return new ImagePlus(imp.getTitle() + "_blue_mask", mask);
    }
    
    /**
     * Run particle analysis with size and circularity filters.
     */
    private static List<Rectangle> analyzeParticles(ImagePlus maskImage, ColonyParams params) {
        List<Rectangle> colonies = new ArrayList<>();
        
        // Configure measurements
        String measurements = "area centroid bounding shape redirect=None decimal=3";
        String particleOptions = String.format(
            "size=%d-%d circularity=%.2f-%.2f show=Nothing display clear",
            params.minSize, params.maxSize, params.minCircularity, params.maxCircularity
        );
        
        // Run particle analysis
        IJ.run(maskImage, "Set Measurements...", measurements);
        IJ.run(maskImage, "Analyze Particles...", particleOptions);
        
        // Extract bounding boxes from results
        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt != null && rt.getCounter() > 0) {
            for (int i = 0; i < rt.getCounter(); i++) {
                double x = rt.getValue("X", i);
                double y = rt.getValue("Y", i);
                double area = rt.getValue("Area", i);
                
                // Estimate bounding box from area (assuming roughly circular)
                int radius = (int) Math.sqrt(area / Math.PI);
                Rectangle bounds = new Rectangle(
                    (int)(x - radius), (int)(y - radius), 
                    radius * 2, radius * 2
                );
                
                colonies.add(bounds);
            }
        }
        
        return colonies;
    }
    
    /**
     * Adaptive threshold using median + k*MAD per well.
     * More robust than global thresholding for uneven illumination.
     */
    public static double calculateAdaptiveThreshold(ImageProcessor ip, Rectangle wellRegion) {
        return AnnulusBackground.estimateBackgroundMAD(ip, wellRegion);
    }
    
    /**
     * Convenience method with default parameters.
     */
    public static List<Rectangle> detectColonies(ImagePlus imp) {
        return detectColonies(imp, new ColonyParams());
    }
    
    /**
     * Detect colonies with custom size and circularity constraints.
     */
    public static List<Rectangle> detectColonies(ImagePlus imp, int minSize, int maxSize, 
                                               double minCircularity) {
        ColonyParams params = new ColonyParams();
        params.minSize = minSize;
        params.maxSize = maxSize;
        params.minCircularity = minCircularity;
        return detectColonies(imp, params);
    }
}