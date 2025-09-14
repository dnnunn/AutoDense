#!/usr/bin/env python3
"""
Test script for the Custom Recipe Engine
"""
from autodense.recipes import RecipeRegistry, Recipe, RecipeStep, SafeOps
import numpy as np

def main():
    print("=== AutoDense Custom Recipe Engine Test ===\n")
    
    # Create registry
    registry = RecipeRegistry()
    
    # Test data (mock intensity values)
    test_data = [100, 120, 95, 110, 105, 200, 90, 115, 85, 125]
    print(f"Test data: {test_data}\n")
    
    # Test 1: Execute built-in preset
    print("1. Testing built-in preset 'normalize_to_control':")
    result = registry.execute_recipe('normalize_to_control', test_data, is_preset=True)
    if result.success:
        execution_result = result.data
        print(f"   ✓ Success! Final result: {execution_result.final_result}")
        print(f"   Steps executed: {len(execution_result.step_results)}")
    else:
        print(f"   ✗ Failed: {result.message}")
    print()
    
    # Test 2: Create and execute custom recipe
    print("2. Creating custom recipe:")
    custom_recipe = Recipe(
        name="Quality Analysis Pipeline",
        description="Custom pipeline for quality control and outlier detection",
        steps=[
            RecipeStep(
                operation="quality_filter",
                parameters={"max_cv": 30.0, "min_val": 50.0},
                step_id="filtered"
            ),
            RecipeStep(
                operation="normalize_to_control",
                parameters={"control_idx": 0},
                input_source="filtered",
                step_id="normalized"
            ),
            RecipeStep(
                operation="outlier_detection",
                parameters={"method": "iqr", "threshold": 1.5},
                input_source="normalized",
                step_id="outliers"
            ),
            RecipeStep(
                operation="statistical_summary",
                parameters={},
                input_source="normalized"
            )
        ]
    )
    
    # Save custom recipe
    save_result = registry.save_custom_recipe(custom_recipe)
    if save_result.success:
        print(f"   ✓ Custom recipe saved: {save_result.message}")
    else:
        print(f"   ✗ Save failed: {save_result.message}")
    
    # Execute custom recipe
    print("   Executing custom recipe:")
    exec_result = registry.execute_recipe('Quality Analysis Pipeline', test_data, is_preset=False)
    if exec_result.success:
        execution_result = exec_result.data
        print(f"   ✓ Custom recipe executed successfully!")
        print(f"   Final result: {execution_result.final_result}")
        for i, step_result in enumerate(execution_result.step_results):
            print(f"   Step {i+1}: {step_result.message}")
    else:
        print(f"   ✗ Execution failed: {exec_result.message}")
    print()
    
    # Test 3: List available recipes
    print("3. Available recipes:")
    print("   Built-in presets:")
    for name in registry.list_presets():
        preset = registry.get_preset(name)
        print(f"   - {preset.name}: {preset.description}")
    
    print("   Custom recipes:")
    custom_recipes = registry.list_custom_recipes()
    for recipe in custom_recipes:
        print(f"   - {recipe['name']}: {recipe['description']}")
    print()
    
    # Test 4: Available operations
    print("4. Available operations:")
    ops_info = registry.engine.get_operation_info()
    for name, info in ops_info.items():
        print(f"   - {name}: {info['description']}")
    
    print("\n=== Test completed successfully! ===")

if __name__ == "__main__":
    main()