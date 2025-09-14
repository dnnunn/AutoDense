# Next Steps: September 13, 2025 - Parameter System Enhancement

> **Doc Meta**
> - **Purpose:** Detailed roadmap for completing parameter system improvements and architectural enhancements in AutoDense
> - **Scope:** Error #1 completion, upload-time image standardization implementation, and comprehensive parameter system overhaul
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-13

## 🎯 Immediate Priorities (Next Session Start)

### 1. Complete Error #1 - Gamma Parameter Integration
**Status**: In Progress (50% complete - investigation done, implementation pending)

**Current State**: 
- UI collects gamma parameter in Streamlit interface
- `PreprocParams` dataclass in `autodense/preprocess/pipeline.py:20` doesn't support gamma
- Implementation location identified and ready for modification

**Required Actions**:
```python
# 1. Add gamma parameter to PreprocParams dataclass
@dataclass
class PreprocParams:
    # ... existing parameters ...
    gamma: float = 1.0  # ADD THIS LINE after line 30

# 2. Add gamma correction function after line 109
def _gamma_correct(g: np.ndarray, gamma: float) -> np.ndarray:
    if gamma == 1.0:
        return g
    return np.power(np.clip(g, 0, 1), gamma).astype(np.float32)

# 3. Integrate gamma correction in pipeline after line 137
g_gamma = _gamma_correct(g_eq, params.gamma)
stages["55_gamma"] = g_gamma

# 4. Update final processing to use g_gamma instead of g_eq
g_sk, deg = _deskew(g_gamma) if params.deskew else (g_gamma, 0.0)
```

**Testing Required**:
- Create `test_gamma_correction.py` to validate gamma functionality
- Test with gamma values: 0.5, 1.0, 1.5, 2.0
- Verify stages dictionary includes gamma step
- Confirm no regression in existing functionality

### 2. Implement Upload-Time Image Standardization
**Status**: Architecture designed, implementation pending

**Foundation**: Complete proposal in `architecture_proposal_image_standardization.md`

**Phase 1 - Core Implementation**:
```python
# Add to ui/streamlit_autodense_app.py around line 100
def standardize_uploaded_image(uploaded_file):
    """Standardize all uploaded images to consistent size immediately"""
    img = Image.open(uploaded_file)
    
    # Resize if needed (maintaining aspect ratio)
    max_dimension = 1920
    if max(img.size) > max_dimension:
        if img.size[0] > img.size[1]:
            new_size = (max_dimension, int(img.size[1] * max_dimension / img.size[0]))
        else:
            new_size = (int(img.size[0] * max_dimension / img.size[1]), max_dimension)
        img = img.resize(new_size, Image.LANCZOS)
    
    return img
```

**Integration Points**:
- File upload handlers (wherever `st.file_uploader` results are processed)
- Session state storage (replace original with standardized image)
- Remove redundant resizing from `_img_to_data_url()` (no longer needed)

## 🔧 Medium Priority Tasks (Within 2-3 Sessions)

### 3. ADParams Enhancement
**Current State**: Analysis complete, implementation needed

**Required Parameters to Add**:
```python
# Add to autodense_autotune/workflow_validator.py or relevant ADParams location
@dataclass
class ADParams:
    # ... existing parameters ...
    gel_type: str = "sds"  # "sds" | "dna" | "protein"
    conf_threshold: float = 0.5  # confidence threshold for detection
    mw_lane: Optional[int] = None  # molecular weight marker lane number
```

**UI Integration**: Update Streamlit parameter collection to pass these parameters

### 4. Parameter Filtering System
**Requirement**: Handle 123 UI parameters → 8-10 backend parameters efficiently

