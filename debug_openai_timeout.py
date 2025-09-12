#!/usr/bin/env python3
"""
Debug script to investigate OpenAI timeout issues
"""
import time
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))

def debug_openai_timeout():
    print("🔍 Debugging OpenAI Timeout Issues")
    print("=" * 40)
    
    try:
        from autodense.preprocess.openai_policy import OpenAIPreprocessingClient, OpenAIConfig
        
        # Test 1: Quick client initialization
        print("1️⃣ Testing client initialization...")
        start = time.time()
        config = OpenAIConfig(model="gpt-4.1", timeout=60.0)  # Increase timeout to 60s
        client = OpenAIPreprocessingClient(config)
        init_time = time.time() - start
        print(f"   ✅ Client initialized in {init_time:.2f}s")
        
        # Test 2: Simple text-only API call (no image)
        print("\n2️⃣ Testing simple text-only API call...")
        start = time.time()
        try:
            response = client.client.chat.completions.create(
                model="gpt-4.1",
                messages=[{"role": "user", "content": "Reply with exactly: 'API_WORKING'"}],
                max_tokens=10,
                temperature=0.0
            )
            api_time = time.time() - start
            result = response.choices[0].message.content.strip()
            print(f"   ✅ API call completed in {api_time:.2f}s")
            print(f"   ✅ Response: {result}")
            
            if "API_WORKING" not in result:
                print(f"   ⚠️  Unexpected response, but API is working")
                
        except Exception as e:
            api_time = time.time() - start
            print(f"   ❌ API call failed after {api_time:.2f}s: {e}")
            return False
        
        # Test 3: Vision API with small image
        print("\n3️⃣ Testing vision API with small synthetic image...")
        start = time.time()
        try:
            # Create tiny test image
            import numpy as np
            tiny_img = np.random.randint(0, 255, (64, 64, 3), dtype=np.uint8)
            pil_img = Image.fromarray(tiny_img)
            
            # Convert to base64 (test the conversion step)
            img_b64 = client._image_to_base64(tiny_img)
            conversion_time = time.time() - start
            print(f"   ✅ Image converted to base64 in {conversion_time:.2f}s (size: {len(img_b64)} chars)")
            
            # Test full vision API call
            vision_start = time.time()
            response = client.client.chat.completions.create(
                model="gpt-4.1",
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": "What do you see in this image? Reply with exactly: 'VISION_WORKING'"},
                            {"type": "image_url", "image_url": {"url": img_b64}}
                        ]
                    }
                ],
                max_tokens=20,
                temperature=0.0
            )
            vision_time = time.time() - vision_start
            vision_result = response.choices[0].message.content.strip()
            
            print(f"   ✅ Vision API call completed in {vision_time:.2f}s")
            print(f"   ✅ Response: {vision_result}")
            
        except Exception as e:
            vision_time = time.time() - start
            print(f"   ❌ Vision API call failed after {vision_time:.2f}s: {e}")
            return False
        
        # Test 4: Full preprocessing workflow (the actual bottleneck)
        print("\n4️⃣ Testing full preprocessing workflow...")
        start = time.time()
        try:
            # Use a real but small image
            test_img = Image.open("SeedImages/SDS-PAGE/light_and_smiley.jpg").convert('RGB')
            # Resize to make it smaller and faster
            test_img = test_img.resize((320, 240), Image.Resampling.LANCZOS)
            
            from autodense.preprocess.openai_policy import openai_guided_preprocess
            
            # Track individual steps
            print(f"   🔄 Starting full preprocessing workflow...")
            outcome = openai_guided_preprocess(test_img)
            
            workflow_time = time.time() - start
            print(f"   ✅ Full workflow completed in {workflow_time:.2f}s")
            print(f"   ✅ Decision: {outcome.mode}")
            print(f"   ✅ Confidence: {outcome.params.get('ai_confidence', 'N/A')}")
            
            return True
            
        except Exception as e:
            workflow_time = time.time() - start
            print(f"   ❌ Full workflow failed after {workflow_time:.2f}s: {e}")
            import traceback
            traceback.print_exc()
            return False
            
    except Exception as e:
        print(f"❌ Setup failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    success = debug_openai_timeout()
    
    if success:
        print("\n✅ OpenAI integration is working correctly!")
        print("\n💡 Timeout Investigation Results:")
        print("   - API calls are completing successfully")
        print("   - The timeout might be from the command line tool timeout (2m default)")
        print("   - Consider increasing command timeout for testing")
    else:
        print("\n❌ Found issues with OpenAI integration")
        print("\n🔧 Potential fixes:")
        print("   1. Check internet connection")
        print("   2. Verify OpenAI API key is valid and has credits")
        print("   3. Try a different model (gpt-4o-mini for faster responses)")
        
    return success

if __name__ == "__main__":
    main()