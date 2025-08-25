package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.analysis.AssistModels.AssistBand;
import ij.ImagePlus;
import ij.process.ImageProcessor;

/**
 * Quantification utilities for measuring band intensities and properties.
 * Works with AssistBand model for detailed band analysis.
 */
public final class Quant {
    private Quant(){}

    /** 
     * Integrate band box minus local background from flank strips.
     * Creates a new AssistBand with quantified properties.
     */
    public static AssistBand integrateBand(ImagePlus imp, Lane lane, int bandTopY, int bandBotY) {
        int x0 = lane.xStart(), x1 = lane.xEnd();
        int y0 = clamp(bandTopY, 0, imp.getHeight() - 1);
        int y1 = clamp(bandBotY, 0, imp.getHeight() - 1);
        if (y1 < y0) { int t = y0; y0 = y1; y1 = t; }

        // Create AssistBand with initial position
        AssistBand band = new AssistBand(x0, x1, y0, y1, (y0 + y1) / 2.0);
        ImageProcessor ip = imp.getProcessor();

        // Fast pixel array access - avoid getf() in nested loops
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        int width = ip.getWidth();
        
        // Band area sum using array indexing
        double sum = 0;
        for (int y = y0; y <= y1; y++) {
            int rowStart = y * width;
            for (int x = x0; x <= x1; x++) {
                sum += pixels[rowStart + x];
            }
        }
        
        // Set the raw area (intensity sum)
        band.areaRaw = sum;

        // Local background from flanks (above & below), same width, height = 12 px (clamped)
        int flankH = Math.min(12, Math.max(4, (y1 - y0 + 1) / 2));
        double bgSum = 0; 
        int bgN = 0;

        // Above flank using array indexing
        int a0 = clamp(y0 - flankH - 4, 0, imp.getHeight() - 1);
        int a1 = clamp(y0 - 4, 0, imp.getHeight() - 1);
        for (int y = a0; y <= a1; y++) {
            int rowStart = y * width;
            for (int x = x0; x <= x1; x++) { 
                bgSum += pixels[rowStart + x]; 
                bgN++; 
            }
        }

        // Below flank using array indexing
        int b0 = clamp(y1 + 4, 0, imp.getHeight() - 1);
        int b1 = clamp(y1 + 4 + flankH, 0, imp.getHeight() - 1);
        for (int y = b0; y <= b1; y++) {
            int rowStart = y * width;
            for (int x = x0; x <= x1; x++) { 
                bgSum += pixels[rowStart + x]; 
                bgN++; 
            }
        }

        double bgMean = bgN > 0 ? bgSum / bgN : 0.0;
        double bandPixels = (double) (x1 - x0 + 1) * (double) (y1 - y0 + 1);
        band.areaBg = bgMean * bandPixels;
        band.areaCorr = Math.max(0.0, sum - band.areaBg);

        // SNR from lane profile around the peak region
        float[] prof = Profiles.verticalSum(imp, x0, x1);
        int py0 = Math.max(0, y0 - flankH);
        int py1 = Math.min(prof.length - 1, y1 + flankH);
        int peak = argmax(prof, py0, py1);
        
        // Create new band with refined y position using sub-pixel precision
        double refinedY = Peaks.subpixelApex(prof, peak);
        AssistBand refinedBand = new AssistBand(x0, x1, y0, y1, refinedY);
        refinedBand.areaRaw = band.areaRaw;
        refinedBand.areaBg = band.areaBg;
        refinedBand.areaCorr = band.areaCorr;
        refinedBand.snr = snr(prof, peak, py0, py1);
        
        // Calculate band sharpness and lane smear
        refinedBand.sharpness = bandSharpness(prof, peak);
        refinedBand.smearPercent = laneSmearPercent(imp, lane, y0, y1);

        return refinedBand;
    }

