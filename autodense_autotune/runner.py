import os, json, subprocess, time, hashlib, glob
from pathlib import Path
from typing import Tuple, Dict, Any, List
from .schemas import RunReport, PatchProposal, Critique, Spec
from .helper import load_spec, review_patch, gate_improvement
from .patcher import apply_patch, revert_patch
from .persistence import OptimizationPersistence

def sh(cmd: str, cwd: str = ".") -> int:
    print(f"[RUN] {cmd}")
    return subprocess.call(cmd, shell=True, cwd=cwd)

def hash_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            b = f.read(1<<20)
            if not b: break
            h.update(b)
    return h.hexdigest()[:12]

def find_latest_report(outdir: Path) -> Path:
    cand = outdir / "run_report.json"
    if cand.exists():
        return cand
    # fallback: search
    matches = list(outdir.glob("**/run_report.json"))
    if not matches:
        raise FileNotFoundError(f"No run_report.json found in {outdir}")
    return max(matches, key=lambda p: p.stat().st_mtime)

def run_once(spec_path: str, input_path: str, workdir: str, run_cmd: str) -> RunReport:
    spec = load_spec(spec_path)
    outdir = Path(workdir) / f"autotune_run_{int(time.time())}"
    outdir.mkdir(parents=True, exist_ok=True)
    cmd = run_cmd.format(input=input_path, outdir=str(outdir))
    rc = sh(cmd)
    if rc != 0:
        raise RuntimeError(f"Run command failed with code {rc}")
    report_path = find_latest_report(outdir)
    rr = RunReport.from_json(str(report_path))
    if not rr.input_hash:
        rr.input_hash = hash_file(input_path)
    return rr

def propose_patch_via_grid(spec: Spec) -> List[PatchProposal]:
    proposals: List[PatchProposal] = []
    search = spec.search or {}
    for item in search.get("param_grids", []):
        file = item["file"]
        path = item["path"]
        values = item.get("values", [])
        for v in values:
            proposals.append(PatchProposal(file=file, kind="param", path=path, to=v, branch_name=f"autofix/{path.replace('.','_')}_{v}", commit_message=f"autotune: set {path} -> {v}"))
    return proposals

def autotune(spec_path: str, input_path: str, workdir: str = "runs", run_cmd: str = None, mode: str = "grid") -> Dict[str, Any]:
    spec = load_spec(spec_path)
    
    # Use run_cmd from spec if not provided
    if not run_cmd:
        run_cmd = spec.runner.get("run_cmd", "")
        if not run_cmd:
            raise ValueError("No run_cmd provided in arguments or spec file")
    
    print(f"Using run command: {run_cmd}")
    
    # Initialize persistence system
    persistence = OptimizationPersistence(workdir=workdir + "/optimization")
    
    # Initial run to get 'before'
    before = run_once(spec_path, input_path, workdir, run_cmd)
    
    # Create optimization session
    session_id = persistence.create_session(
        task=before.task,
        input_path=input_path,
        input_hash=before.input_hash,
        spec_path=spec_path,
        initial_metrics=before.metrics
    )
    
    print(f"Started optimization session: {session_id}")
    
    best = before
    tried_branches = []
    summary = {"before": before.__dict__, "attempts": [], "session_id": session_id}
    
    # Check for similar previous optimizations
    similar_sessions = persistence.find_similar_optimizations(before.input_hash, before.task)
    if similar_sessions:
        print(f"Found {len(similar_sessions)} previous optimization sessions for this input")
    
    # Candidate patches
    candidates = propose_patch_via_grid(spec) if mode=="grid" else []
    attempts = 0
    
    try:
        for patch in candidates:
            if attempts >= spec.budget.get("max_attempts", 3):
                break
                
            critique = review_patch(before, patch, spec)
            
            if critique.verdict == "reject":
                # Record rejected attempt
                attempt_id = persistence.record_attempt(
                    session_id=session_id,
                    patch_proposal=patch,
                    critique=critique,
                    before_metrics=before.metrics,
                    success=False,
                    error_message="Rejected by helper critic"
                )
                summary["attempts"].append({
                    "attempt_id": attempt_id,
                    "patch": patch.__dict__, 
                    "critique": critique.__dict__, 
                    "result": "rejected_pre"
                })
                continue
                
            branch = None
            try:
                branch = apply_patch(patch, repo_root=".")
                new = run_once(spec_path, input_path, workdir, run_cmd)
                ok, deltas = gate_improvement(before, new, spec)
                
                # Record attempt with results
                attempt_id = persistence.record_attempt(
                    session_id=session_id,
                    patch_proposal=patch,
                    critique=critique,
                    before_metrics=before.metrics,
                    after_metrics=new.metrics,
                    success=ok,
                    branch_name=branch
                )
                
                summary["attempts"].append({
                    "attempt_id": attempt_id,
                    "patch": patch.__dict__, 
                    "critique": critique.__dict__, 
                    "ok": ok, 
                    "deltas": deltas, 
                    "after": new.__dict__
                })
                
                if ok:
                    best = new
                    tried_branches.append(branch)
                    print(f"Improvement found! New best metrics: {new.metrics}")
                    break
                    
            except Exception as e:
                error_msg = str(e)
                # Record failed attempt
                attempt_id = persistence.record_attempt(
                    session_id=session_id,
                    patch_proposal=patch,
                    critique=critique,
                    before_metrics=before.metrics,
                    success=False,
                    error_message=error_msg,
                    branch_name=branch
                )
                summary["attempts"].append({
                    "attempt_id": attempt_id,
                    "patch": patch.__dict__, 
                    "error": error_msg
                })
                print(f"Attempt failed: {error_msg}")
            finally:
                if branch:
                    revert_patch(branch, repo_root=".")
            attempts += 1
        
        # Determine final status
        status = "completed"
        if attempts == 0:
            status = "halted"
        elif best.metrics == before.metrics:
            status = "no_improvement"
        
        # Update session completion
        persistence.update_session_completion(
            session_id=session_id,
            status=status,
            best_metrics=best.metrics,
            final_parameters={}  # Would need to extract actual final config parameters
        )
        
        summary["best"] = best.__dict__
        summary["status"] = status
        summary["session_id"] = session_id
        
        # Write traditional audit for backwards compatibility
        audit_dir = Path(workdir) / f"audit_{int(time.time())}"
        audit_dir.mkdir(parents=True, exist_ok=True)
        (audit_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        
        # Export comprehensive session report
        report_path = persistence.export_session_report(session_id)
        print(f"Comprehensive optimization report saved to: {report_path}")
        
        return summary
        
    except Exception as e:
        # Mark session as failed
        persistence.update_session_completion(
            session_id=session_id,
            status="failed",
            best_metrics=best.metrics,
            final_parameters={}
        )
        raise
