# LLM Debug Toolkit - Packaging Guide

> **Doc Meta**
> - **Purpose:** Instructions for packaging and deploying the debug toolkit to other projects
> - **Scope:** Distribution, setup, and integration procedures for interactive debugging
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-27

This document explains how to package and distribute the LLM Debug Toolkit for use in other projects.

## Package Contents

The complete debug toolkit consists of these files:

```
llm-debug-toolkit/
├── debug_orchestrator.py         # Main interactive debug engine
├── debug.sh                      # Wrapper script with venv management
├── requirements.txt              # Python dependencies
├── api-config.properties.example # API key template
├── .gitignore                    # Git ignore rules
├── README.md                     # Full documentation
└── PACKAGE.md                    # This packaging guide
```

## Key Differences from Audit Toolkit

The Debug Toolkit is specifically designed for interactive debugging sessions:

- **Interactive Prompting**: Guided prompt engineering with debugging best practices
- **Problem-Focused**: Targets specific bugs rather than general code quality
- **Real-Time Guidance**: Walks users through effective debugging prompt creation
- **Action-Oriented Output**: Produces immediate actionable debugging steps
- **Session-Based**: Creates timestamped debug sessions with comprehensive documentation

## Distribution Methods

### Method 1: Direct Copy for Debugging Teams

```bash
# Package for distribution
cd /path/to/source/project
tar -czf llm-debug-toolkit-v1.0.tar.gz llm-debug-toolkit/

# Deploy to target project
cd /path/to/buggy/project
tar -xzf llm-debug-toolkit-v1.0.tar.gz
cd llm-debug-toolkit
cp api-config.properties.example api-config.properties
# Edit API keys, then:
./debug.sh
```

### Method 2: Team Repository Setup

For development teams wanting shared debugging resources:

```bash
# Create team debugging repository
mkdir team-debug-toolkit
cd team-debug-toolkit
git init

# Copy debug toolkit
cp -r /path/to/source/llm-debug-toolkit/* .
git add .
git commit -m "Add LLM Debug Toolkit for team debugging sessions"

# Team members can then add to any project
cd /path/to/project/with/bugs
git submodule add https://github.com/yourteam/team-debug-toolkit.git llm-debug-toolkit
```

### Method 3: CI/CD Integration for Post-Incident Analysis

Create automated debugging for incident response:

```yaml
# .github/workflows/debug-incident.yml
name: Post-Incident Debug Analysis
on:
  workflow_dispatch:
    inputs:
      incident_description:
        description: 'Describe the issue that occurred'
        required: true
        type: string
      affected_components:
        description: 'Components likely involved (comma-separated)'
        required: false
        type: string
      error_info:
        description: 'Error messages or stack traces'
        required: false
        type: string

jobs:
  debug:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Setup Debug Toolkit
        run: |
          curl -L https://github.com/yourorg/llm-debug-toolkit/archive/v1.0.tar.gz | tar xz
          mv llm-debug-toolkit-1.0 llm-debug-toolkit
      - name: Create Debug Prompt
        run: |
          cat > debug_input.txt << EOF
          ## Problem Statement
          ${{ github.event.inputs.incident_description }}
          
          ## Likely Components
          ${{ github.event.inputs.affected_components }}
          
          ## Error Information
          ${{ github.event.inputs.error_info }}
          
          ## Context
          Incident occurred at $(date -u). Repository: ${{ github.repository }}
          EOF
      - name: Run Debug Analysis
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
        run: |
          cd llm-debug-toolkit
          # Non-interactive mode with pre-built prompt
          echo "cat debug_input.txt | python debug_orchestrator.py debug --providers openai,gemini"
      - name: Upload Debug Results
        uses: actions/upload-artifact@v4
        with:
          name: incident-debug-analysis
          path: debug_sessions/
```

## Installation Instructions for Recipients

### Quick Start for Development Teams

1. **Extract to your project**:
   ```bash
   cd /path/to/your/buggy/project
   tar -xzf llm-debug-toolkit-v1.0.tar.gz
   ```

2. **Configure API keys**:
   ```bash
   cd llm-debug-toolkit
   cp api-config.properties.example api-config.properties
   # Edit with your team's API keys
   ```

