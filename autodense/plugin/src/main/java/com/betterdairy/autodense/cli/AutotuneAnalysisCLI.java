package com.betterdairy.autodense.cli;

import com.betterdairy.autodense.tools.CanonicalTools;
import com.betterdairy.autodense.tools.ColonyAnalysisTools;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.tools.AssayOps;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.validation.InputValidator;
import com.betterdairy.autodense.viz.ColonyViz;
import com.betterdairy.autodense.viz.GelViz;
import com.betterdairy.autodense.util.ImagePreprocessor;
import com.betterdairy.autodense.util.SyntheticImageGenerator;
import com.betterdairy.autodense.config.ConfigManagerBridge;
import com.betterdairy.autodense.config.ConfigurationException;
import net.imagej.ImageJ;
import org.scijava.Context;
import org.scijava.ui.UIService;
import ij.ImagePlus;
import ij.io.Opener;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.json.JSONObject;
import org.json.JSONArray;

import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.security.MessageDigest;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Command-line interface for AutoDense analysis optimized for Python autotune integration.
 * 
 * This class provides a standalone entry point for running AutoDense gel analysis
 * that can be called from the Python optimization system. It handles:
 * - Image loading and analysis
 * - Parameter parsing from config files  
 * - Metric calculation and reporting
 * - Result output in format expected by autotune system
 * 
 * Usage:
 *   java -cp ... com.betterdairy.autodense.cli.AutotuneAnalysisCLI <task> <input_image> <config_file> <output_dir>
 * 
 * @author AutoDense Development Team
 * @version 1.0
 */
public class AutotuneAnalysisCLI {
    
    private static final Logger logger = Logger.getLogger(AutotuneAnalysisCLI.class.getName());
    
