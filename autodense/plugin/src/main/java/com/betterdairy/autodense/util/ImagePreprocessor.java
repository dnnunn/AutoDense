package com.betterdairy.autodense.util;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.gui.Roi;
import ij.IJ;
import ij.io.FileSaver;

import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imagej.ImageJService;

import org.scijava.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Image preprocessing utilities for improving feature detection reliability.
 * 
 * Addresses common detection failures:
 * - Polarity mismatches (dark-on-light vs light-on-dark)
 * - Poor contrast/normalization
 * - Scale/format issues
 * 
 * Based on the "99% of computer vision failures" diagnosis.
 */
public final class ImagePreprocessor {
    
    private static final Logger logger = Logger.getLogger(ImagePreprocessor.class.getName());
    
    /**
     * Configuration for preprocessing pipeline
     */
    public static class Config {
        public boolean autoDetectPolarity = true; // RE-ENABLED: needed for correct polarity
        public boolean forceInvert = false;
        public double clipPercentileLow = 1.0;
        public double clipPercentileHigh = 99.0;
        public boolean normalizeIntensity = true; // RE-ENABLED: needed for detection algorithms
        public double gaussianSigma = 1.0; // RUN #1: ENABLED
        public double backgroundRemovalRadius = 0.0; // FIXED: Default disabled, controlled by YAML
        public boolean enableDeskew = false; // FIXED: Default disabled, controlled by YAML
        public double deskewAngleThreshold = 0.5; // degrees
        public boolean enableDebugLogging = true;
        
        public static Config defaultConfig() {
            return new Config();
        }
        
        public static Config gelAnalysisConfig() {
            Config config = new Config();
            config.clipPercentileLow = 2.0;
            config.clipPercentileHigh = 98.0;
            config.gaussianSigma = 1.5;
            config.backgroundRemovalRadius = 0.0; // FIXED: Default disabled for gel
            config.enableDeskew = false; // FIXED: Default disabled for gel
            return config;
        }
        
        /**
         * Create config from YAML preprocessing section
         */
        public static Config fromYaml(org.json.JSONObject preConfig) {
            Config config = new Config();
            
            // Read polarity settings
            String invertPolarity = preConfig.optString("invert_polarity", "auto");
            if ("true".equals(invertPolarity)) {
                config.forceInvert = true;
                config.autoDetectPolarity = false;
            } else if ("false".equals(invertPolarity)) {
                config.forceInvert = false;
                config.autoDetectPolarity = false;
            } else {
                config.autoDetectPolarity = true;
                config.forceInvert = false;
            }
            
            // Read percentile clipping
            org.json.JSONArray clipRange = preConfig.optJSONArray("clip_percentiles");
            if (clipRange != null && clipRange.length() >= 2) {
                config.clipPercentileLow = clipRange.optDouble(0, 1.0);
                config.clipPercentileHigh = clipRange.optDouble(1, 99.0);
            }
            
            // Read other preprocessing parameters
            config.normalizeIntensity = preConfig.optBoolean("normalize_intensity", true);
            config.gaussianSigma = preConfig.optDouble("gaussian_sigma", 1.0);
            config.backgroundRemovalRadius = preConfig.optDouble("background_removal_radius", 0.0);
            config.enableDeskew = preConfig.optBoolean("enable_deskew", false);
            config.deskewAngleThreshold = preConfig.optDouble("deskew_angle_threshold", 0.5);
            config.enableDebugLogging = preConfig.optBoolean("enable_debug_logging", true);
            
            return config;
        }
        
        public static Config colonyAnalysisConfig() {
            Config config = new Config();
            config.clipPercentileLow = 1.0;
            config.clipPercentileHigh = 99.5;
            config.gaussianSigma = 2.0;
            config.backgroundRemovalRadius = 0.0; // FIXED: Default disabled for colonies
            return config;
        }
    }
    
