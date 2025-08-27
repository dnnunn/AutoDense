#!/bin/bash
# LLM Audit Toolkit - Portable audit script with virtual environment setup
# This script sets up a virtual environment and runs the audit orchestrator

set -e  # Exit on any error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.audit_venv"
REQUIREMENTS_FILE="$SCRIPT_DIR/requirements.txt"
AUDIT_SCRIPT="$SCRIPT_DIR/audit_orchestrator.py"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Python 3 is available
check_python() {
    if command -v python3 &> /dev/null; then
        PYTHON_CMD="python3"
    elif command -v python &> /dev/null; then
        # Check if it's Python 3
        python_version=$(python --version 2>&1 | head -n1 | cut -d" " -f2 | cut -d"." -f1)
        if [[ $python_version == "3" ]]; then
            PYTHON_CMD="python"
        else
            log_error "Python 3 is required but not found. Please install Python 3."
            exit 1
        fi
    else
        log_error "Python is not installed. Please install Python 3."
        exit 1
    fi
    
    log_info "Using Python: $PYTHON_CMD"
}

# Set up virtual environment
setup_venv() {
    if [ ! -d "$VENV_DIR" ]; then
        log_info "Creating virtual environment..."
        $PYTHON_CMD -m venv "$VENV_DIR"
        log_success "Virtual environment created at $VENV_DIR"
    else
        log_info "Virtual environment already exists at $VENV_DIR"
    fi
    
    # Activate virtual environment
    log_info "Activating virtual environment..."
    source "$VENV_DIR/bin/activate"
    
    # Upgrade pip
    log_info "Upgrading pip..."
    pip install --upgrade pip > /dev/null 2>&1
    
    # Install requirements if file exists
    if [ -f "$REQUIREMENTS_FILE" ]; then
        log_info "Installing requirements from $REQUIREMENTS_FILE..."
        pip install -r "$REQUIREMENTS_FILE" > /dev/null 2>&1
        log_success "Requirements installed successfully"
    else
        log_warning "requirements.txt not found. Installing basic dependencies..."
        pip install httpx typer pydantic pyyaml tiktoken rich > /dev/null 2>&1
        log_success "Basic dependencies installed"
    fi
}

