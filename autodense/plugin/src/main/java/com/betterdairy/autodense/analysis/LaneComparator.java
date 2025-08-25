package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Band;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.*;
import java.util.stream.Collectors;
import java.awt.geom.Point2D;

/**
 * MW-aware statistical comparison between gel lanes with multiple testing correction
 */
public class LaneComparator {
    
    /**
     * Peak representation with quantitative data
     */
    public static class Peak {
        public final Band band;
        public final double intensity;         // Background-corrected area
        public final double mw;                // Molecular weight (kDa)
        public final double rf;                // Relative mobility
        public final double snr;               // Signal-to-noise ratio
        
        public Peak(Band band, double intensity, double mw, double rf, double snr) {
            this.band = band;
            this.intensity = intensity;
            this.mw = mw;
            this.rf = rf;
            this.snr = snr;
        }
    }
    
    /**
     * Grouped peaks matched across lanes by molecular weight
     */
    public static class PeakGroup {
        public final double targetMW;           // Target molecular weight (kDa)
        public final double mwTolerance;        // MW tolerance for grouping
        public final Map<Integer, Peak> peaksByLane;  // Lane index -> Peak
        public final List<Integer> presentLanes;      // Lanes where this MW group is found
        
        public PeakGroup(double targetMW, double mwTolerance) {
            this.targetMW = targetMW;
            this.mwTolerance = mwTolerance;
            this.peaksByLane = new HashMap<>();
            this.presentLanes = new ArrayList<>();
        }
        
        public void addPeak(int laneIndex, Peak peak) {
            peaksByLane.put(laneIndex, peak);
            if (!presentLanes.contains(laneIndex)) {
                presentLanes.add(laneIndex);
            }
        }
        
        public Peak getPeak(int laneIndex) {
            return peaksByLane.get(laneIndex);
        }
        
        public boolean hasPeak(int laneIndex) {
            return peaksByLane.containsKey(laneIndex);
        }
        
        public int getGroupSize() {
            return peaksByLane.size();
        }
        
        /**
         * Get intensities for statistical testing (missing lanes as 0)
         */
        public double[] getIntensities(List<Integer> allLanes) {
            return allLanes.stream()
                          .mapToDouble(lane -> hasPeak(lane) ? getPeak(lane).intensity : 0.0)
                          .toArray();
        }
        
        /**
         * Get log2 fold change between two lane groups
         */
        public double getLog2FoldChange(List<Integer> controlLanes, List<Integer> treatmentLanes) {
            double controlMean = controlLanes.stream()
                                           .filter(this::hasPeak)
                                           .mapToDouble(lane -> getPeak(lane).intensity)
                                           .average().orElse(0.0);
            
            double treatmentMean = treatmentLanes.stream()
                                               .filter(this::hasPeak)
                                               .mapToDouble(lane -> getPeak(lane).intensity)
                                               .average().orElse(0.0);
            
            // Add pseudocount to avoid log(0)
            controlMean = Math.max(controlMean, 1.0);
            treatmentMean = Math.max(treatmentMean, 1.0);
            
            return Math.log(treatmentMean / controlMean) / Math.log(2.0);
        }
    }
    
    /**
     * Statistical test result for peak group comparison
     */
    public static class ComparisonResult {
        public final PeakGroup peakGroup;
        public final double pValue;              // Raw p-value from test
        public final double adjustedPValue;      // Multiple testing corrected p-value
        public final double log2FoldChange;      // Log2 fold change
        public final double meanControl;         // Mean intensity in control lanes
        public final double meanTreatment;       // Mean intensity in treatment lanes
        public final boolean isSignificant;     // Whether adjusted p < 0.05
        public final String testMethod;          // Statistical test used
        
        public ComparisonResult(PeakGroup peakGroup, double pValue, double adjustedPValue,
                              double log2FoldChange, double meanControl, double meanTreatment,
                              boolean isSignificant, String testMethod) {
            this.peakGroup = peakGroup;
            this.pValue = pValue;
            this.adjustedPValue = adjustedPValue;
            this.log2FoldChange = log2FoldChange;
            this.meanControl = meanControl;
            this.meanTreatment = meanTreatment;
            this.isSignificant = isSignificant;
            this.testMethod = testMethod;
        }
    }
    
