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

    // All other parameters will be set in the unified optimization dialog
    private int expectedLaneCount = 0;
    private boolean constantLaneSpacing = false;
    private double laneWidthFraction = 0.55;
    private double gridOffsetFraction = 0.0;
    private boolean preprocessForDetection = true;
    private double postLowPct = 1.0;
    private double postHighPct = 99.0;
    private String postSmoothing = "light";

    @Override
    public void run() {
        GelUI ui = new GelUI(context);
        ui.show();
        
        // Get image from file selection or current ImageJ window
        ImagePlus imp = null;
        if (inputFile != null && inputFile.exists()) {
            IJ.log("AutoDense: Loading selected image file: " + inputFile.getName());
            imp = IJ.openImage(inputFile.getAbsolutePath());
        } else {
            // Try to get current active image window
            imp = IJ.getImage();
            if (imp != null) {
                IJ.log("AutoDense: Using current ImageJ image: " + imp.getTitle());
            }
        }
        
        if (imp != null) {
            imp.show(); // Ensure image is visible
            
            // Start with initial lane detection using default parameters
            List<Lane> initialLanes = LaneDetector.findLanes(imp, expectedLaneCount, constantLaneSpacing, 
                                                           laneWidthFraction, gridOffsetFraction, preprocessForDetection);
            
            // Open unified optimization dialog - user can adjust everything here
            runInteractiveOptimization(imp, initialLanes);
        } else {
            IJ.log("AutoDense: No image available. Please open an image first or select an image file.");
        }

    }

    private void runInteractiveOptimization(ImagePlus imp, List<Lane> lanes) {
        // Build unified dialog with ALL controls - wider dialog for better visibility
        NonBlockingGenericDialog gd = new NonBlockingGenericDialog("AutoDense - Complete Gel Analysis");
        gd.setSize(500, 600); // Make dialog larger for better slider visibility
        
        // Lane detection controls
        gd.addMessage("=== Lane Detection Settings ===");
        gd.addSlider("Expected lane count (0=auto):", 0, 20, expectedLaneCount);
        gd.addCheckbox("Assume constant lane spacing", constantLaneSpacing);
        gd.addCheckbox("Preprocess for detection", preprocessForDetection);
        
        // Lane positioning controls  
        gd.addMessage("=== Lane Positioning (constant spacing mode) ===");
        gd.addSlider("Lane width fraction:", 0.2, 0.9, laneWidthFraction);
        gd.addSlider("Grid offset fraction:", -0.25, 0.25, gridOffsetFraction);
        
        // Band detection controls
        gd.addMessage("=== Band Detection Optimization ===");
        gd.addSlider("Post-contrast low %:", 0, 20, postLowPct);
        gd.addSlider("Post-contrast high %:", 80, 100, postHighPct);
        gd.addChoice("Post-smoothing:", new String[]{"none","light","medium"}, postSmoothing);
        
        // AI assistance section
        gd.addMessage("=== AI Assistance ===");
        gd.addMessage("🤖 Future: AI-guided parameter optimization");
        
        gd.addMessage("💡 Tip: Drag sliders and watch overlays update live. Green=lanes, Red=bands");

        // Track current lanes outside the listener to prevent unwanted recalculation
        @SuppressWarnings("unchecked")
        final List<Lane>[] currentLanes = new List[1];
        currentLanes[0] = lanes;
        
        DialogListener listener = new DialogListener() {
            @Override
            public boolean dialogItemChanged(GenericDialog dlg, AWTEvent e) {
                // Get all parameters from dialog in correct order
                int newExpectedCount = Math.max(0, (int)dlg.getNextNumber());
                boolean newConstantSpacing = dlg.getNextBoolean();
                boolean newPreprocess = dlg.getNextBoolean();
                
                double newLaneWidth = dlg.getNextNumber();
                double newGridOffset = dlg.getNextNumber();
                
                double low = dlg.getNextNumber();
                double high = dlg.getNextNumber();
                String smooth = dlg.getNextChoice();

                // ONLY recalculate lanes if LANE DETECTION parameters changed
                boolean laneDetectionChanged = (newExpectedCount != expectedLaneCount) ||
                                             (newConstantSpacing != constantLaneSpacing) ||
                                             (newPreprocess != preprocessForDetection) ||
                                             (Math.abs(newLaneWidth - laneWidthFraction) > 0.001) ||
                                             (Math.abs(newGridOffset - gridOffsetFraction) > 0.001);

                if (laneDetectionChanged) {
                    IJ.log(String.format("AutoDense: Recalculating lanes - count: %d, constant: %s", 
                           newExpectedCount, newConstantSpacing));
                    currentLanes[0] = LaneDetector.findLanes(imp, newExpectedCount, newConstantSpacing, 
                                                           newLaneWidth, newGridOffset, newPreprocess);
                }

                // Always apply band detection optimization (this should not affect lanes)
                ImagePlus opt = imp.duplicate();
                applyContrastStretch(opt, low/100.0, high/100.0);
                if ("light".equalsIgnoreCase(smooth)) applyBoxBlur(opt, 1);
                else if ("medium".equalsIgnoreCase(smooth)) applyBoxBlur(opt, 2);

                // Draw overlay with better visibility
                Overlay ov = new Overlay();
                int h = imp.getHeight();
                for (Lane lane : currentLanes[0]) {
                    int x = lane.xStart();
                    int w = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                    
                    // Highly visible green lane ROI - thicker stroke
                    Roi laneRoi = new Roi(x, 0, w, h);
                    laneRoi.setStrokeColor(new Color(0, 255, 0, 220));
                    laneRoi.setStrokeWidth(3.0);
                    ov.add(laneRoi);
                    
                    // Highly visible red band markers - much thicker
                    List<Band> bands = com.betterdairy.autodense.analysis.BandDetector.findBands(opt, lane);
                    for (Band b : bands) {
                        int yb = Math.max(0, Math.min(h - 6, b.y()));
                        Roi bandRoi = new Roi(x, yb, w, 6);
                        bandRoi.setStrokeColor(new Color(255, 0, 0, 255));
                        bandRoi.setStrokeWidth(4.0);
                        ov.add(bandRoi);
                    }
                }
                
                imp.setOverlay(ov);
                imp.updateAndDraw();
                
                // Save current values
                expectedLaneCount = newExpectedCount;
                constantLaneSpacing = newConstantSpacing;
                preprocessForDetection = newPreprocess;
                laneWidthFraction = newLaneWidth;
                gridOffsetFraction = newGridOffset;
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
