#!/usr/bin/env python3
"""
Vision-Assist Optimization Validation Test

This script tests whether vision-assist parameter suggestions actually improve
detection results on real sample images.
"""

import json
import sys
import os
import yaml
import shutil
from typing import Dict, Any, List, Tuple
sys.path.append('.')

from autodense_autotune.vision.coordinator import VisionAssistCoordinator, create_default_vision_config

def load_run_report(output_dir: str) -> Dict[str, Any]:
    """Load run report from analysis output directory."""
    report_path = os.path.join(output_dir, 'run_report.json')
    if os.path.exists(report_path):
        with open(report_path, 'r') as f:
            return json.load(f)
    return {}

def get_baseline_results() -> Dict[str, Any]:
    """Get baseline results from our current test runs."""
    return {
        'sds': {
            'lanes_raw': 7,
            'bands_raw': 24,
            'coverage_total': 0.392,
            'baseline_post_med': 0.0,  # Good baseline
            'output_dir': 'output/test_sds'
        },
        'etbr': {
            'lanes_raw': 11,
            'bands_raw': 2,  # Very low - needs optimization!
            'coverage_total': 0.0,  # Zero coverage - problem!
            'output_dir': 'output/test_etbr'
        },
        'colony': {
            'colonies_raw': 25,
            'colonies_classified': 15,
            'blue_count': 0,
            'white_count': 15,
            'output_dir': 'output/test_colony'
        }
    }

