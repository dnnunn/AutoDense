# autodense/service/schemas.py
from pydantic import BaseModel, Field
from typing import List, Optional

class BandOut(BaseModel):
    lane_index: int; band_index: int; y0: int; y1: int; intensity: float; confidence: float; lane_type: str
class LaneOut(BaseModel):
    lane_index: int; lane_type: str; x0: int; x1: int; y0: int; y1: int; band_count: int
class InferRequest(BaseModel):
    modality: str = Field("sds", description="sds or dna")
    min_lanes: int = 6; max_lanes: int = 16; comb: Optional[int] = None
    num_ladders: int = 2; ladder_min_bands: int = 6; ladder_min_score: float = 0.35
    bg_radius: int = 30; invert: str = "auto"
class InferResponse(BaseModel):
    lanes: List[LaneOut]; bands: List[BandOut]; overlay_png_b64: Optional[str] = None
