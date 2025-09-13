#!/usr/bin/env python3
"""
Test script to validate OpenAI API fix for Error #3
Tests that the API calls are syntactically correct without needing actual API key.
"""

import sys
from pathlib import Path

def test_openai_import_and_syntax():
    """Test that OpenAI imports work and API calls are syntactically correct"""
    test_results = {}
    
    try:
        # Test OpenAI import
        import openai
        test_results['openai_import'] = {'success': True, 'version': openai.__version__}
        
        # Test client creation (doesn't require API key)
        client = openai.OpenAI(api_key="test-key")
        test_results['client_creation'] = {'success': True}
        
        # Test that the correct methods exist
        test_results['api_methods'] = {
            'has_chat': hasattr(client, 'chat'),
            'has_completions': hasattr(client.chat, 'completions') if hasattr(client, 'chat') else False,
            'has_create': hasattr(client.chat.completions, 'create') if hasattr(client, 'chat') and hasattr(client.chat, 'completions') else False,
            'no_responses': not hasattr(client, 'responses')  # Should be True (no responses method)
        }
        
        return test_results
        
    except Exception as e:
        test_results['error'] = str(e)
        return test_results

def test_syntax_validation():
    """Test that the fixed code can be parsed (syntax check)"""
    try:
        sys.path.insert(0, str(Path(__file__).parent))
        
        # Try to compile the openai_guided_preprocess function
        ui_file = Path(__file__).parent / "ui" / "streamlit_autodense_app.py"
        
        if not ui_file.exists():
            return {'error': f'UI file not found: {ui_file}'}
        
        # Read and try to compile the file
        with open(ui_file, 'r') as f:
            code = f.read()
        
        # This will raise SyntaxError if there are syntax issues
        compile(code, str(ui_file), 'exec')
        
        # Count the correct API calls
        correct_calls = code.count('client.chat.completions.create')
        invalid_calls = code.count('client.responses.create')
        
        return {
            'syntax_valid': True,
            'correct_api_calls': correct_calls,
            'invalid_api_calls': invalid_calls,
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

def main():
    print("🧪 Testing OpenAI API Fix (Error #3)")
    print("=" * 50)
    
    print("\n1. Testing OpenAI library and methods...")
    openai_test = test_openai_import_and_syntax()
    
    for key, value in openai_test.items():
        print(f"   {key}: {value}")
    
    print("\n2. Testing syntax validation...")
    syntax_test = test_syntax_validation()
    
    for key, value in syntax_test.items():
        print(f"   {key}: {value}")
    
    print("\n" + "=" * 50)
    
    # Determine overall result
    if (openai_test.get('openai_import', {}).get('success') and 
        openai_test.get('client_creation', {}).get('success') and
        openai_test.get('api_methods', {}).get('has_create') and
        syntax_test.get('syntax_valid') and
        syntax_test.get('correct_api_calls', 0) == 2 and
        syntax_test.get('invalid_api_calls', 0) == 0):
        
        print("✅ ERROR #3 FIX VALIDATED: OpenAI API calls are now correct!")
        return True
    else:
        print("❌ ERROR #3 FIX FAILED: Issues found with OpenAI API calls")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)