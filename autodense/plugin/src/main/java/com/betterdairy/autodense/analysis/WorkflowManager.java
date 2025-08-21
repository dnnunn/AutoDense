package com.betterdairy.autodense.analysis;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Manages storage, retrieval, and organization of recorded workflows.
 * Provides persistent storage and workflow library functionality.
 */
public final class WorkflowManager {
    
    private static final String WORKFLOWS_DIR = "workflows";
    private static final String WORKFLOW_FILE_EXT = ".workflow.json";
    
    private final Path workflowsDirectory;
    
    public WorkflowManager() {
        this.workflowsDirectory = getWorkflowsDirectory();
        ensureWorkflowDirectoryExists();
    }
    
    /**
     * Save a workflow to persistent storage
     */
    public void saveWorkflow(WorkflowRecorder.RecordedWorkflow workflow) throws IOException {
        String filename = sanitizeFilename(workflow.getName()) + WORKFLOW_FILE_EXT;
        Path filePath = workflowsDirectory.resolve(filename);
        
        JSONObject json = workflow.toJSON();
        
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(json.toString(2));
        }
        
        System.out.printf("💾 Saved workflow '%s' to %s%n", workflow.getName(), filename);
    }
    
    /**
     * Load a workflow by name
     */
    public WorkflowRecorder.RecordedWorkflow loadWorkflow(String name) throws IOException {
        String filename = sanitizeFilename(name) + WORKFLOW_FILE_EXT;
        Path filePath = workflowsDirectory.resolve(filename);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Workflow not found: " + name);
        }
        
        String jsonContent = Files.readString(filePath);
        JSONObject json = new JSONObject(jsonContent);
        
        return WorkflowRecorder.RecordedWorkflow.fromJSON(json);
    }
    
    /**
     * Load a workflow by ID
     */
    public WorkflowRecorder.RecordedWorkflow loadWorkflowById(String id) throws IOException {
        List<WorkflowRecorder.RecordedWorkflow> workflows = listAllWorkflows();
        return workflows.stream()
                .filter(w -> w.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IOException("Workflow not found with ID: " + id));
    }
    
    /**
     * List all saved workflows
     */
    public List<WorkflowRecorder.RecordedWorkflow> listAllWorkflows() throws IOException {
        if (!Files.exists(workflowsDirectory)) {
            return new ArrayList<>();
        }
        
        List<WorkflowRecorder.RecordedWorkflow> workflows = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(workflowsDirectory)) {
            List<Path> workflowFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(WORKFLOW_FILE_EXT))
                    .collect(Collectors.toList());
            
            for (Path filePath : workflowFiles) {
                try {
                    String jsonContent = Files.readString(filePath);
                    JSONObject json = new JSONObject(jsonContent);
                    WorkflowRecorder.RecordedWorkflow workflow = 
                        WorkflowRecorder.RecordedWorkflow.fromJSON(json);
                    workflows.add(workflow);
                } catch (Exception e) {
                    System.err.println("Failed to load workflow from " + filePath + ": " + e.getMessage());
                }
            }
        }
        
        return workflows;
    }
    
    /**
     * Delete a workflow by name
     */
    public boolean deleteWorkflow(String name) throws IOException {
        String filename = sanitizeFilename(name) + WORKFLOW_FILE_EXT;
        Path filePath = workflowsDirectory.resolve(filename);
        
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            System.out.printf("🗑️  Deleted workflow '%s'%n", name);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a workflow exists
     */
    public boolean workflowExists(String name) {
        String filename = sanitizeFilename(name) + WORKFLOW_FILE_EXT;
        Path filePath = workflowsDirectory.resolve(filename);
        return Files.exists(filePath);
    }
    
    /**
     * Get workflow names (for dropdown/selection)
     */
    public List<String> getWorkflowNames() throws IOException {
        return listAllWorkflows().stream()
                .map(WorkflowRecorder.RecordedWorkflow::getName)
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Search workflows by action type
     */
    public List<WorkflowRecorder.RecordedWorkflow> findWorkflowsByActionType(String actionType) throws IOException {
        return listAllWorkflows().stream()
                .filter(w -> w.containsActionType(actionType))
                .collect(Collectors.toList());
    }
    
    /**
     * Search workflows by description keywords
     */
    public List<WorkflowRecorder.RecordedWorkflow> searchWorkflows(String keyword) throws IOException {
        String lowerKeyword = keyword.toLowerCase();
        return listAllWorkflows().stream()
                .filter(w -> w.getName().toLowerCase().contains(lowerKeyword) ||
                           w.getDescription().toLowerCase().contains(lowerKeyword))
                .collect(Collectors.toList());
    }
    
    /**
     * Get workflow statistics
     */
    public WorkflowStatistics getWorkflowStatistics() throws IOException {
        List<WorkflowRecorder.RecordedWorkflow> workflows = listAllWorkflows();
        
        int totalWorkflows = workflows.size();
        int totalSteps = workflows.stream()
                .mapToInt(w -> w.getActions().size())
                .sum();
        
        int totalEstimatedTime = workflows.stream()
                .mapToInt(WorkflowRecorder.RecordedWorkflow::getEstimatedDuration)
                .sum();
        
        // Count action types
        List<String> allActionTypes = workflows.stream()
                .flatMap(w -> w.getActionTypes().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        return new WorkflowStatistics(totalWorkflows, totalSteps, totalEstimatedTime, allActionTypes);
    }
    
    /**
     * Export all workflows as a single JSON file
     */
    public void exportWorkflows(Path exportPath) throws IOException {
        List<WorkflowRecorder.RecordedWorkflow> workflows = listAllWorkflows();
        
        JSONObject export = new JSONObject();
        export.put("export_version", "1.0");
        export.put("export_timestamp", java.time.LocalDateTime.now().toString());
        export.put("workflow_count", workflows.size());
        
        JSONArray workflowArray = new JSONArray();
        for (WorkflowRecorder.RecordedWorkflow workflow : workflows) {
            workflowArray.put(workflow.toJSON());
        }
        export.put("workflows", workflowArray);
        
        try (FileWriter writer = new FileWriter(exportPath.toFile())) {
            writer.write(export.toString(2));
        }
        
        System.out.printf("📤 Exported %d workflows to %s%n", workflows.size(), exportPath);
    }
    
    /**
     * Import workflows from JSON file
     */
    public int importWorkflows(Path importPath, boolean overwrite) throws IOException {
        String jsonContent = Files.readString(importPath);
        JSONObject importData = new JSONObject(jsonContent);
        
        JSONArray workflowArray = importData.getJSONArray("workflows");
        int importedCount = 0;
        int skippedCount = 0;
        
        for (int i = 0; i < workflowArray.length(); i++) {
            JSONObject workflowJson = workflowArray.getJSONObject(i);
            WorkflowRecorder.RecordedWorkflow workflow = 
                WorkflowRecorder.RecordedWorkflow.fromJSON(workflowJson);
            
            if (workflowExists(workflow.getName()) && !overwrite) {
                skippedCount++;
                System.out.printf("⏭️  Skipped existing workflow: %s%n", workflow.getName());
                continue;
            }
            
            saveWorkflow(workflow);
            importedCount++;
        }
        
        System.out.printf("📥 Imported %d workflows, skipped %d%n", importedCount, skippedCount);
        return importedCount;
    }
    
    private Path getWorkflowsDirectory() {
        // Try to use user's document directory, fall back to current directory
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            return Paths.get(userHome, ".autodense", WORKFLOWS_DIR);
        } else {
            return Paths.get(WORKFLOWS_DIR);
        }
    }
    
    private void ensureWorkflowDirectoryExists() {
        try {
            Files.createDirectories(workflowsDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create workflows directory: " + e.getMessage());
        }
    }
    
    private String sanitizeFilename(String name) {
        // Replace invalid filename characters
        return name.replaceAll("[^a-zA-Z0-9\\-_\\s]", "")
                  .replaceAll("\\s+", "_")
                  .toLowerCase();
    }
    
    /**
     * Workflow statistics summary
     */
    public static class WorkflowStatistics {
        private final int totalWorkflows;
        private final int totalSteps;
        private final int totalEstimatedTime;
        private final List<String> actionTypes;
        
        public WorkflowStatistics(int totalWorkflows, int totalSteps, 
                                 int totalEstimatedTime, List<String> actionTypes) {
            this.totalWorkflows = totalWorkflows;
            this.totalSteps = totalSteps;
            this.totalEstimatedTime = totalEstimatedTime;
            this.actionTypes = actionTypes;
        }
        
        public int getTotalWorkflows() { return totalWorkflows; }
        public int getTotalSteps() { return totalSteps; }
        public int getTotalEstimatedTime() { return totalEstimatedTime; }
        public List<String> getActionTypes() { return actionTypes; }
        
        public double getAverageStepsPerWorkflow() {
            return totalWorkflows > 0 ? (double) totalSteps / totalWorkflows : 0;
        }
        
        public int getAverageTimePerWorkflow() {
            return totalWorkflows > 0 ? totalEstimatedTime / totalWorkflows : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Workflows: %d, Steps: %d, Est. Time: %ds, Avg Steps/Workflow: %.1f",
                totalWorkflows, totalSteps, totalEstimatedTime, getAverageStepsPerWorkflow()
            );
        }
    }
}