package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.analysis.AssistModels.AssistBand;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;

import java.awt.*;
import java.util.List;

/**
 * Utility for rendering lanes and bands as ImageJ overlays.
 * Integrates with existing AutoDense Lane and Band models.
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
}