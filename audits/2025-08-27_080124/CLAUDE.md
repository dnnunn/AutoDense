
    # AutoDense: ClaudeCode Implementation Plan

    **Goal**: Implement the consensus audit recommendations with minimal churn and tight feedback loops.

    ## Working Rules
    - Start with Fast Wins; open small PRs (< 200 lines) with clear titles.
    - Each change must include a test, doc note, or linter rule preventing regression.
    - Keep stateful changes behind feature flags; default off.

    ## Task List (import these into ClaudeCode)
    # CONSENSUS TODO

- [ ] **High Severity: Inconsistent Handle Discipline and Validation**
- [ ] **High Severity: Temporary Resource Leaks (Temp Directories/Files)**
- [ ] **High Severity: Silent Catch-All Exception Handling**
- [ ] **High Severity: Input Validation and Security Gaps**
- [ ] **Medium Severity: Deprecated Overlay Systems and UI Inconsistencies**
- [ ] **Medium Severity: Performance Bottlenecks in Image Processing**
- [ ] **Medium Severity: Cache Consistency and Semantics in Preprocessing**
- [ ] **Low Severity: Ineffective or Stub Methods and API Inconsistencies**
- [ ] **Low Severity: Audit Orchestrator Exclusion Logic Too Naive**
- [ ] **Low Severity: Documentation Gaps and Legacy Docs Remaining**

    ## PR Cadence
    - PR 1: Safety Nets (CI, lint, typing baselines). Ensure green.
    - PR 2..N: One finding per PR; hold the line on tests.

    ## Definition of Done
    - All critical findings addressed or ticketed.
    - CI green, coverage trend non‑decreasing, release notes updated.