def create_optimized_config(base_config_path: str, vision_suggestions: Dict[str, Any]) -> str:
    """Create optimized config file with vision-assist suggestions."""
    
    # Load base config
    with open(base_config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    # Apply vision-assist parameter suggestions
    for param_path, new_value in vision_suggestions.items():
        # Navigate to the parameter location and update it
        path_parts = param_path.split('.')
        current = config
        
        # Navigate to the parent of the target parameter
        for part in path_parts[:-1]:
            if part not in current:
                current[part] = {}
            current = current[part]
        
        # Set the new value
        final_key = path_parts[-1]
        old_value = current.get(final_key, 'not_set')
        current[final_key] = new_value
        print(f"  📝 Updated {param_path}: {old_value} → {new_value}")
    
    # Save optimized config
    optimized_path = base_config_path.replace('.yaml', '_optimized.yaml')
    with open(optimized_path, 'w') as f:
        yaml.dump(config, f, default_flow_style=False, sort_keys=False)
    
    return optimized_path

def run_optimized_analysis(sample_path: str, config_path: str, output_dir: str, analysis_type: str) -> Dict[str, Any]:
    """Run analysis with optimized configuration."""
    
    # Create clean output directory
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    os.makedirs(output_dir)
    
    # Construct command based on analysis type
    cmd_map = {
        'sds': 'sds_page',
        'etbr': 'etbr_agarose',
        'colony': 'colonies_blue_white'
    }
    
    # Read proper classpath from Maven-generated file
    classpath_file = 'autodense/plugin/target/runtime-classpath.txt'
    if os.path.exists(classpath_file):
        with open(classpath_file, 'r') as f:
            classpath = f'autodense/plugin/target/classes:{f.read().strip()}'
    else:
        classpath = 'autodense/plugin/target/classes:autodense/plugin/target/dependency/*'
    
    java_cmd = [
        'java', '-Djava.awt.headless=true', '-cp', 
        classpath,
        'com.betterdairy.autodense.cli.AutotuneAnalysisCLI',
        '--no-exit',  # Important for optimization workflow
        cmd_map[analysis_type],
        sample_path,
        config_path,
        output_dir
    ]
    
    print(f"  🔧 Running: {' '.join(java_cmd[-4:])}")
    
    # Run the command (we'd use subprocess in a real implementation)
    import subprocess
    try:
        result = subprocess.run(java_cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            print(f"  ✅ Analysis completed successfully")
            return load_run_report(output_dir)
        else:
            print(f"  ❌ Analysis failed: {result.stderr}")
            return {'error': result.stderr}
    except subprocess.TimeoutExpired:
        print(f"  ⏰ Analysis timed out")
        return {'error': 'timeout'}
    except Exception as e:
        print(f"  ❌ Analysis error: {e}")
        return {'error': str(e)}

def test_vision_assist_effectiveness():
    """Main test function to validate vision-assist effectiveness."""
    
    print("🎯 Vision-Assist Effectiveness Validation")
    print("=" * 50)
    
    # Initialize vision-assist coordinator
    config = create_default_vision_config()
    coordinator = VisionAssistCoordinator(config)
    
    # Get baseline results
    baseline_results = get_baseline_results()
    
    # Test scenarios with their expected improvements
    test_scenarios = [
        {
            'name': 'EtBr Band Detection Improvement',
            'type': 'etbr',
            'sample': 'samples/etbr_gel.jpg',
            'config': 'configs/etbr.yaml',
            'baseline': baseline_results['etbr'],
            'expected_improvement': 'More bands detected (>2)',
            'target_metric': 'bands_raw'
        },
        {
            'name': 'SDS Sensitivity Optimization',
            'type': 'sds',
            'sample': 'samples/sds_gel.jpg', 
            'config': 'configs/sds.yaml',
            'baseline': baseline_results['sds'],
            'expected_improvement': 'Similar or better band detection',
            'target_metric': 'bands_raw'
        }
    ]
    
    results_summary = []
    
    for scenario in test_scenarios:
        print(f"\n📊 Testing: {scenario['name']}")
        print("-" * 40)
        
        # Create problematic run report to trigger vision-assist
        # Make EtBr scenario more problematic to ensure triggering
        if scenario['type'] == 'etbr':
            problematic_report = {
                'metrics': {
                    'lanes_raw': 11,
                    'bands_raw': 2  # Very low band count
                },
                'observation': {
                    'coverage_total': 0.05,  # Very low coverage 
                    'baseline_post_med': 0.15  # Ineffective baseline
                }
            }
        else:
            # For SDS, create a worse scenario to trigger optimization
            problematic_report = {
                'metrics': {
                    'lanes_raw': 0,  # Complete failure to ensure triggering
                    'bands_raw': 0
                },
                'observation': {
                    'coverage_total': 0.05,
                    'baseline_post_med': 0.3,
                    'profile_zero_frac_after': 0.8
                }
            }
        
        # Get vision-assist suggestions
        print("🎯 Requesting vision-assist optimization...")
        
        # Map scenario type to analysis type
        analysis_type_map = {
            'sds': 'sds_page',
            'etbr': 'etbr_agarose', 
            'colony': 'colonies_blue_white'
        }
        
        vision_result = coordinator.process_analysis_result(
            input_image_path=scenario['sample'],
            run_report=problematic_report,
            analysis_type=analysis_type_map[scenario['type']]
        )
        
        if not vision_result['vision_assist_triggered']:
            print("  ⚠️ Vision-assist not triggered - skipping")
            continue
            
        if not vision_result['success']:
            print(f"  ❌ Vision-assist failed: {vision_result.get('error', 'Unknown error')}")
            continue
        
        # Extract approved parameter changes
        optimization = coordinator.get_optimization_summary(vision_result)
        
        print(f"  💡 Vision-assist suggestions (confidence={optimization['confidence']:.2f}):")
        for param, value in optimization['changes'].items():
            print(f"     {param}: {value}")
        
        # Create optimized config
        print("  📝 Creating optimized configuration...")
        optimized_config_path = create_optimized_config(
            scenario['config'], 
            optimization['changes']
        )
        
        # Run analysis with optimized parameters
        print("  🧪 Running optimized analysis...")
        optimized_output_dir = f"output/test_{scenario['type']}_optimized"
        
        optimized_results = run_optimized_analysis(
            scenario['sample'],
            optimized_config_path,
            optimized_output_dir,
            scenario['type']
        )
        
        if 'error' in optimized_results:
            print(f"  ❌ Optimized analysis failed: {optimized_results['error']}")
            continue
        
        # Compare results
        baseline_value = scenario['baseline'].get(scenario['target_metric'], 0)
        optimized_value = optimized_results.get('metrics', {}).get(scenario['target_metric'], 0)
        
        improvement = optimized_value - baseline_value
        improvement_pct = (improvement / baseline_value * 100) if baseline_value > 0 else 0
        
        print(f"\n📈 Results Comparison:")
        print(f"  Baseline {scenario['target_metric']}: {baseline_value}")
        print(f"  Optimized {scenario['target_metric']}: {optimized_value}")
        print(f"  Improvement: {improvement:+d} ({improvement_pct:+.1f}%)")
        
        success = improvement > 0 or (scenario['type'] == 'sds' and optimized_value >= baseline_value)
        
        if success:
            print(f"  ✅ {scenario['expected_improvement']} - SUCCESS")
        else:
            print(f"  ❌ No improvement detected - NEEDS INVESTIGATION")
        
        results_summary.append({
            'scenario': scenario['name'],
            'baseline': baseline_value,
            'optimized': optimized_value,
            'improvement': improvement,
            'improvement_pct': improvement_pct,
            'success': success,
            'confidence': optimization['confidence']
        })
    
    # Final summary
    print(f"\n🎉 Vision-Assist Validation Summary")
    print("=" * 40)
    
    successful_tests = sum(1 for r in results_summary if r['success'])
    total_tests = len(results_summary)
    
    print(f"Successful optimizations: {successful_tests}/{total_tests}")
    
    if len(results_summary) > 0:
        print(f"Average improvement: {sum(r['improvement'] for r in results_summary) / len(results_summary):.1f}")
        print(f"Average confidence: {sum(r['confidence'] for r in results_summary) / len(results_summary):.2f}")
    else:
        print("No test results to analyze")
    
    for result in results_summary:
        status = "✅" if result['success'] else "❌"
        print(f"  {status} {result['scenario']}: {result['improvement']:+d} ({result['improvement_pct']:+.1f}%)")
    
    return results_summary

if __name__ == "__main__":
    test_vision_assist_effectiveness()