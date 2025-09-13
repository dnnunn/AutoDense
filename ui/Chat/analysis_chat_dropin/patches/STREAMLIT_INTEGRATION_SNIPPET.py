# --- Integration snippet (put this in your main Streamlit app after analysis completes) ---
# 1) Ensure that after your pipeline finishes, you stash results:
#    st.session_state["analysis_results"] = {...}  # your dict-like results
# 2) Then render a dedicated tab or section:
#
# Example with tabs (avoid 'key' argument in st.tabs for Streamlit<=1.49):
#
# tabs = st.tabs(["🧪 Lane Calibration", "🔬 Analysis & Processing", "📊 Results & Export", "💬 Chat"])
# with tabs[3]:
#     from ui.components.analysis_chat import render_analysis_chat
#     render_analysis_chat(st.session_state.get("analysis_results"))
#
# Done.
