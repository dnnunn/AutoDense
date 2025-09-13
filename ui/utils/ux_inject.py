# ui/ux_inject.py
import streamlit as st

def inject_css(css: str, *, key: str = "ux-css"):
    st.markdown(f"<style id='{key}'>\n{css}\n</style>", unsafe_allow_html=True)

def inject_html(html: str):
    st.markdown(html, unsafe_allow_html=True)

