package com.betterdairy.autodense.imageio;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ByteProcessor;

/**
 * Three-pass band filter processor for blue colony detection workflows.
 * 
 * Implements color channel separation and filtering specifically optimized
 * for X-gal blue/white colony screening. Uses RGB channel analysis to
 * distinguish blue colonies (high blue, low red/green) from white colonies
 * (balanced RGB) and mixed colonies (intermediate values).
 * 
 * Three-pass filtering approach:
 * 1. Blue Channel Enhancement - Emphasizes blue components
 * 2. Red/Green Suppression - Reduces non-blue signals  
 * 3. Contrast Optimization - Maximizes blue/white discrimination
 */
public class ColorChannelProcessor {
    
    public enum ColorChannel {
        RED(0),
        GREEN(1), 
        BLUE(2);
        
        private final int index;
        
        ColorChannel(int index) {
            this.index = index;
        }
        
        public int getIndex() { return index; }
    }
    
    public static class ChannelAnalysisResult {
        public final ImagePlus redChannel;
        public final ImagePlus greenChannel;
        public final ImagePlus blueChannel;
        public final ImagePlus blueEnhanced;
        public final ImagePlus colonyMask;
        public final double blueToWhiteRatio;
        public final int estimatedBlueColonies;
        public final int estimatedWhiteColonies;
        
        public ChannelAnalysisResult(ImagePlus red, ImagePlus green, ImagePlus blue,
                                   ImagePlus blueEnhanced, ImagePlus colonyMask,
                                   double blueToWhiteRatio, int blueCount, int whiteCount) {
            this.redChannel = red;
            this.greenChannel = green;
            this.blueChannel = blue;
            this.blueEnhanced = blueEnhanced;
            this.colonyMask = colonyMask;
            this.blueToWhiteRatio = blueToWhiteRatio;
            this.estimatedBlueColonies = blueCount;
            this.estimatedWhiteColonies = whiteCount;
        }
    }
    
    public static class BlueColonyFilterOptions {
        /** Blue enhancement factor (1.0 = no enhancement, 2.0 = double blue signal) */
        public double blueEnhancementFactor = 1.5;
        
        /** Red/Green suppression factor (0.5 = half signal, 1.0 = no suppression) */
        public double rgSuppressionFactor = 0.7;
        
        /** Blue threshold for colony classification (0-255) */
        public int blueThreshold = 120;
        
        /** White threshold (balanced RGB) for colony classification */
        public int whiteBalanceThreshold = 30; // max difference between R,G,B for "white"
        
        /** Minimum colony size in pixels */
        public int minColonySize = 50;
        
        /** Maximum colony size in pixels */
        public int maxColonySize = 5000;
        
        /** Apply median filtering to reduce noise */
        public boolean applyMedianFilter = true;
        
        /** Median filter radius */
        public int medianRadius = 2;
        
        public static BlueColonyFilterOptions defaultOptions() {
            return new BlueColonyFilterOptions();
        }
        
        public static BlueColonyFilterOptions highSensitivity() {
            BlueColonyFilterOptions options = new BlueColonyFilterOptions();
            options.blueEnhancementFactor = 2.0;
            options.rgSuppressionFactor = 0.5;
            options.blueThreshold = 100; // Lower threshold for faint blue
            return options;
        }
        
        public static BlueColonyFilterOptions lowSensitivity() {
            BlueColonyFilterOptions options = new BlueColonyFilterOptions();
            options.blueEnhancementFactor = 1.2;
            options.rgSuppressionFactor = 0.8;
            options.blueThreshold = 150; // Higher threshold for strong blue only
            return options;
        }
    }
    
    /**
     * Perform three-pass color channel analysis for blue colony detection
     */
    public static ChannelAnalysisResult analyzeBlueColonies(ImagePlus colorImage) {
        return analyzeBlueColonies(colorImage, BlueColonyFilterOptions.defaultOptions());
    }
    
    /**
     * Analyze blue colonies with custom options
     */
    public static ChannelAnalysisResult analyzeBlueColonies(ImagePlus colorImage, 
                                                           BlueColonyFilterOptions options) {
        
        if (!(colorImage.getProcessor() instanceof ColorProcessor)) {
            throw new IllegalArgumentException("Input image must be color (RGB)");
        }
        
        // Extract individual color channels
        ImagePlus[] channels = extractColorChannels(colorImage);
        ImagePlus redChannel = channels[0];
        ImagePlus greenChannel = channels[1]; 
        ImagePlus blueChannel = channels[2];
        
        // Apply three-pass filtering
        ImagePlus blueEnhanced = applyThreePassFiltering(colorImage, options);
        
        // Create colony classification mask
        ImagePlus colonyMask = createColonyClassificationMask(colorImage, options);
        
        // Analyze colony statistics
        ColonyStatistics stats = analyzeColonyStatistics(colonyMask, colorImage, options);
        
        return new ChannelAnalysisResult(
            redChannel, greenChannel, blueChannel,
            blueEnhanced, colonyMask,
            stats.blueToWhiteRatio,
            stats.blueColonyCount,
            stats.whiteColonyCount
        );
    }
    
