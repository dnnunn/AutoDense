from __future__ import annotations
from typing import Dict, Any, Optional
from .common import load_image_gray, base_defaults_for_img, merge_with_priors

def preflight_etbr(input_path: str, priors: Optional[Dict[str,Any]]=None) -> Dict[str,Any]:
    img = load_image_gray(input_path)
    defaults = base_defaults_for_img(img)
    defaults["detect"]["prominence_frac"] = max(0.04, defaults["detect"]["prominence_frac"])
    return merge_with_priors(defaults, priors)
