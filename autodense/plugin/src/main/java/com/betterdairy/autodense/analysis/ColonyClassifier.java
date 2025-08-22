package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pure functional colony classification using Lab color space analysis.
 * Implements X-gal detection and k-means clustering.
 */
public final class ColonyClassifier {
    
    /**
     * Lab color space representation
     */
    public static record LabColor(double L, double a, double b) {
        public double deltaE(LabColor other) {
            double dL = this.L - other.L;
            double da = this.a - other.a; 
            double db = this.b - other.b;
            return Math.sqrt(dL*dL + da*da + db*db);
        }
    }
    
    /**
     * Classification result with confidence
     */
    public static record Classification(
        String label,
        double confidence,
        LabColor colonyColor,
        LabColor backgroundColor,
        double bDelta,
        double snrL
    ) {}
    
    /**
     * Comprehensive feature extraction result for a single colony
     */
    public static record ColonyFeatures(
        // Basic identification  
        int colonyId,
        double xMm,
        double yMm,
        double eqDiamMm,
        
        // Lab color measurements
        double L,
        double a,
        double b,
        double LBg,
        double aBg,
        double bBg,
        
        // Derived features
        double bDelta,
        double dEBg,
        double snrL,
        
        // Classifications
        String xgalBinary,    // pos, neg, uncertain
        String xgalGrade,     // light, medium, dark, null
        String sizeBin,       // tiny, small, medium, large
        String label,         // combined: e.g., "dark+large", "neg+small"
        double confidence     // 0-1 sigmoid of |bΔ| and SNR
    ) {}
    
    /**
     * Semi-quantitative X-gal color thresholds
     */
    public static record XGalColorThresholds(
        double bDeltaPos,           // Binary positive threshold (e.g., -6.0)
        double bDeltaDark,          // Dark blue threshold (e.g., -16.0)
        double bDeltaMedium,        // Medium blue threshold (e.g., -10.0)
        double minDE,               // Minimum color difference (e.g., 8.0)
        double minSnrL              // Minimum signal-to-noise ratio (e.g., 2.5)
    ) {
        public static XGalColorThresholds defaults() {
            return new XGalColorThresholds(-6.0, -16.0, -10.0, 8.0, 2.5);
        }
    }
    
    /**
     * Extract comprehensive features for all colonies
     */
    public static List<ColonyFeatures> extractFeatures(ImagePlus image, List<Colony> colonies, 
                                                       OvalRoi plateRoi, double pxPerMM, 
                                                       double[] sizeEdgesMM, XGalColorThresholds thresholds) {
        ImageProcessor proc = image.getProcessor();
        List<ColonyFeatures> features = new ArrayList<>();
        
        for (int i = 0; i < colonies.size(); i++) {
            Colony colony = colonies.get(i);
            double colonyRadius = colony.diameter() / 2.0;
            
            // Convert pixel coordinates to mm
            double xMm = colony.x() / pxPerMM;
            double yMm = colony.y() / pxPerMM;
            double eqDiamMm = colony.diameter() / pxPerMM;
            
            // Sample colony interior and background ring
            LabColor colonyLab = sampleColonyInterior(proc, colony.x(), colony.y(), colonyRadius * 0.7);
            LabColor backgroundLab = sampleBackgroundRing(proc, colony.x(), colony.y(), 
                                                         colonyRadius * 1.2, colonyRadius * 1.8);
            
            // Calculate derived features
            double bDelta = colonyLab.b - backgroundLab.b;
            double dEBg = calculateDeltaE76(colonyLab, backgroundLab);
            double snrL = calculateSNR(colonyLab.L, backgroundLab.L);
            
            // Perform classifications
            String xgalBinary = classifyXGalBinary(bDelta, dEBg, snrL, thresholds);
            String xgalGrade = classifyXGalGrade(bDelta, xgalBinary, thresholds);
            String sizeBin = classifySizeBin(eqDiamMm, sizeEdgesMM);
            
            // Create combined label
            String gradeOrBinary = (xgalGrade != null && !"null".equals(xgalGrade)) ? xgalGrade : xgalBinary;
            String label = gradeOrBinary + "+" + sizeBin;
            
            // Calculate confidence using sigmoid of |bΔ| and SNR
            double confidence = calculateConfidenceScore(bDelta, dEBg, snrL);
            
            ColonyFeatures feature = new ColonyFeatures(
                i + 1, xMm, yMm, eqDiamMm,
                colonyLab.L, colonyLab.a, colonyLab.b,
                backgroundLab.L, backgroundLab.a, backgroundLab.b,
                bDelta, dEBg, snrL,
                xgalBinary, xgalGrade, sizeBin, label, confidence
            );
            
            features.add(feature);
        }
        
        return features;
    }
    
    /**
     * Classify X-gal binary (pos/neg/uncertain)
     */
    private static String classifyXGalBinary(double bDelta, double dEBg, double snrL, XGalColorThresholds thresholds) {
        // Check for ambiguous cases first
        if (isAmbiguousCase(bDelta, dEBg, snrL, thresholds)) {
            return "uncertain";
        }
        
        // Binary classification
        boolean isPositive = (bDelta < thresholds.bDeltaPos()) && 
                           (dEBg >= thresholds.minDE()) && 
                           (snrL >= thresholds.minSnrL());
        
        return isPositive ? "pos" : "neg";
    }
    
