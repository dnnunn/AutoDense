package com.betterdairy.autodense.session;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Handles migration of legacy session storage keys to standardized format.
 * Provides backward compatibility while encouraging adoption of new format.
 * 
 * Migration strategy:
 * 1. Detect legacy keys in session
 * 2. Create migration plan with new standardized keys
 * 3. Execute migration with data validation
 * 4. Maintain backward compatibility for reading
 */
public class SessionStorageMigrator {
    
    private final SessionStore store;
    
    public SessionStorageMigrator(SessionStore store) {
        this.store = store;
    }
    
    /**
     * Migration result with statistics and any issues
     */
    public static class MigrationResult {
        public final boolean successful;
        public final int keysProcessed;
        public final int keysMigrated;
        public final int keysSkipped;
        public final List<String> warnings;
        public final List<String> errors;
        
        public MigrationResult(boolean successful, int keysProcessed, int keysMigrated, 
                             int keysSkipped, List<String> warnings, List<String> errors) {
            this.successful = successful;
            this.keysProcessed = keysProcessed;
            this.keysMigrated = keysMigrated;
            this.keysSkipped = keysSkipped;
            this.warnings = warnings;
            this.errors = errors;
        }
        
        public static MigrationResult success(int processed, int migrated, int skipped, List<String> warnings) {
            return new MigrationResult(true, processed, migrated, skipped, warnings, new ArrayList<>());
        }
        
        public static MigrationResult failure(int processed, List<String> errors) {
            return new MigrationResult(false, processed, 0, 0, new ArrayList<>(), errors);
        }
        
        public String getSummary() {
            if (successful) {
                return String.format(
                    "Migration completed successfully. Processed: %d, Migrated: %d, Skipped: %d, Warnings: %d",
                    keysProcessed, keysMigrated, keysSkipped, warnings.size()
                );
            } else {
                return String.format(
                    "Migration failed. Processed: %d, Errors: %d",
                    keysProcessed, errors.size()
                );
            }
        }
    }
    
    /**
     * Migrate all legacy keys for a specific image to standardized format
     */
    public MigrationResult migrateImageAnalysis(String imageHandle) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // Get all analysis keys for this image
            List<String> analysisKeys = store.getAnalysesForImage(imageHandle);
            int keysProcessed = 0;
            int keysMigrated = 0;
            int keysSkipped = 0;
            
            for (String legacyKey : analysisKeys) {
                keysProcessed++;
                
                // Check if key is already standardized
                if (SessionAnalysisKeys.isStandardizedKey(legacyKey)) {
                    keysSkipped++;
                    continue;
                }
                
                // Attempt to migrate the key
                try {
                    String standardizedKey = SessionAnalysisKeys.migrateKey(legacyKey, imageHandle);
                    
                    if (!legacyKey.equals(standardizedKey)) {
                        // Perform the migration
                        SessionStore.AnalysisRecord record = store.getAnalysis(legacyKey);
                        if (record != null) {
                            // Store with new key
                            store.putAnalysis(standardizedKey, record.data, imageHandle);
                            
                            // Remove old key (if supported)
                            // store.removeAnalysis(legacyKey); // Uncomment when removeAnalysis is available
                            
                            keysMigrated++;
                            warnings.add("Migrated: " + legacyKey + " -> " + standardizedKey);
                        } else {
                            warnings.add("Could not retrieve data for key: " + legacyKey);
                            keysSkipped++;
                        }
                    } else {
                        warnings.add("No migration needed for key: " + legacyKey);
                        keysSkipped++;
                    }
                } catch (Exception e) {
                    errors.add("Failed to migrate key '" + legacyKey + "': " + e.getMessage());
                }
            }
            
