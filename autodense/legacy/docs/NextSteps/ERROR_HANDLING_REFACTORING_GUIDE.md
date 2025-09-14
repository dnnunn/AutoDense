> **Doc Meta**
> - **Purpose:** Guide for refactoring generic Exception catching to specific error handling patterns
> - **Scope:** Phase 4.1 error handling improvements from Combined Audit Action Plan
> - **Owner:** @davidnunn 
> - **Last-verified:** 2025-08-27

# Error Handling Refactoring Guide

**Phase 4.1 Implementation** - Addresses audit finding: "Silent catch-all exception handling masks problems"

## Problem Statement

The codebase currently has **79+ generic `catch (Exception e)` blocks** in tool methods that:
- **Mask specific error types** making debugging difficult
- **Hide root causes** behind generic error messages  
- **Prevent proper error recovery** by treating all errors the same
- **Reduce system reliability** by not handling expected error conditions appropriately

## Solution: Structured Error Handling

### 1. New ErrorHandler Utility

Created `com.betterdairy.autodense.util.ErrorHandler` with:
- **Specific error type handlers** for common failure scenarios
- **Comprehensive logging** with full stack traces
- **Structured error responses** with recovery guidance
- **Error metrics** for monitoring

### 2. Refactoring Pattern

**Before (Generic Exception Handling):**
```java
public JSONObject toolMethod(JSONObject args) {
    try {
        // ... tool logic
        return ok("tool_name", result);
    } catch (Exception e) {
        return recovery.createRecoveryResponse("tool_name", e);
    }
}
```

**After (Specific Error Handling):**
```java
public JSONObject toolMethod(JSONObject args) {
    try {
        // ... tool logic
        return ok("tool_name", result);
    } catch (IllegalArgumentException e) {
        return ErrorHandler.handleValidationError("tool_name", e, logger, recovery);
    } catch (IllegalStateException e) {
        return ErrorHandler.handleSessionError("tool_name", e, logger, recovery);
    } catch (NullPointerException e) {
        return ErrorHandler.handleImageProcessingError("tool_name", e, logger, recovery);
    } catch (RuntimeException e) {
        return ErrorHandler.handleUnexpectedError("tool_name", e, logger, recovery);
    }
}
```

## Error Type Categories

### 1. Validation Errors (`IllegalArgumentException`)
**Common causes:**
- Invalid parameters
- Missing required fields
- Out-of-range values
- Invalid handles

**Example Recovery:**
- Parameter validation guidance
- Handle existence checks
- Input format suggestions

### 2. Image Processing Errors (`NullPointerException`, `ArrayIndexOutOfBoundsException`)
**Common causes:**
- Closed/invalid ImagePlus objects
- Image dimension mismatches
- Pixel array access errors

**Example Recovery:**
- Image validity checks
- Dimension verification
- Type conversion suggestions

### 3. Session Errors (`IllegalStateException`, `ConcurrentModificationException`)
**Common causes:**
- Invalid session state
- Handle not found
- Concurrent access issues

**Example Recovery:**
- Session state validation
- Handle refresh suggestions
- Retry with locking

### 4. File I/O Errors (`IOException`, `FileNotFoundException`)
**Common causes:**
- Missing files
- Permission issues
- Disk space problems

**Example Recovery:**
- File existence checks
- Permission verification
- Disk space warnings

## Implementation Progress

### ✅ Completed
- Created `ErrorHandler` utility class
- Demonstrated pattern on key methods:
  - `GelAnalysisTools.openImage()`
  - `GelAnalysisTools.detectLanes()`

### 🔄 In Progress
- Document refactoring approach
- Create migration checklist

### ⏳ Remaining Work

**High Priority Tools (Core User Functionality):**
1. **GelAnalysisTools.java** (47 methods to refactor)
2. **ColonyAnalysisTools.java** (13 methods to refactor) 
3. **AssayOps.java** (11 methods to refactor)
4. **PlateAnalysisTools.java** (8 methods to refactor)
5. **CanonicalTools.java** (6 methods to refactor)

**Session/Infrastructure** (Lower Priority):
- SessionLogger.java (9 methods)
- SessionStorageMigrator.java (2 methods)
- ConcurrentAccessTester.java (5 methods)

## Migration Checklist

For each method with `catch (Exception e)`:

### Step 1: Import ErrorHandler
```java
import com.betterdairy.autodense.util.ErrorHandler;
```

### Step 2: Identify Likely Exception Types
- Review method logic for potential failure points
- Check ImageJ/ImagePlus operations → `NullPointerException`
- Check parameter validation → `IllegalArgumentException`  
- Check session operations → `IllegalStateException`
- Check file operations → `IOException`

### Step 3: Replace Generic Catch Block
Use specific error handlers based on likely exceptions:

```java
} catch (IllegalArgumentException e) {
    return ErrorHandler.handleValidationError("method_name", e, logger, recovery);
} catch (NullPointerException e) {
    return ErrorHandler.handleImageProcessingError("method_name", e, logger, recovery);
} catch (IllegalStateException e) {
    return ErrorHandler.handleSessionError("method_name", e, logger, recovery);
} catch (java.io.IOException e) {  // Only if method does file I/O
    return ErrorHandler.handleFileError("method_name", e, logger, recovery);
} catch (RuntimeException e) {
    return ErrorHandler.handleUnexpectedError("method_name", e, logger, recovery);
}
```

### Step 4: Test Error Scenarios
- Invalid parameters
- Missing handles
- Closed images
- File permission issues

## Benefits After Refactoring

### For Users
- **Clear error messages** explaining what went wrong
- **Specific recovery suggestions** instead of generic failures
- **Better debugging information** when reporting issues

### For Developers  
- **Easier troubleshooting** with specific error types
- **Stack traces preserved** in comprehensive logs
- **Error metrics** for monitoring system health
- **Structured error responses** for programmatic handling

### For System Stability
- **Expected errors handled gracefully** instead of generic fallback
- **Better error recovery** based on specific failure types
- **Monitoring capabilities** to detect patterns

## Error Response Structure

The new error responses include:
```json
{
    "ok": false,
    "tool": "tool_name",
    "error_type": "VALIDATION_ERROR",
    "error_class": "IllegalArgumentException", 
    "message": "Invalid parameter: handle not found",
    "timestamp": "2025-08-27T14:30:00Z",
    "stack_trace": ["first 5 stack frames..."],
    "recovery_actions": {
        "check_session_store": true,
        "suggestion": "Verify the image handle is valid and the image exists in session"
    },
    "legacy_recovery": { /* existing recovery response */ }
}
```

## Metrics and Monitoring

The ErrorHandler tracks:
- **Error counts by tool and type**
- **Error patterns over time**
- **Most common failure scenarios**

Access via: `ErrorHandler.getErrorStats()`

## Next Steps

1. **Systematic Refactoring**: Apply pattern to all 79+ generic catch blocks
2. **Testing**: Verify error scenarios produce helpful messages
3. **Monitoring**: Set up error metrics dashboard
4. **Documentation**: Update tool documentation with error handling info

## Success Metrics

### Before Refactoring
- Generic "An error occurred" messages
- Lost stack trace information
- Difficult debugging
- No error pattern visibility

### After Refactoring
- Specific error types with context
- Full logging with stack traces
- Clear recovery guidance
- Error metrics for monitoring

---

**Implementation Time Estimate**: 2-3 days for systematic refactoring of all tool methods

**Risk Level**: Low - maintains backward compatibility while improving error handling

**Testing Strategy**: Error injection tests for each error type category