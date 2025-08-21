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

        // Band area sum
        double sum = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                sum += ip.getf(x, y);
            }
        }
        
        // Set the raw area (intensity sum)
        band.areaRaw = sum;

        // Local background from flanks (above & below), same width, height = 12 px (clamped)
        int flankH = Math.min(12, Math.max(4, (y1 - y0 + 1) / 2));
        double bgSum = 0; 
        int bgN = 0;

        // Above flank
        int a0 = clamp(y0 - flankH - 4, 0, imp.getHeight() - 1);
        int a1 = clamp(y0 - 4, 0, imp.getHeight() - 1);
        for (int y = a0; y <= a1; y++) {
            for (int x = x0; x <= x1; x++) { 
                bgSum += ip.getf(x, y); 
                bgN++; 
            }
        }

        // Below flank  
        int b0 = clamp(y1 + 4, 0, imp.getHeight() - 1);
        int b1 = clamp(y1 + 4 + flankH, 0, imp.getHeight() - 1);
        for (int y = b0; y <= b1; y++) {
            for (int x = x0; x <= x1; x++) { 
                bgSum += ip.getf(x, y); 
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

    private static int clamp(int v, int lo, int hi) { 
        return Math.max(lo, Math.min(hi, v)); 
    }
}