# CLAUDE.md (Concise Session Primer)

**Purpose:** Give ClaudeCode a clear, accurate picture of AutoDense's current state at the start of
every session.

---

## Project Summary

AutoDense is a Mac-native Fiji/ImageJ2 application for laboratory image analysis. As of August 2025, 
AutoDense has undergone a **fundamental paradigm shift** in how Google Gemini is utilized - transitioning
from a rigid tool orchestrator to an **intelligent parameter optimizer** with feedback loops.

The system maintains its **handle-based** architecture where images, overlays, and analysis results are 
referenced by IDs (`img_123`, `ov_456`), never raw pixels, but now enables Gemini to read actual 
performance metrics and iteratively optimize analysis parameters.

---

## 🔄 Paradigm Shift: Gemini as Intelligent Optimizer (August 2025)

**CRITICAL:** This is not an add-on feature but a **complete system revision** that unleashes Gemini's 
full potential instead of handicapping it with rigid orchestration.

### New Optimization Architecture

* **Autotune Loop:** Observe → Propose → Patch → Measure → Gate iterative optimization
* **Challenge Packs:** Task-specific optimization configurations (`sds_page_v1`, `colony_count_v1`, `etbr_v1`)
* **Parameter Grids:** Constrained search spaces for safe parameter exploration
* **Helper Critic System:** Second-opinion validation of proposed parameter changes
* **RunReport Schema:** Standardized metrics reporting for performance feedback
* **Python-Java Bridge:** Python optimization engine communicating with Java/ImageJ execution

### Dual Operation Modes

1. **Legacy Mode:** Traditional rigid tool orchestration (still supported for backwards compatibility)
2. **Optimization Mode:** Intelligent parameter tuning with performance feedback loops

### Key Files & Components

* `autodense_autotune/` - Core optimization engine (Python)
* `challenge_packs/` - Task-specific optimization configurations  
* `prompts/orchestrator.system.md` - Gemini optimization prompt
* `prompts/helper.system.md` - Critic validation prompt
* `configs/*.yaml` - Parameter configuration files

---

## Core Capabilities (Implemented)

* **Gel densitometry:** Lane/band detection, protein quantification, MW calibration,
  BandAssist (user-guided band propagation).
* **Colony analysis:** Counting, classification, growth tracking, X-gal blueness scoring.
* **PCR analysis:** Semi-quantitative ΔΔI with housekeeping normalization.
* **Statistics:** MW-aware comparisons, Holm–Bonferroni corrections, volcano plots.
* **Interface:** Embedded chat with Gemini orchestration, voice input, document upload,
  structured session logging.

---

## Current Architecture

### Legacy Architecture (Backwards Compatible)
* **GeminiOrchestrator** → Builds JSON tool plans from user commands.
* **ImageJ tools** → Execute all image operations deterministically.
* **SessionStore** → Persists state (images, overlays, analyses).
* **Handle system** → All references by IDs only.
* **SessionLogger** → Logs conversations, tool calls, results, API interactions.

### New Optimization Architecture (Primary)
* **AutotuneRunner** → Python optimization loop with metric feedback
* **GeminiOrchestrator** → Now acts as intelligent parameter optimizer
* **HelperCritic** → Second-opinion validation system for safety
* **RunReports** → Performance metrics fed back to optimization loop
* **PatchProposals** → Machine-readable parameter change specifications
* **ChallengeSpecs** → Task-specific optimization configurations

**Updated Design Rules:**

1. Gemini **never** processes pixels or describes images. *(unchanged)*
2. Gemini **can** emit structured tool calls OR parameter optimization proposals.
3. ImageJ **executes** tools; Python **optimizes** parameters; Gemini **orchestrates** both.
4. State **always** persists in SessionStore via handles. *(unchanged)*
5. Optimization loop allows iterative parameter refinement with safety gates.

For a more detailed description of the Architecture see:

/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/Architecture.md

---

## Development & Usage

* **Java 17 + Maven** single-module build.
* **Run:** `EnhancedImageJLauncher` with `GEMINI_API_KEY`.
* **API key** must be valid (`AIza…`), set via env var, system property, or `api-config.properties`.

---

## Known Issues (Aug 2025)

* Preset `SDS_PAGE_DENSITOMETRY` mis-routes parameters (lane_count to `open_image`).
* Band detection often skipped; overlay doesn’t refresh reliably.
* ROI Manager pops up unintentionally.
* `open_image` fails without path but plan continues.
* Large images consume significant RAM.

---

## Recent Enhancements

### 🚀 Major: Paradigm Shift to Intelligent Optimization (August 2025)
* **Complete system revision** from rigid orchestration to intelligent parameter optimization
* **Autotune system integration** with Python-Java bridge for iterative improvement
* **Challenge pack framework** for task-specific optimization configurations
* **Helper critic system** for safe parameter change validation
* **Dual-mode operation** supporting both legacy and optimization workflows

