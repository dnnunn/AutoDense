#!/usr/bin/env python3
import yaml
import copy

# Load the config to debug
with open('configs/etbr_advanced_fluorescence.yaml', 'r') as f:
    base_config = yaml.safe_load(f)

print("Base config structure:")
print("pre.clip_percentiles:", base_config.get('pre', {}).get('clip_percentiles', 'NOT FOUND'))
print("Type:", type(base_config.get('pre', {}).get('clip_percentiles', None)))

# Test the navigation
test_params = {
    'pre.clip_percentiles.0': 0.5,
    'pre.clip_percentiles.1': 99.5
}

config = copy.deepcopy(base_config)

for param_path, value in test_params.items():
    print(f"\nProcessing: {param_path} = {value}")
    keys = param_path.split('.')
    print(f"Keys: {keys}")
    
    final_key = keys[-1]
    print(f"Final key: '{final_key}', is digit: {final_key.isdigit()}")
    
    if final_key.isdigit() and len(keys) >= 2:
        # Array index (e.g., clip_percentiles.0)
        array_key = keys[-2]
        index = int(final_key)
        print(f"Array handling: array_key='{array_key}', index={index}")
        
        # Navigate to the parent of the array (not including the array key)
        current = config
        print(f"Starting navigation, keys to traverse: {keys[:-2]}")
        for key in keys[:-2]:  # Stop before the array key
            print(f"  Navigating to: {key}")
            if key not in current:
                current[key] = {}
            current = current[key]
            print(f"    Now at: {type(current)}")
        
        print(f"Final current type: {type(current)}, looking for array_key: '{array_key}'")
        print(f"Current has array_key: {array_key in current}")
        if array_key in current:
            print(f"Array type: {type(current[array_key])}, value: {current[array_key]}")
        
        # Ensure array exists and is the right type
        if array_key not in current or not isinstance(current[array_key], list):
            print(f"Creating/replacing array at current['{array_key}']")
            try:
                current[array_key] = [0, 100]
                print("Success!")
            except Exception as e:
                print(f"ERROR: {e}")
                continue
        
        # Extend array if needed
        while len(current[array_key]) <= index:
            current[array_key].append(0)
        
        current[array_key][index] = value
        print(f"Set {array_key}[{index}] = {value}")
    else:
        # Regular parameter navigation
        current = config
        for key in keys[:-1]:
            if key not in current:
                current[key] = {}
            current = current[key]
        current[final_key] = value

print(f"\nFinal config pre.clip_percentiles: {config.get('pre', {}).get('clip_percentiles')}")