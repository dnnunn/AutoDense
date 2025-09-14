# Session Startup Procedures

> **Doc Meta**
> - **Purpose:** Standardized session initialization to eliminate environment setup time waste
> - **Scope:** Essential context loading and environment validation for productive development sessions
> - **Owner:** @[PROJECT_OWNER]
> - **Last-verified:** [CURRENT_DATE]

## 🚀 Mandatory Session Start Checklist

**CRITICAL:** Follow this checklist every session to prevent wasting 20+ minutes rediscovering basic facts.

### 1. **Load Project Context** [2 minutes]
```bash
# Navigate to project root
cd /path/to/[PROJECT_NAME]

# Read current project primer
cat [CLAUDE.md or PROJECT_CONTEXT.md]

# Check recent session summaries for context
ls -la SessionSummaries/ | tail -3
cat SessionSummaries/[MOST_RECENT_SESSION].md
```

### 2. **Verify Environment Setup** [3 minutes]
```bash
# Load environment (customize for your stack)
[ENVIRONMENT_ACTIVATION_COMMAND]
# Examples:
# source .venv/bin/activate           # Python
# source ~/.nvm/nvm.sh && nvm use     # Node.js  
# export JAVA_HOME=/path/to/java      # Java
# source ~/.cargo/env                 # Rust

# Verify critical tools
[BUILD_TOOL] --version    # mvn, npm, cargo, go, etc.
[RUNTIME] --version       # java, python, node, etc.
[KEY_DEPENDENCIES]        # Database, services, etc.
```

### 3. **Load Current Structure** [2 minutes]
```bash
# Read latest structure documentation
cat StructureDocs/[MOST_RECENT_STRUCTURE].md

# Verify critical paths exist
ls -la [KEY_DIRECTORY_1]
ls -la [KEY_DIRECTORY_2]
ls -la [BUILD_OUTPUT_DIRECTORY]
```

### 4. **Check Recent Issues & Next Steps** [3 minutes]
```bash
# Review outstanding issues
cat Issues/[MOST_RECENT_ISSUES].md

# Check priority tasks
cat NextSteps/[MOST_RECENT_NEXTSTEPS].md

# Verify any blocked items
[CHECK_FOR_BLOCKERS_COMMAND]
```

### 5. **Validate Build System** [5 minutes]
```bash
# Quick build verification
[BUILD_COMMAND]
# Examples:
# mvn compile -q
# npm run build
# cargo check
# go build ./...

# Run quick test if applicable
[QUICK_TEST_COMMAND]
# Examples:
# npm test -- --quick
# cargo test --lib
# python -m pytest tests/quick/
# go test -short ./...
```

## 📋 Project-Specific Quick Reference

### Critical Paths (CUSTOMIZE FOR YOUR PROJECT)
- **Root:** `/path/to/[PROJECT_NAME]`
- **Source:** `src/` or `[SOURCE_DIRECTORY]`
- **Build Output:** `target/`, `dist/`, `build/`
- **Configuration:** `config/`, `settings/`, `.env`
- **Documentation:** `docs/`
- **Tests:** `tests/`, `spec/`, `__tests__/`

### Environment Commands (CUSTOMIZE FOR YOUR STACK)
```bash
# Development environment activation
[ENVIRONMENT_SETUP_COMMAND]

# Build system commands
[BUILD_COMMAND]                # Full build
[QUICK_BUILD_COMMAND]          # Fast compilation
[CLEAN_BUILD_COMMAND]          # Clean rebuild

# Testing commands  
[UNIT_TEST_COMMAND]            # Unit tests
[INTEGRATION_TEST_COMMAND]     # Integration tests
[QUICK_TEST_COMMAND]           # Fast test subset

# Development server/services
[DEV_SERVER_START_COMMAND]     # Start dev server
[DATABASE_START_COMMAND]       # Start database
[SERVICES_START_COMMAND]       # Start dependencies
```

### Common File Locations (CUSTOMIZE FOR YOUR PROJECT)
- **Main Config:** `[MAIN_CONFIG_FILE]`
- **Environment File:** `[ENV_FILE_LOCATION]` 
- **Build Config:** `[BUILD_CONFIG_FILE]`
- **Dependencies:** `[PACKAGE_FILE]` (package.json, pom.xml, Cargo.toml, etc.)
- **Main Entry:** `[MAIN_FILE_LOCATION]`

## 🚨 Red Flags - Stop and Document If:

### Environment Issues
- [ ] Commands fail with "command not found"
- [ ] Build fails due to missing dependencies  
- [ ] Environment variables not set
- [ ] Database/services not accessible
- [ ] File permissions issues

### Documentation Gaps
- [ ] No recent structure documentation
- [ ] Missing environment setup procedures
- [ ] Outdated next steps or issues
- [ ] Broken links in documentation
- [ ] Missing critical file locations

### Project State Issues  
- [ ] Uncommitted changes from previous session
- [ ] Failed tests blocking development
- [ ] Missing or corrupted build artifacts
- [ ] Configuration drift from expected state

**ACTION:** If any red flags appear, stop and update documentation immediately before continuing.

## 🎯 Session Success Criteria

### Ready to Begin Development When:
- [ ] Environment activated and verified
- [ ] Build system working
- [ ] Critical paths confirmed
- [ ] Recent context loaded (issues, next steps, structure)
- [ ] No red flags present
- [ ] Clear understanding of current project state

### Time Investment
- **Total Startup Time:** ~15 minutes first session, ~5 minutes subsequent sessions
- **Time Savings:** ~20-30 minutes per session vs. ad-hoc discovery
- **ROI:** Pays for itself within 2-3 sessions

## 📝 Session Notes Template

```markdown
# Session: [DATE] - [BRIEF_DESCRIPTION]

## Environment Status
- [ ] Environment activated successfully
- [ ] Build system verified
- [ ] Tests passing
- [ ] Dependencies resolved

## Context Loaded
- Last session: [DATE] - [BRIEF_SUMMARY]
- Current priority: [TOP_PRIORITY_TASK]
- Known blockers: [LIST_BLOCKERS]

## Session Goals
1. [PRIMARY_GOAL]
2. [SECONDARY_GOAL]
3. [TERTIARY_GOAL]
```

---

**Remember:** 15 minutes of systematic startup saves 30+ minutes of random discovery. Follow this checklist religiously to maintain development velocity.