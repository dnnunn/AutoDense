# autodense/service/api_infer.py
import base64, io
from fastapi import APIRouter, UploadFile, File, Depends, Query
from fastapi.responses import JSONResponse
from PIL import Image
from .schemas import InferRequest, InferResponse, LaneOut, BandOut
from ..vision.analyzer import analyze_image, draw_overlay
from ..vision.mw_helpers import ladder_lane_indices, compute_mw_or_bp
from pathlib import Path

router = APIRouter(prefix="/api", tags=["infer"])

@router.get("/health")
async def health():
    return JSONResponse({"status":"ok","service":"AutoDense","version":"python-mvp"})

@router.post("/infer", response_model=InferResponse)
async def infer_image(
    file: UploadFile = File(...),
    req: InferRequest = Depends(),
    ladder_lanes: str = Query("", description="Comma-separated lane indices that are ladders, e.g. '1,10'"),
    ladder_type: str = Query("auto", description="auto|pageruler_10_180|neb_1kb")
):
    raw = await file.read()
    im = Image.open(io.BytesIO(raw)).convert("RGB")
    tmp = Path("/tmp/_ad_infer.png"); im.save(tmp)
    gel_type = "protein" if req.modality.lower()=="sds" else "dna"
    res = analyze_image(tmp, gel_type=gel_type, invert_mode=req.invert,
                        min_lanes=req.min_lanes, max_lanes=req.max_lanes, comb=req.comb,
                        num_ladders=req.num_ladders, ladder_min_bands=req.ladder_min_bands,
                        ladder_min_score=req.ladder_min_score, bg_radius=req.bg_radius)
    ladder_idxs = ladder_lane_indices(ladder_lanes)
    fit_info = None
    if ladder_idxs:
        mw = compute_mw_or_bp(res, req.modality, ladder_idxs, ladder_type=ladder_type)
        fit_info = mw

    lanes_out=[]; bands_out=[]
    for ln in res.lanes:
        lanes_out.append(LaneOut(lane_index=ln.index, lane_type=ln.type, x0=ln.x0, x1=ln.x1, y0=ln.y0, y1=ln.y1, band_count=len(ln.bands)))
        for b in ln.bands:
            bands_out.append(BandOut(lane_index=ln.index, band_index=b.index, y0=b.y0, y1=b.y1,
                                     intensity=b.intensity or 0.0, confidence=b.confidence, lane_type=ln.type))

    buf = io.BytesIO(); outp = Path("/tmp/_ad_overlay.png")
    draw_overlay(im.copy(), res, outp)
    Image.open(outp).save(buf, format="PNG")
    payload = InferResponse(lanes=lanes_out, bands=bands_out, overlay_png_b64=base64.b64encode(buf.getvalue()).decode("ascii")).dict()
    if fit_info: payload["mw"] = fit_info
    return JSONResponse(payload)