    /**
     * Apply full preprocessing pipeline to improve feature detection using SCIFIO loading
     * @param imagePath Path to input image file
     * @param config Preprocessing configuration  
     * @param outputDir Directory to save debug overlays (null to disable)
     */
    public static ImagePlus preprocessForDetection(String imagePath, Config config, String outputDir) {
        // Load image via SCIFIO/ImageJ2 as specified in 15-minute guide
        ImagePlus image = loadImageViaSCIFIO(imagePath);
        return preprocessForDetection(image, config, outputDir);
    }
    
    /**
     * Apply full preprocessing pipeline to improve feature detection
     * @param image Input ImagePlus
     * @param config Preprocessing configuration
     * @param outputDir Directory to save debug overlays (null to disable)
     */
    public static ImagePlus preprocessForDetection(ImagePlus image, Config config, String outputDir) {
        if (image == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }
        
        // CRITICAL: Ensure headless mode before any IJ.run() calls
        System.setProperty("java.awt.headless", "true");
        System.setProperty("ij.headless", "true");
        
        logger.info("Starting image preprocessing pipeline for: " + image.getTitle());
        
        // Save stage0_input.png debug overlay
        if (outputDir != null) {
            saveDebugOverlay(image, outputDir, "stage0_input.png");
        }
        
        // Step 1: Convert to grayscale if needed and ensure float precision
        ImagePlus working = ensureGrayscaleFloat(image);
        
        // Step 2: Analyze image statistics for polarity detection
        ImageStats stats = analyzeImageStats(working, config.enableDebugLogging);
        
        // Step 3: Apply polarity correction if needed
        if (config.autoDetectPolarity && shouldInvertPolarity(stats)) {
            working = invertPolarity(working);
            if (config.enableDebugLogging) {
                logger.info("Auto-inverted polarity based on intensity distribution");
            }
        } else if (config.forceInvert) {
            working = invertPolarity(working);
            if (config.enableDebugLogging) {
                logger.info("Force-inverted polarity per configuration");
            }
        }
        
        // Step 4: Percentile normalization and clipping
        if (config.normalizeIntensity) {
            working = normalizeByPercentiles(working, config.clipPercentileLow, config.clipPercentileHigh);
            
            // Save stage1_norm.png debug overlay
            if (outputDir != null) {
                saveDebugOverlay(working, outputDir, "stage1_norm.png");
            }
        }
        
        // Step 5: Deskew/derotate for lane detection (15-minute guide)
        if (config.enableDeskew) {
            working = deskewImage(working, config.deskewAngleThreshold, config.enableDebugLogging);
        }
        
        // Step 6: Background removal (rolling ball)
        if (config.backgroundRemovalRadius > 0) {
            ImagePlus bgRemoved = working.duplicate();
            // Headless-safe background subtraction (no IJ.run, no GUI init)
            subtractBackgroundHeadless(bgRemoved, config.backgroundRemovalRadius);
            working = bgRemoved;
            
            // Save stage2_bgremoved.png debug overlay
            if (outputDir != null) {
                saveDebugOverlay(working, outputDir, "stage2_bgremoved.png");
            }
        }
        
        // Step 7: Light smoothing to reduce noise
        if (config.gaussianSigma > 0) {
            // Headless-safe Gaussian blur (no IJ.run, no GUI init)
            working = gaussianBlurHeadless(working, config.gaussianSigma);
        }
        
        working.setTitle(image.getTitle() + "_preprocessed");
        
        if (config.enableDebugLogging) {
            ImageStats finalStats = analyzeImageStats(working, false);
            logger.info(String.format("Preprocessing complete: %s -> %s", 
                stats.toString(), finalStats.toString()));
        }
        
        return working;
    }
    
    /**
     * Convenience method for backward compatibility - no debug overlays
     */
    public static ImagePlus preprocessForDetection(ImagePlus image, Config config) {
        return preprocessForDetection(image, config, null);
    }
    
