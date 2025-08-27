
    # AutoDense: ClaudeCode Implementation Plan

    **Goal**: Implement the consensus audit recommendations with minimal churn and tight feedback loops.

    ## Working Rules
    - Start with Fast Wins; open small PRs (< 200 lines) with clear titles.
    - Each change must include a test, doc note, or linter rule preventing regression.
    - Keep stateful changes behind feature flags; default off.

    ## Task List (import these into ClaudeCode)
    # CONSENSUS TODO

- [ ] Consensus Executive Summary
- [ ] Unified Findings (Ranked by Severity)
- [ ] **Handle Discipline Enforcement in GelAnalysisTools**
- [ ] **Memory Leaks due to Unmanaged ImageJ Resources**
- [ ] **Audit Orchestrator Inefficient File Loading**
- [ ] **Weak Error Handling and Poor API Error Semantics in HandleGuard**
- [ ] **Path Validation and Injection Risks in openImage()**
- [ ] **Use of Non-Standardized Session Storage Keys in ColonyAnalysisTools**
- [ ] **Missing or Broken Deskew Affine Transform in PlateDetector**
- [ ] **Concurrency and Cache Eviction Risks** (from executive summary, though not detailed fixes; inferred)
- [ ] **Deprecated ColonyVisualizer Usage**
- [ ] **Binning Behavior on Immutable Colony Objects**
- [ ] **CI Pipeline Reduced to Build-Only**
- [ ] **Security Hygiene: API Keys Removed but Vigilance Required**
- [ ] Conflicts & Resolutions
- [ ] Migration Plan
- [ ] Test & CI Plan
- [ ] Risk Register & Rollback/Feature Flags

    ## PR Cadence
    - PR 1: Safety Nets (CI, lint, typing baselines). Ensure green.
    - PR 2..N: One finding per PR; hold the line on tests.

    ## Definition of Done
    - All critical findings addressed or ticketed.
    - CI green, coverage trend non‑decreasing, release notes updated.
