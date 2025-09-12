#!/usr/bin/env python3
"""
Browse Button Diagnostic Test for AutoDense Streamlit App

This test file helps diagnose why the "Browse files" button isn't working
in the st.file_uploader component.
"""

import streamlit as st
import sys
import platform
from pathlib import Path

# Page configuration
st.set_page_config(
    page_title="Browse Button Test",
    page_icon="🔍",
    layout="wide"
)

st.title("🔍 Browse Button Diagnostic Test")

# Environment info
st.markdown("### 🖥️ Environment Information")
col1, col2 = st.columns(2)

with col1:
    st.write(f"**Streamlit version:** {st.__version__}")
    st.write(f"**Python version:** {sys.version.split()[0]}")
    st.write(f"**Platform:** {platform.system()} {platform.release()}")

with col2:
    st.write(f"**Browser:** {st.get_option('browser.gatherUsageStats')}")
    st.write(f"**Server address:** {st.get_option('server.address')}")
    st.write(f"**Server port:** {st.get_option('server.port')}")

# Basic file uploader test
st.markdown("### 📤 Basic File Uploader Test")
st.write("This should show a working browse button:")

uploaded_file = st.file_uploader(
    "Choose a file",
    type=["jpg", "jpeg", "png", "tiff", "tif"],
    help="Click 'Browse files' button to test functionality"
)

if uploaded_file:
    st.success(f"✅ File uploaded: {uploaded_file.name}")
    st.write(f"Size: {uploaded_file.size} bytes")
else:
    st.info("👆 Try clicking the 'Browse files' button")

# Alternative configurations to test
st.markdown("### 🧪 Alternative Configurations")

st.markdown("#### Test 1: Without type restriction")
test1 = st.file_uploader("Upload any file", key="test1")
if test1:
    st.success(f"Test 1 success: {test1.name}")

st.markdown("#### Test 2: With accept_multiple_files")
test2 = st.file_uploader(
    "Upload multiple files",
    accept_multiple_files=True,
    type=["jpg", "jpeg", "png"],
    key="test2"
)
if test2:
    st.success(f"Test 2 success: {len(test2)} files uploaded")

st.markdown("#### Test 3: With label_visibility='collapsed'")
test3 = st.file_uploader(
    "Hidden label test",
    type=["jpg", "jpeg", "png"],
    label_visibility="collapsed",
    key="test3"
)
if test3:
    st.success(f"Test 3 success: {test3.name}")

# JavaScript diagnostics
st.markdown("### 💻 Browser Diagnostics")
st.markdown("""
**Common causes of browse button issues:**
1. **Browser security settings** blocking file dialogs
2. **Streamlit server configuration** issues
3. **CSS/JavaScript conflicts** in the browser
4. **Pop-up blockers** interfering with file dialogs
5. **HTTPS/HTTP mixed content** issues

**Debug steps:**
1. Check browser console for JavaScript errors (F12)
2. Try different browsers (Chrome, Firefox, Safari)
3. Test in incognito/private mode
4. Disable browser extensions
5. Check if running over HTTPS vs HTTP
""")

# Streamlit configuration check
st.markdown("### ⚙️ Streamlit Configuration")
config_items = [
    'server.headless',
    'server.enableCORS',
    'server.enableXsrfProtection',
    'browser.serverAddress',
    'browser.gatherUsageStats'
]

for item in config_items:
    try:
        value = st.get_option(item)
        st.write(f"**{item}:** {value}")
    except Exception as e:
        st.write(f"**{item}:** Error reading ({e})")

# File system permissions test
st.markdown("### 📁 File System Test")
try:
    import tempfile
    temp_dir = tempfile.gettempdir()
    st.write(f"**Temp directory:** {temp_dir}")
    st.write(f"**Temp dir exists:** {Path(temp_dir).exists()}")
    st.write(f"**Temp dir writable:** {Path(temp_dir).is_dir()}")
except Exception as e:
    st.error(f"File system test failed: {e}")

st.markdown("---")
st.markdown("**💡 Tip:** If the browse button still doesn't work, try the following:")
st.markdown("1. Restart the Streamlit server")
st.markdown("2. Clear browser cache")
st.markdown("3. Try a different browser")
st.markdown("4. Check browser console for errors")