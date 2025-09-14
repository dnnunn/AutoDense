# ClaudeKit Reference for AutoDense

> **Doc Meta**
> - **Purpose:** Comprehensive reference guide for ClaudeKit tools optimized for AutoDense development workflow
> - **Scope:** All available commands, agents, and hooks organized by AutoDense project priorities
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-27

## Overview

AutoDense has a fully configured ClaudeKit setup with 22 commands, 32 specialized agents, and 22 hooks. This reference organizes these tools by relevance to AutoDense's current development priorities.

## 🎯 High Priority Tools (Current AutoDense Issues)

### For Preset Parameter Routing Issues
- **`refactoring-expert`** - Systematic code refactoring for SDS_PAGE_DENSITOMETRY parameter mis-routing
- **`triage-expert`** - Context gathering and problem diagnosis for `lane_count` → `open_image` routing bug
- **`code-search`** - Find all instances of preset parameter routing code

### For Overlay & ROI Manager Issues  
- **`/validate-and-fix`** - Code quality checks for overlay refresh problems
- **`git-expert`** - Advanced analysis of overlay-related commits and changes
- **`testing-expert`** - Create tests to verify overlay refresh and ROI Manager suppression

### Quick Diagnostics
```bash
# Check current project health
claudekit doctor

# Immediate code review for specific issues
/code-review

# Validate and fix code quality issues
/validate-and-fix
```

## 🔧 Core Development Workflow

### Session Management
- **`/STARTUP`** - Project initialization with current context (328 tokens)
- **`/WRAPUP`** - Session cleanup and summary generation (732 tokens)
- **`/ADDTOCHANGELOG`** - Automatic changelog updates (432 tokens)

### Git Workflow Integration
- **`/git:commit`** - Enhanced git commits with context (1.1k tokens)
- **`/git:push`** - Intelligent push operations (478 tokens)
- **`/git:status`** - Comprehensive status reporting (505 tokens)
- **`git-expert`** - Advanced git workflow analysis (4.1k tokens)

### Code Quality & Review
- **`/code-review`** - Embedded comprehensive code review (2.6k tokens)
- **`code-review-expert`** - Specialized code review analysis (3.5k tokens)
- **`refactoring-expert`** - Code smell detection and refactoring (3.0k tokens)

## 🏗️ Build & Development Tools

### For Java/Maven Projects
- **`linting-expert`** - Code linting and formatting across languages (3.9k tokens)
- **`typescript-build-expert`** - Build optimization (adaptable to Maven) (4.3k tokens)
- **`docker-expert`** - Containerization for deployment (3.5k tokens)

### Testing & Validation
- **`testing-expert`** - Comprehensive testing strategy (4.7k tokens)
- **`jest-testing-expert`** - Advanced testing frameworks (6.5k tokens)
- **`/validate-and-fix`** - Automated validation and fixes (1.2k tokens)

## 🔍 Specialized Analysis Tools

### For ImageJ/Fiji Integration
- **`triage-expert`** - Diagnose ImageJ plugin integration issues (3.7k tokens)
- **`documentation-expert`** - Analyze and improve technical documentation (3.6k tokens)
- **`oracle`** - General-purpose problem solving (2.2k tokens)

### For API Integration (Gemini Orchestrator)
- **`ai-sdk-expert`** - API integration and optimization (4.9k tokens)
- **`nodejs-expert`** - Server-side integration patterns (7.0k tokens)

### Database & Session Management
- **`database-expert`** - General database optimization (2.9k tokens)
- **`postgres-expert`** - Advanced PostgreSQL operations (6.9k tokens)

## 📋 Project Management Tools

### Specification & Planning
- **`/spec:create`** - Create project specifications (2.3k tokens)
- **`/spec:decompose`** - Break down complex tasks (5.0k tokens)
- **`/spec:execute`** - Execute planned specifications (1.2k tokens)
- **`/spec:validate`** - Validate implementation against specs (1.7k tokens)

### Checkpoint Management
- **`/checkpoint:create`** - Create development checkpoints (316 tokens)
- **`/checkpoint:list`** - List available checkpoints (304 tokens)
- **`/checkpoint:restore`** - Restore previous checkpoint (412 tokens)

### Development Lifecycle
- **`/dev:cleanup`** - Comprehensive project cleanup (2.1k tokens)
- **`/gh:repo-init`** - GitHub repository initialization (359 tokens)

## 🛠️ Custom Tool Creation

### Extensibility
- **`/create-command`** - Create custom project commands (1.1k tokens)
- **`/create-subagent`** - Create specialized agents (2.1k tokens)
- **`agents-md:init`** - Initialize agent documentation (4.1k tokens)

### Configuration
- **`/config:bash-timeout`** - Configure command timeouts (808 tokens)
- **`agents-md:cli`** - CLI management for agents (670 tokens)

## 🎯 AutoDense-Specific Workflows

### Immediate Priority Workflow
```bash
# 1. Diagnose current issues
/triage-expert "SDS_PAGE_DENSITOMETRY preset parameter routing"

# 2. Fix parameter routing
/refactoring-expert "Fix lane_count routing to detect_lanes"

# 3. Validate changes
/validate-and-fix

# 4. Test overlay refresh
/testing-expert "Create tests for overlay refresh mechanism"

# 5. Clean commit
/git:commit
```

### Development Session Workflow
```bash
# Start session
/STARTUP

# Work on features with specialized agents
# [development work]

# Quality checks
/code-review
/validate-and-fix

# Clean finish
/git:commit
/WRAPUP
```

### Emergency Debug Workflow
```bash
# Quick diagnosis
claudekit doctor
/triage-expert

# Deep analysis if needed
/code-review-expert
/git-expert

# Fix and validate
/refactoring-expert
/validate-and-fix
```

## 📊 Tool Usage Analytics

### High-Value Commands (>1k tokens)
- **`/spec:decompose`** (5.0k) - Complex task breakdown
- **`nodejs-expert`** (7.0k) - Server integration
- **`react-performance-expert`** (7.0k) - Performance optimization
- **`postgres-expert`** (6.9k) - Advanced database operations

### Quick Actions (<500 tokens)
- **`/checkpoint:create`** (316) - Fast checkpoints
- **`/STARTUP`** (328) - Session initialization
- **`/checkpoint:list`** (304) - Quick checkpoint overview
- **`/gh:repo-init`** (359) - Repository setup

## 🔄 Integration with AutoDense Architecture

### Handle-Based System Support
- Use **`code-search`** to find handle management code
- Use **`refactoring-expert`** to improve handle consistency
- Use **`testing-expert`** to validate handle persistence

### Gemini Orchestrator Integration
- Use **`ai-sdk-expert`** for API optimization
- Use **`triage-expert`** for orchestration issues
- Use **`documentation-expert`** for API documentation

### ImageJ Plugin Development
- Use **`linting-expert`** for Java code quality
- Use **`docker-expert`** for deployment packaging
- Use **`git-expert`** for plugin versioning

## 📈 Recommended Daily Usage

1. **Start**: `/STARTUP` - Load project context
2. **Plan**: `/spec:decompose` - Break down complex tasks
3. **Code**: Use appropriate expert agents
4. **Quality**: `/code-review` + `/validate-and-fix`
5. **Commit**: `/git:commit` with enhanced context
6. **Finish**: `/WRAPUP` - Clean session closure

This ClaudeKit setup provides comprehensive tooling specifically optimized for AutoDense's image analysis, Java development, and ImageJ plugin architecture.