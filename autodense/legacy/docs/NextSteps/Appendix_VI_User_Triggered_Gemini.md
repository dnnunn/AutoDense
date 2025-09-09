# Appendix VI — User‑Triggered Gemini Behavior (Natural‑Language Control & Feedback‑Driven Assist)

> This appendix specifies how *user feedback* and *plain‑English requests* activate Gemini to adjust analysis **safely**. Gemini converts lab phrasing into bounded parameter patches and orchestration choices; the classical pipeline remains the source of truth for lanes, bands, and colonies.

---

## VI.1 Purpose & Scope

- **Purpose:** Allow scientists to steer analyses using natural language and explicit “this is wrong” feedback, without bypassing truth‑preserving rules.
- **Scope:** SDS‑PAGE, EtBr gels, and colony counting. Applies to optimization (assist loop) and one‑shot runs (analyze now). Integrates with Appendix V (Vision‑Assist) but does not require pixels.

**Non‑Goals**
- Gemini does **not** emit final counts or masks.
- Priors/targets influence **selection**, not **measurement**.
- All applied changes are bounded by a central allowlist.

---

## VI.2 Triggers

Gemini’s optimization/assist mode must trigger when **any** of these occurs:

1) **Explicit user feedback** (UI/CLI/NL): e.g., “under‑counted bands,” “please favor recall,” “these lane calls look wrong.”  
2) **Feedback file** present: `user_feedback.yaml` in the run directory.  
3) **Fail‑gates trip** (from pipeline telemetry): `lanes_raw==0`, `bands_raw==0`, `baseline_post_med≈0`, `coverage_total<τ`, unstable counts, non‑binary plate mask, etc.  
4) **Operator intent**: natural‑language “optimize this” or “rerun with more sensitivity.”

Each trigger sets `trigger_source ∈ { user_request, feedback_file, fail_gates }` in the run log/report.

---

## VI.3 Feedback Schema (YAML)

Place alongside the input image(s) to activate a guided re‑run:

```yaml
user_feedback:
  reason: "under-counted bands"
  targets:
    lanes: 12                 # desired lane count (soft)
    bands_per_lane_min: 4     # soft minimum per lane
  priorities:
    bias: "recall"            # recall | balanced | precision
    preserve_ladder_fit: 0.95 # hard lower bound on R²
  hints:
    ladder_lane_hint: 2
    roi_hint: { x0: 18, y0: 12, w: 780, h: 520 }
  bounds_overrides:           # optional, still intersected with global allowlist
    bands.min_peak_distance_px: [8, 16]
    bands.baseline.window_frac: [0.008, 0.020]
```

**Contract**
- Treated as **soft priors**. Raw counts are never overwritten.
- `bounds_overrides` can **tighten** ranges but cannot exceed global allowlist.

---

## VI.4 Orchestrator Behavior (Policy & Flow)

1) **Baseline run** with current config; record telemetry.  
2) **Build target score** from feedback/priorities:  
   - `prior_match = α·(−|lanes_raw − lanes_target|) + β·(−miss_bands)`  
   - `score = w1·coverage + w2·stability + w3·geometry + w4·prior_match − w5·violations`
3) **Propose patch**  
   - From NL intent (Section VI.7) or `user_feedback.yaml` → bounded parameter deltas (per allowlist).  
   - Optional Vision‑Assist crops if `vision.mode=assist` (Appendix V).
4) **Apply → Re‑run → Evaluate**  
   - **Accept** only if `(coverage↑ OR stability≥)` **AND** no physics regressions (ladder R² not worse; lane geometry sane).  
   - Else **reject** and try next bounded tweak (within `budget`).
5) **Stop conditions**  
   - Score no longer improves; distance‑to‑targets within ε; or budget exhausted.
6) **Truth preservation**  
   - Persist `*_raw` counts; add `*_reconciled` only as “best candidate” **selection**, never as overwrite.

