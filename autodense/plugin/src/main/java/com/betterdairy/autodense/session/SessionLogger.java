package com.betterdairy.autodense.session;

import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Comprehensive session logging for AutoDense gel analysis sessions.
 * Captures all Gemini conversations, tool calls, and session events for:
 * - Troubleshooting and debugging
 * - Training data for AI improvements
 * - Session replay and analysis
 * - User behavior insights
 */
public class SessionLogger {
    
    private static final String LOG_DIR_PROPERTY = "autodense.log.dir";
    private static final String DEFAULT_LOG_DIR = System.getProperty("user.home") + "/.autodense/logs";
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int MAX_LOG_SIZE_MB = 50; // Max 50MB per log file
    private static final int MAX_LOG_FILES = 100;   // Keep max 100 log files
    
    private final String sessionId;
    private final Path logFile;
    private final ConcurrentLinkedQueue<LogEntry> pendingEntries;
    private final ScheduledExecutorService logWriter;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final JSONObject sessionMetadata;
    private volatile boolean isActive = true;
    
    // Session statistics
    private int conversationCount = 0;
    private int toolCallCount = 0;
    private int errorCount = 0;
    private final Instant sessionStartTime;
    
    public SessionLogger(String sessionId) {
        this.sessionId = sessionId;
        this.sessionStartTime = Instant.now();
        this.pendingEntries = new ConcurrentLinkedQueue<>();
        this.logWriter = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "SessionLogger-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        
        // Initialize log directory and file
        this.logFile = initializeLogFile();
        this.sessionMetadata = initializeMetadata();
        
        // Start periodic log writing (every 5 seconds)
        logWriter.scheduleWithFixedDelay(this::flushPendingEntries, 5, 5, TimeUnit.SECONDS);
        
        // Log session start
        logSessionEvent("session_start", "Session initialized", null);
        
        // Cleanup old logs on startup
        cleanupOldLogs();
    }
    
    /**
     * Log a conversation message between user and Gemini AI
     */
    public void logConversation(String speaker, String message, JSONObject metadata) {
        if (!isActive) return;
        
        JSONObject entry = new JSONObject()
            .put("type", "conversation")
            .put("timestamp", getCurrentTimestamp())
            .put("session_id", sessionId)
            .put("sequence", ++conversationCount)
            .put("speaker", speaker) // "user", "gemini", "system"
            .put("message", truncateIfTooLong(message, 10000))
            .put("message_length", message.length())
            .put("metadata", metadata != null ? metadata : new JSONObject());
        
        pendingEntries.offer(new LogEntry(entry, LogLevel.INFO));
    }
    
    /**
     * Log a tool call execution (request and response)
     */
    public void logToolCall(String toolName, JSONObject request, JSONObject response, 
                           long executionTimeMs, boolean success) {
        if (!isActive) return;
        
        JSONObject entry = new JSONObject()
            .put("type", "tool_call")
            .put("timestamp", getCurrentTimestamp())
            .put("session_id", sessionId)
            .put("sequence", ++toolCallCount)
            .put("tool_name", toolName)
            .put("execution_time_ms", executionTimeMs)
            .put("success", success)
            .put("request", sanitizeToolData(request))
            .put("response", sanitizeToolData(response))
            .put("request_size", request.toString().length())
            .put("response_size", response.toString().length());
        
        if (!success) {
            errorCount++;
            entry.put("error_sequence", errorCount);
        }
        
        pendingEntries.offer(new LogEntry(entry, success ? LogLevel.INFO : LogLevel.ERROR));
    }
    
    /**
     * Log a session event (start, end, error, etc.)
     */
    public void logSessionEvent(String eventType, String description, JSONObject data) {
        if (!isActive) return;
        
        JSONObject entry = new JSONObject()
            .put("type", "session_event")
            .put("timestamp", getCurrentTimestamp())
            .put("session_id", sessionId)
            .put("event_type", eventType)
            .put("description", description)
            .put("data", data != null ? data : new JSONObject())
            .put("session_duration_ms", Instant.now().toEpochMilli() - sessionStartTime.toEpochMilli());
        
        LogLevel level = eventType.contains("error") ? LogLevel.ERROR : LogLevel.INFO;
        pendingEntries.offer(new LogEntry(entry, level));
    }
    
    /**
     * Log an error with stack trace
     */
    public void logError(String operation, Exception error, JSONObject context) {
        if (!isActive) return;
        
        errorCount++;
        
        JSONObject entry = new JSONObject()
            .put("type", "error")
            .put("timestamp", getCurrentTimestamp())
            .put("session_id", sessionId)
            .put("error_sequence", errorCount)
            .put("operation", operation)
            .put("error_class", error.getClass().getSimpleName())
            .put("error_message", error.getMessage())
            .put("stack_trace", getStackTraceString(error))
            .put("context", context != null ? context : new JSONObject());
        
        pendingEntries.offer(new LogEntry(entry, LogLevel.ERROR));
    }
    
