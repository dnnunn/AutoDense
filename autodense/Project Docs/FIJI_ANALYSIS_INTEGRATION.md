# Fiji Gel Analysis Integration Guide

## Overview
This document catalogs existing gel analysis functionality found in the Fiji installation and provides an integration roadmap for AutoDense. These proven methods can be directly incorporated into our natural language processing pipeline and enhance our LLM's understanding of gel analysis workflows.

## Primary Discovery: _BandPeakQuantification.ijm

### Source Information
- **Author**: Kenji OHGANE (University of Tokyo)
- **Based on**: Image Studio Lite's gel quantification function
- **Location**: `/Applications/Fiji/plugins/_BandPeakQuantification.ijm`
- **Language**: ImageJ Macro Language (IJM)

### Core Functionality

#### Background Estimation Methods
```ijm
// Three background region options
backpos = "all" | "top/bottom" | "sides"

// Two statistical methods
backtype = "median" | "mean"

// Configurable expansion
expand = 3; // pixels (default)
```

#### Quantification Calculations
```ijm
signal = area * (mean - mean_background)
total = area * mean
// Plus: area, mean, background, ROI bounds
```

#### Advanced Background Region Logic

**1. "All" Region (Default)**
- Uses `run("Make Band...", "band="+expand)`
- Creates expanded border around entire ROI
- Most general approach, works with any ROI shape

**2. "Top/Bottom" Region (Rectangle only)**
```ijm
makePolygon(x, y-expand, x+w, y-expand,
           x+w, y, x, y,
           x, y+h, x+w, y+h,
           x+w, y+h+expand, x, y+h+expand,
           x, y-expand);
```
- Creates background regions above and below ROI
- Ideal for horizontal gel bands

**3. "Sides" Region (Rectangle only)**
```ijm
makePolygon(x-expand, y, x-expand, y+h,
           x, y+h, x, y,
           x+w, y, x+w, y+h,
           x+w+expand,y+h, x+w+expand, y,
           x-expand, y);
```
- Creates background regions left and right of ROI
- Ideal for vertical gel lanes

#### ROI Management Integration
- **Batch Processing**: Works with ROI Manager for multiple bands
- **Fallback**: Single ROI if ROI Manager empty
- **Shape Support**: Any ROI shape (with appropriate background method)
- **Scale Reset**: Optional scale normalization

### Integration Value for AutoDense

#### 1. **Enhanced BandDetector Class**
```java
public class BandDetector {
    public enum BackgroundRegion { ALL, TOP_BOTTOM, SIDES }
    public enum BackgroundMethod { MEDIAN, MEAN }
    
    public BandQuantification quantifyBand(Roi roi, BackgroundRegion region, 
                                          BackgroundMethod method, int expansion) {
        // Implement Fiji's proven algorithms
    }
}
```

#### 2. **Natural Language Command Extensions**
```json
{
  "action": "quantify_bands",
  "parameters": {
    "background_region": "sides",
    "background_method": "median",
    "expansion_pixels": 3,
    "reset_scale": true
  }
}
```

#### 3. **LLM Knowledge Enhancement**
The LLM can now reference specific, proven methodologies:
- "Use side background estimation for vertical lanes"
- "Apply median background for robust noise handling"
- "Expand background by 3 pixels for better baseline"

## Additional Fiji Analysis Tools

### 1. Extended_Profile_Plot.bsh

**Location**: `/Applications/Fiji/plugins/Examples/Extended_Profile_Plot.bsh`
**Language**: BeanShell
**Purpose**: Enhanced profile plotting with polygon support

#### Key Features
```bsh
// Converts any ROI to splined polyline for profiling
polygon = roi.getPolygon();
polygon.addPoint(polygon.xpoints[0], polygon.ypoints[0]);
roi = new PolygonRoi(polygon, roi.POLYLINE);
roi.fitSpline();
new ProfilePlot(dummyImage, true).createWindow();
```

#### Integration Opportunity
- **Enhanced Lane Profiling**: Convert lane ROIs to smooth splines
- **Natural Language**: "Generate smooth profile plot for this lane"
- **Implementation**: Add spline fitting to our LaneDetector

### 2. Dynamic_ROI_Profiler.clj

**Location**: `/Applications/Fiji/plugins/Analyze/Dynamic_ROI_Profiler.clj`
**Language**: Clojure
**Author**: Albert Cardona (2008)
**Purpose**: Real-time profile updates as ROI moves

#### Key Features
```clojure
; Real-time profile plotting
(defn- update [plot-win imp]
  (let [roi (.getRoi imp)]
    ; Update plot as ROI changes
))

; Multi-threaded execution
exec (Executors/newFixedThreadPool 1)

; Mouse interaction
mouseDragged [event]
  (.submit exec #(update plot-win imp))
```

#### Integration Opportunity
- **Interactive Band Optimization**: Real-time feedback during band adjustment
- **Natural Language**: "Show live profile as I adjust this band"
- **Implementation**: Add to our interactive optimization dialogs

### 3. Measure_RGB.txt

**Location**: `/Applications/Fiji/plugins/Analyze/Measure_RGB.txt`
**Language**: ImageJ Macro
**Purpose**: Separate RGB channel measurement

#### Key Features
```ijm
// Separate channel analysis
setRGBWeights(1, 0, 0);  // Red only
setRGBWeights(0, 1, 0);  // Green only
setRGBWeights(0, 0, 1);  // Blue only

// Standard luminance weights
setRGBWeights(0.299, 0.587, 0.114);
```

