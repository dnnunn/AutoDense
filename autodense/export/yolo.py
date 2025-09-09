# autodense/export/yolo.py
from pathlib import Path

def export_yolo_txt(res, out_dir: Path, image_w: int=None, image_h: int=None):
    out_dir.mkdir(parents=True, exist_ok=True)
    W, H = res.image_size if (image_w is None or image_h is None) else (image_w, image_h)
    for ln in res.lanes:
        for b in ln.bands:
            x0, x1 = ln.x0, ln.x1
            y0, y1 = b.y0, b.y1
            cx = (x0 + x1) / 2.0; cy = (y0 + y1) / 2.0
            w = max(1, x1-x0); h = max(1, y1-y0)
            line = "0 {:.6f} {:.6f} {:.6f} {:.6f}\n".format(cx/W, cy/H, w/W, h/H)
            (out_dir / f"bands_{ln.index:02d}.txt").open("a").write(line)
    return out_dir
