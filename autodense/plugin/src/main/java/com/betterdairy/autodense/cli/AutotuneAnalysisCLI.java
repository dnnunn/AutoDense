package com.betterdairy.autodense.cli;

import com.betterdairy.autodense.tools.CanonicalTools;
import com.betterdairy.autodense.tools.GelAnalysisTools;
import com.betterdairy.autodense.tools.AssayOps;
import com.betterdairy.autodense.session.SessionStore;
import com.betterdairy.autodense.validation.InputValidator;
import com.betterdairy.autodense.viz.ColonyViz;
import com.betterdairy.autodense.viz.GelViz;
import net.imagej.ImageJ;
import org.scijava.Context;
import org.scijava.ui.UIService;
import ij.ImagePlus;
import ij.io.Opener;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
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
            
            // Run analysis based on task type
            JSONObject result = switch (task.toLowerCase()) {
                case "sds_page" -> runSdsPageAnalysis(inputImagePath, configFilePath, outDir);
                case "colony_count" -> runColonyAnalysis(inputImagePath, configFilePath, outDir);
                case "etbr_agarose" -> runEtBrAnalysis(inputImagePath, configFilePath, outDir);
                default -> throw new IllegalArgumentException("Unknown task: " + task);
            };
            
            // Always emit run_report.json with consistent schema
            Path reportPath = outDir.resolve("run_report.json");
            writeRunReport(result, reportPath);
            
            System.out.println("Analysis completed successfully: " + reportPath);
            
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
        
        // Load configuration
        JSONObject config = loadConfigFile(configPath);
        
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
        
        // Load configuration
        JSONObject config = loadConfigFile(configPath);
        
        // Create session store and colony analysis tools
        SessionStore store = new SessionStore();
        AssayOps assayOps = new AssayOps(store);
        CanonicalTools canonicalTools = new CanonicalTools(null, assayOps, store);
        
        try {
            // Validate input path
            String validatedPath = InputValidator.validateFilePath(inputPath, true, false);
            
            // Extract colony analysis parameters from config
            JSONObject colonyConfig = config.optJSONObject("colony_detection");
            if (colonyConfig == null) {
                colonyConfig = new JSONObject();
            }
            
            // Run complete plate analysis workflow
            JSONObject analysisArgs = new JSONObject()
                .put("image_path", validatedPath)
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
                // Load image directly for headless rendering
                ImagePlus imagePlus = new Opener().openImage(validatedPath);
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
        
        // Load configuration
        JSONObject config = loadConfigFile(configPath);
        
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
     * Load and parse configuration file (YAML or JSON)
     * Ensures config keys match challenge pack parameter grids for autotune integration
     */
    private static JSONObject loadConfigFile(String configPath) throws IOException {
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
}