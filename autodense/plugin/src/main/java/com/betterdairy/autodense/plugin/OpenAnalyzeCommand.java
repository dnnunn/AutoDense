package com.betterdairy.autodense.plugin;

import net.imagej.ImageJ;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;

import java.io.File;
import java.awt.Color;
import java.awt.AWTEvent;
import java.util.List;

import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.model.Models.Band;

@Plugin(type = Command.class, menuPath = "Plugins>AutoDense>Open & Analyze")
public class OpenAnalyzeCommand implements Command {

    @Parameter(label = "Input image", style = "open", required = false)
    private File inputFile;

    @Parameter
    private Context context;

    @Parameter(label = "Expected lane count", min = "0", description = "0 = auto; set if you know the total number of lanes")
    private int expectedLaneCount = 0;

    @Parameter(label = "Assume constant lane spacing", description = "Evenly space lanes between gel edges when count is provided")
    private boolean constantLaneSpacing = false;

    // Lane layout fine-tuning
    @Parameter(label = "Lane width fraction (of spacing)", min = "0.2", max = "0.9")
    private double laneWidthFraction = 0.55;

    @Parameter(label = "Grid offset fraction (-0.15..0.15)", min = "-0.25", max = "0.25")
    private double gridOffsetFraction = 0.0;

    @Parameter(label = "Preprocess for detection", description = "Use light contrast & blur to find gel bounds")
    private boolean preprocessForDetection = true;

    // Post-detection optimization before band detection
    @Parameter(label = "Post-contrast low %", min = "0", max = "20")
    private double postLowPct = 1.0;

    @Parameter(label = "Post-contrast high %", min = "80", max = "100")
    private double postHighPct = 99.0;

    @Parameter(label = "Post-smoothing", choices = {"none", "light", "medium"})
    private String postSmoothing = "light";

    @Override
    public void run() {
        GelUI ui = new GelUI(context);
        ui.show();
        if (inputFile != null && inputFile.exists()) {
            ImagePlus imp = IJ.openImage(inputFile.getAbsolutePath());
            if (imp != null) {
                imp.show();
                // Detect lanes with user-tuned layout
                List<Lane> lanes = LaneDetector.findLanes(
                        imp,
                        Math.max(0, expectedLaneCount),
                        constantLaneSpacing,
                        laneWidthFraction,
                        gridOffsetFraction,
                        preprocessForDetection
                );
                if (!lanes.isEmpty()) {
                    // Interactive optimization loop with live preview
                    runInteractiveOptimization(imp, lanes);
                    IJ.log("AutoDense: lanes detected = " + lanes.size());
                } else {
                    IJ.log("AutoDense: no lanes detected");
                }
            } else {
                IJ.log("AutoDense: Failed to open image: " + inputFile);
            }
        }

    }

    private void runInteractiveOptimization(ImagePlus imp, List<Lane> lanes) {
        // Build dialog
        NonBlockingGenericDialog gd = new NonBlockingGenericDialog("Band Detection Optimization");
        gd.addNumericField("Post-contrast low %", postLowPct, 2);
        gd.addNumericField("Post-contrast high %", postHighPct, 2);
        gd.addChoice("Post-smoothing", new String[]{"none","light","medium"}, postSmoothing);
        gd.addMessage("Tip: adjust and watch red band marks update live. Close dialog when satisfied.");

        DialogListener listener = new DialogListener() {
            @Override
            public boolean dialogItemChanged(GenericDialog dlg, AWTEvent e) {
                double low = Math.max(0, Math.min(20, dlg.getNextNumber()));
                double high = Math.max(80, Math.min(100, dlg.getNextNumber()));
                String smooth = dlg.getNextChoice();

                // Recompute bands on an optimized duplicate, draw overlay on original
                ImagePlus opt = imp.duplicate();
                applyContrastStretch(opt, low/100.0, high/100.0);
                if ("light".equalsIgnoreCase(smooth)) applyBoxBlur(opt, 1);
                else if ("medium".equalsIgnoreCase(smooth)) applyBoxBlur(opt, 2);

                Overlay ov = new Overlay();
                int h = imp.getHeight();
                for (Lane lane : lanes) {
                    int x = lane.xStart();
                    int w = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                    Roi r = new Roi(x, 0, w, h);
                    r.setStrokeColor(new Color(0, 255, 0, 160));
                    ov.add(r);
                    List<Band> bands = com.betterdairy.autodense.analysis.BandDetector.findBands(opt, lane);
                    for (Band b : bands) {
                        int yb = Math.max(0, Math.min(h - 2, b.y()));
                        Roi br = new Roi(x, yb, w, 2);
                        br.setStrokeColor(new Color(255, 0, 0, 200));
                        ov.add(br);
                    }
                }
                imp.setOverlay(ov);
                imp.updateAndDraw();
                // Save last values so OK applies them
                postLowPct = low;
                postHighPct = high;
                postSmoothing = smooth;
                return true;
            }
        };
        gd.addDialogListener(listener);
        gd.showDialog();

        // On close, one last apply to persist overlay based on current fields
        listener.dialogItemChanged(gd, null);
    }

    // For local testing without ImageJ launcher
    public static void main(String[] args) {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
    }

    // --- Simple optimization helpers (duplicated here to allow user control) ---
    private static void applyContrastStretch(ImagePlus imp, double lowPct, double highPct) {
        ImageProcessor ip = imp.getProcessor();
        int W = ip.getWidth(), H = ip.getHeight();
        int[] hist = new int[256];
        for (int y = 0; y < H; y += 2) for (int x = 0; x < W; x += 2) hist[ip.get(x, y) & 0xFF]++;
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

    private static void applyBoxBlur(ImagePlus imp, int radius) {
        ImageProcessor ip = imp.getProcessor();
        int W = ip.getWidth(), H = ip.getHeight();
        int[][] tmp = new int[H][W];
        // horizontal
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int s = 0, n = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int xx = x + dx; if (xx < 0 || xx >= W) continue; s += (ip.get(xx, y) & 0xFF); n++;
                }
                tmp[y][x] = s / Math.max(1, n);
            }
        }
        // vertical
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int s = 0, n = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int yy = y + dy; if (yy < 0 || yy >= H) continue; s += tmp[yy][x]; n++;
                }
                int v = s / Math.max(1, n); if (v < 0) v = 0; if (v > 255) v = 255; ip.set(x, y, v);
            }
        }
    }
}
