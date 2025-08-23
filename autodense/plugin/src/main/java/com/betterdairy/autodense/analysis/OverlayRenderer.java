package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.analysis.AssistModels.AssistBand;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.OvalRoi;
import ij.gui.TextRoi;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * UNIFIED OVERLAY RENDERING SYSTEM for AutoDense laboratory image analysis.
 * 
 * SINGLE SOURCE OF TRUTH for all overlay visualization (gels and colonies).
 * Provides consistent styling and prevents rendering duplication.
 * 
 * STYLES:
 * - Gel lanes: Light green outlines (rgba 0,255,0,100)
 * - Gel bands: Cyan rectangles with yellow labels
 * - Assisted bands: Color-coded by confidence (green/orange/red)
 * - Colonies: Color-coded by classification with consistent palette
 * - Overlays: Consistent fonts, stroke widths, transparency
 */
public final class OverlayRenderer {
    private OverlayRenderer(){}

    /** 
     * Build an overlay with lane boxes and band boxes+labels from existing lanes.
     */
    public static Overlay fromLanes(List<Lane> lanes, List<List<AssistBand>> allBands) {
        Overlay ov = new Overlay();
        
        // Render lanes (light green outline)
        for (int i = 0; i < lanes.size(); i++) {
            Lane ln = lanes.get(i);
            Roi laneR = new Roi(ln.xStart(), 0, ln.xEnd() - ln.xStart() + 1, 400); // Use reasonable height
            laneR.setStrokeColor(new Color(0, 255, 0, 100));
            laneR.setStrokeWidth(1.0);
            laneR.setName("Lane " + (i + 1));
            ov.add(laneR);
        }

        // Render bands (cyan boxes with labels)
        if (allBands != null) {
            for (int laneIdx = 0; laneIdx < Math.min(lanes.size(), allBands.size()); laneIdx++) {
                List<AssistBand> bands = allBands.get(laneIdx);
                int bandIndex = 1;
                
                for (AssistBand b : bands) {
                    // Band rectangle
                    Roi r = new Roi(b.xStart, b.yStart, b.widthPx(), b.heightPx());
                    r.setStrokeColor(new Color(0, 255, 255));
                    r.setStrokeWidth(1.5);
                    r.setName("L" + (laneIdx + 1) + "B" + bandIndex);
                    ov.add(r);

                    // Band label
                    String label = "L" + (laneIdx + 1) + " B" + bandIndex;
                    TextRoi tr = new TextRoi(b.xStart + 2, b.yStart + 12, label, new Font("Arial", Font.PLAIN, 10));
                    tr.setStrokeColor(Color.YELLOW);
                    tr.setFillColor(new Color(0, 0, 0, 120));
                    ov.add(tr);
                    
                    bandIndex++;
                }
            }
        }
        
        return ov;
    }

    /** 
     * Add a set of bands with a given color and label prefix to an existing overlay.
     */
    public static void addBands(Overlay ov, List<AssistBand> bands, Color color, String labelPrefix) {
        int bandNumber = 1;
        for (AssistBand b : bands) {
            // Band rectangle
            Roi r = new Roi(b.xStart, b.yStart, b.widthPx(), b.heightPx());
            r.setStrokeColor(color);
            r.setStrokeWidth(1.8);
            r.setName((labelPrefix != null ? labelPrefix + "_" : "") + "Band_" + bandNumber);
            ov.add(r);
            
            // Band label
            String label = (labelPrefix != null ? labelPrefix + " " : "") + "#" + bandNumber;
            TextRoi tr = new TextRoi(b.xStart + 2, b.yStart + 12, label, new Font("Arial", Font.PLAIN, 10));
            tr.setStrokeColor(Color.WHITE);
            tr.setFillColor(new Color(0, 0, 0, 140));
            ov.add(tr);
            
            bandNumber++;
        }
    }

