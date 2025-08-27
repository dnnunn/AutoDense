package com.betterdairy.autodense.session;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Centralized and standardized session storage key management.
 * 
 * Design principles:
 * 1. Consistent naming convention: {analysis_type}#{image_handle}
 * 2. Support for data migration from legacy key formats
 * 3. Clear separation between different analysis types
 * 4. Validation to prevent key collisions and ensure uniqueness
 * 5. Backward compatibility with existing sessions
 * 
 * Standard key format: "{analysis_type}#{image_handle}"
 * Legacy formats supported with automatic migration
 */
public final class SessionAnalysisKeys {
    
    private SessionAnalysisKeys() {} // Utility class
    
    // Key separator for new standardized format
    private static final String KEY_SEPARATOR = "#";
    
    /**
     * Standardized analysis types
     */
    public enum AnalysisType {
        // Plate detection and calibration
        PLATE_DETECTION("plate"),
        
        // Colony analysis workflow
        COLONY_DETECTION("colonies"),
        COLONY_CLASSIFICATION("colony_classifications"), 
        COLONY_NORMALIZATION("colony_normalization"),
        COLONY_BINNING("colony_binning"),
        COLONY_ASSIST("colony_assist"),
        
        // Gel analysis workflow  
        LANE_DETECTION("lanes"),
        BAND_DETECTION("bands"),
        BAND_QUANTIFICATION("quantification"),
        MW_CALIBRATION("mw_calibration"),
        LANE_COMPARISON("lane_comparison"),
        
        // Other analysis types
        CONTAMINATION("contamination"),
        NORMALIZATION("normalization"),
        STANDARD_CURVE("standard_curve");
        
        private final String keyPrefix;
        
        AnalysisType(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
        
        public String getKeyPrefix() {
            return keyPrefix;
        }
    }
    
    /**
     * Generate standardized storage key
     */
    public static String createKey(AnalysisType analysisType, String imageHandle) {
        validateImageHandle(imageHandle);
        return analysisType.getKeyPrefix() + KEY_SEPARATOR + imageHandle;
    }
    
    /**
     * Parse analysis type from storage key
     */
    public static AnalysisType parseAnalysisType(String storageKey) {
        if (storageKey.contains(KEY_SEPARATOR)) {
            // New standardized format
            String prefix = storageKey.split(Pattern.quote(KEY_SEPARATOR))[0];
            for (AnalysisType type : AnalysisType.values()) {
                if (type.getKeyPrefix().equals(prefix)) {
                    return type;
                }
            }
        }
        
        // Legacy format detection
        return detectLegacyAnalysisType(storageKey);
    }
    
    /**
     * Extract image handle from storage key
     */
    public static String parseImageHandle(String storageKey) {
        if (storageKey.contains(KEY_SEPARATOR)) {
            // New standardized format
            String[] parts = storageKey.split(Pattern.quote(KEY_SEPARATOR), 2);
            return parts.length > 1 ? parts[1] : null;
        }
        
        // Legacy format detection
        return extractLegacyImageHandle(storageKey);
    }
    
    /**
     * Check if storage key is in standardized format
     */
    public static boolean isStandardizedKey(String storageKey) {
        return storageKey.contains(KEY_SEPARATOR) && parseAnalysisType(storageKey) != null;
    }
    
    /**
     * Migrate legacy storage key to standardized format
     */
    public static String migrateKey(String legacyKey, String imageHandle) {
        AnalysisType analysisType = detectLegacyAnalysisType(legacyKey);
        if (analysisType != null) {
            return createKey(analysisType, imageHandle);
        }
        
        // If can't detect type, preserve original but add image handle
        return legacyKey + KEY_SEPARATOR + imageHandle;
    }
    
    /**
     * Colony analysis specific key generators
     */
    public static class ColonyKeys {
        
        public static String detection(String imageHandle) {
            return createKey(AnalysisType.COLONY_DETECTION, imageHandle);
        }
        
        public static String classification(String imageHandle) {
            return createKey(AnalysisType.COLONY_CLASSIFICATION, imageHandle);
        }
        
        public static String normalization(String imageHandle) {
            return createKey(AnalysisType.COLONY_NORMALIZATION, imageHandle);
        }
        
        public static String binning(String imageHandle) {
            return createKey(AnalysisType.COLONY_BINNING, imageHandle);
        }
        
        public static String assist(String imageHandle) {
            return createKey(AnalysisType.COLONY_ASSIST, imageHandle);
        }
    }
    
    /**
     * Plate analysis specific key generators
     */
    public static class PlateKeys {
        
        public static String detection(String imageHandle) {
            return createKey(AnalysisType.PLATE_DETECTION, imageHandle);
        }
    }
    
    /**
     * Gel analysis specific key generators
     */
    public static class GelKeys {
        
        public static String lanes(String imageHandle) {
            return createKey(AnalysisType.LANE_DETECTION, imageHandle);
        }
        
        public static String bands(String imageHandle) {
            return createKey(AnalysisType.BAND_DETECTION, imageHandle);
        }
        
        public static String quantification(String imageHandle) {
            return createKey(AnalysisType.BAND_QUANTIFICATION, imageHandle);
        }
        
        public static String molecularWeight(String imageHandle) {
            return createKey(AnalysisType.MW_CALIBRATION, imageHandle);
        }
        
        public static String laneComparison(String imageHandle) {
            return createKey(AnalysisType.LANE_COMPARISON, imageHandle);
        }
    }
    
    /**
     * Legacy key patterns and migration support
     */
    public static class LegacyMigration {
        
