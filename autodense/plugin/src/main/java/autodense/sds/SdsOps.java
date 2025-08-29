package autodense.sds;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import autodense.util.Csv;
import autodense.util.OverlayExporter;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public final class SdsOps {

    public static Map<String,Object> detectLanes(ImagePlus imp, Integer laneCount, Integer smoothPx, Double backgroundRemovalRadius, Path outDir) throws Exception {
        ImagePlus work = imp.duplicate();
        IJ.run(work, "8-bit", "");
        double bgRadius = (backgroundRemovalRadius != null) ? backgroundRemovalRadius : 50.0; // FIXED: YAML-controlled parameter
        if (bgRadius > 0) {
            new BackgroundSubtracter().rollingBallBackground(work.getProcessor(), bgRadius, false, false, false, false, false);
        }

        ImageProcessor ip = work.getProcessor();
        int w = ip.getWidth(), h = ip.getHeight();
        double[] colSum = new double[w];
        for (int x=0; x<w; x++) {
            double sum = 0;
            for (int y=0; y<h; y++) sum += (255 - (ip.get(x,y) & 0xff));
            colSum[x] = sum;
        }
        int k = smoothPx == null ? 9 : Math.max(3, smoothPx|1);
        colSum = smooth(colSum, k);

        List<Integer> peaks = peakFind(colSum, /*minProm*/ 0.05 * max(colSum), /*minDist*/ 20);
        if (laneCount != null && peaks.size() != laneCount) {
            // fallback: split width evenly
            peaks = new ArrayList<>();
            for (int i=0;i<laneCount;i++) peaks.add((int)Math.round((i+0.5)*(w/(double)laneCount)));
        }

        int laneW = (int)Math.round(w / (double) Math.max(1, peaks.size()));
        Overlay ov = imp.getOverlay(); if (ov == null) ov = new Overlay();
        List<Roi> laneRois = new ArrayList<>();
        for (int i=0;i<peaks.size();i++) {
            int cx = peaks.get(i);
            int x0 = Math.max(0, cx - laneW/2);
            Roi r = new Roi(x0, 0, Math.min(laneW, w-x0), h);
            r.setStrokeColor(Color.getHSBColor(0.12f, 1f, 0.9f));
            r.setStrokeWidth(1.5);
            r.setName("lane_"+(i+1));
            ov.add(r);
            laneRois.add(r);
        }
        imp.setOverlay(ov);

        Path lanesPng = outDir.resolve("lanes_overlay.png");
        OverlayExporter.exportOverlayPNG(imp, lanesPng.toFile());
        return Map.of("lanes_rois", "lane_"+laneRois.size(), "lanes_png", lanesPng.toString());
    }

    public static Map<String,Object> detectBands(ImagePlus imp, double minProm, int minDistPx, Double backgroundRemovalRadius, Path outDir) throws Exception {
        // For each lane ROI, compute horizontal profile and detect peaks
        Overlay ov = imp.getOverlay(); if (ov == null) throw new IllegalStateException("No lanes overlay");
        List<Roi> lanes = Arrays.asList(ov.toArray());
        List<Map<String,Object>> rows = new ArrayList<>();
        int gid = 1;
        for (int li=0; li<lanes.size(); li++) {
            Roi lane = lanes.get(li);
            ImageStatistics stats = imp.getStatistics(Measurements.RECT); // ensure rect
            imp.setRoi(lane);
            ImagePlus laneImp = new ImagePlus("lane", imp.getProcessor().crop());
            IJ.run(laneImp, "8-bit", "");
            double bgRadius = (backgroundRemovalRadius != null) ? backgroundRemovalRadius : 30.0; // FIXED: YAML-controlled parameter
            if (bgRadius > 0) {
                new BackgroundSubtracter().rollingBallBackground(laneImp.getProcessor(), bgRadius, false, false, false, false, false);
            }

            ImageProcessor ip = laneImp.getProcessor();
            int w = ip.getWidth(), h = ip.getHeight();
            double[] rowSum = new double[h];
            for (int y=0; y<h; y++) {
                double sum=0;
                for (int x=0; x<w; x++) sum += (255 - (ip.get(x,y)&0xff));
                rowSum[y] = sum;
            }
            rowSum = smooth(rowSum, 9);
            List<Integer> bands = peakFind(rowSum, minProm, minDistPx);

            for (Integer y : bands) {
                int gy = y + lane.getBounds().y;
                Line tick = new Line(lane.getBounds().x, gy, lane.getBounds().x+lane.getBounds().width, gy);
                tick.setStrokeColor(Color.getHSBColor(0.58f, 1f, 1f));
                tick.setStrokeWidth(1.0);
                tick.setName(String.format("band_%03d_lane_%02d", gid, li+1));
                ov.add(tick);
                rows.add(Map.of(
                    "id", gid,
                    "lane", li+1,
                    "y_px", gy,
                    "intensity", rowSum[y]
                ));
                gid++;
            }
        }
        imp.setOverlay(ov);
        Path bandsPng = outDir.resolve("bands_overlay.png");
        OverlayExporter.exportOverlayPNG(imp, bandsPng.toFile());

        Path csv = outDir.resolve("bands.csv");
        Csv.write(csv, rows, List.of("id","lane","y_px","intensity"));
        return Map.of("bands_table", csv.toString(), "bands_overlay_png", bandsPng.toString());
    }

    public static Map<String,Object> integrate(ImagePlus imp, Path bandsCsv, Path outCsv) throws Exception {
        return integrate(imp, bandsCsv, outCsv, null, null, null, null);
    }
    
    public static Map<String,Object> integrate(ImagePlus imp, Path bandsCsv, Path outCsv, Integer baselineWin, Double baselineQ, Integer bandHalfwin, Double backgroundRemovalRadius) throws Exception {
        var rows = Csv.read(bandsCsv); // id,lane,y_px,...
        // Group bands by lane to reuse per-lane background
        Map<Integer, List<Map<String,String>>> byLane = new HashMap<>();
        for (var r : rows) {
            int lane = Integer.parseInt(r.get("lane"));
            byLane.computeIfAbsent(lane, k->new ArrayList<>()).add(r);
        }

        List<Map<String,Object>> out = new ArrayList<>();
        for (var e : byLane.entrySet()) {
            int laneIdx = e.getKey();
            Roi lane = findLaneRoi(imp, laneIdx); // your lane ROI from overlay
            imp.setRoi(lane);
            ImagePlus laneImp = new ImagePlus("lane", imp.getProcessor().crop());
            IJ.run(laneImp, "8-bit", "");
            double bgRadius = (backgroundRemovalRadius != null) ? backgroundRemovalRadius : 20.0; // FIXED: YAML-controlled parameter
            if (bgRadius > 0) {
                new BackgroundSubtracter().rollingBallBackground(laneImp.getProcessor(), bgRadius, false, false, false, false, false);
            }

            // Build lane profile and baseline with configurable parameters
            double[] prof = laneProfile(laneImp.getProcessor());           // length = lane height
            int windowSize = (baselineWin != null) ? baselineWin : 21;  // sliding window size for quantile baseline
            double q = (baselineQ != null) ? baselineQ : 0.10;  // quantile for baseline (default 10th percentile)
            double[] base = quantileBaseline(prof, windowSize, q);
            base = smoothSG(base);
            double[] resid = subtract(prof, base);

            // Integrate around each band position (±halfwin)
            int halfWindow = (bandHalfwin != null) ? bandHalfwin : 6;  // integration half-window around band peak
            for (var r : e.getValue()) {
                int id = Integer.parseInt(r.get("id"));
                int y  = (int)Math.round(Double.parseDouble(r.get("y_px")) - lane.getBounds().y);
                int y0 = Math.max(0, y - halfWindow), y1 = Math.min(resid.length-1, y + halfWindow);
                double auc=0;
                for (int yy=y0; yy<=y1; yy++) auc += resid[yy];
                out.add(Map.of("id", id, "lane", laneIdx, "auc_bgsub", auc));
            }
        }
        Csv.write(outCsv, out, List.of("id","lane","auc_bgsub"));
        return Map.of("bands_csv", outCsv.toString());
    }

    // --- helpers ---
    private static double[] smooth(double[] a, int k) {
        double[] out = new double[a.length];
        int r = k/2;
        for (int i=0;i<a.length;i++){
            double s=0; int n=0;
            for (int j=i-r;j<=i+r;j++){
                if (j>=0 && j<a.length){ s+=a[j]; n++; }
            }
            out[i]=s/n;
        }
        return out;
    }
    private static List<Integer> peakFind(double[] a, double minProm, int minDist){
        List<Integer> idx = new ArrayList<>();
        int last=-9999;
        for (int i=1;i<a.length-1;i++){
            if (a[i]>a[i-1] && a[i]>a[i+1] && a[i]>=minProm && (i-last)>=minDist){
                idx.add(i); last=i;
            }
        }
        return idx;
    }
    private static double max(double[] a){ double m=Double.NEGATIVE_INFINITY; for(double v:a) m=Math.max(m,v); return m; }
    
    // --- Lane-wise background model helpers ---
    static double[] laneProfile(ImageProcessor ip) {
        int w = ip.getWidth(), h = ip.getHeight();
        double[] rowSum = new double[h];
        for (int y=0; y<h; y++) {
            double s=0;
            for (int x=0; x<w; x++) s += (255 - (ip.get(x,y)&0xff));
            rowSum[y] = s;
        }
        return rowSum;
    }

    static double[] quantileBaseline(double[] a, int win, double q) {
        // sliding window q-quantile (q in [0,1], e.g., 0.1)
        int n=a.length; double[] b=new double[n];
        int r = Math.max(1, win/2);
        double[] buf = new double[2*r+1];
        for (int i=0;i<n;i++){
            int k=0;
            for (int j=i-r;j<=i+r;j++){
                buf[k++] = (j>=0 && j<n) ? a[j] : a[Math.max(0, Math.min(n-1,j))];
            }
            java.util.Arrays.sort(buf,0,k);
            int idx = (int)Math.round(Math.max(0, Math.min(k-1, q*(k-1))));
            b[i] = buf[idx];
        }
        return b;
    }

    static double[] smoothSG(double[] a) {
        // 5-point Savitzky–Golay (quadratic) smoother: [-3,12,17,12,-3]/35
        int n=a.length; double[] s=new double[n];
        for (int i=0;i<n;i++){
            double v=0; double w=0;
            for (int k=-2;k<=2;k++){
                int j=Math.max(0,Math.min(n-1,i+k));
                int c = switch(k){case -2,-1,1,2 -> 12; case 0 -> 17; default -> 0;};
                if (k==-2 || k==2) c = -3;
                v += c * a[j];
                w += Math.abs(c);
            }
            s[i] = v / (w==0?1:w);
        }
        return s;
    }

    static double[] subtract(double[] a, double[] b){
        double[] r=new double[a.length];
        for (int i=0;i<a.length;i++) r[i]=Math.max(0,a[i]-b[i]);
        return r;
    }

    private static Roi findLaneRoi(ImagePlus imp, int laneIdx){
        Overlay ov = imp.getOverlay(); if (ov==null) throw new IllegalStateException("No overlay");
        for (Roi r : ov.toArray()) if (("lane_"+laneIdx).equals(r.getName())) return r;
        throw new IllegalArgumentException("Lane ROI lane_"+laneIdx+" not found");
    }
}