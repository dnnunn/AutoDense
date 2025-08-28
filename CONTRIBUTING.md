# Contributing to AutoDense Autotune

Thanks for helping shape a careful, self-tuning densitometry assistant.

## Branching & PRs
- Create feature branches from `main`: `git checkout -b feat/<short-name>`
- Small, atomic PRs with **before/after** metrics for the affected pack.
- Never edit `tests/` in auto-mode—only in reviewed PRs.

## Code Style
- Python 3.10+; run `ruff` + `black` before pushing.
- Keep modules small, pure, and testable. Data in/out must be JSON-safe.

## Tests
- Unit tests: `pytest -q`
- Golden tests: add sample images/masks under `tests/data/` (or link to LFS).
- Autotune loop tests must assert:
  - acceptance gates respected (`helper.gate_improvement`),
  - patches are minimal (one param/file per attempt),
  - reports are written to `outdir/run_report.json`.

## Commits with metrics (recommended)
Include a block in the PR:
```
Pack: sds_page_v1
Input: gels/gel01.png (sha: abc123)
Metrics (before → after):
  ladder_r2: 0.983 → 0.996
  band_stability_jitter: 0.42 → 0.78
Overlay: audits/.../sds_overlay.png
```

## Sensitive Data
Do not commit real patient/PII or proprietary images. Use anonymized or synthetic samples.

## Releasing
- Bump version in your app/package as needed.
- Tag and release after CI passes.
