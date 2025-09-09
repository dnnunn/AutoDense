package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Band;
import com.betterdairy.autodense.model.Models.Lane;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.awt.Rectangle;
import autodense.sds.FluorescenceOps;

/**
 * DEPRECATED: Legacy Java band detection - replaced by Python bridge in Phase III heart transplant.
 * 
 * This class is maintained for backward compatibility only. New code should use:
 * com.betterdairy.autodense.service.AnalysisService with Python bridge for superior
 * computer vision analysis.
 * 
 * Migration: Replace BandDetector.findBands() calls with:
 * AnalysisService.analyzeGel(image, config) which includes both lane and band detection
 * 
 * @deprecated Since AutoDense v3.0 (Phase III). Use AnalysisService instead.
 */
@Deprecated
public final class BandDetector {
    private BandDetector() {}

    /**
     * Find bands with configuration-aware baseline parameters
     * 
     * @param imp ImagePlus to analyze
     * @param lane Lane to detect bands within
     * @param config Configuration map with bands.baseline settings
     * @return List of detected bands with proper baseline handling
     */
    public static List<Band> findBands(ImagePlus imp, Lane lane, Map<String,Object> config) {
        // BREAKTHROUGH: Check if fluorescence detection is enabled
        if (config != null && isFluorescenceMode(config)) {
            System.err.println("[FLUORESCENCE_BREAKTHROUGH] Using advanced fluorescence detection algorithms");
            return findFluorescenceBands(imp, lane, config);
        }
        
        // Standard gel detection pathway (existing logic)
        // Get band-specific baseline parameters from config
        int laneWidth = lane.xEnd() - lane.xStart() + 1;
        BaselineParams bandBaseline = BaselineParams.fromBandConfig(config, laneWidth);
        
        // Build vertical profile for this lane
        float[] laneProfile = buildLaneProfile(imp, lane);
        
        // Apply band-specific baseline subtraction
        double[] laneProfileDouble = new double[laneProfile.length];
        for (int i = 0; i < laneProfile.length; i++) {
            laneProfileDouble[i] = laneProfile[i];
        }
        
        // Use band-specific baseline parameters for gentle processing
        double[] baselineCorrected = BaselineUtils.subtractBaseline(
            laneProfileDouble, bandBaseline, "bands", System.err);
        
        // Convert back to float array for existing peak detection logic
        float[] correctedProfile = new float[baselineCorrected.length];
        for (int i = 0; i < baselineCorrected.length; i++) {
            correctedProfile[i] = (float) baselineCorrected[i];
        }
        
        // Continue with existing band detection logic using corrected profile and config
        return findBandsFromProfile(imp, lane, correctedProfile, config);
    }

    /**
     * Legacy method - uses hardcoded parameters
     * @deprecated Use findBands(ImagePlus, Lane, Map<String,Object>) instead
     */
    @Deprecated
    public static List<Band> findBands(ImagePlus imp, Lane lane) {
        // Build lane profile and use existing logic
        float[] laneProfile = buildLaneProfile(imp, lane);
        return findBandsFromProfile(imp, lane, laneProfile, null);
    }

    /**
     * Extract helper method: build vertical intensity profile for a lane
     */
    private static float[] buildLaneProfile(ImagePlus imp, Lane lane) {
        ImageProcessor ip = imp.getProcessor();
        int w = ip.getWidth();
        int h = ip.getHeight();
        int x0 = Math.max(0, lane.xStart());
        int x1 = Math.min(w - 1, lane.xEnd());
        
        // Fast array-based projection (avoid getf() in inner loop)
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        float[] laneProfile = new float[h];
        int laneWidth = x1 - x0 + 1;
        
        for (int y = 0; y < h; y++) {
            float sum = 0;
            for (int x = x0; x <= x1; x++) {
                sum += pixels[y * w + x];
            }
            laneProfile[y] = sum / laneWidth; // mean intensity row-wise in lane
        }
        
        return laneProfile;
    }

