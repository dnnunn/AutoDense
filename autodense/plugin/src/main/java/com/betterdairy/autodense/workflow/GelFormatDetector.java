package com.betterdairy.autodense.workflow;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.ArrayList;

/**
 * Intelligent gel format detection and fallback system.
 * 
 * Handles cases where users don't specify gel format or lane count by:
 * 1. Attempting automatic lane detection with generic parameters
 * 2. Analyzing detected lanes to suggest appropriate formats
 * 3. Providing fallback defaults based on workflow type
 * 4. Prompting users for clarification when needed
 */
public class GelFormatDetector {
    
    public enum DetectionStrategy {
        USER_SPECIFIED("User provided format"),
        AUTO_DETECT("Automatic lane detection"),
        INTELLIGENT_GUESS("Intelligent format guessing"),
        PROMPT_USER("Ask user for clarification"),
        FALLBACK_DEFAULT("Use workflow default");
        
        private final String description;
        
        DetectionStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    public static class DetectionResult {
        public final DetectionStrategy strategy;
        public final Object format;  // NuPAGEFormat or BioRadFormat
        public final int detectedLanes;
        public final double confidence;
        public final String reasoning;
        public final boolean needsUserConfirmation;
        public final List<Object> alternativeFormats;
        
        public DetectionResult(DetectionStrategy strategy, Object format, int detectedLanes, 
                              double confidence, String reasoning, boolean needsConfirmation) {
            this.strategy = strategy;
            this.format = format;
            this.detectedLanes = detectedLanes;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.needsUserConfirmation = needsConfirmation;
            this.alternativeFormats = new ArrayList<>();
        }
    }
    
    /**
     * Main detection method - handles all scenarios for missing gel format info
     */
    public static DetectionResult detectGelFormat(String workflowType, String userInput, 
                                                 JSONObject imageAnalysis) {
        
        // Strategy 1: Check if user specified format explicitly
        DetectionResult userSpecified = checkUserSpecifiedFormat(userInput);
        if (userSpecified != null) {
            return userSpecified;
        }
        
        // Strategy 2: Attempt automatic lane detection
        DetectionResult autoDetection = attemptAutoDetection(imageAnalysis, workflowType);
        if (autoDetection != null && autoDetection.confidence > 0.8) {
            return autoDetection;
        }
        
        // Strategy 3: Intelligent guessing based on workflow and context
        DetectionResult intelligentGuess = makeIntelligentGuess(workflowType, userInput, imageAnalysis);
        if (intelligentGuess != null && intelligentGuess.confidence > 0.6) {
            return intelligentGuess;
        }
        
        // Strategy 4: Prompt user for clarification
        return createUserPrompt(workflowType, autoDetection);
    }
    
    /**
     * Check if user explicitly mentioned a gel format
     */
    private static DetectionResult checkUserSpecifiedFormat(String userInput) {
        if (userInput == null) return null;
        
        String input = userInput.toLowerCase();
        
        // Check for NuPAGE formats
        if (input.contains("nupage") || input.contains("nu-page")) {
            if (input.contains("mini") && input.contains("12")) {
                return new DetectionResult(DetectionStrategy.USER_SPECIFIED, 
                    NuPAGEFormat.createMini12Well(), 12, 0.95,
                    "User specified NuPAGE Mini 12-well", false);
            }
            if (input.contains("mini") && input.contains("15")) {
                return new DetectionResult(DetectionStrategy.USER_SPECIFIED,
                    NuPAGEFormat.createMini15Well(), 15, 0.95,
                    "User specified NuPAGE Mini 15-well", false);
            }
            // Add more NuPAGE detection patterns...
        }
        
        // Check for Bio-Rad formats
        if (input.contains("biorad") || input.contains("bio-rad") || input.contains("agarose")) {
            if (input.contains("10") && (input.contains("well") || input.contains("lane"))) {
                return new DetectionResult(DetectionStrategy.USER_SPECIFIED,
                    BioRadFormat.createMiniSubGT10Well(), 10, 0.90,
                    "User specified Bio-Rad 10-well agarose gel", false);
            }
            if (input.contains("15") && (input.contains("well") || input.contains("lane"))) {
                // Check if wide format
                if (input.contains("wide") || input.contains("130") || input.contains("13")) {
                    return new DetectionResult(DetectionStrategy.USER_SPECIFIED,
                        BioRadFormat.createWideMiniSubGT15Well(), 15, 0.90,
                        "User specified Bio-Rad Wide 15-well", false);
                } else {
                    return new DetectionResult(DetectionStrategy.USER_SPECIFIED,
                        BioRadFormat.createMiniSubGT15Well(), 15, 0.90,
                        "User specified Bio-Rad Mini 15-well", false);
                }
            }
        }
        
        // Check for explicit lane counts
        if (input.matches(".*\\b(\\d{1,2})\\s*(?:lane|well|track).*")) {
            // Extract lane count but return null to let other strategies handle format selection
            return null;
        }
        
        return null;
    }
    
