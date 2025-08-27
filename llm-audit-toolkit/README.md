# LLM Audit Toolkit

> **Doc Meta**
> - **Purpose:** Reusable multi-LLM codebase auditor for comprehensive code analysis
> - **Scope:** Cross-platform toolkit supporting OpenAI, Anthropic, Gemini, and xAI providers
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-27

A comprehensive, reusable toolkit for conducting automated code audits using multiple Large Language Models (LLMs). Originally developed for the AutoDense project, this toolkit has been packaged for use across any codebase.

## Features

- **Multi-LLM Support**: Query OpenAI GPT, Anthropic Claude, Google Gemini, and xAI Grok simultaneously
- **Smart File Exclusion**: Advanced filtering to focus on source code and avoid build artifacts
- **Consensus Generation**: Consolidate multiple audit results into a unified action plan
- **Virtual Environment Management**: Automatic setup and dependency management
- **Robust Fallback**: Graceful handling when binary file uploads aren't supported
- **Rich Output**: Structured reports with executive summaries, findings, and action items

## Quick Start

1. **Clone or copy this toolkit to your project directory**
2. **Set up API keys** (copy `api-config.properties.example` to `api-config.properties` and fill in your keys)
3. **Run the audit**: `./audit.sh`

That's it! The toolkit will automatically set up a virtual environment, install dependencies, and run the audit.

## Installation

### Option 1: Using the Bash Script (Recommended)

The included `audit.sh` script handles everything automatically:

```bash
# Make the script executable (if needed)
chmod +x audit.sh

# Run with default providers (OpenAI, Gemini, Grok)
./audit.sh

# Run with specific providers
./audit.sh --providers openai,anthropic

# Use a different consensus provider
./audit.sh --consensus anthropic

# Set a custom project name
./audit.sh --project-name "MyProject"
```

### Option 2: Manual Setup

If you prefer to set up manually:

```bash
# Create virtual environment
python3 -m venv .audit_venv
source .audit_venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Set your API keys
export OPENAI_API_KEY="your_key_here"
export GEMINI_API_KEY="your_key_here"
# ... etc

# Run the audit
python audit_orchestrator.py run
```

## Configuration

### API Keys

You need at least one API key to run audits. Set them either as environment variables or in an `api-config.properties` file:

#### Environment Variables
```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."
export GEMINI_API_KEY="AIza..."
export XAI_API_KEY="xai-..."  # or GROK_API_KEY
```

#### Configuration File
Copy `api-config.properties.example` to `api-config.properties` and fill in your keys:

```properties
OPENAI_API_KEY=sk-your_openai_key_here
ANTHROPIC_API_KEY=sk-ant-your_anthropic_key_here
GEMINI_API_KEY=AIza_your_gemini_key_here
XAI_API_KEY=xai-your_xai_key_here
```

### Getting API Keys

- **OpenAI**: https://platform.openai.com/api-keys
- **Anthropic**: https://console.anthropic.com/
- **Google Gemini**: https://aistudio.google.com/app/apikey
- **xAI (Grok)**: https://console.x.ai/

### Advanced Configuration

Create a `.auditor.yml` file to customize default settings:

```bash
python audit_orchestrator.py init-config
```

This creates a YAML configuration file you can modify to change:
- Default providers
- Model selections
- File exclusion patterns
- Sampling limits

## Usage Examples

### Basic Usage

```bash
# Run audit with default settings
./audit.sh

# Or directly with Python
python audit_orchestrator.py run
```

### Custom Provider Selection

```bash
# Use only OpenAI and Anthropic
./audit.sh --providers openai,anthropic

# Use Gemini for consensus
./audit.sh --consensus gemini
```

### Advanced Options

```bash
# Audit a different directory
python audit_orchestrator.py run --root /path/to/project

# Specify audit issues file
python audit_orchestrator.py run --issues-file AUDIT_ISSUES.md

# Custom output directory
python audit_orchestrator.py run --out-dir my-audits

# Limit file sampling (for very large repos)
python audit_orchestrator.py run --max-files 500 --max-bytes 3000000
```