    /**
     * Create an overlay specifically for assisted bands with confidence indicators.
     */
    public static Overlay createAssistedBandOverlay(List<AssistBand> assistedBands) {
        Overlay ov = new Overlay();
        
        for (int i = 0; i < assistedBands.size(); i++) {
            AssistBand b = assistedBands.get(i);
            
            // Color based on confidence: high = green, medium = orange, low = red
            Color bandColor;
            if (b.confidence > 0.7) {
                bandColor = Color.GREEN;
            } else if (b.confidence > 0.4) {
                bandColor = Color.ORANGE;
            } else {
                bandColor = Color.RED;
            }
            
            // Band rectangle
            Roi r = new Roi(b.xStart, b.yStart, b.widthPx(), b.heightPx());
            r.setStrokeColor(bandColor);
            r.setStrokeWidth(2.0);
            r.setName("Assisted_Band_" + (i + 1));
            ov.add(r);
            
            // Band label with confidence indicator
            String label = String.format("A%d (%.0f%%)", i + 1, b.confidence * 100);
            TextRoi tr = new TextRoi(b.xStart + 2, b.yStart - 15, label, new Font("Arial", Font.BOLD, 11));
            tr.setStrokeColor(Color.MAGENTA);
            tr.setFillColor(new Color(255, 255, 255, 200));
            ov.add(tr);
        }
        
        return ov;
    }
    
    // COLONY RENDERING METHODS
    
    /**
     * Standard colony classification colors (consistent palette)
     */
    private static final Map<String, Color> COLONY_COLORS = Map.of(
        "xgal_pos", new Color(0, 100, 255),      // Blue (X-gal positive)
        "xgal_neg", new Color(255, 140, 0),      // Orange (X-gal negative)  
        "uncertain", Color.GRAY,                 // Gray (uncertain)
        "dark_blue", new Color(0, 50, 150),      // Dark blue (high X-gal)
        "medium_blue", new Color(50, 100, 200),  // Medium blue (medium X-gal)
        "light_blue", new Color(100, 150, 255),  // Light blue (light X-gal)
        "white", Color.WHITE,                    // White (unclassified)
        "cluster_0", Color.MAGENTA,              // Cluster analysis
        "cluster_1", Color.CYAN,
        "cluster_2", Color.YELLOW
    );
    
    /**
     * Create colony overlay with standardized colors and styling
     */
    public static Overlay createColonyOverlay(List<Colony> colonies, double pxPerMM) {
        return createColonyOverlay(colonies, pxPerMM, false, false);
    }
    
    /**
     * Create colony overlay with full display options
     * @param colonies List of colonies to visualize
     * @param pxPerMM Pixels per millimeter for scaling
     * @param showSizeLabels Show colony size in mm
     * @param showSummary Show count summary
     */
    public static Overlay createColonyOverlay(List<Colony> colonies, double pxPerMM, 
                                             boolean showSizeLabels, boolean showSummary) {
        Overlay overlay = new Overlay();
        
        for (int i = 0; i < colonies.size(); i++) {
            Colony colony = colonies.get(i);
            
            // Determine color from classification or use default
            Color colonyColor = getColonyColor(colony);
            double radiusPx = colony.diameter() / 2.0;
            
            // Colony circle
            OvalRoi circle = new OvalRoi(
                colony.x() - radiusPx, 
                colony.y() - radiusPx, 
                radiusPx * 2, 
                radiusPx * 2
            );
            circle.setStrokeColor(colonyColor);
            circle.setStrokeWidth(2.0);
            circle.setName("Colony_" + (i + 1));
            overlay.add(circle);
            
            // Optional size label
            if (showSizeLabels) {
                double sizeMm = colony.diameterMm();
                String sizeText = String.format("%.1fmm", sizeMm);
                TextRoi sizeLabel = new TextRoi(
                    colony.x() + radiusPx + 5, 
                    colony.y() - 5, 
                    sizeText, 
                    new Font("Arial", Font.PLAIN, 9)
                );
                sizeLabel.setStrokeColor(colonyColor);
                sizeLabel.setFillColor(new Color(255, 255, 255, 200));
                overlay.add(sizeLabel);
            }
        }
        
        // Optional summary
        if (showSummary && !colonies.isEmpty()) {
            String summary = String.format("Colonies: %d", colonies.size());
            TextRoi summaryLabel = new TextRoi(
                10, 10, summary, new Font("Arial", Font.BOLD, 12)
            );
            summaryLabel.setStrokeColor(Color.BLACK);
            summaryLabel.setFillColor(new Color(255, 255, 0, 200));
            overlay.add(summaryLabel);
        }
        
        return overlay;
    }
    
