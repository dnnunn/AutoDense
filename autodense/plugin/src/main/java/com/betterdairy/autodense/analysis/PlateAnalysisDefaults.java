package com.betterdairy.autodense.analysis;

/**
 * Default values and configuration for agar plate analysis workflows.
 * Optimized for common phone photography of 90mm plates under standard lab conditions.
 * 
 * Based on user workflow analysis:
 * - Standard phone cameras at ~30cm distance from 90mm plates
 * - Typical colony sizes 0.2-2.5mm diameter
 * - X-gal screening with blue/white selection
 * - DoG blob detection or Phansalkar local thresholding
 */
public final class PlateAnalysisDefaults {
    
    /**
     * Standard 90mm petri dish configuration
     */
    public static record StandardPlateDimensions(
        double dishDiameterMm,      // Physical dish diameter
        double rimExclusionMm,      // Exclude rim artifacts
        double usableRadiusMm       // Effective analysis area
    ) {
        public static StandardPlateDimensions standard90mm() {
            return new StandardPlateDimensions(
                90.0,    // 90mm dish diameter
                5.0,     // 5mm rim exclusion (middle of 4-6mm range)
                40.0     // 90/2 - 5 = 40mm usable radius
            );
        }
        
        public static StandardPlateDimensions conservative90mm() {
            return new StandardPlateDimensions(
                90.0,    // 90mm dish diameter
                6.0,     // 6mm rim exclusion (conservative)
                39.0     // 90/2 - 6 = 39mm usable radius
            );
        }
        
        public static StandardPlateDimensions aggressive90mm() {
            return new StandardPlateDimensions(
                90.0,    // 90mm dish diameter
                4.0,     // 4mm rim exclusion (aggressive)
                41.0     // 90/2 - 4 = 41mm usable radius
            );
        }
    }
    
    /**
     * Colony size parameters for typical phone photography
     */
    public static record ColonySizeDefaults(
        double minDiameterMm,       // Minimum colony size
        double maxDiameterMm,       // Maximum colony size
        int minDiameterPx,          // Minimum in pixels (estimated)
        int maxDiameterPx,          // Maximum in pixels (estimated)
        String sizeNote             // Usage context
    ) {
        public static ColonySizeDefaults phonePhoto90mm() {
            return new ColonySizeDefaults(
                0.2, 2.5,               // 0.2-2.5mm range
                4, 50,                  // ~4-50px for phone photography
                "Standard phone photography of 90mm plate (~20px/mm)"
            );
        }
        
        public static ColonySizeDefaults highRes90mm() {
            return new ColonySizeDefaults(
                0.2, 2.5,               // Same mm range
                6, 75,                  // Higher pixel range for better cameras
                "High resolution photography (~30px/mm)"
            );
        }
        
        public static ColonySizeDefaults lowRes90mm() {
            return new ColonySizeDefaults(
                0.5, 3.0,               // Slightly larger mm range to avoid noise
                3, 40,                  // Lower pixel range for older phones
                "Lower resolution photography (~13px/mm)"
            );
        }
    }
    
    /**
     * DoG (Difference of Gaussians) blob detection parameters
     */
    public static record DoGDetectionDefaults(
        double[] radiiPx,           // DoG radii in pixels
        double threshold,           // Detection threshold
        boolean findMaxima,         // Find bright or dark spots
        String description          // Parameter context
    ) {
        public static DoGDetectionDefaults standardColonies() {
            return new DoGDetectionDefaults(
                new double[]{3, 5, 8, 12, 16, 24},  // Multi-scale radii
                0.01,                                // Conservative threshold
                false,                               // Find dark colonies (minima)
                "Standard colony detection with multi-scale DoG"
            );
        }
        
        public static DoGDetectionDefaults sensitiveColonies() {
            return new DoGDetectionDefaults(
                new double[]{2, 4, 6, 10, 14, 20, 28},  // More radii, finer scale
                0.005,                                   // More sensitive threshold
                false,                                   // Find dark colonies
                "Sensitive detection for faint or small colonies"
            );
        }
        
        public static DoGDetectionDefaults robustColonies() {
            return new DoGDetectionDefaults(
                new double[]{4, 8, 16, 32},          // Fewer radii, coarse scale
                0.02,                                // Less sensitive threshold
                false,                               // Find dark colonies
                "Robust detection to avoid noise and artifacts"
            );
        }
    }
    
