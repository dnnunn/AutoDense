package com.betterdairy.autodense.session;

import ij.ImagePlus;
import ij.gui.Overlay;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
// Removed unused collection imports

/**
 * Thread-safe, LRU-enabled handle-based state management for gel analysis sessions.
 * Maintains references to images, overlays, and analysis results without passing pixels to the LLM.
 * 
 * Thread Safety:
 * - All maps are ConcurrentHashMap for thread-safe access
 * - Write operations should be called from orchestration thread
 * - UI reads should be called from EDT only
 * - LRU eviction is protected by ReadWriteLock
 * 
 * Memory Management:
 * - LRU eviction after configurable image count or memory threshold
 * - Manual image closure with closeImage(handle)
 * - Automatic ghost overlay cleanup on image lifecycle events
 */
public final class SessionStore {
    
    /**
     * Enhanced image record with LRU tracking and lifecycle management
     */
    public static final class ImageRecord {
        public final String handle;
        public final ImagePlus image;
        public Overlay currentOverlay;
        public Map<String, Object> metadata = new ConcurrentHashMap<>();
        
        // LRU and lifecycle tracking
        public final long creationTime = System.currentTimeMillis();
        public volatile long lastAccessTime = System.currentTimeMillis();
        public final long estimatedMemoryBytes;
        public volatile boolean detached = false; // For lifecycle management
        
        public ImageRecord(String handle, ImagePlus image) {
            this.handle = handle;
            this.image = Objects.requireNonNull(image);
            this.estimatedMemoryBytes = estimateImageMemory(image);
        }
        
        /**
         * Update access time for LRU tracking (thread-safe)
         */
        public void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        /**
         * Estimate memory usage of ImagePlus
         */
        private static long estimateImageMemory(ImagePlus imp) {
            int width = imp.getWidth();
            int height = imp.getHeight();
            int slices = imp.getStackSize();
            int bytesPerPixel = imp.getBytesPerPixel();
            
            // Base image data + overhead estimate
            long baseMemory = (long) width * height * slices * bytesPerPixel;
            return baseMemory + (baseMemory / 10); // +10% overhead estimate
        }
        
        /**
         * Detach from overlays to prevent ghost references
         */
        public void detachOverlays() {
            if (image != null) {
                image.setOverlay(null);
            }
            currentOverlay = null;
            detached = true;
        }
    }
    
    /**
     * Overlay record with handle and ImageJ Overlay
     */
    public static final class OverlayRecord {
        public final String handle;
        public final Overlay overlay;
        public final String imageHandle; // Associated image
        
        public OverlayRecord(String handle, Overlay overlay, String imageHandle) {
            this.handle = handle;
            this.overlay = Objects.requireNonNull(overlay);
            this.imageHandle = imageHandle;
        }
    }
    
    /**
     * Analysis results record
     */
    public static final class AnalysisRecord {
        public final String handle;
        public final String type; // "lanes", "bands", "quantification", etc.
        public final Object data; // Lane list, band list, etc.
        public final String imageHandle;
        
        public AnalysisRecord(String handle, String type, Object data, String imageHandle) {
            this.handle = handle;
            this.type = type;
            this.data = data;
            this.imageHandle = imageHandle;
        }
    }
    
    /**
     * Fraction mapping for purification tracking (yield & purity)
     */
    public static final class FractionMap {
        public final java.util.Map<String, java.util.List<Integer>> lanesByName; // 1-based lane numbers
        public final java.util.Map<String, java.util.List<Double>> volumesMlByName; // per-lane or single per fraction
        public final double loadedUlPerLaneDefault;
        public final double targetMwKda; // for selecting target bands
        
        public FractionMap(java.util.Map<String, java.util.List<Integer>> lanesByName,
                           java.util.Map<String, java.util.List<Double>> volumesMlByName,
                           double loadedUlPerLaneDefault,
                           double targetMwKda) {
            this.lanesByName = lanesByName;
            this.volumesMlByName = volumesMlByName;
            this.loadedUlPerLaneDefault = loadedUlPerLaneDefault;
            this.targetMwKda = targetMwKda;
        }
    }
    
    // Thread-safe storage with enhanced tracking
    private final Map<String, ImageRecord> images = new ConcurrentHashMap<>();
    private final Map<String, OverlayRecord> overlays = new ConcurrentHashMap<>();
    private final Map<String, AnalysisRecord> analyses = new ConcurrentHashMap<>();
    
    // Active handle tracking (thread-safe)
    private volatile String currentImageHandle = null;
    private volatile String lastActiveImageHandle = null;
    private volatile String lastActiveOverlayHandle = null;
    
    // LRU and memory management
    private final ReadWriteLock evictionLock = new ReentrantReadWriteLock();
    private final AtomicLong totalMemoryBytes = new AtomicLong(0);
    
