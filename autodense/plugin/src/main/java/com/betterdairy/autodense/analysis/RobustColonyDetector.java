package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust colony detection using ImageJ's ParticleAnalyzer for accurate sizing.
 * Integrates with ColonyAnalysisParams for comprehensive edge-case handling.
 */
public final class RobustColonyDetector {

    /**
     * Detect colonies using ImageJ ParticleAnalyzer with comprehensive filtering
     */
    public static List<MutableColony> detectColonies(ImagePlus imp, Roi analysisRoi, 
                                                    ColonyAnalysisParams.DetectionParams params) {
        // Prepare image for detection
        ImagePlus workingImage = imp.duplicate();
        ImageProcessor ip = workingImage.getProcessor();
        
        // Apply analysis ROI if provided
        if (analysisRoi != null) {
            ip.setRoi(analysisRoi);
            ip.setBackgroundValue(255); // White background outside analysis area
            ip.fillOutside(analysisRoi);
        }
        
        // Preprocessing for colony detection
        ip = preprocessForColonyDetection(ip, params);
        
        // Threshold detection - typically colonies are darker than background
        AutoThresholder.Method method = selectThresholdMethod(params);
        int[] hist = ip.getHistogram();
        int threshold = new ij.process.AutoThresholder().getThreshold(method, hist);
        ip.threshold(threshold);
        
        // Apply watershed if splitting touching colonies is enabled
        if (params.splitTouchingColonies) {
            applyWatershed(ip);
        }
        
        // Run ParticleAnalyzer with comprehensive measurements
        ResultsTable rt = new ResultsTable();
        int options = ParticleAnalyzer.SHOW_NONE | ParticleAnalyzer.CLEAR_WORKSHEET;
        if (params.ignoreRimArtifacts) {
            options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
        }
        
        int measurements = ij.measure.Measurements.AREA | 
                          ij.measure.Measurements.CENTROID |
                          ij.measure.Measurements.CIRCULARITY |
                          ij.measure.Measurements.FERET |
                          ij.measure.Measurements.SHAPE_DESCRIPTORS;
        
        // Convert size limits from mm to pixels (assuming calibration is set)
        double pxPerMM = 1.0 / imp.getCalibration().pixelWidth; // mm per pixel -> px per mm
        double minAreaPx = Math.PI * Math.pow(params.minDiameterMM * pxPerMM / 2.0, 2);
        double maxAreaPx = Math.PI * Math.pow(params.maxDiameterMM * pxPerMM / 2.0, 2);
        
        ParticleAnalyzer pa = new ParticleAnalyzer(
            options, measurements, rt,
            minAreaPx, maxAreaPx,
            params.minCircularity, 1.0
        );
        
        pa.analyze(new ImagePlus("colonies", ip));
        
        // Convert ResultsTable to MutableColony objects
        List<MutableColony> colonies = new ArrayList<>();
        for (int i = 0; i < rt.getCounter(); i++) {
            // Quality filtering
            double circularity = rt.getValue("Circ.", i);
            double solidity = rt.getValue("Solidity", i);
            
            if (circularity >= params.minCircularity && solidity >= params.minSolidity) {
                double x = rt.getValue("X", i);
                double y = rt.getValue("Y", i);
                double area = rt.getValue("Area", i);
                double eqDiameter = 2.0 * Math.sqrt(area / Math.PI);
                
                MutableColony colony = new MutableColony(i + 1, x, y, eqDiameter);
                colonies.add(colony);
            }
        }
        
        return colonies;
    }
    
    /**
     * Preprocess image for optimal colony detection
     */
    private static ImageProcessor preprocessForColonyDetection(ImageProcessor ip, 
                                                              ColonyAnalysisParams.DetectionParams params) {
        ImageProcessor processed = ip.duplicate();
        
        // Light gaussian blur to reduce noise while preserving colony boundaries
        processed.blurGaussian(1.0);
        
        // Optional: apply background subtraction for uneven illumination
        // This could be enhanced based on PreprocessingParams
        
        return processed;
    }
    
    /**
     * Select appropriate threshold method based on detection parameters
     */
    private static AutoThresholder.Method selectThresholdMethod(ColonyAnalysisParams.DetectionParams params) {
        if (params.splitTouchingColonies) {
            // For crowded plates, Triangle or Li methods work well
            return AutoThresholder.Method.Triangle;
        } else {
            // For standard detection, Otsu is reliable
            return AutoThresholder.Method.Otsu;
        }
    }
    
    /**
     * Apply watershed algorithm to split touching colonies
     */
    private static void applyWatershed(ImageProcessor ip) {
        // ImageJ's watershed implementation
        try {
            // Apply distance transform and watershed
            ip.invert(); // Watershed expects white objects
            ij.plugin.filter.EDM edm = new ij.plugin.filter.EDM();
            edm.setup("watershed", null);
            edm.run(ip);
            ip.invert(); // Convert back
        } catch (Exception e) {
            System.err.println("Watershed failed: " + e.getMessage());
            // Continue without watershed if it fails
        }
    }
    
