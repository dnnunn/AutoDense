# autodense/vision/analyzer.py
from dataclasses import dataclass
from typing import List, Tuple, Optional, Dict
from pathlib import Path
import math, numpy as np
from PIL import Image, ImageOps, ImageDraw
from scipy.ndimage import uniform_filter1d
from skimage.morphology import white_tophat, rectangle
from skimage.filters import gaussian

@dataclass
class Band:
    index: int; y0: int; y1: int; confidence: float; intensity: Optional[float]=None; mw_kda: Optional[float]=None
@dataclass
class Lane:
    index: int; x0: int; x1: int; y0: int; y1: int; type: str; bands: List[Band]
@dataclass
class AnalysisResult:
    lanes: List[Lane]; ladder_lanes: List[int]; mw_fit: Optional[Dict]; image_size: Tuple[int,int]

def to_gray01(im: Image.Image) -> np.ndarray:
    g = ImageOps.grayscale(im); arr = np.asarray(g, dtype=np.float32)
    if arr.max() > 0: arr = (arr - arr.min()) / (arr.max() - arr.min() + 1e-6)
    return arr
def maybe_invert(arr01: np.ndarray, gel_type: str, invert_mode: str) -> np.ndarray:
    if invert_mode == "yes": return 1.0 - arr01
    if invert_mode == "no": return arr01
    if gel_type == "dna": return arr01
    return 1.0 - arr01 if arr01.mean() > 0.45 else arr01
def smooth1d(x: np.ndarray, win: int) -> np.ndarray:
    win = max(1, int(win)); 
    return x if win==1 else uniform_filter1d(x, size=win, mode="nearest")
def find_peaks_1d(x: np.ndarray, min_prom: float, min_distance: int) -> List[int]:
    peaks=[]; n=len(x)
    for i in range(1,n-1):
        if x[i]>x[i-1] and x[i]>=x[i+1]:
            l0=max(0,i-min_distance); r0=min(n-1,i+min_distance)
            prom=x[i]-float(np.min(x[l0:r0+1]))
            if prom>=min_prom:
                if not peaks or (i-peaks[-1])>=min_distance: peaks.append(i)
                else:
                    if x[i]>x[peaks[-1]]: peaks[-1]=i
    return peaks
def centers_to_ranges(centers: List[int], width: int) -> List[Tuple[int,int]]:
    if not centers: return [(0,width)]
    borders=[0]; 
    for a,b in zip(centers[:-1], centers[1:]): borders.append(int((a+b)/2))
    borders.append(width); 
    return [(borders[i],borders[i+1]) for i in range(len(borders)-1)]
def halfmax_bounds(y: np.ndarray, p: int) -> Tuple[int,int]:
    pv=y[p]; hm=max(1e-6, pv*0.5); i=p
    while i>0 and y[i]>hm: i-=1; left=i; j=p
    while j<len(y)-1 and y[j]>hm: j+=1; right=j; return left,right
