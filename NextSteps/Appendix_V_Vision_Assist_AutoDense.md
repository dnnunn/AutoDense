# Appendix V — Vision‑Assist Mode (Internal‑Use Hybrid Plan)

> This appendix layers an **internal-only, hybrid “vision‑assist” mode** onto the AutoDense plan you’ve already implemented (Phases I–III). It lets Gemini view **scrubbed ROI crops** to give advice (ROI, polarity, ladder lane candidates, and bounded parameter tweaks) **while the classical pipeline continues to produce all counts**. The goal is faster rescue on hard images without turning the system into a black box.

---

## V.1 Purpose & Non‑Goals

**Purpose**
- Use pixels selectively to improve recovery on edge cases (faint bands, smear, tilt, glare, plate gradients).
- Keep the pipeline deterministic: **Gemini advises, AutoDense measures**.

**Non‑Goals**
- Gemini does **not** write final numbers or masks.
- No permanent storage of raw images; only small advice JSON + config fingerprints are persisted.
- No changes to Phases I–II truth‑preservation rules.

---

## V.2 Config Extensions (unified schema)

Add the following to your YAML under the same top‑level as `detect` and `segmentation`:

```yaml
vision:
  mode: "off"             # off | assist | qc
  max_side_px: 1024       # downscale longest side for crops
  include_stage: "stage1_norm"   # stage0_input | stage1_norm | overlay
  crop: "auto"            # auto | bbox:[x0,y0,w,h]
  scrub:
    strip_exif: true
    blur_text: true       # blur rim annotations on plates/gels
    grayscale_gels: true  # gels only

  # Bounded knobs Gemini may change (safeguards)
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

> These ranges keep advice creative but safe; they map directly to `detect.{lanes,bands}` and `segmentation` from your unified schema.

---

## V.3 Orchestrator Flow (Phase III extension)

1) **Baseline run** via Python orchestrator (ensure Java CLI is called with `--no-exit`).  
2) **Gate check** — trigger assist if any of the following is true:
   - `lanes_raw == 0` or `bands_raw == 0`
   - `baseline_post_med ≈ 0` or `profile_zero_frac_after > 0.2`
   - `coverage_total < τ` (e.g., 0.2) or `count_stability_score < τ`
   - colonies: `mask_not_binary` or `final_count << components_raw`

3) **If `vision.mode=assist` OR a gate fired:**
   - Generate scrubbed crop (ROI auto/bbox, downscale, EXIF stripped, optional rim‑text blur; grayscale for gels).
   - Build **advisor request** (see V.4) with metrics/observation, crop path, config subset, and allowlist bounds.
   - Validate the returned patch against allowlist + ranges. Reject if out‑of‑bounds or malformed.
   - Apply the patch → rerun → **accept only if** (coverage ↑ or stability ↑) **and** no physics violations (e.g., ladder R² not worse, lane geometry sane).

4) **Persist artifacts**
   - Per attempt: `attempt_###/run_report.json`, `attempt_###/advice.json`
   - Final: `final_report.json` + `before_after` deltas + `optimization_mode`

**Log markers**
- `GEMINI: enabled|disabled`
- `VISION_ASSIST: crop=<path> include_stage=<stage>`
- `ADVICE_APPLIED: { ... } | ADVICE_REJECTED: <reason>`
- `OPT_MODE: telemetry_only|vision_assist|qc`

---

## V.4 Advisor Contract

**Request (from orchestrator)**

```json
{
  "task": "sds_page | etbr | colonies",
  "metrics": { "...": "rich telemetry from pipeline" },
  "observation": { "...": "baseline health, geometry, coverage, stability" },
  "crop_path": "sandbox:/runs/.../assist_crop.png",
  "config_subset": { "detect": { "lanes": {...}, "bands": {...} }, "segmentation": {...} },
  "bounds": { "...": "from vision.allowlist" },
  "ask": ["roi","polarity","ladder_lane","param_tweaks"],
  "version": { "app": "AutoDense X.Y", "model": "Gemini-…" }
}
```

**Response (from advisor)**

```json
{
  "patch": {
    "detect": {
      "bands": {
        "baseline": { "window_frac": 0.012, "quantile": 0.10 },
        "min_peak_distance_px": 12
      }
    }
  },
  "why": "Vertical profiles appear over-flattened; a smaller band baseline and min-dist will recover weak bands.",
  "confidence": 0.78,
  "notes": ["ladder likely at lane 2", "tilt < 2°"],
  "roi_hint": { "x0": 18, "y0": 12, "w": 780, "h": 520 },
  "ladder_lane_hint": 2
}
```

**Acceptance rule (must hold to keep the patch)**
- `(coverage_total_after > coverage_total_before OR stability_after ≥ stability_before)`  
- AND no physics violations (ladder R² not worse; lane geometry scores not degraded).

---

## V.5 Minimal Modules to Add (Python)

- `autodense_autotune/vision/cropper.py` — ROI finding, downscale, EXIF strip, blur rim text.  
- `autodense_autotune/vision/advisor.py` — request building, model call, response parsing.  
- `autodense_autotune/vision/allowlist.py` — bounds validation util.  
- `autodense_autotune/vision/gating.py` — trigger + accept/reject logic.  
- `autodense_autotune/vision/logging.py` — emits the markers above.

> These sit beside the orchestrator; Java detectors are unchanged.

---

## V.6 Run‑Report Additions (truth preserved)

Append the following fields to `final_report.json` (and attempts as needed):

```json
{
  "optimization_mode": "telemetry_only | vision_assist | qc",
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

**Keep reporting raw vs reconciled counts** as in Phase I; do not overwrite measurements with priors.

---

## V.7 Tiny Safety Nets (internal‑friendly)

- `vision.retain_crops: false` by default; keep only `advice.json` + config fingerprints.
- Always re‑run deterministically after applying a patch; **those** outputs are official.
- Provide a `--no-vision` flag (or server toggle) to fall back during outages or model hiccups.

---

## V.8 Rollout Checklist (2 days)

**Day 1**
- Add config keys + log markers.
- Implement cropper + allowlist validator; plumb `vision.mode` through orchestrator.
- Use a **mock advisor** that returns a trivial safe patch to validate the end‑to‑end flow.

**Day 2**
- Wire real advisor.
- Enable assist **on fail‑gates only** (`vision.mode: assist` but routed by `gating.py`).
- Verify three cases:
  1. **EtBr under‑lanes** → smaller **band baseline** and **min‑dist** raises bands/coverage.
  2. **SDS** → band `min_peak_distance_px` drops from ~32 → 12–16; ladder lane chosen from top‑K candidates by R².
  3. **Colonies** → switch to **Lab** color + Phansalkar radius ~25 + sane `min_area_px`, with a clear **filter‑chain ledger**.

---

## V.9 Quick Reference (what to send to the advisor)

- **Gels (lanes):** pre/post baseline med/max, `profile_zero_frac_after`, lane centers/widths arrays, spacing CV, parallelism, coverage numerators/denominators.  
- **Gels (bands):** per‑lane vertical profile med/max/zero‑frac, `bands.baseline_window_px_effective`, `bands.min_peak_distance_px_used`, ladder R² and candidate list.  
- **Colonies:** binary mask contract, filter‑chain counts (`raw → min_area → roundness → edge → watershed → final`), Lab‑b histogram bins, blue/white margins.  
- **Stability:** micro‑jitter spreads for counts/blue‑frac.  
- **Priors:** expected ranges (soft penalties only).

---

**Principle Recap:** *Gemini may “look” to advise; it never “decides” the numbers. All counts remain the output of your deterministic pipeline.*
