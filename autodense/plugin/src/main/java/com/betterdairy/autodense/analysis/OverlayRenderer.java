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
    
    // CONSISTENT FONT/STROKE DEFAULTS - Set once to prevent golden overlay wiggling
    // These fonts work consistently across headless vs GUI ImageJ environments
    // MONOSPACED chosen because it renders identically on all systems/platforms
    // whereas Arial/SansSerif can vary between macOS/Linux/Windows
    public static final Font SMALL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 9);
    public static final Font NORMAL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font BOLD_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    public static final Font LARGE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 14);
    public static final Font TITLE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 16);
    
    // Consistent stroke widths
    public static final float THIN_STROKE = 1.0f;
    public static final float NORMAL_STROKE = 2.0f;
    public static final float THICK_STROKE = 3.0f;

    /** 
     * Build an overlay with lane boxes and band boxes+labels from existing lanes.
     * @deprecated Use fromLanes(lanes, allBands, imageHeight, imageWidth) for proper positioning
     */
    @Deprecated
    public static Overlay fromLanes(List<Lane> lanes, List<List<AssistBand>> allBands) {
        return fromLanes(lanes, allBands, 400, 600); // Default dimensions for backward compatibility
    }
    
    /** 
     * Build an overlay with lane boxes and band boxes+labels from existing lanes.
     * @deprecated Use fromLanes(lanes, allBands, imageHeight, imageWidth) for proper positioning
     */
    @Deprecated
    public static Overlay fromLanes(List<Lane> lanes, List<List<AssistBand>> allBands, int imageHeight) {
        return fromLanes(lanes, allBands, imageHeight, 600); // Default width for backward compatibility
    }
    
    /** 
     * Build an overlay with lane boxes and band boxes+labels from existing lanes.
     * Uses actual image dimensions for proper ROI positioning and bounds checking.
     */
    public static Overlay fromLanes(List<Lane> lanes, List<List<AssistBand>> allBands, int imageHeight, int imageWidth) {
        Overlay ov = new Overlay();
        
        // Render lanes (light green outline)
        for (int i = 0; i < lanes.size(); i++) {
            Lane ln = lanes.get(i);
            Roi laneR = new Roi(ln.xStart(), 0, ln.xEnd() - ln.xStart() + 1, imageHeight);
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
                    // If smear is significant (>15%), draw smear region first as faint gradient
                    if (b.smearPercent > 15.0) {
                        addSmearRegion(ov, b, lanes.get(laneIdx), imageHeight, imageWidth);
                    }
                    
                    // Band rectangle
                    Roi r = new Roi(b.xStart, b.yStart, b.widthPx(), b.heightPx());
                    r.setStrokeColor(new Color(0, 255, 255));
                    r.setStrokeWidth(1.5);
                    r.setName("L" + (laneIdx + 1) + "B" + bandIndex);
                    ov.add(r);

                    // Band label with smear indicator if significant
                    String label = "L" + (laneIdx + 1) + " B" + bandIndex;
                    if (b.smearPercent > 15.0) {
                        label += String.format(" (%.0f%% smear)", b.smearPercent);
                    }
                    // Position text label with bounds checking
                    int textX = Math.max(0, b.xStart + 2);
                    int textY = Math.max(12, Math.min(imageHeight - 5, b.yStart + 12));
                    TextRoi tr = new TextRoi(textX, textY, label, SMALL_FONT);
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
            TextRoi tr = new TextRoi(b.xStart + 2, b.yStart + 12, label, SMALL_FONT);
            tr.setStrokeColor(Color.WHITE);
            tr.setFillColor(new Color(0, 0, 0, 140));
            ov.add(tr);
            
            bandNumber++;
        }
    }

    /**
     * Create an overlay specifically for assisted bands with confidence indicators.
     * @deprecated Use createAssistedBandOverlay(assistedBands, imageHeight, imageWidth) for proper bounds checking
     */
    @Deprecated
    public static Overlay createAssistedBandOverlay(List<AssistBand> assistedBands) {
        return createAssistedBandOverlay(assistedBands, 400, 600); // Default dimensions
    }
    
    /**
     * Create an overlay specifically for assisted bands with confidence indicators.
     * Uses actual image dimensions for proper bounds checking.
     */
    public static Overlay createAssistedBandOverlay(List<AssistBand> assistedBands, int imageHeight, int imageWidth) {
        Overlay ov = new Overlay();
        
        for (int i = 0; i < assistedBands.size(); i++) {
            AssistBand b = assistedBands.get(i);
            
            // If smear is significant (>15%), draw smear region first as faint gradient
            if (b.smearPercent > 15.0) {
                addSmearRegion(ov, b, null, imageHeight, imageWidth); // No lane info for assisted bands
            }
            
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
            
            // Band label with confidence indicator and smear info
            String label = String.format("A%d (%.0f%%)", i + 1, b.confidence * 100);
            if (b.smearPercent > 15.0) {
                label += String.format(" [%.0f%% smear]", b.smearPercent);
            }
            TextRoi tr = new TextRoi(b.xStart + 2, b.yStart - 15, label, BOLD_FONT);
            tr.setStrokeColor(Color.MAGENTA);
            tr.setFillColor(new Color(255, 255, 255, 200));
            ov.add(tr);
        }
        
        return ov;
    }
    
    /**
     * Add a smear region visualization as a faint gradient box.
     * The smear region extends vertically from the band to show migration spreading.
     */
    private static void addSmearRegion(Overlay ov, AssistBand band, Lane lane, int imageHeight, int imageWidth) {
        // Calculate smear region dimensions with bounds checking
        // Smear extends both above and below the band, proportional to smear percentage
        int smearExtension = Math.max(5, (int)(band.heightPx() * (band.smearPercent / 100.0) * 2));
        int smearTop = Math.max(0, band.yStart - smearExtension);
        int smearBottom = Math.min(imageHeight - 1, band.yEnd + smearExtension);
        int smearHeight = smearBottom - smearTop;
        
        // Use lane boundaries if available, otherwise use band boundaries with padding
        int smearLeft = lane != null ? Math.max(0, lane.xStart()) : Math.max(0, band.xStart - 2);
        int smearRight = lane != null ? Math.min(imageWidth - 1, lane.xEnd()) : Math.min(imageWidth - 1, band.xEnd + 2);
        int smearWidth = smearRight - smearLeft;
        
        // Validate positive dimensions before creating ROI
        if (smearWidth <= 0 || smearHeight <= 0) {
            return; // Skip invalid smear region
        }
        
        // Create smear region rectangle with faint fill
        Roi smearRoi = new Roi(smearLeft, smearTop, smearWidth, smearHeight);
        
        // Color intensity based on smear percentage (more smear = more visible)
        int alpha = Math.min(80, (int)(band.smearPercent * 2)); // Max 80 for visibility
        Color smearColor = new Color(255, 165, 0, alpha); // Orange with variable transparency
        
        smearRoi.setFillColor(smearColor);
        smearRoi.setStrokeColor(new Color(255, 140, 0, 150)); // Slightly darker orange border
        smearRoi.setStrokeWidth(1.0);
        smearRoi.setName("Smear_Region_" + band.snr); // Use SNR as unique identifier
        ov.add(smearRoi);
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
                    SMALL_FONT
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
                10, 10, summary, BOLD_FONT
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
                SMALL_FONT
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
        TextRoi title = new TextRoi(x, y, "Classifications", BOLD_FONT);
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
                                       SMALL_FONT);
            label.setStrokeColor(Color.BLACK);
            overlay.add(label);
        }
        
        return overlay;
    }
    
    /**
     * Create uncertain colony preview overlay with gray halos for click-assist
     * Shows only colonies classified as "uncertain" with prominent gray halos
     * prompting user to click for manual classification assistance
     */
    public static Overlay createUncertainMutableColonyOverlay(List<MutableColony> colonies, double pxPerMM) {
        Overlay overlay = new Overlay();
        
        int uncertainCount = 0;
        for (int i = 0; i < colonies.size(); i++) {
            MutableColony colony = colonies.get(i);
            
            // Only show uncertain colonies
            if (!"uncertain".equals(colony.xgalBinary)) {
                continue;
            }
            
            uncertainCount++;
            double radiusPx = colony.eqDiamPx / 2.0;
            
            // Main colony circle with thick gray stroke
            OvalRoi circle = new OvalRoi(
                colony.x - radiusPx, 
                colony.y - radiusPx, 
                radiusPx * 2, 
                radiusPx * 2
            );
            circle.setStrokeColor(Color.GRAY);
            circle.setStrokeWidth(3.0);
            circle.setName("Uncertain_Colony_" + (i + 1));
            overlay.add(circle);
            
            // Gray halo - larger outer circle with faint fill
            double haloRadius = radiusPx * 1.5;
            OvalRoi halo = new OvalRoi(
                colony.x - haloRadius, 
                colony.y - haloRadius, 
                haloRadius * 2, 
                haloRadius * 2
            );
            halo.setStrokeColor(new Color(128, 128, 128, 100)); // Faint gray
            halo.setFillColor(new Color(128, 128, 128, 30));    // Very faint fill
            halo.setStrokeWidth(1.0);
            halo.setName("Uncertain_Halo_" + (i + 1));
            overlay.add(halo);
            
            // Click-assist prompt label
            String promptText = "?";
            TextRoi prompt = new TextRoi(
                colony.x - 5, 
                colony.y - 8, 
                promptText, 
                TITLE_FONT
            );
            prompt.setStrokeColor(Color.WHITE);
            prompt.setFillColor(new Color(128, 128, 128, 200));
            overlay.add(prompt);
        }
        
        // Add summary text if there are uncertain colonies
        if (uncertainCount > 0) {
            String summaryText = uncertainCount + " uncertain colonies - click to assist";
            TextRoi summary = new TextRoi(10, 10, summaryText, BOLD_FONT);
            summary.setStrokeColor(Color.WHITE);
            summary.setFillColor(new Color(128, 128, 128, 180));
            overlay.add(summary);
        }
        
        return overlay;
    }
    
    /**
     * Create click-assist overlay highlighting a specific colony for manual classification
     * Used when user clicks on an uncertain colony to provide classification assistance
     */
    public static Overlay createClickAssistOverlay(Colony targetColony, List<Colony> allColonies, 
                                                  double pxPerMM, String promptMessage) {
        Overlay overlay = new Overlay();
        
        // Highlight the target colony with pulsing effect
        double radiusPx = targetColony.diameter() / 2.0;
        
        // Main highlight circle - bright yellow/orange
        OvalRoi highlight = new OvalRoi(
            targetColony.x() - radiusPx, 
            targetColony.y() - radiusPx, 
            radiusPx * 2, 
            radiusPx * 2
        );
        highlight.setStrokeColor(Color.ORANGE);
        highlight.setFillColor(new Color(255, 255, 0, 60)); // Bright yellow with transparency
        highlight.setStrokeWidth(4.0);
        highlight.setName("Click_Assist_Target");
        overlay.add(highlight);
        
        // Outer pulsing ring
        double pulseRadius = radiusPx * 2.0;
        OvalRoi pulseRing = new OvalRoi(
            targetColony.x() - pulseRadius, 
            targetColony.y() - pulseRadius, 
            pulseRadius * 2, 
            pulseRadius * 2
        );
        pulseRing.setStrokeColor(new Color(255, 165, 0, 150)); // Orange with transparency
        pulseRing.setStrokeWidth(2.0);
        pulseRing.setName("Click_Assist_Pulse");
        overlay.add(pulseRing);
        
        // Instruction text near the colony
        String instructionText = promptMessage != null ? promptMessage : "Click to classify this colony";
        TextRoi instruction = new TextRoi(
            targetColony.x() + radiusPx + 10, 
            targetColony.y() - 10, 
            instructionText, 
            LARGE_FONT
        );
        instruction.setStrokeColor(Color.WHITE);
        instruction.setFillColor(new Color(0, 0, 0, 200));
        overlay.add(instruction);
        
        // Show nearby similar colonies for reference (faint outlines)
        for (Colony colony : allColonies) {
            if (colony == targetColony) continue;
            
            // Only show colonies within reasonable distance
            double distance = Math.sqrt(Math.pow(colony.x() - targetColony.x(), 2) + 
                                      Math.pow(colony.y() - targetColony.y(), 2));
            if (distance < 100) { // Within 100 pixels
                double refRadius = colony.diameter() / 2.0;
                OvalRoi refCircle = new OvalRoi(
                    colony.x() - refRadius, 
                    colony.y() - refRadius, 
                    refRadius * 2, 
                    refRadius * 2
                );
                refCircle.setStrokeColor(new Color(200, 200, 200, 100)); // Very faint gray
                refCircle.setStrokeWidth(1.0);
                refCircle.setName("Reference_Colony");
                overlay.add(refCircle);
            }
        }
        
        return overlay;
    }
    
    /**
     * Create volcano plot overlay (Δlog2 vs -log10 p-value) for lane comparison results
     */
    public static Overlay createVolcanoPlotOverlay(LaneComparator.LaneComparisonAnalysis analysis, 
                                                   int plotWidth, int plotHeight) {
        return createVolcanoPlotOverlay(analysis, plotWidth, plotHeight, 0, 0);
    }
    
    /**
     * Create volcano plot overlay with custom position
     */
    public static Overlay createVolcanoPlotOverlay(LaneComparator.LaneComparisonAnalysis analysis, 
                                                   int plotWidth, int plotHeight, 
                                                   int offsetX, int offsetY) {
        Overlay overlay = new Overlay();
        
        if (analysis == null || analysis.results.isEmpty()) {
            return overlay;
        }
        
        // Calculate plot bounds
        double maxLogFC = analysis.results.stream()
                                         .mapToDouble(r -> Math.abs(r.log2FoldChange))
                                         .max().orElse(2.0);
        double maxNegLogP = analysis.results.stream()
                                           .mapToDouble(r -> -Math.log10(Math.max(r.adjustedPValue, 1e-10)))
                                           .max().orElse(5.0);
        
        // Add 10% padding
        maxLogFC *= 1.1;
        maxNegLogP *= 1.1;
        
        // Plot area dimensions (leave space for axes)
        int margin = 50;
        int innerWidth = plotWidth - 2 * margin;
        int innerHeight = plotHeight - 2 * margin;
        
        // Draw plot border
        Roi plotBorder = new Roi(offsetX + margin, offsetY + margin, innerWidth, innerHeight);
        plotBorder.setStrokeColor(Color.BLACK);
        plotBorder.setStrokeWidth(2.0);
        plotBorder.setName("Volcano_Plot_Border");
        overlay.add(plotBorder);
        
        // Draw axes
        // Y-axis (left side)
        for (int i = 0; i <= 5; i++) {
            double yVal = (maxNegLogP * i) / 5.0;
            int yPos = offsetY + margin + innerHeight - (int)((yVal / maxNegLogP) * innerHeight);
            
            // Y-axis tick marks
            Roi yTick = new Roi(offsetX + margin - 5, yPos, 10, 1);
            yTick.setStrokeColor(Color.BLACK);
            yTick.setStrokeWidth(1.0);
            yTick.setName("Y_Tick_" + i);
            overlay.add(yTick);
            
            // Y-axis labels
            if (i > 0) { // Skip label at origin
                TextRoi yLabel = new TextRoi(offsetX + margin - 35, yPos - 5, 
                                           String.format("%.1f", yVal));
                yLabel.setStrokeColor(Color.BLACK);
                yLabel.setFont(NORMAL_FONT);
                yLabel.setName("Y_Label_" + i);
                overlay.add(yLabel);
            }
        }
        
        // X-axis (bottom)
        for (int i = -5; i <= 5; i++) {
            double xVal = (maxLogFC * i) / 5.0;
            int xPos = offsetX + margin + (int)(((xVal + maxLogFC) / (2 * maxLogFC)) * innerWidth);
            
            // X-axis tick marks
            if (xPos >= offsetX + margin && xPos <= offsetX + margin + innerWidth) {
                Roi xTick = new Roi(xPos, offsetY + margin + innerHeight - 5, 1, 10);
                xTick.setStrokeColor(Color.BLACK);
                xTick.setStrokeWidth(1.0);
                xTick.setName("X_Tick_" + (i + 5));
                overlay.add(xTick);
                
                // X-axis labels
                if (i != 0) { // Skip label at origin
                    TextRoi xLabel = new TextRoi(xPos - 10, offsetY + margin + innerHeight + 15, 
                                               String.format("%.1f", xVal));
                    xLabel.setStrokeColor(Color.BLACK);
                    xLabel.setFont(NORMAL_FONT);
                    xLabel.setName("X_Label_" + (i + 5));
                    overlay.add(xLabel);
                }
            }
        }
        
        // Draw significance threshold lines
        LaneComparator.ComparisonConfig config = analysis.config;
        
        // Horizontal line for p-value threshold
        double pThresholdNegLog = -Math.log10(config.volcanoPThreshold);
        if (pThresholdNegLog <= maxNegLogP) {
            int pThresholdY = offsetY + margin + innerHeight - (int)((pThresholdNegLog / maxNegLogP) * innerHeight);
            Roi pThresholdLine = new Roi(offsetX + margin, pThresholdY, innerWidth, 1);
            pThresholdLine.setStrokeColor(new Color(255, 0, 0, 150)); // Semi-transparent red
            pThresholdLine.setStrokeWidth(2.0);
            pThresholdLine.setName("P_Threshold_Line");
            overlay.add(pThresholdLine);
        }
        
        // Vertical lines for fold change thresholds
        double fcThreshold = config.volcanoFCThreshold;
        if (fcThreshold <= maxLogFC) {
            // Positive fold change threshold
            int posFCX = offsetX + margin + (int)(((fcThreshold + maxLogFC) / (2 * maxLogFC)) * innerWidth);
            if (posFCX <= offsetX + margin + innerWidth) {
                Roi posFCLine = new Roi(posFCX, offsetY + margin, 1, innerHeight);
                posFCLine.setStrokeColor(new Color(0, 0, 255, 150)); // Semi-transparent blue
                posFCLine.setStrokeWidth(2.0);
                posFCLine.setName("Pos_FC_Threshold_Line");
                overlay.add(posFCLine);
            }
            
            // Negative fold change threshold
            int negFCX = offsetX + margin + (int)(((-fcThreshold + maxLogFC) / (2 * maxLogFC)) * innerWidth);
            if (negFCX >= offsetX + margin) {
                Roi negFCLine = new Roi(negFCX, offsetY + margin, 1, innerHeight);
                negFCLine.setStrokeColor(new Color(0, 0, 255, 150)); // Semi-transparent blue
                negFCLine.setStrokeWidth(2.0);
                negFCLine.setName("Neg_FC_Threshold_Line");
                overlay.add(negFCLine);
            }
        }
        
        // Plot data points
        for (int i = 0; i < analysis.results.size(); i++) {
            LaneComparator.ComparisonResult result = analysis.results.get(i);
            
            double x = result.log2FoldChange;
            double y = -Math.log10(Math.max(result.adjustedPValue, 1e-10));
            
            // Convert to plot coordinates
            int plotX = offsetX + margin + (int)(((x + maxLogFC) / (2 * maxLogFC)) * innerWidth);
            int plotY = offsetY + margin + innerHeight - (int)((y / maxNegLogP) * innerHeight);
            
            // Skip points outside plot area
            if (plotX < offsetX + margin || plotX > offsetX + margin + innerWidth ||
                plotY < offsetY + margin || plotY > offsetY + margin + innerHeight) {
                continue;
            }
            
            // Determine point color and size based on significance
            Color pointColor;
            int pointSize = 4;
            
            if (result.isSignificant) {
                if (Math.abs(result.log2FoldChange) >= config.volcanoFCThreshold) {
                    // Significant and large fold change
                    pointColor = new Color(255, 0, 0, 200); // Red
                    pointSize = 6;
                } else {
                    // Significant but small fold change
                    pointColor = new Color(255, 165, 0, 200); // Orange
                    pointSize = 5;
                }
            } else {
                if (Math.abs(result.log2FoldChange) >= config.volcanoFCThreshold) {
                    // Large fold change but not significant
                    pointColor = new Color(0, 0, 255, 150); // Blue
                    pointSize = 5;
                } else {
                    // Neither significant nor large fold change
                    pointColor = new Color(128, 128, 128, 100); // Gray
                    pointSize = 3;
                }
            }
            
            // Create point as small circle
            OvalRoi point = new OvalRoi(plotX - pointSize/2, plotY - pointSize/2, pointSize, pointSize);
            point.setStrokeColor(pointColor);
            point.setFillColor(pointColor);
            point.setStrokeWidth(1.0);
            point.setName(String.format("Peak_MW_%.1f_FC_%.2f_P_%.3e", 
                                       result.peakGroup.targetMW, 
                                       result.log2FoldChange, 
                                       result.adjustedPValue));
            overlay.add(point);
        }
        
        // Add axis labels
        TextRoi xAxisLabel = new TextRoi(offsetX + plotWidth/2 - 30, offsetY + plotHeight - 15, 
                                        "Log2 Fold Change");
        xAxisLabel.setStrokeColor(Color.BLACK);
        xAxisLabel.setFont(BOLD_FONT);
        xAxisLabel.setName("X_Axis_Label");
        overlay.add(xAxisLabel);
        
        // Y-axis label (rotated text approximation)
        TextRoi yAxisLabel = new TextRoi(offsetX + 5, offsetY + plotHeight/2, "-Log10 P-value");
        yAxisLabel.setStrokeColor(Color.BLACK);
        yAxisLabel.setFont(BOLD_FONT);
        yAxisLabel.setName("Y_Axis_Label");
        overlay.add(yAxisLabel);
        
        // Add title
        String title = String.format("Volcano Plot (%d peaks, %d significant)", 
                                    analysis.totalPeaks, analysis.significantPeaks);
        TextRoi titleLabel = new TextRoi(offsetX + plotWidth/2 - 80, offsetY + 10, title);
        titleLabel.setStrokeColor(Color.BLACK);
        titleLabel.setFont(LARGE_FONT);
        titleLabel.setName("Plot_Title");
        overlay.add(titleLabel);
        
        // Add legend
        int legendX = offsetX + plotWidth - 150;
        int legendY = offsetY + 30;
        
        // Legend background
        Roi legendBg = new Roi(legendX - 5, legendY - 5, 140, 80);
        legendBg.setStrokeColor(Color.BLACK);
        legendBg.setFillColor(new Color(255, 255, 255, 200));
        legendBg.setStrokeWidth(1.0);
        legendBg.setName("Legend_Background");
        overlay.add(legendBg);
        
        // Legend entries
        String[] legendTexts = {
            "● Significant + High FC",
            "● Significant", 
            "● High FC only",
            "● Not significant"
        };
        Color[] legendColors = {
            new Color(255, 0, 0),
            new Color(255, 165, 0),
            new Color(0, 0, 255),
            new Color(128, 128, 128)
        };
        
        for (int i = 0; i < legendTexts.length; i++) {
            TextRoi legendEntry = new TextRoi(legendX, legendY + i * 15, legendTexts[i]);
            legendEntry.setStrokeColor(legendColors[i]);
            legendEntry.setFont(NORMAL_FONT);
            legendEntry.setName("Legend_Entry_" + i);
            overlay.add(legendEntry);
        }
        
        return overlay;
    }
}