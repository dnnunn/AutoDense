package com.betterdairy.autodense.analysis;

import org.json.JSONObject;
import org.json.JSONArray;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * METHOD PROVENANCE SYSTEM for gel analysis workflows.
 * 
 * PURPOSE: Record what/when/params/outputs for method reproducibility and replay.
 * NOT FOR: Runtime logging (use SessionLogger for that).
 * 
 * Records and manages custom gel analysis workflows.
 * Enables users to create, save, and replay complex analysis sequences.
 * 
 * SEPARATION OF CONCERNS:
 * - WorkflowRecorder: Method provenance (what/when/params/outputs for replay)
 * - SessionLogger: Runtime logs (info/warn/error, performance, API calls)
 */
public final class WorkflowRecorder {
    
    private String workflowId;
    private String workflowName;
    private String description;
    private String author;
    private LocalDateTime createdAt;
    private List<RecordedAction> actions;
    private Map<String, Object> metadata;
    private boolean isRecording;
    
    public WorkflowRecorder() {
        this.workflowId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.actions = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.isRecording = false;
    }
    
    /**
     * Start recording a new workflow
     */
    public void startRecording(String name, String description) {
        this.workflowName = name;
        this.description = description;
        this.author = System.getProperty("user.name", "Unknown");
        this.actions.clear();
        this.metadata.clear();
        this.isRecording = true;
        
        System.out.println("🔴 Started recording workflow: '" + name + "'");
    }
    
    /**
     * Record an action during workflow recording
     */
    public void recordAction(JSONObject action, String userPrompt, String context) {
        if (!isRecording) {
            return;
        }
        
        RecordedAction recorded = new RecordedAction(
            action,
            userPrompt,
            context,
            LocalDateTime.now(),
            actions.size() + 1
        );
        
        actions.add(recorded);
        System.out.printf("📝 Recorded step %d: %s%n", recorded.stepNumber, 
                         action.optString("action", "unknown"));
    }
    
    /**
     * Stop recording and finalize the workflow
     */
    public RecordedWorkflow stopRecording() {
        if (!isRecording) {
            throw new IllegalStateException("No recording in progress");
        }
        
        isRecording = false;
        
        RecordedWorkflow workflow = new RecordedWorkflow(
            workflowId,
            workflowName,
            description,
            author,
            createdAt,
            new ArrayList<>(actions),
            new HashMap<>(metadata)
        );
        
        System.out.printf("🛑 Stopped recording. Workflow '%s' contains %d steps%n", 
                         workflowName, actions.size());
        
        return workflow;
    }
    
    /**
     * Cancel current recording
     */
    public void cancelRecording() {
        if (isRecording) {
            isRecording = false;
            actions.clear();
            System.out.println("❌ Recording cancelled");
        }
    }
    
    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Get current recording status
     */
    public String getRecordingStatus() {
        if (!isRecording) {
            return "Not recording";
        }
        return String.format("Recording '%s' - %d steps", workflowName, actions.size());
    }
    
    /**
     * Add metadata to the recording
     */
    public void addMetadata(String key, Object value) {
        if (isRecording) {
            metadata.put(key, value);
        }
    }
    
    /**
     * Individual recorded action
     */
    public static class RecordedAction {
        private final JSONObject action;
        private final String userPrompt;
        private final String context;
        private final LocalDateTime timestamp;
        private final int stepNumber;
        
        public RecordedAction(JSONObject action, String userPrompt, String context, 
                             LocalDateTime timestamp, int stepNumber) {
            this.action = action;
            this.userPrompt = userPrompt;
            this.context = context;
            this.timestamp = timestamp;
            this.stepNumber = stepNumber;
        }
        
        // Getters
        public JSONObject getAction() { return action; }
        public String getUserPrompt() { return userPrompt; }
        public String getContext() { return context; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getStepNumber() { return stepNumber; }
        
        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("step", stepNumber);
            json.put("action", action);
            json.put("user_prompt", userPrompt);
            json.put("context", context);
            json.put("timestamp", timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return json;
        }
    }
    
    /**
     * Complete recorded workflow
     */
    public static class RecordedWorkflow {
        private final String id;
        private final String name;
        private final String description;
        private final String author;
        private final LocalDateTime createdAt;
        private final List<RecordedAction> actions;
        private final Map<String, Object> metadata;
        
