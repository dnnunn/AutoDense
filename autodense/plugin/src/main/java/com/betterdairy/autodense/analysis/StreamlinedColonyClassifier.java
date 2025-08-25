package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.session.SessionStore;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.util.List;
import java.util.function.ToDoubleFunction;
import org.json.JSONObject;

/**
 * Streamlined colony classifier based on user's clean design.
 * Direct field assignment to mutable colony objects for efficiency.
 */
public final class StreamlinedColonyClassifier {

    // Use comprehensive parameter system via composition
    public static final class Params {
        public final ColonyAnalysisParams.ColorParams colorParams;
        
        public Params() {
            this.colorParams = new ColonyAnalysisParams.ColorParams();
        }
        
        public Params(ColonyAnalysisParams.ColorParams colorParams) {
            this.colorParams = colorParams;
        }
        
        // Delegate methods for convenience
        public double getBDeltaPos() { return colorParams.bDeltaPos; }
        public double getBDeltaMed() { return colorParams.bDeltaMed; }
        public double getBDeltaDark() { return colorParams.bDeltaDark; }
        public double getMinDE() { return colorParams.minDE; }
        public double getMinSNRL() { return colorParams.minSNRL; }
        public boolean isAutoCalibrate() { return colorParams.autoCalibrate; }
    }

    /**
     * Main classification method with comprehensive preprocessing
     */
    public static void classifyLab(ImagePlus imp, List<MutableColony> colonies, Roi plateRoi, 
                                  ColonyAnalysisParams.AnalysisConfig config) {
        classifyLab(imp, colonies, plateRoi, config, null, null);
    }
    
    /**
     * Main classification method with session store support for persistent calibration
     */
    public static void classifyLab(ImagePlus imp, List<MutableColony> colonies, Roi plateRoi, 
                                  ColonyAnalysisParams.AnalysisConfig config, SessionStore sessionStore, String imageHandle) {
        // Step 1: Preprocess image for challenging conditions
        ImagePlus processedImage = preprocessImage(imp, config.preprocessingParams);
        
        // Step 2: Extract Lab color features for all colonies
        extractLabFeatures(processedImage, colonies, plateRoi, config.preprocessingParams);
        
        // Step 3: Check for persistent calibration data or auto-calibrate
        if (config.colorParams.autoCalibrate) {
            autoCalibrate(colonies, config.colorParams, sessionStore, imageHandle);
        }

        // Step 4: Classify each colony with validation
        for (MutableColony c : colonies) {
            // Edge case: Check if colony is in rim exclusion zone
            if (isInRimExclusionZone(c, plateRoi, config.detectionParams.rimArtifactMM)) {
                c.xgalBinary = "uncertain";
                c.xgalGrade = null;
                c.confidence = 0.1;
                continue;
            }
            
            // Quality checks
            if (c.snrL < config.colorParams.minSNRL || c.dEbg < config.colorParams.minDE) {
                c.xgalBinary = "uncertain";
                c.xgalGrade = null;
                c.confidence = 0.3;
                continue;
            }
            
            // Classification logic
            if (c.bDelta < config.colorParams.bDeltaPos) {
                c.xgalBinary = "pos";
                if (c.bDelta <= config.colorParams.bDeltaDark)      c.xgalGrade = "dark";
                else if (c.bDelta <= config.colorParams.bDeltaMed)  c.xgalGrade = "medium";
                else                                                c.xgalGrade = "light";
                c.confidence = confidenceFrom(c.bDelta, c.snrL, config.colorParams);
            } else {
                c.xgalBinary = "neg";
                c.xgalGrade = null;
                c.confidence = 0.8 - 0.05 * Math.max(0, -c.bDelta);
            }
        }
    }
    
    /**
     * Legacy method for backwards compatibility
     */
    public static void classifyLab(ImagePlus imp, List<MutableColony> colonies, Roi plateRoi, Params p) {
        ColonyAnalysisParams.AnalysisConfig config = new ColonyAnalysisParams.AnalysisConfig();
        config.colorParams = p.colorParams;
        classifyLab(imp, colonies, plateRoi, config);
    }

    /**
     * Preprocess image for challenging conditions
     */
    private static ImagePlus preprocessImage(ImagePlus imp, ColonyAnalysisParams.PreprocessingParams params) {
        if (!params.flattenBackground && !params.detectSaturation) {
            return imp; // No preprocessing needed
        }
        
        ImagePlus processed = imp.duplicate();
        
        if (params.flattenBackground) {
            // Background flattening via gaussian blur and division
            ImagePlus background = processed.duplicate();
            ij.plugin.filter.GaussianBlur blur = new ij.plugin.filter.GaussianBlur();
            blur.blurGaussian(background.getProcessor(), params.gaussianSigma);
            
            // Divide original by background
            ij.plugin.ImageCalculator calc = new ij.plugin.ImageCalculator();
            processed = calc.run("Divide create 32-bit", processed, background);
            
            // Scale back to reasonable range
            processed.getProcessor().multiply(128.0);
        }
        
        if (params.detectSaturation) {
            // Mark saturated pixels for exclusion during color sampling
            // Implementation would flag pixels > saturationThreshold
            // For now, just a placeholder
        }
        
        return processed;
    }
    
