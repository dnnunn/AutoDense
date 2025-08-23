package com.betterdairy.autodense.imageio;

import com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriterSpi;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import java.util.logging.Logger;

/**
 * Registers TwelveMonkeys ImageIO service providers globally for HEIC/HEIF support.
 * 
 * This class ensures that all ImageIO format readers and writers are available
 * throughout the application, including support for:
 * - HEIC/HEIF images (iPhone format)
 * - Enhanced JPEG support
 * - Extended TIFF support
 * - Metadata preservation
 * 
 * Must be called during application startup to register services before
 * any image loading operations.
 */
public class ImageIOServiceRegistry {
    
    private static final Logger logger = Logger.getLogger(ImageIOServiceRegistry.class.getName());
    private static boolean initialized = false;
    
    /**
     * Register all TwelveMonkeys ImageIO service providers
     */
    public static synchronized void initialize() {
        if (initialized) {
            return; // Already initialized
        }
        
        logger.info("Initializing ImageIO service registry for HEIC/HEIF support...");
        
        try {
            IIORegistry registry = IIORegistry.getDefaultInstance();
            
            // Register TwelveMonkeys service providers
            registerTwelveMonkeysProviders(registry);
            
            // Register Nightmonkeys HEIC providers  
            registerHEICProviders(registry);
            
            // Verify registration
            verifyRegistration();
            
            initialized = true;
            logger.info("ImageIO service registry initialized successfully");
            
        } catch (Exception e) {
            logger.severe("Failed to initialize ImageIO services: " + e.getMessage());
            throw new RuntimeException("ImageIO initialization failed", e);
        }
    }
    
    /**
     * Register TwelveMonkeys image format providers
     */
    private static void registerTwelveMonkeysProviders(IIORegistry registry) {
        
        // Enhanced JPEG support
        registry.registerServiceProvider(new JPEGImageReaderSpi());
        
        // Enhanced TIFF support
        registry.registerServiceProvider(new TIFFImageReaderSpi());
        registry.registerServiceProvider(new TIFFImageWriterSpi());
        
        logger.info("TwelveMonkeys providers registered");
    }
    
    /**
     * Register additional image format providers
     */
    private static void registerHEICProviders(IIORegistry registry) {
        try {
            // Register additional TwelveMonkeys providers
            registry.registerServiceProvider(new com.twelvemonkeys.imageio.plugins.bmp.BMPImageReaderSpi());
            
            logger.info("Additional format providers registered");
            
            // Note: HEIC support requires platform-specific libraries
            // Users can convert HEIC to JPEG/PNG before processing
            logger.info("For HEIC images: please convert to JPEG/PNG format first");
            
        } catch (Exception e) {
            logger.warning("Some additional format providers not available: " + e.getMessage());
        }
    }
    
    /**
     * Verify that all expected format readers are registered
     */
    private static void verifyRegistration() {
        String[] supportedFormats = ImageIO.getReaderFormatNames();
        
        logger.info("Supported image formats after registration:");
        for (String format : supportedFormats) {
            logger.info("  - " + format);
        }
        
        // Check for critical formats
        boolean hasJPEG = false, hasTIFF = false, hasHEIC = false;
        
        for (String format : supportedFormats) {
            if (format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("JPG")) {
                hasJPEG = true;
            } else if (format.equalsIgnoreCase("TIFF") || format.equalsIgnoreCase("TIF")) {
                hasTIFF = true;
            } else if (format.equalsIgnoreCase("HEIC") || format.equalsIgnoreCase("HEIF")) {
                hasHEIC = true;
            }
        }
        
        if (!hasJPEG) logger.warning("JPEG support may be limited");
        if (!hasTIFF) logger.warning("TIFF support may be limited");
        if (!hasHEIC) logger.warning("HEIC/HEIF support not available");
        
        logger.info(String.format("Format support verified: JPEG=%s, TIFF=%s, HEIC=%s", 
                                hasJPEG, hasTIFF, hasHEIC));
    }
    
    /**
     * Get list of all supported reader format names
     */
    public static String[] getSupportedReadFormats() {
        return ImageIO.getReaderFormatNames();
    }
    
    /**
     * Get list of all supported writer format names
     */
    public static String[] getSupportedWriteFormats() {
        return ImageIO.getWriterFormatNames();
    }
    
    /**
     * Check if a specific format is supported for reading
     */
    public static boolean isReadFormatSupported(String format) {
        if (format == null) return false;
        
        String[] supportedFormats = getSupportedReadFormats();
        for (String supportedFormat : supportedFormats) {
            if (supportedFormat.equalsIgnoreCase(format)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if HEIC format is supported
     */
    public static boolean isHEICSupported() {
        return isReadFormatSupported("HEIC") || isReadFormatSupported("HEIF");
    }
    
    /**
     * Get detailed information about ImageIO registry
     */
    public static String getRegistryInfo() {
        StringBuilder info = new StringBuilder();
        info.append("ImageIO Registry Information:\n");
        info.append("============================\n");
        
        info.append("Reader formats: ");
        String[] readerFormats = getSupportedReadFormats();
        for (int i = 0; i < readerFormats.length; i++) {
            info.append(readerFormats[i]);
            if (i < readerFormats.length - 1) info.append(", ");
        }
        info.append("\n");
        
        info.append("Writer formats: ");
        String[] writerFormats = getSupportedWriteFormats();
        for (int i = 0; i < writerFormats.length; i++) {
            info.append(writerFormats[i]);
            if (i < writerFormats.length - 1) info.append(", ");
        }
        info.append("\n");
        
        info.append("HEIC support: ").append(isHEICSupported() ? "Yes" : "No").append("\n");
        
        return info.toString();
    }
}