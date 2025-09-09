Yes—use the data. Do it in two layers so you get both good defaults and per-image adaptability:

# 1) Offline “learned defaults” (fit once, update occasionally)

Goal: learn **priors** for preprocessing knobs from a representative corpus (EtBr, SDS-PAGE, colonies). No labels needed—optimize the same QC metrics you already use.

How:

- Assemble folders per task (100–500 images each is plenty).
- Search the preprocessing space (e.g., `invert_polarity`, percentile clip, CLAHE on/off, Gaussian σ, rolling-ball/top-hat radius, `prominence_frac`, `min_peak_distance_px`).
- For each image, **run the real pipeline**, score with your pack metrics (e.g., `ladder_linear_r2`, `background_snr`, `smearing_index`, `touching_fraction`).
- Aggregate the best settings across images → fit **priors** (discrete histograms for categorical knobs; beta/log-normal for continuous).
- Write priors to disk and **shrink** your pack grids to the high-mass regions (fewer dead trials, faster convergence).

Result:

- `challenge_packs/*/spec.yaml` grids become data-driven (good defaults).
- Orchestrator/Helper stay as-is—now they start near the win.

# 2) Online “preflight + fine-tune” (per image, fast and local)

Goal: pick smart starting values from the image itself, then let autotune make tiny corrections.

Preflight (pixel-based, light-weight, **not** the LLM):

- Compute quick features: p1/p99, mean/std, skewness, Otsu threshold, vertical vs horizontal gradient energy, coarse Hough angle, color/luminance balance.
- From these, pick:
  - polarity (`auto/true/false`)
  - clip percentiles (e.g., [0.5–2.0, 98–99.5])
  - CLAHE on/off (+ clip limit, tile size)
  - Gaussian σ and rolling-ball/top-hat radius (scale with image size and detected lane width)
  - initial `prominence_frac` and `min_peak_distance_px` (from 1-D profile stats)

Then run once; **if QC minima not met**, let your existing loop try 2–3 small steps around those starts.

------

## Where this plugs into your current setup

- Keep the “LLM never sees pixels” rule. The **preflight** is local code in the runner; the **LLM** still only sees metrics + specs.
- Add a tiny module (per pack) that computes features → starting params.
- Add a `priors.json` per pack (learned offline) and bias preflight choices toward those priors.
- Widen your grids a bit to include the preflight knobs (examples below).

### Spec tweaks (example)

```
# challenge_packs/etbr_v1/spec.yaml
search:
  param_grids:
    - { file: "configs/etbr.yaml", path: "pre.invert_polarity", values: ["auto","true","false"] }
    - { file: "configs/etbr.yaml", path: "pre.clip_percentiles.low",  values: [0.5, 1.0, 2.0] }
    - { file: "configs/etbr.yaml", path: "detect.prominence_frac",     values: [0.04, 0.08, 0.12] }
    - { file: "configs/etbr.yaml", path: "detect.min_peak_distance_px", values: [6, 10, 14] }
```

### Preflight sketch (put in your runner; 20 lines)

```
def preflight_defaults(img):
    p1, p99 = np.percentile(img, [1, 99])
    mean = img.mean()
    invert = "auto"
    if mean < 0.45 and p99 - p1 > 0.05: invert = "true"
    # scale-aware smoothing and distances
    H, W = img.shape[:2]
    sigma = max(1.0, W / 300.0)          # lanes: wider image → slightly larger σ
    min_d = max(6, int(W / 110.0))       # min peak spacing
    prom  = 0.08 if (p99 - p1) > 0.2 else 0.04
    return {
        "pre": {"invert_polarity": invert, "clip_percentiles": {"low": 1.0, "high": 99.0}},
        "detect": {"gaussian_sigma": sigma, "min_peak_distance_px": min_d, "prominence_frac": prom}
    }
```

------

## Training the priors (one evening, repeat quarterly)

- Run a simple optimizer (random search or Optuna) over the preprocessing knobs; objective = your pack’s `must_improve` metrics with QC minima as constraints.
- Save `priors.json` like:

```
{"detect.prominence_frac":{"type":"beta","alpha":3.2,"beta":12.1},
 "pre.invert_polarity":{"type":"categorical","probs":{"auto":0.6,"true":0.3,"false":0.1}},
 "...": "..."}
```

- At runtime, sample around the prior mean for a start; if preflight features contradict the prior, weight preflight more.

------

## Why this fits your architecture

- Pixels are used **only** by preflight (local), not by the LLM.
- The orchestrator/helper remain unchanged; they simply get **better starting grids** and **smaller neighborhoods** to search.
- You gain continual learning: every accepted run can update the priors (EMA or periodic refit).

