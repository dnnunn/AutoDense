package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Band;
import com.betterdairy.autodense.model.Models.Lane;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public final class BandDetector {
    private BandDetector() {}

    public static List<Band> findBands(ImagePlus imp, Lane lane) {
        // Project intensities horizontally between lane x-bounds and find peaks
        ImageProcessor ip = imp.getProcessor();
        int w = ip.getWidth();
        int h = ip.getHeight();
        int x0 = Math.max(0, lane.xStart());
        int x1 = Math.min(w - 1, lane.xEnd());

        double[] proj = new double[h];
        for (int y = 0; y < h; y++) {
            double s = 0;
            for (int x = x0; x <= x1; x++) s += ip.get(x, y) & 0xFF;
            proj[y] = s / Math.max(1, (x1 - x0 + 1)); // mean intensity row-wise in lane
        }

        // Smooth with small vertical window
        int radius = Math.max(3, h / 200);
        double[] smooth = new double[h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius);
            int y1 = Math.min(h - 1, y + radius);
            double sum = 0;
            for (int yi = y0; yi <= y1; yi++) sum += proj[yi];
            smooth[y] = sum / (y1 - y0 + 1);
        }

        // Gels often have dark bands. Detect valleys by inverting signal.
        double[] inv = new double[h];
        double max = Double.NEGATIVE_INFINITY;
        for (int y = 0; y < h; y++) if (smooth[y] > max) max = smooth[y];
        for (int y = 0; y < h; y++) inv[y] = max - smooth[y];

        // Peak detection on inverted signal
        double mean = 0, std = 0;
        for (double v : inv) mean += v;
        mean /= h;
        for (double v : inv) std += (v - mean) * (v - mean);
        std = Math.sqrt(std / Math.max(1, h - 1));
        double thresh = mean + 0.6 * std; // slightly stricter

        List<Band> bands = new ArrayList<>();
        int idx = 1;
        int y = 1;
        while (y < h - 1) {
            if (inv[y] > thresh && inv[y] > inv[y - 1] && inv[y] >= inv[y + 1]) {
                int top = y, bot = y;
                while (top - 1 >= 0 && inv[top - 1] == inv[y]) top--;
                while (bot + 1 < h && inv[bot + 1] == inv[y]) bot++;
                int peakY = (top + bot) / 2;
                // expand to local minima in original smooth (band boundaries)
                int u = peakY, d = peakY;
                while (u - 1 >= 1 && smooth[u - 1] >= smooth[u]) u--;
                while (d + 1 < h - 1 && smooth[d + 1] >= smooth[d]) d++;

                // Macro-inspired background estimation using expanded side regions
                int expand = 3; // pixels; analogous to macro default
                // sample sides if available, otherwise top/bottom
                double bg;
                if (x0 - expand >= 0 || x1 + expand < w) {
                    bg = medianSideBackground(ip, x0, x1, u, d, expand);
                } else {
                    bg = medianTopBottomBackground(ip, x0, x1, u, d, expand);
                }

                // Compute band integral relative to background using row means
                double area = 0;
                for (int yi = u; yi <= d; yi++) area += Math.max(0, bg - proj[yi]);
                bands.add(new Band(idx++, peakY, area, bg, Double.NaN));
                y = bot + 1;
            } else y++;
        }

        return bands;
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
