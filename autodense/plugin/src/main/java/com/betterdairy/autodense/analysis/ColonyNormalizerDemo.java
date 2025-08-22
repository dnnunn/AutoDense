package com.betterdairy.autodense.analysis;

/**
 * Demonstration of colony normalization for cross-plate comparison.
 * Shows how portable measurements enable consistent analysis across different:
 * - Camera settings and lighting conditions
 * - Plate media colors and batch variations  
 * - Image acquisition parameters
 */
public class ColonyNormalizerDemo {
    
    public static void main(String[] args) {
        demonstrateNormalization();
    }
    
    /**
     * Show normalization features and benefits
     */
    public static void demonstrateNormalization() {
        System.out.println("=== Colony Normalization for Cross-Plate Analysis ===");
        System.out.println();
        
        // Show normalization principles
        showNormalizationPrinciples();
        
        // Show measurement portability
        showMeasurementPortability();
        
        // Show quadrant analysis
        showQuadrantAnalysis();
        
        // Show practical workflow
        showPracticalWorkflow();
    }
    
    /**
     * Explain normalization principles
     */
    private static void showNormalizationPrinciples() {
        System.out.println("🎯 NORMALIZATION PRINCIPLES");
        System.out.println();
        
        System.out.println("1. COLOR NORMALIZATION:");
        System.out.println("   • Per-plate background Lab color (median inside plate mask)");
        System.out.println("   • Colony deltas: Δa* = a_colony - a_background");
        System.out.println("   • Colony deltas: Δb* = b_colony - b_background");
        System.out.println("   • Color difference: ΔE = √[(ΔL*)² + (Δa*)² + (Δb*)²]");
        System.out.println("   → Makes color thresholds portable between different photos");
        System.out.println();
        
        System.out.println("2. SIZE NORMALIZATION:");
        System.out.println("   • Always report diameter in mm using plate calibration");
        System.out.println("   • Detected plate diameter OR user-specified dish size");
        System.out.println("   • Colony diameter (pixels) ÷ pixels_per_mm = diameter (mm)");
        System.out.println("   → Consistent measurements regardless of camera distance/zoom");
        System.out.println();
        
        System.out.println("3. DENSITY NORMALIZATION:");
        System.out.println("   • Report colonies per cm² based on plate area");
        System.out.println("   • Plate area = π × (radius_mm ÷ 10)² cm²");
        System.out.println("   • Colony density = colony_count ÷ plate_area_cm²");
        System.out.println("   → Comparable density across different plate sizes");
        System.out.println();
        
        System.out.println("4. SPATIAL NORMALIZATION (OPTIONAL):");
        System.out.println("   • Split plate into 4 quadrants for plating QC");
        System.out.println("   • Q0=top-right, Q1=top-left, Q2=bottom-left, Q3=bottom-right");
        System.out.println("   • Report colony counts per quadrant");
        System.out.println("   → Detect uneven plating or contamination patterns");
        System.out.println();
    }
    
    /**
     * Show measurement portability examples
     */
    private static void showMeasurementPortability() {
        System.out.println("📊 MEASUREMENT PORTABILITY EXAMPLES");
        System.out.println();
        
        System.out.println("SCENARIO 1: Same colonies, different lighting");
        System.out.println("  Plate A (bright light): Background Lab = (85, 2, -1)");
        System.out.println("  Plate B (dim light):    Background Lab = (70, 1, -2)");
        System.out.println("  ");
        System.out.println("  Blue colony on Plate A: Lab = (75, 5, -15) → Δa* = +3, Δb* = -14");
        System.out.println("  Blue colony on Plate B: Lab = (60, 4, -16) → Δa* = +3, Δb* = -14");
        System.out.println("  → Same Δa*, Δb* despite different absolute Lab values!");
        System.out.println();
        
        System.out.println("SCENARIO 2: Same colonies, different camera zoom");
        System.out.println("  Plate C (close): 25 pixels/mm → 30px colony = 1.2mm");
        System.out.println("  Plate D (far):  15 pixels/mm → 18px colony = 1.2mm");
        System.out.println("  → Same diameter in mm despite different pixel measurements!");
        System.out.println();
        
        System.out.println("SCENARIO 3: Different plate sizes");
        System.out.println("  90mm dish: 120 colonies → density = 1.89 colonies/cm²");
        System.out.println("  60mm dish: 55 colonies  → density = 1.94 colonies/cm²");
        System.out.println("  → Comparable densities for growth rate analysis!");
        System.out.println();
    }
    