    /**
     * Enhanced detection with multiple threshold methods for robustness
     */
    public static List<MutableColony> detectColoniesRobust(ImagePlus imp, Roi analysisRoi,
                                                          ColonyAnalysisParams.DetectionParams params) {
        // Try multiple threshold methods and combine results
        AutoThresholder.Method[] methods = {
            AutoThresholder.Method.Otsu,
            AutoThresholder.Method.Triangle,
            AutoThresholder.Method.Li
        };
        
        List<MutableColony> bestResult = null;
        int bestCount = 0;
        
        for (AutoThresholder.Method method : methods) {
            try {
                // Temporarily modify params to use specific method
                List<MutableColony> colonies = detectColoniesWithMethod(imp, analysisRoi, params, method);
                
                // Evaluate result quality (prefer reasonable colony counts)
                if (colonies.size() > bestCount && colonies.size() < 1000) { // Sanity check
                    bestResult = colonies;
                    bestCount = colonies.size();
                }
            } catch (Exception e) {
                // Continue with next method if one fails
                System.err.println("Detection failed with " + method + ": " + e.getMessage());
            }
        }
        
        return bestResult != null ? bestResult : new ArrayList<>();
    }
    
    /**
     * Helper method to detect colonies with specific threshold method
     */
    private static List<MutableColony> detectColoniesWithMethod(ImagePlus imp, Roi analysisRoi,
                                                               ColonyAnalysisParams.DetectionParams params,
                                                               AutoThresholder.Method method) {
        ImagePlus workingImage = imp.duplicate();
        ImageProcessor ip = workingImage.getProcessor();
        
        if (analysisRoi != null) {
            ip.setRoi(analysisRoi);
            ip.fillOutside(analysisRoi);
        }
        
        ip = preprocessForColonyDetection(ip, params);
        
        // Use specified threshold method
        int[] hist = ip.getHistogram();
        int threshold = new ij.process.AutoThresholder().getThreshold(method, hist);
        ip.threshold(threshold);
        
        if (params.splitTouchingColonies) {
            applyWatershed(ip);
        }
        
        // Continue with ParticleAnalyzer...
        return runParticleAnalysis(ip, imp.getCalibration().pixelWidth, params);
    }
    
    /**
     * Run ParticleAnalyzer and convert results to MutableColony objects
     */
    private static List<MutableColony> runParticleAnalysis(ImageProcessor ip, double pixelWidth,
                                                          ColonyAnalysisParams.DetectionParams params) {
        ResultsTable rt = new ResultsTable();
        
        double pxPerMM = 1.0 / pixelWidth;
        double minAreaPx = Math.PI * Math.pow(params.minDiameterMM * pxPerMM / 2.0, 2);
        double maxAreaPx = Math.PI * Math.pow(params.maxDiameterMM * pxPerMM / 2.0, 2);
        
        ParticleAnalyzer pa = new ParticleAnalyzer(
            ParticleAnalyzer.SHOW_NONE | ParticleAnalyzer.CLEAR_WORKSHEET,
            ij.measure.Measurements.AREA | ij.measure.Measurements.CENTROID |
            ij.measure.Measurements.CIRCULARITY | ij.measure.Measurements.SHAPE_DESCRIPTORS,
            rt, minAreaPx, maxAreaPx, params.minCircularity, 1.0
        );
        
        pa.analyze(new ImagePlus("temp", ip));
        
        List<MutableColony> colonies = new ArrayList<>();
        for (int i = 0; i < rt.getCounter(); i++) {
            double circularity = rt.getValue("Circ.", i);
            double solidity = rt.getValue("Solidity", i);
            
            if (circularity >= params.minCircularity && solidity >= params.minSolidity) {
                double x = rt.getValue("X", i);
                double y = rt.getValue("Y", i);
                double area = rt.getValue("Area", i);
                double eqDiameter = 2.0 * Math.sqrt(area / Math.PI);
                
                colonies.add(new MutableColony(i + 1, x, y, eqDiameter));
            }
        }
        
        return colonies;
    }
    
    /**
     * Validate detected colonies against expected parameters
     */
    public static DetectionQuality assessDetectionQuality(List<MutableColony> colonies, 
                                                         ColonyAnalysisParams.DetectionParams params) {
        if (colonies.isEmpty()) {
            return new DetectionQuality(0, "No colonies detected", false);
        }
        
        // Check size distribution
        double[] diameters = colonies.stream().mapToDouble(c -> c.eqDiamPx).toArray();
        java.util.Arrays.sort(diameters);
        double median = diameters[diameters.length / 2];
        double q1 = diameters[diameters.length / 4];
        double q3 = diameters[3 * diameters.length / 4];
        
        boolean goodDistribution = (q3 / q1) < 5.0; // Size range not too extreme
        boolean reasonableCount = colonies.size() >= 5 && colonies.size() <= 500;
        boolean goodSizes = median > 5.0 && median < 100.0; // Reasonable pixel sizes
        
        double score = 0.0;
        if (goodDistribution) score += 0.4;
        if (reasonableCount) score += 0.4;
        if (goodSizes) score += 0.2;
        
        String report = String.format(
            "Detected %d colonies. Median size: %.1f px. Size range Q1-Q3: %.1f-%.1f px",
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
}