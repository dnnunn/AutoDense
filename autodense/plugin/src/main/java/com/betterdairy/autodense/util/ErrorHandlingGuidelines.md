# AutoDense Error Handling Guidelines

> **Doc Meta**
> - **Purpose:** Comprehensive guidelines for standardized error handling in AutoDense
> - **Scope:** All tool classes, validation, and error recovery patterns
> - **Owner:** @autodense-dev-team
> - **Last-verified:** 2025-08-28

## Overview

This document provides comprehensive guidelines for implementing consistent error handling across all AutoDense tool classes using the standardized ErrorHandler infrastructure.

## Core Principles

### 1. Specific Exception Handling
Always catch specific exception types rather than generic `Exception` where possible:

```java
// ✅ Good: Specific exception handling
try {
    // Tool logic here
    return result;
} catch (IllegalArgumentException e) {
    return ErrorHandler.handleValidationError("tool_name", e, logger, recovery);
} catch (IllegalStateException e) {
    return ErrorHandler.handleSessionError("tool_name", e, logger, recovery);
} catch (IOException e) {
    return ErrorHandler.handleFileError("tool_name", e, logger, recovery);
} catch (Exception e) {
    return ErrorHandler.handleUnexpectedError("tool_name", e, logger, recovery);
}

// ❌ Bad: Generic exception handling only
try {
    // Tool logic here
    return result;
} catch (Exception e) {
    return ErrorHandler.handleUnexpectedError("tool_name", e, logger, recovery);
}
```

### 2. Consistent Tool Naming
Use consistent, descriptive tool names in all ErrorHandler calls:

```java
// ✅ Good: Consistent naming
ErrorHandler.handleValidationError("detect_colonies", e, logger, recovery);
ErrorHandler.handleValidationError("detect_bands", e, logger, recovery);
ErrorHandler.handleValidationError("export_results", e, logger, recovery);

// ❌ Bad: Inconsistent naming
ErrorHandler.handleValidationError("assay_tool", e, logger, recovery);
ErrorHandler.handleValidationError("gel_tool", e, logger, recovery);
ErrorHandler.handleValidationError("export", e, logger, recovery);
```

### 3. Input Validation First
Always validate inputs before processing using ValidationPatterns:

```java
public JSONObject detectColonies(JSONObject params) {
    try {
        // Validate inputs first
        JSONObject validatedParams = ValidationPatterns.validateColonyDetectionTool(params, "detect_colonies");
        String imageHandle = ValidationPatterns.CommonValidations
            .validateAndSanitizeImageHandle(validatedParams, "image_handle");
        
        // Then proceed with logic
        // ...
        
    } catch (InputValidator.ValidationException e) {
        return ErrorHandler.handleValidationError("detect_colonies", e, logger, recovery);
    } catch (IllegalStateException e) {
        return ErrorHandler.handleSessionError("detect_colonies", e, logger, recovery);
    } catch (Exception e) {
        return ErrorHandler.handleUnexpectedError("detect_colonies", e, logger, recovery);
    }
}
```

## Error Handler Categories

### ValidationError
Use for input validation failures:
- Invalid parameter values
- Missing required parameters
- Format violations
- Range violations

```java
// When to use
if (imageHandle == null || imageHandle.trim().isEmpty()) {
    throw new IllegalArgumentException("Image handle cannot be null or empty");
}
// ErrorHandler will catch and create appropriate response
```

### SessionError
Use for session state issues:
- Handle not found in session
- Session corruption
- Concurrent access issues
- State inconsistency

```java
// When to use
SessionStore.ImageRecord imgRecord = store.getImage(imageHandle);
if (imgRecord == null) {
    throw new IllegalStateException("Image handle not found in session: " + imageHandle);
}
```

### FileError  
Use for I/O related issues:
- File not found
- Permission denied
- Disk space issues
- Network issues

```java
// When to use
try {
    Files.copy(source, target);
} catch (IOException e) {
    // ErrorHandler will provide specific recovery guidance
    throw e;
}
```

