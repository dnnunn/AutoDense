# Pre-commit policy: block ImageJ/Fiji/Java regressions

This adds a lightweight policy that fails commits/PRs if the repo reintroduces
ImageJ/Fiji/Scijava-era terms.

## Install
```bash
pip install pre-commit
pre-commit install
# optional: also run on push
pre-commit install -t pre-push
```

## Run manually
```bash
pre-commit run --all-files
```

## CI
This patch includes `.github/workflows/policy.yml` to run the same check in GitHub Actions.

## Customize
- **Allowed files:** edit `ALLOW_FILES` in `scripts/check_no_imagej_terms.py` to permit mentions in specific docs.
- **Ignore dirs:** `IGNORE_DIRS` already excludes `autodense/legacy/legacy_macros` and `autodense/legacy/translated`.
- **Patterns:** tweak `PATTERNS` for stricter or looser matches.
