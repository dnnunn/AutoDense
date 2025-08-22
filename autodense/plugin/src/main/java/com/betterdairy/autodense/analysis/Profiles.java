package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.process.ImageProcessor;

/**
 * Helper class for building intensity profiles from gel images.
 * Used by the BandAssist feature for user-guided band identification.
 */
public final class Profiles {
    private Profiles(){}

    /** 
     * Sum pixel intensities across xStart..xEnd for every y; returns array length = image height.
     * This creates a 1D intensity profile for a lane by summing across its width.
     */
    public static float[] verticalSum(ImagePlus imp, int xStart, int xEnd) {
        ImageProcessor ip = imp.getProcessor();
        int w = ip.getWidth(), h = ip.getHeight();
        int xs = Math.max(0, Math.min(xStart, xEnd));
        int xe = Math.min(w - 1, Math.max(xStart, xEnd));
        
        // Fast pixel array access
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        float[] out = BufferPool.getFloatBuffer(h);
        
        for (int y = 0; y < h; y++) {
            double s = 0;
            int rowStart = y * w;
            for (int x = xs; x <= xe; x++) {
                s += pixels[rowStart + x];
            }
            out[y] = (float) s;
        }
        
        // Return a copy since we're giving this to the caller
        float[] result = java.util.Arrays.copyOf(out, h);
        BufferPool.returnFloatBuffer(out);
        return result;
    }

    /** 
     * Simple Savitzky–Golay style smoothing: central moving average (odd window).
     * Useful for reducing noise in lane profiles before peak detection.
     */
    public static float[] smooth(float[] a, int window) {
        if (window < 3 || window % 2 == 0) return a.clone();
        int r = window / 2, n = a.length;
        float[] out = BufferPool.getFloatBuffer(n);
        
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - r), i1 = Math.min(n - 1, i + r);
            double sum = 0;
            for (int j = i0; j <= i1; j++) sum += a[j];
            out[i] = (float)(sum / (i1 - i0 + 1));
        }
        
        // Return a copy since we're giving this to the caller
        float[] result = java.util.Arrays.copyOf(out, n);
        BufferPool.returnFloatBuffer(out);
        return result;
    }
}