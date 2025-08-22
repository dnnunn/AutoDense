package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.analysis.PlateAnalysisDefaults.*;

/**
 * Demonstration of default values and configuration for agar plate analysis.
 * Shows optimized parameters for common phone photography workflows.
 */
public class PlateAnalysisDefaultsDemo {
    
    public static void main(String[] args) {
        demonstratePhonePhotoDefaults();
    }
    
    /**
     * Show all default values for phone photography workflow
     */
    public static void demonstratePhonePhotoDefaults() {
        System.out.println("=== Agar Plate Analysis Defaults for Phone Photography ===");
        System.out.println("Based on user workflow analysis: 90mm plates, phone cameras, X-gal screening");
        System.out.println();
        
        showPlateDimensions();
        showColonySizeDefaults();
        showDetectionParameters();
        showXGalConfiguration();
        showUIParameterRanges();
        showAutoCalibration();
        showWorkflowExamples();
    }
    
    /**
     * Show standard plate dimensions and rim exclusion
     */
    private static void showPlateDimensions() {
        System.out.println("🔘 PLATE DIMENSIONS");
        System.out.println();
        
        var standard = StandardPlateDimensions.standard90mm();
        var conservative = StandardPlateDimensions.conservative90mm();
        var aggressive = StandardPlateDimensions.aggressive90mm();
        
        System.out.printf("Standard:     %.0fmm dish, %.0fmm rim exclusion → %.0fmm usable radius%n",
                         standard.dishDiameterMm(), standard.rimExclusionMm(), standard.usableRadiusMm());
        System.out.printf("Conservative: %.0fmm dish, %.0fmm rim exclusion → %.0fmm usable radius%n",
                         conservative.dishDiameterMm(), conservative.rimExclusionMm(), conservative.usableRadiusMm());
        System.out.printf("Aggressive:   %.0fmm dish, %.0fmm rim exclusion → %.0fmm usable radius%n",
                         aggressive.dishDiameterMm(), aggressive.rimExclusionMm(), aggressive.usableRadiusMm());
        
        System.out.println();
        System.out.println("Recommendation: Start with 5mm rim exclusion (middle of 4-6mm range)");
        System.out.println("Adjust based on plate quality and edge artifacts");
        System.out.println();
    }
    
    /**
     * Show colony size ranges for different imaging setups
     */
    private static void showColonySizeDefaults() {
        System.out.println("📏 COLONY SIZE PARAMETERS");
        System.out.println();
        
        var phonePhoto = ColonySizeDefaults.phonePhoto90mm();
        var highRes = ColonySizeDefaults.highRes90mm();
        var lowRes = ColonySizeDefaults.lowRes90mm();
        
        System.out.printf("Phone Photo: %.1f-%.1fmm → %d-%dpx (%s)%n",
                         phonePhoto.minDiameterMm(), phonePhoto.maxDiameterMm(),
                         phonePhoto.minDiameterPx(), phonePhoto.maxDiameterPx(),
                         phonePhoto.sizeNote());
        
        System.out.printf("High Res:    %.1f-%.1fmm → %d-%dpx (%s)%n", 
                         highRes.minDiameterMm(), highRes.maxDiameterMm(),
                         highRes.minDiameterPx(), highRes.maxDiameterPx(),
                         highRes.sizeNote());
        
        System.out.printf("Low Res:     %.1f-%.1fmm → %d-%dpx (%s)%n",
                         lowRes.minDiameterMm(), lowRes.maxDiameterMm(),
                         lowRes.minDiameterPx(), lowRes.maxDiameterPx(),
                         lowRes.sizeNote());
        
        System.out.println();
        System.out.println("Standard range: 0.2-2.5mm covers typical E.coli colonies");
        System.out.println("Auto-calibration adjusts pixel ranges based on detected resolution");
        System.out.println();
    }
    
