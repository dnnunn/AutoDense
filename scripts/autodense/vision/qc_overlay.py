# autodense/vision/qc_overlay.py
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

def draw_qc(im: Image.Image, res, show_conf: bool=True, show_types: bool=True, title: str=""):
    draw = ImageDraw.Draw(im, "RGBA")
    # Title
    if title:
        draw.rectangle([0,0, im.width, 28], fill=(255,255,255,200))
        draw.text((8,6), title, fill=(0,0,0,255))
    # Lanes + bands
    for ln in res.lanes:
        color = (0,0,255,80) if ln.type=="marker" else (0,255,0,60)
        draw.rectangle([ln.x0, ln.y0, ln.x1, ln.y1], outline=(0,0,0,200), width=2, fill=color)
        tag = f"{'M' if ln.type=='marker' else 'L'}{ln.index}"
        draw.text((ln.x0+4, ln.y0+4), tag, fill=(0,0,0,220))
        for b in ln.bands:
            draw.rectangle([ln.x0, b.y0, ln.x1, b.y1], outline=(255,0,0,220), width=2)
            if show_conf:
                draw.text((ln.x1+3, b.y0), f"{b.confidence:.2f}", fill=(180,0,0,255))
    return im

def save_qc(im: Image.Image, res, out_path: Path, title: str=""):
    im2 = im.copy()
    im2 = draw_qc(im2, res, title=title)
    im2.save(out_path)
    return out_path
