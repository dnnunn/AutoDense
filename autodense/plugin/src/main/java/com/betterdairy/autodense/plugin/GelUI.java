package com.betterdairy.autodense.plugin;

import org.scijava.Context;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.Roi;
// Removed unused JSON imports - now handled by new architecture

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.analysis.BandDetector;
import com.betterdairy.autodense.model.Models.Lane;
import com.betterdairy.autodense.model.Models.Band;

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
    private GeminiApiClient geminiClient;
    // Stores the most recent Gemini analysis to enable follow-up actions (e.g., refine params)
    private GeminiApiClient.GelAnalysisResponse lastGeminiAnalysis;
    // Conversation history for context
    private List<String> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10; // Keep last 10 exchanges
    
    public GelUI(Context context) {
        // Context not used in new architecture
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Initialize Gemini API client 
        // API key now retrieved from environment variable or config file
        String geminiApiKey = System.getProperty("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY"));
        if (geminiApiKey != null && !geminiApiKey.isEmpty()) {
            this.geminiClient = new GeminiApiClient(geminiApiKey);
            System.out.println("✓ Gemini API client initialized");
        } else {
            System.out.println("⚠ GEMINI_API_KEY not found - using fallback NLP");
        }
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
        
        JButton quickDetect = new JButton("🔍 AI Detect");
        quickDetect.setToolTipText("AI-guided lane and band detection");
        quickDetect.addActionListener(e -> executeAIGuidedDetect());
        actionsPanel.add(quickDetect);
        
        JButton quickQuantify = new JButton("📊 Quick Quantify");
        quickQuantify.setToolTipText("Quantify detected bands");
        quickQuantify.addActionListener(e -> executeQuickQuantify());
        actionsPanel.add(quickQuantify);
        
        JButton refineButton = new JButton("🔧 Refine");
        refineButton.setToolTipText("Refine using last Gemini analysis");
        refineButton.addActionListener(e -> refineAnalysisWithLastGemini());
        actionsPanel.add(refineButton);
        
        JButton clearChatButton = new JButton("🗑️ Clear");
        clearChatButton.setToolTipText("Clear conversation history");
        clearChatButton.addActionListener(e -> clearConversation());
        actionsPanel.add(clearChatButton);
        
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
        
        panel.add(inputScroll, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        
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
        // Add to conversation history
        conversationHistory.add("user: " + command);
        if (conversationHistory.size() > MAX_HISTORY * 2) {
            conversationHistory = conversationHistory.subList(
                conversationHistory.size() - MAX_HISTORY * 2, 
                conversationHistory.size()
            );
        }
        
        // Build messages with history for context
        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[\n");
        messagesJson.append("  {\"role\": \"system\", \"content\": \"You are AutoDense AI for laboratory image analysis. You analyze: 1) Gel electrophoresis (SDS-PAGE, DNA gels) 2) Agar plates with microbial colonies (yeast, bacteria). When users ask you to perform analysis, you should EXECUTE the analysis immediately and show results, not just describe what you would do. Parse user requests for: Gels - Number of lanes, lane types (ladder vs sample), analysis type (detect, quantify, etc). Plates - Colony counting, color analysis, size measurements. Always execute the requested action and provide results.\"},\n");
        
        // Add conversation history
        for (int i = 0; i < conversationHistory.size() - 1; i++) {
            String msg = conversationHistory.get(i);
            String role = msg.startsWith("user:") ? "user" : "assistant";
            String content = msg.substring(msg.indexOf(":") + 1).trim();
            messagesJson.append(String.format("  {\"role\": \"%s\", \"content\": \"%s\"},\n", 
                role, content.replace("\"", "\\\"").replace("\n", "\\n")));
        }
        
        // Add current message
        messagesJson.append(String.format("  {\"role\": \"user\", \"content\": \"%s\"}\n", 
            command.replace("\"", "\\\"").replace("\n", "\\n")));
        messagesJson.append("]");
        
        // Create request to AI server
        String requestBody = String.format("""
            {
              "messages": %s,
              "temperature": 0.1,
              "max_tokens": 512
            }""", messagesJson.toString());
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:8080/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(30))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("AI Server error: " + response.statusCode());
        }
        
        // Parse response (simplified - in production would use JSON library)
        String responseBody = response.body();
        int contentStart = responseBody.indexOf("\"content\":\"") + 11;
        int contentEnd = responseBody.indexOf("\"", contentStart);
        
        if (contentStart > 10 && contentEnd > contentStart) {
            String aiResponse = responseBody.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
            
            // Add AI response to history
            conversationHistory.add("assistant: " + aiResponse);
            
            // Execute actual analysis based on user command
            String executionResult = executeAnalysisFromUserCommand(command, aiResponse);
            if (executionResult != null) {
                return aiResponse + "\n\n" + executionResult;
            }
            
            return aiResponse;
        } else {
            throw new RuntimeException("Could not parse AI response");
        }
    }
    
    private void refineAnalysisWithLastGemini() {
        // Uses stored lastGeminiAnalysis to refine parameters
        if (lastGeminiAnalysis != null) {
            appendToChatArea("🔄 Refining analysis with confidence: " + 
                           String.format("%.1f%%", lastGeminiAnalysis.confidence * 100) + "\n");
            
            // Apply refined parameters from last analysis
            if (lastGeminiAnalysis.parameters.containsKey("lane_count")) {
                int refinedLanes = ((Number) lastGeminiAnalysis.parameters.get("lane_count")).intValue();
                appendToChatArea("   Adjusting to " + refinedLanes + " lanes\n");
            }
        } else {
            appendToChatArea("❌ No previous Gemini analysis to refine\n");
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
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:8080/health"))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                    
                    return response.statusCode() == 200;
                } catch (Exception e) {
                    return false;
                }
            }
            
            @Override
            protected void done() {
                try {
                    boolean connected = get();
                    if (connected) {
                        statusLabel.setText("🟢 AI Server Connected - Ready for commands");
                        statusLabel.setForeground(new Color(0, 120, 0));
                    } else {
                        statusLabel.setText("🔴 AI Server not responding - Check connection");
                        statusLabel.setForeground(Color.RED);
                        appendToChatArea("⚠️  AI Server not available. Start server with:\n");
                        appendToChatArea("   ./llama-server-arm64 --model ... --port 8080\n\n");
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
    
    private void executeAIGuidedDetect() {
        appendToChatArea("👤 You: AI Detect\n");
        
        // Get current image
        ImagePlus imp = getCurrentImage();
        if (imp == null) {
            appendToChatArea("❌ No image loaded. Please load an image first.\n\n");
            return;
        }
        
        // Check if Gemini is available
        if (geminiClient == null) {
            appendToChatArea("❌ Gemini API not available. Please set GEMINI_API_KEY.\n\n");
            return;
        }
        
        appendToChatArea("🤖 AutoDense: Analyzing gel with Gemini vision...\n");
        
        // Disable buttons during analysis
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // Use Gemini to analyze the gel and determine optimal parameters
                    publish("🔍 Gemini analyzing gel structure...");
                    
                    String analysisCommand = "Analyze this gel image and determine: " +
                        "1) How many lanes are present? Count them precisely. " +
                        "2) Are the lanes evenly spaced? " +
                        "3) What's the optimal lane width fraction (0.0-1.0)? " +
                        "4) Do lanes need offset adjustment (left/right)? " +
                        "5) Identify all visible protein/DNA bands.";
                    
                    GeminiApiClient.GelAnalysisResponse geminiResponse = 
                        geminiClient.analyzeGel(analysisCommand, imp);
                    
                    // Store the analysis for future use (enables follow-up refinements)
                    lastGeminiAnalysis = geminiResponse;
                    
                    publish("📊 Gemini analysis: " + geminiResponse.analysis);
                    
                    // Extract parameters from Gemini's response
                    int laneCount = 0;
                    double laneWidth = 0.55;
                    double gridOffset = 0.0;
                    
                    if (geminiResponse.parameters.containsKey("lane_count")) {
                        laneCount = ((Number) geminiResponse.parameters.get("lane_count")).intValue();
                    }
                    if (geminiResponse.parameters.containsKey("lane_width")) {
                        laneWidth = ((Number) geminiResponse.parameters.get("lane_width")).doubleValue();
                    }
                    if (geminiResponse.parameters.containsKey("grid_offset")) {
                        gridOffset = ((Number) geminiResponse.parameters.get("grid_offset")).doubleValue();
                    }
                    
                    publish("⚙️ Applying Gemini-optimized parameters:");
                    publish("   Lane count: " + laneCount);
                    publish("   Lane width: " + String.format("%.2f", laneWidth));
                    publish("   Grid offset: " + String.format("%.3f", gridOffset));
                    
                    // Detect lanes with Gemini's parameters
                    publish("🔍 Detecting lanes with Gemini parameters...");
                    List<Lane> lanes = LaneDetector.findLanes(imp, 
                        laneCount, 
                        true,  // constant spacing
                        laneWidth,
                        gridOffset,
                        true); // preprocess
                    
                    publish("✅ Detected " + lanes.size() + " lanes");
                    
                    // Step 4: Detect bands
                    publish("🎯 Detecting bands in each lane...");
                    int totalBands = 0;
                    Overlay overlay = new Overlay();
                    
                    for (int i = 0; i < lanes.size(); i++) {
                        Lane lane = lanes.get(i);
                        List<Band> bands = BandDetector.findBands(imp, lane);
                        totalBands += bands.size();
                        
                        // Add lane overlay (green) - thicker for better visibility
                        Roi laneRoi = new Roi(lane.xStart(), 0, 
                            Math.max(1, lane.xEnd() - lane.xStart() + 1), imp.getHeight());
                        laneRoi.setStrokeColor(new Color(0, 255, 0, 180));
                        laneRoi.setStrokeWidth(3.0);
                        overlay.add(laneRoi);
                        
                        // Add band overlays (red) - more prominent
                        for (Band band : bands) {
                            int bandWidth = Math.max(6, lane.xEnd() - lane.xStart() + 1);
                            Roi bandRoi = new Roi(lane.xStart(), band.y(), bandWidth, 8);
                            bandRoi.setStrokeColor(new Color(255, 0, 0, 200));
                            bandRoi.setStrokeWidth(4.0);
                            overlay.add(bandRoi);
                        }
                    }
                    
                    // Apply overlay to image
                    SwingUtilities.invokeLater(() -> {
                        imp.setOverlay(overlay);
                        imp.updateAndDraw();
                    });
                    
                    publish("✅ Gemini vision analysis complete!");
                    publish("   Lanes detected: " + lanes.size());
                    publish("   Bands detected: " + totalBands);
                    publish("   🟢 Green = lanes, 🔴 Red = bands");
                    publish("💡 Gemini confidence: " + String.format("%.1f%%", geminiResponse.confidence * 100));
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    publish("❌ Gemini analysis failed: " + e.getMessage());
                }
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendToChatArea(message + "\n");
                }
            }
            
            @Override
            protected void done() {
                appendToChatArea("\n");
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                inputField.requestFocus();
            }
        };
        
        worker.execute();
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
    
    private void executeQuickQuantify() {
        appendToChatArea("👤 You: Quick Quantify\n");
        
        ImagePlus imp = getCurrentImage();
        if (imp == null) {
            appendToChatArea("❌ No image loaded. Please run detection first.\n\n");
            return;
        }
        
        Overlay overlay = imp.getOverlay();
        if (overlay == null || overlay.size() == 0) {
            appendToChatArea("❌ No bands detected. Please run 'Quick Detect' first.\n\n");
            return;
        }
        
        appendToChatArea("🤖 AutoDense: Quantifying band intensities...\n");
        
        try {
            // Count bands and calculate basic measurements
            int bandCount = 0;
            double totalIntensity = 0;
            
            for (int i = 0; i < overlay.size(); i++) {
                Roi roi = overlay.get(i);
                if (roi.getStrokeColor() != null && roi.getStrokeColor().equals(Color.RED)) {
                    bandCount++;
                    
                    // Basic intensity measurement
                    imp.setRoi(roi);
                    double mean = imp.getStatistics().mean;
                    totalIntensity += mean;
                }
            }
            
            appendToChatArea("✅ Quantified " + bandCount + " bands\n");
            appendToChatArea("📊 Average intensity: " + String.format("%.1f", totalIntensity / Math.max(1, bandCount)) + "\n");
            appendToChatArea("📋 Results added to ImageJ Log window\n");
            appendToChatArea("💡 For detailed analysis, use: 'Quantify with background subtraction'\n\n");
            
            // Log to ImageJ
            IJ.log("=== AutoDense Quick Quantification ===");
            IJ.log("Bands quantified: " + bandCount);
            IJ.log("Average intensity: " + String.format("%.2f", totalIntensity / Math.max(1, bandCount)));
            IJ.log("Image: " + imp.getTitle());
            
        } catch (Exception e) {
            appendToChatArea("❌ Quantification failed: " + e.getMessage() + "\n\n");
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
        
        // Try Gemini API first (if available), then fall back to local NLP
        if (geminiClient != null) {
            try {
                System.out.println("DEBUG: Using Gemini API for analysis");
                GeminiApiClient.GelAnalysisResponse geminiResponse = geminiClient.analyzeGel(userCommand, currentImg);
                System.out.println("DEBUG: Gemini response: " + geminiResponse);
                
                return executeActionFromGeminiResponse(geminiResponse, currentImg);
                
            } catch (Exception e) {
                System.out.println("DEBUG: Gemini API failed, falling back to local NLP: " + e.getMessage());
            }
        }
        
        // No local NLP processor - Gemini only
        System.out.println("DEBUG: No local NLP available");
        return null;
    }
    
    private String executeActionFromGeminiResponse(GeminiApiClient.GelAnalysisResponse response, ImagePlus imp) {
        System.out.println("DEBUG: Executing Gemini-guided action: " + response.action);
        
        switch (response.action) {
            case "executeLaneDetection":
            case "executeLaneAdjustment":
                return executeLaneDetectionWithParams(response.originalCommand, imp, response.parameters);
                
            case "executeBandDetection":
                return executeBandDetection(response.originalCommand, imp);
                
            case "executeQuantification":
            case "executeCalibration":
                return "🔄 Action not yet implemented: " + response.action;
                
            default:
                return "✅ Gemini Analysis: " + response.analysis + "\\n" +
                       "🤖 Intent: " + response.intent + " (confidence: " + String.format("%.1f", response.confidence * 100) + "%)\\n" +
                       "💡 Action not yet implemented: " + response.action;
        }
    }
    
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
}
