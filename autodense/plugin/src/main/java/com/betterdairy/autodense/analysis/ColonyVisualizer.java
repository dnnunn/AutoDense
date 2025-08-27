package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import ij.gui.TextRoi;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Use OverlayRenderer.createClassificationOverlay() instead.
 * 
 * Visual overlay system for colony classification results.
 * Uses intuitive color mapping: deep blue = dark X-gal, orange = negative, etc.
 * 
 * CONSOLIDATION NOTE: OverlayRenderer is now the single source of truth
 * for all overlay rendering (gels and colonies) with consistent styling.
 */
@Deprecated
public final class ColonyVisualizer {
    
    // Color mapping for X-gal classification
    private static final Color DARK_BLUE = new Color(0, 50, 150);      // dark X-gal
    private static final Color MEDIUM_BLUE = new Color(50, 100, 200);  // medium X-gal  
    private static final Color LIGHT_BLUE = new Color(100, 150, 255);  // light X-gal
    private static final Color ORANGE = new Color(255, 140, 0);        // negative
    private static final Color GRAY = new Color(128, 128, 128);        // uncertain
    private static final Color WHITE = new Color(255, 255, 255);       // unclassified
    
    /**
     * Create overlay with colony dots colored by X-gal classification
     * 
     * @param colonies List of classified colonies
     * @param pxPerMM Pixels per millimeter for size scaling
     * @param showLabels Whether to show a legend
     * @return Overlay with colored colony dots
     * @deprecated Use OverlayRenderer.createClassificationOverlay() instead
     */
    @Deprecated
    public static Overlay createClassificationOverlay(List<MutableColony> colonies, double pxPerMM, boolean showLabels) {
        // RUNTIME DEPRECATION WARNING
        System.err.println("WARNING: ColonyVisualizer.createClassificationOverlay() is deprecated. " +
                         "Migrate to OverlayRenderer when colony methods are available. " +
                         "This method will be removed in the next release.");
        
        // FALLBACK: Use current implementation until OverlayRenderer supports colonies
        Overlay overlay = new Overlay();
        
        // Draw colony dots
        for (MutableColony colony : colonies) {
            Color dotColor = getClassificationColor(colony);
            double radiusPx = (colony.eqDiamPx / 2.0) * 0.8; // Slightly smaller than actual colony
            
            // Create circular ROI
            OvalRoi dot = new OvalRoi(
                colony.x - radiusPx, 
                colony.y - radiusPx, 
                radiusPx * 2, 
                radiusPx * 2
            );
            
            dot.setStrokeColor(dotColor);
            dot.setStrokeWidth(Math.max(1, (int)(radiusPx * 0.1))); // Stroke proportional to size
            dot.setFillColor(null); // Hollow circles for better visibility
            
            overlay.add(dot);
        }
        
        // Add legend if requested
        if (showLabels) {
            addLegend(overlay, colonies);
        }
        
        return overlay;
    }
    
    /**
     * Get color for colony based on X-gal classification
     */
    private static Color getClassificationColor(MutableColony colony) {
        return switch (colony.xgalBinary) {
            case "pos" -> switch (colony.xgalGrade != null ? colony.xgalGrade : "light") {
                case "dark" -> DARK_BLUE;
                case "medium" -> MEDIUM_BLUE;
                case "light" -> LIGHT_BLUE;
                default -> LIGHT_BLUE;
            };
            case "neg" -> ORANGE;
            case "uncertain" -> GRAY;
            default -> WHITE;
        };
    }
    
    /**
     * Add legend showing color mapping
     */
    private static void addLegend(Overlay overlay, List<MutableColony> colonies) {
        // Count colonies by classification to show relevant legend entries only
        Map<String, Integer> counts = getBinCounts(colonies);
        
        int legendX = 20;
        int legendY = 30;
        int lineHeight = 25;
        int currentY = legendY;
        
        // Legend title
        TextRoi title = new TextRoi(legendX, currentY, "X-gal Classification");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setFillColor(Color.WHITE);
        title.setStrokeColor(Color.BLACK);
        overlay.add(title);
        currentY += lineHeight + 5;
        
        // Legend entries (only show classifications that are present)
        if (counts.getOrDefault("dark", 0) > 0) {
            addLegendEntry(overlay, legendX, currentY, DARK_BLUE, 
                String.format("● Dark blue (%d)", counts.get("dark")));
            currentY += lineHeight;
        }
        
        if (counts.getOrDefault("medium", 0) > 0) {
            addLegendEntry(overlay, legendX, currentY, MEDIUM_BLUE, 
                String.format("● Medium blue (%d)", counts.get("medium")));
            currentY += lineHeight;
        }
        
        if (counts.getOrDefault("light", 0) > 0) {
            addLegendEntry(overlay, legendX, currentY, LIGHT_BLUE, 
                String.format("● Light blue (%d)", counts.get("light")));
            currentY += lineHeight;
        }
        
        if (counts.getOrDefault("neg", 0) > 0) {
            addLegendEntry(overlay, legendX, currentY, ORANGE, 
                String.format("● Negative (%d)", counts.get("neg")));
            currentY += lineHeight;
        }
        
        if (counts.getOrDefault("uncertain", 0) > 0) {
            addLegendEntry(overlay, legendX, currentY, GRAY, 
                String.format("● Uncertain (%d)", counts.get("uncertain")));
            currentY += lineHeight;
        }
    }
    
