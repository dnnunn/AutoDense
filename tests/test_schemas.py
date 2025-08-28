from autodense_autotune.schemas import RunReport, PatchProposal, Critique, Spec

def test_run_report_roundtrip(tmp_path):
    rr = RunReport(task="sds_page", input_path="x.png", input_hash="abc", metrics={"ladder_r2":0.98})
    p = tmp_path/"run_report.json"
    rr.to_json(p.as_posix())
    rr2 = RunReport.from_json(p.as_posix())
    assert rr2.task == "sds_page"
    assert rr2.metrics["ladder_r2"] == 0.98
