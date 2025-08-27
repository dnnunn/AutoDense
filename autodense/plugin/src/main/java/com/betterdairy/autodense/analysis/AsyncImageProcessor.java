package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.performance.PerformanceOptimizer;
import ij.ImagePlus;
import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Async wrapper for image processing operations that may cause UI freezes.
 * Automatically applies performance optimizations based on image size.
 */
public class AsyncImageProcessor {
    
    // Use the ProgressCallback from PerformanceOptimizer for consistency
    public static final PerformanceOptimizer.ProgressCallback DEFAULT_CALLBACK = 
        new PerformanceOptimizer.ProgressCallback() {
            @Override
            public void onProgress(int percentage, String message) {
                System.out.printf("[%d%%] %s%n", percentage, message);
            }
        };
    
    /**
     * Process large images asynchronously to prevent UI freezes
     */
    public static CompletableFuture<JSONObject> processLargeImageAsync(
            ImagePlus image, 
            String operationName,
            PerformanceOptimizer.ProgressCallback callback) {
        
        return PerformanceOptimizer.AsyncProcessor.processWithProgress(
            image,
            processedImage -> {
                JSONObject result = new JSONObject();
                result.put("operation", operationName);
                result.put("original_size", image.getWidth() + "x" + image.getHeight());
                result.put("processed_size", processedImage.getWidth() + "x" + processedImage.getHeight());
                result.put("processing_strategy", PerformanceOptimizer.SmartProcessor
                    .getRecommendedStrategy(image).name());
                return result;
            },
            callback != null ? callback : DEFAULT_CALLBACK
        );
    }
    
    /**
     * Process plate alignment asynchronously for large images
     */
    public static CompletableFuture<PlateAlignment.AlignmentResult> alignPlatesAsync(
            ImagePlus reference, 
            ImagePlus target,
            PlateAlignment.AlignmentOptions options,
            PerformanceOptimizer.ProgressCallback callback) {
        
        return PerformanceOptimizer.AsyncProcessor.processWithProgress(
            reference,
            refImage -> {
                try {
                    // Use downscaled versions for initial alignment if images are large
                    ImagePlus refProcessing = refImage;
                    ImagePlus targetProcessing = target;
                    
                    if (PerformanceOptimizer.SmartProcessor.requiresDownscaling(reference) ||
                        PerformanceOptimizer.SmartProcessor.requiresDownscaling(target)) {
                        
                        callback.onProgress(25, "Creating downscaled versions for alignment...");
                        refProcessing = PerformanceOptimizer.DownscaledSampling.createAnalysisVersion(reference);
                        targetProcessing = PerformanceOptimizer.DownscaledSampling.createAnalysisVersion(target);
                    }
                    
                    callback.onProgress(75, "Computing alignment transformation...");
                    return PlateAlignment.alignPlates(refProcessing, targetProcessing, options);
                    
                } catch (Exception e) {
                    throw new RuntimeException("Plate alignment failed: " + e.getMessage(), e);
                }
            },
            callback
        );
    }
    
    /**
     * Process X-gal blueness analysis asynchronously for multiple colonies
     */
    public static CompletableFuture<JSONObject> analyzeBluenessAsync(
            ImagePlus image,
            java.util.List<java.awt.geom.Point2D> colonyPositions,
            XGalBluenessAnalyzer.BluenessOptions options,
            PerformanceOptimizer.ProgressCallback callback) {
        
        return PerformanceOptimizer.AsyncProcessor.processAsync(
            image,
            processedImage -> {
                try {
                    JSONObject results = new JSONObject();
                    org.json.JSONArray bluenessResults = new org.json.JSONArray();
                    
                    int totalColonies = colonyPositions.size();
                    for (int i = 0; i < totalColonies; i++) {
                        java.awt.geom.Point2D position = colonyPositions.get(i);
                        
                        if (callback != null) {
                            int progress = (i * 100) / totalColonies;
                            callback.onProgress(progress, 
                                String.format("Analyzing colony %d of %d...", i + 1, totalColonies));
                        }
                        
                        XGalBluenessAnalyzer.BluenessResult blueness = 
                            XGalBluenessAnalyzer.analyzeColonyBlueness(processedImage, position, options);
                        
                        bluenessResults.put(blueness.toJSON()
                            .put("colony_index", i)
                            .put("position_x", position.getX())
                            .put("position_y", position.getY()));
                    }
                    
                    results.put("blueness_analyses", bluenessResults);
                    results.put("total_colonies", totalColonies);
                    
                    if (callback != null) {
                        callback.onProgress(100, "Blueness analysis complete");
                    }
                    
                    return results;
                    
                } catch (Exception e) {
                    JSONObject error = new JSONObject();
                    error.put("error", true);
                    error.put("message", "Blueness analysis failed: " + e.getMessage());
                    return error;
                }
            }
        );
    }
    
    /**
     * Smart processing that automatically chooses optimal strategy
     */
    public static <T> CompletableFuture<T> processOptimallyAsync(
            ImagePlus image,
            Function<ImagePlus, T> fastProcessor,
            Function<ImagePlus, T> fullProcessor,
            PerformanceOptimizer.ProgressCallback callback) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (callback != null) {
                    callback.onProgress(10, "Determining optimal processing strategy...");
                }
                
                return PerformanceOptimizer.SmartProcessor.processOptimally(
                    image, fastProcessor, fullProcessor);
                    
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e);
                }
                throw new RuntimeException("Optimal processing failed", e);
            }
        });
    }
    
    /**
     * Batch process multiple images in parallel with progress tracking
     */
    public static CompletableFuture<java.util.List<JSONObject>> processBatchAsync(
            java.util.List<ImagePlus> images,
            Function<ImagePlus, JSONObject> processor,
            PerformanceOptimizer.ProgressCallback callback) {
        
        return PerformanceOptimizer.AsyncProcessor.processParallel(images, processor)
            .thenApply(results -> {
                if (callback != null) {
                    callback.onProgress(100, 
                        String.format("Batch processing complete: %d images processed", results.size()));
                }
                return results;
            });
    }
}