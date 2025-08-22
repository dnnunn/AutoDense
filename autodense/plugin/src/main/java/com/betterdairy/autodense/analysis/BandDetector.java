package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Band;
import com.betterdairy.autodense.model.Models.Lane;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

public final class BandDetector {
    private BandDetector() {}

    public static List<Band> findBands(ImagePlus imp, Lane lane) {
        // Enhanced band detection with local prominence and adaptive parameters
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

        // Adaptive smoothing based on gel type
        // DNA: smaller features (~6-10 px), SDS: larger features (~8-12 px)
        int radius = estimateGelType(laneProfile) == GelType.DNA ? 
            Math.max(2, h / 300) : Math.max(3, h / 200);
        float[] smooth = smoothProfile(laneProfile, radius);

        // Invert profile for dark bands (typical in gels)
        float[] inverted = new float[h];
        float maxVal = getMax(smooth);
        for (int i = 0; i < h; i++) {
            inverted[i] = maxVal - smooth[i];
        }
        
        // Adaptive parameters based on gel type and lane height
        GelType gelType = estimateGelType(laneProfile);
        int minPeakDistance = gelType == GelType.DNA ? 
            Math.max(6, h / 100) : Math.max(8, h / 80); // DNA: 6-10px, SDS: 8-12px
        
        double minProminence = 0.05; // 5% relative prominence
        double minHeight = getMean(inverted) + 0.5 * getStd(inverted);

        // Enhanced peak detection with local prominence and width validation
        List<BandCandidate> candidates = findBandCandidates(inverted, minHeight, minProminence, minPeakDistance);
        
        // Width prior: reject spikes (< 2 px) and smeared blobs (> 1.5× median width)
        candidates = filterByWidth(candidates, inverted);
        
        // Convert candidates to bands with refined measurements
        List<Band> bands = new ArrayList<>();
        int idx = 1;
        for (BandCandidate candidate : candidates) {
            // Refine peak position with adaptive window
            int refinedPeak = refinePeakPosition(laneProfile, candidate.peak, 5);
            int L = Peaks.leftValley(inverted, refinedPeak);
            int R = Peaks.rightValley(inverted, refinedPeak);
            double apex = Peaks.subpixelApex(inverted, refinedPeak);
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
        
        // Above flank using array indexing
        int aboveStart = Math.max(0, y0 - flankHeight - 2);
        int aboveEnd = Math.max(0, y0 - 2);
        for (int y = aboveStart; y <= aboveEnd; y++) {
            int rowStart = y * width;
            for (int x = x0; x <= x1; x++) {
                bgSamples.add(pixels[rowStart + x]);
            }
        }
        
        // Below flank using array indexing
        int belowStart = Math.min(ip.getHeight() - 1, y1 + 2);
        int belowEnd = Math.min(ip.getHeight() - 1, y1 + 2 + flankHeight);
        for (int y = belowStart; y <= belowEnd; y++) {
            int rowStart = y * width;
            for (int x = x0; x <= x1; x++) {
                bgSamples.add(pixels[rowStart + x]);
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
}
