# AutoDense Environment Setup Guide

> **Doc Meta**
> - **Purpose:** Definitive reference for Python/Java environment setup and common command patterns
> - **Scope:** All development workflows, testing, builds, and runtime execution
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-14

## 🚨 CRITICAL: Start Every Session With This

**Always run these commands at the beginning of any development session:**

```bash
# SINGLE COMMAND - Navigate to project root from anywhere
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense && pwd

# SINGLE COMMAND - Activate environment and verify
source .venv/bin/activate && which python

# SINGLE COMMAND - Quick environment check
source .venv/bin/activate && python verify_environment.py
```

**⚠️ IMPORTANT:** Due to Claude Code bash behavior, environment activation doesn't persist across separate commands. Always use `&&` to chain commands in a single bash call.

## 📁 Critical Paths (MEMORIZE)

### Project Structure
- **Root**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`
- **UI Code**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/ui/`
- **Python venv**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/`
- **Java/Maven**: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin/`

### Virtual Environment
- **Activation**: `source .venv/bin/activate` (from project root)
- **Python**: `.venv/bin/python`
- **Pip**: `.venv/bin/pip`
- **Pytest**: `.venv/bin/pytest`

## 🐍 Python Development Commands

### Working Directory Context
```bash
# For UI development - ALWAYS cd to ui/ first
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
cd ui

# Now you can run:
python -m pytest tests/
python -m streamlit run streamlit_autodense_app.py
python test_new_state_management.py
```

### Testing Commands (from ui/ directory)
```bash
# Run all tests
python -m pytest

# Run specific test file
python -m pytest tests/test_state_management.py

# Run with verbose output
python -m pytest tests/test_state_management.py -v

# Run specific test class
python -m pytest tests/test_state_management.py::TestMemoryMappedArray -v
```

### Streamlit Commands (from ui/ directory)
```bash
# Standard development server
python -m streamlit run streamlit_autodense_app.py

# With custom port and external access
python -m streamlit run streamlit_autodense_app.py --server.address 0.0.0.0 --server.port 8501
```

## ☕ Java/Maven Development Commands

### Working Directory Context
```bash
# For Java development - ALWAYS cd to plugin directory
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin

# Now you can run Maven commands:
mvn clean compile
mvn test
mvn package
```

## 🔧 Common Issues & Solutions

### Issue: "python: command not found"
```bash
# Solution: Activate virtual environment first
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate
```

### Issue: "pytest: command not found"
```bash
# Solution: Use python -m pytest instead
python -m pytest tests/

# NOT: pytest tests/
```

### Issue: "ModuleNotFoundError: No module named 'utils'"
```bash
# Solution: Make sure you're in the ui/ directory
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/ui
python -m pytest tests/
```

### Issue: Virtual environment not found
```bash
# Check if .venv exists in project root
ls -la /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/

# If missing, recreate:
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 📋 Standard Workflow Patterns

### Python Development Session
```bash
# 1. Navigate and activate
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
source .venv/bin/activate

# 2. Go to UI directory for development
cd ui

# 3. Run tests
python -m pytest tests/ -v

# 4. Start development server (optional)
python -m streamlit run streamlit_autodense_app.py
```

### Java Development Session
```bash
# 1. Navigate to plugin directory
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/autodense/plugin

# 2. Build project
mvn clean compile test package

# 3. Run CLI (example)
java -cp target/classes:target/dependency/* com.betterdairy.autodense.cli.AutotuneAnalysisCLI
```

## 🚀 Quick Reference Commands

### Environment Check
```bash
# Verify all paths and environments
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense && pwd
source .venv/bin/activate && which python
cd ui && python -c "import utils.state_management; print('✅ Python modules OK')"
cd ../autodense/plugin && mvn --version
```

### Reset Environment (if corrupted)
```bash
# Full reset
cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense
rm -rf .venv/
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cd ui && python -m pytest tests/ -x
```

## 📝 Session Checklist

Before starting any development work:

- [ ] `cd /Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense`
- [ ] `source .venv/bin/activate`
- [ ] `which python` shows `.venv/bin/python`
- [ ] For UI work: `cd ui`
- [ ] For Java work: `cd autodense/plugin`
- [ ] Test imports: `python -c "import streamlit, numpy, PIL"`

## 🔗 Integration with CLAUDE.md

**Add this to the top of every session:**
1. Read this environment setup guide first
2. Follow the standard workflow patterns
3. Use the exact command patterns documented here
4. Never assume environment state - always verify

---

**Remember**: The virtual environment is at project root (`.venv/`), UI code is in `ui/`, and you must activate the environment before any Python work.