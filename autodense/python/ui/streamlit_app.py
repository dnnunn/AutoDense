# ui/streamlit_app.py
import io, json, math, shutil, time
import numpy as np
import pandas as pd
import streamlit as st
from PIL import Image, ImageDraw
from pathlib import Path
from typing import List, Tuple
from autodense.orchestrator.pipeline import Params, run
from autodense.vision.mw_helpers import compute_mw_or_bp, band_center_norm
from autodense.vision.overlay_labels import draw_labels
from autodense.export.coco import export_coco
from autodense.export.yolo import export_yolo_txt
from autodense.export.sidecar import write_sidecar
from autodense.recipes import RecipeRegistry, Recipe, RecipeStep, SafeOps
from autodense.assist.nlp_intent import parse_prompt_to_recipe, execute as exec_recipe, validate_recipe as val_recipe
from autodense.assist.label_fitting import place_labels

# Optional click-canvas
try:
    from streamlit_drawable_canvas import st_canvas
    CANVAS_OK = True
except Exception:
    CANVAS_OK = False

st.set_page_config(page_title="AutoDense • Analyzer", layout="wide")
st.title("AutoDense – SDS/EtBr Analyzer")

# ---------- Session helpers ----------
def set_state(**kwargs):
    for k,v in kwargs.items():
        st.session_state[k] = v

def get_state(name, default=None):
    return st.session_state.get(name, default)

def _init_key(key, value):
    if key not in st.session_state:
        st.session_state[key] = value

# ---------- Sidebar presets ----------
st.sidebar.subheader("Presets")
preset = st.sidebar.selectbox(
    "Quick lab presets",
    [
        "None",
        "SDS • PageRuler 10–180 (Comb 10/12/15)",
        "DNA • NEB 1 kb (Comb 10/20)",
    ],
    index=0,
    help="Applies recommended modality, ladder, and comb presets."
)

# Initialize UI keys so we can override them via presets/suggestions
_init_key("ui_modality", "sds")
_init_key("ui_comb_choice", "auto")
_init_key("ui_ladder_type", "auto")

if preset == "SDS • PageRuler 10–180 (Comb 10/12/15)":
    set_state(ui_modality="sds", ui_ladder_type="pageruler_10_180")
elif preset == "DNA • NEB 1 kb (Comb 10/20)":
    set_state(ui_modality="dna", ui_ladder_type="neb_1kb")

mode = st.sidebar.radio("Mode", ["Guided", "Expert"], index=0)
uploaded = st.sidebar.file_uploader("Upload a gel image", type=["png","jpg","jpeg","tif","tiff"])

# Tabs
tab_analyze, tab_calib, tab_quant, tab_manip, tab_assist, tab_stats, tab_save, tab_label, tab_hist = st.tabs(
    ["Analyze", "Calibrate", "Quantify", "Manipulate", "Assist", "Stats", "Save", "Labeler", "History"]
)