        /**
         * Map of legacy key patterns to standardized analysis types
         */
        private static final LegacyPattern[] LEGACY_PATTERNS = {
            // Colony analysis legacy patterns
            new LegacyPattern("colonies_.*", AnalysisType.COLONY_DETECTION),
            new LegacyPattern("colonies", AnalysisType.COLONY_DETECTION),
            new LegacyPattern("colony_detection", AnalysisType.COLONY_DETECTION),
            new LegacyPattern("colony_classifications", AnalysisType.COLONY_CLASSIFICATION),
            new LegacyPattern("colony_bins", AnalysisType.COLONY_BINNING),
            new LegacyPattern("normalized_.*", AnalysisType.COLONY_NORMALIZATION),
            new LegacyPattern("colony_assist_.*", AnalysisType.COLONY_ASSIST),
            
            // Plate analysis legacy patterns
            new LegacyPattern("plate", AnalysisType.PLATE_DETECTION),
            new LegacyPattern("plate_.*", AnalysisType.PLATE_DETECTION),
            
            // Gel analysis legacy patterns (these are already consistent)
            new LegacyPattern("lanes", AnalysisType.LANE_DETECTION),
            new LegacyPattern("bands", AnalysisType.BAND_DETECTION),
            new LegacyPattern("quantification", AnalysisType.BAND_QUANTIFICATION),
            new LegacyPattern("mw_calibration", AnalysisType.MW_CALIBRATION),
            new LegacyPattern("lane_comparison", AnalysisType.LANE_COMPARISON)
        };
        
        private static class LegacyPattern {
            final String pattern;
            final AnalysisType analysisType;
            
            LegacyPattern(String pattern, AnalysisType analysisType) {
                this.pattern = pattern;
                this.analysisType = analysisType;
            }
        }
        
        /**
         * Find all keys that need migration in a session
         */
        public static List<String> findKeysNeedingMigration(List<String> allKeys) {
            List<String> keysToMigrate = new ArrayList<>();
            
            for (String key : allKeys) {
                if (!isStandardizedKey(key)) {
                    keysToMigrate.add(key);
                }
            }
            
            return keysToMigrate;
        }
        
        /**
         * Generate migration plan for a session
         */
        public static List<MigrationOperation> createMigrationPlan(List<String> legacyKeys, String imageHandle) {
            List<MigrationOperation> operations = new ArrayList<>();
            
            for (String legacyKey : legacyKeys) {
                String standardizedKey = migrateKey(legacyKey, imageHandle);
                if (!legacyKey.equals(standardizedKey)) {
                    operations.add(new MigrationOperation(legacyKey, standardizedKey));
                }
            }
            
            return operations;
        }
        
        /**
         * Migration operation definition
         */
        public static record MigrationOperation(
            String fromKey,
            String toKey
        ) {}
    }
    
    // Private helper methods
    
    private static void validateImageHandle(String imageHandle) {
        if (imageHandle == null || imageHandle.trim().isEmpty()) {
            throw new IllegalArgumentException("Image handle cannot be null or empty");
        }
        if (imageHandle.contains(KEY_SEPARATOR)) {
            throw new IllegalArgumentException("Image handle cannot contain separator: " + KEY_SEPARATOR);
        }
    }
    
    private static AnalysisType detectLegacyAnalysisType(String legacyKey) {
        for (LegacyMigration.LegacyPattern pattern : LegacyMigration.LEGACY_PATTERNS) {
            if (legacyKey.matches(pattern.pattern)) {
                return pattern.analysisType;
            }
        }
        return null; // Unknown legacy format
    }
    
    private static String extractLegacyImageHandle(String legacyKey) {
        // Try to extract image handle from legacy patterns
        if (legacyKey.contains("_img_")) {
            return legacyKey.substring(legacyKey.indexOf("_img_") + 1);
        }
        
        if (legacyKey.matches(".*_img[0-9]+.*")) {
            // Extract patterns like "colonies_img123"
            int underscoreIndex = legacyKey.lastIndexOf('_');
            if (underscoreIndex > 0) {
                return legacyKey.substring(underscoreIndex + 1);
            }
        }
        
        return null; // Can't extract image handle from legacy format
    }
    
    /**
     * Storage key validation utilities
     */
    public static class Validation {
        
        /**
         * Validate storage key format and uniqueness
         */
        public static ValidationResult validateKey(String storageKey, List<String> existingKeys) {
            // Check basic format
            if (storageKey == null || storageKey.trim().isEmpty()) {
                return ValidationResult.invalid("Storage key cannot be null or empty");
            }
            
            // Check for standardized format
            if (!isStandardizedKey(storageKey)) {
                return ValidationResult.warning("Key is not in standardized format: " + storageKey);
            }
            
            // Check for duplicates
            if (existingKeys.contains(storageKey)) {
                return ValidationResult.invalid("Duplicate storage key: " + storageKey);
            }
            
            // Check for analysis type validity
            AnalysisType analysisType = parseAnalysisType(storageKey);
            if (analysisType == null) {
                return ValidationResult.invalid("Unknown analysis type in key: " + storageKey);
            }
            
            return ValidationResult.valid();
        }
        
        public static class ValidationResult {
            public final boolean isValid;
            public final boolean isWarning;
            public final String message;
            
            private ValidationResult(boolean isValid, boolean isWarning, String message) {
                this.isValid = isValid;
                this.isWarning = isWarning;
                this.message = message;
            }
            
            public static ValidationResult valid() {
                return new ValidationResult(true, false, null);
            }
            
            public static ValidationResult invalid(String message) {
                return new ValidationResult(false, false, message);
            }
            
            public static ValidationResult warning(String message) {
                return new ValidationResult(true, true, message);
            }
        }
    }
}