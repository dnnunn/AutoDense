# [PROJECT_NAME].md (AI Assistant Project Primer)

**Purpose:** Give AI assistants a clear, accurate picture of [PROJECT_NAME]'s current state at the start of every session.

---

## Project Summary

[PROJECT_NAME] is a [PROJECT_TYPE] application for [PRIMARY_PURPOSE]. As of [CURRENT_DATE], the project [CURRENT_STATUS_DESCRIPTION].

The system maintains [ARCHITECTURE_PATTERN] where [KEY_ARCHITECTURAL_CONCEPTS] are referenced by [ID_SYSTEM], never [DIRECT_REFERENCES].

---

## 🔄 Current System State (Update This Section Regularly)

**CRITICAL:** This section should reflect the current reality, not aspirational goals.

### Recent Major Changes
* **[RECENT_CHANGE_1]:** [DESCRIPTION_AND_IMPACT]
* **[RECENT_CHANGE_2]:** [DESCRIPTION_AND_IMPACT]
* **[RECENT_CHANGE_3]:** [DESCRIPTION_AND_IMPACT]

### Key Components Status
* **[COMPONENT_1]:** [STATUS] - [BRIEF_DESCRIPTION]
* **[COMPONENT_2]:** [STATUS] - [BRIEF_DESCRIPTION]  
* **[COMPONENT_3]:** [STATUS] - [BRIEF_DESCRIPTION]

### Current Architecture
* **[ARCHITECTURAL_LAYER_1]** → [RESPONSIBILITY]
* **[ARCHITECTURAL_LAYER_2]** → [RESPONSIBILITY]
* **[ARCHITECTURAL_LAYER_3]** → [RESPONSIBILITY]
* **[DATA_LAYER]** → [STORAGE_AND_PERSISTENCE_STRATEGY]

---

## Core Capabilities (Implemented)

* **[FEATURE_1]:** [DESCRIPTION_OF_WHAT_IT_DOES]
* **[FEATURE_2]:** [DESCRIPTION_OF_WHAT_IT_DOES]
* **[FEATURE_3]:** [DESCRIPTION_OF_WHAT_IT_DOES]
* **[INTEGRATION_1]:** [EXTERNAL_SYSTEM_INTEGRATION]
* **[INTEGRATION_2]:** [EXTERNAL_SYSTEM_INTEGRATION]

---

## Current Architecture Details

### [LAYER_1_NAME] (e.g., Frontend, API Layer)
* **[COMPONENT_A]** → [SPECIFIC_RESPONSIBILITY]
* **[COMPONENT_B]** → [SPECIFIC_RESPONSIBILITY]
* **[COMPONENT_C]** → [SPECIFIC_RESPONSIBILITY]

### [LAYER_2_NAME] (e.g., Business Logic, Service Layer)
* **[COMPONENT_A]** → [SPECIFIC_RESPONSIBILITY]
* **[COMPONENT_B]** → [SPECIFIC_RESPONSIBILITY]

### [LAYER_3_NAME] (e.g., Data Access, Infrastructure)
* **[COMPONENT_A]** → [SPECIFIC_RESPONSIBILITY]
* **[COMPONENT_B]** → [SPECIFIC_RESPONSIBILITY]

**Design Principles:**
1. [DESIGN_PRINCIPLE_1] *(unchanged from initial design)*
2. [DESIGN_PRINCIPLE_2] *(modified: [EXPLANATION])*
3. [DESIGN_PRINCIPLE_3] *(new: [EXPLANATION])*
4. [DESIGN_PRINCIPLE_4] *(deprecated: [EXPLANATION])*

For detailed architecture information see: `docs/ARCHITECTURE.md`

---

## Development & Usage

* **[TECHNOLOGY_STACK]** - [VERSION_REQUIREMENTS]
* **Build System:** [BUILD_TOOL] with [CONFIGURATION_FILES]
* **Run Command:** `[MAIN_RUN_COMMAND]`
* **Environment Setup:** [ENVIRONMENT_REQUIREMENTS] - set via [ENVIRONMENT_CONFIG_METHOD]
* **Key Dependencies:** [CRITICAL_EXTERNAL_DEPENDENCIES]

---

## Known Issues ([CURRENT_DATE])

* [ISSUE_1_BRIEF_DESCRIPTION] - [IMPACT_LEVEL]
* [ISSUE_2_BRIEF_DESCRIPTION] - [IMPACT_LEVEL]
* [ISSUE_3_BRIEF_DESCRIPTION] - [IMPACT_LEVEL]
* [ONGOING_TECHNICAL_DEBT] - [IMPACT_ON_DEVELOPMENT]

---

## Recent Enhancements

### 🚀 Major: [RECENT_MAJOR_ENHANCEMENT] ([DATE_RANGE])
* **[SPECIFIC_IMPROVEMENT_1]** with [TECHNICAL_IMPLEMENTATION]
* **[SPECIFIC_IMPROVEMENT_2]** with [TECHNICAL_IMPLEMENTATION]
* **[SPECIFIC_IMPROVEMENT_3]** with [TECHNICAL_IMPLEMENTATION]

### Previous Enhancements
* [ENHANCEMENT_1] with [KEY_BENEFIT]
* [ENHANCEMENT_2] with [KEY_BENEFIT]
* [ENHANCEMENT_3] with [KEY_BENEFIT]

---

## Immediate Priorities

### 🎯 [PRIORITY_CATEGORY_1] Priorities
1. **[PRIORITY_TASK_1]** - [BRIEF_DESCRIPTION]
2. **[PRIORITY_TASK_2]** - [BRIEF_DESCRIPTION]
3. **[PRIORITY_TASK_3]** - [BRIEF_DESCRIPTION]

