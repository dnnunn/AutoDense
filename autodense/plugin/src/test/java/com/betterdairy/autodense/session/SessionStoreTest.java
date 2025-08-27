package com.betterdairy.autodense.session;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive unit tests for SessionStore.
 * Tests handle management, thread safety, memory management, and LRU eviction.
 */
class SessionStoreTest {
    
    private SessionStore sessionStore;
    
    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
    }
    
    @AfterEach
    void tearDown() {
        sessionStore.clear();
    }
    
    /**
     * Create a test image with specified size
     */
    private ImagePlus createTestImage(int width, int height, String title) {
        ByteProcessor processor = new ByteProcessor(width, height);
        processor.setValue(128);
        processor.fill();
        return new ImagePlus(title, processor);
    }
    
    @Test
    @DisplayName("Should store and retrieve images correctly")
    void testImageStorage() {
        // Arrange
        ImagePlus testImage = createTestImage(100, 100, "test-image");
        
        // Act
        String handle = sessionStore.putImage(testImage);
        SessionStore.ImageRecord retrieved = sessionStore.getImage(handle);
        
        // Assert
        assertNotNull(handle, "Handle should not be null");
        assertTrue(handle.startsWith("img_"), "Handle should have correct prefix");
        assertNotNull(retrieved, "Retrieved image should not be null");
        assertEquals(testImage, retrieved.image, "Retrieved image should match stored image");
        assertEquals(handle, retrieved.handle, "Handle should match");
        assertTrue(retrieved.estimatedMemoryBytes > 0, "Memory estimate should be positive");
    }
    
    @Test
    @DisplayName("Should handle overlay storage and association")
    void testOverlayStorage() {
        // Arrange
        ImagePlus testImage = createTestImage(100, 100, "test-image");
        String imageHandle = sessionStore.putImage(testImage);
        
        Overlay testOverlay = new Overlay();
        // Add some ROIs to the overlay if needed
        
        // Act
        String overlayHandle = sessionStore.putOverlay(testOverlay, imageHandle);
        SessionStore.OverlayRecord retrieved = sessionStore.getOverlay(overlayHandle);
        
        // Assert
        assertNotNull(overlayHandle, "Overlay handle should not be null");
        assertTrue(overlayHandle.startsWith("ov_"), "Overlay handle should have correct prefix");
        assertNotNull(retrieved, "Retrieved overlay should not be null");
        assertEquals(testOverlay, retrieved.overlay, "Retrieved overlay should match stored overlay");
        assertEquals(imageHandle, retrieved.imageHandle, "Image association should be correct");
        
        // Verify overlay is associated with image
        SessionStore.ImageRecord imageRecord = sessionStore.getImage(imageHandle);
        assertEquals(testOverlay, imageRecord.currentOverlay, "Image should reference overlay");
    }
    
    @Test
    @DisplayName("Should handle analysis storage")
    void testAnalysisStorage() {
        // Arrange
        ImagePlus testImage = createTestImage(100, 100, "test-image");
        String imageHandle = sessionStore.putImage(testImage);
        String testData = "test analysis data";
        
        // Act
        String analysisHandle = sessionStore.putAnalysis("test_type", testData, imageHandle);
        SessionStore.AnalysisRecord retrieved = sessionStore.getAnalysis(analysisHandle);
        
        // Assert
        assertNotNull(analysisHandle, "Analysis handle should not be null");
        assertTrue(analysisHandle.startsWith("analysis_"), "Analysis handle should have correct prefix");
        assertNotNull(retrieved, "Retrieved analysis should not be null");
        assertEquals("test_type", retrieved.type, "Analysis type should match");
        assertEquals(testData, retrieved.data, "Analysis data should match");
        assertEquals(imageHandle, retrieved.imageHandle, "Image association should be correct");
    }
    
    @Test
    @DisplayName("Should track session statistics correctly")
    void testSessionStatistics() {
        // Arrange
        assertEquals(0, sessionStore.getImageCount(), "Initial image count should be 0");
        assertEquals(0, sessionStore.getOverlayCount(), "Initial overlay count should be 0");
        assertEquals(0, sessionStore.getAnalysisCount(), "Initial analysis count should be 0");
        
        // Act - Add various items
        ImagePlus testImage = createTestImage(100, 100, "test-image");
        String imageHandle = sessionStore.putImage(testImage);
        
        Overlay testOverlay = new Overlay();
        String overlayHandle = sessionStore.putOverlay(testOverlay, imageHandle);
        
        String analysisHandle = sessionStore.putAnalysis("test", "data", imageHandle);
        
        // Assert
        assertEquals(1, sessionStore.getImageCount(), "Image count should be 1");
        assertEquals(1, sessionStore.getOverlayCount(), "Overlay count should be 1");
        assertEquals(1, sessionStore.getAnalysisCount(), "Analysis count should be 1");
        
        // Verify handles are tracked
        assertTrue(sessionStore.hasImage(imageHandle), "Should have image handle");
        assertTrue(sessionStore.hasOverlay(overlayHandle), "Should have overlay handle");
        assertTrue(sessionStore.hasAnalysis(analysisHandle), "Should have analysis handle");
    }
    
    @Test
    @DisplayName("Should manage active handles correctly")
    void testActiveHandleManagement() {
        // Arrange
        ImagePlus image1 = createTestImage(100, 100, "image1");
        ImagePlus image2 = createTestImage(100, 100, "image2");
        
        // Act
        String handle1 = sessionStore.putImage(image1);
        assertEquals(handle1, sessionStore.lastActiveImageHandle(), "First image should be active");
        
        String handle2 = sessionStore.putImage(image2);
        assertEquals(handle2, sessionStore.lastActiveImageHandle(), "Second image should be active");
        
        // Accessing first image should make it active
        sessionStore.getImage(handle1);
        assertEquals(handle1, sessionStore.lastActiveImageHandle(), "Accessed image should be active");
        
        // Explicit setting
        sessionStore.setLastActiveImageHandle(handle2);
        assertEquals(handle2, sessionStore.lastActiveImageHandle(), "Explicitly set image should be active");
    }
    
    @Test
    @DisplayName("Should handle thread safety for concurrent access")
    void testThreadSafety() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 50;
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        // Submit concurrent tasks
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Perform various operations concurrently
                        ImagePlus image = createTestImage(50, 50, "thread-" + threadId + "-image-" + j);
                        String handle = sessionStore.putImage(image);
                        
                        // Verify we can retrieve it
                        SessionStore.ImageRecord retrieved = sessionStore.getImage(handle);
                        if (retrieved != null && retrieved.image == image) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
        executor.shutdown();
        
        // Assert thread safety
        int expectedSuccesses = numThreads * operationsPerThread;
        assertEquals(expectedSuccesses, successCount.get(), "All operations should succeed");
        assertEquals(0, errorCount.get(), "No errors should occur");
        assertEquals(expectedSuccesses, sessionStore.getImageCount(), "All images should be stored");
    }
    
    @Test
    @DisplayName("Should perform LRU eviction when limits exceeded")
    void testLRUEviction() {
        // Arrange - Configure small limits for testing
        sessionStore.configureEviction(3, 1, true); // Max 3 images, 1MB limit, eviction enabled
        
        // Act - Add images that exceed the limit
        ImagePlus image1 = createTestImage(100, 100, "image1");
        ImagePlus image2 = createTestImage(100, 100, "image2");
        ImagePlus image3 = createTestImage(100, 100, "image3");
        ImagePlus image4 = createTestImage(100, 100, "image4");
        
        String handle1 = sessionStore.putImage(image1);
        String handle2 = sessionStore.putImage(image2);
        String handle3 = sessionStore.putImage(image3);
        
        // Access image1 to make it more recently used
        sessionStore.getImage(handle1);
        
        // Add 4th image, should trigger eviction
        String handle4 = sessionStore.putImage(image4);
        
        // Assert - Least recently used image should be evicted
        assertTrue(sessionStore.hasImage(handle1), "Recently accessed image should remain");
        assertTrue(sessionStore.hasImage(handle4), "Newest image should remain");
        // handle2 or handle3 should be evicted (implementation dependent)
        assertTrue(sessionStore.getImageCount() <= 3, "Should not exceed max image count");
    }
    
    @Test
    @DisplayName("Should estimate memory usage correctly")
    void testMemoryTracking() {
        // Arrange
        long initialMemory = sessionStore.getCurrentMemoryMB();
        assertEquals(0, initialMemory, "Initial memory should be 0");
        
        // Act - Add images of different sizes
        ImagePlus smallImage = createTestImage(50, 50, "small");
        ImagePlus largeImage = createTestImage(500, 500, "large");
        
        sessionStore.putImage(smallImage);
        long afterSmallImage = sessionStore.getCurrentMemoryMB();
        
        sessionStore.putImage(largeImage);
        long afterLargeImage = sessionStore.getCurrentMemoryMB();
        
        // Assert
        assertTrue(afterSmallImage > initialMemory, "Memory should increase after adding small image");
        assertTrue(afterLargeImage > afterSmallImage, "Memory should increase more after adding large image");
        
        // Verify memory report
        String memoryReport = sessionStore.getMemoryReport();
        assertNotNull(memoryReport, "Memory report should not be null");
        assertTrue(memoryReport.contains("Total Memory"), "Report should contain total memory");
        assertTrue(memoryReport.contains("Images"), "Report should contain image count");
    }
    
    @Test
    @DisplayName("Should handle invalid handles gracefully")
    void testInvalidHandles() {
        // Test invalid image handle
        assertThrows(IllegalArgumentException.class, () -> {
            sessionStore.getImage("invalid_handle");
        }, "Should throw exception for invalid image handle");
        
        // Test invalid overlay handle
        assertThrows(IllegalArgumentException.class, () -> {
            sessionStore.getOverlay("invalid_handle");
        }, "Should throw exception for invalid overlay handle");
        
        // Test invalid analysis handle
        assertThrows(NullPointerException.class, () -> {
            sessionStore.getAnalysis("invalid_handle");
        }, "Should throw exception for invalid analysis handle");
    }
    
    @Test
    @DisplayName("Should clear session properly")
    void testSessionClear() {
        // Arrange - Add various items
        ImagePlus testImage = createTestImage(100, 100, "test-image");
        String imageHandle = sessionStore.putImage(testImage);
        
        Overlay testOverlay = new Overlay();
        String overlayHandle = sessionStore.putOverlay(testOverlay, imageHandle);
        
        String analysisHandle = sessionStore.putAnalysis("test", "data", imageHandle);
        
        // Verify items exist
        assertTrue(sessionStore.getImageCount() > 0, "Should have images");
        assertTrue(sessionStore.getOverlayCount() > 0, "Should have overlays");
        assertTrue(sessionStore.getAnalysisCount() > 0, "Should have analyses");
        
        // Act - Clear session
        sessionStore.clear();
        
        // Assert - Everything should be cleared
        assertEquals(0, sessionStore.getImageCount(), "Image count should be 0 after clear");
        assertEquals(0, sessionStore.getOverlayCount(), "Overlay count should be 0 after clear");
        assertEquals(0, sessionStore.getAnalysisCount(), "Analysis count should be 0 after clear");
        assertEquals(0, sessionStore.getCurrentMemoryMB(), "Memory should be 0 after clear");
        
        assertFalse(sessionStore.hasImage(imageHandle), "Should not have cleared image");
        assertFalse(sessionStore.hasOverlay(overlayHandle), "Should not have cleared overlay");
        assertFalse(sessionStore.hasAnalysis(analysisHandle), "Should not have cleared analysis");
    }
    
    @Test
    @DisplayName("Should provide meaningful session summary")
    void testSessionSummary() {
        // Arrange
        ImagePlus testImage = createTestImage(100, 100, "test-image");
        sessionStore.putImage(testImage);
        
        // Act
        String summary = sessionStore.getSummary();
        
        // Assert
        assertNotNull(summary, "Summary should not be null");
        assertTrue(summary.contains("Session"), "Summary should mention session");
        assertTrue(summary.contains("1 images"), "Summary should show image count");
        assertTrue(summary.contains("Thread:"), "Summary should show thread info");
    }
}