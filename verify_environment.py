#!/usr/bin/env python3
"""
AutoDense Environment Verification Script

Run this script to verify your development environment is correctly set up.
Usage: python verify_environment.py
"""

import sys
import os
from pathlib import Path

def check_project_root():
    """Verify we're in the correct project directory."""
    expected_root = Path("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense")
    current_dir = Path.cwd()

    if current_dir != expected_root:
        print(f"❌ Wrong directory: {current_dir}")
        print(f"   Expected: {expected_root}")
        print(f"   Solution: cd {expected_root}")
        return False

    print(f"✅ Correct project root: {current_dir}")
    return True

def check_virtual_environment():
    """Verify virtual environment is active."""
    python_path = sys.executable
    expected_venv = "/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/.venv/bin/python"

    if python_path != expected_venv:
        print(f"❌ Virtual environment not active")
        print(f"   Current Python: {python_path}")
        print(f"   Expected: {expected_venv}")
        print(f"   Solution: source .venv/bin/activate")
        return False

    print(f"✅ Virtual environment active: {python_path}")
    return True

def check_ui_directory():
    """Verify UI directory structure."""
    ui_dir = Path("ui")
    required_files = [
        "ui/components/image_upload.py",
        "ui/utils/state_management.py",
        "ui/tests/test_state_management.py",
        "ui/streamlit_autodense_app.py"
    ]

    missing = []
    for file_path in required_files:
        if not Path(file_path).exists():
            missing.append(file_path)

    if missing:
        print(f"❌ Missing UI files:")
        for file_path in missing:
            print(f"   - {file_path}")
        return False

    print(f"✅ UI directory structure complete")
    return True

def check_python_dependencies():
    """Verify critical Python packages are installed."""
    required_packages = [
        'streamlit',
        'numpy',
        'PIL',
        'pytest'
    ]

    missing = []
    for package in required_packages:
        try:
            __import__(package)
        except ImportError:
            missing.append(package)

    if missing:
        print(f"❌ Missing Python packages:")
        for package in missing:
            print(f"   - {package}")
        print("   Solution: pip install -r requirements.txt")
        return False

    print(f"✅ Required Python packages installed")
    return True

def check_ui_imports():
    """Verify UI modules can be imported."""
    try:
        sys.path.insert(0, "ui")
        import utils.state_management
        import components.image_upload
        print("✅ UI modules import successfully")
        return True
    except ImportError as e:
        print(f"❌ UI module import failed: {e}")
        print("   Solution: cd ui/ before running UI-related commands")
        return False

def main():
    """Run all environment checks."""
    print("🔍 AutoDense Environment Verification")
    print("=" * 40)

    checks = [
        ("Project Root", check_project_root),
        ("Virtual Environment", check_virtual_environment),
        ("UI Directory", check_ui_directory),
        ("Python Dependencies", check_python_dependencies),
        ("UI Module Imports", check_ui_imports)
    ]

    passed = 0
    for name, check_func in checks:
        print(f"\n📋 Checking {name}...")
        if check_func():
            passed += 1

    print("\n" + "=" * 40)
    print(f"📊 Environment Check: {passed}/{len(checks)} passed")

    if passed == len(checks):
        print("🎉 Environment is correctly configured!")
        print("\n💡 Ready for development. Sample commands:")
        print("   cd ui && python -m pytest tests/ -v")
        print("   cd ui && python -m streamlit run streamlit_autodense_app.py")
        return 0
    else:
        print("❌ Environment needs fixes. See messages above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())