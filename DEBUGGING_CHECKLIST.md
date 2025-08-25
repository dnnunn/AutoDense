# AutoDense Debugging Checklist
**Next Session Priorities**

## 🎯 **Immediate Debug Tasks (Start Here Tomorrow)**

### **1. Result Communication Debug [FIRST PRIORITY]** 
**Problem**: Tools execute successfully but UI shows generic "Tool executed successfully"

**Debug Steps**:
1. **Start AutoDense** with debug logging enabled
2. **Load test image**: IMG_9715.jpg (gel image)
3. **Run command**: "analyze this gel with 12 lanes"  
4. **Check console output** for:
   ```
   DEBUG: Tool result JSON: {
     "lanes_found": 12,
     "bands_total": 0,    // <-- This should be > 0
     "png_path": "/tmp/gel_overlay_123456.png",
     "success": true
   }
   ```

**Expected Issues**:
- `toolResult` might be null or empty
- JSON structure might not match UI parsing code
- `bands_total` likely 0 (band detection failing)

**Fix Locations**:
- `GelUI.java:478` - Added debug logging (needs testing)
- `GelUI.java:491-515` - Result parsing logic
- `GeminiOrchestrator.java:334-360` - Auto PNG generation

### **2. Band Detection Investigation [SECOND PRIORITY]**
**Problem**: Lane detection works but no protein bands detected

**Debug Steps**:
1. **Check PNG overlay file**: Does it show lane markers (L1-L12) but no band markers (B1, B2)?
2. **Test sensitivity parameters**:
   ```
   Command: "detect protein bands with high sensitivity"
   Expected Gemini JSON: {"action": "detect_bands", "parameters": {"sensitivity": 0.3}}
   ```
3. **Check tool execution chain**: 
   - Does `analyze_gel` call `detect_bands`?
   - Does `detect_bands` call `renderOverlayPng`?
   - Are band overlays being added to the image?

**Files to Check**:
- `CanonicalTools.java:93` - `analyze_gel` workflow
- `GelAnalysisTools.java:349` - `detectBands` implementation  
- `GelAnalysisTools.java:552` - `renderOverlayPng` overlay generation

### **3. PNG File Access [THIRD PRIORITY]**
**Problem**: Users can't easily find/open generated PNG files

**Current State**: Files saved to `/tmp/gel_overlay_[timestamp].png`

**Quick Wins**:
1. **Verify file paths in UI**: Should show full path to PNG file
2. **Test file accessibility**: Can user navigate to and open the PNG?
3. **Consider auto-opening**: Should PNG open in ImageJ automatically?

## 🧪 **Testing Commands (Copy/Paste Ready)**

### **Basic Workflow Tests**
```bash
# Start AutoDense (with debug logging)
export GEMINI_API_KEY="AIzaSyAAaCtNbMtDhutBUT1R9ZbN7n62jZ7Ynwk" 
mvn -q -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.plugin.EnhancedImageJLauncher \
  -Dexec.classpathScope=runtime
```

### **User Commands to Test**
```
# Complete workflow
"analyze this gel with 12 lanes"

# Individual steps  
"detect 12 lanes"
"detect protein bands"
"detect protein bands with high sensitivity"

# Adjustments
"adjust lanes left 5 pixels" 
"offset the lanes to the right"

# Colony analysis (should now work)
"count blue and white colonies"
```

### **Expected vs Actual Results**
| Command | Expected Result | Current Result | Status |
|---------|----------------|---------------|---------|
| "analyze this gel with 12 lanes" | ✅ Detected 12 lanes, X bands<br>📏 Lanes: 12<br>🧬 Bands: X<br>🖼️ PNG saved to: path | ✅ Tool executed successfully | ❌ BROKEN |
| "detect protein bands" | ✅ Detected X protein bands<br>🖼️ PNG saved to: path | ✅ Tool executed successfully | ❌ BROKEN |
| PNG overlay content | Lane markers: L1-L12<br>Band markers: B1, B2, B3... | Lane markers only, no bands | ❌ PARTIAL |

