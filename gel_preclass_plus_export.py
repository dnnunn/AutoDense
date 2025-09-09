#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse, tarfile, io, sys, json, csv, os
from pathlib import Path
from typing import List, Tuple, Optional, Dict, Union
import numpy as np
from PIL import Image, ImageOps, ImageDraw

# -----------------------------
# IO + image utils
# -----------------------------
def ensure_dir(p: Path):
    p.mkdir(parents=True, exist_ok=True)

def load_image_any(p: Path):
    im = Image.open(p); im.load(); return im

def load_image_bytes(fp: io.BytesIO):
    im = Image.open(fp); im.load(); return im

def to_gray(im: Image.Image) -> np.ndarray:
    g = ImageOps.grayscale(im)
    return np.asarray(g, dtype=np.float32) / 255.0

def maybe_invert(arr01: np.ndarray, mode: str) -> np.ndarray:
    if mode == "yes": return 1.0 - arr01
    if mode == "no":  return arr01
    m = float(arr01.mean())
    return arr01 if m < 0.45 else (1.0 - arr01)

# -----------------------------
# Signal helpers
# -----------------------------
def smooth1d(x: np.ndarray, win: int) -> np.ndarray:
    win = max(1, int(win))
    if win == 1: return x
    k = np.ones(win, dtype=np.float32) / win
    return np.convolve(x, k, mode="same")

def find_peaks(x: np.ndarray, min_prom: float, min_distance: int) -> List[int]:
    n = len(x); peaks = []
    for i in range(1, n-1):
        if x[i] > x[i-1] and x[i] >= x[i+1]:
            w = max(3, min_distance)
            l0 = max(0, i-w); r0 = min(n-1, i+w)
            neigh_min = float(np.min(x[l0:r0+1]))
            prom = x[i] - neigh_min
            if prom >= min_prom:
                if len(peaks)==0 or (i - peaks[-1]) >= min_distance:
                    peaks.append(i)
                else:
                    if x[i] > x[peaks[-1]]:
                        peaks[-1] = i
    return peaks

def halfmax_bounds(y: np.ndarray, peak_idx: int) -> Tuple[int,int]:
    peak_val = y[peak_idx]
    if peak_val <= 0: return peak_idx, peak_idx
    hm = peak_val * 0.5
    i = peak_idx
    while i > 0 and y[i] > hm: i -= 1
    left = i
    j = peak_idx; n = len(y)
    while j < n-1 and y[j] > hm: j += 1
    right = j
    return left, right

