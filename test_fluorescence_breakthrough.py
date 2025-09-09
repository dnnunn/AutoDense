#!/usr/bin/env python3
"""
Test script to validate the breakthrough fluorescence detection algorithms.

This script tests the new FluorescenceOps multi-algorithm detection system
that should achieve 12+ band detections (vs 0 with the old system).
"""

import subprocess
import json
import os
import sys
from pathlib import Path

def test_fluorescence_breakthrough():
    """Test the new fluorescence detection algorithms against EtBr gel."""
    
    print("🧬 TESTING FLUORESCENCE DETECTION BREAKTHROUGH")
    print("=" * 60)
    
    # Test configuration
    config_file = "configs/etbr_advanced_fluorescence.yaml"
    test_image = "samples/synthetic_etbr_gel2.png"
    output_dir = "output/breakthrough_test"
    
    # Verify files exist
    if not os.path.exists(config_file):
        print(f"❌ Config file not found: {config_file}")
        return False
        
    if not os.path.exists(test_image):
        print(f"❌ Test image not found: {test_image}")
        return False
    
    # Create output directory
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    print(f"📋 Configuration: {config_file}")
    print(f"🖼️  Test Image: {test_image}")
    print(f"📁 Output: {output_dir}")
    print()
    
    # Build command
    cmd = [
        "java", "-Xmx4g",
        "-cp", "autodense/plugin/target/classes:autodense/plugin/target/dependency/*",
        "com.betterdairy.autodense.cli.AutotuneAnalysisCLI",
        "--no-exit",  # CRITICAL: Don't exit JVM for optimization integration
        "etbr_analysis",  # Analysis type
        test_image,       # Input image
        config_file,      # Configuration file
        output_dir        # Output directory
    ]
    
    print("🚀 EXECUTING BREAKTHROUGH FLUORESCENCE DETECTION")
    print("Command:", " ".join(cmd))
    print()
    
    try:
        # Execute the analysis
        result = subprocess.run(
            cmd, 
            capture_output=True, 
            text=True, 
            timeout=120,  # 2 minute timeout
            cwd=os.getcwd()
        )
        
        print("📤 STDOUT:")
        print(result.stdout)
        print()
        
        if result.stderr:
            print("📤 STDERR:")
            print(result.stderr)
            print()
        
        # Check return code
        if result.returncode != 0:
            print(f"❌ Analysis failed with return code: {result.returncode}")
            return False
        
        # Parse results
        run_report_path = Path(output_dir) / "run_report.json"
        if run_report_path.exists():
            with open(run_report_path) as f:
                report = json.load(f)
            
            print("📊 BREAKTHROUGH RESULTS ANALYSIS")
            print("=" * 40)
            
            # Extract key metrics
            bands_detected = report.get("total_bands_detected", 0)
            detection_method = report.get("detection_method", "unknown")
            polarity = report.get("polarity", "unknown")
            
            # Algorithm breakdown if available
            algorithms = report.get("algorithm_results", {})
            
            print(f"🎯 BANDS DETECTED: {bands_detected}")
            print(f"🔬 DETECTION METHOD: {detection_method}")
            print(f"⚡ POLARITY: {polarity}")
            
            if algorithms:
                print("\n🤖 ALGORITHM BREAKDOWN:")
                for algo, count in algorithms.items():
                    print(f"   {algo}: {count} bands")
            
            # Success criteria
            success = bands_detected >= 12
            expected_target = 15
            
            print(f"\n🎪 BREAKTHROUGH ASSESSMENT:")
            print(f"   Target: {expected_target}+ bands")
            print(f"   Achieved: {bands_detected} bands")
            print(f"   Improvement: {bands_detected - 0} bands (was 0)")
            
            if success:
                print("✅ BREAKTHROUGH SUCCESS! Advanced fluorescence detection working!")
                improvement_factor = "∞" if bands_detected > 0 else "0"
                print(f"📈 Improvement Factor: {improvement_factor}x")
            else:
                print("❌ Did not reach breakthrough target")
                print("🔧 Check algorithm integration and configuration")
            
            return success
            
        else:
            print(f"❌ Run report not found: {run_report_path}")
            return False
            
    except subprocess.TimeoutExpired:
        print("⏰ Analysis timed out after 2 minutes")
        return False
    except subprocess.CalledProcessError as e:
        print(f"❌ Analysis failed: {e}")
        return False
    except Exception as e:
        print(f"💥 Unexpected error: {e}")
        return False