    /**
     * Extract helper method: perform band detection from a lane profile
     */
    private static List<Band> findBandsFromProfile(ImagePlus imp, Lane lane, float[] laneProfile, Map<String,Object> config) {
        ImageProcessor ip = imp.getProcessor();
        int h = imp.getHeight();
        int x0 = Math.max(0, lane.xStart());
        int x1 = Math.min(imp.getWidth() - 1, lane.xEnd());

        // Adaptive smoothing based on gel type
        // DNA: smaller features (~6-10 px), SDS: larger features (~8-12 px)
        int radius = estimateGelType(laneProfile) == GelType.DNA ? 
            Math.max(2, h / 300) : Math.max(3, h / 200);
        float[] smooth = smoothProfile(laneProfile, radius);

        // CRITICAL FIX: Check for fluorescence mode to avoid incorrect inversion
        float[] processed;
        if (config != null && isFluorescenceMode(config)) {
            // FLUORESCENCE: Use bright peaks directly (NO inversion)
            processed = smooth.clone();
            System.err.println("[FLUORESCENCE_FIX] Using direct bright peak detection - NO inversion");
        } else {
            // Standard gel: Invert profile for dark bands (typical in gels)
            processed = new float[h];
            float maxVal = getMax(smooth);
            for (int i = 0; i < h; i++) {
                processed[i] = maxVal - smooth[i];
            }
            System.err.println("[STANDARD_GEL] Using inverted profile for dark band detection");
        }
        
        // Get minimum peak distance from configuration or use adaptive fallback
        int minPeakDistance = getMinPeakDistanceFromConfig(config, laneProfile, h);
        
        double minProminence = 0.05; // 5% relative prominence
        double minHeight = getMean(processed) + 0.5 * getStd(processed);

        // Enhanced peak detection with local prominence and width validation
        List<BandCandidate> candidates = findBandCandidates(processed, minHeight, minProminence, minPeakDistance);
        
        // Width prior: reject spikes (< 2 px) and smeared blobs (> 1.5× median width)
        candidates = filterByWidth(candidates, processed);
        
        // Convert candidates to bands with refined measurements
        List<Band> bands = new ArrayList<>();
        int idx = 1;
        for (BandCandidate candidate : candidates) {
            // Refine peak position with adaptive window
            int refinedPeak = refinePeakPosition(laneProfile, candidate.peak, 5);
            int L = Peaks.leftValley(processed, refinedPeak);
            int R = Peaks.rightValley(processed, refinedPeak);
            double apex = Peaks.subpixelApex(processed, refinedPeak);
            double snr = Quant.snr(laneProfile, refinedPeak, L, R);
            
            // Width validation
            int width = R - L + 1;
            if (width < 2 || snr < 1.0) continue; // Reject spikes and low SNR
            
            // Background estimation using flanking regions
            double bg = estimateLocalBackground(ip, x0, x1, L, R);
            
            // Compute band area with background correction
            double area = 0;
            for (int yi = L; yi <= R; yi++) {
                area += Math.max(0, bg - laneProfile[yi]);
            }
            
            bands.add(new Band(idx++, (int)apex, area, bg, snr));
        }

        return bands;
    }
    
    // Internal classes and helper methods
    private static class BandCandidate {
        int peak;
        double prominence;
        double height;
        
        BandCandidate(int peak, double prominence, double height) {
            this.peak = peak;
            this.prominence = prominence;
            this.height = height;
        }
    }
    
    private static enum GelType {
        DNA, SDS
    }
    
    private static GelType estimateGelType(float[] profile) {
        // Simple heuristic: DNA gels typically have sharper, narrower bands
        // SDS gels have broader bands due to protein size distribution
        float[] smoothed = smoothProfile(profile, 3);
        int peakCount = countPeaks(smoothed, 0.1f);
        int height = profile.length;
        
        // DNA gels typically have more, sharper peaks relative to gel height
        return (peakCount / (double)height > 0.05) ? GelType.DNA : GelType.SDS;
    }
    
