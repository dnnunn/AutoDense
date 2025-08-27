# LLM Audit Toolkit - Packaging Guide

> **Doc Meta**
> - **Purpose:** Instructions for packaging and deploying the audit toolkit to other projects
> - **Scope:** Distribution, setup, and integration procedures
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-27

This document explains how to package and distribute the LLM Audit Toolkit for use in other projects.

## Package Contents

The complete toolkit consists of these files:

```
llm-audit-toolkit/
├── audit_orchestrator.py      # Main audit engine
├── audit.sh                   # Wrapper script with venv management
├── requirements.txt           # Python dependencies
├── api-config.properties.example  # API key template
├── .gitignore                 # Git ignore rules
├── README.md                  # Full documentation
└── PACKAGE.md                 # This packaging guide
```

## Distribution Methods

### Method 1: Direct Copy

The simplest approach for sharing with specific projects:

```bash
# From the source project
cd /path/to/source/project
tar -czf llm-audit-toolkit.tar.gz llm-audit-toolkit/

# To the target project
cd /path/to/target/project
tar -xzf llm-audit-toolkit.tar.gz
cd llm-audit-toolkit
./audit.sh
```

### Method 2: Git Subtree (Recommended for Version Control)

For projects under version control that want to maintain sync with updates:

```bash
# Add as subtree from source project
cd /path/to/target/project
git subtree add --prefix=llm-audit-toolkit \
  /path/to/source/project/llm-audit-toolkit main --squash

# Later updates can be pulled
git subtree pull --prefix=llm-audit-toolkit \
  /path/to/source/project/llm-audit-toolkit main --squash
```

### Method 3: Standalone Repository

For broader distribution:

```bash
# Create a new repository
mkdir llm-audit-toolkit-standalone
cd llm-audit-toolkit-standalone
git init

# Copy toolkit files
cp -r /path/to/source/llm-audit-toolkit/* .
git add .
git commit -m "Initial release of LLM Audit Toolkit"

# Users can then clone
git clone https://github.com/yourorg/llm-audit-toolkit.git
```

### Method 4: ZIP Distribution

For sharing without version control:

```bash
cd llm-audit-toolkit
zip -r ../llm-audit-toolkit-v1.0.zip . -x "*.pyc" "*/__pycache__/*" ".audit_venv/*"
```

## Installation Instructions for Recipients

### Quick Start for Recipients

1. **Extract/Copy** the toolkit to your project directory
2. **Configure API keys**: Copy `api-config.properties.example` to `api-config.properties` and add your API keys
3. **Run**: Execute `./audit.sh` from your project root

### Detailed Setup

```bash
# Navigate to your project
cd /path/to/your/project

# Extract the toolkit (if using archive)
tar -xzf llm-audit-toolkit.tar.gz
# OR unzip llm-audit-toolkit.zip

# Set up configuration
cd llm-audit-toolkit
cp api-config.properties.example api-config.properties
# Edit api-config.properties with your API keys

# Make script executable (if needed)
chmod +x audit.sh

# Run the audit from your project root
cd ..
./llm-audit-toolkit/audit.sh
```

## Integration Examples

### For Java Projects

```bash
# Project structure:
my-java-project/
├── src/
├── pom.xml
├── llm-audit-toolkit/
└── .gitignore  # Add: llm-audit-toolkit/api-config.properties

# Run audit
cd my-java-project
./llm-audit-toolkit/audit.sh --project-name "MyJavaProject"
```

### For Python Projects

```bash
# Project structure:
my-python-project/
├── src/
├── requirements.txt
├── llm-audit-toolkit/
└── .gitignore  # Add: llm-audit-toolkit/api-config.properties

# Run audit
cd my-python-project
./llm-audit-toolkit/audit.sh --providers openai,anthropic
```

### For JavaScript/Node.js Projects

```bash
# Project structure:
my-node-project/
├── src/
├── package.json
├── node_modules/  # Will be excluded automatically
├── llm-audit-toolkit/
└── .gitignore  # Add: llm-audit-toolkit/api-config.properties

# Run audit
cd my-node-project
./llm-audit-toolkit/audit.sh --consensus gemini
```

