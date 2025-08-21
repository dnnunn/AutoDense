package com.betterdairy.autodense.session;

import ij.ImagePlus;
import ij.gui.Overlay;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/**
 * Handle-based state management for gel analysis sessions.
 * Maintains references to images, overlays, and analysis results
 * without passing pixels to the LLM.
 */
public final class SessionStore {
    
    /**
     * Image record with handle and ImageJ ImagePlus
     */
    public static final class ImageRecord {
        public final String handle;
        public final ImagePlus image;
        public Overlay currentOverlay;
        public Map<String, Object> metadata = new ConcurrentHashMap<>();
        
        public ImageRecord(String handle, ImagePlus image) {
            this.handle = handle;
            this.image = Objects.requireNonNull(image);
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
    
    private final Map<String, ImageRecord> images = new ConcurrentHashMap<>();
    private final Map<String, OverlayRecord> overlays = new ConcurrentHashMap<>();
    private final Map<String, AnalysisRecord> analyses = new ConcurrentHashMap<>();
    private String currentImageHandle = null;
    
    /**
     * Store an image and return its handle
     */
    public String putImage(ImagePlus image) {
        String handle = "img_" + UUID.randomUUID().toString().substring(0, 8);
        images.put(handle, new ImageRecord(handle, image));
        currentImageHandle = handle; // Make it current
        return handle;
    }
    
    /**
     * Get image by handle
     */
    public ImageRecord getImage(String handle) {
        return Objects.requireNonNull(images.get(handle), 
            "Invalid image_handle: " + handle);
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
     * Store an overlay and return its handle
     */
    public String putOverlay(Overlay overlay, String imageHandle) {
        String handle = "ov_" + UUID.randomUUID().toString().substring(0, 8);
        overlays.put(handle, new OverlayRecord(handle, overlay, imageHandle));
        
        // Also update the image's current overlay
        ImageRecord img = getImage(imageHandle);
        img.currentOverlay = overlay;
        
        return handle;
    }
    
    /**
     * Get overlay by handle
     */
    public OverlayRecord getOverlay(String handle) {
        return Objects.requireNonNull(overlays.get(handle), 
            "Invalid overlay_handle: " + handle);
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
    
    public boolean hasImage(String handle) {
        return images.containsKey(handle);
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
    
    private String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
    public String getSessionId() { return sessionId; }
    
    /**
     * Clear all session data
     */
    public void clear() {
        images.clear();
        overlays.clear();
        analyses.clear();
        currentImageHandle = null;
        sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Get session summary for debugging
     */
    public String getSummary() {
        return String.format("Session: %d images, %d overlays, %d analyses. Current: %s",
            images.size(), overlays.size(), analyses.size(), currentImageHandle);
    }
}