#!/bin/bash

set -e

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="audit_final_${TIMESTAMP}"
RUNTIME=30

echo "=== FINAL AUDIT SCRIPT ==="
echo "Processing 9 images × 4 parameters = 36 tests"
echo "Results: $RESULTS_DIR"

mkdir -p "$RESULTS_DIR"

# Mapping functions
get_make_target() {
    case "$1" in
        *colony*) echo "tune-colony-ij" ;;
        *etbr*) echo "tune-etbr-ij" ;;
        *sds*) echo "tune-sds-ij" ;;
        *) echo "" ;;
    esac
}

get_config_file() {
    case "$1" in
        *colony*) echo "configs/colony.yaml" ;;
        *etbr*) echo "configs/etbr.yaml" ;;
        *sds*) echo "configs/sds.yaml" ;;
        *) echo "" ;;
    esac
}

# Parameter modifications
apply_param() {
    local variant="$1"
    local config="$2"
    
    case "$variant" in
        "full") ;;  # no changes
        "no_deskew") 
            sed -i.bak 's/enable_deskew: true/enable_deskew: false/g' "$config" ;;
        "no_background") 
            sed -i.bak 's/background_removal_radius: [0-9.]\+/background_removal_radius: 0.0/g' "$config" ;;
        "no_normalization") 
            sed -i.bak 's/normalize_intensity: true/normalize_intensity: false/g' "$config" ;;
    esac
}

restore_config() {
    local config="$1"
    if [ -f "${config}.bak" ]; then
        mv "${config}.bak" "$config"
    fi
}

# Process each image with each parameter
test_count=0
for image in tmp/*.{jpg,png}; do
    [ -f "$image" ] || continue
    
    image_name=$(basename "$image")
    make_target=$(get_make_target "$image_name")
    config_file=$(get_config_file "$image_name")
    
    if [ -z "$make_target" ] || [ -z "$config_file" ]; then
        echo "⚠️  Skipping $image_name - no mapping found"
        continue
    fi
    
    echo "📷 Processing $image_name with $make_target using $config_file"
    
    for variant in full no_deskew no_background no_normalization; do
        test_count=$((test_count + 1))
        test_name="${image_name%.*}_${variant}"
        test_dir="${RESULTS_DIR}/${test_name}"
        
        echo "  [$test_count/36] $test_name"
        
        mkdir -p "$test_dir"
        
        # Backup original config
        cp "$config_file" "${test_dir}/config_original.yaml"
        
        # Apply parameter modification
        apply_param "$variant" "$config_file"
        
        # Save modified config
        cp "$config_file" "${test_dir}/config_used.yaml"
        
        # Clear previous outputs
        rm -rf output/*_test/
        
        # Run analysis with INPUT parameter
        echo "    Running: make $make_target INPUT=\"$image\""
        make "$make_target" INPUT="$image" > "${test_dir}/run_log.txt" 2>&1 &
        pid=$!
        sleep $RUNTIME
        kill $pid 2>/dev/null || true
        wait $pid 2>/dev/null || true
        
        # Collect all outputs
        for output_dir in output/*_test/; do
            if [ -d "$output_dir" ]; then
                echo "    Copying from $output_dir"
                cp -r "$output_dir"* "$test_dir/" 2>/dev/null || true
            fi
        done
        
        # Count what we got
        files_found=$(find "$test_dir" -name "*.png" -o -name "*.json" | wc -l)
        echo "    Collected $files_found image/json files"
        
        # Restore original config
        restore_config "$config_file"
    done
done

echo ""
echo "=== AUDIT COMPLETE ==="
echo "Total tests run: $test_count"
echo "Results directory: $RESULTS_DIR"

# Create tarball
TARBALL="audit_final_${TIMESTAMP}.tar.gz"
tar -czf "$TARBALL" "$RESULTS_DIR"
echo "Tarball created: $TARBALL"

# Summary
total_files=$(find "$RESULTS_DIR" -type f | wc -l)
echo "Total files collected: $total_files"
echo "Ready for external audit"