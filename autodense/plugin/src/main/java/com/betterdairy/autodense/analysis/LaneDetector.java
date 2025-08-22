package com.betterdairy.autodense.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.betterdairy.autodense.model.Models.Lane;

public final class LaneDetector {
    private LaneDetector() {}
    // Lanes are assumed nearly uniform in width; allow +/-25%
    private static final double LANE_WIDTH_TOL_FRAC = 0.25;
    // For constant-spacing mode, occupy this fraction of the spacing as the lane ROI (default)
    private static final double LANE_WIDTH_FILL_FRACTION = 0.55;

    public static List<Lane> findLanes(ImagePlus imp) {
        return findLanes(imp, 0, false);
    }

    public static List<Lane> findLanes(ImagePlus imp, int expectedCount) {
        return findLanes(imp, expectedCount, false);
    }

    public static List<Lane> findLanes(ImagePlus imp, int expectedCount, boolean constantSpacing) {
        return findLanes(imp, expectedCount, constantSpacing, LANE_WIDTH_FILL_FRACTION, 0.0, true);
    }

    public static List<Lane> findLanes(ImagePlus imp,
                                       int expectedCount,
                                       boolean constantSpacing,
                                       double laneWidthFraction,
                                       double gridOffsetFraction,
                                       boolean preprocessForDetection) {
        // 1) Normalize to 8-bit for simple projection
        IJ.run(imp, "8-bit", "");
        ImageProcessor ip = imp.getProcessor();
        int W = ip.getWidth();
        int H = ip.getHeight();

        // 2) Pre-process lightly and estimate gel bounds using longest active run
        if (preprocessForDetection) {
            contrastStretch(ip, 0.01, 0.99); // robust linear stretch
            gaussianBlur(ip, 1.0);
            // Optional: mild tilt compensation (disabled by default for speed)
            // ip = compensateTilt(ip);
        }

        int margin = Math.max(10, W / 200);
        int[] bounds = estimateGelBounds(ip, margin);
        int xLeft = bounds[0];
        int xRight = bounds[1];
        int w = Math.max(1, xRight - xLeft + 1);

        // 2.5) If the user provided an expected count and wants constant spacing,
        // directly synthesize evenly spaced lanes across [xLeft, xRight]
        if (constantSpacing && expectedCount > 0) {
            List<Lane> lanes = new ArrayList<>(expectedCount);
            double spacing = w / (double) expectedCount; // pixels per lane
            double offset = spacing * gridOffsetFraction;
            double widthFrac = Math.max(0.2, Math.min(0.9, laneWidthFraction));
            int targetW = Math.max(3, (int)Math.round(spacing * widthFrac));
            int idx = 1;
            for (int i = 0; i < expectedCount; i++) {
                double center = xLeft + (i + 0.5) * spacing + offset;
                int cx = (int)Math.round(center);
                int half = Math.max(1, targetW / 2);
                int xl = Math.max(xLeft, cx - half);
                int xr = Math.min(xRight, xl + targetW - 1);
                if (xr <= xl) xr = Math.min(xRight, xl + 1);
                if (xr > xl) lanes.add(new Lane(idx++, xl, xr));
            }
            return lanes;
        }

        // 3) Fast column projection using array access (orders faster than getf() calls)
        // Convert to float array for vectorized access
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        double[] proj = new double[w];
        
        for (int xi = 0; xi < w; xi++) {
            int x = xLeft + xi;
            double s = 0;
            for (int y = 0; y < H; y++) {
                int pixelIndex = y * W + x;
                s += 255.0 - pixels[pixelIndex]; // invert: darker -> larger
            }
            proj[xi] = s;
        }
        // Enhanced smoothing with Savitzky-Golay-like filter for better lane boundary detection
        int smoothR = Math.max(5, w / 200); // Smaller radius for better resolution
        double[] smooth = applySavitzkyGolaySmoothing(proj, smoothR);

        // 4) Peak detection with prominence and spacing
        double mean = 0, std = 0;
        for (double v : smooth) mean += v;
        mean /= w;
        for (double v : smooth) std += (v - mean) * (v - mean);
        std = Math.sqrt(std / Math.max(1, w - 1));
        double thresh = mean + 0.5 * std; // lower to catch more lanes
        // Dynamic minimum spacing: set min_lane_sep_px = max(8, width * 0.04)
        int minDist = Math.max(8, (int)(W * 0.04)); // Dynamic spacing based on image width
        double minProm = 0.2 * std; // relaxed prominence

        List<Integer> peaks = detectPeaks(smooth, xLeft, w, mean, smoothR, thresh, minProm, minDist);

        // 5) Expand each peak to lane bounds within cropped region, with padding
        List<Lane> lanes = new ArrayList<>();
        int idx = 1;
        for (int pxAbs : peaks) {
            int p = pxAbs - xLeft;
            int l = p, r = p;
            while (l - 1 >= 1 && smooth[l - 1] <= smooth[l]) l--;
            while (r + 1 < w - 1 && smooth[r + 1] <= smooth[r]) r++;
            int pad = Math.max(6, minDist / 8);
            int xl = Math.max(xLeft, xLeft + l - pad);
            int xr = Math.min(xRight, xLeft + r + pad);
            if (xr > xl) lanes.add(new Lane(idx++, xl, xr));
        }

        // 6) Fallback: if too few lanes detected, relax parameters and retry
        if ((expectedCount > 0 && lanes.size() != expectedCount) || (expectedCount <= 0 && lanes.size() < 6)) {
            int target = expectedCount > 0 ? expectedCount : 8; // heuristic default
            int targetMinDist = Math.max(18, (xRight - xLeft + 1) / Math.max(1, (int)(1.6 * target)));
            double t = thresh; double p = minProm;
            List<Lane> best = lanes; int bestDiff = Math.abs(target - lanes.size());
            for (int step = 0; step < 6 && bestDiff > 0; step++) {
                t -= 0.1 * std; p -= 0.05 * std; if (p < 0) p = 0;
                List<Integer> cand = detectPeaks(smooth, xLeft, w, mean, smoothR, t, p, targetMinDist);
                List<Lane> ls = new ArrayList<>(); idx = 1;
                for (int pxAbs : cand) {
                    int c = pxAbs - xLeft; int l = c, r = c;
                    while (l - 1 >= 1 && smooth[l - 1] <= smooth[l]) l--;
                    while (r + 1 < w - 1 && smooth[r + 1] <= smooth[r]) r++;
                    int pad2 = Math.max(4, targetMinDist / 6);
                    int xl2 = Math.max(xLeft, xLeft + l - pad2);
                    int xr2 = Math.min(xRight, xLeft + r + pad2);
                    if (xr2 > xl2) ls.add(new Lane(idx++, xl2, xr2));
                }
                int diff = Math.abs(target - ls.size());
                if (diff < bestDiff) { best = ls; bestDiff = diff; }
            }
            lanes = best;
        }

        // 7) Enforce near-uniform lane widths within +/-25% of median
        lanes = regularizeLaneWidths(lanes, xLeft, xRight);
        
        // 8) Edge trimming: drop partial lanes cut off by crop (< 70% of median lane width)
        lanes = trimEdgeLanes(lanes);

        return lanes;
    }

