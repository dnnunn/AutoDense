# Documentation Metadata (Doc Meta) Standards

> **Doc Meta**
> - **Purpose:** Standards and templates for required documentation metadata blocks
> - **Scope:** Doc Meta block requirements, examples, and quality enforcement procedures
> - **Owner:** @[PROJECT_OWNER]
> - **Last-verified:** [CURRENT_DATE]

## 📋 Required Doc Meta Block

**MANDATORY:** Every `.md` file in the project MUST include this metadata block at the top (after the title):

### Standard Doc Meta Block Template
```markdown
> **Doc Meta**
> - **Purpose:** [Brief description of what this document is for]
> - **Scope:** [What it covers and what it doesn't cover]
> - **Owner:** @[github-handle-or-team-name]
> - **Last-verified:** [YYYY-MM-DD]
```

### Real Example
```markdown
# API Documentation

> **Doc Meta**
> - **Purpose:** Complete REST API reference for client integration
> - **Scope:** All public endpoints, authentication, and error handling (excludes internal APIs)
> - **Owner:** @backend-team
> - **Last-verified:** 2025-08-30
```

## 📖 Field Definitions

### Purpose
- **What it is:** Brief 1-2 sentence description of the document's primary function
- **Be specific:** "User authentication guide" not "How to use auth"
- **Target audience implied:** Who this document serves
- **Examples:**
  - ✅ "Setup procedures for development environment configuration"
  - ✅ "Bug tracking and issue resolution documentation for Sprint 12"
  - ❌ "Documentation about the system" (too vague)
  - ❌ "Stuff developers need to know" (unprofessional)

### Scope
- **What it covers:** Explicit boundaries of the documentation
- **What it excludes:** Important limitations or related topics not covered
- **Prevents scope creep:** Clear definition of what belongs in this document
- **Examples:**
  - ✅ "Complete API endpoints and authentication (excludes webhook documentation)"
  - ✅ "Java configuration and deployment (excludes database setup procedures)"
  - ❌ "Everything about the API" (too broad)
  - ❌ "Some endpoints" (too vague)

### Owner
- **Format:** Always use `@github-handle` or `@team-name`
- **Accountability:** Who maintains and updates this document
- **Contact person:** Who to ask questions about this documentation
- **Examples:**
  - ✅ `@john-smith`
  - ✅ `@backend-team`
  - ✅ `@documentation-squad`
  - ❌ `John Smith` (no @ symbol)
  - ❌ `Someone` (not specific)

### Last-verified
- **Format:** Always `YYYY-MM-DD`
- **When to update:** Any substantial modification or verification of accuracy
- **Staleness indicator:** Shows document freshness for readers
- **Examples:**
  - ✅ `2025-08-30`
  - ✅ `2025-12-15`
  - ❌ `Aug 30, 2025` (wrong format)
  - ❌ `30/08/2025` (wrong format)

## 🎯 Doc Meta Examples by Document Type

### Session Summary
```markdown
> **Doc Meta**
> - **Purpose:** Session summary documenting API integration implementation
> - **Scope:** Work completed on user authentication and payment endpoints
> - **Owner:** @development-lead
> - **Last-verified:** 2025-08-30
```

### Technical Architecture
```markdown
> **Doc Meta**
> - **Purpose:** System architecture overview for microservices deployment
> - **Scope:** Service communication patterns and data flow (excludes infrastructure)
> - **Owner:** @architecture-team  
> - **Last-verified:** 2025-08-30
```

### Issue Report
```markdown
> **Doc Meta**
> - **Purpose:** Critical bugs discovered during load testing phase
> - **Scope:** Performance and stability issues with proposed solutions
> - **Owner:** @qa-team
> - **Last-verified:** 2025-08-30
```

### Setup Guide
```markdown
> **Doc Meta**
> - **Purpose:** Complete development environment setup for new team members
> - **Scope:** Local development configuration (excludes production deployment)
> - **Owner:** @devops-lead
> - **Last-verified:** 2025-08-30
```

### API Reference
```markdown
> **Doc Meta**
> - **Purpose:** Complete REST API reference for external client integration
> - **Scope:** Public endpoints, authentication, rate limiting (excludes internal APIs)
> - **Owner:** @api-team
> - **Last-verified:** 2025-08-30
```

## 🚨 Quality Standards

### Purpose Quality Checklist
- [ ] **Specific:** Clear about the document's exact function
- [ ] **Actionable:** Reader knows what they can accomplish with this doc
- [ ] **Concise:** 1-2 sentences maximum
- [ ] **Professional:** Appropriate tone and terminology

### Scope Quality Checklist  
- [ ] **Explicit boundaries:** Clear what's included
- [ ] **Explicit exclusions:** Clear what's NOT included
- [ ] **Prevents confusion:** Reader knows if their question is covered
- [ ] **Realistic:** Scope matches actual content

### Owner Quality Checklist
- [ ] **Uses @ symbol:** Proper GitHub handle format
- [ ] **Real person/team:** Actually exists and can be contacted
- [ ] **Appropriate:** Person/team actually responsible for this content
- [ ] **Current:** Not a former team member or disbanded team

### Last-verified Quality Checklist
- [ ] **Correct format:** YYYY-MM-DD only
- [ ] **Recent enough:** Within reasonable timeframe for document type
- [ ] **Accurate:** Reflects when content was actually verified
- [ ] **Updated:** Changes with substantial modifications

## ⚡ Enforcement and Quality Gates

### Pre-commit Hooks (Recommended)
```bash
# Check for Doc Meta blocks in markdown files
#!/bin/bash
for file in $(git diff --cached --name-only | grep '\.md$'); do
  if ! grep -q "> \*\*Doc Meta\*\*" "$file"; then
    echo "ERROR: $file missing required Doc Meta block"
    exit 1
  fi
done
```

### CI/CD Integration
- **Lint documentation:** Check for required Doc Meta blocks
- **Validate format:** Ensure proper formatting and required fields
- **Freshness alerts:** Flag documents with old last-verified dates

### Manual Review Checklist
- [ ] Doc Meta block present at document top
- [ ] All four fields completed (Purpose, Scope, Owner, Last-verified)
- [ ] Purpose is specific and actionable
- [ ] Scope defines clear boundaries
- [ ] Owner uses proper @handle format
- [ ] Last-verified uses YYYY-MM-DD format

## 🔄 Maintenance Procedures

### When to Update Last-verified
- **Major content changes:** Substantial modifications to information
- **Accuracy verification:** Confirming existing information is still correct
- **Scope changes:** Expanding or narrowing what the document covers
- **NOT required for:** Minor typo fixes, formatting improvements

### Document Lifecycle
1. **Creation:** Include complete Doc Meta block
2. **Updates:** Modify last-verified date for substantial changes
3. **Review:** Periodic verification of accuracy and relevance
4. **Deprecation:** Update scope to indicate deprecated status
5. **Archival:** Move to deprecated section with clear tombstone

### Quality Monitoring
- **Weekly:** Review new documents for proper Doc Meta blocks
- **Monthly:** Identify documents with stale last-verified dates
- **Quarterly:** Audit all documentation for Doc Meta compliance

---

**Templates Available:**
- Use the standard template above for new documents
- Copy Doc Meta blocks from similar document types
- Ensure all four fields are completed before publishing
- Update last-verified date when making substantial changes

**This standard ensures consistent documentation quality and helps readers quickly understand document purpose, scope, and maintenance status.**