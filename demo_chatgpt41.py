#!/usr/bin/env python3
"""
Demo script showing ChatGPT-4.1 vs algorithmic preprocessing comparison
"""
import sys
from pathlib import Path
from PIL import Image
import numpy as np

sys.path.insert(0, str(Path(__file__).parent))

def demo_chatgpt41():
    print("🚀 AutoDense ChatGPT-4.1 Integration Demo")
    print("=" * 50)
    
    # Test image
    test_image_path = Path("SeedImages/SDS-PAGE/skewed.jpg")
    if not test_image_path.exists():
        test_image_path = Path("SeedImages/SDS-PAGE/light_and_smiley.jpg")
    
    if not test_image_path.exists():
        print("❌ No test images found in SeedImages/SDS-PAGE/")
        return False
    
    print(f"📁 Loading test image: {test_image_path}")
    test_img = Image.open(test_image_path).convert('RGB')
    print(f"📏 Image size: {test_img.size}")
    
    # Test 1: Algorithmic preprocessing
    print("\n--- Algorithmic AI Preprocessing ---")
    try:
        from autodense.preprocess.policy import guarded_preprocess
        
        algo_outcome = guarded_preprocess(test_img)
        print(f"✅ Decision: {algo_outcome.mode}")
        print(f"✅ SNR change: {algo_outcome.after['snr'] - algo_outcome.before['snr']:.3f}")
        print(f"✅ Parameters: {algo_outcome.params}")
        
    except Exception as e:
        print(f"❌ Algorithmic preprocessing failed: {e}")
        return False
    
    # Test 2: ChatGPT-4.1 preprocessing
    print("\n--- ChatGPT-4.1 AI Preprocessing ---")
    try:
        from autodense.preprocess.openai_policy import openai_guided_preprocess
        
        print("🤖 Sending to ChatGPT-4.1 for analysis...")
        openai_outcome = openai_guided_preprocess(test_img)
        
        print(f"✅ Decision: {openai_outcome.mode}")
        print(f"✅ SNR change: {openai_outcome.after['snr'] - openai_outcome.before['snr']:.3f}")
        print(f"✅ AI Confidence: {openai_outcome.params.get('ai_confidence', 'N/A')}")
        print(f"✅ AI Reasoning: {openai_outcome.params.get('ai_reasoning', 'No reasoning provided')}")
        
        # Save results for comparison
        out_dir = Path("out/chatgpt41_demo")
        out_dir.mkdir(parents=True, exist_ok=True)
        
        # Save original
        test_img.save(out_dir / "original.jpg")
        
        # Save algorithmic result
        algo_result = (np.clip(algo_outcome.image, 0, 1) * 255).astype(np.uint8)
        Image.fromarray(algo_result).save(out_dir / f"algorithmic_{algo_outcome.mode}.png")
        
        # Save ChatGPT result
        openai_result = (np.clip(openai_outcome.image, 0, 1) * 255).astype(np.uint8)
        Image.fromarray(openai_result).save(out_dir / f"chatgpt41_{openai_outcome.mode}.png")
        
        print(f"\n📁 Results saved to: {out_dir}/")
        
        # Summary
        print(f"\n🎯 SUMMARY:")
        print(f"   Algorithmic: {algo_outcome.mode} (ΔSNR: {algo_outcome.after['snr'] - algo_outcome.before['snr']:.3f})")
        print(f"   ChatGPT-4.1: {openai_outcome.mode} (ΔSNR: {openai_outcome.after['snr'] - openai_outcome.before['snr']:.3f})")
        
        if openai_outcome.params.get('ai_confidence'):
            print(f"   AI Confidence: {openai_outcome.params['ai_confidence']:.1%}")
        
        return True
        
    except Exception as e:
        print(f"❌ ChatGPT-4.1 preprocessing failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    success = demo_chatgpt41()
    
    if success:
        print("\n🎉 ChatGPT-4.1 integration is working!")
        print("\n🚀 Ready to use:")
        print("   1. Run: streamlit run ui/streamlit_app_simple.py")
        print("   2. Select 'ChatGPT-4.1 (recommended)' preprocessing mode")
        print("   3. Upload a gel image and see AI-powered analysis!")
        
        print("\n⚡ Performance:")
        print("   - Processing time: ~10-30 seconds per image")
        print("   - Model: GPT-4.1 (upgraded from GPT-4.1-mini)")
        print("   - Automatic fallback to algorithmic if API fails")
        
    else:
        print("\n❌ Demo failed - check OpenAI API key configuration")
        print("\n💡 Troubleshooting:")
        print("   1. Ensure OPENAI_API_KEY is set in environment")
        print("   2. Or add OPENAI_API_KEY=... to api-config.properties")
        print("   3. Check your OpenAI account has credits")
    
    return success

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)