## Output Structure

The audit creates a timestamped directory under `audits/` with:

- **Individual audit reports**: `openai.md`, `gemini.md`, `grok.md`, etc.
- **Consensus report**: `CONSENSUS.md` - unified findings from all providers
- **Action items**: `CONSENSUS.todo.md` - checkbox list of tasks
- **Implementation plan**: `CLAUDE.md` - structured plan for ClaudeCode
- **Raw data**: JSON files with complete API responses
- **Process artifacts**: `PROMPT.txt`, `MANIFEST.txt`

## File Exclusion

The toolkit automatically excludes common build artifacts and dependencies:

### Excluded by Default
- Version control: `.git`, `.github`, `.gitignore`
- Build artifacts: `target`, `build`, `dist`, `*.class`, `*.jar`
- Dependencies: `node_modules`, `venv`, `__pycache__`
- IDEs: `.idea`, `.vscode`
- OS files: `.DS_Store`, `Thumbs.db`
- Large binaries and packages

### Custom Exclusions

Modify the `DEFAULT_EXCLUDES` list in `audit_orchestrator.py` or use a `.auditor.yml` config file.

## Provider-Specific Notes

### OpenAI
- Uses Assistants API for file uploads when possible
- Falls back to Chat Completions API with text sampling
- Default model: `gpt-4o-mini` (fast and capable)

### Anthropic (Claude)
- Uses Messages API with text-based content
- Excellent at detailed code analysis
- Default model: `claude-3-5-sonnet-20240620`

### Google Gemini
- Uses Generative Language API
- Good balance of speed and analysis quality
- Default model: `gemini-1.5-pro`

### xAI Grok
- Uses OpenAI-compatible chat completions API
- Provides unique perspectives on code quality
- Default model: `grok-2-latest`

## Troubleshooting

### Common Issues

1. **"Python not found"**: Install Python 3.7+ and ensure it's in your PATH
2. **API key errors**: Check your API keys are set correctly and have sufficient quota
3. **Rate limiting**: Some providers have rate limits; the toolkit includes retry logic
4. **Large repositories**: Use `--max-files` and `--max-bytes` to limit sampling

### Debug Mode

Set environment variable for verbose output:
```bash
export AUDIT_DEBUG=1
./audit.sh
```

### Virtual Environment Issues

If the virtual environment becomes corrupted:
```bash
rm -rf .audit_venv
./audit.sh  # Will recreate automatically
```

## Integration with Existing Projects

### Adding to Your Project

1. Copy the entire `llm-audit-toolkit/` directory to your project root
2. Add `api-config.properties` to your `.gitignore`
3. Run `./llm-audit-toolkit/audit.sh` from your project root

### CI/CD Integration

Example GitHub Action:

```yaml
name: Code Audit
on:
  schedule:
    - cron: '0 2 * * 0'  # Weekly on Sunday
  workflow_dispatch:

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run LLM Audit
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
        run: |
          cd llm-audit-toolkit
          ./audit.sh --providers openai,gemini
      - name: Upload Results
        uses: actions/upload-artifact@v3
        with:
          name: audit-results
          path: audits/
```

## Extending the Toolkit

### Adding New Providers

1. Create a new provider class inheriting from `Provider`
2. Implement the `audit` method
3. Add to `provider_from_name` function
4. Update `DEFAULT_MODELS` dictionary

### Custom Analysis Prompts

Modify `build_audit_prompt()` function to customize the analysis focus for your specific use case.

### Output Formats

The toolkit generates Markdown reports by default. You can extend the output processing to generate HTML, PDF, or integrate with issue tracking systems.

## License and Attribution

This toolkit was originally developed for the BetterDairy AutoDense project and has been packaged for broader reuse. Feel free to adapt and extend for your specific needs.

## Support

For issues specific to this toolkit, check the troubleshooting section above or review the source code. The toolkit is designed to be self-contained and debuggable.