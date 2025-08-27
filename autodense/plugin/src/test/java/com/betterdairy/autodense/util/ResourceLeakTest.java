package com.betterdairy.autodense.util;

import com.betterdairy.autodense.analysis.ResourceAwareImagePreprocessor;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for resource leak detection and prevention.
 * 
 * These tests validate that our resource cleanup improvements successfully
 * prevent memory leaks from ImagePlus instances created during processing.
 */
public class ResourceLeakTest {
    
    private static final int TEST_IMAGE_WIDTH = 100;
    private static final int TEST_IMAGE_HEIGHT = 100;
    
    @BeforeEach
    void setUp() {
        ResourceLeakDetector.enableTracking();
        ResourceManager.resetGlobalStats();
    }
    
    @AfterEach
    void tearDown() {
        ResourceLeakDetector.cleanup();
    }
    
    @Test
    @DisplayName("ResourceManager should properly track and cleanup ImagePlus instances")
    void testResourceManagerCleanup() {
        ImagePlus testImage = createTestImage();
        
        try (ResourceManager rm = new ResourceManager("test-context")) {
            // Create tracked duplicates
            ImagePlus duplicate1 = rm.createTrackedDuplicate(testImage);
            ImagePlus duplicate2 = rm.createTrackedDuplicate(testImage);
            ImagePlus duplicate3 = rm.createTrackedDuplicate(testImage);
            
            ResourceManager.ResourceStats stats = rm.getStats();
            assertEquals(3, stats.activeImages, "Should track 3 active images");
            
            // Manually dispose one image
            rm.dispose(duplicate2);
            stats = rm.getStats();
            assertEquals(2, stats.activeImages, "Should have 2 active images after manual disposal");
            
            // ResourceManager should auto-cleanup remaining images on close()
        }
        
        ResourceManager.GlobalResourceStats globalStats = ResourceManager.getGlobalStats();
        assertEquals(0, globalStats.potentialLeaks, "Should have no resource leaks after cleanup");
    }
    
    @Test
    @DisplayName("ResourceAwareImagePreprocessor should prevent resource leaks")
    void testResourceAwareImagePreprocessorCleanup() throws Exception {
        ImagePlus testImage = createTestImage();
        
        // Create preprocessing steps that normally cause leaks
        JSONArray steps = new JSONArray();
        steps.put(new JSONObject().put("op", "rotate").put("angle_deg", 90));
        steps.put(new JSONObject().put("op", "flip").put("axis", "horizontal"));
        steps.put(new JSONObject().put("op", "crop").put("x", 10).put("y", 10).put("width", 50).put("height", 50));
        
        ImagePlus result;
        try (ResourceAwareImagePreprocessor processor = new ResourceAwareImagePreprocessor("test")) {
            result = processor.apply(testImage, steps, false);
            
            ResourceManager.ResourceStats stats = processor.getResourceStats();
            assertTrue(stats.totalCreated > 0, "Should have created intermediate images");
            
            // Processor should clean up all intermediate images on close()
        }
        
        assertNotNull(result, "Should return valid result");
        
        ResourceManager.GlobalResourceStats globalStats = ResourceManager.getGlobalStats();
        assertEquals(0, globalStats.potentialLeaks, "Should have no resource leaks after preprocessing");
    }
    
    @Test
    @DisplayName("Static methods should also prevent resource leaks")
    void testStaticMethodsCleanup() {
        ImagePlus testImage = createTestImage();
        
        // Create preprocessing steps
        JSONArray steps = new JSONArray();
        steps.put(new JSONObject().put("op", "clahe").put("blocksize", 64).put("histogram", 128).put("maximum", 2.0));
        steps.put(new JSONObject().put("op", "bandpass").put("filter_large", 30.0).put("filter_small", 2.0));
        
        // Test static applySafely method
        ImagePlus result = ResourceAwareImagePreprocessor.applySafely(testImage, steps, false);
        assertNotNull(result, "Should return valid result from static method");
        
        // Test static applyModeSafely method  
        ImagePlus modeResult = ResourceAwareImagePreprocessor.applyModeSafely(testImage, "coomassie_default", false);
        assertNotNull(modeResult, "Should return valid result from static mode method");
        
        ResourceManager.GlobalResourceStats globalStats = ResourceManager.getGlobalStats();
        assertEquals(0, globalStats.potentialLeaks, "Static methods should not cause resource leaks");
    }
    
