package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.IJ;
import ij.process.ImageProcessor;
import org.json.JSONArray;
import org.json.JSONObject;
import com.betterdairy.autodense.img.IJUtils;
import com.betterdairy.autodense.img.LaneWiseBackground;

/**
 * SINGLE SOURCE OF TRUTH for all image preprocessing operations.
 * Implements proven Fiji algorithms with standardized parameters.
 * All tool executors must delegate to these methods.
 */
public final class ImagePreprocessor {
    
    private ImagePreprocessor() {}
    
    /**
     * Apply a sequence of preprocessing steps to an image.
     * This is the main entry point for all preprocessing operations.
     * 
     * @param imp The input image
     * @param steps JSON array of preprocessing steps
     * @param destructive If true, modifies original image; if false, works on duplicate
     * @return The processed image (original or duplicate depending on destructive flag)
     */
    public static ImagePlus apply(ImagePlus imp, JSONArray steps, boolean destructive) {
        ImagePlus workingImg = destructive ? imp : imp.duplicate();
        
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String op = step.getString("op");
            
            switch (op) {
                case "rotate" -> {
                    double angle = step.getDouble("angle_deg");
                    angle = clamp(angle, -180, 180);
                    workingImg = rotate(workingImg, angle, true);
                }
                case "flip" -> {
                    String axis = step.getString("axis");
                    workingImg = flip(workingImg, axis, true);
                }
                case "crop" -> {
                    int x = step.getInt("x");
                    int y = step.getInt("y");
                    int width = step.getInt("width");
                    int height = step.getInt("height");
                    workingImg = crop(workingImg, x, y, width, height);
                }
                case "clahe" -> {
                    int blocksize = step.optInt("blocksize", 127);
                    int histogram = step.optInt("histogram", 256);
                    double maximum = step.optDouble("maximum", 3.0);
                    workingImg = clahe(workingImg, blocksize, histogram, maximum);
                }
                case "bandpass" -> {
                    double filterLarge = step.optDouble("filter_large", 40.0);
                    double filterSmall = step.optDouble("filter_small", 3.0);
                    workingImg = bandpassFilter(workingImg, filterLarge, filterSmall);
                }
                case "background" -> {
                    String method = step.optString("method", "rolling_ball");
                    int radius = step.optInt("radius_px", 50);
                    boolean sliding = step.optBoolean("sliding", false);
                    boolean smoothing = step.optBoolean("smoothing", false);
                    workingImg = subtractBackground(workingImg, method, radius, sliding, smoothing);
                }
                case "8-bit" -> {
                    IJUtils.silenceRoiManager(workingImg);
                    IJ.run(workingImg, "8-bit", "");
                }
                case "enhance_contrast" -> {
                    double saturated = step.optDouble("saturated", 0.3);
                    boolean normalize = step.optBoolean("normalize", true);
                    String params = "saturated=" + saturated;
                    if (normalize) params += " normalize";
                    IJ.run(workingImg, "Enhance Contrast...", params);
                }
                case "gaussian_blur" -> {
                    double sigma = step.optDouble("sigma", 1.0);
                    IJ.run(workingImg, "Gaussian Blur...", "sigma=" + sigma);
                }
                case "lane_wise_background" -> {
                    int radius = step.optInt("radius", 60);
                    double quantile = step.optDouble("quantile", 0.15);
                    LaneWiseBackground.subtract(workingImg, radius, quantile);
                }
                default -> throw new IllegalArgumentException("Unknown preprocessing operation: " + op);
            }
        }
        
        return workingImg;
    }
    
    /**
     * Apply preset preprocessing mode for common gel analysis workflows.
     * 
     * @param imp The input image
     * @param mode The preprocessing mode name
     * @param destructive If true, modifies original image; if false, works on duplicate  
     * @return The processed image
     */
    public static ImagePlus applyMode(ImagePlus imp, String mode, boolean destructive) {
        ImagePlus workingImg = destructive ? imp : imp.duplicate();
        
        switch (mode) {
            case "coomassie_default" -> {
                IJUtils.silenceRoiManager(workingImg);
                IJ.run(workingImg, "8-bit", "");
                // Do NOT invert for Coomassie if downstream expects dark-on-light=false
                IJ.run(workingImg, "Enhance Contrast...", "saturated=0.3 normalize");
                // Gentle denoise before baseline
                IJ.run(workingImg, "Gaussian Blur...", "sigma=1");
                // Lane-wise background subtraction
                LaneWiseBackground.subtract(workingImg, 60, 0.15);
                workingImg.updateAndDraw();
            }
            default -> throw new IllegalArgumentException("Unknown preprocessing mode: " + mode);
        }
        
        return workingImg;
    }
    
    // Internal utility for angle normalization (not parameter validation)
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Rotate image by specified angle (negative to deskew)
     * Used to make wells perfectly horizontal for accurate band analysis
     */
    public static ImagePlus rotate(ImagePlus imp, double angle, boolean inPlace) {
        ImagePlus result = inPlace ? imp : imp.duplicate();
        
        // Use optimized direct API for 90-degree rotations, IJ.run for arbitrary angles
        if (Math.abs(angle - 90) < 0.001) {
            ImageProcessor proc = result.getProcessor();
            proc.setInterpolationMethod(ImageProcessor.BILINEAR);
            result.setProcessor(proc.rotateLeft());
        } else if (Math.abs(angle + 90) < 0.001 || Math.abs(angle - 270) < 0.001) {
            ImageProcessor proc = result.getProcessor();
            proc.setInterpolationMethod(ImageProcessor.BILINEAR);
            result.setProcessor(proc.rotateRight());
        } else {
            // Use consistent interpolation method (Bilinear for quality)
            IJ.run(result, "Rotate...", "angle=" + angle + " interpolation=Bilinear");
        }
        return result;
    }
    
    // Legacy method for backward compatibility
    public static ImagePlus rotate(ImagePlus imp, double angle) {
        return rotate(imp, angle, false);
    }
    
    /**
     * Flip image horizontally or vertically
     * Used to standardize "wells at top" orientation
     */
    public static ImagePlus flip(ImagePlus imp, String axis, boolean inPlace) {
        ImagePlus result = inPlace ? imp : imp.duplicate();
        ImageProcessor proc = result.getProcessor();
        
        switch (axis.toLowerCase()) {
            case "horizontal" -> proc.flipHorizontal();
            case "vertical" -> proc.flipVertical();
            default -> throw new IllegalArgumentException("Invalid flip axis: " + axis);
        }
        return result;
    }
    
    // Legacy method for backward compatibility
    public static ImagePlus flip(ImagePlus imp, String axis) {
        return flip(imp, axis, false);
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
     * Bandpass filter with standard parameters (used by apply method)
     */
    public static ImagePlus bandpassFilter(ImagePlus imp, double filterLarge, double filterSmall) {
        ImagePlus result = imp.duplicate();
        String params = "filter_large=" + filterLarge + " filter_small=" + filterSmall + 
                       " suppress=None tolerance=5";
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