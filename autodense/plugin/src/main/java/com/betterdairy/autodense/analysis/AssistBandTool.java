package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.analysis.AssistModels.AssistBand;
import com.betterdairy.autodense.analysis.AssistModels.AssistResult;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;

/**
 * User-assisted band identification tool for AutoDense.
 * Allows users to click on a band to seed identification across all lanes.
 * 
 * Workflow:
 * 1. User clicks on a band in any lane
 * 2. System refines the band position and measures properties
 * 3. System propagates to other lanes using Rf (relative mobility) matching
 * 4. Results are displayed with confidence indicators
 */
public final class AssistBandTool {
    
    private final List<Lane> lanes;
    private ImageCanvas canvas;
    private MouseAdapter clicker;
    private boolean isActive = false;
    
    // Enhanced configuration with all parameters
    private AssistConfig config = new AssistConfig();
    
    // UX state management
    private Stack<AssistResult> undoStack = new Stack<>();
    private Map<Integer, String> laneMessages = new HashMap<>(); // Lane index -> message
    private boolean hasMolecularWeightCalibration = false;
    
    /**
     * Configuration container for all AssistBandTool parameters
     */
    public static class AssistConfig {
        // Search and tolerance parameters
        public int searchWindowPx = 30;        // ± around click for refinement
        public double rfTolerance = 0.02;      // ± 0.02 default for Rf matching
        public double minSnr = 2.0;            // Minimum SNR for acceptance  
        public float minProminence = 0.03f;    // Minimum peak prominence
        public double[] widthRatioRange = {0.5, 2.0}; // Width similarity range
        
        // Propagation strategy parameters
        public boolean preferMolecularWeight = true;  // Use MW if available, fallback to Rf
        public double perLaneWindowPercent = 0.03;    // 3% of lane height per-lane search
        public int minPerLaneWindowPx = 8;             // Minimum 8px search window
        
        // UX parameters
        public Color seedBandColor = Color.MAGENTA;           // Seed band color
        public Color acceptedBandColor = Color.CYAN;          // Accepted bands
        public Color confirmedBandColor = Color.GREEN;        // User-confirmed bands  
        public Color ambiguousBandColor = Color.ORANGE;       // Needs confirmation
        public double confidenceThresholdAmbiguous = 0.6;    // Below this = ambiguous
        
        public AssistConfig() {}
        
        public AssistConfig(AssistConfig other) {
            this.searchWindowPx = other.searchWindowPx;
            this.rfTolerance = other.rfTolerance;
            this.minSnr = other.minSnr;
            this.minProminence = other.minProminence;
            this.widthRatioRange = other.widthRatioRange.clone();
            this.preferMolecularWeight = other.preferMolecularWeight;
            this.perLaneWindowPercent = other.perLaneWindowPercent;
            this.minPerLaneWindowPx = other.minPerLaneWindowPx;
            this.seedBandColor = other.seedBandColor;
            this.acceptedBandColor = other.acceptedBandColor;
            this.confirmedBandColor = other.confirmedBandColor;
            this.ambiguousBandColor = other.ambiguousBandColor;
            this.confidenceThresholdAmbiguous = other.confidenceThresholdAmbiguous;
        }
    }
    
    public AssistBandTool(List<Lane> lanes) {
        this.lanes = new ArrayList<>(lanes);
    }
    
    /**
     * Create AssistBandTool with custom configuration
     */
    public AssistBandTool(List<Lane> lanes, AssistConfig config) {
        this.lanes = new ArrayList<>(lanes);
        this.config = new AssistConfig(config);
    }
    
    /**
     * Update configuration parameters
     */
    public void setConfig(AssistConfig config) {
        this.config = new AssistConfig(config);
    }
    
    /**
     * Get current configuration (copy)
     */
    public AssistConfig getConfig() {
        return new AssistConfig(config);
    }
    
    /**
     * Set molecular weight calibration availability
     */
    public void setMolecularWeightCalibration(boolean available) {
        this.hasMolecularWeightCalibration = available;
    }
    
    /**
     * Undo the last assist operation
     */
    public boolean undo(ImagePlus imp) {
        if (undoStack.isEmpty()) {
            IJ.showMessage("BandAssist", "No operations to undo.");
            return false;
        }
        
        AssistResult lastResult = undoStack.pop();
        // Clear the display from the last result
        imp.setOverlay(null);
        
        // If there's a previous result, redisplay it
        if (!undoStack.isEmpty()) {
            displayResult(imp, undoStack.peek());
        }
        
        IJ.showStatus("BandAssist: Undid last operation (" + undoStack.size() + " operations remaining)");
        return true;
    }
    
