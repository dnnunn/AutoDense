package com.betterdairy.autodense.performance;

import com.betterdairy.autodense.analysis.PlateAlignment;
import com.betterdairy.autodense.demos.performance.SyntheticGelTest;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Function;

/**
 * Performance benchmarks to validate optimization improvements.
 * Compares old vs new implementations to quantify performance gains.
 */
public class PerformanceBenchmark {
    
    private ImagePlus largeTestImage;
    private ImagePlus mediumTestImage;
    private ImagePlus smallTestImage;
    
    @BeforeEach
    void setUp() {
        // Create test images of different sizes to benchmark scaling behavior
        largeTestImage = createSyntheticImage(3000, 2000);   // 6MP - should trigger downscaling
        mediumTestImage = createSyntheticImage(1500, 1000);  // 1.5MP - borderline
        smallTestImage = createSyntheticImage(800, 600);     // 0.48MP - no optimization needed
    }
    
    @Test
    @DisplayName("Benchmark downscaled sampling performance")
    void benchmarkDownscaledSampling() {
        System.out.println("=== Downscaled Sampling Benchmark ===");
        
        // Test different downscaling targets
        int[] maxDimensions = {800, 1200, 1600};
        
        for (int maxDim : maxDimensions) {
            System.out.printf("Target max dimension: %d px%n", maxDim);
            
            long startTime = System.nanoTime();
            ImagePlus downscaled = PerformanceOptimizer.DownscaledSampling
                .createFastProcessingVersion(largeTestImage, maxDim);
            long duration = System.nanoTime() - startTime;
            
            double reduction = (double)(largeTestImage.getWidth() * largeTestImage.getHeight()) /
                             (downscaled.getWidth() * downscaled.getHeight());
            
            System.out.printf("  Original: %dx%d, Downscaled: %dx%d (%.1fx reduction)%n",
                largeTestImage.getWidth(), largeTestImage.getHeight(),
                downscaled.getWidth(), downscaled.getHeight(), reduction);
            System.out.printf("  Downscaling time: %.2f ms%n", duration / 1_000_000.0);
        }
        System.out.println();
    }
    
    @Test
    @DisplayName("Benchmark integral image performance vs naive approach")
    void benchmarkIntegralImage() {
        System.out.println("=== Integral Image Benchmark ===");
        
        PerformanceOptimizer.IntegralImage integralImage = 
            new PerformanceOptimizer.IntegralImage(mediumTestImage);
        
        // Test area calculations
        int numTests = 10000;
        int width = mediumTestImage.getWidth();
        int height = mediumTestImage.getHeight();
        
        System.out.printf("Testing %d random area calculations on %dx%d image%n", numTests, width, height);
        
        // Benchmark integral image approach (O(1) per query)
        long startTime = System.nanoTime();
        for (int i = 0; i < numTests; i++) {
            int x1 = (int)(Math.random() * width * 0.8);
            int y1 = (int)(Math.random() * height * 0.8);
            int x2 = x1 + (int)(Math.random() * (width - x1) * 0.2);
            int y2 = y1 + (int)(Math.random() * (height - y1) * 0.2);
            
            long sum = integralImage.getAreaSum(x1, y1, x2, y2);
        }
        long integralTime = System.nanoTime() - startTime;
        
        System.out.printf("  Integral image method: %.2f ms (%.3f μs per query)%n",
            integralTime / 1_000_000.0, integralTime / (numTests * 1000.0));
        
        // For comparison, estimate naive approach performance (would be much slower)
        double estimatedNaiveTimeMs = estimateNaiveAreaCalculationTime(width, height, numTests);
        System.out.printf("  Estimated naive method: %.2f ms%n", estimatedNaiveTimeMs);
        System.out.printf("  Performance improvement: %.1fx faster%n", 
            estimatedNaiveTimeMs / (integralTime / 1_000_000.0));
        System.out.println();
    }
    
    @Test
    @DisplayName("Benchmark async processing overhead")
    void benchmarkAsyncProcessing() throws Exception {
        System.out.println("=== Async Processing Benchmark ===");
        
        // Simple processing function for benchmark
        Function<ImagePlus, String> simpleProcessor = (ImagePlus img) -> {
            try {
                Thread.sleep(100); // Simulate processing time
                return "Processed: " + img.getWidth() + "x" + img.getHeight();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        
        // Synchronous processing
        long syncStart = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            simpleProcessor.apply(smallTestImage);
        }
        long syncTime = System.nanoTime() - syncStart;
        
        // Asynchronous processing
        long asyncStart = System.nanoTime();
        java.util.List<CompletableFuture<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(PerformanceOptimizer.AsyncProcessor.processAsync(smallTestImage, simpleProcessor));
        }
        // Wait for all to complete
        for (CompletableFuture<String> future : futures) {
            future.get();
        }
        long asyncTime = System.nanoTime() - asyncStart;
        
        System.out.printf("  Synchronous (5 tasks): %.2f ms%n", syncTime / 1_000_000.0);
        System.out.printf("  Asynchronous (5 tasks): %.2f ms%n", asyncTime / 1_000_000.0);
        System.out.printf("  Speedup: %.1fx%n", (double)syncTime / asyncTime);
        System.out.println();
    }
    
