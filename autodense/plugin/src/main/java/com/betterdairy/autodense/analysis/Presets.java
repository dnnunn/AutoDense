package com.betterdairy.autodense.analysis;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Named, deterministic multi-action preprocessing sequences.
 * Based on proven laboratory protocols for different gel types.
 */
public final class Presets {
    
    private Presets() {}
    
    /**
     * SDS-PAGE (Coomassie or similar) auto-prep sequence
     * Conservative, safe defaults for protein gels
     */
    public static JSONArray sdsAutoPrep() {
        return new JSONArray()
            // Bandpass filter to suppress speckle and gradients
            .put(new JSONObject()
                .put("op", "bandpass")
                .put("filter_large", 200)
                .put("filter_small", 2))
            // Rolling ball background subtraction
            .put(new JSONObject()
                .put("op", "background")
                .put("method", "rolling_ball")
                .put("radius_px", 120));
    }
    
    /**
     * Agarose DNA gel (Ethidium Bromide or SYBR Safe) auto-prep
     * Boost faint bands safely without creating artifacts
     */
    public static JSONArray dnaEtbrAutoPrep() {
        return new JSONArray()
            // CLAHE to lift faint DNA bands
            .put(new JSONObject()
                .put("op", "clahe")
                .put("blocksize", 127)
                .put("histogram", 256)
                .put("maximum", 3.0))
            // Bandpass filter  
            .put(new JSONObject()
                .put("op", "bandpass")
                .put("filter_large", 200)
                .put("filter_small", 2))
            // Larger rolling ball for UV illumination gradients
            .put(new JSONObject()
                .put("op", "background")
                .put("method", "rolling_ball")
                .put("radius_px", 200));
    }
    
    /**
     * SDS-PAGE with very faint bands - stronger preprocessing
     * More aggressive contrast and larger background radius
     */
    public static JSONArray sdsAutoPrepStrong() {
        return new JSONArray()
            // Gentle bandpass first to avoid amplifying noise with CLAHE
            .put(new JSONObject()
                .put("op", "bandpass")
                .put("filter_small", 2)
                .put("filter_large", 200)
)
            // CLAHE to lift very faint bands (stronger than default)
            .put(new JSONObject()
                .put("op", "clahe")
                .put("blocksize", 127)
                .put("histogram", 256)
                .put("maximum", 3.5))
            // Larger rolling ball for broad background haze
            .put(new JSONObject()
                .put("op", "background")
                .put("method", "rolling_ball")
                .put("radius_px", 160)
)
;
    }
    
    /**
     * DNA gel with weak dye - gentle approach
     * Milder CLAHE to avoid halos, heavier background removal
     */
    public static JSONArray dnaLowDye() {
        return new JSONArray()
            // Milder CLAHE to avoid ringing/halos
            .put(new JSONObject()
                .put("op", "clahe")
                .put("blocksize", 127)
                .put("histogram", 256)
                .put("maximum", 2.0))
            // Bandpass filter
            .put(new JSONObject()
                .put("op", "bandpass")
                .put("filter_small", 2)
                .put("filter_large", 200)
)
            // Heavy background removal for UV falloff
            .put(new JSONObject()
                .put("op", "background")
                .put("method", "rolling_ball")
                .put("radius_px", 240)
)
;
    }
    
    /**
     * Basic cleanup sequence for noisy images
     * Remove artifacts before analysis
     */
    public static JSONArray noiseCleanup() {
        return new JSONArray()
            // Remove salt-and-pepper noise
            .put(new JSONObject()
                .put("op", "bandpass")
                .put("filter_small", 2)
                .put("filter_large", 200)
)
            // Remove hot pixels
            .put(new JSONObject()
                .put("op", "background")
                .put("method", "median")
                .put("radius_px", 1));
    }
    
    /**
     * Orientation correction sequence
     * Standard gel orientation with wells at top
     */
    public static JSONArray orientationCorrection(double rotationAngle) {
        JSONArray sequence = new JSONArray();
        
        // Rotate to horizontal if needed
        if (Math.abs(rotationAngle) > 0.1) {
            sequence.put(new JSONObject()
                .put("op", "rotate")
                .put("angle_deg", rotationAngle));
        }
        
        // Could add flip if wells detected at bottom
        // sequence.put(new JSONObject()
        //     .put("op", "flip")
        //     .put("axis", "vertical"));
        
        return sequence;
    }
    
    /**
     * Expand a named preset into its action array
     */
    public static JSONArray expand(String presetName) {
        return switch (presetName) {
            case "sds_autoprep" -> sdsAutoPrep();
            case "dna_etbr_autoprep" -> dnaEtbrAutoPrep(); 
            case "sds_autoprep_strong" -> sdsAutoPrepStrong();
            case "dna_low_dye" -> dnaLowDye();
            case "noise_cleanup" -> noiseCleanup();
            default -> throw new IllegalArgumentException("Unknown preset: " + presetName);
        };
    }
    
    /**
     * Get all available preset names
     */
    public static String[] getAvailablePresets() {
        return new String[]{
            "sds_autoprep",
            "dna_etbr_autoprep", 
            "sds_autoprep_strong",
            "dna_low_dye",
            "noise_cleanup"
        };
    }
    
    /**
     * Get description of a preset
     */
    public static String getDescription(String presetName) {
        return switch (presetName) {
            case "sds_autoprep" -> 
                "Standard SDS-PAGE preprocessing: bandpass filter, rolling ball (120px), saturation QC";
            case "dna_etbr_autoprep" -> 
                "DNA gel with EtBr/SYBR: CLAHE contrast, bandpass filter, rolling ball (200px), saturation QC";
            case "sds_autoprep_strong" -> 
                "Strong SDS-PAGE for faint bands: bandpass, enhanced CLAHE, large rolling ball (160px)";
            case "dna_low_dye" -> 
                "DNA gel with weak dye: gentle CLAHE, bandpass, heavy background removal (240px)";
            case "noise_cleanup" -> 
                "Basic noise reduction: bandpass filter, median filter for speckle removal";
            default -> "Unknown preset";
        };
    }
    
    /**
     * Get recommended parameters for gel type
     */
    public static class RecommendedSettings {
        
        public static JSONObject forGelType(String gelType) {
            return switch (gelType.toLowerCase()) {
                case "sds", "sds-page", "protein" -> new JSONObject()
                    .put("preset", "sds_autoprep")
                    .put("min_peak_distance_px", 10)
                    .put("background_region", "sides")
                    .put("background_method", "median");
                    
                case "dna", "agarose", "nucleic_acid" -> new JSONObject()
                    .put("preset", "dna_etbr_autoprep") 
                    .put("min_peak_distance_px", 8)
                    .put("background_region", "all")
                    .put("background_method", "median");
                    
                case "western", "immunoblot" -> new JSONObject()
                    .put("preset", "sds_autoprep_strong")
                    .put("min_peak_distance_px", 12)
                    .put("background_region", "sides")
                    .put("background_method", "median");
                    
                default -> new JSONObject()
                    .put("preset", "sds_autoprep")
                    .put("min_peak_distance_px", 10)
                    .put("background_region", "all")
                    .put("background_method", "median");
            };
        }
    }
}