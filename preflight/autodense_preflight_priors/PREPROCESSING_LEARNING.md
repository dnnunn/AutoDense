# Preprocessing defaults: learned priors + per-image preflight

This add-on gives you:
- **Offline learned priors** for preprocessing knobs (fit once, refresh occasionally).
- **Online preflight** that picks smart per-image defaults (pixels stay local; LLM still sees only metrics).

## Files
- `autodense_autotune/preflight/` — lightweight per-image defaults (EtBr/SDS/Colony).
- `autodense_autotune/preflight/apply_preflight.py` — CLI to write an adjusted YAML for a given image.
- `meta/fit_priors.py` — random search across your dataset to derive priors.
- `meta/export_priors.py` — merge per-task priors into one JSON.

## Use at runtime (recommended)
1) Compute adjusted config before running your task:
```bash
python -m autodense_autotune.preflight.apply_preflight   --task etbr_agarose   --input gels/dna1.png   --base-config configs/etbr.yaml   --priors-json meta/priors_all.json   --out-config /tmp/etbr_adjusted.yaml
```
2) Point your runner to the **adjusted** YAML (e.g., via java_bridge `--config /tmp/etbr_adjusted.yaml`).

## Fit priors (offline)
```bash
# EtBr example
python meta/fit_priors.py   --task etbr_agarose   --images-dir datasets/etbr/   --spec challenge_packs/etbr_v1/spec.yaml   --base-config configs/etbr.yaml   --workdir audits/fit_priors_runs   --trials-per-image 12

# Merge
python meta/export_priors.py   --etbr meta/priors/etbr_agarose_priors.json   --sds  meta/priors/sds_page_priors.json   --colony meta/priors/colony_count_priors.json   --out meta/priors_all.json
```

## Spec hints
Widen your `search.param_grids` to include preflight knobs:
```yaml
- { file: "configs/etbr.yaml", path: "pre.invert_polarity", values: ["auto","true","false"] }
- { file: "configs/etbr.yaml", path: "pre.clip_percentiles.low",  values: [0.5,1.0,2.0] }
- { file: "configs/etbr.yaml", path: "detect.prominence_frac",     values: [0.04,0.08,0.12] }
- { file: "configs/etbr.yaml", path: "detect.min_peak_distance_px", values: [6,10,14] }
```

## Notes
- The preflight uses only local pixels to set **starting** parameters; the LLM still operates on metrics + spec.
- `fit_priors.py` runs your *existing* runner command (from the pack spec) and reads `run_report.json` to score trials.
- The scoring is simple; substitute your own objective if needed.
