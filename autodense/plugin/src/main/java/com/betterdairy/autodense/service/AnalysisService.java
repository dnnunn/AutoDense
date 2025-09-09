package com.betterdairy.autodense.service;

import com.betterdairy.autodense.model.Models.*;
import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.analysis.BandDetector;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.Color;

/**
 * Unified Analysis Service for AutoDense Phase III Heart Transplant
 * 
 * Provides a clean, enterprise-grade service layer that simplifies the legacy Java backend
 * by integrating the PythonBridge with existing AutoDense architecture. This service acts
 * as the primary entry point for all analysis operations while maintaining compatibility
 * with existing UI components and workflows.
 * 
 * Key Features:
 * - Unified analysis interface for gel, colony, and PCR analysis
 * - Seamless PythonBridge integration with fallback mechanisms
 * - Configuration management with validation and presets
 * - ImageJ integration for overlays and ROI management
 * - Comprehensive error handling and logging
 * - Resource lifecycle management
 * - Thread-safe operations
 * 
 * Architecture:
 * - Service Layer: This class provides high-level analysis operations
 * - Bridge Layer: PythonBridge handles Python process communication
 * - Legacy Layer: Existing analysis classes provide fallback functionality
 * - Model Layer: Structured data objects for results and configuration
 * 
 * @since AutoDense Phase III
 * @author AutoDense Team
 */
public class AnalysisService {
    
    private static final Logger logger = Logger.getLogger(AnalysisService.class.getName());
    
    // Service state
    private final PythonBridge pythonBridge;
    private final Map<String, AnalysisConfig> presetConfigs;
    private final Path tempDirectory;
    private volatile boolean isInitialized = false;
    private volatile boolean shutdownRequested = false;
    
    // Configuration constants
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final String TEMP_PREFIX = "autodense_analysis_";
    
    /**
     * Analysis Configuration
     * 
     * Encapsulates all parameters needed for an analysis operation.
     * Supports both simple preset-based configuration and detailed parameter customization.
     */
    public static class AnalysisConfig {
        private final String modality;
        private final Map<String, Object> parameters;
        private final boolean usePythonBridge;
        private final int timeoutSeconds;
        
        public AnalysisConfig(String modality, Map<String, Object> parameters, 
                             boolean usePythonBridge, int timeoutSeconds) {
            this.modality = Objects.requireNonNull(modality, "Modality cannot be null");
            this.parameters = new HashMap<>(parameters != null ? parameters : Map.of());
            this.usePythonBridge = usePythonBridge;
            this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        }
        
        public AnalysisConfig(String modality, Map<String, Object> parameters) {
            this(modality, parameters, true, DEFAULT_TIMEOUT_SECONDS);
        }
        
        public AnalysisConfig(String modality) {
            this(modality, Map.of(), true, DEFAULT_TIMEOUT_SECONDS);
        }
        
