package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.analysis.ColonyAssistModels.*;
import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;

import java.awt.Color;
import java.util.*;

/**
 * Colony Identification Assist Tool for user-guided colony detection and classification.
 * 
 * Key Features:
 * - User click to add/delete colonies with refinement to blob peaks
 * - Class propagation via logistic regression in (a*, b*, size) space
 * - Distance map analysis for peak refinement
 * - Machine learning-based reclassification
 */
public final class AssistColonyTool {
    
    private final ImagePlus image;
    private final OvalRoi plateRoi;
    private final double pxPerMM;
    private final AssistConfig config;
    private final List<AssistColony> assistColonies;
    private final Map<ColonyColor, LogisticModel> models;
    
    // Distance map cache for blob peak detection
    private ByteProcessor distanceMap;
    private boolean distanceMapValid = false;
    
    public AssistColonyTool(ImagePlus image, OvalRoi plateRoi, double pxPerMM) {
        this(image, plateRoi, pxPerMM, AssistConfig.defaultConfig());
    }
    
    public AssistColonyTool(ImagePlus image, OvalRoi plateRoi, double pxPerMM, AssistConfig config) {
        this.image = image;
        this.plateRoi = plateRoi;
        this.pxPerMM = pxPerMM;
        this.config = config;
        this.assistColonies = new ArrayList<>();
        this.models = new EnumMap<>(ColonyColor.class);
    }
    
    /**
     * Initialize with existing colonies from detection
     */
    public void initializeWithColonies(List<Colony> detectedColonies) {
        assistColonies.clear();
        
        // Convert to assist colonies with features
        for (Colony colony : detectedColonies) {
            ColonyClassifier.LabColor labColor = getColonyLabColor(colony);
            ColonyNormalizer.NormalizedColony normalized = normalizeColony(colony, labColor);
            double distMapValue = getDistanceMapValue(colony.x(), colony.y());
            
            AssistColony assistColony = AssistColony.fromColony(colony, labColor, normalized, distMapValue);
            assistColonies.add(assistColony);
        }
        
        invalidateDistanceMap();
    }
    
    /**
     * Handle user click to add or delete colony
     */
    public AssistResult handleUserClick(double clickX, double clickY, boolean isDeleteClick) {
        if (isDeleteClick) {
            return handleDeleteClick(clickX, clickY);
        } else {
            return handleAddClick(clickX, clickY);
        }
    }
    
