#!/bin/bash

# Documentation Protocol Setup Script
# Purpose: Initialize documentation protocol structure in any project
# Usage: ./SETUP.sh [project-root-path]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="${1:-$(pwd)}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_DIR="$SCRIPT_DIR/templates"

echo -e "${BLUE}📋 Documentation Protocol Setup${NC}"
echo -e "Project root: ${PROJECT_ROOT}"
echo -e "Template source: ${TEMPLATE_DIR}"
echo ""

# Validate project root exists
if [ ! -d "$PROJECT_ROOT" ]; then
    echo -e "${RED}❌ Error: Project root directory does not exist: $PROJECT_ROOT${NC}"
    exit 1
fi

# Validate template directory exists
if [ ! -d "$TEMPLATE_DIR" ]; then
    echo -e "${RED}❌ Error: Template directory not found: $TEMPLATE_DIR${NC}"
    exit 1
fi

# Function to create directory if it doesn't exist
create_directory() {
    local dir="$1"
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir"
        echo -e "${GREEN}✅ Created directory: $dir${NC}"
    else
        echo -e "${YELLOW}📁 Directory exists: $dir${NC}"
    fi
}

# Function to copy file if it doesn't exist
copy_file() {
    local source="$1"
    local destination="$2"
    local filename=$(basename "$destination")
    
    if [ ! -f "$destination" ]; then
        cp "$source" "$destination"
        echo -e "${GREEN}✅ Created: $filename${NC}"
    else
        echo -e "${YELLOW}📄 File exists: $filename (skipping)${NC}"
    fi
}

echo -e "${BLUE}🏗️  Creating directory structure...${NC}"

# Create main documentation directories
create_directory "$PROJECT_ROOT/SessionSummaries"
create_directory "$PROJECT_ROOT/NextSteps"
create_directory "$PROJECT_ROOT/Issues"
create_directory "$PROJECT_ROOT/StructureDocs"
create_directory "$PROJECT_ROOT/docs"

echo ""
echo -e "${BLUE}📄 Copying core files...${NC}"

# Copy main documentation files to project root
copy_file "$SCRIPT_DIR/STARTUP.md" "$PROJECT_ROOT/STARTUP.md"
copy_file "$SCRIPT_DIR/WRAPUP.md" "$PROJECT_ROOT/WRAPUP.md" 
copy_file "$SCRIPT_DIR/CLAUDE.md" "$PROJECT_ROOT/CLAUDE.md"

echo ""
echo -e "${BLUE}📋 Creating .gitkeep files...${NC}"

# Add .gitkeep files to ensure empty directories are tracked
touch "$PROJECT_ROOT/SessionSummaries/.gitkeep"
touch "$PROJECT_ROOT/NextSteps/.gitkeep" 
touch "$PROJECT_ROOT/Issues/.gitkeep"
touch "$PROJECT_ROOT/StructureDocs/.gitkeep"

echo -e "${GREEN}✅ Created .gitkeep files${NC}"

echo ""
echo -e "${BLUE}📊 Creating initial document catalog...${NC}"

# Create initial document catalog if it doesn't exist
CATALOG_FILE="$PROJECT_ROOT/docs/DOCUMENT_CATALOG.md"
if [ ! -f "$CATALOG_FILE" ]; then
    cat > "$CATALOG_FILE" << 'EOF'
# Project Document Catalog

> **Doc Meta**
> - **Purpose:** Comprehensive catalog with concise summaries of all project documentation
> - **Scope:** All markdown files across the entire project with categories, summaries, and metadata
> - **Owner:** @project-owner
> - **Last-verified:** [UPDATE_DATE]

## Overview

This catalog provides a centralized reference for all project documentation, including concise summaries, categories, word counts, and verification dates.

**Total Documents:** [NUMBER] files across [NUMBER] categories
**Documentation Coverage:** [PERCENTAGE]% of project areas documented

---

## 📋 Core Project Management

| Document | Summary | Words | Last Verified |
|----------|---------|--------|---------------|
| **STARTUP.md** | Session initialization procedures and context loading | ~1000 | [UPDATE_DATE] |
| **WRAPUP.md** | Standardized end-of-session documentation procedures | ~1200 | [UPDATE_DATE] |
| **CLAUDE.md** | AI assistant context and project primer | ~800 | [UPDATE_DATE] |

