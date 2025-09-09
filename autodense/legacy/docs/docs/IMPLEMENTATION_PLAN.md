# AutoDense Implementation Plan: Fiji Integration

> **Doc Meta**
> - **Purpose:** Strategic plan for integrating Fiji's proven gel analysis algorithms
> - **Scope:** 6-week implementation roadmap and technical integration strategy
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## Executive Summary

Based on analysis of the Fiji installation, we have identified critical gel analysis functionality that can significantly enhance AutoDense's capabilities. This plan outlines the integration of proven algorithms from Fiji's `_BandPeakQuantification.ijm` and related tools into our AI-powered gel analysis platform.

## Strategic Value

### Immediate Benefits
- **Proven Algorithms**: 10+ years of academic use in gel analysis
- **Enhanced LLM Knowledge**: Concrete methodologies for natural language processing
- **User Familiarity**: Methods researchers already know and trust
- **Reduced Development Time**: Avoid reinventing established techniques

### Competitive Advantages  
- **AI-Guided Analysis**: Natural language control of sophisticated algorithms
- **Scientific Rigor**: University-developed, peer-reviewed methods
- **Offline Capability**: Complete functionality without internet dependency
- **Extensible Platform**: Foundation for future advanced features

## Phase 1: Core Algorithm Integration (Week 1-2)

### 1.1 Enhanced Band Quantification

#### File: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/BandQuantification.java`
```java
public class BandQuantification {
    // Results matching Fiji's _BandPeakQuantification.ijm
    private final double signal;           // area * (mean - background)
    private final double total;            // area * mean  
    private final double area;
    private final double mean;
    private final double background;
    private final BackgroundMethod method;
    private final Rectangle bounds;
    private final String roiName;
    
    // Constructor and getters
}

public enum BackgroundRegion {
    ALL,         // Expands around entire ROI
    TOP_BOTTOM,  // Above/below ROI (horizontal bands)
    SIDES        // Left/right of ROI (vertical lanes)
}

public enum BackgroundMethod {
    MEDIAN,      // Robust to outliers
    MEAN         // Standard average
}
```

#### File: `autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/BandDetector.java`
```java
public class BandDetector {
    
    public BandQuantification quantifyBand(ImagePlus imp, Roi roi, 
                                         BackgroundRegion region,
                                         BackgroundMethod method,
                                         int expansionPixels) {
        
        // Get ROI statistics
        imp.setRoi(roi);
        ImageStatistics stats = imp.getStatistics();
        double mean = stats.mean;
        double area = stats.area;
        Rectangle bounds = roi.getBounds();
        
        // Create background ROI based on region type
        Roi backgroundRoi = createBackgroundRoi(roi, region, expansionPixels);
        
        // Calculate background value
        imp.setRoi(backgroundRoi);
        ImageStatistics bgStats = imp.getStatistics();
        double background = (method == BackgroundMethod.MEDIAN) ? 
                          bgStats.median : bgStats.mean;
        
        // Calculate signal (Fiji's core formula)
        double signal = area * (mean - background);
        double total = area * mean;
        
        return new BandQuantification(signal, total, area, mean, 
                                    background, method, bounds, roi.getName());
    }
    
    private Roi createBackgroundRoi(Roi roi, BackgroundRegion region, int expand) {
        Rectangle bounds = roi.getBounds();
        int x = bounds.x, y = bounds.y, w = bounds.width, h = bounds.height;
        
        switch (region) {
            case ALL:
                // Use ImageJ's Make Band functionality
                return createBandRoi(roi, expand);
                
            case TOP_BOTTOM:
                // Create polygon above and below (Fiji algorithm)
                int[] xpoints = {x, x+w, x+w, x, x, x+w, x+w, x, x};
                int[] ypoints = {y-expand, y-expand, y, y, y+h, y+h, y+h+expand, y+h+expand, y-expand};
                return new PolygonRoi(xpoints, ypoints, xpoints.length, Roi.POLYGON);
                
            case SIDES:
                // Create polygon left and right (Fiji algorithm)
                int[] xpointsSides = {x-expand, x-expand, x, x, x+w, x+w, x+w+expand, x+w+expand, x-expand};
                int[] ypointsSides = {y, y+h, y+h, y, y, y+h, y+h, y, y};
                return new PolygonRoi(xpointsSides, ypointsSides, xpointsSides.length, Roi.POLYGON);
                
            default:
                throw new IllegalArgumentException("Unknown background region: " + region);
        }
    }
}
```