    /**
     * Show DoG and Phansalkar detection parameters
     */
    private static void showDetectionParameters() {
        System.out.println("🔍 DETECTION PARAMETERS");
        System.out.println();
        
        System.out.println("DoG (Difference of Gaussians) Blob Detection:");
        var dogStandard = DoGDetectionDefaults.standardColonies();
        var dogSensitive = DoGDetectionDefaults.sensitiveColonies();
        var dogRobust = DoGDetectionDefaults.robustColonies();
        
        System.out.print("  Standard:  radii = {");
        for (int i = 0; i < dogStandard.radiiPx().length; i++) {
            System.out.printf("%.0f", dogStandard.radiiPx()[i]);
            if (i < dogStandard.radiiPx().length - 1) System.out.print(", ");
        }
        System.out.printf("} px, threshold = %.3f%n", dogStandard.threshold());
        
        System.out.print("  Sensitive: radii = {");
        for (int i = 0; i < dogSensitive.radiiPx().length; i++) {
            System.out.printf("%.0f", dogSensitive.radiiPx()[i]);
            if (i < dogSensitive.radiiPx().length - 1) System.out.print(", ");
        }
        System.out.printf("} px, threshold = %.4f%n", dogSensitive.threshold());
        
        System.out.print("  Robust:    radii = {");
        for (int i = 0; i < dogRobust.radiiPx().length; i++) {
            System.out.printf("%.0f", dogRobust.radiiPx()[i]);
            if (i < dogRobust.radiiPx().length - 1) System.out.print(", ");
        }
        System.out.printf("} px, threshold = %.3f%n", dogRobust.threshold());
        
        System.out.println();
        System.out.println("Phansalkar Local Thresholding:");
        var phansStandard = PhansalkarDefaults.standardColonies();
        var phansFine = PhansalkarDefaults.fineDetail();
        var phansCoarse = PhansalkarDefaults.coarseDetail();
        
        System.out.printf("  Standard:    %dpx radius (k=%.2f, r=%.1f)%n",
                         phansStandard.radiusPx(), phansStandard.k(), phansStandard.r());
        System.out.printf("  Fine Detail: %dpx radius (k=%.2f, r=%.1f)%n",
                         phansFine.radiusPx(), phansFine.k(), phansFine.r());
        System.out.printf("  Coarse:      %dpx radius (k=%.2f, r=%.1f)%n", 
                         phansCoarse.radiusPx(), phansCoarse.k(), phansCoarse.r());
        
        System.out.println();
        System.out.println("Recommendation: Use DoG {3,5,8,12,16,24} or Phansalkar 15-25px radius");
        System.out.println();
    }
    
    /**
     * Show X-gal classification configuration with UI slider ranges
     */
    private static void showXGalConfiguration() {
        System.out.println("🔬 X-GAL CLASSIFICATION");
        System.out.println();
        
        var xgalStandard = XGalDefaults.standardScreening();
        var xgalSensitive = XGalDefaults.sensitiveScreening();
        var xgalStringent = XGalDefaults.stringentScreening();
        
        System.out.printf("Standard:  b* threshold = %.1f (UI slider: %.1f to %.1f)%n",
                         xgalStandard.bDeltaThreshold(), xgalStandard.bDeltaMin(), xgalStandard.bDeltaMax());
        System.out.printf("           ΔE76 = %.1f, SNR_L = %.1f%n",
                         xgalStandard.dE76Threshold(), xgalStandard.snrLThreshold());
        
        System.out.printf("Sensitive: b* threshold = %.1f (UI slider: %.1f to %.1f)%n",
                         xgalSensitive.bDeltaThreshold(), xgalSensitive.bDeltaMin(), xgalSensitive.bDeltaMax());
        System.out.printf("           ΔE76 = %.1f, SNR_L = %.1f%n",
                         xgalSensitive.dE76Threshold(), xgalSensitive.snrLThreshold());
        
        System.out.printf("Stringent: b* threshold = %.1f (UI slider: %.1f to %.1f)%n",
                         xgalStringent.bDeltaThreshold(), xgalStringent.bDeltaMin(), xgalStringent.bDeltaMax());
        System.out.printf("           ΔE76 = %.1f, SNR_L = %.1f%n",
                         xgalStringent.dE76Threshold(), xgalStringent.snrLThreshold());
        
        System.out.println();
        System.out.println("Key insight: Start at b* delta = -6, expose slider ±10 in UI");
        System.out.println("More negative = more selective for blue colonies");
        System.out.println();
    }
    
