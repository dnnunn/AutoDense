package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import com.betterdairy.autodense.model.Models.ColonySize;

import java.util.List;

/**
 * Extended models for Colony Identification Assist system.
 * Supports user click interactions and machine learning class propagation.
 * 
 * Architecture similar to BandAssist but adapted for colony analysis:
 * - User clicks to add/delete colonies
 * - Click refinement to nearest blob peak (distance-map ridge)
 * - Class propagation via logistic regression in (a*, b*, size) space
 */
public final class ColonyAssistModels {
    
    /**
     * Extended colony with assist-specific data
     */
    public static record AssistColony(
        // Original colony data
        int index,
        double x, double y,
        double area, double diameter, double diameterMm,
        double circularity, double solidity, double meanIntensity,
        ColonyColor colorClass, double colorConfidence,
        ColonySize sizeClass, String binCategory,
        
        // Lab color features for ML classification
        double labL, double labA, double labB,
        
        // Normalized features for cross-plate compatibility
        double deltaA, double deltaB, double deltaE,
        
        // Assist-specific metadata
        boolean userAdded,           // Added by user click
        boolean userDeleted,         // Deleted by user click  
        boolean userRelabeled,       // Class changed by user
        double distanceMapValue,     // Peak strength on distance map
        double classificationConf    // ML confidence for current class
    ) {
        /**
         * Create from original Colony with computed features
         */
        public static AssistColony fromColony(Colony original, 
                                            ColonyClassifier.LabColor labColor,
                                            ColonyNormalizer.NormalizedColony normalized,
                                            double distanceMapValue) {
            return new AssistColony(
                original.index(), original.x(), original.y(),
                original.area(), original.diameter(), normalized.diameterMm(),
                original.circularity(), original.solidity(), original.meanIntensity(),
                original.colorClass(), original.colorConfidence(),
                original.sizeClass(), original.binCategory(),
                labColor.L(), labColor.a(), labColor.b(),
                normalized.deltaA(), normalized.deltaB(), normalized.deltaE(),
                false, false, false,  // User interaction flags
                distanceMapValue, 1.0  // Distance map value, default confidence
            );
        }
        
        /**
         * Create user-added colony from click position
         */
        public static AssistColony fromUserClick(double clickX, double clickY, 
                                                double refinedX, double refinedY,
                                                double estimatedDiameter,
                                                ColonyClassifier.LabColor labColor,
                                                double distanceMapValue,
                                                ColonyColor initialClass) {
            // Estimate area from diameter
            double radius = estimatedDiameter / 2.0;
            double estimatedArea = Math.PI * radius * radius;
            
            return new AssistColony(
                -1, refinedX, refinedY,  // Negative index for user-added
                estimatedArea, estimatedDiameter, 0.0,  // Size measurements
                0.9, 0.8, 128,  // Default shape measurements
                initialClass, 0.5,  // Initial classification
                ColonySize.MEDIUM, "unclassified",  // Size and bin categories
                labColor.L(), labColor.a(), labColor.b(),  // Lab color
                0.0, 0.0, 0.0,  // Normalized deltas (computed later)
                true, false, false,  // User-added flag
                distanceMapValue, 0.5  // Distance map value, medium confidence
            );
        }
        
        /**
         * Update colony with new classification and confidence
         */
        public AssistColony withClassification(ColonyColor newClass, double confidence) {
            return new AssistColony(
                index, x, y, area, diameter, diameterMm,
                circularity, solidity, meanIntensity,
                newClass, colorConfidence,
                sizeClass, binCategory,
                labL, labA, labB, deltaA, deltaB, deltaE,
                userAdded, userDeleted, true,  // Mark as user-relabeled
                distanceMapValue, confidence
            );
        }
        
        /**
         * Mark colony as deleted
         */
        public AssistColony asDeleted() {
            return new AssistColony(
                index, x, y, area, diameter, diameterMm,
                circularity, solidity, meanIntensity,
                colorClass, colorConfidence, sizeClass, binCategory,
                labL, labA, labB, deltaA, deltaB, deltaE,
                userAdded, true, userRelabeled,  // Mark as deleted
                distanceMapValue, classificationConf
            );
        }
        
        /**
         * Feature vector for machine learning: [a*, b*, size_mm]
         */
        public double[] featureVector() {
            return new double[]{deltaA, deltaB, diameterMm};
        }
        
        /**
         * Convert back to standard Colony record
         */
        public Colony toColony() {
            return new Colony(
                index, x, y, area, diameter, diameterMm,
                circularity, solidity, meanIntensity,
                colorClass, colorConfidence, sizeClass, binCategory
            );
        }
    }
    
