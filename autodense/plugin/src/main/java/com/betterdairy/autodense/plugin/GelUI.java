package com.betterdairy.autodense.plugin;

import org.scijava.Context;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.Roi;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import javax.sound.sampled.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.concurrent.atomic.AtomicBoolean;

import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.analysis.BandDetector;
import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.model.Models.Band;
import com.betterdairy.autodense.workflow.WorkflowPreset;
import com.betterdairy.autodense.workflow.WorkflowPresetManager;
import com.betterdairy.autodense.orchestrator.GeminiOrchestrator;

/** AI-Powered Gel Analysis Interface with Natural Language Control */
public class GelUI {
    // Note: This class is being deprecated in favor of GeminiOrchestrator
    // Keeping minimal implementation for backward compatibility
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;
    private HttpClient httpClient;
    private ImagePlus currentImage;
    private GeminiOrchestrator orchestrator;
    // Removed: lastGeminiAnalysis (no longer used after UI cleanup)
    // Conversation history for context
    private List<String> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10; // Keep last 10 exchanges
    
    // Workflow preset management
    private WorkflowPresetManager workflowManager;
    
    // Voice input components
    private JButton voiceButton;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    
    // Document upload components  
    private List<File> uploadedDocuments = new ArrayList<>();
    
    public GelUI(Context context) {
        // Context not used in new architecture
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Initialize GeminiOrchestrator (proper architecture)
        String geminiApiKey = System.getProperty("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY"));
        if (geminiApiKey != null && !geminiApiKey.isEmpty()) {
            this.orchestrator = new GeminiOrchestrator(geminiApiKey);
            System.out.println("✓ GeminiOrchestrator initialized with handle-based architecture");
        } else {
            System.out.println("⚠ GEMINI_API_KEY not found - AI features not available");
        }
        
        // Initialize workflow preset manager
        this.workflowManager = new WorkflowPresetManager();
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            createMainWindow();
            frame.setVisible(true);
            
            // Note: Let user click on input field to focus manually
            
            // Welcome message
            appendToChatArea("🤖 AutoDense AI Assistant Ready!\n");
            appendToChatArea("💡 Type your message below and press Enter to send\n");
            appendToChatArea("📝 Examples:\n");
            appendToChatArea("  🧬 Gel Analysis:\n");
            appendToChatArea("    • Detect 12 lanes in this gel\n");
            appendToChatArea("    • Find all protein bands\n");
            appendToChatArea("    • Quantify band intensities\n");
            appendToChatArea("  🦠 Colony Analysis:\n");
            appendToChatArea("    • Count all colonies on this plate\n");
            appendToChatArea("    • Count only pink colonies\n");
            appendToChatArea("    • Measure colony sizes\n");
            appendToChatArea("\n🔬 I can analyze gels, agar plates, and laboratory workflows.\n\n");
            
