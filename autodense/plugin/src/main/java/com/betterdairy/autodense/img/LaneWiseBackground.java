package com.betterdairy.autodense.img;

import ij.ImagePlus;
import ij.process.ImageProcessor;

/**
 * Lane-wise background subtraction utility for gel images with uneven staining.
 * Compensates for uneven illumination and staining gradients across gel width.
 */
public final class LaneWiseBackground {
    
    private LaneWiseBackground() {} // Utility class
    
    /**
     * Perform column-wise background subtraction using running quantile baseline.
     * Particularly effective for correcting uneven staining in protein gels.
     * 
     * @param imp The image to process (modified in place)
     * @param radiusPx The radius for running quantile calculation (typically 50-70 for 4K smartphone gel photos)
     * @param quantile The quantile to use as baseline (0.15 = 15th percentile works well for Coomassie gels)
     */
    public static void subtract(ImagePlus imp, int radiusPx, double quantile) {
        ImageProcessor ip = imp.getProcessor();
        final int w = ip.getWidth(), h = ip.getHeight();

        // Crude lane width guess ~ 0.40 of inter-lane spacing; fall back to 0.4 * (w/expectedLanes)
        final int window = Math.max(3, radiusPx);
        float[] colBuf = new float[h];
        float[] bgCol = new float[h];

        for (int x = 0; x < w; x++) {
            // vertical column snapshot
            for (int y = 0; y < h; y++) {
                colBuf[y] = ip.getf(x, y);
            }

            // running quantile baseline per column (O(h*window))
            runningQuantile(colBuf, bgCol, window, quantile);

            for (int y = 0; y < h; y++) {
                float val = colBuf[y] - bgCol[y];
                ip.setf(x, y, Math.max(0f, val));
            }
        }
    }

    /**
     * Simple quantile calculation using sliding window insertion sort.
     * Fast enough for real-time processing on smartphone gel photos.
     * 
     * @param src Source intensity values
     * @param out Output quantile values
     * @param win Window size for running calculation
     * @param q Quantile (0.0 to 1.0)
     */
    private static void runningQuantile(float[] src, float[] out, int win, double q) {
        final int n = src.length;
        final int half = win / 2;
        float[] buf = new float[win];
        
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - half);
            int b = Math.min(n - 1, i + half);
            int len = b - a + 1;
            
            if (len != buf.length) {
                buf = new float[len];
            }
            
            for (int k = 0; k < len; k++) {
                buf[k] = src[a + k];
            }
            
            java.util.Arrays.sort(buf, 0, len);
            int idx = (int) Math.floor(q * (len - 1));
            out[i] = buf[idx];
        }
    }
}