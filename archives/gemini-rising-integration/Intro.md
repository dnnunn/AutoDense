I pulled your refactored codebase, merged in the new Gemini-enabled autotune stack, aligned the specs to your current config layout, and re-packed everything so you can `git add` and go.

# Download the merged, up-to-date codebase

AutoDense_consolidated_with_refactor_2025-08-28.tar.gz

Also grabbed a full file inventory so your AI partner can sanity-check contents without unzipping:
 consolidated_inventory.json

# What I actually changed (safely and concretely)

- **Injected the full autotune scaffold** into your refactored tree:
  - `autodense_autotune/` (schemas, helper, patcher, runner, cli, runners, adapters)
  - `challenge_packs/` (SDS-PAGE, Colony, EtBr with metrics + specs)
  - `prompts/` (orchestrator + helper system prompts)
  - `tests/`, `pytest.ini`, `README_AUTOTUNE.md`, `IMPLEMENTATION.md`, `CONTRIBUTING.md`
  - `Makefile` targets: `tune-sds`, `tune-colony`, `tune-etbr`
- **Updated pack runners by default** to call the real sample runners:
  - `runner.run_cmd`: `python -m autodense_autotune.runners <task> --input {input} --outdir {outdir}`
- **Aligned param grids** in each `spec.yaml` to your repository’s structure:
  - Auto-detected likely config files in your tree and set `search.param_grids[*].file` accordingly (kept the same key paths unless your repo uses different names).
  - If you have more specific config names (e.g., `configs/analysis_sds.yaml` vs `configs/sds.yaml`), the specs now point to the discovered file. You can tweak the dot-paths easily.
- **Kept your refactor intact**:
  - I didn’t move or modify your business logic; the new parts live alongside it and can be enabled/disabled per pack.
  - I skipped hidden dot-directories (e.g., IDE files like `.claude/`) when copying to avoid weird file errors.

# How to run after pulling this bundle

```
python -m venv .venv && source .venv/bin/activate
pip install -U pip
pip install numpy scipy scikit-image matplotlib pytest black ruff pyyaml

# Smoke-test the loop on your images (uses the sample runners)
make tune-sds    INPUT=path/to/gel.png
make tune-colony INPUT=path/to/plate.jpg
make tune-etbr   INPUT=path/to/dna_gel.png

# See audits and before/after in audits/autotune_runs/ (plus overlays)
```

# Where you might want to tweak (quick wins)

- **`challenge_packs/\*/spec.yaml` → `search.param_grids`**
   If your refactor renamed keys (e.g., `detection.prominence` → `bands.prominence`), update the dot-paths there; the loop will use your knobs immediately.
- **`runner.run_cmd`**
   Point it to your full pipeline command when ready; just keep writing `{outdir}/run_report.json` with the same schema and the helper/gates will do the rest.
- **Prompts**
   If you’re enabling the Gemini planner/critic, your API keys + model names go via env vars. The prompts are in `prompts/`, and adapters are in `autodense_autotune/llm_adapters.py`.

# What I detected automatically (so you don’t have to)

- Scanned your repo for likely config files (`*.yaml`, `*.yml`, `*.json`), biased toward `configs/` paths, then wired `spec.yaml` grids to those files.
- Left dot-paths for params as-is (e.g., `detection.prominence`, `segmentation.min_area`). If your refactor changed these names, just adjust the path strings—no code changes needed.

If you want, drop me a couple of your new config filenames and any renamed keys (e.g., `analysis.yml` with `bands.min_prom`), and I’ll pin the param grids precisely so the very first autotune run is already on the money. 