    // Configuration (can be modified at runtime)
    private volatile int maxImages = 50;              // Max images before LRU eviction
    private volatile long maxMemoryMB = 2048;         // Max memory in MB before eviction
    private volatile boolean autoEvictionEnabled = true;
    
    // Session tracking
    private String sessionId = UUID.randomUUID().toString().substring(0, 8);
    private final long sessionStartTime = System.currentTimeMillis();
    
    // Purification tracking
    private FractionMap fractionMap;
    
    /**
     * Store an image and return its handle (thread-safe with LRU management)
     */
    public String putImage(ImagePlus image) {
        String handle = "img_" + UUID.randomUUID().toString().substring(0, 8);
        ImageRecord record = new ImageRecord(handle, image);
        
        // Thread-safe insertion with memory tracking
        images.put(handle, record);
        totalMemoryBytes.addAndGet(record.estimatedMemoryBytes);
        
        // Update active handles
        setLastActiveImageHandle(handle);
        currentImageHandle = handle;
        
        // Check for LRU eviction
        if (autoEvictionEnabled) {
            checkAndEvictLRU();
        }
        
        return handle;
    }
    
    /**
     * Get image by handle (thread-safe with LRU touch)
     */
    public ImageRecord getImage(String handle) {
        ImageRecord record = images.get(handle);
        if (record == null) {
            throw new IllegalArgumentException("Invalid image_handle: " + handle);
        }
        
        // Touch for LRU (should be called from appropriate thread)
        record.touch();
        setLastActiveImageHandle(handle);
        
        return record;
    }
    
    /**
     * Check if there is a current image
     */
    public boolean hasCurrentImage() {
        return currentImageHandle != null && images.containsKey(currentImageHandle);
    }
    
    /**
     * Get current image (most recently added/accessed)
     */
    public ImageRecord getCurrentImage() {
        if (currentImageHandle == null) {
            throw new IllegalStateException("No image loaded");
        }
        return getImage(currentImageHandle);
    }
    
    /**
     * Store an overlay and return its handle (thread-safe)
     */
    public String putOverlay(Overlay overlay, String imageHandle) {
        String handle = "ov_" + UUID.randomUUID().toString().substring(0, 8);
        overlays.put(handle, new OverlayRecord(handle, overlay, imageHandle));
        
        // Also update the image's current overlay (thread-safe)
        ImageRecord img = getImage(imageHandle);
        img.currentOverlay = overlay;
        
        // Update active overlay handle
        lastActiveOverlayHandle = handle;
        
        return handle;
    }
    
    /**
     * Get overlay by handle (thread-safe)
     */
    public OverlayRecord getOverlay(String handle) {
        OverlayRecord record = overlays.get(handle);
        if (record == null) {
            throw new IllegalArgumentException("Invalid overlay_handle: " + handle);
        }
        
        // Update active overlay handle
        lastActiveOverlayHandle = handle;
        
        return record;
    }
    
    /**
     * Store analysis results and return handle
     */
    public String putAnalysis(String type, Object data, String imageHandle) {
        String handle = "analysis_" + UUID.randomUUID().toString().substring(0, 8);
        analyses.put(handle, new AnalysisRecord(handle, type, data, imageHandle));
        return handle;
    }
    
    /**
     * Get analysis by handle
     */
    public AnalysisRecord getAnalysis(String handle) {
        return Objects.requireNonNull(analyses.get(handle), 
            "Invalid analysis_handle: " + handle);
    }
    
    /**
     * Get session statistics
     */
    public int getImageCount() { return images.size(); }
    public int getOverlayCount() { return overlays.size(); }
    public int getAnalysisCount() { return analyses.size(); }
    
    public List<String> listAvailableImages() {
        return new ArrayList<>(images.keySet());
    }
    
    public String getMostRecentImageHandle() {
        // Simple implementation - could be enhanced with timestamps
        return images.keySet().stream().reduce((first, second) -> second).orElse(null);
    }
    
    /**
     * Get the last active image handle (cleaner alias for handle discipline)
     */
    public String lastActiveImageHandle() {
        return currentImageHandle != null ? currentImageHandle : getMostRecentImageHandle();
    }
    
    public boolean hasImage(String handle) {
        return images.containsKey(handle);
    }
    
    /**
     * Set the last active image handle explicitly (e.g., when a tool returns a new image handle)
     * No-op if the handle is unknown.
     */
    public void setLastActiveImageHandle(String handle) {
        if (handle == null) return;
        if (images.containsKey(handle)) {
            this.currentImageHandle = handle;
        }
    }
    
    public boolean hasOverlay(String handle) {
        return overlays.containsKey(handle);
    }
    
    public boolean hasAnalysis(String handle) {
        return analyses.containsKey(handle);
    }
    