    /**
     * Phansalkar local thresholding parameters
     */
    public static record PhansalkarDefaults(
        int radiusPx,               // Local window radius
        double k,                   // Phansalkar k parameter
        double r,                   // Phansalkar r parameter
        String description          // Parameter context
    ) {
        public static PhansalkarDefaults standardColonies() {
            return new PhansalkarDefaults(
                20,                     // 20px radius (middle of 15-25 range)
                0.25,                   // Standard k parameter
                0.5,                    // Standard r parameter
                "Standard local thresholding for colony detection"
            );
        }
        
        public static PhansalkarDefaults fineDetail() {
            return new PhansalkarDefaults(
                15,                     // Smaller radius for fine detail
                0.2,                    // More sensitive k
                0.4,                    // Adjusted r
                "Fine detail detection for small colonies"
            );
        }
        
        public static PhansalkarDefaults coarseDetail() {
            return new PhansalkarDefaults(
                25,                     // Larger radius for coarse features
                0.3,                    // Less sensitive k
                0.6,                    // Higher r parameter
                "Coarse detection for large colonies"
            );
        }
    }
    
    /**
     * X-gal classification thresholds with UI adjustment ranges
     */
    public static record XGalDefaults(
        double bDeltaThreshold,     // b* delta threshold
        double bDeltaMin,           // UI slider minimum
        double bDeltaMax,           // UI slider maximum
        double dE76Threshold,       // Color difference threshold
        double snrLThreshold,       // Signal-to-noise ratio threshold
        String description          // Parameter context
    ) {
        public static XGalDefaults standardScreening() {
            return new XGalDefaults(
                -6.0,                   // Start at -6 as specified
                -16.0,                  // UI slider: -6 - 10 = -16
                +4.0,                   // UI slider: -6 + 10 = +4
                8.0,                    // Standard Delta E76 threshold
                2.5,                    // Standard SNR threshold
                "Standard X-gal blue/white screening"
            );
        }
        
        public static XGalDefaults sensitiveScreening() {
            return new XGalDefaults(
                -4.0,                   // More sensitive b* threshold
                -14.0,                  // UI slider: -4 - 10 = -14
                +6.0,                   // UI slider: -4 + 10 = +6
                6.0,                    // Lower Delta E76 for sensitivity
                2.0,                    // Lower SNR for weak signals
                "Sensitive X-gal screening for faint blue colonies"
            );
        }
        
        public static XGalDefaults stringentScreening() {
            return new XGalDefaults(
                -8.0,                   // More stringent b* threshold
                -18.0,                  // UI slider: -8 - 10 = -18
                +2.0,                   // UI slider: -8 + 10 = +2
                10.0,                   // Higher Delta E76 for specificity
                3.0,                    // Higher SNR for clear signals
                "Stringent X-gal screening for strong blue colonies only"
            );
        }
    }
    
    /**
     * Complete workflow configuration combining all parameters
     */
    public static record WorkflowDefaults(
        StandardPlateDimensions plateDims,
        ColonySizeDefaults colonySizes,
        DoGDetectionDefaults dogParams,
        PhansalkarDefaults phansalkarParams,
        XGalDefaults xgalParams,
        String workflowName,
        String description
    ) {
        /**
         * Standard phone photography workflow
         */
        public static WorkflowDefaults standardPhoneWorkflow() {
            return new WorkflowDefaults(
                StandardPlateDimensions.standard90mm(),
                ColonySizeDefaults.phonePhoto90mm(),
                DoGDetectionDefaults.standardColonies(),
                PhansalkarDefaults.standardColonies(),
                XGalDefaults.standardScreening(),
                "Standard Phone Photography",
                "Optimized for typical smartphone photography of 90mm X-gal plates"
            );
        }
        
        /**
         * High quality camera workflow
         */
        public static WorkflowDefaults highQualityWorkflow() {
            return new WorkflowDefaults(
                StandardPlateDimensions.aggressive90mm(),  // Less rim exclusion
                ColonySizeDefaults.highRes90mm(),
                DoGDetectionDefaults.sensitiveColonies(),  // More sensitive detection
                PhansalkarDefaults.fineDetail(),
                XGalDefaults.sensitiveScreening(),
                "High Quality Camera",
                "For DSLR or high-resolution smartphone photography"
            );
        }
        
        /**
         * Robust workflow for challenging conditions
         */
        public static WorkflowDefaults robustWorkflow() {
            return new WorkflowDefaults(
                StandardPlateDimensions.conservative90mm(), // More rim exclusion
                ColonySizeDefaults.lowRes90mm(),
                DoGDetectionDefaults.robustColonies(),      // Less sensitive detection
                PhansalkarDefaults.coarseDetail(),
                XGalDefaults.stringentScreening(),
                "Robust Detection",
                "For poor lighting, low resolution, or noisy images"
            );
        }
    }
    
