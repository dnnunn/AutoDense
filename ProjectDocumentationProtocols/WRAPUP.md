# Session Wrap-up Procedures

> **Doc Meta**
> - **Purpose:** Standardized end-of-session procedures for documentation maintenance and project tracking
> - **Scope:** Session summaries, document catalog updates, issue tracking, and next steps planning
> - **Owner:** @[PROJECT_OWNER]
> - **Last-verified:** [CURRENT_DATE]

## 📋 Document Catalog Maintenance

**REQUIRED: Update document catalog for any new/modified documents**

#### Update Document Catalog
1. **Identify Changes:** Review all documents created or substantially modified during session
2. **Update Catalog:** Add entries to `/docs/DOCUMENT_CATALOG.md` in appropriate category:
   - Include document path and concise 1-2 sentence summary  
   - Add word count (from word count tool or manual estimation)
   - Set Last-verified date to current session date
   - Ensure proper categorization (Architecture, Workflows, Features, etc.)
3. **Verify Format:** Ensure Doc Meta blocks present on all new/modified .md files
4. **Update Statistics:** Refresh total document count and verification percentage in catalog

**Template for new catalog entries:**
```markdown
| **path/file.md** | Concise 1-2 sentence summary of purpose and content | word_count | YYYY-MM-DD |
```

## 📝 Prepare Session Summary

#### Create Session Summary Document
Location: `/[PROJECT_ROOT]/SessionSummaries`
Format: `Session_summary_Month_Date_Year_[Brief_Description].md`

**Required Content:**
- **Key accomplishments** and changes made
- **Documents created/modified** (reference catalog updates)
- **Technical decisions** and rationale
- **Issues encountered** and solutions
- **Code changes** and architectural updates
- **Testing results** and validation
- **Dependencies** or blockers identified

**Template:**
```markdown
# Session Summary: [DATE] - [BRIEF_DESCRIPTION]

> **Doc Meta**
> - **Purpose:** Session summary documenting [SESSION_FOCUS]
> - **Scope:** [WHAT_WAS_COVERED]
> - **Owner:** @[YOUR_HANDLE]  
> - **Last-verified:** [CURRENT_DATE]

## 🎯 Key Accomplishments
- ✅ [ACCOMPLISHMENT_1]
- ✅ [ACCOMPLISHMENT_2]

## 📁 Documents Created/Modified
1. **`path/file.md`** (estimated_words words) - Brief description
2. **`code/file.ext`** - Code changes description

## 🔧 Technical Decisions
### Decision 1
- **Decision:** [WHAT_WAS_DECIDED]
- **Rationale:** [WHY_THIS_CHOICE]
- **Trade-off:** [ALTERNATIVES_CONSIDERED]

## 🐛 Issues Encountered
- **Issue:** [PROBLEM_DESCRIPTION]
- **Solution:** [HOW_RESOLVED]
- **Impact:** [EFFECT_ON_PROJECT]

## 🧪 Testing/Validation
- [WHAT_WAS_TESTED]
- [RESULTS_ACHIEVED]
- [CONFIDENCE_LEVEL]
```

## 🎯 Prepare Next Steps Summary

#### Create Next Steps Document  
Location: `/[PROJECT_ROOT]/NextSteps`
Format: `NextSteps_Month_Date_Year_[Brief_Description].md`

**Required Content:**
- **Priority tasks** for next session (with urgency levels)
- **Unfinished work** requiring continuation  
- **Dependencies and blockers** identified
- **Recommended approach** for pending tasks
- **Success criteria** for priority items
- **Links to relevant documentation**

**Template:**
```markdown
# Next Steps: [DATE] - [BRIEF_DESCRIPTION]

> **Doc Meta**
> - **Purpose:** Priority tasks following [SESSION_FOCUS]
> - **Scope:** [SCOPE_OF_NEXT_WORK]
> - **Owner:** @[YOUR_HANDLE]
> - **Last-verified:** [CURRENT_DATE]

## 🚨 CRITICAL PRIORITY
**Status:** [CURRENT_STATUS]

### Immediate Tasks
#### 1. **[CRITICAL_TASK_1]** [CRITICAL]
- **Issue:** [PROBLEM_DESCRIPTION]
- **Investigation needed:** [WHAT_TO_EXPLORE]
- **Success criteria:** [HOW_TO_KNOW_ITS_FIXED]

## 📋 Secondary Priorities
#### 2. **[TASK_2]** [HIGH/MEDIUM/LOW]
- **Dependencies:** [WHAT_MUST_BE_DONE_FIRST]
- **Approach:** [RECOMMENDED_STRATEGY]

## 🎯 Success Criteria for Next Session
### Minimum Viable Success
- [ ] [MINIMUM_GOAL_1]
- [ ] [MINIMUM_GOAL_2]

### Ideal Success  
- [ ] [IDEAL_OUTCOME_1]
- [ ] [IDEAL_OUTCOME_2]
```