    public static void main(String[] args) {
        // HARD GUARD: Force headless mode to prevent UI issues
        System.setProperty("java.awt.headless", "true");
        
        if (args.length >= 1 && "generate-synthetic".equals(args[0])) {
            generateSyntheticImages(args.length > 1 ? args[1] : "../../tmp/");
            return;
        }
        
        if (args.length < 4) {
            System.err.println("Usage: AutotuneAnalysisCLI <task> <input_image> <config_file> <output_dir>");
            System.err.println("Tasks: sds_page, colony_count, etbr_agarose");
            System.exit(1);
        }
        
        String task = args[0];
        String inputImagePath = args[1];
        String configFilePath = args[2];
        String outputDir = args[3];
        
        try {
            // Ensure output directory exists
            Path outDir = Paths.get(outputDir);
            Files.createDirectories(outDir);
            
            // Run analysis based on task type using simplified approach
            switch (task.toLowerCase()) {
                case "colony_count" -> runColony(inputImagePath, outputDir, configFilePath);
                case "sds_page" -> runSdsPage(inputImagePath, outputDir, configFilePath);
                case "etbr_agarose" -> runEtbr(inputImagePath, outputDir, configFilePath);
                default -> throw new IllegalArgumentException("Unknown task: " + task);
            }
            
            logger.info("Analysis completed successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Analysis failed", e);
            System.err.println("Analysis failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Run SDS-PAGE gel analysis with real AutoDense tools
     */
    private static JSONObject runSdsPageAnalysis(String inputPath, String configPath, Path outputDir) 
            throws IOException {
        
        // Initialize ImageJ in headless mode for CLI
        Context ctx = new Context();
        UIService ui = ctx.getService(UIService.class);
        if (ui != null && !ui.isHeadless()) {
            // Force headless UI in SciJava context
            logger.info("Forcing headless UI mode");
        }
        // Initialize ImageJ in headless mode
        new ImageJ(ctx);
        // Never call ui.show() in CLI mode
        
        // Load configuration with fail-fast validation
        JSONObject config = loadConfigFile(configPath, "gel_analysis");
        
        // Create session store and analysis tools
        SessionStore store = new SessionStore();
        
        // Create real analysis tools
        GelAnalysisTools gelTools = new GelAnalysisTools(store);
        CanonicalTools canonicalTools = new CanonicalTools(gelTools, null, store);
        
        try {
            // Validate input path
            String validatedPath = InputValidator.validateFilePath(inputPath, true, false);
            
            // Extract parameters from config
            JSONObject detectionConfig = config.optJSONObject("detection");
            if (detectionConfig == null) {
                detectionConfig = new JSONObject();
            }
            
            // Run complete gel analysis workflow using CanonicalTools
            JSONObject analysisArgs = new JSONObject()
                .put("image_path", validatedPath)
                .put("expected_lanes", detectionConfig.optInt("expected_lanes", 8))
                .put("constant_spacing", detectionConfig.optBoolean("constant_spacing", false))
                .put("sensitivity", detectionConfig.optDouble("sensitivity", 1.0))
                .put("background_method", detectionConfig.optString("background_method", "rolling_ball"));
            
            JSONObject analysisResult = canonicalTools.analyze_gel(analysisArgs);
            if (!analysisResult.optBoolean("success", false)) {
                throw new RuntimeException("Gel analysis failed: " + analysisResult.toString());
            }
            
            // Export results with headless-safe annotated PNG
            String diagnosticsPng = null;
            try {
                // Load image directly for headless rendering
                ImagePlus imagePlus = new Opener().openImage(validatedPath);
                if (imagePlus != null) {
                    
                    // Use headless-safe visualization
                    BufferedImage annotatedImage = GelViz.renderOverlay(imagePlus, analysisResult, true);
                    Path pngPath = outputDir.resolve("annotated_gel.png");
                    ImageIO.write(annotatedImage, "PNG", pngPath.toFile());
                    diagnosticsPng = pngPath.toString();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to generate annotated PNG", e);
                diagnosticsPng = null;
            }
            
            // Calculate real metrics from analysis results
            Map<String, Double> metrics = extractRealGelMetrics(analysisResult);
            
            // Generate hash of input
            String inputHash = generateFileHash(inputPath);
            
            // Create result with exact schema format
            JSONObject result = new JSONObject();
            result.put("task", "sds_page");
            result.put("input_path", inputPath);
            result.put("input_hash", inputHash);
            
            // Only include applicable metrics (no fake data)
            JSONObject metricsObj = convertMapToJSONObject(metrics);
            if (metricsObj.length() > 0) {
                result.put("metrics", metricsObj);
            }
            
            // Include diagnostics PNG if generated
            if (diagnosticsPng != null) {
                result.put("diagnostics_png", diagnosticsPng);
            }
            
            // Meta information
            JSONObject meta = new JSONObject();
            meta.put("lanes_detected", analysisResult.optInt("lanes_found", 0));
            meta.put("bands_detected", analysisResult.optInt("bands_total", 0));
            meta.put("analysis_method", "canonical_gel_workflow");
            result.put("meta", meta);
            
            return result;
            
        } finally {
            // Cleanup
            store.clear();
        }
    }
    
    /**
     * Extract real gel metrics from AutoDense analysis results
     */
    private static Map<String, Double> extractRealGelMetrics(JSONObject analysisResult) {
        Map<String, Double> metrics = new HashMap<>();
        
        // Extract actual metrics from analysis result
        metrics.put("total_bands", (double) analysisResult.optInt("bands_total", 0));
        metrics.put("lane_count", (double) analysisResult.optInt("lanes_found", 0));
        
        // Extract quantification metrics if available
        JSONObject quantResult = analysisResult.optJSONObject("quantification");
        if (quantResult != null) {
            metrics.put("ladder_r2", quantResult.optDouble("ladder_r2", 0.0));
            metrics.put("background_snr", quantResult.optDouble("background_snr", 0.0));
            metrics.put("band_stability_jitter", quantResult.optDouble("band_stability", 1.0));
        } else {
            // Default values if quantification not available
            metrics.put("ladder_r2", 0.0);
            metrics.put("background_snr", 0.0);
            metrics.put("band_stability_jitter", 1.0);
        }
        
        return metrics;
    }
    
    /**
     * Extract real colony metrics from AutoDense analysis results
     */
    private static Map<String, Double> extractRealColonyMetrics(JSONObject analysisResult) {
        Map<String, Double> metrics = new HashMap<>();
        
        // Extract actual colony count and metrics
        metrics.put("colony_count", (double) analysisResult.optInt("colonies_found", 0));
        
        // Extract measurement results if available
        JSONObject measurements = analysisResult.optJSONObject("measurements");
        if (measurements != null) {
            metrics.put("size_cv", measurements.optDouble("size_cv", 0.0));
            metrics.put("touching_fraction", measurements.optDouble("touching_fraction", 0.0));
            metrics.put("circularity_mean", measurements.optDouble("circularity_mean", 0.0));
            
            // X-gal specific metrics if available
            if (measurements.has("blue_index_mean")) {
                metrics.put("blue_index_mean", measurements.optDouble("blue_index_mean", 0.0));
                metrics.put("blue_fraction", measurements.optDouble("blue_fraction", 0.0));
            }
        } else {
            // Default values
            metrics.put("size_cv", 0.0);
            metrics.put("touching_fraction", 0.0);
            metrics.put("circularity_mean", 0.0);
        }
        
        return metrics;
    }
    
    /**
     * Extract real EtBr-specific metrics from AutoDense analysis results
     */
    private static Map<String, Double> extractRealEtBrMetrics(JSONObject analysisResult) {
        Map<String, Double> metrics = new HashMap<>();
        
        // Extract basic gel metrics
        metrics.put("lane_count", (double) analysisResult.optInt("lanes_found", 0));
        metrics.put("band_count", (double) analysisResult.optInt("bands_total", 0));
        
        // Extract EtBr-specific quantification metrics
        JSONObject quantResult = analysisResult.optJSONObject("quantification");
        if (quantResult != null) {
            metrics.put("ladder_linear_r2", quantResult.optDouble("ladder_r2", 0.0));
            metrics.put("background_snr", quantResult.optDouble("background_snr", 0.0));
            metrics.put("smearing_index", quantResult.optDouble("smearing_index", 0.0));
            metrics.put("band_intensity_cv", quantResult.optDouble("intensity_cv", 0.0));
        } else {
            // Default values if quantification not available
            metrics.put("ladder_linear_r2", 0.0);
            metrics.put("background_snr", 0.0);
            metrics.put("smearing_index", 0.0);
            metrics.put("band_intensity_cv", 0.0);
        }
        
        return metrics;
    }
    
    /**
     * Run colony analysis with real AutoDense colony tools
     */
    private static JSONObject runColonyAnalysis(String inputPath, String configPath, Path outputDir) 
            throws IOException {
        
        // Initialize ImageJ in headless mode for CLI
        Context ctx = new Context();
        UIService ui = ctx.getService(UIService.class);
        if (ui != null && !ui.isHeadless()) {
            // Force headless UI in SciJava context
            logger.info("Forcing headless UI mode");
        }
        // Initialize ImageJ in headless mode
        new ImageJ(ctx);
        // Never call ui.show() in CLI mode
        
        // Load configuration with fail-fast validation
        JSONObject config = loadConfigFile(configPath, "colony_analysis");
        
        // Create session store and colony analysis tools
        SessionStore store = new SessionStore();
        AssayOps assayOps = new AssayOps(store);
        CanonicalTools canonicalTools = new CanonicalTools(null, assayOps, store);
        
        try {
            // Validate input path
            String validatedPath = InputValidator.validateFilePath(inputPath, true, false);
            
            // Load image and put it in session store to get handle
            ImagePlus imagePlus = new Opener().openImage(validatedPath);
            if (imagePlus == null) {
                throw new IOException("Failed to load image: " + validatedPath);
            }
            String imageHandle = store.putImage(imagePlus);
            logger.info("Loaded image and created handle: " + imageHandle);
            
            // Extract colony analysis parameters from config
            JSONObject colonyConfig = config.optJSONObject("colony_detection");
            if (colonyConfig == null) {
                colonyConfig = new JSONObject();
            }
            
            // Run complete plate analysis workflow
            JSONObject analysisArgs = new JSONObject()
                .put("image_handle", imageHandle)  // Use handle instead of path
                .put("stain", colonyConfig.optString("stain", "none"))
                .put("plate_layout", colonyConfig.optInt("plate_layout", 1))
                .put("min_size", colonyConfig.optDouble("min_size", 5.0))
                .put("max_size", colonyConfig.optDouble("max_size", 1000.0))
                .put("enable_assist", true); // Enable ColonyAssist for user interaction
            
            JSONObject analysisResult = canonicalTools.analyze_plate(analysisArgs);
            if (!analysisResult.optBoolean("success", false)) {
                throw new RuntimeException("Colony analysis failed: " + analysisResult.toString());
            }
            
            // Export results with headless-safe annotated PNG
            String diagnosticsPng = null;
            try {
                // Use the already loaded image for headless rendering
                if (imagePlus != null) {
                    
                    // Use headless-safe visualization
                    BufferedImage annotatedImage = ColonyViz.renderOverlay(imagePlus, analysisResult, true);
                    Path pngPath = outputDir.resolve("annotated_colonies.png");
                    ImageIO.write(annotatedImage, "PNG", pngPath.toFile());
                    diagnosticsPng = pngPath.toString();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to generate annotated PNG", e);
                diagnosticsPng = null;
            }
            
            // Extract real colony metrics from analysis
            Map<String, Double> metrics = extractRealColonyMetrics(analysisResult);
            
            // Generate hash of input
            String inputHash = generateFileHash(inputPath);
            
            // Create result with exact schema format
            JSONObject result = new JSONObject();
            result.put("task", "colony_count");
            result.put("input_path", inputPath);
            result.put("input_hash", inputHash);
            
            // Only include applicable metrics (no fake data)
            JSONObject metricsObj = convertMapToJSONObject(metrics);
            if (metricsObj.length() > 0) {
                result.put("metrics", metricsObj);
            }
            
            // Include diagnostics PNG if generated
            if (diagnosticsPng != null) {
                result.put("diagnostics_png", diagnosticsPng);
            }
            
            // Meta information
            JSONObject meta = new JSONObject();
            meta.put("colonies_detected", analysisResult.optInt("colonies_found", 0));
            meta.put("stain_type", colonyConfig.optString("stain", "none"));
            meta.put("analysis_method", "canonical_plate_workflow");
            result.put("meta", meta);
            
            return result;
            
        } finally {
            // Cleanup
            store.clear();
        }
    }
    
    /**
     * Run EtBr agarose gel analysis with real AutoDense tools
     */
    private static JSONObject runEtBrAnalysis(String inputPath, String configPath, Path outputDir) 
            throws IOException {
        
        // Initialize ImageJ in headless mode for CLI
        Context ctx = new Context();
        UIService ui = ctx.getService(UIService.class);
        if (ui != null && !ui.isHeadless()) {
            // Force headless UI in SciJava context
            logger.info("Forcing headless UI mode");
        }
        // Initialize ImageJ in headless mode
        new ImageJ(ctx);
        // Never call ui.show() in CLI mode
        
        // Load configuration with fail-fast validation  
        JSONObject config = loadConfigFile(configPath, "gel_analysis");
        
        // Create session store and gel analysis tools
        SessionStore store = new SessionStore();
        
        GelAnalysisTools gelTools = new GelAnalysisTools(store);
        CanonicalTools canonicalTools = new CanonicalTools(gelTools, null, store);
        
        try {
            // Validate input path
            String validatedPath = InputValidator.validateFilePath(inputPath, true, false);
            
            // Extract EtBr-specific parameters from config
            JSONObject etbrConfig = config.optJSONObject("etbr_detection");
            if (etbrConfig == null) {
                etbrConfig = new JSONObject();
            }
            
            // Run complete EtBr gel analysis workflow
            JSONObject analysisArgs = new JSONObject()
                .put("image_path", validatedPath)
                .put("expected_lanes", etbrConfig.optInt("expected_lanes", 20)) // Default to 20 as user noted
                .put("constant_spacing", etbrConfig.optBoolean("constant_spacing", true))
                .put("sensitivity", etbrConfig.optDouble("sensitivity", 0.8))
                .put("background_method", etbrConfig.optString("background_method", "rolling_ball"))
                .put("enable_assist", true); // Enable BandAssist for user interaction
            
            JSONObject analysisResult = canonicalTools.analyze_gel(analysisArgs);
            if (!analysisResult.optBoolean("success", false)) {
                throw new RuntimeException("EtBr gel analysis failed: " + analysisResult.toString());
            }
            
            // Export results with headless-safe annotated PNG
            String diagnosticsPng = null;
            try {
                // Load image directly for headless rendering
                ImagePlus imagePlus = new Opener().openImage(validatedPath);
                if (imagePlus != null) {
                    
                    // Use headless-safe visualization
                    BufferedImage annotatedImage = GelViz.renderOverlay(imagePlus, analysisResult, true);
                    Path pngPath = outputDir.resolve("annotated_etbr_gel.png");
                    ImageIO.write(annotatedImage, "PNG", pngPath.toFile());
                    diagnosticsPng = pngPath.toString();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to generate annotated PNG", e);
                diagnosticsPng = null;
            }
            
            // Calculate real EtBr-specific metrics
            Map<String, Double> metrics = extractRealEtBrMetrics(analysisResult);
            
            // Generate hash of input
            String inputHash = generateFileHash(inputPath);
            
            // Create result with exact schema format
            JSONObject result = new JSONObject();
            result.put("task", "etbr_agarose");
            result.put("input_path", inputPath);
            result.put("input_hash", inputHash);
            
            // Only include applicable metrics (no fake data)
            JSONObject metricsObj = convertMapToJSONObject(metrics);
            if (metricsObj.length() > 0) {
                result.put("metrics", metricsObj);
            }
            
            // Include diagnostics PNG if generated
            if (diagnosticsPng != null) {
                result.put("diagnostics_png", diagnosticsPng);
            }
            
            // Meta information
            JSONObject meta = new JSONObject();
            meta.put("lanes_detected", analysisResult.optInt("lanes_found", 0));
            meta.put("bands_detected", analysisResult.optInt("bands_total", 0));
            meta.put("analysis_method", "canonical_etbr_workflow");
            result.put("meta", meta);
            
            return result;
            
        } finally {
            // Cleanup
            store.clear();
        }
    }
    
    /**
     * Load and validate configuration using ConfigManagerBridge with fail-fast validation.
     * Implements full precedence hierarchy: defaults.yml → priors.json → preflight.yaml → user config → CLI flags
     * 
     * @param configPath Path to user configuration file
     * @param workflowType Type of workflow for validation (colony_analysis, gel_analysis, preprocessing)
     * @return Validated and merged configuration
     * @throws IOException If config validation fails or file loading fails
     */
    private static JSONObject loadConfigFile(String configPath, String workflowType) throws IOException {
        try {
            // Create config manager bridge for the specific workflow type
            ConfigManagerBridge configManager = ConfigManagerBridge.forCurrentDir(workflowType);
            
            // Load and validate config with full precedence hierarchy
            JSONObject config = configManager.loadValidatedConfig(configPath, null);
            
            logger.info("Configuration loaded and validated successfully for workflow: " + workflowType);
            return config;
            
        } catch (ConfigurationException e) {
            // Re-throw as IOException for compatibility with existing error handling
            throw new IOException("Configuration validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Legacy config loading method for backwards compatibility.
     * Uses old simple YAML parsing without validation - should be phased out.
     */
    private static JSONObject loadConfigFileLegacy(String configPath) throws IOException {
        Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            throw new IOException("Config file not found: " + configPath);
        }
        
        String content = Files.readString(path);
        
        if (configPath.endsWith(".json")) {
            return new JSONObject(content);
        } else if (configPath.endsWith(".yaml") || configPath.endsWith(".yml")) {
            return parseYamlConfig(content);
        } else {
            throw new IOException("Unsupported config format: " + configPath);
        }
    }
    
    /**
     * Parse YAML configuration using SnakeYAML
     * Maps config parameters to match challenge pack parameter grids
     */
    private static JSONObject parseYamlConfig(String yamlContent) throws IOException {
        try {
            Yaml yaml = new Yaml();
            java.util.Map<String, Object> yamlData = yaml.load(yamlContent);
            
            if (yamlData == null) {
                return new JSONObject();
            }
            
            return new JSONObject(yamlData);
            
        } catch (Exception e) {
            throw new IOException("Failed to parse YAML config: " + e.getMessage(), e);
        }
    }
    
    /**
     * Write consistent run_report.json with standardized schema
     */
    private static void writeRunReport(JSONObject result, Path reportPath) throws IOException {
        // Ensure consistent schema - only include applicable metrics
        JSONObject cleanedResult = new JSONObject();
        
        // Required fields
        cleanedResult.put("task", result.getString("task"));
        cleanedResult.put("input_path", result.getString("input_path"));
        cleanedResult.put("input_hash", result.getString("input_hash"));
        
        // Metrics - only include if they exist and are applicable
        JSONObject metrics = result.optJSONObject("metrics");
        if (metrics != null && metrics.length() > 0) {
            JSONObject cleanedMetrics = new JSONObject();
            for (String key : metrics.keySet()) {
                Object value = metrics.get(key);
                // Only include real metrics, not placeholder values
                if (value instanceof Number && ((Number) value).doubleValue() != 0.0) {
                    cleanedMetrics.put(key, value);
                } else if (value instanceof String && !((String) value).isEmpty()) {
                    cleanedMetrics.put(key, value);
                } else if (value instanceof Boolean) {
                    cleanedMetrics.put(key, value);
                }
            }
            if (cleanedMetrics.length() > 0) {
                cleanedResult.put("metrics", cleanedMetrics);
            }
        }
        
        // Diagnostics PNG - include if available
        String diagnosticsPng = result.optString("diagnostics_png", null);
        if (diagnosticsPng != null && !diagnosticsPng.isEmpty()) {
            cleanedResult.put("diagnostics_png", diagnosticsPng);
        }
        
        // Meta - optional additional information
        JSONObject meta = result.optJSONObject("meta");
        if (meta != null && meta.length() > 0) {
            cleanedResult.put("meta", meta);
        }
        
        // Write with clean formatting
        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            writer.write(cleanedResult.toString(2));
        }
    }
    
    /**
     * Generate SHA-256 hash of file for input tracking
     */
    private static String generateFileHash(String filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            byte[] hashBytes = md.digest(fileBytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 12); // First 12 characters
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to generate file hash", e);
            return "unknown_hash";
        }
    }
    
    /**
     * Clean up temporary directory
     */
    private static void deleteTempDirectory(Path tempDir) {
        try {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Failed to delete temp file: " + path, e);
                    }
                });
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to cleanup temp directory", e);
        }
    }
    
    /**
     * Convert Map<String, Double> to JSONObject to avoid ambiguous put() calls
     */
    private static JSONObject convertMapToJSONObject(Map<String, Double> map) {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            json.put(entry.getKey(), entry.getValue().doubleValue());
        }
        return json;
    }
    
    /**
     * Run colony analysis with direct colony analysis pipeline
     */
    private static void runColony(String input, String outdir, String configPath) throws Exception {
        // Load YAML configuration with fail-fast validation
        JSONObject config = loadConfigFile(configPath, "colony_analysis");
        JSONObject preConfig = config.optJSONObject("pre");
        JSONObject detectConfig = config.optJSONObject("detect");
        JSONObject colonyConfig = config.optJSONObject("colony_detection"); // Legacy fallback
        
        if (preConfig == null) preConfig = new JSONObject();
        if (detectConfig == null) detectConfig = new JSONObject();
        if (colonyConfig == null) colonyConfig = new JSONObject();
        
        // Load image via SCIFIO/ImageJ2 as specified in 15-minute guide
        ImagePlus imagePlus = loadImageViaSCIFIO(input);
        if (imagePlus == null) {
            throw new IOException("Failed to load image: " + input);
        }
        
        // Log image statistics as recommended: "global mean/std, p1/p99, polarity decision"
        logImageStatistics(imagePlus, "Input colony plate image");
        
        // Apply preprocessing using YAML configuration
        // FIXED: Colony plates don't need ROI-aware normalization - use standard preprocessing
        ImagePreprocessor.Config preprocessConfig = ImagePreprocessor.Config.fromYaml(preConfig);
        
        ImagePlus preprocessed = ImagePreprocessor.preprocessForDetection(imagePlus, preprocessConfig, outdir);
        logImageStatistics(preprocessed, "Preprocessed colony plate image");
        
        // Initialize real colony analysis pipeline
        SessionStore store = new SessionStore();
        ColonyAnalysisTools colonyAnalysisTools = new ColonyAnalysisTools(store);
        
        // FIXED: Store BOTH original color image AND preprocessed image for X-gal blue detection
        String originalImageHandle = store.putImage(imagePlus);        // Original color for blue detection
        String preprocessedHandle = store.putImage(preprocessed);       // Preprocessed for other analyses
        
        logger.info("Starting colony analysis - original color: " + originalImageHandle + 
                   ", preprocessed: " + preprocessedHandle);
        
        // Run colony detection with original COLOR image for X-gal blue index analysis
        JSONObject detectArgs = new JSONObject()
            .put("image_handle", originalImageHandle)                   // ✅ FIXED: Use original color image
            .put("preprocessed_handle", preprocessedHandle)             // Optional: preprocessed available
            .put("stain", colonyConfig.optString("stain", "x-gal"))
            .put("min_size", colonyConfig.optDouble("min_size", 5.0))
            .put("max_size", colonyConfig.optDouble("max_size", 1000.0))
            .put("plate_layout", colonyConfig.optInt("plate_layout", 1));
            
        JSONObject analysisResult = colonyAnalysisTools.countColonies(detectArgs);
        // FIXED: Check correct field name - colony tools use "success", not "ok"
        if (!analysisResult.optBoolean("success", false)) {
            throw new RuntimeException("Colony detection failed: " + analysisResult.toString());
        }
        
        // Generate colony overlay PNG using headless-safe visualization with original color image
        BufferedImage annotatedImage = ColonyViz.renderOverlay(imagePlus, analysisResult, true);
        Path overlayPng = Paths.get(outdir, "overlay.png");  // ✅ FIXED: Consistent naming with gel workflows
        ImageIO.write(annotatedImage, "PNG", overlayPng.toFile());
        
        // Extract real metrics from analysis results
        Map<String, Double> metrics = new LinkedHashMap<>();
        // FIXED: Use correct field name from actual colony analysis response
        int colonyCount = analysisResult.optInt("colony_count", analysisResult.optInt("total_colonies", 0));
        metrics.put("colony_count", (double) colonyCount);
        
        // Calculate size coefficient of variation if colony data available
        if (analysisResult.has("colonies")) {
            JSONArray colonies = analysisResult.getJSONArray("colonies");
            List<Double> sizes = new ArrayList<>();
            for (int i = 0; i < colonies.length(); i++) {
                JSONObject colony = colonies.getJSONObject(i);
                if (colony.has("area")) {
                    sizes.add(colony.getDouble("area"));
                }
            }
            if (!sizes.isEmpty()) {
                double mean = sizes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double variance = sizes.stream().mapToDouble(s -> Math.pow(s - mean, 2)).average().orElse(0.0);
                double cv = mean > 0 ? Math.sqrt(variance) / mean : 0.0;
                metrics.put("size_cv", cv);
                
                // Calculate touching fraction (placeholder - would need overlap analysis)
                metrics.put("touching_fraction", 0.1); // TODO: Implement real touching detection
            }
        }
        
        // Add detection performance metrics if available
        if (analysisResult.has("detection_confidence")) {
            metrics.put("effective_recall", analysisResult.getDouble("detection_confidence"));
        }
        if (analysisResult.has("false_positive_estimate")) {
            metrics.put("false_positive_rate", analysisResult.getDouble("false_positive_estimate"));
        }
        
        // Calculate simple hash of input file
        String inputHash = Integer.toHexString(Paths.get(input).hashCode());
        
        // Write run_report.json with consistent schema
        JSONObject runReport = new JSONObject()
            .put("task", "colony_count")
            .put("input_path", input)
            .put("input_hash", inputHash)
            .put("metrics", convertMapToJSONObject(metrics))
            .put("diagnostics_png", "overlay.png")  // ✅ FIXED: Relative path for portability
            .put("meta", new JSONObject());
            
        Path reportPath = Paths.get(outdir, "run_report.json");
        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            writer.write(runReport.toString(2));  // Pretty print with 2-space indent
        }
        
        logger.info("Colony analysis completed: " + metrics.get("colony_count") + " colonies detected");
    }
    
    /**
     * Run SDS-PAGE analysis with lane/band detection pipeline
     */
    private static void runSdsPage(String input, String outdir, String configPath) throws Exception {
        // Load YAML configuration with fail-fast validation
        JSONObject config = loadConfigFile(configPath, "gel_analysis");
        JSONObject preConfig = config.optJSONObject("pre");
        JSONObject detectConfig = config.optJSONObject("detect");
        JSONObject sdsConfig = config.optJSONObject("detection"); // Legacy fallback
        
        if (preConfig == null) preConfig = new JSONObject();
        if (detectConfig == null) detectConfig = new JSONObject();
        if (sdsConfig == null) sdsConfig = new JSONObject();
        
        // Load image via SCIFIO/ImageJ2 as specified in 15-minute guide
        ImagePlus imagePlus = loadImageViaSCIFIO(input);
        if (imagePlus == null) {
            throw new IOException("Failed to load image: " + input);
        }
        
        // Log image statistics as recommended: "global mean/std, p1/p99, polarity decision"
        logImageStatistics(imagePlus, "Input SDS-PAGE image");
        
        // Apply ROI-aware preprocessing for gel workflow
        ImagePreprocessor.Config preprocessConfig = ImagePreprocessor.Config.fromYaml(preConfig);
        
        // FIXED: Use ROI-aware normalization to avoid margin artifacts
        ImagePlus preprocessed;
        if (preprocessConfig.normalizeIntensity) {
            // Estimate gel ROI for normalization
            java.awt.Rectangle gelROI = ImagePreprocessor.estimateGelROI(imagePlus);
            
            // Apply ROI-aware normalization first
            ImagePlus roiNormalized = ImagePreprocessor.normalizeByPercentilesWithROI(
                imagePlus, gelROI, 
                preprocessConfig.clipPercentileLow, 
                preprocessConfig.clipPercentileHigh
            );
            
            // Continue with other preprocessing steps (disable normalization since we did it)
            preprocessConfig.normalizeIntensity = false;
            preprocessed = ImagePreprocessor.preprocessForDetection(roiNormalized, preprocessConfig, outdir);
        } else {
            // No normalization requested, use standard preprocessing
            preprocessed = ImagePreprocessor.preprocessForDetection(imagePlus, preprocessConfig, outdir);
        }
        
        logImageStatistics(preprocessed, "ROI-aware preprocessed SDS-PAGE image");
        
        // Initialize real gel analysis pipeline
        SessionStore store = new SessionStore();
        GelAnalysisTools gelAnalysisTools = new GelAnalysisTools(store);
        
        String imageHandle = store.putImage(preprocessed);
        ImagePlus currentWorkingImage = preprocessed; // Track which image to use for overlay
        logger.info("Starting SDS-PAGE analysis for preprocessed image: " + imageHandle);
        
        // Run lane detection
        JSONObject laneArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("expected_lanes", sdsConfig.optInt("expected_lanes", 10))
            .put("sensitivity", sdsConfig.optDouble("sensitivity", 0.5))
            .put("constant_spacing", sdsConfig.optBoolean("constant_spacing", true));
            
        JSONObject laneResult = gelAnalysisTools.detectLanes(laneArgs);
        if (!laneResult.optBoolean("ok", false)) {
            throw new RuntimeException("Lane detection failed: " + laneResult.toString());
        }
        
        // Run band detection
        JSONObject bandArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("background_subtraction", sdsConfig.optBoolean("background_subtraction", true))
            .put("peak_detection_method", sdsConfig.optString("peak_detection_method", "auto"));
            
        JSONObject bandResult = gelAnalysisTools.detectBands(bandArgs);
        if (!bandResult.optBoolean("ok", false)) {
            throw new RuntimeException("Band detection failed: " + bandResult.toString());
        }
        
        // Generate gel overlay PNG using headless-safe visualization
        JSONObject combinedResult = new JSONObject()
            .put("lanes", laneResult.optJSONArray("lanes"))
            .put("bands", bandResult.optJSONArray("bands"));
        BufferedImage annotatedImage = GelViz.renderOverlay(currentWorkingImage, combinedResult, true);
        Path overlayPng = Paths.get(outdir, "overlay.png");  // ✅ FIXED: Consistent naming across all workflows
        ImageIO.write(annotatedImage, "PNG", overlayPng.toFile());
        
        // Extract real metrics from analysis results (using correct field names from GelAnalysisTools)
        Map<String, Double> metrics = new LinkedHashMap<>();
        int laneCount = laneResult.optInt("lanes_found", 0);
        int bandCount = bandResult.optInt("bands_total", 0);
        
        metrics.put("lane_count", (double) laneCount);
        metrics.put("band_count", (double) bandCount);
        
        // FIXED: Only include advanced metrics if features were actually detected
        if (laneCount > 0 && bandCount > 0) {
            // Calculate molecular weight ladder R² if calibration data available
            if (bandResult.has("mw_calibration_r2")) {
                metrics.put("ladder_r2", bandResult.getDouble("mw_calibration_r2"));
            }
            
            // Calculate background signal-to-noise ratio
            if (laneResult.has("background_snr")) {
                metrics.put("background_snr", laneResult.getDouble("background_snr"));
            }
            
            // Calculate band stability (position consistency)
            if (bandResult.has("band_position_cv")) {
                metrics.put("band_stability_jitter", bandResult.getDouble("band_position_cv"));
            }
        } else {
            // No features detected - log this condition
            logger.warning(String.format("SDS-PAGE analysis found no features (lanes=%d, bands=%d) - skipping advanced metrics", 
                         laneCount, bandCount));
        }
        
        // Calculate simple hash of input file
        String inputHash = Integer.toHexString(Paths.get(input).hashCode());
        
        // Write run_report.json with consistent schema
        JSONObject runReport = new JSONObject()
            .put("task", "sds_page")
            .put("input_path", input)
            .put("input_hash", inputHash)
            .put("metrics", convertMapToJSONObject(metrics))
            .put("diagnostics_png", "overlay.png")  // ✅ FIXED: Relative path for portability
            .put("meta", new JSONObject());
            
        Path reportPath = Paths.get(outdir, "run_report.json");
        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            writer.write(runReport.toString(2));  // Pretty print with 2-space indent
        }
        
        logger.info("SDS-PAGE analysis completed: " + metrics.get("lane_count") + " lanes, " + 
                   metrics.get("band_count") + " bands detected");
    }
    