    private static List<Integer> detectPeaks(double[] smooth, int xLeft, int w, double mean, int smoothR,
                                             double thresh, double minProm, int minDist) {
        List<Integer> peaks = new ArrayList<>();
        int i = 1;
        while (i < w - 1) {
            if (smooth[i] > thresh && smooth[i] > smooth[i - 1] && smooth[i] >= smooth[i + 1]) {
                int L = i, R = i;
                while (L - 1 >= 0 && smooth[L - 1] <= smooth[L]) L--;
                while (R + 1 < w && smooth[R + 1] <= smooth[R]) R++;
                int peak = (L + R) / 2;
                int lb = Math.max(0, peak - 3 * smoothR), rb = Math.min(w - 1, peak + 3 * smoothR);
                double base = 0; int cnt = 0;
                for (int k = lb; k <= rb; k++) if (k < L || k > R) { base += smooth[k]; cnt++; }
                base = cnt > 0 ? base / cnt : mean;
                if (smooth[peak] - base >= minProm) {
                    int absX = xLeft + peak;
                    if (peaks.isEmpty() || absX - peaks.get(peaks.size() - 1) >= minDist) {
                        peaks.add(absX);
                    } else {
                        int last = peaks.get(peaks.size() - 1);
                        if (smooth[peak] > smooth[last - xLeft]) peaks.set(peaks.size() - 1, absX);
                    }
                }
                i = R + 1;
            } else i++;
        }
        return peaks;
    }

