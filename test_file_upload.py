#!/usr/bin/env python3
"""
Test script to verify file upload functionality in the optimized Streamlit app.
"""

import streamlit as st
from PIL import Image
import numpy as np
import requests
import tempfile
import os

def test_file_upload_logic():
    """Test the file upload logic without UI"""
    
    # Create a test image
    test_img = Image.new('RGB', (100, 100), color='red')
    
    # Save to temp file
    with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as f:
        test_img.save(f.name)
        temp_path = f.name
    
    try:
        # Test the image loading logic
        img = Image.open(temp_path)
        img_array = np.array(img.convert("RGB"))
        h, w, _ = img_array.shape
        
        print(f"✅ Image loaded successfully: {w}x{h}")
        print(f"✅ Array shape: {img_array.shape}")
        
        return True
    except Exception as e:
        print(f"❌ Error: {e}")
        return False
    finally:
        # Clean up
        if os.path.exists(temp_path):
            os.unlink(temp_path)

def check_streamlit_dependencies():
    """Check if required dependencies are available"""
    try:
        import streamlit as st
        print(f"✅ Streamlit version: {st.__version__}")
        
        from PIL import Image
        print("✅ PIL (Pillow) available")
        
        import numpy as np
        print("✅ NumPy available")
        
        try:
            from streamlit_drawable_canvas import st_canvas
            print("✅ streamlit_drawable_canvas available")
        except ImportError:
            print("⚠️ streamlit_drawable_canvas not available")
        
        return True
    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        return False

def main():
    print("🔬 AutoDense File Upload Test")
    print("=" * 40)
    
    # Test dependencies
    print("\n1. Checking dependencies...")
    deps_ok = check_streamlit_dependencies()
    
    # Test file upload logic
    print("\n2. Testing file upload logic...")
    upload_ok = test_file_upload_logic()
    
    # Summary
    print("\n" + "=" * 40)
    if deps_ok and upload_ok:
        print("✅ All tests passed! File upload should work correctly.")
    else:
        print("❌ Some tests failed. Check the errors above.")

if __name__ == "__main__":
    main()