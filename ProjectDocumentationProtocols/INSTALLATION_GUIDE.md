# Installation and Usage Guide

> **Doc Meta**
> - **Purpose:** Complete guide for installing and customizing the documentation protocol package
> - **Scope:** Setup procedures, customization instructions, and usage examples for any project type
> - **Owner:** @template-maintainer
> - **Last-verified:** 2025-08-30

## 🚀 Quick Installation

### Option 1: Automated Setup (Recommended)
```bash
# Clone or download this documentation protocol package
cd /path/to/your/project

# Run the setup script
/path/to/documentation-protocols/SETUP.sh

# Or run from within your project if you copied the package there:
./SETUP.sh
```

### Option 2: Manual Setup
```bash
# Create directory structure
mkdir -p {SessionSummaries,NextSteps,Issues,StructureDocs,docs}

# Copy core files
cp STARTUP.md WRAPUP.md CLAUDE.md /path/to/your/project/

# Copy templates to appropriate locations  
cp templates/* /path/to/your/project/docs/templates/
```

## 📋 What Gets Created

### Directory Structure
```
your-project/
├── SessionSummaries/          # Session history and summaries
├── NextSteps/                 # Priority planning and tasks  
├── Issues/                    # Bug reports and investigations
├── StructureDocs/             # Project structure snapshots
├── docs/                      # Documentation and catalog
│   └── DOCUMENT_CATALOG.md    # Master document index
├── STARTUP.md                 # Session initialization procedures
├── WRAPUP.md                  # End-of-session procedures
├── CLAUDE.md                  # AI assistant context primer
└── README.md                  # Updated with documentation standards
```

### Template Collection
All templates are stored in the original package for reference:
- `SessionSummary_Template.md`
- `NextSteps_Template.md` 
- `Issues_Template.md`
- `StructureDocs_Template.md`
- `DocumentCatalog_Template.md`
- `EnvironmentSetup_Template.md`
- `DocMeta_Template.md`
- `TodoTracking_Guide.md`

## 🔧 Project-Specific Customization

### Step 1: Update CLAUDE.md (Critical)
Replace all placeholder values with your project information:

```bash
# Edit the project primer
vim CLAUDE.md

# Replace these placeholders:
# [PROJECT_NAME] → Your actual project name
# [PROJECT_TYPE] → Web app, CLI tool, library, etc.
# [PRIMARY_PURPOSE] → What the project does
# [CURRENT_DATE] → Today's date
# [TECHNOLOGY_STACK] → Java 17, Python 3.9, Node.js 18, etc.
# [BUILD_TOOL] → Maven, npm, Cargo, etc.
# [ARCHITECTURE_PATTERN] → Microservices, MVC, etc.
```

### Step 2: Document Your Environment
Create environment setup documentation:

```bash
# Use the template as a guide
cp templates/EnvironmentSetup_Template.md docs/Environment_Setup.md

# Customize with your specific:
# - Runtime versions and requirements
# - Build commands and procedures  
# - Database setup (if applicable)
# - Development server commands
# - Common troubleshooting steps
```

### Step 3: Create Initial Structure Document
```bash
# Document your current project structure
cd StructureDocs
cp ../templates/StructureDocs_Template.md Structure_$(date +%B_%d_%Y)_Initial.md

# Fill in:
# - Your actual directory structure
# - Critical file locations
# - Environment setup procedures
# - Build and test commands
```

### Step 4: Update Document Catalog  
```bash
# Add your existing documentation
vim docs/DOCUMENT_CATALOG.md

# Include:
# - Current README files
# - API documentation
# - Architecture docs
# - Setup guides
# - Any existing technical documentation
```

## 🎯 Project Type Specific Guides

### Web Applications
**Focus areas for customization:**
- Database setup and migrations
- Environment variables and secrets
- Development server configuration
- API endpoint documentation
- Frontend build processes

**Key sections to emphasize in CLAUDE.md:**
```markdown
## Core Capabilities
* **Frontend:** [Framework] with [key features]
* **Backend API:** [Technology] with [authentication method]
* **Database:** [Database type] with [key schemas]
* **Authentication:** [Auth method and configuration]
```

### CLI Tools / Libraries
**Focus areas for customization:**
- Package management and publishing
- Command-line interface documentation
- Testing strategies for various inputs
- Cross-platform compatibility

**Key sections to emphasize in CLAUDE.md:**
```markdown  
## Core Capabilities
* **CLI Interface:** [Commands and options available]
* **Library API:** [Public interfaces and usage]
* **Platform Support:** [Supported operating systems]
* **Installation:** [Package managers and methods]
```