    /**
     * Configuration for lane comparison analysis
     */
    public static class ComparisonConfig {
        public double mwTolerance = 0.1;           // MW tolerance for grouping (kDa)
        public double minIntensity = 100.0;        // Minimum intensity for inclusion
        public double minSNR = 3.0;               // Minimum signal-to-noise ratio
        public String multipleTestingMethod = "holm-bonferroni"; // "holm-bonferroni" or "bonferroni"
        public double significanceLevel = 0.05;    // Alpha level for significance
        public boolean requireBothGroups = true;   // Require peaks in both control and treatment
        public int minGroupSize = 2;              // Minimum number of lanes per group for testing
        
        // Volcano plot parameters
        public double volcanoFCThreshold = 1.0;   // Log2 fold change threshold for volcano
        public double volcanoPThreshold = 0.05;   // P-value threshold for volcano
    }
    
    /**
     * Complete comparison analysis result
     */
    public static class LaneComparisonAnalysis {
        public final List<Integer> controlLanes;
        public final List<Integer> treatmentLanes;
        public final List<PeakGroup> peakGroups;
        public final List<ComparisonResult> results;
        public final ComparisonConfig config;
        public final int totalPeaks;
        public final int significantPeaks;
        public final double fdrLevel;
        
        public LaneComparisonAnalysis(List<Integer> controlLanes, List<Integer> treatmentLanes,
                                    List<PeakGroup> peakGroups, List<ComparisonResult> results,
                                    ComparisonConfig config) {
            this.controlLanes = controlLanes;
            this.treatmentLanes = treatmentLanes;
            this.peakGroups = peakGroups;
            this.results = results;
            this.config = config;
            this.totalPeaks = results.size();
            this.significantPeaks = (int) results.stream().filter(r -> r.isSignificant).count();
            this.fdrLevel = calculateFDR();
        }
        
        private double calculateFDR() {
            if (totalPeaks == 0) return 0.0;
            return (double) significantPeaks / totalPeaks;
        }
        
        /**
         * Get results sorted by adjusted p-value
         */
        public List<ComparisonResult> getSignificantResults() {
            return results.stream()
                         .filter(r -> r.isSignificant)
                         .sorted(Comparator.comparingDouble(r -> r.adjustedPValue))
                         .collect(Collectors.toList());
        }
        
        /**
         * Generate volcano plot data points
         */
        public List<Point2D.Double> getVolcanoPlotPoints() {
            List<Point2D.Double> points = new ArrayList<>();
            for (ComparisonResult result : results) {
                double x = result.log2FoldChange;
                double y = -Math.log10(Math.max(result.adjustedPValue, 1e-10)); // Avoid log(0)
                points.add(new Point2D.Double(x, y));
            }
            return points;
        }
    }
    
    /**
     * Group peaks by molecular weight across lanes
     */
    public static List<PeakGroup> groupPeaksByMW(Map<Integer, List<Peak>> peaksByLane, 
                                                ComparisonConfig config) {
        
        // Collect all unique MW values
        Set<Double> allMWs = new TreeSet<>();
        for (List<Peak> peaks : peaksByLane.values()) {
            for (Peak peak : peaks) {
                if (peak.intensity >= config.minIntensity && peak.snr >= config.minSNR) {
                    allMWs.add(peak.mw);
                }
            }
        }
        
        List<PeakGroup> groups = new ArrayList<>();
        Set<Double> processedMWs = new HashSet<>();
        
        for (double mw : allMWs) {
            if (processedMWs.contains(mw)) continue;
            
            PeakGroup group = new PeakGroup(mw, config.mwTolerance);
            
            // Find all peaks within MW tolerance
            for (Map.Entry<Integer, List<Peak>> entry : peaksByLane.entrySet()) {
                int laneIndex = entry.getKey();
                List<Peak> lanePeaks = entry.getValue();
                
                // Find best matching peak in this lane
                Peak bestMatch = null;
                double bestDistance = Double.MAX_VALUE;
                
                for (Peak peak : lanePeaks) {
                    if (peak.intensity >= config.minIntensity && peak.snr >= config.minSNR) {
                        double distance = Math.abs(peak.mw - mw);
                        if (distance <= config.mwTolerance && distance < bestDistance) {
                            bestDistance = distance;
                            bestMatch = peak;
                        }
                    }
                }
                
                if (bestMatch != null) {
                    group.addPeak(laneIndex, bestMatch);
                    processedMWs.add(bestMatch.mw);
                }
            }
            
            // Only include groups with sufficient representation
            if (group.getGroupSize() >= config.minGroupSize) {
                groups.add(group);
            }
        }
        
        return groups;
    }
    
