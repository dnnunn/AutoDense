package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import com.betterdairy.autodense.model.Models.ColonySize;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.plugin.filter.Binary;
import ij.plugin.filter.Convolver;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified colony detection system consolidating multiple detection approaches.
 * Primary implementation based on PetriColonyMask with enhancements from other detectors.
 * 
 * Design decisions:
 * - Uses CIELAB blue index threshold for robustness (from PetriColonyMask)
 * - Returns Colony models for consistency (from ColonyDetector)
 * - Includes morphological operations for noise reduction
 * - Supports both pixel and millimeter units
 */
public final class UnifiedColonyDetector {
    
    private UnifiedColonyDetector() {} // Utility class
    
    /**
     * Comprehensive colony detection parameters
     */
    public static class DetectionParams {
        // Size constraints (in pixels - converted from mm at call site)
        public int minDiameterPx = 8;           // Minimum colony diameter in pixels
        public int maxDiameterPx = 60;          // Maximum colony diameter in pixels
        
        // Shape constraints
        public double minCircularity = 0.3;     // Minimum circularity (0.0-1.0)
        public double minSolidity = 0.5;        // Minimum solidity (area/convex_hull_area)
        
        // Color thresholding (CIELAB b* for blue detection)
        public double blueThreshold = -6.0;     // b* threshold for colony detection
        public boolean useAdaptiveThreshold = true;  // Use local adaptation
        
        // Processing options
        public boolean splitTouchingColonies = true;  // Apply watershed separation
        public boolean removeRimArtifacts = true;     // Exclude particles touching border
        
        // Output options
        public boolean includeMorphology = true;      // Calculate morphological features
        
        /**
         * Create parameters optimized for given pixel resolution
         */
        public static DetectionParams forResolution(double pixelsPerMM) {
            DetectionParams params = new DetectionParams();
            
            // Scale size constraints based on typical colony sizes (0.2-3.0mm diameter)
            params.minDiameterPx = (int) (0.2 * pixelsPerMM);
            params.maxDiameterPx = (int) (3.0 * pixelsPerMM);
            
            // Adjust morphological parameters for resolution
            if (pixelsPerMM > 30) {
                // High resolution - stricter requirements
                params.minCircularity = 0.4;
                params.minSolidity = 0.6;
            } else if (pixelsPerMM < 15) {
                // Low resolution - relaxed requirements
                params.minCircularity = 0.2;
                params.minSolidity = 0.4;
            }
            
            return params;
        }
    }
    
    /**
     * Detect colonies with default parameters
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi) {
        return detect(image, plateRoi, new DetectionParams(), 10.0); // Default 10 px/mm estimate
    }
    
    /**
     * Detect colonies with full parameter control and unit conversion
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi, 
                                    int minDiamPx, int maxDiamPx, boolean splitTouching) {
        DetectionParams params = new DetectionParams();
        params.minDiameterPx = minDiamPx;
        params.maxDiameterPx = maxDiamPx;
        params.splitTouchingColonies = splitTouching;
        
        return detect(image, plateRoi, params, 10.0); // Default resolution estimate
    }
    
    /**
     * Primary detection method with comprehensive parameter support
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi, 
                                    DetectionParams params, double pixelsPerMM) {
        
        // Step 1: Prepare working image
        ImagePlus working = image.duplicate();
        working.setTitle("unified_colony_detection_" + System.currentTimeMillis());
        
        try {
            // Step 2: Convert to grayscale if needed
            if (working.getNChannels() > 1) {
                working = convertToGrayscaleHeadless(working);
            }
            
            // Step 3: Apply plate mask if provided
            if (plateRoi != null) {
                working.setRoi(plateRoi);
                clearOutsideHeadless(working);
                working.killRoi();
            }
            
            // Step 4: Create detection mask using blue index threshold
            ImagePlus maskImage = createDetectionMask(image, plateRoi, params);
            
            // Step 5: Morphological processing
            applyMorphologicalFiltering(maskImage, params);
            
            // Step 6: Watershed splitting if enabled
            if (params.splitTouchingColonies) {
                applyWatershedSeparation(maskImage);
            }
            
            // Step 7: Particle analysis with comprehensive filtering
            List<Colony> colonies = analyzeParticlesAsColonies(maskImage, params, pixelsPerMM);
            
            return colonies;
            
        } finally {
            working.close();
        }
    }
    
    /**
     * Create binary detection mask using CIELAB blue index threshold
     * Enhanced version of PetriColonyMask.createBlueIndexMask()
     */
    private static ImagePlus createDetectionMask(ImagePlus image, OvalRoi plateRoi, DetectionParams params) {
        ImageProcessor ip = image.getProcessor();
        ImageProcessor mask = ip.createProcessor(ip.getWidth(), ip.getHeight());
        
        // Convert to color if needed
        if (image.getType() != ImagePlus.COLOR_RGB) {
            ip = ip.convertToColorProcessor();
        }
        
        // Apply blue index threshold with optional adaptive enhancement
        double threshold = params.blueThreshold;
        
        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x < ip.getWidth(); x++) {
                // Skip pixels outside plate ROI if provided
                if (plateRoi != null && !plateRoi.contains(x, y)) {
                    mask.putPixel(x, y, 0); // Background
                    continue;
                }
                
                int rgb = ip.getPixel(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                double bStar = BlueIndex.bStar(r, g, b);
                
                // Apply adaptive threshold if enabled
                if (params.useAdaptiveThreshold && plateRoi != null) {
                    Rectangle localRegion = new Rectangle(
                        Math.max(0, x - 50), Math.max(0, y - 50),
                        Math.min(100, ip.getWidth() - x), Math.min(100, ip.getHeight() - y)
                    );
                    double localThreshold = PetriColonyMask.calculateAdaptiveThreshold(ip, localRegion);
                    threshold = Math.min(params.blueThreshold, localThreshold);
                }
                
                // Set mask pixel: white for potential colonies, black for background
                int maskValue = (bStar < threshold) ? 255 : 0;
                mask.putPixel(x, y, maskValue);
            }
        }
        
