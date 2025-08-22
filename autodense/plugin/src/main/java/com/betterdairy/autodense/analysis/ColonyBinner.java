package com.betterdairy.autodense.analysis;

import java.util.List;

/**
 * Simple colony binning by size with combined labels.
 * Based on user's clean design pattern.
 */
public final class ColonyBinner {
    
    /**
     * Determine size bin from equivalent diameter in mm
     * @param dEqMM Equivalent diameter in millimeters
     * @param edges Sorted array of size bin edges in mm
     * @return Size bin name (tiny, small, medium, large, xlarge)
     */
    public static String sizeBin(double dEqMM, double[] edges) {
        // edges sorted ascending; 3 edges -> 4 bins
        String[] names = {"tiny", "small", "medium", "large", "xlarge"};
        int idx = 0;
        while (idx < edges.length && dEqMM > edges[idx]) {
            idx++;
        }
        return names[Math.min(idx, names.length - 1)];
    }

    /**
     * Apply size binning and create combined labels for all colonies
     * @param colonies List of mutable colonies to bin
     * @param pxPerMM Pixels per millimeter conversion factor
     * @param edgesMM Array of size bin edges in millimeters
     */
    public static void applyBins(List<MutableColony> colonies, double pxPerMM, double[] edgesMM) {
        for (MutableColony c : colonies) {
            // Convert diameter to mm
            double dMM = c.eqDiamPx / pxPerMM;
            
            // Determine size bin
            c.sizeBin = sizeBin(dMM, edgesMM);
            
            // Create color tag based on classification
            String colorTag = switch (c.xgalBinary) {
                case "pos" -> c.xgalGrade; // light/medium/dark
                case "neg" -> "neg";
                default    -> "uncertain";
            };
            
            // Create combined label: color + size
            c.label = colorTag + "+" + c.sizeBin;
        }
    }
    
    /**
     * Default size binning with standard edges
     */
    public static void applyBins(List<MutableColony> colonies, double pxPerMM) {
        // Default edges for common phone photography: [0.2, 1.0, 2.0] mm
        double[] defaultEdges = {0.2, 1.0, 2.0};
        applyBins(colonies, pxPerMM, defaultEdges);
    }
    
    /**
     * Get bin summary statistics
     */
    public static java.util.Map<String, Integer> getBinSummary(List<MutableColony> colonies) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        
        for (MutableColony c : colonies) {
            String binKey = c.label;
            counts.put(binKey, counts.getOrDefault(binKey, 0) + 1);
        }
        
        return counts;
    }
    
    /**
     * Export colony data to CSV with comprehensive columns
     */
    public static String exportToCSV(List<MutableColony> colonies, double pxPerMM) {
        StringBuilder csv = new StringBuilder();
        
        // Header row matching user's specification
        csv.append("colony_id,x_mm,y_mm,eq_diam_mm,")
           .append("L,a,b,L_bg,a_bg,b_bg,b_delta,dE_bg,snr_L,")
           .append("xgal_binary,xgal_grade,size_bin,label,confidence")
           .append("\n");
        
        // Data rows
        for (MutableColony c : colonies) {
            double xMm = c.x / pxPerMM;
            double yMm = c.y / pxPerMM;
            double diamMm = c.eqDiamPx / pxPerMM;
            
            csv.append(String.format("%d,%.3f,%.3f,%.3f,", c.id, xMm, yMm, diamMm))
               .append(String.format("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f,%.2f,%.2f,",
                    c.L, c.a, c.b, c.Lbg, c.abg, c.bbg, 
                    c.bDelta, c.dEbg, c.snrL))
               .append(String.format("%s,%s,%s,%s,%.3f",
                    c.xgalBinary, 
                    c.xgalGrade != null ? c.xgalGrade : "null",
                    c.sizeBin, c.label, c.confidence))
               .append("\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Create visual overlay with classification colors
     */
    public static ij.gui.Overlay createVisualOverlay(List<MutableColony> colonies, double pxPerMM, boolean showLegend) {
        return ColonyVisualizer.createClassificationOverlay(colonies, pxPerMM, showLegend);
    }
    
    /**
     * Apply visual overlay to image
     */
    public static void applyVisualOverlay(ij.ImagePlus image, List<MutableColony> colonies, double pxPerMM, boolean showLegend) {
        ColonyVisualizer.applyOverlay(image, colonies, pxPerMM, showLegend);
    }
    
    /**
     * Demonstrate binning with example data
     */
    public static void demonstrateBinning() {
        System.out.println("=== ColonyBinner Example ===");
        System.out.println("User-defined edges [0.2, 1.0, 2.0] mm:");
        System.out.println("  ≤0.2mm = tiny");
        System.out.println("  0.2-1.0mm = small");  
        System.out.println("  1.0-2.0mm = medium");
        System.out.println("  >2.0mm = large");
        System.out.println();
        System.out.println("Example combined labels:");
        System.out.println("  0.15mm X-gal dark → dark+tiny");
        System.out.println("  0.8mm X-gal light → light+small");
        System.out.println("  1.5mm X-gal medium → medium+medium");
        System.out.println("  2.5mm X-gal negative → neg+large");
        System.out.println("  0.5mm uncertain → uncertain+small");
        System.out.println();
        System.out.println("Visual overlay colors:");
        System.out.println("  ● Deep blue = dark X-gal");
        System.out.println("  ● Medium blue = medium X-gal");
        System.out.println("  ● Pale blue = light X-gal");
        System.out.println("  ● Orange = negative");
        System.out.println("  ● Gray = uncertain");
    }
}