            // Check AI server connection
            checkAIConnection();
        });
    }
    
    private void createMainWindow() {
        System.out.println("DEBUG: Creating main window");
        frame = new JFrame("🤖 AutoDense - AI Gel Analysis");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        // Create menu bar
        JMenuBar menuBar = createMenuBar();
        frame.setJMenuBar(menuBar);
        frame.setSize(new Dimension(900, 700));
        System.out.println("DEBUG: Frame created, setting up layout");
        frame.setLocationByPlatform(true);
        
        // Main layout
        frame.setLayout(new BorderLayout());
        
        // Header panel
        JPanel headerPanel = createHeaderPanel();
        frame.add(headerPanel, BorderLayout.NORTH);
        
        // Chat panel (center)
        JPanel chatPanel = createChatPanel();
        frame.add(chatPanel, BorderLayout.CENTER);
        
        // Bottom panel - combine input and status
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        // Input panel 
        JPanel inputPanel = createInputPanel();
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        
        // Status bar
        statusLabel = new JLabel("🟢 AI Server Connected - Ready for commands");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        
        frame.add(bottomPanel, BorderLayout.SOUTH);
        System.out.println("DEBUG: Bottom panel with input and status added");
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        
        // Left side: Title and Analysis Type Selector
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        
        JLabel titleLabel = new JLabel("🔬 AutoDense AI Assistant");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        leftPanel.add(titleLabel);
        
        // Analysis Type Selector
        leftPanel.add(Box.createHorizontalStrut(20));
        JLabel analysisLabel = new JLabel("Analysis Type:");
        analysisLabel.setFont(analysisLabel.getFont().deriveFont(Font.PLAIN, 12f));
        leftPanel.add(analysisLabel);
        
        leftPanel.add(Box.createHorizontalStrut(5));
        JComboBox<String> analysisTypeCombo = new JComboBox<>(new String[]{
            "🧬 Gel Densitometry", "🦠 Colony Counting"
        });
        analysisTypeCombo.setToolTipText("Choose analysis workflow: gel bands or bacterial colonies");
        analysisTypeCombo.addActionListener(e -> {
            String selected = (String) analysisTypeCombo.getSelectedItem();
            boolean isColonyMode = selected.contains("Colony");
            updateUIForAnalysisType(isColonyMode);
        });
        leftPanel.add(analysisTypeCombo);
        
        panel.add(leftPanel, BorderLayout.WEST);
        
        // Quick actions
        JPanel actionsPanel = new JPanel(new FlowLayout());
        
        JButton loadImageButton = new JButton("📁 Load Image");
        loadImageButton.setToolTipText("Load gel image for analysis");
        loadImageButton.addActionListener(e -> loadImage());
        actionsPanel.add(loadImageButton);
        
        JButton demoButton = new JButton("🎭 Demo");
        demoButton.setToolTipText("Load predefined workflow demos with chat examples");
        demoButton.addActionListener(e -> showDemoDialog());
        actionsPanel.add(demoButton);
        
        JButton workflowsButton = new JButton("⚙️ Workflows");
        workflowsButton.setToolTipText("Manage workflow presets and create custom workflows");
        workflowsButton.addActionListener(e -> showWorkflowDialog());
        actionsPanel.add(workflowsButton);
        
        JButton clearChatButton = new JButton("🗑️ Clear");
        clearChatButton.setToolTipText("Clear conversation history");
        clearChatButton.addActionListener(e -> clearConversation());
        actionsPanel.add(clearChatButton);
        
        JButton uploadButton = new JButton("📄 Upload Docs");
        uploadButton.setToolTipText("Upload CSV/Excel/TXT files with lane information");
        uploadButton.addActionListener(e -> showDocumentUploadDialog());
        actionsPanel.add(uploadButton);
        
        JButton helpButton = new JButton("❓ Help");
        helpButton.addActionListener(e -> showHelp());
        actionsPanel.add(helpButton);
        
        panel.add(actionsPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));
        
        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        chatArea.setBackground(new Color(252, 252, 253));
        chatArea.setMargin(new Insets(10, 10, 10, 10));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(850, 400));
        
        // Add drag & drop support to multiple components
        setupDragAndDrop(scrollPane);
        setupDragAndDrop(chatArea);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));
        
        // Create a working input field using JTextArea for better control
        JTextArea inputArea = new JTextArea(2, 50);
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLoweredBevelBorder(),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        
        // Use JTextArea instead of JTextField for better compatibility
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setPreferredSize(new Dimension(600, 50));
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // Voice input button
        voiceButton = new JButton("🎤");
        voiceButton.setPreferredSize(new Dimension(50, 50));
        voiceButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        voiceButton.setToolTipText("Hold to record voice input (experimental)");
        voiceButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                startVoiceRecording();
            }
            
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                stopVoiceRecording();
            }
        });
        
        // Send button - now just sends the text from the area
        sendButton = new JButton("Send 📤");
        sendButton.setPreferredSize(new Dimension(100, 50));
        sendButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        sendButton.setToolTipText("Send message (or press Ctrl+Enter)");
        
        // Action to send message
        Runnable sendAction = () -> {
            String command = inputArea.getText().trim();
            if (!command.isEmpty()) {
                sendCommand(command);
                inputArea.setText("");
                inputArea.requestFocusInWindow();
            }
        };
        
        // Send button action
        sendButton.addActionListener(e -> sendAction.run());
        
        // Add keyboard shortcuts
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "send");
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendAction.run();
            }
        });
        
        // Also support plain Enter for single-line messages
        inputArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && !e.isControlDown() && !e.isShiftDown()) {
                    if (!inputArea.getText().contains("\n") || inputArea.getText().trim().endsWith("\n")) {
                        e.consume();
                        sendAction.run();
                    }
                }
            }
        });
        
        // Button panel for voice and send
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(voiceButton);
        buttonPanel.add(sendButton);
        
        panel.add(inputScroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);
        
        // Request focus after UI is built
        SwingUtilities.invokeLater(() -> {
            inputArea.requestFocusInWindow();
            inputArea.setCaretPosition(0);
        });
        
        // Store reference for other methods
        this.inputField = new JTextField(); // Keep for compatibility
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateField(); }
            public void removeUpdate(DocumentEvent e) { updateField(); }
            public void changedUpdate(DocumentEvent e) { updateField(); }
            private void updateField() {
                inputField.setText(inputArea.getText());
            }
        });
        
        return panel;
    }
    
    private void sendCommand(String command) {
        // Display user command with timestamp
        String timestamp = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        appendToChatArea(String.format("[%s] 👤 You: %s\n", timestamp, command));
        
        // Disable send button while processing (keep input enabled for typing next message)
        sendButton.setEnabled(false);
        statusLabel.setText("🤔 AI thinking...");
        
        // Process command in background thread
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return processCommand(command);
            }
            
            @Override
            protected void done() {
                try {
                    String response = get();
                    String timestamp = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
                    appendToChatArea(String.format("[%s] 🤖 AutoDense: %s\n\n", timestamp, response));
                    statusLabel.setText("🟢 Ready for next command");
                } catch (Exception e) {
                    appendToChatArea("❌ Error: " + e.getMessage() + "\n\n");
                    statusLabel.setText("🔴 Error processing command");
                }
                
                // Re-enable send button
                sendButton.setEnabled(true);
            }
        };
        
        worker.execute();
    }
    
    private String processCommand(String command) throws Exception {
        System.out.println("DEBUG: processCommand() called with: " + command);
        System.out.flush();
        
        try {
            // Add to conversation history
            conversationHistory.add("user: " + command);
            System.out.println("DEBUG: Added to history, size now: " + conversationHistory.size());
            System.out.flush();
        } catch (Exception e) {
            System.out.println("DEBUG: Exception adding to history: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        if (conversationHistory.size() > MAX_HISTORY * 2) {
            conversationHistory = conversationHistory.subList(
                conversationHistory.size() - MAX_HISTORY * 2, 
                conversationHistory.size()
            );
        }
        System.out.println("DEBUG: History processing complete");
        System.out.flush();
        
        // Build messages with history for context
        System.out.println("DEBUG: Building messages JSON");
        System.out.flush();
        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[\n");
        messagesJson.append("  {\"role\": \"system\", \"content\": \"You are AutoDense AI for laboratory image analysis. You analyze: 1) Gel electrophoresis (SDS-PAGE, DNA gels) 2) Agar plates with microbial colonies (yeast, bacteria). When users ask you to perform analysis, you should EXECUTE the analysis immediately and show results, not just describe what you would do. Parse user requests for: Gels - Number of lanes, lane types (ladder vs sample), analysis type (detect, quantify, etc). Plates - Colony counting, color analysis, size measurements. Always execute the requested action and provide results.\"},\n");
        System.out.println("DEBUG: Added system message");
        System.out.flush();
        
        // Add conversation history
        System.out.println("DEBUG: Processing conversation history, size: " + conversationHistory.size());
        System.out.flush();
        for (int i = 0; i < conversationHistory.size() - 1; i++) {
            System.out.println("DEBUG: Processing history item " + i + ": " + conversationHistory.get(i));
            System.out.flush();
            String msg = conversationHistory.get(i);
            String role = msg.startsWith("user:") ? "user" : "assistant";
            String content = msg.substring(msg.indexOf(":") + 1).trim();
            messagesJson.append(String.format("  {\"role\": \"%s\", \"content\": \"%s\"},\n", 
                role, content.replace("\"", "\\\"").replace("\n", "\\n")));
        }
        System.out.println("DEBUG: History loop complete");
        System.out.flush();
        
        // Add current message
        System.out.println("DEBUG: Adding current message: " + command);
        System.out.flush();
        messagesJson.append(String.format("  {\"role\": \"user\", \"content\": \"%s\"}\n", 
            command.replace("\"", "\\\"").replace("\n", "\\n")));
        messagesJson.append("]");
        System.out.println("DEBUG: Messages JSON built successfully");
        System.out.flush();
        
        // Create request to AI server
        System.out.println("DEBUG: Creating AI server request body");
        System.out.flush();
        String requestBody = String.format("""
            {
              "messages": %s,
              "temperature": 0.1,
              "max_tokens": 512
            }""", messagesJson.toString());
        System.out.println("DEBUG: Request body created, length: " + requestBody.length());
        System.out.flush();
        
        // Use GeminiOrchestrator for proper handle-based architecture
        System.out.println("DEBUG: Using GeminiOrchestrator for command processing");
        System.out.flush();
        
        if (orchestrator == null) {
            return "❌ GeminiOrchestrator not available. Please check API key configuration.";
        }
        
        // Get current image (can be null for text-only commands)
        ImagePlus currentImg = getCurrentImage();
        System.out.println("DEBUG: Current image: " + (currentImg != null ? currentImg.getTitle() : "none"));
        System.out.flush();
        
        try {
            // Use orchestrator's processCommand method (proper architecture)
            var futureResult = orchestrator.processCommand(command, currentImg);
            var orchestrationResult = futureResult.get(); // Block for UI thread
            
            System.out.println("DEBUG: Got orchestration result: " + orchestrationResult);
            if (orchestrationResult.toolResult != null) {
                System.out.println("DEBUG: Tool result JSON: " + orchestrationResult.toolResult.toString(2));
            }
            System.out.flush();
            
            if (orchestrationResult.success) {
                // ARCHITECTURAL FIX: Show tool results as primary response, not Gemini's orchestration plan
                String fullResponse = "";
                
                if (orchestrationResult.toolResult != null && !orchestrationResult.toolResult.optBoolean("error", false)) {
                    // Primary response: actual tool execution results
                    String message = orchestrationResult.toolResult.optString("message", "");
                    if (!message.isEmpty()) {
                        fullResponse = "✅ " + message;
                    } else {
                        // Extract meaningful results from tool response
                        if (orchestrationResult.toolResult.has("lanes_found")) {
                            int lanes = orchestrationResult.toolResult.optInt("lanes_found", 0);
                            fullResponse = "✅ Detected " + lanes + " lanes successfully";
                        } else if (orchestrationResult.toolResult.has("bands_total")) {
                            int bands = orchestrationResult.toolResult.optInt("bands_total", 0);
                            fullResponse = "✅ Detected " + bands + " protein bands";
                        } else if (orchestrationResult.toolResult.has("total_colonies")) {
                            int colonies = orchestrationResult.toolResult.optInt("total_colonies", 0);
                            fullResponse = "✅ Counted " + colonies + " colonies";
                        } else if (orchestrationResult.toolResult.has("success")) {
                            String action = orchestrationResult.geminiResponse.action;
                            fullResponse = "✅ " + action.replace("_", " ") + " completed successfully";
                        } else {
                            fullResponse = "✅ Tool executed successfully";
                        }
                    }
                    
                    // Add technical details if available
                    StringBuilder details = new StringBuilder();
                    if (orchestrationResult.toolResult.has("lanes_found")) {
                        details.append("\n📏 Lanes: ").append(orchestrationResult.toolResult.optInt("lanes_found"));
                    }
                    if (orchestrationResult.toolResult.has("bands_total")) {
                        details.append("\n🧬 Bands: ").append(orchestrationResult.toolResult.optInt("bands_total"));
                    }
                    if (orchestrationResult.toolResult.has("visual_feedback_generated") && 
                        orchestrationResult.toolResult.optBoolean("visual_feedback_generated", false)) {
                        details.append("\n🖼️ Visual feedback PNG generated");
                        // Show PNG file paths if available
                        if (orchestrationResult.toolResult.has("visual_feedback_files")) {
                            org.json.JSONArray files = orchestrationResult.toolResult.optJSONArray("visual_feedback_files");
                            if (files != null && files.length() > 0) {
                                details.append("\n📁 PNG file: ").append(files.optString(0));
                            }
                        }
                    }
                    // Also check for direct PNG generation (from renderOverlayPng)
                    if (orchestrationResult.toolResult.has("png_path")) {
                        details.append("\n🖼️ PNG saved to: ").append(orchestrationResult.toolResult.optString("png_path"));
                    }
                    if (orchestrationResult.toolResult.has("exported_files")) {
                        details.append("\n📁 Files exported");
                    }
                    if (details.length() > 0) {
                        fullResponse += details.toString();
                    }
                } else {
                    // Fallback to Gemini's orchestration message only if no tool results
                    fullResponse = orchestrationResult.geminiResponse.analysis;
                }
                
                // Add AI response to history
                conversationHistory.add("assistant: " + fullResponse);
                
                return fullResponse;
            } else {
                String errorMessage = "❌ Processing failed: " + orchestrationResult.errorMessage;
                conversationHistory.add("assistant: " + errorMessage);
                return errorMessage;
            }
            
        } catch (Exception e) {
            System.out.println("DEBUG: GeminiOrchestrator processing failed: " + e.getMessage());
            System.out.flush();
            e.printStackTrace();
            
            String errorMessage = "❌ Command processing failed: " + e.getMessage();
            conversationHistory.add("assistant: " + errorMessage);
            return errorMessage;
        }
    }
    
    
    private void clearConversation() {
        // Clear conversation history
        conversationHistory.clear();
        chatArea.setText("");
        
        // Show welcome message again
        appendToChatArea("🤖 AutoDense AI Assistant Ready!\n");
        appendToChatArea("💡 Type your message below and press Enter to send\n");
        appendToChatArea("📝 Examples:\n");
        appendToChatArea("  🧬 Gel Analysis:\n");
        appendToChatArea("    • Detect 12 lanes in this gel\n");
        appendToChatArea("    • Find all protein bands\n");
        appendToChatArea("    • Quantify band intensities\n");
        appendToChatArea("  🦠 Colony Analysis:\n");
        appendToChatArea("    • Count all colonies on this plate\n");
        appendToChatArea("    • Count only pink colonies\n");
        appendToChatArea("    • Measure colony sizes\n");
        appendToChatArea("\n🔬 I can analyze gels, agar plates, and laboratory workflows.\n\n");
        
        statusLabel.setText("🟢 Conversation cleared - Ready for commands");
    }
    
    private void checkAIConnection() {
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    // Check if Gemini API key is available
                    String apiKey = System.getProperty("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY"));
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        return false;
                    }
                    
                    // Test GeminiOrchestrator availability
                    return orchestrator != null;
                } catch (Exception e) {
                    return false;
                }
            }
            
            @Override
            protected void done() {
                try {
                    boolean connected = get();
                    if (connected) {
                        statusLabel.setText("🟢 Gemini AI Connected - Ready for analysis");
                        statusLabel.setForeground(new Color(0, 120, 0));
                    } else {
                        statusLabel.setText("🔴 Gemini API not available - Check configuration");
                        statusLabel.setForeground(Color.RED);
                        appendToChatArea("⚠️  GeminiOrchestrator not available. Please check:\n");
                        appendToChatArea("   • GEMINI_API_KEY environment variable is set\n");
                        appendToChatArea("   • Internet connection is working\n\n");
                    }
                } catch (Exception e) {
                    statusLabel.setText("🔴 Connection error: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                }
            }
        };
        
        worker.execute();
    }
    
    private void showHelp() {
        String helpText = """
            🔬 AutoDense AI Commands:
            
            🔍 Detection:
            • "Detect bands in lanes 1-5"
            • "Find bands with high sensitivity"
            • "Auto-detect all lanes"
            
            📊 Quantification:
            • "Quantify detected bands"
            • "Measure bands using median background"
            • "Calculate signal intensities"
            
            🧬 Markers & Calibration:
            • "Set protein ladder in lane 1"
            • "Use Bio-Rad marker"
            • "Calibrate molecular weights"
            
            📈 Analysis:
            • "Normalize to lane total"
            • "Compare lanes 2 and 3"
            • "Export results as CSV"
            
            💡 Tips:
            • Be specific about lane numbers
            • Ask for help with any gel analysis task
            • Commands execute automatically when possible
            """;
        
        JOptionPane.showMessageDialog(frame, helpText, 
            "AutoDense AI Help", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void loadImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                       name.endsWith(".png") || name.endsWith(".tif") || 
                       name.endsWith(".tiff") || name.endsWith(".bmp");
            }
            
            @Override
            public String getDescription() {
                return "Image files (*.jpg, *.png, *.tif, *.bmp)";
            }
        });
        
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadImageFile(selectedFile);
        }
    }
    
    private void loadImageFile(File file) {
        try {
            appendToChatArea("📁 Loading: " + file.getName() + "\n");
            
            // Load image using ImageJ
            currentImage = IJ.openImage(file.getAbsolutePath());
            if (currentImage != null) {
                currentImage.show();
                appendToChatArea("✅ Image loaded successfully! Ready for analysis.\n");
                appendToChatArea("💡 Try: 'Detect bands' or 'Analyze this gel'\n\n");
                
                // Update status
                statusLabel.setText("🖼️ Image loaded: " + file.getName());
            } else {
                appendToChatArea("❌ Failed to load image: " + file.getName() + "\n\n");
            }
        } catch (Exception e) {
            appendToChatArea("❌ Error loading image: " + e.getMessage() + "\n\n");
        }
    }
    
    private void setupDragAndDrop(Component component) {
        DropTarget dropTarget = new DropTarget(component, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                System.out.println("DEBUG: Drag entered, checking data flavors");
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    appendToChatArea("📎 Drop image file here...\n");
                    System.out.println("DEBUG: Drag accepted");
                } else {
                    dtde.rejectDrag();
                    System.out.println("DEBUG: Drag rejected - unsupported data flavor");
                }
            }
            
            @Override
            public void dragOver(DropTargetDragEvent dtde) {}
            
            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            
            @Override
            public void dragExit(DropTargetEvent dte) {}
            
            @Override
            public void drop(DropTargetDropEvent dtde) {
                System.out.println("DEBUG: Drop event received");
                try {
                    // Check if the data flavor is supported before accepting
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        System.out.println("DEBUG: Data flavor supported, processing drop");
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        
                        @SuppressWarnings("unchecked")
                        java.util.List<File> files = (java.util.List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                        
                        if (!files.isEmpty()) {
                            File file = files.get(0);
                            appendToChatArea("🔄 Loading dropped file: " + file.getName() + "\n");
                            loadImageFile(file);
                        } else {
                            appendToChatArea("❌ No files in drop\n");
                        }
                        
                        dtde.dropComplete(true);
                    } else {
                        appendToChatArea("❌ Unsupported file type for drag & drop\n");
                        dtde.rejectDrop();
                    }
                } catch (Exception e) {
                    appendToChatArea("❌ Error with drag & drop: " + e.getMessage() + "\n");
                    dtde.dropComplete(false);
                }
            }
        });
        component.setDropTarget(dropTarget);
    }
    
    
    // Removed old AI analysis methods - now using Gemini directly
    /*
    private AIAnalysisResult analyzeGelWithAI(ImagePlus imp) throws Exception {
        // Save image temporarily for AI analysis
        String tempPath = System.getProperty("java.io.tmpdir") + "/autodense_temp.jpg";
        IJ.save(imp, tempPath);
        
        // Encode image as base64 for vision model
        byte[] imageBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(tempPath));
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        
        // Create vision analysis request
        String requestBody = String.format("""
            {
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {
                      "type": "text",
                      "text": "Analyze this gel electrophoresis image. Count the visible lanes and assess their characteristics. Provide: 1) Number of lanes (count vertical bands/tracks) 2) Lane width (narrow=0.3, normal=0.55, wide=0.8) 3) Lane spacing (tight/normal/wide) 4) Any offset needed 5) Gel type (protein/DNA). Be precise with lane counting."
                    },
                    {
                      "type": "image_url",
                      "image_url": {
                        "url": "data:image/jpeg;base64,%s"
                      }
                    }
                  ]
                }
              ],
              "temperature": 0.1,
              "max_tokens": 512
            }""", base64Image);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:8080/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(45))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("AI Vision analysis failed: " + response.statusCode());
        }
        
        // Parse AI response and extract parameters
        String responseBody = response.body();
        int contentStart = responseBody.indexOf("\"content\":\"") + 11;
        int contentEnd = responseBody.lastIndexOf("\"");
        
        if (contentStart > 10 && contentEnd > contentStart) {
            String aiAnalysis = responseBody.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
            
            // Extract parameters from AI analysis
            return parseAIAnalysis(aiAnalysis, imp);
        } else {
            throw new RuntimeException("Could not parse AI vision response");
        }
    }
    
    private AIAnalysisResult parseAIAnalysis(String analysis, ImagePlus imp) {
        AIAnalysisResult result = new AIAnalysisResult();
        
        // Parse the AI response to extract parameters
        String lowerAnalysis = analysis.toLowerCase();
        
        // Extract lane count
        if (lowerAnalysis.contains("lanes")) {
            // Look for numbers in the context of lanes
            String[] words = analysis.split("\\s+");
            for (int i = 0; i < words.length - 1; i++) {
                if (words[i+1].toLowerCase().contains("lane")) {
                    try {
                        int count = Integer.parseInt(words[i].replaceAll("[^0-9]", ""));
                        if (count > 0 && count <= 20) {
                            result.expectedLanes = count;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        
        // Default fallback - estimate from image width
        if (result.expectedLanes == 0) {
            result.expectedLanes = Math.max(4, Math.min(12, imp.getWidth() / 60));
        }
        
        // Extract lane width
        if (lowerAnalysis.contains("narrow") || lowerAnalysis.contains("thin")) {
            result.laneWidthFraction = 0.4;
        } else if (lowerAnalysis.contains("wide") || lowerAnalysis.contains("broad")) {
            result.laneWidthFraction = 0.7;
        } else {
            result.laneWidthFraction = 0.55; // normal
        }
        
        // Extract spacing/offset information
        if (lowerAnalysis.contains("tight") || lowerAnalysis.contains("close")) {
            result.laneWidthFraction *= 1.1; // Slightly wider for tight spacing
        }
        
        if (lowerAnalysis.contains("offset") || lowerAnalysis.contains("shift")) {
            // Could extract specific offset values here
            result.gridOffsetFraction = 0.05;
        } else {
            result.gridOffsetFraction = 0.0;
        }
        
        // Set other parameters
        result.constantSpacing = true; // Usually true for standard gels
        result.preprocessForDetection = true;
        
        return result;
    }
    
    // Helper class for AI analysis results - REMOVED - now using Gemini
    */
    
    
    private void showDemoDialog() {
        JDialog demoDialog = new JDialog(frame, "🎭 AutoDense Workflow Demos", true);
        demoDialog.setSize(600, 500);
        demoDialog.setLocationRelativeTo(frame);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Demo list
        String[] demoTitles = {
            "🧬 12-Lane SDS-PAGE with MW Marker",
            "🧬 15-Lane Protein Gel Analysis", 
            "🦠 Colony Counting - X-gal Blue/White",
            "🦠 Bacterial Growth Quantification",
            "🔬 Custom Gel Workflow Template"
        };
        
        JList<String> demoList = new JList<>(demoTitles);
        demoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        demoList.setSelectedIndex(0);
        
        JScrollPane listScroll = new JScrollPane(demoList);
        listScroll.setPreferredSize(new Dimension(280, 300));
        
        // Demo description panel
        JTextArea descArea = new JTextArea();
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBackground(new Color(245, 245, 245));
        descArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(300, 300));
        
        // Update description when selection changes
        demoList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selected = demoList.getSelectedIndex();
                descArea.setText(getDemoDescription(selected));
            }
        });
        
        // Initial description
        descArea.setText(getDemoDescription(0));
        
        // Layout
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(listScroll, BorderLayout.WEST);
        centerPanel.add(descScroll, BorderLayout.CENTER);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton loadDemoButton = new JButton("🎬 Load Demo");
        loadDemoButton.addActionListener(e -> {
            int selected = demoList.getSelectedIndex();
            if (selected >= 0) {
                loadDemo(selected);
                demoDialog.dispose();
            }
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> demoDialog.dispose());
        
        buttonPanel.add(loadDemoButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        demoDialog.add(mainPanel);
        demoDialog.setVisible(true);
    }
    
    private String getDemoDescription(int demoIndex) {
        return switch (demoIndex) {
            case 0 -> """
                🧬 12-Lane SDS-PAGE Analysis with MW Marker
                
                Workflow:
                • 12-lane polyacrylamide gel
                • Lane 1: Molecular weight standards
                • Lanes 2-12: Protein samples
                • Automatic MW calibration
                
                Natural Language Examples:
                "Analyze this 12-lane gel with MW marker in lane 1"
                "Detect protein bands and calculate molecular weights"
                "Export results with MW calculations to CSV"
                
                This demo shows the complete workflow for SDS-PAGE analysis with automatic molecular weight determination.
                """;
            case 1 -> """
                🧬 15-Lane Protein Gel Analysis
                
                Workflow:
                • 15-lane large format gel
                • High-resolution band detection
                • Optimized for faint bands
                
                Natural Language Examples:
                "Find 15 lanes in this protein gel"
                "Use high sensitivity for faint bands"
                "Quantify all bands with background subtraction"
                
                Demonstrates handling of large format gels with many samples.
                """;
            case 2 -> """
                🦠 X-gal Blue/White Colony Screening
                
                Workflow:
                • Bacterial transformation plates
                • Blue/white colony classification
                • X-gal indicator system
                • Automated colony counting by color
                
                Natural Language Examples:
                "Count blue and white colonies separately"
                "Classify colonies by X-gal reaction"
                "Export colony data with positions and colors"
                
                Perfect for cloning experiments and transformation efficiency.
                """;
            case 3 -> """
                🦠 Bacterial Growth Quantification
                
                Workflow:
                • Growth curve analysis plates
                • Colony size measurements
                • Statistical analysis
                
                Natural Language Examples:
                "Measure all colony sizes on this plate"
                "Group colonies by size ranges"
                "Calculate growth statistics"
                
                Ideal for antibiotic sensitivity and growth studies.
                """;
            case 4 -> """
                🔬 Custom Workflow Template
                
                Create your own reusable workflow:
                • Define gel/plate parameters
                • Set default lane counts
                • Configure analysis preferences
                • Save natural language templates
                
                This option lets you build custom workflows for your specific laboratory protocols.
                """;
            default -> "Unknown demo selected.";
        };
    }
    
    private void loadDemo(int demoIndex) {
        appendToChatArea("🎭 Loading demo: " + getDemoTitle(demoIndex) + "\n\n");
        
        // Clear any existing conversation
        conversationHistory.clear();
        
        // Load demo-specific sample data and chat examples
        switch (demoIndex) {
            case 0 -> load12LaneSdPageDemo();
            case 1 -> load15LaneProteinDemo();
            case 2 -> loadXgalColonyDemo();
            case 3 -> loadGrowthQuantDemo();
            case 4 -> loadCustomWorkflowDemo();
        }
        
        statusLabel.setText("🎭 Demo loaded - Try the example commands!");
    }
    
    private String getDemoTitle(int index) {
        String[] titles = {
            "12-Lane SDS-PAGE with MW Marker",
            "15-Lane Protein Gel Analysis", 
            "Colony Counting - X-gal Blue/White",
            "Bacterial Growth Quantification",
            "Custom Workflow Template"
        };
        return titles[index];
    }
    
    private void load12LaneSdPageDemo() {
        appendToChatArea("🧬 12-Lane SDS-PAGE Demo Loaded!\n\n");
        appendToChatArea("📝 Try these natural language commands:\n");
        appendToChatArea("  💬 \"Analyze this 12-lane gel with MW marker in lane 1\"\n");
        appendToChatArea("  💬 \"Detect protein bands and calculate molecular weights\"\n");
        appendToChatArea("  💬 \"Export results with MW calculations to CSV\"\n");
        appendToChatArea("  💬 \"Show me the quantification results\"\n\n");
        appendToChatArea("🎯 Demo Features:\n");
        appendToChatArea("  • Automatic 12-lane detection\n");
        appendToChatArea("  • MW marker calibration in lane 1\n");
        appendToChatArea("  • High-precision band quantification\n");
        appendToChatArea("  • Professional CSV export\n\n");
        appendToChatArea("💡 Load a gel image and try the commands above!\n\n");
    }
    
    private void load15LaneProteinDemo() {
        appendToChatArea("🧬 15-Lane Protein Gel Demo Loaded!\n\n");
        appendToChatArea("📝 Try these natural language commands:\n");
        appendToChatArea("  💬 \"Find 15 lanes in this protein gel\"\n");
        appendToChatArea("  💬 \"Use high sensitivity for faint bands\"\n");
        appendToChatArea("  💬 \"Quantify all bands with background subtraction\"\n");
        appendToChatArea("  💬 \"Create overlay showing all detected features\"\n\n");
        appendToChatArea("🎯 Demo Features:\n");
        appendToChatArea("  • Large format gel handling\n");
        appendToChatArea("  • Enhanced sensitivity for weak bands\n");
        appendToChatArea("  • Comprehensive quantification\n");
        appendToChatArea("  • Visual overlay generation\n\n");
        appendToChatArea("💡 Load a 15-lane gel and try the commands above!\n\n");
    }
    
    private void loadXgalColonyDemo() {
        appendToChatArea("🦠 X-gal Colony Screening Demo Loaded!\n\n");
        appendToChatArea("📝 Try these natural language commands:\n");
        appendToChatArea("  💬 \"Count blue and white colonies separately\"\n");
        appendToChatArea("  💬 \"Classify colonies by X-gal reaction\"\n");
        appendToChatArea("  💬 \"Show me transformation efficiency\"\n");
        appendToChatArea("  💬 \"Export colony data with positions and colors\"\n\n");
        appendToChatArea("🎯 Demo Features:\n");
        appendToChatArea("  • Automatic color classification\n");
        appendToChatArea("  • Blue/white discrimination\n");
        appendToChatArea("  • Position mapping\n");
        appendToChatArea("  • Transformation statistics\n\n");
        appendToChatArea("💡 Load a bacterial plate and try the commands above!\n\n");
    }
    
    private void loadGrowthQuantDemo() {
        appendToChatArea("🦠 Growth Quantification Demo Loaded!\n\n");
        appendToChatArea("📝 Try these natural language commands:\n");
        appendToChatArea("  💬 \"Measure all colony sizes on this plate\"\n");
        appendToChatArea("  💬 \"Group colonies by size ranges\"\n");
        appendToChatArea("  💬 \"Calculate growth statistics\"\n");
        appendToChatArea("  💬 \"Show size distribution histogram\"\n\n");
        appendToChatArea("🎯 Demo Features:\n");
        appendToChatArea("  • Precise size measurements\n");
        appendToChatArea("  • Statistical analysis\n");
        appendToChatArea("  • Size-based grouping\n");
        appendToChatArea("  • Growth curve data export\n\n");
        appendToChatArea("💡 Load a bacterial growth plate and try the commands above!\n\n");
    }
    
    private void loadCustomWorkflowDemo() {
        appendToChatArea("🔬 Custom Workflow Builder Demo Loaded!\n\n");
        appendToChatArea("📝 Create your own workflow with commands like:\n");
        appendToChatArea("  💬 \"Create workflow: 8-lane mini-gel with ladder\"\n");
        appendToChatArea("  💬 \"Set default: 12 lanes, high sensitivity\"\n");
        appendToChatArea("  💬 \"Save workflow as 'Weekly Protein Analysis'\"\n");
        appendToChatArea("  💬 \"Load my saved workflow for SDS-PAGE\"\n\n");
        appendToChatArea("🎯 Demo Features:\n");
        appendToChatArea("  • Workflow template creation\n");
        appendToChatArea("  • Parameter presets\n");
        appendToChatArea("  • Natural language workflow definition\n");
        appendToChatArea("  • Reusable analysis protocols\n\n");
        appendToChatArea("💡 Design workflows that match your lab's protocols!\n\n");
    }
    
    private void showWorkflowDialog() {
        JDialog workflowDialog = new JDialog(frame, "⚙️ Workflow Presets Manager", true);
        workflowDialog.setSize(800, 600);
        workflowDialog.setLocationRelativeTo(frame);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Create tabbed pane for different views
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Tab 1: Browse and use existing presets
        JPanel browsePanel = createBrowsePresetsPanel();
        tabbedPane.addTab("📚 Browse Presets", browsePanel);
        
        // Tab 2: Create custom workflow
        JPanel createPanel = createCustomWorkflowPanel();
        tabbedPane.addTab("🛠️ Create Custom", createPanel);
        
        // Tab 3: Manage presets (edit/delete)
        JPanel managePanel = createManagePresetsPanel();
        tabbedPane.addTab("⚙️ Manage", managePanel);
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Close button
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> workflowDialog.dispose());
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        workflowDialog.add(mainPanel);
        workflowDialog.setVisible(true);
    }
    
    private JPanel createBrowsePresetsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Preset list
        List<WorkflowPreset> presets = workflowManager.getAllPresets();
        String[] presetNames = presets.stream()
            .map(WorkflowPreset::getName)
            .toArray(String[]::new);
        
        JList<String> presetList = new JList<>(presetNames);
        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(presetList);
        listScroll.setPreferredSize(new Dimension(300, 400));
        
        // Preset details panel
        JTextArea detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setBackground(new Color(248, 248, 248));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setPreferredSize(new Dimension(400, 400));
        
        // Update details when selection changes
        presetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = presetList.getSelectedValue();
                if (selected != null) {
                    WorkflowPreset preset = workflowManager.getPreset(selected);
                    detailsArea.setText(formatPresetDetails(preset));
                }
            }
        });
        
        // Layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detailsScroll);
        splitPane.setResizeWeight(0.4);
        panel.add(splitPane, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton loadPresetButton = new JButton("📥 Load Preset");
        loadPresetButton.addActionListener(e -> {
            String selected = presetList.getSelectedValue();
            if (selected != null) {
                loadWorkflowPreset(selected);
            }
        });
        
        JButton copyExamplesButton = new JButton("📋 Copy Examples");
        copyExamplesButton.addActionListener(e -> {
            String selected = presetList.getSelectedValue();
            if (selected != null) {
                copyPresetExamples(selected);
            }
        });
        
        buttonPanel.add(loadPresetButton);
        buttonPanel.add(copyExamplesButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createCustomWorkflowPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Workflow name
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Workflow Name:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField nameField = new JTextField(20);
        panel.add(nameField, gbc);
        
        // Description
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField descField = new JTextField(20);
        panel.add(descField, gbc);
        
        // Workflow type
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Type:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JComboBox<WorkflowPreset.WorkflowType> typeCombo = new JComboBox<>(WorkflowPreset.WorkflowType.values());
        panel.add(typeCombo, gbc);
        
        // Parameters section
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel paramLabel = new JLabel("Parameters (JSON format):");
        panel.add(paramLabel, gbc);
        
        gbc.gridy = 4; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.5;
        JTextArea paramArea = new JTextArea(8, 40);
        paramArea.setText("{\n  \"expected_lanes\": 12,\n  \"sensitivity\": 0.7,\n  \"background_method\": \"median\"\n}");
        JScrollPane paramScroll = new JScrollPane(paramArea);
        panel.add(paramScroll, gbc);
        
        // Natural language examples
        gbc.gridy = 5; gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel exampleLabel = new JLabel("Natural Language Examples (one per line):");
        panel.add(exampleLabel, gbc);
        
        gbc.gridy = 6; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 0.5;
        JTextArea exampleArea = new JTextArea(6, 40);
        exampleArea.setText("Analyze this 12-lane gel\nDetect protein bands\nExport results to CSV");
        JScrollPane exampleScroll = new JScrollPane(exampleArea);
        panel.add(exampleScroll, gbc);
        
        // Save button
        gbc.gridy = 7; gbc.weighty = 0.0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.CENTER;
        JButton saveButton = new JButton("💾 Save Custom Workflow");
        saveButton.addActionListener(e -> saveCustomWorkflow(nameField, descField, typeCombo, paramArea, exampleArea));
        panel.add(saveButton, gbc);
        
        return panel;
    }
    
    private JPanel createManagePresetsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // User presets only (can't edit built-in)
        List<WorkflowPreset> userPresets = workflowManager.getAllPresets().stream()
            .filter(p -> !workflowManager.isBuiltinPreset(p.getName()))
            .collect(java.util.stream.Collectors.toList());
        
        String[] presetNames = userPresets.stream()
            .map(WorkflowPreset::getName)
            .toArray(String[]::new);
        
        JList<String> presetList = new JList<>(presetNames);
        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(presetList);
        
        panel.add(listScroll, BorderLayout.CENTER);
        
        // Management buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton editButton = new JButton("✏️ Edit");
        editButton.addActionListener(e -> {
            String selected = presetList.getSelectedValue();
            if (selected != null) {
                editWorkflowPreset(selected);
            }
        });
        
        JButton deleteButton = new JButton("🗑️ Delete");
        deleteButton.addActionListener(e -> {
            String selected = presetList.getSelectedValue();
            if (selected != null) {
                deleteWorkflowPreset(selected);
                // Refresh list
                // TODO: Implement list refresh
            }
        });
        
        JButton exportButton = new JButton("📤 Export");
        exportButton.addActionListener(e -> exportWorkflows());
        
        JButton importButton = new JButton("📥 Import");
        importButton.addActionListener(e -> importWorkflows());
        
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(importButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private String formatPresetDetails(WorkflowPreset preset) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏷️ Name: ").append(preset.getName()).append("\n\n");
        sb.append("📋 Type: ").append(preset.getType().getDisplayName()).append("\n\n");
        sb.append("📝 Description:\n").append(preset.getDescription()).append("\n\n");
        
        if (workflowManager.isBuiltinPreset(preset.getName())) {
            sb.append("🔒 Built-in preset (read-only)\n\n");
        }
        
        sb.append("📊 Usage Statistics:\n");
        sb.append("  • Used ").append(preset.getUseCount()).append(" times\n");
        sb.append("  • Created: ").append(preset.getCreated().toString().substring(0, 19)).append("\n");
        if (preset.getLastUsed() != null) {
            sb.append("  • Last used: ").append(preset.getLastUsed().toString().substring(0, 19)).append("\n");
        }
        sb.append("\n");
        
        sb.append("⚙️ Parameters:\n");
        sb.append(preset.getParameters().toString(2)).append("\n\n");
        
        sb.append("💬 Natural Language Examples:\n");
        for (String example : preset.getNaturalLanguageExamples()) {
            sb.append("  • \"").append(example).append("\"\n");
        }
        
        return sb.toString();
    }
    
    private void loadWorkflowPreset(String presetName) {
        WorkflowPreset preset = workflowManager.getPreset(presetName);
        if (preset != null) {
            appendToChatArea("⚙️ Loaded workflow preset: " + presetName + "\n\n");
            
            // Show natural language examples
            appendToChatArea("📝 Try these commands for this workflow:\n");
            for (String example : preset.getNaturalLanguageExamples()) {
                appendToChatArea("  💬 \"" + example + "\"\n");
            }
            appendToChatArea("\n");
            
            // Record usage
            workflowManager.recordPresetUse(presetName);
            
            statusLabel.setText("⚙️ Workflow preset loaded: " + presetName);
        }
    }
    
    private void copyPresetExamples(String presetName) {
        WorkflowPreset preset = workflowManager.getPreset(presetName);
        if (preset != null) {
            StringBuilder examples = new StringBuilder();
            for (String example : preset.getNaturalLanguageExamples()) {
                examples.append(example).append("\n");
            }
            
            // Copy to system clipboard
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(examples.toString());
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            
            statusLabel.setText("📋 Examples copied to clipboard");
        }
    }
    
    private void saveCustomWorkflow(JTextField nameField, JTextField descField, 
                                  JComboBox<WorkflowPreset.WorkflowType> typeCombo,
                                  JTextArea paramArea, JTextArea exampleArea) {
        try {
            String name = nameField.getText().trim();
            String desc = descField.getText().trim();
            
            if (name.isEmpty() || desc.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please fill in name and description", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            WorkflowPreset preset = new WorkflowPreset(name, desc, (WorkflowPreset.WorkflowType) typeCombo.getSelectedItem());
            
            // Parse parameters JSON
            try {
                org.json.JSONObject params = new org.json.JSONObject(paramArea.getText());
                preset.setParameters(params);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Invalid JSON in parameters: " + e.getMessage(), "JSON Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Parse examples
            String[] examples = exampleArea.getText().split("\n");
            for (String example : examples) {
                if (!example.trim().isEmpty()) {
                    preset.addNaturalLanguageExample(example.trim());
                }
            }
            
            // Save preset
            workflowManager.savePreset(preset);
            
            // Clear fields
            nameField.setText("");
            descField.setText("");
            paramArea.setText("{\n  \n}");
            exampleArea.setText("");
            
            JOptionPane.showMessageDialog(frame, "Workflow preset saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("💾 Custom workflow saved: " + name);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error saving workflow: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void editWorkflowPreset(String presetName) {
        // TODO: Implement preset editing dialog
        JOptionPane.showMessageDialog(frame, "Editing workflow: " + presetName + "\n(Feature coming soon)", "Edit Workflow", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void deleteWorkflowPreset(String presetName) {
        int result = JOptionPane.showConfirmDialog(frame, 
            "Are you sure you want to delete the workflow preset '" + presetName + "'?\nThis action cannot be undone.",
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (result == JOptionPane.YES_OPTION) {
            if (workflowManager.deletePreset(presetName)) {
                statusLabel.setText("🗑️ Deleted workflow preset: " + presetName);
                JOptionPane.showMessageDialog(frame, "Workflow preset deleted successfully.", "Deleted", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Could not delete preset. Built-in presets cannot be deleted.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportWorkflows() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        fileChooser.setSelectedFile(new File("autodense_workflows.json"));
        
        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                workflowManager.exportPresets(fileChooser.getSelectedFile().toPath());
                JOptionPane.showMessageDialog(frame, "Workflows exported successfully!", "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void importWorkflows() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                workflowManager.importPresets(fileChooser.getSelectedFile().toPath());
                JOptionPane.showMessageDialog(frame, "Workflows imported successfully!", "Import Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Import failed: " + e.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private ImagePlus getCurrentImage() {
        // First priority: Use our stored currentImage if it exists
        if (currentImage != null) {
            // Don't check isVisible() as it might be false but still valid
            return currentImage;
        }
        
        // Fallback: Try to get from ImageJ WindowManager
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp != null) {
            currentImage = imp;  // Store it for next time
            return imp;
        }
        
        return null;
    }
    
    private String executeAnalysisFromUserCommand(String userCommand, String aiResponse) {
        System.out.println("DEBUG: executeAnalysisFromUserCommand called with: " + userCommand);
        
        // Get current image
        ImagePlus currentImg = getCurrentImage();
        if (currentImg == null) {
            System.out.println("DEBUG: No current image found");
            return "❌ No image loaded. Please load an image first.";
        }
        System.out.println("DEBUG: Current image found: " + currentImg.getTitle() + 
                          " (Width: " + currentImg.getWidth() + ", Height: " + currentImg.getHeight() + ")");
        
        // Use GeminiOrchestrator for proper handle-based processing
        System.out.println("DEBUG: orchestrator != null: " + (orchestrator != null));
        System.out.flush();
        if (orchestrator != null) {
            try {
                System.out.println("DEBUG: Using GeminiOrchestrator for analysis");
                System.out.flush();
                var futureResult = orchestrator.processCommand(userCommand, currentImg);
                var result = futureResult.get();
                System.out.println("DEBUG: Orchestrator result: " + result);
                System.out.flush();
                
                if (result.success) {
                    return result.geminiResponse.analysis + "\n\n✅ " + result.toolResult.optString("message", "Processing completed");
                } else {
                    return "❌ Processing failed: " + result.errorMessage;
                }
                
            } catch (Exception e) {
                System.out.println("DEBUG: GeminiOrchestrator failed: " + e.getMessage());
                System.out.flush();
                return "❌ Command processing failed: " + e.getMessage();
            }
        }
        
        // No orchestrator available
        System.out.println("DEBUG: No GeminiOrchestrator available");
        return "❌ GeminiOrchestrator not initialized - check API key configuration";
    }
    
    // Method removed - using GeminiOrchestrator handle-based architecture
    
    private String executeLaneDetectionWithParams(String command, ImagePlus imp, Map<String, Object> params) {
        try {
            // Get parameters from NLP extraction or use defaults/fallback parsing
            int expectedLanes = 12; // Default
            double gridOffset = 0.0; // Default
            
            if (params.containsKey("lane_count")) {
                expectedLanes = (Integer) params.get("lane_count");
            } else {
                expectedLanes = parseExpectedLaneCount(command); // Fallback
            }
            
            if (params.containsKey("amount") && params.containsKey("direction")) {
                double amount = ((Number) params.get("amount")).doubleValue();
                String direction = (String) params.get("direction");
                gridOffset = "left".equals(direction) ? -amount : amount;
            } else {
                gridOffset = parseGridOffset(command); // Fallback
            }
            
            String message = "🔍 Executing lane detection with " + expectedLanes + " lanes";
            if (gridOffset != 0.0) {
                message += " and offset " + gridOffset;
            }
            appendToChatArea(message + "...\n");
            
            // Execute actual lane detection
            List<com.betterdairy.autodense.model.Models.Lane> lanes = 
                com.betterdairy.autodense.analysis.LaneDetector.findLanes(
                    imp, expectedLanes, true, 0.55, gridOffset, true);
            
            // Create visual overlay
            Overlay overlay = new Overlay();
            int height = imp.getHeight();
            
            for (int i = 0; i < lanes.size(); i++) {
                com.betterdairy.autodense.model.Models.Lane lane = lanes.get(i);
                int x = lane.xStart();
                int width = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                
                Roi laneRoi = new Roi(x, 0, width, height);
                laneRoi.setStrokeColor(new Color(0, 255, 0, 180));
                laneRoi.setStrokeWidth(2.0);
                overlay.add(laneRoi);
            }
            
            // Apply overlay to image
            SwingUtilities.invokeLater(() -> {
                imp.setOverlay(overlay);
                imp.updateAndDraw();
            });
            
            return "✅ EXECUTED: Detected " + lanes.size() + " lanes (requested: " + expectedLanes + ")\n" +
                   "🟢 Green overlays show detected lane boundaries\n" +
                   "💡 Try: 'Detect bands' or 'Quantify intensities'";
                   
        } catch (Exception e) {
            return "❌ Error executing lane detection: " + e.getMessage();
        }
    }
    
    // Legacy method for backward compatibility  
    @SuppressWarnings("unused")
    private String executeLaneDetection(String command, ImagePlus imp) {
        try {
            // Parse lane count and offset from user command
            int expectedLanes = parseExpectedLaneCount(command);
            double gridOffset = parseGridOffset(command);
            
            String message = "🔍 Executing lane detection with " + expectedLanes + " lanes";
            if (gridOffset != 0.0) {
                message += " and offset " + gridOffset;
            }
            appendToChatArea(message + "...\n");
            
            // Execute actual lane detection with user-specified parameters
            List<com.betterdairy.autodense.model.Models.Lane> lanes = 
                com.betterdairy.autodense.analysis.LaneDetector.findLanes(
                    imp, expectedLanes, true, 0.55, gridOffset, true);
            
            // Create visual overlay
            Overlay overlay = new Overlay();
            int height = imp.getHeight();
            
            for (int i = 0; i < lanes.size(); i++) {
                com.betterdairy.autodense.model.Models.Lane lane = lanes.get(i);
                int x = lane.xStart();
                int width = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                
                Roi laneRoi = new Roi(x, 0, width, height);
                laneRoi.setStrokeColor(new Color(0, 255, 0, 180));
                laneRoi.setStrokeWidth(2.0);
                overlay.add(laneRoi);
            }
            
            // Apply overlay to image
            SwingUtilities.invokeLater(() -> {
                imp.setOverlay(overlay);
                imp.updateAndDraw();
            });
            
            return "✅ EXECUTED: Detected " + lanes.size() + " lanes (requested: " + expectedLanes + ")\n" +
                   "🟢 Green overlays show detected lane boundaries\n" +
                   "💡 Next: Try 'detect bands' or 'quantify bands'";
                   
        } catch (Exception e) {
            return "❌ Error executing lane detection: " + e.getMessage();
        }
    }
    
    private int parseExpectedLaneCount(String command) {
        String lowerCommand = command.toLowerCase();
        
        // Look for explicit numbers
        if (lowerCommand.contains("twelve") || lowerCommand.contains("12")) return 12;
        if (lowerCommand.contains("eleven") || lowerCommand.contains("11")) return 11;
        if (lowerCommand.contains("ten") || lowerCommand.contains("10")) return 10;
        if (lowerCommand.contains("nine") || lowerCommand.contains("9")) return 9;
        if (lowerCommand.contains("eight") || lowerCommand.contains("8")) return 8;
        if (lowerCommand.contains("seven") || lowerCommand.contains("7")) return 7;
        if (lowerCommand.contains("six") || lowerCommand.contains("6")) return 6;
        if (lowerCommand.contains("five") || lowerCommand.contains("5")) return 5;
        if (lowerCommand.contains("four") || lowerCommand.contains("4")) return 4;
        if (lowerCommand.contains("three") || lowerCommand.contains("3")) return 3;
        if (lowerCommand.contains("two") || lowerCommand.contains("2")) return 2;
        
        // If no specific count is mentioned, use 12 as default for this gel
        if (lowerCommand.contains("offset") || lowerCommand.contains("adjust") || lowerCommand.contains("shift")) {
            return 12; // Maintain previous lane count for adjustments
        }
        
        // Default to auto-detection
        return 0;
    }
    
    private double parseGridOffset(String command) {
        String lowerCommand = command.toLowerCase();
        
        // Look for offset values
        if (lowerCommand.contains("offset") || lowerCommand.contains("shift")) {
            // Parse direction and amount
            double offsetValue = 0.0;
            
            // Look for numbers with decimal points
            if (lowerCommand.contains("0.05") || lowerCommand.contains(".05")) {
                offsetValue = 0.05;
            } else if (lowerCommand.contains("0.1") || lowerCommand.contains(".1")) {
                offsetValue = 0.1;
            } else if (lowerCommand.contains("0.03") || lowerCommand.contains(".03")) {
                offsetValue = 0.03;
            }
            
            // Apply direction - left is negative offset
            if (lowerCommand.contains("left")) {
                return -offsetValue;
            } else if (lowerCommand.contains("right")) {
                return offsetValue;
            } else {
                // Default to left if direction not specified
                return -offsetValue;
            }
        }
        
        return 0.0; // No offset
    }
    
    private String executeBandDetection(String command, ImagePlus imp) {
        try {
            // Get current overlay to find lanes
            Overlay currentOverlay = imp.getOverlay();
            List<Lane> lanes = new ArrayList<>();
            
            // Extract lanes from overlay or detect them
            if (currentOverlay != null && currentOverlay.size() > 0) {
                // Try to extract lane positions from green overlays
                for (int i = 0; i < currentOverlay.size(); i++) {
                    Roi roi = currentOverlay.get(i);
                    // Check if this is a lane marker (green)
                    if (roi.getStrokeColor() != null && 
                        roi.getStrokeColor().getGreen() == 255 && 
                        roi.getStrokeColor().getRed() == 0) {
                        Rectangle bounds = roi.getBounds();
                        lanes.add(new Lane(lanes.size(), bounds.x, bounds.x + bounds.width - 1));
                    }
                }
            }
            
            // If no lanes found in overlay, detect them
            if (lanes.isEmpty()) {
                lanes = LaneDetector.findLanes(imp, 0, false, 0.55, 0.0, true);
            }
            
            // Detect bands in each lane using the existing BandDetector
            int totalBands = 0;
            Overlay newOverlay = new Overlay();
            
            // Re-add lane overlays
            for (Lane lane : lanes) {
                // Add lane overlay (green)
                int x = lane.xStart();
                int width = Math.max(1, lane.xEnd() - lane.xStart() + 1);
                Roi laneRoi = new Roi(x, 0, width, imp.getHeight());
                laneRoi.setStrokeColor(new Color(0, 255, 0, 180));
                laneRoi.setStrokeWidth(2.0);
                newOverlay.add(laneRoi);
                
                // Detect bands in this lane
                List<Band> bands = BandDetector.findBands(imp, lane);
                totalBands += bands.size();
                
                // Add band overlays (red)
                for (Band band : bands) {
                    int y = Math.max(0, Math.min(imp.getHeight() - 4, band.y()));
                    Roi bandRoi = new Roi(x, y, width, 4);
                    bandRoi.setStrokeColor(new Color(255, 0, 0, 200));
                    bandRoi.setStrokeWidth(1.5);
                    newOverlay.add(bandRoi);
                }
            }
            
            // Apply the new overlay
            imp.setOverlay(newOverlay);
            imp.updateAndDraw();
            
            return "✅ Band detection complete!\n" +
                   "   Lanes analyzed: " + lanes.size() + "\n" +
                   "   Total bands detected: " + totalBands + "\n" +
                   "   🔴 Red markers show detected bands\n" +
                   "💡 Next: Try 'quantify bands' or 'calibrate with ladder'";
                   
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Band detection failed: " + e.getMessage();
        }
    }
    
    
    
    
    
    
    @SuppressWarnings("unused")
    private String showContextualHelp() {
        return "🤖 AutoDense Help:\\n" +
               "🧬 Gel Analysis Commands:\\n" +
               "• 'Find 12 lanes' - Detect lanes in gel\\n" +
               "• 'Offset lanes left by 0.05' - Adjust lane positioning\\n" +
               "• 'Detect bands' - Find protein/DNA bands\\n" +
               "• 'Quantify bands' - Measure band intensities\\n" +
               "• 'Calibrate with protein ladder' - Set molecular weight standards\\n" +
               "\\n🦠 Agar Plate Commands:\\n" +
               "• 'Count all colonies' - Count all visible colonies\\n" +
               "• 'Count pink colonies' - Count colonies by color\\n" +
               "• 'Measure colony sizes' - Analyze colony size distribution\\n" +
               "• 'Check for contamination' - Identify unusual colonies\\n" +
               "\\n📊 Export & Results:\\n" +
               "• 'Export results' - Save analysis data as CSV/JSON";
    }
    
    @SuppressWarnings("unused")
    private String setGelType(String command, ImagePlus imp, Map<String, Object> params) {
        String gelType = "SDS-PAGE"; // Default
        if (params.containsKey("gel_type")) {
            gelType = (String) params.get("gel_type");
        }
        
        // Trigger lane detection since user provided gel info
        
        // If user mentions lane count in gel type command, also detect lanes
        if (command.toLowerCase().contains("12") || command.toLowerCase().contains("twelve")) {
            // Trigger lane detection with 12 lanes
            return "✅ Gel type set to " + gelType + "\\n\\n" + 
                   executeLaneDetectionWithParams(command, imp, Map.of("lane_count", 12));
        }
        
        return "✅ Gel type set to " + gelType + "\\n💡 Try: 'Detect lanes' or 'Find bands'";
    }
    
    private void appendToChatArea(String text) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(text);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
    
    /**
     * Update UI elements based on selected analysis type
     */
    private void updateUIForAnalysisType(boolean isColonyMode) {
        if (isColonyMode) {
            statusLabel.setText("🦠 Colony Analysis Mode - Ready to analyze bacterial plates");
            // Update chat area with colony-specific help
            String colonyHelp = """
                🦠 Colony Analysis Mode Selected
                
                Commands you can try:
                • "Detect the plate boundary"
                • "Count colonies on this plate"  
                • "Classify colonies by color"
                • "Export colony analysis to CSV"
                
                Colony tools: detect_plate, count_colonies, classify_colonies, bin_colonies, export_colonies
                """;
            appendToChatArea(colonyHelp);
        } else {
            statusLabel.setText("🧬 Gel Analysis Mode - Ready to analyze protein/DNA gels");
            // Update chat area with gel-specific help
            String gelHelp = """
                🧬 Gel Densitometry Mode Selected
                
                Commands you can try:
                • "Detect 12 lanes in this gel"
                • "Find bands in all lanes"
                • "Quantify band intensities"
                • "Export results to CSV"
                
                Gel tools: detect_lanes, detect_bands, quantify_bands, export_results
                """;
            appendToChatArea(gelHelp);
        }
    }
    
    /**
     * Create menu bar with View and Help menus
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // View Menu
        JMenu viewMenu = new JMenu("View");
        
        JMenuItem showConsoleItem = new JMenuItem("Show Console Window");
        showConsoleItem.addActionListener(e -> {
            // Show ImageJ Log window
            IJ.log("Console window opened from menu");
            try {
                if (IJ.getTextPanel() != null) {
                    java.awt.Window logWindow = SwingUtilities.getWindowAncestor(IJ.getTextPanel());
                    if (logWindow != null) {
                        logWindow.setVisible(true);
                        logWindow.toFront();
                    }
                }
            } catch (Exception ex) {
                statusLabel.setText("Could not show console: " + ex.getMessage());
            }
        });
        viewMenu.add(showConsoleItem);
        
        JMenuItem hideConsoleItem = new JMenuItem("Hide Console Window");
        hideConsoleItem.addActionListener(e -> {
            try {
                if (IJ.getTextPanel() != null) {
                    java.awt.Window logWindow = SwingUtilities.getWindowAncestor(IJ.getTextPanel());
                    if (logWindow != null) {
                        logWindow.setVisible(false);
                    }
                }
            } catch (Exception ex) {
                statusLabel.setText("Could not hide console: " + ex.getMessage());
            }
        });
        viewMenu.add(hideConsoleItem);
        
        menuBar.add(viewMenu);
        
        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        
        JMenuItem aboutItem = new JMenuItem("About AutoDense");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        JMenuItem voiceHelpItem = new JMenuItem("Voice Input Help");
        voiceHelpItem.addActionListener(e -> showVoiceHelpDialog());
        helpMenu.add(voiceHelpItem);
        
        JMenuItem docHelpItem = new JMenuItem("Document Upload Help");
        docHelpItem.addActionListener(e -> showDocumentHelpDialog());
        helpMenu.add(docHelpItem);
        
        helpMenu.addSeparator();
        
        // Demo submenu
        JMenu demoMenu = new JMenu("Demos & Tutorials");
        
        // Gel Analysis Demos
        JMenu gelDemoMenu = new JMenu("Gel Analysis");
        gelDemoMenu.add(createDemoMenuItem("Band Assist Demo", "com.betterdairy.autodense.demos.gelanalysis.BandAssistDemo", 
            "Interactive band identification across lanes"));
        gelDemoMenu.add(createDemoMenuItem("Core Detection Demo", "com.betterdairy.autodense.demos.gelanalysis.CoreDetectorDemo",
            "Core lane and band detection algorithms"));
        gelDemoMenu.add(createDemoMenuItem("Standard Curve Demo", "com.betterdairy.autodense.demos.gelanalysis.StandardCurveDemo",
            "Molecular weight calibration"));
        gelDemoMenu.add(createDemoMenuItem("Lane Comparison Demo", "com.betterdairy.autodense.demos.gelanalysis.LaneComparisonDemo",
            "Compare protein expressions between lanes"));
        gelDemoMenu.add(createDemoMenuItem("PCR Normalization Demo", "com.betterdairy.autodense.demos.gelanalysis.PCRNormalizationDemo",
            "Normalize gel bands to reference samples"));
        gelDemoMenu.add(createDemoMenuItem("Purification Tracker Demo", "com.betterdairy.autodense.demos.gelanalysis.PurificationTrackerDemo",
            "Track protein purification yield and purity across fractions"));
        gelDemoMenu.add(createDemoMenuItem("Isoform Profiling Demo", "com.betterdairy.autodense.demos.gelanalysis.IsoformProfilingDemo",
            "Analyze protein isoforms within molecular weight windows"));
        gelDemoMenu.add(createDemoMenuItem("HCP Composition Demo", "com.betterdairy.autodense.demos.gelanalysis.HcpCompositionDemo",
            "Quantify host cell protein contamination and identify contaminants"));
        gelDemoMenu.add(createDemoMenuItem("Dephosphorylation Shift Demo", "com.betterdairy.autodense.demos.gelanalysis.DephosphorylationShiftDemo",
            "Compare control vs phosphatase-treated samples for MW shifts and sharpening"));
        gelDemoMenu.add(createDemoMenuItem("Protease Digest Kinetics Demo", "com.betterdairy.autodense.demos.gelanalysis.ProteaseDigestKineticsDemo",
            "Track parent protein decay and fragment emergence over time series"));
        
        // Colony Analysis Demos
        JMenu colonyDemoMenu = new JMenu("Colony Analysis");
        colonyDemoMenu.add(createDemoMenuItem("Colony Classifier Demo", "com.betterdairy.autodense.demos.colonyanalysis.ColonyClassifierDemo",
            "X-gal color classification with background sampling"));
        colonyDemoMenu.add(createDemoMenuItem("Colony Detection Demo", "com.betterdairy.autodense.demos.colonyanalysis.ColonyAnalysisDemo",
            "Automated colony counting and sizing"));
        colonyDemoMenu.add(createDemoMenuItem("Colony Assist Demo", "com.betterdairy.autodense.demos.colonyanalysis.ColonyAssistDemo",
            "User-guided colony identification"));
        
        // Integration & Performance Demos
        JMenu systemDemoMenu = new JMenu("System & Performance");
        systemDemoMenu.add(createDemoMenuItem("Integration Demo", "com.betterdairy.autodense.demos.integration.IntegrationDemo",
            "Complete workflow pipeline demonstration"));
        systemDemoMenu.add(createDemoMenuItem("Synthetic Gel Performance", "com.betterdairy.autodense.demos.performance.SyntheticGelTest",
            "Performance testing with synthetic data"));
        systemDemoMenu.add(createDemoMenuItem("Response Shape Demo", "com.betterdairy.autodense.demos.performance.ResponseShapeDemo",
            "Tool response standardization"));
        
        demoMenu.add(gelDemoMenu);
        demoMenu.add(colonyDemoMenu);
        demoMenu.add(systemDemoMenu);
        
        helpMenu.add(demoMenu);
        
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    /**
     * Create a demo menu item that runs a demo class
     */
    private JMenuItem createDemoMenuItem(String name, String className, String description) {
        JMenuItem item = new JMenuItem(name);
        item.setToolTipText(description);
        item.addActionListener(e -> runDemo(className, name, description));
        return item;
    }
    
    /**
     * Run a demo class in a background thread
     */
    private void runDemo(String className, String name, String description) {
        statusLabel.setText("🎬 Running demo: " + name + "...");
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    publish("🎬 Starting " + name + "...\n");
                    publish("📋 " + description + "\n\n");
                    
                    // Capture System.out during demo execution
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    java.io.PrintStream originalOut = System.out;
                    java.io.PrintStream demoOut = new java.io.PrintStream(baos);
                    
                    try {
                        System.setOut(demoOut);
                        
                        // Load and run the demo class
                        Class<?> demoClass = Class.forName(className);
                        java.lang.reflect.Method mainMethod = demoClass.getMethod("main", String[].class);
                        mainMethod.invoke(null, (Object) new String[]{});
                        
                        demoOut.flush();
                        String output = baos.toString(StandardCharsets.UTF_8);
                        if (!output.trim().isEmpty()) {
                            publish("📤 Demo Output:\n");
                            publish(output + "\n");
                        }
                        
                    } finally {
                        System.setOut(originalOut);
                    }
                    
                    publish("✅ " + name + " completed successfully!\n\n");
                    
                } catch (ClassNotFoundException e) {
                    publish("❌ Demo class not found: " + className + "\n");
                    publish("   This may indicate the demo was moved or renamed.\n\n");
                } catch (NoSuchMethodException e) {
                    publish("❌ Demo class missing main method: " + className + "\n\n");
                } catch (Exception e) {
                    publish("❌ Demo execution failed: " + e.getMessage() + "\n\n");
                    e.printStackTrace();
                }
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendToChatArea(chunk);
                }
            }
            
            @Override
            protected void done() {
                statusLabel.setText("🟢 Ready for next command");
            }
        };
        
        worker.execute();
    }
    
    /**
     * Start voice recording
     */
    private void startVoiceRecording() {
        if (isRecording.get()) return;
        
        try {
            // Configure audio format for speech recognition
            audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                16000, // Sample rate
                16,    // Sample size in bits
                1,     // Channels (mono)
                2,     // Frame size
                16000, // Frame rate
                false  // Big endian
            );
            
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                showVoiceError("Audio input not supported on this system");
                return;
            }
            
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            
            isRecording.set(true);
            voiceButton.setText("🔴");
            voiceButton.setBackground(Color.RED);
            statusLabel.setText("🎤 Recording voice input... Release to send");
            
            // Note: Actual speech-to-text would require integration with
            // speech recognition service (Google Speech API, Azure Cognitive Services, etc.)
            // For now, this sets up the audio recording framework
            
        } catch (LineUnavailableException e) {
            showVoiceError("Could not access microphone: " + e.getMessage());
        }
    }
    
    /**
     * Stop voice recording and process audio
     */
    private void stopVoiceRecording() {
        if (!isRecording.get()) return;
        
        try {
            isRecording.set(false);
            
            if (targetDataLine != null) {
                targetDataLine.stop();
                targetDataLine.close();
            }
            
            voiceButton.setText("🎤");
            voiceButton.setBackground(null);
            statusLabel.setText("🎤 Voice recording stopped - Speech-to-text processing...");
            
            // Simulate processing delay
            SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    Thread.sleep(1000); // Simulate processing
                    // TODO: Integrate with actual speech-to-text service
                    return "Voice input detected: 'Analyze this gel with 12 lanes'";
                }
                
                @Override
                protected void done() {
                    try {
                        String transcribedText = get();
                        // For demo, just show what would happen
                        appendToChatArea("🎤 " + transcribedText + " (Demo)\n");
                        statusLabel.setText("🎤 Voice input complete - Add speech-to-text API key for full functionality");
                    } catch (Exception e) {
                        showVoiceError("Speech processing failed: " + e.getMessage());
                    }
                }
            };
            worker.execute();
            
        } catch (Exception e) {
            showVoiceError("Recording error: " + e.getMessage());
        }
    }
    
    /**
     * Show voice input error
     */
    private void showVoiceError(String message) {
        statusLabel.setText("🎤 Voice input error: " + message);
        voiceButton.setText("🎤");
        voiceButton.setBackground(null);
        isRecording.set(false);
    }
    
    /**
     * Show document upload dialog
     */
    private void showDocumentUploadDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        
        // Add file filters for supported document types
        FileNameExtensionFilter csvFilter = new FileNameExtensionFilter(
            "CSV Files (*.csv)", "csv");
        FileNameExtensionFilter excelFilter = new FileNameExtensionFilter(
            "Excel Files (*.xlsx, *.xls)", "xlsx", "xls");
        FileNameExtensionFilter textFilter = new FileNameExtensionFilter(
            "Text Files (*.txt)", "txt");
        FileNameExtensionFilter allFilter = new FileNameExtensionFilter(
            "All Supported (*.csv, *.xlsx, *.xls, *.txt)", "csv", "xlsx", "xls", "txt");
        
        fileChooser.addChoosableFileFilter(csvFilter);
        fileChooser.addChoosableFileFilter(excelFilter);
        fileChooser.addChoosableFileFilter(textFilter);
        fileChooser.addChoosableFileFilter(allFilter);
        fileChooser.setFileFilter(allFilter);
        
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            for (File file : selectedFiles) {
                processUploadedDocument(file);
            }
        }
    }
    
    /**
     * Process uploaded document
     */
    private void processUploadedDocument(File file) {
        try {
            uploadedDocuments.add(file);
            String fileName = file.getName();
            String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            
            appendToChatArea("📄 Uploaded: " + fileName + "\n");
            
            // Process different file types
            String content = "";
            switch (fileExtension) {
                case "csv":
                    content = processCsvFile(file);
                    break;
                case "txt":
                    content = processTextFile(file);
                    break;
                case "xlsx":
                case "xls":
                    content = processExcelFile(file);
                    break;
                default:
                    appendToChatArea("⚠️ Unsupported file type: " + fileExtension + "\n");
                    return;
            }
            
            if (!content.isEmpty()) {
                // Add document context to conversation
                String documentContext = String.format(
                    "📄 Document uploaded: %s\n" +
                    "Content summary: %s\n" +
                    "This information can be used to enhance gel analysis with lane identities, " +
                    "protein standards, sample amounts, and other experimental metadata.\n\n",
                    fileName, content
                );
                
                appendToChatArea(documentContext);
                conversationHistory.add("system: " + documentContext);
                
                statusLabel.setText("📄 Document processed: " + fileName);
            }
            
        } catch (Exception e) {
            appendToChatArea("❌ Error processing " + file.getName() + ": " + e.getMessage() + "\n");
        }
    }
    
    /**
     * Process CSV file
     */
    private String processCsvFile(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) return "Empty file";
            
            String header = lines.get(0);
            int rowCount = lines.size() - 1;
            
            // Analyze CSV structure
            if (header.toLowerCase().contains("lane")) {
                return String.format("Lane information data with %d entries. Headers: %s", 
                    rowCount, header.substring(0, Math.min(100, header.length())));
            } else if (header.toLowerCase().contains("protein") || header.toLowerCase().contains("standard")) {
                return String.format("Protein/standard data with %d entries. Headers: %s", 
                    rowCount, header.substring(0, Math.min(100, header.length())));
            } else {
                return String.format("CSV data with %d rows and headers: %s", 
                    rowCount, header.substring(0, Math.min(100, header.length())));
            }
            
        } catch (IOException e) {
            return "Error reading CSV file: " + e.getMessage();
        }
    }
    
    /**
     * Process text file
     */
    private String processTextFile(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int lineCount = lines.size();
            String preview = lines.stream()
                .limit(3)
                .reduce("", (a, b) -> a + " " + b)
                .substring(0, Math.min(100, lines.get(0).length()));
            
            return String.format("Text file with %d lines. Preview: %s...", lineCount, preview);
            
        } catch (IOException e) {
            return "Error reading text file: " + e.getMessage();
        }
    }
    
    /**
     * Process Excel file (placeholder - would need Apache POI)
     */
    private String processExcelFile(File file) {
        // TODO: Integrate Apache POI for Excel processing
        return String.format("Excel file detected: %s (Excel processing requires Apache POI integration)", 
            file.getName());
    }
    
    /**
     * Show about dialog
     */
    private void showAboutDialog() {
        String aboutText = """
            🤖 AutoDense AI Assistant
            Version 12.01 Enhanced
            
            Features:
            • 🧬 Gel electrophoresis analysis
            • 🦠 Colony counting and classification
            • 🎤 Voice input support (experimental)
            • 📄 Document upload integration
            • 🤖 Gemini AI-powered analysis
            
            Developed by BetterDairy
            Built with ImageJ2 and Gemini AI
            """;
        
        JOptionPane.showMessageDialog(frame, aboutText, 
            "About AutoDense", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Show voice input help
     */
    private void showVoiceHelpDialog() {
        String voiceHelpText = """
            🎤 Voice Input Help
            
            How to use voice input:
            1. Hold down the microphone button (🎤)
            2. Speak your command clearly
            3. Release the button to stop recording
            
            Voice commands work best with:
            • Clear, direct instructions
            • Lab-specific terminology
            • Short, focused requests
            
            Examples:
            • "Detect twelve lanes in this gel"
            • "Find all protein bands"
            • "Count blue colonies on this plate"
            
            Note: Full speech-to-text requires API integration
            (Google Speech API, Azure Cognitive Services, etc.)
            """;
        
        JOptionPane.showMessageDialog(frame, voiceHelpText, 
            "Voice Input Help", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Show document upload help
     */
    private void showDocumentHelpDialog() {
        String docHelpText = """
            📄 Document Upload Help
            
            Supported file types:
            • CSV files (.csv) - Lane information, protein standards
            • Excel files (.xlsx, .xls) - Experimental data sheets
            • Text files (.txt) - Lab notes, protocols
            
            What you can upload:
            • Lane identities and sample names
            • Protein standard concentrations
            • Molecular weight ladder information
            • Sample loading amounts
            • Experimental conditions
            • Protocol notes
            
            Benefits:
            • Automatic lane labeling
            • Enhanced analysis context
            • Improved AI understanding
            • Streamlined workflow
            
            The AI will use this information to provide more
            accurate and contextually relevant analysis.
            """;
        
        JOptionPane.showMessageDialog(frame, docHelpText, 
            "Document Upload Help", JOptionPane.INFORMATION_MESSAGE);
    }
}
