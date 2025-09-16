# autodense/orchestrator/pipeline.py
from dataclasses import dataclass
from typing import Optional, Dict, Any
from pathlib import Path
from ..vision.analyzer import analyze_image
from ..metrics.gel_metrics import summarize, acceptance_default

@dataclass
class Params:
    modality: str = "sds"
    min_lanes: int = 6
    max_lanes: int = 16
    comb: Optional[int] = None
    num_ladders: int = 2
    ladder_min_bands: int = 6
    ladder_min_score: float = 0.35
    bg_radius: int = 30
    invert: str = "auto"

def observe(res) -> Dict[str, Any]:
    return summarize(res)

def small_rule_coach(obs: Dict[str,Any], p: Params, modality:str) -> Optional[Params]:
    newp = Params(**vars(p)); changed=False
    if obs.get("empty_frac", 0) >= 0.5 and p.comb:
        newp.min_lanes = max(2, p.comb-1); newp.max_lanes = p.comb+1; changed=True
    if obs.get("markers",0)==0 and p.ladder_min_score>0.25:
        newp.ladder_min_score = p.ladder_min_score - 0.05; changed=True
    if obs.get("lane_width_cv",0) > 0.4 and p.comb:
        newp.min_lanes = max(2, p.comb-1); newp.max_lanes = p.comb+1; changed=True
    return newp if changed else None

def run(image: Path, p: Params, retries: int=1, use_llm: bool=False):
    gel_type = "protein" if p.modality.lower()=="sds" else "dna"
    res = analyze_image(image, gel_type=gel_type, invert_mode=p.invert,
                        min_lanes=p.min_lanes, max_lanes=p.max_lanes, comb=p.comb,
                        num_ladders=p.num_ladders, ladder_min_bands=p.ladder_min_bands,
                        ladder_min_score=p.ladder_min_score, bg_radius=p.bg_radius)
    obs = observe(res)
    acc = acceptance_default(p.modality, res)

    attempt = 0
    while attempt < retries and not acc.passed:
        proposal = small_rule_coach(obs, p, p.modality)
        if not proposal:
            proposal = Params(**vars(p))
            proposal.min_lanes = max(2, p.min_lanes - 1)
            proposal.max_lanes = p.max_lanes + 1
            proposal.ladder_min_score = max(0.25, p.ladder_min_score - 0.05)
        p = proposal
        res = analyze_image(image, gel_type=gel_type, invert_mode=p.invert,
                            min_lanes=p.min_lanes, max_lanes=p.max_lanes, comb=p.comb,
                            num_ladders=p.num_ladders, ladder_min_bands=p.ladder_min_bands,
                            ladder_min_score=p.ladder_min_score, bg_radius=p.bg_radius)
        obs = observe(res)
        acc = acceptance_default(p.modality, res)
        attempt += 1

    obs.update({"acceptance": acc.details, "accepted": acc.passed})
    return res, p, obs
