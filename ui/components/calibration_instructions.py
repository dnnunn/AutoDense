"""Calibration Instructions Component for AutoDense.

This component provides detailed instructions and best practices for
two-point lane calibration in gel electrophoresis analysis.
"""

import streamlit as st


def render_calibration_instructions(expanded: bool = False) -> None:
    """
    Render calibration instructions panel with step-by-step guidance.

    Args:
        expanded: Whether to show the instructions expanded by default

    Side Effects:
        Displays calibration instructions and best practices in Streamlit UI
    """

    with st.expander("📖 Calibration Instructions & Best Practices", expanded=expanded):
        col1, col2 = st.columns([2, 1])

        with col1:
            st.markdown("""
            **Step-by-step calibration:**

            1. **Identify reference lanes**: Choose two lanes with distinct, well-defined bands
            2. **First calibration point**: Click the center of a clear band in your MW standard (typically lane 1)
            3. **Second calibration point**: Click the center of another clear band in a different lane, preferably far from the first
            4. **Verify accuracy**: Check that the calculated lane boundaries align with your gel

            **For best accuracy:**
            - Choose bands that are sharp and well-separated from neighbors
            - Avoid bands at the very top or bottom of the gel
            - Select lanes that span a good portion of the gel width
            - Click precisely on the band centers, not the edges
            """)

        with col2:
            st.markdown("""
            **Common mistakes to avoid:**
            - Clicking on band edges instead of centers
            - Using distorted or unclear bands
            - Choosing calibration points too close together
            - Using bands from the same lane
            - Calibrating near gel artifacts or bubbles
            """)