    /**
     * Attempt automatic lane detection with generic parameters
     */
    private static DetectionResult attemptAutoDetection(JSONObject imageAnalysis, String workflowType) {
        if (imageAnalysis == null) return null;
        
        // This would interface with actual ImageJ lane detection
        // For now, simulate the detection logic
        
        // Use generic detection parameters
        JSONObject genericParams = createGenericDetectionParameters();
        
        // Simulate lane detection (in real implementation, this would call ImageJ)
        int detectedLanes = simulateLaneDetection(imageAnalysis, genericParams);
        
        if (detectedLanes > 0) {
            // Try to match detected lanes to known formats
            Object suggestedFormat = suggestFormatForLaneCount(detectedLanes, workflowType);
            double confidence = calculateConfidence(detectedLanes, imageAnalysis);
            
            return new DetectionResult(DetectionStrategy.AUTO_DETECT, suggestedFormat, 
                detectedLanes, confidence,
                "Auto-detected " + detectedLanes + " lanes, suggested format based on common usage",
                confidence < 0.8);  // Need confirmation if low confidence
        }
        
        return null;
    }
    
    /**
     * Make intelligent guess based on workflow type and context
     */
    private static DetectionResult makeIntelligentGuess(String workflowType, String userInput, 
                                                       JSONObject imageAnalysis) {
        
        // Default format suggestions based on workflow type
        switch (workflowType) {
            case "protein_quantification", "compare_lanes" -> {
                // SDS-PAGE workflows typically use NuPAGE
                return new DetectionResult(DetectionStrategy.INTELLIGENT_GUESS,
                    NuPAGEFormat.createMini12Well(), 12, 0.7,
                    "Protein workflows commonly use NuPAGE Mini 12-well format", true);
            }
            case "semi_quantitative_pcr" -> {
                // PCR workflows typically use agarose
                return new DetectionResult(DetectionStrategy.INTELLIGENT_GUESS,
                    BioRadFormat.createMiniSubGT10Well(), 10, 0.7,
                    "PCR workflows commonly use Bio-Rad 10-well agarose format", true);
            }
            default -> {
                // General workflow - most common format
                return new DetectionResult(DetectionStrategy.INTELLIGENT_GUESS,
                    NuPAGEFormat.createMini12Well(), 12, 0.6,
                    "Most common format in lab workflows", true);
            }
        }
    }
    
    /**
     * Create user prompt when automatic detection fails
     */
    private static DetectionResult createUserPrompt(String workflowType, DetectionResult autoDetection) {
        
        // Create fallback with most common format but require confirmation
        Object fallbackFormat;
        int fallbackLanes;
        String reasoning;
        
        if ("semi_quantitative_pcr".equals(workflowType) || 
            (workflowType != null && workflowType.contains("pcr"))) {
            fallbackFormat = BioRadFormat.createMiniSubGT10Well();
            fallbackLanes = 10;
            reasoning = "Please confirm: Using Bio-Rad 10-well agarose as default for PCR analysis. " +
                       "Say 'use 15-well' or 'use NuPAGE' if different.";
        } else {
            fallbackFormat = NuPAGEFormat.createMini12Well();
            fallbackLanes = 12;
            reasoning = "Please confirm: Using NuPAGE Mini 12-well as default for protein analysis. " +
                       "Say 'use 15-well' or 'use Bio-Rad agarose' if different.";
        }
        
        DetectionResult result = new DetectionResult(DetectionStrategy.PROMPT_USER, 
            fallbackFormat, fallbackLanes, 0.5, reasoning, true);
            
        // Add common alternatives
        result.alternativeFormats.add(NuPAGEFormat.createMini15Well());
        result.alternativeFormats.add(BioRadFormat.createMiniSubGT10Well());
        result.alternativeFormats.add(BioRadFormat.createMiniSubGT15Well());
        
        return result;
    }
    