        return new ImagePlus(image.getTitle() + "_detection_mask", mask);
    }
    
    /**
     * Apply morphological filtering to clean up detection mask
     * Fixed: Ensure true binary (0/255) values for IJ1 morphological operations
     */
    private static void applyMorphologicalFiltering(ImagePlus maskImage, DetectionParams params) {
        // Ensure mask is 8-bit grayscale for morphological operations
        if (maskImage.getType() != ImagePlus.GRAY8) {
            convertTo8BitHeadless(maskImage);
        }
        
        // CRITICAL FIX: Ensure true binary values (0/255) for IJ1 operations
        ImageProcessor ip = maskImage.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        
        // Create true binary processor with exactly {0, 255} values
        final ij.process.ByteProcessor binaryProcessor = new ij.process.ByteProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Any non-zero value becomes 255, zero stays 0
                int value = ip.getPixel(x, y);
                binaryProcessor.set(x, y, value > 0 ? 255 : 0);
            }
        }
        
        // Replace with true binary processor
        maskImage.setProcessor(binaryProcessor);
        
        // Now safe to apply headless binary operations
        // Opening to remove small noise
        morphologyOpenHeadless(maskImage, 1);
        
        // Fill holes in colonies
        fillHolesHeadless(maskImage);
        
        // Light closing to connect nearby fragments
        morphologyCloseHeadless(maskImage, 1);
    }
    
    /**
     * Apply watershed separation for touching colonies
     * Enhanced version from RobustColonyDetector
     */
    private static void applyWatershedSeparation(ImagePlus maskImage) {
        try {
            // Distance transform and watershed
            ImageProcessor ip = maskImage.getProcessor();
            ip.invert(); // Watershed expects white objects on black background
            
            ij.plugin.filter.EDM edm = new ij.plugin.filter.EDM();
            edm.setup("watershed", null);
            edm.run(ip);
            
            ip.invert(); // Convert back to standard format
        } catch (Exception e) {
            System.err.println("Watershed separation failed: " + e.getMessage());
            // Continue without watershed if it fails
        }
    }
    
    /**
     * Analyze particles and convert to Colony objects with comprehensive measurements
     */
    private static List<Colony> analyzeParticlesAsColonies(ImagePlus maskImage, 
                                                         DetectionParams params, double pixelsPerMM) {
        List<Colony> colonies = new ArrayList<>();
        
        // Configure comprehensive measurements
        int measurements = ij.measure.Measurements.AREA | 
                          ij.measure.Measurements.CENTROID |
                          ij.measure.Measurements.CIRCULARITY |
                          ij.measure.Measurements.FERET |
                          ij.measure.Measurements.SHAPE_DESCRIPTORS |
                          ij.measure.Measurements.MEAN;
        
        // Configure particle analysis options
        int options = ij.plugin.filter.ParticleAnalyzer.SHOW_NONE | 
                     ij.plugin.filter.ParticleAnalyzer.CLEAR_WORKSHEET;
        
        if (params.removeRimArtifacts) {
            options |= ij.plugin.filter.ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
        }
        
        // Convert diameter constraints to area
        double minAreaPx = Math.PI * Math.pow(params.minDiameterPx / 2.0, 2);
        double maxAreaPx = Math.PI * Math.pow(params.maxDiameterPx / 2.0, 2);
        
        // Run particle analysis
        ResultsTable rt = new ResultsTable();
        ij.plugin.filter.ParticleAnalyzer pa = new ij.plugin.filter.ParticleAnalyzer(
            options, measurements, rt,
            minAreaPx, maxAreaPx,
            params.minCircularity, 1.0
        );
        
        pa.analyze(maskImage);
        
        // Convert results to Colony objects
        for (int i = 0; i < rt.getCounter(); i++) {
            // Extract basic measurements
            double x = rt.getValue("X", i);
            double y = rt.getValue("Y", i);
            double area = rt.getValue("Area", i);
            double circularity = rt.getValue("Circ.", i);
            double solidity = rt.getValue("Solidity", i);
            double meanIntensity = rt.getValue("Mean", i);
            
            // Quality filtering
            if (circularity >= params.minCircularity && solidity >= params.minSolidity) {
                // Calculate derived measurements
                double diameterPx = 2.0 * Math.sqrt(area / Math.PI);
                double diameterMM = diameterPx / pixelsPerMM;
                
                // Determine size class
                ColonySize sizeClass = determineSizeClass(diameterMM);
                
                // Create Colony object
                Colony colony = new Colony(
                    i + 1,                          // index
                    x, y,                           // centroid coordinates
                    area,                           // area in pixels
                    diameterPx,                     // diameter in pixels
                    diameterMM,                     // diameter in mm
                    circularity,                    // circularity
                    solidity,                       // solidity
                    meanIntensity,                  // mean intensity
                    ColonyColor.OTHER,              // color class (to be determined by classifier)
                    0.0,                           // color confidence (to be determined)
                    sizeClass,                      // size class
                    "unclassified"                  // bin category (to be determined)
                );
                
                colonies.add(colony);
            }
        }
        
        return colonies;
    }
    
    /**
     * Determine colony size class based on diameter in millimeters
     */
    private static ColonySize determineSizeClass(double diameterMM) {
        if (diameterMM < 0.5) {
            return ColonySize.SMALL;
        } else if (diameterMM < 1.5) {
            return ColonySize.MEDIUM;
        } else {
            return ColonySize.LARGE;
        }
    }
    
    /**
     * Get detection quality assessment for validation
     */
    public static DetectionQuality assessQuality(List<Colony> colonies, DetectionParams params) {
        if (colonies.isEmpty()) {
            return new DetectionQuality(0.0, "No colonies detected", false);
        }
        
        // Assess size distribution
        double[] diameters = colonies.stream().mapToDouble(c -> c.diameter()).toArray();
        java.util.Arrays.sort(diameters);
        
        double median = diameters[diameters.length / 2];
        double q1 = diameters[diameters.length / 4];
        double q3 = diameters[3 * diameters.length / 4];
        
        // Quality metrics
        boolean goodDistribution = (q3 / q1) < 5.0; // Size range not too extreme
        boolean reasonableCount = colonies.size() >= 3 && colonies.size() <= 1000;
        boolean goodSizes = median > 3.0 && median < 200.0; // Reasonable pixel sizes
        
        double score = 0.0;
        if (goodDistribution) score += 0.4;
        if (reasonableCount) score += 0.4;
        if (goodSizes) score += 0.2;
        
        String report = String.format(
            "Detected %d colonies. Median diameter: %.1f px. Size range Q1-Q3: %.1f-%.1f px",
            colonies.size(), median, q1, q3
        );
        
        return new DetectionQuality(score, report, score > 0.6);
    }
    
    /**
     * Detection quality assessment result
     */
    public static record DetectionQuality(
        double score,           // 0-1 quality score
        String report,          // Human-readable assessment
        boolean acceptable      // Whether detection is usable
    ) {}
    
    // ================== HEADLESS-SAFE IMPLEMENTATIONS ==================
    
    private static ImagePlus convertToGrayscaleHeadless(ImagePlus imp) {
        ImagePlus result = imp.duplicate();
        if (result.getProcessor() instanceof ColorProcessor) {
            ImageProcessor ip = result.getProcessor().convertToByte(true);
            result.setProcessor(ip);
        }
        return result;
    }
    
    private static void clearOutsideHeadless(ImagePlus imp) {
        if (imp.getRoi() != null) {
            ImageProcessor ip = imp.getProcessor();
            ip.setMask(imp.getRoi().getMask());
            ip.fill();
            ip.setMask(null);
        }
    }
    
    private static void convertTo8BitHeadless(ImagePlus imp) {
        ImageProcessor ip = imp.getProcessor().convertToByte(true);
        imp.setProcessor(ip);
    }
    
    private static void morphologyOpenHeadless(ImagePlus imp, int iterations) {
        ImageProcessor ip = imp.getProcessor();
        for (int i = 0; i < iterations; i++) {
            ip.erode();
        }
        for (int i = 0; i < iterations; i++) {
            ip.dilate();
        }
    }
    
    private static void fillHolesHeadless(ImagePlus imp) {
        ImageProcessor ip = imp.getProcessor();
        // Simple hole filling using flood fill from edges
        ij.plugin.filter.Binary binary = new ij.plugin.filter.Binary();
        binary.setup("fill", imp);
        binary.run(ip);
    }
    
    private static void morphologyCloseHeadless(ImagePlus imp, int iterations) {
        ImageProcessor ip = imp.getProcessor();
        for (int i = 0; i < iterations; i++) {
            ip.dilate();
        }
        for (int i = 0; i < iterations; i++) {
            ip.erode();
        }
    }
}