### ImageProcessingError
Use for ImageJ/image processing issues:
- Invalid image operations
- Unsupported image types
- Processing failures

```java
// When to use
if (imp == null || imp.getProcessor() == null) {
    throw new IllegalStateException("Invalid or closed image");
}
```

### UnexpectedError
Use as last resort for unknown errors:
- Unexpected runtime exceptions
- Third-party library failures
- System-level issues

## Standard Error Handling Patterns

### Pattern 1: Public Tool Method
```java
public JSONObject toolMethod(JSONObject params) {
    try {
        // 1. Validate inputs
        JSONObject validatedParams = ValidationPatterns.validateTool(params, "tool_name");
        
        // 2. Extract and validate handles
        String handle = ValidationPatterns.CommonValidations
            .validateAndSanitizeImageHandle(validatedParams, "image_handle");
        
        // 3. Perform operations
        ResultType result = performOperation(handle, validatedParams);
        
        // 4. Return success response
        return createSuccessResponse(result);
        
    } catch (InputValidator.ValidationException e) {
        return ErrorHandler.handleValidationError("tool_name", e, logger, recovery);
    } catch (IllegalStateException e) {
        return ErrorHandler.handleSessionError("tool_name", e, logger, recovery);
    } catch (IOException e) {
        return ErrorHandler.handleFileError("tool_name", e, logger, recovery);
    } catch (Exception e) {
        return ErrorHandler.handleUnexpectedError("tool_name", e, logger, recovery);
    }
}
```

### Pattern 2: Private Helper Method
```java
private ResultType performOperation(String handle, JSONObject params) {
    try {
        // Helper logic
        return result;
    } catch (IllegalArgumentException e) {
        logger.log(Level.WARNING, "Invalid parameters in helper method", e);
        throw e; // Re-throw for public method to handle
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Unexpected error in helper method", e);
        throw e; // Re-throw for public method to handle
    }
}
```

### Pattern 3: Resource Cleanup
```java
public JSONObject processImage(JSONObject params) {
    ImagePlus tempImage = null;
    try {
        // Processing logic
        tempImage = createTempImage();
        return processWithImage(tempImage);
        
    } catch (Exception e) {
        return ErrorHandler.handleUnexpectedError("process_image", e, logger, recovery);
    } finally {
        // Always cleanup resources
        if (tempImage != null) {
            ImageJResourceManager.safeCleanup(tempImage);
        }
    }
}
```

## Performance Integration

### Monitoring Integration
Add performance monitoring to expensive operations:

```java
public JSONObject expensiveOperation(JSONObject params) {
    PerformanceOptimizer.PerformanceMonitor monitor = 
        new PerformanceOptimizer.PerformanceMonitor("expensive_operation");
    
    try {
        // Validate inputs
        monitor.checkpoint("Input validation complete");
        
        // Perform operation
        ResultType result = doExpensiveWork();
        monitor.checkpoint("Processing complete");
        
        return createResponse(result);
        
    } catch (Exception e) {
        return ErrorHandler.handleUnexpectedError("expensive_operation", e, logger, recovery);
    } finally {
        monitor.finish();
    }
}
```

### Large Image Handling
Use performance optimization for large images:

```java
public JSONObject processLargeImage(JSONObject params) {
    try {
        ImagePlus originalImage = getImageFromSession(params);
        
        if (PerformanceOptimizer.SmartProcessor.requiresDownscaling(originalImage)) {
            return processWithDownscaling(originalImage, params);
        } else {
            return processNormally(originalImage, params);
        }
        
    } catch (Exception e) {
        return ErrorHandler.handleUnexpectedError("process_large_image", e, logger, recovery);
    }
}
```

## Best Practices

### 1. Consistent Response Format
All tool methods should return consistent JSON response format:

```java
// Success response
{
    "success": true,
    "tool": "tool_name",
    "result_data": { /* specific results */ },
    "timestamp": "2025-08-28T10:30:00Z"
}

// Error response (handled by ErrorHandler)
{
    "ok": false,
    "tool": "tool_name", 
    "error_type": "VALIDATION_ERROR",
    "message": "Specific error message",
    "recovery_actions": { /* recovery guidance */ },
    "timestamp": "2025-08-28T10:30:00Z"
}
```

