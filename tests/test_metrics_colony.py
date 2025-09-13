import numpy as np
from challenge_packs.colony_count_v1.metrics import colony_metrics

def test_colony_metrics_basic():
    # Simple synthetic: two separate blobs
    mask = np.zeros((50,50), dtype=bool)
    mask[10:15,10:15] = True
    mask[30:35,30:35] = True
    m = colony_metrics(mask, neighbor_radius_px=2)
    assert m["colony_count"] == 2.0
    assert 0.0 <= m["touching_fraction"] <= 1.0
