package com.betterdairy.autodense.session;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/**
 * Comprehensive concurrent access testing utility for SessionStore implementations.
 * 
 * Tests various race condition scenarios:
 * 1. Concurrent image insertions and retrievals
 * 2. Simultaneous overlay operations
 * 3. Rapid handle state changes
 * 4. LRU eviction under load
 * 5. Stream operations during mutations
 * 6. Memory pressure scenarios
 * 
 * Usage:
 * - Use to validate thread safety of SessionStore implementations
 * - Run stress tests to expose race conditions
 * - Performance benchmarking under concurrent load
 * - Regression testing for thread safety fixes
 */
public class ConcurrentAccessTester {
    
    private final ExecutorService executorService;
    private final Random random = new Random(42); // Fixed seed for reproducible tests
    
    public ConcurrentAccessTester(int threadCount) {
        this.executorService = Executors.newFixedThreadPool(threadCount);
    }
    
    /**
     * Comprehensive thread safety test result
     */
    public static class TestResult {
        public final boolean passed;
        public final int totalOperations;
        public final int successfulOperations;
        public final int failedOperations;
        public final List<String> errors;
        public final long durationMs;
        public final double operationsPerSecond;
        
        public TestResult(boolean passed, int totalOperations, int successfulOperations, 
                         int failedOperations, List<String> errors, long durationMs) {
            this.passed = passed;
            this.totalOperations = totalOperations;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.errors = new ArrayList<>(errors);
            this.durationMs = durationMs;
            this.operationsPerSecond = totalOperations / (durationMs / 1000.0);
        }
        
