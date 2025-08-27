package com.betterdairy.autodense.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.geom.AffineTransform;

/**
 * Pure functional plate detection for agar plates.
 * Uses deterministic ImageJ pipeline: Auto Threshold → Fill Holes → Analyze Particles.
 */
public final class PlateDetector {
    
    /**
     * Result of plate detection with all computed parameters
     */
    public static record Result(
        double centerX,
        double centerY, 
        double radiusPx,
        OvalRoi plateRoi,
        double largestParticleArea,
        boolean illuminationCorrected,
        double majorAxis,
        double minorAxis,
        double axisRatio,
        double angle,
        boolean wasDeskewed,
        AffineTransform deskewTransform
    ) {
        public double pxPerMM(double dishDiameterMM) {
            return (2.0 * radiusPx) / dishDiameterMM;
        }
        
        public OvalRoi rimMask(double rimWidthMM) {
            double pxPerMM = pxPerMM(90.0); // Default dish size
            double innerRadius = radiusPx - (rimWidthMM * pxPerMM);
            return new OvalRoi(
                centerX - innerRadius, centerY - innerRadius,
                2 * innerRadius, 2 * innerRadius
            );
        }
    }
    
    /**
     * Core plate detection: Threshold → biggest particle → ROI → ellipse fit → deskew if needed
     */
    public static Result detect(ImagePlus image) {
        return detect(image, "Triangle");
    }
    
    /**
     * Detect plate with configurable threshold method
     */
    public static Result detect(ImagePlus image, String thresholdMethod) {
        
        ImagePlus working = image.duplicate();
        working.setTitle("plate_detection_" + System.currentTimeMillis());
        
        try {
            // Step 1: Convert to grayscale if needed
            if (working.getNChannels() > 1) {
                IJ.run(working, "RGB to Luminance", "");
            }
            
            // Step 2: Threshold → Fill Holes → Analyze Particles
            IJ.setAutoThreshold(working, thresholdMethod + " dark");
            IJ.run(working, "Fill Holes", "");
            
            // Set measurements to include fit ellipse
            IJ.run(working, "Set Measurements...", "area centroid fit display");
            IJ.run(working, "Analyze Particles...", "size=1000-Infinity show=Nothing display clear");
            
            // Step 3: Get biggest particle
            ResultsTable rt = ResultsTable.getResultsTable();
            if (rt == null || rt.getCounter() == 0) {
                throw new RuntimeException("No plate found with threshold method: " + thresholdMethod);
            }
            
            // Find largest particle
            int bestIndex = 0;
            double maxArea = 0;
            for (int i = 0; i < rt.getCounter(); i++) {
                double area = rt.getValue("Area", i);
                if (area > maxArea) {
                    maxArea = area;
                    bestIndex = i;
                }
            }
            
            // Get ellipse fit parameters for largest particle
            double centerX = rt.getValue("X", bestIndex);
            double centerY = rt.getValue("Y", bestIndex);
            double majorAxis = rt.getValue("Major", bestIndex);
            double minorAxis = rt.getValue("Minor", bestIndex);
            double angle = rt.getValue("Angle", bestIndex);
            
            // Calculate axis ratio and check if deskewing is needed
            double axisRatio = minorAxis / majorAxis;
            boolean needsDeskew = axisRatio < 0.95;
            
            AffineTransform deskewTransform = null;
            ImagePlus processedImage = working;
            
            // Step 4: DISABLED - Deskew feature not properly implemented
            if (needsDeskew) {
                throw new UnsupportedOperationException(
                    "Plate deskewing is disabled due to incomplete implementation. " +
                    "The affine transform stub causes inaccurate measurements. " +
                    "Axis ratio: " + String.format("%.3f", axisRatio) + " (threshold: 0.95). " +
                    "Use images with minimal perspective distortion or implement proper affine transforms.");
            }
            
            // Step 5: Create final ROI
            double radius = Math.sqrt(maxArea / Math.PI);
            OvalRoi plateRoi = new OvalRoi(
                centerX - radius, centerY - radius,
                2 * radius, 2 * radius
            );
            plateRoi.setName("Plate");
            
            return new Result(
                centerX, centerY, radius, plateRoi, maxArea,
                false, // illumination correction not used in core detector
                majorAxis, minorAxis, axisRatio, angle,
                needsDeskew, deskewTransform
            );
            
        } finally {
            working.close();
            if (working != image) {
                // Clean up any additional working images
            }
        }
    }
    
    /**
     * DISABLED: Create affine transform to deskew elliptical plate to circular
     * @deprecated Incomplete implementation causes measurement errors
     */
    @Deprecated
    private static AffineTransform createDeskewTransform(double centerX, double centerY, double angleRad) {
        throw new UnsupportedOperationException(
            "createDeskewTransform is disabled due to incomplete implementation. " +
            "The current transform logic does not properly handle perspective correction.");
    }
    
    /**
     * DISABLED: Apply deskew transform to image
     * @deprecated Stub implementation returns original image, causing measurement errors
     */
    @Deprecated
    private static ImagePlus applyDeskewTransform(ImagePlus image, AffineTransform transform) {
        throw new UnsupportedOperationException(
            "applyDeskewTransform is disabled due to stub implementation. " +
            "The method was returning the original image unchanged, " +
            "causing inaccurate plate measurements. Implement proper ImageJ affine transforms.");
    }
    
    /**
     * Perform illumination correction using Gaussian blur background subtraction
     */
    private static boolean performIlluminationCorrection(ImagePlus image, double sigma) {
        try {
            // Create background estimate
            ImagePlus background = image.duplicate();
            GaussianBlur blur = new GaussianBlur();
            blur.blurGaussian(background.getProcessor(), sigma, sigma, 0.01);
            
            // Divide original by background
            ImageCalculator calc = new ImageCalculator();
            ImagePlus corrected = calc.run("Divide create 32-bit", image, background);
            
            // Replace original with corrected
            image.setProcessor(corrected.getProcessor().convertToByte(true));
            
            background.close();
            corrected.close();
            
            return true;
        } catch (Exception e) {
            // Fail silently - continue without correction
            return false;
        }
    }
}