    /**
     * Classify X-gal grade (light/medium/dark/null)
     */
    private static String classifyXGalGrade(double bDelta, String xgalBinary, XGalColorThresholds thresholds) {
        if (!"pos".equals(xgalBinary)) {
            return "null";  // Only grade positive colonies
        }
        
        if (bDelta <= thresholds.bDeltaDark()) {
            return "dark";
        } else if (bDelta <= thresholds.bDeltaMedium()) {
            return "medium";
        } else {
            return "light";
        }
    }
    
    /**
     * Classify size bin based on equivalent diameter
     */
    private static String classifySizeBin(double eqDiamMm, double[] sizeEdgesMM) {
        for (int i = 0; i < sizeEdgesMM.length; i++) {
            if (eqDiamMm <= sizeEdgesMM[i]) {
                return getSizeBinName(i);
            }
        }
        return getSizeBinName(sizeEdgesMM.length); // Largest bin
    }
    
    /**
     * Get size bin name by index
     */
    private static String getSizeBinName(int binIndex) {
        return switch (binIndex) {
            case 0 -> "tiny";
            case 1 -> "small";
            case 2 -> "medium"; 
            case 3 -> "large";
            default -> "xl";
        };
    }
    
    /**
     * Calculate confidence score using sigmoid of |bΔ| and SNR
     */
    private static double calculateConfidenceScore(double bDelta, double dEBg, double snrL) {
        // Combine |bΔ| and SNR into a single confidence metric
        double absBDelta = Math.abs(bDelta);
        
        // Sigmoid transformation: more extreme bΔ and higher SNR = higher confidence
        double score = (absBDelta / 10.0) + (snrL / 5.0);  // Normalize and combine
        double sigmoid = 1.0 / (1.0 + Math.exp(-score + 2.0));  // Sigmoid centered at score=2
        
        // Clip to [0, 1] range
        return Math.max(0.0, Math.min(1.0, sigmoid));
    }
    
    /**
     * Classify colonies using Lab color analysis with background ring sampling
     */
    public static void classifyLab(ImagePlus image, List<Colony> colonies, 
                                  OvalRoi plateRoi, String mode) {
        classifyLab(image, colonies, plateRoi, mode, false, null);
    }
    
    /**
     * Classify colonies with optional auto-calibration
     */
    public static void classifyLab(ImagePlus image, List<Colony> colonies, 
                                  OvalRoi plateRoi, String mode, boolean autoCalibrate,
                                  XGalColorThresholds customThresholds) {
        
        if ("xgal".equals(mode)) {
            XGalColorThresholds thresholds = customThresholds;
            
            if (autoCalibrate) {
                thresholds = autoCalibateThresholds(image, colonies, plateRoi);
            } else if (thresholds == null) {
                thresholds = XGalColorThresholds.defaults();
            }
            
            classifyXGalWithRules(image, colonies, plateRoi, thresholds);
        } else if ("kmeans".equals(mode)) {
            classifyKMeansInAbSpace(image, colonies, plateRoi, 3);
        }
    }
    
    /**
     * Semi-quantitative X-gal classification with color grading
     * 
     * Per-plate normalization using CIE Lab with local background ring sampling.
     * Binary classification (X-gal+ vs neg) followed by grading positives into 
     * light/medium/dark based on Δb* magnitude with ΔE and SNR guards.
     */
    public static void classifyXGalSemiQuant(ImagePlus image, List<Colony> colonies, 
                                           OvalRoi plateRoi, XGalColorThresholds thresholds) {
        ImageProcessor proc = image.getProcessor();
        
        for (Colony colony : colonies) {
            double colonyRadius = colony.diameter() / 2.0;
            
            // Sample colony interior (central region)
            LabColor colonyLab = sampleColonyInterior(proc, colony.x(), colony.y(), colonyRadius * 0.7);
            
            // Sample background ring (annulus around colony)  
            LabColor backgroundLab = sampleBackgroundRing(proc, colony.x(), colony.y(),
                                                         colonyRadius * 1.2, colonyRadius * 1.8);
            
            // Calculate features as specified
            double bDelta = colonyLab.b() - backgroundLab.b();  // bΔ_i = b*_colony - b*_bg
            double dE = calculateDeltaE76(colonyLab, backgroundLab);  // dE_i = ΔE76(Lab_colony, Lab_bg)
            double snrL = calculateSNR(colonyLab.L(), backgroundLab.L());  // SNR_L_i from L* annulus vs interior
            
            String label;
            double confidence;
            
            // Step 1: Binary X-gal classification
            // X-gal positive: bΔ_i < b_delta_pos AND dE_i ≥ min_dE AND SNR_L_i ≥ min_snr_L
            boolean isXGalPositive = (bDelta < thresholds.bDeltaPos()) && 
                                    (dE >= thresholds.minDE()) && 
                                    (snrL >= thresholds.minSnrL());
            
            if (isXGalPositive) {
                // Step 2: Grade positives into light/medium/dark
                if (bDelta <= thresholds.bDeltaDark()) {
                    // dark if bΔ_i ≤ b_delta_dark
                    label = "xgal_dark";
                    confidence = calculateColorGradingConfidence(bDelta, dE, snrL, "dark");
                } else if (bDelta <= thresholds.bDeltaMedium()) {
                    // medium if b_delta_dark < bΔ_i ≤ b_delta_medium
                    label = "xgal_medium";
                    confidence = calculateColorGradingConfidence(bDelta, dE, snrL, "medium");
                } else {
                    // light if b_delta_medium < bΔ_i < b_delta_pos
                    label = "xgal_light";
                    confidence = calculateColorGradingConfidence(bDelta, dE, snrL, "light");
                }
            } else {
                // Check for ambiguous cases
                if (isAmbiguousCase(bDelta, dE, snrL, thresholds) || isVerySmall(colony)) {
                    // Ambiguous: falls near thresholds or SNR low
                    label = "xgal_uncertain";
                    confidence = 0.3;
                } else {
                    // Negatives: xgal_neg
                    label = "xgal_neg";
                    confidence = calculateXGalNegativeConfidence(bDelta, dE, snrL);
                }
            }
            
            // Store enhanced classification result
            // Note: Colony record would need to be extended with color classification fields
            // For now, classification result is available via the label string
        }
    }
    