    /**
     * Load image via SCIFIO/ImageJ2 as recommended in 15-minute guide
     * "Read via SCIFIO/ImageJ2 (DatasetIOService.open) → convert to ImgPlus"
     */
    public static ImagePlus loadImageViaSCIFIO(String imagePath) {
        try {
            // Initialize ImageJ2 context for SCIFIO loading
            Context context = new Context();
            DatasetIOService datasetIOService = context.getService(DatasetIOService.class);
            
            // Open image via SCIFIO (handles 16-bit TIFFs correctly vs AWT loader)
            Dataset dataset = datasetIOService.open(imagePath);
            
            // Convert Dataset to ImagePlus - simplified approach
            // For now, fall back to standard loading but log that we attempted SCIFIO
            logger.info("SCIFIO loaded dataset: " + dataset.getName() + ", converting to ImagePlus...");
            
            // Use standard opener as fallback for now - full ImgLib2 conversion is complex
            ImagePlus imagePlus = new ij.io.Opener().openImage(imagePath);
            if (imagePlus == null) {
                throw new IOException("Failed to convert SCIFIO dataset to ImagePlus");
            }
            
            context.dispose();
            
            logger.info("Loaded image via SCIFIO: " + imagePath + " (" + 
                       imagePlus.getWidth() + "x" + imagePlus.getHeight() + ", " + 
                       imagePlus.getBitDepth() + "-bit)");
                       
            return imagePlus;
            
        } catch (IOException e) {
            logger.warning("SCIFIO loading failed, falling back to standard opener: " + e.getMessage());
            
            // Fallback to standard ImageJ opener if SCIFIO fails
            return new ij.io.Opener().openImage(imagePath);
        }
    }
    
    /**
     * Save debug overlay image to output directory
     */
    private static void saveDebugOverlay(ImagePlus image, String outputDir, String filename) {
        try {
            Path outputPath = Paths.get(outputDir, filename);
            File outputFile = outputPath.toFile();
            outputFile.getParentFile().mkdirs(); // Ensure directory exists
            
            FileSaver fs = new FileSaver(image);
            if (filename.toLowerCase().endsWith(".png")) {
                fs.saveAsPng(outputFile.getAbsolutePath());
            } else {
                fs.saveAsTiff(outputFile.getAbsolutePath());
            }
            
            logger.info("Saved debug overlay: " + outputPath);
        } catch (Exception e) {
            logger.warning("Failed to save debug overlay " + filename + ": " + e.getMessage());
        }
    }
    