    /** 
     * Simple local SNR: (peak - mean flanks) / std(flanks).
     * Used to assess band quality and confidence.
     */
    public static double snr(float[] prof, int peak, int y0, int y1) {
        int L0 = Math.max(0, y0), R1 = Math.min(prof.length - 1, y1);
        int flank = Math.max(4, (R1 - L0 + 1) / 6);
        double sum = 0, sum2 = 0; 
        int n = 0;

        // Top flank
        int t0 = Math.max(0, peak - 2*flank), t1 = Math.max(0, peak - flank);
        for (int i = t0; i < t1 && i < prof.length; i++) { 
            sum += prof[i]; 
            sum2 += prof[i] * prof[i]; 
            n++; 
        }
        
        // Bottom flank
        int b0 = Math.min(prof.length - 1, peak + flank);
        int b1 = Math.min(prof.length - 1, peak + 2*flank);
        for (int i = b0; i <= b1 && i < prof.length; i++) { 
            sum += prof[i]; 
            sum2 += prof[i] * prof[i]; 
            n++; 
        }

        if (n < 2) return 0.0;
        double mean = sum / n;
        double var = Math.max(1e-6, (sum2 / n) - mean * mean);
        double sd = Math.sqrt(var);
        return (prof[peak] - mean) / sd;
    }

    /**
     * Convert SNR to a confidence score between 0 and 1.
     */
    public static double snrToConfidence(double snr) {
        // Map SNR to confidence: SNR >= 5 -> confidence = 1, SNR <= 1 -> confidence = 0
        return Math.max(0.0, Math.min(1.0, (snr - 1.0) / 4.0));
    }

    private static int argmax(float[] v, int a, int b) {
        a = Math.max(0, a); 
        b = Math.min(v.length - 1, b);
        int idx = a; 
        float best = -Float.MAX_VALUE;
        for (int i = a; i <= b; i++) {
            if (v[i] > best) { 
                best = v[i]; 
                idx = i; 
            }
        }
        return idx;
    }

    /**
     * Calculate band sharpness based on edge definition and peak width.
     * Higher values indicate sharper, more well-defined bands.
     * 
     * @param prof Vertical intensity profile of the lane
     * @param peak Peak position in the profile
     * @return Sharpness score (0.0 - 1.0, higher = sharper)
     */
    public static double bandSharpness(float[] prof, int peak) {
        if (peak < 2 || peak >= prof.length - 2) return 0.0;
        
        float peakVal = prof[peak];
        if (peakVal <= 0) return 0.0;
        
        // Calculate full width at half maximum (FWHM)
        float halfMax = peakVal * 0.5f;
        
        // Find left edge at half maximum
        int leftEdge = peak;
        for (int i = peak - 1; i >= 0; i--) {
            if (prof[i] <= halfMax) {
                leftEdge = i;
                break;
            }
        }
        
        // Find right edge at half maximum
        int rightEdge = peak;
        for (int i = peak + 1; i < prof.length; i++) {
            if (prof[i] <= halfMax) {
                rightEdge = i;
                break;
            }
        }
        
        // Calculate FWHM and edge steepness
        int fwhm = Math.max(1, rightEdge - leftEdge);
        
        // Calculate average edge steepness (left and right slopes)
        double leftSlope = 0.0;
        if (peak > leftEdge && leftEdge > 0) {
            leftSlope = (peakVal - prof[leftEdge]) / (double)(peak - leftEdge);
        }
        
        double rightSlope = 0.0;
        if (rightEdge > peak && rightEdge < prof.length - 1) {
            rightSlope = (peakVal - prof[rightEdge]) / (double)(rightEdge - peak);
        }
        
        double avgSlope = (leftSlope + rightSlope) / 2.0;
        
        // Sharpness combines narrow width with steep edges
        // Normalize: narrower FWHM and steeper slopes = higher sharpness
        double widthFactor = Math.max(0.1, 10.0 / fwhm);  // Narrower = better
        double slopeFactor = Math.min(1.0, avgSlope / 20.0);  // Steeper = better
        
        return Math.max(0.0, Math.min(1.0, widthFactor * slopeFactor));
    }
    