# ========================= Analyze =========================
with tab_analyze:
    col1, col2 = st.columns([3,2])
    with col1:
        modality = st.selectbox("Modality", ["sds","dna"], index=(0 if get_state("ui_modality")=="sds" else 1), key="ui_modality",
                                help="SDS = protein gels; DNA = EtBr/gelred gels")
        if modality == "sds":
            comb_choice = st.selectbox("Comb", ["auto","10","12","15"], index=["auto","10","12","15"].index(get_state("ui_comb_choice","auto")), key="ui_comb_choice")
            ladder_type = st.selectbox("Ladder type", ["auto","pageruler_10_180"], index=(0 if get_state("ui_ladder_type")=="auto" else 1), key="ui_ladder_type")
            min_default, max_default = 6, 16
        else:
            comb_choice = st.selectbox("Comb", ["auto","10","20"], index=["auto","10","20"].index(get_state("ui_comb_choice","auto")), key="ui_comb_choice")
            ladder_type = st.selectbox("Ladder type", ["auto","neb_1kb"], index=(0 if get_state("ui_ladder_type")=="auto" else 1), key="ui_ladder_type")
            min_default, max_default = 10, 20

        if mode == "Expert":
            min_lanes = st.number_input("Min lanes", 1, 60, min_default)
            max_lanes = st.number_input("Max lanes", 2, 60, max_default)
            retries = st.slider("Auto-retries", 0, 5, 1)
            conf_min = st.slider("Label bands with confidence ≥", 0.0, 1.0, 0.30, 0.01)
        else:
            min_lanes, max_lanes, retries, conf_min = min_default, max_default, 1, 0.30

        run_btn = st.button("Run analysis", disabled=(uploaded is None))

    if run_btn and uploaded:
        img = Image.open(uploaded).convert("RGB")
        tmp_path = Path("/tmp/_ad_ui.png"); img.save(tmp_path)
        comb = None if comb_choice == "auto" else int(comb_choice)
        p = Params(modality=modality, min_lanes=min_lanes, max_lanes=max_lanes, comb=comb)
        res, p_final, obs = run(tmp_path, p, retries=retries)

        # Base overlay
        im_overlay = img.copy()
        draw = ImageDraw.Draw(im_overlay, "RGBA")
        for ln in res.lanes:
            draw.rectangle([ln.x0, ln.y0, ln.x1, ln.y1], outline=(0,0,0,160), width=2, fill=(0,255,0,40) if ln.type!="marker" else (0,0,255,40))
            cx = int((ln.x0 + ln.x1)/2)
            draw.line([(cx, ln.y0), (cx, ln.y1)], fill=(0,0,0,120), width=1)
            draw.text((cx+3, ln.y0+3), f"{ln.index}", fill=(0,0,0,200))

        # Step 1: ladder lanes
        st.subheader("Step 1 — Mark ladder lanes")
        ladder_idxs = []
        if CANVAS_OK:
            canvas_w = min(900, img.width)
            scale = canvas_w / img.width
            canvas_res = st_canvas(
                fill_color="rgba(255, 0, 0, 0.3)",
                stroke_color="red",
                background_image=im_overlay.resize((int(img.width*scale), int(img.height*scale))),
                height=int(img.height*scale),
                width=int(img.width*scale),
                drawing_mode="point",
                point_display_radius=6,
                key="ladder_select_canvas_v5",
            )
            if canvas_res.json_data is not None:
                objects = canvas_res.json_data.get("objects", [])
                click_xs = []
                for obj in objects:
                    x = float(obj.get("left", 0.0))
                    click_xs.append(x / scale)
                centers = [(ln.index, (ln.x0 + ln.x1)/2.0) for ln in res.lanes]
                for x in click_xs:
                    nearest = min(centers, key=lambda t: abs(t[1]-x))[0] if centers else None
                    if nearest and nearest not in ladder_idxs:
                        ladder_idxs.append(nearest)
            st.info(f"Ladder lanes: {ladder_idxs or '—'}")
            ladder_text = st.text_input("Or type ladder lanes", ",".join(map(str, ladder_idxs)))
        else:
            st.warning("Install `streamlit-drawable-canvas` to enable clicking. Using text input.")
            ladder_text = st.text_input("Ladder lanes (comma-separated)", "")

        ladder_final = [int(x) for x in ladder_text.split(",") if x.strip().isdigit()] if ladder_text else []

        # Compute MW/bp if ladders selected
        fit_info = compute_mw_or_bp(res, modality, ladder_final, ladder_type=ladder_type) if ladder_final else None
        im_labeled = draw_labels(img, res, modality=modality, conf_min=conf_min) if fit_info else im_overlay

        # Triptych
        cA, cB, cC = st.columns(3)
        with cA: st.image(img, caption="Original")
        with cB: st.image(im_overlay, caption="Overlay")
        with cC: st.image(im_labeled, caption=("Labeled (kDa/bp)" if fit_info else "Overlay (no labels)"))

        # Downloads
        buf_overlay = io.BytesIO(); im_overlay.save(buf_overlay, format="PNG")
        st.download_button("Download overlay PNG", data=buf_overlay.getvalue(), file_name="overlay.png", mime="image/png")
        if fit_info:
            buf_label = io.BytesIO(); im_labeled.save(buf_label, format="PNG")
            st.download_button("Download labeled PNG", data=buf_label.getvalue(), file_name="labeled_overlay.png", mime="image/png")

        # Persist session for other tabs
        set_state(img_path=str(tmp_path), modality=modality, res=res, obs=obs, params=p_final.__dict__,
                  ladder_indices=ladder_final, ladder_type=ladder_type, fit_info=fit_info, conf_min=conf_min,
                  im_overlay=im_overlay, im_labeled=im_labeled, im_original=img)

    with col2:
        st.subheader("Status & metrics")
        obs = get_state("obs")
        if obs:
            st.markdown(f"**Status:** {'✅ PASS' if obs.get('accepted') else '⚠️ REVIEW'}")
            st.json(obs)
        params = get_state("params")
        if params:
            st.subheader("Parameters used")
            st.json(params)

