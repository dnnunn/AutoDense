#!/bin/bash
# AutoDense Unified Build Script - Eliminates directory confusion once and for all

set -e

# Get absolute project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_DIR="$PROJECT_ROOT/autodense/plugin"
VENV_DIR="$PROJECT_ROOT/.venv"

echo "🔨 AutoDense Build Script"
echo "Project Root: $PROJECT_ROOT"

# Function: Activate Python environment
activate_python() {
    if [ -f "$VENV_DIR/bin/activate" ]; then
        source "$VENV_DIR/bin/activate"
        echo "✅ Python venv activated"
    else
        echo "❌ Python venv not found at $VENV_DIR"
        exit 1
    fi
}

# Function: Build Java components
build_java() {
    echo "🔨 Building Java components..."
    cd "$PLUGIN_DIR"
    
    # Clean compile
    mvn clean compile -q 2>/dev/null || mvn clean compile
    
    # Create both classpath files to avoid confusion
    mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt -q 2>/dev/null || mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
    mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -q 2>/dev/null || mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
    
    # Create JAR if needed
    if [ ! -f "target/autodense-plugin-0.1.0-SNAPSHOT.jar" ]; then
        mvn package -DskipTests -q 2>/dev/null || mvn package -DskipTests
    fi
    
    echo "✅ Java build complete"
    cd "$PROJECT_ROOT"
}

# Function: Test SDS pipeline
test_sds() {
    local input="${1:-samples/sds_gel.jpg}"
    local output="${2:-output/test_sds}"
    
    echo "🧬 Testing SDS pipeline: $input -> $output"
    
    java -Djava.awt.headless=true \
         -cp "$PLUGIN_DIR/target/classes:$(cat $PLUGIN_DIR/target/runtime-classpath.txt)" \
         com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
         sds_page "$input" configs/sds.yaml "$output"
}

# Function: Test EtBr pipeline  
test_etbr() {
    local input="${1:-samples/etbr_gel.jpg}"
    local output="${2:-output/test_etbr}"
    
    echo "🧬 Testing EtBr pipeline: $input -> $output"
    
    java -Djava.awt.headless=true \
         -cp "$PLUGIN_DIR/target/classes:$(cat $PLUGIN_DIR/target/runtime-classpath.txt)" \
         com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
         etbr_agarose "$input" configs/etbr.yaml "$output"
}

# Function: Test Colony pipeline
test_colony() {
    local input="${1:-samples/colony_plate.jpg}"
    local output="${2:-output/test_colony}"
    
    echo "🧬 Testing Colony pipeline: $input -> $output"
    
    java -Djava.awt.headless=true \
         -cp "$PLUGIN_DIR/target/classes:$(cat $PLUGIN_DIR/target/runtime-classpath.txt)" \
         com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
         colony_count "$input" configs/colony.yaml "$output"
}

# Function: Test detect-only mode (known working)
test_detect_only() {
    local input="${1:-samples/sds_gel.jpg}"
    local output="${2:-output/test_detect_only}"
    
    echo "🔍 Testing detect-only mode: $input -> $output"
    
    java -Djava.awt.headless=true \
         -cp "$PLUGIN_DIR/target/classes:$(cat $PLUGIN_DIR/target/runtime-classpath.txt)" \
         com.betterdairy.autodense.cli.AutotuneAnalysisCLI \
         --detect-only --preprocessed "$input" \
         --roi 50,100,800,400 --config-yaml configs/sds.yaml \
         --outdir "$output"
}

# Main command handling
case "${1:-build}" in
    "build")
        activate_python
        build_java
        echo "🎉 Build complete. Use: ./build.sh test-sds|test-etbr|test-colony"
        ;;
    "test-sds")
        activate_python
        test_sds "$2" "$3"
        ;;
    "test-etbr") 
        activate_python
        test_etbr "$2" "$3"
        ;;
    "test-colony")
        activate_python
        test_colony "$2" "$3"
        ;;
    "test-detect")
        activate_python  
        test_detect_only "$2" "$3"
        ;;
    "test-all")
        activate_python
        echo "🧪 Running all pipeline tests..."
        test_detect_only
        test_sds
        test_etbr  
        test_colony
        ;;
    *)
        echo "Usage: ./build.sh [build|test-sds|test-etbr|test-colony|test-detect|test-all]"
        echo ""
        echo "Commands:"
        echo "  build        - Build Java components (default)"
        echo "  test-sds     - Test SDS-PAGE pipeline"
        echo "  test-etbr    - Test EtBr gel pipeline" 
        echo "  test-colony  - Test colony counting pipeline"
        echo "  test-detect  - Test detect-only mode (known working)"
        echo "  test-all     - Run all pipeline tests"
        ;;
esac