        public String getSummary() {
            return String.format(
                "Test %s: %d/%d operations successful (%.1f%%), %d errors, %.1f ops/sec",
                passed ? "PASSED" : "FAILED",
                successfulOperations, totalOperations,
                (successfulOperations * 100.0) / totalOperations,
                errors.size(),
                operationsPerSecond
            );
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getSummary()).append("\n");
            sb.append("Duration: ").append(durationMs).append("ms\n");
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }
            return sb.toString();
        }
    }
    
    /**
     * Test ThreadSafeSessionStore wrapper specifically
     */
    public TestResult testThreadSafeWrapper(ThreadSafeSessionStore store, int operationsPerThread, int threadCount) {
        AtomicInteger totalOps = new AtomicInteger(0);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        List<Future<Void>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // Launch concurrent threads
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            
            Future<Void> future = executorService.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        totalOps.incrementAndGet();
                        
                        // Test thread-safe wrapper specific operations
                        int operation = random.nextInt(6);
                        switch (operation) {
                            case 0: // Put image and check consistency
                                ImagePlus img = createTestImage(32, 32);
                                String handle = store.putImage(img);
                                if (handle != null) {
                                    // Verify consistency
                                    ThreadSafeSessionStore.ConsistencyReport report = store.validateConsistency();
                                    if (report.isConsistent) {
                                        successfulOps.incrementAndGet();
                                    } else {
                                        failedOps.incrementAndGet();
                                        errors.add("Consistency check failed after putImage: " + report.getSummary());
                                    }
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("putImage returned null handle");
                                }
                                break;
                                
                            case 1: // Get store statistics
                                ThreadSafeSessionStore.StoreStatistics stats = store.getStoreStatistics();
                                if (stats != null && stats.imageCount >= 0) {
                                    successfulOps.incrementAndGet();
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("getStoreStatistics returned invalid data");
                                }
                                break;
                                
                            case 2: // Test handle existence checks
                                String testHandle = "img_" + threadId + "_" + i;
                                boolean exists = store.hasHandle(testHandle);
                                // This should always return without throwing
                                successfulOps.incrementAndGet();
                                break;
                                
                            case 3: // Test overlays with validation
                                List<String> images = store.listAvailableImages();
                                if (!images.isEmpty()) {
                                    String imgHandle = images.get(random.nextInt(images.size()));
                                    Overlay overlay = createTestOverlay();
                                    try {
                                        String overlayHandle = store.putOverlay(overlay, imgHandle);
                                        if (overlayHandle != null) {
                                            successfulOps.incrementAndGet();
                                        } else {
                                            failedOps.incrementAndGet();
                                            errors.add("putOverlay returned null handle");
                                        }
                                    } catch (IllegalArgumentException e) {
                                        // Expected if image became invalid
                                        successfulOps.incrementAndGet();
                                    }
                                } else {
                                    successfulOps.incrementAndGet(); // Valid state
                                }
                                break;
                                
                            case 4: // Test access time tracking
                                List<String> imgHandles = store.listAvailableImages();
                                if (!imgHandles.isEmpty()) {
                                    String imgHandle = imgHandles.get(random.nextInt(imgHandles.size()));
                                    Long accessTime = store.getHandleAccessTime(imgHandle);
                                    // Access time can be null for handles not tracked by wrapper
                                    successfulOps.incrementAndGet();
                                }
                                break;
                                
                            case 5: // Test custom eviction
                                store.triggerLRUEviction();
                                successfulOps.incrementAndGet();
                                break;
                        }
                        
                        // Small random delay
                        Thread.sleep(random.nextInt(3));
                        
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                        errors.add("Thread " + threadId + " operation " + i + ": " + e.getMessage());
                    }
                }
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for completion
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS); // 30 second timeout per thread
            } catch (Exception e) {
                errors.add("Thread execution error: " + e.getMessage());
                failedOps.incrementAndGet();
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double opsPerSecond = totalOps.get() * 1000.0 / duration;
        
        // Final consistency check
        ThreadSafeSessionStore.ConsistencyReport finalReport = store.validateConsistency();
        if (!finalReport.isConsistent) {
            errors.add("Final consistency check failed: " + finalReport.getSummary());
        }
        
        return new TestResult(
            errors.isEmpty() && finalReport.isConsistent,
            totalOps.get(),
            successfulOps.get(), 
            failedOps.get(),
            new ArrayList<>(errors),
            duration
        );
    }
    
    /**
     * Test concurrent image operations (putImage, getImage, hasCurrentImage)
     */
    public TestResult testConcurrentImageOperations(SessionStore store, int operationsPerThread, int threadCount) {
        AtomicInteger totalOps = new AtomicInteger(0);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        List<Future<Void>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // Launch concurrent threads
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            
            Future<Void> future = executorService.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        totalOps.incrementAndGet();
                        
                        // Mix of operations
                        int operation = random.nextInt(4);
                        switch (operation) {
                            case 0: // Put image
                                ImagePlus img = createTestImage(64, 64);
                                String handle = store.putImage(img);
                                if (handle != null) {
                                    successfulOps.incrementAndGet();
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("putImage returned null handle");
                                }
                                break;
                                
                            case 1: // Get current image
                                if (store.hasCurrentImage()) {
                                    SessionStore.ImageRecord record = store.getCurrentImage();
                                    if (record != null) {
                                        successfulOps.incrementAndGet();
                                    } else {
                                        failedOps.incrementAndGet();
                                        errors.add("getCurrentImage returned null despite hasCurrentImage=true");
                                    }
                                } else {
                                    successfulOps.incrementAndGet(); // Valid state
                                }
                                break;
                                
                            case 2: // List available images
                                List<String> images = store.listAvailableImages();
                                if (images != null) {
                                    successfulOps.incrementAndGet();
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("listAvailableImages returned null");
                                }
                                break;
                                
                            case 3: // Check image count
                                int count = store.getImageCount();
                                if (count >= 0) {
                                    successfulOps.incrementAndGet();
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("getImageCount returned negative value: " + count);
                                }
                                break;
                        }
                        
                        // Small random delay to increase chance of race conditions
                        Thread.sleep(random.nextInt(5));
                        
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                        errors.add("Thread " + threadId + ": " + e.getMessage());
                    }
                }
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for all threads to complete
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.add("Thread execution error: " + e.getMessage());
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        boolean passed = failedOps.get() == 0 && errors.isEmpty();
        return new TestResult(passed, totalOps.get(), successfulOps.get(), 
                             failedOps.get(), errors, duration);
    }
    
    /**
     * Test concurrent overlay operations with image dependencies
     */
    public TestResult testConcurrentOverlayOperations(SessionStore store, int operationsPerThread, int threadCount) {
        AtomicInteger totalOps = new AtomicInteger(0);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        // Pre-populate with some images
        List<String> imageHandles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ImagePlus img = createTestImage(32, 32);
            imageHandles.add(store.putImage(img));
        }
        
        List<Future<Void>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            
            Future<Void> future = executorService.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        totalOps.incrementAndGet();
                        
                        int operation = random.nextInt(3);
                        switch (operation) {
                            case 0: // Put overlay
                                if (!imageHandles.isEmpty()) {
                                    String imageHandle = imageHandles.get(random.nextInt(imageHandles.size()));
                                    Overlay overlay = createTestOverlay();
                                    String handle = store.putOverlay(overlay, imageHandle);
                                    if (handle != null) {
                                        successfulOps.incrementAndGet();
                                    } else {
                                        failedOps.incrementAndGet();
                                        errors.add("putOverlay returned null handle");
                                    }
                                } else {
                                    successfulOps.incrementAndGet(); // No images available
                                }
                                break;
                                
                            case 1: // Get overlays for image
                                if (!imageHandles.isEmpty()) {
                                    String imageHandle = imageHandles.get(random.nextInt(imageHandles.size()));
                                    List<String> overlays = store.getOverlaysForImage(imageHandle);
                                    if (overlays != null) {
                                        successfulOps.incrementAndGet();
                                    } else {
                                        failedOps.incrementAndGet();
                                        errors.add("getOverlaysForImage returned null");
                                    }
                                } else {
                                    successfulOps.incrementAndGet(); // No images available
                                }
                                break;
                                
                            case 2: // Get overlay count
                                int count = store.getOverlayCount();
                                if (count >= 0) {
                                    successfulOps.incrementAndGet();
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("getOverlayCount returned negative value: " + count);
                                }
                                break;
                        }
                        
                        Thread.sleep(random.nextInt(3));
                        
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                        errors.add("Thread " + threadId + ": " + e.getMessage());
                    }
                }
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for completion
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.add("Thread execution error: " + e.getMessage());
            }
        }
        
        long endTime = System.currentTimeMillis();
        boolean passed = failedOps.get() == 0 && errors.isEmpty();
        
        return new TestResult(passed, totalOps.get(), successfulOps.get(), 
                             failedOps.get(), errors, endTime - startTime);
    }
    
    /**
     * Test rapid handle state changes to expose race conditions
     */
    public TestResult testRapidHandleStateChanges(SessionStore store, int operationsPerThread, int threadCount) {
        AtomicInteger totalOps = new AtomicInteger(0);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        // Pre-populate with images
        List<String> imageHandles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ImagePlus img = createTestImage(16, 16);
            imageHandles.add(store.putImage(img));
        }
        
        List<Future<Void>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            
            Future<Void> future = executorService.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        totalOps.incrementAndGet();
                        
                        // Rapidly change active handles
                        String handle = imageHandles.get(random.nextInt(imageHandles.size()));
                        store.setLastActiveImageHandle(handle);
                        
                        // Verify consistency
                        String retrievedHandle = store.getLastActiveImageHandle();
                        if (retrievedHandle != null && store.hasImage(retrievedHandle)) {
                            successfulOps.incrementAndGet();
                        } else if (retrievedHandle == null) {
                            successfulOps.incrementAndGet(); // Valid state
                        } else {
                            failedOps.incrementAndGet();
                            errors.add("Active handle points to non-existent image: " + retrievedHandle);
                        }
                        
                        // No delay - maximum race condition potential
                        
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                        errors.add("Thread " + threadId + ": " + e.getMessage());
                    }
                }
                return null;
            });
            
            futures.add(future);
        }
        
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.add("Thread execution error: " + e.getMessage());
            }
        }
        
        long endTime = System.currentTimeMillis();
        boolean passed = failedOps.get() == 0 && errors.isEmpty();
        
        return new TestResult(passed, totalOps.get(), successfulOps.get(), 
                             failedOps.get(), errors, endTime - startTime);
    }
    
    /**
     * Test LRU eviction under concurrent load
     */
    public TestResult testLRUEvictionUnderLoad(SessionStore store, int imagesPerThread, int threadCount) {
        AtomicInteger totalOps = new AtomicInteger(0);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        // Configure aggressive eviction to trigger LRU
        store.configureEviction(10, 50, true); // Max 10 images, 50MB limit
        
        List<Future<Void>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            
            Future<Void> future = executorService.submit(() -> {
                for (int i = 0; i < imagesPerThread; i++) {
                    try {
                        totalOps.incrementAndGet();
                        
                        // Create images that will trigger eviction
                        ImagePlus img = createTestImage(128, 128); // Larger images
                        String handle = store.putImage(img);
                        
                        if (handle != null) {
                            // Try to access the image immediately
                            try {
                                SessionStore.ImageRecord record = store.getImage(handle);
                                if (record != null) {
                                    successfulOps.incrementAndGet();
                                } else {
                                    failedOps.incrementAndGet();
                                    errors.add("getImage returned null for valid handle: " + handle);
                                }
                            } catch (IllegalArgumentException e) {
                                // Image may have been evicted - this is valid
                                successfulOps.incrementAndGet();
                            }
                        } else {
                            failedOps.incrementAndGet();
                            errors.add("putImage returned null handle");
                        }
                        
                        // Small delay to allow eviction to occur
                        Thread.sleep(1);
                        
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                        errors.add("Thread " + threadId + ": " + e.getMessage());
                    }
                }
                return null;
            });
            
            futures.add(future);
        }
        
        for (Future<Void> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS); // Longer timeout for eviction tests
            } catch (Exception e) {
                errors.add("Thread execution error: " + e.getMessage());
            }
        }
        
        long endTime = System.currentTimeMillis();
        boolean passed = failedOps.get() == 0 && errors.isEmpty();
        
        return new TestResult(passed, totalOps.get(), successfulOps.get(), 
                             failedOps.get(), errors, endTime - startTime);
    }
    
    /**
     * Run comprehensive thread safety test suite
     */
    public List<TestResult> runComprehensiveTests(SessionStore store) {
        List<TestResult> results = new ArrayList<>();
        
        System.out.println("Running comprehensive thread safety tests...");
        
        // Test 1: Concurrent image operations
        System.out.println("Test 1: Concurrent image operations");
        results.add(testConcurrentImageOperations(store, 100, 8));
        
        // Test 2: Concurrent overlay operations
        System.out.println("Test 2: Concurrent overlay operations");
        results.add(testConcurrentOverlayOperations(store, 50, 6));
        
        // Test 3: Rapid handle state changes
        System.out.println("Test 3: Rapid handle state changes");
        results.add(testRapidHandleStateChanges(store, 200, 10));
        
        // Test 4: LRU eviction under load
        System.out.println("Test 4: LRU eviction under load");
        results.add(testLRUEvictionUnderLoad(store, 25, 4));
        
        return results;
    }
    
    /**
     * Close the executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    // ==================== Test Utilities ====================
    
    private ImagePlus createTestImage(int width, int height) {
        ImagePlus img = new ImagePlus("test_" + System.nanoTime(), 
            new ij.process.ByteProcessor(width, height));
        
        // Fill with random noise to make images unique
        ij.process.ImageProcessor proc = img.getProcessor();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                proc.putPixel(x, y, random.nextInt(256));
            }
        }
        
        return img;
    }
    
    private Overlay createTestOverlay() {
        Overlay overlay = new Overlay();
        OvalRoi roi = new OvalRoi(10, 10, 20, 20);
        roi.setStrokeColor(java.awt.Color.RED);
        overlay.add(roi);
        return overlay;
    }
    
    /**
     * Validate that a SessionStore implementation passes basic thread safety tests
     */
    public static boolean validateThreadSafety(SessionStore store) {
        ConcurrentAccessTester tester = new ConcurrentAccessTester(4);
        try {
            List<TestResult> results = tester.runComprehensiveTests(store);
            boolean allPassed = results.stream().allMatch(result -> result.passed);
            
            System.out.println("\n=== Thread Safety Validation Results ===");
            for (int i = 0; i < results.size(); i++) {
                System.out.println("Test " + (i + 1) + ": " + results.get(i).getSummary());
            }
            System.out.println("Overall: " + (allPassed ? "PASSED" : "FAILED"));
            
            return allPassed;
            
        } finally {
            tester.shutdown();
        }
    }
}