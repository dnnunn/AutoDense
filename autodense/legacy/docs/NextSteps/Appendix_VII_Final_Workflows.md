# Appendix VII — Final Lab Workflows & Spec Stubs (with ColonyAssist)

This appendix defines six **ready-to-wire workflows** and provides **spec stubs** you can drop into `challenge_packs/`. They align with the intent/slot orchestration, telemetry, and quality gates we established in prior appendices (truth preserved; AI advises, pipeline measures).

**Workflows covered**
1) Protein Quantification (standard curves, LOQ/LLOQ, %CV) — `protein_quant_v1`
2) LaneComparator with MW binning + Holm–Bonferroni — `lane_compare_v1`
3) Semi-quantitative PCR (ΔΔI normalization) — `semi_qpcr_v1`
4) BandAssist (interactive propagation + confidence) — `band_assist_v1`
5) Time-series Colony Tracking + X-gal blueness — `colony_timeseries_v1`
6) ColonyAssist (interactive classification/QA for colonies) — `colony_assist_v1`

---

## A. How to use these stubs

- Save each `spec.yaml` into `challenge_packs/<pack_name>/spec.yaml`.
- Run via orchestrator, e.g.:
```bash
python -m autodense_autotune.cli run \
  --spec challenge_packs/protein_quant_v1/spec.yaml \
  --input ./gels/runA \
  --workdir ./runs/runA_out
```
- Provide any required `sample_sheet` CSV/JSON referred to by the spec.
- All specs follow the **unified config** (`detect.{lanes,bands}`, `segmentation`, `priors`, `vision`, `allowlist`, `budget`, `outputs`).

---

## B. Telemetry & QC (summary)

Each workflow declares minimal **accept gates**; the orchestrator should accept a patch/run only if these hold:

- **ProteinQuant**: curve R² ≥ 0.98 (linear/log-linear) or 4PL residuals sane; ≥3 valid standards; unknowns within [LLOQ, ULOQ] unless `allow_extrapolation=true`.
- **LaneCompare**: ladder R² ≥ 0.95; per-bin sample count ≥ 2/condition; Holm–Bonferroni adjusted p ≤ α.
- **Semi‑qPCR**: SNR for target & housekeeping ≥ τ; no saturation; replicate ΔΔI CI finite.
- **BandAssist**: per-band confidence ≥ τ for majority; no crossovers; monotonic band order.
- **ColonyTimeSeries**: registration RMS ≤ τ px; ≥80% track continuity; growth fit R² above τ.
- **ColonyAssist**: mask binary contract satisfied; classifier margins reasonable; filter-chain ledger consistent.

---

## C. Natural-language intents (one-liners)

- “Quantify protein with log-linear, report LOQ/LLOQ and %CV.” → `protein_quant_v1`
- “Compare treatment vs control at 5 kDa bins; Holm–Bonferroni 0.05.” → `lane_compare_v1`
- “ΔΔI with WT_1 calibrator; 95% CI.” → `semi_qpcr_v1`
- “Propagate bands from ladder; min confidence 0.8.” → `band_assist_v1`
- “Track colonies across days; ECC align; X-gal classification.” → `colony_timeseries_v1`
- “Open colony assist on this plate; fix mask and reclassify.” → `colony_assist_v1`

---

## D. File layout (suggested)

```
challenge_packs/
  protein_quant_v1/spec.yaml
  lane_compare_v1/spec.yaml
  semi_qpcr_v1/spec.yaml
  band_assist_v1/spec.yaml
  colony_timeseries_v1/spec.yaml
  colony_assist_v1/spec.yaml
runs/
  <pack>_<timestamp>/attempt_001/...
  <pack>_<timestamp>/final_report.json
```

---

## E. Stubs included

This package includes six `spec.yaml` files with sane defaults, allowlist bounds, and QC gates. See each file for comments and tune ranges to your lab.
