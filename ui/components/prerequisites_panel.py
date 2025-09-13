"""Prerequisites Status Panel Component for AutoDense.

This component displays the status of prerequisites (image upload and calibration)
required before analysis can proceed.
"""

import streamlit as st
from typing import Callable, Optional


def render_prerequisites_panel(goto_tab: Optional[Callable[[str], None]] = None) -> bool:
    """
    Render prerequisites status panel showing image and calibration status.

    Args:
        goto_tab: Optional function to navigate to specific tabs

    Returns:
        bool: True if all prerequisites are met, False otherwise

    Side Effects:
        Displays status information and navigation buttons in Streamlit UI
    """

    # Prerequisites validation with detailed feedback
    has_image = st.session_state.res_uploaded_image is not None
    has_calibration = st.session_state.res_lane_boundaries is not None

    # Prerequisites status panel
    st.markdown("### 📋 Prerequisites Status")

    col1, col2 = st.columns(2)

    with col1:
        if has_image:
            metadata = st.session_state.res_image_metadata or {}
            filename = metadata.get('filename', 'Unknown')
            dimensions = metadata.get('dimensions', (0, 0))
            st.success(f"✅ **Image Ready:** {filename}")
            st.caption(f"📐 Dimensions: {dimensions[0]}×{dimensions[1]}px")
        else:
            st.error("❌ **Image Missing**")
            st.caption("👈 Upload gel image in **Lane Calibration** tab")

    with col2:
        if has_calibration:
            n_boundaries = len(st.session_state.res_lane_boundaries)
            st.success(f"✅ **Lanes Calibrated:** {n_boundaries} lanes configured")
            st.caption(f"🎯 Ready for {st.session_state.params_gel_type.upper()} analysis")
        else:
            st.error("❌ **Calibration Missing**")
            st.caption("👈 Complete lane setup in **Lane Calibration** tab")

    # Only proceed if prerequisites are met
    if not has_image or not has_calibration:
        st.markdown("---")
        st.info("🔒 **Analysis locked until prerequisites are completed.**")
        if goto_tab:
            st.button("⬅️ Go to Calibration", on_click=lambda: goto_tab("🎯 Lane Calibration"), use_container_width=True)

        # Show what will be available
        with st.expander("🔮 Analysis Capabilities (Available After Prerequisites)", expanded=True):
            st.markdown("""
            **Preprocessing Options:**
            - 🧠 **AI-Guided:** Automatic image optimization using computer vision
            - 🤖 **ChatGPT-4.1:** Advanced AI analysis with reasoning (premium)
            - 🛠️ **Manual:** Custom preprocessing parameters
            - 📷 **Raw:** No preprocessing (analyze original image)

            **Analysis Features:**
            - 🎯 Lane-aware band detection using your calibration
            - 📏 Molecular weight calibration with standard curves
            - 📊 Quantitative band intensity analysis
            - 🧮 Statistical significance testing
            - 📈 Interactive results visualization
            """)

        return False  # Prerequisites not met

    return True  # All prerequisites met