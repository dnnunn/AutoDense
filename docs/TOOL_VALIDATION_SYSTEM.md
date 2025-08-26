# Tool Validation and Response System

## Overview

This document describes the standardized tool validation and response system implemented in AutoDense, including the ToolSchemaValidator utility and simplified response methods.

## ToolSchemaValidator Utility

### Purpose
The `ToolSchemaValidator` class provides centralized parameter validation and normalization for all Gemini tool calls, ensuring consistent input validation across the system.

### Location
`autodense/plugin/src/main/java/com/betterdairy/autodense/plugin/ToolSchemaValidator.java`

### Key Methods

#### `require(JSONObject o, String key)`
Validates that a required field exists and is not null.
- **Throws**: `IllegalArgumentException` with message "Missing {key}" if validation fails
- **Usage**: Called at the start of tool methods to validate required parameters

```java
public static void require(JSONObject o, String key) {
    if (!o.has(key) || o.isNull(key)) 
        throw new IllegalArgumentException("Missing " + key);
}
```

#### `requireImageHandle(JSONObject args)`
Specialized validation for the `image_handle` parameter used by most tools.
- **Purpose**: Ensures image-based operations have a valid image reference
- **Usage**: First validation call in most tool methods

```java
public static void requireImageHandle(JSONObject args) {
    if (!args.has("image_handle") || args.isNull("image_handle"))
        throw new IllegalArgumentException("Missing required field: image_handle");
}
```

#### `requireArray(JSONObject args, String key)`
Validates that a field exists and is a JSONArray.
- **Use case**: Parameters like `export_formats`, `steps`, `color_groups`
- **Throws**: `IllegalArgumentException` if field is missing or not an array

```java
public static void requireArray(JSONObject args, String key) {
    if (!args.has(key) || !(args.get(key) instanceof JSONArray))
        throw new IllegalArgumentException("Field '" + key + "' must be an array");
}
```

#### `clamp(JSONObject obj, String key, double min, double max)`
Constrains numeric values to valid ranges, modifying the JSONObject in place.
- **Purpose**: Prevent invalid parameter values from reaching ImageJ
- **Behavior**: Silently clamps values to [min, max] range if they exist

```java
public static void clamp(JSONObject obj, String key, double min, double max) {
    if (!obj.has(key)) return;
    double v = obj.getNumber(key).doubleValue();
    double c = Math.max(min, Math.min(max, v));
    if (c != v) obj.put(key, c);
}
```

### Usage Pattern

All tool methods now follow this pattern:

```java
public JSONObject toolMethod(JSONObject args) {
    // 1. Validate required parameters first
    ToolSchemaValidator.requireImageHandle(args);
    ToolSchemaValidator.require(args, "required_param");
    ToolSchemaValidator.requireArray(args, "array_param");
    
    // 2. Clamp numeric parameters to valid ranges
    ToolSchemaValidator.clamp(args, "radius", 1.0, 100.0);
    
    // 3. Proceed with tool logic
    try {
        // ... tool implementation ...
        return ok("tool_name", result);
    } catch (Exception e) {
        return fail("error_code", e.getMessage(), "parameter");
    }
}
```

## Simplified Response Methods

### Before: Verbose Response Methods

The original system used verbose methods with repetitive JSON construction:

```java
private JSONObject toolSuccess(String tool, JSONObject data) {
    return new JSONObject()
        .put("success", true)
        .put("tool", tool)
        .put("data", data)
        .put("warnings", new JSONArray());
}

private JSONObject toolFailure(String code, String message, String param) {
    return new JSONObject()
        .put("success", false)
        .put("error", new JSONObject()
            .put("code", code)
            .put("message", message)
            .put("param", param));
}
```

### After: Concise Response Methods

New simplified methods reduce boilerplate and standardize response format:

```java
private JSONObject ok(String tool, JSONObject data) {
    return new JSONObject()
        .put("ok", true)
        .put("tool", tool)
        .put("data", data)
        .put("warnings", new JSONArray());
}

private JSONObject fail(String code, String msg, String param) {
    return new JSONObject()
        .put("ok", false)
        .put("error", new JSONObject()
            .put("code", code)
            .put("message", msg)
            .put("param", param));
}
```

### Response Format Standardization

#### Success Response
```json
{
  "ok": true,
  "tool": "detect_bands",
  "data": {
    "bands_total": 24,
    "image_handle": "img_abc123"
  },
  "warnings": []
}
```

#### Error Response
```json
{
  "ok": false,
  "error": {
    "code": "missing_required_field",
    "message": "Missing required field: image_handle",
    "param": "image_handle"
  }
}
```

## Error Codes

### Standard Error Codes
- `missing_required_field`: Required parameter is missing or null
- `invalid_param`: Parameter value is invalid or out of range
- `image_not_found`: Image handle not found in SessionStore
- `overlay_not_found`: Overlay handle not found in SessionStore
- `image_state_conflict`: Operation requires different image state
- `ij_runtime_error`: ImageJ runtime error occurred

### Usage in Tools
```java
// Parameter validation errors
return fail(ERROR_MISSING_REQUIRED_FIELD, "Missing required field: path", "path");

// Runtime errors
return fail(ERROR_IJ_RUNTIME_ERROR, "ImageJ preprocessing error: " + e.getMessage(), "preprocessing");

// State conflicts
return fail(ERROR_IMAGE_STATE_CONFLICT, "No lanes detected yet - run detect_lanes first", "overlay");
```

## Implementation Status

### Tools Updated (11 total)
All tool methods in `GelAnalysisTools.java` have been updated:

1. **openImage**: `require(args, "path")`
2. **preprocess**: `requireImageHandle(args)`
3. **detectLanes**: `requireImageHandle(args)`
4. **detectBands**: `requireImageHandle(args)`
5. **adjustLanes**: `requireImageHandle(args)`
6. **renderOverlayPng**: `requireImageHandle(args)`
7. **quantifyBands**: `requireImageHandle(args)`
8. **exportResults**: `requireImageHandle(args)` + `requireArray(args, "export_formats")`
9. **detectColonies**: `requireImageHandle(args)`
10. **enableBandAssist**: `requireImageHandle(args)`
11. **disableBandAssist**: `requireImageHandle(args)`

### Method Calls Updated
- **25+ toolSuccess calls** → `ok()` method
- **20+ toolFailure calls** → `fail()` method
- **Zero compilation errors** after refactoring

## Benefits

### For Developers
- **Consistent validation**: All tools use same validation pattern
- **Better error messages**: Specific parameter names in errors
- **Type safety**: Validates JSON structure before use
- **Reduced boilerplate**: Shorter response method calls

### For Users
- **Clear error feedback**: Specific parameter names in error messages
- **Automatic parameter correction**: Clamping prevents invalid values
- **Consistent API**: All tools follow same response format

### For Gemini Integration
- **Self-correction capability**: Error codes help Gemini understand issues
- **Parameter guidance**: Clamp operations show valid ranges
- **Structured responses**: Consistent JSON format for parsing

## Future Enhancements

### Planned Additions
1. **Schema validation**: JSON schema-based parameter validation
2. **Range documentation**: Automatic documentation of valid parameter ranges
3. **Type coercion**: Automatic conversion between compatible types
4. **Validation caching**: Cache validation results for repeated calls

### Integration Opportunities
1. **Tool schema generation**: Automatic tool schema creation for Gemini
2. **Documentation generation**: Auto-generate tool documentation from validation rules
3. **Testing framework**: Validation-based test case generation