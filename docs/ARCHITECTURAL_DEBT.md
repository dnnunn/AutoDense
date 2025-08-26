# Architectural Debt - Known Issues for Future Cleanup

## Overview
This document tracks known architectural violations that may cause issues during pressure testing. These are documented but not immediately fixed to avoid disrupting the working system.

## 1. Scattered ImageJ Processing in Deprecated Tools

### Issue
Deprecated tool classes contain direct `IJ.run()` and `IJ.setAutoThreshold()` calls instead of delegating to analysis classes.

### Violations Found
- **PlateAnalysisTools.java**: 15+ direct IJ calls for thresholding, watershed, particle analysis
- **GelAnalysisTools.java**: Direct `IJ.openImage()` call

### Architectural Intent
```
Tools (Orchestration) → Analysis Classes (ImageJ Processing)
```

### Current Violation
```
Tools (Direct IJ Processing) ❌
```

### Impact During Pressure Testing
- **Thread safety**: Direct IJ calls may not be thread-safe
- **State conflicts**: ImageJ global state pollution between operations  
- **Error isolation**: Failures in one tool affect others
- **Resource leaks**: Unclosed ImagePlus objects from direct calls

### Resolution Strategy
1. **Short term**: Use canonical tools (`analyze_plate`, `export_plate`) which properly delegate
2. **Long term**: Remove deprecated tools entirely once migration complete
3. **If issues arise**: Refactor specific problematic methods to use analysis classes

### Files Affected
```
/tools/PlateAnalysisTools.java - Lines 114,118,119,122,125,126,224,234,237,238,243,244,252,253,1166,1169,1170,1176,1250,1256,1257
/tools/GelAnalysisTools.java - Line 120
```

## 2. Parameter Clamping in Deprecated Tools

### Issue
Deprecated tools still do inline parameter clamping instead of using ParameterValidator.

### Impact
- Inconsistent parameter ranges across tools
- Duplicate validation logic
- No centralized audit trail of parameter adjustments

### Resolution
- Already partially addressed with ParameterValidator
- Deprecated tools keep legacy clamping for backward compatibility
- New canonical tools use proper ParameterValidator

## 3. Session-Scoped Deprecation Logging

### Status
✅ **RESOLVED** - SessionLogger now tracks and logs deprecated tool usage once per session.

## Monitoring During Pressure Testing

Watch for these symptoms that indicate the architectural debt is causing problems:

1. **ImageJ State Conflicts**
   - Error: "Image window not found"
   - Error: "Current image required but none available"
   - Overlays appearing on wrong images

2. **Thread Safety Issues**
   - Concurrent modification exceptions
   - Deadlocks during parallel tool execution
   - Inconsistent results from identical operations

3. **Resource Leaks**
   - Memory usage growing over time
   - Too many ImagePlus windows open
   - File handles not released

4. **Performance Degradation**
   - Slower response times after extended use
   - Tool operations blocking each other
   - ImageJ UI becoming unresponsive

## Recommended Testing Approach

1. **Favor Canonical Tools**: Use `analyze_gel`, `analyze_plate` etc. over deprecated tools
2. **Monitor Session Logs**: Check for deprecation warnings indicating legacy tool usage
3. **Watch ImageJ Console**: Monitor for ImageJ errors during extended sessions
4. **Resource Monitoring**: Track memory and file handle usage over time

---

*Last Updated: August 2025*  
*Created during architectural guardrail implementation*