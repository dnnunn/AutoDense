package com.betterdairy.autodense.service;

import com.betterdairy.autodense.service.AnalysisService.AnalysisConfig;
import com.betterdairy.autodense.service.AnalysisService.AnalysisResult;
import ij.ImagePlus;
import java.util.logging.Logger;

/**
 * Demonstration class showing how to use the new AnalysisService
 * 
 * This class demonstrates the simplified API that Phase III provides
 * for integrating analysis operations into AutoDense applications.
 * 
 * Example Usage Patterns:
 * - Simple gel analysis with default parameters
 * - Custom configuration with specific parameters
 * - Error handling and fallback scenarios
 * - ImageJ integration for visualization
 * 
 * @since AutoDense Phase III
 */
public class AnalysisServiceDemo {
    
    private static final Logger logger = Logger.getLogger(AnalysisServiceDemo.class.getName());
    
    /**
     * Demonstrate simple gel analysis with default configuration
     */
    public static void demonstrateSimpleGelAnalysis(ImagePlus image) {
        logger.info("=== Simple Gel Analysis Demo ===");
        
        try {
            // Create service with defaults
            AnalysisService service = AnalysisService.createDefault();
            
            // Get default gel configuration
            AnalysisConfig config = service.getDefaultConfig("gel");
            
            // Perform analysis
            AnalysisResult result = service.analyzeGel(image, config);
            
            // Display results
            if (result.isSuccess()) {
                logger.info("Analysis successful!");
                logger.info("Detected " + result.getLaneCount() + " lanes");
                logger.info("Detected " + result.getBandCount() + " bands");
                logger.info("Processing time: " + result.getProcessingTimeMs() + "ms");
                logger.info("Used Python bridge: " + result.isUsedPythonBridge());
                
                // Apply overlay to image
                service.displayOverlay(image, result);
                
            } else {
                logger.warning("Analysis failed: " + result.getError());
            }
            
            // Clean up
            service.shutdown();
            
        } catch (Exception e) {
            logger.severe("Demo failed: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrate custom gel analysis configuration
     */
    public static void demonstrateCustomGelAnalysis(ImagePlus image) {
        logger.info("=== Custom Gel Analysis Demo ===");
        
        try {
            AnalysisService service = AnalysisService.createDefault();
            
            // Build custom configuration
            AnalysisConfig config = AnalysisConfig.builder("gel")
                    .parameter("lane_count", 8)
                    .parameter("constant_spacing", true)
                    .parameter("detect_bands", true)
                    .parameter("mw_calibration", true)
                    .parameter("baseline_correction", true)
                    .parameter("polarity", "auto")
                    .timeout(90) // 90 second timeout
                    .build();
            
            // Validate configuration
            if (!service.validateConfig(config)) {
                logger.warning("Configuration validation failed");
                return;
            }
            
            // Perform analysis
            AnalysisResult result = service.analyzeGel(image, config);
            
            if (result.isSuccess()) {
                logger.info("Custom analysis successful!");
                logger.info("Configuration: " + config);
                logger.info("Results: " + result);
                
                // Extract ROIs for further processing
                var laneRois = service.getLaneRois(result);
                var bandRois = service.getBandRois(result);
                logger.info("Extracted " + laneRois.length + " lane ROIs and " + 
                           bandRois.length + " band ROIs");
                
            } else {
                logger.warning("Custom analysis failed: " + result.getError());
            }
            
            service.shutdown();
            
        } catch (Exception e) {
            logger.severe("Custom demo failed: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrate colony analysis
     */
    public static void demonstrateColonyAnalysis(ImagePlus image) {
        logger.info("=== Colony Analysis Demo ===");
        
        try {
            AnalysisService service = AnalysisService.createDefault();
            
            // Get default colony configuration
            AnalysisConfig config = service.getDefaultConfig("colony");
            
            // Perform analysis
            AnalysisResult result = service.analyzeColony(image, config);
            
            if (result.isSuccess()) {
                logger.info("Colony analysis successful!");
                logger.info("Detected " + result.getColonyCount() + " colonies");
                
                // Show colony details
                result.getColonies().forEach(colony -> {
                    logger.info("Colony " + colony.index() + ": " + 
                               colony.colorClass() + " " + colony.sizeClass() + 
                               " (" + String.format("%.1f", colony.diameterMm()) + "mm)");
                });
                
                service.displayOverlay(image, result);
                
            } else {
                logger.warning("Colony analysis failed: " + result.getError());
            }
            
            service.shutdown();
            
        } catch (Exception e) {
            logger.severe("Colony demo failed: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrate error handling and fallback scenarios
     */
    public static void demonstrateErrorHandling(ImagePlus image) {
        logger.info("=== Error Handling Demo ===");
        
        try {
            AnalysisService service = AnalysisService.createDefault();
            
            // Test with invalid configuration
            AnalysisConfig invalidConfig = AnalysisConfig.builder("gel")
                    .parameter("lane_count", -5) // Invalid lane count
                    .parameter("timeout_seconds", 0) // Invalid timeout
                    .build();
            
            if (!service.validateConfig(invalidConfig)) {
                logger.info("Successfully caught invalid configuration");
            }
            
            // Test with Python bridge disabled
            AnalysisConfig legacyConfig = AnalysisConfig.builder("gel")
                    .parameter("lane_count", 4)
                    .usePythonBridge(false) // Force legacy mode
                    .build();
            
            AnalysisResult result = service.analyzeGel(image, legacyConfig);
            logger.info("Legacy fallback result: " + result);
            
            // Test service health
            logger.info("Service healthy: " + service.isServiceHealthy());
            
            service.shutdown();
            
        } catch (Exception e) {
            logger.severe("Error handling demo failed: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrate configuration patterns
     */
    public static void demonstrateConfigurationPatterns() {
        logger.info("=== Configuration Patterns Demo ===");
        
        try {
            AnalysisService service = AnalysisService.createDefault();
            
            // Show default configurations for different modalities
            String[] modalities = {"gel", "colony", "pcr"};
            
            for (String modality : modalities) {
                AnalysisConfig config = service.getDefaultConfig(modality);
                logger.info(modality + " default config: " + config);
                logger.info("  Valid: " + service.validateConfig(config));
            }
            
            // Show builder pattern variations
            AnalysisConfig builderExample1 = AnalysisConfig.builder("gel")
                    .parameter("lane_count", 6)
                    .parameter("constant_spacing", true)
                    .build();
            
            AnalysisConfig builderExample2 = AnalysisConfig.builder("colony")
                    .parameter("min_colony_size", 20)
                    .parameter("max_colony_size", 500)
                    .parameter("xgal_classification", true)
                    .timeout(120)
                    .build();
            
            logger.info("Builder example 1: " + builderExample1);
            logger.info("Builder example 2: " + builderExample2);
            
            service.shutdown();
            
        } catch (Exception e) {
            logger.severe("Configuration demo failed: " + e.getMessage());
        }
    }
    
    /**
     * Main method for running demonstrations
     */
    public static void main(String[] args) {
        logger.info("=== AutoDense AnalysisService Demonstration ===");
        
        // Note: In real usage, you would load an actual image
        // For demo purposes, we'll create a simple placeholder
        ImagePlus demoImage = new ImagePlus("Demo Image", 
                                           new java.awt.image.BufferedImage(800, 600, 
                                           java.awt.image.BufferedImage.TYPE_BYTE_GRAY));
        
        demonstrateConfigurationPatterns();
        demonstrateErrorHandling(demoImage);
        
        // Uncomment these lines when you have actual image data:
        // demonstrateSimpleGelAnalysis(demoImage);
        // demonstrateCustomGelAnalysis(demoImage);
        // demonstrateColonyAnalysis(demoImage);
        
        logger.info("=== Demonstration Complete ===");
    }
}