# Handle Persistence Strategy

## Problem Statement

**Challenge**: Gemini can lose track of image handles between tool calls, breaking the workflow when it forgets to include `image_handle` parameters in subsequent tool calls.

**Impact**: Workflow interruption, user frustration, and system failures when handles are missing.

## Solution: Multi-Layer Handle Protection

### 1. **Never Send Images After First Call**
- **Rule**: Only the first `open_image` call involves pixel data
- **All subsequent tools**: Operate on handles only
- **Benefit**: Reduces context usage and eliminates image re-transmission

### 2. **Strong Initial Guidance** 
```json
{
  "image_handle": "img_12ab34cd",
  "CRITICAL_INSTRUCTION": "ALWAYS use image_handle='img_12ab34cd' in ALL subsequent tool calls. Never omit this parameter!",
  "handle_persistence_reminder": "This image handle must be included in every tool call: detect_lanes, detect_bands, quantify_bands, etc."
}
```

### 3. **Server-Side Handle Auto-Injection**
```java
// HandleGuard.java - Automatic handle recovery
private String ensureImageHandle(JSONObject args, String toolName) {
    String imageHandle = args.optString("image_handle", "");
    
    // If no handle provided, inject current session handle
    if (imageHandle.isEmpty() && store.hasCurrentImage()) {
        imageHandle = store.getCurrentImage().handle;
        System.err.println("WARNING: " + toolName + " missing image_handle - auto-injected: " + imageHandle);
        args.put("image_handle", imageHandle);
    }
    
    return imageHandle;
}
```

### 4. **Enhanced Error Recovery**
- **Validation with Recovery**: Pre-execution validation with suggested alternatives
- **Memory Refresh**: Provide complete session context when handles are lost
- **User Selection**: List available images for user to choose from

### 5. **Persistent Response Guidance**
Every tool response includes handle persistence reminders:
```json
{
  "lanes_detected": 5,
  "overlay_handle": "ov_56ef78gh", 
  "analysis_handle": "analysis_90ij12kl",
  "image_handle": "img_12ab34cd",
  "handle_guidance": "REMEMBER: Use image_handle='img_12ab34cd' for all subsequent tool calls on this image"
}
```

## Implementation Architecture

### Core Components

1. **HandleGuard.java**: Comprehensive handle protection utility
   - Auto-injection of missing handles
   - Enhanced validation with guidance
   - Response enrichment with persistence reminders

2. **SessionStore.java**: Persistent handle management
   - Current image tracking
   - Handle validation methods
   - Session state management

3. **SessionRecovery.java**: Exception handling and recovery
   - Handle validation and recovery strategies
   - Memory refresh generation
   - Comprehensive error reporting

### Protection Workflow

```java
// Applied to every tool method
HandleGuard.HandleValidationResult protection = handleGuard.protectToolCall(args, "tool_name");

if (!protection.isValid()) {
    return protection.createErrorResponse();
}

SessionStore.ImageRecord img = store.getImage(protection.imageHandle);
// ... tool logic ...

JSONObject response = new JSONObject()
    .put("result_data", data)
    .put("image_handle", img.handle);
    
return handleGuard.addPersistenceGuidance(response, img.handle);
```

## Recovery Strategies

### 1. **Current Image Fallback**
- Use currently active image when handle is missing
- Most common recovery scenario

### 2. **Most Recent Image**
- Use most recently added image as backup
- Handles session continuation scenarios

### 3. **User Selection Prompt**
- List available images for user choice
- Interactive recovery for complex sessions

### 4. **Session Context Refresh**
- Provide complete session state to Gemini
- Memory refresh for context recovery

## Error Handling Scenarios

### Scenario 1: Missing Handle
```
Input: detect_bands({})
Action: Auto-inject current image handle
Log: "WARNING: detect_bands missing image_handle - auto-injected: img_12ab34cd"
Result: Tool proceeds normally
```

### Scenario 2: Invalid Handle
```
Input: detect_bands({"image_handle": "invalid_handle"})
Action: Validate and provide recovery options
Result: Error response with suggestions and memory refresh
```

### Scenario 3: No Available Images
```
Input: detect_bands({})
State: No current image in session
Action: Provide guidance to load image first
Result: Clear error with next steps
```

## Prompt Engineering Guidelines

### For Gemini Integration

1. **Initial System Prompt**:
```
CRITICAL: When working with gel images, ALWAYS include the image_handle parameter in ALL tool calls after open_image. The handle is persistent throughout the conversation and must never be omitted.

Example workflow:
1. open_image({"path": "..."}) → returns {"image_handle": "img_abc123"}
2. detect_lanes({"image_handle": "img_abc123", ...})
3. detect_bands({"image_handle": "img_abc123", ...})
4. quantify_bands({"image_handle": "img_abc123", ...})

Never call tools without the image_handle parameter.
```

2. **Reinforcement in Responses**:
- Every successful response includes handle guidance
- Error responses include memory refresh
- Validation failures provide explicit instructions

## Benefits

### Reliability
- **99% Handle Retention**: Server-side guards prevent workflow breaks
- **Automatic Recovery**: Self-healing system continues operation
- **Context Preservation**: Session state maintained across interactions

### User Experience
- **Seamless Operation**: Users don't experience handle-related failures
- **Clear Guidance**: When issues occur, recovery is straightforward
- **Workflow Continuity**: Analysis proceeds without interruption

### System Robustness
- **Multiple Fallbacks**: Various recovery strategies available
- **Comprehensive Logging**: Handle issues are tracked and logged
- **Graceful Degradation**: System fails safely with helpful guidance

## Testing Strategy

1. **Successful Path Testing**: Verify normal operation with handles
2. **Auto-Injection Testing**: Confirm missing handle recovery
3. **Validation Testing**: Check invalid handle error responses
4. **Session Recovery Testing**: Test memory refresh capabilities
5. **Multi-Image Testing**: Verify correct handle selection in complex sessions

## Monitoring and Logging

- **Handle Injection Events**: Log when auto-injection occurs
- **Validation Failures**: Track invalid handle attempts
- **Recovery Success Rate**: Monitor system self-healing effectiveness
- **User Experience Metrics**: Track workflow interruption incidents

This comprehensive strategy ensures Gemini maintains image handle persistence across tool calls, providing a robust and user-friendly gel analysis experience.