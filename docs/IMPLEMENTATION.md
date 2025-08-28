# AutoDense Autotune — Implementation Guide

## Goal
Add a safe, measurable **autotune loop** that:
1) observes outputs (metrics + overlays),
2) plans a **minimal** change (prefer params),
3) applies patch in a sandbox branch,
4) re-measures and gates by **pack-specific QC**,
5) accepts only true improvements (with audit trail),
6) supports multiple “genres” (SDS-PAGE, colonies, EtBr) via **challenge packs**.

## Package Inventory
- `autodense_autotune/`
  - `schemas.py` — data contracts: `RunReport`, `PatchProposal`, `Critique`, `Spec`
  - `helper.py` — second-opinion + acceptance gates
  - `patcher.py` — safe patch application (YAML/JSON params, AST literal tweaks, IJM-style regex var)
  - `runner.py` — observe → propose → patch → measure → gate
  - `cli.py` — command-line entry
  - `adapters.py` — stub emitter (for quick smoke tests)
  - `runners.py` — **real** sample runners for SDS/colony/EtBr
- `challenge_packs/`
  - `sds_page_v1/` — `spec.yaml`, `metrics.py`
  - `colony_count_v1/` — `spec.yaml`, `metrics.py`
  - `etbr_v1/` — `spec.yaml`, `metrics.py`
- `scripts/` — `run_autotune_example.sh`
- `Makefile` — `tune-sds`, `tune-colony`, `tune-etbr`
- `README_AUTOTUNE.md` — usage & wiring
- `CONTRIBUTING.md` — dev workflow
- `IMPLEMENTATION.md` — this doc
- `tests/` — pytest stubs

## Minimal Setup
```bash
python -m venv .venv && source .venv/bin/activate
pip install -U pip
pip install numpy scipy scikit-image matplotlib pytest black ruff pyyaml
```

## Quick Run (uses sample runners)
```bash
make tune-sds INPUT=path/to/gel.png
make tune-colony INPUT=path/to/plate.jpg
make tune-etbr INPUT=path/to/dna_gel.png
```

## Wire to Your Pipeline
- Each pack’s `spec.yaml > runner.run_cmd` points to `autodense_autotune.runners` by default.
- Replace with your pipeline command that writes `{outdir}/run_report.json`:
  ```json
  {
    "task": "sds_page",
    "input_path": "path/to/image",
    "input_hash": "<sha>",
    "metrics": { "...": 0.0 },
    "diagnostics_png": "path/to/overlay.png",
    "meta": {}
  }
  ```

## ClaudeCode / Codex Instructions (concise)
- **Task:** “Given this repo, run the autotune loop for each challenge pack and ensure acceptance gates work.”
- **Steps:**
  1. Create venv and install deps listed above.
  2. For each pack, run the Makefile target with a sample image.
  3. Confirm `audits/.../summary.json` contains at least one attempt, and the best result respects thresholds in `spec.yaml`.
  4. Add a small param grid edit in `spec.yaml` (e.g., tweak `prominence` values) and re-run.
  5. Open a PR with before/after metrics table (see CONTRIBUTING).
- **Do not:** write to images directly; only through runners. Do not modify tests during autotune.

## Adding a New Genre
1. Create `challenge_packs/<task>_vN/` with `spec.yaml` + `metrics.py`.
2. Define QC and acceptance keys in `spec.yaml`.
3. Expose tunable knobs under `search.param_grids`.
4. Ensure your runner writes the required metrics. Reuse `runners.py` as a template.

## Safety Rules
- Param-first edits; one file per attempt.
- AST literal tweaks only in allowed hotspots (extend guards as needed).
- Gates must show improvement **and** meet QC minimums before accepting.
- Never auto-edit tests.
