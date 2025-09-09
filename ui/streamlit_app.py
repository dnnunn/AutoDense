# ui/streamlit_app.py
import streamlit as st
from PIL import Image, ImageDraw
from pathlib import Path
from autodense.orchestrator.pipeline import Params, run
from autodense.vision.mw_helpers import compute_mw_or_bp

st.set_page_config(page_title="AutoDense – MW-aware", layout="wide")
st.title("AutoDense – SDS/EtBr Gel Analyzer")

uploaded = st.file_uploader("Upload a gel image", type=["png","jpg","jpeg","tif","tiff"])

col1, col2 = st.columns(2)
with col1:
    modality = st.selectbox("Modality", ["sds","dna"], index=0)
    # comb presets based on your lab equipment
    if modality == "sds":
        comb_choice = st.selectbox("Comb", ["auto","10","12","15"], index=0)
        ladder_type = st.selectbox("Ladder type", ["auto","pageruler_10_180"], index=0)
    else:
        comb_choice = st.selectbox("Comb", ["auto","10","20"], index=0)
        ladder_type = st.selectbox("Ladder type", ["auto","neb_1kb"], index=0)
    min_lanes = st.number_input("Min lanes", 1, 60, (10 if modality=="dna" else 6))
    max_lanes = st.number_input("Max lanes", 2, 60, (20 if modality=="dna" else 16))
    retries = st.slider("Auto-retries", 0, 3, 1)
    conf_min = st.slider("Show bands with confidence ≥", 0.0, 1.0, 0.30, 0.01)
    ladder_hint = st.text_input("Ladder lanes (comma-separated, e.g., '1,10')", "")

run_btn = st.button("Analyze")

if run_btn and uploaded:
    img = Image.open(uploaded).convert("RGB")
    tmp_path = Path("/tmp/_ad_ui.png"); img.save(tmp_path)
    comb = None
    if comb_choice != "auto":
        comb = int(comb_choice)
    p = Params(modality=modality, min_lanes=min_lanes, max_lanes=max_lanes, comb=comb)
    res, p_final, obs = run(tmp_path, p, retries=retries)

    ladder_idxs = [int(x) for x in ladder_hint.split(",") if x.strip().isdigit()] if ladder_hint else []
    mw_info = compute_mw_or_bp(res, modality, ladder_idxs, ladder_type=ladder_type) if ladder_idxs else None

    im2 = img.copy()
    draw = ImageDraw.Draw(im2, "RGBA")
    for ln in res.lanes:
        draw.rectangle([ln.x0, ln.y0, ln.x1, ln.y1], outline=(0,0,0,160), width=2, fill=(0,255,0,40) if ln.type!="marker" else (0,0,255,40))
        for b in ln.bands:
            if b.confidence >= conf_min:
                draw.rectangle([ln.x0, b.y0, ln.x1, b.y1], outline=(255,0,0,220), width=2)
    out_png = Path("/tmp/_ad_ui_overlay.png")
    im2.save(out_png)

    with col1:
        st.subheader("Overlay")
        st.image(str(out_png))
        st.markdown(f"**Status:** {'✅ PASS' if obs.get('accepted') else '⚠️ REVIEW'}")

    with col2:
        st.subheader("Lanes")
        st.write([{"lane": ln.index, "type": ln.type, "bands": len([b for b in ln.bands if b.confidence >= conf_min])} for ln in res.lanes])
        st.subheader("Observer & Acceptance")
        st.json(obs)
        st.subheader("Params used")
        st.json(p_final.__dict__)
        if mw_info:
            st.subheader("Calibration")
            st.json(mw_info)
            rows = []
            for ln in res.lanes:
                for b in ln.bands:
                    if b.confidence >= conf_min:
                        row = {"lane": ln.index, "band": b.index, "confidence": round(b.confidence,3), "intensity": round(b.intensity or 0.0,2)}
                        if modality=="sds" and getattr(b, "mw_kda", None) is not None:
                            row["kDa"] = round(b.mw_kda, 1)
                        if modality=="dna" and getattr(b, "bp", None) is not None:
                            row["bp"] = int(round(b.bp))
                        rows.append(row)
            st.table(rows)
        else:
            st.subheader("Bands (filtered)")
            rows = []
            for ln in res.lanes:
                for b in ln.bands:
                    if b.confidence >= conf_min:
                        rows.append({"lane": ln.index, "band": b.index, "confidence": round(b.confidence,3), "intensity": round(b.intensity or 0.0,2)})
            st.table(rows)

def main():
    """Entry point for autodense-ui console command"""
    import subprocess
    import sys
    subprocess.run([sys.executable, "-m", "streamlit", "run", __file__])