    /**
     * X-gal classification using enhanced rules with background ring sampling
     */
    private static void classifyXGalWithRules(ImagePlus image, List<Colony> colonies, OvalRoi plateRoi) {
        classifyXGalWithRules(image, colonies, plateRoi, XGalColorThresholds.defaults());
    }
    
    /**
     * X-gal classification with custom thresholds
     */
    private static void classifyXGalWithRules(ImagePlus image, List<Colony> colonies, 
                                            OvalRoi plateRoi, XGalColorThresholds thresholds) {
        ImageProcessor proc = image.getProcessor();
        
        for (Colony colony : colonies) {
            double colonyRadius = colony.diameter() / 2.0;
            
            // Sample colony interior (central region)
            LabColor colonyLab = sampleColonyInterior(proc, colony.x(), colony.y(), colonyRadius * 0.7);
            
            // Sample background ring (annulus around colony)
            LabColor backgroundLab = sampleBackgroundRing(proc, colony.x(), colony.y(), 
                                                         colonyRadius * 1.2, colonyRadius * 1.8);
            
            // Calculate features
            double bDelta = colonyLab.b - backgroundLab.b;
            double dE76 = calculateDeltaE76(colonyLab, backgroundLab);
            double aCol = colonyLab.a;
            double snrL = calculateSNR(colonyLab.L, backgroundLab.L);
            
            // X-gal classification rules (user's exact specification)
            String label;
            double confidence;
            
            // Step 1: Binary X-gal classification
            boolean isXGalPositive = (bDelta < thresholds.bDeltaPos()) && 
                                    (dE76 >= thresholds.minDE()) && 
                                    (snrL >= thresholds.minSnrL());
            
            // Check for ambiguous cases (near thresholds or low SNR)
            boolean isAmbiguous = isAmbiguousCase(bDelta, dE76, snrL, thresholds) || 
                                 isVerySmall(colony);
            
            if (isAmbiguous) {
                label = "xgal_uncertain";
                confidence = 0.3;
            } else if (isXGalPositive) {
                // Step 2: Grade positives into light/medium/dark
                if (bDelta <= thresholds.bDeltaDark()) {
                    label = "xgal_dark";
                    confidence = calculateColorGradingConfidence(bDelta, dE76, snrL, "dark");
                } else if (bDelta <= thresholds.bDeltaMedium()) {
                    label = "xgal_medium";
                    confidence = calculateColorGradingConfidence(bDelta, dE76, snrL, "medium");
                } else {
                    label = "xgal_light";
                    confidence = calculateColorGradingConfidence(bDelta, dE76, snrL, "light");
                }
            } else {
                // Negatives
                label = "xgal_neg";
                confidence = calculateXGalNegativeConfidence(bDelta, dE76, snrL);
            }
            
            // Store classification result (would update mutable colony or use classification map)
            Classification result = new Classification(label, confidence, colonyLab, backgroundLab, bDelta, snrL);
            
            // For now, we can't directly update immutable Colony records
            // In practice, would use a separate classification results structure
        }
    }
    
    /**
     * K-means clustering in (a,b) color space with X-gal mapping
     */
    private static void classifyKMeansInAbSpace(ImagePlus image, List<Colony> colonies, 
                                              OvalRoi plateRoi, int clusters) {
        
        List<double[]> abValues = new ArrayList<>();
        List<LabColor> colonyColors = new ArrayList<>();
        ImageProcessor proc = image.getProcessor();
        
        // Extract (a,b) values for all colonies
        for (Colony colony : colonies) {
            double colonyRadius = colony.diameter() / 2.0;
            
            LabColor colonyLab = sampleColonyInterior(proc, colony.x(), colony.y(), colonyRadius * 0.7);
            LabColor backgroundLab = sampleBackgroundRing(proc, colony.x(), colony.y(), 
                                                         colonyRadius * 1.2, colonyRadius * 1.8);
            
            colonyColors.add(colonyLab);
            abValues.add(new double[]{colonyLab.a, colonyLab.b});
        }
        
        // Perform k-means clustering in (a,b) space
        List<Integer> clusterAssignments = performKMeansInAbSpace(abValues, clusters);
        
        // Find which cluster has the most negative b* values (likely X-gal+)
        int xgalPositiveCluster = findMostNegativeBCluster(abValues, clusterAssignments, clusters);
        
        // Assign labels based on cluster assignments
        for (int i = 0; i < colonies.size(); i++) {
            int cluster = clusterAssignments.get(i);
            String label;
            
            if (cluster == xgalPositiveCluster) {
                label = "xgal_pos";
            } else if (clusters == 2) {
                label = "xgal_neg";
            } else {
                label = "cluster_" + cluster;
            }
            
            // Store classification result
            double confidence = 0.7; // k-means confidence
            Classification result = new Classification(label, confidence, colonyColors.get(i), 
                                                     null, colonyColors.get(i).b, 0.0);
        }
    }
    
