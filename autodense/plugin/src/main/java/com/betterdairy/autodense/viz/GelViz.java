package com.betterdairy.autodense.viz;

import com.betterdairy.autodense.model.Models.*;
import org.json.JSONObject;
import org.json.JSONArray;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Line2D;
import java.util.List;
import java.util.ArrayList;

/**
 * Headless-safe gel electrophoresis visualization renderer.
 * 
 * Creates annotated overlay images for lanes and bands without touching UI components.
 * Safe for use in headless environments and CLI workflows.
 */
public final class GelViz {
    
    /**
     * Render gel analysis overlay on BufferedImage (headless-safe)
     * 
     * @param imagePlus Original gel image
     * @param gelResult Gel analysis results with detected lanes and bands
     * @param headless Whether to render in headless mode (no UI dependencies)
     * @return BufferedImage with gel overlays rendered
     */
    public static BufferedImage renderOverlay(ImagePlus imagePlus, JSONObject gelResult, boolean headless) {
        if (imagePlus == null || gelResult == null) {
            throw new IllegalArgumentException("Image and gel result cannot be null");
        }
        
        // Convert ImagePlus to BufferedImage
        BufferedImage baseImage = imagePlus.getBufferedImage();
        if (baseImage == null) {
            // Fallback: create BufferedImage from ImageProcessor
            ImageProcessor ip = imagePlus.getProcessor();
            baseImage = ip.getBufferedImage();
        }
        
        // Create copy for overlay rendering
        BufferedImage overlayImage = new BufferedImage(
            baseImage.getWidth(), 
            baseImage.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        Graphics2D g2d = overlayImage.createGraphics();
        try {
            // Draw base image
            g2d.drawImage(baseImage, 0, 0, null);
            
            // Configure rendering quality
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Render lane overlays first
            renderLaneOverlays(g2d, gelResult, headless);
            
            // Render band overlays on top
            renderBandOverlays(g2d, gelResult, headless);
            
            // Add molecular weight ladder if available
            renderMolecularWeightLadder(g2d, gelResult, headless);
            
            return overlayImage;
            
        } finally {
            g2d.dispose();
        }
    }
    
    /**
     * Render lane boundary overlays
     */
    private static void renderLaneOverlays(Graphics2D g2d, JSONObject gelResult, boolean headless) {
        JSONArray lanes = gelResult.optJSONArray("lanes");
        if (lanes == null) {
            return;
        }
        
        // Set up lane drawing parameters
        Stroke originalStroke = g2d.getStroke();
        Font originalFont = g2d.getFont();
        
        // Lane boundary stroke
        g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5.0f, 5.0f}, 0));
        Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        g2d.setFont(labelFont);
        
        try {
            for (int i = 0; i < lanes.length(); i++) {
                JSONObject lane = lanes.getJSONObject(i);
                renderSingleLane(g2d, lane, i + 1, headless);
            }
        } finally {
            // Restore original graphics state
            g2d.setStroke(originalStroke);
            g2d.setFont(originalFont);
        }
    }
    
    /**
     * Render individual lane boundaries and labels
     */
    private static void renderSingleLane(Graphics2D g2d, JSONObject lane, int laneNumber, boolean headless) {
        double leftX = lane.optDouble("left_x", 0);
        double rightX = lane.optDouble("right_x", 0);
        double topY = lane.optDouble("top_y", 0);
        double bottomY = lane.optDouble("bottom_y", 0);
        double width = rightX - leftX;
        
        // Color code lanes based on width consistency
        Color laneColor;
        if (width < 20) {
            laneColor = Color.ORANGE; // Narrow lane warning
        } else if (width > 100) {
            laneColor = Color.RED; // Wide lane warning  
        } else {
            laneColor = new Color(0, 150, 255); // Normal lane - bright blue
        }
        
        g2d.setColor(laneColor);
        
        // Draw lane boundaries
        Line2D.Double leftBoundary = new Line2D.Double(leftX, topY, leftX, bottomY);
        Line2D.Double rightBoundary = new Line2D.Double(rightX, topY, rightX, bottomY);
        
        g2d.draw(leftBoundary);
        g2d.draw(rightBoundary);
        
        // Add lane number label at top
        String laneLabel = "L" + laneNumber;
        FontMetrics fm = g2d.getFontMetrics();
        int labelWidth = fm.stringWidth(laneLabel);
        
        int labelX = (int)((leftX + rightX) / 2 - labelWidth / 2);
        int labelY = (int)(topY - 5);
        
        // Draw label background
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(labelX - 2, labelY - fm.getHeight() + 2, labelWidth + 4, fm.getHeight());
        
        // Draw label text
        g2d.setColor(Color.WHITE);
        g2d.drawString(laneLabel, labelX, labelY);
        
        // Add width info in headless mode
        if (headless) {
            String widthInfo = String.format("W=%.0f", width);
            g2d.setColor(laneColor);
            g2d.drawString(widthInfo, labelX, (int)(bottomY + 15));
        }
    }
    
    /**
     * Render band detection overlays
     */
    private static void renderBandOverlays(Graphics2D g2d, JSONObject gelResult, boolean headless) {
        JSONArray bands = gelResult.optJSONArray("bands");
        if (bands == null) {
            return;
        }
        
        // Set up band drawing parameters
        Stroke originalStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(2.0f));
        
        try {
            for (int i = 0; i < bands.length(); i++) {
                JSONObject band = bands.getJSONObject(i);
                renderSingleBand(g2d, band, i + 1, headless);
            }
        } finally {
            g2d.setStroke(originalStroke);
        }
    }
    
    /**
     * Render individual band with intensity-based color coding
     */
    private static void renderSingleBand(Graphics2D g2d, JSONObject band, int bandId, boolean headless) {
        double centerX = band.optDouble("center_x", 0);
        double centerY = band.optDouble("center_y", 0);
        double width = band.optDouble("width", 20);
        double height = band.optDouble("height", 10);
        double intensity = band.optDouble("intensity", 0);
        int laneNumber = band.optInt("lane", 0);
        
        // Color coding based on band intensity
        Color bandColor;
        if (intensity > 0.8) {
            bandColor = new Color(255, 50, 50); // Bright red for high intensity
        } else if (intensity > 0.5) {
            bandColor = new Color(255, 165, 0); // Orange for medium intensity
        } else if (intensity > 0.2) {
            bandColor = new Color(255, 255, 0); // Yellow for low intensity
        } else {
            bandColor = new Color(150, 150, 150); // Gray for very low intensity
        }
        
        g2d.setColor(bandColor);
        
        // Draw band rectangle
        Rectangle2D.Double bandRect = new Rectangle2D.Double(
            centerX - width/2, centerY - height/2, 
            width, height
        );
        g2d.draw(bandRect);
        
        // Add semi-transparent fill
        Color fillColor = new Color(
            bandColor.getRed(), 
            bandColor.getGreen(), 
            bandColor.getBlue(), 
            40
        );
        g2d.setColor(fillColor);
        g2d.fill(bandRect);
        
        // Add molecular weight annotation if available
        if (band.has("molecular_weight")) {
            double mw = band.getDouble("molecular_weight");
            String mwLabel = formatMolecularWeight(mw);
            
            g2d.setColor(Color.WHITE);
            Font mwFont = new Font(Font.SANS_SERIF, Font.BOLD, 10);
            g2d.setFont(mwFont);
            
            FontMetrics fm = g2d.getFontMetrics();
            int labelX = (int)(centerX + width/2 + 5);
            int labelY = (int)(centerY + fm.getHeight()/2);
            
            // Draw MW label background
            int labelWidth = fm.stringWidth(mwLabel);
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRect(labelX - 2, labelY - fm.getHeight() + 2, labelWidth + 4, fm.getHeight());
            
            g2d.setColor(Color.YELLOW);
            g2d.drawString(mwLabel, labelX, labelY);
        }
        
        // Add intensity info in headless mode
        if (headless) {
            String intensityInfo = String.format("I=%.2f", intensity);
            g2d.setColor(bandColor);
            Font infoFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
            g2d.setFont(infoFont);
            g2d.drawString(intensityInfo, (int)(centerX - width/2), (int)(centerY - height/2 - 2));
        }
    }
    
    /**
     * Render molecular weight ladder scale
     */
    private static void renderMolecularWeightLadder(Graphics2D g2d, JSONObject gelResult, boolean headless) {
        JSONObject ladder = gelResult.optJSONObject("molecular_weight_ladder");
        if (ladder == null) {
            return;
        }
        
        double r2 = ladder.optDouble("r2", 0);
        JSONArray calibrationPoints = ladder.optJSONArray("calibration_points");
        
        if (calibrationPoints == null || calibrationPoints.length() == 0) {
            return;
        }
        
        // Draw ladder scale on the left side
        int scaleX = 20;
        int scaleTop = 50;
        int scaleHeight = 200;
        
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2.0f));
        
        // Draw scale line
        Line2D.Double scaleLine = new Line2D.Double(scaleX, scaleTop, scaleX, scaleTop + scaleHeight);
        g2d.draw(scaleLine);
        
        // Add calibration point markers
        Font scaleFont = new Font(Font.SANS_SERIF, Font.BOLD, 10);
        g2d.setFont(scaleFont);
        
        for (int i = 0; i < calibrationPoints.length(); i++) {
            JSONObject point = calibrationPoints.getJSONObject(i);
            double mw = point.optDouble("molecular_weight", 0);
            double position = point.optDouble("position", 0);
            
            // Map position to scale
            int markerY = (int)(scaleTop + (position / 100.0) * scaleHeight);
            
            // Draw marker tick
            Line2D.Double tick = new Line2D.Double(scaleX - 5, markerY, scaleX + 5, markerY);
            g2d.draw(tick);
            
            // Draw MW label
            String mwLabel = formatMolecularWeight(mw);
            g2d.setColor(Color.YELLOW);
            g2d.drawString(mwLabel, scaleX + 10, markerY + 3);
        }
        
        // Add R² value
        String r2Label = String.format("R² = %.3f", r2);
        g2d.setColor(r2 > 0.9 ? Color.GREEN : (r2 > 0.8 ? Color.YELLOW : Color.RED));
        g2d.drawString(r2Label, scaleX, scaleTop - 10);
    }
    
    /**
     * Format molecular weight for display
     */
    private static String formatMolecularWeight(double mw) {
        if (mw >= 1000) {
            return String.format("%.0fkDa", mw / 1000);
        } else {
            return String.format("%.0fDa", mw);
        }
    }
    
    /**
     * Create gel analysis summary statistics overlay (headless-safe)
     */
    public static BufferedImage renderSummaryStats(BufferedImage baseImage, JSONObject gelResult, boolean headless) {
        BufferedImage statsImage = new BufferedImage(
            baseImage.getWidth(), 
            baseImage.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        Graphics2D g2d = statsImage.createGraphics();
        try {
            // Draw base image
            g2d.drawImage(baseImage, 0, 0, null);
            
            // Configure text rendering
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font statsFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
            g2d.setFont(statsFont);
            
            // Extract statistics
            int lanesFound = gelResult.optInt("lanes_found", 0);
            int bandsTotal = gelResult.optInt("bands_total", 0);
            JSONObject quantResult = gelResult.optJSONObject("quantification");
            
            // Prepare statistics text
            List<String> statLines = new ArrayList<>();
            statLines.add("Gel Analysis Results:");
            statLines.add("Lanes Detected: " + lanesFound);
            statLines.add("Total Bands: " + bandsTotal);
            
            if (quantResult != null) {
                double ladderR2 = quantResult.optDouble("ladder_r2", 0);
                double backgroundSnr = quantResult.optDouble("background_snr", 0);
                double bandStability = quantResult.optDouble("band_stability", 0);
                
                statLines.add(String.format("Ladder R²: %.3f", ladderR2));
                statLines.add(String.format("Background SNR: %.1f", backgroundSnr));
                statLines.add(String.format("Band Stability: %.3f", bandStability));
            }
            
            // Add analysis type info
            String analysisType = gelResult.optString("analysis_method", "unknown");
            statLines.add("Method: " + analysisType);
            
            // Draw statistics box
            renderStatsBox(g2d, statLines);
            
            return statsImage;
            
        } finally {
            g2d.dispose();
        }
    }
    
    /**
     * Render statistics box in corner of image
     */
    private static void renderStatsBox(Graphics2D g2d, List<String> statLines) {
        if (statLines.isEmpty()) return;
        
        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();
        int maxWidth = 0;
        
        // Calculate box dimensions
        for (String line : statLines) {
            int width = fm.stringWidth(line);
            if (width > maxWidth) maxWidth = width;
        }
        
        int boxWidth = maxWidth + 20;
        int boxHeight = (statLines.size() * lineHeight) + 15;
        
        // Position in top-right corner
        int boxX = g2d.getClipBounds().width - boxWidth - 10;
        int boxY = 10;
        
        // Draw background box
        g2d.setColor(new Color(0, 0, 0, 180)); // Semi-transparent black
        g2d.fillRect(boxX, boxY, boxWidth, boxHeight);
        
        g2d.setColor(Color.WHITE);
        g2d.drawRect(boxX, boxY, boxWidth, boxHeight);
        
        // Draw text lines
        g2d.setColor(Color.WHITE);
        for (int i = 0; i < statLines.size(); i++) {
            String line = statLines.get(i);
            int textX = boxX + 10;
            int textY = boxY + 15 + (i * lineHeight);
            g2d.drawString(line, textX, textY);
        }
    }
}