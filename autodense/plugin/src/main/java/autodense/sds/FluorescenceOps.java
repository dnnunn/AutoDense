package autodense.sds;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.MaximumFinder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import autodense.util.Csv;
import autodense.util.OverlayExporter;

import java.awt.Color;
import java.awt.Polygon;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Advanced computer vision algorithms specifically designed for fluorescence gel band detection.
 * 
 * Key innovations:
 * 1. Multi-algorithm approach: Profile peaks + 2D blob detection + morphological operations
 * 2. Fluorescence-optimized profile analysis (NO intensity inversion)
 * 3. Adaptive thresholding for varying fluorescence intensity
 * 4. Geometric filtering for valid band shapes
 * 5. Multi-scale detection with validation
 * 
 * @author Claude Vision Engineer
 */
public final class FluorescenceOps {

    private FluorescenceOps() {
        // Utility class - prevent instantiation
    }

    /**
     * Advanced fluorescence band detection using multi-algorithm fusion approach.
     * 
     * @param imp Input image with lanes already detected
     * @param minProm Minimum prominence for peak detection (0.001-0.01 for fluorescence)
     * @param minDistPx Minimum distance between bands in pixels
     * @param backgroundRemovalRadius Background subtraction radius (use 0 for fluorescence)
     * @param outDir Output directory for results
     * @return Map containing detection results and file paths
     */
    public static Map<String, Object> detectFluorescenceBands(final ImagePlus imp, final double minProm, 
            final int minDistPx, final Double backgroundRemovalRadius, final Path outDir) throws Exception {
        
        Overlay ov = imp.getOverlay();
        if (ov == null) {
            throw new IllegalStateException("No lanes overlay");
        }
        
        List<Roi> lanes = Arrays.asList(ov.toArray());
        List<Map<String,Object>> allBands = new ArrayList<>();
        int globalBandId = 1;
        
        // Process each lane with multi-algorithm approach
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            Roi lane = lanes.get(laneIndex);
            
            // Extract lane region
            imp.setRoi(lane);
            ImagePlus laneImg = new ImagePlus("lane", imp.getProcessor().crop());
            
            // Apply minimal preprocessing for fluorescence
            preprocessFluorescenceImage(laneImg, backgroundRemovalRadius);
            
            // Multi-algorithm detection fusion
            List<FluorescenceBand> laneBands = detectBandsMultiAlgorithm(laneImg, lane, laneIndex + 1, minProm, minDistPx);
            
            // Convert to output format and add to overlay
            for (FluorescenceBand band : laneBands) {
                // Add band overlay
                Line tick = new Line(
                    lane.getBounds().x, 
                    band.globalY, 
                    lane.getBounds().x + lane.getBounds().width, 
                    band.globalY
                );
                tick.setStrokeColor(Color.getHSBColor(0.58f, 1f, 1f));
                tick.setStrokeWidth(2.0); // Thicker line for fluorescence visibility
                tick.setName(String.format("fluor_band_%03d_lane_%02d", globalBandId, band.laneNumber));
                ov.add(tick);
                
                // Add to results
                allBands.add(Map.of(
                    "id", globalBandId,
                    "lane", band.laneNumber,
                    "y_px", band.globalY,
                    "intensity", band.intensity,
                    "confidence", band.confidence,
                    "width", band.width,
                    "algorithm", band.detectionMethod
                ));
                globalBandId++;
            }
        }
        
        imp.setOverlay(ov);
        
        // Export results
        Path bandsPng = outDir.resolve("fluorescence_bands_overlay.png");
        OverlayExporter.exportOverlayPNG(imp, bandsPng.toFile());
        
        Path csv = outDir.resolve("fluorescence_bands.csv");
        Csv.write(csv, allBands, List.of("id", "lane", "y_px", "intensity", "confidence", "width", "algorithm"));
        
