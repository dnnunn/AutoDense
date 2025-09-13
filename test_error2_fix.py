#!/usr/bin/env python3
"""
Test script to validate Error #2 fix: Function signature for ad_pipeline_run
Tests that the function signature issues are resolved without needing actual backend.
"""

import sys
from pathlib import Path
import re

def test_function_signature_fix():
    """Test that the ad_pipeline_run call uses correct signature pattern"""
    test_results = {}
    
    try:
        ui_file = Path(__file__).parent / "ui" / "streamlit_autodense_app.py"
        
        if not ui_file.exists():
            return {'error': f'UI file not found: {ui_file}'}
        
        with open(ui_file, 'r') as f:
            content = f.read()
        
        # Look for the _try_run_ad_band_assist function
        func_pattern = r'def _try_run_ad_band_assist\([^)]*\):(.*?)(?=^def |\Z)'
        match = re.search(func_pattern, content, re.DOTALL | re.MULTILINE)
        
        if not match:
            return {'error': 'Could not find _try_run_ad_band_assist function'}
        
        func_body = match.group(1)
        
        # Check for the correct call pattern (named parameters)
        correct_patterns = [
            r'ad_pipeline_run\(p=ad_params,\s*image=base\)',
            r'ad_pipeline_run\(image=base,\s*p=ad_params\)'
        ]
        
        correct_calls = 0
        for pattern in correct_patterns:
            matches = re.findall(pattern, func_body)
            correct_calls += len(matches)
        
        # Check for problematic positional patterns
        problematic_patterns = [
            r'ad_pipeline_run\(ad_params,\s*base\)',
            r'ad_pipeline_run\(base,\s*ad_params\)'
        ]
        
        problematic_calls = 0
        for pattern in problematic_patterns:
            matches = re.findall(pattern, func_body)
            problematic_calls += len(matches)
        
        test_results['function_analysis'] = {
            'correct_named_calls': correct_calls,
            'problematic_positional_calls': problematic_calls,
            'function_found': True
        }
        
        return test_results
        
    except Exception as e:
        return {'error': str(e)}

def test_syntax_validation():
    """Test that the fixed code can be parsed (syntax check)"""
    try:
        ui_file = Path(__file__).parent / "ui" / "streamlit_autodense_app.py"
        
        if not ui_file.exists():
            return {'error': f'UI file not found: {ui_file}'}
        
        # Read and try to compile the file
        with open(ui_file, 'r') as f:
            code = f.read()
        
        # This will raise SyntaxError if there are syntax issues
        compile(code, str(ui_file), 'exec')
        
        return {
            'syntax_valid': True,
            'file_size': len(code)
        }
        
    except SyntaxError as e:
        return {
            'syntax_valid': False,
            'syntax_error': str(e),
            'line': e.lineno
        }
    except Exception as e:
        return {
            'syntax_valid': False,
            'error': str(e)
        }

def test_import_availability():
    """Test that required imports are available"""
    import_results = {}
    
    try:
        sys.path.insert(0, str(Path(__file__).parent))
        
        # Test ADParams import
        try:
            from scripts.autodense.orchestrator.pipeline import Params as ADParams
            import_results['ADParams'] = {'available': True}
        except ImportError as e:
            import_results['ADParams'] = {'available': False, 'error': str(e)}
        
        # Test ad_pipeline_run import 
        try:
            from scripts.autodense.orchestrator.pipeline import run as ad_pipeline_run
            import inspect
            sig = inspect.signature(ad_pipeline_run)
            import_results['ad_pipeline_run'] = {
                'available': True,
                'signature': str(sig),
                'parameters': list(sig.parameters.keys())
            }
        except ImportError as e:
            import_results['ad_pipeline_run'] = {'available': False, 'error': str(e)}
    
    except Exception as e:
        import_results['general_error'] = str(e)
    
    return import_results

def main():
    print("🧪 Testing Error #2 Fix: Function Signature for ad_pipeline_run")
    print("=" * 60)
    
    print("\n1. Testing function signature fix...")
    signature_test = test_function_signature_fix()
    
    for key, value in signature_test.items():
        print(f"   {key}: {value}")
    
    print("\n2. Testing syntax validation...")
    syntax_test = test_syntax_validation()
    
    for key, value in syntax_test.items():
        print(f"   {key}: {value}")
    
    print("\n3. Testing import availability...")
    import_test = test_import_availability()
    
    for key, value in import_test.items():
        print(f"   {key}: {value}")
    
    print("\n" + "=" * 60)
    
    # Determine overall result
    if (signature_test.get('function_analysis', {}).get('correct_named_calls', 0) > 0 and
        signature_test.get('function_analysis', {}).get('problematic_positional_calls', 0) == 0 and
        syntax_test.get('syntax_valid') and
        import_test.get('ad_pipeline_run', {}).get('available')):
        
        print("✅ ERROR #2 FIX VALIDATED: Function signature issues resolved!")
        return True
    else:
        print("❌ ERROR #2 FIX FAILED: Function signature issues still present")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)