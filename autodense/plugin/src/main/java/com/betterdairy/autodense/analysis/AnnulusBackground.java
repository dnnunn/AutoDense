package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Canonical helper for annulus background subtraction per well.
 * Estimates local background from ring outside each well and subtracts.
 */
public final class AnnulusBackground {
    
    private AnnulusBackground() {} // Utility class
    
    /**
     * Subtract annulus background from each well region.
     * 
     * @param imp Source image
     * @param wellCenters Array of well center coordinates [x1,y1, x2,y2, ...]
     * @param wellRadius Radius of wells in pixels
     * @param ringThickness Thickness of background ring in pixels
     * @return Background-corrected image
     */
    public static ImagePlus subtractAnnulusBackground(ImagePlus imp, 
                                                     int[] wellCenters, 
                                                     int wellRadius, 
                                                     int ringThickness) {
        ImageProcessor ip = imp.getProcessor();
        FloatProcessor corrected = ip.convertToFloatProcessor();
        
        // Process each well
        for (int i = 0; i < wellCenters.length; i += 2) {
            int centerX = wellCenters[i];
            int centerY = wellCenters[i + 1];
            
            // Calculate background from annulus
            double background = calculateAnnulusBackground(ip, centerX, centerY, 
                                                         wellRadius, ringThickness);
            
            // Subtract background from well region
            subtractFromWell(corrected, centerX, centerY, wellRadius, background);
        }
        
        ImagePlus result = new ImagePlus(imp.getTitle() + "_bg_corrected", corrected);
        result.setCalibration(imp.getCalibration());
        return result;
    }
    
    /**
     * Calculate background intensity from annulus ring around well.
     */
    private static double calculateAnnulusBackground(ImageProcessor ip, 
                                                   int centerX, int centerY,
                                                   int wellRadius, int ringThickness) {
        ArrayList<Float> ringPixels = new ArrayList<>();
        
        int innerRadius = wellRadius + 2; // Small gap to avoid well edge artifacts
        int outerRadius = innerRadius + ringThickness;
        
        // Sample pixels in annulus ring
        for (int y = centerY - outerRadius; y <= centerY + outerRadius; y++) {
            for (int x = centerX - outerRadius; x <= centerX + outerRadius; x++) {
                if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                    double distance = Math.sqrt((x - centerX) * (x - centerX) + 
                                              (y - centerY) * (y - centerY));
                    
                    if (distance >= innerRadius && distance <= outerRadius) {
                        ringPixels.add(ip.getf(x, y));
                    }
                }
            }
        }
        
        // Use median for robust background estimation
        if (ringPixels.isEmpty()) {
            return 0.0; // Fallback if no valid ring pixels
        }
        
        Collections.sort(ringPixels);
        int medianIndex = ringPixels.size() / 2;
        return ringPixels.get(medianIndex);
    }
    
    /**
     * Subtract background value from all pixels within well radius.
     */
    private static void subtractFromWell(FloatProcessor fp, 
                                       int centerX, int centerY, 
                                       int wellRadius, double background) {
        for (int y = centerY - wellRadius; y <= centerY + wellRadius; y++) {
            for (int x = centerX - wellRadius; x <= centerX + wellRadius; x++) {
                if (x >= 0 && x < fp.getWidth() && y >= 0 && y < fp.getHeight()) {
                    double distance = Math.sqrt((x - centerX) * (x - centerX) + 
                                              (y - centerY) * (y - centerY));
                    
                    if (distance <= wellRadius) {
                        float currentValue = fp.getf(x, y);
                        float correctedValue = Math.max(0f, currentValue - (float)background);
                        fp.setf(x, y, correctedValue);
                    }
                }
            }
        }
    }
    
    /**
     * Estimate background using median absolute deviation (MAD).
     * More robust than standard deviation for outlier-resistant thresholding.
     */
    public static double estimateBackgroundMAD(ImageProcessor ip, Rectangle roi) {
        return estimateBackgroundMAD(ip, roi, 2.5); // Default k=2.5
    }
    
    public static double estimateBackgroundMAD(ImageProcessor ip, Rectangle roi, double kMultiplier) {
        ArrayList<Float> pixels = new ArrayList<>();
        
        // Sample pixels from ROI
        for (int y = roi.y; y < roi.y + roi.height; y++) {
            for (int x = roi.x; x < roi.x + roi.width; x++) {
                if (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight()) {
                    pixels.add(ip.getf(x, y));
                }
            }
        }
        
        if (pixels.isEmpty()) return 0.0;
        
        Collections.sort(pixels);
        float median = pixels.get(pixels.size() / 2);
        
        // Calculate median absolute deviation
        ArrayList<Float> deviations = new ArrayList<>();
        for (float pixel : pixels) {
            deviations.add(Math.abs(pixel - median));
        }
        
        Collections.sort(deviations);
        float mad = deviations.get(deviations.size() / 2);
        
        return median + kMultiplier * mad; // FIXED: YAML-controlled k multiplier
    }
}