        return Map.of(
            "bands_table", csv.toString(), 
            "bands_overlay_png", bandsPng.toString(),
            "total_bands", allBands.size(),
            "detection_method", "multi_algorithm_fluorescence"
        );
    }
    
    /**
     * Multi-algorithm band detection fusion for maximum sensitivity and accuracy.
     */
    private static List<FluorescenceBand> detectBandsMultiAlgorithm(ImagePlus laneImg, Roi originalLane, 
            int laneNumber, double minProm, int minDistPx) {
        
        // Algorithm 1: Enhanced Profile Peak Detection (fixed fluorescence version)
        List<FluorescenceBand> profileBands = detectBandsProfileMethod(laneImg, originalLane, laneNumber, minProm, minDistPx);
        
        // Algorithm 2: 2D Blob Detection for Fluorescent Spots
        List<FluorescenceBand> blobBands = detectBands2DBlobMethod(laneImg, originalLane, laneNumber);
        
        // Algorithm 3: Morphological Top-Hat Detection
        List<FluorescenceBand> morphBands = detectBandsMorphologicalMethod(laneImg, originalLane, laneNumber);
        
        // Algorithm 4: Adaptive Threshold Detection
        List<FluorescenceBand> adaptiveBands = detectBandsAdaptiveThreshold(laneImg, originalLane, laneNumber);
        
        // Fusion: Combine and validate detections
        List<FluorescenceBand> fusedBands = fuseBandDetections(profileBands, blobBands, morphBands, adaptiveBands, minDistPx);
        
        // Sort by Y position (top to bottom)
        Collections.sort(fusedBands, Comparator.comparingInt(b -> b.localY));
        
        return fusedBands;
    }
    
    /**
     * Algorithm 1: Enhanced Profile Peak Detection - FIXED for fluorescence
     * Key fix: NO intensity inversion for fluorescence detection
     */
    private static List<FluorescenceBand> detectBandsProfileMethod(ImagePlus laneImg, Roi originalLane, 
            int laneNumber, double minProm, int minDistPx) {
        
        ImageProcessor ip = laneImg.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();
        
        // Build horizontal intensity profile - CRITICAL: No inversion for fluorescence!
        double[] profile = new double[height];
        for (int y = 0; y < height; y++) {
            double sum = 0;
            for (int x = 0; x < width; x++) {
                // FIXED: Use raw pixel values for fluorescence (bright = high values)
                sum += (ip.get(x, y) & 0xff);  // NO (255 - pixel) inversion!
            }
            profile[y] = sum / width; // Average intensity across lane width
        }
        
        // Smooth profile to reduce noise
        profile = smoothGaussian(profile, 1.5);
        
        // Dynamic prominence calculation based on profile statistics
        double maxIntensity = Arrays.stream(profile).max().orElse(0);
        double minIntensity = Arrays.stream(profile).min().orElse(0);
        double dynamicRange = maxIntensity - minIntensity;
        double adaptivePromMinimum = Math.max(minProm * dynamicRange, 5.0); // Minimum 5 intensity units
        
        // Enhanced peak finding with fluorescence-specific parameters
        List<Integer> peaks = findFluorescencePeaks(profile, adaptivePromMinimum, minDistPx);
        
        // Convert to band objects
        List<FluorescenceBand> bands = new ArrayList<>();
        for (int peakY : peaks) {
            FluorescenceBand band = new FluorescenceBand();
            band.localY = peakY;
            band.globalY = peakY + originalLane.getBounds().y;
            band.laneNumber = laneNumber;
            band.intensity = profile[peakY];
            band.confidence = calculatePeakConfidence(profile, peakY, adaptivePromMinimum);
            band.width = estimateBandWidth(profile, peakY);
            band.detectionMethod = "enhanced_profile";
            bands.add(band);
        }
        
        return bands;
    }
    
    /**
     * Algorithm 2: 2D Blob Detection for Fluorescent Spots
     */
    private static List<FluorescenceBand> detectBands2DBlobMethod(ImagePlus laneImg, Roi originalLane, int laneNumber) {
        ImageProcessor ip = laneImg.getProcessor();
        
        // Convert to 8-bit for blob detection
        if (!(ip instanceof ByteProcessor)) {
            ip = ip.convertToByte(true);
        }
        
        // Use ImageJ's MaximumFinder for blob detection
        MaximumFinder finder = new MaximumFinder();
        
        // Adaptive threshold based on image statistics
        ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.MEAN | Measurements.STD_DEV, null);
        double threshold = stats.mean + 2.0 * stats.stdDev; // 2-sigma above mean
        
        // Find maxima (bright spots)
        Polygon maxima = finder.getMaxima(ip, threshold, false);
        
        List<FluorescenceBand> bands = new ArrayList<>();
        if (maxima != null) {
            for (int i = 0; i < maxima.npoints; i++) {
                int x = maxima.xpoints[i];
                int y = maxima.ypoints[i];
                
                FluorescenceBand band = new FluorescenceBand();
                band.localY = y;
                band.globalY = y + originalLane.getBounds().y;
                band.laneNumber = laneNumber;
                band.intensity = ip.get(x, y) & 0xff;
                band.confidence = 0.8; // Default confidence for blob detection
                band.width = 3; // Estimated width for blob
                band.detectionMethod = "2d_blob";
                bands.add(band);
            }
        }
        
        return bands;
    }
    
    /**
     * Algorithm 3: Morphological Top-Hat Detection
     */
    private static List<FluorescenceBand> detectBandsMorphologicalMethod(ImagePlus laneImg, Roi originalLane, int laneNumber) {
        ImageProcessor ip = laneImg.getProcessor().duplicate();
        
        // Top-hat transform to enhance bright features
        ImageProcessor tophat = morphologicalTopHat(ip, 3);
        
        // Build profile from top-hat result
        int height = tophat.getHeight();
        int width = tophat.getWidth();
        double[] profile = new double[height];
        
        for (int y = 0; y < height; y++) {
            double sum = 0;
            for (int x = 0; x < width; x++) {
                sum += (tophat.get(x, y) & 0xff);
            }
            profile[y] = sum / width;
        }
        
        // Find peaks in top-hat profile
        List<Integer> peaks = findFluorescencePeaks(profile, 10.0, 3);
        
        List<FluorescenceBand> bands = new ArrayList<>();
        for (int peakY : peaks) {
            FluorescenceBand band = new FluorescenceBand();
            band.localY = peakY;
            band.globalY = peakY + originalLane.getBounds().y;
            band.laneNumber = laneNumber;
            band.intensity = profile[peakY];
            band.confidence = 0.7;
            band.width = 2;
            band.detectionMethod = "morphological_tophat";
            bands.add(band);
        }
        
        return bands;
    }
    
    /**
     * Algorithm 4: Adaptive Threshold Detection
     */
    private static List<FluorescenceBand> detectBandsAdaptiveThreshold(ImagePlus laneImg, Roi originalLane, int laneNumber) {
        ImageProcessor ip = laneImg.getProcessor();
        int height = ip.getHeight();
        int width = ip.getWidth();
        
        // Adaptive threshold: local mean + offset
        List<FluorescenceBand> bands = new ArrayList<>();
        int windowSize = 15; // Local window for adaptive threshold
        
        for (int y = windowSize/2; y < height - windowSize/2; y++) {
            // Calculate local statistics
            double localMean = 0;
            double localMax = 0;
            int count = 0;
            
            for (int dy = -windowSize/2; dy <= windowSize/2; dy++) {
                for (int x = 0; x < width; x++) {
                    int pixel = ip.get(x, y + dy) & 0xff;
                    localMean += pixel;
                    localMax = Math.max(localMax, pixel);
                    count++;
                }
            }
            localMean /= count;
            
            // Check if current row has significantly bright pixels
            double rowMean = 0;
            for (int x = 0; x < width; x++) {
                rowMean += (ip.get(x, y) & 0xff);
            }
            rowMean /= width;
            
            // Adaptive threshold condition
            if (rowMean > localMean + 20 && rowMean > 100) { // Bright enough to be fluorescent
                FluorescenceBand band = new FluorescenceBand();
                band.localY = y;
                band.globalY = y + originalLane.getBounds().y;
                band.laneNumber = laneNumber;
                band.intensity = rowMean;
                band.confidence = 0.6;
                band.width = 2;
                band.detectionMethod = "adaptive_threshold";
                bands.add(band);
            }
        }
        
        return bands;
    }
    
    /**
     * Fuse detections from multiple algorithms to get final band list.
     */
    private static List<FluorescenceBand> fuseBandDetections(List<FluorescenceBand> profiles, 
            List<FluorescenceBand> blobs, List<FluorescenceBand> morphs, List<FluorescenceBand> adaptive, int minDistPx) {
        
        // Combine all detections
        List<FluorescenceBand> allDetections = new ArrayList<>();
        allDetections.addAll(profiles);
        allDetections.addAll(blobs);
        allDetections.addAll(morphs);
        allDetections.addAll(adaptive);
        
        if (allDetections.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Sort by Y position
        Collections.sort(allDetections, Comparator.comparingInt(b -> b.localY));
        
        // Merge nearby detections (within minDistPx)
        List<FluorescenceBand> mergedBands = new ArrayList<>();
        List<FluorescenceBand> currentGroup = new ArrayList<>();
        
        for (FluorescenceBand band : allDetections) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(band);
            } else {
                FluorescenceBand lastInGroup = currentGroup.get(currentGroup.size() - 1);
                if (Math.abs(band.localY - lastInGroup.localY) <= minDistPx) {
                    currentGroup.add(band); // Add to current group
                } else {
                    // Process current group and start new one
                    mergedBands.add(mergeBandGroup(currentGroup));
                    currentGroup.clear();
                    currentGroup.add(band);
                }
            }
        }
        
        // Process final group
        if (!currentGroup.isEmpty()) {
            mergedBands.add(mergeBandGroup(currentGroup));
        }
        
        return mergedBands;
    }
    
    /**
     * Merge a group of nearby band detections into single best detection.
     */
    private static FluorescenceBand mergeBandGroup(List<FluorescenceBand> group) {
        if (group.size() == 1) {
            return group.get(0);
        }
        
        // Find the detection with highest confidence or intensity
        FluorescenceBand best = group.stream()
                .max(Comparator.comparingDouble(b -> b.confidence * b.intensity))
                .orElse(group.get(0));
        
        // Enhance confidence based on multiple algorithm agreement
        best.confidence = Math.min(1.0, best.confidence + 0.1 * (group.size() - 1));
        best.detectionMethod = "fused_" + group.size() + "_algorithms";
        
        return best;
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Fluorescence-optimized peak finding (looks for BRIGHT peaks, not dark valleys).
     */
    private static List<Integer> findFluorescencePeaks(double[] profile, double minProminence, int minDist) {
        List<Integer> peaks = new ArrayList<>();
        int lastPeak = -9999;
        
        for (int i = 1; i < profile.length - 1; i++) {
            // Check if this is a local maximum
            if (profile[i] > profile[i-1] && profile[i] > profile[i+1]) {
                // Check prominence
                if (profile[i] >= minProminence && (i - lastPeak) >= minDist) {
                    peaks.add(i);
                    lastPeak = i;
                }
            }
        }
        
        return peaks;
    }
    
    /**
     * Calculate confidence score for a detected peak.
     */
    private static double calculatePeakConfidence(double[] profile, int peakPos, double minProm) {
        if (peakPos <= 0 || peakPos >= profile.length - 1) {
            return 0.0;
        }
        
        double peakValue = profile[peakPos];
        double leftMin = peakValue;
        double rightMin = peakValue;
        
        // Find local minima on both sides
        for (int i = peakPos - 1; i >= Math.max(0, peakPos - 10); i--) {
            leftMin = Math.min(leftMin, profile[i]);
        }
        for (int i = peakPos + 1; i < Math.min(profile.length, peakPos + 10); i++) {
            rightMin = Math.min(rightMin, profile[i]);
        }
        
        double actualProminence = peakValue - Math.max(leftMin, rightMin);
        return Math.min(1.0, actualProminence / minProm);
    }
    
    /**
     * Estimate band width from profile.
     */
    private static int estimateBandWidth(double[] profile, int peakPos) {
        double peakValue = profile[peakPos];
        double halfMax = peakValue * 0.5;
        
        int leftEdge = peakPos;
        int rightEdge = peakPos;
        
        // Find half-maximum points
        while (leftEdge > 0 && profile[leftEdge] > halfMax) leftEdge--;
        while (rightEdge < profile.length - 1 && profile[rightEdge] > halfMax) rightEdge++;
        
        return Math.max(1, rightEdge - leftEdge);
    }
    
    /**
     * Gaussian smoothing for noise reduction.
     */
    private static double[] smoothGaussian(double[] data, double sigma) {
        int kernelSize = (int)(6 * sigma) | 1; // Odd kernel size
        double[] kernel = new double[kernelSize];
        double sum = 0;
        int center = kernelSize / 2;
        
        // Generate Gaussian kernel
        for (int i = 0; i < kernelSize; i++) {
            double x = i - center;
            kernel[i] = Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }
        
        // Normalize kernel
        for (int i = 0; i < kernelSize; i++) {
            kernel[i] /= sum;
        }
        
        // Apply convolution
        double[] smoothed = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            double value = 0;
            double weightSum = 0;
            for (int k = 0; k < kernelSize; k++) {
                int pos = i + k - center;
                if (pos >= 0 && pos < data.length) {
                    value += data[pos] * kernel[k];
                    weightSum += kernel[k];
                }
            }
            smoothed[i] = value / weightSum;
        }
        
        return smoothed;
    }
    
    /**
     * Morphological top-hat transform to enhance bright features.
     */
    private static ImageProcessor morphologicalTopHat(ImageProcessor ip, int structureSize) {
        ImageProcessor result = ip.duplicate();
        
        // Simple approximation: original - opening
        // Opening = erosion followed by dilation
        for (int i = 0; i < structureSize; i++) {
            result.erode();
        }
        for (int i = 0; i < structureSize; i++) {
            result.dilate();
        }
        
        // Top-hat = original - opening
        ImageProcessor tophat = ip.duplicate();
        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x < ip.getWidth(); x++) {
                int original = ip.get(x, y) & 0xff;
                int opened = result.get(x, y) & 0xff;
                int tophatValue = Math.max(0, original - opened);
                tophat.set(x, y, tophatValue);
            }
        }
        
        return tophat;
    }
    
    /**
     * Minimal preprocessing specifically optimized for fluorescence images.
     */
    private static void preprocessFluorescenceImage(ImagePlus laneImg, Double backgroundRemovalRadius) {
        // Convert to 8-bit if needed
        if (!(laneImg.getProcessor() instanceof ByteProcessor)) {
            ImageProcessor ip = laneImg.getProcessor().convertToByte(true);
            laneImg.setProcessor(ip);
        }
        
        // Optional background subtraction (use very small radius or skip entirely for fluorescence)
        double bgRadius = (backgroundRemovalRadius != null && backgroundRemovalRadius > 0) ? backgroundRemovalRadius : 0.0;
        if (bgRadius > 0) {
            new BackgroundSubtracter().rollingBallBackground(laneImg.getProcessor(), bgRadius, false, false, false, false, false);
        }
        
        // Light Gaussian smoothing to reduce noise while preserving edges
        laneImg.getProcessor().smooth();
    }
    
    /**
     * Data structure for fluorescence band detection results.
     */
    private static class FluorescenceBand {
        int localY;           // Y position within lane
        int globalY;          // Y position in full image
        int laneNumber;       // Lane number (1-based)
        double intensity;     // Peak intensity
        double confidence;    // Detection confidence (0-1)
        int width;           // Estimated band width in pixels
        String detectionMethod; // Which algorithm detected this band
    }
}