# ========================= Calibrate =========================
with tab_calib:
    st.subheader("Calibration (semi-log fit)")
    res = get_state("res"); fit_info = get_state("fit_info"); modality = get_state("modality")
    ladder_indices = get_state("ladder_indices"); ladder_type = get_state("ladder_type")
    obs = get_state("obs")
    if not (res and fit_info and ladder_indices and modality):
        st.info("Run analysis and select ladder lanes in the Analyze tab first.")
    else:
        from autodense.vision.mw_calibration import PROTEIN_LADDERS, DNA_LADDERS
        catalogs = PROTEIN_LADDERS if modality=="sds" else DNA_LADDERS
        ladder_name = ladder_type if (ladder_type and ladder_type!="auto") else fit_info["fit"]["ladder"]
        values = catalogs.get(ladder_name, [])
        dists = []
        H = res.image_size[1]
        for ln in res.lanes:
            if ln.index in ladder_indices and ln.bands:
                dists.extend(band_center_norm(ln, H))
        n = min(len(dists), len(values))
        d = np.array(dists[:n], dtype=np.float32)
        v = np.array(values[:n], dtype=np.float32)

        a = float(fit_info["fit"]["a"]); b = float(fit_info["fit"]["b"])
        vhat = np.power(10.0, a*d + b)
        resid = v - vhat
        pct_err = np.abs(resid) / np.maximum(v, 1e-6)

        # Quality checks
        st.markdown("**Quality checks**")
        thr = st.slider("Residual warning threshold (%)", 1, 50, 10, 1) / 100.0
        mean_pct = float(np.mean(pct_err)) if len(pct_err)>0 else 0.0
        max_pct = float(np.max(pct_err)) if len(pct_err)>0 else 0.0
        bad = (mean_pct > thr) or (max_pct > 2*thr)
        if bad:
            st.error(f"Calibration looks off: mean % error={mean_pct*100:.1f}%, max % error={max_pct*100:.1f}% (threshold={thr*100:.0f}%, max threshold={2*thr*100:.0f}%).")
        else:
            st.success(f"Calibration looks good: mean % error={mean_pct*100:.1f}%, max % error={max_pct*100:.1f}%.")

        # Suggestions when bad
        st.subheader("Suggested actions")
        suggested = False
        if bad:
            # (1) Ladder type nudge
            if modality=="sds" and ladder_name != "pageruler_10_180":
                suggested = True
                if st.button("Use ladder: PageRuler 10–180 kDa"):
                    set_state(ui_ladder_type="pageruler_10_180")
                    st.info("Preset applied. Go back to Analyze and re-run.")                
            if modality=="dna" and ladder_name != "neb_1kb":
                suggested = True
                if st.button("Use ladder: NEB 1 kb"):
                    set_state(ui_ladder_type="neb_1kb")
                    st.info("Preset applied. Go back to Analyze and re-run.")
            # (2) Comb tightening based on observed lanes variability
            lane_cv = float(obs.get("lane_width_cv") or 0.0) if obs else 0.0
            if modality=="sds":
                lanes_obs = int(obs.get("lanes") or 0) if obs else 0
                target = min([10,12,15], key=lambda x: (abs(x-lanes_obs), x))
                suggested = True
                if st.button(f"Tighten comb to {target} (SDS)"):
                    set_state(ui_comb_choice=str(target))
                    st.info(f"Comb {target} applied. Return to Analyze and re-run.")
            else:
                lanes_obs = int(obs.get("lanes") or 0) if obs else 0
                target = 10 if abs(lanes_obs-10) <= abs(lanes_obs-20) else 20
                suggested = True
                if st.button(f"Tighten comb to {target} (DNA)"):
                    set_state(ui_comb_choice=str(target))
                    st.info(f"Comb {target} applied. Return to Analyze and re-run.")
            # (3) Insufficient ladder bands
            if n < 5:
                suggested = True
                st.warning("Fewer than 5 ladder bands detected. Ensure you selected actual ladder lanes and that the ladder is visible.")
        if not suggested:
            st.caption("No suggestions at this time.")

        # Table and plots omitted here to keep patch focused (use previous version's plotting if needed)

# ========================= Quantify =========================
with tab_quant:
    st.subheader("Quantification")
    res = get_state("res"); modality = get_state("modality"); conf_min = get_state("conf_min", 0.3)
    if not res:
        st.info("Run analysis in the Analyze tab first.")
    else:
        rows = []
        for ln in res.lanes:
            for b in ln.bands:
                if b.confidence < conf_min: continue
                row = {
                    "lane": ln.index, "lane_type": ln.type, "band": b.index,
                    "y0": b.y0, "y1": b.y1,
                    "intensity": float(b.intensity or 0.0), "confidence": float(b.confidence),
                }
                if modality=="sds" and hasattr(b,"mw_kda") and b.mw_kda is not None:
                    row["kDa"] = float(b.mw_kda)
                if modality=="dna" and hasattr(b,"bp") and b.bp is not None:
                    row["bp"] = float(b.bp)
                rows.append(row)
        if not rows:
            st.warning("No bands above confidence threshold. Lower the threshold in Analyze.")
        else:
            df = pd.DataFrame(rows).sort_values(["lane","band"]).reset_index(drop=True)
            lanes_sorted = sorted(df["lane"].unique())
            ref_lane = st.selectbox("Normalize intensities to lane", lanes_sorted, index=0)
            ref_sum = df[df["lane"]==ref_lane]["intensity"].sum() or 1.0
            df["norm_intensity"] = df["intensity"] / ref_sum
            st.dataframe(df, use_container_width=True)

            # Store for Manipulate tab
            set_state(quant_df=df)

            buf_csv = io.StringIO(); df.to_csv(buf_csv, index=False)
            st.download_button("Download bands CSV", data=buf_csv.getvalue(), file_name="bands_quant.csv", mime="text/csv")