# -----------------------------
# Lane estimation and scoring
# -----------------------------
def estimate_lane_centers(vproj: np.ndarray, min_distance: int, rel_prom: float) -> List[int]:
    x = vproj - vproj.min()
    if x.max() > 0: x = x / x.max()
    W = len(x); sm = smooth1d(x, max(3, W//200))
    peaks_all = find_peaks(sm, min_prom=rel_prom, min_distance=min_distance)
    return sorted(peaks_all)

def centers_to_ranges(centers: List[int], width: int) -> List[Tuple[int,int]]:
    if not centers: return [(0, width)]
    borders = [0]
    for a,b in zip(centers[:-1], centers[1:]):
        borders.append(int((a+b)/2))
    borders.append(width)
    return [(borders[i], borders[i+1]) for i in range(len(borders)-1)]

def score_lane_layout(arr: np.ndarray, lane_ranges_px: List[Tuple[int,int]], gel_type: str) -> float:
    H, W = arr.shape
    scores = []; widths = []; means = []
    for (x0,x1) in lane_ranges_px:
        widths.append(max(1, x1-x0))
        seg = arr[:, x0:x1]; means.append(float(seg.mean()) if seg.size else 0.0)
        yproj = seg.sum(axis=1); yproj = yproj - yproj.min()
        if yproj.max() > 0: yproj = yproj / yproj.max()
        peaks = find_peaks(yproj, min_prom=0.05 if gel_type=="protein" else 0.04, min_distance=max(3, H//180))
        topk = sorted([yproj[i] for i in peaks], reverse=True)[:12] if len(peaks)>0 else [0.0]
        scores.append(float(sum(topk)))
    if not scores: return -1e9
    s_band = float(sum(scores)) / max(1, len(scores))
    w = np.array(widths, dtype=np.float32)
    width_pen = float(np.std(w) / (np.mean(w)+1e-6))
    m = np.array(means, dtype=np.float32); med = float(np.median(m)+1e-6)
    empty_frac = float(np.mean(m < 0.4*med))
    return s_band - 0.25*width_pen - 0.3*empty_frac

def choose_lane_layout(arr: np.ndarray, comb: Optional[int], min_lanes: int, max_lanes: int, rel_prom: float, gel_type: str) -> List[Tuple[int,int]]:
    H, W = arr.shape
    min_sep = max(5, W // 90)
    best = None; best_score = -1e9
    lo = min_lanes; hi = max_lanes
    if comb is not None:
        lo = max(min_lanes, comb-2); hi = min(max_lanes, comb+2)
    for n in range(lo, hi+1):
        centers = estimate_lane_centers(arr.sum(axis=0), min_distance=min_sep, rel_prom=rel_prom)
        x = arr.sum(axis=0); x = x - x.min(); x = x/(x.max()+1e-6)
        if len(centers) != n:
            if len(centers) > n:
                vals = sorted([(i, x[i]) for i in centers], key=lambda t: t[1], reverse=True)[:n]
                centers = sorted([i for i,_ in vals])
            else:
                centers = np.linspace(0, W-1, n).astype(int).tolist()
        ranges = centers_to_ranges(centers, W)
        sc = score_lane_layout(arr, ranges, gel_type=gel_type)
        if sc > best_score: best_score = sc; best = ranges
    return best if best is not None else [(0, arr.shape[1])]

# -----------------------------
# Ladder scoring
# -----------------------------
def _cv(x):
    x = np.asarray(x, dtype=np.float32); m = float(x.mean()) + 1e-8
    return float(np.std(x) / m)

def ladder_score(ycenters: list) -> float:
    # ycenters are normalized (0..1). Higher is more ladder-like.
    if not ycenters or len(ycenters) < 5: return 0.0
    y = np.array(sorted(ycenters), dtype=np.float32); gaps = np.diff(y)
    if (gaps <= 0).any(): return 0.0
    cv_gaps = _cv(gaps)
    idx = np.arange(len(y)-1, dtype=np.float32)
    g = gaps - gaps.mean(); i0 = idx - idx.mean()
    num = float((g * i0).sum()); den = float(np.sqrt((g*g).sum()) * np.sqrt((i0*i0).sum()) + 1e-9)
    corr = num / den
    k = len(y); count_term = min(1.0, (k-4)/6.0)
    score = 0.45*(1.0 - min(1.0, cv_gaps)) + 0.35*max(0.0, corr) + 0.20*count_term
    return float(max(0.0, min(1.0, score)))

# -----------------------------
# QC overlays
# -----------------------------
def draw_overlays(im_rgb: Image.Image, lane_ranges_px: List[Tuple[int,int]], band_boxes_px: Dict[int, List[Tuple[int,int,int,int]]], markers: set, out_path: Path):
    draw = ImageDraw.Draw(im_rgb, "RGBA")
    H, W = im_rgb.height, im_rgb.width
    for li,(x0,x1) in enumerate(lane_ranges_px, start=1):
        is_marker = li in markers
        fill = (0, 0, 255, 90) if is_marker else (0, 255, 0, 70)
        draw.rectangle([x0, 0, x1, H-1], outline=(0,0,0,160), width=2, fill=fill)
        tag = ("M" if is_marker else "L") + str(li)
        draw.text((x0+4, 4), tag, fill=(0,0,0,220))
        for (bx, by, bw, bh) in band_boxes_px.get(li, []):
            draw.rectangle([bx, by, bx+bw, by+bh], outline=(255,0,0,220), width=2)
    im_rgb.save(out_path)

# -----------------------------
# Core processing
# -----------------------------
def process_image(im: Image.Image,
                  image_name: str,
                  gel_type: str,
                  invert_mode: str,
                  comb: Optional[int],
                  detect: str,
                  min_lanes: int, max_lanes: int,
                  peak_prominence: float, min_band_gap_frac: float,
                  min_lane_width_frac: float, empty_lane_thresh: float,
                  ladder_mode: str, num_ladders: int,
                  ladder_min_bands: int, ladder_min_score: float, ladder_fallback: str):
    arr0 = to_gray(im)
    arr = maybe_invert(arr0, invert_mode if invert_mode!="auto" else ("no" if gel_type=="dna" else "auto"))
    H, W = arr.shape

    # lane layout
    if detect == "grid" and comb is not None:
        xs = np.linspace(0, W, comb+1).astype(int).tolist()
        lane_ranges_px = [(xs[i], xs[i+1]) for i in range(comb)]
    else:
        lane_ranges_px = choose_lane_layout(arr, comb=comb, min_lanes=min_lanes, max_lanes=max_lanes, rel_prom=0.05, gel_type=gel_type)

    # cleanup
    lane_means = [float(arr[:, x0:x1].mean()) if (x1-x0)>0 else 0.0 for (x0,x1) in lane_ranges_px]
    if lane_means:
        med = float(np.median(lane_means))
        keep = [i for i,m in enumerate(lane_means) if m >= max(1e-6, med*empty_lane_thresh)]
        if keep and len(keep) != len(lane_ranges_px):
            lane_ranges_px = [lane_ranges_px[i] for i in keep]

    merged = []; i = 0
    while i < len(lane_ranges_px):
        x0,x1 = lane_ranges_px[i]; width = x1 - x0
        if width < max(2, int(W * min_lane_width_frac)) and i+1 < len(lane_ranges_px):
            nx0, nx1 = lane_ranges_px[i+1]; merged.append((x0, nx1)); i += 2
        else:
            merged.append((x0,x1)); i += 1
    lane_ranges_px = merged
    nlanes_actual = len(lane_ranges_px)

    lanes_csv = []; bands_csv = []; lanes_jsonl = []; bands_jsonl = []
    band_boxes_px = {}; lane_band_ycenters = {}

    min_gap = max(3, int(H * (0.010 if gel_type=="dna" else min_band_gap_frac)))
    prom = peak_prominence if gel_type=="protein" else max(0.04, peak_prominence*0.75)

    for lane_idx, (x0, x1) in enumerate(lane_ranges_px, start=1):
        lane_w = x1 - x0
        lane_center = x0 + lane_w/2.0
        lane_type = "sample"; alias = f"L{lane_idx}"

        lane_arr = arr[:, x0:x1]
        yproj = lane_arr.sum(axis=1)
        yproj_sm = smooth1d(yproj, max(3, H//200))
        yp = yproj_sm - yproj_sm.min(); yp = yp / (yp.max() + 1e-6)

        peaks = find_peaks(yp, min_prom=prom, min_distance=min_gap)

        boxes_for_lane = []
        for bi, p in enumerate(peaks, start=1):
            y0, y1 = halfmax_bounds(yp, p)
            y0 = max(0, y0); y1 = min(H-1, y1)
            if y1 <= y0: y0 = max(0, p-1); y1 = min(H-1, p+1)
            bh = max(2, y1 - y0 + 1)
            bw = max(3, int(lane_w * (0.85 if gel_type=="protein" else 0.8)))
            bx = int(lane_center - bw/2); by = int(y0)
            conf = float(yp[p])

            bx0n = max(0.0, bx / W); by0n = max(0.0, by / H)
            bwn = min(1.0, bw / W); bhn = min(1.0, bh / H)

            band_rec = {
                "image_id": image_name, "gel_id": Path(image_name).stem,
                "lane_index": lane_idx, "lane_type": lane_type, "alias": alias, "band_index": bi,
                "bbox_norm": [round(bx0n,6), round(by0n,6), round(bwn,6), round(bhn,6)],
                "y_center_norm": round((by + bh/2)/H, 6), "height_norm": round(bhn, 6),
                "intensity_norm": round(conf, 6), "confidence": round(conf, 6), "label": "band"
            }
            bands_jsonl.append(band_rec)
            bands_csv.append({
                **{k: band_rec[k] for k in ["image_id","gel_id","lane_index","lane_type","alias","band_index"]},
                "x_norm": band_rec["bbox_norm"][0], "y_norm": band_rec["bbox_norm"][1],
                "w_norm": band_rec["bbox_norm"][2], "h_norm": band_rec["bbox_norm"][3],
                "y_center_norm": band_rec["y_center_norm"], "height_norm": band_rec["height_norm"],
                "intensity_norm": band_rec["intensity_norm"], "confidence": band_rec["confidence"], "label": "band"
            })
            boxes_for_lane.append((bx, by, bw, bh))
            lane_band_ycenters[lane_idx] = lane_band_ycenters.get(lane_idx, []) + [ (by + bh/2)/H ]

        band_boxes_px[lane_idx] = boxes_for_lane

        lanes_csv.append({
            "image_id": image_name, "gel_id": Path(image_name).stem, "lane_index": lane_idx,
            "lane_type": lane_type, "alias": alias,
            "x_start_norm": round(x0 / W, 6), "x_end_norm": round(x1 / W, 6),
            "x_center_norm": round((lane_center) / W, 6), "lane_width_norm": round((lane_w) / W, 6),
            "band_count_est": len(peaks),
            "band_y_positions_norm": ",".join(str(round((b[1]+b[3]/2)/H,6)) for b in boxes_for_lane),
            "confidence_0to1": round(min(1.0, 0.5 + 0.05*len(peaks)), 3),
            "notes": f"auto bands; lanes={nlanes_actual}"
        })
        lanes_jsonl.append({
            "image_id": image_name, "gel_id": Path(image_name).stem,
            "lane": {
                "index": lane_idx, "type": lane_type, "alias": alias,
                "x_range_norm": [round(x0/W,6), round(x1/W,6)],
                "center_norm": round(lane_center/W,6), "width_norm": round(lane_w/W,6),
            },
            "provisional": {
                "band_count": len(peaks),
                "band_positions_norm": [round((b[1]+b[3]/2)/H,6) for b in boxes_for_lane],
                "confidence": round(min(1.0, 0.5 + 0.05*len(peaks)), 3),
                "notes": f"auto bands; lanes={nlanes_actual}"
            },
            "image_meta": {"H": H, "W": W, "aspect": round(H/max(1,W),6)}
        })

    # Ladder assignment (sanity filter)
    markers = set()
    if ladder_mode != "off" and nlanes_actual >= 1:
        k = 2 if num_ladders == 2 else 1
        scored = [(li, ladder_score(lane_band_ycenters.get(li, [])), len(lane_band_ycenters.get(li, []))) for li in range(1, nlanes_actual+1)]
        scored.sort(key=lambda t: t[1], reverse=True)
        valid = [li for (li, sc, cnt) in scored if cnt >= ladder_min_bands and sc >= ladder_min_score]
        if len(valid) >= k:
            markers = set(valid[:k])
        else:
            # fallback to edges if available; else 'best' or 'none' would be handled in exporters/labels
            markers = set([1, nlanes_actual][:k])

        def _alias_after(li, alias, nlanes):
            if len(markers)==2:
                if li == min(markers): return "M1"
                if li == max(markers): return "M2"
            if len(markers)==1 and li in markers: return "M"
            return alias

        for r in lanes_csv:
            li = r["lane_index"]
            if li in markers:
                r["lane_type"] = "marker"
                r["alias"] = _alias_after(li, r["alias"], nlanes_actual)
        for r in bands_csv:
            li = r["lane_index"]
            if li in markers:
                r["lane_type"] = "marker"
                r["alias"] = _alias_after(li, r["alias"], nlanes_actual)
        for r in lanes_jsonl:
            li = r["lane"]["index"]
            if li in markers:
                r["lane"]["type"] = "marker"
                r["lane"]["alias"] = _alias_after(li, r["lane"]["alias"], nlanes_actual)
        for r in bands_jsonl:
            li = r["lane_index"]
            if li in markers:
                r["lane_type"] = "marker"
                r["alias"] = _alias_after(li, r["alias"], nlanes_actual)

    return lanes_csv, bands_csv, lanes_jsonl, bands_jsonl, lane_ranges_px, band_boxes_px, markers

# -----------------------------
# Export helpers
# -----------------------------
def clamp(a, lo, hi): return max(lo, min(hi, a))

def export_lane_crops(im: Image.Image, image_name: str, lane_ranges_px: List[Tuple[int,int]], out_dir: Path):
    H, W = im.height, im.width
    ensure_dir(out_dir)
    for li,(x0,x1) in enumerate(lane_ranges_px, start=1):
        x0c = clamp(x0, 0, W-1); x1c = clamp(x1, 1, W)
        crop = im.crop((x0c, 0, x1c, H))
        crop.save(out_dir / f"{Path(image_name).stem}__L{li}.png")

def export_band_crops(im: Image.Image, image_name: str, bands_csv_rows: List[Dict], out_dir: Path, pad_frac: float=0.08):
    H, W = im.height, im.width
    ensure_dir(out_dir)
    for r in bands_csv_rows:
        x = int(r["x_norm"]*W); y = int(r["y_norm"]*H); w = int(r["w_norm"]*W); h = int(r["h_norm"]*H)
        pad = int(max(w,h) * pad_frac)
        x0 = clamp(x-pad, 0, W-1); y0 = clamp(y-pad, 0, H-1)
        x1 = clamp(x+w+pad, 1, W); y1 = clamp(y+h+pad, 1, H)
        crop = im.crop((x0,y0,x1,y1))
        li = r["lane_index"]; bi = r["band_index"]
        crop.save(out_dir / f"{Path(image_name).stem}__L{li}_B{bi}.png")

def export_coco(json_path: Path, images_meta: List[Dict], annotations: List[Dict]):
    coco = {
        "images": images_meta,
        "annotations": annotations,
        "categories": [{"id": 1, "name": "band"}]
    }
    with open(json_path, "w") as f:
        json.dump(coco, f, indent=2)

def export_yolo(dir_path: Path, image_stem: str, bands_csv_rows: List[Dict]):
    ensure_dir(dir_path)
    # one class: 0 = band
    lines = []
    for r in bands_csv_rows:
        xc = r["x_norm"] + r["w_norm"]/2.0
        yc = r["y_norm"] + r["h_norm"]/2.0
        lines.append(f"0 {xc:.6f} {yc:.6f} {r['w_norm']:.6f} {r['h_norm']:.6f}")
    with open(dir_path / f"{image_stem}.txt", "w") as f:
        f.write("\n".join(lines))

# -----------------------------
# CLI
# -----------------------------
def main():
    ap = argparse.ArgumentParser(description="Gel preclassifier + exporters (lanes/bands crops, COCO, YOLO).")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--tar", type=Path, help="Path to tar/tar.gz/tgz of image files")
    src.add_argument("--dir", type=Path, help="Directory of images")

    ap.add_argument("--out", type=Path, default=Path("preclass_export_out"), help="Output directory")
    ap.add_argument("--gel-type", choices=["protein","dna"], default="protein", help="Protein (Coomassie) or DNA (EtBr)")
    ap.add_argument("--invert", choices=["auto","yes","no"], default="auto", help="Invert intensity so bands are high")
    ap.add_argument("--comb", type=str, default="auto", help="Well count prior: 'auto', '10', '20', or any integer")
    ap.add_argument("--detect", choices=["auto","grid"], default="auto", help="Lane detection mode")
    ap.add_argument("--min-lanes", type=int, default=2, help="Lower bound for auto lane count")
    ap.add_argument("--max-lanes", type=int, default=50, help="Upper bound for auto lane count")

    ap.add_argument("--peak-prominence", type=float, default=0.08, help="Min peak prominence (0..1) for bands")
    ap.add_argument("--min-band-gap-frac", type=float, default=0.012, help="Min vertical band separation (fraction of image height)")
    ap.add_argument("--min-lane-width-frac", type=float, default=0.035, help="Merge lanes thinner than this fraction of width")
    ap.add_argument("--empty-lane-thresh", type=float, default=0.4, help="Drop lanes with mean < thresh * median")

    ap.add_argument("--ladder-mode", choices=["auto","edges","off"], default="auto", help="How to assign marker lanes")
    ap.add_argument("--num-ladders", type=int, choices=[1,2], default=2, help="How many marker lanes to assign")
    ap.add_argument("--ladder-min-bands", type=int, default=6, help="Minimum bands required to accept a ladder candidate")
    ap.add_argument("--ladder-min-score", type=float, default=0.35, help="Minimum ladder score required (0..1)")
    ap.add_argument("--ladder-fallback", choices=["edges","best","none"], default="edges", help="If no lane passes sanity, how to assign markers")

    # Exporters
    ap.add_argument("--export-lane-crops", action="store_true", help="Write per-lane crops under out/crops/lanes")
    ap.add_argument("--export-band-crops", action="store_true", help="Write per-band crops under out/crops/bands")
    ap.add_argument("--band-pad-frac", type=float, default=0.08, help="Padding around band crops as fraction of max(w,h)")
    ap.add_argument("--export-coco", type=Path, default=None, help="Path to write COCO JSON (e.g., out/coco/annotations.json)")
    ap.add_argument("--export-yolo", type=Path, default=None, help="Directory to write YOLO .txt labels")

    ap.add_argument("--qc", action="store_true", help="Emit QC overlays")
    ap.add_argument("--extensions", nargs="+", default=[".png",".jpg",".jpeg",".tif",".tiff"], help="Image extensions to include")
    args = ap.parse_args()

    # gel-type sensible defaults
    if args.gel_type == "dna":
        if args.invert == "auto": args.invert = "no"
        if args.peak_prominence == 0.08: args.peak_prominence = 0.06
        if args.min_band_gap_frac == 0.012: args.min_band_gap_frac = 0.010

    # comb prior
    comb = None
    if args.comb.lower() != "auto":
        try: comb = int(args.comb)
        except: print("[warn] --comb must be integer or 'auto'; ignoring.", file=sys.stderr)

    ensure_dir(args.out)
    qc_dir = args.out / "qc"
    crops_lanes_dir = args.out / "crops" / "lanes"
    crops_bands_dir = args.out / "crops" / "bands"
    if args.qc: ensure_dir(qc_dir)

    lanes_csv_path = args.out / "lanes.csv"
    bands_csv_path = args.out / "bands.csv"
    lanes_jsonl_path = args.out / "lanes.jsonl"
    bands_jsonl_path = args.out / "bands.jsonl"

    lanes_fields = ["image_id","gel_id","lane_index","lane_type","alias","x_start_norm","x_end_norm","x_center_norm","lane_width_norm","band_count_est","band_y_positions_norm","confidence_0to1","notes"]
    bands_fields = ["image_id","gel_id","lane_index","lane_type","alias","band_index","x_norm","y_norm","w_norm","h_norm","y_center_norm","height_norm","intensity_norm","confidence","label"]

    f_lanes = open(lanes_csv_path, "w", newline=""); w_lanes = csv.DictWriter(f_lanes, fieldnames=lanes_fields); w_lanes.writeheader()
    f_bands = open(bands_csv_path, "w", newline=""); w_bands = csv.DictWriter(f_bands, fieldnames=bands_fields); w_bands.writeheader()
    f_lanes_j = open(lanes_jsonl_path, "w"); f_bands_j = open(bands_jsonl_path, "w")

    # COCO registry
    coco_images = []; coco_anns = []; ann_id = 1; image_id_counter = 1
    image_id_map = {}

    def handle_image(name: str, im: Image.Image):
        nonlocal ann_id, image_id_counter
        lanes_csv_rows, bands_csv_rows, lanes_jsonl_rows, bands_jsonl_rows, lane_ranges_px, band_boxes_px, markers = process_image(
            im=im, image_name=name,
            gel_type=args.gel_type, invert_mode=args.invert, comb=comb, detect=args.detect,
            min_lanes=args.min_lanes, max_lanes=args.max_lanes,
            peak_prominence=args.peak_prominence, min_band_gap_frac=args.min_band_gap_frac,
            min_lane_width_frac=args.min_lane_width_frac, empty_lane_thresh=args.empty_lane_thresh,
            ladder_mode=args.ladder_mode, num_ladders=args.num_ladders,
            ladder_min_bands=args.ladder_min_bands, ladder_min_score=args.ladder_min_score, ladder_fallback=args.ladder_fallback
        )
        for r in lanes_csv_rows: w_lanes.writerow(r)
        for r in bands_csv_rows: w_bands.writerow(r)
        for r in lanes_jsonl_rows: f_lanes_j.write(json.dumps(r) + "\n")
        for r in bands_jsonl_rows: f_bands_j.write(json.dumps(r) + "\n")

        if args.qc:
            im_rgb = im.convert("RGB").copy()
            draw_overlays(im_rgb, lane_ranges_px, band_boxes_px, markers, qc_dir / f"{Path(name).stem}_qc.png")

        # exports
        if args.export_lane_crops:
            export_lane_crops(im.convert("RGB"), name, lane_ranges_px, crops_lanes_dir)
        if args.export_band_crops:
            export_band_crops(im.convert("RGB"), name, bands_csv_rows, crops_bands_dir, pad_frac=args.band_pad_frac)

        # COCO
        if args.export_coco:
            stem = Path(name).name
            img_id = image_id_map.get(stem)
            if img_id is None:
                img_id = image_id_counter; image_id_map[stem] = img_id; image_id_counter += 1
                coco_images.append({"id": img_id, "file_name": stem, "width": im.width, "height": im.height})
            # add annotations
            for r in bands_csv_rows:
                x = r["x_norm"] * im.width
                y = r["y_norm"] * im.height
                w = r["w_norm"] * im.width
                h = r["h_norm"] * im.height
                coco_anns.append({
                    "id": ann_id,
                    "image_id": img_id,
                    "category_id": 1,
                    "bbox": [float(x), float(y), float(w), float(h)],
                    "area": float(w*h),
                    "iscrowd": 0,
                    "attributes": {"lane_index": r["lane_index"], "lane_type": r["lane_type"]}
                })
                ann_id += 1

        # YOLO
        if args.export_yolo:
            export_yolo(args.export_yolo, Path(name).stem, bands_csv_rows)

    count = 0
    if args.tar:
        mode = "r:gz" if str(args.tar).endswith((".tgz",".tar.gz")) else "r:"
        with tarfile.open(args.tar, mode) as tf:
            for m in tf.getmembers():
                if not m.isfile(): continue
                if Path(m.name).suffix.lower() not in args.extensions: continue
                fp = tf.extractfile(m)
                if fp is None: continue
                try: im = load_image_bytes(io.BytesIO(fp.read()))
                except Exception as e:
                    print(f"[warn] could not read {m.name}: {e}", file=sys.stderr); continue
                handle_image(Path(m.name).name, im); count += 1
    else:
        for p in sorted(args.dir.rglob("*")):
            if p.suffix.lower() in args.extensions:
                try: im = load_image_any(p)
                except Exception as e:
                    print(f"[warn] could not read {p}: {e}", file=sys.stderr); continue
                handle_image(p.name, im); count += 1

    f_lanes.close(); f_bands.close(); f_lanes_j.close(); f_bands_j.close()

    if args.export_coco:
        ensure_dir(Path(args.export_coco).parent)
        export_coco(Path(args.export_coco), coco_images, coco_anns)
        print(f"[ok] COCO written: {args.export_coco} (images: {len(coco_images)}, anns: {len(coco_anns)})")

    print(f"[ok] Processed %d images" % count)
    print(f"[ok] Lanes CSV: %s" % lanes_csv_path)
    print(f"[ok] Bands CSV: %s" % bands_csv_path)
    print(f"[ok] Lanes JSONL: %s" % lanes_jsonl_path)
    print(f"[ok] Bands JSONL: %s" % bands_jsonl_path)
    if args.qc: print(f"[ok] QC overlays dir: %s" % qc_dir)
    if args.export_lane_crops: print(f"[ok] Lane crops dir: %s" % crops_lanes_dir)
    if args.export_band_crops: print(f"[ok] Band crops dir: %s" % crops_bands_dir)
    if args.export_yolo: print(f"[ok] YOLO labels dir: %s" % args.export_yolo)

if __name__ == "__main__":
    main()