    public List<String> getOverlaysForImage(String imageHandle) {
        return overlays.values().stream()
            .filter(record -> imageHandle.equals(record.imageHandle))
            .map(record -> record.handle)
            .collect(java.util.stream.Collectors.toList());
    }
    
    public List<String> getAnalysesForImage(String imageHandle) {
        return analyses.values().stream()
            .filter(record -> imageHandle.equals(record.imageHandle))
            .map(record -> record.handle)
            .collect(java.util.stream.Collectors.toList());
    }
    
    public List<String> getRecentAnalysisTypes() {
        return analyses.values().stream()
            .map(record -> record.type)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
    }
    
    public String getSessionId() { return sessionId; }
    
    // ==================== Enhanced Active Handle Management ====================
    
    /**
     * Get the last active image handle (thread-safe)
     */
    public String getLastActiveImageHandle() {
        return lastActiveImageHandle;
    }
    
    /**
     * Get the last active overlay handle (thread-safe)
     */
    public String getLastActiveOverlayHandle() {
        return lastActiveOverlayHandle;
    }
    
    
    /**
     * Set the last active overlay handle explicitly (thread-safe)
     */
    public void setLastActiveOverlayHandle(String handle) {
        if (handle != null && overlays.containsKey(handle)) {
            lastActiveOverlayHandle = handle;
        }
    }
    
    // ==================== LRU Eviction and Memory Management ====================
    
    /**
     * Configure eviction parameters
     */
    public void configureEviction(int maxImages, long maxMemoryMB, boolean enabled) {
        this.maxImages = maxImages;
        this.maxMemoryMB = maxMemoryMB;
        this.autoEvictionEnabled = enabled;
    }
    
    /**
     * Get current memory usage in MB
     */
    public long getCurrentMemoryMB() {
        return totalMemoryBytes.get() / (1024 * 1024);
    }
    
    /**
     * Check and perform LRU eviction if needed (thread-safe)
     */
    private void checkAndEvictLRU() {
        evictionLock.writeLock().lock();
        try {
            // Check image count threshold
            if (images.size() > maxImages) {
                evictOldestImages(images.size() - maxImages);
            }
            
            // Check memory threshold
            long currentMemoryMB = getCurrentMemoryMB();
            if (currentMemoryMB > maxMemoryMB) {
                evictByMemoryPressure(currentMemoryMB - maxMemoryMB);
            }
        } finally {
            evictionLock.writeLock().unlock();
        }
    }
    
    /**
     * Evict oldest images by count
     */
    private void evictOldestImages(int countToEvict) {
        List<ImageRecord> sortedByAge = new ArrayList<>(images.values());
        sortedByAge.sort((a, b) -> Long.compare(a.lastAccessTime, b.lastAccessTime));
        
        for (int i = 0; i < Math.min(countToEvict, sortedByAge.size()); i++) {
            ImageRecord oldest = sortedByAge.get(i);
            closeImageInternal(oldest.handle, "LRU eviction (count)");
        }
    }
    
    /**
     * Evict images to free memory
     */
    private void evictByMemoryPressure(long memoryToFreeMB) {
        List<ImageRecord> sortedByAge = new ArrayList<>(images.values());
        sortedByAge.sort((a, b) -> Long.compare(a.lastAccessTime, b.lastAccessTime));
        
        long freedMB = 0;
        for (ImageRecord record : sortedByAge) {
            if (freedMB >= memoryToFreeMB) break;
            
            long imageMB = record.estimatedMemoryBytes / (1024 * 1024);
            closeImageInternal(record.handle, "LRU eviction (memory)");
            freedMB += imageMB;
        }
    }
    
    /**
     * Manually close and remove an image to free memory (thread-safe)
     */
    public boolean closeImage(String handle) {
        evictionLock.writeLock().lock();
        try {
            return closeImageInternal(handle, "Manual closure");
        } finally {
            evictionLock.writeLock().unlock();
        }
    }
    
    /**
     * Internal image closure with ghost overlay cleanup
     */
    private boolean closeImageInternal(String handle, String reason) {
        ImageRecord record = images.get(handle);
        if (record == null) return false;
        
        // Step 1: Detach overlays to prevent ghost references
        record.detachOverlays();
        
        // Step 2: Remove associated overlays
        List<String> associatedOverlays = getOverlaysForImage(handle);
        for (String overlayHandle : associatedOverlays) {
            overlays.remove(overlayHandle);
            if (overlayHandle.equals(lastActiveOverlayHandle)) {
                lastActiveOverlayHandle = null;
            }
        }
        
        // Step 3: Remove associated analyses
        List<String> associatedAnalyses = getAnalysesForImage(handle);
        for (String analysisHandle : associatedAnalyses) {
            analyses.remove(analysisHandle);
        }
        
        // Step 4: Update memory tracking
        totalMemoryBytes.addAndGet(-record.estimatedMemoryBytes);
        
        // Step 5: Remove image and update active handles
        images.remove(handle);
        if (handle.equals(currentImageHandle)) {
            currentImageHandle = null;
        }
        if (handle.equals(lastActiveImageHandle)) {
            lastActiveImageHandle = getMostRecentImageHandle();
        }
        
        System.out.println("SessionStore: Closed image " + handle + " (" + reason + ")");
        return true;
    }
    
