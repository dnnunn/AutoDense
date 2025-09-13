#!/usr/bin/env python3
"""
Quick test to validate UI fixes for critical errors.
"""

import sys
from pathlib import Path

def test_ui_fixes():
    """Test that the UI fixes are applied correctly."""
    ui_file = Path("/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/ui/streamlit_autodense_app.py")

    if not ui_file.exists():
        print(f"ERROR: UI file not found: {ui_file}")
        return False

    content = ui_file.read_text()

    fixes_validated = 0
    total_fixes = 4

    # Fix 1: Check that problematic ad_pipeline_run(base) call is removed/commented
    print("[TEST 1] Checking ad_pipeline_run(base) fix...")
    if "ad_pipeline_run(base))" in content and "REMOVED" in content:
        print("SUCCESS: Problematic call is commented out")
        fixes_validated += 1
    elif "ad_pipeline_run(base))" not in content:
        print("SUCCESS: Problematic call was completely removed")
        fixes_validated += 1
    else:
        print("FAILED: Problematic ad_pipeline_run(base) call still exists")

    # Fix 2: Check that sharpen parameter collection is disabled/commented
    print("\n[TEST 2] Checking sharpen parameter fix...")
    if 'manual_params["sharpen"]' in content and "TEMPORARILY DISABLED" in content:
        print("SUCCESS: Sharpen parameter collection is commented out")
        fixes_validated += 1
    elif 'manual_params["sharpen"]' not in content:
        print("SUCCESS: Sharpen parameter collection was removed")
        fixes_validated += 1
    else:
        print("FAILED: Sharpen parameter collection still active")

    # Fix 3: Check that parameter filtering function exists
    print("\n[TEST 3] Checking parameter filtering function...")
    if "def filter_supported_preproc_params" in content:
        print("SUCCESS: Parameter filtering function added")
        fixes_validated += 1
    else:
        print("FAILED: Parameter filtering function not found")

    # Fix 4: Check that image standardization is added to ChatGPT calls
    print("\n[TEST 4] Checking image standardization for ChatGPT...")
    if "_standardize_image_size(pil_img)" in content and "def _standardize_image_size" in content:
        print("SUCCESS: Image standardization added to ChatGPT calls")
        fixes_validated += 1
    else:
        print("FAILED: Image standardization not found")

    # Summary
    print(f"\n" + "="*50)
    print("FIX VALIDATION SUMMARY")
    print("="*50)
    print(f"Fixes Validated: {fixes_validated}/{total_fixes}")
    print(f"Success Rate: {(fixes_validated/total_fixes)*100:.1f}%")

    if fixes_validated == total_fixes:
        print("\nSUCCESS: All fixes validated!")
        return True
    else:
        print(f"\nFAILED: {total_fixes - fixes_validated} fixes not properly applied")
        return False

if __name__ == "__main__":
    success = test_ui_fixes()
    sys.exit(0 if success else 1)