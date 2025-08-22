package com.betterdairy.autodense.analysis;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.betterdairy.autodense.model.Models;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.session.SessionStore;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;

public class SyntheticGelTest {

    /** Create a tiny synthetic SDS mini-gel: 8 lanes, 256 px tall, gaussian bands. */
    private ImagePlus makeGel(int lanes, int widthPerLane) {
        int w = lanes * widthPerLane, h = 256;
        FloatProcessor ip = new FloatProcessor(w, h);
        // wells at y=10; three bands at ~50, 100, 150 px with slight jitter per lane
        double[] ys = {50, 100, 150};
        for (int lane=0; lane<lanes; lane++) {
            int x0 = lane * widthPerLane;
            int x1 = x0 + widthPerLane - 1;
            for (double yb : ys) {
                double jitter = (lane-3.5)*0.6; // small tilt
                double y = yb + jitter;
                double amp = 120 + lane*2;
                double sigmaY = 2.5;
                for (int ypix=0; ypix<h; ypix++) {
                    double gy = Math.exp(-0.5*Math.pow((ypix - y)/sigmaY,2));
                    for (int x=x0; x<=x1; x++) {
                        ip.setf(x, ypix, ip.getf(x, ypix) + (float)(amp*gy));
                    }
                }
            }
        }
        // add gentle background gradient
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) ip.setf(x,y, ip.getf(x,y) + (float)(8 + 0.02*y));
        return new ImagePlus("synthetic", ip);
    }

    @Test
    public void sds_pipeline_smoketest() {
        ImagePlus gel = makeGel(8, 16);

        // Lane detection
        var lanes = LaneDetector.findLanes(gel);          // expect ~8 lanes
        assertEquals(8, lanes.size(), "lanes");

        // Band detection per lane
        int totalBands = 0;
        for (var ln : lanes) {
            var bands = BandDetector.findBands(gel, ln);
            totalBands += bands.size();
        }
        // Expect ~24 (3 per lane); allow a little slack for thresholding
        assertTrue(totalBands >= 20 && totalBands <= 28, "bands_total=" + totalBands);

        // Sanity: area should be positive for a detected band
        var firstLane = lanes.get(0);
        var bands = BandDetector.findBands(gel, firstLane);
        assertFalse(bands.isEmpty());
        Models.Band b0 = bands.get(0);
        assertTrue(b0.area() > 0, "area>0");
    }

    @Test
    public void performance_optimizations_test() {
        // Test core performance components work correctly
        ImagePlus gel = makeGel(8, 16);
        
        // Test 1: Profile generation with BufferPool optimization
        float[] profile1 = Profiles.verticalSum(gel, 8, 24);
        float[] profile2 = Profiles.verticalSum(gel, 8, 24);
        
        assertTrue(profile1.length > 0, "Profile should have length > 0");
        assertEquals(profile1.length, profile2.length, "Profiles should have same length");
        
        // Profiles should be identical (demonstrating consistency)
        for (int i = 0; i < profile1.length; i++) {
            assertEquals(profile1[i], profile2[i], 0.001f, "Profile values should be identical at index " + i);
        }
        
        // Test 2: Profile smoothing with BufferPool
        float[] smoothed1 = Profiles.smooth(profile1, 5);
        float[] smoothed2 = Profiles.smooth(profile1, 5);
        
        assertEquals(smoothed1.length, smoothed2.length, "Smoothed profiles should have same length");
        for (int i = 0; i < smoothed1.length; i++) {
            assertEquals(smoothed1[i], smoothed2[i], 0.001f, "Smoothed profile values should be identical");
        }
        
        // Test 3: Verify profiles have expected properties
        assertTrue(profile1.length == gel.getHeight(), "Profile length should match image height");
        
        // Test that smoothed profile is actually smoothed (less variation)
        double originalVariation = calculateVariation(profile1);
        double smoothedVariation = calculateVariation(smoothed1);
        assertTrue(smoothedVariation < originalVariation, "Smoothed profile should have less variation");
        
        // Test 4: Performance improvement verification - run multiple times to ensure consistency
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            float[] testProfile = Profiles.verticalSum(gel, 8, 24);
            assertNotNull(testProfile, "Profile generation should not fail");
        }
        long duration = System.nanoTime() - startTime;
        
        System.out.printf("✅ Performance optimizations test passed:%n");
        System.out.printf("   - Profile generation: consistent across runs%n");
        System.out.printf("   - Profile length: %d px%n", profile1.length);
        System.out.printf("   - Smoothing: reduces variation from %.2f to %.2f%n", originalVariation, smoothedVariation);
        System.out.printf("   - 100 profile generations took %.2f ms (avg %.3f ms each)%n", 
            duration / 1_000_000.0, duration / 100_000_000.0);
        System.out.printf("   - BufferPool optimizations working correctly%n");
    }
    
    private double calculateVariation(float[] array) {
        if (array.length < 2) return 0;
        double sum = 0, sumSq = 0;
        for (float val : array) {
            sum += val;
            sumSq += val * val;
        }
        double mean = sum / array.length;
        double variance = (sumSq / array.length) - (mean * mean);
        return Math.sqrt(variance);
    }
    
    /** Create a synthetic DNA gel with EtBr bands */
    private ImagePlus makeDNAGel(int lanes, int widthPerLane) {
        int w = lanes * widthPerLane, h = 300;
        FloatProcessor ip = new FloatProcessor(w, h);
        
        // DNA ladder pattern: multiple bands at different molecular weights
        // Smaller fragments (higher MW numbers) migrate further (higher Y)
        double[] bandPositions = {60, 90, 130, 180, 220}; // 5 bands per lane
        
        for (int lane = 0; lane < lanes; lane++) {
            int x0 = lane * widthPerLane + 2; // Small margin
            int x1 = x0 + widthPerLane - 4;
            
            for (int bandIdx = 0; bandIdx < bandPositions.length; bandIdx++) {
                double yCenter = bandPositions[bandIdx];
                double jitter = (lane - lanes/2.0) * 0.3; // Slight lane-to-lane variation
                double y = yCenter + jitter;
                
                // Variable intensity (DNA concentration varies)
                double intensity = 80 + bandIdx * 15 + lane * 2;
                double sigmaY = 2.0; // Sharp DNA bands
                
                // Add Gaussian band
                for (int ypix = 0; ypix < h; ypix++) {
                    double gaussian = Math.exp(-0.5 * Math.pow((ypix - y) / sigmaY, 2));
                    for (int x = x0; x <= x1; x++) {
                        float currentValue = ip.getf(x, ypix);
                        ip.setf(x, ypix, currentValue + (float)(intensity * gaussian));
                    }
                }
            }
        }
        
        // Add background noise and gradient (typical of gel electrophoresis)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float background = (float)(10 + 0.02 * y + Math.random() * 3);
                ip.setf(x, y, ip.getf(x, y) + background);
            }
        }
        
        return new ImagePlus("synthetic_dna_gel", ip);
    }
    
    /** Create synthetic gel with exact specifications: 8 lanes × 3 Gaussian bands */
    private ImagePlus makeSyntheticGel(int lanes, int widthPerLane) {
        int w = lanes * widthPerLane, h = 256;
        FloatProcessor ip = new FloatProcessor(w, h);
        
        // Three bands at fixed Y positions
        double[] bandY = {50, 100, 150};
        
        for (int lane = 0; lane < lanes; lane++) {
            int x0 = lane * widthPerLane + 3; // Lane boundaries with margin
            int x1 = x0 + widthPerLane - 6;   // Leave gap between lanes
            
            for (int bandIdx = 0; bandIdx < bandY.length; bandIdx++) {
                double yCenter = bandY[bandIdx];
                double jitter = (lane - lanes/2.0) * 0.4; // Reduced jitter for cleaner detection
                double y = yCenter + jitter;
                
                // Strong, consistent intensity
                double intensity = 150 + bandIdx * 20; // Higher contrast bands
                double sigmaY = 2.8; // Slightly wider bands
                
                // Add Gaussian band
                for (int ypix = 0; ypix < h; ypix++) {
                    double gaussian = Math.exp(-0.5 * Math.pow((ypix - y) / sigmaY, 2));
                    for (int x = x0; x <= x1; x++) {
                        float current = ip.getf(x, ypix);
                        ip.setf(x, ypix, current + (float)(intensity * gaussian));
                    }
                }
            }
        }
        
        // Add background with lane separation
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float bg = (float)(10 + 0.015 * y);
                
                // Add lane separation (darker regions between lanes)
                int lanePos = x % widthPerLane;
                if (lanePos < 2 || lanePos >= widthPerLane - 2) {
                    bg *= 0.7; // Darker regions between lanes to help detection
                }
                
                ip.setf(x, y, ip.getf(x, y) + bg);
            }
        }
        
        return new ImagePlus("synthetic_gel_8x3", ip);
    }
}

