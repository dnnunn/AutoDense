#!/bin/bash
# build_classpath.sh - Robust classpath construction for AutoDense Java CLI
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_DIR="$PROJECT_ROOT/autodense/plugin"

echo "🔧 Building AutoDense Java classpath..."

# Ensure we're in the right directory
cd "$PLUGIN_DIR"

# Clean and compile to ensure fresh build
echo "📦 Compiling Java components..."
mvn -q clean compile

# Generate classpath file
echo "🔗 Generating Maven classpath..."
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

# Verify classpath file exists
if [[ ! -f "target/classpath.txt" ]]; then
    echo "❌ Failed to generate classpath file"
    exit 1
fi

# Verify compiled classes exist
if [[ ! -d "target/classes" ]]; then
    echo "❌ Compiled classes directory not found"
    exit 1
fi

# Check for critical classes
CRITICAL_CLASSES=(
    "com/betterdairy/autodense/cli/AutotuneAnalysisCLI.class"
    "com/betterdairy/autodense/tools/gel/LaneDetectionTools.class"
    "com/betterdairy/autodense/tools/gel/BandDetectionTools.class"
)

echo "🔍 Verifying critical classes..."
for class_file in "${CRITICAL_CLASSES[@]}"; do
    if [[ ! -f "target/classes/$class_file" ]]; then
        echo "❌ Missing critical class: $class_file"
        exit 1
    fi
done

# Build complete classpath (classes + dependencies)
CLASSPATH_FILE="$PLUGIN_DIR/target/classpath.txt"
FULL_CLASSPATH="$PLUGIN_DIR/target/classes:$(cat "$CLASSPATH_FILE")"

# Write full classpath to a runtime file
echo "$FULL_CLASSPATH" > "$PLUGIN_DIR/target/runtime-classpath.txt"

# Verify JSON dependency is available (critical for CLI)
if ! echo "$FULL_CLASSPATH" | grep -q "json-"; then
    echo "⚠️  Warning: JSON library not found in classpath - CLI may fail"
fi

# Count JAR files
JAR_COUNT=$(echo "$FULL_CLASSPATH" | tr ':' '\n' | grep -c '\.jar$' || echo "0")

echo "✅ Classpath built successfully:"
echo "   📁 Classes: $PLUGIN_DIR/target/classes"  
echo "   📚 Dependencies: $JAR_COUNT JAR files"
echo "   💾 Runtime classpath: $PLUGIN_DIR/target/runtime-classpath.txt"

# Test the classpath by attempting to load the main class
echo "🧪 Testing classpath..."
if java -cp "$FULL_CLASSPATH" com.betterdairy.autodense.cli.AutotuneAnalysisCLI 2>/dev/null; then
    echo "✅ Main class loads successfully"
else
    echo "⚠️  Warning: Main class failed to load - check for missing dependencies"
fi

echo "🎉 Classpath build complete!"