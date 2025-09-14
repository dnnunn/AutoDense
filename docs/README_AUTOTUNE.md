# AutoDense Autotune Scaffold

This adds a safe, explainable **autotune** loop with a **helper (second opinion)** to your repo.

## What you got
- `autodense_autotune/` — core package
  - `schemas.py` — strict data contracts
  - `helper.py` — critic (`helper.review`) + gates
  - `patcher.py` — param/YAML/JSON edits, Python AST literal scaling, regex var for IJM-style macros
  - `runner.py` — observe → propose → patch → measure → gate loop
  - `cli.py` — command-line entry
  - `adapters.py` — stub runner (replace with real pipeline call)
- `challenge_packs/sds_page_v1/spec.yaml` — thresholds, budgets, and param grid examples

## Quick start (stubbed)
```bash
# Run optimization with enhanced persistence
python -m autodense_autotune.cli run \
  --spec challenge_packs/sds_page_v1/spec.yaml \
  --input path/to/your/gel.png \
  --workdir audits/autotune_runs \
  --run-cmd "python -m autodense_autotune.adapters sds_page {input} {outdir}" \
  --print-summary
```

This uses the **stub runner** that just writes a fake `run_report.json`. Replace the `runner.run_cmd` in `spec.yaml` (or the `--run-cmd` flag) with **your pipeline command** that generates `run_report.json` in `{outdir}`.

### Expected `run_report.json`
```json
{
  "task": "sds_page",
  "input_path": "path/to/image.png",
  "input_hash": "abc123...",
  "metrics": {
    "ladder_r2": 0.983,
    "band_stability_jitter": 0.42,
    "lane_count": 8
  },
  "diagnostics_png": "runs/1234/overlay.png",
  "meta": {}
}
```

## Wire to your pipeline
- Modify `runner_cmd` in `spec.yaml` OR pass `--run-cmd` to the CLI.
- Ensure your pipeline writes `run_report.json` into `{outdir}`.
- Start by exposing existing metrics (ladder R², jitter stability, counts).

## Safe patching
- **Params first**: YAML/JSON values via dot-paths (e.g., `detection.min_width`).
- **Python AST (optional)**: scale numeric literals inside *allowed* functions.
- **Regex var (IJM)**: replace `var = NUMBER` only for listed `allowed_vars`.

## Second opinion (helper)
- `helper.review_patch(before, patch, spec)` vetoes unsafe or counter-genre edits.
- `helper.gate_improvement(before, after, spec)` enforces thresholds and improvements.

## Challenge packs (new genres)
Add more packs in `challenge_packs/<task>_vN/` with their own `spec.yaml` and, optionally, `goldens/` and `metrics.py`.

## Prod mode tips
- Run with `--run-cmd` pointing to your real pipeline and set a narrow `param_grids` first.
- Keep `budget.max_attempts` small (3–4).
- Gate via helper before accepting edits; use git branches for commits.
```


---
## Additional challenge packs

### Colony counting (`challenge_packs/colony_count_v1/spec.yaml`)
Expected `metrics` in `run_report.json` (suggested keys):
- `colony_count`: integer
- `touching_fraction`: 0..1 fraction of touching/merged colonies
- `size_cv`: coefficient of variation of colony areas (std/mean)
- `effective_recall` (optional): if you have labels or a heuristic oracle

### EtBr agarose gels (`challenge_packs/etbr_v1/spec.yaml`)
Suggested `metrics`:
- `ladder_linear_r2`: linear fit R² for distance vs log(bp) in a valid size window
- `background_snr`: mean band intensity / background std
- `smearing_index`: 0..1 (higher = worse blur/smear), define via vertical-profile kurtosis or edge spread
- `lane_count`

Update `search.param_grids` to match your actual config files and knobs.


## Sample runners (produce real `run_report.json` from raw images)
Use the provided runners to switch from the stub to real metrics in one step:
```bash
# SDS-PAGE
python -m autodense_autotune.runners sds_page --input path/to/gel.png --outdir runs/demo_sds

