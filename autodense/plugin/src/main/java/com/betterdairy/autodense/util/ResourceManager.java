package com.betterdairy.autodense.util;

import ij.ImagePlus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility for managing ImageJ resources and preventing memory leaks.
 * 
 * Key features:
 * 1. Automatic tracking of ImagePlus instances created during processing
 * 2. Try-with-resources compatible for automatic cleanup
 * 3. Memory usage monitoring and leak detection
 * 4. Safe disposal of resources with proper lifecycle management
 * 
 * Usage patterns:
 * - Use try-with-resources for automatic cleanup
 * - Track duplicates and working images explicitly
 * - Monitor memory usage during processing
 */
public class ResourceManager implements AutoCloseable {
    
    private final List<ImagePlus> trackedImages = new ArrayList<>();
    private final String context;
    private final long startTime;
    private static final AtomicLong totalImagesCreated = new AtomicLong(0);
    private static final AtomicLong totalImagesDisposed = new AtomicLong(0);
    
    public ResourceManager(String context) {
        this.context = context;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Track an ImagePlus for automatic cleanup.
     * Should be called immediately after creating duplicates or working images.
     */
    public <T extends ImagePlus> T track(T image) {
        if (image != null) {
            synchronized (trackedImages) {
                trackedImages.add(image);
                totalImagesCreated.incrementAndGet();
            }
            // Also track in leak detector if enabled
            ResourceLeakDetector.trackImageCreation(image, context);
        }
        return image;
    }
    
    /**
     * Create a tracked duplicate of an image.
     * This is the preferred way to create working copies.
     */
    public ImagePlus createTrackedDuplicate(ImagePlus original) {
        if (original == null) {
            return null;
        }
        
        ImagePlus duplicate = original.duplicate();
        return track(duplicate);
    }
    
    /**
     * Manually dispose of a tracked resource before automatic cleanup.
     * Use when you know a resource is no longer needed.
     */
    public void dispose(ImagePlus image) {
        if (image != null) {
            synchronized (trackedImages) {
                if (trackedImages.remove(image)) {
                    image.close();
                    totalImagesDisposed.incrementAndGet();
                    // Also track closure in leak detector if enabled
                    ResourceLeakDetector.trackImageClosure(image);
                }
            }
        }
    }
    
    /**
     * Get current resource statistics.
     */
    public ResourceStats getStats() {
        synchronized (trackedImages) {
            return new ResourceStats(
                trackedImages.size(),
                totalImagesCreated.get(),
                totalImagesDisposed.get(),
                System.currentTimeMillis() - startTime
            );
        }
    }
    
    /**
     * Check if any resources are still being tracked.
     * Useful for detecting potential leaks.
     */
    public boolean hasActiveResources() {
        synchronized (trackedImages) {
            return !trackedImages.isEmpty();
        }
    }
    
    /**
     * Get list of currently tracked image titles for debugging.
     */
    public List<String> getTrackedImageTitles() {
        synchronized (trackedImages) {
            List<String> titles = new ArrayList<>();
            for (ImagePlus img : trackedImages) {
                titles.add(img.getTitle() != null ? img.getTitle() : "untitled");
            }
            return titles;
        }
    }
    
    /**
     * Automatic cleanup when used with try-with-resources.
     */
    @Override
    public void close() {
        synchronized (trackedImages) {
            int disposed = 0;
            for (ImagePlus image : trackedImages) {
                try {
                    if (image != null) {
                        image.close();
                        disposed++;
                        // Also track closure in leak detector if enabled
                        ResourceLeakDetector.trackImageClosure(image);
                    }
                } catch (Exception e) {
                    // Log but don't fail cleanup for one image
                    System.err.println("Warning: Failed to dispose image in " + context + ": " + e.getMessage());
                }
            }
            
            if (disposed > 0) {
                totalImagesDisposed.addAndGet(disposed);
                System.out.println("ResourceManager[" + context + "]: Disposed " + disposed + 
                    " images after " + (System.currentTimeMillis() - startTime) + "ms");
            }
            
            trackedImages.clear();
        }
    }
    
    /**
     * Get global resource leak statistics across all ResourceManager instances.
     */
    public static GlobalResourceStats getGlobalStats() {
        return new GlobalResourceStats(
            totalImagesCreated.get(),
            totalImagesDisposed.get(),
            totalImagesCreated.get() - totalImagesDisposed.get()
        );
    }
    
    /**
     * Reset global statistics (useful for testing).
     */
    public static void resetGlobalStats() {
        totalImagesCreated.set(0);
        totalImagesDisposed.set(0);
    }
    
    /**
     * Resource statistics for a single ResourceManager instance.
     */
    public static class ResourceStats {
        public final int activeImages;
        public final long totalCreated;
        public final long totalDisposed;
        public final long durationMs;
        
        public ResourceStats(int activeImages, long totalCreated, long totalDisposed, long durationMs) {
            this.activeImages = activeImages;
            this.totalCreated = totalCreated;
            this.totalDisposed = totalDisposed;
            this.durationMs = durationMs;
        }
        
        @Override
        public String toString() {
            return String.format("ResourceStats{active=%d, created=%d, disposed=%d, duration=%dms}",
                activeImages, totalCreated, totalDisposed, durationMs);
        }
    }
    
    /**
     * Global resource statistics across all ResourceManager instances.
     */
    public static class GlobalResourceStats {
        public final long totalCreated;
        public final long totalDisposed;
        public final long potentialLeaks;
        
        public GlobalResourceStats(long totalCreated, long totalDisposed, long potentialLeaks) {
            this.totalCreated = totalCreated;
            this.totalDisposed = totalDisposed;
            this.potentialLeaks = potentialLeaks;
        }
        
        @Override
        public String toString() {
            return String.format("GlobalResourceStats{created=%d, disposed=%d, potential_leaks=%d}",
                totalCreated, totalDisposed, potentialLeaks);
        }
        
        public boolean hasLeaks() {
            return potentialLeaks > 0;
        }
    }
    
    /**
     * Utility method for safely creating and tracking ImagePlus duplicates
     * in legacy code that can't easily use try-with-resources.
     */
    public static ImagePlus safeDuplicate(ImagePlus original, String context) {
        if (original == null) {
            return null;
        }
        
        // For backwards compatibility, we create a temporary ResourceManager
        // This isn't ideal but handles legacy patterns better than nothing
        try (ResourceManager rm = new ResourceManager(context)) {
            return rm.createTrackedDuplicate(original);
        } // Auto-cleanup happens here if there's an exception
    }
}