    /**
     * Calculate the percentage of lane intensity that appears as "smear" 
     * (diffuse background signal) within the molecular weight window.
     * 
     * @param imp Image containing the gel
     * @param lane Lane to analyze
     * @param mwWindowTop Top Y coordinate of MW analysis window
     * @param mwWindowBot Bottom Y coordinate of MW analysis window
     * @return Smear percentage (0.0 - 100.0)
     */
    public static double laneSmearPercent(ImagePlus imp, Lane lane, int mwWindowTop, int mwWindowBot) {
        int x0 = lane.xStart(), x1 = lane.xEnd();
        int y0 = clamp(mwWindowTop, 0, imp.getHeight() - 1);
        int y1 = clamp(mwWindowBot, 0, imp.getHeight() - 1);
        if (y1 < y0) { int t = y0; y0 = y1; y1 = t; }
        
        // Get vertical profile for the lane
        float[] prof = Profiles.verticalSum(imp, x0, x1);
        
        // Calculate baseline (minimum intensity in the MW window)
        float baseline = Float.MAX_VALUE;
        for (int y = y0; y <= y1 && y < prof.length; y++) {
            baseline = Math.min(baseline, prof[y]);
        }
        
        // Find all significant peaks in the window
        java.util.List<Integer> peaks = new java.util.ArrayList<>();
        int windowSize = Math.max(3, (y1 - y0) / 20); // Adaptive window size
        
        for (int y = y0 + windowSize; y <= y1 - windowSize && y < prof.length; y++) {
            boolean isPeak = true;
            float currentVal = prof[y];
            
            // Check if this is a local maximum
            for (int dy = -windowSize; dy <= windowSize; dy++) {
                int checkY = y + dy;
                if (checkY >= 0 && checkY < prof.length && prof[checkY] > currentVal) {
                    isPeak = false;
                    break;
                }
            }
            
            // Only count significant peaks (above baseline + threshold)
            if (isPeak && currentVal > baseline + (baseline * 0.1)) {
                peaks.add(y);
            }
        }
        
        // Calculate total signal and peak signal
        double totalSignal = 0.0;
        double peakSignal = 0.0;
        
        for (int y = y0; y <= y1 && y < prof.length; y++) {
            double signal = Math.max(0, prof[y] - baseline);
            totalSignal += signal;
            
            // Check if this position is part of any peak
            boolean isInPeak = false;
            for (int peakY : peaks) {
                if (Math.abs(y - peakY) <= windowSize) {
                    isInPeak = true;
                    break;
                }
            }
            
            if (isInPeak) {
                peakSignal += signal;
            }
        }
        
        // Smear is the non-peak signal as percentage of total
        if (totalSignal <= 0) return 0.0;
        
        double smearSignal = totalSignal - peakSignal;
        double smearPercent = (smearSignal / totalSignal) * 100.0;
        
        return Math.max(0.0, Math.min(100.0, smearPercent));
    }

    private static int clamp(int v, int lo, int hi) { 
        return Math.max(lo, Math.min(hi, v)); 
    }

    // === ISOFORM PROFILING HELPERS ===

    /** Full-width at half-maximum (FWHM) in pixels around a peak index. */
    public static double fwhmPx(float[] profile, int apexIdx) {
        if (profile == null || profile.length == 0) return Double.NaN;
        int n = profile.length;
        double apex = profile[apexIdx];
        double half = apex * 0.5;

        // Left crossing
        int L = apexIdx;
        while (L > 0 && profile[L] > half) L--;
        double xL = L + (half - profile[L]) / Math.max(1e-6, (profile[L+1] - profile[L]));

        // Right crossing
        int R = apexIdx;
        while (R < n - 1 && profile[R] > half) R++;
        double xR = R - (half - profile[R]) / Math.max(1e-6, (profile[R-1] - profile[R]));

        return Math.max(0.0, xR - xL);
    }

