package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.IJ;
import ij.process.ImageProcessor;

/**
 * Comprehensive image preprocessing operations based on proven Fiji algorithms.
 * Implements the full stack of gel preparation techniques from University research.
 */
public final class ImagePreprocessor {
    
    private ImagePreprocessor() {}
    
    /**
     * Rotate image by specified angle (negative to deskew)
     * Used to make wells perfectly horizontal for accurate band analysis
     */
    public static ImagePlus rotate(ImagePlus imp, double angle) {
        ImagePlus result = imp.duplicate();
        IJ.run(result, "Rotate...", "angle=" + angle + " grid=1 interpolation=None");
        return result;
    }
    
    /**
     * Flip image horizontally or vertically
     * Used to standardize "wells at top" orientation
     */
    public static ImagePlus flip(ImagePlus imp, String axis) {
        ImagePlus result = imp.duplicate();
        switch (axis.toLowerCase()) {
            case "horizontal" -> IJ.run(result, "Flip Horizontally", "");
            case "vertical" -> IJ.run(result, "Flip Vertically", "");
            default -> throw new IllegalArgumentException("Invalid flip axis: " + axis);
        }
        return result;
    }
    
    /**
     * Crop image to specified region
     * Removes ruler/labels to improve background estimation
     */
    public static ImagePlus crop(ImagePlus imp, int x, int y, int width, int height) {
        ImagePlus result = imp.duplicate();
        result.setRoi(x, y, width, height);
        IJ.run(result, "Crop", "");
        return result;
    }
    
    /**
     * Straighten individual lane using polyline selection
     * De-smile curved lanes for accurate quantification
     */
    public static ImagePlus straightenLane(ImagePlus imp, int laneWidth) {
        // Note: This requires a polyline ROI to be set on the image
        // In practice, this would be called after lane detection creates the polyline
        ImagePlus result = imp.duplicate();
        IJ.run(result, "Straighten...", "line width=" + laneWidth);
        return result;
    }
    
    /**
     * Apply CLAHE (Contrast Limited Adaptive Histogram Equalization)
     * Local contrast boost without blowing highlights - great for faint DNA bands
     */
    public static ImagePlus clahe(ImagePlus imp, int blocksize, int histogram, double maximum) {
        ImagePlus result = imp.duplicate();
        String params = "blocksize=" + blocksize + " histogram=" + histogram + " maximum=" + maximum;
        IJ.run(result, "Enhance Local Contrast (CLAHE)", params);
        return result;
    }
    
    /**
     * Apply FFT Bandpass Filter
     * Suppress large-scale gradients and tiny speckles simultaneously
     */
    public static ImagePlus bandpass(ImagePlus imp, int low, int high, String suppress, int tolerance) {
        ImagePlus result = imp.duplicate();
        String params = "filter_large=" + high + " filter_small=" + low + 
                       " suppress=" + suppress + " tolerance=" + tolerance;
        IJ.run(result, "Bandpass Filter...", params);
        return result;
    }
    
    /**
     * Apply rolling ball or sliding paraboloid background subtraction
     * Removes uneven staining or illumination
     */
    public static ImagePlus subtractBackground(ImagePlus imp, String method, int radiusPx, 
                                              boolean sliding, boolean smoothing) {
        ImagePlus result = imp.duplicate();
        String params;
        
        if ("rolling_ball".equals(method)) {
            params = "rolling=" + radiusPx;
            if (sliding) params += " sliding";
            if (!smoothing) params += " disable";
            IJ.run(result, "Subtract Background...", params);
        } else if ("sliding_paraboloid".equals(method)) {
            params = "rolling=" + radiusPx + " sliding";
            if (!smoothing) params += " disable";
            IJ.run(result, "Subtract Background...", params);
        } else if ("median".equals(method)) {
            IJ.run(result, "Median...", "radius=" + radiusPx);
        } else if ("gaussian".equals(method)) {
            IJ.run(result, "Gaussian Blur...", "sigma=" + radiusPx);
        } else {
            throw new IllegalArgumentException("Invalid background method: " + method);
        }
        
        return result;
    }
    
