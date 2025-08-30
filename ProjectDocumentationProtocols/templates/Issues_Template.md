# Issues Report: [DATE] - [BRIEF_DESCRIPTION]

> **Doc Meta**
> - **Purpose:** Issues discovered during [SESSION_FOCUS_OR_INVESTIGATION]
> - **Scope:** [TYPES_OF_ISSUES_COVERED]
> - **Owner:** @[YOUR_GITHUB_HANDLE]
> - **Last-verified:** [CURRENT_DATE]

## 🚨 CRITICAL ISSUES

### Issue #1: [CRITICAL_ISSUE_TITLE]
**Severity:** CRITICAL  
**Impact:** [BUSINESS_OR_TECHNICAL_IMPACT_DESCRIPTION]  

**Description:**
[DETAILED_PROBLEM_DESCRIPTION_WITH_CONTEXT]

**Evidence:**
```
[ERROR_MESSAGES_OR_LOG_EXCERPTS]
```
- [SYMPTOM_1_DESCRIPTION]
- [SYMPTOM_2_DESCRIPTION]  
- [QUANTIFIED_IMPACT_IF_AVAILABLE]

**Location:** `[FILE_PATH]:[LINE_NUMBER]` or `[COMPONENT_NAME]`

**Root Cause Analysis:**
1. **Primary hypothesis:** [MOST_LIKELY_CAUSE]
2. **Alternative hypothesis:** [SECONDARY_POSSIBILITY]
3. **Contributing factors:** [ENVIRONMENTAL_OR_DESIGN_FACTORS]

**Test Case:**
- **Environment:** [TEST_ENVIRONMENT_DESCRIPTION]
- **Steps to reproduce:** [STEP_BY_STEP_REPRODUCTION]
- **Expected vs Actual:** [WHAT_SHOULD_HAPPEN_VS_WHAT_DOES]

**Investigation Required:**
- [ ] [INVESTIGATION_STEP_1]
- [ ] [INVESTIGATION_STEP_2]
- [ ] [INVESTIGATION_STEP_3]

**Fix Priority:** [WHY_THIS_URGENCY_LEVEL_IS_JUSTIFIED]

---

### Issue #2: [HIGH_SEVERITY_ISSUE_TITLE]
**Severity:** HIGH  
**Impact:** [IMPACT_ON_USERS_OR_DEVELOPMENT]

**Description:**
[PROBLEM_DESCRIPTION]

**Symptoms:**
- [OBSERVABLE_BEHAVIOR_1]
- [OBSERVABLE_BEHAVIOR_2]
- [PERFORMANCE_OR_FUNCTIONAL_IMPACT]

**Reproduction:**
```bash
# Steps to reproduce
[COMMAND_1]
[COMMAND_2]
# Expected: [EXPECTED_RESULT]
# Actual: [ACTUAL_RESULT]
```

**Analysis:**
- **Affected components:** [LIST_OF_COMPONENTS]
- **Data loss risk:** [YES/NO_AND_EXPLANATION]
- **Workaround available:** [TEMPORARY_SOLUTION_IF_ANY]

---

## 🔧 TECHNICAL DEBT ISSUES

### Issue #3: [TECHNICAL_DEBT_ISSUE]
**Severity:** MEDIUM  
**Impact:** [LONG_TERM_DEVELOPMENT_IMPACT]

**Description:**
[EXPLANATION_OF_TECHNICAL_DEBT_ACCUMULATION]

**Current Cost:**
- **Development velocity:** [HOW_IT_SLOWS_DEVELOPMENT]
- **Maintenance burden:** [ONGOING_EFFORT_REQUIRED]
- **Risk factors:** [POTENTIAL_FUTURE_PROBLEMS]

**Refactoring Required:**
- [ ] [REFACTORING_TASK_1]
- [ ] [REFACTORING_TASK_2]
- [ ] [REFACTORING_TASK_3]

**Effort Estimate:** [TIME_OR_COMPLEXITY_ESTIMATE]

---

## 🐛 FUNCTIONAL BUGS

### Issue #4: [BUG_TITLE]
**Severity:** MEDIUM  
**Impact:** [USER_OR_FUNCTIONAL_IMPACT]

**Description:**
[BUG_DESCRIPTION_WITH_CONTEXT]

**Steps to Reproduce:**
1. [STEP_1]
2. [STEP_2]
3. [STEP_3]
4. **Observe:** [INCORRECT_BEHAVIOR]

**Expected Behavior:** [WHAT_SHOULD_HAPPEN]

**Environment:**
- **Platform:** [OPERATING_SYSTEM_OR_PLATFORM]
- **Version:** [SOFTWARE_VERSION]
- **Configuration:** [RELEVANT_CONFIG_DETAILS]

