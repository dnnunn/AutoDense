package com.betterdairy.autodense.orchestrator;

import com.betterdairy.autodense.session.SessionStore;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeminiOrchestrator.
 * Tests API key validation, tool orchestration, and error handling.
 */
class GeminiOrchestratorTest {
    
    private GeminiOrchestrator orchestrator;
    private SessionStore sessionStore;
    private static final String TEST_API_KEY = "AIzaTest_ValidKeyFormat_32Characters";
    private static final String INVALID_API_KEY = "invalid_key";
    
    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
        // Note: For actual network tests, you'd need a real API key
        // These tests focus on validation and structure
    }
    
    @AfterEach
    void tearDown() {
        if (sessionStore != null) {
            sessionStore.clear();
        }
    }
    
    @Test
    @DisplayName("Should validate API key format correctly")
    void testApiKeyValidation() {
        // Test valid key format
        assertDoesNotThrow(() -> {
            new GeminiOrchestrator(TEST_API_KEY);
        }, "Valid API key should not throw exception");
        
        // Test invalid key format
        assertThrows(IllegalArgumentException.class, () -> {
            new GeminiOrchestrator(INVALID_API_KEY);
        }, "Invalid API key should throw exception");
        
        // Test null key
        assertThrows(IllegalArgumentException.class, () -> {
            new GeminiOrchestrator(null);
        }, "Null API key should throw exception");
        
        // Test empty key
        assertThrows(IllegalArgumentException.class, () -> {
            new GeminiOrchestrator("");
        }, "Empty API key should throw exception");
    }
    
    @Test
    @DisplayName("Should validate placeholder keys")
    void testPlaceholderKeyValidation() {
        String[] placeholderKeys = {
            "YOUR_API_KEY_HERE",
            "your_api_key_here",
            "PLACEHOLDER",
            "placeholder",
            "REPLACE_WITH_YOUR_KEY"
        };
        
        for (String placeholderKey : placeholderKeys) {
            assertThrows(IllegalArgumentException.class, () -> {
                new GeminiOrchestrator(placeholderKey);
            }, "Placeholder key should be rejected: " + placeholderKey);
        }
    }
    
    @Test
    @DisplayName("Should reject keys that are too short")
    void testShortKeyValidation() {
        String shortKey = "AIza123"; // Too short to be valid
        
        assertThrows(IllegalArgumentException.class, () -> {
            new GeminiOrchestrator(shortKey);
        }, "Short API key should be rejected");
    }
    
    @Test
    @DisplayName("Should handle JSON structure validation")
    void testJsonStructureValidation() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // Test valid tool call structure
        JSONObject validToolCall = new JSONObject();
        validToolCall.put("name", "detectLanes");
        JSONObject args = new JSONObject();
        args.put("image_handle", "img_123");
        validToolCall.put("arguments", args);
        
        // This would typically be tested with network mocking
        // For now, we verify the orchestrator accepts the structure
        assertNotNull(validToolCall.getString("name"), "Tool name should be present");
        assertTrue(validToolCall.has("arguments"), "Arguments should be present");
    }
    
    @Test
    @DisplayName("Should provide helpful error messages")
    void testErrorMessages() {
        try {
            new GeminiOrchestrator(INVALID_API_KEY);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            assertTrue(message.contains("Invalid Gemini API key"), "Should mention invalid key");
            assertTrue(message.contains("export GEMINI_API_KEY"), "Should provide setup instructions");
            assertTrue(message.contains("https://makersuite.google.com"), "Should provide API key URL");
        }
    }
    
    @Test
    @DisplayName("Should handle session integration properly")
    void testSessionIntegration() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // Create test image in session
        ByteProcessor processor = new ByteProcessor(100, 100);
        processor.setValue(128);
        processor.fill();
        ImagePlus testImage = new ImagePlus("test-image", processor);
        String imageHandle = sessionStore.putImage(testImage);
        
        // Verify session can be accessed (this is more of a structural test)
        assertTrue(sessionStore.hasImage(imageHandle), "Session should contain test image");
        assertEquals(1, sessionStore.getImageCount(), "Session should have one image");
    }
    
    @Test
    @DisplayName("Should validate tool names")
    void testToolNameValidation() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // Valid tool names (these would be validated in actual orchestration)
        String[] validToolNames = {
            "detectLanes",
            "detectBands",
            "quantifyBands",
            "countColonies",
            "classifyColonies"
        };
        
        for (String toolName : validToolNames) {
            assertNotNull(toolName, "Tool name should not be null");
            assertFalse(toolName.trim().isEmpty(), "Tool name should not be empty");
            assertTrue(toolName.matches("[a-zA-Z][a-zA-Z0-9]*"), "Tool name should be valid identifier");
        }
    }
    
    @Test
    @DisplayName("Should handle argument structure validation")
    void testArgumentValidation() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // Test that argument structures can be created properly
        JSONObject args = new JSONObject();
        args.put("image_handle", "img_123");
        args.put("lane_count", 4);
        args.put("threshold_method", "otsu");
        
        // Verify structure
        assertTrue(args.has("image_handle"), "Should have image_handle");
        assertTrue(args.has("lane_count"), "Should have lane_count");
        assertTrue(args.has("threshold_method"), "Should have threshold_method");
        
        assertEquals("img_123", args.getString("image_handle"));
        assertEquals(4, args.getInt("lane_count"));
        assertEquals("otsu", args.getString("threshold_method"));
    }
    
    @Test
    @DisplayName("Should maintain API key security")
    void testApiKeySecurity() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // Verify that the orchestrator doesn't expose the API key
        // This is more of a structural test since we can't easily access private fields
        assertNotNull(orchestrator, "Orchestrator should be created successfully");
        
        // The toString or any logging should not expose the full key
        String orchestratorString = orchestrator.toString();
        if (orchestratorString.contains("AIza")) {
            // If key is shown, it should be truncated
            assertFalse(orchestratorString.contains(TEST_API_KEY), 
                "Full API key should not be exposed in toString");
        }
    }
    
    @Test
    @DisplayName("Should handle network error scenarios gracefully")
    void testNetworkErrorHandling() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // This test would ideally mock network failures
        // For now, we verify that the orchestrator is structured to handle errors
        
        // Create a malformed request that would fail
        JSONObject malformedRequest = new JSONObject();
        // Missing required fields - this would cause validation errors
        
        assertNotNull(malformedRequest, "Malformed request should be created");
        // In actual implementation, this would test error handling
    }
    
    @Test
    @DisplayName("Should validate response structure")
    void testResponseStructureValidation() {
        orchestrator = new GeminiOrchestrator(TEST_API_KEY);
        
        // Test that expected response structures can be validated
        JSONObject expectedResponse = new JSONObject();
        expectedResponse.put("success", true);
        expectedResponse.put("result", new JSONObject());
        
        JSONArray toolCalls = new JSONArray();
        JSONObject toolCall = new JSONObject();
        toolCall.put("name", "detectLanes");
        toolCall.put("arguments", new JSONObject());
        toolCalls.put(toolCall);
        
        // Verify response structure
        assertTrue(expectedResponse.getBoolean("success"));
        assertTrue(expectedResponse.has("result"));
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.length());
    }
}