    /**
     * Result from colony assist interaction
     */
    public static record AssistResult(
        List<AssistColony> colonies,
        int totalColonies,
        int userAdded,
        int userDeleted,
        int userRelabeled,
        String lastAction,
        double clickX, double clickY,
        double refinedX, double refinedY,
        String message
    ) {}
    
    /**
     * Machine learning classification result
     */
    public static record ClassificationResult(
        List<AssistColony> reclassifiedColonies,
        int totalReclassified,
        String trainedClass,
        double modelAccuracy,
        String modelFeatures,
        String message
    ) {}
    
    /**
     * Configuration for colony assist behavior
     */
    public static record AssistConfig(
        double clickRadius,         // Search radius around click (pixels)
        double minDistanceMapValue, // Minimum peak strength to consider
        double minColonySize,       // Minimum colony diameter (pixels)
        double maxColonySize,       // Maximum colony diameter (pixels)
        ColonyColor defaultClass,   // Default class for new colonies
        boolean enableMLPropagation // Enable ML-based class propagation
    ) {
        public static AssistConfig defaultConfig() {
            return new AssistConfig(
                25.0,    // 25 pixel search radius
                0.3,     // Moderate peak strength required
                8.0,     // 8 pixel minimum diameter
                60.0,    // 60 pixel maximum diameter
                ColonyColor.OTHER, // Default classification
                true     // Enable ML propagation
            );
        }
    }
    
    /**
     * Simple logistic regression model for colony classification
     */
    public static class LogisticModel {
        private double[] weights;
        private double bias;
        private ColonyColor targetClass;
        private int trainingSize;
        
        public LogisticModel(ColonyColor targetClass) {
            this.targetClass = targetClass;
            this.weights = new double[3];  // [a*, b*, size_mm]
            this.bias = 0.0;
            this.trainingSize = 0;
        }
        
        /**
         * Train model on labeled colonies using simple gradient descent
         */
        public void train(List<AssistColony> labeledColonies) {
            // Separate positive and negative examples
            List<AssistColony> positive = labeledColonies.stream()
                .filter(c -> targetClass == c.colorClass())
                .toList();
            List<AssistColony> negative = labeledColonies.stream()
                .filter(c -> targetClass != c.colorClass())
                .toList();
            
            if (positive.isEmpty() || negative.isEmpty()) {
                // Need both positive and negative examples
                this.trainingSize = 0;
                return;
            }
            
            // Simple feature scaling and weight initialization
            double[] meanFeatures = computeMeanFeatures(labeledColonies);
            double[] stdFeatures = computeStdFeatures(labeledColonies, meanFeatures);
            
            // Initialize weights based on feature differences
            double[] posMean = computeMeanFeatures(positive);
            double[] negMean = computeMeanFeatures(negative);
            
            for (int i = 0; i < 3; i++) {
                weights[i] = (posMean[i] - negMean[i]) / (stdFeatures[i] + 1e-6);
            }
            
            this.trainingSize = labeledColonies.size();
        }
        
        /**
         * Predict probability that colony belongs to target class
         */
        public double predict(AssistColony colony) {
            if (trainingSize < 2) return 0.5;  // No training data
            
            double[] features = colony.featureVector();
            double logit = bias;
            for (int i = 0; i < weights.length && i < features.length; i++) {
                logit += weights[i] * features[i];
            }
            
            // Sigmoid activation
            return 1.0 / (1.0 + Math.exp(-logit));
        }
        
        /**
         * Get model info
         */
        public String getModelInfo() {
            return String.format("LogisticModel[%s]: weights=[%.3f, %.3f, %.3f], bias=%.3f, n=%d",
                targetClass, weights[0], weights[1], weights[2], bias, trainingSize);
        }
        
        private double[] computeMeanFeatures(List<AssistColony> colonies) {
            double[] mean = new double[3];
            for (AssistColony colony : colonies) {
                double[] features = colony.featureVector();
                for (int i = 0; i < 3; i++) {
                    mean[i] += features[i];
                }
            }
            for (int i = 0; i < 3; i++) {
                mean[i] /= colonies.size();
            }
            return mean;
        }
        
        private double[] computeStdFeatures(List<AssistColony> colonies, double[] mean) {
            double[] variance = new double[3];
            for (AssistColony colony : colonies) {
                double[] features = colony.featureVector();
                for (int i = 0; i < 3; i++) {
                    double diff = features[i] - mean[i];
                    variance[i] += diff * diff;
                }
            }
            double[] std = new double[3];
            for (int i = 0; i < 3; i++) {
                std[i] = Math.sqrt(variance[i] / Math.max(1, colonies.size() - 1));
            }
            return std;
        }
    }
}