# AutoDense Minimal UI Spec — “Drop, See, Ask”

**Goal:** A single-screen app where a scientist drops an image and immediately sees **overlay + key counts**. From there, they simply **ask** for a workflow (“compare lanes”, “ΔΔI”, “protein quant”, “track colonies”, etc.). Gemini translates natural language to bounded tool calls; the classical pipeline measures truth.

---

## 1) Design Principles

- **Zero-friction ingest:** Drop → auto-run → overlay. No setup dialogs.
- **Deterministic core:** Counts come from the classical pipeline; AI only proposes bounded parameter tweaks.
- **One brain cell UI:** One screen; one **Optimize** button; one **command bar** for natural language.
- **Fast feedback:** Always show the latest overlay (even imperfect) with tiny helpful prompts.
- **Context first:** All actions apply to the **current run** unless the user explicitly picks a different one.

---

## 2) Screen Layout (Single View)

**Top bar**
- **Open** (file/folder) — accepts single images and folders.
- **Series** — opens the time-series builder drawer (for colony sequences).
- **Optimize** — runs the assist loop (telemetry-only first, vision-assist if fail-gates or user asks).
- **Export** — zips tables, overlays, and `final_report.json` for the current run.

**Left panel — Runs**
- List of recent runs (filename/series name • timestamp • badge: success / warn).
- Click to load any run; selection updates the center and right panels.

**Center — Image + Overlay**
- Always show the **latest overlay** (`overlay.png`) for the selected run.
- Hotkeys:
  - **L** — mark ladder lane (click lane).
  - **I** — invert gel polarity.
  - **R** — rerun with current small deltas (applies bounded patch and re-executes).

**Right panel — Summary & Tiny Controls**
- **Gel**: lanes found, bands total, ladder R², coverage, stability
  - Tiny sliders (bounded): **Sensitivity** (±10% prominence), **Band spacing** (±10% min-dist)
  - Buttons: **Mark Ladder**, **Optimize**
- **Colonies**: total count, blue/white counts, size bins
  - Toggles: **Shading correction**, **Lab color**
  - Button: **Optimize**

**Bottom — Command Bar**
- Free-text prompt: “what should I do next?” → Gemini emits intent/slots/patch.
- History drawer shows last commands and results.

---

## 3) State Machine

`Idle → Ingest → AutoAnalyze → Present → NextAction`

- **Ingest**: file/folder gets a **Run ID** and workdir.
- **AutoAnalyze**:
  - If gel: determine assay (SDS vs EtBr), find lanes + ladder + bands + MW map.
  - If plate: segment colonies, classify blue/white, size-bin.
- **Present**: overlay shows; summary populated.
- **NextAction**: user clicks **Optimize** or asks for a workflow in the command bar.

**Fail-gates** (auto-suggest Optimize, no modal blockers):
- gels: `lanes_raw==0` or `bands_raw==0` or `baseline_post_med≈0`
- colonies: `mask_not_binary`, `final_count ≪ components_raw`, or stability low.

---

## 4) Auto-Run Specs (no clicks)

### Gels (SDS / EtBr)
- Detect assay (heuristic on polarity/channel).
- Run unified `detect.{lanes,bands}` with safely-clamped baseline windows.
- Ladder detection: try top-K lane candidates, pick highest R².
- Render **overlay** with lane IDs, band marks, MW labels; show brief summary.

### Colonies
- Enforce binary mask contract; segment, filter, watershed (if enabled).
- Compute Lab b*; color-code blue/white; size-bin grid.
- Render overlay + summary + filter-chain ledger counts.

---

## 5) Time-Series Builder (Colonies)

**Flow**
- Click **Series** → drawer shows “Drop images” area + thumbnail strip.
- Thumbnails are **ordered by timestamp** (EXIF → filename → mtime).
- **Deduplicate** by perceptual hash; warn on near-duplicates.
- **Process series** button runs `colony_timeseries_v1` across the manifest.

**Manifest format — `series_manifest.json`**
```json
{
  "series_id": "plate_42",
  "created_utc": "2025-09-02T11:05:00Z",
  "items": [
    {"path": "/data/plates/plate_42_t0.jpg", "timestamp": "2025-08-29T10:00:00Z", "phash": "f3a1b2..."},
    {"path": "/data/plates/plate_42_t1.jpg", "timestamp": "2025-08-30T10:00:00Z", "phash": "e0cc91..."}
  ]
}
```
- Users can add images over time; pressing **Process series** re-runs with the updated manifest.
- The manifest lives under the run folder to keep provenance tight.

---

## 6) Orchestrator Interface (Command Bar)

Gemini maps NL → **function schema**. The UI posts JSON to your orchestrator (or shell-invokes the CLI).

**Common request envelope**
```json
{
  "intent": "<one of: analyze_gel | analyze_colonies | optimize_params | lane_compare | protein_quant | semi_qpcr | band_assist | colony_timeseries | colony_assist>",
  "assay":  "sds_page | etbr | colonies",
  "input":  { "path": "<file-or-dir>", "roi": [x0,y0,w,h], "ladder_lane_hint": 2 },
  "preferences": { "bias": "balanced", "expected_lanes": null, "bands_per_lane_min": null },
  "vision_mode": "off | assist | qc",
  "patch": {
    "detect": {
      "lanes": { "min_peak_distance_frac": 0.03, "prominence_frac": 0.05 },
      "bands": { "baseline": { "method":"percentile","window_frac":0.012,"quantile":0.10 },
                 "min_peak_distance_px": 12 }
    },
    "segmentation": { "threshold": {"method":"Phansalkar","radius":25},
                      "min_area_px": 40, "colorspace":"Lab", "blue_cutoff_b": -4.8 }
  },
  "budget": { "max_attempts": 4, "max_minutes": 5 }
}
```