## 🐛 Prepare Issues Summary

#### Create Issues Document
Location: `/[PROJECT_ROOT]/Issues`  
Format: `Issues_Month_Date_Year_[Brief_Description].md`

**Required Content:**
- **Bugs discovered** during session with severity levels
- **Performance issues** or concerns
- **Documentation gaps** identified
- **System limitations** or edge cases
- **Technical debt** accumulated
- **Investigation approaches** suggested

**Template:**
```markdown
# Issues Report: [DATE] - [BRIEF_DESCRIPTION]

> **Doc Meta**
> - **Purpose:** Issues discovered during [SESSION_FOCUS]
> - **Scope:** [TYPES_OF_ISSUES_COVERED]
> - **Owner:** @[YOUR_HANDLE]
> - **Last-verified:** [CURRENT_DATE]

## 🚨 CRITICAL ISSUES

### Issue #1: [ISSUE_TITLE]
**Severity:** CRITICAL/HIGH/MEDIUM/LOW  
**Impact:** [BUSINESS_OR_TECHNICAL_IMPACT]

**Description:** [DETAILED_PROBLEM_DESCRIPTION]

**Evidence:**
- [SYMPTOM_1]
- [SYMPTOM_2]
- [ERROR_MESSAGES_OR_DATA]

**Location:** `file/path:line_number` or [COMPONENT_NAME]

**Root Cause Analysis:**
- **Hypothesis 1:** [POSSIBLE_CAUSE]
- **Hypothesis 2:** [ALTERNATIVE_CAUSE]

**Investigation Required:**
- [ ] [INVESTIGATION_STEP_1]
- [ ] [INVESTIGATION_STEP_2]

**Fix Priority:** [WHY_THIS_URGENCY_LEVEL]
```

## 🌳 Project Structure Documentation

#### Create/Update Project Structure Document
Location: `/[PROJECT_ROOT]/StructureDocs`
Format: `Structure_Month_Date_Year_[Time_Period].md`

**Process:**
1. **Check for existing structure document** for current date
2. **Create new document** if none exists, or **update existing** if multiple sessions same day
3. **Generate current project tree** using appropriate tools (ls, find, tree command, or IDE)
4. **Include Doc Meta block** with purpose as "Current project structure snapshot"
5. **Document notable structural changes** since last structure snapshot
6. **Include critical environment procedures** if they've changed

**Template:**
```markdown
# Project Structure: [DATE] - [SESSION_DESCRIPTION]

> **Doc Meta**
> - **Purpose:** Current project structure snapshot following [SESSION_FOCUS]
> - **Scope:** Complete directory tree with key file locations and organization
> - **Owner:** @[YOUR_HANDLE]
> - **Last-verified:** [CURRENT_DATE]

## 🚨 CRITICAL: Essential Working Procedures

### Environment Setup (MANDATORY)
```bash
# Essential environment commands
[ENVIRONMENT_SETUP_COMMANDS]
```

### Build Procedures (EXACT COMMANDS)
```bash
# Build system commands
[BUILD_COMMANDS]
```

### Key Directories (ABSOLUTE PATHS)
- **Root:** `/absolute/path/to/project`
- **Source:** `/absolute/path/to/source`
- **Build:** `/absolute/path/to/build`

## Directory Structure
```
/project/root
├── [KEY_DIRECTORY_1]/
├── [KEY_DIRECTORY_2]/
└── [KEY_FILES]
```

## Notable Changes Since Last Structure
- [CHANGE_1]
- [CHANGE_2]
```

## ✅ Pre-Commit Checklist

Before ending session, verify:
- [ ] Document catalog updated with all new/modified files
- [ ] All new .md files have required Doc Meta blocks
- [ ] Project structure document created/updated in StructureDocs folder
- [ ] Session summary captures key accomplishments
- [ ] Next steps clearly defined with priorities
- [ ] Issues documented with sufficient detail for follow-up
- [ ] No sensitive information (API keys, passwords) in commits
- [ ] Code compiles and tests pass (if applicable)
- [ ] Environment procedures documented if changed
- [ ] Critical file locations documented

## 🎯 Quality Gates

### Documentation Quality
- [ ] All documents have proper Doc Meta blocks
- [ ] Summaries are clear and actionable
- [ ] File paths and commands are absolute and tested
- [ ] Word counts estimated for catalog entries

### Project Continuity
- [ ] Next session can start immediately with clear priorities
- [ ] Environment setup procedures are complete and tested
- [ ] No "discovery sessions" required for basic project facts

### Knowledge Transfer
- [ ] All decisions and rationale documented
- [ ] Technical debt and issues properly categorized
- [ ] Investigation approaches clearly specified

---

*This process ensures consistent documentation maintenance and project continuity across sessions, preventing the common "What was I working on?" startup delay.*