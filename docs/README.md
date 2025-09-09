# AutoDense Documentation Hub

> **Doc Meta**
> - **Purpose:** Central navigation for AutoDense project documentation  
> - **Scope:** Links to current Python-based documentation (post-migration)
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-09

## 🎉 Python Migration Complete!

**September 9, 2025**: AutoDense has completed its **complete migration from Java/ImageJ/Fiji to pure Python**. This documentation reflects the current Python-based architecture with deterministic computer vision and Streamlit UI.

## 🚀 Quick Start

- **[Python Package Setup](../autodense/python/README.md)** - Install and run Python AutoDense
- **[Streamlit UI Guide](../ui/streamlit_app.py)** - Interactive analysis interface  
- **[Architecture Overview](../Architecture.md)** - Current system design
- **[CLAUDE.md](../CLAUDE.md)** - Session primer for AI assistance

## 📚 Current Documentation

### Core System  
- **[AutoDense Python Package](../autodense/python/)** - Core analysis libraries
- **[Streamlit UI](../ui/streamlit_app.py)** - User interface application
- **[Batch Processing](../scripts/batch_infer.py)** - Command-line analysis tools
- **[Export Systems](../autodense/export/)** - COCO, YOLO, CSV output formats

### Analysis Capabilities
- **[Computer Vision Pipeline](../autodense/vision/)** - Deterministic image analysis
- **[MW Calibration](../autodense/vision/mw_calibration.py)** - Vendor ladder systems
- **[Detection Algorithms](../autodense/vision/detection.py)** - Lane and band detection
- **[Overlay Generation](../autodense/vision/overlay_labels.py)** - Visual result rendering

## 🔧 Development Resources

### Project Management
- **[Changelog](CHANGELOG.md)** - Version history and changes
- **[Release Notes](RELEASE_NOTES.md)** - User-facing updates  
- **[AutoDense Autotune](README_AUTOTUNE.md)** - Parameter optimization system
- **[Contributing Guide](CONTRIBUTING.md)** - Development guidelines
- **[Deprecations](DEPRECATIONS.md)** - Sunset timeline for old features

## 📚 Legacy Documentation

**⚠️ Historical Archive:** Pre-Python migration documentation has been moved to preserve development history while preventing confusion with current architecture.

- **[Legacy Java Documentation](../autodense/legacy/docs/)** - Complete archive of Java/ImageJ era docs
- **[Legacy Issues](../autodense/legacy/docs/Issues/)** - Historical problem reports  
- **[Legacy Session Summaries](../autodense/legacy/docs/SessionSummaries/)** - Development session logs
- **[Legacy Architecture Docs](../autodense/legacy/docs/StructureDocs/)** - Java system design

These documents provide historical context but are **completely obsolete** for current development.

---

## 📖 Documentation Standards

All documents should include:
- **Purpose:** What this doc is for
- **Scope:** What it covers (and doesn't)  
- **Owner:** GitHub handle of maintainer
- **Last-verified:** Date (YYYY-MM-DD)

## 📋 Documentation Templates

- **[New Document Template](NEW_DOC_TEMPLATE.md)** - Required Doc Meta block for all new docs
- **[Deprecation Template](DEPRECATED_TEMPLATE.md)** - For tombstoning old documents

**IMPORTANT:** All new/modified markdown documents MUST include the Doc Meta block. This is enforced by pre-commit hooks and CI.