    /**
     * Create generic detection parameters for unknown gels
     */
    private static JSONObject createGenericDetectionParameters() {
        JSONObject params = new JSONObject();
        params.put("sensitivity", 0.7);  // Middle ground sensitivity
        params.put("min_lane_width_px", 15);
        params.put("max_lane_width_px", 100);
        params.put("lane_spacing_tolerance", 0.25);  // Generous tolerance
        params.put("background_method", "median");
        params.put("expected_lanes_range", new JSONArray(List.of(6, 25)));  // Broad range
        return params;
    }
    
    /**
     * Simulate lane detection (replace with actual ImageJ call)
     */
    private static int simulateLaneDetection(JSONObject imageAnalysis, JSONObject params) {
        // This would be replaced with actual ImageJ lane detection
        // For simulation, return a common lane count
        return 12;  // Simulate detecting 12 lanes
    }
    
    /**
     * Suggest format based on detected lane count
     */
    private static Object suggestFormatForLaneCount(int laneCount, String workflowType) {
        
        // For PCR workflows, prefer Bio-Rad agarose
        if ("semi_quantitative_pcr".equals(workflowType) || 
            (workflowType != null && workflowType.contains("pcr"))) {
            
            return switch (laneCount) {
                case 8 -> BioRadFormat.createMiniSubGT8Well();
                case 10 -> BioRadFormat.createMiniSubGT10Well();
                case 15 -> BioRadFormat.createMiniSubGT15Well();  // Could be mini or wide
                case 20 -> BioRadFormat.createWideMiniSubGT20Well();
                case 25 -> BioRadFormat.createWideMiniSubGT25Well();
                default -> BioRadFormat.createMiniSubGT10Well();  // Default
            };
        }
        
        // For protein workflows, prefer NuPAGE
        return switch (laneCount) {
            case 10 -> NuPAGEFormat.createMini10Well();
            case 12 -> NuPAGEFormat.createMini12Well();
            case 15 -> NuPAGEFormat.createMini15Well();
            case 17 -> NuPAGEFormat.createMini17Well();
            case 20 -> NuPAGEFormat.createMidi20Well();
            case 26 -> NuPAGEFormat.createMidi26Well();
            default -> NuPAGEFormat.createMini12Well();  // Default
        };
    }
    
    /**
     * Calculate confidence based on detection quality
     */
    private static double calculateConfidence(int detectedLanes, JSONObject imageAnalysis) {
        // This would analyze actual detection quality metrics
        // For simulation, return confidence based on lane count reasonableness
        
        if (detectedLanes >= 8 && detectedLanes <= 26) {
            return 0.8;  // Reasonable lane count
        } else if (detectedLanes >= 6 && detectedLanes <= 30) {
            return 0.6;  // Possible but unusual
        } else {
            return 0.3;  // Unlikely lane count
        }
    }
    
    /**
     * Generate user-friendly prompt message
     */
    public static String generatePromptMessage(DetectionResult result) {
        StringBuilder message = new StringBuilder();
        
        message.append(result.reasoning);
        
        if (!result.alternativeFormats.isEmpty()) {
            message.append("\n\nAlternatives available:");
            for (Object alt : result.alternativeFormats) {
                if (alt instanceof NuPAGEFormat) {
                    message.append("\n• ").append(((NuPAGEFormat) alt).getName());
                } else if (alt instanceof BioRadFormat) {
                    message.append("\n• ").append(((BioRadFormat) alt).getName());
                }
            }
        }
        
        message.append("\n\nYou can also say:");
        message.append("\n• 'Detect lanes automatically'");
        message.append("\n• 'Use custom gel format'");
        message.append("\n• 'This is a [number]-well gel'");
        
        return message.toString();
    }
}