    /**
     * Create overlay with colonies colored by classification results
     */
    public static Overlay createClassificationOverlay(List<Colony> colonies, 
                                                     Map<Colony, String> classifications, 
                                                     double pxPerMM) {
        Overlay overlay = new Overlay();
        
        for (int i = 0; i < colonies.size(); i++) {
            Colony colony = colonies.get(i);
            String classification = classifications.getOrDefault(colony, "uncertain");
            Color classColor = COLONY_COLORS.getOrDefault(classification, Color.GRAY);
            
            double radiusPx = colony.diameter() / 2.0;
            
            // Colony circle
            OvalRoi circle = new OvalRoi(
                colony.x() - radiusPx, 
                colony.y() - radiusPx, 
                radiusPx * 2, 
                radiusPx * 2
            );
            circle.setStrokeColor(classColor);
            circle.setStrokeWidth(2.5);
            circle.setName("Colony_" + (i + 1) + "_" + classification);
            overlay.add(circle);
            
            // Classification label
            TextRoi classLabel = new TextRoi(
                colony.x() + radiusPx + 3, 
                colony.y() + radiusPx + 3, 
                classification, 
                new Font("Arial", Font.PLAIN, 8)
            );
            classLabel.setStrokeColor(classColor);
            classLabel.setFillColor(new Color(0, 0, 0, 150));
            overlay.add(classLabel);
        }
        
        return overlay;
    }
    
    /**
     * Get standardized color for a colony based on its properties
     */
    private static Color getColonyColor(Colony colony) {
        // Default color based on size (larger = darker blue)
        double intensity = Math.min(colony.diameter() / 50.0, 1.0); // Normalize to 0-1
        int colorValue = (int)(255 * (1.0 - intensity));
        return new Color(colorValue, colorValue, 255); // Blue gradient
    }
    
    /**
     * Create a legend overlay for colony classifications
     */
    public static Overlay createColonyLegend(List<String> classifications, int x, int y) {
        Overlay overlay = new Overlay();
        
        // Legend background
        Roi legendBg = new Roi(x - 5, y - 5, 120, classifications.size() * 20 + 10);
        legendBg.setStrokeColor(Color.BLACK);
        legendBg.setFillColor(new Color(255, 255, 255, 200));
        overlay.add(legendBg);
        
        // Legend title
        TextRoi title = new TextRoi(x, y, "Classifications", new Font("Arial", Font.BOLD, 10));
        title.setStrokeColor(Color.BLACK);
        overlay.add(title);
        
        // Legend entries
        for (int i = 0; i < classifications.size(); i++) {
            String classification = classifications.get(i);
            Color color = COLONY_COLORS.getOrDefault(classification, Color.GRAY);
            
            int entryY = y + 15 + (i * 18);
            
            // Color dot
            OvalRoi dot = new OvalRoi(x + 5, entryY, 8, 8);
            dot.setStrokeColor(color);
            dot.setFillColor(color);
            overlay.add(dot);
            
            // Label
            TextRoi label = new TextRoi(x + 20, entryY + 2, classification, 
                                       new Font("Arial", Font.PLAIN, 9));
            label.setStrokeColor(Color.BLACK);
            overlay.add(label);
        }
        
        return overlay;
    }
}