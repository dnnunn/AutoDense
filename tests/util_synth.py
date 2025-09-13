# tests/util_synth.py
import numpy as np
from skimage import transform

def make_synth_gel(H=400, W=320, lanes=10, bands_per_lane=4, skew_deg=2.0, bright=True, seed=0):
    """
    Generate a simple synthetic gel:
      - vertical lanes with narrow width
      - horizontal bands at fixed y positions across lanes
      - smooth background gradient + gaussian noise
      - optional small rotation (skew) in degrees
    Returns a float image in [0,1] and the list of band row indices (approx centers).
    """
    rng = np.random.default_rng(seed)
    img = np.zeros((H, W), dtype=np.float32)

    # background gradient
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    bg = 0.15 + 0.15*(yy/H) + 0.05*(xx/W)
    img += bg

    # lane centers
    margin = W*0.08
    xs = np.linspace(margin, W-margin, lanes).astype(np.float32)
    lane_sigma = max(2.0, W/(lanes*12.0))  # controls lane width

    # band positions (as fractions of height)
    fracs = np.linspace(0.2, 0.8, bands_per_lane)
    ys = (fracs * H).astype(np.float32)
    band_sigma = max(2.0, H/180.0)  # vertical thickness

    amp = 0.35 if bright else -0.35
    for xc in xs:
        lane_prof = np.exp(-(xx - xc)**2 / (2*lane_sigma**2))
        for yc in ys:
            band_prof = np.exp(-(yy - yc)**2 / (2*band_sigma**2))
            img += amp * lane_prof * band_prof

    # clip and rotate
    img = np.clip(img, 0.0, 1.0)
    if abs(skew_deg) > 1e-3:
        img = transform.rotate(img, angle=skew_deg, resize=False, preserve_range=True).astype(np.float32)

    # noise
    img += rng.normal(0, 0.01, img.shape).astype(np.float32)
    img = np.clip(img, 0.0, 1.0)
    return img, ys.astype(int).tolist()
