# AutoDense legacy mask ops — Python replacements

Drop-in module and tests to replace ImageJ macro-era steps with pure Python.

## Install test deps

```bash
pip install -U pytest scikit-image scipy numpy
```

## Run tests

```bash
pytest -q
```

## Functions

- `to_8bit(img, clip_percentile=(0.5,99.5))`
- `otsu_mask(img, blur_sigma=0.0, min_size=0, fill=True)` → `(mask, threshold)`
- `fill_holes(mask, area_threshold=None)`
- `erode_mask/dilate_mask/open_mask/close_mask(mask, radius=1)`
- `remove_small(mask, min_size)`
- `keep_largest(mask, n=1)`
- `label_mask(mask, connectivity=2)`
- `regionprops_df(mask, intensity=None, props=(...))`

Map from ImageJ:
- `run("8-bit")` → `to_8bit`
- `run("Convert to Mask")` → `otsu_mask` or `threshold_mask`
- `run("Fill Holes")` → `fill_holes`
- Erode/Dilate/Open/Close → corresponding `_mask` functions
- Analyze Particles → `label_mask` + `regionprops_df`
