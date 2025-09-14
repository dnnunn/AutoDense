# Canonical Tool Surface - Streamlined Gemini Interface

> **Doc Meta**
> - **Purpose:** Streamlined tool interface design for Gemini AI integration
> - **Scope:** Tool consolidation, canonical actions, and deprecation strategy
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## **BEFORE: Confusing Tool Sprawl (35+ methods)**

### Problems Identified:
- **Too Many Tools**: 35+ granular methods across 3 tool classes
- **Naming Inconsistencies**: `detect_lanes` vs `detectLanes`, `count_colonies` (multiple)
- **Functional Overlap**: Same functionality in different classes
- **Too Granular**: `enable_band_assist`, `configure_band_assist`, `disable_band_assist`
- **Scattered Across Classes**: GelAnalysisTools, ColonyAnalysisTools, PlateAnalysisTools

### Old Tool List:
**GelAnalysisTools (13 methods):**
- open_image, preprocess, detect_lanes, detect_bands, adjust_lanes
- quantify_bands, render_overlay_png, export_results, calibrate_molecular_weight
- enable_band_assist, disable_band_assist, configure_band_assist, normalize_intensities

**ColonyAnalysisTools (12 methods):**
- detect_plate, count_colonies, classify_colonies, bin_colonies, normalize_colonies
- export_colonies, enable_colony_assist, disable_colony_assist, colony_assist_click
- propagate_colony_class, relabel_colony, export_detailed_features

**PlateAnalysisTools (10+ methods):**
- detect_colonies, count_colonies_by_color, measure_colony_sizes, check_contamination
- create_labeled_reference, export_for_notebook, export_for_presentation, export_colony_analysis
- (Plus duplicated functionality from other classes)

---

## **AFTER: Streamlined Canonical Interface (8 core actions)**

### Design Principles:
✅ **Complete workflows over granular steps**
✅ **Consistent naming** (verb_noun pattern)  
✅ **No functional overlap**
✅ **Clear separation**: gel vs plate vs cross-cutting

### **CANONICAL TOOLS (Primary Interface):**

#### **Gel Analysis (3 actions):**
1. **`analyze_gel`** - Complete gel workflow
   - Replaces: open_image + detect_lanes + detect_bands + quantify_bands
   - Parameters: image_path, expected_lanes, constant_spacing, sensitivity, background_method
   - Returns: Complete analysis results with lane/band counts

2. **`adjust_gel`** - Fine-tune gel analysis  
   - Replaces: adjust_lanes + enable_band_assist + configure_band_assist + calibrate_molecular_weight
   - Parameters: lane_offset, lane_width, enable_band_assist, assist_sensitivity
   - Returns: Adjustment confirmation

3. **`export_gel`** - Export gel results
   - Replaces: export_results + render_overlay_png + create_labeled_reference
   - Parameters: formats (csv, png, presentation), include_intensities
   - Returns: Export file paths

#### **Plate Analysis (3 actions):**
4. **`analyze_plate`** - Complete plate workflow
   - Replaces: detect_plate + count_colonies + classify_colonies
   - Parameters: image_path, classify (boolean), classification_type
   - Returns: Colony counts and classifications

5. **`adjust_plate`** - Fine-tune plate analysis
   - Replaces: relabel_colony + bin_colonies + propagate_colony_class + colony_assist_*
   - Parameters: relabel_colony, size_bins, propagate_class
   - Returns: Adjustment confirmation

6. **`export_plate`** - Export plate results
   - Replaces: export_colonies + export_colony_analysis + export_for_notebook + export_for_presentation
   - Parameters: formats (csv, png, summary), include_features
   - Returns: Export file paths

#### **Cross-Cutting (2 actions):**
7. **`preprocess_image`** - Image enhancement
   - Replaces: preprocess (with clearer name)
   - Parameters: rotation, contrast, background, filtering
   - Returns: Enhanced image handle

8. **`clear_session`** - Reset analysis state
   - Same functionality as before
   - Parameters: confirm
   - Returns: Session reset confirmation

---

## **Backward Compatibility & Migration**

### **Aliases with Deprecation Warnings:**
All 35+ old tools still work but log deprecation warnings:
```
sessionLogger.warn("deprecated_tool", "detect_lanes deprecated, use analyze_gel");
```

### **Migration Examples:**

**Before (4 separate calls):**
```
open_image({path: "gel.tif"})
detect_lanes({expected_lanes: 12})  
detect_bands({sensitivity: 0.7})
export_results({formats: ["csv", "png"]})
```

**After (2 calls):**
```
analyze_gel({image_path: "gel.tif", expected_lanes: 12, sensitivity: 0.7})
export_gel({formats: ["csv", "png"]})
```

**Before (granular colony analysis):**
```
detect_plate({image_path: "plate.jpg"})
count_colonies({})
classify_colonies({method: "xgal"})
bin_colonies({size_bins: [1.0, 2.0, 3.0]})
export_colonies({formats: ["csv"]})
```

**After (streamlined):**
```
analyze_plate({image_path: "plate.jpg", classify: true})
adjust_plate({size_bins: [1.0, 2.0, 3.0]})
export_plate({formats: ["csv"]})
```

---

## **Benefits Achieved**

### **For Gemini AI:**
✅ **Reduced confusion**: 8 clear actions vs 35+ scattered methods
✅ **Consistent patterns**: All tools follow verb_noun naming
✅ **Fewer decisions**: Complete workflows reduce multi-step planning
✅ **Clear boundaries**: gel vs plate vs image vs session actions

### **For Users:**
✅ **Faster workflows**: One call does complete analysis
✅ **Less fragmentation**: No need to chain multiple small operations  
✅ **Consistent results**: Workflows use optimal parameter combinations
✅ **Better error handling**: Complete workflows handle edge cases

### **For Developers:**
✅ **Easier maintenance**: Single canonical interface to maintain
✅ **Clear deprecation path**: Old tools still work during transition
✅ **Logging visibility**: Deprecation warnings guide migration
✅ **Reduced complexity**: 8 tools instead of 35+ to document/test

---

## **Implementation Details**

### **Files Modified:**
1. **`CanonicalTools.java`** - New canonical interface (8 methods)
2. **`GeminiOrchestrator.java`** - Route canonical first, then aliases with warnings
3. **Existing tool classes** - Unchanged (still work via aliases)

### **Logging & Monitoring:**
- All deprecated tool usage logged via SessionLogger
- Deprecation warnings help identify migration opportunities
- Tool usage analytics show canonical vs deprecated usage

### **Testing Strategy:**
- Canonical tools tested for complete workflow coverage  
- Backward compatibility verified for all existing tools
- Deprecation warnings confirmed in session logs
- Error messages guide users to canonical alternatives

---

## **Recommendations**

### **For New Development:**
- Use only canonical tools in prompts/documentation
- Update examples to use canonical workflows
- Train Gemini on canonical tool patterns

### **For Migration:**
- Monitor deprecation warnings in session logs
- Gradually update user workflows to canonical tools
- Eventually remove deprecated aliases (future version)

### **For Documentation:**
- Highlight 8 canonical tools prominently
- Show workflow examples with canonical tools
- Mark old tools as deprecated in capability registry

The canonical tool surface reduces Gemini's decision complexity from 35+ scattered methods to 8 focused, workflow-oriented actions while maintaining full backward compatibility.