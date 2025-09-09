# tests/test_smoke.py
from pathlib import Path
from autodense.orchestrator.pipeline import Params, run
from PIL import Image

def test_smoke(tmp_path: Path):
    p = tmp_path/"g.png"
    Image.new("L",(128,256),200).save(p)
    res, pf, obs = run(p, Params(modality="sds", min_lanes=2, max_lanes=2), retries=0)
    assert res is not None
    assert "lanes" in obs