    /**
     * Sample Lab color from colony interior (central region)
     */
    private static LabColor sampleColonyInterior(ImageProcessor proc, double centerX, double centerY, double radius) {
        List<double[]> labSamples = new ArrayList<>();
        
        // Sample multiple points within colony interior
        int numRings = 3;
        int pointsPerRing = 8;
        
        for (int ring = 0; ring < numRings; ring++) {
            double sampleRadius = radius * (ring + 1) / numRings;
            int pointsThisRing = (ring == 0) ? 1 : pointsPerRing; // Center point + ring points
            
            for (int i = 0; i < pointsThisRing; i++) {
                double angle = (pointsThisRing == 1) ? 0 : 2 * Math.PI * i / pointsThisRing;
                int x = (int) Math.round(centerX + sampleRadius * Math.cos(angle));
                int y = (int) Math.round(centerY + sampleRadius * Math.sin(angle));
                
                // Ensure within image bounds
                if (x >= 0 && x < proc.getWidth() && y >= 0 && y < proc.getHeight()) {
                    double[] lab = samplePixelLab(proc, x, y);
                    labSamples.add(lab);
                }
            }
        }
        
        // Return median Lab values to reduce noise
        return calculateMedianLab(labSamples);
    }
    
    /**
     * Sample background ring (annulus) around colony
     */
    private static LabColor sampleBackgroundRing(ImageProcessor proc, double centerX, double centerY, 
                                                double innerRadius, double outerRadius) {
        List<double[]> labSamples = new ArrayList<>();
        
        // Sample points in annulus between innerRadius and outerRadius
        int numAngles = 16;
        int numRadii = 4;
        
        for (int a = 0; a < numAngles; a++) {
            double angle = 2 * Math.PI * a / numAngles;
            
            for (int r = 0; r < numRadii; r++) {
                double radius = innerRadius + (outerRadius - innerRadius) * r / (numRadii - 1);
                int x = (int) Math.round(centerX + radius * Math.cos(angle));
                int y = (int) Math.round(centerY + radius * Math.sin(angle));
                
                // Ensure within image bounds
                if (x >= 0 && x < proc.getWidth() && y >= 0 && y < proc.getHeight()) {
                    double[] lab = samplePixelLab(proc, x, y);
                    labSamples.add(lab);
                }
            }
        }
        
        // Return median Lab values to reduce background noise
        return calculateMedianLab(labSamples);
    }
    
    /**
     * Sample Lab color from single pixel
     */
    private static double[] samplePixelLab(ImageProcessor proc, int x, int y) {
        if (proc instanceof ColorProcessor) {
            ColorProcessor cp = (ColorProcessor) proc;
            int[] rgb = new int[3];
            cp.getPixel(x, y, rgb);
            LabColor lab = rgbToLab(rgb[0], rgb[1], rgb[2]);
            return new double[]{lab.L, lab.a, lab.b};
        } else {
            // Grayscale - create neutral Lab
            int gray = proc.getPixel(x, y);
            double L = gray / 255.0 * 100.0;
            return new double[]{L, 0, 0};
        }
    }
    
    /**
     * Calculate median Lab color from samples
     */
    private static LabColor calculateMedianLab(List<double[]> samples) {
        if (samples.isEmpty()) {
            return new LabColor(50, 0, 0); // Neutral Lab
        }
        
        samples.sort((a, b) -> Double.compare(a[0], b[0])); // Sort by L
        int medianIndex = samples.size() / 2;
        
        double[] lSorted = samples.stream().mapToDouble(s -> s[0]).sorted().toArray();
        double[] aSorted = samples.stream().mapToDouble(s -> s[1]).sorted().toArray();
        double[] bSorted = samples.stream().mapToDouble(s -> s[2]).sorted().toArray();
        
        return new LabColor(
            lSorted[lSorted.length / 2],
            aSorted[aSorted.length / 2], 
            bSorted[bSorted.length / 2]
        );
    }
    
    /**
     * Calculate Delta E76 color difference
     */
    private static double calculateDeltaE76(LabColor color1, LabColor color2) {
        double dL = color1.L - color2.L;
        double da = color1.a - color2.a;
        double db = color1.b - color2.b;
        
        return Math.sqrt(dL * dL + da * da + db * db);
    }
    
    /**
     * Calculate X-gal positive confidence based on feature strength
     */
    private static double calculateXGalPositiveConfidence(double bDelta, double dE76, double snrL) {
        // Stronger negative b_delta = higher confidence
        double bDeltaScore = Math.min(1.0, Math.abs(bDelta) / 15.0);
        
        // Higher color difference = higher confidence  
        double dE76Score = Math.min(1.0, dE76 / 20.0);
        
        // Higher SNR = higher confidence
        double snrScore = Math.min(1.0, snrL / 10.0);
        
        // Combined confidence (weighted average)
        return Math.min(0.95, 0.5 + 0.3 * bDeltaScore + 0.15 * dE76Score + 0.15 * snrScore);
    }
    
