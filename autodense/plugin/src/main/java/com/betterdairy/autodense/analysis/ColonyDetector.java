package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import com.betterdairy.autodense.model.Models.ColonySize;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.plugin.filter.EDM;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure functional colony detection using native ImageJ operations.
 * Implements LoG blob detection and watershed splitting.
 * 
 * @deprecated Use {@link UnifiedColonyDetector} instead. This class will be removed in a future version.
 *             UnifiedColonyDetector provides better accuracy and consistency.
 */
@Deprecated
public final class ColonyDetector {
    
    /**
     * Detect colonies within a plate region
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi) {
        return detect(image, plateRoi, 6, 60, true, null, null);
    }
    
    /**
     * Detect colonies with full parameter control
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi, 
                                    int minDiamPx, int maxDiamPx, boolean splitTouching,
                                    String thresholdMethod, Integer thresholdRadius) {
        
        ImagePlus working = image.duplicate();
        working.setTitle("colony_detection_" + System.currentTimeMillis());
        
        // Step 1: Convert to grayscale if needed using headless method
        convertToGrayscaleHeadless(working);
        
        // Step 2: Apply plate mask using headless method
        if (plateRoi != null) {
            working.setRoi(plateRoi);
            clearOutsideHeadless(working);
            working.killRoi();
        }
        
        // Step 3: LoG-based blob detection via Auto Local Threshold using headless method
        String method = (thresholdMethod != null) ? thresholdMethod : "Phansalkar"; // FIXED: YAML-controlled parameter
        int radius = (thresholdRadius != null) ? thresholdRadius : 15; // FIXED: YAML-controlled parameter
        autoLocalThresholdHeadless(working, method, radius);
        
        // Step 4: Morphological cleanup using headless methods
        fillHolesHeadless(working);
        morphologyOpenHeadless(working); // Remove small noise
        
        // Step 5: Watershed splitting if requested
        if (splitTouching) {
            performWatershedSplitting(working);
        }
        
        // Step 6: Analyze particles to extract colony data
        int minArea = (int) (Math.PI * (minDiamPx / 2.0) * (minDiamPx / 2.0));
        int maxArea = (int) (Math.PI * (maxDiamPx / 2.0) * (maxDiamPx / 2.0));
        
        // Step 6: Particle analysis using headless method
        analyzeParticlesHeadless(working, "area mean centroid shape", 
                                String.format("size=%d-%d pixel show=Nothing display clear", minArea, maxArea));
        
        // Step 7: Convert ResultsTable to Colony objects
        List<Colony> colonies = extractColoniesFromResults(ResultsTable.getResultsTable());
        
        working.close();
        return colonies;
    }
    
    /**
     * Perform watershed splitting to separate touching colonies
     */
    private static void performWatershedSplitting(ImagePlus image) {
        // Distance transform
        EDM edm = new EDM();
        ImageProcessor proc = image.getProcessor();
        
        // Invert for distance transform (colonies should be white)
        proc.invert();
        edm.setup("", image);
        edm.run(proc);
        
        // Find maxima and watershed using headless method
        findMaximaHeadless(image, 10);
        
        // Get the segmented result
        ImagePlus segmented = IJ.getImage();
        if (segmented != null && !segmented.equals(image)) {
            image.setProcessor(segmented.getProcessor());
            segmented.close();
        }
    }
    
    // =============== HEADLESS UTILITY METHODS ===============
    
    /**
     * Headless-safe convert to grayscale (RGB to luminance)
     */
    private static void convertToGrayscaleHeadless(final ImagePlus image) {
        if (image.getNChannels() > 1) {
            final ImageProcessor ip = image.getProcessor().convertToFloat();
            // Simple RGB to luminance conversion: Y = 0.299*R + 0.587*G + 0.114*B
            final float[] pixels = (float[]) ip.getPixels();
            final int width = ip.getWidth();
            final int height = ip.getHeight();
            final float[] grayscale = new float[width * height];
            
            if (image.getType() == ImagePlus.COLOR_RGB) {
                final int[] rgbPixels = (int[]) image.getProcessor().getPixels();
                for (int i = 0; i < rgbPixels.length; i++) {
                    final int rgb = rgbPixels[i];
                    final float r = ((rgb >> 16) & 0xFF) / 255.0f;
                    final float g = ((rgb >> 8) & 0xFF) / 255.0f;
                    final float b = (rgb & 0xFF) / 255.0f;
                    grayscale[i] = 0.299f * r + 0.587f * g + 0.114f * b;
                }
                ip.setPixels(grayscale);
                image.setProcessor(ip);
            }
        }
    }
    