    /**
     * Perform statistical comparison between control and treatment lanes
     */
    public static LaneComparisonAnalysis compareLanes(Map<Integer, List<Peak>> peaksByLane,
                                                     List<Integer> controlLanes,
                                                     List<Integer> treatmentLanes,
                                                     ComparisonConfig config) {
        
        // Group peaks by molecular weight
        List<PeakGroup> peakGroups = groupPeaksByMW(peaksByLane, config);
        
        // Filter groups that have representation in both control and treatment (if required)
        if (config.requireBothGroups) {
            peakGroups = peakGroups.stream()
                                  .filter(group -> {
                                      boolean hasControl = controlLanes.stream().anyMatch(group::hasPeak);
                                      boolean hasTreatment = treatmentLanes.stream().anyMatch(group::hasPeak);
                                      return hasControl && hasTreatment;
                                  })
                                  .collect(Collectors.toList());
        }
        
        // Perform statistical tests
        List<ComparisonResult> rawResults = new ArrayList<>();
        for (PeakGroup group : peakGroups) {
            ComparisonResult result = performStatisticalTest(group, controlLanes, treatmentLanes, config);
            if (result != null) {
                rawResults.add(result);
            }
        }
        
        // Apply multiple testing correction
        List<ComparisonResult> correctedResults = applyMultipleTestingCorrection(rawResults, config);
        
        return new LaneComparisonAnalysis(controlLanes, treatmentLanes, peakGroups, 
                                        correctedResults, config);
    }
    
    /**
     * Perform statistical test for a single peak group
     */
    private static ComparisonResult performStatisticalTest(PeakGroup group, 
                                                          List<Integer> controlLanes,
                                                          List<Integer> treatmentLanes,
                                                          ComparisonConfig config) {
        
        // Get intensity values
        List<Double> controlValues = controlLanes.stream()
                                                 .filter(group::hasPeak)
                                                 .map(lane -> group.getPeak(lane).intensity)
                                                 .collect(Collectors.toList());
        
        List<Double> treatmentValues = treatmentLanes.stream()
                                                     .filter(group::hasPeak)
                                                     .map(lane -> group.getPeak(lane).intensity)
                                                     .collect(Collectors.toList());
        
        // Need at least 2 values in each group for testing
        if (controlValues.size() < 2 || treatmentValues.size() < 2) {
            return null;
        }
        
        // Calculate means
        double meanControl = controlValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanTreatment = treatmentValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Calculate log2 fold change
        double log2FC = group.getLog2FoldChange(controlLanes, treatmentLanes);
        
        // Perform two-sample t-test (assuming equal variances)
        double pValue = performTTest(controlValues, treatmentValues);
        
        return new ComparisonResult(group, pValue, pValue, // Will be corrected later
                                  log2FC, meanControl, meanTreatment, 
                                  false, "two-sample t-test"); // Significance will be determined after correction
    }
    
