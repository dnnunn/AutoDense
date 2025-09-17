AutoDense UX Worklog & Codebase Notes

  - Project Shape
      - Streamlit front-end (ui/streamlit_autodense_app.py) drives the UX; major helper modules
    sit under ui/components/, ui/utils/, and scripts/autodense/... for vision/orchestrator logic.
      - Image upload & metadata handling lives in ui/components/image_upload.py.
      - Calibration/analysis pipeline uses Python vision stack (scripts/autodense/vision/
    analyzer.py) and lane utilities; state is managed via st.session_state.
    “workflow-section” styling, and sticky Back/Next footer anchored to section IDs.
      - Streamlined Step 1 header and status area: moved “Show Environment Status” toggle to
    top right, removed redundant status banners, tightened spacing, and added lane-default logic
    (SDS=12, EtBr=20).
      - Simplified image upload feedback: removed stacked success banners; metadata now lives
    solely in the “Image Information” expander.
      - Overhauled calibration UI: no manual coordinate fallback, aligned control buttons atop
    the canvas, added dynamic two-step instruction pill (red → green) and dropped the bottom
    “calibration ready” alerts.
      - Step 4 only renders after analysis, with a visual-first layout and inline guidance.
      - Numerous styling tweaks (CSS sections in streamlit_autodense_app.py) to reduce whitespace
    and harmonize colors.
  - State & Git
      - All modifications are local; sandbox blocks git add/commit. To persist:
          - git add ui/streamlit_autodense_app.py ui/components/image_upload.py
          - git commit -m "Streamline AutoDense UI workflow and calibration guidance"
  - Next Ideas / Follow-ups
      - Review remaining alerts or expander copy for redundancy.
      - Consider animations or transitions for the step-pill instructions if rerun flicker remains
    noticeable.
      - Audit other steps (analysis/results) for similar cleanup opportunities.