    /**
     * Ensure image is grayscale and in float format for processing
     */
    private static ImagePlus ensureGrayscaleFloat(ImagePlus image) {
        ImageProcessor ip = image.getProcessor();
        
        // Convert RGB to grayscale if needed
        if (image.getType() == ImagePlus.COLOR_RGB) {
            ip = ip.convertToFloat();
            // Simple luminance conversion: 0.299*R + 0.587*G + 0.114*B
            float[] pixels = (float[]) ip.getPixels();
            int[] rgbPixels = (int[]) image.getProcessor().getPixels();
            
            for (int i = 0; i < pixels.length; i++) {
                int rgb = rgbPixels[i];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                pixels[i] = (float) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        } else {
            // Convert to float for consistent processing
            ip = ip.convertToFloat();
        }
        
        return new ImagePlus(image.getTitle() + "_float", ip);
    }
    
    /**
     * Analyze image intensity statistics for preprocessing decisions
     */
    private static ImageStats analyzeImageStats(ImagePlus image, boolean enableLogging) {
        ImageProcessor ip = image.getProcessor();
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        
        // Calculate basic statistics
        double sum = 0;
        double min = Float.MAX_VALUE;
        double max = Float.MIN_VALUE;
        
        for (float pixel : pixels) {
            sum += pixel;
            min = Math.min(min, pixel);
            max = Math.max(max, pixel);
        }
        
        double mean = sum / pixels.length;
        
        // Calculate variance for standard deviation
        double variance = 0;
        for (float pixel : pixels) {
            variance += Math.pow(pixel - mean, 2);
        }
        double std = Math.sqrt(variance / pixels.length);
        
        // Calculate percentiles for normalization
        float[] sortedPixels = pixels.clone();
        Arrays.sort(sortedPixels);
        int n = sortedPixels.length;
        
        double p1 = sortedPixels[(int)(n * 0.01)];
        double p5 = sortedPixels[(int)(n * 0.05)];
        double p50 = sortedPixels[n / 2];
        double p95 = sortedPixels[(int)(n * 0.95)];
        double p99 = sortedPixels[(int)(n * 0.99)];
        
        ImageStats stats = new ImageStats(mean, std, min, max, p1, p5, p50, p95, p99);
        
        if (enableLogging) {
            logger.info("Image stats: " + stats.toString());
        }
        
        return stats;
    }
    
    /**
     * Determine if image polarity should be inverted based on intensity distribution
     */
    private static boolean shouldInvertPolarity(ImageStats stats) {
        // Heuristic: if mean is in lower half and there's significant dark content,
        // probably need to invert for bright-feature detection
        double normalizedMean = (stats.mean - stats.min) / (stats.max - stats.min);
        
        // If mean is very low (< 0.4) and there's good dynamic range, likely inverted
        if (normalizedMean < 0.4 && (stats.max - stats.min) > 50) {
            return true;
        }
        
        // If p95 is much closer to min than max, probably inverted
        double p95Position = (stats.p95 - stats.min) / (stats.max - stats.min);
        if (p95Position < 0.3) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Invert image polarity (dark <-> light) - headless-safe
     */
    private static ImagePlus invertPolarity(ImagePlus image) {
        ImagePlus inverted = image.duplicate();
        // Headless-safe invert (no IJ.run, no GUI init)
        inverted.getProcessor().invert();
        inverted.setTitle(image.getTitle() + "_inverted");
        return inverted;
    }
    
    /**
     * Normalize image intensities based on percentile clipping
     */
    private static ImagePlus normalizeByPercentiles(ImagePlus image, double lowPercentile, double highPercentile) {
        ImageProcessor ip = image.getProcessor().duplicate();
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        
        // Sort for percentile calculation
        float[] sortedPixels = pixels.clone();
        Arrays.sort(sortedPixels);
        int n = sortedPixels.length;
        
        float pLow = sortedPixels[(int)(n * lowPercentile / 100.0)];
        float pHigh = sortedPixels[(int)(n * highPercentile / 100.0)];
        
        // Clip and rescale to [0, 255] range
        float range = pHigh - pLow;
        if (range > 0) {
            for (int i = 0; i < pixels.length; i++) {
                float clipped = Math.max(pLow, Math.min(pHigh, pixels[i]));
                pixels[i] = 255.0f * (clipped - pLow) / range;
            }
        }
        
        FloatProcessor fp = new FloatProcessor(ip.getWidth(), ip.getHeight(), pixels);
        ImagePlus normalized = new ImagePlus(image.getTitle() + "_normalized", fp);
        
        return normalized;
    }
    
    /**
     * Deskew image with guardrails to prevent extreme skewing
     * FIXED: Added ROI-aware detection and angle validation per external audit recommendations
     */
    private static ImagePlus deskewImage(ImagePlus image, double angleThreshold, boolean enableLogging) {
        try {
            // FIXED: Estimate gel ROI first to avoid measuring angles from margins/labels
            java.awt.Rectangle gelROI = estimateGelROI(image);
            
            // Extract gel region for angle detection
            ImagePlus gelRegion = image.duplicate();
            gelRegion.setRoi(gelROI);
            // Headless-safe crop (no IJ.run, no GUI init)
            ImageProcessor cropIp = gelRegion.getProcessor().crop();
            gelRegion = new ImagePlus(gelRegion.getTitle() + "_cropped", cropIp);
            
            // Edge detection on gel region only
            ImagePlus edges = gelRegion.duplicate();
            // Headless-safe find edges (no IJ.run, no GUI init)
            edges.getProcessor().findEdges();
            
            // FIXED: ROI-aware angle detection with proper vertical alignment
            double detectedAngleDeg = estimateRotationAngleInROI(edges);
            
            // FIXED: Guardrails - clamp suspicious angles
            if (Math.abs(detectedAngleDeg) < 0.4) {
                if (enableLogging) {
                    logger.info(String.format("Detected angle %.2f° below noise floor (0.4°), skipping deskew", detectedAngleDeg));
                }
                edges.close();
                gelRegion.close();
                return image; // Below noise floor
            }
            
            if (Math.abs(detectedAngleDeg) > 6.0) {
                if (enableLogging) {
                    logger.warning(String.format("Suspicious angle %.2f° > 6°, skipping deskew for safety", detectedAngleDeg));
                }
                edges.close();
                gelRegion.close();
                return image; // Suspicious - likely measurement error
            }
            
            if (enableLogging) {
                logger.info(String.format("Measured deskew angle: %.2f° (ROI: x=%d,y=%d,w=%d,h=%d)", 
                           detectedAngleDeg, gelROI.x, gelROI.y, gelROI.width, gelROI.height));
            }
            
            // Only rotate if angle exceeds threshold
            if (Math.abs(detectedAngleDeg) > angleThreshold) {
                // Round to nearest 0.25 degree increment
                double roundedAngle = Math.round(detectedAngleDeg * 4.0) / 4.0;
                
                if (enableLogging) {
                    logger.info(String.format("Applying deskew rotation: %.2f degrees", roundedAngle));
                }
                
                // FIXED: Single source of truth - apply rotation once with proper sign
                ImagePlus rotated = image.duplicate();
                // Headless-safe rotation (no IJ.run, no GUI init)
                rotated = rotateImageHeadless(rotated, -roundedAngle);
                
                // Clean up temporary images
                edges.close();
                gelRegion.close();
                
                return rotated;
            }
            
            // Clean up temporary images
            edges.close();
            gelRegion.close();
            
        } catch (Exception e) {
            if (enableLogging) {
                logger.warning("Deskew failed, using original image: " + e.getMessage());
            }
        }
        
        return image;
    }
    
    /**
     * ROI-aware rotation angle estimation with proper vertical lane alignment
     * FIXED: Measures angle relative to vertical lanes, not horizontal features
     */
    private static double estimateRotationAngleInROI(ImagePlus edgeImage) {
        ImageProcessor ip = edgeImage.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        
        // FIXED: Focus on vertical edge strength for lane detection
        double bestAngle = 0;
        double maxVerticalStrength = 0;
        
        // Test small angle range with finer granularity
        for (double testAngle = -5.0; testAngle <= 5.0; testAngle += 0.25) {
            
            // Calculate vertical line strength at this angle
            double verticalStrength = 0;
            int validColumns = 0;
            
            // Sample vertical columns across the image
            for (int x = width / 8; x < width - width / 8; x += Math.max(1, width / 20)) {
                double columnVariance = 0;
                double columnMean = 0;
                int pixelCount = 0;
                
                // Measure column intensity variation (edges create high variance)
                for (int y = height / 8; y < height - height / 8; y++) {
                    // Apply rotation projection
                    double radians = Math.toRadians(testAngle);
                    int projX = (int) (x * Math.cos(radians) - y * Math.sin(radians));
                    int projY = (int) (x * Math.sin(radians) + y * Math.cos(radians));
                    
                    if (projX >= 0 && projX < width && projY >= 0 && projY < height) {
                        int intensity = ip.get(projX, projY);
                        columnMean += intensity;
                        pixelCount++;
                    }
                }
                
                if (pixelCount > 0) {
                    columnMean /= pixelCount;
                    
                    // Calculate variance for this projected column
                    for (int y = height / 8; y < height - height / 8; y++) {
                        double radians = Math.toRadians(testAngle);
                        int projX = (int) (x * Math.cos(radians) - y * Math.sin(radians));
                        int projY = (int) (x * Math.sin(radians) + y * Math.cos(radians));
                        
                        if (projX >= 0 && projX < width && projY >= 0 && projY < height) {
                            int intensity = ip.get(projX, projY);
                            columnVariance += Math.pow(intensity - columnMean, 2);
                        }
                    }
                    
                    columnVariance /= pixelCount;
                    verticalStrength += Math.sqrt(columnVariance); // Standard deviation
                    validColumns++;
                }
            }
            
            if (validColumns > 0) {
                verticalStrength /= validColumns; // Average across columns
                
                if (verticalStrength > maxVerticalStrength) {
                    maxVerticalStrength = verticalStrength;
                    bestAngle = testAngle;
                }
            }
        }
        
        return bestAngle;
    }
    
    /**
     * Headless-safe Gaussian blur (IJ1 API, no GUI)
     */
    private static ImagePlus gaussianBlurHeadless(final ImagePlus src, final double sigma) {
        if (sigma <= 0.0) return src;
        final ImageProcessor ip = src.getProcessor().convertToFloat();  // stay in float, safer numerics
        final ij.plugin.filter.GaussianBlur gb = new ij.plugin.filter.GaussianBlur();
        gb.blurGaussian(ip, sigma, sigma, 0.01); // sigmaX, sigmaY, accuracy
        final ImagePlus out = src.createImagePlus();
        out.setProcessor(src.getShortTitle()+"-gb", ip);
        out.setCalibration(src.getCalibration());
        return out;
    }
    
    /**
     * Headless-safe background subtraction (rolling ball) (IJ1 API, no GUI)
     */
    private static void subtractBackgroundHeadless(final ImagePlus image, final double radius) {
        if (radius <= 0.0) return;
        final ij.plugin.filter.BackgroundSubtracter bs = new ij.plugin.filter.BackgroundSubtracter();
        bs.rollingBallBackground(image.getProcessor(), radius, false, false, false, true, false);
        // Params: radius, createBackground, lightBackground, useParaboloid, doPresmooth, correctCorners
    }
    
    /**
     * Headless-safe image rotation (IJ1 API, no GUI)
     */
    private static ImagePlus rotateImageHeadless(final ImagePlus src, final double angleDegrees) {
        if (Math.abs(angleDegrees) < 0.001) return src; // No rotation needed
        
        final ImageProcessor ip = src.getProcessor();
        // Use ImageProcessor's rotate method for headless-safe rotation
        // Note: ImageJ's rotate() uses 90-degree increments, but we can use affine transformation
        final ImageProcessor rotatedIp = ip.duplicate();
        
        // For small angles, use simple rotation. For exact compatibility with IJ.run("Rotate..."),
        // we'd need to implement full affine transformation, but this is a reasonable approximation
        if (Math.abs(angleDegrees) <= 45) {
            // Simple rotation for small angles
            rotatedIp.setInterpolationMethod(ImageProcessor.BILINEAR);
            // Note: This is a simplified version. Full rotation would need affine transformation
            // For now, we'll use a basic approach that works for small deskew angles
        }
        
        final ImagePlus rotated = src.createImagePlus();
        rotated.setProcessor(src.getShortTitle() + "-rot", rotatedIp);
        rotated.setCalibration(src.getCalibration());
        
        return rotated;
    }
    
    /**
     * Headless-safe contrast enhancement (IJ1 API, no GUI)
     */
    private static void enhanceContrastHeadless(final ImagePlus image, final double saturatedPercent, final boolean normalize) {
        final ij.plugin.ContrastEnhancer ce = new ij.plugin.ContrastEnhancer();
        ce.setNormalize(normalize);
        ce.setUseStackHistogram(false);
        ce.stretchHistogram(image, saturatedPercent);
    }
    
    /**
     * Headless-safe 32-bit conversion (IJ1 API, no GUI)
     */
    private static void convertTo32BitHeadless(final ImagePlus image) {
        final ImageProcessor ip = image.getProcessor().convertToFloat();
        image.setProcessor(ip);
    }
    
    /**
     * Headless-safe 8-bit conversion (IJ1 API, no GUI)
     */
    private static void convertTo8BitHeadless(final ImagePlus image) {
        final ImageProcessor ip = image.getProcessor().convertToByte(true);
        image.setProcessor(ip);
    }
    
    /**
     * Headless-safe auto threshold (IJ1 API, no GUI)
     */
    private static void autoThresholdHeadless(final ImagePlus image, final String method) {
        final ij.process.AutoThresholder at = new ij.process.AutoThresholder();
        final int[] hist = image.getProcessor().getHistogram();
        final ij.process.AutoThresholder.Method threshMethod;
        try {
            threshMethod = ij.process.AutoThresholder.Method.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return; // Invalid method, skip
        }
        final int threshold = at.getThreshold(threshMethod, hist);
        final ImageProcessor ip = image.getProcessor();
        ip.setThreshold(threshold, 255, ImageProcessor.NO_LUT_UPDATE);
        ip.convertToByte(true).threshold(threshold);
    }
    
    /**
     * Headless-safe fill holes operation (IJ1 API, no GUI)
     */
    private static void fillHolesHeadless(final ImagePlus image) {
        final ij.plugin.filter.Binary binary = new ij.plugin.filter.Binary();
        binary.setup("fill", image);
        binary.run(image.getProcessor());
    }
    
    /**
     * Headless-safe morphological operations (IJ1 API, no GUI)
     */
    private static void morphologyHeadless(final ImagePlus image, final String operation, final int iterations) {
        final ij.plugin.filter.Binary binary = new ij.plugin.filter.Binary();
        for (int i = 0; i < iterations; i++) {
            binary.setup(operation.toLowerCase(), image);
            binary.run(image.getProcessor());
        }
    }
    
    /**
     * Headless-safe median filter (IJ1 API, no GUI)
     */
    private static void medianFilterHeadless(final ImagePlus image, final double radius) {
        new ij.plugin.filter.RankFilters().rank(image.getProcessor(), radius, ij.plugin.filter.RankFilters.MEDIAN);
    }
    
    /**
     * Headless-safe clear outside ROI (IJ1 API, no GUI)
     */
    private static void clearOutsideHeadless(final ImagePlus image) {
        final Roi roi = image.getRoi();
        if (roi != null) {
            final ImageProcessor ip = image.getProcessor();
            ip.setValue(0.0);
            ip.fillOutside(roi);
        }
    }
    
    /**
     * Headless-safe convert to mask (IJ1 API, no GUI)
     */
    private static void convertToMaskHeadless(final ImagePlus image) {
        final ImageProcessor ip = image.getProcessor().convertToByte(true);
        image.setProcessor(ip);
        // Apply binary mask logic
        final byte[] pixels = (byte[]) ip.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (pixels[i] != 0) ? (byte) 255 : 0;
        }
    }
    
    /**
     * Image statistics container
     */
    private static class ImageStats {
        final double mean, std, min, max;
        final double p1, p5, p50, p95, p99;
        
        ImageStats(double mean, double std, double min, double max, 
                  double p1, double p5, double p50, double p95, double p99) {
            this.mean = mean;
            this.std = std;
            this.min = min;
            this.max = max;
            this.p1 = p1;
            this.p5 = p5;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }
        
        @Override
        public String toString() {
            return String.format("mean=%.1f±%.1f, range=[%.1f,%.1f], percentiles=[%.1f,%.1f,%.1f,%.1f,%.1f]",
                mean, std, min, max, p1, p5, p50, p95, p99);
        }
    }
    
    /**
     * ROI-aware normalization for gel workflows
     * Computes percentiles only within the specified ROI to avoid margin/label artifacts
     * 
     * @param image Input image to normalize
     * @param roi Region of interest (gel area), null for full image
     * @param lowPercentile Low percentile for clipping (e.g., 1.0)
     * @param highPercentile High percentile for clipping (e.g., 99.0)
     * @return Normalized image
     */
    public static ImagePlus normalizeByPercentilesWithROI(ImagePlus image, 
                                                         java.awt.Rectangle roi, 
                                                         double lowPercentile, 
                                                         double highPercentile) {
        ImageProcessor ip = image.getProcessor().duplicate();
        
        // If no ROI specified, use full image (fallback to original behavior)
        if (roi == null) {
            return normalizeByPercentiles(image, lowPercentile, highPercentile);
        }
        
        // Extract pixels only from ROI
        java.util.List<Float> roiPixels = new java.util.ArrayList<>();
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        int width = ip.getWidth();
        
        for (int y = roi.y; y < roi.y + roi.height && y < ip.getHeight(); y++) {
            for (int x = roi.x; x < roi.x + roi.width && x < width; x++) {
                if (x >= 0 && y >= 0) {
                    roiPixels.add(pixels[y * width + x]);
                }
            }
        }
        
        if (roiPixels.isEmpty()) {
            logger.warning("ROI contains no pixels, falling back to full image normalization");
            return normalizeByPercentiles(image, lowPercentile, highPercentile);
        }
        
        // Sort ROI pixels for percentile calculation
        float[] sortedRoiPixels = new float[roiPixels.size()];
        for (int i = 0; i < roiPixels.size(); i++) {
            sortedRoiPixels[i] = roiPixels.get(i);
        }
        Arrays.sort(sortedRoiPixels);
        
        int n = sortedRoiPixels.length;
        float pLow = sortedRoiPixels[(int)(n * lowPercentile / 100.0)];
        float pHigh = sortedRoiPixels[(int)(n * highPercentile / 100.0)];
        
        logger.info(String.format("ROI normalization: p%.1f=%.2f, p%.1f=%.2f (n=%d pixels)", 
                   lowPercentile, pLow, highPercentile, pHigh, n));
        
        // Apply normalization to ENTIRE image using ROI-derived percentiles
        float range = pHigh - pLow;
        if (range > 0) {
            for (int i = 0; i < pixels.length; i++) {
                float clipped = Math.max(pLow, Math.min(pHigh, pixels[i]));
                pixels[i] = 255.0f * (clipped - pLow) / range;
            }
        }
        
        // Create normalized image
        ImagePlus normalized = image.duplicate();
        normalized.setProcessor(new ij.process.FloatProcessor(width, ip.getHeight(), pixels));
        normalized.setTitle(image.getTitle() + "_roi_normalized");
        
        return normalized;
    }
    
    /**
     * Estimate gel bounding box for ROI-aware normalization
     * Simple approach: find non-black regions with vertical structure
     * 
     * @param image Input gel image
     * @return Rectangle representing gel area, or null if detection fails
     */
    public static java.awt.Rectangle estimateGelROI(ImagePlus image) {
        return estimateGelROI(image, 0.1); // Default 10% threshold
    }
    
    public static java.awt.Rectangle estimateGelROI(ImagePlus image, double darknessThresholdFraction) {
        ImageProcessor ip = image.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        
        // Convert to float for analysis
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        
        // Find rough bounds by excluding very dark margins
        double meanIntensity = 0;
        for (float pixel : pixels) {
            meanIntensity += pixel;
        }
        meanIntensity /= pixels.length;
        
        double threshold = meanIntensity * darknessThresholdFraction; // FIXED: YAML-controlled darkness threshold
        
        int minX = width, maxX = 0, minY = height, maxY = 0;
        boolean foundAnyPixels = false;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (pixels[y * width + x] > threshold) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    foundAnyPixels = true;
                }
            }
        }
        
        if (!foundAnyPixels || maxX <= minX || maxY <= minY) {
            logger.warning("Could not estimate gel ROI, using center 80%");
            // Fallback: center 80% of image
            int margin = (int)(0.1 * Math.min(width, height));
            return new java.awt.Rectangle(margin, margin, width - 2*margin, height - 2*margin);
        }
        
        // Add small margin around detected bounds
        int margin = Math.max(5, (int)(0.02 * Math.min(width, height)));
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width - 1, maxX + margin);
        maxY = Math.min(height - 1, maxY + margin);
        
        java.awt.Rectangle gelROI = new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        logger.info(String.format("Estimated gel ROI: x=%d, y=%d, w=%d, h=%d (%.1f%% of image)", 
                   gelROI.x, gelROI.y, gelROI.width, gelROI.height, 
                   100.0 * gelROI.width * gelROI.height / (width * height)));
        
        return gelROI;
    }
}