If you want, I can drop in a tiny `meta/` folder with:

- `fit_priors.py` (batch optimizer over your dataset),
- `export_priors.py` (writes `priors.json`),
- and a `preflight.py` per pack that your runners call before the first pass.

##### You said:

that would be fantastic

###### ChatGPT said:

Thought for 27s

Absolutely. I packaged a drop-in **preflight + learned priors** add-on you can layer onto your current repo without touching your Java bridge or CLI.

# Download

- Add-on bundle:
   autodense_preflight_priors_2025-08-28.tar.gz
- Quick doc: included as `PREPROCESSING_LEARNING.md`

# What you get

- `autodense_autotune/preflight/` — tiny Python module that:
  - reads the **image pixels locally**,
  - computes quick stats (p1/p99, mean, gradients),
  - picks sensible starting knobs (polarity, clip, CLAHE, σ, min peak distance, prominence),
  - merges with optional **task priors**.
- `autodense_autotune/preflight/apply_preflight.py` — CLI to write an **adjusted YAML** before running the pipeline.
- `meta/fit_priors.py` — offline batch script to learn **priors** from a dataset using your real runner.
- `meta/export_priors.py` — merges task priors into a single `priors_all.json`.

# How to use it (two ways)

### A) Per-image preflight at runtime (recommended)

```
# 1) Generate adjusted config based on the image + priors
python -m autodense_autotune.preflight.apply_preflight \
  --task etbr_agarose \
  --input path/to/gel.png \
  --base-config configs/etbr.yaml \
  --priors-json meta/priors_all.json \
  --out-config /tmp/etbr_adjusted.yaml

# 2) Run your normal pipeline with the adjusted config
python -m autodense_autotune.java_bridge \
  --task etbr_agarose \
  --input path/to/gel.png \
  --outdir runs/etbr_gel1 \
  --config /tmp/etbr_adjusted.yaml \
  --java-main com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
  --pom-dir autodense/plugin
```

You can do the same for `sds_page` and `colony_count`.

### B) Learn better defaults (offline)

```
# Fit priors from your representative images
python meta/fit_priors.py \
  --task etbr_agarose \
  --images-dir datasets/etbr/ \
  --spec challenge_packs/etbr_v1/spec.yaml \
  --base-config configs/etbr.yaml \
  --workdir audits/fit_priors_runs \
  --trials-per-image 12

# Merge into one file for runtime use
python meta/export_priors.py \
  --etbr meta/priors/etbr_agarose_priors.json \
  --sds  meta/priors/sds_page_priors.json \
  --colony meta/priors/colony_count_priors.json \
  --out  meta/priors_all.json
```

# Wiring tips

- Expand each pack’s `search.param_grids` to include the preflight knobs (`pre.invert_polarity`, `pre.clip_percentiles.low`, `detect.prominence_frac`, etc.) so the autotune loop can make small adjustments after the preflight guess.
- The LLM still sees **only metrics and specs**; pixels are used **locally** in preflight, preserving your safety model.

If you want, I can also add Makefile helpers like `preflight-etbr` and patch one pack spec as an example so it automatically runs preflight → bridge in one target. 

##### You said:

anything you think I might need to get this done is appreciated

###### ChatGPT said:

Thought for 35s

You’ve got the right instincts. To help you actually land this plane, I put together a **Smart Preflight + Rescue + Debug** toolkit you can drop straight into your repo. It keeps your current flow intact but adds the bits that make stubborn images behave.

# Grab the toolkit

- autodense_debug_rescue_toolkit_2025-08-28.tar.gz

# What’s inside (and why it helps)

- `scripts/with_preflight_and_rescue.py` — one command that:
  1. computes per-image defaults (preflight),
  2. runs your Java pipeline via the bridge,
  3. if lanes/bands/colonies are **zero**, auto-retries with a **rescue** config (invert toggle, lower prominence ×0.33, bigger σ, colony post tweaks),
  4. picks the better result and writes a final `run_report.json` + overlay at the top of `--outdir`.
- `scripts/plot_profiles.py` — makes a horizontal projection plot for fast lane-detection triage.
- `scripts/verify_report.py` — checks schema/keys of `run_report.json`.
- `Makefile.additions` — additive targets you can paste into your Makefile:
  - `smart-etbr`, `smart-sds`, `smart-colony` (preflight→bridge→rescue)
  - `profile-plot` and `verify-report` helpers
- `docs/DEBUGGING_PLAYBOOK.md` — step-by-step checklist for gels and colonies.