# Check API keys
check_api_keys() {
    local keys_found=0
    local missing_keys=()
    
    if [ ! -z "${OPENAI_API_KEY:-}" ]; then
        log_info "✓ OpenAI API key found"
        ((keys_found++))
    else
        missing_keys+=("OPENAI_API_KEY")
    fi
    
    if [ ! -z "${ANTHROPIC_API_KEY:-}" ]; then
        log_info "✓ Anthropic API key found"
        ((keys_found++))
    else
        missing_keys+=("ANTHROPIC_API_KEY")
    fi
    
    if [ ! -z "${GEMINI_API_KEY:-}" ]; then
        log_info "✓ Gemini API key found"
        ((keys_found++))
    else
        missing_keys+=("GEMINI_API_KEY")
    fi
    
    if [ ! -z "${XAI_API_KEY:-}" ] || [ ! -z "${GROK_API_KEY:-}" ]; then
        log_info "✓ xAI/Grok API key found"
        ((keys_found++))
    else
        missing_keys+=("XAI_API_KEY or GROK_API_KEY")
    fi
    
    if [ $keys_found -eq 0 ]; then
        log_error "No API keys found! Please set at least one of:"
        for key in "${missing_keys[@]}"; do
            echo "  - $key"
        done
        echo
        echo "You can also create an api-config.properties file in the current directory with:"
        echo "  OPENAI_API_KEY=your_key_here"
        echo "  GEMINI_API_KEY=your_key_here"
        echo "  # etc..."
        exit 1
    else
        log_success "$keys_found API key(s) configured"
        if [ ${#missing_keys[@]} -gt 0 ]; then
            log_warning "Missing keys: ${missing_keys[*]}"
        fi
    fi
}

# Load API keys from api-config.properties if it exists
load_api_config() {
    local config_file="api-config.properties"
    if [ -f "$config_file" ]; then
        log_info "Loading API keys from $config_file..."
        # Source the properties file, handling both key=value and key="value" formats
        while IFS='=' read -r key value; do
            # Skip comments and empty lines
            if [[ $key =~ ^[[:space:]]*# ]] || [[ -z $key ]]; then
                continue
            fi
            
            # Remove whitespace and quotes
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs | sed 's/^["'\'']*//;s/["'\'']*$//')
            
            if [[ -n $key && -n $value ]]; then
                export "$key"="$value"
            fi
        done < "$config_file"
        log_success "API configuration loaded"
    fi
}

# Print usage information
print_usage() {
    echo "LLM Audit Toolkit - Multi-LLM Codebase Auditor"
    echo
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --providers LIST     Comma-separated list of providers (openai,gemini,grok,anthropic)"
    echo "  --consensus PROVIDER Provider to use for consensus (default: openai)"
    echo "  --project-name NAME  Project name for the audit"
    echo "  --help              Show this help message"
    echo
    echo "Examples:"
    echo "  $0                                    # Run with default providers"
    echo "  $0 --providers openai,gemini         # Run with specific providers"
    echo "  $0 --consensus anthropic             # Use Anthropic for consensus"
    echo "  $0 --project-name MyProject          # Set custom project name"
    echo
    echo "Environment Variables:"
    echo "  OPENAI_API_KEY      OpenAI API key"
    echo "  ANTHROPIC_API_KEY   Anthropic API key"
    echo "  GEMINI_API_KEY      Google Gemini API key"
    echo "  XAI_API_KEY         xAI Grok API key (or GROK_API_KEY)"
    echo
    echo "Configuration:"
    echo "  You can also create 'api-config.properties' with your API keys"
}

# Parse command line arguments
parse_args() {
    PROVIDERS=""
    CONSENSUS=""
    PROJECT_NAME=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --providers)
                PROVIDERS="$2"
                shift 2
                ;;
            --consensus)
                CONSENSUS="$2"
                shift 2
                ;;
            --project-name)
                PROJECT_NAME="$2"
                shift 2
                ;;
            --help|-h)
                print_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
    done
}

# Run the audit
run_audit() {
    local cmd_args=()
    
    if [ ! -z "$PROVIDERS" ]; then
        # Convert comma-separated to space-separated for multiple --providers flags
        IFS=',' read -ra PROVIDER_ARRAY <<< "$PROVIDERS"
        for provider in "${PROVIDER_ARRAY[@]}"; do
            cmd_args+=(--providers "$provider")
        done
    fi
    
    if [ ! -z "$CONSENSUS" ]; then
        cmd_args+=(--consensus "$CONSENSUS")
    fi
    
    if [ ! -z "$PROJECT_NAME" ]; then
        cmd_args+=(--project-name "$PROJECT_NAME")
    fi
    
    log_info "Running audit with command: python $AUDIT_SCRIPT run ${cmd_args[*]}"
    
    # Set environment variables for the audit script
    export OPENAI_API_KEY="${OPENAI_API_KEY:-}"
    export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:-}"
    export GEMINI_API_KEY="${GEMINI_API_KEY:-}"
    export XAI_API_KEY="${XAI_API_KEY:-}"
    export GROK_API_KEY="${GROK_API_KEY:-}"
    
    python "$AUDIT_SCRIPT" run "${cmd_args[@]}"
}

# Main execution
main() {
    echo "=============================================="
    echo "  LLM Audit Toolkit"
    echo "=============================================="
    echo
    
    # Parse command line arguments
    parse_args "$@"
    
    # Check Python availability
    check_python
    
    # Load API configuration
    load_api_config
    
    # Set up virtual environment
    setup_venv
    
    # Check API keys
    check_api_keys
    
    # Run the audit
    log_info "Starting audit..."
    run_audit
    
    log_success "Audit completed successfully!"
    log_info "Check the 'audits/' directory for results"
}

# Run main function with all arguments
main "$@"