        public RecordedWorkflow(String id, String name, String description, String author,
                               LocalDateTime createdAt, List<RecordedAction> actions,
                               Map<String, Object> metadata) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.author = author;
            this.createdAt = createdAt;
            this.actions = actions;
            this.metadata = metadata;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public List<RecordedAction> getActions() { return actions; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        /**
         * Convert workflow to executable JSON plan
         */
        public JSONObject toExecutablePlan() {
            JSONObject plan = new JSONObject();
            plan.put("intent", "multi_action");
            
            JSONArray actionArray = new JSONArray();
            for (RecordedAction recorded : actions) {
                actionArray.put(recorded.getAction());
            }
            plan.put("actions", actionArray);
            
            return plan;
        }
        
        /**
         * Get workflow summary
         */
        public String getSummary() {
            return String.format("%s (%d steps) - %s", name, actions.size(), description);
        }
        
        /**
         * Export workflow to JSON
         */
        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("description", description);
            json.put("author", author);
            json.put("created_at", createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            json.put("version", "1.0");
            
            // Add workflow metadata
            JSONObject meta = new JSONObject();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                meta.put(entry.getKey(), entry.getValue());
            }
            json.put("metadata", meta);
            
            // Add recorded actions
            JSONArray stepsArray = new JSONArray();
            for (RecordedAction action : actions) {
                stepsArray.put(action.toJSON());
            }
            json.put("steps", stepsArray);
            
            return json;
        }
        
        /**
         * Create workflow from JSON
         */
        public static RecordedWorkflow fromJSON(JSONObject json) {
            String id = json.getString("id");
            String name = json.getString("name");
            String description = json.getString("description");
            String author = json.getString("author");
            LocalDateTime createdAt = LocalDateTime.parse(
                json.getString("created_at"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            // Parse metadata
            Map<String, Object> metadata = new HashMap<>();
            JSONObject metaObj = json.optJSONObject("metadata");
            if (metaObj != null) {
                for (String key : metaObj.keySet()) {
                    metadata.put(key, metaObj.get(key));
                }
            }
            
            // Parse steps
            List<RecordedAction> actions = new ArrayList<>();
            JSONArray stepsArray = json.getJSONArray("steps");
            for (int i = 0; i < stepsArray.length(); i++) {
                JSONObject stepObj = stepsArray.getJSONObject(i);
                
                RecordedAction action = new RecordedAction(
                    stepObj.getJSONObject("action"),
                    stepObj.getString("user_prompt"),
                    stepObj.getString("context"),
                    LocalDateTime.parse(stepObj.getString("timestamp"), 
                                       DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    stepObj.getInt("step")
                );
                actions.add(action);
            }
            
            return new RecordedWorkflow(id, name, description, author, createdAt, actions, metadata);
        }
        
        /**
         * Get action types used in this workflow
         */
        public List<String> getActionTypes() {
            List<String> types = new ArrayList<>();
            for (RecordedAction action : actions) {
                String actionType = action.getAction().optString("action", "unknown");
                if (!types.contains(actionType)) {
                    types.add(actionType);
                }
            }
            return types;
        }
        
        /**
         * Check if workflow contains specific action type
         */
        public boolean containsActionType(String actionType) {
            return actions.stream()
                .anyMatch(a -> actionType.equals(a.getAction().optString("action")));
        }
        
        /**
         * Get estimated execution time (in seconds)
         */
        public int getEstimatedDuration() {
            // Rough estimates based on action complexity
            int totalSeconds = 0;
            for (RecordedAction action : actions) {
                String actionType = action.getAction().optString("action");
                switch (actionType) {
                    case "run_preset", "clahe", "bandpass", "background" -> totalSeconds += 5;
                    case "detect_bands" -> totalSeconds += 15;
                    case "quantify_bands" -> totalSeconds += 10;
                    case "calibrate_mw" -> totalSeconds += 8;
                    case "rotate", "flip", "crop" -> totalSeconds += 2;
                    case "set_ladder", "normalize", "export" -> totalSeconds += 3;
                    default -> totalSeconds += 1;
                }
            }
            return Math.max(totalSeconds, 5); // Minimum 5 seconds
        }
        
        @Override
        public String toString() {
            return String.format("Workflow '%s' by %s (%d steps, ~%ds)", 
                               name, author, actions.size(), getEstimatedDuration());
        }
    }
}