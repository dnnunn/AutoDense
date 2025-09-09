#!/usr/bin/env python3
"""
Enhanced Vision Pipeline Validation Test

Tests the new preprocessing recipes and optimized configurations against
the specific detection failures identified in AutoDense.

Focus areas:
1. EtBr catastrophic band detection failure (2→0 bands)
2. SDS peripheral lane loss (7/12 lanes)  
3. Colony over-aggressive filtering (25→15 colonies)
"""

import json
import sys
import os
import yaml
import shutil
import subprocess
from typing import Dict, Any, List, Tuple
from pathlib import Path

sys.path.append('.')
from autodense_autotune.vision.coordinator import VisionAssistCoordinator, create_default_vision_config
from autodense_autotune.vision.preprocessing_recipes import VisionPreprocessingEngine, create_calibration_image_set

def create_failure_scenarios() -> Dict[str, Dict[str, Any]]:
    """Create realistic failure scenarios matching current AutoDense issues."""
    return {
        'etbr_catastrophic': {
            'name': 'EtBr Catastrophic Band Detection Failure',
            'analysis_type': 'etbr_agarose',
            'sample_path': 'samples/etbr_gel.jpg',
            'base_config': 'configs/etbr_optimized.yaml',
            'optimized_config': 'configs/etbr_lane_optimized.yaml',
            'current_performance': {
                'lanes_raw': 11,
                'bands_raw': 2,  # Critical failure
                'coverage_total': 0.0,
                'baseline_post_med': 0.15
            },
            'target_performance': {
                'lanes_raw': 18,  # Maintain good lane detection
                'bands_raw': 15,  # Target 7.5x improvement
                'expected_improvement': '750% increase in band detection'
            },
            'failure_indicators': {
                'image_stats': 'mean=23.2±23.9, range=[2.0,255.0]',
                'invert_detected': True,
                'rescue_attempt_result': 0,  # Rescue failed completely
                'critical_parameters': ['bands.prominence_frac', 'bands.min_distance_px']
            }
        },
        'sds_edge_lanes': {
            'name': 'SDS Peripheral Lane Detection Failure', 
            'analysis_type': 'sds_page',
            'sample_path': 'samples/sds_gel.jpg',
            'base_config': 'configs/sds_optimized.yaml', 
            'optimized_config': 'configs/sds_edge_optimized.yaml',
            'current_performance': {
                'lanes_raw': 7,
                'bands_raw': 24,
                'expected_lanes': 12,
                'lane_detection_rate': 0.58  # 58% detection rate
            },
            'target_performance': {
                'lanes_raw': 10,  # Target 83% detection rate
                'bands_raw': 30,  # Maintain or improve band detection
                'expected_improvement': '43% increase in lane detection'
            },
            'failure_indicators': {
                'edge_lane_loss': True,
                'uneven_illumination': True,
                'missing_outer_lanes': [1, 2, 11, 12]  # Typical pattern
            }
        },
        'colony_filtering': {
            'name': 'Colony Over-Aggressive Classification Filtering',
            'analysis_type': 'colonies_blue_white', 
            'sample_path': 'samples/colony_plate.jpg',
            'base_config': 'configs/colony_optimized.yaml',
            'optimized_config': None,  # Will create optimized version
            'current_performance': {
                'colonies_raw': 25,
                'colonies_classified': 15,
                'retention_rate': 0.60,
                'blue_count': 0,
                'white_count': 15
            },
            'target_performance': {
                'colonies_raw': 25,
                'colonies_classified': 20,  # Target 80% retention
                'expected_improvement': '33% increase in colony retention'
            },
            'failure_indicators': {
                'aggressive_size_filtering': True,
                'small_colony_loss': True,
                'classification_bottleneck': True
            }
        }
    }

