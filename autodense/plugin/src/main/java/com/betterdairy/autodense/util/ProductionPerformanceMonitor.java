package com.betterdairy.autodense.util;

import com.betterdairy.autodense.performance.PerformanceOptimizer;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Production-ready performance monitoring integration for AutoDense tools.
 * 
 * Extends the base PerformanceOptimizer with:
 * - Aggregate performance metrics collection
 * - Tool-specific performance tracking
 * - Performance regression detection
 * - Automated performance alerts
 * - Integration with error handling systems
 */
public final class ProductionPerformanceMonitor {
    
    private static final Logger logger = Logger.getLogger(ProductionPerformanceMonitor.class.getName());
    
    // Performance metrics storage
    private static final ConcurrentHashMap<String, ToolMetrics> toolMetrics = new ConcurrentHashMap<>();
    
    // Performance thresholds (in milliseconds)
    private static final long WARNING_THRESHOLD_MS = 5000;    // 5 seconds
    private static final long ERROR_THRESHOLD_MS = 30000;     // 30 seconds
    private static final long CRITICAL_THRESHOLD_MS = 120000; // 2 minutes
    
    // Performance regression detection
    private static final double REGRESSION_FACTOR = 1.5; // 50% slower than average
    
    private ProductionPerformanceMonitor() {} // Utility class
    
    /**
     * Enhanced performance monitor with production features
     */
    public static class EnhancedMonitor extends PerformanceOptimizer.PerformanceMonitor {
        private final String toolName;
        private final JSONObject params;
        private long startTime;
        private String currentPhase;
        
        public EnhancedMonitor(String toolName, JSONObject params) {
            super(toolName);
            this.toolName = toolName;
            this.params = params;
            this.startTime = System.nanoTime();
            this.currentPhase = "initialization";
            
            // Initialize tool metrics if first time
            toolMetrics.putIfAbsent(toolName, new ToolMetrics());
            
            logger.log(Level.FINE, "Started performance monitoring for tool: " + toolName);
        }
        
        @Override
        public void checkpoint(String message) {
            super.checkpoint(message);
            this.currentPhase = message;
            
            long elapsed = System.nanoTime() - startTime;
            long elapsedMs = elapsed / 1_000_000;
            
            // Check for performance issues during execution
            if (elapsedMs > WARNING_THRESHOLD_MS) {
                logger.log(Level.WARNING, String.format(
                    "Performance warning: %s taking %dms in phase '%s'", 
                    toolName, elapsedMs, currentPhase));
                
                if (elapsedMs > ERROR_THRESHOLD_MS) {
                    logger.log(Level.SEVERE, String.format(
                        "Performance error: %s taking %dms in phase '%s' - investigating timeout", 
                        toolName, elapsedMs, currentPhase));
                }
            }
        }
        
        @Override
        public long finish() {
            long totalElapsed = super.finish();
            long totalMs = totalElapsed / 1_000_000;
            
            // Record performance metrics
            ToolMetrics metrics = toolMetrics.get(toolName);
            metrics.recordExecution(totalMs, params);
            
            // Check for performance regressions
            double avgMs = metrics.getAverageExecutionTime();
            if (avgMs > 0 && totalMs > avgMs * REGRESSION_FACTOR) {
                logger.log(Level.WARNING, String.format(
                    "Performance regression detected: %s took %dms (%.1fx slower than average %.1fms)",
                    toolName, totalMs, totalMs / avgMs, avgMs));
            }
            
            // Log critical performance issues
            if (totalMs > CRITICAL_THRESHOLD_MS) {
                logger.log(Level.SEVERE, String.format(
                    "Critical performance issue: %s took %dms to complete", toolName, totalMs));
            }
            
            return totalElapsed;
        }
        
        /**
         * Get current performance status for error reporting
         */
        public JSONObject getPerformanceStatus() {
            long currentElapsed = (System.nanoTime() - startTime) / 1_000_000;
            ToolMetrics metrics = toolMetrics.get(toolName);
            
            return new JSONObject()
                .put("tool", toolName)
                .put("current_execution_ms", currentElapsed)
                .put("current_phase", currentPhase)
                .put("average_execution_ms", metrics.getAverageExecutionTime())
                .put("total_executions", metrics.getExecutionCount())
                .put("performance_status", getPerformanceLevel(currentElapsed));
        }
        
        private String getPerformanceLevel(long elapsedMs) {
            if (elapsedMs > CRITICAL_THRESHOLD_MS) return "CRITICAL";
            if (elapsedMs > ERROR_THRESHOLD_MS) return "ERROR";  
            if (elapsedMs > WARNING_THRESHOLD_MS) return "WARNING";
            return "NORMAL";
        }
    }
    
    /**
     * Tool-specific performance metrics
     */
    private static class ToolMetrics {
        private final AtomicLong executionCount = new AtomicLong(0);
        private final DoubleAccumulator totalTime = new DoubleAccumulator(Double::sum, 0.0);
        private final DoubleAccumulator maxTime = new DoubleAccumulator(Double::max, 0.0);
        private final DoubleAccumulator minTime = new DoubleAccumulator(Double::min, Double.MAX_VALUE);
        
        // Parameter-specific performance tracking
        private final ConcurrentHashMap<String, ParameterMetrics> parameterMetrics = new ConcurrentHashMap<>();
        
        void recordExecution(long executionTimeMs, JSONObject params) {
            executionCount.incrementAndGet();
            totalTime.accumulate(executionTimeMs);
            maxTime.accumulate(executionTimeMs);  
            minTime.accumulate(executionTimeMs);
            
            // Track performance by parameter combinations
            String parameterKey = createParameterKey(params);
            parameterMetrics.computeIfAbsent(parameterKey, k -> new ParameterMetrics())
                .recordExecution(executionTimeMs);
        }
        
