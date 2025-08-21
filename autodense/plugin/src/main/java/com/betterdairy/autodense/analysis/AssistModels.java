package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models;
import java.util.HashSet;
import java.util.Set;

/**
 * Extended models for BandAssist feature that provide additional properties
 * while remaining compatible with the core AutoDense models.
 */
public final class AssistModels {
    private AssistModels() {}
    
    /**
     * Extended Band with additional properties for assisted identification.
     */
    public static final class AssistBand {
        // Core properties compatible with Models.Band
        public final int xStart, xEnd, yStart, yEnd;
        public final double yApexPx;  // Sub-pixel refined y position
        public double areaRaw;        // Raw intensity sum
        public double areaBg;         // Background estimate
        public double areaCorr;       // Background-corrected area
        public double snr;            // Signal-to-noise ratio
        public double confidence = 0.0;
        public Double mwKDa;          // Molecular weight if calibrated
        public final Set<String> flags = new HashSet<>();
        
        public AssistBand(int xStart, int xEnd, int yStart, int yEnd, double yApexPx) {
            this.xStart = xStart;
            this.xEnd = xEnd;
            this.yStart = yStart;
            this.yEnd = yEnd;
            this.yApexPx = yApexPx;
        }
        
        public int widthPx() { return Math.max(1, xEnd - xStart + 1); }
        public int heightPx() { return Math.max(1, yEnd - yStart + 1); }
        
        /**
         * Convert to core Models.Band for integration with existing system.
         */
        public Models.Band toCoreModel(int index) {
            return new Models.Band(index, (int)yApexPx, areaCorr, areaBg, mwKDa != null ? mwKDa : 0.0);
        }
        
        @Override
        public String toString() {
            return String.format("AssistBand{x=%d-%d, y=%d-%d, apex=%.1f, area=%.0f, snr=%.1f, conf=%.2f}", 
                xStart, xEnd, yStart, yEnd, yApexPx, areaCorr, snr, confidence);
        }
    }
    
    /**
     * Result of assisted band identification containing seed band and propagated bands.
     */
    public static final class AssistResult {
        public final AssistBand seedBand;
        public final java.util.List<AssistBand> propagatedBands;
        public final int totalLanes;
        public final int successfulLanes;
        public final double averageConfidence;
        
        public AssistResult(AssistBand seedBand, java.util.List<AssistBand> propagatedBands, int totalLanes) {
            this.seedBand = seedBand;
            this.propagatedBands = propagatedBands;
            this.totalLanes = totalLanes;
            this.successfulLanes = propagatedBands.size();
            this.averageConfidence = propagatedBands.stream()
                .mapToDouble(b -> b.confidence)
                .average()
                .orElse(0.0);
        }
        
        /**
         * Get all bands (seed + propagated) as a single list.
         */
        public java.util.List<AssistBand> getAllBands() {
            java.util.List<AssistBand> allBands = new java.util.ArrayList<>();
            allBands.add(seedBand);
            allBands.addAll(propagatedBands);
            return allBands;
        }
        
        @Override
        public String toString() {
            return String.format("AssistResult{seed=%s, propagated=%d/%d lanes, avgConf=%.2f}", 
                seedBand, successfulLanes, totalLanes, averageConfidence);
        }
    }
}