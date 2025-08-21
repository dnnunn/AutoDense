package com.betterdairy.autodense.analysis;

import java.util.OptionalInt;

/**
 * Peak detection and refinement utilities for gel band analysis.
 * Used by BandAssist to find and refine band peaks with sub-pixel precision.
 */
public final class Peaks {
    private Peaks(){}

    /**
     * Find the index of the maximum value in the specified range [y0, y1].
     */
    public static int argmax(float[] v, int y0, int y1) {
        y0 = Math.max(1, y0); 
        y1 = Math.min(v.length - 2, y1);
        int idx = y0; 
        float best = -Float.MAX_VALUE;
        for (int i = y0; i <= y1; i++) {
            if (v[i] > best) { 
                best = v[i]; 
                idx = i; 
            }
        }
        return idx;
    }

    /** 
     * Check if v[i] is a local maximum with prominence >= minProm (fraction of local range).
     * Prominence is measured as (peak - baseline) / local_range.
     */
    public static boolean isProminent(float[] v, int i, float minProm) {
        if (i <= 0 || i >= v.length - 1) return false;
        if (!(v[i] > v[i-1] && v[i] >= v[i+1])) return false;
        
        int L = leftValley(v, i), R = rightValley(v, i);
        float base = 0.5f * (v[L] + v[R]);
        float prom = v[i] - base;
        float localRange = max(v, L, R) - min(v, L, R) + 1e-6f;
        return prom / localRange >= minProm;
    }

    /**
     * Find the left valley (local minimum) from peak position.
     */
    public static int leftValley(float[] v, int i) {
        int k = i;
        while (k > 1 && v[k-1] <= v[k]) k--;
        return k;
    }

    /**
     * Find the right valley (local minimum) from peak position.
     */
    public static int rightValley(float[] v, int i) {
        int k = i;
        while (k < v.length - 2 && v[k+1] <= v[k]) k++;
        return k;
    }

    /** 
     * Quadratic interpolation using neighbors to refine apex position to sub-pixel precision.
     * Returns the refined y-coordinate of the peak maximum.
     */
    public static double subpixelApex(float[] v, int i) {
        if (i <= 0 || i >= v.length - 1) return i;
        double ym1 = v[i-1], y0 = v[i], yp1 = v[i+1];
        double denom = (ym1 - 2*y0 + yp1);
        if (Math.abs(denom) < 1e-9) return i;
        double delta = 0.5 * (ym1 - yp1) / denom; // in [-0.5,0.5] typically
        return i + delta;
    }

    /**
     * Find the best (most prominent) peak in the specified range.
     * Returns OptionalInt.empty() if no suitable peak is found.
     */
    public static OptionalInt bestProminent(float[] v, int y0, int y1, float minProm) {
        y0 = Math.max(1, y0); 
        y1 = Math.min(v.length - 2, y1);
        int best = -1; 
        float bestProm = 0;
        
        for (int i = y0; i <= y1; i++) {
            if (!(v[i] > v[i-1] && v[i] >= v[i+1])) continue;
            
            int L = leftValley(v, i), R = rightValley(v, i);
            float base = 0.5f * (v[L] + v[R]);
            float prom = v[i] - base;
            if (prom <= 0) continue;
            
            float localRange = max(v, L, R) - min(v, L, R) + 1e-6f;
            float score = prom / localRange;
            if (score >= minProm && score > bestProm) { 
                bestProm = score; 
                best = i; 
            }
        }
        return best >= 0 ? OptionalInt.of(best) : OptionalInt.empty();
    }

    // Helper methods for finding min/max in array range
    private static float max(float[] v, int a, int b) { 
        float m = -Float.MAX_VALUE; 
        for (int i = a; i <= b && i < v.length; i++) {
            if (v[i] > m) m = v[i];
        }
        return m; 
    }
    
    private static float min(float[] v, int a, int b) { 
        float m = Float.MAX_VALUE; 
        for (int i = a; i <= b && i < v.length; i++) {
            if (v[i] < m) m = v[i];
        }
        return m; 
    }
}