#### Integration Opportunity
- **Multi-channel Gel Analysis**: Fluorescent or multi-stain gels
- **Natural Language**: "Analyze red channel only" or "Use standard luminance weights"
- **Implementation**: Add channel selection to our analysis pipeline

## Implementation Roadmap

### Phase 1: Core Integration (Immediate)

#### 1.1 Enhanced BandDetector
```java
// File: autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/BandDetector.java

public class BandQuantification {
    public double signal;        // area * (mean - background)
    public double total;         // area * mean
    public double area;
    public double mean;
    public double background;
    public BackgroundMethod backgroundMethod;
    public Rectangle bounds;
}

public BandQuantification quantifyBand(ImagePlus imp, Roi roi, 
                                     BackgroundRegion region, 
                                     BackgroundMethod method, 
                                     int expansionPixels) {
    // Implement Fiji's algorithms directly
}
```

#### 1.2 Natural Language Schema Updates
```json
// File: autodense/nl/src/main/resources/intent.schema.json
{
  "quantify_bands": {
    "properties": {
      "background_region": {"enum": ["all", "top_bottom", "sides"]},
      "background_method": {"enum": ["median", "mean"]},
      "expansion_pixels": {"type": "integer", "default": 3},
      "reset_scale": {"type": "boolean", "default": true}
    }
  }
}
```

#### 1.3 LLM Prompt Enhancement
```java
// File: autodense/nl/src/main/java/com/betterdairy/autodense/nl/NLClient.java

private String buildSystemPrompt(NLContext ctx) {
    return """
    Available background estimation methods:
    - "all": Expands around entire ROI (works with any shape)
    - "top_bottom": Above/below ROI (best for horizontal bands)
    - "sides": Left/right of ROI (best for vertical lanes)
    
    Statistical methods:
    - "median": More robust to noise and outliers
    - "mean": Standard average, faster computation
    
    Example commands:
    - "Quantify bands using side background with median estimation"
    - "Use 5-pixel background expansion for better baseline"
    """;
}
```

### Phase 2: Advanced Features (Next Sprint)

#### 2.1 Interactive Profile Plotting
- Real-time profile updates during band adjustment
- Spline-fitted profile plots for smoother visualization
- Multi-threaded profile computation

#### 2.2 Multi-channel Support
- RGB channel separation for fluorescent gels
- Standard luminance weighting options
- Channel-specific quantification

#### 2.3 ROI Manager Integration
- Batch processing for multiple bands
- ROI persistence and management
- Import/export ROI sets

### Phase 3: Production Enhancement (Future)

#### 3.1 Advanced Background Methods
- Polynomial baseline fitting
- Rolling ball background subtraction
- Local background estimation

#### 3.2 Statistical Enhancements
- Confidence intervals for quantification
- Background noise estimation
- Signal-to-noise ratio calculations

#### 3.3 Export Integration
- Include background method in CSV exports
- Document quantification parameters in reports
- Reproducibility metadata

## LLM Command Examples

### Basic Quantification
```
User: "Quantify all detected bands using median background"
LLM: {
  "intent": "multi_action",
  "actions": [{
    "action": "quantify_bands",
    "background_region": "all",
    "background_method": "median",
    "expansion_pixels": 3
  }]
}
```

### Advanced Parameters
```
User: "Use side background regions with 5-pixel expansion for vertical lane analysis"
LLM: {
  "intent": "multi_action", 
  "actions": [{
    "action": "quantify_bands",
    "background_region": "sides",
    "background_method": "median", 
    "expansion_pixels": 5,
    "reset_scale": true
  }]
}
```

### Method Selection Guidance
```
User: "What's the best background method for noisy gels?"
LLM: {
  "intent": "guidance",
  "recommendation": "Use median background estimation with 'all' region for noisy gels. Median is more robust to outliers than mean, and the 'all' region provides comprehensive background sampling around each band."
}
```

## Technical Implementation Notes

### ImageJ Integration Points
1. **Existing Commands**: `run("Make Band...", "band="+expand)`
2. **Measurement Functions**: `getValue("Median")`, `getValue("Mean")`
3. **ROI Management**: `roiManager("Select", k)`, `Roi.getBounds()`
4. **Results Integration**: `setResult()`, `updateResults()`

### Performance Considerations
1. **Background ROI Creation**: Pre-compute for batch operations
2. **Statistical Calculations**: Cache median/mean for repeated use
3. **Memory Management**: Process large gel images in tiles if needed

### Testing Strategy
1. **Unit Tests**: Each background method with synthetic data
2. **Integration Tests**: Compare with Fiji macro outputs
3. **Performance Tests**: Large gel images and batch processing
4. **Accuracy Tests**: Known standards and reference gels

## Benefits for AutoDense

### 1. **Proven Algorithms**
- Battle-tested in academic research
- Based on commercial software (Image Studio Lite)
- Eliminates need to develop from scratch

### 2. **Enhanced LLM Understanding**
- Concrete, specific methodologies to reference
- Clear parameter spaces for optimization
- Established best practices for different gel types

### 3. **User Familiarity**
- Methods users already know from Fiji
- Consistent results with existing workflows
- Easy migration path from manual analysis

### 4. **Extensibility**
- Foundation for advanced features
- Integration with existing ImageJ ecosystem
- Platform for custom algorithm development

This integration transforms AutoDense from a basic analysis tool into a comprehensive, AI-guided gel analysis platform with proven scientific foundations.