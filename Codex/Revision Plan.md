1. Detangle Preprocessing

- Remove “ChatGPT-4.1 (premium)” from the preprocessing selector and delete the associated status panel.
- Delete supporting code: _openai_preprocess_with_timeout, key/status helpers, extra session-state flags.
- Simplify cached_preprocess_image to only handle Auto, Manual, Off. Decommission res_preprocessing_outcome fields tied to AI metadata.
- Refresh UI copy/documentation so users know preprocessing is deterministic.

2. Solidify Deterministic Pipeline

- Audit the lane/band pipeline (Band Assist) so it remains untouched by LLM logic.
- Improve manual correction tooling: redesign the “Band Assist — editable bands” surface into the intended click-to-add/remove experience, with hooks to re-run metrics.
- Update tests/docs to reflect the trimmed preprocessing API (remove ChatGPT fixtures, rewrite UX notes).

3. Build Post-Analysis Chat

- Keep running deterministic analysis to produce res_analysis_data, lane metrics, QC flags.
- Create a new “Analysis Companion” pane that appears once results exist.
- Feed the structured results into GPT-4.1 via a dedicated chat backend; maintain a turn-by-turn history for conversational flow.
- Seed the chat with helpful macros (“Explain this gel”, “Suggest follow-up experiment”, “Check QC anomalies”).
- Gate the entire chat UI behind a verification that stamped analysis results are present.

4. Clean Up & Harden

- Strip unused key handlers (OPENAI_API_KEY loading) from the startup path; only the chat needs them.
- Document the new architecture (diagram deterministic pipeline vs. AI companion).
- Add guardrails: chat prompts should clarify that the AI can’t alter results; surface confidence disclaimers in the UI.
- Expand tests to cover: deterministic preprocessing, band edits, chat integration (mocked GPT responses).
- Preprocessing preview / manual adjustments should live before lane calibration so users can see effects once and carry them through calibration.
- Align the UX flow with the agreed step order (upload → calibration → preprocessing → detection → optional Band Assist → final quantification → chat insights → reporting). Delay heavy export UI until after chat/queries.
- Introduce a "Save/Resume" checkpoint between quantification and chat so results can be persisted before running conversational analyses.
- Remove the legacy chatgpt_postrun_explainer hook from the analysis pipeline; replace with new post-analysis chat module.
