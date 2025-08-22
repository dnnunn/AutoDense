package com.betterdairy.autodense.analysis;

/**
 * Comprehensive parameter set for colony analysis with practical defaults and edge-case handling.
 * Covers phone photography workflows with robust guardrails.
 */
public final class ColonyAnalysisParams {
    
    /**
     * X-gal color classification parameters
     */
    public static final class ColorParams {
        // Core thresholds (user adjustable ±10)
        public double bDeltaPos = -6.0;     // Binary positive threshold
        public double bDeltaMed = -10.0;    // Medium/light boundary
        public double bDeltaDark = -16.0;   // Dark/medium boundary
        public double minDE = 8.0;          // Minimum color difference
        public double minSNRL = 2.5;        // Minimum signal-to-noise ratio
        
        // Auto-calibration
        public boolean autoCalibrate = true;
        
        // UI slider bounds (±10 from defaults)
        public double bDeltaMin = -26.0;    // Slider minimum (bDeltaDark - 10)
        public double bDeltaMax = 4.0;      // Slider maximum (bDeltaPos + 10)
        
        public static ColorParams defaults() {
            return new ColorParams();
        }
        
        public static ColorParams conservative() {
            ColorParams p = new ColorParams();
            p.bDeltaPos = -8.0;   // More stringent
            p.bDeltaMed = -12.0;
            p.bDeltaDark = -18.0;
            return p;
        }
        
        public static ColorParams sensitive() {
            ColorParams p = new ColorParams();
            p.bDeltaPos = -4.0;   // More permissive
            p.bDeltaMed = -8.0;
            p.bDeltaDark = -14.0;
            return p;
        }
    }
    
    /**
     * Size binning parameters with preset options
     */
    public static final class SizeParams {
        public double[] edgesMM = {0.2, 1.0, 2.0};  // Default: tiny/small/medium/large
        
        public static SizeParams microcolonies() {
            SizeParams p = new SizeParams();
            p.edgesMM = new double[]{0.1, 0.5, 1.0};  // For very small colonies
            return p;
        }
        
        public static SizeParams standard() {
            SizeParams p = new SizeParams();
            p.edgesMM = new double[]{0.2, 1.0, 2.0};  // Standard phone photography
            return p;
        }
        
        public static SizeParams large() {
            SizeParams p = new SizeParams();
            p.edgesMM = new double[]{0.5, 1.5, 3.0};  // For larger colonies
            return p;
        }
    }
    
    /**
     * Detection parameters with edge-case handling
     */
    public static final class DetectionParams {
        // Plate detection
        public double rimExclusionMM = 5.0;         // Ignore outer rim (4-6mm typical)
        
        // Colony size limits
        public double minDiameterMM = 0.2;          // Minimum detectable colony
        public double maxDiameterMM = 5.0;          // Maximum reasonable colony
        
        // Detection quality controls
        public boolean splitTouchingColonies = true;  // Enable watershed
        public double minCircularity = 0.5;         // For crowded plates (>=0.5)
        public double minSolidity = 0.7;            // Reject fragmented objects
        
        // Edge artifact handling
        public boolean ignoreRimArtifacts = true;   // Skip rim band
        public double rimArtifactMM = 5.0;          // Width of rim exclusion
        
        public static DetectionParams phonePhoto() {
            return new DetectionParams(); // Use defaults
        }
        
        public static DetectionParams crowdedPlate() {
            DetectionParams p = new DetectionParams();
            p.splitTouchingColonies = true;
            p.minCircularity = 0.6;     // Stricter for crowded conditions
            p.minSolidity = 0.8;
            return p;
        }
        
        public static DetectionParams permissive() {
            DetectionParams p = new DetectionParams();
            p.minCircularity = 0.3;     // Allow irregular shapes
            p.minSolidity = 0.5;
            p.splitTouchingColonies = false;
            return p;
        }
    }
    
    /**
     * Image preprocessing parameters for challenging conditions
     */
    public static final class PreprocessingParams {
        // Uneven lighting correction
        public boolean flattenBackground = true;    // Background divide before color measurement
        public double gaussianSigma = 50.0;         // Smoothing for background estimation
        
        // Color cast robustness
        public boolean useLocalBackground = true;   // Δb* vs local annulus (robust to global cast)
        public double annulusInnerRadius = 1.2;     // Inner radius multiplier
        public double annulusOuterRadius = 1.8;     // Outer radius multiplier
        
        // Saturation handling
        public boolean detectSaturation = true;     // Check for overexposed regions
        public double saturationThreshold = 250.0;  // RGB channel saturation limit
        
        public static PreprocessingParams standard() {
            return new PreprocessingParams(); // Use defaults
        }
        
        public static PreprocessingParams challenging() {
            PreprocessingParams p = new PreprocessingParams();
            p.flattenBackground = true;
            p.gaussianSigma = 75.0;      // Stronger smoothing
            p.useLocalBackground = true;
            return p;
        }
        
        public static PreprocessingParams minimal() {
            PreprocessingParams p = new PreprocessingParams();
            p.flattenBackground = false;
            p.detectSaturation = false;
            return p;
        }
    }
    
    /**
     * Complete analysis configuration
     */
    public static final class AnalysisConfig {
        public ColorParams colorParams;
        public SizeParams sizeParams;
        public DetectionParams detectionParams;
        public PreprocessingParams preprocessingParams;
        
        public AnalysisConfig() {
            this.colorParams = ColorParams.defaults();
            this.sizeParams = SizeParams.standard();
            this.detectionParams = DetectionParams.phonePhoto();
            this.preprocessingParams = PreprocessingParams.standard();
        }
        
