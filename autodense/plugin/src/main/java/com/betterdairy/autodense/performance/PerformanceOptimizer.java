package com.betterdairy.autodense.performance;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ByteProcessor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Performance optimization utilities for AutoDense image processing.
 * 
 * Addresses Phase 4.3 audit findings:
 * - N² sampling algorithms cause UI freezes
 * - Blocking operations on UI thread
 * - Inefficient pixel access patterns
 * 
 * Key Features:
 * 1. Downscaled image sampling for fast processing
 * 2. Async processing framework for UI responsiveness  
 * 3. Integral-image algorithms for O(1) area calculations
 * 4. Efficient pixel buffer operations
 * 5. Performance monitoring and profiling
 */
public final class PerformanceOptimizer {
    
    // Thread pool for async operations
    private static final ExecutorService ASYNC_EXECUTOR = 
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    
    // Performance thresholds
    private static final int LARGE_IMAGE_THRESHOLD = 2000 * 2000; // 4MP
    private static final int MAX_PROCESSING_SIZE = 1000 * 1000; // 1MP max for real-time
    
    private PerformanceOptimizer() {} // Utility class
    
    /**
     * Downscaled image sampling for fast processing
     */
    public static class DownscaledSampling {
        
        /**
         * Create a downscaled version for fast processing
         * Returns smaller image that preserves key features
         */
        public static ImagePlus createFastProcessingVersion(ImagePlus original, int maxDimension) {
            if (original == null) return null;
            
            int width = original.getWidth();
            int height = original.getHeight();
            
            // Skip downscaling if already small enough
            if (width <= maxDimension && height <= maxDimension) {
                return original.duplicate();
            }
            
            // Calculate scale factor to fit within maxDimension
            double scale = (double) maxDimension / Math.max(width, height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);
            
            // Use ImageJ's built-in scaling (bicubic interpolation)
            ImageProcessor originalProc = original.getProcessor();
            ImageProcessor scaledProc = originalProc.resize(newWidth, newHeight, true);
            
            ImagePlus scaled = new ImagePlus(original.getTitle() + "_fast", scaledProc);
            
            // Preserve calibration scaled appropriately
            if (original.getCalibration() != null) {
                scaled.setCalibration(original.getCalibration().copy());
                scaled.getCalibration().pixelWidth /= scale;
                scaled.getCalibration().pixelHeight /= scale;
            }
            
            return scaled;
        }
        
        /**
         * Create preview version optimized for UI display
         */
        public static ImagePlus createPreviewVersion(ImagePlus original) {
            return createFastProcessingVersion(original, 800);
        }
        
        /**
         * Create processing version optimized for analysis
         */
        public static ImagePlus createAnalysisVersion(ImagePlus original) {
            return createFastProcessingVersion(original, 1200);
        }
        
        /**
         * Scale processing results back to original dimensions
         */
        public static <T> T scaleResultsToOriginal(T results, ImagePlus originalImage, 
                                                 ImagePlus processedImage, 
                                                 Function<T, T> scaleFunction) {
            // Apply scaling transformation to results
            return scaleFunction.apply(results);
        }
    }
    
    /**
     * Integral image algorithms for O(1) area calculations
     */
    public static class IntegralImage {
        private final int[][] integralData;
        private final int width;
        private final int height;
        
