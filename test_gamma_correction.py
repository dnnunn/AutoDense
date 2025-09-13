#!/usr/bin/env python3
"""
Test script to validate gamma correction functionality in AutoDense preprocessing pipeline.
"""

import numpy as np
import tempfile
from pathlib import Path
from PIL import Image
import sys
import os

# Add the autodense modules to the path
sys.path.insert(0, '/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense')

from autodense.preprocess.pipeline import PreprocParams, run

def create_test_image(size=(200, 200)):
    """Create a test image with known brightness characteristics."""
    # Create a gradient image from dark to bright
    gradient = np.linspace(0, 255, size[0])
    image_data = np.tile(gradient[:, np.newaxis], (1, size[1], 3)).astype(np.uint8)
    return Image.fromarray(image_data)

def test_gamma_correction():
    """Test gamma correction with different gamma values."""
    print("🧪 Testing Gamma Correction Functionality")
    print("=" * 50)

    # Create test image
    test_image = create_test_image()

    # Test different gamma values
    gamma_values = [0.5, 1.0, 1.5, 2.0]

    with tempfile.TemporaryDirectory() as temp_dir:
        results = {}

        for gamma in gamma_values:
            print(f"Testing gamma = {gamma}")

            # Create parameters with specific gamma value
            params = PreprocParams(gamma=gamma)

            # Run preprocessing
            processed_img, meta, stages = run(test_image, params, save_dir=Path(temp_dir) / f"gamma_{gamma}")

            # Store results
            results[gamma] = {
                'meta': meta,
                'stages': stages,
                'processed': processed_img
            }

            # Verify gamma is recorded in metadata
            assert meta.gamma == gamma, f"Expected gamma {gamma} in metadata, got {meta.gamma}"

            # Verify gamma stage exists in stages
            assert '55_gamma' in stages, f"Gamma stage missing from stages for gamma={gamma}"

            # Check brightness characteristics
            brightness = np.mean(processed_img)
            print(f"  Gamma {gamma}: Average brightness = {brightness:.3f}")

            print(f"  ✅ Gamma {gamma} test passed")

        # Verify gamma relationships (lower gamma = brighter, higher gamma = darker)
        brightness_0_5 = np.mean(results[0.5]['processed'])
        brightness_1_0 = np.mean(results[1.0]['processed'])
        brightness_2_0 = np.mean(results[2.0]['processed'])

        print(f"\nBrightness comparison:")
        print(f"  Gamma 0.5: {brightness_0_5:.3f} (should be brightest)")
        print(f"  Gamma 1.0: {brightness_1_0:.3f} (baseline)")
        print(f"  Gamma 2.0: {brightness_2_0:.3f} (should be darkest)")

        # Test gamma relationships (with some tolerance for floating point)
        assert brightness_0_5 > brightness_1_0, "Gamma 0.5 should be brighter than gamma 1.0"
        assert brightness_1_0 > brightness_2_0, "Gamma 1.0 should be brighter than gamma 2.0"

        print(f"  ✅ Gamma brightness relationships correct")

        # Test gamma=1.0 (no change)
        original_brightness = np.mean(results[1.0]['stages']['50_contrast'])
        gamma_1_brightness = np.mean(results[1.0]['stages']['55_gamma'])

        # With gamma=1.0, the gamma stage should be nearly identical to the input
        assert abs(original_brightness - gamma_1_brightness) < 1e-6, "Gamma 1.0 should not change image"
        print(f"  ✅ Gamma 1.0 (no correction) test passed")

        # Test stage ordering
        stage_keys = list(results[1.0]['stages'].keys())
        gamma_idx = stage_keys.index('55_gamma')
        contrast_idx = stage_keys.index('50_contrast')
        deskew_idx = stage_keys.index('60_deskew')

        assert contrast_idx < gamma_idx < deskew_idx, "Gamma stage should be between contrast and deskew"
        print(f"  ✅ Stage ordering correct: {stage_keys[contrast_idx]} -> {stage_keys[gamma_idx]} -> {stage_keys[deskew_idx]}")

    print("\n🎉 All gamma correction tests passed!")
    print("✅ Error #1 has been successfully resolved!")

def test_parameter_integration():
    """Test that gamma parameter integrates properly with the full pipeline."""
    print("\n🔧 Testing Parameter Integration")
    print("=" * 30)

    # Test with various parameter combinations
    test_cases = [
        {'gamma': 0.8, 'clahe': True, 'deskew': True},
        {'gamma': 1.2, 'clahe': False, 'deskew': False},
        {'gamma': 1.0, 'modality': 'dna'},
    ]

    test_image = create_test_image()

    for i, params_dict in enumerate(test_cases):
        print(f"Test case {i+1}: {params_dict}")

        params = PreprocParams(**params_dict)
        processed_img, meta, stages = run(test_image, params)

        # Verify all expected stages exist
        expected_stages = ['00_input', '10_gray', '20_norm', '30_bg', '40_denoise', '50_contrast', '55_gamma', '60_deskew']
        for stage in expected_stages:
            assert stage in stages, f"Missing stage {stage}"

        # Verify gamma in metadata matches parameter
        assert meta.gamma == params_dict['gamma'], f"Metadata gamma mismatch"

        print(f"  ✅ Test case {i+1} passed")

    print("✅ Parameter integration tests passed!")

if __name__ == "__main__":
    try:
        test_gamma_correction()
        test_parameter_integration()
        print("\n🎯 SUMMARY: Error #1 (Gamma Parameter Integration) has been successfully completed!")
        print("   - Added gamma parameter to PreprocParams")
        print("   - Implemented _gamma_correct() function")
        print("   - Integrated gamma correction into pipeline stages")
        print("   - Added gamma to PreprocMeta for tracking")
        print("   - All tests pass with expected behavior")

    except Exception as e:
        print(f"\n❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)