    /**
     * Show UI parameter ranges for user adjustment
     */
    private static void showUIParameterRanges() {
        System.out.println("🎛️  UI PARAMETER CONFIGURATION");
        System.out.println();
        
        var rimParam = UIParameterConfig.rimExclusion();
        var minSizeParam = UIParameterConfig.minColonySize();
        var maxSizeParam = UIParameterConfig.maxColonySize();
        var xgalParam = UIParameterConfig.xgalThreshold();
        var dogParam = UIParameterConfig.dogSensitivity();
        var phansParam = UIParameterConfig.phansalkarRadius();
        
        System.out.printf("%-25s: %6.1f%-8s [%6.1f - %6.1f, step %4.1f] %s%n",
                         rimParam.parameterName(), rimParam.defaultValue(), rimParam.units(),
                         rimParam.minValue(), rimParam.maxValue(), rimParam.stepSize(),
                         rimParam.tooltip());
        
        System.out.printf("%-25s: %6.1f%-8s [%6.1f - %6.1f, step %4.1f] %s%n",
                         minSizeParam.parameterName(), minSizeParam.defaultValue(), minSizeParam.units(),
                         minSizeParam.minValue(), minSizeParam.maxValue(), minSizeParam.stepSize(),
                         minSizeParam.tooltip());
        
        System.out.printf("%-25s: %6.1f%-8s [%6.1f - %6.1f, step %4.1f] %s%n",
                         maxSizeParam.parameterName(), maxSizeParam.defaultValue(), maxSizeParam.units(),
                         maxSizeParam.minValue(), maxSizeParam.maxValue(), maxSizeParam.stepSize(),
                         maxSizeParam.tooltip());
        
        System.out.printf("%-25s: %6.1f%-8s [%6.1f - %6.1f, step %4.1f] %s%n",
                         xgalParam.parameterName(), xgalParam.defaultValue(), xgalParam.units(),
                         xgalParam.minValue(), xgalParam.maxValue(), xgalParam.stepSize(),
                         xgalParam.tooltip());
        
        System.out.printf("%-25s: %6.3f%-8s [%6.3f - %6.1f, step %4.3f] %s%n",
                         dogParam.parameterName(), dogParam.defaultValue(), dogParam.units(),
                         dogParam.minValue(), dogParam.maxValue(), dogParam.stepSize(),
                         dogParam.tooltip());
        
        System.out.printf("%-25s: %6.0f%-8s [%6.0f - %6.0f, step %4.0f] %s%n",
                         phansParam.parameterName(), phansParam.defaultValue(), phansParam.units(),
                         phansParam.minValue(), phansParam.maxValue(), phansParam.stepSize(),
                         phansParam.tooltip());
        
        System.out.println();
    }
    
    /**
     * Show auto-calibration examples
     */
    private static void showAutoCalibration() {
        System.out.println("⚙️  AUTO-CALIBRATION EXAMPLES");
        System.out.println();
        
        System.out.println("Resolution Detection → Parameter Recommendations:");
        
        // Simulate different imaging scenarios
        double[] testResolutions = {10.0, 15.0, 20.0, 25.0, 30.0};
        String[] scenarios = {"Old phone", "Basic phone", "Good phone", "High-end phone", "DSLR camera"};
        
        for (int i = 0; i < testResolutions.length; i++) {
            double pxPerMM = testResolutions[i];
            var recommended = CalibrationHelper.recommendColonySizes(pxPerMM);
            var dogParams = CalibrationHelper.recommendDoGParams(pxPerMM);
            
            System.out.printf("%-13s (%4.1fpx/mm): colony %d-%dpx, DoG %s%n",
                             scenarios[i], pxPerMM,
                             recommended.minDiameterPx(), recommended.maxDiameterPx(),
                             dogParams.description().toLowerCase().replaceAll(".*for ", ""));
        }
        
        System.out.println();
        System.out.println("DoG Radius Scaling:");
        double[] baseRadii = {3, 5, 8, 12, 16, 24};
        double baseResolution = 20.0; // Reference resolution
        double highResolution = 30.0; // High-res camera
        
        double[] scaledRadii = CalibrationHelper.scaleDoGRadii(baseRadii, highResolution, baseResolution);
        
        System.out.print("  Base (20px/mm):     ");
        for (double r : baseRadii) { System.out.printf("%.0f ", r); }
        System.out.println("px");
        
        System.out.print("  Scaled (30px/mm):   ");
        for (double r : scaledRadii) { System.out.printf("%.0f ", r); }
        System.out.println("px");
        System.out.println();
    }
    