    /**
     * Create binary mask using auto-threshold and morphological operations
     * Build gel area mask, exclude labels/lane markers from stats
     */
    public static ImagePlus createMask(ImagePlus imp, String thresholdMethod, String[] morphologyOps) {
        ImagePlus result = imp.duplicate();
        
        // Auto threshold
        IJ.run(result, "Auto Threshold", "method=" + thresholdMethod + " white");
        IJ.run(result, "Convert to Mask", "");
        
        // Apply morphological operations
        if (morphologyOps != null) {
            for (String op : morphologyOps) {
                switch (op.toLowerCase()) {
                    case "open" -> IJ.run(result, "Open", "");
                    case "close" -> IJ.run(result, "Close", "");
                    case "erode" -> IJ.run(result, "Erode", "");
                    case "dilate" -> IJ.run(result, "Dilate", "");
                    case "watershed" -> IJ.run(result, "Watershed", "");
                    default -> throw new IllegalArgumentException("Invalid morphology operation: " + op);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Apply noise reduction filters
     * Remove salt-and-pepper noise, stabilize peak finding
     */
    public static ImagePlus despeckle(ImagePlus imp) {
        ImagePlus result = imp.duplicate();
        IJ.run(result, "Despeckle", "");
        return result;
    }
    
    /**
     * Remove outlier pixels
     * Kill hot pixels without blurring lanes
     */
    public static ImagePlus removeOutliers(ImagePlus imp, int radius, int threshold, boolean bright) {
        ImagePlus result = imp.duplicate();
        String which = bright ? "Bright" : "Dark";
        String params = "radius=" + radius + " threshold=" + threshold + " which=" + which;
        IJ.run(result, "Remove Outliers...", params);
        return result;
    }
    
    /**
     * Enhance contrast with saturation normalization
     * Standardize grayscale ranges before thresholding
     */
    public static ImagePlus enhanceContrast(ImagePlus imp, double saturated, boolean normalize) {
        ImagePlus result = imp.duplicate();
        String params = "saturated=" + saturated;
        if (normalize) params += " normalize";
        IJ.run(result, "Enhance Contrast...", params);
        return result;
    }
    
    /**
     * Saturation Quality Control
     * Check for overexposed regions that would compromise quantification
     */
    public static SaturationResult checkSaturation(ImagePlus imp, double clipFraction) {
        ImageProcessor ip = imp.getProcessor();
        int[] histogram = ip.getHistogram();
        int totalPixels = ip.getPixelCount();
        
        // Count pixels at maximum value (255 for 8-bit, scaled for other types)
        int maxValue = histogram.length - 1;
        int clippedPixels = histogram[maxValue];
        double clippedFraction = (double) clippedPixels / totalPixels;
        
        boolean saturated = clippedFraction > clipFraction;
        
        return new SaturationResult(saturated, clippedFraction, clippedPixels, totalPixels);
    }
    
    /**
     * Result class for saturation analysis
     */
    public static class SaturationResult {
        public final boolean isSaturated;
        public final double clippedFraction;
        public final int clippedPixels;
        public final int totalPixels;
        
        public SaturationResult(boolean isSaturated, double clippedFraction, 
                               int clippedPixels, int totalPixels) {
            this.isSaturated = isSaturated;
            this.clippedFraction = clippedFraction;
            this.clippedPixels = clippedPixels;
            this.totalPixels = totalPixels;
        }
        
        @Override
        public String toString() {
            return String.format("Saturation: %s (%.4f%% clipped, %d/%d pixels)", 
                               isSaturated ? "DETECTED" : "OK", 
                               clippedFraction * 100, 
                               clippedPixels, 
                               totalPixels);
        }
    }
    
    /**
     * Set ImageJ measurement parameters for consistent quantification
     */
    public static void setMeasurements() {
        IJ.run("Set Measurements...", 
               "area mean min centroid integrated redirect=None decimal=3");
    }
    
    /**
     * Get recommended parameters for different gel types
     */
    public static class RecommendedParams {
        
        /**
         * SDS-PAGE (protein, Coomassie) parameters
         */
        public static class SDS {
            public static final int ROLLING_BALL_RADIUS = 120;
            public static final int MIN_PEAK_DISTANCE = 10;
            public static final double SATURATED_FRACTION = 0.35;
            public static final int BANDPASS_LOW = 2;
            public static final int BANDPASS_HIGH = 200;
        }
        
        /**
         * DNA gel (agarose, ethidium bromide) parameters  
         */
        public static class DNA {
            public static final int ROLLING_BALL_RADIUS = 200;
            public static final int MIN_PEAK_DISTANCE = 8;
            public static final int CLAHE_BLOCKSIZE = 127;
            public static final double CLAHE_MAX = 3.0;
            public static final int BANDPASS_LOW = 2;
            public static final int BANDPASS_HIGH = 200;
        }
    }
}