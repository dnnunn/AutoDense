# CLAUDE.md (Concise Session Primer)

**Purpose:** Give ClaudeCode a clear, accurate picture of AutoDense's current state at the start of
every session.

---

## Project Summary

AutoDense is a Mac-native Fiji/ImageJ2 application for laboratory image analysis. It is controlled via
**Google Gemini**, which acts strictly as a planner/orchestrator. ImageJ executes all pixel operations.
The system is **handle-based**: images, overlays, and analysis results are referenced by IDs
(`img_123`, `ov_456`), never raw pixels.

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

* **GeminiOrchestrator** → Builds JSON tool plans from user commands.
* **ImageJ tools** → Execute all image operations deterministically.
* **SessionStore** → Persists state (images, overlays, analyses).
* **Handle system** → All references by IDs only.
* **SessionLogger** → Logs conversations, tool calls, results, API interactions.

**Design Rules (non-negotiable):**

1. Gemini **never** processes pixels or describes images.
2. Gemini **only** emits structured tool calls.
3. ImageJ **only** executes tools, never plans.
4. State **always** persists in SessionStore via handles.
5. One tool call at a time; deterministic execution.

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

* Protein quantification with standard curves, LOQ/LLOQ, %CV.
* LaneComparator with MW binning + Holm–Bonferroni correction.
* Semi-quantitative PCR with ΔΔI normalization.
* BandAssist interactive band propagation with confidence scoring.
* Time-series colony tracking + plate alignment + X-gal blueness analysis.
* Full session logging in JSONL with sanitized keys and metadata.

---

## Immediate Priorities

1. Fix preset wiring (ensure `detect_lanes` + `detect_bands` run correctly).
2. Implement lane-wise background subtraction for uneven stain.
3. Suppress ROI Manager; rely on overlays only.
4. Enforce overlay refresh in UI.
5. Add debug logging for lanes/bands at each step.
6. Integrate MW calibration into default gel workflow.

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

## Delta Changelog (Last Updated: Aug 26, 2025)

### ✅ Recent Fixes / Additions

* **Session logging** now stable: JSONL logs capture conversations, tool calls, results, API metadata.
* **BandAssist** fully integrated: user click → Rf propagation across lanes with confidence scoring.
* **Time-series colony analysis** added: plate alignment, colony tracking, X-gal blueness quantification.
* **PCR analysis** extended: ΔΔI normalization with housekeeping band selection.
* **MW-aware statistics** implemented: lane comparator with Holm–Bonferroni correction and volcano plots.
* **Gemini API validation** hardened: placeholder keys blocked, clear error messages on startup.

### 🚧 In Progress

* **SDS_PAGE_DENSITOMETRY preset** : mis-routed parameters (lane_count → open_image); fix needed to ensure lanes + bands detected.
* **Overlay refresh** : current overlays sometimes fail to update in ImageJ window.
* **ROI Manager suppression** : prevent popup, rely only on overlays.
* **Lane-wise background subtraction** : helper in development (quantile-based baseline).
* **Debug logging** : need concise lane/band histograms per run.

### 🎯 Next Targets

1. Correct preset wiring so lane/band detection executes as expected.
2. Integrate lane-wise background into preprocessing defaults.
3. Guarantee overlay refresh after every tool call.
4. Add per-step debug summaries: `{lanes_found, bands_per_lane}`.
5. Extend preset to include MW calibration + ladder labeling.
