package com.betterdairy.autodense.util;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.gui.Roi;
import ij.gui.OvalRoi;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Generate synthetic test images with known ground truth for algorithm validation.
 * 
 * Following the 15-minute triage guide: "Run your pipeline on a synthetic image you control.
 * If it detects there, your parameters/preprocessing are off for the real data."
 */
public final class SyntheticImageGenerator {
    
    private static final Logger logger = Logger.getLogger(SyntheticImageGenerator.class.getName());
    
    /**
     * Generate synthetic EtBr agarose gel with known lanes and bands
     */
    public static ImagePlus generateEtBrGel(int width, int height, int numLanes, int bandsPerLane) {
        FloatProcessor fp = new FloatProcessor(width, height);
        
        // Dark background (EtBr gels are typically dark with bright bands)
        float backgroundValue = 30.0f;
        float bandValue = 220.0f;
        
        // Fill background
        fp.setValue(backgroundValue);
        fp.fill();
        
        // Calculate lane positions
        int laneWidth = width / (numLanes + 1);
        int laneSpacing = laneWidth;
        
        logger.info(String.format("Generating synthetic EtBr gel: %dx%d, %d lanes, %d bands/lane", 
            width, height, numLanes, bandsPerLane));
        
        Random rand = new Random(42); // Fixed seed for reproducible results
        
        for (int lane = 0; lane < numLanes; lane++) {
            int laneX = laneSpacing + (lane * laneSpacing);
            int actualLaneWidth = laneWidth - 10; // Leave some margin
            
            // Generate bands at different Y positions
            for (int band = 0; band < bandsPerLane; band++) {
                // Distribute bands across the gel height
                int bandY = (height / (bandsPerLane + 1)) * (band + 1);
                int bandHeight = 8 + rand.nextInt(6); // Variable band thickness
                int bandWidth = actualLaneWidth - 4; // Slightly narrower than lane
                
                // Add some intensity variation to bands
                float actualBandValue = bandValue - rand.nextFloat() * 30.0f;
                
                // Draw rectangular band
                for (int y = bandY - bandHeight/2; y <= bandY + bandHeight/2; y++) {
                    for (int x = laneX - bandWidth/2; x <= laneX + bandWidth/2; x++) {
                        if (x >= 0 && x < width && y >= 0 && y < height) {
                            fp.setf(x, y, actualBandValue);
                        }
                    }
                }
            }
        }
        
        // Add subtle lane guides (faint vertical lines)
        for (int lane = 0; lane < numLanes; lane++) {
            int laneX = laneSpacing + (lane * laneSpacing);
            for (int y = 0; y < height; y++) {
                if (laneX >= 0 && laneX < width) {
                    float currentValue = fp.getf(laneX, y);
                    // Only draw lane guide in background areas
                    if (Math.abs(currentValue - backgroundValue) < 5.0f) {
                        fp.setf(laneX, y, backgroundValue + 15.0f);
                    }
                }
            }
        }
        
        ImagePlus result = new ImagePlus("Synthetic_EtBr_Gel", fp);
        result.getProcessor().setMinAndMax(0, 255);
        
        logger.info("Synthetic EtBr gel generated successfully");
        return result;
    }
    
