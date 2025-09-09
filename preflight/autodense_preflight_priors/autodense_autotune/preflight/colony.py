from __future__ import annotations
from typing import Dict, Any, Optional
from .common import load_image_gray, base_defaults_for_img, merge_with_priors

def preflight_colony(input_path: str, priors: Optional[Dict[str,Any]]=None) -> Dict[str,Any]:
    img = load_image_gray(input_path)
    defaults = base_defaults_for_img(img)
    defaults["pre"]["clahe"]["enabled"] = True
    defaults["segmentation"] = {"method":"phansalkar","min_area": 30}
    defaults["post"] = {"merge_distance_px": 5}
    return merge_with_priors(defaults, priors)