    /**
     * UI configuration for parameter adjustment
     */
    public static record UIParameterConfig(
        String parameterName,
        double defaultValue,
        double minValue,
        double maxValue,
        double stepSize,
        String units,
        String tooltip
    ) {
        // Rim exclusion slider
        public static UIParameterConfig rimExclusion() {
            return new UIParameterConfig(
                "Rim Exclusion", 5.0, 2.0, 10.0, 0.5, "mm",
                "Exclude plate rim to avoid edge artifacts (4-6mm typical)"
            );
        }
        
        // Colony size sliders
        public static UIParameterConfig minColonySize() {
            return new UIParameterConfig(
                "Min Colony Size", 0.2, 0.1, 1.0, 0.1, "mm",
                "Minimum colony diameter to detect"
            );
        }
        
        public static UIParameterConfig maxColonySize() {
            return new UIParameterConfig(
                "Max Colony Size", 2.5, 1.0, 5.0, 0.1, "mm", 
                "Maximum colony diameter to detect"
            );
        }
        
        // X-gal threshold slider (main one mentioned in spec)
        public static UIParameterConfig xgalThreshold() {
            return new UIParameterConfig(
                "X-gal Threshold (b* delta)", -6.0, -16.0, +4.0, 0.5, "Lab units",
                "Blue colony threshold: more negative = more selective"
            );
        }
        
        // DoG sensitivity
        public static UIParameterConfig dogSensitivity() {
            return new UIParameterConfig(
                "Detection Sensitivity", 0.01, 0.001, 0.1, 0.001, "threshold",
                "DoG blob detection sensitivity: lower = more sensitive"
            );
        }
        
        // Phansalkar radius
        public static UIParameterConfig phansalkarRadius() {
            return new UIParameterConfig(
                "Local Threshold Radius", 20, 10, 40, 2, "pixels",
                "Local thresholding window size (15-25px typical)"
            );
        }
    }
    
    /**
     * Auto-calibration helpers for different imaging setups
     */
    public static class CalibrationHelper {
        
        /**
         * Estimate pixels per mm from detected plate size
         */
        public static double estimatePixelsPerMm(double detectedPlateDiameterPx, double actualPlateDiameterMm) {
            return detectedPlateDiameterPx / actualPlateDiameterMm;
        }
        
        /**
         * Recommend colony size pixels based on estimated resolution
         */
        public static ColonySizeDefaults recommendColonySizes(double pixelsPerMm) {
            if (pixelsPerMm > 25) {
                return ColonySizeDefaults.highRes90mm();
            } else if (pixelsPerMm < 15) {
                return ColonySizeDefaults.lowRes90mm();
            } else {
                return ColonySizeDefaults.phonePhoto90mm();
            }
        }
        
        /**
         * Recommend DoG radii based on estimated resolution
         */
        public static DoGDetectionDefaults recommendDoGParams(double pixelsPerMm) {
            if (pixelsPerMm > 25) {
                return DoGDetectionDefaults.sensitiveColonies();
            } else if (pixelsPerMm < 15) {
                return DoGDetectionDefaults.robustColonies();
            } else {
                return DoGDetectionDefaults.standardColonies();
            }
        }
        
        /**
         * Scale DoG radii for actual image resolution
         */
        public static double[] scaleDoGRadii(double[] baseRadii, double pixelsPerMm, double targetPixelsPerMm) {
            double scaleFactor = pixelsPerMm / targetPixelsPerMm;
            double[] scaledRadii = new double[baseRadii.length];
            for (int i = 0; i < baseRadii.length; i++) {
                scaledRadii[i] = baseRadii[i] * scaleFactor;
            }
            return scaledRadii;
        }
    }
    