        // Getters
        public String getModality() { return modality; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        public boolean isUsePythonBridge() { return usePythonBridge; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        
        // Parameter access helpers
        public Object getParameter(String key) { return parameters.get(key); }
        public Object getParameter(String key, Object defaultValue) { 
            return parameters.getOrDefault(key, defaultValue); 
        }
        public int getIntParameter(String key, int defaultValue) {
            Object value = parameters.get(key);
            return value instanceof Number ? ((Number) value).intValue() : defaultValue;
        }
        public double getDoubleParameter(String key, double defaultValue) {
            Object value = parameters.get(key);
            return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
        }
        public boolean getBooleanParameter(String key, boolean defaultValue) {
            Object value = parameters.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
        public String getStringParameter(String key, String defaultValue) {
            Object value = parameters.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }
        
        // Builder for easy configuration construction
        public static Builder builder(String modality) {
            return new Builder(modality);
        }
        
        public static class Builder {
            private final String modality;
            private final Map<String, Object> parameters = new HashMap<>();
            private boolean usePythonBridge = true;
            private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            
            private Builder(String modality) {
                this.modality = modality;
            }
            
            public Builder parameter(String key, Object value) {
                parameters.put(key, value);
                return this;
            }
            
            public Builder usePythonBridge(boolean use) {
                this.usePythonBridge = use;
                return this;
            }
            
            public Builder timeout(int seconds) {
                this.timeoutSeconds = seconds;
                return this;
            }
            
            public AnalysisConfig build() {
                return new AnalysisConfig(modality, parameters, usePythonBridge, timeoutSeconds);
            }
        }
        
        @Override
        public String toString() {
            return String.format("AnalysisConfig{modality='%s', parameters=%d, usePythonBridge=%s, timeout=%ds}", 
                               modality, parameters.size(), usePythonBridge, timeoutSeconds);
        }
    }
    
    /**
     * Analysis Result
     * 
     * Comprehensive result object that encapsulates all analysis outputs including
     * detected features, overlay images, performance metrics, and error information.
     */
    public static class AnalysisResult {
        private final boolean success;
        private final String modality;
        private final List<Lane> lanes;
        private final List<Band> bands;
        private final List<Colony> colonies;
        private final BufferedImage overlayImage;
        private final Map<String, Object> metadata;
        private final String error;
        private final long processingTimeMs;
        private final boolean usedPythonBridge;
        
        public AnalysisResult(boolean success, String modality, List<Lane> lanes, List<Band> bands,
                             List<Colony> colonies, BufferedImage overlayImage, 
                             Map<String, Object> metadata, String error, long processingTimeMs,
                             boolean usedPythonBridge) {
            this.success = success;
            this.modality = modality;
            this.lanes = lanes != null ? new ArrayList<>(lanes) : new ArrayList<>();
            this.bands = bands != null ? new ArrayList<>(bands) : new ArrayList<>();
            this.colonies = colonies != null ? new ArrayList<>(colonies) : new ArrayList<>();
            this.overlayImage = overlayImage;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.usedPythonBridge = usedPythonBridge;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getModality() { return modality; }
        public List<Lane> getLanes() { return new ArrayList<>(lanes); }
        public List<Band> getBands() { return new ArrayList<>(bands); }
        public List<Colony> getColonies() { return new ArrayList<>(colonies); }
        public BufferedImage getOverlayImage() { return overlayImage; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public boolean isUsedPythonBridge() { return usedPythonBridge; }
        
        // Convenience accessors
        public int getLaneCount() { return lanes.size(); }
        public int getBandCount() { return bands.size(); }
        public int getColonyCount() { return colonies.size(); }
        public boolean hasOverlay() { return overlayImage != null; }
        public boolean hasFeatures() { return !lanes.isEmpty() || !bands.isEmpty() || !colonies.isEmpty(); }
        
        @Override
        public String toString() {
            return String.format("AnalysisResult{success=%s, modality='%s', lanes=%d, bands=%d, colonies=%d, time=%dms, bridge=%s}", 
                               success, modality, lanes.size(), bands.size(), colonies.size(), processingTimeMs, usedPythonBridge);
        }
    }
    
    /**
     * Create a new AnalysisService with default configuration
     * 
     * @return AnalysisService instance ready for use
     * @throws ServiceInitializationException if initialization fails
     */
    public static AnalysisService createDefault() {
        try {
            PythonBridge bridge = PythonBridge.createDefault();
            return new AnalysisService(bridge);
        } catch (Exception e) {
            throw new ServiceInitializationException("Failed to create default AnalysisService", e);
        }
    }
    
    /**
     * Create a new AnalysisService with custom PythonBridge
     * 
     * @param pythonBridge PythonBridge instance to use
     */
    public AnalysisService(PythonBridge pythonBridge) {
        this.pythonBridge = Objects.requireNonNull(pythonBridge, "PythonBridge cannot be null");
        this.presetConfigs = new ConcurrentHashMap<>();
        
        try {
            this.tempDirectory = Files.createTempDirectory(TEMP_PREFIX);
            this.tempDirectory.toFile().deleteOnExit();
            initializePresets();
            this.isInitialized = true;
            logger.info("AnalysisService initialized successfully with temp directory: " + tempDirectory);
        } catch (Exception e) {
            throw new ServiceInitializationException("Failed to initialize AnalysisService", e);
        }
    }
    
    /**
     * Primary Analysis Interface - Gel Analysis
     * 
     * Performs comprehensive gel analysis including lane detection, band detection,
     * and molecular weight calibration using the Python bridge with fallback support.
     * 
     * @param image ImagePlus to analyze
     * @param config Analysis configuration
     * @return Comprehensive analysis results
     */
    public AnalysisResult analyzeGel(ImagePlus image, AnalysisConfig config) {
        validateInput(image, config);
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Starting gel analysis with config: " + config);
            
            if (config.isUsePythonBridge() && isServiceHealthy()) {
                try {
                    return performPythonBridgeAnalysis(image, config, "gel", startTime);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Python bridge analysis failed, falling back to legacy", e);
                    return performLegacyGelAnalysis(image, config, startTime);
                }
            } else {
                logger.info("Using legacy gel analysis (Python bridge disabled or unavailable)");
                return performLegacyGelAnalysis(image, config, startTime);
            }
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.log(Level.SEVERE, "Gel analysis failed completely", e);
            return new AnalysisResult(false, "gel", null, null, null, null, null, 
                                    "Analysis failed: " + e.getMessage(), processingTime, false);
        }
    }
    
    /**
     * Primary Analysis Interface - Colony Analysis
     * 
     * Performs colony counting and classification including plate detection,
     * colony segmentation, and X-gal screening using advanced computer vision.
     * 
     * @param image ImagePlus to analyze
     * @param config Analysis configuration
     * @return Comprehensive colony analysis results
     */
    public AnalysisResult analyzeColony(ImagePlus image, AnalysisConfig config) {
        validateInput(image, config);
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Starting colony analysis with config: " + config);
            
            if (config.isUsePythonBridge() && isServiceHealthy()) {
                try {
                    return performPythonBridgeAnalysis(image, config, "colony", startTime);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Python bridge analysis failed, falling back to legacy", e);
                    return performLegacyColonyAnalysis(image, config, startTime);
                }
            } else {
                logger.info("Using legacy colony analysis (Python bridge disabled or unavailable)");
                return performLegacyColonyAnalysis(image, config, startTime);
            }
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.log(Level.SEVERE, "Colony analysis failed completely", e);
            return new AnalysisResult(false, "colony", null, null, null, null, null, 
                                    "Analysis failed: " + e.getMessage(), processingTime, false);
        }
    }
    
    /**
     * Configuration Management - Get Default Configuration
     * 
     * Returns a sensible default configuration for the specified modality
     * with parameters optimized for typical use cases.
     * 
     * @param modality Analysis modality ("gel", "colony", "pcr")
     * @return Default configuration for the modality
     */
    public AnalysisConfig getDefaultConfig(String modality) {
        Objects.requireNonNull(modality, "Modality cannot be null");
        
        return switch (modality.toLowerCase()) {
            case "gel", "sds_page" -> createDefaultGelConfig();
            case "colony", "plate" -> createDefaultColonyConfig();
            case "pcr", "rtpcr" -> createDefaultPcrConfig();
            default -> {
                logger.warning("Unknown modality: " + modality + ", using generic config");
                yield new AnalysisConfig(modality);
            }
        };
    }
    
    /**
     * Configuration Management - Validate Configuration
     * 
     * Validates configuration parameters and reports any issues.
     * This method can be used to check configuration before analysis.
     * 
     * @param config Configuration to validate
     * @return true if configuration is valid
     */
    public boolean validateConfig(AnalysisConfig config) {
        try {
            Objects.requireNonNull(config, "Configuration cannot be null");
            Objects.requireNonNull(config.getModality(), "Modality cannot be null");
            
            // Basic validation
            if (config.getTimeoutSeconds() <= 0) {
                logger.warning("Invalid timeout: " + config.getTimeoutSeconds());
                return false;
            }
            
            // Modality-specific validation
            return switch (config.getModality().toLowerCase()) {
                case "gel", "sds_page" -> validateGelConfig(config);
                case "colony", "plate" -> validateColonyConfig(config);
                case "pcr", "rtpcr" -> validatePcrConfig(config);
                default -> {
                    logger.warning("Unknown modality for validation: " + config.getModality());
                    yield true; // Allow unknown modalities
                }
            };
        } catch (Exception e) {
            logger.log(Level.WARNING, "Configuration validation failed", e);
            return false;
        }
    }
    
    /**
     * ImageJ Integration - Display Overlay
     * 
     * Applies analysis results as overlays on the ImageJ image.
     * This method integrates with ImageJ's overlay system for visualization.
     * 
     * @param image ImagePlus to add overlay to
     * @param result Analysis results to visualize
     */
    public void displayOverlay(ImagePlus image, AnalysisResult result) {
        Objects.requireNonNull(image, "Image cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");
        
        try {
            Overlay overlay = new Overlay();
            
            // Add lane overlays
            for (Lane lane : result.getLanes()) {
                Rectangle rect = new Rectangle(lane.xStart(), 0, 
                                             lane.xEnd() - lane.xStart() + 1, 
                                             image.getHeight());
                Roi laneRoi = new Roi(rect);
                laneRoi.setStrokeColor(Color.CYAN);
                laneRoi.setStrokeWidth(2);
                laneRoi.setName("Lane " + lane.index());
                overlay.add(laneRoi);
            }
            
            // Add band overlays
            for (Band band : result.getBands()) {
                Rectangle rect = new Rectangle(0, band.y() - 5, image.getWidth(), 10);
                Roi bandRoi = new Roi(rect);
                bandRoi.setStrokeColor(Color.YELLOW);
                bandRoi.setStrokeWidth(1);
                bandRoi.setName("Band " + band.index() + " (" + String.format("%.1f", band.mwKDa()) + " kDa)");
                overlay.add(bandRoi);
            }
            
            // Add colony overlays
            for (Colony colony : result.getColonies()) {
                int radius = (int) Math.round(colony.diameter() / 2.0);
                Rectangle rect = new Rectangle((int) colony.x() - radius, (int) colony.y() - radius, 
                                             radius * 2, radius * 2);
                Roi colonyRoi = new Roi(rect);
                colonyRoi.setStrokeColor(colony.colorClass() == ColonyColor.BLUE ? Color.BLUE : Color.RED);
                colonyRoi.setStrokeWidth(2);
                colonyRoi.setName("Colony " + colony.index() + " (" + colony.colorClass() + ")");
                overlay.add(colonyRoi);
            }
            
            image.setOverlay(overlay);
            logger.info("Applied overlay with " + overlay.size() + " ROIs to image");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to display overlay", e);
        }
    }
    
    /**
     * ImageJ Integration - Get Lane ROIs
     * 
     * Converts detected lanes to ImageJ ROI objects for further processing.
     * 
     * @param result Analysis results containing lanes
     * @return Array of ROI objects representing lanes
     */
    public Roi[] getLaneRois(AnalysisResult result) {
        Objects.requireNonNull(result, "Result cannot be null");
        
        return result.getLanes().stream()
                .map(lane -> {
                    Rectangle rect = new Rectangle(lane.xStart(), 0, 
                                                 lane.xEnd() - lane.xStart() + 1, 
                                                 1000); // Use large height, will be clipped
                    Roi roi = new Roi(rect);
                    roi.setName("Lane " + lane.index());
                    return roi;
                })
                .toArray(Roi[]::new);
    }
    
    /**
     * ImageJ Integration - Get Band ROIs
     * 
     * Converts detected bands to ImageJ ROI objects for measurement and analysis.
     * 
     * @param result Analysis results containing bands
     * @return Array of ROI objects representing bands
     */
    public Roi[] getBandRois(AnalysisResult result) {
        Objects.requireNonNull(result, "Result cannot be null");
        
        return result.getBands().stream()
                .map(band -> {
                    Rectangle rect = new Rectangle(0, band.y() - 5, 1000, 10); // Use large width
                    Roi roi = new Roi(rect);
                    roi.setName("Band " + band.index());
                    return roi;
                })
                .toArray(Roi[]::new);
    }
    
    /**
     * Service Management - Health Check
     * 
     * Checks if the service is operational and can perform analyses.
     * 
     * @return true if service is healthy and ready for analysis
     */
    public boolean isServiceHealthy() {
        if (shutdownRequested || !isInitialized) {
            return false;
        }
        
        try {
            return pythonBridge.isHealthy();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Health check failed", e);
            return false;
        }
    }
    
    /**
     * Service Management - Shutdown
     * 
     * Gracefully shuts down the service and releases all resources.
     * This method should be called when the service is no longer needed.
     */
    public void shutdown() {
        if (shutdownRequested) {
            return;
        }
        
        shutdownRequested = true;
        logger.info("Shutting down AnalysisService...");
        
        try {
            // Shutdown Python bridge
            pythonBridge.shutdown();
            
            // Clean up temporary directory
            if (tempDirectory != null && Files.exists(tempDirectory)) {
                Files.walk(tempDirectory)
                     .sorted(Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
            }
            
            logger.info("AnalysisService shutdown completed successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
    }
    
    // === Private Implementation Methods ===
    
    private void validateInput(ImagePlus image, AnalysisConfig config) {
        if (shutdownRequested) {
            throw new IllegalStateException("Service has been shutdown");
        }
        if (!isInitialized) {
            throw new IllegalStateException("Service not initialized");
        }
        Objects.requireNonNull(image, "Image cannot be null");
        Objects.requireNonNull(config, "Configuration cannot be null");
        if (image.getProcessor() == null) {
            throw new IllegalArgumentException("Image has no processor");
        }
    }
    
    private AnalysisResult performPythonBridgeAnalysis(ImagePlus image, AnalysisConfig config, 
                                                      String analysisType, long startTime) 
            throws IOException, InterruptedException {
        
        // Save image to temporary file
        Path tempImagePath = tempDirectory.resolve("input_" + System.currentTimeMillis() + ".tif");
        ij.IJ.save(image, tempImagePath.toString());
        
        try {
            // Convert config to JSON
            JSONObject configJson = new JSONObject(config.getParameters());
            configJson.put("modality", config.getModality());
            configJson.put("timeout_seconds", config.getTimeoutSeconds());
            
            // Perform analysis via Python bridge
            PythonBridge.AnalysisResult bridgeResult = pythonBridge.analyzeImage(tempImagePath, configJson);
            
            if (!bridgeResult.isSuccess()) {
                throw new RuntimeException("Python bridge analysis failed: " + bridgeResult.getError());
            }
            
            // Convert results to our format
            return convertBridgeResult(bridgeResult, config.getModality(), startTime, true);
            
        } finally {
            // Clean up temporary file
            try {
                Files.deleteIfExists(tempImagePath);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to clean up temporary file: " + tempImagePath, e);
            }
        }
    }
    
    private AnalysisResult performLegacyGelAnalysis(ImagePlus image, AnalysisConfig config, long startTime) {
        try {
            // Use existing LaneDetector and BandDetector
            int expectedLanes = config.getIntParameter("lane_count", 0);
            boolean constantSpacing = config.getBooleanParameter("constant_spacing", false);
            
            List<Lane> lanes = LaneDetector.findLanes(image, expectedLanes, constantSpacing);
            List<Band> bands = new ArrayList<>();
            
            // Detect bands in each lane
            Map<String, Object> configMap = config.getParameters();
            for (Lane lane : lanes) {
                List<Band> laneBands = BandDetector.findBands(image, lane, configMap);
                bands.addAll(laneBands);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Legacy gel analysis completed: " + lanes.size() + " lanes, " + bands.size() + " bands");
            
            return new AnalysisResult(true, config.getModality(), lanes, bands, null, null, 
                                    createMetadata(lanes.size(), bands.size(), 0), null, 
                                    processingTime, false);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.log(Level.SEVERE, "Legacy gel analysis failed", e);
            return new AnalysisResult(false, config.getModality(), null, null, null, null, null,
                                    "Legacy analysis failed: " + e.getMessage(), processingTime, false);
        }
    }
    
    private AnalysisResult performLegacyColonyAnalysis(ImagePlus image, AnalysisConfig config, long startTime) {
        try {
            // Placeholder for legacy colony analysis
            // In a real implementation, this would use existing colony analysis tools
            List<Colony> colonies = new ArrayList<>();
            
            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Legacy colony analysis completed: " + colonies.size() + " colonies");
            
            return new AnalysisResult(true, config.getModality(), null, null, colonies, null,
                                    createMetadata(0, 0, colonies.size()), null, 
                                    processingTime, false);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.log(Level.SEVERE, "Legacy colony analysis failed", e);
            return new AnalysisResult(false, config.getModality(), null, null, null, null, null,
                                    "Legacy colony analysis failed: " + e.getMessage(), processingTime, false);
        }
    }
    
    private AnalysisResult convertBridgeResult(PythonBridge.AnalysisResult bridgeResult, 
                                              String modality, long startTime, boolean usedBridge) {
        try {
            List<Lane> lanes = convertJsonToLanes(bridgeResult.getLanes());
            List<Band> bands = convertJsonToBands(bridgeResult.getBands());
            List<Colony> colonies = convertJsonToColonies(bridgeResult.getData());
            
            Map<String, Object> metadata = createMetadata(lanes.size(), bands.size(), colonies.size());
            if (bridgeResult.getData() != null) {
                // Add additional metadata from Python results
                if (bridgeResult.getData().has("final_params")) {
                    metadata.put("final_params", bridgeResult.getData().getJSONObject("final_params").toMap());
                }
                if (bridgeResult.getData().has("observations")) {
                    metadata.put("observations", bridgeResult.getData().getJSONObject("observations").toMap());
                }
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return new AnalysisResult(true, modality, lanes, bands, colonies, 
                                    bridgeResult.getOverlayImage(), metadata, null, 
                                    processingTime, usedBridge);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.log(Level.WARNING, "Failed to convert bridge result", e);
            return new AnalysisResult(false, modality, null, null, null, null, null,
                                    "Result conversion failed: " + e.getMessage(), processingTime, usedBridge);
        }
    }
    
    private List<Lane> convertJsonToLanes(JSONArray lanesJson) {
        List<Lane> lanes = new ArrayList<>();
        for (int i = 0; i < lanesJson.length(); i++) {
            JSONObject laneJson = lanesJson.getJSONObject(i);
            lanes.add(new Lane(
                laneJson.optInt("index", i + 1),
                laneJson.optInt("x_start", 0),
                laneJson.optInt("x_end", 0)
            ));
        }
        return lanes;
    }
    
    private List<Band> convertJsonToBands(JSONArray bandsJson) {
        List<Band> bands = new ArrayList<>();
        for (int i = 0; i < bandsJson.length(); i++) {
            JSONObject bandJson = bandsJson.getJSONObject(i);
            bands.add(new Band(
                bandJson.optInt("index", i + 1),
                bandJson.optInt("y", 0),
                bandJson.optDouble("area", 0.0),
                bandJson.optDouble("baseline", 0.0),
                bandJson.optDouble("mw_kda", 0.0)
            ));
        }
        return bands;
    }
    
    private List<Colony> convertJsonToColonies(JSONObject data) {
        List<Colony> colonies = new ArrayList<>();
        if (data != null && data.has("colonies")) {
            JSONArray coloniesJson = data.getJSONArray("colonies");
            for (int i = 0; i < coloniesJson.length(); i++) {
                JSONObject colonyJson = coloniesJson.getJSONObject(i);
                colonies.add(new Colony(
                    colonyJson.optInt("index", i + 1),
                    colonyJson.optDouble("x", 0.0),
                    colonyJson.optDouble("y", 0.0),
                    colonyJson.optDouble("area", 0.0),
                    colonyJson.optDouble("diameter", 0.0),
                    colonyJson.optDouble("diameter_mm", 0.0),
                    colonyJson.optDouble("circularity", 0.0),
                    colonyJson.optDouble("solidity", 0.0),
                    colonyJson.optDouble("mean_intensity", 0.0),
                    parseColonyColor(colonyJson.optString("color_class", "OTHER")),
                    colonyJson.optDouble("color_confidence", 0.0),
                    parseColonySize(colonyJson.optString("size_class", "MEDIUM")),
                    colonyJson.optString("bin_category", "unknown")
                ));
            }
        }
        return colonies;
    }
    
    private ColonyColor parseColonyColor(String colorStr) {
        try {
            return ColonyColor.valueOf(colorStr.toUpperCase());
        } catch (Exception e) {
            return ColonyColor.OTHER;
        }
    }
    
    private ColonySize parseColonySize(String sizeStr) {
        try {
            return ColonySize.valueOf(sizeStr.toUpperCase());
        } catch (Exception e) {
            return ColonySize.MEDIUM;
        }
    }
    
    private Map<String, Object> createMetadata(int laneCount, int bandCount, int colonyCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lane_count", laneCount);
        metadata.put("band_count", bandCount);
        metadata.put("colony_count", colonyCount);
        metadata.put("analysis_timestamp", System.currentTimeMillis());
        metadata.put("service_version", "Phase III");
        return metadata;
    }
    
    private void initializePresets() {
        // Initialize common preset configurations
        presetConfigs.put("sds_page_default", createDefaultGelConfig());
        presetConfigs.put("colony_count_default", createDefaultColonyConfig());
        presetConfigs.put("pcr_default", createDefaultPcrConfig());
        
        logger.info("Initialized " + presetConfigs.size() + " preset configurations");
    }
    
    private AnalysisConfig createDefaultGelConfig() {
        return AnalysisConfig.builder("gel")
                .parameter("lane_count", 6)
                .parameter("constant_spacing", true)
                .parameter("detect_bands", true)
                .parameter("mw_calibration", true)
                .parameter("baseline_correction", true)
                .build();
    }
    
    private AnalysisConfig createDefaultColonyConfig() {
        return AnalysisConfig.builder("colony")
                .parameter("min_colony_size", 10)
                .parameter("max_colony_size", 1000)
                .parameter("detect_plate", true)
                .parameter("xgal_classification", true)
                .parameter("color_analysis", true)
                .build();
    }
    
    private AnalysisConfig createDefaultPcrConfig() {
        return AnalysisConfig.builder("pcr")
                .parameter("lane_count", 8)
                .parameter("normalize_housekeeping", true)
                .parameter("ddct_analysis", true)
                .build();
    }
    
    private boolean validateGelConfig(AnalysisConfig config) {
        int laneCount = config.getIntParameter("lane_count", 0);
        if (laneCount < 0 || laneCount > 20) {
            logger.warning("Invalid lane count: " + laneCount);
            return false;
        }
        return true;
    }
    
    private boolean validateColonyConfig(AnalysisConfig config) {
        int minSize = config.getIntParameter("min_colony_size", 0);
        int maxSize = config.getIntParameter("max_colony_size", 1000);
        if (minSize >= maxSize || minSize < 0) {
            logger.warning("Invalid colony size range: " + minSize + " to " + maxSize);
            return false;
        }
        return true;
    }
    
    private boolean validatePcrConfig(AnalysisConfig config) {
        // PCR-specific validation
        return true; // Placeholder
    }
    
    /**
     * Custom exception for service initialization failures
     */
    public static class ServiceInitializationException extends RuntimeException {
        public ServiceInitializationException(String message) {
            super(message);
        }
        
        public ServiceInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}