# Colony plates
python -m autodense_autotune.runners colony_count --input path/to/plate.jpg --outdir runs/demo_colony

# EtBr agarose gels
python -m autodense_autotune.runners etbr_agarose --input path/to/dna_gel.png --outdir runs/demo_etbr
```
Then point your `spec.yaml` runner command at these, for example:
```yaml
runner:
  run_cmd: "python -m autodense_autotune.runners sds_page --input {input} --outdir {outdir}"
```
These runners are intentionally simple:
- SDS: detect lanes from vertical projection, build lane profiles, compute stability.
- Colony: Otsu threshold + cleanup, compute touching fraction, size CV, count.
- EtBr: white top-hat + Otsu for band mask, simple lane centers, compute SNR and smearing.
Replace any step with your pipeline’s outputs if you have better masks/profiles—just keep the `run_report.json` schema.


## Makefile shortcuts
After installing dependencies, you can run autotune with one-liners:
```bash
make tune-sds INPUT=path/to/gel.png
make tune-colony INPUT=path/to/plate.jpg
make tune-etbr INPUT=path/to/dna_gel.png
# Optional: set where audits go
make tune-sds INPUT=path/to/gel.png WORKDIR=audits/my_runs
```
These call the real runners (`autodense_autotune.runners`) behind the scenes.
You can still override `--run-cmd` on the CLI if you want to invoke your full pipeline.

---

## 🚀 Enhanced Optimization Persistence (August 2025)

AutoDense autotune now includes comprehensive state management for optimization workflows:

### Core Features
- **SQLite Database**: Persistent storage of all optimization sessions and attempts
- **Session Tracking**: Complete history of parameter changes and performance metrics  
- **Analytics Dashboard**: Success rates, common patch types, optimization effectiveness
- **Resumable Workflows**: Learn from previous optimization sessions
- **Comprehensive Reporting**: Detailed analysis of optimization performance

### CLI Commands

#### Run Optimization
```bash
python -m autodense_autotune.cli run \
  --spec challenge_packs/sds_page_v1/spec.yaml \
  --input path/to/gel.png \
  --workdir audits/autotune_runs \
  --run-cmd "python -m autodense_autotune.runners sds_page --input {input} --outdir {outdir}"
```

#### View Optimization History
```bash
# Show all optimization sessions
python -m autodense_autotune.cli history --workdir audits/autotune_runs

# Filter by task type
python -m autodense_autotune.cli history --task sds_page --workdir audits/autotune_runs

# Export as JSON
python -m autodense_autotune.cli history --format json --workdir audits/autotune_runs
```

#### Optimization Analytics
```bash
# Overall analytics
python -m autodense_autotune.cli analytics --workdir audits/autotune_runs

# Task-specific analytics
python -m autodense_autotune.cli analytics --task sds_page --workdir audits/autotune_runs
```

#### Session Details
```bash
# View attempts for a specific session
python -m autodense_autotune.cli attempts --session-id sds_page_1724876543_a1b2c3d4 --workdir audits/autotune_runs

# Export detailed session report
python -m autodense_autotune.cli report --session-id sds_page_1724876543_a1b2c3d4 --output session_report.json --workdir audits/autotune_runs
```

### Persistence Data Structure
```
workdir/
├── optimization/
│   ├── optimization_history.db    # SQLite database
│   ├── session_sds_page_*.json   # Human-readable session files
│   └── report_*.json             # Detailed session reports
└── audit_*/                      # Legacy audit directories (still created)
```

### Key Benefits
- **Learning from History**: Avoid repeating failed parameter combinations
- **Performance Tracking**: Measure optimization effectiveness over time  
- **Debugging Support**: Detailed logs of what worked and what didn't
- **Analytics**: Understand which optimization strategies are most effective
