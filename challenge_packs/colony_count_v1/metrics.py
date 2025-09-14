"""Colony counting metrics utilities.

Dependencies: numpy, scikit-image (skimage), optionally OpenCV for convenience.
"""
from __future__ import annotations
import numpy as np
from typing import Dict, Optional, Tuple
from skimage.measure import label, regionprops
from skimage.morphology import disk, dilation

def _label_components(binary_mask: np.ndarray) -> Tuple[np.ndarray, int, np.ndarray]:
    lab = label(binary_mask.astype(bool), connectivity=2)
    props = regionprops(lab)
    areas = np.array([p.area for p in props], dtype=float) if props else np.array([], dtype=float)
    return lab, len(props), areas

def colony_metrics(
    binary_mask: np.ndarray,
    neighbor_radius_px: int = 3,
    gt_mask: Optional[np.ndarray] = None
) -> Dict[str, float]:
    """Compute core colony metrics.

    Parameters
    ----------
    binary_mask : np.ndarray
        Boolean/0-1 array where colony pixels are 1/True.
    neighbor_radius_px : int
        Radius (in pixels) to consider colonies as 'touching' neighbors (morph-dilation based).
    gt_mask : Optional[np.ndarray]
        Optional ground-truth mask of colonies for effective recall (same shape). If provided,
        we compute a simple component-wise recall by IoU >= 0.5 matching.

    Returns
    -------
    Dict[str, float] with keys:
        - colony_count
        - size_cv
        - touching_fraction
        - false_positive_rate (if gt provided)
        - effective_recall (if gt provided)
    """
    lab, n, areas = _label_components(binary_mask)
    out = {
        "colony_count": float(n),
        "size_cv": float(np.std(areas)/np.mean(areas)) if n > 1 and np.mean(areas)>0 else 0.0,
    }
    if n == 0:
        out.update({"touching_fraction": 0.0})
        if gt_mask is not None:
            gt_lab, gt_n, _ = _label_components(gt_mask.astype(bool))
            out.update({"false_positive_rate": 0.0, "effective_recall": 0.0, "gt_colony_count": float(gt_n)})
        return out

    # Touching fraction: dilate and see which original components get grouped with others
    dil = dilation(lab > 0, disk(neighbor_radius_px))
    dil_lab = label(dil, connectivity=2)
    # For each dilated component, count how many original labels it contains
    # Mark original colonies that share a dilated component with others
    in_group = np.zeros(n+1, dtype=bool)  # index by original label id
    for d_id in range(1, dil_lab.max()+1):
        mask = dil_lab == d_id
        orig_ids = np.unique(lab[mask])
        orig_ids = orig_ids[orig_ids > 0]
        if orig_ids.size > 1:
            in_group[orig_ids] = True
    touching_fraction = float(in_group.sum() / n)
    out["touching_fraction"] = touching_fraction

    if gt_mask is not None:
        gt_lab, gt_n, _ = _label_components(gt_mask.astype(bool))
        # Compute IoU matrix between predicted and GT components
        iou = _component_iou(lab, gt_lab)
        # Matches by IoU >= 0.5
        matched_gt = int((iou.max(axis=0) >= 0.5).sum()) if iou.size else 0
        matched_pred = int((iou.max(axis=1) >= 0.5).sum()) if iou.size else 0
        effective_recall = matched_gt / gt_n if gt_n > 0 else 0.0
        false_positive_rate = 1.0 - (matched_pred / n) if n > 0 else 0.0
        out.update({
            "false_positive_rate": float(false_positive_rate),
            "effective_recall": float(effective_recall),
            "gt_colony_count": float(gt_n)
        })
    return out

def _component_iou(pred_lab: np.ndarray, gt_lab: np.ndarray) -> np.ndarray:
    pred_ids = np.unique(pred_lab)
    pred_ids = pred_ids[pred_ids>0]
    gt_ids = np.unique(gt_lab)
    gt_ids = gt_ids[gt_ids>0]
    if pred_ids.size == 0 or gt_ids.size == 0:
        return np.zeros((pred_ids.size, gt_ids.size), dtype=float)
    iou = np.zeros((pred_ids.size, gt_ids.size), dtype=float)
    for i, pid in enumerate(pred_ids):
        p_mask = pred_lab == pid
        p_area = p_mask.sum()
        for j, gid in enumerate(gt_ids):
            g_mask = gt_lab == gid
            inter = np.logical_and(p_mask, g_mask).sum()
            union = p_area + g_mask.sum() - inter
            iou[i,j] = inter/union if union>0 else 0.0
    return iou
