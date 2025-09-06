# AutoDense: Debugging Playbook (EtBr, SDS-PAGE, Colonies)

## Golden order of operations
1. **Profile the image**: run `make profile-plot INPUT=img.png OUT=plot.png`.
2. **Preflight + smart run** (no LLM needed): `make smart-etbr INPUT=img.png` (or `smart-sds`, `smart-colony`).
3. **Open artifacts** in the created `smart/` folder:
   - `preflight.yaml` (auto-chosen knobs)
   - `run_report.json` (final metrics; includes `"meta":{"rescue_used":...}`)
   - overlay PNG
   - optional `rescue/` subfolder if a retry ran
4. If lanes/bands/colonies are **0**:
   - Inspect `preflight.yaml`. Manually try: `invert_polarity`, lower `prominence_frac`, raise `gaussian_sigma`.
   - Re-run `smart-*` target; it will retry and pick the better result automatically.

## Common causes & one-liners
- **Wrong polarity** → Set `pre.invert_polarity: true` (gels often bright-on-dark).
- **Flat dynamic range** → widen `pre.clip_percentiles.low` to `0.5` and `high` to `99.5`, or enable CLAHE.
- **Over-smoothing** → lower `detect.gaussian_sigma` if lanes look washed out.
- **Too strict peaks** → lower `detect.prominence_frac` (0.04–0.08 are friendly starts).
- **Colonies merged** → increase `post.merge_distance_px`, increase watershed aggressiveness.
- **Colonies fragmented** → increase `segmentation.min_area`.

## Validate outputs
- `make verify-report FILE=.../run_report.json` → schema sanity.
- Ensure `diagnostics_png` exists and is readable from the JSON path.

## When to involve autotune/LLM
- Once `smart-*` finds **any** features, the grid/LLM loop will refine parameters quickly.
- Keep `budget.max_attempts` small (2–4) while iterating detectors.

## CI ideas
- Add a handful of canary images per task. Assert lane_count/band_count/colony_count are non-zero and within a range.
- Fail the build if `smart-*` returns zero features on a canary.
