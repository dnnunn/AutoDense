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
 * Demo showcasing protease digest kinetics analysis functionality.
 * Shows how to:
 * 1. Set up a synthetic gel with time-series digest samples
 * 2. Track parent protein band decay over time
 * 3. Analyze fragment emergence and accumulation
 * 4. Calculate digest rate constants and half-life
 */
public class ProteaseDigestKineticsDemo {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> runDemo());
    }
    
    public static void runDemo() {
        System.out.println("=== Protease Digest Kinetics Demo ===");
        
        try {
            // Create synthetic time-series digest gel
            ImagePlus syntheticGel = createSyntheticDigestGel();
            
            // Initialize analysis components
            SessionStore sessionStore = new SessionStore();
            GelAnalysisTools gelTools = new GelAnalysisTools(sessionStore);
            
            // Store synthetic gel
            String imageHandle = sessionStore.putImage(syntheticGel);
            System.out.println("Created synthetic digest kinetics gel: " + imageHandle);
            
            // Detect lanes first (8 lanes for time series)
            JSONObject laneParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("expected_lanes", 8)
                .put("sensitivity", 0.8);
            
            JSONObject laneResult = gelTools.detectLanes(laneParams);
            System.out.println("Lane detection result: " + laneResult.optString("message"));
            
            // Detect bands
            JSONObject bandParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("sensitivity", 0.5);
            
            JSONObject bandResult = gelTools.detectBands(bandParams);
            System.out.println("Band detection result: " + bandResult.optString("message"));
            
            // Create mock calibration for digest analysis
            Models.CalibrationModel mockCalibration = createMockCalibration();
            sessionStore.putAnalysis(imageHandle, mockCalibration, "calibration");
            System.out.println("Added mock MW calibration");
            
            // Analyze digest kinetics for 60 kDa parent protein
            System.out.println("\n--- Trypsin Digest Kinetics: 60 kDa Protein ---");
            JSONObject digestParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("lanes", new JSONArray().put(1).put(2).put(3).put(4).put(5).put(6).put(7).put(8))
                .put("times_min", new JSONArray().put(0).put(5).put(10).put(15).put(20).put(30).put(45).put(60))
                .put("parent_mw_kda", 60.0)
                .put("window_pct", 12.0);
            
            JSONObject digestResult = gelTools.digestKinetics(digestParams);
            displayDigestResults(digestResult, "Trypsin Digest");
            
            // Analyze different parent protein (35 kDa)
            System.out.println("\n--- Pepsin Digest Kinetics: 35 kDa Protein ---");
            JSONObject pepsinParams = new JSONObject()
                .put("image_handle", imageHandle)
                .put("lanes", new JSONArray().put(1).put(2).put(3).put(4).put(5).put(6).put(7).put(8))
                .put("times_min", new JSONArray().put(0).put(5).put(10).put(15).put(20).put(30).put(45).put(60))
                .put("parent_mw_kda", 35.0)
                .put("window_pct", 15.0);
            
            JSONObject pepsinResult = gelTools.digestKinetics(pepsinParams);
            displayDigestResults(pepsinResult, "Pepsin Digest");
            
            // Show gel image
            syntheticGel.setTitle("Protease Digest Kinetics Demo Gel");
            syntheticGel.show();
            
            // Display summary
            displayDemoSummary();
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static ImagePlus createSyntheticDigestGel() {
        int width = 640;
        int height = 300;
        ByteProcessor processor = new ByteProcessor(width, height);
        
        // Fill with dark background
        processor.setValue(30);
        processor.fill();
        
        // Lane positions for 8 time points
        int[] laneX = {60, 120, 180, 240, 300, 360, 420, 480};
        int laneWidth = 40;
        double[] times = {0, 5, 10, 15, 20, 30, 45, 60}; // minutes
        
        // Create time-series digest patterns
        for (int lane = 0; lane < 8; lane++) {
            int x = laneX[lane];
            double t = times[lane];
            
            // Parent protein (60 kDa) - exponential decay
            double parentDecay = Math.exp(-0.03 * t); // k = 0.03/min, t½ ≈ 23 min
            int parentIntensity = (int) (200 * parentDecay);
            if (parentIntensity > 20) {
                addBand(processor, x, 80, laneWidth, 6, parentIntensity);
            }
            
            // Secondary parent (35 kDa) - slower decay
            double parent2Decay = Math.exp(-0.015 * t); // k = 0.015/min, t½ ≈ 46 min
            int parent2Intensity = (int) (180 * parent2Decay);
            if (parent2Intensity > 15) {
                addBand(processor, x, 160, laneWidth, 5, parent2Intensity);
            }
            
            // Major fragment 1 (45 kDa) - early emergence, then plateau
            double frag1 = 150 * (1 - Math.exp(-0.05 * t)) * Math.exp(-0.01 * t);
            if (frag1 > 10) {
                addBand(processor, x, 110, laneWidth, 5, (int) frag1);
            }
            
            // Major fragment 2 (30 kDa) - steady accumulation
            double frag2 = 120 * (1 - Math.exp(-0.02 * t));
            if (frag2 > 8) {
                addBand(processor, x, 180, laneWidth, 4, (int) frag2);
            }
            
            // Minor fragment 3 (22 kDa) - late emergence
            if (t > 10) {
                double frag3 = 80 * (1 - Math.exp(-0.03 * (t - 10)));
                if (frag3 > 5) {
                    addBand(processor, x, 210, laneWidth, 3, (int) frag3);
                }
            }
            
            // Small fragments (15 kDa) - continuous accumulation
            if (t > 5) {
                double smallFrags = 60 * (1 - Math.exp(-0.015 * (t - 5)));
                if (smallFrags > 5) {
                    addBand(processor, x, 240, laneWidth, 3, (int) smallFrags);
                }
            }
            
            // Very small fragments (8 kDa) - late stage
            if (t > 20) {
                double verySmallFrags = 40 * (1 - Math.exp(-0.02 * (t - 20)));
                if (verySmallFrags > 3) {
                    addBand(processor, x, 270, laneWidth, 2, (int) verySmallFrags);
                }
            }
            
            // Add some background digestion noise for realism
            if (t > 0) {
                addDigestionBackground(processor, x, laneWidth, t);
            }
        }
        
        return new ImagePlus("Synthetic Digest Kinetics Gel", processor);
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
    
    private static void addDigestionBackground(ByteProcessor processor, int centerX, int laneWidth, double time) {
        // Add subtle background smear representing partial digestion products
        int backgroundIntensity = (int) (20 + 15 * Math.log(1 + time / 10.0));
        
        for (int y = 90; y < 260; y += 3) {
            for (int dx = -laneWidth/2; dx <= laneWidth/2; dx++) {
                int x = centerX + dx;
                if (x >= 0 && x < processor.getWidth() && y >= 0 && y < processor.getHeight()) {
                    int noise = (int) (backgroundIntensity * Math.random() * 0.3);
                    processor.putPixel(x, y, Math.max(processor.getPixel(x, y), noise));
                }
            }
        }
    }
    
    private static Models.CalibrationModel createMockCalibration() {
        // Mock calibration for digest kinetics gel
        double a = -0.023;  // Slope
        double b = 2.95;    // Intercept
        double r2 = 0.96;   // Good correlation
        
        return new Models.CalibrationModel(a, b, r2);
    }
    
    private static void displayDigestResults(JSONObject result, String digestType) {
        if (result.optBoolean("error", false)) {
            System.out.println("Error: " + result.optString("message"));
            return;
        }
        
        System.out.println("Analysis: " + result.optString("analysis"));
        
        double parentMw = result.optDouble("parent_mw_kda");
        double k = result.optDouble("k_per_min");
        double tHalf = result.optDouble("t_half_min");
        double r2 = result.optDouble("r_squared");
        
        System.out.printf("Parent protein: %.1f kDa\n", parentMw);
        System.out.printf("Rate constant: %.4f min⁻¹\n", k);
        System.out.printf("Half-life: %.1f minutes\n", tHalf);
        System.out.printf("Fit quality: R² = %.3f %s\n", r2, 
            r2 > 0.9 ? "(Excellent)" : r2 > 0.7 ? "(Good)" : "(Poor)");
        
        JSONArray timeSeries = result.optJSONArray("time_series");
        if (timeSeries != null && timeSeries.length() > 0) {
            System.out.println("Time course data:");
            for (int i = 0; i < timeSeries.length(); i++) {
                JSONObject point = timeSeries.getJSONObject(i);
                double t = point.optDouble("t_min");
                double area = point.optDouble("parent_area");
                boolean present = point.optBoolean("parent_present");
                int lane = point.optInt("lane");
                
                System.out.printf("  t=%.0f min (Lane %d): Area=%.0f %s\n", 
                    t, lane, area, present ? "✓" : "✗");
            }
        }
        
        JSONArray fragments = result.optJSONArray("fragments");
        if (fragments != null && fragments.length() > 0) {
            System.out.println("Emerging fragments:");
            for (int i = 0; i < fragments.length(); i++) {
                JSONObject frag = fragments.getJSONObject(i);
                double mw = frag.optDouble("mw_kda");
                double slope = frag.optDouble("slope_area_per_min");
                double initialArea = frag.optDouble("initial_area");
                double finalArea = frag.optDouble("final_area");
                double foldIncrease = (initialArea > 0) ? finalArea / initialArea : finalArea / 1.0;
                
                System.out.printf("  %.1f kDa: +%.1f area/min, %.1f→%.1f (%.1fx increase)\n", 
                    mw, slope, initialArea, finalArea, foldIncrease);
            }
        } else {
            System.out.println("No significant fragment accumulation detected");
        }
        
        // Interpretation
        String digestRate = k > 0.05 ? "Fast digestion" : 
                           k > 0.02 ? "Moderate digestion" : 
                           k > 0.005 ? "Slow digestion" : "Very slow digestion";
        
        String halfLifeCategory = tHalf < 15 ? "Rapid turnover" : 
                                 tHalf < 30 ? "Moderate stability" : 
                                 tHalf < 60 ? "Good stability" : "High stability";
        
        System.out.printf("Interpretation: %s, %s\n", digestRate, halfLifeCategory);
    }
    
    private static void displayDemoSummary() {
        JFrame frame = new JFrame("Protease Digest Kinetics Demo Summary");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        JTextArea textArea = new JTextArea(32, 75);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        String summary = """
            PROTEASE DIGEST KINETICS ANALYSIS DEMO SUMMARY
            ==============================================
            
            This demo showcased time-series kinetic analysis for protease digestion studies:
            
            SYNTHETIC GEL DESIGN:
            • 8 lanes representing time points: 0, 5, 10, 15, 20, 30, 45, 60 minutes
            • Parent proteins: 60 kDa (fast digestion) and 35 kDa (slow digestion)
            • Fragment emergence: 45, 30, 22, 15, 8 kDa fragments
            • Realistic kinetic patterns: exponential decay for parent, accumulation for fragments
            • Background digestion noise for authentic appearance
            
            ANALYSIS PERFORMED:
            1. 60 kDa parent protein kinetics (k ≈ 0.03/min, t½ ≈ 23 min)
            2. 35 kDa parent protein kinetics (k ≈ 0.015/min, t½ ≈ 46 min)
            3. Fragment emergence tracking and accumulation rates
            
            KEY FEATURES DEMONSTRATED:
            • Parent band tracking by molecular weight matching
            • Exponential decay fitting: ln(Area) = ln(A₀) - k×t
            • Rate constant (k) and half-life (t½) calculation
            • Fragment emergence analysis with slope calculation
            • R-squared goodness-of-fit assessment
            • Time-series data visualization
            
            KINETIC PARAMETERS:
            • Rate constant (k): First-order decay rate (min⁻¹)
            • Half-life (t½): Time for 50% parent consumption = ln(2)/k
            • R²: Correlation coefficient for exponential fit quality
            • Fragment slopes: Area increase rate (area units/min)
            
            TYPICAL DIGEST KINETICS:
            • Fast proteases (trypsin, pepsin): k > 0.05/min, t½ < 15 min
            • Moderate proteases (chymotrypsin): k = 0.02-0.05/min, t½ = 15-30 min
            • Slow proteases (elastase): k = 0.005-0.02/min, t½ = 30-120 min
            • Very slow conditions: k < 0.005/min, t½ > 2 hours
            
            APPLICATIONS:
            • Protease specificity studies
            • Enzyme kinetics characterization
            • Protein stability assessment
            • Digestion optimization for mass spectrometry
            • Quality control of proteolytic processes
            • Comparative enzyme screening
            • Inhibitor efficacy testing
            
            DATA INTERPRETATION:
            • High R² (>0.9): Good exponential fit, simple kinetics
            • Low R² (<0.7): Complex kinetics, multiple phases, or experimental issues
            • Fast fragments: Early cleavage sites, accessible regions
            • Slow fragments: Protected regions, secondary cleavages
            • Accumulating fragments: Stable end products
            
            EXPERIMENTAL CONSIDERATIONS:
            • Use identical protein concentrations across time points
            • Include protease controls and blanks
            • Account for continued digestion during sample preparation
            • Consider temperature and pH effects on kinetics
            • Validate fragment identity with mass spectrometry
            • Use protease inhibitors to stop reactions
            
            TYPICAL WORKFLOW:
            1. Design time-series experiment with appropriate intervals
            2. Run SDS-PAGE gel with all time points
            3. Detect lanes, bands, and calibrate molecular weights
            4. Run digest_kinetics tool with parent MW and time data
            5. Analyze kinetic parameters and fragment emergence
            6. Interpret results in context of protein structure/function
            
            The digest_kinetics tool provides quantitative analysis of proteolytic
            processes by tracking parent protein decay and fragment accumulation
            over time, fitting exponential models to extract kinetic parameters.
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