    /**
     * Add single legend entry with colored dot and text
     */
    private static void addLegendEntry(Overlay overlay, int x, int y, Color color, String text) {
        // Colored dot
        OvalRoi dot = new OvalRoi(x, y - 6, 12, 12);
        dot.setStrokeColor(color);
        dot.setStrokeWidth(3);
        overlay.add(dot);
        
        // Text label
        TextRoi label = new TextRoi(x + 20, y, text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 14));
        label.setFillColor(Color.WHITE);
        label.setStrokeColor(Color.BLACK);
        overlay.add(label);
    }
    
    /**
     * Count colonies by classification type
     */
    private static Map<String, Integer> getBinCounts(List<MutableColony> colonies) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        
        for (MutableColony colony : colonies) {
            String key = switch (colony.xgalBinary) {
                case "pos" -> colony.xgalGrade != null ? colony.xgalGrade : "light";
                case "neg" -> "neg";
                case "uncertain" -> "uncertain";
                default -> "unclassified";
            };
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        
        return counts;
    }
    
    /**
     * Create overlay with size-proportional dots (alternative visualization)
     * 
     * @param colonies List of classified colonies
     * @param pxPerMM Pixels per millimeter
     * @param minRadius Minimum dot radius in pixels
     * @param maxRadius Maximum dot radius in pixels
     * @return Overlay with size-scaled dots
     * @deprecated Migrate to OverlayRenderer when available
     */
    @Deprecated
    public static Overlay createSizeProportionalOverlay(List<MutableColony> colonies, double pxPerMM, 
                                                        double minRadius, double maxRadius) {
        // RUNTIME DEPRECATION WARNING
        System.err.println("WARNING: ColonyVisualizer.createSizeProportionalOverlay() is deprecated. " +
                         "Migrate to OverlayRenderer when colony methods are available.");
        
        // FALLBACK: Use current implementation
        Overlay overlay = new Overlay();
        
        // Find size range for scaling
        double minDiam = colonies.stream().mapToDouble(c -> c.eqDiamPx).min().orElse(1.0);
        double maxDiam = colonies.stream().mapToDouble(c -> c.eqDiamPx).max().orElse(10.0);
        double diamRange = maxDiam - minDiam;
        
        for (MutableColony colony : colonies) {
            Color dotColor = getClassificationColor(colony);
            
            // Scale radius based on colony size
            double scaleFactor = diamRange > 0 ? (colony.eqDiamPx - minDiam) / diamRange : 0.5;
            double radiusPx = minRadius + scaleFactor * (maxRadius - minRadius);
            
            OvalRoi dot = new OvalRoi(
                colony.x - radiusPx, 
                colony.y - radiusPx, 
                radiusPx * 2, 
                radiusPx * 2
            );
            
            dot.setStrokeColor(dotColor);
            dot.setStrokeWidth(Math.max(1, (int)(radiusPx * 0.15)));
            dot.setFillColor(null);
            
            overlay.add(dot);
        }
        
        return overlay;
    }
    
    /**
     * Apply overlay to image
     * @deprecated Migrate to OverlayRenderer when available
     */
    @Deprecated
    public static void applyOverlay(ImagePlus image, List<MutableColony> colonies, double pxPerMM, boolean showLegend) {
        // RUNTIME DEPRECATION WARNING
        System.err.println("WARNING: ColonyVisualizer.applyOverlay() is deprecated. " +
                         "Migrate to OverlayRenderer when colony methods are available.");
        
        // FALLBACK: Use current implementation
        Overlay overlay = createClassificationOverlay(colonies, pxPerMM, showLegend);
        image.setOverlay(overlay);
        image.updateAndDraw();
    }
    
    /**
     * Demonstrate visualization with example output
     */
    public static void demonstrateVisualization() {
        System.out.println("=== Colony Visualization ===");
        System.out.println("Color mapping for X-gal classification:");
        System.out.println("  ● Deep blue = dark X-gal positive");
        System.out.println("  ● Medium blue = medium X-gal positive"); 
        System.out.println("  ● Pale blue = light X-gal positive");
        System.out.println("  ● Orange = negative");
        System.out.println("  ● Gray = uncertain");
        System.out.println();
        System.out.println("Features:");
        System.out.println("  - Dot radius proportional to colony diameter");
        System.out.println("  - Hollow circles for better colony visibility");
        System.out.println("  - Optional legend with counts per classification");
        System.out.println("  - Stroke width scales with colony size");
    }
}