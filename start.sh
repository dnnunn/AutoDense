#!/bin/bash
# AutoDense Startup Script
# Properly handles virtual environment activation and environment setup

set -e  # Exit on any error

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting AutoDense from: $SCRIPT_DIR"

# Step 1: Activate virtual environment FIRST
echo "📦 Activating virtual environment..."
if [ ! -d ".venv" ]; then
    echo "❌ Virtual environment not found at .venv"
    echo "Please create it with: python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt"
    exit 1
fi

source .venv/bin/activate
echo "✅ Virtual environment activated"

# Step 2: Load environment variables
echo "🔧 Loading environment configuration..."
if [ -f ".env" ]; then
    source .env
    echo "✅ Loaded .env file"
else
    echo "⚠️  No .env file found"
fi

# Step 3: Check API key
if [ -n "$OPENAI_API_KEY" ]; then
    echo "✅ OpenAI API key configured"
else
    echo "⚠️  OpenAI API key not found - AI preprocessing will be disabled"
fi

# Step 4: Set port (default to 8501)
PORT=${1:-8501}
echo "🌐 Starting on port: $PORT"

# Step 5: Kill any existing processes on that port
echo "🧹 Cleaning up any existing processes on port $PORT..."
lsof -ti:$PORT | xargs kill -9 2>/dev/null || true

# Step 6: Start the application using the Python startup script
echo "🎯 Launching AutoDense application..."
python start_autodense.py --port $PORT

echo "👋 AutoDense stopped"