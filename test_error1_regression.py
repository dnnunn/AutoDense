#!/usr/bin/env python3
"""
Regression test for Error #1: Gamma parameter mismatch between UI and PreprocParams.

This test simulates the original error scenario where the Streamlit UI collected
a gamma parameter but PreprocParams didn't support it, causing a TypeError.
"""

import sys
import traceback

# Add the autodense modules to the path
sys.path.insert(0, '/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense')

def test_error1_regression():
    """Test that Error #1 has been fixed - gamma parameter now supported."""
    print("🔍 Testing Error #1 Regression")
    print("=" * 40)
    print("Original error: UI collects gamma parameter but PreprocParams doesn't support it")
    print()

    try:
        from autodense.preprocess.pipeline import PreprocParams, run
        from PIL import Image
        import numpy as np

        # Simulate the original error scenario:
        # UI collects parameters including gamma, but PreprocParams couldn't handle it

        # This was the problematic scenario that caused TypeError before
        ui_collected_params = {
            'modality': 'sds',
            'gamma': 1.2,  # This parameter caused the original error
            'clahe': True,
            'deskew': True
        }

        print(f"🧪 Testing with UI-collected parameters: {ui_collected_params}")

        # This would have failed before the fix with:
        # TypeError: PreprocParams.__init__() got an unexpected keyword argument 'gamma'
        params = PreprocParams(**ui_collected_params)
        print(f"✅ PreprocParams creation successful with gamma={params.gamma}")

        # Create a simple test image
        test_image = Image.fromarray((np.random.rand(100, 100, 3) * 255).astype(np.uint8))

        # This would have failed before if gamma wasn't integrated into the pipeline
        processed_img, meta, stages = run(test_image, params)

        print(f"✅ Pipeline execution successful")
        print(f"✅ Gamma recorded in metadata: {meta.gamma}")
        print(f"✅ Gamma stage present in pipeline: {'55_gamma' in stages}")

        # Verify the specific issues mentioned in the original error analysis
        assert hasattr(params, 'gamma'), "PreprocParams should have gamma attribute"
        assert params.gamma == 1.2, f"Expected gamma=1.2, got {params.gamma}"
        assert hasattr(meta, 'gamma'), "PreprocMeta should have gamma attribute"
        assert meta.gamma == 1.2, f"Expected meta.gamma=1.2, got {meta.gamma}"
        assert '55_gamma' in stages, "Pipeline should include gamma correction stage"

        print()
        print("🎉 ERROR #1 REGRESSION TEST PASSED!")
        print("   ✅ PreprocParams now accepts gamma parameter")
        print("   ✅ Gamma correction is integrated into pipeline")
        print("   ✅ Gamma value is preserved in metadata")
        print("   ✅ No TypeError when UI passes gamma parameter")

        return True

    except Exception as e:
        print(f"❌ ERROR #1 REGRESSION TEST FAILED!")
        print(f"Error: {e}")
        print("\nFull traceback:")
        traceback.print_exc()
        return False

def test_original_error_scenario():
    """Test the exact scenario that caused the original error."""
    print("\n🔬 Testing Original Error Scenario")
    print("=" * 40)

    try:
        from autodense.preprocess.pipeline import PreprocParams

        # This is exactly what the Streamlit UI was trying to do
        print("Simulating Streamlit UI parameter collection...")

        # UI collects these parameters from user input
        streamlit_ui_params = {
            'modality': 'sds',
            'target_channel': 'auto',
            'polarity': 'auto',
            'bg_method': 'auto',
            'bg_radius_px': 'auto',
            'denoise': 'auto',
            'clahe': True,
            'deskew': True,
            'rectify': False,
            'gamma': 1.5  # This was the problematic parameter
        }

        print("Creating PreprocParams with UI parameters...")

        # Before the fix, this line would cause:
        # TypeError: PreprocParams.__init__() got an unexpected keyword argument 'gamma'
        params = PreprocParams(**streamlit_ui_params)

        print(f"✅ Success! PreprocParams created with all UI parameters")
        print(f"   modality: {params.modality}")
        print(f"   gamma: {params.gamma}")
        print(f"   clahe: {params.clahe}")
        print(f"   deskew: {params.deskew}")

        return True

    except TypeError as e:
        if "unexpected keyword argument 'gamma'" in str(e):
            print(f"❌ ORIGINAL ERROR STILL EXISTS: {e}")
            return False
        else:
            print(f"❌ UNEXPECTED TypeError: {e}")
            return False
    except Exception as e:
        print(f"❌ UNEXPECTED ERROR: {e}")
        return False

if __name__ == "__main__":
    print("🚨 Error #1 Regression Testing Suite")
    print("=" * 50)
    print("Testing that gamma parameter integration has fixed the original UI/backend mismatch")
    print()

    success = True

    # Test 1: General regression test
    if not test_error1_regression():
        success = False

    # Test 2: Original error scenario
    if not test_original_error_scenario():
        success = False

    print("\n" + "=" * 50)
    if success:
        print("🎯 ALL REGRESSION TESTS PASSED!")
        print("✅ Error #1 has been successfully resolved")
        print("✅ UI can now pass gamma parameter to backend without errors")
        print("✅ Gamma correction is fully integrated into preprocessing pipeline")
    else:
        print("❌ REGRESSION TESTS FAILED!")
        print("⚠️  Error #1 may not be fully resolved")
        sys.exit(1)