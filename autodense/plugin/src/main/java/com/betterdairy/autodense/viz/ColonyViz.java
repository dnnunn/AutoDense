package com.betterdairy.autodense.viz;

import com.betterdairy.autodense.model.Models.*;
import org.json.JSONObject;
import org.json.JSONArray;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.ArrayList;

/**
 * Headless-safe colony visualization renderer.
 * 
 * Creates annotated overlay images without touching UI components.
 * Safe for use in headless environments and CLI workflows.
 */
public final class ColonyViz {
    
    /**
     * Render colony analysis overlay on BufferedImage (headless-safe)
     * 
     * @param imagePlus Original image
     * @param colonyResult Colony analysis results with detected colonies
     * @param headless Whether to render in headless mode (no UI dependencies)
     * @return BufferedImage with colony overlays rendered
     */
    public static BufferedImage renderOverlay(ImagePlus imagePlus, JSONObject colonyResult, boolean headless) {
        if (imagePlus == null || colonyResult == null) {
            throw new IllegalArgumentException("Image and colony result cannot be null");
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
            
            // Render colony overlays
            renderColonyOverlays(g2d, colonyResult, headless);
            
            return overlayImage;
            
        } finally {
            g2d.dispose();
        }
    }
    
    /**
     * Render individual colony overlays on Graphics2D context
     */
    private static void renderColonyOverlays(Graphics2D g2d, JSONObject colonyResult, boolean headless) {
        JSONArray colonies = colonyResult.optJSONArray("colonies");
        if (colonies == null) {
            return;
        }
        
        // Set up drawing parameters
        Stroke originalStroke = g2d.getStroke();
        Font originalFont = g2d.getFont();
        
        // Colony outline stroke
        g2d.setStroke(new BasicStroke(2.0f));
        Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        g2d.setFont(labelFont);
        
        try {
            for (int i = 0; i < colonies.length(); i++) {
                JSONObject colony = colonies.getJSONObject(i);
                renderSingleColony(g2d, colony, i + 1, headless);
            }
        } finally {
            // Restore original graphics state
            g2d.setStroke(originalStroke);
            g2d.setFont(originalFont);
        }
    }
    
    /**
     * Render a single colony with appropriate color coding
     */
    private static void renderSingleColony(Graphics2D g2d, JSONObject colony, int colonyId, boolean headless) {
        double centerX = colony.optDouble("center_x", 0);
        double centerY = colony.optDouble("center_y", 0);
        double radius = colony.optDouble("radius", 10);
        double area = colony.optDouble("area", 0);
        double circularity = colony.optDouble("circularity", 0);
        
        // X-gal specific properties
        double blueIndex = colony.optDouble("blue_index", 0);
        boolean isBlue = blueIndex > 0.5; // Threshold for blue colonies
        
        // Color coding based on colony properties
        Color outlineColor;
        Color labelColor;
        
        if (isBlue) {
            outlineColor = new Color(0, 100, 255); // Blue for X-gal positive
            labelColor = Color.CYAN;
        } else {
            // Size-based color coding for neutral colonies
            if (area > 500) {
                outlineColor = new Color(255, 50, 50); // Red for large colonies
                labelColor = Color.RED;
            } else if (area < 100) {
                outlineColor = new Color(255, 165, 0); // Orange for small colonies
                labelColor = Color.ORANGE;
            } else {
                outlineColor = new Color(50, 255, 50); // Green for normal colonies
                labelColor = Color.GREEN;
            }
        }
        
        // Draw colony outline
        g2d.setColor(outlineColor);
        Ellipse2D.Double colonyCircle = new Ellipse2D.Double(
            centerX - radius, centerY - radius, 
            2 * radius, 2 * radius
        );
        g2d.draw(colonyCircle);
        
        // Add semi-transparent fill for better visibility
        Color fillColor = new Color(
            outlineColor.getRed(), 
            outlineColor.getGreen(), 
            outlineColor.getBlue(), 
            30
        );
        g2d.setColor(fillColor);
        g2d.fill(colonyCircle);
        
        // Draw colony ID label
        g2d.setColor(labelColor);
        String label = String.valueOf(colonyId);
        
        // Calculate label position (offset from center)
        FontMetrics fm = g2d.getFontMetrics();
        int labelWidth = fm.stringWidth(label);
        int labelHeight = fm.getHeight();
        
        int labelX = (int)(centerX - labelWidth / 2);
        int labelY = (int)(centerY - radius - 5); // Above the colony
        
        // Draw label background for better readability
        g2d.setColor(new Color(0, 0, 0, 128)); // Semi-transparent black
        g2d.fillRect(labelX - 2, labelY - labelHeight + 2, labelWidth + 4, labelHeight);
        
        // Draw label text
        g2d.setColor(Color.WHITE);
        g2d.drawString(label, labelX, labelY);
        
        // Add size indicator for headless mode (more detailed info)
        if (headless) {
            String sizeInfo = String.format("A=%.0f", area);
            g2d.setColor(labelColor);
            g2d.drawString(sizeInfo, labelX, (int)(centerY + radius + 15));
        }
    }
    
    /**
     * Create summary statistics overlay (headless-safe)
     */
    public static BufferedImage renderSummaryStats(BufferedImage baseImage, JSONObject colonyResult, boolean headless) {
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
            int totalColonies = colonyResult.optInt("colonies_found", 0);
            JSONObject measurements = colonyResult.optJSONObject("measurements");
            
            // Prepare statistics text
            List<String> statLines = new ArrayList<>();
            statLines.add("Colony Analysis Results:");
            statLines.add("Total Colonies: " + totalColonies);
            
            if (measurements != null) {
                double sizeCV = measurements.optDouble("size_cv", 0);
                double touchingFraction = measurements.optDouble("touching_fraction", 0);
                double blueFraction = measurements.optDouble("blue_fraction", 0);
                
                statLines.add(String.format("Size CV: %.2f", sizeCV));
                statLines.add(String.format("Touching: %.1f%%", touchingFraction * 100));
                
                if (blueFraction > 0) {
                    statLines.add(String.format("Blue Colonies: %.1f%%", blueFraction * 100));
                }
            }
            
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