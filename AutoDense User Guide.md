# AutoDense User Guide 

## 1) Install

```
# in your virtualenv
pip install -r requirements.txt
# if you haven’t already, add extras used by the UI:
pip install pandas matplotlib streamlit-drawable-canvas
```

## 2) Start the app

```
streamlit run ui/streamlit_app.py
```

You’ll see four tabs: **Analyze | Calibrate | Quantify | History** and a **Guided / Expert** toggle in the sidebar.

------

## 3) Analyze (the “happy path”)

1. **Upload** a gel image (PNG/JPG/TIFF).
2. **Choose Modality**:
   - **SDS** (protein gels; Invitrogen precast): Comb presets **10 / 12 / 15**.
   - **DNA** (EtBr/gelred; Bio-Rad Wide Mini-Sub GT): Comb presets **10 / 20**.
3. **Guided** mode uses safe defaults. **Expert** exposes min/max lanes, auto-retries, and label confidence threshold.
4. Click **Run analysis**. You’ll get:
   - **Original**, **Overlay** (lanes, bands), and **Labeled** (kDa/bp if calibrated).
   - **Status & metrics** (accept/review) with reasons.
5. **Mark ladder lanes**:
   - Click near the center of each ladder lane on the overlay (one click per ladder).
   - Or type “`1,10`” if your ladders sit at lanes 1 and 10.
   - Pick **Ladder type**: `pageruler_10_180` (protein) or `neb_1kb` (DNA), or leave **auto**.
6. After ladder selection, the app computes **kDa** (SDS) or **bp** (DNA) and draws labels.
7. **Download**:
   - **overlay.png** (without kDa/bp text)
   - **labeled_overlay.png** (with kDa/bp)

**Magnifier**: In Analyze, use the magnifier to click and zoom any region (great for fuzzy bands or questionable peaks).

------

## 4) Calibrate (trust the physics)

- Shows a semi-log fit (log10(value) vs normalized migration distance) and **residuals**.
- A **quality banner** warns if error is high:
  - Slider sets % residual threshold; banner flips to **error** if mean % error > threshold or max % error > 2× threshold.
- **Download calibration.csv** with `d_norm, value, pred, residual, abs_pct_error`.

**What to try if the fit looks bad**

- Re-click ladder lanes (ensure you clicked the actual ladder lanes).
- Switch **Ladder type** (e.g., `auto` → `neb_1kb`).
- Narrow **Comb** preset to match the tray (e.g., 10/12/15 or 10/20).
- In Expert: increase background radius or tweak min/max lanes to stabilize lane finding.

------

## 5) Quantify (numbers you can ship)

- Shows a bands table filtered by the **confidence** threshold from Analyze.
- **Normalize to lane** (pick a reference lane; adds `norm_intensity`).
- Includes **kDa** or **bp** when calibrated.
- **Download bands_quant.csv** directly.

------

## 6) Batch mode (datasets, reports, overlays)

Examples:

```
# SDS with PageRuler in lanes 1 and 10, full exports
python -m scripts.batch_infer \
  --dir /path/to/sds \
  --out out_sds \
  --modality sds \
  --ladder-lanes "1,10" \
  --ladder-type pageruler_10_180 \
  --export-coco out_sds/coco \
  --export-yolo out_sds/yolo \
  --labeled-overlay

# DNA with NEB 1 kb in lanes 1 and 20
python -m scripts.batch_infer \
  --dir /path/to/dna \
  --out out_dna \
  --modality dna \
  --ladder-lanes "1,20" \
  --ladder-type neb_1kb \
  --export-coco out_dna/coco \
  --export-yolo out_dna/yolo \
  --labeled-overlay
```

**What gets written (per image)**

- `<stem>_overlay.png` or `<stem>_labeled.png` (if calibrated + `--labeled-overlay`)
- `report.json` (audit trail: params, observer acceptance)
- In the run root: `lanes.csv`, `bands.csv`
- **COCO**: `out/.../coco/<stem>.json` (with measurement in `annotation.attributes.measurement`)
- **YOLO**: `out/.../yolo/<stem>.txt`
- **Sidecar**: `<stem>_sidecar.json` with units, ladder fit, YOLO-normalized boxes, per-object kDa/bp
- **Manifest**: `out/manifest.json` indexing every image (overlay path, report, COCO/YOLO, sidecar, acceptance, fit)

------

## 7) Data schemas at a glance

**COCO (per image)**
 `annotation.attributes` includes:

```
{
  "lane_index": 3,
  "confidence": 0.87,
  "measurement": {"value": 42.1, "units": "kDa"}  // or {"value": 1350, "units": "bp"}
}
```

**Sidecar (per image)**

```
image, image_size{w,h}, modality, units,
ladder{name,a,b,r2,lanes[]},
objects[
  lane, band,
  bbox_px[x0,y0,w,h],
  bbox_yolo[0,cx/W,cy/H,w/W,h/H],
  intensity, confidence,
  measurement{value, units?}
]
```

**Manifest (run-level)**
 Per image: file, overlay, report, coco, yolo, sidecar, accepted, metrics{lanes,markers,empty_frac,lane_width_cv}, fit{status, fit{ladder,a,b,r2,units}}.

------

## 8) Ladder catalogs (preloaded)

- **Protein:** Thermo PageRuler Prestained 10–180 kDa
- **DNA:** NEB 1 kb (N3232)

Use **auto** to let the fitter pick the best match, or force a specific ladder for consistency.

------

## 9) Acceptance & coaching (how to read it)

- **PASS**: lane geometry and band distribution look sane.
- **REVIEW**: triggers include no marker lanes detected, very uneven lane widths, too few bands, or poor calibration.
- The UI sprinkles small coaching hints; Expert mode exposes the knobs to fix the trigger.

------

## 10) Troubleshooting quick hits

- **Too many/few lanes:** tighten **Comb** preset; in Expert, set realistic **min/max lanes**.
- **Missed faint bands:** lower the **confidence** slider; consider raising retries.
- **Calibration fails:** verify ladder lanes; switch ladder type; ensure at least ~5 ladder bands are present.
- **Curved/smiling gels:** current fit assumes straight lanes; results are still useful, but expect higher residuals around edges. Roadmap includes lane-wise warping.

------

## 11) Known limits & roadmap

- No de-smiling / lane warping yet (planned).
- COCO is per-image (a merged run-level COCO is easy to add later).
- UI assumes one ladder profile per run; multi-ladder per gel will need a small extension.

------

## 12) Repro tip (for teams)

- Keep a small `lab_profile.yaml` (comb presets, ladder default, acceptance thresholds). Load it on startup so colleagues get the same defaults every time.