**Log markers to emit**
```
USER_FEEDBACK: {...}
OPT_MODE: telemetry_only|vision_assist|qc
TRIGGER: user_request|feedback_file|fail_gates
PROPOSED_PATCH: {...}
ADVICE_APPLIED|REJECTED: reason=...
BEFORE_AFTER: coverage: 0.31→0.48, lanes: 11→14, bands: 0→6, ladder_r2: 0.65→0.93
```

---

## VI.5 Report Additions (Run JSON)

Append to `final_report.json` (and per attempt as needed):

```json
{
  "optimization_mode": "telemetry_only | vision_assist | qc",
  "trigger_source": "user_request | feedback_file | fail_gates",
  "user_feedback_used": true,
  "targets": { "lanes": 12, "bands_per_lane_min": 4, "bias": "recall" },
  "distance_to_targets_before": { "lanes": 5, "bands_min": 3 },
  "distance_to_targets_after":  { "lanes": 1, "bands_min": 0 },
  "advisor": { "used": true, "model": "Gemini-…", "confidence": 0.78 },
  "advice": { "patch": { ... }, "why": "…", "roi_hint": {…}, "ladder_lane_hint": 2 },
  "before_after": {
    "coverage_total": [0.31, 0.48],
    "lanes_raw":      [11, 14],
    "bands_raw":      [0,  6],
    "ladder_r2":      [0.65, 0.93]
  }
}
```

---

## VI.6 Allowlist & Bounds (tie‑in)

Use the same central allowlist introduced in Appendix V (`vision.allowlist`) for **all** user‑triggered changes. Example (excerpt):

```yaml
allowlist:
  lanes:
    min_peak_distance_frac: [0.02, 0.06]
    prominence_frac:        [0.02, 0.12]
  bands:
    baseline.window_frac:   [0.005, 0.03]
    baseline.quantile:      [0.05, 0.20]
    min_peak_distance_px:   [6, 18]
  colonies:
    threshold.method:       ["Otsu", "Phansalkar"]
    threshold.radius:       [15, 35]
    min_area_px:            [20, 400]
    colorspace:             ["Lab"]
    blue_cutoff_b:          [-8.0, -3.0]
```

Any patch outside these ranges is rejected with `ADVICE_REJECTED`.

---

## VI.7 Natural‑Language Interface (Intents, Slots, Lexicon)

### VI.7.1 Intents
- `analyze_gel` (assay: `sds_page | etbr`)
- `analyze_colonies`
- `optimize_params` (assist loop)
- `rerun_with_feedback` (user asserts mis‑count)
- `compare_runs` (A vs B: coverage, R², stability)
- `summarize_results` (counts, R², coverage, stability)
- `export_reports` (tables/overlays)
- `set_defaults` (save config as lab preset)

### VI.7.2 Slots (extracted from NL)
- `assay` (sds_page | etbr | colonies)
- `input.path`, optional `roi`, `ladder_lane_hint`
- `preferences` (bias profile, expected lanes, bands per lane min)
- `vision_mode` (off | assist | qc)
- `budget` (max attempts/minutes)
- quantifier strength: slightly / a bit / aggressive

### VI.7.3 Domain Lexicon (editable YAML)
```yaml
synonyms:
  assay:
    sds_page: [sds, sds-page, coomassie, denaturing gel]
    etbr: [agarose, dna gel, ethidium, etbr]
    colonies: [plates, colony count, blue white]
  lanes.min_dist: [lane spacing, lane separation, lane distance]
  lanes.prominence: [lane threshold, lane sensitivity]
  bands.min_dist: [band spacing, band separation]
  bands.baseline.window: [band baseline window, vertical baseline]
  polarity: [bands dark, invert, white bands, bright bands]
  colonies.min_area: [min colony size, specks, dust filter]
  colonies.threshold: [otsu, phansalkar, local threshold]
bias_profiles:
  recall:    { lanes.prominence: -10%, bands.min_dist: -20%, bands.baseline.window: -20% }
  precision: { lanes.prominence: +10%, bands.min_dist: +15%, bands.baseline.window: +10% }
quantifiers:
  slightly: 0.10
  "a bit":  0.10
  somewhat: 0.20
  aggressive: 0.35
```

