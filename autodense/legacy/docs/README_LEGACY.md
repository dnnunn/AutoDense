# AutoDense Legacy Documentation

> **Doc Meta**
> - **Purpose:** Archive of obsolete documentation from the Java/ImageJ era
> - **Scope:** Historical documents from pre-Python migration (before September 9, 2025)
> - **Owner:** @davidnunn 
> - **Last-verified:** 2025-09-09

## Migration Context

On September 9, 2025, AutoDense completed a **complete migration from Java/ImageJ/Fiji to pure Python**. This directory contains historical documentation from the Java era that has been preserved for reference but is no longer applicable to the current Python-based architecture.

## Documentation Categories

### `/docs/` - Core Java Documentation
- API references for Java classes and ImageJ integration
- Architecture documents for the deprecated Java plugin system
- Implementation guides for obsolete Java-based analysis tools
- Development guides for the old Maven-based build system

### `/NextSteps/` - Historical Development Plans  
- Pre-migration development roadmaps
- Java architecture improvement plans
- Feature development plans that were superseded by the Python migration

### `/Issues/` - Historical Problem Reports
- Java-specific bug reports and architectural issues
- CLI integration problems from the Java era
- Configuration pipeline issues that were resolved by migration

### `/SessionSummaries/` - Pre-Migration Session Logs
- Development session summaries from the Java era
- Documentation of architectural decisions that are now obsolete
- Progress reports leading up to the Python migration

### `/StructureDocs/` - Historical Architecture Documentation
- Java project structure documentation
- Detailed documentation of the deprecated Java/ImageJ integration
- Build system documentation for the old Maven configuration

## Current Architecture

**As of September 9, 2025**, AutoDense uses:
- **Pure Python** scientific stack (numpy, scipy, scikit-image)
- **Streamlit UI** for user interface
- **Deterministic computer vision** instead of AI orchestration
- **Production ML pipeline** with COCO/YOLO export capabilities

For current documentation, see the main `/docs/` folder in the project root.

## Reference Value

These documents may provide:
- Historical context for architectural decisions
- Algorithm concepts that were ported to Python
- Requirements and user stories that informed the Python implementation
- Examples of what NOT to do in future architectural decisions

**Note:** Code examples, API references, and implementation details in these documents are **completely obsolete** and should not be used for current development.