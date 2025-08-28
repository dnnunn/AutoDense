package com.betterdairy.autodense.util;

import ij.ImagePlus;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for managing ImageJ resources to prevent memory leaks.
 * 
 * <p>This class provides safe cleanup methods for ImageJ objects like ImagePlus
 * that need proper resource disposal to prevent memory leaks in long-running applications.</p>
 * 
 * @author AutoDense Development Team  
 * @version 1.0
 */
public final class ImageJResourceManager {
    
    private static final Logger logger = Logger.getLogger(ImageJResourceManager.class.getName());
    
    // Utility class - prevent instantiation
    private ImageJResourceManager() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Safely cleanup ImagePlus objects to prevent memory leaks.
     * 
     * <p>This method attempts to flush each ImagePlus object, catching and logging
     * any exceptions that occur during cleanup. This ensures that cleanup failures
     * for one image don't prevent cleanup of others.</p>
     * 
     * @param images Variable number of ImagePlus objects to cleanup (null values are ignored)
     */
    public static void safeCleanup(ImagePlus... images) {
        if (images == null) return;
        
        for (ImagePlus img : images) {
            if (img != null) {
                try {
                    img.flush();
                } catch (Exception e) {
                    logger.log(Level.FINE, "Error during ImagePlus cleanup for image: " + 
                        (img.getTitle() != null ? img.getTitle() : "unnamed"), e);
                }
            }
        }
    }
    
    /**
     * Safely cleanup a single ImagePlus object.
     * 
     * @param image ImagePlus object to cleanup (null values are ignored)
     */
    public static void safeCleanup(ImagePlus image) {
        safeCleanup(new ImagePlus[]{image});
    }
}