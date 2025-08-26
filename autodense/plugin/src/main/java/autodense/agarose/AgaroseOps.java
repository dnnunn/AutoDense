package autodense.agarose;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import autodense.util.Csv;

import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public final class AgaroseOps {
    public static Map<String,Object> detectLanes(ImagePlus imp, Integer laneCount, Integer smoothPx, Path outDir) throws Exception {
        // Reuse SDS logic pattern—vertical projection → lanes
        return autodense.sds.SdsOps.detectLanes(imp, laneCount, smoothPx, outDir);
    }
    public static Map<String,Object> detectBands(ImagePlus imp, double minProm, int minDistPx, Path outDir) throws Exception {
        return autodense.sds.SdsOps.detectBands(imp, minProm, minDistPx, outDir);
    }

    public static Map<String,Object> calibrateLadder(ImagePlus imp, String ladderName, int laneIndex, Path bandsCsv, Path outCsv) throws Exception {
        // Load ladder bp list
        List<Integer> ladder = LadderSets.load(ladderName);
        List<Map<String,String>> bands = Csv.read(bandsCsv);
        // Filter chosen lane and sort by y_px ascending (top to bottom)
        List<Double> y = new ArrayList<>();
        for (var r : bands) if (Integer.parseInt(r.get("lane"))==laneIndex) y.add(Double.parseDouble(r.get("y_px")));
        y.sort(Comparator.naturalOrder());
        // Pair top N largest bp to smallest y (inverse relationship)
        int n = Math.min(ladder.size(), y.size());
        double[] X = new double[n], Y = new double[n];
        for (int i=0;i<n;i++){
            X[i] = y.get(i);                    // migration distance
            Y[i] = Math.log10(ladder.get(i));  // log(bp)
        }
        var fit = linreg(X,Y); // returns slope, intercept, r2

        // Emit per-band MW estimates
        List<Map<String,Object>> out = new ArrayList<>();
        for (var r : bands) {
            double yy = Double.parseDouble(r.get("y_px"));
            double logbp = fit.slope*yy + fit.intercept;
            double bp = Math.pow(10, logbp);
            out.add(Map.of(
                "id", Integer.parseInt(r.get("id")),
                "lane", Integer.parseInt(r.get("lane")),
                "y_px", yy,
                "bp", bp
            ));
        }
        Csv.write(outCsv, out, List.of("id","lane","y_px","bp"));
        return Map.of("fit", Map.of("slope",fit.slope,"intercept",fit.intercept,"r2",fit.r2), "mw_table", outCsv.toString());
    }

    private static class Fit { double slope, intercept, r2; Fit(double a,double b,double r){slope=a;intercept=b;r2=r;} }
    private static Fit linreg(double[] x,double[] y){
        int n=x.length; double sx=0,sy=0,sxx=0,sxy=0,syy=0;
        for(int i=0;i<n;i++){ sx+=x[i]; sy+=y[i]; sxx+=x[i]*x[i]; sxy+=x[i]*y[i]; syy+=y[i]*y[i]; }
        double denom = n*sxx - sx*sx; double slope=(n*sxy - sx*sy)/denom; double intercept=(sy - slope*sx)/n;
        double ssTot = syy - (sy*sy)/n; double ssRes=0; for(int i=0;i<n;i++){ double yhat=slope*x[i]+intercept; ssRes += (y[i]-yhat)*(y[i]-yhat); }
        double r2 = 1 - ssRes/ssTot;
        return new Fit(slope,intercept,r2);
    }
}