### 1.2 Natural Language Schema Extension

#### File: `autodense/nl/src/main/resources/intent.schema.json`
```json
{
  "definitions": {
    "quantify_bands_action": {
      "type": "object",
      "properties": {
        "action": {"const": "quantify_bands"},
        "lanes": {
          "type": "array", 
          "items": {"type": "string"},
          "description": "Lane identifiers to quantify"
        },
        "background_region": {
          "type": "string",
          "enum": ["all", "top_bottom", "sides"],
          "default": "all",
          "description": "Background sampling region"
        },
        "background_method": {
          "type": "string", 
          "enum": ["median", "mean"],
          "default": "median",
          "description": "Statistical method for background estimation"
        },
        "expansion_pixels": {
          "type": "integer",
          "minimum": 1,
          "maximum": 50,
          "default": 3,
          "description": "Background region expansion in pixels"
        },
        "reset_scale": {
          "type": "boolean",
          "default": true,
          "description": "Reset image scale before measurement"
        }
      },
      "required": ["action"]
    }
  }
}
```

### 1.3 LLM System Prompt Enhancement

#### File: `autodense/nl/src/main/java/com/betterdairy/autodense/nl/NLClient.java`
```java
private String buildSystemPrompt(NLContext ctx) {
    return String.format("""
        You are AutoDense, an AI assistant for gel densitometry analysis based on proven
        scientific methods from Fiji ImageJ and Image Studio Lite.
        
        QUANTIFICATION METHODS (from University of Tokyo research):
        
        Background Regions:
        - "all": Expands uniformly around ROI - universal method, works with any shape
        - "top_bottom": Above/below ROI - optimal for horizontal protein bands  
        - "sides": Left/right of ROI - optimal for vertical gel lanes
        
        Statistical Methods:
        - "median": More robust to noise and outliers - recommended for noisy gels
        - "mean": Standard average - faster, good for clean gels
        
        Core Quantification Formula (Fiji standard):
        signal = area × (mean_intensity - background_intensity)
        
        BEST PRACTICES:
        - Use "sides" background for vertical lanes (standard gel orientation)
        - Use "median" method for noisy or uneven illumination
        - Use 3-5 pixel expansion for adequate background sampling
        - Reset scale before quantification for consistent results
        
        Current gel context: %s
        
        Respond with valid JSON matching the intent schema.
        """, ctx.metadata().toString(2));
}
```

### 1.4 Action Executor Integration

#### File: `autodense/plugin/src/main/java/com/betterdairy/autodense/plugin/ActionExecutor.java`
```java
public class ActionExecutor {
    
    public void executeQuantifyBands(JSONObject action, GelContext context) {
        try {
            // Parse parameters
            BackgroundRegion region = BackgroundRegion.valueOf(
                action.optString("background_region", "all").toUpperCase());
            BackgroundMethod method = BackgroundMethod.valueOf(
                action.optString("background_method", "median").toUpperCase());
            int expansion = action.optInt("expansion_pixels", 3);
            boolean resetScale = action.optBoolean("reset_scale", true);
            
            // Get target lanes
            JSONArray laneIds = action.optJSONArray("lanes");
            List<String> targetLanes = laneIds != null ? 
                IntStream.range(0, laneIds.length())
                         .mapToObj(laneIds::getString)
                         .collect(Collectors.toList()) :
                context.getAllLaneIds();
            
            // Reset scale if requested (Fiji standard practice)
            if (resetScale) {
                IJ.run(context.getImage(), "Set Scale...", 
                       "distance=0 known=0 pixel=1 unit=pixel");
            }
            
            // Quantify each lane's bands
            BandDetector detector = new BandDetector();
            List<BandQuantification> results = new ArrayList<>();
            
            for (String laneId : targetLanes) {
                List<Roi> bands = context.getLaneBands(laneId);
                for (Roi bandRoi : bands) {
                    BandQuantification quant = detector.quantifyBand(
                        context.getImage(), bandRoi, region, method, expansion);
                    results.add(quant);
                    
                    // Add to results table (ImageJ standard)
                    addToResultsTable(quant, laneId);
                }
            }
            
            // Update context
            context.setBandQuantifications(results);
            context.addHistoryEntry("Quantified " + results.size() + 
                " bands using " + method.name().toLowerCase() + 
                " background (" + region.name().toLowerCase() + " region)");
            
        } catch (Exception e) {
            throw new RuntimeException("Band quantification failed: " + e.getMessage(), e);
        }
    }
    
    private void addToResultsTable(BandQuantification quant, String laneId) {
        int row = ResultsTable.getResultsTable().getCounter();
        ResultsTable rt = ResultsTable.getResultsTable();
        
        rt.setValue("Lane", row, laneId);
        rt.setValue("Signal", row, quant.getSignal());
        rt.setValue("Total", row, quant.getTotal());
        rt.setValue("Area", row, quant.getArea());
        rt.setValue("Mean", row, quant.getMean());
        rt.setValue("Background", row, quant.getBackground());
        rt.setValue("Method", row, quant.getMethod().name());
        rt.setValue("ROI_X", row, quant.getBounds().x);
        rt.setValue("ROI_Y", row, quant.getBounds().y);
        rt.setValue("ROI_W", row, quant.getBounds().width);
        rt.setValue("ROI_H", row, quant.getBounds().height);
        
        rt.show("Results");
    }
}
```