    /**
     * Calculate X-gal negative confidence
     */
    private static double calculateXGalNegativeConfidence(double bDelta, double dE76, double snrL) {
        // Positive or near-zero b_delta indicates non-blue colony
        double bDeltaScore = (bDelta > -2.0) ? 0.8 : 0.4;
        
        // Low color difference suggests similar to background
        double dE76Score = (dE76 < 5.0) ? 0.7 : 0.5;
        
        return Math.min(0.9, 0.4 + 0.4 * bDeltaScore + 0.2 * dE76Score);
    }
    
    /**
     * Check if colony falls on borderline thresholds (uncertain)
     */
    private static boolean isBorderlineCase(double bDelta, double dE76, double snrL) {
        // Near the X-gal positive thresholds
        boolean nearBDeltaThreshold = bDelta > -8.0 && bDelta < -4.0;
        boolean nearDE76Threshold = dE76 > 6.0 && dE76 < 10.0;
        boolean nearSNRThreshold = snrL > 2.0 && snrL < 3.0;
        
        // If 2 or more features are borderline, classify as uncertain
        int borderlineCount = 0;
        if (nearBDeltaThreshold) borderlineCount++;
        if (nearDE76Threshold) borderlineCount++;
        if (nearSNRThreshold) borderlineCount++;
        
        return borderlineCount >= 2;
    }
    
    /**
     * Check if colony is very small (may be unreliable for color analysis)
     */
    private static boolean isVerySmall(Colony colony) {
        return colony.diameter() < 10.0; // Less than 10 pixels diameter
    }
    
    /**
     * Convert RGB to Lab color space (improved conversion)
     */
    public static LabColor rgbToLab(int r, int g, int b) {
        // Normalize RGB values
        double rNorm = r / 255.0;
        double gNorm = g / 255.0;
        double bNorm = b / 255.0;
        
        // Apply gamma correction
        rNorm = (rNorm > 0.04045) ? Math.pow((rNorm + 0.055) / 1.055, 2.4) : rNorm / 12.92;
        gNorm = (gNorm > 0.04045) ? Math.pow((gNorm + 0.055) / 1.055, 2.4) : gNorm / 12.92;
        bNorm = (bNorm > 0.04045) ? Math.pow((bNorm + 0.055) / 1.055, 2.4) : bNorm / 12.92;
        
        // Convert to XYZ color space (Observer = 2°, Illuminant = D65)
        double x = rNorm * 0.4124 + gNorm * 0.3576 + bNorm * 0.1805;
        double y = rNorm * 0.2126 + gNorm * 0.7152 + bNorm * 0.0722;
        double z = rNorm * 0.0193 + gNorm * 0.1192 + bNorm * 0.9505;
        
        // Normalize for D65 illuminant
        x = x / 0.95047;
        y = y / 1.00000;
        z = z / 1.08883;
        
        // Convert XYZ to Lab
        x = (x > 0.008856) ? Math.pow(x, 1.0/3.0) : (7.787 * x + 16.0/116.0);
        y = (y > 0.008856) ? Math.pow(y, 1.0/3.0) : (7.787 * y + 16.0/116.0);
        z = (z > 0.008856) ? Math.pow(z, 1.0/3.0) : (7.787 * z + 16.0/116.0);
        
        double L = 116.0 * y - 16.0;
        double a = 500.0 * (x - y);
        double bLab = 200.0 * (y - z);
        
        return new LabColor(L, a, bLab);
    }
    
    /**
     * Calculate signal-to-noise ratio
     */
    private static double calculateSNR(double signal, double background) {
        if (background == 0) return Double.MAX_VALUE;
        return Math.abs(signal - background) / Math.abs(background);
    }
    
    /**
     * Perform k-means clustering in (a,b) color space
     */
    private static List<Integer> performKMeansInAbSpace(List<double[]> abValues, int k) {
        if (abValues.isEmpty()) return new ArrayList<>();
        
        // Initialize centroids randomly within data range
        double[][] centroids = initializeCentroids(abValues, k);
        List<Integer> assignments = new ArrayList<>(Collections.nCopies(abValues.size(), 0));
        
        // Iterate until convergence (max 20 iterations)
        for (int iteration = 0; iteration < 20; iteration++) {
            boolean changed = false;
            
            // Assign each point to nearest centroid
            for (int i = 0; i < abValues.size(); i++) {
                double[] point = abValues.get(i);
                int nearestCluster = findNearestCentroid(point, centroids);
                
                if (assignments.get(i) != nearestCluster) {
                    assignments.set(i, nearestCluster);
                    changed = true;
                }
            }
            
            // Update centroids
            updateCentroids(centroids, abValues, assignments);
            
            // Check for convergence
            if (!changed) break;
        }
        
        return assignments;
    }
    
    /**
     * Initialize k centroids within data range
     */
    private static double[][] initializeCentroids(List<double[]> data, int k) {
        double[][] centroids = new double[k][2];
        
        // Find data range
        double minA = data.stream().mapToDouble(p -> p[0]).min().orElse(0);
        double maxA = data.stream().mapToDouble(p -> p[0]).max().orElse(0);
        double minB = data.stream().mapToDouble(p -> p[1]).min().orElse(0);
        double maxB = data.stream().mapToDouble(p -> p[1]).max().orElse(0);
        
        // Initialize centroids evenly spaced
        for (int i = 0; i < k; i++) {
            centroids[i][0] = minA + (maxA - minA) * i / (k - 1); // a* value
            centroids[i][1] = minB + (maxB - minB) * i / (k - 1); // b* value
        }
        
        return centroids;
    }
    
