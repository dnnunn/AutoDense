#!/usr/bin/env python3
"""Simple test of AI-guided preprocessing on real gel"""
from PIL import Image
from pathlib import Path
from autodense.preprocess.policy import guarded_preprocess

# Load real gel
gel_path = Path("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/SeedImages/SDS-PAGE/20250130_115229.jpg")
print(f"Loading real gel from: {gel_path}")

if gel_path.exists():
    real_img = Image.open(gel_path).convert('RGB')
    print(f"Image loaded: {real_img.size}")
    
    print("Running AI-guided preprocessing...")
    outcome = guarded_preprocess(real_img)
    
    print(f"✅ Policy decision: {outcome.mode}")
    print(f"✅ SNR change: {outcome.after['snr'] - outcome.before['snr']:.3f}")
    print(f"✅ Separability change: {outcome.after['sep'] - outcome.before['sep']:.3f}")
    print(f"✅ Params: {outcome.params}")
    
    # Save result
    out_dir = Path("out/simple_test")
    out_dir.mkdir(parents=True, exist_ok=True)
    import numpy as np
    Image.fromarray((np.clip(outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "processed.png")
    print(f"✅ Processed image saved to {out_dir}/processed.png")
else:
    print(f"❌ Gel image not found at {gel_path}")