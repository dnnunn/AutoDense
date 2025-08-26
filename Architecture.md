# ARCHITECTURE.md (Detailed Reference)

> **Doc Meta**
> - **Purpose:** Detailed technical reference for AutoDense's handle-based architecture
> - **Scope:** System components, data flow, design principles, and implementation details
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## Overview

AutoDense is a **Mac-native Fiji/ImageJ2 application** with AI-assisted orchestration. The architecture
enforces a **handle-based system**: Gemini (planner) never processes pixels, only generates tool call JSON;
ImageJ (executor) performs all actual analysis.

---

## Core Components

### 1. **GeminiOrchestrator**

* Manages conversation history + system prompt enforcement.
* Translates natural language → structured JSON tool calls.
* Handles tool plan execution, retries, and error handling.

### 2. **SessionStore**

* Central state manager.
* Stores and retrieves images (`img_xxx`), overlays (`ov_xxx`), analyses (`analysis_xxx`).
* Ensures no raw pixels are passed to Gemini.

### 3. **GelAnalysisTools**

* Implements lane/band detection, MW calibration, BandAssist.
* Wraps ImageJ operations into deterministic tool calls.
* Provides structured outputs (counts, overlays, quantification results).

### 4. **ColonyAnalysisTools**

* Implements colony counting, classification, time-series tracking.
* X-gal blueness quantification + growth rate analysis.

### 5. **PCRAnalysisTools**

* Housekeeping normalization, ΔΔI, relative copy number estimates.
* CSV export with raw + normalized values.

### 6. **SessionLogger**

* Logs conversations, tool calls, session events, API calls.
* Output format: JSONL with sanitized metadata.
* Used for debugging, QA, and reproducibility.

### 7. **GelUI**

* Swing-based interface embedding chat.
* Supports drag-drop images, menus, and voice input.
* Calls **GeminiOrchestrator** exclusively (never Gemini API directly).

---

## Workflow Example (Gel Densitometry)

```
User Command → GeminiOrchestrator → Gemini API → Tool JSON
→ ImageJ Tools (detect_lanes, detect_bands, etc.)
→ SessionStore (handles, overlays, analysis results)
→ Results returned to user
```

---

## Handle-Based Principles

* **Gemini only plans** : emits tool calls, never vision.
* **ImageJ only executes** : runs tools, never plans.
* **Handles only** : images, overlays, analyses referenced by IDs.
* **State only in SessionStore** : never in Gemini memory.
* **One tool call at a time** : no parallel pixel ops.

---

## Known Limitations

* Memory usage high for 4K images in SessionStore.
* Overlay refresh occasionally fails in ImageJ.
* ROI Manager still spawns in some workflows.
* Sequential execution can be slower than parallelization.

---

## Delta Changelog (Architecture)

### ✅ Recent

* Migrated to strict **handle-based system** (no pixel passing).
* Added **SessionLogger** with structured JSONL logs.
* Refactored **BandAssist** into modular tools (`enable/configure/disable`).
* Integrated **TimeSeriesColonyTracker** and plate alignment.

### 🚧 In Progress

* Fix **preset wiring** for SDS_PAGE_DENSITOMETRY (misdirected lane_count).
* Implement **lane-wise background subtraction** (quantile baseline).
* Add **overlay refresh hooks** to all tools.
* Consolidate tool surface into **CanonicalTools.java** (8 core actions).

### 🎯 Next Targets

1. Auto-calibration of MW ladder bands.
2. Workflow recording + replay.
3. Multi-image sessions + batch processing.
4. GPU-accelerated analysis kernels (long-term).
