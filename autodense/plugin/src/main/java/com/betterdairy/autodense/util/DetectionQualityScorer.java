package com.betterdairy.autodense.util;

import org.json.JSONObject;
import java.util.logging.Logger;

/**
 * Phase 1.3: Detection Quality Scoring System
 * 
 * Implements weighted scoring function for reconciliation candidate evaluation:
 * score = w1*coverage_total + w2*stability + w3*geometry_score + w4*prior_match - w5*violations
 * 
 * PRINCIPLE: Select best candidate among safe parameter tweaks, keep raw counts sacred
 * 
 * Scoring criteria by analysis type:
 * - SDS-PAGE: Emphasize lane geometry, coverage, stability
 * - EtBr: Emphasize lane parallelism, coverage, minimal violations
 * - Colony: Emphasize coverage, minimal edge artifacts, count stability
 */
public class DetectionQualityScorer {
    
    private static final Logger logger = Logger.getLogger(DetectionQualityScorer.class.getName());
    
    // Analysis type identifiers
    public enum AnalysisType {
        SDS_PAGE,
        ETBR_AGAROSE, 
        COLONY_BLUE_WHITE
    }
    
    /**
     * Main scoring function for detection quality evaluation
     * 
     * @param observation Telemetry data from run_report observation section
     * @param rawCount The sacred raw detection count
     * @param candidateCount Proposed reconciled count
     * @param analysisType Type of analysis for weight selection
     * @return Quality score [0.0 to 1.0+], higher is better
     */
    public static double calculateQualityScore(JSONObject observation, int rawCount, 
                                             int candidateCount, AnalysisType analysisType) {
        try {
            // Extract telemetry components
            double coverage = observation.optDouble("coverage_total", 0.0);
            double stability = observation.optDouble("count_stability_score", 0.5);
            double geometry = calculateGeometryScore(observation, analysisType);
            double priorMatch = calculatePriorMatchScore(rawCount, candidateCount, analysisType);
            double violations = calculateViolationPenalty(observation);
            
            // Get analysis-specific weights
            ScoringWeights weights = getWeightsForAnalysis(analysisType);
            
            // Calculate weighted score
            double score = weights.coverage * coverage + 
                          weights.stability * stability + 
                          weights.geometry * geometry + 
                          weights.priorMatch * priorMatch - 
                          weights.violations * violations;
            
            // Ensure score is non-negative
            score = Math.max(0.0, score);
            
            logger.fine(String.format("Quality score: %.3f (cov=%.2f, stab=%.2f, geom=%.2f, prior=%.2f, viol=%.2f)", 
                                     score, coverage, stability, geometry, priorMatch, violations));
            
            return score;
            
        } catch (Exception e) {
            logger.warning("Failed to calculate quality score: " + e.getMessage());
            return 0.5; // Conservative middle-ground score
        }
    }
    
    /**
     * Calculate geometry score based on analysis type
     */
    private static double calculateGeometryScore(JSONObject observation, AnalysisType analysisType) {
        switch (analysisType) {
            case SDS_PAGE:
                return calculateSdsGeometryScore(observation);
            case ETBR_AGAROSE:
                return calculateEtbrGeometryScore(observation);
            case COLONY_BLUE_WHITE:
                return calculateColonyGeometryScore(observation);
            default:
                return 0.5;
        }
    }
    
    /**
     * SDS-PAGE geometry: Lane parallelism, spacing consistency, ladder physics
     */
    private static double calculateSdsGeometryScore(JSONObject observation) {
        double parallelism = observation.optDouble("lane_parallelism_score", 0.5);
        double spacingCV = observation.optDouble("lane_spacing_cv", 0.3);
        double ladderPhysics = observation.optDouble("ladder_physics_score", 0.5);
        
        // Convert CV to score (lower CV = better geometry)
        double spacingScore = Math.max(0.0, 1.0 - spacingCV);
        
        // Weighted combination for SDS geometry
        return 0.4 * parallelism + 0.4 * spacingScore + 0.2 * ladderPhysics;
    }
    
    /**
     * EtBr geometry: Lane parallelism paramount, minimal edge artifacts
     */
    private static double calculateEtbrGeometryScore(JSONObject observation) {
        double parallelism = observation.optDouble("lane_parallelism_score", 0.5);
        double spacingCV = observation.optDouble("lane_spacing_cv", 0.3);
        double positionJitter = observation.optDouble("position_jitter_px", 5.0);
        
        // Convert metrics to scores
        double spacingScore = Math.max(0.0, 1.0 - spacingCV);
        double jitterScore = Math.max(0.0, 1.0 - positionJitter / 10.0); // Normalize by 10px
        
        // EtBr emphasizes parallelism heavily
        return 0.6 * parallelism + 0.3 * spacingScore + 0.1 * jitterScore;
    }
    
    /**
     * Colony geometry: Edge artifacts, plate coverage, mask quality
     */
    private static double calculateColonyGeometryScore(JSONObject observation) {
        // Check if observation has coverage section
        JSONObject coverage = observation.optJSONObject("coverage");
        if (coverage != null) {
            double edgePileup = coverage.optDouble("edge_pileup_score", 0.1);
            double foregroundEnergy = coverage.optDouble("foreground_energy", 0.7);
            
            // Lower edge pileup = better geometry
            double edgeScore = Math.max(0.0, 1.0 - edgePileup);
            
            return 0.6 * edgeScore + 0.4 * foregroundEnergy;
        }
        
        // Fallback: Use plate ROI coverage if available
        JSONObject plateRoi = observation.optJSONObject("plate_roi");
        if (plateRoi != null) {
            double coverage_frac = plateRoi.optDouble("coverage_frac", 0.7);
            double fitError = plateRoi.optDouble("fit_error_px", 3.0);
            
            double fitScore = Math.max(0.0, 1.0 - fitError / 10.0); // Normalize by 10px
            return 0.7 * coverage_frac + 0.3 * fitScore;
        }
        
        return 0.6; // Conservative default
    }
    
