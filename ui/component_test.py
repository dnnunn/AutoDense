#!/usr/bin/env python3
"""
Component Evaluation Test
Compare streamlit-image-annotation, streamlit-labelstudio, and streamlit-image-coordinates
for AutoDense two-point calibration.

Test criteria:
1. Auto-sizing behavior (responsive to container)
2. API simplicity for point collection
3. Coordinate accuracy and scaling
4. Performance and usability
"""

import streamlit as st
from PIL import Image
import numpy as np

st.set_page_config(page_title="Component Evaluation", layout="wide")

st.title("🔬 AutoDense Calibration Component Evaluation")

# Load test image
test_image_path = "/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/SeedImages/SDS-PAGE/annotated.jpg"

try:
    img = Image.open(test_image_path)
    w, h = img.size
    st.success(f"✅ Test image loaded: {w}×{h}px")
except Exception as e:
    st.error(f"❌ Could not load test image: {e}")
    st.stop()

# Create tabs for each component
tab1, tab2, tab3 = st.tabs([
    "🎯 streamlit-image-coordinates", 
    "🏷️ streamlit-image-annotation", 
    "🏭 streamlit-labelstudio"
])

with tab1:
    st.subheader("streamlit-image-coordinates (Current)")
    
    try:
        from streamlit_image_coordinates import streamlit_image_coordinates
        
        st.markdown("**Auto-sizing test:** Using no explicit width/height")
        
        coordinates = streamlit_image_coordinates(
            img,
            cursor="crosshair",
            key="coords_test"
        )
        
        if coordinates:
            st.json(coordinates)
            
        # Test with explicit sizing
        st.markdown("**Explicit sizing test:** 700px width")
        
        display_width = 700
        display_height = int(700 * h / w)
        
        coordinates2 = streamlit_image_coordinates(
            img,
            width=display_width,
            height=display_height,
            cursor="crosshair",
            key="coords_test2"
        )
        
        if coordinates2:
            st.json(coordinates2)
            
    except ImportError as e:
        st.error(f"❌ streamlit-image-coordinates not available: {e}")

with tab2:
    st.subheader("streamlit-image-annotation")
    
    try:
        from streamlit_image_annotation import pointdet
        
        st.markdown("**Point Detection API:** Perfect for two-point calibration!")
        
        # Save image temporarily for path-based API
        import tempfile
        import os
        
        with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as tmp_file:
            img.save(tmp_file.name, 'JPEG')
            tmp_path = tmp_file.name
        
        try:
            # Test point detection - exactly what we need for calibration
            result = pointdet(
                image_path=tmp_path,
                label_list=["Point 1", "Point 2"],
                height=400,  # Add explicit dimensions to avoid layout_config issues
                width=600,
                key="pointdet_test"
            )
            
            if result:
                st.success("✅ Point detection result:")
                st.json(result)
                
                # Show what we get for coordinates
                if 'points' in result:
                    st.info("📍 Coordinate format analysis:")
                    for i, point in enumerate(result['points']):
                        st.write(f"Point {i+1}: {point}")
            
            st.markdown("**API Parameters:**")
            st.code("""
pointdet(
    image_path,      # File path (required)
    label_list,      # ["Point 1", "Point 2"] 
    points=None,     # Existing points [[x,y], [x,y]]
    labels=None,     # Point labels [0, 1]
    height=512,      # Display height (auto-sizing?)
    width=512,       # Display width (auto-sizing?)
    point_width=3,   # Point marker size
    use_space=False, # Unknown parameter
    key=None         # Streamlit key
)
            """)
            
        finally:
            # Clean up temp file
            try:
                os.unlink(tmp_path)
            except:
                pass
            
    except ImportError as e:
        st.error(f"❌ streamlit-image-annotation not available: {e}")
    except Exception as e:
        st.error(f"❌ Error with streamlit-image-annotation: {e}")
        st.exception(e)

with tab3:
    st.subheader("streamlit-labelstudio")
    
    try:
        from streamlit_labelstudio import st_labelstudio
        
        st.markdown("**Auto-sizing test:** Default behavior")
        
        # Label Studio config for point annotation
        config = """
        <View>
          <Image name="image" value="$image"/>
          <KeyPointLabels name="kp" toName="image">
            <Label value="Point 1" background="red"/>
            <Label value="Point 2" background="blue"/>
          </KeyPointLabels>
        </View>
        """
        
        # Convert PIL image to data URL
        import io
        import base64
        
        img_buffer = io.BytesIO()
        img.save(img_buffer, format='PNG')
        img_str = base64.b64encode(img_buffer.getvalue()).decode()
        img_data_url = f"data:image/png;base64,{img_str}"
        
        # Note: streamlit-labelstudio API: st_labelstudio(config, interfaces, user, task)
        result = st_labelstudio(
            config,
            {},  # interfaces
            {},  # user
            {"image": img_data_url}  # task
        )
        
        if result:
            st.json(result)
            
    except ImportError as e:
        st.error(f"❌ streamlit-labelstudio not available: {e}")
    except Exception as e:
        st.error(f"❌ Error with streamlit-labelstudio: {e}")

# Evaluation criteria
st.markdown("---")
st.subheader("🏆 Evaluation Criteria")

st.markdown("""
**Scoring each component on:**

1. **Auto-sizing** (0-5): How well does it adapt to container size?
2. **API Simplicity** (0-5): How easy is it to get x,y coordinates?
3. **Coordinate Accuracy** (0-5): Are coordinates precise and properly scaled?
4. **Performance** (0-5): Responsiveness and reliability
5. **Usability** (0-5): User experience for two-point calibration

**Requirements for AutoDense:**
- Collect exactly 2 point coordinates
- Auto-fit to available container space
- Return accurate pixel coordinates
- Simple, reliable API
- Good performance with scientific images
""")

# Summary table
st.subheader("📊 Component Comparison")

comparison_data = {
    "Component": [
        "streamlit-image-coordinates", 
        "streamlit-image-annotation", 
        "streamlit-labelstudio"
    ],
    "Auto-sizing": ["?", "?", "?"],
    "API Simplicity": ["?", "?", "?"],
    "Coordinate Accuracy": ["?", "?", "?"],
    "Performance": ["?", "?", "?"],
    "Usability": ["?", "?", "?"],
    "Total Score": ["?", "?", "?"]
}

st.table(comparison_data)
st.info("👆 Test each component above and update scores manually based on behavior")