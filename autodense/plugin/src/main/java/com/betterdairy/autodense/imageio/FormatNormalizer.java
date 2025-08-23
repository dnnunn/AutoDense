package com.betterdairy.autodense.imageio;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Normalizes various image formats (JPEG, HEIC, PNG, etc.) to standardized 
 * 8-bit or 16-bit grayscale TIFF format for consistent analysis.
 * 
 * Handles:
 * - iPhone HEIC images → Grayscale TIFF
 * - JPEG images → Grayscale TIFF  
 * - Color images → Grayscale conversion with proper weighting
 * - Bit depth normalization (8-bit or 16-bit output)
 * - Metadata preservation where possible
 * - Three-channel color analysis for blue colony detection
 */
public class FormatNormalizer {
    
    private static final Logger logger = Logger.getLogger(FormatNormalizer.class.getName());
    
    public enum BitDepth {
        EIGHT_BIT(8),
        SIXTEEN_BIT(16);
        
        private final int bits;
        
        BitDepth(int bits) {
            this.bits = bits;
        }
        
        public int getBits() { return bits; }
        public int getMaxValue() { return (1 << bits) - 1; }
    }
    
    public enum ConversionMethod {
        /** Standard luminance weighting: 0.299*R + 0.587*G + 0.114*B */
        LUMINANCE,
        /** Simple average: (R + G + B) / 3 */
        AVERAGE,
        /** Use single channel (for already grayscale images) */
        SINGLE_CHANNEL,
        /** Preserve color for colony analysis (RGB → 3-channel TIFF) */
        PRESERVE_COLOR_CHANNELS
    }
    
    public static class NormalizationOptions {
        public BitDepth targetBitDepth = BitDepth.EIGHT_BIT;
        public ConversionMethod conversionMethod = ConversionMethod.LUMINANCE;
        public boolean preserveMetadata = true;
        public boolean enhanceContrast = false;
        public double contrastFactor = 1.2;
        public String outputDirectory = null; // null = temp directory
        
        public static NormalizationOptions defaultOptions() {
            return new NormalizationOptions();
        }
        
        public static NormalizationOptions forColonyAnalysis() {
            NormalizationOptions options = new NormalizationOptions();
            options.conversionMethod = ConversionMethod.PRESERVE_COLOR_CHANNELS;
            options.targetBitDepth = BitDepth.EIGHT_BIT;
            options.enhanceContrast = true;
            return options;
        }
        
        public static NormalizationOptions forGelAnalysis() {
            NormalizationOptions options = new NormalizationOptions();
            options.conversionMethod = ConversionMethod.LUMINANCE;
            options.targetBitDepth = BitDepth.EIGHT_BIT;
            options.enhanceContrast = true;
            return options;
        }
    }
    
    public static class NormalizationResult {
        public final String normalizedFilePath;
        public final String originalFormat;
        public final BitDepth outputBitDepth;
        public final ConversionMethod conversionMethod;
        public final boolean hasColorChannels;
        public final int width;
        public final int height;
        public final String metadata;
        
        public NormalizationResult(String normalizedFilePath, String originalFormat, 
                                 BitDepth outputBitDepth, ConversionMethod conversionMethod,
                                 boolean hasColorChannels, int width, int height, String metadata) {
            this.normalizedFilePath = normalizedFilePath;
            this.originalFormat = originalFormat;
            this.outputBitDepth = outputBitDepth;
            this.conversionMethod = conversionMethod;
            this.hasColorChannels = hasColorChannels;
            this.width = width;
            this.height = height;
            this.metadata = metadata;
        }
    }
    
    /**
     * Normalize any supported image format to grayscale TIFF
     */
    public static NormalizationResult normalizeToGrayscaleTIFF(String inputPath) throws IOException {
        return normalizeToGrayscaleTIFF(inputPath, NormalizationOptions.defaultOptions());
    }
    