## 🔧 **Debug Code Snippets**

### **Add More Detailed Logging** 
```java
// In GelUI.java processCommand method (around line 478)
if (orchestrationResult.toolResult != null) {
    System.out.println("DEBUG: Tool result JSON: " + orchestrationResult.toolResult.toString(2));
    System.out.println("DEBUG: Has lanes_found: " + orchestrationResult.toolResult.has("lanes_found"));
    System.out.println("DEBUG: Has bands_total: " + orchestrationResult.toolResult.has("bands_total"));
    System.out.println("DEBUG: Has png_path: " + orchestrationResult.toolResult.has("png_path"));
} else {
    System.out.println("DEBUG: toolResult is NULL - this is the problem!");
}
```

### **Check PNG File Contents**
```java
// Verify PNG file was created and is accessible
Path pngPath = Paths.get(orchestrationResult.toolResult.getString("png_path"));
System.out.println("DEBUG: PNG exists: " + Files.exists(pngPath));
System.out.println("DEBUG: PNG size: " + Files.size(pngPath) + " bytes");
```

### **Trace Tool Execution**
```java  
// In GeminiOrchestrator.executeTool method (around line 334)
System.out.println("DEBUG: About to execute tool: " + toolName);
System.out.println("DEBUG: Tool parameters: " + parameters.toString(2));
JSONObject result = // ... tool execution
System.out.println("DEBUG: Tool result: " + result.toString(2));
```

## 📋 **Success Criteria (How to Know It's Fixed)**

### **Result Communication Fixed**
- [ ] UI shows "✅ Detected 12 lanes successfully" (not generic message)
- [ ] UI shows "🧬 Bands: X" where X > 0  
- [ ] UI shows "🖼️ PNG saved to: /full/path/to/file.png"
- [ ] Console shows detailed JSON with lanes_found, bands_total, png_path

### **Band Detection Working**
- [ ] PNG overlay file shows both lane markers (L1-L12) AND band markers (B1, B2, B3...)
- [ ] bands_total > 0 in tool results
- [ ] Different sensitivity values affect number of detected bands

### **User Experience Acceptable**
- [ ] User can easily find and open PNG overlay files
- [ ] Lane adjustment commands work and show immediate visual feedback
- [ ] Colony counting commands work (no more "not implemented" errors)

## 🚨 **Red Flags to Watch For**

### **Architectural Regression**
- Gemini starts doing vision analysis again ("I see X lanes")
- Tool calls return "not implemented" errors  
- System prompt violations in Gemini responses

### **Performance Issues**
- Tool execution times > 10 seconds
- Memory usage growing indefinitely 
- PNG files getting corrupted or extremely large

### **Integration Failures**  
- ImageJ UI becomes unresponsive
- Exception stack traces in console
- API key authentication failures

## 📂 **Quick Reference: Key Files**

```
# Architecture  
autodense/plugin/src/main/java/com/betterdairy/autodense/orchestrator/GeminiOrchestrator.java
autodense/plugin/src/main/java/com/betterdairy/autodense/tools/CanonicalTools.java  
autodense/plugin/src/main/java/com/betterdairy/autodense/plugin/GelUI.java

# Analysis Tools
autodense/plugin/src/main/java/com/betterdairy/autodense/tools/GelAnalysisTools.java
autodense/plugin/src/main/java/com/betterdairy/autodense/tools/PlateAnalysisTools.java  
autodense/plugin/src/main/java/com/betterdairy/autodense/tools/ColonyAnalysisTools.java

# Documentation
SESSION_SUMMARY_2025-08-25.md (this session's findings)
CLAUDE.md (architectural guidelines)
```

**Ready to resume debugging tomorrow. Start with Result Communication Debug - it's the key to unlocking visibility into what's actually happening.**