#!/usr/bin/env python3
"""
Quick test of ChatGPT-4.1 integration
"""
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))

def quick_test():
    try:
        from autodense.preprocess.openai_policy import openai_guided_preprocess, OpenAIPreprocessingClient
        
        print("🧪 Quick ChatGPT-4.1 Integration Test")
        print("=" * 40)
        
        # Test client setup
        client = OpenAIPreprocessingClient()
        print(f"✅ OpenAI client initialized with model: {client.config.model}")
        
        # Load a small test image
        test_img = Image.open("SeedImages/SDS-PAGE/light_and_smiley.jpg").convert('RGB')
        print(f"✅ Test image loaded: {test_img.size}")
        
        print("🔄 Processing with ChatGPT-4.1...")
        outcome = openai_guided_preprocess(test_img)
        
        print(f"✅ Decision: {outcome.mode}")
        print(f"✅ AI Reasoning: {outcome.params.get('ai_reasoning', 'N/A')}")
        print(f"✅ Confidence: {outcome.params.get('ai_confidence', 'N/A')}")
        print(f"✅ SNR improvement: {outcome.after['snr'] - outcome.before['snr']:.3f}")
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return False

if __name__ == "__main__":
    success = quick_test()
    if success:
        print("\n🎉 ChatGPT-4.1 integration working!")
    sys.exit(0 if success else 1)