### 2. Meaningful Error Messages
Provide specific, actionable error messages:

```java
// ✅ Good: Specific and actionable
throw new IllegalArgumentException("Image handle 'img_123' not found in session. " +
    "Verify the image was loaded successfully and the handle is correct.");

// ❌ Bad: Generic and unhelpful  
throw new IllegalArgumentException("Invalid input");
```

### 3. Proper Logging
Log errors with appropriate level and context:

```java
// Helper methods should log and re-throw
logger.log(Level.WARNING, "Invalid parameters for colony detection: " + params, e);
throw e;

// Public methods should let ErrorHandler handle logging
// ErrorHandler will automatically log with proper context
```

### 4. Resource Management
Always clean up resources, especially in error conditions:

```java
ImagePlus tempImage = null;
try {
    tempImage = createTempImage();
    return process(tempImage);
} finally {
    ImageJResourceManager.safeCleanup(tempImage);
}
```

## Common Mistakes to Avoid

### 1. Swallowing Exceptions
```java
// ❌ Bad: Swallowing exceptions
try {
    riskyOperation();
} catch (Exception e) {
    return createGenericErrorResponse();
}

// ✅ Good: Proper error handling
try {
    riskyOperation(); 
} catch (Exception e) {
    return ErrorHandler.handleUnexpectedError("operation", e, logger, recovery);
}
```

### 2. Inconsistent Tool Names
```java
// ❌ Bad: Inconsistent naming
ErrorHandler.handleValidationError("tool1", e, logger, recovery);
ErrorHandler.handleValidationError("different_name", e, logger, recovery);

// ✅ Good: Consistent naming based on method name
ErrorHandler.handleValidationError("detect_colonies", e, logger, recovery);
ErrorHandler.handleValidationError("measure_colonies", e, logger, recovery);
```

### 3. Missing Input Validation
```java
// ❌ Bad: No validation
public JSONObject tool(JSONObject params) {
    String handle = params.getString("image_handle"); // Can throw!
    // ...
}

// ✅ Good: Proper validation
public JSONObject tool(JSONObject params) {
    try {
        JSONObject validated = ValidationPatterns.validateTool(params, "tool_name");
        String handle = ValidationPatterns.CommonValidations
            .validateAndSanitizeImageHandle(validated, "image_handle");
        // ...
    } catch (ValidationException e) {
        return ErrorHandler.handleValidationError("tool_name", e, logger, recovery);
    }
}
```

## Integration with Existing Code

### Migration Strategy
When updating existing methods:

1. **Add input validation** using ValidationPatterns
2. **Replace generic catch blocks** with specific exception handling  
3. **Update tool names** to be consistent
4. **Add resource cleanup** where needed
5. **Test error scenarios** to ensure proper handling

### Example Migration
```java
// Before
public JSONObject oldMethod(JSONObject params) {
    try {
        String handle = params.getString("image_handle");
        return processImage(handle);
    } catch (Exception e) {
        return recovery.createRecoveryResponse("tool", e);
    }
}

// After  
public JSONObject newMethod(JSONObject params) {
    try {
        JSONObject validated = ValidationPatterns.validateImageTool(params, "process_image");
        String handle = ValidationPatterns.CommonValidations
            .validateAndSanitizeImageHandle(validated, "image_handle");
        return processImage(handle);
    } catch (InputValidator.ValidationException e) {
        return ErrorHandler.handleValidationError("process_image", e, logger, recovery);
    } catch (IllegalStateException e) {
        return ErrorHandler.handleSessionError("process_image", e, logger, recovery);
    } catch (Exception e) {
        return ErrorHandler.handleUnexpectedError("process_image", e, logger, recovery);
    }
}
```

This systematic approach ensures all AutoDense tools provide consistent, robust error handling with meaningful recovery guidance.
