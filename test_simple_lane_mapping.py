# test_simple_lane_mapping.py
"""
Test script for the simplified lane mapping system.
Demonstrates the two-point calibration approach without requiring streamlit_drawable_canvas.
"""

import numpy as np
from PIL import Image, ImageDraw
from pathlib import Path

# Test imports
from autodense.preprocess.simple_lane_mapping import (
    TwoPointCalibration, calculate_lane_positions, add_inset_to_boundaries,
    boundaries_to_dataframe, validate_calibration_points, get_recommended_gel_settings
)
from autodense.preprocess.lane_aware_policy import (
    lane_aware_guarded_preprocess, generate_lane_quality_report
)

def create_synthetic_gel_image(width: int = 800, height: int = 600, n_lanes: int = 12) -> Image.Image:
    """Create a synthetic gel image for testing"""
    # Create base image with gradient background
    img_array = np.zeros((height, width, 3), dtype=np.uint8)
    
    # Add gradient background (darker at top, lighter at bottom)
    for y in range(height):
        intensity = int(30 + (y / height) * 50)  # Dark background
        img_array[y, :] = [intensity, intensity, intensity]
    
    # Add lane patterns
    lane_width = width // n_lanes
    for i in range(n_lanes):
        lane_center = int((i + 0.5) * lane_width)
        lane_left = max(0, lane_center - lane_width//3)
        lane_right = min(width, lane_center + lane_width//3)
        
        # Add some bands (brighter regions)
        for band_y in [height//4, height//2, 3*height//4]:
            band_height = 20
            for y in range(max(0, band_y - band_height//2), 
                          min(height, band_y + band_height//2)):
                # Make MW lane (first) brighter
                intensity = 150 if i == 0 else 120
                img_array[y, lane_left:lane_right] = [intensity, intensity, intensity]
    
    return Image.fromarray(img_array)

def test_two_point_calibration():
    """Test the two-point calibration system"""
    print("🧪 Testing Two-Point Lane Calibration System")
    print("=" * 50)
    
    # Create synthetic gel
    gel_image = create_synthetic_gel_image(width=800, height=600, n_lanes=12)
    print(f"✅ Created synthetic gel image: {gel_image.size}")
    
    # Simulate user clicks (MW lane 1 and reference lane 12)
    point1_x, point1_lane = 50, 1    # MW lane center
    point2_x, point2_lane = 750, 12  # Last lane center
    total_lanes = 12
    
    print(f"📍 Calibration points: Lane {point1_lane} at x={point1_x}, Lane {point2_lane} at x={point2_x}")
    
    # Validate calibration
    is_valid, msg = validate_calibration_points(
        point1_x, point1_lane, point2_x, point2_lane, total_lanes, gel_image.width
    )
    print(f"🔍 Validation: {msg}")
    
    if not is_valid:
        print("❌ Test failed at validation step")
        return False
    
    # Create calibration
    calibration = TwoPointCalibration(
        point1_x=point1_x, point1_lane=point1_lane,
        point2_x=point2_x, point2_lane=point2_lane,
        total_lanes=total_lanes
    )
    
    print(f"📏 Calculated lane spacing: {calibration.lane_spacing_px:.2f} pixels")
    print(f"📏 Calculated lane width: {calibration.lane_width_px:.2f} pixels")
    
    # Generate lane boundaries
    boundaries = calculate_lane_positions(calibration)
    print(f"✅ Generated {len(boundaries)} lane boundaries")
    
    # Add inset
    inset_boundaries = add_inset_to_boundaries(boundaries, inset_px=5.0)
    print("✅ Applied 5px inset to lane boundaries")
    
    # Display results
    df = boundaries_to_dataframe(inset_boundaries)
    print("\n📊 Lane Boundaries:")
    print(df.head())
    print(f"... (showing first 5 of {len(df)} lanes)")
    
    return True, gel_image, inset_boundaries

def test_lane_aware_preprocessing(gel_image: Image.Image, boundaries):
    """Test the lane-aware preprocessing policy"""
    print("\n🧠 Testing Lane-Aware Preprocessing")
    print("=" * 40)
    
    # Run lane-aware preprocessing
    outcome = lane_aware_guarded_preprocess(
        gel_image,
        lane_boundaries=boundaries,
        mw_lane_index=1
    )
    
    print(f"🎯 Preprocessing decision: {outcome.mode}")
    print(f"📊 Analyzed {len(outcome.lane_metrics)} lanes")
    print(f"⚠️ Problematic lanes: {outcome.problematic_lanes}")
    print(f"🧬 MW lane quality (SNR): {outcome.mw_lane_quality:.2f}")
    
    # Generate quality report
    report = generate_lane_quality_report(outcome)
    print(f"\n📋 Overall quality assessment: {report['overall_quality']}")
    print(f"✅ Good lanes: {report['total_lanes'] - len(report['problematic_lanes'])}/{report['total_lanes']}")
    
    return outcome, report

def test_gel_type_settings():
    """Test gel type-specific settings"""
    print("\n⚙️ Testing Gel Type Settings")
    print("=" * 30)
    
    for gel_type in ["sds_page", "etbr_agarose"]:
        for n_lanes in [8, 12, 16]:
            settings = get_recommended_gel_settings(gel_type, n_lanes)
            print(f"{gel_type} ({n_lanes} lanes): {settings['description']}")
            print(f"  Recommended inset: {settings['recommended_inset']}px")
            print(f"  Typical spacing: {settings['typical_spacing_range']}px")

def main():
    """Run all tests"""
    print("🚀 AutoDense Simple Lane Mapping Test Suite")
    print("=" * 60)
    
    try:
        # Test 1: Two-point calibration
        success, gel_image, boundaries = test_two_point_calibration()
        if not success:
            return
        
        # Test 2: Lane-aware preprocessing  
        outcome, report = test_lane_aware_preprocessing(gel_image, boundaries)
        
        # Test 3: Gel type settings
        test_gel_type_settings()
        
        print("\n🎉 All tests completed successfully!")
        print("\n💡 To use in Streamlit:")
        print("   streamlit run ui/streamlit_app_simple.py")
        print("\n📁 Integration status:")
        print("   ✅ Two-point lane calibration")
        print("   ✅ Lane boundary calculation")
        print("   ✅ AI-guided preprocessing with lane awareness")
        print("   ⏳ Full integration with band detection pipeline")
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed with error: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    main()