# Quick wire-up (non-destructive)

1. Unpack at your repo root (it places files under `scripts/` and `docs/`).

2. Append `Makefile.additions` to your existing `Makefile`.

3. Ensure `.env` has your Java bits:

   ```
   AUTODENSE_MAIN_CLASS=com.betterdairy.autodense.cli.AutotuneAnalysisCLI
   POM_DIR=autodense/plugin
   FIJI_DIR=/path/to/Fiji.app
   ```

4. Run a smart pass:

   ```
   make smart-etbr    INPUT=path/to/gel.png
   make smart-sds     INPUT=path/to/gel.png
   make smart-colony  INPUT=path/to/plate.jpg
   ```

   Artifacts land in `audits/autotune_runs/<task>/<image_basename>/smart/`:

   - `preflight.yaml`, `run_report.json`, overlay PNG
   - optional `rescue/` subfolder if a retry was needed
   - `meta.rescue_used` flag in the final JSON

# When things look weird

- Plot the projection to see what the detector “sees”:

  ```
  make profile-plot INPUT=path/to/gel.png OUT=projection.png
  ```

- Validate outputs quickly:

  ```
  make verify-report FILE=.../run_report.json
  ```

# How this meshes with your autotune loop

- Keep your current `runner.run_cmd` as-is for now.
- Once you like the behavior, point a pack’s `runner.run_cmd` to `scripts/with_preflight_and_rescue.py` instead of the raw `java_bridge` to make **preflight+rescue** the default first attempt. The autotune/LLM then fine-tunes around a good starting point instead of flailing at zero features.

# Optional next steps I can ship

- Add unit tests for the rescue policy (mocked reports).
- Bake priors into `meta/priors_all.json` based on your canary set.
- Auto-save stage images (`stage0_input`, `stage1_norm`) from the Java side so we can correlate failures with preprocessing.

If you want me to wire one pack to use the smart wrapper by default, tell me which (EtBr/SDS/Colony) and I’ll hand you the exact `spec.yaml` change. 

##### You said:

I could use all three

###### ChatGPT said:

Thought for 23s

Heck yes — let’s wire **all three packs** to the smart wrapper so every run gets: preflight → real Java/ImageJ → auto-rescue (if zero features) → clean outputs.

# One-step helper script

Download this tiny script and drop it at the root of your repo:

- wire_smart_wrapper.py

Run it from your repo root:

```
python wire_smart_wrapper.py
```

What it does:

- Updates `runner.run_cmd` in all three specs (creates `.bak` backups):
  - `challenge_packs/sds_page_v1/spec.yaml`
  - `challenge_packs/etbr_v1/spec.yaml`
  - `challenge_packs/colony_count_v1/spec.yaml`

They’ll now point to the smart wrapper that uses your Java main + Fiji via the bridge.

# Exact runner commands it writes

- **SDS-PAGE**

```
runner:
  run_cmd: "python scripts/with_preflight_and_rescue.py --task sds_page --input {input} --outdir {outdir} --base-config configs/sds.yaml --priors-json meta/priors_all.json"
```

- **EtBr agarose**

```
runner:
  run_cmd: "python scripts/with_preflight_and_rescue.py --task etbr_agarose --input {input} --outdir {outdir} --base-config configs/etbr.yaml --priors-json meta/priors_all.json"
```

- **Colony counting**

```
runner:
  run_cmd: "python scripts/with_preflight_and_rescue.py --task colony_count --input {input} --outdir {outdir} --base-config configs/colony.yaml --priors-json meta/priors_all.json"
```

Notes:

- `--priors-json` is optional; if `meta/priors_all.json` doesn’t exist yet, the tool harmlessly ignores it.
- The wrapper reads your Java settings from env:
  - `AUTODENSE_MAIN_CLASS=com.betterdairy.autodense.cli.AutotuneAnalysisCLI`
  - `POM_DIR=autodense/plugin`
  - `FIJI_DIR=/path/to/Fiji.app`

# Quick test

```
# EtBr
make tune-etbr   INPUT=path/to/dna_gel.png

# SDS-PAGE
make tune-sds    INPUT=path/to/protein_gel.png

# Colonies
make tune-colony INPUT=path/to/plate.jpg
```

Artifacts (preflight.yaml, final run_report.json, overlay) will land in the pack’s workdir for that image. If the first pass found nothing, the rescue pass runs automatically and the better one is kept.

If you’d rather not run the script, you can paste the YAML `runner:` snippets directly into your three `spec.yaml`s. Either way, you now have **preflight + rescue** by default across EtBr, SDS-PAGE, and colonies. 