## 📝 Session Tracking & Project Management

| Document | Summary | Words | Last Verified |
|----------|---------|--------|---------------|
| **SessionSummaries/** | Session-by-session development history | varies | ongoing |
| **NextSteps/** | Priority task planning for upcoming sessions | varies | ongoing |
| **Issues/** | Bug tracking and issue documentation | varies | ongoing |
| **StructureDocs/** | Project structure snapshots over time | varies | ongoing |

---

**Usage Note:** This catalog should be updated during every session's wrap-up procedures to maintain accuracy and usefulness for project navigation.
EOF
    echo -e "${GREEN}✅ Created: DOCUMENT_CATALOG.md${NC}"
else
    echo -e "${YELLOW}📄 File exists: DOCUMENT_CATALOG.md (skipping)${NC}"
fi

echo ""
echo -e "${BLUE}🔧 Creating configuration files...${NC}"

# Create .editorconfig for consistent formatting
EDITORCONFIG_FILE="$PROJECT_ROOT/.editorconfig"
if [ ! -f "$EDITORCONFIG_FILE" ]; then
    cat > "$EDITORCONFIG_FILE" << 'EOF'
# EditorConfig for Documentation Protocol
root = true

[*.md]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.{yml,yaml}]
indent_style = space
indent_size = 2

[*.sh]
indent_style = space
indent_size = 2
EOF
    echo -e "${GREEN}✅ Created: .editorconfig${NC}"
else
    echo -e "${YELLOW}📄 File exists: .editorconfig (skipping)${NC}"
fi

echo ""
echo -e "${BLUE}📖 Creating README section...${NC}"

# Create README content for documentation protocols (append to existing or create new)
README_FILE="$PROJECT_ROOT/README.md"
DOCS_SECTION="## 📝 Documentation Standards

This project uses systematic documentation protocols. See:
- \`STARTUP.md\` - Session initialization procedures
- \`WRAPUP.md\` - End-of-session documentation
- \`docs/DOCUMENT_CATALOG.md\` - Complete documentation index

### Required for all .md files:
\`\`\`markdown
> **Doc Meta**
> - **Purpose:** Brief description
> - **Scope:** What it covers
> - **Owner:** @your-handle  
> - **Last-verified:** YYYY-MM-DD
\`\`\`

Documentation directories:
- \`SessionSummaries/\` - Development session summaries
- \`NextSteps/\` - Priority planning and tasks
- \`Issues/\` - Bug reports and investigations  
- \`StructureDocs/\` - Project structure snapshots"

if [ -f "$README_FILE" ]; then
    if ! grep -q "Documentation Standards" "$README_FILE"; then
        echo "" >> "$README_FILE"
        echo "$DOCS_SECTION" >> "$README_FILE"
        echo -e "${GREEN}✅ Added documentation section to README.md${NC}"
    else
        echo -e "${YELLOW}📄 README.md already has documentation section${NC}"
    fi
else
    echo "# $(basename "$PROJECT_ROOT")" > "$README_FILE"
    echo "" >> "$README_FILE"
    echo "$DOCS_SECTION" >> "$README_FILE"
    echo -e "${GREEN}✅ Created: README.md with documentation section${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Setup complete!${NC}"
echo ""
echo -e "${BLUE}📋 Next Steps:${NC}"
echo "1. Edit CLAUDE.md to add project-specific context"
echo "2. Run your first session using STARTUP.md procedures"  
echo "3. Create initial structure document in StructureDocs/"
echo "4. Set up any project-specific environment procedures"
echo "5. Consider adding pre-commit hooks for Doc Meta validation"
echo ""
echo -e "${BLUE}📚 Templates available in:${NC}"
echo "$TEMPLATE_DIR/"
echo ""
echo -e "${YELLOW}⚠️  Don't forget to:${NC}"
echo "- Update placeholder values in CLAUDE.md"
echo "- Add your actual project information"
echo "- Update the DOCUMENT_CATALOG.md with current date"
echo "- Commit these files to version control"