3. **Run interactive debugging**:
   ```bash
   ./debug.sh
   ```

### For Bug Triage Workflows

```bash
# Project structure for bug investigation:
project-with-bugs/
├── src/
├── tests/
├── bug-reports/
├── llm-debug-toolkit/      # Debug analysis tools
└── debug_sessions/         # Generated debug analyses

# Workflow:
cd project-with-bugs
./llm-debug-toolkit/debug.sh --project-name "ProductionBug-#1234"
# Follow interactive prompts...
# Results saved to debug_sessions/2025-08-27_HHMMSS/
```

## Integration Examples by Use Case

### Bug Triage Process

```bash
# When a bug report comes in:

# 1. Create debug session with issue context
./llm-debug-toolkit/debug.sh --project-name "Issue-#5432-LoginFailure"

# 2. Follow the interactive prompts to describe:
#    - Expected vs actual behavior
#    - Reproduction steps
#    - Likely components
#    - Error information

# 3. Review multi-LLM analysis in debug_sessions/
#    - CONSENSUS.md for unified analysis
#    - ACTION_ITEMS.md for specific debugging steps
#    - Individual provider perspectives

# 4. Implement fixes based on recommendations
# 5. Document resolution in the debug session folder
```

### Production Incident Response

```bash
# During or after a production incident:

# 1. Gather incident details
INCIDENT_TIME="2025-08-27 14:30 UTC"
AFFECTED_USERS="Premium subscribers"
ERROR_PATTERN="503 Service Unavailable"

# 2. Run focused debug session
./llm-debug-toolkit/debug.sh --project-name "Production-Incident-${INCIDENT_TIME}"

# 3. Use guided prompts to capture:
#    - Timeline of the incident
#    - System behavior during failure
#    - Recent deployments or changes
#    - Performance metrics and logs

# 4. Get multi-perspective analysis for:
#    - Root cause identification
#    - Immediate mitigation steps
#    - Long-term prevention measures
```

### Code Review Debugging

```bash
# When code review reveals potential bugs:

# 1. Run debug session focused on suspicious code
./llm-debug-toolkit/debug.sh --project-name "CodeReview-PotentialRaceCondition"

# 2. Describe the suspected issue:
#    - Code patterns that look problematic
#    - Potential failure scenarios
#    - Components that interact

# 3. Get analysis on:
#    - Whether the suspected bug is real
#    - How to reproduce if it exists
#    - Best approaches to fix
```

## Customization for Different Environments

### Enterprise Security Environments

For organizations with strict security requirements:

1. **Air-gapped deployment**: Package with offline models or internal LLM APIs
2. **Sensitive code handling**: Add additional exclusion patterns for proprietary code
3. **Audit trail**: Enhanced logging for compliance requirements

```python
# In debug_orchestrator.py, add enterprise exclusions:
ENTERPRISE_EXCLUDES = DEFAULT_EXCLUDES + [
    "proprietary/",
    "vendor_confidential/", 
    "internal_apis/",
    "*.confidential",
    "security_configs/",
]
```

### Development Team Workflows

For teams with specific debugging processes:

```bash
# Create team-specific wrapper script: team_debug.sh
#!/bin/bash

# Pre-populate common debugging context
export PROJECT_NAME="TeamProject-$(date +%Y%m%d)"
export DEFAULT_COMPONENTS="AuthService, PaymentProcessor, UserManagement"

# Set team's preferred provider configuration  
./debug.sh \
  --providers openai,anthropic \
  --consensus anthropic \
  --project-name "$PROJECT_NAME"

# Post-process results for team's workflow
echo "Debug session complete. Next steps:"
echo "1. Review CONSENSUS.md"
echo "2. Create JIRA tickets from ACTION_ITEMS.md"  
echo "3. Assign to appropriate team members"
echo "4. Schedule fix implementation"
```

### Multi-Language Projects

For projects using multiple programming languages:

```python
# Enhanced language-specific file prioritization in debug_orchestrator.py
def score(p: Path) -> int:
    ext = p.suffix.lower()
    return {
        # Backend languages (high priority for business logic bugs)
        ".java": 10, ".kt": 10, ".scala": 10,
        ".cs": 10, ".fs": 10, ".vb": 10,
        ".py": 9, ".rb": 9, ".php": 9,
        ".go": 9, ".rs": 9, ".cpp": 9, ".c": 9,
        
        # Frontend languages (medium-high for UI bugs)
        ".ts": 8, ".tsx": 8, ".js": 8, ".jsx": 8,
        ".vue": 8, ".svelte": 8, ".dart": 8,
        
        # Configuration and infrastructure
        ".yaml": 7, ".yml": 7, ".json": 7, ".toml": 7,
        ".dockerfile": 7, ".tf": 7, ".hcl": 7,
        
        # Documentation and markup
        ".md": 6, ".rst": 6, ".html": 6, ".css": 6,
        ".sql": 6, ".graphql": 6,
    }.get(ext, 1)
```

## Version Management & Updates

### Semantic Versioning for Debug Toolkit

```bash
# Tag releases with focus on debugging capabilities:
git tag v1.0.0   # Initial release
git tag v1.1.0   # Enhanced prompt engineering guidance
git tag v1.2.0   # Added new provider support
git tag v2.0.0   # Major changes to interactive flow
```

### Changelog for Debugging Features

```markdown
# Debug Toolkit Changelog

## [1.2.0] - 2025-08-27
### Added
- Interactive prompt engineering with step-by-step guidance
- Multi-LLM consensus building for debug analysis
- Enhanced error information collection
- Debugging best practices integration

### Improved
- Better context gathering for reproduction steps
- More focused component identification
- Actionable output formatting

### Fixed
- Interactive terminal detection issues
- Virtual environment setup on different Python versions
```

## Testing the Debug Package

Before distributing, test with realistic debugging scenarios:

```bash
# Test scenarios:

# 1. Simple Bug Scenario
mkdir test-debug-simple
cd test-debug-simple
echo 'def buggy_function(x): return x / 0' > bug.py
cp -r ../llm-debug-toolkit .
echo "Test the simple division by zero bug" | ./llm-debug-toolkit/debug.sh

# 2. Complex Integration Bug
mkdir test-debug-complex  
cd test-debug-complex
# Create mock complex project structure
mkdir -p src/{auth,payment,user}
echo "Test complex multi-component authentication issue" | ./llm-debug-toolkit/debug.sh

# 3. Performance Issue
mkdir test-debug-performance
cd test-debug-performance
# Create mock performance-related files
echo "Test slow database query performance issue" | ./llm-debug-toolkit/debug.sh
```

## Documentation for Recipients

### Quick Reference Card

Create a quick reference for development teams:

```markdown
# 🐛 Debug Toolkit Quick Reference

## When to Use
- ❌ Bug reports from users
- ❌ Production incidents  
- ❌ Code review concerns
- ❌ Performance issues
- ❌ Integration problems

## Quick Commands
```bash
./llm-debug-toolkit/debug.sh                    # Interactive debugging
./llm-debug-toolkit/debug.sh --providers openai # Single provider
./llm-debug-toolkit/debug.sh --help             # Show all options
```

## Best Debugging Prompts
✅ "Login fails with 401 error after valid credentials on Chrome 120+"
❌ "Login doesn't work"

✅ "Database timeout in UserService.authenticate() line 45 during peak hours"
❌ "Database issues"

## Output Files
- **CONSENSUS.md** → Unified debug plan
- **ACTION_ITEMS.md** → Step-by-step fixes  
- **DEBUG_SUMMARY.md** → Session overview
```

## Support and Maintenance

### Issue Tracking Template

```markdown
## Debug Toolkit Issue Template

**Environment:**
- OS: [e.g., macOS, Ubuntu]
- Python version: [e.g., 3.11]
- Providers used: [e.g., openai, gemini]

**Debugging Context:**
- Bug type: [e.g., authentication, performance, integration]
- Project language: [e.g., Java, Python, JavaScript]

**Issue Description:**
- What debugging question were you trying to answer?
- Which step in the interactive process failed?
- What error message did you receive?

**Expected vs Actual:**
- Expected: [What should have happened]
- Actual: [What happened instead]

**Session Files:**
Please attach the debug session directory if available.
```

This packaging guide ensures the LLM Debug Toolkit can be effectively distributed to development teams and integrated into existing debugging workflows while maintaining its interactive, problem-focused approach to code analysis.