## Customization for Specific Environments

### Enterprise Environments

For organizations with specific requirements:

1. **Modify exclusion patterns** in `audit_orchestrator.py`:
```python
ENTERPRISE_EXCLUDES = DEFAULT_EXCLUDES + [
    "proprietary_libs/",
    "vendor/",
    "*.proprietary",
]
```

2. **Set up organization-specific models**:
```python
ORG_MODELS = {
    "openai": "gpt-4o",  # Use specific model versions
    "anthropic": "claude-3-opus-20240229",
}
```

3. **Configure proxy settings** for corporate networks (in requirements.txt):
```
httpx[socks]>=0.25.0  # Add SOCKS proxy support if needed
```

### CI/CD Integration Templates

#### GitHub Actions

Create `.github/workflows/audit.yml`:

```yaml
name: Code Audit
on:
  schedule:
    - cron: '0 8 * * 1'  # Weekly Monday 8AM UTC
  workflow_dispatch:

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Setup Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      - name: Run Audit
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
        run: |
          chmod +x llm-audit-toolkit/audit.sh
          ./llm-audit-toolkit/audit.sh --providers openai,gemini
      - name: Upload Results
        uses: actions/upload-artifact@v4
        with:
          name: audit-results
          path: audits/
          retention-days: 30
```

#### GitLab CI

Create `.gitlab-ci.yml` section:

```yaml
code_audit:
  stage: quality
  image: python:3.11
  script:
    - chmod +x llm-audit-toolkit/audit.sh
    - ./llm-audit-toolkit/audit.sh --providers openai,anthropic
  artifacts:
    paths:
      - audits/
    expire_in: 1 month
  only:
    - schedules
    - web
  variables:
    OPENAI_API_KEY: $OPENAI_API_KEY
    ANTHROPIC_API_KEY: $ANTHROPIC_API_KEY
```

## Version Management

### Semantic Versioning

Tag releases with semantic versions:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Changelog

Maintain a `CHANGELOG.md`:

```markdown
# Changelog

## [1.0.0] - 2025-08-27
### Added
- Initial release of LLM Audit Toolkit
- Support for OpenAI, Anthropic, Gemini, and xAI providers
- Automatic virtual environment management
- Smart file exclusion patterns

### Changed
- Enhanced exclusion patterns for better cross-project compatibility

### Fixed
- Asyncio handling for concurrent provider requests
```

## Testing the Package

Before distributing, test the package:

```bash
# Test in a clean environment
mkdir test-project
cd test-project

# Copy/extract toolkit
cp -r ../llm-audit-toolkit .

# Test with minimal setup
echo "# Test Project" > README.md
echo "print('hello world')" > test.py

# Run audit
./llm-audit-toolkit/audit.sh --providers openai
```

## Support and Maintenance

### Documentation Updates

When distributing, ensure documentation matches your version:

1. Update version references in README.md
2. Update last-verified dates in doc meta blocks
3. Include any environment-specific notes

### Issue Tracking

If creating a public repository, set up issue templates:

```markdown
# .github/ISSUE_TEMPLATE/bug_report.md
---
name: Bug report
about: Create a report to help us improve
---

**Environment:**
- OS: [e.g., macOS, Ubuntu]
- Python version: [e.g., 3.11]
- Providers used: [e.g., openai, gemini]

**Description:**
A clear description of the issue.

**Steps to Reproduce:**
1. Step 1
2. Step 2

**Expected vs Actual Behavior:**
What you expected vs what happened.
```

## Security Considerations

When packaging for distribution:

1. **Never include actual API keys** in the package
2. **Remind users to add** `api-config.properties` to their `.gitignore`
3. **Document security best practices** in the README
4. **Consider adding a security policy** if creating a public repo

Example security note for recipients:

```markdown
## Security Notice

- **Never commit API keys** to version control
- **Use environment variables** or secure config management in production
- **Regularly rotate API keys** especially if they might be compromised
- **Review audit outputs** before sharing - they may contain sensitive code snippets
```

This packaging guide ensures the LLM Audit Toolkit can be easily distributed and integrated into any project while maintaining security and usability standards.