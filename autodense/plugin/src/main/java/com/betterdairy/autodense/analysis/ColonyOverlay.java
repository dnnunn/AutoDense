package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import ij.gui.TextRoi;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;

/**
 * Pure functional overlay rendering for colony visualization.
 * Creates color-coded overlays based on classification results.
 */
public final class ColonyOverlay {
    
    /**
     * Color scheme for different colony types
     */
    private static final Map<String, Color> CLASSIFICATION_COLORS = Map.of(
        "xgal_pos", new Color(0, 100, 255),      // Blue
        "xgal_neg", new Color(255, 140, 0),      // Orange  
        "uncertain", Color.GRAY,                 // Gray
        "cluster_0", Color.MAGENTA,              // Magenta
        "cluster_1", Color.CYAN,                 // Cyan
        "cluster_2", Color.YELLOW                // Yellow
    );
    
    /**
     * Render colony overlay with color coding and optional size labels
     */
    public static Overlay render(List<Colony> colonies, double pxPerMM) {
        return render(colonies, pxPerMM, false, false);
    }
    
    /**
     * Render overlay with full control over display options
     * @param colonies List of colonies to visualize
     * @param pxPerMM Pixels per millimeter (for stroke width scaling)
     * @param showSizeText Show size text in mm with 1 decimal
     * @param showSummary Show summary statistics
     */
    public static Overlay render(List<Colony> colonies, double pxPerMM, 
                               boolean showSizeText, boolean showSummary) {
        
        Overlay overlay = new Overlay();
        
        for (Colony colony : colonies) {
            // Draw small circle at centroid
            double radius = Math.max(3.0, 8.0 / pxPerMM); // Minimum 3px, scaled by resolution
            OvalRoi circle = new OvalRoi(
                colony.x() - radius, 
                colony.y() - radius,
                radius * 2, 
                radius * 2
            );
            
            // Color by class: blue/orange/gray
            Color color = getClassificationColor(colony.binCategory());
            circle.setStrokeColor(color);
            
            // Stroke width scaled by px/mm (thinner for higher resolution)
            double strokeWidth = Math.max(1.0, 2.0 * 10.0 / pxPerMM); // Scale from 10px/mm baseline
            circle.setStrokeWidth(strokeWidth);
            circle.setName("Colony_" + colony.index());
            
            overlay.add(circle);
            
            // Optional size text (mm with 1 decimal)
            if (showSizeText) {
                double diameterMM = colony.diameter() / pxPerMM;
                String sizeText = String.format("%.1f", diameterMM);
                
                TextRoi sizeLabel = new TextRoi(
                    colony.x() + radius + 2, 
                    colony.y() - radius, 
                    sizeText
                );
                sizeLabel.setStrokeColor(color.darker());
                sizeLabel.setFillColor(new Color(255, 255, 255, 200));
                sizeLabel.setFont(new Font("Arial", Font.PLAIN, 9));
                overlay.add(sizeLabel);
            }
        }
        
        // Add summary statistics
        if (showSummary && !colonies.isEmpty()) {
            TextRoi summary = createSummaryLabel(colonies);
            overlay.add(summary);
        }
        
        return overlay;
    }
    
    /**
     * Create detection overlay (simple circles without classification)
     */
    public static Overlay renderDetection(List<Colony> colonies) {
        Overlay overlay = new Overlay();
        
        for (Colony colony : colonies) {
            double radius = colony.diameter() / 2.0;
            OvalRoi circle = new OvalRoi(
                colony.x() - radius,
                colony.y() - radius, 
                colony.diameter(),
                colony.diameter()
            );
            
            circle.setStrokeColor(Color.YELLOW);
            circle.setStrokeWidth(1.5);
            overlay.add(circle);
        }
        
        return overlay;
    }
    
    /**
     * Create export-ready overlay with enhanced formatting
     */
    public static Overlay renderExport(List<Colony> colonies, double pxPerMM, String title) {
        Overlay overlay = render(colonies, pxPerMM, true, true);
        
        // Add title
        TextRoi titleLabel = new TextRoi(10, 30, title);
        titleLabel.setStrokeColor(Color.BLACK);
        titleLabel.setFillColor(new Color(255, 255, 255, 240));
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        overlay.add(titleLabel);
        
        // Add scale bar if pxPerMM is known
        if (pxPerMM > 0) {
            addScaleBar(overlay, pxPerMM);
        }
        
        return overlay;
    }
    
    /**
     * Get color for colony classification
     */
    private static Color getClassificationColor(String classification) {
        return CLASSIFICATION_COLORS.getOrDefault(classification, Color.WHITE);
    }
    
    /**
     * Create text label for individual colony
     */
    private static TextRoi createColonyLabel(Colony colony, int displayIndex) {
        String labelText = "C" + displayIndex;
        
        // Add size info if available
        if (colony.diameterMm() > 0) {
            labelText += String.format("(%.1fmm)", colony.diameterMm());
        }
        
        TextRoi label = new TextRoi(
            colony.x() - 10, 
            colony.y() - 8, 
            labelText
        );
        
        Color textColor = getClassificationColor(colony.binCategory()).darker();
        label.setStrokeColor(textColor);
        label.setFillColor(new Color(255, 255, 255, 220));
        label.setFont(new Font("Arial", Font.BOLD, 10));
        
        return label;
    }
    
    /**
     * Create summary statistics label
     */
    private static TextRoi createSummaryLabel(List<Colony> colonies) {
        Map<String, Integer> counts = ColonyClassifier.summary(colonies);
        
        StringBuilder summary = new StringBuilder("Total: " + colonies.size());
        
        if (counts.containsKey("xgal_pos") || counts.containsKey("xgal_neg")) {
            int positive = counts.getOrDefault("xgal_pos", 0);
            int negative = counts.getOrDefault("xgal_neg", 0);
            summary.append(String.format(" | X-gal+: %d | X-gal-: %d", positive, negative));
        }
        
        TextRoi summaryLabel = new TextRoi(10, 10, summary.toString());
        summaryLabel.setStrokeColor(Color.BLACK);
        summaryLabel.setFillColor(new Color(255, 255, 255, 240));
        summaryLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        return summaryLabel;
    }
    
    /**
     * Add scale bar to overlay
     */
    private static void addScaleBar(Overlay overlay, double pxPerMM) {
        // 10mm scale bar
        double barLengthPx = 10 * pxPerMM;
        int barX = 10;
        int barY = 50;
        
        // Scale bar line (would need LineRoi - simplified here)
        TextRoi scaleLabel = new TextRoi(barX, barY + 15, "10mm");
        scaleLabel.setStrokeColor(Color.BLACK);
        scaleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        overlay.add(scaleLabel);
    }
}