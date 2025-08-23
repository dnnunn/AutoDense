package com.betterdairy.autodense.workflow;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages workflow preset storage, loading, and user customization.
 * 
 * Workflow presets are stored as JSON files in the user's AutoDense directory:
 * ~/.autodense/workflows/
 * 
 * Built-in presets are automatically created on first run.
 * Users can create, modify, and delete custom presets.
 */
public class WorkflowPresetManager {
    
    private static final String WORKFLOWS_DIR = System.getProperty("user.home") + "/.autodense/workflows";
    private static final String BUILTIN_PRESETS_FILE = "builtin_presets.json";
    private static final String USER_PRESETS_FILE = "user_presets.json";
    
    private Map<String, WorkflowPreset> presets;
    private Path workflowsDir;
    
    public WorkflowPresetManager() {
        this.presets = new HashMap<>();
        this.workflowsDir = Paths.get(WORKFLOWS_DIR);
        
        // Create workflows directory if it doesn't exist
        try {
            Files.createDirectories(workflowsDir);
        } catch (IOException e) {
            System.err.println("Warning: Could not create workflows directory: " + e.getMessage());
        }
        
        // Load presets
        loadBuiltinPresets();
        loadUserPresets();
    }
    
    /**
     * Get all available workflow presets
     */
    public List<WorkflowPreset> getAllPresets() {
        return new ArrayList<>(presets.values());
    }
    
