# Project Structure - January 15, 2025

> **Doc Meta**
>
> - **Purpose:** Current project structure snapshot for AutoDense with lane calibration implementation planning
> - **Scope:** Complete directory tree and key file organization as of session completion
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-01-15

## Major Structural Changes Since Last Session

### New Files Added
- **`NEXT_STEPS_LANE_CALIBRATION.md`**: Comprehensive implementation plan for lane calibration data flow fix
- **`start.sh`**: Shell script for proper virtual environment activation sequence
- **`SessionSummaries/Session_summary_January_15_2025.md`**: Current session documentation

### Modified Core Files
- **`ui/streamlit_autodense_app.py`**: Enhanced with tuple unpacking and improved error handling
- **`ui/utils/data_helpers.py`**: Added 4-strategy JSON extraction for AI preprocessing
- **`ui/utils/preprocessing.py`**: Enhanced preprocessing pipeline with better error handling

## Directory Structure

```
AutoDense/
├── docs/                                    # Project documentation
├── SessionSummaries/                        # Session history and accomplishments
│   ├── Session_summary_January_15_2025.md  # Current session (NEW)
│   └── [22 other session summaries]
├── StructureDocs/                          # Project structure snapshots
│   └── Structure_January_15_2025.md        # Current structure (NEW)
├── ui/                                     # Frontend Streamlit application
│   ├── streamlit_autodense_app.py          # Main application (MODIFIED)
│   ├── utils/                              # Utility modules
│   │   ├── data_helpers.py                 # Data processing utilities (MODIFIED)
│   │   ├── preprocessing.py                # AI preprocessing pipeline (MODIFIED)
│   │   ├── parameter_management.py         # UI-backend parameter translation
│   │   ├── error_handling.py               # Error handling utilities
│   │   ├── state_management.py             # Session state management
│   │   └── [4 other utility modules]
│   └── tests/                              # Test suite
│       └── [9 test modules]
├── scripts/                                # Backend analysis pipeline
│   └── autodense/                          # Core analysis modules
├── .venv/                                  # Virtual environment
├── .env                                    # Environment configuration
├── start.sh                                # Startup script (NEW)
├── start_autodense.py                      # Python startup script
├── NEXT_STEPS_LANE_CALIBRATION.md          # Implementation plan (NEW)
└── [configuration and requirement files]
```

## Key Components and Their Purpose

### Frontend (ui/)
- **`streamlit_autodense_app.py`**: Central Streamlit application with lane calibration UI (lines 1049-1630) and backend integration points (lines 2065, 2273)
- **`utils/parameter_management.py`**: Translates UI parameters to backend-compatible format
- **`utils/data_helpers.py`**: Handles JSON extraction and data processing with robust error handling
- **`utils/preprocessing.py`**: AI preprocessing pipeline with timeout handling

### Backend (scripts/)
- **`scripts/autodense/orchestrator/pipeline.py`**: Core analysis pipeline that needs lane boundary parameter integration
- **Backend parameter investigation required**: Need to understand how to pass lane calibration data

### Environment Setup
- **`.env`**: Environment variables including OpenAI API key configuration
- **`start.sh`**: Shell script ensuring proper virtual environment activation sequence
- **`start_autodense.py`**: Python script with intelligent environment detection

### Documentation
- **`NEXT_STEPS_LANE_CALIBRATION.md`**: 7-phase implementation plan for lane calibration fix
- **`SessionSummaries/`**: Comprehensive session history and decision tracking
- **`docs/`**: Project architecture and workflow documentation

## Working Environments and Dependencies

### Python Environment
- **Virtual Environment**: `.venv/` with isolated dependencies
- **Python Version**: 3.11+ required for lab/vision pipeline compatibility
- **Key Dependencies**:
  - Streamlit for UI framework
  - OpenCV/NumPy for image processing
  - PIL for image handling
  - OpenAI SDK for AI preprocessing

### Development Environment
- **IDE Integration**: VS Code compatible with type checking and debugging
- **Testing Framework**: pytest with comprehensive test coverage in `ui/tests/`
- **Code Quality**: Type hints and error handling throughout codebase

### Runtime Environment
- **Environment Variables**: Loaded from `.env` with fallback to `api_properties.config`
- **Port Management**: Configurable port with process cleanup (default 8501)
- **API Integration**: OpenAI API for preprocessing with fallback handling

## Critical Architectural Findings

### Lane Calibration Data Flow Issue
- **Frontend**: Comprehensive calibration UI and data storage (`st.session_state.res_lane_boundaries`)
- **Backend Calls**: Missing lane calibration parameter passing at critical integration points
- **Impact**: User manual calibration completely ignored across all processing modes

### Interface Compatibility Layer
- **Parameter Translation**: `ui/utils/parameter_management.py` provides type-safe UI-to-backend mapping
- **Error Handling**: Multi-strategy approach for robust JSON processing and API integration
- **Tuple Handling**: Backend returns `(res, params, observations)` tuple that frontend properly unpacks

### Startup Architecture
- **Shell Script**: Ensures virtual environment activation order as required by user
- **Python Script**: Provides intelligent environment detection and configuration
- **Process Management**: Automatic port cleanup and single-process operation

## Next Session Dependencies

### Critical Research Required
1. **Backend Parameter Format**: Investigate `scripts/autodense/orchestrator/pipeline.py` for lane boundary parameter acceptance
2. **Params Dataclass Structure**: Understand how to inject manual calibration data
3. **Lane Constraint Implementation**: Determine backend support for overriding automatic detection

### Implementation Ready
1. **Function Signature Modification**: `_try_run_ad_band_assist()` enhancement planned
2. **Helper Function Creation**: `get_current_lane_calibration()` implementation ready
3. **Call Site Updates**: Lines 2065 and 2273 parameter passing modifications planned

---
*Structure captured: 2025-01-15 at session completion*
*Next session priority: Backend parameter format research and Phase 1 implementation*