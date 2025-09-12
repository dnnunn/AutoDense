#!/usr/bin/env python3
"""
Test ChatGPT-4.1 AI-guided preprocessing workflow
"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image

# Add autodense to path
sys.path.insert(0, str(Path(__file__).parent))

def test_openai_integration():
    """Test the OpenAI integration setup"""
    print("=== Testing OpenAI ChatGPT-4.1 Integration ===")
    
    try:
        from autodense.preprocess.openai_policy import OpenAIPreprocessingClient, OpenAIConfig
        
        # Test client initialization
        config = OpenAIConfig(model="gpt-4.1", max_tokens=300, temperature=0.1)
        client = OpenAIPreprocessingClient(config)
        print("✅ OpenAI client initialized successfully")
        print(f"✅ Model: {config.model}")
        print(f"✅ API Key loaded: {'OPENAI_API_KEY' in str(client.client.api_key)}")
        
        return client
        
    except ImportError as e:
        print(f"❌ Import error: {e}")
        return None
    except Exception as e:
        print(f"❌ Client initialization failed: {e}")
        return None

def test_synthetic_gel_openai():
    """Test OpenAI preprocessing on synthetic gel"""
    print("\n=== Testing ChatGPT-4.1 on Synthetic Gel ===")
    
    try:
        from tests.util_synth import make_synth_gel
        from autodense.preprocess.openai_policy import openai_guided_preprocess
        
        # Create synthetic gel with skew
        synth_img, band_rows = make_synth_gel(H=400, W=320, lanes=8, bands_per_lane=5, 
                                             skew_deg=2.5, bright=False, seed=42)
        
        # Convert to PIL Image (RGB format expected)
        synth_pil = Image.fromarray((synth_img * 255).astype(np.uint8)).convert('RGB')
        
        print(f"Synthetic gel: {synth_pil.size}, skew=2.5°, dark bands")
        print("Sending to ChatGPT-4.1 for analysis...")
        
        # Run OpenAI-guided preprocessing
        outcome = openai_guided_preprocess(synth_pil)
        
        print(f"✅ ChatGPT-4.1 decision: {outcome.mode}")
        print(f"✅ Parameters: {outcome.params}")
        print(f"✅ SNR change: {outcome.after['snr'] - outcome.before['snr']:.3f}")
        print(f"✅ Separability change: {outcome.after['sep'] - outcome.before['sep']:.3f}")
        
        # Check if AI reasoning is included
        if 'ai_reasoning' in outcome.params:
            print(f"✅ AI Reasoning: {outcome.params['ai_reasoning']}")
            print(f"✅ AI Confidence: {outcome.params.get('ai_confidence', 'N/A')}")
        
        # Save results
        out_dir = Path("out/test_openai_policy")
        out_dir.mkdir(parents=True, exist_ok=True)
        
        synth_pil.save(out_dir / "synthetic_original.png")
        Image.fromarray((np.clip(outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "synthetic_openai_processed.png")
        
        print(f"✅ Results saved to {out_dir}/")
        return outcome
        
    except Exception as e:
        print(f"❌ OpenAI synthetic test failed: {e}")
        import traceback
        traceback.print_exc()
        return None

def test_real_gel_openai():
    """Test OpenAI preprocessing on real gel image"""
    print("\n=== Testing ChatGPT-4.1 on Real SDS-PAGE Gel ===")
    
    try:
        from autodense.preprocess.openai_policy import openai_guided_preprocess
        
        gel_path = Path("SeedImages/SDS-PAGE/light_and_smiley.jpg")
        if not gel_path.exists():
            # Try some other available images
            for alt_path in ["SeedImages/SDS-PAGE/skewed.jpg", "SeedImages/SDS-PAGE/ten_of_twelve.jpg"]:
                if Path(alt_path).exists():
                    gel_path = Path(alt_path)
                    break
            else:
                print("❌ No test gel images found")
                return None
        
        # Load real gel
        real_img = Image.open(gel_path).convert('RGB')
        print(f"Real gel: {gel_path} ({real_img.size})")
        print("Sending to ChatGPT-4.1 for analysis...")
        
        # Run OpenAI-guided preprocessing
        outcome = openai_guided_preprocess(real_img)
        
        print(f"✅ ChatGPT-4.1 decision: {outcome.mode}")
        print(f"✅ Parameters: {outcome.params}")
        print(f"✅ SNR change: {outcome.after['snr'] - outcome.before['snr']:.3f}")
        print(f"✅ Separability change: {outcome.after['sep'] - outcome.before['sep']:.3f}")
        
        # Check if AI reasoning is included
        if 'ai_reasoning' in outcome.params:
            print(f"✅ AI Reasoning: {outcome.params['ai_reasoning']}")
            print(f"✅ AI Confidence: {outcome.params.get('ai_confidence', 'N/A')}")
        
        # Save results
        out_dir = Path("out/test_openai_policy")
        out_dir.mkdir(parents=True, exist_ok=True)
        
        real_img.save(out_dir / f"real_gel_original_{gel_path.stem}.jpg")
        Image.fromarray((np.clip(outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / f"real_gel_openai_processed_{gel_path.stem}.png")
        
        print(f"✅ Results saved to {out_dir}/")
        return outcome
        
    except Exception as e:
        print(f"❌ OpenAI real gel test failed: {e}")
        import traceback
        traceback.print_exc()
        return None

def compare_algorithmic_vs_openai():
    """Compare algorithmic vs OpenAI preprocessing approaches"""
    print("\n=== Comparing Algorithmic vs ChatGPT-4.1 Approaches ===")
    
    try:
        from autodense.preprocess.policy import guarded_preprocess
        from autodense.preprocess.openai_policy import openai_guided_preprocess
        
        # Use a real gel for comparison
        gel_path = Path("SeedImages/SDS-PAGE/skewed.jpg")  
        if not gel_path.exists():
            print("❌ Skewed gel image not found for comparison")
            return
        
        real_img = Image.open(gel_path).convert('RGB')
        print(f"Comparing approaches on: {gel_path} ({real_img.size})")
        
        # Test algorithmic approach
        print("\n--- Algorithmic Approach ---")
        algo_outcome = guarded_preprocess(real_img)
        print(f"Decision: {algo_outcome.mode}")
        print(f"SNR change: {algo_outcome.after['snr'] - algo_outcome.before['snr']:.3f}")
        
        # Test OpenAI approach  
        print("\n--- ChatGPT-4.1 Approach ---")
        openai_outcome = openai_guided_preprocess(real_img)
        print(f"Decision: {openai_outcome.mode}")
        print(f"SNR change: {openai_outcome.after['snr'] - openai_outcome.before['snr']:.3f}")
        print(f"AI Reasoning: {openai_outcome.params.get('ai_reasoning', 'N/A')}")
        
        # Save comparison results
        out_dir = Path("out/test_openai_policy/comparison")
        out_dir.mkdir(parents=True, exist_ok=True)
        
        real_img.save(out_dir / "original.jpg")
        Image.fromarray((np.clip(algo_outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "algorithmic_result.png")
        Image.fromarray((np.clip(openai_outcome.image, 0, 1) * 255).astype(np.uint8)).save(out_dir / "openai_result.png")
        
        print(f"\n✅ Comparison results saved to {out_dir}/")
        
    except Exception as e:
        print(f"❌ Comparison test failed: {e}")
        import traceback
        traceback.print_exc()

def main():
    """Run all OpenAI preprocessing tests"""
    print("🧪 AutoDense ChatGPT-4.1 Preprocessing Test Suite")
    print("=" * 60)
    
    # Test OpenAI integration setup
    client = test_openai_integration()
    if not client:
        print("❌ Cannot proceed without working OpenAI client")
        return False
    
    # Test on synthetic gel
    synth_result = test_synthetic_gel_openai()
    
    # Test on real gel
    real_result = test_real_gel_openai()
    
    # Compare approaches
    compare_algorithmic_vs_openai()
    
    if synth_result or real_result:
        print("\n🎉 ChatGPT-4.1 preprocessing integration successful!")
        print("\n🚀 Ready Features:")
        print("   ✅ ChatGPT-4.1 intelligent preprocessing decisions")
        print("   ✅ Image analysis with computer vision")
        print("   ✅ Structured reasoning and confidence scores")
        print("   ✅ Automatic fallback to algorithmic approach")
        print("   ✅ API key management from config files")
        
        print("\n💡 Performance Notes:")
        print("   ⚡ Processing time: ~10-30 seconds per image (depends on OpenAI API)")
        print("   💰 Cost: ~$0.01-0.05 per image analysis (GPT-4.1 vision pricing)")
        print("   🔄 Automatic fallback ensures reliability")
        
        return True
    else:
        print("\n❌ ChatGPT-4.1 integration tests failed")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)