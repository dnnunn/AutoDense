package com.betterdairy.autodense.tools;

import com.betterdairy.autodense.session.SessionStore;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for GelAnalysisTools.
 * Tests core functionality including lane detection, band detection, and quantification.
 */
class GelAnalysisToolsTest {
    
    private GelAnalysisTools gelTools;
    private SessionStore sessionStore;
    // SessionRecovery managed internally by GelAnalysisTools
    private String testImageHandle;
    
    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
        gelTools = new GelAnalysisTools(sessionStore);
        
        // Create synthetic test gel image
        testImageHandle = createTestGelImage();
    }
    
    @AfterEach
    void tearDown() {
        sessionStore.clear();
    }
    
    /**
     * Create a synthetic gel image for testing
     */
    private String createTestGelImage() {
        // Create 400x300 test image simulating a gel with 4 lanes
        ByteProcessor processor = new ByteProcessor(400, 300);
        
        // Fill with background (light gray)
        processor.setValue(200);
        processor.fill();
        
        // Add 4 vertical lanes (darker bands)
        int[] lanePositions = {80, 160, 240, 320};
        for (int laneX : lanePositions) {
            // Lane background (medium gray)
            processor.setRoi(laneX - 20, 50, 40, 200);
            processor.setValue(120);
            processor.fill();
            
            // Add 3 bands per lane at different intensities
            int[] bandYPositions = {80, 140, 200};
            int[] bandIntensities = {50, 70, 90}; // Darker = higher intensity
            
            for (int i = 0; i < bandYPositions.length; i++) {
                processor.setRoi(laneX - 15, bandYPositions[i] - 5, 30, 10);
                processor.setValue(bandIntensities[i]);
                processor.fill();
            }
        }
        
        processor.resetRoi();
        ImagePlus testImage = new ImagePlus("test-gel", processor);
        return sessionStore.putImage(testImage);
    }
    
    @Test
    @DisplayName("Should detect lanes correctly with valid parameters")
    void testDetectLanes_ValidParameters() {
        // Arrange
        JSONObject args = new JSONObject();
        args.put("image_handle", testImageHandle);
        args.put("lane_count", 4);
        args.put("threshold_method", "otsu");
        
        // Act
        JSONObject result = gelTools.detectLanes(args);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Lane detection should succeed");
        assertTrue(result.has("lanes_handle"), "Should return lanes handle");
        assertTrue(result.has("overlay_handle"), "Should return overlay handle");
        
        // Verify lane data structure
        String lanesHandle = result.getString("lanes_handle");
        SessionStore.AnalysisRecord lanesData = sessionStore.getAnalysis(lanesHandle);
        assertNotNull(lanesData, "Lanes analysis should be stored");
        assertEquals("lanes", lanesData.type, "Analysis type should be 'lanes'");
    }
    
    @Test
    @DisplayName("Should fail with missing image_handle")
    void testDetectLanes_MissingImageHandle() {
        // Arrange
        JSONObject args = new JSONObject();
        args.put("lane_count", 4);
        
        // Act
        JSONObject result = gelTools.detectLanes(args);
        
        // Assert
        assertFalse(result.getBoolean("success"), "Should fail without image handle");
        assertTrue(result.has("error"), "Should contain error message");
        assertEquals("MISSING_REQUIRED_FIELD", result.getString("error_code"));
    }
    
    @Test
    @DisplayName("Should fail with invalid image_handle")
    void testDetectLanes_InvalidImageHandle() {
        // Arrange
        JSONObject args = new JSONObject();
        args.put("image_handle", "invalid_handle_123");
        args.put("lane_count", 4);
        
        // Act
        JSONObject result = gelTools.detectLanes(args);
        
        // Assert
        assertFalse(result.getBoolean("success"), "Should fail with invalid handle");
        assertTrue(result.has("error"), "Should contain error message");
    }
    
    @Test
    @DisplayName("Should detect bands after lanes are detected")
    void testDetectBands_AfterLaneDetection() {
        // Arrange - First detect lanes
        JSONObject laneArgs = new JSONObject();
        laneArgs.put("image_handle", testImageHandle);
        laneArgs.put("lane_count", 4);
        JSONObject laneResult = gelTools.detectLanes(laneArgs);
        assertTrue(laneResult.getBoolean("success"), "Lane detection should succeed first");
        
        // Act - Detect bands
        JSONObject bandArgs = new JSONObject();
        bandArgs.put("image_handle", testImageHandle);
        bandArgs.put("lanes_handle", laneResult.getString("lanes_handle"));
        bandArgs.put("threshold_method", "otsu");
        
        JSONObject result = gelTools.detectBands(bandArgs);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Band detection should succeed");
        assertTrue(result.has("bands_handle"), "Should return bands handle");
        assertTrue(result.has("overlay_handle"), "Should return overlay handle");
        
        // Verify band data structure
        String bandsHandle = result.getString("bands_handle");
        SessionStore.AnalysisRecord bandsData = sessionStore.getAnalysis(bandsHandle);
        assertNotNull(bandsData, "Bands analysis should be stored");
        assertEquals("bands", bandsData.type, "Analysis type should be 'bands'");
    }
    
    @Test
    @DisplayName("Should quantify bands after detection")
    void testQuantifyBands_AfterDetection() {
        // Arrange - Detect lanes and bands first
        JSONObject laneArgs = new JSONObject();
        laneArgs.put("image_handle", testImageHandle);
        laneArgs.put("lane_count", 4);
        JSONObject laneResult = gelTools.detectLanes(laneArgs);
        
        JSONObject bandArgs = new JSONObject();
        bandArgs.put("image_handle", testImageHandle);
        bandArgs.put("lanes_handle", laneResult.getString("lanes_handle"));
        JSONObject bandResult = gelTools.detectBands(bandArgs);
        
        // Act - Quantify bands
        JSONObject quantArgs = new JSONObject();
        quantArgs.put("image_handle", testImageHandle);
        quantArgs.put("bands_handle", bandResult.getString("bands_handle"));
        quantArgs.put("method", "integrated_density");
        
        JSONObject result = gelTools.quantifyBands(quantArgs);
        
        // Assert
        assertTrue(result.getBoolean("success"), "Band quantification should succeed");
        assertTrue(result.has("quantification_handle"), "Should return quantification handle");
        
        // Verify quantification data
        String quantHandle = result.getString("quantification_handle");
        SessionStore.AnalysisRecord quantData = sessionStore.getAnalysis(quantHandle);
        assertNotNull(quantData, "Quantification analysis should be stored");
        assertEquals("quantification", quantData.type, "Analysis type should be 'quantification'");
    }
    
    @Test
    @DisplayName("Should handle edge cases gracefully")
    void testEdgeCases() {
        // Test with zero lane count
        JSONObject args = new JSONObject();
        args.put("image_handle", testImageHandle);
        args.put("lane_count", 0);
        
        JSONObject result = gelTools.detectLanes(args);
        assertFalse(result.getBoolean("success"), "Should fail with zero lanes");
        
        // Test with excessive lane count
        args.put("lane_count", 100);
        result = gelTools.detectLanes(args);
        // Should either fail or limit to reasonable number
        // Implementation-specific behavior
    }
    
    @Test
    @DisplayName("Should validate input parameters properly")
    void testInputValidation() {
        // Test null arguments
        JSONObject result = gelTools.detectLanes(null);
        assertFalse(result.getBoolean("success"), "Should handle null arguments");
        
        // Test empty arguments
        result = gelTools.detectLanes(new JSONObject());
        assertFalse(result.getBoolean("success"), "Should handle empty arguments");
        
        // Test missing required fields
        JSONObject args = new JSONObject();
        args.put("threshold_method", "otsu");
        result = gelTools.detectLanes(args);
        assertFalse(result.getBoolean("success"), "Should require image_handle");
    }
    
    @Test
    @DisplayName("Should maintain session state consistency")
    void testSessionStateConsistency() {
        // Arrange
        int initialImageCount = sessionStore.getImageCount();
        int initialAnalysisCount = sessionStore.getAnalysisCount();
        
        // Act - Perform analysis
        JSONObject args = new JSONObject();
        args.put("image_handle", testImageHandle);
        args.put("lane_count", 4);
        JSONObject result = gelTools.detectLanes(args);
        
        // Assert - Session state should be updated appropriately
        assertEquals(initialImageCount, sessionStore.getImageCount(), 
            "Image count should remain same (no new images created)");
        assertTrue(sessionStore.getAnalysisCount() > initialAnalysisCount, 
            "Analysis count should increase");
        
        // Verify handles are valid
        if (result.getBoolean("success")) {
            String lanesHandle = result.getString("lanes_handle");
            String overlayHandle = result.getString("overlay_handle");
            
            assertTrue(sessionStore.hasAnalysis(lanesHandle), "Lanes handle should be valid");
            assertTrue(sessionStore.hasOverlay(overlayHandle), "Overlay handle should be valid");
        }
    }
}