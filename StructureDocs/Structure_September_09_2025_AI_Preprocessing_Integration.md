# AutoDense Project Structure - AI-Guided Preprocessing Integration

> **Doc Meta**
> - **Purpose:** Updated project structure after integrating AI-guided preprocessing policy system
> - **Scope:** New preprocessing modules and updated UI/CLI components with AI-guided decision making
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-09

## 🧠 AI-Guided Preprocessing Integration

This structure update reflects the integration of intelligent preprocessing that replaces blind parameter application with metrics-driven AI decision making.

## 📁 Key Structural Changes

### New AI-Guided Preprocessing System
```
autodense/
├── preprocess/                     # ** NEW: AI-GUIDED PREPROCESSING **
│   ├── __init__.py
│   ├── pipeline.py                 # Manual preprocessing pipeline
│   └── policy.py                   # ** NEW: AI-guided policy with ChatGPT-5 **
│       ├── PolicyThresholds        # SNR ≥20%, separability ≥15% gates
│       ├── PolicyOutcome           # Before/after metrics + decision
│       ├── guarded_preprocess()    # Main AI-guided entry point
│       ├── compute_metrics()       # SNR, separability, skew analysis
│       └── evaluate_transforms()   # Test deskew, destripe, background, CLAHE
```

### Updated CLI System
```
autodense/scripts/
├── cli_batch.py                    # ** UPDATED: Added --preproc-mode **
│   ├── ai_guarded                  # ChatGPT-5 metrics-based decisions
│   ├── manual                      # User-specified parameters
│   └── off                         # Raw grayscale normalization
```

### Enhanced UI Components
```
ui/
├── streamlit_app.py                # ** UPDATED: Three-mode preprocessing **
│   ├── AI guarded (recommended)    # Default intelligent preprocessing
│   ├── Manual                      # Expert parameter control
│   └── Off                         # Raw image analysis
```

### Test Infrastructure
```
test_ai_preprocessing.py            # ** NEW: Comprehensive AI testing **
simple_test.py                      # ** NEW: Minimal validation script **
tests/
├── util_synth.py                   # Synthetic gel generation
└── test_preprocess_pipeline.py     # Pipeline validation
```

## 🔍 AI-Guided Decision Architecture

### Policy Logic Flow
1. **Load image** → RGB conversion for consistent processing
2. **Compute baseline metrics** → SNR proxy, lane separability, skew, stripe ratio, illumination
3. **Evaluate transforms** → Test each: deskew, destripe, background removal, CLAHE
4. **Apply improvement gates** → Only accept if ≥20% SNR gain AND ≥15% separability gain
5. **Return decision** → Mode ("none"|"deskew"|"destripe"|"background"|"clahe") + metrics

### Integration Points
- **Streamlit UI**: Mode selector with real-time policy feedback
- **CLI batch**: `--preproc-mode ai_guarded` for automated processing  
- **Policy transparency**: Full before/after metrics and parameters logged
- **Fallback handling**: Manual/Off modes when AI unavailable

## 🧬 Processing Pipeline Changes

### Before Integration (Blind Processing)
```
Image → Fixed Parameters → Apply All Transforms → Often Messy Results
```

### After Integration (AI-Guided)
```
Image → Metrics Analysis → Evaluate Each Transform → Gate on Improvement → Selective Application
```

## 📊 New Dependencies & Requirements

### Python Packages (Added)
- **OpenAI/ChatGPT API access** for AI-guided decision making
- **Enhanced scikit-image** for advanced metrics computation
- **Policy evaluation framework** for transform assessment

### API Integration
- **ChatGPT-5 inference** for intelligent preprocessing decisions
- **Timeout handling** for long-running AI analysis (2+ minutes)
- **Error recovery** with fallback to manual/off modes

## 🎯 User Workflow Impact

### Default Experience
1. Upload gel image to Streamlit UI
2. **AI automatically analyzes** image quality metrics
3. **Policy decides** whether to apply preprocessing (deskew, background removal, etc.)
4. **Shows improvement metrics** (ΔSNR, ΔSep) for transparency
5. **Proceeds to lane/band detection** with optimized image

### Expert Control Preserved
- **Manual mode** retains all original preprocessing parameters
- **Off mode** provides raw image analysis when needed
- **Full parameter control** available for specialized workflows

## 🔗 File Dependencies

### Core Imports
```python
from autodense.preprocess.policy import guarded_preprocess, PolicyThresholds
from autodense.preprocess.pipeline import PreprocParams, run as preproc_run
```

### CLI Usage
```bash
python -m autodense.scripts.cli_batch --dir images/ --preproc-mode ai_guarded
```

### UI Integration
- Mode selector with three options
- Real-time metrics display  
- Policy decision transparency
- Original image overlay preservation

This integration represents the shift from blind preprocessing to intelligent, metrics-driven image optimization with full user transparency and control.