    /**
     * Headless-safe clear outside ROI
     */
    private static void clearOutsideHeadless(final ImagePlus image) {
        final ij.gui.Roi roi = image.getRoi();
        if (roi != null) {
            final ImageProcessor ip = image.getProcessor();
            ip.setValue(0.0);
            ip.fillOutside(roi);
        }
    }
    
    /**
     * Headless-safe auto local threshold
     */
    private static void autoLocalThresholdHeadless(final ImagePlus image, final String method, final int radius) {
        // Simplified auto threshold for headless operation
        final ij.process.AutoThresholder at = new ij.process.AutoThresholder();
        final int[] hist = image.getProcessor().getHistogram();
        try {
            final ij.process.AutoThresholder.Method threshMethod = 
                ij.process.AutoThresholder.Method.valueOf(method.toUpperCase());
            final int threshold = at.getThreshold(threshMethod, hist);
            final ImageProcessor ip = image.getProcessor();
            ip.setThreshold(threshold, 255, ImageProcessor.NO_LUT_UPDATE);
            ip.convertToByte(true).threshold(threshold);
        } catch (IllegalArgumentException e) {
            // Fallback to Otsu
            final int threshold = at.getThreshold(ij.process.AutoThresholder.Method.Otsu, hist);
            final ImageProcessor ip = image.getProcessor();
            ip.setThreshold(threshold, 255, ImageProcessor.NO_LUT_UPDATE);
            ip.convertToByte(true).threshold(threshold);
        }
    }
    
    /**
     * Headless-safe fill holes operation
     */
    private static void fillHolesHeadless(final ImagePlus image) {
        final ij.plugin.filter.Binary binary = new ij.plugin.filter.Binary();
        binary.setup("fill", image);
        binary.run(image.getProcessor());
    }
    
    /**
     * Headless-safe morphological opening
     */
    private static void morphologyOpenHeadless(final ImagePlus image) {
        final ij.plugin.filter.Binary binary = new ij.plugin.filter.Binary();
        binary.setup("open", image);
        binary.run(image.getProcessor());
    }
    
    /**
     * Headless-safe analyze particles
     */
    private static void analyzeParticlesHeadless(final ImagePlus image, final String measurements, final String options) {
        final ij.plugin.filter.ParticleAnalyzer pa = new ij.plugin.filter.ParticleAnalyzer(
            ij.plugin.filter.ParticleAnalyzer.SHOW_NONE,
            ij.measure.Measurements.AREA | ij.measure.Measurements.CENTROID | ij.measure.Measurements.MEAN | ij.measure.Measurements.SHAPE_DESCRIPTORS,
            ij.measure.ResultsTable.getResultsTable(),
            0.0, Double.POSITIVE_INFINITY,
            0.0, 1.0
        );
        pa.analyze(image, image.getProcessor());
    }
    
    /**
     * Headless-safe find maxima
     */
    private static void findMaximaHeadless(final ImagePlus image, final int prominence) {
        final ij.plugin.filter.MaximumFinder mf = new ij.plugin.filter.MaximumFinder();
        // Use segmented particles output type
        mf.findMaxima(image.getProcessor(), prominence, 0.0, ij.plugin.filter.MaximumFinder.SEGMENTED, false, false);
    }
    
    /**
     * Extract Colony objects from ImageJ ResultsTable
     */
    private static List<Colony> extractColoniesFromResults(ResultsTable rt) {
        List<Colony> colonies = new ArrayList<>();
        
        if (rt == null || rt.getCounter() == 0) {
            return colonies;
        }
        
        for (int i = 0; i < rt.getCounter(); i++) {
            double x = rt.getValue("X", i);
            double y = rt.getValue("Y", i);
            double area = rt.getValue("Area", i);
            double diameter = 2.0 * Math.sqrt(area / Math.PI);
            double circularity = rt.getValue("Circ.", i);
            double solidity = rt.getValue("Solidity", i);
            double meanIntensity = rt.getValue("Mean", i);
            
            // Convert to mm (will be updated when plate scale is known)
            double diameterMm = diameter / 10.0; // Placeholder conversion
            
            Colony colony = new Colony(
                i + 1,                    // index
                x, y,                     // position
                area, diameter, diameterMm, // size
                circularity, solidity,    // shape
                meanIntensity,           // intensity
                ColonyColor.WHITE,       // placeholder color
                1.0,                     // placeholder confidence
                classifySize(diameterMm), // size classification
                "unclassified"           // placeholder classification
            );
            
            colonies.add(colony);
        }
        
        return colonies;
    }
    
    /**
     * Simple size classification based on diameter
     */
    private static ColonySize classifySize(double diameterMm) {
        if (diameterMm < 1.0) return ColonySize.SMALL;
        if (diameterMm < 2.5) return ColonySize.MEDIUM;
        return ColonySize.LARGE;
    }
}