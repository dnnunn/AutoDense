#!/usr/bin/env python3
"""
Image Harvester — batch reverse-image & query search for lab imagery (SDS-PAGE, EtBr gels, X-gal plates)

Supports:
- Reverse-image search (seed URL) via SerpAPI (Google Lens, Bing Reverse Image)
- Classic text queries via SerpAPI (Google Images, Bing Images)
- Robust download with throttling, de-dup via perceptual hash (pHash)
- Lightweight heuristics for gels/plates
- Optional ML filtering (use your own softmax model with --model + --labels)
- Optional quick trainer: fine-tune MobileNetV2 head on harvested folders
- CSV manifest per class with provenance & basic metadata

Dependencies (pip install):
  requests pillow imagehash tqdm numpy opencv-python tensorflow-cpu

Env:
  SERPAPI_KEY          # required (Google/Bing via SerpAPI)
  BRIGHTDATA_KEY, BRIGHTDATA_ZONE   # optional (Bright Data Bing fallback)
"""
from __future__ import annotations
import argparse, csv, hashlib, io, json, os, re, sys, time
from datetime import datetime
from pathlib import Path
from typing import List, Optional, Tuple

import requests
from PIL import Image, ImageFilter
import imagehash
from tqdm import tqdm
import numpy as np

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

def ensure_dir(p: Path) -> None: p.mkdir(parents=True, exist_ok=True)
def now_iso() -> str: return datetime.utcnow().isoformat(timespec="seconds") + "Z"
def sha1_bytes(b: bytes) -> str: return hashlib.sha1(b).hexdigest()

# ------------------------------ Heuristics ------------------------------

def is_reasonable_size(w: int, h: int, min_side: int = 256) -> bool: return min(w, h) >= min_side

def ratio_ok_for_gel(w: int, h: int) -> bool:
    r = max(w, h) / max(1, min(w, h))
    return 1.2 <= r <= 6.0

def passes_class_heuristics(img: Image.Image, klass: str) -> bool:
    w, h = img.size
    if not is_reasonable_size(w, h): return False
    if "gel" in klass.lower() and not ratio_ok_for_gel(w, h): return False
    return True

# ------------------------------ Classifier ------------------------------

class SoftmaxClassifier:
    def __init__(self, model_path: str, labels: List[str]):
        if not HAS_TF:
            raise RuntimeError("TensorFlow not available; install tensorflow-cpu")
        self.model = tf.keras.models.load_model(model_path)
        self.labels = labels
        self.label_to_idx = {l: i for i, l in enumerate(labels)}
    def prob_of_class(self, img: Image.Image, klass: str, size=(224,224)) -> float:
        arr = img.resize(size)
        arr = keras_image.img_to_array(arr)
        arr = np.expand_dims(arr, axis=0)
        arr = preprocess_input(arr)
        preds = self.model.predict(arr, verbose=0)[0]
        idx = self.label_to_idx.get(klass)
        return float(preds[idx]) if idx is not None else float(np.max(preds))

# ------------------------------ Trainer ------------------------------

def build_model(num_classes:int):
    if not HAS_TF:
        raise RuntimeError("TensorFlow not available")
    base = MobileNetV2(include_top=False, input_shape=(224,224,3), weights='imagenet', pooling='avg')
    base.trainable = False
    x = layers.Input(shape=(224,224,3))
    y = base(x, training=False)
    y = layers.Dense(128, activation='relu')(y)
    y = layers.Dropout(0.2)(y)
    out = layers.Dense(num_classes, activation='softmax')(y)
    model = models.Model(inputs=x, outputs=out)
    model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])
    return model

def train_classifier(train_dir: str, val_dir: str, labels: List[str], epochs: int, model_out: str):
    if not HAS_TF:
        raise RuntimeError("TensorFlow not available")
    img_size=(224,224); batch=32
    train_ds=tf.keras.preprocessing.image_dataset_from_directory(train_dir, labels='inferred', label_mode='int', class_names=labels, image_size=img_size, batch_size=batch, shuffle=True)
    val_ds=tf.keras.preprocessing.image_dataset_from_directory(val_dir, labels='inferred', label_mode='int', class_names=labels, image_size=img_size, batch_size=batch)
    autotune=tf.data.AUTOTUNE
    train_ds=train_ds.prefetch(autotune); val_ds=val_ds.prefetch(autotune)
    model=build_model(len(labels))
    cb=[tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True)]
    model.fit(train_ds, validation_data=val_ds, epochs=epochs, callbacks=cb)
    ensure_dir(Path(model_out).parent)
    model.save(model_out)
    print(f"Saved classifier to {model_out}")