        // Preset workflows for common scenarios
        public static AnalysisConfig standardPhoneWorkflow() {
            AnalysisConfig config = new AnalysisConfig();
            config.colorParams = ColorParams.defaults();
            config.sizeParams = SizeParams.standard();
            config.detectionParams = DetectionParams.phonePhoto();
            config.preprocessingParams = PreprocessingParams.standard();
            return config;
        }
        
        public static AnalysisConfig crowdedPlateWorkflow() {
            AnalysisConfig config = new AnalysisConfig();
            config.colorParams = ColorParams.conservative(); // More stringent
            config.sizeParams = SizeParams.microcolonies();  // Smaller size bins
            config.detectionParams = DetectionParams.crowdedPlate();
            config.preprocessingParams = PreprocessingParams.challenging();
            return config;
        }
        
        public static AnalysisConfig challengingConditionsWorkflow() {
            AnalysisConfig config = new AnalysisConfig();
            config.colorParams = ColorParams.sensitive();    // More permissive
            config.sizeParams = SizeParams.large();          // Larger size bins
            config.detectionParams = DetectionParams.permissive();
            config.preprocessingParams = PreprocessingParams.challenging();
            return config;
        }
        
        public static AnalysisConfig highQualityWorkflow() {
            AnalysisConfig config = new AnalysisConfig();
            config.colorParams = ColorParams.defaults();
            config.sizeParams = SizeParams.standard();
            config.detectionParams = DetectionParams.phonePhoto();
            config.preprocessingParams = PreprocessingParams.minimal(); // Less processing needed
            return config;
        }
    }
    
    /**
     * Validation methods for parameter ranges
     */
    public static final class Validation {
        
        public static boolean validateColorParams(ColorParams params) {
            // Ensure proper ordering: dark < medium < positive
            if (!(params.bDeltaDark < params.bDeltaMed && params.bDeltaMed < params.bDeltaPos)) {
                return false;
            }
            
            // Check reasonable ranges
            if (params.bDeltaPos > 0 || params.bDeltaDark < -30.0) {
                return false;
            }
            
            if (params.minDE < 0 || params.minDE > 50.0) {
                return false;
            }
            
            if (params.minSNRL < 0 || params.minSNRL > 10.0) {
                return false;
            }
            
            return true;
        }
        
        public static boolean validateSizeParams(SizeParams params) {
            // Check edges are sorted and positive
            for (int i = 0; i < params.edgesMM.length; i++) {
                if (params.edgesMM[i] <= 0 || params.edgesMM[i] > 10.0) {
                    return false;
                }
                if (i > 0 && params.edgesMM[i] <= params.edgesMM[i-1]) {
                    return false;
                }
            }
            return true;
        }
        
        public static boolean validateDetectionParams(DetectionParams params) {
            // Size limits
            if (params.minDiameterMM <= 0 || params.maxDiameterMM <= params.minDiameterMM) {
                return false;
            }
            
            // Quality thresholds
            if (params.minCircularity < 0 || params.minCircularity > 1.0) {
                return false;
            }
            
            if (params.minSolidity < 0 || params.minSolidity > 1.0) {
                return false;
            }
            
            // Rim exclusion
            if (params.rimExclusionMM < 0 || params.rimExclusionMM > 20.0) {
                return false;
            }
            
            return true;
        }
    }
    
    /**
     * Helper methods for UI integration
     */
    public static final class UIHelpers {
        
        /**
         * Get slider configuration for b-delta parameters
         */
        public static SliderConfig getBDeltaSliderConfig(String paramName, double defaultValue) {
            return new SliderConfig(
                paramName,
                defaultValue,
                defaultValue - 10.0,  // ±10 range
                defaultValue + 10.0,
                0.5,                  // 0.5 step size
                "Lab units",
                "Adjust " + paramName + " threshold (more negative = more selective)"
            );
        }
        
        /**
         * Get preset button configurations for size edges
         */
        public static PresetButton[] getSizePresetButtons() {
            return new PresetButton[]{
                new PresetButton("Microcolonies", "0.1, 0.5, 1.0", SizeParams.microcolonies().edgesMM),
                new PresetButton("Standard", "0.2, 1.0, 2.0", SizeParams.standard().edgesMM),
                new PresetButton("Large", "0.5, 1.5, 3.0", SizeParams.large().edgesMM)
            };
        }
        
        /**
         * Get workflow preset configurations
         */
        public static WorkflowPreset[] getWorkflowPresets() {
            return new WorkflowPreset[]{
                new WorkflowPreset("Standard Phone", "Typical smartphone photography", 
                    AnalysisConfig.standardPhoneWorkflow()),
                new WorkflowPreset("Crowded Plate", "Many small/touching colonies", 
                    AnalysisConfig.crowdedPlateWorkflow()),
                new WorkflowPreset("Challenging", "Poor lighting/uneven conditions", 
                    AnalysisConfig.challengingConditionsWorkflow()),
                new WorkflowPreset("High Quality", "DSLR or controlled lighting", 
                    AnalysisConfig.highQualityWorkflow())
            };
        }
    }
    
    // Helper classes for UI configuration
    public static record SliderConfig(
        String name, double defaultValue, double minValue, double maxValue, 
        double stepSize, String units, String tooltip
    ) {}
    
    public static record PresetButton(
        String name, String description, double[] edgesMM
    ) {}
    
    public static record WorkflowPreset(
        String name, String description, AnalysisConfig config
    ) {}
}