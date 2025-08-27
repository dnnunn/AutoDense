package com.betterdairy.autodense.util;

import ij.ImagePlus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.ref.WeakReference;

/**
 * Utility class for detecting and monitoring resource leaks in AutoDense.
 * 
 * This detector tracks ImagePlus instances and their lifecycle to identify
 * potential memory leaks from unclosed duplicates and working images.
 * 
 * Key features:
 * 1. Tracks all ImagePlus instances created during processing
 * 2. Monitors resource usage patterns and lifecycle
 * 3. Generates leak detection reports with stack traces
 * 4. Provides memory pressure warnings
 * 5. Integration with ResourceManager for comprehensive tracking
 * 
 * Usage:
 * 
 * // Enable leak detection during development/testing
 * ResourceLeakDetector.enableTracking();
 * 
 * // Perform operations that might create leaks
 * performImageProcessing();
 * 
 * // Generate leak report
 * LeakReport report = ResourceLeakDetector.generateLeakReport();
 * if (report.hasLeaks()) {
 *     System.err.println("Memory leaks detected: " + report);
 * }
 */
public class ResourceLeakDetector {
    
    private static final boolean TRACKING_ENABLED = Boolean.getBoolean("autodense.leak.detection");
    private static final int MAX_TRACKED_IMAGES = 10000; // Prevent detector from consuming too much memory
    
    // Track all ImagePlus instances with weak references to avoid holding them in memory
    private static final Map<Integer, ImageTrackingInfo> trackedImages = new ConcurrentHashMap<>();
    private static final AtomicLong totalImagesCreated = new AtomicLong(0);
    private static final AtomicLong totalImagesClosed = new AtomicLong(0);
    
    /**
     * Information tracked for each ImagePlus instance.
     */
    private static class ImageTrackingInfo {
        final WeakReference<ImagePlus> imageRef;
        final String creationContext;
        final long creationTime;
        final StackTraceElement[] creationStack;
        boolean explicitlyClosed = false;
        long closeTime = -1;
        
        ImageTrackingInfo(ImagePlus image, String context) {
            this.imageRef = new WeakReference<>(image);
            this.creationContext = context;
            this.creationTime = System.currentTimeMillis();
            this.creationStack = Thread.currentThread().getStackTrace();
        }
    }
    
    /**
     * Enable resource leak tracking (for development/testing).
     * This should not be enabled in production due to overhead.
     */
    public static void enableTracking() {
        System.setProperty("autodense.leak.detection", "true");
        System.out.println("ResourceLeakDetector: Tracking enabled");
    }
    
    /**
     * Disable resource leak tracking.
     */
    public static void disableTracking() {
        System.setProperty("autodense.leak.detection", "false");
        trackedImages.clear();
        System.out.println("ResourceLeakDetector: Tracking disabled");
    }
    
    /**
     * Track creation of an ImagePlus instance.
     * Should be called immediately after duplicate() or similar operations.
     */
    public static void trackImageCreation(ImagePlus image, String context) {
        if (!TRACKING_ENABLED || image == null) {
            return;
        }
        
        // Prevent memory explosion from tracking too many images
        if (trackedImages.size() >= MAX_TRACKED_IMAGES) {
            return;
        }
        
        int imageId = System.identityHashCode(image);
        trackedImages.put(imageId, new ImageTrackingInfo(image, context));
        totalImagesCreated.incrementAndGet();
    }
    
    /**
     * Track explicit closure of an ImagePlus instance.
     * Should be called when image.close() is invoked.
     */
    public static void trackImageClosure(ImagePlus image) {
        if (!TRACKING_ENABLED || image == null) {
            return;
        }
        
        int imageId = System.identityHashCode(image);
        ImageTrackingInfo info = trackedImages.get(imageId);
        if (info != null) {
            info.explicitlyClosed = true;
            info.closeTime = System.currentTimeMillis();
            totalImagesClosed.incrementAndGet();
        }
    }
    
    /**
     * Generate a comprehensive leak detection report.
     */
    public static LeakReport generateLeakReport() {
        if (!TRACKING_ENABLED) {
            return new LeakReport(false, "Leak detection is disabled", Collections.emptyList(), 0, 0, 0);
        }
        
        List<LeakInfo> potentialLeaks = new ArrayList<>();
        int totalTracked = trackedImages.size();
        int explicitlyClosed = 0;
        int garbageCollected = 0;
        
        for (Map.Entry<Integer, ImageTrackingInfo> entry : trackedImages.entrySet()) {
            ImageTrackingInfo info = entry.getValue();
            ImagePlus image = info.imageRef.get();
            
            if (info.explicitlyClosed) {
                explicitlyClosed++;
            } else if (image == null) {
                // Image was garbage collected without explicit close
                garbageCollected++;
                potentialLeaks.add(new LeakInfo(
                    entry.getKey(),
                    info.creationContext,
                    info.creationTime,
                    System.currentTimeMillis() - info.creationTime,
                    "Garbage collected without explicit close()",
                    info.creationStack
                ));
            } else {
                // Image still exists and wasn't explicitly closed
                potentialLeaks.add(new LeakInfo(
                    entry.getKey(),
                    info.creationContext,
                    info.creationTime,
                    System.currentTimeMillis() - info.creationTime,
                    "Still in memory, not explicitly closed",
                    info.creationStack
                ));
            }
        }
        
        String summary = String.format(
            "Tracked %d images: %d explicitly closed, %d garbage collected, %d potential leaks",
            totalTracked, explicitlyClosed, garbageCollected, potentialLeaks.size()
        );
        
        return new LeakReport(
            !potentialLeaks.isEmpty(),
            summary,
            potentialLeaks,
            totalImagesCreated.get(),
            totalImagesClosed.get(),
            totalTracked
        );
    }
    
