# Input Validation Security Guide

**Phase 4.2 Implementation** - Addresses audit finding: "Weak validation allows malformed inputs"

## Critical Security Issues Identified

### 🚨 Path Traversal Vulnerability (Critical)
**Location**: `GelAnalysisTools.openImage()`  
**Issue**: Direct use of user input as file path without validation
```java
// VULNERABLE CODE:
String path = args.getString("path");
ImagePlus imp = IJ.openImage(path); // Can access any file!
```

**Attack Examples**:
- `../../../etc/passwd` - Access system files
- `C:\Windows\System32\config\sam` - Access Windows system files  
- `file:///proc/version` - Access system information
- `\\server\share\sensitive.txt` - Network file access

**Impact**: Complete filesystem access, data exfiltration, system compromise

### 🔍 Input Validation Weaknesses
1. **No parameter range validation** - Integer overflow, extreme values
2. **No file extension filtering** - Arbitrary file types  
3. **No handle format validation** - Malformed handle injection
4. **No array size limits** - Memory exhaustion attacks
5. **No string length limits** - Buffer overflow potential

## Security Solution: Comprehensive Validation Framework

### New Security Classes

**1. InputValidator.java** - Core validation utilities
- Path traversal prevention
- File extension whitelisting  
- Handle format validation
- Parameter range enforcement
- Input sanitization

**2. SecureToolValidator.java** - Enhanced tool validation
- Backward compatible with ToolSchemaValidator
- Integrated security checks
- Parameter schemas for common patterns

### Security Features Implemented

#### Path Security
```java
// SECURE CODE:
SecureToolValidator.Tools.validateOpenImage(args);
String path = args.getString("path"); // Now validated and sanitized
```

**Protection Against**:
- Directory traversal (`../`, `..\\`, `/..`, etc.)
- Double slashes (`//`, `\/`, `/\\`)
- Null bytes and control characters
- Invalid file extensions
- Non-existent files

#### Handle Validation
```java
// Format enforcement:
validateImageHandle("img_123")     // ✓ Valid
validateImageHandle("malicious")   // ✗ Invalid format  
validateImageHandle("img_../etc")  // ✗ Invalid characters
```

#### Parameter Range Validation
```java
// Prevent extreme values:
requireInt("expected_lanes", 1, 50)      // 1-50 lanes max
requireDouble("sensitivity", 0.1, 2.0)   // Reasonable sensitivity  
requireArray("steps", 1, 20)             // Limit processing steps
```

### Validation Schemas

**Common Tool Patterns**:
```java
// Lane detection with full validation
SecureToolValidator.Tools.validateDetectLanes(args);
// - Validates image handle format
// - Enforces lane count limits (1-50)  
// - Validates sensitivity range (0.1-2.0)
// - Checks parameter types and ranges

// Preprocessing with operation limits  
SecureToolValidator.Tools.validatePreprocess(args);
// - Limits preprocessing steps (max 20)
// - Validates each operation type
// - Enforces parameter ranges per operation
// - Prevents malicious operation chains
```

## Migration Guide

### Step 1: Import Security Classes
```java
import com.betterdairy.autodense.validation.SecureToolValidator;
import com.betterdairy.autodense.validation.InputValidator;
```

### Step 2: Replace Basic Validation

**Before**:
```java
public JSONObject toolMethod(JSONObject args) {
    ToolSchemaValidator.require(args, "path");
    String path = args.getString("path"); // VULNERABLE
}
```

**After**:
```java
public JSONObject toolMethod(JSONObject args) {
    SecureToolValidator.requireSecureFilePath(args, "path", true, false);
    String path = args.getString("path"); // SECURE - validated and sanitized
}
```

### Step 3: Use Tool-Specific Validators

**Lane Detection**:
```java
// Replace multiple ToolSchemaValidator calls with:
SecureToolValidator.Tools.validateDetectLanes(args);
```

**Export Operations**:
```java
// Secure file path validation for exports:
SecureToolValidator.Tools.validateExport(args);
```

**Preprocessing**:
```java  
// Comprehensive preprocessing validation:
SecureToolValidator.Tools.validatePreprocess(args);
```