    /**
     * Find nearest centroid for a point
     */
    private static int findNearestCentroid(double[] point, double[][] centroids) {
        int nearest = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < centroids.length; i++) {
            double distance = euclideanDistance(point, centroids[i]);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = i;
            }
        }
        
        return nearest;
    }
    
    /**
     * Calculate Euclidean distance between two points in (a,b) space
     */
    private static double euclideanDistance(double[] p1, double[] p2) {
        double da = p1[0] - p2[0];
        double db = p1[1] - p2[1];
        return Math.sqrt(da * da + db * db);
    }
    
    /**
     * Update centroids based on current assignments
     */
    private static void updateCentroids(double[][] centroids, List<double[]> data, List<Integer> assignments) {
        for (int k = 0; k < centroids.length; k++) {
            double sumA = 0, sumB = 0;
            int count = 0;
            
            for (int i = 0; i < data.size(); i++) {
                if (assignments.get(i) == k) {
                    sumA += data.get(i)[0];
                    sumB += data.get(i)[1];
                    count++;
                }
            }
            
            if (count > 0) {
                centroids[k][0] = sumA / count;
                centroids[k][1] = sumB / count;
            }
        }
    }
    
    /**
     * Find cluster with most negative b* values (likely X-gal+)
     */
    private static int findMostNegativeBCluster(List<double[]> abValues, List<Integer> assignments, int numClusters) {
        double[] avgBValues = new double[numClusters];
        int[] counts = new int[numClusters];
        
        // Calculate average b* value for each cluster
        for (int i = 0; i < abValues.size(); i++) {
            int cluster = assignments.get(i);
            avgBValues[cluster] += abValues.get(i)[1]; // b* value
            counts[cluster]++;
        }
        
        // Find cluster with most negative average b*
        int mostNegativeCluster = 0;
        double mostNegativeB = Double.MAX_VALUE;
        
        for (int k = 0; k < numClusters; k++) {
            if (counts[k] > 0) {
                double avgB = avgBValues[k] / counts[k];
                if (avgB < mostNegativeB) {
                    mostNegativeB = avgB;
                    mostNegativeCluster = k;
                }
            }
        }
        
        return mostNegativeCluster;
    }
    
    /**
     * Calculate confidence for color grading (light/medium/dark)
     */
    private static double calculateColorGradingConfidence(double bDelta, double dE, double snrL, String grade) {
        // Higher confidence for stronger signals and clearer separation from thresholds
        double bDeltaStrength = Math.abs(bDelta) / 20.0; // Normalize by typical range
        double dEStrength = Math.min(1.0, dE / 15.0);    // Normalize color difference
        double snrStrength = Math.min(1.0, snrL / 5.0);  // Normalize SNR
        
        // Base confidence varies by grade
        double baseConfidence = switch (grade) {
            case "dark" -> 0.9;   // High confidence for dark blue
            case "medium" -> 0.7; // Medium confidence for medium blue
            case "light" -> 0.6;  // Lower confidence for light blue
            default -> 0.5;
        };
        
        // Adjust confidence based on signal strength
        double adjustedConfidence = baseConfidence * 0.7 + 
                                   (bDeltaStrength * 0.15 + dEStrength * 0.1 + snrStrength * 0.05);
        
        return Math.min(0.95, Math.max(0.3, adjustedConfidence));
    }
    
    /**
     * Auto-calibrate thresholds using k-means clustering and percentiles
     */
    private static XGalColorThresholds autoCalibateThresholds(ImagePlus image, List<Colony> colonies, OvalRoi plateRoi) {
        ImageProcessor proc = image.getProcessor();
        
        // Step 1: Extract (a*, b*, bΔ) features for colonies that pass SNR
        List<double[]> features = new ArrayList<>();
        List<Double> bDeltas = new ArrayList<>();
        
        for (Colony colony : colonies) {
            double colonyRadius = colony.diameter() / 2.0;
            
            // Sample colors
            LabColor colonyLab = sampleColonyInterior(proc, colony.x(), colony.y(), colonyRadius * 0.7);
            LabColor backgroundLab = sampleBackgroundRing(proc, colony.x(), colony.y(), 
                                                         colonyRadius * 1.2, colonyRadius * 1.8);
            
            double snrL = calculateSNR(colonyLab.L, backgroundLab.L);
            
            // Only include colonies that pass SNR threshold
            if (snrL >= XGalColorThresholds.defaults().minSnrL()) {
                double bDelta = colonyLab.b - backgroundLab.b;
                features.add(new double[]{colonyLab.a, colonyLab.b});
                bDeltas.add(bDelta);
            }
        }
        
        if (features.size() < 10) {
            // Not enough data for auto-calibration, use defaults
            return XGalColorThresholds.defaults();
        }
        
        // Step 2: Run k-means (k=2) on (a*, b*) to separate blue-ish vs tan
        List<Integer> clusters = performKMeans2D(features, 2);
        
        // Step 3: Identify which cluster is more blue (more negative mean b*)
        double cluster0MeanB = 0, cluster1MeanB = 0;
        int cluster0Count = 0, cluster1Count = 0;
        
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i) == 0) {
                cluster0MeanB += features.get(i)[1];
                cluster0Count++;
            } else {
                cluster1MeanB += features.get(i)[1];
                cluster1Count++;
            }
        }
        
        cluster0MeanB /= cluster0Count;
        cluster1MeanB /= cluster1Count;
        
        // The cluster with more negative mean b* = X-gal positive cluster
        int positiveCluster = (cluster0MeanB < cluster1MeanB) ? 0 : 1;
        
        // Step 4: Extract bΔ values for positive cluster
        List<Double> positiveBDeltas = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i) == positiveCluster) {
                positiveBDeltas.add(bDeltas.get(i));
            }
        }
        
        if (positiveBDeltas.size() < 5) {
            // Not enough positive colonies, use defaults
            return XGalColorThresholds.defaults();
        }
        
        // Step 5: Calculate percentiles within positive cluster
        Collections.sort(positiveBDeltas);
        
        double bDeltaDark = percentile(positiveBDeltas, 20);      // P20
        double bDeltaMedium = percentile(positiveBDeltas, 50);    // P50 (median)
        double bDeltaPos = percentile(positiveBDeltas, 80);       // P80
        
        // Cap positive threshold at -4 to avoid being too lax
        bDeltaPos = Math.min(bDeltaPos, -4.0);
        
        // Apply minimum gap: ensure dark < medium < pos by at least 2 units
        if (bDeltaMedium - bDeltaDark < 2.0) {
            bDeltaMedium = bDeltaDark + 2.0;
        }
        if (bDeltaPos - bDeltaMedium < 2.0) {
            bDeltaPos = bDeltaMedium + 2.0;
        }
        
        // Keep default dE and SNR thresholds
        XGalColorThresholds defaults = XGalColorThresholds.defaults();
        
        return new XGalColorThresholds(bDeltaPos, bDeltaDark, bDeltaMedium, 
                                      defaults.minDE(), defaults.minSnrL());
    }
    
    /**
     * Calculate percentile from sorted list
     */
    private static double percentile(List<Double> sortedValues, int percentile) {
        int n = sortedValues.size();
        double index = (percentile / 100.0) * (n - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        
        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        } else {
            double weight = index - lowerIndex;
            return sortedValues.get(lowerIndex) * (1 - weight) + sortedValues.get(upperIndex) * weight;
        }
    }
    
    /**
     * Simple k-means clustering for 2D data
     */
    private static List<Integer> performKMeans2D(List<double[]> data, int k) {
        int n = data.size();
        if (n < k) return Collections.nCopies(n, 0);
        
        // Initialize centroids randomly
        Random random = new Random(42); // Fixed seed for reproducibility
        double[][] centroids = new double[k][2];
        for (int i = 0; i < k; i++) {
            int randomIndex = random.nextInt(n);
            centroids[i][0] = data.get(randomIndex)[0];
            centroids[i][1] = data.get(randomIndex)[1];
        }
        
        List<Integer> assignments = new ArrayList<>(Collections.nCopies(n, 0));
        boolean changed = true;
        int maxIterations = 20;
        
        for (int iter = 0; iter < maxIterations && changed; iter++) {
            changed = false;
            
            // Assign points to closest centroid
            for (int i = 0; i < n; i++) {
                double minDist = Double.MAX_VALUE;
                int bestCluster = 0;
                
                for (int j = 0; j < k; j++) {
                    double dx = data.get(i)[0] - centroids[j][0];
                    double dy = data.get(i)[1] - centroids[j][1];
                    double dist = dx * dx + dy * dy;
                    
                    if (dist < minDist) {
                        minDist = dist;
                        bestCluster = j;
                    }
                }
                
                if (assignments.get(i) != bestCluster) {
                    assignments.set(i, bestCluster);
                    changed = true;
                }
            }
            
            // Update centroids
            for (int j = 0; j < k; j++) {
                double sumX = 0, sumY = 0;
                int count = 0;
                
                for (int i = 0; i < n; i++) {
                    if (assignments.get(i) == j) {
                        sumX += data.get(i)[0];
                        sumY += data.get(i)[1];
                        count++;
                    }
                }
                
                if (count > 0) {
                    centroids[j][0] = sumX / count;
                    centroids[j][1] = sumY / count;
                }
            }
        }
        
        return assignments;
    }
    
    /**
     * Check if colony falls into ambiguous classification region
     */
    private static boolean isAmbiguousCase(double bDelta, double dE, double snrL, XGalColorThresholds thresholds) {
        // Near threshold boundaries (within 20% of threshold range)
        double posThresholdWindow = Math.abs(thresholds.bDeltaPos()) * 0.2;
        boolean nearPosThreshold = Math.abs(bDelta - thresholds.bDeltaPos()) < posThresholdWindow;
        
        double deThresholdWindow = thresholds.minDE() * 0.2;
        boolean nearDEThreshold = Math.abs(dE - thresholds.minDE()) < deThresholdWindow;
        
        double snrThresholdWindow = thresholds.minSnrL() * 0.2;
        boolean nearSNRThreshold = Math.abs(snrL - thresholds.minSnrL()) < snrThresholdWindow;
        
        // If 2+ features are near boundaries, classify as ambiguous
        int nearBoundaryCount = (nearPosThreshold ? 1 : 0) + 
                               (nearDEThreshold ? 1 : 0) + 
                               (nearSNRThreshold ? 1 : 0);
        
        return nearBoundaryCount >= 2;
    }
    
    /**
     * Map string labels to ColonyColor enum for compatibility
     */
    private static ColonyColor mapToColonyColor(String label) {
        return switch (label) {
            case "xgal_dark", "xgal_medium", "xgal_light" -> ColonyColor.BLUE;
            case "xgal_neg" -> ColonyColor.WHITE;
            case "xgal_uncertain" -> ColonyColor.OTHER;
            default -> ColonyColor.OTHER;
        };
    }
    
    /**
     * Auto-calibrate color thresholds based on colony population
     */
    public static XGalColorThresholds autoCalibrateThresholds(ImagePlus image, List<Colony> colonies, OvalRoi plateRoi) {
        if (colonies.isEmpty()) {
            return XGalColorThresholds.defaults();
        }
        
        ImageProcessor proc = image.getProcessor();
        List<Double> bDeltaValues = new ArrayList<>();
        
        // Collect b* delta values from all colonies
        for (Colony colony : colonies) {
            double colonyRadius = colony.diameter() / 2.0;
            LabColor colonyLab = sampleColonyInterior(proc, colony.x(), colony.y(), colonyRadius * 0.7);
            LabColor backgroundLab = sampleBackgroundRing(proc, colony.x(), colony.y(), 
                                                         colonyRadius * 1.2, colonyRadius * 1.8);
            
            double bDelta = colonyLab.b() - backgroundLab.b();
            bDeltaValues.add(bDelta);
        }
        
        // Sort values to find percentiles
        bDeltaValues.sort(Double::compareTo);
        
        // Auto-calibrate thresholds based on data distribution
        int n = bDeltaValues.size();
        double p10 = bDeltaValues.get(Math.max(0, (int)(n * 0.1)));  // 10th percentile (very blue)
        double p30 = bDeltaValues.get(Math.max(0, (int)(n * 0.3)));  // 30th percentile (medium blue)
        double median = bDeltaValues.get(n / 2);                     // 50th percentile
        
        // Set thresholds relative to data distribution, but within reasonable bounds
        double bDeltaPos = Math.max(-12.0, Math.min(-3.0, median - 2.0));      // Slightly below median
        double bDeltaMedium = Math.max(-20.0, Math.min(-5.0, p30 - 1.0));      // 30th percentile adjusted
        double bDeltaDark = Math.max(-25.0, Math.min(-8.0, p10 - 1.0));        // 10th percentile adjusted
        
        return new XGalColorThresholds(bDeltaPos, bDeltaDark, bDeltaMedium, 8.0, 2.5);
    }
    
    /**
     * Export colony features to CSV format with comprehensive columns
     */
    public static String exportToCSV(List<ColonyFeatures> features) {
        StringBuilder csv = new StringBuilder();
        
        // Header row
        csv.append("colony_id,x_mm,y_mm,eq_diam_mm,")
           .append("L,a,b,L_bg,a_bg,b_bg,b_delta,dE_bg,snr_L,")
           .append("xgal_binary,xgal_grade,size_bin,label,confidence")
           .append("\n");
        
        // Data rows
        for (ColonyFeatures feature : features) {
            csv.append(String.format("%d,%.3f,%.3f,%.3f,", 
                feature.colonyId(), feature.xMm(), feature.yMm(), feature.eqDiamMm()))
               .append(String.format("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f,%.2f,%.2f,",
                feature.L(), feature.a(), feature.b(),
                feature.LBg(), feature.aBg(), feature.bBg(),
                feature.bDelta(), feature.dEBg(), feature.snrL()))
               .append(String.format("%s,%s,%s,%s,%.3f",
                feature.xgalBinary(), feature.xgalGrade(), feature.sizeBin(),
                feature.label(), feature.confidence()))
               .append("\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Export colony features to JSON format
     */
    public static String exportToJSON(List<ColonyFeatures> features) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        
        for (int i = 0; i < features.size(); i++) {
            ColonyFeatures f = features.get(i);
            json.append("  {\n")
                .append(String.format("    \"colony_id\": %d,\n", f.colonyId()))
                .append(String.format("    \"x_mm\": %.3f,\n", f.xMm()))
                .append(String.format("    \"y_mm\": %.3f,\n", f.yMm()))
                .append(String.format("    \"eq_diam_mm\": %.3f,\n", f.eqDiamMm()))
                .append(String.format("    \"L\": %.2f,\n", f.L()))
                .append(String.format("    \"a\": %.2f,\n", f.a()))
                .append(String.format("    \"b\": %.2f,\n", f.b()))
                .append(String.format("    \"L_bg\": %.2f,\n", f.LBg()))
                .append(String.format("    \"a_bg\": %.2f,\n", f.aBg()))
                .append(String.format("    \"b_bg\": %.2f,\n", f.bBg()))
                .append(String.format("    \"b_delta\": %.3f,\n", f.bDelta()))
                .append(String.format("    \"dE_bg\": %.2f,\n", f.dEBg()))
                .append(String.format("    \"snr_L\": %.2f,\n", f.snrL()))
                .append(String.format("    \"xgal_binary\": \"%s\",\n", f.xgalBinary()))
                .append(String.format("    \"xgal_grade\": \"%s\",\n", f.xgalGrade()))
                .append(String.format("    \"size_bin\": \"%s\",\n", f.sizeBin()))
                .append(String.format("    \"label\": \"%s\",\n", f.label()))
                .append(String.format("    \"confidence\": %.3f\n", f.confidence()))
                .append("  }");
            
            if (i < features.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("]");
        return json.toString();
    }
    
    /**
     * Generate summary statistics for classified colonies
     */
    public static Map<String, Integer> summary(List<Colony> colonies) {
        Map<String, Integer> counts = new HashMap<>();
        
        for (Colony colony : colonies) {
            String classification = colony.binCategory();
            counts.put(classification, counts.getOrDefault(classification, 0) + 1);
        }
        
        return counts;
    }
}