    /**
     * Get current memory pressure statistics.
     */
    public static MemoryPressureInfo getMemoryPressure() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double usagePercent = (double) usedMemory / maxMemory * 100;
        boolean highPressure = usagePercent > 80.0;
        
        return new MemoryPressureInfo(
            totalMemory,
            freeMemory,
            usedMemory,
            maxMemory,
            usagePercent,
            highPressure,
            trackedImages.size()
        );
    }
    
    /**
     * Clean up tracking information for garbage collected images.
     * Should be called periodically to prevent memory leaks in the detector itself.
     */
    public static void cleanup() {
        if (!TRACKING_ENABLED) {
            return;
        }
        
        trackedImages.entrySet().removeIf(entry -> entry.getValue().imageRef.get() == null);
    }
    
    /**
     * Information about a potential resource leak.
     */
    public static class LeakInfo {
        public final int imageId;
        public final String creationContext;
        public final long creationTime;
        public final long ageMs;
        public final String leakType;
        public final StackTraceElement[] creationStack;
        
        LeakInfo(int imageId, String creationContext, long creationTime, long ageMs, String leakType, StackTraceElement[] creationStack) {
            this.imageId = imageId;
            this.creationContext = creationContext;
            this.creationTime = creationTime;
            this.ageMs = ageMs;
            this.leakType = leakType;
            this.creationStack = creationStack;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("LeakInfo{")
              .append("id=").append(imageId)
              .append(", context='").append(creationContext).append('\'')
              .append(", age=").append(ageMs).append("ms")
              .append(", type='").append(leakType).append('\'')
              .append('}');
              
            // Add stack trace for debugging
            if (creationStack != null && creationStack.length > 0) {
                sb.append("\n  Created at:");
                for (int i = 3; i < Math.min(creationStack.length, 8); i++) { // Skip first 3 frames (Thread.getStackTrace, etc)
                    StackTraceElement element = creationStack[i];
                    sb.append("\n    ").append(element.toString());
                }
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Comprehensive leak detection report.
     */
    public static class LeakReport {
        public final boolean hasLeaks;
        public final String summary;
        public final List<LeakInfo> potentialLeaks;
        public final long totalImagesCreated;
        public final long totalImagesClosed;
        public final int currentlyTracked;
        
        LeakReport(boolean hasLeaks, String summary, List<LeakInfo> potentialLeaks, 
                  long totalImagesCreated, long totalImagesClosed, int currentlyTracked) {
            this.hasLeaks = hasLeaks;
            this.summary = summary;
            this.potentialLeaks = Collections.unmodifiableList(potentialLeaks);
            this.totalImagesCreated = totalImagesCreated;
            this.totalImagesClosed = totalImagesClosed;
            this.currentlyTracked = currentlyTracked;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("LeakReport{")
              .append("hasLeaks=").append(hasLeaks)
              .append(", summary='").append(summary).append('\'')
              .append(", totalCreated=").append(totalImagesCreated)
              .append(", totalClosed=").append(totalImagesClosed)
              .append(", tracked=").append(currentlyTracked)
              .append('}');
              
            if (!potentialLeaks.isEmpty()) {
                sb.append("\nPotential leaks:");
                for (LeakInfo leak : potentialLeaks) {
                    sb.append("\n  ").append(leak);
                }
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Information about current memory pressure.
     */
    public static class MemoryPressureInfo {
        public final long totalMemory;
        public final long freeMemory;
        public final long usedMemory;
        public final long maxMemory;
        public final double usagePercent;
        public final boolean highPressure;
        public final int trackedImages;
        
        MemoryPressureInfo(long totalMemory, long freeMemory, long usedMemory, long maxMemory,
                          double usagePercent, boolean highPressure, int trackedImages) {
            this.totalMemory = totalMemory;
            this.freeMemory = freeMemory;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            this.usagePercent = usagePercent;
            this.highPressure = highPressure;
            this.trackedImages = trackedImages;
        }
        
        @Override
        public String toString() {
            return String.format(
                "MemoryPressure{used=%.1fMB/%.1fMB (%.1f%%), tracked=%d images, pressure=%s}",
                usedMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0,
                usagePercent,
                trackedImages,
                highPressure ? "HIGH" : "OK"
            );
        }
    }
}