        double getAverageExecutionTime() {
            long count = executionCount.get();
            return count > 0 ? totalTime.get() / count : 0.0;
        }
        
        long getExecutionCount() {
            return executionCount.get();
        }
        
        JSONObject getMetrics() {
            return new JSONObject()
                .put("execution_count", executionCount.get())
                .put("average_ms", getAverageExecutionTime())
                .put("max_ms", maxTime.get())
                .put("min_ms", minTime.get() == Double.MAX_VALUE ? 0 : minTime.get())
                .put("parameter_performance", getParameterPerformanceMetrics());
        }
        
        private JSONObject getParameterPerformanceMetrics() {
            JSONObject paramPerf = new JSONObject();
            for (var entry : parameterMetrics.entrySet()) {
                paramPerf.put(entry.getKey(), entry.getValue().getMetrics());
            }
            return paramPerf;
        }
        
        private String createParameterKey(JSONObject params) {
            // Create a simplified key from important parameters
            StringBuilder key = new StringBuilder();
            
            // Common parameters that affect performance
            if (params.has("image_handle")) {
                // Extract image dimensions if available for performance correlation
                key.append("img_");
            }
            if (params.has("expected_lanes")) {
                key.append("lanes_").append(params.optInt("expected_lanes"));
            }
            if (params.has("min_size")) {
                key.append("_minsize_").append(params.optDouble("min_size"));
            }
            if (params.has("sensitivity")) {
                key.append("_sens_").append(params.optDouble("sensitivity"));
            }
            
            return key.length() > 0 ? key.toString() : "default";
        }
    }
    
    /**
     * Performance metrics for specific parameter combinations
     */
    private static class ParameterMetrics {
        private final AtomicLong count = new AtomicLong(0);
        private final DoubleAccumulator totalTime = new DoubleAccumulator(Double::sum, 0.0);
        
        void recordExecution(long executionTimeMs) {
            count.incrementAndGet();
            totalTime.accumulate(executionTimeMs);
        }
        
        JSONObject getMetrics() {
            long execCount = count.get();
            return new JSONObject()
                .put("count", execCount)
                .put("average_ms", execCount > 0 ? totalTime.get() / execCount : 0.0);
        }
    }
    
    /**
     * Create enhanced performance monitor for tool execution
     */
    public static EnhancedMonitor createMonitor(String toolName, JSONObject params) {
        return new EnhancedMonitor(toolName, params);
    }
    
    /**
     * Get comprehensive performance report for all tools
     */
    public static JSONObject getPerformanceReport() {
        JSONObject report = new JSONObject();
        JSONObject toolReports = new JSONObject();
        
        for (var entry : toolMetrics.entrySet()) {
            toolReports.put(entry.getKey(), entry.getValue().getMetrics());
        }
        
        report.put("tool_metrics", toolReports);
        report.put("thresholds", new JSONObject()
            .put("warning_ms", WARNING_THRESHOLD_MS)
            .put("error_ms", ERROR_THRESHOLD_MS)
            .put("critical_ms", CRITICAL_THRESHOLD_MS));
        report.put("generated_at", java.time.Instant.now().toString());
        
        return report;
    }
    
    /**
     * Reset all performance metrics (useful for testing)
     */
    public static void resetMetrics() {
        toolMetrics.clear();
        logger.info("Performance metrics reset");
    }
    
    /**
     * Get performance metrics for specific tool
     */
    public static JSONObject getToolMetrics(String toolName) {
        ToolMetrics metrics = toolMetrics.get(toolName);
        return metrics != null ? metrics.getMetrics() : new JSONObject().put("error", "Tool not found");
    }
    
    /**
     * Check if any tools are currently experiencing performance issues
     */
    public static JSONObject getPerformanceAlerts() {
        JSONObject alerts = new JSONObject();
        JSONArray activeAlerts = new JSONArray();
        
        for (var entry : toolMetrics.entrySet()) {
            String toolName = entry.getKey();
            ToolMetrics metrics = entry.getValue();
            double avgTime = metrics.getAverageExecutionTime();
            
            if (avgTime > WARNING_THRESHOLD_MS) {
                JSONObject alert = new JSONObject()
                    .put("tool", toolName)
                    .put("average_time_ms", avgTime)
                    .put("severity", avgTime > CRITICAL_THRESHOLD_MS ? "CRITICAL" : 
                                   avgTime > ERROR_THRESHOLD_MS ? "ERROR" : "WARNING")
                    .put("executions", metrics.getExecutionCount());
                activeAlerts.put(alert);
            }
        }
        
        alerts.put("active_alerts", activeAlerts);
        alerts.put("total_alerts", activeAlerts.length());
        return alerts;
    }
    
    /**
     * Integration with ErrorHandler for performance-related error context
     */
    public static JSONObject getPerformanceContextForError(String toolName) {
        ToolMetrics metrics = toolMetrics.get(toolName);
        if (metrics == null) {
            return new JSONObject().put("performance_data", "No metrics available");
        }
        
        return new JSONObject()
            .put("tool_average_ms", metrics.getAverageExecutionTime())
            .put("total_executions", metrics.getExecutionCount())
            .put("performance_status", metrics.getAverageExecutionTime() > WARNING_THRESHOLD_MS ? 
                "PERFORMANCE_CONCERN" : "NORMAL");
    }
}
