from __future__ import annotations
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pathlib import Path
import json
from typing import Any, Dict, List

from autodense.coach_rules import evaluate_hints

app = FastAPI(title="AutoDense Coach API", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

DATA_DIR = Path("runs")  # expects metrics under runs/<run_id>/metrics.json

class RerunRequest(BaseModel):
    run_id: str
    step_id: str
    params_delta: Dict[str, Any] = {}

@app.get("/api/coach/hints")
def get_hints(run_id: str):
    mpath = DATA_DIR / run_id / "metrics.json"
    if not mpath.exists():
        raise HTTPException(404, f"metrics not found for run {run_id}")
    metrics = json.loads(mpath.read_text())
    # Pull current params if you persist them; else empty dict
    ppath = DATA_DIR / run_id / "params.json"
    params = json.loads(ppath.read_text()) if ppath.exists() else {}
    hints = evaluate_hints(metrics, params)
    return {"run_id": run_id, "hints": hints}

@app.post("/api/coach/rerun")
def rerun(req: RerunRequest):
    # Clone run directory to avoid stomping user state
    src = DATA_DIR / req.run_id
    if not src.exists():
        raise HTTPException(404, f"run not found: {req.run_id}")
    # Example clone naming; in production, use UUID or increment
    clone_id = f"{req.run_id}_coach"
    dst = DATA_DIR / clone_id
    dst.mkdir(parents=True, exist_ok=True)

    # Merge params and persist
    base_params_path = src / "params.json"
    base_params = json.loads(base_params_path.read_text()) if base_params_path.exists() else {}
    merged = {**base_params, **req.params_delta}
    (dst / "params.json").write_text(json.dumps(merged, indent=2))

    # ---- Call into your pipeline here ----
    # For example: pipeline.rerun_step(step_id=req.step_id, run_dir=dst, params=merged)
    # This stub just copies metrics over unchanged.
    (dst / "metrics.json").write_text((src / "metrics.json").read_text())

    # Compute hints for the cloned run
    metrics = json.loads((dst / "metrics.json").read_text())
    hints = evaluate_hints(metrics, merged)
    return {"run_id": clone_id, "applied": req.params_delta, "hints": hints}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)