    @Test
    @DisplayName("ResourceLeakDetector should identify potential leaks")
    void testResourceLeakDetection() {
        ImagePlus testImage = createTestImage();
        
        // Create a leak by duplicating without proper cleanup
        ImagePlus leaked = testImage.duplicate();
        ResourceLeakDetector.trackImageCreation(leaked, "intentional-leak-test");
        
        // Create properly managed resource
        try (ResourceManager rm = new ResourceManager("proper-cleanup")) {
            ImagePlus managed = rm.createTrackedDuplicate(testImage);
            assertNotNull(managed, "Should create managed duplicate");
        }
        
        ResourceLeakDetector.LeakReport report = ResourceLeakDetector.generateLeakReport();
        assertTrue(report.hasLeaks, "Should detect the intentional leak");
        assertEquals(1, report.potentialLeaks.size(), "Should identify exactly one leak");
        
        ResourceLeakDetector.LeakInfo leakInfo = report.potentialLeaks.get(0);
        assertEquals("intentional-leak-test", leakInfo.creationContext, "Should identify correct leak context");
        
        // Clean up the leak
        leaked.close();
        ResourceLeakDetector.trackImageClosure(leaked);
    }
    
    @Test
    @DisplayName("Memory pressure monitoring should work correctly")
    void testMemoryPressureMonitoring() {
        ResourceLeakDetector.MemoryPressureInfo pressure = ResourceLeakDetector.getMemoryPressure();
        
        assertNotNull(pressure, "Should return memory pressure info");
        assertTrue(pressure.maxMemory > 0, "Max memory should be positive");
        assertTrue(pressure.usedMemory >= 0, "Used memory should be non-negative");
        assertTrue(pressure.usagePercent >= 0 && pressure.usagePercent <= 100, "Usage percent should be 0-100");
        
        System.out.println("Current memory pressure: " + pressure);
    }
    
    @Test
    @DisplayName("Try-with-resources pattern should prevent leaks")
    void testTryWithResourcesPattern() {
        ImagePlus testImage = createTestImage();
        ResourceManager.ResourceStats finalStats;
        
        // Simulate the pattern used in our fixed GelAnalysisTools methods
        try (ResourceManager rm = new ResourceManager("export-simulation")) {
            ImagePlus workingImage = rm.createTrackedDuplicate(testImage);
            
            // Simulate image processing operations
            ImagePlus processed1 = rm.track(workingImage.duplicate());
            ImagePlus processed2 = rm.track(processed1.duplicate());
            
            // Simulate overlay application
            ImagePlus flattened = processed2.flatten();
            rm.dispose(processed2); // Dispose before reassigning
            ImagePlus finalImage = rm.track(flattened);
            
            ResourceManager.ResourceStats stats = rm.getStats();
            assertTrue(stats.activeImages > 0, "Should track active images during processing");
            
            finalStats = stats;
        } // Auto-cleanup happens here
        
        assertTrue(finalStats.totalCreated > 0, "Should have created images during processing");
        
        ResourceManager.GlobalResourceStats globalStats = ResourceManager.getGlobalStats();
        assertEquals(0, globalStats.potentialLeaks, "Try-with-resources should prevent all leaks");
    }
    
    @Test
    @DisplayName("ResourceManager should handle exceptions gracefully")
    void testExceptionHandling() {
        ImagePlus testImage = createTestImage();
        
        ResourceManager.GlobalResourceStats statsBefore = ResourceManager.getGlobalStats();
        
        try (ResourceManager rm = new ResourceManager("exception-test")) {
            ImagePlus duplicate = rm.createTrackedDuplicate(testImage);
            
            // Simulate an exception during processing
            throw new RuntimeException("Simulated processing error");
        } catch (RuntimeException e) {
            assertEquals("Simulated processing error", e.getMessage(), "Should propagate exception");
        }
        
        ResourceManager.GlobalResourceStats statsAfter = ResourceManager.getGlobalStats();
        assertEquals(statsBefore.potentialLeaks, statsAfter.potentialLeaks, 
                    "Exception handling should not cause resource leaks");
    }
    
    /**
     * Create a test image for use in tests.
     */
    private ImagePlus createTestImage() {
        ByteProcessor processor = new ByteProcessor(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT);
        
        // Create a simple pattern for testing
        for (int y = 0; y < TEST_IMAGE_HEIGHT; y++) {
            for (int x = 0; x < TEST_IMAGE_WIDTH; x++) {
                int value = (x + y) % 256;
                processor.putPixel(x, y, value);
            }
        }
        
        return new ImagePlus("test-image", processor);
    }
}