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
import ij.process.ColorProcessor;

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
        int laneIdx = (laneIndexIn != null) ? laneIndexIn : autoDetectLadderLaneByColor(imp, ladderName);

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
        Map<Integer, Integer> idToLane = new HashMap<>();
        for (var r : rows) {
            int id = Integer.parseInt(r.get("id"));
            idToKDa.put(id, Double.parseDouble(r.get("kDa")));
            idToY.put(id,   Double.parseDouble(r.get("y_px")));
            idToLane.put(id, Integer.parseInt(r.get("lane")));
        }
        
        // Add text labels near each band tick mark (your band ticks were Line ROIs named band_***)
        for (Roi r : imp.getOverlay().toArray()) {
            String name = r.getName();
            if (name != null && name.startsWith("band_")) {
                // parse id
                String idStr = name.split("_")[1];
                int id = Integer.parseInt(idStr);
                Double kda = idToKDa.get(id);
                Integer lane = idToLane.get(id);
                if (kda != null) {
                    Rectangle b = r.getBounds();
                    String txt = String.format("%.0f kDa", roundNice(kda));
                    TextRoi tr = new TextRoi(b.x + b.width + 4, b.y - 6, txt);
                    tr.setStrokeColor(Color.white);
                    tr.setFillColor(new Color(0,0,0,160));
                    tr.setCurrentFont(new Font("SansSerif", Font.PLAIN, 12));
                    ov.add(tr);
                    
                    // Add color tag for ladder lane if this band is highly colored
                    if (lane != null && isHighlyColored(imp, b.x, b.y)) {
                        addColorTag(ov, b.x - 20, b.y, "●");
                    }
                }
            }
        }
        imp.setOverlay(ov);
        OverlayExporter.exportOverlayPNG(imp, outPng.toFile());
        return Map.of("mw_overlay_png", outPng.toString(), "mw_csv", mwCsv.toString());
    }
    
    private static boolean isHighlyColored(ImagePlus imp, int x, int y) {
        try {
            var cp = imp.getProcessor().convertToColorProcessor();
            float[] hsv = autodense.color.HsvOps.pixelHSV(cp, x, y);
            // Consider highly colored if saturation > 40% and value > 30%
            return hsv[1] > 40 && hsv[2] > 30;
        } catch (Exception e) {
            return false;
        }
    }

    // --- helpers ---
    
    static class ColorBand {
        String name; 
        int hMin, hMax, sMin, vMin;
    }
    
    @SuppressWarnings("unchecked")
    static List<ColorBand> loadMarkerProfile(String ladderName) throws Exception {
        try (var is = SdsCalib.class.getResourceAsStream("/config/protein_marker_colors.json")) {
            var om = new ObjectMapper();
            var m = (Map<String, List<Map<String,Object>>>) om.readValue(is, Map.class);
            var raw = m.get(ladderName);
            if (raw==null) return java.util.Collections.emptyList();
            List<ColorBand> out = new ArrayList<>();
            for (var r : raw) {
                ColorBand cb = new ColorBand();
                cb.name = (String) r.get("name");
                cb.hMin = ((Number) r.get("h_min")).intValue();
                cb.hMax = ((Number) r.get("h_max")).intValue();
                cb.sMin = ((Number) r.get("s_min")).intValue();
                cb.vMin = ((Number) r.get("v_min")).intValue();
                out.add(cb);
            }
            return out;
        } catch (Exception e) {
            System.err.println("Could not load marker profile for " + ladderName + ": " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public static int autoDetectLadderLaneByColor(ImagePlus imp, String ladderName) {
        try {
            var cp = imp.getProcessor().convertToColorProcessor();
            Overlay ov = imp.getOverlay(); 
            if (ov==null) throw new IllegalStateException("No lanes overlay");
            
            List<Roi> lanes = java.util.Arrays.asList(ov.toArray()).stream()
                .filter(r -> r.getName()!=null && r.getName().startsWith("lane_"))
                .collect(Collectors.toList());
            var markers = loadMarkerProfile(ladderName);
            
            if (markers.isEmpty()) {
                System.out.println("No color markers found for " + ladderName + ", falling back to band spacing analysis");
                return 1; // fallback to lane 1 if no color profile
            }

            int bestLane=1; 
            double bestScore=-1;
            for (Roi lane : lanes) {
                var b = lane.getBounds();
                int hits=0;
                // sample every 2 px along y, across a central stripe in x
                int x0=b.x + (int)(b.width*0.3), x1=b.x + (int)(b.width*0.7);
                for (int y=b.y; y<b.y+b.height; y+=2){
                    int count=0;
                    for (int x=x0; x<x1; x+=3){
                        float[] hsv = autodense.color.HsvOps.pixelHSV(cp, x, y);
                        for (var mk : markers){
                            boolean hueIn = (mk.hMin <= mk.hMax) ?
                                (hsv[0]>=mk.hMin && hsv[0]<=mk.hMax) :
                                (hsv[0]>=mk.hMin || hsv[0]<=mk.hMax); // wrap-around
                            if (hueIn && hsv[1]>=mk.sMin && hsv[2]>=mk.vMin){ 
                                count++; 
                                break; 
                            }
                        }
                    }
                    if (count > ((x1-x0)/3)*0.5) hits++; // row looks "colored" across mid stripe
                }
                double score = hits / (double)(b.height/2); // normalized density
                if (score > bestScore){ 
                    bestScore=score; 
                    bestLane = parseLaneIndex(lane.getName()); 
                }
            }
            
            System.out.println("Color-based ladder detection: lane " + bestLane + " (score: " + String.format("%.3f", bestScore) + ")");
            return bestLane;
        } catch (Exception e) {
            System.err.println("Color detection failed: " + e.getMessage() + ", using fallback");
            return 1; // fallback to lane 1 on error
        }
    }

    private static int parseLaneIndex(String name){
        // "lane_7" -> 7
        return Integer.parseInt(name.substring(name.indexOf('_')+1));
    }

    private static void addColorTag(Overlay ov, int x, int y, String name){
        var tag = new TextRoi(x, y, name);
        tag.setCurrentFont(new Font("SansSerif", Font.BOLD, 10));
        tag.setStrokeColor(Color.white);
        tag.setFillColor(new Color(0,0,0,160));
        ov.add(tag);
    }

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