    /**
     * Simple two-sample t-test implementation
     */
    private static double performTTest(List<Double> group1, List<Double> group2) {
        if (group1.size() < 2 || group2.size() < 2) return 1.0;
        
        double mean1 = group1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mean2 = group2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Calculate pooled standard deviation
        double var1 = group1.stream().mapToDouble(x -> Math.pow(x - mean1, 2)).sum() / (group1.size() - 1);
        double var2 = group2.stream().mapToDouble(x -> Math.pow(x - mean2, 2)).sum() / (group2.size() - 1);
        
        double pooledVar = ((group1.size() - 1) * var1 + (group2.size() - 1) * var2) / 
                          (group1.size() + group2.size() - 2);
        
        double se = Math.sqrt(pooledVar * (1.0/group1.size() + 1.0/group2.size()));
        
        if (se == 0) return 1.0; // No variance
        
        double t = Math.abs(mean1 - mean2) / se;
        int df = group1.size() + group2.size() - 2;
        
        // Approximate p-value using t-distribution (simplified)
        // This is a rough approximation - in production would use proper statistical library
        double pValue = 2.0 * (1.0 - approximateTCDF(t, df));
        return Math.min(pValue, 1.0);
    }
    
    /**
     * Rough approximation of t-distribution CDF for p-value calculation
     */
    private static double approximateTCDF(double t, int df) {
        if (df >= 30) {
            // Use normal approximation for large df
            return 0.5 + 0.5 * Math.tanh(t / Math.sqrt(2.0));
        }
        
        // Simple approximation for small df
        double x = t / Math.sqrt(df);
        return 0.5 + 0.5 * x / Math.sqrt(1 + x * x);
    }
    
    /**
     * Apply multiple testing correction (Holm-Bonferroni or Bonferroni)
     */
    private static List<ComparisonResult> applyMultipleTestingCorrection(List<ComparisonResult> results,
                                                                        ComparisonConfig config) {
        
        List<ComparisonResult> correctedResults = new ArrayList<>();
        
        if (results.isEmpty()) return correctedResults;
        
        // Sort by p-value for Holm-Bonferroni
        List<ComparisonResult> sortedResults = results.stream()
                                                     .sorted(Comparator.comparingDouble(r -> r.pValue))
                                                     .collect(Collectors.toList());
        
        int m = sortedResults.size();
        
        for (int i = 0; i < sortedResults.size(); i++) {
            ComparisonResult result = sortedResults.get(i);
            double adjustedP;
            
            if ("holm-bonferroni".equals(config.multipleTestingMethod)) {
                // Holm-Bonferroni: p_adj = p * (m - i)
                adjustedP = Math.min(1.0, result.pValue * (m - i));
            } else {
                // Standard Bonferroni: p_adj = p * m
                adjustedP = Math.min(1.0, result.pValue * m);
            }
            
            boolean isSignificant = adjustedP < config.significanceLevel;
            
            ComparisonResult correctedResult = new ComparisonResult(
                result.peakGroup, result.pValue, adjustedP,
                result.log2FoldChange, result.meanControl, result.meanTreatment,
                isSignificant, result.testMethod
            );
            
            correctedResults.add(correctedResult);
        }
        
        return correctedResults;
    }
    
    /**
     * Export analysis results to JSON format
     */
    public static JSONObject exportToJSON(LaneComparisonAnalysis analysis) {
        JSONObject json = new JSONObject();
        
        // Analysis metadata
        json.put("control_lanes", new JSONArray(analysis.controlLanes));
        json.put("treatment_lanes", new JSONArray(analysis.treatmentLanes));
        json.put("total_peaks", analysis.totalPeaks);
        json.put("significant_peaks", analysis.significantPeaks);
        json.put("fdr_level", analysis.fdrLevel);
        json.put("multiple_testing_method", analysis.config.multipleTestingMethod);
        json.put("significance_level", analysis.config.significanceLevel);
        
        // Results array
        JSONArray resultsArray = new JSONArray();
        for (ComparisonResult result : analysis.results) {
            JSONObject resultJson = new JSONObject();
            resultJson.put("target_mw", result.peakGroup.targetMW);
            resultJson.put("p_value", result.pValue);
            resultJson.put("adjusted_p_value", result.adjustedPValue);
            resultJson.put("log2_fold_change", result.log2FoldChange);
            resultJson.put("mean_control", result.meanControl);
            resultJson.put("mean_treatment", result.meanTreatment);
            resultJson.put("is_significant", result.isSignificant);
            resultJson.put("test_method", result.testMethod);
            resultJson.put("group_size", result.peakGroup.getGroupSize());
            resultsArray.put(resultJson);
        }
        json.put("results", resultsArray);
        
        return json;
    }
}