# ------------------------------ Providers ------------------------------

SERP_ENDPOINT = "https://serpapi.com/search.json"
BRIGHTDATA_ENDPOINT = "https://api.brightdata.com/request"

def provider_serpapi_google_lens(url:str, key:str, limit:int, verbose=False)->List[str]:
    r=requests.get(SERP_ENDPOINT, params={"engine":"google_lens","url":url,"api_key":key,"hl":"en"}, timeout=30)
    data=r.json(); urls=[]
    for m in data.get("visual_matches",[])[:limit]:
        u=m.get("link") or m.get("source") or m.get("thumbnail")
        if u: urls.append(u)
    return urls

def provider_serpapi_google_images(q:str, key:str, limit:int, verbose=False)->List[str]:
    r=requests.get(SERP_ENDPOINT, params={"engine":"google_images","q":q,"api_key":key,"hl":"en"}, timeout=30)
    data=r.json(); urls=[]
    for m in data.get("images_results",[])[:limit]:
        u=m.get("original") or m.get("thumbnail")
        if u: urls.append(u)
    return urls

def provider_serpapi_bing_images(q:str, key:str, limit:int, verbose:bool=False)->List[str]:
    r=requests.get(SERP_ENDPOINT, params={"engine":"bing_images","q":q,"api_key":key}, timeout=30)
    data=r.json(); urls=[]
    for m in data.get("images_results",[])[:limit]:
        u=m.get("original") or m.get("thumbnail") or m.get("source")
        if u: urls.append(u)
    return urls

def provider_serpapi_bing_reverse_image(url:str, key:str, limit:int, verbose:bool=False)->List[str]:
    r=requests.get(SERP_ENDPOINT, params={"engine":"bing_reverse_image","image_url":url,"api_key":key}, timeout=30)
    data=r.json(); urls=[]
    results = data.get("image_results",[]) or data.get("visual_search_results",[])
    for m in results[:limit]:
        u=m.get("original") or m.get("thumbnail") or m.get("contentUrl") or m.get("source")
        if u: urls.append(u)
    return urls

def provider_brightdata_bing(q:str, api_key:str, zone:str, limit:int, verbose:bool=False)->List[str]:
    payload={"search_engine":"bing","query":q,"data_format":"parsed_bing_api","format":"json","zone":zone}
    headers={"Authorization":f"Bearer {api_key}","Content-Type":"application/json"}
    r=requests.post("https://api.brightdata.com/request", headers=headers, json=payload, timeout=60)
    data=r.json(); urls=[]
    items=(data.get("response",{}) or {}).get("organic",[]) or []
    for it in items:
        imgs=it.get("images") or []
        if imgs:
            for im in imgs:
                u=im.get("url") or im.get("thumbnail_url")
                if u: urls.append(u)
        else:
            u=it.get("url");
            if u: urls.append(u)
        if len(urls)>=limit: break
    return urls

# ------------------------------ Download ------------------------------

def download_image(url:str, session:requests.Session, timeout:int=30)->Optional[bytes]:
    try:
        r=session.get(url, timeout=timeout, headers={"User-Agent":"Mozilla/5.0"});
        if r.status_code!=200: return None
        if "image" not in r.headers.get("Content-Type","" ).lower(): return None
        return r.content
    except Exception: return None

def open_image_safely(b:bytes)->Optional[Image.Image]:
    try: return Image.open(io.BytesIO(b)).convert("RGB")
    except Exception: return None

# ------------------------------ Main ------------------------------