        /**
         * Create integral image from ImagePlus (replaces N² sampling)
         */
        public IntegralImage(ImagePlus image) {
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.integralData = new int[height][width];
            
            ImageProcessor ip = image.getProcessor();
            
            // Build integral image - O(n) instead of O(n²) for repeated queries
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = ip.getPixel(x, y);
                    
                    integralData[y][x] = pixel;
                    
                    // Add values from left and top
                    if (x > 0) integralData[y][x] += integralData[y][x-1];
                    if (y > 0) integralData[y][x] += integralData[y-1][x];
                    
                    // Subtract double-counted corner
                    if (x > 0 && y > 0) integralData[y][x] -= integralData[y-1][x-1];
                }
            }
        }
        
        /**
         * Get sum of rectangular area in O(1) time
         * Replaces nested loops for area calculations
         */
        public long getAreaSum(int x1, int y1, int x2, int y2) {
            // Clamp coordinates
            x1 = Math.max(0, Math.min(x1, width - 1));
            y1 = Math.max(0, Math.min(y1, height - 1));
            x2 = Math.max(0, Math.min(x2, width - 1));
            y2 = Math.max(0, Math.min(y2, height - 1));
            
            if (x2 < x1 || y2 < y1) return 0;
            
            // O(1) area calculation using integral image
            long sum = integralData[y2][x2];
            
            if (x1 > 0) sum -= integralData[y2][x1-1];
            if (y1 > 0) sum -= integralData[y1-1][x2];
            if (x1 > 0 && y1 > 0) sum += integralData[y1-1][x1-1];
            
            return sum;
        }
        
        /**
         * Get average intensity of rectangular area in O(1) time
         */
        public double getAreaAverage(int x1, int y1, int x2, int y2) {
            long sum = getAreaSum(x1, y1, x2, y2);
            long pixelCount = (long)(x2 - x1 + 1) * (y2 - y1 + 1);
            return pixelCount > 0 ? (double) sum / pixelCount : 0.0;
        }
        
        /**
         * Fast background estimation using integral image
         * Replaces slow pixel-by-pixel sampling
         */
        public double estimateBackground(int sampleSize) {
            if (sampleSize <= 0) sampleSize = Math.min(100, Math.min(width, height) / 10);
            
            // Sample corners and edges efficiently
            double[] samples = new double[4];
            
            // Corner samples using integral image
            samples[0] = getAreaAverage(0, 0, sampleSize, sampleSize);
            samples[1] = getAreaAverage(width - sampleSize, 0, width - 1, sampleSize);
            samples[2] = getAreaAverage(0, height - sampleSize, sampleSize, height - 1);
            samples[3] = getAreaAverage(width - sampleSize, height - sampleSize, width - 1, height - 1);
            
            // Return median to avoid outliers
            java.util.Arrays.sort(samples);
            return (samples[1] + samples[2]) / 2.0;
        }
    }
    
    /**
     * Async processing framework for UI responsiveness
     */
    public static class AsyncProcessor {
        
        /**
         * Process image asynchronously to avoid UI freezing
         */
        public static <T> CompletableFuture<T> processAsync(ImagePlus image, 
                                                           Function<ImagePlus, T> processor) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return processor.apply(image);
                } catch (Exception e) {
                    throw new RuntimeException("Async processing failed", e);
                }
            }, ASYNC_EXECUTOR);
        }
        
        /**
         * Process with progress callback for long operations
         */
        public static <T> CompletableFuture<T> processWithProgress(ImagePlus image,
                                                                  Function<ImagePlus, T> processor,
                                                                  ProgressCallback callback) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    callback.onProgress(0, "Starting processing...");
                    
                    // For large images, use downscaled processing first
                    if (image.getWidth() * image.getHeight() > LARGE_IMAGE_THRESHOLD) {
                        callback.onProgress(25, "Creating fast processing version...");
                        ImagePlus fastVersion = DownscaledSampling.createAnalysisVersion(image);
                        
                        callback.onProgress(50, "Processing downscaled image...");
                        T result = processor.apply(fastVersion);
                        
                        callback.onProgress(100, "Processing complete");
                        return result;
                    } else {
                        callback.onProgress(50, "Processing image...");
                        T result = processor.apply(image);
                        callback.onProgress(100, "Processing complete");
                        return result;
                    }
                } catch (Exception e) {
                    callback.onError(e);
                    throw new RuntimeException("Async processing failed", e);
                }
            }, ASYNC_EXECUTOR);
        }
        
        /**
         * Parallel processing for multiple images
         */
        public static <T> CompletableFuture<java.util.List<T>> processParallel(
                java.util.List<ImagePlus> images, 
                Function<ImagePlus, T> processor) {
            
            java.util.List<CompletableFuture<T>> futures = images.stream()
                .map(img -> processAsync(img, processor))
                .collect(java.util.stream.Collectors.toList());
            
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList()));
        }
    }
    
    /**
     * Fast pixel buffer operations
     */
    public static class FastPixelOps {
        
        /**
         * Optimized pixel array access (replaces individual getPixel calls)
         */
        public static float[] getPixelArrayFloat(ImagePlus image) {
            ImageProcessor ip = image.getProcessor();
            if (ip instanceof FloatProcessor) {
                return (float[]) ip.getPixels();
            } else {
                return (float[]) ip.convertToFloat().getPixels();
            }
        }
        
        /**
         * Optimized pixel array access for byte images
         */
        public static byte[] getPixelArrayByte(ImagePlus image) {
            ImageProcessor ip = image.getProcessor();
            if (ip instanceof ByteProcessor) {
                return (byte[]) ip.getPixels();
            } else {
                return (byte[]) ip.convertToByte(true).getPixels();
            }
        }
        
        /**
         * Fast pixel manipulation using array operations
         * Replaces nested loops with vectorized operations
         */
        public static void applyFunction(ImagePlus image, PixelFunction function) {
            ImageProcessor ip = image.getProcessor();
            int width = ip.getWidth();
            
            if (ip instanceof FloatProcessor) {
                float[] pixels = (float[]) ip.getPixels();
                for (int i = 0; i < pixels.length; i++) {
                    int x = i % width;
                    int y = i / width;
                    pixels[i] = function.apply(pixels[i], x, y);
                }
            } else if (ip instanceof ByteProcessor) {
                byte[] pixels = (byte[]) ip.getPixels();
                for (int i = 0; i < pixels.length; i++) {
                    int x = i % width;
                    int y = i / width;
                    pixels[i] = (byte) Math.max(0, Math.min(255, 
                        function.apply(pixels[i] & 0xFF, x, y)));
                }
            }
        }
        
        /**
         * Parallel pixel processing for CPU-intensive operations
         */
        public static void applyFunctionParallel(ImagePlus image, PixelFunction function) {
            ImageProcessor ip = image.getProcessor();
            int width = ip.getWidth();
            
            // Use parallel streams for CPU-intensive pixel operations
            if (ip instanceof FloatProcessor) {
                float[] pixels = (float[]) ip.getPixels();
                
                java.util.stream.IntStream.range(0, pixels.length).parallel()
                    .forEach(i -> {
                        int x = i % width;
                        int y = i / width;
                        pixels[i] = function.apply(pixels[i], x, y);
                    });
            }
        }
    }
    
    /**
     * Performance monitoring utilities
     */
    public static class PerformanceMonitor {
        private long startTime;
        private final String operationName;
        
        public PerformanceMonitor(String operationName) {
            this.operationName = operationName;
            this.startTime = System.nanoTime();
        }
        
        public void checkpoint(String message) {
            long elapsed = System.nanoTime() - startTime;
            System.out.printf("[PERF] %s - %s: %.2fms%n", 
                operationName, message, elapsed / 1_000_000.0);
        }
        
        public long finish() {
            long elapsed = System.nanoTime() - startTime;
            System.out.printf("[PERF] %s - TOTAL: %.2fms%n", 
                operationName, elapsed / 1_000_000.0);
            return elapsed;
        }
        
        public static void benchmarkOperation(String name, Runnable operation) {
            PerformanceMonitor monitor = new PerformanceMonitor(name);
            operation.run();
            monitor.finish();
        }
    }
    
    /**
     * Utility interfaces
     */
    @FunctionalInterface
    public interface PixelFunction {
        float apply(float pixelValue, int x, int y);
    }
    
    @FunctionalInterface 
    public interface ProgressCallback {
        void onProgress(int percentage, String message);
        default void onError(Exception e) {
            System.err.println("Processing error: " + e.getMessage());
        }
    }
    
    /**
     * Smart processing strategy selection
     */
    public static class SmartProcessor {
        
        /**
         * Automatically choose optimal processing strategy based on image size
         */
        public static <T> T processOptimally(ImagePlus image, 
                                            Function<ImagePlus, T> fastProcessor,
                                            Function<ImagePlus, T> fullProcessor) {
            int pixelCount = image.getWidth() * image.getHeight();
            
            if (pixelCount > LARGE_IMAGE_THRESHOLD) {
                // Use downscaled processing for large images
                ImagePlus fastVersion = DownscaledSampling.createAnalysisVersion(image);
                return fastProcessor.apply(fastVersion);
            } else {
                // Use full processing for smaller images
                return fullProcessor.apply(image);
            }
        }
        
        /**
         * Determine if image requires downscaling for responsive UI
         */
        public static boolean requiresDownscaling(ImagePlus image) {
            return image.getWidth() * image.getHeight() > MAX_PROCESSING_SIZE;
        }
        
        /**
         * Get recommended processing strategy
         */
        public static ProcessingStrategy getRecommendedStrategy(ImagePlus image) {
            int pixelCount = image.getWidth() * image.getHeight();
            
            if (pixelCount > LARGE_IMAGE_THRESHOLD) {
                return ProcessingStrategy.ASYNC_DOWNSCALED;
            } else if (pixelCount > MAX_PROCESSING_SIZE) {
                return ProcessingStrategy.ASYNC_FULL;
            } else {
                return ProcessingStrategy.SYNC_FULL;
            }
        }
    }
    
    public enum ProcessingStrategy {
        SYNC_FULL,          // Synchronous full resolution
        ASYNC_FULL,         // Asynchronous full resolution  
        ASYNC_DOWNSCALED    // Asynchronous downscaled
    }
    
    /**
     * Cleanup resources
     */
    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }
}