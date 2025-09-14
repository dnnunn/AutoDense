#!/usr/bin/env python3
"""
Test script for the Assist functionality
"""
from autodense.assist.nlp_intent import parse_prompt_to_recipe, validate_recipe, execute
import pandas as pd
import numpy as np

def main():
    print("=== AutoDense Assist Module Test ===\n")
    
    # Test samples
    samples = ["WT", "MutA", "MutB", "Control", "Treatment"]
    
    # Test prompts
    test_prompts = [
        "compare WT vs MutA in 45–60 kDa",
        "fold change MutB over WT between 800 and 1500 bp", 
        "show percent of sample total",
        "analysis of bands in 20-40 kDa range"
    ]
    
    print("1. Testing NLP prompt parsing:")
    for i, prompt in enumerate(test_prompts, 1):
        print(f"   Prompt {i}: \"{prompt}\"")
        recipe = parse_prompt_to_recipe(prompt, samples)
        if recipe:
            print(f"   ✓ Parsed successfully: {len(recipe['pipeline'])} steps")
            for j, step in enumerate(recipe['pipeline']):
                print(f"     Step {j+1}: {step['op']} - {step}")
        else:
            print(f"   ✗ Could not parse")
        print()
    
    # Test recipe validation
    print("2. Testing recipe validation:")
    valid_recipe = {
        "name": "test_recipe",
        "pipeline": [
            {"op": "filter_confidence", "min": 0.3},
            {"op": "filter_range", "col": "kDa", "min": 45, "max": 60},
            {"op": "group_sum", "by": "sample", "col": "intensity"},
            {"op": "fold_change", "numerator": "MutA", "denominator": "WT", "log2": True}
        ]
    }
    
    errors = validate_recipe(valid_recipe)
    if errors:
        print(f"   ✗ Validation failed: {'; '.join(errors)}")
    else:
        print(f"   ✓ Recipe validation passed")
    
    # Test invalid recipe
    invalid_recipe = {
        "name": "bad_recipe",
        "pipeline": [
            {"op": "unknown_operation", "param": "value"},
            {"missing_op": "value"}
        ]
    }
    
    errors = validate_recipe(invalid_recipe)
    print(f"   Invalid recipe errors (expected): {len(errors)} errors")
    for error in errors:
        print(f"     - {error}")
    print()
    
    # Test recipe execution with mock data
    print("3. Testing recipe execution:")
    mock_data = pd.DataFrame({
        'sample': ['WT', 'WT', 'MutA', 'MutA', 'Control'],
        'lane': [1, 1, 2, 2, 3], 
        'band': [1, 2, 1, 2, 1],
        'intensity': [100, 80, 150, 120, 90],
        'kDa': [50, 55, 48, 52, 49],
        'confidence': [0.9, 0.8, 0.85, 0.9, 0.7]
    })
    
    print("   Mock data:")
    print(mock_data.to_string(index=False))
    print()
    
    # Execute the first successful recipe
    first_recipe = parse_prompt_to_recipe("compare WT vs MutA in 45–60 kDa", samples)
    if first_recipe:
        print(f"   Executing recipe: {first_recipe['name']}")
        result = execute(first_recipe, mock_data)
        
        print("   Execution results:")
        print(f"     Final data shape: {result['data'].shape}")
        print(f"     Aggregates: {result['ctx']['aggregates']}")
        print(f"     Metrics: {result['ctx']['metrics']}")
        
        # Show fold change if calculated
        fc = result['ctx']['metrics'].get('fold_change')
        log2_fc = result['ctx']['metrics'].get('log2_fc')
        if fc is not None:
            print(f"     Fold change (MutA/WT): {fc:.3f}")
            if log2_fc is not None:
                print(f"     Log2 fold change: {log2_fc:+.3f}")
    
    print("\n=== Assist test completed! ===")

if __name__ == "__main__":
    main()