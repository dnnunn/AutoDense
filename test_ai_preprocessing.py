#!/usr/bin/env python3
"""
Test AI-guided preprocessing workflow from beginning to end
"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image

# Add autodense to path
sys.path.insert(0, str(Path(__file__).parent))

from tests.util_synth import make_synth_gel
from autodense.preprocess.policy import guarded_preprocess

def test_synthetic_gel():
    """Test AI-guided preprocessing on synthetic gel"""
    print("=== Testing AI-guided preprocessing on synthetic gel ===")
    
    # Create synthetic gel with some skew
    synth_img, band_rows = make_synth_gel(H=400, W=320, lanes=8, bands_per_lane=5, 
                                         skew_deg=3.0, bright=False, seed=42)
    
    # Convert to PIL Image (RGB format expected by policy)
    synth_pil = Image.fromarray((synth_img * 255).astype(np.uint8)).convert('RGB')
    
    print(f"Synthetic gel: {synth_pil.size}, skew=3.0°, dark bands")
    
    # Run AI-guided preprocessing
    outcome = guarded_preprocess(synth_pil)
    
    print(f"Policy decision: {outcome.mode}")
    print(f"SNR change: {outcome.after['snr'] - outcome.before['snr']:.3f}")
    print(f"Separability change: {outcome.after['sep'] - outcome.before['sep']:.3f}")
    print(f"Params used: {outcome.params}")
    
    # Save results
    out_dir = Path("out/test_ai_policy")
    out_dir.mkdir(parents=True, exist_ok=True)
    
    synth_pil.save(out_dir / "synthetic_original.png")
    Image.fromarray((np.clip(outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "synthetic_processed.png")
    
    print(f"Results saved to {out_dir}/")
    return outcome

def test_real_gel():
    """Test AI-guided preprocessing on real gel image"""
    print("\n=== Testing AI-guided preprocessing on real SDS-PAGE gel ===")
    
    gel_path = Path("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/SeedImages/SDS-PAGE/20250130_115229.jpg")
    
    if not gel_path.exists():
        print(f"Real gel image not found at {gel_path}")
        return None
    
    # Load real gel
    real_img = Image.open(gel_path).convert('RGB')
    print(f"Real gel: {real_img.size}")
    
    # Run AI-guided preprocessing
    outcome = guarded_preprocess(real_img)
    
    print(f"Policy decision: {outcome.mode}")
    print(f"SNR change: {outcome.after['snr'] - outcome.before['snr']:.3f}")
    print(f"Separability change: {outcome.after['sep'] - outcome.before['sep']:.3f}")
    print(f"Skew detected: {outcome.before.get('skew_deg', 'N/A'):.2f}°" if outcome.before.get('skew_deg') else "Skew not measured")
    print(f"Params used: {outcome.params}")
    
    # Save results
    out_dir = Path("out/test_ai_policy")
    out_dir.mkdir(parents=True, exist_ok=True)
    
    real_img.save(out_dir / "real_gel_original.jpg")
    Image.fromarray((np.clip(outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "real_gel_processed.png")
    
    print(f"Results saved to {out_dir}/")
    return outcome

def compare_modes():
    """Compare all three preprocessing modes on real gel"""
    print("\n=== Comparing preprocessing modes ===")
    
    gel_path = Path("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/SeedImages/SDS-PAGE/20250130_115229.jpg")
    if not gel_path.exists():
        print("Real gel not found, skipping comparison")
        return
    
    real_img = Image.open(gel_path).convert('RGB')
    out_dir = Path("out/test_ai_policy/comparison")
    out_dir.mkdir(parents=True, exist_ok=True)
    
    # Mode 1: AI guarded
    outcome_ai = guarded_preprocess(real_img)
    Image.fromarray((np.clip(outcome_ai.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "mode_ai_guarded.png")
    
    # Mode 3: Off (raw grayscale normalized)
    arr = np.asarray(real_img.convert("L")).astype(np.float32)
    raw_processed = (arr - arr.min()) / (arr.max() - arr.min() + 1e-6)
    Image.fromarray((raw_processed * 255).astype(np.uint8)).save(out_dir / "mode_off.png")
    
    print(f"AI guarded: {outcome_ai.mode} (ΔSNR={outcome_ai.after['snr']-outcome_ai.before['snr']:.2f})")
    print(f"Off mode: raw grayscale normalization")
    print(f"Comparison images saved to {out_dir}/")

if __name__ == "__main__":
    try:
        # Test synthetic gel first
        synth_outcome = test_synthetic_gel()
        
        # Test real gel
        real_outcome = test_real_gel()
        
        # Compare modes
        compare_modes()
        
        print("\n=== AI-guided preprocessing test complete ===")
        
    except Exception as e:
        print(f"Error during testing: {e}")
        import traceback
        traceback.print_exc()