### Data Science / ML Projects
**Focus areas for customization:**
- Data pipeline documentation
- Model training procedures  
- Jupyter notebook organization
- Experiment tracking
- Dataset documentation

**Key sections to emphasize in CLAUDE.md:**
```markdown
## Core Capabilities
* **Data Processing:** [Pipeline stages and tools]
* **Models:** [Model types and performance metrics]
* **Experimentation:** [Tracking tools and procedures]
* **Deployment:** [Model serving and monitoring]
```

### Microservices / Distributed Systems
**Focus areas for customization:**
- Service dependency mapping
- Inter-service communication
- Deployment orchestration
- Monitoring and observability

**Key sections to emphasize in CLAUDE.md:**
```markdown
## Current Architecture
* **[Service1]** → [Responsibility and endpoints]
* **[Service2]** → [Responsibility and endpoints]  
* **Message Bus** → [Communication patterns]
* **Data Storage** → [Database per service strategy]
```

## 🛠️ Integration with Development Tools

### Git Integration
Add pre-commit hooks for documentation quality:

```bash
# .git/hooks/pre-commit
#!/bin/bash
# Check for Doc Meta blocks in markdown files
for file in $(git diff --cached --name-only | grep '\.md$'); do
  if [ -f "$file" ] && ! grep -q "> \*\*Doc Meta\*\*" "$file"; then
    echo "ERROR: $file missing required Doc Meta block"
    echo "Add this at the top of your markdown file:"
    echo "> **Doc Meta**"
    echo "> - **Purpose:** Brief description"
    echo "> - **Scope:** What it covers"  
    echo "> - **Owner:** @your-handle"
    echo "> - **Last-verified:** $(date +%Y-%m-%d)"
    exit 1
  fi
done
```

### IDE Integration
**VS Code:** Add workspace settings for documentation:
```json
{
  "files.associations": {
    "*.md": "markdown"
  },
  "markdown.validate.enabled": true,
  "markdown.suggest.paths.enabled": true
}
```

**IntelliJ/WebStorm:** Configure markdown settings:
- Enable Markdown plugin
- Set up live preview
- Configure spell checking for documentation

### CI/CD Integration
Add documentation validation to your build pipeline:

```yaml
# GitHub Actions example
- name: Validate Documentation
  run: |
    # Check all markdown files have Doc Meta blocks
    find . -name "*.md" -exec grep -L "> \*\*Doc Meta\*\*" {} \; | tee missing-meta.txt
    if [ -s missing-meta.txt ]; then
      echo "Files missing Doc Meta blocks:"
      cat missing-meta.txt
      exit 1
    fi
```

## 📊 Monitoring and Maintenance

### Weekly Tasks
- [ ] Update document catalog with new/modified files
- [ ] Review and complete session summaries
- [ ] Clean up completed tasks from next steps
- [ ] Archive old issues that are resolved

### Monthly Tasks
- [ ] Audit Doc Meta blocks for freshness
- [ ] Update environment procedures if changed
- [ ] Review and update project structure documentation
- [ ] Assess documentation coverage gaps

### Quality Metrics to Track
- **Documentation freshness:** Percentage of docs updated within 90 days
- **Coverage:** Ratio of features with documentation
- **Usage:** How often documentation is referenced
- **Session efficiency:** Time saved through systematic procedures

## 🔍 Troubleshooting

### Common Issues

#### Setup Script Fails
```bash
# Check permissions
ls -la SETUP.sh
chmod +x SETUP.sh

# Check path issues
pwd
ls -la templates/
```

#### Missing Templates
```bash
# Verify template directory exists
ls -la templates/
# Re-download package if templates are missing
```

#### Git Integration Issues
```bash
# Check .gitignore doesn't exclude documentation
grep -v "^#" .gitignore | grep -E "(\.md|SessionSummaries|NextSteps)"
# Remove problematic exclusions
```

### Getting Help
1. **Check README.md** - Project-specific guidance
2. **Review templates** - Examples and patterns
3. **Examine existing usage** - Other projects using these protocols
4. **Create issue** - If templates need improvement

---

## 🎯 Success Checklist

After setup, verify:
- [ ] All directories created and accessible
- [ ] CLAUDE.md customized with project specifics
- [ ] Environment procedures documented
- [ ] Initial structure document created
- [ ] Document catalog established
- [ ] Team members understand Doc Meta requirements
- [ ] Session procedures tested with at least one complete cycle

**Setup time investment:** ~30-60 minutes initial setup, ~15 minutes per session thereafter
**Time savings:** 2-3x faster session startup, systematic progress tracking, no lost context

*This documentation protocol package scales from individual projects to large teams and has proven effective across various project types and development workflows.*