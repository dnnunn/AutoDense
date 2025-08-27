package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import org.json.JSONArray;
import com.betterdairy.autodense.util.ResourceManager;

/**
 * Resource-aware wrapper for ImagePreprocessor that prevents memory leaks.
 * 
 * This class provides the same functionality as ImagePreprocessor but with
 * automatic resource management for intermediate ImagePlus instances created
 * during processing.
 * 
 * Key improvements:
 * 1. Automatic cleanup of intermediate processing images
 * 2. Memory usage monitoring
 * 3. Resource leak detection and reporting
 * 4. Try-with-resources compatibility
 * 
 * Usage patterns:
 * 
 * // Recommended: Use try-with-resources
 * try (ResourceAwareImagePreprocessor processor = new ResourceAwareImagePreprocessor("preprocessing")) {
 *     ImagePlus result = processor.apply(image, steps, false);
 *     // Use result...
 * } // Automatic cleanup of intermediate resources
 * 
 * // Legacy compatibility (still safer than direct ImagePreprocessor)
 * ImagePlus result = ResourceAwareImagePreprocessor.applySafely(image, steps, false);
 */
public class ResourceAwareImagePreprocessor implements AutoCloseable {
    
    private final ResourceManager resourceManager;
    
    public ResourceAwareImagePreprocessor(String context) {
        this.resourceManager = new ResourceManager("ImagePreprocessor-" + context);
    }
    
    /**
     * Resource-aware version of ImagePreprocessor.apply().
     * Automatically tracks and cleans up intermediate images.
     */
    public ImagePlus apply(ImagePlus imp, JSONArray steps, boolean destructive) {
        if (imp == null) {
            return null;
        }
        
        // Track the working image if it's a duplicate
        ImagePlus workingImg = destructive ? imp : resourceManager.createTrackedDuplicate(imp);
        
        try {
            // Apply each preprocessing step
            for (int i = 0; i < steps.length(); i++) {
                // Apply the step using the original ImagePreprocessor
                // We need to carefully manage the lifecycle here
                ImagePlus stepResult = applyStep(workingImg, steps.getJSONObject(i));
                
                // If a new image was created, track it and dispose the old working image
                if (stepResult != workingImg) {
                    if (!destructive) { // Only dispose if we created the working image
                        resourceManager.dispose(workingImg);
                    }
                    workingImg = resourceManager.track(stepResult);
                }
            }
            
            // Don't track the final result - caller owns it
            synchronized (resourceManager) {
                resourceManager.dispose(workingImg); // Remove from tracking
            }
            
            return workingImg;
            
        } catch (Exception e) {
            // Cleanup happens automatically via ResourceManager.close()
            throw new RuntimeException("Image preprocessing failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Apply a single preprocessing step with resource management.
     */
    private ImagePlus applyStep(ImagePlus workingImg, org.json.JSONObject step) {
        String op = step.getString("op");
        
        // For operations that create new images, we need special handling
        switch (op) {
            case "rotate" -> {
                double angle = step.getDouble("angle_deg");
                angle = clamp(angle, -180, 180);
                return ImagePreprocessor.rotate(workingImg, angle, true); // Always in-place to avoid new duplicates
            }
            case "flip" -> {
                String axis = step.getString("axis");
                return ImagePreprocessor.flip(workingImg, axis, true); // Always in-place
            }
            case "crop" -> {
                int x = step.getInt("x");
                int y = step.getInt("y");
                int width = step.getInt("width");
                int height = step.getInt("height");
                // Cropping creates a new image, so we track it
                return ImagePreprocessor.crop(workingImg, x, y, width, height);
            }
            case "clahe" -> {
                int blocksize = step.optInt("blocksize", 127);
                int histogram = step.optInt("histogram", 256);
                double maximum = step.optDouble("maximum", 3.0);
                return ImagePreprocessor.clahe(workingImg, blocksize, histogram, maximum);
            }
            case "bandpass" -> {
                double filterLarge = step.optDouble("filter_large", 40.0);
                double filterSmall = step.optDouble("filter_small", 3.0);
                return ImagePreprocessor.bandpassFilter(workingImg, filterLarge, filterSmall);
            }
            case "background" -> {
                String method = step.optString("method", "rolling_ball");
                int radius = step.optInt("radius_px", 50);
                boolean sliding = step.optBoolean("sliding", false);
                boolean smoothing = step.optBoolean("smoothing", false);
                return ImagePreprocessor.subtractBackground(workingImg, method, radius, sliding, smoothing);
            }
            default -> {
                // For other operations, delegate to original preprocessor
                // These operations modify in-place so they don't create new images
                JSONArray singleStep = new JSONArray();
                singleStep.put(step);
                return ImagePreprocessor.apply(workingImg, singleStep, true); // Always destructive for in-place ops
            }
        }
    }
    
    /**
     * Resource-aware version of ImagePreprocessor.applyMode().
     */
    public ImagePlus applyMode(ImagePlus imp, String mode, boolean destructive) {
        if (imp == null) {
            return null;
        }
        
        ImagePlus workingImg = destructive ? imp : resourceManager.createTrackedDuplicate(imp);
        
        try {
            // Apply mode using original ImagePreprocessor
            ImagePlus result = ImagePreprocessor.applyMode(workingImg, mode, true); // Always in-place
            
            // Remove from tracking if it's the final result
            if (!destructive) {
                resourceManager.dispose(result);
            }
            
            return result;
            
        } catch (Exception e) {
            throw new RuntimeException("Image preprocessing mode '" + mode + "' failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get current resource statistics.
     */
    public ResourceManager.ResourceStats getResourceStats() {
        return resourceManager.getStats();
    }
    
    /**
     * Static method for legacy compatibility - creates a temporary ResourceAwareImagePreprocessor.
     * This is safer than direct ImagePreprocessor usage but not as efficient as try-with-resources.
     */
    public static ImagePlus applySafely(ImagePlus imp, JSONArray steps, boolean destructive) {
        try (ResourceAwareImagePreprocessor processor = new ResourceAwareImagePreprocessor("legacy")) {
            return processor.apply(imp, steps, destructive);
        }
    }
    
    /**
     * Static method for legacy compatibility - applies mode safely.
     */
    public static ImagePlus applyModeSafely(ImagePlus imp, String mode, boolean destructive) {
        try (ResourceAwareImagePreprocessor processor = new ResourceAwareImagePreprocessor("legacy-mode")) {
            return processor.applyMode(imp, mode, destructive);
        }
    }
    
    /**
     * Automatic cleanup when used with try-with-resources.
     */
    @Override
    public void close() {
        resourceManager.close();
    }
    
    // Utility method from ImagePreprocessor
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}