package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure functional normalization for consistent colony analysis across plates.
 * 
 * Color: Uses per-plate background Lab to compute Δa*, Δb*, ΔE for portability
 * Size: Always reports in mm using plate calibration
 * Density: Reports colonies per cm² from plate area
 * Quadrants: Optional plate sectors for plating QC
 */
public final class ColonyNormalizer {
    
    /**
     * Normalized colony with portable measurements
     */
    public static record NormalizedColony(
        Colony original,
        double deltaA,      // Δa* = a_colony - a_background  
        double deltaB,      // Δb* = b_colony - b_background
        double deltaE,      // ΔE from background
        double diameterMm,  // Size in mm (calibrated)
        int quadrant        // Quadrant 0-3 (optional, -1 if disabled)
    ) {}
    
    /**
     * Plate-level normalization statistics
     */
    public static record PlateStats(
        ColonyClassifier.LabColor backgroundLab,  // Median plate background
        double plateRadiusMm,                     // Plate radius in mm
        double plateAreaCm2,                      // Plate area in cm²
        double colonyDensityPerCm2,               // Colonies per cm²
        Map<Integer, Integer> quadrantCounts      // Counts per quadrant (0-3)
    ) {}
    
    /**
     * Complete normalization result
     */
    public static record NormalizationResult(
        List<NormalizedColony> colonies,
        PlateStats plateStats
    ) {}
    
    /**
     * Normalize colonies for cross-plate comparison
     * 
     * @param image Source plate image
     * @param colonies Detected colonies
     * @param plateRoi Plate boundary ROI
     * @param pxPerMM Calibration factor
     * @param enableQuadrants Whether to compute quadrant statistics
     * @return Normalized colonies with plate statistics
     */
    public static NormalizationResult normalize(ImagePlus image, List<Colony> colonies, 
                                               OvalRoi plateRoi, double pxPerMM, 
                                               boolean enableQuadrants) {
        
        // Step 1: Calculate plate background Lab color
        ColonyClassifier.LabColor backgroundLab = calculatePlateBackgroundLab(image, plateRoi);
        
        // Step 2: Calculate plate geometry
        Rectangle bounds = plateRoi.getBounds();
        double plateRadiusPx = Math.max(bounds.width, bounds.height) / 2.0;
        double plateRadiusMm = plateRadiusPx / pxPerMM;
        double plateAreaCm2 = Math.PI * Math.pow(plateRadiusMm / 10.0, 2); // mm² to cm²
        
        // Step 3: Calculate colony density
        double colonyDensityPerCm2 = colonies.size() / plateAreaCm2;
        
        // Step 4: Normalize each colony
        List<NormalizedColony> normalizedColonies = new ArrayList<>();
        Map<Integer, Integer> quadrantCounts = new HashMap<>();
        
        // Initialize quadrant counts
        if (enableQuadrants) {
            for (int i = 0; i < 4; i++) {
                quadrantCounts.put(i, 0);
            }
        }
        
        double plateCenterX = plateRoi.getBounds().x + plateRoi.getBounds().width / 2.0;
        double plateCenterY = plateRoi.getBounds().y + plateRoi.getBounds().height / 2.0;
        
        for (Colony colony : colonies) {
            // Color normalization: Calculate deltas from plate background
            ColonyClassifier.LabColor colonyLab = getColonyLabColor(image, colony);
            double deltaA = colonyLab.a() - backgroundLab.a();
            double deltaB = colonyLab.b() - backgroundLab.b();
            double deltaE = calculateDeltaE76(colonyLab, backgroundLab);
            
            // Size normalization: Convert to mm
            double diameterMm = colony.diameter() / pxPerMM;
            
            // Quadrant assignment (optional)
            int quadrant = -1;
            if (enableQuadrants) {
                quadrant = getQuadrant(colony.x(), colony.y(), plateCenterX, plateCenterY);
                quadrantCounts.put(quadrant, quadrantCounts.get(quadrant) + 1);
            }
            
            normalizedColonies.add(new NormalizedColony(
                colony, deltaA, deltaB, deltaE, diameterMm, quadrant));
        }
        
        // Create plate statistics
        PlateStats plateStats = new PlateStats(
            backgroundLab, plateRadiusMm, plateAreaCm2, 
            colonyDensityPerCm2, quadrantCounts);
        
        return new NormalizationResult(normalizedColonies, plateStats);
    }
    
