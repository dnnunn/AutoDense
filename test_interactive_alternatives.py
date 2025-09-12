#!/usr/bin/env python3
"""
Test script for interactive image annotation alternatives
"""

import streamlit as st
import numpy as np
from PIL import Image
import io

# Test the three alternatives
st.title("Interactive Canvas Alternatives Test")

# Create a test image
test_img = Image.new('RGB', (400, 300), color='lightgray')
test_array = np.array(test_img)

st.write("## Test Image")
st.image(test_img, caption="Test Image", width=400)

# Test 1: streamlit-image-annotation
st.write("## 1. streamlit-image-annotation")
try:
    from streamlit_image_annotation import classification
    
    st.write("✅ streamlit-image-annotation imported successfully")
    
    # Test classification (point annotation)
    result1 = classification(
        image=test_img,
        annotations={"Point": "point"},
        key="annotation_test"
    )
    
    if result1:
        st.write("**Classification Result:**", result1)
        
except ImportError as e:
    st.error(f"❌ streamlit-image-annotation import failed: {e}")
except Exception as e:
    st.error(f"❌ streamlit-image-annotation error: {e}")

# Test 2: streamlit-image-coordinates  
st.write("## 2. streamlit-image-coordinates")
try:
    from streamlit_image_coordinates import streamlit_image_coordinates
    
    st.write("✅ streamlit-image-coordinates imported successfully")
    
    # Test coordinate capture
    value = streamlit_image_coordinates(
        test_img,
        key="coordinates_test"
    )
    
    if value:
        st.write("**Coordinates Result:**", value)
        
except ImportError as e:
    st.error(f"❌ streamlit-image-coordinates import failed: {e}")
except Exception as e:
    st.error(f"❌ streamlit-image-coordinates error: {e}")

# Test 3: streamlit-labelstudio
st.write("## 3. streamlit-labelstudio")
try:
    from streamlit_labelstudio import st_labelstudio
    
    st.write("✅ streamlit-labelstudio imported successfully")
    
    # Simple point annotation config
    config = """
    <View>
      <Image name="image" value="$image"/>
      <KeyPointLabels name="kp" toName="image">
        <Label value="Point" background="red"/>
      </KeyPointLabels>
    </View>
    """
    
    # Convert image to base64 for labelstudio
    buffer = io.BytesIO()
    test_img.save(buffer, format="PNG")
    import base64
    img_b64 = base64.b64encode(buffer.getvalue()).decode()
    
    interfaces = ["panel", "update", "submit", "skip", "controls"]
    
    result3 = st_labelstudio(
        config,
        interfaces,
        {"image": f"data:image/png;base64,{img_b64}"},
        key="labelstudio_test"
    )
    
    if result3:
        st.write("**LabelStudio Result:**", result3)
        
except ImportError as e:
    st.error(f"❌ streamlit-labelstudio import failed: {e}")
except Exception as e:
    st.error(f"❌ streamlit-labelstudio error: {e}")

st.write("## Summary")
st.write("This test helps determine which interactive annotation library works best for our two-point calibration system.")