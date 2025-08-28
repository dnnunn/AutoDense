from autodense_autotune.schemas import RunReport
from autodense_autotune.helper import gate_improvement, load_spec
import json, tempfile, os

SPEC = """    task: sds_page
qc:
  ladder_r2_min: 0.995
acceptance:
  must_improve: ["ladder_r2"]
  must_not_regress: []
budget:
  max_attempts: 2
"""

def test_gate_improvement_pass(tmp_path):
    spec_path = tmp_path/"spec.yaml"
    spec_path.write_text(SPEC, encoding="utf-8")
    spec = load_spec(spec_path.as_posix())
    before = RunReport(task="sds_page", input_path="a.png", input_hash="a", metrics={"ladder_r2":0.990})
    after = RunReport(task="sds_page", input_path="a.png", input_hash="a", metrics={"ladder_r2":0.997})
    ok, deltas = gate_improvement(before, after, spec)
    assert ok
    assert deltas["ladder_r2"] > 0