    /**
     * Clear all assist operations
     */
    public void clearAll(ImagePlus imp) {
        undoStack.clear();
        laneMessages.clear();
        imp.setOverlay(null);
        IJ.showStatus("BandAssist: Cleared all operations");
    }
    
    /**
     * Enable assist mode - user clicks will seed band identification
     */
    public void enable(ImagePlus imp) {
        if (canvas != null) disable();
        
        canvas = imp.getCanvas();
        clicker = new MouseAdapter() {
            @Override 
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                int ox = canvas.offScreenX(e.getX());
                int oy = canvas.offScreenY(e.getY());
                onUserClick(imp, ox, oy);
            }
        };
        
        canvas.addMouseListener(clicker);
        canvas.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR));
        isActive = true;
        
        IJ.showStatus("BandAssist: Click on a band to identify it across all lanes");
    }
    
    /**
     * Disable assist mode
     */
    public void disable() {
        if (canvas != null && clicker != null) {
            canvas.removeMouseListener(clicker);
            canvas.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR));
        }
        canvas = null; 
        clicker = null;
        isActive = false;
        IJ.showStatus("BandAssist disabled");
    }
    
    /**
     * Check if assist mode is currently active
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Handle user click - the core of the assisted identification
     */
    private void onUserClick(ImagePlus imp, int x, int y) {
        try {
            // Find which lane contains the click
            Lane clickedLane = findLaneAtX(x);
            if (clickedLane == null) {
                IJ.showMessage("BandAssist", "Click was not in any lane. Please click within a lane.");
                return;
            }
            
            // Refine the clicked position to find the nearest band
            AssistBand seedBand = refineBandAtY(imp, clickedLane, y, config.searchWindowPx);
            if (seedBand == null) {
                IJ.showMessage("BandAssist", "No clear band found near click position. Try clicking closer to a band.");
                return;
            }
            
            // Mark as seed band
            seedBand.flags.add("seed");
            seedBand.confidence = 1.0; // Seed band has 100% confidence
            
            // Use Rf-based propagation
            double rfSeed = computeRf(clickedLane, seedBand, imp.getHeight());
            List<AssistBand> propagatedBands = propagateToOtherLanes(imp, clickedLane, seedBand, rfSeed);
            IJ.showStatus("BandAssist: Propagating by Rf (" + String.format("%.3f", rfSeed) + ")");
            
            // Create result 
            AssistResult result = new AssistResult(seedBand, propagatedBands, lanes.size());
            
            // Add to undo stack
            undoStack.push(result);
            
            // Update display 
            displayResult(imp, result);
            
            // Show summary
            String message = String.format(
                "BandAssist Result:\n" +
                "✓ Seed band at Rf=%.3f\n" +
                "✓ Found in %d/%d lanes\n" +
                "\nGreen = High confidence\n" +
                "Orange = Medium confidence\n" +
                "Red = Low confidence",
                rfSeed, result.successfulLanes, result.totalLanes
            );
            
            IJ.showMessage("BandAssist Complete", message);
            
        } catch (Exception e) {
            IJ.showMessage("BandAssist Error", "Error during band identification: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Find which lane contains the given x coordinate
     */
    private Lane findLaneAtX(int x) {
        for (Lane lane : lanes) {
            if (x >= lane.xStart() && x <= lane.xEnd()) {
                return lane;
            }
        }
        return null;
    }
    
    /**
     * Refine band position around click using peak detection
     */
    private AssistBand refineBandAtY(ImagePlus imp, Lane lane, int yClick, int windowPx) {
        // Get intensity profile for this lane
        float[] profile = Profiles.verticalSum(imp, lane.xStart(), lane.xEnd());
        int y0 = Math.max(0, yClick - windowPx);
        int y1 = Math.min(profile.length - 1, yClick + windowPx);
        
        // Find nearest prominent peak
        int peak = Peaks.argmax(profile, y0, y1);
        if (!Peaks.isProminent(profile, peak, config.minProminence)) {
            return null;
        }
        
        // Find band boundaries (valleys)
        int leftValley = Peaks.leftValley(profile, peak);
        int rightValley = Peaks.rightValley(profile, peak);
        
        // Create and quantify the band
        AssistBand band = Quant.integrateBand(imp, lane, leftValley, rightValley);
        
        // Check quality thresholds
        if (band.snr < config.minSnr) {
            return null; // Too noisy
        }
        
        return band;
    }
    
    /**
     * Compute relative mobility (Rf) - distance from top as fraction of total lane height
     */
    private double computeRf(Lane lane, AssistBand band, int imageHeight) {
        // Simple Rf calculation: distance from top / total distance
        // In a real system, this might use dye front detection
        return band.yApexPx / (double) imageHeight;
    }
    
    /**
     * Propagate band identification to other lanes using Rf matching
     */
    private List<AssistBand> propagateToOtherLanes(ImagePlus imp, Lane seedLane, AssistBand seedBand, double rfSeed) {
        List<AssistBand> foundBands = new ArrayList<>();
        
        for (Lane lane : lanes) {
            // Skip the seed lane (already have that band)
            if (lane == seedLane) continue;
            
            // Predict Y position in this lane based on Rf
            int yPredicted = (int) Math.round(rfSeed * imp.getHeight());
            
            // Search around prediction
            float[] profile = Profiles.verticalSum(imp, lane.xStart(), lane.xEnd());
            int searchWindow = Math.max(8, (int)(0.03 * profile.length)); // ±3% of lane height
            int y0 = Math.max(0, yPredicted - searchWindow);
            int y1 = Math.min(profile.length - 1, yPredicted + searchWindow);
            
            // Find best prominent peak in search window
            OptionalInt bestPeak = Peaks.bestProminent(profile, y0, y1, config.minProminence);
            if (bestPeak.isEmpty()) continue;
            
            int peak = bestPeak.getAsInt();
            int leftValley = Peaks.leftValley(profile, peak);
            int rightValley = Peaks.rightValley(profile, peak);
            
            // Create and quantify the band
            AssistBand band = Quant.integrateBand(imp, lane, leftValley, rightValley);
            
            // Quality checks
            double snr = band.snr;
            double widthRatio = band.heightPx() / (double) seedBand.heightPx();
            
            if (snr < config.minSnr || widthRatio < config.widthRatioRange[0] || widthRatio > config.widthRatioRange[1]) {
                continue; // Failed quality checks
            }
            
            // Calculate confidence based on SNR and width similarity
            double snrScore = Quant.snrToConfidence(snr);
            double widthScore = 1.0 - Math.abs(widthRatio - 1.0); // 1.0 = perfect match
            band.confidence = (snrScore + widthScore) / 2.0;
            
            band.flags.add("propagated");
            foundBands.add(band);
        }
        
        return foundBands;
    }
    
    /**
     * Display the assist result on the image
     */
    private void displayResult(ImagePlus imp, AssistResult result) {
        // Create overlay for all identified bands
        Overlay overlay = OverlayRenderer.createAssistedBandOverlay(result.getAllBands());
        
        // Highlight the seed band differently
        AssistBand seedBand = result.seedBand;
        ij.gui.Roi seedRoi = new ij.gui.Roi(seedBand.xStart, seedBand.yStart, 
                                           seedBand.widthPx(), seedBand.heightPx());
        seedRoi.setStrokeColor(Color.MAGENTA);
        seedRoi.setStrokeWidth(3.0);
        seedRoi.setName("SEED_BAND");
        overlay.add(seedRoi);
        
        // Add seed label
        ij.gui.TextRoi seedLabel = new ij.gui.TextRoi(seedBand.xStart + 2, seedBand.yStart - 25, 
                                                     "SEED", new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
        seedLabel.setStrokeColor(Color.MAGENTA);
        seedLabel.setFillColor(new Color(255, 255, 255, 240));
        overlay.add(seedLabel);
        
        // Apply overlay to image
        imp.setOverlay(overlay);
        imp.updateAndDraw();
    }
    
    // Configuration setters for fine-tuning
    public void setSearchWindow(int windowPx) { this.config.searchWindowPx = windowPx; }
    public void setMinProminence(float prominence) { this.config.minProminence = prominence; }
    public void setMinSnr(double snr) { this.config.minSnr = snr; }
    public void setWidthRatioRange(double min, double max) { 
        this.config.widthRatioRange[0] = min; 
        this.config.widthRatioRange[1] = max; 
    }
    public void setRfTolerance(double tolerance) { this.config.rfTolerance = tolerance; }
}