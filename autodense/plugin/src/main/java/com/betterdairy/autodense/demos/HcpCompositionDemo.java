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
 * Demo showcasing HCP (Host Cell Protein) composition analysis functionality.
 * Shows how to:
 * 1. Set up a synthetic gel with target protein and contaminants
 * 2. Analyze non-target signal per lane
 * 3. Identify top contaminant bands by molecular weight
 * 4. Display HCP percentage and contaminant profiles
 */
public class HcpCompositionDemo {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> runDemo());
    }
    
    public static void runDemo() {
        System.out.println("=== HCP Composition Demo ===");
        
        try {
            // Create synthetic gel with target protein and HCPs
            ImagePlus syntheticGel = createSyntheticHcpGel();
            
            // Initialize analysis components
            SessionStore sessionStore = new SessionStore();
            GelAnalysisTools gelTools = new GelAnalysisTools(sessionStore);
            
            // Store synthetic gel
            String imageHandle = sessionStore.putImage(syntheticGel);
            System.out.println("Created synthetic HCP gel: " + imageHandle);
            
            // Detect lanes first
            JSONObject laneParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("expected_lanes", 4)
                .put("sensitivity", 0.8);
            
            JSONObject laneResult = gelTools.detectLanes(laneParams);
            System.out.println("Lane detection result: " + laneResult.optString("message"));
            
            // Detect bands
            JSONObject bandParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("sensitivity", 0.6);
            
            JSONObject bandResult = gelTools.detectBands(bandParams);
            System.out.println("Band detection result: " + bandResult.optString("message"));
            
            // Create mock calibration (in real use, this would be from ladder analysis)
            Models.CalibrationModel mockCalibration = createMockCalibration();
            sessionStore.putAnalysis(imageHandle, mockCalibration, "calibration");
            System.out.println("Added mock MW calibration");
            
            // Analyze HCP composition for 50 kDa target protein
            System.out.println("\n--- HCP Analysis for 50 kDa Target Protein ---");
            JSONObject hcpParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("target_mw_kda", 50.0)
                .put("window_pct", 8.0)  // ±8% window (46-54 kDa)
                .put("top_n", 3);        // Show top 3 contaminants
            
            JSONObject hcpResult = gelTools.hcpSnapshot(hcpParams);
            displayHcpResults(hcpResult);
            
            // Analyze with tighter window for highly pure samples
            System.out.println("\n--- HCP Analysis with Tight Window (±5%) ---");
            JSONObject tightParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("target_mw_kda", 50.0)
                .put("window_pct", 5.0)  // ±5% window (47.5-52.5 kDa)
                .put("top_n", 5);        // Show top 5 contaminants
            
            JSONObject tightResult = gelTools.hcpSnapshot(tightParams);
            displayHcpResults(tightResult);
            
            // Show gel image
            syntheticGel.setTitle("HCP Composition Demo Gel");
            syntheticGel.show();
            
            // Display summary
            displayDemoSummary();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static ImagePlus createSyntheticHcpGel() {
        int width = 400;
        int height = 300;
        ByteProcessor processor = new ByteProcessor(width, height);
        
        // Fill with dark background
        processor.setValue(30);
        processor.fill();
        
        // Lane positions
        int[] laneX = {80, 150, 220, 290};
        int laneWidth = 40;
        
        // Create lanes with different HCP profiles
        for (int lane = 0; lane < 4; lane++) {
            int x = laneX[lane];
            
            // High MW contaminants (75 kDa, 65 kDa)
            if (lane >= 1) { // Present in lanes 2-4
                addBand(processor, x, 50, laneWidth, 6, 120 + lane * 10); // 75 kDa
                if (lane >= 2) {
                    addBand(processor, x, 70, laneWidth, 5, 100 + lane * 5); // 65 kDa
                }
            }
            
            // Target protein (50 kDa) - present in all lanes with varying purity
            int targetIntensity = 200 - lane * 30; // Decreasing purity
            addBand(processor, x, 120, laneWidth, 8, targetIntensity);
            
            // Low MW contaminants (40 kDa, 30 kDa, 25 kDa)
            if (lane >= 1) {
                addBand(processor, x, 140, laneWidth, 5, 90 + lane * 8); // 40 kDa
                if (lane >= 2) {
                    addBand(processor, x, 170, laneWidth, 4, 70 + lane * 5); // 30 kDa
                    if (lane >= 3) {
                        addBand(processor, x, 190, laneWidth, 3, 60 + lane * 3); // 25 kDa
                    }
                }
            }
            
            // Very low MW fragments (15 kDa) - degradation products
            if (lane == 3) {
                addBand(processor, x, 230, laneWidth, 3, 50);
            }
        }
        
        return new ImagePlus("Synthetic HCP Gel", processor);
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
    
    private static Models.CalibrationModel createMockCalibration() {
        // Mock calibration: log(MW) = a*y + b
        // Approximate values for typical SDS-PAGE gel
        double a = -0.02;  // Slope (negative because smaller MW migrates further)
        double b = 3.0;    // Intercept (log10 scale)
        double r2 = 0.98;  // Good correlation
        
        return new Models.CalibrationModel(a, b, r2);
    }
    
    private static void displayHcpResults(JSONObject result) {
        if (result.optBoolean("error", false)) {
            System.out.println("Error: " + result.optString("message"));
            return;
        }
        
        System.out.println("Analysis: " + result.optString("analysis"));
        System.out.println("Target MW: " + result.optDouble("target_mw_kda") + " kDa");
        System.out.println("Window: ±" + result.optDouble("window_pct") + "%");
        
        JSONArray lanes = result.optJSONArray("lanes");
        if (lanes != null) {
            for (int i = 0; i < lanes.length(); i++) {
                JSONObject lane = lanes.getJSONObject(i);
                int laneNum = lane.optInt("lane");
                double hcpPercent = lane.optDouble("hcp_percent");
                
                System.out.printf("  Lane %d: %.1f%% HCP content %s\n", 
                    laneNum, hcpPercent, 
                    hcpPercent < 1.0 ? "(Excellent)" : 
                    hcpPercent < 5.0 ? "(Good)" : 
                    hcpPercent < 10.0 ? "(Moderate)" : "(High HCP)");
                
                JSONArray contaminants = lane.optJSONArray("top_contaminants");
                if (contaminants != null && contaminants.length() > 0) {
                    System.out.println("    Top contaminants:");
                    for (int j = 0; j < contaminants.length(); j++) {
                        JSONObject cont = contaminants.getJSONObject(j);
                        int bandIdx = cont.optInt("band_idx");
                        double mw = cont.optDouble("mw_kda");
                        double relAreaPct = cont.optDouble("rel_area_pct");
                        double areaCorr = cont.optDouble("area_corr");
                        
                        System.out.printf("      Band %d: %.1f kDa, %.2f%% of total (area=%.0f)\n", 
                            bandIdx + 1, mw, relAreaPct, areaCorr);
                    }
                } else {
                    System.out.println("    No significant contaminants detected");
                }
            }
        }
    }
    
    private static void displayDemoSummary() {
        JFrame frame = new JFrame("HCP Composition Demo Summary");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        JTextArea textArea = new JTextArea(25, 65);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        String summary = """
            HCP COMPOSITION ANALYSIS DEMO SUMMARY
            ====================================
            
            This demo showcased HCP (Host Cell Protein) composition analysis:
            
            SYNTHETIC GEL DESIGN:
            • Lane 1: Pure target protein (50 kDa) - 0% HCP
            • Lane 2: Target + high MW contaminant (75 kDa) - Low HCP
            • Lane 3: Target + multiple contaminants (75, 65, 40, 30 kDa) - Moderate HCP
            • Lane 4: Target + all contaminants + fragments - High HCP
            
            ANALYSIS PERFORMED:
            1. Standard HCP analysis with ±8% MW window around 50 kDa target
            2. Tight HCP analysis with ±5% MW window for high purity samples
            
            KEY FEATURES DEMONSTRATED:
            • MW calibration requirement for accurate HCP analysis
            • Non-target signal quantification as percentage of total
            • Top contaminant identification sorted by intensity
            • Different purity profiles across lanes
            
            HCP PERCENTAGE INTERPRETATION:
            • <1%: Excellent purity (pharmaceutical grade)
            • 1-5%: Good purity (research grade)
            • 5-10%: Moderate purity (may need further purification)
            • >10%: High HCP content (significant contamination)
            
            APPLICATIONS:
            • Biopharmaceutical quality control
            • Protein purification optimization
            • Batch-to-batch consistency monitoring
            • Regulatory compliance documentation
            • Process development and validation
            
            TYPICAL WORKFLOW:
            1. Run SDS-PAGE gel with samples and MW ladder
            2. Calibrate molecular weights using ladder
            3. Detect lanes and bands automatically
            4. Run hcp_snapshot tool with target protein MW
            5. Review HCP percentage and contaminant profile
            6. Make purification decisions based on results
            
            The hcp_snapshot tool provides quantitative assessment of protein
            purity by identifying and measuring non-target proteins that fall
            outside the specified molecular weight window of the target protein.
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