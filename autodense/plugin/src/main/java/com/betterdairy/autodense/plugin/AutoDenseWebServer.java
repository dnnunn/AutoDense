package com.betterdairy.autodense.plugin;

import com.betterdairy.autodense.orchestrator.GeminiOrchestrator;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Embedded web server for AutoDense web interface
 * Serves the overlay visualization system and API endpoints
 */
public class AutoDenseWebServer {
    private final int port;
    private final GeminiOrchestrator orchestrator;
    private HttpServer server;
    private Path webRoot;
    
    public AutoDenseWebServer(int port, GeminiOrchestrator orchestrator) {
        this.port = port;
        this.orchestrator = orchestrator;
        
        // Set up web root directory
        this.webRoot = Paths.get("autodense/plugin/src/main/resources/web");
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        
        // Static content
        server.createContext("/", this::handleStatic);
        server.createContext("/overlays/", this::handleOverlays);
        server.createContext("/images/", this::handleImages);
        
        // API endpoints
        server.createContext("/api/load-test-image", this::handleLoadTestImage);
        server.createContext("/api/detect-lanes", this::handleDetectLanes);
        server.createContext("/api/detect-bands", this::handleDetectBands);
        server.createContext("/api/quantify-bands", this::handleQuantifyBands);
        server.createContext("/api/export-results", this::handleExportResults);
        
        server.start();
        System.out.println("AutoDense Web Server started on http://localhost:" + port);
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("AutoDense Web Server stopped");
        }
    }
    
    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        
        Path filePath = webRoot.resolve(path.substring(1));
        
        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            byte[] content = Files.readAllBytes(filePath);
            String contentType = getContentType(path);
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(content);
            }
        } else {
            send404(exchange);
        }
    }
    
    private void handleOverlays(HttpExchange exchange) throws IOException {
        Path overlayFile = findMostRecentOverlay();
        
        if (overlayFile != null && Files.exists(overlayFile)) {
            byte[] content = Files.readAllBytes(overlayFile);
            
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, content.length);
            
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(content);
            }
        } else {
            send404(exchange);
        }
    }
    
    private void handleImages(HttpExchange exchange) throws IOException {
        // Placeholder for image serving
        JSONObject response = new JSONObject()
            .put("message", "Image endpoint - implement based on SessionStore");
            
        sendJson(exchange, response);
    }
    
    private void handleLoadTestImage(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        try {
            var futureResult = orchestrator.processCommand("Load test gel image", null);
            var result = futureResult.get(30, TimeUnit.SECONDS);
            
            sendJson(exchange, new JSONObject()
                .put("success", result.success)
                .put("message", result.success ? "Test image loaded successfully" : "Failed to load image"));
                
        } catch (Exception e) {
            sendJson(exchange, new JSONObject()
                .put("success", false)
                .put("message", "Failed to load test image: " + e.getMessage()));
        }
    }
    
    private void handleDetectLanes(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        try {
            var futureResult = orchestrator.processCommand("detect 12 lanes", null);
            var result = futureResult.get(30, TimeUnit.SECONDS);
            
            JSONObject response = new JSONObject()
                .put("success", result.success)
                .put("lanes_found", extractFromResult(result, "lanes_found", 0))
                .put("overlay_png", "/overlays/current.png");
                
            sendJson(exchange, response);
            
        } catch (Exception e) {
            sendJson(exchange, new JSONObject()
                .put("success", false)
                .put("message", "Failed to detect lanes: " + e.getMessage()));
        }
    }
    
    private void handleDetectBands(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        try {
            var futureResult = orchestrator.processCommand("detect protein bands", null);
            var result = futureResult.get(30, TimeUnit.SECONDS);
            
            JSONObject response = new JSONObject()
                .put("success", result.success)
                .put("bands_total", extractFromResult(result, "bands_total", 0))
                .put("overlay_png", "/overlays/current.png");
                
            sendJson(exchange, response);
            
        } catch (Exception e) {
            sendJson(exchange, new JSONObject()
                .put("success", false)
                .put("message", "Failed to detect bands: " + e.getMessage()));
        }
    }
    
    private void handleQuantifyBands(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        try {
            var futureResult = orchestrator.processCommand("quantify bands", null);
            var result = futureResult.get(30, TimeUnit.SECONDS);
            
            JSONObject response = new JSONObject()
                .put("success", result.success)
                .put("lanes_quantified", extractFromResult(result, "lanes_quantified", 0))
                .put("overlay_png", "/overlays/current.png");
                
            sendJson(exchange, response);
            
        } catch (Exception e) {
            sendJson(exchange, new JSONObject()
                .put("success", false)
                .put("message", "Failed to quantify bands: " + e.getMessage()));
        }
    }
    
    private void handleExportResults(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        try {
            var futureResult = orchestrator.processCommand("export results as CSV", null);
            var result = futureResult.get(30, TimeUnit.SECONDS);
            
            JSONObject response = new JSONObject()
                .put("success", result.success)
                .put("message", result.success ? "Results exported successfully" : "Export failed");
                
            sendJson(exchange, response);
            
        } catch (Exception e) {
            sendJson(exchange, new JSONObject()
                .put("success", false)
                .put("message", "Failed to export results: " + e.getMessage()));
        }
    }
    
    private Path findMostRecentOverlay() {
        try {
            return Files.list(Paths.get(System.getProperty("java.io.tmpdir")))
                .filter(p -> p.getFileName().toString().startsWith("gel_overlay_"))
                .filter(p -> p.getFileName().toString().endsWith(".png"))
                .max((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
    
    private void sendJson(HttpExchange exchange, JSONObject json) throws IOException {
        byte[] response = json.toString().getBytes();
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, response.length);
        
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }
    
    private void send404(HttpExchange exchange) throws IOException {
        String response = "404 Not Found";
        exchange.sendResponseHeaders(404, response.length());
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response.getBytes());
        }
    }
    
    private Object extractFromResult(GeminiOrchestrator.OrchestrationResult result, String key, Object defaultValue) {
        if (result == null || result.toolResult == null) return defaultValue;
        
        // Try to extract from nested data objects
        if (result.toolResult.has("data")) {
            JSONObject data = result.toolResult.optJSONObject("data");
            if (data != null && data.has(key)) {
                return data.has(key) ? data.get(key) : defaultValue;
            }
        }
        
        return result.toolResult.has(key) ? result.toolResult.get(key) : defaultValue;
    }
}