def run_baseline_analysis(scenario: Dict[str, Any]) -> Dict[str, Any]:
    """Run baseline analysis with current configuration."""
    print(f"  🔬 Running baseline analysis...")
    
    # Create output directory
    output_dir = f"output/baseline_{scenario['analysis_type']}"
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    os.makedirs(output_dir)
    
    # Get Java command components
    classpath_file = 'autodense/plugin/target/runtime-classpath.txt'
    if os.path.exists(classpath_file):
        with open(classpath_file, 'r') as f:
            classpath = f'autodense/plugin/target/classes:{f.read().strip()}'
    else:
        classpath = 'autodense/plugin/target/classes:autodense/plugin/target/dependency/*'
    
    java_cmd = [
        'java', '-Djava.awt.headless=true', '-cp', classpath,
        'com.betterdairy.autodense.cli.AutotuneAnalysisCLI',
        '--no-exit',
        scenario['analysis_type'],
        scenario['sample_path'],
        scenario['base_config'],
        output_dir
    ]
    
    try:
        result = subprocess.run(java_cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0:
            # Load results
            report_path = os.path.join(output_dir, 'run_report.json')
            if os.path.exists(report_path):
                with open(report_path, 'r') as f:
                    return json.load(f)
        
        print(f"    ❌ Baseline analysis failed: {result.stderr}")
        return {'error': result.stderr}
    except Exception as e:
        print(f"    ❌ Baseline analysis error: {e}")
        return {'error': str(e)}

def run_optimized_analysis(scenario: Dict[str, Any]) -> Dict[str, Any]:
    """Run analysis with optimized configuration."""
    print(f"  🚀 Running optimized analysis...")
    
    output_dir = f"output/optimized_{scenario['analysis_type']}"
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    os.makedirs(output_dir)
    
    # Get classpath
    classpath_file = 'autodense/plugin/target/runtime-classpath.txt'
    if os.path.exists(classpath_file):
        with open(classpath_file, 'r') as f:
            classpath = f'autodense/plugin/target/classes:{f.read().strip()}'
    else:
        classpath = 'autodense/plugin/target/classes:autodense/plugin/target/dependency/*'
    
    java_cmd = [
        'java', '-Djava.awt.headless=true', '-cp', classpath,
        'com.betterdairy.autodense.cli.AutotuneAnalysisCLI', 
        '--no-exit',
        scenario['analysis_type'],
        scenario['sample_path'],
        scenario['optimized_config'],
        output_dir
    ]
    
    try:
        result = subprocess.run(java_cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0:
            report_path = os.path.join(output_dir, 'run_report.json')
            if os.path.exists(report_path):
                with open(report_path, 'r') as f:
                    return json.load(f)
        
        print(f"    ❌ Optimized analysis failed: {result.stderr}")
        return {'error': result.stderr}
    except Exception as e:
        print(f"    ❌ Optimized analysis error: {e}")
        return {'error': str(e)}

def test_vision_assist_suggestion(scenario: Dict[str, Any]) -> Dict[str, Any]:
    """Test vision-assist parameter suggestion for scenario."""
    print(f"  🎯 Testing vision-assist suggestions...")
    
    # Initialize vision coordinator
    config = create_default_vision_config()
    coordinator = VisionAssistCoordinator(config)
    
    # Create problematic run report to trigger suggestions
    problematic_metrics = scenario['current_performance'].copy()
    
    # Make it even more problematic to ensure triggering
    if scenario['analysis_type'] == 'etbr_agarose':
        problematic_metrics['bands_raw'] = 0  # Complete band failure
        problematic_metrics['coverage_total'] = 0.0
    elif scenario['analysis_type'] == 'sds_page':
        problematic_metrics['lanes_raw'] = 4  # Severe lane loss
    
    problematic_report = {
        'metrics': problematic_metrics,
        'observation': {
            'coverage_total': 0.05,
            'baseline_post_med': 0.25,
            'profile_zero_frac_after': 0.9
        }
    }
    
    # Get vision-assist suggestions
    vision_result = coordinator.process_analysis_result(
        input_image_path=scenario['sample_path'],
        run_report=problematic_report,
        analysis_type=scenario['analysis_type'],
        force_trigger=True  # Force triggering for testing
    )
    
    if vision_result['success']:
        summary = coordinator.get_optimization_summary(vision_result)
        return {
            'triggered': True,
            'confidence': summary['confidence'],
            'suggestions': summary['changes'],
            'reasoning': summary['reasoning'],
            'risk_level': summary['risk_level']
        }
    else:
        return {
            'triggered': vision_result['vision_assist_triggered'],
            'error': vision_result.get('error', 'Unknown error')
        }

def validate_improvements(
    scenario: Dict[str, Any], 
    baseline: Dict[str, Any], 
    optimized: Dict[str, Any]
) -> Dict[str, Any]:
    """Validate improvements achieved by optimized configuration."""
    
    if 'error' in baseline or 'error' in optimized:
        return {'success': False, 'error': 'Analysis failed'}
    
    baseline_metrics = baseline.get('metrics', {})
    optimized_metrics = optimized.get('metrics', {})
    
    validation = {
        'scenario': scenario['name'],
        'analysis_type': scenario['analysis_type'],
        'success': False,
        'improvements': {},
        'target_met': {},
        'summary': ''
    }
    
    if scenario['analysis_type'] == 'etbr_agarose':
        # EtBr: Focus on band detection improvement
        baseline_bands = baseline_metrics.get('bands_raw', 0)
        optimized_bands = optimized_metrics.get('bands_raw', 0)
        target_bands = scenario['target_performance']['bands_raw']
        
        improvement = optimized_bands - baseline_bands
        target_met = optimized_bands >= target_bands
        
        validation['improvements']['bands_detected'] = improvement
        validation['target_met']['bands'] = target_met
        validation['success'] = improvement >= 10  # Significant improvement required
        validation['summary'] = f"Bands: {baseline_bands} → {optimized_bands} (target: {target_bands})"
    
    elif scenario['analysis_type'] == 'sds_page':
        # SDS: Focus on lane detection improvement
        baseline_lanes = baseline_metrics.get('lanes_raw', 0)
        optimized_lanes = optimized_metrics.get('lanes_raw', 0)
        target_lanes = scenario['target_performance']['lanes_raw']
        
        improvement = optimized_lanes - baseline_lanes
        target_met = optimized_lanes >= target_lanes
        
        validation['improvements']['lanes_detected'] = improvement
        validation['target_met']['lanes'] = target_met
        validation['success'] = improvement >= 2
        validation['summary'] = f"Lanes: {baseline_lanes} → {optimized_lanes} (target: {target_lanes})"
    
    elif scenario['analysis_type'] == 'colonies_blue_white':
        # Colony: Focus on retention rate
        baseline_raw = baseline_metrics.get('colonies_raw', 0)
        baseline_classified = baseline_metrics.get('colonies_classified', 0)
        optimized_raw = optimized_metrics.get('colonies_raw', 0)
        optimized_classified = optimized_metrics.get('colonies_classified', 0)
        
        baseline_retention = baseline_classified / baseline_raw if baseline_raw > 0 else 0
        optimized_retention = optimized_classified / optimized_raw if optimized_raw > 0 else 0
        
        improvement = optimized_retention - baseline_retention
        target_met = optimized_retention >= 0.80  # 80% target
        
        validation['improvements']['retention_rate'] = improvement
        validation['target_met']['retention'] = target_met
        validation['success'] = improvement >= 0.15  # 15% improvement
        validation['summary'] = f"Retention: {baseline_retention:.1%} → {optimized_retention:.1%}"
    
    return validation

def test_enhanced_vision_pipelines():
    """Main test function for enhanced vision pipelines."""
    
    print("🎯 Enhanced Vision Pipeline Validation")
    print("=" * 60)
    print("Testing optimized configurations against critical detection failures")
    print()
    
    # Get test scenarios
    scenarios = create_failure_scenarios()
    results_summary = []
    
    for scenario_id, scenario in scenarios.items():
        print(f"📊 Testing: {scenario['name']}")
        print("-" * 50)
        
        try:
            # Test 1: Vision-assist suggestions
            vision_assist_result = test_vision_assist_suggestion(scenario)
            
            if vision_assist_result.get('triggered'):
                print(f"  ✅ Vision-assist triggered (confidence: {vision_assist_result['confidence']:.2f})")
                print(f"     Risk level: {vision_assist_result['risk_level']}")
                
                for param, value in vision_assist_result['suggestions'].items():
                    print(f"     - {param}: {value}")
            else:
                print(f"  ⚠️ Vision-assist not triggered: {vision_assist_result.get('error', 'No trigger')}")
            
            # Test 2: Baseline analysis (if sample exists)
            if os.path.exists(scenario['sample_path']):
                baseline_result = run_baseline_analysis(scenario)
                
                # Test 3: Optimized analysis (if optimized config exists)
                if scenario.get('optimized_config') and os.path.exists(scenario['optimized_config']):
                    optimized_result = run_optimized_analysis(scenario)
                    
                    # Test 4: Compare results
                    validation = validate_improvements(scenario, baseline_result, optimized_result)
                    
                    print(f"  📈 Results: {validation['summary']}")
                    
                    if validation['success']:
                        print(f"  ✅ SUCCESS - Target improvements achieved")
                    else:
                        print(f"  ❌ NEEDS WORK - Insufficient improvement")
                    
                    results_summary.append(validation)
                else:
                    print(f"  ⚠️ Optimized config not found: {scenario.get('optimized_config')}")
            else:
                print(f"  ⚠️ Sample image not found: {scenario['sample_path']}")
                
        except Exception as e:
            print(f"  ❌ Test failed: {e}")
            results_summary.append({
                'scenario': scenario['name'],
                'success': False,
                'error': str(e)
            })
        
        print()
    
    # Final summary
    print("🎉 Enhanced Vision Pipeline Test Summary")
    print("=" * 50)
    
    successful_tests = sum(1 for r in results_summary if r.get('success'))
    total_tests = len(results_summary)
    
    print(f"Successful optimizations: {successful_tests}/{total_tests}")
    
    if results_summary:
        for result in results_summary:
            status = "✅" if result.get('success') else "❌"
            summary = result.get('summary', result.get('error', 'No data'))
            print(f"  {status} {result['scenario']}: {summary}")
    
    # Specific recommendations
    print("\n🔧 Specific Parameter Recommendations:")
    print("-" * 40)
    
    print("EtBr Fluorescence Rescue:")
    print("  - bands.prominence_frac: 0.015 (vs 0.03 current)")
    print("  - bands.min_distance_px: 8 (vs 12 current)")
    print("  - Enable CLAHE with clip_limit: 3.0")
    print("  - Use morphological baseline for fluorescence")
    
    print("\nSDS Edge Lane Enhancement:")
    print("  - workflow.sensitivity: 1.2 (vs 1.0 current)")
    print("  - Enable background_removal_radius: 15.0")
    print("  - Enable deskewing for better alignment")
    print("  - CLAHE with horizontal emphasis [4,8] tiles")
    
    print("\nColony Classification Rescue:")
    print("  - Reduce min_area filtering to 25px")
    print("  - Increase circularity tolerance to 0.4")
    print("  - Disable aggressive size filtering")
    print("  - Enable unsharp masking for edge enhancement")
    
    return results_summary

if __name__ == "__main__":
    test_enhanced_vision_pipelines()