    /**
     * Export defaults for different analysis tools
     */
    public static class ToolDefaults {
        
        /**
         * Default parameters for PlateDetector
         */
        public static Object getPlateDetectorDefaults() {
            var dims = StandardPlateDimensions.standard90mm();
            return new Object() {
                public final double expectedDiameterMm = dims.dishDiameterMm;
                public final String thresholdMethod = "Triangle";
                public final boolean correctIllumination = true;
            };
        }
        
        /**
         * Default parameters for ColonyDetector
         */
        public static Object getColonyDetectorDefaults(double pixelsPerMm) {
            var sizes = CalibrationHelper.recommendColonySizes(pixelsPerMm);
            return new Object() {
                public final int minDiamPx = sizes.minDiameterPx;
                public final int maxDiamPx = sizes.maxDiameterPx;
                public final boolean splitTouching = true;
            };
        }
        
        /**
         * Default parameters for ColonyClassifier X-gal mode
         */
        public static Object getXGalClassifierDefaults() {
            var xgal = XGalDefaults.standardScreening();
            return new Object() {
                public final double bDeltaThreshold = xgal.bDeltaThreshold;
                public final double dE76Threshold = xgal.dE76Threshold;
                public final double snrLThreshold = xgal.snrLThreshold;
                public final String mode = "xgal";
            };
        }
        
        /**
         * Default parameters for Binner
         */
        public static Object getBinnerDefaults() {
            return new Object() {
                public final double[] sizeEdgesMm = {1.0, 2.5}; // small, medium, large
            };
        }
        
        /**
         * Default parameters for ColonyNormalizer
         */
        public static Object getNormalizerDefaults() {
            return new Object() {
                public final boolean enableQuadrants = true;
            };
        }
    }
    
    /**
     * Demonstrate default values and ranges
     */
    public static void demonstrateDefaults() {
        System.out.println("=== Plate Analysis Defaults for Phone Photography ===");
        System.out.println();
        
        var workflow = WorkflowDefaults.standardPhoneWorkflow();
        
        System.out.println("📱 STANDARD PHONE WORKFLOW:");
        System.out.printf("  Dish: %.0fmm diameter, %.1fmm rim exclusion%n", 
                         workflow.plateDims.dishDiameterMm, workflow.plateDims.rimExclusionMm);
        System.out.printf("  Colonies: %.1f-%.1fmm diameter (~%d-%dpx)%n",
                         workflow.colonySizes.minDiameterMm, workflow.colonySizes.maxDiameterMm,
                         workflow.colonySizes.minDiameterPx, workflow.colonySizes.maxDiameterPx);
        System.out.printf("  X-gal threshold: %.1f (slider: %.1f to %.1f)%n",
                         workflow.xgalParams.bDeltaThreshold,
                         workflow.xgalParams.bDeltaMin, workflow.xgalParams.bDeltaMax);
        
        System.out.print("  DoG radii: ");
        for (double r : workflow.dogParams.radiiPx) {
            System.out.printf("%.0f ", r);
        }
        System.out.printf("px%n");
        
        System.out.printf("  Phansalkar radius: %dpx (range: 15-25px)%n",
                         workflow.phansalkarParams.radiusPx);
        System.out.println();
        
        System.out.println("🎛️  UI PARAMETER RANGES:");
        var rimParam = UIParameterConfig.rimExclusion();
        System.out.printf("  %s: %.1f%s (%.1f-%.1f, step %.1f)%n",
                         rimParam.parameterName, rimParam.defaultValue, rimParam.units,
                         rimParam.minValue, rimParam.maxValue, rimParam.stepSize);
        
        var xgalParam = UIParameterConfig.xgalThreshold();
        System.out.printf("  %s: %.1f%s (%.1f to %.1f, step %.1f)%n",
                         xgalParam.parameterName, xgalParam.defaultValue, xgalParam.units,
                         xgalParam.minValue, xgalParam.maxValue, xgalParam.stepSize);
        
        System.out.println();
        System.out.println("⚙️  AUTO-CALIBRATION:");
        System.out.println("  Detect plate → estimate pixels/mm → recommend parameters");
        System.out.println("  Example: 900px plate diameter / 90mm = 10px/mm → phone defaults");
        System.out.println("  Example: 1350px plate diameter / 90mm = 15px/mm → low-res defaults");
    }
}