**Examples**

*A. “Quantify protein with log‑linear; report LOQ/LLOQ and %CV.”*
```json
{ "intent":"protein_quant", "assay":"sds_page",
  "input":{"path":"./gels/runA","sample_sheet":"./gels/runA/sheet.csv"},
  "preferences":{"curve_model":"log-linear"} }
```

*B. “Compare treatment vs control in 5 kDa bins; Holm–Bonferroni at 0.05.”*
```json
{ "intent":"lane_compare", "assay":"sds_page",
  "input":{"path":"./gels/compare1","sample_sheet":"./sheet.csv"},
  "preferences":{"bins":"5kDa","multiple_testing":"holm-bonferroni","alpha":0.05} }
```

*C. “ΔΔI with WT_1 as calibrator; 95% CI.”*
```json
{ "intent":"semi_qpcr", "assay":"etbr",
  "input":{"path":"./qpcr/runB","sample_sheet":"./qpcr/sheet.csv"},
  "preferences":{"calibrator":"WT_1"} }
```

*D. “Propagate bands; min confidence 0.8.”*
```json
{ "intent":"band_assist", "assay":"sds_page",
  "input":{"path":"./gels/runC","seeds":"./runC/seeds.json"},
  "preferences":{"min_confidence":0.8} }
```

*E. “Track colonies across days; ECC align; classify X‑gal.”*
```json
{ "intent":"colony_timeseries", "assay":"colonies",
  "input":{"path":"./plates/exp42"}, "preferences":{"registration":"ECC","colorspace":"Lab"} }
```

*F. “Open colony assist; I want to reclassify a few.”*
```json
{ "intent":"colony_assist", "assay":"colonies", "input":{"path":"./plates/plate_99"} }
```

---

## 7) Tiny Controls → Bounded Patches

Sliders and toggles emit **small deltas** within the allowlist, then re-run:

- Sensitivity ↑ → `bands.prominence_frac: -10%`
- Band spacing ↓ → `bands.min_peak_distance_px: -10%`
- Shading correction ON → sets preprocessing flag and re-run
- Lab color ON → `segmentation.colorspace: "Lab"`

The orchestrator **accepts** a re-run only if quality gates hold (e.g., coverage ↑ or stability ≥; ladder R² not worse).

---

## 8) Events & Telemetry (UI-to-Orchestrator)

**Events the UI posts**
- `ingest:file|folder`
- `optimize:clicked`
- `command:submit` (payload: the NL string + parsed JSON)
- `series:add_items` (payload: manifest delta)
- `ladder:set_lane` (lane index)
- `rerun:bounded_patch` (small deltas)

**Telemetry the UI reads (from run_report.json)**
- Gels: lane/band counts, ladder R², coverage, stability, profile zero-fractions
- Colonies: counts, blue fraction, size bins, filter-chain ledger
- Time-series: alignment RMS, continuity %, growth R²
- Optimization mode & advice: `optimization_mode`, `advice.patch`, `before_after`

---

## 9) Files & Artifacts

Each **run folder** contains:
- `overlay.png` (what the center shows)
- `run_report.json` (summary + telemetry + advice if any)
- tables/plots as requested by workflows
- (optional) `series_manifest.json` for time-series

**Export** bundles these into `export_<run_id>.zip` for easy sharing.

---

## 10) Acceptance Criteria (flip-the-switch list)

1. **Gel ingest** → lanes, bands, MW labels, summary in ≤ a few seconds; no prompts.
2. **Plate ingest** → mask + blue/white + size bins; filter-chain ledger visible.
3. **Optimize** improves a “bad” run without exposing expert-only jargon.
4. **Command bar** successfully executes: lane_compare, protein_quant, semi_qpcr, band_assist, colony_timeseries, colony_assist.
5. **Series builder** supports drag-drop of multiple images, maintains a manifest, and processes the series with alignment/track outputs.
6. **Rerun via sliders/toggles** only within allowlisted bounds; results accepted only if quality gates hold.
7. **Export** creates a single zip with overlays, tables, and final report.

---

## 11) Minimal Shell API (for a thin renderer)

From the renderer you can shell out to the orchestrator:

```bash
# Auto-run (gel)
python -m autodense_autotune.cli run \
  --spec challenge_packs/sds_basic_v1/spec.yaml \
  --input /path/to/gel_or_dir \
  --workdir runs/<run_id>

# Auto-run (colonies)
python -m autodense_autotune.cli run \
  --spec challenge_packs/colonies_basic_v1/spec.yaml \
  --input /path/to/plate_or_dir \
  --workdir runs/<run_id>

# Execute a workflow (example: lane_compare)
python -m autodense_autotune.cli run \
  --spec challenge_packs/lane_compare_v1/spec.yaml \
  --input /path/to/gel_or_dir \
  --workdir runs/<run_id>
```

> The UI watches `<workdir>` for `overlay.png` and `run_report.json` and refreshes the view when they appear or change.

---

## 12) Optional: Keyboard Shortcuts

- **L** mark ladder
- **I** invert polarity
- **R** rerun with current bounded patch
- **⌘/Ctrl + K** focus command bar
- **←/→** switch recent runs

---

## 13) Glossary (for the team)

- **Fail-gates**: fast checks that indicate we should try **Optimize** (e.g., zero bands, flat profiles, non-binary mask).
- **Allowlist**: central bounds for safe parameter changes; all UI deltas and Gemini patches must pass validation.
- **Vision-assist**: optional use of scrubbed crops so Gemini can advise; never writes final counts.
- **Truth preservation**: final numbers always come from the deterministic pipeline.

---

**That’s it.** Drop → See → Ask. The rest is just good defaults and honest telemetry.