    // ==================== Enhanced Lifecycle Management ====================
    
    /**
     * Create a duplicate image with proper lifecycle management
     * Returns new handle and detaches from old overlays
     */
    public String duplicateImage(String sourceHandle, String title) {
        ImageRecord source = getImage(sourceHandle);
        ImagePlus duplicate = source.image.duplicate();
        if (title != null) {
            duplicate.setTitle(title);
        }
        
        // Create new image record (this will get new handle)
        String newHandle = putImage(duplicate);
        
        // Important: Don't copy overlays - let tools create new ones
        // This prevents ghost overlay references
        
        return newHandle;
    }
    
    /**
     * Replace image content for destructive operations
     * Maintains handle but detaches old overlays
     */
    public void replaceImageContent(String handle, ImagePlus newImage) {
        ImageRecord record = images.get(handle);
        if (record == null) {
            throw new IllegalArgumentException("Image handle not found: " + handle);
        }
        
        // Detach old overlays to prevent ghost references
        record.detachOverlays();
        
        // Replace the ImagePlus content
        // Note: We can't actually replace the ImagePlus object due to final field
        // So we copy the new image data into the existing ImagePlus
        record.image.setProcessor(newImage.getProcessor());
        record.image.setStack(newImage.getStack());
        
        // Touch for LRU
        record.touch();
        
        System.out.println("SessionStore: Replaced content for image " + handle);
    }
    
    // ==================== Enhanced Session Management ====================
    
    /**
     * Clear all session data with proper cleanup
     */
    public void clear() {
        evictionLock.writeLock().lock();
        try {
            // Detach all overlays first
            for (ImageRecord record : images.values()) {
                record.detachOverlays();
            }
            
            images.clear();
            overlays.clear();
            analyses.clear();
            
            currentImageHandle = null;
            lastActiveImageHandle = null;
            lastActiveOverlayHandle = null;
            
            totalMemoryBytes.set(0);
            sessionId = UUID.randomUUID().toString().substring(0, 8);
            
            System.out.println("SessionStore: Session cleared, new ID: " + sessionId);
        } finally {
            evictionLock.writeLock().unlock();
        }
    }
    
    /**
     * Get enhanced session summary with memory and threading info
     */
    public String getSummary() {
        long uptimeHours = (System.currentTimeMillis() - sessionStartTime) / (1000 * 60 * 60);
        return String.format(
            "Session %s: %d images (%.1f MB), %d overlays, %d analyses. " +
            "Current: %s, LastActive: %s. Uptime: %dh. Thread: %s",
            sessionId, images.size(), getCurrentMemoryMB(), overlays.size(), analyses.size(),
            currentImageHandle, lastActiveImageHandle, uptimeHours, 
            Thread.currentThread().getName());
    }
    
    /**
     * Get detailed memory breakdown
     */
    public String getMemoryReport() {
        StringBuilder report = new StringBuilder();
        report.append(String.format("Total Memory: %.1f MB\n", getCurrentMemoryMB()));
        report.append(String.format("Images: %d (max: %d)\n", images.size(), maxImages));
        report.append(String.format("Memory Limit: %d MB\n", maxMemoryMB));
        report.append(String.format("Auto-eviction: %s\n", autoEvictionEnabled));
        
        if (!images.isEmpty()) {
            report.append("\nImage Details:\n");
            images.values().stream()
                .sorted((a, b) -> Long.compare(b.lastAccessTime, a.lastAccessTime))
                .limit(10)
                .forEach(record -> {
                    long ageSec = (System.currentTimeMillis() - record.lastAccessTime) / 1000;
                    double sizeMB = record.estimatedMemoryBytes / (1024.0 * 1024.0);
                    report.append(String.format("  %s: %.1f MB, %ds ago%s\n", 
                        record.handle, sizeMB, ageSec,
                        record.detached ? " [DETACHED]" : ""));
                });
        }
        
        return report.toString();
    }
    
    /**
     * Set fraction map for purification tracking
     */
    public void setFractionMap(FractionMap fm) { 
        this.fractionMap = fm; 
    }
    
    /**
     * Get fraction map for purification tracking
     */
    public FractionMap getFractionMap() { 
        return this.fractionMap; 
    }
}