    /**
     * Normalize image with custom options
     */
    public static NormalizationResult normalizeToGrayscaleTIFF(String inputPath, 
                                                              NormalizationOptions options) throws IOException {
        
        logger.info("Normalizing image: " + inputPath);
        
        // Ensure ImageIO services are initialized
        ImageIOServiceRegistry.initialize();
        
        // Load original image using ImageIO for format support
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input file does not exist: " + inputPath);
        }
        
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("Unsupported image format or corrupted file: " + inputPath);
        }
        
        String originalFormat = getImageFormat(inputPath);
        logger.info("Original format: " + originalFormat);
        
        // Convert BufferedImage to ImagePlus for ImageJ processing
        ImagePlus imagePlus = convertToImagePlus(originalImage, inputPath);
        
        // Apply normalization based on options
        ImagePlus normalizedImage = applyNormalization(imagePlus, options);
        
        // Generate output path
        String outputPath = generateOutputPath(inputPath, options);
        
        // Save as TIFF
        ij.io.FileSaver saver = new ij.io.FileSaver(normalizedImage);
        boolean success;
        
        if (options.targetBitDepth == BitDepth.SIXTEEN_BIT) {
            success = saver.saveAsTiff(outputPath);
        } else {
            success = saver.saveAsTiff(outputPath);
        }
        
        if (!success) {
            throw new IOException("Failed to save normalized image to: " + outputPath);
        }
        
        logger.info("Image normalized and saved to: " + outputPath);
        
        return new NormalizationResult(
            outputPath,
            originalFormat,
            options.targetBitDepth,
            options.conversionMethod,
            options.conversionMethod == ConversionMethod.PRESERVE_COLOR_CHANNELS,
            normalizedImage.getWidth(),
            normalizedImage.getHeight(),
            extractMetadata(originalImage, inputPath)
        );
    }
    
    /**
     * Convert BufferedImage to ImagePlus for ImageJ processing
     */
    private static ImagePlus convertToImagePlus(BufferedImage bufferedImage, String title) {
        ImagePlus imagePlus = new ImagePlus(title, bufferedImage);
        return imagePlus;
    }
    
    /**
     * Apply normalization based on specified options
     */
    private static ImagePlus applyNormalization(ImagePlus original, NormalizationOptions options) {
        ImageProcessor processor = original.getProcessor();
        
        // Handle color conversion
        switch (options.conversionMethod) {
            case LUMINANCE -> {
                if (processor instanceof ColorProcessor) {
                    // Convert color to grayscale using luminance weighting
                    ColorProcessor colorProc = (ColorProcessor) processor;
                    processor = colorProc.convertToByte(true); // true = use luminance
                }
            }
            case AVERAGE -> {
                if (processor instanceof ColorProcessor) {
                    // Convert using simple average
                    processor = convertToGrayscaleAverage((ColorProcessor) processor);
                }
            }
            case PRESERVE_COLOR_CHANNELS -> {
                // Keep color information for colony analysis
                return preserveColorChannels(original, options);
            }
            case SINGLE_CHANNEL -> {
                // Use as-is (already grayscale)
            }
        }
        
        // Apply bit depth conversion
        processor = convertBitDepth(processor, options.targetBitDepth);
        
        // Apply contrast enhancement if requested
        if (options.enhanceContrast) {
            enhanceContrast(processor, options.contrastFactor);
        }
        
        return new ImagePlus(original.getTitle() + "_normalized", processor);
    }
    
    /**
     * Convert color to grayscale using simple average
     */
    private static ImageProcessor convertToGrayscaleAverage(ColorProcessor colorProc) {
        int width = colorProc.getWidth();
        int height = colorProc.getHeight();
        
        ByteProcessor grayProc = new ByteProcessor(width, height);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = colorProc.getPixel(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                int gray = (r + g + b) / 3;
                grayProc.putPixel(x, y, gray);
            }
        }
        
        return grayProc;
    }
    
    /**
     * Preserve color channels for colony analysis
     */
    private static ImagePlus preserveColorChannels(ImagePlus original, NormalizationOptions options) {
        // Return color image for three-pass band filter analysis
        ImageProcessor processor = original.getProcessor();
        
        // Ensure we have a color processor
        if (!(processor instanceof ColorProcessor)) {
            // Convert grayscale to color if needed
            ColorProcessor colorProc = new ColorProcessor(processor.getWidth(), processor.getHeight());
            colorProc.insert(processor, 0, 0);
            processor = colorProc;
        }
        
        // Apply bit depth conversion if needed
        if (options.targetBitDepth == BitDepth.SIXTEEN_BIT) {
            // Convert to 16-bit color (more complex)
            processor = convertColorTo16Bit((ColorProcessor) processor);
        }
        
        // Apply contrast enhancement
        if (options.enhanceContrast) {
            enhanceContrast(processor, options.contrastFactor);
        }
        
        return new ImagePlus(original.getTitle() + "_color_preserved", processor);
    }
    
    /**
     * Convert processor to specified bit depth
     */
    private static ImageProcessor convertBitDepth(ImageProcessor processor, BitDepth targetDepth) {
        switch (targetDepth) {
            case EIGHT_BIT -> {
                if (!(processor instanceof ByteProcessor)) {
                    return processor.convertToByte(true);
                }
                return processor;
            }
            case SIXTEEN_BIT -> {
                if (!(processor instanceof ShortProcessor)) {
                    return processor.convertToShort(true);
                }
                return processor;
            }
        }
        return processor;
    }
    
    /**
     * Convert color processor to 16-bit
     */
    private static ImageProcessor convertColorTo16Bit(ColorProcessor colorProc) {
        // For now, convert to 16-bit grayscale
        // TODO: Implement true 16-bit color support if needed
        ByteProcessor byteProc = (ByteProcessor) colorProc.convertToByte(true);
        return byteProc.convertToShort(true);
    }
    
    /**
     * Enhance image contrast
     */
    private static void enhanceContrast(ImageProcessor processor, double factor) {
        processor.multiply(factor);
        processor.resetMinAndMax();
    }
    
    /**
     * Generate output file path for normalized image
     */
    private static String generateOutputPath(String inputPath, NormalizationOptions options) {
        Path inputPathObj = Paths.get(inputPath);
        String baseName = inputPathObj.getFileName().toString();
        
        // Remove original extension
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }
        
        // Add normalization suffix
        String suffix = options.conversionMethod == ConversionMethod.PRESERVE_COLOR_CHANNELS 
                       ? "_color_normalized" : "_normalized";
        String outputName = baseName + suffix + ".tiff";
        
        // Determine output directory
        String outputDir;
        if (options.outputDirectory != null) {
            outputDir = options.outputDirectory;
        } else {
            outputDir = System.getProperty("java.io.tmpdir");
        }
        
        return Paths.get(outputDir, outputName).toString();
    }
    
    /**
     * Detect image format from file extension and content
     */
    private static String getImageFormat(String filePath) {
        String extension = "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filePath.substring(lastDot + 1).toLowerCase();
        }
        
        // Map common extensions to standard format names
        return switch (extension) {
            case "jpg", "jpeg" -> "JPEG";
            case "png" -> "PNG";
            case "tif", "tiff" -> "TIFF";
            case "heic", "heif" -> "HEIC";
            case "bmp" -> "BMP";
            case "gif" -> "GIF";
            default -> "UNKNOWN";
        };
    }
    
    /**
     * Extract metadata from original image
     */
    private static String extractMetadata(BufferedImage image, String filePath) {
        StringBuilder metadata = new StringBuilder();
        metadata.append("Original file: ").append(filePath).append("\n");
        metadata.append("Dimensions: ").append(image.getWidth()).append("x").append(image.getHeight()).append("\n");
        
        ColorModel colorModel = image.getColorModel();
        metadata.append("Color model: ").append(colorModel.getClass().getSimpleName()).append("\n");
        metadata.append("Bits per pixel: ").append(colorModel.getPixelSize()).append("\n");
        metadata.append("Has alpha: ").append(colorModel.hasAlpha()).append("\n");
        
        return metadata.toString();
    }
    
    /**
     * Check if file is supported for normalization
     */
    public static boolean isSupportedFormat(String filePath) {
        String format = getImageFormat(filePath);
        return ImageIOServiceRegistry.isReadFormatSupported(format);
    }
    
    /**
     * Get temporary directory for normalized images
     */
    public static String getTempDirectory() {
        return System.getProperty("java.io.tmpdir") + File.separator + "autodense_normalized";
    }
}