# ========================= Manipulate =========================
with tab_manip:
    st.subheader("Manipulate (compare, fold-change, copy number)")
    df = get_state("quant_df")
    modality = get_state("modality","sds")
    if df is None or len(df)==0:
        st.info("Quantify first to populate the bands table.")
    else:
        st.markdown("### 1) Name your samples")
        lanes = sorted(df["lane"].unique())
        default_map = ",".join(f"{ln}:Sample{ln}" for ln in lanes)
        mapping_text = st.text_input("Lane→Sample mapping (e.g., `1:WT,2:MutA,3:MutB`)", value=get_state("lane_map", default_map))
        set_state(lane_map=mapping_text)
        name_map = {}
        try:
            for part in mapping_text.split(","):
                if ":" in part:
                    ln_s, nm = part.split(":",1)
                    ln = int(ln_s.strip()); nm = nm.strip()
                    if nm: name_map[ln] = nm
        except Exception:
            st.warning("Could not parse mapping; using defaults.")
        dfm = df.copy()
        dfm["sample"] = dfm["lane"].map(lambda x: name_map.get(x, f"Lane{x}"))
        st.dataframe(dfm, use_container_width=True)

        st.markdown("### 2) Compare samples (fold-change)")
        samples = sorted(dfm["sample"].unique())
        colc1, colc2, colc3 = st.columns(3)
        with colc1:
            A = st.selectbox("Sample A (denominator)", samples, index=0, key="cmp_A")
        with colc2:
            B = st.selectbox("Sample B (numerator)", samples, index=min(1,len(samples)-1), key="cmp_B")
        with colc3:
            select_mode = st.radio("Band selection", ["All bands","By index","By kDa/bp range"], index=0, horizontal=False)

        sel = pd.Series([True]*len(dfm))
        if select_mode == "By index":
            idx_text = st.text_input("Band indices (e.g., `1,2,3`)", value="1")
            idxs = {int(x.strip()) for x in idx_text.split(",") if x.strip().isdigit()}
            sel = dfm["band"].isin(list(idxs))
        elif select_mode == "By kDa/bp range":
            if modality=="sds":
                k0 = st.number_input("kDa min", 0.0, 1000.0, 20.0, 1.0)
                k1 = st.number_input("kDa max", 0.0, 1000.0, 80.0, 1.0)
                sel = (dfm.get("kDa", np.nan) >= k0) & (dfm.get("kDa", np.nan) <= k1)
            else:
                b0 = st.number_input("bp min", 0.0, 100000.0, 500.0, 10.0)
                b1 = st.number_input("bp max", 0.0, 100000.0, 3000.0, 10.0)
                sel = (dfm.get("bp", np.nan) >= b0) & (dfm.get("bp", np.nan) <= b1)

        df_sel = dfm[sel].copy()
        sumA = df_sel[df_sel["sample"]==A]["intensity"].sum()
        sumB = df_sel[df_sel["sample"]==B]["intensity"].sum()
        fc = (sumB / sumA) if sumA>0 else np.nan
        st.metric(label="Fold-change (B/A)", value=(f"{fc:.3f}" if np.isfinite(fc) else "na"),
                  delta=(f"{math.log(fc,2):+.2f} log2" if np.isfinite(fc) and fc>0 else ""))
        st.caption("Tip: fold-change uses raw intensity sums of selected bands. Use the Quantify tab to change normalization if needed.")

        st.markdown("### 3) Copy number (PCR, same lane)")
        lane_cn = st.selectbox("Lane", lanes, index=0, key="cn_lane")
        in_lane = dfm[dfm["lane"]==lane_cn]
        method = st.radio("Select bands by", ["Index (choose two)","By bp range"], index=0, horizontal=True)
        if method == "Index (choose two)":
            idxs_lane = sorted(in_lane["band"].unique())
            hk_idx = st.selectbox("Housekeeping band index", idxs_lane, index=0, key="hk_idx")
            tg_idx = st.selectbox("Target band index", idxs_lane, index=min(1, len(idxs_lane)-1), key="tg_idx")
            hk = in_lane[in_lane["band"]==hk_idx].iloc[0]
            tg = in_lane[in_lane["band"]==tg_idx].iloc[0]
        else:
            if modality=="dna":
                hk_min = st.number_input("Housekeeping bp min", 0.0, 100000.0, 400.0, 10.0)
                hk_max = st.number_input("Housekeeping bp max", 0.0, 100000.0, 600.0, 10.0)
                tg_min = st.number_input("Target bp min", 0.0, 100000.0, 800.0, 10.0)
                tg_max = st.number_input("Target bp max", 0.0, 100000.0, 1200.0, 10.0)
                hk = in_lane[(in_lane.get("bp", np.nan)>=hk_min)&(in_lane.get("bp", np.nan)<=hk_max)].sort_values("intensity", ascending=False).head(1)
                tg = in_lane[(in_lane.get("bp", np.nan)>=tg_min)&(in_lane.get("bp", np.nan)<=tg_max)].sort_values("intensity", ascending=False).head(1)
                hk = hk.iloc[0] if len(hk) else None
                tg = tg.iloc[0] if len(tg) else None
            else:
                hk_min = st.number_input("Housekeeping kDa min", 0.0, 1000.0, 35.0, 1.0)
                hk_max = st.number_input("Housekeeping kDa max", 0.0, 1000.0, 45.0, 1.0)
                tg_min = st.number_input("Target kDa min", 0.0, 1000.0, 50.0, 1.0)
                tg_max = st.number_input("Target kDa max", 0.0, 1000.0, 100.0, 1.0)
                hk = in_lane[(in_lane.get("kDa", np.nan)>=hk_min)&(in_lane.get("kDa", np.nan)<=hk_max)].sort_values("intensity", ascending=False).head(1)
                tg = in_lane[(in_lane.get("kDa", np.nan)>=tg_min)&(in_lane.get("kDa", np.nan)<=tg_max)].sort_values("intensity", ascending=False).head(1)
                hk = hk.iloc[0] if len(hk) else None
                tg = tg.iloc[0] if len(tg) else None

        if hk is not None and tg is not None:
            # Length-corrected intensity: EtBr brightness ~ mass ~ length; for proteins we skip length correction
            if modality=="dna":
                L_hk = float(hk.get("bp", np.nan) or np.nan)
                L_tg = float(tg.get("bp", np.nan) or np.nan)
                I_hk = float(hk["intensity"]); I_tg = float(tg["intensity"])
                norm_hk = I_hk / (L_hk if np.isfinite(L_hk) and L_hk>0 else 1.0)
                norm_tg = I_tg / (L_tg if np.isfinite(L_tg) and L_tg>0 else 1.0)
                ratio = norm_tg / norm_hk if norm_hk>0 else np.nan
            else:
                ratio = float(tg["intensity"]) / float(hk["intensity"]) if float(hk["intensity"])>0 else np.nan

            expected_hk = st.number_input("Expected housekeeping copies", 0.0, 1000.0, 1.0, 1.0)
            eff = st.slider("PCR efficiency factor (1.0 = assume equal & linear)", 0.5, 1.5, 1.0, 0.01) if modality=="dna" else 1.0
            copy_number = ratio * expected_hk / eff if np.isfinite(ratio) else np.nan

            st.metric("Estimated copy number (target)", f"{copy_number:.2f}" if np.isfinite(copy_number) else "na")
            st.caption("Assumes comparable amplification and staining; DNA estimate length-corrects intensities. For publication-grade copy number, prefer qPCR or digital PCR.")
        else:
            st.info("Select valid housekeeping and target bands to estimate copy number.")

        # Save results for export
        results = {
            "fold_change": {"A": A, "B": B, "selection": select_mode, "fc": (float(fc) if np.isfinite(fc) else None)},
            "copy_number": {
                "lane": int(lane_cn),
                "modality": modality,
                "estimate": (float(copy_number) if 'copy_number' in locals() and np.isfinite(copy_number) else None)
            },
            "mapping": name_map
        }
        set_state(manip_results=results)

        # Export
        st.markdown("### 4) Export manipulations")
        bufj = io.StringIO(); json.dump(results, bufj, indent=2)
        st.download_button("Download manipulations JSON", data=bufj.getvalue(), file_name="manipulations.json", mime="application/json")
        
        # Custom Recipe Engine
        st.markdown("---")
        st.markdown("### 5) Custom Recipe Engine")
        st.caption("Create and execute custom analytical workflows with safety validation")
        
        # Initialize recipe registry
        if "recipe_registry" not in st.session_state:
            st.session_state.recipe_registry = RecipeRegistry()
        
        registry = st.session_state.recipe_registry
        
        # Recipe mode selection
        recipe_mode = st.radio("Recipe mode", ["Execute Preset", "Execute Custom", "Create Custom", "Manage Recipes"], horizontal=True)
        
        if recipe_mode == "Execute Preset":
            presets = registry.list_presets()
            preset_info = registry.get_preset_info()
            
            selected_preset = st.selectbox("Select preset recipe", presets, index=0)
            if selected_preset:
                preset = registry.get_preset(selected_preset)
                st.markdown(f"**{preset.name}**")
                st.caption(preset.description)
                
                # Show steps
                with st.expander("Recipe steps", expanded=False):
                    for i, step in enumerate(preset.steps):
                        st.write(f"{i+1}. **{step.operation}** with parameters: {step.parameters}")
                
                # Extract intensity values for execution
                if st.button("Execute Preset Recipe", type="primary"):
                    intensity_values = df_sel["intensity"].tolist()
                    if intensity_values:
                        result = registry.execute_recipe(selected_preset, intensity_values, is_preset=True)
                        if result.success:
                            execution_result = result.data
                            st.success(f"Recipe executed successfully!")
                            
                            # Display results
                            for i, step_result in enumerate(execution_result.step_results):
                                st.write(f"Step {i+1} result: {step_result.message}")
                                if step_result.warnings:
                                    for warning in step_result.warnings:
                                        st.warning(f"Step {i+1}: {warning}")
                            
                            st.json(execution_result.final_result)
                            
                            # Execution log
                            with st.expander("Execution log"):
                                for log_entry in execution_result.execution_log:
                                    st.text(log_entry)
                        else:
                            st.error(f"Recipe execution failed: {result.message}")
                    else:
                        st.error("No intensity data available. Select bands first.")
        
        elif recipe_mode == "Execute Custom":
            custom_recipes = registry.list_custom_recipes()
            if not custom_recipes:
                st.info("No custom recipes found. Create one first!")
            else:
                recipe_names = [r["name"] for r in custom_recipes]
                selected_custom = st.selectbox("Select custom recipe", recipe_names)
                
                if selected_custom:
                    recipe_info = next((r for r in custom_recipes if r["name"] == selected_custom), None)
                    st.markdown(f"**{selected_custom}**")
                    st.caption(f"Created: {recipe_info['created_at']}, Steps: {recipe_info['steps']}")
                    
                    if st.button("Execute Custom Recipe", type="primary"):
                        intensity_values = df_sel["intensity"].tolist()
                        if intensity_values:
                            result = registry.execute_recipe(selected_custom, intensity_values, is_preset=False)
                            if result.success:
                                execution_result = result.data
                                st.success(f"Recipe executed successfully!")
                                
                                # Display results
                                for i, step_result in enumerate(execution_result.step_results):
                                    st.write(f"Step {i+1} result: {step_result.message}")
                                    if step_result.warnings:
                                        for warning in step_result.warnings:
                                            st.warning(f"Step {i+1}: {warning}")
                                
                                st.json(execution_result.final_result)
                                
                                # Execution log
                                with st.expander("Execution log"):
                                    for log_entry in execution_result.execution_log:
                                        st.text(log_entry)
                            else:
                                st.error(f"Recipe execution failed: {result.message}")
                        else:
                            st.error("No intensity data available. Select bands first.")
        
        elif recipe_mode == "Create Custom":
            st.markdown("#### Build Custom Recipe")
            
            # Recipe metadata
            recipe_name = st.text_input("Recipe name", placeholder="My Custom Analysis")
            recipe_desc = st.text_area("Description", placeholder="Describe what this recipe does...")
            
            # Available operations
            ops_info = registry.engine.get_operation_info()
            
            # Initialize steps in session state
            if "custom_recipe_steps" not in st.session_state:
                st.session_state.custom_recipe_steps = []
            
            st.markdown("#### Recipe Steps")
            
            # Add step interface
            with st.expander("Add New Step", expanded=len(st.session_state.custom_recipe_steps) == 0):
                op_names = list(ops_info.keys())
                selected_op = st.selectbox("Operation", op_names, key="new_step_op")
                
                # Dynamic parameter inputs based on operation
                params = {}
                if selected_op == "normalize_to_control":
                    params["control_idx"] = st.number_input("Control index", 0, 20, 0, key="param_control_idx")
                elif selected_op == "fold_change":
                    st.info("Fold change requires two input arrays. This will use treated=input and control from step reference.")
                elif selected_op == "outlier_detection":
                    params["method"] = st.selectbox("Method", ["iqr", "zscore"], key="param_outlier_method")
                    params["threshold"] = st.number_input("Threshold", 0.1, 5.0, 1.5, 0.1, key="param_outlier_thresh")
                elif selected_op == "log_transform":
                    params["base"] = st.number_input("Base", 1.1, 10.0, 2.0, 0.1, key="param_log_base")
                elif selected_op == "quality_filter":
                    params["min_val"] = st.number_input("Min value (optional)", 0.0, 10000.0, 0.0, key="param_min_val") or None
                    params["max_val"] = st.number_input("Max value (optional)", 0.0, 100000.0, 1000.0, key="param_max_val") or None
                    params["max_cv"] = st.number_input("Max CV % (optional)", 0.0, 100.0, 20.0, key="param_max_cv") or None
                
                input_source = st.selectbox("Input source", ["previous", "original"], key="new_step_input")
                step_id = st.text_input("Step ID (optional)", placeholder="e.g., normalized", key="new_step_id")
                
                if st.button("Add Step"):
                    new_step = RecipeStep(
                        operation=selected_op,
                        parameters=params,
                        input_source=input_source,
                        step_id=step_id if step_id else None
                    )
                    st.session_state.custom_recipe_steps.append(new_step)
                    st.rerun()
            
            # Display current steps
            if st.session_state.custom_recipe_steps:
                st.markdown("#### Current Steps")
                for i, step in enumerate(st.session_state.custom_recipe_steps):
                    col1, col2 = st.columns([4, 1])
                    with col1:
                        st.write(f"{i+1}. **{step.operation}** ({step.input_source})")
                        st.caption(f"Parameters: {step.parameters}")
                    with col2:
                        if st.button("Remove", key=f"remove_step_{i}"):
                            st.session_state.custom_recipe_steps.pop(i)
                            st.rerun()
                
                # Save recipe
                col1, col2 = st.columns(2)
                with col1:
                    if st.button("Save Recipe", type="primary"):
                        if recipe_name and st.session_state.custom_recipe_steps:
                            custom_recipe = Recipe(
                                name=recipe_name,
                                description=recipe_desc or f"Custom recipe with {len(st.session_state.custom_recipe_steps)} steps",
                                steps=st.session_state.custom_recipe_steps.copy()
                            )
                            
                            result = registry.save_custom_recipe(custom_recipe)
                            if result.success:
                                st.success(f"Recipe '{recipe_name}' saved successfully!")
                                st.session_state.custom_recipe_steps = []  # Clear steps
                            else:
                                st.error(f"Failed to save recipe: {result.message}")
                        else:
                            st.error("Recipe name and at least one step required")
                
                with col2:
                    if st.button("Clear Steps"):
                        st.session_state.custom_recipe_steps = []
                        st.rerun()
        
        elif recipe_mode == "Manage Recipes":
            st.markdown("#### Recipe Management")
            
            # List custom recipes
            custom_recipes = registry.list_custom_recipes()
            if custom_recipes:
                st.markdown("**Custom Recipes:**")
                for recipe in custom_recipes:
                    col1, col2 = st.columns([3, 1])
                    with col1:
                        st.write(f"• **{recipe['name']}** - {recipe['description']}")
                        st.caption(f"Created: {recipe['created_at']}, Steps: {recipe['steps']}")
                    with col2:
                        if st.button("Delete", key=f"del_{recipe['filename']}"):
                            result = registry.delete_custom_recipe(recipe['name'])
                            if result.success:
                                st.success(f"Deleted '{recipe['name']}'")
                                st.rerun()
                            else:
                                st.error(f"Delete failed: {result.message}")
            else:
                st.info("No custom recipes found.")
            
            # List presets
            st.markdown("**Built-in Presets:**")
            preset_info = registry.get_preset_info()
            for name, info in preset_info.items():
                st.write(f"• **{info['name']}** ({info['category']}) - {info['description']}")
        
        st.markdown("---")

