package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;

import java.util.List;

/**
 * Pure functional colony binning by size and class.
 * Supports both simple classification (xgal_pos/xgal_neg) and semi-quantitative grading (xgal_light/medium/dark).
 * Equivalent diameter (mm) → size bin index; join with class label into bin = class + "+" + size_bin.
 * Examples: "xgal_dark+large", "xgal_light+small", "xgal_neg+medium"
 */
public final class Binner {
    
    /**
     * Apply size binning to colonies with default bins (small/medium/large)
     */
    public static void apply(List<Colony> colonies, double pxPerMM) {
        apply(colonies, pxPerMM, new double[]{1.0, 2.5}); // Default: <1mm=small, 1-2.5mm=medium, >2.5mm=large
    }
    
    /**
     * Apply size binning with user-defined edges in millimeters
     * Example: edges=[0.2, 1.0, 2.0] → bins: ≤0.2=tiny, 0.2-1.0=small, 1.0-2.0=medium, >2.0=large
     */
    public static void applyMM(List<Colony> colonies, double pxPerMM, double[] edgesMM) {
        apply(colonies, pxPerMM, edgesMM);
    }
    
    /**
     * Apply size binning to colonies with custom size edges
     * @param colonies List of colonies to bin
     * @param pxPerMM Pixels per millimeter conversion factor
     * @param sizeEdgesMM Array of size bin edges in mm (e.g., [1.0, 2.5] creates 3 bins: <1, 1-2.5, >2.5)
     */
    public static void apply(List<Colony> colonies, double pxPerMM, double[] sizeEdgesMM) {
        
        for (Colony colony : colonies) {
            // Convert diameter to mm
            double diameterMM = colony.diameter() / pxPerMM;
            
            // Determine size bin index  
            int sizeBinIndex = getSizeBinIndex(diameterMM, sizeEdgesMM);
            String sizeBinLabel = getSizeBinLabel(sizeBinIndex, sizeEdgesMM.length + 1);
            
            // Get class label (from colony's current classification)
            String classLabel = colony.binCategory();
            if (classLabel == null || classLabel.isEmpty() || "unclassified".equals(classLabel)) {
                classLabel = "unknown";
            }
            
            // Create combined bin label: class + "+" + size_bin
            String combinedBin = classLabel + "+" + sizeBinLabel;
            
            // Note: Since Colony is immutable, we can't directly update it
            // In practice, would need to either:
            // 1. Return new Colony instances with updated binCategory
            // 2. Use a separate binning results structure
            // 3. Update the colony list with new instances
            
            // For now, this demonstrates the binning logic
            // Real implementation would update the colony's binCategory field
        }
    }
    
    /**
     * Determine which size bin a diameter falls into
     */
    private static int getSizeBinIndex(double diameterMM, double[] sizeEdgesMM) {
        for (int i = 0; i < sizeEdgesMM.length; i++) {
            if (diameterMM < sizeEdgesMM[i]) {
                return i;
            }
        }
        return sizeEdgesMM.length; // Largest bin
    }
    
    /**
     * Convert size bin index to string label
     */
    private static String getSizeBinLabel(int binIndex) {
        return switch (binIndex) {
            case 0 -> "tiny";     // For user-defined edges like [0.2, 1.0, 2.0]
            case 1 -> "small";
            case 2 -> "medium"; 
            case 3 -> "large";
            case 4 -> "xl";       // Extra large
            default -> "xxl";     // Extra extra large for more bins
        };
    }
    
    /**
     * Convert size bin index to string label with fallback for many bins
     */
    private static String getSizeBinLabel(int binIndex, int totalBins) {
        if (totalBins <= 4) {
            return getSizeBinLabel(binIndex);
        } else {
            // For many bins, use generic naming: bin0, bin1, bin2, etc.
            return "bin" + binIndex;
        }
    }
    
    /**
     * Create new colonies list with updated bin categories
     */
    public static List<Colony> applyAndReturn(List<Colony> colonies, double pxPerMM, double[] sizeEdgesMM) {
        return colonies.stream()
            .map(colony -> {
                double diameterMM = colony.diameter() / pxPerMM;
                int sizeBinIndex = getSizeBinIndex(diameterMM, sizeEdgesMM);
                String sizeBinLabel = getSizeBinLabel(sizeBinIndex, sizeEdgesMM.length + 1);
                
                String classLabel = colony.binCategory();
                if (classLabel == null || classLabel.isEmpty() || "unclassified".equals(classLabel)) {
                    classLabel = "unknown";
                }
                
                String combinedBin = classLabel + "+" + sizeBinLabel;
                
                // Create new colony with updated bin category
                return new Colony(
                    colony.index(), colony.x(), colony.y(),
                    colony.area(), colony.diameter(), diameterMM,
                    colony.circularity(), colony.solidity(), colony.meanIntensity(),
                    colony.colorClass(), colony.colorConfidence(),
                    colony.sizeClass(), combinedBin
                );
            })
            .toList();
    }
    
    /**
     * Get summary of binning results
     */
    public static java.util.Map<String, Integer> summary(List<Colony> colonies) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        
        for (Colony colony : colonies) {
            String bin = colony.binCategory();
            if (bin != null && !bin.isEmpty()) {
                counts.put(bin, counts.getOrDefault(bin, 0) + 1);
            }
        }
        
        return counts;
    }
    
    /**
     * Example usage and results
     */
    public static void demonstrateBinning() {
        System.out.println("=== Binner Example ===");
        System.out.println("Input: Colonies with classifications and equivalent diameters (d_eq_mm)");
        System.out.println();
        System.out.println("Default edges [1.0, 2.5]: ≤1.0mm=tiny, 1.0-2.5mm=small, >2.5mm=medium");
        System.out.println("User edges [0.2, 1.0, 2.0]: ≤0.2mm=tiny, 0.2-1.0mm=small, 1.0-2.0mm=medium, >2.0mm=large");
        System.out.println();
        System.out.println("Examples - Default binning:");
        System.out.println("  xgal_pos colony, 0.8mm → xgal_pos+tiny");
        System.out.println("  xgal_neg colony, 1.5mm → xgal_neg+small");
        System.out.println("  xgal_pos colony, 3.0mm → xgal_pos+medium");
        System.out.println();
        System.out.println("Examples - User-defined edges [0.2, 1.0, 2.0]:");
        System.out.println("  xgal_light colony, 0.15mm → xgal_light+tiny");
        System.out.println("  xgal_medium colony, 0.8mm → xgal_medium+small");
        System.out.println("  xgal_dark colony, 1.5mm → xgal_dark+medium");
        System.out.println("  xgal_neg colony, 2.5mm → xgal_neg+large");
        System.out.println("  uncertain colony, 0.5mm → uncertain+small");
    }
}