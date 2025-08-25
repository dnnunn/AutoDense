package com.betterdairy.autodense.demos.gelanalysis;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.model.Models;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
import java.awt.*;

/**
 * Demo showcasing dephosphorylation shift analysis functionality.
 * Shows how to:
 * 1. Set up a synthetic gel with control and phosphatase-treated samples
 * 2. Compare molecular weight shifts after phosphatase treatment
 * 3. Analyze sharpening effects from reduced charge heterogeneity
 * 4. Track area changes indicating band consolidation
 */
public class DephosphorylationShiftDemo {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> runDemo());
    }
    
    public static void runDemo() {
        System.out.println("=== Dephosphorylation Shift Analysis Demo ===");
        
        try {
            // Create synthetic gel with control and treated samples
            ImagePlus syntheticGel = createSyntheticDephosphorylationGel();
            
            // Initialize analysis components
            SessionStore sessionStore = new SessionStore();
            GelAnalysisTools gelTools = new GelAnalysisTools(sessionStore);
            
            // Store synthetic gel
            String imageHandle = sessionStore.putImage(syntheticGel);
            System.out.println("Created synthetic dephosphorylation gel: " + imageHandle);
            
            // Detect lanes first
            JSONObject laneParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("expected_lanes", 6)
                .put("sensitivity", 0.8);
            
            JSONObject laneResult = gelTools.detectLanes(laneParams);
            System.out.println("Lane detection result: " + laneResult.optString("message"));
            
            // Detect bands
            JSONObject bandParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("sensitivity", 0.6);
            
            JSONObject bandResult = gelTools.detectBands(bandParams);
            System.out.println("Band detection result: " + bandResult.optString("message"));
            
            // Create mock calibration for dephosphorylation analysis
            Models.CalibrationModel mockCalibration = createMockCalibration();
            sessionStore.putAnalysis(imageHandle, mockCalibration, "calibration");
            System.out.println("Added mock MW calibration");
            
            // Compare treatments for high MW protein (60 kDa target)
            System.out.println("\n--- Dephosphorylation Analysis: 60 kDa Protein ---");
            JSONObject highMwParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("control_lanes", new JSONArray().put(1).put(2))    // Lanes 1-2: control
                .put("treated_lanes", new JSONArray().put(3).put(4))    // Lanes 3-4: +phosphatase
                .put("mw_window", new JSONArray().put(55.0).put(65.0)); // 55-65 kDa window
            
            JSONObject highMwResult = gelTools.compareTreatments(highMwParams);
            displayTreatmentResults(highMwResult, "High MW Protein (60 kDa)");
            
            // Compare treatments for medium MW protein (40 kDa target)
            System.out.println("\n--- Dephosphorylation Analysis: 40 kDa Protein ---");
            JSONObject medMwParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("control_lanes", new JSONArray().put(1).put(2))    // Lanes 1-2: control
                .put("treated_lanes", new JSONArray().put(3).put(4))    // Lanes 3-4: +phosphatase
                .put("mw_window", new JSONArray().put(35.0).put(45.0)); // 35-45 kDa window
            
            JSONObject medMwResult = gelTools.compareTreatments(medMwParams);
            displayTreatmentResults(medMwResult, "Medium MW Protein (40 kDa)");
            
            // Compare different treatment conditions (lanes 5-6: different phosphatase)
            System.out.println("\n--- Alternative Phosphatase Treatment ---");
            JSONObject altParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("control_lanes", new JSONArray().put(1).put(2))    // Lanes 1-2: control
                .put("treated_lanes", new JSONArray().put(5).put(6))    // Lanes 5-6: alt treatment
                .put("mw_window", new JSONArray().put(55.0).put(65.0)); // 55-65 kDa window
            
            JSONObject altResult = gelTools.compareTreatments(altParams);
            displayTreatmentResults(altResult, "Alternative Treatment");
            
            // Show gel image
            syntheticGel.setTitle("Dephosphorylation Shift Analysis Demo Gel");
            syntheticGel.show();
            
            // Display summary
            displayDemoSummary();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static ImagePlus createSyntheticDephosphorylationGel() {
        int width = 480;
        int height = 300;
        ByteProcessor processor = new ByteProcessor(width, height);
        
        // Fill with dark background
        processor.setValue(30);
        processor.fill();
        
        // Lane positions for 6 lanes
        int[] laneX = {60, 120, 180, 240, 300, 360};
        int laneWidth = 40;
        
        // Create lanes with dephosphorylation patterns
        for (int lane = 0; lane < 6; lane++) {
            int x = laneX[lane];
            
            if (lane < 2) {
                // Control lanes (1-2): Phosphorylated proteins with charge heterogeneity
                
                // High MW protein (60 kDa) - multiple charge states
                addBand(processor, x, 80, laneWidth, 4, 140);  // +3 phosphates
                addBand(processor, x, 85, laneWidth, 5, 160);  // +2 phosphates  
                addBand(processor, x, 90, laneWidth, 6, 180);  // +1 phosphate
                addBroadBand(processor, x, 80, 90, laneWidth, 120); // Smearing
                
                // Medium MW protein (40 kDa) - phosphorylated forms
                addBand(processor, x, 140, laneWidth, 4, 130);
                addBand(processor, x, 145, laneWidth, 5, 150);
                addBroadBand(processor, x, 140, 145, laneWidth, 100);
                
                // Low MW protein (25 kDa) - less affected
                addBand(processor, x, 200, laneWidth, 5, 160);
                
            } else if (lane < 4) {
                // Phosphatase-treated lanes (3-4): Dephosphorylated, sharper bands
                
                // High MW protein - collapsed to single sharp band, MW shift
                addBand(processor, x, 95, laneWidth, 7, 220); // Single dephosphorylated form
                
                // Medium MW protein - sharper, MW shift
                addBand(processor, x, 150, laneWidth, 6, 200);
                
                // Low MW protein - unchanged
                addBand(processor, x, 200, laneWidth, 5, 160);
                
            } else {
                // Alternative treatment lanes (5-6): Partial dephosphorylation
                
                // High MW protein - intermediate between control and full treatment
                addBand(processor, x, 87, laneWidth, 5, 150);  // Partially dephosphorylated
                addBand(processor, x, 93, laneWidth, 6, 180);  // More dephosphorylated
                addBroadBand(processor, x, 87, 93, laneWidth, 80);
                
                // Medium MW protein - similar pattern
                addBand(processor, x, 147, laneWidth, 5, 140);
                
                // Low MW protein - unchanged
                addBand(processor, x, 200, laneWidth, 5, 160);
            }
        }
        
        return new ImagePlus("Synthetic Dephosphorylation Gel", processor);
    }
    
    private static void addBand(ByteProcessor processor, int centerX, int y, int width, int height, int intensity) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -width/2; dx <= width/2; dx++) {
                int x = centerX + dx;
                if (x >= 0 && x < processor.getWidth() && y + dy >= 0 && y + dy < processor.getHeight()) {
                    // Gaussian-like profile for realistic band shape
                    double gaussian = Math.exp(-0.5 * (dx * dx) / ((width/4.0) * (width/4.0)));
                    int value = (int) (intensity * gaussian);
                    processor.putPixel(x, y + dy, Math.max(processor.getPixel(x, y + dy), value));
                }
            }
        }
    }
    
    private static void addBroadBand(ByteProcessor processor, int centerX, int yStart, int yEnd, int width, int intensity) {
        for (int y = yStart; y <= yEnd; y++) {
            for (int dx = -width/2; dx <= width/2; dx++) {
                int x = centerX + dx;
                if (x >= 0 && x < processor.getWidth() && y >= 0 && y < processor.getHeight()) {
                    // Gradual intensity across Y range for smearing effect
                    double yFactor = 1.0 - Math.abs(y - (yStart + yEnd)/2.0) / ((yEnd - yStart + 1)/2.0);
                    double gaussian = Math.exp(-0.5 * (dx * dx) / ((width/4.0) * (width/4.0)));
                    int value = (int) (intensity * gaussian * yFactor * 0.6); // Reduced for background smear
                    processor.putPixel(x, y, Math.max(processor.getPixel(x, y), value));
                }
            }
        }
    }
    
    private static Models.CalibrationModel createMockCalibration() {
        // Mock calibration for dephosphorylation gel
        double a = -0.025;  // Slope (slightly steeper for better separation)
        double b = 3.1;     // Intercept
        double r2 = 0.97;   // Good correlation
        
        return new Models.CalibrationModel(a, b, r2);
    }
    
    private static void displayTreatmentResults(JSONObject result, String proteinName) {
        if (result.optBoolean("error", false)) {
            System.out.println("Error: " + result.optString("message"));
            return;
        }
        
        System.out.println("Analysis: " + result.optString("analysis"));
        
        JSONArray pairs = result.optJSONArray("pairs");
        if (pairs != null && pairs.length() > 0) {
            System.out.println("Treatment effects for " + proteinName + ":");
            
            double totalDeltaMw = 0;
            double totalDeltaSharpness = 0;
            double totalAreaRatio = 0;
            int count = 0;
            
            for (int i = 0; i < pairs.length(); i++) {
                JSONObject pair = pairs.getJSONObject(i);
                int controlLane = pair.optInt("control_lane");
                int treatedLane = pair.optInt("treated_lane");
                double mwControl = pair.optDouble("mw_control");
                double mwTreated = pair.optDouble("mw_treated");
                double deltaMw = pair.optDouble("delta_mw");
                double sharpnessControl = pair.optDouble("sharpness_control");
                double sharpnessTreated = pair.optDouble("sharpness_treated");
                double deltaSharpness = pair.optDouble("delta_sharpness");
                double areaRatio = pair.optDouble("area_ratio");
                
                System.out.printf("  Lane %d→%d: %.1f→%.1f kDa (Δ=%.2f), Sharp: %.2f→%.2f (Δ=%.2f), Area×%.2f\n",
                    controlLane, treatedLane, mwControl, mwTreated, deltaMw, 
                    sharpnessControl, sharpnessTreated, deltaSharpness, areaRatio);
                
                totalDeltaMw += Math.abs(deltaMw);
                totalDeltaSharpness += deltaSharpness;
                totalAreaRatio += areaRatio;
                count++;
            }
            
            if (count > 0) {
                double avgDeltaMw = totalDeltaMw / count;
                double avgDeltaSharpness = totalDeltaSharpness / count;
                double avgAreaRatio = totalAreaRatio / count;
                
                System.out.printf("  Average: |ΔMW|=%.2f kDa, ΔSharpness=%.2f, Area×%.2f\n", 
                    avgDeltaMw, avgDeltaSharpness, avgAreaRatio);
                
                // Interpretation
                String mwInterpretation = avgDeltaMw > 2.0 ? "Significant MW shift" : 
                                         avgDeltaMw > 0.5 ? "Moderate MW shift" : "Minimal MW shift";
                String sharpnessInterpretation = avgDeltaSharpness > 0.1 ? "Band sharpening" : 
                                               avgDeltaSharpness < -0.1 ? "Band broadening" : "No sharpness change";
                String areaInterpretation = avgAreaRatio > 1.2 ? "Area increase" : 
                                          avgAreaRatio < 0.8 ? "Area decrease" : "Area stable";
                
                System.out.printf("  Effects: %s, %s, %s\n", 
                    mwInterpretation, sharpnessInterpretation, areaInterpretation);
            }
        } else {
            System.out.println("No matching bands found for comparison");
        }
    }
    
    private static void displayDemoSummary() {
        JFrame frame = new JFrame("Dephosphorylation Shift Analysis Demo Summary");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        JTextArea textArea = new JTextArea(30, 70);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        String summary = """
            DEPHOSPHORYLATION SHIFT ANALYSIS DEMO SUMMARY
            =============================================
            
            This demo showcased treatment comparison analysis for dephosphorylation studies:
            
            SYNTHETIC GEL DESIGN:
            • Lanes 1-2: Control samples (phosphorylated proteins)
              - Multiple charge states creating smeared, broad bands
              - Higher apparent MW due to phosphate groups
            • Lanes 3-4: Phosphatase-treated samples
              - Collapsed to single, sharp bands
              - Lower MW after phosphate removal
            • Lanes 5-6: Alternative treatment (partial dephosphorylation)
              - Intermediate between control and full treatment
            
            ANALYSIS PERFORMED:
            1. High MW protein analysis (55-65 kDa window)
            2. Medium MW protein analysis (35-45 kDa window)
            3. Alternative treatment comparison
            
            KEY FEATURES DEMONSTRATED:
            • 1D cross-correlation alignment between control and treated lanes
            • Molecular weight shift quantification (ΔMW)
            • Band sharpness change measurement
            • Area ratio tracking for signal consolidation
            • Nearest-neighbor band matching by MW
            
            TYPICAL DEPHOSPHORYLATION EFFECTS:
            • MW Shift: -0.5 to -3.0 kDa (loss of phosphate groups)
            • Sharpness: Increase due to reduced charge heterogeneity
            • Area: Often increases due to band consolidation
            • Migration: Faster due to reduced negative charge
            
            APPLICATIONS:
            • Post-translational modification studies
            • Phosphatase activity assays
            • Protein purification optimization
            • Kinase/phosphatase screening
            • Signaling pathway analysis
            • Quality control of recombinant proteins
            
            DATA INTERPRETATION:
            • |ΔMW| > 2 kDa: Significant dephosphorylation
            • ΔSharpness > 0.1: Successful charge homogenization
            • Area ratio > 1.2: Band consolidation occurred
            
            EXPERIMENTAL CONSIDERATIONS:
            • Use identical protein loads between control/treated
            • Include phosphatase inhibitors in controls
            • Consider time-course experiments
            • Validate with phospho-specific antibodies
            • Account for potential protein degradation
            
            TYPICAL WORKFLOW:
            1. Run SDS-PAGE with control and treated samples
            2. Detect lanes, bands, and calibrate MW
            3. Define MW window around target protein(s)
            4. Run compare_treatments tool
            5. Analyze MW shifts, sharpening, and area changes
            6. Interpret results in biological context
            
            The compare_treatments tool provides quantitative assessment of
            treatment effects by aligning lanes and comparing matched bands
            within specified molecular weight windows.
            """;
        
        textArea.setText(summary);
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane, BorderLayout.CENTER);
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> frame.dispose());
        frame.add(closeButton, BorderLayout.SOUTH);
        
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}