    /**
     * Check if colony is in rim exclusion zone
     */
    private static boolean isInRimExclusionZone(MutableColony colony, Roi plateRoi, double rimExclusionMM) {
        if (plateRoi == null || !(plateRoi instanceof ij.gui.OvalRoi)) {
            return false;
        }
        
        ij.gui.OvalRoi ovalRoi = (ij.gui.OvalRoi) plateRoi;
        double plateCenterX = ovalRoi.getBounds().getCenterX();
        double plateCenterY = ovalRoi.getBounds().getCenterY();
        double plateRadius = Math.min(ovalRoi.getBounds().width, ovalRoi.getBounds().height) / 2.0;
        
        // Calculate distance from colony to plate center
        double dx = colony.x - plateCenterX;
        double dy = colony.y - plateCenterY;
        double distanceFromCenter = Math.sqrt(dx * dx + dy * dy);
        
        // Assume ~20 px/mm for rim exclusion conversion (rough estimate)
        double rimExclusionPx = rimExclusionMM * 20.0;
        
        return distanceFromCenter > (plateRadius - rimExclusionPx);
    }
    
    /**
     * Extract Lab color features with enhanced preprocessing support
     */
    private static void extractLabFeatures(ImagePlus imp, List<MutableColony> colonies, Roi plateRoi,
                                         ColonyAnalysisParams.PreprocessingParams params) {
        ImageProcessor proc = imp.getProcessor();
        
        for (MutableColony c : colonies) {
            double radius = c.eqDiamPx / 2.0;
            
            // Sample colony interior (central 70%)
            LabColor colonyLab = sampleColonyInterior(proc, c.x, c.y, radius * 0.7);
            
            // Sample background ring (annulus)
            LabColor backgroundLab = sampleBackgroundRing(proc, c.x, c.y, radius * 1.2, radius * 1.8);
            
            // Populate colony fields
            c.L = colonyLab.L;
            c.a = colonyLab.a;
            c.b = colonyLab.b;
            c.Lbg = backgroundLab.L;
            c.abg = backgroundLab.a;
            c.bbg = backgroundLab.b;
            c.bDelta = colonyLab.b - backgroundLab.b;
            c.dEbg = calculateDeltaE76(colonyLab, backgroundLab);
            c.snrL = calculateSNR(colonyLab.L, backgroundLab.L);
        }
    }

    /**
     * Auto-calibrate thresholds using k-means clustering and percentiles with session persistence
     */
    private static void autoCalibrate(List<MutableColony> colonies, ColonyAnalysisParams.ColorParams p, 
                                     SessionStore sessionStore, String imageHandle) {
        // Check for existing calibration data in session
        if (sessionStore != null && imageHandle != null) {
            // Look for existing calibration data
            for (String analysisHandle : sessionStore.getAnalysesForImage(imageHandle)) {
                SessionStore.AnalysisRecord analysis = sessionStore.getAnalysis(analysisHandle);
                if ("xgal_calibration".equals(analysis.type)) {
                    JSONObject calibData = (JSONObject) analysis.data;
                    
                    // Restore learned thresholds
                    p.bDeltaPos = calibData.getDouble("bDeltaPos");
                    p.bDeltaMed = calibData.getDouble("bDeltaMed");
                    p.bDeltaDark = calibData.getDouble("bDeltaDark");
                    
                    System.out.println("Restored X-gal calibration: pos=" + p.bDeltaPos + 
                                     ", med=" + p.bDeltaMed + ", dark=" + p.bDeltaDark);
                    return; // Use existing calibration
                }
            }
        }
        
        // Perform auto-calibration as before
        // Quick k-means: cluster by (a,b) into 2 groups; pick group with lower mean b as positive
        double[][] pts = colonies.stream()
            .map(c -> new double[]{c.a, c.b})
            .toArray(double[][]::new);
            
        int[] labels = KMeans.k2(pts); // Simple 2-means implementation needed
        
        double meanB0 = meanWhere(colonies, labels, 0, col -> col.b);
        double meanB1 = meanWhere(colonies, labels, 1, col -> col.b);
        int posLabel = (meanB0 < meanB1) ? 0 : 1;

        double[] bDeltas = colonies.stream()
            .filter(c -> labels[c.id] == posLabel)
            .mapToDouble(c -> c.bDelta)
            .sorted()
            .toArray();
            
        if (bDeltas.length >= 10) {
            double p20 = percentile(bDeltas, 20);
            double p50 = percentile(bDeltas, 50);
            double p80 = percentile(bDeltas, 80);
            
            p.bDeltaDark = Math.min(p.bDeltaDark, p20);
            p.bDeltaMed  = Math.min(p.bDeltaMed,  p50);
            p.bDeltaPos  = Math.min(-4.0,         p80);  // don't be too lenient
            
            // Ensure ordering
            if (!(p.bDeltaDark < p.bDeltaMed && p.bDeltaMed < p.bDeltaPos)) {
                p.bDeltaDark = Math.min(p.bDeltaDark, p.bDeltaMed - 2);
                p.bDeltaPos  = Math.max(p.bDeltaPos,  p.bDeltaMed + 2);
            }
            
            // Persist learned thresholds to session
            if (sessionStore != null && imageHandle != null) {
                JSONObject calibData = new JSONObject()
                    .put("bDeltaPos", p.bDeltaPos)
                    .put("bDeltaMed", p.bDeltaMed)
                    .put("bDeltaDark", p.bDeltaDark)
                    .put("colonyCount", colonies.size())
                    .put("calibrationTimestamp", System.currentTimeMillis());
                    
                sessionStore.putAnalysis("xgal_calibration", calibData, imageHandle);
                System.out.println("Stored X-gal calibration: pos=" + p.bDeltaPos + 
                                 ", med=" + p.bDeltaMed + ", dark=" + p.bDeltaDark);
            }
        }
    }

