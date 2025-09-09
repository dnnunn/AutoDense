# AutoDense codemod for ImageJ→Python migration

This tool:
- Scans your repo for ImageJ artifacts (.ijm macros, Java plugin modules, Fiji bundles).
- Translates `.ijm` macros to Python functions using `autodense.legacy.mask_ops` (best-effort).
- Moves macros into `autodense/legacy/legacy_macros/` and writes Python under `autodense/legacy/translated/`.
- Produces a `MIGRATION_REPORT.md` and a `DELETION_CANDIDATES.txt` with safe-to-remove files.
- Optionally commits everything on a new git branch.

## Prereqs
- You’ve added `autodense/legacy/mask_ops.py` (see previous patch).
- Python 3.10+

## Usage
```bash
python tools/autodense_codemod.py --repo /path/to/AutoDense --mode report
python tools/autodense_codemod.py --repo /path/to/AutoDense --mode apply --git
```

## Notes
- Macro translation is heuristic; review the generated Python.
- If your macros use unhandled commands, the tool inserts `# TODO` comments with the raw command.
