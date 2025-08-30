package com.betterdairy.autodense.analysis;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Fast and gentle baseline removal utilities with comprehensive logging.
 * 
 * Provides O(n) moving min/max algorithms using deques and safe baseline
 * subtraction methods to replace the destructive 32px morphological opening
 * that was destroying lane detection.
 */
public class BaselineUtils {
    
    /**
     * Fast O(n) moving minimum using sliding window with deque optimization
     * 
     * @param a Input array
     * @param w Window size (clamped to safe bounds in BaselineParams)
     * @return Array of moving minimum values
     */
    public static double[] movingMin(double[] a, int w) {
        int n = a.length; 
        double[] out = new double[n];
        Deque<Integer> dq = new ArrayDeque<>();
        int k = Math.max(1, w);
        
        for (int i = 0; i < n; i++) {
            // Remove elements not in current window
            while (!dq.isEmpty() && a[dq.peekLast()] >= a[i]) {
                dq.removeLast();
            }
            dq.addLast(i);
            
            int start = i - k + 1;
            if (dq.peekFirst() < start) {
                dq.removeFirst();
            }
            
            if (start >= 0) {
                out[start + k - 1] = a[dq.peekFirst()];
            }
        }
        
        // Fill leading tail
        for (int i = 0; i < k-1 && i < n; i++) {
            out[i] = out[k-1];
        }
        
        return out;
    }
    
    /**
     * Fast O(n) moving maximum using sliding window with deque optimization
     * 
     * @param a Input array  
     * @param w Window size (clamped to safe bounds in BaselineParams)
     * @return Array of moving maximum values
     */
    public static double[] movingMax(double[] a, int w) {
        int n = a.length;
        double[] out = new double[n];
        Deque<Integer> dq = new ArrayDeque<>();
        int k = Math.max(1, w);
        
        for (int i = 0; i < n; i++) {
            // Remove elements not in current window
            while (!dq.isEmpty() && a[dq.peekLast()] <= a[i]) {
                dq.removeLast();
            }
            dq.addLast(i);
            
            int start = i - k + 1;
            if (dq.peekFirst() < start) {
                dq.removeFirst();
            }
            
            if (start >= 0) {
                out[start + k - 1] = a[dq.peekFirst()];
            }
        }
        
        // Fill leading tail
        for (int i = 0; i < k-1 && i < n; i++) {
            out[i] = out[k-1];
        }
        
        return out;
    }
    
    /**
     * Gentle running low envelope using morphological opening
     * 
     * This is a robust proxy for percentile methods; low quantiles behave 
     * similarly to opening at gentle widths. Opening = erode(min) then dilate(max).
     * 
     * @param a Input profile array
     * @param w Window size (safe bounds enforced)
     * @param q Quantile (unused in current implementation, uses opening)
     * @return Low envelope baseline
     */
    public static double[] runningLowEnvelope(double[] a, int w, double q) {
        // Use opening for low envelope when q in [0.05..0.2]; it's fast and stable
        // Opening = erode(min) then dilate(max)
        double[] er = movingMin(a, w);
        return movingMax(er, w);
    }
    
    /**
     * Safe baseline subtraction with comprehensive logging
     * 
     * @param a Input profile array
     * @param bp Baseline parameters with method and safe window size
     * @param tag Label for logging (e.g. "lanes", "bands")  
     * @param log Print stream for logging (null to disable)
     * @return Profile with baseline subtracted, guaranteed non-negative
     */
    public static double[] subtractBaseline(double[] a, BaselineParams bp, String tag, PrintStream log) {
        double[] base;
        
        switch (bp.method) {
            case NONE:
                if (log != null) {
                    log.printf("[BASELINE] method=NONE win=%d%n", bp.windowPx);
                }
                return Arrays.copyOf(a, a.length);
                
            case MORPH: {
                base = runningLowEnvelope(a, bp.windowPx, 0.1);
                break;
            }
            
            case PERCENTILE: 
            default: {
                base = runningLowEnvelope(a, bp.windowPx, bp.quantile);
                break;
            }
        }
        
        // Capture before/after statistics for logging
        double preMed = median(a), preMax = max(a);
        
        // Subtract baseline with non-negative clamp
        double[] out = Arrays.copyOf(a, a.length);
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(0.0, out[i] - base[i]);
        }
        
        double postMed = median(out), postMax = max(out);
        
        // CRITICAL: Log before/after to detect signal destruction
        if (log != null) {
            log.printf("[BASELINE] method=%s win=%d pre{med=%.3f,max=%.3f} post{med=%.3f,max=%.3f}%n",
                    bp.method, bp.windowPx, preMed, preMax, postMed, postMax);
            
            // WARN if signal appears destroyed (near-zero after processing)
            if (postMax < 0.01 && preMax > 0.1) {
                log.printf("[BASELINE] WARNING: Signal appears destroyed! pre_max=%.3f post_max=%.3f%n", 
                          preMax, postMax);
            }
        }
        
        return out;
    }
    
    /**
     * Fast median calculation
     */
    public static double median(double[] a) { 
        double[] c = Arrays.copyOf(a, a.length); 
        Arrays.sort(c); 
        return c[c.length/2]; 
    }
    
    /**
     * Fast maximum calculation
     */
    public static double max(double[] a) { 
        double m = -Double.MAX_VALUE; 
        for (double v : a) {
            m = Math.max(m, v); 
        }
        return m; 
    }
}