    /**
     * Calculate confidence score for positive colonies
     */
    private static double confidenceFrom(double bDelta, double snrL, ColonyAnalysisParams.ColorParams p) {
        // Map stronger blue + higher SNR to higher confidence
        double t = Math.min(1.0, Math.max(0.0, (Math.abs(bDelta) - Math.abs(p.bDeltaPos)) / 12.0));
        double s = Math.min(1.0, (snrL - p.minSNRL) / 3.0);
        return 0.4 + 0.6 * 0.5 * (t + s); // 0.4..1.0
    }

    /**
     * Helper: calculate mean of field where label matches
     */
    private static double meanWhere(List<MutableColony> colonies, int[] labels, int targetLabel, 
                                   ToDoubleFunction<MutableColony> extractor) {
        return colonies.stream()
            .filter(c -> c.id < labels.length && labels[c.id] == targetLabel)
            .mapToDouble(extractor)
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate percentile from sorted array
     */
    private static double percentile(double[] sortedValues, int percentile) {
        int n = sortedValues.length;
        double index = (percentile / 100.0) * (n - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        
        if (lowerIndex == upperIndex) {
            return sortedValues[lowerIndex];
        } else {
            double weight = index - lowerIndex;
            return sortedValues[lowerIndex] * (1 - weight) + sortedValues[upperIndex] * weight;
        }
    }

    /**
     * Lab color space representation
     */
    public static record LabColor(double L, double a, double b) {}
    
    /**
     * Sample Lab color from colony interior
     */
    private static LabColor sampleColonyInterior(ImageProcessor proc, double x, double y, double radius) {
        // Simple circular sampling - could be enhanced with ImageJ ROI sampling
        int centerX = (int) Math.round(x);
        int centerY = (int) Math.round(y);
        int r = Math.max(1, (int) Math.round(radius));
        
        double sumL = 0, sumA = 0, sumB = 0;
        int count = 0;
        
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r * r) {
                    int px = centerX + dx;
                    int py = centerY + dy;
                    if (px >= 0 && px < proc.getWidth() && py >= 0 && py < proc.getHeight()) {
                        int rgb = proc.getPixel(px, py);
                        LabColor lab = rgbToLab(rgb);
                        sumL += lab.L;
                        sumA += lab.a;
                        sumB += lab.b;
                        count++;
                    }
                }
            }
        }
        