    /**
     * Extract RGB color channels as separate grayscale images
     */
    private static ImagePlus[] extractColorChannels(ImagePlus colorImage) {
        ColorProcessor colorProc = (ColorProcessor) colorImage.getProcessor();
        
        int width = colorProc.getWidth();
        int height = colorProc.getHeight();
        
        ByteProcessor redProc = new ByteProcessor(width, height);
        ByteProcessor greenProc = new ByteProcessor(width, height);
        ByteProcessor blueProc = new ByteProcessor(width, height);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = colorProc.getPixel(x, y);
                
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                redProc.putPixel(x, y, r);
                greenProc.putPixel(x, y, g);
                blueProc.putPixel(x, y, b);
            }
        }
        
        return new ImagePlus[] {
            new ImagePlus("Red_Channel", redProc),
            new ImagePlus("Green_Channel", greenProc),
            new ImagePlus("Blue_Channel", blueProc)
        };
    }
    
    /**
     * Apply three-pass filtering for blue colony enhancement
     * 
     * Pass 1: Blue Channel Enhancement
     * Pass 2: Red/Green Suppression  
     * Pass 3: Contrast Optimization
     */
    private static ImagePlus applyThreePassFiltering(ImagePlus colorImage, 
                                                    BlueColonyFilterOptions options) {
        
        ColorProcessor originalProc = (ColorProcessor) colorImage.getProcessor().duplicate();
        int width = originalProc.getWidth();
        int height = originalProc.getHeight();
        
        // Create enhanced processor for three-pass filtering
        ColorProcessor enhancedProc = new ColorProcessor(width, height);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = originalProc.getPixel(x, y);
                
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                // Pass 1: Blue Channel Enhancement
                double enhancedBlue = Math.min(255, b * options.blueEnhancementFactor);
                
                // Pass 2: Red/Green Suppression
                double suppressedRed = r * options.rgSuppressionFactor;
                double suppressedGreen = g * options.rgSuppressionFactor;
                
                // Pass 3: Contrast Optimization
                // Increase contrast between blue and non-blue regions
                double blueRatio = enhancedBlue / Math.max(1, (suppressedRed + suppressedGreen) / 2);
                if (blueRatio > 1.5) {
                    // Strong blue signal - enhance further
                    enhancedBlue = Math.min(255, enhancedBlue * 1.2);
                    suppressedRed *= 0.8;
                    suppressedGreen *= 0.8;
                }
                
                // Clamp values
                int finalR = (int) Math.max(0, Math.min(255, suppressedRed));
                int finalG = (int) Math.max(0, Math.min(255, suppressedGreen));
                int finalB = (int) Math.max(0, Math.min(255, enhancedBlue));
                
                int newRGB = (finalR << 16) | (finalG << 8) | finalB;
                enhancedProc.putPixel(x, y, newRGB);
            }
        }
        
        // Apply median filter if requested
        if (options.applyMedianFilter) {
            enhancedProc.medianFilter();
        }
        
        return new ImagePlus("Blue_Enhanced", enhancedProc);
    }
    
    /**
     * Create binary mask classifying blue vs white colonies
     */
    private static ImagePlus createColonyClassificationMask(ImagePlus colorImage,
                                                           BlueColonyFilterOptions options) {
        
        ColorProcessor colorProc = (ColorProcessor) colorImage.getProcessor();
        int width = colorProc.getWidth();
        int height = colorProc.getHeight();
        
        ByteProcessor maskProc = new ByteProcessor(width, height);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = colorProc.getPixel(x, y);
                
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                // Classify pixel
                ColonyType type = classifyPixel(r, g, b, options);
                
                int maskValue = switch (type) {
                    case BLUE -> 255;      // White in mask = blue colony
                    case WHITE -> 128;     // Gray in mask = white colony
                    case MIXED -> 64;      // Dark gray = mixed colony
                    case BACKGROUND -> 0;  // Black = background
                };
                
                maskProc.putPixel(x, y, maskValue);
            }
        }
        
        return new ImagePlus("Colony_Classification_Mask", maskProc);
    }
    
    /**
     * Classify individual pixel as blue, white, mixed, or background
     */
    private static ColonyType classifyPixel(int r, int g, int b, BlueColonyFilterOptions options) {
        
        // Check if pixel is likely background (very dark)
        int brightness = (r + g + b) / 3;
        if (brightness < 50) {
            return ColonyType.BACKGROUND;
        }
        
        // Check for blue colony: high blue, relatively low red/green
        if (b >= options.blueThreshold && b > r + 30 && b > g + 30) {
            return ColonyType.BLUE;
        }
        
        // Check for white colony: balanced RGB, reasonably bright
        int rgbDiff = Math.max(Math.abs(r - g), Math.max(Math.abs(g - b), Math.abs(r - b)));
        if (rgbDiff <= options.whiteBalanceThreshold && brightness > 100) {
            return ColonyType.WHITE;
        }
        
        // Check for mixed colony: intermediate values
        if (brightness > 80 && b > options.blueThreshold * 0.7) {
            return ColonyType.MIXED;
        }
        
        return ColonyType.BACKGROUND;
    }
    
    private enum ColonyType {
        BLUE, WHITE, MIXED, BACKGROUND
    }
    
    /**
     * Analyze colony statistics from classification mask
     */
    private static ColonyStatistics analyzeColonyStatistics(ImagePlus maskImage, 
                                                           ImagePlus originalImage,
                                                           BlueColonyFilterOptions options) {
        
        ByteProcessor maskProc = (ByteProcessor) maskImage.getProcessor();
        
        // Count pixels of each type
        int bluePixels = 0;
        int whitePixels = 0;
        int mixedPixels = 0;
        int backgroundPixels = 0;
        
        int width = maskProc.getWidth();
        int height = maskProc.getHeight();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int maskValue = maskProc.getPixel(x, y);
                
                if (maskValue == 255) bluePixels++;
                else if (maskValue == 128) whitePixels++;
                else if (maskValue == 64) mixedPixels++;
                else backgroundPixels++;
            }
        }
        
        // Estimate colony counts based on average colony size
        int avgColonySize = (options.minColonySize + options.maxColonySize) / 2;
        int estimatedBlueColonies = bluePixels / avgColonySize;
        int estimatedWhiteColonies = whitePixels / avgColonySize;
        
        // Calculate blue-to-white ratio
        double blueToWhiteRatio = whitePixels > 0 ? (double) bluePixels / whitePixels : 0.0;
        
        return new ColonyStatistics(blueToWhiteRatio, estimatedBlueColonies, estimatedWhiteColonies,
                                  bluePixels, whitePixels, mixedPixels);
    }
    
    private static class ColonyStatistics {
        final double blueToWhiteRatio;
        final int blueColonyCount;
        final int whiteColonyCount;
        final int bluePixels;
        final int whitePixels;
        final int mixedPixels;
        
        ColonyStatistics(double ratio, int blueCount, int whiteCount, 
                        int bluePixels, int whitePixels, int mixedPixels) {
            this.blueToWhiteRatio = ratio;
            this.blueColonyCount = blueCount;
            this.whiteColonyCount = whiteCount;
            this.bluePixels = bluePixels;
            this.whitePixels = whitePixels;
            this.mixedPixels = mixedPixels;
        }
    }
    
    /**
     * Create RGB stack for advanced color analysis
     */
    public static ImagePlus createRGBStack(ImagePlus colorImage) {
        ImagePlus[] channels = extractColorChannels(colorImage);
        
        ImageStack stack = new ImageStack(colorImage.getWidth(), colorImage.getHeight());
        stack.addSlice("Red", channels[0].getProcessor());
        stack.addSlice("Green", channels[1].getProcessor()); 
        stack.addSlice("Blue", channels[2].getProcessor());
        
        return new ImagePlus("RGB_Stack", stack);
    }
    
    /**
     * Calculate color ratios for colony analysis
     */
    public static double[] calculateColorRatios(int r, int g, int b) {
        double total = r + g + b;
        if (total == 0) return new double[]{0, 0, 0};
        
        return new double[] {
            r / total,  // Red ratio
            g / total,  // Green ratio  
            b / total   // Blue ratio
        };
    }
    
    /**
     * Get blue/non-blue discrimination score
     */
    public static double getBlueDiscriminationScore(int r, int g, int b) {
        if (r + g == 0) return 0.0;
        return (double) b / (r + g);
    }
}