# ========================= Assist =========================
with tab_assist:
    st.subheader("Assist — natural-language analysis")
    df = get_state("quant_df")
    if df is None or len(df)==0:
        st.info("Quantify first so I have a table to work with.")
    else:
        lanes = sorted(df["lane"].unique())
        default_map = get_state("lane_map", ",".join(f"{ln}:Sample{ln}" for ln in lanes))
        name_map = {}
        try:
            for part in default_map.split(","):
                if ":" in part:
                    ln_s, nm = part.split(":",1)
                    name_map[int(ln_s.strip())] = nm.strip()
        except Exception:
            pass
        dfm = df.copy()
        dfm["sample"] = dfm["lane"].map(lambda x: name_map.get(x, f"Lane{x}"))
        samples = sorted(dfm["sample"].unique())

        prompt = st.text_area("Ask in plain English", placeholder="compare WT vs MutA in 45–60 kDa; show fold change")
        if st.button("Translate & run", disabled=(not prompt.strip())):
            recipe = parse_prompt_to_recipe(prompt, samples)
            if not recipe:
                st.error("Couldn't parse that into a safe recipe. Try 'compare A vs B in 45–60 kDa'.")
            else:
                st.code(json.dumps(recipe, indent=2), language="json")
                errs = val_recipe(recipe)
                if errs:
                    st.error("; ".join(errs))
                else:
                    res = exec_recipe(recipe, dfm)
                    ctx = res["ctx"]
                    ag = ctx.get("aggregates", {}).get("sum_by_sample", {})
                    if ag:
                        st.write("Sums by sample:", ag)
                    fc = ctx['metrics'].get('fold_change')
                    log2_fc = ctx['metrics'].get('log2_fc')
                    st.metric("Fold-change (B/A)", 
                             f"{fc:.6g}" if fc is not None else "na",
                             delta=(f"{log2_fc:+.3f} log2" if log2_fc is not None else ""))