    /**
     * Get presets filtered by type
     */
    public List<WorkflowPreset> getPresetsByType(WorkflowPreset.WorkflowType type) {
        return presets.values().stream()
            .filter(preset -> preset.getType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Get a preset by name
     */
    public WorkflowPreset getPreset(String name) {
        return presets.get(name);
    }
    
    /**
     * Save a new user preset
     */
    public void savePreset(WorkflowPreset preset) {
        presets.put(preset.getName(), preset);
        saveUserPresets();
    }
    
    /**
     * Delete a user preset (built-in presets cannot be deleted)
     */
    public boolean deletePreset(String name) {
        if (isBuiltinPreset(name)) {
            return false; // Cannot delete built-in presets
        }
        
        WorkflowPreset removed = presets.remove(name);
        if (removed != null) {
            saveUserPresets();
            return true;
        }
        return false;
    }
    
    /**
     * Update an existing preset
     */
    public void updatePreset(WorkflowPreset preset) {
        if (presets.containsKey(preset.getName())) {
            presets.put(preset.getName(), preset);
            if (!isBuiltinPreset(preset.getName())) {
                saveUserPresets();
            }
        }
    }
    
    /**
     * Check if a preset is built-in (read-only)
     */
    public boolean isBuiltinPreset(String name) {
        // Built-in preset names
        return name.equals("Molecular Weight Determination") ||
               name.equals("Protein Quantification with Standards") ||
               name.equals("Compare Lanes") ||
               name.equals("Semi-Quantitative PCR") ||
               name.equals("X-gal Blue/White Colony Screening") ||
               name.equals("Bacterial Growth Quantification");
    }
    
    /**
     * Get preset suggestions based on natural language input
     */
    public List<WorkflowPreset> suggestPresets(String userInput) {
        String input = userInput.toLowerCase();
        List<WorkflowPreset> suggestions = new ArrayList<>();
        
        for (WorkflowPreset preset : presets.values()) {
            // Check if input matches preset name or description
            if (preset.getName().toLowerCase().contains(input) ||
                preset.getDescription().toLowerCase().contains(input)) {
                suggestions.add(preset);
                continue;
            }
            
            // Check if input matches natural language examples
            for (String example : preset.getNaturalLanguageExamples()) {
                if (example.toLowerCase().contains(input) || 
                    input.contains(example.toLowerCase().split(" ")[0])) { // First word match
                    suggestions.add(preset);
                    break;
                }
            }
        }
        
        // Sort by use count (most used first)
        suggestions.sort((a, b) -> Integer.compare(b.getUseCount(), a.getUseCount()));
        
        return suggestions;
    }
    
    /**
     * Record that a preset was used (updates statistics)
     */
    public void recordPresetUse(String presetName) {
        WorkflowPreset preset = presets.get(presetName);
        if (preset != null) {
            preset.recordUse();
            if (!isBuiltinPreset(presetName)) {
                saveUserPresets(); // Update usage stats
            }
        }
    }
    
    private void loadBuiltinPresets() {
        // Create default built-in presets
        WorkflowPreset molWeightDetermination = WorkflowPreset.createMolecularWeightDetermination();
        WorkflowPreset proteinQuant = WorkflowPreset.createProteinQuantification();
        WorkflowPreset compareLanes = WorkflowPreset.createCompareLanes();
        WorkflowPreset semiQuantPCR = WorkflowPreset.createSemiQuantitativePCR();
        WorkflowPreset xgalColony = WorkflowPreset.createXgalColonyScreen();
        WorkflowPreset growthQuant = WorkflowPreset.createGrowthQuantification();
        
        presets.put(molWeightDetermination.getName(), molWeightDetermination);
        presets.put(proteinQuant.getName(), proteinQuant);
        presets.put(compareLanes.getName(), compareLanes);
        presets.put(semiQuantPCR.getName(), semiQuantPCR);
        presets.put(xgalColony.getName(), xgalColony);
        presets.put(growthQuant.getName(), growthQuant);
        
        // Save built-in presets to file for reference
        saveBuiltinPresets();
    }
    
    private void loadUserPresets() {
        Path userPresetsFile = workflowsDir.resolve(USER_PRESETS_FILE);
        if (!Files.exists(userPresetsFile)) {
            return; // No user presets yet
        }
        
        try {
            String json = Files.readString(userPresetsFile);
            JSONObject root = new JSONObject(json);
            JSONArray presetsArray = root.getJSONArray("presets");
            
            for (int i = 0; i < presetsArray.length(); i++) {
                JSONObject presetJson = presetsArray.getJSONObject(i);
                WorkflowPreset preset = WorkflowPreset.fromJSON(presetJson);
                presets.put(preset.getName(), preset);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load user presets: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Warning: Corrupted user presets file: " + e.getMessage());
        }
    }
    
    private void saveBuiltinPresets() {
        Path builtinFile = workflowsDir.resolve(BUILTIN_PRESETS_FILE);
        
        JSONObject root = new JSONObject();
        JSONArray presetsArray = new JSONArray();
        
        // Save only built-in presets
        for (WorkflowPreset preset : presets.values()) {
            if (isBuiltinPreset(preset.getName())) {
                presetsArray.put(preset.toJSON());
            }
        }
        
        root.put("presets", presetsArray);
        root.put("version", "1.0");
        root.put("created", java.time.Instant.now().toString());
        
        try {
            Files.writeString(builtinFile, root.toString(2));
        } catch (IOException e) {
            System.err.println("Warning: Could not save built-in presets: " + e.getMessage());
        }
    }
    
    private void saveUserPresets() {
        Path userFile = workflowsDir.resolve(USER_PRESETS_FILE);
        
        JSONObject root = new JSONObject();
        JSONArray presetsArray = new JSONArray();
        
        // Save only user presets
        for (WorkflowPreset preset : presets.values()) {
            if (!isBuiltinPreset(preset.getName())) {
                presetsArray.put(preset.toJSON());
            }
        }
        
        root.put("presets", presetsArray);
        root.put("version", "1.0");
        root.put("lastUpdated", java.time.Instant.now().toString());
        
        try {
            Files.writeString(userFile, root.toString(2));
        } catch (IOException e) {
            System.err.println("Warning: Could not save user presets: " + e.getMessage());
        }
    }
    
    /**
     * Export presets to a file for backup or sharing
     */
    public void exportPresets(Path exportFile) throws IOException {
        JSONObject root = new JSONObject();
        JSONArray presetsArray = new JSONArray();
        
        for (WorkflowPreset preset : presets.values()) {
            presetsArray.put(preset.toJSON());
        }
        
        root.put("presets", presetsArray);
        root.put("exported", java.time.Instant.now().toString());
        root.put("version", "1.0");
        
        Files.writeString(exportFile, root.toString(2));
    }
    
    /**
     * Import presets from a file
     */
    public void importPresets(Path importFile) throws IOException {
        String json = Files.readString(importFile);
        JSONObject root = new JSONObject(json);
        JSONArray presetsArray = root.getJSONArray("presets");
        
        for (int i = 0; i < presetsArray.length(); i++) {
            JSONObject presetJson = presetsArray.getJSONObject(i);
            WorkflowPreset preset = WorkflowPreset.fromJSON(presetJson);
            
            // Don't overwrite built-in presets
            if (!isBuiltinPreset(preset.getName())) {
                presets.put(preset.getName(), preset);
            }
        }
        
        saveUserPresets();
    }
    
    /**
     * Get workflow statistics
     */
    public Map<String, Object> getWorkflowStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPresets", presets.size());
        stats.put("builtinPresets", (int) presets.values().stream()
            .filter(p -> isBuiltinPreset(p.getName())).count());
        stats.put("userPresets", presets.size() - (Integer) stats.get("builtinPresets"));
        
        int totalUses = presets.values().stream()
            .mapToInt(WorkflowPreset::getUseCount)
            .sum();
        stats.put("totalUses", totalUses);
        
        // Most popular preset
        Optional<WorkflowPreset> mostUsed = presets.values().stream()
            .max(Comparator.comparingInt(WorkflowPreset::getUseCount));
        if (mostUsed.isPresent()) {
            stats.put("mostUsedPreset", mostUsed.get().getName());
            stats.put("mostUsedCount", mostUsed.get().getUseCount());
        }
        
        return stats;
    }
}