    /**
     * Add colony at user click position with refinement
     */
    private AssistResult handleAddClick(double clickX, double clickY) {
        // Check if click is inside plate boundary
        if (plateRoi != null && !plateRoi.contains((int) clickX, (int) clickY)) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(), 0, 0, 0,
                "click_outside_plate", clickX, clickY, clickX, clickY,
                "Click is outside plate boundary"
            );
        }
        
        // Check if click is too close to existing colony
        Optional<AssistColony> nearbyColony = findNearbyColony(clickX, clickY, config.clickRadius());
        if (nearbyColony.isPresent() && !nearbyColony.get().userDeleted()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(), 0, 0, 0,
                "click_too_close", clickX, clickY, clickX, clickY,
                "Click too close to existing colony"
            );
        }
        
        // Refine click position to nearest blob peak
        Point refinedPosition = refineClickToBlob(clickX, clickY);
        double distMapValue = getDistanceMapValue(refinedPosition.x, refinedPosition.y);
        
        // Check if refined position meets minimum peak strength
        if (distMapValue < config.minDistanceMapValue()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(), 0, 0, 0,
                "weak_peak", clickX, clickY, refinedPosition.x, refinedPosition.y,
                "Refined position has weak blob peak signal"
            );
        }
        
        // Estimate colony diameter from local blob analysis
        double estimatedDiameter = estimateColonyDiameter(refinedPosition.x, refinedPosition.y);
        if (estimatedDiameter < config.minColonySize() || estimatedDiameter > config.maxColonySize()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(), 0, 0, 0,
                "invalid_size", clickX, clickY, refinedPosition.x, refinedPosition.y,
                String.format("Estimated diameter %.1fpx outside range [%.1f-%.1f]", 
                             estimatedDiameter, config.minColonySize(), config.maxColonySize())
            );
        }
        
        // Get color at refined position
        ColonyClassifier.LabColor labColor = getPositionLabColor(refinedPosition.x, refinedPosition.y);
        
        // Create new user colony
        AssistColony newColony = AssistColony.fromUserClick(
            clickX, clickY, refinedPosition.x, refinedPosition.y,
            estimatedDiameter, labColor, distMapValue, config.defaultClass()
        );
        
        assistColonies.add(newColony);
        
        return new AssistResult(
            List.copyOf(assistColonies), assistColonies.size(), 
            countUserAdded(), countUserDeleted(), countUserRelabeled(),
            "colony_added", clickX, clickY, refinedPosition.x, refinedPosition.y,
            String.format("Added colony at (%.1f, %.1f), diameter %.1fpx", 
                         refinedPosition.x, refinedPosition.y, estimatedDiameter)
        );
    }
    
    /**
     * Delete colony near click position
     */
    private AssistResult handleDeleteClick(double clickX, double clickY) {
        Optional<AssistColony> nearbyColony = findNearbyColony(clickX, clickY, config.clickRadius());
        
        if (nearbyColony.isEmpty()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(), 
                countUserAdded(), countUserDeleted(), countUserRelabeled(),
                "no_colony_found", clickX, clickY, clickX, clickY,
                "No colony found near click position"
            );
        }
        
        AssistColony toDelete = nearbyColony.get();
        if (toDelete.userDeleted()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(),
                countUserAdded(), countUserDeleted(), countUserRelabeled(),
                "already_deleted", clickX, clickY, toDelete.x(), toDelete.y(),
                "Colony already marked as deleted"
            );
        }
        
        // Mark colony as deleted (don't remove from list to preserve indices)
        AssistColony deletedColony = toDelete.asDeleted();
        assistColonies.set(assistColonies.indexOf(toDelete), deletedColony);
        
        return new AssistResult(
            List.copyOf(assistColonies), assistColonies.size(),
            countUserAdded(), countUserDeleted(), countUserRelabeled(),
            "colony_deleted", clickX, clickY, deletedColony.x(), deletedColony.y(),
            String.format("Deleted colony at (%.1f, %.1f)", deletedColony.x(), deletedColony.y())
        );
    }
    
    /**
     * Propagate classification from user-labeled colonies
     */
    public ClassificationResult propagateClassification(ColonyColor targetClass) {
        if (!config.enableMLPropagation()) {
            return new ClassificationResult(
                List.of(), 0, targetClass.toString(), 0.0, "disabled",
                "ML propagation is disabled in configuration"
            );
        }
        
        // Get user-labeled colonies for training
        List<AssistColony> labeledColonies = assistColonies.stream()
            .filter(c -> !c.userDeleted() && c.userRelabeled())
            .toList();
        
        if (labeledColonies.size() < 2) {
            return new ClassificationResult(
                List.of(), 0, targetClass.toString(), 0.0, "insufficient_data",
                "Need at least 2 user-labeled colonies for training"
            );
        }
        
        // Train logistic model for target class
        LogisticModel model = new LogisticModel(targetClass);
        model.train(labeledColonies);
        models.put(targetClass, model);
        
        // Apply model to unlabeled colonies
        List<AssistColony> reclassified = new ArrayList<>();
        int reclassifiedCount = 0;
        
        for (int i = 0; i < assistColonies.size(); i++) {
            AssistColony colony = assistColonies.get(i);
            
            // Skip deleted, user-added, or user-relabeled colonies
            if (colony.userDeleted() || colony.userAdded() || colony.userRelabeled()) {
                continue;
            }
            
            // Predict using trained model
            double probability = model.predict(colony);
            
            // Apply classification if confident enough
            if (probability > 0.7) {  // High confidence threshold
                AssistColony reclassifiedColony = colony.withClassification(targetClass, probability);
                assistColonies.set(i, reclassifiedColony);
                reclassified.add(reclassifiedColony);
                reclassifiedCount++;
            }
        }
        
        return new ClassificationResult(
            reclassified, reclassifiedCount, targetClass.toString(), 0.0,
            String.format("[deltaA, deltaB, size_mm] -> %s", targetClass),
            String.format("Reclassified %d colonies using %s model trained on %d examples",
                         reclassifiedCount, targetClass, labeledColonies.size())
        );
    }
    
    /**
     * Manually relabel colony at position
     */
    public AssistResult relabelColony(double clickX, double clickY, ColonyColor newClass) {
        Optional<AssistColony> nearbyColony = findNearbyColony(clickX, clickY, config.clickRadius());
        
        if (nearbyColony.isEmpty()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(),
                countUserAdded(), countUserDeleted(), countUserRelabeled(),
                "no_colony_found", clickX, clickY, clickX, clickY,
                "No colony found near click position"
            );
        }
        
        AssistColony colony = nearbyColony.get();
        if (colony.userDeleted()) {
            return new AssistResult(
                List.copyOf(assistColonies), assistColonies.size(),
                countUserAdded(), countUserDeleted(), countUserRelabeled(),
                "colony_deleted", clickX, clickY, colony.x(), colony.y(),
                "Cannot relabel deleted colony"
            );
        }
        
        // Update colony classification
        AssistColony relabeledColony = colony.withClassification(newClass, 0.9);  // High confidence for user labels
        assistColonies.set(assistColonies.indexOf(colony), relabeledColony);
        
        return new AssistResult(
            List.copyOf(assistColonies), assistColonies.size(),
            countUserAdded(), countUserDeleted(), countUserRelabeled(),
            "colony_relabeled", clickX, clickY, colony.x(), colony.y(),
            String.format("Relabeled colony to %s", newClass)
        );
    }
    
    /**
     * Create visualization overlay showing assist colonies
     */
    public Overlay createAssistOverlay() {
        Overlay overlay = new Overlay();
        
        for (AssistColony colony : assistColonies) {
            if (colony.userDeleted()) {
                continue;  // Skip deleted colonies
            }
            
            // Base circle size
            double radius = Math.max(4.0, colony.diameter() / 4.0);
            
            // Create circle ROI
            OvalRoi circle = new OvalRoi(
                colony.x() - radius, colony.y() - radius,
                radius * 2, radius * 2
            );
            
            // Color coding by classification and user interaction
            Color strokeColor = getColonyDisplayColor(colony);
            circle.setStrokeColor(strokeColor);
            
            // Stroke width indicates confidence and user interaction
            double strokeWidth = 2.0;
            if (colony.userAdded()) strokeWidth = 3.0;      // Thicker for user-added
            if (colony.userRelabeled()) strokeWidth = 2.5;   // Medium for user-relabeled
            
            circle.setStrokeWidth(strokeWidth);
            circle.setName(String.format("Colony_%d_%s_%.2f", 
                          colony.index(), colony.colorClass(), colony.classificationConf()));
            
            overlay.add(circle);
        }
        
        return overlay;
    }
    
    /**
     * Get current colony list (excluding deleted)
     */
    public List<Colony> getCurrentColonies() {
        return assistColonies.stream()
            .filter(c -> !c.userDeleted())
            .map(AssistColony::toColony)
            .toList();
    }
    
    /**
     * Get full assist colony list
     */
    public List<AssistColony> getAssistColonies() {
        return List.copyOf(assistColonies);
    }
    
    // Helper methods
    
    private Optional<AssistColony> findNearbyColony(double x, double y, double radius) {
        return assistColonies.stream()
            .filter(c -> distance(x, y, c.x(), c.y()) <= radius)
            .min(Comparator.comparingDouble(c -> distance(x, y, c.x(), c.y())));
    }
    
    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }
    
    private Point refineClickToBlob(double clickX, double clickY) {
        ensureDistanceMap();
        
        // Search in small radius around click for highest distance map value
        int bestX = (int) clickX;
        int bestY = (int) clickY;
        float bestValue = distanceMap.getf(bestX, bestY);
        
        int searchRadius = 15;  // pixels
        for (int dy = -searchRadius; dy <= searchRadius; dy++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                int x = (int) clickX + dx;
                int y = (int) clickY + dy;
                
                if (x >= 0 && x < distanceMap.getWidth() && 
                    y >= 0 && y < distanceMap.getHeight()) {
                    
                    float value = distanceMap.getf(x, y);
                    if (value > bestValue) {
                        bestValue = value;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
        }
        
        return new Point(bestX, bestY);
    }
    
    private double estimateColonyDiameter(double x, double y) {
        ensureDistanceMap();
        
        // Simple diameter estimation from distance map value
        float distValue = distanceMap.getf((int) x, (int) y);
        
        // Distance map value approximates radius, so diameter = 2 * value
        return Math.max(config.minColonySize(), Math.min(config.maxColonySize(), distValue * 2.0));
    }
    
    private void ensureDistanceMap() {
        if (!distanceMapValid || distanceMap == null) {
            computeDistanceMap();
            distanceMapValid = true;
        }
    }
    
    private void computeDistanceMap() {
        // Create binary mask of dark regions (potential colonies)
        ImageProcessor proc = image.getProcessor().duplicate();
        proc.setAutoThreshold("Triangle");
        proc.invert();  // Dark regions become bright
        
        // Compute distance transform
        distanceMap = (ByteProcessor) proc.convertToByte(false);
        // Note: ImageJ's distance transform would go here
        // For simplicity, using thresholded image as approximation
    }
    
    private void invalidateDistanceMap() {
        distanceMapValid = false;
    }
    
    private double getDistanceMapValue(double x, double y) {
        ensureDistanceMap();
        
        int ix = Math.max(0, Math.min(distanceMap.getWidth() - 1, (int) x));
        int iy = Math.max(0, Math.min(distanceMap.getHeight() - 1, (int) y));
        
        return distanceMap.getf(ix, iy) / 255.0;  // Normalize to 0-1
    }
    
    private ColonyClassifier.LabColor getColonyLabColor(Colony colony) {
        return getPositionLabColor(colony.x(), colony.y());
    }
    
    private ColonyClassifier.LabColor getPositionLabColor(double x, double y) {
        ImageProcessor proc = image.getProcessor();
        int pixel = proc.getPixel((int) x, (int) y);
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        return ColonyClassifier.rgbToLab(r, g, b);
    }
    
    private ColonyNormalizer.NormalizedColony normalizeColony(Colony colony, ColonyClassifier.LabColor labColor) {
        // Simplified normalization - would normally need plate background
        return new ColonyNormalizer.NormalizedColony(
            colony, 0.0, 0.0, 0.0, colony.diameter() / pxPerMM, -1
        );
    }
    
    private Color getColonyDisplayColor(AssistColony colony) {
        // Color by classification with modifications for user interactions
        Color baseColor = switch (colony.colorClass()) {
            case BLUE -> Color.BLUE;
            case WHITE -> Color.WHITE;
            case PINK -> Color.PINK;
            case YELLOW -> Color.YELLOW;
            case GREEN -> Color.GREEN;
            case OTHER -> Color.GRAY;
        };
        
        // Modify for user interactions
        if (colony.userAdded()) {
            return baseColor.brighter();  // Brighter for user-added
        } else if (colony.userRelabeled()) {
            return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 200);  // Semi-transparent
        }
        
        return baseColor;
    }
    
    private int countUserAdded() {
        return (int) assistColonies.stream().filter(AssistColony::userAdded).count();
    }
    
    private int countUserDeleted() {
        return (int) assistColonies.stream().filter(AssistColony::userDeleted).count();
    }
    
    private int countUserRelabeled() {
        return (int) assistColonies.stream().filter(AssistColony::userRelabeled).count();
    }
    
    /**
     * Simple point class for coordinates
     */
    private static record Point(double x, double y) {}
}