    /**
     * Show quadrant analysis for QC
     */
    private static void showQuadrantAnalysis() {
        System.out.println("🧪 QUADRANT ANALYSIS FOR PLATING QC");
        System.out.println();
        
        System.out.println("EVEN DISTRIBUTION (Good plating):");
        System.out.println("  Q0: 28 colonies | Q1: 31 colonies");
        System.out.println("  Q2: 29 colonies | Q3: 32 colonies");
        System.out.println("  → Coefficient of variation: 5.4% (excellent)");
        System.out.println();
        
        System.out.println("UNEVEN DISTRIBUTION (Poor plating technique):");
        System.out.println("  Q0: 15 colonies | Q1: 45 colonies"); 
        System.out.println("  Q2: 18 colonies | Q3: 42 colonies");
        System.out.println("  → Coefficient of variation: 35.7% (needs technique improvement)");
        System.out.println();
        
        System.out.println("CONTAMINATION PATTERN (Edge effect):");
        System.out.println("  Q0: 28 colonies | Q1: 31 colonies");
        System.out.println("  Q2: 150+ colonies (dense) | Q3: 29 colonies");
        System.out.println("  → Possible contamination in bottom-left region");
        System.out.println();
    }
    
    /**
     * Show practical analysis workflow
     */
    private static void showPracticalWorkflow() {
        System.out.println("🔬 PRACTICAL ANALYSIS WORKFLOW");
        System.out.println();
        
        System.out.println("STEP 1: Individual Plate Analysis");
        System.out.println("  1. ColonyNormalizer.normalize(image, colonies, plateRoi, pxPerMM, true)");
        System.out.println("  2. Extract: normalized colonies + plate statistics");
        System.out.println("  3. Generate: portable color thresholds for this plate");
        System.out.println();
        
        System.out.println("STEP 2: Cross-Plate Comparison");
        System.out.println("  1. Collect normalized data from multiple plates");
        System.out.println("  2. Compare: Δa*, Δb*, ΔE values instead of raw Lab");
        System.out.println("  3. Compare: colony densities (colonies/cm²)");
        System.out.println("  4. Compare: size distributions in mm");
        System.out.println();
        
        System.out.println("STEP 3: Statistical Analysis");
        System.out.println("  1. Pool normalized measurements across experiments");
        System.out.println("  2. Apply consistent thresholds: Δb* < -6 for X-gal+");
        System.out.println("  3. Calculate: mean colony size, density, color distribution");
        System.out.println("  4. QC check: quadrant uniformity across replicates");
        System.out.println();
        
        System.out.println("EXAMPLE OUTPUT:");
        System.out.println("  Experiment A (n=3 plates):");
        System.out.println("    X-gal+: 45.2 ± 3.1 colonies/plate (Δb* = -8.3 ± 1.2)");
        System.out.println("    Size: 1.8 ± 0.4 mm diameter");
        System.out.println("    Density: 1.9 ± 0.2 colonies/cm²");
        System.out.println("    QC: CV = 8.1% (good plating consistency)");
        System.out.println();
        
        System.out.println("CROSS-EXPERIMENT COMPARISON:");
        System.out.println("  Experiment A vs B: 45.2 vs 52.7 X-gal+ colonies (p < 0.05)");
        System.out.println("  Treatment effect: +16.6% transformation efficiency");
        System.out.println("  → Statistically significant improvement detected");
    }
}