    /** Dimensionless sharpness = apexHeight / FWHM. Larger = sharper/narrower. */
    public static double sharpnessScore(float[] profile, int apexIdx) {
        double f = fwhmPx(profile, apexIdx);
        double h = profile[apexIdx];
        if (Double.isNaN(f) || f <= 0) return 0.0;
        return h / f;
    }

    /** Window filter: true if band MW is within +/- pct of target. If MW unknown, falls back to Rf window. */
    public static boolean inMwWindow(double bandMw, boolean hasMw, double targetMw, double pct, double bandRf, double targetRf, double rfTol) {
        if (hasMw) {
            double rel = Math.abs((bandMw - targetMw) / Math.max(1e-9, targetMw)) * 100.0;
            return rel <= pct;
        } else {
            return Math.abs(bandRf - targetRf) <= rfTol;
        }
    }

    /** Simple smear proxy: fraction of area carried by 'broad' peaks (width > 1.5x median width) within the window. */
    public static double smearFractionWithinWindow(java.util.List<com.betterdairy.autodense.model.Models.Band> bandsInWindow) {
        if (bandsInWindow.isEmpty()) return 0.0;
        double[] widths = new double[bandsInWindow.size()];
        double sumArea = 0;
        for (int i = 0; i < bandsInWindow.size(); i++) {
            var b = bandsInWindow.get(i);
            // Use a default width estimate since Models.Band only has single y coordinate
            widths[i] = Math.max(1.0, 8.0); // Default band width assumption
            sumArea += Math.max(0.0, b.area());
        }
        java.util.Arrays.sort(widths);
        double median = widths[widths.length / 2];
        double broadArea = 0;
        for (var b : bandsInWindow) {
            double w = Math.max(1.0, 8.0); // Default band width assumption
            if (w > 1.5 * median) broadArea += Math.max(0.0, b.area());
        }
        if (sumArea <= 0) return 0.0;
        return broadArea / sumArea;
    }

    // === HCP COMPOSITION HELPERS ===

    /** Sum of corrected area for bands whose MW is NOT within +/- windowPct of targetMw. */
    public static double sumAreaExceptWindow(java.util.List<com.betterdairy.autodense.model.Models.Band> bands,
                                             com.betterdairy.autodense.model.Models.CalibrationModel cal,
                                             double targetMwKda, double windowPct) {
        double sum = 0.0;
        for (var b : bands) {
            double mw = Calibrator.assignMw(cal, b.y());
            double rel = Math.abs((mw - targetMwKda) / Math.max(1e-9, targetMwKda)) * 100.0;
            if (Double.isNaN(mw) || rel > windowPct) sum += Math.max(0.0, b.area());
        }
        return sum;
    }

    /** Return top N non-target bands sorted by area, with MWs. */
    public static java.util.List<com.betterdairy.autodense.model.Models.Band> topNonTargetBands(
            java.util.List<com.betterdairy.autodense.model.Models.Band> bands,
            com.betterdairy.autodense.model.Models.CalibrationModel cal,
            double targetMwKda, double windowPct, int topN) {
        var list = new java.util.ArrayList<com.betterdairy.autodense.model.Models.Band>();
        for (var b : bands) {
            double mw = Calibrator.assignMw(cal, b.y());
            double rel = Math.abs((mw - targetMwKda) / Math.max(1e-9, targetMwKda)) * 100.0;
            if (Double.isNaN(mw) || rel > windowPct) list.add(b);
        }
        list.sort(java.util.Comparator.comparingDouble((com.betterdairy.autodense.model.Models.Band bb) -> bb.area()).reversed());
        if (list.size() > topN) return list.subList(0, topN);
        return list;
    }
}