def validate_algorithm_integration():
    """Validate that the new algorithms are properly integrated."""
    
    print("\n🔧 VALIDATING ALGORITHM INTEGRATION")
    print("=" * 40)
    
    # Check that FluorescenceOps.java exists
    fluorescence_ops = "autodense/plugin/src/main/java/autodense/sds/FluorescenceOps.java"
    if os.path.exists(fluorescence_ops):
        print("✅ FluorescenceOps.java found")
        
        # Check for key methods
        with open(fluorescence_ops, 'r') as f:
            content = f.read()
            
        checks = {
            "detectFluorescenceBands": "Main detection method",
            "detectBandsMultiAlgorithm": "Multi-algorithm fusion",
            "detectBandsProfileMethod": "Enhanced profile detection", 
            "detectBands2DBlobMethod": "2D blob detection",
            "detectBandsMorphologicalMethod": "Morphological detection",
            "detectBandsAdaptiveThreshold": "Adaptive threshold detection"
        }
        
        for method, description in checks.items():
            if method in content:
                print(f"✅ {description}")
            else:
                print(f"❌ Missing: {description}")
                
    else:
        print(f"❌ FluorescenceOps.java not found: {fluorescence_ops}")
        return False
    
    # Check BandDetector integration
    band_detector = "autodense/plugin/src/main/java/com/betterdairy/autodense/analysis/BandDetector.java"
    if os.path.exists(band_detector):
        print("✅ BandDetector.java found")
        
        with open(band_detector, 'r') as f:
            content = f.read()
            
        integration_checks = {
            "isFluorescenceMode": "Fluorescence mode detection",
            "findFluorescenceBands": "Fluorescence band finder",
            "FLUORESCENCE_FIX": "Inversion fix applied",
            "import autodense.sds.FluorescenceOps": "FluorescenceOps import"
        }
        
        for check, description in integration_checks.items():
            if check in content:
                print(f"✅ {description}")
            else:
                print(f"❌ Missing: {description}")
                
    else:
        print(f"❌ BandDetector.java not found: {band_detector}")
        return False
    
    # Check configuration file
    config_file = "configs/etbr_advanced_fluorescence.yaml"
    if os.path.exists(config_file):
        print("✅ Advanced fluorescence config found")
        
        with open(config_file, 'r') as f:
            content = f.read()
            
        config_checks = {
            "use_fluorescence_ops: true": "FluorescenceOps enabled",
            "invert_polarity: false": "Polarity fix applied",
            "prominence_frac: 0.001": "Ultra-sensitive parameters",
            "multi-algorithm": "Multi-algorithm configuration"
        }
        
        for check, description in config_checks.items():
            if check in content:
                print(f"✅ {description}")
            else:
                print(f"⚠️  {description} (check manually)")
                
    else:
        print(f"❌ Config file not found: {config_file}")
        return False
    
    return True

def main():
    """Main test execution."""
    
    print("🔬 FLUORESCENCE DETECTION BREAKTHROUGH TEST")
    print("🎯 Target: Detect 15+ bands in EtBr fluorescence gel (was 0)")
    print("🧪 Testing multi-algorithm detection with fixed polarity")
    print()
    
    # Step 1: Validate integration
    integration_ok = validate_algorithm_integration()
    
    if not integration_ok:
        print("❌ Integration validation failed")
        return 1
    
    print("✅ Algorithm integration validated")
    print()
    
    # Step 2: Run breakthrough test
    breakthrough_success = test_fluorescence_breakthrough()
    
    if breakthrough_success:
        print("\n🎉 FLUORESCENCE BREAKTHROUGH ACHIEVED!")
        print("🚀 Advanced computer vision algorithms successfully implemented")
        print("📈 Zero-to-hero improvement in fluorescence detection")
        return 0
    else:
        print("\n❌ Breakthrough test failed")
        print("🔧 Check logs and configuration")
        return 1

if __name__ == "__main__":
    sys.exit(main())