**Debug Information:**
```
[RELEVANT_DEBUG_OUTPUT_OR_LOGS]
```

---

## 📊 PERFORMANCE ISSUES

### Issue #5: [PERFORMANCE_ISSUE_TITLE]
**Severity:** LOW-MEDIUM  
**Impact:** [PERFORMANCE_IMPACT_DESCRIPTION]

**Metrics:**
- **Current performance:** [QUANTIFIED_CURRENT_STATE]
- **Expected performance:** [PERFORMANCE_TARGET]
- **Degradation:** [HOW_MUCH_SLOWER_THAN_EXPECTED]

**Profiling Results:**
- **Bottleneck location:** `[FILE]:[FUNCTION]`
- **Resource usage:** [CPU_MEMORY_IO_STATS]
- **Scaling behavior:** [HOW_PERFORMANCE_CHANGES_WITH_LOAD]

**Optimization Opportunities:**
1. [OPTIMIZATION_APPROACH_1] - [EXPECTED_IMPROVEMENT]
2. [OPTIMIZATION_APPROACH_2] - [EXPECTED_IMPROVEMENT]

---

## 🔍 INVESTIGATION NEEDED

### Issue #6: [UNCLEAR_ISSUE_TITLE]
**Severity:** UNKNOWN  
**Impact:** [POTENTIAL_IMPACT_RANGE]

**Observation:**
[STRANGE_BEHAVIOR_OR_ANOMALY_DESCRIPTION]

**Questions to Answer:**
- [INVESTIGATION_QUESTION_1]
- [INVESTIGATION_QUESTION_2]
- [INVESTIGATION_QUESTION_3]

**Investigation Plan:**
1. **[INVESTIGATION_PHASE_1]** - [WHAT_TO_LOOK_FOR]
2. **[INVESTIGATION_PHASE_2]** - [HOW_TO_GATHER_DATA]
3. **[INVESTIGATION_PHASE_3]** - [ANALYSIS_METHOD]

**Success Criteria:** [HOW_TO_KNOW_WHEN_INVESTIGATION_IS_COMPLETE]

---

## 📋 DOCUMENTATION GAPS

### Issue #7: [DOCUMENTATION_GAP_TITLE]
**Severity:** LOW  
**Impact:** [IMPACT_ON_ONBOARDING_OR_MAINTENANCE]

**Missing Documentation:**
- [MISSING_DOC_TYPE_1] for [COMPONENT_OR_PROCESS]
- [MISSING_DOC_TYPE_2] for [FEATURE_OR_WORKFLOW]

**Consequences:**
- [DIFFICULTY_1_CAUSED_BY_GAP]
- [DIFFICULTY_2_CAUSED_BY_GAP]

**Documentation Needed:**
- [ ] [SPECIFIC_DOCUMENT_1] - [PURPOSE_AND_AUDIENCE]
- [ ] [SPECIFIC_DOCUMENT_2] - [PURPOSE_AND_AUDIENCE]

---

## 🎯 PRIORITIZATION MATRIX

### Critical Path Blockers (Fix Immediately)
1. [Issue #X] - [BRIEF_TITLE] - Blocks [WHAT_IT_BLOCKS]
2. [Issue #Y] - [BRIEF_TITLE] - Blocks [WHAT_IT_BLOCKS]

### High Impact, Low Effort (Quick Wins)
3. [Issue #Z] - [BRIEF_TITLE] - [WHY_QUICK_WIN]

### High Impact, High Effort (Plan Carefully)
4. [Issue #W] - [BRIEF_TITLE] - [WHY_SIGNIFICANT_EFFORT]

### Low Priority (Address When Convenient)
5. [Issue #V] - [BRIEF_TITLE] - [WHY_LOW_PRIORITY]

---

## 📚 REFERENCE INFORMATION

**Test Environment:**
- **Platform:** [OS_AND_VERSION]
- **Runtime:** [RUNTIME_VERSION]
- **Dependencies:** [KEY_DEPENDENCY_VERSIONS]

**Key Files for Investigation:**
- **Primary component:** `[FILE_PATH]`
- **Configuration:** `[CONFIG_FILE_PATH]`
- **Logs location:** `[LOG_FILE_PATH]`

**Debugging Tools:**
- **Profiler:** [PROFILING_TOOL_AND_USAGE]
- **Debugger:** [DEBUG_SETUP_AND_COMMANDS]
- **Monitoring:** [MONITORING_TOOLS_AVAILABLE]

---

**Investigation Note:** [OVERALL_PATTERN_OR_THEME_ACROSS_ISSUES] suggests [SYSTEMIC_ISSUE_OR_ROOT_CAUSE] that should be addressed holistically.