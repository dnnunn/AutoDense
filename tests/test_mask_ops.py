# tests/test_mask_ops.py
import numpy as np
import pytest

from autodense.legacy.mask_ops import (
    to_8bit, otsu_mask, fill_holes, remove_small, keep_largest,
    erode_mask, dilate_mask, open_mask, close_mask, label_mask, regionprops_df
)

def test_to_8bit_scales_ramp():
    img = np.linspace(0, 1000, 1000, dtype=float).reshape(20,50)
    u8 = to_8bit(img, clip_percentile=(0.0, 100.0))
    assert u8.dtype == np.uint8
    assert u8.min() == 0
    assert u8.max() == 255

def test_otsu_separates_bimodal():
    rng = np.random.default_rng(0)
    a = rng.normal(loc=0.2, scale=0.02, size=(100,100))
    b = rng.normal(loc=0.8, scale=0.02, size=(100,100))
    img = np.block([[a,b],[a,b]])
    mask, thr = otsu_mask(img)
    # majority of high region should be True
    high_true = mask[0:100,100:200].mean()
    low_true = mask[0:100,0:100].mean()
    assert high_true > 0.95 and low_true < 0.05

def test_fill_holes_on_donut():
    y,x = np.ogrid[:100,:100]
    r = np.sqrt((x-50)**2 + (y-50)**2)
    mask = (r < 30) & (r > 20)  # ring with a hole
    filled = fill_holes(mask)
    assert filled.sum() > mask.sum()
    assert filled[50,50] == True  # center hole filled

def test_morph_open_close_noise():
    rng = np.random.default_rng(1)
    mask = (rng.random((100,100)) > 0.98)  # sparse salt noise
    opened = open_mask(mask, radius=1)
    assert opened.sum() <= mask.sum()
    closed = close_mask(opened, radius=2)
    assert closed.sum() >= opened.sum()

def test_remove_small_and_keep_largest():
    mask = np.zeros((50,50), bool)
    mask[5:10,5:10] = True  # 25 px
    mask[30:48, 30:48] = True  # 324 px
    cleaned = remove_small(mask, min_size=50)
    assert cleaned.sum() == 324
    largest = keep_largest(mask, n=1)
    assert largest.sum() == 324

def test_regionprops_df_basic():
    mask = np.zeros((20,20), bool)
    mask[2:5, 2:5] = True
    mask[10:15, 10:18] = True
    lab = label_mask(mask)
    df = regionprops_df(lab, None, props=("label","area","bbox","centroid"))
    assert set(df["label"]) == {1,2}
    areas = dict(zip(df["label"], df["area"]))
    assert areas[1] == 9 and areas[2] == 8*5
