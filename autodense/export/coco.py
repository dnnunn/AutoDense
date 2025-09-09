# autodense/export/coco.py
import json
from pathlib import Path

def export_coco(res, image_filename: str, out_json: Path, category_map=None):
    category_map = category_map or {"band": 1}
    img_w, img_h = res.image_size
    images = [{"id":1,"file_name":image_filename,"width":img_w,"height":img_h}]
    categories = [{"id":1,"name":"band"}]
    anns = []
    ann_id = 1
    for ln in res.lanes:
        for b in ln.bands:
            x0, x1 = ln.x0, ln.x1
            y0, y1 = b.y0, b.y1
            w = max(1, x1-x0); h = max(1, y1-y0)
            anns.append({
                "id": ann_id,
                "image_id": 1,
                "category_id": 1,
                "bbox": [int(x0), int(y0), int(w), int(h)],
                "area": int(w*h),
                "iscrowd": 0,
                "attributes": {"lane_index": ln.index, "confidence": b.confidence}
            })
            ann_id += 1
    out = {"images": images, "annotations": anns, "categories": categories}
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(out, indent=2))
    return out_json