    /**
     * Show complete workflow examples
     */
    private static void showWorkflowExamples() {
        System.out.println("📱 COMPLETE WORKFLOW EXAMPLES");
        System.out.println();
        
        // Standard phone workflow
        var standardFlow = WorkflowDefaults.standardPhoneWorkflow();
        System.out.printf("🔹 %s:%n", standardFlow.workflowName());
        System.out.printf("   %s%n", standardFlow.description());
        System.out.printf("   Plate: %.0fmm, rim %.0fmm%n",
                         standardFlow.plateDims().dishDiameterMm(), standardFlow.plateDims().rimExclusionMm());
        System.out.printf("   Colonies: %.1f-%.1fmm (%d-%dpx)%n",
                         standardFlow.colonySizes().minDiameterMm(), standardFlow.colonySizes().maxDiameterMm(),
                         standardFlow.colonySizes().minDiameterPx(), standardFlow.colonySizes().maxDiameterPx());
        System.out.printf("   X-gal: b*=%.1f, ΔE=%.1f, SNR=%.1f%n",
                         standardFlow.xgalParams().bDeltaThreshold(),
                         standardFlow.xgalParams().dE76Threshold(),
                         standardFlow.xgalParams().snrLThreshold());
        System.out.println();
        
        // High quality workflow
        var highQualityFlow = WorkflowDefaults.highQualityWorkflow();
        System.out.printf("🔹 %s:%n", highQualityFlow.workflowName());
        System.out.printf("   %s%n", highQualityFlow.description());
        System.out.printf("   Plate: %.0fmm, rim %.0fmm%n",
                         highQualityFlow.plateDims().dishDiameterMm(), highQualityFlow.plateDims().rimExclusionMm());
        System.out.printf("   Colonies: %.1f-%.1fmm (%d-%dpx)%n",
                         highQualityFlow.colonySizes().minDiameterMm(), highQualityFlow.colonySizes().maxDiameterMm(),
                         highQualityFlow.colonySizes().minDiameterPx(), highQualityFlow.colonySizes().maxDiameterPx());
        System.out.printf("   X-gal: b*=%.1f, ΔE=%.1f, SNR=%.1f%n",
                         highQualityFlow.xgalParams().bDeltaThreshold(),
                         highQualityFlow.xgalParams().dE76Threshold(),
                         highQualityFlow.xgalParams().snrLThreshold());
        System.out.println();
        
        // Robust workflow
        var robustFlow = WorkflowDefaults.robustWorkflow();
        System.out.printf("🔹 %s:%n", robustFlow.workflowName());
        System.out.printf("   %s%n", robustFlow.description());
        System.out.printf("   Plate: %.0fmm, rim %.0fmm%n",
                         robustFlow.plateDims().dishDiameterMm(), robustFlow.plateDims().rimExclusionMm());
        System.out.printf("   Colonies: %.1f-%.1fmm (%d-%dpx)%n",
                         robustFlow.colonySizes().minDiameterMm(), robustFlow.colonySizes().maxDiameterMm(),
                         robustFlow.colonySizes().minDiameterPx(), robustFlow.colonySizes().maxDiameterPx());
        System.out.printf("   X-gal: b*=%.1f, ΔE=%.1f, SNR=%.1f%n",
                         robustFlow.xgalParams().bDeltaThreshold(),
                         robustFlow.xgalParams().dE76Threshold(),
                         robustFlow.xgalParams().snrLThreshold());
        System.out.println();
        
        System.out.println("💡 IMPLEMENTATION TIPS:");
        System.out.println("   • Auto-detect plate → estimate px/mm → select workflow");
        System.out.println("   • Expose key parameters in UI: rim exclusion, X-gal threshold");
        System.out.println("   • Provide workflow presets: Phone/DSLR/Robust");
        System.out.println("   • Save user preferences per imaging setup");
    }
}