    /**
     * Calculate how well candidate count matches priors (but never override raw measurement)
     */
    private static double calculatePriorMatchScore(int rawCount, int candidateCount, AnalysisType analysisType) {
        // If candidate = raw, perfect prior alignment (no artificial inflation)
        if (candidateCount == rawCount) {
            return 1.0;
        }
        
        // Penalize deviations from raw count (raw is sacred)
        double deviation = Math.abs(candidateCount - rawCount) / (double) Math.max(1, rawCount);
        
        // Strong penalty for moving away from raw measurement
        return Math.max(0.0, 1.0 - 2.0 * deviation);
    }
    
    /**
     * Calculate violation penalty from physics constraints
     */
    private static double calculateViolationPenalty(JSONObject observation) {
        double laneViolations = observation.optDouble("lane_physics_violations", 0.0);
        double bandViolations = observation.optDouble("band_physics_violations", 0.0);
        
        // Total violations with capping at 1.0
        return Math.min(1.0, laneViolations + bandViolations);
    }
    
    /**
     * Get analysis-specific scoring weights
     */
    private static ScoringWeights getWeightsForAnalysis(AnalysisType analysisType) {
        switch (analysisType) {
            case SDS_PAGE:
                // SDS: Emphasize geometry and stability, moderate coverage
                return new ScoringWeights(0.25, 0.30, 0.35, 0.20, 0.50);
            case ETBR_AGAROSE:
                // EtBr: Emphasize geometry (parallelism) and minimal violations
                return new ScoringWeights(0.30, 0.25, 0.40, 0.15, 0.60);
            case COLONY_BLUE_WHITE:
                // Colony: Emphasize coverage and stability, minimal edge artifacts
                return new ScoringWeights(0.40, 0.35, 0.20, 0.15, 0.45);
            default:
                // Balanced default weights
                return new ScoringWeights(0.30, 0.30, 0.30, 0.20, 0.50);
        }
    }
    
    /**
     * Determine whether reconciliation should be applied based on scoring
     * 
     * PRINCIPLE: Only reconcile when there's clear benefit AND raw counts are preserved
     * 
     * @param observation Telemetry data
     * @param rawCount Sacred raw detection count
     * @param candidateCount Proposed reconciled count  
     * @param analysisType Type of analysis
     * @return ReconciliationDecision with explanation
     */
    public static ReconciliationDecision shouldReconcile(JSONObject observation, int rawCount, 
                                                        int candidateCount, AnalysisType analysisType) {
        
        // RULE 1: Raw counts are sacred - preserve by default
        if (candidateCount == rawCount) {
            return new ReconciliationDecision(false, rawCount, 
                "Raw detection preserved: " + rawCount + " (no adjustments needed)");
        }
        
        // RULE 2: Calculate quality scores for comparison
        double rawScore = calculateQualityScore(observation, rawCount, rawCount, analysisType);
        double candidateScore = calculateQualityScore(observation, rawCount, candidateCount, analysisType);
        
        // RULE 3: Only reconcile if candidate is significantly better (10% improvement threshold)
        double improvementThreshold = 0.10;
        double improvement = candidateScore - rawScore;
        
        if (improvement > improvementThreshold) {
            return new ReconciliationDecision(true, candidateCount,
                String.format("Quality improvement: %.2f → %.2f (raw=%d, reconciled=%d)", 
                            rawScore, candidateScore, rawCount, candidateCount));
        } else {
            return new ReconciliationDecision(false, rawCount,
                String.format("Raw detection preserved: %d (candidate=%d, insufficient improvement=%.3f)", 
                            rawCount, candidateCount, improvement));
        }
    }
    
    /**
     * Scoring weights for different components
     */
    private static class ScoringWeights {
        final double coverage;
        final double stability;
        final double geometry;
        final double priorMatch;
        final double violations;
        
        ScoringWeights(double coverage, double stability, double geometry, 
                      double priorMatch, double violations) {
            this.coverage = coverage;
            this.stability = stability;
            this.geometry = geometry;
            this.priorMatch = priorMatch;
            this.violations = violations;
        }
    }
    
    /**
     * Decision result for reconciliation
     */
    public static class ReconciliationDecision {
        public final boolean shouldReconcile;
        public final int finalCount;
        public final String explanation;
        
        public ReconciliationDecision(boolean shouldReconcile, int finalCount, String explanation) {
            this.shouldReconcile = shouldReconcile;
            this.finalCount = finalCount;
            this.explanation = explanation;
        }
    }
    
    /**
     * Parse analysis type from task string
     */
    public static AnalysisType parseAnalysisType(String task) {
        if (task == null) return AnalysisType.SDS_PAGE;
        
        switch (task.toLowerCase()) {
            case "sds_page":
                return AnalysisType.SDS_PAGE;
            case "etbr_agarose":
                return AnalysisType.ETBR_AGAROSE;
            case "colonies_blue_white":
                return AnalysisType.COLONY_BLUE_WHITE;
            default:
                logger.warning("Unknown task type: " + task + ", defaulting to SDS_PAGE");
                return AnalysisType.SDS_PAGE;
        }
    }
}