# ========================= Stats =========================
with tab_stats:
    import math, numpy as np, pandas as pd, matplotlib.pyplot as plt
    st.subheader("Replicates & stats")
    df = get_state("quant_df")
    if df is None or len(df)==0:
        st.info("Quantify first to populate the bands table.")
    else:
        lanes = sorted(df["lane"].unique())
        default_map = get_state("lane_map", ",".join(f"{ln}:Sample{ln}" for ln in lanes))
        name_map = {}
        try:
            for part in default_map.split(","):
                if ":" in part:
                    ln_s, nm = part.split(":",1)
                    name_map[int(ln_s.strip())] = nm.strip()
        except Exception:
            pass
        dfm = df.copy()
        dfm["sample"] = dfm["lane"].map(lambda x: name_map.get(x, f"Lane{x}"))

        use_range = st.checkbox("Filter band range", value=False)
        modality = get_state("modality","sds")
        if use_range:
            if modality=="sds":
                k0 = st.number_input("kDa min", 0.0, 1000.0, 20.0, 1.0)
                k1 = st.number_input("kDa max", 0.0, 1000.0, 80.0, 1.0)
                mask = (dfm.get("kDa", np.nan)>=k0)&(dfm.get("kDa", np.nan)<=k1)
            else:
                b0 = st.number_input("bp min", 0.0, 100000.0, 500.0, 10.0)
                b1 = st.number_input("bp max", 0.0, 100000.0, 3000.0, 10.0)
                mask = (dfm.get("bp", np.nan)>=b0)&(dfm.get("bp", np.nan)<=b1)
            dfm = dfm[mask].copy()

        per_lane = dfm.groupby(["sample","lane"])["intensity"].sum().reset_index(name="sum_intensity")
        st.dataframe(per_lane, use_container_width=True)

        samples = sorted(per_lane["sample"].unique())
        A = st.selectbox("Group A", samples, index=0)
        B = st.selectbox("Group B", samples, index=min(1,len(samples)-1) if len(samples)>1 else 0)

        valsA = per_lane[per_lane["sample"]==A]["sum_intensity"].values
        valsB = per_lane[per_lane["sample"]==B]["sum_intensity"].values

        meanA = float(np.mean(valsA)) if len(valsA)>0 else float("nan")
        meanB = float(np.mean(valsB)) if len(valsB)>0 else float("nan")
        sdA = float(np.std(valsA, ddof=1)) if len(valsA)>1 else float("nan")
        sdB = float(np.std(valsB, ddof=1)) if len(valsB)>1 else float("nan")

        def welch_t(a, b):
            a = np.asarray(a, dtype=float)
            b = np.asarray(b, dtype=float)
            ma, mb = np.mean(a), np.mean(b)
            sa2 = np.var(a, ddof=1) if len(a)>1 else 0.0
            sb2 = np.var(b, ddof=1) if len(b)>1 else 0.0
            na, nb = len(a), len(b)
            if na==0 or nb==0:
                return float("nan"), float("nan")
            denom = math.sqrt(sa2/na + sb2/nb) if sa2>0 or sb2>0 else float("nan")
            t = (mb - ma)/denom if denom and denom>0 else float("nan")
            num = (sa2/na + sb2/nb)**2
            den = (sa2**2)/(na**2*(na-1 if na>1 else 1)) + (sb2**2)/(nb**2*(nb-1 if nb>1 else 1))
            dof = num/den if den>0 else float("nan")
            return t, dof

        tstat, dof = welch_t(valsA, valsB)
        st.markdown(f"**A** (n={len(valsA)}): mean={meanA:.3g}, SD={sdA if sdA==sdA else float('nan'):.3g}  \n"
                    f"**B** (n={len(valsB)}): mean={meanB:.3g}, SD={sdB if sdB==sdB else float('nan'):.3g}")
        st.markdown(f"**Welch's t**: t≈{tstat if tstat==tstat else float('nan'):.3g}, dof≈{dof if dof==dof else float('nan'):.1f}")

        fig = plt.figure()
        x = np.arange(2)
        means = [meanA, meanB]
        sds = [0 if sdA!=sdA else sdA, 0 if sdB!=sdB else sdB]
        plt.bar(x, means, yerr=sds, capsize=6)
        plt.xticks(x, [A,B])
        plt.ylabel("Sum intensity per lane")
        st.pyplot(fig)

