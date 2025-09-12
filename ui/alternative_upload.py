#!/usr/bin/env python3
"""
Alternative file upload implementation for AutoDense

This provides a fallback solution if the browse button continues to have issues.
It includes multiple upload methods and workarounds.
"""

import streamlit as st
import base64
from pathlib import Path
from PIL import Image
import numpy as np
import io

# Page configuration
st.set_page_config(
    page_title="AutoDense – Alternative Upload",
    page_icon="🔬",
    layout="wide"
)

def create_download_link(val, filename):
    """Create a download link for any content"""
    b64 = base64.b64encode(val)
    return f'<a href="data:application/octet-stream;base64,{b64.decode()}" download="{filename}.jpg">Download file</a>'

def alternative_file_upload():
    """Alternative file upload methods"""
    st.title("🔬 AutoDense – Alternative File Upload Methods")
    
    st.markdown("""
    If the standard browse button isn't working, try these alternative methods:
    """)
    
    # Method 1: Standard uploader with different configuration
    st.markdown("### Method 1: Enhanced File Uploader")
    col1, col2 = st.columns([3, 1])
    
    with col1:
        uploaded_file_1 = st.file_uploader(
            "Drag and drop your gel image here",
            type=["jpg", "jpeg", "png", "tiff", "tif"],
            key="uploader_1",
            help="Try dragging and dropping your file directly onto this area"
        )
    
    with col2:
        st.markdown("**Tips:**")
        st.markdown("- Drag & drop works better")
        st.markdown("- Try different browsers")
        st.markdown("- Clear browser cache")
    
    if uploaded_file_1:
        st.success(f"✅ File uploaded: {uploaded_file_1.name}")
        return uploaded_file_1
    
    # Method 2: Multiple file uploader
    st.markdown("### Method 2: Multiple File Support")
    uploaded_files_2 = st.file_uploader(
        "Upload one or more images",
        type=["jpg", "jpeg", "png", "tiff", "tif"],
        accept_multiple_files=True,
        key="uploader_2"
    )
    
    if uploaded_files_2:
        st.success(f"✅ {len(uploaded_files_2)} file(s) uploaded")
        return uploaded_files_2[0]  # Return first file
    
    # Method 3: Camera input (mobile friendly)
    st.markdown("### Method 3: Camera Input")
    camera_image = st.camera_input("Take a photo of your gel")
    if camera_image:
        st.success("✅ Photo captured")
        return camera_image
    
    # Method 4: URL input
    st.markdown("### Method 4: URL Input")
    image_url = st.text_input("Enter image URL", placeholder="https://example.com/image.jpg")
    if image_url and st.button("Load from URL"):
        try:
            import requests
            response = requests.get(image_url)
            image = Image.open(io.BytesIO(response.content))
            st.success("✅ Image loaded from URL")
            st.image(image, caption="Loaded from URL")
            return io.BytesIO(response.content)
        except Exception as e:
            st.error(f"Failed to load image: {e}")
    
    # Method 5: Text area for base64 (developer method)
    with st.expander("🔧 Developer Method: Base64 Input"):
        st.markdown("Paste base64-encoded image data:")
        base64_data = st.text_area("Base64 image data", height=100)
        if base64_data and st.button("Decode Base64"):
            try:
                # Remove data URL prefix if present
                if "base64," in base64_data:
                    base64_data = base64_data.split("base64,")[1]
                
                image_data = base64.b64decode(base64_data)
                image = Image.open(io.BytesIO(image_data))
                st.success("✅ Base64 image decoded")
                st.image(image, caption="Decoded from Base64")
                return io.BytesIO(image_data)
            except Exception as e:
                st.error(f"Failed to decode base64: {e}")
    
    return None

def test_upload_functionality():
    """Test the file upload and display results"""
    uploaded_file = alternative_file_upload()
    
    if uploaded_file:
        try:
            # Process the uploaded file
            if hasattr(uploaded_file, 'read'):
                image = Image.open(uploaded_file)
            else:
                image = uploaded_file
            
            # Convert to numpy array
            img_array = np.array(image.convert("RGB"))
            h, w, _ = img_array.shape
            
            # Display results
            st.markdown("### 📊 Upload Results")
            col1, col2, col3 = st.columns(3)
            
            with col1:
                st.metric("Width", f"{w} px")
            with col2:
                st.metric("Height", f"{h} px")
            with col3:
                st.metric("Channels", img_array.shape[2])
            
            # Show image
            st.markdown("### 🖼️ Uploaded Image")
            st.image(image, caption="Successfully uploaded and processed", use_container_width=True)
            
            # Show histogram
            st.markdown("### 📈 Image Analysis")
            col1, col2 = st.columns(2)
            
            with col1:
                st.markdown("**Color Channels:**")
                import matplotlib.pyplot as plt
                fig, ax = plt.subplots(figsize=(8, 4))
                
                colors = ['red', 'green', 'blue']
                for i, color in enumerate(colors):
                    hist, bins = np.histogram(img_array[:,:,i], bins=50)
                    ax.plot(bins[:-1], hist, color=color, alpha=0.7, label=f'{color.capitalize()} channel')
                
                ax.set_xlabel('Pixel Intensity')
                ax.set_ylabel('Frequency')
                ax.set_title('Color Channel Histograms')
                ax.legend()
                st.pyplot(fig)
            
            with col2:
                st.markdown("**Image Statistics:**")
                st.write(f"Mean intensity: {np.mean(img_array):.2f}")
                st.write(f"Standard deviation: {np.std(img_array):.2f}")
                st.write(f"Min intensity: {np.min(img_array)}")
                st.write(f"Max intensity: {np.max(img_array)}")
                
                # Convert to grayscale for basic analysis
                gray = np.mean(img_array, axis=2)
                st.write(f"Grayscale mean: {np.mean(gray):.2f}")
            
        except Exception as e:
            st.error(f"Error processing image: {e}")
            st.exception(e)

if __name__ == "__main__":
    test_upload_functionality()