    private static List<Lane> regularizeLaneWidths(List<Lane> lanes, int xLeft, int xRight) {
        if (lanes.size() < 2) return lanes;
        List<Integer> widths = new ArrayList<>();
        for (Lane l : lanes) widths.add(l.xEnd() - l.xStart() + 1);
        Collections.sort(widths);
        int medW = widths.get(widths.size() / 2);
        int minW = (int)Math.max(1, Math.floor(medW * (1.0 - LANE_WIDTH_TOL_FRAC)));
        int maxW = (int)Math.ceil(medW * (1.0 + LANE_WIDTH_TOL_FRAC));

        List<Lane> out = new ArrayList<>(lanes.size());
        for (int i = 0; i < lanes.size(); i++) {
            Lane l = lanes.get(i);
            int xl = l.xStart();
            int xr = l.xEnd();
            int w = xr - xl + 1;
            int targetW = w;
            if (w < minW) targetW = minW;
            else if (w > maxW) targetW = maxW;

            if (targetW != w) {
                int cx = xl + w / 2; // center
                int half = targetW / 2;
                int newXl = cx - half;
                int newXr = newXl + targetW - 1;
                // Respect bounds and neighbors
                int leftBound = (i == 0) ? xLeft : lanes.get(i - 1).xEnd() + 1;
                int rightBound = (i == lanes.size() - 1) ? xRight : lanes.get(i + 1).xStart() - 1;
                if (newXl < leftBound) { newXl = leftBound; newXr = Math.min(rightBound, newXl + targetW - 1); }
                if (newXr > rightBound) { newXr = rightBound; newXl = Math.max(leftBound, newXr - targetW + 1); }
                if (newXr >= newXl) { xl = newXl; xr = newXr; }
            }
            out.add(new Lane(l.index(), xl, xr));
        }
        return out;
    }

    // ---- Image preprocessing helpers (lightweight, no external filters) ----
    // Lint fixes: defines methods referenced earlier
    // ID: 598ffeb9-b650-44d3-a999-205cb788492b, c032cf9c-b993-4be0-b409-8dc5b4776810, 6083a01d-f144-4ff1-8cd1-930aedbea16a