## Security Testing

### Test Cases for Path Traversal
```java
@Test
void testPathTraversalPrevention() {
    JSONObject args = new JSONObject().put("path", "../../../etc/passwd");
    assertThrows(ValidationException.class, () -> 
        SecureToolValidator.Tools.validateOpenImage(args));
}

@Test  
void testFileExtensionFiltering() {
    JSONObject args = new JSONObject().put("path", "malware.exe");
    assertThrows(ValidationException.class, () ->
        SecureToolValidator.Tools.validateOpenImage(args));
}
```

### Test Cases for Handle Validation
```java
@Test
void testHandleFormatValidation() {
    JSONObject args = new JSONObject().put("image_handle", "malicious_handle");
    assertThrows(ValidationException.class, () ->
        SecureToolValidator.requireImageHandle(args));
}
```

## Implementation Status

### ✅ Completed
- Created comprehensive validation framework
- Implemented path traversal protection
- Added handle format validation  
- Created parameter range enforcement
- Demonstrated security fixes in key methods:
  - `GelAnalysisTools.openImage()` - Path security
  - `GelAnalysisTools.detectLanes()` - Parameter validation
  - `GelAnalysisTools.preprocess()` - Operation limits

### 🔄 In Progress  
- Documentation and migration guide
- Security testing framework

### ⏳ Remaining Work

**High Priority Tools (Security Critical)**:
1. **All file operations** - Export, import, save methods
2. **All external input handlers** - User commands, file paths
3. **Parameter processing** - All numeric and string inputs
4. **Array processing** - All JSONArray parameters

**Medium Priority**:
- Session management tools  
- Overlay operations
- Analysis data handling

## Security Benefits

### Before Implementation
- **Path traversal vulnerability** - Complete filesystem access
- **No input validation** - Integer overflow, memory exhaustion
- **No file filtering** - Arbitrary file access  
- **Handle injection** - Malformed handles cause crashes

### After Implementation
- **Path traversal blocked** - Only allowed directories accessible
- **Range validation** - Prevents overflow and extreme values
- **File extension filtering** - Only safe image formats allowed
- **Handle format enforcement** - Prevents malformed handle injection
- **Array size limits** - Prevents memory exhaustion attacks
- **String length limits** - Prevents buffer overflow potential

## Deployment Strategy

### Phase 1: Critical Security Fixes
- Deploy path traversal protection immediately
- Fix all file operation methods
- Add handle validation to session operations

### Phase 2: Parameter Validation
- Add range validation to all numeric parameters
- Implement array size limits  
- Add string length validation

### Phase 3: Comprehensive Coverage
- Apply validation to all remaining tools
- Add comprehensive security testing
- Monitor for validation bypass attempts

## Risk Assessment

### High Risk - Immediate Action Required
- **Path traversal** - Can access any file on system
- **File extension bypass** - Can execute arbitrary files
- **Handle injection** - Can crash or compromise session

### Medium Risk - Address in Phase 2  
- **Integer overflow** - Can cause unexpected behavior
- **Memory exhaustion** - Large arrays can crash system
- **String buffer issues** - Very long strings can cause problems

### Low Risk - Monitor and Address  
- **Parameter type mismatches** - Usually caught by JSON parsing
- **Missing optional parameters** - Generally handled gracefully

## Monitoring and Alerting

### Security Metrics to Track
- **Validation failures** - Count of blocked malicious inputs
- **Path traversal attempts** - Security incident indicators
- **Invalid handle formats** - Potential attack patterns
- **Extreme parameter values** - Unusual usage patterns

### Alert Thresholds
- **>5 validation failures/hour** - Potential automated attack
- **Any path traversal attempt** - Immediate security alert  
- **>100 invalid handles/day** - Possible reconnaissance
- **Extreme parameter clusters** - Potential abuse

---

**Implementation Priority**: Critical - Deploy path traversal fixes immediately  
**Security Level**: High - Addresses multiple vulnerability classes  
**Testing Required**: Extensive - Security validation in all attack scenarios