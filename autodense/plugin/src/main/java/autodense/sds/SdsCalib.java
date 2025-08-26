package autodense.sds;

import com.fasterxml.jackson.databind.ObjectMapper;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.process.ImageProcessor;
import autodense.util.Csv;
import autodense.util.OverlayExporter;

import java.awt.*;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class SdsCalib {

    // --- public API ---

    // Calibrate: choose ladder lane (auto if null), detect dye front (auto if null),
    // fit log10(kDa) = a * Rf + b using ladder's expected bands.
    public static Map<String,Object> calibrateLadder(ImagePlus imp,
                                                     String ladderName,
                                                     Integer laneIndexIn,
                                                     Double frontYPxIn,
                                                     Path bandsCsv,
                                                     Path outCsv) throws Exception {
        var ladder = loadProteinLadder(ladderName);
        var bands  = Csv.read(bandsCsv);          // id,lane,y_px,intensity
        int laneIdx = (laneIndexIn != null) ? laneIndexIn : autoDetectLadderLane(bands, ladder);

        // well_y: top edge of lane ROI
        var laneBounds = laneBoundsFromOverlay(imp, laneIdx);
        double wellY = laneBounds.y;  // top of gel for that lane
        double frontY = (frontYPxIn != null) ? frontYPxIn : autoDetectDyeFront(imp);

        // collect ladder lane band y positions (ascending)
        List<Double> y = bands.stream()
                .filter(r -> Integer.parseInt(r.get("lane")) == laneIdx)
                .map(r -> Double.parseDouble(r.get("y_px")))
                .sorted()
                .collect(Collectors.toList());

        // compute Rf = (y - wellY) / (frontY - wellY), clamp to [0,1]
        double denom = Math.max(1.0, frontY - wellY);
        List<Double> rf = y.stream().map(yy -> Math.max(0, Math.min(1, (yy - wellY)/denom))).collect(Collectors.toList());

        // Pair top of gel (small Rf) with largest kDa → sort accordingly
        // Take N=min(len(ladder), len(bands))
        int n = Math.min(ladder.size(), rf.size());
        double[] X = new double[n];     // Rf
        double[] Y = new double[n];     // log10(kDa)
        for (int i=0;i<n;i++){
            X[i] = rf.get(i);
            Y[i] = Math.log10(ladder.get(i));
        }
        var fit = linreg(X, Y);

        // Emit per-band MW estimate for all bands (any lane)
        List<Map<String,Object>> out = new ArrayList<>();
        for (var r : bands) {
            double yy = Double.parseDouble(r.get("y_px"));
            double rfAny = Math.max(0, Math.min(1, (yy - wellY)/denom)); // use same reference well & front
            double logkda = fit.slope * rfAny + fit.intercept;
            double kda = Math.pow(10, logkda);
            out.add(Map.of(
                    "id", Integer.parseInt(r.get("id")),
                    "lane", Integer.parseInt(r.get("lane")),
                    "y_px", yy,
                    "Rf", rfAny,
                    "kDa", kda
            ));
        }
        Csv.write(outCsv, out, List.of("id","lane","y_px","Rf","kDa"));
        return Map.of(
                "fit", Map.of("slope",fit.slope,"intercept",fit.intercept,"r2",fit.r2),
                "mw_table", outCsv.toString(),
                "lane_index", laneIdx
        );
    }

    // Assign MW labels to overlay (non-destructive) using mw_table produced above
    public static Map<String,Object> assignMW(ImagePlus imp, Path mwCsv, Path outPng) throws Exception {
        var rows = Csv.read(mwCsv);
        Overlay ov = imp.getOverlay(); if (ov == null) ov = new Overlay();

        Map<Integer, Double> idToKDa = new HashMap<>();
        Map<Integer, Double> idToY   = new HashMap<>();
        for (var r : rows) {
            int id = Integer.parseInt(r.get("id"));
            idToKDa.put(id, Double.parseDouble(r.get("kDa")));
            idToY.put(id,   Double.parseDouble(r.get("y_px")));
        }
        // Add text labels near each band tick mark (your band ticks were Line ROIs named band_***)
        for (Roi r : imp.getOverlay().toArray()) {
            String name = r.getName();
            if (name != null && name.startsWith("band_")) {
                // parse id
                String idStr = name.split("_")[1];
                int id = Integer.parseInt(idStr);
                Double kda = idToKDa.get(id);
                if (kda != null) {
                    Rectangle b = r.getBounds();
                    String txt = String.format("%.0f kDa", roundNice(kda));
                    TextRoi tr = new TextRoi(b.x + b.width + 4, b.y - 6, txt);
                    tr.setStrokeColor(Color.white);
                    tr.setFillColor(new Color(0,0,0,160));
                    tr.setCurrentFont(new Font("SansSerif", Font.PLAIN, 12));
                    ov.add(tr);
                }
            }
        }
        imp.setOverlay(ov);
        OverlayExporter.exportOverlayPNG(imp, outPng.toFile());
        return Map.of("mw_overlay_png", outPng.toString(), "mw_csv", mwCsv.toString());
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private static List<Integer> loadProteinLadder(String name) throws Exception {
        try (InputStream is = SdsCalib.class.getResourceAsStream("/config/protein_ladders.json")) {
            var om = new ObjectMapper();
            Map<String, List<Integer>> m = om.readValue(is, Map.class);
            var list = m.get(name);
            if (list == null) throw new IllegalArgumentException("Unknown protein ladder: " + name);
            return list;
        }
    }

    // Heuristic: pick lane whose inter-band spacing ratios best match ladder ratios
    private static int autoDetectLadderLane(List<Map<String,String>> bands, List<Integer> ladder) {
        Map<Integer, List<Double>> laneY = new HashMap<>();
        for (var r : bands) {
            int lane = Integer.parseInt(r.get("lane"));
            laneY.computeIfAbsent(lane, k->new ArrayList<>()).add(Double.parseDouble(r.get("y_px")));
        }
        List<Double> ladRatios = ratiosFrom(ladder.size()); // 1,2,3,… positional ratios only
        double bestScore = Double.POSITIVE_INFINITY;
        int bestLane = 1;
        for (var e : laneY.entrySet()) {
            var ys = e.getValue().stream().sorted().collect(Collectors.toList());
            var r  = ratiosFrom(ys.size());
            double score = dtw(ladRatios, r);
            if (score < bestScore) { bestScore = score; bestLane = e.getKey(); }
        }
        return bestLane;
    }

    // very simple dye-front detection: search last 10% rows for max intensity stripe (bromophenol blue)
    private static double autoDetectDyeFront(ImagePlus imp) {
        ImageProcessor ip = imp.getProcessor();
        int w = ip.getWidth(), h = ip.getHeight();
        int y0 = (int)(h*0.85);
        double best=Double.NEGATIVE_INFINITY; int bestY = h-1;
        for (int y=y0; y<h; y++){
            double sum=0;
            for (int x=0; x<w; x++) sum += (255 - (ip.get(x,y)&0xff));
            if (sum>best){ best=sum; bestY=y; }
        }
        return bestY;
    }

    private static Rectangle laneBoundsFromOverlay(ImagePlus imp, int laneIndex) {
        for (Roi r : Optional.ofNullable(imp.getOverlay()).orElse(new Overlay()).toArray()) {
            if (("lane_"+laneIndex).equals(r.getName())) return r.getBounds();
        }
        // fallback: whole image
        return new Rectangle(0,0, imp.getWidth(), imp.getHeight());
    }

    private static class Fit { double slope, intercept, r2; Fit(double a,double b,double r){slope=a;intercept=b;r2=r;} }
    private static Fit linreg(double[] x,double[] y){
        int n=x.length; double sx=0,sy=0,sxx=0,sxy=0,syy=0;
        for (int i=0;i<n;i++){ sx+=x[i]; sy+=y[i]; sxx+=x[i]*x[i]; sxy+=x[i]*y[i]; syy+=y[i]*y[i]; }
        double denom = Math.max(1e-9, n*sxx - sx*sx);
        double slope=(n*sxy - sx*sy)/denom; double intercept=(sy - slope*sx)/n;
        double ssTot = syy - (sy*sy)/n; double ssRes=0; for (int i=0;i<n;i++){ double yhat=slope*x[i]+intercept; ssRes += (y[i]-yhat)*(y[i]-yhat); }
        double r2 = (ssTot<=0) ? 1.0 : 1 - ssRes/ssTot;
        return new Fit(slope,intercept,r2);
    }

    private static double roundNice(double v){
        if (v>=100) return Math.round(v/5.0)*5.0;
        if (v>=30)  return Math.round(v/2.0)*2.0;
        return Math.round(v);
    }

    // crude ratio signatures
    private static List<Double> ratiosFrom(int n){
        List<Double> r = new ArrayList<>();
        for (int i=1;i<n;i++) r.add(i/(double)(n-1));
        return r;
    }
    private static double dtw(List<Double> a, List<Double> b){
        int n=a.size(), m=b.size();
        double[][] dp = new double[n+1][m+1];
        for (int i=0;i<=n;i++) Arrays.fill(dp[i], 1e9);
        dp[0][0]=0;
        for (int i=1;i<=n;i++){
            for (int j=1;j<=m;j++){
                double cost = Math.abs(a.get(i-1)-b.get(j-1));
                dp[i][j] = cost + Math.min(dp[i-1][j], Math.min(dp[i][j-1], dp[i-1][j-1]));
            }
        }
        return dp[n][m];
    }
}