        if (count > 0) {
            return new LabColor(sumL / count, sumA / count, sumB / count);
        } else {
            return new LabColor(50, 0, 0); // Default neutral gray
        }
    }
    
    /**
     * Sample Lab color from background ring (annulus)
     */
    private static LabColor sampleBackgroundRing(ImageProcessor proc, double x, double y, 
                                                double innerRadius, double outerRadius) {
        int centerX = (int) Math.round(x);
        int centerY = (int) Math.round(y);
        int rInner = (int) Math.round(innerRadius);
        int rOuter = (int) Math.round(outerRadius);
        
        double sumL = 0, sumA = 0, sumB = 0;
        int count = 0;
        
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                double distSq = dx * dx + dy * dy;
                if (distSq >= rInner * rInner && distSq <= rOuter * rOuter) {
                    int px = centerX + dx;
                    int py = centerY + dy;
                    if (px >= 0 && px < proc.getWidth() && py >= 0 && py < proc.getHeight()) {
                        int rgb = proc.getPixel(px, py);
                        LabColor lab = rgbToLab(rgb);
                        sumL += lab.L;
                        sumA += lab.a;
                        sumB += lab.b;
                        count++;
                    }
                }
            }
        }
        
        if (count > 0) {
            return new LabColor(sumL / count, sumA / count, sumB / count);
        } else {
            return new LabColor(50, 0, 0); // Default neutral gray
        }
    }
    
    /**
     * Convert RGB to Lab color space (simplified)
     */
    private static LabColor rgbToLab(int rgb) {
        // Extract RGB components
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // Convert to [0,1] range
        double rd = r / 255.0;
        double gd = g / 255.0;
        double bd = b / 255.0;
        
        // Apply gamma correction
        rd = (rd > 0.04045) ? Math.pow((rd + 0.055) / 1.055, 2.4) : rd / 12.92;
        gd = (gd > 0.04045) ? Math.pow((gd + 0.055) / 1.055, 2.4) : gd / 12.92;
        bd = (bd > 0.04045) ? Math.pow((bd + 0.055) / 1.055, 2.4) : bd / 12.92;
        
        // Convert to XYZ (D65 illuminant)
        double x = 0.4124564 * rd + 0.3575761 * gd + 0.1804375 * bd;
        double y = 0.2126729 * rd + 0.7151522 * gd + 0.0721750 * bd;
        double z = 0.0193339 * rd + 0.1191920 * gd + 0.9503041 * bd;
        
        // Normalize to D65 white point
        x = x / 0.95047;
        y = y / 1.00000;
        z = z / 1.08883;
        
        // Convert to Lab
        double fx = (x > 0.008856) ? Math.cbrt(x) : (7.787 * x + 16.0 / 116.0);
        double fy = (y > 0.008856) ? Math.cbrt(y) : (7.787 * y + 16.0 / 116.0);
        double fz = (z > 0.008856) ? Math.cbrt(z) : (7.787 * z + 16.0 / 116.0);
        
        double L = 116.0 * fy - 16.0;
        double a = 500.0 * (fx - fy);
        double bLab = 200.0 * (fy - fz);
        
        return new LabColor(L, a, bLab);
    }
    
    /**
     * Calculate Delta E 1976 color difference
     */
    private static double calculateDeltaE76(LabColor lab1, LabColor lab2) {
        double dL = lab1.L - lab2.L;
        double da = lab1.a - lab2.a;
        double db = lab1.b - lab2.b;
        return Math.sqrt(dL * dL + da * da + db * db);
    }
    
    /**
     * Calculate signal-to-noise ratio in L* channel
     */
    private static double calculateSNR(double signalL, double backgroundL) {
        double noise = Math.max(1.0, Math.abs(backgroundL - 50.0)); // Assume mid-gray baseline
        return Math.abs(signalL - backgroundL) / noise;
    }

    /**
     * Simple k-means clustering for 2D data (k=2)
     */
    public static final class KMeans {
        
        public static int[] k2(double[][] data) {
            int n = data.length;
            if (n < 2) return new int[n]; // All cluster 0
            
            // Initialize centroids with first two points
            double[] c0 = {data[0][0], data[0][1]};
            double[] c1 = {data[Math.min(1, n-1)][0], data[Math.min(1, n-1)][1]};
            
            int[] assignments = new int[n];
            boolean changed = true;
            int maxIterations = 20;
            
            for (int iter = 0; iter < maxIterations && changed; iter++) {
                changed = false;
                
                // Assign points to closest centroid
                for (int i = 0; i < n; i++) {
                    double dist0 = distance(data[i], c0);
                    double dist1 = distance(data[i], c1);
                    int newAssignment = (dist0 < dist1) ? 0 : 1;
                    
                    if (assignments[i] != newAssignment) {
                        assignments[i] = newAssignment;
                        changed = true;
                    }
                }
                
                // Update centroids
                updateCentroid(data, assignments, 0, c0);
                updateCentroid(data, assignments, 1, c1);
            }
            
            return assignments;
        }
        
        private static double distance(double[] p1, double[] p2) {
            double dx = p1[0] - p2[0];
            double dy = p1[1] - p2[1];
            return dx * dx + dy * dy;
        }
        
        private static void updateCentroid(double[][] data, int[] assignments, int cluster, double[] centroid) {
            double sumX = 0, sumY = 0;
            int count = 0;
            
            for (int i = 0; i < data.length; i++) {
                if (assignments[i] == cluster) {
                    sumX += data[i][0];
                    sumY += data[i][1];
                    count++;
                }
            }
            
            if (count > 0) {
                centroid[0] = sumX / count;
                centroid[1] = sumY / count;
            }
        }
    }
}