    /**
     * Generate synthetic SDS-PAGE gel with protein ladder pattern
     */
    public static ImagePlus generateSdsPageGel(int width, int height, int numLanes) {
        FloatProcessor fp = new FloatProcessor(width, height);
        
        // Light background (SDS-PAGE often has light background with dark bands)
        float backgroundValue = 200.0f;
        float bandValue = 50.0f;
        
        fp.setValue(backgroundValue);
        fp.fill();
        
        int laneWidth = width / (numLanes + 1);
        int laneSpacing = laneWidth;
        
        logger.info(String.format("Generating synthetic SDS-PAGE gel: %dx%d, %d lanes", 
            width, height, numLanes));
        
        Random rand = new Random(123);
        
        // Standard protein ladder molecular weights (approximate positions)
        double[] ladderWeights = {250, 150, 100, 75, 50, 37, 25, 20, 15, 10}; // kDa
        
        for (int lane = 0; lane < numLanes; lane++) {
            int laneX = laneSpacing + (lane * laneSpacing);
            int actualLaneWidth = laneWidth - 10;
            
            // First lane is molecular weight ladder
            if (lane == 0) {
                for (int i = 0; i < ladderWeights.length; i++) {
                    // Position bands logarithmically (higher MW = higher position)
                    double logPos = Math.log(ladderWeights[i] / 10.0) / Math.log(25.0); // Normalize to 0-1
                    int bandY = (int) (height * 0.1 + logPos * height * 0.8);
                    int bandHeight = 4 + rand.nextInt(3);
                    
                    drawRectangularBand(fp, laneX, bandY, actualLaneWidth - 2, bandHeight, bandValue);
                }
            } else {
                // Sample lanes with fewer bands
                int bandsInSample = 3 + rand.nextInt(4);
                for (int band = 0; band < bandsInSample; band++) {
                    int bandY = height/6 + rand.nextInt(2 * height/3);
                    int bandHeight = 5 + rand.nextInt(4);
                    float intensity = bandValue + rand.nextFloat() * 30.0f;
                    
                    drawRectangularBand(fp, laneX, bandY, actualLaneWidth, bandHeight, intensity);
                }
            }
        }
        
        ImagePlus result = new ImagePlus("Synthetic_SDS_PAGE", fp);
        result.getProcessor().setMinAndMax(0, 255);
        
        logger.info("Synthetic SDS-PAGE gel generated successfully");
        return result;
    }
    
    /**
     * Generate synthetic colony plate with controlled colony distribution
     */
    public static ImagePlus generateColonyPlate(int width, int height, int numColonies) {
        FloatProcessor fp = new FloatProcessor(width, height);
        
        // Light agar background
        float backgroundValue = 180.0f;
        float colonyValue = 80.0f;
        
        fp.setValue(backgroundValue);
        fp.fill();
        
        logger.info(String.format("Generating synthetic colony plate: %dx%d, %d colonies", 
            width, height, numColonies));
        
        Random rand = new Random(456);
        
        // Generate non-overlapping circular colonies
        for (int i = 0; i < numColonies; i++) {
            int attempts = 0;
            boolean placed = false;
            
            while (!placed && attempts < 100) {
                int colonyX = 50 + rand.nextInt(width - 100);
                int colonyY = 50 + rand.nextInt(height - 100);
                int radius = 15 + rand.nextInt(10);
                
                // Simple collision detection - check if area is clear
                boolean canPlace = true;
                for (int checkY = colonyY - radius - 5; checkY <= colonyY + radius + 5; checkY++) {
                    for (int checkX = colonyX - radius - 5; checkX <= colonyX + radius + 5; checkX++) {
                        if (checkX >= 0 && checkX < width && checkY >= 0 && checkY < height) {
                            if (Math.abs(fp.getf(checkX, checkY) - backgroundValue) > 10) {
                                canPlace = false;
                                break;
                            }
                        }
                    }
                    if (!canPlace) break;
                }
                
                if (canPlace) {
                    drawCircularColony(fp, colonyX, colonyY, radius, colonyValue + rand.nextFloat() * 40.0f);
                    placed = true;
                }
                attempts++;
            }
        }
        
        ImagePlus result = new ImagePlus("Synthetic_Colony_Plate", fp);
        result.getProcessor().setMinAndMax(0, 255);
        
        logger.info("Synthetic colony plate generated successfully");
        return result;
    }
    
    /**
     * Helper method to draw rectangular band
     */
    private static void drawRectangularBand(FloatProcessor fp, int centerX, int centerY, 
                                          int width, int height, float value) {
        for (int y = centerY - height/2; y <= centerY + height/2; y++) {
            for (int x = centerX - width/2; x <= centerX + width/2; x++) {
                if (x >= 0 && x < fp.getWidth() && y >= 0 && y < fp.getHeight()) {
                    fp.setf(x, y, value);
                }
            }
        }
    }
    
    /**
     * Helper method to draw circular colony
     */
    private static void drawCircularColony(FloatProcessor fp, int centerX, int centerY, 
                                         int radius, float value) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (x >= 0 && x < fp.getWidth() && y >= 0 && y < fp.getHeight()) {
                    double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
                    if (distance <= radius) {
                        // Create slight gradient from center to edge
                        float intensity = value + (float) (20.0 * (1.0 - distance / radius));
                        fp.setf(x, y, intensity);
                    }
                }
            }
        }
    }
}