def local_background(arr: np.ndarray, radius_px: int=20) -> np.ndarray:
    blurred=gaussian(arr, sigma=radius_px/4.0, preserve_range=True)
    selem=rectangle(max(3,radius_px//2), max(3,radius_px//2))
    topo=white_tophat(blurred, selem)
    return np.clip(blurred-topo,0,1.0).astype(np.float32)
def detect_lanes(arr: np.ndarray, min_lanes: int, max_lanes: int, comb: Optional[int]=None) -> List[Tuple[int,int]]:
    H,W=arr.shape; vproj=smooth1d(arr.sum(axis=0), max(3,W//200))
    peaks=find_peaks_1d(vproj, min_prom=0.02, min_distance=max(5,W//90))
    target=min(max_lanes, max(min_lanes, comb if comb else (len(peaks) if len(peaks)>0 else min_lanes)))
    if len(peaks)<target: centers=np.linspace(0,W-1,target).astype(int).tolist()
    else: centers=sorted([i for i,_ in sorted([(i,vproj[i]) for i in peaks], key=lambda t:t[1], reverse=True)[:target]])
    return centers_to_ranges(centers, W)
def detect_bands_in_lane(arr_lane: np.ndarray, gel_type: str, min_gap_frac: float=0.012, prom: float=0.08) -> List[Tuple[int,int,float]]:
    H=arr_lane.shape[0]; yproj=smooth1d(arr_lane.sum(axis=1), max(3,H//200))
    yproj=(yproj - yproj.min())/(yproj.max()-yproj.min()+1e-6)
    peaks=find_peaks_1d(yproj, min_prom=(0.06 if gel_type=='dna' else prom), min_distance=max(3,int(H*min_gap_frac)))
    bands=[]; 
    for p in peaks: y0,y1=halfmax_bounds(yproj,p); bands.append((int(y0),int(y1),float(yproj[p])))
    return bands
def ladder_score(ycenters_norm: List[float]) -> float:
    if len(ycenters_norm)<5: return 0.0
    y=np.array(sorted(ycenters_norm),dtype=np.float32); gaps=np.diff(y)
    if (gaps<=0).any(): return 0.0
    cv=float(np.std(gaps)/(np.mean(gaps)+1e-9))
    idx=np.arange(len(y)-1,dtype=np.float32); g=gaps-gaps.mean(); i0=idx-idx.mean()
    corr=float((g*i0).sum())/(math.sqrt((g*g).sum()+1e-9)*math.sqrt((i0*i0).sum()+1e-9))
    count=min(1.0,(len(y)-4)/6.0); return max(0.0,min(1.0,0.45*(1.0-min(1.0,cv))+0.35*max(0.0,corr)+0.20*count))
def assign_ladders(lanes: List[Lane], H: int, num_ladders: int=2, min_bands:int=6, min_score:float=0.35) -> List[int]:
    scored=[]; 
    for ln in lanes:
        ycs=[(b.y0+(b.y1-b.y0)/2)/H for b in ln.bands]; sc=ladder_score(ycs) if len(ycs)>=min_bands else 0.0
        scored.append((ln.index, sc))
    scored.sort(key=lambda t:t[1], reverse=True)
    chosen=[li for li,sc in scored[:num_ladders] if sc>=min_score]
    if len(chosen)<num_ladders and len(lanes)>=2:
        for e in [lanes[0].index, lanes[-1].index]:
            if e not in chosen: chosen.append(e)
            if len(chosen)==num_ladders: break
    return chosen
def integrate_band(arr: np.ndarray, bg: np.ndarray, x0:int, x1:int, y0:int, y1:int) -> float:
    roi=arr[y0:y1,x0:x1]; broi=bg[y0:y1,x0:x1]; return float(np.sum(np.clip(roi-broi,0,1.0)))
def analyze_image(path: Path, gel_type: str="protein", invert_mode: str="auto",
                  min_lanes:int=6, max_lanes:int=16, comb: Optional[int]=None,
                  num_ladders:int=2, ladder_min_bands:int=6, ladder_min_score:float=0.35,
                  bg_radius:int=30, min_lane_width_frac:float=0.035, empty_lane_thresh:float=0.4,
                  manual_lane_boundaries: Optional[List[Tuple[int, int]]]=None):
    im=Image.open(path).convert("RGB"); H,W=im.height,im.width
    gray=to_gray01(im); gray=maybe_invert(gray, gel_type, invert_mode)
    if gel_type=="dna":
        if invert_mode=="auto": pass
        if bg_radius==30: bg_radius=24
        if min_lanes==6: min_lanes=10
        if comb: min_lanes=max(min_lanes, comb-2); max_lanes=min(max_lanes, comb+2)
    else:
        if comb: min_lanes=max(min_lanes, comb-2); max_lanes=min(max_lanes, comb+2)
    # Use manual lane boundaries if provided, otherwise detect automatically
    if manual_lane_boundaries is not None:
        # Convert SimpleLaneBoundary objects to tuples if needed
        lane_ranges = []
        print(f"DEBUG: manual_lane_boundaries type: {type(manual_lane_boundaries)}")
        print(f"DEBUG: manual_lane_boundaries length: {len(manual_lane_boundaries) if manual_lane_boundaries else 'None'}")

        for i, boundary in enumerate(manual_lane_boundaries):
            print(f"DEBUG: boundary[{i}] type: {type(boundary)}, value: {boundary}")

            try:
                if hasattr(boundary, 'left_px') and hasattr(boundary, 'right_px'):
                    # Convert SimpleLaneBoundary to tuple
                    left_px = int(boundary.left_px)
                    right_px = int(boundary.right_px)
                    lane_ranges.append((left_px, right_px))
                    print(f"DEBUG: Converted SimpleLaneBoundary to tuple: ({left_px}, {right_px})")
                elif isinstance(boundary, (list, tuple)) and len(boundary) >= 2:
                    # Already in tuple format
                    left_px = int(boundary[0])
                    right_px = int(boundary[1])
                    lane_ranges.append((left_px, right_px))
                    print(f"DEBUG: Used existing tuple format: ({left_px}, {right_px})")
                else:
                    # Invalid format, skip this boundary
                    print(f"WARNING: Invalid lane boundary format: {type(boundary)}, {boundary}")
            except Exception as e:
                print(f"ERROR: Failed to convert boundary[{i}]: {e}")
                print(f"ERROR: boundary type: {type(boundary)}, value: {boundary}")

        print(f"DEBUG: Final lane_ranges length: {len(lane_ranges)}")
        print(f"DEBUG: lane_ranges content: {lane_ranges}")

        # Validate that all elements in lane_ranges are tuples
        for i, lr in enumerate(lane_ranges):
            if not isinstance(lr, tuple):
                print(f"ERROR: lane_ranges[{i}] is not a tuple: {type(lr)}, {lr}")

        if not lane_ranges:
            # If no valid boundaries, fall back to automatic detection
            print("WARNING: No valid manual lane boundaries, falling back to automatic detection")
            lane_ranges = detect_lanes(gray, min_lanes=min_lanes, max_lanes=max_lanes, comb=comb)
        else:
            print(f"INFO: Using {len(lane_ranges)} manual lane boundaries")
    else:
        lane_ranges = detect_lanes(gray, min_lanes=min_lanes, max_lanes=max_lanes, comb=comb)
    import numpy as np

    # Additional validation: ensure all lane_ranges are tuples before unpacking
    print(f"DEBUG: Before unpacking - lane_ranges type: {type(lane_ranges)}")
    print(f"DEBUG: Before unpacking - lane_ranges content: {lane_ranges}")

    # Validate and fix any non-tuple elements
    validated_lane_ranges = []
    for i, lr in enumerate(lane_ranges):
        if isinstance(lr, tuple) and len(lr) >= 2:
            validated_lane_ranges.append(lr)
        elif hasattr(lr, 'left_px') and hasattr(lr, 'right_px'):
            # Last resort conversion
            print(f"WARNING: Converting SimpleLaneBoundary at index {i} to tuple")
            validated_lane_ranges.append((int(lr.left_px), int(lr.right_px)))
        else:
            print(f"ERROR: Cannot process lane_ranges[{i}]: {type(lr)}, {lr}")
            raise ValueError(f"Invalid lane range format at index {i}: {type(lr)}")

    lane_ranges = validated_lane_ranges
    print(f"DEBUG: After validation - lane_ranges: {lane_ranges}")

    lane_means=[float(gray[:,x0:x1].mean()) if (x1-x0)>0 else 0.0 for (x0,x1) in lane_ranges]
    if lane_means:
        med=float(np.median(lane_means)); keep=[i for i,m in enumerate(lane_means) if m>=max(1e-6, med*empty_lane_thresh)]
        if keep and len(keep)!=len(lane_ranges): lane_ranges=[lane_ranges[i] for i in keep]
    merged=[]; i=0
    while i<len(lane_ranges):
        x0,x1=lane_ranges[i]; w=x1-x0
        if w<max(2,int(W*min_lane_width_frac)) and i+1<len(lane_ranges):
            nx0,nx1=lane_ranges[i+1]; merged.append((x0,nx1)); i+=2
        else: merged.append((x0,x1)); i+=1
    lane_ranges=merged
    lanes=[]; 
    for li,(x0,x1) in enumerate(lane_ranges, start=1):
        lane_arr=gray[:,x0:x1]
        bs_raw=detect_bands_in_lane(lane_arr, gel_type=gel_type)
        bs=[Band(index=bi, y0=y0, y1=y1, confidence=conf) for bi,(y0,y1,conf) in enumerate(bs_raw, start=1)]
        lanes.append(Lane(index=li,x0=x0,x1=x1,y0=0,y1=H-1,type="sample",bands=bs))
    ladder_lanes=assign_ladders(lanes,H,num_ladders=num_ladders,min_bands=ladder_min_bands,min_score=ladder_min_score)
    for ln in lanes:
        if ln.index in ladder_lanes: ln.type="marker"
    bg=local_background(gray, radius_px=bg_radius)
    for ln in lanes:
        for b in ln.bands:
            b.intensity=integrate_band(gray,bg,ln.x0,ln.x1,b.y0,b.y1)
    from dataclasses import dataclass
    @dataclass
    class _Res: pass
    return type("AnalysisResult", (), {"lanes": lanes, "ladder_lanes": ladder_lanes, "mw_fit": None, "image_size": (W,H)})
def draw_overlay(im: Image.Image, res, out_path: Path):
    draw=ImageDraw.Draw(im,"RGBA")
    for ln in res.lanes:
        color=(0,0,255,90) if ln.type=="marker" else (0,255,0,70)
        draw.rectangle([ln.x0,ln.y0,ln.x1,ln.y1], outline=(0,0,0,200), width=2, fill=color)
        draw.text((ln.x0+4,6), f"{'M' if ln.type=='marker' else 'L'}{ln.index}", fill=(0,0,0,220))
        for b in ln.bands:
            draw.rectangle([ln.x0,b.y0,ln.x1,b.y1], outline=(255,0,0,220), width=2)
    im.save(out_path)
