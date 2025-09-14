# Migration Notes — AutoDense Autotune + Gemini Integration

**Audience:** Engineers and AI code partners updating an existing AutoDense/ImageJ workflow to the new metrics‑driven, LLM‑assisted architecture.  
**Scope:** What changed, how to migrate safely, and how to enable (or skip) the LLM planner/reviewer.

---
## Executive summary
We added a constrained **autotune loop** on top of your existing analysis. The LLM (Gemini/OpenAI/Grok) never sees pixels or edits images; it reads **metrics JSON** and proposes strictly bounded **one‑knob** changes (prefer params; tiny AST literal tweaks only if allowed). The pipeline re‑runs, measures again, and a gate accepts only genuine improvements.

**Key contracts**
- **RunReport** (what your runner writes): metrics + overlay path
- **Spec** (per “challenge pack”): QC minima, acceptance rules, and tunable knobs
- **PatchProposal** (what the Orchestrator suggests): one param/code change
- **Critique** (what the Helper returns): accept / revise / reject + reasons

---
## What changed (at a glance)
- Added `autodense_autotune/` (schemas, helper, patcher, runner, cli, runners, llm_adapters)
- Added challenge packs: `challenge_packs/{sds_page_v1,colony_count_v1,etbr_v1}` with `spec.yaml` and `metrics.py`
- Added prompts for LLM roles: `prompts/orchestrator.system.md`, `prompts/helper.system.md`
- Added Makefile targets (`tune-sds`, `tune-colony`, `tune-etbr`) and tests (`pytest` stubs)

---
## Migration checklist (10 minutes)
1. **Create venv and install deps**
   ```bash
   python -m venv .venv && source .venv/bin/activate
   pip install -U pip
   pip install numpy scipy scikit-image matplotlib pyyaml pytest black ruff
   ```
2. **Ensure each workflow writes `run_report.json`**
   Your runner must write to `{outdir}/run_report.json`:
   ```json
   {
     "task": "sds_page|colony_count|etbr_agarose",
     "input_path": "path/to/image",
     "input_hash": "<sha12>",
     "metrics": { "metric_name": 0.0 },
     "diagnostics_png": "runs/.../overlay.png",
     "meta": {}
   }
   ```
3. **Confirm challenge pack specs point to a real config**
   Edit `challenge_packs/*/spec.yaml > search.param_grids[*].file` to match your repo, e.g. `configs/colony.yaml`.
   - If you don’t have configs yet, use the provided starter `configs/*.yaml` or adjust the paths.
4. **Smoke‑test with the sample runners (no LLM required)**
   ```bash
   make tune-sds    INPUT=path/to/gel.png
   make tune-colony INPUT=path/to/plate.jpg
   make tune-etbr   INPUT=path/to/dna_gel.png
   ```
   These call `autodense_autotune.runners` which compute basic metrics and overlays.
5. **(Optional) Enable Gemini/OpenAI/Grok planning & review**
   - Put API keys in env: `GEMINI_API_KEY` / `OPENAI_API_KEY` / `XAI_API_KEY`
   - Use `autodense_autotune/llm_adapters.py` and prompts in `prompts/`
   - Keep `temperature<=0.2` and validate strict JSON output before applying
6. **Run tests**
   ```bash
   pytest -q
   ```

---
## Enabling the LLM roles (optional but powerful)
- **Orchestrator** proposes one `PatchProposal` (param‑first; or tiny AST literal/regex var if allowed by spec).
- **Helper** judges the proposal; can accept, suggest a safer param, or reject.
- Both roles operate on JSON only; they never inspect pixels.

**Quick usage (pseudo‑code)**
```python
from autodense_autotune.llm_adapters import propose_patch_orchestrator, review_patch_helper
patch = propose_patch_orchestrator("gemini", run_report=rr, spec=spec, attempts=attempts)
critique = review_patch_helper("openai", run_report=rr, spec=spec, proposal=patch)
# Apply only if critique.verdict == "accept" and gates pass
```

---
## Safety rails (non‑negotiable)
- **No pixels to LLMs.** Only metrics JSON + file handles.
- **One change per attempt.** Small blast radius; easy rollbacks.
- **Param‑first policy.** Prefer editing YAML/JSON params declared in `spec.search.param_grids`.
- **QC gates.** Accept only if `must_improve` increases and `qc` minima are met; must_not_regress must hold.
- **Never auto‑edit tests/specs.** Human review required.

---
## Challenge packs (examples)
- **SDS‑PAGE**: metrics – `ladder_r2`, `band_stability_jitter`, `lane_count`; knobs – `detection.*`
- **Colony**: metrics – `colony_count`, `touching_fraction`, `size_cv`, optional `effective_recall`; knobs – `segmentation.*`, `post.*`
- **EtBr**: metrics – `ladder_linear_r2`, `background_snr`, `smearing_index`, `lane_count`; knobs – `pre.*`, `lane.*`, `band.*`

---
## CI & tests
- Run `pytest`. Included stubs cover schema roundtrip, acceptance gates, and colony metrics sanity.
- Add canary images per pack and assert metric tolerances to prevent regressions.

---
## Troubleshooting
- **Spec points to missing config** → create `configs/<task>.yaml` or edit `file:` paths in the pack spec.
- **Model returns prose/invalid JSON** → treat as REJECT; keep best run; log and continue.
- **“Missing metrics”** → ensure your runner writes all metrics referenced by `qc` and `acceptance` in the pack’s spec.
- **No improvement after several attempts** → narrow the grid; check that the chosen knobs actually influence the target metric.

---
## Appendix A — Example `spec.yaml` (colony)
```yaml
task: colony_count
qc:
  touching_fraction_max: 0.20
  size_cv_max: 0.30
acceptance:
  must_improve: ["effective_recall"]
  must_not_regress: ["touching_fraction","size_cv","false_positive_rate"]
budget:
  max_attempts: 4
runner:
  run_cmd: "python -m autodense_autotune.runners colony_count --input {input} --outdir {outdir}"
search:
  param_grids:
    - { file: "configs/colony.yaml", path: "segmentation.threshold",   values: [0.25,0.30,0.35] }
    - { file: "configs/colony.yaml", path: "segmentation.min_area",    values: [20,30,40] }
    - { file: "configs/colony.yaml", path: "post.merge_distance_px",   values: [3,5,7] }
```

## Appendix B — `run_report.json` schema (minimal)
```json
{
  "task": "etbr_agarose",
  "input_path": "gels/dna_gel.png",
  "input_hash": "abc123456789",
  "metrics": {
    "ladder_linear_r2": 0.991,
    "background_snr": 4.8,
    "smearing_index": 0.41,
    "lane_count": 6
  },
  "diagnostics_png": "audits/run_123/etbr_overlay.png",
  "meta": {}
}
```