            if (errors.isEmpty()) {
                return MigrationResult.success(keysProcessed, keysMigrated, keysSkipped, warnings);
            } else {
                return MigrationResult.failure(keysProcessed, errors);
            }
            
        } catch (Exception e) {
            errors.add("Migration failed with exception: " + e.getMessage());
            return MigrationResult.failure(0, errors);
        }
    }
    
    /**
     * Check if an image's analysis data needs migration
     */
    public boolean needsMigration(String imageHandle) {
        List<String> analysisKeys = store.getAnalysesForImage(imageHandle);
        
        for (String key : analysisKeys) {
            if (!SessionAnalysisKeys.isStandardizedKey(key)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get migration preview without executing changes
     */
    public MigrationPreview previewMigration(String imageHandle) {
        List<String> analysisKeys = store.getAnalysesForImage(imageHandle);
        List<SessionAnalysisKeys.LegacyMigration.MigrationOperation> operations = 
            SessionAnalysisKeys.LegacyMigration.createMigrationPlan(analysisKeys, imageHandle);
        
        Map<String, String> keyMapping = new HashMap<>();
        List<String> unmigratableKeys = new ArrayList<>();
        
        for (SessionAnalysisKeys.LegacyMigration.MigrationOperation op : operations) {
            if (!op.fromKey().equals(op.toKey())) {
                keyMapping.put(op.fromKey(), op.toKey());
            } else {
                unmigratableKeys.add(op.fromKey());
            }
        }
        
        return new MigrationPreview(keyMapping, unmigratableKeys);
    }
    
    /**
     * Preview of what migration would do
     */
    public static class MigrationPreview {
        public final Map<String, String> keyMapping;      // old -> new key mappings
        public final List<String> unmigratableKeys;       // keys that can't be migrated
        
        public MigrationPreview(Map<String, String> keyMapping, List<String> unmigratableKeys) {
            this.keyMapping = keyMapping;
            this.unmigratableKeys = unmigratableKeys;
        }
        
        public int getTotalChanges() {
            return keyMapping.size();
        }
        
        public boolean hasIssues() {
            return !unmigratableKeys.isEmpty();
        }
        
        public String getSummary() {
            return String.format(
                "Migration preview: %d keys to migrate, %d unmigatable keys",
                keyMapping.size(), unmigratableKeys.size()
            );
        }
    }
    
    /**
     * Enhanced session data retrieval with backward compatibility
     */
    public static class CompatibilityLayer {
        
        private final SessionStore store;
        
        public CompatibilityLayer(SessionStore store) {
            this.store = store;
        }
        
        /**
         * Get colony data with automatic migration support
         * Tries standardized key first, falls back to legacy patterns
         */
        public List<?> getColonyData(String imageHandle, SessionAnalysisKeys.AnalysisType analysisType) {
            // Try standardized key first
            String standardizedKey = SessionAnalysisKeys.createKey(analysisType, imageHandle);
            SessionStore.AnalysisRecord record = store.getAnalysis(standardizedKey);
            
            if (record != null && record.data instanceof List<?>) {
                return (List<?>) record.data;
            }
            
            // Fall back to legacy key patterns
            List<String> legacyPatterns = getLegacyPatterns(analysisType, imageHandle);
            for (String legacyKey : legacyPatterns) {
                SessionStore.AnalysisRecord legacyRecord = store.getAnalysis(legacyKey);
                if (legacyRecord != null && legacyRecord.data instanceof List<?>) {
                    return (List<?>) legacyRecord.data;
                }
            }
            
            return null; // No data found
        }
        
        /**
         * Get analysis data with automatic migration support
         */
        public Object getAnalysisData(String imageHandle, SessionAnalysisKeys.AnalysisType analysisType) {
            // Try standardized key first
            String standardizedKey = SessionAnalysisKeys.createKey(analysisType, imageHandle);
            SessionStore.AnalysisRecord record = store.getAnalysis(standardizedKey);
            
            if (record != null) {
                return record.data;
            }
            
            // Fall back to legacy key patterns
            List<String> legacyPatterns = getLegacyPatterns(analysisType, imageHandle);
            for (String legacyKey : legacyPatterns) {
                SessionStore.AnalysisRecord legacyRecord = store.getAnalysis(legacyKey);
                if (legacyRecord != null) {
                    return legacyRecord.data;
                }
            }
            
            return null; // No data found
        }
        
        /**
         * Generate legacy key patterns for backward compatibility
         */
        private List<String> getLegacyPatterns(SessionAnalysisKeys.AnalysisType analysisType, String imageHandle) {
            List<String> patterns = new ArrayList<>();
            
            switch (analysisType) {
                case COLONY_DETECTION:
                    patterns.add("colonies_" + imageHandle);
                    patterns.add("colonies");
                    patterns.add("colony_detection");
                    break;
                    
                case COLONY_CLASSIFICATION:
                    patterns.add("colony_classifications");
                    break;
                    
                case COLONY_NORMALIZATION:
                    patterns.add("normalized_" + imageHandle);
                    break;
                    
                case COLONY_ASSIST:
                    patterns.add("colony_assist_" + imageHandle);
                    break;
                    
                case PLATE_DETECTION:
                    patterns.add("plate");
                    patterns.add("plate_" + imageHandle);
                    break;
                    
                default:
                    // For other analysis types, try the prefix as-is
                    patterns.add(analysisType.getKeyPrefix());
                    break;
            }
            
            return patterns;
        }
    }
}