## Phase 2: Advanced Features (Week 3-4)

### 2.1 Interactive Profile Plotting
```java
// File: autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/ProfilePlotter.java

public class ProfilePlotter {
    
    public ProfilePlot createSplinedProfile(ImagePlus imp, Roi roi) {
        // Based on Extended_Profile_Plot.bsh
        if (!roi.isLine()) {
            Polygon polygon = roi.getPolygon();
            polygon.addPoint(polygon.xpoints[0], polygon.ypoints[0]);
            roi = new PolygonRoi(polygon, Roi.POLYLINE);
            ((PolygonRoi) roi).fitSpline();
        }
        
        ImagePlus dummyImage = new ImagePlus(imp.getTitle(), imp.getProcessor());
        dummyImage.setRoi(roi);
        return new ProfilePlot(dummyImage, true);
    }
    
    public void createDynamicProfiler(ImagePlus imp) {
        // Based on Dynamic_ROI_Profiler.clj concepts
        // Real-time profile updates as ROI changes
    }
}
```

### 2.2 Multi-channel Analysis
```java
// File: autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/ChannelAnalyzer.java

public class ChannelAnalyzer {
    
    public enum ChannelWeights {
        RED_ONLY(1.0, 0.0, 0.0),
        GREEN_ONLY(0.0, 1.0, 0.0), 
        BLUE_ONLY(0.0, 0.0, 1.0),
        EQUAL_RGB(1.0/3.0, 1.0/3.0, 1.0/3.0),
        LUMINANCE(0.299, 0.587, 0.114);  // ImageJ standard
        
        private final double red, green, blue;
        
        ChannelWeights(double r, double g, double b) {
            this.red = r; this.green = g; this.blue = b;
        }
    }
    
    public BandQuantification quantifyWithChannel(ImagePlus imp, Roi roi, 
                                                ChannelWeights weights,
                                                BackgroundRegion region,
                                                BackgroundMethod method) {
        // Set RGB weights (from Measure_RGB.txt)
        IJ.run("RGB Weights...", String.format("red=%f green=%f blue=%f", 
               weights.red, weights.green, weights.blue));
        
        // Perform quantification
        return new BandDetector().quantifyBand(imp, roi, region, method, 3);
    }
}
```

## Phase 3: Production Integration (Week 5-6)

### 3.1 Export Enhancement
```java
// File: autodense/plugin/src/main/java/com/betterdairy/autodense/export/CSVExporter.java

public class CSVExporter {
    
    public void exportQuantificationResults(List<BandQuantification> results, 
                                          File outputFile) {
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            // Header matching Fiji output format
            writer.println("Lane,Band,Signal,Total,Area,Mean,Background," + 
                          "Method,Region,X,Y,Width,Height,Timestamp");
            
            for (BandQuantification quant : results) {
                writer.printf("%s,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%d,%d,%d,%d,%s%n",
                    quant.getLaneId(), quant.getBandId(),
                    quant.getSignal(), quant.getTotal(), quant.getArea(),
                    quant.getMean(), quant.getBackground(),
                    quant.getMethod(), quant.getRegion(),
                    quant.getBounds().x, quant.getBounds().y,
                    quant.getBounds().width, quant.getBounds().height,
                    new Date());
            }
        }
    }
}
```

