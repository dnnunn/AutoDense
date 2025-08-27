# LLM Debug Toolkit

> **Doc Meta**
> - **Purpose:** Interactive multi-LLM debugging assistant with guided prompt engineering
> - **Scope:** Cross-platform toolkit for debugging any codebase using OpenAI, Anthropic, Gemini, and xAI providers
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-27

An interactive debugging assistant that guides you through creating effective debugging prompts and leverages multiple Large Language Models to analyze your codebase and provide comprehensive debugging strategies.

## 🐛 What Makes This Different

Unlike generic code analysis tools, the LLM Debug Toolkit:

- **Interactive Prompt Engineering**: Guides you through creating effective debugging prompts using best practices
- **Multi-LLM Analysis**: Gets different AI perspectives on your debugging challenge
- **Consensus Building**: Synthesizes multiple analyses into a unified, actionable debug plan
- **Context-Aware**: Understands your specific issue, not just general code quality
- **Practical Focus**: Delivers concrete next steps and implementation guidance

## 🚀 Quick Start

1. **Extract the toolkit to your project directory**
2. **Set up API keys**: Copy `api-config.properties.example` to `api-config.properties` and add your keys
3. **Run the interactive debugger**: `./debug.sh`

The toolkit will guide you through the debugging process step by step.

## 📋 Interactive Debugging Process

### Step 1: Prompt Engineering Guidance
The toolkit shows you how to create effective debugging prompts with components like:
- Clear problem statement (expected vs actual behavior)
- Reproduction steps
- Component focus (which files/modules are involved)
- Error information and context

### Step 2: Multi-LLM Analysis
Your debugging prompt and codebase are sent to multiple AI providers:
- **OpenAI GPT**: Excellent at systematic analysis and solution generation
- **Anthropic Claude**: Strong at understanding complex code interactions
- **Google Gemini**: Good balance of speed and insight
- **xAI Grok**: Provides unique perspectives on debugging approaches

### Step 3: Consensus Debug Plan
All analyses are consolidated into:
- Unified root cause assessment
- Step-by-step investigation plan
- Multiple solution options (quick fix vs comprehensive)
- Testing and validation strategy
- Prevention measures

## 🛠 Installation

### Option 1: Using the Debug Script (Recommended)

```bash
# Make the script executable
chmod +x debug.sh

# Run interactive debugging session
./debug.sh

# Run with specific providers
./debug.sh --providers openai,anthropic

# Use different consensus provider
./debug.sh --consensus gemini

# Set custom project name
./debug.sh --project-name "MyBuggyApp"
```

### Option 2: Manual Setup

```bash
# Create virtual environment
python3 -m venv .debug_venv
source .debug_venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Set API keys
export OPENAI_API_KEY="your_key_here"
export GEMINI_API_KEY="your_key_here"
# ... etc

# Run interactive debugging
python debug_orchestrator.py debug
```

## 🔑 Configuration

### API Keys

You need at least one API key. Set them as environment variables or in `api-config.properties`:

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

## 📊 Example Debugging Session

Here's what a typical session looks like:

```
🐛 LLM Debug Toolkit - Interactive Prompt Builder

📚 Would you like to see prompt engineering guidelines first? [Y/n]: y

[Guidelines displayed]

Let's build your debugging prompt step by step:

1. Problem Statement
   Describe the issue: What should happen vs what actually happens?
> User login fails intermittently - expected redirect to dashboard, 
  but shows "Authentication failed" ~30% of the time

2. Reproduction Steps
   How can the issue be reproduced? Include specific steps, inputs, conditions:
> 1. Go to login page, 2. Enter valid credentials, 3. Click "Sign In"
  Issue occurs mainly during peak hours (2-4 PM EST)

3. Likely Components
   Which files, modules, or system parts are probably involved?
> AuthController.java, UserService.java, database connection pool

4. Error Information
   Any error messages, stack traces, or log entries?
> Stack trace shows timeout in UserService.authenticate() line 45

5. Context & Constraints
   Timeline, risk tolerance, dependencies, recent changes?
> Need quick fix by EOD, added rate limiting middleware last week

[Analysis runs with multiple LLMs...]

✅ Debug Session Complete!
Check debug_sessions/2025-08-27_143022/ for:
- CONSENSUS.md (unified analysis)  
- ACTION_ITEMS.md (step-by-step fixes)
- Individual provider analyses
```

## 📁 Output Structure

Each debug session creates a timestamped directory under `debug_sessions/` with:

### Core Files
- **`CONSENSUS.md`** - Unified analysis and debug plan from all providers
- **`ACTION_ITEMS.md`** - Checkbox list of concrete next steps  
- **`DEBUG_SUMMARY.md`** - Concise session overview and follow-up guidance

### Process Files
- **`USER_PROMPT.md`** - Your original debugging request
- **`FINAL_PROMPT.txt`** - Complete prompt sent to LLMs
- **`MANIFEST.txt`** - Repository file listing

### Individual Analyses
- **`openai.md`**, **`gemini.md`**, **`grok.md`**, etc. - Each provider's analysis
- **`*.json`** - Raw API responses for detailed review

## 🎯 Best Practices for Debugging Prompts

### ✅ Good Debugging Prompts Include:

1. **Specific Behaviors**
   - "Login should redirect to /dashboard but shows 401 error instead"
   - NOT: "Login is broken"

2. **Reproduction Context**
   - "Occurs on Chrome 120+ with valid credentials during peak hours"
   - NOT: "Sometimes doesn't work"

3. **Component Hints**
   - "Likely involves AuthController.authenticate() and Redis session store"
   - NOT: "Something in the backend"

4. **Concrete Error Info**
   - "Stack trace: `NullPointerException at UserService:145`"
   - NOT: "Getting errors"

### 🎛 Command Line Options

```bash
./debug.sh --help                              # Show all options

# Provider selection
./debug.sh --providers openai,anthropic        # Use specific providers
./debug.sh --providers openai                  # Use single provider

# Consensus provider
./debug.sh --consensus anthropic               # Use Claude for final analysis
./debug.sh --consensus gemini                  # Use Gemini for consensus

# Project identification
./debug.sh --project-name "E-commerce API"     # Custom project name
```

## 🔄 Advanced Usage

### Custom Configuration

Create `.debug_config.yml` for persistent settings:

```bash
python debug_orchestrator.py init-config
```

### Integration with IDEs

You can integrate the debug toolkit with your IDE by:

1. **VS Code**: Add a task in `.vscode/tasks.json`:
```json
{
    "label": "LLM Debug",
    "type": "shell",
    "command": "./llm-debug-toolkit/debug.sh",
    "group": "test"
}
```

2. **IntelliJ**: Add as external tool in Settings → Tools → External Tools

### CI/CD Integration

Use for post-incident analysis:

```yaml
name: Post-Incident Debug Analysis
on:
  workflow_dispatch:
    inputs:
      incident_description:
        description: 'Describe the issue that occurred'
        required: true

jobs:
  debug:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run Debug Analysis
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: |
          echo "${{ github.event.inputs.incident_description }}" > incident.md
          ./llm-debug-toolkit/debug.sh --providers openai --project-name "${{ github.repository }}"
```

## 🚨 Troubleshooting

### Common Issues

1. **"No API keys found"**
   - Ensure at least one API key is set in environment variables or `api-config.properties`
   - Check that the key format is correct (OpenAI keys start with `sk-`, etc.)

2. **"Python not found"**
   - Install Python 3.7+ and ensure it's in your PATH
   - Try using `python3` instead of `python`

3. **Virtual environment issues**
   - Delete `.debug_venv/` directory and run again
   - Ensure you have write permissions in the toolkit directory

4. **Interactive prompts not working**
   - Ensure you're running in a terminal (not CI/batch mode)
   - Check that your terminal supports color output

### Debug Mode

Set environment variable for verbose output:
```bash
export DEBUG_MODE=1
./debug.sh
```

## 🔒 Security & Privacy

- **API Keys**: Never commit your `api-config.properties` file
- **Code Samples**: LLMs receive portions of your code - review your company's policies
- **Session Data**: Debug sessions are saved locally - secure your debug output
- **Rate Limits**: Providers have rate limits - the toolkit includes retry logic

## 🤝 Integration with Other Projects

### Adding to Existing Projects

1. **Copy the toolkit**: 
   ```bash
   cp -r llm-debug-toolkit /path/to/your/project/
   ```

2. **Add to .gitignore**:
   ```gitignore
   llm-debug-toolkit/api-config.properties
   llm-debug-toolkit/.debug_venv/
   debug_sessions/
   ```

3. **Run from project root**:
   ```bash
   ./llm-debug-toolkit/debug.sh
   ```

### Language-Specific Notes

- **Java**: Works well with stack traces and Maven/Gradle projects
- **Python**: Excellent integration with error messages and virtual environments  
- **JavaScript/Node.js**: Good at analyzing async issues and dependency problems
- **Go**: Effective for concurrency and performance debugging
- **Any language**: The interactive prompting works regardless of technology stack

## 📈 Comparison with Code Audit Toolkit

| Feature | Debug Toolkit | Audit Toolkit |
|---------|---------------|---------------|
| **Purpose** | Fix specific issues | General code quality |
| **Input** | Interactive problem description | Automated analysis |
| **Focus** | Root cause → solution | Systematic code review |
| **Output** | Action items for fixes | Comprehensive findings |
| **Usage** | When bugs occur | Regular maintenance |
| **Timeline** | Immediate/urgent | Scheduled/planned |

## 🎓 Tips for Effective Debugging Sessions

1. **Be Specific**: The more details you provide, the better the analysis
2. **Include Context**: Recent changes, environment details, timing patterns
3. **Focus Components**: Guide the analysis toward likely problem areas
4. **Multiple Rounds**: For complex issues, run multiple sessions as you learn more
5. **Document Solutions**: Save successful debug strategies for similar future issues

## 📞 Support

This toolkit is designed to be self-contained and debuggable. For best results:

1. **Read the prompt guidelines** before starting your first session
2. **Review the example** debugging prompt in this README
3. **Experiment** with different provider combinations
4. **Keep sessions focused** on specific issues rather than general problems

The interactive nature of the toolkit means it will guide you through creating effective debugging requests, so even if you're new to prompt engineering, you'll get good results.

---

**Happy Debugging!** 🐛➡️✅