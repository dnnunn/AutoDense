package com.betterdairy.autodense.analysis;

import java.awt.Rectangle;
import java.time.LocalDateTime;

/**
 * Represents the quantification results for a single band using proven Fiji algorithms.
 * Based on _BandPeakQuantification.ijm by Kenji OHGANE (University of Tokyo).
 * 
 * Core formula: signal = area × (mean_intensity - background_intensity)
 * This is the gold standard used in Image Studio Lite and academic research.
 */
public class BandQuantification {
    
    public enum BackgroundRegion {
        ALL,         // Expands uniformly around ROI - universal method
        TOP_BOTTOM,  // Above/below ROI - optimal for horizontal bands
        SIDES        // Left/right of ROI - optimal for vertical lanes
    }
    
    public enum BackgroundMethod {
        MEDIAN,      // More robust to noise and outliers  
        MEAN         // Standard average - faster computation
    }
    
    public enum ChannelWeights {
        RED_ONLY(1.0, 0.0, 0.0),
        GREEN_ONLY(0.0, 1.0, 0.0),
        BLUE_ONLY(0.0, 0.0, 1.0),
        EQUAL_RGB(1.0/3.0, 1.0/3.0, 1.0/3.0),
        LUMINANCE(0.299, 0.587, 0.114);  // ImageJ standard
        
        private final double red, green, blue;
        
        ChannelWeights(double r, double g, double b) {
            this.red = r; 
            this.green = g; 
            this.blue = b;
        }
        
        public double getRed() { return red; }
        public double getGreen() { return green; }
        public double getBlue() { return blue; }
    }
    
    // Core quantification results (Fiji standard)
    private final double signal;           // area * (mean - background) - THE KEY MEASUREMENT
    private final double total;            // area * mean - total intensity
    private final double area;             // ROI area in pixels
    private final double mean;             // mean intensity within ROI
    private final double background;       // background intensity (median or mean)
    
    // Methodology metadata
    private final BackgroundRegion backgroundRegion;
    private final BackgroundMethod backgroundMethod;
    private final int expansionPixels;
    private final ChannelWeights channelWeights;
    
    // ROI information
    private final Rectangle bounds;        // ROI bounding box
    private final String roiName;          // ROI identifier
    private final String laneId;           // Lane identifier
    private final String bandId;           // Band identifier within lane
    
    // Analysis metadata
    private final LocalDateTime timestamp;
    private final boolean scaleReset;
    
    public BandQuantification(double signal, double total, double area, double mean, 
                            double background, BackgroundRegion backgroundRegion,
                            BackgroundMethod backgroundMethod, int expansionPixels,
                            ChannelWeights channelWeights, Rectangle bounds, 
                            String roiName, String laneId, String bandId, 
                            boolean scaleReset) {
        this.signal = signal;
        this.total = total;
        this.area = area;
        this.mean = mean;
        this.background = background;
        this.backgroundRegion = backgroundRegion;
        this.backgroundMethod = backgroundMethod;
        this.expansionPixels = expansionPixels;
        this.channelWeights = channelWeights;
        this.bounds = new Rectangle(bounds);  // Defensive copy
        this.roiName = roiName;
        this.laneId = laneId;
        this.bandId = bandId;
        this.scaleReset = scaleReset;
        this.timestamp = LocalDateTime.now();
    }
    
    // Core measurement getters
    public double getSignal() { return signal; }
    public double getTotal() { return total; }
    public double getArea() { return area; }
    public double getMean() { return mean; }
    public double getBackground() { return background; }
    
    // Derived measurements
    public double getSignalToNoiseRatio() {
        return background > 0 ? (mean - background) / background : Double.POSITIVE_INFINITY;
    }
    
    public double getBackgroundSubtractedMean() {
        return mean - background;
    }
    
    public double getSignalPerArea() {
        return area > 0 ? signal / area : 0;
    }
    
    // Methodology metadata getters
    public BackgroundRegion getBackgroundRegion() { return backgroundRegion; }
    public BackgroundMethod getBackgroundMethod() { return backgroundMethod; }
    public int getExpansionPixels() { return expansionPixels; }
    public ChannelWeights getChannelWeights() { return channelWeights; }
    
    // ROI information getters  
    public Rectangle getBounds() { return new Rectangle(bounds); }  // Defensive copy
    public String getRoiName() { return roiName; }
    public String getLaneId() { return laneId; }
    public String getBandId() { return bandId; }
    
    // Analysis metadata getters
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean wasScaleReset() { return scaleReset; }
    
    /**
     * Returns a comprehensive string representation for debugging and export
     */
    @Override
    public String toString() {
        return String.format(
            "BandQuantification{lane=%s, band=%s, signal=%.3f, total=%.3f, " +
            "area=%.1f, mean=%.3f, background=%.3f (method=%s, region=%s, expansion=%dpx), " +
            "SNR=%.2f, bounds=(%d,%d,%d,%d), timestamp=%s}",
            laneId, bandId, signal, total, area, mean, background,
            backgroundMethod, backgroundRegion, expansionPixels,
            getSignalToNoiseRatio(), bounds.x, bounds.y, bounds.width, bounds.height,
            timestamp
        );
    }
    
    /**
     * Creates a CSV header line compatible with Fiji exports
     */
    public static String getCsvHeader() {
        return "Lane,Band,Signal,Total,Area,Mean,Background,Background_Method," +
               "Background_Region,Expansion_Pixels,Channel_Weights,SNR," +
               "ROI_X,ROI_Y,ROI_Width,ROI_Height,Scale_Reset,Timestamp";
    }
    
    /**
     * Returns a CSV line for this quantification result
     */
    public String toCsvLine() {
        return String.format("%s,%s,%.6f,%.6f,%.3f,%.6f,%.6f,%s,%s,%d,%s,%.3f," +
                           "%d,%d,%d,%d,%s,%s",
                           laneId != null ? laneId : "",
                           bandId != null ? bandId : "",
                           signal, total, area, mean, background,
                           backgroundMethod, backgroundRegion, expansionPixels,
                           channelWeights != null ? channelWeights : "NONE",
                           getSignalToNoiseRatio(),
                           bounds.x, bounds.y, bounds.width, bounds.height,
                           scaleReset, timestamp);
    }
    
    /**
     * Validates that the quantification results are scientifically reasonable
     */
    public boolean isValid() {
        return area > 0 && 
               !Double.isNaN(signal) && !Double.isInfinite(signal) &&
               !Double.isNaN(total) && !Double.isInfinite(total) &&
               !Double.isNaN(mean) && !Double.isInfinite(mean) &&
               !Double.isNaN(background) && !Double.isInfinite(background) &&
               bounds.width > 0 && bounds.height > 0;
    }
}