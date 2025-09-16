# Project Structure - September 13, 2025 - Better Dairy Branding Session

> **Doc Meta**
> - **Purpose:** Current project structure snapshot after Better Dairy branding integration
> - **Scope:** Key directories and files, focusing on UI components and branding assets
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-13

## Notable Changes This Session

### Added Files
- **`betterdairyicon.png`** - Better Dairy corporate logo asset (5.7KB)

### Modified Files
- **`ui/streamlit_autodense_app.py`** - Better Dairy logo integration, duplicate header removal, accessibility fixes
- **`UI_FIXES_SUMMARY.md`** - Updated with branding and accessibility improvements documentation

### Documentation Added
- **`SessionSummaries/Session_summary_September_13_2025.md`** - Complete session documentation
- **`NextSteps/NextSteps_September_13_2025.md`** - Future development priorities

## Core Application Structure

```
AutoDense/
├── betterdairyicon.png                    # 🆕 Better Dairy logo asset
├── ui/
│   ├── streamlit_autodense_app.py         # 🔄 Main Streamlit application (Better Dairy branding integrated)
│   ├── autodense_types.py                 # Type definitions and interfaces
│   ├── components/
│   │   ├── __init__.py
│   │   ├── calibration_instructions.py    # Lane calibration UI component
│   │   ├── image_upload.py               # Enhanced image upload with loading states
│   │   └── prerequisites_panel.py        # Prerequisites and setup guidance
│   ├── utils/
│   │   ├── data_helpers.py               # Data conversion and CSV utilities
│   │   ├── error_handling.py             # Unified error handling patterns
│   │   ├── image_processing.py           # Image standardization and conversion
│   │   ├── parameter_management.py       # UI-backend parameter translation
│   │   ├── preprocessing.py              # AI and manual preprocessing functions
│   │   └── ux_inject.py                  # UX enhancement utilities
│   └── tests/
│       ├── __init__.py
│       ├── conftest.py                   # pytest configuration and fixtures
│       ├── test_accessibility.py         # WCAG compliance tests (11/11 passing)
│       ├── test_calibration_instructions.py
│       ├── test_component_error_integration.py
│       ├── test_error_handling.py
│       ├── test_image_upload.py
│       └── test_prerequisites_panel.py
```

## Documentation Structure

```
AutoDense/
├── SessionSummaries/
│   ├── Session_summary_September_09_2025_AI_Guided_Preprocessing_Integration.md
│   ├── Session_summary_September_09_2025_AutoDense_Heart_Transplant_UI_Revolution.md
│   ├── Session_summary_September_12_2025_Critical_Button_Fix.md
│   ├── Session_summary_September_13_2025.md  # 🆕 This session's summary
│   ├── Session_summary_September_13_2025_Systematic_Error_Analysis_and_Image_Standardization.md
│   └── Session_summary_September_13_2025_Type_Safety_and_Error_Handling.md
├── NextSteps/
│   └── NextSteps_September_13_2025.md    # 🆕 Future development priorities
├── StructureDocs/
│   ├── Absolute_Paths.md                 # Build path references
│   ├── Structure_September_09_2025_AI_Preprocessing_Integration.md
│   ├── Structure_September_09_2025_Post_Heart_Transplant.md
│   ├── Structure_September_12_2025_Post_Critical_Button_Fix.md
│   ├── Structure_September_13_2025_AI_Error_Analysis_Session.md
│   ├── Structure_September_13_2025_Better_Dairy_Branding.md  # 🆕 This document
│   └── Structure_September_13_2025_UI_Error_Handling_Session.md
└── UI_FIXES_SUMMARY.md                   # 🔄 Updated with Better Dairy branding section
```

## Key Configuration Files

```
AutoDense/
├── CLAUDE.md                            # Project instructions and context for Claude
├── Architecture.md                      # System architecture documentation
├── pyproject.toml                       # Python project configuration
├── pytest.ini                          # Test configuration
├── requirements.txt                     # Python dependencies
├── requirements_streamlit.txt           # Streamlit-specific dependencies
├── Makefile                            # Build and development commands
└── build.sh                           # Build script
```

## Environments and Dependencies

### Python Virtual Environment
- **Location:** `.venv/` (activated during development)
- **Python Version:** 3.13
- **Key Dependencies:**
  - Streamlit (web framework)
  - PIL/Pillow (image processing, logo loading)
  - pytest (testing framework)
  - numpy, scipy (scientific computing)

### Development Tools
- **MyPy Cache:** `ui/.mypy_cache/` (type checking)
- **Pytest Cache:** `ui/.pytest_cache/` (test caching)
- **Coverage Reports:** `.coverage` (test coverage data)

## Testing Infrastructure

### Test Coverage
- **Total Tests:** 73/73 passing (100% success rate)
- **Accessibility Tests:** 11/11 passing
- **Component Tests:** Full coverage of UI components
- **Error Handling Tests:** Comprehensive error scenarios

### Test Configuration
- **pytest.ini:** Test discovery and execution settings
- **conftest.py:** Shared test fixtures and utilities
- **Coverage:** Automated code coverage reporting

## Notable Architectural Features

### Better Dairy Branding Integration
- **Logo Loading:** PIL-based with graceful fallbacks
- **Favicon Support:** Multi-format browser compatibility
- **Header Layout:** Responsive Better Dairy logo placement

### Accessibility Framework
- **WCAG 2.1 AA Compliance:** Skip links, screen readers, keyboard navigation
- **Screen Reader Support:** Live regions with appropriate priority levels
- **Loading States:** Enhanced user feedback with context

### Error Handling System
- **Unified Patterns:** Consistent error display and recovery
- **User-Friendly Messages:** Clear context and next steps
- **Screen Reader Announcements:** Accessible error reporting

### Component Architecture
- **Modular Design:** Separated UI components with clear interfaces
- **Type Safety:** Comprehensive type annotations and validation
- **Testing Coverage:** Unit tests for all major components

## Working Environments

### Development Environment
- **Primary:** Streamlit development server (localhost:8501)
- **Virtual Environment:** `.venv` activated for all operations
- **Hot Reload:** Automatic updates during development

### Git Workflow
- **Current Branch:** `newheart` (Better Dairy branding ready for merge)
- **Main Branch:** `main` (production-ready code)
- **Commit Status:** Latest changes committed (Better Dairy branding)

### Build System
- **Build Script:** `build.sh` for production builds
- **Makefile:** Development and testing commands
- **Requirements:** Separate files for different deployment contexts

## Performance Considerations

### Image Optimization
- **Logo Size:** 5.7KB Better Dairy PNG (suitable for web)
- **Loading Strategy:** Graceful fallbacks prevent blocking
- **Caching:** Browser-level favicon caching

### Loading States
- **User Feedback:** Comprehensive loading indicators
- **Performance Impact:** Minimal overhead from enhanced UX
- **Accessibility:** Screen reader announcements don't block operations

## Security Notes

### Asset Management
- **Logo Security:** Better Dairy logo properly included in version control
- **API Keys:** No sensitive information in branding assets
- **Dependency Security:** PIL/Pillow for safe image processing

### Access Control
- **File Permissions:** Appropriate permissions on logo assets
- **Path Security:** Safe file path handling for image loading

This structure reflects the successful integration of Better Dairy branding while maintaining the robust architecture and comprehensive testing established in previous sessions.