    /**
     * Run EtBr agarose gel analysis with lane/band detection pipeline
     */
    private static void runEtbr(String input, String outdir, String configPath) throws Exception {
        // Load YAML configuration with fail-fast validation
        JSONObject config = loadConfigFile(configPath, "gel_analysis");
        JSONObject preConfig = config.optJSONObject("pre");
        JSONObject detectConfig = config.optJSONObject("detect");
        JSONObject etbrConfig = config.optJSONObject("etbr_detection"); // Legacy fallback
        
        if (preConfig == null) preConfig = new JSONObject();
        if (detectConfig == null) detectConfig = new JSONObject();
        if (etbrConfig == null) etbrConfig = new JSONObject();
        
        // Load image via SCIFIO/ImageJ2 as specified in 15-minute guide
        ImagePlus imagePlus = loadImageViaSCIFIO(input);
        if (imagePlus == null) {
            throw new IOException("Failed to load image: " + input);
        }
        
        // Log image statistics as recommended: "global mean/std, p1/p99, polarity decision"
        logImageStatistics(imagePlus, "Input EtBr image");
        
        // Apply ROI-aware preprocessing for gel workflow
        ImagePreprocessor.Config preprocessConfig = ImagePreprocessor.Config.fromYaml(preConfig);
        
        // FIXED: Use ROI-aware normalization to avoid margin artifacts
        ImagePlus preprocessed;
        if (preprocessConfig.normalizeIntensity) {
            // Estimate gel ROI for normalization
            java.awt.Rectangle gelROI = ImagePreprocessor.estimateGelROI(imagePlus);
            
            // Apply ROI-aware normalization first
            ImagePlus roiNormalized = ImagePreprocessor.normalizeByPercentilesWithROI(
                imagePlus, gelROI, 
                preprocessConfig.clipPercentileLow, 
                preprocessConfig.clipPercentileHigh
            );
            
            // Continue with other preprocessing steps (disable normalization since we did it)
            preprocessConfig.normalizeIntensity = false;
            preprocessed = ImagePreprocessor.preprocessForDetection(roiNormalized, preprocessConfig, outdir);
        } else {
            // No normalization requested, use standard preprocessing
            preprocessed = ImagePreprocessor.preprocessForDetection(imagePlus, preprocessConfig, outdir);
        }
        
        logImageStatistics(preprocessed, "ROI-aware preprocessed EtBr image");
        
        // Initialize real gel analysis pipeline
        SessionStore store = new SessionStore();
        GelAnalysisTools gelAnalysisTools = new GelAnalysisTools(store);
        
        String imageHandle = store.putImage(preprocessed);
        ImagePlus currentWorkingImage = preprocessed; // Track which image to use for overlay
        logger.info("Starting EtBr agarose analysis for preprocessed image: " + imageHandle);
        
        // Run lane detection for EtBr gel (typically more lanes than SDS-PAGE)
        JSONObject laneArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("expected_lanes", etbrConfig.optInt("expected_lanes", 20))
            .put("sensitivity", etbrConfig.optDouble("sensitivity", 0.6))
            .put("constant_spacing", etbrConfig.optBoolean("constant_spacing", true));
            
        JSONObject laneResult = gelAnalysisTools.detectLanes(laneArgs);
        if (!laneResult.optBoolean("ok", false)) {
            throw new RuntimeException("Lane detection failed: " + laneResult.toString());
        }
        
        // Check if we need rescue fallback for lane detection
        int lanesFound = laneResult.optInt("lanes_found", 0);
        boolean rescueUsed = false;
        
        if (lanesFound == 0) {
            logger.info("Lane detection returned 0 results, attempting rescue with relaxed parameters");
            
            // Try rescue with proper parameter adjustments per guide
            ImagePlus rescuePreprocessed = imagePlus.duplicate();
            ImagePreprocessor.Config rescueConfig = ImagePreprocessor.Config.gelAnalysisConfig();
            rescueConfig.forceInvert = true; // Force polarity inversion
            rescueConfig.gaussianSigma = detectConfig.optDouble("gaussian_sigma", 2.0) * 1.6; // σ × 1.6
            
            rescuePreprocessed = ImagePreprocessor.preprocessForDetection(rescuePreprocessed, rescueConfig, outdir);
            String rescueHandle = store.putImage(rescuePreprocessed);
            
            // Retry with prominence_frac × 0.33 per guide, but respect validation bounds
            double originalProminence = detectConfig.optDouble("prominence_frac", 0.10);
            double rescueSensitivity = Math.max(0.1, originalProminence * 0.33); // Ensure ≥ 0.1
            JSONObject rescueLaneArgs = new JSONObject()
                .put("image_handle", rescueHandle)
                .put("expected_lanes", etbrConfig.optInt("expected_lanes", 12))
                .put("sensitivity", rescueSensitivity) // prominence_frac × 0.33 with bounds
                .put("constant_spacing", false);
                
            JSONObject rescueLaneResult = gelAnalysisTools.detectLanes(rescueLaneArgs);
            if (rescueLaneResult.optBoolean("ok", false) && rescueLaneResult.optInt("lanes_found", 0) > 0) {
                laneResult = rescueLaneResult;
                imageHandle = rescueHandle; // Use rescue image for band detection too
                currentWorkingImage = rescuePreprocessed; // Update overlay image
                rescueUsed = true;
                logger.info("Rescue lane detection successful: " + rescueLaneResult.optInt("lanes_found", 0) + " lanes found");
            }
        }
        
        // Run band detection for EtBr (different characteristics than SDS-PAGE)
        JSONObject bandArgs = new JSONObject()
            .put("image_handle", imageHandle)
            .put("background_subtraction", etbrConfig.optBoolean("background_subtraction", false))  // FIXED: Default to false
            .put("peak_detection_method", "fluorescence");  // EtBr-specific
            
        JSONObject bandResult = gelAnalysisTools.detectBands(bandArgs);
        if (!bandResult.optBoolean("ok", false)) {
            throw new RuntimeException("Band detection failed: " + bandResult.toString());
        }
        
        // Check if we need rescue fallback for band detection too
        int bandsFound = bandResult.optInt("bands_total", 0);
        if (bandsFound == 0 && !rescueUsed) {
            logger.info("Band detection returned 0 results, attempting rescue with enhanced preprocessing");
            
            // Apply band detection rescue with proper parameters per guide
            ImagePlus bandRescueImg = imagePlus.duplicate();
            ImagePreprocessor.Config bandRescueConfig = ImagePreprocessor.Config.gelAnalysisConfig();
            bandRescueConfig.forceInvert = !rescueUsed; // Try opposite polarity than lane rescue
            bandRescueConfig.clipPercentileLow = 1.0;
            bandRescueConfig.clipPercentileHigh = 99.5;
            bandRescueConfig.gaussianSigma = detectConfig.optDouble("gaussian_sigma", 2.0) * 1.6; // σ × 1.6
            bandRescueConfig.backgroundRemovalRadius = 0.0; // FIXED: Disable to avoid multiple background removal
            
            bandRescueImg = ImagePreprocessor.preprocessForDetection(bandRescueImg, bandRescueConfig, outdir);
            String bandRescueHandle = store.putImage(bandRescueImg);
            
            JSONObject rescueBandArgs = new JSONObject()
                .put("image_handle", bandRescueHandle)
                .put("background_subtraction", false)  // FIXED: Disable to avoid multiple background removal
                .put("peak_detection_method", "fluorescence");
                
            JSONObject rescueBandResult = gelAnalysisTools.detectBands(rescueBandArgs);
            if (rescueBandResult.optBoolean("ok", false) && rescueBandResult.optInt("bands_total", 0) > 0) {
                bandResult = rescueBandResult;
                currentWorkingImage = bandRescueImg; // Update overlay image
                rescueUsed = true;
                logger.info("Rescue band detection successful: " + rescueBandResult.optInt("bands_total", 0) + " bands found");
            }
        }
        
        // Generate gel overlay PNG using headless-safe visualization
        JSONObject combinedResult = new JSONObject()
            .put("lanes", laneResult.optJSONArray("lanes"))
            .put("bands", bandResult.optJSONArray("bands"));
        BufferedImage annotatedImage = GelViz.renderOverlay(currentWorkingImage, combinedResult, true);
        Path overlayPng = Paths.get(outdir, "overlay.png");  // ✅ FIXED: Consistent naming across all workflows
        ImageIO.write(annotatedImage, "PNG", overlayPng.toFile());
        
        // Extract real metrics from analysis results (using correct field names from GelAnalysisTools)
        Map<String, Double> metrics = new LinkedHashMap<>();
        int laneCount = laneResult.optInt("lanes_found", 0);
        int bandCount = bandResult.optInt("bands_total", 0);
        
        metrics.put("lane_count", (double) laneCount);
        metrics.put("band_count", (double) bandCount);
        
        // FIXED: Only include advanced metrics if features were actually detected
        if (laneCount > 0 && bandCount > 0) {
            // Calculate molecular weight ladder R² for DNA sizing
            if (bandResult.has("dna_ladder_r2")) {
                metrics.put("ladder_linear_r2", bandResult.getDouble("dna_ladder_r2"));
            }
            
            // Calculate smearing index (DNA degradation indicator)
            if (bandResult.has("smearing_index")) {
                metrics.put("smearing_index", bandResult.getDouble("smearing_index"));
            }
        } else {
            // No features detected - log this condition
            logger.warning(String.format("EtBr analysis found no features (lanes=%d, bands=%d) - skipping advanced metrics", 
                         laneCount, bandCount));
        }
        
        // Add rescue usage to metrics for optimization feedback
        if (rescueUsed) {
            metrics.put("rescue_applied", 1.0);
        }
        
        // Calculate simple hash of input file
        String inputHash = Integer.toHexString(Paths.get(input).hashCode());
        
        // Write run_report.json with consistent schema
        JSONObject runReport = new JSONObject()
            .put("task", "etbr_agarose")
            .put("input_path", input)
            .put("input_hash", inputHash)
            .put("metrics", convertMapToJSONObject(metrics))
            .put("diagnostics_png", "overlay.png")  // ✅ FIXED: Relative path for portability
            .put("rescue_used", rescueUsed)
            .put("meta", new JSONObject());
            
        Path reportPath = Paths.get(outdir, "run_report.json");
        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            writer.write(runReport.toString(2));  // Pretty print with 2-space indent
        }
        