    /**
     * Calculate median background Lab color inside plate mask
     */
    private static ColonyClassifier.LabColor calculatePlateBackgroundLab(ImagePlus image, OvalRoi plateRoi) {
        ImageProcessor proc = image.getProcessor();
        proc.setRoi(plateRoi);
        
        List<Double> lValues = new ArrayList<>();
        List<Double> aValues = new ArrayList<>(); 
        List<Double> bValues = new ArrayList<>();
        
        Rectangle bounds = plateRoi.getBounds();
        
        // Sample points inside plate boundary (every 10th pixel for efficiency)
        for (int y = bounds.y; y < bounds.y + bounds.height; y += 10) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x += 10) {
                if (plateRoi.contains(x, y)) {
                    // Skip colony regions by sampling background areas only
                    if (isBackgroundPixel(proc, x, y)) {
                        int pixel = proc.getPixel(x, y);
                        int r = (pixel >> 16) & 0xFF;
                        int g = (pixel >> 8) & 0xFF;
                        int b = pixel & 0xFF;
                        ColonyClassifier.LabColor lab = ColonyClassifier.rgbToLab(r, g, b);
                        lValues.add(lab.L());
                        aValues.add(lab.a());
                        bValues.add(lab.b());
                    }
                }
            }
        }
        
        // Calculate median values
        double medianL = calculateMedian(lValues);
        double medianA = calculateMedian(aValues);
        double medianB = calculateMedian(bValues);
        
        return new ColonyClassifier.LabColor(medianL, medianA, medianB);
    }
    
    /**
     * Check if pixel represents background (not colony)
     * Simple heuristic: avoid very bright or very dark pixels
     */
    private static boolean isBackgroundPixel(ImageProcessor proc, int x, int y) {
        int pixel = proc.getPixel(x, y);
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        
        int brightness = (r + g + b) / 3;
        return brightness > 60 && brightness < 200; // Avoid very dark/bright pixels
    }
    
    /**
     * Get Lab color for a specific colony
     */
    private static ColonyClassifier.LabColor getColonyLabColor(ImagePlus image, Colony colony) {
        ImageProcessor proc = image.getProcessor();
        
        // Sample from colony center (simple approach - could use full sampling)
        int pixel = proc.getPixel((int) colony.x(), (int) colony.y());
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        return ColonyClassifier.rgbToLab(r, g, b);
    }
    
    /**
     * Calculate Delta E76 color difference
     */
    private static double calculateDeltaE76(ColonyClassifier.LabColor color1, ColonyClassifier.LabColor color2) {
        double dL = color1.L() - color2.L();
        double da = color1.a() - color2.a();
        double db = color1.b() - color2.b();
        
        return Math.sqrt(dL * dL + da * da + db * db);
    }
    
    /**
     * Determine quadrant (0=top-right, 1=top-left, 2=bottom-left, 3=bottom-right)
     */
    private static int getQuadrant(double x, double y, double centerX, double centerY) {
        boolean right = x >= centerX;
        boolean top = y <= centerY;
        
        if (right && top) return 0;      // Top-right
        if (!right && top) return 1;     // Top-left  
        if (!right && !top) return 2;    // Bottom-left
        return 3;                        // Bottom-right
    }
    
    /**
     * Calculate median of double list
     */
    private static double calculateMedian(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        
        values.sort(Double::compareTo);
        int size = values.size();
        
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            return values.get(size / 2);
        }
    }
    
    /**
     * Create portable classification thresholds using normalized color data
     */
    public static Map<String, Double> createPortableThresholds(List<NormalizedColony> colonies) {
        Map<String, Double> thresholds = new HashMap<>();
        
        // Calculate statistics from normalized data
        List<Double> deltaBValues = colonies.stream().mapToDouble(c -> c.deltaB).boxed().toList();
        List<Double> deltaEValues = colonies.stream().mapToDouble(c -> c.deltaE).boxed().toList();
        
        if (!deltaBValues.isEmpty()) {
            double medianDeltaB = calculateMedian(new ArrayList<>(deltaBValues));
            double medianDeltaE = calculateMedian(new ArrayList<>(deltaEValues));
            
            // Adaptive thresholds based on plate-specific data
            thresholds.put("deltaB_xgal_threshold", medianDeltaB - 6.0);
            thresholds.put("deltaE_xgal_threshold", Math.max(8.0, medianDeltaE * 0.8));
            thresholds.put("background_deltaB", medianDeltaB);
            thresholds.put("background_deltaE", medianDeltaE);
        }
        
        return thresholds;
    }
    
    /**
     * Generate normalization summary report
     */
    public static String generateSummaryReport(NormalizationResult result) {
        StringBuilder report = new StringBuilder();
        PlateStats stats = result.plateStats;
        
        report.append("=== Colony Normalization Summary ===\n");
        report.append(String.format("Plate radius: %.1f mm\n", stats.plateRadiusMm));
        report.append(String.format("Plate area: %.1f cm²\n", stats.plateAreaCm2));
        report.append(String.format("Colony density: %.1f colonies/cm²\n", stats.colonyDensityPerCm2));
        
        report.append(String.format("\nBackground Lab: L*=%.1f, a*=%.1f, b*=%.1f\n", 
                     stats.backgroundLab.L(), stats.backgroundLab.a(), stats.backgroundLab.b()));
        
        if (!stats.quadrantCounts.isEmpty()) {
            report.append("\nQuadrant distribution:\n");
            for (int i = 0; i < 4; i++) {
                String quadName = switch (i) {
                    case 0 -> "Top-right";
                    case 1 -> "Top-left";
                    case 2 -> "Bottom-left";
                    case 3 -> "Bottom-right";
                    default -> "Unknown";
                };
                report.append(String.format("  %s: %d colonies\n", quadName, stats.quadrantCounts.get(i)));
            }
        }
        
        // Size distribution in mm
        List<Double> sizes = result.colonies.stream().mapToDouble(c -> c.diameterMm).boxed().toList();
        if (!sizes.isEmpty()) {
            double minSize = sizes.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double maxSize = sizes.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double avgSize = sizes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            report.append(String.format("\nSize distribution (mm): min=%.2f, max=%.2f, avg=%.2f\n", 
                         minSize, maxSize, avgSize));
        }
        
        return report.toString();
    }
    
    /**
     * Example usage demonstration
     */
    public static void demonstrateNormalization() {
        System.out.println("=== Colony Normalization Demo ===");
        System.out.println("Features:");
        System.out.println("  • Color: Per-plate background Lab → portable Δa*, Δb*, ΔE");
        System.out.println("  • Size: Always in mm using plate calibration");
        System.out.println("  • Density: Colonies per cm² from plate area");
        System.out.println("  • Quadrants: 4 sectors for plating QC");
        System.out.println();
        
        System.out.println("Example normalized colony:");
        System.out.println("  Original: (x=120, y=150, diameter=25px)");
        System.out.println("  Normalized: (Δa*=-2.1, Δb*=-8.5, ΔE=12.3, size=2.5mm, Q1)");
        System.out.println("  → Portable across plates with different lighting/cameras");
        System.out.println();
        
        System.out.println("Plate statistics example:");
        System.out.println("  Radius: 45.2mm, Area: 64.1cm², Density: 1.2 colonies/cm²");
        System.out.println("  Background: L*=78.3, a*=1.2, b*=-0.8");
        System.out.println("  Quadrants: Q0=12, Q1=15, Q2=13, Q3=11 (even distribution)");
    }
}