### 3.2 LLM Command Examples

#### Basic Quantification
```
Human: "Quantify all bands using the standard method"
LLM Response: {
  "intent": "multi_action",
  "actions": [{
    "action": "quantify_bands",
    "background_region": "all",
    "background_method": "median", 
    "expansion_pixels": 3,
    "reset_scale": true
  }]
}
```

#### Optimized for Gel Type
```
Human: "Analyze these vertical lanes with side background regions"  
LLM Response: {
  "intent": "multi_action",
  "actions": [{
    "action": "quantify_bands",
    "background_region": "sides",
    "background_method": "median",
    "expansion_pixels": 3,
    "lanes": ["1", "2", "3", "4"]
  }]
}
```

#### Troubleshooting Guidance
```
Human: "The background seems noisy, what should I do?"
LLM Response: {
  "intent": "guidance",
  "recommendation": "For noisy backgrounds, I recommend using median background estimation instead of mean, and increasing the expansion to 5-7 pixels for better sampling. Would you like me to re-quantify with these settings?",
  "suggested_action": {
    "action": "quantify_bands", 
    "background_method": "median",
    "expansion_pixels": 5
  }
}
```

## Testing Strategy

### Unit Tests
```java
// File: autodense/plugin/src/test/java/com/betterdairy/autodense/analysis/BandDetectorTest.java

@Test
public void testQuantificationMatchesFiji() {
    // Create synthetic gel with known values
    ImagePlus testImage = createSyntheticGel();
    Roi testBand = new Roi(50, 50, 100, 20);
    
    // Test against expected Fiji results
    BandQuantification result = detector.quantifyBand(testImage, testBand, 
        BackgroundRegion.ALL, BackgroundMethod.MEDIAN, 3);
    
    assertEquals(expectedSignal, result.getSignal(), 0.001);
    assertEquals(expectedBackground, result.getBackground(), 0.001);
}
```

### Integration Tests
```java
@Test 
public void testNaturalLanguageQuantification() {
    // Test end-to-end NL processing
    String command = "Quantify bands in lanes 1-3 using side background";
    JSONObject result = nlClient.plan(command, context);
    
    assertEquals("quantify_bands", result.getJSONArray("actions")
                                         .getJSONObject(0)
                                         .getString("action"));
    assertEquals("sides", result.getJSONArray("actions")
                               .getJSONObject(0) 
                               .getString("background_region"));
}
```

## Success Metrics

### Technical Metrics
- **Quantification Accuracy**: ±5% vs Fiji reference results
- **Processing Speed**: <2 seconds per band on standard hardware
- **Memory Usage**: <500MB for typical gel analysis
- **Natural Language Accuracy**: >95% command interpretation success

### User Experience Metrics  
- **Learning Curve**: <30 minutes for basic operations
- **Task Completion**: >90% success rate for common workflows
- **User Satisfaction**: >4.5/5 rating for analysis quality

### Scientific Validation
- **Reproducibility**: <2% variance between repeated measurements  
- **Publication Ready**: Results suitable for academic publication
- **Standards Compliance**: Matches established gel analysis protocols

## Risk Mitigation

### Technical Risks
- **Performance**: Implement caching and parallel processing
- **Accuracy**: Extensive validation against known standards
- **Compatibility**: Test with diverse gel types and imaging conditions

### Integration Risks
- **ImageJ Dependencies**: Pin specific versions, test thoroughly
- **LLM Consistency**: Implement fallback parsing and validation
- **Scale Issues**: Progressive enhancement for complex analyses

## Timeline and Resources

### Week 1-2: Core Implementation
- BandQuantification class and quantification algorithms
- Natural language schema updates
- Basic action executor integration
- Unit test suite

### Week 3-4: Advanced Features  
- Profile plotting enhancements
- Multi-channel analysis
- Interactive optimization tools
- Integration test suite

### Week 5-6: Production Polish
- Export functionality enhancement
- Performance optimization
- User interface integration
- Documentation and validation

### Resources Required
- **Development**: 1 senior developer (full-time)
- **Testing**: Access to diverse gel images and reference data
- **Validation**: Collaboration with gel analysis researchers
- **Hardware**: Multi-core development machine with adequate RAM

This implementation plan transforms AutoDense from a basic analysis tool into a scientifically rigorous, AI-powered gel analysis platform that leverages decades of research and development in the ImageJ/Fiji ecosystem.