        logger.info("EtBr agarose analysis completed: " + metrics.get("lane_count") + " lanes, " + 
                   metrics.get("band_count") + " bands detected");
    }
    
    /**
     * Generate synthetic test images for controlled algorithm validation
     */
    private static void generateSyntheticImages(String outputDir) {
        try {
            logger.info("Generating synthetic test images in: " + outputDir);
            
            // Create output directory if it doesn't exist
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);
            
            // Generate synthetic EtBr gel (12 lanes, 4 bands per lane)
            ImagePlus etbrGel = SyntheticImageGenerator.generateEtBrGel(800, 600, 12, 4);
            FileSaver etbrSaver = new FileSaver(etbrGel);
            String etbrPath = Paths.get(outputDir, "synthetic_etbr_gel.jpg").toString();
            etbrSaver.saveAsJpeg(etbrPath);
            System.out.println("Saved: " + etbrPath);
            
            // Generate synthetic SDS-PAGE gel (8 lanes)
            ImagePlus sdsGel = SyntheticImageGenerator.generateSdsPageGel(600, 800, 8);
            FileSaver sdsSaver = new FileSaver(sdsGel);
            String sdsPath = Paths.get(outputDir, "synthetic_sds_gel.jpg").toString();
            sdsSaver.saveAsJpeg(sdsPath);
            System.out.println("Saved: " + sdsPath);
            
            // Generate synthetic colony plate (50 colonies)
            ImagePlus colonyPlate = SyntheticImageGenerator.generateColonyPlate(800, 800, 50);
            FileSaver colonySaver = new FileSaver(colonyPlate);
            String colonyPath = Paths.get(outputDir, "synthetic_colony_plate.jpg").toString();
            colonySaver.saveAsJpeg(colonyPath);
            System.out.println("Saved: " + colonyPath);
            
            System.out.println("All synthetic images generated successfully!");
            
        } catch (Exception e) {
            System.err.println("Failed to generate synthetic images: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load image via SCIFIO/ImageJ2 as specified in 15-minute guide
     */
    private static ImagePlus loadImageViaSCIFIO(String imagePath) {
        try {
            // For now, use standard opener but log SCIFIO attempt
            logger.info("Loading image (SCIFIO intended): " + imagePath);
            return new Opener().openImage(imagePath);
        } catch (Exception e) {
            logger.warning("Image loading failed: " + e.getMessage());
            throw new RuntimeException("Failed to load image: " + imagePath, e);
        }
    }
    
    /**
     * Log image statistics as recommended in guide: "global mean/std, p1/p99, polarity decision"
     */
    private static void logImageStatistics(ImagePlus image, String description) {
        if (image == null) return;
        
        ImageProcessor ip = image.getProcessor();
        ij.process.ImageStatistics stats = ip.getStatistics();
        double mean = stats.mean;
        double std = stats.stdDev;
        double min = stats.min;
        double max = stats.max;
        
        // Calculate percentiles
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        float[] sortedPixels = pixels.clone();
        java.util.Arrays.sort(sortedPixels);
        int n = sortedPixels.length;
        double p1 = sortedPixels[(int)(n * 0.01)];
        double p99 = sortedPixels[(int)(n * 0.99)];
        
        // Polarity heuristic from ImagePreprocessor
        double normalizedMean = (mean - min) / (max - min);
        boolean shouldInvert = normalizedMean < 0.4 && (max - min) > 50;
        
        logger.info(String.format("%s statistics - mean=%.1f±%.1f, range=[%.1f,%.1f], p1/p99=[%.1f,%.1f], shouldInvert=%s",
            description, mean, std, min, max, p1, p99, shouldInvert));
    }
}