    /**
     * Log Gemini API interaction details
     */
    public void logGeminiApiCall(String endpoint, JSONObject request, JSONObject response, 
                                int statusCode, long responseTimeMs) {
        if (!isActive) return;
        
        JSONObject entry = new JSONObject()
            .put("type", "gemini_api")
            .put("timestamp", getCurrentTimestamp())
            .put("session_id", sessionId)
            .put("endpoint", endpoint)
            .put("status_code", statusCode)
            .put("response_time_ms", responseTimeMs)
            .put("request_tokens", estimateTokenCount(request.toString()))
            .put("response_tokens", estimateTokenCount(response.toString()))
            .put("request_size_bytes", request.toString().length())
            .put("response_size_bytes", response.toString().length())
            .put("success", statusCode >= 200 && statusCode < 300)
            .put("request", sanitizeApiData(request))
            .put("response", sanitizeApiData(response));
        
        if (statusCode >= 400) {
            errorCount++;
            entry.put("error_sequence", errorCount);
        }
        
        LogLevel level = statusCode >= 400 ? LogLevel.ERROR : LogLevel.INFO;
        pendingEntries.offer(new LogEntry(entry, level));
    }
    
    /**
     * Get current session statistics
     */
    public JSONObject getSessionStatistics() {
        lock.readLock().lock();
        try {
            return new JSONObject()
                .put("session_id", sessionId)
                .put("start_time", sessionStartTime.toString())
                .put("duration_ms", Instant.now().toEpochMilli() - sessionStartTime.toEpochMilli())
                .put("conversation_count", conversationCount)
                .put("tool_call_count", toolCallCount)
                .put("error_count", errorCount)
                .put("pending_entries", pendingEntries.size())
                .put("log_file", logFile.toString())
                .put("is_active", isActive);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Generate session summary for export
     */
    public JSONObject generateSessionSummary() {
        lock.readLock().lock();
        try {
            JSONObject summary = new JSONObject()
                .put("session_metadata", sessionMetadata)
                .put("session_statistics", getSessionStatistics())
                .put("timestamp", getCurrentTimestamp())
                .put("format_version", "1.0");
            
            // Add recent entries summary
            List<LogEntry> recentEntries = new ArrayList<>(pendingEntries);
            JSONArray entrySummary = new JSONArray();
            for (LogEntry entry : recentEntries) {
                entrySummary.put(entry.data.getJSONObject("type"));
            }
            summary.put("recent_entries", entrySummary);
            
            return summary;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Close session and finalize logs
     */
    public void closeSession() {
        if (!isActive) return;
        
        isActive = false;
        
        // Log session end
        logSessionEvent("session_end", "Session completed", getSessionStatistics());
        
        // Force flush all pending entries
        flushPendingEntries();
        
        // Write session summary
        writeSessionSummary();
        
        // Shutdown log writer
        logWriter.shutdown();
        try {
            if (!logWriter.awaitTermination(10, TimeUnit.SECONDS)) {
                logWriter.shutdownNow();
            }
        } catch (InterruptedException e) {
            logWriter.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Force immediate flush of pending entries
     */
    public void flush() {
        flushPendingEntries();
    }
    
    // Private helper methods
    
    private Path initializeLogFile() {
        try {
            String logDir = System.getProperty(LOG_DIR_PROPERTY, DEFAULT_LOG_DIR);
            Path logPath = Path.of(logDir);
            Files.createDirectories(logPath);
            
            String timestamp = sessionStartTime.atZone(ZoneId.systemDefault()).format(FILENAME_FORMATTER);
            String filename = String.format("autodense_session_%s_%s.jsonl", timestamp, sessionId);
            
            return logPath.resolve(filename);
        } catch (Exception e) {
            // Fallback to temp directory
            String fallbackFile = String.format("autodense_session_%s.jsonl", sessionId);
            return Path.of(System.getProperty("java.io.tmpdir"), fallbackFile);
        }
    }
    
    private JSONObject initializeMetadata() {
        return new JSONObject()
            .put("session_id", sessionId)
            .put("start_time", sessionStartTime.toString())
            .put("log_file", logFile.toString())
            .put("autodense_version", getClass().getPackage().getImplementationVersion())
            .put("java_version", System.getProperty("java.version"))
            .put("os_name", System.getProperty("os.name"))
            .put("os_version", System.getProperty("os.version"))
            .put("user_timezone", ZoneId.systemDefault().toString())
            .put("log_format_version", "1.0");
    }
    
    private void flushPendingEntries() {
        if (!isActive && pendingEntries.isEmpty()) return;
        
        lock.writeLock().lock();
        try {
            List<LogEntry> entriesToWrite = new ArrayList<>();
            LogEntry entry;
            while ((entry = pendingEntries.poll()) != null) {
                entriesToWrite.add(entry);
            }
            
            if (!entriesToWrite.isEmpty()) {
                writeLogEntries(entriesToWrite);
            }
        } catch (Exception e) {
            // Log writing error - try to preserve entries
            System.err.println("Failed to write log entries: " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void writeLogEntries(List<LogEntry> entries) throws Exception {
        StringBuilder logLines = new StringBuilder();
        for (LogEntry entry : entries) {
            logLines.append(entry.data.toString()).append("\n");
        }
        
        // Check file size and rotate if needed
        if (Files.exists(logFile) && Files.size(logFile) > MAX_LOG_SIZE_MB * 1024 * 1024) {
            rotateLogFile();
        }
        
        Files.writeString(logFile, logLines.toString(), 
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    
    private void writeSessionSummary() {
        try {
            Path summaryFile = logFile.resolveSibling(logFile.getFileName().toString().replace(".jsonl", "_summary.json"));
            JSONObject summary = generateSessionSummary();
            Files.writeString(summaryFile, summary.toString(2));
        } catch (Exception e) {
            System.err.println("Failed to write session summary: " + e.getMessage());
        }
    }
    
    private void rotateLogFile() {
        try {
            String baseFileName = logFile.getFileName().toString().replace(".jsonl", "");
            String rotatedName = baseFileName + "_" + System.currentTimeMillis() + ".jsonl";
            Path rotatedFile = logFile.resolveSibling(rotatedName);
            Files.move(logFile, rotatedFile);
        } catch (Exception e) {
            System.err.println("Failed to rotate log file: " + e.getMessage());
        }
    }
    
    private void cleanupOldLogs() {
        try {
            Path logDir = logFile.getParent();
            List<Path> logFiles = Files.list(logDir)
                .filter(p -> p.getFileName().toString().startsWith("autodense_session_"))
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .toList();
                
            // Delete old files beyond the limit
            for (int i = MAX_LOG_FILES; i < logFiles.size(); i++) {
                try {
                    Files.deleteIfExists(logFiles.get(i));
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    private String getCurrentTimestamp() {
        return Instant.now().atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
    }
    
    private String truncateIfTooLong(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    private JSONObject sanitizeToolData(JSONObject data) {
        JSONObject sanitized = new JSONObject(data.toString());
        
        // Remove or truncate large data fields
        if (sanitized.has("base64_image")) {
            String base64 = sanitized.getString("base64_image");
            sanitized.put("base64_image", truncateIfTooLong(base64, 100) + " [truncated]");
            sanitized.put("base64_image_length", base64.length());
        }
        
        // Sanitize file paths to relative paths for privacy
        if (sanitized.has("path")) {
            String path = sanitized.getString("path");
            sanitized.put("path", sanitizeFilePath(path));
        }
        
        return sanitized;
    }
    
    private JSONObject sanitizeApiData(JSONObject data) {
        JSONObject sanitized = new JSONObject(data.toString());
        
        // Remove API keys and sensitive data
        removeRecursive(sanitized, "key");
        removeRecursive(sanitized, "api_key");
        removeRecursive(sanitized, "token");
        
        // Truncate large image data
        truncateImageData(sanitized);
        
        return sanitized;
    }
    
    private void removeRecursive(JSONObject obj, String key) {
        obj.remove(key);
        for (String k : obj.keySet()) {
            Object value = obj.get(k);
            if (value instanceof JSONObject) {
                removeRecursive((JSONObject) value, key);
            } else if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject) {
                        removeRecursive((JSONObject) item, key);
                    }
                }
            }
        }
    }
    
    private void truncateImageData(JSONObject obj) {
        if (obj.has("data") && obj.getJSONObject("inline_data") != null) {
            JSONObject inlineData = obj.getJSONObject("inline_data");
            if (inlineData.has("data")) {
                String data = inlineData.getString("data");
                inlineData.put("data", truncateIfTooLong(data, 100) + " [truncated]");
                inlineData.put("data_length", data.length());
            }
        }
    }
    
    private String sanitizeFilePath(String path) {
        // Convert absolute paths to relative for privacy
        String home = System.getProperty("user.home");
        if (path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }
    
    private int estimateTokenCount(String text) {
        // Rough estimate: ~4 characters per token
        return text.length() / 4;
    }
    
    private String getStackTraceString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    // Helper classes
    
    private enum LogLevel {
        INFO, ERROR, DEBUG
    }
    
    private static class LogEntry {
        final JSONObject data;
        
        LogEntry(JSONObject data, LogLevel level) {
            this.data = data;
            // Note: level and timestamp could be used for filtering/sorting in future versions
        }
    }
}