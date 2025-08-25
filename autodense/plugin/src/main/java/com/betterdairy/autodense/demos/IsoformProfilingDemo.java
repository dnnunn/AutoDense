package com.betterdairy.autodense.demos.gelanalysis;

import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
import java.awt.*;

/**
 * Demo showcasing isoform profiling functionality for analyzing protein variants
 * within molecular weight windows. Shows how to:
 * 1. Set up a synthetic gel with multiple isoforms
 * 2. Profile isoforms within specific MW ranges
 * 3. Calculate sharpness and smear metrics
 * 4. Display results with quality indicators
 */
public class IsoformProfilingDemo {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> runDemo());
    }
    
    public static void runDemo() {
        System.out.println("=== Isoform Profiling Demo ===");
        
        try {
            // Create synthetic gel image with isoforms
            ImagePlus syntheticGel = createSyntheticIsoformGel();
            
            // Initialize analysis components
            SessionStore sessionStore = new SessionStore();
            GelAnalysisTools gelTools = new GelAnalysisTools(sessionStore);
            
            // Store synthetic gel
            String imageHandle = sessionStore.putImage(syntheticGel);
            System.out.println("Created synthetic gel: " + imageHandle);
            
            // Detect lanes first
            JSONObject laneParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("expected_lanes", 4)
                .put("sensitivity", 0.8);
            
            JSONObject laneResult = gelTools.detectLanes(laneParams);
            System.out.println("Lane detection result: " + laneResult.optString("message"));
            
            // Profile isoforms around 50 kDa protein
            System.out.println("\n--- Profiling isoforms around 50 kDa ---");
            JSONObject isoformParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("target_mw_kda", 50.0)
                .put("window_pct", 20.0)  // ±20% window (40-60 kDa)
                .put("lanes", "all");
            
            JSONObject isoformResult = gelTools.profileIsoforms(isoformParams);
            displayIsoformResults(isoformResult);
            
            // Profile isoforms around 25 kDa protein (tighter window)
            System.out.println("\n--- Profiling isoforms around 25 kDa (tight window) ---");
            JSONObject tightParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("target_mw_kda", 25.0)
                .put("window_pct", 10.0)  // ±10% window (22.5-27.5 kDa)
                .put("lanes", new JSONArray().put(2).put(3).put(4)); // Specific lanes only
            
            JSONObject tightResult = gelTools.profileIsoforms(tightParams);
            displayIsoformResults(tightResult);
            
            // Show gel image
            syntheticGel.setTitle("Isoform Profiling Demo Gel");
            syntheticGel.show();
            
            // Display summary
            displayDemoSummary();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static ImagePlus createSyntheticIsoformGel() {
        int width = 400;
        int height = 300;
        ByteProcessor processor = new ByteProcessor(width, height);
        
        // Fill with dark background (typical gel appearance)
        processor.setValue(30);
        processor.fill();
        
        // Lane positions
        int[] laneX = {80, 150, 220, 290};
        int laneWidth = 40;
        
        // Create protein bands with isoforms
        for (int lane = 0; lane < 4; lane++) {
            int x = laneX[lane];
            
            // High MW marker (75 kDa) - single band
            addBand(processor, x, 60, laneWidth, 8, 180);
            
            // Target protein (50 kDa) with isoforms
            // Lane 1: Single sharp isoform
            if (lane == 0) {
                addBand(processor, x, 120, laneWidth, 6, 200);
            }
            // Lane 2: Two close isoforms
            else if (lane == 1) {
                addBand(processor, x, 118, laneWidth, 4, 160);
                addBand(processor, x, 122, laneWidth, 4, 140);
            }
            // Lane 3: Three isoforms with smear
            else if (lane == 2) {
                addBand(processor, x, 116, laneWidth, 3, 120);
                addBand(processor, x, 120, laneWidth, 4, 150);
                addBand(processor, x, 124, laneWidth, 3, 110);
                // Add smear between bands
                addSmear(processor, x, 116, 124, laneWidth, 80);
            }
            // Lane 4: Broad smeared band
            else {
                addBand(processor, x, 120, laneWidth, 12, 130);
            }
            
            // Lower MW protein (25 kDa) with variants
            if (lane == 0) {
                addBand(processor, x, 200, laneWidth, 5, 170);
            } else if (lane == 1) {
                addBand(processor, x, 198, laneWidth, 3, 140);
                addBand(processor, x, 202, laneWidth, 4, 160);
            } else {
                addBand(processor, x, 200, laneWidth, 8, 120);
            }
            
            // Low MW marker (15 kDa)
            addBand(processor, x, 240, laneWidth, 4, 150);
        }
        
        return new ImagePlus("Synthetic Isoform Gel", processor);
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
    
    private static void addSmear(ByteProcessor processor, int centerX, int yStart, int yEnd, int width, int baseIntensity) {
        for (int y = yStart; y <= yEnd; y++) {
            for (int dx = -width/2; dx <= width/2; dx++) {
                int x = centerX + dx;
                if (x >= 0 && x < processor.getWidth() && y >= 0 && y < processor.getHeight()) {
                    int value = baseIntensity + (int) (20 * Math.random()); // Random smear intensity
                    processor.putPixel(x, y, Math.max(processor.getPixel(x, y), value));
                }
            }
        }
    }
    
    private static void displayIsoformResults(JSONObject result) {
        if (result.optBoolean("error", false)) {
            System.out.println("Error: " + result.optString("message"));
            return;
        }
        
        System.out.println("Analysis: " + result.optString("analysis"));
        System.out.println("Target MW: " + result.optDouble("target_mw_kda") + " kDa");
        System.out.println("Window: " + result.optString("mw_window"));
        
        JSONArray profiles = result.optJSONArray("isoform_profiles");
        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                int laneIndex = profile.optInt("lane_index");
                int bandsFound = profile.optInt("bands_in_window");
                double avgSharpness = profile.optDouble("average_sharpness");
                double smearFraction = profile.optDouble("smear_fraction");
                
                System.out.printf("  Lane %d: %d bands, sharpness=%.2f, smear=%.1f%%\n", 
                    laneIndex, bandsFound, avgSharpness, smearFraction * 100);
                
                JSONArray bands = profile.optJSONArray("bands");
                if (bands != null) {
                    for (int j = 0; j < bands.length(); j++) {
                        JSONObject band = bands.getJSONObject(j);
                        double y = band.optDouble("y_position");
                        double sharpness = band.optDouble("sharpness");
                        System.out.printf("    Band %d: Y=%.1f, sharpness=%.2f\n", 
                            j + 1, y, sharpness);
                    }
                }
            }
        }
        
        // Quality assessment
        double avgSmear = result.optDouble("overall_smear_fraction", 0.0);
        double avgSharpness = result.optDouble("overall_average_sharpness", 0.0);
        
        System.out.println("\nQuality Assessment:");
        System.out.printf("Overall smear: %.1f%% %s\n", avgSmear * 100, 
            avgSmear < 0.2 ? "(Good)" : avgSmear < 0.4 ? "(Moderate)" : "(High)");
        System.out.printf("Overall sharpness: %.2f %s\n", avgSharpness,
            avgSharpness > 0.7 ? "(Sharp)" : avgSharpness > 0.4 ? "(Moderate)" : "(Broad)");
    }
    
    private static void displayDemoSummary() {
        JFrame frame = new JFrame("Isoform Profiling Demo Summary");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        JTextArea textArea = new JTextArea(20, 60);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        String summary = """
            ISOFORM PROFILING DEMO SUMMARY
            ==============================
            
            This demo showcased the isoform profiling functionality:
            
            SYNTHETIC GEL DESIGN:
            • Lane 1: Single sharp isoforms (reference)
            • Lane 2: Multiple distinct isoforms
            • Lane 3: Isoforms with background smear
            • Lane 4: Broad, poorly resolved bands
            
            ANALYSIS PERFORMED:
            1. 50 kDa target with ±20% window (40-60 kDa)
            2. 25 kDa target with ±10% window (22.5-27.5 kDa)
            
            METRICS CALCULATED:
            • Sharpness: Based on Full Width at Half Maximum (FWHM)
            • Smear fraction: Percentage of diffuse background signal
            • Band count: Number of resolved peaks in MW window
            
            APPLICATIONS:
            • Protein purification quality assessment
            • Post-translational modification analysis
            • Proteolytic cleavage pattern analysis
            • Expression optimization studies
            
            The profile_isoforms tool helps quantify protein heterogeneity
            and assess purification success for target molecular weights.
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