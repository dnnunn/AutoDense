#!/usr/bin/env python3
"""
Test lane-focused optimization for EtBr gel analysis.

The real problem: detecting 11/20 lanes (55% miss rate) rather than band detection.
"""

import sys
import yaml
sys.path.append('.')

from autodense_autotune.vision.coordinator import VisionAssistCoordinator, create_default_vision_config

def test_lane_focused_optimization():
    """Test vision-assist with lane detection as primary focus."""
    
    print("🎯 EtBr Lane Detection Optimization Test")
    print("=" * 50)
    
    # Initialize vision-assist coordinator  
    config = create_default_vision_config()
    coordinator = VisionAssistCoordinator(config)
    
    # Create run report focused on LANE detection problem
    lane_focused_problem = {
        'metrics': {
            'lanes_raw': 11,      # Only detecting 11 of 20 expected lanes 
            'bands_raw': 2        # Bands are actually OK for detected lanes
        },
        'observation': {
            'coverage_total': 0.1,     # Low coverage due to missing lanes
            'lane_spacing_cv': 0.25,   # High variability suggests detection issues  
            'detection_confidence': 0.5 # Low confidence indicates problems
        }
    }
    
    print("📊 Problem Analysis:")
    print(f"   Expected lanes: 20 (including MW marker)")
    print(f"   Detected lanes: 11") 
    print(f"   Missing lanes: 9 (45% failure rate)")
    print(f"   Impact: Missing ~18 potential bands (9 lanes × 2 bands each)")
    
    # Get vision-assist suggestions with lane focus
    print("\n🎯 Requesting lane-focused vision-assist...")
    vision_result = coordinator.process_analysis_result(
        input_image_path='samples/etbr_gel.jpg',
        run_report=lane_focused_problem,
        analysis_type='etbr_agarose'
    )
    
    if vision_result['vision_assist_triggered'] and vision_result['success']:
        optimization = coordinator.get_optimization_summary(vision_result)
        
        print(f"\n💡 Lane-focused optimization suggestions:")
        print(f"   Confidence: {optimization['confidence']:.2f}")
        print(f"   Risk Level: {optimization['risk_level']}")
        
        # Analyze the suggestions
        lane_params = {}
        band_params = {}
        
        for param, value in optimization['changes'].items():
            if 'lanes.' in param:
                lane_params[param] = value
            elif 'bands.' in param:
                band_params[param] = value
            else:
                print(f"   General: {param} = {value}")
        
        if lane_params:
            print(f"\n🎯 Lane Detection Optimizations:")
            for param, value in lane_params.items():
                print(f"   {param}: {value}")
        else:
            print(f"\n⚠️  No specific lane optimizations suggested")
            
        if band_params:
            print(f"\n🔬 Band Detection Optimizations:")
            for param, value in band_params.items():
                print(f"   {param}: {value}")
        
        print(f"\n📈 Expected Impact:")
        for improvement, value in optimization['expected_improvements'].items():
            if 'lanes' in improvement.lower():
                print(f"   {improvement}: {value} (current: 11)")
            elif 'bands' in improvement.lower():
                print(f"   {improvement}: {value} (current: 2)")
        
        print(f"\n🧠 Analysis:")
        if lane_params:
            print("✅ Vision-assist correctly identified lane detection as priority")
            print("   Lane parameter optimization should improve detection from 11→20 lanes")
            print("   This would enable detection of ~20-40 bands instead of 2")
        else:
            print("❌ Vision-assist focused on bands rather than primary lane problem")
            print("   This suggests the trigger logic needs refinement for lane detection issues")
            
        print(f"\n💭 Reasoning provided:")
        for reason in optimization['reasoning']:
            print(f"   • {reason}")
            
    else:
        print("❌ Vision-assist failed or wasn't triggered")
        if vision_result.get('error'):
            print(f"   Error: {vision_result['error']}")
    
    return vision_result

def create_lane_optimized_config():
    """Create a manually optimized config focused on lane detection."""
    
    print(f"\n🛠️  Creating Manual Lane-Optimized Config")
    print("-" * 40)
    
    # Load base EtBr config
    with open('configs/etbr.yaml', 'r') as f:
        config = yaml.safe_load(f)
    
    print("Base config lane parameters:")
    print(f"   detect.prominence_frac: {config.get('detect', {}).get('prominence_frac', 'not_set')}")
    print(f"   detect.min_peak_distance_frac: {config.get('detect', {}).get('min_peak_distance_frac', 'not_set')}")
    
    # Apply lane-focused optimizations
    # Make lane detection much more sensitive
    if 'detect' not in config:
        config['detect'] = {}
        
    # Increase sensitivity for lane detection
    config['detect']['prominence_frac'] = 0.025  # Much lower (more sensitive)
    config['detect']['min_peak_distance_frac'] = 0.015  # Allow closer lanes
    
    # Add lane-specific section if available
    if 'lanes' not in config:
        config['lanes'] = {}
        
    config['lanes']['prominence_frac'] = 0.03  # Specific lane prominence
    config['lanes']['min_peak_distance_frac'] = 0.015  # Allow tighter spacing
    
    print(f"\nOptimized for lane detection:")
    print(f"   detect.prominence_frac: {config['detect']['prominence_frac']} (much more sensitive)")
    print(f"   detect.min_peak_distance_frac: {config['detect']['min_peak_distance_frac']} (closer spacing allowed)")
    print(f"   lanes.prominence_frac: {config['lanes']['prominence_frac']} (added)")
    print(f"   lanes.min_peak_distance_frac: {config['lanes']['min_peak_distance_frac']} (added)")
    
    # Save optimized config
    with open('configs/etbr_lane_optimized.yaml', 'w') as f:
        yaml.dump(config, f, default_flow_style=False, sort_keys=False)
        
    print(f"\n✅ Created: configs/etbr_lane_optimized.yaml")
    return 'configs/etbr_lane_optimized.yaml'

if __name__ == "__main__":
    # Test vision-assist with lane focus
    result = test_lane_focused_optimization()
    
    # Create manual lane optimization
    config_path = create_lane_optimized_config()
    
    print(f"\n📋 Next Steps:")
    print(f"1. Test the lane-optimized config:")
    print(f"   ./build.sh run-java etbr_agarose samples/etbr_gel.jpg {config_path} output/test_etbr_lanes --no-exit")
    print(f"2. Expected improvement: 11→20 lanes, 2→20-40 bands")
    print(f"3. Validate that lane detection is the key to EtBr optimization")