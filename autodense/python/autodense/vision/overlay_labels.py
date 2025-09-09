# autodense/vision/overlay_labels.py
from PIL import Image, ImageDraw, ImageFont

def _text_bg(draw, xy, text, pad=2):
    x, y = xy
    try:
        font = ImageFont.load_default()
        tw, th = draw.textlength(text, font=font), 11
    except Exception:
        font = None
        tw, th = (6 * len(text)), 12
    box = [x - pad, y - pad, x + tw + pad, y + th + pad]
    draw.rectangle(box, fill=(255,255,255,200))
    draw.text((x, y), text, fill=(0,0,0,255), font=font)

def draw_labels(im, res, modality: str, conf_min: float=0.0):
    im2 = im.copy()
    draw = ImageDraw.Draw(im2, "RGBA")
    for ln in res.lanes:
        for b in ln.bands:
            if getattr(b, "confidence", 1.0) < conf_min:
                continue
            yc = int((b.y0 + b.y1) / 2)
            x_label = ln.x1 + 6
            label = None
            if modality.lower() == "sds" and getattr(b, "mw_kda", None) is not None:
                label = f"{b.mw_kda:.0f} kDa"
            if modality.lower() == "dna" and getattr(b, "bp", None) is not None:
                try:
                    label = f"{int(round(b.bp))} bp"
                except Exception:
                    label = f"{float(b.bp):.0f} bp"
            if label:
                _text_bg(draw, (x_label, yc - 6), label, pad=2)
    return im2
