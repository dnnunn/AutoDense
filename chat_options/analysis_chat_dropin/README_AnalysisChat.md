# Analysis Chat — Streamlit Drop‑In

This adds a post‑analysis chat panel that supports:
- One‑click **preset actions** (summaries, exports, parameter sweeps, gentle re‑runs).
- Free‑form **chat** with an OpenAI‑compatible model (OpenAI or LM Studio).
- **Structured tool calls** via lightweight JSON so the model can trigger your functions.

## Install

```bash
pip install openai>=1.0.0
```

> For local models via LM Studio: set `OPENAI_BASE_URL=http://127.0.0.1:1234/v1` and choose a model that LM Studio serves.

## Configure

Environment variables (pick one path):

- **OpenAI Cloud**
  - `OPENAI_API_KEY=...`
  - `OPENAI_MODEL=gpt-4o-mini` (or your choice)

- **LM Studio (local)**
  - `OPENAI_BASE_URL=http://127.0.0.1:1234/v1`
  - `OPENAI_MODEL=Qwen2.5-VL-7B-Instruct` (or whatever you served)
  - `OPENAI_API_KEY=not-needed` (LM Studio ignores it, but the client requires a string)

## Wire your real tools

`services/tools.py` dispatches to optional callbacks in `st.session_state["analysis_toolkit_callbacks"]`:

```python
st.session_state["analysis_toolkit_callbacks"] = {
    "summarize_results": lambda results: my_summary_fn(results),
    "export_report":     lambda results, path: my_export_fn(results, path),
    "rerun_analysis":    lambda params: my_rerun_fn(params),      # returns new results dict
    "sweep_parameters":  lambda grid: my_sweep_fn(grid),          # returns sweep results
}
```

If you don’t provide these, sensible demo fallbacks run so the UI doesn’t crash.

## Integrate in your app

1. After your main analysis completes, stash a dict‑like results object:

```python
st.session_state["analysis_results"] = results_dict
```

2. Add a new **Chat** tab (don’t pass `key` to `st.tabs` for Streamlit ≤ 1.49 to avoid errors):

```python
tabs = st.tabs(["🧪 Lane Calibration", "🔬 Analysis & Processing", "📊 Results & Export", "💬 Chat"])
with tabs[3]:
    from ui.components.analysis_chat import render_analysis_chat
    render_analysis_chat(st.session_state.get("analysis_results"))
```

3. (Optional) Provide **presets** tailored to your app:

```python
custom_presets = [
    {"label": "Deskew sweep ±2°", "json_command": {"tool": "sweep_parameters", "args": {"deskew_deg":[-2,-1,0,1,2]}}},
    {"label": "Export Excel -> results/run42.xlsx", "json_command": {"tool": "export_report", "args": {"path":"results/run42.xlsx"}}},
]
render_analysis_chat(st.session_state.get("analysis_results"), presets=custom_presets)
```

## How tool calls work

The system prompt asks the model to return a **single JSON object** for actionable requests, e.g.:

```json
{"tool": "rerun_analysis", "args": {"deskew_deg": 0.5, "desmile_px": 6}}
```

The UI parses this and calls the matching function from `services/tools.py`. For normal “explain this” questions, the model replies in plain text.

## Safety & UX notes

- The chat **locks** until results exist.
- History is kept in `st.session_state["chat_messages"]` (truncate as needed).
- If the model returns invalid JSON, we fall back to plain text gracefully.
- No assumptions about your internal data shape—just pass a dict‑like `results` and wire callbacks.
- Keeps Streamlit 1.49 quirks in mind (e.g., `st.tabs` has no `key` parameter).

## Quick grep to see if you already have chat

Run this in your repo root:

```bash
grep -RinE "st\.chat_input|st\.chat_message|ChatOpenAI|ChatAnthropic|openai\.ChatCompletion|lm studio|OPENAI_BASE_URL" .
```

If you see existing hits, you may already have a chat scaffold; drop this module in and migrate your callbacks rather than duplicating.


## Post-analysis quantitative tools

The chat can now trigger these actions via JSON tools:

- `quantify_against_standard` — Fit a standard curve (linear or log–log) and estimate amounts for target bands.
  - Args: `standard_lane` (int) **or** `standard_points` (list of `[amount, intensity]`), `fit` = `"linear"` | `"loglog"`, optional `target_lane`, `target_band_index`.
  - If using `standard_lane`, provide `results["standards"][lane_index] = [known_amounts...]` matching the order of bands.

- `band_ratio` — Ratio of two specified bands: `{"ref":[lane,band], "target":[lane,band]}`.

- `lane_compare` — Summarize total intensity and band counts per lane: `{"lanes":[1,2,3]}`.

- `mobility_shift` — Relative mobility of two bands using centroid_y and image height if available.

- `copy_number_estimate` — Rough EtBr-based DNA copy-number ratio by summing `intensity/length` across bands per lane.

> Intensity key detection is heuristic (`intensity`, `integrated_intensity`, `sum_intensity`, `volume`, etc.). You can normalize your band dicts to include a preferred key named `intensity` for best results.
