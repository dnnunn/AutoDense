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

    public static Map<String,Object> detectLanes(ImagePlus imp, Integer laneCount, Integer smoothPx, Path outDir) throws Exception {
        ImagePlus work = imp.duplicate();
        IJ.run(work, "8-bit", "");
        new BackgroundSubtracter().rollingBallBackground(work.getProcessor(), 50, false, false, false, false, false);

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

    public static Map<String,Object> detectBands(ImagePlus imp, double minProm, int minDistPx, Path outDir) throws Exception {
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
            new BackgroundSubtracter().rollingBallBackground(laneImp.getProcessor(), 30, false, false, false, false, false);

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
        // Dumb but consistent integration: area under local baseline around band Y
        List<Map<String,String>> rows = Csv.read(bandsCsv);
        List<Map<String,Object>> out = new ArrayList<>();
        for (var r : rows) {
            int id = Integer.parseInt(r.get("id"));
            int y = (int) Double.parseDouble(r.get("y_px"));
            imp.setRoi(0, Math.max(0,y-6), imp.getWidth(), 12);
            ImagePlus strip = new ImagePlus("strip", imp.getProcessor().crop());
            IJ.run(strip, "8-bit", "");
            ImageStatistics st = strip.getStatistics(Measurements.MEAN | Measurements.AREA);
            out.add(Map.of(
                "id", id,
                "auc", st.mean * st.area
            ));
        }
        Csv.write(outCsv, out, List.of("id","auc"));
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
}