### [PRIORITY_CATEGORY_2] Maintenance
4. [MAINTENANCE_TASK_1]
5. [MAINTENANCE_TASK_2]
6. [MAINTENANCE_TASK_3]

---

## Security & Configuration Notes

* [SECURITY_CONSIDERATION_1]
* [SECURITY_CONSIDERATION_2]
* [CONFIGURATION_MANAGEMENT_APPROACH]
* [SECRETS_MANAGEMENT_APPROACH]

---

## 📝 Documentation Standards (MANDATORY)

### **All New/Modified Documents Must Include:**

**Required Doc Meta Block (at top of every .md file):**
```markdown
> **Doc Meta**
> - **Purpose:** Brief description of what this document is for
> - **Scope:** What it covers (and what it doesn't)
> - **Owner:** @[github-handle-or-team]
> - **Last-verified:** [YYYY-MM-DD]
```

### **Documentation Rules:**
1. **NEVER create/edit a .md file** without the Doc Meta block
2. **ALWAYS update Last-verified** when making substantial changes
3. **Use present date** (YYYY-MM-DD format) for new documents
4. **Include Purpose & Scope** - be specific about boundaries
5. **Assign clear ownership** - use actual handle or team name

### **Quality Gates:**
- Documentation standards are enforced via [ENFORCEMENT_MECHANISM]
- [FRESHNESS_POLICY] - [VERIFICATION_PROCESS]
- [VALIDATION_PROCESS] ensures all documentation standards

**This rule applies to ALL documentation: technical specs, guides, planning docs, and reference material.**

## Development Environment Critical Procedures

### Environment Setup (PREVENT SESSION TIME WASTE)
```bash
# Navigate to project
cd [ABSOLUTE_PROJECT_PATH]

# Activate environment
[ENVIRONMENT_ACTIVATION_COMMAND]
# Example: source .venv/bin/activate
# Example: source ~/.nvm/nvm.sh && nvm use
# Example: eval $(opam env)

# Verify environment
[VERIFICATION_COMMANDS]
```

### Build Procedures (EXACT COMMANDS)
```bash
# Standard build
[BUILD_COMMAND]

# Clean build  
[CLEAN_BUILD_COMMAND]

# Quick verification
[QUICK_BUILD_COMMAND]

# Development mode
[DEV_MODE_COMMAND]
```

### Testing Procedures
```bash
# Unit tests
[UNIT_TEST_COMMAND]

# Integration tests
[INTEGRATION_TEST_COMMAND]  

# Quick test suite
[QUICK_TEST_COMMAND]

# Test specific component
[COMPONENT_TEST_COMMAND_TEMPLATE]
```

## File Structure & Key Locations

### Absolute Paths (MEMORIZE)
- **Root:** `[ABSOLUTE_ROOT_PATH]`
- **Source:** `[ABSOLUTE_SOURCE_PATH]`  
- **Configuration:** `[ABSOLUTE_CONFIG_PATH]`
- **Build Output:** `[ABSOLUTE_BUILD_PATH]`
- **Documentation:** `[ABSOLUTE_DOCS_PATH]`
- **Tests:** `[ABSOLUTE_TESTS_PATH]`

### Critical Files
- **Main Configuration:** `[CONFIG_FILE_PATH]`
- **Environment Config:** `[ENV_FILE_PATH]`
- **Build Configuration:** `[BUILD_CONFIG_PATH]`
- **Main Entry Point:** `[MAIN_FILE_PATH]`

## Common Mistakes to NEVER Make Again
❌ [COMMON_MISTAKE_1] ([WHY_THIS_FAILS])  
❌ [COMMON_MISTAKE_2] ([WHY_THIS_FAILS])  
❌ [COMMON_MISTAKE_3] ([WHY_THIS_FAILS])  
❌ [ENVIRONMENT_MISTAKE] ([SOLUTION])

---

## Delta Changelog (Last Updated: [CURRENT_DATE])

### 🚀 [RECENT_PERIOD] ([DATE_RANGE])
* **[MAJOR_CHANGE]:** [DESCRIPTION_AND_IMPACT]
* **[FEATURE_ADDITION]:** [DESCRIPTION_AND_IMPACT]  
* **[INFRASTRUCTURE_CHANGE]:** [DESCRIPTION_AND_IMPACT]

### ✅ [PREVIOUS_PERIOD] Stable Features
* **[STABLE_FEATURE_1]:** [STATUS_AND_DESCRIPTION]
* **[STABLE_FEATURE_2]:** [STATUS_AND_DESCRIPTION]
* **[STABLE_FEATURE_3]:** [STATUS_AND_DESCRIPTION]

### 🚧 In Progress
* **[IN_PROGRESS_WORK]:** [CURRENT_STATUS_AND_BLOCKERS]

### 🎯 Next Targets
1. **[NEXT_TARGET_1]** - [EXPECTED_OUTCOME]
2. **[NEXT_TARGET_2]** - [EXPECTED_OUTCOME]  
3. **[NEXT_TARGET_3]** - [EXPECTED_OUTCOME]

# Important Context Reminders
* [PROJECT_SPECIFIC_CONTEXT_1]
* [PROJECT_SPECIFIC_CONTEXT_2]
* [ARCHITECTURAL_CONSTRAINT]
* [BUSINESS_CONSTRAINT]

**IMPORTANT:** This context may or may not be relevant to specific tasks. AI assistants should not respond to this context unless it is highly relevant to the current task.

---

*This document should be updated after significant architectural changes, major feature additions, or shifts in project direction to maintain accuracy for AI assistant context.*