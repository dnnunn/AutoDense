package com.betterdairy.autodense.plugin;

import org.scijava.Context;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.betterdairy.autodense.service.AnalysisService;
import com.betterdairy.autodense.model.Models.*;

/**
 * Simplified Gel Analysis Interface for AutoDense Phase III Heart Transplant
 * 
 * This version removes complex AI orchestration in favor of direct computer vision
 * analysis through the AnalysisService Python bridge. The interface focuses on
 * deterministic analysis operations with clear, simple controls.
 * 
 * Key Features:
 * - Direct analysis buttons for gel, colony, and lane detection
 * - Python bridge integration via AnalysisService
 * - Drag-drop image loading with ImageJ integration
 * - Results display with overlay visualization
 * - Parameter configuration for analysis settings
 * - Error handling and graceful fallbacks
 * 
 * @since AutoDense Phase III
 * @author AutoDense Team
 */
public class GelUI {
    
    private static final Logger logger = Logger.getLogger(GelUI.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // Core components
    private JFrame frame;
    private JTextArea resultsArea;
    private JLabel statusLabel;
    private JLabel imageInfoLabel;
    
    // Analysis components
    private AnalysisService analysisService;
    private ImagePlus currentImage;
    
    // UI Controls
    private JButton analyzeGelButton;
    private JButton analyzeColonyButton;
    private JButton detectLanesButton;
    private JButton loadImageButton;
    private JSpinner laneCountSpinner;
    private JCheckBox constantSpacingCheckBox;
    private JCheckBox enablePythonBridgeCheckBox;
    
    /**
     * Create new GelUI with simplified architecture
     * 
     * @param context SciJava context (unused in simplified version)
     */
    public GelUI(Context context) {
        try {
            // Initialize AnalysisService with Python bridge
            this.analysisService = AnalysisService.createDefault();
            logger.info("AnalysisService initialized successfully");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize AnalysisService, using fallback mode", e);
            // Continue without AnalysisService - UI will show appropriate messages
        }
    }
    
    /**
     * Show the UI window
     */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            createMainWindow();
            frame.setVisible(true);
            
            // Welcome message
            appendToResults("🔬 AutoDense Computer Vision Analysis", "INFO");
            appendToResults("Ready for gel and colony analysis", "INFO");
            
            if (analysisService != null && analysisService.isServiceHealthy()) {
                appendToResults("✓ Python bridge connected - Enhanced analysis available", "INFO");
            } else {
                appendToResults("⚠ Python bridge unavailable - Using legacy analysis only", "WARN");
            }
            
            appendToResults("Drag and drop an image file to get started", "INFO");
        });
    }
    
    /**
     * Create the main UI window with simplified layout
     */
    private void createMainWindow() {
        frame = new JFrame("AutoDense - Computer Vision Analysis");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new Dimension(800, 600));
        frame.setLocationRelativeTo(null);
        
        // Enable drag and drop
        setupDragAndDrop();
        
        // Main layout
        frame.setLayout(new BorderLayout());
        
        // Header panel
        JPanel headerPanel = createHeaderPanel();
        frame.add(headerPanel, BorderLayout.NORTH);
        
        // Results panel (center)
        JPanel resultsPanel = createResultsPanel();
        frame.add(resultsPanel, BorderLayout.CENTER);
        
        // Control panel (east)
        JPanel controlPanel = createControlPanel();
        frame.add(controlPanel, BorderLayout.EAST);
        
        // Status panel (south)
        JPanel statusPanel = createStatusPanel();
        frame.add(statusPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create header panel with image info and load button
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Title label
        JLabel titleLabel = new JLabel("🔬 AutoDense Analysis", JLabel.LEFT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.WEST);
        
        // Image info
        imageInfoLabel = new JLabel("No image loaded", JLabel.CENTER);
        imageInfoLabel.setFont(imageInfoLabel.getFont().deriveFont(Font.ITALIC));
        panel.add(imageInfoLabel, BorderLayout.CENTER);
        
        // Load image button
        loadImageButton = new JButton("Load Image");
        loadImageButton.addActionListener(e -> loadImage());
        panel.add(loadImageButton, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * Create results display panel
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Analysis Results"));
        
        // Results text area
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultsArea.setBackground(new Color(248, 248, 248));
        
        JScrollPane scrollPane = new JScrollPane(resultsArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * Create control panel with analysis buttons and parameters
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Analysis Controls"));
        panel.setPreferredSize(new Dimension(200, 0));
        
        // Analysis buttons
        panel.add(createAnalysisButtonsPanel());
        panel.add(Box.createVerticalStrut(10));
        
        // Parameters
        panel.add(createParametersPanel());
        panel.add(Box.createVerticalStrut(10));
        
        // Settings
        panel.add(createSettingsPanel());
        
        return panel;
    }
    
    /**
     * Create analysis buttons panel
     */
    private JPanel createAnalysisButtonsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Analysis"));
        
        // Analyze Gel button
        analyzeGelButton = new JButton("Analyze Gel");
        analyzeGelButton.setEnabled(false);
        analyzeGelButton.addActionListener(e -> analyzeGel());
        panel.add(analyzeGelButton);
        
        panel.add(Box.createVerticalStrut(5));
        
        // Detect Lanes button
        detectLanesButton = new JButton("Detect Lanes");
        detectLanesButton.setEnabled(false);
        detectLanesButton.addActionListener(e -> detectLanes());
        panel.add(detectLanesButton);
        
        panel.add(Box.createVerticalStrut(5));
        
        // Analyze Colony button
        analyzeColonyButton = new JButton("Analyze Colonies");
        analyzeColonyButton.setEnabled(false);
        analyzeColonyButton.addActionListener(e -> analyzeColonies());
        panel.add(analyzeColonyButton);
        
        return panel;
    }
    
    /**
     * Create parameters panel
     */
    private JPanel createParametersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Parameters"));
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Lane count
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Lanes:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        laneCountSpinner = new JSpinner(new SpinnerNumberModel(6, 0, 20, 1));
        panel.add(laneCountSpinner, gbc);
        
        // Constant spacing
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        constantSpacingCheckBox = new JCheckBox("Constant spacing");
        constantSpacingCheckBox.setSelected(true);
        panel.add(constantSpacingCheckBox, gbc);
        
        return panel;
    }
    
    /**
     * Create settings panel
     */
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Settings"));
        
        // Python bridge enable/disable
        enablePythonBridgeCheckBox = new JCheckBox("Use Python Bridge");
        enablePythonBridgeCheckBox.setSelected(true);
        panel.add(enablePythonBridgeCheckBox);
        
        return panel;
    }
    
    /**
     * Create status panel
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(statusLabel, BorderLayout.WEST);
        
        return panel;
    }
    
    /**
     * Setup drag and drop functionality
     */
    private void setupDragAndDrop() {
        new DropTarget(frame, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        loadImageFile(file);
                    }
                    
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Drag and drop failed", e);
                    appendToResults("Failed to load dropped file: " + e.getMessage(), "ERROR");
                    dtde.dropComplete(false);
                }
            }
        });
    }
    
    /**
     * Load image using file chooser
     */
    private void loadImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            loadImageFile(file);
        }
    }
    
    /**
     * Load image file
     * 
     * @param file Image file to load
     */
    private void loadImageFile(File file) {
        try {
            setStatus("Loading image...");
            
            // Load image using ImageJ
            ImagePlus image = IJ.openImage(file.getAbsolutePath());
            if (image == null) {
                throw new RuntimeException("Failed to open image file");
            }
            
            currentImage = image;
            
            // Update UI
            String fileName = file.getName();
            imageInfoLabel.setText(String.format("%s (%dx%d)", 
                fileName, image.getWidth(), image.getHeight()));
            
            // Enable analysis buttons
            analyzeGelButton.setEnabled(true);
            detectLanesButton.setEnabled(true);
            analyzeColonyButton.setEnabled(true);
            
            // Show the image
            image.show();
            
            appendToResults(String.format("Image loaded: %s (%dx%d pixels)", 
                fileName, image.getWidth(), image.getHeight()), "INFO");
            setStatus("Image loaded successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load image", e);
            appendToResults("Failed to load image: " + e.getMessage(), "ERROR");
            setStatus("Failed to load image");
        }
    }
    
    /**
     * Perform gel analysis
     */
    private void analyzeGel() {
        if (currentImage == null) {
            showError("No image loaded");
            return;
        }
        
        try {
            setStatus("Analyzing gel...");
            appendToResults("Starting gel analysis...", "INFO");
            
            // Create configuration
            AnalysisService.AnalysisConfig config = createGelConfig();
            
            // Perform analysis
            AnalysisService.AnalysisResult result = analysisService.analyzeGel(currentImage, config);
            
            // Display results
            displayAnalysisResult(result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Gel analysis failed", e);
            showError("Gel analysis failed: " + e.getMessage());
            setStatus("Analysis failed");
        }
    }
    
    /**
     * Perform lane detection
     */
    private void detectLanes() {
        if (currentImage == null) {
            showError("No image loaded");
            return;
        }
        
        try {
            setStatus("Detecting lanes...");
            appendToResults("Starting lane detection...", "INFO");
            
            // Create configuration for lane detection only
            AnalysisService.AnalysisConfig config = AnalysisService.AnalysisConfig.builder("gel")
                    .parameter("lane_count", laneCountSpinner.getValue())
                    .parameter("constant_spacing", constantSpacingCheckBox.isSelected())
                    .parameter("detect_bands", false)  // Lane detection only
                    .usePythonBridge(enablePythonBridgeCheckBox.isSelected())
                    .build();
            
            // Perform analysis
            AnalysisService.AnalysisResult result = analysisService.analyzeGel(currentImage, config);
            
            // Display results
            displayAnalysisResult(result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Lane detection failed", e);
            showError("Lane detection failed: " + e.getMessage());
            setStatus("Detection failed");
        }
    }
    
    /**
     * Perform colony analysis
     */
    private void analyzeColonies() {
        if (currentImage == null) {
            showError("No image loaded");
            return;
        }
        
        try {
            setStatus("Analyzing colonies...");
            appendToResults("Starting colony analysis...", "INFO");
            
            // Create configuration
            AnalysisService.AnalysisConfig config = AnalysisService.AnalysisConfig.builder("colony")
                    .parameter("detect_plate", true)
                    .parameter("xgal_classification", true)
                    .parameter("color_analysis", true)
                    .usePythonBridge(enablePythonBridgeCheckBox.isSelected())
                    .build();
            
            // Perform analysis
            AnalysisService.AnalysisResult result = analysisService.analyzeColony(currentImage, config);
            
            // Display results
            displayAnalysisResult(result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Colony analysis failed", e);
            showError("Colony analysis failed: " + e.getMessage());
            setStatus("Analysis failed");
        }
    }
    
    /**
     * Create gel analysis configuration
     */
    private AnalysisService.AnalysisConfig createGelConfig() {
        return AnalysisService.AnalysisConfig.builder("gel")
                .parameter("lane_count", laneCountSpinner.getValue())
                .parameter("constant_spacing", constantSpacingCheckBox.isSelected())
                .parameter("detect_bands", true)
                .parameter("mw_calibration", true)
                .parameter("baseline_correction", true)
                .usePythonBridge(enablePythonBridgeCheckBox.isSelected())
                .build();
    }
    
    /**
     * Display analysis results
     * 
     * @param result Analysis results to display
     */
    private void displayAnalysisResult(AnalysisService.AnalysisResult result) {
        if (result == null) {
            appendToResults("No results received", "ERROR");
            return;
        }
        
        if (!result.isSuccess()) {
            appendToResults("Analysis failed: " + result.getError(), "ERROR");
            setStatus("Analysis failed");
            return;
        }
        
        // Display summary
        appendToResults("=== Analysis Complete ===", "INFO");
        appendToResults(String.format("Modality: %s", result.getModality()), "INFO");
        appendToResults(String.format("Processing time: %d ms", result.getProcessingTimeMs()), "INFO");
        appendToResults(String.format("Used Python bridge: %s", result.isUsedPythonBridge()), "INFO");
        
        // Display features
        if (result.getLaneCount() > 0) {
            appendToResults(String.format("Lanes detected: %d", result.getLaneCount()), "RESULT");
            for (Lane lane : result.getLanes()) {
                appendToResults(String.format("  Lane %d: x=%d-%d", 
                    lane.index(), lane.xStart(), lane.xEnd()), "RESULT");
            }
        }
        
        if (result.getBandCount() > 0) {
            appendToResults(String.format("Bands detected: %d", result.getBandCount()), "RESULT");
            for (Band band : result.getBands()) {
                appendToResults(String.format("  Band %d: y=%d, MW=%.1f kDa, area=%.1f", 
                    band.index(), band.y(), band.mwKDa(), band.area()), "RESULT");
            }
        }
        
        if (result.getColonyCount() > 0) {
            appendToResults(String.format("Colonies detected: %d", result.getColonyCount()), "RESULT");
            for (Colony colony : result.getColonies()) {
                appendToResults(String.format("  Colony %d: (%.1f,%.1f), diameter=%.1f, color=%s", 
                    colony.index(), colony.x(), colony.y(), colony.diameter(), 
                    colony.colorClass()), "RESULT");
            }
        }
        
        // Apply overlays
        if (result.hasFeatures()) {
            try {
                analysisService.displayOverlay(currentImage, result);
                appendToResults("Overlay applied to image", "INFO");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to apply overlay", e);
                appendToResults("Failed to apply overlay: " + e.getMessage(), "WARN");
            }
        }
        
        setStatus("Analysis completed successfully");
    }
    
    /**
     * Append message to results area
     * 
     * @param message Message to append
     * @param level Message level (INFO, WARN, ERROR, RESULT)
     */
    private void appendToResults(String message, String level) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String prefix = switch (level) {
                case "ERROR" -> "❌";
                case "WARN" -> "⚠️";
                case "RESULT" -> "✅";
                default -> "ℹ️";
            };
            
            String formattedMessage = String.format("[%s] %s %s%n", 
                timestamp, prefix, message);
            
            resultsArea.append(formattedMessage);
            resultsArea.setCaretPosition(resultsArea.getDocument().getLength());
        });
    }
    
    /**
     * Set status message
     * 
     * @param status Status message
     */
    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
        });
    }
    
    /**
     * Show error dialog
     * 
     * @param message Error message
     */
    private void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
        appendToResults(message, "ERROR");
    }
    
    /**
     * Clean up resources when UI is closed
     */
    public void dispose() {
        if (analysisService != null) {
            try {
                analysisService.shutdown();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down AnalysisService", e);
            }
        }
        
        if (frame != null) {
            frame.dispose();
        }
    }
}