# ui/streamlit_app.py
import streamlit as st
from PIL import Image
from pathlib import Path
from autodense.orchestrator.pipeline import Params, run
from autodense.vision.analyzer import draw_overlay

st.set_page_config(page_title="AutoDense (Python-only)", layout="wide")
st.title("AutoDense – SDS/EtBr Gel Analyzer")

uploaded = st.file_uploader("Upload a gel image", type=["png","jpg","jpeg","tif","tiff"])
col1, col2 = st.columns(2)
with col1:
    modality = st.selectbox("Modality", ["sds","dna"], index=0)
    min_lanes = st.number_input("Min lanes", 1, 50, 6)
    max_lanes = st.number_input("Max lanes", 2, 60, 16)
    comb_preset = st.selectbox("Comb", ["auto","10","20"], index=0)
    retries = st.slider("Auto-retries", 0, 3, 1)

run_btn = st.button("Analyze")

if run_btn and uploaded:
    img = Image.open(uploaded).convert("RGB")
    tmp_path = Path("/tmp/_ad_ui.png"); img.save(tmp_path)
    comb = 10 if comb_preset=='10' else (20 if comb_preset=='20' else None)
    p = Params(modality=modality, min_lanes=min_lanes, max_lanes=max_lanes, comb=comb)
    res, p_final, obs = run(tmp_path, p, retries=retries)
    im2 = img.copy()
    out_png = Path("/tmp/_ad_ui_overlay.png")
    draw_overlay(im2, res, out_png)

    with col1:
        st.subheader("Overlay")
        st.image(str(out_png))
        st.markdown(f"**Status:** {'✅ PASS' if obs.get('accepted') else '⚠️ REVIEW'}")
    with col2:
        st.subheader("Lanes")
        st.write([{"lane": ln.index, "type": ln.type, "bands": len(ln.bands)} for ln in res.lanes])
        st.subheader("Observer & Acceptance")
        st.json(obs)
        st.subheader("Params used")
        st.json(p_final.__dict__)
