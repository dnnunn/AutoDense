# autodense/vision/reporting.py
import json, hashlib
from dataclasses import asdict
from pathlib import Path
from typing import Any, Dict

def file_sha256(p: Path) -> str:
    h=hashlib.sha256()
    with open(p,"rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()

def write_report(image_path: Path, res, observer: Dict[str, Any], params: Dict[str, Any], out_json: Path):
    rec = {
        "image": image_path.name,
        "image_sha256": file_sha256(image_path),
        "image_size": {"w": res.image_size[0], "h": res.image_size[1]},
        "lanes": [{
            "index": ln.index, "type": ln.type, "x0": ln.x0, "x1": ln.x1, "y0": ln.y0, "y1": ln.y1,
            "bands": [{"index": b.index, "y0": b.y0, "y1": b.y1, "intensity": b.intensity, "confidence": b.confidence} for b in ln.bands]
        } for ln in res.lanes],
        "observer": observer,
        "params": params,
        "version": {"schema": "ad.v1", "analyzer": "python-mvp"}
    }
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(rec, indent=2))
    return out_json
