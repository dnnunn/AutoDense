#!/usr/bin/env python3
"""
Image Harvester — batch reverse-image & query search for lab imagery (SDS-PAGE, EtBr gels, X-gal plates)

This single-file script is **complete** and ready to run. It supports:
- Reverse-image search (seed URL or local file) via **SerpAPI (Google Lens)** and **Bing Visual Search**
- Classic text queries via **SerpAPI (Google Images)**
- Robust download with throttling, de-dup via perceptual hash (pHash)
- Lightweight **heuristics** for gels/plates
- Optional **ML filtering**:
    - Default: ImageNet MobileNetV2 stub (coarse filter)
    - Custom: load your own Keras/TF softmax model (`--model` + `--labels`)
- CSV manifest per class with provenance & basic metadata

Dependencies (pip install):
  requests
  pillow
  imagehash
  tqdm
  opencv-python
  numpy
  tensorflow-cpu   # or tensorflow (GPU) if you want ML filtering

Environment variables:
  SERPAPI_KEY          # for Google Lens / Google Images via SerpAPI
  BING_VISION_KEY      # for Bing Visual Search
  BING_VISION_ENDPOINT # optional, default below

Examples
1) Reverse-image (URL seed) using both providers, save as class "sds_page":
   python image_harvester.py \
     --class sds_page \
     --seed-url https://example.org/my_seed_gel.jpg \
     --providers serpapi,bing \
     --limit 150 \
     --out ./harvest

2) Text query (bootstrap a class):
   python image_harvester.py \
     --class etbr_gel \
     --query "EtBr agarose gel" \
     --providers serpapi \
     --limit 120 \
     --out ./harvest

3) Local file as seed (Bing upload path):
   python image_harvester.py \
     --class xgal_plate \
     --seed-file ./seed_plate.jpg \
     --providers bing \
     --limit 200 \
     --out ./harvest

4) On-the-fly ML filtering with a **custom** model & labels (keeps only images with p>=0.6 for the target class):
   python image_harvester.py \
     --class sds_page \
     --seed-url https://example.org/my_seed_gel.jpg \
     --providers serpapi,bing \
     --predict --model ./models/gel_plate_mobilenet.h5 \
     --labels sds_page,etbr_gel,xgal_plate \
     --class-threshold 0.6 \
     --limit 200 \
     --out ./harvest
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import io
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import List, Optional, Tuple

import requests
from PIL import Image, ImageFilter
import imagehash
from tqdm import tqdm
import numpy as np

# Optional ML
try:
    import tensorflow as tf
    from tensorflow.keras.applications import MobileNetV2
    from tensorflow.keras.applications.mobilenet_v2 import preprocess_input, decode_predictions
    from tensorflow.keras.preprocessing import image as keras_image
    from tensorflow.keras import layers, models
    HAS_TF = True
except Exception:
    HAS_TF = False

# ------------------------------ Utils ------------------------------

def slugify(s: str) -> str:
    s = re.sub(r"[^a-zA-Z0-9-_]+", "_", s.strip())
    s = re.sub(r"_+", "_", s)
    return s.strip("_").lower()[:120]


def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def now_iso() -> str:
    return datetime.utcnow().isoformat(timespec="seconds") + "Z"


def sha1_bytes(b: bytes) -> str:
    return hashlib.sha1(b).hexdigest()

# ------------------------------ Heuristics ------------------------------

def is_reasonable_size(w: int, h: int, min_side: int = 256) -> bool:
    return min(w, h) >= min_side


def ratio_ok_for_gel(w: int, h: int) -> bool:
    # Gels are often tall-ish rectangles; allow a generous range.
    r = max(w, h) / max(1, min(w, h))
    return 1.2 <= r <= 6.0


def looks_circular_plate_pil(img: Image.Image) -> bool:
    # Crude edge density heuristic to hint at circular plates; lenient and optional.
    e = img.convert("L").filter(ImageFilter.FIND_EDGES)
    w, h = e.size
    edge_ratio = sum(1 for px in e.getdata() if px > 32) / float(w * h)
    return 0.005 < edge_ratio < 0.08


def passes_class_heuristics(img: Image.Image, klass: str) -> bool:
    w, h = img.size
    if not is_reasonable_size(w, h):
        return False
    k = klass.lower()
    if "gel" in k and not ratio_ok_for_gel(w, h):
        return False
    if "plate" in k:
        # keep lenient; false negatives hurt dataset growth
        return True
    return True

# ------------------------------ Classifier (optional) ------------------------------

class SoftmaxClassifier:
    """Custom softmax model loader (Keras .h5 with final softmax) and label map.
    Expects outputs aligned with `labels` order. Use with --model and --labels.
    """
    def __init__(self, model_path: str, labels: List[str]):
        if not HAS_TF:
            raise RuntimeError("TensorFlow not available; install tensorflow-cpu to use --model/--predict")
        self.model = tf.keras.models.load_model(model_path)
        self.labels = labels
        self.label_to_idx = {l: i for i, l in enumerate(labels)}

    def prob_of_class(self, img: Image.Image, klass: str, target_size: Tuple[int,int]=(224,224)) -> float:
        arr = img.resize(target_size)
        arr = keras_image.img_to_array(arr)
        arr = np.expand_dims(arr, axis=0)
        arr = preprocess_input(arr)
        preds = self.model.predict(arr, verbose=0)[0]
        idx = self.label_to_idx.get(klass, None)
        if idx is None:
            return float(np.max(preds))
        return float(preds[idx])

# Default ImageNet stub (very coarse)
_imagenet_model = None

def imagenet_top1_conf(img: Image.Image, target_size: Tuple[int,int]=(224,224)) -> Tuple[str, float]:
    global _imagenet_model
    if not HAS_TF:
        return ("", 0.0)
    if _imagenet_model is None:
        _imagenet_model = MobileNetV2(weights="imagenet")
    arr = img.resize(target_size)
    arr = keras_image.img_to_array(arr)
    arr = preprocess_input(arr[None, ...])
    preds = _imagenet_model.predict(arr, verbose=0)
    top = decode_predictions(preds, top=1)[0][0]  # (class_id, label, prob)
    return (top[1], float(top[2]))

# ------------------------------ Providers ------------------------------

SERP_ENDPOINT = "https://serpapi.com/search.json"
BING_ENDPOINT_DEFAULT = "https://api.bing.microsoft.com/v7.0/images/visualsearch"


def provider_serpapi_google_lens(seed_image_url: str, api_key: str, limit: int, verbose: bool=False) -> List[str]:
    """Reverse-image search via SerpAPI Google Lens. Returns candidate image URLs."""
    params = {"engine": "google_lens", "url": seed_image_url, "api_key": api_key, "hl": "en"}
    r = requests.get(SERP_ENDPOINT, params=params, timeout=30)
    if verbose:
        print("[serpapi-lens] status", r.status_code, file=sys.stderr)
    r.raise_for_status()
    data = r.json()
    matches = data.get("visual_matches", []) or []
    urls: List[str] = []
    for m in matches:
        u = m.get("link") or m.get("source") or m.get("thumbnail")
        if u:
            urls.append(u)
        if len(urls) >= limit:
            break
    if verbose:
        print(f"[serpapi-lens] got {len(urls)} urls", file=sys.stderr)
    return urls


def provider_serpapi_query(query: str, api_key: str, limit: int, verbose: bool=False) -> List[str]:
    """Google Images query via SerpAPI: parse images_results[].original."""
    params = {"engine": "google_images", "q": query, "api_key": api_key, "hl": "en"}
    r = requests.get(SERP_ENDPOINT, params=params, timeout=30)
    if verbose:
        print("[serpapi-q] status", r.status_code, file=sys.stderr)
    r.raise_for_status()
    data = r.json()
    results = data.get("images_results", []) or []
    urls: List[str] = []
    for m in results:
        u = m.get("original") or m.get("thumbnail") or m.get("source")
        if u:
            urls.append(u)
        if len(urls) >= limit:
            break
    if verbose:
        print(f"[serpapi-q] got {len(urls)} urls", file=sys.stderr)
    return urls


def provider_bing_visualsearch(seed_image_url: Optional[str], seed_file: Optional[Path],
                               key: str, endpoint: str, limit: int, verbose: bool=False) -> List[str]:
    """Bing Visual Search SimilarImages. Upload local file if provided; else reference URL."""
    headers = {"Ocp-Apim-Subscription-Key": key}
    if seed_file and seed_file.exists():
        with open(seed_file, "rb") as f:
            files = {"image": (seed_file.name, f, "application/octet-stream")}
            r = requests.post(endpoint, headers=headers, files=files, timeout=60)
    else:
        kr = {"imageInfo": {"url": seed_image_url}, "knowledgeRequest": {"invokedSkills": ["SimilarImages"]}}
        data = {"knowledgeRequest": json.dumps(kr)}
        r = requests.post(endpoint, headers=headers, data=data, timeout=60)

    if verbose:
        print("[bing] status", r.status_code, file=sys.stderr)
    r.raise_for_status()
    j = r.json()
    urls: List[str] = []
    for tag in j.get("tags", []) or []:
        for action in tag.get("actions", []) or []:
            if action.get("actionType") in ("VisualSearch", "SimilarImages"):
                for v in action.get("data", {}).get("value", []) or []:
                    u = v.get("contentUrl") or v.get("hostPageUrl")
                    if u:
                        urls.append(u)
                        if len(urls) >= limit:
                            return urls
    return urls

# ------------------------------ Download & Dedup ------------------------------

def download_image(url: str, session: requests.Session, timeout: int=30) -> Optional[bytes]:
    try:
        resp = session.get(url, timeout=timeout, headers={"User-Agent": "Mozilla/5.0 (collector)"})
        if resp.status_code != 200:
            return None
        ct = resp.headers.get("Content-Type", "").lower()
        if ("image" not in ct) and (not resp.content or len(resp.content) < 2000):
            return None
        return resp.content
    except Exception:
        return None


def open_image_safely(b: bytes) -> Optional[Image.Image]:
    try:
        return Image.open(io.BytesIO(b)).convert("RGB")
    except Exception:
        return None

# ------------------------------ Main Runner ------------------------------

def run(args: argparse.Namespace) -> None:
    out_root = Path(args.out).resolve()
    klass = slugify(args.klass)
    out_dir = out_root / klass
    ensure_dir(out_dir)

    manifest_path = out_root / f"manifest_{klass}.csv"
    fieldnames = [
        "saved_path", "class", "source_provider", "source_url", "fetched_at",
        "width", "height", "sha1", "phash", "seed_type", "seed_value", "notes",
        "ml_label", "ml_prob",
    ]

    existing_phashes = set()
    if manifest_path.exists():
        with open(manifest_path, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row.get("phash"):
                    existing_phashes.add(row["phash"])

    # Optional custom classifier
    clf = None
    labels = None
    if args.predict and args.model and args.labels:
        labels = [s.strip() for s in args.labels.split(',') if s.strip()]
        clf = SoftmaxClassifier(args.model, labels)

    # Collect candidate URLs
    cand_urls: List[Tuple[str, str]] = []
    serp_key = os.getenv("SERPAPI_KEY", "")
    bing_key = os.getenv("BING_VISION_KEY", "")
    bing_endpoint = os.getenv("BING_VISION_ENDPOINT", BING_ENDPOINT_DEFAULT)

    if args.query:
        if not serp_key:
            print("ERROR: SERPAPI_KEY not set for --query", file=sys.stderr)
        else:
            urls = provider_serpapi_query(args.query, serp_key, args.limit, args.verbose)
            cand_urls.extend(("serpapi_query", u) for u in urls)

    if args.seed_url or args.seed_file:
        if "serpapi" in args.providers and args.seed_url:
            if not serp_key:
                print("WARNING: SERPAPI_KEY not set; skipping SerpAPI reverse-image", file=sys.stderr)
            else:
                urls = provider_serpapi_google_lens(args.seed_url, serp_key, args.limit, args.verbose)
                cand_urls.extend(("serpapi_lens", u) for u in urls)
        if "bing" in args.providers:
            if not bing_key:
                print("WARNING: BING_VISION_KEY not set; skipping Bing Visual Search", file=sys.stderr)
            else:
                urls = provider_bing_visualsearch(args.seed_url, Path(args.seed_file) if args.seed_file else None,
                                                  bing_key, bing_endpoint, args.limit, args.verbose)
                cand_urls.extend(("bing_visual", u) for u in urls)

    # De-duplicate URLs while preserving order
    seen = set()
    dedup_urls: List[Tuple[str, str]] = []
    for prov, u in cand_urls:
        if u not in seen:
            seen.add(u)
            dedup_urls.append((prov, u))

    if args.verbose:
        print(f"Collected {len(dedup_urls)} unique candidate URLs", file=sys.stderr)

    session = requests.Session()

    with open(manifest_path, "a", newline="", encoding="utf-8") as mf:
        writer = csv.DictWriter(mf, fieldnames=fieldnames)
        if mf.tell() == 0:
            writer.writeheader()

        saved = 0
        for prov, url in tqdm(dedup_urls[: args.limit], desc="Downloading"):
            time.sleep(args.throttle)
            b = download_image(url, session=session, timeout=30)
            if not b:
                continue
            sha1 = sha1_bytes(b)
            img = open_image_safely(b)
            if not img:
                continue

            # Heuristic gating first
            if not passes_class_heuristics(img, klass):
                continue

            ml_label = ""
            ml_prob = ""
            if args.predict:
                if clf is not None:
                    p = clf.prob_of_class(img, klass)
                    if p < args.class_threshold:
                        continue
                    ml_label, ml_prob = klass, f"{p:.3f}"
                else:
                    # Fallback to ImageNet stub for a coarse confidence (not class-specific)
                    label, conf = imagenet_top1_conf(img)
                    if conf < args.class_threshold:
                        continue
                    ml_label, ml_prob = label, f"{conf:.3f}"

            ph = str(imagehash.phash(img))
            if ph in existing_phashes:
                continue

            name = f"{klass}_{sha1[:12]}_{ph}.jpg"
            save_path = out_dir / name
            try:
                img.save(save_path, format="JPEG", quality=92)
            except Exception:
                continue

            existing_phashes.add(ph)
            row = {
                "saved_path": str(save_path),
                "class": klass,
                "source_provider": prov,
                "source_url": url,
                "fetched_at": now_iso(),
                "width": img.size[0],
                "height": img.size[1],
                "sha1": sha1,
                "phash": ph,
                "seed_type": ("url" if args.seed_url else ("file" if args.seed_file else ("query" if args.query else ""))),
                "seed_value": args.seed_url or args.seed_file or args.query or "",
                "notes": "",
                "ml_label": ml_label,
                "ml_prob": ml_prob,
            }
            writer.writerow(row)
            saved += 1

    print(f"Saved {saved} images to {out_dir}")
    print(f"Manifest at {manifest_path}")

# ------------------------------ CLI ------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Batch image harvester with optional ML filtering")
    # Harvest
    p.add_argument("--class", dest="klass", required=True, help="Output class folder: sds_page | etbr_gel | xgal_plate")
    p.add_argument("--out", default="./harvest", help="Output root directory")
    p.add_argument("--providers", default="serpapi", help="Comma-separated: serpapi,bing")
    p.add_argument("--seed-url", dest="seed_url", help="Seed image URL for reverse-image search")
    p.add_argument("--seed-file", dest="seed_file", help="Seed image local file path (Bing upload path)")
    p.add_argument("--query", help="Text query (SerpAPI Google Images)")
    p.add_argument("--limit", type=int, default=100, help="Max images to fetch/save")
    p.add_argument("--throttle", type=float, default=0.8, help="Seconds to sleep between downloads")
    p.add_argument("--verbose", action="store_true", help="Print provider responses / counts")

    # ML filtering
    p.add_argument("--predict", action="store_true", help="Enable ML filtering during harvest")
    p.add_argument("--model", help="Path to custom Keras .h5 model with softmax output")
    p.add_argument("--labels", help="Comma-separated class labels in the model's output order")
    p.add_argument("--class-threshold", type=float, default=0.5, help="Probability threshold for keeping an image")

    return p

if __name__ == "__main__":
    args = build_parser().parse_args()
    args.providers = [s.strip().lower() for s in args.providers.split(",") if s.strip()]
    run(args)