# ========================= Save =========================
with tab_save:
    st.subheader("Save analysis")
    df = get_state("quant_df")
    params = get_state("params")
    obs = get_state("obs")
    if df is None or len(df)==0:
        st.info("Quantify something first.")
    else:
        name = st.text_input("Analysis name", value="analysis_1")
        notes = st.text_area("Notes (optional)", placeholder="e.g., WT vs MutA in 45–60 kDa; residuals good; ladder PageRuler.")
        payload = {"name": name, "notes": notes, "params": params, "metrics": obs}
        bufj = io.StringIO()
        json.dump(payload, bufj, indent=2)
        st.download_button("Download analysis JSON", data=bufj.getvalue(), file_name=f"{name}.analysis.json", mime="application/json")

        st.markdown("**Merge into manifest**")
        manifest_upl = st.file_uploader("Upload existing manifest.json to append this analysis (optional)", type=["json"])
        if manifest_upl and st.button("Produce updated manifest"):
            man = json.loads(manifest_upl.read())
            man.setdefault("analyses", []).append(payload)
            bufm = io.StringIO()
            json.dump(man, bufm, indent=2)
            st.download_button("Download updated manifest.json", data=bufm.getvalue(), file_name="manifest.updated.json", mime="application/json")

# ========================= Labeler =========================
with tab_label:
    st.subheader("Lane labeler — text or voice-assisted (beta)")
    res = get_state("res")
    img = get_state("im_original")
    if not res or img is None:
        st.info("Run analysis first to detect lanes.")
    else:
        labels = [st.text_input(f"Lane {ln.index} label", value=f"Lane{ln.index}") for ln in res.lanes]
        auto_fit = st.checkbox("Auto-fit font and avoid overlaps", value=True)
        if st.button("Render labels"):
            lanes = [(ln.x0, ln.y0, ln.x1, ln.y1) for ln in res.lanes]
            im_labeled = place_labels(img, lanes, labels, y_pad=4, avoid_overlap=auto_fit)
            buf = io.BytesIO()
            im_labeled.save(buf, format="PNG")
            st.image(im_labeled, caption="Labeled (auto-fit)")
            st.download_button("Download labeled PNG", data=buf.getvalue(), file_name="labeled_with_text.png", mime="image/png")

        st.caption("Voice mode: paste a transcript like "label lane 1 as WT; label lane 2 as MutA; label lane 10 as Ladder"")
        transcript = st.text_area("Voice transcript (paste text)")
        if st.button("Apply transcript"):
            import re
            new_labels = labels[:]
            for cmd in re.split(r'[;,\n]+', transcript):
                m = re.search(r'labe?l\s*lane\s*(\d+)\s*as\s*(.+)', cmd.strip(), re.I)
                if m:
                    idx = int(m.group(1))
                    text = m.group(2).strip()
                    for i, ln in enumerate(res.lanes):
                        if ln.index == idx:
                            new_labels[i] = text
            st.success("Labels updated from transcript. Click Render to see them.")
            for i, ln in enumerate(res.lanes):
                st.text_input(f"Lane {ln.index} label", value=new_labels[i], key=f"voice_label_{i}")

# ========================= History =========================
with tab_hist:
    st.subheader("Run history")
    man_file = st.file_uploader("Load a manifest.json", type=["json"], key="manifest_uploader")
    if man_file:
        man = json.loads(man_file.read())
        entries = man.get("images", [])
        if not entries:
            st.warning("No images listed in manifest.")
        else:
            st.success(f"Loaded manifest with {len(entries)} images (modality={man.get('modality')})")
            dfm = pd.DataFrame([{
                "file": e.get("file"),
                "accepted": e.get("accepted"),
                "lanes": e.get("metrics", {}).get("lanes"),
                "markers": e.get("metrics", {}).get("markers"),
                "empty_frac": e.get("metrics", {}).get("empty_frac"),
                "lane_width_cv": e.get("metrics", {}).get("lane_width_cv"),
                "ladder": (e.get("fit",{}).get("fit",{}).get("ladder") if e.get("fit") else None),
                "r2": (e.get("fit",{}).get("fit",{}).get("r2") if e.get("fit") else None),
            } for e in entries])
            st.dataframe(dfm, use_container_width=True)
            st.caption("Tip: the manifest is an index; raw image and overlays are referenced by path fields in each entry.")
