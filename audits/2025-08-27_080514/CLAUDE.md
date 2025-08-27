
    # AutoDense: ClaudeCode Implementation Plan

    **Goal**: Implement the consensus audit recommendations with minimal churn and tight feedback loops.

    ## Working Rules
    - Start with Fast Wins; open small PRs (< 200 lines) with clear titles.
    - Each change must include a test, doc note, or linter rule preventing regression.
    - Keep stateful changes behind feature flags; default off.

    ## Task List (import these into ClaudeCode)
    # CONSENSUS TODO

- [ ] **Incorrect Image Processing in `BandDetector.findBands()`**
- [ ] **Race Condition in `SessionStore.putAnalysis()`**
- [ ] **Handle Management Fragility and Inconsistency**
- [ ] **Lack of Unit Tests on Core Components; CI Pipeline Degraded**
- [ ] **Secrets Exposure Risk (API and Configuration Files)**
- [ ] **`GelAnalysisTools.openImage()` Synchronously Shows UI Image**
- [ ] **Outdated Mutable Colony Analysis Logic in `ColonyAnalysisTools`**
- [ ] **Fragmented and Duplicate Overlay Rendering**
- [ ] **Inconsistent Error Handling Across Tools**
- [ ] **Inefficient Pixel-wise Sampling in `ColonyNormalizer` and `PlateAlignment`**
- [ ] **Synchronous I/O and Missing Streaming in Hot Paths**
- [ ] **Tight Coupling of Session and Tool Logic**
- [ ] **Incomplete Input Validation**
- [ ] **Temporary Directory Leakage in `GelAnalysisTools`**
- [ ] **Fragmented and Outdated Documentation**
- [ ] **Deprecated Code Still Used (e.g., `ColonyVisualizer`)**
- [ ] **Manual Chunking and Binary File Handling in Audit Scripts**
- [ ] **Missing Authentication/Authorization Layer**
- [ ] **Style and Linting Incompleteness (Java and Python)**

    ## PR Cadence
    - PR 1: Safety Nets (CI, lint, typing baselines). Ensure green.
    - PR 2..N: One finding per PR; hold the line on tests.

    ## Definition of Done
    - All critical findings addressed or ticketed.
    - CI green, coverage trend non‑decreasing, release notes updated.
