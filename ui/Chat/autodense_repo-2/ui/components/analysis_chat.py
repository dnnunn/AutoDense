from __future__ import annotations

import os
import json
import streamlit as st

from services.llm_router import LLMRouter
from services import tools as toolkit

SYSTEM_TOOL_SPEC = """
You are an analysis assistant embedded in a gel densitometry app.
You have access to the following tools by returning a SINGLE JSON object:
{
  "tool": "<one of: summarize_results | export_report | rerun_analysis | sweep_parameters>",
  "args": { ... }   // arguments for the tool
}

If the user asks for a preset or structured action, respond ONLY with that JSON object.
If the user is just chatting or asking for explanation, respond in plain text.

Examples:
- {"tool": "summarize_results", "args": {}}
- {"tool": "export_report", "args": {"path":"reports/run_001.xlsx"}}
- {"tool": "rerun_analysis", "args": {"deskew_deg": 0.5, "desmile_px": 6}}
- {"tool": "sweep_parameters", "args": {"deskew_deg":[-1.0, -0.5, 0.0, 0.5, 1.0], "desmile_px":[0, 4, 8]}} 
"""

def _ensure_state():
    if "chat_messages" not in st.session_state:
        st.session_state.chat_messages = []  # list of {"role": "user"/"assistant", "content": str}

def render_analysis_chat(results: dict | None, presets: list[dict] | None = None):
    """
    Renders a chat panel that unlocks once analysis results exist.
    - results: dict-like analysis artifact placed into session_state by your pipeline.
    - presets: optional list of {"label": str, "json_command": dict} to show as one-click actions.
    """
    _ensure_state()

    st.subheader("💬 Analysis Chat")

    if not results:
        st.info("Chat unlocks after analysis runs successfully. No results detected yet.")
        return

    # Optional preset actions
    with st.expander("Preset actions", expanded=True):
        default_presets = [
            {"label": "Summarize results", "json_command": {"tool": "summarize_results", "args": {}}},
            {"label": "Export report (Excel)", "json_command": {"tool": "export_report", "args": {"path": "reports/autodense_report.xlsx"}}},
            {"label": "Rerun with gentle deskew/desmile", "json_command": {"tool": "rerun_analysis", "args": {"deskew_deg": 0.5, "desmile_px": 6}}},
            {"label": "Parameter sweep (deskew ±1°, desmile 0/4/8)", "json_command": {"tool": "sweep_parameters", "args": {"deskew_deg":[-1.0,-0.5,0.0,0.5,1.0],"desmile_px":[0,4,8]}}},
        ]
        for item in (presets or default_presets):
            if st.button(item["label"]):
                cmd_text = json.dumps(item["json_command"])
                out = toolkit.dispatch_json_command(cmd_text, results)
                st.session_state.chat_messages.append({"role":"user", "content": f"[Preset] {item['label']}"})
                st.session_state.chat_messages.append({"role":"assistant", "content": out})

    # Render history
    for msg in st.session_state.chat_messages[-200:]:
        with st.chat_message(msg["role"]):
            st.markdown(msg["content"])

    # Chat input
    user_text = st.chat_input("Ask about the analysis, or request an action…")
    if user_text:
        st.session_state.chat_messages.append({"role": "user", "content": user_text})
        # Try a JSON tool call first
        router = None
        content = None
        try:
            router = LLMRouter()
        except Exception:
            router = None

        took_tool = False
        if router is not None:
            # First, try to get a JSON command
            try:
                content = router.chat(
                    messages=[
                        {"role": "system", "content": SYSTEM_TOOL_SPEC},
                        *st.session_state.chat_messages,
                        {"role": "system", "content": "If a tool is appropriate, answer ONLY with a single JSON object."},
                    ],
                    temperature=0.2,
                    json_mode=True,
                )
                # If it looks like JSON, dispatch it; else fall through to plain text.
                json.loads(content)  # validate
                took_tool = True
            except Exception:
                took_tool = False

        if took_tool and content:
            out = toolkit.dispatch_json_command(content, results)
            st.session_state.chat_messages.append({"role": "assistant", "content": out})
        else:
            # Either no LLM configured or freeform answer
            if router is not None:
                try:
                    content = router.chat(
                        messages=[{"role":"system","content":"Be concise, accurate, and practical."}] + st.session_state.chat_messages,
                        temperature=0.2,
                        json_mode=False,
                    )
                except Exception as e:
                    content = f"(LLM unavailable: {e})"
            else:
                content = "(LLM not configured. Set OPENAI_API_KEY or OPENAI_BASE_URL to enable model responses.)"
            st.session_state.chat_messages.append({"role": "assistant", "content": content})
