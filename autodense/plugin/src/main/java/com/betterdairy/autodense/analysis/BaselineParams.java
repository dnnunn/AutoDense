package com.betterdairy.autodense.analysis;

import java.util.Map;

/**
 * Safe baseline subtraction parameters with hard clamps to prevent signal destruction.
 * 
 * Provides configurable baseline removal methods with ROI-aware window sizing and
 * enforced bounds (3-15px) to prevent the destructive 32px windows that were 
 * destroying lane detection.
 */
public final class BaselineParams {
    public enum Method { 
        NONE,       // No baseline removal
        PERCENTILE, // Running percentile-based baseline 
        MORPH       // Morphological opening (min→max)
    }

    public final Method method;
    public final double windowFrac;      // fraction of ROI width, e.g. 0.02
    public final int    windowPx;        // explicit px; overrides frac if >0
    public final int    clampMinPx;      // 3 - hard minimum bound
    public final int    clampMaxPx;      // 15 - hard maximum bound
    public final double quantile;        // for percentile, e.g. 0.10

    public BaselineParams(Method m, double wf, int wp, int minPx, int maxPx, double q) {
        this.method = m; 
        this.windowFrac = wf; 
        this.windowPx = wp;
        this.clampMinPx = minPx; 
        this.clampMaxPx = maxPx; 
        this.quantile = q;
    }

    /**
     * Load baseline parameters from configuration with ROI-aware sizing
     * 
     * @param cfg Configuration map containing detect.baseline settings
     * @param roiWidth Width of the ROI for fraction-to-pixel conversion
     * @return BaselineParams with resolved pixel window size
     */
    @SuppressWarnings("unchecked")
    public static BaselineParams fromConfig(Map<String,Object> cfg, int roiWidth) {
        Map<String,Object> b = (Map<String,Object>) ((Map<String,Object>)cfg.getOrDefault("detect", Map.of()))
            .getOrDefault("baseline", Map.of());
        
        String m = String.valueOf(b.getOrDefault("method", "percentile")).toUpperCase();
        double wf = ((Number)b.getOrDefault("window_frac", 0.02)).doubleValue();
        int wp    = ((Number)b.getOrDefault("window_px", 0)).intValue();
        int minPx = ((Number)b.getOrDefault("clamp_min_px", 3)).intValue();
        int maxPx = ((Number)b.getOrDefault("clamp_max_px", 15)).intValue();
        double q  = ((Number)b.getOrDefault("quantile", 0.10)).doubleValue();

        // Resolve window size: explicit pixels override fraction
        int win = wp > 0 ? wp : (int)Math.round(Math.max(1, wf * roiWidth));
        
        // CRITICAL: Hard clamps prevent destructive windows (was 32px!)
        win = Math.max(minPx, Math.min(maxPx, win));
        
        // Store resolved pixel size back into windowPx
        return new BaselineParams(Method.valueOf(m), wf, win, minPx, maxPx, q);
    }

    /**
     * Load BAND-SPECIFIC baseline parameters from configuration with ROI-aware sizing
     * 
     * Uses bands.baseline section instead of detect.baseline for separate optimization.
     * Falls back to detect.baseline if bands.baseline not found.
     * 
     * @param cfg Configuration map containing bands.baseline settings
     * @param roiWidth Width of the ROI for fraction-to-pixel conversion
     * @return BaselineParams with resolved pixel window size optimized for band detection
     */
    @SuppressWarnings("unchecked")
    public static BaselineParams fromBandConfig(Map<String,Object> cfg, int roiWidth) {
        // Try bands.baseline first, fall back to detect.baseline
        Map<String,Object> bandsConfig = (Map<String,Object>) cfg.getOrDefault("bands", Map.of());
        Map<String,Object> b = (Map<String,Object>) bandsConfig.getOrDefault("baseline", Map.of());
        
        String parameterSource = "bands.baseline";
        
        // If no bands.baseline found, use detect.baseline as fallback
        if (b.isEmpty()) {
            parameterSource = "detect.baseline";
            Map<String,Object> detectConfig = (Map<String,Object>) cfg.getOrDefault("detect", Map.of());
            b = (Map<String,Object>) detectConfig.getOrDefault("baseline", Map.of());
            System.err.printf("[BASELINE_FIX] FALLBACK: No bands.baseline found, using detect.baseline%n");
        } else {
            System.err.printf("[BASELINE_FIX] SUCCESS: Using bands.baseline parameters%n");
        }
        
        // Band-specific defaults (gentler than lane defaults)
        String m = String.valueOf(b.getOrDefault("method", "percentile")).toUpperCase();
        double wf = ((Number)b.getOrDefault("window_frac", 0.015)).doubleValue(); // Tighter than lanes (0.02)
        int wp    = ((Number)b.getOrDefault("window_px", 0)).intValue();
        int minPx = ((Number)b.getOrDefault("clamp_min_px", 2)).intValue();       // Smaller min for bands
        int maxPx = ((Number)b.getOrDefault("clamp_max_px", 8)).intValue();       // Smaller max for bands
        double q  = ((Number)b.getOrDefault("quantile", 0.08)).doubleValue();     // Lower quantile for bands

        // Resolve window size: explicit pixels override fraction
        int win = wp > 0 ? wp : (int)Math.round(Math.max(1, wf * roiWidth));
        
        // CRITICAL: Hard clamps prevent destructive windows (tighter for bands)
        int originalWin = win;
        win = Math.max(minPx, Math.min(maxPx, win));
        
        // CRITICAL LOGGING: Show parameter source and window size calculation
        System.err.printf("[BASELINE_FIX] fromBandConfig: source=%s roiWidth=%d window_frac=%.3f calculated=%dpx clamped=%dpx%n",
                         parameterSource, roiWidth, wf, originalWin, win);
        
        // Store resolved pixel size back into windowPx
        return new BaselineParams(Method.valueOf(m), wf, win, minPx, maxPx, q);
    }
    
    @Override
    public String toString() {
        return String.format("BaselineParams{method=%s, windowPx=%d, frac=%.3f, clamps=[%d,%d], quantile=%.2f}",
            method, windowPx, windowFrac, clampMinPx, clampMaxPx, quantile);
    }
}