### VI.7.4 Function‑Calling Schema (unified)
```json
{
  "intent": "optimize_params | analyze_gel | analyze_colonies",
  "assay": "sds_page | etbr | colonies",
  "input": { "path": "<file-or-dir>", "roi": [x0,y0,w,h], "ladder_lane_hint": 2 },
  "preferences": { "bias": "recall|balanced|precision", "expected_lanes": 12, "bands_per_lane_min": 4 },
  "vision_mode": "off|assist|qc",
  "patch": {
    "detect": {
      "lanes": { "min_peak_distance_frac": 0.03, "prominence_frac": 0.05 },
      "bands": { "baseline": { "method": "percentile", "window_frac": 0.012, "quantile": 0.10 },
                 "min_peak_distance_px": 12 }
    },
    "segmentation": { "threshold": {"method":"Phansalkar","radius":25}, "min_area_px": 40,
                      "colorspace":"Lab", "blue_cutoff_b": -4.8 }
  },
  "budget": { "max_attempts": 6, "max_minutes": 10 }
}
```

### VI.7.5 Ambiguity Handling
If a critical slot is missing (e.g., assay, input), the model emits a `missing_slots` list for the UI/CLI to resolve **once**. Otherwise, default to last used values and **balanced** bias.

---

## VI.8 Guardrails

- **Truth preservation:** Only the classical pipeline produces counts/masks.  
- **Bounds enforcement:** All patches must validate against the allowlist.  
- **Quality gates:** Accept a patch only if coverage improves or stability doesn’t degrade, and no physics regressions (ladder R², geometry).  
- **Vision optional:** Vision‑assist only when enabled or fail‑gates fire (Appendix V).  
- **Reproducibility:** Persist advice JSON + config fingerprints; re‑run deterministically; only those outputs are official.

---

## VI.9 Few‑Shot NL Examples (for prompt)

**Example A — SDS: “be a bit more sensitive on bands”**
```json
{
  "intent":"analyze_gel","assay":"sds_page",
  "patch":{"detect":{"bands":{"prominence_frac":"-10%","min_peak_distance_px":"-10%"}}}
}
```

**Example B — EtBr: “this should have ~20 lanes; favor recall but keep ladder fit ≥0.95”**
```json
{
  "intent":"optimize_params","assay":"etbr",
  "preferences":{"expected_lanes":20,"bias":"recall"},
  "patch":{"detect":{"lanes":{"prominence_frac":"-10%","min_peak_distance_frac":"-15%"}}},
  "constraints":{"ladder_r2_min":0.95},"budget":{"max_attempts":6}
}
```

**Example C — Colonies: “count blue/white; lots of glare”**
```json
{
  "intent":"analyze_colonies","vision_mode":"assist",
  "patch":{"segmentation":{"threshold":{"method":"Phansalkar","radius":25},
                           "min_area_px":40,"colorspace":"Lab","blue_cutoff_b":-4.8}}
}
```

**Example D — Rerun with feedback file present**
```json
{ "intent":"rerun_with_feedback","assay":"sds_page","input":{"path":"./run1"} }
```

---

## VI.10 Rollout & Testing

**Checklist**
- Add `user_feedback.yaml` ingestion and `trigger_source` logging.
- Implement `distance_to_targets` metrics and selection score with a small `prior_match` weight.
- Wire NL parser to lexicon + function schema; validate patches against allowlist.
- Ensure report fields in VI.5 are appended on final and each attempt.

**NL→JSON test suite**
- Curate 20–30 common lab phrases; store **gold JSON** outputs.  
- CI task runs parser and diffs JSON to catch regressions.

**Dry‑runs**
- SDS under‑bands → patch reduces `bands.min_peak_distance_px`, shrinks band baseline window; coverage↑, bands↑, R² stable.  
- EtBr under‑lanes → small lane prominence/min‑dist reductions; lanes↑ without geometry collapse.  
- Colonies glare → switch to `Lab + Phansalkar`, sane `min_area_px`; filter‑chain ledger shows sensible retention.

---

**Principle Recap:** User voice is a *soft lever*, not a sledgehammer. Gemini translates lab language into small, bounded moves; your deterministic code measures the truth.