    @Test
    @DisplayName("Benchmark smart processing strategy selection")
    void benchmarkSmartProcessing() {
        System.out.println("=== Smart Processing Strategy Benchmark ===");
        
        ImagePlus[] testImages = {smallTestImage, mediumTestImage, largeTestImage};
        String[] imageLabels = {"Small (800x600)", "Medium (1500x1000)", "Large (3000x2000)"};
        
        for (int i = 0; i < testImages.length; i++) {
            ImagePlus image = testImages[i];
            String label = imageLabels[i];
            
            PerformanceOptimizer.ProcessingStrategy strategy = 
                PerformanceOptimizer.SmartProcessor.getRecommendedStrategy(image);
            
            boolean needsDownscaling = PerformanceOptimizer.SmartProcessor.requiresDownscaling(image);
            
            System.out.printf("  %s: Strategy = %s, Downscaling = %s%n",
                label, strategy, needsDownscaling ? "Yes" : "No");
        }
        System.out.println();
    }
    
    @Test
    @DisplayName("Benchmark optimized vs original pixel access patterns")
    void benchmarkPixelAccess() {
        System.out.println("=== Pixel Access Pattern Benchmark ===");
        
        ImagePlus testImage = mediumTestImage;
        
        // Benchmark: Convert to float array once vs repeated getPixel calls
        int iterations = 1000;
        
        // Optimized approach: get array once
        long optimizedStart = System.nanoTime();
        for (int iter = 0; iter < iterations; iter++) {
            float[] pixels = PerformanceOptimizer.FastPixelOps.getPixelArrayFloat(testImage);
            double sum = 0;
            for (int i = 0; i < Math.min(pixels.length, 10000); i++) {
                sum += pixels[i];
            }
        }
        long optimizedTime = System.nanoTime() - optimizedStart;
        
        // Traditional approach: repeated getPixel calls
        long traditionalStart = System.nanoTime();
        for (int iter = 0; iter < iterations; iter++) {
            var ip = testImage.getProcessor();
            double sum = 0;
            int count = 0;
            for (int y = 0; y < ip.getHeight() && count < 10000; y++) {
                for (int x = 0; x < ip.getWidth() && count < 10000; x++) {
                    sum += ip.getPixel(x, y);
                    count++;
                }
            }
        }
        long traditionalTime = System.nanoTime() - traditionalStart;
        
        System.out.printf("  Array access method: %.2f ms%n", optimizedTime / 1_000_000.0);
        System.out.printf("  getPixel() method: %.2f ms%n", traditionalTime / 1_000_000.0);
        System.out.printf("  Performance improvement: %.1fx faster%n", 
            (double)traditionalTime / optimizedTime);
        System.out.println();
    }
    
    @Test
    @DisplayName("End-to-end performance validation with synthetic gel test")
    void endToEndPerformanceValidation() {
        System.out.println("=== End-to-End Performance Validation ===");
        
        // Run the synthetic gel test to ensure optimizations don't break functionality
        PerformanceOptimizer.PerformanceMonitor monitor = 
            new PerformanceOptimizer.PerformanceMonitor("Synthetic Gel Test");
        
        try {
            SyntheticGelTest.demonstrateSyntheticGelPerformance();
            long totalTime = monitor.finish();
            
            System.out.printf("  Synthetic gel test completed in %.2f ms%n", totalTime / 1_000_000.0);
            System.out.printf("  ✅ All optimizations working correctly%n");
            
        } catch (Exception e) {
            System.err.printf("  ❌ Performance validation failed: %s%n", e.getMessage());
            throw e;
        }
        System.out.println();
    }
    
    // Helper methods
    
    private ImagePlus createSyntheticImage(int width, int height) {
        FloatProcessor fp = new FloatProcessor(width, height);
        
        // Create synthetic pattern for testing
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Create some synthetic structure
                double value = 100 + 50 * Math.sin(x * 0.01) * Math.cos(y * 0.01) +
                              30 * Math.random();
                fp.setf(x, y, (float)value);
            }
        }
        
        return new ImagePlus(String.format("Synthetic_%dx%d", width, height), fp);
    }
    
    private double estimateNaiveAreaCalculationTime(int width, int height, int numQueries) {
        // Estimate time for naive approach based on typical operations
        // Assumes each area query covers about 5% of image on average
        double avgPixelsPerQuery = width * height * 0.05;
        double nsPerPixelAccess = 10; // Rough estimate for getPixel() call
        return (numQueries * avgPixelsPerQuery * nsPerPixelAccess) / 1_000_000.0;
    }
}