**Approach**:
```python
def filter_ui_params_for_backend(ui_params: dict) -> tuple[PreprocParams, ADParams]:
    """Filter UI parameters to supported backend parameters"""
    
    # PreprocParams filtering
    preproc_keys = {'modality', 'comb_hint', 'target_channel', 'polarity', 
                    'bg_method', 'bg_radius_px', 'denoise', 'clahe', 
                    'deskew', 'rectify', 'gamma'}  # Added gamma
    
    # ADParams filtering  
    ad_keys = {'gel_type', 'conf_threshold', 'mw_lane'}
    
    # Extract and validate parameters
    preproc_params = {k: v for k, v in ui_params.items() if k in preproc_keys}
    ad_params = {k: v for k, v in ui_params.items() if k in ad_keys}
    
    return PreprocParams(**preproc_params), ADParams(**ad_params)
```

### 5. Progressive Disclosure UI Design
**Goal**: Organize 123 parameters into manageable hierarchy

**Structure**:
- **Basic**: 5-8 most common parameters (always visible)
- **Advanced**: 15-20 specialized parameters (expandable section)
- **Expert**: Remaining parameters (advanced users only)

**Implementation**: Streamlit expander components with logical groupings

## 🎨 Advanced Enhancements (Future Sessions)

### 6. Smart Parameter Presets
**Vision**: Context-aware parameter suggestions based on image analysis

**Implementation Strategy**:
```python
def suggest_parameters(image: np.ndarray) -> dict:
    """Analyze image and suggest optimal parameters"""
    # Image analysis for recommendations
    brightness = np.mean(image)
    contrast = np.std(image)
    
    suggestions = {}
    if brightness < 0.3:
        suggestions['gamma'] = 1.2  # Brighten dark images
    if contrast < 0.1:
        suggestions['clahe'] = True  # Enhance low contrast
        
    return suggestions
```

### 7. Parameter Validation and Guidance
**Features**:
- Real-time validation of parameter combinations
- Warning messages for unusual parameter values
- Tooltips explaining parameter effects
- Visual previews of parameter changes

## 🧪 Comprehensive Testing Framework

### Test Coverage Requirements
1. **Error Regression Tests**: Verify all three original errors stay fixed
2. **Parameter Integration Tests**: Test new parameters end-to-end
3. **Image Standardization Tests**: Validate upload-time resizing
4. **Performance Tests**: Measure improvement from single resize
5. **UI/Backend Integration Tests**: Verify parameter filtering works

### Testing Strategy
```bash
# Create comprehensive test suite
tests/
├── test_error_regression.py      # Prevent error recurrence
├── test_gamma_integration.py     # Gamma parameter functionality
├── test_image_standardization.py # Upload-time resizing
├── test_parameter_filtering.py   # UI → Backend parameter mapping
└── test_performance_impact.py    # Measure improvements
```

## 📋 Implementation Order (Recommended)

### Session 1 (Next):
1. ✅ Complete Error #1 - Gamma parameter
2. 🔧 Implement upload-time image standardization core functionality
3. 🧪 Create and run regression tests

### Session 2:
1. 🔧 Add missing ADParams parameters
2. 🔧 Implement parameter filtering system
3. 🧪 Test parameter integration end-to-end

### Session 3:
1. 🎨 Design and implement progressive disclosure UI
2. 🔧 Create smart parameter presets foundation
3. 🧪 Comprehensive testing and validation

## 🎯 Success Metrics

- **Error Resolution**: All 4 errors fully resolved and tested
- **Performance**: Faster image processing with single resize
- **User Experience**: Organized parameter hierarchy with <10% of parameters visible by default
- **Code Quality**: Comprehensive test coverage for new functionality
- **Architecture**: Clean separation between UI collection and backend processing

## ⚠️ Known Risks and Mitigation

### Risk: Breaking Changes
**Mitigation**: Incremental implementation with comprehensive testing at each step

### Risk: Performance Regression
**Mitigation**: Benchmark before/after performance with realistic test images

### Risk: Parameter Complexity
**Mitigation**: Progressive disclosure UI with smart defaults

## 🔗 Dependencies

- **Error #1** must complete before parameter filtering system
- **Image standardization** can be implemented in parallel
- **UI improvements** depend on parameter system completion
- **Testing framework** should accompany each implementation phase

This roadmap provides clear, actionable steps for completing the parameter system enhancement while maintaining the systematic, test-driven approach established in this session.