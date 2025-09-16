#!/usr/bin/env python3
"""
AutoDense Startup Script

This script automatically handles all environment setup and launches
the AutoDense Streamlit application with optimal configuration.

Usage:
    python start_autodense.py [--port PORT] [--host HOST]

Example:
    python start_autodense.py
    python start_autodense.py --port 8503
    python start_autodense.py --host 0.0.0.0 --port 8501
"""

import sys
import os
import subprocess
import argparse
from pathlib import Path

def setup_environment():
    """Ensure all environment variables and paths are configured."""

    # Set project root
    project_root = Path(__file__).parent.absolute()

    # Add to Python path
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

    # Set PYTHONPATH environment variable
    current_pythonpath = os.environ.get('PYTHONPATH', '')
    if str(project_root) not in current_pythonpath:
        os.environ['PYTHONPATH'] = f"{project_root}:{current_pythonpath}".rstrip(':')

    # Load .env file if it exists
    env_file = project_root / ".env"
    if env_file.exists():
        print(f"📁 Loading environment from {env_file}")
        with open(env_file, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    if key and value:
                        os.environ[key] = value

    # Load API key from api_properties.config as fallback
    api_config = project_root / "api_properties.config"
    if api_config.exists() and not os.environ.get('OPENAI_API_KEY'):
        print(f"🔑 Loading API configuration from {api_config}")
        with open(api_config, 'r') as f:
            for line in f:
                line = line.strip()
                if line.startswith('OPENAI_API_KEY='):
                    key, value = line.split('=', 1)
                    if value:
                        os.environ['OPENAI_API_KEY'] = value
                        break

    # Set defaults for Streamlit
    defaults = {
        'STREAMLIT_BROWSER_GATHER_USAGE_STATS': 'false',
        'STREAMLIT_SERVER_HEADLESS': 'true'
    }

    for key, value in defaults.items():
        if key not in os.environ:
            os.environ[key] = value

    return project_root

def main():
    parser = argparse.ArgumentParser(description='Start AutoDense with automatic environment setup')
    parser.add_argument('--port', type=int, default=8502, help='Port to run on (default: 8502)')
    parser.add_argument('--host', default='localhost', help='Host to bind to (default: localhost)')
    parser.add_argument('--debug', action='store_true', help='Enable debug mode')

    args = parser.parse_args()

    # Setup environment
    print("🚀 Starting AutoDense with automatic environment configuration...")
    project_root = setup_environment()

    # Check dependencies
    try:
        import streamlit
        import numpy
        import PIL
        print("✅ All dependencies available")
    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        print("Please install required packages: pip install -r requirements.txt")
        sys.exit(1)

    # Check API key
    if os.environ.get('OPENAI_API_KEY'):
        print("✅ OpenAI API key configured")
    else:
        print("⚠️  OpenAI API key not found - AI preprocessing will be disabled")

    # Build streamlit command
    app_file = project_root / "ui" / "streamlit_autodense_app.py"

    cmd = [
        sys.executable, "-m", "streamlit", "run", str(app_file),
        "--server.port", str(args.port),
        "--server.address", args.host,
        "--browser.gatherUsageStats", "false",
        "--server.headless", "true"
    ]

    if not args.debug:
        cmd.extend(["--logger.level", "warning"])

    print(f"🌐 Starting AutoDense at http://{args.host}:{args.port}")
    print(f"📂 Project root: {project_root}")
    print(f"🔧 Command: {' '.join(cmd)}")

    # Change to ui directory and run
    os.chdir(project_root / "ui")

    try:
        subprocess.run(cmd, check=True)
    except KeyboardInterrupt:
        print("\n👋 AutoDense stopped by user")
    except subprocess.CalledProcessError as e:
        print(f"❌ Error starting AutoDense: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()