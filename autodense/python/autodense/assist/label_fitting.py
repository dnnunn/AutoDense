# autodense/assist/label_fitting.py
"""
Auto-fit font size per lane and nudge labels to avoid collisions; draws a white halo for readability.
"""
from typing import List, Tuple
from PIL import Image, ImageDraw, ImageFont

def _font(size: int):
    """Get font with fallback to default"""
    try:
        return ImageFont.truetype("DejaVuSans.ttf", size=size)
    except Exception:
        try:
            # Try other common system fonts
            return ImageFont.truetype("/System/Library/Fonts/Arial.ttf", size=size)
        except Exception:
            return ImageFont.load_default()

def _auto_size(draw: ImageDraw.ImageDraw, text: str, max_w: int, max_h: int, lo=8, hi=64) -> int:
    """Auto-fit font size to constraints"""
    s = hi
    while s >= lo:
        f = _font(s)
        try:
            w = int(draw.textlength(text, font=f))
        except AttributeError:
            # Fallback for older Pillow versions
            w = int(draw.textsize(text, font=f)[0])
        h = int(getattr(f, 'size', 12)) + 2
        if w <= max_w and h <= max_h:
            return s
        s -= 1
    return lo

def place_labels(image: Image.Image, lanes: List[Tuple[float, float, float, float]], 
                labels: List[str], y_pad: int = 4, avoid_overlap: bool = True) -> Image.Image:
    """
    Place lane labels with auto-fit font sizing and collision avoidance.
    
    Args:
        image: PIL Image to label
        lanes: List of (x0, y0, x1, y1) lane coordinates 
        labels: List of label strings
        y_pad: Vertical padding above lanes
        avoid_overlap: Whether to nudge labels to avoid overlaps
        
    Returns:
        New PIL Image with labels drawn
    """
    im = image.copy()
    draw = ImageDraw.Draw(im, "RGBA")
    used = []  # Track used vertical spans for overlap avoidance
    
    for i, (x0, y0, x1, y1) in enumerate(lanes):
        text = labels[i] if i < len(labels) else f"Lane{i}"
        if not text.strip():
            text = f"Lane{i}"
            
        # Calculate available space for text
        box_w = max(8, int(x1 - x0) - 4)
        box_h = max(12, int(0.08 * (y1 - y0)))
        
        # Auto-fit font size
        size = _auto_size(draw, text, box_w, box_h)
        f = _font(size)
        
        # Get text dimensions
        try:
            tw = int(draw.textlength(text, font=f))
        except AttributeError:
            # Fallback for older Pillow versions
            tw = int(draw.textsize(text, font=f)[0])
        th = int(getattr(f, 'size', size)) + 2
        
        # Center text horizontally in lane
        tx = int(x0 + (x1 - x0 - tw) // 2)
        
        # Position text above lane with padding
        ty = max(0, int(y0 - th - y_pad))
        
        # Avoid overlaps by nudging vertically
        if avoid_overlap:
            span = [ty, ty + th]
            tries = 0
            while any(not (span[1] < s0 or span[0] > s1) for (s0, s1) in used) and tries < 10:
                ty = max(0, ty - int(th * 0.6))
                span = [ty, ty + th]
                tries += 1
            used.append((span[0], span[1]))
        
        # Draw white halo for readability
        halo = 2
        draw.rectangle(
            [tx - halo, ty - halo, tx + tw + halo, ty + th + halo], 
            fill=(255, 255, 255, 140)
        )
        
        # Draw the text
        draw.text((tx, ty), text, fill=(0, 0, 0, 255), font=f)
    
    return im