    private static void contrastStretch(ImageProcessor ip, double lowPct, double highPct) {
        int W = ip.getWidth(), H = ip.getHeight();
        int[] hist = new int[256];
        // Subsample to speed up
        for (int y = 0; y < H; y += 2) {
            for (int x = 0; x < W; x += 2) {
                int v = ip.get(x, y) & 0xFF;
                hist[v]++;
            }
        }
        int total = 0; for (int h : hist) total += h;
        int lowCount = (int)Math.max(0, Math.floor(total * lowPct));
        int highCount = (int)Math.max(0, Math.floor(total * highPct));
        int c = 0, lowVal = 0, highVal = 255;
        for (int i = 0; i < 256; i++) { c += hist[i]; if (c >= lowCount) { lowVal = i; break; } }
        c = 0; for (int i = 0; i < 256; i++) { c += hist[i]; if (c >= highCount) { highVal = i; break; } }
        if (highVal <= lowVal) return;
        double scale = 255.0 / (highVal - lowVal);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int v = ip.get(x, y) & 0xFF;
                int nv = (int)Math.round((v - lowVal) * scale);
                if (nv < 0) nv = 0; else if (nv > 255) nv = 255;
                ip.set(x, y, nv);
            }
        }
    }

    private static void gaussianBlur(ImageProcessor ip, double sigmaApprox) {
        // Very small separable box-blur approximation (radius 1) to suppress tiny blebs
        int W = ip.getWidth(), H = ip.getHeight();
        int[][] temp = new int[H][W];
        // horizontal
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int s = 0, n = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx; if (xx < 0 || xx >= W) continue; s += (ip.get(xx, y) & 0xFF); n++;
                }
                temp[y][x] = s / n;
            }
        }
        // vertical
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int s = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy; if (yy < 0 || yy >= H) continue; s += temp[yy][x]; n++;
                }
                int v = s / n; if (v < 0) v = 0; if (v > 255) v = 255; ip.set(x, y, v);
            }
        }
    }

    private static int[] estimateGelBounds(ImageProcessor ip, int margin) {
        int W = ip.getWidth(), H = ip.getHeight();
        int start = margin, end = W - margin - 1;
        // Compute dark fraction per column on a coarse stride
        double[] frac = new double[W];
        for (int x = margin; x < W - margin; x++) {
            int dark = 0, cnt = 0;
            for (int y = H / 8; y < H - H / 8; y += 3) {
                int v = ip.get(x, y) & 0xFF; cnt++;
                if (v < 240) dark++;
            }
            frac[x] = dark / Math.max(1.0, cnt);
        }
        // Threshold and find longest contiguous run above threshold (ignore short blebs)
        double thr = 0.04; // require 4% non-white
        int minRun = Math.max(20, W / 25); // ignore short runs
        int bestLen = 0, bestS = start, bestE = end;
        int s = -1;
        for (int x = margin; x < W - margin; x++) {
            boolean active = frac[x] > thr;
            if (active) {
                if (s < 0) s = x;
            } else if (s >= 0) {
                int e = x - 1; int len = e - s + 1;
                if (len >= minRun && len > bestLen) { bestLen = len; bestS = s; bestE = e; }
                s = -1;
            }
        }
        if (s >= 0) {
            int e = W - margin - 1; int len = e - s + 1;
            if (len >= minRun && len > bestLen) { bestLen = len; bestS = s; bestE = e; }
        }
        // Expand slightly and clamp
        bestS = Math.max(margin, bestS - margin);
        bestE = Math.min(W - margin - 1, bestE + margin);
        return new int[]{bestS, bestE};
    }
    
    /**
     * Apply Savitzky-Golay-like smoothing for better lane boundary detection
     * Uses a quadratic local polynomial fit over the window
     */
    private static double[] applySavitzkyGolaySmoothing(double[] data, int radius) {
        int n = data.length;
        double[] smooth = new double[n];
        
        for (int i = 0; i < n; i++) {
            int left = Math.max(0, i - radius);
            int right = Math.min(n - 1, i + radius);
            int window = right - left + 1;
            
            if (window < 3) {
                // Too small for polynomial fit, use simple average
                double sum = 0;
                for (int j = left; j <= right; j++) sum += data[j];
                smooth[i] = sum / window;
            } else {
                // Simplified Savitzky-Golay with quadratic fit
                // For window size, compute weighted average with emphasis on center
                double sum = 0, weightSum = 0;
                for (int j = left; j <= right; j++) {
                    double weight = 1.0 - Math.abs(j - i) / (double)(radius + 1);
                    weight = weight * weight; // Quadratic weighting
                    sum += data[j] * weight;
                    weightSum += weight;
                }
                smooth[i] = sum / weightSum;
            }
        }
        return smooth;
    }
    
    /**
     * Edge trimming: drop partial lanes cut off by crop (< 70% of median lane width)
     */
    private static List<Lane> trimEdgeLanes(List<Lane> lanes) {
        if (lanes.size() < 3) return lanes; // Need at least 3 lanes to compute median
        
        // Calculate median lane width
        List<Integer> widths = new ArrayList<>();
        for (Lane lane : lanes) {
            widths.add(lane.xEnd() - lane.xStart() + 1);
        }
        Collections.sort(widths);
        int medianWidth = widths.get(widths.size() / 2);
        int minAcceptableWidth = (int)(medianWidth * 0.7); // 70% threshold
        
        // Filter out lanes that are too narrow (likely cropped)
        List<Lane> filtered = new ArrayList<>();
        int idx = 1;
        for (Lane lane : lanes) {
            int width = lane.xEnd() - lane.xStart() + 1;
            if (width >= minAcceptableWidth) {
                filtered.add(new Lane(idx++, lane.xStart(), lane.xEnd()));
            }
        }
        return filtered;
    }
    
    /**
     * Tilt compensation: estimate well row line and allow mild deskew before detection
     * This is a simplified version - full implementation would use Hough transform
     */
    private static ImageProcessor compensateTilt(ImageProcessor ip) {
        // Simplified tilt compensation: detect horizontal features
        int W = ip.getWidth(), H = ip.getHeight();
        
        // Sample horizontal projections at different y positions
        int[] sampleYs = {H/4, H/2, 3*H/4};
        double avgTilt = 0;
        int validSamples = 0;
        
        // Fast pixel array access
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        
        for (int y : sampleYs) {
            if (y >= 0 && y < H) {
                // Find local intensity variations along this row using array indexing
                double[] rowProfile = BufferPool.getDoubleBuffer(W);
                int rowStart = y * W;
                for (int x = 0; x < W; x++) {
                    rowProfile[x] = pixels[rowStart + x];
                }
                // Simple gradient analysis to detect tilt
                // This is a placeholder - full implementation would be more sophisticated
                validSamples++;
                
                BufferPool.returnDoubleBuffer(rowProfile);
            }
        }
        
        // For now, return original processor (full tilt compensation would require rotation)
        // Future enhancement: implement rotation based on detected tilt
        return ip;
    }
}