### Previous Enhancements
* Protein quantification with standard curves, LOQ/LLOQ, %CV.
* LaneComparator with MW binning + Holm–Bonferroni correction.
* Semi-quantitative PCR with ΔΔI normalization.
* BandAssist interactive band propagation with confidence scoring.
* Time-series colony tracking + plate alignment + X-gal blueness analysis.
* Full session logging in JSONL with sanitized keys and metadata.

---

## Immediate Priorities

### 🎯 Optimization System Priorities
1. **Complete autotune runner integration** with actual ImageJ analysis pipelines
2. **Implement result persistence** for optimization workflow state management  
3. **Validate challenge pack specifications** against real analysis scenarios
4. **Test Python-Java bridge reliability** under various optimization loads

### Legacy System Maintenance
5. Fix preset wiring (ensure `detect_lanes` + `detect_bands` run correctly)
6. Suppress ROI Manager; rely on overlays only
7. Enforce overlay refresh in UI after optimization cycles

---

## Security Notes

* API keys never committed.
* Session logs sanitize sensitive info.
* Network calls send base64 images to Gemini.

---

## 📝 Documentation Standards (MANDATORY)

### **All New/Modified Documents Must Include:**

**Required Doc Meta Block (at top of every .md file):**
```markdown
> **Doc Meta**
> - **Purpose:** Brief description of what this document is for
> - **Scope:** What it covers (and what it doesn't)
> - **Owner:** @github-handle 
> - **Last-verified:** 2025-08-26
```

### **Documentation Rules:**
1. **NEVER create/edit a .md file** without the Doc Meta block
2. **ALWAYS update Last-verified** when making substantial changes
3. **Use present date** (YYYY-MM-DD format) for new documents
4. **Include Purpose & Scope** - be specific about boundaries
5. **Assign clear ownership** - use actual GitHub handle

### **Quality Gates:**
- Pre-commit hooks **will reject** .md files missing Doc Meta
- Tombstone policy **enforces** 180-day verification freshness  
- CI pipeline **validates** all documentation standards

### **Templates Available:**
- `docs/DEPRECATED_TEMPLATE.md` - For tombstoning old docs
- Standard Doc Meta block - Copy from any recent document

**This rule applies to ALL documentation: technical specs, guides, planning docs, and reference material.**

## Delta Changelog (Last Updated: Aug 28, 2025)

### 🚀 Major System Revision (August 2025)

* **PARADIGM SHIFT:** Complete transition from rigid Gemini orchestration to intelligent parameter optimization
* **Autotune system integrated:** Python-Java bridge with Observe→Propose→Patch→Measure→Gate loops  
* **Challenge pack framework:** Task-specific optimization configs (`sds_page_v1`, `colony_count_v1`, `etbr_v1`)
* **Helper critic system:** Second-opinion validation for safe parameter changes
* **Dual-mode architecture:** Legacy orchestration + new optimization workflows
* **Python environment:** Full scientific stack (numpy, scipy, scikit-image, matplotlib) integrated

### ✅ Previous Stable Features

* **Session logging** stable: JSONL logs capture conversations, tool calls, results, API metadata
* **BandAssist** integrated: user click → Rf propagation across lanes with confidence scoring
* **Time-series colony analysis:** plate alignment, colony tracking, X-gal blueness quantification
* **PCR analysis:** ΔΔI normalization with housekeeping band selection
* **MW-aware statistics:** lane comparator with Holm–Bonferroni correction and volcano plots
* **Gemini API validation** hardened: placeholder keys blocked, clear error messages on startup

### 🚧 In Progress

* **Optimization workflow persistence:** State management for iterative parameter tuning cycles
* **Challenge pack validation:** Testing real-world optimization scenarios
* **Python-Java bridge reliability:** Ensuring stable communication under optimization loads

### 🔗 Critical Integration Requirements (September 2025)

**IMPORTANT: `--no-exit` Flag for Gemini Optimizer Integration**

* **Issue**: `System.exit(0)` added to fix CLI timeout issues **BREAKS** iterative optimization workflow
* **Solution**: `--no-exit` flag implemented in AutotuneAnalysisCLI.java
* **Usage**: When calling from Python optimizer, ALWAYS include `--no-exit` flag:
  ```bash
  java -cp ... com.betterdairy.autodense.cli.AutotuneAnalysisCLI --no-exit sds_page input.jpg config.yaml output/
  ```
* **Why Critical**: Without `--no-exit`, each analysis terminates the JVM, breaking optimization loops
* **Behavior**: 
  - Normal CLI usage: Calls `System.exit(0)` to prevent hanging threads
  - With `--no-exit`: Returns normally, allowing continued execution for optimization cycles
  - Error handling: Throws RuntimeException instead of `System.exit(1)` when `--no-exit` is used

### 🎯 Next Targets

1. **Validate autotune performance** on representative gel/colony/PCR datasets
2. **Implement result caching** for optimization workflow efficiency  
3. **Extend challenge packs** with additional analysis scenarios
4. **Performance benchmarking** of optimization vs legacy modes
5. **User interface integration** for optimization controls and feedback
- before running any build, you must look at this file /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/StructureDocs/Absolute_Paths.md
- always consult DEFINITIVE_BUILD_REFERENCE.md before any build/compile/test
   operation to use the correct paths, environments, and procedures