def run(args):
    if args.train:
        if not args.model or not args.labels or not args.train_data or not args.val_data:
            raise SystemExit("--train requires --model, --labels, --train-data, --val-data")
        labels=[s.strip() for s in args.labels.split(',') if s.strip()]
        train_classifier(args.train_data,args.val_data,labels,args.epochs,args.model)
        return

    out_root=Path(args.out).resolve(); klass=slugify(args.klass); out_dir=out_root/klass; ensure_dir(out_dir)
    manifest_path=out_root/f"manifest_{klass}.csv"
    fieldnames=["saved_path","class","source_provider","source_url","fetched_at","width","height","sha1","phash","seed_type","seed_value","notes","ml_label","ml_prob"]
    existing_phashes=set()
    if manifest_path.exists():
        with open(manifest_path,newline="",encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row.get("phash"): existing_phashes.add(row["phash"])

    clf=None; labels=None
    if args.predict and args.model and args.labels:
        labels=[s.strip() for s in args.labels.split(',') if s.strip()]
        clf=SoftmaxClassifier(args.model,labels)

    cand_urls=[]; serp_key=os.getenv("SERPAPI_KEY",""); bright_key=os.getenv("BRIGHTDATA_KEY",""); bright_zone=os.getenv("BRIGHTDATA_ZONE","")
    if args.query and serp_key:
        urls=provider_serpapi_google_images(args.query,serp_key,args.limit,args.verbose)
        cand_urls.extend(("serpapi_google_images",u) for u in urls)
        urls=provider_serpapi_bing_images(args.query,serp_key,args.limit,args.verbose)
        cand_urls.extend(("serpapi_bing_images",u) for u in urls)
        if "brightdata" in args.providers and bright_key and bright_zone:
            urls=provider_brightdata_bing(args.query,bright_key,bright_zone,args.limit,args.verbose)
            cand_urls.extend(("brightdata_bing",u) for u in urls)
    if args.seed_url and serp_key:
        urls=provider_serpapi_google_lens(args.seed_url,serp_key,args.limit,args.verbose)
        cand_urls.extend(("serpapi_google_lens",u) for u in urls)
        urls=provider_serpapi_bing_reverse_image(args.seed_url,serp_key,args.limit,args.verbose)
        cand_urls.extend(("serpapi_bing_reverse",u) for u in urls)

    seen=set(); dedup=[]
    for prov,u in cand_urls:
        if u not in seen:
            seen.add(u); dedup.append((prov,u))

    session=requests.Session()
    with open(manifest_path,"a",newline="",encoding="utf-8") as mf:
        writer=csv.DictWriter(mf,fieldnames=fieldnames)
        if mf.tell()==0: writer.writeheader()
        saved=0
        for prov,url in tqdm(dedup[:args.limit],desc="Downloading"):
            time.sleep(args.throttle)
            b=download_image(url,session=session)
            if not b: continue
            sha1=sha1_bytes(b); img=open_image_safely(b)
            if not img: continue
            if not passes_class_heuristics(img,klass): continue

            ml_label=""; ml_prob=""
            if args.predict and clf:
                p=clf.prob_of_class(img,klass)
                if p<args.class_threshold: continue
                ml_label,ml_prob=klass,f"{p:.3f}"

            ph=str(imagehash.phash(img))
            if ph in existing_phashes: continue
            name=f"{klass}_{sha1[:12]}_{ph}.jpg"; save_path=out_dir/name
            try: img.save(save_path,format="JPEG",quality=92)
            except Exception: continue
            existing_phashes.add(ph)
            row={"saved_path":str(save_path),"class":klass,"source_provider":prov,"source_url":url,"fetched_at":now_iso(),"width":img.size[0],"height":img.size[1],"sha1":sha1,"phash":ph,"seed_type":"url" if args.seed_url else ("query" if args.query else ""),"seed_value":args.seed_url or args.query or "","notes":"","ml_label":ml_label,"ml_prob":ml_prob}
            writer.writerow(row); saved+=1
    print(f"Saved {saved} images to {out_dir}\nManifest at {manifest_path}")

# ------------------------------ CLI ------------------------------

def build_parser():
    p=argparse.ArgumentParser(description="Batch image harvester with optional ML filtering & training")
    p.add_argument("--class",dest="klass",required=False,help="Class folder")
    p.add_argument("--out",default="./harvest")
    p.add_argument("--providers",default="serpapi")
    p.add_argument("--seed-url")
    p.add_argument("--query")
    p.add_argument("--limit",type=int,default=100)
    p.add_argument("--throttle",type=float,default=0.8)
    p.add_argument("--verbose",action="store_true")
    p.add_argument("--predict",action="store_true")
    p.add_argument("--model")
    p.add_argument("--labels")
    p.add_argument("--class-threshold",type=float,default=0.5)
    # training
    p.add_argument("--train",action="store_true")
    p.add_argument("--train-data")
    p.add_argument("--val-data")
    p.add_argument("--epochs",type=int,default=8)
    return p

if __name__=="__main__":
    args=build_parser().parse_args()
    args.providers=[s.strip().lower() for s in args.providers.split(",") if s.strip()]
    run(args)