    private static int countPeaks(float[] profile, float minProminence) {
        int count = 0;
        for (int i = 1; i < profile.length - 1; i++) {
            if (Peaks.isProminent(profile, i, minProminence)) {
                count++;
            }
        }
        return count;
    }
    
    private static float[] smoothProfile(float[] profile, int radius) {
        int n = profile.length;
        float[] smooth = new float[n];
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - radius);
            int end = Math.min(n - 1, i + radius);
            float sum = 0;
            for (int j = start; j <= end; j++) {
                sum += profile[j];
            }
            smooth[i] = sum / (end - start + 1);
        }
        return smooth;
    }
    
    private static List<BandCandidate> findBandCandidates(float[] profile, double minHeight, 
                                                         double minProminence, int minDistance) {
        List<BandCandidate> candidates = new ArrayList<>();
        
        for (int i = 1; i < profile.length - 1; i++) {
            if (profile[i] > minHeight && profile[i] > profile[i-1] && profile[i] > profile[i+1]) {
                int L = Peaks.leftValley(profile, i);
                int R = Peaks.rightValley(profile, i);
                
                float baseLevel = Math.max(profile[L], profile[R]);
                double prominence = profile[i] - baseLevel;
                double relativeProminence = prominence / (getMax(profile) - getMin(profile));
                
                if (relativeProminence >= minProminence) {
                    // Check minimum distance from existing candidates
                    boolean tooClose = false;
                    for (BandCandidate existing : candidates) {
                        if (Math.abs(i - existing.peak) < minDistance) {
                            tooClose = true;
                            break;
                        }
                    }
                    
                    if (!tooClose) {
                        candidates.add(new BandCandidate(i, relativeProminence, profile[i]));
                    }
                }
            }
        }
        
        return candidates;
    }
    
    private static List<BandCandidate> filterByWidth(List<BandCandidate> candidates, float[] profile) {
        if (candidates.size() < 3) return candidates; // Need multiple bands to compute median
        
        // Calculate widths
        List<Integer> widths = new ArrayList<>();
        for (BandCandidate candidate : candidates) {
            int L = Peaks.leftValley(profile, candidate.peak);
            int R = Peaks.rightValley(profile, candidate.peak);
            widths.add(R - L + 1);
        }
        
        // Find median width
        Collections.sort(widths);
        int medianWidth = widths.get(widths.size() / 2);
        int maxWidth = (int)(medianWidth * 1.5); // 1.5× median threshold
        
        // Filter candidates
        List<BandCandidate> filtered = new ArrayList<>();
        for (BandCandidate candidate : candidates) {
            int L = Peaks.leftValley(profile, candidate.peak);
            int R = Peaks.rightValley(profile, candidate.peak);
            int width = R - L + 1;
            
            if (width >= 2 && width <= maxWidth) {
                filtered.add(candidate);
            }
        }
        
        return filtered;
    }
    
    private static int refinePeakPosition(float[] profile, int initialPeak, int windowSize) {
        int start = Math.max(0, initialPeak - windowSize);
        int end = Math.min(profile.length - 1, initialPeak + windowSize);
        return Peaks.argmax(profile, start, end);
    }
    
    private static double estimateLocalBackground(ImageProcessor ip, int x0, int x1, int y0, int y1) {
        // Use flanking regions above and below the band
        int flankHeight = Math.max(3, (y1 - y0 + 1) / 2);
        List<Float> bgSamples = new ArrayList<>();
        
        // Fast pixel array access
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        int width = ip.getWidth();
        
        // Above flank using array indexing with bounds checking
        int aboveStart = Math.max(0, y0 - flankHeight - 2);
        int aboveEnd = Math.max(0, y0 - 2);
        for (int y = aboveStart; y <= aboveEnd; y++) {
            int rowStart = y * width;
            for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                int pixelIndex = rowStart + x;
                if (pixelIndex >= 0 && pixelIndex < pixels.length) {
                    bgSamples.add(pixels[pixelIndex]);
                }
            }
        }
        
        // Below flank using array indexing with bounds checking
        int belowStart = Math.min(ip.getHeight() - 1, y1 + 2);
        int belowEnd = Math.min(ip.getHeight() - 1, y1 + 2 + flankHeight);
        for (int y = belowStart; y <= belowEnd; y++) {
            int rowStart = y * width;
            for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                int pixelIndex = rowStart + x;
                if (pixelIndex >= 0 && pixelIndex < pixels.length) {
                    bgSamples.add(pixels[pixelIndex]);
                }
            }
        }
        
        // Return median of background samples
        if (bgSamples.isEmpty()) return 0.0;
        Collections.sort(bgSamples);
        return bgSamples.get(bgSamples.size() / 2);
    }
    
    // Statistical helper methods
    private static float getMax(float[] array) {
        float max = Float.NEGATIVE_INFINITY;
        for (float val : array) {
            if (val > max) max = val;
        }
        return max;
    }
    
    private static float getMin(float[] array) {
        float min = Float.POSITIVE_INFINITY;
        for (float val : array) {
            if (val < min) min = val;
        }
        return min;
    }
    
    private static float getMean(float[] array) {
        float sum = 0;
        for (float val : array) sum += val;
        return sum / array.length;
    }
    
    private static float getStd(float[] array) {
        float mean = getMean(array);
        float sumSq = 0;
        for (float val : array) {
            sumSq += (val - mean) * (val - mean);
        }
        return (float)Math.sqrt(sumSq / Math.max(1, array.length - 1));
    }
    
    /**
     * Extract minimum peak distance from configuration with adaptive fallback
     */
    private static int getMinPeakDistanceFromConfig(Map<String,Object> config, float[] laneProfile, int height) {
        if (config != null) {
            System.err.printf("[BAND_CONFIG] Received config with keys: %s%n", config.keySet());
            // Try to get bands section from config
            Object bandsObj = config.get("bands");
            System.err.printf("[BAND_CONFIG] bands object type: %s, value: %s%n", 
                bandsObj != null ? bandsObj.getClass().getSimpleName() : "null", bandsObj);
            if (bandsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String,Object> bandsConfig = (Map<String,Object>) bandsObj;
                System.err.printf("[BAND_CONFIG] bands section keys: %s%n", bandsConfig.keySet());
                
                // First priority: explicit pixel distance (check both naming conventions)
                Object minPeakDistancePx = bandsConfig.get("min_peak_distance_px");
                if (minPeakDistancePx == null) {
                    minPeakDistancePx = bandsConfig.get("min_distance_px");
                }
                if (minPeakDistancePx instanceof Number) {
                    int pixelDistance = ((Number) minPeakDistancePx).intValue();
                    // Clamp to reasonable range (6-32px)
                    int clampedDistance = Math.max(6, Math.min(32, pixelDistance));
                    System.err.printf("[BAND_CONFIG] Using min_distance_px=%d (clamped from %d)%n", clampedDistance, pixelDistance);
                    return clampedDistance;
                }
                
                // Second priority: fractional distance of lane height
                Object minPeakDistanceFrac = bandsConfig.get("min_peak_distance_frac");
                if (minPeakDistanceFrac instanceof Number) {
                    double frac = ((Number) minPeakDistanceFrac).doubleValue();
                    int fracDistance = Math.max(6, (int)(height * frac));
                    return Math.min(32, fracDistance);
                }
            }
        }
        
        // Fallback: adaptive parameters based on gel type and lane height
        GelType gelType = estimateGelType(laneProfile);
        int adaptiveDistance = gelType == GelType.DNA ? 
            Math.max(6, height / 100) : Math.max(8, height / 80); // DNA: 6-10px, SDS: 8-12px
        System.err.printf("[BAND_CONFIG] Using adaptive fallback min_distance_px=%d (gel_type=%s, height=%d)%n", 
            adaptiveDistance, gelType, height);
        return adaptiveDistance;
    }

    private static double medianSideBackground(ImageProcessor ip, int x0, int x1, int u, int d, int expand) {
        int w = ip.getWidth();
        int left0 = Math.max(0, x0 - expand);
        int left1 = Math.max(0, x0 - 1);
        int right0 = Math.min(w - 1, x1 + 1);
        int right1 = Math.min(w - 1, x1 + expand);
        double[] samples = new double[Math.max(1, (d - u + 1) * ( (left1>=left0? (left1-left0+1):0) + (right1>=right0? (right1-right0+1):0) ))];
        int idx = 0;
        for (int y = u; y <= d; y++) {
            for (int x = left0; x <= left1; x++) samples[idx++] = ip.get(x, y) & 0xFF;
            for (int x = right0; x <= right1; x++) samples[idx++] = ip.get(x, y) & 0xFF;
        }
        if (idx == 0) return 255.0; // fallback white
        return medianOfPrefix(samples, idx);
    }

    private static double medianTopBottomBackground(ImageProcessor ip, int x0, int x1, int u, int d, int expand) {
        int h = ip.getHeight();
        int top0 = Math.max(0, u - expand);
        int top1 = Math.max(0, u - 1);
        int bot0 = Math.min(h - 1, d + 1);
        int bot1 = Math.min(h - 1, d + expand);
        double[] samples = new double[Math.max(1, (x1 - x0 + 1) * ( (top1>=top0? (top1-top0+1):0) + (bot1>=bot0? (bot1-bot0+1):0) ))];
        int idx = 0;
        for (int y = top0; y <= top1; y++) for (int x = x0; x <= x1; x++) samples[idx++] = ip.get(x, y) & 0xFF;
        for (int y = bot0; y <= bot1; y++) for (int x = x0; x <= x1; x++) samples[idx++] = ip.get(x, y) & 0xFF;
        if (idx == 0) return 255.0;
        return medianOfPrefix(samples, idx);
    }

    private static double medianOfPrefix(double[] a, int n) {
        Arrays.sort(a, 0, n);
        int mid = n / 2;
        if ((n & 1) == 1) return a[mid];
        return 0.5 * (a[mid - 1] + a[mid]);
    }

    // ============================================================================
    // ADVANCED QUANTIFICATION METHODS (from FijiBandDetector)
    // ============================================================================
    
    /**
     * Quantifies a single band using advanced Fiji algorithms.
     * Provides comprehensive measurements including background correction.
     */
    public static BandQuantification quantifyBand(
            ImagePlus imp, ij.gui.Roi roi,
            BandQuantification.BackgroundRegion region,
            BandQuantification.BackgroundMethod method,
            int expansionPixels,
            BandQuantification.ChannelWeights channelWeights,
            boolean resetScale,
            String laneId, String bandId) {
        
        // Apply channel weights if specified
        if (channelWeights != null) {
            ij.IJ.run(imp, "RGB Weights...", 
                String.format("red=%f green=%f blue=%f", 
                    channelWeights.getRed(), 
                    channelWeights.getGreen(), 
                    channelWeights.getBlue()));
        }
        
        // Reset scale for consistent measurements
        if (resetScale) {
            ij.IJ.run(imp, "Set Scale...", "distance=0 known=0 pixel=1 unit=pixel global");
        }
        
        // Get band measurements
        imp.setRoi(roi);
        ij.process.ImageStatistics stats = imp.getStatistics();
        
        // Calculate background based on specified method
        ij.gui.Roi backgroundRoi = createBackgroundRoi(roi, region, expansionPixels, imp);
        double backgroundValue = calculateBackground(imp, backgroundRoi, method);
        
        // Calculate signal and total using Fiji standard formulas
        double signal = stats.area * (stats.mean - backgroundValue);
        double total = stats.area * stats.mean;
        
        return new BandQuantification(
            signal, total, stats.area, stats.mean, backgroundValue,
            region, method, expansionPixels, channelWeights,
            roi.getBounds(), roi.getName(), laneId, bandId, resetScale
        );
    }
    
    /**
     * Creates background ROI based on specified region method
     */
    private static ij.gui.Roi createBackgroundRoi(ij.gui.Roi bandRoi, 
            BandQuantification.BackgroundRegion region,
            int expansionPixels, ImagePlus imp) {
        
        Rectangle bounds = bandRoi.getBounds();
        
        switch (region) {
            case ALL:
                // Expand ROI by specified pixels
                int x = Math.max(0, bounds.x - expansionPixels);
                int y = Math.max(0, bounds.y - expansionPixels);
                int w = Math.min(imp.getWidth() - x, bounds.width + 2 * expansionPixels);
                int h = Math.min(imp.getHeight() - y, bounds.height + 2 * expansionPixels);
                return new ij.gui.Roi(x, y, w, h);
                
            case TOP_BOTTOM:
                // Create ROIs above/below band
                int adjY = bounds.y + bounds.height + 5;
                return new ij.gui.Roi(bounds.x, adjY, bounds.width, Math.min(20, imp.getHeight() - adjY));
                
            case SIDES:
            default:
                // Sample from lane edges (left side)
                return new ij.gui.Roi(Math.max(0, bounds.x - 10), bounds.y, 
                                    Math.min(10, bounds.x), bounds.height);
        }
    }
    
    /**
     * Calculates background value using specified statistical method
     */
    private static double calculateBackground(ImagePlus imp, ij.gui.Roi backgroundRoi, 
            BandQuantification.BackgroundMethod method) {
        
        if (backgroundRoi == null) return 0.0;
        
        imp.setRoi(backgroundRoi);
        ij.process.ImageStatistics stats = imp.getStatistics();
        
        switch (method) {
            case MEDIAN: return stats.median;
            case MEAN:
            default: return stats.mean;
        }
    }
    
    // ==================== FLUORESCENCE DETECTION METHODS ====================
    
    /**
     * Check if fluorescence detection mode is enabled in configuration.
     */
    private static boolean isFluorescenceMode(Map<String, Object> config) {
        if (config == null) return false;
        
        // Check multiple possible fluorescence indicators
        
        // 1. Explicit fluorescence flag in workflow section
        @SuppressWarnings("unchecked")
        Map<String, Object> workflow = (Map<String, Object>) config.get("workflow");
        if (workflow != null && Boolean.TRUE.equals(workflow.get("use_fluorescence_ops"))) {
            return true;
        }
        
        // 2. Peak detection method indicator
        String peakMethod = (String) config.get("peak_detection_method");
        if (peakMethod != null && peakMethod.contains("fluorescence")) {
            return true;
        }
        
        // 3. Fluorescence section in config
        @SuppressWarnings("unchecked")
        Map<String, Object> fluorescence = (Map<String, Object>) config.get("fluorescence");
        if (fluorescence != null && Boolean.TRUE.equals(fluorescence.get("enable_advanced_detection"))) {
            return true;
        }
        
        // 4. Check pre-processing polarity (fluorescence should have invert_polarity: false)
        @SuppressWarnings("unchecked")
        Map<String, Object> pre = (Map<String, Object>) config.get("pre");
        if (pre != null) {
            Object invertPolarityObj = pre.get("invert_polarity");
            Boolean invertPolarity = null;
            if (invertPolarityObj instanceof Boolean) {
                invertPolarity = (Boolean) invertPolarityObj;
            } else if (invertPolarityObj instanceof String) {
                String invertPolarityStr = (String) invertPolarityObj;
                if ("true".equalsIgnoreCase(invertPolarityStr)) {
                    invertPolarity = true;
                } else if ("false".equalsIgnoreCase(invertPolarityStr)) {
                    invertPolarity = false;
                }
                // For "auto" or other strings, leave as null
            }
            if (Boolean.FALSE.equals(invertPolarity)) {
                // Could be fluorescence, but not definitive - check other indicators
                if (peakMethod != null && (peakMethod.contains("etbr") || peakMethod.contains("fluoresc"))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Advanced fluorescence band detection using FluorescenceOps algorithms.
     */
    private static List<Band> findFluorescenceBands(ImagePlus imp, Lane lane, Map<String, Object> config) {
        try {
            // Create a lane-specific ImagePlus for FluorescenceOps
            ImageProcessor fullIP = imp.getProcessor();
            int x0 = Math.max(0, lane.xStart());
            int x1 = Math.min(fullIP.getWidth() - 1, lane.xEnd());
            int y0 = 0;
            int y1 = fullIP.getHeight() - 1;
            
            // Extract lane region
            fullIP.setRoi(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
            ImageProcessor laneIP = fullIP.crop();
            ImagePlus laneImp = new ImagePlus("lane_" + lane.index(), laneIP);
            
            // Get parameters from config
            double minProm = getDoubleFromConfig(config, "bands", "prominence_frac", 0.001);
            int minDistPx = getIntFromConfig(config, "bands", "min_distance_px", 3);
            Double backgroundRadius = getDoubleFromConfig(config, "pre", "background_removal_radius", 0.0);
            
            // Create a temporary output directory
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("fluorescence_detection");
            
            // Call FluorescenceOps detection
            java.util.Map<String, Object> result = FluorescenceOps.detectFluorescenceBands(
                laneImp, minProm, minDistPx, backgroundRadius, tempDir
            );
            
            // Parse results and convert to Band objects
            List<Band> bands = new ArrayList<>();
            String csvPath = (String) result.get("bands_table");
            
            if (csvPath != null && java.nio.file.Files.exists(java.nio.file.Paths.get(csvPath))) {
                // Read CSV results and convert to Band objects
                List<java.util.Map<String, String>> rows = autodense.util.Csv.read(java.nio.file.Paths.get(csvPath));
                
                for (java.util.Map<String, String> row : rows) {
                    int bandId = Integer.parseInt(row.get("id"));
                    int yLocal = Integer.parseInt(row.get("y_px"));
                    double intensity = Double.parseDouble(row.get("intensity"));
                    double confidence = Double.parseDouble(row.get("confidence"));
                    int width = Integer.parseInt(row.get("width"));
                    
                    // Convert to global coordinates
                    int yGlobal = yLocal; // y_px should already be in global coordinates from FluorescenceOps
                    
                    // Create Band object (area approximated from intensity * width)
                    double area = intensity * width;
                    double background = intensity * 0.1; // Estimate background as 10% of intensity
                    double snr = confidence * 10; // Convert confidence to SNR-like metric
                    
                    bands.add(new Band(bandId, yGlobal, area, background, snr));
                }
            }
            
            // Cleanup temp directory
            try {
                java.nio.file.Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            
            System.err.printf("[FLUORESCENCE_SUCCESS] Detected %d fluorescent bands using advanced algorithms%n", bands.size());
            return bands;
            
        } catch (Exception e) {
            System.err.println("[FLUORESCENCE_ERROR] Advanced detection failed, falling back to standard: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback to standard detection with fluorescence-aware parameters
            return findBandsFromProfile(imp, lane, buildLaneProfile(imp, lane), config);
        }
    }
    
    /**
     * Helper method to safely get double values from nested config maps.
     */
    private static double getDoubleFromConfig(Map<String, Object> config, String section, String key, double defaultValue) {
        if (config == null) return defaultValue;
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sectionMap = (Map<String, Object>) config.get(section);
        if (sectionMap == null) return defaultValue;
        
        Object value = sectionMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        return defaultValue;
    }
    
    /**
     * Helper method to safely get integer values from nested config maps.
     */
    private static int getIntFromConfig(Map<String, Object> config, String section, String key, int defaultValue) {
        if (config == null) return defaultValue;
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sectionMap = (Map<String, Object>) config.get(section);
        if (sectionMap == null) return defaultValue;
        
        Object value = sectionMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        
        return defaultValue;
    }
}
