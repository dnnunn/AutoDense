#!/bin/bash
# AutoDense Test Environment Setup
# Run with: source scripts/test_env.sh

echo "🔧 Setting up AutoDense test environment..."

# Activate Python virtual environment
if [ -f ".venv/bin/activate" ]; then
    source .venv/bin/activate
    echo "✅ Python virtual environment activated"
else
    echo "❌ Python virtual environment not found at .venv/"
fi

# Set Fiji directory
export FIJI_DIR=/Applications/Fiji
echo "✅ FIJI_DIR set to: $FIJI_DIR"

# Set other AutoDense environment variables
export AUTODENSE_MAIN_CLASS=com.betterdairy.autodense.cli.AutotuneAnalysisCLI
export POM_DIR=autodense/plugin
export JAVA_MAX_HEAP=6g
export PYTHONUNBUFFERED=1

echo "✅ Environment ready for AutoDense testing"
echo "🧪 Now you can run: python -m autodense_autotune.java_bridge [task] --input [image] --outdir [output] --config [config]"