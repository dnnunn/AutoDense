#!/usr/bin/env python3
"""
Simple test for Streamlit file uploader functionality
"""

import streamlit as st

st.title("File Upload Test")
st.write("Testing basic Streamlit file uploader functionality")

# Basic file uploader
uploaded_file = st.file_uploader(
    "Choose a file",
    type=["jpg", "jpeg", "png", "txt"]
)

if uploaded_file:
    st.success("File uploaded successfully!")
    st.write("Filename:", uploaded_file.name)
    st.write("File size:", uploaded_file.size, "bytes")
else:
    st.info("Please select a file to upload")

# Debug info
st.write("### Debug Info")
st.write("Streamlit version:", st.__version__)
st.write("Session state:", st.session_state)