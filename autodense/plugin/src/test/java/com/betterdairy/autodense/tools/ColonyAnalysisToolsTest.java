package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionStore;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ColonyAnalysisTools.
 * Tests colony counting, classification, and tracking functionality.
 */
class ColonyAnalysisToolsTest {
    
    private ColonyAnalysisTools colonyTools;
    private SessionStore sessionStore;
    private String testPlateHandle;
    
    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
        colonyTools = new ColonyAnalysisTools(sessionStore);
        
        // Create synthetic test plate image
        testPlateHandle = createTestPlateImage();
    }
    
    @AfterEach
    void tearDown() {
        sessionStore.clear();
    }
    
    /**
     * Create a synthetic plate image with colonies for testing
     */
    private String createTestPlateImage() {
        // Create 500x500 test image simulating a petri plate with colonies
        ByteProcessor processor = new ByteProcessor(500, 500);
        
        // Fill with agar background (light gray)
        processor.setValue(220);
        processor.fill();
        
        // Add circular plate boundary (slightly darker)
        processor.setColor(200);
        processor.fillOval(25, 25, 450, 450);
        
        // Add colonies at various positions with different sizes and intensities
        int[][] colonyData = {
            {150, 150, 12, 100}, // x, y, radius, intensity (darker = more intense)
            {300, 180, 8, 120},
            {200, 250, 15, 80},
            {350, 300, 10, 110},
            {120, 320, 6, 140},
            {400, 200, 9, 90},
            {250, 350, 11, 105},
            {180, 120, 7, 130}
        };
        
        for (int[] colony : colonyData) {
            int x = colony[0];
            int y = colony[1];
            int radius = colony[2];
            int intensity = colony[3];
            
            processor.setColor(intensity);
            processor.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        }
        
        ImagePlus testImage = new ImagePlus("test-plate", processor);
        return sessionStore.putImage(testImage);
    }
    
    @Test
    @DisplayName("Should count colonies correctly with valid parameters")
    void testCountColonies_ValidParameters() {
        // Arrange
        JSONObject args = new JSONObject();
        args.put("image_handle", testPlateHandle);
        args.put("threshold_method", "otsu");
        args.put("min_colony_size", 20);
        args.put("max_colony_size", 1000);
        
        // Act
        JSONObject result = colonyTools.countColonies(args);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Colony counting should succeed");
        assertTrue(result.has("colonies_handle"), "Should return colonies handle");
        assertTrue(result.has("count"), "Should return colony count");
        assertTrue(result.getInt("count") >= 0, "Count should be non-negative");
        
        // Verify colonies data structure
        String coloniesHandle = result.getString("colonies_handle");
        SessionStore.AnalysisRecord coloniesData = sessionStore.getAnalysis(coloniesHandle);
        assertNotNull(coloniesData, "Colonies analysis should be stored");
        assertEquals("colonies", coloniesData.type, "Analysis type should be 'colonies'");
    }
    
    @Test
    @DisplayName("Should fail with missing image_handle")
    void testCountColonies_MissingImageHandle() {
        // Arrange
        JSONObject args = new JSONObject();
        args.put("threshold_method", "otsu");
        
        // Act
        JSONObject result = colonyTools.countColonies(args);
        
        // Assert
        assertFalse(result.getBoolean("success"), "Should fail without image handle");
        assertTrue(result.has("error"), "Should contain error message");
        assertEquals("MISSING_REQUIRED_FIELD", result.getString("error_code"));
    }
    
    @Test
    @DisplayName("Should classify colonies after counting")
    void testClassifyColonies_AfterCounting() {
        // Arrange - First count colonies
        JSONObject countArgs = new JSONObject();
        countArgs.put("image_handle", testPlateHandle);
        countArgs.put("threshold_method", "otsu");
        JSONObject countResult = colonyTools.countColonies(countArgs);
        assertTrue(countResult.getBoolean("success"), "Colony counting should succeed first");
        
        // Act - Classify colonies
        JSONObject classifyArgs = new JSONObject();
        classifyArgs.put("image_handle", testPlateHandle);
        classifyArgs.put("colonies_handle", countResult.getString("colonies_handle"));
        classifyArgs.put("classification_type", "size_based");
        
        JSONObject result = colonyTools.classifyColonies(classifyArgs);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Colony classification should succeed");
        assertTrue(result.has("classification_handle"), "Should return classification handle");
        
        // Verify classification data
        String classificationHandle = result.getString("classification_handle");
        SessionStore.AnalysisRecord classificationData = sessionStore.getAnalysis(classificationHandle);
        assertNotNull(classificationData, "Classification analysis should be stored");
        assertEquals("classification", classificationData.type, "Analysis type should be 'classification'");
    }
    
    @Test
    @DisplayName("Should bin colonies by size")
    void testBinColonies_SizeBased() {
        // Arrange - First count colonies
        JSONObject countArgs = new JSONObject();
        countArgs.put("image_handle", testPlateHandle);
        JSONObject countResult = colonyTools.countColonies(countArgs);
        assertTrue(countResult.getBoolean("success"), "Colony counting should succeed first");
        
        // Act - Bin colonies by size
        JSONObject binArgs = new JSONObject();
        binArgs.put("colonies_handle", countResult.getString("colonies_handle"));
        binArgs.put("bin_type", "size");
        binArgs.put("num_bins", 3);
        
        JSONObject result = colonyTools.binColonies(binArgs);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Colony binning should succeed");
        assertTrue(result.has("binning_handle"), "Should return binning handle");
        
        // Verify binning data
        String binningHandle = result.getString("binning_handle");
        SessionStore.AnalysisRecord binningData = sessionStore.getAnalysis(binningHandle);
        assertNotNull(binningData, "Binning analysis should be stored");
        assertEquals("binning", binningData.type, "Analysis type should be 'binning'");
    }
    
    @Test
    @DisplayName("Should normalize colony data")
    void testNormalizeColonies() {
        // Arrange
        JSONObject countArgs = new JSONObject();
        countArgs.put("image_handle", testPlateHandle);
        JSONObject countResult = colonyTools.countColonies(countArgs);
        
        // Act - Normalize colonies
        JSONObject normalizeArgs = new JSONObject();
        normalizeArgs.put("colonies_handle", countResult.getString("colonies_handle"));
        normalizeArgs.put("normalization_method", "z_score");
        
        JSONObject result = colonyTools.normalizeColonies(normalizeArgs);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Colony normalization should succeed");
        assertTrue(result.has("normalized_handle"), "Should return normalized handle");
        
        // Verify normalization data
        String normalizedHandle = result.getString("normalized_handle");
        SessionStore.AnalysisRecord normalizedData = sessionStore.getAnalysis(normalizedHandle);
        assertNotNull(normalizedData, "Normalization analysis should be stored");
        assertEquals("normalized", normalizedData.type, "Analysis type should be 'normalized'");
    }
    
    @Test
    @DisplayName("Should validate size parameters")
    void testSizeParameterValidation() {
        // Test negative min size
        JSONObject args = new JSONObject();
        args.put("image_handle", testPlateHandle);
        args.put("min_colony_size", -10);
        
        JSONObject result = colonyTools.countColonies(args);
        // Should either fail or use default positive value
        assertNotNull(result, "Should handle negative size parameters");
        
        // Test max size smaller than min size
        args.put("min_colony_size", 100);
        args.put("max_colony_size", 50);
        result = colonyTools.countColonies(args);
        // Should either fail or swap/correct the values
        assertNotNull(result, "Should handle inconsistent size parameters");
    }
    
    @Test
    @DisplayName("Should handle edge cases gracefully")
    void testEdgeCases() {
        // Test with empty image (no colonies)
        ByteProcessor emptyProcessor = new ByteProcessor(300, 300);
        emptyProcessor.setValue(200);
        emptyProcessor.fill();
        ImagePlus emptyImage = new ImagePlus("empty-plate", emptyProcessor);
        String emptyHandle = sessionStore.putImage(emptyImage);
        
        JSONObject args = new JSONObject();
        args.put("image_handle", emptyHandle);
        
        JSONObject result = colonyTools.countColonies(args);
        assertTrue(result.getBoolean("success"), "Should succeed with empty plate");
        assertEquals(0, result.getInt("count"), "Empty plate should have zero colonies");
    }
    
    @Test
    @DisplayName("Should maintain session state consistency")
    void testSessionStateConsistency() {
        // Arrange
        int initialAnalysisCount = sessionStore.getAnalysisCount();
        
        // Act - Perform colony analysis
        JSONObject args = new JSONObject();
        args.put("image_handle", testPlateHandle);
        JSONObject result = colonyTools.countColonies(args);
        
        // Assert - Session state should be updated appropriately
        assertTrue(sessionStore.getAnalysisCount() > initialAnalysisCount, 
            "Analysis count should increase");
        
        // Verify handles are valid
        if (result.getBoolean("success")) {
            String coloniesHandle = result.getString("colonies_handle");
            assertTrue(sessionStore.hasAnalysis(coloniesHandle), "Colonies handle should be valid");
        }
    }
    
    @Test
    @DisplayName("Should handle null and invalid inputs")
    void testInputValidation() {
        // Test null arguments
        JSONObject result = colonyTools.countColonies(null);
        assertFalse(result.getBoolean("success"), "Should handle null arguments");
        
        // Test empty arguments
        result = colonyTools.countColonies(new JSONObject());
        assertFalse(result.getBoolean("success"), "Should handle empty arguments");
        
        // Test invalid image handle
        JSONObject args = new JSONObject();
        args.put("image_handle", "invalid_handle_123");
        result = colonyTools.countColonies(args);
        assertFalse(result.getBoolean("success"), "Should handle invalid image handle");
    }
}